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

/** A small mutable vector for the cloth simulation; the operations change it in place and return it. */
public final class ClothVec {
    public float x;
    public float y;
    public float z;

    public ClothVec() {}

    public ClothVec(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ClothVec clone() {
        return new ClothVec(x, y, z);
    }

    public void copy(ClothVec other) {
        x = other.x;
        y = other.y;
        z = other.z;
    }

    public ClothVec add(ClothVec other) {
        x = x + other.x;
        y = y + other.y;
        z = z + other.z;
        return this;
    }

    public ClothVec add(float dx, float dy, float dz) {
        x += dx;
        y += dy;
        z += dz;
        return this;
    }

    public ClothVec subtract(ClothVec other) {
        x = x - other.x;
        y = y - other.y;
        z = z - other.z;
        return this;
    }

    public ClothVec div(float value) {
        x /= value;
        y /= value;
        z /= value;
        return this;
    }

    public ClothVec mul(float value) {
        x *= value;
        y *= value;
        z *= value;
        return this;
    }

    /** Scales the vector to length 1; a vector too short to have a direction becomes zero. */
    public ClothVec normalize() {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-4F) {
            x = 0;
            y = 0;
            z = 0;
        } else {
            x /= length;
            y /= length;
            z /= length;
        }
        return this;
    }

    public ClothVec rotateDegrees(float degrees) {
        float oldX = x;
        float oldY = y;
        float angle = (float) Math.toRadians(degrees);
        x = (float) (Math.cos(angle) * oldX - Math.sin(angle) * oldY);
        y = (float) (Math.sin(angle) * oldX + Math.cos(angle) * oldY);
        return this;
    }

    public float sqrMagnitude() {
        return x * x + y * y + z * z;
    }
}
