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

import java.nio.ByteBuffer;

final class ShaderSkyUniforms {
    private ShaderSkyUniforms() {}

    static void write(
            ByteBuffer data,
            SkyShader w,
            float seconds,
            int width,
            int height,
            float cameraYaw,
            float cameraPitch,
            float fov,
            float fogStart,
            float fogEnd,
            float fogR,
            float fogG,
            float fogB,
            float fogAlpha) {
        float yaw = (float) Math.toRadians((double) cameraYaw);
        float pitch = (float) Math.toRadians((double) cameraPitch);
        float cy = (float) Math.cos((double) (-yaw));
        float sy = (float) Math.sin((double) (-yaw));
        float cp = (float) Math.cos((double) (-pitch));
        float sp = (float) Math.sin((double) (-pitch));
        data.clear();
        vec(data, seconds, (float) width, (float) height, w.speed.f());
        colour(data, w.color.argb(), w.opacity.f());
        vec(data, w.size.f(), w.intensivnost.f(), fov, 0.0F);
        vec(data, cy, 0.0F, -sy, 0.0F);
        vec(data, sy * sp, cp, cy * sp, 0.0F);
        vec(data, -sy * cp, sp, -cy * cp, 0.0F);
        switch ((String) w.palitraNeona.get()) {
            case "Тёмные":
                gray(data, 0.1F);
                gray(data, 0.25F);
                gray(data, 0.45F);
                break;
            case "Светлые":
                gray(data, 0.8F);
                gray(data, 0.95F);
                gray(data, 1.0F);
                break;
            case "Свои":
                colour(data, w.neonColor1.argb(), 0.0F);
                colour(data, w.neonColor2.argb(), 0.0F);
                colour(data, w.neonColor3.argb(), 0.0F);
                break;
            default:
                vec(data, 1.0F, 0.0F, 0.0F, 0.0F);
                vec(data, 0.0F, 1.0F, 0.0F, 0.0F);
                vec(data, 0.0F, 0.0F, 1.0F, 0.0F);
        }

        vec(data, fogStart, fogEnd, 0.0F, 0.0F);
        vec(data, fogR, fogG, fogB, fogAlpha);
        data.flip();
    }

    private static void vec(ByteBuffer b, float x, float y, float z, float w) {
        b.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
    }

    private static void colour(ByteBuffer b, int c, float alpha) {
        vec(
                b,
                (float) (c >> 16 & 255) * 0.003921569F,
                (float) (c >> 8 & 255) * 0.003921569F,
                (float) (c & 255) * 0.003921569F,
                alpha);
    }

    private static void gray(ByteBuffer b, float v) {
        vec(b, v, v, v, 0.0F);
    }
}
