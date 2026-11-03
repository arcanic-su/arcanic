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

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.gui.EnchantPickerScreen;
import net.arcanic.visuals.mixin.ContainerScreenAccess;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class EnchantSearch extends Module {
    public final Setting.Sizes chosen = this.add(new Setting.Sizes("chosen", "Зачарования"));
    final Setting.Action pick = this.add(new Setting.Action("pick", "Зачарования", "Выбрать", () -> openPicker(this)));
    public final Setting.Mode match = this.add(
            new Setting.Mode("match", "Совпадение", "Любое из них", new String[] {"Любое из них", "Все сразу"}));
    final Setting.Bool everywhere =
            this.add(new Setting.Bool("everywhere", "Во всех сундуках, не только в /ah", false));
    final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет подсветки", true));
    public static final List<Custom> CUSTOM = List.of(
            new Custom("Непробиваемый", 2, "impenetrable"),
            new Custom("Лаваход", 2, "lava"),
            new Custom("Магнетизм", 1, "magnet"),
            new Custom("Бур", 2, "drill"),
            new Custom("Мега-бур", 1, "mega-drill"),
            new Custom("Автоплавка", 1, "foundry"),
            new Custom("Опытный", 3, "exp"),
            new Custom("Неразрушимость", 1, "internal"),
            new Custom("Дровосек", 3, "lumberjack"),
            new Custom("Деликатный", 3, "delicate"),
            new Custom("Фермер", 5, "farmer"),
            new Custom("Посев", 4, "replant"),
            new Custom("Фильтр", 1, "filter"),
            new Custom("Крушитель", 7, "crusher"),
            new Custom("Разрушитель", 2, "destroyer"),
            new Custom("Критический", 2, "critical"),
            new Custom("Богач", 5, "rich"),
            new Custom("Самонаводка", 4, "homing"),
            new Custom("Оглушение", 2, "stun"),
            new Custom("Фармер", 13, "mob-farmer"));
    /** FunTime's own enchantments, read from the lore ("Вампиризм II"); types starting with ft- name the icon. */
    public static final List<Custom> FT_CUSTOM = List.of(
            new Custom("Бульдозер", 2, "ft-pick"),
            new Custom("Мега-Бульдозер", 1, "ft-pick"),
            new Custom("Опытный", 3, "ft-pick"),
            new Custom("Магнит", 1, "ft-pick"),
            new Custom("Авто-Плавка", 1, "ft-pick"),
            new Custom("Паутина", 1, "ft-pick"),
            new Custom("Пингер", 1, "ft-pick"),
            new Custom("Вампиризм", 2, "ft-sword"),
            new Custom("Окисление", 2, "ft-sword"),
            new Custom("Яд", 3, "ft-sword"),
            new Custom("Детекция", 3, "ft-sword"),
            new Custom("Ступор", 3, "ft-sword"),
            new Custom("Скаут", 3, "ft-armor"),
            new Custom("Притяжение", 3, "ft-sword"),
            new Custom("Подрывник", 3, "ft-pick"));

    private final List<Custom> customs;
    private final boolean funTime;

    public EnchantSearch() {
        super(
                "enchant_search",
                "Поиск по зачарованиям",
                "Выбери зачарования — в /ah подсветятся предметы с ними",
                Category.HOLYWORLD,
                true);
        this.chosen.showIf(() -> false);
        this.customs = CUSTOM;
        this.funTime = false;
    }

    /** The same search for FunTime's auction, with FunTime's own enchantments. */
    private EnchantSearch(boolean funTime) {
        super(
                "ft_enchant_search",
                "Поиск по зачарованиям",
                "Выбери зачарования — на аукционе FunTime подсветятся предметы с ними",
                Category.FUNTIME,
                true);
        this.chosen.showIf(() -> false);
        this.customs = FT_CUSTOM;
        this.funTime = funTime;
    }

    public static EnchantSearch funTime() {
        return new EnchantSearch(true);
    }

    public List<Custom> customs() {
        return this.customs;
    }

    private static void openPicker(EnchantSearch search) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new EnchantPickerScreen(mc.screen, search));
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((ScreenEvents.AfterInit) (client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> s) {
                if (!(screen instanceof CreativeModeInventoryScreen)) {
                    ScreenEvents.afterRender(screen).register((ScreenEvents.AfterRender) (sc, g, mx, my, dt) -> {
                        Modules.ENCHANT_SEARCH.overlay(s, g);
                        Modules.FT_ENCHANT_SEARCH.overlay(s, g);
                    });
                    return;
                }
            }
        });
    }

    public static String id(Holder<Enchantment> h) {
        return (String) h.unwrapKey().map((k) -> k.identifier().toString()).orElse("");
    }

    private boolean auction(AbstractContainerScreen<?> s) {
        if (this.everywhere.on()) {
            return true;
        } else {
            String t = s.getTitle().getString().toLowerCase(Locale.ROOT);
            return t.contains("аукцион")
                    || t.contains("auction")
                    || t.contains("аук")
                    || t.contains("маркет")
                    || t.contains("рынок")
                    || t.contains("/ah");
        }
    }

    public static int customLevel(ItemStack st, Custom c) {
        int best = 0;
        CustomData data = (CustomData) st.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            Optional<ListTag> list = data.copyTag()
                    .getCompound("PublicBukkitValues")
                    .flatMap((tx) -> tx.getList("minecraft:custom-enchantments"));
            if (list.isPresent()) {
                for (int i = 0; i < ((ListTag) list.get()).size(); ++i) {
                    CompoundTag en = ((ListTag) list.get()).getCompoundOrEmpty(i);
                    String type = en.getStringOr("minecraft:type", "").toLowerCase(Locale.ROOT);
                    if (!type.isEmpty() && !c.type().startsWith("ft-") && typeIs(type, c)) {
                        best = Math.max(best, Math.max(1, en.getIntOr("minecraft:level", 1)));
                    }
                }
            }
        }

        ItemLore lore = (ItemLore) st.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                String t = ChatFormatting.stripFormatting(line.getString());
                if (t != null) {
                    t = t.trim();
                    if (t.regionMatches(true, 0, c.name(), 0, c.name().length())) {
                        String rest = t.substring(c.name().length()).trim();
                        int lv = rest.isEmpty() ? 1 : fromRoman(rest);
                        if (lv > 0) {
                            best = Math.max(best, lv);
                        }
                    }
                }
            }
        }

        return best;
    }

    private static boolean typeIs(String type, Custom c) {
        String t = type.replace("-enchant-custom", "").replace("-enchant", "");
        boolean var10000;
        switch (c.type()) {
            case "drill" -> var10000 = t.contains("drill") && !t.contains("mega");
            case "farmer" -> var10000 = t.contains("farmer") && !t.contains("mob");
            default -> var10000 = t.contains(c.type());
        }

        return var10000;
    }

    private static int fromRoman(String r) {
        if (r.matches("\\d{1,2}")) {
            return Integer.parseInt(r);
        } else {
            for (int i = 1; i <= 13; ++i) {
                if (roman(i).equalsIgnoreCase(r)) {
                    return i;
                }
            }

            return 0;
        }
    }

    private static int level(ItemStack st, String want) {
        if (want.startsWith("hw:") || want.startsWith("ft:")) {
            for (Custom c : want.startsWith("ft:") ? FT_CUSTOM : CUSTOM) {
                if (c.key().equals(want)) {
                    return customLevel(st, c);
                }
            }
        }

        int best = 0;

        for (ItemEnchantments e : new ItemEnchantments[] {
            (ItemEnchantments) st.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY),
            (ItemEnchantments) st.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
        }) {
            for (Object2IntMap.Entry<Holder<Enchantment>> en : e.entrySet()) {
                if (id((Holder) en.getKey()).equals(want)) {
                    best = Math.max(best, en.getIntValue());
                }
            }
        }

        return best;
    }

    public boolean matches(ItemStack st) {
        Map<String, Double> want = (Map) this.chosen.get();
        if (!st.isEmpty() && !want.isEmpty()) {
            boolean all = this.match.is("Все сразу");

            for (Map.Entry<String, Double> w : want.entrySet()) {
                boolean has = (double) level(st, (String) w.getKey()) >= Math.max(1.0, (Double) w.getValue());
                if (has && !all) {
                    return true;
                }

                if (!has && all) {
                    return false;
                }
            }

            return all;
        } else {
            return false;
        }
    }

    void overlay(AbstractContainerScreen<?> s, GuiGraphics g) {
        if (this.enabled()
                && this.funTime == FunTime.here()
                && !((Map) this.chosen.get()).isEmpty()
                && this.auction(s)) {
            ContainerScreenAccess acc = (ContainerScreenAccess) s;
            int left = acc.arcanicvisuals$left();
            int top = acc.arcanicvisuals$top();
            int col = this.colour.argb() | 0xFF000000;
            Px.begin(g);

            try {
                float gs = (float) Px.guiScale();
                int sz = Math.round(16.0F * gs);
                int r = Math.round(3.0F * gs);

                for (Slot slot : s.getMenu().slots) {
                    if (!(slot.container instanceof Inventory) && slot.hasItem()) {
                        int x = Math.round((float) (left + slot.x) * gs);
                        int y = Math.round((float) (top + slot.y) * gs);
                        if (this.matches(slot.getItem())) {
                            int gl = Math.round(10.0F * gs);
                            Px.glow(g, x - gl / 2, y - gl / 2, sz + gl, sz + gl, Px.a(col, 120));
                            Px.round(g, x, y, sz, sz, r, Px.a(col, 60));
                            Px.outline(g, x, y, sz, sz, r, Px.a(col, 230));
                        }
                    }
                }
            } finally {
                Px.end(g);
            }
        }
    }

    public String summary() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder();
        int n = 0;

        for (Map.Entry<String, Double> w : ((Map<String, Double>) this.chosen.get()).entrySet()) {
            if (n == 3) {
                sb.append(" и ещё ").append(((Map) this.chosen.get()).size() - 3);
                break;
            }

            if (n > 0) {
                sb.append(", ");
            }

            sb.append(name((String) w.getKey()));
            int lv = (int) Math.round((Double) w.getValue());
            if (lv > 1) {
                sb.append(' ').append(roman(lv)).append('+');
            }

            ++n;
        }

        return sb.toString();
    }

    public static String name(String id) {
        if (id.startsWith("hw:") || id.startsWith("ft:")) {
            return id.substring(3);
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return id;
            } else {
                Registry<Enchantment> reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

                for (Holder.Reference<Enchantment> h : reg.listElements().toList()) {
                    if (id(h).equals(id)) {
                        return ((Enchantment) h.value()).description().getString();
                    }
                }

                return id;
            }
        }
    }

    public static String roman(int n) {
        String[] r = new String[] {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    public static record Custom(String name, int max, String type) {
        public String key() {
            return (this.type.startsWith("ft-") ? "ft:" : "hw:") + this.name;
        }
    }
}
