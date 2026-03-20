package org.voxeleers.game.gui.buttons;

import org.voxeleers.game.gui.GUI;

public class GraphicsSettingsButton extends Button {
    public GraphicsSettingsButton() {}

    @Override
    public void clicked() {GUI.graphicsSettingMenuOpen = true;}
}
