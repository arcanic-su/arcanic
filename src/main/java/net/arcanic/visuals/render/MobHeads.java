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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * Our own mob heads (drawn by tools/make_faces.py): one atlas per even pixel size from 16 to 80, so a head is always
 * drawn 1:1, never scaled.
 */
public final class MobHeads {
    private MobHeads() {}

    private static final String[] NAMES = {
        "zombie",
        "husk",
        "drowned",
        "zombie_villager",
        "skeleton",
        "stray",
        "bogged",
        "wither_skeleton",
        "creeper",
        "spider",
        "cave_spider",
        "enderman",
        "slime",
        "magma_cube",
        "blaze",
        "villager",
        "wandering_trader",
        "pillager",
        "vindicator",
        "evoker",
        "illusioner",
        "witch",
        "iron_golem",
        "piglin",
        "piglin_brute",
        "zombified_piglin",
        "pig",
        "cow",
        "mooshroom",
        "sheep",
        "chicken",
        "wolf",
        "phantom",
        "warden",
        "generic"
    };
    private static final int COLS = 8, ROWS = 5, MIN = 16, MAX = 80;
    private static final Map<String, Integer> INDEX = new HashMap<>();

    static {
        for (int i = 0; i < NAMES.length; i++) INDEX.put(NAMES[i], i);
    }

    private static final Map<Integer, Identifier> ATLAS = new HashMap<>();

    private static int index(EntityType<?> type) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        return INDEX.getOrDefault(path, INDEX.get("generic"));
    }

    public static void draw(GuiGraphics g, EntityType<?> type, int x, int y, int size, int argb) {
        int s = Math.max(MIN, Math.min(MAX, size / 2 * 2));
        Identifier tex = ATLAS.computeIfAbsent(
                s,
                k -> Identifier.fromNamespaceAndPath(
                        "arcanicvisuals", "textures/gui/mob_heads/mob_heads_" + k + ".png"));
        int i = index(type), u = (i % COLS) * s, v = (i / COLS) * s;
        int ox = x + (size - s) / 2, oy = y + (size - s) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, ox, oy, u, v, s, s, s, s, COLS * s, ROWS * s, argb);
    }
}
