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
import java.util.Locale;
import java.util.function.BiConsumer;
import net.arcanic.visuals.core.*;
import net.arcanic.visuals.render.*;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * The colour picker: an eyedropper, a saturation/brightness square, hue and opacity bars, a HEX / RGB field and the
 * recent colours.
 */
final class ColourPicker {
    private final String title;
    private final boolean enableAlpha;
    private final BiConsumer<Integer, Boolean> commit;
    private final Anim anim = new Anim();
    private final float scale;
    private int x, y, w, h, colour, gesture;
    private double grabX, grabY;
    private float hue, sat, value, alpha;
    private boolean closing, picking, theme, rgbMode;
    private String editing; // text being typed into the value field (null = not editing)
    private boolean editingAlpha;
    private NativeImage sample;

    ColourPicker(String title, Setting.Colour setting) {
        this(title, setting.argb(), true, (c, th) -> {
            setting.set(c);
            setting.setTheme(th);
        });
        theme = setting.followsTheme();
    }

    ColourPicker(String title, int initial, boolean enableAlpha, BiConsumer<Integer, Boolean> commit) {
        this.title = title;
        this.enableAlpha = enableAlpha;
        this.commit = commit;
        scale = Math.max(1.2f, Math.min(3f, Px.height() / 540f));
        w = s(134);
        update(initial, false);
        anim.set("open", 0);
        layout();
    }

    ColourPicker at(double mx, double my) {
        x = (int) mx + s(8);
        y = (int) my - s(20);
        keepOnScreen();
        return this;
    }

    private static final float PAD = 7, SV_Y = 28, SV_H = 64, BAR_H = 3.5f;

    private float hueY() {
        return SV_Y + SV_H + 7;
    }

    private float alphaY() {
        return hueY() + 9;
    }

    private float fmtY() {
        return (enableAlpha ? alphaY() : hueY()) + 10;
    }

    private float recentY() {
        return fmtY() + 19;
    }

    private int rows() {
        return Math.max(1, (Setting.Colour.PRESETS.size() + 1 + 9) / 10);
    }

    private void layout() {
        h = s(recentY() + 10 + rows() * 12 + PAD - 2);
    }

    private int s(float v) {
        return Math.round(v * scale);
    }

    private int X(float u) {
        return x + s(u);
    }

    private int Y(float u) {
        return y + s(u);
    }

    private boolean hit(double mx, double my, float xx, float yy, float ww, float hh) {
        return mx >= X(xx) && mx < X(xx + ww) && my >= Y(yy) && my < Y(yy + hh);
    }

    private float inner() {
        return w / scale - PAD * 2;
    }

    private void keepOnScreen() {
        x = Math.max(4, Math.min(Px.width() - w - 4, x));
        y = Math.max(4, Math.min(Px.height() - h - 4, y));
    }

    private static float clamp(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }

    private void update(int c, boolean save) {
        colour = c;
        float[] hsv = Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
        if (hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0];
        sat = hsv[1];
        value = hsv[2];
        alpha = (c >>> 24) / 255f;
        if (save) publish();
    }

    private int compose() {
        return (Color.HSBtoRGB(hue, sat, value) & 0xFFFFFF) | ((enableAlpha ? Math.round(alpha * 255) : 255) << 24);
    }

    private void publish() {
        colour = compose();
        theme = false;
        commit.accept(colour, false);
        Config.dirty();
    }

    private String hex() {
        return String.format(Locale.ROOT, "#%06X", colour & 0xFFFFFF);
    }

    private String rgb() {
        return (colour >> 16 & 255) + " " + (colour >> 8 & 255) + " " + (colour & 255);
    }

    void render(GuiGraphics g, double mx, double my) {
        anim.frame();
        drag(mx, my);
        layout();
        float open = anim.to("open", closing ? 0 : 1, 16), oldFade = Px.fade;
        Px.fade *= Math.min(1, open * 1.3f);
        g.pose().pushMatrix();
        float k = .94f + .06f * Anim.easeOutCubic(open);
        g.pose().translate(x + w / 2f, y);
        g.pose().scale(k, k);
        g.pose().translate(-x - w / 2f, -y);
        var t = Theme.p();
        boolean lt = t.light();
        int bg = lt ? 0xFFFFFFFF : 0xF20C0D12, line = lt ? 0x1E000000 : 0x18FFFFFF, muted = lt ? t.muted() : 0xFF757D8A;
        int acc = Theme.accent2() | 0xFF000000;
        g.nextStratum();
        Px.shadow(g, x, y + s(3), w, h, s(9), 0x46000000);
        boolean frost = !lt && false;
        if (frost) Frost.backdrop(g, x, y, w, h, s(8));
        Px.round(g, x, y, w, h, s(8), frost ? 0xDC0C0D12 : bg);
        Px.outline(g, x, y, w, h, s(8), line);
        g.nextStratum();

        // Pick a colour + eyedropper, hex on the right; the setting's name and the "follow theme" ring under it
        int f55 = Math.max(8, s(5.5f));
        String head = Lang.tr("Выбор цвета");
        Px.text(g, head, X(PAD), Y(PAD), Weight.SEMIBOLD, f55, acc);
        int pipX = X(PAD) + Px.textWidth(head, Weight.SEMIBOLD, f55) + s(3);
        boolean pipHv = hit(mx, my, (pipX - x) / scale, PAD - 1, 8, 8);
        Px.icon(g, "w.pipette", pipX, Y(PAD - .5f), Math.max(8, s(6.5f)), pipHv ? t.text() : acc);
        Px.textRight(g, hex(), X(PAD + inner()), Y(PAD), Weight.BOLD, f55, acc);
        int f75 = Math.max(9, s(7.5f));
        Px.text(
                g,
                Px.ellipsize(Lang.tr(title), Weight.SEMIBOLD, f75, s(inner() - 16)),
                X(PAD),
                Y(16),
                Weight.SEMIBOLD,
                f75,
                t.text());
        // theme ring: filled centre when the colour follows the theme accent
        int rr = s(4), rcx = X(PAD + inner()) - rr, rcy = Y(16) + f75 / 2;
        float th = anim.to("theme", theme ? 1 : 0, 14);
        Px.circle(g, rcx, rcy, rr, Theme.accent() | 0xFF000000);
        if (th < .99f)
            Px.circle(g, rcx, rcy, Math.max(1, Math.round(rr * .6f * (1 - th))), lt ? 0xFFFFFFFF : 0xFF12141C);

        // saturation / brightness square with rounded corners (the corners are masked with the panel colour)
        int sx = X(PAD), sy = Y(SV_Y), sw = s(inner()), sh = s(SV_H), cr = s(3);
        Px.hgrad(g, sx, sy, sw, sh, 0xFFFFFFFF, Color.HSBtoRGB(hue, 1, 1) | 0xFF000000);
        g.nextStratum();
        Px.vgrad(g, sx, sy, sw, sh, 0x00000000, 0xFF000000);
        g.nextStratum();
        int mask = frost ? 0xFF0E0F14 : bg | 0xFF000000;
        Px.wedge(g, sx, sy, cr, Px.TL, mask);
        Px.wedge(g, sx + sw - cr, sy, cr, Px.TR, mask);
        Px.wedge(g, sx, sy + sh - cr, cr, Px.BL, mask);
        Px.wedge(g, sx + sw - cr, sy + sh - cr, cr, Px.BR, mask);
        float ds = anim.to("sat", sat, 16),
                dv = anim.to("value", value, 16),
                dh = anim.to("hue", hue, 16),
                da = anim.to("alpha", alpha, 16);
        ring(g, sx + Math.round(sw * ds), sy + Math.round(sh * (1 - dv)), colour | 0xFF000000);

        // hue bar: six gradient pieces through the spectrum, rounded ends
        int by = Y(hueY()), bh = Math.max(3, s(BAR_H));
        for (int i = 0; i < 6; i++) {
            int a = Math.round(sw * i / 6f), b = Math.round(sw * (i + 1) / 6f);
            Px.hgrad(
                    g,
                    sx + a,
                    by,
                    b - a,
                    bh,
                    Color.HSBtoRGB(i / 6f, 1, 1) | 0xFF000000,
                    Color.HSBtoRGB((i + 1) / 6f, 1, 1) | 0xFF000000);
        }
        ring(g, sx + Math.round(sw * dh), by + bh / 2, Color.HSBtoRGB(dh, 1, 1) | 0xFF000000);
        if (enableAlpha) {
            int ay = Y(alphaY());
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/picker/empty.png"),
                    sx,
                    ay,
                    0,
                    0,
                    sw,
                    bh,
                    sw,
                    bh,
                    sw,
                    bh,
                    Px.am(0xFFFFFFFF, Px.fade));
            g.nextStratum();
            Px.hgrad(g, sx, ay, sw, bh, colour & 0xFFFFFF, colour | 0xFF000000);
            ring(g, sx + Math.round(sw * da), ay + bh / 2, colour | 0xFF000000);
        }

        // HEX | RGB, the value field and the opacity
        int fy = Y(fmtY()), fh = s(12), f6 = Math.max(8, s(6));
        int segW = s(17), segX = X(PAD);
        Px.round(g, segX, fy, segW * 2, fh, s(6), lt ? 0x10000000 : 0x6812141A);
        Px.outline(g, segX, fy, segW * 2, fh, s(6), line);
        float fm = anim.to("fmt", rgbMode ? 1 : 0, 16);
        int thx = segX + s(1) + Math.round((segW - s(1)) * fm);
        Px.round(g, thx, fy + s(1), segW - s(1), fh - s(2), s(5), Theme.accent() | 0xFF000000);
        Px.textCenter(
                g, "HEX", segX + segW / 2, fy + (fh - f6) / 2, Weight.SEMIBOLD, f6, Px.mix(0xFFFFFFFF, muted, fm));
        Px.textCenter(
                g,
                "RGB",
                segX + segW + segW / 2,
                fy + (fh - f6) / 2,
                Weight.SEMIBOLD,
                f6,
                Px.mix(muted, 0xFFFFFFFF, fm));
        int alphaW = enableAlpha ? s(24) : 0,
                fieldX = segX + segW * 2 + s(3),
                fieldW = X(PAD + inner()) - fieldX - (enableAlpha ? alphaW + s(3) : 0);
        field(
                g,
                fieldX,
                fy,
                fieldW,
                fh,
                editing != null && !editingAlpha ? editing + caret() : rgbMode ? rgb() : hex(),
                editing != null && !editingAlpha,
                f6,
                t.text(),
                line);
        if (enableAlpha)
            field(
                    g,
                    fieldX + fieldW + s(3),
                    fy,
                    alphaW,
                    fh,
                    editing != null && editingAlpha ? editing + caret() : Math.round(alpha * 100) + "%",
                    editing != null && editingAlpha,
                    f6,
                    t.text(),
                    line);

        // Recent colours: the palette as dots, the current colour ringed; the last dot adds the current colour
        Px.text(g, Lang.tr("Предыдущие цвета"), X(PAD), Y(recentY()), Weight.SEMIBOLD, f55, muted);
        var presets = Setting.Colour.PRESETS;
        for (int i = 0; i <= presets.size() && i < 20; i++) {
            float ux = PAD + (i % 10) * 12, uy = recentY() + 9 + (i / 10) * 12;
            int cx = X(ux) + s(4.25f), cy = Y(uy) + s(4.25f), r = s(4.25f);
            if (i == presets.size()) {
                boolean hv = hit(mx, my, ux, uy, 8.5f, 8.5f);
                Px.ring(g, cx, cy, r, hv ? t.text() : line | 0x30000000);
                Px.icon(g, "w.plus", cx - s(3), cy - s(3), Math.max(8, s(6)), hv ? t.text() : muted);
                continue;
            }
            int c = presets.get(i) | 0xFF000000;
            boolean cur = (presets.get(i) & 0xFFFFFF) == (colour & 0xFFFFFF);
            float sel = anim.to("sw" + i, cur ? 1 : 0, 14);
            Px.circle(g, cx, cy, r, c);
            if (sel > .02f) {
                Px.ring(g, cx, cy, r + Math.max(1, s(1.2f)), Px.am(t.text(), sel));
            }
        }
        g.pose().popMatrix();
        Px.fade = oldFade;

        if (picking) {
            int c = sampleAt(mx, my),
                    tx = (int) Math.max(0, Math.min(Px.width() - s(90), mx + s(8))),
                    ty = (int) Math.max(0, Math.min(Px.height() - s(20), my + s(8)));
            g.nextStratum();
            Px.round(g, tx, ty, s(86), s(18), s(6), 0xF20C0D12);
            Px.outline(g, tx, ty, s(86), s(18), s(6), 0x18FFFFFF);
            Px.circle(g, tx + s(9), ty + s(9), s(5), c);
            Px.text(
                    g,
                    String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF),
                    tx + s(18),
                    ty + (s(18) - f6) / 2,
                    Weight.BOLD,
                    f6,
                    0xFFF4F6FA);
        }
    }

    private String caret() {
        return (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "";
    }

    private void ring(GuiGraphics g, int cx, int cy, int c) {
        int r = s(3.5f);
        Px.circle(g, cx, cy, r + 1, 0x60000000);
        Px.circle(g, cx, cy, r, 0xFFFFFFFF);
        Px.circle(g, cx, cy, Math.max(1, r - s(1.2f)), c);
    }

    private void field(
            GuiGraphics g, int fx, int fy, int fw, int fh, String text, boolean active, int fs, int colour, int line) {
        boolean lt = Theme.p().light();
        Px.round(g, fx, fy, fw, fh, s(6), lt ? 0x10000000 : 0x580E1016);
        Px.outline(g, fx, fy, fw, fh, s(6), active ? Theme.accent() | 0xFF000000 : line);
        Px.textCenter(g, text, fx + fw / 2, fy + (fh - fs) / 2, Weight.SEMIBOLD, fs, colour);
    }

    void click(double mx, double my, int button) {
        if (closing) return;
        if (picking) {
            if (button == 0 && sample != null) update(sampleAt(mx, my), true);
            stopPick();
            return;
        }
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            close();
            return;
        }
        var presets = Setting.Colour.PRESETS;
        for (int i = 0; i <= presets.size() && i < 20; i++) {
            float ux = PAD + (i % 10) * 12, uy = recentY() + 9 + (i / 10) * 12;
            if (!hit(mx, my, ux, uy, 8.5f, 8.5f)) continue;
            if (i == presets.size()) {
                if (button == 0 && !presets.contains(colour)) {
                    presets.add(0, colour);
                    Config.dirty();
                    UiSound.play("click");
                }
            } else if (button == 0) {
                update(presets.get(i), true);
                UiSound.play("click", 1.1f, .6f);
            } else {
                presets.remove(i);
                Config.dirty();
                UiSound.play("reset");
            }
            return;
        }
        if (button != 0) return;
        commitEdit();
        float headW = Px.textWidth(Lang.tr("Выбор цвета"), Weight.SEMIBOLD, Math.max(8, s(5.5f))) / scale;
        if (hit(mx, my, PAD + headW + 1, PAD - 1, 9, 9)) {
            startPick();
            return;
        }
        if (hit(mx, my, PAD + inner() - 9, 14, 9, 10)) { // theme ring
            theme = !theme;
            if (theme) commit.accept(colour, true);
            else commit.accept(colour, false);
            Config.dirty();
            UiSound.play(theme ? "toggle_on" : "toggle_off");
            return;
        }
        float fy = fmtY();
        if (hit(mx, my, PAD, fy, 17, 12)) {
            rgbMode = false;
            UiSound.play("click");
            return;
        }
        if (hit(mx, my, PAD + 17, fy, 17, 12)) {
            rgbMode = true;
            UiSound.play("click");
            return;
        }
        float alphaW = enableAlpha ? 24 : 0,
                fieldX = PAD + 37,
                fieldW = PAD + inner() - fieldX - (enableAlpha ? alphaW + 3 : 0);
        if (hit(mx, my, fieldX, fy, fieldW, 12)) {
            editing = "";
            editingAlpha = false;
            return;
        }
        if (enableAlpha && hit(mx, my, fieldX + fieldW + 3, fy, alphaW, 12)) {
            editing = "";
            editingAlpha = true;
            return;
        }
        if (hit(mx, my, PAD, SV_Y, inner(), SV_H)) gesture = 2;
        else if (hit(mx, my, PAD - 2, hueY() - 2, inner() + 4, BAR_H + 4)) gesture = 1;
        else if (enableAlpha && hit(mx, my, PAD - 2, alphaY() - 2, inner() + 4, BAR_H + 4)) gesture = 3;
        else {
            gesture = 4;
            grabX = mx - x;
            grabY = my - y;
        }
        drag(mx, my);
    }

    void drag(double mx, double my) {
        if (gesture == 0 || closing) return;
        if (gesture == 4) {
            x = (int) (mx - grabX);
            y = (int) (my - grabY);
            keepOnScreen();
            return;
        }
        float u = (float) ((mx - X(PAD)) / (inner() * scale));
        if (gesture == 1) hue = Math.min(.9999f, clamp(u));
        if (gesture == 2) {
            sat = clamp(u);
            value = 1 - clamp((my - Y(SV_Y)) / (SV_H * scale));
        }
        if (gesture == 3) alpha = clamp(u);
        if (compose() != colour) publish();
    }

    void release() {
        gesture = 0;
    }

    void wheel(double amount) {}

    void character(CharacterEvent e) {
        if (editing == null) return;
        String ch = new String(Character.toChars(e.codepoint()));
        boolean ok = editingAlpha ? ch.matches("[0-9]") : rgbMode ? ch.matches("[0-9 ,]") : ch.matches("[0-9a-fA-F#]");
        if (ok && editing.length() < (editingAlpha ? 3 : rgbMode ? 11 : 7)) editing += ch;
    }

    private void commitEdit() {
        if (editing == null) return;
        String v = editing.trim();
        editing = null;
        try {
            if (editingAlpha) {
                if (!v.isEmpty()) {
                    alpha = clamp(Integer.parseInt(v) / 100.0);
                    publish();
                }
                return;
            }
            if (rgbMode) {
                String[] p = v.split("[ ,]+");
                if (p.length == 3)
                    update(
                            0xFF000000
                                    | Math.min(255, Integer.parseInt(p[0])) << 16
                                    | Math.min(255, Integer.parseInt(p[1])) << 8
                                    | Math.min(255, Integer.parseInt(p[2]))
                                    | (colour & 0xFF000000),
                            true);
            } else {
                String hex = v.replaceFirst("^#", "");
                if (hex.matches("[0-9a-fA-F]{6}")) update((int) Long.parseLong(hex, 16) | (colour & 0xFF000000), true);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    void key(KeyEvent e) {
        if (editing != null) {
            if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER || e.key() == GLFW.GLFW_KEY_TAB)
                commitEdit();
            else if (e.key() == GLFW.GLFW_KEY_ESCAPE) editing = null;
            else if (e.key() == GLFW.GLFW_KEY_BACKSPACE && !editing.isEmpty())
                editing = editing.substring(0, editing.length() - 1);
            return;
        }
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (picking) stopPick();
            else close();
            return;
        }
        if ((e.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return;
        var kb = Minecraft.getInstance().keyboardHandler;
        if (e.key() == GLFW.GLFW_KEY_C)
            kb.setClipboard(String.format(Locale.ROOT, "#%06X%02X", colour & 0xFFFFFF, colour >>> 24));
        if (e.key() == GLFW.GLFW_KEY_V) {
            String hex = kb.getClipboard().trim().replaceFirst("^#", "");
            if (!hex.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) return;
            long p = Long.parseLong(hex, 16);
            update(hex.length() == 6 ? (int) p | 0xFF000000 : (int) (p >>> 8) | ((int) p & 255) << 24, true);
        }
    }

    private void startPick() {
        picking = true;
        Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget(), image -> Minecraft.getInstance()
                .execute(() -> {
                    if (picking && !closing) {
                        if (sample != null) sample.close();
                        sample = image;
                    } else image.close();
                }));
    }

    private int sampleAt(double mx, double my) {
        if (sample == null) return colour;
        return sample.getPixel(
                        Math.max(0, Math.min(sample.getWidth() - 1, (int) (mx * sample.getWidth() / Px.width()))),
                        Math.max(0, Math.min(sample.getHeight() - 1, (int) (my * sample.getHeight() / Px.height()))))
                | 0xFF000000;
    }

    private void stopPick() {
        picking = false;
        if (sample != null) {
            sample.close();
            sample = null;
        }
    }
    /** Closing remembers the colour among Recent colours (newest first, ten at most). */
    void close() {
        if (!closing && !theme) {
            var presets = Setting.Colour.PRESETS;
            presets.removeIf(c -> (c & 0xFFFFFF) == (colour & 0xFFFFFF));
            presets.add(0, colour | 0xFF000000);
            while (presets.size() > 10) presets.remove(presets.size() - 1);
            Config.dirty();
        }
        closing = true;
        commitEdit();
        release();
        stopPick();
    }

    boolean finished() {
        return closing && anim.get("open", 0) < .01f;
    }
}
