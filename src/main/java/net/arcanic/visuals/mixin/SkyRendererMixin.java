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

import com.mojang.blaze3d.vertex.PoseStack;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.CustomSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Atmosphere: tints the sky dome colour. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$sky(int colour, CallbackInfo ci) {
        if (CustomSky.render()) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$sunrise(PoseStack pose, float angle, int colour, CallbackInfo ci) {
        if (Modules.WORLD.skyActive()) ci.cancel();
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$dark(CallbackInfo ci) {
        if (Modules.WORLD.skyActive()) ci.cancel();
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$sun(CallbackInfo ci) {
        if (Modules.WORLD.skyActive() && !Modules.WORLD.sunMoon.on()) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void arcanicvisuals$tintSky(
            ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        int c = state.skyColor;
        float[] t = Modules.ATMOS.apply((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, true);
        if (t != null)
            state.skyColor = (c & 0xFF000000)
                    | Math.round(t[0] * 255) << 16
                    | Math.round(t[1] * 255) << 8
                    | Math.round(t[2] * 255);
    }
}
