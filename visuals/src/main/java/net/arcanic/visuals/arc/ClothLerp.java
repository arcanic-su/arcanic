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
package net.arcanic.visuals.arc;

/** A cloth point: its position now and on the previous tick, for smooth drawing between ticks. */
public final class ClothLerp {
    public ClothVec position = new ClothVec(0, 0, 0);
    public ClothVec prevPosition = new ClothVec(0, 0, 0);
    public boolean locked;

    public float getLerpX(float partialTick) {
        return lerp(partialTick, prevPosition.x, position.x);
    }

    public float getLerpY(float partialTick) {
        return lerp(partialTick, prevPosition.y, position.y);
    }

    public float getLerpZ(float partialTick) {
        return lerp(partialTick, prevPosition.z, position.z);
    }

    private static float lerp(float t, float from, float to) {
        return from + t * (to - from);
    }
}
