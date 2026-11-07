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

import java.lang.reflect.Method;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;

public final class SkyShader extends Module {
    public final Setting.Mode mode = this.add(new Setting.Mode(
            "mode",
            "Режим",
            "Туманность",
            "Туманность",
            "Сияние",
            "Звёзды",
            "Плазма",
            "Неон",
            "Каустика",
            "Космос",
            "Галактика",
            "Глубокий космос"));
    public final Setting.Colour color =
            (this.add(new Setting.Colour("color", "Цвет", false, 0xFF3399FF))).showIf(() -> !this.mode.is("Неон"));
    public final Setting.Mode palitraNeona = (this.add(
                    new Setting.Mode("neon_palette", "Палитра неона", "RGB", "RGB", "Тёмные", "Светлые", "Свои")))
            .showIf(() -> this.mode.is("Неон"));
    public final Setting.Colour neonColor1 =
            (this.add(new Setting.Colour("neon_1", "Неон цвет 1", false, -65536))).showIf(this::customNeon);
    public final Setting.Colour neonColor2 =
            (this.add(new Setting.Colour("neon_2", "Неон цвет 2", false, 0xFF00FF00))).showIf(this::customNeon);
    public final Setting.Colour neonColor3 =
            (this.add(new Setting.Colour("neon_3", "Неон цвет 3", false, 0xFF0000FF))).showIf(this::customNeon);
    public final Setting.Num speed = this.add(new Setting.Num("speed", "Скорость", 1.0, 0.1, 5.0, 0.1, ""));
    public final Setting.Num size =
            (this.add(new Setting.Num("size", "Размер", 5.0, 1.0, 20.0, 0.5, ""))).showIf(() -> !this.overlay());
    public final Setting.Num intensivnost = (this.add(
                    new Setting.Num("intensity", "Интенсивность", 0.01, 0.001, 0.05, 0.001, "")))
            .showIf(() -> !this.overlay());
    public final Setting.Num opacity = this.add(new Setting.Num("opacity", "Прозрачность", 1.0, 0.3, 1.0, 0.05, ""));
    private static boolean irisChecked;
    private static Object iris;
    private static Method packInUse;

    public SkyShader() {
        super("sky_shader", "SkyShader", "Анимированное небо", Category.WORLD, false);
    }

    private boolean customNeon() {
        return this.mode.is("Неон") && this.palitraNeona.is("Свои");
    }

    public boolean overlay() {
        return this.mode.is("Каустика")
                || this.mode.is("Космос")
                || this.mode.is("Галактика")
                || this.mode.is("Глубокий космос");
    }

    public boolean active() {
        return this.enabled() && !shaderPackInUse();
    }

    public boolean replaces() {
        return this.active() && !this.overlay();
    }

    public boolean overlays() {
        return this.active() && this.overlay();
    }

    private static boolean shaderPackInUse() {
        if (!irisChecked) {
            irisChecked = true;

            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                packInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
            }
        }

        try {
            return packInUse != null && (Boolean) packInUse.invoke(iris);
        } catch (Throwable e) {
            return false;
        }
    }

    protected void onEnable() {
        ShaderSky.release();
        ThemeSky.release();
    }

    protected void onDisable() {
        ShaderSky.release();
        ThemeSky.release();
    }
}
