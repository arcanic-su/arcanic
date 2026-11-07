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
package net.arcanic.visuals.mixin;

import net.arcanic.visuals.fx.WorldPlus;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({ItemEntityRenderState.class})
public abstract class ItemStateMixin implements WorldPlus.ItemState {
    @Unique
    private boolean arcanicvisuals$onGround;

    @Unique
    private int arcanicvisuals$entity;

    public boolean arcanicvisuals$ground() {
        return this.arcanicvisuals$onGround;
    }

    public int arcanicvisuals$id() {
        return this.arcanicvisuals$entity;
    }

    public void arcanicvisuals$set(boolean ground, int id) {
        this.arcanicvisuals$onGround = ground;
        this.arcanicvisuals$entity = id;
    }
}
