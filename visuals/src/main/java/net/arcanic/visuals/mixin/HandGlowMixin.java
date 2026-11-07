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
import net.arcanic.visuals.fx.HandGlow;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemInHandRenderer.class})
public abstract class HandGlowMixin {
    @Inject(
            method = {"renderHandsWithItems"},
            at = {@At("HEAD")})
    private void arcanicvisuals$glowBegin(
            float partialTick,
            PoseStack pose,
            SubmitNodeCollector collector,
            LocalPlayer player,
            int light,
            CallbackInfo ci) {
        HandGlow.beginHands();
    }

    @Inject(
            method = {"renderItem"},
            at = {@At("HEAD")})
    private void arcanicvisuals$glowRecord(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext context,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            CallbackInfo ci) {
        HandGlow.record(entity, stack, context, pose);
    }

    @Inject(
            method = {"renderHandsWithItems"},
            at = {
                @At(
                        value = "INVOKE",
                        target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures()V",
                        shift = Shift.AFTER)
            })
    private void arcanicvisuals$glowFlush(
            float partialTick,
            PoseStack pose,
            SubmitNodeCollector collector,
            LocalPlayer player,
            int light,
            CallbackInfo ci) {
        HandGlow.flush((ItemInHandRenderer) (Object) this);
    }
}
