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

/** One corner of a model face: where it is and where it samples the texture. */
public class GeoVertex {
    public GeoVec position;
    public float u;
    public float v;

    public GeoVertex(float x, float y, float z, float u, float v) {
        this(new GeoVec(x, y, z), u, v);
    }

    public GeoVertex(GeoVec position, float u, float v) {
        this.position = position;
        this.u = u;
        this.v = v;
    }
}
