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
package net.arcanic.visuals.fx;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.render.Px;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

/**
 * The crosshair: six shapes drawn on the screen's own pixel grid, a dark outline, outer lines, a centre dot, the attack
 * cooldown as a bar, a ring or a spread, a tint on targets. Every change glides: a new shape fades in over the old one
 * and the numbers slide. Ready-made presets, the player's own saved ones, and codes to share them (Valorant codes are
 * read too).
 */
public final class Crosshair extends Module {
    // ------------------------------------------------------------------ the shapes

    public enum Style {
        CROSS("Крест", "Четыре линии вокруг точки прицела", true, true, false, true),
        T_SHAPE("Т-образный", "Крест без верхней линии: цель видно лучше", true, false, false, true),
        DOT("Точка", "Одна точка, самый простой", false, false, false, false),
        CIRCLE("Круг", "Кольцо вокруг точки прицела", false, false, true, true),
        CIRCLE_DOT("Круг с точкой", "Кольцо с точкой в центре", false, false, true, false),
        CHEVRON("Уголок", "Уголок вверх с вершиной на точке прицела", false, false, false, true);

        public final String title;
        public final String hint;
        final boolean lines;
        final boolean topLine;
        final boolean ring;
        final boolean optionalDot;

        Style(String title, String hint, boolean lines, boolean topLine, boolean ring, boolean optionalDot) {
            this.title = title;
            this.hint = hint;
            this.lines = lines;
            this.topLine = topLine;
            this.ring = ring;
            this.optionalDot = optionalDot;
        }

        boolean alwaysDot() {
            return this == CIRCLE_DOT;
        }

        boolean usesThickness() {
            return this != DOT;
        }

        boolean usesGap() {
            return this.lines;
        }

        static Style byTitle(String t) {
            for (Style s : values()) if (s.title.equals(t)) return s;
            return CROSS;
        }

        static Style byName(String n) {
            for (Style s : values()) if (s.name().equalsIgnoreCase(n)) return s;
            return null;
        }

        static String[] titles() {
            String[] out = new String[values().length];
            for (int i = 0; i < out.length; i++) out[i] = values()[i].title;
            return out;
        }
    }

    /** Everything that makes a crosshair, in crosshair pixels. */
    public record Spec(
            Style style,
            int colour,
            int size,
            int thickness,
            int gap,
            boolean outline,
            float outlineOpacity,
            int outlineThickness,
            float opacity,
            boolean centreDot,
            boolean outerLines,
            int outerLength,
            int outerGap) {
        public static final Spec DEFAULT = new Spec(Style.CROSS, -1, 6, 2, 3, true, 1.0F, 1, 1.0F, false, false, 2, 10);

        Spec sane() {
            return new Spec(
                    this.style,
                    this.colour | 0xFF000000,
                    clamp(this.size, 1, 30),
                    clamp(this.thickness, 1, 10),
                    clamp(this.gap, 0, 30),
                    this.outline,
                    Math.max(0.0F, Math.min(1.0F, this.outlineOpacity)),
                    clamp(this.outlineThickness, 1, 4),
                    Math.max(0.1F, Math.min(1.0F, this.opacity)),
                    this.centreDot,
                    this.outerLines,
                    clamp(this.outerLength, 1, 20),
                    clamp(this.outerGap, 0, 40));
        }

        boolean hasDot() {
            return this.style.alwaysDot() || this.style.optionalDot && this.centreDot;
        }

        private static int clamp(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }

    public enum Preset {
        CLASSIC(
                "Классика",
                "Белый крест с тонкой обводкой",
                new Spec(Style.CROSS, -1, 6, 2, 3, true, 1.0F, 1, 1.0F, false, false, 2, 10)),
        DOT(
                "Точка",
                "Маленькая бирюзовая точка",
                new Spec(Style.DOT, -16711681, 4, 2, 0, true, 1.0F, 1, 1.0F, false, false, 2, 10)),
        CIRCLE(
                "Круг",
                "Тонкое кольцо с точкой",
                new Spec(Style.CIRCLE_DOT, -1, 9, 1, 0, true, 0.8F, 1, 1.0F, false, false, 2, 10)),
        VALORANT(
                "Valorant",
                "Бирюзовый крест с короткими внешними линиями",
                new Spec(Style.CROSS, -16711681, 5, 2, 3, true, 0.8F, 1, 1.0F, false, true, 2, 10)),
        CS(
                "CS",
                "Тонкий зелёный крест с маленьким зазором",
                new Spec(Style.CROSS, -16711936, 5, 1, 2, true, 1.0F, 1, 1.0F, false, false, 2, 10)),
        SNIPER(
                "Снайпер",
                "Длинный тонкий крест с точкой",
                new Spec(Style.CROSS, -49088, 12, 1, 5, true, 1.0F, 1, 1.0F, true, false, 2, 10));

        public final String title;
        public final String hint;
        public final Spec spec;

        Preset(String title, String hint, Spec spec) {
            this.title = title;
            this.hint = hint;
            this.spec = spec;
        }
    }

    /** One-click high-contrast colours. */
    public static final int[] SWATCHES = {-1, -16711936, -5374161, -256, -24576, -49088, -65281, -16711681};

    static final String[] COOLDOWNS = {"Полоса", "Кольцо", "Разлёт", "Нет"};
    static final String[] SCALES = {"Авто", "1x", "2x", "3x", "4x"};

    // ------------------------------------------------------------------ the settings

    public final Setting.Mode style = this.add(new Setting.Mode("style", "Форма", Style.CROSS.title, Style.titles()))
            .showIf(() -> false);
    public final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", false, -1));
    public final Setting.Num size = this.add(new Setting.Num("size", "Размер", 6, 1, 30, 1, ""));
    public final Setting.Num thickness = this.add(new Setting.Num("thickness", "Толщина", 2, 1, 10, 1, ""))
            .showIf(() -> this.shape().usesThickness());
    public final Setting.Num gap = this.add(new Setting.Num("gap", "Зазор", 3, 0, 30, 1, ""))
            .showIf(() -> this.shape().usesGap());
    public final Setting.Bool outline = this.add(new Setting.Bool("outline", "Обводка", true));
    public final Setting.Mode scale = this.add(new Setting.Mode("scale", "Пиксель", "Авто", SCALES));
    public final Setting.Mode cooldown =
            this.add(new Setting.Mode("cooldown", "Перезарядка удара", "Полоса", COOLDOWNS));
    public final Setting.Bool advanced = this.add(new Setting.Bool("advanced", "Тонкая настройка", false));
    public final Setting.Num opacity = this.add(new Setting.Num("opacity", "Прозрачность", 1, 0.1, 1, 0.05, ""))
            .showIf(this.advanced::on);
    public final Setting.Num outlineOpacity = this.add(
                    new Setting.Num("outline_opacity", "Прозрачность обводки", 1, 0, 1, 0.05, ""))
            .showIf(() -> this.advanced.on() && this.outline.on());
    public final Setting.Num outlineThickness = this.add(
                    new Setting.Num("outline_thickness", "Толщина обводки", 1, 1, 4, 1, ""))
            .showIf(() -> this.advanced.on() && this.outline.on());
    public final Setting.Bool centreDot = this.add(new Setting.Bool("center_dot", "Точка в центре", false))
            .showIf(() -> this.advanced.on() && this.shape().optionalDot);
    public final Setting.Bool outerLines = this.add(new Setting.Bool("outer_lines", "Внешние линии", false))
            .showIf(() -> this.advanced.on() && this.shape().lines);
    public final Setting.Num outerLength = this.add(new Setting.Num("outer_length", "Длина внешних", 2, 1, 20, 1, ""))
            .showIf(() -> this.advanced.on() && this.shape().lines && this.outerLines.on());
    public final Setting.Num outerGap = this.add(new Setting.Num("outer_gap", "Зазор внешних", 10, 0, 40, 1, ""))
            .showIf(() -> this.advanced.on() && this.shape().lines && this.outerLines.on());
    public final Setting.Bool targetTint =
            this.add(new Setting.Bool("target", "Цвет на цели", true)).showIf(this.advanced::on);
    public final Setting.Colour targetColour = this.add(new Setting.Colour("target_color", "Цвет цели", false, -49088))
            .showIf(() -> this.advanced.on() && this.targetTint.on());
    public final Setting.Bool thirdPerson = this.add(new Setting.Bool("third_person", "От третьего лица", false))
            .showIf(this.advanced::on);

    public Crosshair() {
        super("crosshair", "Прицел", "Формы, пресеты, перезарядка и коды для друзей", Category.COMBAT, false);
    }

    public Style shape() {
        return Style.byTitle(this.style.get());
    }

    public Spec current() {
        return new Spec(
                        this.shape(),
                        this.colour.argb(),
                        this.size.i(),
                        this.thickness.i(),
                        this.gap.i(),
                        this.outline.on(),
                        this.outlineOpacity.f(),
                        this.outlineThickness.i(),
                        this.opacity.f(),
                        this.centreDot.on(),
                        this.outerLines.on(),
                        this.outerLength.i(),
                        this.outerGap.i())
                .sane();
    }

    public void apply(Spec s) {
        this.style.set(s.style.title);
        this.colour.setTheme(false);
        this.colour.set(s.colour | 0xFF000000);
        this.size.set((double) s.size);
        this.thickness.set((double) s.thickness);
        this.gap.set((double) s.gap);
        this.outline.set(s.outline);
        this.outlineOpacity.set((double) s.outlineOpacity);
        this.outlineThickness.set((double) s.outlineThickness);
        this.opacity.set((double) s.opacity);
        this.centreDot.set(s.centreDot);
        this.outerLines.set(s.outerLines);
        this.outerLength.set((double) s.outerLength);
        this.outerGap.set((double) s.outerGap);
        Config.dirty();
    }

    public boolean is(Spec s) {
        Spec c = this.current();
        return c.style == s.style
                && (c.colour | 0xFF000000) == (s.colour | 0xFF000000)
                && c.size == s.size
                && c.thickness == s.thickness
                && c.gap == s.gap
                && c.outline == s.outline
                && c.centreDot == s.centreDot
                && c.outerLines == s.outerLines;
    }

    // ------------------------------------------------------------------ what is drawn: every number glides

    /** The crosshair as drawn this frame: the settings eased in, the shape cross-faded. */
    static final class Frame {
        Style style = Style.CROSS;
        Style previous = Style.CROSS;
        float blend = 1.0F;
        int colour = -1;
        float size;
        float thickness;
        float gap;
        float outline;
        float outlineOpacity;
        float outlineThickness;
        float opacity;
        float dot;
        float outer;
        float outerLength;
        float outerGap;
        float extent;
    }

    static final class Animator {
        final Frame f = new Frame();
        private boolean primed;
        private long last;
        private int colourFrom;
        private int colourTo;
        private float colourK = 1.0F;

        Frame update(Spec s) {
            long now = System.nanoTime();
            float dt = this.primed ? Math.min(0.1F, (now - this.last) / 1.0E9F) : 0.0F;
            this.last = now;
            if (!this.primed) {
                this.primed = true;
                this.f.style = this.f.previous = s.style;
                this.f.blend = 1.0F;
                this.colourFrom = this.colourTo = this.f.colour = s.colour;
                this.f.size = s.size;
                this.f.thickness = s.thickness;
                this.f.gap = s.gap;
                this.f.outline = s.outline ? 1 : 0;
                this.f.outlineOpacity = s.outlineOpacity;
                this.f.outlineThickness = s.outlineThickness;
                this.f.opacity = s.opacity;
                this.f.dot = s.hasDot() ? 1 : 0;
                this.f.outer = s.outerLines && s.style.lines ? 1 : 0;
                this.f.outerLength = s.outerLength;
                this.f.outerGap = s.outerGap;
                this.f.extent = (float) Math.ceil(restExtent(s));
                return this.f;
            }
            float k = 1.0F - (float) Math.exp(-dt * 14.0F); // about 0.2 s to settle
            if (s.style != this.f.style) {
                this.f.previous = this.f.blend >= 0.5F ? this.f.style : this.f.previous;
                this.f.style = s.style;
                this.f.blend = 0.0F;
            }
            this.f.blend = Math.min(1.0F, this.f.blend + dt / 0.22F);
            if (s.colour != this.colourTo) {
                this.colourFrom = this.f.colour;
                this.colourTo = s.colour;
                this.colourK = 0.0F;
            }
            this.colourK = Math.min(1.0F, this.colourK + dt / 0.2F);
            this.f.colour = Px.mix(this.colourFrom | 0xFF000000, this.colourTo | 0xFF000000, this.colourK);
            this.f.size += (s.size - this.f.size) * k;
            this.f.thickness += (s.thickness - this.f.thickness) * k;
            this.f.gap += (s.gap - this.f.gap) * k;
            this.f.outline += ((s.outline ? 1 : 0) - this.f.outline) * k;
            this.f.outlineOpacity += (s.outlineOpacity - this.f.outlineOpacity) * k;
            this.f.outlineThickness += (s.outlineThickness - this.f.outlineThickness) * k;
            this.f.opacity += (s.opacity - this.f.opacity) * k;
            this.f.dot += ((s.hasDot() ? 1 : 0) - this.f.dot) * k;
            this.f.outer += ((s.outerLines && s.style.lines ? 1 : 0) - this.f.outer) * k;
            this.f.outerLength += (s.outerLength - this.f.outerLength) * k;
            this.f.outerGap += (s.outerGap - this.f.outerGap) * k;
            this.f.extent += ((float) Math.ceil(restExtent(s)) - this.f.extent) * k;
            return this.f;
        }
    }

    private final Animator hud = new Animator();
    private final Animator studio = new Animator();
    private float appear;
    private float spread;
    private float fade;
    private float tint;
    private long lastHud;

    // ------------------------------------------------------------------ drawing (window pixels, after Px.begin)

    private static final int[] SX = new int[3];
    private static final int[] SL = new int[3];
    private static final float[] SC = new float[3];
    private static final int[] TY = new int[3];
    private static final int[] TL = new int[3];
    private static final float[] TC = new float[3];

    private static int axis(float a, float b, int[] st, int[] ln, float[] cv) {
        int fa = (int) Math.floor(a);
        int cb = (int) Math.ceil(b);
        if (cb - fa <= 1) {
            st[0] = fa;
            ln[0] = 1;
            cv[0] = b - a;
            return 1;
        }
        int n = 0;
        int ia = (int) Math.ceil(a);
        int ib = (int) Math.floor(b);
        if (ia > a) {
            st[n] = fa;
            ln[n] = 1;
            cv[n++] = ia - a;
        }
        if (ib > ia) {
            st[n] = ia;
            ln[n] = ib - ia;
            cv[n++] = 1.0F;
        }
        if (b > ib) {
            st[n] = ib;
            ln[n] = 1;
            cv[n++] = b - ib;
        }
        return n;
    }

    /** A rectangle at any fractional position: whole pixels solid, the edge pixels by how much they are covered. */
    private static void cover(GuiGraphics g, float x, float y, float w, float h, int c) {
        if (w <= 0.0F || h <= 0.0F || c >>> 24 == 0) return;
        int n = axis(x, x + w, SX, SL, SC);
        int m = axis(y, y + h, TY, TL, TC);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                float k = SC[i] * TC[j];
                if (k > 0.004F) Px.rect(g, SX[i], TY[j], SL[i], TL[j], k >= 0.996F ? c : Px.am(c, k));
            }
        }
    }

    private static int alpha(int c, float a) {
        a = Math.max(0.0F, Math.min(1.0F, a));
        return Math.round(a * (c >>> 24)) << 24 | c & 0xFFFFFF;
    }

    static float parity(float v) {
        return (Math.round(v) & 1) == 1 ? 0.5F : 0.0F;
    }

    static float restExtent(Spec s) {
        float e =
                switch (s.style) {
                    case CROSS, T_SHAPE -> {
                        float half = parity(s.thickness);
                        float inner = s.gap + half + s.size;
                        yield Math.max(inner, s.outerLines ? s.outerGap + half + s.outerLength : 0.0F);
                    }
                    case DOT -> s.size * 0.5F;
                    case CIRCLE, CIRCLE_DOT -> s.size + s.thickness * 0.5F;
                    case CHEVRON -> (s.size + s.thickness) * 0.70710677F;
                };
        return s.outline ? e + s.outlineThickness : e;
    }

    private static float extent(Frame f, Style s, float spread) {
        float e =
                switch (s) {
                    case CROSS, T_SHAPE -> {
                        float a = f.gap + spread + f.size + parity(f.thickness);
                        float b = f.outerGap + spread + f.outerLength + parity(f.thickness);
                        yield f.outer > 0.002F ? Math.max(a, b) : a;
                    }
                    case DOT -> f.size * 0.5F;
                    case CIRCLE, CIRCLE_DOT -> f.size + spread + f.thickness * 0.5F;
                    case CHEVRON -> (f.size + f.thickness) * 0.70710677F;
                };
        return e + f.outlineThickness * f.outline;
    }

    static float extent(Frame f, float spread) {
        return Math.max(extent(f, f.style, spread), f.blend < 1.0F ? extent(f, f.previous, spread) : 0.0F);
    }

    /** The whole crosshair round (cx, cy): unit is screen pixels per crosshair pixel, half the odd-width nudge. */
    static void draw(
            GuiGraphics g, Frame f, float cx, float cy, float unit, float half, float spread, int colour, float a) {
        if (f.blend >= 1.0F) {
            drawStyle(g, f, f.style, cx, cy, unit, half, spread, colour, a);
        } else {
            drawStyle(
                    g,
                    f,
                    f.previous,
                    cx,
                    cy,
                    unit * (1.0F - 0.2F * f.blend),
                    half,
                    spread,
                    colour,
                    a * (1.0F - f.blend));
            drawStyle(
                    g, f, f.style, cx, cy, unit * (1.0F - 0.2F * (1.0F - f.blend)), half, spread, colour, a * f.blend);
        }
    }

    private static void drawStyle(
            GuiGraphics g,
            Frame f,
            Style s,
            float cx,
            float cy,
            float unit,
            float half,
            float spread,
            int colour,
            float a) {
        if (a <= 0.002F) return;
        float shade = f.outline * f.outlineOpacity * a;
        if (shade > 0.002F) {
            elements(g, f, s, cx, cy, unit, half, spread, f.outlineThickness * unit, 0xFF000000, shade, false);
        }
        elements(g, f, s, cx, cy, unit, half, spread, 0.0F, colour, f.opacity * a, true);
    }

    private static void elements(
            GuiGraphics g,
            Frame f,
            Style s,
            float cx,
            float cy,
            float unit,
            float half,
            float spread,
            float grow,
            int colour,
            float a,
            boolean fill) {
        int c = alpha(colour | 0xFF000000, a);
        float dotK = 1.0F;
        switch (s) {
            case CROSS, T_SHAPE -> dotK = lines(g, f, s, cx, cy, unit, half, spread, grow, colour, a, fill);
            case DOT -> dot(g, cx, cy, f.size, unit, grow, c);
            case CIRCLE, CIRCLE_DOT ->
                band(g, cx, cy, (f.size + spread) * unit, f.thickness * unit + grow * 2.0F, 1.0F, c);
            case CHEVRON -> chevron(g, f, cx, cy, unit, grow, c);
        }
        float dotA = a * f.dot * dotK;
        if ((s.alwaysDot() || s.optionalDot) && dotA > 0.002F) {
            float ds = f.thickness + 2.0F * Math.max(0.0F, Math.min(1.0F, 2.0F - f.thickness));
            dot(g, cx, cy, ds, unit, grow, alpha(colour | 0xFF000000, dotA));
        }
    }

    private static float lines(
            GuiGraphics g,
            Frame f,
            Style s,
            float cx,
            float cy,
            float unit,
            float half,
            float spread,
            float grow,
            int colour,
            float a,
            boolean fill) {
        float thick = f.thickness * unit;
        float start = (f.gap + spread) * unit;
        arms(g, cx, cy, start + half, f.size * unit, thick, grow, s.topLine, alpha(colour | 0xFF000000, a));
        // with no gap the four arms meet: the middle square joins them, the centre dot gives way
        float join = Math.max(0.0F, Math.min(1.0F, 1.0F - start / unit));
        if (fill && half > 0.0F && join > 0.0F) {
            cover(g, cx - half, cy - half, half * 2.0F, half * 2.0F, alpha(colour | 0xFF000000, a * join));
        }
        float outerA = a * f.outer;
        if (outerA > 0.002F) {
            arms(
                    g,
                    cx,
                    cy,
                    (f.outerGap + spread) * unit + half,
                    f.outerLength * unit,
                    thick,
                    grow,
                    s.topLine,
                    alpha(colour | 0xFF000000, outerA));
        }
        return 1.0F - join;
    }

    /** Four arms (or three, without the top): the cross of the outline never overlaps itself in the middle. */
    private static void arms(
            GuiGraphics g, float cx, float cy, float start, float len, float thick, float grow, boolean top, int c) {
        float h = thick * 0.5F;
        float y0 = cy - h - grow;
        float y1 = cy + h + grow;
        float x0 = cx - h - grow;
        float x1 = cx + h + grow;
        float far = start + len + grow;
        float near = start - grow;
        float leftEnd = Math.min(cx - near, cx);
        float rightStart = Math.max(cx + near, cx);
        cover(g, cx - far, y0, leftEnd - (cx - far), y1 - y0, c);
        cover(g, rightStart, y0, cx + far - rightStart, y1 - y0, c);
        float hx0 = Math.max(x0, cx - near);
        float hx1 = Math.min(x1, cx + near);
        if (top) vertical(g, x0, x1, cy - far, Math.min(cy - near, cy), y0, y1, hx0, hx1, c);
        vertical(g, x0, x1, Math.max(cy + near, cy), cy + far, y0, y1, hx0, hx1, c);
    }

    private static void vertical(
            GuiGraphics g,
            float xa,
            float xb,
            float ya,
            float yb,
            float band0,
            float band1,
            float hx0,
            float hx1,
            int c) {
        if (yb <= ya) return;
        float topEnd = Math.min(yb, band0);
        if (topEnd > ya) cover(g, xa, ya, xb - xa, topEnd - ya, c);
        float botStart = Math.max(ya, band1);
        if (yb > botStart) cover(g, xa, botStart, xb - xa, yb - botStart, c);
        float oy0 = Math.max(ya, band0);
        float oy1 = Math.min(yb, band1);
        if (oy1 > oy0 && hx1 > hx0) cover(g, hx0, oy0, hx1 - hx0, oy1 - oy0, c);
    }

    private static void dot(GuiGraphics g, float cx, float cy, float size, float unit, float grow, int c) {
        float s = size * unit + grow * 2.0F;
        float r = s * 0.5F * Math.max(0.0F, Math.min(1.0F, size - 3.0F));
        if (r < 1.0F) {
            cover(g, cx - s * 0.5F, cy - s * 0.5F, s, s, c);
        } else {
            int x = Math.round(cx - s * 0.5F);
            int y = Math.round(cy - s * 0.5F);
            int d = Math.max(1, Math.round(s));
            Px.round(g, x, y, d, d, Math.round(r), c);
        }
    }

    private static void band(GuiGraphics g, float cx, float cy, float r, float w, float progress, int c) {
        float w2 = w + 1.0F; // a pixel more: the round brush thins the band a little
        Px.arc(g, cx, cy, r + w2 * 0.5F, Math.max(1, Math.round(w2)), -Math.PI / 2.0, Math.PI * 2.0 * progress, c, c);
    }

    private static void chevron(GuiGraphics g, Frame f, float cx, float cy, float unit, float grow, int c) {
        float t = f.thickness * unit;
        float h = t * 0.5F;
        float from = -h - grow;
        float len = f.size * unit + h + grow * 2.0F;
        float w = t + grow * 2.0F;
        for (double ang : new double[] {Math.PI / 4.0, Math.PI * 3.0 / 4.0}) {
            float dx = (float) Math.cos(ang);
            float dy = (float) Math.sin(ang);
            Px.seg(g, cx + dx * from, cy + dy * from, cx + dx * (from + len), cy + dy * (from + len), w, c);
        }
    }

    private static void cooldownBar(
            GuiGraphics g, Frame f, float cx, float cy, float unit, float half, float p, int colour, float a) {
        float hw = Math.max(8.0F, f.extent) * unit + half;
        float y = cy + (f.extent + 3.0F) * unit + half;
        float h = 2.0F * unit;
        float fr = unit;
        cover(g, cx - hw - fr, y - fr, hw * 2.0F + fr * 2.0F, h + fr * 2.0F, alpha(0xFF000000, 0.55F * a));
        cover(
                g,
                cx - hw,
                y,
                hw * 2.0F * Math.max(0.0F, Math.min(1.0F, p)),
                h,
                alpha(colour | 0xFF000000, f.opacity * a));
    }

    private static void cooldownRing(
            GuiGraphics g, Frame f, float cx, float cy, float unit, float half, float p, int colour, float a) {
        float r = (f.extent + 4.0F) * unit + half;
        float w = 2.0F * unit;
        band(g, cx, cy, r, w, 1.0F, alpha(0xFF000000, 0.35F * a));
        if (p > 0.001F) band(g, cx, cy, r, w, Math.min(1.0F, p), alpha(colour | 0xFF000000, f.opacity * a));
    }

    private static float anchor(int size, float half) {
        float mid = size * 0.5F;
        float base = (float) Math.floor(mid);
        return base + half * (mid - base >= 0.5F ? 1.0F : -1.0F);
    }

    public int pixelUnit(int screenH) {
        return switch (this.scale.index()) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> Math.max(1, Math.round(screenH / 720.0F));
        };
    }

    // ------------------------------------------------------------------ on screen

    private boolean shows(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.options.hideGui) return false;
        if (!mc.options.getCameraType().isFirstPerson() && !this.thirdPerson.on()) return false;
        if (mc.player.isSpectator()) return mc.crosshairPickEntity != null;
        return true;
    }

    /** The crosshair in the game, instead of the vanilla one. */
    public void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = this.lastHud == 0L ? 0.0F : Math.min(0.1F, (now - this.lastHud) / 1.0E9F);
        this.lastHud = now;
        if (!this.shows(mc)) return;
        // it grows back in when a screen closes
        this.appear = mc.screen == null ? Math.min(1.0F, this.appear + dt / 0.18F) : 0.0F;
        if (this.appear <= 0.01F) return;
        Spec spec = this.current();
        Frame f = this.hud.update(spec);
        int w = Px.width();
        int h = Px.height();
        float unit = this.pixelUnit(h) * (0.6F + 0.4F * this.appear);
        float half = parity((spec.style == Style.DOT ? spec.size : spec.thickness) * this.pixelUnit(h));
        float charge = mc.player.getAttackStrengthScale(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        int mode = this.cooldown.index();
        float k = 1.0F - (float) Math.exp(-dt * 18.0F);
        this.fade += ((charge < 1.0F ? 1.0F : 0.0F) - this.fade) * k;
        this.spread += ((mode == 2 ? 8.0F * (1.0F - charge) : 0.0F) - this.spread) * k;
        boolean onTarget = this.targetTint.on()
                && charge >= 1.0F
                && mc.crosshairPickEntity instanceof LivingEntity le
                && le.isAlive();
        this.tint += ((onTarget ? 1.0F : 0.0F) - this.tint) * k;
        int colour = Px.mix(f.colour | 0xFF000000, this.targetColour.argb() | 0xFF000000, this.tint);
        float a = Math.min(1.0F, this.appear);
        Px.begin(g);
        try {
            float cx = anchor(w, half);
            float cy = anchor(h, half);
            draw(g, f, cx, cy, unit, half, this.spread, colour, a);
            if (mode == 0) cooldownBar(g, f, cx, cy, unit, half, charge, colour, a * this.fade);
            else if (mode == 1) cooldownRing(g, f, cx, cy, unit, half, charge, colour, a * this.fade);
        } finally {
            Px.end(g);
        }
    }

    /**
     * The studio preview: the crosshair in the middle of the box (the caller draws the backdrop), the cooldown running
     * round every 2.2 s.
     */
    public void preview(GuiGraphics g, int x, int y, int w, int h, int r) {
        Spec spec = this.current();
        Frame f = this.studio.update(spec);
        float cycle = (System.nanoTime() / 1.0E6F) % 2200.0F / 2200.0F;
        float charge = Math.min(1.0F, cycle / 0.7F);
        float show = cycle < 0.7F ? 1.0F : (1.0F - cycle) / 0.3F;
        int mode = this.cooldown.index();
        float spread = mode == 2 ? 8.0F * (1.0F - charge) : 0.0F;
        float ext = Math.max(extent(f, 8.0F), f.extent + 6.0F);
        float unit = Math.max(1.0F, Math.min(6.0F, Math.min(w, h) * 0.62F / (2.0F * ext)));
        unit = Math.round(unit);
        float half = parity((spec.style == Style.DOT ? spec.size : spec.thickness) * unit);
        float cx = x + w / 2.0F + half;
        float cy = y + h / 2.0F + half;
        draw(g, f, cx, cy, unit, half, spread, f.colour, 1.0F);
        if (mode == 0) cooldownBar(g, f, cx, cy, unit, half, charge, f.colour, show);
        else if (mode == 1) cooldownRing(g, f, cx, cy, unit, half, charge, f.colour, show);
    }

    /** A small picture of a crosshair for a tile, fitted into a box of the given size. */
    public static void icon(GuiGraphics g, Spec s, float cx, float cy, float box) {
        Frame f = new Frame();
        f.style = f.previous = s.style;
        f.colour = s.colour;
        f.size = s.size;
        f.thickness = s.thickness;
        f.gap = s.gap;
        f.outline = s.outline ? 1 : 0;
        f.outlineOpacity = s.outlineOpacity;
        f.outlineThickness = s.outlineThickness;
        f.opacity = s.opacity;
        f.dot = s.hasDot() ? 1 : 0;
        f.outer = s.outerLines && s.style.lines ? 1 : 0;
        f.outerLength = s.outerLength;
        f.outerGap = s.outerGap;
        f.extent = (float) Math.ceil(restExtent(s));
        float unit = Math.max(0.5F, Math.min(2.0F, box / (2.0F * Math.max(1.0F, restExtent(s)))));
        float half = parity(s.thickness * unit);
        draw(g, f, Math.round(cx) + half, Math.round(cy) + half, unit, half, 0.0F, s.colour, Px.fade);
    }

    // ------------------------------------------------------------------ codes

    public static final String PREFIX = "ARC>";
    private static final String EVO = "EVO2>";
    private static final Pattern VALORANT = Pattern.compile("^\\d+;.*");
    private static final int[] VALORANT_COLOURS = {-1, -16711936, -5374161, -3473617, -256, -16711681, -65281, -65536};

    public static String encode(Spec s, String name) {
        String code = PREFIX + "S:" + s.style.name()
                + "|C:" + String.format(Locale.ROOT, "%08X", s.colour)
                + "|Z:" + s.size + "," + s.thickness + "," + s.gap
                + "|O:" + (s.outline ? 1 : 0) + "," + number(s.outlineOpacity) + "," + s.outlineThickness
                + "|A:" + number(s.opacity)
                + "|D:" + (s.centreDot ? 1 : 0)
                + "|U:" + (s.outerLines ? 1 : 0) + "," + s.outerLength + "," + s.outerGap;
        if (name != null && !name.isBlank()) {
            code += "|N:"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
        }
        return code;
    }

    private static String number(float v) {
        return v == (int) v ? Integer.toString((int) v) : Float.toString(v);
    }

    /** Our code, an Evolution one (the same layout) or a Valorant crosshair code. */
    public static Optional<Spec> decode(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim();
        try {
            if (s.startsWith(PREFIX)) return ours(s.substring(PREFIX.length()));
            if (s.startsWith(EVO)) return ours(s.substring(EVO.length()));
            if (VALORANT.matcher(s).matches()) return valorant(s);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public static String nameOf(String raw) {
        String s = raw == null ? "" : raw.trim();
        int at = s.indexOf("|N:");
        if (at < 0) return null;
        String b = s.substring(at + 3).split("\\|", 2)[0];
        try {
            String n = new String(Base64.getUrlDecoder().decode(b), StandardCharsets.UTF_8).strip();
            return n.isEmpty() ? null : n;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Optional<Spec> ours(String body) {
        Spec d = Spec.DEFAULT;
        Style style = d.style;
        int colour = d.colour;
        int size = d.size;
        int thickness = d.thickness;
        int gap = d.gap;
        boolean outline = d.outline;
        float outlineOpacity = d.outlineOpacity;
        int outlineThickness = d.outlineThickness;
        float opacity = d.opacity;
        boolean dot = d.centreDot;
        boolean outer = d.outerLines;
        int outerLength = d.outerLength;
        int outerGap = d.outerGap;
        boolean any = false;
        for (String part : body.split("\\|")) {
            String[] kv = part.split(":", 2);
            if (kv.length < 2) continue;
            String[] v = kv[1].split(",");
            switch (kv[0]) {
                case "S" -> {
                    style = Style.byName(v[0]);
                    if (style == null) return Optional.empty();
                }
                case "C" -> colour = (int) Long.parseLong(v[0], 16);
                case "Z" -> {
                    size = Integer.parseInt(v[0]);
                    thickness = Integer.parseInt(v[1]);
                    gap = Integer.parseInt(v[2]);
                }
                case "O" -> {
                    outline = "1".equals(v[0]);
                    outlineOpacity = Float.parseFloat(v[1]);
                    outlineThickness = Integer.parseInt(v[2]);
                }
                case "A" -> opacity = Float.parseFloat(v[0]);
                case "D" -> dot = "1".equals(v[0]);
                case "U" -> {
                    outer = "1".equals(v[0]);
                    outerLength = Integer.parseInt(v[1]);
                    outerGap = Integer.parseInt(v[2]);
                }
                default -> {
                    continue;
                }
            }
            any = true;
        }
        if (!any) return Optional.empty();
        return Optional.of(new Spec(
                        style,
                        colour,
                        size,
                        thickness,
                        gap,
                        outline,
                        outlineOpacity,
                        outlineThickness,
                        opacity,
                        dot,
                        outer,
                        outerLength,
                        outerGap)
                .sane());
    }

    /** A Valorant code: its primary crosshair, colours by index or rgba, inner and outer lines, the centre dot. */
    private static Optional<Spec> valorant(String code) {
        String[] p = code.split(";");
        int colour = VALORANT_COLOURS[0];
        int index = 0;
        int custom = 0;
        boolean hasCustom = false;
        boolean outline = true;
        float outlineOpacity = 0.5F;
        int outlineThickness = 1;
        boolean dot = false;
        int dotSize = 2;
        float dotOpacity = 1.0F;
        boolean inner = true;
        int innerThickness = 2;
        int innerLength = 6;
        int innerOffset = 3;
        float innerOpacity = 0.8F;
        boolean outer = true;
        int outerLength = 2;
        int outerOffset = 10;
        boolean primary = false;
        boolean seen = false;
        int i = 1;
        while (i < p.length) {
            String k = p[i];
            if ("P".equals(k) || "A".equals(k) || "S".equals(k)) {
                primary = "P".equals(k);
                seen |= primary;
                i++;
                continue;
            }
            if (i + 1 >= p.length) break;
            String v = p[i + 1];
            i += 2;
            if (!primary) continue;
            switch (k) {
                case "c" -> index = Integer.parseInt(v);
                case "u" -> {
                    custom = rgbaToArgb(v);
                    hasCustom = true;
                }
                case "h" -> outline = "1".equals(v);
                case "t" -> outlineThickness = Math.round(Float.parseFloat(v));
                case "o" -> outlineOpacity = Float.parseFloat(v);
                case "d" -> dot = "1".equals(v);
                case "z" -> dotSize = Math.round(Float.parseFloat(v));
                case "a" -> dotOpacity = Float.parseFloat(v);
                case "0b" -> inner = !"0".equals(v);
                case "0t" -> innerThickness = Math.round(Float.parseFloat(v));
                case "0l" -> innerLength = Math.round(Float.parseFloat(v));
                case "0o" -> innerOffset = Math.round(Float.parseFloat(v));
                case "0a" -> innerOpacity = Float.parseFloat(v);
                case "1b" -> outer = !"0".equals(v);
                case "1l" -> outerLength = Math.round(Float.parseFloat(v));
                case "1o" -> outerOffset = Math.round(Float.parseFloat(v));
                default -> {}
            }
        }
        if (!seen) return Optional.empty();
        if (index == 8 && hasCustom) colour = custom;
        else if (index >= 0 && index < VALORANT_COLOURS.length) colour = VALORANT_COLOURS[index];
        Spec s = !inner && dot
                ? new Spec(
                        Style.DOT,
                        colour,
                        dotSize,
                        innerThickness,
                        innerOffset,
                        outline,
                        outlineOpacity,
                        outlineThickness,
                        dotOpacity,
                        false,
                        false,
                        outerLength,
                        outerOffset)
                : new Spec(
                        Style.CROSS,
                        colour,
                        innerLength,
                        innerThickness,
                        innerOffset,
                        outline,
                        outlineOpacity,
                        outlineThickness,
                        innerOpacity,
                        dot,
                        outer,
                        outerLength,
                        outerOffset);
        return Optional.of(s.sane());
    }

    private static int rgbaToArgb(String hex) {
        long v = Long.parseLong(hex.length() > 8 ? hex.substring(0, 8) : hex, 16);
        if (hex.length() <= 6) return 0xFF000000 | (int) v;
        int rgb = (int) (v >>> 8) & 0xFFFFFF;
        int a = (int) v & 0xFF;
        return a << 24 | rgb;
    }

    // ------------------------------------------------------------------ the player's own presets

    public record Saved(String name, Spec spec) {}

    private static final Path LIBRARY =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/crosshairs.json");
    public static final int LIBRARY_MAX = 12;
    private final List<Saved> library = new ArrayList<>();
    private boolean libraryRead;

    public List<Saved> library() {
        if (!this.libraryRead) {
            this.libraryRead = true;
            try {
                if (Files.exists(LIBRARY)) {
                    for (JsonElement e : JsonParser.parseString(Files.readString(LIBRARY, StandardCharsets.UTF_8))
                            .getAsJsonArray()) {
                        JsonObject o = e.getAsJsonObject();
                        decode(o.get("code").getAsString())
                                .ifPresent(s ->
                                        this.library.add(new Saved(o.get("name").getAsString(), s)));
                    }
                }
            } catch (Exception ignored) {
                // a broken file: start over
            }
        }
        return this.library;
    }

    private void saveLibrary() {
        JsonArray a = new JsonArray();
        for (Saved s : this.library) {
            JsonObject o = new JsonObject();
            o.addProperty("name", s.name());
            o.addProperty("code", encode(s.spec(), null));
            a.add(o);
        }
        try {
            Files.createDirectories(LIBRARY.getParent());
            Files.writeString(
                    LIBRARY, new GsonBuilder().setPrettyPrinting().create().toJson(a), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // kept for this start only
        }
    }

    /** Saves a crosshair under a free name; false when the library is full or holds it already. */
    public boolean keep(String name, Spec s) {
        List<Saved> lib = this.library();
        for (Saved o : lib) if (o.spec().equals(s)) return true;
        if (lib.size() >= LIBRARY_MAX) return false;
        String base = name == null || name.isBlank() ? Lang.tr("Мой прицел") : name.strip();
        String n = base;
        for (int i = 2; this.named(n); i++) n = base + " " + i;
        lib.add(new Saved(n, s));
        this.saveLibrary();
        return true;
    }

    private boolean named(String n) {
        for (Saved o : this.library()) if (o.name().equalsIgnoreCase(n)) return true;
        return false;
    }

    public void forget(Saved s) {
        if (this.library().remove(s)) this.saveLibrary();
    }
}
