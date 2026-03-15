package org.voxeleers.game.world.types;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.voxeleers.Main;
import org.voxeleers.game.blocks.types.BlockTypes;
import org.voxeleers.game.blocks.types.LightBlockType;
import org.voxeleers.game.noise.Noises;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Molecule;
import org.voxeleers.game.world.LightHelper;
import org.voxeleers.game.world.World;
import org.voxeleers.game.world.shapes.Blob;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.voxeleers.engine.Utils.condensePos;
import static org.voxeleers.game.rendering.Renderer.*;
import static org.voxeleers.game.world.World.*;
import static org.voxeleers.game.world.World.getLight;

public class LunaWorldType extends WorldType {
    private Path worldPath = Path.of(Main.mainFolder+"world0/luna");
    public static Random seededRand = new Random(35311350L);
    public static Cell globalAtmo = new Cell(0, new ArrayList<>());
    public static ByteArrayList globalElements = new ByteArrayList();

    @Override
    public Random rand() {return seededRand;}
    @Override
    public Path getWorldPath() {
        return worldPath;
    }
    @Override
    public String getWorldTypeName() {
        return "Luna";
    }
    @Override
    public Cell getGlobalAtmo() {return globalAtmo;}
    @Override
    public ByteArrayList getGlobalElements() {return globalElements;}
    @Override
    public void renderCelestialBodies() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().rotateXYZ(0.5f, 0.5f, 0.5f).setTranslation(sunPos).scale(60).get(stack.mallocFloat(16)));
        }
        glUniform4f(raster.uniforms.get("color"), 1.2f, 1.2f, 1.25f, 1);
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().rotateXYZ(0.5f, 0.f, 0.5f).setTranslation(munPos).scale(500).get(stack.mallocFloat(16)));
        }
        glUniform4f(raster.uniforms.get("color"), 0.25f, 0.33f, 0.95f, 1);
        drawCube();
    }
    @Override
    public void tick() {
        sunPos.set(0, World.size*2, 0);
        sunPos.rotateZ((float) time);
        sunPos.rotateX(0.25f);
        sunPos.set(sunPos.x+(World.size/2f), sunPos.y, sunPos.z+(World.size/2f)+128);
        munPos.set(0, World.size*1.5f, 0);
        munPos.rotateZ(1.15f);
        munPos.set(munPos.x+(World.size/2f), munPos.y, munPos.z+(World.size/2f)+300);
    }

    @Override
    public ExecutorService generate() throws IOException {
        ExecutorService executorService = null;
        for (Molecule molecule : globalAtmo.molecules) {
            globalElements.addLast(molecule.element);
        }
        generated = false;
        if (Files.exists(getWorldPath())) {
            executorService = loadWorld(getWorldPath()+"/");
        } else {
            long worldgenStarted = System.currentTimeMillis();
            createNew();
            System.out.print("Took "+String.format("%.2f", (System.currentTimeMillis()-worldgenStarted)/1000.f)+"s to generate world.\n");
        }
        generated = true;
        return executorService;
    }

    @Override
    public void createNew() {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                float basePerlinNoise = (Noises.COHERERENT_NOISE.sample(x, z) + 0.5f) / 2;
                float baseCellularNoise = Noises.CELLULAR_NOISE.sample(x, z) / 2;
                int surface = (int) (((200 * (Math.max(0.1f, baseCellularNoise) * basePerlinNoise)) + 70));
                surface = Math.max(16, surface);
                heightmap[condensePos(x, z)] = (short) (surface);
                for (int y = surface; y >= 0; y--) {
                    setBlock(x, y, z, BlockTypes.getId(BlockTypes.STONE), 0);
                }
            }
        }

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int maxSteepness = 0;
                int minNeighborY = height - 1;
                int condensedPos = condensePos(x, z);
                int surface = heightmap[condensedPos];
                for (int pos : new int[]{condensePos(Math.min(size - 1, x + 3), z), condensePos(Math.max(0, x - 3), z), condensePos(x, Math.min(size - 1, z + 3)), condensePos(x, Math.max(0, z - 3)),
                        condensePos(Math.max(0, x - 3), Math.max(0, z - 3)), condensePos(Math.min(size - 1, x + 3), Math.max(0, z - 3)), condensePos(Math.max(0, x - 3), Math.min(size - 1, z + 3)), condensePos(Math.min(size - 1, x + 3), Math.min(size - 1, z + 3))}) {
                    int nY = heightmap[pos];
                    minNeighborY = Math.min(minNeighborY, nY);
                    int steepness = Math.abs(surface - nY);
                    maxSteepness = Math.max(maxSteepness, steepness);
                }
                boolean flat = maxSteepness < 3;
                if (flat) {
                    for (int newY = surface; newY >= surface - 5; newY--) {
                        setBlock(x, newY, z, BlockTypes.getId(BlockTypes.REGOLITH), 0);
                    }
                } else {
                    for (int newY = surface; newY >= surface - 5; newY--) {
                        setBlock(x, newY, z, BlockTypes.getId(BlockTypes.GRAVEL), 0);
                    }
                }
            }
        }

        for (int x = (size / 2) - 29; x <= size / 2; x++) {
            for (int z = (size / 2) - 29; z < size / 2; z++) {
                setBlock(x, 100, z, 11, 0);
                boolean xWall = x == (size / 2) - 29 || x == (size / 2);
                if (xWall || z == (size / 2) - 29 || z == (size / 2) - 1) {
                    int block = xWall ? (x > 500 ? BlockTypes.getId(BlockTypes.RED_STAINED_GLASS) : BlockTypes.getId(BlockTypes.MAGENTA_STAINED_GLASS)) : (z > 500 ? BlockTypes.getId(BlockTypes.BLUE_STAINED_GLASS) : BlockTypes.getId(BlockTypes.LIME_STAINED_GLASS));
                    setBlock(x, 99, z, block, 0);
                    setBlock(x, 98, z, block, 0);
                    setBlock(x, 97, z, block, 0);
                    setBlock(x, 96, z, block, 0);
                    setBlock(x, 95, z, block, 0);
                    setBlock(x, 94, z, block, 0);
                    setBlock(x, 93, z, block, 0);
                    setBlock(x, 92, z, block, 0);
                    setBlock(x, 91, z, block, 0);
                    setBlock(x, 90, z, block, 0);
                    setBlock(x, 89, z, block, 0);
                    setBlock(x, 88, z, block, 0);
                    setBlock(x, 87, z, block, 0);
                    setBlock(x, 86, z, block, 0);
                    setBlock(x, 85, z, block, 0);
                    setBlock(x, 84, z, block, 0);
                    setBlock(x, 83, z, block, 0);
                    setBlock(x, 82, z, block, 0);
                    setBlock(x, 81, z, block, 0);
                    setBlock(x, 80, z, block, 0);
                    setBlock(x, 79, z, block, 0);
                    setBlock(x, 78, z, block, 0);
                    setBlock(x, 77, z, block, 0);
                    setBlock(x, 76, z, block, 0);
                    setBlock(x, 75, z, block, 0);
                    setBlock(x, 74, z, block, 0);
                    setBlock(x, 73, z, block, 0);
                    setBlock(x, 72, z, block, 0);
                    setBlock(x, 71, z, block, 0);
                    setBlock(x, 70, z, block, 0);
                }
            }
        }

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int surface = heightmap[(x * size) + z];
                Vector2i blockOn = getBlock(x, surface, z);
                float randomNumber = seededRand.nextFloat();
                if (blockOn.x == BlockTypes.getId(BlockTypes.GRAVEL) || randomNumber < 0.002f) {
                    if (randomNumber < 0.03f) {
                        Blob.generate(blockOn, x, surface, z, BlockTypes.getId(BlockTypes.MARBLE), 0, (int) (2 + (seededRand.nextFloat() * 8)));
                    }
                }
            }
        }

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                updateHeightmap(x, z);
            }
        }

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = 0; y <= heightmap[(x * size) + z] + 1; y++) {
                    Vector2i thisBlock = getBlock(x, y, z);
                    if (BlockTypes.blockTypeMap.get(thisBlock.x) instanceof LightBlockType ||
                            getLight(x, y, z + 1, false).w() > 0 || getLight(x + 1, y, z, false).w() > 0 || getLight(x, y, z - 1, false).w() > 0 ||
                            getLight(x - 1, y, z, false).w() > 0 || getLight(x, y + 1, z, false).w() > 0 || getLight(x, y - 1, z, false).w() > 0) {
                        LightHelper.updateLight(new Vector3i(x, y, z), getBlock(x, y, z), getLight(x, y, z));
                    }
                }
            }
        }
    }
}
