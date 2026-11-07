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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.LowFire;
import net.arcanic.visuals.fx.NoRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class NoRenderOverlayMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void arcanicvisuals$fire(
            PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NoRender.hides(Modules.NO_RENDER.fire)) {
            ci.cancel();
            return;
        }
        LowFire low = Modules.LOW_FIRE;
        if (low.enabled()) {
            // the two flames sit at y -0.3 around the view's centre: lower them and shrink them about their base
            float k = low.size.get().floatValue();
            pose.pushPose();
            pose.translate(0.0F, -low.height.get().floatValue(), 0.0F);
            pose.translate(0.0F, -0.8F * (1.0F - k), 0.0F);
            pose.scale(k, k, 1.0F);
        }
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void arcanicvisuals$fireDone(
            PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (Modules.LOW_FIRE.enabled() && !NoRender.hides(Modules.NO_RENDER.fire)) pose.popPose();
    }

    @WrapOperation(
            method = "renderFire",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer arcanicvisuals$fireColour(
            VertexConsumer v, float r, float g, float b, float a, Operation<VertexConsumer> original) {
        LowFire low = Modules.LOW_FIRE;
        if (!low.enabled()) return original.call(v, r, g, b, a);
        if (low.tint.on()) {
            int c = low.colour.argb();
            r *= (c >> 16 & 255) / 255.0F;
            g *= (c >> 8 & 255) / 255.0F;
            b *= (c & 255) / 255.0F;
        }
        return original.call(v, r, g, b, low.alpha(a));
    }

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void arcanicvisuals$insideBlock(CallbackInfo ci) {
        if (NoRender.hides(Modules.NO_RENDER.insideBlock)) ci.cancel();
    }

    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void arcanicvisuals$water(CallbackInfo ci) {
        if (NoRender.hides(Modules.NO_RENDER.insideBlock)) ci.cancel();
    }
}
