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
package net.arcanic.visuals.fx.lyrics;

import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.Projection;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A flat sheet in the world that a chunk of lyrics is written on: a point plus a right and a down vector, one plane
 * unit long each, and a screen projection of it for placement and visibility checks. The camera, field of view and
 * aspect are read once per frame ({@link #frame}), so projecting a point is plain arithmetic with no allocation. The
 * words themselves are drawn in the world pass with the real camera matrices; this is only for deciding where they go.
 */
public final class WordPlane {
    private double ox, oy, oz;
    private final Vector3f right = new Vector3f();
    private final Vector3f down = new Vector3f();
    private int width = 1, height = 1;
    private double camX, camY, camZ;
    private float fx, fy, fz, ux, uy, uz, lx, ly, lz;
    private double k = 1, kx = 1;

    /** Reads the camera, field of view and aspect for this frame of {@code width} x {@code height} pixels. */
    public void frame(Camera cam, int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        Vec3 p = cam.position();
        camX = p.x;
        camY = p.y;
        camZ = p.z;
        Vector3fc f = cam.forwardVector(), u = cam.upVector(), l = cam.leftVector();
        fx = f.x();
        fy = f.y();
        fz = f.z();
        ux = u.x();
        uy = u.y();
        uz = u.z();
        lx = l.x();
        ly = l.y();
        lz = l.z();
        k = this.height / 2.0 / Math.tan(Math.toRadians(Projection.fov()) / 2.0);
        float window = (float) this.width / this.height;
        kx = k * window / Modules.ASPECT.now(window);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void set(double x, double y, double z, Vector3f right, Vector3f down) {
        this.ox = x;
        this.oy = y;
        this.oz = z;
        this.right.set(right);
        this.down.set(down);
    }

    public void set(Vec3 origin, Vector3f right, Vector3f down) {
        set(origin.x, origin.y, origin.z, right, down);
    }

    /** Projects plane point (u, v) into {@code out} as frame pixels; false when it is behind the camera. */
    public boolean project(float u, float v, float[] out) {
        double rx = ox + right.x * u + down.x * v - camX;
        double ry = oy + right.y * u + down.y * v - camY;
        double rz = oz + right.z * u + down.z * v - camZ;
        double z = rx * fx + ry * fy + rz * fz;
        if (z < 0.1) return false;
        double x = -(rx * lx + ry * ly + rz * lz);
        double y = rx * ux + ry * uy + rz * uz;
        out[0] = (float) (width / 2.0 + x / z * kx);
        out[1] = (float) (height / 2.0 - y / z * k);
        return true;
    }

    /** The world direction through a screen point (0..1, y down), the inverse of {@link #project}. */
    public Vec3 direction(float sx, float sy) {
        double x = (sx * width - width / 2.0) / kx;
        double y = (height / 2.0 - sy * height) / k;
        Vec3 dir = new Vec3(fx - lx * x + ux * y, fy - ly * x + uy * y, fz - lz * x + uz * y);
        return dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, 0.0F) : dir.normalize();
    }
}
