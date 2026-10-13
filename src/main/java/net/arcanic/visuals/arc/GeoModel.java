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

/** A cube of a model: its size, six faces, pivot, rotation, inflation and mirroring. */
public class GeoModel {
    public GeoVec size;
    public GeoQuad[] faces = new GeoQuad[6];
    public GeoVec pivot = new GeoVec(0, 0, 0);
    public GeoVec rotation = new GeoVec(0, 0, 0);
    public float inflate = 0;
    public boolean mirror = false;

    public GeoModel(float width, float height, float depth) {
        this.size = new GeoVec(width, height, depth);
    }
}
