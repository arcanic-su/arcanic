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
package net.arcanic.visuals.fx;

import net.minecraft.world.entity.Entity;

/** Fields our mixins add to vanilla classes. */
public final class Ducks {
    private Ducks() {}
    /** On EntityRenderState: the entity it was made from (render states do not keep it). */
    public interface EntityLink {
        Entity arcanicvisuals$entity();

        void arcanicvisuals$setEntity(Entity e);
    }
    /** On ItemStackRenderState: this item is the local player's painted weapon (third person). */
    public interface PaintFlag {
        boolean arcanicvisuals$paint();

        void arcanicvisuals$setPaint(boolean on);
    }

    public static Entity entityOf(Object state) {
        return state instanceof EntityLink l ? l.arcanicvisuals$entity() : null;
    }
}
