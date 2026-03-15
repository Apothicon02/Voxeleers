package org.voxeleers.game.noise;

import org.voxeleers.Main;
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

    public static ExecutorService init() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(3, Runtime.getRuntime().availableProcessors()));
        pool.submit(() -> {try {create(COHERERENT_NOISE, "generic/texture/coherent_noise");} catch (IOException e) {throw new RuntimeException(e);}});
        pool.submit(() -> {try {create(CELLULAR_NOISE, "generic/texture/cellular_noise");} catch (IOException e) {throw new RuntimeException(e);}});
        pool.submit(() -> {try {create(WHITE_NOISE, "generic/texture/white_noise");} catch (IOException e) {throw new RuntimeException(e);}});
//        pool.submit(() -> {try {create(NOODLE_NOISE, "generic/texture/noodle_noise");} catch (IOException e) {throw new RuntimeException(e);}});
//        pool.submit(() -> {try {create(CLOUD_NOISE, "generic/texture/cloud_noise");} catch (IOException e) {throw new RuntimeException(e);}});
//        pool.submit(() -> {try {create(SPIRAL_NOISE, "generic/texture/spiral_noise");} catch (IOException e) {throw new RuntimeException(e);}});
        pool.shutdown();
        return pool;
    }

    private static void create(Noise noise, String name) throws IOException {
        noise.init(loadImage(name));
        noiseMap.put(noiseMap.size(), noise);
    }

    private static BufferedImage loadImage(String name) throws IOException {
        InputStream inputStream = Main.class.getClassLoader().getResourceAsStream("assets/base/"+name+".png");
//        BufferedInputStream bInputStream = new BufferedInputStream(inputStream);
//        ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
//        reader.setInput(ImageIO.createImageInputStream(bInputStream), true);
//        BufferedImage image = reader.read(0);
//        inputStream.close();
//        bInputStream.close();
        return ImageIO.read(inputStream);
    }
}