package org.voxeleers.game.gui.buttons;

import org.voxeleers.game.audio.AudioController;
import org.voxeleers.game.gui.GUI;

public class ControlsSettingsButton extends Button {
    public ControlsSettingsButton() {}

    @Override
    public void clicked() {
        GUI.controlsSettingMenuOpen = true;
        AudioController.playButtonSound();
    }
}
