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

import java.util.Set;
import net.arcanic.visuals.hud.Toasts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Trust mode: PvP helpers are off until the game restarts. Not saved, no way to turn it off in game. Blocked modules
 * keep their own switch, so after a restart everything is as before.
 */
public final class Trust {
    private Trust() {}

    public static final Set<String> BLOCKED = Set.of(
            "fast_swap",
            "elytra_swap",
            "click_pearl",
            "auto_sprint",
            "item_scroller",
            "kits",
            "combohelper",
            "hit_range",
            "trajectories",
            "see_invisible",
            "fullbright",
            "free_look",
            "hitboxes",
            "outline",
            "totem_tracker",
            "consume_esp");

    private static volatile boolean on;
    private static long since;

    public static boolean on() {
        return on;
    }

    public static long since() {
        return since;
    }

    public static boolean blockable(Module m) {
        return m != null && BLOCKED.contains(m.id);
    }

    public static boolean blocks(Module m) {
        return on && blockable(m);
    }

    public static void enable() {
        if (on) return;
        for (Module m : Modules.ALL) {
            if (blockable(m) && m.switchedOn()) {
                try {
                    m.onDisable(); // camera, gamma etc.
                } catch (RuntimeException e) {
                    System.err.println("[ArcanicVisuals] trust: " + m.id + ": " + e);
                }
            }
        }
        since = System.currentTimeMillis();
        on = true;
        UiSound.play("charge");
        Toasts.setting(Lang.tr("Trust mode включён до перезапуска игры"), true);
        say(Lang.tr("Trust mode включён: функции, которые помогают в PvP, заблокированы до перезапуска игры"));
    }

    /** m == null: a click on the trust switch itself. */
    public static void deny(Module m) {
        UiSound.play("reset", 0.8F, 0.8F);
        Toasts.setting(
                m == null
                        ? Lang.tr("Trust mode выключится только после перезапуска игры")
                        : Lang.tr("Заблокировано Trust mode до перезапуска игры"),
                false);
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GREEN), false);
        }
    }
}
