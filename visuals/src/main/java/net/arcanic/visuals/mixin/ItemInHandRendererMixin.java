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
import com.mojang.math.Axis;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.VisualFx;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemInHandRenderer.class})
public abstract class ItemInHandRendererMixin {
    @Unique
    private float arcanicvisuals$swing;

    @Unique
    private InteractionHand arcanicvisuals$hand;

    public ItemInHandRendererMixin() {
        this.arcanicvisuals$hand = InteractionHand.MAIN_HAND;
    }

    @ModifyVariable(
            method = {"renderArmWithItem"},
            at = {@At("HEAD")},
            argsOnly = true,
            ordinal = 2)
    private float arcanicvisuals$takeSwing(
            float swing,
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float originalSwing,
            ItemStack stack,
            float equip,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light) {
        this.arcanicvisuals$hand = hand;
        this.arcanicvisuals$swing = swing;
        return swing;
    }

    @ModifyVariable(
            method = {"renderArmWithItem"},
            at = {@At("HEAD")},
            argsOnly = true,
            ordinal = 3)
    private float arcanicvisuals$equip(
            float equip,
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float swing,
            ItemStack stack,
            float originalEquip,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light) {
        return Modules.SWING_ANIMATION.activeFor(hand) ? 0.0F : equip;
    }

    @Inject(
            method = {"renderArmWithItem"},
            at = {
                @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = Shift.AFTER)
            })
    private void arcanicvisuals$handView(
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float swing,
            ItemStack stack,
            float equip,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            CallbackInfo ci) {
        VisualFx.HandView v = Modules.HANDS;
        if (v.enabled() && (v.both.on() || hand == InteractionHand.MAIN_HAND)) {
            float side = side(player, hand);
            pose.translate(v.x.f() * side, v.y.f(), v.z.f());
            pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(v.rx.f()));
            pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(v.ry.f() * side));
            pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(v.rz.f() * side));
            float s = v.scale.f();
            pose.scale(s, s, s);
        }
    }

    @Inject(
            method = {"renderArmWithItem"},
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V")
            })
    private void arcanicvisuals$animate(
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float swing,
            ItemStack stack,
            float equip,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            CallbackInfo ci) {
        if (Modules.SWING_ANIMATION.activeFor(hand)) {
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                    ? player.getMainArm()
                    : player.getMainArm().getOpposite();
            Modules.SWING_ANIMATION.apply(this.arcanicvisuals$swing, arm, pose);
        }
    }

    @Inject(
            method = {"swingArm"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$swingStyle(float swing, PoseStack pose, int side, HumanoidArm arm, CallbackInfo ci) {
        if (Modules.SWING_ANIMATION.activeFor(this.arcanicvisuals$hand)) {
            ci.cancel();
        }
    }

    @Invoker("renderPlayerArm")
    abstract void arcanicvisuals$renderPlayerArm(
            PoseStack pose, SubmitNodeCollector collector, int light, float equip, float swing, HumanoidArm arm);

    @Unique
    private static float side(AbstractClientPlayer player, InteractionHand hand) {
        boolean right = hand == InteractionHand.MAIN_HAND == (player.getMainArm() == HumanoidArm.RIGHT);
        return right ? 1.0F : -1.0F;
    }
}
