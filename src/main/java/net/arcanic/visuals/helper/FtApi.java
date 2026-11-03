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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.arcanic.visuals.render.TexWarm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;

/** Every FunTime anarchy's events from funtime.me, as HolyWorld's come from its API. */
public final class FtApi {
    private static final String URL = "https://funtime.me/api/backend/api/v1/events?event-type=all&server-type=";
    private static final long POLL = 5000L;
    /** The site's own names for its events. */
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("airdrop", "Аирдроп"),
            Map.entry("altarundead", "Алтарь нежити"),
            Map.entry("beacon", "Маяк-убийца"),
            Map.entry("geyser", "Гейзер"),
            Map.entry("myst_beacon", "Загадочный маяк"),
            Map.entry("hellm", "Адская резня"),
            Map.entry("deathchest", "Сундук Смерти"),
            Map.entry("mystic", "Мистический сундук"),
            Map.entry("altar", "Алтарь"),
            Map.entry("vulkan", "Вулкан"),
            Map.entry("volcano", "Вулкан"),
            Map.entry("santa", "Санта"),
            Map.entry("meteor_rain", "Метеоритный дождь"),
            Map.entry("meteor", "Метеорит"),
            Map.entry("portal", "Портал"),
            Map.entry("cursed_graveyard", "Проклятое кладбище"),
            Map.entry("vote", "Голосование"),
            Map.entry("voting", "Голосование"));

    private static final Map<String, String> PHASES = Map.ofEntries(
            Map.entry("RUNNING", "Идёт"),
            Map.entry("LOOTING", "Лут готов"),
            Map.entry("OPENED", "Открыт"),
            Map.entry("WAITING", "Ожидание"),
            Map.entry("PREPARING", "Готовится"),
            Map.entry("STARTING", "Запускается"),
            Map.entry("ACTIVATING", "Активация"),
            Map.entry("STOPPING", "Завершается"),
            Map.entry("FINISHED", "Завершён"),
            Map.entry("CLOSED", "Завершён"),
            Map.entry("VOTING", "Голосование"));
    /** The picture a kind of event is shown with; the same picture serves both names of the volcano. */
    private static final Map<String, String> PICTURE = Map.ofEntries(
            Map.entry("airdrop", "airdrop"),
            Map.entry("altarundead", "altarundead"),
            Map.entry("beacon", "beacon"),
            Map.entry("geyser", "geyser"),
            Map.entry("myst_beacon", "myst_beacon"),
            Map.entry("hellm", "hellm"),
            Map.entry("deathchest", "deathchest"),
            Map.entry("mystic", "mystic"),
            Map.entry("altar", "altar"),
            Map.entry("vulkan", "volcano"),
            Map.entry("volcano", "volcano"),
            Map.entry("meteor_rain", "meteor"),
            Map.entry("meteor", "meteor"));

    private static final Map<String, Boolean> PRESENT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> LIVE = Set.of("LOOTING", "OPENED", "RUNNING", "ACTIVATING");

    /** One event on one anarchy. */
    public record Event(
            int anarchy, String id, String name, String phase, String loot, long endsAt, int[] at, boolean live) {}

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();
    private static volatile List<Event> events = List.of();
    private static boolean busy;
    private static long nextPoll;
    private static long answeredAt;

    private FtApi() {}

    public static List<Event> events() {
        return events;
    }

    public static long answeredAt() {
        return answeredAt;
    }

    static void tick() {
        long now = System.currentTimeMillis();
        if (!FunTime.here()) {
            events = List.of();
            return;
        }
        if (busy || now < nextPoll) return;
        busy = true;
        nextPoll = now + POLL;
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(10L))
                .header("User-Agent", "ArcanicVisuals")
                .header("Accept", "application/json")
                .header("Referer", "https://funtime.me/events")
                .GET()
                .build();
        HTTP.sendAsync(req, BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((res, err) -> {
            List<Event> read = null;
            if (err == null && res.statusCode() == 200) {
                try {
                    read = read(res.body(), System.currentTimeMillis());
                } catch (RuntimeException ignored) {
                }
            }
            List<Event> done = read;
            Minecraft.getInstance().execute(() -> {
                busy = false;
                if (done != null) {
                    events = done;
                    answeredAt = System.currentTimeMillis();
                }
            });
        });
    }

    private static List<Event> read(String body, long now) {
        List<Event> out = new ArrayList<>();
        JsonArray servers = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("response");
        for (JsonElement s : servers) {
            JsonObject server = s.getAsJsonObject();
            int anarchy = number(server.get("server").getAsString());
            for (JsonElement el : server.getAsJsonArray("events")) {
                JsonObject e = el.getAsJsonObject();
                if (!e.has("id") || e.get("id").isJsonNull()) continue; // the next system event, not yet known
                String id = e.get("id").getAsString().toLowerCase();
                String phase = e.has("phase") && !e.get("phase").isJsonNull()
                        ? e.get("phase").getAsString()
                        : "";
                String loot = e.has("loot") && !e.get("loot").isJsonNull()
                        ? e.get("loot").getAsString()
                        : "";
                long left = e.has("time-ms-left")
                        ? e.get("time-ms-left").getAsLong()
                        : e.get("time-seconds-left").getAsLong() * 1000L;
                int[] at = null;
                if (e.has("location-event") && e.get("location-event").isJsonObject()) {
                    JsonObject l = e.getAsJsonObject("location-event");
                    at = new int[] {
                        l.get("x").getAsInt(), l.get("y").getAsInt(), l.get("z").getAsInt()
                    };
                }
                out.add(new Event(
                        anarchy,
                        id,
                        NAMES.getOrDefault(id, "Событие"),
                        PHASES.getOrDefault(phase, phase),
                        loot,
                        left > 0 ? now + left : 0L,
                        at,
                        LIVE.contains(phase)));
            }
        }
        out.sort(Comparator.comparing((Event e) -> !e.live())
                .thenComparing(e -> -lootRank(e.loot()))
                .thenComparingInt(Event::anarchy));
        return List.copyOf(out);
    }

    private static int number(String server) {
        String n = server.replaceAll("\\D+", "");
        return n.isEmpty() || n.length() > 6 ? -1 : Integer.parseInt(n);
    }

    public static int lootRank(String loot) {
        return switch (loot) {
            case "Проклятые сокровища" -> 5;
            case "Легендарный" -> 4;
            case "Элитный" -> 3;
            case "Богатый" -> 2;
            case "Солидный" -> 1;
            default -> 0;
        };
    }

    public static int lootColour(String loot) {
        return switch (loot) {
            case "Проклятые сокровища" -> -50373;
            case "Легендарный" -> -19641;
            case "Элитный" -> -5141249;
            case "Богатый" -> -9518593;
            case "Солидный" -> -8658784;
            default -> -7696222;
        };
    }

    /** The event's picture, or null while it is not there (shown as a coloured dot then). */
    public static Identifier picture(String id) {
        String p = PICTURE.get(id);
        if (p == null) return null;
        Identifier tex = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/funtime/" + p + ".png");
        boolean there = PRESENT.computeIfAbsent(p, k -> Minecraft.getInstance()
                .getResourceManager()
                .getResource(tex)
                .isPresent());
        return there && TexWarm.ready(tex) ? tex : null;
    }

    /** Over to that anarchy. */
    public static void join(Event e) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null && e.anarchy() > 0) conn.sendCommand("an" + e.anarchy());
    }
}
