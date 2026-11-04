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
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.cosmetic.WearPreview;
import net.arcanic.visuals.cosmetic.Wearables;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.gui.GuiGraphics;

/** The studio beside the wardrobe for wings, backpacks and headwear: size, offsets, tilt and turn of what you wear. */
final class WearStudioPanel {
    private final ArcanicMenu menu;
    Wearables open;
    private Wearables shown;
    private int x, y, w, h, inner;

    WearStudioPanel(ArcanicMenu menu) {
        this.menu = menu;
    }

    int width() {
        return Math.min(this.menu.S(340.0), Px.width() - this.menu.S(16.0));
    }

    boolean over(double mx, double my) {
        return this.shown != null && mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
    }

    void draw(GuiGraphics g) {
        boolean on = this.open != null;
        float k = this.menu.an("ws", on ? 1.0F : 0.0F, on ? 14.0F : 22.0F);
        if (on) this.shown = this.open;
        else if (k < 0.01F) this.shown = null;
        Wearables m = this.shown;
        if (m == null) return;
        float a = ArcanicMenu.ease(k);
        boolean live = on && !this.menu.blocked() && k > 0.6F;
        int sw = Px.width();
        int sh = Px.height();
        int pad = this.menu.S(16.0);
        this.w = this.width();
        int cw = this.w - pad * 2;
        int head = this.menu.S(58.0);
        int pvh = Math.round(cw * 0.62F);
        this.h = Math.min(sh - this.menu.S(24.0), Math.max(this.menu.ph, head + pvh + this.menu.S(360.0)));
        this.x = Math.min(this.menu.px + this.menu.pw + this.menu.S(12.0), sw - this.menu.S(8.0) - this.w);
        this.y = Math.max(this.menu.S(12.0), this.menu.py + (this.menu.ph - this.h) / 2);
        this.menu.clipped(this.x, this.y, this.w, this.h, 0.0F, 0, sh, () -> {}, null);
        float kf = Px.fade;
        Px.fade = kf * Math.min(1.0F, k * 1.4F);
        g.pose().pushMatrix();
        g.pose().translate((1.0F - a) * -this.menu.S(12.0), 0.0F);
        int R = this.menu.S(12.0);
        Px.shadow(g, this.x, this.y + this.menu.S(8.0), this.w, this.h, this.menu.S(24.0), Px.a(0, 130));
        Px.round(g, this.x, this.y, this.w, this.h, R, ArcanicMenu.panelBg());
        Px.outline(g, this.x, this.y, this.w, this.h, R, ArcanicMenu.edgeMedium());

        Wearables.Item it = m.current();
        String title = it == null ? m.title() : Lang.tr(it.name());
        int bs = this.menu.S(30.0);
        Px.text(
                g,
                Px.ellipsize(title, Px.Weight.SEMIBOLD, this.menu.S(15.0), cw - bs - this.menu.S(8.0)),
                this.x + pad,
                this.y + this.menu.S(14.0),
                Px.Weight.SEMIBOLD,
                this.menu.S(15.0),
                ArcanicMenu.T().text());
        Px.text(
                g,
                Lang.tr("Как сидит на тебе: видно в виде от 3-го лица"),
                this.x + pad,
                this.y + this.menu.S(33.0),
                Px.Weight.MEDIUM,
                this.menu.S(11.0),
                ArcanicMenu.faint());
        int bx = this.x + this.w - pad - bs + this.menu.S(6.0);
        int by = this.y + this.menu.S(12.0);
        boolean xh = live && this.menu.hover(bx, by, bs, bs);
        if (xh) this.menu.hot("ws:close");
        float xk = this.menu.an("ws:close", xh ? 1.0F : 0.0F, 18.0F);
        if (xk > 0.01F) Px.round(g, bx, by, bs, bs, this.menu.S(8.0), Px.a(ArcanicMenu.field(), (int) (255 * xk)));
        int gs = this.menu.S(14.0);
        ArcanicMenu.glyph(g, "x", bx + (bs - gs) / 2, by + (bs - gs) / 2, gs, ArcanicMenu.sub());
        if (live) {
            this.menu.clipped(
                    bx,
                    by,
                    bs,
                    bs,
                    0.0F,
                    0,
                    sh,
                    () -> {
                        this.open = null;
                        UiSound.play("pop", 0.8F, 0.6F);
                    },
                    null);
        }
        Px.rect(
                g,
                this.x,
                this.y + head - this.menu.S(4.0),
                this.w,
                Math.max(1, this.menu.S(1.0)),
                ArcanicMenu.edgeSmall());

        int pvx = this.x + pad;
        int pvy = this.y + head + this.menu.S(6.0);
        Px.round(g, pvx, pvy, cw, pvh, this.menu.S(10.0), ArcanicMenu.light() ? 0xFFDCDCE4 : 0xFF0D0D10);
        if (it != null) {
            g.nextStratum();
            WearPreview.draw(g, it, pvx, pvy, cw, pvh, 0.0F, pvy, pvy + pvh);
        }
        g.nextStratum();

        int t = pvy + pvh + this.menu.S(12.0);
        int b = this.y + this.h - this.menu.S(6.0);
        float sc = (float) Math.round(this.menu.scrolled("ws", this.inner, t, b));
        g.enableScissor(this.x, t, this.x + this.w, b);
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -sc);
        int cy = t;
        this.menu.compact = true;
        cy = this.menu.settings(g, m, m.fit(), this.x + pad, cy, cw, sc, t, b, live);
        this.menu.compact = false;
        cy += this.menu.S(8.0);
        cy = this.reset(g, m, this.x + pad, cy, sc, t, b, live);
        for (String line : new String[] {"Запоминается для этого вида косметики.", "Другие видят её как есть."}) {
            Px.text(g, Lang.tr(line), this.x + pad, cy, Px.Weight.MEDIUM, this.menu.S(10.0), ArcanicMenu.faint());
            cy += this.menu.S(15.0);
        }
        this.inner = cy - t + this.menu.S(12.0);
        g.pose().popMatrix();
        g.disableScissor();
        if (this.inner > b - t) {
            this.menu.scrollbar(g, this.x + this.w - this.menu.S(8.0), t, b, this.inner, sc, "ws");
        }
        g.pose().popMatrix();
        Px.fade = kf;
    }

    private int reset(GuiGraphics g, Wearables m, int cx, int cy, float sc, int t, int b, boolean live) {
        String rs = Lang.tr("Сбросить положение и размер");
        int fs = this.menu.S(12.0);
        int rw = Px.textWidth(rs, Px.Weight.MEDIUM, fs) + this.menu.S(24.0);
        int rh = this.menu.S(28.0);
        boolean hv = live && this.menu.my >= t && this.menu.my < b && this.menu.hover(cx, Math.round(cy - sc), rw, rh);
        if (hv) this.menu.hot("ws:reset");
        float rk = this.menu.an("ws:reset", hv ? 1.0F : 0.0F, 18.0F);
        Px.round(g, cx, cy, rw, rh, this.menu.S(7.0), Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), rk));
        Px.textCenter(g, rs, cx + rw / 2, cy + (rh - fs) / 2, Px.Weight.MEDIUM, fs, ArcanicMenu.label());
        if (live) {
            this.menu.clipped(
                    cx,
                    cy,
                    rw,
                    rh,
                    sc,
                    t,
                    b,
                    () -> {
                        m.resetFit();
                        Config.dirty();
                        UiSound.play("reset");
                    },
                    null);
        }
        return cy + rh + this.menu.S(10.0);
    }
}
