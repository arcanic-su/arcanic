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

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import net.arcanic.visuals.core.Modules;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/** Chams (effect, compositor and shaders). */
public final class ArcChams {
    private ArcChams() {}

    private static TextureTarget mask, effect;
    private static RenderBuffers buffers;
    private static SubmitNodeStorage queue;
    private static FeatureRenderDispatcher dispatcher;
    private static final CameraRenderState CAMERA = new CameraRenderState();
    private static int apply, present, vao, vbo, fbo, quadW = -1, quadH = -1;
    private static boolean broken;
    private static final long START = System.currentTimeMillis();

    /** END_MAIN: all wanted enemies through the mask and the composite, then onto the frame. */
    public static void render(WorldRenderContext ctx) {
        var o = Modules.OUTLINE;
        if (broken || !o.enabled() || !o.chams()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            var main = mc.getMainRenderTarget();
            int w = main.width, h = main.height;
            boolean prepared = false, any = false;
            var camera = mc.gameRenderer.getMainCamera();
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity le) || !o.wants(le)) continue;
                if (!prepared) {
                    setup(w, h);
                    RenderSystem.getDevice().createCommandEncoder().clearColorTexture(effect.getColorTexture(), 0);
                    mc.getEntityRenderDispatcher().prepare(camera, mc.getCameraEntity());
                    CAMERA.initialized = true;
                    CAMERA.pos = camera.position();
                    CAMERA.blockPos = camera.blockPosition();
                    CAMERA.entityPos = CAMERA.pos;
                    CAMERA.orientation = camera.rotation();
                    prepared = true;
                }
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .clearColorAndDepthTextures(mask.getColorTexture(), 0, mask.getDepthTexture(), 1.0);
                if (!toMask(mc, ctx, e, pt)) continue;
                float health = Math.max(0, Math.min(1, le.getHealth() / Math.max(1, le.getMaxHealth())));
                float time = (System.currentTimeMillis() - START) / 1000f;
                int visible = o.colourAt(.5f, time, health), hidden = o.colourAt(.5f, time, health);
                visible = (Math.round((float) (o.opacity.get() / 100.0) * 255) << 24) | (visible & 0xFFFFFF);
                hidden = (Math.round((float) (o.opacity.get() / 100.0) * 150) << 24) | (hidden & 0xFFFFFF);
                composite(
                        main,
                        w,
                        h,
                        visible,
                        hidden,
                        true,
                        o.walls.on(),
                        o.material(),
                        (System.currentTimeMillis() - START) / 700f);
                any = true;
            }
            if (any) present(main, w, h);
        } catch (Throwable t) {
            broken = true;
            System.err.println("[ArcanicVisuals] chams off: " + t);
        }
    }

    private static void setup(int w, int h) {
        if (mask == null) {
            mask = new TextureTarget("Arcanic chams mask", w, h, true);
            effect = new TextureTarget("Arcanic chams effect", w, h, true);
        } else if (mask.width != w || mask.height != h) {
            mask.resize(w, h);
            effect.resize(w, h);
        }
        if (dispatcher == null) {
            Minecraft mc = Minecraft.getInstance();
            buffers = new RenderBuffers(1);
            queue = new SubmitNodeStorage();
            dispatcher = new FeatureRenderDispatcher(
                    queue,
                    mc.getBlockRenderer(),
                    buffers.bufferSource(),
                    mc.getAtlasManager(),
                    buffers.outlineBufferSource(),
                    buffers.crumblingBufferSource(),
                    mc.font);
        }
    }

    private static boolean toMask(Minecraft mc, WorldRenderContext ctx, Entity e, float pt) {
        var erd = mc.getEntityRenderDispatcher();
        EntityRenderState s = erd.extractEntity(e, pt);
        if (s == null) return false;
        s.nameTag = null;
        s.nameTagAttachment = null;
        s.displayFireAnimation = false;
        s.outlineColor = 0;
        s.isInvisible = false;
        s.shadowRadius = 0;
        s.shadowPieces.clear();
        GpuTextureView pc = RenderSystem.outputColorTextureOverride, pd = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = mask.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = mask.getDepthTextureView();
        boolean ok = false;
        try {
            PoseStack pose = ctx.matrices();
            Vec3 cam = CAMERA.pos;
            pose.pushPose();
            try {
                erd.submit(s, CAMERA, s.x - cam.x, s.y - cam.y, s.z - cam.z, pose, queue);
            } finally {
                pose.popPose();
            }
            dispatcher.renderAllFeatures();
            buffers.bufferSource().endBatch();
            buffers.crumblingBufferSource().endBatch();
            buffers.outlineBufferSource().endOutlineBatch();
            dispatcher.endFrame();
            ok = true;
        } catch (Throwable t) {
            try {
                queue.clear();
            } catch (Throwable ignored) {
            }
        } finally {
            RenderSystem.outputColorTextureOverride = pc;
            RenderSystem.outputDepthTextureOverride = pd;
        }
        return ok;
    }

    private static int loc(int p, String n) {
        return GL20.glGetUniformLocation(p, n);
    }

    private static void init() {
        if (apply != 0) return;
        apply = program("chams_apply.frag");
        present = program("chams_present.frag");
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 120L, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 20, 0L);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 20, 12L);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        fbo = GL30.glGenFramebuffers();
    }

    private static int program(String frag) {
        int p = GL20.glCreateProgram(),
                v = shader(GL20.GL_VERTEX_SHADER, "chams_screen.vert"),
                f = shader(GL20.GL_FRAGMENT_SHADER, frag);
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0)
            throw new IllegalStateException("chams link: " + GL20.glGetProgramInfoLog(p));
        return p;
    }

    private static int shader(int type, String name) {
        String src;
        try (InputStream in = ArcChams.class.getResourceAsStream("/assets/arcanicvisuals/shaders/chams/" + name)) {
            src = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(name, e);
        }
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0)
            throw new IllegalStateException(name + ": " + GL20.glGetShaderInfoLog(s));
        return s;
    }

    private static void quad(int w, int h) {
        if (quadW == w && quadH == h) return;
        FloatBuffer b = BufferUtils.createFloatBuffer(30);
        float[][] v = {{0, 0, 0, 1}, {w, 0, 1, 1}, {w, h, 1, 0}, {0, 0, 0, 1}, {w, h, 1, 0}, {0, h, 0, 0}};
        for (float[] p : v) b.put(p[0]).put(p[1]).put(0).put(p[2]).put(p[3]);
        b.flip();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, b);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        quadW = w;
        quadH = h;
    }

    private static int id(GpuTextureView v) {
        return v != null && !v.isClosed() && v.texture() instanceof GlTexture t && !t.isClosed() ? t.glId() : 0;
    }

    private static void target(int tex) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL33.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        GL33.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
    }

    private static void bind(int unit, int tex) {
        GlStateManager._activeTexture(GL33.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(tex);
        GL33.glBindSampler(unit, 0);
    }

    private static void colour(int p, String n, int c) {
        GL20.glUniform4f(loc(p, n), (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f);
    }

    private static void composite(
            RenderTarget main,
            int w,
            int h,
            int visible,
            int hidden,
            boolean showVisible,
            boolean showHidden,
            int mode,
            float time) {
        init();
        quad(w, h);
        Snapshot st = new Snapshot();
        try {
            target(id(effect.getColorTextureView()));
            GlStateManager._viewport(0, 0, w, h);
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableCull();
            GlStateManager._disableScissorTest();
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(770, 771, 1, 771);
            GL20.glUseProgram(apply);
            GL20.glUniform2f(loc(apply, "uScreenSize"), w, h);
            GL20.glUniform1i(loc(apply, "uMaskTexture"), 0);
            GL20.glUniform1i(loc(apply, "uSceneTexture"), 1);
            GL20.glUniform1i(loc(apply, "uMaskDepthTexture"), 2);
            GL20.glUniform1i(loc(apply, "uSceneDepthTexture"), 3);
            GL20.glUniform1i(loc(apply, "uMode"), mode);
            GL20.glUniform1i(loc(apply, "uVisibleEnabled"), showVisible ? 1 : 0);
            GL20.glUniform1i(loc(apply, "uHiddenEnabled"), showHidden ? 1 : 0);
            colour(apply, "uColor", visible);
            colour(apply, "uHiddenColor", hidden);
            GL20.glUniform2f(loc(apply, "uLocation"), 0, 0);
            GL20.glUniform2f(loc(apply, "uRectSize"), w, h);
            GL20.glUniform1f(loc(apply, "uTime"), time);
            bind(0, id(mask.getColorTextureView()));
            bind(1, id(main.getColorTextureView()));
            bind(2, id(mask.getDepthTextureView()));
            bind(3, id(main.getDepthTextureView()));
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GL30.glBindVertexArray(0);
        } finally {
            st.restore();
        }
    }

    private static void present(RenderTarget main, int w, int h) {
        Snapshot st = new Snapshot();
        try {
            target(id(main.getColorTextureView()));
            GlStateManager._viewport(0, 0, w, h);
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableCull();
            GlStateManager._disableScissorTest();
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(770, 771, 1, 771);
            GL20.glUseProgram(present);
            GL20.glUniform2f(loc(present, "uScreenSize"), w, h);
            GL20.glUniform1i(loc(present, "uTexture"), 0);
            bind(0, id(effect.getColorTextureView()));
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GL30.glBindVertexArray(0);
        } finally {
            st.restore();
        }
    }

    /** OpenGlStateSnapshot: what we touch, put back exactly. */
    private static final class Snapshot {
        final int fb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                vaoB = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                ab = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        final int prog = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), active = GL11.glGetInteger(GL33.GL_ACTIVE_TEXTURE);
        final int srcRgb = GL11.glGetInteger(GL33.GL_BLEND_SRC_RGB),
                dstRgb = GL11.glGetInteger(GL33.GL_BLEND_DST_RGB),
                srcA = GL11.glGetInteger(GL33.GL_BLEND_SRC_ALPHA),
                dstA = GL11.glGetInteger(GL33.GL_BLEND_DST_ALPHA);
        final int[] tex = new int[4], smp = new int[4], vp = new int[4], sc = new int[4];
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                cull = GL11.glIsEnabled(GL11.GL_CULL_FACE),
                scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                blend = GL11.glIsEnabled(GL11.GL_BLEND),
                mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        Snapshot() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, sc);
            for (int u = 0; u < 4; u++) {
                tex[u] = GL30.glGetIntegeri(GL33.GL_TEXTURE_BINDING_2D, u);
                smp[u] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, u);
            }
        }

        void restore() {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fb);
            GL30.glBindVertexArray(vaoB);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, ab);
            GL20.glUseProgram(prog);
            for (int u = 0; u < 4; u++) {
                GlStateManager._activeTexture(GL33.GL_TEXTURE0 + u);
                GlStateManager._bindTexture(tex[u]);
                GL33.glBindSampler(u, smp[u]);
            }
            GlStateManager._activeTexture(active);
            GlStateManager._viewport(vp[0], vp[1], vp[2], vp[3]);
            GlStateManager._scissorBox(sc[0], sc[1], sc[2], sc[3]);
            if (depth) GlStateManager._enableDepthTest();
            else GlStateManager._disableDepthTest();
            if (cull) GlStateManager._enableCull();
            else GlStateManager._disableCull();
            if (scissor) GlStateManager._enableScissorTest();
            else GlStateManager._disableScissorTest();
            if (blend) GlStateManager._enableBlend();
            else GlStateManager._disableBlend();
            GlStateManager._depthMask(mask);
            GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
        }
    }
}
