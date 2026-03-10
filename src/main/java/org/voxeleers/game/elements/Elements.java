package org.voxeleers.game.elements;

import java.util.ArrayList;

public class Elements {
    public static double UGC = 8.31446261815324;
    public static ArrayList<Element> elementMap = new ArrayList<>();

    public static Element
            OXYGEN = create(new Element("Oxygen", 0.918d, 54)),
            CARBON_DIOXIDE = create(new Element("Carbon Dioxide", 0.839d, 194)),
            NITROGEN = create(new Element("Nitrogen", 1.04d, 63)),
            ARGON = create(new Element("Argon", 0.5203d, 83));

    public static Element create(Element element) {
        elementMap.addLast(element);
        return element;
    }
}
