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
package net.arcanic.visuals.fx.impact;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.OptionalInt;
import net.arcanic.visuals.fx.Studio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

public final class ScreenFx {
    private static boolean wanted;
    public static boolean studio;
    private static float cx = 0.5F;
    private static float cy = 0.5F;
    // how much of the effect's point is in view: 1 on screen, fading out past the edges, 0 behind the camera.
    // Everything drawn round that point (the wave, the pull, the smear, the colour flood) is scaled by it, so a kill
    // behind the player or far off to the side never drags the whole picture with it.
    private static float presence = 1.0F;
    private static float waveR;
    private static float waveW;
    private static float waveK;
    private static float chroma;
    private static float desat;
    private static float flash;
    private static float flashR = 1.0F;
    private static float flashG = 1.0F;
    private static float flashB = 1.0F;
    private static float vignette;
    private static float blur;
    private static float pull;
    private static float floodR;
    private static float floodW;
    private static float floodDesat;
    private static float floodVivid;
    private static RenderTarget copy;
    private static RenderPipeline pipeline;
    private static MappableRingBuffer params;

    private ScreenFx() {}

    public static void reset() {
        wanted = false;
        presence = 1.0F;
        cx = 0.5F;
        cy = 0.5F;
        pull = 0.0F;
        blur = 0.0F;
        vignette = 0.0F;
        flash = 0.0F;
        desat = 0.0F;
        chroma = 0.0F;
        waveK = 0.0F;
        waveW = 0.0F;
        waveR = 0.0F;
        floodVivid = 0.0F;
        floodDesat = 0.0F;
        floodW = 0.0F;
        floodR = 0.0F;
    }

    public static void centre(float x, float y) {
        if (!studio) {
            float out = Math.max(Math.max(-x, x - 1.0F), Math.max(-y, y - 1.0F));
            presence = out <= 0.0F ? 1.0F : Math.max(0.0F, 1.0F - out / 0.35F);
            cx = Math.max(-0.15F, Math.min(1.15F, x));
            cy = Math.max(-0.15F, Math.min(1.15F, y));
        }
    }

    /** The effect's point is behind the camera: nothing may be drawn round it. */
    public static void behind() {
        if (!studio) presence = 0.0F;
    }

    /** Where a camera-space point falls on the screen, or behind() when it is behind the camera. */
    public static void project(Vector4f clip) {
        if (clip.w > 0.01F) centre(clip.x / clip.w * 0.5F + 0.5F, clip.y / clip.w * 0.5F + 0.5F);
        else behind();
    }

    public static void wave(float radius, float width, float strength, float chromatic) {
        if (!studio) {
            if (!(strength <= 0.0F) || !(chromatic <= 0.0F)) {
                wanted = true;
                if (strength >= waveK) {
                    waveR = radius;
                    waveW = width;
                    waveK = strength;
                }

                chroma = Math.max(chroma, chromatic);
            }
        }
    }

    public static void flash(float strength, float r, float g, float b) {
        if (!(strength <= 0.0F)) {
            if (studio) {
                Studio.flash(strength, r, g, b);
            } else {
                wanted = true;
                if (strength > flash) {
                    flash = strength;
                    flashR = r;
                    flashG = g;
                    flashB = b;
                }
            }
        }
    }

    public static void grade(float desaturate, float darkEdges, float smear, float drawIn) {
        if (studio) {
            Studio.grade(desaturate, darkEdges);
        } else if (!(desaturate <= 0.0F) || !(darkEdges <= 0.0F) || !(smear <= 0.0F) || drawIn != 0.0F) {
            wanted = true;
            desat = Math.max(desat, desaturate);
            vignette = Math.max(vignette, darkEdges);
            blur = Math.max(blur, smear);
            if (Math.abs(drawIn) > Math.abs(pull)) {
                pull = drawIn;
            }
        }
    }

    public static void flood(float radius, float width, float desaturate, float vivid) {
        if (studio) {
            Studio.grade(desaturate * 0.6F, 0.0F);
        } else if (!(desaturate <= 0.0F) || !(vivid <= 0.0F)) {
            wanted = true;
            if (desaturate >= floodDesat) {
                floodR = radius;
                floodW = width;
                floodDesat = desaturate;
            }

            floodVivid = Math.max(floodVivid, vivid);
        }
    }

    public static void apply() {
        if (wanted) {
            wanted = false;
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (main != null) {
                GpuTexture tex = main.getColorTexture();
                GpuTextureView view = main.getColorTextureView();
                if (tex != null && view != null) {
                    int w = Math.max(1, main.width);
                    int h = Math.max(1, main.height);
                    if (copy == null) {
                        copy = new TextureTarget("arcanicvisuals_totem_screen", w, h, false);
                    } else if (copy.width != w || copy.height != h) {
                        copy.resize(w, h);
                    }

                    GpuTexture copyTex = copy.getColorTexture();
                    GpuTextureView copyView = copy.getColorTextureView();
                    if (copyTex != null && copyView != null) {
                        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
                        enc.copyTextureToTexture(tex, copyTex, 0, 0, 0, 0, 0, w, h);
                        if (pipeline == null) {
                            pipeline = RenderPipeline.builder()
                                    .withLocation(Identifier.fromNamespaceAndPath(
                                            "arcanicvisuals", "pipeline/impact_totem_screen"))
                                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                                    .withFragmentShader(Identifier.fromNamespaceAndPath(
                                            "arcanicvisuals", "post/impact_totem_screen"))
                                    .withDepthWrite(false)
                                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                    .withColorWrite(true, true)
                                    .withCull(false)
                                    .withoutBlend()
                                    .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
                                    .withSampler("InSampler")
                                    .withUniform("Params", UniformType.UNIFORM_BUFFER)
                                    .build();
                            Std140SizeCalculator size = new Std140SizeCalculator();

                            for (int i = 0; i < 5; i++) {
                                size.putVec4();
                            }

                            params = new MappableRingBuffer(() -> "arcanicvisuals totem screen", 130, size.get());
                        }

                        float aspect = (float) w / (float) h;
                        float time = (float) (System.currentTimeMillis() % 100000L) / 1000.0F;
                        // the point-bound parts, scaled by how much of the point is in view and held to sane sizes
                        float p = presence;
                        float wk = Math.min(0.06F, waveK) * p;
                        float ch = Math.min(0.6F, chroma) * p;
                        float sm = Math.min(0.08F, blur) * p;
                        float pl = Math.max(-0.08F, Math.min(0.08F, pull)) * p;
                        float fd = floodDesat * p;
                        float fv = floodVivid * p;

                        try (GpuBuffer.MappedView mapped = enc.mapBuffer(params.currentBuffer(), false, true)) {
                            Std140Builder.intoBuffer(mapped.data())
                                    .putVec4(cx, cy, aspect, time)
                                    .putVec4(waveR, waveW, wk, ch)
                                    .putVec4(desat, flash, flashR, flashG)
                                    .putVec4(flashB, vignette, sm, pl)
                                    .putVec4(floodR, floodW, fd, fv);
                        }

                        try (RenderPass pass =
                                enc.createRenderPass(() -> "arcanicvisuals totem screen", view, OptionalInt.empty())) {
                            pass.setPipeline(pipeline);
                            pass.setUniform("Params", params.currentBuffer());
                            pass.bindTexture(
                                    "InSampler",
                                    copyView,
                                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                            pass.draw(0, 3);
                        }

                        params.rotate();
                    }
                }
            }
        }
    }
}
