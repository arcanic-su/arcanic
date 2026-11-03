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
package net.arcanic.visuals.cosmetic;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.arcanic.visuals.mixin.GuiGraphicsAccess;
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.TexWarm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

public final class WearPreview {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(
                    RenderPipelinesAccess.arcanicvisuals$guiTextureSnippet())
            .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/wear_preview"))
            .withCull(false)
            .build();
    private static final Map<Integer, float[]> POSED = new HashMap<>();
    private static final float LX = -0.42F;
    private static final float LY = 0.74F;
    private static final float LZ = -0.53F;

    private WearPreview() {}

    public static boolean draw(
            GuiGraphics g, Wearables.Item it, int x, int y, int w, int h, float hover, int clipTop, int clipBottom) {
        Wearables.Geo geo = Wearables.Geo.get(it.n());
        if (geo != null && geo.quadCount != 0) {
            if (!TexWarm.ready(geo.texture)) {
                return false;
            } else {
                long ms = System.currentTimeMillis();
                double sec = (double) (ms % 3600000L) / 1000.0;
                boolean wings = it.type().equals("wings");
                boolean back = wings || it.type().equals("bodywear");
                Wearables.Drive d = new Wearables.Drive(
                        sec + (double) it.n() * 0.37,
                        (float) (sec * 6.0),
                        wings ? 0.0F : hover,
                        wings ? hover : 0.0F,
                        0.0F,
                        (float) (5.0 + 5.0 * Math.sin(sec * 1.7)),
                        (float) (3.0 * Math.sin(sec * 1.1)),
                        ms);
                float[] q = (float[]) POSED.computeIfAbsent(it.n(), (kx) -> new float[geo.quadCount * 32]);
                geo.pose(d, q);
                double yaw =
                        Math.toRadians((double) (back ? 200 : -28) + 28.0 * Math.sin(sec * 0.55 + (double) it.n()));
                double pitch = Math.toRadians(back ? 12.0 : 16.0);
                float cy = (float) Math.cos(yaw);
                float sy = (float) Math.sin(yaw);
                float cp = (float) Math.cos(pitch);
                float sp = (float) Math.sin(pitch);
                float[] b = geo.bounds();
                float mx = (b[0] + b[3]) / 2.0F;
                float my = (b[1] + b[4]) / 2.0F;
                float mz = (b[2] + b[5]) / 2.0F;
                float rx = (b[3] - b[0]) / 2.0F;
                float ry = (b[4] - b[1]) / 2.0F;
                float rz = (b[5] - b[2]) / 2.0F;
                float rh = (float) Math.sqrt((double) (rx * rx + rz * rz));
                float scale = Math.min(
                                (float) w / 2.0F / Math.max(rh, 0.001F),
                                (float) h / 2.0F / Math.max(ry * cp + rh * sp, 0.001F))
                        * 0.9F
                        * (1.0F + 0.05F * hover);
                float ox = (float) x + (float) w / 2.0F;
                float oy = (float) y + (float) h / 2.0F;
                int n = geo.quadCount;
                float[] verts = new float[n * 16];
                int[] colours = new int[n * 4];
                long[] order = new long[n];
                float all = geo.frameShift(ms);
                int alpha = Math.round(Px.fade * 255.0F);
                int k = 0;

                for (int i = 0; k < n; i += 32) {
                    float depth = 0.0F;
                    float dv = all + geo.shift(k, ms);
                    int mode = geo.quadMode[k];
                    boolean lit = mode != 1 && mode != 3;

                    for (int c = 0; c < 4; c++) {
                        int o = i + c * 8;
                        float px = -q[o] - mx;
                        float py = q[o + 1] - my;
                        float pz = q[o + 2] - mz;
                        float x2 = px * cy - pz * sy;
                        float z2 = px * sy + pz * cy;
                        float y3 = py * cp + z2 * sp;
                        float z3 = -py * sp + z2 * cp;
                        verts[k * 16 + c * 4] = ox + x2 * scale;
                        verts[k * 16 + c * 4 + 1] = oy - y3 * scale;
                        verts[k * 16 + c * 4 + 2] = q[o + 3];
                        verts[k * 16 + c * 4 + 3] = q[o + 4] + dv;
                        depth += z3;
                        float bright = 1.0F;
                        if (lit) {
                            float nx = -q[o + 5];
                            float ny = q[o + 6];
                            float nz = q[o + 7];
                            float n2 = nx * cy - nz * sy;
                            float nz2 = nx * sy + nz * cy;
                            float n3 = ny * cp + nz2 * sp;
                            float nz3 = -ny * sp + nz2 * cp;
                            bright = 0.56F + 0.44F * Math.abs(n2 * -0.42F + n3 * 0.74F + nz3 * -0.53F);
                        }

                        int grey = Math.round(Math.min(1.0F, bright) * 255.0F);
                        colours[k * 4 + c] = alpha << 24 | grey << 16 | grey << 8 | grey;
                    }

                    order[k] = (long) Math.round((10.0F - depth / 4.0F) * 100000.0F) << 20 | (long) k;
                    ++k;
                }

                Arrays.sort(order);
                AbstractTexture tex =
                        Minecraft.getInstance().getTextureManager().getTexture(geo.texture);
                Matrix3x2f matrix = new Matrix3x2f(g.pose());
                ScreenRectangle bounds = (new ScreenRectangle(x, y, w, h)).transformMaxBounds(matrix);
                ScreenRectangle clip = (new ScreenRectangle(x, clipTop, w, Math.max(0, clipBottom - clipTop)))
                        .transformMaxBounds(matrix);
                bounds = bounds.intersection(clip);
                if (bounds != null && bounds.height() != 0) {
                    ((GuiGraphicsAccess) g)
                            .arcanicvisuals$state()
                            .submitGuiElement(new State(
                                    matrix,
                                    verts,
                                    colours,
                                    order,
                                    TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler()),
                                    bounds));
                    return true;
                } else {
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private static record State(
            Matrix3x2f pose,
            float[] verts,
            int[] colours,
            long[] order,
            TextureSetup textureSetup,
            ScreenRectangle bounds)
            implements GuiElementRenderState {
        public RenderPipeline pipeline() {
            return WearPreview.PIPELINE;
        }

        public ScreenRectangle scissorArea() {
            return this.bounds;
        }

        public void buildVertices(VertexConsumer vc) {
            for (long key : this.order) {
                int k = (int) (key & 1048575L);
                int o = k * 16;

                for (int c = 0; c < 4; c++) {
                    vc.addVertexWith2DPose(this.pose, this.verts[o + c * 4], this.verts[o + c * 4 + 1])
                            .setUv(this.verts[o + c * 4 + 2], this.verts[o + c * 4 + 3])
                            .setColor(this.colours[k * 4 + c]);
                }
            }
        }
    }
}
