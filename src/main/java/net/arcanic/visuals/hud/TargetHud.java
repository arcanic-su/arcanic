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
package net.arcanic.visuals.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.arcanic.visuals.core.Health;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.MobHeads;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;

public final class TargetHud extends HudModule {
    final Setting.Mode style = this.add(new Setting.Mode(
            "style3", "Стиль", "Панель", new String[] {"Панель", "Карточка", "Классический", "Компактный"}));
    final Setting.Mode cardArmor = (Setting.Mode) ((Setting.Mode)
                    this.add(new Setting.Mode("card_armor", "Броня", "Число", new String[] {"Нет", "Число", "Иконки"})))
            .showIf(() -> this.style.is("Карточка"));
    final Setting.Bool hurtTint = (this.add(new Setting.Bool("hurt_tint", "Краснеет от урона", true)))
            .showIf(() -> this.style.is("Компактный"));
    final Setting.Mode barStyle = (Setting.Mode)
            this.add(new Setting.Mode("bar2", "Цвет полоски", "Тема", new String[] {"Тема", "Здоровье"}));
    final Setting.Bool gear =
            (this.add(new Setting.Bool("gear2", "Снаряжение", true))).showIf(() -> this.style.is("Классический"));
    final Setting.Bool popups =
            (this.add(new Setting.Bool("popups", "Урон у цифры", true))).showIf(() -> this.style.is("Классический"));
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static LivingEntity lastHit;
    private static long lastHitAt;
    private static TargetHud INSTANCE;
    private LivingEntity shownTarget;
    private float fade;
    private float flash;
    private float hpShown = 1.0F;
    private float hpGhost = 1.0F;
    private float hpNumber;
    private boolean hpKnown = true;
    private float lastHp = -1.0F;
    private long last = System.nanoTime();
    private long ghostHold;
    private long diedAt;
    private long swapAt;
    private float stripK;
    private boolean showing;
    private float dt = 0.016666668F;
    private final List<Pop> pops = new ArrayList<>();
    private final Tween cardShow;
    private final Tween cardHead;
    private final Tween cardName;
    private final Tween cardBar;
    private final Tween cardArmour;
    private final Tween cardAbsorb;
    private static final Identifier CORNERS =
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/corner_mask.png");
    private static final Identifier WHO =
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/card_who.png");
    private static final Identifier WHO_DARK =
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/card_whodark.png");
    private static final float CARD_H = 30.0F;
    private LivingEntity cardTarget;
    private float cardBarShown;
    private float cardBarTrail;
    private float cardLastHp;
    private long cardTrailHold;

    public TargetHud() {
        super("targethud", "Таргет-HUD", "Карточка цели: здоровье, тип и снаряжение", false, 0.53, 0.56);
        this.cardShow = new Tween(300L, 0.0F, Tween.OVERSHOOT);
        this.cardHead = new Tween(300L, 0.0F, Tween.SOFT);
        this.cardName = new Tween(300L, 0.0F, Tween.SOFT);
        this.cardBar = new Tween(300L, 0.0F, Tween.SOFT);
        this.cardArmour = new Tween(300L, 0.0F, Tween.SOFT);
        this.cardAbsorb = new Tween(300L, 0.0F, Tween.OVERSHOOT);
        this.cardBarShown = 1.0F;
        this.cardBarTrail = 1.0F;
        INSTANCE = this;
    }

    public static LivingEntity current() {
        return INSTANCE == null ? null : INSTANCE.shownTarget;
    }

    public static float presence() {
        return INSTANCE == null ? 0.0F : INSTANCE.fade;
    }

    public static void update() {
        if (INSTANCE != null) {
            INSTANCE.shown();
        }
    }

    public static void hit(Entity e) {
        if (e instanceof LivingEntity l) {
            lastHit = l;
            lastHitAt = System.currentTimeMillis();
        }
    }

    private LivingEntity target() {
        Minecraft mc = Minecraft.getInstance();
        Entity var3 = mc.crosshairPickEntity;
        if (var3 instanceof LivingEntity l) {
            if (l.isAlive()) {
                return l;
            }
        }

        return lastHit != null
                        && System.currentTimeMillis() - lastHitAt < 4000L
                        && mc.player != null
                        && lastHit.distanceTo(mc.player) < 24.0F
                        && lastHit.isAlive()
                ? lastHit
                : null;
    }

    public boolean shown() {
        long now = System.nanoTime();
        this.dt = Math.min(0.1F, (float) (now - this.last) / 1.0E9F);
        this.last = now;
        LivingEntity t = this.target();
        if (t != null && t != this.shownTarget) {
            if (this.fade > 0.3F) {
                this.swapAt = System.currentTimeMillis();
            }

            this.shownTarget = t;
            this.lastHp = -1.0F;
            this.diedAt = 0L;
            Health.Reading first = Health.of(t);
            this.hpShown = this.hpGhost = first.fraction();
            this.hpNumber = first.total();
            this.pops.clear();
        }

        if (this.shownTarget != null && !this.shownTarget.isAlive() && this.diedAt == 0L) {
            this.diedAt = System.currentTimeMillis();
        }

        this.showing = t != null;
        this.fade += ((float) (this.showing ? 1 : 0) - this.fade)
                * (1.0F - (float) Math.exp((double) (-this.dt * (float) (this.showing ? 10 : 6))));
        this.flash *= (float) Math.exp((double) (-this.dt * 6.0F));
        if (this.style.is("Карточка") || this.style.is("Панель")) {
            return this.cardShown(this.showing) && this.shownTarget != null;
        } else {
            return this.fade > 0.02F && this.shownTarget != null;
        }
    }

    private static int hpColour(float frac) {
        return frac > 0.5F ? Px.mix(-340971, -11870592, (frac - 0.5F) * 2.0F) : Px.mix(-770210, -340971, frac * 2.0F);
    }

    private static String one(float v) {
        return String.format(RU, "%.1f", v);
    }

    private static String num(float v) {
        return Math.abs(v - (float) Math.round(v)) < 0.05F ? String.valueOf(Math.round(v)) : one(v);
    }

    private static List<ItemStack> equipment(LivingEntity t) {
        List<ItemStack> out = new ArrayList<>();

        for (EquipmentSlot s : new EquipmentSlot[] {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        }) {
            ItemStack it = t.getItemBySlot(s);
            if (!it.isEmpty()) {
                out.add(it);
            }
        }

        return out;
    }

    private static int faded() {
        return Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, Px.fade))) << 24 | 0xFFFFFF;
    }

    private static void item(GuiGraphics g, ItemStack it, int x, int y, int size) {
        float k = Px.fade;
        if (!(k < 0.08F)) {
            int s = Math.round((float) size * (0.55F + 0.45F * Math.min(1.0F, k)));
            HudElements.item(g, it, x + (size - s) / 2, y + (size - s) / 2, s);
        }
    }

    private void card(GuiGraphics g, Theme.Palette p, int x, int y, int cw, int ch, int r) {
        if (this.background.on()) {
            Px.shadow(g, x, y + this.S(4.0), cw, ch, this.S(18.0), Px.a(0, p.light() ? 40 : 125));
            Px.round(g, x, y, cw, ch, r, p.bg() & 0xFFFFFF | (Theme.style == 1 ? 0xF8000000 : 0xF0000000));
            Px.outline(g, x, y, cw, ch, r, Px.mix(p.border(), Px.a(16007006, 170), this.flash * 0.8F));
        }
    }

    private int c(double v) {
        return this.S(v * 2.2);
    }

    private void compact(GuiGraphics g, LivingEntity t, boolean editing) {
        Theme.Palette p = Theme.p();
        this.w = this.c(118.5);
        this.h = this.c(36.0);
        float old = Px.fade;
        Px.fade = old * (editing ? 1.0F : this.fade);
        g.pose().pushMatrix();
        float vis = editing ? 1.0F : Math.min(1.0F, this.fade);
        float ease = Anim.easeOutCubic(vis);
        g.pose().translate(0.0F, (float) Math.round((float) this.S(6.0) * (1.0F - ease)));

        try {
            this.card(g, p, 0, 0, this.w, this.h, this.c(8.0));
            int fx = this.c(5.0);
            int fy = this.c(5.0);
            int face = this.c(26.0);
            Px.glow(
                    g,
                    fx - this.c(6.0),
                    fy - this.c(6.0),
                    face + this.c(12.0),
                    face + this.c(12.0),
                    Px.a(Theme.accent(), 26));
            Px.shadow(g, fx, fy, face, face, this.c(5.0), 671088640);
            if (t instanceof ClientAvatarEntity av) {
                PlayerFaceRenderer.draw(g, av.getSkin(), fx, fy, face, faded());
            } else {
                MobHeads.draw(g, t.getType(), fx, fy, face, faded());
            }

            if (this.hurtTint.on() && t.hurtTime > 0) {
                float k = 1.0F - Math.abs((float) t.hurtTime / 5.0F - 1.0F);
                Px.round(g, fx, fy, face, face, this.c(3.0), Px.a(16007006, Math.round(140.0F * k)));
            }

            ItemStack using = t.isUsingItem() ? t.getUseItem() : ItemStack.EMPTY;
            if (!using.isEmpty()) {
                Px.round(g, fx, fy, face, face, this.c(3.0), 0x99000000);
                int is = this.c(12.0);
                HudElements.item(g, using, fx + (face - is) / 2, fy + this.c(3.0), is);
                float left = Math.max(0.0F, (float) (using.getUseDuration(t) - t.getTicksUsingItem()) / 20.0F);
                Locale var10001 = Locale.ROOT;
                Px.textCenter(
                        g,
                        String.format(var10001, "%.1f", left) + Lang.tr("с"),
                        fx + face / 2,
                        fy + this.c(16.0),
                        Px.Weight.SEMIBOLD,
                        this.c(5.0),
                        -1);
            }

            int tx = this.c(35.0);
            Px.text(
                    g,
                    Px.ellipsize(kind(t), Px.Weight.MEDIUM, this.c(5.0), this.c(55.0)),
                    tx,
                    this.c(6.25),
                    Px.Weight.MEDIUM,
                    this.c(5.0),
                    Theme.accent2() | 0xFF000000);
            String name = t.getName().getString();
            Px.text(
                    g,
                    Px.ellipsize(name, Px.Weight.SEMIBOLD, this.c(7.5), this.c(52.0)),
                    tx,
                    this.c(12.25),
                    Px.Weight.SEMIBOLD,
                    this.c(7.5),
                    p.text());
            ItemStack[] gearRow = new ItemStack[] {
                t.getItemBySlot(EquipmentSlot.HEAD),
                t.getItemBySlot(EquipmentSlot.CHEST),
                t.getItemBySlot(EquipmentSlot.LEGS),
                t.getItemBySlot(EquipmentSlot.FEET),
                t.getMainHandItem(),
                t.getOffhandItem()
            };
            int gx = tx;
            int gy = this.c(23.25);
            int gs = this.c(6.0);

            for (int i = 0; i < gearRow.length; ++i) {
                if (!gearRow[i].isEmpty()) {
                    HudElements.item(g, gearRow[i], gx, gy, gs);
                } else if (i < 4) {
                    Px.round(g, gx, gy, gs, gs, this.c(1.5), p.light() ? 335544320 : 352321535);
                }

                gx += this.c(8.5);
            }

            float rcx = (float) this.c(101.0);
            float rcy = (float) this.c(18.5);
            float r = (float) this.c(10.0);
            int thick = Math.max(2, this.c(2.2));
            int track = p.light() ? 436207616 : 520093695;
            Px.arc(g, rcx, rcy, r, thick, 0.0, (Math.PI * 2D), track, track);
            boolean byHealth = this.barStyle.is("Здоровье");
            int ringA = byHealth ? hpColour(this.hpShown) : Theme.accent() | 0xFF000000;
            int ringB = byHealth ? Px.mix(hpColour(this.hpShown), -1, 0.25F) : Theme.accent2() | 0xFF000000;
            if (this.hpGhost > this.hpShown + 0.002F) {
                Px.arc(
                        g,
                        rcx,
                        rcy,
                        r,
                        thick,
                        (-Math.PI / 2D),
                        (Math.PI * 2D) * (double) this.hpGhost,
                        0x88F87171,
                        0x88F87171);
            }

            if (this.hpShown > 0.002F) {
                Px.arc(g, rcx, rcy, r, thick, (-Math.PI / 2D), (Math.PI * 2D) * (double) this.hpShown, ringA, ringB);
            }

            String hpText = this.hpKnown ? String.valueOf(Math.round(this.hpNumber)) : "?";
            Px.textCenter(
                    g,
                    hpText,
                    Math.round(rcx),
                    Math.round(rcy - (float) this.c(3.25)),
                    Px.Weight.BOLD,
                    this.c(6.5),
                    p.text());
        } finally {
            g.pose().popMatrix();
            Px.fade = old;
        }
    }

    // ---- the panel: the HUD kit's own target card

    private static final float SLAB_W = 112.0F;
    private static final float SLAB_H = 38.0F;
    private static final float SLAB_TEXT = 38.0F;
    private static final int GOLD = 0xFFFFDC51;
    private final List<float[]> sparks = new ArrayList<>();
    private float slabHp = 1.0F;
    private float slabTrail = 1.0F;
    private long slabTrailHold;
    private float slabAbsorb;
    private int slabHurt;
    private long slabLast = System.nanoTime();
    private LivingEntity slabTarget;

    /**
     * A 112 x 38 panel: the head 26 units square with soft corners, the name (sliding to and fro when it is too long),
     * the health as a heart and a number with "hp" in the accent, a bar in our two accents with a pale trail where the
     * health was a moment ago and the absorption in gold from its right end, the armour on small tiles under the
     * panel, and a burst of sparks from the head on every hit.
     */
    private void slab(GuiGraphics g, LivingEntity t, boolean editing, Health.Reading health) {
        long nanos = System.nanoTime();
        float dt = Math.min(0.1F, (nanos - this.slabLast) / 1.0E9F);
        this.slabLast = nanos;
        float a = editing ? 1.0F : this.cardShow.get();
        List<ItemStack> armour = new ArrayList<>(4);
        for (EquipmentSlot s :
                new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack it = t.getItemBySlot(s);
            if (!it.isEmpty()) armour.add(it);
        }
        this.w = Math.round(this.u(SLAB_W));
        this.h = Math.round(this.u(SLAB_H + (armour.isEmpty() ? 0.0F : 15.0F)));
        float old = Px.fade;
        Px.fade = old * Math.max(0.0F, Math.min(1.0F, a));
        g.pose().pushMatrix();
        float cx = this.u(SLAB_W / 2.0F), cy = this.u(SLAB_H / 2.0F);
        g.pose().translate(cx, cy);
        g.pose().scale(Math.max(0.0F, a), Math.max(0.0F, a));
        g.pose().translate(-cx, -cy);
        try {
            if (t != this.slabTarget) {
                this.slabTarget = t;
                this.slabHp = this.slabTrail = health.fraction();
                this.sparks.clear();
            }
            this.panel(g, 0.0F, 0.0F, SLAB_W, SLAB_H, RADIUS);
            int fx = Math.round(this.u(6.0)), fy = Math.round(this.u(6.0)), fs = Math.round(this.u(26.0));
            if (t instanceof ClientAvatarEntity av) {
                PlayerFaceRenderer.draw(g, av.getSkin(), fx, fy, fs, faded());
            } else {
                MobHeads.draw(g, t.getType(), fx, fy, fs, faded());
            }
            if (t.hurtTime > 0) {
                float k = (float) t.hurtTime / 10.0F;
                Px.round(g, fx, fy, fs, fs, Math.round(this.u(6.0)), Px.a(0xFF4B4B, Math.round(120.0F * k)));
            }
            corners(g, fx, fy, fs, Math.round(this.u(6.0)), panelFill());
            // the health: a heart, the number and "hp" in the accent
            float total = health.hp() + health.absorption();
            String hp = this.hpKnown ? num(Math.round(total * 10.0F) / 10.0F) : "?";
            int accent = accentInk();
            float right = SLAB_W - 6.0F;
            float hw = tWidth(FACE_SEMI, hp, 6.3F), uw = tWidth(FACE_SEMI, "hp", 6.3F);
            float hx = right - uw - hw;
            this.cText(g, FACE_SEMI, hp, hx, 12.5F, 6.3F, Px.am(PANEL_TEXT, 0.7F));
            this.cText(g, FACE_SEMI, "hp", hx + hw, 12.5F, 6.3F, accent);
            this.icon(g, "heart", hx - 4.5F, 12.5F, 6.0F, accent);
            this.slabName(
                    g, t.isInvisible() ? Lang.tr("Невидимка") : t.getName().getString(), hx - 9.0F - SLAB_TEXT);
            // the bar, with the trail that lingers a moment after a hit
            float k = 1.0F - (float) Math.exp(-dt * 9.0F);
            float frac = health.fraction();
            this.slabHp += (frac - this.slabHp) * k;
            long now = System.currentTimeMillis();
            if (frac < this.slabTrail - 0.002F && this.slabTrailHold == 0L) this.slabTrailHold = now + 400L;
            if (now >= this.slabTrailHold) {
                this.slabTrail += (this.slabHp - this.slabTrail) * (1.0F - (float) Math.exp(-dt * 4.0F));
                if (Math.abs(this.slabTrail - this.slabHp) < 0.002F) this.slabTrailHold = 0L;
            }
            if (this.slabTrail < this.slabHp) this.slabTrail = this.slabHp;
            this.slabAbsorb += (Math.min(1.0F, health.absorption() / 20.0F) - this.slabAbsorb) * k;
            float bx = SLAB_TEXT, bw = right - bx, by = 24.5F, bh = 4.0F;
            this.rRound(g, bx, by, bw, bh, bh / 2.0F, Px.am(panelDark(), 0.85F));
            if (this.slabTrail > this.slabHp + 0.002F) {
                this.rRound(g, bx, by, bw * this.slabTrail, bh, bh / 2.0F, 0x50FFFFFF);
            }
            if (this.slabHp > 0.004F) {
                boolean byHealth = this.barStyle.is("Здоровье");
                int c1 = byHealth ? hpColour(this.slabHp) : accent;
                int c2 = byHealth ? Px.mix(c1, 0xFFFFFFFF, 0.2F) : accentDeep();
                this.rRoundH(g, bx, by, Math.max(bh, bw * this.slabHp), bh, bh / 2.0F, c1, c2);
            }
            if (this.slabAbsorb > 0.004F) {
                float aw = bw * this.slabAbsorb;
                this.rRound(g, bx + bw - aw, by, aw, bh, bh / 2.0F, GOLD);
            }
            this.glare(g, 0.0F, 0.0F, SLAB_W, SLAB_H, RADIUS);
            // the armour, on tiles under the panel
            if (!armour.isEmpty()) {
                float tile = 13.0F, gap = 2.0F;
                float x = SLAB_W / 2.0F - (armour.size() * tile + (armour.size() - 1) * gap) / 2.0F;
                for (ItemStack it : armour) {
                    this.rRound(g, x, SLAB_H + 2.0F, tile, tile, 4.0F, panelFill());
                    this.wItem(g, it, x + 2.0F, SLAB_H + 4.0F, 9.0F);
                    x += tile + gap;
                }
            }
            this.sparks(g, t, dt);
        } finally {
            g.pose().popMatrix();
            Px.fade = old;
        }
    }

    /** The name, sliding to its end and back when it does not fit its room, fading at the edges. */
    private void slabName(GuiGraphics g, String name, float room) {
        float size = 7.0F, cy = 12.5F;
        float width = tWidth(FACE, name, size);
        if (width <= room) {
            this.cText(g, FACE, name, SLAB_TEXT, cy, size, PANEL_TEXT);
            return;
        }
        float over = width - room;
        float travel = (over + room * 0.36F) / 18.0F;
        float cycle = 1.5F + travel * 2.0F;
        float t = (System.currentTimeMillis() % Math.max(1L, (long) (cycle * 1000.0F))) / 1000.0F;
        float shift;
        if (t < 0.75F) shift = 0.0F;
        else if (t < 0.75F + travel) shift = over * Anim.easeOutCubic((t - 0.75F) / travel);
        else if (t < 1.5F + travel) shift = over;
        else shift = over * (1.0F - Anim.easeOutCubic((t - 1.5F - travel) / travel));
        int x0 = Math.round(this.u(SLAB_TEXT)), x1 = Math.round(this.u(SLAB_TEXT + room));
        g.enableScissor(x0, 0, x1, Math.round(this.u(SLAB_H)));
        this.cText(g, FACE, name, SLAB_TEXT - shift, cy, size, PANEL_TEXT);
        g.disableScissor();
    }

    /** Eight sparks from the middle of the head when a hit lands, drifting out and fading over a second. */
    private void sparks(GuiGraphics g, LivingEntity t, float dt) {
        if (t.hurtTime > this.slabHurt && t.hurtTime >= 8) {
            for (int i = 0; i < 8; i++) {
                double angle = Math.random() * Math.PI * 2.0;
                double speed = 18.0 + Math.random() * 36.0;
                this.sparks.add(new float[] {
                    19.0F, 19.0F, (float) (Math.cos(angle) * speed), (float) (Math.sin(angle) * speed), 1.0F, i / 7.0F
                });
            }
            if (this.sparks.size() > 96)
                this.sparks.subList(0, this.sparks.size() - 96).clear();
        }
        this.slabHurt = t.hurtTime;
        int a1 = accentInk(), a2 = Theme.accent2() | 0xFF000000;
        for (int i = this.sparks.size() - 1; i >= 0; i--) {
            float[] s = this.sparks.get(i);
            s[0] += s[2] * dt;
            s[1] += s[3] * dt;
            s[2] *= (float) Math.exp(-dt * 2.5F);
            s[3] *= (float) Math.exp(-dt * 2.5F);
            s[4] -= dt;
            if (s[4] <= 0.0F) {
                this.sparks.remove(i);
                continue;
            }
            float life = s[4];
            int c = Px.mix(a1, a2, s[5]);
            int d = Math.max(2, Math.round(this.u(3.0 * (0.4 + 0.6 * life))));
            Px.glow(
                    g,
                    Math.round(this.u(s[0])) - d * 2,
                    Math.round(this.u(s[1])) - d * 2,
                    d * 4,
                    d * 4,
                    Px.am(c, life * 0.55F));
            Px.circle(g, Math.round(this.u(s[0])), Math.round(this.u(s[1])), Math.max(1, d / 2), Px.am(c, life));
        }
    }

    private boolean cardShown(boolean showing) {
        this.cardShow.easing(showing ? Tween.OVERSHOOT : Tween.BACK_IN);
        return this.cardShow.to(showing) > 0.001F;
    }

    private static void fadeText(GuiGraphics g, String s, int x, int y, Px.Weight wt, int px, int c, int room) {
        if (Px.textWidth(s, wt, px) <= room) {
            Px.text(g, s, x, y, wt, px, c);
        } else {
            float keep = Px.fade;
            int cx = x;

            String ch;
            for (int i = 0; i < s.length(); cx += Px.textWidth(ch, wt, px)) {
                int cp = s.codePointAt(i);
                ch = new String(Character.toChars(cp));
                i += Character.charCount(cp);
                float f = (float) (cx - x) / (float) room;
                if (f >= 1.0F) {
                    break;
                }

                Px.fade = keep * (f < 0.7F ? 1.0F : 1.0F - (f - 0.7F) / 0.3F);
                Px.text(g, ch, cx, y, wt, px, c);
            }

            Px.fade = keep;
        }
    }

    private static void corners(GuiGraphics g, int x, int y, int size, int r, int c) {
        c = Px.am(c, Px.fade);
        if (c >>> 24 != 0 && r > 0) {
            RenderPipeline pipe = RenderPipelines.GUI_TEXTURED;
            g.blit(pipe, CORNERS, x, y, 0.0F, 0.0F, r, r, 64, 64, 128, 128, c);
            g.blit(pipe, CORNERS, x + size - r, y, 64.0F, 0.0F, r, r, 64, 64, 128, 128, c);
            g.blit(pipe, CORNERS, x, y + size - r, 0.0F, 64.0F, r, r, 64, 64, 128, 128, c);
            g.blit(pipe, CORNERS, x + size - r, y + size - r, 64.0F, 64.0F, r, r, 64, 64, 128, 128, c);
        }
    }

    private void card(GuiGraphics g, LivingEntity t, boolean editing, Health.Reading health, long now) {
        Theme.Palette p = Theme.p();
        this.w = this.U(96.0);
        this.h = this.U(30.0);
        int bg = p.light() ? p.bg() & 0xFFFFFF | 0xF2000000 : 0xF00E0E11;
        int text = p.text() | 0xFF000000;
        float a = editing ? 1.0F : this.cardShow.get();
        float old = Px.fade;
        Px.fade = old * Math.min(1.0F, a);
        g.pose().pushMatrix();
        float sc = 0.85F + Math.min(1.0F, a) * 0.15F;
        g.pose().translate((float) this.w / 2.0F, (float) this.h / 2.0F);
        g.pose().scale(sc, sc);
        g.pose().translate((float) (-this.w) / 2.0F, (float) (-this.h) / 2.0F);

        try {
            float head = this.cardHead.to(a >= 1.0F);
            float name = this.cardName.to(head >= 0.6F);
            float bar = this.cardBar.to(name >= 0.6F);
            float armourIn = this.cardArmour.to(bar >= 0.6F);
            float hp = health.hp();
            float target = Math.max(0.0F, Math.min(1.0F, hp / Math.max(1.0F, health.max())));
            if (this.cardTarget != t) {
                this.cardTarget = t;
                this.cardBarShown = this.cardBarTrail = target;
                this.cardLastHp = hp;
            }

            if (hp < this.cardLastHp - 0.05F) {
                this.flash = 1.0F;
            }

            this.cardLastHp = hp;
            if (target < this.cardBarShown - 0.002F && now >= this.cardTrailHold) {
                this.cardTrailHold = now + 90L;
            }

            this.cardBarShown += (target - this.cardBarShown)
                    * (1.0F - (float) Math.exp((double) (-this.dt * (float) (target < this.cardBarShown ? 12 : 7))));
            if (now >= this.cardTrailHold) {
                this.cardBarTrail +=
                        (this.cardBarShown - this.cardBarTrail) * (1.0F - (float) Math.exp((double) (-this.dt * 6.0F)));
            }

            if (this.cardBarTrail < this.cardBarShown) {
                this.cardBarTrail = this.cardBarShown;
            }

            float absorb = this.cardAbsorb.to(health.absorption() / 20.0F);
            if (!this.cardArmor.is("Нет") && a > 0.0F) {
                this.armour(g, t, armourIn, bg);
            }

            int R = this.U(7.0);
            if (this.background.on()) {
                Px.shadow(g, 0, this.U(2.0), this.w, this.h, this.U(9.0), Px.a(0, p.light() ? 35 : 110));
                Px.round(g, 0, 0, this.w, this.h, R, bg);
                Px.outline(g, 0, 0, this.w, this.h, R, p.light() ? Px.a(0, 22) : 352321535);
            }

            float keep = Px.fade;
            int face = this.U(20.0);
            int fx = this.U(5.0);
            int fy = (this.h - face) / 2;
            int r = this.U(5.0);
            Px.fade = keep * head;
            g.pose().pushMatrix();
            float fs0 = 0.8F + 0.2F * head;
            g.pose().translate((float) fx + (float) face / 2.0F, (float) fy + (float) face / 2.0F);
            g.pose().scale(fs0, fs0);
            g.pose().translate((float) (-fx) - (float) face / 2.0F, (float) (-fy) - (float) face / 2.0F);
            if (t instanceof ClientAvatarEntity av) {
                PlayerFaceRenderer.draw(g, av.getSkin(), fx, fy, face, faded());
            } else {
                g.blit(
                        RenderPipelines.GUI_TEXTURED,
                        p.light() ? WHO : WHO_DARK,
                        fx,
                        fy,
                        0.0F,
                        0.0F,
                        face,
                        face,
                        38,
                        38,
                        38,
                        38,
                        faded());
            }

            if (this.flash > 0.01F) {
                Px.rect(g, fx, fy, face, face, Px.a(-50358, Math.round(110.0F * this.flash)));
            }

            if (this.background.on()) {
                corners(g, fx, fy, face, r, bg);
            }

            g.pose().popMatrix();
            int tx = fx + face + this.U(6.0);
            int right = this.w - this.U(6.0);
            int nfs = this.U(6.5);
            int col = softHp(Math.max(0.0F, Math.min(1.0F, this.cardBarShown)));
            Px.fade = keep * name;
            fadeText(
                    g,
                    t.getName().getString(),
                    tx,
                    fy + this.U(1.0) + Math.round((float) this.U(2.0) * (1.0F - name)),
                    Px.Weight.SEMIBOLD,
                    nfs,
                    text,
                    right - tx);
            int bh = Math.max(3, this.U(3.0));
            int br = bh / 2;
            int bw = right - tx;
            int by = fy + face - bh - this.U(1.0);
            Px.fade = keep * bar;
            Px.round(g, tx, by, bw, bh, br, p.light() ? Px.a(0, 22) : 486539263);
            int fw = Math.round((float) bw * Math.max(0.0F, Math.min(1.0F, this.cardBarShown)));
            int tw = Math.round((float) bw * Math.max(0.0F, Math.min(1.0F, this.cardBarTrail)) * bar);
            if (tw > fw) {
                Px.round(g, tx, by, Math.max(bh, tw), bh, br, Px.a(Px.mix(col, -1, 0.55F), 110));
            }

            fw = Math.round((float) fw * bar);
            if (fw > 0) {
                Px.round(g, tx, by, Math.max(bh, fw), bh, br, col);
            }

            int aw = Math.round((float) bw * Math.max(0.0F, Math.min(1.0F, absorb)) * bar);
            if (aw > 0) {
                Px.round(
                        g,
                        tx,
                        by - Math.max(1, this.U(1.0)) - this.U(0.5),
                        Math.max(2, aw),
                        Math.max(1, this.U(1.0)),
                        1,
                        0xE6FFD36B);
            }

            Px.fade = keep;
        } finally {
            g.pose().popMatrix();
            Px.fade = old;
        }
    }

    private static int softHp(float k) {
        return k < 0.5F ? Px.mix(-1026721, -870325, k * 2.0F) : Px.mix(-870325, -10495605, (k - 0.5F) * 2.0F);
    }

    private void armour(GuiGraphics g, LivingEntity t, float in, int bg) {
        List<ItemStack> items = new ArrayList<>();

        for (EquipmentSlot s : new EquipmentSlot[] {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
        }) {
            ItemStack it = t.getItemBySlot(s);
            if (!it.isEmpty()) {
                items.add(it);
            }
        }

        if (!items.isEmpty() && !(in < 0.01F)) {
            int is = this.U(10.0);
            int gap = this.U(2.5);
            int pad = this.U(3.0);
            int n = items.size();
            int pw = pad * 2 + n * is + (n - 1) * gap;
            int ph = is + pad * 2;
            int x = (this.w - pw) / 2;
            int y = this.h + this.U(3.0) + Math.round((float) this.U(3.0) * (1.0F - in));
            float keep = Px.fade;
            Px.fade = keep * in;
            if (this.background.on()) {
                Px.round(g, x, y, pw, ph, this.U(5.0), bg);
                Px.outline(g, x, y, pw, ph, this.U(5.0), Theme.p().light() ? Px.a(0, 22) : 352321535);
            }

            for (int i = 0; i < n; ++i) {
                ItemStack it = (ItemStack) items.get(i);
                int ix = x + pad + i * (is + gap);
                int iy = y + pad;
                HudElements.item(g, it, ix, iy, is);
                if (it.isDamageableItem() && it.getMaxDamage() > 0) {
                    float left = 1.0F - (float) it.getDamageValue() / (float) it.getMaxDamage();
                    int lw = Math.max(1, Math.round((float) is * left));
                    int lh = Math.max(1, this.U(0.8));
                    g.nextStratum();
                    Px.rect(g, ix, iy + is + this.U(0.5), lw, lh, Px.a(softHp(left), 220));
                }
            }

            Px.fade = keep;
        }
    }

    private static String kind(LivingEntity t) {
        PlayerTeam team = t.getTeam();
        if (team != null) {
            String prefix =
                    team.getPlayerPrefix().getString().replaceAll("§.?", "").trim();
            if (prefix.chars().anyMatch(Character::isLetterOrDigit)) {
                return prefix;
            }
        }

        if (t instanceof Player) {
            return Lang.tr("Игрок");
        } else if (t instanceof AbstractVillager) {
            return Lang.tr("Житель");
        } else {
            return t instanceof Animal ? Lang.tr("Животное") : Lang.tr(t instanceof Mob ? "Моб" : "Существо");
        }
    }

    public void render(GuiGraphics g, boolean editing) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity t = (LivingEntity) (editing && this.shownTarget == null ? mc.player : this.shownTarget);
        Theme.Palette p = Theme.p();
        int pad = this.S(12.0);
        int face = this.S(42.0) / 2 * 2;
        int barH = Math.max(4, this.S(8.0) / 2 * 2);
        int cardH = pad + face + this.S(13.0) + barH + pad;
        this.w = this.S(252.0);
        List<ItemStack> items = t != null && this.gear.on() ? equipment(t) : List.of();
        int stripH = this.S(42.0);
        int stripGap = this.S(6.0);
        this.stripK += ((float) (items.isEmpty() ? 0 : 1) - this.stripK) * Math.min(1.0F, this.dt * 12.0F);
        this.h = cardH + Math.round((float) (stripGap + stripH) * this.stripK);
        if (t == null) {
            this.h = cardH;
            this.plate(g, this.w, this.h);
            Px.icon(g, "target", pad, this.h / 2 - this.S(8.0), this.S(16.0), p.dim());
            Px.text(
                    g,
                    Lang.tr("Таргет-HUD"),
                    pad + this.S(24.0),
                    this.h / 2 - this.S(7.0),
                    Px.Weight.SEMIBOLD,
                    this.S(13.0),
                    p.muted());
        } else {
            long now = System.currentTimeMillis();
            boolean dead = !t.isAlive() || t.isDeadOrDying();
            Health.Reading health = Health.of(t);
            this.hpKnown = health.known();
            float max = health.max();
            float hp = health.hp();
            float abs = health.absorption();
            if (this.lastHp >= 0.0F && hp < this.lastHp - 0.05F) {
                this.flash = 1.0F;
                this.ghostHold = now + 380L;
                if (this.popups.on()) {
                    List var10000 = this.pops;
                    String var10003 = this.lastHp - hp >= 10.0F
                            ? String.valueOf(Math.round(this.lastHp - hp))
                            : one(this.lastHp - hp);
                    var10000.add(new Pop("-" + var10003, now));
                }
            }

            this.lastHp = hp;
            float frac = health.fraction();
            this.hpShown += (frac - this.hpShown) * Math.min(1.0F, this.dt * 10.0F);
            if (now > this.ghostHold) {
                this.hpGhost += (this.hpShown - this.hpGhost) * Math.min(1.0F, this.dt * 3.5F);
            }

            if (this.hpGhost < this.hpShown) {
                this.hpGhost = this.hpShown;
            }

            this.hpNumber += (hp + abs - this.hpNumber) * Math.min(1.0F, this.dt * 12.0F);
            if (this.style.is("Панель")) {
                this.slab(g, t, editing, health);
            } else if (this.style.is("Компактный")) {
                this.compact(g, t, editing);
            } else if (this.style.is("Карточка")) {
                this.card(g, t, editing, health, now);
            } else {
                float a = editing ? 1.0F : this.fade;
                float old = Px.fade;
                Px.fade = old * a;
                g.pose().pushMatrix();
                float vis = editing ? 1.0F : Math.min(1.0F, this.fade);
                float ease = Anim.easeOutCubic(vis);
                float sc = 0.96F + 0.04F * ease;
                g.pose()
                        .translate(
                                (float) this.w / 2.0F,
                                (float) cardH / 2.0F + (float) Math.round((float) this.S(8.0) * (1.0F - ease)));
                g.pose().scale(sc, sc);
                g.pose().translate((float) (-this.w) / 2.0F, (float) (-cardH) / 2.0F);
                this.card(g, p, 0, 0, this.w, cardH, this.S(14.0));
                boolean themed = this.barStyle.is("Тема");
                int fillC = (themed ? Theme.accent() : hpColour(this.hpShown)) | 0xFF000000;
                int fillC2 = themed ? Theme.accent2() | 0xFF000000 : Px.mix(fillC, -1, 0.3F);
                Px.glow(
                        g,
                        pad - this.S(16.0),
                        pad - this.S(16.0),
                        face + this.S(32.0),
                        face + this.S(32.0),
                        Px.a(Theme.accent(), p.light() ? 22 : 30));
                if (t instanceof ClientAvatarEntity) {
                    ClientAvatarEntity av = (ClientAvatarEntity) t;
                    Px.round(
                            g,
                            pad,
                            pad,
                            face,
                            face,
                            this.S(11.0),
                            Px.mix(p.control(), Px.a(Theme.accent(), 90), 0.22F));
                    Px.outline(g, pad, pad, face, face, this.S(11.0), Px.a(Theme.accent(), p.light() ? 60 : 46));
                    int fs = Math.max(8, (face - this.S(10.0)) / 8 * 8);
                    int in = (face - fs) / 2;
                    PlayerFaceRenderer.draw(g, av.getSkin(), pad + in, pad + in, fs, faded());
                } else {
                    MobHeads.draw(g, t.getType(), pad, pad, face, faded());
                }

                if (t.hurtTime > 0) {
                    float k = 1.0F - Math.abs((float) t.hurtTime / 5.0F - 1.0F);
                    Px.round(g, pad, pad, face, face, this.S(11.0), Px.a(16007006, Math.round(110.0F * k)));
                }

                if (dead) {
                    Px.round(g, pad, pad, face, face, this.S(11.0), Integer.MIN_VALUE);
                }

                float sk = Math.min(1.0F, (float) (now - this.swapAt) / 260.0F);
                float swap = this.swapAt == 0L ? 1.0F : Anim.easeOutCubic(sk);
                float keepC = Px.fade;
                Px.fade = keepC * (0.2F + 0.8F * swap);
                g.pose().pushMatrix();
                g.pose().translate((float) Math.round((float) this.S(8.0) * (1.0F - swap)), 0.0F);
                int tx = pad + face + this.S(12.0);
                int right = this.w - pad;
                String kind = kind(t);
                int kfs = this.S(10.0);
                int chipH = this.S(18.0);
                int chipW = Math.min(this.S(90.0), Px.textWidth(kind, Px.Weight.SEMIBOLD, kfs) + this.S(14.0));
                int chipX = right - chipW;
                int chipY = pad + this.S(2.0);
                Px.round(g, chipX, chipY, chipW, chipH, chipH / 2, Px.a(Theme.accent(), p.light() ? 36 : 30));
                Px.textCenter(
                        g,
                        Px.ellipsize(kind, Px.Weight.SEMIBOLD, kfs, chipW - this.S(12.0)),
                        chipX + chipW / 2,
                        chipY + (chipH - kfs) / 2,
                        Px.Weight.SEMIBOLD,
                        kfs,
                        Px.mix(Theme.accent() | 0xFF000000, p.light() ? 0xFF000000 : -1, 0.35F));
                String name = t.getName().getString();
                int nfs = this.S(15.0);
                Px.text(
                        g,
                        Px.ellipsize(name, Px.Weight.BOLD, nfs, chipX - this.S(8.0) - tx),
                        tx,
                        chipY + (chipH - nfs) / 2,
                        Px.Weight.BOLD,
                        nfs,
                        dead ? p.muted() : p.text());
                int hfs = this.S(12.0);
                int hy = pad + this.S(26.0);
                int heart = this.S(12.0);
                Px.icon(g, "heart", tx, hy, heart, fillC);
                int hx = tx + heart + this.S(6.0);
                String hpText = this.hpKnown ? num(Math.max(0.0F, this.hpNumber - abs)) : "?";
                int hpCol = Px.mix(p.text(), -495247, this.flash * 0.8F);
                Px.text(g, hpText, hx, hy, Px.Weight.SEMIBOLD, hfs, hpCol);
                hx += Px.textWidth(hpText, Px.Weight.SEMIBOLD, hfs);
                if (this.hpKnown) {
                    String of = " / " + num(max);
                    Px.text(g, of, hx, hy, Px.Weight.MEDIUM, hfs, p.dim());
                    hx += Px.textWidth(of, Px.Weight.MEDIUM, hfs);
                }

                int hpEnd = hx;
                if (abs > 0.0F) {
                    String gold = "+" + num(abs);
                    Px.text(g, gold, hx + this.S(6.0), hy, Px.Weight.SEMIBOLD, hfs, -208051);
                    hpEnd = hx + this.S(6.0) + Px.textWidth(gold, Px.Weight.SEMIBOLD, hfs);
                }

                if (this.hpKnown) {
                    Px.textRight(
                            g, Math.round(this.hpShown * 100.0F) + "%", right, hy, Px.Weight.MEDIUM, hfs, p.muted());
                }

                g.pose().popMatrix();
                Px.fade = keepC;
                int by = pad + face + this.S(13.0);
                int bw = this.w - pad * 2;
                int br = barH / 2;
                Px.round(g, pad, by, bw, barH, br, Px.a(p.text(), p.light() ? 20 : 14));
                if (this.hpGhost > this.hpShown + 0.002F) {
                    Px.round(
                            g,
                            pad,
                            by,
                            Math.max(barH, Math.round((float) bw * this.hpGhost)),
                            barH,
                            br,
                            Px.a(16281969, 120));
                }

                if (this.hpShown > 0.002F) {
                    int fw = Math.max(barH, Math.round((float) bw * this.hpShown));
                    Px.glow(
                            g,
                            pad - this.S(10.0),
                            by - this.S(8.0),
                            fw + this.S(20.0),
                            barH + this.S(16.0),
                            Px.a(fillC, p.light() ? 30 : 46));
                    Px.pill(g, pad, by, fw, barH, fillC, fillC2);
                    int sheen = Math.max(1, barH / 3);
                    if (fw > barH) {
                        Px.round(g, pad + br, by + 1, fw - barH, sheen, sheen / 2, 754974719);
                    }
                }

                if (abs > 0.0F) {
                    Px.round(
                            g,
                            pad,
                            by,
                            Math.max(barH, Math.round((float) bw * Math.min(1.0F, abs / Math.max(1.0F, max)))),
                            barH,
                            br,
                            Px.a(16569165, 150));
                }

                this.pops.removeIf((ppx) -> now - ppx.born > 800L || dead);

                for (Pop pp : this.pops) {
                    float k = (float) (now - pp.born) / 800.0F;
                    float oldF = Px.fade;
                    Px.fade = oldF * (1.0F - k * k);
                    Px.text(
                            g,
                            pp.text,
                            hpEnd + this.S(8.0),
                            hy - Math.round((float) this.S(12.0) * Anim.easeOutCubic(k)),
                            Px.Weight.BOLD,
                            this.S(11.0),
                            -495247);
                    Px.fade = oldF;
                }

                if (!items.isEmpty() && this.stripK > 0.01F) {
                    float stripOld = Px.fade;
                    Px.fade = stripOld * this.stripK;
                    int cell = this.S(32.0);
                    int sw = this.S(8.0) + items.size() * cell;
                    int sx = (this.w - sw) / 2;
                    int sy = cardH + stripGap - Math.round((float) this.S(8.0) * (1.0F - this.stripK));
                    this.card(g, p, sx, sy, sw, stripH, this.S(12.0));

                    for (int i = 0; i < items.size(); ++i) {
                        ItemStack it = (ItemStack) items.get(i);
                        int cx = sx + this.S(4.0) + i * cell;
                        Px.round(
                                g,
                                cx + this.S(2.0),
                                sy + this.S(4.0),
                                cell - this.S(4.0),
                                stripH - this.S(8.0),
                                this.S(8.0),
                                p.light() ? 201326592 : 218103807);
                        item(g, it, cx + (cell - this.S(19.0)) / 2, sy + this.S(7.0), this.S(19.0));
                        int lw = this.S(16.0);
                        int lh = Math.max(2, this.S(2.5));
                        int lx = cx + (cell - lw) / 2;
                        int ly = sy + stripH - this.S(10.0);
                        if (it.isDamageableItem()) {
                            float f = 1.0F - (float) it.getDamageValue() / (float) Math.max(1, it.getMaxDamage());
                            Px.round(g, lx, ly, lw, lh, lh / 2, Px.a(p.text(), 18));
                            Px.round(g, lx, ly, Math.max(lh, Math.round((float) lw * f)), lh, lh / 2, hpColour(f));
                        } else if (it.getCount() > 1) {
                            String n = Integer.toString(it.getCount());
                            Px.textCenter(
                                    g, n, cx + cell / 2, ly - this.S(4.0), Px.Weight.SEMIBOLD, this.S(9.0), p.muted());
                        } else {
                            Px.round(g, lx + lw / 4, ly, lw / 2, lh, lh / 2, Px.a(Theme.accent(), 110));
                        }
                    }

                    Px.fade = stripOld;
                }

                g.pose().popMatrix();
                Px.fade = old;
            }
        }
    }

    private static record Pop(String text, long born) {}
}
