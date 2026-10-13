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
import java.util.Arrays;
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
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Drawing for the painted weapon and armour and for the enemy outline. */
public final class PaintRender {
    private PaintRender() {}

    public static final int ITEM_MARK = 0x7A51C0DE, AURA_MARK = 0x7A51C0DF;
    public static int pendingItem;

    public static boolean firstPerson(LivingEntity e, ItemStack s, ItemDisplayContext ctx) {
        Paint.Weapon w = Modules.WEAPON;
        Minecraft mc = Minecraft.getInstance();
        if (!w.enabled() || e != mc.player || !ctx.firstPerson() || !w.paints(s)) return false;
        return s == mc.player.getMainHandItem() || (w.offhand.on() && s == mc.player.getOffhandItem());
    }

    public static boolean thirdPerson(LivingEntity e, ItemStack s, boolean mainHand) {
        Paint.Weapon w = Modules.WEAPON;
        return w.enabled()
                && w.thirdPerson.on()
                && e == Minecraft.getInstance().player
                && (mainHand || w.offhand.on())
                && w.paints(s);
    }

    public static int[] markTints(int[] tints) {
        int[] t = Arrays.copyOf(tints, tints.length + 1);
        t[tints.length] = ITEM_MARK;
        return t;
    }

    public static boolean marked(int[] tints) {
        return tints != null && tints.length > 0 && tints[tints.length - 1] == ITEM_MARK;
    }

    public static boolean auraMarked(int[] tints) {
        return tints != null && tints.length > 0 && tints[tints.length - 1] == AURA_MARK;
    }

    public static int[] markAura(int[] tints) {
        int[] t = Arrays.copyOf(tints, tints.length + 1);
        t[tints.length] = AURA_MARK;
        return t;
    }

    public static RenderType auraType(List<BakedQuad> quads) {
        if (!Modules.WEAPON.aura.on()) return null;
        Identifier grey = GrayTex.atlasFor(quads);
        return grey == null ? null : Fx.glowWith(grey);
    }

    public static void aura(PoseStack.Pose pose, VertexConsumer vc, List<BakedQuad> quads) {
        Paint.Weapon w = Modules.WEAPON;
        float time = Paint.time(), power = w.auraPower.f() / 100f;
        Matrix4f m = pose.pose();
        float[][] layers = {{1.07f, .55f}, {1.16f, .28f}, {1.27f, .12f}};
        for (float[] layer : layers) {
            float s = layer[0], k = layer[1] * power * (.85f + .15f * (float) Math.sin(time * 3.1));
            for (BakedQuad q : quads) {
                pose.transformNormal(q.direction().getUnitVec3f(), N);
                for (int i = 0; i < 4; i++) {
                    Vector3fc p = q.position(i);
                    float x = .5f + (p.x() - .5f) * s, y = .5f + (p.y() - .5f) * s, z = .5f + (p.z() - .5f) * s;
                    int c = w.colourAt(Paint.clamp((p.x() + p.y()) * .5f), time);
                    // additive blending ignores alpha: the strength goes into the colour itself
                    int r = Math.round(((c >> 16) & 255) * k),
                            g = Math.round(((c >> 8) & 255) * k),
                            b = Math.round((c & 255) * k);
                    long uv = q.packedUV(i);
                    m.transformPosition(x, y, z, P);
                    vc.addVertex(
                            P.x,
                            P.y,
                            P.z,
                            0xFF000000 | (r << 16) | (g << 8) | b,
                            UVPair.unpackU(uv),
                            UVPair.unpackV(uv),
                            OverlayTexture.NO_OVERLAY,
                            LightTexture.FULL_BRIGHT,
                            N.x,
                            N.y,
                            N.z);
                }
            }
        }
    }

    public static RenderType itemType(RenderType type, List<BakedQuad> quads) {
        if (!Modules.WEAPON.recolour()) return type;
        Identifier grey = GrayTex.atlasFor(quads);
        return grey == null ? type : RenderTypes.itemEntityTranslucentCull(grey);
    }

    private static final Vector3f N = new Vector3f(), P = new Vector3f(), M = new Vector3f();
    private static final float[] PX = new float[4],
            PY = new float[4],
            PZ = new float[4],
            U = new float[4],
            V = new float[4];

    /** Draw time: the quads of a painted item, one colour per vertex. */
    public static void item(
            PoseStack.Pose pose, VertexConsumer vc, List<BakedQuad> quads, int[] tints, int light, int overlay) {
        Paint.Weapon w = Modules.WEAPON;
        float time = Paint.time();
        Matrix4f m = pose.pose();
        for (BakedQuad q : quads) {
            pose.transformNormal(q.direction().getUnitVec3f(), N);
            int lt = w.glow.on()
                    ? LightTexture.FULL_BRIGHT
                    : LightTexture.lightCoordsWithEmission(light, q.lightEmission());
            int alpha = 255;
            if (q.isTinted() && q.tintIndex() < tints.length - 1) alpha = tints[q.tintIndex()] >>> 24;
            float minX = 9, maxX = -9, minY = 9, maxY = -9, minZ = 9, maxZ = -9;
            for (int i = 0; i < 4; i++) {
                Vector3fc p = q.position(i);
                PX[i] = p.x();
                PY[i] = p.y();
                PZ[i] = p.z();
                long uv = q.packedUV(i);
                U[i] = UVPair.unpackU(uv);
                V[i] = UVPair.unpackV(uv);
                minX = Math.min(minX, PX[i]);
                maxX = Math.max(maxX, PX[i]);
                minY = Math.min(minY, PY[i]);
                maxY = Math.max(maxY, PY[i]);
                minZ = Math.min(minZ, PZ[i]);
                maxZ = Math.max(maxZ, PZ[i]);
            }
            float size = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
            int div = size > .3f ? 16 : size > .12f ? 4 : 1;
            for (int i = 0; i < div; i++)
                for (int j = 0; j < div; j++) {
                    float s0 = i / (float) div,
                            s1 = (i + 1) / (float) div,
                            t0 = j / (float) div,
                            t1 = (j + 1) / (float) div;
                    itemVertex(vc, m, w, s0, t0, alpha, overlay, lt, time);
                    itemVertex(vc, m, w, s1, t0, alpha, overlay, lt, time);
                    itemVertex(vc, m, w, s1, t1, alpha, overlay, lt, time);
                    itemVertex(vc, m, w, s0, t1, alpha, overlay, lt, time);
                }
        }
    }

    private static float bilerp(float[] a, float s, float t) {
        return (1 - s) * (1 - t) * a[0] + s * (1 - t) * a[1] + s * t * a[2] + (1 - s) * t * a[3];
    }

    private static void itemVertex(
            VertexConsumer vc,
            Matrix4f m,
            Paint.Weapon w,
            float s,
            float t,
            int alpha,
            int overlay,
            int light,
            float time) {
        float x = bilerp(PX, s, t), y = bilerp(PY, s, t), z = bilerp(PZ, s, t);
        int c = w.colourAt(Paint.clamp((x + y) * .5f), time); // handle (bottom left) -> tip (top right)
        c = (alpha << 24) | (c & 0xFFFFFF);
        m.transformPosition(x, y, z, P);
        vc.addVertex(P.x, P.y, P.z, c, bilerp(U, s, t), bilerp(V, s, t), overlay, light, N.x, N.y, N.z);
    }

    public static final int LAYER_ARMOR = 1, LAYER_HULL = 2, LAYER_GLOW = 3, LAYER_FILL = 4;

    static final class Job {
        int seq;
        final Matrix4f inv = new Matrix4f();
        float height = 2, thick, health = 1;
        boolean flash;
    }

    private static final Job[] JOBS = new Job[4096];

    static {
        for (int i = 0; i < JOBS.length; i++) JOBS[i] = new Job();
    }

    private static int seq;

    static Job job() {
        seq = (seq + 1) & 0xFFFFF;
        if (seq == 0) seq = 1;
        Job j = JOBS[seq & (JOBS.length - 1)];
        j.seq = seq;
        j.flash = false;
        j.health = 1;
        return j;
    }

    static int magic(Job j, int layer) {
        return 0x01000000 | (j.seq << 4) | layer;
    }

    static Job find(int colour) {
        if ((colour >>> 24) != 1) return null;
        int s = (colour >> 4) & 0xFFFFF;
        Job j = JOBS[s & (JOBS.length - 1)];
        return j.seq == s ? j : null;
    }

    public static boolean model(ModelPart root, PoseStack ps, VertexConsumer vc, int light, int overlay, int colour) {
        Job j = find(colour);
        if (j == null) return false;
        try {
            part(root, ps, vc, light, overlay, j, colour & 15, Paint.time());
        } catch (RuntimeException e) {
            /* never break the frame */
        }
        return true;
    }

    private static void part(
            ModelPart p, PoseStack ps, VertexConsumer vc, int light, int overlay, Job j, int layer, float time) {
        if (!p.visible) return;
        ModelPartAccess acc = (ModelPartAccess) (Object) p;
        List<ModelPart.Cube> cubes = acc.arcanicvisuals$cubes();
        Map<String, ModelPart> kids = acc.arcanicvisuals$children();
        if (cubes.isEmpty() && kids.isEmpty()) return;
        ps.pushPose();
        p.translateAndRotate(ps);
        if (!p.skipDraw)
            for (ModelPart.Cube c : cubes) {
                if (layer == LAYER_ARMOR) armourCube(c, ps.last(), vc, light, overlay, j, time);
                else hullCube(c, ps.last(), vc, j, layer, time);
            }
        for (ModelPart k : kids.values()) part(k, ps, vc, light, overlay, j, layer, time);
        ps.popPose();
    }

    private static float height(Job j, Vector3f camSpace) {
        j.inv.transformPosition(camSpace, M);
        return (1.501f - M.y) / j.height;
    }

    private static int armourMagic;

    public static void armourBegin(EquipmentClientInfo.LayerType type, Object state, PoseStack pose) {
        armourMagic = 0;
        Paint.Armor a = Modules.ARMOR_PAINT;
        if (!a.enabled()
                || (type != EquipmentClientInfo.LayerType.HUMANOID
                        && type != EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS)) return;
        Entity e = Ducks.entityOf(state);
        if (e == null || (e != Minecraft.getInstance().player && !(a.others.on() && e instanceof Player))) return;
        Job j = job();
        j.inv.set(pose.last().pose()).invert();
        j.height = 2f;
        armourMagic = magic(j, LAYER_ARMOR);
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

    private static final float[] VX = new float[4],
            VY = new float[4],
            VZ = new float[4],
            VU = new float[4],
            VV = new float[4];

    private static void armourCube(
            ModelPart.Cube c, PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, Job j, float time) {
        Paint.Armor a = Modules.ARMOR_PAINT;
        int lt = a.glow.on() ? LightTexture.FULL_BRIGHT : light;
        Matrix4f m = pose.pose();
        for (ModelPart.Polygon poly : c.polygons) {
            ModelPart.Vertex[] vs = poly.vertices();
            if (vs.length != 4) continue;
            pose.transformNormal(poly.normal(), N);
            for (int i = 0; i < 4; i++) {
                VX[i] = vs[i].x();
                VY[i] = vs[i].y();
                VZ[i] = vs[i].z();
                VU[i] = vs[i].u();
                VV[i] = vs[i].v();
            }
            float du = dist(0, 1), dv = dist(1, 2);
            int nu = Math.max(1, Math.min(8, (int) Math.ceil(du / 2))),
                    nv = Math.max(1, Math.min(8, (int) Math.ceil(dv / 2)));
            for (int i = 0; i < nu; i++)
                for (int k = 0; k < nv; k++) {
                    float s0 = i / (float) nu,
                            s1 = (i + 1) / (float) nu,
                            t0 = k / (float) nv,
                            t1 = (k + 1) / (float) nv;
                    armourVertex(vc, m, a, j, s0, t0, overlay, lt, time);
                    armourVertex(vc, m, a, j, s1, t0, overlay, lt, time);
                    armourVertex(vc, m, a, j, s1, t1, overlay, lt, time);
                    armourVertex(vc, m, a, j, s0, t1, overlay, lt, time);
                }
        }
    }

    private static float dist(int p, int q) {
        float dx = VX[p] - VX[q], dy = VY[p] - VY[q], dz = VZ[p] - VZ[q];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void armourVertex(
            VertexConsumer vc, Matrix4f m, Paint.Armor a, Job j, float s, float t, int overlay, int light, float time) {
        m.transformPosition(bilerp(VX, s, t) / 16f, bilerp(VY, s, t) / 16f, bilerp(VZ, s, t) / 16f, P);
        int c = a.colourAt(Paint.clamp(height(j, P)), time);
        vc.addVertex(P.x, P.y, P.z, c, bilerp(VU, s, t), bilerp(VV, s, t), overlay, light, N.x, N.y, N.z);
    }

    private static RenderType outlineType;
    private static boolean outlineFailed;

    static RenderType outlineType() {
        if (outlineType != null || outlineFailed) return outlineType;
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
    }

    /** Right after vanilla submitted an entity's body: submit the outline shells (and fill) for enemies. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void hull(Model model, LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector c) {
        Outline o = Modules.OUTLINE;
        if (!o.enabled() || o.chams() || irisShadowPass()) return; // chams are drawn by ArcChams
        if (!(Ducks.entityOf(state) instanceof LivingEntity e) || !o.wants(e)) return;
        RenderType rt = outlineType();
        if (rt == null) return;
        Minecraft mc = Minecraft.getInstance();
        Matrix4f m = pose.last().pose();
        Job j = job();
        j.inv.set(m).invert();
        float sy =
                (float) Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12()); // blocks per model unit
        if (sy < 1e-5f) return;
        j.height = Math.max(.3f, state.boundingBoxHeight) / sy;
        // the same width on screen near and far: thickness in screen pixels turned into blocks at this distance
        double dist = Math.sqrt(state.distanceToCameraSq);
        double fov = Math.toRadians(mc.options.fov().get());
        float worldPerPx = (float)
                (2 * dist * Math.tan(fov / 2) / Math.max(1, mc.getWindow().getHeight()));
        float world = Math.max(.012f, o.thickness.f() * worldPerPx);
        j.thick = Math.min(world, .09f) / sy * 16;
        j.health = Math.max(0, Math.min(1, e.getHealth() / Math.max(1, e.getMaxHealth())));
        j.flash = o.hitFlash.on() && state.hasRedOverlay;
        int light = LightTexture.FULL_BRIGHT, ov = OverlayTexture.NO_OVERLAY;
        if (o.glow.on()) c.submitModel(model, state, pose, rt, light, ov, magic(j, LAYER_GLOW), null, 0, null);
        c.submitModel(model, state, pose, rt, light, ov, magic(j, LAYER_HULL), null, 0, null);
        if (o.fill.on()) c.submitModel(model, state, pose, rt, light, ov, magic(j, LAYER_FILL), null, 0, null);
    }

    private static void hullCube(
            ModelPart.Cube c, PoseStack.Pose pose, VertexConsumer vc, Job j, int layer, float time) {
        Outline o = Modules.OUTLINE;
        float t = layer == LAYER_GLOW ? j.thick * 2.5f : layer == LAYER_HULL ? j.thick : .05f; // model pixels
        boolean shell = layer != LAYER_FILL; // shells show their inner side: the rim around the body
        float alpha = (float) (o.opacity.get() / 100.0) * (layer == LAYER_GLOW ? .42f : 1);
        if (layer == LAYER_FILL) alpha = (float) (o.fillOpacity.get() / 100.0);
        float cx = (c.minX + c.maxX) / 2, cy = (c.minY + c.maxY) / 2, cz = (c.minZ + c.maxZ) / 2;
        Matrix4f m = pose.pose();
        for (ModelPart.Polygon poly : c.polygons) {
            ModelPart.Vertex[] vs = poly.vertices();
            if (vs.length != 4) continue;
            pose.transformNormal(poly.normal(), N);
            if (shell) N.negate();
            for (int k = 0; k < 4; k++) {
                ModelPart.Vertex v = vs[shell ? 3 - k : k];
                float x = v.x() + Math.signum(v.x() - cx) * t,
                        y = v.y() + Math.signum(v.y() - cy) * t,
                        z = v.z() + Math.signum(v.z() - cz) * t;
                m.transformPosition(x / 16f, y / 16f, z / 16f, P);
                int col = o.colourAt(height(j, P), time, j.health);
                if (j.flash) col = Paint.lerp(col, 0xFFFF3B4E, .75f);
                if (layer == LAYER_FILL) col = Paint.lerp(col, 0xFFFFFFFF, .15f);
                col = (Math.round(alpha * 255) << 24) | (col & 0xFFFFFF);
                vc.addVertex(
                        P.x,
                        P.y,
                        P.z,
                        col,
                        .5f,
                        .5f,
                        OverlayTexture.NO_OVERLAY,
                        LightTexture.FULL_BRIGHT,
                        N.x,
                        N.y,
                        N.z);
            }
        }
    }

    // shadows)
    private static Method shadowPass;
    private static Object iris;
    private static boolean irisChecked;

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
        if (shadowPass == null) return false;
        try {
            return (boolean) shadowPass.invoke(iris);
        } catch (Throwable e) {
            return false;
        }
    }
}
