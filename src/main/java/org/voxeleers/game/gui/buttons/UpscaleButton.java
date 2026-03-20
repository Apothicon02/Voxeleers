package org.voxeleers.game.gui.buttons;

import org.voxeleers.game.audio.AudioController;
import org.voxeleers.game.rendering.Renderer;

public class UpscaleButton extends Button {
    public UpscaleButton() {}

    @Override
    public void clicked() {
        Renderer.upscale = !Renderer.upscale;
        AudioController.playButtonSound();
    }
}
