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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.NoRender;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The warden's darkness pulse on the light map. */
@Mixin(LightTexture.class)
public abstract class NoRenderLightMixin {
    @Inject(method = "calculateDarknessScale", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$darkness(
            LivingEntity entity, float darkness, float partial, CallbackInfoReturnable<Float> cir) {
        if (NoRender.hides(Modules.NO_RENDER.darkness)) cir.setReturnValue(0f);
    }

    @ModifyExpressionValue(
            method = "updateLightTexture",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float arcanicvisuals$darknessBlend(float original) {
        return NoRender.hides(Modules.NO_RENDER.darkness) ? 0 : original;
    }
}
