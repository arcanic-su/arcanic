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

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.mixin.*;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/** The ember loading field on our GUI pipeline, in the violet palette. */
public final class FrontEndBackdrop {
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("arcanicvisuals", "core/front_end");
    private static final Identifier PALETTE = Identifier.fromNamespaceAndPath("arcanicvisuals", "front_end_palette");
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(
                    RenderPipelinesAccess.arcanicvisuals$guiTextureSnippet())
            .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/front_end"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withCull(false)
            .build();
    private static DynamicTexture palette;
    private static int lastAccent, lastEnd;
    private static boolean unavailable;

    private FrontEndBackdrop() {}

    public static void draw(GuiGraphics g, float opacity, float progress, boolean loading, boolean ready) {
        int w = Px.width(), h = Px.height();
        // Early startup must not depend on fonts, atlases or resource-manager shaders being loaded.
        if (!ready || unavailable) {
            fallback(g, w, h, opacity);
            return;
        }
        try {
            if (!RenderSystem.getDevice().precompilePipeline(PIPELINE).isValid()) {
                unavailable = true;
                fallback(g, w, h, opacity);
                return;
            }
            int a = Theme.accent(), b = Theme.accent2();
            if (palette == null) {
                NativeImage image = new NativeImage(3, 1, false);
                palette = new DynamicTexture(() -> "Arcanic front end palette", image);
                Minecraft.getInstance().getTextureManager().register(PALETTE, palette);
            }
            if (a != lastAccent || b != lastEnd) {
                var image = palette.getPixels();
                image.setPixel(0, 0, a | 0xFF000000);
                image.setPixel(1, 0, Px.mix(a, b, .55f) | 0xFF000000);
                image.setPixel(2, 0, b | 0xFF000000);
                palette.upload();
                lastAccent = a;
                lastEnd = b;
            }
            var matrix = new Matrix3x2f(g.pose());
            var bounds = new ScreenRectangle(0, 0, w, h).transformMaxBounds(matrix);
            float cycle = Theme.animations ? (System.nanoTime() % 300_000_000_000L) / 300_000_000_000f : 0;
            ((GuiGraphicsAccess) g)
                    .arcanicvisuals$state()
                    .submitGuiElement(new State(
                            matrix,
                            w,
                            h,
                            cycle,
                            Math.max(0, Math.min(1, progress)),
                            Px.a(loading ? 0xFF0000 : 0, Math.round(opacity * 255)),
                            TextureSetup.singleTexture(palette.getTextureView(), palette.getSampler()),
                            bounds));
        } catch (RuntimeException ex) {
            unavailable = true;
            System.err.println("[ArcanicVisuals] front end fallback: " + ex);
            fallback(g, w, h, opacity);
        }
    }

    private static void fallback(GuiGraphics g, int w, int h, float opacity) {
        double time = Theme.animations ? System.nanoTime() / 1e9 : 0;
        // Full-height gradients have no moving silhouette or staircase at strip boundaries.
        for (int i = 0; i < 128; i++) {
            double f = i / 127.0;
            int x = w * i / 128, x1 = w * (i + 1) / 128;
            float glow = (float) (.5 + .5 * Math.sin(f * 4 + time * .12));
            int top = Px.mix(0x080611, Theme.accent(), .025f + .02f * glow);
            int bottom = Px.mix(0x080611, Theme.accent(), .14f + .06f * glow);
            g.fillGradient(x, 0, x1, h, Px.a(top, Math.round(255 * opacity)), Px.a(bottom, Math.round(255 * opacity)));
        }
    }

    private record State(
            Matrix3x2f pose,
            int w,
            int h,
            float time,
            float progress,
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
            vertex(v, 0, 0);
            vertex(v, 0, h);
            vertex(v, w, h);
            vertex(v, w, 0);
        }

        private void vertex(VertexConsumer v, int x, int y) {
            v.addVertexWith2DPose(pose, x, y).setUv(time, progress).setColor(colour);
        }
    }
}
