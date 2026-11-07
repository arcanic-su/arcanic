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

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The held items and how far each is raised, swapped for a moment by the knife studio's picture. */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererAccess {
    @Accessor("mainHandItem")
    ItemStack arcanicvisuals$mainHandItem();

    @Accessor("mainHandItem")
    void arcanicvisuals$setMainHandItem(ItemStack stack);

    @Accessor("offHandItem")
    ItemStack arcanicvisuals$offHandItem();

    @Accessor("offHandItem")
    void arcanicvisuals$setOffHandItem(ItemStack stack);

    @Accessor("mainHandHeight")
    float arcanicvisuals$mainHandHeight();

    @Accessor("mainHandHeight")
    void arcanicvisuals$setMainHandHeight(float height);

    @Accessor("oMainHandHeight")
    float arcanicvisuals$oMainHandHeight();

    @Accessor("oMainHandHeight")
    void arcanicvisuals$setOMainHandHeight(float height);

    @Accessor("offHandHeight")
    float arcanicvisuals$offHandHeight();

    @Accessor("offHandHeight")
    void arcanicvisuals$setOffHandHeight(float height);

    @Accessor("oOffHandHeight")
    float arcanicvisuals$oOffHandHeight();

    @Accessor("oOffHandHeight")
    void arcanicvisuals$setOOffHandHeight(float height);
}
