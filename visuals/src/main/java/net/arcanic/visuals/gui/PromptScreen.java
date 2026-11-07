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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class PromptScreen extends Screen {
    private final Screen parent;
    private final String ok;
    private final Anim anim = new Anim();
    private String message;
    private String placeholder;
    private String switchOff;
    private String switchOn;
    private List<String> options = List.of();
    private List<String> lines = List.of();
    private boolean switched;
    private int picked;
    private Runnable confirmed = () -> {};
    private String initial;
    private String dangerLabel;
    private int limit = 32;
    private Runnable danger;
    private EditBox field;
    private Kit k = new Kit();
    private int head;
    private int fieldY;
    private int switchY;
    private int optionsY;
    private int buttonY;
    private int cx;
    private int cy;
    private int cw;
    private int ch;

    private PromptScreen(Screen parent, String title, String ok) {
        super(Component.literal(Lang.tr(title)));
        this.parent = parent;
        this.ok = ok;
        this.anim.set("open", 0.0F);
    }

    public static PromptScreen ask(Screen parent, String title, String message, String ok, Runnable onOk) {
        PromptScreen p = new PromptScreen(parent, title, ok);
        p.message = message;
        p.confirmed = onOk;
        return p;
    }

    public static PromptScreen field(
            Screen parent, String title, String placeholder, String ok, Consumer<String> onOk) {
        PromptScreen p = new PromptScreen(parent, title, ok);
        p.placeholder = placeholder;
        p.confirmed = () -> onOk.accept(p.typed());
        return p;
    }

    public static PromptScreen fieldAndSwitch(
            Screen parent,
            String title,
            String placeholder,
            String off,
            String on,
            String ok,
            BiConsumer<String, Boolean> onOk) {
        PromptScreen p = new PromptScreen(parent, title, ok);
        p.placeholder = placeholder;
        p.switchOff = off;
        p.switchOn = on;
        p.confirmed = () -> onOk.accept(p.typed(), p.switched);
        return p;
    }

    public static PromptScreen pick(
            Screen parent, String title, List<String> options, int picked, String ok, IntConsumer onOk) {
        PromptScreen p = new PromptScreen(parent, title, ok);
        p.options = options;
        p.picked = picked;
        p.confirmed = () -> onOk.accept(p.picked);
        return p;
    }

    public PromptScreen value(String v) {
        this.initial = v;
        return this;
    }

    public PromptScreen limit(int max) {
        this.limit = Math.max(1, max);
        return this;
    }

    public PromptScreen note(String text) {
        this.message = text;
        return this;
    }

    public PromptScreen danger(String label, Runnable run) {
        this.dangerLabel = label;
        this.danger = run;
        return this;
    }

    private int dangerW(int w) {
        return Math.round((float) (w - this.k.S(8.0)) * 0.4F);
    }

    private String typed() {
        return this.field == null ? "" : this.field.getValue().trim();
    }

    private boolean ready() {
        return this.field == null || !this.typed().isEmpty();
    }

    protected void init() {
        String was = this.field != null ? this.field.getValue() : (this.initial != null ? this.initial : "");
        this.clearWidgets();
        this.k = new Kit();
        this.cw = this.k.S(300.0);
        int pad = this.k.S(18.0);
        int inner = this.cw - pad * 2;
        this.lines =
                this.message == null ? List.of() : wrap(Lang.tr(this.message), Px.Weight.MEDIUM, this.k.S(12.0), inner);
        this.head = this.k.S(52.0) + this.lines.size() * this.k.S(17.0);
        int y = this.head + this.k.S(12.0);
        this.fieldY = y;
        if (this.placeholder != null) {
            y += this.k.S(32.0) + this.k.S(10.0);
        }

        this.switchY = y;
        if (this.switchOff != null) {
            y += this.k.S(28.0) + this.k.S(10.0);
        }

        this.optionsY = y;
        if (!this.options.isEmpty()) {
            y += this.options.size() * this.k.S(34.0) + this.k.S(8.0);
        }

        this.buttonY = y;
        this.ch = y + this.k.S(34.0) + pad;
        this.cx = (Px.width() - this.cw) / 2;
        this.cy = (Px.height() - this.ch) / 2;
        if (this.placeholder != null) {
            this.field = new EditBox(
                    this.font,
                    PromptScreen.Kit.logical(this.cx + pad),
                    PromptScreen.Kit.logical(this.cy + this.fieldY),
                    PromptScreen.Kit.logical(inner),
                    PromptScreen.Kit.logical(this.k.S(32.0)),
                    Component.literal(Lang.tr(this.placeholder)));
            this.field.setMaxLength(this.limit);
            this.field.setBordered(false);
            this.field.setValue(was);
            this.addWidget(this.field);
            this.setInitialFocus(this.field);
        }
    }

    static List<String> wrap(String text, Px.Weight w, int size, int max) {
        List<String> out = new ArrayList<>();
        String line = "";

        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && Px.textWidth(next, w, size) > max) {
                out.add(line);
                line = word;
            } else {
                line = next;
            }
        }

        if (!line.isEmpty()) {
            out.add(line);
        }

        return out;
    }

    private void confirm() {
        if (!this.ready()) {
            UiSound.play("pop", 0.7F, 1.0F);
        } else {
            Minecraft.getInstance().setScreen(this.parent);
            this.confirmed.run();
        }
    }

    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key != 257 && key != 335) {
            if (this.options.isEmpty() || key != 265 && key != 264) {
                if (this.switchOff != null && key == 258) {
                    this.switched = !this.switched;
                    UiSound.play("tick");
                    return true;
                } else {
                    return super.keyPressed(event);
                }
            } else {
                this.picked = Math.floorMod(this.picked + (key == 265 ? -1 : 1), this.options.size());
                UiSound.play("tick");
                return true;
            }
        } else {
            this.confirm();
            return true;
        }
    }

    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = Px.px(e.x());
        double my = Px.px(e.y());
        if (!PromptScreen.Kit.in(mx, my, this.cx, this.cy, this.cw, this.ch)) {
            this.onClose();
            return true;
        } else if (e.button() != 0) {
            return true;
        } else {
            int x = this.cx + this.k.S(18.0);
            int w = this.cw - this.k.S(36.0);
            if (PromptScreen.Kit.in(
                    mx,
                    my,
                    this.cx + this.cw - this.k.S(18.0) - this.k.S(28.0),
                    this.cy + this.k.S(14.0),
                    this.k.S(28.0),
                    this.k.S(28.0))) {
                UiSound.play("pop", 0.85F, 0.7F);
                this.onClose();
                return true;
            } else if (this.switchOff != null
                    && PromptScreen.Kit.in(mx, my, x, this.cy + this.switchY, w, this.k.S(28.0))) {
                boolean right = mx >= (double) (x + w / 2);
                if (right != this.switched) {
                    UiSound.play("click");
                }

                this.switched = right;
                return true;
            } else {
                for (int i = 0; i < this.options.size(); i++) {
                    if (PromptScreen.Kit.in(
                            mx, my, x, this.cy + this.optionsY + i * this.k.S(34.0), w, this.k.S(34.0))) {
                        if (this.picked != i) {
                            UiSound.play("click");
                        }

                        this.picked = i;
                        return true;
                    }
                }

                int dw = this.dangerW(w);
                int bx = x + dw + (dw > 0 ? this.k.S(8.0) : 0);
                if (PromptScreen.Kit.in(mx, my, x, this.cy + this.buttonY, dw, this.k.S(34.0))) {
                    UiSound.play("click");
                    Minecraft.getInstance().setScreen(this.parent);
                    if (this.danger != null) {
                        this.danger.run();
                    }

                    return true;
                } else if (PromptScreen.Kit.in(mx, my, bx, this.cy + this.buttonY, x + w - bx, this.k.S(34.0))) {
                    this.confirm();
                    return true;
                } else {
                    return super.mouseClicked(e, dbl);
                }
            }
        }
    }

    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        if (this.parent != null) {
            this.parent.render(g, -1, -1, delta);
        }
    }

    public void render(GuiGraphics g, int mx, int my, float delta) {
        this.renderBackground(g, mx, my, delta);
        g.nextStratum();
        this.anim.frame();
        float open = this.anim.to("open", 1.0F, 15.0F);
        int px = (int) Px.px((double) mx);
        int py = (int) Px.px((double) my);
        int x = this.cx + this.k.S(18.0);
        int w = this.cw - this.k.S(36.0);
        Px.begin(g);
        float old = Px.fade;

        try {
            Px.fade = open;
            Px.rect(g, 0, 0, Px.width(), Px.height(), 0x99000000);
            int top = this.cy;
            float sc = 0.94F + 0.06F * ArcanicMenu.ease(open);
            g.pose().pushMatrix();
            g.pose().translate((float) this.cx + (float) this.cw / 2.0F, (float) this.cy + (float) this.ch / 2.0F);
            g.pose().scale(sc, sc);
            g.pose()
                    .translate(
                            (float) (-this.cx) - (float) this.cw / 2.0F, (float) (-this.cy) - (float) this.ch / 2.0F);
            this.k.card(g, this.cx, top, this.cw, this.ch);
            this.k.header(g, x, top, w - this.k.S(36.0), this.getTitle().getString(), null, this.head);
            this.k.close(
                    g, this.cx + this.cw - this.k.S(18.0) - this.k.S(28.0), top + this.k.S(14.0), px, py, this.anim);

            for (int i = 0; i < this.lines.size(); i++) {
                Px.text(
                        g,
                        this.lines.get(i),
                        x,
                        top + this.k.S(42.0) + i * this.k.S(17.0),
                        Px.Weight.MEDIUM,
                        this.k.S(12.0),
                        PromptScreen.Kit.dim());
            }

            if (this.field != null) {
                this.k.field(
                        g,
                        this.field,
                        x,
                        top + this.fieldY,
                        w,
                        Lang.tr(this.placeholder),
                        null,
                        PromptScreen.Kit.in((double) px, (double) py, x, this.cy + this.fieldY, w, this.k.S(32.0)),
                        this.anim,
                        "f");
            }

            if (this.switchOff != null) {
                this.k.segmented(
                        g,
                        x,
                        top + this.switchY,
                        w,
                        Lang.tr(this.switchOff),
                        Lang.tr(this.switchOn),
                        this.anim.to("seg", this.switched ? 1.0F : 0.0F, 16.0F),
                        px,
                        py + (top - this.cy));
            }

            for (int i = 0; i < this.options.size(); i++) {
                int oy = top + this.optionsY + i * this.k.S(34.0);
                int oh = this.k.S(34.0);
                boolean hv = PromptScreen.Kit.in(
                        (double) px, (double) py, x, this.cy + this.optionsY + i * this.k.S(34.0), w, oh);
                float hk = this.anim.to("o" + i, hv ? 1.0F : 0.0F, 20.0F);
                if (hk > 0.01F) {
                    Px.round(
                            g,
                            x,
                            oy,
                            w,
                            oh,
                            this.k.S(5.0),
                            Px.a(PromptScreen.Kit.T().text(), (int)
                                    ((float) (PromptScreen.Kit.light() ? 12 : 14) * hk)));
                }

                if (i == this.picked) {
                    ArcanicMenu.glyph(
                            g,
                            "check",
                            x + this.k.S(10.0),
                            oy + (oh - this.k.S(14.0)) / 2,
                            this.k.S(14.0),
                            ArcanicMenu.accText());
                }

                Px.text(
                        g,
                        this.options.get(i),
                        x + this.k.S(34.0),
                        oy + (oh - this.k.S(13.0)) / 2,
                        i == this.picked ? Px.Weight.SEMIBOLD : Px.Weight.MEDIUM,
                        this.k.S(13.0),
                        i == this.picked
                                ? PromptScreen.Kit.T().text()
                                : PromptScreen.Kit.T().muted());
            }

            int dw = this.dangerW(w);
            int bx = x + dw + (dw > 0 ? this.k.S(8.0) : 0);
            if (this.danger != null) {
                this.k.dangerButton(
                        g,
                        Lang.tr(this.dangerLabel),
                        x,
                        top + this.buttonY,
                        dw,
                        PromptScreen.Kit.in((double) px, (double) py, x, this.cy + this.buttonY, dw, this.k.S(34.0)),
                        this.anim,
                        "d");
            } else {
                this.k.secondary(
                        g,
                        Lang.tr("Отмена"),
                        x,
                        top + this.buttonY,
                        dw,
                        PromptScreen.Kit.in((double) px, (double) py, x, this.cy + this.buttonY, dw, this.k.S(34.0)),
                        this.anim,
                        "c");
            }

            this.k.button(
                    g,
                    Lang.tr(this.ok),
                    bx,
                    top + this.buttonY,
                    x + w - bx,
                    this.ready(),
                    PromptScreen.Kit.in(
                            (double) px, (double) py, bx, this.cy + this.buttonY, x + w - bx, this.k.S(34.0)),
                    this.anim,
                    "b");
            g.pose().popMatrix();
        } finally {
            Px.fade = old;
            Px.end(g);
        }
    }

    static final class Kit {
        static final int SCRIM = 0x99000000;
        final float u;

        Kit() {
            this.u = (float) Math.max(
                    0.45,
                    Math.min(
                            2.2,
                            Math.min((double) Px.height() / 1080.0, (double) Px.width() / 1060.0)
                                    * Theme.menuScale
                                    * 0.86));
        }

        int S(double v) {
            return (int) Math.round(v * (double) this.u);
        }

        static int logical(int px) {
            return Math.round((float) px / (float) Px.guiScale());
        }

        static boolean in(double mx, double my, int x, int y, int w, int h) {
            return mx >= (double) x && mx < (double) (x + w) && my >= (double) y && my < (double) (y + h);
        }

        static Theme.Palette T() {
            return Theme.p();
        }

        static boolean light() {
            return Theme.p().light();
        }

        static int dim() {
            return light() ? T().dim() : 0xFF757D8A;
        }

        static int hair() {
            return Px.a(T().text(), light() ? 22 : 14);
        }

        static int inset(boolean hv) {
            return light() ? Px.a(1774896, hv ? 14 : 8) : Px.mix(T().bg() | 0xFF000000, 0xFF000000, hv ? 0.05F : 0.22F);
        }

        void card(GuiGraphics g, int x, int y, int w, int h) {
            Px.shadow(g, x, y + this.S(10.0), w, h, this.S(30.0), Px.a(0, light() ? 34 : 150));
            Px.round(g, x, y, w, h, this.S(16.0), light() ? -1 : 0xFA0E0E11);
            Px.outline(g, x, y, w, h, this.S(16.0), light() ? Px.a(T().text(), 26) : 385875967);
        }

        void close(GuiGraphics g, int x, int y, int mx, int my, Anim anim) {
            int s = this.S(28.0);
            float hk = anim.to("close", in((double) mx, (double) my, x, y, s, s) ? 1.0F : 0.0F, 20.0F);
            Px.round(g, x, y, s, s, s / 2, Px.mix(inset(false), inset(true), hk));
            int is = this.S(12.0);
            ArcanicMenu.glyph(g, "x", x + (s - is) / 2, y + (s - is) / 2, is, Px.mix(dim(), T().text(), hk));
        }

        void secondary(GuiGraphics g, String label, int x, int y, int w, boolean hv, Anim anim, String key) {
            int h = this.S(34.0);
            float hk = anim.to(key, hv ? 1.0F : 0.0F, 20.0F);
            Px.round(g, x, y, w, h, this.S(9.0), Px.mix(inset(false), inset(true), hk));
            Px.textCenter(
                    g,
                    label,
                    x + w / 2,
                    y + (h - this.S(13.0)) / 2,
                    Px.Weight.SEMIBOLD,
                    this.S(13.0),
                    Px.mix(T().muted(), T().text(), hk));
        }

        void header(GuiGraphics g, int x, int y, int w, String title, String sub, int head) {
            Px.text(
                    g,
                    Px.ellipsize(title, Px.Weight.SEMIBOLD, this.S(15.0), w),
                    x,
                    y + this.S(19.0),
                    Px.Weight.SEMIBOLD,
                    this.S(16.0),
                    T().text());
            if (sub != null) {
                Px.text(
                        g,
                        Px.ellipsize(sub, Px.Weight.MEDIUM, this.S(11.0), w),
                        x,
                        y + this.S(36.0),
                        Px.Weight.MEDIUM,
                        this.S(11.0),
                        dim());
            }
        }

        void field(
                GuiGraphics g,
                EditBox box,
                int x,
                int y,
                int w,
                String hint,
                String icon,
                boolean hv,
                Anim anim,
                String key) {
            int h = this.S(32.0);
            boolean focus = box.isFocused();
            float f = anim.to(key, focus ? 1.0F : 0.0F, 18.0F);
            Px.round(g, x, y, w, h, this.S(9.0), inset(hv && !focus));
            Px.outline(g, x, y, w, h, this.S(9.0), Px.mix(Px.a(T().text(), light() ? 26 : 12), ArcanicMenu.acc(), f));
            int tx = x + this.S(11.0);
            if (icon != null) {
                ArcanicMenu.glyph(
                        g, icon, tx, y + (h - this.S(14.0)) / 2, this.S(14.0), Px.mix(dim(), ArcanicMenu.accText(), f));
                tx += this.S(14.0) + this.S(8.0);
            }

            int size = this.S(13.0);
            int avail = x + w - this.S(11.0) - tx;
            String value = box.getValue();
            int cur = Math.max(0, Math.min(value.length(), box.getCursorPosition()));

            int from;
            for (from = 0;
                    from < cur && Px.textWidth(value.substring(from, cur), Px.Weight.MEDIUM, size) > avail;
                    ++from) {}

            String shown;
            for (shown = value.substring(from);
                    !shown.isEmpty() && Px.textWidth(shown, Px.Weight.MEDIUM, size) > avail;
                    shown = shown.substring(0, shown.length() - 1)) {}

            int ty = y + (h - size) / 2;
            if (value.isEmpty() && hint != null) {
                Px.text(g, Px.ellipsize(hint, Px.Weight.MEDIUM, size, avail), tx, ty, Px.Weight.MEDIUM, size, dim());
            } else {
                Px.text(g, shown, tx, ty, Px.Weight.MEDIUM, size, T().text());
            }

            if (focus && System.currentTimeMillis() / 530L % 2L == 0L) {
                int at = tx + (cur > from ? Px.textWidth(value.substring(from, cur), Px.Weight.MEDIUM, size) + 1 : 0);
                Px.rect(g, at, y + this.S(9.0), Math.max(1, this.S(1.5)), h - this.S(18.0), ArcanicMenu.accText());
            }
        }

        void segmented(GuiGraphics g, int x, int y, int w, String left, String right, float thumb, int mx, int my) {
            int h = this.S(28.0);
            int in = this.S(4.0);
            Px.round(g, x, y, w, h, this.S(6.0), light() ? 0xFFEFEFF4 : inset(false));
            Px.outline(g, x, y, w, h, this.S(6.0), Px.a(T().text(), light() ? 20 : 14));
            float seg = (float) (w - in * 2) / 2.0F;
            int t0 = x + in + Math.round(seg * thumb);
            int t1 = x + in + Math.round(seg * (thumb + 1.0F));
            Px.round(g, t0, y + in, t1 - t0, h - in * 2, this.S(4.0), ArcanicMenu.acc());

            for (int i = 0; i < 2; i++) {
                float a = Math.max(0.0F, 1.0F - Math.abs(thumb - (float) i));
                int s0 = x + in + Math.round(seg * (float) i);
                int s1 = x + in + Math.round(seg * (float) (i + 1));
                boolean hv = in((double) mx, (double) my, s0, y, s1 - s0, h);
                String label =
                        Px.ellipsize(i == 0 ? left : right, Px.Weight.SEMIBOLD, this.S(12.0), s1 - s0 - this.S(12.0));
                Px.textCenter(
                        g,
                        label,
                        (s0 + s1) / 2,
                        y + (h - this.S(12.0)) / 2,
                        a > 0.5F ? Px.Weight.SEMIBOLD : Px.Weight.MEDIUM,
                        this.S(12.0),
                        Px.mix(hv ? T().text() : T().muted(), -1, a));
            }
        }

        void button(GuiGraphics g, String label, int x, int y, int w, boolean live, boolean hv, Anim anim, String key) {
            int h = this.S(34.0);
            float hk = anim.to(key, live && hv ? 1.0F : 0.0F, 20.0F);
            if (live) {
                Px.round(g, x, y, w, h, this.S(9.0), Px.mix(ArcanicMenu.acc(), -1, 0.1F * hk));
            } else {
                Px.round(g, x, y, w, h, this.S(9.0), inset(false));
            }

            Px.textCenter(
                    g,
                    label,
                    x + w / 2,
                    y + (h - this.S(13.0)) / 2,
                    Px.Weight.SEMIBOLD,
                    this.S(13.0),
                    live ? -1 : dim());
        }

        void dangerButton(GuiGraphics g, String label, int x, int y, int w, boolean hv, Anim anim, String key) {
            int h = this.S(34.0);
            int red = 0xFFF0525C;
            float hk = anim.to(key, hv ? 1.0F : 0.0F, 20.0F);
            Px.round(g, x, y, w, h, this.S(7.0), inset(false));
            if (hk > 0.01F) {
                Px.round(g, x, y, w, h, this.S(7.0), Px.a(red, Math.round(40.0F * hk)));
            }

            Px.outline(g, x, y, w, h, this.S(7.0), Px.a(red, Math.round(70.0F + 80.0F * hk)));
            Px.textCenter(
                    g,
                    label,
                    x + w / 2,
                    y + (h - this.S(13.0)) / 2,
                    Px.Weight.SEMIBOLD,
                    this.S(13.0),
                    Px.mix(red, -1, 0.2F * hk));
        }
    }
}
