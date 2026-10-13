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
import net.minecraft.client.Minecraft;

/**
 * World Tweaker module
 *
 * Changes the look of the world: lighting, fog, time of day, sky and weather.
 */
public final class WorldTweaks extends Module {
    public static final String[] SKIES = {
        "Dreamscape", "Aurora", "Galaxy", "Nebula", "Clouds", "Black Hole", "Starfall", "Caustics", "Storm", "End Beams"
    };

    // lighting
    public final Setting.Bool light = add(new Setting.Bool("light", "Освещение", false));
    public final Setting.Num brightness = add(new Setting.Num("brightness", "Яркость мира", .6, 0, 1, .05, ""))
            .showIf(light::on);
    // fog
    public final Setting.Bool fog = add(new Setting.Bool("fog", "Свой туман", false));
    public final Setting.Colour fogA =
            add(new Setting.Colour("fog_a", "Цвет тумана 1", true)).showIf(fog::on);
    public final Setting.Colour fogB =
            add(new Setting.Colour("fog_b", "Цвет тумана 2", false, 0xFFFFFFFF)).showIf(fog::on);
    public final Setting.Num fogStart = add(new Setting.Num("fog_start", "Начало тумана", 0, 0, 256, 4, " бл"))
            .showIf(fog::on);
    public final Setting.Num fogEnd = add(new Setting.Num("fog_end", "Конец тумана", 128, 16, 512, 8, " бл"))
            .showIf(fog::on);
    public final Setting.Num fogHeight = add(new Setting.Num("fog_height", "Высота тумана", 120, -64, 320, 4, ""))
            .showIf(fog::on);
    public final Setting.Num fogFalloff = add(new Setting.Num("fog_falloff", "Спад по высоте", 40, 4, 160, 4, " бл"))
            .showIf(fog::on);
    public final Setting.Num fogNoise =
            add(new Setting.Num("fog_noise", "Шум тумана", .2, 0, 1, .05, "")).showIf(fog::on);
    // time
    public final Setting.Bool time = add(new Setting.Bool("time", "Своё время", false));
    public final Setting.Num hour =
            add(new Setting.Num("hour", "Время суток", 18, 0, 24, .25, " ч")).showIf(time::on);
    // sky
    public final Setting.Bool sky = add(new Setting.Bool("sky", "Своё небо", true));
    public final Setting.Mode skyMode = add(new Setting.Mode("sky_mode", "Вид неба", "Шейдер", "Градиент", "Шейдер"))
            .showIf(sky::on);
    public final Setting.Mode shader = add(new Setting.Mode("sky_shader", "Шейдер неба", "Aurora", SKIES))
            .showIf(() -> sky.on() && skyMode.is("Шейдер"));
    public final Setting.Mode dreamStyle = add(new Setting.Mode(
                    "dream_style", "Стиль Dreamscape", "Ember", "Ember", "Nebula", "Silk", "Contour"))
            .showIf(() -> shaderSky() && shader.is("Dreamscape"));
    public final Setting.Bool themeColours =
            add(new Setting.Bool("theme_colours", "Цвета темы", true)).showIf(sky::on);
    public final Setting.Colour skyA = add(new Setting.Colour("sky_a", "Цвет неба 1", false, 0xFF6D28D9))
            .showIf(() -> sky.on() && !themeColours.on());
    public final Setting.Colour skyB = add(new Setting.Colour("sky_b", "Цвет неба 2", false, 0xFF0EA5E9))
            .showIf(() -> sky.on() && !themeColours.on());
    public final Setting.Colour skyC = add(new Setting.Colour("sky_c", "Цвет неба 3", false, 0xFFF472B6))
            .showIf(() -> shaderSky() && shader.is("Dreamscape") && !themeColours.on());
    public final Setting.Bool skyGradient =
            add(new Setting.Bool("sky_gradient", "Смешивать два цвета", true)).showIf(sky::on);
    public final Setting.Num pattern = add(new Setting.Num("pattern", "Размер узора", .5, .2, 2, .05, "×"))
            .showIf(() -> shaderSky() && shader.is("Dreamscape"));
    public final Setting.Num speed = add(new Setting.Num("sky_speed", "Скорость шейдера", 1, 0, 5, .1, "×"))
            .showIf(this::shaderSky);
    public final Setting.Num alpha = add(new Setting.Num("sky_alpha", "Плотность шейдера", 1, 0, 1, .05, ""))
            .showIf(this::shaderSky);
    public final Setting.Num skyBright = add(new Setting.Num("sky_brightness", "Яркость шейдера", 1, .2, 3, .05, "×"))
            .showIf(this::shaderSky);
    public final Setting.Mode skyQuality = add(new Setting.Mode(
                    "sky_quality", "Качество шейдера", "Авто", "Авто", "Высокое", "Среднее", "Низкое"))
            .showIf(this::shaderSky);
    public final Setting.Bool fullSky =
            add(new Setting.Bool("full_sky", "Небо и под горизонтом", false)).showIf(sky::on);
    public final Setting.Bool sunMoon =
            add(new Setting.Bool("sun_moon", "Солнце и луна", true)).showIf(sky::on);
    public final Setting.Bool clouds =
            add(new Setting.Bool("clouds", "Облака", true)).showIf(sky::on);
    // weather
    public final Setting.Bool weather = add(new Setting.Bool("weather", "Своя погода", false));
    public final Setting.Mode weatherMode = add(new Setting.Mode(
                    "weather_mode", "Погода", "Ясно", "Ясно", "Дождь", "Гроза"))
            .showIf(weather::on);
    public final Setting.Num weatherStrength = add(new Setting.Num("weather_strength", "Сила погоды", 1, 0, 1, .05, ""))
            .showIf(() -> weather.on() && !weatherMode.is("Ясно"));

    public WorldTweaks() {
        super(
                "world_tweaks",
                "World Tweaker",
                "Свет, туман, время, небо и погода — как тебе нравится",
                Category.WORLD,
                false);
    }

    private boolean shaderSky() {
        return sky.on() && skyMode.is("Шейдер");
    }

    public boolean skyActive() {
        return enabled() && sky.on() && !CustomSky.shaderPackInUse();
    }

    public int colourA() {
        return (themeColours.on() ? Theme.accent() : skyA.argb()) | 0xFF000000;
    }

    public int colourB() {
        return (themeColours.on() ? Theme.accent2() : skyB.argb()) | 0xFF000000;
    }

    public int colourC() {
        return (themeColours.on() ? Paint.lerp(Theme.accent(), 0xFFFFFFFF, .45f) : skyC.argb()) | 0xFF000000;
    }

    @Override
    public void tick() {
        var level = Minecraft.getInstance().level;
        if (level != null && time.on())
            level.getLevelData().setDayTime(Math.floorMod(Math.round((hour.get() - 6) * 1000), 24000));
    }

    @Override
    protected void onDisable() {
        CustomSky.release();
    }

    public float rain(float vanilla) {
        return !enabled() || !weather.on() ? vanilla : weatherMode.is("Ясно") ? 0 : weatherStrength.f();
    }

    public float thunder(float vanilla) {
        return !enabled() || !weather.on() ? vanilla : weatherMode.is("Гроза") ? weatherStrength.f() : 0;
    }

    public float gamma(float vanilla) {
        if (!enabled() || !light.on()) return vanilla;
        float b = brightness.f();
        return vanilla + b * b * 12;
    }

    public boolean fogOn() {
        return enabled() && fog.on();
    }

    public float[] fogColour() {
        var mc = Minecraft.getInstance();
        double y = mc.gameRenderer.getMainCamera().position().y;
        float k = (float) Math.max(0, Math.min(1, (y - fogHeight.get()) / fogFalloff.get() + .5));
        float n = fogNoise.f() * (float) (Math.sin(System.nanoTime() / 1.7e9) * .5 + .5) * .25f;
        int c = Paint.lerp(fogA.argb() | 0xFF000000, fogB.argb() | 0xFF000000, Math.max(0, Math.min(1, k + n)));
        return new float[] {(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }
}
