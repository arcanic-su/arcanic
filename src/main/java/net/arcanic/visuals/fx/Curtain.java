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

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Tag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

final class Curtain {
    static final int RED = -50354;
    private static final Map<Long, Double> GROUND = new HashMap<>();
    private static long groundAt;

    private Curtain() {}

    static void draw(
            VertexConsumer vc,
            TrapZone.Frame f,
            Vec3 o,
            double r,
            boolean under,
            float a,
            float appear,
            float burnt,
            int base,
            boolean rising) {
        draw(vc, f, o, r, under, a, appear, burnt, base, rising, false);
    }

    static void draw(
            VertexConsumer vc,
            TrapZone.Frame f,
            Vec3 o,
            double r,
            boolean under,
            float a,
            float appear,
            float burnt,
            int base,
            boolean rising,
            boolean terrain) {
        if (!(a < 0.01F) && !(r < 0.3)) {
            float grow = ease(appear / 0.35F);
            double rr = r * (double) grow;
            int n = Math.max(64, Math.min(256, (int) (rr * 9.0)));
            double[][] path = new double[n][];

            for (int i = 0; i < n; ++i) {
                double t = (double) i * Math.PI * 2.0 / (double) n;
                path[i] = new double[] {o.x + Math.cos(t) * rr, o.z + Math.sin(t) * rr};
            }

            zone(
                    vc,
                    f,
                    path,
                    o.x,
                    o.z,
                    o.y - 0.02,
                    Math.max(0.7, Math.min(1.4, r * 0.06)),
                    under,
                    a,
                    appear,
                    burnt,
                    base,
                    rising,
                    terrain);
        }
    }

    static void ring(
            VertexConsumer vc, TrapZone.Frame f, Vec3 o, double r, boolean under, float a, float appear, int colour) {
        if (!(a < 0.01F) && !(r < 0.3)) {
            double rr = r * (double) ease(appear / 0.45F);
            int n = Math.max(48, Math.min(200, (int) (rr * 9.0)));
            double ref = o.y;
            if (under) {
                int bands = 4;

                for (int b = 0; b < bands; ++b) {
                    double k0 = (double) b / (double) bands;
                    double k1 = (double) (b + 1) / (double) bands;
                    int c0 = TrapZone.alpha(colour, a * 0.16F * (float) (k0 * k0));
                    int c1 = TrapZone.alpha(colour, a * 0.16F * (float) (k1 * k1));

                    for (int i = 0; i < n; ++i) {
                        double t0 = (double) i * Math.PI * 2.0 / (double) n;
                        double t1 = (double) (i + 1) * Math.PI * 2.0 / (double) n;
                        double ax = o.x + Math.cos(t0) * rr * k0;
                        double az = o.z + Math.sin(t0) * rr * k0;
                        double bx = o.x + Math.cos(t1) * rr * k0;
                        double bz = o.z + Math.sin(t1) * rr * k0;
                        double cx = o.x + Math.cos(t1) * rr * k1;
                        double cz = o.z + Math.sin(t1) * rr * k1;
                        double dx = o.x + Math.cos(t0) * rr * k1;
                        double dz = o.z + Math.sin(t0) * rr * k1;
                        TrapZone.face(
                                vc,
                                f,
                                ax,
                                ground(ax, az, ref) + 0.03,
                                az,
                                bx,
                                ground(bx, bz, ref) + 0.03,
                                bz,
                                cx,
                                ground(cx, cz, ref) + 0.03,
                                cz,
                                dx,
                                ground(dx, dz, ref) + 0.03,
                                dz,
                                c0,
                                c0,
                                c1,
                                c1);
                    }
                }

            } else {
                int lc = TrapZone.alpha(Fx.lerp(colour, -1, 0.15F), a);
                double dash = 1.1;
                double gap = 0.7;
                double len = (Math.PI * 2D) * rr;
                int count = Math.max(6, (int) Math.round(len / (dash + gap)));
                double step = (Math.PI * 2D) / (double) count;
                double fill = step * dash / (dash + gap);
                double turn = (double) f.time() * 0.12;

                for (int i = 0; i < count; ++i) {
                    double s0 = (double) i * step + turn;
                    int parts = 4;

                    for (int j = 0; j < parts; ++j) {
                        double t0 = s0 + fill * (double) j / (double) parts;
                        double t1 = s0 + fill * (double) (j + 1) / (double) parts;
                        double ax = o.x + Math.cos(t0) * rr;
                        double az = o.z + Math.sin(t0) * rr;
                        double bx = o.x + Math.cos(t1) * rr;
                        double bz = o.z + Math.sin(t1) * rr;
                        TrapZone.line(
                                vc,
                                f,
                                new Vec3(ax, ground(ax, az, ref) + 0.06, az),
                                new Vec3(bx, ground(bx, bz, ref) + 0.06, bz),
                                3.2F,
                                lc,
                                lc);
                    }
                }
            }
        }
    }

    static double ground(double x, double z, double ref) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return ref;
        } else {
            long now = System.currentTimeMillis();
            if (now - groundAt > 500L || GROUND.size() > 20000) {
                GROUND.clear();
                groundAt = now;
            }

            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(ref);
            int bz = (int) Math.floor(z);
            long key = BlockPos.asLong(bx, by, bz);
            Double v = (Double) GROUND.get(key);
            if (v != null) {
                return v;
            } else {
                double h = scan(level, bx, by, bz, ref);
                GROUND.put(key, h);
                return h;
            }
        }
    }

    private static double scan(Level level, int x, int y, int z, double ref) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (solid(level, p.set(x, y, z))) {
            for (int i = 1; i <= 8; ++i) {
                if (!solid(level, p.set(x, y + i, z))) {
                    return top(level, p.set(x, y + i - 1, z));
                }
            }

            return ref;
        } else {
            for (int i = 1; i <= 16; ++i) {
                if (solid(level, p.set(x, y - i, z))) {
                    return top(level, p);
                }
            }

            return ref;
        }
    }

    private static boolean solid(Level level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        if (!st.isAir() && !st.is(Blocks.TNT)) {
            return !st.getCollisionShape(level, p).isEmpty();
        } else {
            return false;
        }
    }

    private static double top(Level level, BlockPos p) {
        VoxelShape shape = level.getBlockState(p).getCollisionShape(level, p);
        double h = shape.isEmpty() ? 1.0 : shape.max(Axis.Y);
        return (double) p.getY() + Math.max(0.0, Math.min(1.5, h));
    }

    static void square(
            VertexConsumer vc,
            TrapZone.Frame f,
            double x0,
            double z0,
            double x1,
            double z1,
            double y,
            boolean under,
            float a,
            float appear,
            float burnt,
            int base,
            boolean rising) {
        if (!(a < 0.01F)) {
            double mx = (x0 + x1) / 2.0;
            double mz = (z0 + z1) / 2.0;
            double g = (double) ease(appear / 0.35F);
            double hx = (x1 - x0) / 2.0 * g;
            double hz = (z1 - z0) / 2.0 * g;
            int per = 16;
            double[][] path = new double[per * 4][];
            double[][] corners =
                    new double[][] {{mx - hx, mz - hz}, {mx + hx, mz - hz}, {mx + hx, mz + hz}, {mx - hx, mz + hz}};

            for (int s = 0; s < 4; ++s) {
                for (int i = 0; i < per; ++i) {
                    double t = (double) i / (double) per;
                    double[] p = corners[s];
                    double[] q = corners[(s + 1) % 4];
                    path[s * per + i] = new double[] {p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t};
                }
            }

            zone(vc, f, path, mx, mz, y, 1.1, under, a, appear, burnt, base, rising, false);
        }
    }

    static void cube(
            VertexConsumer vc,
            TrapZone.Frame f,
            double[] b,
            boolean under,
            float a,
            float appear,
            int base,
            boolean rising) {
        if (!(a < 0.01F)) {
            double g = (double) ease(appear / 0.4F);
            double mx = (b[0] + b[3]) / 2.0;
            double mz = (b[2] + b[5]) / 2.0;
            double hx = (b[3] - b[0]) / 2.0 * g;
            double hz = (b[5] - b[2]) / 2.0 * g;
            double y = b[1] + 0.02;
            double h = b[4] - b[1];
            int per = 24;
            double[][] corners =
                    new double[][] {{mx - hx, mz - hz}, {mx + hx, mz - hz}, {mx + hx, mz + hz}, {mx - hx, mz + hz}};
            double[][] path = new double[per * 4][];

            for (int s = 0; s < 4; ++s) {
                for (int i = 0; i < per; ++i) {
                    double t = (double) i / (double) per;
                    double[] p = corners[s];
                    double[] q = corners[(s + 1) % 4];
                    path[s * per + i] = new double[] {p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t};
                }
            }

            zone(vc, f, path, mx, mz, y, h, under, a * 0.8F, appear, 0.0F, base, rising, false);
            if (!under) {
                double up = h * (double) ease((appear - 0.1F) / 0.5F);
                int bright = TrapZone.alpha(Fx.lerp(base, -1, 0.35F), a);
                int dim = TrapZone.alpha(base, a * 0.35F);

                for (int s = 0; s < 4; ++s) {
                    double[] p = corners[s];
                    double[] q = corners[(s + 1) % 4];
                    double[] o = corners[(s + 3) % 4];
                    Vec3 pc = new Vec3(p[0], y, p[1]);
                    TrapZone.line(vc, f, pc, pc.add(0.0, up, 0.0), 1.8F, bright, TrapZone.alpha(base, a * 0.15F));
                    TrapZone.line(vc, f, new Vec3(p[0], y + up, p[1]), new Vec3(q[0], y + up, q[1]), 1.3F, dim, dim);
                    double len = Math.min(2.5, Math.min(hx, hz) * 0.25);
                    if (!(len < 0.05)) {
                        Vec3 toQ = (new Vec3(q[0] - p[0], 0.0, q[1] - p[1]))
                                .normalize()
                                .scale(len);
                        Vec3 toO = (new Vec3(o[0] - p[0], 0.0, o[1] - p[1]))
                                .normalize()
                                .scale(len);
                        TrapZone.line(
                                vc, f, pc.add(0.0, 0.005, 0.0), pc.add(toQ).add(0.0, 0.005, 0.0), 3.4F, bright, bright);
                        TrapZone.line(
                                vc, f, pc.add(0.0, 0.005, 0.0), pc.add(toO).add(0.0, 0.005, 0.0), 3.4F, bright, bright);
                    }
                }
            }
        }
    }

    private static float ease(float t) {
        t = TrapZone.clamp(t);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private static void zone(
            VertexConsumer vc,
            TrapZone.Frame f,
            double[][] path,
            double mx,
            double mz,
            double y,
            double height,
            boolean under,
            float a,
            float appear,
            float burnt,
            int base,
            boolean rising,
            boolean terrain) {
        int c = Fx.lerp(base, -50354, TrapZone.clamp(burnt));
        int n = path.length;
        double ref = y + 0.02;
        double[] gy = new double[n];

        for (int i = 0; i < n; ++i) {
            gy[i] = terrain ? ground(path[i][0], path[i][1], ref) + 0.02 : y;
        }

        if (under) {
            int bands = 5;

            for (int b = 0; b < bands; ++b) {
                float k0 = (float) b / (float) bands;
                float k1 = (float) (b + 1) / (float) bands;
                float fillA = terrain ? 0.2F : 0.12F;
                int c0 = TrapZone.alpha(c, a * fillA * k0 * k0);
                int c1 = TrapZone.alpha(c, a * fillA * k1 * k1);

                for (int i = 0; i < n; ++i) {
                    double[] p = path[i];
                    double[] q = path[(i + 1) % n];
                    double ax = mx + (p[0] - mx) * (double) k0;
                    double az = mz + (p[1] - mz) * (double) k0;
                    double bx = mx + (q[0] - mx) * (double) k0;
                    double bz = mz + (q[1] - mz) * (double) k0;
                    double cx = mx + (q[0] - mx) * (double) k1;
                    double cz = mz + (q[1] - mz) * (double) k1;
                    double dx = mx + (p[0] - mx) * (double) k1;
                    double dz = mz + (p[1] - mz) * (double) k1;
                    TrapZone.face(
                            vc,
                            f,
                            ax,
                            terrain ? ground(ax, az, ref) + 0.02 : y,
                            az,
                            bx,
                            terrain ? ground(bx, bz, ref) + 0.02 : y,
                            bz,
                            cx,
                            terrain ? ground(cx, cz, ref) + 0.02 : y,
                            cz,
                            dx,
                            terrain ? ground(dx, dz, ref) + 0.02 : y,
                            dz,
                            c0,
                            c0,
                            c1,
                            c1);
                }
            }

            double h = height * (double) ease((appear - 0.1F) / 0.5F);
            if (h > 0.02) {
                int rows = 12;
                float speed = 0.45F + 0.9F * burnt;

                for (int j = 0; j < rows; ++j) {
                    float t0 = (float) j / (float) rows;
                    float t1 = (float) (j + 1) / (float) rows;
                    int c0 = TrapZone.alpha(c, a * wall(t0, f.time(), speed, rising));
                    int c1 = TrapZone.alpha(c, a * wall(t1, f.time(), speed, rising));

                    for (int i = 0; i < n; ++i) {
                        double[] p = path[i];
                        double[] q = path[(i + 1) % n];
                        double pa = gy[i] + h * (double) t0;
                        double pb = gy[i] + h * (double) t1;
                        double qa = gy[(i + 1) % n] + h * (double) t0;
                        double qb = gy[(i + 1) % n] + h * (double) t1;
                        TrapZone.face(
                                vc, f, p[0], pa, p[1], q[0], qa, q[1], q[0], qb, q[1], p[0], pb, p[1], c0, c0, c1, c1);
                    }
                }
            }
        } else {
            int lc = TrapZone.alpha(Fx.lerp(c, -1, terrain ? 0.3F : 0.2F), a);
            double lift = terrain ? 0.04 : 0.0;

            for (int i = 0; i < n; ++i) {
                double[] p = path[i];
                double[] q = path[(i + 1) % n];
                TrapZone.line(
                        vc,
                        f,
                        new Vec3(p[0], gy[i] + lift, p[1]),
                        new Vec3(q[0], gy[(i + 1) % n] + lift, q[1]),
                        terrain ? 3.4F : 2.2F,
                        lc,
                        lc);
            }
        }
    }

    private static float wall(float t, float time, float speed, boolean rising) {
        float base = 0.34F * (float) Math.pow((double) (1.0F - t), 1.7);
        if (!rising) {
            return base;
        } else {
            float band = 0.0F;

            for (int i = 0; i < 2; ++i) {
                float at = (time * speed + (float) i * 0.5F) % 1.0F;
                float d = (t - at) / 0.09F;
                band += (float) Math.exp((double) (-d * d));
            }

            return base + 0.16F * band * (1.0F - t);
        }
    }

    static void badge(
            GuiGraphics g,
            Vec3 at,
            ItemStack icon,
            float left,
            boolean lit,
            float damage,
            float health,
            int base,
            float a,
            boolean showDamage) {
        double[] s = Projection.toScreen(at, Px.width(), Px.height());
        if (s != null && !(a < 0.02F)) {
            int fs = Math.max(10, Math.round((float) Px.height() / 1080.0F * 12.0F));
            int rr = Math.round((float) fs * 1.45F);
            int cx = (int) s[0];
            int cy = (int) s[1];
            boolean kills = showDamage && damage >= health && damage > 0.0F;
            int c = kills ? -50354 : Fx.lerp(base, -50354, lit ? 1.0F - left : 0.0F);
            float k = Px.fade;
            Px.fade = k * a;
            Px.shadow(g, cx - rr, cy - rr + fs / 6, rr * 2, rr * 2, fs / 2, 1426063360);
            Px.round(g, cx - rr, cy - rr, rr * 2, rr * 2, rr, 0xEB0E0E11);
            Px.ring(g, cx, cy, rr, 452984831);
            int th = Math.max(2, fs / 6);
            Px.arc(g, (float) cx, (float) cy, (float) (rr - th), th, 0.0, (Math.PI * 2D), 654311423, 654311423);
            Px.arc(
                    g,
                    (float) cx,
                    (float) cy,
                    (float) (rr - th),
                    th,
                    (-Math.PI / 2D),
                    (Math.PI * 2D) * (double) TrapZone.clamp(left),
                    c,
                    c);
            int is = Math.round((float) rr * 1.05F);
            HudElements.item(g, icon, cx - is / 2, cy - is / 2, is);
            if (showDamage && (kills || damage >= 0.25F)) {
                Tag.draw(g, cx, cy + rr + fs / 3, fs, Tag.damage(damage, kills), kills, 1.0F);
            }

            Px.fade = k;
        }
    }
}
