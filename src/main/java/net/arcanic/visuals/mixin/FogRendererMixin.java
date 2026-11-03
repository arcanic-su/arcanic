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
import net.arcanic.visuals.fx.CustomFog;
import net.arcanic.visuals.fx.NoRender;
import net.arcanic.visuals.fx.ShaderSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FogRenderer.class})
public abstract class FogRendererMixin {
    @Unique
    private static final Vector4f arcanicvisuals$noFogColour = new Vector4f(0.0F);

    @Inject(
            method = {"computeFogColor"},
            at = {@At("RETURN")},
            require = 0)
    private void arcanicvisuals$tintFog(
            Camera camera,
            float partialTick,
            ClientLevel level,
            int renderDistance,
            float darken,
            CallbackInfoReturnable<Vector4f> cir) {
        Vector4f v = (Vector4f) cir.getReturnValue();
        if (v != null && Modules.CUSTOM_FOG.active()) {
            Modules.CUSTOM_FOG.tint(v);
        }
    }

    @WrapOperation(
            method = {"setupFog"},
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V")
            })
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
        CustomFog fog = Modules.CUSTOM_FOG;
        if (NoRender.hides(Modules.NO_RENDER.fog)) {
            envStart = renderStart = envEnd = renderEnd = Float.MAX_VALUE;
        } else if (fog.active()) {
            colour = new Vector4f(colour);
            fog.tint(colour);
            if (fog.customRange.on()) {
                renderStart = 0.0F;
                envStart = 0.0F;
                envEnd = renderEnd = skyEnd = cloudEnd = fog.fogEnd.f();
            }
        }

        ShaderSky.captureFog(envStart, envEnd, skyEnd, colour);
        op.call(new Object[] {self, buffer, offset, colour, envStart, envEnd, renderStart, renderEnd, skyEnd, cloudEnd
        });
    }
}
