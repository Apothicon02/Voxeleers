package org.voxeleers.game.items;

import org.voxeleers.game.elements.Element;
import org.voxeleers.game.elements.Elements;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Molecule;
import org.voxeleers.game.rooms.Room;
import org.voxeleers.game.rooms.Rooms;
import org.voxeleers.game.world.World;

public class IceItem extends Item implements Cloneable {
    @Override
    public void tick() {
        long time = System.currentTimeMillis();
        if (prevTickTime != 0) {
            long dif = time - prevTickTime;
            timeExisted += dif;
            rot += (dif / 50f) * Math.random();
            if (rot >= 360) {
                rot = 0;
            }
            double hoverInc = (dif / 1750f) * Math.min(Math.max(0.01f, 0.1f - hover) * 10, Math.max(0.01f, 0.1f - Math.abs(hover - 0.1f)) * 10) * Math.random();
            if (hoverMeridiem) {
                hover += hoverInc;
                if (hover >= 0.1) {
                    hover = 0.1f;
                    hoverMeridiem = false;
                }
            } else {
                hover -= hoverInc;
                if (hover < 0.f) {
                    hover = 0.f;
                    hoverMeridiem = true;
                }
            }
            int xyz = Rooms.packCellPos((int) pos.x(), (int) pos.y(), (int) pos.z());
            Room room = Rooms.getRoom(xyz);
            if (room != null) {
                Cell cell = room.cells.get(xyz);
                Element element = ((IceItemType) this.type).element;
                double temp = cell.getTemperature() * 1000;
                if (temp > element.freezingTemp) {
                    int elementId = Elements.elementMap.indexOf(element);
                    Molecule molecule = null;
                    for (Molecule cellMolecule : cell.molecules) {
                        if (cellMolecule.element == elementId) {
                            molecule = cellMolecule;
                            break;
                        }
                    }
                    if (molecule == null) {
                        molecule = new Molecule(elementId, 1);
                    } else {
                        molecule.amount++;
                    }
                    this.amount--;
                    if (this.amount <= 0) {
                        World.items.remove(this);
                    }
                }
            }
        }
        prevTickTime = time;
    }

    @Override
    public IceItem clone() {
        return (IceItem) super.clone();
    }
}
