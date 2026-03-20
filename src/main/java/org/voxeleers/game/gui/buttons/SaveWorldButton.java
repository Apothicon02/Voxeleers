package org.voxeleers.game.gui.buttons;

import org.voxeleers.Main;
import org.voxeleers.game.audio.AudioController;

public class SaveWorldButton extends Button {
    public SaveWorldButton() {}

    @Override
    public void clicked() {
        Main.isSaving = true;
        AudioController.playButtonSound();
    }
}
