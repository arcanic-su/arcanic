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
package net.arcanic.visuals.hud;

import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

public final class HudDrag {
    private static HudModule pressed;
    private static HudModule dragging;
    private static double pressX;
    private static double pressY;
    private static double grabX;
    private static double grabY;
    private static boolean snapX;
    private static boolean snapY;

    private HudDrag() {}

    public static boolean chatOpen() {
        return Minecraft.getInstance().screen instanceof ChatScreen;
    }

    public static HudModule at(double mx, double my, boolean all) {
        int sw = Px.width();
        int sh = Px.height();

        for (int i = Modules.HUD.size() - 1; i >= 0; --i) {
            HudModule h = (HudModule) Modules.HUD.get(i);
            if (all || h.enabled()) {
                int x = Math.max(0, Math.min(sw - h.w, h.x(sw)));
                int y = Math.max(0, Math.min(sh - h.h, h.y(sh)));
                if (mx >= (double) (x - 3)
                        && mx < (double) (x + h.w + 3)
                        && my >= (double) (y - 3)
                        && my < (double) (y + h.h + 3)) {
                    return h;
                }
            }
        }

        return null;
    }

    public static HudModule dragging() {
        return dragging;
    }

    public static void press(HudModule h, double mx, double my) {
        pressed = h;
        pressX = mx;
        pressY = my;
    }

    public static boolean holding() {
        return pressed != null;
    }

    public static void move(double mx, double my) {
        if (dragging == null) {
            if (pressed == null || Math.hypot(mx - pressX, my - pressY) < 4.0) {
                return;
            }

            dragging = pressed;
            int sw = Px.width();
            int sh = Px.height();
            grabX = pressX - (double) Math.max(0, Math.min(sw - dragging.w, dragging.x(sw)));
            grabY = pressY - (double) Math.max(0, Math.min(sh - dragging.h, dragging.y(sh)));
        }

        int sw = Px.width();
        int sh = Px.height();
        int snap = 8;
        int w = dragging.w;
        int h = dragging.h;
        double x = mx - grabX;
        double y = my - grabY;
        snapX = Math.abs(x + (double) w / 2.0 - (double) sw / 2.0) < (double) snap;
        if (snapX) {
            x = (double) sw / 2.0 - (double) w / 2.0;
        }

        snapY = Math.abs(y + (double) h / 2.0 - (double) sh / 2.0) < (double) snap;
        if (snapY) {
            y = (double) sh / 2.0 - (double) h / 2.0;
        }

        if (Math.abs(x - 6.0) < (double) snap) {
            x = 6.0;
        }

        if (Math.abs(x + (double) w - (double) sw + 6.0) < (double) snap) {
            x = (double) (sw - w - 6);
        }

        if (Math.abs(y - 6.0) < (double) snap) {
            y = 6.0;
        }

        if (Math.abs(y + (double) h - (double) sh + 6.0) < (double) snap) {
            y = (double) (sh - h - 6);
        }

        dragging.fx = Math.max(0.0, Math.min((double) (sw - w), x)) / (double) sw;
        dragging.fy = Math.max(0.0, Math.min((double) (sh - h), y)) / (double) sh;
    }

    public static HudModule release() {
        HudModule clicked = dragging == null ? pressed : null;
        if (dragging != null) {
            Config.dirty();
        }

        dragging = null;
        pressed = null;
        snapY = false;
        snapX = false;
        return clicked;
    }

    public static void guides(GuiGraphics g) {
        if (dragging != null) {
            int sw = Px.width();
            int sh = Px.height();
            if (snapX) {
                Px.rect(g, sw / 2, 0, 1, sh, Px.a(Theme.accent(), 220));
            }

            if (snapY) {
                Px.rect(g, 0, sh / 2, sw, 1, Px.a(Theme.accent(), 220));
            }
        }
    }

    public static void frames(GuiGraphics g, double mx, double my) {
        HudModule hover = dragging != null ? dragging : at(mx, my, false);
        if (hover != null) {
            int sw = Px.width();
            int sh = Px.height();
            int x = Math.max(0, Math.min(sw - hover.w, hover.x(sw)));
            int y = Math.max(0, Math.min(sh - hover.h, hover.y(sh)));
            Px.outline(g, x - 3, y - 3, hover.w + 6, hover.h + 6, 12, hover == dragging ? Theme.accent2() : 1358954495);
        }
    }
}
