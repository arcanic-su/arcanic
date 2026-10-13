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

import java.util.Locale;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Combo helper: a small coach under the crosshair. */
public final class ComboHelper extends Module {
    final Setting.Bool timing = add(new Setting.Bool("timing", "Тайминг удара", true));
    final Setting.Bool sprint = add(new Setting.Bool("sprint", "Сброс спринта", true));
    final Setting.Bool reach = add(new Setting.Bool("reach", "Дистанция", true));
    final Setting.Bool combo = add(new Setting.Bool("combo", "Счётчик комбо", true));
    final Setting.Num size = add(new Setting.Num("size", "Размер", 1, .6, 1.6, .05, "×"));

    private static final int GREEN = 0xFF6EE7B7, RED = 0xFFF87171, AMBER = 0xFFF5C46B;

    private void say(String word, int colour, long now) {
        verdict = word;
        verdictColour = colour;
        verdictAt = now;
    }

    private LivingEntity target;
    private long targetSeen, attackAt, earlyAt, wastedAt, brokenAt, comboAt, sprintHitAt, verdictAt;
    private String verdict = "";
    private int verdictColour;
    private int comboCount, brokenCount, lastSelfHurt, lastTargetHurt;
    private final Anim anim = new Anim();

    public ComboHelper() {
        super(
                "combohelper",
                "Комбо-хелпер",
                "Подсказки для комбо: тайминг удара, сброс спринта, дистанция и счёт",
                Category.COMBAT,
                false);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Our own attack (client side, before it is sent). */
    public void onAttack(Player p, Entity e) {
        if (!(e instanceof LivingEntity l)) return;
        long now = System.currentTimeMillis();
        if (target != l) {
            target = l;
            comboCount = 0;
        }
        targetSeen = now;
        attackAt = now;
        boolean early = p.getAttackStrengthScale(.5f) < .9f, wasted = l.hurtTime > 2;
        if (early) earlyAt = now;
        if (wasted) wastedAt = now;
        say(early ? "рано" : wasted ? "неуязвим" : "точно", early ? RED : wasted ? AMBER : GREEN, now);
        if (p.isSprinting()) sprintHitAt = now;
    }

    @Override
    public void tick() {
        var mc = mc();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (mc.crosshairPickEntity instanceof LivingEntity l && l.isAlive() && l != target) {
            target = l;
            comboCount = 0;
        }
        if (mc.crosshairPickEntity == target && target != null) targetSeen = now;
        if (target != null
                && (!target.isAlive()
                        || target.isRemoved()
                        || now - targetSeen > 3000
                        || target.distanceTo(mc.player) > 12)) {
            target = null;
            comboCount = 0;
        }
        // a hit of ours registered on the target: its hurt time jumps up shortly after our swing
        if (target != null) {
            int ht = target.hurtTime;
            if (ht > lastTargetHurt && now - attackAt < 600) {
                comboCount = now - comboAt < 2500 ? comboCount + 1 : 1;
                comboAt = now;
            }
            lastTargetHurt = ht;
        }
        // we were hit: the combo is broken
        int self = mc.player.hurtTime;
        if (self > lastSelfHurt) {
            if (comboCount >= 2) {
                brokenAt = now;
                brokenCount = comboCount;
                say("комбо сбито", RED, now);
            }
            comboCount = 0;
        }
        lastSelfHurt = self;
        if (now - comboAt > 2500) comboCount = 0;
    }

    /** Drawn in pixel space by the crosshair element. */
    public void render(GuiGraphics g) {
        var mc = mc();
        if (mc.player == null) return;
        anim.frame();
        long now = System.currentTimeMillis();
        int cx = Px.width() / 2, cy = Px.height() / 2;
        float u = Px.height() / 1080f * size.f() * 1.1f;
        boolean engaged = target != null && now - targetSeen < 3000;
        float show = anim.to("show", engaged ? 1 : 0, engaged ? 10 : 6);
        float base = Px.fade;
        if (show < .01f) {
            lastWidth = 0;
            return;
        }
        int fs = Math.max(9, Math.round(13 * u)), small = Math.max(8, Math.round(11 * u));

        double dist = engaged ? Math.sqrt(target.getBoundingBox().distanceToSqr(mc.player.getEyePosition())) : 0;
        double range = mc.player.entityInteractionRange();
        boolean inReach = engaged && dist <= range;

        // the plate under the crosshair: distance · bar · combo
        String d = reach.on() ? String.format(Locale.ROOT, "%.1f", dist) : "";
        String dm = reach.on() ? Lang.tr("м") : "";
        String c = combo.on() && comboCount >= 2 ? "×" + comboCount : "";
        int barW = timing.on() ? Math.round(64 * u) : 0,
                barH = Math.max(3, Math.round(5 * u)) / 2 * 2 + (Math.max(3, Math.round(5 * u)) % 2);
        int pad = Math.round(11 * u), gap = Math.round(10 * u), h = Math.max(fs + 10, Math.round(26 * u)) / 2 * 2;
        int dW = d.isEmpty() ? 0 : steady(d, fs) + Math.round(3 * u) + Px.textWidth(dm, Weight.MEDIUM, small);
        int cW = c.isEmpty() ? 0 : Px.textWidth(c, Weight.BOLD, fs);
        float cWa = anim.to("cw", cW, 14);
        int inner = dW + (dW > 0 && barW > 0 ? gap : 0) + barW + (cWa > .5f ? gap + Math.round(cWa) : 0);
        if (inner <= 0) {
            Px.fade = base;
            return;
        }
        int w = pad * 2 + inner;
        int x = cx - w / 2, y = cy + Math.round(30 * u);
        Px.fade = base * show;
        Px.round(g, x, y, w, h, h / 2, 0xC40D0C12);
        Px.outline(g, x, y, w, h, h / 2, 0x22FFFFFF);
        int ix = x + pad, mid = y + h / 2;
        if (dW > 0) {
            int dc = inReach ? GREEN : dist <= range + 1.5 ? AMBER : RED;
            Px.text(g, d, ix, mid - fs / 2, Weight.SEMIBOLD, fs, dc);
            Px.text(
                    g,
                    dm,
                    ix + steady(d, fs) + Math.round(3 * u),
                    mid - small / 2 + Math.round(1 * u),
                    Weight.MEDIUM,
                    small,
                    0xFF9C95AE);
            ix += dW + (barW > 0 ? gap : 0);
        }
        if (barW > 0) {
            float swing = Math.min(1, mc.player.getAttackStrengthScale(.5f));
            float open = engaged ? 1 - Math.min(1, target.hurtTime / 9f) : 1;
            float ready = Math.min(swing, open);
            float fill = anim.to("fill", ready, 24);
            boolean go = ready >= .97f && inReach;
            float gk = anim.to("go", go ? 1 : 0, 16);
            int by = mid - barH / 2;
            Px.round(g, ix, by, barW, barH, barH / 2, 0x2EFFFFFF);
            int fc = Px.mix(inReach ? (Theme.accent2() | 0xFF000000) : 0xFF8E879B, GREEN, gk);
            fc = Px.mix(fc, RED, flash(now, earlyAt));
            fc = Px.mix(fc, AMBER, flash(now, wastedAt));
            int fw = Math.round(barW * Math.max(0, Math.min(1, fill)));
            if (fw > 0) Px.round(g, ix, by, Math.max(barH, fw), barH, barH / 2, fc);
            if (gk > .02f) { // ready: the bar breathes a soft green light
                float pulse = (float) (Math.sin(now / 120.0) * .5 + .5);
                Px.fade = base * show * gk * (.25f + .2f * pulse);
                Px.round(g, ix - 2, by - 2, barW + 4, barH + 4, barH / 2 + 2, GREEN);
                Px.fade = base * show;
            }
            ix += barW;
        }
        if (cWa > .5f) {
            ix += gap;
            float pop = Math.min(1, (now - comboAt) / 160f);
            int col = comboCount >= 6 ? 0xFFFFB020 : 0xFFF2EEF8;
            float k = Px.fade;
            Px.fade = k * Math.min(1, cWa / Math.max(1, cW == 0 ? cWa : cW));
            g.pose().pushMatrix();
            float sc = 1 + .3f * (1 - Anim.easeOutCubic(pop));
            g.pose().translate(ix + cWa / 2f, mid);
            g.pose().scale(sc, sc);
            String shown = c.isEmpty() ? lastCombo : c;
            int sw = Px.textWidth(shown, Weight.BOLD, fs);
            Px.text(g, shown, -sw / 2, -fs / 2, Weight.BOLD, fs, col);
            g.pose().popMatrix();
            Px.fade = k;
        }
        if (!c.isEmpty()) lastCombo = c;

        // the verdict of the last hit
        float vt = (now - verdictAt) / 900f;
        if (timing.on() && verdictAt > 0 && vt < 1 && !verdict.isEmpty()) {
            String t = Lang.tr(verdict);
            Px.fade = base * show * (vt < .12f ? vt / .12f : 1 - Math.max(0, (vt - .55f) / .45f));
            // beside the plate on the right, sliding out from it: never over the crosshair
            int tx = x + w + Math.round(8 * u) - Math.round(6 * u * (1 - Anim.easeOutCubic(Math.min(1, vt * 4f))));
            int ty = y + (h - small) / 2;
            Px.text(g, t, tx + 1, ty + 1, Weight.SEMIBOLD, small, 0x99000000);
            Px.text(g, t, tx, ty, Weight.SEMIBOLD, small, verdictColour);
        }

        // re-sprint: "W: sprint" under the plate while the sprint is gone after a sprint hit
        boolean needSprint = engaged
                && sprint.on()
                && now - sprintHitAt < 2500
                && !mc.player.isSprinting()
                && mc.options.keyUp.isDown()
                && mc.player.getFoodData().getFoodLevel() > 6;
        float sk = anim.to("sprint", needSprint ? 1 : 0, needSprint ? 14 : 9);
        if (sk > .01f) {
            Px.fade = base * sk;
            String t = Lang.tr("спринт");
            int kh = Math.max(small + 6, Math.round(18 * u)) / 2 * 2, kw = kh;
            int tw = Px.textWidth(t, Weight.SEMIBOLD, small), dash = Math.round(6 * u);
            int total = kw + dash + tw;
            int sx = cx - total / 2, sy = y + h + Math.round(8 * u) - Math.round(4 * u * (1 - sk));
            float pulse = (float) (Math.sin(now / 150.0) * .5 + .5);
            Px.round(g, sx, sy, kw, kh, Math.max(2, Math.round(5 * u)), Px.a(0xF5C46B, (int) (40 + 40 * pulse)));
            Px.outline(g, sx, sy, kw, kh, Math.max(2, Math.round(5 * u)), Px.a(0xF5C46B, (int) (150 + 100 * pulse)));
            int ww = Px.textWidth("W", Weight.BOLD, small);
            Px.text(g, "W", sx + (kw - ww) / 2, sy + (kh - small) / 2, Weight.BOLD, small, 0xFFFFE7B8);
            Px.text(g, t, sx + kw + dash, sy + (kh - small) / 2, Weight.SEMIBOLD, small, 0xFFF5E6C8);
        }
        Px.fade = base;
        lastWidth = w;
    }

    private String lastCombo = "";
    private int lastWidth;

    private static int steady(String s, int fs) {
        return Math.max(
                Px.textWidth(s, Weight.SEMIBOLD, fs), Px.textWidth(s.replaceAll("[0-9]", "0"), Weight.SEMIBOLD, fs));
    }

    private static float flash(long now, long at) {
        float t = (now - at) / 380f;
        return at == 0 || t >= 1 ? 0 : 1 - t * t;
    }
}
