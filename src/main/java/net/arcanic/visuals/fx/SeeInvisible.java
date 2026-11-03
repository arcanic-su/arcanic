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

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.helper.HolyEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** HolyWorld only: an invisible player with no armour on gets a soft ring on the floor and a breathing light. */
public final class SeeInvisible extends Module {
    private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", false, 0xFFBE5AFF));
    private final Setting.Num opacity =
            this.add(new Setting.Num("opacity", "Прозрачность", 90.0, 10.0, 100.0, 5.0, "%"));
    private static final float RADIUS = 0.6F;
    private static final float HEIGHT = 2.0F;
    private static final int SEGMENTS = 60;
    private final Map<Integer, float[]> shown = new HashMap<>();
    private long lastNanos = System.nanoTime();
    private float time;

    public SeeInvisible() {
        super(
                "see_invisible",
                "See Invisible",
                "Помечает невидимых игроков без брони кругом на полу — только на HolyWorld",
                Category.HOLYWORLD,
                false);
    }

    private static boolean marked(Player p, Player me) {
        if (p == me || !p.isAlive() || !p.isInvisible() || p.distanceToSqr(me) > 10000.0) return false;
        for (EquipmentSlot s :
                new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!p.getItemBySlot(s).isEmpty()) return false;
        }
        return true;
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.enabled() || !HolyEvents.onHolyWorld() || mc.player == null || mc.level == null) {
            this.shown.clear();
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - this.lastNanos) / 1.0E9F, 0.1F);
        this.lastNanos = now;
        this.time += dt;
        float ease = 1.0F - (float) Math.exp(-dt * 8.0F);
        Set<Integer> live = new HashSet<>();
        double fov = Math.toRadians(Projection.fov());
        TrapZone.Frame f = new TrapZone.Frame(
                ctx.matrices().last(),
                cam,
                (float) (2.0 * Math.tan(fov / 2.0) / Math.max(1, mc.getWindow().getHeight())),
                pt,
                this.time);
        VertexConsumer glow = ctx.consumers().getBuffer(Fx.ribbon(Fx.Tex.LINE_GLOW, false));
        VertexConsumer edge = ctx.consumers().getBuffer(Fx.ribbon(Fx.Tex.LINE_AA, false));
        int c = this.colour.argb() | 0xFF000000;
        float alpha = ((Double) this.opacity.get()).floatValue() / 100.0F;
        for (Player p : mc.level.players()) {
            boolean on = marked(p, mc.player);
            float[] k = this.shown.get(p.getId());
            if (k == null) {
                if (!on) continue;
                k = new float[] {0.0F};
                this.shown.put(p.getId(), k);
            }
            k[0] += ((on ? 1.0F : 0.0F) - k[0]) * ease;
            if (k[0] < 0.01F && !on) continue;
            live.add(p.getId());
            Vec3 at = p.getPosition(pt).add(0.0, 0.02, 0.0);
            this.mark(glow, edge, f, at, c, k[0] * alpha, this.time + (p.getId() % 100) * 0.37F);
        }
        this.shown.keySet().retainAll(live);
    }

    private void mark(VertexConsumer glow, VertexConsumer edge, TrapZone.Frame f, Vec3 at, int c, float a, float t) {
        float beat = 0.85F + 0.15F * (0.5F + 0.5F * (float) Math.sin(t * 2.2));
        float wall = HEIGHT * beat;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Math.PI * 2.0 / SEGMENTS;
            double a1 = (i + 1) * Math.PI * 2.0 / SEGMENTS;
            double x0 = at.x + Math.cos(a0) * RADIUS;
            double z0 = at.z + Math.sin(a0) * RADIUS;
            double x1 = at.x + Math.cos(a1) * RADIUS;
            double z1 = at.z + Math.sin(a1) * RADIUS;
            float w0 = (float) Math.max(0.12, Math.sin(a0 * 3.0 + t * 2.5));
            float w1 = (float) Math.max(0.12, Math.sin(a1 * 3.0 + t * 2.5));
            int b0 = TrapZone.alpha(c, 0.43F * a * w0);
            int b1 = TrapZone.alpha(c, 0.43F * a * w1);
            int top = TrapZone.alpha(c, 0.0F);
            TrapZone.face(
                    glow, f, x0, at.y, z0, x1, at.y, z1, x1, at.y + wall, z1, x0, at.y + wall, z0, b0, b1, top, top);
        }
        TrapZone.ring(edge, f, at, RADIUS, 3.0F, TrapZone.alpha(Fx.lerp(c, -1, 0.3F), 0.9F * a));
        TrapZone.ring(glow, f, at, RADIUS, 14.0F, TrapZone.alpha(c, 0.35F * a * beat));
    }

    protected void onDisable() {
        this.shown.clear();
    }
}
