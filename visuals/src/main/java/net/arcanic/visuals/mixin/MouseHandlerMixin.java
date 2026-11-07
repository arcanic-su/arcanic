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
import net.arcanic.visuals.helper.Pings;
import net.arcanic.visuals.hud.DynamicIsland;
import net.arcanic.visuals.hud.HudDrag;
import net.arcanic.visuals.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MouseHandler.class})
public abstract class MouseHandlerMixin {
    private static double pixelX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.xpos()
                * (double) mc.getWindow().getWidth()
                / (double) mc.getWindow().getScreenWidth();
    }

    private static double pixelY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.ypos()
                * (double) mc.getWindow().getHeight()
                / (double) mc.getWindow().getScreenHeight();
    }

    @Inject(
            method = {"onMove"},
            at = {@At("TAIL")})
    private void arcanicvisuals$drag(long window, double xpos, double ypos, CallbackInfo ci) {
        if (HudDrag.holding() && HudDrag.chatOpen()) {
            HudDrag.move(pixelX(), pixelY());
        }
    }

    @Inject(
            method = {"onButton"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$bind(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (action == 1 && mc.screen instanceof ChatScreen) {
            double x = mc.mouseHandler.xpos()
                    * (double) mc.getWindow().getWidth()
                    / (double) mc.getWindow().getScreenWidth();
            double y = mc.mouseHandler.ypos()
                    * (double) mc.getWindow().getHeight()
                    / (double) mc.getWindow().getScreenHeight();
            if (info.button() == 0 && Modules.HOLY_EVENTS_HUD.clickRow(x, y)) {
                ci.cancel();
                return;
            }

            HudModule under = info.button() == 0 ? HudDrag.at(x, y, false) : null;
            if (under instanceof DynamicIsland || under == Modules.HOLY_EVENTS_HUD) {
                under = null;
            }

            if (under != null) {
                HudDrag.press(under, x, y);
                ci.cancel();
                return;
            }

            if (Modules.MARKS.click(x, y, info.button())) {
                ci.cancel();
                return;
            }

            for (HudModule h : Modules.HUD) {
                if (h instanceof DynamicIsland) {
                    DynamicIsland island = (DynamicIsland) h;
                    if (island.musicClick(x, y, info.button())) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }

        if (action == 0 && HudDrag.holding()) {
            HudModule clicked = HudDrag.release();
            if (clicked instanceof DynamicIsland) {
                DynamicIsland island = (DynamicIsland) clicked;
                island.musicClick(pixelX(), pixelY(), 0);
            }

            ci.cancel();
        } else {
            if (action == 1 && info.button() >= 2) {
                Object before = mc.screen;
                try {
                    Keys.pressed(1000 + info.button());
                } catch (RuntimeException ignored) {
                }

                // the press opened a screen or went to pings: it must not reach the screen or pick a block
                if (mc.screen != before || Pings.holding()) {
                    ci.cancel();
                }
            }

            if (action == 0 && info.button() == 2 && Pings.release()) {
                ci.cancel(); // the wheel came up quickly: a tap ping or the player menu
            }
        }
    }
}
