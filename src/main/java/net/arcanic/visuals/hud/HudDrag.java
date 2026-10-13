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

/** Moving HUD elements with the mouse, in the HUD editor and with the chat open. Coordinates are screen pixels. */
public final class HudDrag {
    private HudDrag() {}

    private static HudModule pressed, dragging;
    private static double pressX, pressY, grabX, grabY;
    private static boolean snapX, snapY;

    public static boolean chatOpen() {
        return Minecraft.getInstance().screen instanceof ChatScreen;
    }

    /** The element under the cursor; with {@code all} off only the ones that are switched on and visible. */
    public static HudModule at(double mx, double my, boolean all) {
        int sw = Px.width(), sh = Px.height();
        for (int i = Modules.HUD.size() - 1; i >= 0; i--) {
            HudModule h = Modules.HUD.get(i);
            if (!all && !h.enabled()) continue;
            int x = Math.max(0, Math.min(sw - h.w, h.x(sw))), y = Math.max(0, Math.min(sh - h.h, h.y(sh)));
            if (mx >= x - 3 && mx < x + h.w + 3 && my >= y - 3 && my < y + h.h + 3) return h;
        }
        return null;
    }

    public static HudModule dragging() {
        return dragging;
    }

    /** A press on an element: it only starts moving once the mouse does, so a plain click stays a click. */
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
            if (pressed == null || Math.hypot(mx - pressX, my - pressY) < 4) return;
            dragging = pressed;
            if (dragging instanceof DynamicIsland island) island.detach(Px.width());
            int sw = Px.width(), sh = Px.height();
            grabX = pressX - Math.max(0, Math.min(sw - dragging.w, dragging.x(sw)));
            grabY = pressY - Math.max(0, Math.min(sh - dragging.h, dragging.y(sh)));
        }
        int sw = Px.width(), sh = Px.height(), snap = 8, w = dragging.w, h = dragging.h;
        double x = mx - grabX, y = my - grabY;
        snapX = Math.abs(x + w / 2.0 - sw / 2.0) < snap;
        if (snapX) x = sw / 2.0 - w / 2.0;
        snapY = Math.abs(y + h / 2.0 - sh / 2.0) < snap;
        if (snapY) y = sh / 2.0 - h / 2.0;
        if (Math.abs(x - 6) < snap) x = 6;
        if (Math.abs(x + w - sw + 6) < snap) x = sw - w - 6;
        if (Math.abs(y - 6) < snap) y = 6;
        if (Math.abs(y + h - sh + 6) < snap) y = sh - h - 6;
        dragging.fx = Math.max(0, Math.min(sw - w, x)) / sw;
        dragging.fy = Math.max(0, Math.min(sh - h, y)) / sh;
    }

    /** Lets go. Returns the element that was pressed without being moved (a click), or null. */
    public static HudModule release() {
        HudModule clicked = dragging == null ? pressed : null;
        if (dragging != null) Config.dirty();
        pressed = dragging = null;
        snapX = snapY = false;
        return clicked;
    }

    /** The centre lines an element snaps to, while one is being moved. */
    public static void guides(GuiGraphics g) {
        if (dragging == null) return;
        int sw = Px.width(), sh = Px.height();
        if (snapX) Px.rect(g, sw / 2, 0, 1, sh, Px.a(Theme.accent(), 220));
        if (snapY) Px.rect(g, 0, sh / 2, sw, 1, Px.a(Theme.accent(), 220));
    }

    /** With the chat open: a light frame around the element under the cursor, an accent one around the moved one. */
    public static void frames(GuiGraphics g, double mx, double my) {
        HudModule hover = dragging != null ? dragging : at(mx, my, false);
        if (hover == null) return;
        int sw = Px.width(), sh = Px.height();
        int x = Math.max(0, Math.min(sw - hover.w, hover.x(sw))), y = Math.max(0, Math.min(sh - hover.h, hover.y(sh)));
        Px.outline(g, x - 3, y - 3, hover.w + 6, hover.h + 6, 12, hover == dragging ? Theme.accent2() : 0x50FFFFFF);
    }
}
