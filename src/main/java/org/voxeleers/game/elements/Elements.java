package org.voxeleers.game.elements;

import java.util.ArrayList;

public class Elements {
    public static double UGC = 8.31446261815324;
    public static ArrayList<Element> elementMap = new ArrayList<>();

    public static Element
            OXYGEN = create(new Element("Oxygen", 0.918f)),
            CARBON_DIOXIDE = create(new Element("Carbon Dioxide", 0.839f)),
            NITROGEN = create(new Element("Nitrogen", 1.04f)),
            ARGON = create(new Element("Argon", 0.5203f));

    public static Element create(Element element) {
        elementMap.addLast(element);
        return element;
    }
}
