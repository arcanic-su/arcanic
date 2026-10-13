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
import net.arcanic.visuals.fx.Ducks;
import net.arcanic.visuals.fx.PaintRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third person: an item state flagged at extract time paints its layers while it is submitted. */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements Ducks.PaintFlag {
    @Unique
    private boolean arcanicvisuals$paint;

    @Unique
    private boolean arcanicvisuals$active;

    @Override
    public boolean arcanicvisuals$paint() {
        return arcanicvisuals$paint;
    }

    @Override
    public void arcanicvisuals$setPaint(boolean on) {
        arcanicvisuals$paint = on;
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void arcanicvisuals$begin(
            PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        arcanicvisuals$active = arcanicvisuals$paint;
        if (arcanicvisuals$active) PaintRender.pendingItem++;
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void arcanicvisuals$end(
            PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        if (arcanicvisuals$active) {
            PaintRender.pendingItem = Math.max(0, PaintRender.pendingItem - 1);
            arcanicvisuals$active = false;
        }
    }
}
