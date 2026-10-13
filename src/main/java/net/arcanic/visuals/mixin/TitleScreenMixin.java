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
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique
    private FrontEndMenu arcanic$menu;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void arcanic$init(CallbackInfo ci) {
        List<AbstractButton> actions = children().stream()
                .filter(c -> c instanceof AbstractButton)
                .map(c -> (AbstractButton) c)
                .toList();
        clearWidgets();
        arcanic$menu = new FrontEndMenu(this, actions, button -> addRenderableWidget(button));
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void arcanic$render(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        if (arcanic$menu != null) {
            arcanic$menu.render(g, mx, my, delta);
            ci.cancel();
        }
    }
}
