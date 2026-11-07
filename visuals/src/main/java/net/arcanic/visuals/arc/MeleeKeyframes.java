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

/** The keyframes of one channel (rotation, position or scale) of a bone: times and three values per key. */
public final class MeleeKeyframes {
    private final float[] times;
    private final float[] pre;
    private final float[] post;
    private final boolean[] smooth;

    MeleeKeyframes(float[] times, float[] pre, float[] post, boolean[] smooth) {
        this.times = times;
        this.pre = pre;
        this.post = post;
        this.smooth = smooth;
    }

    public float lastTime() {
        return times[times.length - 1];
    }

    /** Writes the three values at {@code time} into {@code out} from {@code offset}. */
    public void sample(float time, float[] out, int offset) {
        int last = times.length - 1;
        if (time <= times[0]) {
            copy(pre, 0, out, offset);
            return;
        }
        if (time >= times[last]) {
            copy(post, last, out, offset);
            return;
        }
        int key = indexOf(time, last);
        float span = times[key + 1] - times[key];
        if (span <= 0) {
            copy(post, key, out, offset);
            return;
        }
        float t = (time - times[key]) / span;
        if (smooth[key]) spline(key, t, out, offset, last);
        else interpolate(key, t, out, offset);
    }

    private int indexOf(float time, int last) {
        int key = last;
        while (key > 0 && times[key] > time) key--;
        return key;
    }

    private void interpolate(int key, float t, float[] out, int offset) {
        int from = key * 3;
        int to = from + 3;
        for (int i = 0; i < 3; i++) {
            float a = post[from + i];
            out[offset + i] = a + (pre[to + i] - a) * t;
        }
    }

    private void spline(int key, float t, float[] out, int offset, int last) {
        int p0 = Math.max(key - 1, 0) * 3;
        int p1 = key * 3;
        int p2 = p1 + 3;
        int p3 = Math.min(key + 2, last) * 3;
        float t2 = t * t;
        float t3 = t2 * t;
        for (int i = 0; i < 3; i++) {
            float v0 = post[p0 + i];
            float v1 = post[p1 + i];
            float v2 = pre[p2 + i];
            float v3 = pre[p3 + i];
            out[offset + i] = 0.5F
                    * (2.0F * v1
                            + (v2 - v0) * t
                            + (2.0F * v0 - 5.0F * v1 + 4.0F * v2 - v3) * t2
                            + (-v0 + 3.0F * v1 - 3.0F * v2 + v3) * t3);
        }
    }

    private static void copy(float[] values, int key, float[] out, int offset) {
        int i = key * 3;
        out[offset] = values[i];
        out[offset + 1] = values[i + 1];
        out[offset + 2] = values[i + 2];
    }
}
