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

/**
 * Glyph coverage from FreeType is linear, and light text blended over a dark panel comes out thin and greyish at
 * small sizes.
 */
@Mixin(NativeImage.class)
public abstract class GlyphContrastMixin {
    @Shadow
    @Final
    private int width;

    @Shadow
    @Final
    private int height;

    @Shadow
    private long pixels;

    @Shadow
    public abstract NativeImage.Format format();

    private static final byte[] CURVE = new byte[256];

    static {
        for (int i = 0; i < 256; i++) {
            double a = i / 255.0;
            a = Math.max(0, Math.min(1, (a - .04) / .9)); // drop the faint haze, close the core
            a = Math.pow(a, .78); // thicker light-on-dark stems
            CURVE[i] = (byte) Math.round(a * 255);
        }
    }

    @Inject(method = "copyFromFont", at = @At("RETURN"))
    private void arcanic$contrast(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || format().components() != 1 || pixels == 0) return;
        long n = (long) width * height;
        for (long i = 0; i < n; i++) MemoryUtil.memPutByte(pixels + i, CURVE[MemoryUtil.memGetByte(pixels + i) & 0xFF]);
    }
}
