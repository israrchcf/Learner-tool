package com.wains.app.services.manager;

import android.Manifest;
import androidx.annotation.NonNull;
import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import android.app.Activity;
import androidx.annotation.NonNull;
import android.content.Context;
import androidx.annotation.NonNull;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import android.location.Criteria;
import androidx.annotation.NonNull;
import android.location.Location;
import androidx.annotation.NonNull;
import android.location.LocationListener;
import androidx.annotation.NonNull;
import android.location.LocationManager;
import androidx.annotation.NonNull;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.os.Looper;
import androidx.annotation.NonNull;

import androidx.annotation.NonNull;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;

/**
 * Lightweight Location helper (single-shot + last-known fallback).
 * Caller must ensure runtime permission ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION has been granted.
 */
public class MyLocationManager {

    public interface LocationCallback {
        void onSuccess(@NonNull Location location);
        void onError(@NonNull String reason);
    }

    private final Context appContext;

    public MyLocationManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Requests a single location update and returns via callback.
     * Make sure caller has requested runtime permissions beforehand.
     */
    @SuppressLint("MissingPermission")
    public void requestSingleLocation(@NonNull Activity activity, @NonNull final LocationCallback callback) {
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback.onError("location_permission_not_granted");
            return;
        }

        final LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            callback.onError("location_manager_unavailable");
            return;
        }

        try {
            // Try best provider last-known first
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            String provider = lm.getBestProvider(criteria, true);

            Location last = null;
            if (provider != null) last = lm.getLastKnownLocation(provider);
            if (last == null) {
                for (String p : lm.getProviders(true)) {
                    last = lm.getLastKnownLocation(p);
                    if (last != null) break;
                }
            }

            if (last != null) {
                callback.onSuccess(last);
                return;
            }

            // If no last-known, request a single update on main looper
            final LocationListener singleListener = new LocationListener() {
                @Override public void onLocationChanged(@NonNull Location location) {
                    try {
                        lm.removeUpdates(this);
                    } catch (Exception ignored) {}
                    callback.onSuccess(location);
                }
                @Override public void onProviderEnabled(@NonNull String provider) {}
                @Override public void onProviderDisabled(@NonNull String provider) {}
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            };

            String requestProvider = (provider != null) ? provider : LocationManager.GPS_PROVIDER;
            lm.requestSingleUpdate(requestProvider, singleListener, Looper.getMainLooper());
        } catch (SecurityException se) {
            callback.onError("security_exception:" + se.getMessage());
        } catch (Exception e) {
            callback.onError("error:" + e.getMessage());
        }
    }
}
