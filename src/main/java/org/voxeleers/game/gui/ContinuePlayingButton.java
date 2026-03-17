package org.voxeleers.game.gui;

public class ContinuePlayingButton extends Button {
    public ContinuePlayingButton() {}

    @Override
    public void clicked() {
        GUI.pauseMenuOpen = false;
    }
}
