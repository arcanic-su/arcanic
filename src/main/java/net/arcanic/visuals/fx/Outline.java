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
import net.arcanic.visuals.hud.TargetHud;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Outline module
 *
 * Draws a glowing outline around enemies. It is depth tested, so it never shows through walls.
 */
public final class Outline extends Module {
    public final Setting.Mode style = add(new Setting.Mode("style", "Стиль", "Чамсы", "Чамсы", "Контур"));

    public final Setting.Mode material = add(new Setting.Mode(
                    "material", "Материал", "Заливка", "Простой", "Заливка", "Стекло", "Хром", "Жидкое стекло"))
            .showIf(this::chams);
    public final Setting.Bool walls =
            add(new Setting.Bool("walls", "Сквозь стены", false)).showIf(this::chams);
    public final Setting.Bool players = add(new Setting.Bool("players", "Игроки", true));
    public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Враждебные мобы", false));
    public final Setting.Bool onlyTarget = add(new Setting.Bool("only_target", "Только цель", false));
    public final Setting.Bool allies = add(new Setting.Bool("allies", "Без союзников", true));
    public final Setting.Mode colour =
            add(new Setting.Mode("colour", "Цвет", "Тема", "Тема", "Свой", "Здоровье", "Радуга"));
    public final Setting.Colour a =
            add(new Setting.Colour("color_a", "Цвет 1", false, 0xFFC084FC)).showIf(() -> colour.is("Свой"));
    public final Setting.Colour b =
            add(new Setting.Colour("color_b", "Цвет 2", false, 0xFF22D3EE)).showIf(() -> colour.is("Свой"));
    public final Setting.Num thickness =
            add(new Setting.Num("thickness", "Толщина", 3, 1, 6, .5, " px")).showIf(() -> !chams());
    public final Setting.Num opacity = add(new Setting.Num("opacity", "Яркость", 95, 20, 100, 5, "%"));
    public final Setting.Bool glow =
            add(new Setting.Bool("glow", "Свечение", true)).showIf(() -> !chams());
    public final Setting.Bool flow = add(new Setting.Bool("flow", "Переливание", true));
    public final Setting.Bool fill =
            add(new Setting.Bool("fill", "Заливка", false)).showIf(() -> !chams());
    public final Setting.Num fillOpacity = add(new Setting.Num("fill_opacity", "Сила заливки", 22, 5, 60, 1, "%"))
            .showIf(() -> fill.on() && !chams());
    public final Setting.Bool hitFlash = add(new Setting.Bool("hit_flash", "Вспышка при ударе", true));

    public Outline() {
        super(
                "outline",
                "Обводка",
                "Светящийся контур врагов — только когда их видно, не сквозь стены",
                Category.PLAYERS,
                false);
    }

    public boolean chams() {
        return style.is("Чамсы");
    }

    public int material() {
        return switch (material.get()) {
            case "Простой" -> 0;
            case "Заливка" -> 1;
            case "Стекло" -> 2;
            case "Хром" -> 3;
            default -> 4;
        };
    }

    public boolean wants(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e == mc.player || !e.isAlive() || e.isInvisibleTo(mc.player)) return false;
        if (onlyTarget.on()) return e == TargetHud.current();
        if (allies.on() && e.isAlliedTo(mc.player)) return false;
        if (e instanceof Avatar) return players.on() && !e.isSpectator();
        return mobs.on() && e instanceof Enemy;
    }

    int colourAt(float h, float time, float health) {
        float tm = flow.on() ? time * .55f : 0;
        return switch (colour.get()) {
            case "Свой" -> Paint.lerp(a.argb() | 0xFF000000, b.argb() | 0xFF000000, wave(h, tm));
            case "Здоровье" ->
                health > .5f
                        ? Paint.lerp(0xFFFACC15, 0xFF4ADE80, (health - .5f) * 2)
                        : Paint.lerp(0xFFF43F5E, 0xFFFACC15, health * 2);
            case "Радуга" -> Paint.hsv(Paint.frac(h * .5f - tm * .4f), .65f, 1);
            default -> Paint.lerp(Theme.accent() | 0xFF000000, Theme.accent2() | 0xFF000000, wave(h, tm));
        };
    }

    private static float wave(float h, float tm) {
        return tm == 0 ? Paint.clamp(h) : .5f + .5f * (float) Math.sin((h * 1.3f - tm) * Math.PI);
    }
}
