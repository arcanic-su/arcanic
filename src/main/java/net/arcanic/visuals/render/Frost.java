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
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
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

/** Frosted glass behind panels (on the 1.21.11 GPU API). */
public final class Frost {
    private Frost() {}

    public static final Identifier ID = Identifier.fromNamespaceAndPath("arcanicvisuals", "frost");
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/frost_blur"))
            .withVertexShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/frost_blur"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/frost_blur"))
            .withUniform("Frost", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();
    private static final float[] STEPS = {1.5f, 2.5f, 3.5f, 4.5f};

    private static TextureTarget a, b;
    private static GpuBuffer[] uniforms;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    private static long frame = Long.MIN_VALUE;
    private static boolean ready, broken;
    private static int srcW, srcH;

    /** The blurred picture as a GUI texture (it points at our render target, which we own and free ourselves). */
    private static final class View extends AbstractTexture {
        void show(TextureTarget t) {
            texture = t.getColorTexture();
            textureView = t.getColorTextureView();
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }

        @Override
        public void close() {
            texture = null;
            textureView = null;
        }
    }

    private static View view;

    public static boolean on() {
        return Modules.PREFS.blur.on() && !Optimizer.lowEffects() && !broken && Minecraft.getInstance().screen == null;
    }

    /** Blurs the current frame once per frame; returns whether a frosted picture is available. */
    public static boolean prepare() {
        if (!on()) return false;
        var mc = Minecraft.getInstance();
        long f = mc.getFrameTimeNs();
        if (f == frame) return ready;
        frame = f;
        ready = false;
        try {
            var device = RenderSystem.getDevice();
            if (!device.precompilePipeline(PIPELINE).isValid()) {
                broken = true;
                return false;
            }
            var main = mc.getMainRenderTarget();
            int w = Math.max(1, main.width / 2), h = Math.max(1, main.height / 2);
            if (a == null || a.width != w || a.height != h) {
                close();
                a = new TextureTarget("Arcanic frost A", w, h, false);
                b = new TextureTarget("Arcanic frost B", w, h, false);
            }
            if (uniforms == null) {
                uniforms = new GpuBuffer[STEPS.length];
                for (int i = 0; i < STEPS.length; i++)
                    uniforms[i] = device.createBuffer(
                            () -> "Arcanic frost step", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
            }
            srcW = main.width;
            srcH = main.height;
            var encoder = device.createCommandEncoder();
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuTextureView src = main.getColorTextureView();
            float texW = 1f / main.width, texH = 1f / main.height;
            TextureTarget out = a;
            for (int i = 0; i < STEPS.length; i++) {
                DATA.clear();
                DATA.putFloat(texW).putFloat(texH).putFloat(STEPS[i]).putFloat(0);
                DATA.flip();
                encoder.writeToBuffer(uniforms[i].slice(), DATA);
                try (var pass = encoder.createRenderPass(
                        () -> "Arcanic frost", out.getColorTextureView(), OptionalInt.empty())) {
                    pass.setPipeline(PIPELINE);
                    pass.setUniform("Frost", uniforms[i]);
                    pass.bindTexture("Sampler0", src, sampler);
                    pass.draw(0, 3);
                }
                src = out.getColorTextureView();
                texW = 1f / out.width;
                texH = 1f / out.height;
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

    /**
     * The frosted picture inside a rounded rectangle at (x, y, w, h) in the current pose (window pixels under
     * Px.begin).
     */
    public static void backdrop(GuiGraphics g, int x, int y, int w, int h, int r) {
        if (w <= 0 || h <= 0 || !prepare()) return;
        int c = Px.am(0xFFFFFFFF, Px.fade);
        if ((c >>> 24) == 0) return;
        var mc = Minecraft.getInstance();
        float k = Px.width() / (float) Math.max(1, mc.getWindow().getGuiScaledWidth()); // GUI units → window pixels
        Vector2f p0 = g.pose().transformPosition(x, y, new Vector2f()),
                p1 = g.pose().transformPosition(x + w, y + h, new Vector2f());
        float sx = p0.x * k, sy = p0.y * k, ux = (p1.x - p0.x) * k / w, uy = (p1.y - p0.y) * k / h;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        // the middle in one piece, the rounded rows above and below one strip each
        strip(g, x, y + r, w, h - 2 * r, 0, r, sx, sy, ux, uy, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - .5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            strip(g, x + inset, y + i, w - inset * 2, 1, inset, i, sx, sy, ux, uy, c);
            strip(g, x + inset, y + h - 1 - i, w - inset * 2, 1, inset, h - 1 - i, sx, sy, ux, uy, c);
        }
    }

    private static void strip(
            GuiGraphics g, int x, int y, int w, int h, int dx, int dy, float sx, float sy, float ux, float uy, int c) {
        if (w <= 0 || h <= 0) return;
        float u = sx + dx * ux, top = sy + dy * uy;
        // render targets are stored bottom-up: walk the rows upwards in the texture
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                ID,
                x,
                y,
                u,
                srcH - top,
                w,
                h,
                Math.max(1, Math.round(w * ux)),
                -Math.max(1, Math.round(h * uy)),
                srcW,
                srcH,
                c);
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
        if (view != null) view.close();
        ready = false;
    }
}
