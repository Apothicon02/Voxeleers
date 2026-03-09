package org.voxeleers.game.rooms;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.joml.Vector3i;
import org.voxeleers.game.blocks.types.BlockTypes;
import org.voxeleers.game.elements.Elements;
import org.voxeleers.game.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Rooms {
    public static Vector3i min = new Vector3i();
    public static Vector3i max = new Vector3i();
    public static int maxSize = 50;
    public static List<Room> rooms = new ArrayList<>();
    public static Room currentScan = new Room();
    public static Random roomRandom = new Random(911);

    public static void tick() {
        ArrayList<Room> roomsToRemove = new ArrayList<>();
        for (Room room : rooms) {
            boolean sealed = true;
            boolean matchesGlobal = true;
            Cell globalCell = World.worldType.getGlobalAtmo();
            for (int xyz : room.cells.keySet()) {
                Cell cell = room.cells.get(xyz);
                Vector3i pos = unpackCellPos(xyz);

                int randomOffset = roomRandom.nextInt(6);
                Vector3i[] neighbors = new Vector3i[]{
                        (new Vector3i(pos.x() + 1, pos.y(), pos.z())),
                        (new Vector3i(pos.x() - 1, pos.y(), pos.z())),
                        (new Vector3i(pos.x(), pos.y() + 1, pos.z())),
                        (new Vector3i(pos.x(), pos.y() - 1, pos.z())),
                        (new Vector3i(pos.x(), pos.y(), pos.z() + 1)),
                        (new Vector3i(pos.x(), pos.y(), pos.z() - 1))};
                for (int i = randomOffset; i < randomOffset+6; i++) {
                    int idx = i-(((int)(i/6))*6);
                    Vector3i nPos = neighbors[idx];
                    boolean flowMoles = true;
                    Cell nCell = room.cells.get(packCellPos(nPos.x(), nPos.y(), nPos.z()));
                    if (nCell == null) {
                        nCell = new Cell(globalCell);
                        if (BlockTypes.blockTypeMap.get(World.getBlockTypeUnchecked(nPos.x(), nPos.y(), nPos.z())).blockProperties.isSolid) {
                            flowMoles = false;
                        } else {
                            sealed = false;
                        }
                    }
                    for (Molecule molecule : cell.molecules) {
                        int cellMoles = 0;
                        for (Molecule aMolecule : cell.molecules) {
                            cellMoles += aMolecule.amount;
                        }
                        double massLost = 0.d;
                        if (flowMoles) {
                            Molecule nMolecule = null;
                            for (Molecule potentialNMolecule : nCell.molecules) {
                                if (molecule.element == potentialNMolecule.element) {
                                    nMolecule = potentialNMolecule;
                                    break;
                                }
                            }
                            boolean doesMoleculeReallyExist = true;
                            if (nMolecule == null) {
                                doesMoleculeReallyExist = false;
                                nMolecule = new Molecule(molecule.element, 0);
                            }
                            double moleFlow = Math.ceilDiv(molecule.amount - nMolecule.amount, 2);
                            if (moleFlow > 0) {
                                massLost = moleFlow / cellMoles;
                                molecule.amount -= (int) moleFlow;
                                nMolecule.amount += (int) moleFlow;
                                if (!doesMoleculeReallyExist) {
                                    nCell.molecules.add(nMolecule);
                                }
                            }
                        }
                        double cellTemp = cell.getTemperature();
                        double nCellTemp = nCell.getTemperature();
                        double tempFlow = cell.getEnergyFromTemperature((cellTemp - nCellTemp)/100)*((double) molecule.amount/cellMoles);
                        if (tempFlow < 0) {
                            tempFlow = 0;
                        }
                        if (cellTemp >= nCellTemp) {
                            tempFlow = Math.max(1, tempFlow);
                        }
                        if (massLost < 0) {
                            massLost = 0;
                        }
                        float specificHeat = Elements.elementMap.get(molecule.element).specificHeat;
                        int energyFlow = (int) (Math.max(cell.energy * massLost, tempFlow)*specificHeat);
                        if (energyFlow != 0) {
                            cell.energy -= energyFlow;
                            nCell.energy += energyFlow;
                        }
                    }
                    cell.molecules.removeIf((molecule) -> molecule.amount <= 0);
                    nCell.molecules.removeIf((molecule) -> molecule.amount <= 0);
                }

                if (matchesGlobal) {
                    if (Math.abs(cell.energy - globalCell.energy) > 100) {
                        matchesGlobal = false;
                    } else if (cell.molecules.size() != globalCell.molecules.size()) {
                        matchesGlobal = false;
                    } else {
                        ByteArrayList elements = new ByteArrayList();
                        breakingPoint:
                        for (Molecule molecule : cell.molecules) {
                            for (Molecule globalMolecule : globalCell.molecules) {
                                elements.addLast(molecule.element);
                                if (globalMolecule.element == molecule.element) {
                                    if (Math.abs(globalMolecule.amount - molecule.amount) > 0) {
                                        matchesGlobal = false;
                                        break breakingPoint;
                                    }
                                }
                            }
                        }
                        if (matchesGlobal) {
                            for (byte element : World.worldType.getGlobalElements()) {
                                if (!elements.contains(element)) {
                                    matchesGlobal = false;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (matchesGlobal && !sealed) {
                roomsToRemove.add(room);
            }
        }
        rooms.removeAll(roomsToRemove);
    }
    public static Room generateRoomIfNeeded(Vector3i pos) {
        Room room = getRoom(pos);
        if (room == null) {
            room = detectRoom(pos.x, pos.y, pos.z);
        }
        return room;
    }
    public static void inject(Vector3i pos, Molecule molecule, int energy) {
        Room room = generateRoomIfNeeded(pos);
        if (room != null) {
            int xyz = packCellPos(pos);
            Cell cell = room.cells.get(xyz);
            Molecule exists = null;
            for (Molecule cellMolecule : cell.molecules) {
                if (cellMolecule.element == molecule.element) {
                    exists = cellMolecule;
                    break;
                }
            }
            cell.energy += energy;
            if (exists != null) {
                exists.amount += molecule.amount;
            } else {
                cell.molecules.add(molecule);
            }
        }
    }
    public static void mulEnergy(Vector3i pos, int energyMul) {
        Room room = generateRoomIfNeeded(pos);
        if (room != null) {
            int xyz = packCellPos(pos);
            Cell cell = room.cells.get(xyz);
            cell.energy *= energyMul;
        }
    }
    public static void removeCell(Vector3i pos) {
        Room room = getRoom(pos);
        if (room != null) {
            int xyz = packCellPos(pos);
            room.cells.remove(xyz);
        }
    }

    public static Room getRoom(int ogxyz) {
        for (Room room : rooms) {
            for (int xyz : room.cells.keySet()) {
                if (xyz == ogxyz) {
                    return room;
                }
            }
        }
        return null;
    }
    public static Room getRoom(Vector3i pos) {
        return getRoom(packCellPos(pos.x(), pos.y(), pos.z()));
    }

    public static void detectRooms(int x, int y, int z) {
        detectRoom(x, y, z);
        detectRoom(x+1, y, z);
        detectRoom(x-1, y, z);
        detectRoom(x, y+1, z);
        detectRoom(x, y-1, z);
        detectRoom(x, y, z+1);
        detectRoom(x, y, z-1);
    }
    public static Room detectRoom(int x, int y, int z) {
        min = new Vector3i(x-maxSize, y-maxSize, z-maxSize);
        min.max(new Vector3i(1));
        max = new Vector3i(x+maxSize, y+maxSize, z+maxSize);
        max.min(new Vector3i(World.size-2, World.height-2, World.size-2));
        currentScan = new Room();
        if (scanCells(x, y, z) && !currentScan.cells.isEmpty()) {
            mergeRooms(currentScan);
            rooms.addLast(currentScan);
            currentScan = null;
            return rooms.getLast();
        }
        currentScan = null;
        return null;
    }

    private static void mergeRooms(Room mergedRoom) {
        List<Room> roomsToRemove = new ArrayList<>();
        for (Room room : rooms) {
            for (int ogxyz : room.cells.keySet()) {
                if (mergedRoom.cells.containsKey(ogxyz)) {
                    for (int xyz : room.cells.keySet()) {
                        Cell cell = room.cells.get(xyz);
                        Cell mergedCell = mergedRoom.cells.get(xyz);
                        if (mergedCell == null) {
                            mergedCell = new Cell();
                            mergedRoom.cells.put(xyz, mergedCell);
                        }
                        mergedCell.energy += cell.energy;
                        for (Molecule molecule : cell.molecules) {
                            boolean matched = false;
                            for (Molecule mergedMolecule : mergedCell.molecules) {
                                if (molecule.element == mergedMolecule.element) {
                                    mergedMolecule.amount += molecule.amount;
                                    matched = true;
                                    break;
                                }
                            }
                            if (!matched) {
                                mergedCell.molecules.add(molecule);
                            }
                        }
                    }
                    roomsToRemove.add(room);
                    break;
                }
            }
        }
        for (Map.Entry<Integer, Cell> entry : mergedRoom.cells.entrySet()) {
            if (entry.getValue() == null) {
                entry.setValue(new Cell(World.worldType.getGlobalAtmo()));
            }
        }
        rooms.removeIf(roomsToRemove::contains);
    }
    private static void clearRooms(Room area) {
        for (int xyz : area.cells.keySet()) {
            rooms.removeIf(room -> room.cells.containsKey(xyz));
        }
    }

    public static boolean scanCells(int x, int y, int z) {
        if (x > min.x() && x < max.x() && y > min.y() && y < max.y() && z > min.z() && z < max.z()) {
            int packed = packCellPos(x, y, z);
            if (getCell(x, y, z, packed)) {
                if (!scanCells(x+1, y, z)) {
                    return false;
                }
                if (!scanCells(x-1, y, z)) {
                    return false;
                }
                if (!scanCells(x, y+1, z)) {
                    return false;
                }
                if (!scanCells(x, y-1, z)) {
                    return false;
                }
                if (!scanCells(x, y, z+1)) {
                    return false;
                }
                if (!scanCells(x, y, z-1)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
    public static boolean getCell(int x, int y, int z, int packed) {
        if (!currentScan.cells.containsKey(packed)) {
            if (!BlockTypes.blockTypeMap.get(World.getBlockTypeUnchecked(x, y, z)).blockProperties.isSolid) {
                currentScan.cells.put(packed, null);
                return true;
            }
        }
        return false;
    }
    public static int packCellPos(Vector3i pos) {
        return (pos.x() << 20) | (pos.y() << 10) | pos.z();
    }
    public static int packCellPos(int x, int y, int z) {
        return (x << 20) | (y << 10) | z;
    }
    public static Vector3i unpackCellPos(int xyz) {
        return new Vector3i((xyz >> 20) & 0x3FF, (xyz >> 10) & 0x3FF, xyz & 0x3FF);
    }
}
