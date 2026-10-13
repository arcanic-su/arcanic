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
package net.arcanic.visuals.core;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** The menu's own sounds (assets/arcanicvisuals/sounds/ui/*.ogg). */
public final class UiSound {
    private UiSound() {}

    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
    private static final Map<String, Long> LAST = new HashMap<>();
    public static long lastAny;

    public static void play(String name) {
        play(name, 1, 1);
    }

    public static void play(String name, float pitch, float volume) {
        lastAny++;
        MenuPrefs p = Modules.PREFS;
        if (!p.sounds.on() || p.volume.get() <= 0) return;
        if ((name.equals("hover") && !p.hoverSounds.on()) || (name.equals("tick") && !p.sliderSounds.on())) return;
        long now = System.currentTimeMillis();
        long gap =
                switch (name) {
                    case "hover" -> 45;
                    case "tick" -> 26;
                    case "type" -> 18;
                    default -> 35;
                };
        Long last = LAST.get(name);
        if (last != null && now - last < gap) return;
        LAST.put(name, now);
        SoundEvent ev = EVENTS.computeIfAbsent(
                name,
                n -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("arcanicvisuals", "ui." + n)));
        float base =
                switch (name) {
                    case "hover" -> .35f;
                    case "tick" -> .5f;
                    case "type" -> .45f;
                    case "open", "close" -> .8f;
                    default -> .7f;
                };
        float vol = (float) (p.volume.get() / 100.0) * base * volume;
        try {
            Minecraft.getInstance()
                    .getSoundManager()
                    .play(SimpleSoundInstance.forUI(ev, Math.max(.5f, Math.min(2f, pitch)), vol));
        } catch (RuntimeException ignored) {
        }
    }
}
