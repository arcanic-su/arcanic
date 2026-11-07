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

import net.arcanic.visuals.fx.Ambience;
import net.arcanic.visuals.fx.WorldPlus;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LightTexture.class})
public abstract class LightTextureMixin {
    @ModifyArg(
            require = 0,
            method = {"updateLightTexture"},
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                        ordinal = 3)
            })
    private float arcanicvisuals$fullbright(float nightVision) {
        return WorldPlus.Fullbright.nightVision(nightVision);
    }

    @Inject(
            method = {"updateLightTexture"},
            at = {@At("TAIL")})
    private void arcanicvisuals$night(float partialTick, CallbackInfo ci) {
        Ambience.tintLightmap(((LightTexture) (Object) this).getTextureView());
    }
}
