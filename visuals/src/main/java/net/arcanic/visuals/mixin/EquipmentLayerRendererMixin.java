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
import net.arcanic.visuals.fx.PaintRender;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Armour colour: the base armour layers get the grey texture (for Recolour) and our marked colour. */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
    private static final String LAYERS =
            "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";

    @Inject(method = LAYERS, at = @At("HEAD"))
    private void arcanicvisuals$begin(
            EquipmentClientInfo.LayerType type,
            ResourceKey<EquipmentAsset> asset,
            Model<?> model,
            Object state,
            ItemStack stack,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            Identifier texture,
            int outline,
            int order,
            CallbackInfo ci) {
        try {
            PaintRender.armourBegin(type, state, pose);
        } catch (RuntimeException ignored) {
        }
    }

    @Inject(method = LAYERS, at = @At("RETURN"))
    private void arcanicvisuals$end(
            EquipmentClientInfo.LayerType type,
            ResourceKey<EquipmentAsset> asset,
            Model<?> model,
            Object state,
            ItemStack stack,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            Identifier texture,
            int outline,
            int order,
            CallbackInfo ci) {
        PaintRender.armourEnd();
    }

    @ModifyArg(
            method = LAYERS,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private Identifier arcanicvisuals$texture(Identifier texture) {
        return PaintRender.armourTexture(texture);
    }

    @ModifyArg(
            method = LAYERS,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
                            ordinal = 0),
            index = 6)
    private int arcanicvisuals$colour(int colour) {
        return PaintRender.armourColour(colour);
    }
}
