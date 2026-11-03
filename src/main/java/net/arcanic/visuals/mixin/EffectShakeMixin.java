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

import net.arcanic.visuals.fx.impact.ImpactDraw;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Camera.class})
public abstract class EffectShakeMixin {
    @Shadow
    protected abstract void setRotation(float var1, float var2);

    @Inject(
            method = {"setup"},
            at = {
                @At(
                        value = "INVOKE",
                        target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                        ordinal = 1,
                        shift = Shift.AFTER)
            })
    private void arcanicvisuals$effectsShake(
            Level level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        float[] shake = ImpactDraw.shakeOffset();
        if (shake != null) {
            this.setRotation(self.yRot() + shake[0], self.xRot() + shake[1]);
        }
    }
}
