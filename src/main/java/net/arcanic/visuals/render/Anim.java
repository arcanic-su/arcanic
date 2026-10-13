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
package net.arcanic.visuals.render;

import java.util.HashMap;
import java.util.Map;
import net.arcanic.visuals.core.Theme;

/** Frame-rate independent springs keyed by name: hover glows, toggles, expanding cards, page fades. */
public final class Anim {
    public static float speedMul = 1;

    private final Map<String, Float> values = new HashMap<>();
    private long last = System.nanoTime();
    private float dt = 1 / 60f;

    public void frame() {
        long now = System.nanoTime();
        dt = Math.min(.1f, (now - last) / 1e9f);
        last = now;
    }

    public float to(String key, float target, float speed) {
        float v = values.getOrDefault(key, target);
        if (!Theme.animations) v = target;
        else v += (target - v) * (1 - (float) Math.exp(-dt * speed * speedMul));
        if (Math.abs(target - v) < .0015f) v = target;
        values.put(key, v);
        return v;
    }

    public float get(String key, float def) {
        return values.getOrDefault(key, def);
    }

    public void set(String key, float v) {
        values.put(key, v);
    }

    public void clear(String prefix) {
        values.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public float factor(float speed) {
        return Theme.animations ? 1 - (float) Math.exp(-dt * speed * speedMul) : 1;
    }

    public static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    public static float easeOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }
}
