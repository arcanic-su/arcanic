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
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A small dialog over the menu: a title, then a field, a switch or a list, and one button. */
public final class PromptScreen extends Screen {
    private final Screen parent;
    private final String ok;
    private final Anim anim = new Anim();
    private String message, placeholder, switchOff, switchOn;
    private List<String> options = List.of();
    private List<String> lines = List.of();
    private boolean switched;
    private int picked;
    private Runnable confirmed = () -> {};
    private EditBox field;
    private Kit k = new Kit();
    // offsets from the top of the card
    private int head, fieldY, switchY, optionsY, buttonY;
    private int cx, cy, cw, ch;

    private PromptScreen(Screen parent, String title, String ok) {
        super(Component.literal(Lang.tr(title)));
        this.parent = parent;
        this.ok = ok;
        anim.set("open", 0);
    }

    /** A question with nothing to fill in. */
    public static PromptScreen ask(Screen parent, String title, String message, String ok, Runnable onOk) {
        PromptScreen p = new PromptScreen(parent, title, ok);
        p.message = message;
        p.confirmed = onOk;
        return p;
    }

    /** One line to type. */
    public static PromptScreen field(
            Screen parent, String title, String placeholder, String ok, Consumer<String> onOk) {
        PromptScreen p = new PromptScreen(parent, title, ok);
        p.placeholder = placeholder;
        p.confirmed = () -> onOk.accept(p.typed());
        return p;
    }

    /** A line to type and a two-way switch under it, {@code off} on the left and {@code on} on the right. */
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

    /** One of several; {@code onOk} gets the index. The options are shown as they are, not translated. */
    public static PromptScreen pick(
            Screen parent, String title, List<String> options, int picked, String ok, IntConsumer onOk) {
        PromptScreen p = new PromptScreen(parent, title, ok);
        p.options = options;
        p.picked = picked;
        p.confirmed = () -> onOk.accept(p.picked);
        return p;
    }

    private String typed() {
        return field == null ? "" : field.getValue().trim();
    }

    private boolean ready() {
        return field == null || !typed().isEmpty();
    }

    @Override
    protected void init() {
        String was = field != null ? field.getValue() : "";
        clearWidgets();
        k = new Kit();
        cw = k.S(300);
        int pad = k.S(18), inner = cw - pad * 2;
        lines = message == null ? List.of() : wrap(Lang.tr(message), Weight.MEDIUM, k.S(12), inner);
        head = k.S(46) + lines.size() * k.S(17);
        int y = head + k.S(12);
        fieldY = y;
        if (placeholder != null) y += k.S(32) + k.S(10);
        switchY = y;
        if (switchOff != null) y += k.S(28) + k.S(10);
        optionsY = y;
        if (!options.isEmpty()) y += options.size() * k.S(34) + k.S(8);
        buttonY = y;
        ch = y + k.S(34) + pad;
        cx = (Px.width() - cw) / 2;
        cy = (Px.height() - ch) / 2;
        if (placeholder == null) return;
        field = new EditBox(
                font,
                Kit.logical(cx + pad),
                Kit.logical(cy + fieldY),
                Kit.logical(inner),
                Kit.logical(k.S(32)),
                Component.literal(Lang.tr(placeholder)));
        field.setMaxLength(32);
        field.setBordered(false);
        field.setValue(was);
        addWidget(field); // typed natively, drawn by us in the client font
        setInitialFocus(field);
    }

    static List<String> wrap(String text, Weight w, int size, int max) {
        List<String> out = new ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && Px.textWidth(next, w, size) > max) {
                out.add(line);
                line = word;
            } else line = next;
        }
        if (!line.isEmpty()) out.add(line);
        return out;
    }

    private void confirm() {
        if (!ready()) {
            UiSound.play("pop", .7f, 1);
            return;
        }
        Minecraft.getInstance().setScreen(parent);
        confirmed.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (!options.isEmpty() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            picked = Math.floorMod(picked + (key == GLFW.GLFW_KEY_UP ? -1 : 1), options.size());
            UiSound.play("tick");
            return true;
        }
        if (switchOff != null && key == GLFW.GLFW_KEY_TAB) {
            switched = !switched;
            UiSound.play("tick");
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = Px.px(e.x()), my = Px.px(e.y());
        if (!Kit.in(mx, my, cx, cy, cw, ch)) {
            onClose();
            return true;
        }
        if (e.button() != 0) return true;
        int x = cx + k.S(18), w = cw - k.S(36);
        if (switchOff != null && Kit.in(mx, my, x, cy + switchY, w, k.S(28))) {
            boolean right = mx >= x + w / 2;
            if (right != switched) UiSound.play("click");
            switched = right;
            return true;
        }
        for (int i = 0; i < options.size(); i++)
            if (Kit.in(mx, my, x, cy + optionsY + i * k.S(34), w, k.S(34))) {
                if (picked != i) UiSound.play("click");
                picked = i;
                return true;
            }
        if (Kit.in(mx, my, x, cy + buttonY, w, k.S(34))) {
            confirm();
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        if (parent != null) parent.render(g, -1, -1, delta);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);
        g.nextStratum();
        anim.frame();
        float open = anim.to("open", 1, 15);
        int px = (int) Px.px(mx), py = (int) Px.px(my);
        int x = cx + k.S(18), w = cw - k.S(36);
        Px.begin(g);
        float old = Px.fade;
        try {
            Px.fade = open;
            Px.rect(g, 0, 0, Px.width(), Px.height(), Kit.SCRIM);
            int top = cy + Math.round(k.S(8) * (1 - ArcanicMenu.ease(open)));
            k.card(g, cx, top, cw, ch);
            k.header(g, x, top, w, getTitle().getString(), null, head);
            for (int i = 0; i < lines.size(); i++)
                Px.text(g, lines.get(i), x, top + k.S(36) + i * k.S(17), Weight.MEDIUM, k.S(12), Kit.dim());

            if (field != null)
                k.field(
                        g,
                        field,
                        x,
                        top + fieldY,
                        w,
                        Lang.tr(placeholder),
                        null,
                        Kit.in(px, py, x, cy + fieldY, w, k.S(32)),
                        anim,
                        "f");
            if (switchOff != null)
                k.segmented(
                        g,
                        x,
                        top + switchY,
                        w,
                        Lang.tr(switchOff),
                        Lang.tr(switchOn),
                        anim.to("seg", switched ? 1 : 0, 16),
                        px,
                        py + (top - cy));
            for (int i = 0; i < options.size(); i++) {
                int oy = top + optionsY + i * k.S(34), oh = k.S(34);
                boolean hv = Kit.in(px, py, x, cy + optionsY + i * k.S(34), w, oh);
                float hk = anim.to("o" + i, hv ? 1 : 0, 20);
                if (hk > .01f)
                    Px.round(g, x, oy, w, oh, k.S(5), Px.a(Kit.T().text(), (int) ((Kit.light() ? 12 : 14) * hk)));
                if (i == picked)
                    ArcanicMenu.glyph(g, "check", x + k.S(10), oy + (oh - k.S(14)) / 2, k.S(14), ArcanicMenu.accText());
                Px.text(
                        g,
                        options.get(i),
                        x + k.S(34),
                        oy + (oh - k.S(13)) / 2,
                        i == picked ? Weight.SEMIBOLD : Weight.MEDIUM,
                        k.S(13),
                        i == picked ? Kit.T().text() : Kit.T().muted());
            }
            k.button(
                    g,
                    Lang.tr(ok),
                    x,
                    top + buttonY,
                    w,
                    ready(),
                    Kit.in(px, py, x, cy + buttonY, w, k.S(34)),
                    anim,
                    "b");
        } finally {
            Px.fade = old;
            Px.end(g);
        }
    }

    /** The pieces of the module settings card, for the dialogs and Arcanic Social. Sizes follow the menu's scale. */
    static final class Kit {
        static final int SCRIM = 0x99000000;
        final float u;

        Kit() {
            u = (float) Math.max(
                    .45, Math.min(2.2, Math.min(Px.height() / 1080.0, Px.width() / 1060.0) * Theme.menuScale * .86));
        }

        int S(double v) {
            return (int) Math.round(v * u);
        }

        static int logical(int px) {
            return Math.round(px / (float) Px.guiScale());
        }

        static boolean in(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
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
            return light() ? Px.a(0x1B1530, hv ? 14 : 8) : Px.mix(T().bg() | 0xFF000000, 0xFF000000, hv ? .05f : .22f);
        }

        void card(GuiGraphics g, int x, int y, int w, int h) {
            Px.shadow(g, x, y + S(6), w, h, S(18), Px.a(0, light() ? 30 : 70));
            Px.round(g, x, y, w, h, S(13), light() ? 0xFFFFFFFF : 0xF40C0D12);
            Px.outline(g, x, y, w, h, S(13), light() ? Px.a(T().text(), 26) : 0x18FFFFFF);
        }

        /** The name, a line under it if any, and the hairline that closes the header at {@code head}. */
        void header(GuiGraphics g, int x, int y, int w, String title, String sub, int head) {
            Px.text(
                    g,
                    Px.ellipsize(title, Weight.SEMIBOLD, S(15), w),
                    x,
                    y + S(15),
                    Weight.SEMIBOLD,
                    S(15),
                    T().text());
            if (sub != null)
                Px.text(g, Px.ellipsize(sub, Weight.MEDIUM, S(11), w), x, y + S(36), Weight.MEDIUM, S(11), dim());
            Px.rect(g, x, y + head - S(4), w, Math.max(1, S(1)), hair());
        }

        /** A 32 px field like the value boxes, drawn by us; the tail of a long value stays in view. */
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
            int h = S(32);
            boolean focus = box.isFocused();
            float f = anim.to(key, focus ? 1 : 0, 18);
            Px.round(g, x, y, w, h, S(6), inset(hv && !focus));
            Px.outline(g, x, y, w, h, S(6), Px.mix(Px.a(T().text(), light() ? 30 : 16), ArcanicMenu.acc(), f));
            int tx = x + S(11);
            if (icon != null) {
                ArcanicMenu.glyph(g, icon, tx, y + (h - S(14)) / 2, S(14), Px.mix(dim(), ArcanicMenu.accText(), f));
                tx += S(14) + S(8);
            }
            int size = S(13), avail = x + w - S(11) - tx;
            String value = box.getValue();
            int cur = Math.max(0, Math.min(value.length(), box.getCursorPosition())), from = 0;
            while (from < cur && Px.textWidth(value.substring(from, cur), Weight.MEDIUM, size) > avail) from++;
            String shown = value.substring(from);
            while (!shown.isEmpty() && Px.textWidth(shown, Weight.MEDIUM, size) > avail)
                shown = shown.substring(0, shown.length() - 1);
            int ty = y + (h - size) / 2;
            if (value.isEmpty() && hint != null)
                Px.text(g, Px.ellipsize(hint, Weight.MEDIUM, size, avail), tx, ty, Weight.MEDIUM, size, dim());
            else Px.text(g, shown, tx, ty, Weight.MEDIUM, size, T().text());
            if (focus && (System.currentTimeMillis() / 530) % 2 == 0) {
                int at = tx + (cur > from ? Px.textWidth(value.substring(from, cur), Weight.MEDIUM, size) + 1 : 0);
                Px.rect(g, at, y + S(9), Math.max(1, S(1.5)), h - S(18), ArcanicMenu.accText());
            }
        }

        /** Two choices with the accent thumb under the picked one, as the menu draws its modes. */
        void segmented(GuiGraphics g, int x, int y, int w, String left, String right, float thumb, int mx, int my) {
            int h = S(28), in = S(4);
            Px.round(g, x, y, w, h, S(6), light() ? 0xFFEFEFF4 : inset(false));
            Px.outline(g, x, y, w, h, S(6), Px.a(T().text(), light() ? 20 : 14));
            float seg = (w - in * 2) / 2f;
            int t0 = x + in + Math.round(seg * thumb), t1 = x + in + Math.round(seg * (thumb + 1));
            Px.round(g, t0, y + in, t1 - t0, h - in * 2, S(4), ArcanicMenu.acc());
            for (int i = 0; i < 2; i++) {
                float a = Math.max(0, 1 - Math.abs(thumb - i));
                int s0 = x + in + Math.round(seg * i), s1 = x + in + Math.round(seg * (i + 1));
                boolean hv = in(mx, my, s0, y, s1 - s0, h);
                String label = Px.ellipsize(i == 0 ? left : right, Weight.SEMIBOLD, S(12), s1 - s0 - S(12));
                Px.textCenter(
                        g,
                        label,
                        (s0 + s1) / 2,
                        y + (h - S(12)) / 2,
                        a > .5f ? Weight.SEMIBOLD : Weight.MEDIUM,
                        S(12),
                        Px.mix(hv ? T().text() : T().muted(), 0xFFFFFFFF, a));
            }
        }

        /** The accent button; flat and dim until it can go. */
        void button(GuiGraphics g, String label, int x, int y, int w, boolean live, boolean hv, Anim anim, String key) {
            int h = S(34);
            float hk = anim.to(key, live && hv ? 1 : 0, 20);
            if (live) Px.round(g, x, y, w, h, S(7), Px.mix(ArcanicMenu.acc(), 0xFFFFFFFF, .08f * hk));
            else {
                Px.round(g, x, y, w, h, S(7), inset(false));
                Px.outline(g, x, y, w, h, S(7), hair());
            }
            Px.textCenter(g, label, x + w / 2, y + (h - S(13)) / 2, Weight.SEMIBOLD, S(13), live ? 0xFFFFFFFF : dim());
        }
    }
}
