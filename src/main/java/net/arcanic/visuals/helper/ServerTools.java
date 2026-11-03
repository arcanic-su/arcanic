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
import java.util.regex.Pattern;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;

/** Server helpers taken over from Pulse: reconnecting, a pickup log, locked slots, the elytra swap, ReallyWorld. */
public final class ServerTools {
    private ServerTools() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void say(Component text) {
        if (mc().player != null) mc().player.displayClientMessage(text, false);
    }

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> Modules.RW_HELPER.allows(message));
    }

    // ---------------------------------------------------------------- auto reconnect

    /** FunTime: back to your anarchy ({@code /an<number>}) when the server drops you into the hub. */
    public static final class AutoReconnect extends Module {
        private static final long RETRY = 30000L;
        private int last = -1;
        private long triedAt;

        public AutoReconnect() {
            super(
                    "auto_reconnect",
                    "Авто-перезаход",
                    "Если FunTime выкинул в хаб, сам возвращает на последнюю анархию",
                    Category.FUNTIME,
                    false);
        }

        public void tick() {
            LocalPlayer me = mc().player;
            if (me == null || !FunTime.here()) return;
            int now = FunTime.anarchy();
            if (now != -1) {
                this.last = now;
                return;
            }
            long t = System.currentTimeMillis();
            if (this.last != -1 && me.tickCount > 20 && t - this.triedAt >= RETRY) {
                ClientPacketListener conn = mc().getConnection();
                if (conn != null) conn.sendCommand("an" + this.last);
                this.triedAt = t;
            }
        }
    }

    // ---------------------------------------------------------------- pickup log

    /** Each item you pick up, told in the chat; paid items (★ or a scrambled name) called out as such. */
    public static final class PickupLog extends Module {
        private final Setting.Bool paidOnly = this.add(new Setting.Bool("paid_only", "Только донат предметы", false));

        public PickupLog() {
            super("pickup_log", "Лог подбора", "Пишет в чат, что ты поднял", Category.UTILITY, false);
        }

        public void picked(ClientboundTakeItemEntityPacket packet) {
            LocalPlayer me = mc().player;
            if (!this.enabled() || me == null || mc().level == null || packet.getPlayerId() != me.getId()) return;
            Entity e = mc().level.getEntity(packet.getItemId());
            if (!(e instanceof ItemEntity item)) return;
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) return;
            boolean paid = paid(stack);
            if (this.paidOnly.on() && !paid) return;
            MutableComponent line = Component.literal(paid ? "Поднят донат предмет: " : "Поднят: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(stack.getHoverName());
            int count = Math.max(packet.getAmount(), 1);
            if (count > 1) line.append(Component.literal(" x" + count).withStyle(ChatFormatting.GRAY));
            say(line);
        }

        private static boolean paid(ItemStack stack) {
            Component name = stack.getHoverName();
            if (name.getString().contains("★")) return true;
            if (name.getStyle().isObfuscated()) return true;
            for (Component part : name.getSiblings()) if (part.getStyle().isObfuscated()) return true;
            return false;
        }
    }

    // ---------------------------------------------------------------- lock slot

    /** Items in chosen hotbar slots, and spheres and charms anywhere, can't be thrown away by accident. */
    public static final class LockSlot extends Module {
        private final Setting.Bool wholeBar = this.add(new Setting.Bool("whole_bar", "Весь хотбар", false));
        private final Setting.Bool[] slots = new Setting.Bool[9];
        private final Setting.Bool charms = this.add(new Setting.Bool("charms", "Сферы и талисманы", false));

        public LockSlot() {
            super("lock_slot", "Lock Slot", "Не даёт выбросить предметы из выбранных слотов", Category.UTILITY, false);
            for (int i = 0; i < 9; i++) {
                this.slots[i] = (Setting.Bool) this.add(new Setting.Bool("slot" + (i + 1), "Слот " + (i + 1), false))
                        .showIf(() -> !this.wholeBar.on());
            }
        }

        /** Whether the item in player inventory slot {@code index} (40 is the off hand) must stay. */
        public boolean locked(int index) {
            LocalPlayer me = mc().player;
            if (!this.enabled() || me == null) return false;
            if (index >= 0 && index < 9 && (this.wholeBar.on() || this.slots[index].on())) return true;
            return index >= 0 && index <= 40 && this.charm(me.getInventory().getItem(index));
        }

        /** A click that throws a slot's item out of an open container. */
        public boolean blocksThrow(int slotId, ClickType type) {
            LocalPlayer me = mc().player;
            if (type != ClickType.THROW || me == null || slotId < 0) return false;
            if (slotId >= me.containerMenu.slots.size()) return false;
            Slot slot = me.containerMenu.getSlot(slotId);
            return slot.container == me.getInventory() && this.locked(slot.getContainerSlot());
        }

        private boolean charm(ItemStack s) {
            if (!this.charms.on() || s.isEmpty()) return false;
            if (s.is(Items.PLAYER_HEAD)) {
                ResolvableProfile profile = s.get(DataComponents.PROFILE);
                return profile != null;
            }
            if (!s.is(Items.TOTEM_OF_UNDYING)) return false;
            ItemEnchantments ench = s.get(DataComponents.ENCHANTMENTS);
            return ench != null && !ench.isEmpty();
        }
    }

    // ---------------------------------------------------------------- elytra swap

    /** One key puts the elytra on in place of the chestplate, or the chestplate back. */
    public static final class ElytraSwap extends Module {
        private static final int CHEST_SLOT = 6;
        private static final int OFF_HAND_BUTTON = 40;
        public final Setting.Key key = this.add(new Setting.Key("swap_key", "Клавиша"));

        public ElytraSwap() {
            super(
                    "elytra_swap",
                    "Elytra Swap",
                    "Меняет нагрудник на элитры и обратно по клавише",
                    Category.UTILITY,
                    false);
        }

        public void pressed(int code) {
            LocalPlayer me = mc().player;
            if (!this.enabled()
                    || !this.key.bound()
                    || this.key.code() != code
                    || me == null
                    || mc().gameMode == null) {
                return;
            }
            boolean wearing = me.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
            int from = this.find(me, wearing);
            if (from < 0) return;
            int id = me.inventoryMenu.containerId;
            mc().gameMode.handleInventoryMouseClick(id, from, OFF_HAND_BUTTON, ClickType.SWAP, me);
            mc().gameMode.handleInventoryMouseClick(id, CHEST_SLOT, OFF_HAND_BUTTON, ClickType.SWAP, me);
            mc().gameMode.handleInventoryMouseClick(id, from, OFF_HAND_BUTTON, ClickType.SWAP, me);
            me.displayClientMessage(Component.literal(wearing ? "Снял элитры" : "Надел элитры"), true);
        }

        /** A menu slot of the inventory or hotbar holding what goes on next: a chestplate, or an elytra. */
        private int find(LocalPlayer me, boolean chestplate) {
            for (int slot = 9; slot <= 44; slot++) {
                ItemStack s = me.inventoryMenu.getSlot(slot).getItem();
                if (chestplate ? isChestplate(s) : s.is(Items.ELYTRA)) return slot;
            }
            return -1;
        }

        private static boolean isChestplate(ItemStack s) {
            if (s.is(Items.ELYTRA)) return false;
            Equippable eq = s.get(DataComponents.EQUIPPABLE);
            return eq != null && eq.slot() == EquipmentSlot.CHEST;
        }
    }

    // ---------------------------------------------------------------- ReallyWorld

    /** ReallyWorld: closes the menu the server opens on joining, and keeps forbidden words out of your messages. */
    public static final class RwHelper extends Module {
        private static final String JOIN_MENU = "ꈁꀀꈂꌁꈂꀁ§0ꈃꄀ";
        private static final Pattern BANNED = Pattern.compile(String.join(
                "|",
                "акриен(а|у|ом|е|чик)?",
                "рич(а|у|ом|ей|е)?",
                "ньюкод(ом|а|у|ами|ик|е)?",
                "экспенсив(ом|а|у|ами|е)?",
                "импакт(ом|а|у|ами|ик|е)?",
                "экселл?ент(ом|а|у|ами|ик|е)?",
                "катл[ао]ван(ом|а|у|ами|чик)?",
                "целестиал(ом|а|у|ами|е)?",
                "целк(ой|а|у|ами|очка|е)?",
                "матикс(ом|а|у|ами|е)?",
                "инерти(я|ей|ю|ями|е)?",
                "эксп(а|ой|ою|у|уличка|е)?",
                "флюгер(ом|а|у|ами)?",
                "рикер(а|у|ом|очек)?",
                "фанпе(й|ю|я|ем|е|йчик)?",
                "вексайд(ом|а|у|ами|ик|е)?",
                "нурсултан(а|у|е|ом|чик)?",
                "нурик(а|у|ом|е)?",
                "нурлан(а|у|ом|чик|е)?",
                "векс(ом|у|а|ами|ик|е)?",
                "релейк(ом|у|а|ами|е)?",
                "арбуз(ом|а|у|ами|ик|е)?",
                "вилд(ом|у|а|ами|ик|е)?",
                "фантайм(е|а|у)?",
                "холик(е|а|у)?",
                "холиворлд(а|у|е)?",
                "рокстар(ом|а|у|ами|чик|е)?",
                "рогалик(а|у|ом|е)?",
                "тандерхак(ом|у|и|ами|а|е)?",
                "ликвидбаунс(а|у|ами|е)?",
                "expensive|celestial|newcode|arbuz|akrien|nursultan|relake|wild|wurst|catlovan|excellent",
                "rockstar|catlavan|impact|matix|inertia|wexside|wex|nurik|nurlan|rich|funpay|fluger|riker",
                "funtime|holyworld|wwe|hvh|rogalik|thunderhack|liquidbounce"));
        private final Setting.Bool closeMenu =
                this.add(new Setting.Bool("close_menu", "Закрывать меню при входе", true));
        private final Setting.Bool filter = this.add(new Setting.Bool("filter", "Фильтр запрещённых слов", true));

        public RwHelper() {
            super(
                    "rw_helper",
                    "RW Helper",
                    "ReallyWorld: закрывает меню при входе и не даёт отправить запрещённые слова",
                    Category.UTILITY,
                    false);
        }

        private static boolean here() {
            ServerData d = mc().getCurrentServer();
            return d != null && d.ip != null && d.ip.toLowerCase(Locale.ROOT).contains("reallyworld");
        }

        /** True when the menu was closed and should not open. */
        public boolean closes(ClientboundOpenScreenPacket packet) {
            LocalPlayer me = mc().player;
            if (!this.enabled() || !this.closeMenu.on() || !here() || me == null || me.tickCount > 99) return false;
            if (!packet.getTitle().getString().contains(JOIN_MENU)) return false;
            ClientPacketListener conn = mc().getConnection();
            if (conn != null) conn.send(new ServerboundContainerClosePacket(packet.getContainerId()));
            return true;
        }

        boolean allows(String message) {
            if (!this.enabled() || !this.filter.on() || !here()) return true;
            if (!BANNED.matcher(message.toLowerCase(Locale.ROOT)).find()) return true;
            say(Component.literal("RW Helper: сообщение с запрещённым словом не отправлено")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
    }
}
