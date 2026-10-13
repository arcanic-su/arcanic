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

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import net.arcanic.visuals.arc.Capes;
import net.arcanic.visuals.cosmetic.Wearables;
import net.arcanic.visuals.emote.Emotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;

/**
 * Emotes, capes and everything else worn (wings, backpacks, companions, headwear) seen by other players with the
 * client.
 */
public final class Sync {
    private Sync() {}

    /** What another player with the client is showing. */
    public record Remote(String emote, String cape, Map<String, String> wear) {
        public String worn(String slot) {
            return wear == null ? null : wear.get(slot);
        }
    }

    private static final String API = System.getProperty("arcanic.api", "https://arcanic.su");
    // from the launcher, or from a sign-in made in the game (the Arcanic Social screen)
    private static volatile String TOKEN = launcherOrSaved();

    private static String launcherOrSaved() {
        String t = System.getenv("ARCANIC_TOKEN");
        if (t == null || t.isEmpty()) t = System.getProperty("arcanic.token", "");
        return t.isEmpty() ? ArcanicAccount.savedToken() : t;
    }

    static String api() {
        return API;
    }

    static String token() {
        return TOKEN;
    }

    static void setToken(String token) {
        TOKEN = token == null ? "" : token;
        refused = false;
        Minecraft.getInstance().execute(Sync::leave);
    }

    private static final Map<UUID, Remote> REMOTE = new ConcurrentHashMap<>();
    private static final Map<String, Remote> BY_NAME =
            new ConcurrentHashMap<>(); // some networks show a player under another UUID
    private static final Map<UUID, String> NAMES =
            new ConcurrentHashMap<>(); // whose nick is whose, to forget both together
    private static final Map<UUID, String> PLAYING = new HashMap<>();
    private static HttpClient http;
    private static volatile boolean inFlight, refused;
    private static int ticks;
    private static String emoteName;
    private static long emoteAt;

    // ---- the live line
    private static volatile WebSocket socket;
    private static volatile boolean connecting;
    private static volatile long retryAt;
    private static volatile int fails;
    private static WebSocket greeted; // the socket our hello went to
    private static String sentWho, sentState, sentSee, sentEmote;
    private static CompletableFuture<?> tail = CompletableFuture.completedFuture(null); // sends go one after another

    public static boolean signedIn() {
        return !TOKEN.isEmpty();
    }

    public static boolean on() {
        return !API.isEmpty() && !TOKEN.isEmpty() && !refused;
    }

    public static Remote of(UUID id) {
        return REMOTE.get(id);
    }

    public static Remote of(Entity e) {
        Remote r = REMOTE.get(e.getUUID());
        return r != null ? r : BY_NAME.get(lower(e.getName().getString()));
    }

    /** Every client tick: our own changes go out, and the remote emotes are started or stopped on their players. */
    public static void tick(Minecraft mc) {
        if (!on() || mc.player == null || mc.level == null) {
            leave();
            return;
        }
        var playing = Modules.EMOTES.playing();
        String now = playing == null ? null : playing.name();
        if (!Objects.equals(now, emoteName)) {
            emoteName = now;
            emoteAt = System.currentTimeMillis();
            ticks = 20;
        } // send a change at once
        WebSocket w = socket;
        if (w != null) live(mc, w);
        else {
            if (!connecting && System.currentTimeMillis() >= retryAt) connect();
            if (++ticks >= 20 && !inFlight) {
                ticks = 0;
                send(mc);
            } // no line (yet): once a second over HTTP
        }
        apply(mc);
    }

    private static void leave() {
        REMOTE.clear();
        BY_NAME.clear();
        NAMES.clear();
        PLAYING.clear();
        WebSocket w = socket;
        if (w != null) {
            socket = null;
            greeted = null;
            try {
                w.sendClose(WebSocket.NORMAL_CLOSURE, "");
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static JsonObject state() {
        JsonObject o = new JsonObject();
        String cape = Modules.CAPES.enabled() ? Capes.id(Modules.CAPES.design.get()) : null;
        if (cape != null) o.addProperty("cape", cape);
        else o.add("cape", JsonNull.INSTANCE);
        // everything else worn: the wearable models by their number
        JsonObject wear = new JsonObject();
        String[][] slots = {{"wings", "w"}, {"backpack", "b"}, {"companion", "c"}, {"headwear", "h"}};
        Wearables[] mods = {Modules.WINGS, Modules.BACKPACKS, Modules.COMPANIONS, Modules.HEADWEAR};
        for (int i = 0; i < mods.length; i++) {
            var m = mods[i];
            var it = m != null && m.enabled() ? m.current() : null;
            if (it != null) wear.addProperty(slots[i][0], String.valueOf(it.n()));
        }
        o.add("wear", wear);
        return o;
    }

    private static JsonArray around(Minecraft mc) {
        JsonArray see = new JsonArray();
        for (var pl : mc.level.players()) {
            if (pl == mc.player || see.size() >= 200) continue;
            JsonObject p = new JsonObject();
            p.addProperty("uuid", pl.getUUID().toString());
            p.addProperty("name", pl.getName().getString());
            see.add(p);
        }
        return see;
    }

    private static HttpClient client() {
        if (http == null)
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return http;
    }

    private static void connect() {
        connecting = true;
        try {
            URI uri = URI.create(
                    (API.startsWith("https") ? "wss" + API.substring(5) : "ws" + API.substring(4)) + "/api/v1/ws");
            client().newWebSocketBuilder()
                    .header("Authorization", "Bearer " + TOKEN)
                    .connectTimeout(Duration.ofSeconds(8))
                    .buildAsync(uri, new Line())
                    .whenComplete((w, err) -> {
                        connecting = false;
                        if (err != null) failed();
                    });
        } catch (RuntimeException e) {
            connecting = false;
            failed();
        }
    }

    private static void failed() {
        fails++;
        retryAt = System.currentTimeMillis() + Math.min(60_000L, 2000L << Math.min(fails, 5));
    }

    private static final class Line implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void onOpen(WebSocket w) {
            fails = 0;
            socket = w;
            w.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String s = buf.toString();
                buf.setLength(0);
                try {
                    handle(s);
                } catch (RuntimeException ignored) {
                }
            } else if (buf.length() > 1 << 20) buf.setLength(0);
            w.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
            lost(w);
            return null;
        }

        @Override
        public void onError(WebSocket w, Throwable e) {
            lost(w);
        }
    }

    private static void lost(WebSocket w) {
        if (socket == w) {
            socket = null;
            failed();
        }
    }

    private static synchronized void push(WebSocket w, JsonObject o) {
        String s = o.toString();
        tail = tail.handle((a, b) -> null).thenCompose(x -> w.sendText(s, true)).exceptionally(e -> null);
    }

    private static void live(Minecraft mc, WebSocket w) {
        boolean second = ++ticks >= 20;
        if (greeted != w) {
            greeted = w;
            sentWho = null;
        }
        String who = mc.player.getUUID() + "|" + mc.player.getName().getString();
        if (!who.equals(sentWho)) { // a new line, or a server that gave us another UUID
            sentWho = who;
            sentState = sentSee = null;
            sentEmote = "\0";
            second = true;
            JsonObject o = new JsonObject();
            o.addProperty("t", "hello");
            o.addProperty("uuid", mc.player.getUUID().toString());
            o.addProperty("name", mc.player.getName().getString());
            push(w, o);
        }
        if (!Objects.equals(emoteName, sentEmote)) {
            sentEmote = emoteName;
            JsonObject o = new JsonObject();
            o.addProperty("t", "emote");
            if (emoteName != null) o.addProperty("name", emoteName);
            else o.add("name", JsonNull.INSTANCE);
            push(w, o);
        }
        if (!second) return;
        ticks = 0;
        JsonObject st = state();
        String s = st.toString();
        if (!s.equals(sentState)) {
            sentState = s;
            st.addProperty("t", "state");
            push(w, st);
        }
        JsonArray see = around(mc);
        String k = see.toString();
        if (!k.equals(sentSee)) {
            sentSee = k;
            JsonObject o = new JsonObject();
            o.addProperty("t", "see");
            o.add("players", see);
            push(w, o);
        }
        // whoever left our view is forgotten: when they come back the service tells us afresh what they show
        Set<UUID> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (var pl : mc.level.players()) {
            ids.add(pl.getUUID());
            names.add(lower(pl.getName().getString()));
        }
        for (var it = NAMES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!ids.contains(e.getKey()) && !names.contains(e.getValue())) {
                REMOTE.remove(e.getKey());
                BY_NAME.remove(e.getValue());
                it.remove();
            }
        }
    }

    private static void handle(String text) {
        JsonObject p = JsonParser.parseString(text).getAsJsonObject();
        String t = str(p, "t"), uuid = str(p, "uuid");
        if (t == null || uuid == null) return;
        UUID id = UUID.fromString(uuid);
        switch (t) {
            case "player" -> remember(id, str(p, "name"), remote(p));
            case "emote" -> {
                Remote old = REMOTE.get(id);
                remember(
                        id,
                        null,
                        old == null
                                ? new Remote(str(p, "name"), null, Map.of())
                                : new Remote(str(p, "name"), old.cape(), old.wear()));
            }
            case "gone" -> {
                REMOTE.remove(id);
                String n = NAMES.remove(id);
                if (n != null) BY_NAME.remove(n);
            }
            default -> {}
        }
    }

    private static Remote remote(JsonObject p) {
        Map<String, String> wear = new HashMap<>();
        if (p.has("wear") && p.get("wear").isJsonObject())
            for (var e : p.getAsJsonObject("wear").entrySet())
                if (e.getValue().isJsonPrimitive())
                    wear.put(e.getKey(), e.getValue().getAsString());
        return new Remote(str(p, "emote"), str(p, "cape"), Map.copyOf(wear));
    }

    private static void remember(UUID id, String name, Remote r) {
        REMOTE.put(id, r);
        String n = name == null ? NAMES.get(id) : lower(name);
        if (n == null || n.isEmpty()) return;
        String was = NAMES.put(id, n);
        if (was != null && !was.equals(n)) BY_NAME.remove(was);
        BY_NAME.put(n, r);
    }

    // (no line)
    private static void send(Minecraft mc) {
        JsonObject o = state();
        o.addProperty("uuid", mc.player.getUUID().toString());
        o.addProperty("name", mc.player.getName().getString());
        if (emoteName != null) {
            o.addProperty("emote", emoteName);
            o.addProperty("emoteAt", emoteAt);
        }
        o.add("see", around(mc));
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "/api/v1/sync"))
                .timeout(Duration.ofSeconds(6))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build();
        inFlight = true;
        client().sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            inFlight = false;
            if (err != null || res == null) return; // offline for a moment: keep what we have
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                refused = true;
                REMOTE.clear();
                BY_NAME.clear();
                NAMES.clear();
                return;
            }
            if (res.statusCode() != 200 || socket != null) return;
            try {
                Map<UUID, Remote> fresh = new HashMap<>();
                Map<UUID, String> names = new HashMap<>();
                for (var e :
                        JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("players")) {
                    JsonObject p = e.getAsJsonObject();
                    UUID id = UUID.fromString(p.get("uuid").getAsString());
                    fresh.put(id, remote(p));
                    if (str(p, "name") != null) names.put(id, lower(str(p, "name")));
                }
                REMOTE.keySet().retainAll(fresh.keySet());
                REMOTE.putAll(fresh);
                NAMES.keySet().retainAll(names.keySet());
                NAMES.putAll(names);
                BY_NAME.keySet().retainAll(new HashSet<>(names.values()));
                names.forEach((id, n) -> BY_NAME.put(n, fresh.get(id)));
            } catch (RuntimeException ignored) {
            }
        });
    }

    private static final Component LOGO = Component.literal("")
            .withStyle(Style.EMPTY
                    .withFont(
                            new FontDescription.Resource(Identifier.fromNamespaceAndPath("arcanicvisuals", "nametag")))
                    .withColor(0xFFFFFF));

    public static void badge(Avatar entity, AvatarRenderState s) {
        if (s.nameTag == null || REMOTE.isEmpty() || of(entity) == null) return;
        s.nameTag = Component.empty().append(LOGO).append(" ").append(s.nameTag);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static void apply(Minecraft mc) {
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player) continue;
            Remote r = of(p);
            String want = r == null ? null : r.emote(), had = PLAYING.get(p.getUUID());
            if (Objects.equals(want, had)) continue;
            if (Emotes.playOn(p, want)) {
                if (want == null) PLAYING.remove(p.getUUID());
                else PLAYING.put(p.getUUID(), want);
            }
        }
        PLAYING.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
    }
}
