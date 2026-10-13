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
import java.util.*;
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.arcanic.visuals.mixin.RenderTypeAccess;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Texture jump circles. */
public final class ArcEffects {
    private record Jump(Vec3 at, int colour, float radius, float life, boolean spin, long born, String[] layers) {}

    private static final List<Jump> JUMPS = new ArrayList<>();
    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();

    private static Identifier texture(String folder, String key) {
        return Identifier.fromNamespaceAndPath("arcanic", "textures/" + folder + "/" + key + ".png");
    }

    private static final RenderPipeline TEXTURED = RenderPipeline.builder(
                    RenderPipelinesAccess.arcanicvisuals$entitySnippet())
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

    private static RenderType type(Identifier id) {
        return TYPES.computeIfAbsent(id, t -> {
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
    // Straight alpha includes texture coverage. ONE/ONE ignored transparent white pixels in the source vortex.
    private static int colour(int c, float a) {
        return (Math.round(Math.max(0, Math.min(1, a)) * (c >>> 24)) << 24) | (c & 0xFFFFFF);
    }

    public static boolean jump(String style, Vec3 at, int c, float radius, float life, boolean spin) {
        String[] layers =
                switch (style) {
                    case "Arc Storm" -> new String[] {"shtorm1", "shtorm2", "glow"};
                    case "Arc Pool" -> new String[] {"glow", "omut2", "omut3"};
                    case "Arc Rings" -> new String[] {"colso1", "omut2", "colso3"};
                    case "Arc Explosion" -> new String[] {"boom1", "boom2", "glow"};
                    default -> null;
                };
        if (layers == null) return false;
        if (JUMPS.size() < 64) JUMPS.add(new Jump(at, c, radius, life, spin, System.nanoTime(), layers));
        return true;
    }

    public static void world(WorldRenderContext ctx) {
        var mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera().position();
        long now = System.nanoTime();
        JUMPS.removeIf(j -> (now - j.born) / 1e9 >= j.life);
        for (Jump j : JUMPS) {
            float age = (now - j.born) / 1e9f, t = age / j.life;
            // EXPO_OUT size curve, hold opacity to 55%, then fade over the final 45%.
            float alpha = t < .55f ? 1 : 1 - (t - .55f) / .45f;
            float radius = .15f + (j.radius - .15f) * (1 - (float) Math.pow(2, -10 * t));
            float angle = j.spin ? age * (float) (Math.PI * 2) : 0;
            float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
            int layerIndex = 0;
            for (String layer : j.layers) {
                var vc = ctx.consumers().getBuffer(type(texture("jumpcircles", layer)));
                float x = (float) (j.at.x - cam.x), y = (float) (j.at.y + .04 - cam.y), z = (float) (j.at.z - cam.z);
                float layerAlpha = layer.equals("glow") ? .25f : .82f;
                flat(
                        vc,
                        ctx.matrices().last(),
                        x,
                        y + layerIndex++ * .002f,
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
        Fx.quad(vc, p, x, y, z, r * c, 0, r * s, -r * s, 0, r * c, colour);
    }
}
