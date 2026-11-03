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

public enum Category {
    COMBAT("Combat", "swords", "Удары, цель, прицел и всё о бое"),
    VISUALS("Visuals", "eye", "Игроки, эффекты, руки и мир вокруг"),
    UTILITY("Utilities", "w.wrench", "Удобства: чат, инвентарь, зум и помощники"),
    HUD("HUD", "layout-dashboard", "Элементы интерфейса поверх игры"),
    PLAYERS("Players", "user-round", "Как ты видишь других: хитбоксы, обводка, ники"),
    EFFECTS("Effects", "sparkles", "Следы и частицы, которые тянутся за тобой"),
    HANDS("Hands", "hand", "Положение, анимации и подсветка рук"),
    WORLD("World", "compass", "Небо, свет, погода и всё вокруг"),
    COSMETICS("Cosmetics", "shirt", "Плащи, ножи и аксессуары на персонаже"),
    HOLYWORLD("HolyWorld", "flame", "Хелперы для сервера HolyWorld"),
    FUNTIME("FunTime", "flame", "Хелперы для сервера FunTime");

    public final String title;
    public final String icon;
    public final String subtitle;

    private Category(String title, String icon, String subtitle) {
        this.title = title;
        this.icon = icon;
        this.subtitle = subtitle;
    }

    public Category section() {
        Category var10000;
        switch (this.ordinal()) {
            case 0:
            case 4:
            case 5:
            case 6:
            case 7:
                var10000 = VISUALS;
                break;
            case 1:
            case 2:
            case 3:
            default:
                var10000 = this;
        }

        return var10000;
    }
}
