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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import net.arcanic.visuals.hud.Toasts;
import net.minecraft.client.Minecraft;

/** Presets shared through our server: a preset goes up and comes back as a short code anyone can paste in. */
public final class CloudPresets {
    private CloudPresets() {}

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    /** Uploads a saved preset; its code is kept with the preset and ends up in the clipboard. */
    public static void share(String name) {
        JsonObject data = Config.presetData(name);
        if (data == null) {
            Toasts.setting(Lang.tr("Не удалось поделиться пресетом"), false);
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.add("data", data);
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(Sync.api() + "/api/v1/presets"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (Sync.signedIn()) req.header("Authorization", "Bearer " + Sync.token());
        HTTP.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString()).whenComplete((r, e) -> {
            String code = e == null && r.statusCode() == 200 ? field(r.body(), "code") : null;
            if (code == null) {
                boolean busy = r != null && r.statusCode() == 429;
                done(() -> Toasts.setting(
                        Lang.tr(busy ? "Слишком часто, подожди немного" : "Не удалось поделиться пресетом"), false));
                return;
            }
            done(() -> {
                Config.presetShared(name, code);
                Minecraft.getInstance().keyboardHandler.setClipboard(code);
                Toasts.setting(Lang.tr("Код скопирован") + ": " + code, true);
            });
        });
    }

    /** Fetches a shared preset by its code and keeps it; {@code saved} gets its name. */
    public static void fetch(String code, Consumer<String> saved) {
        String clean = code.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (clean.isEmpty()) return;
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(Sync.api() + "/api/v1/presets/" + URLEncoder.encode(clean, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((r, e) -> {
            JsonObject o = e == null && r.statusCode() == 200 ? object(r.body()) : null;
            if (o == null || !o.has("data") || !o.get("data").isJsonObject()) {
                boolean none = r != null && r.statusCode() == 404;
                done(() -> Toasts.setting(
                        Lang.tr(none ? "Пресета с таким кодом нет" : "Не удалось загрузить пресет"), false));
                return;
            }
            String name = o.has("name") ? o.get("name").getAsString() : clean;
            done(() -> {
                String kept = Config.importPreset(name, o.getAsJsonObject("data"), clean);
                if (kept != null) saved.accept(kept);
            });
        });
    }

    private static JsonObject object(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String field(String body, String key) {
        JsonObject o = object(body);
        return o != null && o.has(key) ? o.get(key).getAsString() : null;
    }

    private static void done(Runnable r) {
        Minecraft.getInstance().execute(r);
    }
}
