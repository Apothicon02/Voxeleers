package org.voxeleers.game.gui.buttons;

import org.voxeleers.Main;
import org.voxeleers.game.audio.AudioController;

public class QuitToDesktopButton extends Button {
    public QuitToDesktopButton() {}

    @Override
    public void clicked() {
        Main.isClosing = true;
        AudioController.playButtonSound();
    }
}
