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
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerItemInHandLayer.class)
public abstract class MeleeThirdPersonMixin {
    @Inject(
            method =
                    "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void arc$renderMeleeSkin(
            AvatarRenderState state,
            ItemStackRenderState itemState,
            ItemStack item,
            HumanoidArm arm,
            PoseStack matrices,
            SubmitNodeCollector queue,
            int light,
            CallbackInfo callback) {
        if (MeleeSkins.renderingThirdPerson(item) != null) {
            Minecraft client = Minecraft.getInstance();
            AbstractClientPlayer player = client.player;
            if (player != null && player.getId() == state.id) {
                AvatarRenderer<?> renderer = client.getEntityRenderDispatcher().getPlayerRenderer(player);
                if (renderer != null) {
                    matrices.pushPose();
                    // the skin is placed in the arm's own space (renderThirdPerson)
                    PlayerModel skin = (PlayerModel) renderer.getModel();
                    skin.translateToHand(state, arm, matrices);
                    boolean rendered = MeleeRenderer.getInstance()
                            .renderThirdPerson(player, item, state, skin, arm, matrices, queue, light);
                    matrices.popPose();
                    if (rendered) {
                        callback.cancel();
                    }
                }
            }
        }
    }
}
