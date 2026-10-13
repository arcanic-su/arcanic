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

import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Development only: samples of every text and icon size on a black page, to measure their real pixel bounds. */
public final class CalibScreen extends Screen {
    public CalibScreen() {
        super(Component.literal("calib"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Px.begin(g);
        Px.rect(g, 0, 0, Px.width(), Px.height(), 0xFF000000);
        int y = 20;
        for (int px = 8; px <= 24; px++) {
            y += px + 12;
            Px.rect(g, 0, y, 4, 1, 0xFFFF0000);
            g.drawString(Minecraft.getInstance().font, Px.text("НАГЕ", Px.Weight.MEDIUM, px), 20, y, 0xFFFFFFFF, false);
            g.drawString(Minecraft.getInstance().font, Px.icon("square", px), 400, y, 0xFFFFFFFF, false);
            y += px + 12;
        }
        Px.end(g);
    }
}
