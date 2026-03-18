package org.voxeleers.game.gui;

import org.voxeleers.game.audio.AudioController;

public class MuteButton extends Button {
    public MuteButton() {}

    @Override
    public void clicked() {
        AudioController.muted = !AudioController.muted;
    }
}
