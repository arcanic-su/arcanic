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

/** One reveal per connection; its clock starts at the first visible world frame. */
final class IslandIntro {
    private boolean pending;
    private long started = -1;

    void queue() {
        pending = true;
        started = -1;
    }

    void reset() {
        pending = false;
        started = -1;
    }

    float seconds(long now, boolean visible, boolean animated) {
        if (!animated) {
            reset();
            return 10;
        }
        if (pending && visible) {
            pending = false;
            started = now;
        }
        if (pending) return 0;
        return started < 0 ? 10 : Math.max(0, (now - started) / 1_000_000_000f);
    }

    static float stage(float seconds, float delay, float duration) {
        float t = Math.max(0, Math.min(1, (seconds - delay) / duration));
        return t * t * (3 - 2 * t);
    }
}
