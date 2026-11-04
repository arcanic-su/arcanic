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
package net.arcanic.visuals.lua;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** The Lua API: events, draw, color, json, timer, storage. The game and the mod are in {@link LuaGame}, {@link LuaMod}. */
final class LuaApi {
    private LuaApi() {}

    private static final Gson GSON = new Gson();

    static void install(Globals g, Script s) {
        g.set("events", events(s));
        g.set("draw", draw());
        g.set("color", colour());
        g.set("json", json());
        g.set("timer", timer(s));
        g.set("storage", storage(s));
        LuaGame.install(g, s);
        LuaMod.install(g, s);
    }

    // ---- helpers

    interface Body {
        Varargs call(Varargs args);
    }

    static VarArgFunction fn(Body body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return body.call(args);
            }
        };
    }

    static VarArgFunction value(Supplier<Object> get) {
        return fn(a -> LuaJson.toLua(get.get()));
    }

    static VarArgFunction act(Consumer<Varargs> body) {
        return fn(a -> {
            body.accept(a);
            return LuaValue.NONE;
        });
    }

    static int argb(Varargs a, int i) {
        return (int) (long) a.checkdouble(i);
    }

    static int argb(Varargs a, int i, int def) {
        return a.isnoneornil(i) ? def : argb(a, i);
    }

    static LuaValue colour(int c) {
        return LuaValue.valueOf((double) (c & 0xFFFFFFFFL));
    }

    static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ---- events

    private static LuaTable events(Script s) {
        LuaTable t = new LuaTable();
        t.set("on", act(a -> s.on(a.checkjstring(1), a.checkfunction(2))));
        t.set("off", act(a -> s.off(a.checkjstring(1), a.checkfunction(2))));
        // to the handlers of other scripts; built-in events come from the client only
        t.set("emit", act(a -> {
            String e = a.checkjstring(1);
            if (Scripts.BUILT_IN.contains(e)) throw new LuaError("событие " + e + " шлёт только клиент");
            Object[] args = new Object[Math.max(0, a.narg() - 1)];
            for (int i = 0; i < args.length; i++) args[i] = a.arg(i + 2);
            Scripts.emit(e, args);
        }));
        return t;
    }

    // ---- draw

    static GuiGraphics g() {
        GuiGraphics g = Scripts.graphics;
        if (g == null) throw new LuaError("рисовать можно только в render и в HUD-элементах");
        return g;
    }

    private static Px.Weight weight(Varargs a, int i) {
        return switch (a.optjstring(i, "medium").toLowerCase(Locale.ROOT)) {
            case "bold" -> Px.Weight.BOLD;
            case "semibold" -> Px.Weight.SEMIBOLD;
            default -> Px.Weight.MEDIUM;
        };
    }

    private static int size(Varargs a, int i) {
        return Math.max(4, Math.min(160, a.optint(i, 12)));
    }

    private record Image(Identifier id, int w, int h) {}

    private static final Map<String, Image> IMAGES = new HashMap<>();

    private static Image image(String file) {
        String clean = file.replace('\\', '/');
        if (clean.contains("..")
                || clean.startsWith("/")
                || !clean.toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new LuaError("картинка: только .png из папки scripts/images");
        }
        Image had = IMAGES.get(clean);
        if (had != null) return had;
        Path p = Scripts.DIR.resolve("images").resolve(clean);
        NativeImage img;
        try (InputStream in = Files.newInputStream(p)) {
            img = NativeImage.read(in);
        } catch (Exception e) {
            throw new LuaError("нет картинки scripts/images/" + clean);
        }
        if (img.getWidth() > 2048 || img.getHeight() > 2048) {
            img.close();
            throw new LuaError("картинка больше 2048×2048");
        }
        Identifier id = Identifier.fromNamespaceAndPath("arcanicvisuals", "lua/image" + IMAGES.size());
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "lua " + clean, img));
        Image made = new Image(id, img.getWidth(), img.getHeight());
        IMAGES.put(clean, made);
        return made;
    }

    /** Images are read again when the scripts are reloaded. */
    static void forgetImages() {
        for (Image i : IMAGES.values())
            Minecraft.getInstance().getTextureManager().release(i.id());
        IMAGES.clear();
    }

    private static final Map<String, ItemStack> ITEMS = new HashMap<>();

    static ItemStack item(String id) {
        return ITEMS.computeIfAbsent(id, k -> {
            Identifier rl = Identifier.tryParse(k);
            return rl == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(rl));
        });
    }

    private static LuaTable draw() {
        LuaTable t = new LuaTable();
        t.set("width", value(Px::width));
        t.set("height", value(Px::height));
        t.set("scale", value(() -> Px.height() / 1080.0));
        t.set("rect", act(a -> Px.rect(g(), a.checkint(1), a.checkint(2), a.checkint(3), a.checkint(4), argb(a, 5))));
        t.set(
                "round",
                act(a -> Px.round(
                        g(), a.checkint(1), a.checkint(2), a.checkint(3), a.checkint(4), a.checkint(5), argb(a, 6))));
        t.set(
                "outline",
                act(a -> Px.outline(
                        g(), a.checkint(1), a.checkint(2), a.checkint(3), a.checkint(4), a.checkint(5), argb(a, 6))));
        t.set("circle", act(a -> Px.circle(g(), a.checkint(1), a.checkint(2), a.checkint(3), argb(a, 4))));
        t.set("ring", act(a -> Px.ring(g(), a.checkint(1), a.checkint(2), a.checkint(3), argb(a, 4))));
        t.set(
                "line",
                act(a -> Px.seg(
                        g(),
                        (float) a.checkdouble(1),
                        (float) a.checkdouble(2),
                        (float) a.checkdouble(3),
                        (float) a.checkdouble(4),
                        (float) a.optdouble(5, 2.0),
                        argb(a, 6, 0xFFFFFFFF))));
        t.set("gradient", act(a -> {
            if (a.optboolean(7, false)) {
                Px.vgrad(g(), a.checkint(1), a.checkint(2), a.checkint(3), a.checkint(4), argb(a, 5), argb(a, 6));
            } else {
                Px.hgrad(g(), a.checkint(1), a.checkint(2), a.checkint(3), a.checkint(4), argb(a, 5), argb(a, 6));
            }
        }));
        t.set(
                "shadow",
                act(a -> Px.shadow(
                        g(),
                        a.checkint(1),
                        a.checkint(2),
                        a.checkint(3),
                        a.checkint(4),
                        a.optint(5, 16),
                        argb(a, 6, 0x80000000))));
        t.set("text", act(a -> {
            String s = clip(a.checkjstring(1), 512);
            int c = argb(a, 4, 0xFFFFFFFF);
            int x = a.checkint(2), y = a.checkint(3), size = size(a, 5);
            Px.Weight w = weight(a, 6);
            switch (a.optjstring(7, "left")) {
                case "center" -> Px.textCenter(g(), s, x, y, w, size, c);
                case "right" -> Px.textRight(g(), s, x, y, w, size, c);
                default -> {
                    if (a.optboolean(8, false)) Px.textShadow(g(), s, x, y, w, size, c);
                    else Px.text(g(), s, x, y, w, size, c);
                }
            }
        }));
        t.set("textWidth", fn(a -> LuaValue.valueOf(Px.textWidth(a.checkjstring(1), weight(a, 3), size(a, 2)))));
        t.set(
                "icon",
                act(a -> Px.icon(g(), a.checkjstring(1), a.checkint(2), a.checkint(3), a.checkint(4), argb(a, 5, -1))));
        t.set("item", act(a -> {
            ItemStack st = item(a.checkjstring(1));
            if (st.isEmpty()) return;
            GuiGraphics g = g();
            float k = a.optint(4, 16) / 16.0F;
            g.pose().pushMatrix();
            g.pose().translate(a.checkint(2), a.checkint(3));
            g.pose().scale(k, k);
            g.renderItem(st, 0, 0);
            g.pose().popMatrix();
        }));
        t.set("head", act(a -> LuaGame.head(g(), a.checkjstring(1), a.checkint(2), a.checkint(3), a.checkint(4))));
        t.set("image", act(a -> {
            Image img = image(a.checkjstring(1));
            int w = a.optint(4, img.w()), h = a.optint(5, img.h());
            g().blit(
                            RenderPipelines.GUI_TEXTURED,
                            img.id(),
                            a.checkint(2),
                            a.checkint(3),
                            0.0F,
                            0.0F,
                            w,
                            h,
                            img.w(),
                            img.h(),
                            img.w(),
                            img.h(),
                            argb(a, 6, -1));
        }));
        t.set("clip", act(a -> g().enableScissor(
                        a.checkint(1), a.checkint(2), a.checkint(1) + a.checkint(3), a.checkint(2) + a.checkint(4))));
        t.set("unclip", act(a -> g().disableScissor()));
        t.set("push", act(a -> g().pose().pushMatrix()));
        t.set("pop", act(a -> g().pose().popMatrix()));
        t.set("translate", act(a -> g().pose().translate((float) a.checkdouble(1), (float) a.checkdouble(2))));
        t.set("zoom", act(a -> g().pose().scale((float) a.checkdouble(1), (float) a.optdouble(2, a.checkdouble(1)))));
        t.set("rotate", act(a -> g().pose().rotate((float) Math.toRadians(a.checkdouble(1)))));
        t.set("alpha", act(a -> Px.fade = (float) Math.max(0, Math.min(1, a.checkdouble(1)))));
        return t;
    }

    // ---- color

    private static int c255(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static LuaTable colour() {
        LuaTable t = new LuaTable();
        t.set(
                "rgb",
                fn(a -> colour(c255(a.optdouble(4, 255)) << 24
                        | c255(a.checkdouble(1)) << 16
                        | c255(a.checkdouble(2)) << 8
                        | c255(a.checkdouble(3)))));
        t.set("hsv", fn(a -> {
            int rgb = java.awt.Color.HSBtoRGB(
                    (float) (a.checkdouble(1) / 360.0), (float) a.optdouble(2, 1), (float) a.optdouble(3, 1));
            return colour(c255(a.optdouble(4, 255)) << 24 | rgb & 0xFFFFFF);
        }));
        t.set("hex", fn(a -> {
            String s = a.checkjstring(1).strip();
            if (s.startsWith("#")) s = s.substring(1);
            try {
                long v = Long.parseLong(s, 16);
                if (s.length() <= 6) v |= 0xFF000000L;
                return colour((int) v);
            } catch (NumberFormatException e) {
                throw new LuaError("не цвет: " + a.checkjstring(1));
            }
        }));
        t.set("unpack", fn(a -> {
            int c = argb(a, 1);
            return LuaValue.varargsOf(new LuaValue[] {
                LuaValue.valueOf(c >> 16 & 255),
                LuaValue.valueOf(c >> 8 & 255),
                LuaValue.valueOf(c & 255),
                LuaValue.valueOf(c >>> 24)
            });
        }));
        t.set("accent", fn(a -> colour(Theme.accent() | 0xFF000000)));
        t.set("accent2", fn(a -> colour(Theme.accent2() | 0xFF000000)));
        t.set("text", fn(a -> colour(Theme.text())));
        t.set("muted", fn(a -> colour(Theme.muted())));
        t.set("background", fn(a -> colour(Theme.bg())));
        t.set("mix", fn(a -> colour(Px.mix(argb(a, 1), argb(a, 2), (float) a.checkdouble(3)))));
        t.set("alpha", fn(a -> colour(c255(a.checkdouble(2)) << 24 | argb(a, 1) & 0xFFFFFF)));
        t.set("rainbow", fn(a -> {
            double speed = a.optdouble(1, 1), offset = a.optdouble(2, 0);
            float hue = (float) ((System.currentTimeMillis() / 1000.0 * speed * 0.25 + offset) % 1.0);
            return colour(java.awt.Color.HSBtoRGB(hue, (float) a.optdouble(3, 0.7), 1.0F) | 0xFF000000);
        }));
        return t;
    }

    // ---- json, timer, storage

    private static LuaTable json() {
        LuaTable t = new LuaTable();
        t.set("encode", fn(a -> LuaValue.valueOf(GSON.toJson(LuaJson.toJson(a.arg1())))));
        t.set("decode", fn(a -> {
            try {
                return LuaJson.toLua(JsonParser.parseString(a.checkjstring(1)));
            } catch (LuaError e) {
                throw e;
            } catch (RuntimeException e) {
                throw new LuaError("не JSON: " + e.getMessage());
            }
        }));
        return t;
    }

    private static LuaTable timer(Script s) {
        LuaTable t = new LuaTable();
        t.set("after", fn(a -> LuaValue.valueOf(s.timer((long) a.checkdouble(1), false, a.checkfunction(2)))));
        t.set("every", fn(a -> LuaValue.valueOf(s.timer((long) a.checkdouble(1), true, a.checkfunction(2)))));
        t.set("cancel", act(a -> s.cancel(a.checkint(1))));
        t.set("now", value(System::currentTimeMillis));
        return t;
    }

    private static LuaTable storage(Script s) {
        LuaTable t = new LuaTable();
        t.set("get", fn(a -> {
            LuaValue v = s.load(a.checkjstring(1));
            return v.isnil() ? a.arg(2) : v;
        }));
        t.set("set", act(a -> s.store(a.checkjstring(1), a.arg(2))));
        t.set("keys", fn(a -> s.keys()));
        t.set("clear", act(a -> s.clearStorage()));
        return t;
    }
}
