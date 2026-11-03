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

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.fx.impact.ImpactDraw;
import net.arcanic.visuals.fx.impact.ScreenFx;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class PotionFx extends Module {
    final Setting.Mode palette = this.add(new Setting.Mode("palette", "Цвета", "Зелье", "Зелье", "Радуга", "Тема"));
    final Setting.Bool drink = this.add(new Setting.Bool("drink", "Когда пьёшь", true));
    final Setting.Bool thrown = this.add(new Setting.Bool("throw", "Когда кидаешь", true));
    final Setting.Num size = this.add(new Setting.Num("size", "Размер", 1.0, 0.6, 1.6, 0.05, "×"));
    final Setting.Bool screen = this.add(new Setting.Bool("screen", "Волны по миру", true));
    final Setting.Bool shake = this.add(new Setting.Bool("shake", "Тряска камеры", true));
    final Setting.Bool sound = this.add(new Setting.Bool("sound", "Звук", true));
    final Setting.Num volume =
            (this.add(new Setting.Num("volume", "Громкость", 0.6, 0.1, 1.0, 0.05, ""))).showIf(() -> this.sound.on());
    private static final Identifier RING = Identifier.fromNamespaceAndPath("arcanic", "textures/totem/ring.png");
    private static final Identifier ORB = Identifier.fromNamespaceAndPath("arcanic", "textures/targetesp/orb.png");
    private static final float GATHER = 0.25F;
    private static final float RINGS_FRONT = 0.86F;
    private static final float GREY_RUN = 1.25F;
    private static final float COLOUR_AFTER = 0.6F;
    private static final float COLOUR_RUN = 1.45F;
    private static final float LIFE = 2.65F;
    private static final int RAYS = 22;
    private final List<Burst> bursts = new ArrayList<>();
    private boolean wasDrinking;
    private int lastRemaining;
    private ItemStack lastStack;

    public PotionFx() {
        super("potion_fx", "Potion Drink", "Эффект, когда пьёшь или кидаешь зелье", Category.COMBAT, false);
        this.lastStack = ItemStack.EMPTY;
    }

    protected void onDisable() {
        this.bursts.clear();
    }

    public void tick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            boolean drinking = p.isUsingItem() && p.getUseItem().is(Items.POTION);
            if (this.wasDrinking && !drinking && this.lastRemaining <= 3 && this.drink.on()) {
                this.start(this.lastStack, 1.0F);
            }

            if (drinking) {
                this.lastRemaining = p.getUseItemRemainingTicks();
                this.lastStack = p.getUseItem().copy();
            }

            this.wasDrinking = drinking;
        }
    }

    public void used(ItemStack stack) {
        if (this.enabled() && this.thrown.on() && (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))) {
            this.start(stack, 0.75F);
        }
    }

    private void start(ItemStack stack, float strength) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            this.bursts.add(new Burst(
                    this.colours(stack),
                    this.size.f(),
                    strength,
                    p.position().add(0.0, (double) p.getBbHeight() * 0.55, 0.0)));
        }
    }

    public Waves waves() {
        if (this.screen.on() && !this.bursts.isEmpty()) {
            Burst b = (Burst) this.bursts.get(this.bursts.size() - 1);
            float t = (float) (System.currentTimeMillis() - b.start) / 1000.0F - 0.25F;
            if (t <= 0.0F) {
                return null;
            } else {
                Minecraft mc = Minecraft.getInstance();
                float far = Math.min(256.0F, Math.max(96.0F, (float) mc.options.getEffectiveRenderDistance() * 16.0F));
                float u1 = t / 1.25F;
                float u2 = (t - 0.6F) / 1.45F;
                float r1 = far * easeOutQuart(u1);
                float r2 = u2 <= 0.0F ? 0.0F : far * easeOutQuart(u2);
                float k = b.strength * (1.0F - smooth((t - 2.0500002F) / 0.3F));
                float fade1 = 1.0F - smooth((u1 - 0.65F) / 0.35F);
                float fade2 = 1.0F - smooth((u2 - 0.7F) / 0.3F);
                int c1 = Fx.lerp(b.colours[0], 0xFFE8F4FF, 0.7F);
                int c2 = Fx.lerp(b.colours[b.colours.length / 2], -1, 0.2F);
                return new Waves(
                        b.origin,
                        r1,
                        r2,
                        2.5F + r1 * 0.05F,
                        3.5F + r2 * 0.06F,
                        0.92F * k,
                        u2 > 0.0F ? 0.95F * k * fade2 : 0.0F,
                        far * 0.85F,
                        this.palette.is("Тема") ? 0.35F : 0.75F,
                        0.55F * k * fade1,
                        u2 > 0.0F ? 0.9F * k * fade2 : 0.0F,
                        u2 > 0.0F ? 0.4F * k * fade2 : 0.0F,
                        c1,
                        c2);
            }
        } else {
            return null;
        }
    }

    private int[] colours(ItemStack stack) {
        if (this.palette.is("Радуга")) {
            int[] c = new int[8];

            for (int i = 0; i < c.length; i++) {
                c[i] = 0xFF000000 | Mth.hsvToRgb((float) i / (float) c.length, 0.62F, 1.0F);
            }

            return c;
        } else if (this.palette.is("Тема")) {
            return new int[] {
                Theme.accent() | 0xFF000000,
                Theme.accent2() | 0xFF000000,
                Fx.lerp(Theme.accent() | 0xFF000000, -1, 0.4F)
            };
        } else {
            PotionContents contents = (PotionContents) stack.get(DataComponents.POTION_CONTENTS);
            int base = contents == null ? 0xFF7FB2FF : contents.getColor() | 0xFF000000;
            float[] hsv = hsv(base);
            int[] c = new int[6];

            for (int i = 0; i < c.length; i++) {
                float h = hsv[0] + ((float) i - 2.5F) * 0.035F;
                c[i] = 0xFF000000
                        | Mth.hsvToRgb(
                                h - (float) Math.floor((double) h), Math.min(1.0F, Math.max(0.45F, hsv[1])), 1.0F);
            }

            return c;
        }
    }

    private static float[] hsv(int c) {
        float r = (float) (c >> 16 & 255) / 255.0F;
        float g = (float) (c >> 8 & 255) / 255.0F;
        float b = (float) (c & 255) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0.0F;
        if (d > 1.0E-5F) {
            if (max == r) {
                h = (g - b) / d / 6.0F;
            } else if (max == g) {
                h = ((b - r) / d + 2.0F) / 6.0F;
            } else {
                h = ((r - g) / d + 4.0F) / 6.0F;
            }
        }

        if (h < 0.0F) {
            ++h;
        }

        return new float[] {h, max <= 0.0F ? 0.0F : d / max, max};
    }

    public void world(PoseStack ps, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.bursts.isEmpty() && mc.level != null && mc.player != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            Matrix4f pose = ps.last().pose();
            Vec3 cam = camera.position();
            Vector3f left = new Vector3f(camera.leftVector());
            Vector3f up = new Vector3f(camera.upVector());
            boolean firstPerson = mc.options.getCameraType().isFirstPerson();
            LocalPlayer me = mc.player;
            Vec3 feet = me.getPosition(partial);
            Vec3 centre = firstPerson
                    ? me.getEyePosition(partial)
                            .add(me.getViewVector(partial).scale(0.7))
                            .add(0.0, -0.35, 0.0)
                    : feet.add(0.0, (double) me.getBbHeight() * 0.55, 0.0);
            long now = System.currentTimeMillis();
            Iterator<Burst> it = this.bursts.iterator();

            while (it.hasNext()) {
                Burst b = (Burst) it.next();
                float t = (float) (now - b.start) / 1000.0F;
                if (t > 2.65F) {
                    it.remove();
                } else {
                    float dt = Math.max(0.0F, Math.min(0.1F, t - b.last));
                    b.last = t;
                    this.burst(b, t, dt, pose, cam, left, up, centre, feet, firstPerson, mc);
                }
            }
        }
    }

    private void burst(
            Burst b,
            float t,
            float dt,
            Matrix4f pose,
            Vec3 cam,
            Vector3f left,
            Vector3f up,
            Vec3 centre,
            Vec3 feet,
            boolean firstPerson,
            Minecraft mc) {
        float s = b.scale;
        float k = b.strength;
        float x = (float) (centre.x - cam.x);
        float y = (float) (centre.y - cam.y);
        float z = (float) (centre.z - cam.z);
        int main = b.colours[0];
        int light = Fx.lerp(main, -1, 0.55F);
        if (this.screen.on()) {
            ScreenFx.centre(0.5F, 0.5F);
            if (!firstPerson) {
                Vector4f q = new Vector4f(x, y, z, 1.0F);
                pose.transform(q);
                ImpactDraw.PROJECTION.transform(q);
                ScreenFx.project(q);
            }
        }

        if (t < 0.25F) {
            if (b.once(0) && this.shake.on()) {
                ImpactDraw.shake(0.9F * k);
            }

            emit(b, dt, 320.0F, () -> {
                Vector3f d = dir(b);
                float r = (1.6F + b.rnd.nextFloat() * 1.6F) * s;
                float life = 0.2F + b.rnd.nextFloat() * 0.06F;
                Fx.spawn()
                        .at(centre.add((double) (d.x * r), (double) (d.y * r), (double) (d.z * r)))
                        .vel((double) (-d.x * r / life), (double) (-d.y * r / life), (double) (-d.z * r / life))
                        .tex(Fx.Tex.SPARK)
                        .mode(Fx.Mode.STRETCH)
                        .size(0.035F * s, 0.012F * s)
                        .life(life)
                        .drag(0.0F)
                        .fadeIn(0.05F)
                        .colour(light, b.colours[b.rnd.nextInt(b.colours.length)]);
            });
            if (this.screen.on()) {
                float g = smooth(t / 0.25F) * k;
                ScreenFx.grade(0.25F * g, 0.45F * g, 0.0F, 0.05F * g);
            }

        } else {
            float te = t - 0.25F;
            if (b.once(1)) {
                if (this.shake.on()) {
                    ImpactDraw.shake(2.2F * k);
                }

                if (this.sound.on()) {
                    float v = this.volume.f() * k;
                    mc.level.playLocalSound(
                            centre.x,
                            centre.y,
                            centre.z,
                            SoundEvents.AMETHYST_BLOCK_CHIME,
                            SoundSource.PLAYERS,
                            v,
                            1.35F,
                            false);
                    mc.level.playLocalSound(
                            centre.x,
                            centre.y,
                            centre.z,
                            SoundEvents.BEACON_POWER_SELECT,
                            SoundSource.PLAYERS,
                            v * 0.45F,
                            1.7F,
                            false);
                    mc.level.playLocalSound(
                            centre.x,
                            centre.y,
                            centre.z,
                            SoundEvents.AMETHYST_BLOCK_RESONATE,
                            SoundSource.PLAYERS,
                            v * 0.7F,
                            1.2F,
                            false);
                }

                for (int i = 0; i < 140; i++) {
                    Vector3f d = dir(b);
                    float sp = (8.0F + b.rnd.nextFloat() * 10.0F) * s;
                    int c = b.colours[b.rnd.nextInt(b.colours.length)];
                    Fx.spawn()
                            .at(centre)
                            .vel((double) (d.x * sp), (double) (d.y * sp), (double) (d.z * sp))
                            .tex(Fx.Tex.SPARK)
                            .mode(Fx.Mode.STRETCH)
                            .size(0.06F * s, 0.0F)
                            .life(0.5F + b.rnd.nextFloat() * 0.45F)
                            .drag(3.4F)
                            .colour(Fx.lerp(c, -1, 0.4F), c & 0xFFFFFF);
                }

                for (int i = 0; i < 60; i++) {
                    Vector3f d = dir(b);
                    float sp = (1.6F + b.rnd.nextFloat() * 3.4F) * s;
                    int c = b.colours[b.rnd.nextInt(b.colours.length)];
                    Fx.spawn()
                            .at(centre)
                            .vel((double) (d.x * sp), (double) (d.y * sp + 0.4F), (double) (d.z * sp))
                            .tex(i % 3 == 0 ? Fx.Tex.STAR : (i % 3 == 1 ? Fx.Tex.SPARKLE : Fx.Tex.GLOW))
                            .size(0.12F * s, 0.0F)
                            .spin(b.rnd.nextFloat() * 6.0F, (b.rnd.nextFloat() - 0.5F) * 5.0F)
                            .life(1.1F + b.rnd.nextFloat() * 0.8F)
                            .drag(1.9F)
                            .twinkle(0.55F)
                            .colour(c, c & 0xFFFFFF);
                }
            }

            if (te >= 0.6F && b.once(2)) {
                if (this.shake.on()) {
                    ImpactDraw.shake(0.8F * k);
                }

                if (this.sound.on()) {
                    mc.level.playLocalSound(
                            centre.x,
                            centre.y,
                            centre.z,
                            SoundEvents.AMETHYST_BLOCK_RESONATE,
                            SoundSource.PLAYERS,
                            this.volume.f() * k * 0.8F,
                            1.55F,
                            false);
                }
            }

            if (te < 0.4F) {
                sprite(
                        pose,
                        left,
                        up,
                        ORB,
                        x,
                        y,
                        z,
                        (0.8F + te * 11.0F) * s,
                        0.0F,
                        light,
                        0.0F,
                        (1.0F - te / 0.4F) * k);
            }

            if (te < 1.1F) {
                sprite(pose, left, up, ORB, x, y, z, 0.6F * s, 0.0F, main, 0.0F, 0.75F * (1.0F - te / 1.1F) * k);
            }

            if (te < 1.25F) {
                for (Ray r : b.rays) {
                    float u = te - r.delay;
                    if (!(u <= 0.0F)) {
                        float head = r.length * easeOut(u / 0.6F);
                        float tail = r.length * easeOut((u - 0.12F) / 0.75F);
                        float a = (1.0F - smooth((u - 0.45F) / 0.7F)) * k;
                        if (!(a <= 0.01F) && !(head - tail < 0.05F)) {
                            int n = 9;
                            float[] xs = new float[n];
                            float[] ys = new float[n];
                            float[] zs = new float[n];

                            for (int i = 0; i < n; i++) {
                                float d = tail + (head - tail) * (float) i / (float) (n - 1);
                                xs[i] = x + r.dir.x * d;
                                ys[i] = y + r.dir.y * d;
                                zs[i] = z + r.dir.z * d;
                            }

                            float thin = 1.0F - 0.55F * smooth(u / 0.8F);
                            ribbon(pose, xs, ys, zs, 0.42F * r.width * thin, r.colour, 0.55F * a);
                            ribbon(pose, xs, ys, zs, 0.09F * r.width * thin, Fx.lerp(r.colour, -1, 0.6F), a);
                        }
                    }
                }
            }

            if (te < 1.1F) {
                float u = te / 1.1F;
                float kk = (1.0F - u) * (1.0F - u) * k;
                Matrix4f m = (new Matrix4f(pose))
                        .translate(
                                (float) (feet.x - cam.x), (float) (feet.y - cam.y) + 0.04F, (float) (feet.z - cam.z));
                flat(m, RING, 12.0F * s * easeOut(u) / 0.86F, new int[] {light, main, light, main}, 0.3F * kk, kk);
                if (u < 0.8F) {
                    flat(
                            m,
                            RING,
                            6.0F * s * easeOut(u / 0.8F) / 0.86F,
                            new int[] {main, light, main, light},
                            0.0F,
                            0.6F * kk);
                }
            }

            if (te < 1.8F) {
                float rate = 90.0F * (1.0F - te / 1.8F);
                emit(b, dt, rate, () -> {
                    float ang = te * 9.0F + b.rnd.nextFloat() * 0.6F;
                    float rr = (0.55F + b.rnd.nextFloat() * 0.25F) * s;
                    Vec3 at = feet.add(
                            Math.cos((double) ang) * (double) rr,
                            0.1 + (double) te * 1.4 * (double) s + (double) b.rnd.nextFloat() * 0.2,
                            Math.sin((double) ang) * (double) rr);
                    int c = b.colours[b.rnd.nextInt(b.colours.length)];
                    Fx.spawn()
                            .at(at)
                            .vel(
                                    -Math.sin((double) ang) * 1.6 * (double) s,
                                    (1.1 + (double) b.rnd.nextFloat() * 0.6) * (double) s,
                                    Math.cos((double) ang) * 1.6 * (double) s)
                            .tex(b.rnd.nextBoolean() ? Fx.Tex.SPARKLE : Fx.Tex.GLOW)
                            .size(0.07F * s, 0.0F)
                            .spin(b.rnd.nextFloat() * 6.0F, 3.0F)
                            .life(0.7F + b.rnd.nextFloat() * 0.4F)
                            .drag(2.2F)
                            .twinkle(0.5F)
                            .colour(Fx.lerp(c, -1, 0.3F), c & 0xFFFFFF);
                });
            }

            if (this.screen.on()) {
                ScreenFx.flash(0.25F * (1.0F - smooth(te / 0.3F)) * k, r(light), g(light), bl(light));
                if (te < 0.7F) {
                    float w = (1.0F - te / 0.7F) * k;
                    ScreenFx.wave(1.6F * easeOut(te / 0.7F), 0.07F, 0.03F * w, 0.7F * w);
                }

                float t2 = te - 0.6F;
                if (t2 > 0.0F && t2 < 0.7F) {
                    float w = (1.0F - t2 / 0.7F) * k;
                    ScreenFx.wave(1.6F * easeOut(t2 / 0.7F), 0.08F, 0.025F * w, 0.9F * w);
                }

                ScreenFx.grade(0.0F, 0.45F * (1.0F - smooth(te / 0.5F)) * k, 0.0F, 0.0F);
            }
        }
    }

    private static void emit(Burst b, float dt, float rate, Runnable one) {
        b.owed += dt * rate;

        while (b.owed >= 1.0F) {
            --b.owed;
            one.run();
        }
    }

    private static Vector3f dir(Burst b) {
        double u = b.rnd.nextDouble() * 2.0 - 1.0;
        double th = b.rnd.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(1.0 - u * u);
        return new Vector3f((float) (r * Math.cos(th)), (float) u, (float) (r * Math.sin(th)));
    }

    private static void twice(Matrix4f m, Identifier tex, float shape, float glow, Consumer<Emit> quads) {
        if (shape > 0.003F) {
            Emit e = new Emit(m, shape);
            quads.accept(e);
            ImpactDraw.draw(ImpactDraw.type(tex, Mode.QUADS, true, false), e.b);
        }

        if (glow > 0.003F) {
            Emit e = new Emit(m, glow);
            quads.accept(e);
            ImpactDraw.draw(ImpactDraw.type(tex, Mode.QUADS, true, true), e.b);
        }
    }

    private static void sprite(
            Matrix4f pose,
            Vector3f l,
            Vector3f u,
            Identifier tex,
            float x,
            float y,
            float z,
            float half,
            float rot,
            int c,
            float shape,
            float glow) {
        float cr = Mth.cos((double) rot) * half;
        float sr = Mth.sin((double) rot) * half;
        float[][] k =
                new float[][] {{cr - sr, sr + cr}, {cr + sr, sr - cr}, {-cr + sr, -sr - cr}, {-cr - sr, -sr + cr}};
        float[] tu = {0.0F, 0.0F, 1.0F, 1.0F};
        float[] tv = {0.0F, 1.0F, 1.0F, 0.0F};
        twice(pose, tex, shape, glow, (e) -> {
            for (int i = 0; i < 4; i++) {
                e.v(
                        x + l.x * k[i][0] + u.x * k[i][1],
                        y + l.y * k[i][0] + u.y * k[i][1],
                        z + l.z * k[i][0] + u.z * k[i][1],
                        tu[i],
                        tv[i],
                        c);
            }
        });
    }

    private static void flat(Matrix4f m, Identifier tex, float half, int[] c, float shape, float glow) {
        twice(m, tex, shape, glow, (e) -> {
            e.v(-half, 0.0F, -half, 0.0F, 0.0F, c[0]);
            e.v(-half, 0.0F, half, 0.0F, 1.0F, c[1]);
            e.v(half, 0.0F, half, 1.0F, 1.0F, c[2]);
            e.v(half, 0.0F, -half, 1.0F, 0.0F, c[3]);
        });
    }

    private static void ribbon(Matrix4f pose, float[] xs, float[] ys, float[] zs, float width, int c, float a) {
        int n = xs.length;
        float[] ox = new float[n];
        float[] oy = new float[n];
        float[] oz = new float[n];

        for (int i = 0; i < n; i++) {
            int p = Math.max(0, i - 1);
            int q = Math.min(n - 1, i + 1);
            float tx = xs[q] - xs[p];
            float ty = ys[q] - ys[p];
            float tz = zs[q] - zs[p];
            float ex = xs[i];
            float ey = ys[i];
            float ez = zs[i];
            float cx = ty * ez - tz * ey;
            float cy = tz * ex - tx * ez;
            float cz = tx * ey - ty * ex;
            float l = (float) Math.sqrt((double) (cx * cx + cy * cy + cz * cz));
            float w = width * 0.5F * taper(i, n) / Math.max(l, 1.0E-6F);
            ox[i] = cx * w;
            oy[i] = cy * w;
            oz[i] = cz * w;
        }

        twice(pose, Fx.Tex.LINE_GLOW.id, 0.0F, a, (e) -> {
            for (int i = 0; i + 1 < n; i++) {
                int c0 = alpha(c, taper(i, n));
                int c1 = alpha(c, taper(i + 1, n));
                e.v(xs[i] - ox[i], ys[i] - oy[i], zs[i] - oz[i], 0.0F, 0.0F, c0);
                e.v(xs[i] + ox[i], ys[i] + oy[i], zs[i] + oz[i], 0.0F, 1.0F, c0);
                e.v(xs[i + 1] + ox[i + 1], ys[i + 1] + oy[i + 1], zs[i + 1] + oz[i + 1], 1.0F, 1.0F, c1);
                e.v(xs[i + 1] - ox[i + 1], ys[i + 1] - oy[i + 1], zs[i + 1] - oz[i + 1], 1.0F, 0.0F, c1);
            }
        });
    }

    private static float taper(int i, int n) {
        float f = (float) i / (float) (n - 1);
        return smooth(f / 0.7F) * (1.0F - smooth((f - 0.85F) / 0.15F) * 0.6F);
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static float smooth(float t) {
        t = clamp(t);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOutQuart(float t) {
        t = 1.0F - clamp(t);
        return 1.0F - t * t * t * t;
    }

    private static float easeOut(float t) {
        t = 1.0F - clamp(t);
        return 1.0F - t * t * t;
    }

    private static int alpha(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * (float) (c >>> 24)) << 24 | c & 0xFFFFFF;
    }

    private static float r(int c) {
        return (float) (c >> 16 & 255) / 255.0F;
    }

    private static float g(int c) {
        return (float) (c >> 8 & 255) / 255.0F;
    }

    private static float bl(int c) {
        return (float) (c & 255) / 255.0F;
    }

    public static record Waves(
            Vec3 origin,
            float r1,
            float r2,
            float w1,
            float w2,
            float grey,
            float vivid,
            float sky,
            float rainbow,
            float light1,
            float light2,
            float echoes,
            int colour1,
            int colour2) {}

    private static record Ray(Vector3f dir, float length, int colour, float delay, float width) {}

    private static final class Burst {
        final long start = System.currentTimeMillis();
        final Random rnd = new Random();
        final int[] colours;
        final float scale;
        final float strength;
        final Vec3 origin;
        final List<Ray> rays = new ArrayList<>();
        float last;
        float owed;
        int done;

        Burst(int[] colours, float scale, float strength, Vec3 origin) {
            this.colours = colours;
            this.scale = scale;
            this.strength = strength;
            this.origin = origin;

            for (int i = 0; i < 22; i++) {
                double a = (double) i * Math.PI * 2.0 / 22.0 + this.rnd.nextDouble() * 0.3;
                double y = -0.25 + this.rnd.nextDouble() * 1.05;
                Vector3f d = (new Vector3f((float) Math.cos(a), (float) y, (float) Math.sin(a))).normalize();
                this.rays.add(new Ray(
                        d,
                        (7.5F + this.rnd.nextFloat() * 5.5F) * scale,
                        colours[i % colours.length],
                        this.rnd.nextFloat() * 0.1F,
                        (1.1F + this.rnd.nextFloat() * 0.6F) * scale));
            }
        }

        boolean once(int step) {
            if ((this.done & 1 << step) != 0) {
                return false;
            } else {
                this.done |= 1 << step;
                return true;
            }
        }
    }

    private static final class Emit {
        final BufferBuilder b;
        final Matrix4f m;
        final float k;

        Emit(Matrix4f m, float k) {
            this.b = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            this.m = m;
            this.k = k;
        }

        void v(float x, float y, float z, float u, float v, int c) {
            this.b.addVertex(this.m, x, y, z).setUv(u, v).setColor(PotionFx.alpha(c, this.k));
        }
    }
}
