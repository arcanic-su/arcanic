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
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import net.arcanic.visuals.core.Modules;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

public final class AmbienceWeather {
    private static final float CELL_X = 42.0F;
    private static final float CELL_Y = 30.0F;
    private static final float CELL_Z = 42.0F;
    private static final float FALL = 24.0F;
    private static final float SIZE = 0.034F;
    private static final float STRETCH = 17.0F;
    private static final float DRIFT = 0.05F;
    private static final float TINT_R = 0.69803923F;
    private static final float TINT_G = 0.76862746F;
    private static final float TINT_B = 0.8392157F;
    private static final int STREAKS = 4000;
    private static final int ROOF = 96;
    private static final float ROOF_STEP = 2.0F;
    private static final float ROOF_SPAN = 192.0F;
    private static final long SEED = 1592635409L;
    private static boolean broken;
    private static int wet;
    private static int rainScreen;
    private static int streak;
    private static int screenVao;
    private static int streakVao;
    private static int streakVbo;
    private static int streakCount = -1;
    private static int fbo;
    private static int readFbo;
    private static int temp;
    private static int tempW = -1;
    private static int tempH = -1;
    private static int roof;
    private static boolean roofReady;
    private static float roofX;
    private static float roofZ;
    private static float roofY;
    private static long roofTime;
    private static final ByteBuffer ROOF_DATA = BufferUtils.createByteBuffer(9216);
    private static float drops;
    private static long lastDrops;
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static int wave;
    private static boolean waveBroken;
    private static final float[] MATRIX = new float[16];

    private AmbienceWeather() {}

    public static void render(WorldRenderContext ctx) {
        Ambience a = Modules.AMBIENCE;
        if (!broken && a.enabled() && (a.wet.on() || a.rain.on())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                if (ThemeSky.viewProjection(VIEW_PROJ)) {
                    INVERSE.set(VIEW_PROJ).invert();

                    try {
                        RenderTarget main = mc.getMainRenderTarget();
                        int w = main.width;
                        int h = main.height;
                        int colour = id(main.getColorTextureView());
                        int depth = id(main.getDepthTextureView());
                        if (colour == 0 || depth == 0) {
                            return;
                        }

                        init(w, h);
                        Camera camera = mc.gameRenderer.getMainCamera();
                        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                        float time = (float) (System.currentTimeMillis() % 3600000L) / 1000.0F;
                        Snapshot st = new Snapshot();

                        try {
                            GlStateManager._disableCull();
                            GlStateManager._disableScissorTest();
                            if (a.wet.on()) {
                                wet(colour, depth, w, h, camera, pt, time, mc);
                            }

                            FogType medium = camera.getFluidInCamera();
                            boolean submerged =
                                    medium == FogType.WATER || medium == FogType.LAVA || medium == FogType.POWDER_SNOW;
                            if (a.rain.on() && !submerged) {
                                rain(colour, depth, w, h, camera, time, mc);
                            }
                        } finally {
                            st.restore();
                        }
                    } catch (Throwable t) {
                        broken = true;
                        System.err.println("[ArcanicVisuals] Ambience weather off: " + String.valueOf(t));
                    }
                }
            }
        }
    }

    public static void potionWaves(WorldRenderContext ctx) {
        if (!waveBroken && Modules.POTION_FX.enabled()) {
            PotionFx.Waves v = Modules.POTION_FX.waves();
            if (v != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null && ThemeSky.viewProjection(VIEW_PROJ)) {
                    INVERSE.set(VIEW_PROJ).invert();

                    try {
                        RenderTarget main = mc.getMainRenderTarget();
                        int w = main.width;
                        int h = main.height;
                        int colour = id(main.getColorTextureView());
                        int depth = id(main.getDepthTextureView());
                        if (colour == 0 || depth == 0) {
                            return;
                        }

                        init(w, h);
                        if (wave == 0) {
                            wave = program("screen.vert", "potion_wave.frag");
                        }

                        Vec3 cam = mc.gameRenderer.getMainCamera().position();
                        Snapshot st = new Snapshot();

                        try {
                            GlStateManager._disableCull();
                            GlStateManager._disableScissorTest();
                            copy(colour, w, h);
                            target(colour, 0, w, h);
                            GlStateManager._disableDepthTest();
                            GlStateManager._depthMask(false);
                            GlStateManager._disableBlend();
                            GL20.glUseProgram(wave);
                            matrix(wave, "InvViewProj", INVERSE);
                            GL20.glUniform3f(
                                    loc(wave, "Origin"),
                                    (float) (v.origin().x - cam.x),
                                    (float) (v.origin().y - cam.y),
                                    (float) (v.origin().z - cam.z));
                            GL20.glUniform4f(loc(wave, "Waves"), v.r1(), v.r2(), v.w1(), v.w2());
                            GL20.glUniform4f(loc(wave, "Look"), v.grey(), v.vivid(), v.sky(), v.rainbow());
                            GL20.glUniform4f(loc(wave, "Fronts"), v.light1(), v.light2(), v.echoes(), 0.0F);
                            int c1 = v.colour1();
                            int c2 = v.colour2();
                            GL20.glUniform3f(
                                    loc(wave, "Colour1"),
                                    (float) (c1 >> 16 & 255) / 255.0F,
                                    (float) (c1 >> 8 & 255) / 255.0F,
                                    (float) (c1 & 255) / 255.0F);
                            GL20.glUniform3f(
                                    loc(wave, "Colour2"),
                                    (float) (c2 >> 16 & 255) / 255.0F,
                                    (float) (c2 >> 8 & 255) / 255.0F,
                                    (float) (c2 & 255) / 255.0F);
                            GL20.glUniform1f(
                                    loc(wave, "Time"), (float) (System.currentTimeMillis() % 3600000L) / 1000.0F);
                            GL20.glUniform1i(loc(wave, "Sampler0"), 0);
                            GL20.glUniform1i(loc(wave, "Sampler1"), 1);
                            bind(0, temp, 9729);
                            bind(1, depth, 9728);
                            GL30.glBindVertexArray(screenVao);
                            GL11.glDrawArrays(4, 0, 3);
                        } finally {
                            st.restore();
                        }
                    } catch (Throwable t) {
                        waveBroken = true;
                        System.err.println("[ArcanicVisuals] potion waves off: " + String.valueOf(t));
                    }
                }
            }
        }
    }

    private static void wet(int colour, int depth, int w, int h, Camera camera, float pt, float time, Minecraft mc) {
        Ambience a = Modules.AMBIENCE;
        copy(colour, w, h);
        target(colour, 0, w, h);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableBlend();
        GL20.glUseProgram(wet);
        matrix(wet, "ViewProj", VIEW_PROJ);
        matrix(wet, "InvViewProj", INVERSE);
        Vec3 cam = camera.position();
        GL20.glUniform3f(loc(wet, "CamPos"), (float) cam.x, (float) cam.y, (float) cam.z);
        int sky = Ambience.drawnSky;
        GL20.glUniform3f(
                loc(wet, "SkyTint"),
                (float) (sky >> 16 & 255) / 255.0F,
                (float) (sky >> 8 & 255) / 255.0F,
                (float) (sky & 255) / 255.0F);
        float sun = (Float) camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, pt);
        GL20.glUniform3f(loc(wet, "SunDir"), (float) (-Math.sin((double) sun)), (float) Math.cos((double) sun), 0.0F);
        GL20.glUniform1f(loc(wet, "Reflectivity"), a.wetReflection.f() / 100.0F);
        GL20.glUniform1f(loc(wet, "Wetness"), a.wetAmount.f() / 100.0F);
        GL20.glUniform1f(loc(wet, "Ripple"), 0.35F);
        GL20.glUniform1f(loc(wet, "Gloss"), mc.level.dimensionType().hasSkyLight() ? 0.45F : 0.0F);
        GL20.glUniform1f(
                loc(wet, "Steps"), a.wetQuality.is("Низкое") ? 14.0F : (a.wetQuality.is("Высокое") ? 40.0F : 24.0F));
        GL20.glUniform1f(loc(wet, "HitThickness"), 0.98F);
        GL20.glUniform1f(loc(wet, "MaxDistance"), 24.0F);
        GL20.glUniform1f(loc(wet, "UpOnly"), 1.0F);
        GL20.glUniform1f(loc(wet, "Time"), time);
        GL20.glUniform1i(loc(wet, "Sampler0"), 0);
        GL20.glUniform1i(loc(wet, "Sampler1"), 1);
        bind(0, temp, 9729);
        bind(1, depth, 9728);
        GL30.glBindVertexArray(screenVao);
        GL11.glDrawArrays(4, 0, 3);
    }

    private static void rain(int colour, int depth, int w, int h, Camera camera, float time, Minecraft mc) {
        Ambience a = Modules.AMBIENCE;
        Vec3 cam = camera.position();
        updateRoof(mc, cam);
        long now = System.currentTimeMillis();
        float dt = lastDrops == 0L ? 0.0F : (float) (now - lastDrops) / 350.0F;
        lastDrops = now;
        boolean open = mc.level.canSeeSky(BlockPos.containing(mc.player.getEyePosition()));
        drops = open ? Math.min(1.0F, drops + dt) : Math.max(0.0F, drops - dt);
        int count = Math.max(1, Math.round(4000.0F * a.rainDensity.f() / 100.0F));
        buildStreaks(count);
        target(colour, depth, w, h);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(515);
        GlStateManager._depthMask(false);
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 771);
        GL20.glUseProgram(streak);
        matrix(streak, "ViewProj", VIEW_PROJ);
        GL20.glUniform3f(loc(streak, "CamPos"), (float) cam.x, (float) cam.y, (float) cam.z);
        Vector3f right = camera.rotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f up = camera.rotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        GL20.glUniform3f(loc(streak, "CamRight"), right.x, right.y, right.z);
        GL20.glUniform3f(loc(streak, "CamUp"), up.x, up.y, up.z);
        GL20.glUniform3f(loc(streak, "Motion"), 0.0F, -24.0F, 0.0F);
        GL20.glUniform3f(loc(streak, "Cell"), 42.0F, 30.0F, 42.0F);
        GL20.glUniform4f(loc(streak, "Tint"), 0.69803923F, 0.76862746F, 0.8392157F, a.rainOpacity.f() / 100.0F);
        GL20.glUniform1f(loc(streak, "Time"), time);
        GL20.glUniform1f(loc(streak, "Size"), 0.034F);
        GL20.glUniform1f(loc(streak, "Drift"), 0.05F);
        GL20.glUniform1f(loc(streak, "Flicker"), 0.0F);
        GL20.glUniform1f(loc(streak, "Stretch"), 17.0F);
        roofUniforms(streak, "Sampler0", 0);
        GL30.glBindVertexArray(streakVao);
        GL11.glDrawArrays(4, 0, count * 6);
        float glass = a.rainDrops.f() / 100.0F * drops;
        float splashes = a.rainSplashes.f() / 100.0F;
        if (!(glass <= 0.01F) || !(splashes <= 0.01F)) {
            copy(colour, w, h);
            target(colour, 0, w, h);
            GlStateManager._disableDepthTest();
            GlStateManager._disableBlend();
            GL20.glUseProgram(rainScreen);
            matrix(rainScreen, "InvViewProj", INVERSE);
            GL20.glUniform3f(loc(rainScreen, "CamPos"), (float) cam.x, (float) cam.y, (float) cam.z);
            GL20.glUniform3f(loc(rainScreen, "Tint"), 0.69803923F, 0.76862746F, 0.8392157F);
            GL20.glUniform1f(loc(rainScreen, "Time"), time);
            GL20.glUniform1f(loc(rainScreen, "Drops"), glass);
            GL20.glUniform1f(loc(rainScreen, "Splashes"), splashes);
            GL20.glUniform1f(loc(rainScreen, "Aspect"), (float) w / (float) h);
            GL20.glUniform1i(loc(rainScreen, "Sampler0"), 0);
            GL20.glUniform1i(loc(rainScreen, "Sampler1"), 1);
            bind(0, temp, 9729);
            bind(1, depth, 9728);
            roofUniforms(rainScreen, "Sampler2", 2);
            GL30.glBindVertexArray(screenVao);
            GL11.glDrawArrays(4, 0, 3);
        }
    }

    private static void roofUniforms(int program, String sampler, int unit) {
        GL20.glUniform1i(loc(program, sampler), unit);
        bind(unit, roof, 9728);
        GL20.glUniform4f(loc(program, "Roof"), roofX, roofZ, roofY, roofReady ? 1.0F : 0.0F);
        GL20.glUniform1f(loc(program, "RoofSpan"), 192.0F);
    }

    private static void updateRoof(Minecraft mc, Vec3 cam) {
        long now = System.currentTimeMillis();
        float x = (float) cam.x - 96.0F;
        float z = (float) cam.z - 96.0F;
        boolean moved = Math.abs(x - roofX) > 8.0F || Math.abs(z - roofZ) > 8.0F;
        if (!roofReady || moved || now - roofTime >= 500L) {
            roofTime = now;
            roofX = x;
            roofZ = z;
            roofY = (float) cam.y;
            ROOF_DATA.clear();

            for (int i = 0; i < 96; ++i) {
                int bz = (int) Math.floor((double) (roofZ + (float) i * 2.0F));

                for (int j = 0; j < 96; ++j) {
                    int bx = (int) Math.floor((double) (roofX + (float) j * 2.0F));
                    int top = mc.level.getHeight(Types.MOTION_BLOCKING, bx, bz);
                    ROOF_DATA.put((byte) Math.max(0, Math.min(255, Math.round((float) top - roofY) + 128)));
                }
            }

            ROOF_DATA.flip();
            GlStateManager._activeTexture(33984);
            GlStateManager._bindTexture(roof);
            int pbo = cleanUnpack();
            GL11.glTexSubImage2D(3553, 0, 0, 0, 96, 96, 6403, 5121, ROOF_DATA);
            restoreUnpack(pbo);
            roofReady = true;
        }
    }

    private static void buildStreaks(int count) {
        if (count != streakCount) {
            streakCount = count;
            Random random = new Random(1592635409L);
            ByteBuffer data = BufferUtils.createByteBuffer(count * 6 * 24);
            float[][] corner =
                    new float[][] {{0.0F, 0.0F}, {0.0F, 1.0F}, {1.0F, 1.0F}, {0.0F, 0.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}};

            for (int i = 0; i < count; ++i) {
                float x = random.nextFloat() * 42.0F;
                float y = random.nextFloat() * 30.0F;
                float z = random.nextFloat() * 42.0F;
                int r = (int) (random.nextFloat() * 255.0F);
                int g = (int) (random.nextFloat() * 255.0F);
                int b = (int) (random.nextFloat() * 255.0F);
                int alpha = 150 + (int) (random.nextFloat() * 105.0F);

                for (float[] c : corner) {
                    data.putFloat(x).putFloat(y).putFloat(z).putFloat(c[0]).putFloat(c[1]);
                    data.put((byte) r).put((byte) g).put((byte) b).put((byte) alpha);
                }
            }

            data.flip();
            GL30.glBindVertexArray(streakVao);
            GL15.glBindBuffer(34962, streakVbo);
            GL15.glBufferData(34962, data, 35044);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, 5126, false, 24, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, 5126, false, 24, 12L);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 4, 5121, true, 24, 20L);
            GL30.glBindVertexArray(0);
        }
    }

    static int cleanUnpack() {
        int pbo = GL11.glGetInteger(35055);
        if (pbo != 0) {
            GL15.glBindBuffer(35052, 0);
        }

        GL11.glPixelStorei(3314, 0);
        GL11.glPixelStorei(3315, 0);
        GL11.glPixelStorei(3316, 0);
        GL11.glPixelStorei(3317, 1);
        return pbo;
    }

    static void restoreUnpack(int pbo) {
        GL11.glPixelStorei(3317, 4);
        if (pbo != 0) {
            GL15.glBindBuffer(35052, pbo);
        }
    }

    private static void init(int w, int h) {
        int pbo = cleanUnpack();

        try {
            allocate(w, h);
        } finally {
            restoreUnpack(pbo);
        }
    }

    private static void allocate(int w, int h) {
        if (wet == 0) {
            wet = program("screen.vert", "wet.frag");
            rainScreen = program("screen.vert", "rain_screen.frag");
            streak = program("streak.vert", "streak.frag");
            screenVao = GL30.glGenVertexArrays();
            streakVao = GL30.glGenVertexArrays();
            streakVbo = GL15.glGenBuffers();
            fbo = GL30.glGenFramebuffers();
            readFbo = GL30.glGenFramebuffers();
            roof = GL11.glGenTextures();
            GlStateManager._activeTexture(33984);
            GlStateManager._bindTexture(roof);
            GL11.glTexImage2D(3553, 0, 33321, 96, 96, 0, 6403, 5121, 0L);
            params(9728);
        }

        if (w != tempW || h != tempH) {
            if (temp != 0) {
                GL11.glDeleteTextures(temp);
            }

            temp = GL11.glGenTextures();
            GlStateManager._activeTexture(33984);
            GlStateManager._bindTexture(temp);
            GL11.glTexImage2D(3553, 0, 32856, w, h, 0, 6408, 5121, 0L);
            params(9729);
            tempW = w;
            tempH = h;
        }
    }

    private static void copy(int colour, int w, int h) {
        GL30.glBindFramebuffer(36008, readFbo);
        GL30.glFramebufferTexture2D(36008, 36064, 3553, colour, 0);
        GL30.glBindFramebuffer(36009, fbo);
        GL30.glFramebufferTexture2D(36009, 36064, 3553, temp, 0);
        GL30.glFramebufferTexture2D(36009, 36096, 3553, 0, 0);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, 16384, 9728);
    }

    private static void target(int colour, int depth, int w, int h) {
        GL30.glBindFramebuffer(36160, fbo);
        GL30.glFramebufferTexture2D(36160, 36064, 3553, colour, 0);
        GL30.glFramebufferTexture2D(36160, 36096, 3553, depth, 0);
        GlStateManager._viewport(0, 0, w, h);
    }

    private static void bind(int unit, int tex, int filter) {
        GlStateManager._activeTexture('蓀' + unit);
        GlStateManager._bindTexture(tex);
        GL33.glBindSampler(unit, 0);
        params(filter);
    }

    private static void params(int filter) {
        GL11.glTexParameteri(3553, 10241, filter);
        GL11.glTexParameteri(3553, 10240, filter);
        GL11.glTexParameteri(3553, 10242, 33071);
        GL11.glTexParameteri(3553, 10243, 33071);
        GL11.glTexParameteri(3553, 33085, 0);
        GL11.glTexParameteri(3553, 34892, 0);
    }

    private static void matrix(int program, String name, Matrix4f m) {
        m.get(MATRIX);
        GL20.glUniformMatrix4fv(loc(program, name), false, MATRIX);
    }

    private static int loc(int p, String n) {
        return GL20.glGetUniformLocation(p, n);
    }

    private static int id(GpuTextureView v) {
        int var10000;
        if (v != null && !v.isClosed()) {
            GpuTexture var2 = v.texture();
            if (var2 instanceof GlTexture) {
                GlTexture t = (GlTexture) var2;
                if (!t.isClosed()) {
                    var10000 = t.glId();
                    return var10000;
                }
            }
        }

        var10000 = 0;
        return var10000;
    }

    private static int program(String vert, String frag) {
        int p = GL20.glCreateProgram();
        int v = shader(35633, vert);
        int f = shader(35632, frag);
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glBindAttribLocation(p, 0, "Position");
        GL20.glBindAttribLocation(p, 1, "UV0");
        GL20.glBindAttribLocation(p, 2, "Color");
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        if (GL20.glGetProgrami(p, 35714) == 0) {
            throw new IllegalStateException("weather link " + frag + ": " + GL20.glGetProgramInfoLog(p));
        } else {
            return p;
        }
    }

    private static int shader(int type, String name) {
        String src;
        try {
            InputStream in =
                    AmbienceWeather.class.getResourceAsStream("/assets/arcanicvisuals/shaders/weather/" + name);

            try {
                src = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Throwable var7) {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable var6) {
                        var7.addSuppressed(var6);
                    }
                }

                throw var7;
            }

            if (in != null) {
                in.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException(name, e);
        }

        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, 35713) == 0) {
            throw new IllegalStateException(name + ": " + GL20.glGetShaderInfoLog(s));
        } else {
            return s;
        }
    }

    private static final class Snapshot {
        final int drawFb = GL11.glGetInteger(36006);
        final int readFb = GL11.glGetInteger(36010);
        final int vaoB = GL11.glGetInteger(34229);
        final int ab = GL11.glGetInteger(34964);
        final int prog = GL11.glGetInteger(35725);
        final int active = GL11.glGetInteger(34016);
        final int srcRgb = GL11.glGetInteger(32969);
        final int dstRgb = GL11.glGetInteger(32968);
        final int srcA = GL11.glGetInteger(32971);
        final int dstA = GL11.glGetInteger(32970);
        final int depthFunc = GL11.glGetInteger(2932);
        final int unpack = GL11.glGetInteger(3317);
        final int[] tex = new int[3];
        final int[] smp = new int[3];
        final int[] vp = new int[4];
        final int[] sc = new int[4];
        final boolean depth = GL11.glIsEnabled(2929);
        final boolean cull = GL11.glIsEnabled(2884);
        final boolean scissor = GL11.glIsEnabled(3089);
        final boolean blend = GL11.glIsEnabled(3042);
        final boolean mask = GL11.glGetBoolean(2930);

        Snapshot() {
            GL11.glGetIntegerv(2978, this.vp);
            GL11.glGetIntegerv(3088, this.sc);
            int was = GL11.glGetInteger(34016);

            for (int u = 0; u < 3; ++u) {
                GL33.glActiveTexture('蓀' + u);
                this.tex[u] = GL11.glGetInteger(32873);
                this.smp[u] = GL11.glGetInteger(35097);
            }

            GL33.glActiveTexture(was);
        }

        void restore() {
            GL30.glBindFramebuffer(36008, this.readFb);
            GL30.glBindFramebuffer(36009, this.drawFb);
            GL30.glBindVertexArray(this.vaoB);
            GL15.glBindBuffer(34962, this.ab);
            GL20.glUseProgram(this.prog);

            for (int u = 0; u < 3; ++u) {
                GlStateManager._activeTexture('蓀' + u);
                GlStateManager._bindTexture(this.tex[u]);
                GL33.glBindSampler(u, this.smp[u]);
            }

            GlStateManager._activeTexture(this.active);
            GlStateManager._viewport(this.vp[0], this.vp[1], this.vp[2], this.vp[3]);
            GlStateManager._scissorBox(this.sc[0], this.sc[1], this.sc[2], this.sc[3]);
            GL11.glPixelStorei(3317, this.unpack);
            if (this.depth) {
                GlStateManager._enableDepthTest();
            } else {
                GlStateManager._disableDepthTest();
            }

            GlStateManager._depthFunc(this.depthFunc);
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
