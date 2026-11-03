/*
 * This file is part of Arcanic Visuals (https://github.com/arcanic-su/arcanic)
 *
 * Copyright (c) 2026 Arcanic
 *
 * Arcanic Visuals is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Arcanic Visuals is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Arcanic Visuals. If not, see <https://www.gnu.org/licenses/>.
 */
package net.arcanic.visuals.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.io.InputStream;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

public final class Wallpaper {
    private static final Identifier SHARP = Identifier.fromNamespaceAndPath("arcanicvisuals", "menu_wallpaper");
    private static final Identifier SOFT = Identifier.fromNamespaceAndPath("arcanicvisuals", "menu_wallpaper_blur");
    private static final String PATH = "/assets/arcanicvisuals/textures/gui/menu/";
    private static int w = 1920;
    private static int h = 1080;
    private static int bw = 960;
    private static int bh = 540;
    private static boolean loaded;
    private static boolean broken;
    private static float parX;
    private static float parY;
    private static long lastFrame = -1L;
    private static long lastTick;

    private Wallpaper() {}

    private static boolean load() {
        if (!loaded && !broken) {
            try {
                try (InputStream a =
                        Wallpaper.class.getResourceAsStream("/assets/arcanicvisuals/textures/gui/menu/wallpaper.png")) {
                    try (InputStream b = Wallpaper.class.getResourceAsStream(
                            "/assets/arcanicvisuals/textures/gui/menu/wallpaper_blur.png")) {
                        NativeImage sharp = NativeImage.read(a);
                        NativeImage soft = NativeImage.read(b);
                        w = sharp.getWidth();
                        h = sharp.getHeight();
                        bw = soft.getWidth();
                        bh = soft.getHeight();
                        TextureManager textures = Minecraft.getInstance().getTextureManager();
                        textures.register(SHARP, new Texture("Arcanic wallpaper", sharp));
                        textures.register(SOFT, new Texture("Arcanic wallpaper, blurred", soft));
                        loaded = true;
                    }
                }
            } catch (Exception ex) {
                broken = true;
                System.err.println("[ArcanicVisuals] menu wallpaper: " + ex);
            }

            return loaded;
        } else {
            return loaded;
        }
    }

    public static float unit() {
        return Math.max(0.45F, Math.min(1.35F, Math.min((float) Px.height() / 900.0F, (float) Px.width() / 1440.0F)));
    }

    public static float reach() {
        return 27.0F * unit();
    }

    private static void lean(float reach) {
        Minecraft mc = Minecraft.getInstance();
        long frame = mc.getFrameTimeNs();
        if (frame != lastFrame) {
            lastFrame = frame;
            long now = System.nanoTime();
            float dt = lastTick == 0L ? 0.0F : Math.min(0.1F, (float) (now - lastTick) / 1.0E9F);
            lastTick = now;
            Window window = mc.getWindow();
            double mx = mc.mouseHandler.xpos() / (double) Math.max(1, window.getScreenWidth()) * 2.0 - 1.0;
            double my = mc.mouseHandler.ypos() / (double) Math.max(1, window.getScreenHeight()) * 2.0 - 1.0;
            float tx = (float) Math.max(-1.0, Math.min(1.0, mx)) * reach;
            float ty = (float) Math.max(-1.0, Math.min(1.0, my)) * reach;
            float k = Theme.animations ? 1.0F - (float) Math.pow(0.0025, (double) dt) : 1.0F;
            parX += (tx - parX) * k;
            parY += (ty - parY) * k;
        }
    }

    public static void draw(GuiGraphics g, float opacity) {
        draw(g, opacity, 0.0F, 1.0F, reach());
    }

    public static void draw(GuiGraphics g, float opacity, float blur, float zoom, float reach) {
        if (!(opacity <= 0.0F)) {
            int sw = Px.width();
            int sh = Px.height();
            int alpha = Math.round(Math.max(0.0F, Math.min(1.0F, opacity)) * 255.0F);
            if (!load()) {
                g.fillGradient(0, 0, sw, sh, alpha << 24 | 1316383, alpha << 24 | 460555);
            } else {
                lean(reach);
                float margin = 30.0F * unit();
                float cover =
                        Math.max(((float) sw + 2.0F * margin) / (float) w, ((float) sh + 2.0F * margin) / (float) h)
                                * zoom;
                int dw = Math.round((float) w * cover);
                int dh = Math.round((float) h * cover);
                int x = Math.round((float) sw / 2.0F - parX - (float) dw / 2.0F);
                int y = Math.round((float) sh / 2.0F - parY - (float) dh / 2.0F);
                g.blit(
                        RenderPipelines.GUI_TEXTURED,
                        SHARP,
                        x,
                        y,
                        0.0F,
                        0.0F,
                        dw,
                        dh,
                        w,
                        h,
                        w,
                        h,
                        alpha << 24 | 0xFFFFFF);
                blur = Math.max(0.0F, Math.min(1.0F, blur));
                if (blur > 0.003F) {
                    g.blit(
                            RenderPipelines.GUI_TEXTURED,
                            SOFT,
                            x,
                            y,
                            0.0F,
                            0.0F,
                            dw,
                            dh,
                            bw,
                            bh,
                            bw,
                            bh,
                            Math.round((float) alpha * blur) << 24 | 0xFFFFFF);
                }
            }
        }
    }

    private static final class Texture extends DynamicTexture {
        Texture(String name, NativeImage image) {
            super(() -> name, image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }
}
