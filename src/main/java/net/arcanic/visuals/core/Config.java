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

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.arcanic.visuals.hud.HudModule;
import net.fabricmc.loader.api.FabricLoader;

/** config/arcanicvisuals.json (current state) and config/arcanicvisuals/presets/*.json (saved presets). */
public final class Config {
    private Config() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals.json");
    private static final Path PRESETS =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals").resolve("presets");
    private static long dirtyAt;
    private static String active = "";
    private static final Map<String, Seen> SEEN = new HashMap<>();

    /** A saved preset as the list shows it; {@code server} is empty for one that is not tied to a server. */
    public record Preset(String name, String server, long saved, String code, boolean cloud) {}

    private record Seen(long modified, Preset preset) {}

    public static JsonObject snapshot() {
        JsonObject root = new JsonObject();
        JsonObject theme = new JsonObject();
        theme.addProperty("palette", Theme.palette);
        theme.addProperty("style", Theme.style);
        theme.addProperty("scale", Theme.menuScale);
        theme.addProperty("animations", Theme.animations);
        theme.addProperty("frontBackground", Theme.frontBackground);
        theme.addProperty("accentA", Integer.toHexString(Theme.customA));
        theme.addProperty("accentB", Integer.toHexString(Theme.customB));
        root.add("theme", theme);
        JsonObject mods = new JsonObject();
        for (Module m : Modules.ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.enabled());
            if (m.bind >= 0) o.addProperty("bind", m.bind);
            JsonObject s = new JsonObject();
            for (Setting<?> st : m.settings) s.add(st.key, st.save());
            o.add("settings", s);
            if (m instanceof HudModule h) {
                o.addProperty("x", h.fx);
                o.addProperty("y", h.fy);
            }
            mods.add(m.id, o);
        }
        root.add("modules", mods);
        JsonObject menu = new JsonObject();
        JsonObject ms = new JsonObject();
        for (Setting<?> st : Modules.PREFS.settings) ms.add(st.key, st.save());
        menu.add("settings", ms);
        menu.addProperty("x", MenuPrefs.posX);
        menu.addProperty("y", MenuPrefs.posY);
        menu.addProperty("compact", MenuPrefs.compactSidebar);
        JsonArray colours = new JsonArray();
        for (int c : Setting.Colour.PRESETS) colours.add(Integer.toHexString(c));
        menu.add("colourPresets", colours);
        root.add("menu", menu);
        return root;
    }

    public static void apply(JsonObject root) {
        if (root.has("theme")) {
            JsonObject t = root.getAsJsonObject("theme");
            if (t.has("palette"))
                Theme.palette = t.get("palette").getAsInt() == Theme.LIGHT
                        ? Theme.LIGHT
                        : Theme.NIGHT; // only night / day remain; no crossfade on load
            if (t.has("style")) Theme.style = Math.floorMod(t.get("style").getAsInt(), 2);
            if (t.has("scale"))
                Theme.menuScale = Math.max(.7, Math.min(1.3, t.get("scale").getAsDouble()));
            if (t.has("animations")) Theme.animations = t.get("animations").getAsBoolean();
            if (t.has("frontBackground"))
                Theme.frontBackground =
                        Math.max(1, Math.min(4, t.get("frontBackground").getAsInt()));
            try {
                if (t.has("accentA"))
                    Theme.customA = (int) Long.parseLong(t.get("accentA").getAsString(), 16);
                if (t.has("accentB"))
                    Theme.customB = (int) Long.parseLong(t.get("accentB").getAsString(), 16);
                Theme.customA = 0;
                Theme.customB = 0; // custom accents went away with the theme page
            } catch (RuntimeException ignored) {
            }
        }
        if (root.has("menu")) {
            JsonObject menu = root.getAsJsonObject("menu");
            if (menu.has("colourPresets") && menu.get("colourPresets").isJsonArray()) {
                List<Integer> colours = new ArrayList<>();
                for (JsonElement c : menu.getAsJsonArray("colourPresets")) {
                    if (colours.size() >= 10) break;
                    try {
                        colours.add((int) Long.parseLong(c.getAsString(), 16));
                    } catch (RuntimeException ignored) {
                    }
                }
                Setting.Colour.PRESETS.clear();
                Setting.Colour.PRESETS.addAll(colours);
            }
            if (menu.has("settings")) {
                JsonObject ms = menu.getAsJsonObject("settings");
                for (Setting<?> st : Modules.PREFS.settings)
                    if (ms.has(st.key))
                        try {
                            st.load(ms.get(st.key));
                        } catch (RuntimeException ignored) {
                        }
            }
        } else { // older configs kept these two in the theme
            Modules.PREFS.scale.set(Theme.menuScale * 100);
            Modules.PREFS.animations.set(Theme.animations);
        }
        Modules.PREFS.sync();
        if (!root.has("modules")) return;
        JsonObject mods = root.getAsJsonObject("modules");
        if (!mods.has("arc_hand_shader") && mods.has("weapon_paint")) {
            JsonObject old = mods.getAsJsonObject("weapon_paint");
            if (old.has("enabled"))
                Modules.HAND_SHADER.setEnabled(old.get("enabled").getAsBoolean());
            if (old.has("bind")) Modules.HAND_SHADER.bind = old.get("bind").getAsInt();
        }
        for (Module m : Modules.ALL) {
            if (!mods.has(m.id)) continue;
            JsonObject o = mods.getAsJsonObject(m.id);
            if (o.has("settings")) {
                JsonObject s = o.getAsJsonObject("settings");
                for (Setting<?> st : m.settings)
                    if (s.has(st.key))
                        try {
                            st.load(s.get(st.key));
                        } catch (RuntimeException ignored) {
                        }
            }
            if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
            m.bind = o.has("bind") ? o.get("bind").getAsInt() : -1;
            m.knob = m.enabled() ? 1 : 0;
            if (m instanceof HudModule h && o.has("x") && o.has("y")) {
                h.fx = o.get("x").getAsDouble();
                h.fy = o.get("y").getAsDouble();
            }
        }
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                apply(root);
                // only from the main file (never from a preset): the panel position
                if (root.has("menu")) {
                    JsonObject menu = root.getAsJsonObject("menu");
                    if (menu.has("x")) MenuPrefs.posX = menu.get("x").getAsDouble();
                    if (menu.has("y")) MenuPrefs.posY = menu.get("y").getAsDouble();
                    if (menu.has("compact"))
                        MenuPrefs.compactSidebar = menu.get("compact").getAsBoolean();
                    if (menu.has("preset")) active = menu.get("preset").getAsString();
                    if (!Files.exists(file(active))) active = "";
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[ArcanicVisuals] config: " + e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = snapshot();
            root.getAsJsonObject("menu").addProperty("preset", active);
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[ArcanicVisuals] save: " + e);
        }
        dirtyAt = 0;
    }

    public static void dirty() {
        if (dirtyAt == 0) dirtyAt = System.currentTimeMillis();
    }

    public static void tick() {
        if (dirtyAt != 0 && System.currentTimeMillis() - dirtyAt > 800) save();
    }

    private static Path file(String name) {
        return PRESETS.resolve(name + ".json");
    }

    public static Path presetsFolder() throws IOException {
        return Files.createDirectories(PRESETS);
    }

    /** The preset loaded or saved last, "" if there is none. */
    public static String activePreset() {
        return active;
    }

    /** Every saved preset, by name. A file is read again only after it changed. */
    public static List<Preset> presets() {
        List<Preset> out = new ArrayList<>();
        try (var s = Files.list(PRESETS)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String file = p.getFileName().toString();
                if (!file.endsWith(".json")) continue;
                String name = file.substring(0, file.length() - 5);
                long modified = Files.getLastModifiedTime(p).toMillis();
                Seen seen = SEEN.get(name);
                if (seen == null || seen.modified() != modified) {
                    seen = new Seen(modified, describe(name, read(name), modified));
                    SEEN.put(name, seen);
                }
                out.add(seen.preset());
            }
        } catch (IOException ignored) {
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    private static Preset describe(String name, JsonObject data, long modified) {
        JsonObject about = data != null && data.has("about") ? data.getAsJsonObject("about") : new JsonObject();
        return new Preset(
                name,
                about.has("server") ? about.get("server").getAsString() : "",
                about.has("saved") ? about.get("saved").getAsLong() : modified,
                about.has("code") ? about.get("code").getAsString() : "",
                about.has("cloud") && about.get("cloud").getAsBoolean());
    }

    private static JsonObject read(String name) {
        try {
            return JsonParser.parseString(Files.readString(file(name), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static JsonObject about(String server, String code, boolean cloud) {
        JsonObject about = new JsonObject();
        about.addProperty("server", server);
        about.addProperty("saved", System.currentTimeMillis());
        if (!code.isEmpty()) about.addProperty("code", code);
        if (cloud) about.addProperty("cloud", true);
        return about;
    }

    /**
     * Saves the current settings as a new preset and makes it the active one; a name that is taken gets " (2)",
     * " (3)" and so on.
     */
    public static String savePreset(String name, String server) {
        JsonObject data = snapshot();
        data.add("about", about(server, "", false));
        String kept = writePreset(name, data);
        if (kept != null) {
            active = kept;
            dirty();
        }
        return kept;
    }

    /** Writes the current settings over a preset that exists; what is known about it stays. */
    public static boolean overwritePreset(String name) {
        JsonObject old = read(name);
        if (old == null) return false;
        Preset was = describe(name, old, System.currentTimeMillis());
        JsonObject data = snapshot();
        data.add("about", about(was.server(), "", was.cloud()));
        try {
            Files.writeString(file(name), GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return false;
        }
        active = name;
        dirty();
        return true;
    }

    /** A preset someone shared: kept under its own name like one made here. */
    public static String importPreset(String name, JsonObject data, String code) {
        data.add("about", about("", code, true));
        return writePreset(name, data);
    }

    /** The settings of a preset without the notes about it: what goes to the server when it is shared. */
    public static JsonObject presetData(String name) {
        JsonObject data = read(name);
        if (data != null) data.remove("about");
        return data;
    }

    /** Remembers the code a preset was shared under. */
    public static void presetShared(String name, String code) {
        JsonObject data = read(name);
        if (data == null) return;
        JsonObject about = data.has("about") ? data.getAsJsonObject("about") : about("", "", false);
        about.addProperty("code", code);
        data.add("about", about);
        try {
            Files.writeString(file(name), GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String writePreset(String wanted, JsonObject data) {
        String base = wanted.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (base.length() > 32) base = base.substring(0, 32).trim();
        if (base.isEmpty()) base = Lang.tr("Пресет");
        try {
            Files.createDirectories(PRESETS);
            String name = base;
            for (int n = 2; Files.exists(file(name)); n++) name = base + " (" + n + ")";
            Files.writeString(file(name), GSON.toJson(data), StandardCharsets.UTF_8);
            return name;
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean loadPreset(String name) {
        JsonObject preset = read(name);
        if (preset == null) return false;
        try {
            if (preset.has("menu") && preset.getAsJsonObject("menu").has("settings"))
                preset.getAsJsonObject("menu").getAsJsonObject("settings").remove("language");
            apply(preset);
        } catch (RuntimeException e) {
            return false;
        }
        active = name;
        save();
        return true;
    }

    public static boolean deletePreset(String name) {
        try {
            if (!Files.deleteIfExists(file(name))) return false;
        } catch (IOException e) {
            return false;
        }
        SEEN.remove(name);
        if (name.equals(active)) {
            active = "";
            dirty();
        }
        return true;
    }

    public static void resetAll() {
        for (Module m : Modules.ALL) for (Setting<?> s : m.settings) s.reset();
        save();
    }
}
