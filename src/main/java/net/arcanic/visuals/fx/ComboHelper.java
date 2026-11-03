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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class ComboHelper extends Module {
    final Setting.Bool timing = this.add(new Setting.Bool("timing", "Тайминг удара", true));
    final Setting.Bool sprint = this.add(new Setting.Bool("sprint", "Сброс спринта", true));
    final Setting.Bool reach = this.add(new Setting.Bool("reach", "Дистанция", true));
    final Setting.Bool combo = this.add(new Setting.Bool("combo", "Счётчик комбо", true));
    final Setting.Num size = this.add(new Setting.Num("size", "Размер", 1.0, 0.6, 1.6, 0.05, "×"));
    private static final int GREEN = -9508937;
    private static final int RED = -495247;
    private static final int AMBER = -670613;
    private LivingEntity target;
    private long targetSeen;
    private long attackAt;
    private long earlyAt;
    private long wastedAt;
    private long brokenAt;
    private long comboAt;
    private long sprintHitAt;
    private long verdictAt;
    private String verdict = "";
    private int verdictColour;
    private int comboCount;
    private int brokenCount;
    private int lastSelfHurt;
    private int lastTargetHurt;
    private final Anim anim = new Anim();
    private String lastCombo = "";
    private int lastWidth;

    private void say(String word, int colour, long now) {
        this.verdict = word;
        this.verdictColour = colour;
        this.verdictAt = now;
    }

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

    public void onAttack(Player p, Entity e) {
        if (e instanceof LivingEntity l) {
            long now = System.currentTimeMillis();
            if (this.target != l) {
                this.target = l;
                this.comboCount = 0;
            }

            this.targetSeen = now;
            this.attackAt = now;
            boolean early = p.getAttackStrengthScale(0.5F) < 0.9F;
            boolean wasted = l.hurtTime > 2;
            if (early) {
                this.earlyAt = now;
            }

            if (wasted) {
                this.wastedAt = now;
            }

            this.say(
                    early ? "рано" : (wasted ? "неуязвим" : "точно"),
                    early ? -495247 : (wasted ? -670613 : -9508937),
                    now);
            if (p.isSprinting()) {
                this.sprintHitAt = now;
            }
        }
    }

    public void tick() {
        Minecraft mc = mc();
        if (mc.player != null) {
            long now = System.currentTimeMillis();
            Entity var5 = mc.crosshairPickEntity;
            if (var5 instanceof LivingEntity) {
                LivingEntity l = (LivingEntity) var5;
                if (l.isAlive() && l != this.target) {
                    this.target = l;
                    this.comboCount = 0;
                }
            }

            if (mc.crosshairPickEntity == this.target && this.target != null) {
                this.targetSeen = now;
            }

            if (this.target != null
                    && (!this.target.isAlive()
                            || this.target.isRemoved()
                            || now - this.targetSeen > 3000L
                            || this.target.distanceTo(mc.player) > 12.0F)) {
                this.target = null;
                this.comboCount = 0;
            }

            if (this.target != null) {
                int ht = this.target.hurtTime;
                if (ht > this.lastTargetHurt && now - this.attackAt < 600L) {
                    this.comboCount = now - this.comboAt < 2500L ? this.comboCount + 1 : 1;
                    this.comboAt = now;
                }

                this.lastTargetHurt = ht;
            }

            int self = mc.player.hurtTime;
            if (self > this.lastSelfHurt) {
                if (this.comboCount >= 2) {
                    this.brokenAt = now;
                    this.brokenCount = this.comboCount;
                    this.say("комбо сбито", -495247, now);
                }

                this.comboCount = 0;
            }

            this.lastSelfHurt = self;
            if (now - this.comboAt > 2500L) {
                this.comboCount = 0;
            }
        }
    }

    public void render(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.player != null) {
            this.anim.frame();
            long now = System.currentTimeMillis();
            int cx = Px.width() / 2;
            int cy = Px.height() / 2;
            float u = (float) Px.height() / 1080.0F * this.size.f() * 1.1F;
            boolean engaged = this.target != null && now - this.targetSeen < 3000L;
            float show = this.anim.to("show", engaged ? 1.0F : 0.0F, engaged ? 10.0F : 6.0F);
            float base = Px.fade;
            if (show < 0.01F) {
                this.lastWidth = 0;
            } else {
                int fs = Math.max(9, Math.round(13.0F * u));
                int small = Math.max(8, Math.round(11.0F * u));
                double dist = engaged
                        ? Math.sqrt(this.target.getBoundingBox().distanceToSqr(mc.player.getEyePosition()))
                        : 0.0;
                double range = mc.player.entityInteractionRange();
                boolean inReach = engaged && dist <= range;
                String d = this.reach.on() ? String.format(Locale.ROOT, "%.1f", dist) : "";
                String dm = this.reach.on() ? Lang.tr("м") : "";
                String c = this.combo.on() && this.comboCount >= 2 ? "×" + this.comboCount : "";
                int barW = this.timing.on() ? Math.round(64.0F * u) : 0;
                int barH = Math.max(3, Math.round(5.0F * u)) / 2 * 2 + Math.max(3, Math.round(5.0F * u)) % 2;
                int pad = Math.round(11.0F * u);
                int gap = Math.round(10.0F * u);
                int h = Math.max(fs + 10, Math.round(26.0F * u)) / 2 * 2;
                int dW = d.isEmpty()
                        ? 0
                        : steady(d, fs) + Math.round(3.0F * u) + Px.textWidth(dm, Px.Weight.MEDIUM, small);
                int cW = c.isEmpty() ? 0 : Px.textWidth(c, Px.Weight.BOLD, fs);
                float cWa = this.anim.to("cw", (float) cW, 14.0F);
                int inner = dW + (dW > 0 && barW > 0 ? gap : 0) + barW + (cWa > 0.5F ? gap + Math.round(cWa) : 0);
                if (inner <= 0) {
                    Px.fade = base;
                } else {
                    int w = pad * 2 + inner;
                    int x = cx - w / 2;
                    int y = cy + Math.round(30.0F * u);
                    Px.fade = base * show;
                    Px.round(g, x, y, w, h, h / 2, 0xC40D0C12);
                    Px.outline(g, x, y, w, h, h / 2, 587202559);
                    int ix = x + pad;
                    int mid = y + h / 2;
                    if (dW > 0) {
                        int dc = inReach ? -9508937 : (dist <= range + 1.5 ? -670613 : -495247);
                        Px.text(g, d, ix, mid - fs / 2, Px.Weight.SEMIBOLD, fs, dc);
                        Px.text(
                                g,
                                dm,
                                ix + steady(d, fs) + Math.round(3.0F * u),
                                mid - small / 2 + Math.round(1.0F * u),
                                Px.Weight.MEDIUM,
                                small,
                                -6515282);
                        ix += dW + (barW > 0 ? gap : 0);
                    }

                    if (barW > 0) {
                        float swing = Math.min(1.0F, mc.player.getAttackStrengthScale(0.5F));
                        float open = engaged ? 1.0F - Math.min(1.0F, (float) this.target.hurtTime / 9.0F) : 1.0F;
                        float ready = Math.min(swing, open);
                        float fill = this.anim.to("fill", ready, 24.0F);
                        boolean go = ready >= 0.97F && inReach;
                        float gk = this.anim.to("go", go ? 1.0F : 0.0F, 16.0F);
                        int by = mid - barH / 2;
                        Px.round(g, ix, by, barW, barH, barH / 2, 788529151);
                        int fc = Px.mix(inReach ? Theme.accent2() | 0xFF000000 : -7436389, -9508937, gk);
                        fc = Px.mix(fc, -495247, flash(now, this.earlyAt));
                        fc = Px.mix(fc, -670613, flash(now, this.wastedAt));
                        int fw = Math.round((float) barW * Math.max(0.0F, Math.min(1.0F, fill)));
                        if (fw > 0) {
                            Px.round(g, ix, by, Math.max(barH, fw), barH, barH / 2, fc);
                        }

                        if (gk > 0.02F) {
                            float pulse = (float) (Math.sin((double) now / 120.0) * 0.5 + 0.5);
                            Px.fade = base * show * gk * (0.25F + 0.2F * pulse);
                            Px.round(g, ix - 2, by - 2, barW + 4, barH + 4, barH / 2 + 2, -9508937);
                            Px.fade = base * show;
                        }

                        ix += barW;
                    }

                    if (cWa > 0.5F) {
                        ix += gap;
                        float pop = Math.min(1.0F, (float) (now - this.comboAt) / 160.0F);
                        int col = this.comboCount >= 6 ? -20448 : -856328;
                        float k = Px.fade;
                        Px.fade = k * Math.min(1.0F, cWa / Math.max(1.0F, cW == 0 ? cWa : (float) cW));
                        g.pose().pushMatrix();
                        float sc = 1.0F + 0.3F * (1.0F - Anim.easeOutCubic(pop));
                        g.pose().translate((float) ix + cWa / 2.0F, (float) mid);
                        g.pose().scale(sc, sc);
                        String shown = c.isEmpty() ? this.lastCombo : c;
                        int sw = Px.textWidth(shown, Px.Weight.BOLD, fs);
                        Px.text(g, shown, -sw / 2, -fs / 2, Px.Weight.BOLD, fs, col);
                        g.pose().popMatrix();
                        Px.fade = k;
                    }

                    if (!c.isEmpty()) {
                        this.lastCombo = c;
                    }

                    float vt = (float) (now - this.verdictAt) / 900.0F;
                    if (this.timing.on() && this.verdictAt > 0L && vt < 1.0F && !this.verdict.isEmpty()) {
                        String t = Lang.tr(this.verdict);
                        Px.fade = base * show * (vt < 0.12F ? vt / 0.12F : 1.0F - Math.max(0.0F, (vt - 0.55F) / 0.45F));
                        int tx = x
                                + w
                                + Math.round(8.0F * u)
                                - Math.round(6.0F * u * (1.0F - Anim.easeOutCubic(Math.min(1.0F, vt * 4.0F))));
                        int ty = y + (h - small) / 2;
                        Px.text(g, t, tx + 1, ty + 1, Px.Weight.SEMIBOLD, small, 0x99000000);
                        Px.text(g, t, tx, ty, Px.Weight.SEMIBOLD, small, this.verdictColour);
                    }

                    boolean needSprint = engaged
                            && this.sprint.on()
                            && now - this.sprintHitAt < 2500L
                            && !mc.player.isSprinting()
                            && mc.options.keyUp.isDown()
                            && mc.player.getFoodData().getFoodLevel() > 6;
                    float sk = this.anim.to("sprint", needSprint ? 1.0F : 0.0F, needSprint ? 14.0F : 9.0F);
                    if (sk > 0.01F) {
                        Px.fade = base * sk;
                        String t = Lang.tr("спринт");
                        int kh = Math.max(small + 6, Math.round(18.0F * u)) / 2 * 2;
                        int tw = Px.textWidth(t, Px.Weight.SEMIBOLD, small);
                        int dash = Math.round(6.0F * u);
                        int total = kh + dash + tw;
                        int sx = cx - total / 2;
                        int sy = y + h + Math.round(8.0F * u) - Math.round(4.0F * u * (1.0F - sk));
                        float pulse = (float) (Math.sin((double) now / 150.0) * 0.5 + 0.5);
                        Px.round(g, sx, sy, kh, kh, Math.max(2, Math.round(5.0F * u)), Px.a(16106603, (int)
                                (40.0F + 40.0F * pulse)));
                        Px.outline(g, sx, sy, kh, kh, Math.max(2, Math.round(5.0F * u)), Px.a(16106603, (int)
                                (150.0F + 100.0F * pulse)));
                        int ww = Px.textWidth("W", Px.Weight.BOLD, small);
                        Px.text(g, "W", sx + (kh - ww) / 2, sy + (kh - small) / 2, Px.Weight.BOLD, small, -6216);
                        Px.text(g, t, sx + kh + dash, sy + (kh - small) / 2, Px.Weight.SEMIBOLD, small, -661816);
                    }

                    Px.fade = base;
                    this.lastWidth = w;
                }
            }
        }
    }

    private static int steady(String s, int fs) {
        return Math.max(
                Px.textWidth(s, Px.Weight.SEMIBOLD, fs),
                Px.textWidth(s.replaceAll("[0-9]", "0"), Px.Weight.SEMIBOLD, fs));
    }

    private static float flash(long now, long at) {
        float t = (float) (now - at) / 380.0F;
        return at != 0L && !(t >= 1.0F) ? 1.0F - t * t : 0.0F;
    }
}
