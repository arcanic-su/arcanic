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
package net.arcanic.visuals.render;

import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Small drawing kit on top of GuiGraphics: rounded panels, horizontal gradients, colour maths. */
public final class Draw {
    private Draw() {}

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Filled rectangle with rounded corners (radius in GUI pixels). */
    public static void round(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - .5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
    }
    /** One-pixel rounded outline. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        g.fill(x + r, y, x + w - r, y + 1, c);
        g.fill(x + r, y + h - 1, x + w - r, y + h, c);
        g.fill(x, y + r, x + 1, y + h - r, c);
        g.fill(x + w - 1, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - .5;
            int a = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            double dy2 = r - i - 1.5;
            int b = i + 1 < r ? (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy2 * dy2))) : a;
            int len = Math.max(1, a - b + 1);
            g.fill(x + b, y + i, x + b + len, y + i + 1, c);
            g.fill(x + w - b - len, y + i, x + w - b, y + i + 1, c);
            g.fill(x + b, y + h - 1 - i, x + b + len, y + h - i, c);
            g.fill(x + w - b - len, y + h - 1 - i, x + w - b, y + h - i, c);
        }
    }

    public static void hgrad(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0) return;
        for (int i = 0; i < w; i++) g.fill(x + i, y, x + i + 1, y + h, mix(c1, c2, w == 1 ? 0 : i / (float) (w - 1)));
    }

    public static void pill(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(h / 2, w / 2);
        for (int i = 0; i < w; i++) {
            int inset = 0;
            if (i < r || i >= w - r) {
                double dx = i < r ? r - i - .5 : i - (w - r) + .5;
                inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dx * dx)));
            }
            g.fill(x + i, y + inset, x + i + 1, y + h - inset, mix(c1, c2, w == 1 ? 0 : i / (float) (w - 1)));
        }
    }

    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int c, int layers) {
        for (int i = layers; i >= 1; i--)
            round(g, x - i, y - i, w + i * 2, h + i * 2, r + i, alpha(c, (int) (26f / i)));
    }

    public static void text(GuiGraphics g, String s, int x, int y, int c) {
        g.drawString(font(), s, x, y, c, false);
    }

    public static void textShadow(GuiGraphics g, String s, int x, int y, int c) {
        g.drawString(font(), s, x, y, c, true);
    }

    public static void centered(GuiGraphics g, String s, int cx, int y, int c) {
        g.drawString(font(), s, cx - font().width(s) / 2, y, c, false);
    }

    public static int width(String s) {
        return font().width(s);
    }

    public static int alpha(int c, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
    }

    public static int am(int c, float k) {
        return alphaMul(c, k);
    }

    public static int alphaMul(int c, float k) {
        return alpha(c, (int) ((c >>> 24) * Math.max(0, Math.min(1, k))));
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24
                | (int) (ar + (br - ar) * t) << 16
                | (int) (ag + (bg - ag) * t) << 8
                | (int) (ab + (bb - ab) * t);
    }

    public static int rainbow(float offset, float speed) {
        float h = (System.currentTimeMillis() % (long) (4000 / Math.max(.05f, speed))) / (4000f / Math.max(.05f, speed))
                + offset;
        return 0xFF000000 | Color.HSBtoRGB(h % 1f, .65f, 1f) & 0xFFFFFF;
    }

    public static float approach(float cur, float target, float speed) {
        float d = target - cur;
        return Math.abs(d) < .002f ? target : cur + d * Math.min(1, speed);
    }
}
