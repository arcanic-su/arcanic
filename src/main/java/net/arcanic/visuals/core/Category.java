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
    COMBAT("Бой", "swords", "Отклик ударов, прицел и всё о твоей цели"),
    PLAYERS("Игроки", "user-round", "Как ты видишь других: хитбоксы, обводка, ники"),
    WORLD("Мир", "compass", "Небо, свет, погода и всё вокруг"),
    EFFECTS("Эффекты", "sparkles", "Следы и частицы, которые тянутся за тобой"),
    HANDS("Руки", "hand", "Положение, анимации и подсветка рук"),
    COSMETICS("Косметика", "shirt", "Плащи, ножи и аксессуары на персонаже"),
    HUD("HUD", "layout-dashboard", "Элементы интерфейса поверх игры");
    public final String title, icon, subtitle;

    Category(String title, String icon, String subtitle) {
        this.title = title;
        this.icon = icon;
        this.subtitle = subtitle;
    }
}
