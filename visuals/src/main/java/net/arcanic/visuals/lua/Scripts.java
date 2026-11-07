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
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** User scripts from config/arcanicvisuals/scripts. Each runs in its own sandbox, see {@link Script}. */
public final class Scripts {
    private Scripts() {}

    public static final Path DIR =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals").resolve("scripts");
    static final Path DATA = DIR.resolve("data");
    public static final Path DOCS = DIR.resolve("API.md");
    private static final Path STATE = DIR.resolve("enabled.json");
    private static final Path LOG_FILE = DIR.resolve("log.txt");
    private static final Logger LOG = LoggerFactory.getLogger("ArcanicLua");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    static final Set<String> BUILT_IN = Set.of(
            "tick", "render", "chat", "send", "key", "join", "leave", "screen", "damage", "death", "attack", "unload");

    private static final List<Script> SCRIPTS = new ArrayList<>();
    private static final Set<String> ENABLED = new LinkedHashSet<>();
    private static boolean started;
    private static boolean logReset;
    /** Set only while something of a script draws. */
    static GuiGraphics graphics;

    private static String screen = "";
    private static float health = -1;

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || received(message));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> received(message));
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> LuaGame.sending || emit("send", message));
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> LuaGame.sending || emit("send", "/" + command));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> {
            ServerData d = mc.getCurrentServer();
            emit("join", d == null ? "" : d.ip);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> emit("leave")));
    }

    /** A message is hidden when a "chat" handler returns false. */
    private static boolean received(Component message) {
        return !started || emit("chat", message.getString());
    }

    public static void start() {
        if (started || net.arcanic.visuals.core.Trust.ON) return; // trust mode runs no scripts
        started = true;
        try {
            Files.createDirectories(DIR);
            Files.createDirectories(DIR.resolve("images"));
            if (!hasScripts()) example("hello.lua");
            writeDocs();
            if (Files.isRegularFile(STATE)) {
                JsonElement e = JsonParser.parseString(Files.readString(STATE, StandardCharsets.UTF_8));
                if (e.isJsonArray()) for (JsonElement n : e.getAsJsonArray()) ENABLED.add(n.getAsString());
            }
        } catch (IOException | RuntimeException e) {
            log("scripts", "не удалось открыть папку скриптов: " + e.getMessage());
        }
        reload();
    }

    private static boolean hasScripts() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.anyMatch(p -> p.getFileName().toString().endsWith(".lua"));
        }
    }

    /** The API reference next to the scripts, rewritten each start so it matches this version. */
    private static void writeDocs() throws IOException {
        try (InputStream in = Scripts.class.getResourceAsStream("/assets/arcanicvisuals/lua/API.md")) {
            if (in != null) Files.write(DOCS, in.readAllBytes());
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
            log("scripts", "не удалось создать скрипт: " + e.getMessage());
        }
        return null;
    }

    public static void reload() {
        for (Script s : SCRIPTS) s.stop();
        SCRIPTS.clear();
        LuaApi.forgetImages();
        try (Stream<Path> files = Files.list(DIR)) {
            files.filter(p -> p.getFileName().toString().endsWith(".lua") && Files.isRegularFile(p))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .limit(64)
                    .forEach(p -> SCRIPTS.add(new Script(p)));
        } catch (IOException e) {
            log("scripts", "не удалось прочитать папку скриптов: " + e.getMessage());
        }
        for (Script s : SCRIPTS) if (ENABLED.contains(s.name())) s.start();
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
    }

    private static void save() {
        try {
            JsonArray a = new JsonArray();
            for (String n : ENABLED) a.add(n);
            Files.createDirectories(DIR);
            Files.writeString(STATE, GSON.toJson(a), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log("scripts", "не удалось сохранить список скриптов: " + e.getMessage());
        }
    }

    // ---- events

    public static void tick() {
        if (net.arcanic.visuals.core.Trust.ON) return;
        if (!started) start();
        Minecraft mc = Minecraft.getInstance();
        String now = mc.screen == null ? "" : mc.screen.getClass().getSimpleName();
        if (!now.equals(screen)) {
            screen = now;
            emit("screen", now);
        }
        LocalPlayer me = mc.player;
        float hp = me == null ? -1 : me.getHealth();
        if (me != null && health > 0 && hp < health) {
            emit("damage", health - hp, hp);
            if (hp <= 0) emit("death");
        }
        health = hp;
        for (Script s : List.copyOf(SCRIPTS)) if (s.running()) s.tick();
        LuaGame.pump();
        ScriptModules.tick();
    }

    public static void render(GuiGraphics g) {
        if (!started || SCRIPTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        boolean any = false;
        for (Script s : SCRIPTS) any |= s.running() && s.listens("render");
        if (!any) return;
        Px.begin(g);
        graphics = g;
        float fade = Px.fade;
        try {
            for (Script s : SCRIPTS) {
                if (!s.running()) continue;
                g.pose().pushMatrix();
                try {
                    s.emit("render");
                } finally {
                    g.pose().popMatrix();
                    Px.fade = fade;
                }
            }
        } finally {
            Px.fade = fade;
            graphics = null;
            Px.end(g);
        }
    }

    public static void key(int code) {
        if (!started) return;
        emit("key", LuaKeys.name(code));
        for (Script s : List.copyOf(SCRIPTS)) if (s.running()) s.key(code);
    }

    public static void attack(Entity target) {
        if (started) emit("attack", LuaGame.entity(target));
    }

    /** False when a handler returned false (for "chat" and "send": the message is cancelled). */
    static boolean emit(String event, Object... args) {
        if (!started) return true;
        boolean allowed = true;
        for (Script s : List.copyOf(SCRIPTS)) if (s.running() && !s.emit(event, args)) allowed = false;
        return allowed;
    }

    // ---- log: scripts/log.txt and the game log

    static void log(String who, String text) {
        String line = LocalTime.now().format(STAMP) + "  " + who + ": " + text;
        LOG.info("{}: {}", who, text);
        try {
            Files.createDirectories(DIR);
            if (!logReset) {
                logReset = true;
                Files.writeString(LOG_FILE, "", StandardCharsets.UTF_8);
            }
            Files.writeString(
                    LOG_FILE,
                    (line.length() > 2000 ? line.substring(0, 2000) : line) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
