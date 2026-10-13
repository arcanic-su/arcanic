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

import net.arcanic.visuals.fx.Ducks;
import net.arcanic.visuals.fx.PaintRender;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third person: flag the local player's weapon item states for painting. */
@Mixin(ArmedEntityRenderState.class)
public abstract class ArmedEntityRenderStateMixin {
    @Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
    private static void arcanicvisuals$paint(
            LivingEntity entity,
            ArmedEntityRenderState state,
            ItemModelResolver resolver,
            float partialTick,
            CallbackInfo ci) {
        boolean rightMain = state.mainArm == HumanoidArm.RIGHT;
        ((Ducks.PaintFlag) state.rightHandItemState)
                .arcanicvisuals$setPaint(PaintRender.thirdPerson(entity, state.rightHandItemStack, rightMain));
        ((Ducks.PaintFlag) state.leftHandItemState)
                .arcanicvisuals$setPaint(PaintRender.thirdPerson(entity, state.leftHandItemStack, !rightMain));
    }
}
