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

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** Sample distance once per game tick, then sample the knife's run clip at every render frame. */
public final class HandMovement {
    private static LocalPlayer owner;
    private static float previous, current;

    private HandMovement() {}

    public static void tick(Minecraft mc) {
        if (mc.player != owner) {
            owner = mc.player;
            previous = current = owner == null ? 0 : owner.moveDist;
        } else if (owner != null) {
            previous = current;
            current = owner.moveDist;
            if (current < previous || current - previous > 4) previous = current;
        }
    }

    public static float distance() {
        var mc = Minecraft.getInstance();
        if (owner != mc.player) return mc.player == null ? 0 : mc.player.moveDist;
        return interpolate(previous, current, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    public static float interpolate(float before, float after, float partial) {
        return Mth.lerp(Mth.clamp(partial, 0, 1), before, after);
    }
}
