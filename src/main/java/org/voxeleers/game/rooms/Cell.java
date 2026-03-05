package org.voxeleers.game.rooms;

public class Cell {
    public int temperature;
    public Molecule[] molecule;
    public Cell() {
        temperature = 300;
        molecule = new Molecule[]{new Molecule()};
    }
}
