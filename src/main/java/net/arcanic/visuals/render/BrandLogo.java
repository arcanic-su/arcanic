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

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.arcanic.visuals.core.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The Arcanic A intro (tools/logo_render.py: a diagonal reveal, a spark along the blade, a glow that dies down) as
 * one alpha-safe GPU sprite sheet.
 */
public final class BrandLogo {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("arcanicvisuals", "brand_logo_animation");
    // the cauldron alone, edge to edge, for the places where it stands in a line with text
    private static final Identifier MARK =
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/arcanic_mark.png");
    private static final int MARK_SIZE = 96;
    private static int size, stride, columns, width, height, still, total;
    private static int[] ends;
    private static long started;
    private static boolean registered;

    private BrandLogo() {}

    private static void prepare() {
        if (registered) return;
        try (var meta = BrandLogo.class.getResourceAsStream("/assets/arcanicvisuals/logo_animation.json");
                var png = BrandLogo.class.getResourceAsStream(
                        "/assets/arcanicvisuals/textures/gui/arcanic_A_frames.png")) {
            var root = JsonParser.parseReader(new InputStreamReader(meta, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            size = root.get("size").getAsInt();
            stride = root.get("stride").getAsInt();
            columns = root.get("columns").getAsInt();
            width = root.get("width").getAsInt();
            height = root.get("height").getAsInt();
            still = root.get("hold").getAsInt();
            var times = root.getAsJsonArray("durations");
            ends = new int[times.size()];
            for (int i = 0; i < ends.length; i++) {
                total += times.get(i).getAsInt();
                ends[i] = total;
            }
            NativeImage image = NativeImage.read(png);
            Minecraft.getInstance().getTextureManager().register(ID, new LogoTexture(image));
            started = System.nanoTime();
            registered = true;
        } catch (IOException ex) {
            throw new IllegalStateException("Arcanic GIF logo missing", ex);
        }
    }

    private static final class LogoTexture extends DynamicTexture {
        LogoTexture(NativeImage image) {
            super(() -> "Arcanic GIF animation", image);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    private static int frameAt(long millis) {
        long held = Math.max(0, Math.min(millis, ends[still] - 1L));
        int result = Arrays.binarySearch(ends, (int) held + 1);
        return result >= 0 ? result : -result - 1;
    }

    /** The still cauldron filling the box exactly: in the watermark, the menu headers and beside the brand name. */
    public static void idle(GuiGraphics g, int x, int y, int targetSize) {
        mark(g, x, y, targetSize, Px.fade);
    }

    public static void mark(GuiGraphics g, int x, int y, int targetSize, float opacity) {
        if (opacity <= 0) return;
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                MARK,
                x,
                y,
                0,
                0,
                targetSize,
                targetSize,
                MARK_SIZE,
                MARK_SIZE,
                MARK_SIZE,
                MARK_SIZE,
                Px.a(0xFFFFFF, Math.round(Math.max(0, Math.min(1, opacity)) * 255)));
    }

    public static void draw(GuiGraphics g, int x, int y, int targetSize, float opacity) {
        drawFrom(g, x, y, targetSize, opacity, -1);
    }
    /**
     * Plays the animation from its own start time (System.nanoTime()), so a surface can replay the A when it changes;
     * -1 = the shared start.
     */
    public static void drawFrom(GuiGraphics g, int x, int y, int targetSize, float opacity, long since) {
        if (opacity <= 0) return;
        prepare();
        int frame =
                Theme.animations ? frameAt((System.nanoTime() - (since < 0 ? started : since)) / 1_000_000L) : still;
        // Compensate for the GIF's transparent margins to fit the visible mark to the requested size.
        int drawSize = Math.max(1, Math.round(targetSize * 1.35f)), offset = (drawSize - targetSize) / 2;
        int u = (frame % columns) * stride + 1, v = (frame / columns) * stride + 1;
        g.blit(
                RenderPipelines.MOJANG_LOGO,
                ID,
                x - offset,
                y - offset,
                u,
                v,
                drawSize,
                drawSize,
                size,
                size,
                width,
                height,
                Px.a(0xFFFFFF, Math.round(Math.max(0, Math.min(1, opacity)) * 255)));
    }

    public static long started() {
        prepare();
        return started;
    }
}
