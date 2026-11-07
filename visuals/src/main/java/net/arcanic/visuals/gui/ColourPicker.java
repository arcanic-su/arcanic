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

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

final class ColourPicker {
    private final String title;
    private final boolean enableAlpha;
    private final BiConsumer<Integer, Boolean> commit;
    private final Anim anim;
    private final float scale;
    private final int initial;
    private int x;
    private int y;
    private int w;
    private int h;
    private int colour;
    private int gesture;
    private double grabX;
    private double grabY;
    private float hue;
    private float sat;
    private float value;
    private float alpha;
    private boolean closing;
    private boolean picking;
    private boolean theme;
    private boolean rgbMode;
    private boolean selectAll;
    private String editing;
    private boolean editingAlpha;
    private NativeImage sample;
    private static final float W = 166.0F;
    private static final float PAD = 8.0F;
    private static final float HEAD = 14.0F;
    private static final float HUE_W = 10.0F;
    private static final float GAP = 6.0F;
    private static final float SV_H = 88.0F;
    private static final float CAPS = 6.0F;
    private static final float BAR_H = 10.0F;
    private static final float PCT_W = 28.0F;
    private static final float PREV = 31.0F;
    private static final float FIELD_H = 13.0F;
    private static final float SEG = 17.0F;
    private static final float DOT = 10.0F;

    ColourPicker(String title, Setting.Colour setting) {
        this(title, setting.argb(), true, (c, th) -> {
            setting.set(c);
            setting.setTheme(th);
        });
        this.theme = setting.followsTheme();
    }

    ColourPicker(String title, int initial, boolean enableAlpha, BiConsumer<Integer, Boolean> commit) {
        this.anim = new Anim();
        this.title = title;
        this.enableAlpha = enableAlpha;
        this.commit = commit;
        this.initial = initial;
        this.scale = Math.max(1.0F, Math.min(2.2F, (float) Px.height() / 1080.0F * 1.2F));
        this.w = this.s(166.0F);
        this.update(initial, false);
        this.anim.set("open", 0.0F);
        this.layout();
    }

    ColourPicker at(double mx, double my) {
        this.x = (int) mx + this.s(10.0F);
        this.y = (int) my - this.s(24.0F);
        this.keepOnScreen();
        return this;
    }

    private float svY() {
        return 29.0F;
    }

    private float svW() {
        return this.inner() - 10.0F - 6.0F;
    }

    private float hueX() {
        return 8.0F + this.inner() - 10.0F;
    }

    private float capsY() {
        return this.svY() + 88.0F + 8.0F;
    }

    private float alphaY() {
        return this.capsY() + 6.0F + 4.0F;
    }

    private float alphaW() {
        return this.inner() - 28.0F - 5.0F;
    }

    private float bottomY() {
        return (this.enableAlpha ? this.alphaY() + 10.0F : this.svY() + 88.0F) + 9.0F;
    }

    private float rightX() {
        return 46.0F;
    }

    private float rightW() {
        return 8.0F + this.inner() - this.rightX();
    }

    private float themeY() {
        return this.bottomY() + 13.0F + 5.0F;
    }

    private float paletteY() {
        return this.bottomY() + 31.0F + 9.0F;
    }

    private float recentY() {
        return this.paletteY() + 10.0F + 4.0F;
    }

    private void layout() {
        this.h = this.s(this.recentY() + 10.0F + 8.0F);
    }

    private float inner() {
        return 150.0F;
    }

    private float dotStep(int n) {
        return n <= 1 ? 0.0F : (this.inner() - 10.0F) / (float) (n - 1);
    }

    private int s(float v) {
        return Math.round(v * this.scale);
    }

    private int X(float u) {
        return this.x + this.s(u);
    }

    private int Y(float u) {
        return this.y + this.s(u);
    }

    private boolean hit(double mx, double my, float xx, float yy, float ww, float hh) {
        return mx >= (double) this.X(xx)
                && mx < (double) this.X(xx + ww)
                && my >= (double) this.Y(yy)
                && my < (double) this.Y(yy + hh);
    }

    private void keepOnScreen() {
        this.x = Math.max(4, Math.min(Px.width() - this.w - 4, this.x));
        this.y = Math.max(4, Math.min(Px.height() - this.h - 4, this.y));
    }

    private static float clamp(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    private void update(int c, boolean save) {
        this.colour = c;
        float[] hsv = Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
        if (hsv[1] > 0.001F && hsv[2] > 0.001F) {
            this.hue = hsv[0];
        }

        this.sat = hsv[1];
        this.value = hsv[2];
        this.alpha = (float) (c >>> 24) / 255.0F;
        if (save) {
            this.publish();
        }
    }

    private int compose() {
        return Color.HSBtoRGB(this.hue, this.sat, this.value) & 0xFFFFFF
                | (this.enableAlpha ? Math.round(this.alpha * 255.0F) : 255) << 24;
    }

    private void publish() {
        this.colour = this.compose();
        this.theme = false;
        this.commit.accept(this.colour, false);
        Config.dirty();
    }

    private String hex() {
        return String.format(Locale.ROOT, "#%06X", this.colour & 0xFFFFFF);
    }

    private String rgb() {
        return (this.colour >> 16 & 255) + " " + (this.colour >> 8 & 255) + " " + (this.colour & 255);
    }

    void render(GuiGraphics g, double mx, double my) {
        this.anim.frame();
        if (this.gesture != 0 && !ArcanicMenu.leftHeld()) {
            this.release();
        }

        this.drag(mx, my);
        this.layout();
        float open = this.anim.to("open", this.closing ? 0.0F : 1.0F, 16.0F);
        float oldFade = Px.fade;
        Px.fade *= Math.min(1.0F, open * 1.3F);
        g.pose().pushMatrix();
        float k = 0.92F + 0.08F * Anim.easeOutCubic(open);
        g.pose().translate((float) this.x, (float) this.y);
        g.pose().scale(k, k);
        g.pose().translate((float) (-this.x), (float) (-this.y));
        int bg = ArcanicMenu.panelBg();
        int text = ArcanicMenu.T().text();
        g.nextStratum();
        Px.shadow(
                g,
                this.x,
                this.y + this.s(5.0F),
                this.w,
                this.h,
                this.s(15.0F),
                ArcanicMenu.light() ? 704643072 : 0x99000000);
        Px.round(g, this.x, this.y, this.w, this.h, this.s(8.0F), bg);
        Px.outline(g, this.x, this.y, this.w, this.h, this.s(8.0F), ArcanicMenu.edgeMedium());
        g.nextStratum();
        this.header(g, mx, my, text);
        this.square(g, bg);
        this.hueBar(g, bg);
        if (this.enableAlpha) {
            this.alphaBar(g, mx, my, bg, text);
        }

        this.preview(g, mx, my, bg);
        this.fields(g, mx, my, text);
        this.themeRow(g, mx, my, text);
        this.dots(g, mx, my);
        g.pose().popMatrix();
        Px.fade = oldFade;
        if (this.picking) {
            this.loupe(g, mx, my);
        }
    }

    private void header(GuiGraphics g, double mx, double my, int text) {
        int fs = Math.max(9, this.s(7.0F));
        int cy = this.Y(15.0F);
        Px.textCenter(
                g,
                Px.ellipsize(Lang.tr(this.title), Px.Weight.SEMIBOLD, fs, this.s(this.inner() - 40.0F)),
                this.X(83.0F),
                cy - fs / 2,
                Px.Weight.SEMIBOLD,
                fs,
                text);
        this.button(g, mx, my, "pick", "w.pipette", 8.0F, 8.0F, 14.0F, this.picking, false);
        this.button(g, mx, my, "close", "x", 8.0F + this.inner() - 13.0F, 8.5F, 13.0F, false, true);
    }

    private void button(
            GuiGraphics g,
            double mx,
            double my,
            String id,
            String icon,
            float ux,
            float uy,
            float size,
            boolean on,
            boolean round) {
        boolean hv = !this.picking && this.hit(mx, my, ux, uy, size, size);
        float hk = this.anim.to("btn:" + id, hv ? 1.0F : 0.0F, 18.0F);
        int bx = this.X(ux);
        int by = this.Y(uy);
        int bs = this.s(size);
        Px.round(
                g,
                bx,
                by,
                bs,
                bs,
                round ? bs / 2 : this.s(4.5F),
                on ? ArcanicMenu.acc() : Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), hk));
        int is = this.s(size * 0.52F);
        ArcanicMenu.glyph(
                g,
                icon,
                bx + (bs - is) / 2,
                by + (bs - is) / 2,
                is,
                on ? -1 : Px.mix(ArcanicMenu.sub(), ArcanicMenu.T().text(), hk));
    }

    private static void rounded(GuiGraphics g, int x0, int y0, int ww, int hh, int r, int bg, Row row) {
        r = Math.min(r, Math.min(ww, hh) / 2);

        for (int i = 0; i < hh; i++) {
            float dy = i < r ? (float) (r - i) - 0.5F : (i >= hh - r ? (float) (i - (hh - r)) + 0.5F : 0.0F);
            int in = dy > 0.0F
                    ? Math.round((float) r - (float) Math.sqrt((double) Math.max(0.0F, (float) (r * r) - dy * dy)))
                    : 0;
            row.at(y0 + i, x0 + in, x0 + ww - in, ((float) i + 0.5F) / (float) hh);
        }

        g.nextStratum();
        if (r > 1) {
            Px.outline(g, x0, y0, ww, hh, r, Px.a(bg, 200));
        }
    }

    private void checkerRow(GuiGraphics g, int left, int top, int yy, int x0, int x1) {
        int c = Math.max(2, this.s(3.0F));
        boolean lt = ArcanicMenu.light();
        Px.rect(g, x0, yy, x1 - x0, 1, lt ? -1 : 0xFF3A3A40);
        int odd = (yy - top) / c % 2;

        for (int cx = left + (x0 - left) / c * c; cx < x1; cx += c) {
            if (((cx - left) / c + odd) % 2 == 0) {
                int a = Math.max(cx, x0);
                int b = Math.min(cx + c, x1);
                if (b > a) {
                    Px.rect(g, a, yy, b - a, 1, lt ? 0xFFD9D9DE : 0xFF26262B);
                }
            }
        }
    }

    private void square(GuiGraphics g, int bg) {
        int sx = this.X(8.0F);
        int sy = this.Y(this.svY());
        int sw = this.s(this.svW());
        int sh = this.s(88.0F);
        int pure = Color.HSBtoRGB(this.hue, 1.0F, 1.0F) | 0xFF000000;
        rounded(g, sx, sy, sw, sh, this.s(5.0F), bg, (yy, x0, x1, t) -> {
            float v = 1.0F - t;
            int left = Px.mix(0xFF000000, -1, v);
            int right = Px.mix(0xFF000000, pure, v);
            int a = Math.round(sw == 0 ? 0.0F : (float) (x0 - sx) / (float) sw * 255.0F);
            int b = Math.round((float) (x1 - sx) / (float) sw * 255.0F);
            Px.hgrad(
                    g,
                    x0,
                    yy,
                    x1 - x0,
                    1,
                    Px.mix(left, right, (float) a / 255.0F),
                    Px.mix(left, right, (float) b / 255.0F));
        });
        float ds = this.anim.to("sat", this.sat, 22.0F);
        float dv = this.anim.to("value", this.value, 22.0F);
        this.knob(
                g,
                sx + Math.round((float) sw * ds),
                sy + Math.round((float) sh * (1.0F - dv)),
                this.colour | 0xFF000000,
                this.s(8.0F),
                this.s(8.0F));
    }

    private void hueBar(GuiGraphics g, int bg) {
        int hx = this.X(this.hueX());
        int hy = this.Y(this.svY());
        int hw = this.s(10.0F);
        int hh = this.s(88.0F);
        rounded(
                g,
                hx,
                hy,
                hw,
                hh,
                this.s(4.0F),
                bg,
                (yy, x0, x1, t) ->
                        Px.rect(g, x0, yy, x1 - x0, 1, Color.HSBtoRGB(Math.min(0.9999F, t), 1.0F, 1.0F) | 0xFF000000));
        float dh = this.anim.to("hue", this.hue, 22.0F);
        this.knob(
                g,
                hx + hw / 2,
                hy + Math.round((float) hh * dh),
                Color.HSBtoRGB(dh, 1.0F, 1.0F) | 0xFF000000,
                hw + this.s(4.0F),
                this.s(5.5F));
    }

    private void alphaBar(GuiGraphics g, double mx, double my, int bg, int text) {
        int fs = Math.max(8, this.s(5.5F));
        Px.text(
                g,
                Lang.tr("ПРОЗРАЧНОСТЬ"),
                this.X(8.0F),
                this.Y(this.capsY()),
                Px.Weight.SEMIBOLD,
                fs,
                ArcanicMenu.faint());
        int ax = this.X(8.0F);
        int ay = this.Y(this.alphaY());
        int aw = this.s(this.alphaW());
        int ah = this.s(10.0F);
        int r = ah / 2;
        int solid = this.colour | 0xFF000000;
        rounded(g, ax, ay, aw, ah, r, bg, (yy, x0, x1, t) -> {
            this.checkerRow(g, ax, ay, yy, x0, x1);
            float a = (float) (x0 - ax) / (float) aw;
            float b = (float) (x1 - ax) / (float) aw;
            Px.hgrad(g, x0, yy, x1 - x0, 1, Px.am(solid, a), Px.am(solid, b));
        });
        float da = this.anim.to("alpha", this.alpha, 22.0F);
        this.knob(g, ax + r + Math.round((float) (aw - r * 2) * da), ay + ah / 2, solid, this.s(8.0F), this.s(8.0F));
        boolean active = this.editing != null && this.editingAlpha;
        this.field(
                g,
                mx,
                my,
                "alpha",
                this.X(8.0F + this.inner() - 28.0F),
                ay,
                this.s(28.0F),
                ah,
                active ? this.editing : Math.round(this.alpha * 100.0F) + "%",
                active,
                Math.max(8, this.s(5.8F)),
                text);
    }

    private void knob(GuiGraphics g, int cx, int cy, int c, int kw, int kh) {
        int r = Math.min(kw, kh) * 2 / 5;
        int e = Math.max(2, this.s(1.4F));
        Px.shadow(g, cx - kw / 2, cy - kh / 2 + this.s(0.8F), kw, kh, this.s(4.0F), 1610612736);
        Px.round(g, cx - kw / 2, cy - kh / 2, kw, kh, r, -1);
        Px.round(g, cx - kw / 2 + e, cy - kh / 2 + e, kw - e * 2, kh - e * 2, Math.max(1, r - e / 2), c);
    }

    private void preview(GuiGraphics g, double mx, double my, int bg) {
        int px0 = this.X(8.0F);
        int py0 = this.Y(this.bottomY());
        int ps = this.s(31.0F);
        int r = this.s(6.0F);
        boolean clear = this.colour >>> 24 < 255;
        rounded(g, px0, py0, ps, ps, r, bg, (yy, x0, x1, t) -> {
            if (clear) {
                this.checkerRow(g, px0, py0, yy, x0, x1);
            }

            Px.rect(g, x0, yy, x1 - x0, 1, this.colour);
        });
        Px.outline(g, px0, py0, ps, ps, r, ArcanicMenu.edgeMedium());
        float diff = this.anim.to("diff", this.initial != this.colour ? 1.0F : 0.0F, 16.0F);
        if (!(diff < 0.01F)) {
            boolean hv = !this.picking && this.hit(mx, my, 27.0F, this.bottomY() + 31.0F - 12.0F, 14.0F, 14.0F);
            float hk = this.anim.to("revert", hv ? 1.0F : 0.0F, 18.0F);
            float kf = Px.fade;
            Px.fade = kf * diff;
            int cs = this.s(11.0F + 2.0F * hk);
            int cx = px0 + ps - cs + this.s(3.0F);
            int cy = py0 + ps - cs + this.s(3.0F);
            int ring = Math.max(2, this.s(1.6F));
            g.nextStratum();
            Px.round(g, cx - ring, cy - ring, cs + ring * 2, cs + ring * 2, this.s(4.5F) + ring, bg);
            Px.round(g, cx, cy, cs, cs, this.s(4.5F), this.initial | 0xFF000000);
            if (hk > 0.01F) {
                int is = this.s(7.0F);
                Px.fade = kf * diff * hk;
                ArcanicMenu.glyph(g, "rotate-ccw", cx + (cs - is) / 2, cy + (cs - is) / 2, is, ink(this.initial));
            }

            Px.fade = kf;
        }
    }

    private static int ink(int c) {
        double l = 0.2126 * (double) (c >> 16 & 255) + 0.7152 * (double) (c >> 8 & 255) + 0.0722 * (double) (c & 255);
        return l > 150.0 ? 0xFF15151A : -1;
    }

    private void fields(GuiGraphics g, double mx, double my, int text) {
        int fy = this.Y(this.bottomY());
        int fh = this.s(13.0F);
        int fs = Math.max(8, this.s(6.0F));
        int segW = this.s(17.0F);
        int segX = this.X(this.rightX());
        Px.round(g, segX, fy, segW * 2, fh, this.s(5.0F), ArcanicMenu.field());
        float fm = this.anim.to("fmt", this.rgbMode ? 1.0F : 0.0F, 16.0F);
        int thx = segX + this.s(1.5F) + Math.round((float) (segW - this.s(1.5F)) * fm);
        Px.round(g, thx, fy + this.s(1.5F), segW - this.s(1.5F), fh - this.s(3.0F), this.s(4.0F), ArcanicMenu.acc());
        int lfs = Math.max(7, this.s(5.2F));
        Px.textCenter(
                g,
                "HEX",
                segX + segW / 2,
                fy + (fh - lfs) / 2,
                Px.Weight.SEMIBOLD,
                lfs,
                Px.mix(-1, ArcanicMenu.sub(), fm));
        Px.textCenter(
                g,
                "RGB",
                segX + segW + segW / 2,
                fy + (fh - lfs) / 2,
                Px.Weight.SEMIBOLD,
                lfs,
                Px.mix(ArcanicMenu.sub(), -1, fm));
        boolean valueEdit = this.editing != null && !this.editingAlpha;
        int fieldX = segX + segW * 2 + this.s(3.0F);
        int fieldW = this.X(8.0F + this.inner()) - fieldX;
        this.field(
                g,
                mx,
                my,
                "value",
                fieldX,
                fy,
                fieldW,
                fh,
                valueEdit ? this.editing : (this.rgbMode ? this.rgb() : this.hex()),
                valueEdit,
                fs,
                text);
    }

    private void field(
            GuiGraphics g,
            double mx,
            double my,
            String id,
            int fx,
            int fy,
            int fw,
            int fh,
            String value,
            boolean active,
            int fs,
            int text) {
        boolean hv = !this.picking
                && mx >= (double) fx
                && mx < (double) (fx + fw)
                && my >= (double) fy
                && my < (double) (fy + fh);
        float hk = this.anim.to("field:" + id, hv ? 1.0F : 0.0F, 18.0F);
        float ak = this.anim.to("fieldOn:" + id, active ? 1.0F : 0.0F, 16.0F);
        Px.round(g, fx, fy, fw, fh, this.s(4.5F), Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), hk * 0.8F));
        if (ak > 0.01F) {
            Px.outline(g, fx, fy, fw, fh, this.s(4.5F), Px.a(ArcanicMenu.acc(), Math.round(200.0F * ak)));
        }

        int tw = Px.textWidth(value, Px.Weight.SEMIBOLD, fs);
        int tx = fx + (fw - tw) / 2;
        int ty = fy + (fh - fs) / 2;
        if (active && this.selectAll && !value.isEmpty()) {
            Px.round(
                    g,
                    tx - this.s(1.5F),
                    ty - this.s(1.0F),
                    tw + this.s(3.0F),
                    fs + this.s(2.0F),
                    this.s(2.0F),
                    Px.a(ArcanicMenu.acc(), 90));
        }

        Px.text(g, value, tx, ty, Px.Weight.SEMIBOLD, fs, text);
        if (active && !this.selectAll && System.currentTimeMillis() / 520L % 2L == 0L) {
            Px.rect(g, tx + tw + this.s(0.8F), ty, Math.max(1, this.s(0.6F)), fs, text);
        }
    }

    private void themeRow(GuiGraphics g, double mx, double my, int text) {
        int ty = this.Y(this.themeY());
        int th = this.s(13.0F);
        int fs = Math.max(8, this.s(6.2F));
        boolean hv = !this.picking && this.hit(mx, my, this.rightX(), this.themeY(), this.rightW(), 13.0F);
        float hk = this.anim.to("themeH", hv ? 1.0F : 0.0F, 18.0F);
        float on = this.anim.to("theme", this.theme ? 1.0F : 0.0F, 16.0F);
        Px.text(
                g,
                Lang.tr("Как в теме"),
                this.X(this.rightX()) + this.s(2.0F),
                ty + (th - fs) / 2,
                Px.Weight.MEDIUM,
                fs,
                Px.mix(ArcanicMenu.sub(), text, Math.max(hk, on)));
        int tw = this.s(20.0F);
        int tk = this.s(11.0F);
        int tx = this.X(8.0F + this.inner()) - tw;
        int tyy = ty + (th - tk) / 2;
        Px.round(
                g,
                tx,
                tyy,
                tw,
                tk,
                Math.round((float) tk * 0.38F),
                Px.mix(Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), hk), ArcanicMenu.acc(), on));
        int ks = tk - this.s(3.0F);
        int kx = tx + this.s(1.5F) + Math.round((float) (tw - this.s(3.0F) - ks) * on);
        Px.round(g, kx, tyy + this.s(1.5F), ks, ks, ks / 2, Px.mix(ArcanicMenu.sub(), -1, on));
    }

    private void dots(GuiGraphics g, double mx, double my) {
        int[] pal = Setting.Colour.PALETTE;

        for (int i = 0; i < pal.length; i++) {
            this.dot(
                    g,
                    mx,
                    my,
                    "p" + i,
                    8.0F + (float) i * this.dotStep(pal.length),
                    this.paletteY(),
                    pal[i] | 0xFF000000,
                    false);
        }

        List<Integer> recent = Setting.Colour.PRESETS;
        int n = Math.min(recent.size(), 11);
        float step = this.dotStep(12);

        for (int i = 0; i < n; i++) {
            this.dot(g, mx, my, "r" + i, 8.0F + (float) i * step, this.recentY(), recent.get(i) | 0xFF000000, false);
        }

        this.dot(g, mx, my, "add", 8.0F + (float) n * step, this.recentY(), 0, true);
    }

    private void dot(GuiGraphics g, double mx, double my, String id, float ux, float uy, int c, boolean plus) {
        boolean hv = !this.picking && this.hit(mx, my, ux, uy, 10.0F, 10.0F);
        float hk = this.anim.to("dot:" + id, hv ? 1.0F : 0.0F, 18.0F);
        int ds = this.s(10.0F);
        int dx = this.X(ux);
        int dy = this.Y(uy);
        int r = this.s(4.2F);
        if (plus) {
            Px.round(g, dx, dy, ds, ds, r, Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), hk));
            int is = this.s(6.0F);
            ArcanicMenu.glyph(
                    g,
                    "plus",
                    dx + (ds - is) / 2,
                    dy + (ds - is) / 2,
                    is,
                    Px.mix(ArcanicMenu.faint(), ArcanicMenu.T().text(), hk));
        } else {
            boolean cur = (c & 0xFFFFFF) == (this.colour & 0xFFFFFF);
            float sel = this.anim.to("sel:" + id, cur ? 1.0F : 0.0F, 14.0F);
            int shrink = Math.round((float) this.s(2.0F) * sel);
            int grow = Math.round((float) this.s(1.0F) * hk);
            Px.round(
                    g,
                    dx + shrink - grow,
                    dy + shrink - grow,
                    ds - (shrink - grow) * 2,
                    ds - (shrink - grow) * 2,
                    r,
                    c);
            if (sel > 0.02F) {
                int o = Math.max(1, this.s(0.5F));
                Px.outline(
                        g,
                        dx - o,
                        dy - o,
                        ds + o * 2,
                        ds + o * 2,
                        r + o,
                        Px.am(ArcanicMenu.T().text(), sel));
            }
        }
    }

    private void loupe(GuiGraphics g, double mx, double my) {
        int c = this.sampleAt(mx, my);
        int cells = 11;
        int cs = Math.max(3, this.s(4.0F));
        int grid = cells * cs;
        int pad = this.s(5.0F);
        int lw = grid + pad * 2;
        int lh = grid + pad * 2 + this.s(14.0F);
        int lx = (int) Math.max(4.0, Math.min((double) (Px.width() - lw - 4), mx + (double) this.s(14.0F)));
        int ly = (int) Math.max(4.0, Math.min((double) (Px.height() - lh - 4), my + (double) this.s(14.0F)));
        g.nextStratum();
        Px.shadow(g, lx, ly + this.s(3.0F), lw, lh, this.s(10.0F), 0x8C000000);
        Px.round(g, lx, ly, lw, lh, this.s(7.0F), ArcanicMenu.panelBg());
        Px.outline(g, lx, ly, lw, lh, this.s(7.0F), ArcanicMenu.edgeMedium());
        if (this.sample != null) {
            int sx = (int) (mx * (double) this.sample.getWidth() / (double) Px.width());
            int sy = (int) (my * (double) this.sample.getHeight() / (double) Px.height());

            for (int j = 0; j < cells; j++) {
                for (int i = 0; i < cells; i++) {
                    int px = Math.max(0, Math.min(this.sample.getWidth() - 1, sx + i - cells / 2));
                    int py = Math.max(0, Math.min(this.sample.getHeight() - 1, sy + j - cells / 2));
                    Px.rect(g, lx + pad + i * cs, ly + pad + j * cs, cs, cs, this.sample.getPixel(px, py) | 0xFF000000);
                }
            }

            int mid = cells / 2;
            Px.outline(g, lx + pad + mid * cs - 1, ly + pad + mid * cs - 1, cs + 2, cs + 2, 1, -1);
        }

        int fs = Math.max(8, this.s(6.0F));
        int ty = ly + pad + grid + this.s(4.0F);
        Px.round(g, lx + pad, ty + (this.s(9.0F) - this.s(7.0F)) / 2, this.s(7.0F), this.s(7.0F), this.s(2.5F), c);
        Px.text(
                g,
                String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF),
                lx + pad + this.s(10.0F),
                ty + (this.s(9.0F) - fs) / 2,
                Px.Weight.BOLD,
                fs,
                ArcanicMenu.T().text());
    }

    private boolean onHue(double mx, double my) {
        return this.hit(mx, my, this.hueX() - 3.0F, this.svY() - 3.0F, 16.0F, 94.0F);
    }

    private boolean onAlpha(double mx, double my) {
        return this.enableAlpha && this.hit(mx, my, 5.0F, this.alphaY() - 4.0F, this.alphaW() + 6.0F, 18.0F);
    }

    private boolean onSquare(double mx, double my) {
        return this.hit(mx, my, 8.0F, this.svY(), this.svW(), 88.0F);
    }

    void click(double mx, double my, int button) {
        if (!this.closing) {
            if (this.picking) {
                if (button == 0 && this.sample != null) {
                    this.update(this.sampleAt(mx, my), true);
                    UiSound.play("select", 1.1F, 0.7F);
                }

                this.stopPick();
            } else if (!(mx < (double) this.x)
                    && !(mx >= (double) (this.x + this.w))
                    && !(my < (double) this.y)
                    && !(my >= (double) (this.y + this.h))) {
                this.commitEdit();
                int[] pal = Setting.Colour.PALETTE;

                for (int i = 0; i < pal.length; i++) {
                    if (this.hit(mx, my, 8.0F + (float) i * this.dotStep(pal.length), this.paletteY(), 10.0F, 10.0F)) {
                        if (button == 0) {
                            this.update(pal[i] | (this.enableAlpha ? this.colour & 0xFF000000 : 0xFF000000), true);
                            UiSound.play("click", 1.1F, 0.6F);
                        }

                        return;
                    }
                }

                List<Integer> recent = Setting.Colour.PRESETS;
                int n = Math.min(recent.size(), 11);
                float step = this.dotStep(12);

                for (int i = 0; i <= n; i++) {
                    if (this.hit(mx, my, 8.0F + (float) i * step, this.recentY(), 10.0F, 10.0F)) {
                        if (i == n) {
                            if (button == 0 && !recent.contains(this.colour)) {
                                recent.add(0, this.colour);

                                while (recent.size() > 11) {
                                    recent.remove(recent.size() - 1);
                                }

                                Config.dirty();
                                UiSound.play("pop");
                            }
                        } else if (button == 0) {
                            this.update(recent.get(i), true);
                            UiSound.play("click", 1.1F, 0.6F);
                        } else {
                            recent.remove(i);
                            Config.dirty();
                            UiSound.play("reset", 1.0F, 0.6F);
                        }

                        return;
                    }
                }

                if (button == 0) {
                    if (this.hit(mx, my, 8.0F + this.inner() - 13.0F, 8.5F, 13.0F, 13.0F)) {
                        UiSound.play("pop", 0.85F, 0.7F);
                        this.close();
                    } else if (this.hit(mx, my, 8.0F, 8.0F, 14.0F, 14.0F)) {
                        UiSound.play("click");
                        this.startPick();
                    } else if (this.initial != this.colour
                            && this.hit(mx, my, 27.0F, this.bottomY() + 31.0F - 12.0F, 14.0F, 14.0F)) {
                        this.update(this.initial, true);
                        UiSound.play("reset", 1.2F, 0.6F);
                    } else if (this.hit(mx, my, this.rightX(), this.themeY(), this.rightW(), 13.0F)) {
                        this.theme = !this.theme;
                        this.commit.accept(this.colour, this.theme);
                        Config.dirty();
                        UiSound.play(this.theme ? "toggle_on" : "toggle_off");
                    } else {
                        float fy = this.bottomY();
                        if (this.hit(mx, my, this.rightX(), fy, 17.0F, 13.0F)) {
                            this.rgbMode = false;
                            UiSound.play("tab");
                        } else if (this.hit(mx, my, this.rightX() + 17.0F, fy, 17.0F, 13.0F)) {
                            this.rgbMode = true;
                            UiSound.play("tab");
                        } else if (this.hit(
                                mx, my, this.rightX() + 34.0F + 3.0F, fy, this.rightW() - 34.0F - 3.0F, 13.0F)) {
                            this.editing = this.rgbMode ? this.rgb() : this.hex();
                            this.editingAlpha = false;
                            this.selectAll = true;
                            UiSound.play("click", 1.2F, 0.5F);
                        } else if (this.enableAlpha
                                && this.hit(mx, my, 8.0F + this.inner() - 28.0F, this.alphaY(), 28.0F, 10.0F)) {
                            this.editing = String.valueOf(Math.round(this.alpha * 100.0F));
                            this.editingAlpha = true;
                            this.selectAll = true;
                            UiSound.play("click", 1.2F, 0.5F);
                        } else {
                            if (this.onSquare(mx, my)) {
                                this.gesture = 2;
                            } else if (this.onHue(mx, my)) {
                                this.gesture = 1;
                            } else if (this.onAlpha(mx, my)) {
                                this.gesture = 3;
                            } else {
                                this.gesture = 4;
                                this.grabX = mx - (double) this.x;
                                this.grabY = my - (double) this.y;
                            }

                            if (this.gesture != 4) {
                                UiSound.play("grab", 1.1F, 0.5F);
                            }

                            this.drag(mx, my);
                        }
                    }
                }
            } else {
                this.close();
            }
        }
    }

    void drag(double mx, double my) {
        if (this.gesture != 0 && !this.closing) {
            if (this.gesture == 4) {
                this.x = (int) (mx - this.grabX);
                this.y = (int) (my - this.grabY);
                this.keepOnScreen();
            } else {
                if (this.gesture == 1) {
                    this.hue = Math.min(
                            0.9999F, clamp((my - (double) this.Y(this.svY())) / (double) (88.0F * this.scale)));
                }

                if (this.gesture == 2) {
                    this.sat = clamp((mx - (double) this.X(8.0F)) / (double) (this.svW() * this.scale));
                    this.value = 1.0F - clamp((my - (double) this.Y(this.svY())) / (double) (88.0F * this.scale));
                }

                if (this.gesture == 3) {
                    float r = 5.0F;
                    this.alpha = clamp(
                            (mx - (double) this.X(8.0F + r)) / (double) ((this.alphaW() - r * 2.0F) * this.scale));
                }

                if (this.compose() != this.colour) {
                    this.publish();
                }
            }
        }
    }

    void release() {
        if (this.gesture != 0 && this.gesture != 4) {
            UiSound.play("drop", 1.1F, 0.4F);
        }

        this.gesture = 0;
    }

    void wheel(double mx, double my, double amount) {
        if (!this.closing && !this.picking && amount != 0.0) {
            float d = (float) Math.signum(amount);
            if (this.onHue(mx, my)) {
                this.hue = (this.hue - d * 0.008F + 1.0F) % 1.0F;
            } else if (this.onAlpha(mx, my)) {
                this.alpha = clamp((double) (this.alpha + d * 0.02F));
            } else {
                if (!this.onSquare(mx, my)) {
                    return;
                }

                this.value = clamp((double) (this.value + d * 0.02F));
            }

            if (this.compose() != this.colour) {
                this.publish();
                UiSound.play("tick", 1.0F + d * 0.05F, 0.6F);
            }
        }
    }

    void character(CharacterEvent e) {
        if (this.editing != null) {
            String ch = new String(Character.toChars(e.codepoint()));
            boolean ok = this.editingAlpha
                    ? ch.matches("[0-9]")
                    : (this.rgbMode ? ch.matches("[0-9 ,]") : ch.matches("[0-9a-fA-F#]"));
            if (ok) {
                if (this.selectAll) {
                    this.editing = "";
                    this.selectAll = false;
                }

                if (this.editing.length() < (this.editingAlpha ? 3 : (this.rgbMode ? 11 : 7))) {
                    this.editing = this.editing + ch;
                    UiSound.play("type", 1.15F, 0.35F);
                }
            }
        }
    }

    private void commitEdit() {
        if (this.editing != null) {
            String v = this.editing.trim();
            this.editing = null;
            this.selectAll = false;

            try {
                if (this.editingAlpha) {
                    if (!v.isEmpty()) {
                        this.alpha = clamp((double) Integer.parseInt(v) / 100.0);
                        this.publish();
                    }

                    return;
                }

                if (this.rgbMode) {
                    String[] p = v.split("[ ,]+");
                    if (p.length == 3) {
                        this.update(
                                Math.min(255, Integer.parseInt(p[0])) << 16
                                        | Math.min(255, Integer.parseInt(p[1])) << 8
                                        | Math.min(255, Integer.parseInt(p[2]))
                                        | this.colour & 0xFF000000,
                                true);
                    }
                } else {
                    String hex = v.replaceFirst("^#", "");
                    if (hex.matches("[0-9a-fA-F]{3}")) {
                        hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2)
                                + hex.charAt(2);
                    }

                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        this.update((int) Long.parseLong(hex, 16) | this.colour & 0xFF000000, true);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    void key(KeyEvent e) {
        int k = e.key();
        boolean shift = (e.modifiers() & 1) != 0;
        if (this.editing == null) {
            if (k != 256 && k != 257 && k != 335) {
                float step = shift ? 0.05F : 0.01F;
                boolean moved = true;
                switch (k) {
                    case 262 -> this.sat = clamp((double) (this.sat + step));
                    case 263 -> this.sat = clamp((double) (this.sat - step));
                    case 264 -> this.value = clamp((double) (this.value - step));
                    case 265 -> this.value = clamp((double) (this.value + step));
                    default -> moved = false;
                }

                if (moved) {
                    if (this.compose() != this.colour) {
                        this.publish();
                    }

                } else if ((e.modifiers() & 2) != 0) {
                    KeyboardHandler kb = Minecraft.getInstance().keyboardHandler;
                    if (k == 67) {
                        kb.setClipboard(
                                String.format(Locale.ROOT, "#%06X%02X", this.colour & 0xFFFFFF, this.colour >>> 24));
                        UiSound.play("click", 1.2F, 0.5F);
                    }

                    if (k == 86) {
                        String hex = kb.getClipboard().trim().replaceFirst("^#", "");
                        if (!hex.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                            return;
                        }

                        long p = Long.parseLong(hex, 16);
                        this.update(
                                hex.length() == 6
                                        ? (int) p | this.colour & 0xFF000000
                                        : (int) (p >>> 8) | ((int) p & 255) << 24,
                                true);
                        UiSound.play("select", 1.1F, 0.6F);
                    }
                }
            } else {
                if (this.picking) {
                    this.stopPick();
                } else {
                    this.close();
                }
            }
        } else {
            if (k != 257 && k != 335 && k != 258) {
                if (k == 256) {
                    this.editing = null;
                    this.selectAll = false;
                } else if (k == 259 && !this.editing.isEmpty()) {
                    this.editing = this.selectAll ? "" : this.editing.substring(0, this.editing.length() - 1);
                    this.selectAll = false;
                    UiSound.play("type", 0.9F, 0.3F);
                }
            } else {
                this.commitEdit();
                UiSound.play("select", 1.1F, 0.6F);
            }
        }
    }

    private void startPick() {
        this.picking = true;
        Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget(), (image) -> Minecraft.getInstance()
                .execute(() -> {
                    if (this.picking && !this.closing) {
                        if (this.sample != null) {
                            this.sample.close();
                        }

                        this.sample = image;
                    } else {
                        image.close();
                    }
                }));
    }

    private int sampleAt(double mx, double my) {
        return this.sample == null
                ? this.colour
                : this.sample.getPixel(
                                Math.max(0, Math.min(this.sample.getWidth() - 1, (int)
                                        (mx * (double) this.sample.getWidth() / (double) Px.width()))),
                                Math.max(0, Math.min(this.sample.getHeight() - 1, (int)
                                        (my * (double) this.sample.getHeight() / (double) Px.height()))))
                        | 0xFF000000;
    }

    private void stopPick() {
        this.picking = false;
        if (this.sample != null) {
            this.sample.close();
            this.sample = null;
        }
    }

    void close() {
        if (!this.closing) {
            this.commitEdit();
            if (!this.theme && this.colour != this.initial) {
                List<Integer> recent = Setting.Colour.PRESETS;
                recent.removeIf((c) -> (c & 0xFFFFFF) == (this.colour & 0xFFFFFF));
                recent.add(0, this.colour | 0xFF000000);

                while (recent.size() > 11) {
                    recent.remove(recent.size() - 1);
                }

                Config.dirty();
            }

            this.closing = true;
            this.release();
            this.stopPick();
        }
    }

    boolean finished() {
        return this.closing && this.anim.get("open", 0.0F) < 0.01F;
    }

    private interface Row {
        void at(int y, int x0, int x1, float t);
    }
}
