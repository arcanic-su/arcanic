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

import net.arcanic.visuals.core.Keys;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.hud.DynamicIsland;
import net.arcanic.visuals.hud.HudDrag;
import net.arcanic.visuals.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Module binds on the middle and side mouse buttons, and HUD elements moved by the mouse while the chat is open. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    private static double pixelX() {
        var mc = Minecraft.getInstance();
        return mc.mouseHandler.xpos()
                * mc.getWindow().getWidth()
                / mc.getWindow().getScreenWidth();
    }

    private static double pixelY() {
        var mc = Minecraft.getInstance();
        return mc.mouseHandler.ypos()
                * mc.getWindow().getHeight()
                / mc.getWindow().getScreenHeight();
    }

    @Inject(method = "onMove", at = @At("TAIL"))
    private void arcanicvisuals$drag(long window, double xpos, double ypos, CallbackInfo ci) {
        if (HudDrag.holding() && HudDrag.chatOpen()) HudDrag.move(pixelX(), pixelY());
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void arcanicvisuals$bind(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (action == GLFW.GLFW_PRESS && mc.screen instanceof ChatScreen) {
            double x = mc.mouseHandler.xpos()
                    * mc.getWindow().getWidth()
                    / mc.getWindow().getScreenWidth();
            double y = mc.mouseHandler.ypos()
                    * mc.getWindow().getHeight()
                    / mc.getWindow().getScreenHeight();
            // a HUD element under the cursor is moved by dragging; the player under the watermark still gets its clicks
            HudModule under = info.button() == 0 ? HudDrag.at(x, y, false) : null;
            if (under != null) {
                HudDrag.press(under, x, y);
                ci.cancel();
                return;
            }
            for (var h : Modules.HUD)
                if (h instanceof DynamicIsland island && island.musicClick(x, y, info.button())) {
                    ci.cancel();
                    return;
                }
        }
        if (action == GLFW.GLFW_RELEASE && HudDrag.holding()) {
            HudModule clicked = HudDrag.release();
            if (clicked instanceof DynamicIsland island) island.musicClick(pixelX(), pixelY(), 0);
            ci.cancel();
            return;
        }
        if (action == GLFW.GLFW_PRESS && info.button() >= 2)
            try {
                Keys.pressed(Keys.MOUSE + info.button());
            } catch (RuntimeException ignored) {
            }
    }
}
