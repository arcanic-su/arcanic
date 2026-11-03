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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Health;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.helper.HolyEvents;
import net.arcanic.visuals.helper.Waypoints;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.mixin.ArrowAccess;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class ArcVisuals {
    private ArcVisuals() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static Vec3 lerp(Entity e, float pt) {
        return new Vec3(
                e.xo + (e.getX() - e.xo) * (double) pt,
                e.yo + (e.getY() - e.yo) * (double) pt,
                e.zo + (e.getZ() - e.zo) * (double) pt);
    }

    public static void world(WorldRenderContext ctx) {
        if (mc().level != null && mc().player != null) {
            Vec3 cam = mc().gameRenderer.getMainCamera().position();
            float pt = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            BlockHighlight m = Modules.BLOCK;
            if (m.enabled()) {
                m.render(ctx, cam);
            }

            ProjectileTrails trails = Modules.PROJECTILES;
            if (trails.enabled()) {
                trails.render(ctx, cam);
            }

            Hitboxes boxes = Modules.HITBOXES;
            if (boxes.enabled()) {
                boxes.render(ctx, cam, pt);
            }

            Trajectories traj = Modules.TRAJECTORIES;
            if (traj.enabled()) {
                traj.render(ctx, cam, pt);
            }

            for (TrapZone trap : Modules.ZONES) {
                if (trap.enabled()) {
                    trap.render(ctx, cam, pt);
                }
            }

            ExplosiveThing thing = Modules.HOLY_THING;
            if (thing.enabled()) {
                thing.render(ctx, cam, pt);
            }

            TntZone tnt = Modules.HOLY_TNT;
            if (tnt.enabled()) {
                tnt.render(ctx, cam, pt);
            }

            DrillHelper drill = Modules.DRILL;
            if (drill.enabled()) {
                drill.render(ctx, cam, pt);
            }

            Modules.FT_HELPER.render(ctx, cam, pt);
            Modules.FT_REGIONS.render(ctx, cam, pt);
            Modules.SEE_INVISIBLE.render(ctx, cam, pt);
            Extras.world(ctx, cam, pt);

            PlayerTrail trail = Modules.TRAILS;
            if (trail.enabled()) {
                trail.render(ctx, cam, pt);
            }
        }
    }

    public static void hud(GuiGraphics g) {
        if (mc().level != null && mc().player != null) {
            float pt = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            NameTags m = Modules.NAMETAGS;
            if (m.enabled()) {
                m.render(g, pt);
            }

            TNTTimer tnt = Modules.TNT;
            if (tnt.enabled()) {
                tnt.render(g, pt);
            }

            ArrowTags arrows = Modules.ARROW_TAGS;
            if (arrows.enabled()) {
                arrows.render(g, pt);
            }

            ConsumeESP consume = Modules.CONSUME;
            if (consume.enabled()) {
                consume.render(g, pt);
            }

            Trajectories traj = Modules.TRAJECTORIES;
            if (traj.enabled()) {
                traj.hud(g);
            }

            for (TrapZone trap : Modules.ZONES) {
                if (trap.enabled()) {
                    trap.hud(g);
                }
            }

            ExplosiveThing thing = Modules.HOLY_THING;
            if (thing.enabled()) {
                thing.hud(g);
            }

            TntZone holyTnt = Modules.HOLY_TNT;
            if (holyTnt.enabled()) {
                holyTnt.hud(g);
            }

            HolyEvents events = Modules.HOLY_EVENTS;
            if (events.enabled()) {
                events.hud(g);
            }

            Waypoints marks = Modules.MARKS;
            if (marks.enabled()) {
                marks.hud(g);
            }

            ClickPearl pearl = Modules.CLICK_PEARL;
            if (pearl.enabled()) {
                pearl.hud(g);
            }

            Modules.FT_HELPER.hud(g);
            Modules.FT_REGIONS.hud(g);
        }
    }

    private static boolean visible(Vec3 at) {
        Vec3 eye = mc().gameRenderer.getMainCamera().position();
        return mc().level
                        .clip(new ClipContext(eye, at, Block.COLLIDER, Fluid.NONE, mc().player))
                        .getType()
                == Type.MISS;
    }

    private static boolean onScreen(double[] p) {
        return p != null
                && p[0] > -150.0
                && p[0] < (double) (Px.width() + 150)
                && p[1] > -40.0
                && p[1] < (double) (Px.height() + 40);
    }

    private static void pill(GuiGraphics g, Vec3 at, String text, int c) {
        double[] p = Projection.toScreen(at, Px.width(), Px.height());
        if (onScreen(p)) {
            text = Px.ellipsize(text, Px.Weight.SEMIBOLD, 12, 240);
            int w = Px.textWidth(text, Px.Weight.SEMIBOLD, 12) + 24;
            int x = (int) p[0] - w / 2;
            int y = (int) p[1] - 12;
            Px.shadow(g, x, y, w, 25, 8, 1342177280);
            Px.round(g, x, y, w, 25, 9, 0xEC15121F);
            Px.outline(g, x, y, w, 25, 9, alpha(c, 0.6F));
            Px.textCenter(g, text, (int) p[0], y + 6, Px.Weight.SEMIBOLD, 12, -658945);
        }
    }

    private static int alpha(int c, float a) {
        return (int) (Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | c & 0xFFFFFF;
    }

    private static void line(WorldRenderContext ctx, Vec3 cam, Vec3 a, Vec3 b, int c, double width) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(cam.subtract(a));
        if (!(side.lengthSqr() < 1.0E-10)) {
            side = side.normalize().scale(width / 2.0);
            quad(ctx, cam, a.add(side), a.subtract(side), b.subtract(side), b.add(side), c);
        }
    }

    private static void quad(WorldRenderContext ctx, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int col) {
        VertexConsumer vc = ctx.consumers().getBuffer(Fx.type(Fx.Tex.WHITE, false));
        PoseStack.Pose pose = ctx.matrices().last();
        Vec3[] pts = new Vec3[] {a, b, c, d};

        for (int i = 0; i < 4; ++i) {
            Vec3 p = pts[i].subtract(cam);
            Fx.v(
                    vc,
                    pose,
                    (float) p.x,
                    (float) p.y,
                    (float) p.z,
                    i != 0 && i != 3 ? 1.0F : 0.0F,
                    i < 2 ? 0.0F : 1.0F,
                    col);
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

    public abstract static class Colored extends Module {
        public final Setting.Mode palette = this.add(
                new Setting.Mode("palette", "Окраска", "Градиент", new String[] {"Градиент", "Радуга", "Цвет"}));
        public final Setting.Colour color =
                (this.add(new Setting.Colour("color", "Цвет", true))).showIf(() -> this.palette.is("Цвет"));

        Colored(String id, String title, String text, Category category) {
            super(id, title, text, category, false);
        }

        public int color(float f) {
            return VisualFx.colourFor((String) this.palette.get(), this.color, f);
        }
    }

    static record Point(Vec3 at, long born) {}

    public static final class ProjectileTrails extends Colored {
        final Setting.Num life =
                (Setting.Num) this.add(new Setting.Num("life", "Длина следа", 1.0, 0.2, 3.0, 0.1, " с"));
        final Setting.Num width = this.add(new Setting.Num("width", "Толщина", 0.06, 0.015, 0.2, 0.005, " бл"));
        final Map<Integer, ArrayDeque<Point>> trails = new HashMap<>();
        private Object level;

        public ProjectileTrails() {
            super("projectile_trails", "Следы снарядов", "Неоновые следы стрел, жемчуга и снежков", Category.EFFECTS);
        }

        public void tick() {
            if (this.level != ArcVisuals.mc().level) {
                this.trails.clear();
                this.level = ArcVisuals.mc().level;
            }

            if (ArcVisuals.mc().level != null && ArcVisuals.mc().player != null) {
                long now = System.currentTimeMillis();
                int count = 0;

                for (Entity e : ArcVisuals.mc().level.entitiesForRendering()) {
                    if (e instanceof Projectile
                            && e.distanceToSqr((Entity) ArcVisuals.mc().player) < 9216.0
                            && count++ < 96) {
                        ArrayDeque<Point> points =
                                (ArrayDeque) this.trails.computeIfAbsent(e.getId(), (id) -> new ArrayDeque<>());
                        if (e.getDeltaMovement().lengthSqr() > 1.0E-4
                                && (points.isEmpty()
                                        || ((Point) points.getLast()).at.distanceToSqr(e.position()) > 0.002)) {
                            points.add(new Point(e.position(), now));
                        }

                        while (points.size() > 128) {
                            points.removeFirst();
                        }
                    }
                }

                this.trails.values().forEach((pointsx) -> {
                    while (!pointsx.isEmpty()
                            && (double) (now - ((Point) pointsx.getFirst()).born) > (Double) this.life.get() * 1000.0) {
                        pointsx.removeFirst();
                    }
                });
                this.trails.values().removeIf(Collection::isEmpty);
            }
        }

        protected void onDisable() {
            this.trails.clear();
        }

        void render(WorldRenderContext ctx, Vec3 cam) {
            long now = System.currentTimeMillis();

            for (ArrayDeque<Point> points : this.trails.values()) {
                Point last = null;

                for (Point p : points) {
                    float fade = 1.0F - (float) ((double) (now - p.born) / ((Double) this.life.get() * 1000.0));
                    if (last != null) {
                        ArcVisuals.line(ctx, cam, last.at, p.at, ArcVisuals.alpha(this.color(fade), fade), (double)
                                (this.width.f() * Math.max(0.1F, fade)));
                    }

                    last = p;
                }
            }
        }
    }

    public static final class NameTags extends Colored {
        final Setting.Bool health = this.add(new Setting.Bool("health", "Здоровье", true));
        final Setting.Bool distance = this.add(new Setting.Bool("distance", "Расстояние", true));
        final Setting.Num range = this.add(new Setting.Num("range", "Дальность", 48.0, 8.0, 96.0, 4.0, " бл"));

        public NameTags() {
            super("nametags", "Никнеймы", "Карточки над игроками: имя, здоровье и расстояние", Category.PLAYERS);
        }

        public boolean replaces(Entity e) {
            return this.enabled()
                    && ArcVisuals.mc().player != null
                    && e instanceof Player
                    && e != ArcVisuals.mc().player
                    && !e.isInvisible()
                    && e.distanceToSqr((Entity) ArcVisuals.mc().player)
                            < (Double) this.range.get() * (Double) this.range.get();
        }

        void render(GuiGraphics g, float pt) {
            for (Player p : ArcVisuals.mc().level.players()) {
                if (p != ArcVisuals.mc().player
                        && !p.isInvisible()
                        && p.distanceToSqr(ArcVisuals.mc().player)
                                < (Double) this.range.get() * (Double) this.range.get()) {
                    Vec3 at = ArcVisuals.lerp(p, pt).add(0.0, (double) p.getBbHeight() + 0.4, 0.0);
                    if (ArcVisuals.visible(at)) {
                        String label = p.getName().getString();
                        if (this.health.on()) {
                            label = label + "  " + Health.of(p).text() + " HP";
                        }

                        if (this.distance.on()) {
                            label = label + "  " + Math.round(p.distanceTo(ArcVisuals.mc().player)) + Lang.tr(" м");
                        }

                        ArcVisuals.pill(g, at, label, this.color(0.0F));
                    }
                }
            }
        }
    }

    public static final class TNTTimer extends Colored {
        final Setting.Num range = this.add(new Setting.Num("range", "Дальность", 48.0, 8.0, 96.0, 4.0, " бл"));

        public TNTTimer() {
            super("tnt_timer", "Таймер TNT", "Обратный отсчёт и полоса запала над динамитом", Category.COMBAT);
        }

        void render(GuiGraphics g, float pt) {
            for (Entity e : ArcVisuals.mc().level.entitiesForRendering()) {
                if (e instanceof PrimedTnt t) {
                    if (e.distanceToSqr((Entity) ArcVisuals.mc().player)
                            < (Double) this.range.get() * (Double) this.range.get()) {
                        Vec3 at = ArcVisuals.lerp(t, pt).add(0.0, 1.35, 0.0);
                        if (ArcVisuals.visible(at)) {
                            int c = t.getFuse() < 20 ? -39809 : this.color(0.0F);
                            Locale var10002 = Locale.ROOT;
                            String var10003 = Lang.tr("%.1f с");
                            Object[] var10004 = new Object[] {(double) Math.max(0.0F, (float) t.getFuse() - pt) / 20.0};
                            ArcVisuals.pill(g, at, "TNT · " + String.format(var10002, var10003, var10004), c);
                            double[] screen = Projection.toScreen(at, Px.width(), Px.height());
                            if (screen != null) {
                                int x = (int) screen[0] - 40;
                                int y = (int) screen[1] + 15;
                                Px.round(g, x, y, 80, 3, 1, 1347175260);
                                Px.round(g, x, y, (int) (80.0F * Math.min(1.0F, (float) t.getFuse() / 80.0F)), 3, 1, c);
                            }
                        }
                    }
                }
            }
        }
    }

    public static final class ArrowTags extends Colored {
        final Setting.Num range = this.add(new Setting.Num("range", "Дальность", 20.0, 6.0, 48.0, 2.0, " бл"));
        final Setting.Bool tippedOnly = this.add(new Setting.Bool("tipped_only", "Только с эффектом", false));
        private final Map<Integer, Long> born = new HashMap<>();

        public ArrowTags() {
            super("arrow_tags", "Имена стрел", "Над прилетевшей стрелой: что это за стрела", Category.PLAYERS);
        }

        void render(GuiGraphics g, float pt) {
            long now = System.currentTimeMillis();
            Set<Integer> seen = new HashSet<>();

            for (Entity e : ArcVisuals.mc().level.entitiesForRendering()) {
                if (e instanceof AbstractArrow a) {
                    if (((ArrowAccess) a).arcanicvisuals$inGround()
                            && !(e.distanceToSqr((Entity) ArcVisuals.mc().player)
                                    > (Double) this.range.get() * (Double) this.range.get())) {
                        ItemStack st = a.getPickupItemStackOrigin();
                        if (!st.isEmpty()) {
                            boolean tipped = st.has(DataComponents.POTION_CONTENTS) || st.is(Items.SPECTRAL_ARROW);
                            if (!this.tippedOnly.on() || tipped) {
                                Vec3 at = ArcVisuals.lerp(e, pt).add(0.0, 0.45, 0.0);
                                if (ArcVisuals.visible(at)) {
                                    double[] screen = Projection.toScreen(at, Px.width(), Px.height());
                                    if (ArcVisuals.onScreen(screen)) {
                                        seen.add(e.getId());
                                        float in = Math.min(
                                                1.0F,
                                                (float) (now - (Long) this.born.computeIfAbsent(e.getId(), (k) -> now))
                                                        / 200.0F);
                                        double dist = Math.sqrt(e.distanceToSqr((Entity) ArcVisuals.mc().player));
                                        float u = (float) ((double) Px.height() / 540.0)
                                                * (float) Math.max(0.7, 1.0 - dist / (Double) this.range.get() * 0.35);
                                        TextColor tc = st.getStyledHoverName()
                                                .getStyle()
                                                .getColor();
                                        int c = tipped && tc != null
                                                ? 0xFF000000 | tc.getValue()
                                                : this.color(0.0F) | 0xFF000000;
                                        int fs = Math.max(8, Math.round(5.0F * u));
                                        int is = Math.round(9.0F * u);
                                        int h = Math.round(12.0F * u);
                                        int pad = Math.round(3.0F * u);
                                        String name = Px.ellipsize(
                                                st.getHoverName().getString(),
                                                Px.Weight.SEMIBOLD,
                                                fs,
                                                Math.round(100.0F * u));
                                        int w = pad
                                                + is
                                                + Math.round(3.0F * u)
                                                + Px.textWidth(name, Px.Weight.SEMIBOLD, fs)
                                                + pad
                                                + Math.round(2.0F * u);
                                        int x = (int) Math.round(screen[0]) - w / 2;
                                        int y = (int) Math.round(screen[1]) - h;
                                        float old = Px.fade;
                                        Px.fade = old * Anim.easeOutCubic(in);

                                        try {
                                            Px.shadow(g, x, y + Math.round(u), w, h, Math.round(4.0F * u), 1342177280);
                                            Px.round(g, x, y, w, h, h / 2, 0xE6111116);
                                            Px.outline(g, x, y, w, h, h / 2, tipped ? Px.a(c, 110) : 486539263);
                                            HudElements.item(g, st, x + pad, y + (h - is) / 2, is);
                                            Px.text(
                                                    g,
                                                    name,
                                                    x + pad + is + Math.round(3.0F * u),
                                                    y + (h - fs) / 2,
                                                    Px.Weight.SEMIBOLD,
                                                    fs,
                                                    tipped ? Px.mix(c, -1, 0.35F) : -723720);
                                        } finally {
                                            Px.fade = old;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            this.born.keySet().retainAll(seen);
        }
    }

    public static final class ConsumeESP extends Colored {
        final Setting.Bool self = this.add(new Setting.Bool("self", "Себя в третьем лице", true));
        final Setting.Bool itemName = this.add(new Setting.Bool("item_name", "Название предмета", false));
        final Setting.Num range = this.add(new Setting.Num("range", "Дальность", 32.0, 8.0, 64.0, 4.0, " бл"));
        private final Map<Integer, Eater> eaters = new HashMap<>();

        public ConsumeESP() {
            super(
                    "consume_esp",
                    "Индикатор еды",
                    "Сколько осталось есть или пить игроку рядом: над его ником",
                    Category.PLAYERS);
        }

        void render(GuiGraphics g, float pt) {
            long now = System.currentTimeMillis();

            for (Entity e : ArcVisuals.mc().level.entitiesForRendering()) {
                if (e instanceof LivingEntity p) {
                    if (p.isUsingItem()
                            && !(e.distanceToSqr((Entity) ArcVisuals.mc().player)
                                    > (Double) this.range.get() * (Double) this.range.get())
                            && (p != ArcVisuals.mc().player
                                    || this.self.on()
                                            && !ArcVisuals.mc()
                                                    .options
                                                    .getCameraType()
                                                    .isFirstPerson())) {
                        ItemStack item = p.getUseItem();
                        ItemUseAnimation use = item.getUseAnimation();
                        if (use == ItemUseAnimation.EAT || use == ItemUseAnimation.DRINK) {
                            Vec3 at = ArcVisuals.lerp(p, pt)
                                    .add(0.0, (double) p.getBbHeight() + (p instanceof Player ? 0.95 : 0.5), 0.0);
                            if (ArcVisuals.visible(at)) {
                                double[] screen = Projection.toScreen(at, Px.width(), Px.height());
                                if (ArcVisuals.onScreen(screen)) {
                                    Eater st = (Eater) this.eaters.get(e.getId());
                                    if (st == null || st.doneAt != 0L) {
                                        st = new Eater();
                                        st.born = now;
                                        st.x = (float) screen[0];
                                        st.y = (float) screen[1];
                                        this.eaters.put(e.getId(), st);
                                    }

                                    st.who = p;
                                    st.item = item.copy();
                                    st.seen = now;
                                    st.x += ((float) screen[0] - st.x) * 0.6F;
                                    st.y += ((float) screen[1] - st.y) * 0.6F;
                                    float dur = Math.max(1.0F, (float) item.getUseDuration(p));
                                    float used = (float) p.getTicksUsingItem() + pt;
                                    st.progress = Math.min(1.0F, used / dur);
                                    st.left = Math.max(0.0F, (dur - used) / 20.0F);
                                    double dist = Math.sqrt(p.distanceToSqr(ArcVisuals.mc().player));
                                    st.scale = (float) Math.max(0.75, 1.0 - dist / (Double) this.range.get() * 0.3);
                                }
                            }
                        }
                    }
                }
            }

            Iterator<Eater> it = this.eaters.values().iterator();

            while (it.hasNext()) {
                Eater st = (Eater) it.next();
                if (st.seen != now && st.doneAt == 0L) {
                    st.doneAt = now;
                }

                float out = st.doneAt == 0L
                        ? 1.0F
                        : 1.0F - Math.min(1.0F, (float) (now - st.doneAt) / (st.progress > 0.9F ? 520.0F : 200.0F));
                if (out <= 0.0F) {
                    it.remove();
                } else {
                    if (st.doneAt != 0L && st.who != null && st.who.isAlive()) {
                        Vec3 at = ArcVisuals.lerp(st.who, pt)
                                .add(0.0, (double) st.who.getBbHeight() + (st.who instanceof Player ? 0.95 : 0.5), 0.0);
                        double[] screen = Projection.toScreen(at, Px.width(), Px.height());
                        if (screen != null) {
                            st.x += ((float) screen[0] - st.x) * 0.6F;
                            st.y += ((float) screen[1] - st.y) * 0.6F;
                        }
                    }

                    float in = Math.min(1.0F, (float) (now - st.born) / 220.0F);
                    this.plate(g, st, Anim.easeOutCubic(in) * out, now);
                }
            }
        }

        private void plate(GuiGraphics g, Eater st, float a, long now) {
            if (!(a <= 0.003F)) {
                float u = (float) ((double) Px.height() / 540.0) * st.scale;
                boolean done = st.doneAt != 0L && st.progress > 0.9F;
                float doneK = done ? Math.min(1.0F, (float) (now - st.doneAt) / 160.0F) : 0.0F;
                int c = Px.mix(this.color(0.0F) | 0xFF000000, -11870592, doneK);
                int fs = Math.max(8, Math.round(5.5F * u));
                int us = Math.max(7, Math.round(4.5F * u));
                int h = Math.round(13.0F * u);
                int icon = Math.round(10.0F * u);
                int pad = Math.round(3.0F * u);
                int gap = Math.round(4.0F * u);
                int bw = Math.round(34.0F * u);
                int bh = Math.max(2, Math.round(3.0F * u));
                String secs = String.format(Locale.ROOT, "%.1f", st.left);
                int tw = done
                        ? fs
                        : Px.textWidth(secs, Px.Weight.BOLD, fs)
                                + Math.round(1.2F * u)
                                + Px.textWidth(Lang.tr("с"), Px.Weight.MEDIUM, us);
                int w = pad + icon + gap + bw + gap + tw + pad + Math.round(u);
                int x = Math.round(st.x - (float) w / 2.0F);
                int y = Math.round(st.y - (float) h);
                float old = Px.fade;
                Px.fade = old * a;
                g.pose().pushMatrix();

                try {
                    float hop = done
                            ? (float) Math.sin((double) Math.min(1.0F, (float) (now - st.doneAt) / 260.0F) * Math.PI)
                                    * 0.06F
                            : 0.0F;
                    float k = 0.9F + 0.1F * a + hop;
                    g.pose().translate(st.x, (float) y + (float) h / 2.0F + (float) Math.round(4.0F * u * (1.0F - a)));
                    g.pose().scale(k, k);
                    g.pose().translate(-st.x, -((float) y + (float) h / 2.0F));
                    Px.shadow(g, x, y + Math.round(u), w, h, Math.round(5.0F * u), 1426063360);
                    Px.round(g, x, y, w, h, h / 2, 0xE6111116);
                    float near = st.progress > 0.8F ? (st.progress - 0.8F) / 0.2F : 0.0F;
                    Px.outline(g, x, y, w, h, h / 2, Px.mix(486539263, Px.a(c, 150), Math.max(near * 0.7F, doneK)));
                    int iy = y + (h - icon) / 2;
                    if (!done) {
                        iy += Math.round((float) Math.sin((double) now / 75.0) * 0.7F * u);
                    }

                    HudElements.item(g, st.item, x + pad, iy, icon);
                    int bx = x + pad + icon + gap;
                    int by = y + (h - bh) / 2;
                    Px.round(g, bx, by, bw, bh, bh / 2, 788529151);
                    float p = done ? 1.0F : st.progress;
                    int fw = Math.max(bh, Math.round((float) bw * p));
                    Px.glow(
                            g,
                            bx + fw - Math.round(6.0F * u),
                            by - Math.round(4.0F * u),
                            Math.round(12.0F * u),
                            bh + Math.round(8.0F * u),
                            Px.a(c, 90));
                    Px.pill(g, bx, by, fw, bh, Px.mix(c, 0xFF000000, 0.15F), Px.mix(c, -1, 0.35F));
                    int tx = bx + bw + gap;
                    if (done) {
                        Px.icon(g, "check", tx, y + (h - fs) / 2, fs, c);
                    } else {
                        Px.text(g, secs, tx, y + (h - fs) / 2, Px.Weight.BOLD, fs, -723720);
                        int sx = tx + Px.textWidth(secs, Px.Weight.BOLD, fs) + Math.round(1.2F * u);
                        Px.text(
                                g,
                                Lang.tr("с"),
                                sx,
                                y + (h - fs) / 2 + Px.baseline(fs) - Px.baseline(us),
                                Px.Weight.MEDIUM,
                                us,
                                0x99FFFFFF);
                    }

                    if (this.itemName.on()) {
                        String name = Px.ellipsize(
                                st.item.getHoverName().getString(), Px.Weight.SEMIBOLD, us, Math.round(90.0F * u));
                        TextColor tc = st.item.getStyledHoverName().getStyle().getColor();
                        Px.textCenter(
                                g,
                                name,
                                Math.round(st.x),
                                y - us - Math.round(2.0F * u),
                                Px.Weight.SEMIBOLD,
                                us,
                                tc == null ? -723206 : 0xFF000000 | tc.getValue());
                    }
                } finally {
                    g.pose().popMatrix();
                    Px.fade = old;
                }
            }
        }

        private static final class Eater {
            LivingEntity who;
            ItemStack item;
            float x;
            float y;
            float progress;
            float left;
            float scale;
            long born;
            long seen;
            long doneAt;

            private Eater() {
                this.item = ItemStack.EMPTY;
                this.scale = 1.0F;
            }
        }
    }

    public static final class Zoom extends Module {
        public final Setting.Num factor = this.add(new Setting.Num("factor", "Приближение", 3.0, 1.5, 8.0, 0.25, "×"));
        public final Setting.Mode key = (Setting.Mode)
                this.add(new Setting.Mode("key", "Клавиша удержания", "C", new String[] {"C", "V", "Z", "Левый Alt"}));
        private float blend;
        private long last = System.nanoTime();

        public Zoom() {
            super("zoom", "Плавный зум", "Удерживай C для плавного приближения камеры", Category.WORLD, false);
        }

        public float fov(float original) {
            long now = System.nanoTime();
            float dt = Math.min(0.1F, (float) (now - this.last) / 1.0E9F);
            this.last = now;
            short var10000;
            switch ((String) this.key.get()) {
                case "V" -> var10000 = 86;
                case "Z" -> var10000 = 90;
                case "Левый Alt" -> var10000 = 342;
                default -> var10000 = 67;
            }

            int code = var10000;
            boolean held = this.enabled()
                    && ArcVisuals.mc().screen == null
                    && ArcVisuals.mc().player != null
                    && InputConstants.isKeyDown(ArcVisuals.mc().getWindow(), code);
            this.blend += (held ? 1.0F - this.blend : -this.blend) * (1.0F - (float) Math.exp((double) (-dt * 15.0F)));
            return original / (1.0F + this.blend * (this.factor.f() - 1.0F));
        }
    }
}
