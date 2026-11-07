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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.mixin.ContainerScreenAccess;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A "Сортировать" button over an open chest: its items get grouped by kind, moved one swap per tick through a free
 * hotbar slot the way a player would.
 */
public final class ChestSorter extends Module {
    private static final int MAX_STEPS = 250;
    private static final Comparator<ItemStack> ORDER = Comparator.<ItemStack>comparingInt(
                    s -> BuiltInRegistries.ITEM.getId(s.getItem()))
            .thenComparingInt(s -> s.getComponents().hashCode())
            .thenComparingInt(s -> -s.getCount());
    private boolean running;
    private int wait;
    private int steps;
    private int menuId = -1;
    private int hand = -1;
    private ItemStack handBefore = ItemStack.EMPTY;
    private final List<ItemStack> target = new ArrayList<>();

    public ChestSorter() {
        super(
                "chest_sorter",
                "Сортировка сундука",
                "Кнопка над сундуком раскладывает его по видам предметов",
                Category.UTILITY,
                false);
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> c) || screen instanceof InventoryScreen) return;
            if (!Modules.CHEST_SORTER.enabled()) return;
            ContainerScreenAccess a = (ContainerScreenAccess) c;
            int x = a.arcanicvisuals$left() + a.arcanicvisuals$width() - 64;
            int y = a.arcanicvisuals$top() - 18;
            Screens.getButtons(screen)
                    .add(Button.builder(Component.literal("Сортировать"), b -> Modules.CHEST_SORTER.toggle())
                            .bounds(x, y, 64, 16)
                            .build());
        });
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public void toggle() {
        if (this.running) this.stop();
        else this.start();
    }

    private void start() {
        LocalPlayer me = mc().player;
        if (me == null || mc().gameMode == null) return;
        AbstractContainerMenu menu = me.containerMenu;
        List<Slot> slots = chestSlots(menu, me);
        List<ItemStack> items = new ArrayList<>();
        for (Slot s : slots) if (!s.getItem().isEmpty()) items.add(s.getItem().copy());
        if (items.isEmpty()) return;
        items.sort(ORDER);
        this.target.clear();
        for (int i = 0; i < slots.size(); i++) this.target.add(i < items.size() ? items.get(i) : ItemStack.EMPTY);
        this.hand = freeHand(me);
        this.handBefore = me.getInventory().getItem(this.hand).copy();
        this.menuId = menu.containerId;
        this.running = true;
        this.steps = 0;
        this.wait = 0;
    }

    private void stop() {
        this.running = false;
        this.target.clear();
        this.menuId = -1;
        this.handBefore = ItemStack.EMPTY;
    }

    public void tick() {
        if (!this.running) return;
        LocalPlayer me = mc().player;
        AbstractContainerMenu menu = me == null ? null : me.containerMenu;
        if (mc().gameMode == null
                || menu == null
                || menu.containerId != this.menuId
                || !(mc().screen instanceof AbstractContainerScreen<?>)) {
            this.stop();
            return;
        }
        if (this.wait > 0) {
            this.wait--;
            return;
        }
        int slot = this.next(menu, me);
        if (slot < 0) {
            this.stop();
            return;
        }
        mc().gameMode.handleInventoryMouseClick(this.menuId, slot, this.hand, ClickType.SWAP, me);
        this.wait = ThreadLocalRandom.current().nextFloat() < 0.25F ? 1 : 0;
    }

    protected void onDisable() {
        this.stop();
    }

    /** The menu slot to swap with the hand slot next, or -1 when the chest is in order. */
    private int next(AbstractContainerMenu menu, LocalPlayer me) {
        if (++this.steps > MAX_STEPS) return -1;
        List<Slot> slots = chestSlots(menu, me);
        if (slots.size() != this.target.size()) return -1;
        ItemStack held = me.getInventory().getItem(this.hand);
        if (!held.isEmpty()) {
            for (int i = 0; i < slots.size(); i++) {
                if (!this.placed(slots, i) && same(held, this.target.get(i))) return slots.get(i).index;
            }
        }
        if (same(held, this.handBefore)) {
            for (int i = 0; i < slots.size(); i++) {
                if (!this.placed(slots, i) && !this.target.get(i).isEmpty()) {
                    int from = this.find(slots, this.target.get(i));
                    if (from >= 0) return slots.get(from).index;
                }
            }
            return -1;
        }
        if (held.isEmpty()) {
            for (int i = 0; i < slots.size(); i++) {
                if (!this.placed(slots, i) && same(slots.get(i).getItem(), this.handBefore)) return slots.get(i).index;
            }
        }
        return -1;
    }

    private int find(List<Slot> slots, ItemStack want) {
        for (int i = 0; i < slots.size(); i++) {
            if (!this.placed(slots, i) && same(slots.get(i).getItem(), want)) return i;
        }
        return -1;
    }

    private boolean placed(List<Slot> slots, int i) {
        return same(slots.get(i).getItem(), this.target.get(i));
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    private static List<Slot> chestSlots(AbstractContainerMenu menu, LocalPlayer me) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : menu.slots) if (s.container != me.getInventory()) out.add(s);
        return out;
    }

    /** A hotbar slot to carry items through: an empty one, else the off hand, else one not in use. */
    private static int freeHand(LocalPlayer me) {
        for (int i = 0; i <= 8; i++) if (me.getInventory().getItem(i).isEmpty()) return i;
        if (me.getInventory().getItem(40).isEmpty()) return 40;
        return me.getInventory().getSelectedSlot() == 0 ? 1 : 0;
    }
}
