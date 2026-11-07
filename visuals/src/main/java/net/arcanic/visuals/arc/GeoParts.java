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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A parsed model: its root bones and the size of its texture. */
public class GeoParts {
    public List<GeoPart> roots = new ArrayList<>();
    public int textureWidth = 64;
    public int textureHeight = 64;

    public Optional<GeoPart> findPart(String name) {
        for (GeoPart root : roots) {
            GeoPart found = findPart(name, root);
            if (found != null) return Optional.of(found);
        }
        return Optional.empty();
    }

    private GeoPart findPart(String name, GeoPart part) {
        if (part.name.equals(name)) return part;
        for (GeoPart child : part.children) {
            GeoPart found = findPart(name, child);
            if (found != null) return found;
        }
        return null;
    }
}
