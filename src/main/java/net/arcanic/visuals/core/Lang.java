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

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Display-only translations. */
public final class Lang {
    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> RU = new HashMap<>();

    private static final Pattern NUMBER = Pattern.compile(
            "^([+−\\-]?[0-9]+(?:[.,][0-9]+)?)( функций| колонки| активно| чанков| МБ| мс| бл| м| с| т| ч)$");
    private static final String[] PREFIXES = {
        "Профиль «",
        "Сейчас играешь как ",
        "Сохранено: ",
        "Загружен: ",
        "Удалён: ",
        "Плащ: ",
        "Нож: ",
        "Пресет ",
        "ЛКМ  ",
        "Играешь как ",
        "Шляпа: ",
        "Головной убор: ",
        "Рюкзак: ",
        "Крылья: ",
        "Питомец: "
    };
    private static final String[] SUFFIXES = {
        "» возвращён", "» добавлен — нажми на него, чтобы играть", "» добавлен", "» удалён", " · выкл", " — без клавиши"
    };
    private static final Map<String, String> VANILLA = Map.ofEntries(
            Map.entry("menu.singleplayer", "Singleplayer"), Map.entry("menu.multiplayer", "Multiplayer"),
            Map.entry("menu.options", "Options"), Map.entry("options.language", "Minecraft language"),
            Map.entry("options.accessibility", "Accessibility"), Map.entry("menu.online", "Minecraft Realms"),
            Map.entry("menu.quit", "Quit game"), Map.entry("menu.playdemo", "Play demo"),
            Map.entry("menu.resetdemo", "Reset demo world"), Map.entry("title.credits", "Credits"));

    static {
        try (var in = Lang.class.getResourceAsStream("/assets/arcanicvisuals/i18n/en.json")) {
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            json.entrySet().forEach(e -> EN.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            throw new IllegalStateException("Missing Arcanic English translations", e);
        }
        try (var in = Lang.class.getResourceAsStream("/assets/arcanicvisuals/i18n/ru.json")) {
            if (in != null)
                JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject()
                        .entrySet()
                        .forEach(e -> RU.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            System.err.println("[ArcanicVisuals] ru.json: " + e);
        }
    }

    private Lang() {}

    public static boolean english() {
        return Modules.PREFS != null && Modules.PREFS.language.is("English");
    }

    public static String tr(String text) {
        return english() ? english(text) : RU.getOrDefault(text, text);
    }

    public static String english(String text) {
        String exact = EN.get(text);
        if (exact != null) return exact;
        boolean russian = false;
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) >= '\u0400' && text.charAt(i) <= '\u04ff') {
                russian = true;
                break;
            }
        if (!russian) return text;
        for (String prefix : PREFIXES)
            if (text.startsWith(prefix)) {
                String rest = text.substring(prefix.length());
                for (String suffix : SUFFIXES)
                    if (rest.endsWith(suffix))
                        return EN.get(prefix) + rest.substring(0, rest.length() - suffix.length()) + EN.get(suffix);
                return EN.get(prefix) + rest;
            }
        for (String suffix : SUFFIXES)
            if (text.endsWith(suffix))
                return english(text.substring(0, text.length() - suffix.length())) + EN.get(suffix);
        int separator = text.indexOf(" — ");
        if (separator > 0 && EN.containsKey(text.substring(0, separator)))
            return EN.get(text.substring(0, separator)) + " — " + english(text.substring(separator + 3));
        // Numeric labels and units, without translating substrings of player or track names.
        var number = NUMBER.matcher(text);
        if (number.matches()) return number.group(1) + EN.getOrDefault(number.group(2), number.group(2));
        return text;
    }

    public static boolean matches(String text, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return text.toLowerCase(Locale.ROOT).contains(q)
                || english(text).toLowerCase(Locale.ROOT).contains(q)
                || RU.getOrDefault(text, text).toLowerCase(Locale.ROOT).contains(q);
    }

    public static Component component(Component value) {
        if (!english()) return value;
        if (value.getContents() instanceof TranslatableContents c && VANILLA.containsKey(c.getKey()))
            return Component.literal(VANILLA.get(c.getKey())).withStyle(value.getStyle());
        return Component.literal(tr(value.getString())).withStyle(value.getStyle());
    }
}
