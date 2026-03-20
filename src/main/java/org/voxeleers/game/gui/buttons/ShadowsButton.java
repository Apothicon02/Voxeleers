package org.voxeleers.game.gui.buttons;

import org.voxeleers.game.audio.AudioController;
import org.voxeleers.game.rendering.Renderer;

public class ShadowsButton extends Button {
    public ShadowsButton() {}

    @Override
    public void clicked() {
        Renderer.shadowsEnabled = !Renderer.shadowsEnabled;
        AudioController.playButtonSound();
    }
}
