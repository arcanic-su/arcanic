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

import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;

public final class Paint {
    static final long T0 = System.nanoTime();

    private Paint() {}

    public static float time() {
        return (float) (System.nanoTime() - T0) / 1.0E9F;
    }

    static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    static float frac(float v) {
        return v - (float) Math.floor((double) v);
    }

    static int lerp(int a, int b, float t) {
        int ar = a >> 16 & 255;
        int ag = a >> 8 & 255;
        int ab = a & 255;
        int aa = a >>> 24;
        int br = b >> 16 & 255;
        int bg = b >> 8 & 255;
        int bb = b & 255;
        int ba = b >>> 24;
        return (int) ((float) aa + (float) (ba - aa) * t) << 24
                | (int) ((float) ar + (float) (br - ar) * t) << 16
                | (int) ((float) ag + (float) (bg - ag) * t) << 8
                | (int) ((float) ab + (float) (bb - ab) * t);
    }

    static int hsv(float h, float s, float v) {
        int i = (int) Math.floor((double) (h * 6.0F));
        float f = h * 6.0F - (float) i;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r;
        float g;
        float b;
        switch (Math.floorMod(i, 6)) {
            case 0:
                r = v;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = v;
                b = p;
                break;
            case 2:
                r = p;
                g = v;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = v;
                break;
            case 4:
                r = t;
                g = p;
                b = v;
                break;
            default:
                r = v;
                g = p;
                b = q;
        }

        return 0xFF000000 | Math.round(r * 255.0F) << 16 | Math.round(g * 255.0F) << 8 | Math.round(b * 255.0F);
    }

    public abstract static class Base extends Module {
        public final Setting.Mode mode = this.add(
                new Setting.Mode("mode", "Режим", "Переливание", "Переливание", "Градиент", "Радуга", "Один цвет"));
        public final Setting.Colour a = this.add(new Setting.Colour("color_a", "Цвет 1", true));
        public final Setting.Colour b = (this.add(new Setting.Colour("color_b", "Цвет 2", false, 0xFF22D3EE)))
                .showIf(() -> !this.mode.is("Один цвет") && !this.mode.is("Радуга"));
        public final Setting.Mode style =
                this.add(new Setting.Mode("style", "Стиль", "Перекраска", "Перекраска", "Оттенок"));
        public final Setting.Num speed = (this.add(new Setting.Num("speed", "Скорость", 1.0, 0.2, 3.0, 0.1, "×")))
                .showIf(() -> !this.mode.is("Градиент") || this.shineOn());
        public final Setting.Bool shine = this.add(new Setting.Bool("shine", "Блик", true));
        public final Setting.Bool glow;

        protected Base(String id, String name, String desc, Category c, boolean glowDefault) {
            super(id, name, desc, c, false);
            this.glow = this.add(new Setting.Bool("glow", "Свечение", glowDefault));
        }

        private boolean shineOn() {
            return this.shine != null && this.shine.on();
        }

        public boolean recolour() {
            return this.style.is("Перекраска");
        }

        int colourA() {
            return this.a.argb() | 0xFF000000;
        }

        int colourB() {
            return (this.b.followsTheme() ? Theme.accent2() : this.b.argb()) | 0xFF000000;
        }

        public int colourAt(float t, float time) {
            float tm = time * this.speed.f();
            int n;
            switch (this.mode.get()) {
                case "Градиент" -> n = Paint.lerp(this.colourA(), this.colourB(), Paint.clamp(t));
                case "Радуга" -> n = Paint.hsv(Paint.frac(t * 0.55F - tm * 0.22F), 0.7F, 1.0F);
                case "Один цвет" -> n = this.colourA();
                default ->
                    n = Paint.lerp(
                            this.colourA(),
                            this.colourB(),
                            0.5F + 0.5F * (float) Math.sin((double) (t * 1.7F - tm * 1.1F) * Math.PI));
            }

            int c = n;
            if (this.shine.on()) {
                float p = Paint.frac(tm * 0.38F) * 2.2F - 0.6F;
                float d = Math.abs(t - p);
                float s = Math.max(0.0F, 1.0F - d / 0.16F);
                s = s * s * (3.0F - 2.0F * s);
                c = Paint.lerp(c, -1, 0.78F * s);
            }

            return c;
        }
    }

    public static final class Armor extends Base {
        public final Setting.Bool others = this.add(new Setting.Bool("others", "На других игроках", false));

        public Armor() {
            super(
                    "armor_paint",
                    "Цвет брони",
                    "Перекрашивает надетую броню: градиент, переливание, блик",
                    Category.PLAYERS,
                    false);
        }
    }
}
