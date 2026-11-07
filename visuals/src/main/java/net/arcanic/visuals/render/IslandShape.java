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

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

public final class IslandShape {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("arcanicvisuals", "island_shape");
    private static final RenderPipeline PIPELINE;
    private static final int VEC4S = 14;
    private static final ByteBuffer DATA;
    private static TextureTarget target;
    private static final List<TextureTarget> retired;
    private static long retiredAt;
    private static GpuBuffer uniform;
    private static View view;
    private static boolean broken;

    private IslandShape() {}

    public static boolean draw(GuiGraphics g, int x, int y, int w, int h, Spec s, float opacity) {
        if (!broken && w > 0 && h > 0 && !(opacity <= 0.0F)) {
            try {
                if (!EmbeddedShaders.compile(PIPELINE).isValid()) {
                    broken = true;
                    return false;
                } else {
                    Minecraft mc = Minecraft.getInstance();
                    GpuDevice device = RenderSystem.getDevice();
                    long frame = mc.getFrameTimeNs();
                    if (frame != retiredAt) {
                        for (TextureTarget t : retired) {
                            t.destroyBuffers();
                        }

                        retired.clear();
                    }

                    if (target == null || target.width < w || target.height < h) {
                        if (target != null) {
                            retired.add(target);
                            retiredAt = frame;
                        }

                        int tw = Math.max(256, (w + 127) / 128 * 128);
                        int th = Math.max(128, (h + 63) / 64 * 64);
                        if (target != null) {
                            tw = Math.max(tw, target.width);
                            th = Math.max(th, target.height);
                        }

                        target = new TextureTarget("Arcanic island", tw, th, false);
                    }

                    if (uniform == null) {
                        uniform = device.createBuffer(() -> "Arcanic island", 136, 224L);
                    }

                    DATA.clear();
                    put(
                            (float) target.width,
                            (float) target.height,
                            (float) (System.nanoTime() % 3600000000000L) / 1.0E9F,
                            s.scale);
                    put(s.mx, s.my, s.mw, s.mh);
                    put(s.lx, s.ly, s.lw, s.lh);
                    put(s.rx, s.ry, s.rw, s.rh);
                    put(s.mr, s.lr, s.rr, s.melt);
                    colour(s.fill, 1.0F);
                    colour(s.rim, 1.0F);
                    colour(s.glow, s.glowStrength);
                    colour(s.sweep, s.sweepStrength);
                    put(s.sweepHead, s.sweepLength, s.glowReach, s.shadow);
                    put(s.shadowBlur, s.shadowDrop, s.tintStrength, s.sheen);
                    colour(s.tint, s.tintReach);
                    put(s.dx, s.dy, s.dw, s.dh);
                    put(s.dr, 0.0F, 0.0F, 0.0F);
                    DATA.flip();
                    CommandEncoder encoder = device.createCommandEncoder();
                    encoder.writeToBuffer(uniform.slice(), DATA);

                    try (RenderPass pass = encoder.createRenderPass(
                            () -> "Arcanic island", target.getColorTextureView(), OptionalInt.of(0))) {
                        pass.setPipeline(PIPELINE);
                        pass.setUniform("Island", uniform);
                        pass.draw(0, 3);
                    }

                    if (view == null) {
                        view = new View();
                        mc.getTextureManager().register(ID, view);
                    }

                    view.show(target);
                    int a = Math.round(Math.max(0.0F, Math.min(1.0F, opacity)) * 255.0F);
                    g.blit(
                            RenderPipelines.GUI_TEXTURED,
                            ID,
                            x,
                            y,
                            0.0F,
                            0.0F,
                            w,
                            h,
                            w,
                            h,
                            target.width,
                            target.height,
                            a << 24 | 0xFFFFFF);
                    return true;
                }
            } catch (RuntimeException e) {
                broken = true;
                System.err.println("[ArcanicVisuals] island shader off: " + e);
                return false;
            }
        } else {
            return !broken;
        }
    }

    private static void put(float a, float b, float c, float d) {
        DATA.putFloat(a).putFloat(b).putFloat(c).putFloat(d);
    }

    private static void colour(int argb, float k) {
        put(
                (float) (argb >> 16 & 255) / 255.0F,
                (float) (argb >> 8 & 255) / 255.0F,
                (float) (argb & 255) / 255.0F,
                (float) (argb >>> 24) / 255.0F * k);
    }

    static {
        PIPELINE = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/island"))
                .withVertexShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/island"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/island"))
                .withUniform("Island", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build();
        DATA = ByteBuffer.allocateDirect(224).order(ByteOrder.nativeOrder());
        retired = new ArrayList<>();
        retiredAt = Long.MIN_VALUE;
    }

    public static final class Spec {
        public float mx;
        public float my;
        public float mw;
        public float mh;
        public float mr;
        public float lx;
        public float ly;
        public float lw;
        public float lh;
        public float lr;
        public float rx;
        public float ry;
        public float rw;
        public float rh;
        public float rr;
        public float dx;
        public float dy;
        public float dw;
        public float dh;
        public float dr;
        public float melt;
        public int fill = 0xF0000000;
        public int rim = 654311423;
        public int glow;
        public int sweep;
        public int tint;
        public float glowStrength;
        public float glowReach = 12.0F;
        public float sweepStrength;
        public float sweepHead;
        public float sweepLength = 0.14F;
        public float shadow = 0.32F;
        public float shadowBlur = 8.0F;
        public float shadowDrop = 3.0F;
        public float tintStrength;
        public float tintReach = 0.7F;
        public float sheen = 0.05F;
        public float scale = 1.0F;
    }

    private static final class View extends AbstractTexture {
        void show(TextureTarget t) {
            this.texture = t.getColorTexture();
            this.textureView = t.getColorTextureView();
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }

        public void close() {
            this.texture = null;
            this.textureView = null;
        }
    }
}
