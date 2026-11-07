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
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.arcanic.visuals.mixin.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/** Section sprites and Arcanic category glyphs, with proper MTSDF edge smoothing. */
public final class SectionIcons {
    private static final Identifier ATLAS =
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/sections/arc.png");
    private static final Map<Integer, float[]> GLYPHS = new HashMap<>();
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(
                    RenderPipelinesAccess.arcanicvisuals$guiTextureSnippet())
            .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/section_icon"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/section_icon"))
            .withCull(false)
            .build();

    static {
        try (var in = SectionIcons.class.getResourceAsStream("/assets/arcanicvisuals/section_icons.json")) {
            var root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            float w = root.getAsJsonObject("atlas").get("width").getAsFloat(),
                    h = root.getAsJsonObject("atlas").get("height").getAsFloat();
            for (var entry : root.getAsJsonArray("glyphs")) {
                var o = entry.getAsJsonObject();
                if (!o.has("atlasBounds")) continue;
                var b = o.getAsJsonObject("atlasBounds");
                GLYPHS.put(o.get("unicode").getAsInt(), new float[] {
                    b.get("left").getAsFloat() / w,
                    1 - b.get("top").getAsFloat() / h,
                    b.get("right").getAsFloat() / w,
                    1 - b.get("bottom").getAsFloat() / h
                });
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Arcanic section icons", ex);
        }
    }

    private SectionIcons() {}

    public static void draw(GuiGraphics g, String key, int x, int y, int size, int colour) {
        if (key.startsWith("rs:")) {
            Identifier id = Identifier.fromNamespaceAndPath(
                    "arcanicvisuals", "textures/gui/sections/" + key.substring(3) + ".png");
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    id,
                    x,
                    y,
                    0,
                    0,
                    size,
                    size,
                    size,
                    size,
                    size,
                    size,
                    Px.am(colour, Px.fade));
            return;
        }
        if (!key.startsWith("arc:")) {
            Px.icon(g, key, x, y, size, colour);
            return;
        }
        float[] uv = GLYPHS.get((int) key.charAt(4));
        if (uv == null) return;
        float ratio = (uv[2] - uv[0]) * 436 / ((uv[3] - uv[1]) * 856);
        float w = ratio >= 1 ? size : size * ratio, h = ratio >= 1 ? size / ratio : size;
        float xx = x + (size - w) / 2, yy = y + (size - h) / 2;
        var tex = Minecraft.getInstance().getTextureManager().getTexture(ATLAS);
        var matrix = new Matrix3x2f(g.pose());
        var bounds = new ScreenRectangle(x, y, size, size).transformMaxBounds(matrix);
        ((GuiGraphicsAccess) g)
                .arcanicvisuals$state()
                .submitGuiElement(new State(
                        matrix,
                        xx,
                        yy,
                        w,
                        h,
                        uv,
                        Px.am(colour, Px.fade),
                        TextureSetup.singleTexture(
                                tex.getTextureView(),
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                        bounds));
    }

    private record State(
            Matrix3x2f pose,
            float x,
            float y,
            float w,
            float h,
            float[] uv,
            int colour,
            TextureSetup textureSetup,
            ScreenRectangle bounds)
            implements GuiElementRenderState {
        public RenderPipeline pipeline() {
            return PIPELINE;
        }

        public ScreenRectangle scissorArea() {
            return bounds;
        }

        public void buildVertices(VertexConsumer v) {
            vertex(v, x, y, uv[0], uv[1]);
            vertex(v, x, y + h, uv[0], uv[3]);
            vertex(v, x + w, y + h, uv[2], uv[3]);
            vertex(v, x + w, y, uv[2], uv[1]);
        }

        private void vertex(VertexConsumer v, float xx, float yy, float u, float t) {
            v.addVertexWith2DPose(pose, xx, yy).setUv(u, t).setColor(colour);
        }
    }
}
