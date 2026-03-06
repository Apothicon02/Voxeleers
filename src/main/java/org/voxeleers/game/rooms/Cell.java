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
        float mass = 0;
        for (Molecule molecule : molecules) {
            Element element = Elements.elementMap.get(molecule.element);
            mass += element.specificHeat*molecule.amount;
        }
        return energy/(mass/10.f);
    }

    public int getEnergyFromTemperature(float temp) {
        float mass = 0;
        for (Molecule molecule : molecules) {
            Element element = Elements.elementMap.get(molecule.element);
            mass += element.specificHeat*molecule.amount;
        }
        return (int) (temp*(mass/10.f));
    }
}
