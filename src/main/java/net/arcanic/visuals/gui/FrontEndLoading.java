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
package net.arcanic.visuals.gui;

import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.hud.DynamicIsland;
import net.arcanic.visuals.render.BrandLogo;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;

/** Resource and world loading share one inexpensive scene. */
public final class FrontEndLoading {
    private FrontEndLoading() {}

    public static final float LEAVE_MS = 1500;

    private static final float LAND_MS = 880;
    private static long splashLeaving = -1, worldLeaving = -1;
    private static boolean worldPending;
    private static float shownBar;
    private static long lastBar = -1;

    public static void draw(GuiGraphics g, float progress, float opacity, String message, boolean ready) {
        draw(g, progress, opacity, message, ready, false);
    }

    public static void draw(
            GuiGraphics g, float progress, float opacity, String message, boolean ready, boolean resourceLoading) {
        scene(g, progress, opacity, message, ready, resourceLoading, -1);
    }

    private static float smooth(float a, float b, float t) {
        float x = Math.max(0, Math.min(1, (t - a) / (b - a)));
        return x * x * (3 - 2 * x);
    }

    private static float since(long start) {
        return start < 0 ? -1 : (System.nanoTime() - start) / 1e6f;
    }

    private static DynamicIsland island() {
        for (var h : Modules.HUD) if (h instanceof DynamicIsland d && d.enabled()) return d;
        return null;
    }

    public static void splashLeaving() {
        if (splashLeaving >= 0) return;
        splashLeaving = System.nanoTime();
        DynamicIsland d = island();
        if (d != null && Minecraft.getInstance().level == null) d.joined();
    }

    public static void splashGone() {
        splashLeaving = -1;
    }

    public static float splashMs() {
        return Theme.animations ? since(splashLeaving) : -1;
    }

    public static boolean splashReleased() {
        var mc = Minecraft.getInstance();
        return mc.getOverlay() == null || splashMs() >= LAND_MS;
    }

    public static void world() {
        worldPending = true;
        worldLeaving = -1;
    }

    public static boolean holdsIsland() {
        if (!Theme.animations) return false;
        if (worldPending) return worldLeaving < 0 || since(worldLeaving) < LAND_MS;
        return splashLeaving >= 0 && since(splashLeaving) < LAND_MS;
    }
    /** Over the first frames of the world (called from the HUD, in pixel space). */
    public static void worldOutro(GuiGraphics g) {
        if (!worldPending) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            worldPending = false;
            return;
        }
        if (mc.screen != null) {
            if (!(mc.screen instanceof LevelLoadingScreen)) worldPending = false;
            return;
        }
        if (worldLeaving < 0) worldLeaving = System.nanoTime();
        float ms = since(worldLeaving);
        if (ms >= LEAVE_MS || !Theme.animations) {
            worldPending = false;
            worldLeaving = -1;
            return;
        }
        g.nextStratum();
        Px.end(g);
        try {
            scene(g, 1, 1, "Подготовка мира", true, false, ms);
        } finally {
            Px.begin(g);
        }
    }

    public static void scene(
            GuiGraphics g,
            float progress,
            float opacity,
            String message,
            boolean ready,
            boolean resourceLoading,
            float leaving) {
        if (opacity <= 0) return;
        progress = Math.max(0, Math.min(1, progress));
        g.nextStratum();
        Px.begin(g);
        float old = Px.fade;
        if (leaving >= 0) shownBar = Math.max(shownBar, progress);
        boolean out = leaving >= 0;
        float ui = out ? 1 - smooth(0, 320, leaving) : 1, bg = out ? 1 - smooth(250, 1050, leaving) : 1;
        float logoA = ui;
        if (out) progress = Math.max(progress, Math.min(1, progress + leaving / 300f));
        try {
            Px.fade = opacity * bg;
            MenuBackdrop.draw(g, opacity * bg);
            int w = Px.width(),
                    h = Px.height(),
                    cx = w / 2,
                    cy = (int) (h * .42),
                    size = Math.max(48, Math.min(100, h / 9));
            g.nextStratum();
            Px.fade = opacity * logoA;
            BrandLogo.draw(g, cx - size / 2, cy - size / 2, size, opacity * logoA);
            Px.fade = opacity * ui;
            if (ready && ui > 0) {
                g.nextStratum();
                g.pose().pushMatrix();
                g.pose().translate(0, -10 * (1 - ui));
                Px.textCenter(
                        g,
                        Lang.tr(message),
                        cx,
                        (int) (h * .76),
                        Weight.MEDIUM,
                        Math.max(10, Math.min(14, h / 65)),
                        0xFFB8ACCC);
                g.pose().popMatrix();
            }
            if (ui > 0) {
                // the game reports progress in jumps: the bar glides towards it over time and never moves back
                long now = System.nanoTime();
                float dt = lastBar < 0 ? 0 : Math.min(.1f, (now - lastBar) / 1e9f);
                lastBar = now;
                if (progress < shownBar - .25f) shownBar = 0;
                shownBar = Theme.animations
                        ? shownBar + (Math.max(shownBar, progress) - shownBar) * (1 - (float) Math.exp(-dt * 5))
                        : progress;
                int bw = Math.min(340, w / 3), bh = Math.max(3, Math.round(h / 360f)), by = (int) (h * .81);
                g.nextStratum();
                Px.fade = opacity * ui;
                Px.round(g, cx - bw / 2, by, bw, bh, bh / 2, 0x24FFFFFF);
                int fw = Math.max(bh, Math.round(bw * shownBar));
                Px.shadow(g, cx - bw / 2, by, fw, bh, 8, Px.a(Theme.accent(), 70));
                Px.pill(g, cx - bw / 2, by, fw, bh, Theme.accent() | 0xFF000000, Theme.accent2() | 0xFF000000);
            }
        } finally {
            Px.fade = old;
            Px.end(g);
        }
    }
}
