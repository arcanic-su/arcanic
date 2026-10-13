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
package net.arcanic.visuals.arc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.arcanic.visuals.core.Modules;
import org.slf4j.LoggerFactory;

/** Every melee skin listed in {@code cosmetic/melee/index.json}, read once at start. */
public final class MeleeCatalog {
    private static final Set<String> LAST = Set.of(
            "annihil",
            "cobra_vanguard",
            "executor",
            "mercy",
            "null_annihil",
            "polaris",
            "quenching",
            "shadowkiller",
            "silencefd",
            "thieve_sea",
            "treasure_kn");

    private static final List<MeleeDesign> SKINS = load();

    public static List<MeleeDesign> all() {
        return SKINS;
    }

    public static MeleeDesign byKey(String key) {
        return SKINS.stream().filter(s -> s.getKey().equals(key)).findFirst().orElse(null);
    }

    public static MeleeDesign equipped() {
        return Modules.KNIVES.selected();
    }

    public static String equippedKey() {
        MeleeDesign skin = equipped();
        return skin == null ? null : skin.getKey();
    }

    private static List<MeleeDesign> load() {
        try (InputStream in = MeleeCatalog.class.getResourceAsStream("/assets/arcanic/cosmetic/melee/index.json")) {
            if (in == null) return List.of();
            JsonObject index = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray entries = index.getAsJsonArray("melee");
            if (entries == null) return List.of();
            List<MeleeDesign> skins = new ArrayList<>(entries.size());
            for (JsonElement entry : entries) {
                MeleeDesign skin = readSkin(entry.getAsJsonObject());
                if (skin != null) skins.add(skin);
            }
            skins.sort(Comparator.comparingInt(s -> LAST.contains(s.getKey()) ? 1 : 0));
            return Collections.unmodifiableList(skins);
        } catch (Exception e) {
            LoggerFactory.getLogger("arcanicvisuals").warn("Melee index failed", e);
            return List.of();
        }
    }

    private static MeleeDesign readSkin(JsonObject entry) {
        String key = entry.has("key") ? entry.get("key").getAsString() : null;
        if (key == null || key.isEmpty()) return null;
        try {
            String name = entry.has("name") ? entry.get("name").getAsString() : key;
            String model = entry.has("model") ? entry.get("model").getAsString() : key;
            JsonObject transforms = entry.getAsJsonObject("transforms");
            return new MeleeDesign(
                    key,
                    model,
                    name,
                    readVariants(entry),
                    readStrings(entry, "holyBones"),
                    readTransform(transforms, "thirdperson_righthand"),
                    readTransform(transforms, "thirdperson_lefthand"));
        } catch (Exception e) {
            LoggerFactory.getLogger("arcanicvisuals").warn("Melee entry failed: " + key, e);
            return null;
        }
    }

    private static List<MeleeVariant> readVariants(JsonObject entry) {
        JsonArray variants = entry.getAsJsonArray("variants");
        if (variants == null || variants.isEmpty()) return List.of(MeleeVariant.DEFAULT);
        List<MeleeVariant> out = new ArrayList<>(variants.size());
        for (JsonElement element : variants) {
            JsonObject variant = element.getAsJsonObject();
            String suffix = variant.has("suffix") ? variant.get("suffix").getAsString() : "";
            List<String> hidden = new ArrayList<>();
            JsonArray hiddenBones = variant.getAsJsonArray("hidden");
            if (hiddenBones != null) for (JsonElement bone : hiddenBones) hidden.add(bone.getAsString());
            out.add(new MeleeVariant(suffix, List.copyOf(hidden)));
        }
        return List.copyOf(out);
    }

    private static List<String> readStrings(JsonObject entry, String field) {
        JsonArray array = entry.getAsJsonArray(field);
        if (array == null || array.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(array.size());
        for (JsonElement element : array) out.add(element.getAsString());
        return List.copyOf(out);
    }

    private static MeleeTransform readTransform(JsonObject transforms, String field) {
        if (transforms == null || !transforms.has(field)) return MeleeTransform.IDENTITY;
        JsonObject transform = transforms.getAsJsonObject(field);
        return new MeleeTransform(
                readVector(transform, "rotation", 0),
                readVector(transform, "translation", 0),
                readVector(transform, "scale", 1));
    }

    private static float[] readVector(JsonObject object, String field, float fallback) {
        float[] out = {fallback, fallback, fallback};
        if (object == null || !object.has(field) || !object.get(field).isJsonArray()) return out;
        JsonArray array = object.getAsJsonArray(field);
        for (int i = 0; i < 3 && i < array.size(); i++) out[i] = array.get(i).getAsFloat();
        return out;
    }
}
