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
package net.arcanic.visuals.lua;

import static net.arcanic.visuals.lua.LuaApi.act;
import static net.arcanic.visuals.lua.LuaApi.clip;
import static net.arcanic.visuals.lua.LuaApi.fn;
import static net.arcanic.visuals.lua.LuaApi.value;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import net.arcanic.visuals.ArcanicVisuals;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.fx.Friends;
import net.arcanic.visuals.fx.Projection;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Px;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * The game for scripts: the player, the world as you see it, the inventory, chat, keys, mouse and sound. Reading
 * only; scripts do not move, look, attack or click in the inventory for the player. Other entities are listed only
 * while they are in sight, so a script cannot see through walls.
 */
final class LuaGame {
    private LuaGame() {}

    private static final int BLOCK_REACH = 8;
    private static final int MAX_RADIUS = 64;

    static void install(Globals g, Script s) {
        g.set("player", player());
        g.set("world", world());
        g.set("inventory", inventory());
        g.set("client", client());
        g.set("chat", chat());
        g.set("keys", keys(s));
        g.set("mouse", mouse());
        g.set("sound", sound());
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static LocalPlayer me() {
        return mc().player;
    }

    private static float pt() {
        return mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    static String id(ItemStack st) {
        return st == null || st.isEmpty()
                ? ""
                : BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    private static LuaTable item(ItemStack st, int slot) {
        LuaTable t = new LuaTable();
        t.set("slot", slot);
        t.set("id", id(st));
        t.set("name", st.getHoverName().getString());
        t.set("count", st.getCount());
        t.set("damage", st.getDamageValue());
        t.set("maxDamage", st.getMaxDamage());
        return t;
    }

    // ---- player

    private static LuaTable player() {
        LuaTable t = new LuaTable();
        t.set("name", value(() -> mc().getUser().getName()));
        t.set("uuid", value(() -> me() == null ? "" : me().getStringUUID()));
        t.set("health", value(() -> me() == null ? 0 : me().getHealth()));
        t.set("maxHealth", value(() -> me() == null ? 0 : me().getMaxHealth()));
        t.set("absorption", value(() -> me() == null ? 0 : me().getAbsorptionAmount()));
        t.set("food", value(() -> me() == null ? 0 : me().getFoodData().getFoodLevel()));
        t.set("saturation", value(() -> me() == null ? 0 : me().getFoodData().getSaturationLevel()));
        t.set("armor", value(() -> me() == null ? 0 : me().getArmorValue()));
        t.set("level", value(() -> me() == null ? 0 : me().experienceLevel));
        t.set("xp", value(() -> me() == null ? 0 : me().experienceProgress));
        t.set("pos", fn(a -> {
            LocalPlayer p = me();
            if (p == null) return LuaValue.varargsOf(LuaValue.valueOf(0), LuaValue.valueOf(0), LuaValue.valueOf(0));
            Vec3 v = p.getPosition(pt());
            return LuaValue.varargsOf(LuaValue.valueOf(v.x), LuaValue.valueOf(v.y), LuaValue.valueOf(v.z));
        }));
        t.set("velocity", fn(a -> {
            LocalPlayer p = me();
            if (p == null) return LuaValue.varargsOf(LuaValue.valueOf(0), LuaValue.valueOf(0), LuaValue.valueOf(0));
            return LuaValue.varargsOf(
                    LuaValue.valueOf((p.getX() - p.xo) * 20),
                    LuaValue.valueOf((p.getY() - p.yo) * 20),
                    LuaValue.valueOf((p.getZ() - p.zo) * 20));
        }));
        t.set("speed", value(() -> {
            LocalPlayer p = me();
            if (p == null) return 0;
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            return Math.sqrt(dx * dx + dz * dz) * 20.0;
        }));
        t.set("yaw", value(() -> me() == null ? 0 : me().getViewYRot(pt())));
        t.set("pitch", value(() -> me() == null ? 0 : me().getViewXRot(pt())));
        t.set("onGround", value(() -> me() != null && me().onGround()));
        t.set("sneaking", value(() -> me() != null && me().isShiftKeyDown()));
        t.set("sprinting", value(() -> me() != null && me().isSprinting()));
        t.set("swimming", value(() -> me() != null && me().isSwimming()));
        t.set("inWater", value(() -> me() != null && me().isInWater()));
        t.set("flying", value(() -> me() != null && (me().isFallFlying() || me().getAbilities().flying)));
        t.set("burning", value(() -> me() != null && me().isOnFire()));
        t.set("hurtTime", value(() -> me() == null ? 0 : me().hurtTime));
        t.set("fallDistance", value(() -> me() == null ? 0 : me().fallDistance));
        t.set("attackCharge", value(() -> me() == null ? 0 : me().getAttackStrengthScale(pt())));
        t.set("gamemode", value(() -> {
            if (mc().gameMode == null) return "";
            return mc().gameMode.getPlayerMode().getName();
        }));
        t.set("held", value(() -> me() == null ? "" : id(me().getMainHandItem())));
        t.set("offhand", value(() -> me() == null ? "" : id(me().getOffhandItem())));
        t.set("armorItems", fn(a -> {
            LuaTable list = new LuaTable();
            LocalPlayer p = me();
            if (p != null) {
                EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
                };
                for (int i = 0; i < slots.length; i++) list.set(i + 1, item(p.getItemBySlot(slots[i]), i));
            }
            return list;
        }));
        t.set("effects", fn(a -> {
            LuaTable list = new LuaTable();
            LocalPlayer p = me();
            if (p != null) {
                int i = 1;
                for (MobEffectInstance e : p.getActiveEffects()) {
                    LuaTable o = new LuaTable();
                    o.set("id", e.getEffect().getRegisteredName());
                    o.set("name", e.getEffect().value().getDisplayName().getString());
                    o.set("level", e.getAmplifier() + 1);
                    o.set("seconds", Math.max(0, (e.getDuration() + 19) / 20));
                    list.set(i++, o);
                }
            }
            return list;
        }));
        t.set(
                "dimension",
                value(() -> mc().level == null
                        ? ""
                        : mc().level.dimension().identifier().toString()));
        t.set("biome", value(() -> {
            if (me() == null || mc().level == null) return "";
            return mc().level.getBiome(me().blockPosition()).getRegisteredName();
        }));
        t.set("lookingAt", fn(a -> {
            HitResult hit = mc().hitResult;
            if (hit instanceof EntityHitResult e) return entity(e.getEntity());
            if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK && mc().level != null) {
                LuaTable o = new LuaTable();
                BlockPos p = b.getBlockPos();
                o.set("type", "block");
                o.set(
                        "id",
                        BuiltInRegistries.BLOCK
                                .getKey(mc().level.getBlockState(p).getBlock())
                                .toString());
                o.set("x", p.getX());
                o.set("y", p.getY());
                o.set("z", p.getZ());
                o.set("face", b.getDirection().getName());
                return o;
            }
            return LuaValue.NIL;
        }));
        return t;
    }

    // ---- world

    /** In sight: not invisible to us and with nothing in between. */
    private static boolean seen(Entity e) {
        LocalPlayer me = me();
        return me != null && e != me && !e.isInvisibleTo(me) && me.hasLineOfSight(e);
    }

    static LuaTable entity(Entity e) {
        LocalPlayer me = me();
        LuaTable o = new LuaTable();
        Vec3 v = e.getPosition(pt());
        o.set("type", "entity");
        o.set("id", e.getId());
        o.set("uuid", e.getStringUUID());
        o.set("kind", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
        o.set("name", e.getName().getString());
        o.set("x", v.x);
        o.set("y", v.y);
        o.set("z", v.z);
        o.set("height", e.getBbHeight());
        o.set("distance", me == null ? 0 : me.distanceTo(e));
        o.set("isPlayer", LuaValue.valueOf(e instanceof Player));
        o.set("friend", LuaValue.valueOf(Friends.is(e)));
        o.set("sneaking", LuaValue.valueOf(e.isShiftKeyDown()));
        if (e instanceof LivingEntity l) {
            o.set("health", l.getHealth());
            o.set("maxHealth", l.getMaxHealth());
            o.set("armor", l.getArmorValue());
            o.set("held", id(l.getMainHandItem()));
            o.set("hurtTime", l.hurtTime);
        }
        return o;
    }

    private static LuaTable world() {
        LuaTable t = new LuaTable();
        t.set("loaded", value(() -> mc().level != null));
        t.set(
                "time",
                value(() -> mc().level == null
                        ? 0
                        : Math.floorMod(mc().level.getLevelData().getDayTime(), 24000L)));
        t.set(
                "day",
                value(() -> mc().level == null ? 0 : mc().level.getLevelData().getDayTime() / 24000L));
        t.set("weather", value(() -> {
            if (mc().level == null) return "clear";
            return mc().level.isThundering() ? "thunder" : (mc().level.isRaining() ? "rain" : "clear");
        }));
        t.set("block", fn(a -> {
            LocalPlayer me = me();
            if (me == null || mc().level == null) return LuaValue.NIL;
            BlockPos p = new BlockPos(a.checkint(1), a.checkint(2), a.checkint(3));
            // near the player only: a script must not scan the world for ores
            if (p.getCenter().distanceTo(me.getEyePosition()) > BLOCK_REACH) {
                throw new LuaError("world.block: блок дальше " + BLOCK_REACH + " блоков");
            }
            return LuaValue.valueOf(BuiltInRegistries.BLOCK
                    .getKey(mc().level.getBlockState(p).getBlock())
                    .toString());
        }));
        t.set("players", fn(a -> {
            LuaTable list = new LuaTable();
            if (mc().level == null) return list;
            int i = 1;
            for (Player p : mc().level.players()) {
                if (seen(p) && me().distanceTo(p) <= 128) list.set(i++, entity(p));
            }
            return list;
        }));
        t.set("entities", fn(a -> {
            LuaTable list = new LuaTable();
            if (mc().level == null || me() == null) return list;
            double r = Math.min(MAX_RADIUS, a.optdouble(1, 32));
            int i = 1;
            for (Entity e : mc().level.entitiesForRendering()) {
                if (i > 256) break;
                if (me().distanceTo(e) <= r && seen(e)) list.set(i++, entity(e));
            }
            return list;
        }));
        t.set("online", fn(a -> {
            LuaTable list = new LuaTable();
            ClientPacketListener c = mc().getConnection();
            if (c == null) return list;
            int i = 1;
            for (PlayerInfo info : c.getOnlinePlayers()) {
                LuaTable o = new LuaTable();
                o.set("name", info.getProfile().name());
                o.set("ping", info.getLatency());
                list.set(i++, o);
            }
            return list;
        }));
        t.set("toScreen", fn(a -> {
            if (mc().level == null) return LuaValue.NIL;
            double[] s = Projection.toScreen(
                    new Vec3(a.checkdouble(1), a.checkdouble(2), a.checkdouble(3)), Px.width(), Px.height());
            if (s == null) return LuaValue.NIL;
            return LuaValue.varargsOf(LuaValue.valueOf(s[0]), LuaValue.valueOf(s[1]), LuaValue.valueOf(s[2]));
        }));
        return t;
    }

    // ---- inventory: reading only

    private static LuaTable inventory() {
        LuaTable t = new LuaTable();
        t.set("selected", value(() -> me() == null ? 0 : me().getInventory().getSelectedSlot() + 1));
        t.set("get", fn(a -> {
            LocalPlayer p = me();
            int slot = a.checkint(1);
            if (p == null || slot < 0 || slot >= p.getInventory().getContainerSize()) return LuaValue.NIL;
            return item(p.getInventory().getItem(slot), slot);
        }));
        t.set("items", fn(a -> {
            LuaTable list = new LuaTable();
            LocalPlayer p = me();
            if (p == null) return list;
            int i = 1;
            for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                ItemStack st = p.getInventory().getItem(slot);
                if (!st.isEmpty()) list.set(i++, item(st, slot));
            }
            return list;
        }));
        t.set("count", fn(a -> {
            LocalPlayer p = me();
            String id = a.checkjstring(1);
            int n = 0;
            if (p != null) {
                for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                    ItemStack st = p.getInventory().getItem(slot);
                    if (id(st).equals(id)) n += st.getCount();
                }
            }
            return LuaValue.valueOf(n);
        }));
        return t;
    }

    // ---- client

    private static LuaTable client() {
        LuaTable t = new LuaTable();
        t.set("fps", value(() -> mc().getFps()));
        t.set("ping", value(() -> mc().getCurrentServer() == null ? 0 : HudElements.ping()));
        t.set("ms", value(System::currentTimeMillis));
        t.set(
                "clock",
                fn(a -> LuaValue.valueOf(
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern(a.optjstring(1, "HH:mm"))))));
        t.set("server", value(() -> {
            ServerData d = mc().getCurrentServer();
            return d == null ? "" : d.ip;
        }));
        t.set(
                "screen",
                value(() -> mc().screen == null ? "" : mc().screen.getClass().getSimpleName()));
        t.set("version", value(() -> FabricLoader.getInstance()
                .getModContainer(ArcanicVisuals.ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("")));
        t.set("minecraft", value(() -> SharedConstants.getCurrentVersion().name()));
        t.set("english", value(Lang::english));
        t.set("notify", act(a -> Toasts.setting(clip(a.checkjstring(1), 120), a.optboolean(2, true))));
        t.set("title", act(a -> {
            mc().gui.setTitle(Component.literal(clip(a.checkjstring(1), 64)));
            if (!a.isnoneornil(2)) mc().gui.setSubtitle(Component.literal(clip(a.checkjstring(2), 96)));
            mc().gui.setTimes(a.optint(3, 5), a.optint(4, 40), a.optint(5, 10));
        }));
        t.set("actionbar", act(a -> {
            if (me() != null) me().displayClientMessage(Component.literal(clip(a.checkjstring(1), 128)), true);
        }));
        t.set("openMenu", act(a -> mc().setScreen(new net.arcanic.visuals.gui.ArcanicMenu())));
        t.set("closeScreen", act(a -> {
            if (mc().screen != null) mc().setScreen(null);
        }));
        return t;
    }

    // ---- chat

    private static final Deque<String> OUTBOX = new ArrayDeque<>();
    private static long sentAt;
    static boolean sending;

    /** One message from the scripts every 1.2 s at most, so a script cannot get the player kicked for spam. */
    static void pump() {
        if (OUTBOX.isEmpty() || System.currentTimeMillis() - sentAt < 1200) return;
        ClientPacketListener c = mc().getConnection();
        if (c == null) {
            OUTBOX.clear();
            return;
        }
        String m = OUTBOX.poll();
        sentAt = System.currentTimeMillis();
        sending = true;
        try {
            if (m.startsWith("/")) c.sendCommand(m.substring(1));
            else c.sendChat(m);
        } finally {
            sending = false;
        }
    }

    private static LuaTable chat() {
        LuaTable t = new LuaTable();
        t.set("send", act(a -> {
            String m = a.checkjstring(1).strip();
            if (m.isEmpty()) return;
            if (OUTBOX.size() >= 10) throw new LuaError("chat.send: очередь переполнена");
            OUTBOX.add(clip(m, 256));
        }));
        t.set("print", act(a -> {
            if (me() == null) return;
            ChatFormatting f = ChatFormatting.getByName(a.optjstring(2, "gray"));
            me().displayClientMessage(
                            Component.literal(clip(a.checkjstring(1), 512))
                                    .withStyle(f == null ? ChatFormatting.GRAY : f),
                            false);
        }));
        t.set("clear", act(a -> mc().gui.getChat().clearMessages(false)));
        return t;
    }

    // ---- keys, mouse, sound

    private static LuaTable keys(Script s) {
        LuaTable t = new LuaTable();
        t.set("down", fn(a -> LuaValue.valueOf(LuaKeys.down(LuaKeys.code(a.checkjstring(1))))));
        t.set("bind", act(a -> {
            int code = LuaKeys.code(a.checkjstring(1));
            if (code < 0) throw new LuaError("нет клавиши " + a.checkjstring(1));
            s.bind(code, a.checkfunction(2));
        }));
        t.set("name", fn(a -> LuaValue.valueOf(LuaKeys.name(a.checkint(1)))));
        return t;
    }

    private static LuaTable mouse() {
        LuaTable t = new LuaTable();
        t.set(
                "x",
                value(() -> mc().mouseHandler.xpos()
                        * Px.width()
                        / Math.max(1, mc().getWindow().getScreenWidth())));
        t.set(
                "y",
                value(() -> mc().mouseHandler.ypos()
                        * Px.height()
                        / Math.max(1, mc().getWindow().getScreenHeight())));
        t.set(
                "down",
                fn(a -> LuaValue.valueOf(LuaKeys.down(net.arcanic.visuals.core.Keys.MOUSE + a.optint(1, 1) - 1))));
        return t;
    }

    private static LuaTable sound() {
        LuaTable t = new LuaTable();
        t.set("play", act(a -> {
            Identifier id = Identifier.tryParse(a.checkjstring(1));
            if (id == null) throw new LuaError("нет звука " + a.checkjstring(1));
            SoundEvent ev = SoundEvent.createVariableRangeEvent(id);
            float volume = (float) Math.max(0, Math.min(1, a.optdouble(2, 1)));
            float pitch = (float) Math.max(0.5, Math.min(2, a.optdouble(3, 1)));
            mc().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
        }));
        t.set("ui", act(a -> UiSound.play(a.checkjstring(1))));
        return t;
    }

    /** A player's face by name, from the tab list; a plain plate when the player is not online here. */
    static void head(GuiGraphics g, String name, int x, int y, int size) {
        ClientPacketListener c = mc().getConnection();
        PlayerInfo info = c == null ? null : c.getPlayerInfo(name);
        if (info == null) {
            Px.round(g, x, y, size, size, Math.max(2, size / 4), 0xFF2C2C30);
            return;
        }
        PlayerFaceRenderer.draw(g, info.getSkin(), x, y, size);
    }
}
