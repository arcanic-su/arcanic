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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public abstract class ChatCommandClickMixin {
    @Inject(
            method = {"sendUnattendedCommand"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$ours(String command, Screen screen, CallbackInfo ci) {
        String c = command.startsWith("/") ? command.substring(1) : command;
        if (c.startsWith("arcanic markat ")) {
            Modules.MARKS.chatMark(c.substring("arcanic markat ".length()));
            ci.cancel();
        } else if (c.startsWith("arcanic join ")) {
            Modules.HOLY_EVENTS.join(c.substring("arcanic join ".length()).trim());
            ci.cancel();
        }
    }
}
