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

import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.mixin.GameRendererAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public final class Projection {
    private static final Matrix4f BOB = new Matrix4f();

    private Projection() {}

    public static void bob(Matrix4fc m) {
        BOB.set(m);
    }

    public static double[] toScreen(Vec3 p, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 rel = p.subtract(cam.position());
        Vector3fc f = cam.forwardVector();
        Vector3fc u = cam.upVector();
        Vector3fc l = cam.leftVector();
        double z0 = rel.x * (double) f.x() + rel.y * (double) f.y() + rel.z * (double) f.z();
        double x0 = -(rel.x * (double) l.x() + rel.y * (double) l.y() + rel.z * (double) l.z());
        double y0 = rel.x * (double) u.x() + rel.y * (double) u.y() + rel.z * (double) u.z();
        Vector4f v = BOB.transform(new Vector4f((float) x0, (float) y0, (float) (-z0), 1.0F));
        double z = (double) (-v.z);
        double x = (double) v.x;
        double y = (double) v.y;
        if (z < 0.1) {
            return null;
        } else {
            double fov = Math.toRadians((double) fov());
            double k = (double) h / 2.0 / Math.tan(fov / 2.0);
            float window = (float) w / (float) Math.max(1, h);
            double kx = k * (double) window / (double) Modules.ASPECT.now(window);
            return new double[] {(double) w / 2.0 + x / z * kx, (double) h / 2.0 - y / z * k, z};
        }
    }

    public static float fov() {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return ((GameRendererAccess) mc.gameRenderer).arcanicvisuals$fov(mc.gameRenderer.getMainCamera(), pt, true);
    }
}
