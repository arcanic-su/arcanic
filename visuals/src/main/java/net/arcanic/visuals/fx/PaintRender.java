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

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.mixin.ModelPartAccess;
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.arcanic.visuals.mixin.RenderTypeAccess;
import net.arcanic.visuals.render.GrayTex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PaintRender {
    private static final Vector3f N = new Vector3f();
    private static final Vector3f P = new Vector3f();
    private static final Vector3f M = new Vector3f();
    private static final float[] PX = new float[4];
    private static final float[] PY = new float[4];
    private static final float[] PZ = new float[4];
    private static final float[] U = new float[4];
    private static final float[] V = new float[4];
    public static final int LAYER_ARMOR = 1;
    public static final int LAYER_HULL = 2;
    public static final int LAYER_GLOW = 3;
    public static final int LAYER_FILL = 4;
    private static final Job[] JOBS = new Job[4096];
    private static int seq;
    private static int armourMagic;
    private static final float[] VX;
    private static final float[] VY;
    private static final float[] VZ;
    private static final float[] VU;
    private static final float[] VV;
    private static RenderType outlineType;
    private static boolean outlineFailed;
    private static Method shadowPass;
    private static Object iris;
    private static boolean irisChecked;

    private PaintRender() {}

    static Job job() {
        seq = seq + 1 & 1048575;
        if (seq == 0) {
            seq = 1;
        }

        Job j = JOBS[seq & JOBS.length - 1];
        j.seq = seq;
        j.flash = false;
        j.health = 1.0F;
        return j;
    }

    static int magic(Job j, int layer) {
        return 16777216 | j.seq << 4 | layer;
    }

    static Job find(int colour) {
        if (colour >>> 24 != 1) {
            return null;
        } else {
            int s = colour >> 4 & 1048575;
            Job j = JOBS[s & JOBS.length - 1];
            return j.seq == s ? j : null;
        }
    }

    public static boolean model(ModelPart root, PoseStack ps, VertexConsumer vc, int light, int overlay, int colour) {
        Job j = find(colour);
        if (j == null) {
            return false;
        } else {
            try {
                part(root, ps, vc, light, overlay, j, colour & 15, Paint.time());
            } catch (RuntimeException ignored) {
            }

            return true;
        }
    }

    private static void part(
            ModelPart p, PoseStack ps, VertexConsumer vc, int light, int overlay, Job j, int layer, float time) {
        if (p.visible) {
            ModelPartAccess acc = (ModelPartAccess) (Object) p;
            List<ModelPart.Cube> cubes = acc.arcanicvisuals$cubes();
            Map<String, ModelPart> kids = acc.arcanicvisuals$children();
            if (!cubes.isEmpty() || !kids.isEmpty()) {
                ps.pushPose();
                p.translateAndRotate(ps);
                if (!p.skipDraw) {
                    for (ModelPart.Cube c : cubes) {
                        if (layer == 1) {
                            armourCube(c, ps.last(), vc, light, overlay, j, time);
                        } else {
                            hullCube(c, ps.last(), vc, j, layer, time);
                        }
                    }
                }

                for (ModelPart k : kids.values()) {
                    part(k, ps, vc, light, overlay, j, layer, time);
                }

                ps.popPose();
            }
        }
    }

    private static float height(Job j, Vector3f camSpace) {
        j.inv.transformPosition(camSpace, M);
        return (1.501F - M.y) / j.height;
    }

    public static void armourBegin(EquipmentClientInfo.LayerType type, Object state, PoseStack pose) {
        armourMagic = 0;
        Paint.Armor a = Modules.ARMOR_PAINT;
        if (a.enabled() && (type == LayerType.HUMANOID || type == LayerType.HUMANOID_LEGGINGS)) {
            Entity e = Ducks.entityOf(state);
            if (e != null && (e == Minecraft.getInstance().player || a.others.on() && e instanceof Player)) {
                Job j = job();
                j.inv.set(pose.last().pose()).invert();
                j.height = 2.0F;
                armourMagic = magic(j, 1);
            }
        }
    }

    public static void armourEnd() {
        armourMagic = 0;
    }

    public static Identifier armourTexture(Identifier tex) {
        return armourMagic != 0 && Modules.ARMOR_PAINT.recolour() ? GrayTex.single(tex) : tex;
    }

    public static int armourColour(int c) {
        return armourMagic != 0 ? armourMagic : c;
    }

    private static void armourCube(
            ModelPart.Cube c, PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, Job j, float time) {
        Paint.Armor a = Modules.ARMOR_PAINT;
        int lt = a.glow.on() ? 15728880 : light;
        Matrix4f m = pose.pose();

        for (ModelPart.Polygon poly : c.polygons) {
            ModelPart.Vertex[] vs = poly.vertices();
            if (vs.length == 4) {
                pose.transformNormal(poly.normal(), N);

                for (int i = 0; i < 4; i++) {
                    VX[i] = vs[i].x();
                    VY[i] = vs[i].y();
                    VZ[i] = vs[i].z();
                    VU[i] = vs[i].u();
                    VV[i] = vs[i].v();
                }

                float du = dist(0, 1);
                float dv = dist(1, 2);
                int nu = Math.max(1, Math.min(8, (int) Math.ceil((double) (du / 2.0F))));
                int nv = Math.max(1, Math.min(8, (int) Math.ceil((double) (dv / 2.0F))));

                for (int i = 0; i < nu; i++) {
                    for (int k = 0; k < nv; k++) {
                        float s0 = (float) i / (float) nu;
                        float s1 = (float) (i + 1) / (float) nu;
                        float t0 = (float) k / (float) nv;
                        float t1 = (float) (k + 1) / (float) nv;
                        armourVertex(vc, m, a, j, s0, t0, overlay, lt, time);
                        armourVertex(vc, m, a, j, s1, t0, overlay, lt, time);
                        armourVertex(vc, m, a, j, s1, t1, overlay, lt, time);
                        armourVertex(vc, m, a, j, s0, t1, overlay, lt, time);
                    }
                }
            }
        }
    }

    private static float bilerp(float[] a, float s, float t) {
        return (1.0F - s) * (1.0F - t) * a[0] + s * (1.0F - t) * a[1] + s * t * a[2] + (1.0F - s) * t * a[3];
    }

    private static float dist(int p, int q) {
        float dx = VX[p] - VX[q];
        float dy = VY[p] - VY[q];
        float dz = VZ[p] - VZ[q];
        return (float) Math.sqrt((double) (dx * dx + dy * dy + dz * dz));
    }

    private static void armourVertex(
            VertexConsumer vc, Matrix4f m, Paint.Armor a, Job j, float s, float t, int overlay, int light, float time) {
        m.transformPosition(bilerp(VX, s, t) / 16.0F, bilerp(VY, s, t) / 16.0F, bilerp(VZ, s, t) / 16.0F, P);
        int c = a.colourAt(Paint.clamp(height(j, P)), time);
        vc.addVertex(P.x, P.y, P.z, c, bilerp(VU, s, t), bilerp(VV, s, t), overlay, light, N.x, N.y, N.z);
    }

    static RenderType outlineType() {
        if (outlineType == null && !outlineFailed) {
            try {
                RenderPipeline pipeline = RenderPipeline.builder(RenderPipelinesAccess.arcanicvisuals$entitySnippet())
                        .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/outline"))
                        .withShaderDefine("EMISSIVE")
                        .withShaderDefine("NO_OVERLAY")
                        .withShaderDefine("NO_CARDINAL_LIGHTING")
                        .withSampler("Sampler0")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthWrite(false)
                        .withCull(true)
                        .build();
                Fx.irisAssign(pipeline);
                outlineType = RenderTypeAccess.arcanicvisuals$create(
                        "arcanicvisuals_outline",
                        RenderSetup.builder(pipeline)
                                .withTexture("Sampler0", Fx.Tex.WHITE.id)
                                .sortOnUpload()
                                .createRenderSetup());
            } catch (Throwable e) {
                outlineFailed = true;
                System.err.println("[ArcanicVisuals] outline pipeline unavailable: " + e);
            }

            return outlineType;
        } else {
            return outlineType;
        }
    }

    public static void hull(Model model, LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector c) {
        Outline o = Modules.OUTLINE;
        if (o.enabled() && !o.chams() && !irisShadowPass()) {
            Entity entity2 = Ducks.entityOf(state);
            if (entity2 instanceof LivingEntity) {
                LivingEntity e = (LivingEntity) entity2;
                if (o.wants(e)) {
                    RenderType rt = outlineType();
                    if (rt == null) {
                        return;
                    }

                    Minecraft mc = Minecraft.getInstance();
                    Matrix4f m = pose.last().pose();
                    Job j = job();
                    j.inv.set(m).invert();
                    float sy = (float) Math.sqrt((double) (m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12()));
                    if (sy < 1.0E-5F) {
                        return;
                    }

                    j.height = Math.max(0.3F, state.boundingBoxHeight) / sy;
                    double dist = Math.sqrt(state.distanceToCameraSq);
                    double fov =
                            Math.toRadians((double) (Integer) mc.options.fov().get());
                    float worldPerPx = (float) (2.0
                            * dist
                            * Math.tan(fov / 2.0)
                            / (double) Math.max(1, mc.getWindow().getHeight()));
                    float world = Math.max(0.012F, o.thickness.f() * worldPerPx);
                    j.thick = Math.min(world, 0.09F) / sy * 16.0F;
                    j.health = Math.max(0.0F, Math.min(1.0F, e.getHealth() / Math.max(1.0F, e.getMaxHealth())));
                    j.flash = o.hitFlash.on() && state.hasRedOverlay;
                    int light = 15728880;
                    int ov = OverlayTexture.NO_OVERLAY;
                    if (o.glow.on()) {
                        c.submitModel(
                                model,
                                state,
                                pose,
                                rt,
                                light,
                                ov,
                                magic(j, 3),
                                (TextureAtlasSprite) null,
                                0,
                                (ModelFeatureRenderer.CrumblingOverlay) null);
                    }

                    c.submitModel(
                            model,
                            state,
                            pose,
                            rt,
                            light,
                            ov,
                            magic(j, 2),
                            (TextureAtlasSprite) null,
                            0,
                            (ModelFeatureRenderer.CrumblingOverlay) null);
                    if (o.fill.on()) {
                        c.submitModel(
                                model,
                                state,
                                pose,
                                rt,
                                light,
                                ov,
                                magic(j, 4),
                                (TextureAtlasSprite) null,
                                0,
                                (ModelFeatureRenderer.CrumblingOverlay) null);
                    }

                    return;
                }
            }
        }
    }

    private static void hullCube(
            ModelPart.Cube c, PoseStack.Pose pose, VertexConsumer vc, Job j, int layer, float time) {
        Outline o = Modules.OUTLINE;
        float t = layer == 3 ? j.thick * 2.5F : (layer == 2 ? j.thick : 0.05F);
        boolean shell = layer != 4;
        float alpha = (float) ((Double) o.opacity.get() / 100.0) * (layer == 3 ? 0.42F : 1.0F);
        if (layer == 4) {
            alpha = (float) ((Double) o.fillOpacity.get() / 100.0);
        }

        float cx = (c.minX + c.maxX) / 2.0F;
        float cy = (c.minY + c.maxY) / 2.0F;
        float cz = (c.minZ + c.maxZ) / 2.0F;
        Matrix4f m = pose.pose();

        for (ModelPart.Polygon poly : c.polygons) {
            ModelPart.Vertex[] vs = poly.vertices();
            if (vs.length == 4) {
                pose.transformNormal(poly.normal(), N);
                if (shell) {
                    N.negate();
                }

                for (int k = 0; k < 4; k++) {
                    ModelPart.Vertex v = vs[shell ? 3 - k : k];
                    float x = v.x() + Math.signum(v.x() - cx) * t;
                    float y = v.y() + Math.signum(v.y() - cy) * t;
                    float z = v.z() + Math.signum(v.z() - cz) * t;
                    m.transformPosition(x / 16.0F, y / 16.0F, z / 16.0F, P);
                    int col = o.colourAt(height(j, P), time, j.health);
                    if (j.flash) {
                        col = Paint.lerp(col, -50354, 0.75F);
                    }

                    if (layer == 4) {
                        col = Paint.lerp(col, -1, 0.15F);
                    }

                    col = Math.round(alpha * 255.0F) << 24 | col & 0xFFFFFF;
                    vc.addVertex(P.x, P.y, P.z, col, 0.5F, 0.5F, OverlayTexture.NO_OVERLAY, 15728880, N.x, N.y, N.z);
                }
            }
        }
    }

    static boolean irisShadowPass() {
        if (!irisChecked) {
            irisChecked = true;

            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                shadowPass = api.getMethod("isRenderingShadowPass");
            } catch (Throwable ignored) {
            }
        }

        if (shadowPass == null) {
            return false;
        } else {
            try {
                return (Boolean) shadowPass.invoke(iris);
            } catch (Throwable e) {
                return false;
            }
        }
    }

    static {
        for (int i = 0; i < JOBS.length; i++) {
            JOBS[i] = new Job();
        }

        VX = new float[4];
        VY = new float[4];
        VZ = new float[4];
        VU = new float[4];
        VV = new float[4];
    }

    static final class Job {
        int seq;
        final Matrix4f inv = new Matrix4f();
        float height = 2.0F;
        float thick;
        float health = 1.0F;
        boolean flash;
    }
}
