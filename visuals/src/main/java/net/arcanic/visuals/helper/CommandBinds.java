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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.gui.CommandBindsScreen;
import net.arcanic.visuals.hud.Notify;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.lwjgl.glfw.GLFW;

public final class CommandBinds extends Module {
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/command-binds.json");
    public final List<Bind> binds = new ArrayList<>();
    private boolean loaded;
    private long lastSent;
    final Setting.Action editor =
            this.add(new Setting.Action("editor", "Список биндов", "Открыть", () -> Minecraft.getInstance()
                    .setScreen(new CommandBindsScreen(Minecraft.getInstance().screen))));

    public CommandBinds() {
        super(
                "command_binds",
                "Бинды команд",
                "Своя клавиша на любую команду или сообщение: /ah sell, /home, /kit",
                Category.UTILITY,
                false);
    }

    public List<Bind> list() {
        this.load();
        return this.binds;
    }

    private void load() {
        if (!this.loaded) {
            this.loaded = true;

            try {
                if (!Files.exists(FILE)) {
                    return;
                }

                for (JsonElement e : JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                        .getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    this.binds.add(
                            new Bind(o.get("key").getAsInt(), o.get("command").getAsString()));
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void save() {
        JsonArray a = new JsonArray();

        for (Bind b : this.binds) {
            JsonObject o = new JsonObject();
            o.addProperty("key", b.key);
            o.addProperty("command", b.command);
            a.add(o);
        }

        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(
                    FILE, (new GsonBuilder()).setPrettyPrinting().create().toJson(a), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        this.load();
        if (mc.player != null) {
            long win = mc.getWindow().handle();

            for (Bind b : this.binds) {
                boolean down = b.key > 0 && mc.screen == null && GLFW.glfwGetKey(win, b.key) == 1;
                if (down && !b.down) {
                    this.send(b.command);
                }

                b.down = down;
            }
        }
    }

    private void send(String command) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null && command != null && !command.isBlank()) {
            long now = System.currentTimeMillis();
            if (now - this.lastSent >= 250L) {
                this.lastSent = now;
                String c = command.trim();
                if (c.startsWith("/")) {
                    conn.sendCommand(c.substring(1));
                } else {
                    conn.sendChat(c);
                }

                Notify.text("Бинд: " + c, true);
            }
        }
    }

    public static final class Bind {
        public int key;
        public String command;
        boolean down;

        public Bind(int key, String command) {
            this.key = key;
            this.command = command;
        }
    }
}
