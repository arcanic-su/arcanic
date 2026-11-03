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
package net.arcanic.visuals.core;

import java.util.Map;

public final class Names {
    private static final Map<String, String> BY_ID = Map.ofEntries(
            Map.entry("hit_effects", "Hit Effects"),
            Map.entry("swing_trail", "Swing Trail"),
            Map.entry("swing_animation", "Swing Animation"),
            Map.entry("damage_numbers", "Damage Numbers"),
            Map.entry("kill_effects", "Kill Effects"),
            Map.entry("mace_fx", "Mace Smash"),
            Map.entry("projectile_hit", "Projectile Hit"),
            Map.entry("totem_fx", "Totem Effect"),
            Map.entry("potion_fx", "Potion Drink"),
            Map.entry("target_marker", "Target ESP"),
            Map.entry("hitmarker", "Hit Marker"),
            Map.entry("combohelper", "Combo Helper"),
            Map.entry("crosshair", "Crosshair"),
            Map.entry("trajectories", "Trajectories"),
            Map.entry("tnt_timer", "TNT Timer"),
            Map.entry("hit_range", "Hit Range"),
            Map.entry("hitsound", "Hit Sound"),
            Map.entry("killsound", "Kill Sound"),
            Map.entry("click_pearl", "Click Pearl"),
            Map.entry("targethud", "Target HUD"),
            Map.entry("outline", "Outline"),
            Map.entry("hitboxes", "Hitboxes"),
            Map.entry("nametags", "Nametags"),
            Map.entry("consume_esp", "Consume ESP"),
            Map.entry("arrow_tags", "Arrow Tags"),
            Map.entry("armor_paint", "Armor Color"),
            Map.entry("player_trail", "Trails"),
            Map.entry("jump_circles", "Jump Circles"),
            Map.entry("footprints", "Footprints"),
            Map.entry("projectile_trails", "Projectile Trails"),
            Map.entry("slipstream", "Slipstream"),
            Map.entry("hand_view", "View Model"),
            Map.entry("arc_hand_shader", "Hand Shader"),
            Map.entry("hand_glow", "Hand Glow"),
            Map.entry("arc_melee", "CS Knives"),
            Map.entry("arc_bow", "CS AWP"),
            Map.entry("ft_helper", "FT Helper"),
            Map.entry("ft_events", "FunTime Events"),
            Map.entry("ft_regions", "FunTime Regions"),
            Map.entry("see_invisible", "See Invisible"),
            Map.entry("totem_tracker", "Totem Tracker"),
            Map.entry("totem_bar", "Totem Bar"),
            Map.entry("hit_color", "Hit Color"),
            Map.entry("hit_bubble", "Hit Bubble"),
            Map.entry("body_glow", "Body Glow"),
            Map.entry("halo", "Halo"),
            Map.entry("china_hat", "China Hat"),
            Map.entry("self_nametag", "Self Nametag"),
            Map.entry("world_particles", "World Particles"),
            Map.entry("ui_animations", "UI Animations"),
            Map.entry("auto_reconnect", "Auto Reconnect"),
            Map.entry("pickup_log", "Pickup Log"),
            Map.entry("lock_slot", "Lock Slot"),
            Map.entry("elytra_swap", "Elytra Swap"),
            Map.entry("rw_helper", "RW Helper"),
            Map.entry("chest_sorter", "Chest Sorter"),
            Map.entry("capes", "Capes"),
            Map.entry("wings", "Wings"),
            Map.entry("backpacks", "Backpacks"),
            Map.entry("headwear", "Headwear"),
            Map.entry("emotes", "Emotes"),
            Map.entry("sky_shader", "Sky Shader"),
            Map.entry("custom_fog", "Custom Fog"),
            Map.entry("custom_time", "Custom Time"),
            Map.entry("ambience", "Ambience"),
            Map.entry("block_overlay", "Block Overlay"),
            Map.entry("item_physics", "Item Physics"),
            Map.entry("chunk_animator", "Chunk Animator"),
            Map.entry("fullbright", "Fullbright"),
            Map.entry("crystal_look", "Crystals"),
            Map.entry("motion_blur", "Motion Blur"),
            Map.entry("aspectratio", "Aspect Ratio"),
            Map.entry("optimizer", "Optimizer"),
            Map.entry("zoom", "Zoom"),
            Map.entry("free_look", "Free Look"),
            Map.entry("auto_sprint", "Auto Sprint"),
            Map.entry("item_scroller", "Item Scroller"),
            Map.entry("shulker_preview", "Shulker Preview"),
            Map.entry("chat_helper", "Chat Helper"),
            Map.entry("command_safe", "Command Guard"),
            Map.entry("auction_helper", "Auction Helper"),
            Map.entry("command_binds", "Command Binds"),
            Map.entry("sound_mixer", "Sound Mixer"),
            Map.entry("streamer", "Streamer Mode"),
            Map.entry("kits", "Kits"),
            Map.entry("fake_player", "Dummy"),
            Map.entry("chat_coords", "Chat Coords"),
            Map.entry("waypoints", "Waypoints"),
            Map.entry("holy_items", "Item Radius"),
            Map.entry("holy_events", "Events"),
            Map.entry("enchant_search", "Enchant Search"),
            Map.entry("drill_helper", "Drill Helper"),
            Map.entry("holy_tnt", "Dynamite"),
            Map.entry("holy_thing", "Explosive Thing"),
            Map.entry("watermark", "Watermark"),
            Map.entry("potions", "Potions"),
            Map.entry("hotkeys", "Keybinds"),
            Map.entry("cooldowns", "Cooldowns"),
            Map.entry("armor", "Armor HUD"),
            Map.entry("coords", "Coordinates"),
            Map.entry("keystrokes", "Keystrokes"),
            Map.entry("holy_events_hud", "Events HUD"),
            Map.entry("notify", "Notifications"),
            Map.entry("low_hp", "Low HP"),
            Map.entry("inventory_hud", "Inventory"),
            Map.entry("boss_bars", "Boss Bars"),
            Map.entry("tab_list", "Tab List"),
            Map.entry("saturation", "Saturation"),
            Map.entry("armor_alert", "Armor Alert"),
            Map.entry("no_render", "No Render"),
            Map.entry("world_lyrics", "World Lyrics"),
            Map.entry("fast_swap", "FastSwap"),
            Map.entry("friends", "Friends"),
            Map.entry("menu", "Menu"));

    private Names() {}

    public static String of(Module m) {
        String n = (String) BY_ID.get(m.id);
        return n != null ? n : Lang.english(m.name);
    }
}
