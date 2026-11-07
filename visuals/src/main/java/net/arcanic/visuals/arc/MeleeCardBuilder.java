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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

/** The flat picture of a melee skin for its card in the menu. */
public final class MeleeCardBuilder {
    private static final String LOADER_THREAD = "arc-melee-card";
    private static final String ROOT_BONE = "knife_and_right";
    private static final String ANCHOR_BONE = "righthand_pos";
    private static final String ANCHOR_FALLBACK_BONE = "lefthand_pos";
    private static final Set<String> SERVICE_BONES = Set.of(
            "righthand",
            "lefthand",
            "righthand_pos",
            "lefthand_pos",
            "knife_and_left",
            "constraint",
            "camera",
            "view",
            "positioning");

    private static final float MODEL_UNIT = 16.0F;
    private static final int STACK_DEPTH = 32;
    public static final int CORNERS = 4;
    public static final int VERTEX_STRIDE = 4;

    public static final int QUAD_STRIDE = 16;
    private static final int SOURCE_VERTEX_STRIDE = 5;

    private static final int SOURCE_STRIDE = 20;
    private static final int PROJECTED_STRIDE = 17;

    private static final int DEPTH_INDEX = 16;
    private static final int SOURCE_U = 3;
    private static final int SOURCE_V = 4;
    private static final int AXIS_COUNT = 3;
    private static final int AXIS_SUM = 3;
    private static final float BLADE_ANGLE_RADIANS = (float) Math.toRadians(45.0);
    private static final float TILT_RADIANS = (float) Math.toRadians(14.0);
    private static final int OCCLUSION_RESOLUTION = 320;
    private static final float OCCLUSION_FILL = 0.92F;
    private static final float OCCLUSION_CENTER = 160.0F;
    private static final float BARYCENTRIC_EPSILON = -0.002F;
    private static final float DEGENERATE_EPSILON = 1.0E-9F;
    private static final int NO_OWNER = -1;
    private static final MeleeCardMesh EMPTY = new MeleeCardMesh(new float[0], 0.0F, 0.0F);
    private static final Map<String, MeleeCardMesh> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, LOADER_THREAD);
        thread.setDaemon(true);
        return thread;
    });

    private MeleeCardBuilder() {}

    public static MeleeCardMesh get(MeleeDesign design) {
        if (design == null) return null;
        String key = cacheKey(design);
        MeleeCardMesh mesh = CACHE.get(key);
        if (mesh == null) {
            schedule(design, key);
            return null;
        }
        return mesh.quads().length == 0 ? null : mesh;
    }

    private static String cacheKey(MeleeDesign design) {
        List<MeleeVariant> variants = design.getVariants();
        MeleeVariant variant = variants.isEmpty() ? MeleeVariant.DEFAULT : variants.get(0);
        return design.getGeometryKey() + variant.suffix();
    }

    private static void schedule(MeleeDesign design, String key) {
        if (!LOADING.add(key)) return;
        LOADER.execute(() -> {
            try {
                CACHE.put(key, build(design));
            } catch (Exception e) {
                CACHE.put(key, EMPTY);
                LoggerFactory.getLogger("arcanicvisuals").warn("Melee preview failed: " + key, e);
            } finally {
                LOADING.remove(key);
            }
        });
    }

    private static MeleeCardMesh build(MeleeDesign design) {
        GeoParts parts = MeleeModels.definition(design);
        if (parts == null) return EMPTY;
        Vector3f anchor = findAnchor(parts);
        hideBones(parts, design);
        List<float[]> quads = new ArrayList<>();
        Matrix4fStack stack = new Matrix4fStack(STACK_DEPTH);
        Vector3f scratch = new Vector3f();
        GeoPart root = parts.findPart(ROOT_BONE).orElse(null);
        if (root != null) {
            collect(root, stack, quads, scratch);
        } else {
            for (GeoPart part : parts.roots) collect(part, stack, quads, scratch);
        }
        return quads.isEmpty() ? EMPTY : project(quads, anchor);
    }

    private static void hideBones(GeoParts parts, MeleeDesign design) {
        for (String bone : SERVICE_BONES) parts.findPart(bone).ifPresent(part -> part.setHidden(true));
        List<MeleeVariant> variants = design.getVariants();
        if (variants.isEmpty()) return;
        for (String bone : variants.get(0).hiddenBones()) parts.findPart(bone).ifPresent(part -> part.setHidden(true));
    }

    private static Vector3f findAnchor(GeoParts parts) {
        Vector3f right = locateBone(parts, ANCHOR_BONE);
        return right != null ? right : locateBone(parts, ANCHOR_FALLBACK_BONE);
    }

    private static Vector3f locateBone(GeoParts parts, String name) {
        Vector3f out = new Vector3f();
        for (GeoPart root : parts.roots) {
            if (locateBone(root, name, new Matrix4fStack(STACK_DEPTH), out)) return out;
        }
        return null;
    }

    private static boolean locateBone(GeoPart part, String name, Matrix4fStack stack, Vector3f out) {
        stack.pushMatrix();
        applyPart(part, stack);
        if (part.getName().equals(name)) {
            stack.transformPosition(
                    part.getPivotX() / MODEL_UNIT, part.getPivotY() / MODEL_UNIT, part.getPivotZ() / MODEL_UNIT, out);
            stack.popMatrix();
            return true;
        }
        for (GeoPart child : part.children) {
            if (locateBone(child, name, stack, out)) {
                stack.popMatrix();
                return true;
            }
        }
        stack.popMatrix();
        return false;
    }

    private static void collect(GeoPart part, Matrix4fStack stack, List<float[]> quads, Vector3f scratch) {
        if (part.isHidden()) return;
        stack.pushMatrix();
        applyPart(part, stack);
        for (GeoModel cube : part.cubes) {
            stack.pushMatrix();
            applyCube(cube, stack);
            for (GeoQuad face : cube.faces) {
                if (face == null) continue;
                float[] quad = new float[SOURCE_STRIDE];
                for (int c = 0; c < CORNERS; c++) {
                    GeoVertex corner = face.corners[c];
                    stack.transformPosition(corner.position.x, corner.position.y, corner.position.z, scratch);
                    int at = c * SOURCE_VERTEX_STRIDE;
                    quad[at] = scratch.x();
                    quad[at + 1] = scratch.y();
                    quad[at + 2] = scratch.z();
                    quad[at + SOURCE_U] = corner.u;
                    quad[at + SOURCE_V] = corner.v;
                }
                quads.add(quad);
            }
            stack.popMatrix();
        }
        for (GeoPart child : part.children) collect(child, stack, quads, scratch);
        stack.popMatrix();
    }

    private static void applyPart(GeoPart part, Matrix4fStack stack) {
        stack.translate(
                -part.getOffsetX() / MODEL_UNIT, part.getOffsetY() / MODEL_UNIT, part.getOffsetZ() / MODEL_UNIT);
        stack.translate(part.getPivotX() / MODEL_UNIT, part.getPivotY() / MODEL_UNIT, part.getPivotZ() / MODEL_UNIT);
        if (part.getRotZ() != 0.0F) stack.rotateZ(part.getRotZ());
        if (part.getRotY() != 0.0F) stack.rotateY(part.getRotY());
        if (part.getRotX() != 0.0F) stack.rotateX(part.getRotX());
        stack.scale(part.getScaleX(), part.getScaleY(), part.getScaleZ());
        stack.translate(-part.getPivotX() / MODEL_UNIT, -part.getPivotY() / MODEL_UNIT, -part.getPivotZ() / MODEL_UNIT);
    }

    private static void applyCube(GeoModel cube, Matrix4fStack stack) {
        stack.translate(cube.pivot.x / MODEL_UNIT, cube.pivot.y / MODEL_UNIT, cube.pivot.z / MODEL_UNIT);
        if (cube.rotation.z != 0.0F) stack.rotateZ(cube.rotation.z);
        if (cube.rotation.y != 0.0F) stack.rotateY(cube.rotation.y);
        if (cube.rotation.x != 0.0F) stack.rotateX(cube.rotation.x);
        stack.translate(-cube.pivot.x / MODEL_UNIT, -cube.pivot.y / MODEL_UNIT, -cube.pivot.z / MODEL_UNIT);
    }

    private static MeleeCardMesh project(List<float[]> quads, Vector3f anchor) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] quad : quads) {
            for (int c = 0; c < CORNERS; c++) {
                int at = c * SOURCE_VERTEX_STRIDE;
                for (int axis = 0; axis < AXIS_COUNT; axis++) {
                    float value = quad[at + axis];
                    min[axis] = Math.min(min[axis], value);
                    max[axis] = Math.max(max[axis], value);
                }
            }
        }
        float[] center = new float[AXIS_COUNT];
        float[] size = new float[AXIS_COUNT];
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            center[axis] = (min[axis] + max[axis]) * 0.5F;
            size[axis] = max[axis] - min[axis];
        }

        int along = size[0] >= size[1] ? (size[0] >= size[2] ? 0 : 2) : (size[1] >= size[2] ? 1 : 2);
        int thin = size[0] <= size[1] ? (size[0] <= size[2] ? 0 : 2) : (size[1] <= size[2] ? 1 : 2);
        if (thin == along) thin = (along + 1) % AXIS_COUNT;
        int across = AXIS_SUM - along - thin;
        float sign = handleSign(quads, anchor, center, along);

        // the blade angle in the card's plane, then the tilt back into depth
        float bladeCos = (float) Math.cos(BLADE_ANGLE_RADIANS);
        float bladeSin = (float) Math.sin(BLADE_ANGLE_RADIANS);
        float tiltCos = (float) Math.cos(TILT_RADIANS);
        float tiltSin = (float) Math.sin(TILT_RADIANS);
        float xAlong = bladeCos;
        float yAlong = bladeSin;
        float xAcross = -bladeSin * tiltCos;
        float yAcross = bladeCos * tiltCos;
        float depthAcross = tiltSin;
        float xDepth = bladeSin * tiltSin;
        float yDepth = -bladeCos * tiltSin;
        float depthDepth = tiltCos;

        List<float[]> projected = new ArrayList<>(quads.size());
        for (float[] quad : quads) {
            float[] out = new float[PROJECTED_STRIDE];
            float depth = 0.0F;
            for (int c = 0; c < CORNERS; c++) {
                int at = c * SOURCE_VERTEX_STRIDE;
                float a = (quad[at + along] - center[along]) * sign;
                float b = (quad[at + across] - center[across]) * sign;
                float d = quad[at + thin] - center[thin];
                int to = c * VERTEX_STRIDE;
                out[to] = a * xAlong + b * xAcross + d * xDepth;
                out[to + 1] = -(a * yAlong + b * yAcross + d * yDepth);
                out[to + 2] = quad[at + SOURCE_U];
                out[to + 3] = quad[at + SOURCE_V];
                depth += b * depthAcross + d * depthDepth;
            }
            out[DEPTH_INDEX] = depth / 4.0F;
            projected.add(out);
        }
        projected.sort((first, second) -> Float.compare(first[DEPTH_INDEX], second[DEPTH_INDEX]));
        return assemble(projected, cull(projected));
    }

    private static float handleSign(List<float[]> quads, Vector3f anchor, float[] center, int axis) {
        if (anchor == null) return 1.0F;
        float[] lowSum = new float[AXIS_COUNT];
        float[] highSum = new float[AXIS_COUNT];
        int lowCount = 0;
        int highCount = 0;
        for (float[] quad : quads) {
            for (int c = 0; c < CORNERS; c++) {
                int at = c * SOURCE_VERTEX_STRIDE;
                boolean low = quad[at + axis] < center[axis];
                float[] sum = low ? lowSum : highSum;
                for (int k = 0; k < AXIS_COUNT; k++) sum[k] += quad[at + k];
                if (low) lowCount++;
                else highCount++;
            }
        }
        if (lowCount == 0 || highCount == 0) return 1.0F;
        return distance(lowSum, lowCount, anchor) <= distance(highSum, highCount, anchor) ? 1.0F : -1.0F;
    }

    private static float distance(float[] sum, int count, Vector3f point) {
        float dx = sum[0] / count - point.x();
        float dy = sum[1] / count - point.y();
        float dz = sum[2] / count - point.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean[] cull(List<float[]> quads) {
        boolean[] visible = new boolean[quads.size()];
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (float[] quad : quads) {
            for (int c = 0; c < CORNERS; c++) {
                int at = c * VERTEX_STRIDE;
                minX = Math.min(minX, quad[at]);
                maxX = Math.max(maxX, quad[at]);
                minY = Math.min(minY, quad[at + 1]);
                maxY = Math.max(maxY, quad[at + 1]);
            }
        }
        float extent = Math.max(maxX - minX, maxY - minY);
        if (extent <= 0.0F) return visible;

        float scale = OCCLUSION_RESOLUTION * OCCLUSION_FILL / extent;
        float centerX = (minX + maxX) * 0.5F;
        float centerY = (minY + maxY) * 0.5F;
        float[] depthBuffer = new float[OCCLUSION_RESOLUTION * OCCLUSION_RESOLUTION];
        int[] owners = new int[OCCLUSION_RESOLUTION * OCCLUSION_RESOLUTION];
        Arrays.fill(depthBuffer, -Float.MAX_VALUE);
        Arrays.fill(owners, NO_OWNER);
        float[] screen = new float[CORNERS * 2];
        for (int q = 0; q < quads.size(); q++) {
            float[] quad = quads.get(q);
            for (int c = 0; c < CORNERS; c++) {
                int at = c * VERTEX_STRIDE;
                screen[c * 2] = (quad[at] - centerX) * scale + OCCLUSION_CENTER;
                screen[c * 2 + 1] = (quad[at + 1] - centerY) * scale + OCCLUSION_CENTER;
            }
            float depth = quad[DEPTH_INDEX];
            rasterize(screen, 0, 1, 2, depth, q, depthBuffer, owners);
            rasterize(screen, 0, 2, 3, depth, q, depthBuffer, owners);
        }
        for (int owner : owners) {
            if (owner != NO_OWNER) visible[owner] = true;
        }
        return visible;
    }

    private static void rasterize(
            float[] screen, int a, int b, int c, float depth, int owner, float[] depthBuffer, int[] owners) {
        float x0 = screen[a * 2];
        float y0 = screen[a * 2 + 1];
        float x1 = screen[b * 2];
        float y1 = screen[b * 2 + 1];
        float x2 = screen[c * 2];
        float y2 = screen[c * 2 + 1];
        float area = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
        if (Math.abs(area) < DEGENERATE_EPSILON) return;
        int fromX = Math.max(0, (int) Math.min(x0, Math.min(x1, x2)));
        int toX = Math.min(OCCLUSION_RESOLUTION - 1, (int) Math.max(x0, Math.max(x1, x2)) + 1);
        int fromY = Math.max(0, (int) Math.min(y0, Math.min(y1, y2)));
        int toY = Math.min(OCCLUSION_RESOLUTION - 1, (int) Math.max(y0, Math.max(y1, y2)) + 1);
        for (int y = fromY; y <= toY; y++) {
            float py = y + 0.5F;
            for (int x = fromX; x <= toX; x++) {
                float px = x + 0.5F;
                float w0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / area;
                float w1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / area;
                float w2 = 1.0F - w0 - w1;
                if (w0 < BARYCENTRIC_EPSILON || w1 < BARYCENTRIC_EPSILON || w2 < BARYCENTRIC_EPSILON) continue;
                int pixel = y * OCCLUSION_RESOLUTION + x;
                if (depth >= depthBuffer[pixel]) {
                    depthBuffer[pixel] = depth;
                    owners[pixel] = owner;
                }
            }
        }
    }

    private static MeleeCardMesh assemble(List<float[]> quads, boolean[] visible) {
        int count = 0;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int q = 0; q < quads.size(); q++) {
            if (!visible[q]) continue;
            count++;
            float[] quad = quads.get(q);
            for (int c = 0; c < CORNERS; c++) {
                int at = c * VERTEX_STRIDE;
                minX = Math.min(minX, quad[at]);
                maxX = Math.max(maxX, quad[at]);
                minY = Math.min(minY, quad[at + 1]);
                maxY = Math.max(maxY, quad[at + 1]);
            }
        }
        if (count == 0) return EMPTY;

        float centerX = (minX + maxX) * 0.5F;
        float centerY = (minY + maxY) * 0.5F;
        float[] out = new float[count * QUAD_STRIDE];
        int to = 0;
        for (int q = 0; q < quads.size(); q++) {
            if (!visible[q]) continue;
            float[] quad = quads.get(q);
            for (int c = 0; c < CORNERS; c++) {
                int at = c * VERTEX_STRIDE;
                out[to] = quad[at] - centerX;
                out[to + 1] = quad[at + 1] - centerY;
                out[to + 2] = quad[at + 2];
                out[to + 3] = quad[at + 3];
                to += VERTEX_STRIDE;
            }
        }
        return new MeleeCardMesh(out, maxX - minX, maxY - minY);
    }
}
