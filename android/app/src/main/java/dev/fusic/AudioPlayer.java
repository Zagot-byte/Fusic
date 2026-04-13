package dev.fusic;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import java.io.IOException;

public class AudioPlayer {
    private MediaPlayer mediaPlayer;
    private final MediaPlayerService service;
    private final String url;
    private boolean isPrepared = false;

    public AudioPlayer(MediaPlayerService service, String url) {
        this.service = service;
        this.url = url;
    }

    public void init() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(
            new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        );

        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                mediaPlayer.setDataSource(url);
            } else {
                mediaPlayer.setDataSource(service, Uri.parse(url));
            }
            mediaPlayer.setOnPreparedListener(mp -> {
                isPrepared = true;
                service.onPrepared();
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                service.onCompletion();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                service.onError("MediaPlayer error " + what + " " + extra);
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            service.onError(e.getMessage());
        }
    }

    public void play() {
        if (isPrepared && mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    public void pause() {
        if (isPrepared && mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            isPrepared = false;
        }
    }

    public void seek(long ms) {
        if (isPrepared && mediaPlayer != null) {
            mediaPlayer.seekTo((int) ms);
        }
    }

    public long getDuration() {
        return (isPrepared && mediaPlayer != null) ? mediaPlayer.getDuration() : 0;
    }

    public long getPosition() {
        return (isPrepared && mediaPlayer != null) ? mediaPlayer.getCurrentPosition() : 0;
    }

    public void setVolume(float vol) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(vol, vol);
        }
    }
}
