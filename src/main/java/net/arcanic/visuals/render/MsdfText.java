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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.arcanic.visuals.mixin.GuiGraphicsAccess;
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/** Menu and HUD text rendering. */
public final class MsdfText {
    private MsdfText() {}

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(
                    RenderPipelinesAccess.arcanicvisuals$guiTextureSnippet())
            .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/msdf_text"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/msdf_text"))
            .withCull(false)
            .build();

    /** One glyph: advance, plane box (em units, y up) and atlas box (0..1, v down). */
    private record Glyph(
            float advance, float l, float b, float r, float t, float u0, float v0, float u1, float v1, boolean ink) {}

    private static final class Face {
        final Identifier atlas;
        final Map<Integer, Glyph> glyphs = new HashMap<>();
        final Map<String, Float> widths = new HashMap<>();

        Face(String name) {
            atlas = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/font/msdf/" + name + ".png");
            try (var in = MsdfText.class.getResourceAsStream("/assets/arcanicvisuals/msdf/" + name + ".json")) {
                var root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                var a = root.getAsJsonObject("atlas");
                float w = a.get("width").getAsFloat(), h = a.get("height").getAsFloat();
                for (var e : root.getAsJsonArray("glyphs")) {
                    var o = e.getAsJsonObject();
                    float adv = o.get("advance").getAsFloat();
                    if (!o.has("planeBounds")) {
                        glyphs.put(o.get("unicode").getAsInt(), new Glyph(adv, 0, 0, 0, 0, 0, 0, 0, 0, false));
                        continue;
                    }
                    var p = o.getAsJsonObject("planeBounds");
                    var q = o.getAsJsonObject("atlasBounds");
                    glyphs.put(
                            o.get("unicode").getAsInt(),
                            new Glyph(
                                    adv,
                                    p.get("left").getAsFloat(),
                                    p.get("bottom").getAsFloat(),
                                    p.get("right").getAsFloat(),
                                    p.get("top").getAsFloat(),
                                    q.get("left").getAsFloat() / w,
                                    1 - q.get("top").getAsFloat() / h,
                                    q.get("right").getAsFloat() / w,
                                    1 - q.get("bottom").getAsFloat() / h,
                                    true));
                }
            } catch (Exception ex) {
                throw new IllegalStateException("MSDF font " + name, ex);
            }
        }
    }

    private static final Face[] FACES = new Face[Px.Weight.values().length];

    private static Face face(Px.Weight w) {
        Face f = FACES[w.ordinal()];
        if (f == null)
            FACES[w.ordinal()] = f = new Face(
                    switch (w) {
                        case MEDIUM -> "sf_pro_display_medium";
                        case SEMIBOLD -> "inter_semibold";
                        case BOLD -> "inter_bold";
                    });
        return f;
    }

    private static Boolean ready;

    public static boolean ready() {
        if (ready == null || !ready) {
            try {
                ready = RenderSystem.getDevice().precompilePipeline(PIPELINE).isValid();
            } catch (RuntimeException e) {
                ready = false;
            }
        }
        return ready;
    }

    /** Width in pixels of {@code s} at {@code px} (the em size, as for our TrueType fonts). */
    public static float width(String s, Px.Weight w, int px) {
        Face f = face(w);
        Float em = f.widths.get(s);
        if (em == null) {
            float sum = 0;
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                i += Character.charCount(cp);
                Glyph g = f.glyphs.get(cp);
                sum += g != null ? g.advance : Px.ttfWidth(new String(Character.toChars(cp)), w, px) / (float) px;
            }
            if (f.widths.size() > 4096) f.widths.clear();
            f.widths.put(s, em = sum);
        }
        return em * px;
    }

    /** Draws {@code s} with its baseline at {@code baseline}, starting at x. */
    public static void draw(GuiGraphics g, String s, int x, int baseline, Px.Weight w, int px, int colour) {
        if ((colour >>> 24) == 0 || s.isEmpty()) return;
        Face f = face(w);
        List<float[]> quads = new ArrayList<>();
        float pen = x, top = baseline, bottom = baseline;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            Glyph gl = f.glyphs.get(cp);
            if (gl == null) {
                String ch = new String(Character.toChars(cp));
                Px.ttfDraw(g, ch, Math.round(pen), baseline, w, px, colour);
                pen += Px.ttfWidth(ch, w, px);
                continue;
            }
            if (gl.ink) {
                float x0 = pen + gl.l * px, x1 = pen + gl.r * px, y0 = baseline - gl.t * px, y1 = baseline - gl.b * px;
                quads.add(new float[] {x0, y0, x1, y1, gl.u0, gl.v0, gl.u1, gl.v1});
                top = Math.min(top, y0);
                bottom = Math.max(bottom, y1);
            }
            pen += gl.advance * px;
        }
        if (quads.isEmpty()) return;
        var tex = Minecraft.getInstance().getTextureManager().getTexture(f.atlas);
        var pose = new Matrix3x2f(g.pose());
        int bx = (int) Math.floor(x) - 1, by = (int) Math.floor(top) - 1;
        var bounds = new ScreenRectangle(bx, by, (int) Math.ceil(pen) - bx + 2, (int) Math.ceil(bottom) - by + 2)
                .transformMaxBounds(pose);
        var setup = TextureSetup.singleTexture(
                tex.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        ((GuiGraphicsAccess) g)
                .arcanicvisuals$state()
                .submitGlyphToCurrentLayer(new Run(pose, quads, colour, setup, g.scissorStack.peek(), bounds));
    }

    private record Run(
            Matrix3x2f pose,
            List<float[]> quads,
            int colour,
            TextureSetup textureSetup,
            ScreenRectangle scissorArea,
            ScreenRectangle bounds)
            implements GuiElementRenderState {
        public RenderPipeline pipeline() {
            return PIPELINE;
        }

        public void buildVertices(VertexConsumer v) {
            for (float[] q : quads) {
                v.addVertexWith2DPose(pose, q[0], q[1]).setUv(q[4], q[5]).setColor(colour);
                v.addVertexWith2DPose(pose, q[0], q[3]).setUv(q[4], q[7]).setColor(colour);
                v.addVertexWith2DPose(pose, q[2], q[3]).setUv(q[6], q[7]).setColor(colour);
                v.addVertexWith2DPose(pose, q[2], q[1]).setUv(q[6], q[5]).setColor(colour);
            }
        }
    }
}
