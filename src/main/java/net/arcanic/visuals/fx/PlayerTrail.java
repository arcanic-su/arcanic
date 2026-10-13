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
import net.minecraft.world.phys.Vec3;

/**
 * Trail module
 *
 * Leaves a translucent band behind the player.
 */
public final class PlayerTrail extends ArcVisuals.Colored {
    private final Setting.Num duration = add(new Setting.Num("duration", "Длина", 400, 100, 2000, 50, " мс"));
    private final Setting.Num height = add(new Setting.Num("height", "Высота", 1, .1, 2.5, .05, "×"));
    private final Setting.Bool fill = add(new Setting.Bool("fill", "Заливка", true));
    private final Setting.Num fillAlpha = add(new Setting.Num("fill_alpha", "Плотность заливки", .5, .1, 1, .05, ""))
            .showIf(fill::on);
    private final Setting.Bool lines = add(new Setting.Bool("lines", "Кромки", true));
    private final Setting.Num lineWidth = add(new Setting.Num("line_width", "Толщина кромок", 2, 1, 5, .5, " px"))
            .showIf(lines::on);
    private final Setting.Bool firstPerson = add(new Setting.Bool("first_person", "Видно от 1-го лица", false));

    public PlayerTrail() {
        super("player_trail", "След", "Светящаяся лента в твой рост тянется за тобой, как в Arcanic", Category.EFFECTS);
    }

    private record Pt(Vec3 pos, long t) {}

    private final ArrayDeque<Pt> points = new ArrayDeque<>();

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static int alpha(int c, float a) {
        return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (c & 0xFFFFFF);
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        var p = mc().player;
        boolean fp = mc().options.getCameraType().isFirstPerson();
        if (fp && !firstPerson.on()) {
            points.clear();
            return;
        }
        long now = System.currentTimeMillis();
        long life = Math.round(duration.get());
        Vec3 at = p.getPosition(pt);
        while (!points.isEmpty() && now - points.peekFirst().t > life) points.pollFirst();
        if (points.isEmpty() || points.peekLast().pos.distanceToSqr(at) > .0009) points.addLast(new Pt(at, now));
        if (points.size() < 2) return;

        List<Pt> pts = new ArrayList<>(points);
        int n = pts.size();
        float top = p.getBbHeight() * height.f();
        PoseStack.Pose pose = ctx.matrices().last();
        double fov = Math.toRadians(Projection.fov());
        float perPx =
                (float) (2 * Math.tan(fov / 2) / Math.max(1, mc().getWindow().getHeight()));

        float[] a = new float[n];
        int[] col = new int[n];
        for (int i = 0; i < n; i++) {
            // fades with age and towards the old end, ramped by index
            float age = 1 - (now - pts.get(i).t) / (float) life;
            float idx = (i + 1) / (float) n;
            a[i] = smooth(Math.min(age * 1.4f, 1)) * smooth(Math.min(idx * 1.25f, 1));
            col[i] = color(i / (float) Math.max(1, n - 1)) | 0xFF000000;
        }
        VertexConsumer vc = ctx.consumers().getBuffer(Fx.soft(false));
        if (fill.on()) {
            float k = fillAlpha.f();
            for (int i = 0; i < n - 1; i++) {
                Vec3 r0 = pts.get(i).pos.subtract(cam), r1 = pts.get(i + 1).pos.subtract(cam);
                int c0 = alpha(col[i], a[i] * k), c1 = alpha(col[i + 1], a[i + 1] * k);
                int t0 = alpha(col[i], a[i] * k * .55f), t1 = alpha(col[i + 1], a[i + 1] * k * .55f);
                // both windings, so the curtain is seen from either side
                quad(vc, pose, r0, r1, top, c0, c1, t0, t1);
                quad(vc, pose, r1, r0, top, c1, c0, t1, t0);
            }
        }
        if (lines.on()) {
            edge(vc, pose, cam, pts, .02, col, a, perPx);
            edge(vc, pose, cam, pts, top, col, a, perPx);
        }
    }

    private static void quad(
            VertexConsumer vc, PoseStack.Pose pose, Vec3 r0, Vec3 r1, float top, int b0, int b1, int t0, int t1) {
        Fx.v(vc, pose, (float) r0.x, (float) r0.y + .02f, (float) r0.z, 0, .5f, b0);
        Fx.v(vc, pose, (float) r0.x, (float) r0.y + top, (float) r0.z, 0, .5f, t0);
        Fx.v(vc, pose, (float) r1.x, (float) r1.y + top, (float) r1.z, 1, .5f, t1);
        Fx.v(vc, pose, (float) r1.x, (float) r1.y + .02f, (float) r1.z, 1, .5f, b1);
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
        Vec3[] rel = new Vec3[n], off = new Vec3[n];
        for (int i = 0; i < n; i++) rel[i] = pts.get(i).pos.add(0, y, 0).subtract(cam);
        float px = lineWidth.f() + .5f;
        for (int i = 0; i < n; i++) {
            Vec3 dir = rel[Math.min(n - 1, i + 1)].subtract(rel[Math.max(0, i - 1)]);
            Vec3 side = dir.cross(rel[i]);
            if (side.lengthSqr() < 1e-12) side = i > 0 && off[i - 1] != null ? off[i - 1] : new Vec3(0, 1, 0);
            off[i] = side.normalize().scale(Math.max(.002, rel[i].length() * perPx * px / 2));
        }
        for (int i = 0; i < n - 1; i++) {
            Vec3 p0 = rel[i], p1 = rel[i + 1], s0 = off[i], s1 = off[i + 1];
            int c0 = alpha(col[i], a[i]), c1 = alpha(col[i + 1], a[i + 1]);
            Fx.v(vc, pose, (float) (p0.x + s0.x), (float) (p0.y + s0.y), (float) (p0.z + s0.z), 0, 0, c0);
            Fx.v(vc, pose, (float) (p0.x - s0.x), (float) (p0.y - s0.y), (float) (p0.z - s0.z), 0, 1, c0);
            Fx.v(vc, pose, (float) (p1.x - s1.x), (float) (p1.y - s1.y), (float) (p1.z - s1.z), 1, 1, c1);
            Fx.v(vc, pose, (float) (p1.x + s1.x), (float) (p1.y + s1.y), (float) (p1.z + s1.z), 1, 0, c1);
        }
    }

    @Override
    protected void onDisable() {
        points.clear();
    }
}
