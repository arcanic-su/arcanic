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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Plays the sounds of melee clips, by their name in the mod's namespace. */
public final class MeleeSoundIds {
    public static SoundInstance play(String key) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        var event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("arcanic", key));
        var sound = SimpleSoundInstance.forUI(event, 1, 0.65f);
        mc.getSoundManager().play(sound);
        return sound;
    }

    public static void stop(SoundInstance sound) {
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
    }

    public static boolean isActive(SoundInstance sound) {
        return sound != null && Minecraft.getInstance().getSoundManager().isActive(sound);
    }
}
