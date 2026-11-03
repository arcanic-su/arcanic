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
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.arcanic.visuals.fx.Fx;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The flash at a gun's muzzle as it fires: a soft warm bloom, a four-point star and a short streak ahead. */
final class MuzzleFlash {
    private static final float PIXEL = 1.0F / 16.0F;
    private static final float BLOOM = 7.0F;
    private static final float STAR = 12.0F;
    private static final float STREAK_LENGTH = 26.0F;
    private static final float STREAK_WIDTH = 5.0F;
    private static final float STREAK_BEHIND = 0.15F;
    private static final float[] BLOOM_TINT = {1.0F, 0.78F, 0.48F};
    private static final float[] STAR_TINT = {1.0F, 0.88F, 0.64F};
    private static final float[] STREAK_TINT = {1.0F, 0.72F, 0.42F};

    private MuzzleFlash() {}

    /**
     * {@code muzzle} takes the model's own space (in blocks, forward along -Z) to the pose's space, with the
     * point of the flash at its origin; the camera sits at the pose space's origin.
     */
    static void submit(PoseStack pose, SubmitNodeCollector collector, Matrix4f muzzle, float strength, float spin) {
        if (strength <= 0.0F) return;
        float k = strength * strength;
        collector.submitCustomGeometry(pose, Fx.type(Fx.Tex.GLOW, true), (last, consumer) -> {
            Frame f = new Frame(new Matrix4f(last.pose()).mul(muzzle), 0.0F);
            billboard(consumer, f, BLOOM * f.scale, BLOOM_TINT, k);
        });
        collector.submitCustomGeometry(pose, Fx.type(Fx.Tex.STAR, true), (last, consumer) -> {
            Frame f = new Frame(new Matrix4f(last.pose()).mul(muzzle), spin);
            billboard(consumer, f, STAR * f.scale * (0.8F + 0.2F * strength), STAR_TINT, k);
        });
        collector.submitCustomGeometry(pose, Fx.type(Fx.Tex.SPARK, true), (last, consumer) -> {
            Frame f = new Frame(new Matrix4f(last.pose()).mul(muzzle), 0.0F);
            streak(consumer, f, STREAK_LENGTH * f.scale * (0.6F + 0.4F * strength), STREAK_WIDTH * f.scale, k);
        });
    }

    private static void billboard(VertexConsumer consumer, Frame f, float radius, float[] tint, float k) {
        Vector3f r = new Vector3f(f.right).mul(radius);
        Vector3f u = new Vector3f(f.up).mul(radius);
        Vector3f c = f.center;
        vertex(consumer, c.x - r.x - u.x, c.y - r.y - u.y, c.z - r.z - u.z, 0.0F, 1.0F, tint, k);
        vertex(consumer, c.x + r.x - u.x, c.y + r.y - u.y, c.z + r.z - u.z, 1.0F, 1.0F, tint, k);
        vertex(consumer, c.x + r.x + u.x, c.y + r.y + u.y, c.z + r.z + u.z, 1.0F, 0.0F, tint, k);
        vertex(consumer, c.x - r.x + u.x, c.y - r.y + u.y, c.z - r.z + u.z, 0.0F, 0.0F, tint, k);
    }

    private static void streak(VertexConsumer consumer, Frame f, float length, float width, float k) {
        Vector3f side = new Vector3f(f.forward).cross(f.view);
        if (side.lengthSquared() < 1.0E-8F) return; // looking straight down the barrel: the bloom covers it
        side.normalize().mul(width * 0.5F);
        Vector3f base = new Vector3f(f.forward).mul(-length * STREAK_BEHIND).add(f.center);
        Vector3f tip = new Vector3f(f.forward).mul(length).add(base);
        vertex(consumer, base.x - side.x, base.y - side.y, base.z - side.z, 0.0F, 1.0F, STREAK_TINT, k);
        vertex(consumer, tip.x - side.x, tip.y - side.y, tip.z - side.z, 1.0F, 1.0F, STREAK_TINT, k);
        vertex(consumer, tip.x + side.x, tip.y + side.y, tip.z + side.z, 1.0F, 0.0F, STREAK_TINT, k);
        vertex(consumer, base.x + side.x, base.y + side.y, base.z + side.z, 0.0F, 0.0F, STREAK_TINT, k);
    }

    private static void vertex(
            VertexConsumer consumer, float x, float y, float z, float u, float v, float[] tint, float k) {
        consumer.addVertex(x, y, z)
                .setColor(tint[0] * k, tint[1] * k, tint[2] * k, k)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** The muzzle seen from the camera: where it is, where the barrel points and a face turned to the eye. */
    private static final class Frame {
        final Vector3f center;
        final Vector3f forward;
        final Vector3f view;
        final Vector3f right = new Vector3f();
        final Vector3f up = new Vector3f();
        final float scale;

        Frame(Matrix4f m, float spin) {
            center = m.transformPosition(new Vector3f());
            forward = m.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F));
            scale = forward.length() * PIXEL;
            forward.normalize();
            view = center.lengthSquared() > 1.0E-8F ? new Vector3f(center).normalize() : new Vector3f(0, 0, -1);
            view.cross(0.0F, 1.0F, 0.0F, right);
            if (right.lengthSquared() < 1.0E-6F) right.set(1.0F, 0.0F, 0.0F);
            right.normalize();
            right.cross(view, up).normalize();
            if (spin != 0.0F) {
                float cos = (float) Math.cos(spin);
                float sin = (float) Math.sin(spin);
                Vector3f r = new Vector3f(right).mul(cos).add(new Vector3f(up).mul(sin));
                up.mul(cos).sub(new Vector3f(right).mul(sin));
                right.set(r);
            }
        }
    }
}
