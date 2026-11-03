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
package net.arcanic.visuals.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.arcanic.visuals.core.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class BrandLogo {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("arcanicvisuals", "brand_logo_animation");
    private static final Identifier MARK =
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/arcanic_mark.png");
    private static final int MARK_SIZE = 96;
    private static int size;
    private static int stride;
    private static int columns;
    private static int width;
    private static int height;
    private static int still;
    private static int total;
    private static int[] ends;
    private static long started;
    private static boolean registered;

    private BrandLogo() {}

    private static void prepare() {
        if (!registered) {
            try {
                InputStream meta = BrandLogo.class.getResourceAsStream("/assets/arcanicvisuals/logo_animation.json");

                try {
                    InputStream png = BrandLogo.class.getResourceAsStream(
                            "/assets/arcanicvisuals/textures/gui/arcanic_A_frames.png");

                    try {
                        JsonObject root = JsonParser.parseReader(new InputStreamReader(meta, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                        size = root.get("size").getAsInt();
                        stride = root.get("stride").getAsInt();
                        columns = root.get("columns").getAsInt();
                        width = root.get("width").getAsInt();
                        height = root.get("height").getAsInt();
                        still = root.get("hold").getAsInt();
                        JsonArray times = root.getAsJsonArray("durations");
                        ends = new int[times.size()];

                        for (int i = 0; i < ends.length; ++i) {
                            total += times.get(i).getAsInt();
                            ends[i] = total;
                        }

                        NativeImage image = NativeImage.read(png);
                        Minecraft.getInstance().getTextureManager().register(ID, new LogoTexture(image));
                        started = System.nanoTime();
                        registered = true;
                    } catch (Throwable var7) {
                        if (png != null) {
                            try {
                                png.close();
                            } catch (Throwable var6) {
                                var7.addSuppressed(var6);
                            }
                        }

                        throw var7;
                    }

                    if (png != null) {
                        png.close();
                    }
                } catch (Throwable var8) {
                    if (meta != null) {
                        try {
                            meta.close();
                        } catch (Throwable var5) {
                            var8.addSuppressed(var5);
                        }
                    }

                    throw var8;
                }

                if (meta != null) {
                    meta.close();
                }

            } catch (IOException ex) {
                throw new IllegalStateException("Arcanic GIF logo missing", ex);
            }
        }
    }

    private static int frameAt(long millis) {
        long held = Math.max(0L, Math.min(millis, (long) ends[still] - 1L));
        int result = Arrays.binarySearch(ends, (int) held + 1);
        return result >= 0 ? result : -result - 1;
    }

    public static void idle(GuiGraphics g, int x, int y, int targetSize) {
        mark(g, x, y, targetSize, Px.fade);
    }

    public static void mark(GuiGraphics g, int x, int y, int targetSize, float opacity) {
        if (!(opacity <= 0.0F)) {
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    MARK,
                    x,
                    y,
                    0.0F,
                    0.0F,
                    targetSize,
                    targetSize,
                    96,
                    96,
                    96,
                    96,
                    Px.a(0xFFFFFF, Math.round(Math.max(0.0F, Math.min(1.0F, opacity)) * 255.0F)));
        }
    }

    public static void draw(GuiGraphics g, int x, int y, int targetSize, float opacity) {
        drawFrom(g, x, y, targetSize, opacity, -1L);
    }

    public static void drawFrom(GuiGraphics g, int x, int y, int targetSize, float opacity, long since) {
        if (!(opacity <= 0.0F)) {
            prepare();
            int frame =
                    Theme.animations ? frameAt((System.nanoTime() - (since < 0L ? started : since)) / 1000000L) : still;
            int drawSize = Math.max(1, Math.round((float) targetSize * 1.35F));
            int offset = (drawSize - targetSize) / 2;
            int u = frame % columns * stride + 1;
            int v = frame / columns * stride + 1;
            g.blit(
                    RenderPipelines.MOJANG_LOGO,
                    ID,
                    x - offset,
                    y - offset,
                    (float) u,
                    (float) v,
                    drawSize,
                    drawSize,
                    size,
                    size,
                    width,
                    height,
                    Px.a(0xFFFFFF, Math.round(Math.max(0.0F, Math.min(1.0F, opacity)) * 255.0F)));
        }
    }

    public static long started() {
        prepare();
        return started;
    }

    private static final class LogoTexture extends DynamicTexture {
        LogoTexture(NativeImage image) {
            super(() -> "Arcanic GIF animation", image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }
}
