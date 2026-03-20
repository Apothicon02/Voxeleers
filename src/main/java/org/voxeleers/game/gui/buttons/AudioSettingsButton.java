package org.voxeleers.game.gui.buttons;

import org.voxeleers.game.gui.GUI;

public class AudioSettingsButton extends Button {
    public AudioSettingsButton() {}

    @Override
    public void clicked() {
        GUI.audioSettingMenuOpen = true;
    }
}
