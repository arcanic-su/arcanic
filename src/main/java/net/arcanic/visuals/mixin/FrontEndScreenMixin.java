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

import net.arcanic.visuals.gui.Wallpaper;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Screen.class})
public abstract class FrontEndScreenMixin {
    @Inject(
            method = {"renderBackground"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$background(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        if (Minecraft.getInstance().level == null) {
            Px.begin(g);

            try {
                Wallpaper.draw(g, 1.0F, 1.0F, 1.0F, Wallpaper.reach());
                g.fill(0, 0, Px.width(), Px.height(), 1375731712);
            } finally {
                Px.end(g);
            }

            ci.cancel();
        }
    }
}
