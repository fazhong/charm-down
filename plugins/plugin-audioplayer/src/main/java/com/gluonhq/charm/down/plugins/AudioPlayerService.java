package com.gluonhq.charm.down.plugins;

public interface AudioPlayerService {
    void playAudio(String audioName);
    void pauseAudio();
    void resumeAudio();
    void stopAudio();

}
