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
import net.arcanic.visuals.fx.ShaderSky;
import net.arcanic.visuals.fx.ThemeSky;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SkyRenderer.class})
public abstract class SkyRendererMixin {
    @ModifyVariable(
            method = {"renderSkyDisc"},
            at = {@At("HEAD")},
            argsOnly = true)
    private int arcanicvisuals$ambienceSky(int colour) {
        return Modules.AMBIENCE.sky(colour);
    }

    @Redirect(
            method = {"renderStars"},
            at = {@At(value = "NEW", target = "(FFFF)Lorg/joml/Vector4f;")})
    private Vector4f arcanicvisuals$ambienceStars(float r, float g, float b, float a) {
        return Modules.AMBIENCE.stars(r, g, b, a);
    }

    @Inject(
            method = {"renderSkyDisc"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$sky(int colour, CallbackInfo ci) {
        if (Modules.SKY_SHADER.replaces()) {
            ShaderSky.render(Modules.SKY_SHADER);
            ci.cancel();
        } else if (Modules.CUSTOM_FOG.hidesSky()) {
            ci.cancel();
        }
    }

    @Inject(
            method = {"renderSkyDisc"},
            at = {@At("RETURN")})
    private void arcanicvisuals$themeSky(int colour, CallbackInfo ci) {
        if (Modules.SKY_SHADER.overlays()) {
            ThemeSky.render(Modules.SKY_SHADER);
        }
    }

    @Inject(
            method = {"renderSunriseAndSunset"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$sunrise(CallbackInfo ci) {
        if (hidden()) {
            ci.cancel();
        }
    }

    @Inject(
            method = {"renderDarkDisc"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$dark(CallbackInfo ci) {
        if (hidden()) {
            ci.cancel();
        }
    }

    @Inject(
            method = {"renderSunMoonAndStars"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$sun(CallbackInfo ci) {
        if (hidden()) {
            ci.cancel();
        }
    }

    private static boolean hidden() {
        return Modules.SKY_SHADER.replaces() || Modules.CUSTOM_FOG.hidesSky();
    }
}
