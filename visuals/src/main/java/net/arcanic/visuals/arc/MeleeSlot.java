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

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What a melee skin takes the place of: a knife is drawn for a sword, a gun for a bow. */
public enum MeleeSlot {
    KNIFE,
    BOW;

    static MeleeSlot named(String name) {
        return "bow".equals(name) ? BOW : KNIFE;
    }

    public static MeleeSlot of(ItemStack stack) {
        for (MeleeSlot slot : values()) {
            if (slot.holds(stack)) return slot;
        }
        return null;
    }

    public boolean holds(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return this == BOW ? stack.is(Items.BOW) : stack.is(ItemTags.SWORDS);
    }

    /** A gun is held in both hands, so whatever the other hand carries stays out of the picture. */
    public boolean twoHanded() {
        return this == BOW;
    }

    /** A gun breathes in the hands while it is held still; a knife keeps its own still pose. */
    boolean idles() {
        return this == BOW;
    }
}
