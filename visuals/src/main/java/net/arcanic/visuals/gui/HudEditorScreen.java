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

import java.util.Locale;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.hud.HudDrag;
import net.arcanic.visuals.hud.HudModule;
import net.arcanic.visuals.hud.HudRenderer;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private final Anim anim = new Anim();
    private HudModule resizing;
    private double cardX;
    private double cardY;
    private double cardScale;
    private int cardW;
    private int cardH;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 1879048192);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        double mx = Px.px((double) mouseX);
        double my = Px.px((double) mouseY);
        int W = Px.width();
        int H = Px.height();
        int u = Math.max(1, H / 1080);
        Px.begin(g);

        for (int x = 0; x < W; x += 40 * u) {
            Px.rect(g, x, 0, 1, H, 218103807);
        }

        for (int y = 0; y < H; y += 40 * u) {
            Px.rect(g, 0, y, W, 1, 218103807);
        }

        HudDrag.guides(g);
        HudModule dragging = HudDrag.dragging();
        this.anim.frame();

        for (HudModule h : Modules.HUD) {
            HudRenderer.draw(g, h, true, !h.enabled());
            int x = Math.max(0, Math.min(W - h.w, h.x(W)));
            int y = Math.max(0, Math.min(H - h.h, h.y(H)));
            boolean hover = mx >= (double) x && mx < (double) (x + h.w) && my >= (double) y && my < (double) (y + h.h);
            Px.outline(
                    g,
                    x - 3,
                    y - 3,
                    h.w + 6,
                    h.h + 6,
                    12,
                    h == dragging ? Theme.accent2() : (hover ? Px.a(Theme.accent(), 230) : 1090519039));
            int hs = Math.max(14, Math.round((float) (18 * H) / 1080.0F));
            int gx = x + h.w - hs / 2;
            int gy = y + h.h - hs / 2;
            boolean onGrip = mx >= (double) (gx - 4)
                    && mx < (double) (gx + hs + 4)
                    && my >= (double) (gy - 4)
                    && my < (double) (gy + hs + 4);
            float gk = this.anim.to("grip:" + h.id, !hover && !onGrip && h != this.resizing ? 0.0F : 1.0F, 16.0F);
            float gh = this.anim.to("griph:" + h.id, !onGrip && h != this.resizing ? 0.0F : 1.0F, 18.0F);
            if (gk > 0.01F) {
                float old = Px.fade;
                Px.fade = old * gk;
                int s = hs + Math.round(gh * 4.0F);
                int sx = gx - (s - hs) / 2;
                int sy = gy - (s - hs) / 2;
                Px.shadow(g, sx, sy + 2, s, s, 10, 1610612736);
                Px.round(g, sx, sy, s, s, s / 3, Px.mix(Px.a(Theme.accent(), 200), Theme.accent2() | 0xFF000000, gh));
                Px.outline(g, sx, sy, s, s, s / 3, 1627389951);
                int is = Math.round((float) s * 0.62F);
                Px.icon(g, "maximize-2", sx + (s - is) / 2, sy + (s - is) / 2, is, -1);
                Px.fade = old;
            }

            if (h == this.resizing) {
                String sc = String.format(Locale.ROOT, "x%.2f", h.scale.get());
                int fs = Math.round((float) (12 * H) / 1080.0F);
                int lw = Px.textWidth(sc, Px.Weight.SEMIBOLD, fs) + 18;
                int lh = fs + 10;
                int lx = x + h.w - lw;
                int ly = y + h.h + 12;
                Px.round(g, lx, ly, lw, lh, lh / 2, Theme.p().card());
                Px.outline(g, lx, ly, lw, lh, lh / 2, Px.a(Theme.accent(), 200));
                Px.textCenter(
                        g,
                        sc,
                        lx + lw / 2,
                        ly + 5,
                        Px.Weight.SEMIBOLD,
                        fs,
                        Theme.p().text());
            }

            if (hover || h == dragging) {
                String label = h.title() + (h.enabled() ? "" : " · выкл");
                int fs = Math.round((float) (12 * H) / 1080.0F);
                int lw = Px.textWidth(Lang.tr(label), Px.Weight.SEMIBOLD, fs) + 20;
                int lh = fs + 12;
                int ly = y - lh - 8 < 0 ? y + h.h + 8 : y - lh - 8;
                Px.round(g, x - 3, ly, lw, lh, 8, Theme.p().card());
                Px.outline(g, x - 3, ly, lw, lh, 8, Theme.p().border());
                Px.text(
                        g,
                        Lang.tr(label),
                        x + 7,
                        ly + 6,
                        Px.Weight.SEMIBOLD,
                        fs,
                        h.enabled() ? Theme.p().text() : Theme.p().muted());
            }
        }

        String hint = "ЛКМ — двигать   ·   угол — размер   ·   ПКМ — вкл/выкл   ·   Esc — назад";
        int fs = Math.round((float) (13 * H) / 1080.0F);
        int hw = Px.textWidth(Lang.tr(hint), Px.Weight.MEDIUM, fs) + 40;
        int hh = fs + 22;
        int hx = (W - hw) / 2;
        int hy = H - hh - 30;
        Px.shadow(g, hx, hy, hw, hh, 20, 0x90000000);
        Px.round(g, hx, hy, hw, hh, hh / 2, Theme.p().card());
        Px.outline(g, hx, hy, hw, hh, hh / 2, Theme.p().border());
        Px.textCenter(
                g,
                Lang.tr(hint),
                W / 2,
                hy + 11,
                Px.Weight.MEDIUM,
                fs,
                Theme.p().muted());
        Px.end(g);
    }

    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = Px.px(e.x());
        double my = Px.px(e.y());
        if (e.button() == 0) {
            int W = Px.width();
            int H = Px.height();
            int hs = Math.max(14, Math.round((float) (18 * H) / 1080.0F));

            for (int i = Modules.HUD.size() - 1; i >= 0; --i) {
                HudModule c = (HudModule) Modules.HUD.get(i);
                int x = Math.max(0, Math.min(W - c.w, c.x(W)));
                int y = Math.max(0, Math.min(H - c.h, c.y(H)));
                int gx = x + c.w - hs / 2;
                int gy = y + c.h - hs / 2;
                if (mx >= (double) (gx - 4)
                        && mx < (double) (gx + hs + 4)
                        && my >= (double) (gy - 4)
                        && my < (double) (gy + hs + 4)) {
                    this.resizing = c;
                    this.cardX = mx;
                    this.cardY = my;
                    this.cardScale = (Double) c.scale.get();
                    this.cardW = Math.max(1, c.w);
                    this.cardH = Math.max(1, c.h);
                    return true;
                }
            }
        }

        HudModule h = HudDrag.at(mx, my, true);
        if (h == null) {
            return super.mouseClicked(e, dbl);
        } else if (e.button() == 1) {
            h.toggle();
            Config.dirty();
            return true;
        } else {
            HudDrag.press(h, mx, my);
            return true;
        }
    }

    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (this.resizing != null) {
            double mx = Px.px(e.x());
            double my = Px.px(e.y());
            double k = Math.max(
                    ((double) this.cardW + mx - this.cardX) / (double) this.cardW,
                    ((double) this.cardH + my - this.cardY) / (double) this.cardH);
            this.resizing.scale.set(this.cardScale * Math.max(0.1, k));
            return true;
        } else if (!HudDrag.holding()) {
            return super.mouseDragged(e, dx, dy);
        } else {
            HudDrag.move(Px.px(e.x()), Px.px(e.y()));
            return true;
        }
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        if (this.resizing != null) {
            this.resizing = null;
            Config.dirty();
            return true;
        } else if (!HudDrag.holding()) {
            return super.mouseReleased(e);
        } else {
            HudDrag.release();
            return true;
        }
    }

    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        HudModule h = HudDrag.at(Px.px(mx), Px.px(my), true);
        if (h == null) {
            return false;
        } else {
            h.scale.set((Double) h.scale.get() + (sy > 0.0 ? 0.05 : -0.05));
            Config.dirty();
            return true;
        }
    }

    public void onClose() {
        Config.save();
        this.minecraft.setScreen(this.parent);
    }
}
