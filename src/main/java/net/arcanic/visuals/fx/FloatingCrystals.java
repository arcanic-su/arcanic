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

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.impact.ImpactDraw;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class FloatingCrystals extends Module {
    private static final int MAX = 100;
    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static final Shape CRYSTAL = crystal();
    private static final Shape PYRAMID = pyramid();
    private static final Shape STAR = star();
    private static final Shape ICOSAHEDRON = icosahedron();

    public FloatingCrystals() {
        super("floating_crystals", "Кристаллы", "Парящие светящиеся кристаллы вокруг тебя", Category.WORLD, false);
    }

    protected void onDisable() {
        PARTICLES.clear();
    }

    public void clear() {
        PARTICLES.clear();
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            PARTICLES.removeIf((px) -> px.alpha.value() == 0.0F && px.expired());

            for (Particle p : PARTICLES) {
                p.tick();
            }

            int alive = 0;

            for (Particle p : PARTICLES) {
                if (!p.dead) {
                    ++alive;
                }
            }

            for (int i = PARTICLES.size() - 1; i >= 0 && alive > 100; --i) {
                Particle p = (Particle) PARTICLES.get(i);
                if (!p.dead) {
                    p.dead = true;
                    --alive;
                }
            }

            for (int i = 0; i < 4 && alive < 100; ++i) {
                Vec3 at = spawn(mc);
                if (at == null) {
                    break;
                }

                PARTICLES.add(new Particle(
                        at,
                        Vec3.ZERO,
                        new Vec3(random(-1.0, 1.0), random(0.0, 2.0), random(-1.0, 1.0)),
                        new Vec3(random(-1.0, 1.0), random(-1.0, 1.0), random(-1.0, 1.0)),
                        (long) random(1500.0, 4500.0),
                        (float) random(0.1, 0.3)));
                ++alive;
            }

        } else {
            PARTICLES.clear();
        }
    }

    private static double random(double min, double max) {
        return min + (max - min) * ThreadLocalRandom.current().nextDouble();
    }

    private static Vec3 spawn(Minecraft mc) {
        Vec3 first = null;

        for (int i = 0; i < 8; ++i) {
            Vec3 p = mc.player.position().add(random(-20.0, 20.0), random(1.6, 6.0), random(-20.0, 20.0));
            if (!solid(mc, p)) {
                if (first == null) {
                    first = p;
                }

                if (!solid(mc, p.subtract(0.0, 1.2, 0.0))) {
                    return p;
                }
            }
        }

        return first;
    }

    private static boolean solid(Minecraft mc, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        if (mc.level.isOutsideBuildHeight(pos.getY())) {
            return true;
        } else {
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) {
                return false;
            } else {
                VoxelShape shape = state.getCollisionShape(mc.level, pos);
                if (shape.isEmpty()) {
                    return false;
                } else {
                    Vec3 local = p.subtract((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());

                    for (AABB box : shape.toAabbs()) {
                        if (box.inflate(0.01).contains(local)) {
                            return true;
                        }
                    }

                    return false;
                }
            }
        }
    }

    private static Shape crystal() {
        float r = 0.42F;
        float t = 0.7F;
        float[][] v = new float[][] {
            {0.0F, t, 0.0F}, {0.0F, -t, 0.0F}, {r, 0.0F, 0.0F}, {-r, 0.0F, 0.0F}, {0.0F, 0.0F, r}, {0.0F, 0.0F, -r}
        };
        int[][] e = new int[][] {
            {0, 2}, {0, 3}, {0, 4}, {0, 5}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 4}, {4, 3}, {3, 5}, {5, 2}
        };
        return new Shape(v, e, new int[][] {{0, 1}, {2, 3}, {4, 5}});
    }

    private static Shape pyramid() {
        float s = 0.5F;
        float[][] v = new float[][] {{s, s, s}, {s, -s, -s}, {-s, s, -s}, {-s, -s, s}, {0.0F, 0.0F, 0.0F}};
        int[][] e = new int[][] {{0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}};
        return new Shape(v, e, new int[][] {{0, 4}, {1, 4}, {2, 4}, {3, 4}});
    }

    private static Shape star() {
        float s = 0.42F;
        float[][] v = new float[][] {
            {s, s, s}, {s, -s, -s}, {-s, s, -s}, {-s, -s, s}, {-s, -s, -s}, {-s, s, s}, {s, -s, s}, {s, s, -s}
        };
        int[][] e = new int[][] {
            {0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}, {4, 5}, {4, 6}, {4, 7}, {5, 6}, {5, 7}, {6, 7}
        };
        int[][] in = new int[][] {
            {0, 5}, {0, 6}, {0, 7}, {1, 4}, {1, 6}, {1, 7}, {2, 4}, {2, 5}, {2, 7}, {3, 4}, {3, 5}, {3, 6}
        };
        return new Shape(v, e, in);
    }

    private static Shape icosahedron() {
        float g = (1.0F + (float) Math.sqrt(5.0)) / 2.0F;
        float k = 0.5F / (float) Math.sqrt((double) (1.0F + g * g)) * 1.15F;
        float b = g * k;
        float[][] v = new float[][] {
            {-k, b, 0.0F},
            {k, b, 0.0F},
            {-k, -b, 0.0F},
            {k, -b, 0.0F},
            {0.0F, -k, b},
            {0.0F, k, b},
            {0.0F, -k, -b},
            {0.0F, k, -b},
            {b, 0.0F, -k},
            {b, 0.0F, k},
            {-b, 0.0F, -k},
            {-b, 0.0F, k}
        };
        List<int[]> e = new ArrayList<>();
        float edge = 2.0F * k;

        for (int i = 0; i < v.length; ++i) {
            for (int j = i + 1; j < v.length; ++j) {
                float dx = v[i][0] - v[j][0];
                float dy = v[i][1] - v[j][1];
                float dz = v[i][2] - v[j][2];
                if (Math.abs(Math.sqrt((double) (dx * dx + dy * dy + dz * dz)) - (double) edge) < 0.001) {
                    e.add(new int[] {i, j});
                }
            }
        }

        return new Shape(
                v, (int[][]) e.toArray(new int[0][]), new int[][] {{0, 3}, {1, 2}, {4, 7}, {5, 6}, {8, 11}, {9, 10}});
    }

    private Shape current() {
        Shape var10000;
        switch ((String) Modules.AMBIENCE.crystalShape.get()) {
            case "Пирамида" -> var10000 = PYRAMID;
            case "Звезда" -> var10000 = STAR;
            case "Икосаэдр" -> var10000 = ICOSAHEDRON;
            default -> var10000 = CRYSTAL;
        }

        return var10000;
    }

    public static void render(WorldRenderContext ctx, Vec3 cam, Vector3f left, Vector3f up, float pt) {
        if (!PARTICLES.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            int base = Modules.AMBIENCE.crystalColour.argb() & 0xFFFFFF;
            PoseStack.Pose pose = ctx.matrices().last();
            long now = System.currentTimeMillis();

            for (Particle p : PARTICLES) {
                p.alpha.update(!p.expired(), now);
            }

            BufferBuilder bloom = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            boolean any = false;

            for (Particle p : PARTICLES) {
                float a = p.alpha.value();
                if (!(a <= 0.0F)) {
                    Vec3 at = p.prev.lerp(p.pos, (double) pt).subtract(cam);
                    float half = 2.0F * p.size;
                    float x = (float) at.x;
                    float y = (float) at.y;
                    float z = (float) at.z;
                    int col = alpha(base, a * 0.55F);
                    float lx = left.x * half;
                    float ly = left.y * half;
                    float lz = left.z * half;
                    float ux = up.x * half;
                    float uy = up.y * half;
                    float uz = up.z * half;
                    bloom.addVertex(pose, x - lx - ux, y - ly - uy, z - lz - uz)
                            .setUv(0.0F, 1.0F)
                            .setColor(col);
                    bloom.addVertex(pose, x + lx - ux, y + ly - uy, z + lz - uz)
                            .setUv(1.0F, 1.0F)
                            .setColor(col);
                    bloom.addVertex(pose, x + lx + ux, y + ly + uy, z + lz + uz)
                            .setUv(1.0F, 0.0F)
                            .setColor(col);
                    bloom.addVertex(pose, x - lx + ux, y - ly + uy, z - lz + uz)
                            .setUv(0.0F, 0.0F)
                            .setColor(col);
                    any = true;
                }
            }

            if (any) {
                ImpactDraw.draw(ImpactDraw.type(Fx.Tex.MOTE_HALO.id, Mode.QUADS, true, true), bloom);
            }

            RenderType type = Fx.glowLines();
            if (type != null) {
                VertexConsumer lines = ctx.consumers().getBuffer(type);
                float px = Math.max(1.0F, (float) mc.getWindow().getHeight() / 1080.0F);
                Shape s = Modules.FLOATING_CRYSTALS.current();
                Vector3f[] c = new Vector3f[s.v.length];

                for (Particle p : PARTICLES) {
                    float a = p.alpha.value();
                    if (!(a <= 0.0F)) {
                        Vec3 at = p.prev.lerp(p.pos, (double) pt).subtract(cam);
                        Vec3 r = p.prevRot.lerp(p.rot, (double) pt);
                        Matrix4f mx = (new Matrix4f())
                                .translate((float) at.x, (float) at.y, (float) at.z)
                                .rotate((new Quaternionf()).rotationXYZ((float) r.x, (float) r.y, (float) r.z))
                                .scale(p.size);

                        for (int i = 0; i < c.length; ++i) {
                            c[i] = mx.transformPosition(s.v[i][0], s.v[i][1], s.v[i][2], new Vector3f());
                        }

                        int faint = alpha(base, a * 0.4F);
                        int bright = alpha(base, a * 205.0F / 255.0F);

                        for (int[] e : s.inner) {
                            segment(lines, pose, c[e[0]], c[e[1]], faint, px);
                        }

                        for (int[] e : s.edges) {
                            segment(lines, pose, c[e[0]], c[e[1]], bright, px * 1.4F);
                        }
                    }
                }
            }
        }
    }

    private static void segment(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, int col, float w) {
        Fx.line(vc, pose, a.x, a.y, a.z, b.x, b.y, b.z, col, w);
    }

    private static int alpha(int rgb, float a) {
        return (int) (Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | rgb;
    }

    private static record Shape(float[][] v, int[][] edges, int[][] inner) {}

    private static final class Fade {
        private float t;
        private long last = -1L;

        void update(boolean on, long now) {
            float dt = this.last < 0L ? 0.0F : (float) (now - this.last) / 1000.0F;
            this.last = now;
            this.t = on ? Math.min(1.0F, this.t + dt) : Math.max(0.0F, this.t - dt);
        }

        float value() {
            return this.t * this.t * (3.0F - 2.0F * this.t);
        }
    }

    private static final class Particle {
        Vec3 prev;
        Vec3 prevRot;
        Vec3 pos;
        Vec3 rot;
        Vec3 motion;
        Vec3 rotMotion;
        final long life;
        final long born = System.currentTimeMillis();
        final float size;
        boolean dead;
        final Fade alpha = new Fade();

        Particle(Vec3 pos, Vec3 rot, Vec3 motion, Vec3 rotMotion, long life, float size) {
            this.pos = this.prev = pos;
            this.rot = this.prevRot = rot;
            this.motion = motion.scale((double) 0.04F);
            this.rotMotion = rotMotion.scale((double) 0.04F);
            this.life = life;
            this.size = size;
        }

        boolean expired() {
            return this.dead || System.currentTimeMillis() - this.born >= this.life;
        }

        void tick() {
            this.prev = this.pos;
            this.prevRot = this.rot;
            this.pos = this.pos.add(this.motion);
            this.rot = this.rot.add(this.rotMotion);
            this.motion = this.motion.scale(0.98);
            this.rotMotion = this.rotMotion.scale(0.98);
        }
    }
}
