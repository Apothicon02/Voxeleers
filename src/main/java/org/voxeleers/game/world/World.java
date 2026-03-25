package org.voxeleers.game.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.voxeleers.Main;
import org.voxeleers.engine.Utils;
import org.voxeleers.game.ScheduledTicker;
import org.voxeleers.game.audio.AudioController;
import org.voxeleers.game.audio.SFX;
import org.voxeleers.game.audio.Sounds;
import org.voxeleers.game.audio.Source;
import org.voxeleers.game.blocks.entities.BlockEntity;
import org.voxeleers.game.blocks.entities.BlockEntityTypes;
import org.voxeleers.game.blocks.types.BlockType;
import org.voxeleers.game.blocks.types.BlockTypes;
import org.voxeleers.game.blocks.types.LightBlockType;
import org.voxeleers.game.items.Item;
import org.voxeleers.game.rendering.Renderer;
import org.voxeleers.game.rendering.Textures;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Molecule;
import org.voxeleers.game.rooms.Room;
import org.voxeleers.game.rooms.Rooms;
import org.voxeleers.game.world.types.WorldType;
import org.voxeleers.game.world.types.WorldTypes;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4i;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.voxeleers.engine.Utils.*;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL12.glTexSubImage3D;
import static org.lwjgl.opengl.GL30.*;

public class World {
    public static int size = 1024;
    public static int halfSize = 1024/2;
    public static int height = 320;
    public static int seaLevel = 63;
    public static boolean generated = false;
    public static WorldType worldType = WorldTypes.MARS;
    public static WorldType nextWorldType = WorldTypes.LUNA;
    public static ObjectOpenHashSet<Item> items = new ObjectOpenHashSet<>();
    public static Int2ObjectOpenHashMap<BlockEntity> blockEntities = new Int2ObjectOpenHashMap<>();
    public static short[][] blocks;// = new short[height][(size*size)*2];
    public static boolean[] unsavedBlocks;// = new boolean[height];
    public static short[][] blocksLOD;// = new short[height/4][(size*size)/4];
    public static short[][] blocksLOD2;// = new short[height/16][(size*size)/16];
    public static byte[][] lights;// = new byte[height][(size*size)*4];
    public static boolean[] unsavedLights;// = new boolean[height];
    public static short[] heightmap;// = new short[size*size];

    public static void clearData() {
        items.clear();
        blockEntities.clear();
        Rooms.rooms.clear();
        blocks = new short[height][(size*size)*2];
        unsavedBlocks = new boolean[height];
        blocksLOD = new short[height/4][(size*size)/4];
        blocksLOD2 = new short[height/16][(size*size)/16];
        lights = new byte[height][(size*size)*4];
        unsavedLights = new boolean[height];
        heightmap = new short[size*size];
    }

    public static void init() {
        clearData();
    }

    public static void tickBlockEntities() {
        blockEntities.forEach((Integer xyz, BlockEntity blockEntity) -> {
            Vector3i pos = Rooms.unpackCellPos(xyz);
            blockEntity.tick(getBlockUnchecked(pos.x(), pos.y(), pos.z()), pos);
        });
    }
    public static void tickItems() {
        for (Item item : World.items) {
            if (item.timeExisted >= 600000) { //600000ms = 10m
                World.items.remove(item);
            } else {
                item.tick();
            }
        }
    }

    public static void finishGenerating() throws InterruptedException {
        if (Main.worldPool != null) {
            long worldLoadStarted = System.currentTimeMillis();
            Main.worldPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS); //wait until world loading is done, since it is used in the next step (renderer initialization)
            System.out.print("Waited " + String.format("%.2f", (System.currentTimeMillis() - worldLoadStarted) / 1000.f) + "s on world to finish loading. ");
        }
        long startTime = System.currentTimeMillis();
        for (int x = 1; x < size-1; x++) {
            for (int z = 1; z < size-1; z++) {
                for (int y = Math.max(heightmap[(x * size) + z], Math.max(heightmap[((x-1) * size) + z], Math.max(heightmap[((x+1) * size) + z], Math.max(heightmap[(x * size) + (z-1)], heightmap[(x * size) + (z+1)])))) + 1;
                     y >= Math.min(heightmap[(x * size) + z], Math.min(heightmap[((x-1) * size) + z], Math.min(heightmap[((x+1) * size) + z], Math.min(heightmap[(x * size) + (z-1)], heightmap[(x * size) + (z+1)])))); y--) {
                    Vector2i thisBlock = getBlock(x, y, z);
                    BlockType type = BlockTypes.blockTypeMap.get(thisBlock.x);
                    if (type instanceof LightBlockType || !type.blocksLight(thisBlock)) {
                        LightHelper.updateLight(new Vector3i(x, y, z), thisBlock, new Vector4i(0, 0, 0, 16));
                    }
                }
            }
        }
        System.out.print("Took "+(System.currentTimeMillis()-startTime)+"ms to fill lighting.\n");
        World.generated = true;
    }

    public static void dropItem(Item item) {
        World.items.add(item.clone().timeExisted(-2000).prevTickTime(Main.timeMS).moveTo(Main.player.getCameraMatrixWithoutPitch().invert().translate(0, -Main.player.eyeHeight + 0.2f, -1f).getTranslation(new Vector3f())));
    }

    public static boolean inBounds(int x, int y, int z) {
        return (x >= 0 && x < size && y >= 0 && y < height && z >= 0 && z < size);
    }

    public static ByteBuffer smallLightBuffer = ByteBuffer.allocateDirect(4);
    public static void setLight(int x, int y, int z, int r, int b, int g, int s) {
        if (inBounds(x, y, z)) {
            int pos = condensePos(x, z)*4;
            lights[y][pos] = (byte)r;
            lights[y][pos+1] = (byte)b;
            lights[y][pos+2] = (byte)g;
            lights[y][pos+3] = (byte)s;
            if (generated) {
                unsavedLights[y] = true;
                glBindTexture(GL_TEXTURE_3D, Textures.lights.id);
                glTexSubImage3D(GL_TEXTURE_3D, 0, z, y, x, 1, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, smallLightBuffer.put((byte)r).put((byte)b).put((byte)g).put((byte)s).flip());
                updateLODS(x, y, z);
            }
        }
    }
    public static void setLight(int x, int y, int z, Vector4i light) {
        setLight(x, y, z, light.x, light.y, light.z, light.w);
    }
    public static Vector4i getLight(int x, int y, int z, boolean returnNull) {
        if (inBounds(x, y, z)) {
            int pos = condensePos(x, z)*4;
            return new Vector4i(lights[y][pos], lights[y][pos+1], lights[y][pos+2], lights[y][pos+3]);
        }
        return returnNull ? null : new Vector4i(0);
    }
    public static Vector4i getLight(int x, int y, int z) {
        return getLight(x, y, z, true);
    }
    public static Vector4i getLight(Vector3i pos) {
        return getLight(pos.x, pos.y, pos.z, true);
    }

    public static IntBuffer smallLodBuffer = ByteBuffer.allocateDirect(16).asIntBuffer();
    public static void updateLODS(int x, int y, int z) {
        glBindTexture(GL_TEXTURE_3D, Textures.blocks.id);
        byte[] firstLight = null;
        boolean clear = true;
        loop:
        for (int cX = (int) Math.floor(x / 4f) * 4; cX < (Math.floor(x / 4f) * 4) + 4; cX++) {
            for (int cY = (int) Math.floor(y / 4f) * 4; cY < (Math.floor(y / 4f) * 4) + 4; cY++) {
                for (int cZ = (int) Math.floor(z / 4f) * 4; cZ < (Math.floor(z / 4f) * 4) + 4; cZ++) {
                    if (firstLight == null) {
                        firstLight = new byte[]{lights[cY][condensePos(cX, cZ)*4], lights[cY][(condensePos(cX, cZ)*4)+1], lights[cY][(condensePos(cX, cZ)*4)+2], lights[cY][(condensePos(cX, cZ)*4)+3]};
                    }
                    if (blocks[cY][condensePos(cX, cZ) * 2] > 0 || lights[cY][condensePos(cX, cZ)*4] != firstLight[0] || lights[cY][(condensePos(cX, cZ)*4)+1] != firstLight[1] || lights[cY][(condensePos(cX, cZ)*4)+2] != firstLight[2] || lights[cY][(condensePos(cX, cZ)*4)+3] != firstLight[3]) {
                        clear = false;
                        break loop;
                    }
                }
            }
        }
        smallLodBuffer.clear();
        glTexSubImage3D(GL_TEXTURE_3D, 2, z/4, y/4, x/4, 1, 1, 1, GL_RGBA_INTEGER, GL_INT, smallLodBuffer.put(clear ? 0 : 1).flip());
        blocksLOD[y/4][condensePosLOD(x, z)] = (short)(clear ? 0 : 1);
        if (clear) {
            firstLight = null;
            loop:
            for (int cX = (int) Math.floor(x/16f)*16; cX < (Math.floor(x/16f)*16)+16; cX++) {
                for (int cY = (int) Math.floor(y/16f)*16; cY < (Math.floor(y/16f)*16)+16; cY++) {
                    for (int cZ = (int) Math.floor(z/16f)*16; cZ < (Math.floor(z/16f)*16)+16; cZ++) {
                        if (firstLight == null) {
                            firstLight = new byte[]{lights[cY][condensePos(cX, cZ)*4], lights[cY][(condensePos(cX, cZ)*4)+1], lights[cY][(condensePos(cX, cZ)*4)+2], lights[cY][(condensePos(cX, cZ)*4)+3]};
                        }
                        if (blocks[cY][condensePos(cX, cZ)*2] > 0 || lights[cY][condensePos(cX, cZ)*4] != firstLight[0] || lights[cY][(condensePos(cX, cZ)*4)+1] != firstLight[1] || lights[cY][(condensePos(cX, cZ)*4)+2] != firstLight[2] || lights[cY][(condensePos(cX, cZ)*4)+3] != firstLight[3]) {
                            clear = false;
                            break loop;
                        }
                    }
                }
            }
        }
        smallLodBuffer.clear();
        glTexSubImage3D(GL_TEXTURE_3D, 4, z/16, y/16, x/16, 1, 1, 1, GL_RGBA_INTEGER, GL_INT, smallLodBuffer.put(clear ? 0 : 1).flip());
        blocksLOD2[y/16][condensePosLOD2(x, z)] = (short)(clear ? 0 : 1);
    }

    public static boolean setBlock(int x, int y, int z, int block, int blockSubType, boolean replace, boolean priority, int tickDelay, boolean silent) {
        Vector2i existing = getBlock(x, y, z);
        if (existing != null && (replace || existing.x() == 0)) {
            Vector3i pos = new Vector3i(x, y, z);
            int xyz = Rooms.packCellPos(x, y, z);
            BlockType existingType = BlockTypes.blockTypeMap.get(existing.x);
            Vector2i newBlock = new Vector2i(block, blockSubType);
            BlockType blockType = BlockTypes.blockTypeMap.get(block);
            boolean shouldActuallyPlaceBlock = true;
            if (existingType.permeable() != blockType.permeable()) {
                if (existingType.permeable()) { //if placing solid block in cell of gas
                    Room room = Rooms.getRoom(xyz);
                    if (room != null) {
                        Cell removingCell = room.cells.get(xyz);
                        if (removingCell.energy > 0) { //allow blocks to be placed in vacuums
                            shouldActuallyPlaceBlock = false;
                            Vector3i[] neighbors = new Vector3i[]{
                                    (new Vector3i(pos.x() + 1, pos.y(), pos.z())),
                                    (new Vector3i(pos.x() - 1, pos.y(), pos.z())),
                                    (new Vector3i(pos.x(), pos.y() + 1, pos.z())),
                                    (new Vector3i(pos.x(), pos.y() - 1, pos.z())),
                                    (new Vector3i(pos.x(), pos.y(), pos.z() + 1)),
                                    (new Vector3i(pos.x(), pos.y(), pos.z() - 1))};
                            for (Vector3i nPos : neighbors) {
                                int nXyz = Rooms.packCellPos(nPos);
                                Cell nCell = room.cells.get(nXyz);
                                if (nCell != null) {
                                    nCell.energy += removingCell.energy;
                                    for (Molecule molecule : removingCell.molecules) {
                                        boolean merged = false;
                                        for (Molecule nMolecule : nCell.molecules) {
                                            if (nMolecule.element == molecule.element) {
                                                nMolecule.amount += molecule.amount;
                                                merged = true;
                                                break;
                                            }
                                        }
                                        if (!merged) {
                                            nCell.molecules.add(molecule);
                                        }
                                    }
                                    room.cells.remove(xyz);
                                    shouldActuallyPlaceBlock = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (shouldActuallyPlaceBlock) {
                BlockEntity existingBlockEntity = World.blockEntities.get(xyz);
                if (existingBlockEntity != null) {
                    existingBlockEntity.remove(xyz);
                }
                Vector4i oldLight = getLight(pos);
                byte r = 0;
                byte g = 0;
                byte b = 0;
                boolean lightChanged = existingType instanceof LightBlockType;
                if (blockType instanceof LightBlockType lType) {
                    lightChanged = true;
                    r = lType.lightBlockProperties().r;
                    g = lType.lightBlockProperties().g;
                    b = lType.lightBlockProperties().b;
                }
                setBlock(x, y, z, block, blockSubType);
                Rooms.detectRooms(x, y, z);
                BlockEntity blockEntity = BlockEntityTypes.blockTypeToEntity.get(blockType);
                if (blockEntity != null) {
                    World.blockEntities.put(xyz, blockEntity.create());
                }
                if (tickDelay > 0) {
                    ScheduledTicker.scheduleTick(Main.currentTick + tickDelay, pos, 0);
                }
                if (!lightChanged) {
                    lightChanged = blockType.blocksLight(newBlock) != existingType.blocksLight(newBlock);
                }
                if (lightChanged) {
                    setLight(x, y, z, r, g, b, 0);
                }

                if (blockType.obstructingHeightmap(new Vector2i(block, blockSubType)) != existingType.obstructingHeightmap(existing)) {
                    updateHeightmap(x, z);
                }

                if (lightChanged) {
                    LightHelper.recalculateLight(pos, Math.max(oldLight.x, r), Math.max(oldLight.y, g), Math.max(oldLight.z, b), oldLight.w);
                }

                if (block == 0) {
                    existingType.onPlace(pos, existing, silent);
                } else {
                    BlockTypes.blockTypeMap.get(block).onPlace(pos, new Vector2i(block, blockSubType), silent);
                }
                return true;
            } else {
                Source source = new Source(new Vector3f(x, y, z), 1.f, 1.f, 0.f, 0);
                AudioController.disposableSources.add(source);
                source.play(Sounds.CLOUD);
            }
        }
        return false;
    }

    public static void setBlockNoUpdates(int x, int y, int z, int block, int blockSubType) {
        int pos = condensePos(x, z)*2;
        blocks[y][pos] = (short)(block);
        blocks[y][pos+1] = (short)(blockSubType);

        glBindTexture(GL_TEXTURE_3D, Textures.blocks.id);
        glTexSubImage3D(GL_TEXTURE_3D, 0, z, y, x, 1, 1, 1, GL_RGBA_INTEGER, GL_INT, new int[]{block, blockSubType, 0, 0});
        updateLODS(x, y, z);
    }
    public static void setBlock(int x, int y, int z, int block, int blockSubType) {
        if (inBounds(x, y, z)) {
            int pos = condensePos(x, z)*2;
            blocks[y][pos] = (short)(block);
            blocks[y][pos+1] = (short)(blockSubType);
            if (generated) {
                unsavedBlocks[y] = true;
                glBindTexture(GL_TEXTURE_3D, Textures.blocks.id);
                glTexSubImage3D(GL_TEXTURE_3D, 0, z, y, x, 1, 1, 1, GL_RGBA_INTEGER, GL_INT, new int[]{block, blockSubType, 0, 0});
                updateLODS(x, y, z);
            } else if (block > 0) {
                blocksLOD2[y/16][condensePosLOD2(x, z)] = (short)(block);
                blocksLOD[y/4][condensePosLOD(x, z)] = (short)(block);
            }
        }
    }
    public static void setBlock(float x, float y, float z, int block, int blockSubType) {
        setBlock((int) x, (int) y, (int) z, block, blockSubType);
    }

    public static Vector2i getBlockUnchecked(int x, int y, int z) {
        int pos = condensePos(x, z)*2;
        return new Vector2i(blocks[y][pos], blocks[y][pos+1]);
    }
    public static int getBlockTypeUnchecked(int x, int y, int z) {
        int pos = condensePos(x, z)*2;
        return blocks[y][pos];
    }
    public static Vector2i getBlock(int x, int y, int z) {
        if (inBounds(x, y, z)) {
            int pos = condensePos(x, z)*2;
            return new Vector2i(blocks[y][pos], blocks[y][pos+1]);
        } else {
            return null;
        }
    }
    public static Vector2i getBlock(Vector3i pos) {
        return getBlock(pos.x, pos.y, pos.z);
    }
    public static Vector2i getBlock(float x, float y, float z) {
        return getBlock((int) x, (int) y, (int) z);
    }
    public static Vector2i getBlockNotNull(float x, float y, float z) {
        Vector2i block = getBlock((int) x, (int) y, (int) z);
        return block == null ? new Vector2i(0) : block;
    }

    public static void updateHeightmap(int x, int z) {
        int prevHeight = heightmap[condensePos(x, z)];
        int newHeight = prevHeight;
        boolean setHeightmap = false;
        for (int y = height-1; y >= 0; y--) {
            Vector2i block = getBlock(x, y, z);
            BlockType blockType = BlockTypes.blockTypeMap.get(block.x);
            if (!setHeightmap) {
                if (!blockType.obstructingHeightmap(block)) {
                    Vector4i light = getLight(x, y, z);
                    setLight(x, y, z, light.x, light.y, light.z, 15);
                } else {
                    setHeightmap = true;
                    newHeight = y;
                    heightmap[condensePos(x, z)] = (short) (y);
                }
            } else {
                Vector4i light = getLight(x, y, z);
                setLight(x, y, z, light.x, light.y, light.z, 0);
                if (generated) {
                    LightHelper.recalculateLight(new Vector3i(x, y, z), light.x, light.y, light.z, light.w);
                }
            }
        }
        if (generated) {
            for (int y = prevHeight-1; y > newHeight; y--) {
                LightHelper.updateLight(new Vector3i(x, y, z), getBlock(x, y, z), getLight(x, y, z));
            }
        }
    }

    public static void saveWorld(String path) throws IOException {
        boolean didExist = Files.exists(Path.of(path));
        new File(path).mkdirs();

        String globalDataPath = path+"global.data";
        FileOutputStream out = new FileOutputStream(globalDataPath);
        byte[] globalData = Utils.intArrayToByteArray(new int[]{(int)(Renderer.time*1000), (int)(Main.timePassed*1000), Main.meridiem});
        out.write(globalData);
        out.close();

        String heightmapDataPath = path+"heightmap.data";
        out = new FileOutputStream(heightmapDataPath);
        byte[] heightmapData = Utils.shortArrayToByteArray(heightmap);
        out.write(heightmapData);
        out.close();

        Path blocksPath = Path.of(path + "blocks/");
        if (Files.notExists(blocksPath)) {Files.createDirectory(blocksPath);};
        int y = 0;
        for (boolean unsaved : unsavedBlocks) {
            if (unsaved || !didExist) {
                new FileOutputStream(path + "blocks/" + y + ".data").write(Utils.shortArrayToByteArray(blocks[y]));
                unsavedBlocks[y] = false;
            }
            y++;
        }
        Path blocksLODPath = Path.of(path + "blocksLOD/");
        if (Files.notExists(blocksLODPath)) {Files.createDirectory(blocksLODPath);};
        for (int i = 0; i < height/4; i++) {
            new FileOutputStream(path + "blocksLOD/"+i+".data").write(Utils.shortArrayToByteArray(blocksLOD[i]));
        }
        Path blocksLOD2Path = Path.of(path + "blocksLOD2/");
        if (Files.notExists(blocksLOD2Path)) {Files.createDirectory(blocksLOD2Path);};
        for (int i = 0; i < height/16; i++) {
            new FileOutputStream(path + "blocksLOD2/"+i+".data").write(Utils.shortArrayToByteArray(blocksLOD2[i]));
        }
        Path lightsPath = Path.of(path + "lights/");
        if (Files.notExists(lightsPath)) {Files.createDirectory(lightsPath);};
        y = 0;
        for (boolean unsaved : unsavedLights) {
            if (unsaved || !didExist) {
                new FileOutputStream(path + "lights/" + y + ".data").write(lights[y]);
                unsavedLights[y] = false;
            }
            y++;
        }

        String itemsPath = path + "items.data";
        out = new FileOutputStream(itemsPath);
        IntArrayList data = new IntArrayList();
        int i = 0;
        for (Item item : items) {
            int[] itemData = item.getData();
            data.addElements(i, itemData);
            i += itemData[0]+1;
        }
        out.write(Utils.intArrayToByteArray(data.toIntArray()));

        out.close();
    }

    public static ExecutorService loadWorld(String path) throws IOException {
        int[] globalData = Utils.flipIntArray(Utils.byteArrayToIntArray(new FileInputStream(path+"global.data").readAllBytes()));
        Renderer.time = globalData[0]/1000f;
        Main.timePassed = globalData[1]/1000f;
        Main.meridiem = globalData[2];
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(6, Runtime.getRuntime().availableProcessors()));
        pool.submit(() -> {try {Utils.byteArrayToShortArray(new FileInputStream(path + "heightmap.data").readAllBytes());} catch (IOException e) {throw new RuntimeException(e);}}, heightmap);
        pool.submit(() -> {try {
            for (int i = 0; i < height; i++) {
                blocks[i] = Utils.byteArrayToShortArray(new FileInputStream(path + "blocks/" + i + ".data").readAllBytes());
            }
        } catch (IOException e) {throw new RuntimeException(e);}});
        pool.submit(() -> {try {
            for (int i = 0; i < height/4; i++) {
                blocksLOD[i] = Utils.byteArrayToShortArray(new FileInputStream(path + "blocksLOD/" + i + ".data").readAllBytes());
            }
        } catch (IOException e) {throw new RuntimeException(e);}});
        pool.submit(() -> {try {
            for (int i = 0; i < height/16; i++) {
                blocksLOD2[i] = Utils.byteArrayToShortArray(new FileInputStream(path + "blocksLOD2/" + i + ".data").readAllBytes());
            }
        } catch (IOException e) {throw new RuntimeException(e);}});
        pool.submit(() -> {try {
            for (int i = 0; i < height; i++) {
                lights[i] = new FileInputStream(path + "lights/" + i + ".data").readAllBytes();
            }
        } catch (IOException e) {throw new RuntimeException(e);}});
        pool.submit(() -> {try {
            if (Files.exists(Path.of(path + "items.data"))) {
                int[] itemsData = Utils.flipIntArray(Utils.byteArrayToIntArray(new FileInputStream(path + "items.data").readAllBytes()));
                for (int i = 0; i < itemsData.length; ) {
                    int itemDataLength = itemsData[i++];
                    if (itemDataLength > 0) {
                        items.add(Item.load(itemsData, i));
                        i += itemDataLength;
                    }
                }
            }
        } catch (IOException e) {throw new RuntimeException(e);}});
        pool.shutdown();
        return pool;
    }
}
