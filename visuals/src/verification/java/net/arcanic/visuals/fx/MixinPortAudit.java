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

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Starts Fabric's class loader without the game window and applies every mixin to its target (Mixin's own audit).
 * A mixin whose target method or injection point is wrong fails here instead of in the game.
 */
public final class MixinPortAudit {
    public static void main(String[] args) {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/mixin-audit/mods"));
            ClassLoader loader = new Knot(EnvType.CLIENT)
                    .init(new String[] {"--gameDir", "build/mixin-audit", "--version", "1.21.11"});
            Thread.currentThread().setContextClassLoader(loader);
            MixinEnvironment.getCurrentEnvironment().audit();
            System.out.println("MIXIN AUDIT PASSED");
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}
