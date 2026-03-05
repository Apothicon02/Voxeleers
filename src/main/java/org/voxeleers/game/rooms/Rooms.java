package org.voxeleers.game.rooms;

import org.joml.Vector3i;
import org.voxeleers.game.world.World;

import java.util.ArrayList;
import java.util.List;

public class Rooms {
    public static Vector3i min = new Vector3i();
    public static Vector3i max = new Vector3i();
    public static int maxSize = 50;
    public static List<Room> rooms = new ArrayList<>();
    public static Room currentScan = new Room();

    public static void inject(Vector3i pos, Molecule molecule) {
        Room room = getRoom(pos);
        if (room != null) {
            int xyz = packCellPos(pos);
            Cell cell = room.cells.get(xyz);
            boolean injected = false;
            for (Molecule cellMolecule : cell.molecules) {
                if (cellMolecule.element == molecule.element) {
                    cellMolecule.amount += molecule.amount;
                    injected = true;
                    break;
                }
            }
            if (!injected) {
                cell.molecules.add(molecule);
            }
        }
    }
    public static void inject(Vector3i pos, int energy) {
        Room room = getRoom(pos);
        if (room != null) {
            int xyz = packCellPos(pos);
            Cell cell = room.cells.get(xyz);
            cell.energy += energy;
        }
    }

    public static Room getRoom(Vector3i pos) {
        int ogxyz = packCellPos(pos.x(), pos.y(), pos.z());
        for (Room room : rooms) {
            for (int xyz : room.cells.keySet()) {
                if (xyz == ogxyz) {
                    return room;
                }
            }
        }
        return null;
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
    public static void detectRoom(int x, int y, int z) {
        min = new Vector3i(x-maxSize, y-maxSize, z-maxSize);
        min.max(new Vector3i(1));
        max = new Vector3i(x+maxSize, y+maxSize, z+maxSize);
        max.min(new Vector3i(World.size-2, World.height-2, World.size-2));
        currentScan = new Room();
        if (scanCells(x, y, z) && !currentScan.cells.isEmpty()) {
            mergeRooms(currentScan);
            rooms.add(currentScan);
        } else {
            //clearRooms(currentScan);
        }
        currentScan = null;
    }

    private static void mergeRooms(Room mergedRoom) {
        List<Room> roomsToRemove = new ArrayList<>();
        for (Room room : rooms) {
            for (int ogxyz : room.cells.keySet()) {
                if (mergedRoom.cells.containsKey(ogxyz)) {
                    for (int xyz : room.cells.keySet()) {
                        Cell cell = room.cells.get(xyz);
                        Cell mergedCell =  mergedRoom.cells.get(xyz);
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
            if (World.getBlock(x, y, z).x() <= 0) {
                currentScan.cells.put(packed, new Cell());
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
