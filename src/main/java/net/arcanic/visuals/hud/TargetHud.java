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
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Target HUD module
 *
 * Shows the target's face, name, health, weapon and armour.
 */
public final class TargetHud extends HudModule {
    final Setting.Mode style = add(new Setting.Mode("style", "Стиль", "Классический", "Классический", "Компактный"));
    final Setting.Bool hurtTint =
            add(new Setting.Bool("hurt_tint", "Краснеет от урона", true)).showIf(() -> style.is("Компактный"));
    final Setting.Mode barStyle = add(new Setting.Mode("bar2", "Цвет полоски", "Тема", "Тема", "Здоровье"));
    final Setting.Bool gear = add(new Setting.Bool("gear2", "Снаряжение", true));
    final Setting.Bool popups = add(new Setting.Bool("popups", "Урон у цифры", true));

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static LivingEntity lastHit;
    private static long lastHitAt;
    private static TargetHud INSTANCE;

    private LivingEntity shownTarget;
    private float fade, flash, hpShown = 1, hpGhost = 1, hpNumber;
    private boolean hpKnown = true;
    private float lastHp = -1;
    private long last = System.nanoTime(), ghostHold, diedAt, swapAt;
    private float stripK;
    private boolean showing;
    private float dt = 1 / 60f;

    private record Pop(String text, long born) {}

    private final List<Pop> pops = new ArrayList<>();

    public TargetHud() {
        super("targethud", "Таргет-HUD", "Карточка цели: здоровье, тип и снаряжение", false, .53, .56);
        INSTANCE = this;
    }

    public static LivingEntity current() {
        return INSTANCE == null ? null : INSTANCE.shownTarget;
    }

    public static float presence() {
        return INSTANCE == null ? 0 : INSTANCE.fade;
    }

    public static void update() {
        if (INSTANCE != null) INSTANCE.shown();
    }

    public static void hit(Entity e) {
        if (e instanceof LivingEntity l) {
            lastHit = l;
            lastHitAt = System.currentTimeMillis();
        }
    }

    private LivingEntity target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.crosshairPickEntity instanceof LivingEntity l && l.isAlive()) return l;
        if (lastHit != null
                && System.currentTimeMillis() - lastHitAt < 4000
                && mc.player != null
                && lastHit.distanceTo(mc.player) < 24) {
            if (lastHit.isAlive()) return lastHit;
        }
        return null;
    }

    @Override
    public boolean shown() {
        long now = System.nanoTime();
        dt = Math.min(.1f, (now - last) / 1e9f);
        last = now;
        LivingEntity t = target();
        if (t != null && t != shownTarget) {
            if (fade > .3f)
                swapAt = System.currentTimeMillis(); // switching targets while visible: the content slides in anew
            shownTarget = t;
            lastHp = -1;
            diedAt = 0;
            Health.Reading first = Health.of(t);
            hpShown = hpGhost = first.fraction();
            hpNumber = first.total();
            pops.clear();
        }
        if (shownTarget != null && !shownTarget.isAlive() && diedAt == 0) diedAt = System.currentTimeMillis();
        showing = t != null;
        fade += ((showing ? 1 : 0) - fade) * (1 - (float) Math.exp(-dt * (showing ? 11 : 5)));
        flash *= (float) Math.exp(-dt * 8);
        return fade > .02f && shownTarget != null;
    }

    private static boolean dead(LivingEntity t) {
        return t != null && (!t.isAlive() || t.isDeadOrDying());
    }

    private static int hpColour(float frac) {
        return frac > .5f ? Px.mix(0xFFFACC15, 0xFF4ADE80, (frac - .5f) * 2) : Px.mix(0xFFF43F5E, 0xFFFACC15, frac * 2);
    }

    private static String one(float v) {
        return String.format(RU, "%.1f", v);
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
            if (!it.isEmpty()) out.add(it);
        }
        return out;
    }

    private static int faded() {
        return (Math.round(255 * Math.max(0, Math.min(1, Px.fade))) << 24) | 0xFFFFFF;
    }

    private static void item(GuiGraphics g, ItemStack it, int x, int y, int size) {
        float k = Px.fade;
        if (k < .08f) return;
        int s = Math.round(size * (.55f + .45f * Math.min(1, k)));
        HudElements.item(g, it, x + (size - s) / 2, y + (size - s) / 2, s);
    }

    /** Compact layout units: the card is 118.5 x 36 of them. */
    private int c(double v) {
        return S(v * 2.2);
    }

    /** The compact card: the face, the name, the gear in a row and the health as a ring. */
    private void compact(GuiGraphics g, LivingEntity t, boolean editing) {
        Theme.Palette p = Theme.p();
        w = c(118.5);
        h = c(36);
        float old = Px.fade;
        Px.fade = old * (editing ? 1 : fade);
        try {
            if (background.on()) {
                Px.shadow(g, 0, S(3), w, h, S(14), Px.a(0, p.light() ? 45 : 120));
                Px.round(g, 0, 0, w, h, c(8), (p.bg() & 0xFFFFFF) | (Theme.style == 1 ? 0xF8000000 : 0xEE000000));
                Px.outline(g, 0, 0, w, h, c(8), Px.mix(p.border(), Px.a(0xF43F5E, 190), flash * .85f));
            }
            int fx = c(5), fy = c(5), face = c(26);
            Px.shadow(g, fx, fy, face, face, c(5), 0x28000000);
            if (t instanceof ClientAvatarEntity av) PlayerFaceRenderer.draw(g, av.getSkin(), fx, fy, face, faded());
            else MobHeads.draw(g, t.getType(), fx, fy, face, faded());
            if (hurtTint.on() && t.hurtTime > 0) {
                float k = 1 - Math.abs(t.hurtTime / 5f - 1); // up and back down over the hurt time
                Px.round(g, fx, fy, face, face, c(3), Px.a(0xF43F5E, Math.round(150 * k)));
            }
            ItemStack using = t.isUsingItem() ? t.getUseItem() : ItemStack.EMPTY;
            if (!using.isEmpty()) {
                Px.round(g, fx, fy, face, face, c(3), 0x99000000);
                int is = c(12);
                HudElements.item(g, using, fx + (face - is) / 2, fy + c(3), is);
                float left = Math.max(0, (using.getUseDuration(t) - t.getTicksUsingItem()) / 20f);
                Px.textCenter(
                        g,
                        String.format(Locale.ROOT, "%.1f", left) + Lang.tr("с"),
                        fx + face / 2,
                        fy + c(16),
                        Weight.SEMIBOLD,
                        c(5),
                        0xFFFFFFFF);
            }
            int tx = c(35);
            Px.text(
                    g,
                    Px.ellipsize(kind(t), Weight.MEDIUM, c(5), c(60)),
                    tx,
                    c(6.25),
                    Weight.MEDIUM,
                    c(5),
                    Theme.accent2() | 0xFF000000);
            String name = t.getName().getString();
            Px.text(
                    g,
                    Px.ellipsize(name, Weight.SEMIBOLD, c(7.5), c(55.5)),
                    tx,
                    c(12.25),
                    Weight.SEMIBOLD,
                    c(7.5),
                    p.text());
            ItemStack[] gearRow = {
                t.getItemBySlot(EquipmentSlot.HEAD), t.getItemBySlot(EquipmentSlot.CHEST),
                t.getItemBySlot(EquipmentSlot.LEGS), t.getItemBySlot(EquipmentSlot.FEET),
                t.getMainHandItem(), t.getOffhandItem()
            };
            int gx = tx, gy = c(23.25), gs = c(6);
            for (int i = 0; i < gearRow.length; i++) {
                if (!gearRow[i].isEmpty()) HudElements.item(g, gearRow[i], gx, gy, gs);
                else if (i < 4) Px.round(g, gx, gy, gs, gs, c(1.5), 0x1EFFFFFF);
                gx += c(8.5);
            }
            // health: a ring that empties as it drops, the number inside
            float rcx = c(101), rcy = c(18.5), r = c(10);
            int thick = Math.max(2, c(2.5));
            Px.arc(g, rcx, rcy, r, thick, 0, Math.PI * 2, 0x26FFFFFF, 0x26FFFFFF);
            int ringC = barStyle.is("Здоровье") ? hpColour(hpShown) : Theme.accent2() | 0xFF000000;
            if (hpShown > 0) Px.arc(g, rcx, rcy, r, thick, -Math.PI / 2, Math.PI * 2 * hpShown, ringC, ringC);
            String hpText = hpKnown ? String.valueOf(Math.round(hpNumber)) : "?";
            Px.textCenter(g, hpText, Math.round(rcx), Math.round(rcy - c(3.25)), Weight.BOLD, c(6.5), p.text());
        } finally {
            Px.fade = old;
        }
    }

    /** The team prefix if the server gives one, otherwise what kind of target it is. */
    private static String kind(LivingEntity t) {
        var team = t.getTeam();
        if (team != null) {
            String prefix =
                    team.getPlayerPrefix().getString().replaceAll("§.?", "").trim();
            if (prefix.chars().anyMatch(Character::isLetterOrDigit)) return prefix;
        }
        if (t instanceof Player) return Lang.tr("Игрок");
        if (t instanceof AbstractVillager) return Lang.tr("Житель");
        if (t instanceof Animal) return Lang.tr("Животное");
        return Lang.tr(t instanceof Mob ? "Моб" : "Существо");
    }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity t = editing && shownTarget == null ? mc.player : shownTarget;
        Theme.Palette p = Theme.p();
        int pad = S(12), face = S(42) / 2 * 2, barH = S(12);
        int cardH = pad + face + S(12) + barH + pad;
        w = S(252);
        List<ItemStack> items = t == null || !gear.on() ? List.of() : equipment(t);
        int stripH = S(40), stripGap = S(6);
        // the gear strip grows and shrinks instead of popping
        stripK += ((items.isEmpty() ? 0 : 1) - stripK) * Math.min(1, dt * 12);
        h = cardH + Math.round((stripGap + stripH) * stripK);
        if (t == null) {
            h = cardH;
            plate(g, w, h);
            Px.icon(g, "target", pad, h / 2 - S(8), S(16), p.dim());
            Px.text(g, Lang.tr("Таргет-HUD"), pad + S(24), h / 2 - S(7), Weight.SEMIBOLD, S(13), p.muted());
            return;
        }
        long now = System.currentTimeMillis();
        boolean dead = !t.isAlive() || t.isDeadOrDying();
        Health.Reading health = Health.of(t);
        hpKnown = health.known();
        float max = health.max(), hp = health.hp(), abs = health.absorption();
        if (lastHp >= 0 && hp < lastHp - .05f) {
            flash = 1;
            ghostHold = now + 320;
            if (popups.on())
                pops.add(new Pop(
                        "-" + (lastHp - hp >= 10 ? String.valueOf(Math.round(lastHp - hp)) : one(lastHp - hp)), now));
        }
        lastHp = hp;
        float frac = health.fraction();
        hpShown += (frac - hpShown) * Math.min(1, dt * 14);
        if (now > ghostHold) hpGhost += (hpShown - hpGhost) * Math.min(1, dt * 4);
        if (hpGhost < hpShown) hpGhost = hpShown;
        hpNumber += (hp + abs - hpNumber) * Math.min(1, dt * 16);
        if (style.is("Компактный")) {
            compact(g, t, editing);
            return;
        }

        float a = editing ? 1 : fade;
        float old = Px.fade;
        Px.fade = old * a;
        g.pose().pushMatrix();
        // in: a soft spring up to full size; out: it settles down and shrinks a little while fading
        float vis = editing ? 1 : Math.min(1, fade);
        float ease = showing || editing ? Anim.easeOutBack(vis) : Anim.easeOutCubic(vis);
        float sc = .93f + .07f * ease;
        g.pose().translate(w / 2f, cardH / 2f + Math.round(S(6) * (1 - ease)));
        g.pose().scale(sc, sc);
        g.pose().translate(-w / 2f, -cardH / 2f);

        // card
        if (background.on()) {
            Px.shadow(g, 0, S(3), w, cardH, S(16), Px.a(0, p.light() ? 45 : 130));
            Px.round(g, 0, 0, w, cardH, S(12), (p.bg() & 0xFFFFFF) | (Theme.style == 1 ? 0xF8000000 : 0xEE000000));
            Px.outline(g, 0, 0, w, cardH, S(12), Px.mix(p.border(), Px.a(0xF43F5E, 190), flash * .85f));
        }

        // avatar: the skin's face in a rounded frame for players, our own drawn head for mobs
        int fx = pad, fy = pad;
        if (t instanceof ClientAvatarEntity av) {
            Px.round(g, fx, fy, face, face, S(10), Px.mix(p.control(), Px.a(Theme.accent(), 90), .25f));
            int fs = Math.max(8, (face - S(8)) / 8 * 8),
                    in = (face - fs) / 2; // a whole number of pixels per skin texel
            PlayerFaceRenderer.draw(g, av.getSkin(), fx + in, fy + in, fs, faded());
        } else MobHeads.draw(g, t.getType(), fx, fy, face, faded());
        if (t.hurtTime > 0) Px.round(g, fx, fy, face, face, S(10), Px.a(0xF43F5E, t.hurtTime * 10));

        // a new target slides its details in
        float sk = Math.min(1, (now - swapAt) / 220f), swap = swapAt == 0 ? 1 : Anim.easeOutCubic(sk);
        float keepC = Px.fade;
        Px.fade = keepC * (.25f + .75f * swap);
        g.pose().pushMatrix();
        g.pose().translate(Math.round(S(8) * (1 - swap)), 0);

        int tx = fx + face + S(12), right = w - pad, chipRight = right;

        // name and "HP / 16,0"
        String name = t.getName().getString();
        Px.text(g, Px.ellipsize(name, Weight.BOLD, S(15), chipRight - tx), tx, fy + S(3), Weight.BOLD, S(15), p.text());
        int hy = fy + S(24), hfs = S(12);
        int hx = tx;
        Px.text(g, Lang.tr("HP"), hx, hy, Weight.MEDIUM, hfs, p.muted());
        hx += Px.textWidth(Lang.tr("HP"), Weight.MEDIUM, hfs) + S(6);
        Px.text(g, Lang.tr("/"), hx, hy, Weight.MEDIUM, hfs, p.dim());
        hx += Px.textWidth(Lang.tr("/"), Weight.MEDIUM, hfs) + S(6);
        String hpText = hpKnown ? one(Math.max(0, hpNumber - abs)) : "?";
        int hpCol = Px.mix(Px.mix(p.text(), p.muted(), .2f), 0xFFF87171, flash);
        Px.text(g, Lang.tr(hpText), hx, hy, Weight.SEMIBOLD, hfs, hpCol);
        int hpEnd = hx + Px.textWidth(Lang.tr(hpText), Weight.SEMIBOLD, hfs);
        if (abs > 0) Px.text(g, Lang.tr("+" + one(abs)), hpEnd + S(6), hy, Weight.SEMIBOLD, hfs, 0xFFFCD34D);
        g.pose().popMatrix();
        Px.fade = keepC;

        // health bar across the card: a calm track and one solid fill with softly rounded corners
        int bx = pad, by = fy + face + S(12), bw = w - pad * 2, br = Math.max(2, Math.round(barH * .3f));
        Px.round(g, bx, by, bw, barH, br, Px.a(p.text(), p.light() ? 20 : 12));
        boolean themed = barStyle.is("Тема");
        int fillC = (themed ? Theme.accent() : hpColour(frac)) | 0xFF000000;
        if (hpGhost > hpShown + .002f)
            Px.round(g, bx, by, Math.max(br * 2, Math.round(bw * hpGhost)), barH, br, Px.a(fillC, 70));
        if (hpShown > .002f) Px.round(g, bx, by, Math.max(br * 2, Math.round(bw * hpShown)), barH, br, fillC);
        if (abs > 0)
            Px.round(
                    g,
                    bx,
                    by,
                    Math.max(br * 2, Math.round(bw * Math.min(1, abs / max))),
                    barH,
                    br,
                    Px.a(0xFCD34D, 150));

        // damage numbers rising from the health value
        pops.removeIf(pp -> now - pp.born > 700 || dead);
        for (Pop pp : pops) {
            float k = (now - pp.born) / 700f;
            float oldF = Px.fade;
            Px.fade = oldF * (1 - k * k);
            Px.text(
                    g,
                    Lang.tr(pp.text),
                    hpEnd + S(abs > 0 ? 50 : 8),
                    hy - Math.round(S(12) * Anim.easeOutCubic(k)),
                    Weight.BOLD,
                    S(11),
                    0xFFF87171);
            Px.fade = oldF;
        }

        // the strip with weapon and armour
        if (!items.isEmpty() && stripK > .01f) {
            float stripOld = Px.fade;
            Px.fade = stripOld * stripK;
            int cell = S(32),
                    sw = S(8) + items.size() * cell,
                    sx = (w - sw) / 2,
                    sy = cardH + stripGap - Math.round(S(8) * (1 - stripK));
            if (background.on()) {
                Px.shadow(g, sx, sy + S(2), sw, stripH, S(12), Px.a(0, p.light() ? 40 : 110));
                Px.round(
                        g,
                        sx,
                        sy,
                        sw,
                        stripH,
                        S(10),
                        (p.bg() & 0xFFFFFF) | (Theme.style == 1 ? 0xF8000000 : 0xEE000000));
                Px.outline(g, sx, sy, sw, stripH, S(10), p.border());
            }
            for (int i = 0; i < items.size(); i++) {
                ItemStack it = items.get(i);
                int cx = sx + S(4) + i * cell;
                item(g, it, cx + (cell - S(19)) / 2, sy + S(6), S(19));
                int lw = S(18), lx = cx + (cell - lw) / 2, ly = sy + stripH - S(8);
                if (it.isDamageableItem()) {
                    float f = 1 - it.getDamageValue() / (float) Math.max(1, it.getMaxDamage());
                    Px.round(g, lx, ly, lw, Math.max(2, S(2)), S(1), Px.a(p.text(), 18));
                    Px.round(g, lx, ly, Math.max(S(2), Math.round(lw * f)), Math.max(2, S(2)), S(1), hpColour(f));
                } else Px.round(g, lx, ly, lw, Math.max(2, S(2)), S(1), Px.a(Theme.accent(), 120));
            }
            Px.fade = stripOld;
        }
        g.pose().popMatrix();
        Px.fade = old;
    }
}
