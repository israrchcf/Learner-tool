package com.wains.app.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class CallMonitorService extends BroadcastReceiver {
    private static final String TAG = "CallMonitorService";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            Log.d(TAG, "Phone state changed.");
            String phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                Map<String, Object> callData = new HashMap<>();
                callData.put("timestamp", System.currentTimeMillis());

                switch (phoneState) {
                    case TelephonyManager.EXTRA_STATE_RINGING:
                        callData.put("state", "ringing");
                        callData.put("number", incomingNumber);
                        Log.i(TAG, "Ringing from: " + incomingNumber);
                        break;
                    case TelephonyManager.EXTRA_STATE_OFFHOOK:
                        callData.put("state", "off-hook");
                        Log.i(TAG, "Call started.");
                        break;
                    case TelephonyManager.EXTRA_STATE_IDLE:
                        callData.put("state", "idle");
                        Log.i(TAG, "Call ended or idle.");
                        break;
                    default:
                        Log.d(TAG, "Unknown phone state: " + phoneState);
                        return;
                }

                FirebaseManager.getInstance().getCallReference(user.getUid())
                        .push()
                        .setValue(callData)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Call log uploaded to Firebase successfully."))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to upload call log to Firebase.", e));
            }
        }
    }
}
