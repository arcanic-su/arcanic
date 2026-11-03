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
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.cosmetic.Wearables;
import net.arcanic.visuals.fx.Ducks;
import net.arcanic.visuals.fx.Extras;
import net.arcanic.visuals.fx.PaintRender;
import net.arcanic.visuals.fx.TrapZone;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected EntityModel<?> model;

    @ModifyArg(
            method = {
                "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"
            },
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V")
            },
            index = 6)
    private int arcanicvisuals$trapTint(
            Model<?> model,
            Object state,
            PoseStack pose,
            RenderType type,
            int light,
            int overlay,
            int colour,
            TextureAtlasSprite sprite,
            int outline,
            ModelFeatureRenderer.CrumblingOverlay crumbling) {
        try {
            Entity e = Ducks.entityOf((EntityRenderState) state);

            for (TrapZone z : Modules.ZONES) {
                colour = z.tint(e, colour);
            }

            return Modules.HIT_COLOR.colour(state, Modules.HOLY_THING.tint(e, colour));
        } catch (RuntimeException e) {
            return colour;
        }
    }

    @Inject(
            method = {
                "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"
            },
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
                        shift = Shift.AFTER)
            })
    private void arcanicvisuals$outline(
            LivingEntityRenderState state,
            PoseStack pose,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo ci) {
        try {
            ((EntityModel<LivingEntityRenderState>) (EntityModel<?>) this.model).setupAnim(state);
        } catch (RuntimeException ignored) {
        }

        try {
            PaintRender.hull(this.model, state, pose, collector);
        } catch (RuntimeException ignored) {
        }

        try {
            Wearables.afterBody(this.model, state, pose, collector);
        } catch (RuntimeException ignored) {
        }

        try {
            Extras.afterBody(this.model, state, pose, collector);
        } catch (RuntimeException ignored) {
        }
    }

    /** Hit Color: the hurt flash drawn flat in its colour instead of the skin. */
    @ModifyArg(
            method = {
                "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"
            },
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V")
            },
            index = 3)
    private RenderType arcanicvisuals$hitColourType(
            Model<?> model,
            Object state,
            PoseStack pose,
            RenderType type,
            int light,
            int overlay,
            int colour,
            TextureAtlasSprite sprite,
            int outline,
            ModelFeatureRenderer.CrumblingOverlay crumbling) {
        return Modules.HIT_COLOR.type(state, type);
    }

    /** Hit Color: no vanilla red on top of our own colour. */
    @ModifyArg(
            method = {
                "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"
            },
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V")
            },
            index = 5)
    private int arcanicvisuals$hitColourOverlay(
            Model<?> model,
            Object state,
            PoseStack pose,
            RenderType type,
            int light,
            int overlay,
            int colour,
            TextureAtlasSprite sprite,
            int outline,
            ModelFeatureRenderer.CrumblingOverlay crumbling) {
        return Modules.HIT_COLOR.overlay(state, overlay);
    }

    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void arcanicvisuals$selfName(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (Modules.SELF_NAME.shows(entity)) cir.setReturnValue(true);
    }
}
