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

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.arcanic.visuals.mixin.RenderTypeAccess;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class ArcEffects {
    private static final List<Jump> JUMPS = new ArrayList<>();
    private static final List<Jump> STUDIO_JUMPS = new ArrayList<>();
    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
    private static final RenderPipeline TEXTURED;

    private static Identifier texture(String folder, String key) {
        return Identifier.fromNamespaceAndPath("arcanic", "textures/" + folder + "/" + key + ".png");
    }

    private static RenderType type(Identifier id) {
        return (RenderType) TYPES.computeIfAbsent(id, (t) -> {
            Fx.irisAssign(TEXTURED);
            return RenderTypeAccess.arcanicvisuals$create(
                    "arcanicvisuals_arc_effect",
                    RenderSetup.builder(TEXTURED)
                            .withTexture("Sampler0", t, () -> RenderSystem.getSamplerCache()
                                    .getClampToEdge(FilterMode.LINEAR))
                            .sortOnUpload()
                            .createRenderSetup());
        });
    }

    private static int colour(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * (float) (c >>> 24)) << 24 | c & 0xFFFFFF;
    }

    public static boolean jump(String style, Vec3 at, int c, float radius, float life, boolean spin) {
        String[] var10000;
        switch (style) {
            case "Arc Storm" -> var10000 = new String[] {"shtorm1", "shtorm2", "glow"};
            case "Arc Pool" -> var10000 = new String[] {"glow", "omut2", "omut3"};
            case "Arc Rings" -> var10000 = new String[] {"colso1", "omut2", "colso3"};
            case "Arc Explosion" -> var10000 = new String[] {"boom1", "boom2", "glow"};
            default -> var10000 = null;
        }

        String[] layers = var10000;
        if (layers == null) {
            return false;
        } else {
            List<Jump> to = Fx.studio ? STUDIO_JUMPS : JUMPS;
            if (to.size() < 64) {
                to.add(new Jump(at, c, radius, life, spin, System.nanoTime(), layers));
            }

            return true;
        }
    }

    public static void world(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        draw(
                JUMPS,
                ctx.matrices().last(),
                ctx.consumers(),
                mc.gameRenderer.getMainCamera().position());
    }

    public static void renderStudio(PoseStack.Pose pose, MultiBufferSource buffers, Vec3 cam) {
        draw(STUDIO_JUMPS, pose, buffers, cam);
    }

    public static void clearStudio() {
        STUDIO_JUMPS.clear();
    }

    private static void draw(List<Jump> jumps, PoseStack.Pose pose, MultiBufferSource buffers, Vec3 cam) {
        long now = System.nanoTime();
        jumps.removeIf((jx) -> (double) (now - jx.born) / (double) 1.0E9F >= (double) jx.life);

        for (Jump j : jumps) {
            float age = (float) (now - j.born) / 1.0E9F;
            float t = age / j.life;
            float alpha = t < 0.55F ? 1.0F : 1.0F - (t - 0.55F) / 0.45F;
            float radius = 0.15F + (j.radius - 0.15F) * (1.0F - (float) Math.pow(2.0, (double) (-10.0F * t)));
            float angle = j.spin ? age * ((float) Math.PI * 2F) : 0.0F;
            float c = (float) Math.cos((double) angle);
            float s = (float) Math.sin((double) angle);
            int layerIndex = 0;

            for (String layer : j.layers) {
                VertexConsumer vc = buffers.getBuffer(type(texture("jumpcircles", layer)));
                float x = (float) (j.at.x - cam.x);
                float y = (float) (j.at.y + 0.04 - cam.y);
                float z = (float) (j.at.z - cam.z);
                float layerAlpha = layer.equals("glow") ? 0.25F : 0.82F;
                flat(
                        vc,
                        pose,
                        x,
                        y + (float) (layerIndex++) * 0.002F,
                        z,
                        radius,
                        c,
                        s,
                        colour(j.colour, alpha * layerAlpha));
            }
        }
    }

    private static void flat(
            VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float r, float c, float s, int colour) {
        Fx.quad(vc, p, x, y, z, r * c, 0.0F, r * s, -r * s, 0.0F, r * c, colour);
    }

    static {
        TEXTURED = RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$entitySnippet())
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/arc_effect"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/arc_effect"))
                .withShaderDefine("EMISSIVE")
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withSampler("Sampler0")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .withCull(false)
                .build();
    }

    private static record Jump(
            Vec3 at, int colour, float radius, float life, boolean spin, long born, String[] layers) {}
}
