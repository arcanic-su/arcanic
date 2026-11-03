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

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.impact.ImpactDraw;
import net.arcanic.visuals.fx.impact.ScreenFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class KillFx {
    static final int ASH = 0;
    static final int EXECUTION = 1;
    static final int BLACK_HOLE = 2;
    static final int GLITCH = 3;
    static final int SLICE = 4;
    static final int CONFETTI = 5;
    static final int METEOR = 6;
    static final int THUNDER = 7;
    private static final float[] LIFE = {3.2F, 2.3F, 3.1F, 2.4F, 2.6F, 3.4F, 3.3F, 3.0F};
    private static final float TAU = ((float) Math.PI * 2F);
    private static final List<Death> WORLD = new ArrayList<>();
    private static final List<Death> STUDIO = new ArrayList<>();
    private static ClientLevel level;
    private static final float HOLE_SHUT = 2.2F;
    private static final float HOLE_BURST = 2.42F;
    private static final float GLITCH_LINE = 1.0F;
    private static final float GLITCH_DOT = 1.18F;
    private static final float GLITCH_OFF = 1.4F;
    private static final float SHEATH = 0.7F;
    private static final int[] PARTY = {-45683, -11713, 0xFF3EE6FF, 0xFF7CFF6B, 0xFFB57BFF, -30147, -1};
    private static final float METEOR_HIT = 0.62F;
    private static final float[] STRIKES = {0.5F, 0.78F, 1.02F, 1.32F};

    private KillFx() {}

    static Show.Pal own(int style) {
        return switch (style) {
            case 1 -> new Show.Pal(-1, -11138, -38083);
            case 2 -> new Show.Pal(0xFFF3E8FF, 0xFFB47BFF, 0xFF4B1D9E);
            case 3 -> new Show.Pal(-1, 0xFF22F3FF, -54319);
            case 4 -> new Show.Pal(-1, -52392, 0xFF9E0F30);
            case 5 -> new Show.Pal(-3152, -41054, 0xFF5B8CFF);
            case 6 -> new Show.Pal(0xFFEAF6FF, 0xFF7CC4FF, 0xFF5A3BFF);
            case 7 -> new Show.Pal(0xFFF4F1FF, 0xFFA48BFF, 0xFF4A35FF);
            default -> new Show.Pal(-7264, -30166, 0xFFD2341E);
        };
    }

    private static long now(boolean studio) {
        return studio ? Studio.clockMs() : System.currentTimeMillis();
    }

    static void spawn(
            int style, Vec3 at, float width, float height, float yaw, float scale, Show.Pal pal, boolean studio) {
        Death d = new Death(Math.max(0, Math.min(LIFE.length - 1, style)), at, width, height, yaw, scale, pal, studio);
        switch (d.style) {
            case 0:
                ash(d);
            case 1:
            default:
                break;
            case 2:
                blackHole(d);
                break;
            case 3:
                glitch(d);
                break;
            case 4:
                slice(d);
                break;
            case 5:
                confetti(d);
        }

        List<Death> list = studio ? STUDIO : WORLD;
        list.add(d);

        while (list.size() > 4) {
            list.remove(0);
        }
    }

    static void clear() {
        WORLD.clear();
    }

    static void clearStudio() {
        STUDIO.clear();
    }

    static void world(PoseStack ps) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            level = mc.level;
            WORLD.clear();
        }

        if (!WORLD.isEmpty() && mc.level != null) {
            draw(WORLD, Show.view(ps));
        }
    }

    static void studio(Show.View v) {
        if (!STUDIO.isEmpty()) {
            draw(STUDIO, v);
        }
    }

    private static void draw(List<Death> deaths, Show.View v) {
        Iterator<Death> it = deaths.iterator();

        while (it.hasNext()) {
            Death d = (Death) it.next();
            float t = (float) (now(d.studio) - d.start) / 1000.0F;
            if (!(t > LIFE[d.style]) && !(t < -0.05F)) {
                t = Math.max(0.0F, t);
                float dt = Math.max(0.0F, Math.min(0.1F, t - d.last));
                d.last = t;
                Show.Pal c = d.pal;
                switch (d.style) {
                    case 1:
                        execution(d, t, v, c);
                        break;
                    case 2:
                        blackHole(d, t, v, c);
                        break;
                    case 3:
                        glitch(d, t, dt, v, c);
                        break;
                    case 4:
                        slice(d, t, v, c);
                        break;
                    case 5:
                        confetti(d, t, dt, v, c);
                        break;
                    case 6:
                        meteor(d, t, dt, v, c);
                        break;
                    case 7:
                        thunder(d, t, v, c);
                        break;
                    default:
                        ash(d, t, dt, v, c);
                }
            } else {
                it.remove();
            }
        }
    }

    private static void sound(Death d, SoundEvent s, float vol, float pitch) {
        if (d.sound) {
            if (d.studio) {
                ImpactDraw.studioSound(s, vol * d.volume, pitch);
            } else {
                Show.play(d.at, s, vol * d.volume, pitch);
            }
        }
    }

    private static void jolt(Death d, float strength) {
        if (d.shake) {
            ImpactDraw.shake(strength * (d.studio ? 1.0F : Show.reach(d.at, 30.0F)));
        }
    }

    private static float screenReach(Death d) {
        return d.screen ? (d.studio ? 1.0F : Show.reach(d.at, 30.0F)) : 0.0F;
    }

    private static int count(Death d, int n) {
        return Math.round((float) n * Mth.clamp(d.width * d.height / 1.08F, 0.4F, 2.0F));
    }

    private static double[] inBody(Death d) {
        float yn = d.rnd.nextFloat();
        float profile = yn > 0.78F ? 0.55F : (yn > 0.42F ? 1.0F : 0.72F);
        double a = d.rnd.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(d.rnd.nextDouble()) * (double) d.width * 0.5 * (double) profile;
        return new double[] {
            d.at.x + Math.cos(a) * r, d.at.y + (double) (yn * d.height), d.at.z + Math.sin(a) * r, (double) yn
        };
    }

    private static Vector3f randomAxis(Random r) {
        return (new Vector3f(r.nextFloat() - 0.5F, r.nextFloat() - 0.5F, r.nextFloat() - 0.5F)).normalize();
    }

    private static float hash(long seed, int i) {
        long h = seed * -7046029254386353131L + (long) i * -4417276706812531889L;
        h ^= h >>> 31;
        h *= -4658895280553007687L;
        h ^= h >>> 29;
        return (float) (h >>> 40) / 1.6777216E7F;
    }

    private static void dots(Show.View v, Identifier tex, List<Dot> list, float shape, float glow) {
        if (!list.isEmpty()) {
            Show.twice(v.pose(), tex, shape, glow, (e) -> {
                for (Dot p : list) {
                    Show.quad(e, v, p.x(), p.y(), p.z(), p.half(), 0.0F, p.colour());
                }
            });
        }
    }

    private static void rect(Show.Emit e, Show.View v, float x, float y, float z, float hw, float hh, int c) {
        Vector3f l = v.left();
        Vector3f u = v.up();
        e.v(x - l.x * hw - u.x * hh, y - l.y * hw - u.y * hh, z - l.z * hw - u.z * hh, 0.0F, 1.0F, c);
        e.v(x + l.x * hw - u.x * hh, y + l.y * hw - u.y * hh, z + l.z * hw - u.z * hh, 1.0F, 1.0F, c);
        e.v(x + l.x * hw + u.x * hh, y + l.y * hw + u.y * hh, z + l.z * hw + u.z * hh, 1.0F, 0.0F, c);
        e.v(x - l.x * hw + u.x * hh, y - l.y * hw + u.y * hh, z - l.z * hw + u.z * hh, 0.0F, 0.0F, c);
    }

    private static void plane(
            Show.View v,
            Identifier tex,
            float x,
            float y,
            float z,
            Vector3f e1,
            Vector3f e2,
            float half,
            int side,
            int c,
            float shape,
            float glow) {
        if (!(half <= 0.001F)) {
            float v0 = side > 0 ? 0.5F : 0.0F;
            float v1 = side < 0 ? 0.5F : 1.0F;
            float s0 = (v0 * 2.0F - 1.0F) * half;
            float s1 = (v1 * 2.0F - 1.0F) * half;
            Show.twice(v.pose(), tex, shape, glow, (e) -> {
                e.v(x - e1.x * half + e2.x * s0, y - e1.y * half + e2.y * s0, z - e1.z * half + e2.z * s0, 0.0F, v0, c);
                e.v(x + e1.x * half + e2.x * s0, y + e1.y * half + e2.y * s0, z + e1.z * half + e2.z * s0, 1.0F, v0, c);
                e.v(x + e1.x * half + e2.x * s1, y + e1.y * half + e2.y * s1, z + e1.z * half + e2.z * s1, 1.0F, v1, c);
                e.v(x - e1.x * half + e2.x * s1, y - e1.y * half + e2.y * s1, z - e1.z * half + e2.z * s1, 0.0F, v1, c);
            });
        }
    }

    private static int shade(int c, float k) {
        int r = Math.min(255, Math.round((float) (c >> 16 & 255) * k));
        int g = Math.min(255, Math.round((float) (c >> 8 & 255) * k));
        int b = Math.min(255, Math.round((float) (c & 255) * k));
        return c & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void emit(Death d, float dt, float rate, Runnable one) {
        d.owed += dt * rate;

        while (d.owed >= 1.0F) {
            --d.owed;
            one.run();
        }
    }

    private static void ash(Death d) {
        int n = Math.round(950.0F * Mth.clamp(d.width * d.height / 1.08F, 0.4F, 2.0F));

        for (int i = 0; i < n; i++) {
            float yn = d.rnd.nextFloat();
            float profile = yn > 0.78F ? 0.55F : (yn > 0.42F ? 1.0F : 0.72F);
            double a = d.rnd.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(d.rnd.nextDouble()) * (double) d.width * 0.5 * (double) profile;
            float size = (0.05F + d.rnd.nextFloat() * 0.065F) * d.scale;
            boolean ember = d.rnd.nextFloat() < 0.35F;
            Flake f = new Flake(
                    0.05F + (1.0F - yn) * 0.7F + d.rnd.nextFloat() * 0.08F,
                    size,
                    ember,
                    d.rnd.nextFloat() * 6.0F,
                    tri(d.rnd, size),
                    tri(d.rnd, size),
                    tri(d.rnd, size),
                    (new Vector3f(d.rnd.nextFloat() - 0.5F, d.rnd.nextFloat() - 0.5F, d.rnd.nextFloat() - 0.5F))
                            .normalize());
            f.x = d.at.x + Math.cos(a) * r;
            f.y = d.at.y + (double) (yn * d.height);
            f.z = d.at.z + Math.sin(a) * r;
            float gust = 0.6F + d.rnd.nextFloat() * 0.9F;
            f.vx = (double) (d.wind.x * gust) + Math.cos(a) * 0.25;
            f.vy = 0.35 + d.rnd.nextDouble() * 0.7;
            f.vz = (double) (d.wind.z * gust) + Math.sin(a) * 0.25;
            f.spin = 2.0F + d.rnd.nextFloat() * 6.0F;
            d.flakes.add(f);
        }
    }

    private static Vector3f tri(Random r, float size) {
        return (new Vector3f(r.nextFloat() - 0.5F, r.nextFloat() - 0.5F, r.nextFloat() - 0.5F)).mul(size * 2.0F);
    }

    private static void ash(Death d, float t, float dt, Show.View v, Show.Pal c) {
        if (d.once(0)) {
            sound(d, SoundEvents.FIRE_EXTINGUISH, 0.9F, 0.6F);
            sound(d, SoundEvents.SAND_BREAK, 1.0F, 0.6F);
        }

        float fade = 1.0F - Show.smooth((t - LIFE[0] + 0.9F) / 0.9F);
        List<Show.Tri> dark = new ArrayList<>();
        List<Show.Tri> hot = new ArrayList<>();
        Quaternionf q = new Quaternionf();
        List<float[]> embers = new ArrayList<>();

        for (Flake f : d.flakes) {
            float age = t - f.free;
            if (!(age < 0.0F)) {
                float drag = (float) Math.exp((double) (-0.5F * dt));
                f.vx = f.vx * (double) drag
                        + (double) (Mth.sin((double) (t * 2.3F + (float) f.y * 3.0F + f.phase)) * 0.9F * dt);
                f.vz = f.vz * (double) drag
                        + (double) (Mth.cos((double) (t * 1.9F + (float) f.x * 3.0F + f.phase)) * 0.9F * dt);
                f.vy = f.vy * (double) drag + (double) (0.3F * dt);
                f.x += f.vx * (double) dt;
                f.y += f.vy * (double) dt;
                f.z += f.vz * (double) dt;
                f.angle += f.spin * dt;
                float life = (1.0F - Show.smooth((age - 0.5F) / 1.3F)) * fade;
                if (!(life <= 0.01F)) {
                    float px = v.x(f.x);
                    float py = v.y(f.y);
                    float pz = v.z(f.z);
                    if (f.ember) {
                        embers.add(new float[] {
                            px, py, pz, f.size * 1.7F, life * (0.6F + 0.4F * Mth.sin((double) (t * 12.0F + f.phase)))
                        });
                    } else {
                        q.identity().rotateAxis(f.angle, f.axis);
                        Vector3f at = new Vector3f(px, py, pz);
                        Vector3f a = q.transform(new Vector3f(f.a)).add(at);
                        Vector3f b = q.transform(new Vector3f(f.b)).add(at);
                        Vector3f cc = q.transform(new Vector3f(f.c)).add(at);
                        float cool = Show.smooth(age / 0.35F);
                        dark.add(new Show.Tri(a, b, cc, Show.alpha(Fx.lerp(c.main(), 0xFF1D1A19, cool), 0.9F * life)));
                        if (cool < 1.0F) {
                            hot.add(new Show.Tri(a, b, cc, Show.alpha(c.light(), (1.0F - cool) * life)));
                        }
                    }
                }
            }
        }

        Show.triangles(v, dark, false);
        Show.triangles(v, hot, true);
        if (!embers.isEmpty()) {
            Show.twice(v.pose(), Show.ORB, 0.0F, 1.0F, (e) -> {
                for (float[] em : embers) {
                    Show.quad(
                            e,
                            v,
                            em[0],
                            em[1],
                            em[2],
                            em[3],
                            0.0F,
                            Show.alpha(Fx.lerp(c.light(), c.deep(), 1.0F - em[4]), em[4]));
                }
            });
        }
    }

    private static void execution(Death d, float t, Show.View v, Show.Pal c) {
        float s = d.scale;
        float x = v.x(d.at.x);
        float y = v.y(d.at.y);
        float z = v.z(d.at.z);
        float strike = 0.07F;
        float te = t - strike;
        Vector3f side = new Vector3f(v.left().x, 0.0F, v.left().z);
        if (side.lengthSquared() < 1.0E-4F) {
            side.set(1.0F, 0.0F, 0.0F);
        }

        side.normalize();
        if (te >= 0.0F && d.once(0)) {
            sound(d, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 0.7F);
            sound(d, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.55F);
            sound(d, SoundEvents.WARDEN_SONIC_BOOM, 0.25F, 2.0F);
            jolt(d, 3.4F * s);

            for (int i = 0; i < 90; i++) {
                float dir = i % 2 == 0 ? 1.0F : -1.0F;
                float sp = (5.0F + d.rnd.nextFloat() * 7.0F) * s;
                Fx.spawn()
                        .at(d.at.add(0.0, d.rnd.nextDouble() * (double) d.height, 0.0))
                        .vel(
                                (double) (side.x * dir * sp) + d.rnd.nextGaussian(),
                                1.0 + d.rnd.nextDouble() * 4.0,
                                (double) (side.z * dir * sp) + d.rnd.nextGaussian())
                        .tex(Fx.Tex.SPARK)
                        .mode(Fx.Mode.STRETCH)
                        .size(0.06F * s, 0.0F)
                        .life(0.45F + d.rnd.nextFloat() * 0.4F)
                        .drag(2.6F)
                        .gravity(8.0F)
                        .colour(c.light(), c.deep() & 0xFFFFFF);
            }
        }

        float top = 22.0F * s;
        float chest = d.height * 0.55F;
        int n = 40;
        float open = te < 0.0F ? 0.0F : Show.easeOut(te / 0.06F) * (1.0F - Show.smooth((te - 0.1F) / 0.32F));
        float thread = te < 0.0F ? 1.0F : 1.0F - Show.smooth((te - 0.05F) / 0.4F);
        if (thread > 0.01F || open > 0.01F) {
            float[] xs = new float[n];
            float[] ys = new float[n];
            float[] zs = new float[n];
            float[] glowW = new float[n];
            float[] coreW = new float[n];
            int[] glow = new int[n];
            int[] core = new int[n];
            float lean = Mth.sin((double) d.lean);

            for (int i = 0; i < n; i++) {
                float f = (float) i / (float) (n - 1);
                float h = f * top;
                xs[i] = x + v.left().x * lean * (h - chest);
                ys[i] = y + h;
                zs[i] = z + v.left().z * lean * (h - chest);
                float shape = h < chest
                        ? (float) Math.pow((double) (h / chest), 0.7)
                        : (float) Math.pow((double) (1.0F - (h - chest) / (top - chest)), 1.6);
                coreW[i] = (0.04F + 0.55F * open * shape) * s;
                glowW[i] = coreW[i] * 2.4F + 0.15F * s;
                glow[i] = c.main();
                core[i] = c.light();
            }

            float a = Math.max(thread, open);
            Show.line(v, Show.TRAIL, xs, ys, zs, glowW, glow, 0.0F, 0.7F * a);
            Show.line(v, Show.TRAIL, xs, ys, zs, coreW, core, 0.8F * a, a);
        }

        if (!(te < 0.0F)) {
            if (te < 0.22F) {
                Show.sprite(v, Show.ORB, x, y + chest, z, 3.0F * s, 0.0F, c.light(), 0.0F, 1.0F - te / 0.22F);
            }

            float grow = Show.easeOut(te / 0.12F);
            float glowing = 1.0F - Show.smooth((te - 0.4F) / 1.5F);
            Show.flat(v, Show.DISC, x, y + 0.006F, z, 1.1F * s / 0.9F, 0.0F, 0xFF120B08, 0.45F * glowing, 0.0F);
            Random jag = new Random(d.seed);

            for (int dir = -1; dir <= 1; dir += 2) {
                int m = 10;
                float[] gx = new float[m];
                float[] gz = new float[m];
                float len = 3.6F * s * grow;

                for (int i = 0; i < m; i++) {
                    float f = (float) i / (float) (m - 1);
                    float off = i == 0 ? 0.0F : (jag.nextFloat() - 0.5F) * 0.18F * s;
                    gx[i] = x + side.x * (float) dir * len * f - side.z * off;
                    gz[i] = z + side.z * (float) dir * len * f + side.x * off;
                }

                Show.groundLine(v, gx, gz, y + 0.012F, 0.36F * s, c.main(), 0.6F * glowing);
                Show.groundLine(v, gx, gz, y + 0.013F, 0.07F * s, c.light(), glowing);
            }

            Show.groundRing(v, x, y, z, te, 0.0F, 0.6F, 3.4F * s, c.light(), 1.0F);
            float reach = screenReach(d);
            if (reach > 0.0F && te < 0.45F) {
                Show.centre(v, x, y + chest, z);
                float k = 1.0F - te / 0.45F;
                ScreenFx.flash(
                        0.45F * (1.0F - Show.clamp(te / 0.18F)) * reach,
                        Show.r(c.light()),
                        Show.g(c.light()),
                        Show.b(c.light()));
                ScreenFx.wave(te / 0.45F * 1.3F, 0.05F, 0.04F * k * reach, 0.6F * k * reach);
                ScreenFx.grade(0.75F * (1.0F - Show.smooth((te - 0.1F) / 0.3F)) * reach, 0.35F * k * reach, 0.0F, 0.0F);
            }
        }
    }

    private static void blackHole(Death d) {
        Vec3 hole = d.at.add(0.0, (double) d.height * 0.55, 0.0);
        int i = 0;

        for (int n = count(d, 380); i < n; i++) {
            double[] p = inBody(d);
            Bit b = new Bit();
            b.ox = (float) (p[0] - hole.x);
            b.oy = (float) (p[1] - hole.y);
            b.oz = (float) (p[2] - hole.z);
            b.size = (0.035F + d.rnd.nextFloat() * 0.045F) * d.scale;
            b.free = 0.3F + d.rnd.nextFloat() * 1.05F;
            b.phase = d.rnd.nextFloat() * ((float) Math.PI * 2F);
            b.spin = 4.0F + d.rnd.nextFloat() * 8.0F;
            b.axis = randomAxis(d.rnd);
            d.bits.add(b);
        }
    }

    private static void blackHole(Death d, float t, Show.View v, Show.Pal c) {
        float s = d.scale;
        float x = v.x(d.at.x);
        float y = v.y(d.at.y);
        float z = v.z(d.at.z);
        float cy = y + d.height * 0.55F;
        float open = Show.easeOut(t / 0.4F);
        float shut = t < 2.2F ? 1.0F : 1.0F - Show.easeIn((t - 2.2F) / 0.22000003F);
        float k = open * shut;
        float R = 0.46F * s * k;
        float reach = screenReach(d);
        if (d.once(0)) {
            sound(d, SoundEvents.WARDEN_SONIC_CHARGE, 0.9F, 0.55F);
            sound(d, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.5F);
            sound(d, SoundEvents.PORTAL_TRIGGER, 0.35F, 1.7F);
        }

        if (t < 2.42F) {
            if (reach > 0.0F) {
                Show.centre(v, x, cy, z);
                ScreenFx.wave(
                        0.1F + 0.03F * Mth.sin((double) (t * 4.0F)), 0.07F, 0.035F * k * reach, 0.45F * k * reach);
                ScreenFx.grade(0.3F * k * reach, 0.45F * k * reach, 0.0F, 0.05F * k * reach);
            }

            float tilt = 0.4F + d.lean;
            Vector3f e1 = new Vector3f(d.wind.x, 0.0F, d.wind.z);
            Vector3f e2 = new Vector3f(
                    -d.wind.z * Mth.cos((double) tilt), Mth.sin((double) tilt), d.wind.x * Mth.cos((double) tilt));
            Vector3f eye = new Vector3f(v.toward(x, cy, z));
            if (eye.lengthSquared() > 1.0E-6F) {
                eye.normalize();
            }

            int far = e2.dot(eye) > 0.0F ? 1 : -1;
            List<Dot> back = new ArrayList<>();
            List<Dot> front = new ArrayList<>();
            float whirl = t > 2.2F ? (t - 2.2F) * 14.0F : 0.0F;

            for (int i = 0; i < 160; i++) {
                float f = hash(d.seed, i);
                float ring = 1.5F + 2.4F * f * f;
                float a = hash(d.seed, i + 977) * ((float) Math.PI * 2F)
                        + t * 5.0F / (float) Math.pow((double) ring, 1.5)
                        + whirl;
                float ca = Mth.cos((double) a);
                float sa = Mth.sin((double) a);
                float rr = R * ring;
                float px = x + (e1.x * ca + e2.x * sa) * rr;
                float py = cy + (e1.y * ca + e2.y * sa) * rr;
                float pz = z + (e1.z * ca + e2.z * sa) * rr;
                float glow = (0.55F + 0.45F * Mth.sin((double) (t * 6.0F + (float) i))) * k;
                int col = Show.alpha(Fx.lerp(c.light(), c.deep(), f), glow);
                Dot dot = new Dot(px, py, pz, (0.045F + 0.04F * (1.0F - f)) * s * Math.max(0.3F, k), col);
                (sa * (float) far > 0.0F ? back : front).add(dot);
            }

            Show.sprite(v, Show.ORB, x, cy, z, R * 3.6F, 0.0F, c.main(), 0.0F, 0.32F * k);
            plane(v, Show.RING, x, cy, z, e1, e2, R * 2.3F / 0.86F, far, c.main(), 0.15F * k, 0.55F * k);
            dots(v, Show.ORB, back, 0.0F, 1.0F);
            List<Show.Tri> scraps = new ArrayList<>();
            List<Show.Tri> lit = new ArrayList<>();
            Quaternionf q = new Quaternionf();

            for (Bit b : d.bits) {
                float u = Show.clamp((t - b.free) / 1.05F);
                if (!(t < b.free) && !(u >= 1.0F)) {
                    float left = 1.0F - Show.easeIn(u);
                    float turn = u * u * 7.0F;
                    float ca = Mth.cos((double) turn);
                    float sa = Mth.sin((double) turn);
                    float ox = b.ox * ca - b.oz * sa;
                    float oz = b.ox * sa + b.oz * ca;
                    Vector3f p = new Vector3f(x + ox * left, cy + b.oy * left, z + oz * left);
                    Vector3f in = (new Vector3f(x, cy, z)).sub(p);
                    if (in.lengthSquared() > 1.0E-8F) {
                        in.normalize();
                    }

                    float sz = b.size * (0.5F + 0.5F * left);
                    q.identity().rotateAxis(t * b.spin + b.phase, b.axis);
                    Vector3f a1 = q.transform(new Vector3f(sz, 0.0F, 0.0F)).add(p);
                    Vector3f a2 =
                            q.transform(new Vector3f(-sz * 0.5F, sz, 0.0F)).add(p);
                    Vector3f tip =
                            (new Vector3f(in)).mul(sz * (1.5F + 9.0F * u)).add(p);
                    scraps.add(new Show.Tri(a1, a2, tip, Show.alpha(Fx.lerp(0xFF221A2C, c.main(), u), 0.9F)));
                    if (u > 0.4F) {
                        lit.add(new Show.Tri(a1, a2, tip, Show.alpha(c.light(), (u - 0.4F) / 0.6F * 0.8F)));
                    }
                }
            }

            Show.triangles(v, scraps, false);
            Show.triangles(v, lit, true);
            Show.sprite(v, Show.ORB, x, cy, z, R * 1.4F, 0.0F, 0xFF000000, 1.0F, 0.0F);
            Show.sprite(v, Show.ORB, x, cy, z, R * 1.15F, 0.0F, 0xFF000000, 1.0F, 0.0F);
            Show.sprite(v, Show.RING, x, cy, z, R * 1.06F / 0.86F, t * 1.5F, c.light(), 0.25F * k, 0.95F * k);
            dots(v, Show.ORB, front, 0.0F, 1.0F);
            plane(v, Show.RING, x, cy, z, e1, e2, R * 2.3F / 0.86F, -far, c.main(), 0.15F * k, 0.55F * k);
        } else {
            if (d.once(1)) {
                sound(d, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.5F);
                sound(d, SoundEvents.WARDEN_SONIC_BOOM, 0.45F, 1.4F);
                sound(d, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.6F);
                jolt(d, 3.0F * s);
                Vec3 at = d.at.add(0.0, (double) d.height * 0.55, 0.0);

                for (int i = 0; i < 70; i++) {
                    Vec3 dv = (new Vec3(d.rnd.nextGaussian(), d.rnd.nextGaussian(), d.rnd.nextGaussian()))
                            .normalize()
                            .scale((3.0 + d.rnd.nextDouble() * 6.0) * (double) s);
                    Fx.spawn()
                            .at(at)
                            .vel(dv.x, dv.y, dv.z)
                            .tex(i % 3 == 0 ? Fx.Tex.GLOW : Fx.Tex.SPARKLE)
                            .size(0.13F * s, 0.0F)
                            .spin(d.rnd.nextFloat() * 6.0F, 3.0F)
                            .life(0.6F + d.rnd.nextFloat() * 0.6F)
                            .drag(2.2F)
                            .twinkle(0.5F)
                            .colour(c.light(), c.main() & 0xFFFFFF);
                }
            }

            float te = t - 2.42F;
            float u = te / 0.5F;
            if (u < 1.0F) {
                Show.sprite(
                        v, Show.ORB, x, cy, z, (0.3F + 2.6F * Show.easeOut(u)) * s, 0.0F, c.light(), 0.0F, 1.0F - u);
                Show.sprite(
                        v,
                        Fx.Tex.RING_WAVE.id,
                        x,
                        cy,
                        z,
                        (0.4F + 3.6F * Show.easeOut(u)) * s,
                        0.0F,
                        c.main(),
                        0.2F * (1.0F - u),
                        1.0F - u);
                if (reach > 0.0F) {
                    Show.centre(v, x, cy, z);
                    ScreenFx.flash(
                            0.5F * (1.0F - Show.clamp(u * 3.0F)) * reach,
                            Show.r(c.light()),
                            Show.g(c.light()),
                            Show.b(c.light()));
                    ScreenFx.wave(u * 1.3F, 0.06F, 0.05F * (1.0F - u) * reach, 0.7F * (1.0F - u) * reach);
                }
            }

            Show.groundRing(v, x, y, z, te, 0.0F, 0.7F, 3.2F * s, c.main(), 1.0F);
            float a = 1.0F - Show.smooth((te - 0.2F) / 0.5F);
            Show.sprite(v, Fx.Tex.SPARKLE.id, x, cy, z, 0.25F * s * a, t * 3.0F, c.light(), 0.0F, a);
        }
    }

    private static void glitch(Death d) {
        float cell = 0.1F * d.scale;
        int i = 0;

        for (int n = count(d, 320); i < n; i++) {
            double[] p = inBody(d);
            Bit b = new Bit();
            b.x = d.at.x + (double) ((float) Math.round((p[0] - d.at.x) / (double) cell) * cell);
            b.y = d.at.y + (double) ((float) Math.round((p[1] - d.at.y) / (double) cell) * cell);
            b.z = d.at.z + (double) ((float) Math.round((p[2] - d.at.z) / (double) cell) * cell);
            b.size = cell * 0.92F;
            b.phase = d.rnd.nextFloat();
            b.colour = d.rnd.nextInt(3);
            b.spin = (float) i;
            d.bits.add(b);
        }
    }

    private static void glitch(Death d, float t, float dt, Show.View v, Show.Pal c) {
        float s = d.scale;
        float x = v.x(d.at.x);
        float y = v.y(d.at.y);
        float z = v.z(d.at.z);
        float cy = y + d.height * 0.55F;
        int frame = (int) (t * 18.0F);
        Random jit = new Random(d.seed ^ (long) frame * -7046029254386353131L);
        float grow = Show.smooth(t / 0.7F);
        float squash = t < 1.0F ? 1.0F : 1.0F - Show.easeIn((t - 1.0F) / 0.17999995F);
        float narrow = t < 1.18F ? 1.0F : 1.0F - Show.easeIn((t - 1.18F) / 0.22000003F);
        float reach = screenReach(d);
        if (t < 1.0F) {
            d.owed += dt * 15.0F;

            while (d.owed >= 1.0F) {
                --d.owed;
                sound(d, SoundEvents.NOTE_BLOCK_BIT.value(), 0.35F, 0.5F + d.rnd.nextFloat() * 1.5F);
            }
        }

        if (t >= 1.0F && d.once(0)) {
            sound(d, SoundEvents.BEACON_DEACTIVATE, 0.9F, 2.0F);
        }

        if (t >= 1.4F && d.once(1)) {
            sound(d, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 2.0F);
            sound(d, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8F, 1.8F);
        }

        if (!(t < 1.4F)) {
            float te = t - 1.4F;
            if (te < 0.25F) {
                float a = 1.0F - te / 0.25F;
                Show.sprite(v, Show.ORB, x, cy, z, (0.25F + 0.5F * te) * s, 0.0F, c.light(), 0.0F, a);
                Show.twice(v.pose(), Fx.Tex.WHITE.id, 0.0F, a, (e) -> {
                    rect(e, v, x, cy, z, (0.35F + 1.2F * te) * s * a, 0.008F * s, c.light());
                    rect(e, v, x, cy, z, 0.008F * s, (0.18F + 0.4F * te) * s * a, c.light());
                });
                if (reach > 0.0F && te < 0.12F) {
                    ScreenFx.flash(0.3F * (1.0F - te / 0.12F) * reach, 1.0F, 1.0F, 1.0F);
                }
            }

            float fade = 1.0F - Show.smooth((t - LIFE[3] + 0.4F) / 0.4F);
            List<Dot> dead = new ArrayList<>();

            for (int i = 0; i < Math.min(18, d.bits.size()); i++) {
                Bit b = (Bit) d.bits.get(i);
                if (!(hash(d.seed + (long) frame, i) < 0.3F)) {
                    float rise = te * (0.3F + b.phase * 0.5F);
                    int col = b.colour == 0 ? c.deep() : (b.colour == 1 ? c.main() : c.light());
                    dead.add(new Dot(v.x(b.x), v.y(b.y) + rise, v.z(b.z), b.size * 0.5F, Show.alpha(col, fade)));
                }
            }

            dots(v, Fx.Tex.WHITE.id, dead, 0.0F, 1.0F);
        } else {
            if (reach > 0.0F) {
                Show.centre(v, x, cy, z);
                float spike = jit.nextFloat() < 0.3F ? jit.nextFloat() : 0.0F;
                ScreenFx.wave(0.6F, 1.5F, 0.0F, (0.12F + 0.85F * spike) * grow * reach);
                if (t >= 1.0F) {
                    ScreenFx.grade(0.4F * reach, 0.3F * reach, 0.0F, 0.0F);
                }
            }

            float[] shove = new float[9];

            for (int i = 0; i < shove.length; i++) {
                shove[i] = jit.nextFloat() < 0.4F * grow * squash ? (jit.nextFloat() - 0.5F) * 0.7F * s * grow : 0.0F;
            }

            boolean skip = squash == 1.0F && jit.nextFloat() < 0.1F * grow;
            float split = (0.015F + 0.07F * grow * jit.nextFloat()) * s * squash;
            if (!skip) {
                voxels(d, v, c.deep(), split, shove, squash, narrow, x, cy, z, frame, grow, 0.0F, 0.75F);
                voxels(d, v, c.main(), -split, shove, squash, narrow, x, cy, z, frame, grow, 0.0F, 0.75F);
                voxels(d, v, c.light(), 0.0F, shove, squash, narrow, x, cy, z, frame, grow, 0.85F, 0.35F);
            }

            if (t < 1.0F) {
                int bars = jit.nextInt(4);
                float[][] bar = new float[bars][];

                for (int i = 0; i < bars; i++) {
                    bar[i] = new float[] {
                        (jit.nextFloat() - 0.5F) * 1.6F * s,
                        (jit.nextFloat() - 0.1F) * d.height,
                        (0.15F + jit.nextFloat() * 0.5F) * s,
                        (0.012F + jit.nextFloat() * 0.04F) * s,
                        (float) jit.nextInt(3)
                    };
                }

                Vector3f l = v.left();
                Show.twice(v.pose(), Fx.Tex.WHITE.id, 0.0F, 0.8F * grow, (e) -> {
                    for (float[] b : bar) {
                        int col = b[4] < 1.0F ? c.deep() : (b[4] < 2.0F ? c.main() : c.light());
                        rect(e, v, x + l.x * b[0], y + b[1], z + l.z * b[0], b[2], b[3], col);
                    }
                });
            }

            if (t >= 1.0F) {
                float hw = Math.max(0.02F, d.width * 1.1F * s * narrow) + 0.05F * s * (1.0F - squash);
                Show.twice(
                        v.pose(), Fx.Tex.WHITE.id, 0.0F, 1.0F, (e) -> rect(e, v, x, cy, z, hw, 0.018F * s, c.light()));
                Show.sprite(v, Show.ORB, x, cy, z, (0.3F + 0.4F * narrow) * s, 0.0F, c.main(), 0.0F, 0.6F);
            }
        }
    }

    private static void voxels(
            Death d,
            Show.View v,
            int colour,
            float off,
            float[] shove,
            float squash,
            float narrow,
            float cx,
            float cy,
            float cz,
            int frame,
            float grow,
            float shape,
            float glow) {
        Vector3f l = v.left();
        Show.twice(v.pose(), Fx.Tex.WHITE.id, shape, glow, (e) -> {
            for (Bit b : d.bits) {
                if (!(hash(d.seed + (long) frame, (int) b.spin) < 0.07F * grow)) {
                    float hy = (float) ((b.y - d.at.y) / (double) d.height);
                    int band = Math.max(0, Math.min(shove.length - 1, (int) (hy * (float) shove.length)));
                    float px = v.x(b.x);
                    float py = v.y(b.y);
                    float pz = v.z(b.z);
                    py = cy + (py - cy) * squash;
                    float lat = (px - cx) * l.x + (pz - cz) * l.z;
                    px -= l.x * lat * (1.0F - narrow);
                    pz -= l.z * lat * (1.0F - narrow);
                    float sh = shove[band] + off;
                    Show.quad(e, v, px + l.x * sh, py + l.y * sh, pz + l.z * sh, b.size * 0.5F, 0.0F, colour);
                }
            }
        });
    }

    private static void slice(Death d) {
        d.cuts = new float[4][];

        for (int k = 0; k < 4; k++) {
            float side = k % 2 == 0 ? 1.0F : -1.0F;
            d.cuts[k] = new float[] {
                side * (float) Math.toRadians((double) (16.0F + d.rnd.nextFloat() * 52.0F)),
                (d.rnd.nextFloat() - 0.5F) * 0.5F,
                0.08F + (float) k * 0.1F,
                (d.rnd.nextFloat() - 0.5F) * 0.6F
            };
        }
    }

    private static void slice(Death d, float t, Show.View v, Show.Pal c) {
        float s = d.scale;
        float x = v.x(d.at.x);
        float y = v.y(d.at.y);
        float z = v.z(d.at.z);
        float mid = y + d.height * 0.55F;
        Vector3f l = v.left();
        Vector3f u = v.up();
        float reach = screenReach(d);
        if (d.once(0)) {
            sound(d, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 2.0F);
        }

        if (reach > 0.0F && t < 0.85F) {
            float k = Show.smooth(t / 0.08F) * (1.0F - Show.smooth((t - 0.7F) / 0.15F));
            Show.centre(v, x, mid, z);
            ScreenFx.grade(0.85F * k * reach, 0.4F * k * reach, 0.0F, 0.0F);
        }

        float fade = t < 0.7F ? 1.0F : 1.0F - Show.smooth((t - 0.7F) / 0.25F);
        float flare = t < 0.7F ? 0.0F : 1.0F - Show.smooth((t - 0.7F) / 0.18F);

        for (int k = 0; k < d.cuts.length; k++) {
            float[] cut = d.cuts[k];
            float te = t - cut[2];
            if (!(te < 0.0F)) {
                float ca = Mth.cos((double) cut[0]);
                float sa = Mth.sin((double) cut[0]);
                float dx = l.x * ca + u.x * sa;
                float dy = l.y * ca + u.y * sa;
                float dz = l.z * ca + u.z * sa;
                float nx = u.x * ca - l.x * sa;
                float ny = u.y * ca - l.y * sa;
                float nz = u.z * ca - l.z * sa;
                float off = cut[1] * s * 0.4F;
                float len = 2.6F * s;
                float ox = x + nx * off;
                float oy = mid + cut[3] * d.height * 0.5F + ny * off;
                float oz = z + nz * off;
                if (d.once(1 + k)) {
                    sound(d, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.3F + (float) k * 0.15F);

                    for (int i = 0; i < 16; i++) {
                        float f = d.rnd.nextFloat() - 0.5F;
                        float side = d.rnd.nextBoolean() ? 1.0F : -1.0F;
                        float sp = (1.5F + d.rnd.nextFloat() * 3.0F) * s;
                        Fx.spawn()
                                .at(v.world(new Vector3f(ox + dx * f * len, oy + dy * f * len, oz + dz * f * len)))
                                .vel((double) (nx * side * sp), (double) (ny * side * sp + 1.0F), (double)
                                        (nz * side * sp))
                                .tex(Fx.Tex.SPARK)
                                .mode(Fx.Mode.STRETCH)
                                .size(0.045F * s, 0.0F)
                                .life(0.3F + d.rnd.nextFloat() * 0.25F)
                                .drag(3.0F)
                                .colour(c.light(), c.main() & 0xFFFFFF);
                    }
                }

                if (!(fade <= 0.01F)) {
                    float draw = Show.easeOut(te / 0.07F);
                    int n = 16;
                    float[] xs = new float[n];
                    float[] ys = new float[n];
                    float[] zs = new float[n];
                    float[] wg = new float[n];
                    float[] wc = new float[n];
                    int[] cg = new int[n];
                    int[] cc = new int[n];

                    for (int i = 0; i < n; i++) {
                        float along = ((float) i / (float) (n - 1) * draw - 0.5F) * len;
                        xs[i] = ox + dx * along;
                        ys[i] = oy + dy * along;
                        zs[i] = oz + dz * along;
                        wc[i] = 0.03F * s * (1.0F + 1.6F * flare) * (0.6F + 0.4F * fade);
                        wg[i] = 0.17F * s * (1.0F + flare);
                        cg[i] = c.main();
                        cc[i] = c.light();
                    }

                    Show.line(v, Show.TRAIL, xs, ys, zs, wg, cg, 0.0F, 0.75F * fade + 0.5F * flare);
                    Show.line(v, Show.TRAIL, xs, ys, zs, wc, cc, 0.9F * fade, fade + flare);
                }
            }
        }

        if (t >= 0.7F && d.once(6)) {
            sound(d, SoundEvents.ANVIL_LAND, 0.25F, 2.0F);
            sound(d, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
            sound(d, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
            jolt(d, 2.2F * s);

            for (float[] cut : d.cuts) {
                float ca = Mth.cos((double) cut[0]);
                float sa = Mth.sin((double) cut[0]);
                float dx = l.x * ca + u.x * sa;
                float dy = l.y * ca + u.y * sa;
                float dz = l.z * ca + u.z * sa;
                float nx = u.x * ca - l.x * sa;
                float ny = u.y * ca - l.y * sa;
                float nz = u.z * ca - l.z * sa;
                float off = cut[1] * s * 0.4F;
                float len = 2.6F * s;
                float ox = x + nx * off;
                float oy = mid + cut[3] * d.height * 0.5F + ny * off;
                float oz = z + nz * off;

                for (int i = 0; i < 40; i++) {
                    float f = d.rnd.nextFloat() - 0.5F;
                    float side = d.rnd.nextBoolean() ? 1.0F : -1.0F;
                    float sp = (1.0F + d.rnd.nextFloat() * 2.6F) * s;
                    Vec3 at = v.world(new Vector3f(ox + dx * f * len, oy + dy * f * len, oz + dz * f * len));
                    Fx.spawn()
                            .at(at)
                            .vel(
                                    (double) (nx * side * sp),
                                    (double) (ny * side * sp) + 0.6 + d.rnd.nextDouble() * 1.4,
                                    (double) (nz * side * sp))
                            .tex(Fx.Tex.SAKURA)
                            .blend(false)
                            .size(0.13F * s, 0.09F * s)
                            .spin(d.rnd.nextFloat() * 6.0F, (d.rnd.nextFloat() - 0.5F) * 8.0F)
                            .life(1.4F + d.rnd.nextFloat() * 0.8F)
                            .drag(1.6F)
                            .gravity(1.1F)
                            .colour(c.main(), c.deep() & 0xFFFFFF);
                    if (i % 4 == 0) {
                        Fx.spawn()
                                .at(at)
                                .vel(
                                        (double) (nx * side * sp) * 0.6,
                                        (double) (ny * side * sp) * 0.6 + 0.3,
                                        (double) (nz * side * sp) * 0.6)
                                .tex(Fx.Tex.GLOW)
                                .size(0.1F * s, 0.0F)
                                .life(0.6F + d.rnd.nextFloat() * 0.4F)
                                .drag(2.0F)
                                .twinkle(0.5F)
                                .colour(c.light(), c.main() & 0xFFFFFF);
                    }
                }
            }
        }

        if (t >= 0.7F && reach > 0.0F) {
            float k = (t - 0.7F) / 0.3F;
            if (k < 1.0F) {
                ScreenFx.flash(0.35F * (1.0F - k) * reach, 1.0F, 1.0F, 1.0F);
            }
        }

        float glowing = t < 0.1F ? 0.0F : 1.0F - Show.smooth((t - 0.7F - 0.3F) / 1.2F);
        Vector3f side = new Vector3f(l.x, 0.0F, l.z);
        if (side.lengthSquared() < 1.0E-4F) {
            side.set(1.0F, 0.0F, 0.0F);
        }

        side.normalize();
        float[] gx = new float[6];
        float[] gz = new float[6];

        for (int i = 0; i < 6; i++) {
            float f = ((float) i / 5.0F - 0.5F) * 2.6F * s * Show.easeOut((t - 0.1F) / 0.1F);
            gx[i] = x + side.x * f;
            gz[i] = z + side.z * f;
        }

        Show.groundLine(v, gx, gz, y + 0.012F, 0.26F * s, c.main(), 0.6F * glowing);
        Show.groundLine(v, gx, gz, y + 0.013F, 0.05F * s, c.light(), glowing);
    }

    private static int partyColour(Death d, int i) {
        if (d.pal.equals(own(5))) {
            return PARTY[Math.floorMod(i, PARTY.length)];
        } else {
            int[] mine = {d.pal.light(), d.pal.main(), d.pal.deep(), -1};
            return mine[Math.floorMod(i, mine.length)];
        }
    }

    private static void confetti(Death d) {
        Vec3 o = d.at.add(0.0, (double) d.height * 0.6, 0.0);
        float s = d.scale;

        for (int i = 0; i < 260; i++) {
            Bit b = new Bit();
            b.x = o.x + (d.rnd.nextDouble() - 0.5) * (double) d.width;
            b.y = o.y + (d.rnd.nextDouble() - 0.5) * 0.6;
            b.z = o.z + (d.rnd.nextDouble() - 0.5) * (double) d.width;
            double a = d.rnd.nextDouble() * Math.PI * 2.0;
            double out = (1.5 + d.rnd.nextDouble() * 4.5) * (double) s;
            b.vx = Math.cos(a) * out;
            b.vz = Math.sin(a) * out;
            b.vy = (3.0 + d.rnd.nextDouble() * 6.0) * (double) s;
            b.size = (0.05F + d.rnd.nextFloat() * 0.035F) * s;
            b.aspect = 0.45F + d.rnd.nextFloat() * 0.3F;
            b.axis = randomAxis(d.rnd);
            b.spin = 6.0F + d.rnd.nextFloat() * 12.0F;
            b.phase = d.rnd.nextFloat() * ((float) Math.PI * 2F);
            b.colour = partyColour(d, d.rnd.nextInt(28));
            d.bits.add(b);
        }
    }

    private static void confetti(Death d, float t, float dt, Show.View v, Show.Pal c) {
        float s = d.scale;
        float x = v.x(d.at.x);
        float y = v.y(d.at.y);
        float z = v.z(d.at.z);
        float chest = d.height * 0.6F;
        float reach = screenReach(d);
        if (d.once(0)) {
            sound(d, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0F, 1.2F);
            sound(d, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.8F, 1.4F);
            sound(d, SoundEvents.PLAYER_LEVELUP, 0.45F, 1.5F);
            jolt(d, 1.2F * s);
        }

        float[] at = {0.0F, 0.1F, 0.2F, 0.34F};
        float[] pitch = {1.0F, 1.26F, 1.5F, 2.0F};

        for (int i = 0; i < at.length; i++) {
            if (t >= at[i] && d.once(1 + i)) {
                sound(d, SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F, pitch[i]);
            }
        }

        if (t < 0.35F) {
            float u = t / 0.35F;
            Show.sprite(
                    v, Show.ORB, x, y + chest, z, (0.3F + 1.8F * Show.easeOut(u)) * s, 0.0F, c.light(), 0.0F, 1.0F - u);
            Show.sprite(
                    v,
                    Fx.Tex.RING_DOTS.id,
                    x,
                    y + chest,
                    z,
                    (0.3F + 2.4F * Show.easeOut(u)) * s,
                    u * 2.0F,
                    c.main(),
                    0.0F,
                    1.0F - u);
            if (reach > 0.0F) {
                Show.centre(v, x, y + chest, z);
                ScreenFx.flash(0.18F * (1.0F - u) * reach, Show.r(c.light()), Show.g(c.light()), Show.b(c.light()));
            }
        }

        float fade = 1.0F - Show.smooth((t - LIFE[5] + 0.7F) / 0.7F);
        double ground = d.at.y + 0.02;
        List<Show.Tri> paper = new ArrayList<>();
        Quaternionf q = new Quaternionf();

        for (Bit b : d.bits) {
            if (!b.down) {
                float drag = (float) Math.exp((double) (-2.4F * dt));
                b.vx = b.vx * (double) drag + (double) (Mth.sin((double) (t * 5.0F + b.phase)) * 2.2F * dt);
                b.vz = b.vz * (double) drag + (double) (Mth.cos((double) (t * 4.3F + b.phase * 1.3F)) * 2.2F * dt);
                b.vy = Math.max(-1.1 * (double) s, b.vy * (double) drag - (double) (7.0F * dt));
                b.x += b.vx * (double) dt;
                b.y += b.vy * (double) dt;
                b.z += b.vz * (double) dt;
                b.angle += b.spin * dt;
                if (b.y <= ground && b.vy < 0.0) {
                    b.y = ground;
                    b.down = true;
                    b.axis = new Vector3f(1.0F, 0.0F, 0.0F);
                    b.angle = ((float) Math.PI / 2F);
                }
            }

            q.identity().rotateAxis(b.angle, b.axis);
            float hw = b.size;
            float hh = b.size * b.aspect;
            Vector3f p = new Vector3f(v.x(b.x), v.y(b.y), v.z(b.z));
            Vector3f a1 = q.transform(new Vector3f(-hw, -hh, 0.0F)).add(p);
            Vector3f a2 = q.transform(new Vector3f(hw, -hh, 0.0F)).add(p);
            Vector3f a3 = q.transform(new Vector3f(hw, hh, 0.0F)).add(p);
            Vector3f a4 = q.transform(new Vector3f(-hw, hh, 0.0F)).add(p);
            Vector3f normal = q.transform(new Vector3f(0.0F, 0.0F, 1.0F));
            Vector3f eye = new Vector3f(v.toward(p.x, p.y, p.z));
            if (eye.lengthSquared() > 1.0E-6F) {
                eye.normalize();
            }

            int col = Show.alpha(shade(b.colour, 0.45F + 0.65F * Math.abs(normal.dot(eye))), fade);
            paper.add(new Show.Tri(a1, a2, a3, col));
            paper.add(new Show.Tri(a1, a3, a4, col));
        }

        Show.triangles(v, paper, false);
        Vector3f l = v.left();

        for (int r = 0; r < 7; r++) {
            float a = hash(d.seed, r) * ((float) Math.PI * 2F);
            float up = 0.7F + 0.3F * hash(d.seed, r + 50);
            float sp = (5.0F + 3.0F * hash(d.seed, r + 90)) * s;
            float side = (float) Math.sqrt((double) (1.0F - up * up));
            int col = partyColour(d, r * 3 + 1);
            int n = 18;
            float[] xs = new float[n];
            float[] ys = new float[n];
            float[] zs = new float[n];
            float[] w = new float[n];
            int[] cs = new int[n];

            for (int i = 0; i < n; i++) {
                float tau = Math.max(0.0F, t - (float) i * 0.045F);
                float k = (1.0F - (float) Math.exp((double) (-2.2F * tau))) / 2.2F;
                float curl = Mth.sin((double) ((float) i * 0.9F + t * 5.0F + (float) r)) * 0.1F * s;
                xs[i] = x + Mth.cos((double) a) * side * sp * k + l.x * curl;
                ys[i] = y + chest + up * sp * k - 0.9F * s * tau * tau;
                zs[i] = z + Mth.sin((double) a) * side * sp * k + l.z * curl;
                w[i] = 0.055F * s;
                cs[i] = col;
            }

            Show.line(v, Fx.Tex.LINE_SOFT.id, xs, ys, zs, w, cs, 0.95F * fade, 0.25F * fade);
        }

        float[] when = {0.4F, 0.65F, 0.95F};

        for (int f = 0; f < when.length; f++) {
            if (t >= when[f] && d.once(5 + f)) {
                Vec3 burst = d.at.add(
                        (double) ((hash(d.seed, 200 + f) - 0.5F) * 2.4F * s),
                        (double) (d.height + (1.2F + hash(d.seed, 210 + f) * 1.4F) * s),
                        (double) ((hash(d.seed, 220 + f) - 0.5F) * 2.4F * s));
                int col = partyColour(d, f * 2);
                sound(d, f == 1 ? SoundEvents.FIREWORK_ROCKET_TWINKLE : SoundEvents.FIREWORK_ROCKET_BLAST, 0.7F, 1.3F);

                for (int i = 0; i < 48; i++) {
                    Vec3 dv = (new Vec3(d.rnd.nextGaussian(), d.rnd.nextGaussian(), d.rnd.nextGaussian()))
                            .normalize()
                            .scale((2.5 + d.rnd.nextDouble() * 1.5) * (double) s);
                    Fx.spawn()
                            .at(burst)
                            .vel(dv.x, dv.y, dv.z)
                            .tex(Fx.Tex.SPARKLE)
                            .size(0.1F * s, 0.0F)
                            .spin(d.rnd.nextFloat() * 6.0F, 2.0F)
                            .life(0.7F + d.rnd.nextFloat() * 0.4F)
                            .drag(2.5F)
                            .gravity(1.2F)
                            .twinkle(0.7F)
                            .colour(col, col & 0xFFFFFF);
                }
            }
        }
    }

    private static void meteor(Death d, float t, float dt, Show.View v, Show.Pal c) {
        float s = d.scale;
        float x = v.x(d.at.x);
        float y = v.y(d.at.y);
        float z = v.z(d.at.z);
        float reach = screenReach(d);
        Vector3f from = new Vector3f(x - d.wind.x * 14.0F * s, y + 34.0F * s, z - d.wind.z * 14.0F * s);
        Vector3f to = new Vector3f(x, y + 0.2F, z);
        if (d.once(0)) {
            sound(d, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F, 0.45F);
            sound(d, SoundEvents.PHANTOM_SWOOP, 1.0F, 0.5F);
        }

        if (t < 0.62F) {
            int n = 22;
            float[] xs = new float[n];
            float[] ys = new float[n];
            float[] zs = new float[n];
            float[] w = new float[n];
            float[] wc = new float[n];
            int[] cs = new int[n];
            int[] cc = new int[n];

            for (int i = 0; i < n; i++) {
                float tt = Math.max(0.0F, t - (float) i * 0.018F);
                Vector3f p = (new Vector3f(from)).lerp(to, Show.easeIn(tt / 0.62F));
                float f = (float) i / (float) (n - 1);
                xs[i] = p.x;
                ys[i] = p.y;
                zs[i] = p.z;
                w[i] = 0.9F * s * (1.0F - f);
                wc[i] = 0.25F * s * (1.0F - f);
                cs[i] = Fx.lerp(c.main(), c.deep(), f);
                cc[i] = c.light();
            }

            Show.line(v, Show.TRAIL, xs, ys, zs, w, cs, 0.2F, 1.0F);
            Show.line(v, Show.TRAIL, xs, ys, zs, wc, cc, 0.0F, 1.0F);
            Vector3f head = (new Vector3f(from)).lerp(to, Show.easeIn(t / 0.62F));
            Show.sprite(v, Show.ORB, head.x, head.y, head.z, 0.9F * s, 0.0F, c.main(), 0.0F, 0.9F);
            Show.sprite(v, Show.ORB, head.x, head.y, head.z, 0.3F * s, 0.0F, -1, 0.0F, 1.0F);
            Show.sprite(v, Fx.Tex.SPARKLE.id, head.x, head.y, head.z, 0.7F * s, t * 8.0F, c.light(), 0.0F, 0.8F);
            Vec3 headW = v.world(head);
            emit(d, dt, 90.0F, () -> Fx.spawn()
                    .at(headW)
                    .vel(d.rnd.nextGaussian() * 0.6, d.rnd.nextGaussian() * 0.6, d.rnd.nextGaussian() * 0.6)
                    .tex(Fx.Tex.GLOW)
                    .size(0.12F * s, 0.0F)
                    .life(0.4F + d.rnd.nextFloat() * 0.3F)
                    .drag(1.5F)
                    .colour(c.light(), c.deep() & 0xFFFFFF));
            if (reach > 0.0F) {
                Show.centre(v, head.x, head.y, head.z);
                float k = t / 0.62F;
                ScreenFx.grade(0.2F * k * reach, 0.3F * k * reach, 0.0F, 0.0F);
            }

        } else {
            if (d.once(1)) {
                sound(d, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.7F);
                sound(d, SoundEvents.ANVIL_LAND, 0.6F, 0.5F);
                sound(d, SoundEvents.WARDEN_SONIC_BOOM, 0.3F, 1.8F);
                jolt(d, 4.5F * s);

                for (int i = 0; i < 46; i++) {
                    Bit b = new Bit();
                    b.x = d.at.x;
                    b.y = d.at.y + 0.2;
                    b.z = d.at.z;
                    double a = d.rnd.nextDouble() * Math.PI * 2.0;
                    double out = (2.5 + d.rnd.nextDouble() * 5.0) * (double) s;
                    b.vx = Math.cos(a) * out;
                    b.vz = Math.sin(a) * out;
                    b.vy = (3.0 + d.rnd.nextDouble() * 6.0) * (double) s;
                    b.size = (0.06F + d.rnd.nextFloat() * 0.1F) * s;
                    b.axis = randomAxis(d.rnd);
                    b.spin = 4.0F + d.rnd.nextFloat() * 10.0F;
                    d.bits.add(b);
                }

                for (int i = 0; i < 22; i++) {
                    Fx.spawn()
                            .at(d.at.add((d.rnd.nextDouble() - 0.5) * 1.2, 0.2, (d.rnd.nextDouble() - 0.5) * 1.2))
                            .vel(d.rnd.nextGaussian() * 0.8, 0.6 + d.rnd.nextDouble() * 1.2, d.rnd.nextGaussian() * 0.8)
                            .tex(Fx.Tex.SMOKE)
                            .blend(false)
                            .size(0.5F * s, 1.5F * s)
                            .life(1.6F + d.rnd.nextFloat())
                            .drag(1.2F)
                            .gravity(-0.15F)
                            .colour(0xA0707078, 5263448);
                }

                for (int i = 0; i < 70; i++) {
                    double a = d.rnd.nextDouble() * Math.PI * 2.0;
                    double sp = (3.0 + d.rnd.nextDouble() * 7.0) * (double) s;
                    Fx.spawn()
                            .at(d.at.add(0.0, 0.2, 0.0))
                            .vel(Math.cos(a) * sp, (2.0 + d.rnd.nextDouble() * 6.0) * (double) s, Math.sin(a) * sp)
                            .tex(Fx.Tex.SPARK)
                            .mode(Fx.Mode.STRETCH)
                            .size(0.06F * s, 0.0F)
                            .life(0.5F + d.rnd.nextFloat() * 0.5F)
                            .drag(1.8F)
                            .gravity(9.0F)
                            .colour(c.light(), c.main() & 0xFFFFFF);
                }
            }

            float te = t - 0.62F;
            if (te < 0.25F) {
                Show.sprite(v, Show.ORB, x, y + 0.5F, z, 3.5F * s, 0.0F, c.light(), 0.0F, 1.0F - te / 0.25F);
            }

            Show.groundRing(v, x, y, z, te, 0.0F, 0.55F, 4.5F * s, c.light(), 1.0F);
            Show.groundRing(v, x, y, z, te, 0.08F, 0.9F, 3.0F * s, c.main(), 0.7F);
            float glowing = 1.0F - Show.smooth((te - 0.5F) / 1.8F);
            float end = 1.0F - Show.smooth((te - 2.0F) / 0.6F);
            Show.flat(v, Show.DISC, x, y + 0.006F, z, 1.5F * s / 0.9F, 0.0F, 0xFF0B0A10, 0.55F * end, 0.0F);
            Random jag = new Random(d.seed);

            for (int k = 0; k < 8; k++) {
                float a = (float) k * ((float) Math.PI * 2F) / 8.0F + jag.nextFloat() * 0.5F;
                float len = (1.4F + jag.nextFloat() * 1.2F) * s * Show.easeOut(te / 0.15F);
                int m = 7;
                float[] gx = new float[m];
                float[] gz = new float[m];

                for (int i = 0; i < m; i++) {
                    float f = (float) i / (float) (m - 1);
                    float wob = i == 0 ? 0.0F : (jag.nextFloat() - 0.5F) * 0.5F;
                    gx[i] = x + Mth.cos((double) (a + wob * 0.4F)) * len * f;
                    gz[i] = z + Mth.sin((double) (a + wob * 0.4F)) * len * f;
                }

                Show.groundLine(v, gx, gz, y + 0.012F, 0.22F * s, c.main(), 0.7F * glowing);
                Show.groundLine(v, gx, gz, y + 0.013F, 0.05F * s, c.light(), glowing);
            }

            float cool = Show.smooth(te / 2.0F);
            Show.sprite(
                    v,
                    Show.ORB,
                    x,
                    y + 0.2F * s,
                    z,
                    0.7F * s * (1.0F - 0.5F * cool),
                    0.0F,
                    Fx.lerp(c.light(), c.deep(), cool),
                    0.0F,
                    (0.8F * glowing + 0.1F) * end);
            Show.sprite(v, Show.ORB, x, y + 0.2F * s, z, 0.22F * s, 0.0F, 0xFF141218, 0.9F * end, 0.0F);
            emit(d, dt, te < 2.0F ? 10.0F : 0.0F, () -> Fx.spawn()
                    .at(d.at.add((d.rnd.nextDouble() - 0.5) * 0.5, 0.25, (d.rnd.nextDouble() - 0.5) * 0.5))
                    .vel(d.rnd.nextGaussian() * 0.1, 0.5 + d.rnd.nextDouble() * 0.4, d.rnd.nextGaussian() * 0.1)
                    .tex(Fx.Tex.SMOKE)
                    .blend(false)
                    .size(0.25F * s, 0.9F * s)
                    .life(1.2F + d.rnd.nextFloat() * 0.6F)
                    .drag(0.8F)
                    .gravity(-0.2F)
                    .colour(1885364328, 5263448));
            List<Show.Tri> rock = new ArrayList<>();
            List<Show.Tri> hot = new ArrayList<>();
            Quaternionf q = new Quaternionf();

            for (Bit b : d.bits) {
                if (!b.down) {
                    float drag = (float) Math.exp((double) (-0.3F * dt));
                    b.vx *= (double) drag;
                    b.vz *= (double) drag;
                    b.vy -= (double) (16.0F * dt);
                    b.x += b.vx * (double) dt;
                    b.y += b.vy * (double) dt;
                    b.z += b.vz * (double) dt;
                    b.angle += b.spin * dt;
                    double floor = d.at.y + (double) b.size * 0.5;
                    if (b.y < floor && b.vy < 0.0) {
                        b.y = floor;
                        if (b.bounces++ == 0) {
                            b.vy = -b.vy * 0.35;
                            b.vx *= 0.5;
                            b.vz *= 0.5;
                        } else {
                            b.down = true;
                        }
                    }
                }

                q.identity().rotateAxis(b.angle, b.axis);
                Vector3f p = new Vector3f(v.x(b.x), v.y(b.y), v.z(b.z));
                float h = b.size;
                Vector3f a1 = q.transform(new Vector3f(-h, -h, 0.0F)).add(p);
                Vector3f a2 = q.transform(new Vector3f(h, -h * 0.7F, 0.0F)).add(p);
                Vector3f a3 = q.transform(new Vector3f(h * 0.8F, h, 0.0F)).add(p);
                Vector3f a4 =
                        q.transform(new Vector3f(-h * 0.6F, h * 0.9F, 0.0F)).add(p);
                int stone = Show.alpha(0xFF2A2730, end);
                rock.add(new Show.Tri(a1, a2, a3, stone));
                rock.add(new Show.Tri(a1, a3, a4, stone));
                int glow = Show.alpha(Fx.lerp(c.light(), c.deep(), cool), (1.0F - cool) * end);
                hot.add(new Show.Tri(a1, a2, a3, glow));
                hot.add(new Show.Tri(a1, a3, a4, glow));
            }

            Show.triangles(v, rock, false);
            Show.triangles(v, hot, true);
            if (reach > 0.0F && te < 0.5F) {
                Show.centre(v, x, y + 0.5F, z);
                float k = 1.0F - te / 0.5F;
                ScreenFx.flash(
                        0.55F * (1.0F - Show.clamp(te / 0.12F)) * reach,
                        Show.r(c.light()),
                        Show.g(c.light()),
                        Show.b(c.light()));
                ScreenFx.wave(te / 0.5F * 1.4F, 0.07F, 0.06F * k * reach, 0.5F * k * reach);
                ScreenFx.grade(0.4F * k * reach, 0.4F * k * reach, 0.0F, 0.0F);
            }
        }
    }

    private static void thunder(Death d, float t, Show.View v, Show.Pal c) {
        float s = d.scale;
        float x = v.x(d.at.x);
        float y = v.y(d.at.y);
        float z = v.z(d.at.z);
        float cloudY = y + d.height + 2.8F * s;
        float cloud = Show.easeOut(t / 0.45F) * (1.0F - Show.smooth((t - 2.1F) / 0.8F));
        float reach = screenReach(d);
        if (d.once(0)) {
            sound(d, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.35F, 0.55F);
        }

        if (reach > 0.0F) {
            Show.centre(v, x, y + d.height * 0.5F, z);
            ScreenFx.grade(0.35F * cloud * reach, 0.5F * cloud * reach, 0.0F, 0.0F);
        }

        float lit = 0.0F;
        int last = -1;

        for (int k = 0; k < STRIKES.length; k++) {
            float te = t - STRIKES[k];
            if (te >= 0.0F) {
                last = k;
            }

            if (te >= 0.0F && te < 0.22F) {
                lit = Math.max(lit, 1.0F - te / 0.22F);
            }
        }

        Random r = new Random(d.seed);
        List<Dot> dark = new ArrayList<>();
        List<Dot> glow = new ArrayList<>();

        for (int i = 0; i < 18; i++) {
            float a = r.nextFloat() * ((float) Math.PI * 2F)
                    + t * (0.15F + r.nextFloat() * 0.2F) * (float) (i % 2 == 0 ? 1 : -1);
            float rr = (0.3F + r.nextFloat() * 1.6F) * s;
            float px = x + Mth.cos((double) a) * rr;
            float pz = z + Mth.sin((double) a) * rr;
            float py = cloudY + (r.nextFloat() - 0.5F) * 0.7F * s;
            float half = (0.6F + r.nextFloat() * 0.6F) * s * (0.6F + 0.4F * cloud);
            dark.add(new Dot(px, py, pz, half, Show.alpha(0xFF15131D, 0.85F * cloud)));
            float inner = 0.5F + r.nextFloat() * 0.5F;
            if (lit > 0.0F) {
                glow.add(new Dot(px, py, pz, half * 0.9F, Show.alpha(c.main(), lit * 0.7F * cloud * inner)));
            }
        }

        dots(v, Fx.Tex.SMOKE.id, dark, 1.0F, 0.0F);
        dots(v, Show.ORB, glow, 0.0F, 1.0F);

        for (int k = 0; k < STRIKES.length; k++) {
            float te = t - STRIKES[k];
            if (!(te < 0.0F)) {
                Random br = new Random(d.seed + (long) k * 977L);
                float ha = br.nextFloat() * ((float) Math.PI * 2F);
                float hr = d.width * 0.3F;
                Vector3f top = new Vector3f(
                        x + (br.nextFloat() - 0.5F) * 1.6F * s,
                        cloudY - 0.3F * s,
                        z + (br.nextFloat() - 0.5F) * 1.6F * s);
                Vector3f hit = new Vector3f(
                        x + Mth.cos((double) ha) * hr,
                        y + (0.35F + br.nextFloat() * 0.6F) * d.height,
                        z + Mth.sin((double) ha) * hr);
                if (d.once(1 + k)) {
                    sound(d, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.2F + (float) k * 0.1F);
                    if (k % 2 == 0) {
                        sound(d, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4F, 1.4F);
                    }

                    jolt(d, 1.8F * s);
                    Vec3 at = v.world(hit);

                    for (int i = 0; i < 34; i++) {
                        Vec3 dv = (new Vec3(d.rnd.nextGaussian(), d.rnd.nextGaussian() + 0.6, d.rnd.nextGaussian()))
                                .normalize()
                                .scale((2.0 + d.rnd.nextDouble() * 4.0) * (double) s);
                        Fx.spawn()
                                .at(at)
                                .vel(dv.x, dv.y, dv.z)
                                .tex(Fx.Tex.SPARK)
                                .mode(Fx.Mode.STRETCH)
                                .size(0.05F * s, 0.0F)
                                .life(0.3F + d.rnd.nextFloat() * 0.35F)
                                .drag(2.2F)
                                .gravity(6.0F)
                                .colour(c.light(), c.main() & 0xFFFFFF);
                    }
                }

                if (!(te > 0.2F) && (int) (te * 70.0F) % 3 != 2) {
                    float a = 1.0F - te / 0.2F;
                    float[][] main = jag(br, top, hit, 5, 0.32F);
                    strip(v, main, 0.34F * s, 0.07F * s, c, a);

                    for (int f = 0; f < 2; f++) {
                        int from = 6 + br.nextInt(14);
                        Vector3f start = new Vector3f(main[0][from], main[1][from], main[2][from]);
                        Vector3f end = (new Vector3f(hit))
                                .sub(start)
                                .mul(0.45F + br.nextFloat() * 0.3F)
                                .add((br.nextFloat() - 0.5F) * 1.4F * s, -0.2F * s, (br.nextFloat() - 0.5F) * 1.4F * s)
                                .add(start);
                        strip(v, jag(br, start, end, 4, 0.4F), 0.2F * s, 0.04F * s, c, a * 0.8F);
                    }
                }
            }
        }

        if (last >= 0 && t < 2.2F) {
            Random ar = new Random(d.seed ^ (long) (t * 30.0F));
            float a = 1.0F - Show.smooth((t - 1.4F) / 0.8F);

            for (int i = 0; i < 3; i++) {
                float a1 = ar.nextFloat() * ((float) Math.PI * 2F);
                float a2 = a1 + (ar.nextFloat() - 0.5F) * 2.0F;
                float rr = d.width * 0.55F;
                Vector3f p1 = new Vector3f(
                        x + Mth.cos((double) a1) * rr, y + ar.nextFloat() * d.height, z + Mth.sin((double) a1) * rr);
                Vector3f p2 = new Vector3f(
                        x + Mth.cos((double) a2) * rr, y + ar.nextFloat() * d.height, z + Mth.sin((double) a2) * rr);
                strip(v, jag(ar, p1, p2, 3, 0.5F), 0.12F * s, 0.03F * s, c, a * 0.8F);
            }
        }

        float burn = last < 0 ? 0.0F : 1.0F - Show.smooth((t - 2.2F) / 0.8F);
        Show.flat(v, Show.DISC, x, y + 0.006F, z, s / 0.9F, 0.0F, 0xFF0D0B12, 0.5F * burn, 0.0F);
        if (lit > 0.0F) {
            Show.flat(v, Show.RING, x, y + 0.03F, z, 1.4F * s / 0.86F, t, c.main(), 0.2F * lit, lit);
            if (reach > 0.0F) {
                ScreenFx.flash(0.3F * lit * lit * reach, Show.r(c.light()), Show.g(c.light()), Show.b(c.light()));
            }
        }
    }

    private static float[][] jag(Random r, Vector3f a, Vector3f b, int levels, float rough) {
        int n = (1 << levels) + 1;
        float[] xs = new float[n];
        float[] ys = new float[n];
        float[] zs = new float[n];
        xs[0] = a.x;
        ys[0] = a.y;
        zs[0] = a.z;
        xs[n - 1] = b.x;
        ys[n - 1] = b.y;
        zs[n - 1] = b.z;
        float len = a.distance(b);

        for (int step = n - 1; step > 1; step /= 2) {
            float off = rough * len * (float) step / (float) (n - 1);

            for (int i = step / 2; i < n; i += step) {
                int i0 = i - step / 2;
                int i1 = i + step / 2;
                xs[i] = (xs[i0] + xs[i1]) / 2.0F + (r.nextFloat() - 0.5F) * off;
                ys[i] = (ys[i0] + ys[i1]) / 2.0F + (r.nextFloat() - 0.5F) * off * 0.5F;
                zs[i] = (zs[i0] + zs[i1]) / 2.0F + (r.nextFloat() - 0.5F) * off;
            }
        }

        return new float[][] {xs, ys, zs};
    }

    private static void strip(Show.View v, float[][] p, float glowW, float coreW, Show.Pal c, float a) {
        int n = p[0].length;
        float[] wg = new float[n];
        float[] wc = new float[n];
        int[] cg = new int[n];
        int[] cc = new int[n];
        Arrays.fill(wg, glowW);
        Arrays.fill(wc, coreW);
        Arrays.fill(cg, c.main());
        Arrays.fill(cc, c.light());
        Show.line(v, Show.TRAIL, p[0], p[1], p[2], wg, cg, 0.0F, 0.8F * a);
        Show.line(v, Show.TRAIL, p[0], p[1], p[2], wc, cc, 0.8F * a, a);
    }

    private static final class Death {
        final int style;
        final boolean studio;
        final boolean screen;
        final boolean shake;
        final boolean sound;
        final float volume;
        final Show.Pal pal;
        final long start;
        final Random rnd = new Random();
        final long seed;
        final Vec3 at;
        final float width;
        final float height;
        final float yaw;
        final float scale;
        int done;
        float last;
        float owed;
        final List<Flake> flakes;
        final List<Bit> bits;
        float[][] cuts;
        final Vector3f wind;
        final float lean;

        Death(int style, Vec3 at, float width, float height, float yaw, float scale, Show.Pal pal, boolean studio) {
            this.seed = this.rnd.nextLong();
            this.flakes = new ArrayList<>();
            this.bits = new ArrayList<>();
            this.style = style;
            this.pal = pal;
            this.studio = studio;
            this.start = KillFx.now(studio);
            CombatFx.KillEffects m = Modules.KILL;
            this.screen = m.screen.on();
            this.shake = m.shake.on();
            this.sound = m.sound.on();
            this.volume = m.volume.f();
            this.at = at;
            this.width = width;
            this.height = height;
            this.yaw = yaw;
            this.scale = scale;
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            this.wind = new Vector3f((float) Math.cos(a), 0.0F, (float) Math.sin(a));
            this.lean = (float) Math.toRadians((double) ((this.rnd.nextFloat() - 0.5F) * 16.0F));
        }

        boolean once(int moment) {
            if ((this.done & 1 << moment) != 0) {
                return false;
            } else {
                this.done |= 1 << moment;
                return true;
            }
        }
    }

    private static final class Flake {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        final float free;
        final float size;
        final float phase;
        final boolean ember;
        final Vector3f a;
        final Vector3f b;
        final Vector3f c;
        final Vector3f axis;
        float angle;
        float spin;

        Flake(float free, float size, boolean ember, float phase, Vector3f a, Vector3f b, Vector3f c, Vector3f axis) {
            this.free = free;
            this.size = size;
            this.ember = ember;
            this.phase = phase;
            this.a = a;
            this.b = b;
            this.c = c;
            this.axis = axis;
        }
    }

    private static final class Bit {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        float ox;
        float oy;
        float oz;
        float size;
        float aspect = 1.0F;
        float phase;
        float free;
        float angle;
        float spin;
        int colour;
        int bounces;
        Vector3f axis = new Vector3f(0.0F, 1.0F, 0.0F);
        boolean down;
    }

    private static record Dot(float x, float y, float z, float half, int colour) {}
}
