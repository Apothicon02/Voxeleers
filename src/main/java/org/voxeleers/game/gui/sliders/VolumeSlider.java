package org.voxeleers.game.gui.sliders;

import org.voxeleers.game.audio.AudioController;

public class VolumeSlider extends Slider {
    public VolumeSlider() {}

    @Override
    public void clicked(int cursorX) {
        AudioController.masterVolume = Math.abs(((float) (bounds.x()-cursorX)) / (bounds.z()-bounds.x()))*2;
    }
}
