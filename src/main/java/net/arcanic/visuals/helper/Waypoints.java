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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.fx.Friends;
import net.arcanic.visuals.fx.Fx;
import net.arcanic.visuals.fx.Projection;
import net.arcanic.visuals.gui.PromptScreen;
import net.arcanic.visuals.hud.DynamicIsland;
import net.arcanic.visuals.hud.Overlays;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class Waypoints extends Module {
    private final Setting.Bool events = this.add(new Setting.Bool("events", "Метки ивентов", true));
    private final Setting.Bool arrows = this.add(new Setting.Bool("arrows", "Стрелки за краем экрана", true));
    private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет своих меток", false, -9459457));
    private final List<Mark> marks = new ArrayList<>();
    private boolean loaded;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/waypoints.json");
    private static final String MAIN = "main";
    private final Map<String, String> spawnDims = new HashMap<>();
    private String arrivalDim;
    private String arrivalServer;
    private static final Path WORLDS =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/waypoints-worlds.json");
    private final List<Mark> plateMarks = new ArrayList<>();
    private final List<int[]> plateRects = new ArrayList<>();
    private static final Pattern XYZ = Pattern.compile("(-?\\d{1,7})\\s+(-?\\d{1,4})\\s+(-?\\d{1,7})");
    private static final Pattern STARTS = Pattern.compile("^(.+?)\\s+начн[её]тся на координатах");
    private static final Pattern APPEARS = Pattern.compile("появится\\s+(.+?)(?:\\s+с\\s+.*)?[!.]?$");
    private static final Pattern IN =
            Pattern.compile("(?:через|Осталось:|До открытия:)\\s*(\\d+)?\\s*(секунд\\w*|сек|минут\\w*|мин)", 2);
    static final String CHAT = "CHAT";
    static final String FRIEND = "FRIEND";
    private static final Pattern POS = Pattern.compile("(-?\\d+)\\s+(~|-?\\d+)\\s+(-?\\d+)");
    private HolyEvents.Event going;
    private boolean asking;
    private long askAt;
    private long listenUntil;
    private long lastAsk;
    private Object lastLevel;

    public Waypoints() {
        super(
                "waypoints",
                "Метки",
                "Свои метки и метки ивентов: видно через всю карту, сколько до них и что это",
                Category.HOLYWORLD,
                true);
    }

    private static int block(double v) {
        return (int) Math.floor(v);
    }

    private static String where(Mark m) {
        return Overlays.Streamer.hidesCoords()
                ? "••• ••• •••"
                : String.format(Locale.ROOT, "%d %d %d", block(m.x), block(m.y), block(m.z));
    }

    private static String server() {
        Minecraft mc = Minecraft.getInstance();
        ServerData d = mc.getCurrentServer();
        String s = d != null && d.ip != null ? d.ip.toLowerCase(Locale.ROOT) : "local";
        String here = HolyEvents.onHolyWorld() ? Modules.HOLY_EVENTS.here() : null;
        return here != null ? s + "/" + here : s;
    }

    private static String dim() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    private static boolean netherOrEnd(String dim) {
        return dim.contains("nether") || dim.contains("end");
    }

    private boolean eventWorld() {
        String d = dim();
        return !netherOrEnd(d) && !d.contains("lobby") && !d.contains("hub") && !AnarchyJump.inHub();
    }

    private boolean inMainWorld() {
        String d = dim();
        if (!netherOrEnd(d) && !d.contains("spawn") && !d.contains("lobby") && !d.contains("hub")) {
            String spawn = (String) this.spawnDims.get(server());
            return spawn == null || !spawn.equals(d);
        } else {
            return false;
        }
    }

    private void newWorld() {
        String srv = server();
        String d = dim();
        if (!d.isEmpty()) {
            if (!srv.equals(this.arrivalServer)) {
                this.arrivalServer = srv;
                this.arrivalDim = d;
            } else {
                if (this.arrivalDim != null
                        && !this.arrivalDim.equals(d)
                        && !netherOrEnd(d)
                        && !netherOrEnd(this.arrivalDim)) {
                    if (!this.arrivalDim.equals(this.spawnDims.get(srv))) {
                        this.spawnDims.put(srv, this.arrivalDim);
                        this.saveWorlds();
                    }

                    this.arrivalDim = null;
                }
            }
        }
    }

    private void loadWorlds() {
        try {
            if (!Files.exists(WORLDS, new LinkOption[0])) {
                return;
            }

            JsonObject o = JsonParser.parseString(Files.readString(WORLDS, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                this.spawnDims.put((String) e.getKey(), ((JsonElement) e.getValue()).getAsString());
            }
        } catch (Exception ignored) {
        }
    }

    private void saveWorlds() {
        JsonObject o = new JsonObject();
        this.spawnDims.forEach(o::addProperty);

        try {
            Files.createDirectories(WORLDS.getParent());
            Files.writeString(
                    WORLDS, (new GsonBuilder()).setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private boolean here(Mark m) {
        if ("main".equals(m.dim)) {
            if (!this.eventWorld()) {
                return false;
            }
        } else if (!m.dim.equals(dim())) {
            return false;
        }

        String s = server();
        return m.server.equals(s) || !s.contains("/") && m.server.startsWith(s + "/");
    }

    private void load() {
        if (!this.loaded) {
            this.loaded = true;
            this.loadWorlds();

            try {
                if (!Files.exists(FILE, new LinkOption[0])) {
                    return;
                }

                for (JsonElement e : JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                        .getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    Mark m = new Mark();
                    m.name = o.get("name").getAsString();
                    m.server = o.get("server").getAsString();
                    m.dim = o.get("dim").getAsString();
                    m.x = o.get("x").getAsDouble();
                    m.y = o.get("y").getAsDouble();
                    m.z = o.get("z").getAsDouble();
                    m.flat = o.has("flat") && o.get("flat").getAsBoolean();
                    this.marks.add(m);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void save() {
        JsonArray a = new JsonArray();

        for (Mark m : this.marks) {
            if (!m.event) {
                JsonObject o = new JsonObject();
                o.addProperty("name", m.name);
                o.addProperty("server", m.server);
                o.addProperty("dim", m.dim);
                o.addProperty("x", m.x);
                o.addProperty("y", m.y);
                o.addProperty("z", m.z);
                if (m.flat) {
                    o.addProperty("flat", true);
                }

                a.add(o);
            }
        }

        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(
                    FILE, (new GsonBuilder()).setPrettyPrinting().create().toJson(a), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((ClientReceiveMessageEvents.Game) (msg, overlay) -> {
            if (!overlay) {
                Modules.MARKS.heard(msg.getString());
            }
        });
    }

    private static void say(String text) {
        DynamicIsland.notice("Метка: " + text, true);
    }

    private String nextName() {
        int n = 1;

        for (Mark m : this.marks) {
            if (!m.event && m.name.startsWith("Метка ")) {
                ++n;
            }
        }

        return "Метка " + n;
    }

    public void key() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            this.load();
            Mark aimed = this.aimed();
            if (aimed != null) {
                this.edit(aimed);
            } else {
                Vec3 var10000;
                label18:
                {
                    HitResult hit = mc.player.pick(256.0, 1.0F, false);
                    if (hit instanceof BlockHitResult) {
                        BlockHitResult bh = (BlockHitResult) hit;
                        if (hit.getType() == Type.BLOCK) {
                            var10000 = Vec3.atBottomCenterOf(bh.getBlockPos().above());
                            break label18;
                        }
                    }

                    var10000 = mc.player.position();
                }

                Vec3 at = var10000;
                this.add(this.nextName(), at);
            }
        }
    }

    private void addHere(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.load();
            this.add(name != null && !name.isBlank() ? name.trim() : this.nextName(), mc.player.position());
        }
    }

    private void add(String name, Vec3 at) {
        Mark m = new Mark();
        m.name = name;
        m.server = server();
        m.dim = dim();
        m.x = Math.floor(at.x) + 0.5;
        m.y = Math.floor(at.y);
        m.z = Math.floor(at.z) + 0.5;
        this.marks.add(m);
        this.save();
        say("метка «" + name + "» поставлена: " + where(m));
    }

    /** A mark to send to a friend: its name and where it is. */
    public record Shared(String name, Vec3 at) {}

    /** The mark under the crosshair, else the newest own mark in this world; null when there is none. */
    public Shared shareable() {
        this.load();
        Mark m = this.aimed();
        if (m == null) {
            for (Mark k : this.marks) {
                if (!k.event && this.here(k)) {
                    m = k;
                }
            }
        }

        return m == null ? null : new Shared(m.name, new Vec3(m.x, m.y, m.z));
    }

    /** The block the player looks at (up to 256 blocks), else where the player stands. */
    public static Vec3 looking() {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.player.pick(256.0, 1.0F, false);
        return hit instanceof BlockHitResult bh && hit.getType() == Type.BLOCK
                ? Vec3.atBottomCenterOf(bh.getBlockPos().above())
                : mc.player.position();
    }

    /** A friend's mark: one per key, moved when it comes again. Never saved; a shared one fades in half an hour. */
    public void friendMark(String key, String name, Vec3 at, String dim) {
        this.load();
        Mark mark = null;

        for (Mark k : this.marks) {
            if (key.equals(k.key)) {
                mark = k;
            }
        }

        if (mark == null) {
            mark = new Mark();
            mark.event = true;
            mark.type = FRIEND;
            mark.key = key;
            this.marks.add(mark);
        }

        mark.name = name;
        mark.server = server();
        mark.dim = dim == null ? dim() : dim;
        mark.x = Math.floor(at.x) + 0.5;
        mark.y = Math.floor(at.y);
        mark.z = Math.floor(at.z) + 0.5;
        mark.until = key.startsWith("track:") ? 0L : System.currentTimeMillis() + 1800000L;
    }

    public void dropFriendMark(String key) {
        this.marks.removeIf((m) -> key.equals(m.key));
    }

    private void edit(Mark m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Vec3 var10000 = mc.player.position();
            Vec3 var10001 = new Vec3(m.x, m.y, m.z);
            String far = (int) var10000.distanceTo(var10001) + " м";
            if (m.event && !"CHAT".equals(m.type)) {
                mc.setScreen(PromptScreen.ask(
                        (Screen) null,
                        m.name,
                        where(m) + "  ·  " + far
                                + (FRIEND.equals(m.type) ? ". Метка друга: убрать её?" : ". Метка ивента: убрать её?"),
                        "Убрать",
                        () -> this.drop(m)));
            } else {
                PromptScreen var4 = PromptScreen.field(
                                (Screen) null, "Метка", "Название метки", "Сохранить", (name) -> this.rename(m, name))
                        .value("CHAT".equals(m.type) ? "" : m.name);
                String var10002 = where(m);
                mc.setScreen(var4.note(var10002 + "  ·  " + far).danger("Удалить", () -> this.drop(m)));
            }
        }
    }

    private void rename(Mark m, String name) {
        if (!name.isBlank()) {
            m.name = name.trim();
            if ("CHAT".equals(m.type)) {
                m.event = false;
                m.type = null;
                m.until = 0L;
            }

            this.save();
            String var10000 = m.name;
            say("метка «" + var10000 + "» · " + where(m));
        }
    }

    private void drop(Mark m) {
        if (this.marks.remove(m)) {
            this.save();
            say("метка «" + m.name + "» убрана");
        }
    }

    private Mark under(double x, double y) {
        for (int i = this.plateRects.size() - 1; i >= 0; --i) {
            int[] r = (int[]) this.plateRects.get(i);
            if (x >= (double) r[0] && x < (double) (r[0] + r[2]) && y >= (double) r[1] && y < (double) (r[1] + r[3])) {
                return (Mark) this.plateMarks.get(i);
            }
        }

        return null;
    }

    public boolean click(double x, double y, int button) {
        if (this.enabled() && button == 0) {
            Mark m = this.under(x, y);
            if (m != null && this.marks.contains(m)) {
                UiSound.play("click");
                this.edit(m);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void remove(String name) {
        this.load();
        boolean all = name.equalsIgnoreCase("all") || name.equalsIgnoreCase("все");
        int before = this.marks.size();
        this.marks.removeIf((m) -> this.here(m) && (all || m.name.equalsIgnoreCase(name.trim())));
        this.save();
        say(before == this.marks.size() ? "нет такой метки" : "убрано меток: " + (before - this.marks.size()));
    }

    private Mark aimed() {
        Minecraft mc = Minecraft.getInstance();
        Mark best = null;
        double bestD = 18.0 * Math.max(1.0, (double) Px.height() / 1080.0);

        for (Mark m : this.marks) {
            if (this.here(m)) {
                double[] s = Projection.toScreen(new Vec3(m.x, m.y + 1.0, m.z), Px.width(), Px.height());
                if (s != null) {
                    double d = Math.hypot(s[0] - (double) Px.width() / 2.0, s[1] - (double) Px.height() / 2.0);
                    if (d < bestD) {
                        bestD = d;
                        best = m;
                    }
                }
            }
        }

        return best;
    }

    private void heard(String raw) {
        if (this.enabled() && this.events.on() && HolyEvents.onHolyWorld()) {
            String s = raw.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "").trim();
            if (!s.startsWith("Arcanic")) {
                if (!this.answer(s)) {
                    if (s.startsWith("▶") && s.toLowerCase(Locale.ROOT).contains("координат")) {
                        s = s.substring(1).trim();
                        Matcher c = XYZ.matcher(s);
                        if (c.find()) {
                            double x = (double) Integer.parseInt(c.group(1)) + 0.5;
                            double y = (double) Integer.parseInt(c.group(2));
                            double z = (double) Integer.parseInt(c.group(3)) + 0.5;
                            String name = null;
                            Matcher m = STARTS.matcher(s);
                            if (m.find()) {
                                name = m.group(1);
                            } else if (s.contains("Королевск")) {
                                name = "Королевская награда";
                            } else if ((m = APPEARS.matcher(s)).find()) {
                                name = m.group(1);
                            }

                            if (name == null) {
                                name = "Ивент";
                            }

                            String var10000 = name.substring(0, 1).toUpperCase(Locale.ROOT);
                            name = var10000 + name.substring(1);
                            long now = System.currentTimeMillis();
                            long starts = 0L;
                            Matcher t = IN.matcher(s);
                            if (t.find()) {
                                int n = t.group(1) == null ? 1 : Integer.parseInt(t.group(1));
                                starts = now
                                        + (long) n
                                                * (t.group(2)
                                                                .toLowerCase(Locale.ROOT)
                                                                .startsWith("с")
                                                        ? 1000L
                                                        : 60000L);
                            }

                            String type = typeOf(name);
                            HolyEvents.Event ev = eventHere(type, name);
                            if (ev != null) {
                                name = ev.name();
                                type = ev.type();
                            }

                            this.put(name, type, x, y, z, starts);
                        }
                    }
                }
            }
        }
    }

    public void chatMark(String pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Matcher p = POS.matcher(pos.trim());
            if (p.matches()) {
                this.load();
                double x = (double) Integer.parseInt(p.group(1)) + 0.5;
                double z = (double) Integer.parseInt(p.group(3)) + 0.5;
                boolean flat = p.group(2).equals("~");
                double y = flat ? Math.floor(mc.player.getY()) : (double) Integer.parseInt(p.group(2));
                Iterator<Mark> it = this.marks.iterator();

                while (it.hasNext()) {
                    Mark k = (Mark) it.next();
                    if ("CHAT".equals(k.type) && Math.abs(k.x - x) < 2.0 && Math.abs(k.z - z) < 2.0 && this.here(k)) {
                        it.remove();
                        say("метка " + where(k) + " убрана");
                        return;
                    }
                }

                Mark m = new Mark();
                m.event = true;
                m.type = "CHAT";
                m.flat = flat;
                m.name = flat
                        ? String.format(Locale.ROOT, "%d %d", block(x), block(z))
                        : String.format(Locale.ROOT, "%d %d %d", block(x), (int) y, block(z));
                m.server = server();
                m.dim = "main";
                m.x = x;
                m.y = y;
                m.z = z;
                m.until = System.currentTimeMillis() + 3600000L;
                this.marks.add(m);
                say("метка поставлена: " + m.name + " (клик по ней в чате — назвать или убрать)");
            }
        }
    }

    private void put(String name, String type, double x, double y, double z, long starts) {
        long now = System.currentTimeMillis();
        this.load();
        Mark mark = null;

        for (Mark k : this.marks) {
            if (k.event && k.server.equals(server()) && Math.abs(k.x - x) < 3.0 && Math.abs(k.z - z) < 3.0) {
                mark = k;
            }
        }

        if (mark == null) {
            mark = new Mark();
            mark.event = true;
            mark.server = server();
            mark.dim = "main";
            this.marks.add(mark);
        }

        mark.name = name;
        mark.type = type;
        mark.x = x;
        mark.y = y;
        mark.z = z;
        mark.startsAt = starts;
        long run = type != null ? Modules.HOLY_EVENTS.runFor(type) : 900000L;
        mark.until = Math.max(starts, now) + run;
    }

    /** A FunTime event's place, read from the server's answer: shown until it should be over. */
    public void funTimeEvent(String name, double x, double y, double z, long startsAt, long until) {
        if (!this.enabled() || !this.events.on()) return;
        this.load();
        Mark mark = null;
        for (Mark k : this.marks) {
            if (k.event && k.server.equals(server()) && Math.abs(k.x - x) < 3.0 && Math.abs(k.z - z) < 3.0) {
                mark = k;
            }
        }
        if (mark == null) {
            mark = new Mark();
            mark.event = true;
            mark.server = server();
            mark.dim = "main";
            this.marks.add(mark);
        }
        mark.name = name;
        mark.type = "FUNTIME";
        mark.x = x;
        mark.y = y;
        mark.z = z;
        mark.startsAt = startsAt;
        mark.until = until;
    }

    /** Drops the FunTime event marks of this server before a fresh answer lays them out again. */
    public void clearFunTimeEvents() {
        this.load();
        String here = server();
        this.marks.removeIf(k -> k.event && "FUNTIME".equals(k.type) && here.equals(k.server));
    }

    /** Moves the start of a FunTime event mark named {@code name}. */
    public void funTimeStarts(String name, long startsAt) {
        this.load();
        for (Mark k : this.marks) {
            if (k.event && "FUNTIME".equals(k.type) && name.equalsIgnoreCase(k.name)) {
                k.startsAt = startsAt;
                k.until = Math.max(k.until, startsAt + 600000L);
                return;
            }
        }
    }

    private static HolyEvents.Event eventHere(String type, String name) {
        String here = Modules.HOLY_EVENTS.here();
        HolyEvents.Event only = null;
        int count = 0;

        for (HolyEvents.Event e : Modules.HOLY_EVENTS.current()) {
            if (here == null || e.server().equals(here)) {
                if (type != null && type.equalsIgnoreCase(e.type())) {
                    return e;
                }

                if (name != null && e.name().equalsIgnoreCase(name)) {
                    return e;
                }

                only = e;
                ++count;
            }
        }

        return count == 1 && here != null ? only : null;
    }

    private void ask(HolyEvents.Event e, long ms) {
        if (this.enabled() && this.events.on()) {
            if (e != null) {
                this.going = e;
            }

            this.asking = true;
            this.askAt = System.currentTimeMillis() + ms;
        }
    }

    public void startedHere(HolyEvents.Event e) {
        this.ask(e, 1200L);
    }

    public void goingTo(HolyEvents.Event e) {
        this.going = e;
        this.askAt = 0L;
        this.listenUntil = 0L;
    }

    public void arrived() {
        if (this.going != null) {
            this.ask(this.going, 1500L);
        }
    }

    private boolean answer(String s) {
        if (this.listenUntil != 0L && System.currentTimeMillis() <= this.listenUntil) {
            String low = s.toLowerCase(Locale.ROOT);
            HolyEvents.Event named = this.going != null ? this.going : named(low);
            if (!low.contains("координат")
                    && !low.contains("ивент")
                    && !s.contains("X:")
                    && !s.contains("[")
                    && named == null) {
                return false;
            } else {
                Matcher c = XYZ.matcher(s.replace("[", " ").replace("]", " ").replace(",", " "));
                Matcher xyz = Pattern.compile("X:\\s*(-?\\d+).*?Y:\\s*(-?\\d+).*?Z:\\s*(-?\\d+)")
                        .matcher(s);
                double x;
                double y;
                double z;
                if (xyz.find()) {
                    x = (double) Integer.parseInt(xyz.group(1)) + 0.5;
                    y = (double) Integer.parseInt(xyz.group(2));
                    z = (double) Integer.parseInt(xyz.group(3)) + 0.5;
                } else {
                    if (!c.find()) {
                        return false;
                    }

                    x = (double) Integer.parseInt(c.group(1)) + 0.5;
                    y = (double) Integer.parseInt(c.group(2));
                    z = (double) Integer.parseInt(c.group(3)) + 0.5;
                }

                long starts = 0L;
                Matcher t = IN.matcher(s);
                if (t.find()) {
                    int n = t.group(1) == null ? 1 : Integer.parseInt(t.group(1));
                    starts = System.currentTimeMillis()
                            + (long) n * (t.group(2).toLowerCase(Locale.ROOT).startsWith("с") ? 1000L : 60000L);
                }

                HolyEvents.Event e = named != null ? named : eventHere((String) null, (String) null);
                String name = e != null ? e.name() : "Ивент";
                String type = e != null ? e.type() : null;
                if (this.going != null) {
                    this.going = null;
                }

                this.put(name, type, x, y, z, starts);
                say(String.format(Locale.ROOT, "метка ивента «%s»: %d %d %d", name, block(x), (int) y, block(z)));
                return true;
            }
        } else {
            return false;
        }
    }

    private static HolyEvents.Event named(String low) {
        String here = Modules.HOLY_EVENTS.here();

        for (HolyEvents.Event e : Modules.HOLY_EVENTS.current()) {
            if ((here == null || e.server().equals(here))
                    && !e.name().isEmpty()
                    && low.contains(e.name().toLowerCase(Locale.ROOT))) {
                return e;
            }
        }

        return null;
    }

    private static String typeOf(String name) {
        String n = name.toLowerCase(Locale.ROOT);

        for (HolyEvents.Event e : Modules.HOLY_EVENTS.current()) {
            String en = e.name().toLowerCase(Locale.ROOT);
            if (!en.isEmpty() && (en.equals(n) || n.contains(en) || en.contains(n))) {
                return e.type();
            }
        }

        if (n.contains("груз")) {
            return "CARGO";
        } else if (n.contains("корабл")) {
            return "SHIP";
        } else if (n.contains("кубик")) {
            return "CUBE";
        } else if (n.contains("шахт")) {
            return "MINE";
        } else if (n.contains("посылк")) {
            return "PARCELS";
        } else if (n.contains("контейнер")) {
            return "CONTAINER";
        } else if (n.contains("джейк")) {
            return "JAYCOB";
        } else if (n.contains("крепост")) {
            return "GOLDEN_FORTRESS";
        } else if (n.contains("карьер")) {
            return "SNOWQUARRY";
        } else if (n.contains("босс")) {
            return "BOSS";
        } else {
            return null;
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != this.lastLevel) {
            this.lastLevel = mc.level;
            this.load();
            if (mc.level != null) {
                this.newWorld();
            }

            if (mc.level != null && HolyEvents.onHolyWorld()) {
                this.ask((HolyEvents.Event) null, 3000L);
            }
        }

        if (this.asking && this.askAt > 0L && now >= this.askAt && mc.player != null) {
            ClientPacketListener conn = mc.getConnection();
            if (conn != null && now - this.lastAsk > 8000L && !AnarchyJump.inHub()) {
                conn.sendCommand("event");
                this.lastAsk = now;
                this.listenUntil = now + 6000L;
            }

            this.asking = false;
            this.askAt = 0L;
        }

        if (this.listenUntil > 0L && now > this.listenUntil) {
            this.listenUntil = 0L;
            this.going = null;
        }

        if (this.marks.removeIf((m) -> m.event && (this.gone(m) || !m.confirmed && m.until > 0L && now > m.until))) {
            this.save();
        }
    }

    private boolean gone(Mark m) {
        int slash = m.server.lastIndexOf(47);
        if (slash >= 0 && Modules.HOLY_EVENTS.lastAnswer() != 0L) {
            String anarchy = m.server.substring(slash + 1);
            boolean on = false;

            for (HolyEvents.Event e : Modules.HOLY_EVENTS.current()) {
                if (e.server().equals(anarchy)) {
                    if (m.type != null) {
                        if (!m.type.equalsIgnoreCase(e.type())) {
                            continue;
                        }
                    } else if (!e.name().equalsIgnoreCase(m.name)) {
                        continue;
                    }

                    on = true;
                }
            }

            if (on) {
                m.confirmed = true;
            }

            return m.confirmed && !on;
        } else {
            return false;
        }
    }

    public void ended(HolyEvents.Event e) {
        boolean any = this.marks.removeIf((m) -> {
            boolean var10000;
            label27:
            {
                if (m.event && m.server.endsWith("/" + e.server())) {
                    if (m.type != null) {
                        if (m.type.equalsIgnoreCase(e.type())) {
                            break label27;
                        }
                    } else if (m.name.equalsIgnoreCase(e.name())) {
                        break label27;
                    }
                }

                var10000 = false;
                return var10000;
            }

            var10000 = true;
            return var10000;
        });
        if (any) {
            this.save();
        }
    }

    public void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && (mc.screen == null || mc.screen instanceof ChatScreen)) {
            this.load();
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 eye = mc.player.getEyePosition(pt);
            int w = Px.width();
            int h = Px.height();
            int fs = Math.max(10, Math.round((float) h / 1080.0F * 12.0F));
            long now = System.currentTimeMillis();
            Mark aimed = this.aimed();
            boolean chat = mc.screen instanceof ChatScreen;
            Mark hover = chat
                    ? this.under(
                            mc.mouseHandler.xpos()
                                    * (double) w
                                    / (double) Math.max(1, mc.getWindow().getScreenWidth()),
                            mc.mouseHandler.ypos()
                                    * (double) h
                                    / (double) Math.max(1, mc.getWindow().getScreenHeight()))
                    : null;
            this.plateMarks.clear();
            this.plateRects.clear();

            for (Mark m : this.marks) {
                if (this.here(m)) {
                    if (m.flat) {
                        ground(m);
                    }

                    Vec3 at = new Vec3(m.x, m.y + 0.2, m.z);
                    double dist = eye.distanceTo(at);
                    float near = (float) Math.min(1.0, Math.max(0.0, (dist - 2.0) / 4.0));
                    if (!(near < 0.02F)) {
                        int accent = "CHAT".equals(m.type)
                                ? Theme.accent() | 0xFF000000
                                : FRIEND.equals(m.type)
                                        ? Friends.glowColour()
                                        : (m.event ? HolyEvents.headColour(m.type) : this.colour.argb() | 0xFF000000);
                        double[] s = Projection.toScreen(at, w, h);
                        boolean on = s != null && s[0] > 0.0 && s[0] < (double) w && s[1] > 0.0 && s[1] < (double) h;
                        float k = Px.fade;
                        Px.fade = k * near;
                        if (on) {
                            this.plateMarks.add(m);
                            this.plateRects.add(this.plate(
                                    g, m, (int) s[0], (int) s[1], dist, now, accent, fs, m == aimed || m == hover));
                        } else if (this.arrows.on()) {
                            arrow(g, at, w, h, accent, fs);
                        }

                        Px.fade = k;
                    }
                }
            }
        }
    }

    private static void ground(Mark m) {
        ClientLevel level = Minecraft.getInstance().level;
        int bx = block(m.x);
        int bz = block(m.z);
        if (level != null && level.hasChunkAt(new BlockPos(bx, 0, bz))) {
            int top = level.getHeight(Types.MOTION_BLOCKING, bx, bz);
            if (top > level.getMinY()) {
                m.y = (double) top;
            }
        }
    }

    private int[] plate(
            GuiGraphics g, Mark m, int px, int py, double dist, long now, int accent, int fs0, boolean aimed) {
        float scale = (float) Math.max(0.8, 1.0 - Math.min(1.0, dist / 400.0) * 0.2);
        int fs = Math.max(9, Math.round((float) fs0 * scale));
        int fs2 = Math.max(8, Math.round((float) fs * 0.86F));
        String dst = dist >= 1000.0 ? String.format(Locale.ROOT, "%.1f км", dist / 1000.0) : (int) dist + " м";
        String when = null;
        int h = Math.round((float) fs * 2.2F);
        int pad = Math.round((float) fs * 0.5F);
        int icon = Math.round((float) fs * 1.55F);
        int gap = Math.round((float) fs * 0.5F);
        int r = h / 2;
        int nameW = Px.textWidth(m.name, Px.Weight.SEMIBOLD, fs);
        int dstW = Px.textWidth(dst, Px.Weight.MEDIUM, fs2);
        int chipW = when == null ? 0 : Px.textWidth(when, Px.Weight.SEMIBOLD, fs2) + Math.round((float) fs * 0.9F);
        int bw = pad
                + icon
                + gap
                + nameW
                + gap
                + dstW
                + (chipW > 0 ? gap + chipW : 0)
                + pad
                + Math.round((float) fs * 0.2F);
        int stem = Math.round((float) fs * 1.6F);
        int x = px - bw / 2;
        int y = py - stem - h;
        int gs = Math.round((float) fs * 2.2F);
        Px.glow(g, px - gs / 2, py - gs / 2, gs, gs, Px.a(accent & 0xFFFFFF, 120));
        int d = Math.max(4, Math.round((float) fs * 0.42F));
        Px.round(g, px - d / 2, py - d / 2, d, d, d / 2, Px.mix(accent, -1, 0.25F));
        int sw = Math.max(1, Math.round((float) fs / 9.0F));
        Px.vgrad(g, px - sw / 2, y + h, sw, stem - d, Px.a(accent & 0xFFFFFF, 170), Px.a(accent & 0xFFFFFF, 20));
        Px.shadow(g, x, y + Math.max(1, fs / 6), bw, h, Math.max(4, fs / 2), 1426063360);
        Px.round(g, x, y, bw, h, r, 0xEB0E0E11);
        Px.outline(g, x, y, bw, h, r, aimed ? Px.a(accent & 0xFFFFFF, 210) : 452984831);
        int ix = x + pad;
        int iy = y + (h - icon) / 2;
        Identifier head = m.event ? HolyEvents.head(m.type) : null;
        if (head != null) {
            HolyEvents.picture(g, head, 38, 38, ix, iy, icon, icon);
        } else if ("CHAT".equals(m.type)) {
            Px.icon(g, "w.flag", ix + icon / 6, iy + icon / 6, icon * 2 / 3, accent);
        } else if (FRIEND.equals(m.type)) {
            Px.icon(g, "w.users", ix + icon / 6, iy + icon / 6, icon * 2 / 3, accent);
        } else if (m.event) {
            Px.icon(g, "w.flame", ix + icon / 6, iy + icon / 6, icon * 2 / 3, accent);
        } else {
            int dd = Math.round((float) icon * 0.5F);
            Px.round(g, ix + (icon - dd) / 2, iy + (icon - dd) / 2, dd, dd, dd / 2, accent);
        }

        int tx = ix + icon + gap;
        Px.text(g, m.name, tx, y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, -723722);
        tx += nameW + gap;
        Px.text(g, dst, tx, y + (h - fs2) / 2 + Math.round((float) fs * 0.05F), Px.Weight.MEDIUM, fs2, -7434602);
        if (chipW > 0) {
            int cx = tx + dstW + gap;
            int ch = Math.round((float) fs * 1.35F);
            Px.round(g, cx, y + (h - ch) / 2, chipW, ch, ch / 2, Px.a(accent & 0xFFFFFF, 55));
            Px.textCenter(
                    g, when, cx + chipW / 2, y + (h - fs2) / 2, Px.Weight.SEMIBOLD, fs2, Fx.lerp(accent, -1, 0.5F));
        }

        if (aimed) {
            Px.textCenter(g, where(m), px, y + h + Math.round((float) fs * 0.3F), Px.Weight.MEDIUM, fs2, -4934468);
        }

        return new int[] {x, y, bw, h + stem};
    }

    private static void arrow(GuiGraphics g, Vec3 at, int w, int h, int accent, int fs) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 rel = at.subtract(cam.position());
        Vector3fc u = cam.upVector();
        Vector3fc l = cam.leftVector();
        double x = -(rel.x * (double) l.x() + rel.y * (double) l.y() + rel.z * (double) l.z());
        double y = rel.x * (double) u.x() + rel.y * (double) u.y() + rel.z * (double) u.z();
        double ang = Math.atan2(x, y);
        double r = (double) Math.min(w, h) * 0.12;
        float cx = (float) ((double) w / 2.0 + Math.sin(ang) * r);
        float cy = (float) ((double) h / 2.0 - Math.cos(ang) * r);
        int s = Math.round((float) fs * 1.3F);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate((float) ang);
        Px.icon(g, "w.chev-up", -s / 2, -s / 2, s, accent);
        g.pose().popMatrix();
    }

    private static final class Mark {
        String key;
        String name;
        String server;
        String dim;
        String type;
        double x;
        double y;
        double z;
        long startsAt;
        long until;
        boolean event;
        boolean confirmed;
        boolean flat;
    }
}
