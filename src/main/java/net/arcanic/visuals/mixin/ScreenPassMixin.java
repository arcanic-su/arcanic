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

import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.impact.ImpactEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GameRenderer.class})
public abstract class ScreenPassMixin {
    @Inject(
            method = {"renderLevel"},
            at = {@At("RETURN")})
    private void arcanicvisuals$screenPass(DeltaTracker delta, CallbackInfo ci) {
        ImpactEffects.frameDrawn();
    }

    @Inject(
            method = {"displayItemActivation"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$hideVanillaTotem(ItemStack stack, CallbackInfo ci) {
        if (stack.is(Items.TOTEM_OF_UNDYING) && Modules.TOTEM.hides(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}
