package org.voxeleers.game.gui.buttons;

import org.voxeleers.game.audio.AudioController;

public class QuitToMenuButton extends Button {
    public QuitToMenuButton() {}

    @Override
    public void clicked() {
        AudioController.playButtonSound();
    }
}
