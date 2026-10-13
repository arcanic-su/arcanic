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

/**
 * Hitboxes module
 *
 * Draws the hitboxes of entities: an outline or corners, with an optional glow and fill.
 */
public final class Hitboxes extends Module {
    public final Setting.Bool players = add(new Setting.Bool("players", "Игроки", true));
    public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Враждебные мобы", true));
    public final Setting.Bool animals = add(new Setting.Bool("animals", "Животные", false));
    public final Setting.Bool items = add(new Setting.Bool("items", "Предметы", false));
    public final Setting.Num range = add(new Setting.Num("range", "Дальность", 48, 8, 128, 4, " бл"));
    public final Setting.Bool walls = add(new Setting.Bool("walls", "Сквозь стены", false));

    public final Setting.Bool outline = add(new Setting.Bool("outline", "Обводка", true));
    public final Setting.Mode style = add(new Setting.Mode("style", "Вид обводки", "Полная", "Полная", "Углы"))
            .showIf(outline::on);
    public final Setting.Num corner = add(new Setting.Num("corner", "Длина углов", .25, .05, .45, .05, ""))
            .showIf(() -> outline.on() && style.is("Углы"));
    public final Setting.Num width =
            add(new Setting.Num("width2", "Толщина", 2, .5, 5, .5, " px")).showIf(outline::on);

    public final Setting.Mode colourMode =
            add(new Setting.Mode("colour_mode", "Окраска", "Тема", "Тема", "Свой", "Здоровье", "Радуга"));
    public final Setting.Colour colourA =
            add(new Setting.Colour("colour_a", "Цвет", false, 0xFFC084FC)).showIf(() -> colourMode.is("Свой"));
    public final Setting.Bool gradient =
            add(new Setting.Bool("gradient", "Градиент", true)).showIf(() -> !colourMode.is("Здоровье"));
    public final Setting.Colour colourB = add(new Setting.Colour("colour_b", "Цвет градиента", false, 0xFF22D3EE))
            .showIf(() -> gradient.on() && colourMode.is("Свой"));
    public final Setting.Bool hover = add(new Setting.Bool("hover", "Цвет при наведении", true));
    public final Setting.Colour hoverColour = add(new Setting.Colour(
                    "hover_colour", "Цвет наведения", false, 0xFFFF3B4E))
            .showIf(hover::on);
    public final Setting.Bool flash = add(new Setting.Bool("flash", "Вспышка при ударе", true));
    public final Setting.Colour flashColour = add(new Setting.Colour("flash_colour", "Цвет вспышки", false, 0xFFFFFFFF))
            .showIf(flash::on);

    public final Setting.Bool glow = add(new Setting.Bool("glow", "Свечение", true));
    public final Setting.Mode glowMode = add(new Setting.Mode(
                    "glow_mode", "Сила свечения", "Обычное", "Обычное", "Сильное"))
            .showIf(glow::on);
    public final Setting.Bool fill = add(new Setting.Bool("fill", "Заливка", false));
    public final Setting.Mode fillType = add(new Setting.Mode(
                    "fill_type", "Тип заливки", "Прозрачная", "Прозрачная", "Переливание"))
            .showIf(fill::on);
    public final Setting.Num fillAlpha = add(new Setting.Num("fill_alpha", "Плотность заливки", 22, 5, 70, 1, "%"))
            .showIf(fill::on);

    public Hitboxes() {
        super(
                "hitboxes",
                "Хитбоксы",
                "Чёткие рамки сущностей: углы, градиент, свечение и заливка",
                Category.PLAYERS,
                false);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private boolean wants(Entity e) {
        if (e == mc().player || e.isRemoved() || (e.isInvisible() && !(e instanceof ItemEntity))) return false;
        if (e instanceof Avatar) return players.on() && !e.isSpectator();
        if (e instanceof ItemEntity) return items.on();
        if (!(e instanceof LivingEntity)) return false;
        return e instanceof Enemy ? mobs.on() : animals.on();
    }

    private int colour(Entity e, float t, float time, int index) {
        int c;
        switch (colourMode.get()) {
            case "Свой" ->
                c = gradient.on()
                        ? Paint.lerp(colourA.argb() | 0xFF000000, colourB.argb() | 0xFF000000, t)
                        : colourA.argb() | 0xFF000000;
            case "Радуга" ->
                c = Paint.hsv(Paint.frac(index * .13f + time * .12f + (gradient.on() ? t * .35f : 0)), .65f, 1);
            case "Здоровье" -> {
                float hp = e instanceof LivingEntity l ? Health.of(l).fraction() : 1;
                c = hp > .5f
                        ? Paint.lerp(0xFFFACC15, 0xFF4ADE80, (hp - .5f) * 2)
                        : Paint.lerp(0xFFF43F5E, 0xFFFACC15, hp * 2);
            }
            default ->
                c = gradient.on()
                        ? Paint.lerp(Theme.accent() | 0xFF000000, Theme.accent2() | 0xFF000000, t)
                        : Theme.accent() | 0xFF000000;
        }
        if (hover.on() && e == mc().crosshairPickEntity) c = Paint.lerp(c, hoverColour.argb() | 0xFF000000, .85f);
        if (flash.on() && e instanceof LivingEntity l && l.hurtTime > 0)
            c = Paint.lerp(c, flashColour.argb() | 0xFF000000, l.hurtTime / 10f);
        return c;
    }

    private record Box(Entity e, AABB b, int index) {}

    public void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        if (mc().level == null || mc().player == null) return;
        double r2 = range.get() * range.get();
        List<Box> boxes = new ArrayList<>();
        int index = 0;
        for (Entity e : mc().level.entitiesForRendering()) {
            if (!wants(e) || e.distanceToSqr(mc().player) > r2) continue;
            double x = e.xo + (e.getX() - e.xo) * pt,
                    y = e.yo + (e.getY() - e.yo) * pt,
                    z = e.zo + (e.getZ() - e.zo) * pt;
            boxes.add(new Box(e, e.getBoundingBox().move(x - e.getX(), y - e.getY(), z - e.getZ()), index++));
        }
        if (boxes.isEmpty()) return;
        PoseStack.Pose pose = ctx.matrices().last();
        float time = Theme.animations ? (System.nanoTime() % 1_000_000_000_000L) / 1e9f : 0;
        double fov = Math.toRadians(mc().options.fov().get());
        float perPx =
                (float) (2 * Math.tan(fov / 2) / Math.max(1, mc().getWindow().getHeight()));
        boolean top = walls.on();

        // one buffer at a time: fills and lines share the translucent type, the glow uses the additive one
        VertexConsumer solid = ctx.consumers().getBuffer(Fx.solid(top));
        for (Box b : boxes) {
            if (fill.on()) fill(solid, pose, cam, b, time);
            if (outline.on()) edges(solid, pose, cam, b, time, perPx, width.f(), 1, false);
        }
        if (glow.on() && outline.on()) {
            VertexConsumer g =
                    ctx.consumers().getBuffer(top ? Fx.typeOnTop(Fx.Tex.LINE_GLOW) : Fx.type(Fx.Tex.LINE_GLOW, true));
            boolean strong = glowMode.is("Сильное");
            for (Box b : boxes) {
                edges(g, pose, cam, b, time, perPx, width.f() * (strong ? 7 : 4.5f) + 4, strong ? .6f : .28f, true);
                if (strong) edges(g, pose, cam, b, time, perPx, width.f() * 14 + 8, .3f, true);
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

    private static final int[][] EDGES = {
        {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

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
        double h = Math.max(1e-3, box.b.maxY - box.b.minY);
        boolean corners = style.is("Углы");
        for (int[] edge : EDGES) {
            Vec3 a = p[edge[0]], b = p[edge[1]];
            if (!corners) {
                segment(vc, pose, cam, box, a, b, h, time, perPx, px, strength, glowPass);
                continue;
            }
            double k = corner.get();
            segment(vc, pose, cam, box, a, a.lerp(b, k), h, time, perPx, px, strength, glowPass);
            segment(vc, pose, cam, box, b.lerp(a, k), b, h, time, perPx, px, strength, glowPass);
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
        Vec3 ra = a.subtract(cam), rb = b.subtract(cam), dir = rb.subtract(ra);
        Vec3 mid = ra.add(rb).scale(.5);
        Vec3 side = dir.cross(mid);
        if (side.lengthSqr() < 1e-12) return;
        double halfA = Math.max(.003, ra.length() * perPx * px / 2),
                halfB = Math.max(.003, rb.length() * perPx * px / 2);
        side = side.normalize();
        int ca = colour(box.e, (float) ((a.y - box.b.minY) / h), time, box.index),
                cb = colour(box.e, (float) ((b.y - box.b.minY) / h), time, box.index);
        if (glowPass) {
            ca = Fx.pm(Px(ca, strength));
            cb = Fx.pm(Px(cb, strength));
        }
        // stretch the ends by half a width so neighbouring edges meet without gaps
        Vec3 ext = dir.normalize();
        Vec3 a0 = ra.subtract(ext.scale(glowPass ? 0 : halfA)), b0 = rb.add(ext.scale(glowPass ? 0 : halfB));
        Vec3 sa = side.scale(halfA), sb = side.scale(halfB);
        Fx.v(vc, pose, (float) (a0.x + sa.x), (float) (a0.y + sa.y), (float) (a0.z + sa.z), 0, 0, ca);
        Fx.v(vc, pose, (float) (a0.x - sa.x), (float) (a0.y - sa.y), (float) (a0.z - sa.z), 0, 1, ca);
        Fx.v(vc, pose, (float) (b0.x - sb.x), (float) (b0.y - sb.y), (float) (b0.z - sb.z), 1, 1, cb);
        Fx.v(vc, pose, (float) (b0.x + sb.x), (float) (b0.y + sb.y), (float) (b0.z + sb.z), 1, 0, cb);
    }

    private static int Px(int c, float a) {
        return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (c & 0xFFFFFF);
    }

    private void fill(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Box box, float time) {
        Vec3[] p = corners(box.b);
        double h = Math.max(1e-3, box.b.maxY - box.b.minY);
        float alpha = fillAlpha.f() / 100f;
        boolean flow = fillType.is("Переливание");
        int bands = flow ? 10 : 1;
        int[][] sides = {{0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
        for (int[] f : sides)
            for (int i = 0; i < bands; i++) {
                float t0 = i / (float) bands, t1 = (i + 1) / (float) bands;
                Vec3 a = p[f[0]].lerp(p[f[3]], t0),
                        b = p[f[1]].lerp(p[f[2]], t0),
                        c = p[f[1]].lerp(p[f[2]], t1),
                        d = p[f[0]].lerp(p[f[3]], t1);
                int c0 = fillColour(box, t0, time, alpha, flow), c1 = fillColour(box, t1, time, alpha, flow);
                quad(vc, pose, cam, a, b, c, d, c0, c0, c1, c1);
            }
        int cTop = fillColour(box, 1, time, alpha, flow), cBot = fillColour(box, 0, time, alpha, flow);
        quad(vc, pose, cam, p[4], p[5], p[6], p[7], cTop, cTop, cTop, cTop);
        quad(vc, pose, cam, p[0], p[1], p[2], p[3], cBot, cBot, cBot, cBot);
    }

    private int fillColour(Box box, float t, float time, float alpha, boolean flow) {
        float a = alpha;
        if (flow) a *= .45f + .55f * (float) (.5 + .5 * Math.sin((t * 2.2 - time * 1.6) * Math.PI));
        return Px(colour(box.e, t, time, box.index), a);
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
        Vec3[] q = {a, b, c, d};
        int[] col = {ca, cb, cc, cd};
        for (int i = 0; i < 4; i++) {
            Vec3 r = q[i].subtract(cam);
            Fx.v(vc, pose, (float) r.x, (float) r.y, (float) r.z, i == 0 || i == 3 ? 0 : 1, i < 2 ? 0 : 1, col[i]);
        }
    }
}
