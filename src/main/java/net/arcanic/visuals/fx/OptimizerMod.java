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
package net.arcanic.visuals.fx;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;

/** The bundled optimiser mod (libs/optimizermod, nested in our jar) follows the Optimizer module and nothing else. */
public final class OptimizerMod {
    private OptimizerMod() {}

    private static final String SCREEN = "net.arcanic.optimizer.OptimizerScreen";
    private static Boolean state;

    public static void set(boolean on) {
        if (state != null && state == on) return;
        state = on;
        try {
            Class<?> config = Class.forName("net.arcanic.optimizer.OptimizerConfig");
            Object values = config.getField("values").get(null);
            Object defaults = values.getClass().getDeclaredConstructor().newInstance();
            for (Field f : values.getClass().getFields()) {
                if (f.getType() != boolean.class) continue;
                String name = f.getName();
                f.setBoolean(
                        values,
                        on
                                && !name.equals("updateCheck")
                                && (name.equals("immediateRingForce") || f.getBoolean(defaults)));
            }
            config.getMethod("refresh").invoke(null);
            config.getMethod("save").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
    }

    public static void guard(Minecraft mc) {
        if (mc.screen != null && SCREEN.equals(mc.screen.getClass().getName())) mc.setScreen(null);
    }
}
