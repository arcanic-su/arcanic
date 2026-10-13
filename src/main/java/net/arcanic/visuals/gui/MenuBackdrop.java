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
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.mixin.*;
import net.arcanic.visuals.render.EmbeddedShaders;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/** The animated menu backgrounds, separate from the early resource-loading renderer. */
public final class MenuBackdrop {
    private static final Identifier PALETTE = Identifier.fromNamespaceAndPath("arcanicvisuals", "menu_palette");
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(
                    RenderPipelinesAccess.arcanicvisuals$guiTextureSnippet())
            .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/menu_arcanic"))
            .withVertexShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/front_end"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/menu_arcanic"))
            .withCull(false)
            .build();
    private static DynamicTexture palette;
    private static int lastA, lastB, previous = 4, current = 4;
    private static long changed;
    private static boolean unavailable;
    public static final String[] NAMES = {"", "Nebula", "Silk", "Contour", "Ember"};

    private MenuBackdrop() {}

    public static void select(int mode) {
        Theme.frontBackground = Math.max(1, Math.min(4, mode));
        Config.dirty();
    }

    public static void draw(GuiGraphics g) {
        draw(g, 1);
    }
    /** The main menu background at the given opacity. */
    public static void draw(GuiGraphics g, float opacity) {
        if (opacity <= 0) return;
        if (unavailable) {
            FrontEndBackdrop.draw(g, opacity, 0, false, true);
            return;
        }
        int mode = Math.max(1, Math.min(4, Theme.frontBackground));
        // Finish the current dissolve before accepting the latest queued choice.
        if (mode != current && (!Theme.animations || changed == 0 || System.nanoTime() - changed >= 600_000_000L)) {
            previous = current;
            current = mode;
            changed = System.nanoTime();
        }
        try {
            if (!EmbeddedShaders.compile(PIPELINE).isValid()
                    && !RenderSystem.getDevice().precompilePipeline(PIPELINE).isValid()) {
                unavailable = true;
                FrontEndBackdrop.draw(g, opacity, 0, false, true);
                return;
            }
            int a = Theme.accent(), b = Theme.accent2();
            if (palette == null) {
                palette = new DynamicTexture(() -> "Arcanic menu palette", new NativeImage(3, 1, false));
                Minecraft.getInstance().getTextureManager().register(PALETTE, palette);
            }
            if (a != lastA || b != lastB) {
                var image = palette.getPixels();
                image.setPixel(0, 0, a | 0xFF000000);
                image.setPixel(1, 0, Px.mix(a, b, .45f) | 0xFF000000);
                image.setPixel(2, 0, b | 0xFF000000);
                palette.upload();
                lastA = a;
                lastB = b;
            }
            int w = Px.width(), h = Px.height();
            var matrix = new Matrix3x2f(g.pose());
            var bounds = new ScreenRectangle(0, 0, w, h).transformMaxBounds(matrix);
            float cycle = Theme.animations ? (System.nanoTime() % 300_000_000_000L) / 300_000_000_000f : 0;
            float fade = Theme.animations ? Math.min(1, (System.nanoTime() - changed) / 600_000_000f) : 1;
            int colour = Math.round(Math.max(0, Math.min(1, opacity)) * 255) << 24 | current << 16 | previous << 8;
            ((GuiGraphicsAccess) g)
                    .arcanicvisuals$state()
                    .submitGuiElement(new State(
                            matrix,
                            w,
                            h,
                            cycle,
                            fade,
                            colour,
                            TextureSetup.singleTexture(palette.getTextureView(), palette.getSampler()),
                            bounds));
        } catch (RuntimeException ex) {
            unavailable = true;
            System.err.println("[ArcanicVisuals] menu background fallback: " + ex);
            FrontEndBackdrop.draw(g, opacity, 0, false, true);
        }
    }

    private record State(
            Matrix3x2f pose,
            int w,
            int h,
            float time,
            float fade,
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
            v.addVertexWith2DPose(pose, x, y).setUv(time, fade).setColor(colour);
        }
    }
}
