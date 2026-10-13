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
package net.arcanic.visuals.arc;

/** A cape as a chain of 16 points joined by sticks of length 1 (Verlet-style). */
public class ClothSimulation {
    public static final int PART_COUNT = 16;
    private static final int NUM_ITERATIONS = 3;
    private static final float MAX_BEND = 20.0F;
    private static final float INITIAL_STICK_LENGTH = 1.0F;
    private static final float DELTA_TIME = 0.05F;
    private static final float HALF_FACTOR = 0.5F;
    private static final float CLIP_FACTOR = 5.0F;
    private static final int MAX_SELF_CLIP_RUNS = 32;
    private static final float MIN_SELF_CLIP_SQR = 0.99F;
    private static final float MIN_MAGNITUDE = 1.0E-4F;
    private static final float ZERO_FLOAT = 0.0F;
    private static final float DEFAULT_GRAVITY_Y = -1.0F;
    private static final double RAD_TO_DEG = 180.0 / Math.PI;
    private static final float BEND_MULTIPLIER = 2.0F;
    public final ClothLerp[] points = new ClothLerp[PART_COUNT];
    private final ClothNode[] sticks = new ClothNode[PART_COUNT - 1];
    private final ClothVec gravityDirection = new ClothVec(ZERO_FLOAT, DEFAULT_GRAVITY_Y, ZERO_FLOAT);
    private float gravity = 0.0F;
    private boolean sneaking = false;
    private boolean built = false;

    /** Builds the chain the first time; true only on that first call. */
    public boolean init() {
        if (built) return false;
        for (int i = 0; i < PART_COUNT; i++) {
            ClothLerp point = new ClothLerp();
            point.position.y = -i;
            point.position.x = -i;
            point.locked = i == 0;
            points[i] = point;
            if (i > 0) sticks[i - 1] = new ClothNode(points[i - 1], point, INITIAL_STICK_LENGTH);
        }
        built = true;
        return true;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public void setGravity(float gravity) {
        this.gravity = gravity;
    }

    public void setGravityDirection(ClothVec direction) {
        gravityDirection.copy(direction);
    }

    public void applyMovement(ClothVec movement) {
        points[0].prevPosition.copy(points[0].position);
        points[0].position.add(movement);
    }

    public float lerpX(int point, float partialTick) {
        return points[point].getLerpX(partialTick);
    }

    public float lerpY(int point, float partialTick) {
        return points[point].getLerpY(partialTick);
    }

    public float lerpZ(int point, float partialTick) {
        return points[point].getLerpZ(partialTick);
    }

    public void simulate() {
        applyGravity();
        preventClipping();
        preventSelfClipping();
        applyMotion();
        preventSelfClipping();
        preventHardBends();
        limitLength();
    }

    private void applyGravity() {
        float step = gravity * DELTA_TIME;
        float gx = gravityDirection.x * step;
        float gy = gravityDirection.y * step;
        float gz = gravityDirection.z * step;
        for (ClothLerp point : points) {
            if (point.locked) continue;
            point.prevPosition.x = point.position.x;
            point.prevPosition.y = point.position.y;
            point.prevPosition.z = point.position.z;
            point.position.x += gx;
            point.position.y += gy;
            point.position.z += gz;
        }
    }

    private void applyMotion() {
        for (int iteration = 0; iteration < NUM_ITERATIONS; iteration++) {
            for (int s = sticks.length - 1; s >= 0; s--) {
                ClothNode stick = sticks[s];
                float cx = (stick.pointA.position.x + stick.pointB.position.x) * HALF_FACTOR;
                float cy = (stick.pointA.position.y + stick.pointB.position.y) * HALF_FACTOR;
                float cz = (stick.pointA.position.z + stick.pointB.position.z) * HALF_FACTOR;
                float dx = stick.pointA.position.x - stick.pointB.position.x;
                float dy = stick.pointA.position.y - stick.pointB.position.y;
                float dz = stick.pointA.position.z - stick.pointB.position.z;
                float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < MIN_MAGNITUDE) {
                    dx = 0.0F;
                    dy = 0.0F;
                    dz = 0.0F;
                } else {
                    dx /= length;
                    dy /= length;
                    dz /= length;
                }
                float half = stick.length * HALF_FACTOR;
                float ox = dx * half;
                float oy = dy * half;
                float oz = dz * half;
                if (!stick.pointA.locked) {
                    stick.pointA.position.x = cx + ox;
                    stick.pointA.position.y = cy + oy;
                    stick.pointA.position.z = cz + oz;
                }
                if (!stick.pointB.locked) {
                    stick.pointB.position.x = cx - ox;
                    stick.pointB.position.y = cy - oy;
                    stick.pointB.position.z = cz - oz;
                }
            }
        }
    }

    private void limitLength() {
        for (ClothNode stick : sticks) {
            if (stick.pointB.locked) continue;
            float dx = stick.pointA.position.x - stick.pointB.position.x;
            float dy = stick.pointA.position.y - stick.pointB.position.y;
            float dz = stick.pointA.position.z - stick.pointB.position.z;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < MIN_MAGNITUDE) {
                dx = 0.0F;
                dy = 0.0F;
                dz = 0.0F;
            } else {
                dx /= length;
                dy /= length;
                dz /= length;
            }
            stick.pointB.position.x = stick.pointA.position.x - dx * stick.length;
            stick.pointB.position.y = stick.pointA.position.y - dy * stick.length;
            stick.pointB.position.z = stick.pointA.position.z - dz * stick.length;
        }
    }

    private void preventSelfClipping() {
        int runs = 0;
        while (true) {
            boolean clipped = false;
            for (int i = 0; i < points.length; i++) {
                for (int j = i + 1; j < points.length; j++) {
                    ClothLerp a = points[i];
                    ClothLerp b = points[j];
                    float dx = a.position.x - b.position.x;
                    float dy = a.position.y - b.position.y;
                    float dz = a.position.z - b.position.z;
                    float distanceSqr = dx * dx + dy * dy + dz * dz;
                    if (distanceSqr >= MIN_SELF_CLIP_SQR) continue;
                    clipped = true;
                    runs++;
                    float distance = (float) Math.sqrt(distanceSqr);
                    if (distance < MIN_MAGNITUDE) {
                        dx = 0.0F;
                        dy = 0.0F;
                        dz = 0.0F;
                    } else {
                        dx /= distance;
                        dy /= distance;
                        dz /= distance;
                    }
                    float cx = (a.position.x + b.position.x) * HALF_FACTOR;
                    float cy = (a.position.y + b.position.y) * HALF_FACTOR;
                    float cz = (a.position.z + b.position.z) * HALF_FACTOR;
                    float ox = dx * HALF_FACTOR;
                    float oy = dy * HALF_FACTOR;
                    float oz = dz * HALF_FACTOR;
                    if (!a.locked) {
                        a.position.x = cx + ox;
                        a.position.y = cy + oy;
                        a.position.z = cz + oz;
                    }
                    if (!b.locked) {
                        b.position.x = cx - ox;
                        b.position.y = cy - oy;
                        b.position.z = cz - oz;
                    }
                }
            }
            if (!clipped || runs >= MAX_SELF_CLIP_RUNS) return;
        }
    }

    private void preventHardBends() {
        for (int i = 1; i < points.length - 2; i++) {
            double angle = getAngle(points[i].position, points[i - 1].position, points[i + 1].position);
            if (angle < -MAX_BEND) {
                replacePoint(
                        points[i + 1].position,
                        points[i].position,
                        points[i - 1].position,
                        -MAX_BEND * BEND_MULTIPLIER);
            } else if (angle > MAX_BEND) {
                replacePoint(
                        points[i + 1].position, points[i].position, points[i - 1].position, MAX_BEND * BEND_MULTIPLIER);
            }
        }
    }

    private void preventClipping() {
        ClothLerp anchor = points[0];
        for (int i = 1; i < points.length; i++) {
            ClothLerp point = points[i];
            if (point.position.x - anchor.position.x > 0.0F) point.position.x = anchor.position.x;
            float t = (float) i / points.length;
            float limit = t * t * CLIP_FACTOR;
            float dz = anchor.position.z - point.position.z;
            if (dz > limit) point.position.z = anchor.position.z - limit;
            if (dz < -limit) point.position.z = anchor.position.z + limit;
        }
    }

    private void replacePoint(ClothVec target, ClothVec joint, ClothVec previous, float degrees) {
        float dx = joint.x - previous.x;
        float dy = joint.y - previous.y;
        float dz = joint.z - previous.z;
        float angle = (float) Math.toRadians(degrees);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float rx = cos * dx - sin * dy;
        float ry = sin * dx + cos * dy;
        target.x = rx + joint.x;
        target.y = ry + joint.y;
        target.z = dz + joint.z;
    }

    private double getAngle(ClothVec point, ClothVec previous, ClothVec next) {
        float ax = previous.x - point.x;
        float ay = previous.y - point.y;
        float bx = previous.x - next.x;
        float by = previous.y - next.y;
        float dot = ax * bx + ay * by;
        float cross = ax * by - ay * bx;
        double angle = Math.atan2(cross, dot);
        return angle * RAD_TO_DEG;
    }
}
