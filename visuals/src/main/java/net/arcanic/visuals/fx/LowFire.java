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

/**
 * The fire on the screen while you burn, made quieter: lowered out of the way, smaller, see-through and in any
 * colour. The vanilla overlay is two quads in front of the camera; this only moves, scales and tints them.
 */
public final class LowFire extends Module {
    public final Setting.Num height = add(new Setting.Num("height", "Высота", 0.35, 0.0, 0.8, 0.01, ""));
    public final Setting.Num size = add(new Setting.Num("size", "Размер", 0.8, 0.3, 1.2, 0.01, "×"));
    public final Setting.Num opacity = add(new Setting.Num("opacity", "Прозрачность", 0.45, 0.05, 1.0, 0.01, ""));
    public final Setting.Bool tint = add(new Setting.Bool("tint", "Свой цвет", false));
    public final Setting.Colour colour =
            add(new Setting.Colour("color", "Цвет огня", false, 0xFFFFB066)).showIf(this.tint::on);

    public LowFire() {
        super(
                "low_fire",
                "Low Fire",
                "Огонь на экране, когда горишь: ниже, меньше и полупрозрачный, чтобы не закрывал обзор. Можно перекрасить.",
                Category.WORLD,
                false);
    }

    /** The overlay's alpha (vanilla 0.9) times the chosen opacity. */
    public float alpha(float vanilla) {
        return vanilla * this.opacity.get().floatValue();
    }
}
