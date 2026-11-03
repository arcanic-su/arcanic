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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.Window;
import java.io.InputStream;
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
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class Px {
    public static final Identifier ATLAS = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/atlas.png");
    private static int atlasW = 1024;
    private static int atlasH = 1024;
    private static final Map<Integer, int[]> DISC = new HashMap<>();
    private static final Map<Integer, int[]> RING = new HashMap<>();
    private static int[] shadow;
    private static int[] hgrad;
    private static int[] vgrad;
    private static final Map<String, Integer> ICONS = new HashMap<>();
    public static final int MAX_R = 36;
    public static float fade = 1.0F;
    public static final Identifier GLOW;
    public static final Identifier CHAMFER;
    private static final int CH_M = 48;
    private static final int CH_W = 1176;
    private static final int CH_H = 288;
    public static final int TL = 1;
    public static final int TR = 2;
    public static final int BR = 4;
    public static final int BL = 8;
    private static final Map<String, Style> STYLES;
    private static final Map<String, Integer> KIT;
    private static int kitCols;
    private static int kitRows;
    private static final Map<String, String> ICON_ALIAS;

    private Px() {}

    private static int f(int c) {
        return fade >= 1.0F ? c : am(c, fade);
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
        Window w = Minecraft.getInstance().getWindow();
        g.pose().pushMatrix();
        g.pose()
                .scale(
                        (float) w.getGuiScaledWidth() / (float) Math.max(1, w.getWidth()),
                        (float) w.getGuiScaledHeight() / (float) Math.max(1, w.getHeight()));
    }

    public static void end(GuiGraphics g) {
        g.pose().popMatrix();
    }

    public static double px(double gui) {
        Window w = Minecraft.getInstance().getWindow();
        return gui * (double) w.getWidth() / (double) Math.max(1, w.getGuiScaledWidth());
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int c) {
        c = f(c);
        if (w > 0 && h > 0 && c >>> 24 != 0) {
            if ((w <= 2 || h <= 2) && guiScale() > 1) {
                g.nextStratum();
            }

            g.fill(x, y, x + w, y + h, c);
        }
    }

    private static void edge(GuiGraphics g, int x, int y, int w, int h, int c) {
        c = f(c);
        if (w > 0 && h > 0 && c >>> 24 != 0) {
            g.fill(x, y, x + w, y + h, c);
        }
    }

    private static void sprite(GuiGraphics g, int x, int y, int u, int v, int w, int h, int c) {
        if (w > 0 && h > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, (float) u, (float) v, w, h, w, h, atlasW, atlasH, f(c));
        }
    }

    private static void stretched(GuiGraphics g, int x, int y, int w, int h, int u, int v, int uw, int vh, int c) {
        if (w > 0 && h > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, (float) u, (float) v, w, h, uw, vh, atlasW, atlasH, f(c));
        }
    }

    public static void round(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        if (w > 0 && h > 0 && c >>> 24 != 0) {
            r = Math.max(0, Math.min(Math.min(r, 36), Math.min(w, h) / 2));
            if (r == 0) {
                rect(g, x, y, w, h, c);
            } else {
                int[] d = (int[]) DISC.get(r);
                sprite(g, x, y, d[0], d[1], r, r, c);
                sprite(g, x + w - r, y, d[0] + r, d[1], r, r, c);
                sprite(g, x, y + h - r, d[0], d[1] + r, r, r, c);
                sprite(g, x + w - r, y + h - r, d[0] + r, d[1] + r, r, r, c);
                rect(g, x + r, y, w - 2 * r, r, c);
                rect(g, x + r, y + h - r, w - 2 * r, r, c);
                rect(g, x, y + r, w, h - 2 * r, c);
            }
        }
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        if (w > 0 && h > 0 && c >>> 24 != 0) {
            if (guiScale() > 1) {
                g.nextStratum();
            }

            r = Math.max(1, Math.min(Math.min(r, 36), Math.min(w, h) / 2));
            int[] d = (int[]) RING.get(r);
            sprite(g, x, y, d[0], d[1], r, r, c);
            sprite(g, x + w - r, y, d[0] + r, d[1], r, r, c);
            sprite(g, x, y + h - r, d[0], d[1] + r, r, r, c);
            sprite(g, x + w - r, y + h - r, d[0] + r, d[1] + r, r, r, c);
            edge(g, x + r, y, w - 2 * r, 1, c);
            edge(g, x + r, y + h - 1, w - 2 * r, 1, c);
            edge(g, x, y + r, 1, h - 2 * r, c);
            edge(g, x + w - 1, y + r, 1, h - 2 * r, c);
        }
    }

    public static void circle(GuiGraphics g, int cx, int cy, int r, int c) {
        if (r > 0 && c >>> 24 != 0) {
            if (r > 36) {
                round(g, cx - r, cy - r, r * 2, r * 2, r, c);
            } else {
                int[] d = (int[]) DISC.get(r);
                sprite(g, cx - r, cy - r, d[0], d[1], r * 2, r * 2, c);
            }
        }
    }

    public static void ring(GuiGraphics g, int cx, int cy, int r, int c) {
        if (r > 0 && c >>> 24 != 0) {
            if (r > 36) {
                outline(g, cx - r, cy - r, r * 2, r * 2, r, c);
            } else {
                int[] d = (int[]) RING.get(r);
                sprite(g, cx - r, cy - r, d[0], d[1], r * 2, r * 2, c);
            }
        }
    }

    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int spread, int c) {
        int sx = shadow[0];
        int sy = shadow[1];
        int S = shadow[2];
        int B = shadow[3];
        int inset = B - 16;
        int k = Math.max(4, spread);
        int bx = x - k;
        int by = y - k;
        int bw = w + k * 2;
        int bh = h + k * 2;
        int cb = Math.min(B, Math.min(bw, bh) / 2);
        stretched(g, bx, by, cb, cb, sx, sy, B, B, c);
        stretched(g, bx + bw - cb, by, cb, cb, sx + S - B, sy, B, B, c);
        stretched(g, bx, by + bh - cb, cb, cb, sx, sy + S - B, B, B, c);
        stretched(g, bx + bw - cb, by + bh - cb, cb, cb, sx + S - B, sy + S - B, B, B, c);
        stretched(g, bx + cb, by, bw - 2 * cb, cb, sx + B, sy, S - 2 * B, B, c);
        stretched(g, bx + cb, by + bh - cb, bw - 2 * cb, cb, sx + B, sy + S - B, S - 2 * B, B, c);
        stretched(g, bx, by + cb, cb, bh - 2 * cb, sx, sy + B, B, S - 2 * B, c);
        stretched(g, bx + bw - cb, by + cb, cb, bh - 2 * cb, sx + S - B, sy + B, B, S - 2 * B, c);
        stretched(g, bx + cb, by + cb, bw - 2 * cb, bh - 2 * cb, sx + S / 2, sy + S / 2, 1, 1, c);
    }

    public static void hgrad(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        if (w > 0 && h > 0) {
            if (c1 >>> 24 == 255 && c2 >>> 24 == 255) {
                rect(g, x, y, w, h, c1);
                stretched(g, x, y, w, h, hgrad[0], hgrad[1], 256, 4, c2);
            } else {
                if (c1 >>> 24 != 0) {
                    stretched(g, x, y, w, h, hgrad[0] + 256, hgrad[1], -256, 4, c1);
                }

                if (c2 >>> 24 != 0) {
                    stretched(g, x, y, w, h, hgrad[0], hgrad[1], 256, 4, c2);
                }
            }
        }
    }

    public static void vgrad(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        if (w > 0 && h > 0) {
            if (c1 >>> 24 == 255 && c2 >>> 24 == 255) {
                rect(g, x, y, w, h, c1);
                stretched(g, x, y, w, h, vgrad[0], vgrad[1], 4, 256, c2);
            } else {
                if (c1 >>> 24 != 0) {
                    stretched(g, x, y, w, h, vgrad[0], vgrad[1] + 256, 4, -256, c1);
                }

                if (c2 >>> 24 != 0) {
                    stretched(g, x, y, w, h, vgrad[0], vgrad[1], 4, 256, c2);
                }
            }
        }
    }

    public static void pill(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        int r = Math.min(h / 2, w / 2);
        if (w <= 2 * r) {
            round(g, x, y, w, h, r, c1);
        } else {
            int[] d = (int[]) DISC.get(Math.max(1, r));
            sprite(g, x, y, d[0], d[1], r, r, c1);
            sprite(g, x, y + h - r, d[0], d[1] + r, r, r, c1);
            rect(g, x, y + r, r, h - 2 * r, c1);
            sprite(g, x + w - r, y, d[0] + r, d[1], r, r, c2);
            sprite(g, x + w - r, y + h - r, d[0] + r, d[1] + r, r, r, c2);
            rect(g, x + w - r, y + r, r, h - 2 * r, c2);
            hgrad(g, x + r, y, w - 2 * r, h, c1, c2);
        }
    }

    public static void arc(
            GuiGraphics g, float cx, float cy, float r, int thickness, double start, double sweep, int c1, int c2) {
        if (!(Math.abs(sweep) < 0.001) && thickness > 0) {
            int dr = Math.max(1, Math.round((float) thickness / 2.0F));
            int[] d = (int[]) DISC.get(Math.min(36, dr));
            int steps = Math.max(
                    2, (int) Math.ceil(Math.abs(sweep) * (double) r / (double) Math.max(0.8F, (float) dr * 0.45F)));

            for (int i = 0; i <= steps; ++i) {
                double t = (double) i / (double) steps;
                double a = start + sweep * t;
                int x = Math.round((float) ((double) cx + Math.cos(a) * (double) r)) - dr;
                int y = Math.round((float) ((double) cy + Math.sin(a) * (double) r)) - dr;
                sprite(g, x, y, d[0], d[1], dr * 2, dr * 2, mix(c1, c2, (float) t));
            }
        }
    }

    public static void glow(GuiGraphics g, int x, int y, int w, int h, int c) {
        c = f(c);
        if (w > 0 && h > 0 && c >>> 24 != 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, GLOW, x, y, 0.0F, 0.0F, w, h, 256, 256, 256, 256, c);
        }
    }

    private static void cham(GuiGraphics g, int x, int y, int n, int row, int c) {
        if (n > 0) {
            n = Math.min(n, 48);
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    CHAMFER,
                    x,
                    y,
                    (float) (n * (n - 1) / 2),
                    (float) (row * 48),
                    n,
                    n,
                    n,
                    n,
                    1176,
                    288,
                    f(c));
        }
    }

    public static void wedge(GuiGraphics g, int x, int y, int n, int which, int c) {
        if (c >>> 24 != 0) {
            cham(g, x, y, n, which, c);
        }
    }

    public static void chamfer(GuiGraphics g, int x, int y, int w, int h, int cut, int corners, int c) {
        if (w > 0 && h > 0 && c >>> 24 != 0) {
            cut = Math.max(0, Math.min(Math.min(cut, 48), Math.min(w, h) / 2));
            if (cut != 0 && corners != 0) {
                rect(g, x + cut, y, w - 2 * cut, cut, c);
                rect(g, x, y + cut, w, h - 2 * cut, c);
                rect(g, x + cut, y + h - cut, w - 2 * cut, cut, c);
                if ((corners & 1) != 0) {
                    cham(g, x, y, cut, 0, c);
                } else {
                    rect(g, x, y, cut, cut, c);
                }

                if ((corners & 2) != 0) {
                    cham(g, x + w - cut, y, cut, 1, c);
                } else {
                    rect(g, x + w - cut, y, cut, cut, c);
                }

                if ((corners & 4) != 0) {
                    cham(g, x + w - cut, y + h - cut, cut, 2, c);
                } else {
                    rect(g, x + w - cut, y + h - cut, cut, cut, c);
                }

                if ((corners & 8) != 0) {
                    cham(g, x, y + h - cut, cut, 3, c);
                } else {
                    rect(g, x, y + h - cut, cut, cut, c);
                }

            } else {
                rect(g, x, y, w, h, c);
            }
        }
    }

    public static void seg(GuiGraphics g, float x0, float y0, float x1, float y1, float thick, int c) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot((double) dx, (double) dy);
        c = f(c);
        if (!(len < 0.5F) && c >>> 24 != 0) {
            g.pose().pushMatrix();
            g.pose().translate(x0, y0);
            g.pose().rotate((float) Math.atan2((double) dy, (double) dx));
            int t = Math.max(1, Math.round(thick));
            g.fill(0, -t / 2, Math.round(len), -t / 2 + t, c);
            g.pose().popMatrix();
        }
    }

    private static Style style(String tag, int px) {
        return (Style) STYLES.computeIfAbsent(
                tag + "_" + px,
                (k) -> Style.EMPTY.withFont(
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
        return font().width((FormattedText) text(s, w, px));
    }

    static void ttfDraw(GuiGraphics g, String s, int x, int baseline, Weight w, int px, int c) {
        g.drawString(font(), text(s, w, px), x, baseline - 7, c, false);
    }

    private static int clampMsdf(int px) {
        return Math.max(6, Math.min(96, px));
    }

    public static void text(GuiGraphics g, String s, int x, int y, Weight w, int px, int c) {
        if (MsdfText.ready()) {
            MsdfText.draw(g, s, x, textY(y, px) + 7, w, clampMsdf(px), f(c));
        } else {
            g.drawString(font(), text(s, w, px), x, textY(y, px), f(c), false);
        }
    }

    public static void textRight(GuiGraphics g, String s, int right, int y, Weight w, int px, int c) {
        text(g, s, right - textWidth(s, w, px), y, w, px, c);
    }

    public static void textCenter(GuiGraphics g, String s, int cx, int y, Weight w, int px, int c) {
        text(g, s, cx - textWidth(s, w, px) / 2, y, w, px, c);
    }

    public static int capHeight(int px) {
        return Math.round((float) px * 0.735F);
    }

    public static int textY(int y, int px) {
        return Math.round((float) y + (float) px / 2.0F + (float) capHeight(px) / 2.0F - 7.0F);
    }

    public static int iconY(int y, int px) {
        int ih = Math.round((float) px * 0.82F);
        int bottom = 6 - px / 17;
        return Math.round((float) y + (float) px / 2.0F + (float) ih / 2.0F - (float) bottom);
    }

    public static int baseline(int px) {
        return textY(0, px);
    }

    public static void textShadow(GuiGraphics g, String s, int x, int y, Weight w, int px, int c) {
        int sh = Math.max(1, px / 12);
        int shadow = f(am(0xFF000000, (float) (c >>> 24) / 255.0F * 0.55F));
        if (MsdfText.ready()) {
            MsdfText.draw(g, s, x + sh, textY(y, px) + 7 + sh, w, clampMsdf(px), shadow);
            MsdfText.draw(g, s, x, textY(y, px) + 7, w, clampMsdf(px), f(c));
        } else {
            g.drawString(font(), text(s, w, px), x + sh, y + baseline(px) + sh, shadow, false);
            g.drawString(font(), text(s, w, px), x, textY(y, px), f(c), false);
        }
    }

    public static String ellipsize(String s, Weight w, int px, int max) {
        if (textWidth(s, w, px) <= max) {
            return s;
        } else {
            while (s.length() > 1 && textWidth(s + "…", w, px) > max) {
                s = s.substring(0, s.length() - 1);
            }

            return s + "…";
        }
    }

    public static Component icon(String name, int px) {
        Integer cp = (Integer) ICONS.get(name);
        return Component.literal(cp == null ? "?" : new String(Character.toChars(cp)))
                .withStyle(style("icons", Math.max(8, Math.min(40, px))));
    }

    public static void icon(GuiGraphics g, String name, int x, int y, int px, int c) {
        int kit = kitCell(name);
        if (kit >= 0) {
            kitIcon(g, kit, x, y, px, c);
        } else if (kit != -2) {
            g.drawString(font(), icon(name, px), x, iconY(y, px), f(c), false);
        }
    }

    public static boolean hasIcon(String name) {
        return ICONS.containsKey(name) || kitCell(name) >= 0;
    }

    private static int kitCell(String name) {
        if (!KIT.isEmpty() && name != null) {
            if (name.endsWith("~")) {
                return ICON_ALIAS.containsKey(name.substring(0, name.length() - 1)) ? -2 : -1;
            } else if (name.startsWith("w.")) {
                return (Integer) KIT.getOrDefault(name.substring(2), -1);
            } else {
                String n = (String) ICON_ALIAS.get(name);
                if (n == null && name.endsWith("-s")) {
                    n = (String) ICON_ALIAS.get(name.substring(0, name.length() - 2));
                }

                if (n == null && name.startsWith("a.")) {
                    String base = name.substring(2);
                    if (base.endsWith("-s")) {
                        base = base.substring(0, base.length() - 2);
                    }

                    n = ICON_ALIAS.containsKey(base)
                            ? (String) ICON_ALIAS.get(base)
                            : (KIT.containsKey(base) ? base : null);
                }

                if (n == null && KIT.containsKey(name)) {
                    n = name;
                }

                return n == null ? -1 : (Integer) KIT.getOrDefault(n, -1);
            }
        } else {
            return -1;
        }
    }

    private static void kitIcon(GuiGraphics g, int cell, int x, int y, int size, int c) {
        int px = Math.max(8, Math.min(40, size));
        c = f(c);
        if (c >>> 24 != 0 && size > 0) {
            Identifier tex = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/kit/icons_" + px + ".png");
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    tex,
                    x,
                    y,
                    (float) (cell % kitCols * px),
                    (float) (cell / kitCols * px),
                    size,
                    size,
                    px,
                    px,
                    kitCols * px,
                    kitRows * px,
                    c);
        }
    }

    public static int a(int c, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | c & 0xFFFFFF;
    }

    public static int am(int c, float k) {
        return a(c, (int) ((float) (c >>> 24) * Math.max(0.0F, Math.min(1.0F, k))));
    }

    public static int mix(int a, int b, float t) {
        return Draw.mix(a, b, t);
    }

    static {
        try {
            InputStream in = Px.class.getResourceAsStream("/assets/arcanicvisuals/atlas.json");

            try {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();

                for (Map.Entry<String, JsonElement> e :
                        o.getAsJsonObject("disc").entrySet()) {
                    JsonArray a = ((JsonElement) e.getValue()).getAsJsonArray();
                    DISC.put(
                            Integer.parseInt((String) e.getKey()),
                            new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()});
                }

                for (Map.Entry<String, JsonElement> e :
                        o.getAsJsonObject("ring").entrySet()) {
                    JsonArray a = ((JsonElement) e.getValue()).getAsJsonArray();
                    RING.put(
                            Integer.parseInt((String) e.getKey()),
                            new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()});
                }

                JsonArray s = o.getAsJsonArray("shadow");
                shadow = new int[] {
                    s.get(0).getAsInt(),
                    s.get(1).getAsInt(),
                    s.get(2).getAsInt(),
                    s.get(3).getAsInt()
                };
                JsonArray h = o.getAsJsonArray("hgrad");
                hgrad = new int[] {h.get(0).getAsInt(), h.get(1).getAsInt()};
                JsonArray v = o.getAsJsonArray("vgrad");
                vgrad = new int[] {v.get(0).getAsInt(), v.get(1).getAsInt()};
                JsonArray size = o.getAsJsonArray("size");
                atlasW = size.get(0).getAsInt();
                atlasH = size.get(1).getAsInt();
            } catch (Throwable var13) {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable var7) {
                        var13.addSuppressed(var7);
                    }
                }

                throw var13;
            }

            if (in != null) {
                in.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("arcanicvisuals atlas", e);
        }

        try {
            InputStream in = Px.class.getResourceAsStream("/assets/arcanicvisuals/icons.json");

            try {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();

                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    ICONS.put((String) e.getKey(), ((JsonElement) e.getValue()).getAsInt());
                }
            } catch (Throwable var11) {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable var6) {
                        var11.addSuppressed(var6);
                    }
                }

                throw var11;
            }

            if (in != null) {
                in.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("arcanicvisuals icons", e);
        }

        GLOW = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/glow.png");
        CHAMFER = Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/chamfer.png");
        STYLES = new HashMap<>();
        KIT = new HashMap<>();
        kitCols = 16;
        kitRows = 8;
        ICON_ALIAS = new HashMap<>();

        try {
            InputStream in = Px.class.getResourceAsStream("/assets/arcanicvisuals/kit_icons.json");

            try {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    kitCols = o.get("cols").getAsInt();
                    kitRows = o.get("rows").getAsInt();

                    for (Map.Entry<String, JsonElement> e :
                            o.getAsJsonObject("icons").entrySet()) {
                        KIT.put((String) e.getKey(), ((JsonElement) e.getValue()).getAsInt());
                    }
                }
            } catch (Throwable var9) {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                    }
                }

                throw var9;
            }

            if (in != null) {
                in.close();
            }
        } catch (Exception e) {
            System.err.println("[ArcanicVisuals] kit icons: " + String.valueOf(e));
        }

        String[][] alias = new String[][] {
            {"combat", "sword"},
            {"players", "users"},
            {"world", "planet"},
            {"effects", "sparkles"},
            {"hands", "tap"},
            {"cosmetics", "gem"},
            {"hud", "monitor"},
            {"search", "search"},
            {"presets", "stack"},
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
            {"knife", "knife"},
            {"sparkle", "sparkles"},
            {"modules", "apps"},
            {"user", "user-circle"},
            {"server", "server"},
            {"bolt", "bolt"},
            {"ping", "router"},
            {"clock", "pie"},
            {"bind", "keyboard"},
            {"keyboard", "keyboard"},
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
            {"wand-sparkles", "gem"},
            {"shirt", "gem"},
            {"mouse-pointer-click", "click"},
            {"user-round", "users"},
            {"compass", "planet"},
            {"monitor", "monitor"},
            {"type", "type"},
            {"info", "alert-circle"},
            {"crosshair", "crosshair"},
            {"target", "target"},
            {"heart", "heart"},
            {"gauge", "gauge"},
            {"footprints", "footprints"},
            {"flame", "flame"},
            {"star", "star"},
            {"activity", "activity"},
            {"cpu", "cpu"},
            {"square", "square"},
            {"power", "power"},
            {"music", "music"},
            {"ghost", "ghost"},
            {"bell", "bell"},
            {"infinity", "infinity"},
            {"flag", "lang"},
            {"language", "lang"}
        };

        for (String[] a : alias) {
            if (KIT.containsKey(a[1])) {
                ICON_ALIAS.put(a[0], a[1]);
                if (!a[0].contains("-") || a[0].startsWith("chev") || a[0].endsWith("-s")) {
                    ICON_ALIAS.put("a." + a[0], a[1]);
                    ICON_ALIAS.put("a." + a[0] + "-s", a[1]);
                }
            }
        }
    }

    public static enum Weight {
        MEDIUM("medium"),
        SEMIBOLD("semibold"),
        BOLD("bold");

        final String tag;

        private Weight(String t) {
            this.tag = t;
        }
    }
}
