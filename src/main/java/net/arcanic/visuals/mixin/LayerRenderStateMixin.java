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
import java.util.List;
import net.arcanic.visuals.fx.PaintRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A painted item is submitted with our mark in its tint layers (and the grey atlas for Recolour). */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class LayerRenderStateMixin {
    @WrapOperation(
            method = "submit",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"))
    private void arcanicvisuals$paint(
            SubmitNodeCollector collector,
            PoseStack pose,
            ItemDisplayContext ctx,
            int light,
            int overlay,
            int outline,
            int[] tints,
            List<BakedQuad> quads,
            RenderType type,
            ItemStackRenderState.FoilType foil,
            Operation<Void> op) {
        if (PaintRender.pendingItem > 0) {
            RenderType painted = type;
            try {
                painted = PaintRender.itemType(type, quads);
            } catch (RuntimeException ignored) {
            }
            op.call(collector, pose, ctx, light, overlay, outline, PaintRender.markTints(tints), quads, painted, foil);
            RenderType aura = null;
            try {
                aura = PaintRender.auraType(quads);
            } catch (RuntimeException ignored) {
            }
            if (aura != null)
                op.call(
                        collector,
                        pose,
                        ctx,
                        light,
                        overlay,
                        0,
                        PaintRender.markAura(tints),
                        quads,
                        aura,
                        ItemStackRenderState.FoilType.NONE);
        } else op.call(collector, pose, ctx, light, overlay, outline, tints, quads, type, foil);
    }
}
