package org.voxeleers.game.rooms;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3i;
import org.voxeleers.game.blocks.types.BlockTypes;
import org.voxeleers.game.world.World;

import java.util.ArrayList;
import java.util.List;

public class Rooms {
    public static Vector3i min = new Vector3i();
    public static Vector3i max = new Vector3i();
    public static int maxSize = 50;
    public static List<IntArrayList> rooms = new ArrayList<>();
    public static IntArrayList currentScan = new IntArrayList();
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
        currentScan = new IntArrayList();
        if (scanCells(x, y, z) && !currentScan.isEmpty()) {
            mergeRooms(currentScan);
            rooms.add(currentScan);
        } else {
            clearRooms(currentScan);
        }
        currentScan = null;
    }

    private static void mergeRooms(IntArrayList mergedRoom) {
        List<IntArrayList> roomsToRemove = new ArrayList<>();
        for (IntArrayList room : rooms) {
            for (int ogxyz : room) {
                if (mergedRoom.contains(ogxyz)) {
                    roomsToRemove.add(room);
                    break;
                }
            }
        }
        rooms.removeIf(roomsToRemove::contains);
    }
    private static void clearRooms(IntArrayList area) {
        for (int xyz : area) {
            rooms.removeIf(room -> room.contains(xyz));
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
        if (!currentScan.contains(packed)) {
            if (World.getBlock(x, y, z).x() <= 0) {
                currentScan.add(packed);
                return true;
            }
        }
        return false;
    }
    public static int packCellPos(int x, int y, int z) {
        return (x << 20) | (y << 10) | z;
    }
    public static Vector3i unpackCellPos(int xyz) {
        return new Vector3i((xyz >> 20) & 0x3FF, (xyz >> 10) & 0x3FF, xyz & 0x3FF);
    }
}
