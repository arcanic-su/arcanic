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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.hud.HudModule;

/**
 * Modules and HUD elements made by scripts. Their switch, bind, position and settings are kept in
 * scripts/data/modules.json, so they come back as they were when the script starts again.
 */
final class ScriptModules {
    private ScriptModules() {}

    private static final Path FILE = Scripts.DATA.resolve("modules.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static JsonObject saved;
    private static String written = "";
    private static boolean dirty;
    private static int ticks;

    private static JsonObject saved() {
        if (saved == null) {
            saved = new JsonObject();
            try {
                if (Files.isRegularFile(FILE)) {
                    JsonElement e = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
                    if (e.isJsonObject()) saved = e.getAsJsonObject();
                }
            } catch (IOException | RuntimeException ignored) {
            }
            written = GSON.toJson(saved);
        }
        return saved;
    }

    static boolean taken(String id) {
        for (Module m : Modules.everything()) if (m.id.equals(id)) return true;
        return false;
    }

    static void register(Module m) {
        JsonObject o = saved().getAsJsonObject(m.id);
        if (o != null) {
            if (o.has("bind")) m.bind = o.get("bind").getAsInt();
            if (m instanceof HudModule h && o.has("x") && o.has("y")) {
                h.fx = o.get("x").getAsDouble();
                h.fy = o.get("y").getAsDouble();
            }
        }
        Modules.SCRIPTED.add(m);
        if (m instanceof HudModule h) Modules.HUD.add(h);
    }

    /** Switches the module on if it was on last time; called once the script has finished loading. */
    static void resume(Module m) {
        JsonObject o = saved().getAsJsonObject(m.id);
        if (o != null && o.has("enabled") && o.get("enabled").getAsBoolean()) m.setEnabled(true);
        m.knob = m.enabled() ? 1.0F : 0.0F;
    }

    static void unregister(Module m) {
        remember(m);
        Modules.SCRIPTED.remove(m);
        if (m instanceof HudModule h) Modules.HUD.remove(h);
        flush();
    }

    static void restore(Module m, Setting<?> s) {
        s.markDefault();
        JsonObject o = saved().getAsJsonObject(m.id);
        if (o == null || !o.has("settings")) return;
        JsonObject st = o.getAsJsonObject("settings");
        if (st.has(s.key)) {
            try {
                s.load(st.get(s.key));
            } catch (RuntimeException ignored) {
            }
        }
    }

    static void changed() {
        dirty = true;
    }

    private static void remember(Module m) {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", m.enabled());
        if (m.bind >= 0) o.addProperty("bind", m.bind);
        if (m instanceof HudModule h) {
            o.addProperty("x", h.fx);
            o.addProperty("y", h.fy);
        }
        JsonObject st = new JsonObject();
        for (Setting<?> s : m.settings) st.add(s.key, s.save());
        o.add("settings", st);
        saved().add(m.id, o);
    }

    /** Every couple of seconds: writes the file when something changed (settings change from the menu too). */
    static void tick() {
        if (++ticks % 40 != 0 && !dirty) return;
        dirty = false;
        for (Module m : Modules.SCRIPTED) remember(m);
        flush();
    }

    private static void flush() {
        String json = GSON.toJson(saved());
        if (json.equals(written)) return;
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json, StandardCharsets.UTF_8);
            written = json;
        } catch (IOException e) {
            Scripts.log("scripts", "не удалось сохранить модули: " + e.getMessage());
        }
    }
}
