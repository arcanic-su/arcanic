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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

public final class ArcChams {
    private static TextureTarget mask;
    private static TextureTarget effect;
    private static RenderBuffers buffers;
    private static SubmitNodeStorage queue;
    private static FeatureRenderDispatcher dispatcher;
    private static final CameraRenderState CAMERA = new CameraRenderState();
    private static int apply;
    private static int present;
    private static int vao;
    private static int vbo;
    private static int fbo;
    private static int quadW = -1;
    private static int quadH = -1;
    private static boolean broken;
    private static final long START = System.currentTimeMillis();

    private ArcChams() {}

    public static void render(WorldRenderContext ctx) {
        Outline o = Modules.OUTLINE;
        boolean enemies = o.enabled() && o.chams();
        boolean friends = Friends.anyGlow();
        if (!broken && (enemies || friends)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                try {
                    RenderTarget main = mc.getMainRenderTarget();
                    int w = main.width;
                    int h = main.height;
                    boolean prepared = false;
                    boolean any = false;
                    Camera camera = mc.gameRenderer.getMainCamera();
                    float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

                    for (Entity e : mc.level.entitiesForRendering()) {
                        if (e instanceof LivingEntity) {
                            LivingEntity le = (LivingEntity) e;
                            boolean friend = friends && Friends.glows(le);
                            if (friend || enemies && o.wants(le)) {
                                if (!prepared) {
                                    setup(w, h);
                                    RenderSystem.getDevice()
                                            .createCommandEncoder()
                                            .clearColorTexture(effect.getColorTexture(), 0);
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
                                        .clearColorAndDepthTextures(
                                                mask.getColorTexture(), 0, mask.getDepthTexture(), 1.0);
                                if (toMask(mc, ctx, e, pt)) {
                                    float health = Math.max(
                                            0.0F, Math.min(1.0F, le.getHealth() / Math.max(1.0F, le.getMaxHealth())));
                                    float time = (float) (System.currentTimeMillis() - START) / 1000.0F;
                                    int rgb = friend ? Friends.glowColour() : o.colourAt(0.5F, time, health);
                                    float opacity = enemies ? o.opacity.f() / 100.0F : 0.95F;
                                    int visible = Math.round(opacity * 255.0F) << 24 | rgb & 0xFFFFFF;
                                    int hidden = Math.round(opacity * 150.0F) << 24 | rgb & 0xFFFFFF;
                                    composite(
                                            main,
                                            w,
                                            h,
                                            visible,
                                            hidden,
                                            true,
                                            false,
                                            enemies ? o.material() : 1,
                                            (float) (System.currentTimeMillis() - START) / 700.0F);
                                    any = true;
                                }
                            }
                        }
                    }

                    if (any) {
                        present(main, w, h);
                    }
                } catch (Throwable t) {
                    broken = true;
                    System.err.println("[ArcanicVisuals] chams off: " + t);
                }
            }
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
        EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
        EntityRenderState s = erd.extractEntity(e, pt);
        if (s == null) {
            return false;
        } else {
            s.nameTag = null;
            s.nameTagAttachment = null;
            s.displayFireAnimation = false;
            s.outlineColor = 0;
            s.isInvisible = false;
            s.shadowRadius = 0.0F;
            s.shadowPieces.clear();
            GpuTextureView pc = RenderSystem.outputColorTextureOverride;
            GpuTextureView pd = RenderSystem.outputDepthTextureOverride;
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
    }

    private static int loc(int p, String n) {
        return GL20.glGetUniformLocation(p, n);
    }

    private static void init() {
        if (apply == 0) {
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
    }

    private static int program(String frag) {
        int p = GL20.glCreateProgram();
        int v = shader(GL20.GL_VERTEX_SHADER, "chams_screen.vert");
        int f = shader(GL20.GL_FRAGMENT_SHADER, frag);
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("chams link: " + GL20.glGetProgramInfoLog(p));
        } else {
            return p;
        }
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
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException(name + ": " + GL20.glGetShaderInfoLog(s));
        } else {
            return s;
        }
    }

    private static void quad(int w, int h) {
        if (quadW != w || quadH != h) {
            FloatBuffer b = BufferUtils.createFloatBuffer(30);
            float[][] v = new float[][] {
                {0.0F, 0.0F, 0.0F, 1.0F},
                {(float) w, 0.0F, 1.0F, 1.0F},
                {(float) w, (float) h, 1.0F, 0.0F},
                {0.0F, 0.0F, 0.0F, 1.0F},
                {(float) w, (float) h, 1.0F, 0.0F},
                {0.0F, (float) h, 0.0F, 0.0F}
            };

            for (float[] p : v) {
                b.put(p[0]).put(p[1]).put(0.0F).put(p[2]).put(p[3]);
            }

            b.flip();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, b);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            quadW = w;
            quadH = h;
        }
    }

    private static int id(GpuTextureView v) {
        if (v != null && !v.isClosed() && v.texture() instanceof GlTexture t && !t.isClosed()) {
            return t.glId();
        }
        return 0;
    }

    private static void target(int tex) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL33.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        GL33.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
    }

    private static void bind(int unit, int tex) {
        GlStateManager._activeTexture('蓀' + unit);
        GlStateManager._bindTexture(tex);
        GL33.glBindSampler(unit, 0);
    }

    private static void colour(int p, String n, int c) {
        GL20.glUniform4f(
                loc(p, n),
                (float) (c >> 16 & 255) / 255.0F,
                (float) (c >> 8 & 255) / 255.0F,
                (float) (c & 255) / 255.0F,
                (float) (c >>> 24) / 255.0F);
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
            GL20.glUniform2f(loc(apply, "uScreenSize"), (float) w, (float) h);
            GL20.glUniform1i(loc(apply, "uMaskTexture"), 0);
            GL20.glUniform1i(loc(apply, "uSceneTexture"), 1);
            GL20.glUniform1i(loc(apply, "uMaskDepthTexture"), 2);
            GL20.glUniform1i(loc(apply, "uSceneDepthTexture"), 3);
            GL20.glUniform1i(loc(apply, "uMode"), mode);
            GL20.glUniform1i(loc(apply, "uVisibleEnabled"), showVisible ? 1 : 0);
            GL20.glUniform1i(loc(apply, "uHiddenEnabled"), showHidden ? 1 : 0);
            colour(apply, "uColor", visible);
            colour(apply, "uHiddenColor", hidden);
            GL20.glUniform2f(loc(apply, "uLocation"), 0.0F, 0.0F);
            GL20.glUniform2f(loc(apply, "uRectSize"), (float) w, (float) h);
            GL20.glUniform1f(loc(apply, "uTime"), time);
            bind(0, id(mask.getColorTextureView()));
            bind(1, id(main.getColorTextureView()));
            bind(2, id(mask.getDepthTextureView()));
            bind(3, id(main.getDepthTextureView()));
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(4, 0, 6);
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
            GL20.glUniform2f(loc(present, "uScreenSize"), (float) w, (float) h);
            GL20.glUniform1i(loc(present, "uTexture"), 0);
            bind(0, id(effect.getColorTextureView()));
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(4, 0, 6);
            GL30.glBindVertexArray(0);
        } finally {
            st.restore();
        }
    }

    private static final class Snapshot {
        final int fb = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int vaoB = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int ab = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        final int prog = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        final int dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int[] tex = new int[4];
        final int[] smp = new int[4];
        final int[] vp = new int[4];
        final int[] sc = new int[4];
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        Snapshot() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, this.vp);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, this.sc);
            int was = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

            for (int u = 0; u < 4; u++) {
                GL33.glActiveTexture('蓀' + u);
                this.tex[u] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                this.smp[u] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            }

            GL33.glActiveTexture(was);
        }

        void restore() {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fb);
            GL30.glBindVertexArray(this.vaoB);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.ab);
            GL20.glUseProgram(this.prog);

            for (int u = 0; u < 4; u++) {
                GlStateManager._activeTexture('蓀' + u);
                GlStateManager._bindTexture(this.tex[u]);
                GL33.glBindSampler(u, this.smp[u]);
            }

            GlStateManager._activeTexture(this.active);
            GlStateManager._viewport(this.vp[0], this.vp[1], this.vp[2], this.vp[3]);
            GlStateManager._scissorBox(this.sc[0], this.sc[1], this.sc[2], this.sc[3]);
            if (this.depth) {
                GlStateManager._enableDepthTest();
            } else {
                GlStateManager._disableDepthTest();
            }

            if (this.cull) {
                GlStateManager._enableCull();
            } else {
                GlStateManager._disableCull();
            }

            if (this.scissor) {
                GlStateManager._enableScissorTest();
            } else {
                GlStateManager._disableScissorTest();
            }

            if (this.blend) {
                GlStateManager._enableBlend();
            } else {
                GlStateManager._disableBlend();
            }

            GlStateManager._depthMask(this.mask);
            GlStateManager._blendFuncSeparate(this.srcRgb, this.dstRgb, this.srcA, this.dstA);
        }
    }
}
