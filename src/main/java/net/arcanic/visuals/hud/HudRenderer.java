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

import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.ArcVisuals;
import net.arcanic.visuals.fx.TargetEsp;
import net.arcanic.visuals.gui.FrontEndLoading;
import net.arcanic.visuals.gui.HudEditorScreen;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Draws the HUD elements in screen pixels (in game, and inside the editor). */
public final class HudRenderer {
    private HudRenderer() {}

    public static void renderAll(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen instanceof HudEditorScreen || mc.player == null) return;
        TargetEsp.hud(g);
        Px.begin(g);
        try {
            ArcVisuals.hud(g);
            if (Modules.DAMAGE.enabled()) Modules.DAMAGE.render(g);
            // with the chat open every enabled element shows, so it can be moved
            boolean chat = HudDrag.chatOpen();
            for (HudModule h : Modules.HUD) {
                if (!h.enabled()) continue;
                boolean live = h.shown();
                if (live || chat) draw(g, h, chat && !live, false);
            }
            Toasts.render(g);
            if (HudDrag.chatOpen()) {
                double mx = mouse(mc.mouseHandler.xpos(), mc.getWindow().getScreenWidth(), Px.width()),
                        my = mouse(mc.mouseHandler.ypos(), mc.getWindow().getScreenHeight(), Px.height());
                HudDrag.frames(g, mx, my);
                HudDrag.guides(g);
            }
            FrontEndLoading.worldOutro(g);
        } finally {
            Px.end(g);
        }
    }

    private static double mouse(double pos, int window, int pixels) {
        return pos * pixels / Math.max(1, window);
    }

    public static void menuOverlay(GuiGraphics g) {
        Px.begin(g);
        try {
            for (HudModule h : Modules.HUD) if (h instanceof DynamicIsland && h.enabled()) draw(g, h, false, false);
            Toasts.render(g);
        } finally {
            Px.end(g);
        }
    }

    /** One element at its place (call inside pixel space); {@code ghost} draws it faded. */
    public static void draw(GuiGraphics g, HudModule h, boolean editing, boolean ghost) {
        int sw = Px.width(), sh = Px.height();
        h.prepare();
        int x = Math.max(0, Math.min(sw - h.w, h.x(sw))), y = Math.max(0, Math.min(sh - h.h, h.y(sh)));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        float old = Px.fade;
        if (ghost) Px.fade = old * .35f;
        try {
            h.render(g, editing);
        } catch (RuntimeException e) {
            /* a broken element must never break the HUD */
        } finally {
            Px.fade = old;
            g.pose().popMatrix();
        }
    }
}
