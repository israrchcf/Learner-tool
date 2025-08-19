package com.wains.app.services.manager;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple runtime permission helper.
 *
 * Usage:
 *  PermissionManager.requestPermissions(activity, new String[]{ Manifest.permission.SEND_SMS }, REQ_CODE, callback);
 *  In Activity.onRequestPermissionsResult -> PermissionManager.handleResult(...)
 */
public class PermissionManager {

    public interface PermissionCallback {
        void onResult(boolean allGranted, @NonNull String[] grantedPermissions, @NonNull String[] deniedPermissions);
    }

    /**
     * Request permissions; shows a minimal rationale dialog when needed.
     */
    public static void requestPermissions(@NonNull final Activity activity,
                                          @NonNull final String[] permissions,
                                          final int requestCode) {
        List<String> toRequest = new ArrayList<>();
        List<String> rationale = new ArrayList<>();

        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, p)) {
                    rationale.add(p);
                }
            }
        }

        if (toRequest.isEmpty()) {
            // nothing to request
            return;
        }

        final String[] arr = toRequest.toArray(new String[0]);

        if (!rationale.isEmpty()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Permissions required")
                    .setMessage("The app needs these permissions to work properly. Please allow them.")
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(activity, arr, requestCode);
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(activity, arr, requestCode);
        }
    }

    /**
     * Helper to analyze permission results in Activity.onRequestPermissionsResult().
     */
    public static void handleResult(int requestCode,
                                    @NonNull String[] permissions,
                                    @NonNull int[] grantResults,
                                    @NonNull PermissionCallback callback) {
        List<String> granted = new ArrayList<>();
        List<String> denied = new ArrayList<>();

        for (int i = 0; i < permissions.length; i++) {
            if (i < grantResults.length && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                granted.add(permissions[i]);
            } else {
                denied.add(permissions[i]);
            }
        }

        callback.onResult(denied.isEmpty(), granted.toArray(new String[0]), denied.toArray(new String[0]));
    }
}
