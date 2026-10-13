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
package net.arcanic.visuals.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Drawing in real screen pixels. */
public final class Px {
    private Px() {}

    public static final Identifier ATLAS = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/atlas.png");
    private static int atlasW = 1024, atlasH = 1024;
    private static final Map<Integer, int[]> DISC = new HashMap<>(), RING = new HashMap<>();
    private static int[] shadow, hgrad, vgrad;
    private static final Map<String, Integer> ICONS = new HashMap<>();
    public static final int MAX_R = 36;
    public static float fade = 1;

    private static int f(int c) {
        return fade >= 1 ? c : am(c, fade);
    }

    static {
        try (var in = Px.class.getResourceAsStream("/assets/arcanicvisuals/atlas.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (var e : o.getAsJsonObject("disc").entrySet()) {
                var a = e.getValue().getAsJsonArray();
                DISC.put(
                        Integer.parseInt(e.getKey()),
                        new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()});
            }
            for (var e : o.getAsJsonObject("ring").entrySet()) {
                var a = e.getValue().getAsJsonArray();
                RING.put(
                        Integer.parseInt(e.getKey()),
                        new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()});
            }
            var s = o.getAsJsonArray("shadow");
            shadow = new int[] {
                s.get(0).getAsInt(),
                s.get(1).getAsInt(),
                s.get(2).getAsInt(),
                s.get(3).getAsInt()
            };
            var h = o.getAsJsonArray("hgrad");
            hgrad = new int[] {h.get(0).getAsInt(), h.get(1).getAsInt()};
            var v = o.getAsJsonArray("vgrad");
            vgrad = new int[] {v.get(0).getAsInt(), v.get(1).getAsInt()};
            var size = o.getAsJsonArray("size");
            atlasW = size.get(0).getAsInt();
            atlasH = size.get(1).getAsInt();
        } catch (Exception e) {
            throw new IllegalStateException("arcanicvisuals atlas", e);
        }
        try (var in = Px.class.getResourceAsStream("/assets/arcanicvisuals/icons.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (var e : o.entrySet()) ICONS.put(e.getKey(), e.getValue().getAsInt());
        } catch (Exception e) {
            throw new IllegalStateException("arcanicvisuals icons", e);
        }
    }

    public static int guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static int width() {
        return Minecraft.getInstance().getWindow().getWidth();
    }

    public static int height() {
        return Minecraft.getInstance().getWindow().getHeight();
    }

    public static void begin(GuiGraphics g) {
        var w = Minecraft.getInstance().getWindow();
        g.pose().pushMatrix();
        g.pose()
                .scale(
                        w.getGuiScaledWidth() / (float) Math.max(1, w.getWidth()),
                        w.getGuiScaledHeight() / (float) Math.max(1, w.getHeight()));
    }

    public static void end(GuiGraphics g) {
        g.pose().popMatrix();
    }

    public static double px(double gui) {
        var w = Minecraft.getInstance().getWindow();
        return gui * w.getWidth() / (double) Math.max(1, w.getGuiScaledWidth());
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int c) {
        c = f(c);
        if (w > 0 && h > 0 && (c >>> 24) != 0) {
            if ((w <= 2 || h <= 2) && guiScale() > 1) g.nextStratum();
            g.fill(x, y, x + w, y + h, c);
        }
    }

    private static void edge(GuiGraphics g, int x, int y, int w, int h, int c) {
        c = f(c);
        if (w > 0 && h > 0 && (c >>> 24) != 0) g.fill(x, y, x + w, y + h, c);
    }

    private static void sprite(GuiGraphics g, int x, int y, int u, int v, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, u, v, w, h, w, h, atlasW, atlasH, f(c));
    }

    private static void stretched(GuiGraphics g, int x, int y, int w, int h, int u, int v, int uw, int vh, int c) {
        if (w <= 0 || h <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, u, v, w, h, uw, vh, atlasW, atlasH, f(c));
    }

    /** Anti-aliased rounded rectangle. */
    public static void round(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        r = Math.max(0, Math.min(Math.min(r, MAX_R), Math.min(w, h) / 2));
        if (r == 0) {
            rect(g, x, y, w, h, c);
            return;
        }
        int[] d = DISC.get(r);
        sprite(g, x, y, d[0], d[1], r, r, c);
        sprite(g, x + w - r, y, d[0] + r, d[1], r, r, c);
        sprite(g, x, y + h - r, d[0], d[1] + r, r, r, c);
        sprite(g, x + w - r, y + h - r, d[0] + r, d[1] + r, r, r, c);
        rect(g, x + r, y, w - 2 * r, r, c);
        rect(g, x + r, y + h - r, w - 2 * r, r, c);
        rect(g, x, y + r, w, h - 2 * r, c);
    }
    /** 1px anti-aliased rounded outline. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        if (guiScale() > 1) g.nextStratum();
        r = Math.max(1, Math.min(Math.min(r, MAX_R), Math.min(w, h) / 2));
        int[] d = RING.get(r);
        sprite(g, x, y, d[0], d[1], r, r, c);
        sprite(g, x + w - r, y, d[0] + r, d[1], r, r, c);
        sprite(g, x, y + h - r, d[0], d[1] + r, r, r, c);
        sprite(g, x + w - r, y + h - r, d[0] + r, d[1] + r, r, r, c);
        edge(g, x + r, y, w - 2 * r, 1, c);
        edge(g, x + r, y + h - 1, w - 2 * r, 1, c);
        edge(g, x, y + r, 1, h - 2 * r, c);
        edge(g, x + w - 1, y + r, 1, h - 2 * r, c);
    }

    public static void circle(GuiGraphics g, int cx, int cy, int r, int c) {
        if (r <= 0 || (c >>> 24) == 0) return;
        if (r > MAX_R) {
            round(g, cx - r, cy - r, r * 2, r * 2, r, c);
            return;
        }
        int[] d = DISC.get(r);
        sprite(g, cx - r, cy - r, d[0], d[1], r * 2, r * 2, c);
    }

    public static void ring(GuiGraphics g, int cx, int cy, int r, int c) {
        if (r <= 0 || (c >>> 24) == 0) return;
        if (r > MAX_R) {
            outline(g, cx - r, cy - r, r * 2, r * 2, r, c);
            return;
        }
        int[] d = RING.get(r);
        sprite(g, cx - r, cy - r, d[0], d[1], r * 2, r * 2, c);
    }

    /** Soft drop shadow around a box; {@code spread} grows the blurred area, {@code c} its colour and strength. */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int spread, int c) {
        int sx = shadow[0], sy = shadow[1], S = shadow[2], B = shadow[3];
        int inset = B - 16; // where the original box sat inside the shadow texture
        int k = Math.max(4, spread);
        int bx = x - k, by = y - k, bw = w + k * 2, bh = h + k * 2;
        int cb = Math.min(B, Math.min(bw, bh) / 2); // corner size on screen
        int ccb = cb;
        // corners
        stretched(g, bx, by, ccb, ccb, sx, sy, B, B, c);
        stretched(g, bx + bw - ccb, by, ccb, ccb, sx + S - B, sy, B, B, c);
        stretched(g, bx, by + bh - ccb, ccb, ccb, sx, sy + S - B, B, B, c);
        stretched(g, bx + bw - ccb, by + bh - ccb, ccb, ccb, sx + S - B, sy + S - B, B, B, c);
        // edges
        stretched(g, bx + ccb, by, bw - 2 * ccb, ccb, sx + B, sy, S - 2 * B, B, c);
        stretched(g, bx + ccb, by + bh - ccb, bw - 2 * ccb, ccb, sx + B, sy + S - B, S - 2 * B, B, c);
        stretched(g, bx, by + ccb, ccb, bh - 2 * ccb, sx, sy + B, B, S - 2 * B, c);
        stretched(g, bx + bw - ccb, by + ccb, ccb, bh - 2 * ccb, sx + S - B, sy + B, B, S - 2 * B, c);
        stretched(g, bx + ccb, by + ccb, bw - 2 * ccb, bh - 2 * ccb, sx + S / 2, sy + S / 2, 1, 1, c);
        int unused = inset;
    }

    public static void hgrad(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        if ((c1 >>> 24) == 255 && (c2 >>> 24) == 255) { // opaque: two layers through the alpha ramp
            rect(g, x, y, w, h, c1);
            stretched(g, x, y, w, h, hgrad[0], hgrad[1], 256, 4, c2);
            return;
        }
        // translucent fades: c1 through the reversed alpha ramp, c2 through the ramp (two sprites instead of strips)
        if ((c1 >>> 24) != 0) stretched(g, x, y, w, h, hgrad[0] + 256, hgrad[1], -256, 4, c1);
        if ((c2 >>> 24) != 0) stretched(g, x, y, w, h, hgrad[0], hgrad[1], 256, 4, c2);
    }

    public static void vgrad(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        rect(g, x, y, w, h, c1);
        stretched(g, x, y, w, h, vgrad[0], vgrad[1], 4, 256, c2);
    }
    /** Rounded pill filled with a horizontal gradient: the gradient is laid inside a rounded mask via end caps. */
    public static void pill(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        int r = Math.min(h / 2, w / 2);
        if (w <= 2 * r) {
            round(g, x, y, w, h, r, c1);
            return;
        }
        int[] d = DISC.get(Math.max(1, r));
        sprite(g, x, y, d[0], d[1], r, r, c1);
        sprite(g, x, y + h - r, d[0], d[1] + r, r, r, c1);
        rect(g, x, y + r, r, h - 2 * r, c1);
        sprite(g, x + w - r, y, d[0] + r, d[1], r, r, c2);
        sprite(g, x + w - r, y + h - r, d[0] + r, d[1] + r, r, r, c2);
        rect(g, x + w - r, y + r, r, h - 2 * r, c2);
        hgrad(g, x + r, y, w - 2 * r, h, c1, c2);
    }

    /** Anti-aliased arc from start (radians, 0 = right, clockwise) over sweep, blending c1 into c2 along it. */
    public static void arc(
            GuiGraphics g, float cx, float cy, float r, int thickness, double start, double sweep, int c1, int c2) {
        if (Math.abs(sweep) < 1e-3 || thickness <= 0) return;
        int dr = Math.max(1, Math.round(thickness / 2f));
        int[] d = DISC.get(Math.min(MAX_R, dr));
        int steps = Math.max(2, (int) Math.ceil(Math.abs(sweep) * r / Math.max(.8f, dr * .45f)));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps, a = start + sweep * t;
            int x = Math.round((float) (cx + Math.cos(a) * r)) - dr,
                    y = Math.round((float) (cy + Math.sin(a) * r)) - dr;
            sprite(g, x, y, d[0], d[1], dr * 2, dr * 2, mix(c1, c2, (float) t));
        }
    }

    public static final Identifier GLOW = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/glow.png");

    public static void glow(GuiGraphics g, int x, int y, int w, int h, int c) {
        c = f(c);
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, GLOW, x, y, 0, 0, w, h, 256, 256, 256, 256, c);
    }

    // exact sizes)
    public static final Identifier CHAMFER =
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/chamfer.png");
    private static final int CH_M = 48, CH_W = 1176, CH_H = 288;
    public static final int TL = 1, TR = 2, BR = 4, BL = 8;

    private static void cham(GuiGraphics g, int x, int y, int n, int row, int c) {
        if (n <= 0) return;
        n = Math.min(n, CH_M);
        g.blit(RenderPipelines.GUI_TEXTURED, CHAMFER, x, y, n * (n - 1) / 2, row * CH_M, n, n, n, n, CH_W, CH_H, f(c));
    }

    public static void wedge(GuiGraphics g, int x, int y, int n, int which, int c) {
        if ((c >>> 24) != 0) cham(g, x, y, n, which, c);
    }
    /** A rectangle whose corners in {@code corners} (TL | TR | BR | BL) are cut at 45 degrees by {@code cut} pixels. */
    public static void chamfer(GuiGraphics g, int x, int y, int w, int h, int cut, int corners, int c) {
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        cut = Math.max(0, Math.min(Math.min(cut, CH_M), Math.min(w, h) / 2));
        if (cut == 0 || corners == 0) {
            rect(g, x, y, w, h, c);
            return;
        }
        rect(g, x + cut, y, w - 2 * cut, cut, c);
        rect(g, x, y + cut, w, h - 2 * cut, c);
        rect(g, x + cut, y + h - cut, w - 2 * cut, cut, c);
        if ((corners & TL) != 0) cham(g, x, y, cut, 0, c);
        else rect(g, x, y, cut, cut, c);
        if ((corners & TR) != 0) cham(g, x + w - cut, y, cut, 1, c);
        else rect(g, x + w - cut, y, cut, cut, c);
        if ((corners & BR) != 0) cham(g, x + w - cut, y + h - cut, cut, 2, c);
        else rect(g, x + w - cut, y + h - cut, cut, cut, c);
        if ((corners & BL) != 0) cham(g, x, y + h - cut, cut, 3, c);
        else rect(g, x, y + h - cut, cut, cut, c);
    }

    public static void seg(GuiGraphics g, float x0, float y0, float x1, float y1, float thick, int c) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
        c = f(c);
        if (len < .5f || (c >>> 24) == 0) return;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().rotate((float) Math.atan2(dy, dx));
        int t = Math.max(1, Math.round(thick));
        g.fill(0, -t / 2, Math.round(len), -t / 2 + t, c);
        g.pose().popMatrix();
    }

    public enum Weight {
        MEDIUM("medium"),
        SEMIBOLD("semibold"),
        BOLD("bold");
        final String tag;

        Weight(String t) {
            tag = t;
        }
    }

    private static final Map<String, Style> STYLES = new HashMap<>();

    private static Style style(String tag, int px) {
        return STYLES.computeIfAbsent(
                tag + "_" + px,
                k -> Style.EMPTY.withFont(
                        new FontDescription.Resource(Identifier.fromNamespaceAndPath("arcanicvisuals", k))));
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int clampPx(int px) {
        return Math.max(8, Math.min(32, px));
    }

    public static Component text(String s, Weight w, int px) {
        return Component.literal(s).withStyle(style(w.tag, clampPx(px)));
    }

    public static int textWidth(String s, Weight w, int px) {
        return MsdfText.ready() ? Math.round(MsdfText.width(s, w, clampMsdf(px))) : ttfWidth(s, w, px);
    }

    static int ttfWidth(String s, Weight w, int px) {
        return font().width(text(s, w, px));
    }

    static void ttfDraw(GuiGraphics g, String s, int x, int baseline, Weight w, int px, int c) {
        g.drawString(font(), text(s, w, px), x, baseline - 7, c, false);
    }

    private static int clampMsdf(int px) {
        return Math.max(6, Math.min(96, px));
    }

    public static void text(GuiGraphics g, String s, int x, int y, Weight w, int px, int c) {
        if (MsdfText.ready()) MsdfText.draw(g, s, x, textY(y, px) + 7, w, clampMsdf(px), f(c));
        else g.drawString(font(), text(s, w, px), x, textY(y, px), f(c), false);
    }

    public static void textRight(GuiGraphics g, String s, int right, int y, Weight w, int px, int c) {
        text(g, s, right - textWidth(s, w, px), y, w, px, c);
    }

    public static void textCenter(GuiGraphics g, String s, int cx, int y, Weight w, int px, int c) {
        text(g, s, cx - textWidth(s, w, px) / 2, y, w, px, c);
    }
    /*
     * Measured metrics (see CalibScreen): TTF glyphs always sit on a baseline 7 units below the draw y, whatever the
     * size; cap height is ~0.735 px. Icons (Lucide) end ~6 units below y and are ~0.82 px tall. Text/icons drawn at
     * y are therefore centred inside the box [y, y + px].
     */
    public static int capHeight(int px) {
        return Math.round(px * .735f);
    }

    public static int textY(int y, int px) {
        return Math.round(y + px / 2f + capHeight(px) / 2f - 7);
    }

    public static int iconY(int y, int px) {
        int ih = Math.round(px * .82f), bottom = 6 - px / 17;
        return Math.round(y + px / 2f + ih / 2f - bottom);
    }

    public static int baseline(int px) {
        return textY(0, px);
    }

    public static void textShadow(GuiGraphics g, String s, int x, int y, Weight w, int px, int c) {
        int sh = Math.max(1, px / 12);
        int shadow = f(am(0xFF000000, ((c >>> 24) / 255f) * .55f));
        if (MsdfText.ready()) {
            MsdfText.draw(g, s, x + sh, textY(y, px) + 7 + sh, w, clampMsdf(px), shadow);
            MsdfText.draw(g, s, x, textY(y, px) + 7, w, clampMsdf(px), f(c));
            return;
        }
        g.drawString(font(), text(s, w, px), x + sh, y + baseline(px) + sh, shadow, false);
        g.drawString(font(), text(s, w, px), x, textY(y, px), f(c), false);
    }

    public static String ellipsize(String s, Weight w, int px, int max) {
        if (textWidth(s, w, px) <= max) return s;
        while (s.length() > 1 && textWidth(s + "…", w, px) > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    public static Component icon(String name, int px) {
        Integer cp = ICONS.get(name);
        return Component.literal(cp == null ? "?" : new String(Character.toChars(cp)))
                .withStyle(style("icons", Math.max(8, Math.min(40, px))));
    }

    public static void icon(GuiGraphics g, String name, int x, int y, int px, int c) {
        int kit = kitCell(name);
        if (kit >= 0) {
            kitIcon(g, kit, x, y, px, c);
            return;
        }
        if (kit == -2) return; // the tint layer of a glyph that is now a solid icon
        g.drawString(font(), icon(name, px), x, iconY(y, px), f(c), false);
    }

    public static boolean hasIcon(String name) {
        return ICONS.containsKey(name) || kitCell(name) >= 0;
    }

    // ---- the icon set (solid glyphs, baked per pixel size by tools/kit_icons.py)
    private static final Map<String, Integer> KIT = new HashMap<>();
    private static int kitCols = 16, kitRows = 8;
    private static final Map<String, String> ICON_ALIAS = new HashMap<>();

    static {
        try (var in = Px.class.getResourceAsStream("/assets/arcanicvisuals/kit_icons.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                kitCols = o.get("cols").getAsInt();
                kitRows = o.get("rows").getAsInt();
                for (var e : o.getAsJsonObject("icons").entrySet())
                    KIT.put(e.getKey(), e.getValue().getAsInt());
            }
        } catch (Exception e) {
            System.err.println("[ArcanicVisuals] kit icons: " + e);
        }
        String[][] alias = {
            {"combat", "sword"},
            {"players", "users"},
            {"world", "mountains"},
            {"effects", "sparkles"},
            {"hands", "tap"},
            {"cosmetics", "wizard-hat"},
            {"hud", "monitor"},
            {"search", "search"},
            {"presets", "bookmark"},
            {"move", "expand-h"},
            {"settings", "sliders"},
            {"sliders", "sliders"},
            {"sun", "sun"},
            {"moon", "moon"},
            {"x", "x"},
            {"check", "check"},
            {"chev-down", "chev-down"},
            {"chev-right", "chev-right"},
            {"chev-left", "chev-left"},
            {"plus", "plus"},
            {"arrow-right", "arrow-right"},
            {"reset", "undo"},
            {"key", "key"},
            {"save", "save"},
            {"trash", "trash"},
            {"knife", "sword"},
            {"sparkle", "sparkles"},
            {"modules", "apps"},
            {"user", "user-circle"},
            {"server", "server"},
            {"bolt", "bolt"},
            {"ping", "router"},
            {"clock", "pie"},
            {"bind", "keyboard"},
            {"keyboard", "keyboard"},
            // plain (Lucide) names used across the menu and HUD
            {"chevron-down", "chev-down"},
            {"chevron-right", "chev-right"},
            {"chevron-up", "chev-up"},
            {"sliders-horizontal", "sliders"},
            {"pipette", "pipette"},
            {"layout-grid", "apps"},
            {"layout-dashboard", "monitor"},
            {"rotate-ccw", "undo"},
            {"sparkles", "sparkles"},
            {"eye", "eye"},
            {"list", "list"},
            {"zap", "bolt"},
            {"shield", "shield"},
            {"swords", "sword"},
            {"sword", "sword"},
            {"palette", "swatch"},
            {"swatch-book", "swatch"},
            {"paintbrush", "roller"},
            {"brush", "roller"},
            {"house", "house"},
            {"download", "download"},
            {"trash-2", "trash"},
            {"bookmark", "bookmark"},
            {"flask-conical", "flask"},
            {"hand", "tap"},
            {"badge-check", "award"},
            {"medal", "award"},
            {"trophy", "award"},
            {"layers", "server"},
            {"timer", "pie"},
            {"wand-sparkles", "wizard-hat"},
            {"shirt", "wizard-hat"},
            {"mouse-pointer-click", "click"},
            {"user-round", "users"},
            {"compass", "mountains"},
            {"monitor", "monitor"},
            {"type", "type"},
            {"info", "alert-circle"},
        };
        for (String[] a : alias) {
            if (!KIT.containsKey(a[1])) continue;
            ICON_ALIAS.put(a[0], a[1]);
            if (!a[0].contains("-") || a[0].startsWith("chev") || a[0].endsWith("-s")) {
                // our own glyph family: "a.name", its small "-s" cut and their tint layers
                ICON_ALIAS.put("a." + a[0], a[1]);
                ICON_ALIAS.put("a." + a[0] + "-s", a[1]);
            }
        }
    }

    private static int kitCell(String name) {
        if (KIT.isEmpty() || name == null) return -1;
        if (name.endsWith("~")) return ICON_ALIAS.containsKey(name.substring(0, name.length() - 1)) ? -2 : -1;
        if (name.startsWith("w.")) return KIT.getOrDefault(name.substring(2), -1);
        String n = ICON_ALIAS.get(name);
        if (n == null && name.endsWith("-s")) n = ICON_ALIAS.get(name.substring(0, name.length() - 2));
        return n == null ? -1 : KIT.getOrDefault(n, -1);
    }

    private static void kitIcon(GuiGraphics g, int cell, int x, int y, int px, int c) {
        px = Math.max(8, Math.min(40, px));
        c = f(c);
        if ((c >>> 24) == 0) return;
        Identifier tex = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/kit/icons_" + px + ".png");
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                tex,
                x,
                y,
                (cell % kitCols) * px,
                (cell / kitCols) * px,
                px,
                px,
                px,
                px,
                kitCols * px,
                kitRows * px,
                c);
    }

    public static int a(int c, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (c & 0xFFFFFF);
    }

    public static int am(int c, float k) {
        return a(c, (int) ((c >>> 24) * Math.max(0, Math.min(1, k))));
    }

    public static int mix(int a, int b, float t) {
        return Draw.mix(a, b, t);
    }
}
