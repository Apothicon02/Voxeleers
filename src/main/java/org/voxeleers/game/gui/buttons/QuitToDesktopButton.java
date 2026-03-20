package org.voxeleers.game.gui.buttons;

import org.voxeleers.Main;

public class QuitToDesktopButton extends Button {
    public QuitToDesktopButton() {}

    @Override
    public void clicked() {
        Main.isClosing = true;
    }
}
