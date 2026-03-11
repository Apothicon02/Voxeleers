package org.voxeleers.game.rendering;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.voxeleers.Main;
import org.voxeleers.game.gameplay.HandManager;
import org.voxeleers.game.items.Item;
import org.voxeleers.game.items.ItemType;
import org.voxeleers.game.noise.Noises;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Room;
import org.voxeleers.game.rooms.Rooms;
import org.voxeleers.game.world.World;
import org.joml.*;
import org.voxeleers.engine.*;
import org.voxeleers.engine.Window;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.lang.Math;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL46.*;
import static org.voxeleers.Main.*;

public class Renderer {
    public static ShaderProgram raster;
    public static ShaderProgram scene;
    public static ShaderProgram unchecker;
    public static ShaderProgram aa;
    public static ShaderProgram blur;
    public static ShaderProgram gui;

    public static int rasterFBOId;
    public static int sceneFBOId;
    public static int uncheckerFBOId;
    public static int blurryFBOId;
    public static int blurredFBOId;

    public static int playerSSBOId;
    public static int modelsSSBOId;
    public static int colorsSSBOId;
    public static int atlasOffsetSSBOId;

    public static boolean taa = true;
    public static boolean showUI = true;
    public static boolean shadowsEnabled = true;
    public static boolean reflectionShadows = false;
    public static boolean reflectionsEnabled = true;
    public static int renderDistanceMul = 8; //3
    public static int aoQuality = 2;
    public static float timeOfDay = 0.5f;
    public static double time = 0.5f;
    public static boolean screenshot = false;
    public static boolean forceTiltShift = false;
    public static boolean upscale = true;

    public static void createGLDebugger() {
        glEnable(GL_DEBUG_OUTPUT);
        glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
        glDebugMessageCallback((source, type, id, severity, length, message, userParam) -> {
            String msg = GLDebug.decode(source, type, id, severity, length, message, userParam);
            if (msg != null) {
                System.out.println(msg);
            }
        }, 0);
    }

    public static boolean[] collisionData = new boolean[(1024*1024)+1024];
    public static boolean alreadyCreatedTextures = false;

    public static void initiallyFillTextures(Window window, boolean resized) throws IOException {
        glBindFramebuffer(GL_FRAMEBUFFER, rasterFBOId);
        float[] emptyData = new float[window.getWidth()*window.getHeight()*4];
        glBindTexture(GL_TEXTURE_2D, Textures.rasterColor.id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, window.getWidth(), window.getHeight(), 0, GL_RGBA, GL_FLOAT, emptyData);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, Textures.rasterColor.id, 0);
        glBindTexture(GL_TEXTURE_2D, Textures.rasterPos.id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, window.getWidth(), window.getHeight(), 0, GL_RGBA, GL_FLOAT, emptyData);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, Textures.rasterPos.id, 0);
        glBindTexture(GL_TEXTURE_2D, Textures.rasterNorm.id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, window.getWidth(), window.getHeight(), 0, GL_RGBA, GL_FLOAT, emptyData);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, Textures.rasterNorm.id, 0);
        glBindTexture(GL_TEXTURE_2D, Textures.rasterDepth.id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, window.getWidth(), window.getHeight(), 0, GL_DEPTH_COMPONENT, GL_FLOAT, emptyData);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, Textures.rasterDepth.id, 0);
        glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT2});

        glBindFramebuffer(GL_FRAMEBUFFER, uncheckerFBOId);
        glBindTexture(GL_TEXTURE_2D, Textures.scene.id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, window.getWidth(), window.getHeight(), 0, GL_RGBA, GL_FLOAT, emptyData);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, Textures.scene.id, 0);

        glBindTexture(GL_TEXTURE_2D, Textures.sceneColorOld.id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, window.getWidth(), window.getHeight(), 0, GL_RGBA, GL_FLOAT, emptyData);

        if (!resized) {
            if (!alreadyCreatedTextures) {
                BufferedImage atlasImage = ImageIO.read(Renderer.class.getClassLoader().getResourceAsStream("assets/base/generic/texture/atlas.png"));
                for (int x = 0; x < Textures.atlas.width; x++) {
                    for (int y = 0; y < 1024; y++) {
                        Color color = new Color(atlasImage.getRGB(x, y), true);
                        collisionData[(x * 1024) + y] = color.getAlpha() != 0;
                    }
                }
                glBindTexture(GL_TEXTURE_3D, Textures.atlas.id);
                glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA32F, Textures.atlas.width, Textures.atlas.height, ((Texture3D) Textures.atlas).depth, 0, GL_RGBA, GL_UNSIGNED_BYTE, Utils.imageToBuffer(atlasImage));
            }

            glBindTexture(GL_TEXTURE_3D, Textures.blocks.id);
            if (!alreadyCreatedTextures) {
                glTexStorage3D(GL_TEXTURE_3D, 5, GL_RGBA16I, Textures.blocks.width, Textures.blocks.height, ((Texture3D) Textures.blocks).depth);
            }
            for (int i = 0; i < World.height; i++) {
                glTexSubImage3D(GL_TEXTURE_3D, 0, 0, i, 0, Textures.blocks.width, 1, ((Texture3D) Textures.blocks).depth, GL_RG_INTEGER, GL_SHORT, World.blocks[i]);
            }
            for (int i = 0; i < World.height/4; i++) {
                glTexSubImage3D(GL_TEXTURE_3D, 2, 0, i, 0, Textures.blocks.width/4, 1, ((Texture3D) Textures.blocks).depth/4, GL_RED_INTEGER, GL_SHORT, World.blocksLOD[i]);
            }
            for (int i = 0; i < World.height/16; i++) {
                glTexSubImage3D(GL_TEXTURE_3D, 4, 0, i, 0, Textures.blocks.width/16, 1, ((Texture3D) Textures.blocks).depth/16, GL_RED_INTEGER, GL_SHORT, World.blocksLOD2[i]);
            }

            glBindTexture(GL_TEXTURE_3D, Textures.lights.id);
            if (!alreadyCreatedTextures) {
                glTexStorage3D(GL_TEXTURE_3D, 1, GL_RGBA4, Textures.lights.width, Textures.lights.height, ((Texture3D) Textures.lights).depth);
            }
            for (int i = 0; i < World.height; i++) {
                byte[] data = World.lights[i];
                glTexSubImage3D(GL_TEXTURE_3D, 0, 0, i, 0, Textures.lights.width, 1, ((Texture3D) Textures.lights).depth, GL_RGBA, GL_BYTE, ByteBuffer.allocateDirect(data.length).put(data).flip());
            }
            if (!alreadyCreatedTextures) {
                float[] mergedNoises = new float[(Textures.noises.width * Textures.noises.height) * 4];
                for (int x = 0; x < Textures.noises.width; x++) {
                    for (int y = 0; y < Textures.noises.height; y++) {
                        int pos = 4 * ((x * Textures.noises.height) + y);
                        mergedNoises[pos] = Noises.COHERERENT_NOISE.sample(x, y);
                        mergedNoises[pos + 1] = Noises.WHITE_NOISE.sample(x, y);
                    }
                }
                glBindTexture(GL_TEXTURE_2D, Textures.noises.id);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, Textures.noises.width, Textures.noises.height, 0, GL_RGBA, GL_FLOAT, mergedNoises);

                GUI.fillTexture();
            }
        }
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFBOId);
        glBindTexture(GL_TEXTURE_2D, Textures.sceneColor.id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, window.getWidth(), window.getHeight(), 0, GL_RGBA, GL_FLOAT, emptyData);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, Textures.sceneColor.id, 0);

        glBindFramebuffer(GL_FRAMEBUFFER, blurryFBOId);
        glBindTexture(GL_TEXTURE_2D, Textures.blurry.id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, window.getWidth(), window.getHeight(), 0, GL_RGBA, GL_FLOAT, emptyData);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, Textures.blurry.id, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, blurredFBOId);
        glBindTexture(GL_TEXTURE_2D, Textures.blurred.id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, window.getWidth(), window.getHeight(), 0, GL_RGBA, GL_FLOAT, emptyData);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, Textures.blurred.id, 0);
        alreadyCreatedTextures = true;
    }

    public static void bindTextures() {
        glBindTextureUnit(0, Textures.rasterColor.id);
        glBindTextureUnit(1, Textures.rasterPos.id);
        glBindTextureUnit(2, Textures.rasterNorm.id);
        glBindTextureUnit(3, Textures.atlas.id);
        glBindTextureUnit(4, Textures.blocks.id);
        glBindTextureUnit(5, Textures.lights.id);
        glBindTextureUnit(6, Textures.noises.id);
    }

    public static void createBuffers() {
        playerSSBOId = glCreateBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, playerSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, playerSSBOId);
        glBufferStorage(GL_SHADER_STORAGE_BUFFER, new float[6], GL_CLIENT_STORAGE_BIT | GL_MAP_READ_BIT);

        modelsSSBOId = glCreateBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, modelsSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, modelsSSBOId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, new float[16], GL_DYNAMIC_DRAW);
        colorsSSBOId = glCreateBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, colorsSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, colorsSSBOId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, new float[4], GL_DYNAMIC_DRAW);
        atlasOffsetSSBOId = glCreateBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, atlasOffsetSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, atlasOffsetSSBOId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, new int[2], GL_DYNAMIC_DRAW);
    }

    public static void updateBuffers() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, playerSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, playerSSBOId);
        float[] data = new float[6];
        glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
        player.selectedBlock.set(data[0], data[1], data[2]);
        player.prevSelectedBlock.set(data[3], data[4], data[5]);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, modelsSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, modelsSSBOId);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, colorsSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, colorsSSBOId);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, atlasOffsetSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, atlasOffsetSSBOId);
    }

    public static void init(Window window) throws Exception {
        createGLDebugger();
        scene = new ShaderProgram("scene.vert", new String[]{"scene.frag"},
                new String[]{"res", "projection", "view", "selected", "offsetIdx", "checkerStep", "reverseChecker", "taa", "ui", "upscale", "renderDistance", "aoQuality", "timeOfDay", "time", "shadowsEnabled", "reflectionShadows", "sun", "mun"});
        raster = new ShaderProgram("debug.vert", new String[]{"debug.frag"},
                new String[]{"res", "projection", "view", "model", "selected", "offsetIdx", "color", "tex", "atlasOffset", "taa", "instanced", "ui", "alwaysUpfront", "renderDistance", "aoQuality", "timeOfDay", "time", "shadowsEnabled", "reflectionShadows", "sun", "mun"});
        unchecker = new ShaderProgram("scene.vert", new String[]{"unchecker.frag"},
                new String[]{});
        aa = new ShaderProgram("scene.vert", new String[]{"aa.frag"},
                new String[]{"res", "projection", "prevProj", "view", "prevView", "upscale", "taa", "offsetIdx", "offsetIdxOld"});
        blur = new ShaderProgram("scene.vert", new String[]{"blur.frag"},
                new String[]{"res","dir"});
        gui = new ShaderProgram("gui.vert", new String[]{"gui.frag"},
                new String[]{"res", "model", "color", "tex", "layer", "atlasOffset", "offset", "size", "scale", "tiltShift", "dof"});
        rasterFBOId = glGenFramebuffers();
        sceneFBOId = glGenFramebuffers();
        uncheckerFBOId = glGenFramebuffers();
        blurryFBOId = glGenFramebuffers();
        blurredFBOId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, rasterFBOId);

        createBuffers();
        Textures.generate();
        initiallyFillTextures(window, false);
    }

    public static Vector3f sunPos = new Vector3f(0, World.height*2, 0);
    public static Vector3f munPos = new Vector3f(0, World.height*-2, 0);
    public static int offsetIdx = 0;
    public static int offsetIdxOld = 0;
    public static Matrix4f viewMatrix = new Matrix4f();
    public static Matrix4f prevViewMatrix = new Matrix4f();
    public static Matrix4f projMatrix = new Matrix4f();
    public static Matrix4f prevProjMatrix = new Matrix4f();

    public static void  updateUniforms(ShaderProgram program, Window window) {
        projMatrix = new Matrix4f(window.updateProjectionMatrix());
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(program.uniforms.get("projection"), false, projMatrix.get(stack.mallocFloat(16)));
        }
        viewMatrix = new Matrix4f(player.getCameraMatrix());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(program.uniforms.get("view"), false, viewMatrix.get(stack.mallocFloat(16)));
        }
        glUniform1i(program.uniforms.get("offsetIdx"), offsetIdx);
        Vector3f selected = player.selectedBlock;
        glUniform3i(program.uniforms.get("selected"), (int) selected.x, (int) selected.y, (int) selected.z);
        glUniform1i(program.uniforms.get("taa"), taa ? 1 : 0);
        glUniform1i(program.uniforms.get("ui"), showUI && !screenshot && !Main.isSwappingWorldType ? 1 : 0);
        glUniform1i(program.uniforms.get("renderDistance"), 200 + (100 * renderDistanceMul));
        glUniform1i(program.uniforms.get("aoQuality"), aoQuality);
        glUniform1f(program.uniforms.get("timeOfDay"), timeOfDay);
        glUniform1d(program.uniforms.get("time"), time);
        glUniform1i(program.uniforms.get("shadowsEnabled"), shadowsEnabled ? 1 : 0);
        glUniform1i(program.uniforms.get("reflectionShadows"), reflectionShadows ? 1 : 0);
        sunPos.set(0, World.size*2, 0);
        sunPos.rotateZ((float) time);
        sunPos.rotateX(0.5f);
        sunPos.set(sunPos.x+(World.size/2f), sunPos.y, sunPos.z+(World.size/2f)+128);
        glUniform3f(program.uniforms.get("sun"), sunPos.x, sunPos.y, sunPos.z);
        munPos.set(0, World.size*-2, 0);
        munPos.rotateZ((float) time);
        sunPos.rotateX(-0.2f);
        munPos.set(munPos.x+(World.size/2f), munPos.y, munPos.z+(World.size/2f)+128);
        glUniform3f(program.uniforms.get("mun"), munPos.x, munPos.y, munPos.z);
    }

    public static void draw() {
        glBindVertexArray(Models.SCREEN_TRIANGLE.vaoId);
        glEnableVertexAttribArray(0);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDisableVertexAttribArray(0);
    }
    public static void drawHalf() {
        glBindVertexArray(Models.SCREEN_TRIANGLE_HALF.vaoId);
        glEnableVertexAttribArray(0);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDisableVertexAttribArray(0);
    }
    public static void drawCube() {
        glBindVertexArray(Models.CUBE.vaoId);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLES, 0, Models.CUBE.positions.length);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
    }
    public static void drawCubes(int amount) {
        glBindVertexArray(Models.CUBE.vaoId);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glDrawArraysInstanced(GL_TRIANGLES, 0, Models.CUBE.positions.length, amount);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
    }
    public static void drawPlane() {
        glBindVertexArray(Models.QUAD_UNNORMALIZED.vaoId);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLES, 0, Models.QUAD_UNNORMALIZED.positions.length);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
    }
    public static void drawDoubleSidedPlane() {
        glBindVertexArray(Models.PLANE_DB.vaoId);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLES, 0, Models.PLANE_DB.positions.length);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
    }
    public static void drawDoubleSidedPlanes(int amount) {
        glBindVertexArray(Models.PLANE_DB.vaoId);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glDrawArraysInstanced(GL_TRIANGLES, 0, Models.PLANE_DB.positions.length, amount);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
    }
    public static void drawHuman() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(506, 101, 504).scale(0.5f).get(stack.mallocFloat(16)));
        }
        glUniform4f(raster.uniforms.get("color"), 0.5f, 0.5f, 0.53f, 1);
        glBindVertexArray(Models.HUMAN.vaoId);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLES, 0, Models.HUMAN.positions.length);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
    }
    public static void drawDebugWheel() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512, 75, 512).scale(10).get(stack.mallocFloat(16)));//.translate(Main.player.pos).translate(10, 0, 0).get(stack.mallocFloat(16)));
        }
        glUniform4f(raster.uniforms.get("color"), 0.5f, 0.5f, 0.5f, 1);
        glBindVertexArray(Models.TORUS.vaoId);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLES, 0, Models.TORUS.positions.length);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
    }
    public static Vector3f[] debugColors = new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(1, 0.5f, 0), new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), new Vector3f(0, 1, 1),
            new Vector3f(0, 0, 1), new Vector3f(0.5f, 0, 1), new Vector3f(1, 0, 0.33f), new Vector3f(1, 0, 1), new Vector3f(1), new Vector3f(0.5f), new Vector3f(0)};
    public static void drawDebugRooms() {
        Random roomRand = new Random(911);
        int i = 0;
        for (Room room : Rooms.rooms) {
            Vector3f color = debugColors[i];
            i++;
            if (i >= debugColors.length) {
                i = 0;
            }
            glUniform4f(raster.uniforms.get("color"), color.x()*0.95f, color.y()*0.95f, color.z()*0.95f, 1.f);
            for (int xyz : room.cells.keySet()) {
                if (!room.cells.get(xyz).molecules.isEmpty()) {
                    Vector3i cellPos = Rooms.unpackCellPos(xyz);
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().setTranslation(cellPos.x() + roomRand.nextFloat(), cellPos.y() + roomRand.nextFloat(), cellPos.z() + roomRand.nextFloat()).scale(0.125f).get(stack.mallocFloat(16)));
                    }
                    drawCube();
                }
            }
        }
    }
    public static void drawGas() {
        if (uiState == 1) {
            Random roomRand = new Random(911);
            int i = 0;
            for (Room room : Rooms.rooms) {
                FloatBuffer modelBuffer = BufferUtils.createFloatBuffer(room.cells.size()*16);
                FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(room.cells.size()*4);
                i++;
                if (i >= debugColors.length) {
                    i = 0;
                }
                for (int xyz : room.cells.keySet()) {
                    Cell cell = room.cells.get(xyz);
                    if (!cell.molecules.isEmpty()) {
                        Vector3f color = Utils.getColorOfTemp(cell.getTemperature());
                        colorBuffer.put(color.x());
                        colorBuffer.put(color.y());
                        colorBuffer.put(color.z());
                        colorBuffer.put(1.f);
                        Vector3i cellPos = Rooms.unpackCellPos(xyz);
                        float scale = (float) Math.clamp(cell.getPressure() / 10000000000.f, 0.01f, 0.125f)/2;
                        float scaleOF = (1-(scale*2));
                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            modelBuffer.put(new Matrix4f().setTranslation(cellPos.x() + scale + (roomRand.nextFloat()*scaleOF), cellPos.y() + scale + (roomRand.nextFloat()*scaleOF),
                                            cellPos.z() + scale + (roomRand.nextFloat()*scaleOF)).
                                    scale(scale).get(stack.mallocFloat(16)));
                        }
                    }
                }
                modelBuffer.flip();
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, modelsSSBOId);
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, modelsSSBOId);
                glBufferData(GL_SHADER_STORAGE_BUFFER, modelBuffer, GL_DYNAMIC_DRAW);
                colorBuffer.flip();
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, colorsSSBOId);
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, colorsSSBOId);
                glBufferData(GL_SHADER_STORAGE_BUFFER, colorBuffer, GL_DYNAMIC_DRAW);
                drawCubes(room.cells.size());
            }
        }
    }

    public static void drawClouds() {
        FloatBuffer modelBuffer = BufferUtils.createFloatBuffer(196*16);
        FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(196*4);
        Random cloudRand = new Random(911);
        float brightness = Math.clamp((640+sunPos.y())/640, 0.3f, 1.f);
        for (int i = 0; i < 196; i++) {
            float b = Math.max(0.25f, brightness-(cloudRand.nextFloat()/2));
            Vector3f pos = new Vector3f(0, 0, 2000*(cloudRand.nextFloat()+0.05f)).rotateY((float) ((cloudRand.nextFloat()*10)+(time*(3+cloudRand.nextInt(2)))));
            try (MemoryStack stack = MemoryStack.stackPush()) {
                modelBuffer.put(new Matrix4f().rotateY(cloudRand.nextFloat()/10).setTranslation(pos.set(pos.x + 512, cloudRand.nextInt(200)+320-((Math.abs(pos.x)+Math.abs(pos.z))/10), pos.z + 512)).scale(10+cloudRand.nextInt(10), 3+cloudRand.nextInt(6), 10+cloudRand.nextInt(10)).get(stack.mallocFloat(16)));
            }
            colorBuffer.put(b);
            colorBuffer.put(b);
            colorBuffer.put(b);
            colorBuffer.put(-1);
        }
        modelBuffer.flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, modelsSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, modelsSSBOId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, modelBuffer, GL_DYNAMIC_DRAW);
        colorBuffer.flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, colorsSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, colorsSSBOId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, colorBuffer, GL_DYNAMIC_DRAW);
        drawCubes(1024);
    }
    public static void drawSunAndMoon() {
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
    public static Vector3f[] starColors = new Vector3f[]{new Vector3f(0.9f, 0.95f, 1.f), new Vector3f(1, 0.95f, 0.4f), new Vector3f(0.72f, 0.05f, 0), new Vector3f(0.42f, 0.85f, 1.f), new Vector3f(0.04f, 0.3f, 1.f), new Vector3f(1, 1, 0.1f)};
    public static int starDist = World.size+100;
    public static void drawStars() {
        FloatBuffer modelBuffer = BufferUtils.createFloatBuffer(1024*16);
        FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(1024*4);
        Random starRand = new Random(911);
        for (int i = 0; i < 1024; i++) {
            Vector3f starPos = new Vector3f(0, starDist * 2, 0)
                    .rotateX(starRand.nextFloat() * 10)
                    .rotateY(starRand.nextFloat() * 10)
                    .rotateZ((float) (time*3) + starRand.nextFloat() * 10);
            starPos.set(starPos.x + (starDist / 2f), starPos.y, starPos.z + (starDist / 2f));
            float starSize = (starRand.nextFloat()*2)+2;
            if (starSize > 0.01f) {
                Matrix4f starMatrix = new Matrix4f()
                        .rotateXYZ(starRand.nextFloat(), starRand.nextFloat(), starRand.nextFloat())
                        .setTranslation(starPos)
                        .scale(starSize);
                if (starMatrix.getTranslation(new Vector3f()).y > World.seaLevel-player.pos.y()) {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        modelBuffer.put(starMatrix.get(stack.mallocFloat(16)));
                    }
                    Vector3f color = starRand.nextFloat() < 0.64f ? new Vector3f(0.97f, 0.98f, 1.f) : starColors[starRand.nextInt(starColors.length - 1)];
                    colorBuffer.put(color.x*12);
                    colorBuffer.put(color.y*12);
                    colorBuffer.put(color.z*12);
                    colorBuffer.put(2);
                }
            }
        }
        modelBuffer.flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, modelsSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, modelsSSBOId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, modelBuffer, GL_DYNAMIC_DRAW);
        colorBuffer.flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, colorsSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, colorsSSBOId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, colorBuffer, GL_DYNAMIC_DRAW);
        drawCubes(1024);
    }
    public static void drawCenter() {
        glUniform4f(raster.uniforms.get("color"), 0.5f, 0.5f, 0.5f, 1);
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512.5f, 319.5f, 512.5f).scale(0.5f).get(stack.mallocFloat(16)));
        }
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512.5f, 269.5f, 512.5f).scale(0.5f).get(stack.mallocFloat(16)));
        }
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512.5f, 219.5f, 512.5f).scale(0.5f).get(stack.mallocFloat(16)));
        }
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512.5f, 169.5f, 512.5f).scale(0.5f).get(stack.mallocFloat(16)));
        }
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512.5f, 119.5f, 512.5f).scale(0.5f).get(stack.mallocFloat(16)));
        }
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512.5f, 95.5f, 512.5f).scale(0.5f).get(stack.mallocFloat(16)));
        }
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512.5f, 95.5f, 516.5f).scale(0.5f).get(stack.mallocFloat(16)));
        }
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512.5f, 95.5f, 519.5f).scale(0.5f).get(stack.mallocFloat(16)));
        }
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512.5f, 95.5f, 522.5f).scale(0.5f).get(stack.mallocFloat(16)));
        }
        drawCube();
        try(MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(raster.uniforms.get("model"), false, new Matrix4f().translate(512.5f, 95.5f, 525.5f).scale(0.5f).get(stack.mallocFloat(16)));
        }
        drawCube();
    }
    public static void drawItems() {
        glUniform1i(raster.uniforms.get("tex"), 1); // rendering item
        glBindTextureUnit(0, Textures.items.id);
        FloatBuffer modelBuffer = BufferUtils.createFloatBuffer(World.items.size()*16);
        FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(World.items.size()*4);
        IntBuffer atlasOffsetBuffer = BufferUtils.createIntBuffer(World.items.size()*2);
        for (Item item : World.items) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                modelBuffer.put(new Matrix4f().rotateY((float) Math.toRadians(item.rot)).setTranslation(new Vector3f(item.pos).add(0, item.hover, 0)).scale(0.5f).get(stack.mallocFloat(16)));
            }
            colorBuffer.put(1.f);
            colorBuffer.put(1.f);
            colorBuffer.put(1.f);
            colorBuffer.put(1.f);
            atlasOffsetBuffer.put(item.type.atlasOffset.x());
            atlasOffsetBuffer.put(item.type.atlasOffset.y());
        }
        modelBuffer.flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, modelsSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, modelsSSBOId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, modelBuffer, GL_DYNAMIC_DRAW);
        colorBuffer.flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, colorsSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, colorsSSBOId);
        glBufferData(GL_SHADER_STORAGE_BUFFER,  colorBuffer, GL_DYNAMIC_DRAW);
        atlasOffsetBuffer.flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, atlasOffsetSSBOId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, atlasOffsetSSBOId);
        glBufferData(GL_SHADER_STORAGE_BUFFER,  atlasOffsetBuffer, GL_DYNAMIC_DRAW);
        drawDoubleSidedPlanes(World.items.size());
    }
    public static void drawPlayer() {
        Item item = player.inv.getItem(player.inv.selectedSlot);
        glUniform1i(raster.uniforms.get("alwaysUpfront"), 1);
        if (item != null) {
            glUniform1i(raster.uniforms.get("tex"), 1); // rendering item
            glUniform4f(raster.uniforms.get("color"), 1, 1, 1, 1);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                glUniformMatrix4fv(raster.uniforms.get("model"), false, player.getCameraMatrixWithoutPitch().invert().translate(0.045f + Math.max(0, handTilt() * 0.03f), -0.115f + (player.bobbing * 0.05f) - Math.min(0, handTilt() * 0.1f), -0.03f + (handTilt() * 0.1f)).rotateY((float) Math.toRadians(-90.f)).rotateZ((float) Math.toRadians(55.f + (handTilt() < 0 ? (handTilt() * 80) : (handTilt() * 40)) + HandManager.getTilt())).scale(0.125f).get(stack.mallocFloat(16)));
            }
            glUniform2i(raster.uniforms.get("atlasOffset"), item.type.atlasOffset.x(), item.type.atlasOffset.y());
            drawDoubleSidedPlane();
        } else {
            glUniform1i(raster.uniforms.get("tex"), -1); //rendering hand
            if (showUI && !Main.isSwappingWorldType) {
                glUniform4f(raster.uniforms.get("color"), 0.6f, 0.45f, 0.35f, 1);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    glUniformMatrix4fv(raster.uniforms.get("model"), false, player.getCameraMatrixWithoutPitch().invert().translate(0.55f, -0.35f + (player.bobbing * 0.325f), 0.f).rotateX((float) Math.toRadians((handTilt() * -88) + (HandManager.getTilt() / 2))).scale(0.1375f, 0.1375f, 0.5f).get(stack.mallocFloat(16)));
                }
                drawCube();
            }
        }
    }
    public static boolean reverseChecker = false;
    public static int checkerStepX = 0;
    public static int checkerStepY = 0;
    public static void render(Window window) throws IOException {
        if (!Main.isClosing) {
            offsetIdx++;
            if (offsetIdx > 15) {
                offsetIdx = 0;
            }
            boolean tiltShift = false;
            boolean dof = false;
            if (player.inv.open) {
                dof = true;
            }
            if (player.blockBreathing.x() == 1) {
                tiltShift = true;
                dof = true;
            }
            if (isSwappingWorldType) {
                tiltShift = true;
            }
            if (forceTiltShift) {
                tiltShift = true;
            }

            glBindFramebuffer(GL_FRAMEBUFFER, rasterFBOId);
            raster.bind();
            glClearColor(0, 0, 0, 0);
            glClearDepthf(0.f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            updateBuffers();
            updateUniforms(raster, window);
            glUniform2i(raster.uniforms.get("res"), window.getWidth(), window.getHeight());
            glUniform1i(raster.uniforms.get("alwaysUpfront"), 0);
            glUniform1i(raster.uniforms.get("tex"), 0); //not rendering item
            glUniform1i(raster.uniforms.get("instanced"), 1);
            //drawDebugRooms();
            drawGas();
            drawClouds();
            drawStars();
            drawItems();
            glUniform1i(raster.uniforms.get("instanced"), 0);
            glUniform1i(raster.uniforms.get("tex"), 0); //not rendering item
            drawSunAndMoon();
//            drawCenter();
//            drawDebugWheel();
//            drawHuman();
            drawPlayer();

            glBindFramebuffer(GL_FRAMEBUFFER, upscale ? sceneFBOId : uncheckerFBOId);
            scene.bind();
            glClearColor(0, 0, 0, 0);
            glClearDepthf(0.f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            updateUniforms(scene, window);
            glUniform2i(scene.uniforms.get("res"), window.getWidth(), window.getHeight());
            glUniform1i(scene.uniforms.get("upscale"), upscale ? 1 : 0);
            bindTextures();
            if (upscale) {
                checkerStepX++;
                if (checkerStepX > 1) {
                    checkerStepX = 0;
                    checkerStepY++;
                    if (checkerStepY > 1) {
                        checkerStepY = 0;
                        reverseChecker = !reverseChecker;
                    }
                }
                glUniform1i(scene.uniforms.get("reverseChecker"), reverseChecker ? 1 : 0);
                glUniform2i(scene.uniforms.get("checkerStep"), checkerStepX, checkerStepY);

                drawHalf();

                glBindFramebuffer(GL_FRAMEBUFFER, uncheckerFBOId);
                unchecker.bind();
                glBindTextureUnit(0, Textures.sceneColor.id);
                draw();
            } else {
                draw();
            }

            glBindFramebuffer(GL_FRAMEBUFFER, sceneFBOId);
            aa.bind();
            glUniform2i(aa.uniforms.get("res"), window.getWidth(), window.getHeight());
            glUniform1i(aa.uniforms.get("upscale"), upscale ? 1 : 0);
            glUniform1i(aa.uniforms.get("taa"), taa ? 1 : 0);
            try(MemoryStack stack = MemoryStack.stackPush()) {
                glUniformMatrix4fv(aa.uniforms.get("projection"), false, window.updateProjectionMatrix().get(stack.mallocFloat(16)));
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                glUniformMatrix4fv(aa.uniforms.get("view"), false, viewMatrix.get(stack.mallocFloat(16)));
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                glUniformMatrix4fv(aa.uniforms.get("prevView"), false, prevViewMatrix.get(stack.mallocFloat(16)));
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                glUniformMatrix4fv(aa.uniforms.get("prevProj"), false, prevProjMatrix.get(stack.mallocFloat(16)));
            }
            glUniform1i(aa.uniforms.get("offsetIdx"), offsetIdx);
            glUniform1i(aa.uniforms.get("offsetIdxOld"), offsetIdxOld);
            glBindTextureUnit(0, Textures.sceneColorOld.id);
            glBindTextureUnit(1, Textures.scene.id);
            draw();
            glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 0, 0, window.getWidth(), window.getHeight(), 0);

            glBindFramebuffer(GL_FRAMEBUFFER, blurryFBOId);
            glClearColor(0, 0, 0, 0);
            glClear(GL_COLOR_BUFFER_BIT);
            blur.bind();
            glUniform2i(blur.uniforms.get("res"), window.getWidth(), window.getHeight());
            glUniform2f(blur.uniforms.get("dir"), 1f, 0f);
            glBindTextureUnit(0, Textures.sceneColor.id);
            draw();
            glBindFramebuffer(GL_FRAMEBUFFER, blurredFBOId);
            glClearColor(0, 0, 0, 0);
            glClear(GL_COLOR_BUFFER_BIT);
            glUniform2f(blur.uniforms.get("dir"), 0f, 1f);
            glBindTextureUnit(0, Textures.blurry.id);
            draw();

            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            gui.bind();
            screenshot(window);
            glClearDepthf(0.f);
            glClear(GL_DEPTH_BUFFER_BIT);
            GUI.updateGUI(window);
            glUniform2i(gui.uniforms.get("res"), window.getWidth(), window.getHeight());
            glUniform4f(gui.uniforms.get("color"), -1f, -1f, -1f, -1f);
            glUniform1i(gui.uniforms.get("tiltShift"), tiltShift ? 1 : 0);
            glUniform1i(gui.uniforms.get("dof"), dof ? 1 : 0);
            try(MemoryStack stack = MemoryStack.stackPush()) {
                glUniformMatrix4fv(Renderer.gui.uniforms.get("model"), false, new Matrix4f().get(stack.mallocFloat(16)));
            }
            draw();
            if (showUI && !Main.isSwappingWorldType) {
                GUI.draw(window);
            }
            GUI.drawAlwaysVisible(window);

            prevViewMatrix = new Matrix4f(viewMatrix);
            prevProjMatrix = new Matrix4f(projMatrix);
            offsetIdxOld = offsetIdx;
        }
    }

    public static float handTilt() {
        return (player.getCameraMatrix().invert().translate(0, 0, 1).getTranslation(new Vector3f()).y()-(player.getCameraMatrixWithoutPitch().invert().getTranslation(new Vector3f()).y()));
    }

    public static void screenshot(Window window) throws IOException {
        if (screenshot) {
            screenshot = false;
            float[] data = new float[4 * window.getWidth() * window.getHeight()];
            glReadPixels(0, 0, window.getWidth(), window.getHeight(), GL_RGBA, GL_FLOAT, data);

            ColorModel colorModel = new ComponentColorModel(
                    ColorSpace.getInstance(ColorSpace.CS_sRGB),
                    new int[]{10, 10, 10},               // bits per component
                    false,              // no alpha
                    false,              // not premultiplied
                    Transparency.OPAQUE,
                    DataBuffer.TYPE_USHORT // store in 16-bit unsigned short
            );
            SampleModel sampleModel = new PixelInterleavedSampleModel(
                    DataBuffer.TYPE_USHORT,
                    window.getWidth(),
                    window.getHeight(),
                    3,                  // number of bands
                    window.getWidth() * 3,          // scanline stride
                    new int[]{0, 1, 2}  // band offsets
            );
            DataBuffer dataBuffer = new DataBufferUShort(window.getWidth() * window.getHeight() * 3);
            WritableRaster imgRaster = Raster.createWritableRaster(sampleModel, dataBuffer, null);
            BufferedImage image = new BufferedImage(colorModel, imgRaster, false, null);
            int i = 0;
            for (int h = window.getHeight()-1; h >= 0; h--) {
                for (int w = 0; w < window.getWidth(); w++) {
                    imgRaster.setPixel(w, h, new int[]{(int)Math.min(65534, data[i++]*65534), (int)Math.min(65534, data[i++]*65534), (int)Math.min(65534, data[i++]*65534)});
                    i++;
                }
            }
            String dir = Main.mainFolder+"screenshots/";
            Path path = Path.of(dir);
            Files.createDirectories(path);
            int num = 0;
            File file = new File(dir+num+".png");
            while (file.exists()) {
                num++;
                file = new File(dir+num+".png");
            }
            ImageIO.write(image, "png", file);
        }
    }
}