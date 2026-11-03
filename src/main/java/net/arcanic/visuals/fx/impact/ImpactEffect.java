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
package net.arcanic.visuals.fx.impact;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public abstract class ImpactEffect extends Module {
    protected static final Minecraft mc = Minecraft.getInstance();

    protected ImpactEffect(String id, String name, String description) {
        super(id, name, description, Category.COMBAT, false);
    }

    public void attack(Entity target) {}

    public void world(PoseStack ps, float partial) {}

    public void left() {}

    public abstract void clearStudio();

    protected void onDisable() {
        this.left();
        this.clearStudio();
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    protected final class Choice {
        private final String[] options;
        private final Setting.Mode one;
        private final Setting.Bool[] many;

        Choice(String name, String[] options, int def) {
            this.options = options;
            this.one = (Setting.Mode)
                    ImpactEffect.this.add(new Setting.Mode(ImpactEffect.this.key(name), name, options[def], options));
            this.many = null;
        }

        Choice(String name, String[] options, int[] defs) {
            this.options = options;
            this.one = null;
            this.many = new Setting.Bool[options.length];

            for (int i = 0; i < options.length; ++i) {
                boolean on = false;

                for (int d : defs) {
                    on |= d == i;
                }

                this.many[i] = (Setting.Bool) ImpactEffect.this.add(new Setting.Bool(
                        ImpactEffect.this.key(name) + "." + ImpactEffect.this.key(options[i]),
                        name + ": " + options[i],
                        on));
            }
        }

        public String[] options() {
            return this.options;
        }

        public int index() {
            return this.one == null ? 0 : Math.max(0, this.one.index());
        }

        public boolean is(int i) {
            return this.many != null ? i >= 0 && i < this.many.length && this.many[i].on() : this.index() == i;
        }

        public boolean has(String option) {
            for (int i = 0; i < this.options.length; ++i) {
                if (this.options[i].equals(option)) {
                    return this.is(i);
                }
            }

            return false;
        }

        public Choice showIf(BooleanSupplier when) {
            if (this.one != null) {
                this.one.showIf(when);
            } else {
                for (Setting.Bool b : this.many) {
                    b.showIf(when);
                }
            }

            return this;
        }
    }

    protected final class Toggle {
        private final Setting.Bool b;

        Toggle(String name, boolean def) {
            this.b = (Setting.Bool) ImpactEffect.this.add(new Setting.Bool(ImpactEffect.this.key(name), name, def));
        }

        public boolean on() {
            return this.b.on();
        }

        public Toggle showIf(BooleanSupplier when) {
            this.b.showIf(when);
            return this;
        }
    }

    protected final class Slider {
        private final Setting.Num num;

        Slider(String name, float def, float min, float max, float step) {
            this.num = (Setting.Num) ImpactEffect.this.add(new Setting.Num(
                    ImpactEffect.this.key(name), name, (double) def, (double) min, (double) max, (double) step, ""));
        }

        public float value() {
            return this.num.f();
        }

        public Slider showIf(BooleanSupplier when) {
            this.num.showIf(when);
            return this;
        }
    }

    protected final class Paint {
        private final Setting.Colour c;

        Paint(String name, Color def) {
            this.c = (Setting.Colour)
                    ImpactEffect.this.add(new Setting.Colour(ImpactEffect.this.key(name), name, false, def.getRGB()));
        }

        public Color colour() {
            return new Color(this.c.argb(), true);
        }

        public Paint showIf(BooleanSupplier when) {
            this.c.showIf(when);
            return this;
        }
    }
}
