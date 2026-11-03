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
package net.arcanic.visuals.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.arcanic.visuals.fx.Crosshair;
import net.arcanic.visuals.helper.Pings;
import net.arcanic.visuals.helper.Waypoints;
import net.arcanic.visuals.hud.FriendCards;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

/**
 * Arcanic Social friends. They are kept by the sync service per Arcanic account, so a friend stays a friend on any
 * server and is seen from the title screen too. Everything here goes over the sync line (SyncLine).
 */
public final class Social {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/social.json");
    private static final int ITEM_LIMIT = 10_000; // characters of one item's data; heavier items go as the bare item
    private static volatile List<Friend> friends = List.of();
    private static volatile List<Request> incoming = List.of();
    private static volatile List<Request> outgoing = List.of();
    private static volatile boolean listed;
    private static final Set<String> UNLIT = new HashSet<>();
    private static final Map<String, Supplier<PlayerSkin>> SKINS = new HashMap<>();
    private static boolean loaded;
    private static boolean showCoords = true;
    private static boolean shareInventory = true;
    private static String tracking;
    private static SyncLine sentOn;
    private static String sentWhere;
    private static int whereTicks;
    private static InventoryView shown;
    // what the player did while the line was down: sent as soon as it is back (only the last minute of it)
    private static final List<JsonObject> OUTBOX = new ArrayList<>();
    private static final List<Long> OUTBOX_AT = new ArrayList<>();
    // requests already shown as a card this start, so a list sent again does not pop them up twice
    private static final Set<String> SEEN = new HashSet<>();
    private static volatile String lastNote = "";
    private static volatile boolean lastGood;
    private static volatile long noteAt;

    private Social() {}

    public record Friend(
            String account,
            String name,
            UUID uuid,
            boolean online,
            boolean inWorld,
            String server,
            boolean local,
            String dim,
            Vec3 pos) {
        /** One short line: where the friend is right now. */
        public String status() {
            if (!online) return Lang.tr("Не в сети");
            if (!inWorld) return Lang.tr("В меню игры");
            if (local) return Lang.tr("Одиночная игра");
            return server == null ? Lang.tr("В игре") : server;
        }

        public boolean joinable() {
            return inWorld && !local && server != null && !server.equals(serverHere());
        }

        public boolean near() {
            Minecraft mc = Minecraft.getInstance();
            return inWorld
                    && pos != null
                    && server != null
                    && server.equals(serverHere())
                    && mc.level != null
                    && Objects.equals(dim, mc.level.dimension().identifier().toString());
        }
    }

    public record Request(String account, String name, UUID uuid) {}

    /** A friend's inventory as it came: 36 slots, then the boots up to the helmet, then the off hand. */
    public record InventoryView(String account, String name, List<ItemStack> items, boolean denied, long at) {}

    public static boolean ready() {
        return Sync.on() && listed;
    }

    public static List<Friend> friends() {
        return friends;
    }

    public static List<Request> incoming() {
        return incoming;
    }

    public static List<Request> outgoing() {
        return outgoing;
    }

    public static Friend friend(String account) {
        for (Friend f : friends) if (f.account.equals(account)) return f;
        return null;
    }

    private static boolean is(Player p, String name, UUID uuid) {
        return uuid != null && uuid.equals(p.getUUID())
                || name != null && name.equalsIgnoreCase(p.getGameProfile().name());
    }

    public static Friend friendOf(Player p) {
        for (Friend f : friends) if (f.inWorld && f.near() && is(p, f.name, f.uuid)) return f;
        for (Friend f : friends) if (is(p, f.name, f.uuid)) return f;
        return null;
    }

    public static Request askedBy(Player p) {
        for (Request r : incoming) if (is(p, r.name, r.uuid)) return r;
        return null;
    }

    public static Request askedTo(Player p) {
        for (Request r : outgoing) if (is(p, r.name, r.uuid)) return r;
        return null;
    }

    // ------------------------------------------------------------------ what stays on this computer

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            JsonObject o = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (o.has("unlit")) for (JsonElement e : o.getAsJsonArray("unlit")) UNLIT.add(e.getAsString());
            if (o.has("coords")) showCoords = o.get("coords").getAsBoolean();
            if (o.has("inventory")) shareInventory = o.get("inventory").getAsBoolean();
        } catch (Exception ignored) {
            // the defaults then
        }
    }

    private static void save() {
        JsonObject o = new JsonObject();
        JsonArray unlit = new JsonArray();
        UNLIT.forEach(unlit::add);
        o.add("unlit", unlit);
        o.addProperty("coords", showCoords);
        o.addProperty("inventory", shareInventory);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(
                    FILE, new GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // kept for this start only
        }
    }

    public static boolean showsCoords() {
        load();
        return showCoords;
    }

    public static void showCoords(boolean on) {
        load();
        showCoords = on;
        sentWhere = null;
        save();
    }

    public static boolean sharesInventory() {
        load();
        return shareInventory;
    }

    public static void shareInventory(boolean on) {
        load();
        shareInventory = on;
        save();
    }

    public static boolean lit(String account) {
        load();
        return !UNLIT.contains(account);
    }

    public static void light(String account, boolean on) {
        load();
        if (on) UNLIT.remove(account);
        else UNLIT.add(account);
        save();
    }

    /** True when this player is an Arcanic friend whose highlight is on. */
    public static boolean glows(Entity e) {
        if (!(e instanceof Player p) || e == Minecraft.getInstance().player || friends.isEmpty()) return false;
        Friend f = friendOf(p);
        return f != null && lit(f.account);
    }

    public static boolean anyGlow() {
        if (friends.isEmpty()) return false;
        load();
        for (Friend f : friends) if (f.inWorld && !UNLIT.contains(f.account)) return true;
        return false;
    }

    // ------------------------------------------------------------------ the line

    /** Sends now, or keeps it for the moment the line is back; false only when it cannot go at all. */
    private static boolean send(JsonObject o) {
        if (!Sync.on()) {
            note(Lang.tr("Нужен вход: войди в Arcanic Social"), false);
            return false;
        }
        if (Sync.send(o)) return true;
        synchronized (OUTBOX) {
            if (OUTBOX.size() < 20) {
                OUTBOX.add(o);
                OUTBOX_AT.add(System.currentTimeMillis());
            }
        }
        note(Lang.tr("Нет связи: отправлю, как только она вернётся"), true);
        return true;
    }

    private static void flush() {
        long now = System.currentTimeMillis();
        synchronized (OUTBOX) {
            for (int i = 0; i < OUTBOX.size(); i++) if (now - OUTBOX_AT.get(i) < 60000L) Sync.send(OUTBOX.get(i));
            OUTBOX.clear();
            OUTBOX_AT.clear();
        }
    }

    /** The last answer of the service, for screens that have no HUD under them: text, good or not, and when. */
    public static String lastNote() {
        return System.currentTimeMillis() - noteAt < 5000L ? lastNote : "";
    }

    public static boolean lastNoteGood() {
        return lastGood;
    }

    private static JsonObject msg(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        return o;
    }

    public static void note(String text, boolean good) {
        lastNote = text;
        lastGood = good;
        noteAt = System.currentTimeMillis();
        FriendCards.note(text, good);
    }

    /** The server address friends can join, or null in a single-player or LAN world. */
    public static String serverHere() {
        Minecraft mc = Minecraft.getInstance();
        ServerData d = mc.getCurrentServer();
        if (mc.level == null || d == null || d.isLan() || d.ip == null) return null;
        String ip = d.ip.trim().toLowerCase(Locale.ROOT);
        return ip.endsWith(":25565") ? ip.substring(0, ip.length() - 6) : ip;
    }

    /** Once a second while the line is open: where this game is, sent again only when it changes. */
    static void tick(Minecraft mc, SyncLine w) {
        if (w != sentOn) {
            sentOn = w;
            sentWhere = null;
            flush();
        }
        if (mc.player == null || mc.level == null) {
            sentWhere = null; // Sync says "bye" on the way out: the service forgets the place itself
            return;
        }
        if (++whereTicks < 20 && sentWhere != null) return;
        whereTicks = 0;
        ServerData d = mc.getCurrentServer();
        JsonObject o = msg("where");
        o.addProperty("server", serverHere());
        o.addProperty("local", d == null || d.isLan());
        o.addProperty("dim", mc.level.dimension().identifier().toString());
        if (showsCoords()) {
            o.addProperty("x", Math.floor(mc.player.getX()));
            o.addProperty("y", Math.floor(mc.player.getY()));
            o.addProperty("z", Math.floor(mc.player.getZ()));
        }
        String s = o.toString();
        if (!s.equals(sentWhere)) {
            sentWhere = s;
            Sync.send(o);
        }
    }

    static void lost() {
        listed = false;
        sentWhere = null;
    }

    /** A message of the sync line that belongs here; handled on the game thread. */
    static boolean handle(String t, JsonObject p) {
        switch (t) {
            case "beacon", "beacon_ack", "beacon_off" -> {
                Minecraft.getInstance().execute(() -> {
                    try {
                        Pings.heard(t, p);
                    } catch (RuntimeException ignored) {
                        // a broken ping changes nothing
                    }
                });
                return true;
            }
            case "friends", "fpos", "friend_req", "friend_ok", "note", "mark", "inv_ask", "inv", "xhair" -> {
                Minecraft.getInstance().execute(() -> {
                    try {
                        apply(t, p);
                    } catch (RuntimeException ignored) {
                        // a broken message changes nothing
                    }
                });
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void apply(String t, JsonObject p) {
        switch (t) {
            case "friends" -> {
                List<Friend> list = new ArrayList<>();
                for (JsonElement e : p.getAsJsonArray("friends")) list.add(friend(e.getAsJsonObject()));
                friends = sorted(list);
                incoming = requests(p.getAsJsonArray("incoming"));
                outgoing = requests(p.getAsJsonArray("outgoing"));
                listed = true;
                // a request that came while this game was away pops up now
                for (Request r : incoming) if (SEEN.add(r.account)) FriendCards.request(r);
                if (tracking != null && friend(tracking) == null) track(null);
                follow();
            }
            case "fpos" -> {
                Friend f = friend(p);
                List<Friend> list = new ArrayList<>(friends);
                list.replaceAll(o -> o.account.equals(f.account) ? f : o);
                Friend was = friend(f.account);
                friends = sorted(list);
                if (was != null && was.online != f.online) FriendCards.status(f, f.online);
                follow();
            }
            case "friend_req" -> {
                Request r = request(p);
                SEEN.add(r.account);
                FriendCards.request(r);
            }
            case "friend_ok" -> FriendCards.accepted(request(p));
            case "note" -> {
                String what = str(p, "what");
                String name = str(p, "name");
                switch (what == null ? "" : what) {
                    case "sent" -> note(Lang.tr("Заявка отправлена") + ": " + name, true);
                    case "not_arcanic" -> note(Lang.tr("У игрока нет Arcanic Visuals"), false);
                    case "unknown" -> note(Lang.tr("Такого игрока нет в Arcanic"), false);
                    case "self" -> note(Lang.tr("Это ты"), false);
                    case "already" -> note(Lang.tr("Вы уже друзья"), true);
                    case "limit" -> note(Lang.tr("Слишком много друзей или заявок"), false);
                    default -> {}
                }
            }
            case "mark" -> {
                Vec3 at = new Vec3(num(p, "x"), num(p, "y"), num(p, "z"));
                String server = str(p, "server");
                String dim = str(p, "dim");
                String name = str(p, "name");
                String label = str(p, "label");
                boolean here = Objects.equals(server, serverHere());
                if (here && Modules.MARKS.enabled())
                    Modules.MARKS.friendMark(
                            "friend:" + str(p, "from"), label == null ? name : name + " · " + label, at, dim);
                FriendCards.mark(name, label, at, here ? null : server);
            }
            case "inv_ask" -> answer(str(p, "from"), str(p, "name"));
            case "xhair" -> {
                // a friend sent a crosshair: it goes to the player's own presets, to try from the studio
                String code = str(p, "code");
                String name = Objects.requireNonNullElse(str(p, "name"), "?");
                Optional<Crosshair.Spec> spec = Crosshair.decode(code);
                if (spec.isPresent()) {
                    String title = Crosshair.nameOf(code);
                    boolean kept =
                            Modules.CROSSHAIR.keep(title == null ? Lang.tr("Прицел") + " " + name : title, spec.get());
                    note(
                            Lang.tr(kept ? "Прицел от друга сохранён" : "Прицел от друга: мои пресеты заполнены") + ": "
                                    + name,
                            kept);
                }
            }
            case "inv" -> {
                List<ItemStack> items = new ArrayList<>();
                boolean denied = p.has("denied");
                if (!denied && p.has("items")) items = items(p.getAsJsonArray("items"));
                shown = new InventoryView(str(p, "from"), str(p, "name"), items, denied, System.currentTimeMillis());
            }
            default -> {}
        }
    }

    private static List<Friend> sorted(List<Friend> list) {
        list.sort(Comparator.comparing((Friend f) -> !f.inWorld)
                .thenComparing(f -> !f.online)
                .thenComparing(f -> f.name.toLowerCase(Locale.ROOT)));
        return List.copyOf(list);
    }

    private static Friend friend(JsonObject o) {
        boolean pos = o.has("x") && !o.get("x").isJsonNull();
        return new Friend(
                str(o, "account"),
                Objects.requireNonNullElse(str(o, "name"), str(o, "account")),
                uuid(str(o, "uuid")),
                o.has("online") && o.get("online").getAsBoolean(),
                o.has("inWorld") && o.get("inWorld").getAsBoolean(),
                str(o, "server"),
                o.has("local") && o.get("local").getAsBoolean(),
                str(o, "dim"),
                pos ? new Vec3(num(o, "x") + 0.5, num(o, "y"), num(o, "z") + 0.5) : null);
    }

    private static Request request(JsonObject o) {
        return new Request(
                str(o, "account"), Objects.requireNonNullElse(str(o, "name"), str(o, "account")), uuid(str(o, "uuid")));
    }

    private static List<Request> requests(JsonArray a) {
        List<Request> out = new ArrayList<>();
        if (a != null) for (JsonElement e : a) out.add(request(e.getAsJsonObject()));
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static double num(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0;
    }

    private static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ what the player does

    public static void add(Player p) {
        JsonObject o = msg("friend_add");
        o.addProperty("uuid", p.getUUID().toString());
        send(o);
    }

    /** By a game nick or an Arcanic login. */
    public static void add(String name) {
        if (name == null || name.isBlank()) return;
        JsonObject o = msg("friend_add");
        o.addProperty("name", name.trim());
        send(o);
    }

    private static void about(String t, String account) {
        JsonObject o = msg(t);
        o.addProperty("account", account);
        send(o);
    }

    public static void accept(String account) {
        about("friend_accept", account);
        FriendCards.answered(account);
    }

    public static void decline(String account) {
        about("friend_decline", account);
        FriendCards.answered(account);
    }

    public static void cancel(String account) {
        about("friend_cancel", account);
    }

    public static void remove(String account) {
        about("friend_remove", account);
        if (account.equals(tracking)) track(null);
        note(Lang.tr("Убран из друзей"), false);
    }

    /** Sends a point to a friend: the mark under the crosshair or the last own one here, else where I look. */
    public static void shareMark(Friend f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Waypoints.Shared mark = Modules.MARKS.shareable();
        point(f, mark == null ? Waypoints.looking() : mark.at(), mark == null ? null : mark.name());
    }

    public static void sharePosition(Friend f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) point(f, mc.player.position(), Lang.tr("я здесь"));
    }

    private static void point(Friend f, Vec3 at, String label) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = msg("mark");
        o.addProperty("to", f.account);
        o.addProperty("x", Math.floor(at.x));
        o.addProperty("y", Math.floor(at.y));
        o.addProperty("z", Math.floor(at.z));
        o.addProperty("dim", mc.level.dimension().identifier().toString());
        o.addProperty("server", serverHere());
        if (label != null) o.addProperty("label", label);
        if (send(o)) note(Lang.tr("Метка отправлена") + ": " + f.name, true);
    }

    /** Sends a crosshair code to a friend: it lands in their own presets. */
    public static void sendCrosshair(Friend f, String code) {
        JsonObject o = msg("xhair");
        o.addProperty("to", f.account);
        o.addProperty("code", code);
        if (send(o)) note(Lang.tr("Прицел отправлен") + ": " + f.name, true);
    }

    public static void askInventory(Friend f) {
        shown = null;
        JsonObject o = msg("inv_ask");
        o.addProperty("to", f.account);
        send(o);
    }

    public static InventoryView shown(String account) {
        InventoryView i = shown;
        return i != null && i.account.equals(account) ? i : null;
    }

    private static void answer(String to, String name) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = msg("inv");
        o.addProperty("to", to);
        if (!sharesInventory() || mc.player == null || mc.level == null) {
            o.addProperty("denied", true);
        } else {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
            Inventory inv = mc.player.getInventory();
            JsonArray items = new JsonArray();
            for (int i = 0; i < Math.min(41, inv.getContainerSize()); i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty()) continue;
                JsonElement item = ItemStack.CODEC.encodeStart(ops, st).result().orElse(null);
                if (item == null || item.toString().length() > ITEM_LIMIT)
                    item = ItemStack.CODEC
                            .encodeStart(ops, new ItemStack(st.getItem(), st.getCount()))
                            .result()
                            .orElse(null);
                if (item == null) continue;
                JsonObject slot = new JsonObject();
                slot.addProperty("slot", i);
                slot.add("item", item);
                items.add(slot);
            }
            o.add("items", items);
            note(Lang.tr("Друг смотрит твой инвентарь") + ": " + name, true);
        }
        Sync.send(o);
    }

    private static List<ItemStack> items(JsonArray a) {
        NonNullList<ItemStack> out = NonNullList.withSize(41, ItemStack.EMPTY);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return out;
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
        for (JsonElement e : a) {
            JsonObject o = e.getAsJsonObject();
            int slot = o.get("slot").getAsInt();
            if (slot < 0 || slot >= 41) continue;
            ItemStack.CODEC.parse(ops, o.get("item")).result().ifPresent(st -> out.set(slot, st));
        }
        return out;
    }

    // ------------------------------------------------------------------ finding a friend

    public static String tracking() {
        return tracking;
    }

    /** A live mark over the friend, moved with them; null takes it away. */
    public static void track(String account) {
        if (tracking != null) Modules.MARKS.dropFriendMark("track:" + tracking);
        tracking = account;
        follow();
    }

    private static void follow() {
        if (tracking == null) return;
        Friend f = friend(tracking);
        if (f != null && f.near()) Modules.MARKS.friendMark("track:" + f.account, f.name, f.pos, f.dim);
        else Modules.MARKS.dropFriendMark("track:" + tracking);
    }

    public static void join(Friend f) {
        Minecraft mc = Minecraft.getInstance();
        if (!f.joinable()) return;
        String ip = f.server;
        if (mc.level != null) mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        ConnectScreen.startConnecting(
                new TitleScreen(),
                mc,
                ServerAddress.parseString(ip),
                new ServerData(f.name, ip, ServerData.Type.OTHER),
                false,
                null);
    }

    /** The friend's skin: from the world when they are here, else looked up by the nick like a player head. */
    public static PlayerSkin skin(String name, UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (AbstractClientPlayer p : mc.level.players()) if (is(p, name, uuid)) return p.getSkin();
            if (mc.getConnection() != null) {
                PlayerInfo info = uuid != null ? mc.getConnection().getPlayerInfo(uuid) : null;
                if (info == null) info = mc.getConnection().getPlayerInfo(name);
                if (info != null) return info.getSkin();
            }
        }
        Supplier<PlayerSkin> s = SKINS.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> {
            Supplier<PlayerSkinRenderCache.RenderInfo> info =
                    mc.playerSkinRenderCache().createLookup(ResolvableProfile.createUnresolved(name));
            return () -> info.get().playerSkin();
        });
        try {
            return s.get();
        } catch (RuntimeException e) {
            return uuid == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.get(uuid);
        }
    }
}
