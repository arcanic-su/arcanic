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
import java.util.List;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Health;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Hitboxes extends Module {
    public final Setting.Bool players = this.add(new Setting.Bool("players", "Игроки", true));
    public final Setting.Bool mobs = this.add(new Setting.Bool("mobs", "Враждебные мобы", true));
    public final Setting.Bool animals = this.add(new Setting.Bool("animals", "Животные", false));
    public final Setting.Bool items = this.add(new Setting.Bool("items", "Предметы", false));
    public final Setting.Num range = this.add(new Setting.Num("range", "Дальность", 48.0, 8.0, 128.0, 4.0, " бл"));
    public final Setting.Bool outline = this.add(new Setting.Bool("outline", "Обводка", true));
    public final Setting.Mode style;
    public final Setting.Num corner;
    public final Setting.Num width;
    public final Setting.Mode colourMode;
    public final Setting.Colour colourA;
    public final Setting.Bool gradient;
    public final Setting.Colour colourB;
    public final Setting.Bool hover;
    public final Setting.Colour hoverColour;
    public final Setting.Bool flash;
    public final Setting.Colour flashColour;
    public final Setting.Bool glow;
    public final Setting.Mode glowMode;
    public final Setting.Bool fill;
    public final Setting.Mode fillType;
    public final Setting.Num fillAlpha;
    private static final int[][] EDGES =
            new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
            };

    public Hitboxes() {
        super(
                "hitboxes",
                "Хитбоксы",
                "Чёткие рамки сущностей: углы, градиент, свечение и заливка",
                Category.PLAYERS,
                false);
        Setting.Mode var10001 = (Setting.Mode)
                this.add(new Setting.Mode("style", "Вид обводки", "Полная", new String[] {"Полная", "Углы"}));
        Setting.Bool var10002 = this.outline;
        this.style = (Setting.Mode) var10001.showIf(var10002::on);
        this.corner = (Setting.Num) (this.add(new Setting.Num("corner", "Длина углов", 0.25, 0.05, 0.45, 0.05, "")))
                .showIf(() -> this.outline.on() && this.style.is("Углы"));
        Setting.Num var1 = this.add(new Setting.Num("width2", "Толщина", 2.0, 0.5, 5.0, 0.5, " px"));
        var10002 = this.outline;
        this.width = (Setting.Num) var1.showIf(var10002::on);
        this.colourMode = this.add(new Setting.Mode(
                "colour_mode", "Окраска", "Тема", new String[] {"Тема", "Свой", "Здоровье", "Радуга"}));
        this.colourA = (this.add(new Setting.Colour("colour_a", "Цвет", false, -4160260)))
                .showIf(() -> this.colourMode.is("Свой"));
        this.gradient = (this.add(new Setting.Bool("gradient", "Градиент", true)))
                .showIf(() -> !this.colourMode.is("Здоровье"));
        this.colourB = (Setting.Colour) (this.add(new Setting.Colour("colour_b", "Цвет градиента", false, -14494738)))
                .showIf(() -> this.gradient.on() && this.colourMode.is("Свой"));
        this.hover = this.add(new Setting.Bool("hover", "Цвет при наведении", true));
        Setting.Colour var2 = this.add(new Setting.Colour("hover_colour", "Цвет наведения", false, -50354));
        var10002 = this.hover;
        this.hoverColour = (Setting.Colour) var2.showIf(var10002::on);
        this.flash = this.add(new Setting.Bool("flash", "Вспышка при ударе", true));
        var2 = this.add(new Setting.Colour("flash_colour", "Цвет вспышки", false, -1));
        var10002 = this.flash;
        this.flashColour = (Setting.Colour) var2.showIf(var10002::on);
        this.glow = this.add(new Setting.Bool("glow", "Свечение", true));
        Setting.Mode var4 = this.add(
                new Setting.Mode("glow_mode", "Сила свечения", "Обычное", new String[] {"Обычное", "Сильное"}));
        var10002 = this.glow;
        this.glowMode = (Setting.Mode) var4.showIf(var10002::on);
        this.fill = this.add(new Setting.Bool("fill", "Заливка", false));
        var4 = this.add(
                new Setting.Mode("fill_type", "Тип заливки", "Прозрачная", new String[] {"Прозрачная", "Переливание"}));
        var10002 = this.fill;
        this.fillType = (Setting.Mode) var4.showIf(var10002::on);
        Setting.Num var6 = this.add(new Setting.Num("fill_alpha", "Плотность заливки", 22.0, 5.0, 70.0, 1.0, "%"));
        var10002 = this.fill;
        this.fillAlpha = (Setting.Num) var6.showIf(var10002::on);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private boolean wants(Entity e) {
        if (e != mc().player && !e.isRemoved() && (!e.isInvisible() || e instanceof ItemEntity)) {
            if (!(e instanceof Avatar)) {
                if (e instanceof ItemEntity) {
                    return this.items.on();
                } else if (!(e instanceof LivingEntity)) {
                    return false;
                } else {
                    return e instanceof Enemy ? this.mobs.on() : this.animals.on();
                }
            } else {
                return this.players.on() && !e.isSpectator();
            }
        } else {
            return false;
        }
    }

    private int colour(Entity e, float t, float time, int index) {
        int c;
        switch ((String) this.colourMode.get()) {
            case "Свой":
                c = this.gradient.on()
                        ? Paint.lerp(this.colourA.argb() | 0xFF000000, this.colourB.argb() | 0xFF000000, t)
                        : this.colourA.argb() | 0xFF000000;
                break;
            case "Радуга":
                c = Paint.hsv(
                        Paint.frac((float) index * 0.13F + time * 0.12F + (this.gradient.on() ? t * 0.35F : 0.0F)),
                        0.65F,
                        1.0F);
                break;
            case "Здоровье":
                float var10000;
                if (e instanceof LivingEntity l) {
                    var10000 = Health.of(l).fraction();
                } else {
                    var10000 = 1.0F;
                }

                float hp = var10000;
                c = hp > 0.5F
                        ? Paint.lerp(-340971, -11870592, (hp - 0.5F) * 2.0F)
                        : Paint.lerp(-770210, -340971, hp * 2.0F);
                break;
            default:
                c = this.gradient.on()
                        ? Paint.lerp(Theme.accent() | 0xFF000000, Theme.accent2() | 0xFF000000, t)
                        : Theme.accent() | 0xFF000000;
        }

        if (this.hover.on() && e == mc().crosshairPickEntity) {
            c = Paint.lerp(c, this.hoverColour.argb() | 0xFF000000, 0.85F);
        }

        if (this.flash.on() && e instanceof LivingEntity l) {
            if (l.hurtTime > 0) {
                c = Paint.lerp(c, this.flashColour.argb() | 0xFF000000, (float) l.hurtTime / 10.0F);
            }
        }

        return c;
    }

    public void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        if (mc().level != null && mc().player != null) {
            double r2 = (Double) this.range.get() * (Double) this.range.get();
            List<Box> boxes = new ArrayList<>();
            int index = 0;

            for (Entity e : mc().level.entitiesForRendering()) {
                if (this.wants(e) && !(e.distanceToSqr((Entity) mc().player) > r2)) {
                    double x = e.xo + (e.getX() - e.xo) * (double) pt;
                    double y = e.yo + (e.getY() - e.yo) * (double) pt;
                    double z = e.zo + (e.getZ() - e.zo) * (double) pt;
                    boxes.add(new Box(e, e.getBoundingBox().move(x - e.getX(), y - e.getY(), z - e.getZ()), index++));
                }
            }

            if (!boxes.isEmpty()) {
                PoseStack.Pose pose = ctx.matrices().last();
                float time = Theme.animations ? (float) (System.nanoTime() % 1000000000000L) / 1.0E9F : 0.0F;
                double fov =
                        Math.toRadians((double) (Integer) mc().options.fov().get());
                float perPx = (float) (2.0
                        * Math.tan(fov / 2.0)
                        / (double) Math.max(1, mc().getWindow().getHeight()));
                VertexConsumer solid = ctx.consumers().getBuffer(Fx.solid(false));

                for (Box b : boxes) {
                    if (this.fill.on()) {
                        this.fill(solid, pose, cam, b, time);
                    }

                    if (this.outline.on()) {
                        this.edges(solid, pose, cam, b, time, perPx, this.width.f(), 1.0F, false);
                    }
                }

                if (this.glow.on() && this.outline.on()) {
                    VertexConsumer g = ctx.consumers().getBuffer(Fx.type(Fx.Tex.LINE_GLOW, true));
                    boolean strong = this.glowMode.is("Сильное");

                    for (Box b : boxes) {
                        double dist = b.b.getCenter().distanceTo(cam);
                        float near = 1.0F - (float) Math.max(0.0, Math.min(1.0, (dist - 14.0) / 18.0));
                        if (!(near <= 0.01F)) {
                            this.edges(
                                    g,
                                    pose,
                                    cam,
                                    b,
                                    time,
                                    perPx,
                                    this.width.f() * (strong ? 7.0F : 4.5F) + 4.0F,
                                    (strong ? 0.6F : 0.28F) * near,
                                    true);
                            if (strong) {
                                this.edges(
                                        g, pose, cam, b, time, perPx, this.width.f() * 14.0F + 8.0F, 0.3F * near, true);
                            }
                        }
                    }
                }
            }
        }
    }

    private static Vec3[] corners(AABB b) {
        return new Vec3[] {
            new Vec3(b.minX, b.minY, b.minZ),
            new Vec3(b.maxX, b.minY, b.minZ),
            new Vec3(b.maxX, b.minY, b.maxZ),
            new Vec3(b.minX, b.minY, b.maxZ),
            new Vec3(b.minX, b.maxY, b.minZ),
            new Vec3(b.maxX, b.maxY, b.minZ),
            new Vec3(b.maxX, b.maxY, b.maxZ),
            new Vec3(b.minX, b.maxY, b.maxZ)
        };
    }

    private void edges(
            VertexConsumer vc,
            PoseStack.Pose pose,
            Vec3 cam,
            Box box,
            float time,
            float perPx,
            float px,
            float strength,
            boolean glowPass) {
        Vec3[] p = corners(box.b);
        double h = Math.max(0.001, box.b.maxY - box.b.minY);
        boolean corners = this.style.is("Углы");

        for (int[] edge : EDGES) {
            Vec3 a = p[edge[0]];
            Vec3 b = p[edge[1]];
            if (!corners) {
                this.segment(vc, pose, cam, box, a, b, h, time, perPx, px, strength, glowPass);
            } else {
                double k = (Double) this.corner.get();
                this.segment(vc, pose, cam, box, a, a.lerp(b, k), h, time, perPx, px, strength, glowPass);
                this.segment(vc, pose, cam, box, b.lerp(a, k), b, h, time, perPx, px, strength, glowPass);
            }
        }
    }

    private void segment(
            VertexConsumer vc,
            PoseStack.Pose pose,
            Vec3 cam,
            Box box,
            Vec3 a,
            Vec3 b,
            double h,
            float time,
            float perPx,
            float px,
            float strength,
            boolean glowPass) {
        Vec3 ra = a.subtract(cam);
        Vec3 rb = b.subtract(cam);
        Vec3 dir = rb.subtract(ra);
        Vec3 mid = ra.add(rb).scale(0.5);
        Vec3 side = dir.cross(mid);
        if (!(side.lengthSqr() < 1.0E-12)) {
            double cap = Math.min(box.b.getXsize(), box.b.getYsize()) * (glowPass ? 0.12 : 0.035);
            double halfA = Math.min(cap, Math.max(0.002, ra.length() * (double) perPx * (double) px / 2.0));
            double halfB = Math.min(cap, Math.max(0.002, rb.length() * (double) perPx * (double) px / 2.0));
            side = side.normalize();
            int ca = this.colour(box.e, (float) ((a.y - box.b.minY) / h), time, box.index);
            int cb = this.colour(box.e, (float) ((b.y - box.b.minY) / h), time, box.index);
            if (glowPass) {
                ca = Fx.pm(Px(ca, strength));
                cb = Fx.pm(Px(cb, strength));
            }

            Vec3 ext = dir.normalize();
            Vec3 a0 = ra.subtract(ext.scale(glowPass ? 0.0 : halfA));
            Vec3 b0 = rb.add(ext.scale(glowPass ? 0.0 : halfB));
            Vec3 sa = side.scale(halfA);
            Vec3 sb = side.scale(halfB);
            Fx.v(vc, pose, (float) (a0.x + sa.x), (float) (a0.y + sa.y), (float) (a0.z + sa.z), 0.0F, 0.0F, ca);
            Fx.v(vc, pose, (float) (a0.x - sa.x), (float) (a0.y - sa.y), (float) (a0.z - sa.z), 0.0F, 1.0F, ca);
            Fx.v(vc, pose, (float) (b0.x - sb.x), (float) (b0.y - sb.y), (float) (b0.z - sb.z), 1.0F, 1.0F, cb);
            Fx.v(vc, pose, (float) (b0.x + sb.x), (float) (b0.y + sb.y), (float) (b0.z + sb.z), 1.0F, 0.0F, cb);
        }
    }

    private static int Px(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | c & 0xFFFFFF;
    }

    private void fill(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Box box, float time) {
        Vec3[] p = corners(box.b);
        double h = Math.max(0.001, box.b.maxY - box.b.minY);
        float alpha = this.fillAlpha.f() / 100.0F;
        boolean flow = this.fillType.is("Переливание");
        int bands = flow ? 10 : 1;
        int[][] sides = new int[][] {{0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};

        for (int[] f : sides) {
            for (int i = 0; i < bands; ++i) {
                float t0 = (float) i / (float) bands;
                float t1 = (float) (i + 1) / (float) bands;
                Vec3 a = p[f[0]].lerp(p[f[3]], (double) t0);
                Vec3 b = p[f[1]].lerp(p[f[2]], (double) t0);
                Vec3 c = p[f[1]].lerp(p[f[2]], (double) t1);
                Vec3 d = p[f[0]].lerp(p[f[3]], (double) t1);
                int c0 = this.fillColour(box, t0, time, alpha, flow);
                int c1 = this.fillColour(box, t1, time, alpha, flow);
                quad(vc, pose, cam, a, b, c, d, c0, c0, c1, c1);
            }
        }

        int cTop = this.fillColour(box, 1.0F, time, alpha, flow);
        int cBot = this.fillColour(box, 0.0F, time, alpha, flow);
        quad(vc, pose, cam, p[4], p[5], p[6], p[7], cTop, cTop, cTop, cTop);
        quad(vc, pose, cam, p[0], p[1], p[2], p[3], cBot, cBot, cBot, cBot);
    }

    private int fillColour(Box box, float t, float time, float alpha, boolean flow) {
        float a = alpha;
        if (flow) {
            a = alpha
                    * (0.45F
                            + 0.55F
                                    * (float)
                                            (0.5 + 0.5 * Math.sin(((double) t * 2.2 - (double) time * 1.6) * Math.PI)));
        }

        return Px(this.colour(box.e, t, time, box.index), a);
    }

    private static void quad(
            VertexConsumer vc,
            PoseStack.Pose pose,
            Vec3 cam,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            int ca,
            int cb,
            int cc,
            int cd) {
        Vec3[] q = new Vec3[] {a, b, c, d};
        int[] col = new int[] {ca, cb, cc, cd};

        for (int i = 0; i < 4; ++i) {
            Vec3 r = q[i].subtract(cam);
            Fx.v(
                    vc,
                    pose,
                    (float) r.x,
                    (float) r.y,
                    (float) r.z,
                    i != 0 && i != 3 ? 1.0F : 0.0F,
                    i < 2 ? 0.0F : 1.0F,
                    col[i]);
        }
    }

    private static record Box(Entity e, AABB b, int index) {}
}
