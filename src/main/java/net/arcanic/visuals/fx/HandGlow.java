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
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

public final class HandGlow extends Module {
    public final Setting.Num strength = this.add(new Setting.Num("strength", "Сила свечения", 3.0, 1.0, 5.0, 1.0, ""));
    public final Setting.Mode colourMode =
            this.add(new Setting.Mode("colour_mode", "Цвет", "Переливание", "Тема", "Свой", "Переливание"));
    public final Setting.Colour colour = (this.add(new Setting.Colour("colour", "Свой цвет", false, 0xFF3399FF)))
            .showIf(() -> this.colourMode.is("Свой"));
    public final Setting.Colour gradientA = (this.add(new Setting.Colour("gradient_a", "Цвет 1", false, 0xFF1E78FF)))
            .showIf(() -> this.colourMode.is("Переливание"));
    public final Setting.Colour gradientB = (this.add(new Setting.Colour("gradient_b", "Цвет 2", false, 0xFF00C8FF)))
            .showIf(() -> this.colourMode.is("Переливание"));
    public final Setting.Bool chamsFill = this.add(new Setting.Bool("fill", "Заливка предмета", true));
    public final Setting.Num fillAlpha;
    public final Setting.Bool trail;
    public final Setting.Num trailLength;
    public final Setting.Num trailRise;
    public final Setting.Num trailWobble;
    public final Setting.Num trailBright;
    public final Setting.Num trailSoft;
    private static final int PASSES = 8;
    private static final float BLUR_OFFSET = 2.5F;
    private static final float GLOW = 7.0F;
    private static final float OUTLINE_STRENGTH = 1.0F;
    private static final float OUTLINE_RADIUS = 1.0F;
    private static final float GLOW_OFFSET_X = 0.0F;
    private static final float GLOW_OFFSET_Y = -8.0F;
    private static final int TRAIL_PASSES = 3;
    private static final float TRAIL_OFFSET = 2.2F;
    private static final float TRAIL_CUT = 0.85F;
    private static final List<Held> ITEMS = new ArrayList<>();
    private static boolean inHands;
    private static boolean replaying;
    private static boolean broken;
    private static boolean trailLive;
    private static long lastFrame;
    private static long lastItems;
    private static long lastNanos;
    private static float step = 1.0F;
    private static TextureTarget items;
    private static RenderBuffers buffers;
    private static SubmitNodeStorage queue;
    private static FeatureRenderDispatcher dispatcher;
    private static int fill;
    private static int blurH;
    private static int blurV;
    private static int composite;
    private static int present;
    private static int shift;
    private static int chams;
    private static int vao;
    private static int fbo;
    private static int ping;
    private static int pong;
    private static int result;
    private static int acc;
    private static int tmp;
    private static int out;
    private static int smallW = -1;
    private static int smallH = -1;
    private static int fullW = -1;
    private static int fullH = -1;

    public HandGlow() {
        super("hand_glow", "Свечение в руке", "Светящийся ореол и шлейф за предметами в руках", Category.HANDS, false);
        Setting.Num num = this.add(new Setting.Num("fill_alpha", "Плотность заливки", 100.0, 10.0, 100.0, 5.0, "%"));
        this.fillAlpha = (Setting.Num) num.showIf(this.chamsFill::on);
        this.trail = this.add(new Setting.Bool("trail", "Шлейф", true));
        this.trailLength = this.add(new Setting.Num("trail_length", "Длина шлейфа", 50.0, 0.0, 80.0, 1.0, ""))
                .showIf(this.trail::on);
        this.trailRise = this.add(new Setting.Num("trail_rise", "Подъём шлейфа", 2.5, 0.5, 4.0, 0.1, ""))
                .showIf(this.trail::on);
        this.trailWobble = this.add(new Setting.Num("trail_wobble", "Колыхание", 2.0, 0.0, 2.0, 0.1, ""))
                .showIf(this.trail::on);
        this.trailBright = this.add(new Setting.Num("trail_bright", "Яркость шлейфа", 2.0, 0.5, 4.0, 0.1, ""))
                .showIf(this.trail::on);
        this.trailSoft = this.add(new Setting.Num("trail_soft", "Мягкость шлейфа", 3.0, 1.0, 5.0, 1.0, ""))
                .showIf(this.trail::on);
    }

    protected void onDisable() {
        ITEMS.clear();
        trailLive = false;
    }

    public static void beginHands() {
        ITEMS.clear();
        inHands = !broken && Modules.HAND_GLOW.enabled();
    }

    public static void record(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose) {
        if (inHands && !replaying && stack != null && !stack.isEmpty()) {
            PoseStack own = new PoseStack();
            own.last().set(pose.last());
            ITEMS.add(new Held(entity, stack, context, own));
        }
    }

    public static void flush(ItemInHandRenderer renderer) {
        boolean wanted = inHands;
        inHands = false;
        if (!wanted) {
            ITEMS.clear();
        } else {
            HandGlow m = Modules.HAND_GLOW;
            long now = System.currentTimeMillis();
            if (now - lastFrame > 250L) {
                trailLive = false;
            }

            lastFrame = now;
            long nanos = System.nanoTime();
            step = lastNanos == 0L ? 1.0F : Math.max(0.0F, Math.min(4.0F, (float) (nanos - lastNanos) / 1.6666667E7F));
            lastNanos = nanos;
            Minecraft mc = Minecraft.getInstance();

            try {
                RenderTarget main = mc.getMainRenderTarget();
                int w = main.width;
                int h = main.height;
                setup(w, h);
                boolean captured = false;
                if (!ITEMS.isEmpty()) {
                    RenderSystem.getDevice()
                            .createCommandEncoder()
                            .clearColorAndDepthTextures(items.getColorTexture(), 0, items.getDepthTexture(), 1.0);
                    captured = capture(renderer);
                }

                if (captured) {
                    lastItems = now;
                } else if (now - lastItems > 2000L) {
                    trailLive = false;
                }

                boolean trailOn = m.trail.on();
                if (!trailOn) {
                    trailLive = false;
                }

                if (captured || trailLive) {
                    render(id(main.getColorTextureView()), w, h, captured, trailOn, mc);
                    return;
                }
            } catch (Throwable t) {
                broken = true;
                System.err.println("[ArcanicVisuals] hand glow off: " + t);
                return;
            } finally {
                ITEMS.clear();
            }
        }
    }

    private static boolean capture(ItemInHandRenderer renderer) {
        GpuTextureView pc = RenderSystem.outputColorTextureOverride;
        GpuTextureView pd = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = items.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = items.getDepthTextureView();
        replaying = true;

        boolean held;
        try {
            for (Held h : ITEMS) {
                renderer.renderItem(h.entity(), h.stack(), h.context(), h.pose(), queue, 15728880);
            }

            dispatcher.renderAllFeatures();
            buffers.bufferSource().endBatch();
            buffers.crumblingBufferSource().endBatch();
            buffers.outlineBufferSource().endOutlineBatch();
            dispatcher.endFrame();
            return true;
        } catch (Throwable e) {
            try {
                queue.clear();
            } catch (Throwable ignored) {
            }

            held = false;
        } finally {
            replaying = false;
            RenderSystem.outputColorTextureOverride = pc;
            RenderSystem.outputDepthTextureOverride = pd;
        }

        return held;
    }

    private static void setup(int w, int h) {
        if (items == null) {
            items = new TextureTarget("Arcanic hand glow", w, h, true);
        } else if (items.width != w || items.height != h) {
            items.resize(w, h);
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

        if (fill == 0) {
            fill = program("fill.frag");
            blurH = program("blur_h.frag");
            blurV = program("blur_v.frag");
            composite = program("composite.frag");
            present = program("present.frag");
            shift = program("shift.frag");
            chams = program("chams.frag");
            vao = GL30.glGenVertexArrays();
            fbo = GL30.glGenFramebuffers();
        }

        int sw = Math.max(1, (int) Math.floor((double) ((float) w * 0.5F)));
        int sh = Math.max(1, (int) Math.floor((double) ((float) h * 0.5F)));
        if (sw != smallW || sh != smallH) {
            ping = texture(ping, sw, sh);
            pong = texture(pong, sw, sh);
            smallW = sw;
            smallH = sh;
        }

        if (w != fullW || h != fullH) {
            result = texture(result, w, h);
            acc = texture(acc, w, h);
            tmp = texture(tmp, w, h);
            out = texture(out, w, h);
            fullW = w;
            fullH = h;
            trailLive = false;
        }
    }

    private static int texture(int old, int w, int h) {
        if (old != 0) {
            GL11.glDeleteTextures(old);
        }

        int t = GL11.glGenTextures();
        GlStateManager._bindTexture(t);
        int pbo = AmbienceWeather.cleanUnpack();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        AmbienceWeather.restoreUnpack(pbo);
        linear();
        GlStateManager._bindTexture(0);
        return t;
    }

    private static void linear() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
    }

    private static void render(int screen, int w, int h, boolean captured, boolean trailOn, Minecraft mc) {
        int source = id(items.getColorTextureView());
        if (source != 0 && screen != 0) {
            HandGlow m = Modules.HAND_GLOW;
            double gs = (double) mc.getWindow().getGuiScale();
            Snapshot st = new Snapshot();

            try {
                GlStateManager._disableDepthTest();
                GlStateManager._depthMask(false);
                GlStateManager._disableCull();
                GlStateManager._disableScissorTest();
                GlStateManager._enableBlend();
                GL30.glBindVertexArray(vao);
                if (captured && m.chamsFill.on()) {
                    target(screen, w, h, false);
                    GlStateManager._blendFuncSeparate(1, 771, 1, 771);
                    colours(chams, h, gs);
                    GL20.glUniform1i(loc(chams, "Sampler0"), 0);
                    GL20.glUniform1f(loc(chams, "Alpha"), m.fillAlpha.f() / 100.0F);
                    bind(0, source);
                    draw(1);
                }

                if (captured) {
                    target(source, w, h, false);
                    GlStateManager._blendFuncSeparate(772, 0, 0, 1);
                    paint(h, gs);
                    glow(screen, source, w, h, m.strength.i(), gs);
                }

                if (trailOn) {
                    trail(screen, source, w, h, captured, gs);
                }
            } finally {
                GL30.glBindVertexArray(0);
                st.restore();
            }
        }
    }

    private static void paint(int h, double gs) {
        HandGlow m = Modules.HAND_GLOW;
        int c = m.colourMode.is("Свой") ? m.colour.argb() : Theme.accent();
        GL20.glUseProgram(fill);
        GL20.glUniform4f(
                loc(fill, "Color"),
                (float) (c >> 16 & 255) / 255.0F,
                (float) (c >> 8 & 255) / 255.0F,
                (float) (c & 255) / 255.0F,
                1.0F);
        colours(fill, h, gs);
        draw(1);
    }

    private static void colours(int program, int h, double gs) {
        HandGlow m = Modules.HAND_GLOW;
        boolean gradient = m.colourMode.is("Переливание");
        int c = m.colourMode.is("Свой") ? m.colour.argb() : Theme.accent();
        GL20.glUseProgram(program);
        GL20.glUniform3f(
                loc(program, "Colour"),
                (float) (c >> 16 & 255) / 255.0F,
                (float) (c >> 8 & 255) / 255.0F,
                (float) (c & 255) / 255.0F);
        GL20.glUniform1i(loc(program, "Gradient"), gradient ? 1 : 0);
        if (gradient) {
            int a = m.gradientA.argb();
            int b = m.gradientB.argb();
            float band = (float) h / 4.0F;
            float scroll = (float)
                    ((double) (System.currentTimeMillis() % 1000000L) / 1000.0 * 80.0 * gs % (double) (2.0F * band));
            GL20.glUniform3f(
                    loc(program, "ColA"),
                    (float) (a >> 16 & 255) / 255.0F,
                    (float) (a >> 8 & 255) / 255.0F,
                    (float) (a & 255) / 255.0F);
            GL20.glUniform3f(
                    loc(program, "ColB"),
                    (float) (b >> 16 & 255) / 255.0F,
                    (float) (b >> 8 & 255) / 255.0F,
                    (float) (b & 255) / 255.0F);
            GL20.glUniform3f(loc(program, "Band"), band, scroll, (float) h);
        }
    }

    private static void glow(int screen, int source, int w, int h, int repeat, double gs) {
        defaultBlend();
        blurPasses(source, repeat, 8, 2.5F);
        target(result, w, h, true);
        GL20.glUseProgram(composite);
        GL20.glUniform1i(loc(composite, "Sampler0"), 0);
        GL20.glUniform1i(loc(composite, "Sampler1"), 1);
        GL20.glUniform2f(loc(composite, "Resolution"), 1.0F / (float) w, 1.0F / (float) h);
        GL20.glUniform1f(loc(composite, "Offset"), 7.0F);
        GL20.glUniform1f(loc(composite, "OutlineStrength"), 1.0F);
        GL20.glUniform1f(loc(composite, "OutlineRadius"), 1.0F);
        GL20.glUniform2f(loc(composite, "GlowOffset"), 0.0F, -8.0F);
        bind(0, source);
        bind(1, pong);
        draw(1);
        target(screen, w, h, false);
        GlStateManager._blendFuncSeparate(1, 1, 1, 1);
        present(result, 0.0F, (float) (-0.5 * gs / (double) h), 1.0F, 1.0F);
        draw(2);
    }

    private static void trail(int screen, int source, int w, int h, boolean captured, double gs) {
        HandGlow m = Modules.HAND_GLOW;
        if (!trailLive) {
            target(acc, w, h, true);
        } else {
            target(tmp, w, h, true);
            GlStateManager._blendFuncSeparate(1, 0, 1, 0);
            GL20.glUseProgram(shift);
            GL20.glUniform1i(loc(shift, "Sampler0"), 0);
            GL20.glUniform1f(
                    loc(shift, "Rise"), (float) ((Double) m.trailRise.get() * gs / (double) h * (double) step));
            GL20.glUniform1f(
                    loc(shift, "Wobble"), (float) ((Double) m.trailWobble.get() * gs / (double) w * (double) step));
            GL20.glUniform1f(loc(shift, "Time"), (float) (System.currentTimeMillis() % 100000L) / 1000.0F);
            bind(0, acc);
            draw(1);
            int swap = acc;
            acc = tmp;
            tmp = swap;
            float f = 0.8F + Math.max(0.0F, Math.min(1.0F, m.trailLength.f() / 80.0F)) * 0.15F;
            if (!captured) {
                f += (1.0F - f) * 0.5F;
            }

            f = (float) Math.pow((double) f, (double) step);
            target(acc, w, h, false);
            GlStateManager._blendFuncSeparate(0, 770, 0, 770);
            GL20.glUseProgram(fill);
            GL20.glUniform4f(loc(fill, "Color"), 0.0F, 0.0F, 0.0F, f);
            GL20.glUniform1i(loc(fill, "Gradient"), 0);
            draw(1);
        }

        int blurred;
        if (captured) {
            target(acc, w, h, false);
            premultipliedBlend();
            present(source, 0.0F, 0.0F, 1.0F, 1.0F);
            draw(1);
            trailLive = true;
            target(out, w, h, true);
            GlStateManager._blendFuncSeparate(1, 0, 1, 0);
            present(acc, 0.0F, 0.0F, 1.0F, 1.0F);
            draw(1);
            GlStateManager._blendFuncSeparate(0, 771, 0, 771);
            present(source, 0.0F, 0.0F, 1.0F, 0.85F);
            draw(1);
            blurred = out;
        } else {
            blurred = acc;
        }

        premultipliedBlend();
        blurPasses(blurred, Math.max(1, m.trailSoft.i() / 2), 3, 2.2F);
        target(screen, w, h, false);
        GlStateManager._blendFuncSeparate(1, 1, 1, 1);
        present(pong, 0.0F, 0.0F, m.trailBright.f() * 0.5F, 1.0F);
        draw(1);
    }

    private static void defaultBlend() {
        GlStateManager._blendFuncSeparate(770, 771, 1, 771);
    }

    private static void premultipliedBlend() {
        GlStateManager._blendFuncSeparate(1, 771, 1, 771);
    }

    private static void present(int tex, float sx, float sy, float rgb, float alpha) {
        GL20.glUseProgram(present);
        GL20.glUniform1i(loc(present, "Sampler0"), 0);
        GL20.glUniform2f(loc(present, "Shift"), sx, sy);
        GL20.glUniform4f(loc(present, "Mul"), rgb, rgb, rgb, alpha);
        bind(0, tex);
    }

    private static void blurPasses(int source, int repeat, int passes, float offset) {
        blur(blurH, ping, source, repeat, offset);
        blur(blurV, pong, ping, repeat, offset);

        for (int i = 1; i < passes; i++) {
            blur(blurH, ping, pong, repeat, offset);
            blur(blurV, pong, ping, repeat, offset);
        }
    }

    private static void blur(int program, int to, int from, int repeat, float offset) {
        target(to, smallW, smallH, true);
        GL20.glUseProgram(program);
        GL20.glUniform1i(loc(program, "Sampler0"), 0);
        GL20.glUniform2f(loc(program, "Resolution"), 1.0F / (float) smallW, 1.0F / (float) smallH);
        GL20.glUniform1f(loc(program, "Offset"), offset);
        bind(0, from);
        draw(repeat);
    }

    private static void draw(int times) {
        for (int i = 0; i < times; i++) {
            GL11.glDrawArrays(4, 0, 3);
        }
    }

    private static void target(int tex, int w, int h, boolean clear) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
        GlStateManager._viewport(0, 0, w, h);
        if (clear) {
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
    }

    private static void bind(int unit, int tex) {
        GlStateManager._activeTexture('蓀' + unit);
        GlStateManager._bindTexture(tex);
        GL33.glBindSampler(unit, 0);
        linear();
    }

    private static int loc(int p, String n) {
        return GL20.glGetUniformLocation(p, n);
    }

    private static int id(GpuTextureView v) {
        int n2;
        if (v != null && !v.isClosed()) {
            GpuTexture gpuTexture = v.texture();
            if (gpuTexture instanceof GlTexture) {
                GlTexture t = (GlTexture) gpuTexture;
                if (!t.isClosed()) {
                    return t.glId();
                }
            }
        }

        return 0;
    }

    private static int program(String frag) {
        int p = GL20.glCreateProgram();
        int v = shader(35633, "screen.vert");
        int f = shader(35632, frag);
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("hand glow link: " + GL20.glGetProgramInfoLog(p));
        } else {
            return p;
        }
    }

    private static int shader(int type, String name) {
        String src;
        try {
            try (InputStream in =
                    HandGlow.class.getResourceAsStream("/assets/arcanicvisuals/shaders/handglow/" + name)) {
                src = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
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

    private static record Held(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose) {}

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
        final int[] tex = new int[2];
        final int[] smp = new int[2];
        final int[] vp = new int[4];
        final int[] sc = new int[4];
        final float[] clear = new float[4];
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        Snapshot() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, this.vp);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, this.sc);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, this.clear);
            int was = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

            for (int u = 0; u < 2; u++) {
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

            for (int u = 0; u < 2; u++) {
                GlStateManager._activeTexture('蓀' + u);
                GlStateManager._bindTexture(this.tex[u]);
                GL33.glBindSampler(u, this.smp[u]);
            }

            GlStateManager._activeTexture(this.active);
            GlStateManager._viewport(this.vp[0], this.vp[1], this.vp[2], this.vp[3]);
            GlStateManager._scissorBox(this.sc[0], this.sc[1], this.sc[2], this.sc[3]);
            GL11.glClearColor(this.clear[0], this.clear[1], this.clear[2], this.clear[3]);
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
