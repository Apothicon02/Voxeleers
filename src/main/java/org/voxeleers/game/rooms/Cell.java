package org.voxeleers.game.rooms;

import org.voxeleers.game.elements.Element;
import org.voxeleers.game.elements.Elements;

import java.util.ArrayList;
import java.util.List;

import static org.voxeleers.game.elements.Elements.UGC;

public class Cell {
    public long energy;
    public ArrayList<Molecule> molecules;
    public Cell() {
        energy = 0;
        molecules = new ArrayList<>(List.of());
    }
    public Cell(long energy, ArrayList<Molecule> molecules) {
        this.energy = energy;
        this.molecules = molecules;
    }
    public Cell(long energy, List<Molecule> molecules) {
        this.energy = energy;
        this.molecules = new ArrayList<>(molecules);
    }
    public Cell(Cell cell) {
        energy = cell.energy;
        molecules = new ArrayList<>();
        for (Molecule molecule : cell.molecules) {
            molecules.add(new Molecule(molecule));
        }
    }

    public double getPressure() {
        double mass = 0;
        double thermalMass = 0;
        for (Molecule molecule : molecules) {
            Element element = Elements.elementMap.get(molecule.element);
            mass += molecule.amount;
            thermalMass += element.specificHeat*molecule.amount;
        }
        double temperature = energy/thermalMass;
        return mass*UGC*temperature;
    }

    public double getMolesFromPressure(double pressure) {
        return pressure/(UGC*getTemperature());
    }

    public double getTemperature() {
        double mass = 0;
        for (Molecule molecule : molecules) {
            Element element = Elements.elementMap.get(molecule.element);
            mass += element.specificHeat*molecule.amount;
        }
        return energy/mass;
    }

    public long getEnergyFromTemperature(double temp) {
        double mass = 0;
        for (Molecule molecule : molecules) {
            Element element = Elements.elementMap.get(molecule.element);
            mass += element.specificHeat*molecule.amount;
        }
        return (long) (temp*mass);
    }
}
