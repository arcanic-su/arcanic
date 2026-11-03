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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.arcanic.visuals.fx.impact.ImpactDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class HitFx {
    static final int GLASS = 0;
    static final int SAKURA = 1;
    static final int STARDUST = 2;
    static final int BUTTERFLIES = 3;
    static final int BUBBLES = 4;
    private static final float[] LIFE = {1.05F, 2.6F, 1.4F, 2.0F, 2.1F};
    private static final Identifier PETAL = Identifier.fromNamespaceAndPath("arcanic", "textures/targetesp/sakura.png");
    private static final Identifier WING = Identifier.fromNamespaceAndPath("arcanic", "textures/hit/wing.png");
    private static final Identifier BUBBLE = Identifier.fromNamespaceAndPath("arcanic", "textures/hit/bubble.png");
    private static final Identifier SHINE = Identifier.fromNamespaceAndPath("arcanic", "textures/hit/shine.png");
    private static final List<Hit> WORLD = new ArrayList<>();
    private static final List<Hit> STUDIO = new ArrayList<>();
    private static ClientLevel level;

    private HitFx() {}

    static Show.Pal own(int style) {
        return switch (style) {
            case 1 -> new Show.Pal(-6416, -23609, 0xFFE0627F);
            case 2 -> new Show.Pal(-2346, -11410, 0xFFB98CFF);
            case 3 -> new Show.Pal(0xFFE2F5FF, 0xFF6FC3FF, 0xFF8E6BFF);
            case 4 -> new Show.Pal(-3334, 0xFF9DE8FF, 0xFFC8A8FF);
            default -> new Show.Pal(0xFFF4FDFF, 0xFFA9ECFF, 0xFF6AB8FF);
        };
    }

    private static long now(boolean studio) {
        return studio ? Studio.clockMs() : System.currentTimeMillis();
    }

    static Ground ground(LivingEntity l) {
        ClientLevel lvl = Minecraft.getInstance().level;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (lvl != null) {
            for (int i = 0; i < 8; i++) {
                pos.set(l.getBlockX(), Mth.floor(l.getY() + 0.05) - i, l.getBlockZ());
                BlockState st = lvl.getBlockState(pos);
                VoxelShape shape = st.getCollisionShape(lvl, pos);
                if (!st.isAir() && !shape.isEmpty()) {
                    double top = (double) pos.getY() + shape.max(Axis.Y);
                    if (!(top > l.getY() + 0.05)) {
                        int col = st.getMapColor(lvl, pos).col;
                        boolean model = st.getRenderShape() == RenderShape.MODEL
                                && st.getFluidState().isEmpty();
                        return new Ground(
                                (float) top,
                                model ? st : Blocks.STONE.defaultBlockState(),
                                LevelRenderer.getLightColor(lvl, pos.above()),
                                col != 0 ? 0xFF000000 | col : 0xFF8C8478);
                    }
                }
            }
        }

        return new Ground((float) l.getY() - 8.0F, Blocks.STONE.defaultBlockState(), 15728880, 0xFF8C8478);
    }

    static void spawn(
            int style,
            Vec3 at,
            Vec3 dir,
            AABB body,
            Ground ground,
            boolean crit,
            float scale,
            Show.Pal pal,
            boolean sound,
            boolean studio) {
        Hit h = new Hit(style, at, dir, body, ground, crit, scale * (crit ? 1.3F : 1.0F), pal, sound, studio);
        List<Hit> list = studio ? STUDIO : WORLD;
        list.add(h);

        while (list.size() > 10) {
            list.remove(0);
        }

        switch (style) {
            case 0 -> web(h);
            case 1 -> sakuraBurst(h);
            case 2 -> stardustBurst(h);
            case 3 -> butterflyBurst(h);
            case 4 -> bubbleBurst(h);
        }
    }

    private static Vec3 randomDir(Random r) {
        return (new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian())).normalize();
    }

    private static Vec3 back(Hit h, double up) {
        Vec3 d = randomDir(h.rnd);
        double into = d.dot(h.dir);
        if (into > 0.0) {
            d = d.subtract(h.dir.scale(2.0 * into));
        }

        return d.add(0.0, up, 0.0).normalize();
    }

    private static void hear(Hit h, SoundEvent s, float vol, float pitch) {
        if (h.sound && s != null) {
            if (h.studio) {
                ImpactDraw.studioSound(s, vol, pitch);
            } else {
                Show.play(h.at, s, vol, pitch);
            }
        }
    }

    static void clear() {
        WORLD.clear();
    }

    static void clearStudio() {
        STUDIO.clear();
    }

    static void world(PoseStack ps) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            level = mc.level;
            WORLD.clear();
        }

        if (!WORLD.isEmpty() && mc.level != null) {
            draw(WORLD, Show.view(ps));
        }
    }

    static void studio(Show.View v) {
        if (!STUDIO.isEmpty()) {
            draw(STUDIO, v);
        }
    }

    private static void draw(List<Hit> hits, Show.View v) {
        Iterator<Hit> it = hits.iterator();

        while (it.hasNext()) {
            Hit h = (Hit) it.next();
            float t = (float) (now(h.studio) - h.start) / 1000.0F;
            if (!(t > LIFE[h.style]) && !(t < -0.05F)) {
                t = Math.max(0.0F, t);
                float dt = Math.max(0.0F, Math.min(0.1F, t - h.last));
                h.last = t;
                switch (h.style) {
                    case 1:
                        sakura(h, t, dt, v);
                        break;
                    case 2:
                        stardust(h, t, dt, v);
                        break;
                    case 3:
                        butterflies(h, t, dt, v);
                        break;
                    case 4:
                        bubbles(h, t, dt, v);
                        break;
                    default:
                        glass(h, t, dt, v);
                }
            } else {
                it.remove();
            }
        }
    }

    private static void emit(Hit h, float dt, float rate, Runnable one) {
        h.owed += dt * rate;

        while (h.owed >= 1.0F) {
            --h.owed;
            one.run();
        }
    }

    private static Vector3f plane(Show.View v, Vector3f p, float u, float w) {
        return (new Vector3f(p)).add((new Vector3f(v.left())).mul(u)).add((new Vector3f(v.up())).mul(w));
    }

    private static void sakuraBurst(Hit h) {
        float s = h.scale;

        for (int i = 0; i < (h.crit ? 18 : 11); i++) {
            Vec3 d = back(h, 0.6);
            petal(
                    h,
                    h.at,
                    d.scale((1.2 + h.rnd.nextDouble() * 1.2) * (double) s),
                    (0.06F + h.rnd.nextFloat() * 0.035F) * s);
        }

        if (h.crit) {
            Vec3 c = h.body.getCenter();
            double r = Math.max(h.body.getXsize(), h.body.getZsize()) * 0.6;

            for (int i = 0; i < 12; i++) {
                double a = (double) i * Math.PI * 2.0 / 12.0;
                Vec3 at = new Vec3(c.x + Math.cos(a) * r, h.at.y, c.z + Math.sin(a) * r);
                Vec3 vel = (new Vec3(
                                -Math.sin(a) * 2.0 + Math.cos(a) * 0.8, 0.5, Math.cos(a) * 2.0 + Math.sin(a) * 0.8))
                        .scale((double) s);
                petal(h, at, vel, (0.06F + h.rnd.nextFloat() * 0.03F) * s);
            }
        }

        hear(h, SoundEvents.CHERRY_LEAVES_BREAK, 0.4F, 1.4F);
    }

    private static void petal(Hit h, Vec3 at, Vec3 vel, float size) {
        Petal p = new Petal((new Vector3f(h.rnd.nextFloat() - 0.5F, h.rnd.nextFloat() - 0.5F, h.rnd.nextFloat() - 0.5F))
                .normalize());
        p.x = at.x;
        p.y = at.y;
        p.z = at.z;
        p.vx = vel.x;
        p.vy = vel.y;
        p.vz = vel.z;
        p.angle = h.rnd.nextFloat() * 6.0F;
        p.spin = (2.5F + h.rnd.nextFloat() * 3.5F) * (float) (h.rnd.nextBoolean() ? 1 : -1);
        p.size = size;
        p.phase = h.rnd.nextFloat() * 6.0F;
        p.turn = h.rnd.nextFloat() * 6.0F;
        h.petals.add(p);
    }

    private static void sakura(Hit h, float t, float dt, Show.View v) {
        Show.Pal c = h.pal;
        Vector3f at = v.at(h.at);
        if (t < 0.12F) {
            Show.sprite(
                    v, Show.ORB, at.x, at.y, at.z, 0.26F * h.scale, 0.0F, c.main(), 0.0F, 0.35F * (1.0F - t / 0.12F));
        }

        float end = 1.0F - Show.smooth((t - LIFE[1] + 0.5F) / 0.5F);
        Quaternionf q = new Quaternionf();
        Show.twice(v.pose(), PETAL, 0.95F * end, 0.2F * end, (e) -> {
            for (Petal p : h.petals) {
                float a = p.landed < 0.0F ? 1.0F : 1.0F - Show.smooth((t - p.landed - 0.5F) / 0.6F);
                if (!(a <= 0.01F)) {
                    int top = Show.alpha(c.light(), a);
                    int bottom = Show.alpha(c.main(), a);
                    float px = v.x(p.x);
                    float py = v.y(p.y);
                    float pz = v.z(p.z);
                    float r = p.size;
                    if (p.landed >= 0.0F) {
                        float cr = Mth.cos((double) p.turn) * r;
                        float sr = Mth.sin((double) p.turn) * r;
                        e.v(px - cr + sr, py, pz - sr - cr, 0.0F, 0.0F, top);
                        e.v(px - cr - sr, py, pz - sr + cr, 0.0F, 1.0F, bottom);
                        e.v(px + cr - sr, py, pz + sr + cr, 1.0F, 1.0F, bottom);
                        e.v(px + cr + sr, py, pz + sr - cr, 1.0F, 0.0F, top);
                    } else {
                        q.identity().rotateAxis(p.angle, p.axis);
                        Vector3f a0 = q.transform(new Vector3f(-r, r, 0.0F));
                        Vector3f a1 = q.transform(new Vector3f(-r, -r, 0.0F));
                        Vector3f a2 = q.transform(new Vector3f(r, -r, 0.0F));
                        Vector3f a3 = q.transform(new Vector3f(r, r, 0.0F));
                        e.v(px + a0.x, py + a0.y, pz + a0.z, 0.0F, 0.0F, top);
                        e.v(px + a1.x, py + a1.y, pz + a1.z, 0.0F, 1.0F, bottom);
                        e.v(px + a2.x, py + a2.y, pz + a2.z, 1.0F, 1.0F, bottom);
                        e.v(px + a3.x, py + a3.y, pz + a3.z, 1.0F, 0.0F, top);
                    }
                }
            }
        });

        for (Petal p : h.petals) {
            if (!(p.landed >= 0.0F)) {
                float drag = (float) Math.exp((double) (-2.2F * dt));
                p.vx = p.vx * (double) drag + (double) (Mth.sin((double) (t * 3.1F + p.phase)) * 0.55F * dt);
                p.vz = p.vz * (double) drag + (double) (Mth.cos((double) (t * 2.6F + p.phase)) * 0.55F * dt);
                p.vy = Math.max(-0.65, p.vy * Math.exp(-1.6 * (double) dt) - 0.9 * (double) dt);
                p.x += p.vx * (double) dt;
                p.y += p.vy * (double) dt;
                p.z += p.vz * (double) dt;
                p.angle += p.spin * dt;
                if (p.y <= (double) h.ground.y() + 0.012) {
                    p.y = (double) h.ground.y() + 0.012;
                    p.landed = t;
                }
            }
        }
    }

    private static void stardustBurst(Hit h) {
        Show.Pal c = h.pal;
        float s = h.scale;
        int[] cols = {c.light(), c.main(), c.deep()};

        for (int i = 0; i < (h.crit ? 30 : 20); i++) {
            Vec3 d = back(h, 0.3).scale((1.0 + h.rnd.nextDouble() * 1.6) * (double) s);
            int col = cols[i % 3];
            Fx.spawn()
                    .at(h.at)
                    .vel(d.x, d.y, d.z)
                    .tex(Fx.Tex.SPARKLE)
                    .size((0.05F + h.rnd.nextFloat() * 0.05F) * s, 0.0F)
                    .spin(h.rnd.nextFloat() * 6.0F, (h.rnd.nextFloat() - 0.5F) * 4.0F)
                    .life(0.7F + h.rnd.nextFloat() * 0.5F)
                    .drag(2.6F)
                    .gravity(-0.25F)
                    .twinkle(0.8F)
                    .colour(col, col & 0xFFFFFF);
        }

        for (int i = 0; i < 8; i++) {
            Vec3 d = back(h, 0.4).scale((0.5 + h.rnd.nextDouble()) * (double) s);
            Fx.spawn()
                    .at(h.at)
                    .vel(d.x, d.y, d.z)
                    .tex(Fx.Tex.GLOW)
                    .size(0.035F * s, 0.0F)
                    .life(0.9F + h.rnd.nextFloat() * 0.4F)
                    .drag(2.0F)
                    .gravity(-0.2F)
                    .twinkle(0.7F)
                    .colour(c.light(), c.main() & 0xFFFFFF);
        }

        hear(h, SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, h.crit ? 1.3F : 1.7F);
    }

    private static void stardust(Hit h, float t, float dt, Show.View v) {
        Show.Pal c = h.pal;
        float s = h.scale;
        Vector3f p = v.at(h.at);
        if (t < 0.08F) {
            Show.sprite(v, Show.ORB, p.x, p.y, p.z, 0.22F * s, 0.0F, c.light(), 0.0F, 0.45F * (1.0F - t / 0.08F));
        }

        if (t < 0.4F) {
            Vec3 centre = h.body.getCenter();
            double r = Math.max(h.body.getXsize(), h.body.getZsize()) * 0.5 + 0.18 * (double) s;
            double start = Math.atan2(h.at.z - centre.z, h.at.x - centre.x);
            int swirls = h.crit ? 2 : 1;
            emit(h, dt, (float) (75 * swirls), () -> {
                int k = h.rnd.nextInt(swirls);
                double a = start + (double) ((float) (k == 0 ? 1 : -1) * t * 15.0F);
                double y = h.at.y
                        + Math.sin((double) (t * 9.0F + (float) (k * 2))) * 0.08 * (double) s
                        + (k == 0 ? 0.0 : -0.12 * (double) s);
                Vec3 at = new Vec3(centre.x + Math.cos(a) * r, y, centre.z + Math.sin(a) * r);
                int col = h.rnd.nextInt(3) == 0 ? c.light() : (h.rnd.nextBoolean() ? c.main() : c.deep());
                Fx.spawn()
                        .at(at)
                        .vel(-Math.sin(a) * 0.3, 0.15 + h.rnd.nextDouble() * 0.2, Math.cos(a) * 0.3)
                        .tex(h.rnd.nextInt(3) == 0 ? Fx.Tex.GLOW : Fx.Tex.SPARKLE)
                        .size((0.04F + h.rnd.nextFloat() * 0.035F) * s, 0.0F)
                        .spin(h.rnd.nextFloat() * 6.0F, 2.0F)
                        .life(0.4F + h.rnd.nextFloat() * 0.25F)
                        .drag(1.5F)
                        .twinkle(0.7F)
                        .colour(col, col & 0xFFFFFF);
            });
        }
    }

    private static void butterflyBurst(Hit h) {
        Show.Pal c = h.pal;
        float s = h.scale;

        for (int i = 0; i < (h.crit ? 9 : 5); i++) {
            Vec3 d = back(h, 0.8).scale((1.5 + h.rnd.nextDouble() * 1.1) * (double) s);
            Fly f = new Fly();
            f.x = h.at.x;
            f.y = h.at.y;
            f.z = h.at.z;
            f.vx = d.x;
            f.vy = d.y;
            f.vz = d.z;
            f.yaw = (float) Math.atan2(d.z, d.x);
            f.phase = h.rnd.nextFloat() * 6.0F;
            f.rate = 17.0F + h.rnd.nextFloat() * 7.0F;
            f.size = (0.055F + h.rnd.nextFloat() * 0.025F) * s;
            f.life = 1.3F + h.rnd.nextFloat() * 0.6F;
            f.tint = i % 3;
            h.flies.add(f);
        }

        for (int i = 0; i < 7; i++) {
            Vec3 d = back(h, 0.5).scale((0.4 + h.rnd.nextDouble() * 0.6) * (double) s);
            Fx.spawn()
                    .at(h.at)
                    .vel(d.x, d.y, d.z)
                    .tex(Fx.Tex.GLOW)
                    .size(0.03F * s, 0.0F)
                    .life(0.6F + h.rnd.nextFloat() * 0.4F)
                    .drag(2.0F)
                    .gravity(-0.15F)
                    .twinkle(0.7F)
                    .colour(c.light(), c.main() & 0xFFFFFF);
        }

        hear(h, SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.6F);
    }

    private static int tint(Show.Pal c, int k, boolean outer) {
        return switch (k) {
            case 1 -> outer ? c.light() : c.main();
            case 2 -> outer ? c.main() : c.deep();
            default -> outer ? c.light() : c.deep();
        };
    }

    private static float shown(Fly f, float t) {
        return Show.smooth(t / 0.08F) * (1.0F - Show.smooth((t - f.life + 0.45F) / 0.45F));
    }

    private static void butterflies(Hit h, float t, float dt, Show.View v) {
        Show.Pal c = h.pal;
        float s = h.scale;
        Vector3f at = v.at(h.at);
        if (t < 0.1F) {
            Show.sprite(v, Show.ORB, at.x, at.y, at.z, 0.24F * s, 0.0F, c.light(), 0.0F, 0.35F * (1.0F - t / 0.1F));
        }

        float k = 1.0F - (float) Math.exp((double) (-2.5F * dt));

        for (Fly f : h.flies) {
            float beat = t * f.rate + f.phase;
            f.yaw += Mth.sin((double) (t * 2.1F + f.phase)) * 2.2F * dt;
            double wx = (double) Mth.cos((double) f.yaw) * 0.75 * (double) s;
            double wz = (double) Mth.sin((double) f.yaw) * 0.75 * (double) s;
            double wy = (0.45 + 0.5 * (double) Mth.sin((double) beat)) * (double) s;
            f.vx += (wx - f.vx) * (double) k;
            f.vy += (wy - f.vy) * (double) k;
            f.vz += (wz - f.vz) * (double) k;
            f.x += f.vx * (double) dt;
            f.y += f.vy * (double) dt;
            f.z += f.vz * (double) dt;
            f.fwd.set((float) f.vx, (float) f.vy * 0.4F, (float) f.vz);
            if (f.fwd.lengthSquared() < 1.0E-6F) {
                f.fwd.set(Mth.cos((double) f.yaw), 0.0F, Mth.sin((double) f.yaw));
            }

            f.fwd.normalize();
            f.right.set(f.fwd).cross(0.0F, 1.0F, 0.0F);
            if (f.right.lengthSquared() < 1.0E-6F) {
                f.right.set(1.0F, 0.0F, 0.0F);
            }

            f.right.normalize();
            f.up.set(f.right).cross(f.fwd).normalize();
            f.wing = 0.5F + 0.78F * Mth.sin((double) beat);
            if (h.rnd.nextFloat() < dt * 9.0F && shown(f, t) > 0.3F) {
                int col = tint(c, f.tint, true);
                Fx.spawn()
                        .at(new Vec3(f.x, f.y, f.z))
                        .vel(h.rnd.nextGaussian() * 0.05, -0.12, h.rnd.nextGaussian() * 0.05)
                        .tex(Fx.Tex.GLOW)
                        .size(0.022F * s, 0.0F)
                        .life(0.45F + h.rnd.nextFloat() * 0.3F)
                        .drag(1.0F)
                        .twinkle(0.6F)
                        .colour(col, col & 0xFFFFFF);
            }
        }

        Show.twice(v.pose(), WING, 0.95F, 0.28F, (e) -> {
            for (Fly f : h.flies) {
                float a = shown(f, t);
                if (!(a <= 0.01F)) {
                    float sz = f.size * (0.4F + 0.6F * Show.easeOut(t / 0.12F));
                    float px = v.x(f.x);
                    float py = v.y(f.y);
                    float pz = v.z(f.z);
                    int in = Show.alpha(tint(c, f.tint, false), a);
                    int out = Show.alpha(tint(c, f.tint, true), a);
                    float hx = f.fwd.x * sz;
                    float hy = f.fwd.y * sz;
                    float hz = f.fwd.z * sz;

                    for (int side = -1; side <= 1; side += 2) {
                        float cw = Mth.cos((double) f.wing) * (float) side * sz * 2.0F;
                        float sw = Mth.sin((double) f.wing) * sz * 2.0F;
                        float wx = f.right.x * cw + f.up.x * sw;
                        float wy = f.right.y * cw + f.up.y * sw;
                        float wz = f.right.z * cw + f.up.z * sw;
                        e.v(px + hx, py + hy, pz + hz, 0.0F, 0.0F, in);
                        e.v(px - hx, py - hy, pz - hz, 0.0F, 1.0F, in);
                        e.v(px - hx + wx, py - hy + wy, pz - hz + wz, 1.0F, 1.0F, out);
                        e.v(px + hx + wx, py + hy + wy, pz + hz + wz, 1.0F, 0.0F, out);
                    }
                }
            }
        });
        Show.twice(v.pose(), Show.ORB, 0.9F, 0.35F, (e) -> {
            for (Fly f : h.flies) {
                float a = shown(f, t);
                if (!(a <= 0.01F)) {
                    float sz = f.size * (0.4F + 0.6F * Show.easeOut(t / 0.12F));
                    float px = v.x(f.x);
                    float py = v.y(f.y);
                    float pz = v.z(f.z);
                    Vector3f across = (new Vector3f(f.fwd)).cross(v.toward(px, py, pz));
                    if (!(across.lengthSquared() < 1.0E-8F)) {
                        across.normalize().mul(sz * 0.3F);
                        float lx = f.fwd.x * sz * 1.2F;
                        float ly = f.fwd.y * sz * 1.2F;
                        float lz = f.fwd.z * sz * 1.2F;
                        int col = Show.alpha(c.light(), a);
                        e.v(px + lx - across.x, py + ly - across.y, pz + lz - across.z, 0.0F, 0.0F, col);
                        e.v(px - lx - across.x, py - ly - across.y, pz - lz - across.z, 0.0F, 1.0F, col);
                        e.v(px - lx + across.x, py - ly + across.y, pz - lz + across.z, 1.0F, 1.0F, col);
                        e.v(px + lx + across.x, py + ly + across.y, pz + lz + across.z, 1.0F, 0.0F, col);
                    }
                }
            }
        });
    }

    private static void bubbleBurst(Hit h) {
        float s = h.scale;

        for (int i = 0; i < (h.crit ? 12 : 7); i++) {
            Vec3 d = back(h, 0.45).scale((0.9 + h.rnd.nextDouble() * 1.3) * (double) s);
            Bubble b = new Bubble();
            b.x = h.at.x;
            b.y = h.at.y;
            b.z = h.at.z;
            b.vx = d.x;
            b.vy = d.y;
            b.vz = d.z;
            float k = h.rnd.nextFloat();
            b.r = (0.04F + k * k * 0.07F) * s;
            b.phase = h.rnd.nextFloat() * 6.0F;
            b.life = 0.7F + h.rnd.nextFloat() * 1.1F;
            h.bubbles.add(b);
        }

        hear(h, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.5F, 1.2F);
    }

    private static int film(Show.Pal c, float k) {
        float f = (k / 6.2832F % 1.0F + 1.0F) % 1.0F * 3.0F;
        int i = (int) f;
        int[] cols = {c.light(), c.main(), c.deep()};
        return Fx.lerp(cols[i % 3], cols[(i + 1) % 3], f - (float) i);
    }

    private static void bubbles(Hit h, float t, float dt, Show.View v) {
        Show.Pal c = h.pal;

        for (Bubble b : h.bubbles) {
            if (!(b.popped >= 0.0F)) {
                if (t >= b.life) {
                    pop(h, b, t);
                } else {
                    float drag = (float) Math.exp((double) (-2.4F * dt));
                    b.vx = b.vx * (double) drag + (double) (Mth.sin((double) (t * 1.9F + b.phase)) * 0.25F * dt);
                    b.vz = b.vz * (double) drag + (double) (Mth.cos((double) (t * 1.6F + b.phase)) * 0.25F * dt);
                    b.vy = b.vy * (double) drag + (double) (0.5F * dt);
                    b.x += b.vx * (double) dt;
                    b.y += b.vy * (double) dt;
                    b.z += b.vz * (double) dt;
                }
            }
        }

        float grow = Show.backOut(t / 0.16F);
        float settle = 0.35F + 0.65F * (float) Math.exp((double) (-3.0F * t));
        Vector3f l = v.left();
        Vector3f u = v.up();
        Show.twice(v.pose(), BUBBLE, 0.85F, 0.35F, (e) -> {
            for (Bubble b : h.bubbles) {
                if (!(b.popped >= 0.0F)) {
                    float q = Mth.sin((double) (t * 20.0F + b.phase)) * 0.1F * settle;
                    float rx = b.r * grow * (1.0F + q);
                    float ry = b.r * grow * (1.0F - q);
                    float k = t * 2.4F + b.phase;
                    bubbleQuad(
                            e, v, b, l, u, rx, ry, film(c, k), film(c, k + 1.6F), film(c, k + 3.2F), film(c, k + 4.8F));
                }
            }
        });
        int light = Show.whiter(c.light(), 0.7F);
        Show.twice(v.pose(), SHINE, 0.9F, 0.3F, (e) -> {
            for (Bubble b : h.bubbles) {
                if (!(b.popped >= 0.0F)) {
                    float q = Mth.sin((double) (t * 20.0F + b.phase)) * 0.1F * settle;
                    bubbleQuad(
                            e,
                            v,
                            b,
                            l,
                            u,
                            b.r * grow * (1.0F + q),
                            b.r * grow * (1.0F - q),
                            light,
                            light,
                            light,
                            light);
                }
            }
        });

        for (Bubble b : h.bubbles) {
            if (!(b.popped < 0.0F) && !(t - b.popped > 0.14F)) {
                float k = (t - b.popped) / 0.14F;
                Show.sprite(
                        v,
                        Show.RING,
                        v.x(b.x),
                        v.y(b.y),
                        v.z(b.z),
                        b.r * (1.0F + 0.7F * Show.easeOut(k)) / 0.86F,
                        0.0F,
                        c.light(),
                        0.5F * (1.0F - k),
                        0.8F * (1.0F - k));
            }
        }
    }

    private static void bubbleQuad(
            Show.Emit e,
            Show.View v,
            Bubble b,
            Vector3f l,
            Vector3f u,
            float rx,
            float ry,
            int c0,
            int c1,
            int c2,
            int c3) {
        float px = v.x(b.x);
        float py = v.y(b.y);
        float pz = v.z(b.z);
        float lx = l.x * rx;
        float ly = l.y * rx;
        float lz = l.z * rx;
        float ux = u.x * ry;
        float uy = u.y * ry;
        float uz = u.z * ry;
        e.v(px + lx + ux, py + ly + uy, pz + lz + uz, 0.0F, 0.0F, c0);
        e.v(px + lx - ux, py + ly - uy, pz + lz - uz, 0.0F, 1.0F, c1);
        e.v(px - lx - ux, py - ly - uy, pz - lz - uz, 1.0F, 1.0F, c2);
        e.v(px - lx + ux, py - ly + uy, pz - lz + uz, 1.0F, 0.0F, c3);
    }

    private static void pop(Hit h, Bubble b, float t) {
        b.popped = t;
        if (h.pops++ < 4) {
            hear(h, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.22F, 1.5F + h.rnd.nextFloat() * 0.5F);
        }

        for (int i = 0; i < 6; i++) {
            Vec3 d = randomDir(h.rnd);
            double sp = 0.6 + h.rnd.nextDouble() * 0.6;
            Fx.spawn()
                    .at(new Vec3(b.x + d.x * (double) b.r, b.y + d.y * (double) b.r, b.z + d.z * (double) b.r))
                    .vel(d.x * sp, d.y * sp + 0.3, d.z * sp)
                    .tex(Fx.Tex.GLOW)
                    .size(0.025F * h.scale, 0.0F)
                    .life(0.25F + h.rnd.nextFloat() * 0.15F)
                    .drag(2.0F)
                    .gravity(3.0F)
                    .colour(h.pal.light(), h.pal.main() & 0xFFFFFF);
        }
    }

    private static void web(Hit h) {
        int spokes = 9;
        float s = h.scale;
        float[][][] pts = new float[spokes][5][];

        for (int k = 0; k < spokes; k++) {
            float a = (float)
                    ((double) k * Math.PI * 2.0 / (double) spokes + (double) ((h.rnd.nextFloat() - 0.5F) * 0.4F));
            float reach = (0.42F + 0.3F * h.rnd.nextFloat()) * s;

            for (int j = 0; j < 5; j++) {
                float f = (float) j / 4.0F;
                float side = j == 0 ? 0.0F : (h.rnd.nextFloat() - 0.5F) * 0.08F * s;
                float r = reach * f;
                pts[k][j] = new float[] {
                    Mth.cos((double) a) * r - Mth.sin((double) a) * side,
                    Mth.sin((double) a) * r + Mth.cos((double) a) * side
                };
            }
        }

        for (int k = 0; k < spokes; k++) {
            float[] us = new float[5];
            float[] ws = new float[5];

            for (int j = 0; j < 5; j++) {
                us[j] = pts[k][j][0];
                ws[j] = pts[k][j][1];
            }

            h.web.add(new float[][] {us, ws});
        }

        for (int ringAt : new int[] {1, 3}) {
            for (int k = 0; k < spokes; k++) {
                float[] a = pts[k][ringAt];
                float[] b = pts[(k + 1) % spokes][ringAt];
                float mu = (a[0] + b[0]) / 2.0F * (0.92F + h.rnd.nextFloat() * 0.08F);
                float mw = (a[1] + b[1]) / 2.0F * (0.92F + h.rnd.nextFloat() * 0.08F);
                h.web.add(new float[][] {{a[0], mu, b[0]}, {a[1], mw, b[1]}});
            }
        }

        h.web.add(new float[][] {flatten(pts, 0), flatten(pts, 1)});
    }

    private static float[] flatten(float[][][] pts, int c) {
        float[] out = new float[pts.length * 5];

        for (int k = 0; k < pts.length; k++) {
            for (int j = 0; j < 5; j++) {
                out[k * 5 + j] = pts[k][j][c];
            }
        }

        return out;
    }

    private static void glass(Hit h, float t, float dt, Show.View v) {
        Show.Pal c = h.pal;
        float s = h.scale;
        float crack = 0.13F;
        Vector3f p = v.at(h.at);
        if (!(t < crack)) {
            if (h.once(0)) {
                shatter(h, v, p);
            }

            if (t - crack < 0.2F) {
                float u = (t - crack) / 0.2F;
                Show.sprite(
                        v,
                        Show.RING,
                        p.x,
                        p.y,
                        p.z,
                        (0.2F + 0.9F * Show.easeOut(u)) * s / 0.86F,
                        0.0F,
                        c.light(),
                        0.3F * (1.0F - u),
                        1.0F - u);
            }

            shards(h, dt, v, 1.0F - Show.smooth((t - 0.6F) / 0.42F), 0.42F);
        } else {
            float grow = Show.easeOut(t / 0.06F);

            for (int i = 0; i < h.web.size() - 1; i++) {
                float[][] l = (float[][]) h.web.get(i);
                boolean spoke = l[0].length == 5;
                if (spoke || !(t < 0.03F)) {
                    int n = spoke ? Math.max(2, Math.round(5.0F * grow)) : 3;
                    float[] xs = new float[n];
                    float[] ys = new float[n];
                    float[] zs = new float[n];

                    for (int j = 0; j < n; j++) {
                        Vector3f q = plane(v, p, l[0][j], l[1][j]);
                        xs[j] = q.x;
                        ys[j] = q.y;
                        zs[j] = q.z;
                    }

                    Show.line(v, Show.TRAIL, xs, ys, zs, 0.16F * s, c.main(), 0.5F);
                    Show.line(v, Show.TRAIL, xs, ys, zs, 0.035F * s, c.light(), 1.0F);
                }
            }

            Show.sprite(v, Show.ORB, p.x, p.y, p.z, 0.4F * s, 0.0F, c.light(), 0.0F, 0.6F);
        }
    }

    private static void shards(Hit h, float dt, Show.View v, float fade, float body) {
        Show.Pal c = h.pal;
        List<Show.Tri> fill = new ArrayList<>();
        List<Show.Tri> shine = new ArrayList<>();
        Quaternionf q = new Quaternionf();

        for (Shard sh : h.shards) {
            sh.vy -= (double) (9.0F * dt);
            float drag = (float) Math.exp((double) (-1.2F * dt));
            sh.vx *= (double) drag;
            sh.vy *= (double) drag;
            sh.vz *= (double) drag;
            sh.x += sh.vx * (double) dt;
            sh.y += sh.vy * (double) dt;
            sh.z += sh.vz * (double) dt;
            sh.angle += sh.spin * dt;
            q.identity().rotateAxis(sh.angle, sh.axis);
            Vector3f at = new Vector3f(v.x(sh.x), v.y(sh.y), v.z(sh.z));
            Vector3f a = q.transform(new Vector3f(sh.a)).add(at);
            Vector3f b = q.transform(new Vector3f(sh.b)).add(at);
            Vector3f cc = q.transform(new Vector3f(sh.c)).add(at);
            fill.add(new Show.Tri(a, b, cc, Show.alpha(c.light(), body * fade)));
            Vector3f n =
                    (new Vector3f(b)).sub(a).cross((new Vector3f(cc)).sub(a)).normalize();
            float facing = Math.abs(n.dot((new Vector3f(v.toward(at.x, at.y, at.z))).normalize()));
            float glint = (float) Math.pow((double) facing, 6.0) * fade;
            if (glint > 0.03F) {
                shine.add(new Show.Tri(a, b, cc, Show.alpha(c.light(), glint)));
            }
        }

        Show.triangles(v, fill, false);
        Show.triangles(v, shine, true);
    }

    private static void shatter(Hit h, Show.View v, Vector3f p) {
        hear(h, SoundEvents.GLASS_BREAK, 0.5F, h.crit ? 1.25F : 1.7F);
        float[][] keep = (float[][]) h.web.get(h.web.size() - 1);
        int spokes = keep[0].length / 5;
        Vector3f toward = new Vector3f((float) (-h.dir.x), (float) (-h.dir.y), (float) (-h.dir.z));
        int[][] bands = new int[][] {{0, 1}, {1, 3}, {3, 4}};

        for (int k = 0; k < spokes; k++) {
            for (int[] band : bands) {
                int k2 = (k + 1) % spokes;
                Vector3f a = plane(v, p, keep[0][k * 5 + band[0]], keep[1][k * 5 + band[0]]);
                Vector3f b = plane(v, p, keep[0][k2 * 5 + band[0]], keep[1][k2 * 5 + band[0]]);
                Vector3f c2 = plane(v, p, keep[0][k2 * 5 + band[1]], keep[1][k2 * 5 + band[1]]);
                Vector3f d = plane(v, p, keep[0][k * 5 + band[1]], keep[1][k * 5 + band[1]]);
                piece(h, v, p, toward, a, b, c2);
                piece(h, v, p, toward, a, c2, d);
            }
        }

        for (int i = 0; i < 8; i++) {
            Vec3 d = randomDir(h.rnd).scale(2.0 + h.rnd.nextDouble() * 2.0);
            Fx.spawn()
                    .at(h.at)
                    .vel(d.x, d.y, d.z)
                    .tex(Fx.Tex.SPARKLE)
                    .size(0.12F * h.scale, 0.0F)
                    .spin(h.rnd.nextFloat() * 6.0F, 3.0F)
                    .life(0.5F)
                    .drag(3.0F)
                    .colour(h.pal.light(), h.pal.main() & 0xFFFFFF);
        }
    }

    private static void piece(Hit h, Show.View v, Vector3f p, Vector3f toward, Vector3f a, Vector3f b, Vector3f c) {
        Vector3f mid = (new Vector3f(a)).add(b).add(c).div(3.0F);
        Vector3f out = (new Vector3f(mid)).sub(p);
        float len = out.length();
        if (len > 1.0E-4F) {
            out.div(len);
        }

        Shard s = new Shard(
                (new Vector3f(a)).sub(mid),
                (new Vector3f(b)).sub(mid),
                (new Vector3f(c)).sub(mid),
                (new Vector3f(h.rnd.nextFloat() - 0.5F, h.rnd.nextFloat() - 0.5F, h.rnd.nextFloat() - 0.5F))
                        .normalize());
        Vec3 w = v.world(mid);
        s.x = w.x;
        s.y = w.y;
        s.z = w.z;
        float sp = (2.2F + h.rnd.nextFloat() * 2.4F) * h.scale;
        float back = 1.0F + h.rnd.nextFloat() * 1.5F;
        s.vx = (double) (out.x * sp + toward.x * back);
        s.vy = (double) (out.y * sp + toward.y * back + 1.0F);
        s.vz = (double) (out.z * sp + toward.z * back);
        s.spin = 6.0F + h.rnd.nextFloat() * 9.0F;
        h.shards.add(s);
    }

    static record Ground(float y, BlockState block, int light, int dust) {
        static final Ground STUDIO;

        static {
            STUDIO = new Ground(0.0F, Blocks.STONE.defaultBlockState(), 15728880, 0xFF8C8478);
        }
    }

    private static final class Hit {
        final int style;
        final boolean studio;
        final boolean crit;
        final boolean sound;
        final long start;
        final Random rnd = new Random();
        final long seed;
        final Vec3 at;
        final Vec3 dir;
        final AABB body;
        final Ground ground;
        final float scale;
        final Show.Pal pal;
        int done;
        float last;
        float owed;
        int pops;
        final List<float[][]> web;
        final List<Shard> shards;
        final List<Petal> petals;
        final List<Fly> flies;
        final List<Bubble> bubbles;

        Hit(
                int style,
                Vec3 at,
                Vec3 dir,
                AABB body,
                Ground ground,
                boolean crit,
                float scale,
                Show.Pal pal,
                boolean sound,
                boolean studio) {
            this.seed = this.rnd.nextLong();
            this.web = new ArrayList<>();
            this.shards = new ArrayList<>();
            this.petals = new ArrayList<>();
            this.flies = new ArrayList<>();
            this.bubbles = new ArrayList<>();
            this.style = style;
            this.at = at;
            this.dir = dir;
            this.body = body;
            this.ground = ground;
            this.crit = crit;
            this.scale = scale;
            this.pal = pal;
            this.sound = sound;
            this.studio = studio;
            this.start = HitFx.now(studio);
        }

        boolean once(int moment) {
            if ((this.done & 1 << moment) != 0) {
                return false;
            } else {
                this.done |= 1 << moment;
                return true;
            }
        }
    }

    private static final class Shard {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        final Vector3f a;
        final Vector3f b;
        final Vector3f c;
        final Vector3f axis;
        float angle;
        float spin;

        Shard(Vector3f a, Vector3f b, Vector3f c, Vector3f axis) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.axis = axis;
        }
    }

    private static final class Petal {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        final Vector3f axis;
        float angle;
        float spin;
        float size;
        float phase;
        float landed = -1.0F;
        float turn;

        Petal(Vector3f axis) {
            this.axis = axis;
        }
    }

    private static final class Fly {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        float yaw;
        float phase;
        float rate;
        float size;
        float life;
        float wing;
        int tint;
        final Vector3f fwd = new Vector3f(1.0F, 0.0F, 0.0F);
        final Vector3f right = new Vector3f(0.0F, 0.0F, 1.0F);
        final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
    }

    private static final class Bubble {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        float r;
        float phase;
        float life;
        float popped = -1.0F;
    }
}
