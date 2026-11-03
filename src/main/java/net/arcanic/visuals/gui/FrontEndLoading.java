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
import net.arcanic.visuals.hud.HudModule;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;

public final class FrontEndLoading {
    public static final float LEAVE_MS = 1500.0F;
    private static final float LAND_MS = 880.0F;
    private static long splashLeaving = -1L;
    private static long worldLeaving = -1L;
    private static boolean worldPending;
    private static float shownBar;
    private static long lastBar = -1L;

    private FrontEndLoading() {}

    public static void draw(GuiGraphics g, float progress, float opacity, String message, boolean ready) {
        draw(g, progress, opacity, message, ready, false);
    }

    public static void draw(
            GuiGraphics g, float progress, float opacity, String message, boolean ready, boolean resourceLoading) {
        scene(g, progress, opacity, message, ready, resourceLoading, -1.0F);
    }

    private static float smooth(float a, float b, float t) {
        float x = Math.max(0.0F, Math.min(1.0F, (t - a) / (b - a)));
        return x * x * (3.0F - 2.0F * x);
    }

    private static float since(long start) {
        return start < 0L ? -1.0F : (float) (System.nanoTime() - start) / 1000000.0F;
    }

    private static DynamicIsland island() {
        for (HudModule h : Modules.HUD) {
            if (h instanceof DynamicIsland d) {
                if (d.enabled()) {
                    return d;
                }
            }
        }

        return null;
    }

    public static void splashLeaving() {
        if (splashLeaving < 0L) {
            splashLeaving = System.nanoTime();
            DynamicIsland d = island();
            if (d != null && Minecraft.getInstance().level == null) {
                d.joined();
            }
        }
    }

    public static void splashGone() {
        splashLeaving = -1L;
    }

    public static float splashMs() {
        return Theme.animations ? since(splashLeaving) : -1.0F;
    }

    public static boolean splashReleased() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getOverlay() == null || splashMs() >= 880.0F;
    }

    public static void world() {
        worldPending = true;
        worldLeaving = -1L;
    }

    public static boolean holdsIsland() {
        if (!Theme.animations) {
            return false;
        } else if (worldPending) {
            return worldLeaving < 0L || since(worldLeaving) < 880.0F;
        } else {
            return splashLeaving >= 0L && since(splashLeaving) < 880.0F;
        }
    }

    public static void worldOutro(GuiGraphics g) {
        if (worldPending) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                if (mc.screen != null) {
                    if (!(mc.screen instanceof LevelLoadingScreen)) {
                        worldPending = false;
                    }

                } else {
                    if (worldLeaving < 0L) {
                        worldLeaving = System.nanoTime();
                    }

                    float ms = since(worldLeaving);
                    if (!(ms >= 1500.0F) && Theme.animations) {
                        g.nextStratum();
                        Px.end(g);

                        try {
                            scene(g, 1.0F, 1.0F, "Подготовка мира", true, false, ms);
                        } finally {
                            Px.begin(g);
                        }

                    } else {
                        worldPending = false;
                        worldLeaving = -1L;
                    }
                }
            } else {
                worldPending = false;
            }
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
        if (!(opacity <= 0.0F)) {
            progress = Math.max(0.0F, Math.min(1.0F, progress));
            g.nextStratum();
            Px.begin(g);
            float old = Px.fade;
            if (leaving >= 0.0F) {
                shownBar = Math.max(shownBar, progress);
            }

            boolean out = leaving >= 0.0F;
            float ui = out ? 1.0F - smooth(0.0F, 320.0F, leaving) : 1.0F;
            float bg = out ? 1.0F - smooth(250.0F, 1050.0F, leaving) : 1.0F;
            if (out) {
                progress = Math.max(progress, Math.min(1.0F, progress + leaving / 300.0F));
            }

            try {
                Px.fade = opacity * bg;
                Wallpaper.draw(g, opacity * bg, 0.0F, 1.04F, Wallpaper.reach() * 0.6F);
                int w = Px.width();
                int h = Px.height();
                int cx = w / 2;
                float u = Wallpaper.unit();
                g.nextStratum();
                g.fillGradient(0, h - Math.round(260.0F * u), w, h, 0, Px.am(0xA0000000, opacity * bg));
                Px.fade = opacity * ui;
                if (ready && ui > 0.0F) {
                    g.nextStratum();
                    g.pose().pushMatrix();
                    g.pose().translate(0.0F, -10.0F * (1.0F - ui));
                    Px.textCenter(
                            g,
                            Lang.tr(message),
                            cx,
                            h - Math.round(96.0F * u),
                            Px.Weight.MEDIUM,
                            Math.max(10, Math.min(15, Math.round(15.0F * u))),
                            0xE6FFFFFF);
                    g.pose().popMatrix();
                }

                if (ui > 0.0F) {
                    long now = System.nanoTime();
                    float dt = lastBar < 0L ? 0.0F : Math.min(0.1F, (float) (now - lastBar) / 1.0E9F);
                    lastBar = now;
                    if (progress < shownBar - 0.25F) {
                        shownBar = 0.0F;
                    }

                    shownBar = Theme.animations
                            ? shownBar
                                    + (Math.max(shownBar, progress) - shownBar)
                                            * (1.0F - (float) Math.exp((double) (-dt * 5.0F)))
                            : progress;
                    int bw = Math.min(Math.round(280.0F * u), w / 3);
                    int bh = Math.max(3, Math.round((float) h / 360.0F));
                    int by = h - Math.round(62.0F * u);
                    g.nextStratum();
                    Px.fade = opacity * ui;
                    Px.round(g, cx - bw / 2, by, bw, bh, bh / 2, 620756991);
                    int fw = Math.max(bh, Math.round((float) bw * shownBar));
                    Px.shadow(g, cx - bw / 2, by, fw, bh, 8, Px.a(Theme.accent(), 70));
                    Px.pill(g, cx - bw / 2, by, fw, bh, Theme.accent() | 0xFF000000, Theme.accent2() | 0xFF000000);
                }
            } finally {
                Px.fade = old;
                Px.end(g);
            }
        }
    }
}
