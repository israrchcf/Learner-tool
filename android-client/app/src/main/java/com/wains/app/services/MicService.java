package com.wains.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

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
import java.io.IOException;

public class MicService extends Service {
    private static final String TAG = "MicService";
    private static final String CHANNEL_ID = "MicServiceChannel";
    private MediaRecorder mediaRecorder;
    private File audioFile;
    private DatabaseReference micCommandRef;
    private ValueEventListener commandListener;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Mic Service")
                .setContentText("Recording audio in the background...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MicService started.");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            micCommandRef = FirebaseManager.getInstance().getMicReference(user.getUid()).child("command");
            startListeningForCommands();
        } else {
            Log.e(TAG, "Firebase user not authenticated. MicService cannot function.");
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Mic Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void startListeningForCommands() {
        if (micCommandRef == null) return;

        commandListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String command = snapshot.getValue(String.class);
                if ("record_audio".equals(command)) {
                    Log.d(TAG, "Received record_audio command.");
                    startRecording();
                    // Clear the command to avoid re-triggering
                    micCommandRef.setValue(null);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to read command from Firebase.", error.toException());
            }
        };
        micCommandRef.addValueEventListener(commandListener);
    }

    private void startRecording() {
        if (mediaRecorder != null) return; // Already recording

        try {
            audioFile = File.createTempFile("audio_rec", ".3gp", getExternalFilesDir(null));
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            Log.d(TAG, "Recording started: " + audioFile.getAbsolutePath());

            // Stop recording after a fixed duration (e.g., 10 seconds)
            // You might want to make this configurable via Firebase in a real app
            new android.os.Handler().postDelayed(this::stopRecording, 10000);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording.", e);
            stopRecording();
        }
    }

    private void stopRecording() {
        if (mediaRecorder == null) return; // Not recording

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Log.d(TAG, "Recording stopped.");
            uploadAudioToFirebase();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to stop recording. MediaRecorder not in a valid state.", e);
        }
    }

    private void uploadAudioToFirebase() {
        if (audioFile == null || !audioFile.exists()) {
            Log.e(TAG, "Audio file not found for upload.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "User not authenticated. Cannot upload audio.");
            return;
        }

        String fileName = "mic_" + System.currentTimeMillis() + ".3gp";
        StorageReference audioRef = FirebaseStorage.getInstance().getReference()
                .child("audio").child(user.getUid()).child(fileName);

        audioRef.putFile(android.net.Uri.fromFile(audioFile))
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Audio uploaded successfully.");
                    // Delete the local file after successful upload
                    if (audioFile.delete()) {
                        Log.d(TAG, "Local audio file deleted successfully.");
                    } else {
                        Log.e(TAG, "Failed to delete local audio file.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload audio.", e);
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MicService destroyed.");
        if (commandListener != null && micCommandRef != null) {
            micCommandRef.removeEventListener(commandListener);
            Log.d(TAG, "Mic command listener removed.");
        }
        if (mediaRecorder != null) {
            mediaRecorder.release();
        }
    }
}
