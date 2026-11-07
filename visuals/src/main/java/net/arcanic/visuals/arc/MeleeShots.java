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
package net.arcanic.visuals.arc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;

/** The local player's bow shots, seen from the outside: a pull let go that would loose an arrow is a shot. */
public final class MeleeShots {
    private static final float MIN_POWER = 0.1F; // a weaker pull looses nothing (BowItem#releaseUsing)
    private static final float FLASH_SECONDS = 0.07F;
    private static final float NANOS_PER_SECOND = 1.0E9F;
    private static boolean pulling;
    private static float power;
    private static InteractionHand pullHand = InteractionHand.MAIN_HAND;
    private static int pullSlot = -1;
    private static int count;
    private static InteractionHand shotHand = InteractionHand.MAIN_HAND;
    private static long shotNanos;

    private MeleeShots() {}

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            pulling = false;
            return;
        }
        boolean now = player.isUsingItem() && player.getUseItem().is(Items.BOW);
        if (now) {
            pullHand = player.getUsedItemHand();
            pullSlot = player.getInventory().getSelectedSlot();
            power = BowItem.getPowerForTime(player.getTicksUsingItem());
        } else if (pulling && power >= MIN_POWER && stillHeld(player)) {
            count++;
            shotHand = pullHand;
            shotNanos = System.nanoTime();
        }
        pulling = now;
    }

    /** Switching away mid-pull drops the arrow without a shot. */
    private static boolean stillHeld(LocalPlayer player) {
        return player.getItemInHand(pullHand).is(Items.BOW)
                && (pullHand == InteractionHand.OFF_HAND
                        || player.getInventory().getSelectedSlot() == pullSlot);
    }

    static int count() {
        return count;
    }

    static InteractionHand hand() {
        return shotHand;
    }

    /** The last shot's muzzle flash, 1 as it fires and 0 once it has burnt out. */
    static float flash() {
        if (count == 0) return 0.0F;
        float seconds = (System.nanoTime() - shotNanos) / NANOS_PER_SECOND;
        return Math.max(0.0F, 1.0F - seconds / FLASH_SECONDS);
    }

    /** How far the bow in {@code hand} is pulled right now, 0 to 1, between ticks too. */
    static float pull(LocalPlayer player, InteractionHand hand, float partialTick) {
        if (player == null || !player.isUsingItem() || player.getUsedItemHand() != hand) return 0.0F;
        if (!player.getUseItem().is(Items.BOW)) return 0.0F;
        float t = player.getTicksUsingItem(partialTick) / 20.0F;
        return Math.min(1.0F, (t * t + t * 2.0F) / 3.0F);
    }
}
