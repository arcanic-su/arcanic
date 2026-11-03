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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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

public final class Fx {
    private static final List<P> live = new ArrayList<>();
    public static boolean studio;
    private static final List<P> studioLive = new ArrayList<>();
    private static long studioLast = System.nanoTime();
    private static final Random R = new Random();
    private static long last = System.nanoTime();
    private static final int MAX = 4000;
    private static Function<Identifier, RenderType> additive;
    private static boolean additiveFailed;
    private static Function<Identifier, RenderType> onTop;
    private static boolean onTopFailed;
    private static final RenderType[] SOFT = new RenderType[2];
    private static boolean softFailed;
    private static final RenderPipeline[] RIBBON_PIPE = new RenderPipeline[2];
    private static final Map<String, RenderType> RIBBON = new HashMap<>();
    private static boolean ribbonFailed;
    private static final RenderType[] SOLID = new RenderType[2];
    private static boolean solidFailed;
    private static RenderType glowLines;
    private static boolean glowLinesFailed;

    private Fx() {}

    public static P spawn() {
        P p = new P();
        List<P> to = studio ? studioLive : live;
        if (to.size() < 4000) {
            to.add(p);
        }

        return p;
    }

    public static void clearStudio() {
        studioLive.clear();
    }

    public static void renderStudio(
            PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vector3f camLeft, Vector3f camUp, float speed) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float) (now - studioLast) / 1.0E9F) * speed;
        studioLast = now;
        run(studioLive, dt, ps, buffers, cam, camLeft, camUp);
    }

    public static Random rnd() {
        return R;
    }

    public static void clear() {
        live.clear();
    }

    public static RenderType type(Tex t, boolean add) {
        if (add && !additiveFailed) {
            if (additive == null) {
                additive = makeAdditive(false);
                if (additive == null) {
                    additiveFailed = true;
                }
            }

            if (additive != null) {
                try {
                    return (RenderType) additive.apply(t.id);
                } catch (RuntimeException var3) {
                    additive = null;
                    additiveFailed = true;
                }
            }
        }

        return RenderTypes.eyes(t.id);
    }

    public static RenderType glowWith(Identifier texture) {
        if (additiveFailed) {
            return null;
        } else {
            if (additive == null) {
                additive = makeAdditive(false);
                if (additive == null) {
                    additiveFailed = true;
                    return null;
                }
            }

            try {
                return (RenderType) additive.apply(texture);
            } catch (RuntimeException var2) {
                return null;
            }
        }
    }

    public static RenderType typeOnTop(Tex t) {
        if (!onTopFailed) {
            if (onTop == null) {
                onTop = makeAdditive(true);
                if (onTop == null) {
                    onTopFailed = true;
                }
            }

            if (onTop != null) {
                try {
                    return (RenderType) onTop.apply(t.id);
                } catch (RuntimeException var2) {
                    onTop = null;
                    onTopFailed = true;
                }
            }
        }

        return type(t, true);
    }

    static void irisAssign(RenderPipeline pipeline) {
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Class<?> prog = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
                Object inst = api.getMethod("getInstance").invoke((Object) null);
                Object program = Enum.valueOf(
                        (Class) prog, System.getProperty("arcanicvisuals.irisprogram", "EMISSIVE_ENTITIES"));
                api.getMethod("assignPipeline", RenderPipeline.class, prog).invoke(inst, pipeline, program);
            } catch (Throwable e) {
                System.err.println("[ArcanicVisuals] Iris pipeline assignment failed: " + String.valueOf(e));
            }
        }
    }

    public static RenderType soft(boolean onTop) {
        int i = onTop ? 1 : 0;
        if (SOFT[i] == null && !softFailed) {
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
                if (onTop) {
                    b = b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
                }

                RenderPipeline pipeline = b.build();
                irisAssign(pipeline);
                SOFT[i] = RenderTypeAccess.arcanicvisuals$create(
                        onTop ? "arcanicvisuals_soft_top" : "arcanicvisuals_soft",
                        RenderSetup.builder(pipeline)
                                .withTexture("Sampler0", Fx.Tex.LINE_SOFT.id)
                                .sortOnUpload()
                                .createRenderSetup());
            } catch (Throwable e) {
                softFailed = true;
                System.err.println("[ArcanicVisuals] soft pipeline unavailable: " + String.valueOf(e));
                return solid(onTop);
            }

            return SOFT[i];
        } else {
            return SOFT[i] != null ? SOFT[i] : solid(onTop);
        }
    }

    public static RenderType ribbon(Tex tex, boolean onTop) {
        int i = onTop ? 1 : 0;
        String var10000 = tex.name().toLowerCase(Locale.ROOT);
        String name = "arcanicvisuals_ribbon_" + var10000 + (onTop ? "_top" : "");
        RenderType cached = (RenderType) RIBBON.get(name);
        if (cached != null) {
            return cached;
        } else if (ribbonFailed) {
            return soft(onTop);
        } else {
            try {
                if (RIBBON_PIPE[i] == null) {
                    RenderPipeline.Builder b = RenderPipeline.builder(
                                    RenderPipelinesAccess.arcanicvisuals$entitySnippet())
                            .withLocation(Identifier.fromNamespaceAndPath(
                                    "arcanicvisuals", onTop ? "pipeline/ribbon_top" : "pipeline/ribbon"))
                            .withShaderDefine("EMISSIVE")
                            .withShaderDefine("NO_OVERLAY")
                            .withShaderDefine("NO_CARDINAL_LIGHTING")
                            .withSampler("Sampler0")
                            .withBlend(BlendFunction.TRANSLUCENT)
                            .withDepthWrite(false)
                            .withCull(false);
                    if (onTop) {
                        b = b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
                    }

                    RIBBON_PIPE[i] = b.build();
                    irisAssign(RIBBON_PIPE[i]);
                }

                RenderType type = RenderTypeAccess.arcanicvisuals$create(
                        name,
                        RenderSetup.builder(RIBBON_PIPE[i])
                                .withTexture("Sampler0", tex.id)
                                .sortOnUpload()
                                .createRenderSetup());
                RIBBON.put(name, type);
                return type;
            } catch (Throwable e) {
                ribbonFailed = true;
                System.err.println("[ArcanicVisuals] ribbon pipeline unavailable: " + String.valueOf(e));
                return soft(onTop);
            }
        }
    }

    public static RenderType solid(boolean onTop) {
        int i = onTop ? 1 : 0;
        if (SOLID[i] == null && !solidFailed) {
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
                if (onTop) {
                    b = b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
                }

                RenderPipeline pipeline = b.build();
                irisAssign(pipeline);
                SOLID[i] = RenderTypeAccess.arcanicvisuals$create(
                        onTop ? "arcanicvisuals_solid_top" : "arcanicvisuals_solid",
                        RenderSetup.builder(pipeline)
                                .withTexture("Sampler0", Fx.Tex.WHITE.id)
                                .sortOnUpload()
                                .createRenderSetup());
            } catch (Throwable e) {
                solidFailed = true;
                System.err.println("[ArcanicVisuals] solid pipeline unavailable: " + String.valueOf(e));
                return RenderTypes.eyes(Fx.Tex.WHITE.id);
            }

            return SOLID[i];
        } else {
            return SOLID[i] != null ? SOLID[i] : RenderTypes.eyes(Fx.Tex.WHITE.id);
        }
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
            if (onTop) {
                b = b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
            }

            RenderPipeline pipeline = b.build();
            irisAssign(pipeline);
            Map<Identifier, RenderType> cache = new HashMap<>();
            String name = onTop ? "arcanicvisuals_glow_top" : "arcanicvisuals_glow";
            return (id) -> (RenderType) cache.computeIfAbsent(
                    id,
                    (k) -> RenderTypeAccess.arcanicvisuals$create(
                            name,
                            RenderSetup.builder(pipeline)
                                    .withTexture("Sampler0", k)
                                    .sortOnUpload()
                                    .createRenderSetup()));
        } catch (Throwable e) {
            System.err.println(
                    "[ArcanicVisuals] additive pipeline unavailable, using translucent: " + String.valueOf(e));
            return null;
        }
    }

    public static RenderType glowLines() {
        if (glowLines == null && !glowLinesFailed) {
            try {
                RenderPipeline pipeline = RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$linesSnippet())
                        .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/glow_lines"))
                        .withCull(false)
                        .withBlend(BlendFunction.LIGHTNING)
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                        .build();
                irisAssign(pipeline);
                glowLines = RenderTypeAccess.arcanicvisuals$create(
                        "arcanicvisuals_glow_lines",
                        RenderSetup.builder(pipeline)
                                .sortOnUpload()
                                .bufferSize(1536)
                                .createRenderSetup());
            } catch (Throwable e) {
                glowLinesFailed = true;
                System.err.println("[ArcanicVisuals] glow lines unavailable: " + String.valueOf(e));
            }

            return glowLines;
        } else {
            return glowLines;
        }
    }

    public static void line(
            VertexConsumer vc,
            PoseStack.Pose pose,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            int col,
            float width) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float l = (float) Math.sqrt((double) (dx * dx + dy * dy + dz * dz));
        if (!(l < 1.0E-6F)) {
            dx /= l;
            dy /= l;
            dz /= l;
            vc.addVertex(pose, ax, ay, az)
                    .setColor(col)
                    .setNormal(pose, dx, dy, dz)
                    .setLineWidth(width);
            vc.addVertex(pose, bx, by, bz)
                    .setColor(col)
                    .setNormal(pose, dx, dy, dz)
                    .setLineWidth(width);
        }
    }

    public static void render(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vector3f camLeft, Vector3f camUp) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float) (now - last) / 1.0E9F);
        last = now;
        if (Minecraft.getInstance().isPaused()) {
            dt = 0.0F;
        }

        run(live, dt, ps, buffers, cam, camLeft, camUp);
    }

    private static void run(
            List<P> live,
            float dt,
            PoseStack ps,
            MultiBufferSource buffers,
            Vec3 cam,
            Vector3f camLeft,
            Vector3f camUp) {
        if (!live.isEmpty()) {
            PoseStack.Pose pose = ps.last();
            Map<RenderType, List<P>> groups = new LinkedHashMap<>();
            Iterator<P> it = live.iterator();

            while (it.hasNext()) {
                P p = (P) it.next();
                p.age += dt;
                if (p.age >= p.life) {
                    it.remove();
                } else {
                    float k = (float) Math.exp((double) (-p.drag * dt));
                    p.vx *= (double) k;
                    p.vy = p.vy * (double) k - (double) (p.gravity * dt);
                    p.vz *= (double) k;
                    p.x += p.vx * (double) dt;
                    p.y += p.vy * (double) dt;
                    p.z += p.vz * (double) dt;
                    p.rot += p.spin * dt;
                    ((List) groups.computeIfAbsent(type(p.tex, p.additive), (t) -> new ArrayList<>())).add(p);
                }
            }

            for (Map.Entry<RenderType, List<P>> group : groups.entrySet()) {
                VertexConsumer vc = buffers.getBuffer((RenderType) group.getKey());

                for (P p : group.getValue()) {
                    draw(vc, pose, p, cam, camLeft, camUp);
                }
            }
        }
    }

    private static void draw(VertexConsumer vc, PoseStack.Pose pose, P p, Vec3 cam, Vector3f camLeft, Vector3f camUp) {
        float t = p.age / p.life;
        float a = (p.fadeIn > 0.0F && p.age < p.fadeIn ? p.age / p.fadeIn : 1.0F) * (1.0F - t * t);
        float size = p.size + (p.sizeEnd - p.size) * t;
        if (p.twinkle > 0.0F) {
            size *= 1.0F - p.twinkle * 0.5F * (1.0F + (float) Math.sin((double) (p.age * 22.0F + p.rot * 7.0F)));
        }

        if (!(size <= 0.001F) && !(a <= 0.003F)) {
            int col = lerp(p.c0, p.c1, t);
            col = (int) ((float) (col >>> 24) / 255.0F * a * 255.0F) << 24 | col & 0xFFFFFF;
            if (p.additive && !additiveFailed) {
                col = pm(col);
            }

            float x = (float) (p.x - cam.x);
            float y = (float) (p.y - cam.y);
            float z = (float) (p.z - cam.z);
            switch (p.mode.ordinal()) {
                case 1:
                    Vector3f v = new Vector3f((float) p.vx, (float) p.vy, (float) p.vz);
                    float speed = v.length();
                    if (speed < 1.0E-4F) {
                        v.set(camUp);
                    } else {
                        v.div(speed);
                    }

                    float len = size * (1.5F + Math.min(6.0F, speed * 0.6F));
                    Vector3f toCam = (new Vector3f(-x, -y, -z)).normalize();
                    Vector3f side = v.cross(toCam, new Vector3f()).normalize().mul(size * 0.35F);
                    Vector3f along = (new Vector3f(v)).mul(len);
                    quad(
                            vc,
                            pose,
                            x - along.x * 0.5F,
                            y - along.y * 0.5F,
                            z - along.z * 0.5F,
                            along.x * 0.5F,
                            along.y * 0.5F,
                            along.z * 0.5F,
                            side.x,
                            side.y,
                            side.z,
                            col);
                    break;
                case 2:
                    float c = (float) Math.cos((double) p.rot) * size;
                    float s = (float) Math.sin((double) p.rot) * size;
                    quad(vc, pose, x, y, z, c, 0.0F, s, -s, 0.0F, c, col);
                    quad(vc, pose, x, y, z, -s, 0.0F, c, c, 0.0F, s, col);
                    break;
                default:
                    c = (float) Math.cos((double) p.rot);
                    s = (float) Math.sin((double) p.rot);
                    Vector3f r = (new Vector3f(camLeft)).mul(c * size).add((new Vector3f(camUp)).mul(s * size));
                    Vector3f u = (new Vector3f(camUp)).mul(c * size).sub((new Vector3f(camLeft)).mul(s * size));
                    quad(vc, pose, x, y, z, r.x, r.y, r.z, u.x, u.y, u.z, col);
            }
        }
    }

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
        v(vc, pose, x - ax - bx, y - ay - by, z - az - bz, 0.0F, 1.0F, col);
        v(vc, pose, x + ax - bx, y + ay - by, z + az - bz, 1.0F, 1.0F, col);
        v(vc, pose, x + ax + bx, y + ay + by, z + az + bz, 1.0F, 0.0F, col);
        v(vc, pose, x - ax + bx, y - ay + by, z - az + bz, 0.0F, 0.0F, col);
    }

    public static void v(
            VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float vv, int col) {
        vc.addVertex(pose, x, y, z)
                .setColor(col)
                .setUv(u, vv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    public static int pm(int c) {
        int a = c >>> 24;
        return 0xFF000000 | (c >> 16 & 255) * a / 255 << 16 | (c >> 8 & 255) * a / 255 << 8 | (c & 255) * a / 255;
    }

    public static boolean additiveOk() {
        return !additiveFailed;
    }

    public static int lerp(int a, int b, float t) {
        if (a == b) {
            return a;
        } else {
            int aa = a >>> 24;
            int ar = a >> 16 & 255;
            int ag = a >> 8 & 255;
            int ab = a & 255;
            int ba = b >>> 24;
            int br = b >> 16 & 255;
            int bg = b >> 8 & 255;
            int bb = b & 255;
            return (int) ((float) aa + (float) (ba - aa) * t) << 24
                    | (int) ((float) ar + (float) (br - ar) * t) << 16
                    | (int) ((float) ag + (float) (bg - ag) * t) << 8
                    | (int) ((float) ab + (float) (bb - ab) * t);
        }
    }

    public static Vec3 dir() {
        double u = R.nextDouble() * 2.0 - 1.0;
        double th = R.nextDouble() * Math.PI * 2.0;
        double s = Math.sqrt(1.0 - u * u);
        return new Vec3(s * Math.cos(th), u, s * Math.sin(th));
    }

    public static enum Tex {
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
        SNOW("snow"),
        DOLLAR("dollar"),
        SPARKLE("sparkle"),
        FEATHER("feather"),
        SAKURA("sakura"),
        RING_SOFT("ring_soft"),
        FLAME("flame"),
        LINE_GLOW("line_glow"),
        LINE_SOFT("line_soft"),
        LINE_CORE("line_core"),
        LINE_AA("line_aa"),
        LINE_EDGE("line_edge"),
        MOTE_HALO("mote_halo"),
        MOTE("mote");

        final Identifier id;

        private Tex(String n) {
            this.id = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/effect/" + n + ".png");
        }
    }

    public static enum Mode {
        BILLBOARD,
        STRETCH,
        FLAT;
    }

    public static final class P {
        Tex tex;
        Mode mode;
        boolean additive;
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        float drag;
        float gravity;
        float life;
        float age;
        float size;
        float sizeEnd;
        float rot;
        float spin;
        int c0;
        int c1;
        float fadeIn;
        float twinkle;

        public P() {
            this.tex = Fx.Tex.GLOW;
            this.mode = Fx.Mode.BILLBOARD;
            this.additive = true;
            this.drag = 2.5F;
            this.gravity = 0.0F;
            this.life = 0.6F;
            this.size = 0.2F;
            this.sizeEnd = 0.0F;
            this.c0 = -1;
            this.c1 = -1;
            this.fadeIn = 0.05F;
        }

        public P at(Vec3 v) {
            this.x = v.x;
            this.y = v.y;
            this.z = v.z;
            return this;
        }

        public P vel(double a, double b, double c) {
            this.vx = a;
            this.vy = b;
            this.vz = c;
            return this;
        }

        public P tex(Tex t) {
            this.tex = t;
            return this;
        }

        public P mode(Mode m) {
            this.mode = m;
            return this;
        }

        public P life(float l) {
            this.life = l;
            return this;
        }

        public P size(float a, float b) {
            this.size = a;
            this.sizeEnd = b;
            return this;
        }

        public P colour(int a, int b) {
            this.c0 = a;
            this.c1 = b;
            return this;
        }

        public P drag(float d) {
            this.drag = d;
            return this;
        }

        public P gravity(float g) {
            this.gravity = g;
            return this;
        }

        public P spin(float r, float s) {
            this.rot = r;
            this.spin = s;
            return this;
        }

        public P blend(boolean add) {
            this.additive = add;
            return this;
        }

        public P fadeIn(float f) {
            this.fadeIn = f;
            return this;
        }

        public P twinkle(float t) {
            this.twinkle = t;
            return this;
        }
    }
}
