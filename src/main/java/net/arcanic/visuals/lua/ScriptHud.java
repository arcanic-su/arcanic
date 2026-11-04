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

import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.hud.HudModule;
import net.minecraft.client.gui.GuiGraphics;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;

/**
 * A HUD element made by a script. It is dragged and scaled in the HUD editor like ours; the script draws in its own
 * units (1080p pixels), the element's scale is applied on top.
 */
final class ScriptHud extends HudModule {
    final Script owner;
    final LuaFunction draw;
    int baseW, baseH;

    ScriptHud(
            Script owner,
            String id,
            String name,
            String description,
            LuaFunction draw,
            int w,
            int h,
            double x,
            double y) {
        super(id, name, description, false, x, y);
        this.owner = owner;
        this.draw = draw;
        this.baseW = w;
        this.baseH = h;
    }

    <S extends Setting<?>> S setting(S s) {
        for (Setting<?> had : settings) {
            if (had.key.equals(s.key)) throw new LuaError("настройка " + s.key + " уже есть");
        }
        add(s);
        ScriptModules.restore(this, s);
        return s;
    }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        this.w = S(baseW);
        this.h = S(baseH);
        if (!owner.running()) return;
        GuiGraphics was = Scripts.graphics;
        Scripts.graphics = g;
        g.pose().pushMatrix();
        g.pose().scale(k, k);
        try {
            owner.call(draw, LuaValue.valueOf(editing), LuaValue.valueOf(baseW), LuaValue.valueOf(baseH));
        } finally {
            g.pose().popMatrix();
            Scripts.graphics = was;
        }
    }

    @Override
    protected void onEnable() {
        ScriptModules.changed();
    }

    @Override
    protected void onDisable() {
        ScriptModules.changed();
    }
}
