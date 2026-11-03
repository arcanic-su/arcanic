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
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.Optimizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;

public final class Frost {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("arcanicvisuals", "frost");
    private static final RenderPipeline PIPELINE;
    private static final float[] STEPS;
    private static TextureTarget a;
    private static TextureTarget b;
    private static GpuBuffer[] uniforms;
    private static final ByteBuffer DATA;
    private static long frame;
    private static boolean ready;
    private static boolean broken;
    private static int srcW;
    private static int srcH;
    private static View view;

    private Frost() {}

    public static boolean on() {
        return Modules.PREFS.blur.on() && !Optimizer.lowEffects() && !broken && Minecraft.getInstance().screen == null;
    }

    public static boolean prepare() {
        if (!on()) {
            return false;
        } else {
            Minecraft mc = Minecraft.getInstance();
            long f = mc.getFrameTimeNs();
            if (f == frame) {
                return ready;
            } else {
                frame = f;
                ready = false;

                try {
                    GpuDevice device = RenderSystem.getDevice();
                    if (!device.precompilePipeline(PIPELINE).isValid()) {
                        broken = true;
                        return false;
                    }

                    RenderTarget main = mc.getMainRenderTarget();
                    int w = Math.max(1, main.width / 2);
                    int h = Math.max(1, main.height / 2);
                    if (a == null || a.width != w || a.height != h) {
                        close();
                        a = new TextureTarget("Arcanic frost A", w, h, false);
                        b = new TextureTarget("Arcanic frost B", w, h, false);
                    }

                    if (uniforms == null) {
                        uniforms = new GpuBuffer[STEPS.length];

                        for (int i = 0; i < STEPS.length; i++) {
                            uniforms[i] = device.createBuffer(() -> "Arcanic frost step", 136, 16L);
                        }
                    }

                    srcW = main.width;
                    srcH = main.height;
                    CommandEncoder encoder = device.createCommandEncoder();
                    GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                    GpuTextureView src = main.getColorTextureView();
                    float texW = 1.0F / (float) main.width;
                    float texH = 1.0F / (float) main.height;
                    TextureTarget out = a;

                    for (int i = 0; i < STEPS.length; i++) {
                        DATA.clear();
                        DATA.putFloat(texW).putFloat(texH).putFloat(STEPS[i]).putFloat(0.0F);
                        DATA.flip();
                        encoder.writeToBuffer(uniforms[i].slice(), DATA);

                        try (RenderPass pass = encoder.createRenderPass(
                                () -> "Arcanic frost", out.getColorTextureView(), OptionalInt.empty())) {
                            pass.setPipeline(PIPELINE);
                            pass.setUniform("Frost", uniforms[i]);
                            pass.bindTexture("Sampler0", src, sampler);
                            pass.draw(0, 3);
                        }

                        src = out.getColorTextureView();
                        texW = 1.0F / (float) out.width;
                        texH = 1.0F / (float) out.height;
                        out = out == a ? b : a;
                    }

                    TextureTarget result = out == a ? b : a;
                    if (view == null) {
                        view = new View();
                        mc.getTextureManager().register(ID, view);
                    }

                    view.show(result);
                    ready = true;
                } catch (RuntimeException e) {
                    broken = true;
                    System.err.println("[ArcanicVisuals] frosted glass off: " + e);
                }

                return ready;
            }
        }
    }

    public static void backdrop(GuiGraphics g, int x, int y, int w, int h, int r) {
        if (w > 0 && h > 0 && prepare()) {
            int c = Px.am(-1, Px.fade);
            if (c >>> 24 != 0) {
                Minecraft mc = Minecraft.getInstance();
                float k =
                        (float) Px.width() / (float) Math.max(1, mc.getWindow().getGuiScaledWidth());
                Vector2f p0 = g.pose().transformPosition((float) x, (float) y, new Vector2f());
                Vector2f p1 = g.pose().transformPosition((float) (x + w), (float) (y + h), new Vector2f());
                float sx = p0.x * k;
                float sy = p0.y * k;
                float ux = (p1.x - p0.x) * k / (float) w;
                float uy = (p1.y - p0.y) * k / (float) h;
                r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
                strip(g, x, y + r, w, h - 2 * r, 0, r, sx, sy, ux, uy, c);

                for (int i = 0; i < r; i++) {
                    double dy = (double) (r - i) - 0.5;
                    int inset = (int) Math.round((double) r - Math.sqrt(Math.max(0.0, (double) (r * r) - dy * dy)));
                    strip(g, x + inset, y + i, w - inset * 2, 1, inset, i, sx, sy, ux, uy, c);
                    strip(g, x + inset, y + h - 1 - i, w - inset * 2, 1, inset, h - 1 - i, sx, sy, ux, uy, c);
                }
            }
        }
    }

    private static void strip(
            GuiGraphics g, int x, int y, int w, int h, int dx, int dy, float sx, float sy, float ux, float uy, int c) {
        if (w > 0 && h > 0) {
            float u = sx + (float) dx * ux;
            float top = sy + (float) dy * uy;
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    ID,
                    x,
                    y,
                    u,
                    (float) srcH - top,
                    w,
                    h,
                    Math.max(1, Math.round((float) w * ux)),
                    -Math.max(1, Math.round((float) h * uy)),
                    srcW,
                    srcH,
                    c);
        }
    }

    public static void close() {
        if (a != null) {
            a.destroyBuffers();
            a = null;
        }

        if (b != null) {
            b.destroyBuffers();
            b = null;
        }

        if (view != null) {
            view.close();
        }

        ready = false;
    }

    static {
        PIPELINE = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/frost_blur"))
                .withVertexShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/frost_blur"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/frost_blur"))
                .withUniform("Frost", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build();
        STEPS = new float[] {1.5F, 2.5F, 3.5F, 4.5F};
        DATA = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        frame = Long.MIN_VALUE;
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
