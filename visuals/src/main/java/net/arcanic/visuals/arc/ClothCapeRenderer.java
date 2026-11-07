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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/** Draws a cape as 16 bending segments that follow the player's cloth simulation, with a gentle wind swing. */
public class ClothCapeRenderer {
    private static final int PART_COUNT = 16;
    private static final float ALPHA = 1.0F;
    private static final float WIND_AMPLITUDE = 1.5F;
    private static final float ARMOR_Z_OFFSET = 0.1F;
    private static final int FULL_BRIGHT = 15728880;
    private static final float HALF_WIDTH = 0.3125F;

    private static final float DEPTH = 0.0625F;
    private static final float SEGMENT = 0.0625F;
    private static final float SEGMENT_V = 0.03125F;

    private static ClothCapeRenderer instance;

    public static ClothCapeRenderer getInstance() {
        if (instance == null) instance = new ClothCapeRenderer();
        return instance;
    }

    public void render(
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            AvatarRenderState state,
            AbstractClientPlayer player,
            PlayerModel model) {
        Identifier texture = Capes.resolve(player);
        if (texture == null) return;
        ClothSimulation sim = ClothSimulations.getInstance().simulationFor(player);
        if (sim == null) return;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean underwater = player.isUnderWater();
        pose.pushPose();
        model.root().translateAndRotate(pose);
        model.body.translateAndRotate(pose);
        if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty())
            pose.translate(0.0, 0.0, ARMOR_Z_OFFSET); // over the chestplate
        RenderType type = RenderTypes.entityTranslucent(texture);
        collector.submitCustomGeometry(
                pose, type, (last, consumer) -> renderMesh(last, consumer, sim, partialTick, light, underwater));
        pose.popPose();
    }

    private void renderMesh(
            Pose last, VertexConsumer consumer, ClothSimulation sim, float partialTick, int light, boolean underwater) {
        // at full brightness the faces are lit from the other side, so their normals are flipped
        boolean flip = light == FULL_BRIGHT;
        PoseStack pose = new PoseStack();
        pose.last().pose().set(last.pose());

        // the pose of every segment, and the normals of its front and back faces
        Matrix4f[] segments = new Matrix4f[PART_COUNT];
        Vector3f[] frontNormals = new Vector3f[PART_COUNT];
        Vector3f[] backNormals = new Vector3f[PART_COUNT];
        for (int i = 0; i < PART_COUNT; i++) {
            pose.pushPose();
            applySegmentPose(pose, sim, partialTick, i, underwater);
            Matrix4f current = new Matrix4f(pose.last().pose());
            segments[i] = current;
            int prev = Math.max(i - 1, 0);
            frontNormals[i] = normalVec(
                    segments[prev],
                    segments[prev],
                    current,
                    new Vector3f(HALF_WIDTH, i * SEGMENT, -DEPTH),
                    new Vector3f(-HALF_WIDTH, i * SEGMENT, -DEPTH),
                    new Vector3f(HALF_WIDTH, (i + 1) * SEGMENT, -DEPTH),
                    flip);
            backNormals[i] = normalVec(
                    segments[prev],
                    segments[prev],
                    current,
                    new Vector3f(HALF_WIDTH, (i + 1) * SEGMENT, 0.0F),
                    new Vector3f(-HALF_WIDTH, (i + 1) * SEGMENT, 0.0F),
                    new Vector3f(HALF_WIDTH, i * SEGMENT, 0.0F),
                    flip);
            pose.popPose();
        }

        for (int i = 0; i < PART_COUNT; i++) {
            Matrix4f current = segments[i];
            Matrix4f previous = segments[Math.max(i - 1, 0)];

            if (i == 0) { // the top edge
                float u0 = 0.015625F, u1 = 0.171875F, v0 = 0.0F, v1 = 0.03125F;
                Vector3f normal = normalVec(
                        segments[0],
                        segments[0],
                        segments[0],
                        new Vector3f(HALF_WIDTH, 0.0F, 0.0F),
                        new Vector3f(-HALF_WIDTH, 0.0F, 0.0F),
                        new Vector3f(HALF_WIDTH, 0.0F, DEPTH),
                        flip);
                vertex(consumer, segments[0], HALF_WIDTH, 0.0F, 0.0F, u1, v1, light, normal);
                vertex(consumer, segments[0], -HALF_WIDTH, 0.0F, 0.0F, u0, v1, light, normal);
                vertex(consumer, segments[0], -HALF_WIDTH, 0.0F, -DEPTH, u0, v0, light, normal);
                vertex(consumer, segments[0], HALF_WIDTH, 0.0F, -DEPTH, u1, v0, light, normal);
            }

            if (i == PART_COUNT - 1) { // the bottom edge
                float u0 = 0.171875F, u1 = 0.328125F, v0 = 0.0F, v1 = 0.03125F;
                Vector3f normal = normalVec(
                        current,
                        current,
                        current,
                        new Vector3f(HALF_WIDTH, 1.0F, -DEPTH),
                        new Vector3f(-HALF_WIDTH, 1.0F, -DEPTH),
                        new Vector3f(HALF_WIDTH, 1.0F, 0.0F),
                        flip);
                vertex(consumer, current, HALF_WIDTH, 1.0F, -DEPTH, u1, v0, light, normal);
                vertex(consumer, current, -HALF_WIDTH, 1.0F, -DEPTH, u0, v0, light, normal);
                vertex(consumer, current, -HALF_WIDTH, 1.0F, 0.0F, u0, v1, light, normal);
                vertex(consumer, current, HALF_WIDTH, 1.0F, 0.0F, u1, v1, light, normal);
            }

            float vTop = SEGMENT_V * (i + 1);
            float vBottom = vTop + SEGMENT_V;
            float top = i * SEGMENT;
            float bottom = (i + 1) * SEGMENT;

            // left side
            float u0 = 0.0F;
            float u1 = 0.015625F;
            Vector3f side = normalVec(
                    current,
                    current,
                    previous,
                    new Vector3f(-HALF_WIDTH, bottom, 0.0F),
                    new Vector3f(-HALF_WIDTH, bottom, -DEPTH),
                    new Vector3f(-HALF_WIDTH, top, 0.0F),
                    flip);
            vertex(consumer, current, -HALF_WIDTH, bottom, 0.0F, u0, vBottom, light, side);
            vertex(consumer, current, -HALF_WIDTH, bottom, -DEPTH, u1, vBottom, light, side);
            vertex(consumer, previous, -HALF_WIDTH, top, -DEPTH, u1, vTop, light, side);
            vertex(consumer, previous, -HALF_WIDTH, top, 0.0F, u0, vTop, light, side);

            // right side
            u0 = 0.171875F;
            u1 = 0.1875F;
            side = normalVec(
                    current,
                    current,
                    previous,
                    new Vector3f(HALF_WIDTH, bottom, -DEPTH),
                    new Vector3f(HALF_WIDTH, bottom, 0.0F),
                    new Vector3f(HALF_WIDTH, top, -DEPTH),
                    flip);
            vertex(consumer, current, HALF_WIDTH, bottom, -DEPTH, u0, vBottom, light, side);
            vertex(consumer, current, HALF_WIDTH, bottom, 0.0F, u1, vBottom, light, side);
            vertex(consumer, previous, HALF_WIDTH, top, 0.0F, u1, vTop, light, side);
            vertex(consumer, previous, HALF_WIDTH, top, -DEPTH, u0, vTop, light, side);

            // front, with normals smoothed with the segments above and below
            u0 = 0.015625F;
            u1 = 0.171875F;
            Vector3f upper = average(frontNormals[i], frontNormals[Math.max(i - 1, 0)]);
            Vector3f lower = average(frontNormals[i], frontNormals[Math.min(i + 1, PART_COUNT - 1)]);
            vertex(consumer, previous, HALF_WIDTH, top, -DEPTH, u1, vTop, light, upper);
            vertex(consumer, previous, -HALF_WIDTH, top, -DEPTH, u0, vTop, light, upper);
            vertex(consumer, current, -HALF_WIDTH, bottom, -DEPTH, u0, vBottom, light, lower);
            vertex(consumer, current, HALF_WIDTH, bottom, -DEPTH, u1, vBottom, light, lower);

            // back
            u0 = 0.1875F;
            u1 = 0.34375F;
            upper = average(backNormals[i], backNormals[Math.max(i - 1, 0)]);
            lower = average(backNormals[i], backNormals[Math.min(i + 1, PART_COUNT - 1)]);
            vertex(consumer, previous, HALF_WIDTH, top, 0.0F, u0, vTop, light, upper);
            vertex(consumer, previous, -HALF_WIDTH, top, 0.0F, u1, vTop, light, upper);
            vertex(consumer, current, -HALF_WIDTH, bottom, 0.0F, u1, vBottom, light, lower);
            vertex(consumer, current, HALF_WIDTH, bottom, 0.0F, u0, vBottom, light, lower);
        }
    }

    private void applySegmentPose(
            PoseStack pose, ClothSimulation sim, float partialTick, int part, boolean underwater) {
        pose.translate(0.0, 0.0, 0.125);
        float dx = sim.lerpX(part, partialTick) - sim.lerpX(0, partialTick);
        if (dx > 0.0F) dx = 0.0F; // the cape never goes into the body
        float dy = sim.lerpY(0, partialTick) - part - sim.lerpY(part, partialTick);
        float dz = sim.lerpZ(0, partialTick) - sim.lerpZ(part, partialTick);
        float bend = getRotation(partialTick, part, sim);
        float wind = naturalWindSwing(part, underwater);
        pose.mulPose(Axis.XP.rotationDegrees(6.0F + wind));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(-dz / 16.0F, dy / 16.0F, dx / 16.0F);
        pose.translate(0.0, 0.03, -0.03);
        pose.translate(0.0F, part / 16.0F, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(-bend));
        pose.translate(0.0F, -part / 16.0F, 0.0F);
        pose.translate(0.0, -0.03, 0.03);
    }

    private float getRotation(float partialTick, int part, ClothSimulation sim) {
        if (part == PART_COUNT - 1) return getRotation(partialTick, part - 1, sim);
        float x0 = sim.lerpX(part, partialTick);
        float y0 = sim.lerpY(part, partialTick);
        float x1 = sim.lerpX(part + 1, partialTick);
        float y1 = sim.lerpY(part + 1, partialTick);
        return (float) (Math.toDegrees(Math.atan2(x1 - x0, y1 - y0)) + 180.0);
    }

    private float naturalWindSwing(int part, boolean underwater) {
        long phase = System.currentTimeMillis() / (underwater ? 9 : 3) % 360L;
        float position = (part + 1) / 16.0F;
        return (float) (Math.sin(Math.toRadians(position * 360.0F - (float) phase)) * WIND_AMPLITUDE);
    }

    private static Vector3f average(Vector3f a, Vector3f b) {
        return new Vector3f((a.x + b.x) / 2.0F, (a.y + b.y) / 2.0F, (a.z + b.z) / 2.0F);
    }

    private static Vector3f normalVec(
            Matrix4f matA, Matrix4f matB, Matrix4f matC, Vector3f a, Vector3f b, Vector3f c, boolean flip) {
        Vector3fc origin = transform(matA, a);
        Vector3f normal = transform(matB, b);
        Vector3f edge = transform(matC, c);
        normal.sub(origin);
        edge.sub(origin);
        normal.cross(edge);
        normal.normalize();
        if (flip) normal.mul(-1.0F);
        return normal;
    }

    private static Vector3f transform(Matrix4f matrix, Vector3f point) {
        Vector4f result = matrix.transform(new Vector4f(point.x, point.y, point.z, 1.0F));
        return new Vector3f(result.x, result.y, result.z);
    }

    private void vertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            float u,
            float v,
            int light,
            Vector3f normal) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(ALPHA, ALPHA, ALPHA, ALPHA)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z);
    }
}
