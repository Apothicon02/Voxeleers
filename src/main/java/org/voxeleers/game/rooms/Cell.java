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
}
