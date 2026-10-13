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

import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Per-bone matrices reused from frame to frame, grown when a model has more bones than before. */
final class MeleeBuffer {
    Matrix4f[] world = new Matrix4f[0];
    Matrix4f[] combined = new Matrix4f[0];
    Matrix3f[] normals = new Matrix3f[0];
    boolean[] visible = new boolean[0];

    MeleeBuffer() {}

    void ensureCapacity(int bones) {
        if (world.length >= bones) return;
        world = new Matrix4f[bones];
        combined = new Matrix4f[bones];
        normals = new Matrix3f[bones];
        visible = new boolean[bones];
        for (int i = 0; i < bones; i++) {
            world[i] = new Matrix4f();
            combined[i] = new Matrix4f();
            normals[i] = new Matrix3f();
        }
    }
}
