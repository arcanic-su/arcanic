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
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.hud.TargetHud;
import net.arcanic.visuals.mixin.GuiGraphicsAccess;
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.arcanic.visuals.mixin.RenderTypeAccess;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Target ESP: three spinning discs (Vortex, Brackets), bead rings (Garland), comets (Orbit), a pair of crescents
 * facing the camera (Circle) and a screen-space marker over the head (Marker).
 */
public final class TargetEsp {
    private TargetEsp() {}

    private static final BlendFunction GLOW = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE);
    private static final Identifier VORTEX = texture("vortex"),
            BRACKETS = texture("brackets"),
            MARKER = texture("marker");
    private static final RenderPipeline TRANSLUCENT = pipeline("target_esp", BlendFunction.TRANSLUCENT, false);
    private static final RenderPipeline ADDITIVE = pipeline("target_esp_glow", GLOW, false);
    private static final RenderPipeline ADDITIVE_TOP = pipeline("target_esp_glow_top", GLOW, true);
    private static final RenderPipeline HUD = hudPipeline("target_esp_hud", BlendFunction.TRANSLUCENT);
    private static final RenderPipeline HUD_GLOW = hudPipeline("target_esp_hud_glow", GLOW);
    private static final Map<String, RenderType> TYPES = new HashMap<>();

    private static final int SEGMENTS = 48, RING_COLOURS = 24;
    private static final float[] CRESCENT_COS = new float[SEGMENTS + 1],
            CRESCENT_SIN = new float[SEGMENTS + 1],
            CRESCENT_TAPER = new float[SEGMENTS + 1],
            CRESCENT_DEG = new float[SEGMENTS + 1];

    static {
        // each crescent covers a bit more than half a turn and tapers to points at both ends
        double arc = 3.5255651, start = -(arc - Math.PI) * .5;
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS, angle = start + arc * t;
            CRESCENT_COS[i] = (float) Math.cos(angle);
            CRESCENT_SIN[i] = (float) Math.sin(angle);
            CRESCENT_TAPER[i] = (float) Math.sin(Math.PI * t);
            CRESCENT_DEG[i] = (float) Math.toDegrees(angle);
        }
    }

    private static final long START = System.nanoTime();
    private static final Random RANDOM = new Random();
    private static final float[] spinScale = {1, 1, 1}, wobbleSpeed = {1, 1, 1}, wobblePhase = new float[3];
    private static final float[] offset = {-30, 0, 30};
    private static final float[] pulse = new float[3], ringYaw = new float[3], ringPitch = new float[3];
    private static final float[] yawCos = new float[3],
            yawSin = new float[3],
            pitchCos = new float[3],
            pitchSin = new float[3];
    private static final int[] ringColours = new int[RING_COLOURS];
    private static LivingEntity last;
    private static float circleSpin;
    private static long lastSpin;
    private static boolean markerPlaced;
    private static double markerX, markerY;

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("arcanic", "textures/targetesp/" + name + ".png");
    }

    private static RenderPipeline pipeline(String name, BlendFunction blend, boolean throughWalls) {
        RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$entitySnippet())
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/" + name))
                .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/arc_effect"))
                .withShaderDefine("EMISSIVE")
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withSampler("Sampler0")
                .withBlend(blend)
                .withDepthWrite(false)
                .withCull(false);
        if (throughWalls) b = b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        return b.build();
    }

    private static RenderPipeline hudPipeline(String name, BlendFunction blend) {
        return RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$guiTextureSnippet())
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/" + name))
                .withBlend(blend)
                .withCull(false)
                .build();
    }

    private static RenderType type(RenderPipeline pipeline, Identifier texture) {
        return TYPES.computeIfAbsent(pipeline.getLocation() + "|" + texture, k -> {
            Fx.irisAssign(pipeline);
            return RenderTypeAccess.arcanicvisuals$create(
                    "arcanicvisuals_target_esp",
                    RenderSetup.builder(pipeline)
                            .withTexture("Sampler0", texture, () -> RenderSystem.getSamplerCache()
                                    .getClampToEdge(FilterMode.LINEAR))
                            .sortOnUpload()
                            .createRenderSetup());
        });
    }

    private static float seconds() {
        return (System.nanoTime() - START) / 1e9f;
    }

    private static LivingEntity target() {
        LivingEntity t = TargetHud.current();
        if (t == null || t == Minecraft.getInstance().player || TargetHud.presence() <= .01f) return null;
        if (t != last) {
            last = t;
            randomizeRings();
            markerPlaced = false;
        }
        return t;
    }

    public static void world(WorldRenderContext ctx, Vec3 cam, Vector3f left, Vector3f up, float partialTick) {
        var m = Modules.MARKER;
        if (m.style.is("Маркер")) return;
        LivingEntity t = target();
        if (t == null) return;
        float fade = TargetHud.presence();
        AABB box = t.getBoundingBox();
        double ix = t.xo + (t.getX() - t.xo) * partialTick - t.getX(),
                iy = t.yo + (t.getY() - t.yo) * partialTick - t.getY(),
                iz = t.zo + (t.getZ() - t.zo) * partialTick - t.getZ();
        float dx = (float) ((box.minX + box.maxX) * .5 + ix - cam.x),
                dy = (float) (box.minY + iy - cam.y),
                dz = (float) ((box.minZ + box.maxZ) * .5 + iz - cam.z);
        float height = (float) box.getYsize(), width = (float) Math.max(box.getXsize(), box.getZsize());
        Matrix4f pose = ctx.matrices().last().pose();
        float speed = m.speed.f(), time = seconds();
        if (m.style.is("Круг")) {
            circle(ctx, pose, t, dx, dy, dz, width, height, fade, left, up);
            return;
        }
        updateRings(time, speed);
        float base = width * 5 * m.size.f(), lift = height * .62f - .006f;
        int colour = m.colour(t, 90, fade);
        switch (m.style.get()) {
            case "Вихрь" -> discs(ctx, pose, VORTEX, dx, dy + lift, dz, base * .5f, colour);
            case "Скобки" -> discs(ctx, pose, BRACKETS, dx, dy + lift, dz, base * .5f, colour);
            case "Гирлянда" -> garland(ctx, pose, left, up, dx, dy + lift, dz, base, colour, time);
            case "Орбита" -> orbit(ctx, pose, left, up, dx, dy + height * .5f, dz, width, colour, time, speed);
            default -> {}
        }
    }

    private static void randomizeRings() {
        for (int i = 0; i < 3; i++) {
            spinScale[i] = .75f + RANDOM.nextFloat() * .5f;
            wobbleSpeed[i] = .8f + RANDOM.nextFloat() * .8f;
            wobblePhase[i] = RANDOM.nextFloat() * (float) Math.PI * 2;
        }
        // three yaw offsets in [-30, 30], kept at least 22 degrees apart so the rings never stack
        for (int attempt = 0; attempt < 64; attempt++) {
            for (int i = 0; i < 3; i++) offset[i] = -30 + RANDOM.nextFloat() * 60;
            if (Math.abs(offset[0] - offset[1]) >= 22
                    && Math.abs(offset[0] - offset[2]) >= 22
                    && Math.abs(offset[1] - offset[2]) >= 22) return;
        }
        offset[0] = -30 + RANDOM.nextFloat() * 4;
        offset[1] = -2 + RANDOM.nextFloat() * 4;
        offset[2] = 30 - RANDOM.nextFloat() * 4;
    }

    private static void updateRings(float time, float speed) {
        for (int i = 0; i < 3; i++) {
            float wave = ((float) Math.sin(time * speed * 3.4f + i * Math.PI * 2 / 3) + 1) / 2;
            pulse[i] = .35f + wave * .65f;
            float yaw = -time * 140 * speed * spinScale[i] + offset[i];
            ringPitch[i] = (float) Math.sin(time * wobbleSpeed[i] + wobblePhase[i]) * 30;
            ringYaw[i] = (float) Math.toRadians(90 + yaw);
            double y = ringYaw[i], p = Math.toRadians(ringPitch[i]);
            yawCos[i] = (float) Math.cos(y);
            yawSin[i] = (float) Math.sin(y);
            pitchCos[i] = (float) Math.cos(p);
            pitchSin[i] = (float) Math.sin(p);
        }
    }

    private static void discs(
            WorldRenderContext ctx, Matrix4f pose, Identifier texture, float x, float y, float z, float half, int c) {
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = ctx.consumers().getBuffer(type(pass == 0 ? TRANSLUCENT : ADDITIVE, texture));
            float passAlpha = pass == 0 ? 1 : .85f;
            for (int i = 0; i < 3; i++) {
                Matrix4f m = new Matrix4f(pose)
                        .translate(x, y + .006f * i, z)
                        .rotateY(ringYaw[i])
                        .rotateX((float) Math.toRadians(ringPitch[i]));
                int col = alpha(c, pulse[i] * passAlpha);
                vertex(vc, m, -half, 0, -half, 0, 0, col);
                vertex(vc, m, -half, 0, half, 0, 1, col);
                vertex(vc, m, half, 0, half, 1, 1, col);
                vertex(vc, m, half, 0, -half, 1, 0, col);
            }
        }
    }

    private static void garland(
            WorldRenderContext ctx,
            Matrix4f pose,
            Vector3f left,
            Vector3f up,
            float x,
            float y,
            float z,
            float base,
            int c,
            float time) {
        VertexConsumer vc = ctx.consumers().getBuffer(type(ADDITIVE, Fx.Tex.WHITE.id));
        float radius = base * .34f, beadRadius = Math.max(.045f, Math.min(.085f, base * .025f));
        float[][] beads = new float[3 * 24][];
        for (int i = 0; i < 3; i++)
            for (int k = 0; k < 24; k++) {
                double angle = Math.PI * 2 * k / 24;
                float px = (float) Math.cos(angle) * radius, py = (float) Math.sin(angle) * radius;
                float twinkle = ((float) Math.sin(time * 4 + k * .9f + i) + 1) / 2;
                float ox = px * yawCos[i] + py * pitchCos[i] * yawSin[i];
                float oy = -py * pitchSin[i];
                float oz = -px * yawSin[i] + py * pitchCos[i] * yawCos[i];
                beads[i * 24 + k] = new float[] {
                    x + ox, y + .006f * i + oy, z + oz, beadRadius * (.9f + twinkle * .28f), twinkle, pulse[i]
                };
            }
        // all halos first, so no bead is hidden behind the glow of its neighbour
        for (float[] b : beads) {
            float r = b[3], tw = b[4], p = b[5];
            glow(vc, pose, left, up, b[0], b[1], b[2], r * (3.6f + tw * .7f), alpha(c, (.105f + tw * .035f) * p));
            glow(vc, pose, left, up, b[0], b[1], b[2], r * (1.9f + tw * .3f), alpha(c, (.18f + tw * .055f) * p));
        }
        for (float[] b : beads) bead(vc, pose, b[0], b[1], b[2], b[3], alpha(c, (.78f + b[4] * .22f) * b[5]));
    }

    private static void orbit(
            WorldRenderContext ctx,
            Matrix4f pose,
            Vector3f left,
            Vector3f up,
            float x,
            float y,
            float z,
            float width,
            int c,
            float time,
            float speed) {
        VertexConsumer vc = ctx.consumers().getBuffer(type(ADDITIVE, Fx.Tex.WHITE.id));
        float size = Modules.MARKER.size.f();
        float radius = (width * .5f + .55f) * size;
        float ball = Math.max(.09f, Math.min(.24f, width * .16f * size));
        for (int i = 0; i < 3; i++) {
            double yaw = Math.toRadians(offset[i] * 3), pitch = Math.toRadians(35 + ringPitch[i] * .5f);
            float yc = (float) Math.cos(yaw), ys = (float) Math.sin(yaw);
            float pc = (float) Math.cos(pitch), ps = (float) Math.sin(pitch);
            float spin = time * 150 * speed * spinScale[i] + offset[i] * 6;
            for (int k = 0; k < 20; k++) {
                float fade = 1 - k / 20f;
                double angle = Math.toRadians(spin - k * 7);
                float a = (float) Math.cos(angle) * radius, b = (float) Math.sin(angle) * radius;
                float ox = a * yc + b * pc * ys, oy = -b * ps, oz = -a * ys + b * pc * yc;
                float r = ball * fade * (k == 0 ? 1.8f : 1);
                glow(
                        vc,
                        pose,
                        left,
                        up,
                        x + ox,
                        y + oy,
                        z + oz,
                        r,
                        alpha(c, fade * fade * (k == 0 ? .85f : .32f) * pulse[i]));
            }
        }
    }

    private static void circle(
            WorldRenderContext ctx,
            Matrix4f pose,
            LivingEntity t,
            float dx,
            float dy,
            float dz,
            float width,
            float height,
            float fade,
            Vector3f left,
            Vector3f up) {
        var m = Modules.MARKER;
        float radius = Math.max(.35f, (width * .5f + .3f) * m.circleRadius.f());
        float thick = radius * ((m.thickness.f() - 1) / 4 * .6f);
        float cy = dy + height * .5f;
        float distance = (float) Math.sqrt(dx * dx + cy * cy + dz * dz);
        // fades out as the camera comes inside the ring
        float a = fade * Math.max(0, Math.min(1, (distance - radius) / Math.max(radius, .01f)));
        advanceSpin(m.speed.f());
        if (a <= .02f) return;
        for (int i = 0; i < RING_COLOURS; i++) ringColours[i] = mixWhite(m.colour(t, Math.round(i * 15f), a));
        VertexConsumer vc =
                ctx.consumers().getBuffer(type(m.throughWalls.on() ? ADDITIVE_TOP : ADDITIVE, Fx.Tex.WHITE.id));
        // a billboard: x along the camera's left, y along its up
        Matrix4f mat = new Matrix4f(pose).translate(dx, cy, dz).mul(new Matrix4f().set(new float[] {
                    left.x, left.y, left.z, 0, up.x, up.y, up.z, 0, 0, 0, 1, 0, 0, 0, 0, 1
                }));
        float tilt = (float) Math.cos(Math.toRadians(m.tilt.get()));
        float glow = m.glow.f();
        if (glow > 0)
            for (int g = 3; g >= 1; g--) crescents(vc, mat, radius, thick * (1 + g * .35f * glow), tilt, .13f / g);
        crescents(vc, mat, radius, thick, tilt, 1);
    }

    private static int mixWhite(int c) {
        return (c & 0xFF000000) | (Fx.lerp(c | 0xFF000000, 0xFFFFFFFF, .2f) & 0xFFFFFF);
    }

    private static void advanceSpin(float speed) {
        long now = System.nanoTime();
        float dt = lastSpin == 0 ? 0 : Math.max(0, Math.min(.1f, (now - lastSpin) / 1e9f));
        lastSpin = now;
        circleSpin = (float) ((circleSpin + 2 * speed * dt) % (Math.PI * 2));
    }

    private static void crescents(VertexConsumer vc, Matrix4f m, float radius, float thick, float tilt, float alpha) {
        float c = (float) Math.cos(circleSpin), s = (float) Math.sin(circleSpin);
        for (int crescent = 0; crescent < 2; crescent++) {
            float sign = crescent == 0 ? 1 : -1;
            for (int k = 0; k < SEGMENTS; k++) {
                float w0 = thick * CRESCENT_TAPER[k] * .5f, w1 = thick * CRESCENT_TAPER[k + 1] * .5f;
                if (w0 <= 0 && w1 <= 0) continue;
                float x0 = CRESCENT_COS[k] * sign, y0 = CRESCENT_SIN[k] * sign;
                float x1 = CRESCENT_COS[k + 1] * sign, y1 = CRESCENT_SIN[k + 1] * sign;
                float rx0 = x0 * c - y0 * s, ry0 = x0 * s + y0 * c;
                float rx1 = x1 * c - y1 * s, ry1 = x1 * s + y1 * c;
                int index = Math.floorMod(
                        Math.round((CRESCENT_DEG[k] + crescent * 180) / 360 * RING_COLOURS), RING_COLOURS);
                int col = alpha < 1 ? alpha(ringColours[index], alpha) : ringColours[index];
                ringVertex(vc, m, rx0, ry0, radius - w0, tilt, col);
                ringVertex(vc, m, rx0, ry0, radius + w0, tilt, col);
                ringVertex(vc, m, rx1, ry1, radius + w1, tilt, col);
                ringVertex(vc, m, rx1, ry1, radius - w1, tilt, col);
            }
        }
    }

    private static void ringVertex(VertexConsumer vc, Matrix4f m, float x, float y, float r, float tilt, int c) {
        vertex(vc, m, x * r * tilt, y * r, 0, .5f, .5f, c);
    }

    private static void glow(
            VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, float x, float y, float z, float r, int c) {
        int rim = c & 0xFFFFFF;
        for (int i = 0; i < 36; i++) {
            double a0 = Math.PI * 2 * i / 36, a1 = Math.PI * 2 * (i + 1) / 36;
            float c0 = (float) Math.cos(a0) * r, s0 = (float) Math.sin(a0) * r;
            float c1 = (float) Math.cos(a1) * r, s1 = (float) Math.sin(a1) * r;
            float x1 = x + left.x * c1 + up.x * s1, y1 = y + left.y * c1 + up.y * s1, z1 = z + left.z * c1 + up.z * s1;
            vertex(vc, m, x, y, z, .5f, .5f, c);
            vertex(
                    vc,
                    m,
                    x + left.x * c0 + up.x * s0,
                    y + left.y * c0 + up.y * s0,
                    z + left.z * c0 + up.z * s0,
                    .5f,
                    .5f,
                    rim);
            vertex(vc, m, x1, y1, z1, .5f, .5f, rim);
            vertex(vc, m, x1, y1, z1, .5f, .5f, rim);
        }
    }

    private static void bead(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, int c) {
        for (int stack = 0; stack < 8; stack++) {
            double lat0 = Math.PI * stack / 8 - Math.PI / 2, lat1 = lat0 + Math.PI / 8;
            float y0 = (float) Math.sin(lat0) * r, r0 = (float) Math.cos(lat0) * r;
            float y1 = (float) Math.sin(lat1) * r, r1 = (float) Math.cos(lat1) * r;
            for (int slice = 0; slice < 12; slice++) {
                double lon0 = Math.PI * 2 * slice / 12, lon1 = Math.PI * 2 * (slice + 1) / 12;
                float c0 = (float) Math.cos(lon0), s0 = (float) Math.sin(lon0);
                float c1 = (float) Math.cos(lon1), s1 = (float) Math.sin(lon1);
                vertex(vc, m, x + c0 * r0, y + y0, z + s0 * r0, .5f, .5f, c);
                vertex(vc, m, x + c0 * r1, y + y1, z + s0 * r1, .5f, .5f, c);
                vertex(vc, m, x + c1 * r1, y + y1, z + s1 * r1, .5f, .5f, c);
                vertex(vc, m, x + c1 * r0, y + y0, z + s1 * r0, .5f, .5f, c);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int c) {
        vc.addVertex(m, x, y, z)
                .setUv(u, v)
                .setColor(c)
                .setOverlay(0)
                .setLight(0xF000F0)
                .setNormal(0, 1, 0);
    }

    static int alpha(int c, float a) {
        return (Math.round(Math.max(0, Math.min(1, a)) * (c >>> 24)) << 24) | (c & 0xFFFFFF);
    }

    /** The Marker style: a spinning, breathing sprite over the target's head; drawn with the HUD in GUI units. */
    public static void hud(GuiGraphics g) {
        var m = Modules.MARKER;
        if (!m.enabled() || !m.style.is("Маркер")) return;
        LivingEntity t = target();
        if (t == null) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 at = t.getPosition(pt).add(0, (t.getEyeHeight() + .4) * .5, 0);
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double[] p = Projection.toScreen(at, w, h);
        if (p == null) return;
        if (!markerPlaced) {
            markerX = p[0];
            markerY = p[1];
            markerPlaced = true;
        } else {
            markerX += (p[0] - markerX) * .5;
            markerY += (p[1] - markerY) * .5;
        }
        float fade = TargetHud.presence();
        double time = (System.nanoTime() - START) / 1e6 * m.speed.get();
        float breathe = (float) (Math.sin(time / 500) + 1) / 2;
        float spin = (float) (Math.sin(time / 1000) + 1) / 2 * 360;
        float tilt = Math.max(0, Math.min(90, breathe * 90));
        float scale = Math.max(.9f, Math.min(1.1f, breathe * 1.1f));
        float size = m.markerSize.f() * scale;
        float x = (float) markerX, y = (float) markerY;
        Matrix3x2f pose = new Matrix3x2f(g.pose())
                .translate(x, y)
                .rotate((float) Math.toRadians(90 - tilt + spin))
                .translate(-x, -y);
        var texture = mc.getTextureManager().getTexture(MARKER);
        float half = size / 2;
        var bounds = new ScreenRectangle((int) (x - size), (int) (y - size), (int) (size * 2), (int) (size * 2));
        ((GuiGraphicsAccess) g)
                .arcanicvisuals$state()
                .submitGuiElement(new MarkerState(
                        pose,
                        x - half,
                        y - half,
                        size,
                        new int[] {
                            m.colour(t, 0, fade), m.colour(t, 270, fade), m.colour(t, 180, fade), m.colour(t, 90, fade)
                        },
                        m.markerGlow.on() ? HUD_GLOW : HUD,
                        TextureSetup.singleTexture(
                                texture.getTextureView(),
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                        bounds));
    }

    /** Corner colours run top-left, bottom-left, bottom-right, top-right. */
    private record MarkerState(
            Matrix3x2f pose,
            float x,
            float y,
            float size,
            int[] corners,
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            ScreenRectangle bounds)
            implements GuiElementRenderState {
        public ScreenRectangle scissorArea() {
            return null;
        }

        public void buildVertices(VertexConsumer v) {
            v.addVertexWith2DPose(pose, x, y).setUv(0, 0).setColor(corners[0]);
            v.addVertexWith2DPose(pose, x, y + size).setUv(0, 1).setColor(corners[1]);
            v.addVertexWith2DPose(pose, x + size, y + size).setUv(1, 1).setColor(corners[2]);
            v.addVertexWith2DPose(pose, x + size, y).setUv(1, 0).setColor(corners[3]);
        }
    }
}
