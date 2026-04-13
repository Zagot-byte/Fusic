package dev.fusic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bumptech.glide.Glide;
import dev.fusic.app.R;

public class MediaPlayerService extends Service {

    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_RESUME = "resume";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_SEEK = "seek";
    public static final String ACTION_NEXT = "next";
    public static final String ACTION_PREV = "prev";

    public static final String BROADCAST_STATE = "fusic.player.state";

    private MediaSessionCompat mediaSession;
    private AudioPlayer audioPlayer;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());

    private String currentTitle = "";
    private String currentArtist = "";
    private String currentAlbum = "";
    private String currentThumbnailUrl = "";
    private Bitmap currentArtBitmap = null;

    private boolean isPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSession = new MediaSessionCompat(this, "MediaPlayerService");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() { handleCommand(ACTION_RESUME, null); }
            @Override
            public void onPause() { handleCommand(ACTION_PAUSE, null); }
            @Override
            public void onSkipToNext() { sendActionToWeb(ACTION_NEXT); }
            @Override
            public void onSkipToPrevious() { sendActionToWeb(ACTION_PREV); }
            @Override
            public void onSeekTo(long pos) {
                Intent i = new Intent();
                i.putExtra("position", pos);
                handleCommand(ACTION_SEEK, i);
            }
        });
        mediaSession.setActive(true);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("action")) {
            handleCommand(intent.getStringExtra("action"), intent);
        }
        return START_STICKY;
    }

    private void handleCommand(String action, Intent intent) {
        if (action == null) return;
        switch (action) {
            case ACTION_PLAY:
                if (intent != null) {
                    currentTitle = intent.getStringExtra("title");
                    currentArtist = intent.getStringExtra("artist");
                    currentAlbum = intent.getStringExtra("album");
                    currentThumbnailUrl = intent.getStringExtra("thumbnailUrl");
                    String url = intent.getStringExtra("url");
                    if (url != null) {
                        if (audioPlayer != null) audioPlayer.stop();
                        audioPlayer = new AudioPlayer(this, url);
                        audioPlayer.init();
                        isPlaying = false;
                        loadAlbumArtAndShowNotification();
                    }
                }
                break;
            case ACTION_PAUSE:
                if (audioPlayer != null) {
                    audioPlayer.pause();
                    isPlaying = false;
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                    showNotification();
                    broadcastState("paused");
                    stopProgressUpdate();
                }
                break;
            case ACTION_RESUME:
                if (audioPlayer != null) {
                    audioPlayer.play();
                    isPlaying = true;
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                    showNotification();
                    broadcastState("playing");
                    startProgressUpdate();
                }
                break;
            case ACTION_STOP:
                if (audioPlayer != null) {
                    audioPlayer.stop();
                    isPlaying = false;
                }
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
                stopForeground(true);
                broadcastState("stopped");
                stopProgressUpdate();
                break;
            case ACTION_SEEK:
                if (audioPlayer != null && intent != null) {
                    long pos = intent.getLongExtra("position", 0);
                    audioPlayer.seek(pos);
                }
                break;
            case ACTION_NEXT:
                sendActionToWeb(ACTION_NEXT);
                break;
            case ACTION_PREV:
                sendActionToWeb(ACTION_PREV);
                break;
        }
    }

    public void onPrepared() {
        audioPlayer.play();
        isPlaying = true;
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
        showNotification();
        broadcastState("playing");
        startProgressUpdate();
    }

    public void onCompletion() {
        isPlaying = false;
        broadcastState("completed");
        stopProgressUpdate();
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
    }

    public void onError(String msg) {
        isPlaying = false;
        Intent intent = new Intent(BROADCAST_STATE);
        intent.putExtra("error", msg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void updatePlaybackState(int state) {
        long position = audioPlayer != null ? audioPlayer.getPosition() : PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(state, position, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void loadAlbumArtAndShowNotification() {
        new Thread(() -> {
            try {
                if (currentThumbnailUrl != null && !currentThumbnailUrl.isEmpty()) {
                    currentArtBitmap = Glide.with(getApplicationContext())
                            .asBitmap()
                            .load(currentThumbnailUrl)
                            .submit()
                            .get();
                } else {
                    currentArtBitmap = null;
                }
            } catch (Exception e) {
                currentArtBitmap = null;
            }
            updateMediaMetadata();
            showNotification();
        }).start();
    }

    private void updateMediaMetadata() {
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentAlbum);
        
        if (currentArtBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArtBitmap);
        }
        if (audioPlayer != null) {
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, audioPlayer.getDuration());
        }
        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void showNotification() {
        int playPauseIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String playPauseTitle = isPlaying ? "Pause" : "Play";
        String playPauseAction = isPlaying ? ACTION_PAUSE : ACTION_RESUME;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "fusic_playback")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setLargeIcon(currentArtBitmap)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .addAction(android.R.drawable.ic_media_previous, "Previous", buildActionPendingIntent(ACTION_PREV))
                .addAction(playPauseIcon, playPauseTitle, buildActionPendingIntent(playPauseAction))
                .addAction(android.R.drawable.ic_media_next, "Next", buildActionPendingIntent(ACTION_NEXT));

        Notification notification = builder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, notification);
        }
    }

    private PendingIntent buildActionPendingIntent(String action) {
        Intent intent = new Intent(this, MediaPlayerService.class);
        intent.putExtra("action", action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, action.hashCode(), intent, flags);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "fusic_playback",
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.channel_desc));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void broadcastState(String state) {
        Intent intent = new Intent(BROADCAST_STATE);
        intent.putExtra("state", state);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendActionToWeb(String action) {
        Intent intent = new Intent(BROADCAST_STATE);
        intent.putExtra("mediaAction", action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying && audioPlayer != null) {
                Intent intent = new Intent(BROADCAST_STATE);
                intent.putExtra("type", "progress");
                intent.putExtra("position", audioPlayer.getPosition());
                intent.putExtra("duration", audioPlayer.getDuration());
                LocalBroadcastManager.getInstance(MediaPlayerService.this).sendBroadcast(intent);
            }
            if (isPlaying) {
                progressHandler.postDelayed(this, 1000);
            }
        }
    };

    private void startProgressUpdate() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdate() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopProgressUpdate();
        if (audioPlayer != null) audioPlayer.stop();
        mediaSession.release();
    }
}
