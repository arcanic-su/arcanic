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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.arcanic.visuals.hud.DynamicIsland;
import net.arcanic.visuals.hud.HudModule;
import net.fabricmc.loader.api.FabricLoader;

public final class Config {
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals.json");
    private static final Path PRESETS =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals").resolve("presets");
    private static long dirtyAt;
    private static String active = "";
    private static final Map<String, Seen> SEEN = new HashMap<>();
    private static long changedAt;
    private static boolean changed;

    private Config() {}

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
            if (m.bind >= 0) {
                o.addProperty("bind", m.bind);
            }

            JsonObject s = new JsonObject();

            for (Setting<?> st : m.settings) {
                s.add(st.key, st.save());
            }

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

        for (Setting<?> st : Modules.PREFS.settings) {
            ms.add(st.key, st.save());
        }

        menu.add("settings", ms);
        menu.addProperty("x", MenuPrefs.posX);
        menu.addProperty("y", MenuPrefs.posY);
        menu.addProperty("compact", MenuPrefs.compactSidebar);
        JsonArray colours = new JsonArray();

        for (int c : Setting.Colour.PRESETS) {
            colours.add(Integer.toHexString(c));
        }

        menu.add("colourPresets", colours);
        root.add("menu", menu);
        return root;
    }

    public static void apply(JsonObject root) {
        if (root.has("theme")) {
            JsonObject t = root.getAsJsonObject("theme");
            if (t.has("palette")) {
                Theme.palette = t.get("palette").getAsInt() == 5 ? 5 : 0;
            }

            if (t.has("style")) {
                Theme.style = Math.floorMod(t.get("style").getAsInt(), 2);
            }

            if (t.has("scale")) {
                Theme.menuScale = Math.max(0.5, Math.min(2.0, t.get("scale").getAsDouble()));
            }

            if (t.has("animations")) {
                Theme.animations = t.get("animations").getAsBoolean();
            }

            if (t.has("frontBackground")) {
                Theme.frontBackground =
                        Math.max(1, Math.min(4, t.get("frontBackground").getAsInt()));
            }

            try {
                if (t.has("accentA")) {
                    Theme.customA = (int) Long.parseLong(t.get("accentA").getAsString(), 16);
                }

                if (t.has("accentB")) {
                    Theme.customB = (int) Long.parseLong(t.get("accentB").getAsString(), 16);
                }

                Theme.customA = 0;
                Theme.customB = 0;
            } catch (RuntimeException ignored) {
            }
        }

        if (root.has("menu")) {
            JsonObject menu = root.getAsJsonObject("menu");
            if (menu.has("colourPresets") && menu.get("colourPresets").isJsonArray()) {
                List<Integer> colours = new ArrayList<>();

                for (JsonElement c : menu.getAsJsonArray("colourPresets")) {
                    if (colours.size() >= 10) {
                        break;
                    }

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

                for (Setting<?> st : Modules.PREFS.settings) {
                    if (ms.has(st.key)) {
                        try {
                            st.load(ms.get(st.key));
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
            }
        } else {
            Modules.PREFS.scale.set(Theme.menuScale * 100.0);
            Modules.PREFS.animations.set(Theme.animations);
        }

        Modules.PREFS.sync();
        if (root.has("modules")) {
            JsonObject mods = root.getAsJsonObject("modules");
            if (!mods.has("arc_hand_shader") && mods.has("weapon_paint")) {
                JsonObject old = mods.getAsJsonObject("weapon_paint");
                if (old.has("enabled")) {
                    Modules.HAND_SHADER.setEnabled(old.get("enabled").getAsBoolean());
                }

                if (old.has("bind")) {
                    Modules.HAND_SHADER.bind = old.get("bind").getAsInt();
                }
            }

            String[][] renamed = new String[][] {
                {"rs_ambience", "ambience"}, {"rs_hand_glow", "hand_glow"}, {"rv_projectile", "projectile_hit"}
            };

            for (String[] r : renamed) {
                if (mods.has(r[0]) && !mods.has(r[1])) {
                    mods.add(r[1], mods.get(r[0]));
                }
            }

            if (!mods.has(Modules.HOLY_ITEMS.id)) {
                Modules.HOLY_ITEMS.migrate(mods);
            }

            for (Module m : Modules.ALL) {
                if (mods.has(m.id)) {
                    JsonObject o = mods.getAsJsonObject(m.id);
                    if (o.has("settings")) {
                        JsonObject s = o.getAsJsonObject("settings");

                        for (Setting<?> st : m.settings) {
                            if (s.has(st.key)) {
                                try {
                                    st.load(s.get(st.key));
                                } catch (RuntimeException ignored) {
                                }
                            }
                        }
                    }

                    if (o.has("enabled")) {
                        m.setEnabled(o.get("enabled").getAsBoolean());
                    }

                    m.bind = o.has("bind") ? o.get("bind").getAsInt() : -1;
                    m.knob = m.enabled() ? 1.0F : 0.0F;
                    if (m instanceof HudModule) {
                        HudModule h = (HudModule) m;
                        if (o.has("x") && o.has("y")) {
                            h.fx = o.get("x").getAsDouble();
                            h.fy = o.get("y").getAsDouble();
                        }
                    }
                }
            }
        }
    }

    public static void load() {
        try {
            if (Files.exists(FILE, new LinkOption[0])) {
                JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                apply(root);
                if (root.has("menu")) {
                    JsonObject menu = root.getAsJsonObject("menu");
                    if (menu.has("x")) {
                        MenuPrefs.posX = menu.get("x").getAsDouble();
                    }

                    if (menu.has("y")) {
                        MenuPrefs.posY = menu.get("y").getAsDouble();
                    }

                    if (menu.has("compact")) {
                        MenuPrefs.compactSidebar = menu.get("compact").getAsBoolean();
                    }

                    if (menu.has("preset")) {
                        active = menu.get("preset").getAsString();
                    }

                    if (!Files.exists(file(active), new LinkOption[0])) {
                        active = "";
                    }
                }
            } else {
                firstStart();
            }
        } catch (RuntimeException | IOException e) {
            System.err.println("[ArcanicVisuals] config: " + String.valueOf(e));
        }
    }

    private static void firstStart() {
        for (Module m : Modules.ALL) {
            m.initial(m == Modules.OPTIMIZER || m instanceof DynamicIsland);
        }

        Modules.OPTIMIZER.profile.set("Максимум FPS");
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = snapshot();
            root.getAsJsonObject("menu").addProperty("preset", active);
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[ArcanicVisuals] save: " + String.valueOf(e));
        }

        dirtyAt = 0L;
    }

    public static void dirty() {
        if (dirtyAt == 0L) {
            dirtyAt = System.currentTimeMillis();
        }
    }

    public static void tick() {
        if (dirtyAt != 0L && System.currentTimeMillis() - dirtyAt > 800L) {
            save();
            if (!active.isEmpty() && Modules.PREFS != null && Modules.PREFS.presetAutosave.on()) {
                changedAt = 0L;
                if (activeChanged()) {
                    writeActive();
                }
            }
        }
    }

    public static boolean activeChanged() {
        if (active.isEmpty()) {
            return false;
        } else {
            long now = System.currentTimeMillis();
            if (now - changedAt < 450L) {
                return changed;
            } else {
                changedAt = now;
                JsonObject saved = read(active);
                if (saved == null) {
                    changed = false;
                    return false;
                } else {
                    JsonObject current = snapshot();
                    changed = !same(current, saved, "modules") || !same(current, saved, "theme");
                    return changed;
                }
            }
        }
    }

    private static boolean same(JsonObject a, JsonObject b, String key) {
        JsonElement x = a.get(key);
        JsonElement y = b.get(key);
        return x == null ? y == null : x.equals(y);
    }

    private static void writeActive() {
        JsonObject old = read(active);
        if (old != null) {
            Preset was = describe(active, old, System.currentTimeMillis());
            JsonObject data = snapshot();
            data.add("about", about(was.server(), was.code(), was.cloud()));

            try {
                Files.writeString(file(active), GSON.toJson(data), StandardCharsets.UTF_8);
                changed = false;
                changedAt = System.currentTimeMillis();
            } catch (IOException ignored) {
            }
        }
    }

    private static Path file(String name) {
        return PRESETS.resolve(name + ".json");
    }

    public static Path presetsFolder() throws IOException {
        return Files.createDirectories(PRESETS);
    }

    public static String activePreset() {
        return active;
    }

    public static List<Preset> presets() {
        List<Preset> out = new ArrayList<>();

        try {
            Stream<Path> s = Files.list(PRESETS);

            try {

                for (Path p : (Iterable<Path>) s::iterator) {
                    String file = p.getFileName().toString();
                    if (file.endsWith(".json")) {
                        String name = file.substring(0, file.length() - 5);
                        long modified = Files.getLastModifiedTime(p).toMillis();
                        Seen seen = (Seen) SEEN.get(name);
                        if (seen == null || seen.modified() != modified) {
                            seen = new Seen(modified, describe(name, read(name), modified));
                            SEEN.put(name, seen);
                        }

                        out.add(seen.preset());
                    }
                }
            } catch (Throwable var10) {
                if (s != null) {
                    try {
                        s.close();
                    } catch (Throwable var9) {
                        var10.addSuppressed(var9);
                    }
                }

                throw var10;
            }

            if (s != null) {
                s.close();
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
        } catch (RuntimeException | IOException var2) {
            return null;
        }
    }

    private static JsonObject about(String server, String code, boolean cloud) {
        JsonObject about = new JsonObject();
        about.addProperty("server", server);
        about.addProperty("saved", System.currentTimeMillis());
        if (!code.isEmpty()) {
            about.addProperty("code", code);
        }

        if (cloud) {
            about.addProperty("cloud", true);
        }

        return about;
    }

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

    public static boolean overwritePreset(String name) {
        JsonObject old = read(name);
        if (old == null) {
            return false;
        } else {
            Preset was = describe(name, old, System.currentTimeMillis());
            JsonObject data = snapshot();
            data.add("about", about(was.server(), "", was.cloud()));

            try {
                Files.writeString(file(name), GSON.toJson(data), StandardCharsets.UTF_8);
            } catch (IOException var5) {
                return false;
            }

            active = name;
            dirty();
            return true;
        }
    }

    public static String importPreset(String name, JsonObject data, String code) {
        data.add("about", about("", code, true));
        return writePreset(name, data);
    }

    public static JsonObject presetData(String name) {
        JsonObject data = read(name);
        if (data != null) {
            data.remove("about");
        }

        return data;
    }

    public static void presetShared(String name, String code) {
        JsonObject data = read(name);
        if (data != null) {
            JsonObject about = data.has("about") ? data.getAsJsonObject("about") : about("", "", false);
            about.addProperty("code", code);
            data.add("about", about);

            try {
                Files.writeString(file(name), GSON.toJson(data), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }
    }

    private static String writePreset(String wanted, JsonObject data) {
        String base = wanted.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (base.length() > 32) {
            base = base.substring(0, 32).trim();
        }

        if (base.isEmpty()) {
            base = Lang.tr("Пресет");
        }

        try {
            Files.createDirectories(PRESETS);
            String name = base;

            for (int n = 2; Files.exists(file(name), new LinkOption[0]); ++n) {
                name = base + " (" + n + ")";
            }

            Files.writeString(file(name), GSON.toJson(data), StandardCharsets.UTF_8);
            return name;
        } catch (IOException var5) {
            return null;
        }
    }

    public static boolean loadPreset(String name) {
        JsonObject preset = read(name);
        if (preset == null) {
            return false;
        } else {
            try {
                if (preset.has("menu") && preset.getAsJsonObject("menu").has("settings")) {
                    preset.getAsJsonObject("menu").getAsJsonObject("settings").remove("language");
                }

                apply(preset);
            } catch (RuntimeException var3) {
                return false;
            }

            active = name;
            save();
            return true;
        }
    }

    public static boolean deletePreset(String name) {
        try {
            if (!Files.deleteIfExists(file(name))) {
                return false;
            }
        } catch (IOException var2) {
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
        for (Module m : Modules.ALL) {
            for (Setting<?> s : m.settings) {
                s.reset();
            }
        }

        save();
    }

    public static record Preset(String name, String server, long saved, String code, boolean cloud) {}

    private static record Seen(long modified, Preset preset) {}
}
