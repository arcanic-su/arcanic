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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public final class ItemCatalog {
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/holyworld-items.json");
    private static final Gson GSON =
            (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, JsonObject> known = new LinkedHashMap<>();
    private static final Map<String, String> tntIds = new LinkedHashMap<>();
    private static boolean loaded;
    private static boolean dirty;
    private static long savedAt;

    private ItemCatalog() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick) (mc) -> {
            if (mc.player != null && mc.level != null) {
                if (mc.level.getGameTime() % 10L == 0L && HolyEvents.onHolyWorld()) {
                    scan(mc);
                }

                if (dirty && System.currentTimeMillis() - savedAt > 3000L) {
                    save();
                }
            }
        });
    }

    private static void scan(Minecraft mc) {
        load();
        Inventory inv = mc.player.getInventory();

        for (int i = 0; i < inv.getContainerSize(); ++i) {
            see(inv.getItem(i), "инвентарь");
        }

        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            String where = mc.screen != null ? mc.screen.getTitle().getString() : "меню";

            for (Slot slot : mc.player.containerMenu.slots) {
                see(slot.getItem(), where);
            }
        }

        see(mc.player.containerMenu.getCarried(), "курсор");
    }

    public static void see(ItemStack s, String where) {
        if (s != null && !s.isEmpty()) {
            CustomData data = (CustomData) s.get(DataComponents.CUSTOM_DATA);
            if (data != null) {
                load();
                String item = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                String own = HolyItems.id(s);
                String custom = data.copyTag().toString();
                String key = item + "|"
                        + (own != null
                                ? own
                                : s.getHoverName().getString()
                                        + String.valueOf(data.copyTag().keySet()));
                String name = HolyEvents.plain(s.getHoverName().getString());
                HolyItems.Tnt byName = HolyItems.tntByName(s);
                if (byName != null && own != null && !byName.id().equals(tntIds.get(own))) {
                    tntIds.put(own, byName.id());
                    dirty = true;
                }

                if (!known.containsKey(key) && known.size() < 2000) {
                    JsonObject o = new JsonObject();
                    o.addProperty("item", item);
                    o.addProperty("name", name);
                    if (own != null) {
                        o.addProperty("id", own);
                    }

                    if (byName != null) {
                        o.addProperty("dynamite", byName.id());
                    }

                    o.addProperty("custom_data", custom);
                    ItemLore lore = (ItemLore) s.get(DataComponents.LORE);
                    if (lore != null) {
                        JsonArray lines = new JsonArray();

                        for (Component line : lore.lines()) {
                            lines.add(line.getString());
                        }

                        o.add("lore", lines);
                    }

                    o.addProperty("seen_in", where);
                    o.addProperty("key", key);
                    o.addProperty("seen_at", LocalDateTime.now().withNano(0).toString());
                    known.put(key, o);
                    dirty = true;
                }
            }
        }
    }

    public static String tntFor(String own) {
        if (own == null) {
            return null;
        } else {
            load();
            return (String) tntIds.get(own);
        }
    }

    private static void load() {
        if (!loaded) {
            loaded = true;

            try {
                if (!Files.exists(FILE, new LinkOption[0])) {
                    return;
                }

                JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (root.has("items")) {
                    for (JsonElement e : root.getAsJsonArray("items")) {
                        JsonObject o = e.getAsJsonObject();
                        String item = o.has("item") ? o.get("item").getAsString() : "";
                        String k = o.has("key") ? o.get("key").getAsString() : item + "|" + String.valueOf(o.get("id"));
                        known.put(k, o);
                    }
                }

                if (root.has("dynamite")) {
                    for (Map.Entry<String, JsonElement> e :
                            root.getAsJsonObject("dynamite").entrySet()) {
                        tntIds.put((String) e.getKey(), ((JsonElement) e.getValue()).getAsString());
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void save() {
        dirty = false;
        savedAt = System.currentTimeMillis();
        JsonObject root = new JsonObject();
        JsonObject dyn = new JsonObject();
        tntIds.forEach(dyn::addProperty);
        root.add("dynamite", dyn);
        JsonArray items = new JsonArray();
        known.values().forEach(items::add);
        root.add("items", items);

        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception var4) {
            dirty = true;
        }
    }
}
