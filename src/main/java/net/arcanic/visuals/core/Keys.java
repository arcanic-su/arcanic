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
            return switch (code - 1000) {
                case 2 -> "СКМ";
                default -> "M" + (code - 1000 + 1);
            };
        } else if (code >= 65 && code <= 90) {
            return String.valueOf((char) code);
        } else if (code >= 48 && code <= 57) {
            return String.valueOf((char) code);
        } else if (code >= 290 && code <= 314) {
            return "F" + (code - 290 + 1);
        } else if (code >= 320 && code <= 329) {
            return "Num" + (code - 320);
        } else {
            return switch (code) {
                case 32 -> "Space";
                case 39 -> "'";
                case 44 -> ",";
                case 45 -> "-";
                case 46 -> ".";
                case 47 -> "/";
                case 59 -> ";";
                case 61 -> "=";
                case 91 -> "[";
                case 92 -> "\\";
                case 93 -> "]";
                case 96 -> "`";
                case 257 -> "Enter";
                case 258 -> "Tab";
                case 260 -> "Ins";
                case 261 -> "Del";
                case 262 -> "→";
                case 263 -> "←";
                case 264 -> "↓";
                case 265 -> "↑";
                case 266 -> "PgUp";
                case 267 -> "PgDn";
                case 268 -> "Home";
                case 269 -> "End";
                case 280 -> "Caps";
                case 281 -> "ScrLk";
                case 283 -> "PrtSc";
                case 284 -> "Pause";
                case 331 -> "Num/";
                case 332 -> "Num*";
                case 333 -> "Num-";
                case 334 -> "Num+";
                case 335 -> "NumEnter";
                case 340 -> "LShift";
                case 341 -> "LCtrl";
                case 342 -> "LAlt";
                case 344 -> "RShift";
                case 345 -> "RCtrl";
                case 346 -> "RAlt";
                default -> "#" + code;
            };
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
                    if (Trust.blocks(m)) {
                        Trust.deny(m);
                    } else if (m == Modules.CLICK_PEARL) {
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
