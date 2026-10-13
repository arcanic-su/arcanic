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

import java.util.ArrayList;
import java.util.List;

/** A bone of a model: its pivot, its pose (rotation, offset, scale), its cubes and its child bones. */
public class GeoPart {
    public GeoPart parent;
    public String name;
    public boolean hidden;
    public float pivotX;
    public float pivotY;
    public float pivotZ;
    private float rotX;
    private float rotY;
    private float rotZ;
    private float offsetX;
    private float offsetY;
    private float offsetZ;
    public List<GeoPart> children = new ArrayList<>();
    public List<GeoModel> cubes = new ArrayList<>();
    private float scaleX = 1;
    private float scaleY = 1;
    private float scaleZ = 1;

    public GeoPart(String name) {
        this.name = name;
    }

    public float getRotX() {
        return rotX;
    }

    public float getRotY() {
        return rotY;
    }

    public float getRotZ() {
        return rotZ;
    }

    public void setRotX(float value) {
        rotX = value;
    }

    public void setRotY(float value) {
        rotY = value;
    }

    public void setRotZ(float value) {
        rotZ = value;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public float getOffsetZ() {
        return offsetZ;
    }

    public void setOffsetX(float value) {
        offsetX = value;
    }

    public void setOffsetY(float value) {
        offsetY = value;
    }

    public void setOffsetZ(float value) {
        offsetZ = value;
    }

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public float getScaleZ() {
        return scaleZ;
    }

    public void setScaleX(float value) {
        scaleX = value;
    }

    public void setScaleY(float value) {
        scaleY = value;
    }

    public void setScaleZ(float value) {
        scaleZ = value;
    }

    public float getPivotX() {
        return pivotX;
    }

    public float getPivotY() {
        return pivotY;
    }

    public float getPivotZ() {
        return pivotZ;
    }

    public String getName() {
        return name;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean value) {
        hidden = value;
    }
}
