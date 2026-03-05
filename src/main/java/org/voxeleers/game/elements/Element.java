package org.voxeleers.game.elements;

public class Element {
    public float specificHeat;
    public String name;

    public Element(String name) {
        this.name = name;
        specificHeat = 1.f;
    }
    public Element(String name, float specificHeat) {
        this.name = name;
        this.specificHeat = specificHeat;
    }
}
