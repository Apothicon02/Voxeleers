package org.voxeleers.game.rooms;

import java.util.ArrayList;
import java.util.List;

public class Cell {
    public int energy;
    public List<Molecule> molecules;
    public Cell() {
        energy = 0;
        molecules = new ArrayList<>(List.of());
    }
    public Cell(int energy, List<Molecule> molecules) {
        this.energy = energy;
        this.molecules = molecules;
    }
    public Cell(Cell cell) {
        energy = cell.energy;
        molecules = new ArrayList<>(cell.molecules);
    }
}
