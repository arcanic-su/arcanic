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
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.arcanic.visuals.mixin.RenderTypeAccess;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class TargetEsp {
    private static final BlendFunction GLOW;
    private static final RenderPipeline TRANSLUCENT;
    private static final RenderPipeline TRANSLUCENT_TOP;
    private static final RenderPipeline ADDITIVE;
    private static final RenderPipeline ADDITIVE_TOP;
    private static final Map<String, RenderType> TYPES;
    private static final Identifier ARCS;
    private static final Identifier TREFOIL;
    private static final Identifier DIAMONDS;
    private static final Identifier SIGHT;
    private static final Identifier TRIANGLE;
    private static final Identifier HALO;
    private static final Identifier LIGHT;
    private static final float TAU = ((float) Math.PI * 2F);
    private static final long START;
    private static LivingEntity last;
    private static float clock;
    private static float acquired;
    private static float step;
    private static float owed;
    private static long lastFrame;
    private static int lastHurt;
    private static final Random RANDOM;
    private static final float[] discSpin;
    private static final float[] discRock;
    private static final float[] discPhase;
    private static final float[] discLean;
    private static float markerTurn;
    private static float markerSpin;
    private static boolean markerBack;
    private static boolean sight;
    private static long sightAt;
    private static LivingEntity sightOf;
    private static float studioClock;
    private static float studioOwed;
    private static final float[][] ORBITS;
    private static final int TAIL = 16;

    private TargetEsp() {}

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
        if (throughWalls) {
            b = b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        }

        return b.build();
    }

    private static RenderType type(RenderPipeline pipeline, Identifier texture) {
        return (RenderType) TYPES.computeIfAbsent(pipeline.getLocation() + "|" + texture, (k) -> {
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

    private static VertexConsumer shape(MultiBufferSource buffers, Identifier tex, boolean top) {
        return buffers.getBuffer(type(top ? TRANSLUCENT_TOP : TRANSLUCENT, tex));
    }

    private static VertexConsumer glow(MultiBufferSource buffers, Identifier tex, boolean top) {
        return buffers.getBuffer(type(top ? ADDITIVE_TOP : ADDITIVE, tex));
    }

    private static boolean inSight(LivingEntity t) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) {
            return false;
        } else {
            long now = System.currentTimeMillis();
            if (t != sightOf || now - sightAt >= 100L) {
                sight = me.hasLineOfSight(t);
                sightAt = now;
                sightOf = t;
            }

            return sight;
        }
    }

    private static float seconds() {
        return (float) (System.nanoTime() - START) / 1.0E9F;
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static float smooth(float t) {
        t = clamp(t);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float backOut(float t) {
        t = clamp(t) - 1.0F;
        return 1.0F + t * t * (1.7F * t + 0.7F);
    }

    private static float sin(double a) {
        return (float) Math.sin(a);
    }

    private static float cos(double a) {
        return (float) Math.cos(a);
    }

    private static LivingEntity target() {
        LivingEntity t = TargetHud.current();
        if (t != null && t != Minecraft.getInstance().player && !(TargetHud.presence() <= 0.01F)) {
            if (t != last) {
                last = t;
                acquired = seconds();
                lastHurt = t.hurtTime;
                roll();
            }

            return t;
        } else {
            return null;
        }
    }

    private static void roll() {
        for (int j = 0; j < 3; j++) {
            discSpin[j] = 0.75F + RANDOM.nextFloat() * 0.5F;
            discRock[j] = 0.8F + RANDOM.nextFloat() * 0.8F;
            discPhase[j] = RANDOM.nextFloat() * ((float) Math.PI * 2F);
        }

        for (int tries = 0; tries < 64; tries++) {
            for (int j = 0; j < 3; j++) {
                discLean[j] = -30.0F + RANDOM.nextFloat() * 60.0F;
            }

            if (Math.abs(discLean[0] - discLean[1]) >= 22.0F
                    && Math.abs(discLean[0] - discLean[2]) >= 22.0F
                    && Math.abs(discLean[1] - discLean[2]) >= 22.0F) {
                return;
            }
        }

        discLean[0] = -30.0F + RANDOM.nextFloat() * 4.0F;
        discLean[1] = -2.0F + RANDOM.nextFloat() * 4.0F;
        discLean[2] = 30.0F - RANDOM.nextFloat() * 4.0F;
    }

    private static void advance(float speed) {
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0.0F : Math.max(0.0F, Math.min(0.1F, (float) (now - lastFrame) / 1.0E9F));
        lastFrame = now;
        step = Minecraft.getInstance().isPaused() ? 0.0F : dt;
        clock = (clock + dt * speed) % 3600.0F;
        swing(step, speed);
    }

    private static void swing(float dt, float speed) {
        float ticks = dt * 20.0F;
        if (markerSpin > 25.0F || markerSpin < -25.0F) {
            markerBack = markerSpin > 25.0F;
        }

        markerSpin += (markerBack ? -0.5F : 0.5F) * ticks;
        markerTurn = (markerTurn + markerSpin * ticks * speed) % 360.0F;
    }

    public static void world(WorldRenderContext ctx, Vec3 cam, Vector3f left, Vector3f up, float partialTick) {
        CombatFx.TargetMarker m = Modules.MARKER;
        advance(m.speed.f());
        LivingEntity t = target();
        if (t != null) {
            float since = seconds() - acquired;
            float fade = TargetHud.presence() * smooth(since / 0.3F);
            if (!(fade <= 0.01F)) {
                AABB box = t.getBoundingBox();
                double ix = t.xo + (t.getX() - t.xo) * (double) partialTick - t.getX();
                double iy = t.yo + (t.getY() - t.yo) * (double) partialTick - t.getY();
                double iz = t.zo + (t.getZ() - t.zo) * (double) partialTick - t.getZ();
                float hit = t.hurtTime > 0 ? (float) t.hurtTime / 10.0F : 0.0F;
                boolean struck = t.hurtTime > lastHurt;
                lastHurt = t.hurtTime;
                draw(new Mark(
                        ctx.consumers(),
                        ctx.matrices().last().pose(),
                        t,
                        left,
                        up,
                        cam,
                        (float) ((box.minX + box.maxX) * 0.5 + ix - cam.x),
                        (float) (box.minY + iy - cam.y),
                        (float) ((box.minZ + box.maxZ) * 0.5 + iz - cam.z),
                        (float) Math.max(box.getXsize(), box.getZsize()),
                        (float) box.getYsize(),
                        fade,
                        since,
                        m.size.f(),
                        hit * hit,
                        struck,
                        inSight(t)));
            }
        }
    }

    private static void draw(Mark mark) {
        switch ((String) Modules.MARKER.style.get()) {
            case "Трилистник" -> discs(mark, TREFOIL);
            case "Ромбы" -> discs(mark, DIAMONDS);
            case "Прицел" -> marker(mark, SIGHT);
            case "Треугольник" -> marker(mark, TRIANGLE);
            case "Спутники" -> satellites(mark);
            case "Ореол" -> halo(mark);
            default -> discs(mark, ARCS);
        }
    }

    public static void studio(
            MultiBufferSource buffers,
            Matrix4f pose,
            Vector3f left,
            Vector3f up,
            Vector3f forward,
            LivingEntity t,
            AABB box,
            float since,
            float hit,
            boolean struck,
            float dt) {
        CombatFx.TargetMarker m = Modules.MARKER;
        float worldClock = clock;
        float worldStep = step;
        float worldOwed = owed;
        studioClock = (studioClock + dt * m.speed.f()) % 3600.0F;
        clock = studioClock;
        step = dt;
        owed = studioOwed;

        try {
            draw(new Mark(
                    buffers,
                    pose,
                    t,
                    left,
                    up,
                    Vec3.ZERO,
                    (float) ((box.minX + box.maxX) * 0.5),
                    (float) box.minY,
                    (float) ((box.minZ + box.maxZ) * 0.5),
                    (float) Math.max(box.getXsize(), box.getZsize()),
                    (float) box.getYsize(),
                    smooth(since / 0.3F),
                    since,
                    m.size.f(),
                    hit * hit,
                    struck,
                    true));
        } finally {
            studioOwed = owed;
            clock = worldClock;
            step = worldStep;
            owed = worldOwed;
        }
    }

    private static void discs(Mark k, Identifier tex) {
        float half = k.width * 3.0F * k.size * (1.0F + 0.08F * k.hit);
        float y = k.y + k.height * 0.62F;
        Matrix4f[] at = new Matrix4f[3];
        float[] bright = new float[3];

        for (int j = 0; j < 3; j++) {
            bright[j] = 0.35F
                    + 0.65F * (sin((double) (clock * 3.4F + (float) j * ((float) Math.PI * 2F) / 3.0F)) + 1.0F) * 0.5F;
            float spin = -clock * 140.0F * discSpin[j] + discLean[j];
            float rock = sin((double) (clock * discRock[j] + discPhase[j])) * 30.0F;
            at[j] = (new Matrix4f(k.pose))
                    .translate(k.x, y + (float) j * 0.006F, k.z)
                    .rotateY((float) Math.toRadians((double) (90.0F + spin)))
                    .rotateX((float) Math.toRadians((double) rock));
        }

        VertexConsumer vc = shape(k.buffers, tex, false);

        for (int j = 0; j < 3; j++) {
            disc(vc, at[j], half, k.colour(j * 30, bright[j]));
        }

        vc = glow(k.buffers, tex, false);

        for (int j = 0; j < 3; j++) {
            disc(vc, at[j], half, k.colour(j * 30, bright[j] * (0.85F + 0.8F * k.hit)));
        }
    }

    private static void disc(VertexConsumer vc, Matrix4f m, float half, int c) {
        vertex(vc, m, -half, 0.0F, -half, 0.0F, 0.0F, c);
        vertex(vc, m, -half, 0.0F, half, 0.0F, 1.0F, c);
        vertex(vc, m, half, 0.0F, half, 1.0F, 1.0F, c);
        vertex(vc, m, half, 0.0F, -half, 1.0F, 0.0F, c);
    }

    private static void marker(Mark k, Identifier tex) {
        float half = (2.2F - k.fade) * 0.24F * k.size * (1.0F - 0.12F * k.hit);
        float cy = k.y + k.height * 0.5F;
        float angle = (float) Math.toRadians((double) markerTurn);
        int[] c = {k.colour(0, 1.0F), k.colour(0, 1.0F), k.colour(90, 1.0F), k.colour(180, 1.0F)};
        card(shape(k.buffers, tex, k.seen), k, k.x, cy, k.z, half, angle, c, 1.0F);
        card(glow(k.buffers, tex, k.seen), k, k.x, cy, k.z, half, angle, c, 0.45F + 0.8F * k.hit);
    }

    private static void card(
            VertexConsumer vc, Mark k, float x, float y, float z, float r, float angle, int[] c, float a) {
        float ca = cos((double) angle);
        float sa = sin((double) angle);
        Vector3f l = k.left;
        Vector3f u = k.up;
        float lx = (l.x * ca + u.x * sa) * r;
        float ly = (l.y * ca + u.y * sa) * r;
        float lz = (l.z * ca + u.z * sa) * r;
        float ux = (u.x * ca - l.x * sa) * r;
        float uy = (u.y * ca - l.y * sa) * r;
        float uz = (u.z * ca - l.z * sa) * r;
        vertex(vc, k.pose, x - lx + ux, y - ly + uy, z - lz + uz, 0.0F, 1.0F, alpha(c[0], a));
        vertex(vc, k.pose, x + lx + ux, y + ly + uy, z + lz + uz, 1.0F, 1.0F, alpha(c[1], a));
        vertex(vc, k.pose, x + lx - ux, y + ly - uy, z + lz - uz, 1.0F, 0.0F, alpha(c[2], a));
        vertex(vc, k.pose, x - lx - ux, y - ly - uy, z - lz - uz, 0.0F, 0.0F, alpha(c[3], a));
    }

    private static void satellites(Mark k) {
        float r = k.width * (0.9F + 0.5F * (1.0F - backOut(k.since / 0.5F))) * k.size * (1.0F + 0.25F * k.hit);
        float cy = k.y + k.height * 0.55F;
        float big = 0.2F * k.size * (1.0F + 0.5F * k.hit);
        VertexConsumer vc = glow(k.buffers, LIGHT, false);

        for (int o = 0; o < ORBITS.length; o++) {
            float[] orbit = ORBITS[o];
            Matrix4f m = (new Matrix4f())
                    .rotateY((float) Math.toRadians((double) orbit[1]) + clock * 0.2F)
                    .rotateX((float) Math.toRadians((double) orbit[0]));

            for (int s = 15; s >= 0; --s) {
                float f = 1.0F - (float) s / 16.0F;
                float a = clock * orbit[2] + (float) o * 2.1F - Math.signum(orbit[2]) * (float) s * 0.07F;
                Vector3f p = m.transformPosition(new Vector3f(cos((double) a) * r, 0.0F, sin((double) a) * r));
                sprite(
                        vc,
                        k.pose,
                        k.left,
                        k.up,
                        k.x + p.x,
                        cy + p.y,
                        k.z + p.z,
                        big * (0.25F + 0.75F * f) * 0.5F,
                        k.colour(o * 120 + s * 3, 0.75F * f * f));
                if (s == 0) {
                    sprite(
                            vc,
                            k.pose,
                            k.left,
                            k.up,
                            k.x + p.x,
                            cy + p.y,
                            k.z + p.z,
                            big * 0.22F,
                            whiter(k.colour(o * 120, 0.9F)));
                }
            }
        }
    }

    private static void halo(Mark k) {
        float settle = backOut(k.since / 0.5F);
        float r = 0.34F * k.size * (1.0F + 0.18F * k.hit) / 0.8F;
        float y = k.y + k.height + 0.3F * k.size + 0.05F * sin((double) (clock * 2.1F)) + (1.0F - settle) * 0.5F;
        Matrix4f m = (new Matrix4f(k.pose))
                .translate(k.x, y, k.z)
                .rotateY(clock * 0.6F)
                .rotateX((float) Math.toRadians((double) (8.0F * sin((double) (clock * 1.3F)))))
                .rotateZ((float) Math.toRadians((double) (8.0F * cos((double) (clock * 1.1F)))));
        disc(shape(k.buffers, HALO, false), m, r, k.colour(0, 0.9F));
        disc(glow(k.buffers, HALO, false), m, r, k.colour(90, 0.55F + 0.8F * k.hit));
        emit(4.0F, () -> {
            float a = RANDOM.nextFloat() * ((float) Math.PI * 2F);
            float rr = 0.34F * k.size;
            int c = whiter(k.colour(RANDOM.nextInt(360), 1.0F));
            Fx.spawn()
                    .at(k.world(k.x + cos((double) a) * rr, y, k.z + sin((double) a) * rr))
                    .vel(0.0, -0.25 - RANDOM.nextDouble() * 0.15, 0.0)
                    .tex(Fx.Tex.GLOW)
                    .size(0.03F * k.size, 0.0F)
                    .life(0.8F + RANDOM.nextFloat() * 0.4F)
                    .drag(0.5F)
                    .twinkle(0.5F)
                    .colour(c, c & 0xFFFFFF);
        });
    }

    private static void emit(float rate, Runnable one) {
        owed = Math.min(owed + step * rate, 8.0F);

        while (owed >= 1.0F) {
            --owed;
            one.run();
        }
    }

    private static void sprite(
            VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, float x, float y, float z, float r, int c) {
        if (c >>> 24 != 0) {
            float lx = left.x * r;
            float ly = left.y * r;
            float lz = left.z * r;
            float ux = up.x * r;
            float uy = up.y * r;
            float uz = up.z * r;
            vertex(vc, m, x - lx - ux, y - ly - uy, z - lz - uz, 0.0F, 1.0F, c);
            vertex(vc, m, x + lx - ux, y + ly - uy, z + lz - uz, 1.0F, 1.0F, c);
            vertex(vc, m, x + lx + ux, y + ly + uy, z + lz + uz, 1.0F, 0.0F, c);
            vertex(vc, m, x - lx + ux, y - ly + uy, z - lz + uz, 0.0F, 0.0F, c);
        }
    }

    private static int whiter(int c) {
        return c & 0xFF000000 | Fx.lerp(c | 0xFF000000, -1, 0.45F) & 0xFFFFFF;
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int c) {
        vc.addVertex((Matrix4fc) m, x, y, z)
                .setUv(u, v)
                .setColor(c)
                .setOverlay(0)
                .setLight(15728880)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    static int alpha(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * (float) (c >>> 24)) << 24 | c & 0xFFFFFF;
    }

    static {
        GLOW = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE);
        TRANSLUCENT = pipeline("target_esp", BlendFunction.TRANSLUCENT, false);
        TRANSLUCENT_TOP = pipeline("target_esp_top", BlendFunction.TRANSLUCENT, true);
        ADDITIVE = pipeline("target_esp_glow", GLOW, false);
        ADDITIVE_TOP = pipeline("target_esp_glow_top", GLOW, true);
        TYPES = new HashMap<>();
        ARCS = texture("disc_arcs");
        TREFOIL = texture("disc_trefoil");
        DIAMONDS = texture("disc_diamonds");
        SIGHT = texture("marker_sight");
        TRIANGLE = texture("marker_triangle");
        HALO = texture("halo");
        LIGHT = texture("glow");
        START = System.nanoTime();
        RANDOM = new Random();
        discSpin = new float[3];
        discRock = new float[3];
        discPhase = new float[3];
        discLean = new float[3];
        markerSpin = 1.0F;
        roll();
        ORBITS = new float[][] {{24.0F, 0.0F, 1.6F}, {-28.0F, 120.0F, -1.3F}, {62.0F, 240.0F, 1.1F}};
    }

    private static record Mark(
            MultiBufferSource buffers,
            Matrix4f pose,
            LivingEntity t,
            Vector3f left,
            Vector3f up,
            Vec3 cam,
            float x,
            float y,
            float z,
            float width,
            float height,
            float fade,
            float since,
            float size,
            float hit,
            boolean struck,
            boolean seen) {
        int colour(int offset, float alpha) {
            return Modules.MARKER.colour(this.t, offset, alpha * this.fade);
        }

        Vec3 world(float px, float py, float pz) {
            return new Vec3(this.cam.x + (double) px, this.cam.y + (double) py, this.cam.z + (double) pz);
        }
    }
}
