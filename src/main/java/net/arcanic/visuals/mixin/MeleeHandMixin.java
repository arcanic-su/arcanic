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
import net.arcanic.visuals.arc.*;
import net.arcanic.visuals.core.Modules;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class MeleeHandMixin {
    @Inject(
            method =
                    "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("HEAD"))
    private void arc$beginMeleeHands(
            float tickProgress,
            PoseStack matrices,
            SubmitNodeCollector queue,
            LocalPlayer player,
            int light,
            CallbackInfo callback) {
        MeleeRenderer.getInstance().beginHands();
    }

    @Inject(
            method =
                    "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures()V"))
    private void arc$endMeleeHands(
            float tickProgress,
            PoseStack matrices,
            SubmitNodeCollector queue,
            LocalPlayer player,
            int light,
            CallbackInfo callback) {
        MeleeRenderer.getInstance().flushHandItem(player, matrices, queue, light);
    }

    @Inject(
            method =
                    "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void arc$renderMeleeSkin(
            AbstractClientPlayer player,
            float tickProgress,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            PoseStack matrices,
            SubmitNodeCollector queue,
            int light,
            CallbackInfo callback) {
        if (!player.isScoping()) {
            var knives = Modules.KNIVES;
            if (knives.hidesEmptyMainHand(player, hand, item)) {
                callback.cancel();
            } else if (knives.rendersFirstPerson(item)) {
                // the knife's own draw animation replaces vanilla's lowering; other items still lower when switched
                float retireProgress = knives.holdsSkinned(hand) ? 0.0F : equipProgress;
                if (MeleeRenderer.getInstance().renderFirstPerson(player, hand, matrices, queue, light, retireProgress))
                    callback.cancel();
            } else if (knives.attachesHandItem(player, hand, item)) {
                if (MeleeRenderer.getInstance().deferHandItem(item, equipProgress)) callback.cancel();
            }
        }
    }
}
