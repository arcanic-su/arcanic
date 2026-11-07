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

import java.util.ArrayList;
import java.util.List;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Dark pills in two columns hugging a round close button in the middle of the screen, as on RP servers: they fly out
 * of it when the menu opens. The player menu and the ping menu are made of it.
 */
abstract class PillMenu extends Screen {
    static final int PILL = 0xF0161719;
    static final int PILL_HOVER = 0xFFF4F4F6;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_HOVER = 0xFF141518;
    static final int GREEN = 0xFF4ADE80;
    static final int AMBER = 0xFFFBBF24;
    static final int BLUE = 0xFF60A5FA;
    static final int VIOLET = 0xFFA78BFA;
    static final int RED = 0xFFFF5C72;
    static final int GREY = 0xFFA1A1AA;

    final Anim anim = new Anim();
    final List<Pill> pills = new ArrayList<>();
    final long openedAt = System.currentTimeMillis();
    private long closedAt;
    /** The pill under the mouse: its index, -1 for the close button, -2 for none. */
    int hovered = -2;
    // where each pill was drawn this frame: x, y, w, h
    private int[][] boxes = new int[0][];

    record Pill(String icon, int colour, String label, Runnable run, boolean stays, boolean danger) {}

    PillMenu() {
        super(Component.literal("Arcanic"));
    }

    /** The pills as they are now; called every frame, so labels follow the state. */
    abstract void build(List<Pill> into);

    abstract String title();

    abstract String subtitle();

    /** The world behind is blurred; a menu used in a fight keeps it sharp. */
    boolean blur() {
        return true;
    }

    float u() {
        return Math.max(0.6F, Math.min(1.6F, (float) Px.height() / 1080.0F));
    }

    int s(float v) {
        return Math.max(1, Math.round(v * this.u()));
    }

    void close() {
        if (this.closedAt == 0L) {
            this.closedAt = System.currentTimeMillis();
            UiSound.play("interact_close");
        }
    }

    boolean closing() {
        return this.closedAt != 0L;
    }

    /** Runs a pill as a click on it does. */
    void pick(int i) {
        if (i < 0 || i >= this.pills.size()) return;
        Pill p = this.pills.get(i);
        UiSound.play("interact_pick");
        p.run.run();
        if (!p.stays) this.close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (this.blur() && Minecraft.getInstance().level != null) {
            this.renderBlurredBackground(g);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.pills.clear();
        this.build(this.pills);
        long now = System.currentTimeMillis();
        float out = this.closedAt == 0L ? 0.0F : Math.min(1.0F, (float) (now - this.closedAt) / 140.0F);
        if (out >= 1.0F) {
            this.onClose();
            return;
        }

        double mx = Px.px(mouseX);
        double my = Px.px(mouseY);
        float t = (float) (now - this.openedAt) / 1000.0F;
        int cx = Px.width() / 2;
        int cy = Px.height() / 2;
        int h = this.s(48.0F);
        int pitch = h + this.s(18.0F);
        int fs = this.s(16.0F);
        int is = this.s(18.0F);
        int pad = this.s(24.0F);
        int ring = this.s(88.0F);
        int rows = (this.pills.size() + 1) / 2;
        this.boxes = new int[this.pills.size()][];
        int closeD = this.s(54.0F);
        int hover = -2;
        if (Math.hypot(mx - cx, my - cy) <= closeD / 2.0) hover = -1;

        Px.begin(g);
        float old = Px.fade;
        try {
            float base = 1.0F - out;
            Px.fade = base * Math.min(1.0F, t / 0.18F);
            Px.rect(g, 0, 0, Px.width(), Px.height(), this.blur() ? 0x33000000 : 0x22000000);

            // whose menu this is, small, above the pills
            int top = cy - rows * pitch / 2 - this.s(10.0F);
            Px.textCenter(g, this.title(), cx, top - this.s(40.0F), Px.Weight.SEMIBOLD, this.s(18.0F), TEXT);
            Px.textCenter(g, this.subtitle(), cx, top - this.s(16.0F), Px.Weight.MEDIUM, this.s(12.0F), 0xB3FFFFFF);

            for (int i = 0; i < this.pills.size(); i++) {
                Pill p = this.pills.get(i);
                boolean left = i % 2 == 0;
                int row = i / 2;
                int dy = Math.round(((float) row - (float) (rows - 1) / 2.0F) * (float) pitch);
                // the inner ends follow a circle round the close button
                int inner = (int)
                        Math.max(this.s(13.0F), Math.sqrt(Math.max(0.0, (double) ring * ring - (double) dy * dy)));
                int w = pad * 2 + is + this.s(10.0F) + Px.textWidth(Lang.tr(p.label), Px.Weight.MEDIUM, fs);
                int x = left ? cx - inner - w : cx + inner;
                int y = cy + dy - h / 2;
                this.boxes[i] = new int[] {x, y, w, h};
                if (mx >= x && mx < x + w && my >= y && my < y + h) hover = i;
            }

            if (hover != this.hovered) {
                if (hover != -2) UiSound.play("interact_hover");
                this.hovered = hover;
            }

            for (int i = 0; i < this.pills.size(); i++) {
                Pill p = this.pills.get(i);
                int[] b = this.boxes[i];
                float k = Anim.easeOutCubic(Math.max(0.0F, Math.min(1.0F, (t - 0.03F * i) / 0.24F)));
                float hk = this.anim.to("h" + i, hover == i ? 1.0F : 0.0F, 22.0F);
                boolean left = i % 2 == 0;
                int slide = Math.round((float) this.s(46.0F) * (1.0F - k));
                int x = b[0] + (left ? slide : -slide);
                Px.fade = base * k;
                Px.shadow(g, x, b[1] + this.s(4.0F), b[2], b[3], this.s(14.0F), 0x55000000);
                Px.round(g, x, b[1], b[2], b[3], b[3] / 2, Px.mix(PILL, PILL_HOVER, hk));
                int tx = x + pad;
                ArcanicMenu.glyph(g, p.icon, tx, b[1] + (b[3] - is) / 2, is, p.colour);
                int tc = p.danger ? Px.mix(RED, 0xFFE11D48, hk) : Px.mix(TEXT, TEXT_HOVER, hk);
                Px.text(g, Lang.tr(p.label), tx + is + this.s(10.0F), b[1] + (b[3] - fs) / 2, Px.Weight.MEDIUM, fs, tc);
            }

            // the round close button in the middle
            float ck = Anim.easeOutCubic(Math.min(1.0F, t / 0.22F));
            float chk = this.anim.to("close", hover == -1 ? 1.0F : 0.0F, 22.0F);
            int d = Math.round((float) closeD * (0.6F + 0.4F * ck));
            Px.fade = base * ck;
            Px.shadow(g, cx - d / 2, cy - d / 2 + this.s(4.0F), d, d, this.s(14.0F), 0x55000000);
            Px.round(g, cx - d / 2, cy - d / 2, d, d, d / 2, Px.mix(PILL, PILL_HOVER, chk));
            int xs = this.s(22.0F);
            Px.icon(g, "w.x", cx - xs / 2, cy - xs / 2, xs, Px.mix(TEXT, TEXT_HOVER, chk));
        } finally {
            Px.fade = old;
            Px.end(g);
        }
    }

    /** The pill under a point, -1 for the close button, -2 for none. */
    int at(double mx, double my) {
        for (int i = 0; i < this.boxes.length && i < this.pills.size(); i++) {
            int[] b = this.boxes[i];
            if (b != null && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return i;
        }
        return Math.hypot(mx - Px.width() / 2.0, my - Px.height() / 2.0) <= this.s(54.0F) / 2.0 ? -1 : -2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (this.closedAt != 0L) return true;
        if (e.button() == 2) {
            // the wheel closes the menu again, but never the click that has just opened it
            if (System.currentTimeMillis() - this.openedAt > 350L) this.close();
            return true;
        }
        int i = this.at(Px.px(e.x()), Px.px(e.y()));
        if (i >= 0) {
            this.pick(i);
        } else {
            this.close(); // the close button and anything outside the pills
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(e);
    }
}
