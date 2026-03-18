package org.voxeleers.game.gui;

public class AudioSettingsButton extends Button {
    public AudioSettingsButton() {}

    @Override
    public void clicked() {
        GUI.audioSettingMenuOpen = true;
    }
}
