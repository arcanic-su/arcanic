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

import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.hud.HudDrag;
import net.arcanic.visuals.hud.HudModule;
import net.arcanic.visuals.hud.HudRenderer;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag HUD elements around; right-click toggles one, wheel resizes it. */
public final class HudEditorScreen extends Screen {
    private final Screen parent;

    public HudEditorScreen(Screen parent) {
        super(Component.literal(Lang.tr("Редактор HUD")));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x70000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        double mx = Px.px(mouseX), my = Px.px(mouseY);
        int W = Px.width(), H = Px.height(), u = Math.max(1, H / 1080);
        Px.begin(g);
        for (int x = 0; x < W; x += 40 * u) Px.rect(g, x, 0, 1, H, 0x0CFFFFFF);
        for (int y = 0; y < H; y += 40 * u) Px.rect(g, 0, y, W, 1, 0x0CFFFFFF);
        HudDrag.guides(g);
        HudModule dragging = HudDrag.dragging();
        for (HudModule h : Modules.HUD) {
            HudRenderer.draw(g, h, true, !h.enabled());
            int x = Math.max(0, Math.min(W - h.w, h.x(W))), y = Math.max(0, Math.min(H - h.h, h.y(H)));
            boolean hover = mx >= x && mx < x + h.w && my >= y && my < y + h.h;
            Px.outline(
                    g,
                    x - 3,
                    y - 3,
                    h.w + 6,
                    h.h + 6,
                    12,
                    h == dragging ? Theme.accent2() : hover ? Px.a(Theme.accent(), 230) : 0x40FFFFFF);
            if (hover || h == dragging) {
                String label = h.name + (h.enabled() ? "" : " · выкл");
                int fs = Math.round(12 * H / 1080f),
                        lw = Px.textWidth(Lang.tr(label), Weight.SEMIBOLD, fs) + 20,
                        lh = fs + 12;
                int ly = y - lh - 8 < 0 ? y + h.h + 8 : y - lh - 8;
                Px.round(g, x - 3, ly, lw, lh, 8, Theme.p().card());
                Px.outline(g, x - 3, ly, lw, lh, 8, Theme.p().border());
                Px.text(
                        g,
                        Lang.tr(label),
                        x + 7,
                        ly + 6,
                        Weight.SEMIBOLD,
                        fs,
                        h.enabled() ? Theme.p().text() : Theme.p().muted());
            }
        }
        String hint = "ЛКМ — двигать   ·   ПКМ — вкл/выкл   ·   колесо — размер   ·   Esc — назад";
        int fs = Math.round(13 * H / 1080f), hw = Px.textWidth(Lang.tr(hint), Weight.MEDIUM, fs) + 40, hh = fs + 22;
        int hx = (W - hw) / 2, hy = H - hh - 30;
        Px.shadow(g, hx, hy, hw, hh, 20, 0x90000000);
        Px.round(g, hx, hy, hw, hh, hh / 2, Theme.p().card());
        Px.outline(g, hx, hy, hw, hh, hh / 2, Theme.p().border());
        Px.textCenter(
                g, Lang.tr(hint), W / 2, hy + 11, Weight.MEDIUM, fs, Theme.p().muted());
        Px.end(g);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = Px.px(e.x()), my = Px.px(e.y());
        HudModule h = HudDrag.at(mx, my, true);
        if (h == null) return super.mouseClicked(e, dbl);
        if (e.button() == 1) {
            h.toggle();
            Config.dirty();
            return true;
        }
        HudDrag.press(h, mx, my);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (!HudDrag.holding()) return super.mouseDragged(e, dx, dy);
        HudDrag.move(Px.px(e.x()), Px.px(e.y()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (!HudDrag.holding()) return super.mouseReleased(e);
        HudDrag.release();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        HudModule h = HudDrag.at(Px.px(mx), Px.px(my), true);
        if (h == null) return false;
        h.scale.set(h.scale.get() + (sy > 0 ? .05 : -.05));
        Config.dirty();
        return true;
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }
}
