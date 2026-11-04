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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.arcanic.visuals.core.Trust;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/** User scripts from config/arcanicvisuals/scripts. Each runs in its own sandbox, see {@link Script}. Paused in trust mode. */
public final class Scripts {
    private Scripts() {}

    public static final Path DIR =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals").resolve("scripts");
    static final Path DATA = DIR.resolve("data");
    private static final Path STATE = DIR.resolve("enabled.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int LOG_LINES = 300;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private static final List<Script> SCRIPTS = new ArrayList<>();
    private static final Set<String> ENABLED = new LinkedHashSet<>();
    private static final Deque<String> LOG = new ArrayDeque<>();
    private static boolean started;
    private static long changedAt;
    /** Set only while render handlers run. */
    static GuiGraphics graphics;

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) chat(message);
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> chat(message));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> {
            ServerData d = mc.getCurrentServer();
            emit("join", d == null ? "" : d.ip);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> emit("leave")));
    }

    public static void start() {
        if (started) return;
        started = true;
        try {
            Files.createDirectories(DIR);
            if (!hasScripts()) example("hello.lua");
            if (Files.isRegularFile(STATE)) {
                JsonElement e = JsonParser.parseString(Files.readString(STATE, StandardCharsets.UTF_8));
                if (e.isJsonArray()) for (JsonElement n : e.getAsJsonArray()) ENABLED.add(n.getAsString());
            }
        } catch (IOException | RuntimeException e) {
            log("scripts", "Не удалось открыть папку скриптов: " + e.getMessage());
        }
        reload();
    }

    private static boolean hasScripts() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.anyMatch(p -> p.getFileName().toString().endsWith(".lua"));
        }
    }

    public static Path example(String name) throws IOException {
        Files.createDirectories(DIR);
        Path out = DIR.resolve(name);
        if (Files.exists(out)) return out;
        try (InputStream in = Scripts.class.getResourceAsStream("/assets/arcanicvisuals/lua/hello.lua")) {
            if (in == null) throw new IOException("hello.lua is missing from the jar");
            Files.write(out, in.readAllBytes());
        }
        return out;
    }

    public static String create() {
        try {
            for (int i = 1; i < 1000; i++) {
                String name = "script" + i + ".lua";
                if (!Files.exists(DIR.resolve(name))) {
                    example(name);
                    reload();
                    return name;
                }
            }
        } catch (IOException e) {
            log("scripts", "Не удалось создать скрипт: " + e.getMessage());
        }
        return null;
    }

    public static void reload() {
        for (Script s : SCRIPTS) s.stop();
        SCRIPTS.clear();
        try (Stream<Path> files = Files.list(DIR)) {
            files.filter(p -> p.getFileName().toString().endsWith(".lua") && Files.isRegularFile(p))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .limit(64)
                    .forEach(p -> SCRIPTS.add(new Script(p)));
        } catch (IOException e) {
            log("scripts", "Не удалось прочитать папку скриптов: " + e.getMessage());
        }
        for (Script s : SCRIPTS) if (ENABLED.contains(s.name())) s.start();
        changedAt = System.currentTimeMillis();
    }

    public static List<Script> list() {
        start();
        return SCRIPTS;
    }

    public static boolean enabled(Script s) {
        return ENABLED.contains(s.name());
    }

    public static void toggle(Script s) {
        if (ENABLED.remove(s.name())) {
            s.stop();
            log(s.name(), "выключен");
        } else {
            ENABLED.add(s.name());
            s.start();
        }
        save();
        changedAt = System.currentTimeMillis();
    }

    private static void save() {
        try {
            JsonArray a = new JsonArray();
            for (String n : ENABLED) a.add(n);
            Files.createDirectories(DIR);
            Files.writeString(STATE, GSON.toJson(a), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log("scripts", "Не удалось сохранить список скриптов: " + e.getMessage());
        }
    }

    public static long changedAt() {
        return changedAt;
    }

    public static boolean live() {
        return !Trust.on();
    }

    // ---- events

    public static void tick() {
        if (!started) start();
        if (!live()) return;
        for (Script s : SCRIPTS) {
            if (s.running()) {
                s.emit("tick");
                s.flush();
            }
        }
    }

    public static void render(GuiGraphics g) {
        if (!started || !live() || SCRIPTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        boolean any = false;
        for (Script s : SCRIPTS) any |= s.running() && s.listens("render");
        if (!any) return;
        Px.begin(g);
        graphics = g;
        try {
            for (Script s : SCRIPTS) if (s.running()) s.emit("render");
        } finally {
            graphics = null;
            Px.end(g);
        }
    }

    private static void chat(Component message) {
        if (!started || !live()) return;
        String text = message.getString();
        Minecraft.getInstance().execute(() -> emit("chat", text));
    }

    private static void emit(String event, Object... args) {
        if (!started || !live()) return;
        for (Script s : SCRIPTS) if (s.running()) s.emit(event, args);
    }

    // ---- console

    static void log(String who, String text) {
        String line = LocalTime.now().format(STAMP) + "  " + who + ": " + text;
        synchronized (LOG) {
            for (String part : line.split("\\R")) {
                LOG.addLast(part.length() > 300 ? part.substring(0, 300) : part);
                while (LOG.size() > LOG_LINES) LOG.removeFirst();
            }
        }
    }

    public static List<String> console() {
        synchronized (LOG) {
            return new ArrayList<>(LOG);
        }
    }

    public static void clearConsole() {
        synchronized (LOG) {
            LOG.clear();
        }
    }
}
