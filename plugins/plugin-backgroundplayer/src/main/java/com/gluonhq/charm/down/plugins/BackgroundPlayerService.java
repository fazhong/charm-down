package com.gluonhq.charm.down.plugins;

public interface BackgroundPlayerService {
    void playAudio(String audioName);
    void pauseAudio();
    void resumeAudio();
    void stopAudio();

}
