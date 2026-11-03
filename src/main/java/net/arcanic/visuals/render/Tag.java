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
package net.arcanic.visuals.render;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class Tag {
    private static final Identifier HEART = Identifier.withDefaultNamespace("hud/heart/hardcore_full");
    private static final Identifier FRAME = Identifier.withDefaultNamespace("hud/heart/container_hardcore");
    private static final Identifier WITHERED = Identifier.withDefaultNamespace("hud/heart/withered_hardcore_full");
    public static final int RED = -1752497;

    private Tag() {}

    public static int heartSize(int fs) {
        return 9 * Math.max(1, Math.round((float) fs * 1.15F / 9.0F));
    }

    public static void heart(GuiGraphics g, int x, int y, int size, boolean lethal) {
        int c = Px.am(-1, Px.fade);
        if (c >>> 24 != 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x, y, size, size, c);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, lethal ? WITHERED : HEART, x, y, size, size, c);
        }
    }

    public static int height(int fs) {
        return Math.max(heartSize(fs) + Math.round((float) fs * 0.7F), Math.round((float) fs * 1.95F));
    }

    public static int width(int fs, String text) {
        int pad = Math.round((float) fs * 0.62F);
        return pad
                + heartSize(fs)
                + Math.round((float) fs * 0.4F)
                + Px.textWidth(text, Px.Weight.SEMIBOLD, fs)
                + pad
                + fs / 6;
    }

    public static void draw(GuiGraphics g, int cx, int top, int fs, String text, boolean lethal, float a) {
        if (!(a < 0.02F)) {
            int h = height(fs);
            int w = width(fs, text);
            int x = cx - w / 2;
            int r = h / 2;
            int pad = Math.round((float) fs * 0.62F);
            int hs = heartSize(fs);
            float keep = Px.fade;
            Px.fade = keep * a;
            Px.shadow(g, x, top + Math.max(1, fs / 6), w, h, Math.max(4, fs / 2), 1426063360);
            Px.round(g, x, top, w, h, r, lethal ? 0xF2341016 : 0xEB0E0E11);
            Px.outline(g, x, top, w, h, r, lethal ? Px.a(-1752497, 150) : 452984831);
            heart(g, x + pad, top + (h - hs) / 2, hs, lethal);
            g.nextStratum();
            Px.text(
                    g,
                    text,
                    x + pad + hs + Math.round((float) fs * 0.4F),
                    top + (h - fs) / 2,
                    Px.Weight.SEMIBOLD,
                    fs,
                    lethal ? -7195 : -855308);
            Px.fade = keep;
        }
    }

    public static String damage(float hp, boolean lethal) {
        if (lethal) {
            return "Насмерть";
        } else {
            float hearts = hp / 2.0F;
            return Math.abs(hearts - (float) Math.round(hearts)) < 0.05F
                    ? "−" + Math.round(hearts)
                    : String.format(Locale.ROOT, "−%.1f", hearts);
        }
    }
}
