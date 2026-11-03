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

    private static final RenderPipeline PIPELINE = pipeline("msdf_text", null);
    /** The same text shader for atlases baked with another distance range (the HUD faces and icons). */
    private static final RenderPipeline PIPELINE_12 = pipeline("msdf_text_r12", "RANGE_12");

    private static final RenderPipeline PIPELINE_6 = pipeline("msdf_text_r6", "RANGE_6");

    private static RenderPipeline pipeline(String name, String range) {
        RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$guiTextureSnippet())
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/" + name))
                .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/msdf_text"))
                .withCull(false);
        if (range != null) b.withShaderDefine(range);
        return b.build();
    }

    /** One glyph: advance, plane box (em units, y up) and atlas box (0..1, v down). */
    private record Glyph(
            float advance, float l, float b, float r, float t, float u0, float v0, float u1, float v1, boolean ink) {}

    private static final class Face {
        final Identifier atlas;
        final Map<Integer, Glyph> glyphs = new HashMap<>();
        final Map<String, Float> widths = new HashMap<>();
        /** Kerning by (left << 32 | right), in em; only the faces that are set kerned (the HUD ones) use it. */
        final Map<Long, Float> kerning = new HashMap<>();

        boolean kerned;
        RenderPipeline pipeline = PIPELINE;
        float baseline = 0.96875F; // lineHeight + descender, in em: where the baseline sits under a text box's top
        float lineHeight = 1.2099609F;

        Face(String name) {
            atlas = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/font/msdf/" + name + ".png");
            try (var in = MsdfText.class.getResourceAsStream("/assets/arcanicvisuals/msdf/" + name + ".json")) {
                var root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                var a = root.getAsJsonObject("atlas");
                var m = root.getAsJsonObject("metrics");
                if (m != null && m.has("lineHeight") && m.has("descender"))
                    baseline = m.get("lineHeight").getAsFloat()
                            + m.get("descender").getAsFloat();
                float w = a.get("width").getAsFloat(), h = a.get("height").getAsFloat();
                float range = a.has("distanceRange") ? a.get("distanceRange").getAsFloat() : 10;
                pipeline = range >= 11 ? PIPELINE_12 : range <= 7 ? PIPELINE_6 : PIPELINE;
                kerned = name.startsWith("suisse");
                for (var e : root.getAsJsonArray("glyphs")) {
                    var o = e.getAsJsonObject();
                    float adv = o.get("advance").getAsFloat();
                    // icon atlases number their glyphs by index; the index is the character that draws it
                    int cp = (o.has("unicode") ? o.get("unicode") : o.get("index")).getAsInt();
                    if (!o.has("planeBounds")) {
                        glyphs.put(cp, new Glyph(adv, 0, 0, 0, 0, 0, 0, 0, 0, false));
                        continue;
                    }
                    var p = o.getAsJsonObject("planeBounds");
                    var q = o.getAsJsonObject("atlasBounds");
                    glyphs.put(
                            cp,
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
                if (kerned && root.has("kerning")) {
                    for (var e : root.getAsJsonArray("kerning")) {
                        var k = e.getAsJsonObject();
                        kerning.put(
                                (long) k.get("unicode1").getAsInt() << 32
                                        | k.get("unicode2").getAsInt() & 0xFFFFFFFFL,
                                k.get("advance").getAsFloat());
                    }
                }
            } catch (Exception ex) {
                throw new IllegalStateException("MSDF font " + name, ex);
            }
        }
    }

    private static float kern(Face f, int left, int right) {
        if (!f.kerned || left < 0) return 0;
        Float k = f.kerning.get((long) left << 32 | right & 0xFFFFFFFFL);
        return k == null ? 0 : k;
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

    private static final Map<String, Face> NAMED = new HashMap<>();

    private static Face named(String name) {
        return NAMED.computeIfAbsent(name, Face::new);
    }

    /** A face's line height in em. */
    public static float lineHeight(String face) {
        return named(face).lineHeight;
    }

    /** Where the baseline sits under the top of a text box, for a face loaded by name, at {@code px}. */
    public static float baseline(String face, float px) {
        return named(face).baseline * px;
    }

    /** Width in pixels of {@code s} in a face loaded by name (Delta's fonts), unknown glyphs counted as nothing. */
    public static float width(String face, String s, float px) {
        Face f = named(face);
        Float em = f.widths.get(s);
        if (em == null) {
            float sum = 0;
            int prev = -1;
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                i += Character.charCount(cp);
                Glyph g = f.glyphs.get(cp);
                if (g == null) continue;
                sum += kern(f, prev, cp) + g.advance;
                prev = cp;
            }
            if (f.widths.size() > 4096) f.widths.clear();
            f.widths.put(s, em = sum);
        }
        return em * px;
    }

    /** Draws {@code s} in a face loaded by name with the top of its text box at y, as Delta's font renderer does. */
    public static void drawTop(GuiGraphics g, String face, String s, float x, float y, float px, int colour) {
        Face f = named(face);
        emit(g, f, s, x, y + f.baseline * px, px, colour, null);
    }

    private static Boolean ready;

    public static boolean ready() {
        if (ready == null || !ready) {
            try {
                ready = RenderSystem.getDevice().precompilePipeline(PIPELINE).isValid()
                        && RenderSystem.getDevice()
                                .precompilePipeline(PIPELINE_12)
                                .isValid()
                        && RenderSystem.getDevice()
                                .precompilePipeline(PIPELINE_6)
                                .isValid();
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
        emit(g, face(w), s, x, baseline, px, colour, w);
    }

    private static void emit(
            GuiGraphics g, Face f, String s, float x, float baseline, float px, int colour, Px.Weight fallback) {
        if ((colour >>> 24) == 0 || s.isEmpty()) return;
        List<float[]> quads = new ArrayList<>();
        float pen = x, top = baseline, bottom = baseline;
        int prev = -1;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            Glyph gl = f.glyphs.get(cp);
            if (gl != null) {
                pen += kern(f, prev, cp) * px;
                prev = cp;
            }
            if (gl == null) {
                if (fallback == null) continue;
                String ch = new String(Character.toChars(cp));
                Px.ttfDraw(g, ch, Math.round(pen), Math.round(baseline), fallback, Math.round(px), colour);
                pen += Px.ttfWidth(ch, fallback, Math.round(px));
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
                .submitGlyphToCurrentLayer(
                        new Run(f.pipeline, pose, quads, colour, setup, g.scissorStack.peek(), bounds));
    }

    private record Run(
            RenderPipeline pipeline,
            Matrix3x2f pose,
            List<float[]> quads,
            int colour,
            TextureSetup textureSetup,
            ScreenRectangle scissorArea,
            ScreenRectangle bounds)
            implements GuiElementRenderState {
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
