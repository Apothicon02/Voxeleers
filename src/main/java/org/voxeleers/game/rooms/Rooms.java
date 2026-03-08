package org.voxeleers.game.rooms;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.joml.Vector3i;
import org.voxeleers.game.blocks.types.BlockType;
import org.voxeleers.game.blocks.types.BlockTypes;
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
            boolean matchesGlobal = true;
            int globalCell = World.worldType.getGlobalTemp();
            for (int xyz : room.cells.keySet()) {
                int cell = room.cells.get(xyz);
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
                    int divider = 20;
                    boolean updateNeighbor = true;
                    int nxyz = packCellPos(nPos.x(), nPos.y(), nPos.z());
                    int nCell = room.cells.get(nxyz);
                    if (nCell <= 0) {
                        nCell = globalCell;
                        divider = 20000;
                        updateNeighbor = false;
                    }
                    int flow = Math.ceilDiv(cell-nCell, divider);
                    room.cells.put(xyz, cell - flow);
                    if (updateNeighbor) {
                        room.cells.put(nxyz, nCell + flow);
                    }
                }

                if (matchesGlobal) {
                    if (cell != globalCell) {
                        matchesGlobal = false;
                    }
                }
            }
            if (matchesGlobal) {
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
    public static void inject(Vector3i pos, int heat) {
        Room room = generateRoomIfNeeded(pos);
        if (room != null) {
            int xyz = packCellPos(pos);
            room.cells.put(xyz, Math.clamp(room.cells.get(xyz)+heat, 1, Integer.MAX_VALUE));
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
                        int cell = room.cells.get(xyz);
                        int mergedCell = mergedRoom.cells.get(xyz);
                        if (mergedCell <= 0) {
                            mergedRoom.cells.put(xyz, cell+mergedCell);
                        }
                    }
                    roomsToRemove.add(room);
                    break;
                }
            }
        }
        mergedRoom.cells.forEach((xyz, temp) -> {
            if (temp <= 0) {
                mergedRoom.cells.put((int)xyz, World.worldType.getGlobalTemp());
            }
        });
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
            BlockType blockType = BlockTypes.blockTypeMap.get(World.getBlockTypeUnchecked(x, y, z));
            if (!blockType.blockProperties.isSolid) {
                currentScan.cells.put(packed, 0);
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
