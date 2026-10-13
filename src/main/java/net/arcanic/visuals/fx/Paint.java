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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** Weapon colour and Armour colour: paint the held weapon and the worn armour with a colour, gradient or shimmer. */
public final class Paint {
    private Paint() {}

    static final long T0 = System.nanoTime();

    public static float time() {
        return (System.nanoTime() - T0) / 1e9f;
    }

    public abstract static class Base extends Module {
        public final Setting.Mode mode =
                add(new Setting.Mode("mode", "Режим", "Переливание", "Переливание", "Градиент", "Радуга", "Один цвет"));
        public final Setting.Colour a = add(new Setting.Colour("color_a", "Цвет 1", true));
        public final Setting.Colour b = add(new Setting.Colour("color_b", "Цвет 2", false, 0xFF22D3EE))
                .showIf(() -> !mode.is("Один цвет") && !mode.is("Радуга"));
        public final Setting.Mode style =
                add(new Setting.Mode("style", "Стиль", "Перекраска", "Перекраска", "Оттенок"));
        public final Setting.Num speed = add(new Setting.Num("speed", "Скорость", 1, .2, 3, .1, "×"))
                .showIf(() -> !mode.is("Градиент") || shineOn());
        public final Setting.Bool shine = add(new Setting.Bool("shine", "Блик", true));
        public final Setting.Bool glow;

        protected Base(String id, String name, String desc, Category c, boolean glowDefault) {
            super(id, name, desc, c, false);
            glow = add(new Setting.Bool("glow", "Свечение", glowDefault));
        }

        private boolean shineOn() {
            return shine != null && shine.on();
        }

        public boolean recolour() {
            return style.is("Перекраска");
        }

        int colourA() {
            return a.argb() | 0xFF000000;
        }

        int colourB() {
            return (b.followsTheme() ? Theme.accent2() : b.argb()) | 0xFF000000;
        }

        /** Colour at position t (0 = handle / feet, 1 = tip / head) at the given time. */
        public int colourAt(float t, float time) {
            float tm = time * speed.f();
            int c =
                    switch (mode.get()) {
                        case "Градиент" -> lerp(colourA(), colourB(), clamp(t));
                        case "Радуга" -> hsv(frac(t * .55f - tm * .22f), .7f, 1);
                        case "Один цвет" -> colourA();
                        default ->
                            lerp(colourA(), colourB(), .5f + .5f * (float) Math.sin((t * 1.7f - tm * 1.1f) * Math.PI));
                    };
            if (shine.on()) {
                float p = frac(tm * .38f) * 2.2f - .6f; // a light band sweeping up every few seconds
                float d = Math.abs(t - p), s = Math.max(0, 1 - d / .16f);
                s = s * s * (3 - 2 * s);
                c = lerp(c, 0xFFFFFFFF, .78f * s);
            }
            return c;
        }
    }

    public static final class Weapon extends Base {
        public final Setting.Bool aura = add(new Setting.Bool("aura", "Аура", true));
        public final Setting.Num auraPower = add(new Setting.Num("aura_power", "Сила ауры", 60, 10, 100, 5, "%"))
                .showIf(aura::on);
        public final Setting.Bool onlyWeapons = add(new Setting.Bool("only_weapons", "Только оружие", true));
        public final Setting.Bool offhand = add(new Setting.Bool("offhand", "Левая рука", false));
        public final Setting.Bool thirdPerson = add(new Setting.Bool("third_person", "От третьего лица", true));

        public Weapon() {
            super(
                    "weapon_paint",
                    "Цвет оружия",
                    "Перекрашивает меч в руке: градиент, переливание, блик и аура",
                    Category.HANDS,
                    false);
        }

        public boolean paints(ItemStack s) {
            if (s.isEmpty()) return false;
            return !onlyWeapons.on() || s.has(DataComponents.WEAPON);
        }
    }

    public static final class Armor extends Base {
        public final Setting.Bool others = add(new Setting.Bool("others", "На других игроках", false));

        public Armor() {
            super(
                    "armor_paint",
                    "Цвет брони",
                    "Перекрашивает надетую броню: градиент, переливание, блик",
                    Category.PLAYERS,
                    false);
        }
    }

    static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = a >>> 24;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = b >>> 24;
        return ((int) (aa + (ba - aa) * t) << 24)
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    static int hsv(float h, float s, float v) {
        float r, g, b;
        int i = (int) Math.floor(h * 6);
        float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (Math.floorMod(i, 6)) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }
}
