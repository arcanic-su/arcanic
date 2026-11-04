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

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Px;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** The Lua API. Read-only and drawing only: no input, no chat or commands, no game changes. */
public final class LuaApi {
    private LuaApi() {}

    public static final Set<String> EVENTS = Set.of("tick", "render", "chat", "join", "leave", "unload");

    /** Shown on the API tab. */
    public static final List<String[]> REFERENCE = List.of(
            new String[] {"# События", ""},
            new String[] {"events.on(\"tick\", fn)", "20 раз в секунду"},
            new String[] {"events.on(\"render\", fn)", "каждый кадр: здесь рисуют на экране"},
            new String[] {"events.on(\"chat\", fn(text))", "пришло сообщение в чат"},
            new String[] {"events.on(\"join\", fn(server))", "зашёл на сервер"},
            new String[] {"events.on(\"leave\", fn)", "вышел с сервера"},
            new String[] {"events.on(\"unload\", fn)", "скрипт выключают"},
            new String[] {"# Рисование (только в render)", ""},
            new String[] {"draw.width(), draw.height()", "размер экрана в пикселях"},
            new String[] {"draw.rect(x, y, w, h, color)", "прямоугольник"},
            new String[] {"draw.round(x, y, w, h, r, color)", "скруглённый прямоугольник"},
            new String[] {"draw.outline(x, y, w, h, r, color)", "рамка"},
            new String[] {"draw.text(text, x, y, color, size, weight)", "текст; weight: medium, semibold, bold"},
            new String[] {"draw.textWidth(text, size, weight)", "ширина текста"},
            new String[] {"# Цвета", ""},
            new String[] {"color.rgb(r, g, b, a)", "цвет из 0–255"},
            new String[] {"color.hex(\"#8B5CF6\")", "цвет из #RRGGBB или #AARRGGBB"},
            new String[] {"color.accent(), color.accent2(), color.text()", "цвета темы"},
            new String[] {"color.mix(c1, c2, t), color.alpha(c, a)", "смешать, прозрачность 0–255"},
            new String[] {"# Игрок", ""},
            new String[] {"player.name(), player.health(), player.maxHealth()", ""},
            new String[] {"player.food(), player.armor(), player.level()", "голод, броня, уровень опыта"},
            new String[] {"player.pos()", "x, y, z"},
            new String[] {"player.yaw(), player.pitch(), player.speed()", "speed в блоках в секунду"},
            new String[] {"player.onGround(), player.sneaking(), player.sprinting()", ""},
            new String[] {"player.held()", "id предмета в руке, например minecraft:diamond_sword"},
            new String[] {"player.effects()", "список {name, level, seconds}"},
            new String[] {"player.dimension()", "minecraft:overworld и т. п."},
            new String[] {"# Мир и клиент", ""},
            new String[] {"world.loaded(), world.time(), world.weather()", "время суток 0–24000, clear/rain/thunder"},
            new String[] {"client.fps(), client.ping(), client.ms()", "ms — время в миллисекундах"},
            new String[] {"client.server(), client.screen()", "адрес сервера, открытое меню"},
            new String[] {"client.print(text)", "строка в чат, видишь только ты"},
            new String[] {"client.notify(text)", "уведомление Arcanic"},
            new String[] {"modules.enabled(id), modules.list()", "включена ли функция Arcanic"},
            new String[] {"storage.get(key), storage.set(key, value)", "значения сохраняются между играми"},
            new String[] {"print(...)", "строка в консоль на этой странице"});

    static void install(Globals g, Script script) {
        g.set("draw", draw());
        g.set("color", colour());
        g.set("player", player());
        g.set("world", world());
        g.set("client", client());
        g.set("modules", modules());
        LuaTable storage = new LuaTable();
        storage.set("get", fn(a -> script.load(a.checkjstring(1))));
        storage.set("set", fn(a -> {
            script.store(a.checkjstring(1), a.arg(2));
            return LuaValue.NONE;
        }));
        g.set("storage", storage);
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
        return fn(a -> toLua(get.get()));
    }

    static LuaValue toLua(Object o) {
        if (o == null) return LuaValue.NIL;
        if (o instanceof LuaValue v) return v;
        if (o instanceof Boolean b) return LuaValue.valueOf(b);
        if (o instanceof Integer i) return LuaValue.valueOf(i);
        if (o instanceof Long l) return LuaValue.valueOf((double) l);
        if (o instanceof Number n) return LuaValue.valueOf(n.doubleValue());
        return LuaValue.valueOf(String.valueOf(o));
    }

    private static int argb(Varargs a, int i) {
        return (int) (long) a.checkdouble(i);
    }

    private static LuaValue colourValue(int c) {
        return LuaValue.valueOf((double) (c & 0xFFFFFFFFL));
    }

    private static LocalPlayer me() {
        return Minecraft.getInstance().player;
    }

    // ---- draw

    private static GuiGraphics g() {
        GuiGraphics g = Scripts.graphics;
        if (g == null) throw new LuaError("draw.* работает только внутри events.on(\"render\", ...)");
        return g;
    }

    private static Px.Weight weight(Varargs a, int i) {
        String w = a.optjstring(i, "medium").toLowerCase(Locale.ROOT);
        return switch (w) {
            case "bold" -> Px.Weight.BOLD;
            case "semibold" -> Px.Weight.SEMIBOLD;
            default -> Px.Weight.MEDIUM;
        };
    }

    private static int size(Varargs a, int i) {
        return Math.max(4, Math.min(96, a.optint(i, 12)));
    }

    private static LuaTable draw() {
        LuaTable t = new LuaTable();
        t.set("width", value(Px::width));
        t.set("height", value(Px::height));
        t.set("rect", fn(a -> {
            Px.rect(g(), a.checkint(1), a.checkint(2), a.checkint(3), a.checkint(4), argb(a, 5));
            return LuaValue.NONE;
        }));
        t.set("round", fn(a -> {
            Px.round(g(), a.checkint(1), a.checkint(2), a.checkint(3), a.checkint(4), a.checkint(5), argb(a, 6));
            return LuaValue.NONE;
        }));
        t.set("outline", fn(a -> {
            Px.outline(g(), a.checkint(1), a.checkint(2), a.checkint(3), a.checkint(4), a.checkint(5), argb(a, 6));
            return LuaValue.NONE;
        }));
        t.set("text", fn(a -> {
            String s = a.checkjstring(1);
            if (s.length() > 256) s = s.substring(0, 256);
            int c = a.isnoneornil(4) ? 0xFFFFFFFF : argb(a, 4);
            Px.text(g(), s, a.checkint(2), a.checkint(3), weight(a, 6), size(a, 5), c);
            return LuaValue.NONE;
        }));
        t.set("textWidth", fn(a -> LuaValue.valueOf(Px.textWidth(a.checkjstring(1), weight(a, 3), size(a, 2)))));
        return t;
    }

    // ---- color

    private static int clamp255(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static LuaTable colour() {
        LuaTable t = new LuaTable();
        t.set(
                "rgb",
                fn(a -> colourValue(clamp255(a.optdouble(4, 255)) << 24
                        | clamp255(a.checkdouble(1)) << 16
                        | clamp255(a.checkdouble(2)) << 8
                        | clamp255(a.checkdouble(3)))));
        t.set("hex", fn(a -> {
            String s = a.checkjstring(1).strip();
            if (s.startsWith("#")) s = s.substring(1);
            try {
                long v = Long.parseLong(s, 16);
                if (s.length() <= 6) v |= 0xFF000000L;
                return colourValue((int) v);
            } catch (NumberFormatException e) {
                throw new LuaError("не цвет: " + a.checkjstring(1));
            }
        }));
        t.set("accent", fn(a -> colourValue(Theme.accent() | 0xFF000000)));
        t.set("accent2", fn(a -> colourValue(Theme.accent2() | 0xFF000000)));
        t.set("text", fn(a -> colourValue(Theme.text())));
        t.set("mix", fn(a -> colourValue(Px.mix(argb(a, 1), argb(a, 2), (float) a.checkdouble(3)))));
        t.set("alpha", fn(a -> colourValue(clamp255(a.checkdouble(2)) << 24 | argb(a, 1) & 0xFFFFFF)));
        return t;
    }

    // ---- player

    private static LuaTable player() {
        LuaTable t = new LuaTable();
        t.set("name", value(() -> Minecraft.getInstance().getUser().getName()));
        t.set("health", value(() -> me() == null ? 0 : me().getHealth()));
        t.set("maxHealth", value(() -> me() == null ? 0 : me().getMaxHealth()));
        t.set("food", value(() -> me() == null ? 0 : me().getFoodData().getFoodLevel()));
        t.set("armor", value(() -> me() == null ? 0 : me().getArmorValue()));
        t.set("level", value(() -> me() == null ? 0 : me().experienceLevel));
        t.set("pos", fn(a -> {
            LocalPlayer p = me();
            if (p == null) return LuaValue.varargsOf(LuaValue.valueOf(0), LuaValue.valueOf(0), LuaValue.valueOf(0));
            return LuaValue.varargsOf(
                    LuaValue.valueOf(p.getX()), LuaValue.valueOf(p.getY()), LuaValue.valueOf(p.getZ()));
        }));
        t.set("yaw", value(() -> me() == null ? 0 : me().getYRot()));
        t.set("pitch", value(() -> me() == null ? 0 : me().getXRot()));
        t.set("speed", value(() -> {
            LocalPlayer p = me();
            if (p == null) return 0;
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            return Math.sqrt(dx * dx + dz * dz) * 20.0;
        }));
        t.set("onGround", value(() -> me() != null && me().onGround()));
        t.set("sneaking", value(() -> me() != null && me().isShiftKeyDown()));
        t.set("sprinting", value(() -> me() != null && me().isSprinting()));
        t.set("held", value(() -> {
            LocalPlayer p = me();
            if (p == null) return "";
            ItemStack st = p.getMainHandItem();
            return st.isEmpty()
                    ? ""
                    : BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        }));
        t.set("dimension", value(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? "" : mc.level.dimension().identifier().toString();
        }));
        t.set("effects", fn(a -> {
            LuaTable list = new LuaTable();
            LocalPlayer p = me();
            if (p != null) {
                int i = 1;
                for (MobEffectInstance e : p.getActiveEffects()) {
                    LuaTable o = new LuaTable();
                    o.set("name", e.getEffect().value().getDisplayName().getString());
                    o.set("id", e.getEffect().getRegisteredName());
                    o.set("level", e.getAmplifier() + 1);
                    o.set("seconds", Math.max(0, (e.getDuration() + 19) / 20));
                    list.set(i++, o);
                }
            }
            return list;
        }));
        return t;
    }

    // ---- world and client

    private static LuaTable world() {
        LuaTable t = new LuaTable();
        t.set("loaded", value(() -> Minecraft.getInstance().level != null));
        t.set("time", value(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? 0 : Math.floorMod(mc.level.getLevelData().getDayTime(), 24000L);
        }));
        t.set("weather", value(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return "clear";
            return mc.level.isThundering() ? "thunder" : (mc.level.isRaining() ? "rain" : "clear");
        }));
        return t;
    }

    private static LuaTable client() {
        LuaTable t = new LuaTable();
        t.set("fps", value(() -> Minecraft.getInstance().getFps()));
        t.set("ping", value(() -> Minecraft.getInstance().getCurrentServer() == null ? 0 : HudElements.ping()));
        t.set("ms", value(System::currentTimeMillis));
        t.set("server", value(() -> {
            ServerData d = Minecraft.getInstance().getCurrentServer();
            return d == null ? "" : d.ip;
        }));
        t.set("screen", value(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.screen == null ? "" : mc.screen.getClass().getSimpleName();
        }));
        t.set("print", fn(a -> {
            Minecraft mc = Minecraft.getInstance();
            String s = a.checkjstring(1);
            if (s.length() > 256) s = s.substring(0, 256);
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("[Lua] ")
                                .withStyle(ChatFormatting.DARK_PURPLE)
                                .append(Component.literal(s).withStyle(ChatFormatting.GRAY)),
                        false);
            }
            return LuaValue.NONE;
        }));
        t.set("notify", fn(a -> {
            String s = a.checkjstring(1);
            Toasts.setting(s.length() > 120 ? s.substring(0, 120) : s, true);
            return LuaValue.NONE;
        }));
        return t;
    }

    private static LuaTable modules() {
        LuaTable t = new LuaTable();
        t.set("enabled", fn(a -> {
            String id = a.checkjstring(1);
            for (Module m : Modules.ALL) if (m.id.equals(id)) return LuaValue.valueOf(m.enabled());
            return LuaValue.FALSE;
        }));
        t.set("list", fn(a -> {
            LuaTable list = new LuaTable();
            int i = 1;
            for (Module m : Modules.ALL) list.set(i++, m.id);
            return list;
        }));
        return t;
    }
}
