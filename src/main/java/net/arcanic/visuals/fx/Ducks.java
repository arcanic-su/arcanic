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

public final class Ducks {
    private Ducks() {}

    public static Entity entityOf(Object state) {
        Entity var10000;
        if (state instanceof EntityLink l) {
            var10000 = l.arcanicvisuals$entity();
        } else {
            var10000 = null;
        }

        return var10000;
    }

    public interface EntityLink {
        Entity arcanicvisuals$entity();

        void arcanicvisuals$setEntity(Entity var1);
    }
}
