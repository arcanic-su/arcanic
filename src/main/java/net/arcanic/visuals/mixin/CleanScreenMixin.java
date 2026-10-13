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

import net.arcanic.visuals.core.Modules;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class CleanScreenMixin {
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void arcanic$vignette(GuiGraphics g, Entity e, CallbackInfo ci) {
        if (Modules.NO_RENDER.enabled() && Modules.NO_RENDER.vignette.on()) ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void arcanic$portal(GuiGraphics g, float alpha, CallbackInfo ci) {
        if (Modules.NO_RENDER.enabled() && Modules.NO_RENDER.portal.on()) ci.cancel();
    }

    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void arcanic$nausea(GuiGraphics g, float alpha, CallbackInfo ci) {
        if (Modules.NO_RENDER.enabled() && Modules.NO_RENDER.nausea.on()) ci.cancel();
    }
}
