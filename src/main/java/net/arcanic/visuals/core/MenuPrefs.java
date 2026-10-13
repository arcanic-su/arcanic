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

import net.arcanic.visuals.render.Anim;

/** Settings of the menu itself (the Settings page): sounds, look, behaviour, and where the panel stands. */
public final class MenuPrefs extends Module {
    public final Setting.Mode language =
            add(new Setting.Mode("language", "Язык / Language", "Русский", "Русский", "English"));
    public final Setting.Bool sounds = add(new Setting.Bool("sounds", "Звуки меню", true));
    public final Setting.Num volume =
            add(new Setting.Num("volume", "Громкость", 70, 0, 100, 5, "%")).showIf(sounds::on);
    public final Setting.Bool hoverSounds =
            add(new Setting.Bool("hover_sounds", "Звук при наведении", true)).showIf(sounds::on);
    public final Setting.Bool sliderSounds =
            add(new Setting.Bool("slider_sounds", "Щелчки ползунков", true)).showIf(sounds::on);

    public final Setting.Bool blur = add(new Setting.Bool("blur", "Размытие мира", true));
    public final Setting.Num dim = add(new Setting.Num("dim", "Затемнение", 40, 0, 90, 5, "%"));
    public final Setting.Bool spotlight = add(new Setting.Bool("spotlight", "Свет за курсором", true));
    public final Setting.Bool ripples = add(new Setting.Bool("ripples", "Волны от клика", true));
    public final Setting.Bool tips = add(new Setting.Bool("tips", "Подсказки", true));
    public final Setting.Bool notify = add(new Setting.Bool("notify", "Уведомления биндов", true));

    public final Setting.Bool animations = add(new Setting.Bool("animations", "Анимации", true));
    public final Setting.Num animSpeed =
            add(new Setting.Num("anim_speed", "Скорость", 1, .5, 2, .1, "×")).showIf(animations::on);
    public final Setting.Num scale = add(new Setting.Num("scale", "Размер меню", 100, 70, 130, 5, "%"));
    public final Setting.Bool drag = add(new Setting.Bool("drag", "Перетаскивание", true));
    public final Setting.Bool remember =
            add(new Setting.Bool("remember", "Помнить позицию", true)).showIf(drag::on);

    public static double posX, posY;
    public static boolean compactSidebar;

    public MenuPrefs() {
        super("menu", "Меню", "Настройки самого меню", Category.HUD, true);
    }

    public void sync() {
        Theme.animations = animations.on();
        Theme.menuScale = scale.get() / 100.0;
        Anim.speedMul = animations.on() ? animSpeed.f() : 1;
    }
}
