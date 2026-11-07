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
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.NoRender;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class NoRenderGuiMixin {
    @Shadow
    public float vignetteBrightness;

    @Inject(method = "updateVignetteBrightness", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$vignetteState(Entity entity, CallbackInfo ci) {
        if (NoRender.hides(Modules.NO_RENDER.vignette)) {
            vignetteBrightness = 0;
            ci.cancel();
        }
    }

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$vignette(CallbackInfo ci) {
        if (NoRender.hides(Modules.NO_RENDER.vignette)) ci.cancel();
    }

    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$nausea(CallbackInfo ci) {
        if (NoRender.hides(Modules.NO_RENDER.nausea)) ci.cancel();
    }

    @Inject(
            method =
                    "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void arcanicvisuals$scoreboard(CallbackInfo ci) {
        if (NoRender.hides(Modules.NO_RENDER.scoreboard)) ci.cancel();
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$effects(CallbackInfo ci) {
        if (NoRender.hides(Modules.NO_RENDER.effectIcons)) ci.cancel();
    }

    @Inject(method = "renderAirBubbles", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$air(CallbackInfo ci) {
        if (NoRender.hides(Modules.NO_RENDER.airBubbles)) ci.cancel();
    }

    /** Our crosshair is on: the vanilla one never draws, whoever calls it (the HUD element only replaces one path). */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$ownCrosshair(CallbackInfo ci) {
        if (Modules.CROSSHAIR.enabled()) ci.cancel();
    }

    @WrapOperation(
            method = "renderCrosshair",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object arcanicvisuals$attackIndicator(OptionInstance<?> option, Operation<Object> original) {
        if (NoRender.hides(Modules.NO_RENDER.attackIndicator)
                && option == Minecraft.getInstance().options.attackIndicator()) return AttackIndicatorStatus.OFF;
        return original.call(option);
    }
}
