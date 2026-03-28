package org.voxeleers.game.gameplay;

import org.voxeleers.Main;
import org.voxeleers.engine.Camera;
import org.voxeleers.engine.Utils;
import org.voxeleers.game.audio.AudioController;
import org.voxeleers.game.audio.Sounds;
import org.voxeleers.game.audio.Source;
import org.voxeleers.game.blocks.BlockTags;
import org.voxeleers.game.blocks.types.BlockTypes;
import org.voxeleers.game.items.Item;
import org.voxeleers.game.rendering.Renderer;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Room;
import org.voxeleers.game.rooms.Rooms;
import org.voxeleers.game.world.World;
import org.joml.*;

import java.io.*;
import java.lang.Math;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.voxeleers.game.gameplay.Inventory.invPath;

public class Player {
    public final Camera camera = new Camera();
    public Vector3f pos = new Vector3f();
    public Vector3f selectedBlock = new Vector3f();
    public Vector3f prevSelectedBlock = new Vector3f();
    public Inventory inv = new Inventory();

    public boolean chiselMode = false;
    public boolean bobbingDir = true;
    public float bobbing = 0f;
    public float dynamicSpeedOld = 0;
    public float dynamicSpeed = 0;
    public float scale = 2f;
    public float baseEyeHeight = 1.625f*scale;
    public float eyeHeight = baseEyeHeight;
    public float baseHeight = eyeHeight+(0.175f*scale);
    public float height = baseHeight;
    public float bobbingScale = (height*-0.05f)/scale;
    public float width = 0.2f*scale;
    public float baseSpeed = Math.max(0.115f, 0.115f*scale);
    public float speed = baseSpeed;
    public float sprintSpeed = 1.5f;
    public float airSpeed = 0.33f;
    public boolean onGround = false;
    public boolean crawling = false;
    public boolean crouching = false;
    public boolean sprint = false;
    public boolean superSprint = false;
    public boolean forward = false;
    public boolean backward = false;
    public boolean rightward = false;
    public boolean leftward = false;
    public boolean upward = false;
    public boolean downward = false;
    public boolean flying = false;
    public boolean creative = false;

    public final Source breakingSource;
    public final Source windSource;

    public Player(Vector3f newPos) {
        breakingSource = new Source(newPos, 1, 1, 0, 1);
        windSource = new Source(newPos, 0, 1, 0, 1);
        setPos(newPos);
        oldPos = newPos;
    }

    public static Path plrPath = Path.of(Main.mainFolder+"world0/player.data");
    public static void create() throws IOException {
        //long plrInitStarted = System.currentTimeMillis();
        if (Files.exists(plrPath)) {
            int[] plrData = Utils.flipIntArray(Utils.byteArrayToIntArray(new FileInputStream(plrPath.toFile()).readAllBytes()));
            int i = 0;
            Main.player = new Player(new Vector3f(plrData[i++]/1000f, plrData[i++]/1000f, plrData[i++]/1000f));
            float[] camMatrix = new float[16];
            for (int cI = 0; cI < 16; cI++) {
                camMatrix[cI] = plrData[i++]/1000f;
            }
            Main.player.setCameraMatrix(camMatrix);
            Main.player.camera.pitch.set(plrData[i++]/1000f, plrData[i++]/1000f, plrData[i++]/1000f, plrData[i++]/1000f);
            Main.player.movement.set(plrData[i++]/1000f, plrData[i++]/1000f, plrData[i++]/1000f);
            Main.player.vel.set(plrData[i++]/1000f, plrData[i++]/1000f, plrData[i++]/1000f);
            Main.player.creative = plrData[i++] != 0;
            Main.player.flying = plrData[i++] != 0;
        } else {
            Main.player = new Player(new Vector3f(522, 97, 500));
            Main.player.setCameraMatrix(new Matrix4f().get(new float[16]));
        }
        if (Files.exists(invPath)) {
            Main.player.inv.load();
        } else {
            Main.player.inv.init();
        }
        //System.out.print("Took "+String.format("%.2f", (System.currentTimeMillis()-plrInitStarted)/1000.f)+"s to load player.\n");
    }
    public void save() throws IOException {
        FileOutputStream out = new FileOutputStream(plrPath.toFile());
        float[] cam = new float[16];
        camera.getViewMatrixWithoutPitch().get(cam);
        int[] data = new int[31];
        int i = 0;
        data[i++] = (int)(pos.x()*1000);
        data[i++] = (int)(pos.y()*1000);
        data[i++] = (int)(pos.z()*1000);
        for (int cI = 0; cI < 16; cI++) {
            data[i++] = (int)(cam[cI]*1000);
        }
        data[i++] = (int)(camera.pitch.x()*1000);
        data[i++] = (int)(camera.pitch.y()*1000);
        data[i++] = (int)(camera.pitch.z()*1000);
        data[i++] = (int)(camera.pitch.w()*1000);
        data[i++] = (int)(movement.x()*1000);
        data[i++] = (int)(movement.y()*1000);
        data[i++] = (int)(movement.z()*1000);
        data[i++] = (int)(vel.x()*1000);
        data[i++] = (int)(vel.y()*1000);
        data[i++] = (int)(vel.z()*1000);
        data[i++] = Main.player.creative ? 1 : 0;
        data[i++] = Main.player.flying ? 1 : 0;
        out.write(Utils.intArrayToByteArray(data));
        out.close();
        inv.save();
    }

    public void clearVars() {
        forward = false;
        backward = false;
        rightward = false;
        leftward = false;
        upward = false;
        downward = false;
    }

    public Vector2i blockOn = new Vector2i(0);
    public Vector2i blockIn = new Vector2i(0);
    public Vector2i blockBreathing = new Vector2i(0);
    boolean submerged = false;

    public float bounciness = 0.66f;
    public float friction = 0.75f;
    public float grav = 0.1f;
    public float jumpStrength = Math.max(0.25f, 0.25f*scale);
    public long lastJump = 1000;
    public long jump = 0;
    public boolean setSolidBlockOn = false;
    public boolean solid(float x, float y, float z, boolean recordFriction, boolean recordBounciness) {
        Vector2i block = World.getBlockNotNull(x, y, z);
        int typeId = block.x;
        if (BlockTypes.blockTypeMap.get(typeId).blockProperties.isCollidable) {
            if (Renderer.collisionData[(1024 * ((typeId * 8) + (int) ((x - Math.floor(x)) * 8))) + (block.y() * 64) + ((Math.abs(((int) ((y - Math.floor(y)) * 8)) - 8) - 1) * 8) + (int) ((z - Math.floor(z)) * 8)]) {
                if (recordFriction) {
                    if (typeId == 7) { //kyanite
                        friction = Math.min(friction, 0.95f);
                    } else if (typeId == 11 || typeId == 12 || typeId == 13 || typeId == 66 || typeId == 67) { //glass
                        friction = Math.min(friction, 0.85f);
                    } else if (BlockTags.planks.tagged.contains(block.x)) { //wood
                        friction = Math.min(friction, 0.5f);
                    } else if (BlockTypes.blockTypeMap.get(typeId).blockProperties.isCollidable) {
                        friction = Math.min(friction, 0.75f);
                    }
                    blockOn = block;
                    setSolidBlockOn = true;
                }
                if (recordBounciness) {
                    if (typeId == 7) { //kyanite
                        bounciness = Math.min(bounciness, -2f);
                    } else if (typeId == 11 || typeId == 12 || typeId == 13 || typeId == 66 || typeId == 67) { //glass
                        bounciness = Math.min(bounciness, -0.33f);
                    }
                }
                return true;
            }
        }
        if (!setSolidBlockOn && recordFriction) {
            blockOn = block;
        }
        return false;
    }
    public boolean solid(float x, float y, float z, float w, float h, boolean recordFriction, boolean recordBounciness) {
        setSolidBlockOn = false;
        boolean returnValue = false;
        for (float newX = x-w; newX <= x+w; newX+=0.125f) {
            for (float newY = y; newY <= y + h; newY += 0.125f) {
                for (float newZ = z - w; newZ <= z + w; newZ += 0.125f) {
                    if (solid(newX, newY, newZ, recordFriction, recordBounciness)) {
                        if (recordFriction) {
                            returnValue = true;
                        } else {
                            return true;
                        }
                    }
                }
            }
        }
        return returnValue;
    }

    public Vector3f oldPos;
    public Vector3i blockPos;
    public Vector3f vel = new Vector3f(0f);
    public Vector3f movement = new Vector3f(0f);
    public float prevStrafe = 0.f;
    public float strafe = 0.f;
    public float prevTilt = 0.f;
    public float tilt = 0.f;
    public void tick() {
        pickupTick();
        movementTick();
        HandManager.tick();
    }

    public void pickupTick() {
        for (Item item : World.items) {
            if (item.timeExisted > 500 && item.pos.y() >= pos.y() && item.pos.y() <= pos.y()+height && new Vector2f(item.pos.x(), item.pos.z()).distance(new Vector2f(pos.x(), pos.z())) < 1f) { //500ms = 0.5s
                World.items.remove(item);
                item = inv.addToInventory(item, true);
                if (item != null && item.amount > 0) {
                    World.items.add(item);
                }
            }
        }
    }

    public Vector3f oldCamTranslation = new Vector3f();

    public void movementTick() {
        bobbingScale = (height*-0.05f)/scale;
        oldCamTranslation.set(getCameraTranslation());
        prevStrafe = strafe;
        prevTilt = tilt;
        if (!creative) {
            flying = false;
        }
        friction = 1f;
        boolean prevOnGround = onGround;
        onGround = solid(pos.x, pos.y-0.125f, pos.z, width, 0.125f, true, false);
        if (flying || !onGround) {
            onGround = false;
            crouching = false;
            crawling = false;
        } else if (!prevOnGround) { //when landing from a fall
            bobbing = bobbingScale;
            bobbingDir = false;
        }
        blockBreathing = World.getBlockNotNull(blockPos.x, (blockPos.y+eyeHeight-bobbing)+1, blockPos.z);
        blockIn = World.getBlockNotNull(blockPos.x, blockPos.y, blockPos.z);
        submerged = BlockTypes.blockTypeMap.get(blockBreathing.x).blockProperties.isFluid;
        boolean canMove = (flying || onGround || blockIn.x == 1);
        if (rightward) {
            strafe = (sprint ? -0.02f : -0.01f) * (canMove ? 1 : airSpeed);
        } else if (leftward) {
            strafe = (sprint ? 0.02f : 0.01f) * (canMove ? 1 : airSpeed);
        } else {
            strafe = 0.f;
        }
        if (forward) {
            tilt = (sprint ? -0.02f : -0.01f) * (canMove ? 1 : airSpeed);
        } else if (backward) {
            tilt = (sprint ? 0.02f : 0.01f) * (canMove ? 1 : airSpeed);
        } else {
            tilt = 0.f;
        }
        speed = baseSpeed;
        boolean hittingCeiling = solid(pos.x, pos.y+height, pos.z, width, 0.125f, false, false);
        boolean mightBeCrawling = false;
        if (!crouching && !hittingCeiling) {
            height = Math.min(height+0.125f, baseHeight);
            eyeHeight = Math.min(eyeHeight+0.125f, baseEyeHeight);
        } else {
            mightBeCrawling = true;
        }
        boolean realSprint = sprint;
        boolean realSuperSprint = superSprint;
        if (crouching || mightBeCrawling) {
            if (sprint) {
                crawling = true;
            } else {
                crawling = false;
            }
            if (!crawling) {
                if (height < baseHeight * 0.83f) {
                    speed = baseSpeed*0.5f;
                    if (!hittingCeiling) {
                        height = Math.min(height + 0.125f, baseHeight * 0.83f);
                        eyeHeight = Math.min(eyeHeight + 0.125f, baseEyeHeight * 0.83f);
                    }
                } else {
                    speed = baseSpeed * 0.75f;
                    if (crouching) {
                        height = Math.max(height - 0.125f, baseHeight * 0.83f);
                        eyeHeight = Math.max(eyeHeight - 0.125f, baseEyeHeight * 0.83f);
                    }
                }
            } else {
                speed = baseSpeed*0.5f;
                height = Math.max(height-0.125f, baseHeight*0.5f);
                eyeHeight = Math.max(eyeHeight-0.125f, baseEyeHeight*0.5f);
            }

            realSprint = false;
            realSuperSprint = false;
        }
        float modifiedSpeed = speed;
        float modifiedGrav = grav;
        if (!flying) {
            if (blockIn.x == 1) { //water
                modifiedGrav *= 0.2f;
                friction *= 0.9f;
                modifiedSpeed *= 0.5f;
            } else if (BlockTags.leaves.tagged.contains(blockIn.x)) { //leaves
                if (blockIn.y == 0) {
                    friction *= 0.5f;
                    modifiedSpeed *= 0.5f;
                } else {
                    friction *= 0.9f;
                    modifiedSpeed *= 0.9f;
                }
            }
            friction = Math.min(0.99f, friction); //1-airFriction=maxFriction
        } else {
            friction = 0.5f;
        }
        Vector3f newMovement = new Vector3f(0f);
        if (forward || backward) {
            Vector3f translatedPos = new Matrix4f(getCameraWithoutPitchOrProcessing()).translate(0, 0, modifiedSpeed * (realSprint || realSuperSprint ? (backward ? (realSuperSprint && realSprint ? 100 : (realSuperSprint ? 10 : 1.25f)) : (flying ? (realSuperSprint ? 100 : 10) : sprintSpeed)) : 1) * (forward ? 1 : -1)).getTranslation(new Vector3f());
            newMovement.add(pos.x - translatedPos.x,0, pos.z - translatedPos.z);
        }
        if (rightward || leftward) {
            Vector3f translatedPos = new Matrix4f(getCameraWithoutPitchOrProcessing()).translate(modifiedSpeed * (realSprint || realSuperSprint ? (flying ? (realSuperSprint ? 100 : 10) : sprintSpeed) : 1) * (rightward ? -1 : 1), 0, 0).getTranslation(new Vector3f());
            newMovement.add(pos.x - translatedPos.x, 0, pos.z - translatedPos.z);
        }
        if (upward || downward) {
            if (flying) {
                Vector3f translatedPos = new Matrix4f(getCameraWithoutPitchOrProcessing()).translate(0, speed * (downward ? (realSprint || realSuperSprint ? (realSuperSprint ? 50 : 5) : 1f) : -1.2f * (realSprint || realSuperSprint ? (realSuperSprint ? 50 : 5) : 1)), 0).getTranslation(new Vector3f());
                newMovement.add(0, pos.y - translatedPos.y, 0);
            } else if (blockIn.x == 1 && submerged) {
                Vector3f translatedPos = new Matrix4f(getCameraWithoutPitchOrProcessing()).translate(0, speed * (upward ? -2 : downward ? 1 : 0), 0).getTranslation(new Vector3f());
                newMovement.add(0, pos.y - translatedPos.y, 0);
            }
        }
        if (!canMove) {
            newMovement.mul(airSpeed);
        }
        movement = new Vector3f(Utils.furthestFromZero(newMovement.x, movement.x*friction), Utils.furthestFromZero(newMovement.y, movement.y*friction), Utils.furthestFromZero(newMovement.z, movement.z*friction));
        vel = new Vector3f(vel.x*friction, vel.y*friction, vel.z*friction);

        if (!flying && vel.y >= modifiedGrav*-30 && !onGround) {
            vel.set(vel.x, vel.y-modifiedGrav, vel.z);
        }

        if (Main.timeMS-jump < 100 && !flying) { //prevent jumping when space bar was pressed longer than 0.1s ago or when flying
            if ((onGround || (blockIn.x == 1 && solid(pos.x, pos.y, pos.z, width*1.125f, height, false, false))) && !submerged) {
                bobbing = bobbingScale;
                bobbingDir = false;
                jump = 1000;
                lastJump = Main.timeMS;
                vel.y = Math.max(vel.y, jumpStrength);
            }
        }
        vel = new Vector3f(Math.clamp(vel.x, -3, 3), Math.clamp(vel.y, -3, 3), Math.clamp(vel.z, -3, 3));
        float mX = (vel.x + movement.x)/5;
        float mY = (vel.y + movement.y)/5;
        float mZ = (vel.z + movement.z)/5;
        Vector3f hitPos = pos;
        Vector3f destPos = new Vector3f(mX+pos.x, mY+pos.y, mZ+pos.z);
        Float maxX = null;
        Float maxY = null;
        Float maxZ = null;
        float detail = 0.1f;
        Vector3f dif = new Vector3f(destPos).sub(pos);
        Vector3f inc = new Vector3f(detail).mul(new Vector3f(dif).div(Math.max(Math.abs(dif.x()), Math.max(Math.abs(dif.y()), Math.abs(dif.z())))));
        Vector3f stepsVec = new Vector3f(dif).div(detail).absolute();
        float maxInc = Math.max(stepsVec.x(), Math.max(stepsVec.y(), stepsVec.z()));
        int steps = (int) (maxInc/detail);
        for (int i = 0; i < steps; i++) {
            Vector3f rayPos = new Vector3f(maxX != null ? maxX : pos.x()+(inc.x()*i), maxY != null ? maxY : pos.y()+(inc.y()*i), maxZ != null ? maxZ : pos.z()+(inc.z()*i));
            if (crouching && onGround) {
                if (!solid(rayPos.x, hitPos.y - 0.125f, hitPos.z, width, 0.125f, true, false)) {
                    maxX = hitPos.x;
                    rayPos.x = hitPos.x;
                }
                if (!solid(hitPos.x, hitPos.y - 0.125f, rayPos.z, width, 0.125f, true, false)) {
                    maxZ = hitPos.z;
                    rayPos.z = hitPos.z;
                }
            }
            if (solid(rayPos.x, rayPos.y, rayPos.z, width, height, false, false)) {
                if (maxX == null) {
                    bounciness = 0.66f;
                    if (solid(rayPos.x, hitPos.y, hitPos.z, width, height, false, !flying)) {
                        bounciness = Math.max(bounciness, -0.66f);
                        maxX = hitPos.x;
                        vel.x *= bounciness;
                        movement.x *= bounciness;
                    }
                }
                if (maxY == null) {
                    bounciness = 0.66f;
                    if (solid(hitPos.x, rayPos.y, hitPos.z, width, height, false, !flying)) {
                        bounciness = (upward && mY <= 0.f) ? bounciness : Math.max(bounciness, -0.66f); //dont limit bounciness if jumping and moving downwards
                        maxY = hitPos.y;
                        vel.y *= bounciness;
                        movement.y *= bounciness;
                    }
                }
                if (maxZ == null) {
                    bounciness = 0.66f;
                    if (solid(hitPos.x, hitPos.y, rayPos.z, width, height, false, !flying)) {
                        bounciness = Math.max(bounciness, -0.66f);
                        maxZ = hitPos.z;
                        vel.z *= bounciness;
                        movement.z *= bounciness;
                    }
                }
                if (maxX != null && maxY != null && maxZ != null) {
                    break;
                }
            } else {
                hitPos = rayPos;
            }
        }
        Vector3f prevPos = new Vector3f(pos);
        setPos(hitPos);
        dynamicSpeedOld = dynamicSpeed;
        dynamicSpeed = Math.min(1.f, pos.distance(prevPos));
    }

    public void setPos(Vector3f newPos) {
        oldPos = pos;
        pos = newPos;
        blockPos = new Vector3i((int) newPos.x, (int) newPos.y, (int) newPos.z);
        doSounds();
    }

    public long timeSinceAmbientSoundAttempt = 0;
    public float windGain = 0f;
    int ambientWind = 0;
    public void doSounds() {
        Matrix4f cam = getCameraMatrix();
        Vector3f forward = new Vector3f();
        Vector3f up = new Vector3f();
        cam.positiveZ(forward).negate();
        cam.positiveY(up);
        AudioController.setListenerData(new Vector3f(pos.x(), pos.y()+eyeHeight, pos.z()), vel, new float[]{forward.x(), forward.y(), forward.z(), up.x(), up.y(), up.z()});
        Room room = Rooms.getRoom(blockPos);
        Cell cell = World.worldType.getGlobalAtmo();// room == null ? World.worldType.getGlobalAtmo() : room.cells.get(Rooms.packCellPos(blockPos));
        double pressure = cell.getPressure();
        if (pressure > 0.05f) {
            long currentTime = Main.timeMS;
            if (currentTime - timeSinceAmbientSoundAttempt >= 1000) {
                timeSinceAmbientSoundAttempt = currentTime;
                if (windSource.soundPlaying == -1) {
                    windSource.play(Sounds.WIND);
                }
                if (World.inBounds(blockPos.x, blockPos.y, blockPos.z)) {
                    int sunLight = World.getLight(blockPos).w;
                    ambientWind = Math.min(333, ambientWind + sunLight);
                    windGain = ambientWind / 333f;
                }
            }
            float velocity = (Math.max(Math.abs(vel.x + movement.x), Math.max(Math.abs(vel.y + movement.y), Math.abs(vel.z + movement.z))));
            windSource.setGain(Math.clamp(windGain + (Math.max(0.05f, velocity / 10) - 0.05f), 0, 1));
            ambientWind = Math.max(0, ambientWind - 1);
        } else {
            windSource.setGain(0.f);
        }
    }

    public void setCameraMatrix(float[] matrix) {
        camera.setViewMatrix(matrix);
        camera.move(0, camera.getViewMatrix().getTranslation(new Vector3f()).y(), 0, false);
    }
    public Vector3f getCameraTranslation() {
        Vector3f translation = new Vector3f();
        camera.getViewMatrix().getTranslation(translation);
        return translation.add(pos.x(), pos.y()+eyeHeight+bobbing, pos.z());
    }
    public Matrix4f getCameraWithoutPitchOrProcessing() {
        Vector3f camOffset = new Vector3f();
        Matrix4f camMatrix = new Matrix4f(camera.getViewMatrixWithoutPitch());
        camMatrix.getTranslation(camOffset);
        return camMatrix.setTranslation(camOffset.x+pos.x, camOffset.y+pos.y, camOffset.z+pos.z);
    }
    public Matrix4f getCameraMatrixWithoutPitchTilt() {
        return new Matrix4f(camera.getViewMatrixWithoutPitch()).setTranslation(Utils.getInterpolatedVec(oldCamTranslation, getCameraTranslation())).invert();
    }
    public Matrix4f getCameraMatrix() {
        return new Matrix4f(camera.getViewMatrix()).setTranslation(Utils.getInterpolatedVec(oldCamTranslation, getCameraTranslation()))
                .rotateX(Utils.getInterpolatedFloat(prevTilt, tilt)).rotateZ(Utils.getInterpolatedFloat(prevStrafe, strafe)).invert();
    }

    public void rotate(float pitch, float yaw) {
        camera.rotate(pitch, yaw);
    }
}