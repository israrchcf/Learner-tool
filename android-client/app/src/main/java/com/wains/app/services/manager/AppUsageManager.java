package com.wains.app.services.manager;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Helper for querying app usage statistics (requires "Usage Access" enabled for the app).
 */
public class AppUsageManager {

    /**
     * Check if the app has UsageStats permission (Usage Access).
     */
    public static boolean hasUsageStatsPermission(@NonNull Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Open Settings screen where user can grant Usage Access.
     */
    public static void requestUsageAccessSettings(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Query usage stats between beginMillis and endMillis. Returns list sorted by lastTimeUsed desc.
     */
    public static List<UsageStats> queryUsageStats(@NonNull Context context, long beginMillis, long endMillis) {
        List<UsageStats> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return out;

        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return out;

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginMillis, endMillis);
        if (stats == null) return out;

        Collections.sort(stats, new Comparator<UsageStats>() {
            @Override
            public int compare(UsageStats a, UsageStats b) {
                return Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed());
            }
        });

        out.addAll(stats);
        return out;
    }
}
