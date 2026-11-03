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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.render.Px;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public final class HolyRules {
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/holyworld-rules.json");
    private static final Set<String> STUN_OFF = new HashSet<>();
    private static final Set<String> BLAST_OFF = new HashSet<>();
    private static boolean loaded;

    private HolyRules() {}

    private static void load() {
        if (!loaded) {
            loaded = true;

            try {
                if (!Files.exists(FILE, new LinkOption[0])) {
                    return;
                }

                JsonObject o = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (o.has("stun_off")) {
                    for (JsonElement e : o.getAsJsonArray("stun_off")) {
                        STUN_OFF.add(e.getAsString());
                    }
                }

                if (o.has("blast_off")) {
                    for (JsonElement e : o.getAsJsonArray("blast_off")) {
                        BLAST_OFF.add(e.getAsString());
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void save() {
        JsonObject o = new JsonObject();
        JsonArray s = new JsonArray();
        JsonArray b = new JsonArray();
        STUN_OFF.forEach(s::add);
        BLAST_OFF.forEach(b::add);
        o.add("stun_off", s);
        o.add("blast_off", b);

        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static boolean stunOffHere() {
        load();
        String here = Modules.HOLY_EVENTS.here();
        return here != null && STUN_OFF.contains(here);
    }

    public static boolean blastOffHere() {
        load();
        String here = Modules.HOLY_EVENTS.here();
        return here != null && BLAST_OFF.contains(here);
    }

    public static void scan() {
        Minecraft mc = Minecraft.getInstance();
        Screen var2 = mc.screen;
        if (var2 instanceof AbstractContainerScreen<?> s) {
            if (mc.player != null) {
                load();
                boolean changed = false;

                for (Slot slot : s.getMenu().slots) {
                    if (slot.container != mc.player.getInventory() && slot.hasItem()) {
                        CustomData data = (CustomData) slot.getItem().get(DataComponents.CUSTOM_DATA);
                        if (data != null) {
                            String tag = data.copyTag().toString();
                            int i = tag.indexOf("advancedserverselecter:server\":\"");
                            if (i >= 0) {
                                String srv = tag.substring(i + 32, tag.indexOf(34, i + 32));
                                String n = srv.replaceAll("\\D+", "");
                                if (!n.isEmpty() && srv.startsWith("lanarchy")) {
                                    String id = "LITE_ANARCHY_" + n;
                                    StringBuilder text = new StringBuilder();
                                    ItemLore lore = (ItemLore) slot.getItem().get(DataComponents.LORE);
                                    if (lore != null) {
                                        for (Component l : lore.lines()) {
                                            text.append(' ').append(l.getString());
                                        }
                                    }

                                    String low = text.toString().toLowerCase(Locale.ROOT);
                                    boolean off = low.contains("отключен");
                                    changed |= mark(STUN_OFF, id, off && low.contains("стан"));
                                    changed |= mark(BLAST_OFF, id, off && low.contains("взрывн"));
                                }
                            }
                        }
                    }
                }

                if (changed) {
                    save();
                }

                return;
            }
        }
    }

    private static boolean mark(Set<String> set, String id, boolean on) {
        return on ? set.add(id) : set.remove(id);
    }

    public static boolean safeHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        } else {
            String d = mc.level.dimension().identifier().toString();
            return d.contains("spawn") || d.contains("lobby") || d.contains("hub") || AnarchyJump.inHub();
        }
    }

    public static void note(GuiGraphics g, String text) {
        int fs = Math.max(11, Math.round((float) Px.height() / 1080.0F * 13.0F));
        int w = Px.textWidth(text, Px.Weight.SEMIBOLD, fs) + fs * 2;
        int h = Math.round((float) fs * 2.1F);
        int x = (Px.width() - w) / 2;
        int y = Px.height() / 2 + Math.round((float) fs * 3.2F);
        Px.round(g, x, y, w, h, h / 2, 0xE00B0B10);
        Px.outline(g, x, y, w, h, h / 2, 1627348332);
        Px.textCenter(g, text, Px.width() / 2, y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, -30060);
    }

    public static final class Use {
        private final int slot;
        private final ItemStack before;
        private final long at;
        private final boolean cooling;
        private long since;

        private Use(int slot, ItemStack before, long at, boolean cooling) {
            this.slot = slot;
            this.before = before;
            this.at = at;
            this.cooling = cooling;
        }

        public static Use of(InteractionHand hand) {
            LocalPlayer p = Minecraft.getInstance().player;
            int slot = hand == InteractionHand.OFF_HAND ? 40 : p.getInventory().getSelectedSlot();
            ItemStack item = p.getItemInHand(hand).copy();
            return new Use(
                    slot, item, System.currentTimeMillis(), p.getCooldowns().isOnCooldown(item));
        }

        public long at() {
            return this.at;
        }

        private boolean gone() {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) {
                return false;
            } else {
                ItemStack now = p.getInventory().getItem(this.slot);
                return now.isEmpty()
                        || now.getCount() < this.before.getCount()
                        || !ItemStack.isSameItem(now, this.before);
            }
        }

        public boolean taken() {
            if (this.cooling) {
                return false;
            } else if (!this.gone()) {
                this.since = 0L;
                return false;
            } else {
                long now = System.currentTimeMillis();
                if (this.since == 0L) {
                    this.since = now;
                }

                return now - this.since >= hold();
            }
        }

        private static long hold() {
            return (long) Math.min(900, Math.max(200, ping() * 3 / 2 + 100));
        }

        private static int ping() {
            Minecraft mc = Minecraft.getInstance();
            ClientPacketListener conn = mc.getConnection();
            PlayerInfo info = conn != null && mc.player != null ? conn.getPlayerInfo(mc.player.getUUID()) : null;
            return info == null ? 0 : Math.max(0, info.getLatency());
        }

        public boolean late() {
            return this.cooling
                    || System.currentTimeMillis() - this.at > Math.max(2000L, hold() + (long) ping() + 800L);
        }
    }
}
