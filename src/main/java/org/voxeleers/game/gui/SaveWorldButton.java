package org.voxeleers.game.gui;

import org.voxeleers.Main;

public class SaveWorldButton extends Button {
    public SaveWorldButton() {}

    @Override
    public void clicked() {
        Main.isSaving = true;
    }
}
