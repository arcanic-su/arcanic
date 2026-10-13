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

import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.render.Frost;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.gui.GuiGraphics;

/** A draggable HUD element, drawn in screen pixels. */
public abstract class HudModule extends Module {
    public double fx, fy;
    public int w = 120, h = 30;
    public final Setting.Num scale;
    public final Setting.Bool background;
    protected float k = 1;

    protected HudModule(String id, String name, String description, boolean enabled, double fx, double fy) {
        super(id, name, description, Category.HUD, enabled);
        this.fx = fx;
        this.fy = fy;
        scale = add(new Setting.Num("scale", "Размер", 1, .5, 2, .05, "×"));
        background = add(new Setting.Bool("bg", "Подложка", true));
    }

    public abstract void render(GuiGraphics g, boolean editing);

    public boolean shown() {
        return true;
    }

    protected int S(double v) {
        return (int) Math.round(v * k);
    }

    public void prepare() {
        k = (float) (Px.height() / 1080.0 * scale.get());
    }

    public int x(int screenW) {
        return (int) Math.round(fx * screenW);
    }

    public int y(int screenH) {
        return (int) Math.round(fy * screenH);
    }

    protected int U(double v) {
        return S(v * 2);
    }

    /** The plate every element sits on: a translucent near-black panel, radius 8, a hairline white edge. */
    protected void plate(GuiGraphics g, int w, int h) {
        if (!background.on()) return;
        Theme.Palette p = Theme.p();
        int r = Math.min(U(8), h / 2);
        boolean frost = !p.light() && Theme.style != 1 && Frost.prepare();
        if (frost) Frost.backdrop(g, 0, 0, w, h, r);
        Px.round(
                g,
                0,
                0,
                w,
                h,
                r,
                p.light()
                        ? 0xE6F6F6F9
                        : (p.bg() & 0x00FFFFFF) | (Theme.style == 1 ? 0xF4000000 : frost ? 0xB8000000 : 0xD8000000));
        Px.outline(g, 0, 0, w, h, r, p.light() ? 0x1A000000 : 0x24FFFFFF);
    }

    protected int kitHeader(GuiGraphics g, String icon, String title, int w) {
        int c = Theme.p().text();
        Px.text(g, Lang.tr(title), U(5), U(4.5), Weight.BOLD, Math.max(9, U(8)), c);
        Px.icon(g, "w." + icon, w - U(13), U(5.5), Math.max(8, U(8)), c);
        return U(22.5);
    }

    protected int kitTitleWidth(String title) {
        return U(5) + Px.textWidth(Lang.tr(title), Weight.BOLD, Math.max(9, U(8))) + U(19);
    }

    protected int badge(GuiGraphics g, String text, int right, int y, boolean filled, int colour) {
        return badge(g, null, text, right, y, filled, colour, 0);
    }
    /** The same pill with a small icon before the text (a clock for timers) and an optional tint (red for harmful). */
    protected int badge(
            GuiGraphics g, String icon, String text, int right, int y, boolean filled, int colour, int tint) {
        int fs = Math.max(8, U(5.5)), h = U(10.5), bw = badgeWidth(icon, text), x = right - bw;
        boolean lt = Theme.p().light();
        if (filled) Px.round(g, x, y, bw, h, h / 2, tint != 0 ? Px.a(tint, 46) : lt ? 0x14000000 : 0x600E1016);
        Px.outline(g, x, y, bw, h, h / 2, tint != 0 ? Px.a(tint, 90) : lt ? 0x22000000 : 0x28FFFFFF);
        int tx = x + U(4);
        if (icon != null) {
            int is = Math.max(8, U(5.5));
            Px.icon(g, "w." + icon, tx, y + (h - is) / 2, is, colour);
            tx += is + U(2);
        }
        Px.text(g, text, tx, y + (h - fs) / 2, Weight.BOLD, fs, colour);
        return bw;
    }

    protected int badgeWidth(String text) {
        return badgeWidth(null, text);
    }

    protected int badgeWidth(String icon, String text) {
        int fs = Math.max(8, U(5.5));
        return Px.textWidth(text, Weight.BOLD, fs) + U(8) + (icon == null ? 0 : Math.max(8, U(5.5)) + U(2));
    }

    protected int header(GuiGraphics g, String icon, String title, int pad) {
        Theme.Palette p = Theme.p();
        Px.icon(g, icon, pad, S(12), S(16), Theme.accent2());
        Px.text(g, Lang.tr(title), pad + S(24), S(12), Weight.SEMIBOLD, S(14), p.text());
        return S(40);
    }

    protected int chip(GuiGraphics g, String text, int right, int y, int h, int minW, int colour) {
        Theme.Palette p = Theme.p();
        int fs = S(11);
        int cw = chipWidth(text, minW);
        Px.round(g, right - cw, y, cw, h, h / 2, Px.a(p.text(), p.light() ? 14 : 9));
        Px.outline(g, right - cw, y, cw, h, h / 2, Px.a(p.text(), p.light() ? 34 : 22));
        if (text.equals("∞")) Px.icon(g, "infinity", right - cw / 2 - S(7), y + (h - S(14)) / 2, S(14), colour);
        else Px.textCenter(g, Lang.tr(text), right - cw / 2, y + (h - fs) / 2, Weight.MEDIUM, fs, colour);
        return cw;
    }

    protected int chipWidth(String text, int minW) {
        return Math.max(minW, Px.textWidth(Lang.tr(text), Weight.MEDIUM, S(11)) + S(18));
    }
}
