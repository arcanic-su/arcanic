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

import java.util.Optional;
import net.arcanic.visuals.helper.ShulkerPreview;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ItemStack.class})
public abstract class ShulkerTooltipMixin {
    @Inject(
            require = 0,
            method = {"getTooltipImage"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$shulker(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        try {
            Optional<TooltipComponent> p = ShulkerPreview.of((ItemStack) (Object) this);
            if (p.isPresent()) {
                cir.setReturnValue(p);
            }
        } catch (RuntimeException ignored) {
        }
    }
}
