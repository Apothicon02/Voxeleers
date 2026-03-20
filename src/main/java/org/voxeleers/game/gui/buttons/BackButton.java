package org.voxeleers.game.gui.buttons;

import org.voxeleers.game.audio.AudioController;
import org.voxeleers.game.gui.GUI;

public class BackButton extends Button {
    public BackButton() {}

    @Override
    public void clicked() {
        if (GUI.accessibilitySettingMenuOpen) {
            GUI.accessibilitySettingMenuOpen = false;
        } else if (GUI.graphicsSettingMenuOpen) {
            GUI.graphicsSettingMenuOpen = false;
        } else if (GUI.controlsSettingMenuOpen) {
            GUI.controlsSettingMenuOpen = false;
        } else if (GUI.audioSettingMenuOpen) {
            GUI.audioSettingMenuOpen = false;
        } else if (GUI.settingMenuOpen) {
            GUI.settingMenuOpen = false;
        } else {
            GUI.pauseMenuOpen = false;
        }
        AudioController.playButtonSound();
    }
}
