package org.voxeleers.game.noise;

import org.voxeleers.Main;
import org.voxeleers.engine.Utils;
import org.voxeleers.game.rendering.Renderer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Noises {
    public static Map<Integer, Noise> noiseMap = new HashMap<>(Map.of());
    public static Noise COHERERENT_NOISE = new Noise();
    public static Noise CELLULAR_NOISE = new Noise();
    public static Noise WHITE_NOISE = new Noise();
//    public static Noise NOODLE_NOISE = new Noise();
//    public static Noise CLOUD_NOISE = new Noise();
//    public static Noise SPIRAL_NOISE = new Noise();

    public static void init(ExecutorService pool) throws InterruptedException {
        pool.submit(() -> {try {create(COHERERENT_NOISE, "generic/texture/coherent_noise");} catch (IOException e) {throw new RuntimeException(e);}});
        pool.submit(() -> {try {create(CELLULAR_NOISE, "generic/texture/cellular_noise");} catch (IOException e) {throw new RuntimeException(e);}});
        pool.submit(() -> {try {create(WHITE_NOISE, "generic/texture/white_noise");} catch (IOException e) {throw new RuntimeException(e);}});
//        pool.submit(() -> {try {create(NOODLE_NOISE, "generic/texture/noodle_noise");} catch (IOException e) {throw new RuntimeException(e);}});
//        pool.submit(() -> {try {create(CLOUD_NOISE, "generic/texture/cloud_noise");} catch (IOException e) {throw new RuntimeException(e);}});
//        pool.submit(() -> {try {create(SPIRAL_NOISE, "generic/texture/spiral_noise");} catch (IOException e) {throw new RuntimeException(e);}});
    }

    private static void create(Noise noise, String name) throws IOException {
        noise.init(Utils.loadImage(name));
        noiseMap.put(noiseMap.size(), noise);
    }
}