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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import java.util.function.Function;
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.arcanic.visuals.mixin.RenderTypeAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Our own particle system. */
public final class Fx {
    private Fx() {}

    public enum Tex {
        GLOW("glow"),
        SPARK("spark"),
        STAR("star"),
        HEART("heart"),
        SHOCK("shock"),
        SLASH("slash"),
        SMOKE("smoke"),
        RING_NEON("ring_neon"),
        RING_WAVE("ring_wave"),
        RING_DOTS("ring_dots"),
        RING_SUN("ring_sun"),
        TRAIL("trail"),
        CRESCENT("crescent"),
        BRACKET("bracket"),
        FOOT("foot"),
        SWING("swing"),
        CURTAIN("curtain"),
        WHITE("white"),
        LINE_GLOW("line_glow"),
        LINE_SOFT("line_soft"),
        LINE_CORE("line_core");
        final Identifier id;

        Tex(String n) {
            id = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/effect/" + n + ".png");
        }
    }

    public enum Mode {
        BILLBOARD,
        STRETCH,
        FLAT
    }

    /** One particle. */
    public static final class P {
        Tex tex = Tex.GLOW;
        Mode mode = Mode.BILLBOARD;
        boolean additive = true;
        double x, y, z, vx, vy, vz;
        float drag = 2.5f, gravity = 0, life = .6f, age;
        float size = .2f, sizeEnd = 0, rot, spin;
        int c0 = 0xFFFFFFFF, c1 = 0xFFFFFFFF;
        float fadeIn = .05f, twinkle;

        public P at(Vec3 v) {
            x = v.x;
            y = v.y;
            z = v.z;
            return this;
        }

        public P vel(double a, double b, double c) {
            vx = a;
            vy = b;
            vz = c;
            return this;
        }

        public P tex(Tex t) {
            tex = t;
            return this;
        }

        public P mode(Mode m) {
            mode = m;
            return this;
        }

        public P life(float l) {
            life = l;
            return this;
        }

        public P size(float a, float b) {
            size = a;
            sizeEnd = b;
            return this;
        }

        public P colour(int a, int b) {
            c0 = a;
            c1 = b;
            return this;
        }

        public P drag(float d) {
            drag = d;
            return this;
        }

        public P gravity(float g) {
            gravity = g;
            return this;
        }

        public P spin(float r, float s) {
            rot = r;
            spin = s;
            return this;
        }

        public P blend(boolean add) {
            additive = add;
            return this;
        }

        public P fadeIn(float f) {
            fadeIn = f;
            return this;
        }

        public P twinkle(float t) {
            twinkle = t;
            return this;
        }
    }

    private static final List<P> live = new ArrayList<>();
    private static final Random R = new Random();
    private static long last = System.nanoTime();
    private static final int MAX = 4000;

    public static P spawn() {
        P p = new P();
        if (live.size() < MAX) live.add(p);
        return p;
    }

    public static Random rnd() {
        return R;
    }

    public static void clear() {
        live.clear();
    }

    private static Function<Identifier, RenderType> additive;
    private static boolean additiveFailed;

    public static RenderType type(Tex t, boolean add) {
        if (add && !additiveFailed) {
            if (additive == null) {
                additive = makeAdditive(false);
                if (additive == null) additiveFailed = true;
            }
            if (additive != null)
                try {
                    return additive.apply(t.id);
                } catch (RuntimeException e) {
                    additive = null;
                    additiveFailed = true;
                }
        }
        return RenderTypes.eyes(t.id);
    }
    /** Additive glow with any texture (the weapon aura uses the grey item atlas); null without the glow pipeline. */
    public static RenderType glowWith(Identifier texture) {
        if (additiveFailed) return null;
        if (additive == null) {
            additive = makeAdditive(false);
            if (additive == null) {
                additiveFailed = true;
                return null;
            }
        }
        try {
            return additive.apply(texture);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Function<Identifier, RenderType> onTop;
    private static boolean onTopFailed;
    /** Additive glow drawn over everything (no depth test): the Crosshair marker stays visible on the target's body. */
    public static RenderType typeOnTop(Tex t) {
        if (!onTopFailed) {
            if (onTop == null) {
                onTop = makeAdditive(true);
                if (onTop == null) onTopFailed = true;
            }
            if (onTop != null)
                try {
                    return onTop.apply(t.id);
                } catch (RuntimeException e) {
                    onTop = null;
                    onTopFailed = true;
                }
        }
        return type(t, true);
    }
    /** With Iris shaders on, tell Iris which of its programs draws our glow pipeline (otherwise it is skipped). */
    static void irisAssign(RenderPipeline pipeline) {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> prog = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object inst = api.getMethod("getInstance").invoke(null);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object program =
                    Enum.valueOf((Class) prog, System.getProperty("arcanicvisuals.irisprogram", "EMISSIVE_ENTITIES"));
            api.getMethod("assignPipeline", RenderPipeline.class, prog).invoke(inst, pipeline, program);
        } catch (Throwable e) {
            System.err.println("[ArcanicVisuals] Iris pipeline assignment failed: " + e);
        }
    }

    private static final RenderType[] SOFT = new RenderType[2];
    private static boolean softFailed;
    /** Translucent ribbons with soft, anti-aliased edges (the soft line texture across the width). */
    public static RenderType soft(boolean onTop) {
        int i = onTop ? 1 : 0;
        if (SOFT[i] != null || softFailed) return SOFT[i] != null ? SOFT[i] : solid(onTop);
        try {
            RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$entitySnippet())
                    .withLocation(Identifier.fromNamespaceAndPath(
                            "arcanicvisuals", onTop ? "pipeline/soft_top" : "pipeline/soft"))
                    .withShaderDefine("EMISSIVE")
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false);
            if (onTop) b = b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
            RenderPipeline pipeline = b.build();
            irisAssign(pipeline);
            SOFT[i] = RenderTypeAccess.arcanicvisuals$create(
                    onTop ? "arcanicvisuals_soft_top" : "arcanicvisuals_soft",
                    RenderSetup.builder(pipeline)
                            .withTexture("Sampler0", Tex.LINE_SOFT.id)
                            .sortOnUpload()
                            .createRenderSetup());
        } catch (Throwable e) {
            softFailed = true;
            System.err.println("[ArcanicVisuals] soft pipeline unavailable: " + e);
            return solid(onTop);
        }
        return SOFT[i];
    }

    private static final RenderType[] SOLID = new RenderType[2];
    private static boolean solidFailed;
    /** Plain translucent colour (lines and fills of the hitboxes); {@code onTop} ignores walls. */
    public static RenderType solid(boolean onTop) {
        int i = onTop ? 1 : 0;
        if (SOLID[i] != null || solidFailed) return SOLID[i] != null ? SOLID[i] : RenderTypes.eyes(Tex.WHITE.id);
        try {
            RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$entitySnippet())
                    .withLocation(Identifier.fromNamespaceAndPath(
                            "arcanicvisuals", onTop ? "pipeline/solid_top" : "pipeline/solid"))
                    .withShaderDefine("EMISSIVE")
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false);
            if (onTop) b = b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
            RenderPipeline pipeline = b.build();
            irisAssign(pipeline);
            SOLID[i] = RenderTypeAccess.arcanicvisuals$create(
                    onTop ? "arcanicvisuals_solid_top" : "arcanicvisuals_solid",
                    RenderSetup.builder(pipeline)
                            .withTexture("Sampler0", Tex.WHITE.id)
                            .sortOnUpload()
                            .createRenderSetup());
        } catch (Throwable e) {
            solidFailed = true;
            System.err.println("[ArcanicVisuals] solid pipeline unavailable: " + e);
            return RenderTypes.eyes(Tex.WHITE.id);
        }
        return SOLID[i];
    }

    private static Function<Identifier, RenderType> makeAdditive(boolean onTop) {
        try {
            RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$entitySnippet())
                    .withLocation(Identifier.fromNamespaceAndPath(
                            "arcanicvisuals", onTop ? "pipeline/glow_top" : "pipeline/glow"))
                    .withShaderDefine("EMISSIVE")
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.ADDITIVE)
                    .withDepthWrite(false)
                    .withCull(false);
            if (onTop) b = b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
            RenderPipeline pipeline = b.build();
            irisAssign(pipeline);
            Map<Identifier, RenderType> cache = new HashMap<>();
            String name = onTop ? "arcanicvisuals_glow_top" : "arcanicvisuals_glow";
            return id -> cache.computeIfAbsent(
                    id,
                    k -> RenderTypeAccess.arcanicvisuals$create(
                            name,
                            RenderSetup.builder(pipeline)
                                    .withTexture("Sampler0", k)
                                    .sortOnUpload()
                                    .createRenderSetup()));
        } catch (Throwable e) {
            System.err.println("[ArcanicVisuals] additive pipeline unavailable, using translucent: " + e);
            return null;
        }
    }

    public static void render(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vector3f camLeft, Vector3f camUp) {
        long now = System.nanoTime();
        float dt = Math.min(.1f, (now - last) / 1e9f);
        last = now;
        if (Minecraft.getInstance().isPaused()) dt = 0;
        if (live.isEmpty()) return;
        PoseStack.Pose pose = ps.last();
        // update first, then draw grouped by render type: opening another type's buffer closes the previous one
        Map<RenderType, List<P>> groups = new LinkedHashMap<>();
        for (Iterator<P> it = live.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            float k = (float) Math.exp(-p.drag * dt);
            p.vx *= k;
            p.vy = p.vy * k - p.gravity * dt;
            p.vz *= k;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.rot += p.spin * dt;
            groups.computeIfAbsent(type(p.tex, p.additive), t -> new ArrayList<>())
                    .add(p);
        }
        for (Map.Entry<RenderType, List<P>> group : groups.entrySet()) {
            VertexConsumer vc = buffers.getBuffer(group.getKey());
            for (P p : group.getValue()) draw(vc, pose, p, cam, camLeft, camUp);
        }
    }

    private static void draw(VertexConsumer vc, PoseStack.Pose pose, P p, Vec3 cam, Vector3f camLeft, Vector3f camUp) {
        float t = p.age / p.life;
        float a = (p.fadeIn > 0 && p.age < p.fadeIn ? p.age / p.fadeIn : 1) * (1 - t * t);
        float size = p.size + (p.sizeEnd - p.size) * t;
        if (p.twinkle > 0) size *= 1 - p.twinkle * .5f * (1 + (float) Math.sin(p.age * 22 + p.rot * 7));
        if (size <= 0.001f || a <= 0.003f) return;
        int col = lerp(p.c0, p.c1, t);
        col = ((int) (((col >>> 24) / 255f) * a * 255) << 24) | (col & 0xFFFFFF);
        if (p.additive && !additiveFailed) col = pm(col);
        float x = (float) (p.x - cam.x), y = (float) (p.y - cam.y), z = (float) (p.z - cam.z);
        switch (p.mode) {
            case FLAT -> {
                float c = (float) Math.cos(p.rot) * size, s = (float) Math.sin(p.rot) * size;
                quad(vc, pose, x, y, z, c, 0, s, -s, 0, c, col);
                quad(vc, pose, x, y, z, -s, 0, c, c, 0, s, col);
            }
            case STRETCH -> {
                Vector3f v = new Vector3f((float) p.vx, (float) p.vy, (float) p.vz);
                float speed = v.length();
                if (speed < 1e-4f) v.set(camUp);
                else v.div(speed);
                float len = size * (1.5f + Math.min(6, speed * .6f));
                Vector3f toCam = new Vector3f(-x, -y, -z).normalize();
                Vector3f side = v.cross(toCam, new Vector3f()).normalize().mul(size * .35f);
                Vector3f along = new Vector3f(v).mul(len);
                quad(
                        vc,
                        pose,
                        x - along.x * .5f,
                        y - along.y * .5f,
                        z - along.z * .5f,
                        along.x * .5f,
                        along.y * .5f,
                        along.z * .5f,
                        side.x,
                        side.y,
                        side.z,
                        col);
            }
            default -> {
                float c = (float) Math.cos(p.rot), s = (float) Math.sin(p.rot);
                Vector3f r = new Vector3f(camLeft).mul(c * size).add(new Vector3f(camUp).mul(s * size));
                Vector3f u = new Vector3f(camUp).mul(c * size).sub(new Vector3f(camLeft).mul(s * size));
                quad(vc, pose, x, y, z, r.x, r.y, r.z, u.x, u.y, u.z, col);
            }
        }
    }
    /** Quad centred at (x,y,z) with half-axes a and b. */
    static void quad(
            VertexConsumer vc,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            int col) {
        v(vc, pose, x - ax - bx, y - ay - by, z - az - bz, 0, 1, col);
        v(vc, pose, x + ax - bx, y + ay - by, z + az - bz, 1, 1, col);
        v(vc, pose, x + ax + bx, y + ay + by, z + az + bz, 1, 0, col);
        v(vc, pose, x - ax + bx, y - ay + by, z - az + bz, 0, 0, col);
    }

    public static void v(
            VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float vv, int col) {
        vc.addVertex(pose, x, y, z)
                .setColor(col)
                .setUv(u, vv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, 0, 1, 0);
    }

    public static int pm(int c) {
        int a = c >>> 24;
        return 0xFF000000 | ((c >> 16 & 255) * a / 255) << 16 | ((c >> 8 & 255) * a / 255) << 8 | ((c & 255) * a / 255);
    }

    public static boolean additiveOk() {
        return !additiveFailed;
    }

    public static int lerp(int a, int b, float t) {
        if (a == b) return a;
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24
                | (int) (ar + (br - ar) * t) << 16
                | (int) (ag + (bg - ag) * t) << 8
                | (int) (ab + (bb - ab) * t);
    }

    public static Vec3 dir() {
        double u = R.nextDouble() * 2 - 1, th = R.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
        return new Vec3(s * Math.cos(th), u, s * Math.sin(th));
    }
}
