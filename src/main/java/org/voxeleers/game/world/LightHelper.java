package org.voxeleers.game.world;

import org.voxeleers.engine.Utils;
import org.voxeleers.game.blocks.types.BlockType;
import org.voxeleers.game.blocks.types.BlockTypes;
import org.voxeleers.game.blocks.types.LightBlockType;
import org.joml.Vector2i;
import org.joml.Vector3i;
import org.joml.Vector4i;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;

import static org.voxeleers.engine.Utils.condensePos;
import static org.voxeleers.engine.Utils.packInts;
import static org.voxeleers.game.world.World.*;

public class LightHelper {
    public static ArrayDeque<Vector3i> lightQueue = new ArrayDeque<>();
    public static HashSet<Vector3i> lightSet = new HashSet<>();

    public static void queueLightUpdate(Vector3i pos) {
        boolean exists = lightSet.contains(pos);
        if (!exists) {
            lightSet.add(pos);
            lightQueue.add(pos);
        }
    }

    public static boolean print = false;
    public static void iterateLightQueue() {
//        long timeStarted = System.currentTimeMillis();
        while (!lightQueue.isEmpty()) {
            Vector3i pos = lightQueue.pollFirst();
            updateLight(pos, getBlock(pos), getLight(pos));
            lightSet.remove(pos);
        }
//        if (print) {
//            long timeSpent = (System.currentTimeMillis() - timeStarted);
//            System.out.print("Took " + timeSpent + "ms to do light queue. \n");
//            print = false;
//        }
    }

    public static void updateLight(Vector3i pos, Vector2i block, Vector4i light) {
        BlockType blockType = BlockTypes.blockTypeMap.get(block.x);
        boolean isLight = blockType instanceof LightBlockType;
        if (!blocksLight(block) || isLight) {
            int r = Math.max(light.x(), isLight ? ((LightBlockType) blockType).lightBlockProperties().r : 0);
            int g = Math.max(light.y(), isLight ? ((LightBlockType) blockType).lightBlockProperties().g : 0);
            int b = Math.max(light.z(), isLight ? ((LightBlockType) blockType).lightBlockProperties().b : 0);
            int s = (pos.y > World.heightmap[Utils.condensePos(pos.x, pos.z)] ? 15 : light.w);
            for (Vector3i neighborPos : new Vector3i[]{
                    new Vector3i(pos.x, pos.y, pos.z + 1), new Vector3i(pos.x + 1, pos.y, pos.z), new Vector3i(pos.x, pos.y, pos.z - 1),
                    new Vector3i(pos.x - 1, pos.y, pos.z), new Vector3i(pos.x, pos.y + 1, pos.z), new Vector3i(pos.x, pos.y - 1, pos.z)
            }) {
                Vector2i neighbor = World.getBlock(neighborPos);
                Vector4i neighborLight = World.getLight(neighborPos);
                if (neighborLight != null) {
                    BlockType neighborBlockType = BlockTypes.blockTypeMap.get(neighbor.x);
                    boolean isNLight = neighborBlockType instanceof LightBlockType;
                    if (!blocksLight(neighbor) || isNLight) {
                        r = Math.max(r, Math.max(neighborLight.x(), isNLight ? ((LightBlockType) neighborBlockType).lightBlockProperties().r : 0) - 1);
                        g = Math.max(g, Math.max(neighborLight.y(), isNLight ? ((LightBlockType) neighborBlockType).lightBlockProperties().g : 0) - 1);
                        b = Math.max(b, Math.max(neighborLight.z(), isNLight ? ((LightBlockType) neighborBlockType).lightBlockProperties().b : 0) - 1);
                        s = Math.max(s, neighborLight.w() - 1);
                    }
                }
            }
            setLight(pos.x, pos.y, pos.z, new Vector4i(r, g, b, s));
            for (Vector3i neighborPos : new Vector3i[]{
                    new Vector3i(pos.x, pos.y, pos.z + 1), new Vector3i(pos.x + 1, pos.y, pos.z), new Vector3i(pos.x, pos.y, pos.z - 1),
                    new Vector3i(pos.x - 1, pos.y, pos.z), new Vector3i(pos.x, pos.y + 1, pos.z), new Vector3i(pos.x, pos.y - 1, pos.z)
            }) {
                Vector4i nLight = getLight(neighborPos);
                if (nLight != null) {
                    if (isDarker(r, g, b, s, nLight)) {
                        queueLightUpdate(neighborPos);
                    }
                }
            }
        }
    }
    public static boolean isDarker(int r, int g, int b, int s, Vector4i darker) {
        return r-2 > darker.x() || g-2 > darker.y() || b-2 > darker.z() || s-2 > darker.w();
    }
    public static boolean blocksLight(Vector2i block) {
        return BlockTypes.blockTypeMap.get(block.x).blocksLight(block);
    }

    public static ArrayDeque<lightNode> removalQueue = new ArrayDeque<>();
    public static HashSet<Vector3i> removalSet = new HashSet<>();
    public static void recalculateLight(Vector3i ogPos, int r, int g, int b, int s) {
        removalQueue.add(new lightNode(ogPos.x(), ogPos.y(), ogPos.z(), r, g, b, s));
        removalSet.add(ogPos);

        while (!removalQueue.isEmpty()) {
            lightNode node = removalQueue.pollFirst();
            Vector3i pos = new Vector3i(node.x, node.y, node.z);
            Vector4i light = new Vector4i(node.r(), node.g(), node.b(), node.s());
            if (light.x() > 0 || light.y() > 0 || light.z() > 0 || light.w() > 0) {
                setLight(pos.x(), pos.y(), pos.z(), new Vector4i(0));
                for (Vector3i neighborPos : new Vector3i[]{
                        new Vector3i(pos.x, pos.y, pos.z + 1), new Vector3i(pos.x + 1, pos.y, pos.z), new Vector3i(pos.x, pos.y, pos.z - 1),
                        new Vector3i(pos.x - 1, pos.y, pos.z), new Vector3i(pos.x, pos.y + 1, pos.z), new Vector3i(pos.x, pos.y - 1, pos.z)
                }) {
                    if (!removalSet.contains(neighborPos)) {
                        lightQueue.add(neighborPos);
                        lightSet.add(neighborPos);
                        Vector4i nLight = getLight(neighborPos);
                        if (nLight != null) {
                            if ((nLight.x() > 0 && nLight.x() == light.x() - 1) || (nLight.y() > 0 && nLight.y() == light.y() - 1) ||
                                    (nLight.z() > 0 && nLight.z() == light.z() - 1) || (nLight.w() > 0 && nLight.w() == light.w() - 1)) {
                                removalQueue.add(new lightNode(neighborPos.x(), neighborPos.y(), neighborPos.z(), nLight.x(), nLight.y(), nLight.z(), nLight.w()));
                                removalSet.add(neighborPos);
                            }
                        }
                    }
                }
            }
        }
        removalSet.clear();
    }

    public record lightNode(int x, int y, int z, int r, int g, int b, int s) {}
}