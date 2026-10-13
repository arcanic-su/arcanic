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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Trajectories for things already thrown - ender pearls, snowballs, eggs, potions - by you or anyone else. */
public final class Trajectories extends Module {
    private final Setting.Mode which =
            add(new Setting.Mode("which", "Что показывать", "Всё брошенное", "Всё брошенное", "Только жемчуг"));
    private final Setting.Bool mine = add(new Setting.Bool("mine", "Свои броски", true));
    private final Setting.Bool others = add(new Setting.Bool("others", "Чужие броски", true));
    private final Setting.Colour pathColour = add(new Setting.Colour("path_color", "Цвет пути", false, 0xFF3D1466));
    private final Setting.Colour fillColour =
            add(new Setting.Colour("fill_color", "Цвет заполнения", false, 0xFFC264FF));
    private final Setting.Num width = add(new Setting.Num("width", "Толщина", 1.5, .5, 4, .25, " px"));
    private final Setting.Bool marker = add(new Setting.Bool("marker", "Метка падения", true));
    private final Setting.Bool ghost = add(new Setting.Bool("ghost", "Призрак у жемчуга", true));
    private final Setting.Bool label = add(new Setting.Bool("label", "Время до падения", true));
    private final Setting.Bool walls = add(new Setting.Bool("walls", "Видно сквозь стены", true));

    public Trajectories() {
        super(
                "trajectories",
                "Траектории",
                "Брошенный жемчуг, снежок или зелье: путь до места падения заполняется по мере полёта",
                Category.COMBAT,
                false);
    }

    /** The rest of a flight as simulated from the projectile's current position and speed. */
    private record Ahead(List<Vec3> pts, Vec3 end, Direction face, Entity target, int ticks) {}

    private static final class Track {
        final boolean pearl;
        final long born = System.nanoTime();
        final List<Vec3> trail = new ArrayList<>();
        Vec3 head, mark, markN = new Vec3(0, 1, 0);
        Ahead ahead;
        Entity target;
        float remain, shown; // seconds left (smoothed), how much of the line is filled (smoothed, 0..1)
        long dead = -1;
        int lastTick = -1;
        float yaw; // the thrower's facing, for the ghost at a pearl's landing spot
        // the prediction is redone once per game tick, never between frames

        Track(boolean pearl) {
            this.pearl = pearl;
        }
    }

    private static final long FADE_NS = 850_000_000L;
    private final Map<Integer, Track> tracks = new HashMap<>();
    private long lastNanos = System.nanoTime();

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static float back(float t) {
        t = Math.max(0, Math.min(1, t));
        float c = 1.70158f;
        return 1 + (c + 1) * (float) Math.pow(t - 1, 3) + c * (float) Math.pow(t - 1, 2);
    }

    private static int alpha(int c, float a) {
        return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (c & 0xFFFFFF);
    }

    // drag, move)
    private Ahead simulate(Vec3 pos, Vec3 v, double gravity, Entity self, Entity owner, List<Entity> candidates) {
        var level = mc().level;
        List<Vec3> pts = new ArrayList<>();
        pts.add(pos);
        for (int t = 1; t <= 300; t++) {
            boolean water = level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
            v = v.add(0, -gravity, 0).scale(water ? .8 : .99);
            Vec3 next = pos.add(v);
            BlockHitResult hit =
                    level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
            if (hit.getType() != HitResult.Type.MISS) next = hit.getLocation();
            Entity target = null;
            double best = pos.distanceToSqr(next);
            AABB seg = new AABB(pos, next).inflate(.3);
            for (Entity e : candidates) {
                if (e == self || e == owner || !e.getBoundingBox().intersects(seg)) continue;
                var c = e.getBoundingBox().inflate(.3).clip(pos, next);
                if (c.isPresent() && pos.distanceToSqr(c.get()) < best) {
                    best = pos.distanceToSqr(c.get());
                    target = e;
                    next = c.get();
                }
            }
            pts.add(next);
            if (target != null) return new Ahead(pts, next, null, target, t);
            if (hit.getType() != HitResult.Type.MISS) return new Ahead(pts, next, hit.getDirection(), null, t);
            pos = next;
            if (pos.y < level.getMinY() - 8) break;
        }
        return new Ahead(pts, pts.get(pts.size() - 1), null, null, 300);
    }

    private void update(float pt, long now, float dt) {
        Player me = mc().player;
        Set<Integer> seen = new HashSet<>();
        List<Entity> candidates = null;
        float k = 1 - (float) Math.exp(-dt * 16);
        for (Entity e : mc().level.entitiesForRendering()) {
            if (!(e instanceof ThrowableItemProjectile p) || e.isRemoved() || e.distanceToSqr(me) > 128 * 128) continue;
            boolean pearl = e instanceof ThrownEnderpearl;
            if (which.is("Только жемчуг") && !pearl) continue;
            boolean own = p.getOwner() == me;
            if (own ? !mine.on() : !others.on()) continue;
            if (candidates == null)
                candidates = mc().level
                        .getEntities(
                                me,
                                me.getBoundingBox().inflate(160),
                                x -> !x.isSpectator() && x.isPickable() && !(x instanceof ThrowableItemProjectile));
            Track t = tracks.get(e.getId());
            Vec3 at = e.getPosition(pt);
            if (t == null) {
                t = new Track(pearl);
                t.trail.add(at);
                tracks.put(e.getId(), t);
            }
            if (t.dead >= 0) continue;
            seen.add(e.getId());
            t.head = at;
            t.yaw = p.getOwner() != null ? p.getOwner().getYRot() : e.getYRot();
            if (t.trail.get(t.trail.size() - 1).distanceToSqr(at) > .01 && t.trail.size() < 600) t.trail.add(at);
            List<Entity> cands = new ArrayList<>(candidates);
            if (p.getOwner() != null && p.getOwner() != me) cands.add(me);
            // predict from the real tick position: the interpolated one made the path wobble
            if (t.ahead == null || e.tickCount != t.lastTick) {
                t.lastTick = e.tickCount;
                t.ahead = simulate(e.position(), e.getDeltaMovement(), e.getGravity(), e, p.getOwner(), cands);
            }
            Vec3 end = t.ahead.target != null ? t.ahead.target.position() : t.ahead.end;
            Vec3 n = t.ahead.face != null ? Vec3.atLowerCornerOf(t.ahead.face.getUnitVec3i()) : new Vec3(0, 1, 0);
            t.mark = t.mark == null ? end : t.mark.lerp(end, k);
            t.markN = t.markN.lerp(n, k).normalize();
            t.target = t.ahead.target;
            float r = t.ahead.ticks / 20f;
            t.remain = t.remain == 0 ? r : t.remain + (r - t.remain) * k;
        }
        for (var it = tracks.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Track t = en.getValue();
            if (t.dead < 0 && !seen.contains(en.getKey())) { // it has landed: the line runs to the mark, fully filled
                t.dead = now;
                if (t.mark != null && t.target == null) t.trail.add(t.mark);
                t.head = null;
                t.ahead = null;
            }
            if (t.dead >= 0 && now - t.dead > FADE_NS) it.remove();
        }
    }

    private static float alphaOf(Track t, long now) {
        float in = smooth((now - t.born) / 220e6f);
        float out = t.dead < 0 ? 1 : 1 - smooth((now - t.dead) / (float) FADE_NS);
        return in * out;
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        long now = System.nanoTime();
        float dt = Math.min(.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        update(pt, now, dt);
        if (tracks.isEmpty()) return;

        PoseStack.Pose pose = ctx.matrices().last();
        double fov = Math.toRadians(Projection.fov());
        float perPx =
                (float) (2 * Math.tan(fov / 2) / Math.max(1, mc().getWindow().getHeight()));
        boolean top = walls.on();
        float k = 1 - (float) Math.exp(-dt * 14);

        // what has been flown (the projectile's own recorded path) and what is still ahead (the prediction)
        VertexConsumer soft = ctx.consumers().getBuffer(Fx.soft(top));
        int dark = pathColour.get(), lit = fillColour.get();
        float px = width.f() + .5f;
        for (Track t : tracks.values()) {
            float a = alphaOf(t, now);
            if (a < .005f) continue;
            List<Vec3> flownPts = new ArrayList<>(t.trail);
            if (t.head != null) flownPts.add(t.head);
            List<Vec3> trail = spline(flownPts);
            double done = length(trail);
            // the road ahead: a thin dark line from the projectile to the mark
            if (t.dead < 0 && t.head != null && t.ahead != null && t.ahead.pts.size() > 1) {
                List<Vec3> aheadPts = new ArrayList<>();
                aheadPts.add(t.head);
                for (Vec3 v : t.ahead.pts.subList(1, t.ahead.pts.size()))
                    if (v.distanceToSqr(t.head) > .04 || aheadPts.size() > 1) aheadPts.add(v);
                List<Vec3> ahead = spline(aheadPts);
                double total = done + length(ahead);
                float target = total < 1e-3 ? 1 : (float) (done / total);
                t.shown = t.shown == 0 ? target : t.shown + (target - t.shown) * k;
                strip(soft, pose, cam, ahead, perPx, d -> px, d -> alpha(dark, a * .85f * smooth((float) (d / .6))));
            } else t.shown = Math.min(1, t.shown + (1 - t.shown) * k);
            // the flown part as a thin trail that fades towards the throw
            double len = Math.max(1e-3, done);
            strip(soft, pose, cam, trail, perPx, d -> px * (.45f + .75f * (float) (d / len)), d -> {
                float f = (float) (d / len);
                return alpha(Fx.lerp(Fx.lerp(dark, lit, .55f), lit, f), a * (.14f + .86f * (float) Math.pow(f, 1.25)));
            });
            if (marker.on()) ring(soft, pose, cam, t, a, perPx, now);
            if (ghost.on() && t.pearl && t.target == null && t.mark != null) ghost(soft, pose, cam, t, a, now);
        }
    }

    private void ghost(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Track t, float a, long now) {
        float time = (now - t.born) / 1e9f;
        float pulse = .5f + .5f * (float) Math.sin(time * 5.5f);
        float grow = smooth((now - t.born) / 300e6f);
        float k = (1 / 16f) * (.94f + .06f * grow + .025f * pulse);
        int c = alpha(Fx.lerp(fillColour.get(), 0xFFFFFFFF, .18f * pulse), a * (.22f + .2f * pulse));
        double yaw = Math.toRadians(-t.yaw);
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        Vec3 o = (t.dead >= 0 && !t.trail.isEmpty() ? t.trail.get(t.trail.size() - 1) : t.mark).subtract(cam);
        float[][] parts = {
            {-4, 0, -2, 0, 12, 2},
            {0, 0, -2, 4, 12, 2},
            {-4, 12, -2, 4, 24, 2},
            {-8, 12, -2, -4, 24, 2},
            {4, 12, -2, 8, 24, 2},
            {-4, 24, -4, 4, 32, 4}
        };
        for (float[] b : parts) box(vc, pose, o, cos, sin, k, b, c);
    }

    private static void box(
            VertexConsumer vc, PoseStack.Pose pose, Vec3 o, float cos, float sin, float k, float[] b, int c) {
        float x0 = b[0], y0 = b[1], z0 = b[2], dx = b[3] - b[0], dy = b[4] - b[1], dz = b[5] - b[2];
        float[][] faces = { // origin, u, v with u × v pointing out of the box
            {x0 + dx, y0, z0, 0, dy, 0, 0, 0, dz}, {x0, y0, z0, 0, 0, dz, 0, dy, 0},
            {x0, y0 + dy, z0, 0, 0, dz, dx, 0, 0}, {x0, y0, z0, dx, 0, 0, 0, 0, dz},
            {x0, y0, z0 + dz, dx, 0, 0, 0, dy, 0}, {x0, y0, z0, 0, dy, 0, dx, 0, 0}
        };
        for (float[] f : faces) {
            corner(vc, pose, o, cos, sin, k, f[0], f[1], f[2], c);
            corner(vc, pose, o, cos, sin, k, f[0] + f[3], f[1] + f[4], f[2] + f[5], c);
            corner(vc, pose, o, cos, sin, k, f[0] + f[3] + f[6], f[1] + f[4] + f[7], f[2] + f[5] + f[8], c);
            corner(vc, pose, o, cos, sin, k, f[0] + f[6], f[1] + f[7], f[2] + f[8], c);
        }
    }

    private static void corner(
            VertexConsumer vc,
            PoseStack.Pose pose,
            Vec3 o,
            float cos,
            float sin,
            float k,
            float x,
            float y,
            float z,
            int c) {
        float rx = x * cos - z * sin, rz = x * sin + z * cos;
        Fx.v(vc, pose, (float) o.x + rx * k, (float) o.y + y * k, (float) o.z + rz * k, 0, .5f, c);
    }

    private static double length(List<Vec3> pts) {
        double s = 0;
        for (int i = 1; i < pts.size(); i++) s += pts.get(i).distanceTo(pts.get(i - 1));
        return s;
    }

    private static List<Vec3> spline(List<Vec3> p) {
        List<Vec3> pts = new ArrayList<>();
        for (Vec3 v : p) if (pts.isEmpty() || pts.get(pts.size() - 1).distanceToSqr(v) > 1e-6) pts.add(v);
        if (pts.size() < 3) return pts;
        List<Vec3> out = new ArrayList<>();
        out.add(pts.get(0));
        for (int i = 0; i < pts.size() - 1; i++) {
            Vec3 p0 = pts.get(Math.max(0, i - 1)),
                    p1 = pts.get(i),
                    p2 = pts.get(i + 1),
                    p3 = pts.get(Math.min(pts.size() - 1, i + 2));
            int n = Math.max(1, (int) Math.ceil(p1.distanceTo(p2) / .18));
            for (int j = 1; j <= n; j++) {
                double t = j / (double) n, t2 = t * t, t3 = t2 * t;
                out.add(p0.scale(-.5 * t3 + t2 - .5 * t)
                        .add(p1.scale(1.5 * t3 - 2.5 * t2 + 1))
                        .add(p2.scale(-1.5 * t3 + 2 * t2 + .5 * t))
                        .add(p3.scale(.5 * t3 - .5 * t2)));
            }
        }
        return out;
    }

    private interface Shade {
        int at(double dist);
    }

    /**
     * One continuous camera-facing strip through the points (neighbouring pieces share their edge, so there are no
     * seams), {@code px} screen pixels wide at any distance.
     */
    private interface Width {
        float at(double dist);
    }

    private static void strip(
            VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, List<Vec3> pts, float perPx, float px, Shade shade) {
        strip(vc, pose, cam, pts, perPx, d -> px, shade);
    }

    private static void strip(
            VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, List<Vec3> pts, float perPx, Width width, Shade shade) {
        int n = pts.size();
        if (n < 2) return;
        Vec3[] rel = new Vec3[n], off = new Vec3[n];
        int[] col = new int[n];
        float[] wid = new float[n];
        double run = 0;
        for (int i = 0; i < n; i++) {
            rel[i] = pts.get(i).subtract(cam);
            if (i > 0) run += pts.get(i).distanceTo(pts.get(i - 1));
            col[i] = shade.at(run);
            wid[i] = width.at(run);
            // right at the eyes the line would sprawl across the screen: start it a little way out
            float near = smooth((float) ((rel[i].length() - 1.2) / 1.8));
            col[i] = ((Math.round((col[i] >>> 24) * near)) << 24) | (col[i] & 0xFFFFFF);
        }
        for (int i = 0; i < n; i++) {
            Vec3 dir = rel[Math.min(n - 1, i + 1)].subtract(rel[Math.max(0, i - 1)]);
            Vec3 side = dir.cross(rel[i]);
            if (side.lengthSqr() < 1e-12) side = i > 0 && off[i - 1] != null ? off[i - 1] : new Vec3(0, 1, 0);
            off[i] = side.normalize().scale(Math.max(.002, rel[i].length() * perPx * wid[i] / 2));
        }
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = rel[i], b = rel[i + 1], sa = off[i], sb = off[i + 1];
            Fx.v(vc, pose, (float) (a.x + sa.x), (float) (a.y + sa.y), (float) (a.z + sa.z), 0, 0, col[i]);
            Fx.v(vc, pose, (float) (a.x - sa.x), (float) (a.y - sa.y), (float) (a.z - sa.z), 0, 1, col[i]);
            Fx.v(vc, pose, (float) (b.x - sb.x), (float) (b.y - sb.y), (float) (b.z - sb.z), 1, 1, col[i + 1]);
            Fx.v(vc, pose, (float) (b.x + sb.x), (float) (b.y + sb.y), (float) (b.z + sb.z), 1, 0, col[i + 1]);
        }
    }

    private void ring(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Track t, float a, float perPx, long now) {
        if (t.mark == null) return;
        Vec3 n = t.markN;
        Vec3 u = Math.abs(n.y) > .7
                ? new Vec3(1, 0, 0)
                : n.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 w = n.cross(u).normalize();
        Vec3 c = t.mark.add(n.scale(.02));
        float pop = back(Math.min(1, (now - t.born) / 380e6f));
        float landed = t.dead < 0 ? 0 : (now - t.dead) / (float) FADE_NS;
        double base = t.target != null ? Math.max(.42, t.target.getBbWidth() * .75) : .4;
        double r = base * pop + .45 * smooth(landed);
        float progress = t.dead >= 0 ? 1 : Math.max(0, Math.min(1, t.shown));
        int dark = pathColour.get(), lit = t.target != null ? 0xFFF87171 : fillColour.get();
        int glowCol = Fx.lerp(dark, lit, .35f + .65f * progress);
        // the pool of light: bright in the middle, brighter as the projectile closes in, a flash on landing
        float flash = landed > 0 ? (1 - landed) * 1.6f : 0;
        float pool = a * Math.min(1, .16f + .2f * progress + .25f * flash);
        double pr = r * 1.55;
        int centre = alpha(glowCol, pool), edge = alpha(glowCol, 0);
        for (int i = 0; i < 48; i++) {
            double a0 = i * Math.PI / 24, a1 = (i + 1) * Math.PI / 24;
            fan(
                    vc,
                    pose,
                    cam,
                    c,
                    c.add(u.scale(Math.cos(a0) * pr)).add(w.scale(Math.sin(a0) * pr)),
                    c.add(u.scale(Math.cos(a1) * pr)).add(w.scale(Math.sin(a1) * pr)),
                    centre,
                    edge);
        }
        Vec3 c2 = c.add(n.scale(.004));
        // the progress ring, filling clockwise from the top
        circle(
                vc,
                pose,
                cam,
                c2,
                u,
                w,
                r,
                perPx,
                width.f() + .75f,
                f -> alpha(Fx.lerp(dark, lit, smooth((progress - f) / .06f + .5f)), a));
        // a faint inner ring and the centre dot
        circle(vc, pose, cam, c2, u, w, r * .55, perPx, Math.max(1, width.f() - .25f), f -> alpha(glowCol, a * .45f));
        double dr = .05 * pop;
        int dot = alpha(Fx.lerp(0xFFFFFFFF, lit, .3f), a * (.5f + .5f * progress));
        for (int i = 0; i < 20; i++) {
            double a0 = i * Math.PI / 10, a1 = (i + 1) * Math.PI / 10;
            fan(
                    vc,
                    pose,
                    cam,
                    c2,
                    c2.add(u.scale(Math.cos(a0) * dr)).add(w.scale(Math.sin(a0) * dr)),
                    c2.add(u.scale(Math.cos(a1) * dr)).add(w.scale(Math.sin(a1) * dr)),
                    dot,
                    dot);
        }
    }

    private interface Around {
        int at(float f);
    }

    private static void circle(
            VertexConsumer vc,
            PoseStack.Pose pose,
            Vec3 cam,
            Vec3 c,
            Vec3 u,
            Vec3 w,
            double r,
            float perPx,
            float px,
            Around shade) {
        List<Vec3> pts = new ArrayList<>();
        int steps = 72;
        for (int i = 0; i <= steps; i++) {
            double ang = Math.PI / 2 - i * Math.PI * 2 / steps;
            pts.add(c.add(u.scale(Math.cos(ang) * r)).add(w.scale(Math.sin(ang) * r)));
        }
        double len = length(pts);
        strip(vc, pose, cam, pts, perPx, px, d -> shade.at((float) (d / len)));
    }

    private static void fan(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, int ca, int cbc) {
        Vec3 ra = a.subtract(cam), rb = b.subtract(cam), rc = c.subtract(cam);
        Fx.v(vc, pose, (float) ra.x, (float) ra.y, (float) ra.z, 0, .5f, ca);
        Fx.v(vc, pose, (float) rb.x, (float) rb.y, (float) rb.z, 0, .5f, cbc);
        Fx.v(vc, pose, (float) rc.x, (float) rc.y, (float) rc.z, 0, .5f, cbc);
        Fx.v(vc, pose, (float) rc.x, (float) rc.y, (float) rc.z, 0, .5f, cbc);
    }

    void hud(GuiGraphics g) {
        if (!label.on() || tracks.isEmpty() || mc().screen != null) return;
        long now = System.nanoTime();
        Vec3 me = mc().player.position();
        List<Track> list = new ArrayList<>();
        for (Track t : tracks.values()) if (t.mark != null && t.dead < 0) list.add(t);
        list.sort((x, y) -> Double.compare(x.mark.distanceToSqr(me), y.mark.distanceToSqr(me)));
        int fs = Math.max(11, Math.round(Px.height() / 1080f * 13));
        for (int i = 0; i < Math.min(4, list.size()); i++) {
            Track t = list.get(i);
            float a = alphaOf(t, now);
            double lift = t.target != null ? t.target.getBbHeight() + .45 : .5;
            double[] s = Projection.toScreen(t.mark.add(0, lift, 0), Px.width(), Px.height());
            if (s == null || s[0] < -80 || s[0] > Px.width() + 80 || s[1] < -40 || s[1] > Px.height() + 40) continue;
            String time = String.format(Locale.ROOT, "%.1f с", Math.max(0, t.remain));
            String text = t.target != null
                    ? ChatFormatting.stripFormatting(t.target.getName().getString()) + " · " + time
                    : t.pearl ? Lang.tr("Телепорт") + " · " + time : time;
            int h = Math.round(fs * 2f), w = Px.textWidth(text, Px.Weight.SEMIBOLD, fs) + fs * 2;
            int x = (int) s[0] - w / 2, y = (int) s[1] - h;
            float old = Px.fade;
            Px.fade = old * a;
            int accent = t.target != null ? 0xFFF87171 : fillColour.get();
            Px.shadow(g, x, y, w, h, fs, 0x48000000);
            Px.round(g, x, y, w, h, h / 2, 0xE60C0B14);
            Px.outline(g, x, y, w, h, h / 2, (accent & 0xFFFFFF) | 0x70000000);
            Px.textCenter(g, text, x + w / 2, y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, 0xFFF3EFF8);
            Px.fade = old;
        }
    }
}
