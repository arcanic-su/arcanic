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
import net.arcanic.visuals.fx.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws wither hearts as normal ones. Gui.HeartType is private, so it is matched by enum name. */
@Mixin(targets = "net.minecraft.client.gui.Gui$HeartType")
public abstract class NoRenderHeartMixin {
    @Unique
    private static Object arcanicvisuals$normal;

    @Inject(method = "forPlayer", at = @At("RETURN"), cancellable = true)
    private static void arcanicvisuals$witherHeart(CallbackInfoReturnable<Object> cir) {
        if (!NoRender.hides(Modules.NO_RENDER.witherHearts)
                || !(cir.getReturnValue() instanceof Enum<?> type)
                || !type.name().equals("WITHERED")) return;
        if (arcanicvisuals$normal == null) {
            for (Object c : type.getDeclaringClass().getEnumConstants()) {
                if (((Enum<?>) c).name().equals("NORMAL")) arcanicvisuals$normal = c;
            }
        }
        if (arcanicvisuals$normal != null) cir.setReturnValue(arcanicvisuals$normal);
    }
}
