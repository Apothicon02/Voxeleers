package org.voxeleers.game.blocks.entities;

import org.joml.Vector2i;
import org.joml.Vector3i;
import org.voxeleers.game.elements.Elements;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Molecule;
import org.voxeleers.game.rooms.Room;
import org.voxeleers.game.rooms.Rooms;
import org.voxeleers.game.world.Directions;
import org.voxeleers.game.world.World;

public class PoweredVentBlockEntity extends BlockEntity {
    public PoweredVentBlockEntity() {}

    @Override
    public BlockEntity create() {
        return new PoweredVentBlockEntity();
    }

    @Override
    public void tick(Vector2i block, Vector3i pos) {
        Vector3i dir = Directions.dirs[block.y];

        Vector3i inPos = new Vector3i(pos.x()-dir.x(), pos.y()-dir.y(), pos.z()-dir.z());
        int inXYZ = Rooms.packCellPos(inPos);
        Room inRoom = Rooms.getRoom(inXYZ);
        boolean updateIn = inRoom != null;
        Cell inCell = inRoom == null ? World.worldType.getGlobalAtmo() : inRoom.cells.get(inXYZ);

        Vector3i outPos = new Vector3i(pos.x()+dir.x(), pos.y()+dir.y(), pos.z()+dir.z());
        int outXYZ = Rooms.packCellPos(outPos);
        Room outRoom = Rooms.getRoom(outXYZ);
        boolean updateOut = outRoom != null;
        Cell outCell = outRoom == null ? World.worldType.getGlobalAtmo() : outRoom.cells.get(outXYZ);

        if (updateOut || updateIn) { // && inCell != null && outCell != null
            int cellMoles = 0;
            for (Molecule inMolecule : inCell.molecules) {
                cellMoles+=inMolecule.amount;
            }
            for (Molecule inMolecule : inCell.molecules) {
                Molecule outMolecule = null;
                for (Molecule molecule : outCell.molecules) {
                    if (inMolecule.element == molecule.element) {
                        outMolecule = molecule;
                        break;
                    }
                }
                if (outMolecule == null) {
                    outMolecule = new Molecule(inMolecule.element, 0);
                    outCell.molecules.add(outMolecule);
                }
                int flow = Math.min(10000, inMolecule.amount);
                if (flow > 0) {
                    long eFlow = (long)(inCell.energy*((double)flow/cellMoles)*Elements.elementMap.get(inMolecule.element).specificHeat);
                    if (updateIn) {
                        inMolecule.amount -= flow;
                        inCell.energy -= eFlow;
                    }
                    if (updateOut) {
                        outMolecule.amount += flow;
                        outCell.energy += eFlow;
                    }
                }
            }
            inCell.molecules.removeIf(inMolecule -> inMolecule.amount <= 0);
        }
    }
}
