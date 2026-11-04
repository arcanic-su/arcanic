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

import static net.arcanic.visuals.lua.LuaApi.act;
import static net.arcanic.visuals.lua.LuaApi.argb;
import static net.arcanic.visuals.lua.LuaApi.clip;
import static net.arcanic.visuals.lua.LuaApi.colour;
import static net.arcanic.visuals.lua.LuaApi.fn;
import static net.arcanic.visuals.lua.LuaApi.value;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.ThemeStudio;
import net.arcanic.visuals.helper.HolyFeatures;
import net.arcanic.visuals.hud.HudModule;
import net.arcanic.visuals.hud.Toasts;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** The mod for scripts: our modules and their settings, the script's own modules and HUD elements, theme, configs, HTTP. */
final class LuaMod {
    private LuaMod() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final int MAX_BODY = 1 << 20;

    static void install(Globals g, Script s) {
        g.set("modules", modules());
        g.set("module", moduleFactory(s));
        g.set("hud", hudFactory(s));
        g.set("theme", theme());
        g.set("config", config());
        g.set("http", http(s));
    }

    // ---- our modules

    private static Module find(String id) {
        for (Module m : Modules.everything()) if (m.id.equals(id)) return m;
        throw new LuaError("нет модуля " + id);
    }

    private static Setting<?> setting(Module m, String key) {
        for (Setting<?> s : m.settings) if (s.key.equals(key)) return s;
        throw new LuaError("у " + m.id + " нет настройки " + key);
    }

    private static LuaTable info(Module m) {
        LuaTable t = new LuaTable();
        t.set("id", m.id);
        t.set("name", m.title());
        t.set("description", m.description);
        t.set("category", m.category.name().toLowerCase(Locale.ROOT));
        t.set("enabled", LuaValue.valueOf(m.enabled()));
        t.set("bind", m.bind < 0 ? LuaValue.NIL : LuaValue.valueOf(LuaKeys.name(m.bind)));
        t.set("script", LuaValue.valueOf(m instanceof ScriptModule || m instanceof ScriptHud));
        return t;
    }

    private static LuaValue read(Setting<?> s) {
        if (s instanceof Setting.Colour c) return colour(c.argb());
        if (s instanceof Setting.Action) return LuaValue.NIL;
        return LuaJson.toLua(s.get());
    }

    private static void write(Setting<?> s, LuaValue v) {
        if (s instanceof Setting.Bool b) b.set(v.checkboolean());
        else if (s instanceof Setting.Num n) n.set(v.checkdouble());
        else if (s instanceof Setting.Mode m) {
            String str = v.checkjstring();
            if (!m.modes.contains(str)) throw new LuaError("у " + s.key + " нет варианта " + str);
            m.set(str);
        } else if (s instanceof Setting.Colour c) {
            c.setTheme(false);
            c.set((int) (long) v.checkdouble());
        } else if (s instanceof Setting.Text t) t.set(clip(v.checkjstring(), Math.max(1, t.max)));
        else if (s instanceof Setting.Key k) k.set(v.isnil() ? -1 : LuaKeys.code(v.checkjstring()));
        else if (s instanceof Setting.Items it) {
            LuaTable tab = v.checktable();
            for (Setting.Items.Entry e : it.entries) {
                LuaValue on = tab.get(e.key());
                if (on.isboolean() && on.toboolean() != it.on(e.key())) it.toggle(e.key());
            }
        } else throw new LuaError("настройку " + s.key + " нельзя менять из скрипта");
        Config.dirty();
        ScriptModules.changed();
    }

    private static LuaTable describe(Setting<?> s) {
        LuaTable t = new LuaTable();
        t.set("key", s.key);
        t.set("name", s.name);
        t.set("visible", LuaValue.valueOf(s.visible()));
        t.set("value", read(s));
        String type = s.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        t.set("type", type);
        if (s instanceof Setting.Num n) {
            t.set("min", n.min);
            t.set("max", n.max);
            t.set("step", n.step);
        } else if (s instanceof Setting.Mode m) {
            t.set("options", LuaJson.toLua(m.modes));
        }
        return t;
    }

    private static void enable(Module m, boolean on) {
        if (on == m.enabled()) return;
        if (on && HolyFeatures.forbidden(m)) {
            HolyFeatures.deny(m);
            return;
        }
        m.setEnabled(on);
        m.knob = on ? 1.0F : 0.0F;
        Config.dirty();
    }

    private static LuaTable modules() {
        LuaTable t = new LuaTable();
        t.set("list", fn(a -> {
            LuaTable list = new LuaTable();
            int i = 1;
            for (Module m : Modules.everything()) list.set(i++, m.id);
            return list;
        }));
        t.set("info", fn(a -> info(find(a.checkjstring(1)))));
        t.set("enabled", fn(a -> LuaValue.valueOf(find(a.checkjstring(1)).enabled())));
        t.set("set", act(a -> enable(find(a.checkjstring(1)), a.checkboolean(2))));
        t.set("toggle", act(a -> {
            Module m = find(a.checkjstring(1));
            enable(m, !m.enabled());
            Toasts.module(m, false);
        }));
        t.set("settings", fn(a -> {
            Module m = find(a.checkjstring(1));
            LuaTable list = new LuaTable();
            int i = 1;
            for (Setting<?> s : m.settings) list.set(i++, describe(s));
            return list;
        }));
        t.set("get", fn(a -> read(setting(find(a.checkjstring(1)), a.checkjstring(2)))));
        t.set("setValue", act(a -> write(setting(find(a.checkjstring(1)), a.checkjstring(2)), a.arg(3))));
        t.set("press", act(a -> {
            if (setting(find(a.checkjstring(1)), a.checkjstring(2)) instanceof Setting.Action ac) ac.run.run();
            else throw new LuaError(a.checkjstring(2) + " не кнопка");
        }));
        return t;
    }

    // ---- the script's own modules and HUD elements

    private static String str(LuaTable t, String k, String def) {
        LuaValue v = t.get(k);
        return v.isnil() ? def : v.checkjstring();
    }

    /** Arguments after the table itself when called as m:method(...), or from the first when called as m.method(...). */
    private static int from(Varargs a, LuaTable self) {
        return a.arg1().raweq(self) ? 2 : 1;
    }

    private static LuaFunction optFn(LuaTable t, String k) {
        LuaValue v = t.get(k);
        return v.isnil() ? null : v.checkfunction();
    }

    private static LuaTable handle(Script s, Module m) {
        LuaTable self = new LuaTable();
        self.set("id", m.id);
        self.set("enabled", fn(a -> LuaValue.valueOf(m.enabled())));
        self.set("setEnabled", act(a -> enable(m, a.checkboolean(from(a, self)))));
        self.set("get", fn(a -> read(setting(m, a.checkjstring(from(a, self))))));
        self.set("set", act(a -> {
            int i = from(a, self);
            write(setting(m, a.checkjstring(i)), a.arg(i + 1));
        }));
        self.set("bool", fn(a -> {
            int i = from(a, self);
            add(m, new Setting.Bool(a.checkjstring(i), a.checkjstring(i + 1), a.optboolean(i + 2, false)));
            return self;
        }));
        self.set("slider", fn(a -> {
            int i = from(a, self);
            double min = a.optdouble(i + 3, 0), max = a.optdouble(i + 4, 100);
            if (max <= min) throw new LuaError("slider: max должен быть больше min");
            add(
                    m,
                    new Setting.Num(
                            a.checkjstring(i),
                            a.checkjstring(i + 1),
                            a.optdouble(i + 2, min),
                            min,
                            max,
                            a.optdouble(i + 5, (max - min) / 100),
                            a.optjstring(i + 6, "")));
            return self;
        }));
        self.set("mode", fn(a -> {
            int i = from(a, self);
            LuaTable opts = a.checktable(i + 3);
            String[] modes = new String[opts.length()];
            for (int j = 0; j < modes.length; j++) modes[j] = opts.get(j + 1).checkjstring();
            if (modes.length == 0) throw new LuaError("mode: нужен хотя бы один вариант");
            add(m, new Setting.Mode(a.checkjstring(i), a.checkjstring(i + 1), a.optjstring(i + 2, modes[0]), modes));
            return self;
        }));
        self.set("color", fn(a -> {
            int i = from(a, self);
            add(m, new Setting.Colour(a.checkjstring(i), a.checkjstring(i + 1), false, argb(a, i + 2, 0xFF8B5CF6)));
            return self;
        }));
        self.set("text", fn(a -> {
            int i = from(a, self);
            add(m, new Setting.Text(a.checkjstring(i), a.checkjstring(i + 1), a.optjstring(i + 2, ""), "", 256));
            return self;
        }));
        self.set("key", fn(a -> {
            int i = from(a, self);
            Setting.Key k = new Setting.Key(a.checkjstring(i), a.checkjstring(i + 1));
            if (!a.isnoneornil(i + 2)) k.set(LuaKeys.code(a.checkjstring(i + 2)));
            add(m, k);
            return self;
        }));
        self.set("button", fn(a -> {
            int i = from(a, self);
            LuaFunction f = a.checkfunction(i + 3);
            add(
                    m,
                    new Setting.Action(
                            a.checkjstring(i), a.checkjstring(i + 1), a.checkjstring(i + 2), () -> s.call(f)));
            return self;
        }));
        return self;
    }

    private static void add(Module m, Setting<?> st) {
        if (m instanceof ScriptModule sm) sm.setting(st);
        else if (m instanceof ScriptHud h) h.setting(st);
    }

    private static String newId(Script s, LuaTable t) {
        String raw = t.get("id").checkjstring().toLowerCase(Locale.ROOT);
        if (!raw.matches("[a-z0-9_]{1,40}")) throw new LuaError("id: только a-z, 0-9 и _, до 40 символов");
        String id = "lua_" + raw;
        if (ScriptModules.taken(id)) throw new LuaError("модуль " + id + " уже есть");
        if (s.modules.size() >= 32) throw new LuaError("больше 32 модулей в одном скрипте");
        return id;
    }

    private static LuaTable moduleFactory(Script s) {
        LuaTable t = new LuaTable();
        t.set("create", fn(a -> {
            LuaTable spec = a.checktable(1);
            ScriptModule m = new ScriptModule(
                    s,
                    newId(s, spec),
                    clip(str(spec, "name", spec.get("id").tojstring()), 40),
                    clip(str(spec, "description", ""), 300),
                    ScriptModule.category(str(spec, "category", "visuals")));
            m.onEnable = optFn(spec, "onEnable");
            m.onDisable = optFn(spec, "onDisable");
            ScriptModules.register(m);
            s.modules.add(m);
            if (!s.loading()) ScriptModules.resume(m);
            return handle(s, m);
        }));
        return t;
    }

    private static LuaTable hudFactory(Script s) {
        LuaTable t = new LuaTable();
        t.set("element", fn(a -> {
            if (Scripts.graphics != null) throw new LuaError("hud.element нельзя создавать во время рисования");
            LuaTable spec = a.checktable(1);
            LuaValue size = spec.get("size");
            int w = size.istable() ? size.get(1).checkint() : spec.get("width").optint(120);
            int h = size.istable() ? size.get(2).checkint() : spec.get("height").optint(40);
            ScriptHud el = new ScriptHud(
                    s,
                    newId(s, spec),
                    clip(str(spec, "name", spec.get("id").tojstring()), 40),
                    clip(str(spec, "description", ""), 300),
                    spec.get("render").checkfunction(),
                    Math.max(4, Math.min(1920, w)),
                    Math.max(4, Math.min(1080, h)),
                    spec.get("x").optdouble(0.02),
                    spec.get("y").optdouble(0.4));
            ScriptModules.register(el);
            s.modules.add(el);
            if (!s.loading()) ScriptModules.resume(el);
            LuaTable self = handle(s, el);
            self.set("size", act(b -> {
                int i = from(b, self);
                el.baseW = Math.max(4, Math.min(1920, b.checkint(i)));
                el.baseH = Math.max(4, Math.min(1080, b.checkint(i + 1)));
            }));
            return self;
        }));
        t.set("elements", fn(a -> {
            LuaTable list = new LuaTable();
            int i = 1;
            for (HudModule h : Modules.HUD) list.set(i++, h.id);
            return list;
        }));
        t.set("position", fn(a -> {
            for (HudModule h : Modules.HUD) {
                if (h.id.equals(a.checkjstring(1))) {
                    return LuaValue.varargsOf(LuaValue.valueOf(h.fx), LuaValue.valueOf(h.fy));
                }
            }
            return LuaValue.NIL;
        }));
        return t;
    }

    // ---- theme and configs

    private static Setting.Colour token(String name) {
        ThemeStudio st = Modules.THEMES;
        for (Setting.Colour c : st.colours()) if (c.key.equals(name)) return c;
        throw new LuaError("нет цвета темы " + name);
    }

    private static LuaTable theme() {
        LuaTable t = new LuaTable();
        t.set("custom", value(() -> Modules.THEMES.active()));
        t.set("setCustom", act(a -> {
            Theme.fadeFrom(Theme.p());
            Modules.THEMES.custom.set(a.checkboolean(1));
            Config.dirty();
        }));
        t.set("get", fn(a -> colour(token(a.checkjstring(1)).get() | 0xFF000000)));
        t.set("set", act(a -> {
            Theme.fadeFrom(Theme.p());
            token(a.checkjstring(1)).set(argb(a, 2) | 0xFF000000);
            Modules.THEMES.custom.set(true);
            Config.dirty();
        }));
        t.set("presets", fn(a -> {
            LuaTable list = new LuaTable();
            int i = 1;
            for (ThemeStudio.Preset p : ThemeStudio.PRESETS) list.set(i++, p.name());
            return list;
        }));
        t.set("preset", act(a -> {
            for (ThemeStudio.Preset p : ThemeStudio.PRESETS) {
                if (p.name().equalsIgnoreCase(a.checkjstring(1))) {
                    Modules.THEMES.apply(p);
                    Config.dirty();
                    return;
                }
            }
            throw new LuaError("нет темы " + a.checkjstring(1));
        }));
        t.set("code", value(() -> Modules.THEMES.code()));
        t.set("load", fn(a -> {
            boolean ok = Modules.THEMES.load(a.checkjstring(1));
            if (ok) Config.dirty();
            return LuaValue.valueOf(ok);
        }));
        return t;
    }

    private static LuaTable config() {
        LuaTable t = new LuaTable();
        t.set("list", fn(a -> {
            LuaTable list = new LuaTable();
            int i = 1;
            for (Config.Preset p : Config.presets()) list.set(i++, p.name());
            return list;
        }));
        t.set("active", value(Config::activePreset));
        t.set("load", fn(a -> LuaValue.valueOf(Config.loadPreset(a.checkjstring(1)))));
        return t;
    }

    // ---- http: https only, no local addresses, a few requests at a time

    private static URI check(String url) {
        URI u;
        try {
            u = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new LuaError("плохой адрес: " + url);
        }
        if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null) {
            throw new LuaError("http: только https-адреса");
        }
        return u;
    }

    private static boolean local(String host) {
        try {
            for (InetAddress ip : InetAddress.getAllByName(host)) {
                if (ip.isLoopbackAddress()
                        || ip.isSiteLocalAddress()
                        || ip.isLinkLocalAddress()
                        || ip.isAnyLocalAddress()
                        || ip.isMulticastAddress()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static void request(Script s, String method, Varargs a) {
        URI u = check(a.checkjstring(1));
        boolean post = method.equals("POST");
        String body = post ? clip(a.checkjstring(2), MAX_BODY) : null;
        int at = post ? 3 : 2;
        LuaFunction cb = a.isnoneornil(at) ? null : a.checkfunction(at);
        LuaTable headers = a.istable(at + 1) ? a.checktable(at + 1) : null;
        long now = System.currentTimeMillis();
        if (now - s.minuteStart > 60_000) {
            s.minuteStart = now;
            s.minuteCount = 0;
        }
        if (++s.minuteCount > 60) throw new LuaError("http: больше 60 запросов в минуту");
        if (s.requests >= 4) throw new LuaError("http: больше 4 запросов сразу");
        HttpRequest.Builder b =
                HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(15)).header("User-Agent", "ArcanicVisuals-Lua");
        if (headers != null) {
            LuaValue k = LuaValue.NIL;
            while (true) {
                Varargs next = headers.next(k);
                if ((k = next.arg1()).isnil()) break;
                String name = k.tojstring();
                if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("user-agent")) {
                    b.header(name, clip(next.arg(2).tojstring(), 2048));
                }
            }
        }
        if (post) b.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        else b.GET();
        HttpRequest req = b.build();
        s.requests++;
        Thread.ofVirtual().start(() -> {
            int status;
            String text;
            try {
                if (local(u.getHost())) throw new IllegalStateException("локальные адреса запрещены");
                HttpResponse<byte[]> r = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                status = r.statusCode();
                byte[] bytes = r.body();
                text = new String(bytes, 0, Math.min(bytes.length, MAX_BODY), StandardCharsets.UTF_8);
            } catch (Exception e) {
                status = 0;
                text = String.valueOf(e.getMessage());
            }
            int st = status;
            String tx = text;
            s.later(() -> {
                s.requests--;
                if (cb != null) s.call(cb, LuaValue.valueOf(st), LuaValue.valueOf(tx));
            });
        });
    }

    private static LuaTable http(Script s) {
        LuaTable t = new LuaTable();
        t.set("get", act(a -> request(s, "GET", a)));
        t.set("post", act(a -> request(s, "POST", a)));
        return t;
    }
}
