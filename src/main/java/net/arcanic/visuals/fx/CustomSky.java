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
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.mixin.GameRendererAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/** Draws World Tweaker's sky instead of vanilla's sky disc: one full-screen pass with our sky shaders. */
public final class CustomSky {
    private CustomSky() {}

    private static Identifier id(String p) {
        return Identifier.fromNamespaceAndPath("arcanicvisuals", p);
    }

    private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<String, Boolean> BROKEN = new HashMap<>();
    private static final Map<String, RenderPipeline> SMALL =
            new HashMap<>(); // the same shaders, writing into the small picture
    private static TextureTarget small;
    private static RenderPipeline blit;
    private static boolean smallBroken;
    private static GpuBuffer rayParams, fsParams;
    private static final ByteBuffer RAY = ByteBuffer.allocateDirect(160).order(ByteOrder.nativeOrder());
    private static final ByteBuffer FS = ByteBuffer.allocateDirect(208).order(ByteOrder.nativeOrder());
    private static DynamicTexture noise;
    private static final long T0 = System.nanoTime();

    private static boolean fullScreen(String key) {
        return key.equals("aurora") || key.equals("blackhole") || key.equals("starfall") || key.equals("endbeams");
    }

    private static boolean needsNoise(String key) {
        return key.equals("blackhole") || key.equals("endbeams");
    }

    private static String key(WorldTweaks w) {
        if (w.skyMode.is("Градиент")) return "gradient";
        return switch (w.shader.get()) {
            case "Dreamscape" -> "dreamscape";
            case "Aurora" -> "aurora";
            case "Galaxy" -> "galaxy";
            case "Nebula" -> "nebula";
            case "Clouds" -> "clouds";
            case "Black Hole" -> "blackhole";
            case "Starfall" -> "starfall";
            case "Caustics" -> "caustics";
            case "Storm" -> "storm";
            default -> "endbeams";
        };
    }

    private static int divisor(WorldTweaks w, String key, int width, int height) {
        if (key.equals("gradient") || smallBroken) return 1;
        if (Optimizer.lowEffects()) return 4; // the optimiser: the smallest sky picture
        return switch (w.skyQuality.get()) {
            case "Высокое" -> 1;
            case "Среднее" -> 2;
            case "Низкое" -> 3;
            default -> Math.max(2, Math.min(4, (int) Math.round(Math.sqrt(width * (double) height / 1_100_000.0))));
        };
    }

    private static RenderPipeline blitPipeline() {
        if (blit == null)
            blit = RenderPipeline.builder()
                    .withLocation(id("pipeline/arcsky_blit"))
                    .withVertexShader(id("core/sky_fs"))
                    .withFragmentShader(id("core/arcsky_blit"))
                    .withSampler("Sampler0")
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build();
        return blit;
    }

    private static RenderPipeline pipeline(String key) {
        return pipeline(key, false);
    }

    private static RenderPipeline pipeline(String key, boolean offscreen) {
        return (offscreen ? SMALL : PIPELINES).computeIfAbsent(key, k -> {
            RenderPipeline.Builder b = RenderPipeline.builder()
                    .withLocation(id("pipeline/arcsky_" + k + (offscreen ? "_small" : "")))
                    .withFragmentShader(id("core/arcsky_" + k))
                    .withCull(false)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
            b = offscreen ? b.withoutBlend() : b.withBlend(BlendFunction.TRANSLUCENT);
            if (fullScreen(k)) {
                b = b.withVertexShader(id("core/sky_fs"))
                        .withUniform("SkyParams", UniformType.UNIFORM_BUFFER)
                        .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES);
                if (needsNoise(k)) b = b.withSampler("NoiseTex").withSampler("History");
            } else {
                b = b.withVertexShader(id("core/sky_rays"))
                        .withUniform("ArcSky", UniformType.UNIFORM_BUFFER)
                        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS);
            }
            return b.build();
        });
    }

    private static Method packInUse;
    private static Object iris;
    private static boolean irisChecked;

    public static boolean shaderPackInUse() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                packInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
            }
        }
        try {
            return packInUse != null && (boolean) packInUse.invoke(iris);
        } catch (Throwable e) {
            return false;
        }
    }

    private static double hash(int x, int y) {
        long h = x * 374761393L + y * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0;
    }

    private static GpuTextureView noiseView() {
        if (noise == null) {
            NativeImage img = new NativeImage(256, 256, false);
            Random r = new Random(1337);
            for (int y = 0; y < 256; y++)
                for (int x = 0; x < 256; x++) {
                    // red/green: white noise (green is red shifted, as the shaders expect), blue: smooth value noise
                    double fx = x / 32.0, fy = y / 32.0;
                    int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
                    double tx = fx - ix, ty = fy - iy, sx = tx * tx * (3 - 2 * tx), sy = ty * ty * (3 - 2 * ty);
                    double v = (hash(ix & 7, iy & 7) * (1 - sx) + hash((ix + 1) & 7, iy & 7) * sx) * (1 - sy)
                            + (hash(ix & 7, (iy + 1) & 7) * (1 - sx) + hash((ix + 1) & 7, (iy + 1) & 7) * sx) * sy;
                    int red = r.nextInt(256), blue = (int) Math.round(v * 255);
                    img.setPixel(x, y, 0xFF000000 | red << 16 | (red * 7 + 37 & 255) << 8 | blue);
                }
            noise = new DynamicTexture(() -> "Arcanic sky noise", img);
        }
        return noise.getTextureView();
    }

    public static void release() {
        if (noise != null) {
            noise.close();
            noise = null;
        }
        if (small != null) {
            small.destroyBuffers();
            small = null;
        }
    }

    /** Called instead of vanilla's sky disc; false when vanilla should draw it after all. */
    public static boolean render() {
        WorldTweaks w = Modules.WORLD;
        if (!w.skyActive()) return false;
        String key = key(w);
        if (BROKEN.getOrDefault(key, false)) return false;
        Minecraft mc = Minecraft.getInstance();
        var device = RenderSystem.getDevice();
        var screen = mc.getMainRenderTarget();
        int div = divisor(w, key, screen.width, screen.height);
        if (div > 1) {
            try {
                int sw = Math.max(1, screen.width / div), sh = Math.max(1, screen.height / div);
                if (small == null || small.width != sw || small.height != sh) {
                    if (small != null) small.destroyBuffers();
                    small = new TextureTarget("Arcanic sky", sw, sh, false);
                }
                if (!device.precompilePipeline(blitPipeline()).isValid()
                        || !device.precompilePipeline(pipeline(key, true)).isValid()) {
                    smallBroken = true;
                    div = 1;
                }
            } catch (RuntimeException e) {
                smallBroken = true;
                div = 1;
                System.err.println("[ArcanicVisuals] small sky off: " + e);
            }
        }
        boolean offscreen = div > 1;
        RenderPipeline p = pipeline(key, offscreen);
        try {
            if (!device.precompilePipeline(p).isValid()) {
                BROKEN.put(key, true);
                System.err.println("[ArcanicVisuals] sky shader failed: " + key);
                return false;
            }
            var camera = mc.gameRenderer.getMainCamera();
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float fov = ((GameRendererAccess) mc.gameRenderer).arcanicvisuals$fov(camera, pt, true);
            RenderTarget target = offscreen ? small : screen; // where the sky is worked out
            OptionalInt wipe = offscreen ? OptionalInt.of(0) : OptionalInt.empty();
            float aspect = target.width / (float) Math.max(1, target.height),
                    th = (float) Math.tan(Math.toRadians(fov) / 2);
            Vector3f f = new Vector3f(camera.forwardVector()),
                    up = new Vector3f(camera.upVector()),
                    left = new Vector3f(camera.leftVector());
            // top left, bottom left, bottom right, top right
            Vector3f[] rays = {
                ray(f, up, left, th, aspect, -1, 1),
                ray(f, up, left, th, aspect, -1, -1),
                ray(f, up, left, th, aspect, 1, -1),
                ray(f, up, left, th, aspect, 1, 1)
            };
            float time = (System.nanoTime() - T0) / 1e9f * w.speed.f();
            var encoder = device.createCommandEncoder();
            if (fullScreen(key)) {
                if (fsParams == null)
                    fsParams = device.createBuffer(
                            () -> "Arcanic sky params", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 208);
                FS.clear();
                for (Vector3f r : rays)
                    FS.putFloat(r.x).putFloat(r.y).putFloat(r.z).putFloat(0);
                FS.putFloat(time).putFloat(target.width).putFloat(target.height).putFloat(w.skyBright.f());
                putColour(FS, w.colourA(), w.skyGradient.on() ? 1 : 0);
                putColour(
                        FS,
                        w.colourB(),
                        switch (key) {
                            case "blackhole" -> 0.5f;
                            case "starfall" -> 1.5f;
                            case "aurora" -> 2.5f;
                            default -> 3.5f;
                        });
                FS.putFloat(0).putFloat(0).putFloat(0).putFloat(0); // no temporal history
                for (int i = 0; i < 16; i++) FS.putFloat(i % 5 == 0 ? 1 : 0); // previous view-projection (unused)
                FS.putFloat(w.alpha.f())
                        .putFloat(w.fullSky.on() ? 1 : 0)
                        .putFloat(0)
                        .putFloat(0);
                FS.flip();
                encoder.writeToBuffer(fsParams.slice(), FS);
                var view = needsNoise(key) ? noiseView() : null; // created before the pass opens
                try (RenderPass pass =
                        encoder.createRenderPass(() -> "Arcanic sky", target.getColorTextureView(), wipe)) {
                    pass.setPipeline(p);
                    pass.setUniform("SkyParams", fsParams);
                    if (view != null) {
                        pass.bindTexture(
                                "NoiseTex", view, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
                        pass.bindTexture(
                                "History", view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                    }
                    pass.draw(0, 3);
                }
            } else {
                if (rayParams == null)
                    rayParams = device.createBuffer(
                            () -> "Arcanic sky colours", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 160);
                RAY.clear();
                int a = w.colourA(), b = w.skyGradient.on() ? w.colourB() : w.colourA();
                putColour(RAY, a, 1);
                putColour(RAY, a, 1);
                putColour(RAY, w.colourC(), 1);
                putColour(RAY, b, 1);
                int style =
                        switch (w.dreamStyle.get()) {
                            case "Nebula" -> 1;
                            case "Silk" -> 2;
                            case "Contour" -> 3;
                            default -> 4;
                        };
                RAY.putFloat(style).putFloat(w.skyBright.f()).putFloat(300).putFloat(w.pattern.f());
                var cam = camera.position();
                RAY.putFloat((float) cam.x)
                        .putFloat((float) cam.y)
                        .putFloat((float) cam.z)
                        .putFloat(time);
                float spin = time * .01f;
                RAY.putFloat((float) Math.sin(spin))
                        .putFloat((float) Math.cos(spin))
                        .putFloat(w.fullSky.on() ? 1 : 0)
                        .putFloat(0);
                RAY.putFloat(target.width).putFloat(target.height).putFloat(0).putFloat(0);
                RAY.flip();
                encoder.writeToBuffer(rayParams.slice(), RAY);
                BufferBuilder bb =
                        Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                int alpha = Math.round(255 * (key.equals("gradient") ? 1 : w.alpha.f()));
                int colour = alpha << 24 | 0xFFFFFF;
                bb.addVertex(rays[1].x, rays[1].y, rays[1].z).setUv(-1, -1).setColor(colour);
                bb.addVertex(rays[2].x, rays[2].y, rays[2].z).setUv(1, -1).setColor(colour);
                bb.addVertex(rays[3].x, rays[3].y, rays[3].z).setUv(1, 1).setColor(colour);
                bb.addVertex(rays[0].x, rays[0].y, rays[0].z).setUv(-1, 1).setColor(colour);
                try (MeshData mesh = bb.buildOrThrow()) {
                    GpuBuffer vertices =
                            DefaultVertexFormat.POSITION_TEX_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
                    var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                    int count = mesh.drawState().indexCount();
                    try (RenderPass pass =
                            encoder.createRenderPass(() -> "Arcanic sky", target.getColorTextureView(), wipe)) {
                        pass.setPipeline(p);
                        pass.setUniform("ArcSky", rayParams);
                        pass.setVertexBuffer(0, vertices);
                        pass.setIndexBuffer(indices.getBuffer(count), indices.type());
                        pass.drawIndexed(0, 0, count, 1);
                    }
                }
            }
            if (offscreen) { // the small picture, stretched smoothly over the frame
                try (RenderPass pass = encoder.createRenderPass(
                        () -> "Arcanic sky blit", screen.getColorTextureView(), OptionalInt.empty())) {
                    pass.setPipeline(blitPipeline());
                    pass.bindTexture(
                            "Sampler0",
                            small.getColorTextureView(),
                            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                    pass.draw(0, 3);
                }
            }
            return true;
        } catch (RuntimeException e) {
            if (offscreen) {
                smallBroken = true;
                System.err.println("[ArcanicVisuals] small sky off: " + e);
                return false;
            }
            BROKEN.put(key, true);
            System.err.println("[ArcanicVisuals] sky " + key + ": " + e);
            return false;
        }
    }

    private static Vector3f ray(Vector3f f, Vector3f up, Vector3f left, float th, float aspect, float sx, float sy) {
        return new Vector3f(f)
                .add(new Vector3f(up).mul(sy * th))
                .sub(new Vector3f(left).mul(sx * th * aspect))
                .normalize();
    }

    private static void putColour(ByteBuffer b, int c, float w) {
        b.putFloat((c >> 16 & 255) / 255f)
                .putFloat((c >> 8 & 255) / 255f)
                .putFloat((c & 255) / 255f)
                .putFloat(w);
    }
}
