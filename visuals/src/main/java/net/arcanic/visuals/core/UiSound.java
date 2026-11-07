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
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class UiSound {
    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
    private static final Map<String, Long> LAST = new HashMap<>();
    private static final Random JITTER = new Random();
    public static long lastAny;

    private UiSound() {}

    public static void play(String name) {
        play(name, 1.0F, 1.0F);
    }

    public static void play(String name, float pitch, float volume) {
        ++lastAny;
        MenuPrefs p = Modules.PREFS;
        if (p.sounds.on() && !((Double) p.volume.get() <= 0.0)) {
            if ((!name.endsWith("hover") || p.hoverSounds.on()) && (!name.equals("tick") || p.sliderSounds.on())) {
                long now = System.currentTimeMillis();
                long gap =
                        switch (name) {
                            case "hover" -> 45L;
                            case "tick" -> 26L;
                            case "type" -> 18L;
                            default -> 35L;
                        };
                Long last = LAST.get(name);
                if (last == null || now - last >= gap) {
                    LAST.put(name, now);
                    SoundEvent ev = (SoundEvent) EVENTS.computeIfAbsent(
                            name,
                            (n) -> SoundEvent.createVariableRangeEvent(
                                    Identifier.fromNamespaceAndPath("arcanicvisuals", "ui." + n)));
                    float f;
                    switch (name) {
                        case "hover":
                            f = 0.35F;
                            break;
                        case "tick":
                            f = 0.5F;
                            break;
                        case "type":
                            f = 0.45F;
                            break;
                        case "open":
                        case "close":
                        case "unlock":
                            f = 0.8F;
                            break;
                        default:
                            f = 0.7F;
                    }

                    float base = f;
                    float vol = (float) ((Double) p.volume.get() / 100.0) * base * volume;
                    if (name.equals("click")
                            || name.equals("hover")
                            || name.equals("tick")
                            || name.equals("type")
                            || name.equals("tab")) {
                        pitch *= 1.0F + (JITTER.nextFloat() - 0.5F) * 0.07F;
                    }

                    try {
                        Minecraft.getInstance()
                                .getSoundManager()
                                .play(SimpleSoundInstance.forUI(ev, Math.max(0.5F, Math.min(2.0F, pitch)), vol));
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
    }
}
