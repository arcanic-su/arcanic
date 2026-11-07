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
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.ThemeStudio;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Theme Studio: presets and colour rows on the left, live preview and the share code on the right. */
final class ThemesPage {
    private static final String SCROLL = "themes";
    private final ArcanicMenu menu;
    private int lastColours;
    private boolean seen;
    private int splitX;

    ThemesPage(ArcanicMenu menu) {
        this.menu = menu;
    }

    private int S(double v) {
        return this.menu.S(v);
    }

    String scrollKey(double mouseX) {
        return SCROLL;
    }

    private static ThemeStudio studio() {
        return Modules.THEMES;
    }

    /** Editing a colour turns the custom theme on, otherwise the change would not show. */
    private void followEdits() {
        int h = 17;
        for (Setting.Colour c : studio().colours()) h = h * 31 + c.get();
        h = h * 31 + studio().opacity.i();
        if (this.seen && h != this.lastColours && !studio().active()) {
            Theme.fadeFrom(Theme.p());
            studio().custom.set(true);
            Config.dirty();
        }
        this.seen = true;
        this.lastColours = h;
    }

    void draw(GuiGraphics g) {
        this.followEdits();
        int gap = this.S(18.0);
        int rw = Math.min(this.S(300.0), this.menu.cw0 / 2 - gap);
        int lw = this.menu.cw0 - rw - gap;
        int lx = this.menu.cx0;
        int rx = lx + lw + gap;
        this.splitX = rx;
        this.left(g, lx, lw);
        g.nextStratum();
        this.right(g, rx, rw);
    }

    // ---- left column

    private void left(GuiGraphics g, int x, int w) {
        int t = this.menu.top;
        int b = this.menu.bottom;
        int cols = w > this.S(520.0) ? 4 : 3;
        int tg = this.S(8.0);
        int tw = (w - tg * (cols - 1)) / cols;
        int th = this.S(62.0);
        List<ThemeStudio.Preset> presets = ThemeStudio.PRESETS;
        int rows = (presets.size() + cols - 1) / cols;
        List<Setting<?>> list = new ArrayList<>(studio().settings);
        int settingsH = 0;
        for (Setting<?> s : list) settingsH += this.menu.rowH(s, w);
        int headH = this.S(28.0);
        int total = headH + rows * th + (rows - 1) * tg + this.S(22.0) + headH + settingsH + this.S(10.0);
        float sc = (float) Math.round(this.menu.scrolled(SCROLL, total, t, b));
        g.enableScissor(x - this.S(12.0), t, x + w + this.S(12.0), b);
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -sc);
        int y = t;
        this.heading(g, Lang.tr("Готовые темы"), x, y, headH);
        y += headH;
        for (int i = 0; i < presets.size(); i++) {
            int cx = x + (i % cols) * (tw + tg);
            int cy = y + (i / cols) * (th + tg);
            g.nextStratum();
            this.preset(g, presets.get(i), cx, cy, tw, th, sc, t, b);
        }
        y += rows * th + (rows - 1) * tg + this.S(22.0);
        this.heading(g, Lang.tr("Цвета"), x, y, headH);
        y += headH;
        this.menu.settings(g, studio(), list, x, y, w, sc, t, b, !this.menu.under());
        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        this.menu.edges(g, x - this.S(12.0), w + this.S(24.0), t, b, sc, this.menu.baseColour());
        this.menu.scrollbar(g, x + w + this.S(6.0), t, b, total, sc, SCROLL);
    }

    private void heading(GuiGraphics g, String text, int x, int y, int h) {
        int fs = this.S(13.0);
        Px.text(
                g,
                text,
                x,
                y + (h - fs) / 2 - this.S(2.0),
                Px.Weight.SEMIBOLD,
                fs,
                ArcanicMenu.T().text());
    }

    private void preset(GuiGraphics g, ThemeStudio.Preset p, int x, int y, int w, int h, float sc, int t, int b) {
        boolean on = studio().active() && studio().is(p);
        boolean hv = !this.menu.under()
                && this.menu.my >= t
                && this.menu.my < b
                && this.menu.hover(x, Math.round(y - sc), w, h);
        String id = "themePreset:" + p.name();
        if (hv) this.menu.hot(id);
        float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
        float ok = this.menu.tween(id + ":on", on ? 1.0F : 0.0F, 200.0F);
        int r = this.S(10.0);
        // mini menu: background, sidebar, a card, both accents
        Px.round(g, x, y, w, h, r, p.panel());
        int side = Math.max(this.S(10.0), w / 6);
        Px.round(g, x, y, side + r, h, r, p.sidebar());
        Px.rect(g, x + side, y, r, h, p.panel());
        int pad = this.S(8.0);
        int dot = this.S(10.0);
        Px.round(g, x + (side - dot) / 2, y + pad, dot, dot, dot / 2, p.a());
        int cardX = x + side + pad;
        int cardW = w - side - pad * 2;
        int cardH = this.S(14.0);
        Px.round(g, cardX, y + pad, cardW, cardH, this.S(4.0), p.card());
        int bar = Math.max(2, this.S(3.0));
        Px.round(g, cardX + this.S(5.0), y + pad + (cardH - bar) / 2, cardW / 2, bar, bar / 2, p.muted());
        int knobW = this.S(14.0);
        Px.round(
                g,
                cardX + cardW - knobW - this.S(4.0),
                y + pad + this.S(3.0),
                knobW,
                cardH - this.S(6.0),
                this.S(4.0),
                p.a());
        int fs = this.S(11.0);
        Px.text(
                g,
                Px.ellipsize(Lang.tr(p.name()), Px.Weight.SEMIBOLD, fs, cardW),
                cardX,
                y + h - pad - fs,
                Px.Weight.SEMIBOLD,
                fs,
                p.text());
        int sw = this.S(8.0);
        Px.round(g, x + w - pad - sw, y + h - pad - sw, sw, sw, sw / 2, p.b());
        int edge = Px.mix(Px.mix(ArcanicMenu.hairline(18), ArcanicMenu.hairline(40), hk), ArcanicMenu.acc(), ok);
        Px.outline(g, x, y, w, h, r, edge);
        if (ok > 0.01F) Px.outline(g, x + 1, y + 1, w - 2, h - 2, r - 1, Px.a(ArcanicMenu.acc(), (int) (150 * ok)));
        if (!this.menu.under()) {
            this.menu.clipped(
                    x,
                    y,
                    w,
                    h,
                    sc,
                    t,
                    b,
                    () -> {
                        studio().apply(p);
                        this.seen = false;
                        Config.dirty();
                        UiSound.play("select");
                        Toasts.setting(Lang.tr("Тема") + ": " + Lang.tr(p.name()), true);
                    },
                    null);
        }
    }

    // ---- right column

    private void right(GuiGraphics g, int x, int w) {
        int y = this.menu.top;
        ThemeStudio s = studio();
        int ph = Math.min(this.S(250.0), (this.menu.bottom - y) / 2 + this.S(40.0));
        this.preview(g, s, x, y, w, ph);
        y += ph + this.S(14.0);
        int bh = this.S(32.0);
        int gap = this.S(8.0);
        this.button(g, "themeCopy", "w.copy", Lang.tr("Скопировать код темы"), x, y, w, bh, true, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(s.code());
            UiSound.play("select");
            Toasts.setting(Lang.tr("Код темы скопирован: отправь его другу"), true);
        });
        y += bh + gap;
        this.button(g, "themePaste", "w.download", Lang.tr("Вставить код из буфера"), x, y, w, bh, false, () -> {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (s.load(clip)) {
                this.seen = false;
                Config.dirty();
                UiSound.play("select");
                Toasts.setting(Lang.tr("Тема из кода применена"), true);
            } else {
                UiSound.play("reset");
                Toasts.setting(Lang.tr("В буфере нет кода темы"), false);
            }
        });
        y += bh + gap;
        this.button(g, "themeReset", "w.undo", Lang.tr("Вернуть стандартную"), x, y, w, bh, false, () -> {
            s.resetAll();
            this.seen = false;
            Config.dirty();
            UiSound.play("reset");
            Toasts.setting(Lang.tr("Стандартная тема"), false);
        });
        y += bh + this.S(14.0);
        String hint = Lang.tr(
                "Нажми на цвет слева, чтобы выбрать свой. Тема сохраняется в конфиге, а код можно отправить другу.");
        int fs = this.S(11.0);
        for (String line : PromptScreen.wrap(hint, Px.Weight.MEDIUM, fs, w)) {
            if (y + fs > this.menu.bottom) break;
            Px.text(g, line, x, y, Px.Weight.MEDIUM, fs, ArcanicMenu.faint());
            y += this.S(16.0);
        }
    }

    private void preview(GuiGraphics g, ThemeStudio s, int x, int y, int w, int h) {
        int r = this.S(12.0);
        int panel = s.panelBg();
        int text = s.text.get() | 0xFF000000;
        int acc = s.accent.get() | 0xFF000000;
        int acc2 = s.accent2.get() | 0xFF000000;
        Px.shadow(g, x, y + this.S(8.0), w, h, this.S(24.0), Px.a(0, 110));
        Px.round(g, x, y, w, h, r, panel);
        int side = Math.round(w * 0.3F);
        Px.round(g, x, y, side + r, h, r, s.chrome());
        Px.rect(g, x + side, y, r, h, panel);
        Px.outline(g, x, y, w, h, r, Px.a(text, 26));
        // sidebar
        int pad = this.S(10.0);
        int ls = this.S(14.0);
        Px.round(g, x + pad, y + pad, ls, ls, this.S(4.0), acc);
        Px.text(g, "Arcanic", x + pad + ls + this.S(6.0), y + pad + this.S(2.0), Px.Weight.BOLD, this.S(11.0), text);
        int iy = y + pad + ls + this.S(14.0);
        int ih = this.S(18.0);
        String[] items = {"Visuals", "Utilities", "HUD", "Configs", "Lua"};
        for (int i = 0; i < items.length && iy + ih < y + h - pad; i++) {
            boolean on = i == 0;
            if (on) Px.round(g, x + this.S(6.0), iy, side - this.S(12.0), ih, ih / 2, acc);
            Px.round(
                    g,
                    x + this.S(14.0),
                    iy + (ih - this.S(7.0)) / 2,
                    this.S(7.0),
                    this.S(7.0),
                    this.S(2.0),
                    on ? -1 : s.faint());
            Px.text(
                    g,
                    items[i],
                    x + this.S(27.0),
                    iy + (ih - this.S(10.0)) / 2,
                    Px.Weight.MEDIUM,
                    this.S(10.0),
                    on ? -1 : s.sub());
            iy += ih + this.S(3.0);
        }
        // content
        int cx = x + side + pad;
        int cw = w - side - pad * 2;
        Px.text(g, "Visuals", cx, y + pad + this.S(1.0), Px.Weight.SEMIBOLD, this.S(12.0), text);
        int sy = y + pad;
        int sw2 = Math.min(this.S(70.0), cw / 2);
        Px.round(g, cx + cw - sw2, sy - this.S(2.0), sw2, this.S(16.0), this.S(5.0), s.fieldBg());
        int cy = y + pad + this.S(24.0);
        int ch = this.S(34.0);
        for (int i = 0; i < 3 && cy + ch <= y + h - pad; i++) {
            boolean on = i != 1;
            Px.round(g, cx, cy, cw, ch, this.S(8.0), i == 0 ? s.cardHi() : s.cardBg());
            Px.outline(g, cx, cy, cw, ch, this.S(8.0), Px.a(text, 18));
            Px.text(
                    g,
                    i == 0 ? "Hit Effects" : (i == 1 ? "Trails" : "Crosshair"),
                    cx + this.S(9.0),
                    cy + this.S(7.0),
                    Px.Weight.SEMIBOLD,
                    this.S(10.5),
                    text);
            int bar = Math.max(2, this.S(3.0));
            Px.round(g, cx + this.S(9.0), cy + ch - this.S(10.0), cw / 3, bar, bar / 2, s.fieldBg());
            Px.round(
                    g,
                    cx + this.S(9.0),
                    cy + ch - this.S(10.0),
                    Math.round(cw / 3F * (0.35F + 0.25F * i)),
                    bar,
                    bar / 2,
                    i == 2 ? acc2 : acc);
            int tw = this.S(24.0);
            int tht = this.S(14.0);
            int tx = cx + cw - tw - this.S(9.0);
            int ty = cy + (ch - tht) / 2;
            Px.round(g, tx, ty, tw, tht, tht / 2, on ? acc : s.fieldBg());
            int k = tht - this.S(4.0);
            Px.round(
                    g,
                    on ? tx + tw - k - this.S(2.0) : tx + this.S(2.0),
                    ty + this.S(2.0),
                    k,
                    k,
                    k / 2,
                    on ? -1 : s.faint());
            cy += ch + this.S(6.0);
        }
        String state = s.active() ? Lang.tr("Своя тема включена") : Lang.tr("Сейчас стандартная тема");
        int fs = this.S(10.0);
        Px.textRight(g, state, x + w - pad, y + h - pad - fs, Px.Weight.MEDIUM, fs, s.faint());
    }

    private void button(
            GuiGraphics g,
            String id,
            String icon,
            String text,
            int x,
            int y,
            int w,
            int h,
            boolean primary,
            Runnable run) {
        boolean hv = !this.menu.under() && this.menu.hover(x, y, w, h);
        if (hv) this.menu.hot(id);
        float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
        int bg = primary
                ? Px.mix(ArcanicMenu.acc(), -1, 0.1F * hk)
                : Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), hk);
        Px.round(g, x, y, w, h, this.S(8.0), bg);
        int tc = primary ? -1 : Px.mix(ArcanicMenu.label(), ArcanicMenu.T().text(), hk);
        int is = this.S(14.0);
        int fs = this.S(12.0);
        ArcanicMenu.glyph(g, icon, x + this.S(12.0), y + (h - is) / 2, is, tc);
        Px.text(g, text, x + this.S(12.0) + is + this.S(8.0), y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, tc);
        if (!this.menu.under()) {
            this.menu.clipped(x, y, w, h, 0.0F, this.menu.top, this.menu.bottom, run, null);
        }
    }
}
