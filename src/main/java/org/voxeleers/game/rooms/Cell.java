package org.voxeleers.game.rooms;

import org.voxeleers.game.elements.Element;
import org.voxeleers.game.elements.Elements;

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

    public float getTemperature() {
        int mass = 0;
        float avgSpecificHeat = 0.f;
        int i = 0;
        for (Molecule molecule : molecules) {
            Element element = Elements.elementMap.get(molecule.element);
            avgSpecificHeat += element.specificHeat;
            mass += molecule.amount;
            i++;
        }
        avgSpecificHeat/=i;
        return energy/(mass*avgSpecificHeat);
    }
}
