package org.voxeleers.game.gui;

import org.joml.*;
import org.lwjgl.opengl.GL12;
import org.voxeleers.Main;
import org.voxeleers.engine.Engine;
import org.voxeleers.engine.Utils;
import org.voxeleers.engine.Window;
import org.voxeleers.game.audio.AudioController;
import org.voxeleers.game.elements.Element;
import org.voxeleers.game.elements.Elements;
import org.voxeleers.game.gameplay.HandManager;
import org.voxeleers.game.gui.buttons.*;
import org.voxeleers.game.gui.sliders.Slider;
import org.voxeleers.game.gui.sliders.VolumeSlider;
import org.voxeleers.game.items.Item;
import org.voxeleers.game.items.ItemType;
import org.voxeleers.game.items.ItemTypes;
import org.voxeleers.game.rendering.Models;
import org.voxeleers.game.rendering.Renderer;
import org.voxeleers.game.rendering.Texture3D;
import org.voxeleers.game.rendering.Textures;
import org.voxeleers.game.rooms.Cell;
import org.voxeleers.game.rooms.Molecule;
import org.voxeleers.game.rooms.Room;
import org.voxeleers.game.rooms.Rooms;
import org.voxeleers.game.world.World;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.Math;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.voxeleers.game.gameplay.Inventory.invWidth;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

public class GUI {
    public static float guiScale = 1;
    public static float guiScaleMul = 4f; //even-though it's a float, should always be set to a round number to prevent distortion of pixel-art
    public static float aspectRatio = 0f;
    public static int width = 0;
    public static int height = 0;

    public static int hotbarSizeX = 282;
    public static int hotbarSizeY = 22;
    public static float hotbarPosX = 0.f;
    public static float hotbarPosY = 0.f;
    public static int slotSize = 20;
    public static int slotSizeY = 22;
    public static int enlargedSlotSize = 24;

    public static boolean audioSettingMenuOpen = false;
    public static boolean controlsSettingMenuOpen = false;
    public static boolean graphicsSettingMenuOpen = false;
    public static boolean accessibilitySettingMenuOpen = false;
    public static boolean settingMenuOpen = false;
    public static boolean pauseMenuOpen = false;
    public static boolean inventoryOpen = false;

    public static Slider drawingSlider = null;
    public static float sliderX = 0.f;
    public static List<Slider> sliders = new ArrayList<>();
    public static Button drawingButton = null;
    public static List<Button> buttons = new ArrayList<>();

    public static void update(Window window) {
        glBindTextureUnit(0, Textures.sceneColor.id);
        glBindTextureUnit(1, Textures.blurred.id);
        glBindTextureUnit(2, Textures.gui.id);
        glBindTextureUnit(3, Textures.items.id);
        width = window.getWidth();
        height = window.getHeight();
        guiScaleMul = (int) (Math.min(width, height) / 270f);
        guiScale = width / guiScaleMul;
        aspectRatio = (float) width / height;
        buttons.clear();
        sliders.clear();
    }

    public static void tick(Window window) {
        Vector2i cursorPos = new Vector2i(cursorPxX(), cursorPxY());
        glUniform4f(Renderer.gui.uniforms.get("color"), 1.f, 1.f, 1.f, 1.f);
        glUniform1i(Renderer.gui.uniforms.get("tex"), 0); //use gui atlas
        glUniform1i(Renderer.gui.uniforms.get("layer"), 5); //button
        for (Button button : buttons) {
            if (cursorPos.x() > button.bounds.x() && cursorPos.x() < button.bounds.z() && cursorPos.y() > button.bounds.y() && cursorPos.y() < button.bounds.w()) {
                Vector2i borderData = getButtonBorderData(button.width);
                glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), 0, borderData.y() + 128);
                drawQuad(false, false, (float) button.bounds.x() / width, (float) button.bounds.y() / height, button.width, 16);
                if (Main.isLMBClick) {
                    button.clicked();
                }
            }
        }
        for (Slider slider : sliders) {
            if (cursorPos.x() > slider.bounds.x() && cursorPos.x() < slider.bounds.z() && cursorPos.y() > slider.bounds.y() && cursorPos.y() < slider.bounds.w()) {
                glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), 277, 320);
                drawQuad(true, false, (float) cursorPos.x() / width, (float) slider.bounds.y() / height, 5, 16);
                if (Main.wasLMBDown) {
                    slider.clicked(cursorPos.x());
                }
            }
        }
    }

    public static void drawAlwaysVisible(Window window) {
        if (Main.isSaving || pauseMenuOpen) {
            glUniform4f(Renderer.gui.uniforms.get("color"), 1.f, 1.f, 1.f, 1.f);
            glUniform1i(Renderer.gui.uniforms.get("tex"), 0); //use gui atlas
            Vector2i border = new Vector2i((int) ((32 * (width / 3840f)) / guiScaleMul), (int) ((32 * (height / 2180f)) / guiScaleMul));
            if (Main.isSaving) {
                drawText(false, 0, 0, 2 + border.x(), 2 + border.y(), "Saving data...".toCharArray());
            }
            if (Main.isSwappingWorldType || pauseMenuOpen) {
                if (Main.isSwappingWorldType) {
                    drawText(false, 0, 1, 2 + border.x(), -2 - border.y() - charHeight, ("Travelling to " + World.nextWorldType.getWorldTypeName() + " from " + World.worldType.getWorldTypeName()).toCharArray());
                }
                glUniform1i(Renderer.gui.uniforms.get("layer"), 3); //frame
                glUniform2i(Renderer.gui.uniforms.get("size"), 3840, 2160);
                glUniform2i(Renderer.gui.uniforms.get("scale"), width, (int) (height * aspectRatio));
                glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), 0, 0);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    glUniformMatrix4fv(Renderer.gui.uniforms.get("model"), false, new Matrix4f().get(stack.mallocFloat(16)));
                }
                Renderer.draw();
            }
        }

        glUniform4f(Renderer.gui.uniforms.get("color"), 1.f, 1.f, 1.f, 1.f);
        glUniform1i(Renderer.gui.uniforms.get("tex"), 0); //use gui atlas
        glUniform1i(Renderer.gui.uniforms.get("layer"), 0); //text
        if (audioSettingMenuOpen) {
            drawText(true, 0.5f, 1, 0, -10 - charHeight, "Audio Settings".toCharArray());
            drawingButton = new BackButton();
            drawButton(true, 0.5f, 0.5f, 0, (charHeight * 5) + 2, "Back To Settings Menu".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            drawingSlider = new VolumeSlider();
            sliderX = AudioController.masterVolume/2.f;
            drawSlider(true, 0.5f, 0.5f, 0, (charHeight * 3) + 1, ("Master Volume:"+String.format("%.1f", sliderX*200)+"%").toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            drawingButton = new MuteButton();
            drawButton(true, 0.5f, 0.5f, -35.5f, charHeight, (AudioController.muted ? "  Muted  " :  " Unmuted ").toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            drawingButton = new AudioChannelButton();
            drawButton(true, 0.5f, 0.5f, 35.5f, charHeight, AudioController.getOutputModeAsTxt().toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
        } else if (settingMenuOpen) {
            drawText(true, 0.5f, 1, 0, -10 - charHeight, "Settings".toCharArray());
            drawingButton = new BackButton();
            drawButton(true, 0.5f, 0.5f, 0, (charHeight * 5) + 2, "Back To Main Menu".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            drawingButton = new AudioSettingsButton();
            drawButton(true, 0.5f, 0.5f, -35.5f, (charHeight * 3) + 1, "  Audio  ".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            drawButton(true, 0.5f, 0.5f, 35.5f, (charHeight * 3) + 1, "Controls".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            drawButton(true, 0.5f, 0.5f, 0, charHeight, "    Graphics    ".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            drawButton(true, 0.5f, 0.5f, 0, (-charHeight)-1, "Accessibility".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
        } else if (pauseMenuOpen) {
            drawText(true, 0.5f, 1, 0, -10 - charHeight, "Paused".toCharArray());
            drawingButton = new BackButton();
            drawButton(true, 0.5f, 0.5f, 0, (charHeight * 5) + 2, "Continue Playing".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            char[] saveChars = "Save World".toCharArray();
            drawingButton = new SaveWorldButton();
            drawButton(true, 0.5f, 0.5f, -35.5f, (charHeight * 3) + 1, saveChars, new Vector4f(1.f), new Vector4f(1.f));
            drawingButton = new SettingsButton();
            drawButton(true, 0.5f, 0.5f, 35.5f, (charHeight * 3) + 1, "Settings".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            drawButton(true, 0.5f, 0.5f, 0, charHeight, "    Language    ".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            drawingButton = new QuitToMenuButton();
            drawButton(true, 0.5f, 0.5f, 0, (-charHeight) - 1, "Quit To Menu".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
            drawingButton = new QuitToDesktopButton();
            drawButton(true, 0.5f, 0.5f, 0, (charHeight * -3) - 2, "Quit To Desktop".toCharArray(), new Vector4f(1.f), new Vector4f(1.f));
        }
    }

    public static void drawDebug(Window window) {
        glUniform4f(Renderer.gui.uniforms.get("color"), 1.f, 1.f, 1.f, 0.5f);
        if (!Main.isSwappingWorldType && !pauseMenuOpen) {
            glUniform1i(Renderer.gui.uniforms.get("tex"), 0); //use gui atlas
            drawText(false, 0, 1, 2, -2 - charHeight, ((long) (Engine.avgMS) + "fps ").toCharArray());
            if (Main.showDebug) {
                drawText(false, 0, 1, 2, -2 - (charHeight * 2), (String.format("%.1f", 1000d / (Engine.avgMS)) + "ms").toCharArray());
                drawText(false, 0, 1, 2, -2 - (charHeight * 3), ((int) Main.player.pos.x + "x," + (int) Main.player.pos.y + "y," + (int) Main.player.pos.z + "z").toCharArray());
                Room room = Rooms.getRoom(Main.player.blockPos);
                Cell cell = new Cell(World.worldType.getGlobalAtmo());
                if (room != null) {
                    int xyz = Rooms.packCellPos(Main.player.blockPos);
                    cell = room.cells.get(xyz);
                }
                double temperature = cell.getTemperature();
                drawText(false, 0, 1, 2, -2 - (charHeight * 4), ("Pressure:" + String.format("%.2f", cell.getPressure() / 10000000.f) + "kPa Temperature:" + String.format("%.2f", temperature) + "K" + " Energy:" + cell.energy).toCharArray()); //258
                int i = 0;
                for (Molecule molecule : cell.molecules) {
                    Element element = Elements.elementMap.get(molecule.element);
                    String str = element.name + ":" + molecule.amount;
                    drawText(false, 0, 1, 2, -2 - (charHeight * (5 + (i++))), str.toCharArray());
                }
            }
        }
    }

    public static Vector2i getButtonBorderData(int buttonWidth) {
        if (buttonWidth > 211) {
            return new Vector2i(282, 0);
        } else if (buttonWidth > 140) {
            return new Vector2i(211, 16);
        } else if (buttonWidth > 69) {
            return new Vector2i(140, 32);
        } else if (buttonWidth > 47) {
            return new Vector2i(69, 48);
        } else if (buttonWidth > 31) {
            return new Vector2i(47, 64);
        } else if (buttonWidth > 15) {
            return new Vector2i(31, 80);
        } else {
            return new Vector2i(16, 96);
        }
    }

    public static void drawSlider(boolean centered, float offsetX, float offsetY, float offsetPX, float offsetPY, char[] chars, Vector4f bgColor, Vector4f txtColor) {
        glUniform1i(Renderer.gui.uniforms.get("layer"), 5); //button/slider
        Vector2i borderData = getButtonBorderData((charWidth * chars.length) + 6);
        glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), 0, borderData.y()+224);
        glUniform4f(Renderer.gui.uniforms.get("color"), bgColor.x(), bgColor.y(), bgColor.z(), bgColor.w());
        drawSlot(true, false, offsetX, offsetY, offsetPX - 1, offsetPY - 4, 0, 0, borderData.x() + 2, 16);
        drawingSlider = null;
        glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), 272, 320);
        glUniform4f(Renderer.gui.uniforms.get("color"), bgColor.x(), bgColor.y(), bgColor.z(), 1);
        float posX = (((sliderX-0.5f)*4)*borderData.x())/width;
        drawSlot(true, false, offsetX+posX, offsetY, offsetPX - 1, offsetPY - 4, 0, 0, 5, 16);
        sliderX = 0.f;

        glUniform1i(Renderer.gui.uniforms.get("layer"), 0); //text
        glUniform4f(Renderer.gui.uniforms.get("color"), txtColor.x(), txtColor.y(), txtColor.z(), txtColor.w());
        float size = chars.length * charWidth;
        float centeredOffset = centered ? size / 2 : 0.f;
        float offset = 0;
        for (char character : chars) {
            int charAtlasOffset = getCharAtlasOffset(character);
            if (charAtlasOffset >= 0) {
                glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), charAtlasOffset, 0);
                drawSlot(offsetX, offsetY, offsetPX + offset - centeredOffset, offsetPY, 0, 0, charWidth, charHeight);
            }
            offset += charWidth;
        }
    }

    public static void drawButton(boolean centered, float offsetX, float offsetY, float offsetPX, float offsetPY, char[] chars, Vector4f bgColor, Vector4f txtColor) {
        glUniform1i(Renderer.gui.uniforms.get("layer"), 5); //button
        Vector2i borderData = getButtonBorderData((charWidth * chars.length) + 6);
        glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), 0, borderData.y());
        glUniform4f(Renderer.gui.uniforms.get("color"), bgColor.x(), bgColor.y(), bgColor.z(), bgColor.w());
        drawSlot(true, false, offsetX, offsetY, offsetPX - 1, offsetPY - 4, 0, 0, borderData.x() + 2, 16);
        drawingButton = null;

        glUniform1i(Renderer.gui.uniforms.get("layer"), 0); //text
        glUniform4f(Renderer.gui.uniforms.get("color"), txtColor.x(), txtColor.y(), txtColor.z(), txtColor.w());
        float size = chars.length * charWidth;
        float centeredOffset = centered ? size / 2 : 0.f;
        float offset = 0;
        for (char character : chars) {
            int charAtlasOffset = getCharAtlasOffset(character);
            if (charAtlasOffset >= 0) {
                glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), charAtlasOffset, 0);
                drawSlot(offsetX, offsetY, offsetPX + offset - centeredOffset, offsetPY, 0, 0, charWidth, charHeight);
            }
            offset += charWidth;
        }
    }

    public static void drawText(boolean centered, float offsetX, float offsetY, float offsetPX, float offsetPY, char[] chars) {
        float size = chars.length * charWidth;
        float centeredOffset = centered ? size / 2 : 0.f;
        float offset = 0;
        for (char character : chars) {
            int charAtlasOffset = getCharAtlasOffset(character);
            if (charAtlasOffset >= 0) {
                glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), charAtlasOffset, 0);
                drawSlot(offsetX, offsetY, offsetPX + offset - centeredOffset, offsetPY, 0, 0, charWidth, charHeight);
            }
            offset += charWidth;
        }
    }

    public static void draw(Window window) {
        hotbarPosX = (0.5f - ((hotbarSizeX / 2f) / guiScale));
        hotbarPosY = 5.f / height;
        glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), 0, 0);
        glUniform1i(Renderer.gui.uniforms.get("tex"), 0);
        glUniform1i(Renderer.gui.uniforms.get("layer"), 1); //inventory
        float containerPosY = hotbarPosY + (((hotbarSizeY * 5) / guiScale) * aspectRatio);
        if (GUI.inventoryOpen) {
            glUniform4f(Renderer.gui.uniforms.get("color"), 0.85f, 0.85f, 0.85f, 0.85f);
            drawQuad(false, false, hotbarPosX, hotbarPosY + ((hotbarSizeY / guiScale) * aspectRatio), hotbarSizeX, hotbarSizeY);
            drawQuad(false, false, hotbarPosX, hotbarPosY + (((hotbarSizeY * 2) / guiScale) * aspectRatio), hotbarSizeX, hotbarSizeY);
            drawQuad(false, false, hotbarPosX, hotbarPosY + (((hotbarSizeY * 3) / guiScale) * aspectRatio), hotbarSizeX, hotbarSizeY);
            glUniform1i(Renderer.gui.uniforms.get("layer"), 0); //gui
            drawText(false, hotbarPosX, hotbarPosY + ((((hotbarSizeY * 4) + 3) / guiScale) * aspectRatio), 0, 0, "Inventory".toCharArray());
            glUniform1i(Renderer.gui.uniforms.get("layer"), 1); //inventory
            glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), 0, 0);
            if (Main.player.creative) {
                glUniform4f(Renderer.gui.uniforms.get("color"), 1, 1, 1, 1);
                drawQuad(false, false, hotbarPosX, containerPosY, hotbarSizeX, hotbarSizeY);
                drawQuad(false, false, hotbarPosX, containerPosY + ((hotbarSizeY / guiScale) * aspectRatio), hotbarSizeX, hotbarSizeY);
                drawQuad(false, false, hotbarPosX, containerPosY + (((hotbarSizeY * 2) / guiScale) * aspectRatio), hotbarSizeX, hotbarSizeY);
                drawQuad(false, false, hotbarPosX, containerPosY + (((hotbarSizeY * 3) / guiScale) * aspectRatio), hotbarSizeX, hotbarSizeY);
                glUniform1i(Renderer.gui.uniforms.get("layer"), 0); //gui
                drawText(false, hotbarPosX, containerPosY + ((((hotbarSizeY * 4) + 3) / guiScale) * aspectRatio), 0, 0, "Creative Supplies".toCharArray());
                glUniform1i(Renderer.gui.uniforms.get("layer"), 1); //inventory
                glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), 0, 0);
            }
        }
        glUniform4f(Renderer.gui.uniforms.get("color"), 1.f, 1.f, 1.f, 1.f);
        drawQuad(false, false, hotbarPosX, hotbarPosY, hotbarSizeX, hotbarSizeY); //hotbar
        glUniform1i(Renderer.gui.uniforms.get("layer"), 2); //selector
        Vector2i selSlot = null;
        if (GUI.inventoryOpen) {
            Vector2f clampedPos = confineToMenu(hotbarPosX, hotbarPosY, hotbarSizeX, hotbarSizeY * 4);
            if (clampedPos.x() > -1 && clampedPos.y() > -1) {
                Main.player.inv.selectedSlot = new Vector2i((int) (clampedPos.x() * invWidth), (int) (clampedPos.y() * 4));
                selSlot = Main.player.inv.selectedSlot;
            } else if (Main.player.creative) {
                Main.player.inv.selectedSlot = null;
                clampedPos = confineToMenu(hotbarPosX, containerPosY, hotbarSizeX, hotbarSizeY * 4);
                Main.player.inv.selectedContainerSlot = new Vector2i((int) (clampedPos.x() * invWidth), (int) (clampedPos.y() * 4));
                selSlot = Main.player.inv.selectedContainerSlot;
            }
        } else {
            Main.player.inv.selectedSlot = new Vector2i(HandManager.hotbarSlot, 0);
            selSlot = Main.player.inv.selectedSlot;
        }
        if (selSlot != null) {
            if (selSlot.x() < 0 || selSlot.y() < 0) {
                selSlot.set(-1, -1);
            } else {
                drawSlot(hotbarPosX, selSlot == Main.player.inv.selectedContainerSlot ? containerPosY : hotbarPosY, 0, -1, selSlot.x(), selSlot.y(), enlargedSlotSize, enlargedSlotSize);
            }
        }

        glUniform1i(Renderer.gui.uniforms.get("layer"), 0); //items
        for (int y = 0; y < (GUI.inventoryOpen ? 4 : 1); y++) {
            for (int x = 0; x < invWidth; x++) {
                Item item = Main.player.inv.getItem(x, y);
                if (item != null) {
                    ItemType itemType = item.type;
                    if (itemType != ItemTypes.AIR) {
                        glUniform1i(Renderer.gui.uniforms.get("tex"), 1); //use item atlas
                        glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), itemType.atlasOffset.x(), itemType.atlasOffset.y());
                        int offX = 3 + (x * slotSize);
                        int offY = 3 + (y * slotSizeY);
                        drawSlot(hotbarPosX, hotbarPosY, offX, offY, 0, 0, ItemTypes.itemTexSize, ItemTypes.itemTexSize);
                        if (item.amount > 1) {
                            glUniform1i(Renderer.gui.uniforms.get("tex"), 0); //use gui atlas
                            char[] chars = String.valueOf(item.amount).toCharArray();
                            float startOffset = 16 - (chars.length * charWidth);
                            drawText(false, hotbarPosX, hotbarPosY, offX + startOffset, offY + 1, chars);
                        }
                    }
                }
            }
        }
        if (GUI.inventoryOpen && Main.player.creative) {
            boolean isFirstSlot = true;
            int itemId = 0;
            done:
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < invWidth; x++) {
                    ItemType itemType = ItemTypes.itemTypeMap.get(itemId);
                    glUniform1i(Renderer.gui.uniforms.get("tex"), isFirstSlot ? 0 : 1); //use item atlas unless first slot then use gui atlas
                    if (isFirstSlot) {
                        glUniform1i(Renderer.gui.uniforms.get("layer"), 4); //trash
                        glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), 0, 0);
                    } else {
                        glUniform1i(Renderer.gui.uniforms.get("layer"), 0); //items
                        glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), itemType.atlasOffset.x(), itemType.atlasOffset.y());
                    }
                    int offX = 3 + (x * slotSize);
                    int offY = 3 + (y * slotSizeY);
                    drawSlot(hotbarPosX, containerPosY, offX, offY, 0, 0, ItemTypes.itemTexSize, ItemTypes.itemTexSize);
                    itemId++;
                    if (itemId >= ItemTypes.itemTypeMap.size()) {
                        break done;
                    }
                    isFirstSlot = false;
                }
            }
        }
        glUniform1i(Renderer.gui.uniforms.get("tex"), 1); //use item atlas
        if (Main.player.inv.cursorItem != null) { //cursor item
            ItemType itemType = Main.player.inv.cursorItem.type;
            glUniform2i(Renderer.gui.uniforms.get("atlasOffset"), itemType.atlasOffset.x(), itemType.atlasOffset.y());
            float offX = Engine.window.currentPos.x() / width;
            float offY = Math.abs(height - (Engine.window.currentPos.y())) / height;
            drawQuad(true, true, offX, offY, ItemTypes.itemTexSize, ItemTypes.itemTexSize);
            if (Main.player.inv.cursorItem.amount > 1) {
                glUniform1i(Renderer.gui.uniforms.get("tex"), 0); //use gui atlas
                char[] chars = String.valueOf(Main.player.inv.cursorItem.amount).toCharArray();
                float startOffset = 16 - (chars.length * charWidth);
                drawText(false, offX, offY, 1 + startOffset - (charWidth * 1.5f), 1 - charHeight, chars);
            }
        }
        drawDebug(window);
    }

    public static void drawSlot(float offsetX, float offsetY, float offPxX, float offPxY, int x, int y, int sizeX, int sizeY) {
        drawSlot(false, false, offsetX, offsetY, offPxX, offPxY, x, y, sizeX, sizeY);
    }

    public static void drawSlot(boolean centeredX, boolean centeredY, float offsetX, float offsetY, float offPxX, float offPxY, int x, int y, int sizeX, int sizeY) {
        float selectedPosX = x * (slotSize / guiScale);
        float selectedPosY = y * ((slotSizeY / guiScale) * aspectRatio);
        drawQuad(centeredX, centeredY, selectedPosX + offsetX + (offPxX / guiScale), selectedPosY + (offsetY - (3.f / height)) + ((offPxY / guiScale) * aspectRatio), sizeX, sizeY);
    }

    public static Vector2f confineToMenu(float posX, float posY, int sizeX, int sizeY) {
        return new Vector2f(
                relative(cursorX(), posX, sizeX / guiScale),
                relative(cursorY(), posY, (sizeY / guiScale) * aspectRatio)
        );
    }

    public static float relative(float cursor, float pos, float size) {
        return (cut(cursor, pos, pos + size - (1f / width)) - pos) * (1 / size);
    }

    public static float cut(float in, float min, float max) {
        if (in < min || in > max) {
            return -1;
        }
        return in;
    }

    public static float cursorX() {
        return Engine.window.currentPos.x() / width;
    }

    public static int cursorPxX() {
        return (int) Engine.window.currentPos.x();
    }

    public static float cursorY() {
        return Math.abs(height - Engine.window.currentPos.y()) / height;
    }

    public static int cursorPxY() {
        return (int) Math.abs(height - Engine.window.currentPos.y());
    }

    public static void drawQuad(boolean centeredX, boolean centeredY, float x, float y, int scaleX, int scaleY) {
        float xScale = (scaleX / guiScale);
        float yScale = (scaleY / guiScale) * aspectRatio;
        float xOffset = ((x * 2) - 1) + (centeredX ? 0 : xScale);
        float yOffset = ((y * 2) - 1) + (centeredY ? 0 : yScale);
        Vector2i offset = new Vector2i((int) ((x - (centeredX ? xScale / 2 : 0)) * width), (int) ((y + (centeredY ? yScale / 2 : 0)) * height));
        glUniform2i(Renderer.gui.uniforms.get("offset"), offset.x(), offset.y());
        glUniform2i(Renderer.gui.uniforms.get("size"), scaleX, scaleY);
        Vector2i scale = new Vector2i((int) (xScale * width), (int) (yScale * height));
        glUniform2i(Renderer.gui.uniforms.get("scale"), scale.x(), scale.y());
        if (drawingButton != null) {
            drawingButton.bounds = new Vector4i(offset.x(), offset.y(), offset.x() + scale.x(), offset.y() + scale.y());
            drawingButton.width = scaleX;
            buttons.add(drawingButton);
        } else if (drawingSlider != null) {
            drawingSlider.bounds = new Vector4i(offset.x(), offset.y(), offset.x() + scale.x(), offset.y() + scale.y());
            drawingSlider.width = scaleX;
            sliders.add(drawingSlider);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(Renderer.gui.uniforms.get("model"), false, new Matrix4f().translate(xOffset, yOffset, 0.f).scale(xScale, yScale, 1).get(stack.mallocFloat(16)));
        }
        glBindVertexArray(Models.QUAD_UNNORMALIZED.vaoId);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLES, 0, Models.QUAD_UNNORMALIZED.positions.length);
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
    }

    public static int charWidth = 6;
    public static int charHeight = 8;
    public static char[] alphabet = """
            0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz.!?$:,;`'"()[]{}*=+-/\\^%&#~<>|
            """.toCharArray();
    public static Map<Character, Integer> charAtlasOffsetIndex = new HashMap<>();
    public static char space = " ".toCharArray()[0];

    public static int getCharAtlasOffset(char character) {
        return character == space ? -1 : charAtlasOffsetIndex.get(character);
    }

    public static void fillTexture() throws IOException {
        int i = 0;
        for (char character : alphabet) {
            charAtlasOffsetIndex.put(character, i);
            i += charWidth;
        }
        glBindTexture(GL_TEXTURE_3D, Textures.gui.id);
        GL12.glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA32F, Textures.gui.width, Textures.gui.height, ((Texture3D) (Textures.gui)).depth, 0, GL_RGBA, GL_FLOAT, 0);
        loadImage("texture/font");
        loadImage("texture/hotbar");
        loadImage("texture/selected_slot");
        loadImage("texture/frame");
        loadImage("texture/trash");
        loadImage("texture/button");

        ItemTypes.fillTexture();
    }

    public static int guiTexDepth = 0;

    public static void loadImage(String path) throws IOException {
        BufferedImage img = ImageIO.read(Renderer.class.getClassLoader().getResourceAsStream("assets/base/gui/" + path + ".png"));
        glTexSubImage3D(GL_TEXTURE_3D, 0, 0, 0, guiTexDepth, img.getWidth(), img.getHeight(), 1, GL_RGBA, GL_UNSIGNED_BYTE, Utils.imageToBuffer(img));
        guiTexDepth++;
    }
}
