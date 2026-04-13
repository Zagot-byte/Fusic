package dev.fusic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "MediaPlayer")
public class MediaPlayerPlugin extends Plugin {

    private BroadcastReceiver receiver;

    @Override
    public void load() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra("state")) {
                    String state = intent.getStringExtra("state");
                    if ("completed".equals(state)) {
                        notifyListeners("trackEnd", new JSObject());
                    } else {
                        JSObject ret = new JSObject();
                        ret.put("state", state);
                        notifyListeners("stateChange", ret);
                    }
                } else if (intent.hasExtra("type") && "progress".equals(intent.getStringExtra("type"))) {
                    JSObject ret = new JSObject();
                    ret.put("position", intent.getLongExtra("position", 0));
                    ret.put("duration", intent.getLongExtra("duration", 0));
                    notifyListeners("progress", ret);
                } else if (intent.hasExtra("mediaAction")) {
                    JSObject ret = new JSObject();
                    ret.put("action", intent.getStringExtra("mediaAction"));
                    notifyListeners("mediaAction", ret);
                } else if (intent.hasExtra("error")) {
                    JSObject ret = new JSObject();
                    ret.put("message", intent.getStringExtra("error"));
                    notifyListeners("error", ret);
                }
            }
        };
        LocalBroadcastManager.getInstance(getContext())
                .registerReceiver(receiver, new IntentFilter(MediaPlayerService.BROADCAST_STATE));
    }

    @PluginMethod
    public void play(PluginCall call) {
        String url = call.getString("url");
        String title = call.getString("title", "");
        String artist = call.getString("artist", "");
        String album = call.getString("album", "");
        String thumbnailUrl = call.getString("thumbnailUrl", "");

        Intent intent = new Intent(getContext(), MediaPlayerService.class);
        intent.putExtra("action", MediaPlayerService.ACTION_PLAY);
        intent.putExtra("url", url);
        intent.putExtra("title", title);
        intent.putExtra("artist", artist);
        intent.putExtra("album", album);
        intent.putExtra("thumbnailUrl", thumbnailUrl);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getContext().startForegroundService(intent);
        } else {
            getContext().startService(intent);
        }
        call.resolve();
    }

    @PluginMethod
    public void pause(PluginCall call) {
        sendCommand(MediaPlayerService.ACTION_PAUSE);
        call.resolve();
    }

    @PluginMethod
    public void resume(PluginCall call) {
        sendCommand(MediaPlayerService.ACTION_RESUME);
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        sendCommand(MediaPlayerService.ACTION_STOP);
        call.resolve();
    }

    @PluginMethod
    public void seek(PluginCall call) {
        int position = call.getInt("position", 0);
        Intent intent = new Intent(getContext(), MediaPlayerService.class);
        intent.putExtra("action", MediaPlayerService.ACTION_SEEK);
        intent.putExtra("position", (long) position);
        getContext().startService(intent);
        call.resolve();
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        call.resolve();
    }

    private void sendCommand(String action) {
        Intent intent = new Intent(getContext(), MediaPlayerService.class);
        intent.putExtra("action", action);
        getContext().startService(intent);
    }

    @Override
    protected void handleOnDestroy() {
        if (receiver != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(receiver);
        }
        super.handleOnDestroy();
    }
}
