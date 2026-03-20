package org.voxeleers.game.gui.buttons;

import org.voxeleers.game.audio.AudioController;
import org.voxeleers.game.rendering.Renderer;

public class TAAButton extends Button {
    public TAAButton() {}

    @Override
    public void clicked() {
        Renderer.taa = !Renderer.taa;
        AudioController.playButtonSound();
    }
}
