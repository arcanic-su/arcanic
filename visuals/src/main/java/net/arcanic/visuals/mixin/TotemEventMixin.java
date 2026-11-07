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
import net.arcanic.visuals.hud.DynamicIsland;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public abstract class TotemEventMixin {
    @Inject(
            method = {"handleEntityEvent"},
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                        shift = Shift.AFTER)
            })
    private void arcanicvisuals$totemEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() == 35) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                Entity entity2 = packet.getEntity(level);
                if (entity2 instanceof LivingEntity) {
                    LivingEntity l = (LivingEntity) entity2;
                    if (Modules.TOTEM.enabled()) {
                        Modules.TOTEM.popped(l);
                    }

                    Modules.TOTEM_TRACKER.popped(l);
                    DynamicIsland.totem(l);
                    return;
                }
            }
        }
    }
}
