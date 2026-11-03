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

public final class MenuPrefs extends Module {
    public final Setting.Mode language = (Setting.Mode)
            this.add(new Setting.Mode("language", "Язык / Language", "Русский", new String[] {"Русский", "English"}));
    public final Setting.Bool sounds = this.add(new Setting.Bool("sounds", "Звуки меню", true));
    public final Setting.Bool wide = this.add(new Setting.Bool("wide", "Большое меню", false));
    public final Setting.Num volume;
    public final Setting.Bool hoverSounds;
    public final Setting.Bool sliderSounds;
    public final Setting.Bool blur;
    public final Setting.Num dim;
    public final Setting.Bool spotlight;
    public final Setting.Bool ripples;
    public final Setting.Bool tips;
    public final Setting.Bool notify;
    public final Setting.Bool presetAutosave;
    public final Setting.Bool animations;
    public final Setting.Num animSpeed;
    public final Setting.Num scale;
    public final Setting.Bool drag;
    public final Setting.Bool remember;
    /** Size set by dragging the menu corner, in unscaled menu units; 0 means the default size. */
    public final Setting.Num width =
            add(new Setting.Num("panel_w", "Ширина меню", 0, 0, 4000, 1, "")).showIf(() -> false);

    public final Setting.Num height =
            add(new Setting.Num("panel_h", "Высота меню", 0, 0, 4000, 1, "")).showIf(() -> false);
    public static double posX;
    public static double posY;
    public static boolean compactSidebar;

    public MenuPrefs() {
        super("menu", "Меню", "Настройки самого меню", Category.HUD, true);
        Setting.Num var10001 = this.add(new Setting.Num("volume", "Громкость", 70.0, 0.0, 100.0, 5.0, "%"));
        Setting.Bool var10002 = this.sounds;
        this.volume = (Setting.Num) var10001.showIf(var10002::on);
        Setting.Bool var1 = this.add(new Setting.Bool("hover_sounds", "Звук при наведении", true));
        var10002 = this.sounds;
        this.hoverSounds = (Setting.Bool) var1.showIf(var10002::on);
        var1 = this.add(new Setting.Bool("slider_sounds", "Щелчки ползунков", true));
        var10002 = this.sounds;
        this.sliderSounds = (Setting.Bool) var1.showIf(var10002::on);
        this.blur = this.add(new Setting.Bool("blur", "Размытие мира", true));
        this.dim = this.add(new Setting.Num("dim", "Затемнение", 40.0, 0.0, 90.0, 5.0, "%"));
        this.spotlight = this.add(new Setting.Bool("spotlight", "Свет за курсором", true));
        this.ripples = this.add(new Setting.Bool("ripples", "Волны от клика", true));
        this.tips = this.add(new Setting.Bool("tips", "Подсказки", true));
        this.notify = this.add(new Setting.Bool("notify", "Уведомления биндов", true));
        this.presetAutosave = this.add(new Setting.Bool("preset_autosave", "Автосохранение конфига", false));
        this.animations = this.add(new Setting.Bool("animations", "Анимации", true));
        Setting.Num var3 = this.add(new Setting.Num("anim_speed", "Скорость", 1.0, 0.5, 2.0, 0.1, "×"));
        var10002 = this.animations;
        this.animSpeed = (Setting.Num) var3.showIf(var10002::on);
        this.scale = this.add(new Setting.Num("scale", "Размер меню", 100.0, 50.0, 200.0, 5.0, "%"));
        this.drag = this.add(new Setting.Bool("drag", "Перетаскивание", true));
        Setting.Bool var4 = this.add(new Setting.Bool("remember", "Помнить позицию", true));
        var10002 = this.drag;
        this.remember = (Setting.Bool) var4.showIf(var10002::on);
    }

    public void sync() {
        Theme.animations = this.animations.on();
        Theme.menuScale = (Double) this.scale.get() / 100.0;
        Anim.speedMul = this.animations.on() ? this.animSpeed.f() : 1.0F;
    }
}
