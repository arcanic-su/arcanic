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
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.arcanic.visuals.fx.PaintRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draw time of a painted item: our own quads with a colour per vertex (the glint buffer still gets them too). */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private static void arcanicvisuals$paint(
            ItemDisplayContext ctx,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay,
            int[] tints,
            List<BakedQuad> quads,
            RenderType type,
            ItemStackRenderState.FoilType foil,
            CallbackInfo ci) {
        if (PaintRender.auraMarked(tints)) {
            ci.cancel();
            PaintRender.aura(pose.last(), buffers.getBuffer(type), quads);
            return;
        }
        if (!PaintRender.marked(tints)) return;
        ci.cancel();
        VertexConsumer vc = ItemRenderer.getFoilBuffer(buffers, type, true, foil != ItemStackRenderState.FoilType.NONE);
        PaintRender.item(pose.last(), vc, quads, tints, light, overlay);
    }
}
