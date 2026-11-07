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

import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.mixin.ContainerScreenAccess;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class ItemScroller extends Module {
    private final Setting.Bool wheel = this.add(new Setting.Bool("wheel", "Колёсико: по одному предмету", true));
    private final Setting.Bool drag = this.add(new Setting.Bool("drag", "Shift + ЛКМ протяжкой", true));
    private Slot lastDragged;

    public ItemScroller() {
        super(
                "item_scroller",
                "Item Scroller",
                "Колёсико над предметом перекладывает его по одному, Shift — стаком, Shift+ЛКМ протяжкой — все стаки",
                Category.WORLD,
                false);
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((ScreenEvents.AfterInit) (client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> s) {
                if (!(screen instanceof CreativeModeInventoryScreen)) {
                    ScreenMouseEvents.allowMouseScroll(screen).register((ScreenMouseEvents.AllowMouseScroll)
                            (sc, mx, my, hz, v) -> Modules.ITEM_SCROLLER.scroll(s, v));
                    ScreenMouseEvents.allowMouseDrag(screen).register((ScreenMouseEvents.AllowMouseDrag)
                            (sc, e, dx, dy) -> Modules.ITEM_SCROLLER.drag(s));
                    ScreenMouseEvents.beforeMouseRelease(screen).register((ScreenMouseEvents.BeforeMouseRelease)
                            (sc, e) -> Modules.ITEM_SCROLLER.lastDragged = null);
                    return;
                }
            }
        });
    }

    private static boolean shift() {
        long win = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(win, 340) == 1 || GLFW.glfwGetKey(win, 344) == 1;
    }

    private boolean scroll(AbstractContainerScreen<?> s, double v) {
        if (this.enabled() && this.wheel.on() && v != 0.0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null
                    && mc.gameMode != null
                    && s.getMenu().getCarried().isEmpty()) {
                Slot at = ((ContainerScreenAccess) s).arcanicvisuals$hovered();
                if (at != null && (at.hasItem() || !(v < 0.0))) {
                    if (v < 0.0) {
                        if (shift()) {
                            click(s, at, 0, ClickType.QUICK_MOVE);
                        } else {
                            moveOne(s, at, target(s, at, at.getItem()));
                        }
                    } else {
                        ItemStack want = at.getItem();
                        if (want.isEmpty() || want.getCount() >= want.getMaxStackSize()) {
                            return false;
                        }

                        Slot from = source(s, at, want);
                        if (from != null) {
                            if (shift()) {
                                click(s, from, 0, ClickType.QUICK_MOVE);
                            } else {
                                moveOne(s, from, at);
                            }
                        }
                    }

                    return false;
                } else {
                    return true;
                }
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    private boolean drag(AbstractContainerScreen<?> s) {
        if (this.enabled() && this.drag.on() && shift()) {
            long win = Minecraft.getInstance().getWindow().handle();
            if (GLFW.glfwGetMouseButton(win, 0) != 1) {
                return true;
            } else if (!s.getMenu().getCarried().isEmpty()) {
                return true;
            } else {
                Slot at = ((ContainerScreenAccess) s).arcanicvisuals$hovered();
                if (at != null
                        && at != this.lastDragged
                        && at.hasItem()
                        && at.mayPickup(Minecraft.getInstance().player)) {
                    click(s, at, 0, ClickType.QUICK_MOVE);
                }

                this.lastDragged = at;
                return true;
            }
        } else {
            return true;
        }
    }

    private static void moveOne(AbstractContainerScreen<?> s, Slot from, Slot to) {
        if (from != null && to != null && from != to && from.hasItem()) {
            click(s, from, 0, ClickType.PICKUP);
            click(s, to, 1, ClickType.PICKUP);
            if (!s.getMenu().getCarried().isEmpty()) {
                click(s, from, 0, ClickType.PICKUP);
            }
        }
    }

    private static void click(AbstractContainerScreen<?> s, Slot slot, int button, ClickType type) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(s.getMenu().containerId, slot.index, button, type, mc.player);
    }

    private static int side(AbstractContainerScreen<?> s, Slot slot) {
        boolean mine = slot.container instanceof Inventory;
        if (s instanceof InventoryScreen) {
            if (!mine) {
                return -1;
            } else {
                int i = slot.getContainerSlot();
                return i < 9 ? 0 : (i < 36 ? 1 : -1);
            }
        } else {
            return mine ? 1 : 0;
        }
    }

    private static Slot target(AbstractContainerScreen<?> s, Slot from, ItemStack item) {
        int side = side(s, from);
        if (side < 0) {
            return null;
        } else {
            Slot empty = null;

            for (Slot t : s.getMenu().slots) {
                if (t != from && side(s, t) == 1 - side && t.mayPlace(item)) {
                    ItemStack has = t.getItem();
                    if (has.isEmpty()) {
                        if (empty == null) {
                            empty = t;
                        }
                    } else if (ItemStack.isSameItemSameComponents(has, item)
                            && has.getCount() < Math.min(has.getMaxStackSize(), t.getMaxStackSize(has))) {
                        return t;
                    }
                }
            }

            return empty;
        }
    }

    private static Slot source(AbstractContainerScreen<?> s, Slot to, ItemStack item) {
        int side = side(s, to);
        if (side < 0) {
            return null;
        } else {
            for (Slot t : s.getMenu().slots) {
                if (t != to
                        && side(s, t) == 1 - side
                        && t.hasItem()
                        && ItemStack.isSameItemSameComponents(t.getItem(), item)
                        && t.mayPickup(Minecraft.getInstance().player)) {
                    return t;
                }
            }

            return null;
        }
    }
}
