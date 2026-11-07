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
package net.arcanic.visuals.helper;

import java.util.Locale;
import java.util.Set;
import net.arcanic.visuals.mixin.PlayerTabOverlayAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** What the FunTime helpers need to know about the server: is it FunTime, which anarchy, what an item is. */
public final class FunTime {
    /** Anarchies where the server switches the paid items (traps, plasts and so on) off. */
    private static final Set<Integer> NO_SPECIAL_ITEMS = Set.of(106, 107, 212, 213, 214, 308, 309);

    private FunTime() {}

    public static boolean here() {
        ServerData d = Minecraft.getInstance().getCurrentServer();
        return d != null && d.ip != null && d.ip.toLowerCase(Locale.ROOT).contains("funtime");
    }

    /** The anarchy's number from the tab header ("Анархия-212"), or -1. */
    public static int anarchy() {
        Minecraft mc = Minecraft.getInstance();
        try {
            Component header = ((PlayerTabOverlayAccess) mc.gui.getTabList()).arcanicvisuals$header();
            if (header == null) return -1;
            String s = ChatFormatting.stripFormatting(header.getString());
            if (s == null) return -1;
            int at = s.indexOf("Анархия-");
            if (at < 0) return -1;
            StringBuilder n = new StringBuilder();
            for (int i = at + "Анархия-".length(); i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isDigit(c)) n.append(c);
                else if (!n.isEmpty()) break;
            }
            return n.isEmpty() || n.length() > 6 ? -1 : Integer.parseInt(n.toString());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public static boolean specialItemsOff(int anarchy) {
        return NO_SPECIAL_ITEMS.contains(anarchy);
    }

    /** A FunTime paid item: a trap (cage), a plast (wall), or nothing. */
    public enum Item {
        TRAP,
        PLAST,
        NONE
    }

    public static Item item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Item.NONE;
        Item tagged = tagged(stack);
        if (tagged != Item.NONE) return tagged;
        String name = plain(stack.getHoverName().getString());
        if (name.contains("трапка")) return Item.TRAP;
        if (name.contains("пласт")) return Item.PLAST;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return Item.NONE;
        boolean cage = false;
        boolean wall = false;
        for (Component line : lore.lines()) {
            String s = plain(line.getString());
            cage |= s.contains("нерушимая клетка");
            wall |= s.contains("нерушимая стена");
        }
        return cage && !wall ? Item.TRAP : (wall && !cage ? Item.PLAST : Item.NONE);
    }

    private static Item tagged(ItemStack stack) {
        CompoundTag values = bukkit(stack);
        if (values == null) return Item.NONE;
        Item byId = named(values.getStringOr("minecraft:ftid", ""));
        if (byId != Item.NONE) return byId;
        Item paid = named(values.getStringOr("minecraft:don-item", ""));
        if (paid != Item.NONE) return paid;
        if (values.getIntOr("minecraft:trap", 0) == 1) return Item.TRAP;
        return values.getIntOr("minecraft:stratum", 0) == 1 ? Item.PLAST : Item.NONE;
    }

    /** The server's own values on an item ({@code PublicBukkitValues}), or null. */
    public static CompoundTag bukkit(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag values = data.copyTag().getCompoundOrEmpty("PublicBukkitValues");
        return values.isEmpty() ? null : values;
    }

    private static Item named(String id) {
        if ("trap".equals(id)) return Item.TRAP;
        return "stratum".equals(id) ? Item.PLAST : Item.NONE;
    }

    private static String plain(String s) {
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }
}
