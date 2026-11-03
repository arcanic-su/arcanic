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
package net.arcanic.visuals.fx.impact;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.arcanic.visuals.fx.Fx;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class ProjectileHit extends ImpactEffect {
    private static final int CRACK = 0;
    private static final int FLASH = 1;
    private static final int BLOOD = 2;
    private static final int CONE = 3;
    private static final float[] LIFE = {1.2F, 0.25F, 2.4F, 0.75F};
    private static final int RAYS = 13;
    private static final int MAX_BURSTS = 12;
    private static final int TRACK_TICKS = 12;
    private static final int SEGMENT_TICKS = 10;
    private static final double REACH = 2.0;
    private final ImpactEffect.Choice style =
            new ImpactEffect.Choice("Эффект", new String[] {"Пробой", "Импакт", "Брызги", "Конус"}, 0);
    private final ImpactEffect.Choice targets =
            new ImpactEffect.Choice("Цели", new String[] {"Игроки", "Монстры", "Мирные"}, new int[] {0});
    private final ImpactEffect.Choice projectiles =
            new ImpactEffect.Choice("Снаряды", new String[] {"Стрелы", "Броски"}, new int[] {0, 1});
    private final ImpactEffect.Toggle onlyMine = new ImpactEffect.Toggle("Только мои снаряды", true);
    private final ImpactEffect.Slider size = new ImpactEffect.Slider("Размер", 1.0F, 0.5F, 2.0F, 0.1F);
    private final ImpactEffect.Toggle shake = new ImpactEffect.Toggle("Тряска камеры", false);
    private final ImpactEffect.Toggle sound = new ImpactEffect.Toggle("Звук", true);
    private final ImpactEffect.Slider volume;
    private final ImpactEffect.Toggle clientColour;
    private final ImpactEffect.Paint colour;
    private final ImpactEffect.Paint blood;
    private final List<Burst> bursts;
    private final Map<Integer, Track> tracks;
    private Map<Integer, Integer> hurt;
    private final Random random;
    private int tick;

    public ProjectileHit() {
        super("projectile_hit", "Projectile Hit", "Эффект при успешном попадании снарядом по сущности");
        this.volume = new ImpactEffect.Slider("Громкость", 0.4F, 0.1F, 1.0F, 0.1F).showIf(this.sound::on);
        this.clientColour = (new ImpactEffect.Toggle("Цвет клиента", true)).showIf(() -> !this.style.is(2));
        this.colour = (new ImpactEffect.Paint("Кастомный цвет", new Color(158, 237, 237)))
                .showIf(() -> !this.style.is(2) && !this.clientColour.on());
        this.blood = (new ImpactEffect.Paint("Цвет крови", new Color(126, 12, 14))).showIf(() -> this.style.is(2));
        this.bursts = new ArrayList<>();
        this.tracks = new HashMap<>();
        this.hurt = new HashMap<>();
        this.random = new Random();
    }

    public void tick() {
        if (mc.level != null && mc.player != null) {
            ++this.tick;
            this.followProjectiles();
            this.findHits();
        } else {
            this.tracks.clear();
            this.hurt.clear();
        }
    }

    public void left() {
        this.tracks.clear();
        this.hurt.clear();
        this.bursts.clear();
    }

    public void clearStudio() {}

    protected void onDisable() {
        super.onDisable();
        this.left();
    }

    private void followProjectiles() {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (this.watched(e)) {
                Track t = (Track) this.tracks.computeIfAbsent(e.getId(), (id) -> {
                    Track fresh = new Track();
                    Vec3 v = e.getDeltaMovement();
                    fresh.move(e.getX() - v.x, e.getY() - v.y, e.getZ() - v.z, e.getX(), e.getY(), e.getZ(), this.tick);
                    return fresh;
                });
                if (t.seen) {
                    t.move(t.x, t.y, t.z, e.getX(), e.getY(), e.getZ(), this.tick);
                }

                t.x = e.getX();
                t.y = e.getY();
                t.z = e.getZ();
                t.seen = true;
                t.lastTick = this.tick;
            }
        }

        this.tracks.entrySet().removeIf((en) -> this.tick - ((Track) en.getValue()).lastTick > 12);
    }

    private boolean watched(Entity e) {
        if (e instanceof AbstractArrow) {
            if (!this.projectiles.has("Стрелы")) {
                return false;
            }
        } else if (!(e instanceof ThrowableProjectile) || !this.projectiles.has("Броски")) {
            return false;
        }

        return !this.onlyMine.on() || ((Projectile) e).getOwner() == mc.player;
    }

    private void findHits() {
        Map<Integer, Integer> now = new HashMap<>();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity l) {
                Integer before = this.hurt.get(e.getId());
                now.put(e.getId(), l.hurtTime);
                if (e != mc.player && before != null && l.hurtTime > before && this.wanted(l)) {
                    this.struck(l);
                }
            }
        }

        this.hurt = now;
    }

    private boolean wanted(LivingEntity e) {
        if (e instanceof Player) {
            return this.targets.has("Игроки");
        } else {
            return e instanceof Enemy ? this.targets.has("Монстры") : this.targets.has("Мирные");
        }
    }

    private void struck(LivingEntity e) {
        AABB box = e.getBoundingBox().inflate(0.35);
        Vec3 centre = box.getCenter();
        double best = Double.MAX_VALUE;
        Vec3 at = null;
        Vec3 dir = null;
        Track by = null;

        for (Track t : this.tracks.values()) {
            if (!t.hit.contains(e.getId())) {
                for (double[] m : t.moves) {
                    if (this.tick - (int) m[6] <= 10) {
                        Vec3 a = new Vec3(m[0], m[1], m[2]);
                        Vec3 b = new Vec3(m[3], m[4], m[5]);
                        Optional<Vec3> clip = box.clip(a, b);
                        Vec3 point;
                        double off;
                        if (clip.isPresent()) {
                            point = (Vec3) clip.get();
                            off = 0.0;
                        } else {
                            Vec3 near = nearest(a, b, centre);
                            off = distance(box, near);
                            if (off > 2.0) {
                                continue;
                            }

                            point = clamp(box, near);
                        }

                        if (off < best) {
                            best = off;
                            at = point;
                            dir = b.subtract(a);
                            by = t;
                        }
                    }
                }
            }
        }

        if (at != null) {
            by.hit.add(e.getId());
            this.burst(at, dir);
        }
    }

    private static Vec3 nearest(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        return len < 1.0E-9
                ? a
                : a.add(ab.scale(Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len))));
    }

    private static double distance(AABB box, Vec3 p) {
        double dx = Math.max(Math.max(box.minX - p.x, 0.0), p.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - p.y, 0.0), p.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - p.z, 0.0), p.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Vec3 clamp(AABB box, Vec3 p) {
        return new Vec3(
                Math.max(box.minX, Math.min(box.maxX, p.x)),
                Math.max(box.minY, Math.min(box.maxY, p.y)),
                Math.max(box.minZ, Math.min(box.maxZ, p.z)));
    }

    private void burst(Vec3 at, Vec3 flew) {
        Vec3 dir = flew.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flew.normalize();
        int kind = Math.max(0, Math.min(3, this.style.index()));
        this.bursts.add(new Burst(at, dir, ImpactDraw.now(), kind, this.random));

        while (this.bursts.size() > 12) {
            this.bursts.remove(0);
        }

        if (kind == 2) {
            this.splash(at, dir);
        }

        if (kind == 3) {
            this.cone(at, dir);
        }

        this.play(at, kind);
        if (this.shake.on() && mc.player != null) {
            double d = mc.player.position().distanceTo(at);
            if (d < 40.0) {
                ImpactDraw.shake(1.1F * (float) Math.max(0.2, 1.0 - d / 40.0) * this.size.value());
            }
        }
    }

    private void play(Vec3 at, int kind) {
        if (this.sound.on() && mc.level != null) {
            SoundEvent s =
                    switch (kind) {
                        case 1 -> SoundEvents.ARROW_HIT_PLAYER;
                        case 2 -> SoundEvents.SLIME_SQUISH;
                        case 3 -> SoundEvents.GENERIC_EXPLODE.value();
                        default -> SoundEvents.GLASS_BREAK;
                    };
            float pitch = kind == 1 ? 1.0F : (kind == 2 ? 1.5F : (kind == 3 ? 1.9F : 1.6F));
            mc.level.playLocalSound(at.x, at.y, at.z, s, SoundSource.MASTER, this.volume.value(), pitch, false);
        }
    }

    private void splash(Vec3 at, Vec3 dir) {
        Color c = this.blood.colour();
        int main = 0xFF000000 | c.getRGB() & 0xFFFFFF;
        int dark = main & 0xFFFFFF;
        float s = this.size.value();

        for (int i = 0; i < 34; i++) {
            boolean back = i % 6 == 5;
            Vec3 way = (back ? dir.scale(-1.0) : dir)
                    .add(
                            ((double) this.random.nextFloat() - 0.5) * (back ? 1.2 : 0.7),
                            ((double) this.random.nextFloat() - 0.5) * (back ? 1.2 : 0.7) + 0.14,
                            ((double) this.random.nextFloat() - 0.5) * (back ? 1.2 : 0.7))
                    .normalize()
                    .scale(
                            back
                                    ? 1.3 + (double) this.random.nextFloat() * 1.7
                                    : 3.0 + (double) this.random.nextFloat() * 5.5);
            Fx.spawn()
                    .at(at)
                    .vel(way.x, way.y, way.z)
                    .tex(Fx.Tex.GLOW)
                    .blend(false)
                    .size((0.05F + this.random.nextFloat() * 0.05F) * s, 0.02F * s)
                    .life(0.8F + this.random.nextFloat() * 0.8F)
                    .drag(1.2F)
                    .gravity(14.0F)
                    .colour(main, dark);
        }
    }

    private void cone(Vec3 at, Vec3 dir) {
        int c = this.burstColour();
        float s = this.size.value();

        for (int i = 0; i < 24; i++) {
            Vec3 way = dir.add(
                            ((double) this.random.nextFloat() - 0.5) * 0.9,
                            ((double) this.random.nextFloat() - 0.5) * 0.9 + 0.2,
                            ((double) this.random.nextFloat() - 0.5) * 0.9)
                    .normalize()
                    .scale((2.5 + (double) (this.random.nextFloat() * 5.0F)) * (double) s);
            Fx.spawn()
                    .at(at)
                    .vel(way.x, way.y, way.z)
                    .tex(Fx.Tex.SPARK)
                    .mode(Fx.Mode.STRETCH)
                    .size(0.05F * s, 0.0F)
                    .life(0.35F + this.random.nextFloat() * 0.35F)
                    .drag(2.5F)
                    .gravity(6.0F)
                    .colour(-1, c & 0xFFFFFF);
        }
    }

    private int burstColour() {
        Color c = this.clientColour.on() ? ImpactDraw.clientColour() : this.colour.colour();
        return 0xFF000000 | c.getRGB() & 0xFFFFFF;
    }

    public void world(PoseStack ps, float partial) {
        if (!this.bursts.isEmpty() && mc.level != null) {
            Vec3 cam = mc.getEntityRenderDispatcher().camera.position();
            long now = ImpactDraw.now();
            int c = this.burstColour();
            float s = this.size.value();
            Iterator<Burst> it = this.bursts.iterator();

            while (it.hasNext()) {
                Burst b = (Burst) it.next();
                float t = (float) (now - b.start) / 1000.0F;
                if (t > LIFE[b.style]) {
                    it.remove();
                } else {
                    ps.pushPose();
                    ps.translate(b.at.x - cam.x, b.at.y - cam.y, b.at.z - cam.z);
                    Matrix4f m = ps.last().pose();
                    switch (b.style) {
                        case 0:
                            crack(m, b, t, c, s);
                            break;
                        case 1:
                            flash(m, b, t / LIFE[1], c, s);
                        case 2:
                        default:
                            break;
                        case 3:
                            ring(m, b, t / LIFE[3], c, 0.6F * s, 1.6F * s);
                    }

                    ps.popPose();
                }
            }
        }
    }

    private static void crack(Matrix4f m, Burst b, float t, int c, float s) {
        float grow = Math.min(1.0F, t / 0.12F);
        float fade = 1.0F - smooth((t - 0.6F) / 0.6F);
        BufferBuilder buf = quads();

        for (int i = 0; i < 13; i++) {
            float px = 0.0F;
            float py = 0.0F;

            for (int k = 0; k < 4; k++) {
                float k1 = Math.min(1.0F, grow * 4.0F - (float) k);
                if (k1 <= 0.0F) {
                    break;
                }

                float qx = b.rays[i * 8 + k * 2] * s;
                float qy = b.rays[i * 8 + k * 2 + 1] * s;
                qx = px + (qx - px) * k1;
                qy = py + (qy - py) * k1;
                segment(buf, m, b, px, py, qx, qy, (0.016F - (float) k * 0.003F) * s, -1, 0.95F * fade);
                px = qx;
                py = qy;
            }
        }

        ImpactDraw.draw(ImpactDraw.type(Mode.QUADS, true, false, true), buf);
        if (t < 0.3F) {
            BufferBuilder glass = quads();

            for (int i = 0; i < 13; i++) {
                int j = (i + 1) % 13;
                float ax = b.rays[i * 8 + 6] * s * grow;
                float ay = b.rays[i * 8 + 7] * s * grow;
                float bx = b.rays[j * 8 + 6] * s * grow;
                float by = b.rays[j * 8 + 7] * s * grow;
                corner(glass, m, b, 0.0F, 0.0F, 0.0F, c, 0.16F);
                corner(glass, m, b, ax, ay, 0.0F, c, 0.16F);
                corner(glass, m, b, bx, by, 0.0F, c, 0.16F);
                corner(glass, m, b, bx, by, 0.0F, c, 0.16F);
            }

            ImpactDraw.draw(ImpactDraw.type(Mode.QUADS, true, false, false), glass);
        } else {
            float ft = t - 0.3F;
            BufferBuilder falling = quads();

            for (int i = 0; i < 13; i++) {
                int j = (i + 1) % 13;
                float ox = b.shards[i * 6] * ft * s;
                float oy = b.shards[i * 6 + 1] * ft * s;
                float drop = 7.5F * ft * ft;
                float along = b.shards[i * 6 + 2] * ft * s * 0.3F;
                float spin = b.shards[i * 6 + 3] * ft;
                float cx = (b.rays[i * 8 + 6] + b.rays[j * 8 + 6]) / 3.0F * s;
                float cy = (b.rays[i * 8 + 7] + b.rays[j * 8 + 7]) / 3.0F * s;
                float a = 0.5F * fade * b.shards[i * 6 + 4];
                float[][] tri = new float[][] {
                    {0.0F, 0.0F},
                    {b.rays[i * 8 + 6] * s, b.rays[i * 8 + 7] * s},
                    {b.rays[j * 8 + 6] * s, b.rays[j * 8 + 7] * s}
                };

                for (int k = 0; k < 4; k++) {
                    float[] p = tri[Math.min(k, 2)];
                    float rx = p[0] - cx;
                    float ry = p[1] - cy;
                    float x = cx + ox + rx * (float) Math.cos((double) spin) - ry * (float) Math.sin((double) spin);
                    float y = cy + oy + rx * (float) Math.sin((double) spin) + ry * (float) Math.cos((double) spin);
                    Vec3 w = b.side.scale((double) x)
                            .add(b.up.scale((double) y))
                            .add(b.dir.scale((double) along))
                            .add(0.0, (double) (-drop), 0.0);
                    falling.addVertex(m, (float) w.x, (float) w.y, (float) w.z).setColor(argb(c, a));
                }
            }

            ImpactDraw.draw(ImpactDraw.type(Mode.QUADS, true, false, false), falling);
        }
    }

    private static void flash(Matrix4f m, Burst b, float u, int c, float s) {
        float open = 1.0F - (1.0F - u) * (1.0F - u);
        float a = 1.0F - u;
        star(m, b, 18, (0.3F + 0.9F * open) * s, (0.12F + 0.35F * open) * s, c, a);
        star(m, b, 18, (0.15F + 0.5F * open) * s, (0.07F + 0.2F * open) * s, -1, a);
    }

    private static void star(Matrix4f m, Burst b, int points, float outer, float inner, int c, float a) {
        BufferBuilder buf = quads();

        for (int i = 0; i < points; i++) {
            double a0 = (double) i * Math.PI * 2.0 / (double) points;
            double a1 = (double) (i + 1) * Math.PI * 2.0 / (double) points;
            float r0 = i % 2 == 0 ? outer : inner;
            float r1 = i % 2 == 0 ? inner : outer;
            corner(buf, m, b, 0.0F, 0.0F, 0.0F, c, a);
            corner(buf, m, b, (float) Math.cos(a0) * r0, (float) Math.sin(a0) * r0, 0.0F, c, a);
            corner(buf, m, b, (float) Math.cos(a1) * r1, (float) Math.sin(a1) * r1, 0.0F, c, a);
            corner(buf, m, b, (float) Math.cos(a1) * r1, (float) Math.sin(a1) * r1, 0.0F, c, a);
        }

        ImpactDraw.draw(ImpactDraw.type(Mode.QUADS, true, false, true), buf);
    }

    private static void ring(Matrix4f m, Burst b, float u, int c, float from, float to) {
        float r = from + (to - from) * (1.0F - (1.0F - u) * (1.0F - u));
        float w = 0.06F * (1.0F - u) + 0.01F;
        BufferBuilder buf = quads();
        int n = 32;

        for (int i = 0; i < n; i++) {
            double a0 = (double) i * Math.PI * 2.0 / (double) n;
            double a1 = (double) (i + 1) * Math.PI * 2.0 / (double) n;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            corner(buf, m, b, c0 * (r - w), s0 * (r - w), 0.0F, c, 1.0F - u);
            corner(buf, m, b, c0 * (r + w), s0 * (r + w), 0.0F, c, 0.0F);
            corner(buf, m, b, c1 * (r + w), s1 * (r + w), 0.0F, c, 0.0F);
            corner(buf, m, b, c1 * (r - w), s1 * (r - w), 0.0F, c, 1.0F - u);
        }

        ImpactDraw.draw(ImpactDraw.type(Mode.QUADS, true, false, true), buf);
    }

    private static BufferBuilder quads() {
        return Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void segment(
            BufferBuilder buf, Matrix4f m, Burst b, float x0, float y0, float x1, float y1, float w, int c, float a) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt((double) (dx * dx + dy * dy));
        if (!(len < 1.0E-5F)) {
            float nx = -dy / len * w;
            float ny = dx / len * w;
            corner(buf, m, b, x0 + nx, y0 + ny, 0.0F, c, a);
            corner(buf, m, b, x0 - nx, y0 - ny, 0.0F, c, a);
            corner(buf, m, b, x1 - nx, y1 - ny, 0.0F, c, a * 0.6F);
            corner(buf, m, b, x1 + nx, y1 + ny, 0.0F, c, a * 0.6F);
        }
    }

    private static void corner(BufferBuilder buf, Matrix4f m, Burst b, float x, float y, float z, int c, float a) {
        Vec3 p = b.side.scale((double) x).add(b.up.scale((double) y)).add(b.dir.scale((double) z - 0.07));
        buf.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(argb(c, a));
    }

    private static int argb(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | c & 0xFFFFFF;
    }

    private static float smooth(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    private static final class Track {
        final Deque<double[]> moves = new ArrayDeque<>();
        final Set<Integer> hit = new HashSet<>();
        double x;
        double y;
        double z;
        boolean seen;
        int lastTick;

        void move(double x0, double y0, double z0, double x1, double y1, double z1, int tick) {
            this.moves.addLast(new double[] {x0, y0, z0, x1, y1, z1, (double) tick});

            while (this.moves.size() > 10) {
                this.moves.removeFirst();
            }
        }
    }

    private static final class Burst {
        final Vec3 at;
        final Vec3 dir;
        final Vec3 side;
        final Vec3 up;
        final long start;
        final int style;
        final float[] rays = new float[104];
        final float[] shards = new float[78];

        Burst(Vec3 at, Vec3 dir, long start, int style, Random r) {
            this.at = at;
            this.dir = dir;
            this.start = start;
            this.style = style;
            Vec3 helper = Math.abs(dir.y) > 0.98 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(0.0, 1.0, 0.0);
            this.side = helper.cross(dir).normalize();
            this.up = dir.cross(this.side).normalize();

            for (int i = 0; i < 13; i++) {
                double a = (double) i * Math.PI * 2.0 / 13.0 + ((double) r.nextFloat() - 0.5) * 0.35;
                float len = 0.55F + r.nextFloat() * 0.35F;

                for (int k = 0; k < 4; k++) {
                    float f = (float) (k + 1) / 4.0F * len;
                    double wobble = a + ((double) r.nextFloat() - 0.5) * 0.25;
                    this.rays[i * 8 + k * 2] = (float) Math.cos(wobble) * f;
                    this.rays[i * 8 + k * 2 + 1] = (float) Math.sin(wobble) * f;
                }

                this.shards[i * 6] = (float) Math.cos(a) * (0.5F + r.nextFloat() * 1.4F);
                this.shards[i * 6 + 1] = (float) Math.sin(a) * (0.5F + r.nextFloat() * 1.4F);
                this.shards[i * 6 + 2] = 1.1F + r.nextFloat() * 2.4F;
                this.shards[i * 6 + 3] = (r.nextFloat() - 0.5F) * 9.0F;
                this.shards[i * 6 + 4] = 0.72F + r.nextFloat() * 0.28F;
                this.shards[i * 6 + 5] = 0.3F + r.nextFloat() * 0.1F;
            }
        }
    }
}
