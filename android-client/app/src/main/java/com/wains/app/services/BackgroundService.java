package com.wains.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.wains.app.R;

public class BackgroundService extends Service {

    private static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "StudentLabChannel";

    private DatabaseReference ref;
    private ValueEventListener listener;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Background service created");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Background service started");

        // Build foreground notification (silent)
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("StudentLab Monitor")
                .setContentText("Background service running")
                .setSmallIcon(android.R.drawable.stat_notify_more) // ensure icon exists
                .setPriority(NotificationCompat.PRIORITY_MIN) // low priority (less visible)
                .setSilent(true)
                .build();

        startForeground(1, notification);

        // Example: Listen for a value in Firebase
        ref = FirebaseDatabase.getInstance().getReference("service_commands");
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                // Handle data update
                Log.d(TAG, "Firebase data: " + snapshot.getValue());
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Firebase listener cancelled", error.toException());
            }
        };
        ref.addValueEventListener(listener);

        return START_STICKY; // restart if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Background service destroyed");
        if (ref != null && listener != null) {
            ref.removeEventListener(listener);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "StudentLab Background",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps StudentLab service running");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
