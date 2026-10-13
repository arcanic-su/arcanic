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

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** One adjustable value of a module: a switch, a slider, a choice of modes or a colour. */
public abstract class Setting<T> {
    public final String key, name;
    protected T value;
    public final T def;
    private BooleanSupplier visible = () -> true;

    protected Setting(String key, String name, T def) {
        this.key = key;
        this.name = name;
        this.def = def;
        this.value = def;
    }

    public T get() {
        return value;
    }

    public void set(T v) {
        value = v;
    }

    protected T base;

    public void reset() {
        value = base != null ? base : def;
    }

    public void markDefault() {
        base = value;
    }

    public boolean modified() {
        return !Objects.equals(value, base != null ? base : def);
    }

    public boolean visible() {
        return visible.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S showIf(BooleanSupplier s) {
        visible = s;
        return (S) this;
    }

    public abstract JsonElement save();

    public abstract void load(JsonElement e);

    public static final class Bool extends Setting<Boolean> {
        public Bool(String key, String name, boolean def) {
            super(key, name, def);
        }

        public boolean on() {
            return value;
        }

        public void toggle() {
            value = !value;
        }

        @Override
        public JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        public void load(JsonElement e) {
            if (e.isJsonPrimitive()) value = e.getAsBoolean();
        }
    }

    public static final class Num extends Setting<Double> {
        public final double min, max, step;
        public final String unit;

        public Num(String key, String name, double def, double min, double max, double step, String unit) {
            super(key, name, def);
            this.min = min;
            this.max = max;
            this.step = step;
            this.unit = unit;
        }

        public float f() {
            return value.floatValue();
        }

        public int i() {
            return (int) Math.round(value);
        }

        @Override
        public void set(Double v) {
            double s = Math.round((Math.max(min, Math.min(max, v)) - min) / step) * step + min;
            value = Math.round(s * 1000) / 1000.0;
        }

        public double fraction() {
            return (value - min) / (max - min);
        }

        public String text() {
            String n = step >= 1
                    ? String.valueOf((long) Math.round(value))
                    : String.format(Locale.ROOT, step >= .1 ? "%.1f" : "%.2f", value);
            return n + unit;
        }

        @Override
        public JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        public void load(JsonElement e) {
            if (e.isJsonPrimitive()) set(e.getAsDouble());
        }
    }

    public static final class Mode extends Setting<String> {
        public final List<String> modes;

        public Mode(String key, String name, String def, String... modes) {
            super(key, name, def);
            this.modes = List.of(modes);
        }

        public boolean is(String m) {
            return value.equals(m);
        }

        public int index() {
            return Math.max(0, modes.indexOf(value));
        }

        public void cycle(int dir) {
            value = modes.get(Math.floorMod(index() + dir, modes.size()));
        }

        @Override
        public JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        public void load(JsonElement e) {
            if (e.isJsonPrimitive() && modes.contains(e.getAsString())) value = e.getAsString();
        }
    }

    /** ARGB colour; "theme" follows the menu accent. */
    public static final class Colour extends Setting<Integer> {
        public static final List<Integer> PRESETS =
                new ArrayList<>(List.of(0xFF007AFF, 0xFF34C759, 0xFFFFCC00, 0xFFFF3B30, 0xFF9747FF));
        public static final int[] PALETTE = {
            0xFF8B5CF6,
            0xFFC084FC,
            0xFFF472B6,
            0xFFF43F5E,
            0xFFFB923C,
            0xFFFACC15,
            0xFF4ADE80,
            0xFF34D399,
            0xFF22D3EE,
            0xFF60A5FA,
            0xFFFFFFFF,
            0xFF94A3B8
        };
        private boolean theme;

        public Colour(String key, String name, boolean theme) {
            this(key, name, theme, PALETTE[0]);
        }

        private boolean defTheme;

        public Colour(String key, String name, boolean theme, int def) {
            super(key, name, def);
            this.theme = theme;
            this.defTheme = theme;
        }

        @Override
        public void markDefault() {
            super.markDefault();
            defTheme = theme;
        }

        @Override
        public boolean modified() {
            return theme != defTheme || (!theme && super.modified());
        }

        public boolean followsTheme() {
            return theme;
        }

        public void setTheme(boolean t) {
            theme = t;
        }

        public int argb() {
            return theme ? Theme.accent() : value;
        }

        @Override
        public void reset() {
            super.reset();
            theme = defTheme;
        }

        @Override
        public JsonElement save() {
            return new JsonPrimitive(theme ? "theme" : Integer.toHexString(value));
        }

        @Override
        public void load(JsonElement e) {
            if (!e.isJsonPrimitive()) return;
            String s = e.getAsString();
            if (s.equals("theme")) {
                theme = true;
                return;
            }
            try {
                value = (int) Long.parseLong(s, 16);
                theme = false;
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
