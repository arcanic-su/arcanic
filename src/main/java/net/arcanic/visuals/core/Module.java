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
package net.arcanic.visuals.core;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    public int bind = -1;
    public boolean expanded = true;
    public float knob;

    protected Module(String id, String name, String description, Category category, boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabled;
        this.knob = enabled ? 1.0F : 0.0F;
    }

    public String title() {
        return Names.of(this);
    }

    protected <S extends Setting<?>> S add(S s) {
        this.settings.add(s);
        return s;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void setEnabled(boolean on) {
        if (on != this.enabled) {
            this.enabled = on;
            if (on) {
                this.onEnable();
            } else {
                this.onDisable();
            }
        }
    }

    void initial(boolean on) {
        this.enabled = on;
        this.knob = on ? 1.0F : 0.0F;
    }

    public void toggle() {
        this.setEnabled(!this.enabled());
    }

    protected void onEnable() {}

    protected void onDisable() {}

    public void tick() {}

    public boolean modified() {
        for (Setting<?> s : this.settings) {
            if (s.visible() && s.modified()) {
                return true;
            }
        }

        return false;
    }

    public void resetSettings() {
        for (Setting<?> s : this.settings) {
            s.reset();
        }
    }
}
