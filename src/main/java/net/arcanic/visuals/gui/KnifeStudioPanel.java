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
package net.arcanic.visuals.gui;

import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.arcanic.visuals.arc.KnifeStudio;
import net.arcanic.visuals.arc.MeleeDesign;
import net.arcanic.visuals.arc.MeleeSkins;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.mixin.GuiGraphicsAccess;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

final class KnifeStudioPanel {
    private static final String RIGHT = "Правая";
    private static final String LEFT = "Левая";
    private static final int COLUMNS = 7;
    private static final float COMPACT = 340.0F;
    private static final float LARGE = 620.0F;
    private static final float NARROWEST = 300.0F;
    private static final float WIDEST = 980.0F;
    private static final double PX = 16.0;
    private static float widthUnits = 340.0F;
    private final ArcanicMenu menu;
    private MeleeDesign shown;
    private final Setting.Mode hand =
            new Setting.Mode("studio_hand", "Нож в руке", "Правая", new String[] {"Правая", "Левая"});
    private final Setting.Mode gunHand =
            new Setting.Mode("studio_hand", "Оружие в руке", "Правая", new String[] {"Правая", "Левая"});
    private final Map<String, Setting.Num> nums = new HashMap<>();
    private int x;
    private int y;
    private int w;
    private int h;
    private int inner;

    KnifeStudioPanel(ArcanicMenu menu) {
        this.menu = menu;
    }

    int width() {
        int sw = Px.width();
        int min = this.menu.S(300.0);
        float units = this.menu.an("ks:w", widthUnits, this.menu.dragging("ks:resize") ? 60.0F : 12.0F);
        int fit = sw - this.menu.S(20.0) - this.menu.pw - this.menu.S(12.0);
        return Math.max(
                Math.min(min, sw - this.menu.S(16.0)), Math.min(this.menu.S((double) units), Math.max(fit, min)));
    }

    boolean over(double mx, double my) {
        return this.shown != null
                && mx >= (double) this.x
                && mx < (double) (this.x + this.w)
                && my >= (double) this.y
                && my < (double) (this.y + this.h);
    }

    void draw(GuiGraphics g) {
        boolean open = KnifeStudio.design != null;
        float k = this.menu.an("ks", open ? 1.0F : 0.0F, open ? 14.0F : 22.0F);
        if (open) {
            this.shown = KnifeStudio.design;
        } else if (k < 0.01F) {
            this.shown = null;
        }

        MeleeDesign d = this.shown;
        if (d != null) {
            float a = ArcanicMenu.ease(k);
            boolean live = open && !this.menu.blocked() && k > 0.6F;
            int sw = Px.width();
            int sh = Px.height();
            this.w = this.width();
            int pad = this.menu.S(16.0);
            int cw = this.w - pad * 2;
            int head = this.menu.S(58.0);
            int pvh = Math.round((float) cw * 0.64F);
            this.h = Math.min(
                    sh - this.menu.S(24.0),
                    Math.max(this.menu.ph, head + this.menu.S(16.0) + pvh + this.menu.S(300.0)));
            this.x = Math.min(this.menu.px + this.menu.pw + this.menu.S(12.0), sw - this.menu.S(8.0) - this.w);
            this.y = Math.max(this.menu.S(12.0), this.menu.py + (this.menu.ph - this.h) / 2);
            this.click(this.x, this.y, this.w, this.h, () -> {});
            float kf = Px.fade;
            Px.fade = kf * Math.min(1.0F, k * 1.4F);
            g.pose().pushMatrix();
            if (a < 0.999F) {
                float grow = 0.97F + 0.03F * a;
                float oy = (float) (this.y + this.menu.S(60.0));
                g.pose().translate((1.0F - a) * (float) (-this.menu.S(12.0)), 0.0F);
                g.pose().translate((float) this.x, oy);
                g.pose().scale(grow, grow);
                g.pose().translate((float) (-this.x), -oy);
            }

            int bg = ArcanicMenu.panelBg();
            int R = this.menu.S(12.0);
            Px.shadow(
                    g,
                    this.x,
                    this.y + this.menu.S(8.0),
                    this.w,
                    this.h,
                    this.menu.S(24.0),
                    Px.a(0, ArcanicMenu.light() ? 30 : 130));
            Px.round(g, this.x, this.y, this.w, this.h, R, bg);
            Px.outline(g, this.x, this.y, this.w, this.h, R, ArcanicMenu.edgeMedium());
            int bs = this.menu.S(30.0);
            int xx = this.x + this.w - pad - bs + this.menu.S(6.0);
            int ex = xx - bs - this.menu.S(4.0);
            int by = this.y + this.menu.S(12.0);
            Px.text(
                    g,
                    Px.ellipsize(
                            d.getDisplayName(), Px.Weight.SEMIBOLD, this.menu.S(15.0), cw - bs * 2 - this.menu.S(12.0)),
                    this.x + pad,
                    this.y + this.menu.S(14.0),
                    Px.Weight.SEMIBOLD,
                    this.menu.S(15.0),
                    ArcanicMenu.T().text());
            Px.text(
                    g,
                    Px.ellipsize(
                            Lang.tr(
                                    d.getSlot().twoHanded()
                                            ? "Студия оружия: вид от первого лица"
                                            : "Студия ножа: вид от первого лица"),
                            Px.Weight.MEDIUM,
                            this.menu.S(11.0),
                            cw - bs * 2 - this.menu.S(12.0)),
                    this.x + pad,
                    this.y + this.menu.S(33.0),
                    Px.Weight.MEDIUM,
                    this.menu.S(11.0),
                    ArcanicMenu.faint());
            boolean wide = widthUnits > 480.0F;
            this.headButton(g, "maximize-2", "ks:wide", ex, by, bs, wide, live, () -> {
                widthUnits = widthUnits > 480.0F ? 340.0F : 620.0F;
                UiSound.play("click");
            });
            this.headButton(g, "x", "ks:close", xx, by, bs, false, live, () -> {
                KnifeStudio.close();
                UiSound.play("pop", 0.8F, 0.6F);
            });
            Px.rect(
                    g,
                    this.x,
                    this.y + head - this.menu.S(4.0),
                    this.w,
                    Math.max(1, this.menu.S(1.0)),
                    ArcanicMenu.edgeSmall());
            int pvx = this.x + pad;
            int pvy = this.y + head + this.menu.S(6.0);
            g.nextStratum();
            this.preview(g, pvx, pvy, cw, pvh, a, live);
            g.nextStratum();
            int t = pvy + pvh + this.menu.S(10.0);
            int b = this.y + this.h - this.menu.S(6.0);
            float sc = (float) Math.round(this.menu.scrolled("ks", this.inner, t, b));
            g.enableScissor(this.x, t, this.x + this.w, b);
            g.pose().pushMatrix();
            g.pose().translate(0.0F, -sc);
            this.inner = this.controls(g, d, this.x + pad, t, cw, sc, t, b, live) - t + this.menu.S(12.0);
            g.pose().popMatrix();
            g.disableScissor();
            g.nextStratum();
            if (this.inner > b - t) {
                this.menu.edges(g, this.x + this.menu.S(1.0), this.w - this.menu.S(2.0), t, b, sc, bg);
                this.menu.scrollbar(
                        g,
                        this.x + this.w - this.menu.S(8.0),
                        t + this.menu.S(6.0),
                        b - this.menu.S(6.0),
                        this.inner,
                        sc,
                        "ks");
            }

            this.resizeEdge(g, live, sw, this.y + this.menu.S(12.0), pvy + pvh);
            g.pose().popMatrix();
            Px.fade = kf;
        }
    }

    private void headButton(
            GuiGraphics g, String icon, String id, int bx, int by, int bs, boolean on, boolean live, Runnable run) {
        boolean hv = live && this.menu.hover(bx, by, bs, bs);
        if (hv) {
            this.menu.hot(id);
        }

        float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
        float ok = this.menu.tween(id + ":on", on ? 1.0F : 0.0F, 200.0F);
        if (hk > 0.01F || ok > 0.01F) {
            Px.round(
                    g,
                    bx,
                    by,
                    bs,
                    bs,
                    this.menu.S(8.0),
                    Px.mix(Px.a(ArcanicMenu.field(), (int) (255.0F * hk)), ArcanicMenu.tint(0.3F), ok));
        }

        int gs = this.menu.S(14.0);
        ArcanicMenu.glyph(
                g,
                icon,
                bx + (bs - gs) / 2,
                by + (bs - gs) / 2,
                gs,
                Px.mix(Px.mix(ArcanicMenu.sub(), ArcanicMenu.T().text(), hk), ArcanicMenu.acc(), ok));
        if (live) {
            this.click(bx, by, bs, bs, run);
        }
    }

    private void resizeEdge(GuiGraphics g, boolean live, int sw, int top, int bottom) {
        int ew = this.menu.S(12.0);
        int ex = this.x + this.w - ew / 2;
        int eh = bottom - top;
        boolean drag = this.menu.dragging("ks:resize");
        boolean hv = drag || live && this.menu.hover(ex, top, ew, eh);
        if (hv) {
            this.menu.hot("ks:resize");
        }

        float hk = this.menu.an("ks:resize", hv ? 1.0F : 0.0F, 18.0F);
        int bw = Math.max(2, this.menu.S(3.0));
        int bh = this.menu.S(44.0);
        Px.round(
                g,
                this.x + this.w - bw / 2,
                top + (eh - bh) / 2,
                bw,
                bh,
                bw / 2,
                Px.mix(ArcanicMenu.edgeMedium(), ArcanicMenu.acc(), hk));
        if (live) {
            this.menu.dragRegion(
                    ex,
                    top,
                    ew,
                    eh,
                    (mx, my) -> {
                        double want = 2.0 * mx - (double) sw - (double) this.menu.pw - (double) this.menu.S(12.0);
                        float units = (float) (want * 1000.0 / (double) Math.max(1, this.menu.S(1000.0)));
                        widthUnits = Math.max(300.0F, Math.min(980.0F, units));
                    },
                    "ks:resize");
        }
    }

    private void preview(GuiGraphics g, int px, int py, int pw, int ph, float a, boolean live) {
        int R = this.menu.S(10.0);
        int in = this.menu.S(3.0);
        boolean lightTheme = ArcanicMenu.light();
        Px.round(g, px, py, pw, ph, R, lightTheme ? -2302748 : -15921904);
        Px.vgrad(
                g,
                px + in,
                py + in,
                pw - in * 2,
                ph - in * 2,
                lightTheme ? -3549464 : Px.mix(-15395556, ArcanicMenu.acc(), 0.22F),
                lightTheme ? -1184271 : -15921904);
        Px.outline(g, px, py, pw, ph, R, ArcanicMenu.edgeMedium());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            Px.textCenter(
                    g,
                    Lang.tr("Зайди в мир, чтобы увидеть руки"),
                    px + pw / 2,
                    py + ph / 2 - this.menu.S(6.0),
                    Px.Weight.MEDIUM,
                    this.menu.S(12.0),
                    ArcanicMenu.faint());
        } else {
            Window win = mc.getWindow();
            double k = (double) win.getGuiScaledWidth() / (double) Math.max(1, win.getWidth());
            int gx0 = (int) Math.ceil((double) (px + in) * k);
            int gy0 = (int) Math.ceil((double) (py + in) * k);
            int gx1 = (int) Math.floor((double) (px + pw - in) * k);
            int gy1 = (int) Math.floor((double) (py + ph - in) * k);
            if (a > 0.97F && gx1 > gx0 && gy1 > gy0) {
                ((GuiGraphicsAccess) g)
                        .arcanicvisuals$state()
                        .submitPicturesInPictureState(new KnifeStudio.Picture(gx0, gy0, gx1, gy1));
            }

            g.nextStratum();
            int bh = this.menu.S(24.0);
            int bx = px + this.menu.S(10.0);
            int by = py + ph - this.menu.S(10.0) - bh;
            bx += this.button(g, "Осмотр", bx, by, bh, live, KnifeStudio::inspect) + this.menu.S(6.0);
            this.button(g, KnifeStudio.gun() ? "Выстрел" : "Удар", bx, by, bh, live, KnifeStudio::attack);
        }
    }

    private int button(GuiGraphics g, String label, int bx, int by, int bh, boolean live, Runnable run) {
        String s = Lang.tr(label);
        int fs = this.menu.S(11.0);
        int bw = Px.textWidth(s, Px.Weight.MEDIUM, fs) + this.menu.S(20.0);
        boolean hv = live && this.menu.hover(bx, by, bw, bh);
        if (hv) {
            this.menu.hot("ks:b:" + label);
        }

        float hk = this.menu.an("ks:b:" + label, hv ? 1.0F : 0.0F, 18.0F);
        Px.round(g, bx, by, bw, bh, bh / 2, Px.a(Px.mix(-14935006, ArcanicMenu.acc(), hk), 225));
        Px.textCenter(g, s, bx + bw / 2, by + (bh - fs) / 2, Px.Weight.MEDIUM, fs, -1);
        if (live) {
            this.click(bx, by, bw, bh, () -> {
                run.run();
                UiSound.play("click");
            });
        }

        return bw;
    }

    private int controls(GuiGraphics g, MeleeDesign d, int cx, int cy, int cw, float sc, int t, int b, boolean live) {
        MeleeSkins knives = MeleeSkins.of(d);
        if (d.getSlot().twoHanded()) {
            return this.gunControls(g, d, knives, cx, cy, cw, sc, t, b, live);
        }

        KnifeStudio.knifeRight = this.hand.is("Правая");
        cy = this.menu.settings(g, knives, List.of(this.hand), cx, cy, cw, sc, t, b, live);
        KnifeStudio.knifeRight = this.hand.is("Правая");
        cy += this.menu.S(4.0);
        ItemStack other = KnifeStudio.other;
        String otherName =
                other.isEmpty() ? Lang.tr("Пусто") : other.getHoverName().getString();
        cy = this.caption(g, "Во второй руке", otherName, cx, cy, cw);
        cy = this.grid(g, KnifeStudio.ITEMS, cx, cy, cw, sc, t, b, live);
        List<ItemStack> own = hotbar();
        if (!own.isEmpty()) {
            cy += this.menu.S(6.0);
            cy = this.caption(g, "Из твоего хотбара", (String) null, cx, cy, cw);
            cy = this.grid(g, own, cx, cy, cw, sc, t, b, live);
        }

        String knifeKey = MeleeSkins.knifeKey(d);
        String otherKey = MeleeSkins.handKey(other);
        String otherTitle = other.isEmpty() ? Lang.tr("Пустая рука") : otherName;
        boolean knifeRight = KnifeStudio.knifeRight;
        cy += this.menu.S(8.0);
        cy = this.handRows(
                g,
                knives,
                "Правая рука",
                knifeRight ? d.getDisplayName() : otherTitle,
                knifeRight ? knifeKey : otherKey,
                knifeRight || !other.isEmpty(),
                cx,
                cy,
                cw,
                sc,
                t,
                b,
                live);
        cy += this.menu.S(6.0);
        cy = this.handRows(
                g,
                knives,
                "Левая рука",
                knifeRight ? otherTitle : d.getDisplayName(),
                knifeRight ? otherKey : knifeKey,
                !knifeRight || !other.isEmpty(),
                cx,
                cy,
                cw,
                sc,
                t,
                b,
                live);
        cy += this.menu.S(6.0);
        cy = this.resetButton(g, knives, new String[] {knifeKey, otherKey}, cx, cy, sc, t, b, live);

        for (String line : new String[] {
            "Нож запоминается для этого ножа, предмет:", "рядом с любым ножом. X: наружу, Y: вверх, Z: вперёд."
        }) {
            Px.text(g, Lang.tr(line), cx, cy, Px.Weight.MEDIUM, this.menu.S(10.0), ArcanicMenu.faint());
            cy += this.menu.S(15.0);
        }

        return cy;
    }

    /** A gun holds both hands: no item beside it, only where it sits and how large it is. */
    private int gunControls(
            GuiGraphics g,
            MeleeDesign d,
            MeleeSkins guns,
            int cx,
            int cy,
            int cw,
            float sc,
            int t,
            int b,
            boolean live) {
        KnifeStudio.knifeRight = this.gunHand.is("Правая");
        cy = this.menu.settings(g, guns, List.of(this.gunHand), cx, cy, cw, sc, t, b, live);
        KnifeStudio.knifeRight = this.gunHand.is("Правая");
        cy += this.menu.S(8.0);
        String key = MeleeSkins.knifeKey(d);
        cy = this.handRows(g, guns, "Положение", d.getDisplayName(), key, true, cx, cy, cw, sc, t, b, live);
        cy += this.menu.S(6.0);
        cy = this.resetButton(g, guns, new String[] {key}, cx, cy, sc, t, b, live);

        for (String line : new String[] {"Запоминается для этого скина.", "X: наружу, Y: вверх, Z: вперёд."}) {
            Px.text(g, Lang.tr(line), cx, cy, Px.Weight.MEDIUM, this.menu.S(10.0), ArcanicMenu.faint());
            cy += this.menu.S(15.0);
        }

        return cy;
    }

    private int resetButton(
            GuiGraphics g, MeleeSkins knives, String[] keys, int cx, int cy, float sc, int t, int b, boolean live) {
        String rs = Lang.tr("Сбросить положение и размер");
        int fs = this.menu.S(12.0);
        int rw = Px.textWidth(rs, Px.Weight.MEDIUM, fs) + this.menu.S(24.0);
        int rh = this.menu.S(28.0);
        boolean rhv = live && this.my(t, b) && this.menu.hover(cx, Math.round((float) cy - sc), rw, rh);
        if (rhv) {
            this.menu.hot("ks:reset");
        }

        float rk = this.menu.an("ks:reset", rhv ? 1.0F : 0.0F, 18.0F);
        Px.round(g, cx, cy, rw, rh, this.menu.S(7.0), Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), rk));
        Px.textCenter(
                g,
                rs,
                cx + rw / 2,
                cy + (rh - fs) / 2,
                Px.Weight.MEDIUM,
                fs,
                Px.mix(ArcanicMenu.label(), ArcanicMenu.T().text(), rk));
        if (live) {
            this.menu.clipped(
                    cx,
                    cy,
                    rw,
                    rh,
                    sc,
                    t,
                    b,
                    () -> {
                        for (String key : keys) {
                            knives.sizes.put(key, 1.0);

                            for (String axis : MeleeSkins.AXES) {
                                knives.sizes.put(MeleeSkins.axisKey(key, axis), 0.0, 0.0);
                            }
                        }

                        this.nums.clear();
                        Config.dirty();
                        UiSound.play("reset");
                    },
                    (Runnable) null);
        }

        return cy + rh + this.menu.S(10.0);
    }

    private int handRows(
            GuiGraphics g,
            MeleeSkins knives,
            String title,
            String what,
            String key,
            boolean sized,
            int cx,
            int cy,
            int cw,
            float sc,
            int t,
            int b,
            boolean live) {
        cy = this.caption(g, title, what, cx, cy, cw);
        MeleeSkins.Placement at = knives.placement(key);
        List<Setting<?>> rows = new ArrayList<>();
        Setting.Num nx = this.num(key, "x", "Сдвиг X", (double) at.x() * 16.0, -8.0, 8.0, 0.1, " px");
        Setting.Num ny = this.num(key, "y", "Сдвиг Y", (double) at.y() * 16.0, -8.0, 8.0, 0.1, " px");
        Setting.Num nz = this.num(key, "z", "Сдвиг Z", (double) at.z() * 16.0, -8.0, 8.0, 0.1, " px");
        Setting.Num ns = this.num(key, "size", "Размер", (double) at.size() * 100.0, 30.0, 200.0, 5.0, "%");
        rows.add(nx);
        rows.add(ny);
        rows.add(nz);
        if (sized) {
            rows.add(ns);
        }

        this.menu.compact = true;
        cy = this.menu.settings(g, knives, rows, cx, cy, cw, sc, t, b, live);
        this.menu.compact = false;
        boolean changed = false;
        float[] axes = new float[] {at.x(), at.y(), at.z()};
        Setting.Num[] axisNums = new Setting.Num[] {nx, ny, nz};

        for (int i = 0; i < 3; ++i) {
            double v = (Double) axisNums[i].get() / 16.0;
            if (Math.abs(v - (double) axes[i]) > 1.0E-4) {
                knives.sizes.put(MeleeSkins.axisKey(key, MeleeSkins.AXES[i]), v, 0.0);
                changed = true;
            }
        }

        if (sized && Math.abs((Double) ns.get() / 100.0 - (double) at.size()) > 1.0E-4) {
            knives.sizes.put(key, (Double) ns.get() / 100.0);
            changed = true;
        }

        if (changed) {
            Config.dirty();
        }

        return cy;
    }

    private Setting.Num num(
            String key, String axis, String label, double value, double min, double max, double step, String unit) {
        String id = key + "|" + axis;
        Setting.Num n = (Setting.Num) this.nums.get(id);
        if (n == null) {
            n = new Setting.Num(id, label, 0.0, min, max, step, unit);
            n.set(value);
            this.nums.put(id, n);
        }

        return n;
    }

    private boolean my(int t, int b) {
        return this.menu.my >= (double) t && this.menu.my < (double) b;
    }

    private int caption(GuiGraphics g, String text, String right, int cx, int cy, int cw) {
        int fs = this.menu.S(11.0);
        Px.text(g, Lang.tr(text), cx, cy, Px.Weight.SEMIBOLD, fs, ArcanicMenu.faint());
        if (right != null) {
            Px.textRight(
                    g,
                    Px.ellipsize(right, Px.Weight.MEDIUM, fs, cw / 2),
                    cx + cw,
                    cy,
                    Px.Weight.MEDIUM,
                    fs,
                    ArcanicMenu.accText());
        }

        return cy + this.menu.S(22.0);
    }

    private int grid(
            GuiGraphics g, List<ItemStack> items, int cx, int cy, int cw, float sc, int t, int b, boolean live) {
        int gap = this.menu.S(6.0);
        int cols = Math.max(7, (cw + gap) / (this.menu.S(44.0) + gap));
        int ts = (cw - gap * (cols - 1)) / cols;
        int is = Math.round((float) ts * 0.62F);

        for (int i = 0; i < items.size(); ++i) {
            ItemStack s = (ItemStack) items.get(i);
            int tx = cx + i % cols * (ts + gap);
            int ty = cy + i / cols * (ts + gap);
            boolean chosen = same(s, KnifeStudio.other);
            String var10000 = s.isEmpty() ? "-" : MeleeSkins.itemKey(s);
            String id = "ks:i:" + var10000;
            boolean hv = live && this.my(t, b) && this.menu.hover(tx, Math.round((float) ty - sc), ts, ts);
            if (hv && !chosen) {
                this.menu.hot(id);
            }

            float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
            float ck = this.menu.tween(id + ":c", chosen ? 1.0F : 0.0F, 180.0F);
            int r = this.menu.S(8.0);
            Px.round(
                    g,
                    tx,
                    ty,
                    ts,
                    ts,
                    r,
                    Px.mix(Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), hk), ArcanicMenu.tint(0.28F), ck));
            if (ck > 0.01F) {
                Px.outline(g, tx, ty, ts, ts, r, Px.a(ArcanicMenu.acc(), (int) (255.0F * ck)));
            }

            if (s.isEmpty()) {
                int es = this.menu.S(13.0);
                ArcanicMenu.glyph(g, "x-s", tx + (ts - es) / 2, ty + (ts - es) / 2, es, ArcanicMenu.faint());
            } else {
                g.pose().pushMatrix();
                g.pose().translate((float) tx + (float) (ts - is) / 2.0F, (float) ty + (float) (ts - is) / 2.0F);
                g.pose().scale((float) is / 16.0F, (float) is / 16.0F);
                g.renderItem(s, 0, 0);
                g.pose().popMatrix();
            }

            if (live && !chosen) {
                this.menu.clipped(
                        tx,
                        ty,
                        ts,
                        ts,
                        sc,
                        t,
                        b,
                        () -> {
                            KnifeStudio.other = s.copy();
                            UiSound.play("click");
                        },
                        (Runnable) null);
            }
        }

        int rows = (items.size() + cols - 1) / cols;
        return cy + rows * (ts + gap);
    }

    private void click(int cx, int cy, int cw, int ch, Runnable run) {
        this.menu.clipped(cx, cy, cw, ch, 0.0F, 0, Px.height(), run, () -> {});
    }

    private static List<ItemStack> hotbar() {
        LocalPlayer p = Minecraft.getInstance().player;
        List<ItemStack> out = new ArrayList<>();
        if (p == null) {
            return out;
        } else {
            Set<String> seen = new HashSet<>();

            for (ItemStack s : KnifeStudio.ITEMS) {
                if (!s.isEmpty()) {
                    seen.add(MeleeSkins.itemKey(s));
                }
            }

            for (int i = 0; i < 9; ++i) {
                ItemStack s = p.getInventory().getItem(i);
                if (!s.isEmpty() && !s.is(ItemTags.SWORDS) && seen.add(MeleeSkins.itemKey(s))) {
                    out.add(s.copyWithCount(1));
                }
            }

            return out;
        }
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (!a.isEmpty() && !b.isEmpty()) {
            return a.getItem() == b.getItem();
        } else {
            return a.isEmpty() && b.isEmpty();
        }
    }
}
