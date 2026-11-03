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
import net.arcanic.visuals.fx.FreeLook;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Camera.class})
public abstract class FreeLookCameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(
            method = {"setup"},
            at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F")})
    private void arcanicvisuals$freeLook(
            Level level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        FreeLook fl = Modules.FREE_LOOK;
        if (fl.owns()) {
            fl.frame(partial);
            if (fl.owns()) {
                this.setRotation(fl.yaw(), fl.pitch());
            }
        }
    }

    @Inject(
            require = 0,
            method = {"isDetached"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$hideSelf(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.FREE_LOOK.hideSelf()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyArg(
            method = {"setup"},
            at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F")},
            index = 0)
    private float arcanicvisuals$freeLookZoom(float distance) {
        return Modules.FREE_LOOK.owns() ? distance * Math.max(0.02F, Modules.FREE_LOOK.zoom()) : distance;
    }

    /** Анимации интерфейса: the camera eases back out into third person. */
    @ModifyArg(
            method = {"setup"},
            at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F")},
            index = 0)
    private float arcanicvisuals$easeOut(float distance) {
        return Modules.UI_ANIMATIONS.cameraDistance(distance);
    }
}
