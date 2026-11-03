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
package net.arcanic.visuals.hud;

final class Tween {
    static final Easing OVERSHOOT = bezier(0.45, 1.45, 0.49, 1.15);
    static final Easing SOFT = bezier(0.27, 1.09, 0.49, 1.06);
    static final Easing BACK_IN = bezier(0.62, -0.16, 0.8, 0.37);
    static final Easing IN_OUT = bezier(0.42, 0.0, 0.58, 1.0);
    static final Easing SINE = (t) -> (float) (-(Math.cos(Math.PI * (double) t) - 1.0) / 2.0);
    private final long duration;
    private Easing easing;
    private float value;
    private float from;
    private float target;
    private long start;

    Tween(long duration, float value, Easing easing) {
        this.duration = duration;
        this.easing = easing;
        this.value = this.from = this.target = value;
    }

    void easing(Easing e) {
        this.easing = e;
    }

    float to(boolean on) {
        return this.to(on ? 1.0F : 0.0F);
    }

    float to(float t) {
        long now = System.currentTimeMillis();
        if (t != this.target) {
            this.from = this.value;
            this.target = t;
            this.start = now;
        }

        long passed = now - this.start;
        if (passed >= this.duration) {
            this.value = this.target;
            return this.value;
        } else {
            this.value =
                    this.from + (this.target - this.from) * this.easing.ease((float) passed / (float) this.duration);
            return this.value;
        }
    }

    void set(float v) {
        this.value = this.from = this.target = v;
    }

    float get() {
        return this.value;
    }

    static Easing bezier(double x1, double y1, double x2, double y2) {
        float ax = (float) x1;
        float ay = (float) y1;
        float bx = (float) x2;
        float by = (float) y2;
        return (t) -> {
            if (t <= 0.0F) {
                return 0.0F;
            } else if (t >= 1.0F) {
                return 1.0F;
            } else {
                float u = t;

                for (int i = 0; i < 8; i++) {
                    float x = curve(u, ax, bx) - t;
                    if (Math.abs(x) < 1.0E-5F) {
                        break;
                    }

                    float d = slope(u, ax, bx);
                    if (Math.abs(d) < 1.0E-6F) {
                        break;
                    }

                    u -= x / d;
                }

                u = Math.max(0.0F, Math.min(1.0F, u));
                return curve(u, ay, by);
            }
        };
    }

    private static float curve(float t, float p1, float p2) {
        float m = 1.0F - t;
        return 3.0F * m * m * t * p1 + 3.0F * m * t * t * p2 + t * t * t;
    }

    private static float slope(float t, float p1, float p2) {
        float m = 1.0F - t;
        return 3.0F * m * m * p1 + 6.0F * m * t * (p2 - p1) + 3.0F * t * t * (1.0F - p2);
    }

    interface Easing {
        float ease(float t);
    }
}
