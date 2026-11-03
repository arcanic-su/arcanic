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
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    public final String key;
    public final String name;
    protected T value;
    public final T def;
    private BooleanSupplier visible = () -> true;
    protected T base;

    protected Setting(String key, String name, T def) {
        this.key = key;
        this.name = name;
        this.def = def;
        this.value = def;
    }

    public T get() {
        return this.value;
    }

    public void set(T v) {
        this.value = v;
    }

    public void reset() {
        this.value = (T) (this.base != null ? this.base : this.def);
    }

    public void markDefault() {
        this.base = this.value;
    }

    public boolean modified() {
        return !Objects.equals(this.value, this.base != null ? this.base : this.def);
    }

    public boolean visible() {
        return this.visible.getAsBoolean();
    }

    public <S extends Setting<T>> S showIf(BooleanSupplier s) {
        this.visible = s;
        return (S) this;
    }

    public abstract JsonElement save();

    public abstract void load(JsonElement json);

    public static final class Bool extends Setting<Boolean> {
        public Bool(String key, String name, boolean def) {
            super(key, name, def);
        }

        public boolean on() {
            return (Boolean) this.value;
        }

        public void toggle() {
            this.value = !(Boolean) this.value;
        }

        public JsonElement save() {
            return new JsonPrimitive(this.value);
        }

        public void load(JsonElement e) {
            if (e.isJsonPrimitive()) {
                this.value = e.getAsBoolean();
            }
        }
    }

    public static final class Text extends Setting<String> {
        public final String hint;
        public final int max;

        public Text(String key, String name, String def, String hint, int max) {
            super(key, name, def);
            this.hint = hint;
            this.max = max;
        }

        public JsonElement save() {
            return new JsonPrimitive(this.value);
        }

        public void load(JsonElement e) {
            if (e != null && e.isJsonPrimitive()) {
                this.value = e.getAsString();
            }
        }
    }

    public static final class Action extends Setting<Boolean> {
        public final String label;
        public final Runnable run;

        public Action(String key, String name, String label, Runnable run) {
            super(key, name, false);
            this.label = label;
            this.run = run;
        }

        public boolean modified() {
            return false;
        }

        public JsonElement save() {
            return new JsonPrimitive(false);
        }

        public void load(JsonElement e) {}
    }

    /** A key or mouse button; -1 means unbound. Mouse buttons are stored as {@link Keys#MOUSE} + button. */
    public static final class Key extends Setting<Integer> {
        public Key(String key, String name) {
            super(key, name, -1);
        }

        public int code() {
            return value;
        }

        public boolean bound() {
            return value >= 0;
        }

        public JsonElement save() {
            return new JsonPrimitive(value);
        }

        public void load(JsonElement e) {
            if (e != null && e.isJsonPrimitive()) value = e.getAsInt();
        }
    }

    public static final class Num extends Setting<Double> {
        public final double min;
        public final double max;
        public final double step;
        public final String unit;

        public Num(String key, String name, double def, double min, double max, double step, String unit) {
            super(key, name, def);
            this.min = min;
            this.max = max;
            this.step = step;
            this.unit = unit;
        }

        public float f() {
            return ((Double) this.value).floatValue();
        }

        public int i() {
            return (int) Math.round((Double) this.value);
        }

        public void set(Double v) {
            double s =
                    (double) Math.round((Math.max(this.min, Math.min(this.max, v)) - this.min) / this.step) * this.step
                            + this.min;
            this.value = (double) Math.round(s * 1000.0) / 1000.0;
        }

        public double fraction() {
            return ((Double) this.value - this.min) / (this.max - this.min);
        }

        public String text() {
            String n = this.step >= 1.0
                    ? String.valueOf(Math.round((Double) this.value))
                    : String.format(
                            Locale.ROOT, this.step >= 0.1 ? "%.1f" : (this.step >= 0.01 ? "%.2f" : "%.3f"), this.value);
            return n + this.unit;
        }

        public JsonElement save() {
            return new JsonPrimitive((Number) this.value);
        }

        public void load(JsonElement e) {
            if (e.isJsonPrimitive()) {
                this.set(e.getAsDouble());
            }
        }
    }

    public static final class Mode extends Setting<String> {
        public final List<String> modes;

        public Mode(String key, String name, String def, String... modes) {
            super(key, name, def);
            this.modes = List.of(modes);
        }

        public boolean is(String m) {
            return ((String) this.value).equals(m);
        }

        public int index() {
            return Math.max(0, this.modes.indexOf(this.value));
        }

        public void cycle(int dir) {
            this.value = this.modes.get(Math.floorMod(this.index() + dir, this.modes.size()));
        }

        public JsonElement save() {
            return new JsonPrimitive(this.value);
        }

        public void load(JsonElement e) {
            if (e.isJsonPrimitive() && this.modes.contains(e.getAsString())) {
                this.value = e.getAsString();
            }
        }
    }

    public static final class Colour extends Setting<Integer> {
        public static final List<Integer> PRESETS =
                new ArrayList(List.of(0xFF007AFF, 0xFF34C759, -13312, -50384, 0xFF9747FF));
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
            -1,
            0xFF94A3B8
        };
        private boolean theme;
        private boolean defTheme;

        public Colour(String key, String name, boolean theme) {
            this(key, name, theme, PALETTE[0]);
        }

        public Colour(String key, String name, boolean theme, int def) {
            super(key, name, def);
            this.theme = theme;
            this.defTheme = theme;
        }

        public void markDefault() {
            super.markDefault();
            this.defTheme = this.theme;
        }

        public boolean modified() {
            return this.theme != this.defTheme || !this.theme && super.modified();
        }

        public boolean followsTheme() {
            return this.theme;
        }

        public void setTheme(boolean t) {
            this.theme = t;
        }

        public int argb() {
            return this.theme ? Theme.accent() : (Integer) this.value;
        }

        public void reset() {
            super.reset();
            this.theme = this.defTheme;
        }

        public JsonElement save() {
            return new JsonPrimitive(this.theme ? "theme" : Integer.toHexString((Integer) this.value));
        }

        public void load(JsonElement e) {
            if (e.isJsonPrimitive()) {
                String s = e.getAsString();
                if (s.equals("theme")) {
                    this.theme = true;
                } else {
                    try {
                        this.value = (int) Long.parseLong(s, 16);
                        this.theme = false;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    public static final class Items extends Setting<Map<String, Boolean>> {
        public final List<Entry> entries;

        public Items(String key, String name, List<Entry> entries) {
            super(key, name, defaults(entries));
            this.entries = List.copyOf(entries);
        }

        private static Map<String, Boolean> defaults(List<Entry> entries) {
            Map<String, Boolean> m = new HashMap<>();

            for (Entry e : entries) {
                m.put(e.key(), e.on());
            }

            return Map.copyOf(m);
        }

        public boolean on(String item) {
            Boolean v = (Boolean) ((Map) this.value).get(item);
            return v != null && v;
        }

        public void toggle(String item) {
            Map<String, Boolean> next = new HashMap(this.value);
            next.put(item, !this.on(item));
            this.value = Map.copyOf(next);
        }

        public int count() {
            int n = 0;

            for (Entry e : this.entries) {
                if (this.on(e.key())) {
                    ++n;
                }
            }

            return n;
        }

        public JsonElement save() {
            JsonObject o = new JsonObject();

            for (Entry e : this.entries) {
                o.addProperty(e.key(), this.on(e.key()));
            }

            return o;
        }

        public void load(JsonElement e) {
            if (e.isJsonObject()) {
                Map<String, Boolean> next = new HashMap(this.def);

                for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                    if (next.containsKey(en.getKey()) && ((JsonElement) en.getValue()).isJsonPrimitive()) {
                        next.put(en.getKey(), ((JsonElement) en.getValue()).getAsBoolean());
                    }
                }

                this.value = Map.copyOf(next);
            }
        }

        public static record Entry(String key, String name, String hint, String icon, boolean on, Colour colour) {}
    }

    public static final class Sizes extends Setting<Map<String, Double>> {
        public static final double MIN = 0.2;
        public static final double MAX = 3.0;
        public static final double LIMIT = 10.0;

        public Sizes(String key, String name) {
            super(key, name, Map.of());
        }

        public double of(String name) {
            return this.of(name, 1.0);
        }

        public double of(String name, double def) {
            Double v = (Double) ((Map) this.value).get(name);
            return v == null ? def : v;
        }

        public void put(String name, double size) {
            this.put(name, Math.max(0.2, Math.min(3.0, size)), 1.0);
        }

        public void put(String name, double v, double def) {
            Map<String, Double> next = new HashMap(this.value);
            double r = (double) Math.round(Math.max(-10.0, Math.min(10.0, v)) * 1000.0) / 1000.0;
            if (r == def) {
                next.remove(name);
            } else {
                next.put(name, r);
            }

            this.value = Map.copyOf(next);
        }

        public JsonElement save() {
            JsonObject o = new JsonObject();

            for (Map.Entry<String, Double> e : new TreeMap<String, Double>(this.value).entrySet()) {
                o.addProperty(e.getKey(), (Number) e.getValue());
            }

            return o;
        }

        public void load(JsonElement e) {
            if (e.isJsonObject()) {
                Map<String, Double> next = new HashMap<>();

                for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                    JsonElement v = (JsonElement) en.getValue();
                    if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() && Double.isFinite(v.getAsDouble())) {
                        next.put(en.getKey(), Math.max(-10.0, Math.min(10.0, v.getAsDouble())));
                    }
                }

                this.value = Map.copyOf(next);
            }
        }
    }
}
