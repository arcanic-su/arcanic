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
import net.arcanic.visuals.core.Keys;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.gui.GuiGraphics;

/** Small cards in the bottom-right corner when a module is switched by its bind. */
public final class Toasts {
    private Toasts() {}

    public record Toast(String title, String key, boolean on, long born) {}

    private static final List<Toast> LIST = new ArrayList<>();
    public static final long LIFE = 2400;

    public static void module(Module m) {
        module(m, true);
    }

    public static void module(Module m, boolean sound) {
        if (Notify.module(m)) return; // the pop-ups under the crosshair take module switches when they are on
        if (!Modules.PREFS.notify.on()) return;
        LIST.removeIf(t -> t.title.equals(m.name));
        LIST.add(new Toast(m.name, Keys.label(m.bind), m.enabled(), System.currentTimeMillis()));
        while (LIST.size() > 4) LIST.remove(0);
        if (sound) UiSound.play(m.enabled() ? "toggle_on" : "toggle_off", 1, .8f);
    }

    public static void menu(boolean open) {
        if (!Modules.PREFS.notify.on()) return;
        LIST.removeIf(t -> t.key.equals("menu"));
        LIST.add(new Toast(open ? "Меню открыто" : "Меню закрыто", "menu", open, System.currentTimeMillis()));
        while (LIST.size() > 4) LIST.remove(0);
    }

    public static void setting(String title, boolean on) {
        if (Notify.text(title, on)) return;
        if (!Modules.PREFS.notify.on()) return;
        LIST.removeIf(t -> t.title.equals(title));
        LIST.add(new Toast(title, "", on, System.currentTimeMillis()));
        while (LIST.size() > 4) LIST.remove(0);
    }

    public static List<Toast> active() {
        LIST.removeIf(t -> System.currentTimeMillis() - t.born > LIFE);
        return Modules.PREFS.notify.on() ? List.copyOf(LIST) : List.of();
    }

    /** Called inside pixel space; cards are a fallback when the island is disabled. */
    public static void render(GuiGraphics g) {
        active();
        if (!Modules.PREFS.notify.on() || LIST.isEmpty()) return;
        for (var hud : Modules.HUD) if (hud instanceof DynamicIsland && hud.enabled()) return;
        long now = System.currentTimeMillis();
        LIST.removeIf(t -> now - t.born > LIFE);
        float k = (float) (Px.height() / 1080.0);
        int w = Math.round(214 * k), h = Math.round(46 * k), gap = Math.round(8 * k), r = Math.round(10 * k);
        int x0 = Px.width() - w - Math.round(18 * k), y = Px.height() - Math.round(18 * k) - h;
        Theme.Palette p = Theme.p();
        for (int i = LIST.size() - 1; i >= 0; i--) {
            Toast t = LIST.get(i);
            float age = now - t.born;
            float in = Math.min(1, age / 260f), out = Math.max(0, (age - (LIFE - 300)) / 300f);
            float slide = (1 - Anim.easeOutBack(in)) + out * out;
            int x = x0 + Math.round(slide * (w + 30 * k));
            float old = Px.fade;
            Px.fade = old * Math.min(1, in * 1.5f) * (1 - out);
            int acc = t.on ? Theme.accent() : 0xFF94A3B8;
            Px.shadow(g, x, y + Math.round(2 * k), w, h, Math.round(14 * k), Px.a(0, p.light() ? 50 : 120));
            Px.round(g, x, y, w, h, r, (p.bg() & 0xFFFFFF) | 0xEE000000);
            Px.outline(g, x, y, w, h, r, p.border());
            int ic = Math.round(28 * k);
            Px.round(g, x + Math.round(9 * k), y + (h - ic) / 2, ic, ic, Math.round(8 * k), Px.a(acc, 50));
            Px.icon(
                    g,
                    "power",
                    x + Math.round(9 * k) + (ic - Math.round(15 * k)) / 2,
                    y + (h - Math.round(15 * k)) / 2,
                    Math.round(15 * k),
                    t.on ? Theme.accent2() : 0xFF94A3B8);
            int tx = x + Math.round(46 * k);
            Px.text(
                    g,
                    Lang.tr(Px.ellipsize(
                            Lang.tr(t.title), Weight.SEMIBOLD, Math.round(13 * k), w - Math.round(56 * k))),
                    tx,
                    y + Math.round(7 * k),
                    Weight.SEMIBOLD,
                    Math.round(13 * k),
                    p.text());
            Px.text(
                    g,
                    Lang.tr(t.on ? "включён" : "выключен"),
                    tx,
                    y + Math.round(24 * k),
                    Weight.MEDIUM,
                    Math.round(11 * k),
                    t.on ? Theme.accent2() : p.muted());
            // time left
            float left = 1 - Math.min(1, age / (float) LIFE);
            int lw = w - 2 * r;
            Px.rect(
                    g,
                    x + r,
                    y + h - Math.max(2, Math.round(2 * k)),
                    Math.round(lw * left),
                    Math.max(1, Math.round(2 * k)),
                    Px.a(acc, 150));
            Px.fade = old;
            y -= h + gap;
        }
    }
}
