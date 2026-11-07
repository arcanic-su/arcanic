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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Social;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.fx.Crosshair;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The crosshair studio, laid out as the menu's other studio: the presets and the player's own as a list on the left
 * with the code actions under it, the preview in the middle on a backdrop of choice, and the switch, the shapes, the
 * colours and every setting on the card at the right.
 */
final class CrosshairPage {
    private static final String[] BACKDROPS = {"Тёмный", "Светлый", "Небо"};

    private final ArcanicMenu menu;
    private long confirmAt;
    private String confirm = "";
    private int backdrop;

    CrosshairPage(ArcanicMenu menu) {
        this.menu = menu;
    }

    private int S(double v) {
        return this.menu.S(v);
    }

    String scrollKey() {
        return "xh:settings";
    }

    void studio(GuiGraphics g) {
        int gap = this.S(12.0);
        int lw = this.S(190.0);
        int rw = this.S(272.0);
        int lx = this.menu.cx0;
        int pvx = lx + lw + gap;
        int pvw = this.menu.cw0 - lw - rw - gap * 2;
        int rx = pvx + pvw + gap;
        this.list(g, lx, lw);
        this.stage(g, pvx, pvw);
        this.card(g, rx, rw);
    }

    // ------------------------------------------------------------------ the left: presets, the player's own, codes

    private void list(GuiGraphics g, int lx, int lw) {
        Crosshair xh = Modules.CROSSHAIR;
        int t = this.menu.top;
        int b = this.menu.bottom;
        int ih = this.S(30.0);
        int step = ih + this.S(3.0);
        String[][] share = {{"copy", "Скопировать код"}, {"download", "Вставить код"}, {"send", "Отправить другу"}};
        int foot = share.length * step + this.S(12.0);
        int lb = b - foot;

        Crosshair.Preset[] presets = Crosshair.Preset.values();
        List<Crosshair.Saved> lib = xh.library();
        boolean room = lib.size() < Crosshair.LIBRARY_MAX;
        int inner = this.S(2.0)
                + this.S(20.0)
                + presets.length * step
                + this.S(8.0)
                + this.S(20.0)
                + (lib.size() + 1) * step;
        float sc = (float) Math.round(this.menu.scrolled("xh:list", inner, t, lb));
        g.enableScissor(lx, t, lx + lw, lb);
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -sc);
        int y = t + this.S(2.0);
        this.heading(g, Lang.tr("Пресеты"), null, lx, lw, y);
        y += this.S(20.0);
        for (int i = 0; i < presets.length; i++) {
            Crosshair.Preset p = presets[i];
            this.row(
                    g,
                    "xp:" + i,
                    lx,
                    y,
                    lw,
                    ih,
                    sc,
                    lb,
                    xh.is(p.spec),
                    p.spec,
                    null,
                    Lang.tr(p.title),
                    () -> {
                        xh.apply(p.spec);
                        UiSound.play("select");
                    },
                    null);
            y += step;
        }
        y += this.S(8.0);
        this.heading(g, Lang.tr("Мои пресеты"), lib.size() + "/" + Crosshair.LIBRARY_MAX, lx, lw, y);
        y += this.S(20.0);
        for (int i = lib.size() - 1; i >= 0; i--) {
            Crosshair.Saved s = lib.get(i);
            boolean sure = this.confirm.equals(s.name()) && this.menu.now - this.confirmAt < 2600L;
            this.row(
                    g,
                    "xs:" + s.name(),
                    lx,
                    y,
                    lw,
                    ih,
                    sc,
                    lb,
                    xh.is(s.spec()),
                    s.spec(),
                    null,
                    sure ? Lang.tr("Удалить?") : s.name(),
                    () -> {
                        xh.apply(s.spec());
                        UiSound.play("select");
                    },
                    () -> {
                        if (this.confirm.equals(s.name()) && this.menu.now - this.confirmAt < 2600L) {
                            xh.forget(s);
                            this.confirm = "";
                            UiSound.play("drain");
                        } else {
                            this.confirm = s.name();
                            this.confirmAt = this.menu.now;
                            UiSound.play("pop", 0.9F, 0.8F);
                        }
                    });
            y += step;
        }
        this.row(
                g,
                "xs:+",
                lx,
                y,
                lw,
                ih,
                sc,
                lb,
                false,
                null,
                "plus",
                Lang.tr(room ? "Сохранить текущий" : "Всё занято"),
                room
                        ? () -> {
                            xh.keep(Lang.tr("Мой прицел"), xh.current());
                            UiSound.play("select");
                            Social.note(Lang.tr("Прицел сохранён в мои пресеты"), true);
                        }
                        : null,
                null);
        g.pose().popMatrix();
        g.disableScissor();
        this.menu.edges(g, lx, lw, t, lb, sc, ArcanicMenu.panelBg());

        // the code actions, fixed under the list
        int fy = lb + this.S(6.0);
        Px.rect(g, lx + this.S(8.0), fy, lw - this.S(16.0), Math.max(1, this.S(1.0)), ArcanicMenu.edgeSmall());
        fy += this.S(6.0);
        for (int i = 0; i < share.length; i++) {
            int idx = i;
            this.row(
                    g,
                    "xb:" + i,
                    lx,
                    fy,
                    lw,
                    ih,
                    0.0F,
                    b,
                    false,
                    null,
                    share[i][0],
                    Lang.tr(share[i][1]),
                    () -> {
                        switch (idx) {
                            case 0 -> this.copy();
                            case 1 -> this.paste();
                            default -> this.send();
                        }
                    },
                    null);
            fy += step;
        }
    }

    private void heading(GuiGraphics g, String text, String right, int x, int w, int y) {
        int fs = this.S(11.0);
        Px.text(g, text, x + this.S(12.0), y, Px.Weight.SEMIBOLD, fs, ArcanicMenu.faint());
        if (right != null) {
            Px.textRight(g, right, x + w - this.S(12.0), y, Px.Weight.MEDIUM, fs, ArcanicMenu.faint());
        }
    }

    /**
     * One list row as the studio's effect list: a small picture (a crosshair, or a glyph) in a well, the name, the
     * chosen one on a raised plate with the accent bar at its left.
     */
    private void row(
            GuiGraphics g,
            String id,
            int x,
            int y,
            int w,
            int h,
            float sc,
            int clipB,
            boolean on,
            Crosshair.Spec look,
            String glyph,
            String name,
            Runnable click,
            Runnable alt) {
        boolean live = click != null;
        int vy = Math.round(y - sc);
        boolean hv = live
                && !this.menu.under()
                && this.menu.hover(x, vy, w, h)
                && this.menu.my >= this.menu.top
                && this.menu.my < clipB;
        if (hv && !on) this.menu.hot(id);
        float hk = this.menu.an("xr:" + id, hv && !on ? 1.0F : 0.0F, 18.0F);
        float sk = this.menu.tween("xrs:" + id, on ? 1.0F : 0.0F, 180.0F);
        float old = Px.fade;
        if (!live) Px.fade = old * 0.45F;
        int r = this.S(8.0);
        if (sk > 0.01F) {
            Px.round(g, x, y, w, h, r, Px.a(ArcanicMenu.cardHi(), (int) (255.0F * sk)));
            Px.round(
                    g,
                    x,
                    y + this.S(9.0),
                    Math.max(2, this.S(3.0)),
                    h - this.S(18.0),
                    this.S(2.0),
                    Px.a(ArcanicMenu.acc(), (int) (255.0F * sk)));
        } else if (hk > 0.01F) {
            Px.round(g, x, y, w, h, r, Px.a(ArcanicMenu.field(), (int) (255.0F * hk)));
        }
        int well = this.S(22.0);
        int wx = x + this.S(12.0);
        int wy = y + (h - well) / 2;
        Px.round(g, wx, wy, well, well, this.S(6.0), ArcanicMenu.light() ? 0xFF1B1D24 : ArcanicMenu.field());
        if (look != null) {
            Crosshair.icon(g, look, wx + well / 2.0F, wy + well / 2.0F, well - this.S(6.0));
        } else if (glyph != null) {
            int is = this.S(13.0);
            ArcanicMenu.glyph(
                    g,
                    glyph,
                    wx + (well - is) / 2,
                    wy + (well - is) / 2,
                    is,
                    ArcanicMenu.light()
                            ? -1
                            : Px.mix(ArcanicMenu.sub(), ArcanicMenu.T().text(), hk));
        }
        int fs = this.S(13.0);
        int tx = wx + well + this.S(10.0);
        Px.text(
                g,
                Px.ellipsize(name, Px.Weight.MEDIUM, fs, x + w - this.S(10.0) - tx),
                tx,
                y + (h - fs) / 2,
                Px.Weight.MEDIUM,
                fs,
                Px.mix(ArcanicMenu.sub(), ArcanicMenu.T().text(), Math.max(hk, sk)));
        Px.fade = old;
        if (live) this.menu.clipped(x, y, w, h, sc, this.menu.top, clipB, click, alt);
    }

    // ------------------------------------------------------------------ the middle: the preview

    private void stage(GuiGraphics g, int pvx, int pvw) {
        Crosshair xh = Modules.CROSSHAIR;
        int t = this.menu.top;
        int b = this.menu.bottom;
        int ph = b - t;
        int r = this.S(12.0);
        boolean lt = ArcanicMenu.light();
        switch (this.backdrop) {
            case 1 -> Px.round(g, pvx, t, pvw, ph, r, 0xFFDCE2EA);
            case 2 -> {
                // a Minecraft sky: deep at the top, pale at the horizon
                Px.vgrad(g, pvx, t + r * 2, pvw, ph - r * 4, 0xFF6F9FF2, 0xFFC4DBFF);
                Px.round(g, pvx, t, pvw, r * 2, r, 0xFF6F9FF2);
                Px.rect(g, pvx, t + r, pvw, r, 0xFF6F9FF2);
                Px.round(g, pvx, b - r * 2, pvw, r * 2, r, 0xFFC4DBFF);
                Px.rect(g, pvx, b - r * 2, pvw, r, 0xFFC4DBFF);
            }
            default -> {
                Px.round(g, pvx, t, pvw, ph, r, lt ? 0xFF1B1D24 : 0xFF0D0D0F);
                g.enableScissor(pvx, t, pvx + pvw, b);
                Px.glow(g, pvx + pvw / 10, t - ph / 10, pvw * 4 / 5, ph, Px.a(ArcanicMenu.acc(), 40));
                g.disableScissor();
            }
        }
        Px.outline(g, pvx, t, pvw, ph, r, ArcanicMenu.edgeMedium());
        xh.preview(g, pvx, t, pvw, ph, r);
        g.nextStratum();
        this.backdropSwitch(g, pvx + this.S(12.0), t + this.S(12.0));

        Crosshair.Style st = xh.shape();
        String what = Lang.tr(st.title);
        int cfs = this.S(12.0);
        int cph = this.S(26.0);
        int cpw = Px.textWidth(what, Px.Weight.MEDIUM, cfs) + this.S(24.0);
        int cpy = b - this.S(12.0) - cph;
        Px.round(g, pvx + this.S(12.0), cpy, cpw, cph, cph / 2, 0xB8101013);
        Px.text(g, what, pvx + this.S(24.0), cpy + (cph - cfs) / 2, Px.Weight.MEDIUM, cfs, 0xFFF4F4F6);
        int hfs = this.S(11.0);
        String hint = Px.ellipsize(Lang.tr(st.hint), Px.Weight.MEDIUM, hfs, pvw - cpw - this.S(48.0));
        int hw = Px.textWidth(hint, Px.Weight.MEDIUM, hfs) + this.S(24.0);
        if (hw > this.S(30.0)) {
            int hx = pvx + pvw - this.S(12.0) - hw;
            Px.round(g, hx, cpy, hw, cph, cph / 2, 0xB8101013);
            Px.text(g, hint, hx + this.S(12.0), cpy + (cph - hfs) / 2, Px.Weight.MEDIUM, hfs, 0xB3F4F4F6);
        }
    }

    /** The backdrop switch at the preview's corner, the menu's segmented pill with the accent plate gliding. */
    private void backdropSwitch(GuiGraphics g, int x, int y) {
        int fs = this.S(11.0);
        int h = this.S(26.0);
        int pad = this.S(12.0);
        int inset = this.S(3.0);
        int[] ws = new int[BACKDROPS.length];
        int[] xs = new int[BACKDROPS.length];
        int total = inset * 2;
        for (int i = 0; i < BACKDROPS.length; i++) {
            ws[i] = Px.textWidth(Lang.tr(BACKDROPS[i]), Px.Weight.MEDIUM, fs) + pad * 2;
            total += ws[i];
        }
        Px.round(g, x, y, total, h, h / 2, 0xB8101013);
        int cx = x + inset;
        for (int i = 0; i < BACKDROPS.length; i++) {
            xs[i] = cx;
            cx += ws[i];
        }
        int plx = Math.round(this.menu.tween("xhBgX", (float) xs[this.backdrop], 200.0F));
        int plw = Math.round(this.menu.tween("xhBgW", (float) ws[this.backdrop], 200.0F));
        Px.round(g, plx, y + inset, plw, h - inset * 2, (h - inset * 2) / 2, ArcanicMenu.acc());
        for (int i = 0; i < BACKDROPS.length; i++) {
            boolean on = i == this.backdrop;
            boolean hv = !this.menu.under() && this.menu.hover(xs[i], y, ws[i], h);
            if (hv && !on) this.menu.hot("xhBg:" + i);
            float hk = this.menu.an("xhBg:" + i, hv ? 1.0F : 0.0F, 18.0F);
            float ak = this.menu.tween("xhBgOn:" + i, on ? 1.0F : 0.0F, 180.0F);
            int c = Px.mix(Px.mix(0xFF8A8F98, 0xFFE4E4E7, hk), -1, ak);
            Px.textCenter(g, Lang.tr(BACKDROPS[i]), xs[i] + ws[i] / 2, y + (h - fs) / 2, Px.Weight.MEDIUM, fs, c);
            int pick = i;
            this.menu.clipped(
                    xs[i],
                    y,
                    ws[i],
                    h,
                    0.0F,
                    this.menu.top,
                    this.menu.bottom,
                    () -> {
                        if (this.backdrop != pick) {
                            this.backdrop = pick;
                            UiSound.play("click");
                        }
                    },
                    null);
        }
    }

    // ------------------------------------------------------------------ the right: switch, shapes, colours, settings

    private void card(GuiGraphics g, int rx, int rw) {
        Crosshair xh = Modules.CROSSHAIR;
        int t = this.menu.top;
        int b = this.menu.bottom;
        Px.round(g, rx, t, rw, b - t, this.S(12.0), ArcanicMenu.cardBg());
        Px.outline(g, rx, t, rw, b - t, this.S(12.0), ArcanicMenu.edgeMedium());
        int pad = this.S(14.0);
        int iw = rw - pad * 2;
        int ix = rx + pad;
        Px.text(
                g,
                xh.title(),
                ix,
                t + this.S(14.0),
                Px.Weight.SEMIBOLD,
                this.S(15.0),
                ArcanicMenu.T().text());
        Px.text(
                g,
                Px.ellipsize(Lang.tr(xh.description), Px.Weight.MEDIUM, this.S(11.0), iw),
                ix,
                t + this.S(34.0),
                Px.Weight.MEDIUM,
                this.S(11.0),
                ArcanicMenu.faint());

        // the switch
        int ry = t + this.S(56.0);
        int rh = this.S(38.0);
        Px.round(g, ix - this.S(4.0), ry, iw + this.S(8.0), rh, this.S(8.0), ArcanicMenu.field());
        Px.text(
                g,
                Lang.tr("Включён в игре"),
                ix + this.S(8.0),
                ry + (rh - this.S(13.0)) / 2,
                Px.Weight.MEDIUM,
                this.S(13.0),
                ArcanicMenu.label());
        float onK = this.menu.tween("on:" + xh.id, xh.enabled() ? 1.0F : 0.0F, 180.0F);
        boolean thv = !this.menu.under() && this.menu.hover(ix - this.S(4.0), ry, iw + this.S(8.0), rh);
        this.menu.toggle(g, ix + iw - this.S(30.0) - this.S(4.0), ry, this.S(30.0), rh, onK, thv ? 1.0F : 0.0F);
        this.menu.clipped(
                ix - this.S(4.0),
                ry,
                iw + this.S(8.0),
                rh,
                0.0F,
                t,
                b,
                () -> {
                    xh.toggle();
                    UiSound.play(xh.enabled() ? "charge" : "drain");
                    Toasts.module(xh, false);
                    Config.dirty();
                },
                null);
        ry += rh + this.S(14.0);

        // the shapes: a three by two grid of small tiles
        Px.text(g, Lang.tr("Форма"), ix, ry, Px.Weight.SEMIBOLD, this.S(11.0), ArcanicMenu.faint());
        ry += this.S(18.0);
        Crosshair.Style st = xh.shape();
        Crosshair.Style[] styles = Crosshair.Style.values();
        int sg = this.S(6.0);
        int sw = (iw - sg * 2) / 3;
        int sh = this.S(48.0);
        for (int i = 0; i < styles.length; i++) {
            Crosshair.Style s = styles[i];
            Crosshair.Spec look = new Crosshair.Spec(
                    s, -1, s == Crosshair.Style.DOT ? 4 : 5, 2, 2, true, 1.0F, 1, 1.0F, false, false, 2, 10);
            this.shape(
                    g,
                    "xst:" + i,
                    ix + (i % 3) * (sw + sg),
                    ry + (i / 3) * (sh + sg),
                    sw,
                    sh,
                    st == s,
                    look,
                    Lang.tr(s.title),
                    () -> {
                        xh.style.set(s.title);
                        Config.dirty();
                        UiSound.play("tab");
                    });
        }
        ry += ((styles.length + 2) / 3) * (sh + sg) + this.S(8.0);

        // the quick colours: plain dots, the chosen one ringed
        Px.text(g, Lang.tr("Быстрые цвета"), ix, ry, Px.Weight.SEMIBOLD, this.S(11.0), ArcanicMenu.faint());
        ry += this.S(18.0);
        int n = Crosshair.SWATCHES.length;
        int d = Math.min(this.S(20.0), (iw - sg * (n - 1)) / n);
        int dg = n > 1 ? (iw - d * n) / (n - 1) : 0;
        for (int i = 0; i < n; i++) {
            int c = Crosshair.SWATCHES[i] | 0xFF000000;
            int sx = ix + i * (d + dg);
            boolean on = !xh.colour.followsTheme() && (xh.colour.argb() | 0xFF000000) == c;
            boolean hv = !this.menu.under() && this.menu.hover(sx, ry, d, d);
            if (hv && !on) this.menu.hot("xsw:" + i);
            float hk = this.menu.an("xsw:" + i, hv ? 1.0F : 0.0F, 18.0F);
            float ak = this.menu.tween("xswOn:" + i, on ? 1.0F : 0.0F, 160.0F);
            int ring = Math.max(1, this.S(2.0));
            if (ak > 0.01F) {
                int o = ring * 2;
                Px.round(
                        g,
                        sx - o,
                        ry - o,
                        d + o * 2,
                        d + o * 2,
                        (d + o * 2) / 2,
                        Px.a(ArcanicMenu.acc(), Math.round(255 * ak)));
                Px.round(g, sx - ring, ry - ring, d + ring * 2, d + ring * 2, (d + ring * 2) / 2, ArcanicMenu.cardBg());
            }
            int shrink = Math.round(this.S(1.5) * (1.0F - hk));
            Px.round(g, sx + shrink, ry + shrink, d - shrink * 2, d - shrink * 2, (d - shrink * 2) / 2, c);
            Px.outline(
                    g,
                    sx + shrink,
                    ry + shrink,
                    d - shrink * 2,
                    d - shrink * 2,
                    (d - shrink * 2) / 2,
                    ArcanicMenu.hairline(30));
            this.menu.clipped(
                    sx,
                    ry,
                    d,
                    d,
                    0.0F,
                    t,
                    b,
                    () -> {
                        xh.colour.setTheme(false);
                        xh.colour.set(c);
                        Config.dirty();
                        UiSound.play("click");
                    },
                    null);
        }
        ry += d + this.S(14.0);

        // every setting, scrolling under the divider
        int sb = b - this.S(8.0);
        Px.rect(g, rx, ry - this.S(6.0), rw, Math.max(1, this.S(1.0)), ArcanicMenu.edgeSmall());
        List<Setting<?>> list = new ArrayList<>(ArcanicMenu.params(xh));
        this.menu.compact = true;
        int inner = 0;
        for (Setting<?> s : list) inner += this.menu.rowH(s, iw);
        float sc = (float) Math.round(this.menu.scrolled(this.scrollKey(), inner + this.S(8.0), ry, sb));
        g.enableScissor(rx, ry, rx + rw, sb);
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -sc);
        this.menu.settings(g, xh, list, ix, ry, iw, sc, ry, sb, !this.menu.under());
        g.pose().popMatrix();
        g.disableScissor();
        this.menu.compact = false;
        g.nextStratum();
        this.menu.scrollbar(
                g,
                rx + rw - this.S(8.0),
                ry + this.S(4.0),
                sb - this.S(4.0),
                inner + this.S(8.0),
                sc,
                this.scrollKey());
    }

    /** A shape tile: the picture on the field, the name under it; the chosen one tinted and edged in the accent. */
    private void shape(
            GuiGraphics g,
            String id,
            int x,
            int y,
            int w,
            int h,
            boolean active,
            Crosshair.Spec look,
            String name,
            Runnable click) {
        boolean hv = !this.menu.under() && this.menu.hover(x, y, w, h);
        if (hv && !active) this.menu.hot(id);
        float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
        float ak = this.menu.tween(id + ":on", active ? 1.0F : 0.0F, 180.0F);
        int r = this.S(8.0);
        Px.round(
                g,
                x,
                y,
                w,
                h,
                r,
                Px.mix(Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), hk), Px.a(ArcanicMenu.acc(), 70), ak * 0.5F));
        if (ak > 0.01F) Px.outline(g, x, y, w, h, r, Px.a(ArcanicMenu.acc(), Math.round(220 * ak)));
        int nameH = this.S(15.0);
        int box = Math.min(w, h - nameH) - this.S(12.0);
        Crosshair.icon(g, look, x + w / 2.0F, y + this.S(4.0) + (h - nameH - this.S(4.0)) / 2.0F, box);
        int fs = this.S(10.5);
        Px.textCenter(
                g,
                Px.ellipsize(name, Px.Weight.MEDIUM, fs, w - this.S(8.0)),
                x + w / 2,
                y + h - nameH + (nameH - fs) / 2 - this.S(2.0),
                Px.Weight.MEDIUM,
                fs,
                Px.mix(ArcanicMenu.sub(), ArcanicMenu.T().text(), Math.max(hk, ak)));
        this.menu.clipped(x, y, w, h, 0.0F, this.menu.top, this.menu.bottom, click, null);
    }

    // ------------------------------------------------------------------ sharing

    private void copy() {
        Crosshair xh = Modules.CROSSHAIR;
        String me = Minecraft.getInstance().getUser().getName();
        Minecraft.getInstance()
                .keyboardHandler
                .setClipboard(Crosshair.encode(xh.current(), Lang.tr("Прицел") + " " + me));
        UiSound.play("click");
        Social.note(Lang.tr("Код прицела скопирован"), true);
    }

    private void paste() {
        String raw = Minecraft.getInstance().keyboardHandler.getClipboard();
        Optional<Crosshair.Spec> spec = Crosshair.decode(raw);
        if (spec.isEmpty()) {
            UiSound.play("reset");
            Social.note(Lang.tr("Код прицела не распознан: скопируй код Arcanic, Evolution или Valorant"), false);
            return;
        }
        Crosshair xh = Modules.CROSSHAIR;
        xh.apply(spec.get());
        if (!xh.enabled()) xh.setEnabled(true);
        String name = Crosshair.nameOf(raw);
        boolean kept = xh.keep(name == null ? Lang.tr("Из кода") : name, spec.get());
        UiSound.play("select");
        Social.note(Lang.tr(kept ? "Прицел применён и сохранён" : "Прицел применён: мои пресеты заполнены"), true);
    }

    private void send() {
        List<Social.Friend> online = new ArrayList<>();
        for (Social.Friend f : Social.friends()) if (f.online()) online.add(f);
        if (online.isEmpty()) {
            UiSound.play("reset");
            Social.note(Lang.tr(Social.ready() ? "Нет друзей в сети" : "Нужен вход: войди в Arcanic Social"), false);
            return;
        }
        List<String> names = new ArrayList<>();
        for (Social.Friend f : online) names.add(f.name());
        UiSound.play("click");
        Minecraft.getInstance().setScreen(PromptScreen.pick(this.menu, "Отправить прицел", names, 0, "Отправить", i -> {
            Crosshair xh = Modules.CROSSHAIR;
            String me = Minecraft.getInstance().getUser().getName();
            Social.sendCrosshair(online.get(i), Crosshair.encode(xh.current(), Lang.tr("Прицел") + " " + me));
            UiSound.play("friend_sent");
        }));
    }
}
