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

import java.util.List;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.lua.Script;
import net.arcanic.visuals.lua.Scripts;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;

/** Tools → Lua: the scripts with a switch each. The API reference is API.md in the scripts folder. */
final class LuaPage {
    private static final int GREEN = 0xFF4ADE80;
    private static final int RED = 0xFFF43F5E;
    private static final String SCROLL = "lua";
    private final ArcanicMenu menu;

    LuaPage(ArcanicMenu menu) {
        this.menu = menu;
    }

    private int S(double v) {
        return this.menu.S(v);
    }

    String scrollKey() {
        return SCROLL;
    }

    void draw(GuiGraphics g) {
        int x = this.menu.cx0;
        int w = this.menu.cw0;
        int y = this.actions(g, x, this.menu.top, w);
        this.scripts(g, x, y, w);
    }

    private int actions(GuiGraphics g, int x, int y, int w) {
        int bh = this.S(30.0);
        int gap = this.S(6.0);
        int right = x + w;
        right -= this.button(g, "luaReload", "w.refresh", Lang.tr("Перезагрузить"), right, y, bh, false, () -> {
                    Scripts.reload();
                    UiSound.play("select");
                    Toasts.setting(Lang.tr("Скрипты перезагружены"), true);
                })
                + gap;
        right -= this.button(g, "luaFolder", "w.folder", Lang.tr("Папка"), right, y, bh, false, () -> {
                    Scripts.start();
                    Util.getPlatform().openPath(Scripts.DIR);
                    UiSound.play("click");
                })
                + gap;
        right -= this.button(g, "luaDocs", "w.scroll", Lang.tr("Документация"), right, y, bh, false, () -> {
                    Scripts.start();
                    Util.getPlatform().openPath(Scripts.DOCS);
                    UiSound.play("click");
                })
                + gap;
        this.button(g, "luaNew", "plus", Lang.tr("Новый скрипт"), right, y, bh, true, () -> {
            String made = Scripts.create();
            if (made != null) {
                Util.getPlatform().openPath(Scripts.DIR);
                UiSound.play("select");
                Toasts.setting(Lang.tr("Создан") + " " + made, true);
            }
        });
        return y + bh + this.S(14.0);
    }

    private void scripts(GuiGraphics g, int x, int top, int w) {
        List<Script> list = Scripts.list();
        int b = this.menu.bottom;
        if (list.isEmpty()) {
            int cy = top + (b - top) / 2 - this.S(30.0);
            Px.textCenter(
                    g,
                    Lang.tr("Скриптов пока нет"),
                    x + w / 2,
                    cy,
                    Px.Weight.SEMIBOLD,
                    this.S(14.0),
                    ArcanicMenu.T().text());
            Px.textCenter(
                    g,
                    Lang.tr("Нажми «Новый скрипт» или положи .lua файлы в папку"),
                    x + w / 2,
                    cy + this.S(22.0),
                    Px.Weight.MEDIUM,
                    this.S(12.0),
                    ArcanicMenu.faint());
            return;
        }
        int rh = this.S(54.0);
        int gap = this.S(8.0);
        int total = list.size() * (rh + gap);
        float sc = (float) Math.round(this.menu.scrolled(SCROLL, total, top, b));
        g.enableScissor(x - this.S(12.0), top, x + w + this.S(12.0), b);
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -sc);
        int y = top;
        for (Script s : list) {
            g.nextStratum();
            this.row(g, s, x, y, w, rh, sc, top, b);
            y += rh + gap;
        }
        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        this.menu.edges(g, x - this.S(12.0), w + this.S(24.0), top, b, sc, this.menu.baseColour());
        this.menu.scrollbar(g, x + w + this.S(6.0), top, b, total, sc, SCROLL);
    }

    private void row(GuiGraphics g, Script s, int x, int y, int w, int h, float sc, int t, int b) {
        String id = "lua:" + s.name();
        boolean hv = !this.menu.under()
                && this.menu.my >= t
                && this.menu.my < b
                && this.menu.hover(x, Math.round(y - sc), w, h);
        if (hv) this.menu.hot(id);
        float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
        boolean on = Scripts.enabled(s);
        float ok = this.menu.tween(id + ":on", on ? 1.0F : 0.0F, 180.0F);
        int r = this.S(12.0);
        Px.round(g, x, y, w, h, r, Px.mix(ArcanicMenu.cardBg(), ArcanicMenu.cardHi(), hk));
        Px.outline(g, x, y, w, h, r, Px.mix(ArcanicMenu.hairline(14), ArcanicMenu.hairline(30), hk));
        this.menu.topLight(g, x, y, w, r);
        int pad = this.S(14.0);
        int is = this.S(18.0);
        ArcanicMenu.glyph(
                g, "w.scroll", x + pad, y + (h - is) / 2, is, Px.mix(ArcanicMenu.faint(), ArcanicMenu.acc(), ok));
        int tx = x + pad + is + this.S(12.0);
        int room = w - (tx - x) - this.S(80.0);
        Px.text(
                g,
                Px.ellipsize(s.name(), Px.Weight.SEMIBOLD, this.S(14.0), room),
                tx,
                y + this.S(10.0),
                Px.Weight.SEMIBOLD,
                this.S(14.0),
                ArcanicMenu.T().text());
        String state;
        int colour;
        if (!s.error().isEmpty()) {
            state = Lang.tr("Ошибка") + ": " + s.error();
            colour = RED;
        } else if (s.running()) {
            state = Lang.tr("Работает");
            colour = GREEN;
        } else {
            state = Lang.tr("Выключен");
            colour = ArcanicMenu.faint();
        }
        Px.text(
                g,
                Px.ellipsize(state, Px.Weight.MEDIUM, this.S(11.5), room),
                tx,
                y + this.S(31.0),
                Px.Weight.MEDIUM,
                this.S(11.5),
                colour);
        int tw = this.S(30.0);
        this.menu.toggle(g, x + w - pad - tw, y, tw, h, ok, hk);
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
                        Scripts.toggle(s);
                        UiSound.play(Scripts.enabled(s) ? "charge" : "drain");
                    },
                    null);
        }
    }

    /** Right-aligned at {@code right}, returns the width. */
    private int button(
            GuiGraphics g,
            String id,
            String icon,
            String text,
            int right,
            int y,
            int h,
            boolean primary,
            Runnable run) {
        int fs = this.S(12.0);
        int is = this.S(14.0);
        int w = this.S(11.0) + is + this.S(6.0) + Px.textWidth(text, Px.Weight.SEMIBOLD, fs) + this.S(12.0);
        int x = right - w;
        boolean hv = !this.menu.under() && this.menu.hover(x, y, w, h);
        if (hv) this.menu.hot(id);
        float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
        int bg = primary
                ? Px.mix(ArcanicMenu.acc(), -1, 0.1F * hk)
                : Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), hk);
        Px.round(g, x, y, w, h, this.S(8.0), bg);
        int tc = primary ? -1 : Px.mix(ArcanicMenu.sub(), ArcanicMenu.T().text(), hk);
        ArcanicMenu.glyph(g, icon, x + this.S(11.0), y + (h - is) / 2, is, tc);
        Px.text(g, text, x + this.S(11.0) + is + this.S(6.0), y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, tc);
        if (!this.menu.under()) {
            this.menu.clipped(x, y, w, h, 0.0F, this.menu.top, this.menu.bottom, run, null);
        }
        return w;
    }
}
