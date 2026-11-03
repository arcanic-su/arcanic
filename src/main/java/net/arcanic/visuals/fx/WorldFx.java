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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class WorldFx {
    private static final ArrayDeque<SwingPt> swingPts = new ArrayDeque<>();
    private static float lastAnim;
    private static float lastPhi = -99.0F;

    private WorldFx() {}

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 cam = camera.position();
            Vector3f left = new Vector3f(camera.leftVector());
            Vector3f up = new Vector3f(camera.upVector());
            PoseStack ps = ctx.matrices();
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            if (Modules.MARKER.enabled()) {
                TargetEsp.world(ctx, cam, left, up, pt);
            }

            if (Modules.SWING.enabled()) {
                swing(ps, ctx, cam, mc, pt);
            }

            if (Modules.AMBIENCE.enabled() && Modules.AMBIENCE.crystals.on()) {
                FloatingCrystals.render(ctx, cam, left, up, pt);
            }

            ArcVisuals.world(ctx);
            WorldPlus.world(ctx);
            ArcEffects.world(ctx);
            Fx.render(ps, ctx.consumers(), cam, left, up);
        }
    }

    private static void swing(PoseStack ps, WorldRenderContext ctx, Vec3 cam, Minecraft mc, float pt) {
        CombatFx.SwingTrail m = Modules.SWING;
        LocalPlayer p = mc.player;
        long now = System.currentTimeMillis();
        double life = (Double) m.life.get() * 1000.0;
        float anim = p.getAttackAnim(pt);
        if (anim < lastAnim - 0.2F) {
            swingPts.addLast(new SwingPt((Vec3) null, (Vec3) null, now, 0.0F));
            lastPhi = -99.0F;
        }

        lastAnim = anim;
        if (anim > 0.001F && anim < 0.999F) {
            float e = 1.0F - (float) Math.pow((double) (1.0F - anim), 3.0);
            float phi0 = 0.7F;
            float phi1 = 3.75F;
            float phi = phi0 + (phi1 - phi0) * e;
            float from = lastPhi < -50.0F ? phi : lastPhi;
            int steps = Math.max(1, (int) Math.ceil((double) (Math.abs(phi - from) / 0.1F)));
            double yaw = Math.toRadians((double) p.getViewYRot(pt));
            double pitch = Math.toRadians((double) p.getViewXRot(pt) * 0.6);
            Vec3 f = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            double side = p.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
            Vec3 r = (new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw))).scale(side);
            Vec3 u = r.cross(f).scale(side).normalize();
            Vec3 e1 = r;
            Vec3 e2 = u.scale(0.8).add(f.scale(0.6)).normalize();
            Vec3 shoulder = (new Vec3(
                            p.xo + (p.getX() - p.xo) * (double) pt,
                            p.yo + (p.getY() - p.yo) * (double) pt + (double) p.getBbHeight() * 0.62,
                            p.zo + (p.getZ() - p.zo) * (double) pt))
                    .add(f.scale(0.55));
            float lo = 1.55F * m.size.f();
            float li = 0.6F * m.size.f();

            for (int i = 1; i <= steps; ++i) {
                float ph = from + (phi - from) * (float) i / (float) steps;
                Vec3 dir = e1.scale(Math.cos((double) ph)).add(e2.scale(Math.sin((double) ph)));
                swingPts.addLast(new SwingPt(
                        shoulder.add(dir.scale((double) li)),
                        shoulder.add(dir.scale((double) lo)),
                        now,
                        (ph - phi0) / (phi1 - phi0)));
            }

            lastPhi = phi;
        } else {
            lastPhi = -99.0F;
        }

        while (!swingPts.isEmpty() && (double) (now - ((SwingPt) swingPts.peekFirst()).t()) > life) {
            swingPts.pollFirst();
        }

        if (swingPts.size() >= 2 && (!mc.options.getCameraType().isFirstPerson() || m.firstPerson.on())) {
            PoseStack.Pose pose = ps.last();
            VertexConsumer vc = ctx.consumers().getBuffer(Fx.type(Fx.Tex.SWING, false));
            SwingPt prev = null;

            for (SwingPt s : swingPts) {
                if (s.in() == null) {
                    prev = null;
                } else {
                    if (prev != null) {
                        float a0 = 1.0F - (float) Math.min(1.0, (double) (now - prev.t()) / life);
                        float a1 = 1.0F - (float) Math.min(1.0, (double) (now - s.t()) / life);
                        int c0 = straight(m.colourAt(prev.frac()), a0 * 0.95F);
                        int c1 = straight(m.colourAt(s.frac()), a1 * 0.95F);
                        Vec3 o0 = prev.out().subtract(cam);
                        Vec3 i0 = prev.in().subtract(cam);
                        Vec3 o1 = s.out().subtract(cam);
                        Vec3 i1 = s.in().subtract(cam);
                        quad4(vc, pose, o0, i0, i1, o1, a0, a1, c0, c1);
                        quad4(vc, pose, o1, i1, i0, o0, a1, a0, c1, c0);
                    }

                    prev = s;
                }
            }
        }
    }

    private static int straight(int c, float a) {
        return (int) (Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | c & 0xFFFFFF;
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
        Fx.v(vc, pose, (float) a0.x, (float) a0.y, (float) a0.z, u0, 0.0F, c0);
        Fx.v(vc, pose, (float) b0.x, (float) b0.y, (float) b0.z, u0, 1.0F, c0);
        Fx.v(vc, pose, (float) b1.x, (float) b1.y, (float) b1.z, u1, 1.0F, c1);
        Fx.v(vc, pose, (float) a1.x, (float) a1.y, (float) a1.z, u1, 0.0F, c1);
    }

    static int alpha(int c, float a) {
        int r = (int) (Math.max(0.0F, Math.min(1.0F, a)) * ((float) (c >>> 24) / 255.0F) * 255.0F) << 24 | c & 0xFFFFFF;
        return Fx.additiveOk() ? Fx.pm(r) : r;
    }

    private static record SwingPt(Vec3 in, Vec3 out, long t, float frac) {}
}
