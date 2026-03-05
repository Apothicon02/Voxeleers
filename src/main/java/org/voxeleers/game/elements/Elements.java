package org.voxeleers.game.elements;

import java.util.ArrayList;

public class Elements {
    public static ArrayList<Element> elementMap = new ArrayList<>();

    public static Element
            OXYGEN = create(new Element("Oxygen", 1.f)),
            CARBON_DIOXIDE = create(new Element("Carbon Dioxide", 1.1f));

    public static Element create(Element element) {
        elementMap.addLast(element);
        return element;
    }
}
