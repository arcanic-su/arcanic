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
import net.arcanic.visuals.core.*;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Frost;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.gui.GuiGraphics;

/** The window a module's settings open in. */
final class ModulePopup {
    private final ArcanicMenu menu;

    ModulePopup(ArcanicMenu menu) {
        this.menu = menu;
    }

    void modulePopup(GuiGraphics g) {
        float k = menu.an("pm", menu.popMod != null ? 1 : 0, menu.popMod != null ? 15 : 22);
        if (menu.popMod != null) menu.popShown = menu.popMod;
        else if (k < .01f) menu.popShown = null;
        Module m = menu.popShown;
        if (m == null) return;
        float a = menu.ease(k);
        boolean live = menu.popMod != null && !menu.blocked() && k > .5f;
        int sw = Px.width(), sh = Px.height();
        List<Setting<?>> list = new ArrayList<>();
        for (Setting<?> s : m.settings) if (s.visible()) list.add(s);
        menu.compact = true;
        int w = menu.S(300), pad = menu.S(18), cw = w - pad * 2, head = menu.S(66);
        int inner = head;
        for (Setting<?> s : list) inner += menu.rowH(s, cw);
        inner += menu.S(8) + menu.S(40) + menu.S(8);
        int h = Math.min(inner, Math.min(menu.ph, sh - menu.S(24)));
        boolean fitsR = menu.px + menu.pw + menu.S(14) + w <= sw - menu.S(10),
                fitsL = menu.px - menu.S(14) - w >= menu.S(10);
        boolean leftColumn = menu.ancX + menu.ancW / 2 < menu.px + menu.pw / 2;
        boolean right = leftColumn ? !fitsL : (fitsR || !fitsL);
        int x = right ? menu.px + menu.pw + menu.S(14) : menu.px - menu.S(14) - w;
        if (x < menu.S(10)) {
            right = true;
            x = Math.min(sw - w - menu.S(10), menu.ancX + menu.ancW + menu.S(10));
        }
        int y = Math.max(
                Math.max(menu.S(12), menu.py),
                Math.min(Math.min(sh - menu.S(12), menu.py + menu.ph) - h, menu.ancY - menu.S(6)));
        menu.pmX = x;
        menu.pmY = y;
        menu.pmW = w;
        menu.pmH = h;
        float kf = Px.fade;
        Px.fade = kf * Math.min(1, k * 1.4f);
        g.pose().pushMatrix();
        if (a < .999f) g.pose().translate((1 - a) * (right ? -menu.S(14) : menu.S(14)), 0);
        boolean lt = menu.light();
        int bg = lt ? 0xFFFFFFFF : 0xF40C0D12;
        Px.shadow(g, x, y + menu.S(6), w, h, menu.S(18), Px.a(0, lt ? 30 : 70));
        if (!lt && false) {
            Frost.backdrop(g, x, y, w, h, menu.S(13));
            bg = 0xDC0C0D12;
        }
        Px.round(g, x, y, w, h, menu.S(13), bg);
        Px.outline(g, x, y, w, h, menu.S(13), lt ? Px.a(menu.T().text(), 26) : 0x18FFFFFF);
        // a thin tie towards the row it belongs to
        int tieY = Math.max(y + menu.S(16), Math.min(y + h - menu.S(16), menu.ancY + menu.ancH / 2));
        if (right)
            Px.rect(
                    g,
                    menu.px + menu.pw,
                    tieY,
                    x - menu.px - menu.pw,
                    Math.max(1, menu.S(1)),
                    Px.a(Theme.accent(), (int) (120 * a)));
        else Px.rect(g, x + w, tieY, menu.px - x - w, Math.max(1, menu.S(1)), Px.a(Theme.accent(), (int) (120 * a)));
        int t = y + menu.S(3), b = y + h - menu.S(3);
        float sc = Math.round(menu.scrolled("pm:" + m.id, inner, y, y + h));
        g.enableScissor(x, t, x + w, b);
        g.pose().pushMatrix();
        g.pose().translate(0, -sc);
        int cx = x + pad;
        // header like a module card: the name, what it does under it, and the module's own switch on the right
        int tgW = menu.S(34);
        Px.text(
                g,
                Px.ellipsize(Lang.tr(m.name), Weight.SEMIBOLD, menu.S(15), cw - tgW - menu.S(12)),
                cx,
                y + menu.S(15),
                Weight.SEMIBOLD,
                menu.S(15),
                menu.T().text());
        Px.text(
                g,
                Px.ellipsize(Lang.tr(m.description), Weight.MEDIUM, menu.S(11), cw - tgW - menu.S(12)),
                cx,
                y + menu.S(36),
                Weight.MEDIUM,
                menu.S(11),
                menu.lt(menu.T().dim(), 0xFF757D8A));
        float mOn = menu.tween("on:" + m.id, m.enabled() ? 1 : 0, 180);
        boolean tgHv = live && menu.hover(cx + cw - tgW, Math.round(y + menu.S(12) - sc), tgW, menu.S(30));
        menu.toggle(g, cx + cw - tgW, y + menu.S(12), tgW, menu.S(30), mOn, tgHv ? 1 : 0);
        if (live)
            menu.clipped(
                    cx + cw - tgW - menu.S(4),
                    y + menu.S(8),
                    tgW + menu.S(8),
                    menu.S(38),
                    sc,
                    t,
                    b,
                    () -> {
                        m.toggle();
                        if (m.enabled()) menu.pulse.put(m.id, menu.now);
                        UiSound.play(m.enabled() ? "charge" : "drain");
                        Toasts.module(m, false);
                        Config.dirty();
                    },
                    null);
        Px.rect(
                g,
                cx,
                y + head - menu.S(4),
                cw,
                Math.max(1, menu.S(1)),
                lt ? Px.a(menu.T().text(), 18) : 0x12FFFFFF);
        int cy = menu.settings(g, m, list, cx, y + head, cw, sc, t, b, live);
        cy += menu.S(8);
        Px.rect(g, cx, cy, cw, Math.max(1, menu.S(1)), Px.a(menu.T().text(), lt ? 20 : 14));
        boolean cap = menu.binding == m;
        int rh = menu.S(40);
        boolean khv = live && menu.my >= t && menu.my < b && menu.hover(cx, Math.round(cy - sc), cw, rh);
        if (khv) menu.hot("bind");
        Px.text(
                g,
                Lang.tr("Клавиша"),
                cx,
                cy + (rh - menu.S(13)) / 2,
                Weight.MEDIUM,
                menu.S(13),
                menu.T().muted());
        String kt = cap ? Lang.tr("Нажми клавишу") : m.bind >= 0 ? Keys.label(m.bind) : "—";
        menu.valueBox(g, kt, cx + cw, cy + (rh - menu.S(26)) / 2, cap, khv);
        if (live)
            menu.clipped(
                    cx,
                    cy,
                    cw,
                    rh,
                    sc,
                    t,
                    b,
                    () -> {
                        menu.binding = cap ? null : m;
                        UiSound.play("click");
                    },
                    () -> {
                        m.bind = -1;
                        menu.binding = null;
                        Config.dirty();
                        UiSound.play("reset");
                    });
        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        if (inner > h) {
            menu.edges(g, x + menu.S(1), w - menu.S(2), t, b, sc, bg);
            menu.scrollbar(g, x + w - menu.S(7), t + menu.S(8), b - menu.S(8), inner, sc, "pm:" + m.id);
        }
        g.pose().popMatrix();
        Px.fade = kf;
        menu.compact = false;
    }
}
