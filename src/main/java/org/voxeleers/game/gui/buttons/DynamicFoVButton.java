package org.voxeleers.game.gui.buttons;

import org.voxeleers.game.Settings;

public class DynamicFoVButton extends Button {
    public DynamicFoVButton() {}

    @Override
    public void clicked() {
        Settings.dynamicFoV = !Settings.dynamicFoV;
    }
}
