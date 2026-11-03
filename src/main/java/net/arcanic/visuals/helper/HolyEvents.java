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
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.mixin.BossHealthOverlayAccess;
import net.arcanic.visuals.render.Px;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class HolyEvents extends Module {
    private static final String API = "https://api.holyworld.me/v1/";
    private static final long POLL = 5000L;
    private static final long LIFE = 14000L;
    private static final long FRESH = 180000L;
    private final Setting.Bool sound = this.add(new Setting.Bool("sound", "Звук уведомления", true));
    private final Setting.Bool showCommon = this.add(new Setting.Bool("show_common", "Обычные", true));
    private final Setting.Bool showRare = this.add(new Setting.Bool("show_rare", "Редкие", true));
    private final Setting.Bool showEpic = this.add(new Setting.Bool("show_epic", "Эпические", true));
    private final Setting.Bool showLegendary = this.add(new Setting.Bool("show_legendary", "Легендарные", true));
    private final Setting.Bool showMythic = this.add(new Setting.Bool("show_mythic", "Мифические", true));
    private final Setting.Bool showBoss = this.add(new Setting.Bool("show_boss", "Боссы", true));
    private static final Set<String> HEADED = Set.of(
            "boss",
            "cargo",
            "golden_fortress",
            "ship",
            "snowquarry",
            "container",
            "parcels",
            "jaycob",
            "mine",
            "end",
            "cube");
    private static final Map<String, Identifier> HEADS = new HashMap<>();
    public static final Identifier LOGO =
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/holyworld/logo.png");
    public static final int LOGO_W = 103;
    public static final int LOGO_H = 96;
    public static final int HEAD = 38;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();
    private final Set<String> seen = new HashSet<>();
    private final Map<String, Long> lastSeen = new HashMap<>();
    private final Map<String, Event> alive = new LinkedHashMap<>();
    private final Map<String, Long> born = new HashMap<>();
    private final Set<String> before = new HashSet<>();
    private final List<Flag> flags = new ArrayList<>();
    private Map<String, String> names = Map.of();
    private List<Event> current = List.of();
    private boolean seeded;
    private boolean busy;
    private boolean wasDown;
    private long nextPoll;
    private long lastAnswer;
    private String here;
    private String livePhase;
    private String liveTime;
    private long liveMax;
    private static final Map<String, Long> WAIT = Map.of("CUBE", 480000L);
    private static final Map<String, Long> RUN = Map.of(
            "JAYCOB",
            1200000L,
            "CUBE",
            600000L,
            "CARGO",
            900000L,
            "CONTAINER",
            900000L,
            "PARCELS",
            900000L,
            "SHIP",
            1200000L,
            "GOLDEN_FORTRESS",
            900000L,
            "SNOWQUARRY",
            900000L,
            "MINE",
            900000L);
    private static final long RUN_DEFAULT = 900000L;
    private static final Path LEARNED =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/holyworld-durations.json");
    private final Map<String, long[]> learned = new HashMap<>();
    private boolean learnedRead;
    private static final Pattern TIMER =
            Pattern.compile("(\\d{1,2}:\\d{2}(?::\\d{2})?)|(\\d+\\s*(?:сек|с\\b|мин|м\\b))");
    private static final Pattern SERVER =
            Pattern.compile("(соло|дуо|трио|клан)?\\s*лайт\\s*(?:\\(1\\.20\\)\\s*)?#\\s*(\\d+)", 2);
    private static final Map<String, Integer> HEAD_COLOUR = Map.ofEntries(
            Map.entry("boss", 0xFFA8B23C),
            Map.entry("cargo", 0xFFED7A1C),
            Map.entry("golden_fortress", 0xFFB0B4BC),
            Map.entry("ship", 0xFF2E7CDC),
            Map.entry("snowquarry", 0xFFE58AD1),
            Map.entry("container", 0xFFE8463F),
            Map.entry("parcels", 0xFF3FA9C9),
            Map.entry("jaycob", 0xFF71B158),
            Map.entry("mine", 0xFF8E5BD6),
            Map.entry("end", 0xFF15B1F4),
            Map.entry("cube", -11750));

    public static int stageColour(int stage) {
        return stage == 2 ? 0xFF5BD68A : (stage == 1 ? 0xFFF2C14E : (stage == 0 ? -41620 : 0xFF8A90A2));
    }

    public static Identifier head(String type) {
        if (type != null && !type.isEmpty()) {
            String t = type.toLowerCase(Locale.ROOT);
            if (t.contains("boss")) {
                t = "boss";
            } else if (t.equals("end") || t.startsWith("end_") || t.contains("dragon")) {
                t = "end";
            }

            return !HEADED.contains(t)
                    ? null
                    : (Identifier) HEADS.computeIfAbsent(
                            t,
                            (k) -> Identifier.fromNamespaceAndPath(
                                    "arcanicvisuals", "textures/gui/holyworld/" + k + ".png"));
        } else {
            return null;
        }
    }

    public static void picture(GuiGraphics g, Identifier tex, int tw, int th, int x, int y, int w, int h) {
        float k = Math.min((float) w / (float) tw, (float) h / (float) th);
        int dw = Math.round((float) tw * k);
        int dh = Math.round((float) th * k);
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                tex,
                x + (w - dw) / 2,
                y + (h - dh) / 2,
                0.0F,
                0.0F,
                dw,
                dh,
                tw,
                th,
                tw,
                th,
                Px.am(-1, Px.fade));
    }

    public HolyEvents() {
        super(
                "holy_events",
                "Ивенты HolyWorld",
                "Флажок о новом ивенте, его фаза и время, переход на анархию по кнопке",
                Category.HOLYWORLD,
                true);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static boolean onHolyWorld() {
        ServerData d = mc().getCurrentServer();
        return d != null && d.ip != null && d.ip.toLowerCase(Locale.ROOT).contains("holyworld");
    }

    public List<Event> current() {
        return this.current;
    }

    public long lastAnswer() {
        return this.lastAnswer;
    }

    public String here() {
        return this.here;
    }

    public void tick() {
        Minecraft mc = mc();
        AnarchyJump.tick();
        if (mc.player != null && onHolyWorld()) {
            long now = System.currentTimeMillis();
            this.flags.removeIf((f) -> now - f.born() > 14000L);
            if (!this.busy && now >= this.nextPoll) {
                this.busy = true;
                this.nextPoll = now + 5000L;
                if (this.names.isEmpty()) {
                    this.get("servers", this::readNames);
                }

                this.get("events", this::readEvents);
            }

            if (mc.level != null && mc.level.getGameTime() % 10L == 0L) {
                this.readScreen();
                HolyRules.scan();
            }

        } else {
            this.seeded = false;
            this.seen.clear();
            this.lastSeen.clear();
            this.alive.clear();
            this.born.clear();
            this.before.clear();
            this.flags.clear();
            this.current = List.of();
            this.here = null;
        }
    }

    private void get(String path, Consumer<String> then) {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.holyworld.me/v1/" + path))
                .timeout(Duration.ofSeconds(10L))
                .header("User-Agent", "ArcanicVisuals")
                .GET()
                .build();
        this.http.sendAsync(req, BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((res, err) -> mc().execute(
                        () -> {
                            if (path.equals("events")) {
                                this.busy = false;
                            }

                            if (err == null && res.statusCode() == 200) {
                                try {
                                    then.accept((String) res.body());
                                } catch (RuntimeException ignored) {
                                }
                            }
                        }));
    }

    private void readNames(String body) {
        Map<String, String> out = new HashMap<>();
        JsonElement root = JsonParser.parseString(body);
        if (root.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                if (((JsonElement) e.getValue()).isJsonPrimitive()) {
                    out.put(e.getKey(), plain(((JsonElement) e.getValue()).getAsString()));
                }
            }
        }

        this.names = out;
    }

    private void readEvents(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (root.isJsonObject()) {
            List<Event> all = new ArrayList<>();

            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                JsonElement list = (JsonElement) e.getValue();
                if (list.isJsonArray()) {
                    for (JsonElement ev : list.getAsJsonArray()) {
                        all.add(event(e.getKey(), ev));
                    }
                } else {
                    all.add(event(e.getKey(), list));
                }
            }

            long time = System.currentTimeMillis();
            this.lastAnswer = time;

            for (Event ev : all) {
                String k = key(ev);
                this.lastSeen.put(k, time);
                this.alive.put(k, ev);
                if (!this.born.containsKey(k)) {
                    this.born.put(k, time);
                    if (!this.seeded) {
                        this.before.add(k);
                    }
                }

                boolean twin = this.alive.values().stream()
                        .anyMatch((o) -> o != ev
                                && !key(o).equals(k)
                                && o.server().equals(ev.server())
                                && o.type().equals(ev.type()));
                if (this.seeded && !this.seen.contains(k) && !twin) {
                    this.announce(ev, false);
                }

                if (this.seeded
                        && !this.seen.contains(k)
                        && !twin
                        && ev.server().equals(this.here)) {
                    Modules.MARKS.startedHere(ev);
                }

                this.seen.add(k);
            }

            Set<String> ask = new HashSet<>();
            Iterator<Map.Entry<String, Event>> it = this.alive.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<String, Event> en = (Map.Entry) it.next();
                long last = (Long) this.lastSeen.getOrDefault(en.getKey(), 0L);
                if (last != time) {
                    if (time - last > 90000L) {
                        this.over(en.getKey(), (Event) en.getValue(), last);
                        it.remove();
                    } else {
                        ask.add(((Event) en.getValue()).server());
                    }
                }
            }

            for (String s : ask) {
                this.get("events?server=" + s, (b) -> this.confirm(s, b));
            }

            if (!this.seeded && !this.alive.isEmpty()) {
                this.ongoing(new ArrayList(this.alive.values()));
            }

            this.seeded = true;
            this.publish();
        }
    }

    private void confirm(String server, String body) {
        JsonElement root = JsonParser.parseString(body);
        JsonElement list = root.isJsonObject() ? root.getAsJsonObject().get(server) : root;
        Set<String> there = new HashSet<>();
        if (list != null && list.isJsonArray()) {
            for (JsonElement ev : list.getAsJsonArray()) {
                there.add(key(event(server, ev)));
            }
        }

        long time = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<String, Event>> it = this.alive.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Event> en = (Map.Entry) it.next();
            if (((Event) en.getValue()).server().equals(server)) {
                if (there.contains(en.getKey())) {
                    this.lastSeen.put(en.getKey(), time);
                } else {
                    this.over(en.getKey(), (Event) en.getValue(), (Long) this.lastSeen.getOrDefault(en.getKey(), time));
                    it.remove();
                    changed = true;
                }
            }
        }

        if (changed) {
            this.publish();
        }
    }

    private void over(String k, Event e, long last) {
        if (!this.before.contains(k)) {
            this.learn(e, last - (Long) this.born.getOrDefault(k, last));
        }

        Modules.MARKS.ended(e);
    }

    private void publish() {
        this.lastSeen.keySet().retainAll(this.alive.keySet());
        this.born.keySet().retainAll(this.alive.keySet());
        this.before.retainAll(this.alive.keySet());
        this.seen.retainAll(this.alive.keySet());
        Map<String, Event> one = new LinkedHashMap<>();

        for (Event ev : this.alive.values()) {
            one.putIfAbsent(ev.server() + "|" + ev.type(), ev);
        }

        this.current = List.copyOf(one.values());
    }

    private static String key(Event e) {
        return e.server() + "|" + e.id();
    }

    private static Event event(String server, JsonElement e) {
        if (!e.isJsonObject()) {
            String n = e.isJsonPrimitive() ? plain(e.getAsString()) : "Ивент";
            return new Event(server, "", n, "", n);
        } else {
            JsonObject o = e.getAsJsonObject();
            JsonObject meta = o.has("metadata") && o.get("metadata").isJsonObject() ? o.getAsJsonObject("metadata") : o;
            String type = text(o, "id", "type");
            String name = text(meta, "displayName", "name", "title");
            if (name == null) {
                name = type;
            }

            String rare = type != null && type.toLowerCase(Locale.ROOT).contains("boss")
                    ? "Босс"
                    : rarity(text(meta, "rare", "rarity"));
            if (rare.isEmpty()) {
                rare = ownRarity(type);
            }

            String id = text(o, "instanceId", "uuid");
            if (id == null) {
                id = type + "@" + rare;
            }

            return new Event(server, type == null ? "" : type, name == null ? "Ивент" : name, rare, id);
        }
    }

    private static String text(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k)
                    && o.get(k).isJsonPrimitive()
                    && !o.get(k).getAsString().isBlank()) {
                return plain(o.get(k).getAsString());
            }
        }

        return null;
    }

    private static String rarity(String r) {
        if (r == null) {
            return "";
        } else {
            String c = r.toLowerCase(Locale.ROOT);
            if (!c.startsWith("legendary") && !c.startsWith("легенд")) {
                if (!c.startsWith("epic") && !c.startsWith("эпич")) {
                    if (!c.startsWith("rare") && !c.startsWith("редк")) {
                        if (!c.startsWith("mythic") && !c.startsWith("миф")) {
                            if (c.startsWith("peaceful")) {
                                return "Мирный";
                            } else if (c.startsWith("explosive")) {
                                return "Взрывной";
                            } else if (c.startsWith("deadly")) {
                                return "Смертельный";
                            } else if (!c.contains("rich") && !c.startsWith("зажит")) {
                                if (!c.contains("lux") && !c.startsWith("роскош")) {
                                    return !c.startsWith("normal")
                                                    && !c.startsWith("common")
                                                    && !c.startsWith("обычн")
                                                    && !c.contains("_default")
                                            ? ""
                                            : "Обычный";
                                } else {
                                    return "Роскошный";
                                }
                            } else {
                                return "Зажиточный";
                            }
                        } else {
                            return "Мифический";
                        }
                    } else {
                        return "Редкий";
                    }
                } else {
                    return "Эпический";
                }
            } else {
                return "Легендарный";
            }
        }
    }

    static String plain(String s) {
        return s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "").trim();
    }

    public String serverName(String id) {
        return (String) this.names.getOrDefault(id, id);
    }

    private long bornAt(Event e) {
        return (Long) this.born.getOrDefault(key(e), System.currentTimeMillis());
    }

    public boolean fresh(Event e) {
        return !this.before.contains(key(e)) && System.currentTimeMillis() - this.bornAt(e) < 180000L;
    }

    private static String ownRarity(String type) {
        String text2;
        switch (type == null ? "" : type) {
            case "GOLDEN_FORTRESS":
                text2 = "Легендарный";
                break;
            case "MINE":
                text2 = "Эпический";
                break;
            default:
                String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
                text2 = !t.equals("end") && !t.contains("dragon") ? "Обычный" : "Легендарный";
        }

        return text2;
    }

    static int rank(String rarity) {
        byte b2;
        switch (rarity) {
            case "Босс":
                b2 = 5;
                break;
            case "Мифический":
                b2 = 4;
                break;
            case "Легендарный":
            case "Роскошный":
            case "Смертельный":
                b2 = 3;
                break;
            case "Эпический":
            case "Зажиточный":
            case "Взрывной":
                b2 = 2;
                break;
            case "Редкий":
                b2 = 1;
                break;
            default:
                b2 = 0;
        }

        return b2;
    }

    public boolean wanted(Event e) {
        return switch (rank(e.rarity())) {
            case 1 -> this.showRare.on();
            case 2 -> this.showEpic.on();
            case 3 -> this.showLegendary.on();
            case 4 -> this.showMythic.on();
            case 5 -> this.showBoss.on();
            default -> this.showCommon.on();
        };
    }

    static int number(String server) {
        String n = server.replaceAll("\\D+", "");
        return !n.isEmpty() && n.length() <= 6 ? Integer.parseInt(n) : 9999;
    }

    public List<Event> sorted() {
        List<Event> out = new ArrayList(this.current);
        out.sort((a, b) -> {
            boolean fa = this.fresh(a);
            boolean fb = this.fresh(b);
            if (fa != fb) {
                return fa ? -1 : 1;
            } else if (fa) {
                return Long.compare(this.bornAt(b), this.bornAt(a));
            } else {
                int r = Integer.compare(rank(b.rarity()), rank(a.rarity()));
                return r != 0 ? r : Integer.compare(number(a.server()), number(b.server()));
            }
        });
        return out;
    }

    public static String clock(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return s >= 3600L
                ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600L, s / 60L % 60L, s % 60L)
                : String.format(Locale.ROOT, "%02d:%02d", s / 60L, s % 60L);
    }

    public long runFor(String type) {
        this.readLearned();
        long[] l = (long[]) this.learned.get(type);
        return l != null && l[1] >= 2L ? l[0] : (Long) RUN.getOrDefault(type, 900000L);
    }

    private void readLearned() {
        if (!this.learnedRead) {
            this.learnedRead = true;

            try {
                JsonObject o = JsonParser.parseString(Files.readString(LEARNED)).getAsJsonObject();

                for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                    JsonArray a = ((JsonElement) en.getValue()).getAsJsonArray();
                    this.learned.put(
                            en.getKey(),
                            new long[] {a.get(0).getAsLong(), a.get(1).getAsLong()});
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void learn(Event e, long seenFor) {
        this.readLearned();
        long run = seenFor - (Long) WAIT.getOrDefault(e.type(), 0L);
        if (!e.type().isEmpty() && run >= 60000L && run <= 10800000L) {
            long[] l = (long[]) this.learned.computeIfAbsent(e.type(), (k) -> new long[] {run, 0L});
            long n = Math.min(l[1], 19L);
            l[0] = (l[0] * n + run) / (n + 1L);
            l[1]++;

            try {
                JsonObject o = new JsonObject();

                for (Map.Entry<String, long[]> en : this.learned.entrySet()) {
                    JsonArray a = new JsonArray();
                    a.add(((long[]) en.getValue())[0]);
                    a.add(((long[]) en.getValue())[1]);
                    o.add(en.getKey(), a);
                }

                Files.createDirectories(LEARNED.getParent());
                Files.writeString(LEARNED, o.toString());
            } catch (Exception ignored) {
            }
        }
    }

    public Status status(Event e) {
        long on = System.currentTimeMillis() - this.bornAt(e);
        boolean early = this.before.contains(key(e));
        String tilde = early ? "~" : "";
        if (e.server().equals(this.here) && this.livePhase != null && this.liveTime != null) {
            long left = millis(this.liveTime);
            if (left > this.liveMax) {
                this.liveMax = left;
            }

            String low = this.livePhase.toLowerCase(Locale.ROOT);
            boolean toStart =
                    low.contains("начн") || low.contains("откро") || low.contains("старт") || low.contains("до начала");
            return new Status(
                    toStart ? "старт" : "конец",
                    clock(Math.max(0L, left)),
                    left > 0L && this.liveMax > 0L ? 1.0F - (float) left / (float) this.liveMax : -1.0F,
                    true,
                    left,
                    this.liveMax,
                    toStart);
        } else {
            long wait = (Long) WAIT.getOrDefault(e.type(), 0L);
            long run = this.runFor(e.type());
            if (on < wait) {
                return new Status(
                        "старт", tilde + clock(wait - on), (float) on / (float) wait, false, wait - on, wait, true);
            } else {
                long left = Math.max(0L, wait + run - on);
                return new Status(
                        "конец", tilde + clock(left), (float) (on - wait) / (float) run, false, left, run, false);
            }
        }
    }

    private void readScreen() {
        Minecraft mc = mc();
        String was = this.livePhase;
        this.livePhase = null;
        this.liveTime = null;
        List<String> lines = new ArrayList<>();

        try {
            for (LerpingBossEvent bar : ((BossHealthOverlayAccess) mc.gui.getBossOverlay())
                    .arcanicvisuals$events()
                    .values()) {
                lines.add(plain(bar.getName().getString()));
            }
        } catch (RuntimeException ignored) {
        }

        Scoreboard sb = mc.level == null ? null : mc.level.getScoreboard();
        if (sb != null) {
            Objective side = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (side != null) {
                lines.add(plain(side.getDisplayName().getString()));

                for (PlayerScoreEntry entry : sb.listPlayerScores(side)) {
                    PlayerTeam team = sb.getPlayersTeam(entry.owner());
                    String text = entry.owner();
                    if (team != null) {
                        text = team.getPlayerPrefix().getString()
                                + text
                                + team.getPlayerSuffix().getString();
                    }

                    if (entry.display() != null) {
                        text = entry.display().getString();
                    }

                    lines.add(plain(text));
                }
            }
        }

        if (mc.getConnection() != null && mc.gui.getTabList() != null) {
            try {
                PlayerTabOverlay tab = mc.gui.getTabList();

                for (Field f : tab.getClass().getDeclaredFields()) {
                    if (f.getType() == Component.class) {
                        f.setAccessible(true);
                        Object c = f.get(tab);
                        if (c instanceof Component) {
                            Component comp = (Component) c;
                            lines.add(plain(comp.getString()));
                        }
                    }
                }
            } catch (RuntimeException | ReflectiveOperationException ignored) {
            }
        }

        for (String l : lines) {
            String low = l.toLowerCase(Locale.ROOT);
            Matcher s = SERVER.matcher(low);
            if (s.find()) {
                this.here = this.serverFor(s.group(1), s.group(2), low.contains("1.20"));
            }

            Matcher t = TIMER.matcher(l);
            boolean about = low.contains("ивент")
                    || low.contains("откро")
                    || low.contains("взрыв")
                    || low.contains("закон")
                    || low.contains("начн")
                    || low.contains("фаза")
                    || low.contains("груз")
                    || low.contains("куб");
            if (about && t.find() && this.livePhase == null) {
                this.livePhase =
                        l.replace(t.group(), "").replaceAll("[:\\s·|-]+$", "").trim();
                this.liveTime = t.group();
                if (this.livePhase.isEmpty()) {
                    this.livePhase = "Идёт";
                }

                if (!this.livePhase.equals(was)) {
                    this.liveMax = 0L;
                }
            }
        }
    }

    private static long millis(String s) {
        try {
            if (s.contains(":")) {
                long v = 0L;

                for (String part : s.split(":")) {
                    v = v * 60L + Long.parseLong(part.trim());
                }

                return v * 1000L;
            } else {
                long n = Long.parseLong(s.replaceAll("\\D+", ""));
                return !s.contains("мин") && !s.trim().endsWith("м") ? n * 1000L : n * 60000L;
            }
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    private String serverFor(String type, String n, boolean newer) {
        for (String id : this.names.keySet()) {
            if (number(id) == Integer.parseInt(n) && id.contains("NEW") == newer) {
                return id;
            }
        }

        return (newer ? "LITE_NEW_ANARCHY_" : "LITE_ANARCHY_") + n;
    }

    public static int headColour(String type) {
        Identifier head = head(type);
        if (head != null) {
            String p = head.getPath();
            Integer c = HEAD_COLOUR.get(p.substring(p.lastIndexOf(47) + 1, p.length() - 4));
            if (c != null) {
                return c;
            }
        }

        return -20163;
    }

    public static int headColour(Event e) {
        Identifier head = head(e.type());
        if (head != null) {
            String p = head.getPath();
            Integer c = HEAD_COLOUR.get(p.substring(p.lastIndexOf(47) + 1, p.length() - 4));
            if (c != null) {
                return c;
            }
        }

        return HolyEventsHud.colour(e.rarity());
    }

    private void announce(Event ev, boolean ongoing) {
        if (this.wanted(ev)) {
            if (!ongoing && this.sound.on()) {
                UiSound.play("charge", 1.1F, 0.9F);
            }
        }
    }

    private void ongoing(List<Event> list) {}

    public void join(Event e) {
        Modules.MARKS.goingTo(e);
        AnarchyJump.start(e.server(), this.serverName(e.server()));
    }

    public void join(String server) {
        for (Event e : this.sorted()) {
            if (e.server().equals(server)) {
                this.join(e);
                return;
            }
        }

        AnarchyJump.start(server, this.serverName(server));
    }

    public void hud(GuiGraphics g) {
        AnarchyJump.hud(g);
    }

    public static record Event(String server, String type, String name, String rarity, String id) {}

    public static record Status(
            String phase, String time, float progress, boolean live, long left, long total, boolean start) {
        Status(String phase, String time, float progress, boolean live) {
            this(phase, time, progress, live, -1L, -1L, false);
        }

        public int stage() {
            if (this.left < 0L) {
                return -1;
            } else {
                return this.left > 300000L ? 2 : (this.left > 120000L ? 1 : 0);
            }
        }
    }

    private static record Flag(Event event, long born, boolean ongoing, String top) {
        Flag(Event event, long born, boolean ongoing) {
            this(event, born, ongoing, null);
        }
    }
}
