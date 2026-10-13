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
package net.arcanic.visuals.core;

/** Colour tokens of the menu and HUD. */
public final class Theme {
    private Theme() {}

    /** fx: 0 none, 1 aurora, 2 matrix rain, 3 synthwave grid, 4 prism smoke, 5 vortex, 6 neon beams. */
    public record Palette(
            String name,
            int bg,
            int side,
            int card,
            int control,
            int border,
            int text,
            int muted,
            int dim,
            int a,
            int b,
            boolean light,
            int fx) {
        public boolean animated() {
            return fx != 0;
        }
    }

    public static final Palette[] PALETTES = {
        // night: a cool near-black, white hairline borders and cool greys, with our violet accent
        new Palette(
                "Обсидиан",
                0xF2090A0E,
                0xFF0C0D12,
                0xFF12141C,
                0xFF1E2028,
                0x18FFFFFF,
                0xFFF4F6FA,
                0xFFB0B8C4,
                0xFF757D8A,
                0xFF8B5CF6,
                0xFFB79CFF,
                false,
                0),
        new Palette(
                "Аметист",
                0xF20E0A16,
                0xFF140F1F,
                0xFF181224,
                0xFF241B33,
                0x1AE9D5FF,
                0xFFF6F1FF,
                0xFFA898C2,
                0xFF6B5C85,
                0xFFA855F7,
                0xFFE9D5FF,
                false,
                0),
        new Palette(
                "Полночь",
                0xF2080C16,
                0xFF0D1220,
                0xFF101727,
                0xFF19233A,
                0x18BFDBFE,
                0xFFF0F5FF,
                0xFF93A3C2,
                0xFF56637E,
                0xFF3B82F6,
                0xFF93C5FD,
                false,
                0),
        new Palette(
                "Закат",
                0xF2120C0A,
                0xFF1A110E,
                0xFF1E1410,
                0xFF2B1D17,
                0x18FED7AA,
                0xFFFFF5EE,
                0xFFC2A190,
                0xFF7E6355,
                0xFFF97316,
                0xFFFDBA74,
                false,
                0),
        new Palette(
                "Изумруд",
                0xF2081210,
                0xFF0D1916,
                0xFF0F1D1A,
                0xFF172B26,
                0x18A7F3D0,
                0xFFEFFFF9,
                0xFF8FB8AA,
                0xFF55786C,
                0xFF10B981,
                0xFF6EE7B7,
                false,
                0),
        new Palette(
                "Светлая",
                0xF5F6F6F9,
                0xFFFFFFFF,
                0xFFFFFFFF,
                0xFFEEEEF3,
                0x16000000,
                0xFF15151A,
                0xFF696978,
                0xFFA2A2AE,
                0xFF7C3AED,
                0xFFA78BFA,
                true,
                0),
        new Palette(
                "Аврора",
                0xF20B0916,
                0xC4110E1E,
                0xBC151127,
                0xD0211B38,
                0x22E0E7FF,
                0xFFF5F3FF,
                0xFFA5A0C8,
                0xFF67628A,
                0xFF8B5CF6,
                0xFF22D3EE,
                false,
                1),
        new Palette(
                "Матрица",
                0xF4040906,
                0xC4071009,
                0xBC0A140D,
                0xD0132219,
                0x2638FF7A,
                0xFFE9FFF0,
                0xFF8FBF9E,
                0xFF4F7A5E,
                0xFF22C55E,
                0xFF86EFAC,
                false,
                2),
        new Palette(
                "Синтвейв",
                0xF40B0514,
                0xC4140A22,
                0xBC170C28,
                0xD0261440,
                0x2AFF5FD2,
                0xFFFDF0FF,
                0xFFC4A0D8,
                0xFF7D5E92,
                0xFFEC4899,
                0xFFA855F7,
                false,
                3),
        new Palette(
                "Призма",
                0xF40A0716,
                0xC4120E26,
                0xBC171230,
                0xD022193F,
                0x26D8B4FE,
                0xFFF5F0FF,
                0xFFAFA2D2,
                0xFF6E6392,
                0xFFA78BFA,
                0xFFF0ABFC,
                false,
                4),
        new Palette(
                "Вихрь",
                0xF4050A14,
                0xC40A1222,
                0xBC0D1830,
                0xD0142440,
                0x2693C5FD,
                0xFFF0F6FF,
                0xFF9FB4D2,
                0xFF5D7092,
                0xFF38BDF8,
                0xFF818CF8,
                false,
                5),
        new Palette(
                "Неон",
                0xF406060E,
                0xC40C0C1A,
                0xBC0F0F22,
                0xD01A1A33,
                0x2822D3EE,
                0xFFF0FDFF,
                0xFF9EC3D0,
                0xFF5E7A88,
                0xFF22D3EE,
                0xFFF472B6,
                false,
                6),
    };
    public static int palette = 0;
    public static final int NIGHT = 0, LIGHT = 5;
    public static int style = 0;

    public static double menuScale = 1.0;
    public static boolean animations = true;
    public static int frontBackground = 4;
    public static int customA, customB;

    // ---- smooth switching: every token blends from the old palette to the new one
    private static Palette from;
    private static long changedAt;
    public static final long FADE_MS = 520;

    public static void setPalette(int i) {
        i = Math.floorMod(i, PALETTES.length);
        if (i == palette) return;
        from = p(); // the colours on screen right now, even mid-transition
        changedAt = System.currentTimeMillis();
        palette = i;
    }

    public static void setAccent(int a, int b, boolean fade) {
        if (fade) {
            from = p();
            changedAt = System.currentTimeMillis();
        }
        customA = a;
        customB = b;
    }

    public static float transition() {
        if (from == null) return 1;
        float t = (System.currentTimeMillis() - changedAt) / (float) FADE_MS;
        if (t >= 1) {
            from = null;
            return 1;
        }
        return t;
    }

    public static Palette base() {
        return PALETTES[Math.floorMod(palette, PALETTES.length)];
    }

    private static Palette target() {
        Palette b = base();
        if (customA == 0 && customB == 0) return b;
        return new Palette(
                b.name(),
                b.bg(),
                b.side(),
                b.card(),
                b.control(),
                b.border(),
                b.text(),
                b.muted(),
                b.dim(),
                customA != 0 ? customA : b.a(),
                customB != 0 ? customB : b.b(),
                b.light(),
                b.fx());
    }

    public static Palette p() {
        Palette to = target();
        float t = transition();
        if (t >= 1) return to;
        float e = t * t * (3 - 2 * t);
        Palette f = from;
        return new Palette(
                to.name(),
                mix(f.bg(), to.bg(), e),
                mix(f.side(), to.side(), e),
                mix(f.card(), to.card(), e),
                mix(f.control(), to.control(), e),
                mix(f.border(), to.border(), e),
                mix(f.text(), to.text(), e),
                mix(f.muted(), to.muted(), e),
                mix(f.dim(), to.dim(), e),
                mix(f.a(), to.a(), e),
                mix(f.b(), to.b(), e),
                e > .5f ? to.light() : f.light(),
                to.fx());
    }

    public static Palette fading() {
        return transition() < 1 ? from : null;
    }

    private static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24
                | (int) (ar + (br - ar) * t) << 16
                | (int) (ag + (bg - ag) * t) << 8
                | (int) (ab + (bb - ab) * t);
    }

    public static int accent() {
        return p().a();
    }

    public static int accent2() {
        return p().b();
    }

    public static int text() {
        return p().text();
    }

    public static int muted() {
        return p().muted();
    }

    public static int dim() {
        return p().dim();
    }

    public static int bg() {
        int c = p().bg();
        return style == 1 ? c | 0xFF000000 : c;
    }

    // compatibility with the HUD code
    public static int panel() {
        return bg();
    }

    public static int sidebar() {
        return p().side();
    }

    public static int card() {
        return p().card();
    }

    public static int line() {
        return p().border();
    }

    public static final int TEXT = 0xFFF1F1F6, MUTED = 0xFF9C9CAA, DIM = 0xFF5E5E6C;
}
