package org.voxeleers.game.gui.buttons;

import org.voxeleers.game.gui.GUI;

public class SettingsButton extends Button {
    public SettingsButton() {}

    @Override
    public void clicked() {
        GUI.settingMenuOpen = true;
    }
}
