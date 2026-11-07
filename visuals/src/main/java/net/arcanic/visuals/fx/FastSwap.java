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

import java.util.List;
import java.util.function.Predicate;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.UiSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Swaps one item for another in the chosen hand on a key press: totem ↔ shield, sword ↔ mace and so on. */
public final class FastSwap extends Module {
    private static final String OFF_HAND = "Левая рука";
    private static final String MAIN_HAND = "Правая рука";
    private static final int OFF_HAND_BUTTON = 40;

    private static final List<Kind> KINDS = List.of(
            new Kind("Тотем", s -> s.is(Items.TOTEM_OF_UNDYING)),
            new Kind("Щит", s -> s.is(Items.SHIELD)),
            new Kind("Золотое яблоко", s -> s.is(Items.GOLDEN_APPLE)),
            new Kind("Зачарованное яблоко", s -> s.is(Items.ENCHANTED_GOLDEN_APPLE)),
            new Kind("Шар (голова)", s -> s.is(Items.PLAYER_HEAD)),
            new Kind("Эндер-жемчуг", s -> s.is(Items.ENDER_PEARL)),
            new Kind("Фейерверк", s -> s.is(Items.FIREWORK_ROCKET)),
            new Kind("Меч", s -> s.is(ItemTags.SWORDS)),
            new Kind("Топор", s -> s.is(ItemTags.AXES)),
            new Kind("Булава", s -> s.is(Items.MACE)),
            new Kind("Трезубец", s -> s.is(Items.TRIDENT)),
            new Kind("Лук", s -> s.is(Items.BOW)),
            new Kind("Арбалет", s -> s.is(Items.CROSSBOW)),
            new Kind("Зелье", s -> s.is(Items.POTION) || s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)),
            new Kind("Еда", s -> s.has(DataComponents.FOOD)),
            new Kind("Блоки", s -> s.getItem() instanceof BlockItem));

    private final Rule[] rules = {
        new Rule(1, "Тотем", "Щит", OFF_HAND),
        new Rule(2, "Меч", "Булава", MAIN_HAND),
        new Rule(3, "Тотем", "Золотое яблоко", OFF_HAND)
    };

    private record Kind(String name, Predicate<ItemStack> matches) {}

    private final class Rule {
        final Setting.Bool on;
        final Setting.Mode first;
        final Setting.Mode second;
        final Setting.Mode hand;
        final Setting.Key key;

        Rule(int n, String a, String b, String side) {
            String[] names = KINDS.stream().map(Kind::name).toArray(String[]::new);
            on = add(new Setting.Bool("swap" + n, "Свап " + n, n == 1));
            first = add(new Setting.Mode("swap" + n + "_a", "Предмет", a, names))
                    .showIf(on::on);
            second = add(new Setting.Mode("swap" + n + "_b", "Меняется на", b, names))
                    .showIf(on::on);
            hand = add(new Setting.Mode("swap" + n + "_hand", "Рука", side, OFF_HAND, MAIN_HAND))
                    .showIf(on::on);
            key = add(new Setting.Key("swap" + n + "_key", "Клавиша")).showIf(on::on);
        }
    }

    public FastSwap() {
        super(
                "fast_swap",
                "FastSwap",
                "Меняет предмет в руке на другой по клавише: тотем на щит, меч на булаву. Пары, руку и клавиши настраиваешь сам — до трёх свапов.",
                Category.UTILITY,
                false);
    }

    /** Called for every key or mouse press outside screens. */
    public void pressed(int code) {
        if (!enabled() || code < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.screen != null || mc.player.isSpectator()) return;
        for (Rule r : rules) {
            if (r.on.on() && r.key.code() == code) swap(mc, mc.player, r);
        }
    }

    private void swap(Minecraft mc, LocalPlayer me, Rule r) {
        Kind a = kind(r.first.get()), b = kind(r.second.get());
        boolean off = r.hand.is(OFF_HAND);
        Inventory inv = me.getInventory();
        ItemStack held = off ? me.getOffhandItem() : me.getMainHandItem();

        Kind want = a.matches().test(held) ? b : a;
        int slot = find(inv, want, off ? -1 : inv.getSelectedSlot());
        if (slot < 0 && want == a && !b.matches().test(held)) {
            want = b;
            slot = find(inv, want, off ? -1 : inv.getSelectedSlot());
        }
        if (slot < 0) return;

        int menuSlot = slot < Inventory.getSelectionSize() ? 36 + slot : slot;
        int container = me.inventoryMenu.containerId;
        if (off) {
            mc.gameMode.handleInventoryMouseClick(container, menuSlot, OFF_HAND_BUTTON, ClickType.SWAP, me);
        } else if (slot < Inventory.getSelectionSize()) {
            inv.setSelectedSlot(slot);
        } else {
            mc.gameMode.handleInventoryMouseClick(container, menuSlot, inv.getSelectedSlot(), ClickType.SWAP, me);
        }
        UiSound.play("click", 1.25f, 0.35f);
    }

    /** First inventory slot (hotbar first) holding the item, skipping the hand it would replace. */
    private static int find(Inventory inv, Kind kind, int skip) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (i != skip && kind.matches().test(inv.getItem(i))) return i;
        }
        return -1;
    }

    private static Kind kind(String name) {
        for (Kind k : KINDS) if (k.name().equals(name)) return k;
        return KINDS.getFirst();
    }
}
