/*
 * Copyright (c) 2017 Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL GLUON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.charm.down.plugins.android;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Patterns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import com.gluonhq.impl.charm.down.plugins.DefaultVideoService;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.media.MediaPlayer.Status;
import javafxports.android.FXActivity;

public class AndroidVideoService extends DefaultVideoService implements TextureView.SurfaceTextureListener, MediaController.MediaPlayerControl  {
    
    private static final Logger LOG = Logger.getLogger(AndroidVideoService.class.getName());
    private final FXActivity activity = FXActivity.getInstance();

    private MediaPlayer mediaPlayer;
    private MediaController mediaController;
    private String videoName;
    
    private final FrameLayout frameLayout;
    private final TextureView textureView;
    private boolean showing;
    private final BooleanProperty ready = new SimpleBooleanProperty();
    
    private boolean looping;
    private boolean controlsVisible;

    private final AudioManager audioManager;

    private final int maxVolume;
    private int preMuteVolume = 0;
    private int currentVolume = 0;
    
    private final ReadOnlyObjectWrapper<Status> status;
    private final BooleanProperty fullScreen;
    private final IntegerProperty currentIndex;
    
    private Pos alignment = Pos.CENTER;
    private double topPadding = 0;
    private double rightPadding = 0;
    private double bottomPadding = 0;
    private double leftPadding = 0;
    private double mediaHeight = 0;
    private double mediaWidth = 0;
    
    private boolean isVideo;
    
    private FileInputStream fis;
    
    private final ChangeListener<Boolean> readyListener = new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> obs, Boolean ov, Boolean nv) {
                if (nv) {
                    ready.removeListener(this);
                    if (debug) {
                        LOG.log(Level.INFO, String.format("Video start playing [%d/%d]: %s", (currentIndex.get() + 1), playlist.size(), videoName));
                    }
                    play();
                }
            }
        };
    
    private final ChangeListener<Number> indexListener = new ChangeListener<Number>() {
        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            pause();
            int index = newValue.intValue();
            if (0 <= index && index < playlist.size()) {
                nextMedia(index);
            } else if (looping) {
                currentIndex.set(0); // rewind
            } else {
                nextMedia(-1); // stop
            }
        }
    };
            
    public AndroidVideoService() {
        super();
        
        status = new ReadOnlyObjectWrapper<>(Status.UNKNOWN);
        status.addListener((obs, ov, nv) -> {
            switch (nv) {
                case PLAYING: setScreenOn(true); break;
                default: setScreenOn(false); break;
            }
        });
        currentIndex = new SimpleIntegerProperty();
        currentIndex.addListener(indexListener);
        
        fullScreen = new SimpleBooleanProperty() {
            @Override
            protected void invalidated() {
                updateSystemUI(get());
            }
        };
        
        playlist.addListener((Observable o) -> {
            if (playlist.isEmpty()) {
                hide();
            } else if (videoName != null) {
                
                // update current Index if any change happens
                if (! playlist.contains(videoName)) {
                    // current video was removed, restart from first item
                    if (currentIndex.get() == 0) {
                        currentIndex.set(-1);
                    }
                    // update index
                    currentIndex.set(0);
                } else {
                    int index = playlist.indexOf(videoName);
                    if (index != currentIndex.get()) {
                        currentIndex.removeListener(indexListener);
                        // update index to new position in playlist
                        currentIndex.set(index);
                        currentIndex.addListener(indexListener);
                    }
                }
            }
        });
        
        activity.setVolumeControlStream(AudioManager‌​.STREAM_MUSIC);
        audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        
        frameLayout = new FrameLayout(activity);
        textureView = new TextureView(activity) {
            
            private boolean scaling;
            private ScaleGestureDetector mScaleDetector;
                {
                    activity.runOnUiThread(() -> {
                        mScaleDetector = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            @Override
                            public void onScaleEnd(ScaleGestureDetector detector) {
                                super.onScaleEnd(detector); 
                                if (debug) {
                                    LOG.log(Level.INFO, String.format("Pinch detected with scale %f", detector.getScaleFactor()));
                                }
                                if (fullScreen.get() && detector.getScaleFactor() < 1.0) {
                                    fullScreen.set(false);
                                } else if (!fullScreen.get() && detector.getScaleFactor() > 1.0) {
                                    fullScreen.set(true);
                                }
                                scaling = false;
                            }
                            @Override
                            public boolean onScaleBegin(ScaleGestureDetector detector) {
                                scaling = true;
                                return true;
                            }
                        });
                    });
                    
                    setOnClickListener((View v) -> {
                        if (! scaling) {
                            if (controlsVisible && isVideo && mediaController != null && ! mediaController.isShowing()) {
                                mediaController.show();
                            }
                        } 
                    });
                }
            @Override
            protected void onConfigurationChanged(Configuration newConfig) {
                super.onConfigurationChanged(newConfig);
                resizeRelocateVideo();
            }
            
            @Override
            public boolean dispatchKeyEvent(final KeyEvent ke) {
                dispatchVolume(ke);
                return true;
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                requestFocus();
                mScaleDetector.onTouchEvent(event);
                return super.onTouchEvent(event);
            }
        };

        textureView.setFocusable(true);
        textureView.setFocusableInTouchMode(true);
        textureView.setSurfaceTextureListener(this);
        
        frameLayout.addView(textureView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, 
                    FrameLayout.LayoutParams.MATCH_PARENT, 
                    Gravity.CENTER)); 
        
    }

    @Override
    public void show() {
        if (playlist.isEmpty()) {
            LOG.log(Level.WARNING, "The playlist is empty");
            return;
        }
        
        if (showing) {
            if (debug) {
                LOG.log(Level.INFO, "Video layer was already added");
            }
            return;
        }
        
        videoName = playlist.get(currentIndex.get());
        ready.set(false);
        showing = true;
        
        if (prepareMedia()) {
            if (isVideo) {
                activity.runOnUiThread(() -> {
                    if (debug) {
                        LOG.log(Level.INFO, "Showing video layer");
                    }
                    FXActivity.getViewGroup().addView(frameLayout);
                    textureView.requestFocus();
                });
            } else {
                activity.runOnUiThread(() -> {
                    if (debug) {
                        LOG.log(Level.INFO, "Adding audio layer");
                    }
                    FXActivity.getViewGroup().addView(frameLayout, 0);
                    textureView.requestFocus();
                });
            }
        } else {
            if (debug) {
                LOG.log(Level.INFO, "Invalid media file found, trying the next one");
            }
            showing = false;
            ready.removeListener(readyListener);
            currentIndex.set(currentIndex.get() + 1);
        }
    }
    
    @Override
    public void play() {
        if (playlist.isEmpty()) {
            LOG.log(Level.WARNING, "The playlist is empty");
            return;
        }
        
        if (status.get() == Status.STOPPED || status.get() == Status.DISPOSED) {
            // rewind
            updateStatus(Status.UNKNOWN);
            internalHide();
            currentIndex.set(0);
        }
        
        if (! ready.get()) {
            ready.addListener(readyListener);
            if (! showing) {
                show();
            }
        } else if (mediaPlayer != null) {
            if (debug) {
                LOG.log(Level.INFO, "Video play");
            }
            updateStatus(Status.PLAYING);
            mediaPlayer.start();
        }
    }

    @Override
    public void pause() {
        if (mediaPlayer != null) {
            if (debug) {
                LOG.log(Level.INFO, "Video pause");
            }
            updateStatus(Status.PAUSED);
            mediaPlayer.pause();
        }
    }

    @Override
    public void stop() {
        if (mediaPlayer != null) {
            if (debug) {
                LOG.log(Level.INFO, "Video stop");
            }
            mediaPlayer.stop();
            updateStatus(Status.STOPPED);
        }
    }

    @Override
    public void hide() {
        internalHide();
        updateStatus(Status.DISPOSED);
    }

    @Override
    public void setPosition(Pos alignment, double topPadding, double rightPadding, double bottomPadding, double leftPadding) {
        this.alignment = alignment;
        this.topPadding = topPadding;
        this.rightPadding  = rightPadding;
        this.bottomPadding = bottomPadding;
        this.leftPadding = leftPadding;
    }

    @Override
    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    @Override
    public void setControlsVisible(boolean controlsVisible) {
        this.controlsVisible = controlsVisible;
    }

    @Override
    public void setFullScreen(boolean fullScreen) {
        this.fullScreen.set(fullScreen);
    }

    @Override
    public BooleanProperty fullScreenProperty() {
        return fullScreen;
    }
    
    @Override
    public ReadOnlyObjectProperty<Status> statusProperty() {
        return status.getReadOnlyProperty();
    }

    @Override
    public void setCurrentIndex(int index) {
        if (index < 0 || index >= playlist.size()) {
            if (debug) {
                LOG.log(Level.INFO, "Wrong item value");
            }
            return;
        }
        currentIndex.set(index);
    }

    @Override
    public IntegerProperty currentIndexProperty() {
        return currentIndex;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int i, int i1) {
        if (debug) {
            LOG.log(Level.INFO, "Adding surface to Media player");
        }
        Surface surface = new Surface(st);
        mediaPlayer.setSurface(surface);
        
        setupMedia();
    }
    
    private void setupMedia() {
        resizeRelocateVideo();
        
        mediaPlayer.prepareAsync();
        mediaPlayer.setOnCompletionListener(mp -> currentIndex.set(currentIndex.get() + 1));
        mediaPlayer.setOnPreparedListener(mp -> {
            if (debug) {
                LOG.log(Level.INFO, "Media player prepared and ready");
            }
            if (controlsVisible && isVideo) {
                mediaController = new MediaController(activity);
                mediaController.setMediaPlayer(this);
                mediaController.setAnchorView(textureView);

                new Handler(activity.getMainLooper()).post(() -> {
                    if (mediaController != null) {
                        mediaController.setEnabled(true);
                        mediaController.show();
                    }
                });
            }
            
            updateStatus(Status.READY);
            ready.set(true);
        });
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int i, int i1) { }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }

    @Override public void start() { play(); }
    @Override public int getDuration() { return mediaPlayer.getDuration(); }
    @Override public int getCurrentPosition() { return mediaPlayer.getCurrentPosition(); }
    @Override public void seekTo(int i) { mediaPlayer.seekTo(i); }
    @Override public boolean isPlaying() { return mediaPlayer.isPlaying(); }
    @Override public int getBufferPercentage() { return 0; }
    @Override public boolean canPause() { return true; }
    @Override public boolean canSeekBackward() { return true; }
    @Override public boolean canSeekForward() { return true; }
    @Override public int getAudioSessionId() { return 0; }

    private boolean prepareMedia() {
        try {
            if (debug) {
                LOG.log(Level.INFO, String.format("Creating new MediaPlayer for %s", videoName));
            }
            mediaPlayer = new MediaPlayer();
            
            MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
            if (Patterns.WEB_URL.matcher(videoName).matches()) {
                metaRetriever.setDataSource(videoName, new HashMap<>());
                mediaPlayer.setDataSource(activity, Uri.parse(videoName));
                if (debug) {
                    LOG.log(Level.INFO, String.format("Video file from URL: %s", Uri.parse(videoName).toString()));
                }
            } else {
                FileDescriptor fd = getFileDescriptor(videoName);
                if (fd != null) {
                    if (debug) {
                        LOG.log(Level.INFO, String.format("Got Video file from Resources: %s", fd.valid() ? "valid" : "invalid"));
                    }
                    metaRetriever.setDataSource(fd);
                    mediaPlayer.setDataSource(fd);
                    if (fis != null) {
                        fis.close();
                    }
                } else {
                    LOG.log(Level.WARNING, String.format("Invalid video file: %s", videoName));
                    updateStatus(Status.UNKNOWN);
                    mediaPlayer.release();
                    mediaPlayer = null;
                    return false;
                }
            }
            isVideo = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null;
            mediaHeight = parse(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            mediaWidth = parse(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            if (debug) {
                if (isVideo) {
                    LOG.log(Level.INFO, String.format("Video size: %f x %f", mediaWidth, mediaHeight));
                } else {
                    LOG.log(Level.INFO, "Audio file");
                }
            }
        } catch (IllegalArgumentException e) {
            LOG.log(Level.SEVERE, "Error loading content", e);
            updateStatus(Status.UNKNOWN);
            mediaPlayer.release();
            mediaPlayer = null;
            return false;
        } catch (SecurityException e) {
            LOG.log(Level.SEVERE, "Error loading content", e);
            updateStatus(Status.UNKNOWN);
            mediaPlayer.release();
            mediaPlayer = null;
            return false;
        } catch (IllegalStateException e) {
            LOG.log(Level.SEVERE, "Error loading content", e);
            updateStatus(Status.UNKNOWN);
            mediaPlayer.release();
            mediaPlayer = null;
            return false;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error loading content", e);
            updateStatus(Status.UNKNOWN);
            mediaPlayer.release();
            mediaPlayer = null;
            return false;
        }
        return true;
    }

    private void nextMedia(int index) {
        if (debug) {
            LOG.log(Level.INFO, "Hiding current video file");
        }
        internalHide();
        if (0 <= index && index < playlist.size()) {
            if (debug) {
                LOG.log(Level.INFO, String.format("Showing video file [%d/%d]", (currentIndex.get() + 1), playlist.size()));
            }
            play();
        } else {
            if (debug) {
                LOG.log(Level.INFO, "Disposing media player");
            }
            if (fullScreen.get()) {
                fullScreen.set(false);
            }
            updateStatus(Status.DISPOSED);
        }
    }
    
    private void internalHide() {
        if (mediaPlayer != null) {
            if (debug) {
                LOG.log(Level.INFO, "Media Player release");
            }
            if (mediaController != null) {
                mediaController.hide();
                mediaController = null;
            }
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        showing = false;
        if (frameLayout != null /* && isVideo  */) {
            activity.runOnUiThread(() -> {
                FXActivity.getViewGroup().removeView(frameLayout);
            });
        }
        ready.set(false);
    }

    private void resizeRelocateVideo() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        final float scaleFactor = (fullScreen.get() ? 0 : 1) * displayMetrics.scaledDensity;
        final double maxW = displayMetrics.widthPixels - (leftPadding + rightPadding) * scaleFactor;
        final double maxH = displayMetrics.heightPixels - (topPadding + bottomPadding) * scaleFactor;
        final double screenFactor = maxW / maxH;
        int w, h;
        if (isVideo) {
            final double mediaFactor = mediaWidth / mediaHeight;
            if (mediaFactor > screenFactor) {
                w = (int) maxW;
                h = (int) (maxW / mediaFactor);
            } else {
                w = (int) (maxH * mediaFactor);
                h = (int) maxH;
            }
        } else {
            w = (int) maxW;
            h = (int) Math.min(maxH, 300);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.CENTER);
        if (! fullScreen.get()) {
            switch (alignment) {
                case TOP_LEFT: lp.gravity = Gravity.TOP + Gravity.LEFT; break;
                case TOP_CENTER: lp.gravity = Gravity.TOP + Gravity.CENTER_HORIZONTAL; break;
                case TOP_RIGHT: lp.gravity = Gravity.TOP + Gravity.RIGHT; break;
                case CENTER_LEFT: lp.gravity = Gravity.CENTER + Gravity.LEFT; break;
                case CENTER: lp.gravity = Gravity.CENTER; break;
                case CENTER_RIGHT: lp.gravity = Gravity.CENTER + Gravity.RIGHT; break;
                case BOTTOM_LEFT: lp.gravity = Gravity.BOTTOM + Gravity.LEFT; break;
                case BOTTOM_CENTER: lp.gravity = Gravity.BOTTOM + Gravity.CENTER_HORIZONTAL; break;
                case BOTTOM_RIGHT: lp.gravity = Gravity.BOTTOM + Gravity.RIGHT; break;
            }
        }
        lp.setMargins((int) (leftPadding * scaleFactor), (int) (topPadding * scaleFactor), 
                (int) (rightPadding * scaleFactor), (int) (bottomPadding * scaleFactor));

        textureView.setLayoutParams(lp);
        if (debug) {
            LOG.log(Level.INFO, String.format("Media margins: %d %d %d %d", lp.topMargin, lp.rightMargin, lp.bottomMargin, lp.leftMargin));
        }
        
        frameLayout.setBackgroundColor(fullScreen.get() ? Color.BLACK : Color.TRANSPARENT);
        
        if (fullScreen.get() && ! isVideo) {
            if (debug) {
                LOG.log(Level.INFO, "Audio file doesn't allow full screen mode");
            }
            Platform.runLater(() -> setFullScreen(false));
        }
    }
    
    private void updateSystemUI(boolean fullScreen) {
        if (mediaPlayer == null) { 
            if (debug) {
                LOG.log(Level.INFO, "No media player found");
            }
            Platform.runLater(() -> setFullScreen(false));
            return;
        }
        if (fullScreen && ! isVideo) {
            if (debug) {
                LOG.log(Level.INFO, "Audio file doesn't allow full screen mode");
            }
            Platform.runLater(() -> setFullScreen(false));
            return;
        }
        
        activity.runOnUiThread(() -> {
            if (fullScreen) {
                if (debug) {
                    LOG.log(Level.INFO, "Entering full screen mode");
                }
                activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            } else {
                if (debug) {
                    LOG.log(Level.INFO, "Exiting full screen mode");
                }
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            resizeRelocateVideo();
        });
    }
    
    private void setScreenOn(boolean on) {
        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();
            if (on) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private double parse(String s) {
        if (s == null || s.isEmpty()) {
            return 0d;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException nfe) {}
        return 0d;
    }
    
    private void updateStatus(Status status) {
        if (this.status.get() != status) {
            Platform.runLater(() -> this.status.set(status));
        }
    }
    
    private void dispatchVolume(final KeyEvent ke) {
        if (ke.getAction() == KeyEvent.ACTION_DOWN) {
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            switch (ke.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            (currentVolume + 1) <= maxVolume ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                    break;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            (currentVolume - 1) >= 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                    break;
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                    if (currentVolume > 0) {
                        preMuteVolume = currentVolume;
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME,
                                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE | AudioManager.FLAG_SHOW_UI);
                    } else {
                        preMuteVolume = 0;
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, preMuteVolume, AudioManager.FLAG_SHOW_UI);
                    }
                    break;
                default: break;
            }
        }
    }
    
    private FileDescriptor getFileDescriptor(String filePath) {
        if (debug) {
            LOG.log(Level.INFO, String.format("Finding file descriptor for video file: %s", filePath));
        }
        try {
            if (playlistMap.get(filePath) == null) {
                // this shouldn't happen
                if (debug) {
                    LOG.log(Level.INFO, String.format("Video file: %s not processed yet", filePath));
                }
                return null;
            }
            if (debug) {
                LOG.log(Level.INFO, String.format("Got file for video file: %s -> %s", filePath, playlistMap.get(filePath) ? "yes" : "no"));
            }
            if (! playlistMap.get(filePath)) {
                if (debug) {
                    LOG.log(Level.INFO, String.format("Video file: %s not found", filePath));
                }
                return null;
            }
            File videoFile = getFileFromAssets(filePath);
            if (! videoFile.exists()) {
                return null;
            }
            fis = new FileInputStream(videoFile);
            return fis.getFD();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error getting file descriptor", ex);
        }
        return null;
    }
    
}
