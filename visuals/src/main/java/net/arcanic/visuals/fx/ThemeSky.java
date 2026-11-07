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
package net.arcanic.visuals.fx;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class ThemeSky {
    private static final int BYTES = 96;
    private static final int BAKE_BYTES = 32;
    private static final int BAKE_W = 2048;
    private static final int BAKE_H = 1024;
    private static final int SLICES = 12;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder());
    private static final ByteBuffer BAKE_DATA = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
    private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Set<String> REPORTED = new HashSet<>();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static GpuBuffer uniforms;
    private static GpuBuffer bakeUniforms;
    private static TextureTarget baked;
    private static String bakedKey;
    private static int slice;
    private static long started = -1L;
    private static long lastBake;
    private static boolean captured;

    private ThemeSky() {}

    public static void capture(Matrix4f view, Matrix4f projection) {
        VIEW.set(view);
        PROJECTION.set(projection);
        captured = true;
    }

    public static boolean viewProjection(Matrix4f dest) {
        dest.set(PROJECTION).mul(VIEW);
        return captured;
    }

    private static RenderPipeline view(String key, boolean sampled) {
        return (RenderPipeline) PIPELINES.computeIfAbsent(key, (k) -> {
            RenderPipeline.Builder b = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/theme_sky_" + k))
                    .withVertexShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/theme_sky"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/theme_sky_" + k))
                    .withUniform("ThemeSky", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
            if (sampled) {
                b = b.withSampler("Sampler0");
            }

            return b.build();
        });
    }

    private static RenderPipeline bake(String key) {
        return (RenderPipeline) PIPELINES.computeIfAbsent(key + "_bake", (k) -> RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/theme_sky_" + k))
                .withVertexShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/theme_sky_bake"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/theme_sky_" + k))
                .withUniform("SkyBake", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build());
    }

    public static void release() {
        started = -1L;
        bakedKey = null;
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }

        if (bakeUniforms != null) {
            bakeUniforms.close();
            bakeUniforms = null;
        }

        if (baked != null) {
            baked.destroyBuffers();
            baked = null;
        }
    }

    public static void render(SkyShader w) {
        Minecraft mc = Minecraft.getInstance();
        if (captured && mc.level != null && mc.player != null) {
            String key =
                    switch ((String) w.mode.get()) {
                        case "Каустика" -> "caustic";
                        case "Галактика" -> "galaxy";
                        case "Глубокий космос" -> "space";
                        default -> "nebula";
                    };
            boolean bakes = key.equals("galaxy") || key.equals("space");

            try {
                GpuDevice device = RenderSystem.getDevice();
                RenderPipeline p = view(bakes ? key + "_view" : key, bakes);
                if (!device.precompilePipeline(p).isValid()
                        || bakes && !device.precompilePipeline(bake(key)).isValid()) {
                    if (REPORTED.add(key)) {
                        System.err.println("[ArcanicVisuals] Theme sky shader failed: " + key);
                    }

                    return;
                }

                if (started < 0L) {
                    started = System.currentTimeMillis();
                }

                if (uniforms == null) {
                    uniforms = device.createBuffer(() -> "Arcanic theme sky parameters", 136, 96L);
                }

                int c = w.color.argb();
                float r = (float) (c >> 16 & 255) / 255.0F;
                float g = (float) (c >> 8 & 255) / 255.0F;
                float b = (float) (c & 255) / 255.0F;
                float seconds = (float) (System.currentTimeMillis() - started) / 1000.0F * w.speed.f();
                CommandEncoder encoder = device.createCommandEncoder();
                if (bakes) {
                    bake(key, r, g, b, seconds);
                }

                INVERSE.set(PROJECTION).mul(VIEW).invert();
                DATA.clear();
                INVERSE.get(0, DATA);
                DATA.position(64);
                DATA.putFloat(r).putFloat(g).putFloat(b).putFloat(seconds);
                DATA.putFloat(1.0F).putFloat(1.0F).putFloat(1.0F).putFloat(w.opacity.f());
                DATA.flip();
                encoder.writeToBuffer(uniforms.slice(), DATA);
                RenderTarget target = mc.getMainRenderTarget();

                try (RenderPass pass = encoder.createRenderPass(
                        () -> "Arcanic theme sky", target.getColorTextureView(), OptionalInt.empty())) {
                    pass.setPipeline(p);
                    pass.setUniform("ThemeSky", uniforms);
                    if (bakes) {
                        pass.bindTexture(
                                "Sampler0",
                                baked.getColorTextureView(),
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                    }

                    pass.draw(0, 3);
                }
            } catch (RuntimeException e) {
                if (REPORTED.add(key)) {
                    System.err.println("[ArcanicVisuals] Theme sky " + key + ": " + e);
                }
            }
        }
    }

    private static void bake(String key, float r, float g, float b, float seconds) {
        GpuDevice device = RenderSystem.getDevice();
        if (baked == null) {
            baked = new TextureTarget("Arcanic theme sky bake", 2048, 1024, false);
            bakedKey = null;
        }

        if (bakeUniforms == null) {
            bakeUniforms = device.createBuffer(() -> "Arcanic theme sky bake", 136, 32L);
        }

        BAKE_DATA.clear();
        BAKE_DATA.putFloat(r).putFloat(g).putFloat(b).putFloat(seconds);
        BAKE_DATA.putFloat(2048.0F).putFloat(1024.0F).putFloat(0.0F).putFloat(0.0F);
        BAKE_DATA.flip();
        CommandEncoder encoder = device.createCommandEncoder();
        encoder.writeToBuffer(bakeUniforms.slice(), BAKE_DATA);
        long now = System.currentTimeMillis();
        boolean full = !key.equals(bakedKey) || now - lastBake > 1000L;
        lastBake = now;

        try (RenderPass pass = encoder.createRenderPass(
                () -> "Arcanic theme sky bake", baked.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(bake(key));
            pass.setUniform("SkyBake", bakeUniforms);
            if (full) {
                bakedKey = key;
                slice = 0;
            } else {
                int y0 = 1024 * slice / 12;
                int y1 = 1024 * (slice + 1) / 12;
                pass.enableScissor(0, y0, 2048, y1 - y0);
                slice = (slice + 1) % 12;
            }

            pass.draw(0, 3);
        }
    }
}
