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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.helper.HolyItems;
import net.arcanic.visuals.helper.HolyRules;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Tag;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TrapZone extends Module {
    private final Kind kind;
    private final ItemZones owner;
    private final Setting.Colour zone;
    private final Setting.Colour caught;
    private final Setting.Bool players;
    private final Setting.Bool timer;
    private final Setting.Bool damage;
    private final Setting.Bool blast;
    private final Setting.Bool scan;
    private static final int BLAST = -23987;
    private final List<Active> active = new ArrayList<>();
    private HolyItems.Trap heldTrap;
    private HolyItems.Blast heldBlast;
    private float show;
    private long heldSince;
    private double[] at;
    private double[] blastAt;
    private final Map<Integer, float[]> marks = new HashMap<>();
    private long lastNanos = System.nanoTime();
    private boolean warned;
    private final List<Pending> pending = new ArrayList<>();

    public TrapZone(ItemZones owner, Kind kind) {
        super(kind.id, kind.name, "", Category.HOLYWORLD, true);
        this.kind = kind;
        this.owner = owner;
        ItemZones.Item it = owner.item(kind.item);
        this.zone = it.zone;
        this.caught = it.caught;
        this.players = it.players;
        this.timer = it.timer;
        this.damage = it.damage;
        this.blast = it.trapBlast;
        this.scan = it.glow;
    }

    public boolean enabled() {
        return this.owner.on(this.kind.item, this);
    }

    private HolyItems.Trap trapHeld(Player p) {
        if (this.kind != TrapZone.Kind.BLAST && !HolyRules.safeHere()) {
            HolyItems.Trap t = HolyItems.heldTrap(p);
            if (t == null) {
                return null;
            } else if (t.id().equals("STUN") != (this.kind == TrapZone.Kind.STUN)) {
                return null;
            } else {
                return this.kind == TrapZone.Kind.STUN && HolyRules.stunOffHere() ? null : t;
            }
        } else {
            return null;
        }
    }

    private HolyItems.Blast blastHeld(Player p) {
        return this.kind == TrapZone.Kind.BLAST && !HolyRules.blastOffHere() && !HolyRules.safeHere()
                ? HolyItems.heldBlast(p)
                : null;
    }

    private boolean offHere(Player p) {
        if (p == null) {
            return false;
        } else if (this.kind == TrapZone.Kind.STUN) {
            HolyItems.Trap t = HolyItems.heldTrap(p);
            return t != null && t.id().equals("STUN") && HolyRules.stunOffHere();
        } else {
            return this.kind == TrapZone.Kind.BLAST && HolyItems.heldBlast(p) != null && HolyRules.blastOffHere();
        }
    }

    static int alpha(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | c & 0xFFFFFF;
    }

    static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    static float smooth(float t) {
        t = clamp(t);
        return t * t * (3.0F - 2.0F * t);
    }

    static float expo(float t) {
        return t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, (double) (-10.0F * clamp(t)));
    }

    public static void register() {
        UseItemCallback.EVENT.register((UseItemCallback) (player, level, hand) -> {
            for (TrapZone z : Modules.ZONES) {
                z.used(player);
            }

            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((UseBlockCallback) (player, level, hand, hit) -> {
            for (TrapZone z : Modules.ZONES) {
                z.used(player);
            }

            return InteractionResult.PASS;
        });
    }

    private void used(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (this.enabled() && player == mc.player && !HolyRules.safeHere()) {
            long now = System.currentTimeMillis();
            if (this.active.isEmpty() || now - ((Active) this.active.get(this.active.size() - 1)).start() >= 300L) {
                if (this.pending.isEmpty()
                        || now
                                        - ((Pending) this.pending.get(this.pending.size() - 1))
                                                .use()
                                                .at()
                                >= 300L) {
                    HolyItems.Trap t = this.trapHeld(player);
                    HolyItems.Blast b = this.blastHeld(player);
                    if (t != null || b != null) {
                        InteractionHand hand = InteractionHand.MAIN_HAND;
                        ItemStack icon = player.getMainHandItem();
                        if (HolyItems.id(icon) == null
                                && !icon.getHoverName().getString().contains("Стан")) {
                            hand = InteractionHand.OFF_HAND;
                            icon = player.getOffhandItem();
                        }

                        icon = icon.copy();
                        Active a;
                        if (t != null) {
                            AABB box = t.box(player.blockPosition());
                            a = new Active(
                                    t,
                                    (HolyItems.Blast) null,
                                    box,
                                    box.getCenter(),
                                    now,
                                    now + (long) Math.round(t.seconds() * 1000.0F),
                                    icon);
                        } else {
                            a = new Active(
                                    (HolyItems.Trap) null,
                                    b,
                                    b.box(player.position()),
                                    player.position(),
                                    now,
                                    now + (long) Math.round(b.fuse() * 1000.0F),
                                    icon);
                        }

                        this.pending.add(new Pending(HolyRules.Use.of(hand), a));
                    }
                }
            }
        }
    }

    public void tick() {
        Iterator<Pending> it = this.pending.iterator();

        while (it.hasNext()) {
            Pending p = (Pending) it.next();
            if (p.use().taken()) {
                this.active.add(p.active());
                it.remove();
            } else if (p.use().late()) {
                it.remove();
            }
        }
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Player me = mc.player;
        long nanos = System.nanoTime();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (float) (nanos - this.lastNanos) / 1.0E9F);
        this.lastNanos = nanos;
        HolyItems.Trap t = this.trapHeld(me);
        HolyItems.Blast b = t == null ? this.blastHeld(me) : null;
        boolean holding = t != null || b != null;
        if (holding && (this.show < 0.02F || t != this.heldTrap || b != this.heldBlast)) {
            this.heldSince = now;
        }

        if (holding) {
            this.heldTrap = t;
            this.heldBlast = b;
        }

        this.show += ((float) (holding ? 1 : 0) - this.show)
                * (1.0F - (float) Math.exp((double) (-dt * (float) (holding ? 12 : 9))));
        this.active.removeIf((ax) -> now > ax.end() + 1200L);
        if (this.show < 0.01F) {
            this.at = null;
            this.blastAt = null;
        }

        if (this.show < 0.01F && this.active.isEmpty()) {
            this.marks.clear();
        } else {
            Vec3 feet = me.getPosition(pt);
            BlockPos block = BlockPos.containing(feet);
            AABB box = this.heldTrap != null ? this.heldTrap.box(block) : null;
            if (box != null) {
                double[] want = {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ};
                float k = 1.0F - (float) Math.exp((double) (-dt * 20.0F));
                if (this.at != null
                        && !(Math.abs(this.at[0] - want[0])
                                        + Math.abs(this.at[1] - want[1])
                                        + Math.abs(this.at[2] - want[2])
                                > 6.0)) {
                    for (int i = 0; i < 6; i++) {
                        this.at[i] += (want[i] - this.at[i]) * (double) k;
                    }
                } else {
                    this.at = want.clone();
                }
            }

            if (this.heldBlast != null) {
                double[] want = bounds(this.heldBlast.box(feet));
                float k = 1.0F - (float) Math.exp((double) (-dt * 16.0F));
                if (this.blastAt != null
                        && !(Math.abs(this.blastAt[0] - want[0])
                                        + Math.abs(this.blastAt[1] - want[1])
                                        + Math.abs(this.blastAt[2] - want[2])
                                > 6.0)) {
                    for (int i = 0; i < 6; i++) {
                        this.blastAt[i] += (want[i] - this.blastAt[i]) * (double) k;
                    }
                } else {
                    this.blastAt = want.clone();
                }
            }

            this.track(mc, me, box, feet, dt, now);
            double fov = Math.toRadians((double) Projection.fov());
            Frame f = new Frame(
                    ctx.matrices().last(),
                    cam,
                    (float) (2.0
                            * Math.tan(fov / 2.0)
                            / (double) Math.max(1, mc.getWindow().getHeight())),
                    pt,
                    (float) (nanos % 1000000000000L) / 1.0E9F);
            float appear = (float) (now - this.heldSince) / 700.0F;

            for (boolean under : new boolean[] {true, false}) {
                VertexConsumer vc = ctx.consumers()
                        .getBuffer(Fx.ribbon(
                                under
                                        ? Fx.Tex.LINE_GLOW
                                        : (this.kind == TrapZone.Kind.TRAP ? Fx.Tex.LINE_EDGE : Fx.Tex.LINE_AA),
                                false));
                int zc = this.zone.argb() | 0xFF000000;
                if (this.show > 0.01F && this.heldTrap != null && this.at != null) {
                    if (this.kind == TrapZone.Kind.STUN) {
                        Curtain.cube(vc, f, this.at, under, this.show, appear * 0.7F, zc, this.scan.on());
                    } else {
                        this.box(vc, f, this.at, feet, under, this.show, appear, true, zc);
                    }
                }

                if (this.show > 0.01F && this.heldBlast != null && this.blastAt != null) {
                    this.pad(vc, f, this.blastAt, under, this.show, appear, this.danger(0.25F), 0.25F);
                }

                for (Active a : this.active) {
                    float life = clamp((float) (now - a.start()) / 250.0F)
                            * (1.0F - clamp((float) (now - a.end()) / 500.0F));
                    float left = clamp((float) (a.end() - now) / (float) (a.end() - a.start()));
                    if (a.trap() != null) {
                        if (this.kind == TrapZone.Kind.STUN) {
                            Curtain.cube(
                                    vc,
                                    f,
                                    bounds(a.box()),
                                    under,
                                    life * (0.45F + 0.55F * left),
                                    9.0F,
                                    zc,
                                    this.scan.on());
                        } else {
                            this.box(
                                    vc,
                                    f,
                                    bounds(a.box()),
                                    (Vec3) null,
                                    under,
                                    life * (0.45F + 0.55F * left),
                                    9.0F,
                                    false,
                                    zc);
                        }
                    } else {
                        float d = 0.25F + 0.75F * (1.0F - left);
                        this.pad(vc, f, bounds(a.box()), under, life, 9.0F, this.danger(d), d);
                    }
                }
            }
        }
    }

    private void track(Minecraft mc, Player me, AABB held, Vec3 feet, float dt, long now) {
        float k = 1.0F - (float) Math.exp((double) (-dt * 10.0F));
        Map<Integer, float[]> next = new HashMap<>();

        for (AbstractClientPlayer p : mc.level.players()) {
            if (p != me && !p.isSpectator() && !p.isRemoved() && !(p.distanceToSqr(me) > 2304.0)) {
                AABB bb = p.getBoundingBox();
                boolean in = this.show > 0.3F && held != null && bb.intersects(held);
                float hp = 0.0F;
                if (this.show > 0.3F && this.heldBlast != null) {
                    hp = this.damageTo(p, feet, this.heldBlast);
                }

                for (Active a : this.active) {
                    if (now <= a.end()) {
                        if (a.trap() != null) {
                            in |= bb.intersects(a.box());
                        } else {
                            hp = Math.max(hp, this.damageTo(p, a.centre(), a.blast()));
                        }
                    }
                }

                float[] old = this.marks.get(p.getId());
                float c = old == null ? 0.0F : old[0];
                float target = !in && !(hp > 0.25F) ? 0.0F : 1.0F;
                c += (target - c) * k;
                if (c > 0.01F || target > 0.0F) {
                    next.put(p.getId(), new float[] {c, hp, p.getHealth() + p.getAbsorptionAmount()});
                }
            }
        }

        this.marks.clear();
        this.marks.putAll(next);
    }

    private static double[] bounds(AABB x) {
        return new double[] {x.minX, x.minY, x.minZ, x.maxX, x.maxY, x.maxZ};
    }

    private int danger(float k) {
        return Fx.lerp(this.zone.argb() | 0xFF000000, this.caught.argb() | 0xFF000000, clamp(k));
    }

    private float damageTo(Player p, Vec3 at, HolyItems.Blast b) {
        try {
            return HolyItems.blastDamage(p, at, b);
        } catch (RuntimeException e) {
            if (!this.warned) {
                this.warned = true;
                System.err.println("[ArcanicVisuals] trap damage estimate failed: " + e);
            }

            return 0.0F;
        }
    }

    private void box(
            VertexConsumer vc,
            Frame f,
            double[] b,
            Vec3 feet,
            boolean under,
            float a,
            float appear,
            boolean drawOut,
            int c) {
        zone(vc, f, b, feet, under, a, appear, drawOut, c, this.scan.on());
        float edges = drawOut ? expo((appear - 0.25F) / 0.5F) : 1.0F;
        if (this.blast.on() && drawOut && feet != null && this.heldTrap != null && !under) {
            ring(
                    vc,
                    f,
                    new Vec3(feet.x, feet.y + 0.03, feet.z),
                    (double) (this.heldTrap.damage() * edges),
                    4.5F,
                    alpha(-23987, a * 0.7F));
        }
    }

    /**
     * A trap zone as HolyWorld's helpers draw it: rays from the player out to the corners as it appears, edges
     * growing in, breathing walls with a scan band, glowing corner dots. Two passes: {@code under} is the soft glow.
     */
    static void zone(
            VertexConsumer vc,
            Frame f,
            double[] b,
            Vec3 feet,
            boolean under,
            float a,
            float appear,
            boolean drawOut,
            int c,
            boolean scanOn) {
        int light = Fx.lerp(c, -1, 0.35F);
        double x0 = b[0];
        double z0 = b[2];
        double x1 = b[3];
        double z1 = b[5];
        double y0 = b[1] + 0.02;
        double y1 = b[4];
        double h = y1 - y0;
        float rays = drawOut ? expo(appear / 0.45F) : 1.0F;
        float edges = drawOut ? expo((appear - 0.25F) / 0.5F) : 1.0F;
        float walls = drawOut ? smooth((appear - 0.35F) / 0.55F) : 1.0F;
        float breathe = 0.78F + 0.22F * (float) Math.sin((double) f.time * Math.PI * 2.0 / 1.8);
        double[][] corners = new double[][] {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        if (drawOut && feet != null && appear < 1.2F) {
            float fade = 1.0F - smooth((appear - 0.5F) / 0.5F);
            Vec3 from = feet.add(0.0, 1.1, 0.0);
            int head = alpha(light, a * fade * (under ? 0.5F : 1.0F));

            for (double[] cn : corners) {
                for (double y : new double[] {y0, y1}) {
                    Vec3 to = new Vec3(cn[0], y, cn[1]);
                    line(
                            vc,
                            f,
                            from,
                            from.add(to.subtract(from).scale((double) rays)),
                            under ? 12.0F : 5.0F,
                            alpha(c, 0.0F),
                            head);
                }
            }
        }

        if (under && walls > 0.01F) {
            int rows = 12;
            float band = scanOn ? f.time % 2.6F / 2.6F : -1.0F;
            float k = a * walls * breathe;

            for (int r = 0; r < rows; r++) {
                float t0 = (float) r / (float) rows;
                float t1 = (float) (r + 1) / (float) rows;
                int c0 = alpha(c, k * wall(t0, band));
                int c1 = alpha(c, k * wall(t1, band));
                double ya = y0 + h * (double) t0;
                double yb = y0 + h * (double) t1;
                face(vc, f, x0, ya, z0, x1, ya, z0, x1, yb, z0, x0, yb, z0, c0, c0, c1, c1);
                face(vc, f, x1, ya, z0, x1, ya, z1, x1, yb, z1, x1, yb, z0, c0, c0, c1, c1);
                face(vc, f, x1, ya, z1, x0, ya, z1, x0, yb, z1, x1, yb, z1, c0, c0, c1, c1);
                face(vc, f, x0, ya, z1, x0, ya, z0, x0, yb, z0, x0, yb, z1, c0, c0, c1, c1);
            }

            int fl = alpha(c, k * 0.12F);
            face(vc, f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, fl, fl, fl, fl);
        }

        if (edges > 0.01F) {
            float px = under ? 14.0F : 6.0F;
            float g = under ? 0.45F : 1.0F;
            int bright = alpha(light, a * g);
            int dim = alpha(c, a * 0.55F * g);

            for (int i = 0; i < 4; i++) {
                double[] p = corners[i];
                double[] q = corners[(i + 1) % 4];
                grow(vc, f, new Vec3(p[0], y0, p[1]), new Vec3(q[0], y0, q[1]), edges, px, bright);
                grow(vc, f, new Vec3(p[0], y1, p[1]), new Vec3(q[0], y1, q[1]), edges, px, dim);
                line(
                        vc,
                        f,
                        new Vec3(p[0], y0, p[1]),
                        new Vec3(p[0], y0 + h * (double) edges, p[1]),
                        px,
                        bright,
                        Fx.lerp(bright, dim, edges));
            }

            if (under) {
                int dot = alpha(Fx.lerp(c, -1, 0.55F), a * edges * (0.45F + 0.3F * breathe));

                for (double[] cn : corners) {
                    line(vc, f, new Vec3(cn[0], y0, cn[1]), new Vec3(cn[0], y0, cn[1]), 30.0F, dot, dot);
                    line(vc, f, new Vec3(cn[0], y1, cn[1]), new Vec3(cn[0], y1, cn[1]), 20.0F, dot, dot);
                }
            }
        }
    }

    private void pad(
            VertexConsumer vc, Frame f, double[] b, boolean under, float a, float appear, int c, float danger) {
        Curtain.square(
                vc,
                f,
                b[0],
                b[2],
                b[3],
                b[5],
                b[1] + 1.02,
                under,
                a,
                appear * 0.7F,
                clamp((danger - 0.25F) / 0.75F),
                this.zone.argb() | 0xFF000000,
                this.scan.on());
    }

    static float wall(float t, float band) {
        float base = 0.22F * (float) Math.pow((double) (1.0F - t), 1.6);
        if (band < 0.0F) {
            return base;
        } else {
            float d = (t - band) / 0.09F;
            return base + 0.12F * (float) Math.exp((double) (-d * d)) * (1.0F - t * 0.6F);
        }
    }

    public int tint(Entity e, int colour) {
        if (this.enabled() && this.players.on() && e != null) {
            float[] m = this.marks.get(e.getId());
            if (m != null && !(m[0] < 0.01F)) {
                float time = (float) (System.nanoTime() % 1000000000000L) / 1.0E9F;
                float pulse = 0.5F + 0.5F * (float) Math.sin((double) time * Math.PI * 2.0 * 1.3);
                float k = m[0] * (0.35F + 0.5F * pulse);
                int red = Fx.lerp(this.caught.argb() | 0xFF000000, -65536, 0.3F);
                return mul(colour, Fx.lerp(-1, red, k));
            } else {
                return colour;
            }
        } else {
            return colour;
        }
    }

    private static int mul(int a, int b) {
        int r = (a >> 16 & 255) * (b >> 16 & 255) / 255;
        int g = (a >> 8 & 255) * (b >> 8 & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return a & 0xFF000000 | r << 16 | g << 8 | bl;
    }

    void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.level != null) {
            if (this.offHere(mc.player)) {
                HolyRules.note(
                        g,
                        this.kind == TrapZone.Kind.STUN
                                ? "Станы на этой анархии отключены сервером"
                                : "Взрывные трапки на этой анархии отключены сервером");
            }

            long now = System.currentTimeMillis();
            int fs = Math.max(11, Math.round((float) Px.height() / 1080.0F * 13.0F));
            if (this.timer.on()) {
                for (Active a : this.active) {
                    float left = (float) (a.end() - now) / 1000.0F;
                    float total = (float) (a.end() - a.start()) / 1000.0F;
                    float life = clamp((float) (now - a.start()) / 250.0F)
                            * (1.0F - clamp((float) (now - a.end()) / 500.0F));
                    Vec3 at =
                            a.trap() != null ? a.box().getCenter() : a.centre().add(0.0, 1.2, 0.0);
                    this.countdown(g, at, Math.max(0.0F, left), total, life, a.trap() == null, fs, a.icon());
                }
            }

            if (this.damage.on() && this.players.on()) {
                float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                int red = this.caught.argb() | 0xFF000000;

                for (Map.Entry<Integer, float[]> e : this.marks.entrySet()) {
                    float k = ((float[]) e.getValue())[0];
                    float hp = ((float[]) e.getValue())[1];
                    float health = ((float[]) e.getValue())[2];
                    if (!(hp < 0.25F) && !(k < 0.05F)) {
                        Entity ent = mc.level.getEntity(e.getKey());
                        if (ent != null) {
                            Vec3 over = ent.getPosition(pt).add(0.0, (double) ent.getBbHeight() + 0.55, 0.0);
                            double[] s = Projection.toScreen(over, Px.width(), Px.height());
                            if (s != null) {
                                boolean kills = hp >= health;
                                int accent = kills ? red : Fx.lerp(-23987, red, clamp(hp / Math.max(1.0F, health)));
                                String tag = Tag.damage(hp, kills);
                                pill(g, (int) s[0], (int) s[1], kills, tag, kills ? red : accent, kills, k, fs);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void pill(
            GuiGraphics g, int cx, int y, boolean skull, String text, int accent, boolean solid, float a, int fs) {
        Tag.draw(g, cx, y - Tag.height(fs), fs, text, skull, a);
    }

    private void countdown(
            GuiGraphics g, Vec3 at, float left, float total, float a, boolean bang, int fs, ItemStack icon) {
        countdown(
                g, at, left, total, a, bang, fs, icon, this.zone.argb() | 0xFF000000, this.caught.argb() | 0xFF000000);
    }

    /** The timer card over a placed zone: time left, a bar running down, beating faster near the end. */
    static void countdown(
            GuiGraphics g,
            Vec3 at,
            float left,
            float total,
            float a,
            boolean bang,
            int fs,
            ItemStack icon,
            int zoneColour,
            int red) {
        double[] s = Projection.toScreen(at, Px.width(), Px.height());
        if (s != null && !(a < 0.01F)) {
            int cx = (int) s[0];
            int cy = (int) s[1];
            int base = bang ? red : zoneColour;
            float urgent = bang ? 1.0F : 1.0F - clamp(left / 3.0F);
            int accent = Fx.lerp(base, red, urgent);
            float time = (float) (System.nanoTime() % 1000000000000L) / 1.0E9F;
            float beat = 1.0F + 0.06F * urgent * (float) Math.max(0.0, Math.sin((double) time * Math.PI * 2.0 * 1.6));
            String text = String.format(Locale.ROOT, "%.1f с", left);
            int ns = Math.round((float) fs * 1.15F);
            int gap = fs / 2;
            int w = Math.max(Px.textWidth("00.0 с", Px.Weight.BOLD, ns), Px.textWidth(text, Px.Weight.BOLD, ns))
                    + ns
                    + gap
                    + fs * 2;
            int h = Math.round((float) fs * 2.4F);
            int x = cx - w / 2;
            int y = cy - h / 2;
            int bar = Math.max(2, fs / 5);
            float old = Px.fade;
            Px.fade = old * a;
            g.pose().pushMatrix();
            g.pose().translate((float) cx, (float) cy);
            g.pose().scale(beat, beat);
            g.pose().translate((float) (-cx), (float) (-cy));
            Px.shadow(g, x, y + fs / 4, w, h, fs, 1610612736);
            Px.round(g, x, y, w, h, fs / 2, 0xEB0B0B10);
            Px.outline(g, x, y, w, h, fs / 2, accent & 0xFFFFFF | 1610612736);
            if (icon != null && !icon.isEmpty()) {
                HudElements.item(g, icon, x + fs, y + (h - bar - ns) / 2, ns);
            } else {
                Px.icon(g, bang ? "w.bolt" : "w.pie", x + fs, y + (h - bar - ns) / 2, ns, Fx.lerp(accent, -1, 0.25F));
            }

            Px.text(g, text, x + fs + ns + gap, y + (h - bar - ns) / 2, Px.Weight.BOLD, ns, -1);
            int bx = x + fs / 2;
            int bw = w - fs;
            int by = y + h - bar - fs / 3;
            Px.round(g, bx, by, bw, bar, bar / 2, 587202559);
            Px.pill(
                    g,
                    bx,
                    by,
                    Math.max(bar, Math.round((float) bw * clamp(left / total))),
                    bar,
                    Fx.lerp(accent, -1, 0.3F),
                    accent);
            g.pose().popMatrix();
            Px.fade = old;
        }
    }

    private static void grow(VertexConsumer vc, Frame f, Vec3 a, Vec3 b, float k, float px, int c) {
        if (k >= 0.999F) {
            line(vc, f, a, b, px, c, c);
        } else {
            Vec3 mid = a.add(b).scale(0.5);
            line(vc, f, a, a.add(mid.subtract(a).scale((double) k)), px, c, c);
            line(vc, f, b, b.add(mid.subtract(b).scale((double) k)), px, c, c);
        }
    }

    static void ring(VertexConsumer vc, Frame f, Vec3 o, double r, float px, int c) {
        if (!(r < 0.05)) {
            int n = Math.max(24, (int) (r * 14.0));

            for (int i = 0; i < n; i++) {
                double a0 = (double) i * Math.PI * 2.0 / (double) n;
                double a1 = (double) (i + 1) * Math.PI * 2.0 / (double) n;
                Vec3 p = o.add(Math.cos(a0) * r, 0.0, Math.sin(a0) * r);
                Vec3 q = o.add(Math.cos(a1) * r, 0.0, Math.sin(a1) * r);
                line(vc, f, p, q, px, c, c);
            }
        }
    }

    static void line(VertexConsumer vc, Frame f, Vec3 a, Vec3 b, float px, int ca, int cb) {
        double len = a.distanceTo(b);
        if (len < 1.0E-4) {
            Vec3 p = a.subtract(f.cam);
            double s = Math.max(0.0015, p.length() * (double) f.perPx * (double) px / 2.0);
            Vec3 side = p.cross(new Vec3(0.0, 1.0, 0.0));
            if (side.lengthSqr() < 1.0E-12) {
                side = new Vec3(1.0, 0.0, 0.0);
            }

            Vec3 u = side.normalize().scale(s);
            Vec3 v = u.cross(p).normalize().scale(s);
            Fx.v(
                    vc,
                    f.pose,
                    (float) (p.x + u.x + v.x),
                    (float) (p.y + u.y + v.y),
                    (float) (p.z + u.z + v.z),
                    0.0F,
                    0.0F,
                    ca);
            Fx.v(
                    vc,
                    f.pose,
                    (float) (p.x - u.x + v.x),
                    (float) (p.y - u.y + v.y),
                    (float) (p.z - u.z + v.z),
                    0.0F,
                    1.0F,
                    ca);
            Fx.v(
                    vc,
                    f.pose,
                    (float) (p.x - u.x - v.x),
                    (float) (p.y - u.y - v.y),
                    (float) (p.z - u.z - v.z),
                    1.0F,
                    1.0F,
                    ca);
            Fx.v(
                    vc,
                    f.pose,
                    (float) (p.x + u.x - v.x),
                    (float) (p.y + u.y - v.y),
                    (float) (p.z + u.z - v.z),
                    1.0F,
                    0.0F,
                    ca);
        } else {
            int n = Math.max(1, (int) Math.ceil(len / 0.5));
            Vec3 dir = b.subtract(a);

            for (int i = 0; i < n; i++) {
                float t0 = (float) i / (float) n;
                float t1 = (float) (i + 1) / (float) n;
                Vec3 p0 = a.add(dir.scale((double) t0)).subtract(f.cam);
                Vec3 p1 = a.add(dir.scale((double) t1)).subtract(f.cam);
                Vec3 s0 = dir.cross(p0);
                Vec3 s1 = dir.cross(p1);
                if (!(s0.lengthSqr() < 1.0E-14) && !(s1.lengthSqr() < 1.0E-14)) {
                    s0 = s0.normalize().scale(Math.max(0.0015, p0.length() * (double) f.perPx * (double) px / 2.0));
                    s1 = s1.normalize().scale(Math.max(0.0015, p1.length() * (double) f.perPx * (double) px / 2.0));
                    int c0 = Fx.lerp(ca, cb, t0);
                    int c1 = Fx.lerp(ca, cb, t1);
                    Fx.v(
                            vc,
                            f.pose,
                            (float) (p0.x + s0.x),
                            (float) (p0.y + s0.y),
                            (float) (p0.z + s0.z),
                            0.0F,
                            0.0F,
                            c0);
                    Fx.v(
                            vc,
                            f.pose,
                            (float) (p0.x - s0.x),
                            (float) (p0.y - s0.y),
                            (float) (p0.z - s0.z),
                            0.0F,
                            1.0F,
                            c0);
                    Fx.v(
                            vc,
                            f.pose,
                            (float) (p1.x - s1.x),
                            (float) (p1.y - s1.y),
                            (float) (p1.z - s1.z),
                            1.0F,
                            1.0F,
                            c1);
                    Fx.v(
                            vc,
                            f.pose,
                            (float) (p1.x + s1.x),
                            (float) (p1.y + s1.y),
                            (float) (p1.z + s1.z),
                            1.0F,
                            0.0F,
                            c1);
                }
            }
        }
    }

    static void face(
            VertexConsumer vc,
            Frame f,
            double ax,
            double ay,
            double az,
            double bx,
            double by,
            double bz,
            double cx,
            double cy,
            double cz,
            double dx,
            double dy,
            double dz,
            int ca,
            int cb,
            int cc,
            int cd) {
        Vec3 o = f.cam;
        Fx.v(vc, f.pose, (float) (ax - o.x), (float) (ay - o.y), (float) (az - o.z), 0.0F, 0.5F, ca);
        Fx.v(vc, f.pose, (float) (bx - o.x), (float) (by - o.y), (float) (bz - o.z), 0.0F, 0.5F, cb);
        Fx.v(vc, f.pose, (float) (cx - o.x), (float) (cy - o.y), (float) (cz - o.z), 0.0F, 0.5F, cc);
        Fx.v(vc, f.pose, (float) (dx - o.x), (float) (dy - o.y), (float) (dz - o.z), 0.0F, 0.5F, cd);
    }

    public static enum Kind {
        TRAP("holy_traps", "Трапка", "trap"),
        BLAST("holy_blast_trap", "Взрывная трапка", "blast_trap"),
        STUN("holy_stun", "Стан", "stun");

        final String id;
        final String name;
        final String item;

        private Kind(String id, String name, String item) {
            this.id = id;
            this.name = name;
            this.item = item;
        }
    }

    private static record Active(
            HolyItems.Trap trap, HolyItems.Blast blast, AABB box, Vec3 centre, long start, long end, ItemStack icon) {}

    static record Frame(PoseStack.Pose pose, Vec3 cam, float perPx, float pt, float time) {}

    private static record Pending(HolyRules.Use use, Active active) {}
}
