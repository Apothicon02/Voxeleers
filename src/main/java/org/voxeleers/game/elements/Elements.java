package org.voxeleers.game.elements;

import java.util.ArrayList;

public class Elements {
    public static ArrayList<Element> elementMap = new ArrayList<>();

    public static Element
            OXYGEN = create(new Element("Oxygen")),
            CARBON_DIOXIDE = create(new Element("Carbon Dioxide")),
            NITROGEN = create(new Element("Nitrogen")),
            ARGON = create(new Element("Argon"));

    public static Element create(Element element) {
        elementMap.addLast(element);
        return element;
    }
}
