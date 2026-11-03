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

import net.arcanic.visuals.arc.MeleeAnimator;
import net.arcanic.visuals.core.Modules;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Knife attack animations: left click plays the light attack, right click the heavy one. */
@Mixin(Minecraft.class)
public abstract class MeleeAttackMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void arcanicvisuals$primary(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.KNIVES.handlesAnyHand()) MeleeAnimator.getInstance().onAttack(true);
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void arcanicvisuals$secondary(CallbackInfo ci) {
        // raising a shield (or eating) is not a heavy attack, whichever hand the item is in
        var player = Minecraft.getInstance().player;
        if (player != null && player.isUsingItem()) return;
        if (Modules.KNIVES.handlesAnyHand()) MeleeAnimator.getInstance().onAttack(false);
    }
}
