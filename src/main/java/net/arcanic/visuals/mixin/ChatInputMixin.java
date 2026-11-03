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

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.arcanic.visuals.helper.ChatTools;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({ChatScreen.class})
public abstract class ChatInputMixin {
    @WrapMethod(
            require = 0,
            method = {"handleChatInput"})
    private void arcanicvisuals$send(String message, boolean history, Operation<Void> original) {
        String m;
        try {
            m = ChatTools.rewrite(message);
            if (!ChatTools.send(m)) {
                return;
            }
        } catch (RuntimeException e) {
            m = message;
        }

        original.call(new Object[] {m, history});
    }
}
