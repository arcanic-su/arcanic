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
import net.arcanic.visuals.fx.WorldPlus;
import net.arcanic.visuals.gui.FrontEndLoading;
import net.arcanic.visuals.gui.HudEditorScreen;
import net.arcanic.visuals.helper.Pings;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class HudRenderer {
    private HudRenderer() {}

    public static void renderAll(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.hideGui && !(mc.screen instanceof HudEditorScreen) && mc.player != null) {
            Px.begin(g);

            try {
                Survival.screen(g);
                ArcVisuals.hud(g);
                WorldPlus.hud(g);
                if (Modules.DAMAGE.enabled()) {
                    Modules.DAMAGE.render(g);
                }
                if (Modules.LYRICS.enabled()) {
                    Modules.LYRICS.render(g);
                }

                boolean chat = HudDrag.chatOpen();

                for (HudModule h : Modules.HUD) {
                    if (h.enabled()) {
                        boolean live = h.shown();
                        if (live || chat) {
                            draw(g, h, chat && !live, false);
                        }
                    }
                }

                Toasts.render(g);
                Pings.render(g);
                FriendCards.render(g);
                if (HudDrag.chatOpen()) {
                    double mx = mouse(mc.mouseHandler.xpos(), mc.getWindow().getScreenWidth(), Px.width());
                    double my = mouse(mc.mouseHandler.ypos(), mc.getWindow().getScreenHeight(), Px.height());
                    HudDrag.frames(g, mx, my);
                    HudDrag.guides(g);
                }

                FrontEndLoading.worldOutro(g);
            } finally {
                Px.end(g);
            }
        }
    }

    private static double mouse(double pos, int window, int pixels) {
        return pos * (double) pixels / (double) Math.max(1, window);
    }

    public static void menuOverlay(GuiGraphics g) {
        Px.begin(g);

        try {
            for (HudModule h : Modules.HUD) {
                if (h instanceof DynamicIsland && h.enabled()) {
                    draw(g, h, false, false);
                }
            }

            Toasts.render(g);
        } finally {
            Px.end(g);
        }
    }

    public static void draw(GuiGraphics g, HudModule h, boolean editing, boolean ghost) {
        int sw = Px.width();
        int sh = Px.height();
        h.prepare();
        int x = Math.max(0, Math.min(sw - h.w, h.x(sw)));
        int y = Math.max(0, Math.min(sh - h.h, h.y(sh)));
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        float old = Px.fade;
        if (ghost) {
            Px.fade = old * 0.35F;
        }

        try {
            Anim.snap = h.rescaled();
            h.render(g, editing);
        } catch (RuntimeException ignored) {
        } finally {
            Anim.snap = false;
            Px.fade = old;
            g.pose().popMatrix();
        }
    }
}
