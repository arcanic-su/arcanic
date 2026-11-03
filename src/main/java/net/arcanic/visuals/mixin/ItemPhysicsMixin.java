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
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.WorldPlus;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemEntityRenderer.class})
public abstract class ItemPhysicsMixin {
    @Inject(
            require = 0,
            method = {
                "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V"
            },
            at = {@At("TAIL")})
    private void arcanicvisuals$state(ItemEntity e, ItemEntityRenderState s, float pt, CallbackInfo ci) {
        ((WorldPlus.ItemState) s).arcanicvisuals$set(e.onGround(), e.getId());
    }

    @WrapOperation(
            require = 0,
            method = {
                "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"
            },
            at = {@At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V")})
    private void arcanicvisuals$lift(
            PoseStack ps,
            float x,
            float y,
            float z,
            Operation<Void> op,
            @Local(argsOnly = true) ItemEntityRenderState s) {
        if (Modules.ITEM_PHYSICS.enabled()) {
            Modules.ITEM_PHYSICS.place(ps, s);
        } else {
            op.call(new Object[] {ps, x, y, z});
        }
    }

    @WrapOperation(
            require = 0,
            method = {
                "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"
            },
            at = {
                @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V")
            })
    private void arcanicvisuals$spin(PoseStack ps, Quaternionfc q, Operation<Void> op) {
        if (!Modules.ITEM_PHYSICS.enabled()) {
            op.call(new Object[] {ps, q});
        }
    }
}
