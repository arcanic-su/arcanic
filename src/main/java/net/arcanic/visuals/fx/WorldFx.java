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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import net.arcanic.visuals.core.Modules;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Everything our modules draw in the world: the particle system, the target marker and the trail ribbon. */
public final class WorldFx {
    private WorldFx() {}

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        Vector3f left = new Vector3f(camera.leftVector()), up = new Vector3f(camera.upVector());
        PoseStack ps = ctx.matrices();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (Modules.MARKER.enabled()) TargetEsp.world(ctx, cam, left, up, pt);
        if (Modules.HALO.enabled() && !mc.options.getCameraType().isFirstPerson() && !mc.player.isInvisible())
            halo(ps, ctx, cam, left, up, pt, mc);
        if (Modules.SWING.enabled()) swing(ps, ctx, cam, mc, pt);
        if (Modules.HAT.enabled() && !mc.options.getCameraType().isFirstPerson() && !mc.player.isInvisible())
            hat(ps, ctx, cam, mc, pt);
        ArcVisuals.world(ctx);
        ArcEffects.world(ctx);
        Fx.render(ps, ctx.consumers(), cam, left, up);
    }

    private static void halo(
            PoseStack ps, WorldRenderContext ctx, Vec3 cam, Vector3f left, Vector3f up, float pt, Minecraft mc) {
        var m = Modules.HALO;
        var p = mc.player;
        double time = System.currentTimeMillis() / 1000.0;
        double x = p.xo + (p.getX() - p.xo) * pt, y = p.yo + (p.getY() - p.yo) * pt, z = p.zo + (p.getZ() - p.zo) * pt;
        float headY = (float) (y + p.getBbHeight() + m.height.get() + Math.sin(time * 2.2) * .035);
        float r = .3f * m.size.f();
        float yaw = (float) Math.toRadians(-(p.yHeadRotO + (p.getYHeadRot() - p.yHeadRotO) * pt));
        float pitch = (float) Math.toRadians((p.xRotO + (p.getXRot() - p.xRotO) * pt) * .25 + Math.sin(time * 1.3) * 4);
        // ring plane: horizontal, tilted forward a little with the head
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw); // forward
        float rx = fz, rz = -fx; // right
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float ax = rx * r, ay = 0, az = rz * r; // right axis stays level
        float bx = fx * cp * r, by = -sp * r, bz = fz * cp * r; // forward axis dips with the pitch
        float cx = (float) (x - cam.x), cy = (float) (headY - cam.y), cz = (float) (z - cam.z);
        PoseStack.Pose pose = ps.last();
        int c = m.colourAt(0), c2 = m.colourAt(.5f);
        VertexConsumer glow = ctx.consumers().getBuffer(Fx.type(Fx.Tex.GLOW, true));
        Fx.quad(glow, pose, cx, cy, cz, ax * 1.5f, ay, az * 1.5f, bx * 1.5f, by * 1.5f, bz * 1.5f, alpha(c, .35f));
        Fx.quad(glow, pose, cx, cy, cz, bx * 1.5f, by * 1.5f, bz * 1.5f, ax * 1.5f, ay, az * 1.5f, alpha(c, .35f));
        VertexConsumer ring = ctx.consumers().getBuffer(Fx.type(Fx.Tex.RING_NEON, true));
        Fx.quad(ring, pose, cx, cy, cz, ax, ay, az, bx, by, bz, alpha(c2, .95f));
        Fx.quad(ring, pose, cx, cy, cz, bx, by, bz, ax, ay, az, alpha(c2, .95f));
        if (m.sparks.on()) {
            VertexConsumer st = ctx.consumers().getBuffer(Fx.type(Fx.Tex.STAR, true));
            for (int i = 0; i < 3; i++) {
                double ang = time * 2.6 + i * Math.PI * 2 / 3;
                float k = .86f;
                float px = cx + (float) (Math.cos(ang) * ax * k + Math.sin(ang) * bx * k),
                        py = cy + (float) (Math.cos(ang) * ay * k + Math.sin(ang) * by * k),
                        pz = cz + (float) (Math.cos(ang) * az * k + Math.sin(ang) * bz * k);
                float s = .055f + .02f * (float) Math.sin(time * 9 + i * 2);
                Vector3f l2 = new Vector3f(left).mul(s), u2 = new Vector3f(up).mul(s);
                Fx.quad(st, pose, px, py, pz, l2.x, l2.y, l2.z, u2.x, u2.y, u2.z, alpha(0xFFFFFFFF, .9f));
            }
        }
    }

    private record SwingPt(Vec3 in, Vec3 out, long t, float frac) {}

    private static final ArrayDeque<SwingPt> swingPts = new ArrayDeque<>();
    private static float lastAnim, lastPhi = -99;

    private static void swing(PoseStack ps, WorldRenderContext ctx, Vec3 cam, Minecraft mc, float pt) {
        var m = Modules.SWING;
        var p = mc.player;
        long now = System.currentTimeMillis();
        double life = m.life.get() * 1000;
        float anim = p.getAttackAnim(pt);
        if (anim < lastAnim - .2f) {
            swingPts.addLast(new SwingPt(null, null, now, 0));
            lastPhi = -99;
        } // a new swing
        lastAnim = anim;
        if (anim > .001f && anim < .999f) {
            float e = 1 - (float) Math.pow(1 - anim, 3);
            float phi0 = .7f, phi1 = 3.75f, phi = phi0 + (phi1 - phi0) * e;
            float from = lastPhi < -50 ? phi : lastPhi;
            int steps = Math.max(1, (int) Math.ceil(Math.abs(phi - from) / .1f));
            double yaw = Math.toRadians(p.getViewYRot(pt)), pitch = Math.toRadians(p.getViewXRot(pt) * .6);
            Vec3 f = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            double side = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
            Vec3 r = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(side);
            Vec3 u = r.cross(f).scale(side).normalize();
            // the arc lies in a plane that faces the camera and leans forward
            Vec3 e1 = r, e2 = u.scale(.8).add(f.scale(.6)).normalize();
            Vec3 shoulder = new Vec3(
                            p.xo + (p.getX() - p.xo) * pt,
                            p.yo + (p.getY() - p.yo) * pt + p.getBbHeight() * .62,
                            p.zo + (p.getZ() - p.zo) * pt)
                    .add(f.scale(.55));
            float lo = 1.55f * m.size.f(), li = .6f * m.size.f();
            for (int i = 1; i <= steps; i++) {
                float ph = from + (phi - from) * i / steps;
                Vec3 dir = e1.scale(Math.cos(ph)).add(e2.scale(Math.sin(ph)));
                swingPts.addLast(new SwingPt(
                        shoulder.add(dir.scale(li)), shoulder.add(dir.scale(lo)), now, (ph - phi0) / (phi1 - phi0)));
            }
            lastPhi = phi;
        } else lastPhi = -99;
        while (!swingPts.isEmpty() && now - swingPts.peekFirst().t() > life) swingPts.pollFirst();
        if (swingPts.size() < 2 || (mc.options.getCameraType().isFirstPerson() && !m.firstPerson.on())) return;
        PoseStack.Pose pose = ps.last();
        VertexConsumer vc = ctx.consumers().getBuffer(Fx.type(Fx.Tex.SWING, false));
        SwingPt prev = null;
        for (SwingPt s : swingPts) {
            if (s.in() == null) {
                prev = null;
                continue;
            }
            if (prev != null) {
                float a0 = 1 - (float) Math.min(1, (now - prev.t()) / life),
                        a1 = 1 - (float) Math.min(1, (now - s.t()) / life);
                int c0 = straight(m.colourAt(prev.frac()), a0 * .95f), c1 = straight(m.colourAt(s.frac()), a1 * .95f);
                Vec3 o0 = prev.out().subtract(cam),
                        i0 = prev.in().subtract(cam),
                        o1 = s.out().subtract(cam),
                        i1 = s.in().subtract(cam);
                quad4(vc, pose, o0, i0, i1, o1, a0, a1, c0, c1);
                quad4(vc, pose, o1, i1, i0, o0, a1, a0, c1, c0);
            }
            prev = s;
        }
    }

    private static void hat(PoseStack ps, WorldRenderContext ctx, Vec3 cam, Minecraft mc, float pt) {
        var m = Modules.HAT;
        var p = mc.player;
        double time = System.currentTimeMillis() / 1000.0;
        float sz = m.size.f(), op = m.opacity.f();
        double x = p.xo + (p.getX() - p.xo) * pt, z = p.zo + (p.getZ() - p.zo) * pt;
        double y = p.yo + (p.getY() - p.yo) * pt + p.getBbHeight() + .02;
        float R = .56f * sz, H = .3f * sz;
        float cx = (float) (x - cam.x), by = (float) (y - cam.y), cz = (float) (z - cam.z), ay = by + H;
        PoseStack.Pose pose = ps.last();
        VertexConsumer vc = ctx.consumers().getBuffer(Fx.type(Fx.Tex.WHITE, false));
        int n = 36;
        double rot = m.spin.on() ? time * .8 : 0;
        int apex = straight(Fx.lerp(m.colourAt(0) | 0xFF000000, 0xFFFFFFFF, .35f), op * .9f);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float x0 = cx + (float) Math.cos(a0) * R,
                    z0 = cz + (float) Math.sin(a0) * R,
                    x1 = cx + (float) Math.cos(a1) * R,
                    z1 = cz + (float) Math.sin(a1) * R;
            float f0 = (float) (.5 + .5 * Math.sin(a0 + rot)), f1 = (float) (.5 + .5 * Math.sin(a1 + rot));
            int c0 = straight(m.colourAt(f0), op * .7f), c1 = straight(m.colourAt(f1), op * .7f);
            // the cone (both windings) and a bright rim
            Fx.v(vc, pose, cx, ay, cz, .5f, 0, apex);
            Fx.v(vc, pose, x0, by, z0, 0, 1, c0);
            Fx.v(vc, pose, x1, by, z1, 1, 1, c1);
            Fx.v(vc, pose, cx, ay, cz, .5f, 0, apex);
            Fx.v(vc, pose, cx, ay, cz, .5f, 0, apex);
            Fx.v(vc, pose, x1, by, z1, 1, 1, c1);
            Fx.v(vc, pose, x0, by, z0, 0, 1, c0);
            Fx.v(vc, pose, cx, ay, cz, .5f, 0, apex);
            int r0 = straight(Fx.lerp(m.colourAt(f0) | 0xFF000000, 0xFFFFFFFF, .3f), Math.min(1, op + .4f)),
                    r1 = straight(Fx.lerp(m.colourAt(f1) | 0xFF000000, 0xFFFFFFFF, .3f), Math.min(1, op + .4f));
            float t = .025f;
            Fx.v(vc, pose, x0, by + t, z0, 0, 0, r0);
            Fx.v(vc, pose, x0, by - t, z0, 0, 1, r0);
            Fx.v(vc, pose, x1, by - t, z1, 1, 1, r1);
            Fx.v(vc, pose, x1, by + t, z1, 1, 0, r1);
            Fx.v(vc, pose, x1, by + t, z1, 1, 0, r1);
            Fx.v(vc, pose, x1, by - t, z1, 1, 1, r1);
            Fx.v(vc, pose, x0, by - t, z0, 0, 1, r0);
            Fx.v(vc, pose, x0, by + t, z0, 0, 0, r0);
        }
    }

    private static int straight(int c, float a) {
        return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (c & 0xFFFFFF);
    }

    private static void quad4(
            VertexConsumer vc,
            PoseStack.Pose pose,
            Vec3 a0,
            Vec3 b0,
            Vec3 b1,
            Vec3 a1,
            float u0,
            float u1,
            int c0,
            int c1) {
        Fx.v(vc, pose, (float) a0.x, (float) a0.y, (float) a0.z, u0, 0, c0);
        Fx.v(vc, pose, (float) b0.x, (float) b0.y, (float) b0.z, u0, 1, c0);
        Fx.v(vc, pose, (float) b1.x, (float) b1.y, (float) b1.z, u1, 1, c1);
        Fx.v(vc, pose, (float) a1.x, (float) a1.y, (float) a1.z, u1, 0, c1);
    }

    static int alpha(int c, float a) {
        int r = ((int) (Math.max(0, Math.min(1, a)) * ((c >>> 24) / 255f) * 255) << 24) | (c & 0xFFFFFF);
        return Fx.additiveOk() ? Fx.pm(r) : r;
    }
}
