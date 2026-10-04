package com.thor.displaypowertest;

import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

/**
 * Background art and the muted looping video. The video plays only while the
 * activity is resumed and the confirmed display state shows both screens on.
 * All methods run on the UI thread.
 */
final class BackgroundMediaController implements TextureView.SurfaceTextureListener {
    private final DashboardViews views;
    private final ImageView backgroundImage;
    private final TextureView videoTexture;
    private MediaPlayer backgroundPlayer;
    private Surface videoSurface;
    private boolean videoPrepared;
    private boolean resumed;
    private boolean visualBottomOn = true;
    private DashboardStateModel.Visual displayVisual = DashboardStateModel.Visual.BOTH_ON;

    BackgroundMediaController(DashboardViews views, ImageView backgroundImage,
            TextureView videoTexture) {
        this.views = views;
        this.backgroundImage = backgroundImage;
        this.videoTexture = videoTexture;
        videoTexture.setSurfaceTextureListener(this);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        startBackgroundVideo(texture);
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {}

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        release();
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}

    void onResume() {
        resumed = true;
        resumeBackgroundVideo();
    }

    void onPause() {
        resumed = false;
        if (backgroundPlayer != null && backgroundPlayer.isPlaying()) backgroundPlayer.pause();
    }

    void setVisual(DashboardStateModel.Visual visual) {
        if (displayVisual == visual) return;
        displayVisual = visual;
        visualBottomOn = visual == DashboardStateModel.Visual.BOTH_ON;
        String resourceName;
        if (visual == DashboardStateModel.Visual.BOTH_ON) {
            resourceName = "jesty_thor_background";
        } else if (visual == DashboardStateModel.Visual.AYN_FAKE_OFF) {
            resourceName = "jesty_thor_background_fake_off";
        } else {
            resourceName = "jesty_thor_background_true_off";
        }
        backgroundImage.setImageResource(views.resource("drawable", resourceName));
        if (visualBottomOn) {
            resumeBackgroundVideo();
        } else {
            videoTexture.setVisibility(View.GONE);
            if (backgroundPlayer != null && backgroundPlayer.isPlaying()) backgroundPlayer.pause();
        }
    }

    void release() {
        videoPrepared = false;
        if (backgroundPlayer != null) {
            try { backgroundPlayer.stop(); } catch (Throwable ignored) {}
            backgroundPlayer.release();
            backgroundPlayer = null;
        }
        if (videoSurface != null) { videoSurface.release(); videoSurface = null; }
    }

    private void startBackgroundVideo(SurfaceTexture texture) {
        release();
        try {
            AssetFileDescriptor source = views.activity().getResources().openRawResourceFd(
                    views.resource("raw", "jesty_thor_background_loop"));
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(source.getFileDescriptor(), source.getStartOffset(), source.getLength());
            source.close();
            videoSurface = new Surface(texture);
            player.setSurface(videoSurface);
            player.setLooping(true);
            player.setVolume(0f, 0f);
            player.setOnPreparedListener(prepared -> {
                videoPrepared = true;
                if (resumed && visualBottomOn) prepared.start();
            });
            player.setOnErrorListener((failed, what, extra) -> {
                videoTexture.setVisibility(View.GONE);
                release();
                return true;
            });
            backgroundPlayer = player;
            player.prepareAsync();
        } catch (Throwable ignored) {
            videoTexture.setVisibility(View.GONE);
            release();
        }
    }

    private void resumeBackgroundVideo() {
        if (!visualBottomOn) {
            videoTexture.setVisibility(View.GONE);
            return;
        }
        videoTexture.setVisibility(View.VISIBLE);
        if (backgroundPlayer == null && videoTexture.isAvailable()) {
            startBackgroundVideo(videoTexture.getSurfaceTexture());
        } else if (resumed && videoPrepared && !backgroundPlayer.isPlaying()) {
            backgroundPlayer.start();
        }
    }
}
