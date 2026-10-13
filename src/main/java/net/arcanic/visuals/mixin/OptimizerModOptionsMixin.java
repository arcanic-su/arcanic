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
package net.arcanic.visuals.mixin;

import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The bundled optimiser mod is run by our Optimizer alone (fx/OptimizerMod): its page of switches is not added to
 * Sodium's video settings.
 */
@Pseudo
@Mixin(targets = "net.arcanic.optimizer.SodiumOptions", remap = false)
public abstract class OptimizerModOptionsMixin {
    @Inject(method = "registerConfigLate", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void arcanicvisuals$noPage(CallbackInfo ci) {
        LoggerFactory.getLogger("arcanicvisuals")
                .info("optimiser mod: its settings page is left out, the optimiser module runs it");
        ci.cancel();
    }
}
