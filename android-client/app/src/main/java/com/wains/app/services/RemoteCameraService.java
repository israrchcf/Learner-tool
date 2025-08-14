package com.wains.app.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCapture.OnImageSavedCallback;
import androidx.camera.core.ImageCapture.OutputFileOptions;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
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

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class RemoteCameraService extends LifecycleService {
    private static final String TAG = "RemoteCameraService";
    private static final String CHANNEL_ID = "RemoteCameraServiceChannel";
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private DatabaseReference remoteCameraCommandRef;
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
                .setContentTitle("Remote Camera Service")
                .setContentText("Taking photos on command...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(4, notification);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "RemoteCameraService started.");
        
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            deviceId = user.getUid();
            remoteCameraCommandRef = FirebaseManager.getInstance().getCommandReference(deviceId).child("take_photo");
            startListeningForCommands();
        } else {
            Log.e(TAG, "Firebase user not authenticated. RemoteCameraService cannot function.");
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Remote Camera Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void startListeningForCommands() {
        if (remoteCameraCommandRef == null) return;

        commandListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class)) {
                    Log.d(TAG, "Received take_photo command.");
                    takePhoto();
                    // Clear the command to avoid re-triggering
                    remoteCameraCommandRef.setValue(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to read command from Firebase.", error.toException());
            }
        };
        remoteCameraCommandRef.addValueEventListener(commandListener);
    }

    @SuppressLint("MissingPermission")
    private void takePhoto() {
        if (imageCapture != null) {
            Log.d(TAG, "Image capture is already in progress.");
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraToLifecycle();
                captureAndSaveImage();
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

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Binding failed", e);
        }
    }

    @SuppressLint("MissingPermission")
    private void captureAndSaveImage() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture not initialized.");
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + timeStamp + ".jpg");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Remote");

        OutputFileOptions outputOptions = new OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
        ).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Log.d(TAG, "Photo saved successfully: " + outputFileResults.getSavedUri());
                if (outputFileResults.getSavedUri() != null) {
                    uploadPhotoToFirebase(outputFileResults.getSavedUri());
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
            }
        });
    }

    private void uploadPhotoToFirebase(android.net.Uri fileUri) {
        if (deviceId == null) {
            Log.e(TAG, "Device ID is null, cannot upload photo.");
            return;
        }

        StorageReference photoRef = FirebaseStorage.getInstance().getReference()
                .child("photos").child(deviceId).child(fileUri.getLastPathSegment());

        photoRef.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Photo uploaded successfully.");
                    // Get a download URL and update Firebase Realtime Database if needed
                    taskSnapshot.getStorage().getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();
                        Log.d(TAG, "Download URL: " + downloadUrl);
                        // Here you can push the URL to the Realtime Database
                        // to be accessed by the admin panel.
                        // For example:
                        // FirebaseManager.getInstance().getPhotoReference(deviceId).push().setValue(downloadUrl);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Photo upload failed: " + e.getMessage(), e);
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "RemoteCameraService destroyed.");
        if (commandListener != null && remoteCameraCommandRef != null) {
            remoteCameraCommandRef.removeEventListener(commandListener);
            Log.d(TAG, "Remote camera command listener removed.");
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}
