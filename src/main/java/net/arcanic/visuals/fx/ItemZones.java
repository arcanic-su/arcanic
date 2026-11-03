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
package net.arcanic.visuals.fx;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.helper.HolyFeatures;

public final class ItemZones extends Module {
    public static final String TRAP = "trap";
    public static final String BLAST = "blast_trap";
    public static final String STUN = "stun";
    public static final String THING = "thing";
    public static final String TNT = "tnt";
    public static final String PLAIN = "plain_tnt";
    public final List<Item> items = new ArrayList<>();
    private final boolean funTime;

    private Item item(String key, String name, String icon, String group, boolean on, int colour) {
        Item it = new Item(key, name, icon, group, on, colour);
        this.items.add(it);
        return it;
    }

    public ItemZones() {
        super(
                "holy_items",
                "Радиус предметов",
                "Зоны, урон и таймеры предметов HolyWorld — каждый настраивается отдельно",
                Category.HOLYWORLD,
                true);
        this.funTime = false;
        String use = "Расходники";
        String boom = "Взрывчатка";
        Item t = this.item("trap", "Трапка", "minecraft:popped_chorus_fruit", use, true, -8020737);
        t.show(t.on, t.zone, t.players, t.caught, t.timer, t.trapBlast, t.glow);
        Item b = this.item("blast_trap", "Взрывная трапка", "minecraft:prismarine_shard", use, true, -23987);
        b.show(b.on, b.zone, b.players, b.caught, b.damage, b.timer, b.glow);
        Item s = this.item("stun", "Стан", "minecraft:nether_star", use, true, -5141249);
        s.show(s.on, s.zone, s.players, s.caught, s.timer, s.glow);
        Item th = this.item("thing", "Взрывная штучка", "minecraft:fire_charge", use, true, -34235);
        th.show(th.on, th.zone, th.players, th.hearts, th.glow);
        Item d = this.item("tnt", "Динамит", "minecraft:tnt", boom, true, -20163);
        d.show(d.on, d.hearts, d.timer);
        Item p = this.item("plain_tnt", "Обычный TNT", "minecraft:flint_and_steel", boom, false, -38339);
        p.show(p.on, p.hearts, p.timer);

        for (Item it : this.items) {
            for (Setting<?> st : it.all()) {
                this.add(st).showIf(() -> false);
            }
        }
    }

    /** FunTime's paid items, the same list and pages as HolyWorld's; FtHelper draws them. */
    private ItemZones(boolean funTime) {
        super(
                "ft_items",
                "Радиус предметов",
                "Зоны и таймеры предметов FunTime — каждый настраивается отдельно",
                Category.FUNTIME,
                true);
        this.funTime = funTime;
        String use = "Расходники";
        String tools = "Инструменты";
        Item t = this.item("trap", "Трапка", "minecraft:netherite_scrap", use, true, -8020737);
        t.show(t.on, t.zone, t.caught, t.timer, t.dragon);
        Item p = this.item("plast", "Пласт", "minecraft:dried_kelp", use, true, -11141291);
        p.show(p.on, p.zone, p.caught, p.timer, p.dragon);
        Item d = this.item("disorient", "Дезориентация", "minecraft:ender_eye", use, true, -5141249);
        d.show(d.on, d.zone, d.caught);
        Item s = this.item("dust", "Явная пыль", "minecraft:sugar", use, true, -1);
        s.show(s.on, s.zone, s.caught);
        Item b = this.item("snowball", "Снежок заморозки", "minecraft:snowball", use, true, -7351297);
        b.show(b.on, b.zone, b.caught);
        Item drill = this.item("drill", "Бур", "minecraft:netherite_pickaxe", tools, true, -20163);
        drill.show(drill.on, drill.zone);

        for (Item it : this.items) {
            for (Setting<?> st : it.all()) {
                this.add(st).showIf(() -> false);
            }
        }
    }

    public static ItemZones funTime() {
        return new ItemZones(true);
    }

    public Item item(String key) {
        for (Item it : this.items) {
            if (it.key.equals(key)) {
                return it;
            }
        }

        throw new IllegalArgumentException(key);
    }

    public boolean enabled() {
        return true;
    }

    public int shownCount() {
        int n = 0;

        for (Item it : this.items) {
            if (it.on.on()) {
                ++n;
            }
        }

        return n;
    }

    public boolean on(String item, Module part) {
        return this.item(item).on.on() && !HolyFeatures.hidden(part);
    }

    public void tick() {
        if (this.funTime) {
            Modules.FT_HELPER.tick();
            return;
        }
        for (Module part : Modules.ITEM_PARTS) {
            if (part.enabled()) {
                try {
                    part.tick();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    public void migrate(JsonObject mods) {
        String[][] old = new String[][] {
            {"holy_traps", "trap"},
            {"holy_blast_trap", "blast_trap"},
            {"holy_stun", "stun"},
            {"holy_thing", "thing"},
            {"holy_tnt", "tnt"}
        };
        boolean any = false;

        for (String[] o : old) {
            if (mods.has(o[0])) {
                any = true;
                Item it = this.item(o[1]);
                JsonObject m = mods.getAsJsonObject(o[0]);

                try {
                    if (m.has("enabled")) {
                        it.on.set(m.get("enabled").getAsBoolean());
                    }

                    if (m.has("settings")) {
                        JsonObject s = m.getAsJsonObject("settings");
                        if (s.has("zone_color")) {
                            it.zone.load(s.get("zone_color"));
                        }

                        if (o[1].equals("tnt") && s.has("plain")) {
                            this.item("plain_tnt").on.set(s.get("plain").getAsBoolean());
                        }
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }

        if (any) {
            Config.dirty();
        }
    }

    public static final class Item {
        public final String key;
        public final String name;
        public final String icon;
        public final String group;
        public final Setting.Bool on;
        public final Setting.Bool players;
        public final Setting.Bool damage;
        public final Setting.Bool hearts;
        public final Setting.Bool timer;
        public final Setting.Bool death;
        public final Setting.Bool trapBlast;
        public final Setting.Bool glow;
        public final Setting.Bool dragon;
        public final Setting.Colour zone;
        public final Setting.Colour caught;
        public final List<Setting<?>> shown = new ArrayList<>();

        Item(String key, String name, String icon, String group, boolean on, int colour) {
            this.key = key;
            this.name = name;
            this.icon = icon;
            this.group = group;
            this.on = new Setting.Bool(key + ".on", "Показывать", on);
            this.zone = new Setting.Colour(key + ".zone", "Цвет зоны", false, colour);
            this.caught = new Setting.Colour(key + ".hit", "Цвет попавших", false, -45730);
            this.players = new Setting.Bool(key + ".players", "Подсвечивать игроков", true);
            this.damage = new Setting.Bool(key + ".damage", "Урон над головой", true);
            this.hearts = new Setting.Bool(key + ".hearts", "Урон в сердцах", true);
            this.timer = new Setting.Bool(key + ".timer", "Таймер", true);
            this.death = new Setting.Bool(key + ".death", "Линия смерти", true);
            this.trapBlast = new Setting.Bool(key + ".blast", "Радиус урона трапки", false);
            this.glow = new Setting.Bool(key + ".glow", "Свечение", true);
            this.dragon = new Setting.Bool(key + ".dragon", "Зона драконьей трапки", false);
        }

        Item show(Setting<?>... s) {
            this.shown.addAll(List.of(s));
            return this;
        }

        List<Setting<?>> all() {
            return List.of(
                    this.on,
                    this.zone,
                    this.caught,
                    this.players,
                    this.damage,
                    this.hearts,
                    this.timer,
                    this.death,
                    this.trapBlast,
                    this.glow,
                    this.dragon);
        }
    }
}
