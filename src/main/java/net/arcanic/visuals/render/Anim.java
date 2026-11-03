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

public final class Anim {
    public static float speedMul = 1.0F;
    private final Map<String, Run> runs = new HashMap<>();
    private long last = System.nanoTime();
    private float dt = 0.016666668F;
    public static boolean snap;

    public void frame() {
        long now = System.nanoTime();
        this.dt = Math.min(0.1F, (float) (now - this.last) / 1.0E9F);
        this.last = now;
    }

    public float to(String key, float target, float speed) {
        Run r = (Run) this.runs.get(key);
        long now = System.nanoTime();
        if (r == null) {
            r = new Run();
            r.from = r.target = r.value = target;
            this.runs.put(key, r);
            return target;
        } else if (Theme.animations && !snap) {
            if (Math.abs(target - r.target) > 1.0E-6F) {
                r.from = r.value;
                r.target = target;
                r.start = now;
                r.seconds = Math.max(0.06F, Math.min(0.8F, 2.0F / Math.max(0.1F, speed * speedMul)));
            }

            if (r.value != r.target) {
                float t = Math.min(1.0F, (float) (now - r.start) / 1.0E9F / r.seconds);
                float e = 1.0F - (1.0F - t) * (1.0F - t);
                r.value = t >= 1.0F ? r.target : r.from + (r.target - r.from) * e;
            }

            return r.value;
        } else {
            r.from = r.target = r.value = target;
            return target;
        }
    }

    public float get(String key, float def) {
        Run r = (Run) this.runs.get(key);
        return r == null ? def : r.value;
    }

    public void set(String key, float v) {
        Run r = (Run) this.runs.computeIfAbsent(key, (k) -> new Run());
        r.from = r.target = r.value = v;
    }

    public void clear(String prefix) {
        this.runs.keySet().removeIf((k) -> k.startsWith(prefix));
    }

    public float factor(float speed) {
        return Theme.animations ? 1.0F - (float) Math.exp((double) (-this.dt * speed * speedMul)) : 1.0F;
    }

    public static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow((double) (t - 1.0F), 3.0) + c1 * (float) Math.pow((double) (t - 1.0F), 2.0);
    }

    public static float easeOutCubic(float t) {
        return 1.0F - (float) Math.pow((double) (1.0F - t), 3.0);
    }

    public static float easeOutExpo(float t) {
        return t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, (double) (-10.0F * Math.max(0.0F, t)));
    }

    private static final class Run {
        float from;
        float target;
        float value;
        long start;
        float seconds;
    }
}
