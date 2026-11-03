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
package net.arcanic.visuals.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.server.level.ParticleStatus;

public final class Optimizer extends Module {
    public final Setting.Mode profile = this.add(
            new Setting.Mode("profile", "Режим", "Максимум FPS", new String[] {"Ультра", "Максимум FPS", "Баланс"}));
    public final Setting.Num distance =
            this.add(new Setting.Num("distance", "Дальность прорисовки", 6.0, 2.0, 16.0, 1.0, " чанков"));
    public final Setting.Bool particles = this.add(new Setting.Bool("particles", "Меньше частиц", true));
    public final Setting.Bool effects = this.add(new Setting.Bool("effects", "Упростить эффекты Arcanic", true));
    public final Setting.Bool unlock = this.add(new Setting.Bool("unlock", "Снять ограничение FPS", true));
    private String applied;
    private static final String MODULES = "_modules";

    public Optimizer() {
        super(
                "optimizer",
                "Оптимайзер",
                "Для слабых ПК: снижает настройки графики и упрощает тяжёлые эффекты, чтобы поднять FPS. Выключишь — всё вернётся как было.",
                Category.WORLD,
                true);
    }

    public static boolean lowEffects() {
        Optimizer o = Modules.OPTIMIZER;
        return o != null && o.enabled() && o.effects.on();
    }

    private static <T> Slot slot(final OptionInstance<T> o, final Function<String, T> parse) {
        return new Slot() {
            public String get() {
                T v = o.get();
                String var10000;
                if (v instanceof Enum<?> e) {
                    var10000 = e.name();
                } else {
                    var10000 = String.valueOf(v);
                }

                return var10000;
            }

            public void set(String v) {
                o.set(parse.apply(v));
            }
        };
    }

    private static Map<String, Slot> slots() {
        Options o = Minecraft.getInstance().options;
        Map<String, Slot> m = new LinkedHashMap<>();
        m.put("renderDistance", slot(o.renderDistance(), Integer::valueOf));
        m.put("simulationDistance", slot(o.simulationDistance(), Integer::valueOf));
        m.put("entityDistanceScaling", slot(o.entityDistanceScaling(), Double::valueOf));
        m.put("cloudStatus", slot(o.cloudStatus(), CloudStatus::valueOf));
        m.put("particles", slot(o.particles(), ParticleStatus::valueOf));
        m.put("entityShadows", slot(o.entityShadows(), Boolean::valueOf));
        m.put("ambientOcclusion", slot(o.ambientOcclusion(), Boolean::valueOf));
        m.put("biomeBlendRadius", slot(o.biomeBlendRadius(), Integer::valueOf));
        m.put("improvedTransparency", slot(o.improvedTransparency(), Boolean::valueOf));
        m.put("cutoutLeaves", slot(o.cutoutLeaves(), Boolean::valueOf));
        m.put("vignette", slot(o.vignette(), Boolean::valueOf));
        m.put("chunkSectionFadeInTime", slot(o.chunkSectionFadeInTime(), Double::valueOf));
        m.put("prioritizeChunkUpdates", slot(o.prioritizeChunkUpdates(), PrioritizeChunkUpdates::valueOf));
        m.put("menuBackgroundBlurriness", slot(o.menuBackgroundBlurriness(), Integer::valueOf));
        m.put("mipmapLevels", slot(o.mipmapLevels(), Integer::valueOf));
        m.put("bobView", slot(o.bobView(), Boolean::valueOf));
        m.put("screenEffectScale", slot(o.screenEffectScale(), Double::valueOf));
        m.put("fovEffectScale", slot(o.fovEffectScale(), Double::valueOf));
        m.put("enableVsync", slot(o.enableVsync(), Boolean::valueOf));
        m.put("framerateLimit", slot(o.framerateLimit(), Integer::valueOf));
        return m;
    }

    private static File backupFile() {
        return new File(Minecraft.getInstance().gameDirectory, "config/arcanic-optimizer.json");
    }

    private Map<String, String> wanted(Map<String, String> before) {
        boolean ultra = this.ultra();
        boolean max = ultra || this.profile.is("Максимум FPS");
        Map<String, String> w = new LinkedHashMap<>();
        int had = parse((String) before.get("renderDistance"), 12);
        int sim = parse((String) before.get("simulationDistance"), 12);
        w.put(
                "renderDistance",
                String.valueOf(Math.max(
                        2,
                        Math.min(
                                had,
                                ultra
                                        ? Math.min(this.distance.i(), 4)
                                        : (max ? this.distance.i() : Math.max(this.distance.i(), 8))))));
        w.put("simulationDistance", String.valueOf(Math.max(5, Math.min(sim, max ? 5 : 8))));
        w.put("entityDistanceScaling", max ? "0.5" : "0.75");
        w.put("cloudStatus", max ? "OFF" : "FAST");
        if (this.particles.on() || ultra) {
            w.put("particles", max ? "MINIMAL" : "DECREASED");
        }

        if (ultra) {
            w.put("mipmapLevels", "0");
            w.put("bobView", "false");
            w.put("screenEffectScale", "0.0");
            w.put("fovEffectScale", "0.0");
        }

        w.put("entityShadows", "false");
        w.put("ambientOcclusion", max ? "false" : "true");
        w.put("biomeBlendRadius", max ? "0" : "1");
        w.put("improvedTransparency", "false");
        w.put("cutoutLeaves", "true");
        if (max) {
            w.put("vignette", "false");
            w.put("chunkSectionFadeInTime", "0.0");
            w.put("menuBackgroundBlurriness", "0");
        }

        w.put("prioritizeChunkUpdates", "NONE");
        if (this.unlock.on() || ultra) {
            w.put("enableVsync", "false");
            w.put("framerateLimit", "260");
        }

        return w;
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException var3) {
            return def;
        }
    }

    public boolean ultra() {
        return this.profile.is("Ультра");
    }

    private static boolean visual(Module m) {
        return m != Modules.OPTIMIZER
                && m != Modules.KILLSOUND
                && m != Modules.HITSOUND
                && m != Modules.NOTIFY
                && m.category != Category.HUD;
    }

    private void visuals(Map<String, String> before) throws IOException {
        if (this.enabled() && this.ultra()) {
            if (!before.containsKey("_modules")) {
                StringBuilder on = new StringBuilder();

                for (Module m : Modules.ALL) {
                    if (visual(m) && m.enabled()) {
                        on.append(on.length() > 0 ? "," : "").append(m.id);
                    }
                }

                before.put("_modules", on.toString());
                write(before);
            }

            for (Module m : Modules.ALL) {
                if (visual(m) && m.enabled()) {
                    m.setEnabled(false);
                }
            }
        } else if (before.containsKey("_modules")) {
            Set<String> ids = new HashSet(Arrays.asList(((String) before.remove("_modules")).split(",")));

            for (Module m : Modules.ALL) {
                if (ids.contains(m.id) && !m.enabled()) {
                    m.setEnabled(true);
                }
            }

            write(before);
        }

        Config.dirty();
    }

    private static void write(Map<String, String> before) throws IOException {
        JsonObject o = new JsonObject();
        before.forEach(o::addProperty);
        File f = backupFile();
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), o.toString(), StandardCharsets.UTF_8);
    }

    private String signature() {
        String var10000 = (String) this.profile.get();
        return var10000 + "|" + this.distance.i() + "|" + this.particles.on() + "|" + this.unlock.on();
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        OptimizerMod.set(true);
        if (mc.options != null && mc.level != null) {
            String now = this.signature();
            if (!now.equals(this.applied)) {
                this.apply();
                this.applied = now;
            }
        }
    }

    protected void onDisable() {
        this.restore();
        this.applied = null;
        OptimizerMod.set(false);
    }

    private void apply() {
        try {
            Map<String, Slot> slots = slots();
            Map<String, String> before = readBackup();
            if (before == null) {
                before = new LinkedHashMap<>();

                for (Map.Entry<String, Slot> e : slots.entrySet()) {
                    before.put((String) e.getKey(), ((Slot) e.getValue()).get());
                }

                write(before);
            }

            for (Map.Entry<String, Slot> e : slots.entrySet()) {
                before.putIfAbsent((String) e.getKey(), ((Slot) e.getValue()).get());
            }

            this.visuals(before);
            Map<String, String> want = this.wanted(before);

            for (Map.Entry<String, Slot> e : slots.entrySet()) {
                String v = (String) want.getOrDefault(e.getKey(), (String) before.get(e.getKey()));
                if (v != null && !v.equals(((Slot) e.getValue()).get())) {
                    try {
                        ((Slot) e.getValue()).set(v);
                    } catch (RuntimeException ex) {
                        PrintStream var10000 = System.err;
                        String var10001 = (String) e.getKey();
                        var10000.println("[ArcanicVisuals] optimizer " + var10001 + ": " + String.valueOf(ex));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ArcanicVisuals] optimizer: " + String.valueOf(e));
        }
    }

    private void restore() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options == null) {
                return;
            }

            Map<String, String> before = readBackup();
            if (before == null) {
                return;
            }

            Map<String, Slot> slots = slots();
            if (before.containsKey("_modules")) {
                Set<String> ids = new HashSet(Arrays.asList(((String) before.get("_modules")).split(",")));

                for (Module m : Modules.ALL) {
                    if (ids.contains(m.id) && !m.enabled()) {
                        m.setEnabled(true);
                    }
                }

                Config.dirty();
            }

            for (Map.Entry<String, String> e : before.entrySet()) {
                Slot s = (Slot) slots.get(e.getKey());
                if (s != null && !((String) e.getValue()).equals(s.get())) {
                    try {
                        s.set((String) e.getValue());
                    } catch (RuntimeException ex) {
                        PrintStream var10000 = System.err;
                        String var10001 = (String) e.getKey();
                        var10000.println("[ArcanicVisuals] optimizer " + var10001 + ": " + String.valueOf(ex));
                    }
                }
            }

            Files.deleteIfExists(backupFile().toPath());
            mc.options.save();
        } catch (Exception e) {
            System.err.println("[ArcanicVisuals] optimizer: " + String.valueOf(e));
        }
    }

    private static Map<String, String> readBackup() {
        File f = backupFile();
        if (!f.isFile()) {
            return null;
        } else {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                Map<String, String> m = new LinkedHashMap<>();

                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    m.put((String) e.getKey(), ((JsonElement) e.getValue()).getAsString());
                }

                return m;
            } catch (Exception var5) {
                return null;
            }
        }
    }

    private interface Slot {
        String get();

        void set(String var1);
    }
}
