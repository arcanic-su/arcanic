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
import net.minecraft.client.gui.GuiGraphics;

public final class Toasts {
    private static final List<Toast> LIST = new ArrayList<>();
    public static final long LIFE = 2400L;

    private Toasts() {}

    public static void module(Module m) {
        module(m, true);
    }

    public static void module(Module m, boolean sound) {
        DynamicIsland.module(m);
        if (!Notify.module(m)) {
            if (Modules.PREFS.notify.on()) {
                LIST.removeIf((t) -> t.title.equals(m.title()));
                LIST.add(new Toast(m.title(), Keys.label(m.bind), m.enabled(), System.currentTimeMillis()));

                while (LIST.size() > 4) {
                    LIST.remove(0);
                }

                if (sound) {
                    UiSound.play(m.enabled() ? "toggle_on" : "toggle_off", 1.0F, 0.8F);
                }
            }
        }
    }

    public static void menu(boolean open) {
        if (Modules.PREFS.notify.on()) {
            LIST.removeIf((t) -> t.key.equals("menu"));
            LIST.add(new Toast(open ? "Меню открыто" : "Меню закрыто", "menu", open, System.currentTimeMillis()));

            while (LIST.size() > 4) {
                LIST.remove(0);
            }
        }
    }

    public static void setting(String title, boolean on) {
        DynamicIsland.notice(title, on);
        if (!Notify.text(title, on)) {
            if (Modules.PREFS.notify.on()) {
                LIST.removeIf((t) -> t.title.equals(title));
                LIST.add(new Toast(title, "", on, System.currentTimeMillis()));

                while (LIST.size() > 4) {
                    LIST.remove(0);
                }
            }
        }
    }

    public static List<Toast> active() {
        LIST.removeIf((t) -> System.currentTimeMillis() - t.born > 2400L);
        return Modules.PREFS.notify.on() ? List.copyOf(LIST) : List.of();
    }

    public static void render(GuiGraphics g) {
        active();
        if (Modules.PREFS.notify.on() && !LIST.isEmpty()) {
            for (HudModule hud : Modules.HUD) {
                if (hud instanceof DynamicIsland && hud.enabled()) {
                    return;
                }
            }

            long now = System.currentTimeMillis();
            LIST.removeIf((txx) -> now - txx.born > 2400L);
            float k = (float) ((double) Px.height() / 1080.0);
            int w = Math.round(214.0F * k);
            int h = Math.round(46.0F * k);
            int gap = Math.round(8.0F * k);
            int r = Math.round(10.0F * k);
            int x0 = Px.width() - w - Math.round(18.0F * k);
            int y = Px.height() - Math.round(18.0F * k) - h;
            Theme.Palette p = Theme.p();

            for (int i = LIST.size() - 1; i >= 0; --i) {
                Toast t = (Toast) LIST.get(i);
                float age = (float) (now - t.born);
                float in = Math.min(1.0F, age / 260.0F);
                float out = Math.max(0.0F, (age - 2100.0F) / 300.0F);
                float slide = 1.0F - Anim.easeOutBack(in) + out * out;
                int x = x0 + Math.round(slide * ((float) w + 30.0F * k));
                float old = Px.fade;
                Px.fade = old * Math.min(1.0F, in * 1.5F) * (1.0F - out);
                int acc = t.on ? Theme.accent() : -7035976;
                Px.shadow(g, x, y + Math.round(2.0F * k), w, h, Math.round(14.0F * k), Px.a(0, p.light() ? 50 : 120));
                Px.round(g, x, y, w, h, r, p.bg() & 0xFFFFFF | 0xEE000000);
                Px.outline(g, x, y, w, h, r, p.border());
                int ic = Math.round(28.0F * k);
                Px.round(g, x + Math.round(9.0F * k), y + (h - ic) / 2, ic, ic, Math.round(8.0F * k), Px.a(acc, 50));
                Px.icon(
                        g,
                        "power",
                        x + Math.round(9.0F * k) + (ic - Math.round(15.0F * k)) / 2,
                        y + (h - Math.round(15.0F * k)) / 2,
                        Math.round(15.0F * k),
                        t.on ? Theme.accent2() : -7035976);
                int tx = x + Math.round(46.0F * k);
                Px.text(
                        g,
                        Lang.tr(Px.ellipsize(
                                Lang.tr(t.title),
                                Px.Weight.SEMIBOLD,
                                Math.round(13.0F * k),
                                w - Math.round(56.0F * k))),
                        tx,
                        y + Math.round(7.0F * k),
                        Px.Weight.SEMIBOLD,
                        Math.round(13.0F * k),
                        p.text());
                Px.text(
                        g,
                        Lang.tr(t.on ? "включён" : "выключен"),
                        tx,
                        y + Math.round(24.0F * k),
                        Px.Weight.MEDIUM,
                        Math.round(11.0F * k),
                        t.on ? Theme.accent2() : p.muted());
                float left = 1.0F - Math.min(1.0F, age / 2400.0F);
                int lw = w - 2 * r;
                Px.rect(
                        g,
                        x + r,
                        y + h - Math.max(2, Math.round(2.0F * k)),
                        Math.round((float) lw * left),
                        Math.max(1, Math.round(2.0F * k)),
                        Px.a(acc, 150));
                Px.fade = old;
                y -= h + gap;
            }
        }
    }

    public static record Toast(String title, String key, boolean on, long born) {}
}
