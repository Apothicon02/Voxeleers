package org.voxeleers.game.world.types;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.joml.Matrix4f;
import org.joml.Vector4i;
import org.lwjgl.system.MemoryStack;
import org.voxeleers.Main;
import org.voxeleers.engine.VoxeleersMath;
import org.voxeleers.game.blocks.types.BlockTypes;
import org.voxeleers.game.blocks.types.LightBlockType;
import org.voxeleers.game.elements.Elements;
import org.voxeleers.game.noise.Noises;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Molecule;
import org.voxeleers.game.world.LightHelper;
import org.voxeleers.game.world.World;
import org.voxeleers.game.world.shapes.Blob;
import org.joml.Vector2i;
import org.joml.Vector3i;
import org.voxeleers.game.world.shapes.Cube;

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
import static org.voxeleers.engine.Utils.condensePos;
import static org.voxeleers.engine.Utils.distance;
import static org.voxeleers.game.rendering.Renderer.*;
import static org.voxeleers.game.world.World.*;

public class MarsWorldType extends WorldType {
    private Path worldPath = Path.of(Main.mainFolder+"world0/mars");
    public static Random seededRand = new Random(35311350L);
    public static Cell globalAtmo = new Cell(64000, List.of(new Molecule(Elements.elementMap.indexOf(Elements.CARBON_DIOXIDE), 246), new Molecule(Elements.elementMap.indexOf(Elements.NITROGEN), 7), new Molecule(Elements.elementMap.indexOf(Elements.ARGON), 5)));
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
        return "Mars";
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
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().rotateXYZ(0.5f, 0.5f, 0.5f).setTranslation(munPos).scale(20).get(stack.mallocFloat(16)));
        }
        glUniform4f(raster.uniforms.get("color"), 1.f, 0.88f, 1.f, 1);
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().rotateXYZ(0.5f, 0.5f, 0.5f).setTranslation(munPos.x()+450, munPos.y(), munPos.z()+900).scale(15).get(stack.mallocFloat(16)));
        }
        glUniform4f(raster.uniforms.get("color"), 1.f, 0.88f, 0.93f, 1);
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
        Vector3i[] craters = new Vector3i[5];
        for (int i = 0; i < craters.length; i++) {
            int radius = seededRand.nextInt(90) + 10;
            int borderOffset = radius*2;
            int x = seededRand.nextInt(size-borderOffset) + (borderOffset/2);
            int z = seededRand.nextInt(size-borderOffset) + (borderOffset/2);
            craters[i] = new Vector3i(x, radius, z);
        }
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int interval = size/threads;
        for (int cX = 0; cX < size; cX+=interval) {
            int startX = cX;
            pool.submit(() -> {
                for (int x = startX; x < startX+interval; x++) {
                    for (int z = 0; z < size; z++) {
                        float basePerlinNoise = (Noises.COHERERENT_NOISE.sample(x*2, z*2) + 0.5f) / 2;
                        float cellScale = 1+(((float) z / size)*0.33f);
                        float baseCellularNoise = Noises.CELLULAR_NOISE.sample((int) (x*cellScale), (int) (z*cellScale)) / 2;
                        int surface = (int) (((100 * Math.max(Math.abs(baseCellularNoise/4), Math.sqrt(Math.max(0, baseCellularNoise-0.33f)*(Math.clamp(((float) x+z) / size, 0.85f, 0.9f)-0.848f)*24))) + 70));
                        surface += basePerlinNoise*VoxeleersMath.gradient(surface, 80, 120, 32, 0);
                        double craterSurfMul = 1.f;
                        double craterSurfMaxMul = 1.f;
                        for (Vector3i crater : craters) {
                            double craterDist = distance(crater.x(), crater.z(), x, z);
                            int radius = crater.y();
                            if (craterDist < radius) {
                                craterDist /= radius;
                                craterDist = Math.pow(craterDist, 2);
                                craterDist *= 0.5f; //depth
                                double antiRidge = VoxeleersMath.gradient(Math.clamp(surface, 70, 96), 70, 96, 0.2f, 0.f);
                                craterDist += 0.7f-antiRidge;
                                double ridgePeak = 1.1f-(antiRidge/2);
                                if (craterDist > ridgePeak) { //ridges
                                    craterDist -= ((craterDist-ridgePeak)*2.f);
                                }
                                craterSurfMul = Math.min(craterDist, craterSurfMul);
                                craterSurfMaxMul = Math.max(craterDist, craterSurfMaxMul);
                            }
                        }
                        surface = (int) Math.max(16, surface*(craterSurfMul >= 1.f ? craterSurfMaxMul : craterSurfMul));
                        heightmap[condensePos(x, z)] = (short) (surface);
                        for (int y = surface; y >= 0; y--) {
                            setBlock(x, y, z, BlockTypes.getId(BlockTypes.SANDSTONE), 0);
                        }
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        System.out.print("Took "+(System.currentTimeMillis()-startTime)+"ms to generate heightmap from noise.");

        startTime = System.currentTimeMillis();
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
                boolean flat = maxSteepness < 4;
                if (flat) {
                    for (int newY = surface; newY >= surface - 5; newY--) {
                        setBlock(x, newY, z, BlockTypes.getId(BlockTypes.SAND), 0);
                    }
                } else {
                    for (int newY = surface; newY >= surface - 17; newY--) {
                        setBlock(x, newY, z, BlockTypes.getId(BlockTypes.GRAVEL), 0);
                    }
                }
            }
        }
        System.out.print("Took "+(System.currentTimeMillis()-startTime)+"ms to fill blocks.");

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

        startTime = System.currentTimeMillis();
        threads = Runtime.getRuntime().availableProcessors();
        pool = Executors.newFixedThreadPool(threads);
        int featureInterval = size/threads;
        Blob.generate(new Vector2i(), 550, heightmap[(550 * size) + 550]-15, 550, 0, 0, 100);
        for (int cX = 0; cX < size; cX+=featureInterval) {
            int startX = cX;
            pool.submit(() -> {
                for (int x = startX; x < startX + featureInterval; x++) {
                    for (int z = 0; z < size; z++) {
                        int surface = heightmap[(x * size) + z];
                        Vector2i blockOn = getBlock(x, surface, z);
                        float randomNumber = seededRand.nextFloat();
                        if (blockOn.x == BlockTypes.getId(BlockTypes.GRAVEL) || randomNumber < 0.0005f) {
                            if (randomNumber < 0.2f) {
                                Cube.generate(blockOn, x, surface, z, BlockTypes.getId(BlockTypes.SANDSTONE), 0, (int) (1 + (seededRand.nextFloat() * 4)));
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
