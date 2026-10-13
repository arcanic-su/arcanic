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

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Health;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Projectile and elytra trails, nametags, TNT timer, consume ESP, zoom and clean screen. */
public final class ArcVisuals {
    private ArcVisuals() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static Vec3 lerp(Entity e, float pt) {
        return new Vec3(e.xo + (e.getX() - e.xo) * pt, e.yo + (e.getY() - e.yo) * pt, e.zo + (e.getZ() - e.zo) * pt);
    }

    public abstract static class Colored extends Module {
        public final Setting.Mode palette =
                add(new Setting.Mode("palette", "Окраска", "Градиент", "Градиент", "Радуга", "Цвет"));
        public final Setting.Colour color =
                add(new Setting.Colour("color", "Цвет", true)).showIf(() -> palette.is("Цвет"));

        Colored(String id, String title, String text, Category category) {
            super(id, title, text, category, false);
        }

        public int color(float f) {
            return VisualFx.colourFor(palette.get(), color, f);
        }
    }

    record Point(Vec3 at, long born) {}

    public static final class ProjectileTrails extends Colored {
        final Setting.Num life = add(new Setting.Num("life", "Длина следа", 1, .2, 3, .1, " с"));
        final Setting.Num width = add(new Setting.Num("width", "Толщина", .06, .015, .2, .005, " бл"));
        final Map<Integer, ArrayDeque<Point>> trails = new HashMap<>();
        private Object level;

        public ProjectileTrails() {
            super("projectile_trails", "Следы снарядов", "Неоновые следы стрел, жемчуга и снежков", Category.EFFECTS);
        }

        @Override
        public void tick() {
            if (level != mc().level) {
                trails.clear();
                level = mc().level;
            }
            if (mc().level == null || mc().player == null) return;
            long now = System.currentTimeMillis();
            int count = 0;
            for (Entity e : mc().level.entitiesForRendering())
                if (e instanceof Projectile && e.distanceToSqr(mc().player) < 96 * 96 && count++ < 96) {
                    var points = trails.computeIfAbsent(e.getId(), id -> new ArrayDeque<>());
                    if (e.getDeltaMovement().lengthSqr() > .0001
                            && (points.isEmpty() || points.getLast().at.distanceToSqr(e.position()) > .002))
                        points.add(new Point(e.position(), now));
                    while (points.size() > 128) points.removeFirst();
                }
            trails.values().forEach(points -> {
                while (!points.isEmpty() && now - points.getFirst().born > life.get() * 1000) points.removeFirst();
            });
            trails.values().removeIf(Deque::isEmpty);
        }

        @Override
        protected void onDisable() {
            trails.clear();
        }

        void render(WorldRenderContext ctx, Vec3 cam) {
            long now = System.currentTimeMillis();
            for (var points : trails.values()) {
                Point last = null;
                for (Point p : points) {
                    float fade = 1 - (float) ((now - p.born) / (life.get() * 1000));
                    if (last != null)
                        line(ctx, cam, last.at, p.at, alpha(color(fade), fade), width.f() * Math.max(.1f, fade));
                    last = p;
                }
            }
        }
    }

    public static final class ElytraTrails extends Colored {
        final Setting.Num life = add(new Setting.Num("life", "Длина", 1.2, .3, 3, .1, " с"));
        final Setting.Num width = add(new Setting.Num("width", "Ширина", .08, .02, .3, .01, " бл"));
        final ArrayDeque<Point> left = new ArrayDeque<>(), right = new ArrayDeque<>();
        private Object level;

        public ElytraTrails() {
            super("elytra_trails", "Следы элитр", "Два светящихся шлейфа от концов крыльев в полёте", Category.EFFECTS);
        }

        @Override
        public void tick() {
            if (level != mc().level) {
                left.clear();
                right.clear();
                level = mc().level;
            }
            var p = mc().player;
            long now = System.currentTimeMillis();
            if (p != null && p.isFallFlying()) {
                double yaw = Math.toRadians(p.getYRot());
                Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(.85);
                Vec3 at = p.position().add(0, .5, 0).subtract(p.getLookAngle().scale(.25));
                left.add(new Point(at.add(side), now));
                right.add(new Point(at.subtract(side), now));
            }
            for (var points : List.of(left, right))
                while (!points.isEmpty() && now - points.getFirst().born > life.get() * 1000) points.removeFirst();
        }

        @Override
        protected void onDisable() {
            left.clear();
            right.clear();
        }

        void render(WorldRenderContext ctx, Vec3 cam) {
            long now = System.currentTimeMillis();
            for (var points : List.of(left, right)) {
                Point last = null;
                for (Point p : points) {
                    float fade = 1 - (float) ((now - p.born) / (life.get() * 1000));
                    if (last != null)
                        line(ctx, cam, last.at, p.at, alpha(color(fade), fade), width.f() * Math.max(.1f, fade));
                    last = p;
                }
            }
        }
    }

    public static final class NameTags extends Colored {
        final Setting.Bool health = add(new Setting.Bool("health", "Здоровье", true));
        final Setting.Bool distance = add(new Setting.Bool("distance", "Расстояние", true));
        final Setting.Num range = add(new Setting.Num("range", "Дальность", 48, 8, 96, 4, " бл"));

        public NameTags() {
            super("nametags", "Никнеймы", "Карточки над игроками: имя, здоровье и расстояние", Category.PLAYERS);
        }

        public boolean replaces(Entity e) {
            return enabled()
                    && mc().player != null
                    && e instanceof Player
                    && e != mc().player
                    && !e.isInvisible()
                    && e.distanceToSqr(mc().player) < range.get() * range.get();
        }

        void render(GuiGraphics g, float pt) {
            for (Player p : mc().level.players())
                if (p != mc().player && !p.isInvisible() && p.distanceToSqr(mc().player) < range.get() * range.get()) {
                    Vec3 at = lerp(p, pt).add(0, p.getBbHeight() + .4, 0);
                    if (!visible(at)) continue;
                    String label = p.getName().getString();
                    if (health.on()) label += "  " + Health.of(p).text() + " HP";
                    if (distance.on()) label += "  " + Math.round(p.distanceTo(mc().player)) + Lang.tr(" м");
                    pill(g, at, label, color(0));
                }
        }
    }

    public static final class TNTTimer extends Colored {
        final Setting.Num range = add(new Setting.Num("range", "Дальность", 48, 8, 96, 4, " бл"));

        public TNTTimer() {
            super("tnt_timer", "Таймер TNT", "Обратный отсчёт и полоса запала над динамитом", Category.COMBAT);
        }

        void render(GuiGraphics g, float pt) {
            for (Entity e : mc().level.entitiesForRendering())
                if (e instanceof PrimedTnt t && e.distanceToSqr(mc().player) < range.get() * range.get()) {
                    Vec3 at = lerp(t, pt).add(0, 1.35, 0);
                    if (!visible(at)) continue;
                    int c = t.getFuse() < 20 ? 0xFFFF647F : color(0);
                    pill(
                            g,
                            at,
                            "TNT · "
                                    + String.format(
                                            Locale.ROOT, Lang.tr("%.1f с"), Math.max(0, t.getFuse() - pt) / 20.0),
                            c);
                    var screen = Projection.toScreen(at, Px.width(), Px.height());
                    if (screen != null) {
                        int x = (int) screen[0] - 40, y = (int) screen[1] + 15;
                        Px.round(g, x, y, 80, 3, 1, 0x504C435C);
                        Px.round(g, x, y, (int) (80 * Math.min(1, t.getFuse() / 80f)), 3, 1, c);
                    }
                }
        }
    }

    public static final class ConsumeESP extends Colored {
        final Setting.Bool self = add(new Setting.Bool("self", "Себя в третьем лице", true));
        final Setting.Bool itemName = add(new Setting.Bool("item_name", "Название предмета", false));
        final Setting.Num range = add(new Setting.Num("range", "Дальность", 32, 8, 64, 4, " бл"));
        // where each eater's plate is drawn and since when: the position follows the head smoothly
        private final java.util.Map<Integer, double[]> shown = new java.util.HashMap<>();

        public ConsumeESP() {
            super(
                    "consume_esp",
                    "Индикатор еды",
                    "Сколько осталось есть или пить игроку рядом: над его ником",
                    Category.PLAYERS);
        }

        void render(GuiGraphics g, float pt) {
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            long now = System.currentTimeMillis();
            for (Entity e : mc().level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity p) || !p.isUsingItem()) continue;
                if (e.distanceToSqr(mc().player) > range.get() * range.get()) continue;
                if (p == mc().player
                        && (!self.on() || mc().options.getCameraType().isFirstPerson())) continue;
                var item = p.getUseItem();
                var use = item.getUseAnimation();
                if (use != ItemUseAnimation.EAT && use != ItemUseAnimation.DRINK) continue;
                // above the nick, not on it
                Vec3 at = lerp(p, pt).add(0, p.getBbHeight() + (p instanceof Player ? .95 : .5), 0);
                if (!visible(at)) continue;
                var screen = Projection.toScreen(at, Px.width(), Px.height());
                if (!onScreen(screen)) continue;
                seen.add(e.getId());
                double[] st = shown.computeIfAbsent(e.getId(), k -> new double[] {screen[0], screen[1], now});
                st[0] += (screen[0] - st[0]) * .6;
                st[1] += (screen[1] - st[1]) * .6;
                float appear = (float) Math.max(0, Math.min(1, (now - st[2]) / 150));
                plate(g, p, item, (float) st[0], (float) st[1], appear, pt);
            }
            shown.keySet().retainAll(seen);
        }

        /** A round badge over the head: the item, a ring that fills as they eat and the seconds left. */
        private void plate(GuiGraphics g, LivingEntity p, ItemStack item, float cx, float bottom, float a, float pt) {
            float dur = Math.max(1f, item.getUseDuration(p)), used = p.getTicksUsingItem() + pt;
            float progress = Math.min(1, used / dur);
            double dist = Math.sqrt(p.distanceToSqr(mc().player));
            float u = (float) (Px.height() / 540.0) * (float) Math.max(.75, 1 - dist / range.get() * .3);
            int c = color(0) | 0xFF000000;
            int r = Math.round(11 * u), thick = Math.max(2, Math.round(2 * u)), fs = Math.max(8, Math.round(5 * u));
            String left = String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, (dur - used) / 20f)) + Lang.tr("с");
            int pillW = Px.textWidth(left, Px.Weight.BOLD, fs) + Math.round(7 * u), pillH = fs + Math.round(4 * u);
            int ccx = Math.round(cx), ccy = Math.round(bottom - pillH - Math.round(2 * u) - r);
            float old = Px.fade;
            // pops in from a little smaller; a soft pulse once it is almost done
            float k = .85f + .15f * a, near = progress > .85f ? (progress - .85f) / .15f : 0;
            Px.fade = old * a;
            g.pose().pushMatrix();
            try {
                g.pose().translate(ccx, ccy);
                g.pose().scale(k, k);
                g.pose().translate(-ccx, -ccy);
                Px.shadow(g, ccx - r, ccy - r, r * 2, r * 2, Math.round(6 * u), 0x60000000);
                if (near > 0) Px.circle(g, ccx, ccy, r + Math.round(3 * u), Px.a(c, Math.round(40 * near)));
                Px.circle(g, ccx, ccy, r, 0xF00B0A12);
                Px.arc(g, ccx, ccy, r - thick / 2f, thick, 0, Math.PI * 2, 0x22FFFFFF, 0x22FFFFFF);
                if (progress > 0)
                    Px.arc(
                            g,
                            ccx,
                            ccy,
                            r - thick / 2f,
                            thick,
                            -Math.PI / 2,
                            Math.PI * 2 * progress,
                            Px.mix(c, 0xFFFFFFFF, .25f),
                            c);
                int is = Math.round(r * 1.05f);
                HudElements.item(g, item, ccx - is / 2, ccy - is / 2, is);
                // the seconds under the badge
                int px = ccx - pillW / 2, py = ccy + r + Math.round(2 * u);
                Px.round(g, px, py, pillW, pillH, pillH / 2, 0xE00B0A12);
                Px.outline(g, px, py, pillW, pillH, pillH / 2, Px.a(c, 90));
                Px.textCenter(g, left, ccx, py + (pillH - fs) / 2, Px.Weight.BOLD, fs, Px.mix(c, 0xFFFFFFFF, .35f));
                if (itemName.on()) {
                    String name =
                            Px.ellipsize(item.getHoverName().getString(), Px.Weight.SEMIBOLD, fs, Math.round(90 * u));
                    var tc = item.getStyledHoverName().getStyle().getColor();
                    int nx = ccx + r + Math.round(5 * u),
                            nw = Px.textWidth(name, Px.Weight.SEMIBOLD, fs) + Math.round(8 * u);
                    Px.round(g, nx, ccy - pillH / 2, nw, pillH, pillH / 2, 0xE00B0A12);
                    Px.text(
                            g,
                            name,
                            nx + Math.round(4 * u),
                            ccy - fs / 2,
                            Px.Weight.SEMIBOLD,
                            fs,
                            tc == null ? 0xFFF4F6FA : 0xFF000000 | tc.getValue());
                }
            } finally {
                g.pose().popMatrix();
                Px.fade = old;
            }
        }
    }

    public static final class Zoom extends Module {
        public final Setting.Num factor = add(new Setting.Num("factor", "Приближение", 3, 1.5, 8, .25, "×"));
        public final Setting.Mode key =
                add(new Setting.Mode("key", "Клавиша удержания", "C", "C", "V", "Z", "Левый Alt"));
        private float blend;
        private long last = System.nanoTime();

        public Zoom() {
            super("zoom", "Плавный зум", "Удерживай C для плавного приближения камеры", Category.WORLD, false);
        }

        public float fov(float original) {
            long now = System.nanoTime();
            float dt = Math.min(.1f, (now - last) / 1e9f);
            last = now;
            int code =
                    switch (key.get()) {
                        case "V" -> 86;
                        case "Z" -> 90;
                        case "Левый Alt" -> 342;
                        default -> 67;
                    };
            boolean held = enabled()
                    && mc().screen == null
                    && mc().player != null
                    && InputConstants.isKeyDown(mc().getWindow(), code);
            blend += (held ? 1 - blend : -blend) * (1 - (float) Math.exp(-dt * 15));
            return original / (1 + blend * (factor.f() - 1));
        }
    }

    public static final class NoRender extends Module {
        public final Setting.Bool vignette = add(new Setting.Bool("vignette", "Убрать затемнение краёв", true));
        public final Setting.Bool portal = add(new Setting.Bool("portal", "Убрать оверлей портала", true));
        public final Setting.Bool nausea = add(new Setting.Bool("nausea", "Убрать оверлей тошноты", true));

        public NoRender() {
            super("no_render", "Чистый экран", "Скрывает виньетку, портал и экранную тошноту", Category.WORLD, false);
        }
    }

    public static void world(WorldRenderContext ctx) {
        if (mc().level == null || mc().player == null) return;
        Vec3 cam = mc().gameRenderer.getMainCamera().position();
        float pt = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var m = Modules.BLOCK;
        if (m.enabled()) m.render(ctx, cam);
        var trails = Modules.PROJECTILES;
        if (trails.enabled()) trails.render(ctx, cam);
        var wings = Modules.ELYTRA;
        if (wings.enabled()) wings.render(ctx, cam);
        var boxes = Modules.HITBOXES;
        if (boxes.enabled()) boxes.render(ctx, cam, pt);
        var traj = Modules.TRAJECTORIES;
        if (traj.enabled()) traj.render(ctx, cam, pt);
        var trail = Modules.TRAILS;
        if (trail.enabled()) trail.render(ctx, cam, pt);
    }

    public static void hud(GuiGraphics g) {
        if (mc().level == null || mc().player == null) return;
        float pt = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var m = Modules.NAMETAGS;
        if (m.enabled()) m.render(g, pt);
        var tnt = Modules.TNT;
        if (tnt.enabled()) tnt.render(g, pt);
        var consume = Modules.CONSUME;
        if (consume.enabled()) consume.render(g, pt);
        var traj = Modules.TRAJECTORIES;
        if (traj.enabled()) traj.hud(g);
    }

    private static boolean visible(Vec3 at) {
        Vec3 eye = mc().gameRenderer.getMainCamera().position();
        return mc().level
                        .clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc().player))
                        .getType()
                == HitResult.Type.MISS;
    }

    private static boolean onScreen(double[] p) {
        return p != null && p[0] > -150 && p[0] < Px.width() + 150 && p[1] > -40 && p[1] < Px.height() + 40;
    }

    private static void pill(GuiGraphics g, Vec3 at, String text, int c) {
        var p = Projection.toScreen(at, Px.width(), Px.height());
        if (!onScreen(p)) return;
        text = Px.ellipsize(text, Px.Weight.SEMIBOLD, 12, 240);
        int w = Px.textWidth(text, Px.Weight.SEMIBOLD, 12) + 24, x = (int) p[0] - w / 2, y = (int) p[1] - 12;
        Px.shadow(g, x, y, w, 25, 8, 0x50000000);
        Px.round(g, x, y, w, 25, 9, 0xEC15121F);
        Px.outline(g, x, y, w, 25, 9, alpha(c, .6f));
        Px.textCenter(g, text, (int) p[0], y + 6, Px.Weight.SEMIBOLD, 12, 0xFFF5F1FF);
    }

    private static int alpha(int c, float a) {
        return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (c & 0xFFFFFF);
    }

    private static void line(WorldRenderContext ctx, Vec3 cam, Vec3 a, Vec3 b, int c, double width) {
        Vec3 dir = b.subtract(a), side = dir.cross(cam.subtract(a));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize().scale(width / 2);
        quad(ctx, cam, a.add(side), a.subtract(side), b.subtract(side), b.add(side), c);
    }

    private static void quad(WorldRenderContext ctx, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int col) {
        VertexConsumer vc = ctx.consumers().getBuffer(Fx.type(Fx.Tex.WHITE, false));
        var pose = ctx.matrices().last();
        Vec3[] pts = {a, b, c, d};
        for (int i = 0; i < 4; i++) {
            Vec3 p = pts[i].subtract(cam);
            Fx.v(vc, pose, (float) p.x, (float) p.y, (float) p.z, i == 0 || i == 3 ? 0 : 1, i < 2 ? 0 : 1, col);
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
}
