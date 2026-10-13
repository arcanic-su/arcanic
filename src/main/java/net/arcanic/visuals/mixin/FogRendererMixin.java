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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.ByteBuffer;
import net.arcanic.visuals.core.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Atmosphere: tints the fog colour (which also colours the distance and the clear colour). */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"), require = 0)
    private void arcanicvisuals$tintFog(
            Camera camera,
            float partialTick,
            ClientLevel level,
            int renderDistance,
            float darken,
            CallbackInfoReturnable<Vector4f> cir) {
        Vector4f v = cir.getReturnValue();
        if (v == null) return;
        if (Modules.WORLD.fogOn()) {
            float[] c = Modules.WORLD.fogColour();
            v.set(c[0], c[1], c[2], v.w);
            return;
        }
        float[] c = Modules.ATMOS.apply(v.x, v.y, v.z, false);
        if (c != null) v.set(c[0], c[1], c[2], v.w);
    }

    @WrapOperation(
            method = "setupFog",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void arcanicvisuals$fogDistance(
            FogRenderer self,
            ByteBuffer buffer,
            int offset,
            Vector4f colour,
            float envStart,
            float envEnd,
            float renderStart,
            float renderEnd,
            float skyEnd,
            float cloudEnd,
            Operation<Void> op) {
        var w = Modules.WORLD;
        if (w.fogOn()) {
            envStart = w.fogStart.f();
            envEnd = Math.max(envStart + 1, w.fogEnd.f());
            renderStart = Math.min(renderStart, envStart);
            renderEnd = Math.min(renderEnd, envEnd);
        }
        op.call(self, buffer, offset, colour, envStart, envEnd, renderStart, renderEnd, skyEnd, cloudEnd);
    }
}
