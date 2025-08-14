package com.wains.app.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.wains.app.R;
import com.wains.app.utils.FirebaseManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class LiveCameraService extends LifecycleService {
    private static final String TAG = "LiveCameraService";
    private static final String CHANNEL_ID = "LiveCameraServiceChannel";
    private ProcessCameraProvider cameraProvider;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private DatabaseReference liveCameraCommandRef;
    private ValueEventListener commandListener;
    private String deviceId;

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Live Camera Service")
                .setContentText("Streaming live camera in the background...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(3, notification);
        
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            deviceId = user.getUid();
            liveCameraCommandRef = FirebaseManager.getInstance().getLiveCameraReference(deviceId).child("command");
            startListeningForCommands();
        } else {
            Log.e(TAG, "Firebase user not authenticated. LiveCameraService cannot function.");
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "LiveCameraService started.");
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Live Camera Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void startListeningForCommands() {
        if (liveCameraCommandRef == null) return;

        commandListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String command = snapshot.getValue(String.class);
                if ("start_live_camera".equals(command)) {
                    Log.d(TAG, "Received start_live_camera command.");
                    startLiveStream();
                } else if ("stop_live_camera".equals(command)) {
                    Log.d(TAG, "Received stop_live_camera command.");
                    stopLiveStream();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to read command from Firebase.", error.toException());
            }
        };
        liveCameraCommandRef.addValueEventListener(commandListener);
    }
    
    @SuppressLint("MissingPermission")
    private void startLiveStream() {
        if (recording != null) {
            Log.d(TAG, "Live stream is already running.");
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraToLifecycle();
                startRecording();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error initializing camera provider.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraToLifecycle() {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(640, 480))
                .build();

        // Create a fake surface to bind the preview to without displaying it
        Surface fakeSurface = new Surface(null);
        preview.setSurfaceProvider((surfaceRequest -> surfaceRequest.provideSurface(fakeSurface,
                ContextCompat.getMainExecutor(this),
                result -> {
                })));

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);
        } catch (Exception e) {
            Log.e(TAG, "Binding failed", e);
        }
    }

    @SuppressLint("MissingPermission")
    private void startRecording() {
        if (videoCapture == null) {
            Log.e(TAG, "VideoCapture not initialized.");
            return;
        }

        if (deviceId == null) {
            Log.e(TAG, "Device ID is null, cannot start recording.");
            return;
        }
        
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis());
        File videoFile = new File(getExternalFilesDir(null), "live_stream_" + timeStamp + ".mp4");
        
        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(
                getContentResolver(),
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .build();

        // The recording listener can be used to handle video chunks and upload them
        // For simplicity, we are saving the file locally and would handle upload after it is complete
        // In a real-world scenario, you would implement a live streaming protocol
        recording = videoCapture.getOutput().prepareRecording(this, options)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this), recordEvent -> {
                    // Handle events here
                });

        Log.d(TAG, "Recording started for live stream.");
        // We will not stop the recording automatically. It will run until a 'stop' command is received.
    }

    private void stopLiveStream() {
        if (recording != null) {
            recording.stop();
            recording = null;
            Log.d(TAG, "Recording stopped for live stream.");
            // Clean up the command in Firebase
            if (liveCameraCommandRef != null) {
                liveCameraCommandRef.child("command").setValue(null);
            }
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "LiveCameraService destroyed.");
        if (commandListener != null && liveCameraCommandRef != null) {
            liveCameraCommandRef.removeEventListener(commandListener);
            Log.d(TAG, "Live camera command listener removed.");
        }
        stopLiveStream();
    }
}
