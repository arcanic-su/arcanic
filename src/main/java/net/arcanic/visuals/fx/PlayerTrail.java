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
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class PlayerTrail extends ArcVisuals.Colored {
    private final Setting.Num duration =
            this.add(new Setting.Num("duration", "Длина", 400.0, 100.0, 2000.0, 50.0, " мс"));
    private final Setting.Num height = this.add(new Setting.Num("height", "Высота", 1.0, 0.1, 2.5, 0.05, "×"));
    private final Setting.Bool fill = this.add(new Setting.Bool("fill", "Заливка", true));
    private final Setting.Num fillAlpha;
    private final Setting.Bool lines;
    private final Setting.Num lineWidth;
    private final Setting.Bool firstPerson;
    private final ArrayDeque<Pt> points;

    public PlayerTrail() {
        super("player_trail", "След", "Светящаяся лента в твой рост тянется за тобой, как в Arcanic", Category.EFFECTS);
        Setting.Num var10001 = this.add(new Setting.Num("fill_alpha", "Плотность заливки", 0.5, 0.1, 1.0, 0.05, ""));
        Setting.Bool var10002 = this.fill;
        this.fillAlpha = (Setting.Num) var10001.showIf(var10002::on);
        this.lines = this.add(new Setting.Bool("lines", "Кромки", true));
        var10001 = this.add(new Setting.Num("line_width", "Толщина кромок", 2.0, 1.0, 5.0, 0.5, " px"));
        var10002 = this.lines;
        this.lineWidth = (Setting.Num) var10001.showIf(var10002::on);
        this.firstPerson = this.add(new Setting.Bool("first_person", "Видно от 1-го лица", false));
        this.points = new ArrayDeque<>();
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static float smooth(float x) {
        x = Math.max(0.0F, Math.min(1.0F, x));
        return x * x * (3.0F - 2.0F * x);
    }

    private static int alpha(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | c & 0xFFFFFF;
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        LocalPlayer p = mc().player;
        boolean fp = mc().options.getCameraType().isFirstPerson();
        if (fp && !this.firstPerson.on()) {
            this.points.clear();
        } else {
            long now = System.currentTimeMillis();
            long life = Math.round((Double) this.duration.get());
            Vec3 at = p.getPosition(pt);

            while (!this.points.isEmpty() && now - ((Pt) this.points.peekFirst()).t > life) {
                this.points.pollFirst();
            }

            if (this.points.isEmpty() || ((Pt) this.points.peekLast()).pos.distanceToSqr(at) > 9.0E-4) {
                this.points.addLast(new Pt(at, now));
            }

            if (this.points.size() >= 2) {
                List<Pt> pts = new ArrayList(this.points);
                int n = pts.size();
                float top = p.getBbHeight() * this.height.f();
                PoseStack.Pose pose = ctx.matrices().last();
                double fov = Math.toRadians((double) Projection.fov());
                float perPx = (float) (2.0
                        * Math.tan(fov / 2.0)
                        / (double) Math.max(1, mc().getWindow().getHeight()));
                float[] a = new float[n];
                int[] col = new int[n];

                for (int i = 0; i < n; ++i) {
                    float age = 1.0F - (float) (now - ((Pt) pts.get(i)).t) / (float) life;
                    float idx = (float) (i + 1) / (float) n;
                    a[i] = smooth(Math.min(age * 1.4F, 1.0F)) * smooth(Math.min(idx * 1.25F, 1.0F));
                    col[i] = this.color((float) i / (float) Math.max(1, n - 1)) | 0xFF000000;
                }

                VertexConsumer vc = ctx.consumers().getBuffer(Fx.soft(false));
                if (this.fill.on()) {
                    float k = this.fillAlpha.f();

                    for (int i = 0; i < n - 1; ++i) {
                        Vec3 r0 = ((Pt) pts.get(i)).pos.subtract(cam);
                        Vec3 r1 = ((Pt) pts.get(i + 1)).pos.subtract(cam);
                        int c0 = alpha(col[i], a[i] * k);
                        int c1 = alpha(col[i + 1], a[i + 1] * k);
                        int t0 = alpha(col[i], a[i] * k * 0.55F);
                        int t1 = alpha(col[i + 1], a[i + 1] * k * 0.55F);
                        quad(vc, pose, r0, r1, top, c0, c1, t0, t1);
                        quad(vc, pose, r1, r0, top, c1, c0, t1, t0);
                    }
                }

                if (this.lines.on()) {
                    this.edge(vc, pose, cam, pts, 0.02, col, a, perPx);
                    this.edge(vc, pose, cam, pts, (double) top, col, a, perPx);
                }
            }
        }
    }

    private static void quad(
            VertexConsumer vc, PoseStack.Pose pose, Vec3 r0, Vec3 r1, float top, int b0, int b1, int t0, int t1) {
        Fx.v(vc, pose, (float) r0.x, (float) r0.y + 0.02F, (float) r0.z, 0.0F, 0.5F, b0);
        Fx.v(vc, pose, (float) r0.x, (float) r0.y + top, (float) r0.z, 0.0F, 0.5F, t0);
        Fx.v(vc, pose, (float) r1.x, (float) r1.y + top, (float) r1.z, 1.0F, 0.5F, t1);
        Fx.v(vc, pose, (float) r1.x, (float) r1.y + 0.02F, (float) r1.z, 1.0F, 0.5F, b1);
    }

    private void edge(
            VertexConsumer vc,
            PoseStack.Pose pose,
            Vec3 cam,
            List<Pt> pts,
            double y,
            int[] col,
            float[] a,
            float perPx) {
        int n = pts.size();
        Vec3[] rel = new Vec3[n];
        Vec3[] off = new Vec3[n];

        for (int i = 0; i < n; ++i) {
            rel[i] = ((Pt) pts.get(i)).pos.add(0.0, y, 0.0).subtract(cam);
        }

        float px = this.lineWidth.f() + 0.5F;

        for (int i = 0; i < n; ++i) {
            Vec3 dir = rel[Math.min(n - 1, i + 1)].subtract(rel[Math.max(0, i - 1)]);
            Vec3 side = dir.cross(rel[i]);
            if (side.lengthSqr() < 1.0E-12) {
                side = i > 0 && off[i - 1] != null ? off[i - 1] : new Vec3(0.0, 1.0, 0.0);
            }

            off[i] = side.normalize().scale(Math.max(0.002, rel[i].length() * (double) perPx * (double) px / 2.0));
        }

        for (int i = 0; i < n - 1; ++i) {
            Vec3 p0 = rel[i];
            Vec3 p1 = rel[i + 1];
            Vec3 s0 = off[i];
            Vec3 s1 = off[i + 1];
            int c0 = alpha(col[i], a[i]);
            int c1 = alpha(col[i + 1], a[i + 1]);
            Fx.v(vc, pose, (float) (p0.x + s0.x), (float) (p0.y + s0.y), (float) (p0.z + s0.z), 0.0F, 0.0F, c0);
            Fx.v(vc, pose, (float) (p0.x - s0.x), (float) (p0.y - s0.y), (float) (p0.z - s0.z), 0.0F, 1.0F, c0);
            Fx.v(vc, pose, (float) (p1.x - s1.x), (float) (p1.y - s1.y), (float) (p1.z - s1.z), 1.0F, 1.0F, c1);
            Fx.v(vc, pose, (float) (p1.x + s1.x), (float) (p1.y + s1.y), (float) (p1.z + s1.z), 1.0F, 0.0F, c1);
        }
    }

    protected void onDisable() {
        this.points.clear();
    }

    private static record Pt(Vec3 pos, long t) {}
}
