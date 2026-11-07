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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.arcanic.visuals.core.Module;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
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
    private static final int CALL_BUDGET = 400; // 400k
    private static final int MAX_SOURCE = 512 * 1024;
    private static final int MAX_TIMERS = 256;
    private static final Gson GSON = new Gson();

    private final Path file;
    private final String name;
    private Globals globals;
    private final Map<String, List<LuaFunction>> handlers = new HashMap<>();
    private final Map<Integer, List<LuaFunction>> binds = new HashMap<>();
    private final List<Timer> timers = new ArrayList<>();
    private final Queue<Runnable> later = new ConcurrentLinkedQueue<>();
    final List<Module> modules = new ArrayList<>();
    private boolean running;
    private boolean loading;
    private String error = "";
    private int used;
    private int budget;
    private int depth;
    private int nextTimer = 1;
    int requests;
    long minuteStart;
    int minuteCount;
    private JsonObject storage;
    private boolean storageDirty;

    private record Timer(int id, long every, LuaFunction fn, long[] at) {}

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

    boolean loading() {
        return loading;
    }

    boolean listens(String event) {
        List<LuaFunction> l = handlers.get(event);
        return l != null && !l.isEmpty();
    }

    void start() {
        stop();
        error = "";
        try {
            if (Files.size(file) > MAX_SOURCE) throw new IOException("файл больше 512 КБ");
            String source = Files.readString(file, StandardCharsets.UTF_8);
            globals = sandbox();
            LuaValue chunk = globals.load(source, "@" + name);
            running = true;
            loading = true;
            run(LOAD_BUDGET, chunk::call);
            loading = false;
            if (running) {
                for (Module m : List.copyOf(modules)) ScriptModules.resume(m);
                Scripts.log(name, "запущен");
            }
        } catch (LuaError e) {
            fail(e.getMessage());
        } catch (IOException | RuntimeException e) {
            fail(String.valueOf(e.getMessage()));
        }
    }

    void stop() {
        if (running && listens("unload")) emit("unload");
        // switched off while the script still runs, so their onDisable is called; the saved state stays as it was
        for (Module m : List.copyOf(modules)) {
            ScriptModules.unregister(m);
            if (m.enabled()) m.setEnabled(false);
        }
        modules.clear();
        flush();
        drop();
    }

    private void drop() {
        running = false;
        loading = false;
        for (Module m : modules) {
            ScriptModules.unregister(m);
            if (m.enabled()) m.setEnabled(false);
        }
        modules.clear();
        handlers.clear();
        binds.clear();
        timers.clear();
        later.clear();
        globals = null;
    }

    private void fail(String why) {
        error = why == null || why.isBlank() ? "ошибка" : why;
        Scripts.log(name, "остановлен: " + error);
        flush();
        drop();
    }

    /**
     * Calls every handler of the event. False when one of them returned false: for "chat" and "send" that cancels the
     * message.
     */
    boolean emit(String event, Object... args) {
        List<LuaFunction> list = handlers.get(event);
        if (list == null || list.isEmpty() || !running) return true;
        LuaValue[] values = new LuaValue[args.length];
        for (int i = 0; i < args.length; i++) values[i] = LuaJson.toLua(args[i]);
        boolean allowed = true;
        for (LuaFunction f : List.copyOf(list)) {
            Varargs r = call(f, values);
            if (r != null && r.arg1().isboolean() && !r.arg1().toboolean()) allowed = false;
            if (!running) break;
        }
        return allowed;
    }

    /** Runs a function of this script with the budget; null when it failed (the script is stopped then). */
    Varargs call(LuaFunction f, LuaValue... args) {
        if (!running) return null;
        try {
            Varargs[] out = new Varargs[1];
            run(CALL_BUDGET, () -> out[0] = f.invoke(LuaValue.varargsOf(args)));
            return out[0];
        } catch (LuaError e) {
            fail(e.getMessage());
        } catch (RuntimeException e) {
            fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return null;
    }

    private void run(int hooks, Runnable code) {
        // nested calls (a module switched from the script, a HUD element drawn from it) share the outer budget
        if (depth == 0) {
            used = 0;
            budget = hooks;
        }
        depth++;
        try {
            code.run();
        } catch (StackOverflowError e) {
            throw new LuaError("слишком глубокая рекурсия");
        } finally {
            depth--;
        }
    }

    void on(String event, LuaFunction f) {
        handlers.computeIfAbsent(event, k -> new ArrayList<>()).add(f);
    }

    void off(String event, LuaFunction f) {
        List<LuaFunction> l = handlers.get(event);
        if (l != null) l.remove(f);
    }

    void bind(int code, LuaFunction f) {
        binds.computeIfAbsent(code, k -> new ArrayList<>()).add(f);
    }

    void key(int code) {
        List<LuaFunction> l = binds.get(code);
        if (l != null) for (LuaFunction f : List.copyOf(l)) if (running) call(f);
    }

    // ---- timers and callbacks from other threads

    int timer(long delay, boolean repeat, LuaFunction fn) {
        if (timers.size() >= MAX_TIMERS) throw new LuaError("больше " + MAX_TIMERS + " таймеров");
        long every = repeat ? Math.max(50, delay) : 0;
        Timer t = new Timer(nextTimer++, every, fn, new long[] {System.currentTimeMillis() + Math.max(0, delay)});
        timers.add(t);
        return t.id();
    }

    void cancel(int id) {
        timers.removeIf(t -> t.id() == id);
    }

    /** Runs a callback on the game thread at the next tick, if the script is still running then. */
    void later(Runnable r) {
        later.add(r);
    }

    void tick() {
        Runnable r;
        while (running && (r = later.poll()) != null) r.run();
        long now = System.currentTimeMillis();
        for (Iterator<Timer> it = List.copyOf(timers).iterator(); it.hasNext() && running; ) {
            Timer t = it.next();
            if (now < t.at()[0]) continue;
            if (t.every() > 0) t.at()[0] = now + t.every();
            else timers.remove(t);
            call(t.fn());
        }
        if (running) emit("tick");
        flush();
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
        g.set("SCRIPT", name);
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
        return e == null ? LuaValue.NIL : LuaJson.toLua(e);
    }

    LuaValue keys() {
        return LuaJson.toLua(new ArrayList<>(storage().keySet()));
    }

    void store(String key, LuaValue v) {
        if (key.length() > 64) throw new LuaError("ключ длиннее 64 символов");
        JsonObject s = storage();
        if (v.isnil()) s.remove(key);
        else s.add(key, LuaJson.toJson(v));
        if (s.size() > 512) throw new LuaError("в storage больше 512 ключей");
        if (GSON.toJson(s).length() > 1 << 20) throw new LuaError("storage больше 1 МБ");
        storageDirty = true;
    }

    void clearStorage() {
        storage().entrySet().clear();
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
