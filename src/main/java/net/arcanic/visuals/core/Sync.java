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
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Sync {
    private static final String API = System.getProperty("arcanic.api", "https://arcanic.su");
    private static volatile String TOKEN = launcherOrSaved();
    private static final Map<UUID, Remote> REMOTE = new ConcurrentHashMap<>();
    private static final Map<String, Remote> BY_NAME = new ConcurrentHashMap<>();
    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PLAYING = new HashMap<>();
    private static HttpClient http;
    private static volatile boolean inFlight;
    private static volatile boolean refused;
    private static int ticks;
    private static String emoteName;
    private static long emoteAt;
    // the sync line: the entry server (its name, then its address), which passes it on to the main server; see SyncLine
    private static final List<String> LINE_HOSTS = Arrays.asList(
            System.getProperty("arcanic.line", "ru.arcanic.su,5.83.140.174").split(","));
    private static final int LINE_PORT = Integer.getInteger("arcanic.linePort", 8447);
    private static volatile SyncLine socket;
    private static volatile boolean connecting;
    private static volatile long retryAt;
    private static volatile int fails;
    private static volatile long pingAt;
    private static final Logger LOGGER = LoggerFactory.getLogger("arcanicvisuals");
    private static SyncLine greeted;
    private static String sentWho;
    private static String sentState;
    private static String sentSee;
    private static String sentEmote;
    private static final Component LOGO;

    private Sync() {}

    private static String launcherOrSaved() {
        String t = System.getenv("ARCANIC_TOKEN");
        if (t == null || t.isEmpty()) {
            t = System.getProperty("arcanic.token", "");
        }

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

    public static boolean signedIn() {
        return !TOKEN.isEmpty();
    }

    public static boolean on() {
        return !API.isEmpty() && !TOKEN.isEmpty() && !refused;
    }

    public static Remote of(UUID id) {
        return (Remote) REMOTE.get(id);
    }

    public static Remote of(Entity e) {
        Remote r = (Remote) REMOTE.get(e.getUUID());
        return r != null ? r : (Remote) BY_NAME.get(lower(e.getName().getString()));
    }

    public static void tick(Minecraft mc) {
        if (!on()) {
            leave();
            return;
        }
        SyncLine w = socket;
        if (w != null && !w.open()) {
            socket = null;
            w = null;
        }
        // the line stays open in the menu too: friends see each other there
        if (w == null && !connecting && System.currentTimeMillis() >= retryAt) {
            connect();
        }

        if (mc.player != null && mc.level != null) {
            Emotes.Emote playing = Modules.EMOTES.playing();
            String now = playing == null ? null : playing.name();
            if (!Objects.equals(now, emoteName)) {
                emoteName = now;
                emoteAt = System.currentTimeMillis();
                ticks = 20;
            }

            if (w != null) {
                live(mc, w);
            } else if (++ticks >= 20 && !inFlight) {
                ticks = 0;
                send(mc);
            }

            apply(mc);
        } else {
            outOfWorld(w);
        }

        if (w != null) {
            Social.tick(mc, w);
            // every 5 s a ping of our own, answered by the service with a pong: the game's network stack does not
            // answer the service's pings, so this is how both ends know the line is alive
            if (System.currentTimeMillis() - pingAt > 5000L) {
                pingAt = System.currentTimeMillis();
                JsonObject o = new JsonObject();
                o.addProperty("t", "ping");
                push(w, o);
            }
        }
    }

    /** Back in the menu: nobody is around, and the service hears that this game left its world. */
    private static void outOfWorld(SyncLine w) {
        REMOTE.clear();
        BY_NAME.clear();
        NAMES.clear();
        PLAYING.clear();
        if (w != null && greeted == w && sentWho != null) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "bye");
            push(w, o);
        }
        sentWho = null;
    }

    /** Sends a message on the open line, for the helpers outside this package; false when there is none. */
    public static boolean post(JsonObject o) {
        return send(o);
    }

    /** Sends a message on the open line; false when there is none. */
    static boolean send(JsonObject o) {
        SyncLine w = socket;
        if (w == null) {
            return false;
        }
        push(w, o);
        return true;
    }

    private static void leave() {
        REMOTE.clear();
        BY_NAME.clear();
        NAMES.clear();
        PLAYING.clear();
        SyncLine w = socket;
        if (w != null) {
            socket = null;
            greeted = null;
            Social.lost();
            w.close();
        }
    }

    private static JsonObject state() {
        JsonObject o = new JsonObject();
        String cape = Modules.CAPES.enabled() ? Capes.id((String) Modules.CAPES.design.get()) : null;
        if (cape != null) {
            o.addProperty("cape", cape);
        } else {
            o.add("cape", JsonNull.INSTANCE);
        }

        JsonObject wear = new JsonObject();
        String[][] slots = new String[][] {{"wings", "w"}, {"backpack", "b"}, {"companion", "c"}, {"headwear", "h"}};
        Wearables[] mods = new Wearables[] {Modules.WINGS, Modules.BACKPACKS, null, Modules.HEADWEAR};

        for (int i = 0; i < mods.length; ++i) {
            Wearables m = mods[i];
            Wearables.Item it = m != null && m.enabled() ? m.current() : null;
            if (it != null) {
                wear.addProperty(slots[i][0], String.valueOf(it.n()));
            }
        }

        o.add("wear", wear);
        return o;
    }

    private static JsonArray around(Minecraft mc) {
        JsonArray see = new JsonArray();

        for (AbstractClientPlayer pl : mc.level.players()) {
            if (pl != mc.player && see.size() < 200) {
                JsonObject p = new JsonObject();
                p.addProperty("uuid", pl.getUUID().toString());
                p.addProperty("name", pl.getName().getString());
                see.add(p);
            }
        }

        return see;
    }

    private static HttpClient client() {
        if (http == null) {
            http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5L))
                    .build();
        }

        return http;
    }

    private static void connect() {
        connecting = true;
        String token = TOKEN;
        Thread t = new Thread(
                () -> {
                    try {
                        SyncLine line = SyncLine.open(LINE_HOSTS, LINE_PORT, token, Sync::heard, Sync::lost);
                        fails = 0;
                        if (line.open()) {
                            socket = line;
                            LOGGER.info("Arcanic Social line open");
                        }
                    } catch (IOException | RuntimeException e) {
                        LOGGER.info("Arcanic Social line could not open: {}", String.valueOf(e));
                        failed();
                    } finally {
                        connecting = false;
                    }
                },
                "Arcanic line connect");
        t.setDaemon(true);
        t.start();
    }

    static void log(String text) {
        LOGGER.info(text);
    }

    /** A message from the service, on the line's reader thread. */
    private static void heard(String text) {
        try {
            handle(text);
        } catch (RuntimeException ignored) {
            // a broken message changes nothing
        }
    }

    private static void failed() {
        ++fails;
        retryAt = System.currentTimeMillis() + Math.min(60000L, 2000L << Math.min(fails, 5));
    }

    private static void lost(SyncLine w) {
        if (socket == w) {
            socket = null;
            Social.lost();
            failed();
            retryAt = Math.min(retryAt, System.currentTimeMillis() + 1000L); // a line that worked comes back at once
        }
        w.close();
    }

    private static void push(SyncLine w, JsonObject o) {
        w.send(o.toString());
    }

    private static void live(Minecraft mc, SyncLine w) {
        boolean second = ++ticks >= 20;
        if (greeted != w) {
            greeted = w;
            sentWho = null;
        }

        String var10000 = String.valueOf(mc.player.getUUID());
        String who = var10000 + "|" + mc.player.getName().getString();
        if (!who.equals(sentWho)) {
            sentWho = who;
            sentSee = null;
            sentState = null;
            sentEmote = "\u0000";
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
            if (emoteName != null) {
                o.addProperty("name", emoteName);
            } else {
                o.add("name", JsonNull.INSTANCE);
            }

            push(w, o);
        }

        if (second) {
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

            Set<UUID> ids = new HashSet<>();
            Set<String> names = new HashSet<>();

            for (AbstractClientPlayer pl : mc.level.players()) {
                ids.add(pl.getUUID());
                names.add(lower(pl.getName().getString()));
            }

            Iterator<Map.Entry<UUID, String>> it = NAMES.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<UUID, String> e = (Map.Entry) it.next();
                if (!ids.contains(e.getKey()) && !names.contains(e.getValue())) {
                    REMOTE.remove(e.getKey());
                    BY_NAME.remove(e.getValue());
                    it.remove();
                }
            }
        }
    }

    private static void handle(String text) {
        JsonObject p = JsonParser.parseString(text).getAsJsonObject();
        String t = str(p, "t");
        if ("denied".equals(t)) { // the token is no account's any more: stop until a new sign-in
            refused = true;
            return;
        }
        if (t != null && Social.handle(t, p)) {
            return;
        }
        String uuid = str(p, "uuid");
        if (t != null && uuid != null) {
            UUID id = UUID.fromString(uuid);
            switch (t) {
                case "player":
                    remember(id, str(p, "name"), remote(p));
                    break;
                case "emote":
                    Remote old = (Remote) REMOTE.get(id);
                    remember(
                            id,
                            (String) null,
                            old == null
                                    ? new Remote(str(p, "name"), (String) null, Map.of())
                                    : new Remote(str(p, "name"), old.cape(), old.wear()));
                    break;
                case "gone":
                    REMOTE.remove(id);
                    String n = (String) NAMES.remove(id);
                    if (n != null) {
                        BY_NAME.remove(n);
                    }
            }
        }
    }

    private static Remote remote(JsonObject p) {
        Map<String, String> wear = new HashMap<>();
        if (p.has("wear") && p.get("wear").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("wear").entrySet()) {
                if (((JsonElement) e.getValue()).isJsonPrimitive()) {
                    wear.put((String) e.getKey(), ((JsonElement) e.getValue()).getAsString());
                }
            }
        }

        return new Remote(str(p, "emote"), str(p, "cape"), Map.copyOf(wear));
    }

    private static void remember(UUID id, String name, Remote r) {
        REMOTE.put(id, r);
        String n = name == null ? (String) NAMES.get(id) : lower(name);
        if (n != null && !n.isEmpty()) {
            String was = (String) NAMES.put(id, n);
            if (was != null && !was.equals(n)) {
                BY_NAME.remove(was);
            }

            BY_NAME.put(n, r);
        }
    }

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
                .timeout(Duration.ofSeconds(6L))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TOKEN)
                .POST(BodyPublishers.ofString(o.toString()))
                .build();
        inFlight = true;
        client().sendAsync(req, BodyHandlers.ofString()).whenComplete((res, err) -> {
            inFlight = false;
            if (err == null && res != null) {
                if (res.statusCode() != 401 && res.statusCode() != 403) {
                    if (res.statusCode() == 200 && socket == null) {
                        try {
                            Map<UUID, Remote> fresh = new HashMap<>();
                            Map<UUID, String> names = new HashMap<>();

                            for (JsonElement e : JsonParser.parseString((String) res.body())
                                    .getAsJsonObject()
                                    .getAsJsonArray("players")) {
                                JsonObject p = e.getAsJsonObject();
                                UUID id = UUID.fromString(p.get("uuid").getAsString());
                                fresh.put(id, remote(p));
                                if (str(p, "name") != null) {
                                    names.put(id, lower(str(p, "name")));
                                }
                            }

                            REMOTE.keySet().retainAll(fresh.keySet());
                            REMOTE.putAll(fresh);
                            NAMES.keySet().retainAll(names.keySet());
                            NAMES.putAll(names);
                            BY_NAME.keySet().retainAll(new HashSet(names.values()));
                            names.forEach((idx, n) -> BY_NAME.put(n, (Remote) fresh.get(idx)));
                        } catch (RuntimeException ignored) {
                        }
                    }
                } else {
                    refused = true;
                    REMOTE.clear();
                    BY_NAME.clear();
                    NAMES.clear();
                }
            }
        });
    }

    public static void badge(Avatar entity, AvatarRenderState s) {
        if (s.nameTag != null && !REMOTE.isEmpty() && of((Entity) entity) != null) {
            s.nameTag = Component.empty().append(LOGO).append(" ").append(s.nameTag);
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static void apply(Minecraft mc) {
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p != mc.player) {
                Remote r = of((Entity) p);
                String want = r == null ? null : r.emote();
                String had = (String) PLAYING.get(p.getUUID());
                if (!Objects.equals(want, had) && Emotes.playOn(p, want)) {
                    if (want == null) {
                        PLAYING.remove(p.getUUID());
                    } else {
                        PLAYING.put(p.getUUID(), want);
                    }
                }
            }
        }

        PLAYING.keySet().removeIf((id) -> mc.level.getPlayerByUUID(id) == null);
    }

    static {
        LOGO = Component.literal("\ue100")
                .withStyle(Style.EMPTY
                        .withFont(new FontDescription.Resource(
                                Identifier.fromNamespaceAndPath("arcanicvisuals", "nametag")))
                        .withColor(0xFFFFFF));
    }

    public static record Remote(String emote, String cape, Map<String, String> wear) {
        public String worn(String slot) {
            return this.wear == null ? null : (String) this.wear.get(slot);
        }
    }
}
