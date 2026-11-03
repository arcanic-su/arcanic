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

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.fx.Studio;
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.arcanic.visuals.mixin.RenderTypeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class ImpactDraw {
    private static final Map<String, RenderPipeline> PIPES = new HashMap<>();
    private static final Map<String, RenderType> TYPES = new HashMap<>();
    public static final RenderType LINES;
    public static boolean studio;
    public static final SoundEvent RAILGUN_FIRE;
    public static final SoundEvent RAILGUN_CHARGE;
    private static float shake;
    private static float studioShake;
    private static long shakeAt;
    private static long studioShakeAt;
    public static final Matrix4f PROJECTION;

    private ImpactDraw() {}

    public static RenderType type(VertexFormat.Mode mode, boolean depthTest, boolean depthWrite, boolean additive) {
        return type(mode, depthTest, depthWrite, additive, false);
    }

    public static RenderType type(
            VertexFormat.Mode mode, boolean depthTest, boolean depthWrite, boolean additive, boolean bias) {
        String key = "pc|" + mode.name() + "|" + depthTest + "|" + depthWrite + "|" + additive + "|" + bias;
        return (RenderType) TYPES.computeIfAbsent(key, (k) -> {
            int var10000 = k.hashCode();
            String name = "impact_position_color_" + Integer.toHexString(var10000 & 0xFFFFFF);
            RenderPipeline pipe = (RenderPipeline) PIPES.computeIfAbsent(
                    k, (kk) -> pipeline(name, mode, false, depthTest, depthWrite, additive, bias));
            return RenderTypeAccess.arcanicvisuals$create(
                    "arcanicvisuals:" + name,
                    RenderSetup.builder(pipe).sortOnUpload().bufferSize(1536).createRenderSetup());
        });
    }

    public static RenderType type(Identifier tex, VertexFormat.Mode mode, boolean depthTest, boolean additive) {
        String key = String.valueOf(tex) + "|pt|" + mode.name() + "|" + depthTest + "|" + additive;
        return (RenderType) TYPES.computeIfAbsent(key, (k) -> {
            int var10000 = k.hashCode();
            String name = "impact_position_tex_color_" + Integer.toHexString(var10000 & 0xFFFFFF);
            RenderPipeline pipe = (RenderPipeline)
                    PIPES.computeIfAbsent(k, (kk) -> pipeline(name, mode, true, depthTest, false, additive, false));
            return RenderTypeAccess.arcanicvisuals$create(
                    "arcanicvisuals:" + name,
                    RenderSetup.builder(pipe)
                            .withTexture("Sampler0", tex, () -> RenderSystem.getSamplerCache()
                                    .getClampToEdge(FilterMode.LINEAR))
                            .sortOnUpload()
                            .bufferSize(1536)
                            .createRenderSetup());
        });
    }

    private static RenderPipeline pipeline(
            String name,
            VertexFormat.Mode mode,
            boolean textured,
            boolean depthTest,
            boolean depthWrite,
            boolean additive,
            boolean bias) {
        String shader = textured ? "core/position_tex_color" : "core/position_color";
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/" + name))
                .withVertexShader(Identifier.withDefaultNamespace(shader))
                .withFragmentShader(Identifier.withDefaultNamespace(shader))
                .withVertexFormat(
                        textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR, mode)
                .withCull(false)
                .withBlend(additive ? BlendFunction.LIGHTNING : BlendFunction.TRANSLUCENT)
                .withDepthWrite(depthWrite)
                .withDepthTestFunction(
                        depthTest ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER);
        if (textured) {
            b.withSampler("Sampler0");
        }

        if (bias) {
            b.withDepthBias(-1.0F, -1.0F);
        }

        return b.build();
    }

    public static void draw(RenderType type, BufferBuilder buf) {
        if (buf != null && type != null) {
            MeshData mesh = buf.build();
            if (mesh != null) {
                type.draw(mesh);
            }
        }
    }

    public static long studioNow() {
        return Studio.clockMs();
    }

    public static long now() {
        return studio ? studioNow() : System.currentTimeMillis();
    }

    public static Color clientColour() {
        return new Color(Theme.accent() | 0xFF000000, true);
    }

    private static SoundEvent sound(String id) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("arcanicvisuals", id));
    }

    public static void studioSound(SoundEvent sound, float volume, float pitch) {
        if (sound != null && !(volume <= 0.0F)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                Vec3 at = mc.player.position();
                mc.level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.MASTER, volume, pitch, false);
            } else {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        }
    }

    public static void shake(float strength) {
        if (!(strength <= 0.0F)) {
            if (studio) {
                studioShake(strength);
            } else {
                shake = Math.max(shake, strength);
                shakeAt = System.currentTimeMillis();
            }
        }
    }

    public static float[] shakeOffset() {
        if (shake <= 0.0F) {
            return null;
        } else {
            long t = System.currentTimeMillis() - shakeAt;
            if (t >= 700L) {
                shake = 0.0F;
                return null;
            } else {
                float k = (float) t / 700.0F;
                float s = shake * (1.0F - k) * (1.0F - k);
                float w = (float) t * 0.055F;
                return new float[] {
                    (float) Math.sin((double) w * 1.3 + Math.sin((double) w * 2.1)) * s,
                    (float) Math.sin((double) w * 1.7 + 1.23) * s
                };
            }
        }
    }

    public static void studioShake(float strength) {
        if (!(strength <= 0.0F)) {
            studioShake = Math.max(studioShake, strength);
            studioShakeAt = studioNow();
        }
    }

    public static float[] studioShakeOffset() {
        if (studioShake <= 0.0F) {
            return null;
        } else {
            long t = studioNow() - studioShakeAt;
            if (t >= 0L && t < 650L) {
                float k = (float) t / 650.0F;
                float s = studioShake * (1.0F - k) * (1.0F - k);
                float w = (float) t * 0.06F;
                return new float[] {
                    (float) Math.sin((double) w * 1.4 + Math.sin((double) w * 2.3)) * s,
                    (float) Math.sin((double) w * 1.9 + 0.8) * s
                };
            } else {
                studioShake = 0.0F;
                return null;
            }
        }
    }

    static {
        LINES = RenderTypeAccess.arcanicvisuals$create(
                "arcanicvisuals:impact_lines_translucent",
                RenderSetup.builder(RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$linesSnippet())
                                .withLocation(
                                        Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/impact_lines"))
                                .withCull(false)
                                .withBlend(BlendFunction.TRANSLUCENT)
                                .withDepthWrite(false)
                                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                                .build())
                        .sortOnUpload()
                        .bufferSize(1536)
                        .createRenderSetup());
        RAILGUN_FIRE = sound("impact.railgun_equip");
        RAILGUN_CHARGE = sound("impact.railgun_scope_on");
        PROJECTION = new Matrix4f();
    }
}
