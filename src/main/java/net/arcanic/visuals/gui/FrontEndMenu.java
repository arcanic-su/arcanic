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

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Sync;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.hud.HudRenderer;
import net.arcanic.visuals.render.*;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.PlayerSkin;

/** Arcanic launch screen, centred. */
public final class FrontEndMenu {
    private enum Kind {
        PLAY,
        CLIENT,
        UTILITY,
        ACCOUNT,
        BACKGROUND,
        FOOTER,
        HINT
    }

    private record Spec(int rank, String icon, Kind kind) {}

    private static final Map<String, Spec> SPECS = Map.ofEntries(
            Map.entry("menu.singleplayer", new Spec(0, "arc:b", Kind.PLAY)),
            Map.entry("menu.playdemo", new Spec(0, "arc:b", Kind.PLAY)),
            Map.entry("menu.multiplayer", new Spec(10, "arc:p", Kind.PLAY)),
            Map.entry("menu.resetdemo", new Spec(20, "arc:E", Kind.PLAY)),
            Map.entry("menu.options", new Spec(0, "arc:F", Kind.UTILITY)),
            Map.entry("options.language", new Spec(10, "arc:4", Kind.UTILITY)),
            Map.entry("options.accessibility", new Spec(20, "arc:P", Kind.UTILITY)),
            Map.entry("menu.online", new Spec(30, "arc:H", Kind.UTILITY)),
            Map.entry("menu.quit", new Spec(100, "arc:M", Kind.UTILITY)));
    private final Anim opening = new Anim();
    private final List<Entry> entries = new ArrayList<>();
    private final Screen screen;
    private float u, opacity;
    private int colX, colTop, itemW, itemH, brandH;
    private long openedAt = System.currentTimeMillis();

    private float stage(int n) {
        float t = (System.currentTimeMillis() - openedAt - n * 55L) / 380f;
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private Supplier<PlayerSkin> skin;
    private static boolean hintClosed;

    private Entry hintClose;
    private int hintX, hintY, hintW, hintH;

    public FrontEndMenu(Screen screen, List<AbstractButton> originals, Consumer<Button> add) {
        this.screen = screen;
        opening.set("open", 0);
        for (AbstractButton original : originals) {
            String key = original.getMessage().getContents() instanceof TranslatableContents c ? c.getKey() : "";
            boolean credits = key.equals("title.credits")
                    || original.getMessage().getString().contains("Mojang");
            Spec spec = credits
                    ? new Spec(0, "info", Kind.FOOTER)
                    : SPECS.getOrDefault(key, new Spec(50, "wand-sparkles", Kind.CLIENT));
            Entry entry = new Entry(original.getMessage(), spec, original::onPress);
            entry.active = original.active;
            entries.add(entry);
        }
        entries.add(new Entry(
                Component.literal("Arcanic Social"), new Spec(40, "players", Kind.CLIENT), i -> Minecraft.getInstance()
                        .setScreen(new SocialScreen(screen))));
        entries.add(new Entry(
                Component.literal(Lang.tr("Аккаунты")),
                new Spec(0, "user-round", Kind.ACCOUNT),
                i -> Minecraft.getInstance().setScreen(new AccountManagerScreen(screen))));
        if (!Sync.signedIn() && !hintClosed) {
            hintClose = new Entry(Component.literal(Lang.tr("Закрыть")), new Spec(0, "x", Kind.HINT), i -> {
                hintClosed = true;
                hintClose.visible = false;
            });
            entries.add(hintClose);
        }
        entries.sort(Comparator.comparing((Entry e) -> e.spec.kind).thenComparingInt(e -> e.spec.rank));
        layout();
        entries.forEach(add);
    }

    private int s(float value) {
        return Math.max(1, Math.round(value * u));
    }

    private List<Entry> group(Kind kind) {
        return entries.stream().filter(e -> e.spec.kind == kind).toList();
    }

    private void place(Entry e, int xx, int yy, int ww, int hh) {
        int gs = Px.guiScale();
        e.setX(Math.round(xx / (float) gs));
        e.setY(Math.round(yy / (float) gs));
        e.setWidth(Math.max(1, Math.round(ww / (float) gs)));
        e.setHeight(Math.max(1, Math.round(hh / (float) gs)));
    }

    private void layout() {
        int sw = Px.width(),
                sh = Px.height(),
                items = group(Kind.PLAY).size() + group(Kind.CLIENT).size();
        u = Math.max(.45f, Math.min(1.35f, Math.min(sh / 900f, sw / 1440f)));
        brandH = s(46);
        itemH = s(50);
        itemW = s(500);
        int step = itemH + s(10), colH = brandH + s(64) + items * step - s(10) + s(64) + s(50);
        colX = (sw - itemW) / 2;
        colTop = (sh - colH) / 2 - s(10);
        int row = colTop + brandH + s(64);
        for (Entry e : group(Kind.PLAY)) {
            place(e, colX, row, itemW, itemH);
            row += step;
        }
        for (Entry e : group(Kind.CLIENT)) {
            place(e, colX, row, itemW, itemH);
            row += step;
        }
        List<Entry> tools = group(Kind.UTILITY);
        int sq = s(50), gap = s(12), left = (sw - (tools.size() * sq + (tools.size() - 1) * gap)) / 2;
        row += s(64) - s(10);
        for (Entry e : tools) {
            place(e, left, row, sq, sq);
            left += sq + gap;
        }
        for (Entry e : group(Kind.ACCOUNT)) place(e, s(24), s(22), s(200), s(36));
        hintW = s(400);
        hintH = s(142);
        hintX = sw - s(24) - hintW;
        hintY = s(22);
        if (hintClose != null) place(hintClose, hintX + hintW - s(36), hintY + s(12), s(24), s(24));
        List<Entry> footer = group(Kind.FOOTER);
        for (int i = 0; i < footer.size(); i++) place(footer.get(i), sw - s(24) - s(220), sh - s(38), s(220), s(20));
    }

    public void render(GuiGraphics g, int mx, int my, float delta) {
        opening.frame();
        opacity = opening.to("open", 1, 8);
        layout();
        Px.begin(g);
        float old = Px.fade;
        MenuBackdrop.draw(g);
        g.nextStratum();
        int sw = Px.width(), sh = Px.height();
        Px.rect(g, 0, 0, sw, sh, 0x38060711);
        g.fillGradient(0, sh - s(160), sw, sh, 0x00080911, 0x80080911);
        g.nextStratum();
        // the mark, centred: the logo and the name set like a word
        float k = stage(0);
        Px.fade = old * opacity * k;
        int ls = brandH,
                fs = s(30),
                w1 = Px.textWidth("Arcanic", Weight.BOLD, fs),
                w2 = Px.textWidth(" Visuals", Weight.MEDIUM, fs);
        int total = ls + s(16) + w1 + w2, bx = (sw - total) / 2, by = colTop + Math.round(s(10) * (1 - k));
        BrandLogo.idle(g, bx, by, ls);
        int tx = bx + ls + s(16), ty = by + (ls - fs) / 2;
        Px.text(g, "Arcanic", tx, ty, Weight.BOLD, fs, 0xFFF4F1F8);
        Px.text(g, " Visuals", tx + w1, ty, Weight.MEDIUM, fs, 0xFF9890A6);
        Px.fade = old * opacity;
        Px.text(g, "Arcanic Visuals 1.6.7", s(24), sh - s(38), Weight.MEDIUM, s(12), 0xFF7A7388);
        if (hintClose != null && hintClose.visible) accountHint(g, stage(7));
        Px.fade = old;
        Px.end(g);
        g.nextStratum();
        for (Entry e : entries) {
            e.render(g, mx, my, delta);
            if (e.active && e.isHoveredOrFocused()) g.requestCursor(CursorTypes.POINTING_HAND);
        }
        g.nextStratum();
        HudRenderer.menuOverlay(g);
    }

    private void accountHint(GuiGraphics g, float k) {
        float old = Px.fade;
        Px.fade = old * k;
        int acc = Theme.accent() | 0xFF000000, acc2 = Theme.accent2() | 0xFF000000;
        int x = hintX + Math.round(s(16) * (1 - k)), y = hintY, w = hintW, h = hintH;
        Px.round(g, x, y, w, h, s(12), 0xE6141220);
        Px.outline(g, x, y, w, h, s(12), Px.a(acc, 110));
        Px.circle(g, x + s(32), y + s(34), s(17), Px.a(acc, 60));
        Px.icon(g, "sparkles", x + s(32) - s(9), y + s(34) - s(9), s(18), acc2);
        int tx = x + s(60), tw = w - s(60) - s(44);
        Px.text(g, Lang.tr("Косметика и синхронизация"), tx, y + s(20), Weight.SEMIBOLD, s(15), 0xFFF4F1F8);
        String body = Lang.tr("Войди в аккаунт Arcanic в лаунчере — другие игроки увидят твои плащи, крылья и"
                + " эмоции, а ты увидишь их.");
        int ly = y + s(46);
        for (String line : wrap(body, Weight.MEDIUM, s(12), tw + s(24))) {
            Px.text(g, line, tx, ly, Weight.MEDIUM, s(12), 0xFFA79FB8);
            ly += s(17);
        }
        Px.icon(g, "user", tx, y + h - s(30), s(14), acc2);
        Px.text(
                g,
                Lang.tr("Войти можно в «Arcanic Social» в меню"),
                tx + s(20),
                y + h - s(29),
                Weight.MEDIUM,
                s(12),
                Px.mix(acc2, 0xFFFFFFFF, .25f));
        Px.fade = old;
    }

    private static List<String> wrap(String text, Weight weight, int size, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && Px.textWidth(next, weight, size) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else line = new StringBuilder(next);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    public static void logo(GuiGraphics g, int cx, int cy, int r) {
        BrandLogo.idle(g, cx - r, cy - r, 2 * r);
    }

    private final class Entry extends Button {
        private final Spec spec;
        private final Anim hover = new Anim();
        private final Consumer<InputWithModifiers> action;

        Entry(Component label, Spec spec, Consumer<InputWithModifiers> action) {
            super(0, 0, 1, 1, label, b -> {}, DEFAULT_NARRATION);
            this.spec = spec;
            this.action = action;
            hover.set("hover", 0);
            if (spec.kind == Kind.UTILITY || spec.kind == Kind.BACKGROUND || spec.kind == Kind.FOOTER)
                setTooltip(Tooltip.create(Lang.component(label)));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.accept(input);
        }

        @Override
        protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
            hover.frame();
            float v = hover.to("hover", active && isHoveredOrFocused() ? 1 : 0, 9);
            int gs = Px.guiScale(), xx = getX() * gs, yy = getY() * gs, ww = getWidth() * gs, hh = getHeight() * gs;
            Px.begin(g);
            float old = Px.fade;
            Px.fade = opacity * (active ? 1 : .35f);
            int order = spec.kind == Kind.PLAY
                    ? 1 + spec.rank / 10
                    : spec.kind == Kind.CLIENT
                            ? 4
                            : spec.kind == Kind.UTILITY ? 5 + spec.rank / 10 : spec.kind == Kind.HINT ? 7 : 1;
            float k = stage(order);
            Px.fade = old * opacity * (active ? 1 : .4f) * k;
            g.pose().pushMatrix();
            g.pose().translate(0, Math.round(s(10) * (1 - k)));
            int acc = Theme.accent() | 0xFF000000, acc2 = Theme.accent2() | 0xFF000000;
            if (spec.kind == Kind.HINT) {
                Px.circle(g, xx + ww / 2, yy + hh / 2, ww / 2, Px.a(0xFFFFFFFF, Math.round(28 * v)));
                int is = s(12);
                Px.icon(g, "x", xx + (ww - is) / 2, yy + (hh - is) / 2, is, Px.mix(0xFF8C85A0, 0xFFF4F1F8, v));
            } else if (spec.kind == Kind.FOOTER) {
                Px.textRight(
                        g,
                        Lang.tr("© Mojang AB · Minecraft"),
                        xx + ww,
                        yy + s(4),
                        Weight.MEDIUM,
                        s(10),
                        Px.mix(0xFF6F687C, 0xFFCEC1E1, v));
            } else if (spec.kind == Kind.UTILITY) {
                Px.round(g, xx, yy, ww, hh, s(9), Px.mix(0xA6141220, 0xC81E1B2C, v));
                int is = s(19);
                SectionIcons.draw(
                        g, spec.icon, xx + (ww - is) / 2, yy + (hh - is) / 2, is, Px.mix(0xFF8C85A0, 0xFFF4F1F8, v));
            } else if (spec.kind == Kind.ACCOUNT) {
                // the profile chip in the corner: your skin's face and your name
                var mc = Minecraft.getInstance();
                if (skin == null) skin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
                int fsz = Math.max(8, s(20) / 8 * 8), fx = xx + s(9), fy = yy + (hh - fsz) / 2, nx = fx + fsz + s(9);
                String name = Px.ellipsize(mc.getUser().getName(), Weight.SEMIBOLD, s(13), ww - fsz - s(30));
                int cw = Math.min(ww, nx + Px.textWidth(name, Weight.SEMIBOLD, s(13)) + s(12) - xx);
                Px.round(g, xx, yy, cw, hh, s(8), Px.mix(0xA6141220, 0xC81E1B2C, v));
                PlayerFaceRenderer.draw(g, skin.get(), fx, fy, fsz, (Math.round(255 * Px.fade) << 24) | 0xFFFFFF);
                Px.text(g, name, nx, yy + (hh - s(13)) / 2, Weight.SEMIBOLD, s(13), Px.mix(0xFFD9D4E2, 0xFFFFFFFF, v));
            } else {
                // a calm flat bar: the icon and the word in the middle; the client's own entry is in the accent
                boolean own = spec.kind == Kind.CLIENT;
                int plate = own ? Px.mix(Px.a(acc, 64), Px.a(acc, 96), v) : Px.mix(0xA6141220, 0xC81E1B2C, v);
                Px.round(g, xx, yy, ww, hh, s(9), plate);
                String label = Lang.tr(Lang.component(getMessage()).getString());
                String icon = spec.kind == Kind.PLAY ? (spec.rank < 10 ? "user" : "a.players") : spec.icon;
                int fs = s(16),
                        is = s(18),
                        gap = s(10),
                        lw = Px.textWidth(label, Weight.MEDIUM, fs),
                        cx = xx + (ww - (is + gap + lw)) / 2;
                int tc = own
                        ? Px.mix(Px.mix(acc2, 0xFFFFFFFF, .1f), 0xFFFFFFFF, v * .6f)
                        : Px.mix(0xFF9D96AE, 0xFFF4F1F8, v);
                int ic = own ? tc : Px.mix(0xFF8C85A0, acc2, v);
                Px.icon(g, icon, cx, yy + (hh - is) / 2, is, ic);
                Px.text(g, label, cx + is + gap, yy + (hh - fs) / 2, Weight.MEDIUM, fs, tc);
            }
            g.pose().popMatrix();
            Px.fade = old;
            Px.end(g);
        }
    }
}
