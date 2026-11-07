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

/**
 * Trust mode: the launcher's "Trust mode" button starts the game with -Darcanic.trust=true. Then the client is
 * visuals only: every module that clicks, swaps or moves for the player is off and hidden, and Lua does not run.
 * What the player chose for those modules stays in the config for a normal start.
 */
public final class Trust {
    private Trust() {}

    public static final boolean ON = Boolean.getBoolean("arcanic.trust");

    /** Modules that act for the player: inventory clicks, item swaps, throws, sprint. */
    private static final Set<String> BLOCKED =
            Set.of("elytra_swap", "fast_swap", "click_pearl", "auto_sprint", "item_scroller", "kits", "chest_sorter");

    public static boolean blocks(Module m) {
        return ON && BLOCKED.contains(m.id);
    }
}
