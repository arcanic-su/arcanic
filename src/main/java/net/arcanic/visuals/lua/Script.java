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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;

/**
 * One .lua file in its own globals: base, table, string, math and bit32 only, no io/os/load/require/luajava. Every call
 * into the script gets an instruction budget, so an endless loop stops the script and not the game.
 */
public final class Script {
    /** Budgets are in hook calls, HOOK_EVERY instructions each. */
    private static final int HOOK_EVERY = 1000;

    private static final int LOAD_BUDGET = 5000; // 5M
    private static final int EVENT_BUDGET = 400; // 400k
    private static final int MAX_SOURCE = 512 * 1024;
    private static final Gson GSON = new Gson();

    private final Path file;
    private final String name;
    private Globals globals;
    private final Map<String, List<LuaFunction>> handlers = new HashMap<>();
    private boolean running;
    private String error = "";
    private int used;
    private int budget;
    private JsonObject storage;
    private boolean storageDirty;

    Script(Path file) {
        this.file = file;
        this.name = file.getFileName().toString();
    }

    public String name() {
        return name;
    }

    public boolean running() {
        return running;
    }

    public String error() {
        return error;
    }

    boolean listens(String event) {
        List<LuaFunction> l = handlers.get(event);
        return l != null && !l.isEmpty();
    }

    void start() {
        stop();
        error = "";
        try {
            long size = Files.size(file);
            if (size > MAX_SOURCE) throw new IOException("файл больше 512 КБ");
            String source = Files.readString(file, StandardCharsets.UTF_8);
            globals = sandbox();
            LuaValue chunk = globals.load(source, "@" + name);
            running = true;
            run(LOAD_BUDGET, () -> chunk.call());
            if (running) Scripts.log(name, "запущен");
        } catch (LuaError e) {
            fail(e.getMessage());
        } catch (IOException | RuntimeException e) {
            fail(String.valueOf(e.getMessage()));
        }
    }

    void stop() {
        if (running && listens("unload")) emit("unload");
        flush();
        running = false;
        handlers.clear();
        globals = null;
    }

    private void fail(String why) {
        error = why == null || why.isBlank() ? "ошибка" : why;
        Scripts.log(name, "остановлен: " + error);
        running = false;
        handlers.clear();
        globals = null;
    }

    void emit(String event, Object... args) {
        List<LuaFunction> list = handlers.get(event);
        if (list == null || list.isEmpty() || !running) return;
        LuaValue[] values = new LuaValue[args.length];
        for (int i = 0; i < args.length; i++) values[i] = LuaApi.toLua(args[i]);
        Varargs va = LuaValue.varargsOf(values);
        for (LuaFunction f : List.copyOf(list)) {
            if (!running) return;
            try {
                run(EVENT_BUDGET, () -> f.invoke(va));
            } catch (LuaError e) {
                fail(e.getMessage());
                return;
            } catch (RuntimeException e) {
                fail(e.getClass().getSimpleName() + ": " + e.getMessage());
                return;
            }
        }
    }

    private void run(int hooks, Runnable code) {
        used = 0;
        budget = hooks;
        try {
            code.run();
        } catch (StackOverflowError e) {
            throw new LuaError("слишком глубокая рекурсия");
        }
    }

    void on(String event, LuaFunction f) {
        handlers.computeIfAbsent(event, k -> new ArrayList<>()).add(f);
    }

    // ---- sandbox

    private Globals sandbox() {
        Globals g = new Globals();
        g.load(new JseBaseLib());
        g.load(new PackageLib()); // the libs below need package.loaded
        g.load(new TableLib());
        g.load(new StringLib());
        g.load(new JseMathLib());
        g.load(new Bit32Lib());
        LoadState.install(g);
        LuaC.install(g);
        // instruction budget
        g.load(new DebugLib());
        LuaValue sethook = g.get("debug").get("sethook");
        g.set("debug", LuaValue.NIL);
        LuaValue hook = new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (++used > budget) throw new LuaError("скрипт работает слишком долго (бесконечный цикл?)");
                return NONE;
            }
        };
        sethook.invoke(LuaValue.varargsOf(hook, LuaValue.EMPTYSTRING, LuaValue.valueOf(HOOK_EVERY)));
        for (String unsafe : new String[] {"dofile", "loadfile", "load", "loadstring", "require", "module"}) {
            g.set(unsafe, LuaValue.NIL);
        }
        g.set("package", LuaValue.NIL);
        g.get("string").set("dump", LuaValue.NIL);
        g.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder b = new StringBuilder();
                for (int i = 1; i <= args.narg(); i++) {
                    if (i > 1) b.append('\t');
                    b.append(args.arg(i).tojstring());
                }
                Scripts.log(name, b.toString());
                return NONE;
            }
        });
        LuaTable events = new LuaTable();
        events.set("on", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue event, LuaValue fn) {
                String e = event.checkjstring();
                if (!LuaApi.EVENTS.contains(e)) throw new LuaError("нет события \"" + e + "\"");
                on(e, fn.checkfunction());
                return NONE;
            }
        });
        g.set("events", events);
        LuaApi.install(g, this);
        return g;
    }

    // ---- storage, scripts/data/<name>.json

    private JsonObject storage() {
        if (storage == null) {
            storage = new JsonObject();
            Path f = storageFile();
            try {
                if (Files.isRegularFile(f)) {
                    JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
                    if (e.isJsonObject()) storage = e.getAsJsonObject();
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return storage;
    }

    private Path storageFile() {
        return Scripts.DATA.resolve(name.replaceAll("\\.lua$", "") + ".json");
    }

    LuaValue load(String key) {
        JsonElement e = storage().get(key);
        if (e == null || !e.isJsonPrimitive()) return LuaValue.NIL;
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return LuaValue.valueOf(p.getAsBoolean());
        if (p.isNumber()) return LuaValue.valueOf(p.getAsDouble());
        return LuaValue.valueOf(p.getAsString());
    }

    void store(String key, LuaValue v) {
        JsonObject s = storage();
        if (key.length() > 64) throw new LuaError("ключ длиннее 64 символов");
        if (v.isnil()) s.remove(key);
        else if (v.isboolean()) s.addProperty(key, v.toboolean());
        else if (v.isnumber()) s.addProperty(key, v.todouble());
        else if (v.isstring()) {
            String str = v.tojstring();
            if (str.length() > 4096) throw new LuaError("строка длиннее 4096 символов");
            s.addProperty(key, str);
        } else throw new LuaError("storage хранит только строки, числа и true/false");
        if (s.size() > 256) throw new LuaError("в storage больше 256 ключей");
        storageDirty = true;
    }

    void flush() {
        if (!storageDirty || storage == null) return;
        storageDirty = false;
        try {
            Files.createDirectories(Scripts.DATA);
            Files.writeString(storageFile(), GSON.toJson(storage), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Scripts.log(name, "не удалось сохранить storage: " + e.getMessage());
        }
    }
}
