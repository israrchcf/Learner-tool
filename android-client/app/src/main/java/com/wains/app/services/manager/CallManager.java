package com.wains.app.services.manager;

import android.Manifest;
import androidx.annotation.NonNull;
import android.content.Context;
import androidx.annotation.NonNull;
import android.content.Intent;
import androidx.annotation.NonNull;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import android.database.Cursor;
import androidx.annotation.NonNull;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.provider.CallLog;
import androidx.annotation.NonNull;

import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import androidx.annotation.NonNull;
import java.util.List;
import androidx.annotation.NonNull;

/**
 * Call-related utilities:
 * - dialNumber: opens dialer (no permission)
 * - startCall: perform ACTION_CALL (requires CALL_PHONE)
 * - readCallLogs: requires READ_CALL_LOG
 */
public class CallManager {

    /**
     * Open dialer with number prefilled (no permission required).
     */
    public static void dialNumber(@NonNull Context context, @NonNull String number) {
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Try to start a direct call. Requires CALL_PHONE permission.
     * Returns true if intent started (permission must already be granted).
     */
    public static boolean startCall(@NonNull Context context, @NonNull String number) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    /**
     * Read recent call logs. Returns list of "number | type | date | duration".
     */
    public static List<String> readCallLogs(@NonNull Context context, int limit) {
        List<String> rows = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            rows.add("permission_denied:READ_CALL_LOG");
            return rows;
        }

        Cursor c = null;
        try {
            String order = CallLog.Calls.DATE + " DESC LIMIT " + Math.max(1, limit);
            c = context.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                    null, null, order);
            if (c != null) {
                while (c.moveToNext()) {
                    String num = c.getString(0);
                    String type = c.getString(1);
                    String date = c.getString(2);
                    String dur = c.getString(3);
                    rows.add(num + " | " + type + " | " + date + " | " + dur);
                }
            }
        } catch (SecurityException se) {
            rows.clear();
            rows.add("security_exception:" + se.getMessage());
        } catch (Exception e) {
            rows.clear();
            rows.add("error:" + e.getMessage());
        } finally {
            if (c != null) c.close();
        }
        return rows;
    }
}
