package dev.fusic;

import android.os.Build;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

import java.util.ArrayList;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MediaPlayerPlugin.class);
        super.onCreate(savedInstanceState);

        requestPermissions();
    }

    private void requestPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(android.Manifest.permission.FOREGROUND_SERVICE);
        }
        if (Build.VERSION.SDK_INT >= 34) { // Android 14 API 34
            permissions.add(android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), 101);
        }
    }
}
