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

import java.util.List;
import net.arcanic.visuals.gui.FrontEndMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({TitleScreen.class})
public abstract class TitleScreenMixin extends Screen {
    @Unique
    private FrontEndMenu arcanicvisuals$menu;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(
            method = {"init()V"},
            at = {@At("RETURN")})
    private void arcanicvisuals$init(CallbackInfo ci) {
        List<AbstractButton> actions = this.children().stream()
                .filter((c) -> c instanceof AbstractButton)
                .map((c) -> (AbstractButton) c)
                .toList();
        this.clearWidgets();
        this.arcanicvisuals$menu = new FrontEndMenu(this, actions, (button) -> this.addRenderableWidget(button));
    }

    @Inject(
            method = {"render"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$render(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        if (this.arcanicvisuals$menu != null) {
            this.arcanicvisuals$menu.render(g, mx, my, delta);
            ci.cancel();
        }
    }

    @Inject(
            method = {"mouseClicked"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$press(MouseButtonEvent e, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this.arcanicvisuals$menu != null && this.arcanicvisuals$menu.pressed()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = {"mouseClicked"},
            at = {@At("RETURN")})
    private void arcanicvisuals$missed(MouseButtonEvent e, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this.arcanicvisuals$menu != null && e.button() == 0 && !cir.getReturnValueZ()) {
            this.arcanicvisuals$menu.missed(e.x(), e.y());
        }
    }

    public boolean keyPressed(KeyEvent e) {
        return this.arcanicvisuals$menu != null && this.arcanicvisuals$menu.key(e.key()) ? true : super.keyPressed(e);
    }
}
