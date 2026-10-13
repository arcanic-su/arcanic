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

/** One animation of a melee model: its length, a track per bone and the sounds it plays on the way. */
public final class MeleeClip {
    public static final int CHANNEL_STRIDE = 3;
    public static final int ROTATION_OFFSET = 0;
    public static final int POSITION_OFFSET = 3;
    public static final int SCALE_OFFSET = 6;
    public static final int BONE_STRIDE = 9;
    private final String name;
    private final float length;
    private final MeleeBoneTrack[] tracks;
    private final float[] soundTimes;
    private final String[] soundNames;

    MeleeClip(String name, float length, MeleeBoneTrack[] tracks, float[] soundTimes, String[] soundNames) {
        this.name = name;
        this.length = length;
        this.tracks = tracks;
        this.soundTimes = soundTimes;
        this.soundNames = soundNames;
    }

    public String getName() {
        return name;
    }

    public float getLength() {
        return length;
    }

    public MeleeBoneTrack[] getTracks() {
        return tracks;
    }

    public int soundCount() {
        return soundTimes.length;
    }

    public float soundTime(int index) {
        return soundTimes[index];
    }

    public String soundName(int index) {
        return soundNames[index];
    }
}
