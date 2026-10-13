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

/** The keyframes of one bone in a clip: rotation, position and scale, each optional. */
public final class MeleeBoneTrack {
    private final int target;
    private final MeleeKeyframes rotation;
    private final MeleeKeyframes position;
    private final MeleeKeyframes scale;

    MeleeBoneTrack(int target, MeleeKeyframes rotation, MeleeKeyframes position, MeleeKeyframes scale) {
        this.target = target;
        this.rotation = rotation;
        this.position = position;
        this.scale = scale;
    }

    public int getTarget() {
        return target;
    }

    public void sample(float time, float[] out) {
        int base = target * MeleeClip.BONE_STRIDE;
        if (rotation != null) rotation.sample(time, out, base + MeleeClip.ROTATION_OFFSET);
        if (position != null) position.sample(time, out, base + MeleeClip.POSITION_OFFSET);
        if (scale != null) scale.sample(time, out, base + MeleeClip.SCALE_OFFSET);
    }
}
