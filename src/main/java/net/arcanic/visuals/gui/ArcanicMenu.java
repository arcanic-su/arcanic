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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.arcanic.visuals.core.*;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.hud.HudRenderer;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.BrandLogo;
import net.arcanic.visuals.render.Frost;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** The client menu: a header with the sections and the search, the modules in two columns and a dock of pages. */
public final class ArcanicMenu extends Screen {
    private enum Page {
        MODULES,
        WARDROBE,
        PRESETS,
        SETTINGS
    }

    private static final Category[] TABS = {
        Category.COMBAT, Category.PLAYERS, Category.WORLD, Category.EFFECTS, Category.HANDS, Category.HUD
    };
    static final String[] WARD = {"Плащи", "Ножи", "Головные уборы", "Рюкзаки", "Крылья", "Питомцы"};
    private static final String[] WARD_ICONS = {"w.scroll", "w.sword", "w.wizard-hat", "w.bag", "w.leaf", "w.hand-heart"
    };

    private static Module wardMaster(int i) {
        return switch (i) {
            case 0 -> Modules.CAPES;
            case 1 -> Modules.KNIVES;
            case 2 -> Modules.HEADWEAR;
            case 3 -> Modules.BACKPACKS;
            case 4 -> Modules.WINGS;
            case 5 -> Modules.COMPANIONS;
            default -> null;
        };
    }

    private static Category category = Category.COMBAT;
    private static Page page = Page.MODULES;
    static int wardrobe = 0;

    private final Anim anim = new Anim();
    private final Map<String, double[]> tweens = new HashMap<>(); // key -> {from, to, start}
    private float u = 1;
    double mx, my;
    long now, openedAt = System.currentTimeMillis(), contentAt = System.currentTimeMillis();
    private boolean closing;
    private String search = "";
    private boolean searchFocused;
    Module popMod, popShown;
    int ancX, ancY, ancW, ancH, pmX, pmY, pmW, pmH;
    private final Map<String, Float> scroll = new HashMap<>();
    private Setting.Mode pop;
    private int popX, popY, popW;
    private long popAt;
    private ColourPicker picker;
    Module binding;
    String hot, lastHot;
    public static long devNanos, devFrames, devMax;

    private interface Drag {
        void to(double x, double y);
    }

    private record Region(int x, int y, int w, int h, Runnable click, Runnable alt, Drag drag, String id) {
        boolean in(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private final List<Region> regions = new ArrayList<>();
    private Region dragging;

    int px, py, pw, ph, cx0, cw0, top, bottom;

    final WardrobePage wardrobePage = new WardrobePage(this);

    final PresetsPage presetsPage = new PresetsPage(this);

    final PrefsPage prefsPage = new PrefsPage(this);

    final ModulePopup popup = new ModulePopup(this);

    public ArcanicMenu() {
        super(Component.literal("Arcanic"));
        UiSound.play("open");
        Toasts.menu(true);
        if (category == Category.COSMETICS) category = Category.COMBAT;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    int S(double v) {
        return (int) Math.round(v * u);
    }

    static Theme.Palette T() {
        return Theme.p();
    }

    static boolean light() {
        return Theme.p().light();
    }

    static int acc() {
        return Theme.accent() | 0xFF000000;
    }

    static int accText() {
        return light() ? Theme.accent() : Theme.accent2();
    }

    int lt(int lightColour, int darkColour) {
        return light() ? lightColour : darkColour;
    }

    private int hair() {
        return Px.a(T().text(), light() ? 22 : 14);
    }

    private int inset(boolean hv) {
        return light() ? Px.a(0x1B1530, hv ? 14 : 8) : Px.mix(T().bg() | 0xFF000000, 0xFF000000, hv ? .05f : .22f);
    }

    int plate() {
        return light() ? 0xFFFFFFFF : Px.mix(T().bg() | 0xFF000000, 0xFFFFFFFF, .025f);
    }

    boolean hover(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    float an(String k, float target, float speed) {
        return anim.to(k, target, speed);
    }

    static float ease(float t) {
        return Anim.easeOutCubic(Math.max(0, Math.min(1, t)));
    }

    boolean blocked() {
        return pop != null || picker != null;
    }

    boolean under() {
        return blocked();
    }

    private void region(int x, int y, int w, int h, Runnable click) {
        regions.add(new Region(x, y, w, h, click, null, null, null));
    }

    void clipped(int x, int y, int w, int h, float sc, int t, int b, Runnable click, Runnable alt) {
        int y0 = (int) Math.max(t, y - sc), y1 = (int) Math.min(b, y - sc + h);
        if (y1 > y0) regions.add(new Region(x, y0, w, y1 - y0, click, alt, null, null));
    }

    static void glyph(GuiGraphics g, String name, int x, int y, int size, int line) {
        Px.icon(g, Px.hasIcon("a." + name) ? "a." + name : name, x, y, size, line);
    }

    float tween(String k, float target, float ms) {
        double[] t = tweens.get(k);
        if (t == null || !Theme.animations) {
            tweens.put(k, new double[] {target, target, now});
            return target;
        }
        double dur = ms / Math.max(.2, Anim.speedMul);
        double p = Math.min(1, (now - t[2]) / dur), cur = t[0] + (t[1] - t[0]) * inOut(p);
        if (t[1] != target) {
            t[0] = cur;
            t[1] = target;
            t[2] = now;
        }
        return (float) cur;
    }

    private static double inOut(double p) {
        return p < .5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
    }

    void hot(String key) {
        hot = key;
    }

    void toggle(GuiGraphics g, int x, int y, int w, int h, float on, float hv) {
        boolean lt = light();
        int th = S(20) / 2 * 2, tw = Math.round(th * 1.5f) / 2 * 2, tx = x + w - tw, ty = y + (h - th) / 2, r = th / 2;
        int off = lt ? Px.mix(0xFFE4E2EB, 0xFFD9D6E2, hv) : Px.a(0xFFFFFF, 24 + Math.round(14 * hv));
        Px.round(g, tx, ty, tw, th, r, Px.mix(off, acc(), on));
        int m = Math.max(2, Math.round(th * .15f)), ks = (th - m * 2) / 2 * 2;
        int kx = tx + m + Math.round((tw - m * 2 - ks) * on);
        Px.round(g, kx, ty + m, ks, ks, ks / 2, Px.mix(lt ? 0xFFFFFFFF : 0xFF757D8A, lt ? 0xFFFFFFFF : 0xFF12141C, on));
    }

    private int keycap(GuiGraphics g, String t, int x, int y, int fs) {
        int w = Px.textWidth(t, Weight.SEMIBOLD, fs) + S(12), h = fs + S(9);
        Px.round(g, x, y, w, h, S(6), Px.a(T().text(), light() ? 10 : 9));
        Px.outline(g, x, y, w, h, S(6), Px.a(T().text(), light() ? 26 : 20));
        Px.text(g, t, x + S(6), y + (h - fs) / 2, Weight.SEMIBOLD, fs, T().muted());
        return w;
    }

    private int keycapW(String t, int fs) {
        return Px.textWidth(t, Weight.SEMIBOLD, fs) + S(12);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        long t0 = System.nanoTime();
        draw(g, mouseX, mouseY, pt);
        long dt = System.nanoTime() - t0;
        devNanos += dt;
        devFrames++;
        devMax = Math.max(devMax, dt);
    }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        Modules.PREFS.sync();
        anim.frame();
        regions.clear();
        now = System.currentTimeMillis();
        mx = Px.px(mouseX);
        my = Px.px(mouseY);
        int sw = Px.width(), sh = Px.height();
        u = (float) Math.max(.45, Math.min(2.2, Math.min(sh / 1080.0, sw / 1060.0) * Theme.menuScale * .86));
        float open = an("open", closing ? 0 : 1, closing ? 18 : 8);
        if (closing && open < .02f) {
            super.onClose();
            return;
        }
        float e = ease(open);

        pw = Math.min(S(1010), sw - S(80));
        ph = Math.min(S(640), sh - S(210));
        px = (sw - pw) / 2;
        py = (sh - ph - S(28) + S(78)) / 2;

        Px.begin(g);
        float keep = Px.fade;
        Px.rect(g, 0, 0, sw, sh, Px.a(0x05040A, (int) (255 * Modules.PREFS.dim.get() / 100.0 * open)));
        Px.fade = keep * Math.min(1, open * 1.3f);
        brand(g, sw / 2, py - S(58));
        g.nextStratum();
        g.pose().pushMatrix();
        g.pose().translate(0, Math.round((1 - e) * S(18)));
        panel(g);
        g.nextStratum();
        dock(g, sw / 2, py + ph);
        g.pose().popMatrix();
        g.nextStratum();
        popup.modulePopup(g);
        g.nextStratum();
        if (pop != null) popover(g);
        Px.fade = keep;
        if (picker != null) {
            g.nextStratum();
            picker.render(g, Px.px(mouseX), Px.px(mouseY));
            if (picker.finished()) picker = null;
        }
        Px.end(g);
        HudRenderer.menuOverlay(g);
        // a soft tick when the cursor reaches something new
        if (hot != null && !hot.equals(lastHot) && now - openedAt > 250 && !closing)
            UiSound.play("hover", 1 + (hot.hashCode() % 7) * .015f, 1);
        lastHot = hot;
        hot = null;
    }

    private void brand(GuiGraphics g, int cx, int y) {
        float t = (now - openedAt) / 1000f, a = ease(t / .35f);
        int ls = S(28), fs = S(21), ts = S(21), gap = S(10);
        String w1 = "Arcanic", w2 = " Visuals";
        int w1w = Px.textWidth(w1, Weight.BOLD, fs), w2w = Px.textWidth(w2, Weight.MEDIUM, ts);
        int total = ls + gap + w1w + w2w, x = cx - total / 2;
        float k = Px.fade;
        Px.fade = k * a;
        int dy = Math.round((1 - a) * S(6));
        // the plate under the mark
        int px0 = x - S(18), py0 = y + dy - S(10), pw0 = total + S(38), ph0 = ls + S(20);
        boolean lt = light();
        Px.shadow(g, px0, py0 + S(4), pw0, ph0, S(16), Px.a(0, lt ? 30 : 90));
        boolean frost = !lt && false;
        if (frost) Frost.backdrop(g, px0, py0, pw0, ph0, ph0 / 2);
        Px.round(g, px0, py0, pw0, ph0, ph0 / 2, lt ? 0xF2FFFFFF : frost ? 0xC80C0D12 : 0xF00C0D12);
        Px.glow(g, x - S(8), py0 + S(2), ls + S(16), ph0 - S(4), Px.a(Theme.accent(), lt ? 50 : 90));
        Px.outline(g, px0, py0, pw0, ph0, ph0 / 2, lt ? Px.a(T().text(), 24) : 0x1CFFFFFF);
        int hl = Math.max(1, S(1)), half = (pw0 - ph0) / 2;
        Px.hgrad(g, px0 + ph0 / 2, py0, half, hl, Px.a(Theme.accent2(), 0), Px.a(Theme.accent2(), lt ? 70 : 130));
        Px.hgrad(
                g, px0 + ph0 / 2 + half, py0, half, hl, Px.a(Theme.accent2(), lt ? 70 : 130), Px.a(Theme.accent2(), 0));
        BrandLogo.idle(g, x, y + dy, ls);
        int ty = y + dy + (ls - fs) / 2;
        Px.text(g, w1, x + ls + gap, ty, Weight.BOLD, fs, lt ? 0xFF15151A : 0xFFF4F6FA);
        Px.text(g, w2, x + ls + gap + w1w, ty, Weight.MEDIUM, ts, lt ? 0xFF696978 : 0xFF8F97A4);
        Px.fade = k;
    }

    private void panel(GuiGraphics g) {
        boolean lt = light();
        int R = S(12);
        Px.shadow(g, px, py + S(16), pw, ph, S(46), Px.a(0, lt ? 40 : 160));
        boolean frost = !lt && false;
        if (frost) Frost.backdrop(g, px, py, pw, ph, R); // frosted glass under the panel
        Px.round(g, px, py, pw, ph, R, lt ? 0xF8F8F7FA : (T().bg() & 0xFFFFFF) | (frost ? 0xD4000000 : 0xF2000000));
        Px.outline(g, px, py, pw, ph, R, Px.a(T().text(), lt ? 28 : 20));
        g.nextStratum();
        int pad = S(36), hy = py + S(26), hh = S(40);
        cx0 = px + pad;
        cw0 = pw - pad * 2;
        header(g, cx0, hy, cw0, hh);
        int lineY = hy + hh + S(18);
        Px.rect(g, cx0, lineY, cw0, Math.max(1, S(1)), hair());
        top = lineY + S(18);
        bottom = py + ph - S(44);
        g.nextStratum();
        switch (page) {
            case MODULES -> rows(g, visible(), "c:" + (search.isEmpty() ? category : "search"));
            case WARDROBE -> wardrobePage.wardrobe(g);
            case PRESETS -> presetsPage.presets(g);
            case SETTINGS -> prefsPage.prefs(g);
        }
    }

    // segmented sections, search, theme
    private void header(GuiGraphics g, int x, int y, int w, int h) {
        // the sections: a strip with a solid plate that slides under the chosen one
        String[] labels;
        int active;
        if (page == Page.MODULES) {
            labels = new String[TABS.length];
            for (int i = 0; i < TABS.length; i++) labels[i] = Lang.tr(TABS[i].title);
            active = -1;
            if (search.isEmpty()) for (int i = 0; i < TABS.length; i++) if (TABS[i] == category) active = i;
        } else if (page == Page.WARDROBE) {
            labels = new String[WARD.length];
            for (int i = 0; i < WARD.length; i++) labels[i] = Lang.tr(WARD[i]);
            active = wardrobe;
        } else if (page == Page.PRESETS) {
            labels = new String[] {Lang.tr("Все"), Lang.tr("Локальные"), Lang.tr("Облачные")};
            active = presetsPage.tab;
        } else {
            labels = new String[] {Lang.tr("Настройки меню")};
            active = 0;
        }
        // navigation: an icon and a label per section, a pill under the chosen one
        String[] icons = new String[labels.length];
        for (int i = 0; i < labels.length; i++)
            icons[i] = page == Page.MODULES
                    ? TABS[i].icon
                    : page == Page.WARDROBE
                            ? WARD_ICONS[i]
                            : page == Page.PRESETS ? PresetsPage.TAB_ICONS[i] : "w.sliders";
        boolean many = labels.length > 6,
                crowd = labels.length > 8; // the wardrobe has many sections: they sit tighter the more there are
        int fs = S(crowd ? 12 : many ? 13 : 14),
                is = S(crowd ? 13 : many ? 14 : 15),
                ip = S(crowd ? 6 : many ? 9 : 12),
                gap = S(crowd ? 0 : many ? 2 : 4),
                tg = S(crowd ? 5 : 8),
                sx = x - ip,
                ph2 = S(32) / 2 * 2,
                py2 = y + (h - ph2) / 2;
        int[] xs = new int[labels.length], ws = new int[labels.length];
        int tx = sx;
        for (int i = 0; i < labels.length; i++) {
            ws[i] = ip + is + tg + Px.textWidth(labels[i], Weight.SEMIBOLD, fs) + ip;
            xs[i] = tx;
            tx += ws[i] + gap;
        }
        int stripW = tx - gap - sx;
        if (active >= 0) {
            float plx = tween("plateX:" + page, xs[active], 260), plw = tween("plateW:" + page, ws[active], 260);
            Px.round(g, Math.round(plx), py2, Math.round(plw), ph2, S(11), light() ? Px.a(0x1B1530, 14) : 0xA01A1C24);
            Px.outline(g, Math.round(plx), py2, Math.round(plw), ph2, S(11), light() ? Px.a(0x1B1530, 20) : 0x14FFFFFF);
        }
        for (int i = 0; i < labels.length; i++) {
            boolean on = i == active, hv = !under() && hover(xs[i], y, ws[i], h);
            if (hv) hot("tab:" + page + i);
            float hk = an("tab:" + page + i, hv ? 1 : 0, 18), ak = tween("tabA:" + page + i, on ? 1 : 0, 220);
            int c = Px.mix(Px.mix(lt(T().muted(), 0xFFB0B8C4), T().text(), hk * .6f), T().text(), ak);
            glyph(g, icons[i], xs[i] + ip, py2 + (ph2 - is) / 2, is, Px.mix(c, accText(), ak * .9f));
            Px.text(g, labels[i], xs[i] + ip + is + tg, py2 + (ph2 - fs) / 2, Weight.SEMIBOLD, fs, c);
            int idx = i;
            if (!under() && labels.length > 1) region(xs[i], y, ws[i], h, () -> tabClick(idx));
        }

        // right side: the theme button, then the search (or the page's own action)
        int bs = h, bx = x + w - bs;
        themeButton(g, bx, y, bs);
        int sw = Math.max(S(170), Math.min(S(250), bx - S(12) - (sx + stripW + S(18)))), spx = bx - S(10) - sw;
        if (page == Page.MODULES) searchPill(g, spx, y, sw, h);
        else if (page == Page.WARDROBE && wardMaster(wardrobe) != null) {
            Module master = wardMaster(wardrobe);
            float on = tween("on:" + master.id, master.enabled() ? 1 : 0, 200);
            int tw2 = S(42), th2 = S(24), tgx = bx - S(16) - tw2;
            boolean hv = !under() && hover(tgx - S(130), y, S(130) + tw2, h);
            if (hv) hot("master");
            toggle(g, tgx, y + (h - th2) / 2, tw2, th2, on, hv ? 1 : 0);
            String lbl = Lang.tr(
                    switch (wardrobe) {
                        case 0 -> master.enabled() ? "Плащ включён" : "Плащ выключен";
                        case 1 -> master.enabled() ? "Нож включён" : "Нож выключен";
                        case 2 -> master.enabled() ? "Убор надет" : "Убор снят";
                        case 3 -> master.enabled() ? "Рюкзак надет" : "Рюкзак снят";
                        case 4 -> master.enabled() ? "Крылья надеты" : "Крылья сняты";
                        default -> master.enabled() ? "Питомец включён" : "Питомец выключен";
                    });
            int lw = Px.textWidth(lbl, Weight.MEDIUM, S(13));
            if (tgx - S(12) - lw < sx + stripW + S(10)) lw = 0; // no room beside the sections: the switch alone
            else
                Px.text(
                        g,
                        lbl,
                        tgx - S(12) - lw,
                        y + (h - S(13)) / 2,
                        Weight.MEDIUM,
                        S(13),
                        Px.mix(T().muted(), T().text(), on));
            if (!under())
                region(tgx - S(12) - lw, y, S(12) + lw + tw2, h, () -> {
                    master.toggle();
                    UiSound.play(master.enabled() ? "charge" : "drain");
                    Toasts.module(master, false);
                    Config.dirty();
                });
        } else if (page == Page.PRESETS) {
            String t = Lang.tr("Создать пресет");
            int bw = S(16) + S(8) + Px.textWidth(t, Weight.SEMIBOLD, S(13)) + S(34), bx2 = bx - S(10) - bw;
            boolean hv = !under() && hover(bx2, y, bw, h);
            if (hv) hot("save");
            float hk = an("saveBtn", hv ? 1 : 0, 18);
            Px.round(g, bx2, y, bw, h, h / 2, Px.mix(acc(), 0xFFFFFFFF, .08f * hk));
            glyph(g, "plus", bx2 + S(17), y + (h - S(16)) / 2, S(16), 0xFFFFFFFF);
            Px.text(g, t, bx2 + S(41), y + (h - S(13)) / 2, Weight.SEMIBOLD, S(13), 0xFFFFFFFF);
            if (!under())
                region(bx2, y, bw, h, () -> {
                    UiSound.play("click");
                    presetsPage.create();
                });
            // a preset someone shared: its code goes in here
            String it = Lang.tr("Вставить код");
            int iw = Px.textWidth(it, Weight.SEMIBOLD, S(13)) + S(34), ix = bx2 - S(8) - iw;
            boolean ihv = !under() && hover(ix, y, iw, h);
            if (ihv) hot("import");
            float ik = an("importBtn", ihv ? 1 : 0, 18);
            Px.round(g, ix, y, iw, h, h / 2, Px.mix(Px.a(T().text(), light() ? 14 : 12), Px.a(T().text(), 26), ik));
            Px.textCenter(g, it, ix + iw / 2, y + (h - S(13)) / 2, Weight.SEMIBOLD, S(13), T().text());
            if (!under())
                region(ix, y, iw, h, () -> {
                    UiSound.play("click");
                    presetsPage.paste();
                });
        }
    }

    private void tabClick(int i) {
        if (page == Page.MODULES) {
            if (category != TABS[i] || !search.isEmpty()) {
                category = TABS[i];
                search = "";
                searchFocused = false;
                content("tab");
            }
        } else if (page == Page.WARDROBE && wardrobe != i) {
            wardrobe = i;
            content("tab");
        } else if (page == Page.PRESETS && presetsPage.tab != i) {
            presetsPage.tab = i;
            content("tab");
        }
    }

    private void content(String sound) {
        contentAt = now;
        popMod = null;
        pop = null;
        if (sound != null) UiSound.play(sound);
    }

    private void searchPill(GuiGraphics g, int x, int y, int w, int h) {
        boolean over = !under() && hover(x, y, w, h);
        if (over) hot("search");
        float f = tween("sfocus", searchFocused ? 1 : 0, 180), hv = an("shover", over ? 1 : 0, 18);
        Px.round(g, x, y, w, h, S(7), light() ? Px.a(0x1B1530, (int) (8 + 5 * hv)) : inset(hv > .5f));
        Px.outline(
                g,
                x,
                y,
                w,
                h,
                S(7),
                Px.mix(Px.a(T().text(), light() ? (int) (16 + 10 * hv) : (int) (12 + 8 * hv)), acc(), f));
        glyph(g, "search-s", x + S(15), y + (h - S(15)) / 2, S(15), Px.mix(T().dim(), accText(), f));
        boolean empty = search.isEmpty();
        Px.text(
                g,
                Px.ellipsize(empty ? Lang.tr("Поиск") : search, Weight.MEDIUM, S(14), w - S(80)),
                x + S(40),
                y + (h - S(14)) / 2,
                Weight.MEDIUM,
                S(14),
                empty ? T().dim() : T().text());
        if (searchFocused && (now - openedAt) / 530 % 2 == 0)
            Px.rect(
                    g,
                    x + S(40) + (empty ? 0 : Px.textWidth(search, Weight.MEDIUM, S(14))) + S(1),
                    y + S(11),
                    Math.max(1, S(1.5)),
                    h - S(22),
                    accText());
        if (!empty) {
            boolean xh = !under() && hover(x + w - S(34), y, S(34), h);
            glyph(g, "x-s", x + w - S(26), y + (h - S(13)) / 2, S(13), xh ? T().text() : T().muted());
            if (!under())
                region(x + w - S(34), y, S(34), h, () -> {
                    search = "";
                    content("click");
                });
        } else if (!searchFocused)
            keycap(g, "Ctrl F", x + w - S(12) - keycapW("Ctrl F", S(10)), y + (h - S(19)) / 2, S(10));
        if (!under())
            region(x, y, w - (empty ? 0 : S(34)), h, () -> {
                searchFocused = true;
                UiSound.play("click", 1.2f, .6f);
            });
    }

    private void themeButton(GuiGraphics g, int x, int y, int s) {
        boolean hv = !under() && hover(x, y, s, s);
        if (hv) hot("theme");
        float hk = an("themeH", hv ? 1 : 0, 18), k = tween("themeK", Theme.palette == Theme.LIGHT ? 1 : 0, 420);
        Px.circle(
                g,
                x + s / 2,
                y + s / 2,
                s / 2,
                light() ? Px.a(0x1B1530, (int) (9 + 7 * hk)) : Px.a(0xFFFFFF, (int) (6 + 8 * hk)));
        Px.ring(
                g,
                x + s / 2,
                y + s / 2,
                s / 2,
                Px.a(T().text(), light() ? (int) (16 + 10 * hk) : (int) (12 + 10 * hk)));
        int gs = S(17), c = Px.mix(T().muted(), T().text(), hk);
        for (int i = 0; i < 2; i++) {
            float vis = i == 0 ? 1 - k : k;
            if (vis < .02f) continue;
            float kf = Px.fade;
            Px.fade = kf * vis;
            g.pose().pushMatrix();
            g.pose().translate(x + s / 2f, y + s / 2f);
            if (vis < .999f) {
                g.pose().rotate((float) ((i == 0 ? k : k - 1) * Math.PI * .6));
                float sc = .6f + .4f * vis;
                g.pose().scale(sc, sc);
            }
            glyph(g, i == 0 ? "moon-s" : "sun-s", -gs / 2, -gs / 2, gs, c);
            g.pose().popMatrix();
            Px.fade = kf;
        }
        if (!under())
            region(x, y, s, s, () -> {
                Theme.setPalette(Theme.palette == Theme.LIGHT ? Theme.NIGHT : Theme.LIGHT);
                Config.dirty();
                UiSound.play("select");
            });
    }

    float scrolled(String key, int total, int t, int b) {
        float max = Math.max(0, total - (b - t));
        float want = Math.max(0, Math.min(max, scroll.getOrDefault(key, 0f)));
        scroll.put(key, want);
        return an("sc:" + key, want, 15);
    }

    int baseColour() {
        return light() ? 0xFFF8F8F7 : (T().bg() | 0xFF000000);
    }

    void edges(GuiGraphics g, int x, int w, int t, int b, float sc, int colour) {
        int fh = S(24);
        // over frosted glass the panel is translucent: a softer fade, or it reads as a dark band
        int max = !light() && false ? 110 : 240;
        if (sc > .5f) Px.vgrad(g, x, t, w, fh, Px.a(colour, (int) (Math.min(1, sc / S(24)) * max)), Px.a(colour, 0));
        Px.vgrad(g, x, b - fh, w, fh, Px.a(colour, 0), Px.a(colour, max));
    }

    void scrollbar(GuiGraphics g, int x, int t, int b, int total, float sc, String key) {
        int view = b - t;
        if (total <= view) return;
        int th = Math.max(S(40), view * view / total),
                ty = t + Math.round((view - th) * Math.min(1, sc / (total - view)));
        boolean drag = dragging != null && ("sb:" + key).equals(dragging.id());
        boolean hv = drag || (!blocked() && hover(x - S(8), t, S(20), view));
        float hk = an("sb:" + key, hv ? 1 : 0, 18);
        int wdt = S(3) + Math.round(S(3) * hk);
        Px.round(
                g,
                x + S(2) - wdt / 2,
                ty,
                wdt,
                th,
                wdt / 2,
                light() ? Px.mix(Px.a(T().text(), 46), Px.a(T().text(), 90), hk) : Px.mix(0x30FFFFFF, 0x60FFFFFF, hk));
        int max = total - view;
        if (!blocked())
            regions.add(new Region(
                    x - S(8),
                    t,
                    S(20),
                    view,
                    null,
                    null,
                    (ax, ay) -> {
                        float v = (float) Math.max(0, Math.min(1, (ay - t - th / 2.0) / Math.max(1, view - th))) * max;
                        scroll.put(key, v);
                        anim.set("sc:" + key, v);
                    },
                    "sb:" + key));
    }

    private List<Module> visible() {
        List<Module> pool = search.isEmpty() ? Modules.of(category) : Modules.ALL;
        List<Module> out = new ArrayList<>();
        String q = search.toLowerCase(Locale.ROOT);
        for (Module m : pool)
            if (m != Modules.PREFS && (q.isEmpty() || Lang.matches(m.name, q) || Lang.matches(m.description, q)))
                out.add(m);
        return out;
    }

    private static int n(Module m) {
        int n = 0;
        for (Setting<?> s : m.settings) if (s.visible()) n++;
        return n;
    }

    private static List<Setting<?>> params(Module m) {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> st : m.settings) if (st.visible()) out.add(st);
        return out;
    }

    private int headH() {
        return S(58);
    }

    private int cardH(Module m, List<Setting<?>> ls, int cw) {
        return headH();
    }

    private void rows(GuiGraphics g, List<Module> list, String key) {
        if (list.isEmpty()) {
            float t = ease((now - contentAt) / 300f);
            float k = Px.fade;
            Px.fade = k * t;
            int cy = top + (bottom - top) / 2 - S(46);
            glyph(g, "search-s", cx0 + cw0 / 2 - S(15), cy, S(30), T().dim());
            Px.textCenter(
                    g, Lang.tr("Ничего не найдено"), cx0 + cw0 / 2, cy + S(46), Weight.SEMIBOLD, S(15), T().text());
            Px.textCenter(
                    g, Lang.tr("Попробуй другое слово"), cx0 + cw0 / 2, cy + S(70), Weight.MEDIUM, S(12), T().dim());
            Px.fade = k;
            return;
        }
        compact = true;
        int gap = S(12), cols = cw0 >= S(1100) ? 3 : 2, cw = (cw0 - gap * (cols - 1)) / cols;
        int count = list.size();
        int[] colY = new int[cols], cx = new int[count], cy = new int[count], ch = new int[count];
        List<List<Setting<?>>> ps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Setting<?>> ls = params(list.get(i));
            ps.add(ls);
            int c = 0;
            for (int j = 1; j < cols; j++) if (colY[j] < colY[c]) c = j;
            cx[i] = cx0 + c * (cw + gap);
            cy[i] = top + S(2) + colY[c];
            ch[i] = cardH(list.get(i), ls, cw);
            colY[c] += ch[i] + gap;
        }
        int total = 0;
        for (int v : colY) total = Math.max(total, v);
        total += S(4);
        float sc = scrolled(key, total, top, bottom);
        g.enableScissor(cx0 - S(12), top, cx0 + cw0 + S(12), bottom);
        g.pose().pushMatrix();
        g.pose().translate(0, -Math.round(sc));
        for (int i = 0; i < count; i++) {
            g.nextStratum();
            card(g, list.get(i), ps.get(i), cx[i], cy[i], cw, ch[i], Math.round(sc), i);
        }
        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        compact = false;
        edges(g, cx0 - S(12), cw0 + S(24), top, bottom, sc, baseColour());
        scrollbar(g, cx0 + cw0 + S(14), top, bottom, total, sc, key);
    }

    private void card(GuiGraphics g, Module m, List<Setting<?>> ls, int x, int y, int w, int h, float sc, int i) {
        float sy = y - sc;
        if (popMod == m) {
            ancX = x;
            ancY = Math.round(sy);
            ancW = w;
            ancH = h;
        }
        if (sy + h < top - S(20) || sy > bottom + S(20)) return;
        float app = ease((now - contentAt - Math.min(i, 12) * 22L) / 300f);
        boolean lt = light();
        int pad = S(18), head = headH(), R = S(12);
        boolean hv = !under() && my >= top && my < bottom && hover(x, Math.round(sy), w, head);
        if (hv) hot("card:" + m.id);
        float hk = an("ch:" + m.id, hv ? 1 : 0, 18);
        float on = tween("on:" + m.id, m.enabled() ? 1 : 0, 180);
        float k = Px.fade;
        Px.fade = k * app;
        g.pose().pushMatrix();
        g.pose().translate(0, Math.round((1 - app) * S(8)));
        float sel = tween("rs:" + m.id, popMod == m ? 1 : 0, 200);
        Px.round(
                g,
                x,
                y,
                w,
                h,
                R,
                Px.mix(plate(), lt ? 0xFFFFFFFF : Px.mix(T().bg() | 0xFF000000, 0xFFFFFFFF, .055f), hk));
        Px.outline(g, x, y, w, h, R, Px.mix(lt ? Px.a(T().text(), 22) : 0x18FFFFFF, Px.a(Theme.accent(), 200), sel));

        // header: name and what it does on the left; the key and the checkbox on the right
        int ckS = S(34) / 2 * 2, ckX = x + w - pad - ckS;
        boolean waiting = binding == m;
        String kl = waiting ? Lang.tr("Нажми клавишу") : m.bind >= 0 ? Keys.label(m.bind) : "";
        int kfs = S(11), kh = S(24) / 2 * 2, kmin = S(34) / 2 * 2;
        int kwT = kl.isEmpty() ? kmin : Math.max(kmin, Px.textWidth(kl, Weight.SEMIBOLD, kfs) + S(18));
        int kw = Math.round(tween("kw:" + m.id, kwT, 220)), kx = ckX - S(10) - kw, ky = y + (head - kh) / 2;
        boolean hasP = !ls.isEmpty();
        int gs = Math.round(kh * 1.2f) / 2 * 2, gx = kx - S(6) - gs;
        int textW = (hasP ? gx : kx) - S(10) - (x + pad);
        int nfs = S(15);
        String name = Px.ellipsize(Lang.tr(m.name), Weight.SEMIBOLD, nfs, textW);
        int nameC = Px.mix(Px.mix(T().muted(), T().text(), .55f), T().text(), Math.max(on, hk));
        int ny = y + S(12);
        if (search.isEmpty()) Px.text(g, name, x + pad, ny, Weight.SEMIBOLD, nfs, nameC);
        else nameLit(g, name, x + pad, ny, nfs, nameC);
        String desc = Px.ellipsize(
                search.isEmpty() ? Lang.tr(m.description) : Lang.tr(m.category.title), Weight.MEDIUM, S(12), textW);
        Px.text(g, desc, x + pad, ny + S(20), Weight.MEDIUM, S(12), T().dim());
        boolean khv = hv && hover(kx, Math.round(sy + (ky - y)), kw, kh);
        keyChip(g, kl, kx, ky, kw, kh, kfs, waiting, khv, m.id);
        toggle(g, ckX, y, ckS, head, on, hk);
        if (hasP) {
            // expand button: an outlined pill with a chevron that turns down while the parameters are open
            boolean gh = hv && hover(gx, Math.round(sy + (ky - y)), gs, kh);
            if (gh) hot("gear:" + m.id);
            float gk = an("gear:" + m.id, gh ? 1 : 0, 18), act = Math.max(gk, sel);
            if (act > .01f)
                Px.round(g, gx, ky, gs, kh, kh / 2, Px.a(lt ? 0x1B1530 : 0xFFFFFF, (int) (12 * gk + 6 * sel)));
            Px.outline(
                    g,
                    gx,
                    ky,
                    gs,
                    kh,
                    kh / 2,
                    Px.mix(lt ? Px.a(T().text(), 26) : 0x18FFFFFF, Px.a(Theme.accent(), 190), sel));
            int cs = S(12);
            g.pose().pushMatrix();
            g.pose().translate(gx + gs / 2f, ky + kh / 2f);
            g.pose().rotate((float) (Math.PI / 2 * sel));
            glyph(g, "chev-right", -cs / 2, -cs / 2, cs, Px.mix(lt(T().muted(), 0xFF757D8A), accText(), act));
            g.pose().popMatrix();
        }
        g.pose().popMatrix();
        Px.fade = k;
        if (under() || app < .3f) return;
        Runnable flip = () -> {
            m.toggle();
            if (m.enabled()) pulse.put(m.id, now);
            UiSound.play(m.enabled() ? "charge" : "drain");
            Toasts.module(m, false);
            Config.dirty();
        };
        Runnable params = hasP ? () -> openPopup(m, x, Math.round(sy), w, h) : null;
        clipped(x, y, (hasP ? gx : kx) - S(4) - x, head, sc, top, bottom, flip, params);
        if (hasP) clipped(gx - S(3), ky - S(3), gs + S(6), kh + S(6), sc, top, bottom, params, params);
        clipped(ckX - S(4), y, x + w - ckX + S(4), head, sc, top, bottom, flip, null);
        clipped(
                kx - S(3),
                ky - S(3),
                kw + S(6),
                kh + S(6),
                sc,
                top,
                bottom,
                () -> {
                    binding = binding == m ? null : m;
                    UiSound.play("click");
                },
                () -> {
                    m.bind = -1;
                    binding = null;
                    Config.dirty();
                    UiSound.play("reset");
                });
    }

    final Map<String, Long> pulse = new HashMap<>();

    private void keyChip(
            GuiGraphics g, String t, int x, int y, int w, int h, int fs, boolean waiting, boolean hv, String id) {
        boolean lt = light();
        float wk = tween("kwait:" + id, waiting ? 1 : 0, 200);
        float pulse = (float) (Math.sin(now / 220.0) * .5 + .5) * wk;
        // an outlined pill, filled only on hover or while it waits for a key
        int bg = Px.mix(
                Px.a(lt ? 0x1B1530 : 0xFFFFFF, hv ? 12 : 0),
                Px.a(Theme.accent(), (int) ((lt ? 26 : 38) + 22 * pulse)),
                wk);
        if ((bg >>> 24) != 0) Px.round(g, x, y, w, h, h / 2, bg);
        Px.outline(
                g,
                x,
                y,
                w,
                h,
                h / 2,
                Px.mix(lt ? Px.a(T().text(), 24) : 0x18FFFFFF, Px.a(Theme.accent(), (int) (160 + 90 * pulse)), wk));
        g.enableScissor(x, y, x + w, y + h);
        float kf = Px.fade;
        if (t.isEmpty() || wk > .01f && waiting) {
            if (!waiting) {
                int gs = S(20), gx = x + (w - gs) / 2, gy = y + (h - gs) / 2;
                Px.icon(g, "a.keyboard-s~", gx, gy, gs, Px.a(Theme.accent(), hv ? 120 : 70));
                Px.icon(g, "a.keyboard-s", gx, gy, gs, hv ? T().text() : T().muted());
            } else {
                Px.fade = kf * wk;
                Px.textCenter(g, t, x + w / 2, y + (h - fs) / 2, Weight.SEMIBOLD, fs, accText());
                Px.fade = kf;
            }
        } else
            Px.textCenter(
                    g,
                    t,
                    x + w / 2,
                    y + (h - fs) / 2,
                    Weight.SEMIBOLD,
                    fs,
                    hv ? T().text() : Px.mix(T().muted(), T().text(), .45f));
        g.disableScissor();
    }

    private int nameLit(GuiGraphics g, String name, int x, int y, int fs, int c) {
        int i = search.isEmpty() ? -1 : name.toLowerCase(Locale.ROOT).indexOf(search.toLowerCase(Locale.ROOT));
        if (i < 0) {
            Px.text(g, name, x, y, Weight.MEDIUM, fs, c);
            return x + Px.textWidth(name, Weight.MEDIUM, fs);
        }
        String a = name.substring(0, i),
                b = name.substring(i, Math.min(name.length(), i + search.length())),
                r = name.substring(Math.min(name.length(), i + search.length()));
        Px.text(g, a, x, y, Weight.MEDIUM, fs, c);
        x += Px.textWidth(a, Weight.MEDIUM, fs);
        Px.text(g, b, x, y, Weight.SEMIBOLD, fs, accText());
        x += Px.textWidth(b, Weight.SEMIBOLD, fs);
        Px.text(g, r, x, y, Weight.MEDIUM, fs, c);
        x += Px.textWidth(r, Weight.MEDIUM, fs);
        return x;
    }

    private void openPopup(Module m, int x, int y, int w, int h) {
        if (popMod == m) {
            popMod = null;
            UiSound.play("pop", .8f, .6f);
            return;
        }
        if (popShown != m) anim.set("pm", 0);
        popMod = m;
        ancX = x;
        ancY = y;
        ancW = w;
        ancH = h;
        binding = null;
        pop = null;
        scroll.put("pm:" + m.id, 0f);
        anim.set("sc:pm:" + m.id, 0);
        UiSound.play("pop");
    }

    // menu settings
    List<Config.Preset> presetCache;
    private long presetAt;

    List<Config.Preset> presetList() {
        if (presetCache == null || now - presetAt > 400) {
            presetCache = Config.presets();
            presetAt = now;
        }
        return presetCache;
    }

    /** The menu's own settings, grouped into cards: an icon, a title and a line under it, then the rows. */
    record Group(String icon, String title, String subtitle, List<Setting<?>> rows) {}

    int groupH(Group gr, int cw) {
        int h = S(64);
        for (Setting<?> s : gr.rows()) if (s.visible()) h += rowH(s, cw - S(36));
        return h + S(10);
    }

    // into the panel's lower edge
    private static final String[][] DOCK = {
        {"modules-s", "Модули"},
        {"cosmetics-s", "Косметика"},
        {"hud-s", "Редактор HUD"},
        {"presets-s", "Пресеты"},
        {"flag-s", "Язык"}
    };

    private void dock(GuiGraphics g, int cx, int edge) {
        int bs = S(44), gap = S(8), pad = S(6), n = DOCK.length;
        int w = n * bs + (n - 1) * gap + pad * 2, h = bs + pad * 2, x = cx - w / 2, y = edge - h / 2;
        boolean lt = light();
        Px.round(g, x, y, w, h, h / 2, lt ? 0xFFFFFFFF : Px.mix(T().bg() | 0xFF000000, 0xFFFFFFFF, .025f));
        Px.outline(g, x, y, w, h, h / 2, Px.a(T().text(), lt ? 30 : 22));
        int activeIdx =
                switch (page) {
                    case MODULES -> 0;
                    case WARDROBE -> 1;
                    case PRESETS -> 3;
                    case SETTINGS -> 0;
                };
        int ty = y + pad;
        float tx = tween("dockX", x + pad + activeIdx * (bs + gap), 280);
        int txi = Math.round(tx);
        Px.circle(g, txi + bs / 2, ty + bs / 2, bs / 2, acc());
        for (int i = 0; i < n; i++) {
            int bx = x + pad + i * (bs + gap);
            boolean hv = !under() && hover(bx, ty, bs, bs);
            if (hv) hot("dock:" + i);
            float hk = an("dock:" + i, hv ? 1 : 0, 18);
            float lit = Math.max(0, 1 - Math.abs(tx - bx) / (float) (bs + gap));
            if (hk > .01f)
                Px.circle(
                        g, bx + bs / 2, ty + bs / 2, bs / 2, Px.a(T().text(), (int) ((lt ? 12 : 12) * hk * (1 - lit))));
            int gs = S(20);
            glyph(
                    g,
                    DOCK[i][0],
                    bx + (bs - gs) / 2,
                    ty + (bs - gs) / 2,
                    gs,
                    Px.mix(Px.mix(lt(0xFF8E89A0, 0xFF857E98), T().text(), hk), 0xFFFFFFFF, lit));
            if (hk > .02f) { // the label below the dock
                String t = i == 4 ? Lang.tr("Язык интерфейса") : Lang.tr(DOCK[i][1]);
                int tw = Px.textWidth(t, Weight.MEDIUM, S(12)) + S(20),
                        th = S(26),
                        lx = bx + bs / 2 - tw / 2,
                        ly = y + h + S(8) - Math.round((1 - hk) * S(4));
                float kf = Px.fade;
                Px.fade = kf * hk;
                Px.round(g, lx, ly, tw, th, th / 2, lt ? 0xFFFFFFFF : Px.mix(T().bg() | 0xFF000000, 0xFFFFFFFF, .06f));
                Px.outline(g, lx, ly, tw, th, th / 2, Px.a(T().text(), lt ? 26 : 22));
                Px.textCenter(g, t, bx + bs / 2, ly + (th - S(12)) / 2, Weight.MEDIUM, S(12), T().text());
                Px.fade = kf;
            }
            int idx = i;
            if (!under()) region(bx, ty, bs, bs, () -> dockClick(idx));
        }
    }

    private void dockClick(int i) {
        Page to =
                switch (i) {
                    case 0 -> Page.MODULES;
                    case 1 -> Page.WARDROBE;
                    case 3 -> Page.PRESETS;
                    case 4 -> Page.SETTINGS;
                    default -> null;
                };
        if (i == 4) {
            UiSound.play("click");
            List<String> languages = List.of("Русский", "English");
            Minecraft.getInstance()
                    .setScreen(PromptScreen.pick(
                            this, "Язык интерфейса", languages, Lang.english() ? 1 : 0, "Применить", picked -> {
                                Modules.PREFS.language.set(languages.get(picked));
                                Config.dirty();
                                UiSound.play("select");
                            }));
            return;
        }
        if (to == null) {
            UiSound.play("click");
            Minecraft.getInstance().setScreen(new HudEditorScreen(this));
            return;
        }
        if (page == to) return;
        page = to;
        search = "";
        searchFocused = false;
        content("page");
    }

    boolean compact;

    int valueBox(GuiGraphics g, String v, int right, int y, boolean accent, boolean hv) {
        int bw = Math.max(S(compact ? 44 : 52), Px.textWidth(v, Weight.MEDIUM, S(13)) + S(compact ? 18 : 22)),
                bh = S(compact ? 26 : 30),
                bx = right - bw;
        Px.round(g, bx, y, bw, bh, S(5), accent ? Px.a(Theme.accent(), light() ? 26 : 40) : inset(hv));
        Px.outline(g, bx, y, bw, bh, S(5), accent ? acc() : Px.a(T().text(), light() ? 30 : 16));
        Px.textCenter(g, v, bx + bw / 2, y + (bh - S(13)) / 2, Weight.MEDIUM, S(13), accent ? accText() : T().text());
        return bw;
    }

    private boolean segmented(Setting.Mode md, int w) {
        int n = md.modes.size();
        if (n < 2 || n > 3) return false;
        int seg = (w - S(8)) / n;
        for (String s : md.modes) if (Px.textWidth(Lang.tr(s), Weight.MEDIUM, S(12)) > seg - S(18)) return false;
        return true;
    }

    private static String numText(Setting.Num n, double v) {
        String s = n.step >= 1
                ? String.valueOf((long) Math.round(v))
                : String.format(Locale.ROOT, n.step >= .1 ? "%.1f" : "%.2f", v);
        return s + n.unit;
    }

    int rowH(Setting<?> s, int w) {
        if (s instanceof Setting.Num) return S(compact ? 66 : 76);
        if (s instanceof Setting.Mode md && segmented(md, w)) return S(compact ? 70 : 82);
        return S(compact ? 40 : 46);
    }

    int settings(
            GuiGraphics g, Module m, List<Setting<?>> list, int x, int y, int w, float sc, int t, int b, boolean live) {
        for (Setting<?> s : list) {
            int h = rowH(s, w);
            g.nextStratum();
            if (y - sc + h >= t && y - sc <= b) row(g, m, s, x, y, w, h, sc, t, b, live);
            y += h;
        }
        return y;
    }

    private void row(
            GuiGraphics g, Module m, Setting<?> s, int x, int y, int w, int h, float sc, int t, int b, boolean live) {
        String id = m.id + ":" + s.key;
        boolean hv = live && my >= t && my < b && hover(x, Math.round(y - sc), w, h);
        if (hv) hot("set:" + id);
        float hk = an("row:" + id, hv ? 1 : 0, 18);
        int label = T().text();
        String name = Px.ellipsize(Lang.tr(s.name), Weight.MEDIUM, S(14), w - S(110));
        if (s instanceof Setting.Bool bo) {
            Px.text(g, name, x, y + (h - S(14)) / 2, Weight.MEDIUM, S(14), label);
            float on = tween("b:" + id, bo.on() ? 1 : 0, 200);
            toggle(g, x + w - S(44), y + (h - S(24)) / 2, S(44), S(24), on, hk);
            if (live)
                clipped(
                        x,
                        y,
                        w,
                        h,
                        sc,
                        t,
                        b,
                        () -> {
                            bo.toggle();
                            UiSound.play(bo.on() ? "toggle_on" : "toggle_off");
                            Config.dirty();
                        },
                        null);
        } else if (s instanceof Setting.Num n) {
            boolean drag = dragging != null && id.equals(dragging.id());
            // number row: the name, the value, a track with a knob and the range under it
            int fsN = compact ? S(13) : S(14), ly = y + S(10);
            Px.text(g, name, x, ly, Weight.MEDIUM, fsN, label);
            float vk = an("nv:" + id, drag || hk > .5f ? 1 : 0, 16);
            Px.textRight(
                    g, n.text(), x + w, ly, Weight.SEMIBOLD, S(13), Px.mix(accText(), lt(T().text(), 0xFFFFFFFF), vk));
            int th = Math.max(2, S(3)), kr = S(6), tx = x, tw = w, ty = y + h - S(compact ? 24 : 28);
            int rs = S(10), rc = lt(T().dim(), 0xFF5A616C);
            Px.text(g, numText(n, n.min), tx, ty + S(9), Weight.MEDIUM, rs, rc);
            Px.textRight(g, numText(n, n.max), tx + tw, ty + S(9), Weight.MEDIUM, rs, rc);
            float f = drag ? (float) n.fraction() : an("n:" + id, (float) n.fraction(), 22);
            int kx = tx + kr + Math.round((tw - kr * 2) * f), kcy = ty + th / 2;
            Px.round(g, tx, ty, tw, th, th / 2, light() ? 0xFFDEDDE6 : Px.a(0xFFFFFF, 24));
            Px.round(g, tx, ty, Math.max(th, kx - tx), th, th / 2, acc());
            float kh = an(
                    "nk:" + id,
                    drag ? 1 : hv && hover(tx - S(10), Math.round(ty - sc - S(12)), tw + S(20), S(30)) ? .6f : 0,
                    20);
            int outer = kr + Math.round(S(1.5) * kh), inner = Math.max(1, Math.round(outer * .34f));
            Px.circle(g, kx, kcy, outer, acc());
            Px.circle(g, kx, kcy, inner, light() ? 0xFFFFFFFF : 0xFF12141C);
            if (live) {
                int y0 = (int) Math.max(t, ty - S(14) - sc), y1 = (int) Math.min(b, ty + S(20) - sc);
                if (y1 > y0)
                    regions.add(new Region(
                            tx - S(10),
                            y0,
                            tw + S(20),
                            y1 - y0,
                            null,
                            null,
                            (at, ay) -> {
                                double before = n.get();
                                n.set(n.min
                                        + (n.max - n.min) * Math.max(0, Math.min(1, (at - tx - kr) / (tw - kr * 2))));
                                if (n.get() != before) UiSound.play("tick", .75f + (float) n.fraction() * .8f, .55f);
                                anim.set("n:" + id, (float) n.fraction());
                                Config.dirty();
                            },
                            id));
            }
        } else if (s instanceof Setting.Mode md) {
            if (segmented(md, w)) {
                Px.text(g, name, x, y + S(12), Weight.MEDIUM, S(14), label);
                int sh = S(compact ? 28 : 32), sy = y + h - sh - S(compact ? 8 : 10), n = md.modes.size(), in = S(4);
                float segW = (w - in * 2) / (float) n;
                Px.round(g, x, sy, w, sh, S(6), lt(0xFFEFEFF4, inset(false)));
                Px.outline(g, x, sy, w, sh, S(6), Px.a(T().text(), light() ? 20 : 14));
                float thumb = tween("seg:" + id, md.index(), 240);
                int t0 = x + in + Math.round(segW * thumb), t1 = x + in + Math.round(segW * (thumb + 1));
                Px.round(g, t0, sy + in, t1 - t0, sh - in * 2, S(4), acc());
                for (int i = 0; i < n; i++) {
                    String mode = md.modes.get(i);
                    float a = Math.max(0, 1 - Math.abs(thumb - i));
                    int s0 = x + in + Math.round(segW * i), s1 = x + in + Math.round(segW * (i + 1));
                    boolean sh2 = live && hover(s0, Math.round(sy - sc), s1 - s0, sh);
                    if (sh2) hot("seg:" + id + i);
                    Px.textCenter(
                            g,
                            Lang.tr(mode),
                            (s0 + s1) / 2,
                            sy + (sh - S(12)) / 2,
                            a > .5f ? Weight.SEMIBOLD : Weight.MEDIUM,
                            S(12),
                            Px.mix(sh2 ? T().text() : T().muted(), 0xFFFFFFFF, a));
                    if (live && md.index() != i)
                        clipped(
                                s0,
                                sy,
                                s1 - s0,
                                sh,
                                sc,
                                t,
                                b,
                                () -> {
                                    md.set(mode);
                                    afterMode(md);
                                    UiSound.play("click");
                                },
                                null);
                }
            } else {
                Px.text(g, name, x, y + (h - S(14)) / 2, Weight.MEDIUM, S(14), label);
                String v = Lang.tr(md.get());
                int vw = Math.min(w / 2, Px.textWidth(v, Weight.MEDIUM, S(13)) + S(42)),
                        vh = S(30),
                        vx = x + w - vw,
                        vy = y + (h - vh) / 2;
                boolean open = pop == md;
                Px.round(g, vx, vy, vw, vh, S(5), open ? Px.a(Theme.accent(), light() ? 22 : 36) : inset(hk > .5f));
                Px.outline(g, vx, vy, vw, vh, S(5), open ? acc() : Px.a(T().text(), light() ? 30 : 16));
                Px.text(
                        g,
                        Px.ellipsize(v, Weight.MEDIUM, S(13), vw - S(40)),
                        vx + S(12),
                        vy + (vh - S(13)) / 2,
                        Weight.MEDIUM,
                        S(13),
                        T().text());
                float turn = tween("dd:" + id, open ? 1 : 0, 200);
                int cs = S(12);
                g.pose().pushMatrix();
                g.pose().translate(vx + vw - S(18), vy + vh / 2f);
                g.pose().rotate((float) (Math.PI / 2 - Math.PI * turn));
                glyph(g, "chev-right", -cs / 2, -cs / 2, cs, Px.mix(lt(T().muted(), 0xFF757D8A), accText(), turn));
                g.pose().popMatrix();
                if (live)
                    clipped(
                            x,
                            y,
                            w,
                            h,
                            sc,
                            t,
                            b,
                            () -> {
                                if (pop == md) {
                                    pop = null;
                                    UiSound.play("pop", .8f, .6f);
                                    return;
                                }
                                pop = md;
                                popW = Math.max(S(200), vw);
                                popX = vx + vw - popW;
                                popY = Math.round(vy - sc + vh + S(6));
                                popAt = now;
                                UiSound.play("pop");
                            },
                            null);
            }
        } else if (s instanceof Setting.Colour c) {
            Px.text(g, name, x, y + (h - S(14)) / 2, Weight.MEDIUM, S(14), label);
            int col = c.argb() | 0xFF000000, sw = S(26), sx = x + w - sw, syy = y + (h - sw) / 2;
            String hex = c.followsTheme() ? Lang.tr("Как в теме") : String.format("#%06X", col & 0xFFFFFF);
            Px.textRight(g, hex, sx - S(10), y + (h - S(12)) / 2, Weight.MEDIUM, S(12), T().dim());
            Px.round(g, sx, syy, sw, sw, sw / 2, col);
            Px.outline(g, sx, syy, sw, sw, sw / 2, Px.a(0xFFFFFF, light() ? 0 : 40));
            if (live)
                clipped(
                        x,
                        y,
                        w,
                        h,
                        sc,
                        t,
                        b,
                        () -> {
                            picker = new ColourPicker(s.name, c).at(mx, my);
                            UiSound.play("pop");
                        },
                        null);
        }
    }

    private void afterMode(Setting.Mode md) {
        if (md == Modules.HANDS.preset) Modules.HANDS.applyPreset();
        else if (md == Modules.HITSOUND.sound) Modules.HITSOUND.play(Modules.HITSOUND.current());
        else if (md == Modules.KILLSOUND.sound) Modules.KILLSOUND.play(Modules.KILLSOUND.current());
        Config.dirty();
    }

    private void popover(GuiGraphics g) {
        Setting.Mode md = pop;
        float a = ease((now - popAt) / 170f);
        int ih = S(38), rows = Math.min(md.modes.size(), 8), h = ih * rows + S(12);
        int x = popX, y = Math.min(popY, Px.height() - h - S(12));
        String key = "pop:" + md.key;
        int max = Math.max(0, (md.modes.size() - rows) * ih);
        float msc = Math.max(0, Math.min(max, scroll.getOrDefault(key, (float)
                Math.max(0, Math.min(max, (md.index() - rows / 2) * ih)))));
        scroll.put(key, msc);
        float k = Px.fade;
        Px.fade = k * a;
        g.pose().pushMatrix();
        if (a < .999f) {
            g.pose().translate(x + popW, y);
            g.pose().scale(.95f + .05f * a, .95f + .05f * a);
            g.pose().translate(-(x + popW), -y + (1 - a) * -S(6));
        }
        boolean lt = light();
        Px.shadow(g, x, y + S(6), popW, h, S(18), Px.a(0, lt ? 30 : 70));
        boolean pfrost = !lt && false;
        if (pfrost) Frost.backdrop(g, x, y, popW, h, S(12));
        Px.round(g, x, y, popW, h, S(12), lt ? 0xFFFFFFFF : pfrost ? 0xDC0C0D12 : 0xF20C0D12);
        Px.outline(g, x, y, popW, h, S(12), lt ? Px.a(T().text(), 26) : 0x18FFFFFF);
        g.enableScissor(x, y + S(6), x + popW, y + h - S(6));
        for (int i = 0; i < md.modes.size(); i++) {
            String mode = md.modes.get(i);
            int iy = Math.round(y + S(6) + i * ih - msc);
            if (iy + ih < y || iy > y + h) continue;
            boolean hv = hover(x + S(6), iy, popW - S(12), ih) && my > y && my < y + h;
            if (hv) hot("popi:" + i);
            float hk = an("popH:" + i, hv ? 1 : 0, 20);
            if (hk > .01f)
                Px.round(g, x + S(6), iy, popW - S(12), ih, S(5), Px.a(T().text(), (int) ((lt ? 12 : 14) * hk)));
            boolean cur = md.is(mode);
            if (cur) glyph(g, "check", x + S(16), iy + (ih - S(14)) / 2, S(14), accText());
            Px.text(
                    g,
                    Px.ellipsize(Lang.tr(mode), Weight.MEDIUM, S(13), popW - S(56)),
                    x + S(40),
                    iy + (ih - S(13)) / 2,
                    cur ? Weight.SEMIBOLD : Weight.MEDIUM,
                    S(13),
                    cur ? T().text() : Px.mix(T().muted(), T().text(), hk));
            int y0 = Math.max(iy, y + S(6)), y1 = Math.min(iy + ih, y + h - S(6));
            if (y1 > y0)
                regions.add(new Region(
                        x + S(6),
                        y0,
                        popW - S(12),
                        y1 - y0,
                        () -> {
                            md.set(mode);
                            pop = null;
                            afterMode(md);
                            UiSound.play("click");
                        },
                        null,
                        null,
                        "pop"));
        }
        g.disableScissor();
        g.pose().popMatrix();
        Px.fade = k;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double x = Px.px(e.x()), y = Px.px(e.y());
        if (picker != null) {
            picker.click(x, y, e.button());
            return true;
        }
        if (binding != null && e.button() >= 2) {
            binding.bind = Keys.MOUSE + e.button();
            binding = null;
            Config.dirty();
            UiSound.play("select");
            return true;
        }
        if (pop != null) {
            for (int i = regions.size() - 1; i >= 0; i--) {
                Region r = regions.get(i);
                if ("pop".equals(r.id()) && r.in(x, y)) {
                    r.click().run();
                    return true;
                }
            }
            pop = null;
            UiSound.play("pop", .8f, .6f);
            return true;
        }
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (!r.in(x, y) || "pop".equals(r.id())) continue;
            if (r.drag() != null && e.button() == 0) {
                dragging = r;
                UiSound.play("grab", 1, .6f);
                r.drag().to(x, y);
                return true;
            }
            if (e.button() == 0 && r.click() != null) {
                searchFocused = false;
                r.click().run();
                return true;
            }
            if (e.button() == 1 && r.alt() != null) {
                r.alt().run();
                return true;
            }
        }
        searchFocused = false;
        binding = null;
        if (popMod != null) {
            popMod = null;
            UiSound.play("pop", .8f, .6f);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        double x = Px.px(e.x()), y = Px.px(e.y());
        if (picker != null) {
            picker.drag(x, y);
            return true;
        }
        if (dragging != null) dragging.drag().to(x, y);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (picker != null) {
            picker.release();
            return true;
        }
        if (dragging != null) UiSound.play("drop", 1, .5f);
        dragging = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (picker != null) return true;
        if (pop != null) {
            String k = "pop:" + pop.key;
            scroll.put(k, scroll.getOrDefault(k, 0f) - (float) sy * S(38));
            return true;
        }
        if (popMod != null && mx >= pmX && mx < pmX + pmW && my >= pmY && my < pmY + pmH) {
            String p = "pm:" + popMod.id;
            scroll.put(p, scroll.getOrDefault(p, 0f) - (float) sy * S(60));
            return true;
        }
        String k =
                switch (page) {
                    case MODULES -> "c:" + (search.isEmpty() ? category : "search");
                    case WARDROBE -> "w:" + wardrobe;
                    case PRESETS -> "presets" + presetsPage.tab;
                    case SETTINGS -> "prefs";
                };
        scroll.put(k, scroll.getOrDefault(k, 0f) - (float) sy * S(76));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (picker != null) {
            picker.key(e);
            return true;
        }
        if (binding != null) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                binding = null;
                return true;
            }
            if (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) {
                binding.bind = -1;
                binding = null;
                Config.dirty();
                UiSound.play("reset");
                return true;
            }
            binding.bind = k;
            binding = null;
            Config.dirty();
            UiSound.play("select");
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (pop != null) {
                pop = null;
                return true;
            }
            if (popMod != null) {
                popMod = null;
                UiSound.play("pop", .8f, .6f);
                return true;
            }
            if (searchFocused || !search.isEmpty()) {
                searchFocused = false;
                if (!search.isEmpty()) {
                    search = "";
                    content("click");
                }
                return true;
            }
            onClose();
            return true;
        }
        if (k == GLFW.GLFW_KEY_F && (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            toModules();
            searchFocused = true;
            UiSound.play("click", 1.2f, .6f);
            return true;
        }
        if (!searchFocused
                && page == Page.MODULES
                && search.isEmpty()
                && (k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_RIGHT)
                && popMod == null) {
            int i = 0;
            for (int j = 0; j < TABS.length; j++) if (TABS[j] == category) i = j;
            tabClick(Math.floorMod(i + (k == GLFW.GLFW_KEY_RIGHT ? 1 : -1), TABS.length));
            return true;
        }
        if (searchFocused) {
            if (k == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                UiSound.play("type", .9f, .3f);
                if (search.isEmpty()) contentAt = now;
                return true;
            }
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
                List<Module> v = visible();
                if (!v.isEmpty() && !search.isEmpty()) {
                    Module m = v.get(0);
                    if (n(m) > 0) openPopup(m, cx0, top + S(2), (cw0 - S(12)) / 2, headH());
                }
                searchFocused = false;
                return true;
            }
            return true;
        }
        return super.keyPressed(e);
    }

    private void toModules() {
        if (page != Page.MODULES) {
            page = Page.MODULES;
            content(null);
        }
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        String s = e.codepointAsString();
        if (picker != null) {
            picker.character(e);
            return true;
        } // typing a hex / rgb / opacity value
        if (s.isEmpty() || s.codePointAt(0) < 32 || pop != null || binding != null) return false;
        if (!searchFocused) {
            if (!Character.isLetterOrDigit(s.codePointAt(0))) return false;
            toModules();
            popMod = null;
            searchFocused = true;
        }
        if (search.length() < 40) {
            if (search.isEmpty()) contentAt = now;
            search += s;
            UiSound.play("type", 1.2f, .35f);
        }
        return true;
    }

    @Override
    public void onClose() {
        if (picker != null) {
            picker.close();
            return;
        }
        Config.save();
        if (!closing) {
            closing = true;
            UiSound.play("close");
            Toasts.menu(false);
        } else super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (Modules.PREFS.blur.on() && Minecraft.getInstance().level != null) renderBlurredBackground(g);
        else if (Minecraft.getInstance().level == null) super.renderBackground(g, mouseX, mouseY, pt);
    }
}
