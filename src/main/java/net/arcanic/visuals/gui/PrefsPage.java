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
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.gui.GuiGraphics;

/** The client's own settings page: theme, language, sounds and the rest. */
final class PrefsPage {
    private final ArcanicMenu menu;

    PrefsPage(ArcanicMenu menu) {
        this.menu = menu;
    }

    void prefs(GuiGraphics g) {
        List<ArcanicMenu.Group> groups = prefGroups();
        int gap = menu.S(12), cw = (menu.cw0 - gap) / 2;
        int[] colY = new int[2], gx = new int[groups.size()], gy = new int[groups.size()], gh = new int[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            int c = colY[0] <= colY[1] ? 0 : 1;
            gx[i] = menu.cx0 + c * (cw + gap);
            gy[i] = menu.top + menu.S(2) + colY[c];
            gh[i] = menu.groupH(groups.get(i), cw);
            colY[c] += gh[i] + gap;
        }
        int total = Math.max(colY[0], colY[1]) + menu.S(4);
        float sc = Math.round(menu.scrolled("prefs", total, menu.top, menu.bottom));
        g.enableScissor(menu.cx0 - menu.S(12), menu.top, menu.cx0 + menu.cw0 + menu.S(12), menu.bottom);
        g.pose().pushMatrix();
        g.pose().translate(0, -sc);
        for (int i = 0; i < groups.size(); i++) {
            ArcanicMenu.Group gr = groups.get(i);
            float app = menu.ease((menu.now - menu.contentAt - i * 40L) / 300f);
            float k = Px.fade;
            Px.fade = k * app;
            g.pose().pushMatrix();
            g.pose().translate(0, Math.round((1 - app) * menu.S(8)));
            g.nextStratum();
            int x = gx[i], y = gy[i], w = cw, h = gh[i], R = menu.S(12), pad = menu.S(18);
            boolean lt = menu.light();
            Px.round(g, x, y, w, h, R, menu.plate());
            Px.outline(g, x, y, w, h, R, lt ? Px.a(menu.T().text(), 22) : 0x18FFFFFF);
            // header: the group's icon in a soft square, its title and what it is about
            int ib = menu.S(34);
            Px.round(
                    g,
                    x + pad,
                    y + menu.S(15),
                    ib,
                    ib,
                    menu.S(10),
                    lt ? Px.a(Theme.accent(), 22) : Px.a(Theme.accent(), 34));
            menu.glyph(
                    g,
                    gr.icon(),
                    x + pad + (ib - menu.S(17)) / 2,
                    y + menu.S(15) + (ib - menu.S(17)) / 2,
                    menu.S(17),
                    menu.accText());
            Px.text(
                    g,
                    Lang.tr(gr.title()),
                    x + pad + ib + menu.S(12),
                    y + menu.S(16),
                    Weight.SEMIBOLD,
                    menu.S(15),
                    menu.T().text());
            Px.text(
                    g,
                    Lang.tr(gr.subtitle()),
                    x + pad + ib + menu.S(12),
                    y + menu.S(36),
                    Weight.MEDIUM,
                    menu.S(12),
                    menu.lt(menu.T().dim(), 0xFF757D8A));
            Px.rect(
                    g,
                    x + pad,
                    y + menu.S(62),
                    w - pad * 2,
                    Math.max(1, menu.S(1)),
                    lt ? Px.a(menu.T().text(), 18) : 0x12FFFFFF);
            List<Setting<?>> rows = new ArrayList<>();
            for (Setting<?> s : gr.rows()) if (s.visible()) rows.add(s);
            menu.settings(
                    g,
                    Modules.PREFS,
                    rows,
                    x + pad,
                    y + menu.S(64),
                    w - pad * 2,
                    sc,
                    menu.top,
                    menu.bottom,
                    app > .3f && !menu.under());
            g.pose().popMatrix();
            Px.fade = k;
        }
        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        menu.edges(g, menu.cx0 - menu.S(12), menu.cw0 + menu.S(24), menu.top, menu.bottom, sc, menu.baseColour());
        menu.scrollbar(g, menu.cx0 + menu.cw0 + menu.S(14), menu.top, menu.bottom, total, sc, "prefs");
    }

    List<ArcanicMenu.Group> prefGroups() {
        var p = Modules.PREFS;
        List<ArcanicMenu.Group> out = new ArrayList<>();
        out.add(new ArcanicMenu.Group("w.sliders", "Основное", "Язык интерфейса", List.of(p.language)));
        out.add(new ArcanicMenu.Group(
                "w.headset",
                "Звук",
                "Щелчки и отклик меню",
                List.of(p.sounds, p.volume, p.hoverSounds, p.sliderSounds)));
        out.add(new ArcanicMenu.Group(
                "w.swatch",
                "Вид",
                "Фон, свет и подсказки",
                List.of(p.blur, p.dim, p.spotlight, p.ripples, p.tips, p.notify)));
        out.add(new ArcanicMenu.Group(
                "w.bolt",
                "Поведение",
                "Анимации, размер и положение",
                List.of(p.animations, p.animSpeed, p.scale, p.drag, p.remember)));
        return out;
    }
}
