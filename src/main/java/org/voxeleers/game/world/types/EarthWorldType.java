package org.voxeleers.game.world.types;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.lwjgl.system.MemoryStack;
import org.voxeleers.Main;
import org.voxeleers.engine.VoxeleersMath;
import org.voxeleers.game.blocks.types.BlockTypes;
import org.voxeleers.game.elements.Elements;
import org.voxeleers.game.noise.Noises;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Molecule;
import org.voxeleers.game.world.World;
import org.voxeleers.game.world.shapes.*;
import org.voxeleers.game.world.trees.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.voxeleers.engine.Utils.*;
import static org.voxeleers.game.rendering.Renderer.*;
import static org.voxeleers.game.world.World.*;

public class EarthWorldType extends WorldType {
    private Path worldPath = Path.of(Main.mainFolder+"world0/earth");
    public static Random seededRand = new Random(35311350L);
    public static Cell globalAtmo = new Cell(124000000, List.of(new Molecule(Elements.elementMap.indexOf(Elements.NITROGEN), 390000), new Molecule(Elements.elementMap.indexOf(Elements.OXYGEN), 47000), new Molecule(Elements.elementMap.indexOf(Elements.CARBON_DIOXIDE), 3000)));
    public static ByteArrayList globalElements = new ByteArrayList();

    @Override
    public boolean hasVisualAtmo() {return true;}
    @Override
    public Random rand() {return seededRand;}
    @Override
    public Path getWorldPath() {
        return worldPath;
    }
    @Override
    public String getWorldTypeName() {
        return "Earth";
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
        glUniform4f(raster.uniforms.get("color"), 1.25f, 1.2f, 0, 1);
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().rotateXYZ(0.5f, 0.5f, 0.5f).setTranslation(munPos).scale(20).get(stack.mallocFloat(16)));
        }
        glUniform4f(raster.uniforms.get("color"), 0.95f, 0.88f, 1.f, 1);
        drawCube();
    }
    @Override
    public void tick() {
        sunPos.set(0, World.size*2, 0);
        sunPos.rotateZ((float) time);
        sunPos.rotateX(0.5f);
        sunPos.set(sunPos.x+(World.size/2f), sunPos.y, sunPos.z+(World.size/2f)+128);
        munPos.set(0, World.size*-2, 0);
        munPos.rotateZ((float) time);
        sunPos.rotateX(-0.2f);
        munPos.set(munPos.x+(World.size/2f), munPos.y, munPos.z+(World.size/2f)+128);
    }

    @Override
    public ExecutorService generate() throws IOException, InterruptedException {
        ExecutorService executorService = null;
        for (Molecule molecule : globalAtmo.molecules) {
            globalElements.addLast(molecule.element);
        }
        generated = false;
        if (Files.exists(getWorldPath())) {
            executorService = loadWorld(getWorldPath()+"/");
        } else {
            long worldgenStarted = System.currentTimeMillis();
            executorService = createNew();
            System.out.print("Took "+String.format("%.2f", (System.currentTimeMillis()-worldgenStarted)/1000.f)+"s to generate world.\n");
        }
        return executorService;
    }

    @Override
    public ExecutorService createNew() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int interval = size/threads;
        for (int cX = 0; cX < size; cX+=interval) {
            int startX = cX;
            pool.submit(() -> {
                for (int x = startX; x < startX+interval; x+=2) {
                    for (int z = 0; z < size; z+=2) {
                        float basePerlinNoise = (Noises.COHERERENT_NOISE.sample(x, z) + 0.5f) / 2;
                        float baseCellularNoise = Noises.CELLULAR_NOISE.sample(x, z) / 2;
                        float centDist = (float) (distance(x, z, size / 2.f, size / 2.f) / halfSize);
                        float centDistExp = (Math.max(0.5f, centDist) - 0.5f);
                        centDistExp *= centDistExp;
                        int surface = (int) (((200 * (Math.max(0.1f, baseCellularNoise) * basePerlinNoise)) + 70) - (centDistExp * 300));
                        surface = Math.max(14, surface);
                        surface = (2*(int)(surface/2))-1;
                        heightmap[condensePos(x, z)] = (short) (surface);
                        heightmap[condensePos(x+1, z)] = (short) (surface);
                        heightmap[condensePos(x, z+1)] = (short) (surface);
                        heightmap[condensePos(x+1, z+1)] = (short) (surface);
                        for (int y = surface; y >= 0; y-=2) {
                            setBlock(x, y, z, 3, 0);
                        }
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        System.out.print("Took "+(System.currentTimeMillis()-startTime)+"ms to generate heightmap from noise.");

        startTime = System.currentTimeMillis();
        for (int x = 0; x < size; x+=2) {
            for (int z = 0; z < size; z+=2) {
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
                boolean flat = maxSteepness < 4;
                if (flat) {
                    if (surface < seaLevel) {
                        setBlock(x, seaLevel, z, 1, 13);
                        for (int y = 62; y > surface; y-=2) {
                            setBlock(x, y, z, 1, 15);
                        }
                    } else if (surface < seaLevel + 3) {
                        setBlock(x, surface, z, BlockTypes.getId(BlockTypes.SAND), 0);
                        for (int newY = surface - 2; newY >= surface - 5; newY-=2) {
                            setBlock(x, newY, z, BlockTypes.getId(BlockTypes.SANDSTONE), 0);
                        }
                    } else {
                        setBlock(x, surface, z, 2, 0);
                        double flowerChance = seededRand.nextDouble();
                        if (flowerChance < 0.0001f) {
                            setBlock(x, surface+1, z, BlockTypes.getId(BlockTypes.PORECAP), 0);
                        } else {
                            int flower = (flowerChance > 0.95f ? (flowerChance > 0.97f ? 14 : 1) : 0);
                            if (flower > 0 || seededRand.nextFloat() < 0.33f) { //grass has 33% chance to place
                                setBlock(x, surface + 1, z, 4 + flower, seededRand.nextInt(0, 3));
                            }
                        }
                    }
                } else {
                    if (surface < seaLevel) {
                        setBlock(x, seaLevel, z, 1, 13);
                        for (int y = 62; y > surface; y-=2) {
                            setBlock(x, y, z, 1, 15);
                        }
                    }
                    for (int newY = surface; newY >= surface - 5; newY-=2) {
                        setBlock(x, newY, z, 55, 0);
                    }
                }
            }
        }
        System.out.print("Took "+(System.currentTimeMillis()-startTime)+"ms to fill blocks.");

        startTime = System.currentTimeMillis();
        threads = Runtime.getRuntime().availableProcessors();
        pool = Executors.newFixedThreadPool(threads);
        int featureInterval = size/threads;
        for (int cX = 0; cX < size; cX+=featureInterval) {
            int startX = cX;
            pool.submit(() -> {
                for (int x = startX; x < startX + featureInterval; x+=2) {
                    for (int z = 0; z < size; z+=2) {
                        int surface = heightmap[(x * size) + z];
                        Vector2i blockOn = getBlock(x, surface, z);
                        float basePerlinNoise = Noises.COHERERENT_NOISE.sample(x, z);
                        float randomNumber = seededRand.nextFloat();
                        if (blockOn.x == 2) {
                            float foliageChanceExp = basePerlinNoise * basePerlinNoise;
                            float foliageType = seededRand.nextFloat();
                            if (randomNumber * 10 < foliageChanceExp && foliageType < VoxeleersMath.gradient(surface, 78, 86, 1, 0.002f)) {
                                int maxHeight = seededRand.nextInt(16) + 12;
                                int radius = seededRand.nextInt(2) + 3;
                                boolean overgrown = seededRand.nextInt(4) == 0;
                                JungleTree.generate(blockOn, x, surface, z, maxHeight, radius, BlockTypes.getId(BlockTypes.CHERRY_LOG), 0, BlockTypes.getId(BlockTypes.CHERRY_LEAVES), 0, overgrown);
                            } else if (randomNumber * 10 < foliageChanceExp - 0.2f || randomNumber < 0.0002f) { //tree
                                if (foliageType < 0.0015f) { //0.15% chance the tree is dead
                                    int maxHeight = seededRand.nextInt(6) + 12;
                                    DeadOakTree.generate(blockOn, x, surface, z, maxHeight, 47, 0);
                                    Blob.generate(blockOn, x, surface, z, 3, 0, (int) ((rand().nextDouble() + 1) * 3), new int[]{2, 23}, true);
                                } else {
                                    int maxHeight = seededRand.nextInt(6) + 12;
                                    int leavesHeight = seededRand.nextInt(3) + 3;
                                    int radius = seededRand.nextInt(4) + 6;
                                    OakTree.generate(blockOn, x, surface, z, maxHeight, radius, leavesHeight, 16, 0, 17, 0);
                                }
                            } else if (foliageChanceExp < 0.2f && foliageType < 0.0005f) { //0.05% chance to generate spruce tree
                                int maxHeight = seededRand.nextInt(6) + 12;
                                SpruceTree.generate(blockOn, x, surface, z, maxHeight, BlockTypes.getId(BlockTypes.SPRUCE_LOG), 0, BlockTypes.getId(BlockTypes.SPRUCE_LEAVES), 0);
                            } else if ((randomNumber * 10) + 0.15f < basePerlinNoise - 0.2f || randomNumber < 0.0005f) { //bush
                                int maxHeight = (int) (rand().nextDouble() + 1);
                                OakShrub.generate(blockOn, x, surface, z, maxHeight, 3 + (maxHeight * 2), 16, 0, 17, 0);
                            }
                        } else if (blockOn.x == 23) {
                            int foliageChance = seededRand.nextInt(0, 400);
                            if (foliageChance == 0) { //tree
                                PalmTree.generate(blockOn, x, surface, z, seededRand.nextInt(8, 22), 25, 0, 27, 0);
                            } else if (randomNumber < 0.001f) {
                                setBlock(x, surface + 1, z, BlockTypes.getId(BlockTypes.TORCH), 0);
                            }
                        } else if (blockOn.x == 55) {
                            if (randomNumber < 0.08f) {
                                Blob.generate(blockOn, x, surface, z, randomNumber < 0.001f ? BlockTypes.getId(BlockTypes.KYANITE) : 8, 0, (int) (2 + (seededRand.nextFloat() * 8)));
                            }
                        }
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        System.out.print("Took "+(System.currentTimeMillis()-startTime)+"ms to generate features.");

        threads = Runtime.getRuntime().availableProcessors();
        pool = Executors.newFixedThreadPool(threads);
        int heightInterval = size/threads;
        for (int cX = 0; cX < size; cX+=heightInterval) {
            int startX = cX;
            pool.submit(() -> {
                for (int x = startX; x < startX + heightInterval; x++) {
                    for (int z = 0; z < size; z++) {
                        updateHeightmap(x, z);
                    }
                }
            });
        }
        pool.shutdown();

        return pool;
    }
}
