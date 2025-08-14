package com.wains.app.utils.models;

public class Device {
    public String deviceId;
    public long lastSeen;
    public String status;

    public Device() {
        // Default constructor required for calls to DataSnapshot.getValue(Device.class)
    }

    public Device(String deviceId, long lastSeen, String status) {
        this.deviceId = deviceId;
        this.lastSeen = lastSeen;
        this.status = status;
    }
}
