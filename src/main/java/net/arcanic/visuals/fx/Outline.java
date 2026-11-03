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

public final class Outline extends Module {
    public final Setting.Mode style =
            this.add(new Setting.Mode("style", "Стиль", "Чамсы", new String[] {"Чамсы", "Контур"}));
    public final Setting.Mode material = (this.add(new Setting.Mode("material", "Материал", "Заливка", new String[] {
                "Простой", "Заливка", "Стекло", "Хром", "Жидкое стекло"
            })))
            .showIf(this::chams);
    public final Setting.Bool players = this.add(new Setting.Bool("players", "Игроки", true));
    public final Setting.Bool mobs = this.add(new Setting.Bool("mobs", "Враждебные мобы", false));
    public final Setting.Bool onlyTarget = this.add(new Setting.Bool("only_target", "Только цель", false));
    public final Setting.Bool allies = this.add(new Setting.Bool("allies", "Без союзников", true));
    public final Setting.Mode colour = (Setting.Mode)
            this.add(new Setting.Mode("colour", "Цвет", "Тема", new String[] {"Тема", "Свой", "Здоровье", "Радуга"}));
    public final Setting.Colour a =
            (this.add(new Setting.Colour("color_a", "Цвет 1", false, -4160260))).showIf(() -> this.colour.is("Свой"));
    public final Setting.Colour b =
            (this.add(new Setting.Colour("color_b", "Цвет 2", false, -14494738))).showIf(() -> this.colour.is("Свой"));
    public final Setting.Num thickness =
            (this.add(new Setting.Num("thickness", "Толщина", 3.0, 1.0, 6.0, 0.5, " px"))).showIf(() -> !this.chams());
    public final Setting.Num opacity = this.add(new Setting.Num("opacity", "Яркость", 95.0, 20.0, 100.0, 5.0, "%"));
    public final Setting.Bool glow =
            (Setting.Bool) (this.add(new Setting.Bool("glow", "Свечение", true))).showIf(() -> !this.chams());
    public final Setting.Bool flow = this.add(new Setting.Bool("flow", "Переливание", true));
    public final Setting.Bool fill =
            (Setting.Bool) (this.add(new Setting.Bool("fill", "Заливка", false))).showIf(() -> !this.chams());
    public final Setting.Num fillOpacity = (this.add(
                    new Setting.Num("fill_opacity", "Сила заливки", 22.0, 5.0, 60.0, 1.0, "%")))
            .showIf(() -> this.fill.on() && !this.chams());
    public final Setting.Bool hitFlash = this.add(new Setting.Bool("hit_flash", "Вспышка при ударе", true));

    public Outline() {
        super(
                "outline",
                "Обводка",
                "Светящийся контур врагов — только когда их видно, не сквозь стены",
                Category.PLAYERS,
                false);
    }

    public boolean chams() {
        return this.style.is("Чамсы");
    }

    public int material() {
        byte var10000;
        switch ((String) this.material.get()) {
            case "Простой" -> var10000 = 0;
            case "Заливка" -> var10000 = 1;
            case "Стекло" -> var10000 = 2;
            case "Хром" -> var10000 = 3;
            default -> var10000 = 4;
        }

        return var10000;
    }

    public boolean wants(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && e != mc.player && e.isAlive() && !e.isInvisibleTo(mc.player)) {
            if (this.onlyTarget.on()) {
                return e == TargetHud.current();
            } else if (this.allies.on() && e.isAlliedTo(mc.player) || Friends.hideFromEnemies(e)) {
                return false;
            } else if (e instanceof Avatar) {
                return this.players.on() && !e.isSpectator();
            } else {
                return this.mobs.on() && e instanceof Enemy;
            }
        } else {
            return false;
        }
    }

    int colourAt(float h, float time, float health) {
        float tm = this.flow.on() ? time * 0.55F : 0.0F;
        int var10000;
        switch ((String) this.colour.get()) {
            case "Свой" -> var10000 = Paint.lerp(this.a.argb() | 0xFF000000, this.b.argb() | 0xFF000000, wave(h, tm));
            case "Здоровье" ->
                var10000 = health > 0.5F
                        ? Paint.lerp(-340971, -11870592, (health - 0.5F) * 2.0F)
                        : Paint.lerp(-770210, -340971, health * 2.0F);
            case "Радуга" -> var10000 = Paint.hsv(Paint.frac(h * 0.5F - tm * 0.4F), 0.65F, 1.0F);
            default -> var10000 = Paint.lerp(Theme.accent() | 0xFF000000, Theme.accent2() | 0xFF000000, wave(h, tm));
        }

        return var10000;
    }

    private static float wave(float h, float tm) {
        return tm == 0.0F ? Paint.clamp(h) : 0.5F + 0.5F * (float) Math.sin((double) (h * 1.3F - tm) * Math.PI);
    }
}
