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

import net.arcanic.visuals.helper.ChatTools;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ChatComponent.class})
public abstract class ChatToolsMixin {
    @ModifyVariable(
            require = 0,
            method = {
                "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V"
            },
            at = {@At("HEAD")},
            argsOnly = true,
            ordinal = 0)
    private Component arcanicvisuals$incoming(Component message) {
        try {
            return ChatTools.incoming((ChatComponent) (Object) this, message);
        } catch (RuntimeException var3) {
            return message;
        }
    }

    @Inject(
            require = 0,
            method = {"clearMessages"},
            at = {@At("HEAD")},
            cancellable = true)
    private void arcanicvisuals$keep(boolean history, CallbackInfo ci) {
        if (history && ChatTools.keepChat()) {
            ci.cancel();
        }
    }
}
