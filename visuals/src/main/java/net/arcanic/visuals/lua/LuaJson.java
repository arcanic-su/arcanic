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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Java values and JSON to Lua and back. A table with keys 1..n is an array, any other table an object. */
final class LuaJson {
    private LuaJson() {}

    private static final int MAX_DEPTH = 32;

    static LuaValue toLua(Object o) {
        if (o == null) return LuaValue.NIL;
        if (o instanceof LuaValue v) return v;
        if (o instanceof Boolean b) return LuaValue.valueOf(b);
        if (o instanceof Integer i) return LuaValue.valueOf(i);
        if (o instanceof Long l) return LuaValue.valueOf((double) l);
        if (o instanceof Number n) return LuaValue.valueOf(n.doubleValue());
        if (o instanceof JsonElement e) return toLua(e, 0);
        if (o instanceof List<?> list) {
            LuaTable t = new LuaTable();
            for (int i = 0; i < list.size(); i++) t.set(i + 1, toLua(list.get(i)));
            return t;
        }
        if (o instanceof Map<?, ?> map) {
            LuaTable t = new LuaTable();
            for (Map.Entry<?, ?> e : map.entrySet()) t.set(String.valueOf(e.getKey()), toLua(e.getValue()));
            return t;
        }
        return LuaValue.valueOf(String.valueOf(o));
    }

    private static LuaValue toLua(JsonElement e, int depth) {
        if (depth > MAX_DEPTH) throw new LuaError("JSON слишком глубокий");
        if (e == null || e.isJsonNull()) return LuaValue.NIL;
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return LuaValue.valueOf(p.getAsBoolean());
            if (p.isNumber()) return LuaValue.valueOf(p.getAsDouble());
            return LuaValue.valueOf(p.getAsString());
        }
        LuaTable t = new LuaTable();
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) t.set(i + 1, toLua(a.get(i), depth + 1));
        } else {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                t.set(en.getKey(), toLua(en.getValue(), depth + 1));
            }
        }
        return t;
    }

    static JsonElement toJson(LuaValue v) {
        return toJson(v, 0);
    }

    private static JsonElement toJson(LuaValue v, int depth) {
        if (depth > MAX_DEPTH) throw new LuaError("таблица слишком глубокая (или ссылается сама на себя)");
        if (v.isnil()) return JsonNull.INSTANCE;
        if (v.isboolean()) return new JsonPrimitive(v.toboolean());
        if (v.isnumber()) {
            double d = v.todouble();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return new JsonPrimitive((long) d);
            return new JsonPrimitive(d);
        }
        if (v.isstring()) return new JsonPrimitive(v.tojstring());
        if (!v.istable()) throw new LuaError("в JSON нельзя положить " + v.typename());
        LuaTable t = v.checktable();
        int n = t.length();
        boolean array = n > 0;
        if (array) {
            LuaValue k = LuaValue.NIL;
            int count = 0;
            while (true) {
                Varargs next = t.next(k);
                if ((k = next.arg1()).isnil()) break;
                count++;
            }
            array = count == n;
        }
        if (array) {
            JsonArray a = new JsonArray();
            for (int i = 1; i <= n; i++) a.add(toJson(t.get(i), depth + 1));
            return a;
        }
        JsonObject o = new JsonObject();
        LuaValue k = LuaValue.NIL;
        while (true) {
            Varargs next = t.next(k);
            if ((k = next.arg1()).isnil()) break;
            o.add(k.tojstring(), toJson(next.arg(2), depth + 1));
        }
        return o;
    }
}
