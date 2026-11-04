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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Custom menu colours. While {@link #custom} is on, the menu and the HUD accent read their colours from here. */
public final class ThemeStudio extends Module {
    private static final String CODE_PREFIX = "ARCTHEME1:";

    public final Setting.Bool custom = add(new Setting.Bool("custom", "Своя тема", false));
    public final Setting.Colour accent = add(new Setting.Colour("accent", "Акцент", false, 0xFF859CFF));
    public final Setting.Colour accent2 = add(new Setting.Colour("accent2", "Второй акцент", false, 0xFFB4C2FF));
    public final Setting.Colour panel = add(new Setting.Colour("panel", "Фон меню", false, 0xFF141417));
    public final Setting.Colour sidebar = add(new Setting.Colour("sidebar", "Боковая панель", false, 0xFF1B1B1F));
    public final Setting.Colour card = add(new Setting.Colour("card", "Карточки", false, 0xFF161619));
    public final Setting.Colour field = add(new Setting.Colour("field", "Поля и кнопки", false, 0xFF1A1A1E));
    public final Setting.Colour text = add(new Setting.Colour("text", "Текст", false, 0xFFFFFFFF));
    public final Setting.Colour muted = add(new Setting.Colour("muted", "Второй текст", false, 0xFF96969B));
    public final Setting.Num opacity =
            add(new Setting.Num("opacity", "Непрозрачность меню", 100.0, 55.0, 100.0, 5.0, "%"));

    /** Colours in the order of {@link #colours()}. */
    public record Preset(String name, int panel, int sidebar, int card, int field, int text, int muted, int a, int b) {}

    public static final List<Preset> PRESETS = List.of(
            new Preset(
                    "Arcanic",
                    0xFF0E1020,
                    0xFF13162B,
                    0xFF151831,
                    0xFF1C2040,
                    0xFFF3F5FF,
                    0xFF9AA3C7,
                    0xFF5B7BFF,
                    0xFF8B5CFF),
            new Preset(
                    "Обсидиан",
                    0xFF141417,
                    0xFF1B1B1F,
                    0xFF161619,
                    0xFF1A1A1E,
                    0xFFFFFFFF,
                    0xFF96969B,
                    0xFF859CFF,
                    0xFFB4C2FF),
            new Preset(
                    "Светлая",
                    0xFFF4F4F6,
                    0xFFEBEBEF,
                    0xFFFFFFFF,
                    0xFFE6E6EA,
                    0xFF15151A,
                    0xFF6A6A74,
                    0xFF7C3AED,
                    0xFFA78BFA),
            new Preset(
                    "Аметист",
                    0xFF120D1C,
                    0xFF16101F,
                    0xFF1A1326,
                    0xFF261C35,
                    0xFFF6F1FF,
                    0xFFA898C2,
                    0xFFA855F7,
                    0xFFE9D5FF),
            new Preset(
                    "Полночь",
                    0xFF0B1020,
                    0xFF0F1526,
                    0xFF111829,
                    0xFF1A243B,
                    0xFFF0F5FF,
                    0xFF93A3C2,
                    0xFF3B82F6,
                    0xFF93C5FD),
            new Preset(
                    "Закат",
                    0xFF170F0C,
                    0xFF1C130F,
                    0xFF1F1511,
                    0xFF2C1E18,
                    0xFFFFF5EE,
                    0xFFC2A190,
                    0xFFF97316,
                    0xFFFDBA74),
            new Preset(
                    "Изумруд",
                    0xFF0B1614,
                    0xFF0E1A17,
                    0xFF101E1B,
                    0xFF182C27,
                    0xFFEFFFF9,
                    0xFF8FB8AA,
                    0xFF10B981,
                    0xFF6EE7B7),
            new Preset(
                    "Сакура",
                    0xFF1A1016,
                    0xFF21141C,
                    0xFF24161F,
                    0xFF33212C,
                    0xFFFFF1F7,
                    0xFFD2A6BC,
                    0xFFF472B6,
                    0xFFFBCFE8),
            new Preset(
                    "Неон",
                    0xFF08080F,
                    0xFF0C0C1A,
                    0xFF0F0F22,
                    0xFF1A1A33,
                    0xFFF0FDFF,
                    0xFF9EC3D0,
                    0xFF22D3EE,
                    0xFFF472B6),
            new Preset(
                    "Матрица",
                    0xFF060C08,
                    0xFF081109,
                    0xFF0A140D,
                    0xFF132219,
                    0xFFE9FFF0,
                    0xFF8FBF9E,
                    0xFF22C55E,
                    0xFF86EFAC));

    public ThemeStudio() {
        super("theme_studio", "Студия тем", "Свои цвета меню и акцента", Category.HUD, true);
    }

    public List<Setting.Colour> colours() {
        return List.of(panel, sidebar, card, field, text, muted, accent, accent2);
    }

    public boolean active() {
        return custom.on();
    }

    public void apply(Preset p) {
        int[] v = {p.panel(), p.sidebar(), p.card(), p.field(), p.text(), p.muted(), p.a(), p.b()};
        List<Setting.Colour> cs = colours();
        Theme.Palette was = Theme.p();
        for (int i = 0; i < cs.size(); i++) {
            cs.get(i).setTheme(false);
            cs.get(i).set(v[i] | 0xFF000000);
        }
        custom.set(true);
        Theme.fadeFrom(was);
    }

    public boolean is(Preset p) {
        int[] v = {p.panel(), p.sidebar(), p.card(), p.field(), p.text(), p.muted(), p.a(), p.b()};
        List<Setting.Colour> cs = colours();
        for (int i = 0; i < cs.size(); i++) if ((cs.get(i).get() | 0xFF000000) != (v[i] | 0xFF000000)) return false;
        return true;
    }

    public void resetAll() {
        Theme.Palette was = Theme.p();
        resetSettings();
        custom.set(false);
        Theme.fadeFrom(was);
    }

    private static int opaque(Setting.Colour c) {
        return c.get() | 0xFF000000;
    }

    static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24
                | (int) (ar + (br - ar) * t) << 16
                | (int) (ag + (bg - ag) * t) << 8
                | (int) (ab + (bb - ab) * t);
    }

    private static float luminance(int c) {
        return ((c >> 16 & 255) * 0.2126F + (c >> 8 & 255) * 0.7152F + (c & 255) * 0.0722F) / 255.0F;
    }

    private int alpha(int c) {
        return Math.round(255.0F * (float) (opacity.get() / 100.0)) << 24 | c & 0xFFFFFF;
    }

    // ---- menu tokens

    public int panelBg() {
        return alpha(opaque(panel));
    }

    public int chrome() {
        return alpha(opaque(sidebar));
    }

    public int cardBg() {
        return opaque(card);
    }

    public int cardHi() {
        return mix(opaque(card), opaque(text), 0.06F);
    }

    public int fieldBg() {
        return opaque(field);
    }

    public int label() {
        return mix(opaque(muted), opaque(text), 0.72F);
    }

    public int sub() {
        return opaque(muted);
    }

    public int faint() {
        return mix(opaque(muted), opaque(panel), 0.38F);
    }

    public int edge(float k) {
        return mix(opaque(panel), opaque(text), k);
    }

    public boolean light() {
        return luminance(opaque(panel)) > 0.5F;
    }

    /** For {@link Theme#p()}. */
    public Theme.Palette palette() {
        int t = opaque(text);
        return new Theme.Palette(
                "Своя",
                alpha(opaque(panel)),
                alpha(opaque(sidebar)),
                opaque(card),
                opaque(field),
                0x1A000000 | t & 0xFFFFFF,
                t,
                opaque(muted),
                mix(opaque(muted), opaque(panel), 0.45F),
                opaque(accent),
                opaque(accent2),
                light(),
                0);
    }

    // ---- share code

    public String code() {
        JsonObject o = new JsonObject();
        for (Setting.Colour c : colours()) o.addProperty(c.key, Integer.toHexString(opaque(c) & 0xFFFFFF));
        o.addProperty("opacity", opacity.i());
        return CODE_PREFIX
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(o.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** False if the text is not a theme code. */
    public boolean load(String code) {
        if (code == null) return false;
        String s = code.strip();
        int at = s.indexOf(CODE_PREFIX);
        if (at < 0) return false;
        s = s.substring(at + CODE_PREFIX.length()).split("\\s")[0];
        try {
            JsonObject o = JsonParser.parseString(
                            new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Theme.Palette was = Theme.p();
            for (Setting.Colour c : colours()) {
                if (o.has(c.key)) {
                    c.setTheme(false);
                    c.set((int) Long.parseLong(o.get(c.key).getAsString(), 16) | 0xFF000000);
                }
            }
            if (o.has("opacity")) opacity.set(o.get("opacity").getAsDouble());
            custom.set(true);
            Theme.fadeFrom(was);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
