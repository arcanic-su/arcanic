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

import net.arcanic.visuals.gui.FrontEndLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rendering only: vanilla tick retains completion and error callbacks. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private boolean fadeIn;

    @Shadow
    private long fadeOutStart;

    @Shadow
    private long fadeInStart;

    @Unique
    private long arcanic$last;

    @Unique
    private float arcanic$progress;

    @Unique
    private float arcanic$target;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void arcanic$loading(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        ci.cancel();
        long now = Util.getMillis();
        if (fadeIn && fadeInStart < 0) fadeInStart = now;
        float dt = arcanic$last == 0 ? 0 : Math.min(.1f, (now - arcanic$last) / 1000f);
        arcanic$last = now;
        arcanic$target = Math.max(arcanic$target, fadeOutStart >= 0 ? 1 : Math.min(.995f, reload.getActualProgress()));
        arcanic$progress += (arcanic$target - arcanic$progress) * (1 - (float) Math.exp(-dt * 12));
        if (arcanic$target == 1 && arcanic$progress > .998f) arcanic$progress = 1;
        float leaving = -1;
        if (fadeOutStart >= 0) {
            FrontEndLoading.splashLeaving();
            leaving = FrontEndLoading.splashMs();
            if (leaving < 0) leaving = Math.max(0, now - fadeOutStart) * 1.6f;
        }
        float in = fadeIn ? Math.max(0, Math.min(1, (now - fadeInStart) / 300f)) : 1;
        float alpha = in * in * (3 - 2 * in);
        if ((leaving >= 0 || fadeIn && in < 1) && minecraft.screen != null)
            minecraft.screen.renderWithTooltipAndSubtitles(g, mx, my, delta);
        FrontEndLoading.scene(
                g, arcanic$progress, alpha, "Загрузка ресурсов", fadeIn || fadeOutStart >= 0, true, leaving);
        if (leaving >= FrontEndLoading.LEAVE_MS && minecraft.getOverlay() == (Object) this) {
            FrontEndLoading.splashGone();
            minecraft.setOverlay(null);
        }
    }
}
