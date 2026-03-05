package org.voxeleers.game.rooms;

import java.util.ArrayList;
import java.util.List;

public class Cell {
    public int energy;
    public List<Molecule> molecules;
    public Cell() {
        energy = 1000;
        molecules = new ArrayList<>(List.of(new Molecule(), new Molecule(1, 18)));
    }
}
