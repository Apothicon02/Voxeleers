package org.voxeleers.game.world.types;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.voxeleers.Main;
import org.voxeleers.engine.VoxeleersMath;
import org.voxeleers.game.blocks.types.BlockTypes;
import org.voxeleers.game.blocks.types.LightBlockType;
import org.voxeleers.game.elements.Element;
import org.voxeleers.game.elements.Elements;
import org.voxeleers.game.noise.Noises;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Molecule;
import org.voxeleers.game.world.LightHelper;
import org.voxeleers.game.world.World;
import org.voxeleers.game.world.shapes.Blob;
import org.voxeleers.game.world.trees.*;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.voxeleers.engine.Utils.condensePos;
import static org.voxeleers.engine.Utils.distance;
import static org.voxeleers.game.world.World.*;
import static org.voxeleers.game.world.World.getLight;

public class TemperateWorldType extends WorldType {
    private Path worldPath = Path.of(Main.mainFolder+"world0/mars");
    public static Random seededRand = new Random(35311350L);
    public static Cell globalAtmo = new Cell(List.of(new Molecule(Elements.elementMap.indexOf(Elements.CARBON_DIOXIDE), 570), new Molecule(Elements.elementMap.indexOf(Elements.NITROGEN), 17), new Molecule(Elements.elementMap.indexOf(Elements.ARGON), 13)));
    public static ByteArrayList globalElements = new ByteArrayList();

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
    public void generate() throws IOException {
        for (Molecule molecule : globalAtmo.molecules) {
            globalElements.addLast(molecule.element);
        }
        generated = false;
        if (Files.exists(getWorldPath())) {
            loadWorld(getWorldPath()+"/");
        } else {
            createNew();
        }
        generated = true;
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
                    setBlock(x, y, z, BlockTypes.getId(BlockTypes.SANDSTONE), 0);
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
                        setBlock(x, newY, z, BlockTypes.getId(BlockTypes.SAND), 0);
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
                if (x == (size / 2) - 29 || x == (size / 2) || z == (size / 2) - 29 || z == (size / 2) - 1) {
                    setBlock(x, 99, z, 11, 0);
                    setBlock(x, 98, z, 11, 0);
                    setBlock(x, 97, z, 11, 0);
                    setBlock(x, 96, z, 11, 0);
                    setBlock(x, 95, z, 11, 0);
                    setBlock(x, 94, z, 11, 0);
                    setBlock(x, 93, z, 11, 0);
                    setBlock(x, 92, z, 11, 0);
                    setBlock(x, 91, z, 11, 0);
                    setBlock(x, 90, z, 11, 0);
                    setBlock(x, 89, z, 11, 0);
                    setBlock(x, 88, z, 11, 0);
                    setBlock(x, 87, z, 11, 0);
                    setBlock(x, 86, z, 11, 0);
                    setBlock(x, 85, z, 11, 0);
                    setBlock(x, 84, z, 11, 0);
                    setBlock(x, 83, z, 11, 0);
                    setBlock(x, 82, z, 11, 0);
                    setBlock(x, 81, z, 11, 0);
                    setBlock(x, 80, z, 11, 0);
                    setBlock(x, 79, z, 11, 0);
                    setBlock(x, 78, z, 11, 0);
                    setBlock(x, 77, z, 11, 0);
                    setBlock(x, 76, z, 11, 0);
                    setBlock(x, 75, z, 11, 0);
                    setBlock(x, 74, z, 11, 0);
                    setBlock(x, 73, z, 11, 0);
                    setBlock(x, 72, z, 11, 0);
                    setBlock(x, 71, z, 11, 0);
                    setBlock(x, 70, z, 11, 0);
                }
            }
        }
        setBlock(512, 95, 512, 11, 0);

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int surface = heightmap[(x * size) + z];
                Vector2i blockOn = getBlock(x, surface, z);
                float randomNumber = seededRand.nextFloat();
                if (blockOn.x == BlockTypes.getId(BlockTypes.GRAVEL)) {
                    if (randomNumber < 0.08f) {
                        Blob.generate(blockOn, x, surface, z, BlockTypes.getId(BlockTypes.SANDSTONE), 0, (int) (2 + (seededRand.nextFloat() * 8)));
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
