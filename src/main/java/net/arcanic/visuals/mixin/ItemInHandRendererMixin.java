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
import net.arcanic.visuals.fx.HandAnim;
import net.arcanic.visuals.fx.PaintRender;
import net.arcanic.visuals.fx.VisualFx;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person hand: applies the view model offsets and replaces vanilla's swing with the chosen animation. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Unique
    private float arcanicvisuals$swing;

    @Unique
    private boolean arcanicvisuals$painting;

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void arcanicvisuals$paintBegin(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext ctx,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            CallbackInfo ci) {
        arcanicvisuals$painting = PaintRender.firstPerson(entity, stack, ctx);
        if (arcanicvisuals$painting) PaintRender.pendingItem++;
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void arcanicvisuals$paintEnd(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext ctx,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            CallbackInfo ci) {
        if (arcanicvisuals$painting) {
            PaintRender.pendingItem = Math.max(0, PaintRender.pendingItem - 1);
            arcanicvisuals$painting = false;
        }
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float arcanicvisuals$takeSwing(float swing) {
        arcanicvisuals$swing = swing;
        return Modules.ANIM.customSwing() ? 0 : swing;
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float arcanicvisuals$equip(float equip) {
        return Modules.ANIM.enabled() && Modules.ANIM.noEquip.on() ? 0 : equip;
    }

    @Inject(
            method = "renderArmWithItem",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                            shift = At.Shift.AFTER))
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
        if (!v.enabled() || (!v.both.on() && hand != InteractionHand.MAIN_HAND)) return;
        float side = side(player, hand);
        pose.translate(v.x.f() * side, v.y.f(), v.z.f());
        pose.mulPose(Axis.XP.rotationDegrees(v.rx.f()));
        pose.mulPose(Axis.YP.rotationDegrees(v.ry.f() * side));
        pose.mulPose(Axis.ZP.rotationDegrees(v.rz.f() * side));
        float s = v.scale.f();
        pose.scale(s, s, s);
    }

    @Inject(
            method = "renderArmWithItem",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"))
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
        HandAnim a = Modules.ANIM;
        if (!a.enabled()) return;
        float side = side(player, hand);
        if (hand == InteractionHand.MAIN_HAND) {
            float t = a.inspectProgress();
            if (t >= 0) {
                a.applyInspect(pose, t, side);
                return;
            }
        }
        if (a.customSwing()) a.applySwing(pose, arcanicvisuals$swing, side);
    }

    @Invoker("renderPlayerArm")
    abstract void arcanicvisuals$renderPlayerArm(
            PoseStack pose, SubmitNodeCollector collector, int light, float equip, float swing, HumanoidArm arm);

    /** Regrip: the arms that take part in the inspect, drawn after the held items. */
    // before the hand pass is flushed (renderAllFeatures), so the arms are drawn with the hands' projection
    @Inject(
            method = "renderHandsWithItems",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures()V"))
    private void arcanicvisuals$inspectArms(
            float partialTick,
            PoseStack pose,
            SubmitNodeCollector collector,
            LocalPlayer player,
            int light,
            CallbackInfo ci) {
        HandAnim a = Modules.ANIM;
        if (!a.enabled() || player.isInvisible()) return;
        float t = a.inspectProgress();
        if (t < 0) return;
        HumanoidArm main = player.getMainArm();
        float mainSide = main == HumanoidArm.RIGHT ? 1 : -1;
        float[] off = a.armPose(true, t), mainPose = a.armPose(false, t);
        if (off != null && player.getOffhandItem().isEmpty()) {
            // a stack of our own: submissions keep the pose by reference, so a popped (reused) pose would move the arm
            PoseStack own = new PoseStack();
            own.last().set(pose.last());
            HandAnim.applyArm(own, off, mainSide, -mainSide);
            arcanicvisuals$renderPlayerArm(own, collector, light, 0, 0, main.getOpposite());
        }
        if (mainPose != null && mainPose[1] > -.79f) {
            PoseStack own = new PoseStack();
            own.last().set(pose.last());
            HandAnim.applyArm(own, mainPose, mainSide, mainSide);
            arcanicvisuals$renderPlayerArm(own, collector, light, 0, 0, main);
        }
    }

    @Unique
    private static float side(AbstractClientPlayer player, InteractionHand hand) {
        boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
        return right ? 1 : -1;
    }
}
