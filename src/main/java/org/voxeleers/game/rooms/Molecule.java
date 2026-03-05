package org.voxeleers.game.rooms;

public class Molecule {
    public byte element;
    public int amount;
    public Molecule() {
        element = 0;
        amount = 1;
    }
    public Molecule(int element, int amount) {
        this.element = (byte)(element);
        this.amount = amount;
    }
}
