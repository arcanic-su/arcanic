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

import net.arcanic.visuals.mixin.GameRendererAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/** World point -> screen coordinates through the main camera. */
public final class Projection {
    private Projection() {}

    /** {x, y, depth} in GUI pixels, or null when behind the camera. */
    public static double[] toScreen(Vec3 p, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 rel = p.subtract(cam.position());
        Vector3fc f = cam.forwardVector(), u = cam.upVector(), l = cam.leftVector();
        double z = rel.x * f.x() + rel.y * f.y() + rel.z * f.z();
        if (z < .1) return null;
        double x = -(rel.x * l.x() + rel.y * l.y() + rel.z * l.z());
        double y = rel.x * u.x() + rel.y * u.y() + rel.z * u.z();
        double fov = Math.toRadians(fov());
        double k = (h / 2.0) / Math.tan(fov / 2);
        return new double[] {w / 2.0 + x / z * k, h / 2.0 - y / z * k, z};
    }

    /** The field of view the frame is really drawn with. Sprinting, speed effects and the zoom change it. */
    public static float fov() {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return ((GameRendererAccess) mc.gameRenderer).arcanicvisuals$fov(mc.gameRenderer.getMainCamera(), pt, true);
    }
}
