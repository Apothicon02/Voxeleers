package org.voxeleers.game.elements;

import org.voxeleers.game.items.ItemType;

public class Element {
    public double specificHeat;
    public int freezingTemp;
    public ItemType iceItemType = null;
    public String name;

    public Element(String name, double specificHeat, int freezingTemp) {
        this.name = name;
        this.specificHeat = specificHeat;
        this.freezingTemp = freezingTemp;
    }
}
