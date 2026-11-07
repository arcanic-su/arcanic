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

/** A player's cape simulation and what it remembers between ticks. */
final class ClothLink {
    final ClothSimulation simulation = new ClothSimulation();
    final ClothVec gravityDirection = new ClothVec(0.0F, -1.0F, 0.0F);
    final ClothVec movementChange = new ClothVec(0.0F, 0.0F, 0.0F);
    double prevX;
    double prevY;
    double prevZ;
    boolean hasPrev;

    ClothLink() {}
}
