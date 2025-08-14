package com.wains.app.utils;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class WallpaperManagerUtil {
    private static final String TAG = "WallpaperManagerUtil";
    private final Context context;
    private final FirebaseManager firebaseManager;
    private ValueEventListener wallpaperListener;
    private final String deviceId;
    private final DatabaseReference wallpaperRef;

    public WallpaperManagerUtil(Context context) {
        this.context = context;
        this.firebaseManager = FirebaseManager.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            this.deviceId = user.getUid();
            this.wallpaperRef = firebaseManager.getWallpaperReference(this.deviceId);
            startListeningForWallpaperCommands();
        } else {
            this.deviceId = null;
            this.wallpaperRef = null;
        }
    }

    private void startListeningForWallpaperCommands() {
        if (wallpaperRef == null) {
            Log.e(TAG, "Device ID is null, cannot listen for wallpaper commands.");
            return;
        }

        wallpaperListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String wallpaperUrl = dataSnapshot.getValue(String.class);
                if (wallpaperUrl != null && !wallpaperUrl.isEmpty()) {
                    Log.d(TAG, "New wallpaper URL received: " + wallpaperUrl);
                    setWallpaperFromUrl(wallpaperUrl);
                    // Remove the listener after the command is processed
                    removeListener();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to read wallpaper command.", databaseError.toException());
            }
        };
        wallpaperRef.addValueEventListener(wallpaperListener);
    }

    public void removeListener() {
        if (wallpaperListener != null && wallpaperRef != null) {
            wallpaperRef.removeEventListener(wallpaperListener);
            Log.d(TAG, "Wallpaper command listener removed.");
        }
    }

    private void setWallpaperFromUrl(String urlString) {
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);

                if (bitmap != null) {
                    WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
                    wallpaperManager.setBitmap(bitmap);
                    Log.d(TAG, "Wallpaper set successfully.");
                } else {
                    Log.e(TAG, "Downloaded bitmap is null.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to set wallpaper from URL: " + urlString, e);
            }
        }).start();
    }
}
