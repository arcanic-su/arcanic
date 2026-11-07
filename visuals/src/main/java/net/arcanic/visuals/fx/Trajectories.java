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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class Trajectories extends Module {
    private final Setting.Mode which =
            this.add(new Setting.Mode("which", "Что показывать", "Всё брошенное", "Всё брошенное", "Только жемчуг"));
    private final Setting.Mode style = this.add(new Setting.Mode("style", "Стиль", "Arcanic", "Arcanic", "Gamesense"));
    private final Setting.Bool before = this.add(new Setting.Bool("before", "До броска", true));
    private final Setting.Bool rainbow =
            (this.add(new Setting.Bool("rainbow", "Радуга", false))).showIf(() -> this.style.is("Gamesense"));
    private final Setting.Bool mine = this.add(new Setting.Bool("mine", "Свои броски", true));
    private final Setting.Bool others = this.add(new Setting.Bool("others", "Чужие броски", true));
    private final Setting.Colour pathColour =
            this.add(new Setting.Colour("path_color", "Цвет пути", false, 0xFF3D1466));
    private final Setting.Colour fillColour =
            this.add(new Setting.Colour("fill_color", "Цвет заполнения", true, 0xFFC264FF));
    private final Setting.Colour hitColour = this.add(new Setting.Colour("hit_color", "Цвет попадания", false, -42386));
    private final Setting.Num width = this.add(new Setting.Num("width", "Толщина", 1.5, 0.5, 4.0, 0.25, " px"));
    private final Setting.Num glow = this.add(new Setting.Num("glow", "Свечение", 55.0, 0.0, 100.0, 5.0, "%"));
    private final Setting.Bool gradient =
            (this.add(new Setting.Bool("gradient", "Градиент по длине", true))).showIf(() -> this.style.is("Arcanic"));
    private final Setting.Bool marker = this.add(new Setting.Bool("marker", "Метка падения", true));
    private final Setting.Bool highlight = this.add(new Setting.Bool("highlight", "Подсветка цели", true));
    private final Setting.Bool ghost =
            (this.add(new Setting.Bool("ghost", "Призрак у жемчуга", true))).showIf(() -> this.style.is("Arcanic"));
    private final Setting.Bool label =
            (this.add(new Setting.Bool("label", "Время до падения", true))).showIf(() -> this.style.is("Arcanic"));
    private final Setting.Bool walls = this.add(new Setting.Bool("walls", "Видно сквозь стены", true));
    private static final long FADE_NS = 850000000L;
    private static final long REVEAL_NS = 300000000L;
    private final Map<Integer, Track> tracks = new HashMap<>();
    private long lastNanos = System.nanoTime();
    private Ahead aim;
    private Ahead shownAim;
    private float aimShow;
    private ItemStack aimItem;
    private final Track aimTrack;
    private static final int UNDER = 0;
    private static final int RING = 1;
    private static final int LINES = 2;

    public Trajectories() {
        super(
                "trajectories",
                "Траектории",
                "Брошенный жемчуг, снежок или зелье: путь до места падения заполняется по мере полёта",
                Category.COMBAT,
                false);
        this.aimItem = ItemStack.EMPTY;
        this.aimTrack = new Track(false);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static float smooth(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    private static int alpha(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | c & 0xFFFFFF;
    }

    private float glowK() {
        return this.glow.f() / 100.0F;
    }

    private int lit() {
        return this.fillColour.argb() | 0xFF000000;
    }

    private int hit() {
        return this.hitColour.argb() | 0xFF000000;
    }

    private Ahead simulate(Vec3 pos, Vec3 v, double gravity, Entity self, Entity owner, List<Entity> candidates) {
        ClientLevel level = mc().level;
        List<Vec3> pts = new ArrayList<>();
        pts.add(pos);
        int t = 1;

        while (true) {
            if (t <= 300) {
                boolean water = level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
                v = v.add(0.0, -gravity, 0.0).scale(water ? 0.8 : 0.99);
                Vec3 next = pos.add(v);
                BlockHitResult hit = level.clip(new ClipContext(pos, next, Block.COLLIDER, Fluid.NONE, self));
                if (hit.getType() != Type.MISS) {
                    next = hit.getLocation();
                }

                Entity target = null;
                double best = pos.distanceToSqr(next);
                AABB seg = (new AABB(pos, next)).inflate(0.3);

                for (Entity e : candidates) {
                    if (e != self && e != owner && e.getBoundingBox().intersects(seg)) {
                        Optional<Vec3> c = e.getBoundingBox().inflate(0.3).clip(pos, next);
                        if (c.isPresent() && pos.distanceToSqr((Vec3) c.get()) < best) {
                            best = pos.distanceToSqr((Vec3) c.get());
                            target = e;
                            next = (Vec3) c.get();
                        }
                    }
                }

                pts.add(next);
                if (target != null) {
                    return new Ahead(pts, next, (Direction) null, target, t);
                }

                if (hit.getType() != Type.MISS) {
                    return new Ahead(pts, next, hit.getDirection(), (Entity) null, t);
                }

                pos = next;
                if (!(next.y < (double) (level.getMinY() - 8))) {
                    ++t;
                    continue;
                }
            }

            return new Ahead(pts, (Vec3) pts.get(pts.size() - 1), (Direction) null, (Entity) null, 300);
        }
    }

    private static Throw throwOf(Player p, float pt) {
        for (InteractionHand hand : InteractionHand.values()) {
            Throw t = throwOf(p, p.getItemInHand(hand), hand, pt);
            if (t != null) {
                return t;
            }
        }

        return null;
    }

    private static Throw throwOf(Player p, ItemStack s, InteractionHand hand, float pt) {
        if (s.isEmpty()) {
            return null;
        } else {
            boolean using = p.isUsingItem() && p.getUsedItemHand() == hand;
            if (!s.is(Items.ENDER_PEARL) && !s.is(Items.SNOWBALL) && !(s.getItem() instanceof EggItem)) {
                if (!s.is(Items.SPLASH_POTION) && !s.is(Items.LINGERING_POTION)) {
                    if (s.is(Items.EXPERIENCE_BOTTLE)) {
                        return aim(p, pt, s, -20.0F, 0.7, 0.07, 0.99, 0.8, true);
                    } else if (s.is(Items.BOW) && using) {
                        float power = BowItem.getPowerForTime(p.getTicksUsingItem());
                        return power < 0.1F
                                ? null
                                : aim(
                                        p,
                                        pt,
                                        new ItemStack(Items.ARROW),
                                        0.0F,
                                        (double) (power * 3.0F),
                                        0.05,
                                        0.99,
                                        0.6,
                                        true);
                    } else if (s.is(Items.CROSSBOW)) {
                        ChargedProjectiles loaded = (ChargedProjectiles) s.get(DataComponents.CHARGED_PROJECTILES);
                        return loaded != null && !loaded.isEmpty() && !loaded.contains(Items.FIREWORK_ROCKET)
                                ? aim(p, pt, new ItemStack(Items.ARROW), 0.0F, 3.15, 0.05, 0.99, 0.6, false)
                                : null;
                    } else {
                        return s.is(Items.TRIDENT) && using && p.getTicksUsingItem() >= 10
                                ? aim(p, pt, s, 0.0F, 2.5, 0.05, 0.99, 0.99, true)
                                : null;
                    }
                } else {
                    return aim(p, pt, s, -20.0F, 0.5, 0.05, 0.99, 0.8, true);
                }
            } else {
                return aim(p, pt, s, 0.0F, 1.5, 0.03, 0.99, 0.8, true);
            }
        }
    }

    private static Throw aim(
            Player p,
            float pt,
            ItemStack item,
            float lift,
            double speed,
            double gravity,
            double air,
            double water,
            boolean carry) {
        float xr = p.getViewXRot(pt);
        float yr = p.getViewYRot(pt);
        double x = -Math.sin(Math.toRadians((double) yr)) * Math.cos(Math.toRadians((double) xr));
        double y = -Math.sin(Math.toRadians((double) (xr + lift)));
        double z = Math.cos(Math.toRadians((double) yr)) * Math.cos(Math.toRadians((double) xr));
        Vec3 v = (new Vec3(x, y, z)).normalize().scale(speed);
        if (carry) {
            Vec3 m = p.getDeltaMovement();
            v = v.add(m.x, p.onGround() ? 0.0 : m.y, m.z);
        }

        return new Throw(p.getEyePosition(pt).subtract(0.0, 0.1, 0.0), v, gravity, air, water, item);
    }

    private Ahead fly(Throw th, Entity self, List<Entity> candidates) {
        ClientLevel level = mc().level;
        List<Vec3> pts = new ArrayList<>();
        Vec3 pos = th.from;
        Vec3 v = th.v;
        pts.add(pos);
        int t = 1;

        while (true) {
            if (t <= 300) {
                Vec3 next = pos.add(v);
                BlockHitResult hit = level.clip(new ClipContext(pos, next, Block.COLLIDER, Fluid.NONE, self));
                if (hit.getType() != Type.MISS) {
                    next = hit.getLocation();
                }

                Entity target = null;
                double best = pos.distanceToSqr(next);
                AABB seg = (new AABB(pos, next)).inflate(0.3);

                for (Entity e : candidates) {
                    if (e != self && e.getBoundingBox().intersects(seg)) {
                        Optional<Vec3> c = e.getBoundingBox().inflate(0.3).clip(pos, next);
                        if (c.isPresent() && pos.distanceToSqr((Vec3) c.get()) < best) {
                            best = pos.distanceToSqr((Vec3) c.get());
                            target = e;
                            next = (Vec3) c.get();
                        }
                    }
                }

                pts.add(next);
                if (target != null) {
                    return new Ahead(pts, next, (Direction) null, target, t);
                }

                if (hit.getType() != Type.MISS) {
                    return new Ahead(pts, next, hit.getDirection(), (Entity) null, t);
                }

                pos = next;
                boolean wet = level.getFluidState(BlockPos.containing(next)).is(FluidTags.WATER);
                v = v.scale(wet ? th.water : th.air).add(0.0, -th.gravity, 0.0);
                if (!(next.y < (double) (level.getMinY() - 8))) {
                    ++t;
                    continue;
                }
            }

            return new Ahead(pts, (Vec3) pts.get(pts.size() - 1), (Direction) null, (Entity) null, 300);
        }
    }

    private void update(float pt, long now, float dt) {
        Player me = mc().player;
        Set<Integer> seen = new HashSet<>();
        List<Entity> candidates = null;
        float k = 1.0F - (float) Math.exp((double) (-dt * 16.0F));

        for (Entity e : mc().level.entitiesForRendering()) {
            if (e instanceof ThrowableItemProjectile p) {
                if (!e.isRemoved() && !(e.distanceToSqr((Entity) me) > 16384.0)) {
                    boolean pearl = e instanceof ThrownEnderpearl;
                    if (!this.which.is("Только жемчуг") || pearl) {
                        boolean own = p.getOwner() == me;
                        if (own) {
                            if (!this.mine.on()) {
                                continue;
                            }
                        } else if (!this.others.on()) {
                            continue;
                        }

                        if (candidates == null) {
                            candidates = mc().level
                                    .getEntities(
                                            me,
                                            me.getBoundingBox().inflate(160.0),
                                            (x) -> !x.isSpectator()
                                                    && x.isPickable()
                                                    && !(x instanceof ThrowableItemProjectile));
                        }

                        Track t = (Track) this.tracks.get(e.getId());
                        Vec3 at = e.getPosition(pt);
                        if (t == null) {
                            t = new Track(pearl);
                            t.trail.add(at);
                            t.own = own;
                            t.item = p.getItem().copy();
                            this.tracks.put(e.getId(), t);
                        }

                        if (t.dead < 0L) {
                            seen.add(e.getId());
                            t.head = at;
                            t.yaw = p.getOwner() != null ? p.getOwner().getYRot() : e.getYRot();
                            if (((Vec3) t.trail.get(t.trail.size() - 1)).distanceToSqr(at) > 0.01
                                    && t.trail.size() < 600) {
                                t.trail.add(at);
                            }

                            List<Entity> cands = new ArrayList(candidates);
                            if (p.getOwner() != null && p.getOwner() != me) {
                                cands.add(me);
                            }

                            if (t.ahead == null || e.tickCount != t.lastTick) {
                                t.lastTick = e.tickCount;
                                t.ahead = this.simulate(
                                        e.position(), e.getDeltaMovement(), e.getGravity(), e, p.getOwner(), cands);
                            }

                            Vec3 end = t.ahead.target != null ? t.ahead.target.position() : t.ahead.end;
                            Vec3 n = t.ahead.face != null
                                    ? Vec3.atLowerCornerOf(t.ahead.face.getUnitVec3i())
                                    : new Vec3(0.0, 1.0, 0.0);
                            t.mark = t.mark == null ? end : t.mark.lerp(end, (double) k);
                            t.markN = t.markN.lerp(n, (double) k).normalize();
                            t.target = t.ahead.target;
                            float r = (float) t.ahead.ticks / 20.0F;
                            t.remain = t.remain == 0.0F ? r : t.remain + (r - t.remain) * k;
                            if (t.total == 0.0F) {
                                t.total = r + (float) (now - t.born) / 1.0E9F;
                            }
                        }
                    }
                }
            }
        }

        Ahead found = null;
        if (this.before.on() && me != null && mc().screen == null) {
            Throw th = throwOf(me, pt);
            if (th != null) {
                List<Entity> near = mc().level
                        .getEntities(
                                me,
                                me.getBoundingBox().inflate(96.0),
                                (x) -> !x.isSpectator() && x.isPickable() && !(x instanceof ThrowableItemProjectile));
                found = this.fly(th, me, near);
                this.aimItem = th.item;
            }
        }

        this.aim = found;
        if (found != null) {
            if (this.aimShow < 0.02F) {
                this.aimTrack.born = now;
            }

            this.shownAim = found;
            Vec3 end = found.target != null ? found.target.position() : found.end;
            Vec3 n = found.face != null ? Vec3.atLowerCornerOf(found.face.getUnitVec3i()) : new Vec3(0.0, 1.0, 0.0);
            if (this.aimTrack.mark != null && !(this.aimTrack.mark.distanceToSqr(end) > 64.0)) {
                this.aimTrack.mark =
                        this.aimTrack.mark.lerp(end, (double) (1.0F - (float) Math.exp((double) (-dt * 30.0F))));
            } else {
                this.aimTrack.mark = end;
            }

            this.aimTrack.markN = this.aimTrack.markN.lerp(n, (double) k).normalize();
            this.aimTrack.target = found.target;
            this.aimTrack.remain = (float) found.ticks / 20.0F;
        }

        this.aimShow += ((float) (found != null ? 1 : 0) - this.aimShow)
                * (1.0F - (float) Math.exp((double) (-dt * (float) (found != null ? 16 : 11))));
        if (found == null && this.aimShow < 0.01F) {
            this.aimShow = 0.0F;
            this.shownAim = null;
            this.aimTrack.mark = null;
            this.aimTrack.target = null;
        }

        Iterator<Map.Entry<Integer, Track>> it = this.tracks.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Integer, Track> en = (Map.Entry) it.next();
            Track t = (Track) en.getValue();
            if (t.dead < 0L && !seen.contains(en.getKey())) {
                t.dead = now;
                if (t.mark != null && t.target == null) {
                    t.trail.add(t.mark);
                }

                t.head = null;
                t.ahead = null;
            }

            if (t.dead >= 0L && now - t.dead > 850000000L) {
                it.remove();
            }
        }
    }

    private static float alphaOf(Track t, long now) {
        float in = smooth((float) (now - t.born) / 2.2E8F);
        float out = t.dead < 0L ? 1.0F : 1.0F - smooth((float) (now - t.dead) / 8.5E8F);
        return in * out;
    }

    private static void prepare(Track t, float k) {
        List<Vec3> flownPts = new ArrayList(t.trail);
        if (t.head != null) {
            flownPts.add(t.head);
        }

        t.flown = spline(flownPts);
        t.done = length(t.flown);
        t.road = null;
        if (t.dead < 0L && t.head != null && t.ahead != null && t.ahead.pts.size() > 1) {
            List<Vec3> aheadPts = new ArrayList<>();
            aheadPts.add(t.head);

            for (Vec3 v : t.ahead.pts.subList(1, t.ahead.pts.size())) {
                if (v.distanceToSqr(t.head) > 0.04 || aheadPts.size() > 1) {
                    aheadPts.add(v);
                }
            }

            t.road = spline(aheadPts);
            double total = t.done + length(t.road);
            float target = total < 0.001 ? 1.0F : (float) (t.done / total);
            t.shown = t.shown == 0.0F ? target : t.shown + (target - t.shown) * k;
        } else {
            t.shown = Math.min(1.0F, t.shown + (1.0F - t.shown) * k);
        }
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float) (now - this.lastNanos) / 1.0E9F);
        this.lastNanos = now;
        this.update(pt, now, dt);
        if (!this.tracks.isEmpty() || this.shownAim != null) {
            float k = 1.0F - (float) Math.exp((double) (-dt * 14.0F));

            for (Track t : this.tracks.values()) {
                prepare(t, k);
            }

            double fov = Math.toRadians((double) Projection.fov());
            float perPx = (float) (2.0
                    * Math.tan(fov / 2.0)
                    / (double) Math.max(1, mc().getWindow().getHeight()));
            Frame f = new Frame(ctx.matrices().last(), cam, perPx, now, pt);
            boolean top = this.walls.on();
            this.draw(ctx.consumers().getBuffer(Fx.ribbon(Fx.Tex.LINE_GLOW, top)), f, 0);
            if (this.marker.on() && !this.style.is("Gamesense")) {
                this.draw(ctx.consumers().getBuffer(Fx.ribbon(Fx.Tex.LINE_EDGE, top)), f, 1);
            }

            this.draw(ctx.consumers().getBuffer(Fx.ribbon(Fx.Tex.LINE_AA, top)), f, 2);
        }
    }

    private void draw(VertexConsumer vc, Frame f, int pass) {
        boolean gs = this.style.is("Gamesense");
        if (this.shownAim != null && this.shownAim.pts.size() > 1 && this.aimShow > 0.005F) {
            float reveal = smooth((float) (f.now - this.aimTrack.born) / 3.0E8F);
            List<Vec3> path = prefix(spline(this.shownAim.pts), reveal);
            if (gs) {
                this.laser(vc, f, path, this.aimShow, pass);
            } else {
                this.path(vc, f, path, this.aimShow, pass, -1.0);
                if (this.marker.on() && this.aimTrack.mark != null) {
                    this.mark(vc, f, this.aimTrack, this.aimShow * smooth((reveal - 0.55F) / 0.45F), pass, 1.0F);
                }
            }

            if (this.highlight.on() && this.aimTrack.target != null) {
                this.brackets(vc, f, this.aimTrack.target, this.aimShow * smooth((reveal - 0.55F) / 0.45F), pass);
            }
        }

        for (Track t : this.tracks.values()) {
            float a = alphaOf(t, f.now);
            if (!(a < 0.005F)) {
                float reveal = smooth((float) (f.now - t.born) / 3.0E8F);
                if (gs) {
                    if (t.road != null && t.road.size() > 1) {
                        this.laser(vc, f, prefix(t.road, reveal), a, pass);
                    }
                } else {
                    if (t.road != null && t.road.size() > 1) {
                        this.road(vc, f, prefix(t.road, reveal), a, pass);
                    }

                    if (t.flown.size() > 1) {
                        this.path(vc, f, t.flown, a, pass, t.done);
                    }

                    if (this.marker.on()) {
                        this.mark(
                                vc,
                                f,
                                t,
                                a * reveal,
                                pass,
                                t.dead >= 0L ? 1.0F : Math.max(0.0F, Math.min(1.0F, t.shown)));
                    }

                    if (this.highlight.on() && t.target != null && t.dead < 0L) {
                        this.brackets(vc, f, t.target, a * reveal, pass);
                    }

                    if (pass == 2 && this.ghost.on() && t.pearl && t.target == null && t.mark != null) {
                        this.ghost(vc, f, t, a);
                    }
                }
            }
        }
    }

    private float core() {
        return this.width.f() + 0.25F;
    }

    private void path(VertexConsumer vc, Frame f, List<Vec3> pts, float a, int pass, double flown) {
        int lit = this.lit();
        int hi = Fx.lerp(lit, -1, 0.5F);
        double len = Math.max(0.001, length(pts));
        boolean aimed = flown < 0.0;
        Width w = aimed ? (d) -> this.core() : (d) -> this.core() * (0.5F + 0.7F * (float) (d / len));
        Shade s = (d) -> {
            float t = (float) (d / len);
            int c = this.gradient.on() ? Fx.lerp(hi, lit, aimed ? t : 1.0F - t) : lit;
            float fade = aimed ? 1.0F - 0.35F * t : 0.12F + 0.88F * (float) Math.pow((double) t, 1.3);
            return alpha(c, a * 0.95F * fade);
        };
        this.ribbon(vc, f, pts, pass, w, s);
    }

    private void road(VertexConsumer vc, Frame f, List<Vec3> pts, float a, int pass) {
        int c = Fx.lerp(this.pathColour.argb() | 0xFF000000, this.lit(), 0.3F);
        this.ribbon(
                vc, f, pts, pass, (d) -> this.core() * 0.85F, (d) -> alpha(c, a * 0.8F * smooth((float) (d / 0.6))));
    }

    private void ribbon(VertexConsumer vc, Frame f, List<Vec3> pts, int pass, Width w, Shade s) {
        if (pass == 0 && this.glowK() > 0.0F) {
            float g = this.glowK();
            strip(vc, f, pts, (d) -> w.at(d) * 3.4F + 7.0F, (d) -> {
                int c = s.at(d);
                return alpha(c, (float) (c >>> 24) / 255.0F * 0.36F * g);
            });
        } else if (pass == 2) {
            strip(vc, f, pts, (d) -> w.at(d) + 2.0F, s);
        }
    }

    private void laser(VertexConsumer vc, Frame f, List<Vec3> pts, float a, int pass) {
        double len = Math.max(0.001, length(pts));
        float px = this.width.f();
        if (pass == 0) {
            strip(
                    vc,
                    f,
                    pts,
                    (d) -> px * 4.0F + 6.0F,
                    (d) -> alpha(this.laserColour(d / len), a * (0.18F + 0.3F * this.glowK())));
        } else if (pass == 2) {
            strip(vc, f, pts, (d) -> px + 2.0F, (d) -> alpha(Fx.lerp(this.laserColour(d / len), -1, 0.25F), a * 0.95F));
        }
    }

    private int laserColour(double along) {
        if (!this.rainbow.on()) {
            return this.lit();
        } else {
            float hue = (float) ((along * 0.85 + (double) System.nanoTime() / (double) 1.0E9F * 0.15) % 1.0);
            return Mth.hsvToRgb(hue, 0.7F, 1.0F) | 0xFF000000;
        }
    }

    private void mark(VertexConsumer vc, Frame f, Track t, float a, int pass, float progress) {
        if (t.mark != null && !(a < 0.005F) && pass != 2) {
            Vec3 n = t.markN;
            Vec3 u = Math.abs(n.y) > 0.7
                    ? new Vec3(1.0, 0.0, 0.0)
                    : n.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            Vec3 w = n.cross(u).normalize();
            double dist = t.mark.distanceTo(f.cam);
            Vec3 c = t.mark.add(n.scale(0.012 + dist * 0.0012));
            float in =
                    1.0F - (float) Math.pow((double) (1.0F - Math.min(1.0F, (float) (f.now - t.born) / 3.2E8F)), 3.0);
            float landed = t.dead < 0L ? 0.0F : smooth((float) (f.now - t.dead) / 8.5E8F);
            double base = t.target != null ? Math.max(0.45, (double) t.target.getBbWidth() * 0.7) : 0.42;
            double r = base * (0.6 + 0.4 * (double) in) * (1.0 + 0.3 * (double) landed);
            float al = a * in;
            int lit = t.target != null ? this.hit() : this.lit();
            int hi = Fx.lerp(lit, -1, 0.35F);
            float ring = Math.max(6.0F, (this.width.f() + 0.75F) * 2.6F);
            if (pass == 0) {
                annulus(vc, f, c, u, w, r * 0.17, r * 0.97, alpha(lit, al * 0.1F), alpha(lit, al * 0.28F), 48);
                disc(vc, f, c, u, w, r * 0.08, alpha(hi, al), alpha(hi, al), 20);
                if (this.glowK() > 0.0F) {
                    circle(
                            vc,
                            f,
                            c,
                            u,
                            w,
                            r,
                            0.0F,
                            1.0F,
                            (d) -> ring * 2.6F,
                            (x) -> alpha(lit, al * 0.3F * this.glowK()));
                }

            } else {
                if (progress < 0.999F) {
                    circle(vc, f, c, u, w, r, progress, 1.0F, (d) -> ring, (x) -> alpha(lit, al * 0.45F));
                }

                if (progress > 0.001F) {
                    circle(vc, f, c, u, w, r, 0.0F, progress, (d) -> ring, (x) -> alpha(Fx.lerp(hi, lit, x), al));
                }
            }
        }
    }

    private void brackets(VertexConsumer vc, Frame f, Entity e, float a, int pass) {
        if (!(a < 0.005F) && pass != 1 && (pass != 0 || !(this.glowK() <= 0.0F))) {
            Vec3 shift = e.getPosition(f.pt).subtract(e.position());
            AABB bb = e.getBoundingBox().move(shift).inflate(0.06);
            double sx = bb.getXsize();
            double sy = bb.getYsize();
            double sz = bb.getZsize();
            double lx = Math.min(0.3, sx * 0.3);
            double ly = Math.min(0.35, sy * 0.25);
            double lz = Math.min(0.3, sz * 0.3);
            float pulse = 0.78F + 0.22F * (float) Math.sin((double) f.now / (double) 1.0E9F * 6.0);
            int c = this.hit();
            float wpx = this.core() + 0.25F;
            boolean light = pass == 0;
            Width wd = light ? (d) -> wpx * 3.4F + 7.0F : (d) -> wpx + 2.0F;
            int col = light ? alpha(c, a * 0.4F * this.glowK()) : alpha(Fx.lerp(c, -1, 0.2F), a * pulse);

            for (int i = 0; i < 8; i++) {
                boolean bx = (i & 1) != 0;
                boolean by = (i & 2) != 0;
                boolean bz = (i & 4) != 0;
                Vec3 o = new Vec3(bx ? bb.maxX : bb.minX, by ? bb.maxY : bb.minY, bz ? bb.maxZ : bb.minZ);
                strip(vc, f, List.of(o, o.add(bx ? -lx : lx, 0.0, 0.0)), wd, (d) -> col);
                strip(vc, f, List.of(o, o.add(0.0, by ? -ly : ly, 0.0)), wd, (d) -> col);
                strip(vc, f, List.of(o, o.add(0.0, 0.0, bz ? -lz : lz)), wd, (d) -> col);
            }
        }
    }

    private void ghost(VertexConsumer vc, Frame f, Track t, float a) {
        float time = (float) (f.now - t.born) / 1.0E9F;
        float pulse = 0.5F + 0.5F * (float) Math.sin((double) (time * 5.5F));
        float grow = smooth((float) (f.now - t.born) / 3.0E8F);
        float k = 0.0625F * (0.94F + 0.06F * grow + 0.025F * pulse);
        int c = alpha(Fx.lerp(this.lit(), -1, 0.18F * pulse), a * (0.22F + 0.2F * pulse));
        double yaw = Math.toRadians((double) (-t.yaw));
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        Vec3 o = (t.dead >= 0L && !t.trail.isEmpty() ? (Vec3) t.trail.get(t.trail.size() - 1) : t.mark).subtract(f.cam);
        float[][] parts = new float[][] {
            {-4.0F, 0.0F, -2.0F, 0.0F, 12.0F, 2.0F},
            {0.0F, 0.0F, -2.0F, 4.0F, 12.0F, 2.0F},
            {-4.0F, 12.0F, -2.0F, 4.0F, 24.0F, 2.0F},
            {-8.0F, 12.0F, -2.0F, -4.0F, 24.0F, 2.0F},
            {4.0F, 12.0F, -2.0F, 8.0F, 24.0F, 2.0F},
            {-4.0F, 24.0F, -4.0F, 4.0F, 32.0F, 4.0F}
        };

        for (float[] b : parts) {
            box(vc, f.pose, o, cos, sin, k, b, c);
        }
    }

    private static void box(
            VertexConsumer vc, PoseStack.Pose pose, Vec3 o, float cos, float sin, float k, float[] b, int c) {
        float x0 = b[0];
        float y0 = b[1];
        float z0 = b[2];
        float dx = b[3] - b[0];
        float dy = b[4] - b[1];
        float dz = b[5] - b[2];
        float[][] faces = new float[][] {
            {x0 + dx, y0, z0, 0.0F, dy, 0.0F, 0.0F, 0.0F, dz},
            {x0, y0, z0, 0.0F, 0.0F, dz, 0.0F, dy, 0.0F},
            {x0, y0 + dy, z0, 0.0F, 0.0F, dz, dx, 0.0F, 0.0F},
            {x0, y0, z0, dx, 0.0F, 0.0F, 0.0F, 0.0F, dz},
            {x0, y0, z0 + dz, dx, 0.0F, 0.0F, 0.0F, dy, 0.0F},
            {x0, y0, z0, 0.0F, dy, 0.0F, dx, 0.0F, 0.0F}
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
        float rx = x * cos - z * sin;
        float rz = x * sin + z * cos;
        Fx.v(vc, pose, (float) o.x + rx * k, (float) o.y + y * k, (float) o.z + rz * k, 0.0F, 0.5F, c);
    }

    private static double length(List<Vec3> pts) {
        double s = 0.0;

        for (int i = 1; i < pts.size(); i++) {
            s += ((Vec3) pts.get(i)).distanceTo((Vec3) pts.get(i - 1));
        }

        return s;
    }

    private static List<Vec3> prefix(List<Vec3> pts, float frac) {
        if (!(frac >= 1.0F) && pts.size() >= 2) {
            double want = length(pts) * (double) Math.max(0.0F, frac);
            double run = 0.0;
            List<Vec3> out = new ArrayList<>();
            out.add((Vec3) pts.get(0));

            for (int i = 1; i < pts.size(); i++) {
                Vec3 a = (Vec3) pts.get(i - 1);
                Vec3 b = (Vec3) pts.get(i);
                double seg = a.distanceTo(b);
                if (run + seg >= want) {
                    out.add(a.lerp(b, seg < 1.0E-9 ? 0.0 : (want - run) / seg));
                    break;
                }

                out.add(b);
                run += seg;
            }

            return out;
        } else {
            return pts;
        }
    }

    private static List<Vec3> spline(List<Vec3> p) {
        List<Vec3> pts = new ArrayList<>();

        for (Vec3 v : p) {
            if (pts.isEmpty() || ((Vec3) pts.get(pts.size() - 1)).distanceToSqr(v) > 1.0E-6) {
                pts.add(v);
            }
        }

        if (pts.size() < 3) {
            return pts;
        } else {
            List<Vec3> out = new ArrayList<>();
            out.add((Vec3) pts.get(0));

            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 p0 = (Vec3) pts.get(Math.max(0, i - 1));
                Vec3 p1 = (Vec3) pts.get(i);
                Vec3 p2 = (Vec3) pts.get(i + 1);
                Vec3 p3 = (Vec3) pts.get(Math.min(pts.size() - 1, i + 2));
                int n = Math.max(1, (int) Math.ceil(p1.distanceTo(p2) / 0.18));

                for (int j = 1; j <= n; j++) {
                    double t = (double) j / (double) n;
                    double t2 = t * t;
                    double t3 = t2 * t;
                    out.add(p0.scale(-0.5 * t3 + t2 - 0.5 * t)
                            .add(p1.scale(1.5 * t3 - 2.5 * t2 + 1.0))
                            .add(p2.scale(-1.5 * t3 + 2.0 * t2 + 0.5 * t))
                            .add(p3.scale(0.5 * t3 - 0.5 * t2)));
                }
            }

            return out;
        }
    }

    private static void strip(VertexConsumer vc, Frame f, List<Vec3> pts, Width width, Shade shade) {
        int n = pts.size();
        if (n >= 2) {
            Vec3[] rel = new Vec3[n];
            Vec3[] off = new Vec3[n];
            int[] col = new int[n];
            float[] wid = new float[n];
            double run = 0.0;

            for (int i = 0; i < n; i++) {
                rel[i] = ((Vec3) pts.get(i)).subtract(f.cam);
                if (i > 0) {
                    run += ((Vec3) pts.get(i)).distanceTo((Vec3) pts.get(i - 1));
                }

                col[i] = shade.at(run);
                float near = smooth((float) ((rel[i].length() - 1.2) / 1.8));
                col[i] = Math.round((float) (col[i] >>> 24) * near) << 24 | col[i] & 0xFFFFFF;
                wid[i] = width.at(run);
            }

            Vec3 last = null;

            for (int i = 0; i < n; i++) {
                Vec3 dir = rel[Math.min(n - 1, i + 1)].subtract(rel[Math.max(0, i - 1)]);
                Vec3 side = dir.cross(rel[i]);
                if (side.lengthSqr() < 1.0E-12) {
                    side = last != null ? last : new Vec3(0.0, 1.0, 0.0);
                }

                side = side.normalize();
                if (last != null && side.dot(last) < 0.0) {
                    side = side.scale(-1.0);
                }

                last = side;
                off[i] = side.scale(Math.max(0.0015, rel[i].length() * (double) f.perPx * (double) wid[i] / 2.0));
            }

            for (int i = 0; i < n - 1; i++) {
                if (col[i] >>> 24 != 0 || col[i + 1] >>> 24 != 0) {
                    Vec3 a = rel[i];
                    Vec3 b = rel[i + 1];
                    Vec3 sa = off[i];
                    Vec3 sb = off[i + 1];
                    Fx.v(
                            vc,
                            f.pose,
                            (float) (a.x + sa.x),
                            (float) (a.y + sa.y),
                            (float) (a.z + sa.z),
                            0.0F,
                            0.0F,
                            col[i]);
                    Fx.v(
                            vc,
                            f.pose,
                            (float) (a.x - sa.x),
                            (float) (a.y - sa.y),
                            (float) (a.z - sa.z),
                            0.0F,
                            1.0F,
                            col[i]);
                    Fx.v(
                            vc,
                            f.pose,
                            (float) (b.x - sb.x),
                            (float) (b.y - sb.y),
                            (float) (b.z - sb.z),
                            1.0F,
                            1.0F,
                            col[i + 1]);
                    Fx.v(
                            vc,
                            f.pose,
                            (float) (b.x + sb.x),
                            (float) (b.y + sb.y),
                            (float) (b.z + sb.z),
                            1.0F,
                            0.0F,
                            col[i + 1]);
                }
            }
        }
    }

    private static void circle(
            VertexConsumer vc,
            Frame f,
            Vec3 c,
            Vec3 u,
            Vec3 w,
            double r,
            float from,
            float to,
            Width width,
            Around shade) {
        int steps = Math.max(2, Math.round(72.0F * (to - from)));
        List<Vec3> pts = new ArrayList<>();

        for (int i = 0; i <= steps; i++) {
            double ang = (Math.PI / 2D)
                    - ((double) from + (double) ((to - from) * (float) i) / (double) steps) * Math.PI * 2.0;
            pts.add(c.add(u.scale(Math.cos(ang) * r)).add(w.scale(Math.sin(ang) * r)));
        }

        double len = Math.max(1.0E-6, length(pts));
        strip(vc, f, pts, width, (d) -> shade.at(from + (to - from) * (float) (d / len)));
    }

    private static void annulus(
            VertexConsumer vc, Frame f, Vec3 c, Vec3 u, Vec3 w, double r0, double r1, int inner, int outer, int n) {
        for (int i = 0; i < n; i++) {
            double a0 = (double) i * Math.PI * 2.0 / (double) n;
            double a1 = (double) (i + 1) * Math.PI * 2.0 / (double) n;
            Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0)));
            Vec3 d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
            Vec3 p0 = c.add(d0.scale(r0)).subtract(f.cam);
            Vec3 p1 = c.add(d0.scale(r1)).subtract(f.cam);
            Vec3 p2 = c.add(d1.scale(r1)).subtract(f.cam);
            Vec3 p3 = c.add(d1.scale(r0)).subtract(f.cam);
            Fx.v(vc, f.pose, (float) p0.x, (float) p0.y, (float) p0.z, 0.0F, 0.5F, inner);
            Fx.v(vc, f.pose, (float) p1.x, (float) p1.y, (float) p1.z, 0.0F, 0.5F, outer);
            Fx.v(vc, f.pose, (float) p2.x, (float) p2.y, (float) p2.z, 0.0F, 0.5F, outer);
            Fx.v(vc, f.pose, (float) p3.x, (float) p3.y, (float) p3.z, 0.0F, 0.5F, inner);
        }
    }

    private static void disc(
            VertexConsumer vc, Frame f, Vec3 c, Vec3 u, Vec3 w, double r, int centre, int edge, int n) {
        Vec3 rc = c.subtract(f.cam);

        for (int i = 0; i < n; i++) {
            double a0 = (double) i * Math.PI * 2.0 / (double) n;
            double a1 = (double) (i + 1) * Math.PI * 2.0 / (double) n;
            Vec3 ra = c.add(u.scale(Math.cos(a0) * r))
                    .add(w.scale(Math.sin(a0) * r))
                    .subtract(f.cam);
            Vec3 rb = c.add(u.scale(Math.cos(a1) * r))
                    .add(w.scale(Math.sin(a1) * r))
                    .subtract(f.cam);
            Fx.v(vc, f.pose, (float) rc.x, (float) rc.y, (float) rc.z, 0.0F, 0.5F, centre);
            Fx.v(vc, f.pose, (float) ra.x, (float) ra.y, (float) ra.z, 0.0F, 0.5F, edge);
            Fx.v(vc, f.pose, (float) rb.x, (float) rb.y, (float) rb.z, 0.0F, 0.5F, edge);
            Fx.v(vc, f.pose, (float) rb.x, (float) rb.y, (float) rb.z, 0.0F, 0.5F, edge);
        }
    }

    void hud(GuiGraphics g) {
        if (mc().screen == null) {
            if (this.style.is("Gamesense")) {
                this.discs(g);
            } else if (this.label.on() && !this.tracks.isEmpty()) {
                long now = System.nanoTime();
                Vec3 me = mc().player.position();
                List<Track> list = new ArrayList<>();

                for (Track t : this.tracks.values()) {
                    if (t.mark != null && t.dead < 0L) {
                        list.add(t);
                    }
                }

                list.sort((xx, yx) -> Double.compare(xx.mark.distanceToSqr(me), yx.mark.distanceToSqr(me)));
                int fs = Math.max(11, Math.round((float) Px.height() / 1080.0F * 13.0F));

                for (int i = 0; i < Math.min(4, list.size()); i++) {
                    Track t = (Track) list.get(i);
                    float a = alphaOf(t, now);
                    double lift = t.target != null ? (double) t.target.getBbHeight() + 0.45 : 0.5;
                    double[] s = Projection.toScreen(t.mark.add(0.0, lift, 0.0), Px.width(), Px.height());
                    if (s != null
                            && !(s[0] < -80.0)
                            && !(s[0] > (double) (Px.width() + 80))
                            && !(s[1] < -40.0)
                            && !(s[1] > (double) (Px.height() + 40))) {
                        String time = String.format(Locale.ROOT, "%.1f с", Math.max(0.0F, t.remain));
                        String text = t.target != null
                                ? ChatFormatting.stripFormatting(
                                                t.target.getName().getString()) + " · " + time
                                : (t.pearl ? Lang.tr("Телепорт") + " · " + time : time);
                        int h = Math.round((float) fs * 2.0F);
                        int w = Px.textWidth(text, Px.Weight.SEMIBOLD, fs) + fs * 2;
                        int x = (int) s[0] - w / 2;
                        int y = (int) s[1] - h;
                        float old = Px.fade;
                        Px.fade = old * a;
                        int accent = t.target != null ? this.hit() : this.lit();
                        Px.shadow(g, x, y, w, h, fs, 1207959552);
                        Px.glow(g, x - fs, y - fs / 2, w + fs * 2, h + fs, accent & 0xFFFFFF | 805306368);
                        Px.round(g, x, y, w, h, h / 2, 0xE60C0B14);
                        Px.outline(g, x, y, w, h, h / 2, accent & 0xFFFFFF | 1879048192);
                        Px.textCenter(g, text, x + w / 2, y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, 0xFFF3EFF8);
                        Px.fade = old;
                    }
                }
            }
        }
    }

    private void discs(GuiGraphics g) {
        long now = System.nanoTime();
        Vec3 me = mc().player.position().add(0.0, (double) (mc().player.getBbHeight() / 2.0F), 0.0);
        List<Track> list = new ArrayList<>();

        for (Track t : this.tracks.values()) {
            if (t.mark != null && t.dead < 0L) {
                list.add(t);
            }
        }

        list.sort((x, y) -> Double.compare(y.mark.distanceToSqr(me), x.mark.distanceToSqr(me)));

        for (Track t : list) {
            float left = t.total > 0.0F ? Math.max(0.0F, Math.min(1.0F, t.remain / t.total)) : 1.0F;
            this.disc(g, t.mark, t.item, left, alphaOf(t, now), t.own ? 0.0F : danger(t, me));
        }

        if (this.aim != null && this.aimTrack.mark != null) {
            this.disc(g, this.aimTrack.mark, this.aimItem, 1.0F, this.aimShow, 0.0F);
        }
    }

    private static float danger(Track t, Vec3 me) {
        double reach = !t.item.is(Items.SPLASH_POTION) && !t.item.is(Items.LINGERING_POTION) ? 1.5 : 4.0;
        double d = t.target == mc().player ? 0.0 : t.mark.distanceTo(me);
        return smooth((float) ((reach * 2.0 - d) / reach));
    }

    private void disc(GuiGraphics g, Vec3 at, ItemStack item, float left, float a, float danger) {
        int w = Px.width();
        int h = Px.height();
        float u = (float) h / 1080.0F;
        int r = Math.max(14, Math.round(30.0F * u));
        int m = r + Math.round(10.0F * u);
        double[] s = Projection.toScreen(at, w, h);
        float cx;
        float cy;
        if (s != null
                && !(s[0] < (double) m)
                && !(s[0] > (double) (w - m))
                && !(s[1] < (double) m)
                && !(s[1] > (double) (h - m))) {
            cx = (float) s[0];
            cy = (float) s[1];
        } else {
            Camera cam = mc().gameRenderer.getMainCamera();
            Vec3 rel = at.subtract(cam.position());
            Vector3fc lv = cam.leftVector();
            Vector3fc uv = cam.upVector();
            double x = -(rel.x * (double) lv.x() + rel.y * (double) lv.y() + rel.z * (double) lv.z());
            double y = rel.x * (double) uv.x() + rel.y * (double) uv.y() + rel.z * (double) uv.z();
            double ang = Math.atan2(-y, x);
            double ring = (double) Math.min(w, h) * 0.36;
            cx = (float) ((double) w / 2.0 + Math.cos(ang) * ring);
            cy = (float) ((double) h / 2.0 + Math.sin(ang) * ring);
        }

        int ix = Math.round(cx);
        int iy = Math.round(cy);
        int acc = this.laserColour(0.0);
        float old = Px.fade;
        Px.fade = old * a;
        Px.circle(g, ix, iy, r, 0xB80E0D14);
        if (danger > 0.01F) {
            Px.circle(g, ix, iy, r, Px.a(15026253, Math.round(165.0F * danger)));
        }

        Px.ring(g, ix, iy, r, 620756991);
        int thick = Math.max(2, Math.round(3.0F * u));
        Px.arc(
                g,
                cx,
                cy,
                (float) r - (float) thick / 2.0F - 1.0F,
                thick,
                (-Math.PI / 2D),
                (Math.PI * 2D) * (double) left,
                acc,
                acc);
        int is = Math.round((float) r * 1.05F);
        if (!item.isEmpty()) {
            HudElements.item(g, item, ix - is / 2, iy - is / 2, is);
        }

        Px.fade = old;
    }

    private static record Ahead(List<Vec3> pts, Vec3 end, Direction face, Entity target, int ticks) {}

    private static final class Track {
        final boolean pearl;
        long born = System.nanoTime();
        final List<Vec3> trail = new ArrayList<>();
        Vec3 head;
        Vec3 mark;
        Vec3 markN = new Vec3(0.0, 1.0, 0.0);
        Ahead ahead;
        Entity target;
        float remain;
        float shown;
        long dead = -1L;
        int lastTick = -1;
        float total;
        boolean own;
        ItemStack item;
        float yaw;
        List<Vec3> flown;
        List<Vec3> road;
        double done;

        Track(boolean pearl) {
            this.item = ItemStack.EMPTY;
            this.flown = List.of();
            this.pearl = pearl;
        }
    }

    private static record Frame(PoseStack.Pose pose, Vec3 cam, float perPx, long now, float pt) {}

    private static record Throw(Vec3 from, Vec3 v, double gravity, double air, double water, ItemStack item) {}

    private interface Around {
        int at(float t);
    }

    private interface Shade {
        int at(double t);
    }

    private interface Width {
        float at(double t);
    }
}
