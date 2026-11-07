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

import java.util.Locale;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import org.luaj.vm2.LuaFunction;

/** A module made by a script: it sits in the menu like ours, with its settings, bind and switch. */
final class ScriptModule extends Module {
    final Script owner;
    LuaFunction onEnable, onDisable;

    ScriptModule(Script owner, String id, String name, String description, Category category) {
        super(id, name, description, category, false);
        this.owner = owner;
    }

    static Category category(String name) {
        return switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case "combat" -> Category.COMBAT;
            case "players" -> Category.PLAYERS;
            case "effects" -> Category.EFFECTS;
            case "hands" -> Category.HANDS;
            case "world" -> Category.WORLD;
            case "utility", "utilities" -> Category.UTILITY;
            case "hud" -> Category.HUD;
            default -> Category.VISUALS;
        };
    }

    <S extends Setting<?>> S setting(S s) {
        for (Setting<?> had : settings) {
            if (had.key.equals(s.key)) throw new org.luaj.vm2.LuaError("настройка " + s.key + " уже есть");
        }
        add(s);
        ScriptModules.restore(this, s);
        return s;
    }

    @Override
    protected void onEnable() {
        if (onEnable != null) owner.call(onEnable);
        ScriptModules.changed();
    }

    @Override
    protected void onDisable() {
        if (onDisable != null) owner.call(onDisable);
        ScriptModules.changed();
    }
}
