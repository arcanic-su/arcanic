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

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public final class AnarchyJump {
    private static final Path LOG = FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/jump.txt");
    private static final String[] KINDS = {"solo", "duo", "trio", "clans"};
    private static State state;
    private static String target;
    private static String title;
    private static String kind;
    private static String number;
    private static boolean newer;
    private static boolean joining;
    private static boolean escWas;
    private static long started;
    private static long stepAt;
    private static long endedAt;
    private static Object world;
    private static String step;
    private static int opens;
    private static int kindClicks;
    private static final Set<String> clicked;
    private static final List<String> notes;

    private AnarchyJump() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static boolean busy() {
        return state == AnarchyJump.State.HUB || state == AnarchyJump.State.OPENING || state == AnarchyJump.State.MENU;
    }

    public static void start(String server, String name) {
        Minecraft mc = mc();
        if (mc.player != null) {
            if (server.equals(Modules.HOLY_EVENTS.here())) {
                target = server;
                title = name == null ? server : name;
                started = System.currentTimeMillis();
                done("Ты уже здесь · " + title);
            } else {
                target = server;
                title = name == null ? server : name;
                String low = title.toLowerCase(Locale.ROOT);
                kind = low.contains("соло")
                        ? "solo"
                        : (low.contains("дуо")
                                ? "duo"
                                : (low.contains("трио") ? "trio" : (low.contains("клан") ? "clans" : null)));
                newer = server.contains("NEW") || low.contains("1.20");
                String n = server.replaceAll("\\D+", "");
                number = n.isEmpty() ? null : n;
                started = stepAt = System.currentTimeMillis();
                world = mc.level;
                joining = false;
                escWas = true;
                opens = 0;
                kindClicks = 0;
                clicked.clear();
                notes.clear();
                notes.add("target " + server + " / " + title + " / kind " + kind + " / newer " + newer);
                if (mc.screen instanceof AbstractContainerScreen) {
                    state = AnarchyJump.State.MENU;
                } else if (serverMenuSlot() >= 0) {
                    state = AnarchyJump.State.OPENING;
                } else {
                    state = AnarchyJump.State.HUB;
                    step = "Перехожу в лобби";
                    send("hub");
                }

                if (state != AnarchyJump.State.HUB) {
                    step = "Открываю выбор режима";
                }

                UiSound.play("select");
            }
        }
    }

    private static void send(String command) {
        ClientPacketListener conn = mc().getConnection();
        if (conn != null) {
            conn.sendCommand(command);
        }

        notes.add("/" + command);
    }

    private static void done(String what) {
        Modules.MARKS.arrived();
        state = AnarchyJump.State.DONE;
        step = what;
        endedAt = System.currentTimeMillis();
        UiSound.play("charge");
    }

    private static void fail(String why) {
        state = AnarchyJump.State.FAILED;
        step = why;
        endedAt = System.currentTimeMillis();
        notes.add("failed: " + why);

        try {
            Files.createDirectories(LOG.getParent());
            Files.writeString(
                    LOG,
                    "# Arcanic jump, " + LocalDateTime.now() + "\n" + String.join("\n", notes) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }

        UiSound.play("drain");
    }

    public static boolean inHub() {
        return serverMenuSlot() >= 0;
    }

    private static int serverMenuSlot() {
        LocalPlayer p = mc().player;
        if (p == null) {
            return -1;
        } else {
            for (int i = 0; i < 9; i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (!s.isEmpty()
                        && (data(s).contains("server-menu")
                                || HolyEvents.plain(s.getHoverName().getString())
                                        .toLowerCase(Locale.ROOT)
                                        .contains("выбор режима"))) {
                    return i;
                }
            }

            return -1;
        }
    }

    private static String data(ItemStack s) {
        CustomData d = (CustomData) s.get(DataComponents.CUSTOM_DATA);
        return d == null ? "" : d.copyTag().toString();
    }

    private static String lore(ItemStack s) {
        StringBuilder b = new StringBuilder(HolyEvents.plain(s.getHoverName().getString()));
        ItemLore lore = (ItemLore) s.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                b.append('\n').append(HolyEvents.plain(line.getString()));
            }
        }

        return b.toString().toLowerCase(Locale.ROOT);
    }

    public static void tick() {
        Minecraft mc = mc();
        if (busy()) {
            long now = System.currentTimeMillis();
            if (mc.player != null && mc.level != null) {
                boolean esc = mc.screen == null && InputConstants.isKeyDown(mc.getWindow(), 256);
                if (esc && !escWas) {
                    fail("Отменено");
                } else {
                    escWas = esc;
                    if (mc.level != world) {
                        world = mc.level;
                        if (joining) {
                            done("Готово · " + title);
                            return;
                        }

                        if (state == AnarchyJump.State.HUB) {
                            state = AnarchyJump.State.OPENING;
                            step = "Открываю выбор режима";
                            stepAt = now + 800L;
                            return;
                        }
                    }

                    if (now - started > 45000L) {
                        fail("Не получилось перейти");
                    } else if (now >= stepAt + 350L) {
                        switch (state) {
                            case HUB:
                                if (now - stepAt > 6000L) {
                                    state = AnarchyJump.State.OPENING;
                                    stepAt = now;
                                }
                                break;
                            case OPENING:
                                if (mc.screen instanceof AbstractContainerScreen) {
                                    state = AnarchyJump.State.MENU;
                                    return;
                                }

                                if (mc.screen != null) {
                                    return;
                                }

                                int slot = serverMenuSlot();
                                if (slot < 0) {
                                    if (now - stepAt > 5000L) {
                                        fail("Нет компаса «Выбор режима»");
                                    }

                                    return;
                                }

                                if (opens++ > 3) {
                                    fail("Выбор режима не открылся");
                                    return;
                                }

                                mc.player.getInventory().setSelectedSlot(slot);
                                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                                notes.add("compass in slot " + slot);
                                state = AnarchyJump.State.MENU;
                                stepAt = now;
                                break;
                            case MENU:
                                Screen screen2 = mc.screen;
                                if (screen2 instanceof AbstractContainerScreen) {
                                    AbstractContainerScreen<?> s = (AbstractContainerScreen) screen2;
                                    choose(s, now);
                                } else if (now - stepAt > 3000L && !joining) {
                                    state = AnarchyJump.State.OPENING;
                                    stepAt = now;
                                }
                        }
                    }
                }
            }
        }
    }

    private static void click(AbstractContainerScreen<?> s, Slot slot, String what, long now) {
        AbstractContainerMenu menu = s.getMenu();
        clicked.add(menu.containerId + ":" + slot.index);
        mc().gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.PICKUP, mc().player);
        notes.add("clicked [" + slot.index + "] " + what);
        stepAt = now;
    }

    private static void choose(AbstractContainerScreen<?> s, long now) {
        Minecraft mc = mc();
        AbstractContainerMenu menu = s.getMenu();
        String id = number == null ? null : "lanarchy" + number;
        Slot server = null;
        Slot category = null;
        Slot lite = null;
        List<Slot> kinds = new ArrayList<>();
        StringBuilder seen =
                new StringBuilder("menu \"" + HolyEvents.plain(s.getTitle().getString()) + "\":");

        for (Slot slot : menu.slots) {
            if (slot.container != mc.player.getInventory() && slot.hasItem()) {
                ItemStack st = slot.getItem();
                String d = data(st);
                String l = lore(st);
                seen.append("\n  [")
                        .append(slot.index)
                        .append("] ")
                        .append(l.split("\n")[0])
                        .append(" ")
                        .append(d);
                boolean done = clicked.contains(menu.containerId + ":" + slot.index);
                if (d.contains("advancedserverselecter:server\"")
                        && (!newer && id != null && d.contains("\"" + id + "\"")
                                || newer && number != null && l.matches("(?s).*#\\s*" + number + "(\\D.*|$)"))) {
                    server = slot;
                }

                if (d.contains("server-type")) {
                    if (kind != null && d.contains("\"" + kind + "\"") && !done) {
                        category = slot;
                    }

                    if (!done) {
                        kinds.add(slot);
                    }
                }

                if (l.contains("лайт") && l.contains(newer ? "1.20" : "1.16") && !done) {
                    lite = slot;
                }
            }
        }

        if (notes.size() < 40) {
            notes.add(seen.toString());
        }

        if (server != null) {
            click(s, server, "the anarchy", now);
            joining = true;
            step = "Подключаюсь · " + title;
        } else if (category != null && kindClicks < 3) {
            ++kindClicks;
            click(s, category, "kind " + kind, now);
            step = "Открываю " + kindName();
        } else if (kind == null && !kinds.isEmpty() && kindClicks++ < 6) {
            click(s, (Slot) kinds.get(0), "a kind, looking", now);
            step = "Ищу анархию";
        } else if (lite != null) {
            click(s, lite, "Lite", now);
            step = "Открываю Лайт анархии";
        } else if (now - stepAt > 2500L) {
            fail("Не нашёл анархию в меню");
            mc.setScreen((Screen) null);
        }
    }

    private static String kindName() {
        return switch (kind == null ? "" : kind) {
            case "solo" -> "СолоЛайт";
            case "duo" -> "ДуоЛайт";
            case "trio" -> "ТриоЛайт";
            case "clans" -> "КланЛайт";
            default -> "анархии";
        };
    }

    public static void hud(GuiGraphics g) {
        if (state != AnarchyJump.State.IDLE) {
            long now = System.currentTimeMillis();
            float out = state != AnarchyJump.State.DONE && state != AnarchyJump.State.FAILED
                    ? 0.0F
                    : Math.min(1.0F, (float) (now - endedAt - 2200L) / 350.0F);
            if (out >= 1.0F) {
                state = AnarchyJump.State.IDLE;
            } else {
                float in = Math.min(1.0F, (float) (now - started) / 300.0F);
                float o = Math.max(0.0F, out);
                float k = (float) ((double) Px.height() / 1080.0);
                int w = Math.round(320.0F * k);
                int h = Math.round(58.0F * k);
                int r = Math.round(12.0F * k);
                int x = Px.width()
                        - w
                        - Math.round(20.0F * k)
                        + Math.round((1.0F - Anim.easeOutBack(in) + o * o) * ((float) w + 40.0F * k));
                int y = Px.height() / 2 - h / 2 - Math.round(140.0F * k);
                int acc2 = Theme.accent2() | 0xFF000000;
                int tone = state == AnarchyJump.State.FAILED
                        ? -41620
                        : (state == AnarchyJump.State.DONE ? 0xFF7BE0A0 : Theme.accent() | 0xFF000000);
                float old = Px.fade;
                Px.fade = old * Math.min(1.0F, in * 1.5F) * (1.0F - o);
                Px.shadow(g, x, y + Math.round(4.0F * k), w, h, Math.round(18.0F * k), 1879048192);
                Px.round(g, x, y, w, h, r, 0xEB0B0B12);
                Px.outline(g, x, y, w, h, r, Px.a(tone & 0xFFFFFF, 150));
                int is = Math.round(26.0F * k);
                int ix = x + Math.round(16.0F * k);
                int iy = y + (h - is) / 2;
                if (busy()) {
                    float t = (float) (now % 900L) / 900.0F;

                    for (int i = 0; i < 8; i++) {
                        double a = (Math.PI * 2D) * (double) i / 8.0;
                        float lit = 1.0F - ((float) i / 8.0F - t + 1.0F) % 1.0F;
                        int ds = Math.max(2, Math.round(4.0F * k));
                        int dx = ix + is / 2 + (int) Math.round(Math.cos(a) * (double) is * 0.4) - ds / 2;
                        int dy = iy + is / 2 + (int) Math.round(Math.sin(a) * (double) is * 0.4) - ds / 2;
                        Px.round(g, dx, dy, ds, ds, ds / 2, Px.a(acc2 & 0xFFFFFF, 60 + Math.round(195.0F * lit * lit)));
                    }
                } else {
                    Px.icon(g, state == AnarchyJump.State.DONE ? "check" : "x", ix, iy, is, tone);
                }

                int tx = ix + is + Math.round(14.0F * k);
                int tw = x + w - Math.round(14.0F * k) - tx;
                int fs1 = Math.round(12.0F * k);
                int fs2 = Math.round(14.0F * k);
                Px.text(
                        g,
                        Px.ellipsize("Переход · " + title, Px.Weight.MEDIUM, fs1, tw),
                        tx,
                        y + Math.round(11.0F * k),
                        Px.Weight.MEDIUM,
                        fs1,
                        0xFF9AA0B4);
                Px.text(
                        g,
                        Px.ellipsize(step, Px.Weight.SEMIBOLD, fs2, tw),
                        tx,
                        y + Math.round(29.0F * k),
                        Px.Weight.SEMIBOLD,
                        fs2,
                        0xFFF4F6FA);
                Px.fade = old;
            }
        }
    }

    static {
        state = AnarchyJump.State.IDLE;
        step = "";
        clicked = new HashSet<>();
        notes = new ArrayList<>();
    }

    private static enum State {
        IDLE,
        HUB,
        OPENING,
        MENU,
        DONE,
        FAILED;
    }
}
