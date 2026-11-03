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
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.fx.impact.ImpactDraw;
import net.arcanic.visuals.fx.impact.ScreenFx;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class Show {
    static final Identifier ORB = Identifier.fromNamespaceAndPath("arcanic", "textures/targetesp/orb.png");
    static final Identifier TRAIL = Identifier.fromNamespaceAndPath("arcanic", "textures/targetesp/trail.png");
    static final Identifier RING = Identifier.fromNamespaceAndPath("arcanic", "textures/totem/ring.png");
    static final Identifier DISC = Identifier.fromNamespaceAndPath("arcanic", "textures/totem/disc.png");
    static final Identifier BEAM = Identifier.fromNamespaceAndPath("arcanic", "textures/totem/beam.png");
    static final float RING_FRONT = 0.86F;

    private Show() {}

    static View view(PoseStack ps) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return new View(
                ps,
                ps.last().pose(),
                camera.position(),
                new Vector3f(camera.leftVector()),
                new Vector3f(camera.upVector()),
                (Vector3f) null);
    }

    static Pal theme() {
        int a = Theme.accent() | 0xFF000000;
        int b = Theme.accent2() | 0xFF000000;
        return new Pal(Fx.lerp(a, -1, 0.55F), a, b);
    }

    static void twice(Matrix4f m, Identifier tex, float shape, float glow, Consumer<Emit> quads) {
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

    static void quad(Emit e, View v, float x, float y, float z, float half, float rot, int c) {
        float cr = Mth.cos((double) rot) * half;
        float sr = Mth.sin((double) rot) * half;
        Vector3f l = v.left;
        Vector3f u = v.up;
        float[][] k =
                new float[][] {{cr - sr, sr + cr}, {cr + sr, sr - cr}, {-cr + sr, -sr - cr}, {-cr - sr, -sr + cr}};
        float[] tu = new float[] {0.0F, 0.0F, 1.0F, 1.0F};
        float[] tv = new float[] {0.0F, 1.0F, 1.0F, 0.0F};

        for (int i = 0; i < 4; ++i) {
            e.v(
                    x + l.x * k[i][0] + u.x * k[i][1],
                    y + l.y * k[i][0] + u.y * k[i][1],
                    z + l.z * k[i][0] + u.z * k[i][1],
                    tu[i],
                    tv[i],
                    c);
        }
    }

    static void sprite(
            View v, Identifier tex, float x, float y, float z, float half, float rot, int c, float shape, float glow) {
        if (!(half <= 0.001F)) {
            twice(v.pose, tex, shape, glow, (e) -> quad(e, v, x, y, z, half, rot, c));
        }
    }

    static void flat(
            View v, Identifier tex, float x, float y, float z, float half, float rot, int c, float shape, float glow) {
        if (!(half <= 0.001F)) {
            float cr = Mth.cos((double) rot) * half;
            float sr = Mth.sin((double) rot) * half;
            twice(v.pose, tex, shape, glow, (e) -> {
                e.v(x - cr + sr, y, z - sr - cr, 0.0F, 0.0F, c);
                e.v(x - cr - sr, y, z - sr + cr, 0.0F, 1.0F, c);
                e.v(x + cr - sr, y, z + sr + cr, 1.0F, 1.0F, c);
                e.v(x + cr + sr, y, z + sr - cr, 1.0F, 0.0F, c);
            });
        }
    }

    static void groundRing(
            View v, float x, float y, float z, float t, float from, float to, float radius, int c, float a) {
        float u = (t - from) / (to - from);
        if (!(u <= 0.0F) && !(u >= 1.0F)) {
            float k = (1.0F - u) * (1.0F - u);
            flat(v, RING, x, y + 0.03F, z, radius * easeOut(u) / 0.86F, 0.0F, c, 0.3F * k * a, k * a);
        }
    }

    static void line(
            View v,
            Identifier tex,
            float[] xs,
            float[] ys,
            float[] zs,
            float[] widths,
            int[] colours,
            float shape,
            float glow) {
        int n = xs.length;
        if (n >= 2) {
            float[] ox = new float[n];
            float[] oy = new float[n];
            float[] oz = new float[n];

            for (int i = 0; i < n; ++i) {
                int p = Math.max(0, i - 1);
                int q = Math.min(n - 1, i + 1);
                float tx = xs[q] - xs[p];
                float ty = ys[q] - ys[p];
                float tz = zs[q] - zs[p];
                Vector3f to = v.toward(xs[i], ys[i], zs[i]);
                float cx = ty * to.z - tz * to.y;
                float cy = tz * to.x - tx * to.z;
                float cz = tx * to.y - ty * to.x;
                float l = (float) Math.sqrt((double) (cx * cx + cy * cy + cz * cz));
                float w = widths[i] * 0.5F * taper(i, n) / Math.max(l, 1.0E-6F);
                ox[i] = cx * w;
                oy[i] = cy * w;
                oz[i] = cz * w;
            }

            twice(v.pose, tex, shape, glow, (e) -> {
                for (int i = 0; i + 1 < n; ++i) {
                    int c0 = alpha(colours[i], taper(i, n));
                    int c1 = alpha(colours[i + 1], taper(i + 1, n));
                    e.v(xs[i] - ox[i], ys[i] - oy[i], zs[i] - oz[i], 0.0F, 0.0F, c0);
                    e.v(xs[i] + ox[i], ys[i] + oy[i], zs[i] + oz[i], 0.0F, 1.0F, c0);
                    e.v(xs[i + 1] + ox[i + 1], ys[i + 1] + oy[i + 1], zs[i + 1] + oz[i + 1], 1.0F, 1.0F, c1);
                    e.v(xs[i + 1] - ox[i + 1], ys[i + 1] - oy[i + 1], zs[i + 1] - oz[i + 1], 1.0F, 0.0F, c1);
                }
            });
        }
    }

    static void line(View v, Identifier tex, float[] xs, float[] ys, float[] zs, float width, int c, float glow) {
        float[] w = new float[xs.length];
        int[] cs = new int[xs.length];
        Arrays.fill(w, width);
        Arrays.fill(cs, c);
        line(v, tex, xs, ys, zs, w, cs, 0.0F, glow);
    }

    static void groundLine(View v, float[] xs, float[] zs, float y, float width, int c, float a) {
        int n = xs.length;
        if (n >= 2 && !(a <= 0.003F)) {
            twice(v.pose, Fx.Tex.LINE_GLOW.id, 0.0F, a, (e) -> {
                for (int i = 0; i + 1 < n; ++i) {
                    float dx = xs[i + 1] - xs[i];
                    float dz = zs[i + 1] - zs[i];
                    float l = (float) Math.hypot((double) dx, (double) dz) + 1.0E-5F;
                    float w0 = width * 0.5F * taper(i, n);
                    float w1 = width * 0.5F * taper(i + 1, n);
                    float sx = -dz / l;
                    float sz = dx / l;
                    int c0 = alpha(c, taper(i, n));
                    int c1 = alpha(c, taper(i + 1, n));
                    e.v(xs[i] - sx * w0, y, zs[i] - sz * w0, 0.0F, 0.0F, c0);
                    e.v(xs[i] + sx * w0, y, zs[i] + sz * w0, 0.0F, 1.0F, c0);
                    e.v(xs[i + 1] + sx * w1, y, zs[i + 1] + sz * w1, 1.0F, 1.0F, c1);
                    e.v(xs[i + 1] - sx * w1, y, zs[i + 1] - sz * w1, 1.0F, 0.0F, c1);
                }
            });
        }
    }

    static float taper(int i, int n) {
        if (n < 3) {
            return 1.0F;
        } else {
            float f = (float) i / (float) (n - 1);
            return smooth(Math.min(f, 1.0F - f) / 0.2F) * 0.85F + 0.15F * (float) (i != 0 && i != n - 1 ? 1 : 0);
        }
    }

    static void triangles(View v, List<Tri> tris, boolean added) {
        if (!tris.isEmpty()) {
            BufferBuilder b = Tesselator.getInstance().begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            for (Tri t : tris) {
                b.addVertex(v.pose, t.a.x, t.a.y, t.a.z).setColor(t.colour);
                b.addVertex(v.pose, t.b.x, t.b.y, t.b.z).setColor(t.colour);
                b.addVertex(v.pose, t.c.x, t.c.y, t.c.z).setColor(t.colour);
            }

            ImpactDraw.draw(ImpactDraw.type(Mode.TRIANGLES, true, false, added), b);
        }
    }

    static void centre(View v, float x, float y, float z) {
        Vector4f q = new Vector4f(x, y, z, 1.0F);
        v.pose.transform(q);
        ImpactDraw.PROJECTION.transform(q);
        ScreenFx.project(q);
    }

    static float reach(Vec3 at, float far) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) {
            return 0.0F;
        } else {
            double d = me.position().distanceTo(at);
            return d > (double) far ? 0.0F : (float) (1.0 - d / (double) far);
        }
    }

    static void play(Vec3 at, SoundEvent s, float vol, float pitch) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && s != null && !(vol <= 0.0F)) {
            level.playLocalSound(at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch, false);
        }
    }

    static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    static float smooth(float t) {
        t = clamp(t);
        return t * t * (3.0F - 2.0F * t);
    }

    static float easeOut(float t) {
        t = 1.0F - clamp(t);
        return 1.0F - t * t * t;
    }

    static float easeIn(float t) {
        t = clamp(t);
        return t * t * t;
    }

    static float backOut(float t) {
        t = clamp(t) - 1.0F;
        return 1.0F + t * t * (2.2F * t + 1.2F);
    }

    static int alpha(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * (float) (c >>> 24)) << 24 | c & 0xFFFFFF;
    }

    static int whiter(int c, float k) {
        return c & 0xFF000000 | Fx.lerp(c | 0xFF000000, -1, k) & 0xFFFFFF;
    }

    static float r(int c) {
        return (float) (c >> 16 & 255) / 255.0F;
    }

    static float g(int c) {
        return (float) (c >> 8 & 255) / 255.0F;
    }

    static float b(int c) {
        return (float) (c & 255) / 255.0F;
    }

    static record View(PoseStack ps, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vector3f forward) {
        Vector3f toward(float x, float y, float z) {
            return this.forward != null ? this.forward : new Vector3f(x, y, z);
        }

        float x(double w) {
            return (float) (w - this.cam.x);
        }

        float y(double w) {
            return (float) (w - this.cam.y);
        }

        float z(double w) {
            return (float) (w - this.cam.z);
        }

        Vector3f at(Vec3 w) {
            return new Vector3f(this.x(w.x), this.y(w.y), this.z(w.z));
        }

        Vec3 world(Vector3f p) {
            return new Vec3(this.cam.x + (double) p.x, this.cam.y + (double) p.y, this.cam.z + (double) p.z);
        }
    }

    static record Pal(int light, int main, int deep) {}

    static final class Emit {
        final BufferBuilder b;
        final Matrix4f m;
        final float k;

        Emit(Matrix4f m, float k) {
            this.b = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            this.m = m;
            this.k = k;
        }

        void v(float x, float y, float z, float u, float v, int c) {
            this.b.addVertex(this.m, x, y, z).setUv(u, v).setColor(Show.alpha(c, this.k));
        }
    }

    static record Tri(Vector3f a, Vector3f b, Vector3f c, int colour) {}
}
