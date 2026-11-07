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
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

public final class ShaderSky {
    private static final int BYTES = 176;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(176).order(ByteOrder.nativeOrder());
    private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();
    private static GpuBuffer uniforms;
    private static long started = -1L;
    private static float fogStart;
    private static float fogEnd = 1.0F;
    private static float fogR;
    private static float fogG;
    private static float fogB;
    private static float fogAlpha;
    private static final Set<String> REPORTED = new HashSet<>();

    private ShaderSky() {}

    private static Identifier id(String s) {
        return Identifier.fromNamespaceAndPath("arcanicvisuals", s);
    }

    private static RenderPipeline pipeline(String mode) {
        return (RenderPipeline) PIPELINES.computeIfAbsent(mode, (key) -> RenderPipeline.builder()
                .withLocation(id("pipeline/shader_sky_" + key))
                .withVertexShader(id("core/shader_sky"))
                .withFragmentShader(id("core/shader_sky_" + key))
                .withUniform("ShaderSky", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
                .withBlend(new BlendFunction(
                        SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ZERO))
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build());
    }

    public static void release() {
        started = -1L;
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }

    public static boolean render(SkyShader w) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            String key =
                    switch ((String) w.mode.get()) {
                        case "Сияние" -> "aurora";
                        case "Звёзды" -> "stars";
                        case "Плазма" -> "plasma";
                        case "Неон" -> "neon";
                        default -> "nebula";
                    };

            try {
                GpuDevice device = RenderSystem.getDevice();
                RenderPipeline p = pipeline(key);
                if (!device.precompilePipeline(p).isValid()) {
                    if (REPORTED.add(key)) {
                        System.err.println("[ArcanicVisuals] Animated sky shader failed: " + key);
                    }

                    return false;
                } else {
                    if (started < 0L) {
                        started = System.currentTimeMillis();
                    }

                    if (uniforms == null) {
                        uniforms = device.createBuffer(() -> "Arcanic animated sky parameters", 136, 176L);
                    }

                    RenderTarget target = mc.getMainRenderTarget();
                    Camera camera = mc.gameRenderer.getMainCamera();
                    ShaderSkyUniforms.write(
                            DATA,
                            w,
                            (float) (System.currentTimeMillis() - started) / 1000.0F,
                            target.width,
                            target.height,
                            camera.yRot(),
                            camera.xRot(),
                            ((Integer) mc.options.fov().get()).floatValue(),
                            fogStart,
                            fogEnd,
                            fogR,
                            fogG,
                            fogB,
                            fogAlpha);
                    CommandEncoder encoder = device.createCommandEncoder();
                    encoder.writeToBuffer(uniforms.slice(), DATA);

                    try (RenderPass pass = encoder.createRenderPass(
                            () -> "Arcanic animated sky", target.getColorTextureView(), OptionalInt.empty())) {
                        pass.setPipeline(p);
                        pass.setUniform("ShaderSky", uniforms);
                        pass.draw(0, 3);
                    }

                    return true;
                }
            } catch (RuntimeException e) {
                if (REPORTED.add(key)) {
                    System.err.println("[ArcanicVisuals] Animated sky " + key + ": " + e);
                }

                return false;
            }
        } else {
            return false;
        }
    }

    public static void captureFog(float environmentalStart, float environmentalEnd, float skyEnd, Vector4f colour) {
        fogStart = Math.min(0.0F, environmentalStart);
        fogEnd = Math.max(fogStart + 0.1F, Math.min(environmentalEnd, skyEnd));
        fogR = colour.x;
        fogG = colour.y;
        fogB = colour.z;
        fogAlpha = colour.w;
    }
}
