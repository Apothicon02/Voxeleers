package org.voxeleers.game.rooms;

import java.util.ArrayList;
import java.util.List;

public class Cell {
    public ArrayList<Molecule> molecules;
    public Cell() {
        molecules = new ArrayList<>(List.of());
    }
    public Cell(ArrayList<Molecule> molecules) {
        this.molecules = molecules;
    }
    public Cell(List<Molecule> molecules) {
        this.molecules = new ArrayList<>(molecules);
    }
    public Cell(Cell cell) {
        molecules = new ArrayList<>();
        for (Molecule molecule : cell.molecules) {
            molecules.add(new Molecule(molecule));
        }
    }
}
