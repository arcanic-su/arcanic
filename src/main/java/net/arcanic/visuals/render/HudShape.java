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

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.arcanic.visuals.mixin.GuiGraphicsAccess;
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * HUD shapes drawn per pixel by {@code hud_shape.fsh}, at any fractional size: rounded panels and their rims of any
 * thickness, timer rings and the glare band that runs over a panel. Each is one quad in the GUI's own batches; its
 * parameters ride in the texture coordinate as {@code 8 * P + 2 + 4 * f}, so no uniform is needed per shape. When the
 * shader cannot be used the callers fall back to {@link Px}.
 */
public final class HudShape {
    private HudShape() {}

    private static final RenderPipeline RECT = pipeline("hud_shape", null);
    private static final RenderPipeline RING = pipeline("hud_shape_ring", "RING");
    private static final RenderPipeline GLARE = pipeline("hud_shape_glare", "GLARE");
    private static Boolean ready;

    private static RenderPipeline pipeline(String name, String mode) {
        RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$guiTextureSnippet())
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/" + name))
                .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/hud_shape"))
                .withCull(false);
        if (mode != null) b.withShaderDefine(mode);
        return b.build();
    }

    public static boolean ready() {
        if (ready == null || !ready) {
            try {
                ready = RenderSystem.getDevice().precompilePipeline(RECT).isValid()
                        && RenderSystem.getDevice().precompilePipeline(RING).isValid()
                        && RenderSystem.getDevice().precompilePipeline(GLARE).isValid();
            } catch (RuntimeException e) {
                ready = false;
            }
        }
        return ready;
    }

    /** A rounded panel, filled. */
    public static void round(GuiGraphics g, float x, float y, float w, float h, float r, int colour) {
        submit(g, RECT, x, y, w, h, Math.round(Math.max(0, r) * 4), 0, colour);
    }

    /** Only the inner rim of a rounded panel, {@code thick} pixels wide. */
    public static void rim(GuiGraphics g, float x, float y, float w, float h, float r, float thick, int colour) {
        submit(g, RECT, x, y, w, h, Math.round(Math.max(0, r) * 4), Math.max(1, Math.round(thick * 16)), colour);
    }

    /** A rounded panel filled with a colour that runs from {@code top} to {@code bottom}. */
    public static void roundV(GuiGraphics g, float x, float y, float w, float h, float r, int top, int bottom) {
        submit(g, RECT, x, y, w, h, Math.round(Math.max(0, r) * 4), 0, top, bottom);
    }

    /** A rounded panel filled with a colour that runs from {@code left} to {@code right}. */
    public static void roundH(GuiGraphics g, float x, float y, float w, float h, float r, int left, int right) {
        submit(g, RECT, x, y, w, h, Math.round(Math.max(0, r) * 4), 0, left, left, right, right);
    }

    /** The inner rim lit from above: {@code top} at the upper edge fading to {@code bottom} at the lower one. */
    public static void rimV(
            GuiGraphics g, float x, float y, float w, float h, float r, float thick, int top, int bottom) {
        submit(g, RECT, x, y, w, h, Math.round(Math.max(0, r) * 4), Math.max(1, Math.round(thick * 16)), top, bottom);
    }

    /** A ring of outer diameter {@code size}, {@code thick} pixels wide, filled from the top clockwise to {@code k}. */
    public static void ring(GuiGraphics g, float x, float y, float size, float thick, float k, int colour) {
        float r = size / 2;
        int t = Math.round(Math.max(0, Math.min(1, thick / Math.max(0.5F, r - 0.5F))) * 1000);
        submit(g, RING, x, y, size, size, Math.round(Math.max(0, Math.min(1, k)) * 1000), t, colour);
    }

    /** The glare band at {@code head} (-0.5 before the panel .. 1.5 past it), clipped to the rounded panel. */
    public static void glare(
            GuiGraphics g, float x, float y, float w, float h, float r, float head, boolean flip, int colour) {
        int at = Math.round((Math.max(-0.5F, Math.min(1.5F, head)) + 0.5F) * 1000);
        submit(g, GLARE, x, y, w, h, Math.round(Math.max(0, r) * 4), at + (flip ? 4096 : 0), colour);
    }

    private static void submit(
            GuiGraphics g, RenderPipeline pipe, float x, float y, float w, float h, int px, int py, int colour) {
        submit(g, pipe, x, y, w, h, px, py, colour, colour);
    }

    private static void submit(
            GuiGraphics g,
            RenderPipeline pipe,
            float x,
            float y,
            float w,
            float h,
            int px,
            int py,
            int colour,
            int bottomColour) {
        submit(g, pipe, x, y, w, h, px, py, colour, bottomColour, bottomColour, colour);
    }

    /** The corner colours go top left, bottom left, bottom right, top right. */
    private static void submit(
            GuiGraphics g,
            RenderPipeline pipe,
            float x,
            float y,
            float w,
            float h,
            int px,
            int py,
            int tl,
            int bl,
            int br,
            int tr) {
        int c = Px.am(tl, Px.fade), c2 = Px.am(bl, Px.fade), c3 = Px.am(br, Px.fade), c4 = Px.am(tr, Px.fade);
        if (w <= 0 || h <= 0 || (c | c2 | c3 | c4) >>> 24 == 0) return;
        var tex = Minecraft.getInstance().getTextureManager().getTexture(Px.ATLAS);
        var pose = new Matrix3x2f(g.pose());
        int bx = (int) Math.floor(x) - 2, by = (int) Math.floor(y) - 2;
        var bounds = new ScreenRectangle(bx, by, (int) Math.ceil(x + w) - bx + 4, (int) Math.ceil(y + h) - by + 4)
                .transformMaxBounds(pose);
        var setup = TextureSetup.singleTexture(
                tex.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        ((GuiGraphicsAccess) g)
                .arcanicvisuals$state()
                .submitGuiElement(
                        new State(pipe, pose, x, y, w, h, px, py, c, c2, c3, c4, setup, g.scissorStack.peek(), bounds));
    }

    private record State(
            RenderPipeline pipeline,
            Matrix3x2f pose,
            float x,
            float y,
            float w,
            float h,
            int px,
            int py,
            int colour,
            int bottomColour,
            int bottomRight,
            int topRight,
            TextureSetup textureSetup,
            ScreenRectangle scissorArea,
            ScreenRectangle bounds)
            implements GuiElementRenderState {
        public void buildVertices(VertexConsumer v) {
            // a pixel of room on every side, so the edge's soft half pixel is not cut by the quad
            float ex = 1.0F / w, ey = 1.0F / h;
            vertex(v, x - 1, y - 1, -ex, -ey, colour);
            vertex(v, x - 1, y + h + 1, -ex, 1 + ey, bottomColour);
            vertex(v, x + w + 1, y + h + 1, 1 + ex, 1 + ey, bottomRight);
            vertex(v, x + w + 1, y - 1, 1 + ex, -ey, topRight);
        }

        private void vertex(VertexConsumer v, float xx, float yy, float fx, float fy, int c) {
            v.addVertexWith2DPose(pose, xx, yy)
                    .setUv(8.0F * px + 2 + 4 * fx, 8.0F * py + 2 + 4 * fy)
                    .setColor(c);
        }
    }
}
