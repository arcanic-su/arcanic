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
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Packets the server helpers listen to: items picked up, menus opened. */
@Mixin(ClientPacketListener.class)
public abstract class ServerToolsMixin {
    private static final String ON_GAME_THREAD =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

    @Inject(
            method = "handleTakeItemEntity",
            at = @At(value = "INVOKE", target = ON_GAME_THREAD, shift = Shift.AFTER),
            require = 0)
    private void arcanicvisuals$picked(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        Modules.PICKUP_LOG.picked(packet);
    }

    @Inject(
            method = "handleOpenScreen",
            at = @At(value = "INVOKE", target = ON_GAME_THREAD, shift = Shift.AFTER),
            cancellable = true,
            require = 0)
    private void arcanicvisuals$joinMenu(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (Modules.RW_HELPER.closes(packet)) ci.cancel();
    }
}
