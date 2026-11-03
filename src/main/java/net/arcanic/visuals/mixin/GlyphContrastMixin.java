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

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({NativeImage.class})
public abstract class GlyphContrastMixin {
    @Shadow
    @Final
    private int width;

    @Shadow
    @Final
    private int height;

    @Shadow
    private long pixels;

    private static final byte[] CURVE = new byte[256];

    @Shadow
    public abstract NativeImage.Format format();

    @Inject(
            method = {"copyFromFont"},
            at = {@At("RETURN")})
    private void arcanicvisuals$contrast(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && this.format().components() == 1 && this.pixels != 0L) {
            long n = (long) this.width * (long) this.height;

            for (long i = 0L; i < n; ++i) {
                MemoryUtil.memPutByte(this.pixels + i, CURVE[MemoryUtil.memGetByte(this.pixels + i) & 255]);
            }
        }
    }

    static {
        for (int i = 0; i < 256; ++i) {
            double a = (double) i / 255.0;
            a = Math.max(0.0, Math.min(1.0, (a - 0.04) / 0.9));
            a = Math.pow(a, 0.78);
            CURVE[i] = (byte) ((int) Math.round(a * 255.0));
        }
    }
}
