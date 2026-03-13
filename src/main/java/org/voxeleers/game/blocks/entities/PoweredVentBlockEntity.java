package org.voxeleers.game.blocks.entities;

import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.voxeleers.Main;
import org.voxeleers.game.audio.AudioController;
import org.voxeleers.game.audio.Sounds;
import org.voxeleers.game.audio.Source;
import org.voxeleers.game.blocks.types.BlockTypes;
import org.voxeleers.game.elements.Elements;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Molecule;
import org.voxeleers.game.rooms.Room;
import org.voxeleers.game.rooms.Rooms;
import org.voxeleers.game.world.Directions;
import org.voxeleers.game.world.World;

public class PoweredVentBlockEntity extends BlockEntity {
    public PoweredVentBlockEntity() {

    }

    @Override
    public BlockEntity create() {
        return new PoweredVentBlockEntity();
    }

    Source source = null;

    @Override
    public void remove(int xyz) {
        if (source != null) {
            source.delete();
            source = null;
        }
        World.blockEntities.remove(xyz);
    }

    @Override
    public void tick(Vector2i block, Vector3i pos) {
        Vector3i dir = Directions.dirs[block.y];

        boolean flowed = false;
        Vector3i inPos = new Vector3i(pos.x()-dir.x(), pos.y()-dir.y(), pos.z()-dir.z());
        Vector3i outPos = new Vector3i(pos.x()+dir.x(), pos.y()+dir.y(), pos.z()+dir.z());
        if (BlockTypes.blockTypeMap.get(World.getBlockTypeUnchecked(inPos.x(), inPos.y(), inPos.z())).permeable() && BlockTypes.blockTypeMap.get(World.getBlockTypeUnchecked(outPos.x(), outPos.y(), outPos.z())).permeable()) {
            int inXYZ = Rooms.packCellPos(inPos);
            Room inRoom = Rooms.getRoom(inXYZ);
            boolean updateIn = inRoom != null;
            Cell inCell = inRoom == null ? World.worldType.getGlobalAtmo() : inRoom.cells.get(inXYZ);

            int outXYZ = Rooms.packCellPos(outPos);
            Room outRoom = Rooms.getRoom(outXYZ);
            boolean updateOut = outRoom != null;
            Cell outCell = outRoom == null ? World.worldType.getGlobalAtmo() : outRoom.cells.get(outXYZ);

            if (updateOut || updateIn) { // && inCell != null && outCell != null
                int cellMoles = 0;
                for (Molecule inMolecule : inCell.molecules) {
                    cellMoles += inMolecule.amount;
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
                        flowed = true;
                        long eFlow = (long) (inCell.energy * ((double) flow / cellMoles) * Elements.elementMap.get(inMolecule.element).specificHeat);
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
        boolean inAudibleRange = pos.distance(Main.player.blockPos) < 100;
        if (inAudibleRange && flowed) {
            if (source == null) {
                source = new Source(new Vector3f(pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f), 1.f, 1.3f, 0.f, 1);
                source.play(Sounds.VENT);
            }
        } else if (source != null) {
            source.delete();
            source = null;
        }
    }
}
