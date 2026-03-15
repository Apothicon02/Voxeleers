package org.voxeleers.game.world.types;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.voxeleers.game.rooms.Cell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import static org.voxeleers.game.world.World.*;

public class WorldType {
    public Random rand() {
        return null;
    }
    public Path getWorldPath() {
        return Path.of("none");
    }
    public String getWorldTypeName() {
        return "none";
    }
    public Cell getGlobalAtmo() {return new Cell();}
    public ByteArrayList getGlobalElements() {return new ByteArrayList();}
    public boolean hasVisualAtmo() {return false;}
    public void renderCelestialBodies() {}
    public void tick() {}
    public ExecutorService generate() throws IOException {
        ExecutorService executorService = null;
        generated = false;
        if (Files.exists(getWorldPath())) {
            loadWorld(getWorldPath()+"/");
        } else {
            long worldgenStarted = System.currentTimeMillis();
            createNew();
            System.out.print("Took "+String.format("%.2f", (System.currentTimeMillis()-worldgenStarted)/1000.f)+"s to generate world.\n");
        }
        generated = true;
        return executorService;
    }

    public void createNew() {}
}
