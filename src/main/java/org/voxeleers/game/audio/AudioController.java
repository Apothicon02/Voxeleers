package org.voxeleers.game.audio;

import org.voxeleers.Main;
import org.joml.Vector3f;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryUtil;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Room;
import org.voxeleers.game.rooms.Rooms;
import org.voxeleers.game.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.openal.AL10.AL_NO_ERROR;
import static org.lwjgl.openal.AL10.alGetError;
import static org.lwjgl.openal.ALC11.ALC_MONO_SOURCES;
import static org.lwjgl.openal.ALC11.ALC_STEREO_SOURCES;

public class AudioController {
    public static long context;
    public static long device;
    public static ALCCapabilities alcCapabilities;
    public static ALCapabilities alCapabilities;

    public static void init() {
        String defaultDeviceName = ALC10.alcGetString(0, ALC10.ALC_DEFAULT_DEVICE_SPECIFIER);
        device = ALC10.alcOpenDevice(defaultDeviceName);
        alcCapabilities = ALC.createCapabilities(device);

        context = ALC10.alcCreateContext(device, new int[]{ALC_MONO_SOURCES, 16256, ALC_STEREO_SOURCES, 128, 0});
        ALC10.alcMakeContextCurrent(context);

        alCapabilities = AL.createCapabilities(alcCapabilities);
        AL10.alDistanceModel(AL11.AL_EXPONENT_DISTANCE);

//        int maxSourcesBeforeError = 0;
//        while (true) {
//            AL10.alGenSources();
//            if (AL10.alGetError() != AL_NO_ERROR) {
//                break;
//            }
//            maxSourcesBeforeError++;
//        }
    }

    public static ArrayList<Source> disposableSources = new ArrayList<>(List.of());

    public static void tick() {
//        if (alGetError() != AL_NO_ERROR) {
//            boolean nothing = true;
//        }
        for (int i = 0; i < disposableSources.size(); i++) {
            Source source = disposableSources.get(i);
            if (!source.isPlaying()) {
                source.delete();
                source = null;
                disposableSources.remove(i--);
            }
        }
    }

    public static void setListenerData(Vector3f pos, Vector3f vel, float[] orientation) {
        AL10.alListener3f(AL10.AL_POSITION, pos.x, pos.y, pos.z);
        AL10.alListener3f(AL10.AL_VELOCITY, vel.x, vel.y, vel.z);
        AL10.alListenerfv(AL10.AL_ORIENTATION, orientation);
        if (Main.player != null) {
            Room room = Rooms.getRoom(Main.player.blockPos);
            Cell cell = room == null ? World.worldType.getGlobalAtmo() : room.cells.get(Rooms.packCellPos(Main.player.blockPos));
            double pressure = cell.getPressure();
            if (Double.isNaN(pressure)) {
                pressure = 0.f;
            }
            float gain = Math.clamp((float) (pressure / 500000000.f), 0.1f, 1.f);
            AL10.alListenerf(AL10.AL_GAIN, gain);
        } else {
            AL10.alListenerf(AL10.AL_GAIN, 1.f);
        }
    }

    public static SFX loadSound(String file) {
        int buffer = AL10.alGenBuffers();
        WaveData waveFile = WaveData.create(file);
        if (waveFile != null) {
            AL10.alBufferData(buffer, waveFile.format, waveFile.data, waveFile.sampleRate);
            waveFile.dispose();
            return new SFX(buffer, (float) (waveFile.totalBytes / waveFile.bytesPerFrame) / waveFile.sampleRate);
        }
        return new SFX(buffer, 0);
    }

    public static String prevRandomSound = "";
    public static SFX loadRandomSound(String path) throws IOException {
        String folder = Main.resourcesPath+path;
        Path folderPath = Path.of(folder);
        if (Files.exists(folderPath)) {
            int buffer = AL10.alGenBuffers();
            List<String> allSounds = new ArrayList<>(Arrays.asList(new File(folder).list()));
            if (allSounds.size() > 1) {
                allSounds.remove(prevRandomSound);
                String name = allSounds.get((int) (Math.random() * (allSounds.size() - 1)));
                prevRandomSound = name;
                WaveData waveFile = WaveData.createFromAppdata(folder + name);
                AL10.alBufferData(buffer, waveFile.format, waveFile.data, waveFile.sampleRate);
                waveFile.dispose();
                return new SFX(buffer, (float) (waveFile.totalBytes / waveFile.bytesPerFrame) / waveFile.sampleRate);
            }
        } else {
            Files.createDirectory(folderPath);
        }
        return Sounds.CLOUD;
    }

    public static void cleanup() {
        ALC10.alcMakeContextCurrent(MemoryUtil.NULL);
        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);
    }
}
