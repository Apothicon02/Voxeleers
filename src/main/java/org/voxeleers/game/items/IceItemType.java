package org.voxeleers.game.items;

import org.voxeleers.game.elements.Element;

public class IceItemType extends ItemType {
    public Element element = null;
    public IceItemType(String name) {
        super(name);
    }

    public IceItemType element(Element element) {
        this.element = element;
        element.iceItemType = this;
        return this;
    }

    @Override
    public IceItem createItem() {
        return (IceItem) new IceItem().type(this);
    }
}
