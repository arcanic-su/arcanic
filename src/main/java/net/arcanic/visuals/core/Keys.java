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

import net.arcanic.visuals.helper.HolyFeatures;
import net.arcanic.visuals.helper.Pings;
import net.arcanic.visuals.hud.Toasts;
import net.minecraft.client.Minecraft;

public final class Keys {
    public static final int MOUSE = 1000;

    private Keys() {}

    public static String label(int code) {
        if (code < 0) {
            return "—";
        } else if (code >= 1000) {
            String var1;
            switch (code - 1000) {
                case 2 -> var1 = "СКМ";
                default -> var1 = "M" + (code - 1000 + 1);
            }

            return var1;
        } else if (code >= 65 && code <= 90) {
            return String.valueOf((char) code);
        } else if (code >= 48 && code <= 57) {
            return String.valueOf((char) code);
        } else if (code >= 290 && code <= 314) {
            return "F" + (code - 290 + 1);
        } else if (code >= 320 && code <= 329) {
            return "Num" + (code - 320);
        } else {
            String var10000;
            switch (code) {
                case 32 -> var10000 = "Space";
                case 39 -> var10000 = "'";
                case 44 -> var10000 = ",";
                case 45 -> var10000 = "-";
                case 46 -> var10000 = ".";
                case 47 -> var10000 = "/";
                case 59 -> var10000 = ";";
                case 61 -> var10000 = "=";
                case 91 -> var10000 = "[";
                case 92 -> var10000 = "\\";
                case 93 -> var10000 = "]";
                case 96 -> var10000 = "`";
                case 257 -> var10000 = "Enter";
                case 258 -> var10000 = "Tab";
                case 260 -> var10000 = "Ins";
                case 261 -> var10000 = "Del";
                case 262 -> var10000 = "→";
                case 263 -> var10000 = "←";
                case 264 -> var10000 = "↓";
                case 265 -> var10000 = "↑";
                case 266 -> var10000 = "PgUp";
                case 267 -> var10000 = "PgDn";
                case 268 -> var10000 = "Home";
                case 269 -> var10000 = "End";
                case 280 -> var10000 = "Caps";
                case 281 -> var10000 = "ScrLk";
                case 283 -> var10000 = "PrtSc";
                case 284 -> var10000 = "Pause";
                case 331 -> var10000 = "Num/";
                case 332 -> var10000 = "Num*";
                case 333 -> var10000 = "Num-";
                case 334 -> var10000 = "Num+";
                case 335 -> var10000 = "NumEnter";
                case 340 -> var10000 = "LShift";
                case 341 -> var10000 = "LCtrl";
                case 342 -> var10000 = "LAlt";
                case 344 -> var10000 = "RShift";
                case 345 -> var10000 = "RCtrl";
                case 346 -> var10000 = "RAlt";
                default -> var10000 = "#" + code;
            }

            return var10000;
        }
    }

    public static void pressed(int code) {
        Minecraft mc = Minecraft.getInstance();
        if (code >= 0 && mc.screen == null && mc.player != null) {
            Modules.FAST_SWAP.pressed(code);
            Modules.ELYTRA_SWAP.pressed(code);
            // the wheel: pings and the player menu when a friend plays here or an Arcanic player is aimed at;
            // otherwise the old friend list
            if (code == MOUSE + 2 && !Pings.press()) Modules.FRIENDS.middleClicked();
            for (Module m : Modules.ALL) {
                if (m.bind == code) {
                    if (m == Modules.CLICK_PEARL) {
                        Modules.CLICK_PEARL.press();
                    } else if (!m.enabled() && HolyFeatures.forbidden(m)) {
                        HolyFeatures.deny(m);
                    } else {
                        m.toggle();
                        m.knob = m.enabled() ? 1.0F : 0.0F;
                        Toasts.module(m);
                        Config.dirty();
                    }
                }
            }
        }
    }
}
