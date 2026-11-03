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

final class IslandIntro {
    private boolean pending;
    private long started = -1L;

    void queue() {
        this.pending = true;
        this.started = -1L;
    }

    void reset() {
        this.pending = false;
        this.started = -1L;
    }

    boolean waiting() {
        return this.pending;
    }

    float seconds(long now, boolean visible, boolean animated) {
        if (!animated) {
            this.reset();
            return 10.0F;
        } else {
            if (this.pending && visible) {
                this.pending = false;
                this.started = now;
            }

            if (this.pending) {
                return 0.0F;
            } else {
                return this.started < 0L ? 10.0F : Math.max(0.0F, (float) (now - this.started) / 1.0E9F);
            }
        }
    }

    static float stage(float seconds, float delay, float duration) {
        float t = Math.max(0.0F, Math.min(1.0F, (seconds - delay) / duration));
        return t * t * (3.0F - 2.0F * t);
    }
}
