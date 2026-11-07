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
package net.arcanic.visuals.fx.lyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * The EvoVisuals lyrics faces (their own MTSDF atlases, {@code lyrics_medium} and {@code lyrics_bold}), set exactly the
 * way Evo's MSDF font sets a word: kerning between pairs, a twentieth of the size of extra tracking after every glyph,
 * a space of 0.35 of the size, small capitals read as capitals and a colour code after {@code §} skipped. Like Evo, a
 * glyph's quad starts right at the pen and is as wide as its ink box.
 */
public final class LyricsFont {
    public static final LyricsFont MEDIUM = new LyricsFont("lyrics_medium");
    public static final LyricsFont BOLD = new LyricsFont("lyrics_bold");

    private static final float TRACKING = 0.05F;
    private static final float SPACE = 0.35F;
    private static final int SECTION = '§';

    /** Advance, ink box (em: width, height, top above the baseline) and atlas box (0..1, v down) of one glyph. */
    record Glyph(float advance, float width, float height, float top, float u0, float v0, float u1, float v1) {}

    /** Receives one glyph quad in the word's plane units (y down) with its atlas box. */
    interface Sink {
        void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1);
    }

    public final Identifier atlas;
    private final String name;
    /** Glyphs by code point (the faces stop below U+0500), so a lookup is an array read with no boxing. */
    private Glyph[] glyphs;
    /** Kerning by (left << 32 | right). */
    private Long2FloatOpenHashMap kerning;

    private float range = 10.0F;

    private LyricsFont(String name) {
        this.name = name;
        this.atlas = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/font/msdf/" + name + ".png");
    }

    /** The atlas distance range in texels, for the edge maths in the shader. */
    public float range() {
        load();
        return range;
    }

    private void load() {
        if (glyphs != null) return;
        Map<Integer, Glyph> map = new HashMap<>();
        Long2FloatOpenHashMap kern = new Long2FloatOpenHashMap();
        try (var in = LyricsFont.class.getResourceAsStream("/assets/arcanicvisuals/msdf/" + name + ".json")) {
            var root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var a = root.getAsJsonObject("atlas");
            if (a.has("distanceRange")) range = a.get("distanceRange").getAsFloat();
            float w = a.get("width").getAsFloat(), h = a.get("height").getAsFloat();
            for (var e : root.getAsJsonArray("glyphs")) {
                JsonObject o = e.getAsJsonObject();
                float adv = o.get("advance").getAsFloat();
                float u0 = 0, v0 = 0, u1 = 0, v1 = 0, gw = 0, gh = 0, top = 0;
                if (o.has("atlasBounds")) {
                    JsonObject q = o.getAsJsonObject("atlasBounds");
                    u0 = q.get("left").getAsFloat() / w;
                    u1 = q.get("right").getAsFloat() / w;
                    v0 = 1 - q.get("top").getAsFloat() / h;
                    v1 = 1 - q.get("bottom").getAsFloat() / h;
                }
                if (o.has("planeBounds")) {
                    JsonObject p = o.getAsJsonObject("planeBounds");
                    gw = p.get("right").getAsFloat() - p.get("left").getAsFloat();
                    gh = p.get("top").getAsFloat() - p.get("bottom").getAsFloat();
                    top = p.get("top").getAsFloat();
                }
                map.put(o.get("unicode").getAsInt(), new Glyph(adv, gw, gh, top, u0, v0, u1, v1));
            }
            if (root.has("kerning")) {
                for (var e : root.getAsJsonArray("kerning")) {
                    JsonObject k = e.getAsJsonObject();
                    kern.put(
                            (long) k.get("unicode1").getAsInt() << 32
                                    | k.get("unicode2").getAsInt() & 0xFFFFFFFFL,
                            k.get("advance").getAsFloat());
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Lyrics font " + name, ex);
        }
        int max = 0;
        for (int cp : map.keySet()) max = Math.max(max, cp);
        Glyph[] table = new Glyph[max + 1];
        map.forEach((cp, g) -> table[cp] = g);
        kerning = kern;
        glyphs = table;
    }

    private Glyph glyph(int cp) {
        return cp >= 0 && cp < glyphs.length ? glyphs[cp] : null;
    }

    private float kern(int left, int right) {
        return kerning.isEmpty() || left < 0 ? 0 : kerning.get((long) left << 32 | right & 0xFFFFFFFFL);
    }

    /** Width of {@code s} at {@code size}, kerning and tracking included. */
    public float width(String s, float size) {
        load();
        float w = 0;
        int prev = -1;
        boolean code = false;
        for (int i = 0; i < s.length(); i++) {
            int cp = mapped(s.charAt(i));
            if (code) {
                code = false;
                continue;
            }
            if (cp == SECTION) {
                code = true;
                continue;
            }
            if (cp == ' ') {
                w += size * SPACE;
                prev = ' ';
                continue;
            }
            Glyph g = glyph(cp);
            if (g == null) continue;
            w += kern(prev, cp) * size;
            w += g.advance() * size + TRACKING * size;
            prev = cp;
        }
        return w;
    }

    /** Lays the glyphs of {@code s} out from pen x with the baseline at y, both in plane units. */
    void layout(String s, float size, float x, float baseline, Sink out) {
        load();
        float pen = x;
        int prev = -1;
        boolean code = false;
        for (int i = 0; i < s.length(); i++) {
            int cp = mapped(s.charAt(i));
            if (code) {
                code = false;
                continue;
            }
            if (cp == SECTION) {
                code = true;
                continue;
            }
            if (cp == ' ') {
                pen += size * SPACE;
                prev = ' ';
                continue;
            }
            Glyph g = glyph(cp);
            if (g == null) continue;
            pen += kern(prev, cp) * size;
            if (g.width() > 0 && g.height() > 0) {
                float y0 = baseline - g.top() * size;
                out.quad(pen, y0, pen + g.width() * size, y0 + g.height() * size, g.u0(), g.v0(), g.u1(), g.v1());
            }
            pen += g.advance() * size + TRACKING * size;
            prev = cp;
        }
    }

    /** Evo's character folding: small capitals to capitals, the small capital A to the Cyrillic one. */
    private static int mapped(char c) {
        return switch (c) {
            case 'ǫ', 'ʠ' -> 'Q';
            case 'ɢ' -> 'G';
            case 'ɪ' -> 'I';
            case 'ɴ' -> 'N';
            case 'ʀ' -> 'R';
            case 'ʏ' -> 'Y';
            case 'ʙ' -> 'B';
            case 'ʜ' -> 'H';
            case 'ʟ' -> 'L';
            case 'ᴀ' -> 'А';
            case 'ᴄ' -> 'C';
            case 'ᴅ' -> 'D';
            case 'ᴇ' -> 'E';
            case 'ᴊ' -> 'J';
            case 'ᴋ' -> 'K';
            case 'ᴍ' -> 'M';
            case 'ᴏ' -> 'O';
            case 'ᴘ' -> 'P';
            case 'ᴛ' -> 'T';
            case 'ᴜ' -> 'U';
            case 'ᴠ' -> 'V';
            case 'ᴡ' -> 'W';
            case 'ᴢ' -> 'Z';
            case 'ꜰ' -> 'F';
            case 'ꜱ' -> 'S';
            default -> c;
        };
    }
}
