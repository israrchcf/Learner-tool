package com.wains.app.services.manager;

import android.Manifest;
import androidx.annotation.NonNull;
import android.content.ContentResolver;
import androidx.annotation.NonNull;
import android.content.Context;
import androidx.annotation.NonNull;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import android.database.Cursor;
import androidx.annotation.NonNull;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.telephony.SmsManager;
import androidx.annotation.NonNull;

import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import androidx.annotation.NonNull;
import java.util.List;
import androidx.annotation.NonNull;

/**
 * SMS helper utilities.
 * - sendTextSms: sends SMS (requires SEND_SMS)
 * - readInbox: reads SMS inbox (requires READ_SMS)
 *
 * Be mindful: carriers may charge for SMS.
 */
public class MySmsManager {

    /**
     * Send an SMS text message. Returns true when send call initiated.
     * Caller must have SEND_SMS permission.
     */
    public static boolean sendTextSms(@NonNull Context context, @NonNull String destination, @NonNull String message) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(destination, null, message, null, null);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Read recent SMS from inbox. Returns a list of trimmed strings; each item: "address | date | body".
     * Caller must have READ_SMS permission.
     */
    public static List<String> readInbox(@NonNull Context context, int limit) {
        List<String> rows = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            rows.add("permission_denied:READ_SMS");
            return rows;
        }

        ContentResolver cr = context.getContentResolver();
        Uri uri = Uri.parse("content://sms/inbox");
        String order = "date DESC LIMIT " + Math.max(1, limit);
        Cursor c = null;
        try {
            c = cr.query(uri, new String[]{"address", "date", "body"}, null, null, order);
            if (c != null) {
                while (c.moveToNext()) {
                    String addr = c.getString(0);
                    String date = c.getString(1);
                    String body = c.getString(2);
                    if (body != null && body.length() > 200) body = body.substring(0, 200) + "...";
                    rows.add(addr + " | " + date + " | " + body);
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
