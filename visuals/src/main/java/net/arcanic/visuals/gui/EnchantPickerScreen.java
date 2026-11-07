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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.helper.EnchantSearch;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

public final class EnchantPickerScreen extends Screen {
    private final Screen parent;
    private final EnchantSearch search;
    private final Anim anim = new Anim();
    private PromptScreen.Kit k = new PromptScreen.Kit();
    private EditBox field;
    private int cx;
    private int cy;
    private int cw;
    private int ch;
    private int listTop;
    private int listBottom;
    private int segW;
    private float scroll;
    private float shown;
    private float grab = Float.NaN;
    private static final int HOLY = 0;
    private static final int ARMOR = 1;
    private static final int WEAPON = 2;
    private static final int TOOLS = 3;
    private static final int RANGED = 4;
    private static final int TRIDENT = 5;
    private static final int ROD = 6;
    private static final int ANY = 7;
    private static final int OTHER = 8;
    private static final int CURSE = 9;
    private static final String[] KINDS = {
        "HolyWorld",
        "Броня",
        "Оружие",
        "Инструменты",
        "Луки и арбалеты",
        "Трезубец и булава",
        "Удочка",
        "Для любых предметов",
        "Прочее",
        "Проклятия"
    };
    private static final int[] ORDER = {1, 3, 2, 4, 5, 6};
    private static final Item[][] ITEMS;
    private final List<Ench> all = new ArrayList<>();
    private static final ItemStack BOOK;
    private final List<Hit> hits = new ArrayList<>();
    private int barX;
    private int barH;
    private int thumbY;
    private int thumbH;
    private float barMax;

    public EnchantPickerScreen(Screen parent, EnchantSearch search) {
        super(Component.literal(search.title()));
        this.parent = parent;
        this.search = search;
        this.anim.set("open", 0.0F);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Registry<Enchantment> reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

            for (Holder.Reference<Enchantment> h : reg.listElements().toList()) {
                Enchantment en = h.value();
                int kind = 8;
                int kinds = 0;
                ItemStack icon = null;

                for (int kd : ORDER) {
                    boolean fits = false;

                    for (Item it : ITEMS[kd]) {
                        ItemStack st = new ItemStack(it);
                        if (en.isSupportedItem(st)) {
                            fits = true;
                            if (icon == null) {
                                icon = st;
                            }
                        }
                    }

                    if (fits && kinds++ == 0) {
                        kind = kd;
                    }
                }

                if (kinds >= 3) {
                    kind = 7;
                }

                if (h.is(EnchantmentTags.CURSE)) {
                    kind = 9;
                }

                if (kind == 7 || kind == 9 || icon == null) {
                    icon = BOOK;
                }

                this.all.add(new Ench(EnchantSearch.id(h), en.description().getString(), en.getMaxLevel(), kind, icon));
            }
        }

        for (EnchantSearch.Custom c : this.search.customs()) {
            boolean known = false;

            for (Ench e : this.all) {
                known |= e.name().equalsIgnoreCase(c.name());
            }

            if (!known) {
                this.all.add(new Ench(c.key(), c.name(), c.max(), 0, new ItemStack(holyItem(c.type()))));
            }
        }

        this.all.sort(Comparator.comparing(Ench::name));
    }

    private static Item holyItem(String type) {
        return switch (type) {
            case "lumberjack" -> Items.NETHERITE_AXE;
            case "ft-sword" -> Items.NETHERITE_SWORD;
            case "ft-armor" -> Items.NETHERITE_HELMET;
            case "farmer", "replant" -> Items.NETHERITE_HOE;
            case "impenetrable" -> Items.NETHERITE_CHESTPLATE;
            case "lava" -> Items.NETHERITE_BOOTS;
            case "homing" -> Items.BOW;
            case "critical", "rich", "stun", "mob-farmer" -> Items.NETHERITE_SWORD;
            default -> Items.NETHERITE_PICKAXE;
        };
    }

    protected void init() {
        String was = this.field != null ? this.field.getValue() : "";
        this.clearWidgets();
        this.k = new PromptScreen.Kit();
        this.cw = Math.min(this.k.S(760.0), Px.width() - this.k.S(40.0));
        this.ch = Math.min(this.k.S(620.0), Px.height() - this.k.S(40.0));
        this.cx = (Px.width() - this.cw) / 2;
        this.cy = (Px.height() - this.ch) / 2;
        int pad = this.k.S(20.0);
        this.segW = this.k.S(200.0);
        this.field = new EditBox(
                this.font,
                PromptScreen.Kit.logical(this.cx + pad),
                PromptScreen.Kit.logical(this.cy + this.k.S(58.0)),
                PromptScreen.Kit.logical(this.cw - pad * 2 - this.segW - this.k.S(10.0)),
                PromptScreen.Kit.logical(this.k.S(32.0)),
                Component.literal(Lang.tr("Найти зачарование")));
        this.field.setMaxLength(40);
        this.field.setBordered(false);
        this.field.setValue(was);
        this.addWidget(this.field);
        this.setInitialFocus(this.field);
        this.listTop = this.cy + this.k.S(58.0) + this.k.S(32.0) + this.k.S(12.0);
        this.listBottom = this.cy + this.ch - this.k.S(66.0);
    }

    private int level(Ench e) {
        return (int) Math.round(this.search.chosen.of(e.id(), 0.0));
    }

    private void set(Ench e, int lv) {
        this.search.chosen.put(e.id(), (double) lv, 0.0);
        Config.dirty();
    }

    private int rowH() {
        return this.k.S(30.0);
    }

    private List<Line> layout(int[] height) {
        String q = this.field == null ? "" : this.field.getValue().trim().toLowerCase(Locale.ROOT);
        List<Line> out = new ArrayList<>();
        int pad = this.k.S(20.0);
        int gap = this.k.S(24.0);
        int x0 = this.cx + pad;
        int w = this.cw - pad * 2 - this.k.S(14.0);
        int colW = (w - gap) / 2;
        int y = 0;
        int rh = this.rowH();
        int headH = this.k.S(30.0);
        List<List<Ench>> groups = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (q.isEmpty() && !((Map) this.search.chosen.get()).isEmpty()) {
            List<Ench> picked = new ArrayList<>();

            for (Ench e : this.all) {
                if (this.level(e) > 0) {
                    picked.add(e);
                }
            }

            groups.add(picked);
            names.add("Выбрано");
        }

        for (int kd = 0; kd < KINDS.length; kd++) {
            List<Ench> in = new ArrayList<>();

            for (Ench e : this.all) {
                if (e.kind() == kd
                        && (q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q))) {
                    in.add(e);
                }
            }

            groups.add(in);
            names.add(KINDS[kd]);
        }

        for (int gi = 0; gi < groups.size(); gi++) {
            List<Ench> in = (List) groups.get(gi);
            if (!in.isEmpty()) {
                if (y > 0) {
                    y += this.k.S(10.0);
                }

                out.add(new Line(Lang.tr(names.get(gi)), in.size(), (Ench) null, x0, y, w, null));
                y += headH;

                for (int i = 0; i < in.size(); i++) {
                    int col = i % 2;
                    out.add(new Line(
                            null,
                            0,
                            (Ench) in.get(i),
                            x0 + col * (colW + gap),
                            y,
                            colW,
                            gi + ":" + ((Ench) in.get(i)).id()));
                    if (col == 1 || i == in.size() - 1) {
                        y += rh;
                    }
                }
            }
        }

        height[0] = y + this.k.S(6.0);
        return out;
    }

    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        double mx = Px.px(ev.x());
        double my = Px.px(ev.y());
        if (!PromptScreen.Kit.in(mx, my, this.cx, this.cy, this.cw, this.ch)) {
            this.onClose();
            return true;
        } else if (ev.button() == 0
                && this.barMax >= 1.0F
                && PromptScreen.Kit.in(mx, my, this.barX - this.k.S(8.0), this.listTop, this.k.S(20.0), this.barH)) {
            this.grab = my >= (double) this.thumbY && my < (double) (this.thumbY + this.thumbH)
                    ? (float) (my - (double) this.thumbY)
                    : (float) this.thumbH / 2.0F;
            this.dragBar(my);
            UiSound.play("grab", 1.0F, 0.6F);
            return true;
        } else {
            if (ev.button() == 0) {
                for (int i = this.hits.size() - 1; i >= 0; --i) {
                    Hit h = (Hit) this.hits.get(i);
                    if (PromptScreen.Kit.in(mx, my, h.x(), h.y(), h.w(), h.h())) {
                        h.run().run();
                        return true;
                    }
                }
            }

            return super.mouseClicked(ev, dbl);
        }
    }

    public boolean mouseDragged(MouseButtonEvent ev, double dx, double dy) {
        if (!Float.isNaN(this.grab)) {
            this.dragBar(Px.px(ev.y()));
            return true;
        } else {
            return super.mouseDragged(ev, dx, dy);
        }
    }

    public boolean mouseReleased(MouseButtonEvent ev) {
        this.grab = Float.NaN;
        return super.mouseReleased(ev);
    }

    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        this.scroll -= (float) sy * (float) this.k.S(50.0);
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() != 257 && event.key() != 335) {
            boolean r = super.keyPressed(event);
            this.scroll = 0.0F;
            return r;
        } else {
            this.onClose();
            return true;
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

    public void render(GuiGraphics g, int mx0, int my0, float delta) {
        this.renderBackground(g, mx0, my0, delta);
        g.nextStratum();
        this.anim.frame();
        this.hits.clear();
        if (!Float.isNaN(this.grab) && !ArcanicMenu.leftHeld()) {
            this.grab = Float.NaN;
        }

        float open = this.anim.to("open", 1.0F, 15.0F);
        float e1 = ArcanicMenu.ease(open);
        int mx = (int) Px.px((double) mx0);
        int my = (int) Px.px((double) my0);
        int pad = this.k.S(20.0);
        Px.begin(g);
        float old = Px.fade;

        try {
            Px.fade = open;
            Px.rect(g, 0, 0, Px.width(), Px.height(), 0x99000000);
            g.pose().pushMatrix();
            float sc = 0.94F + 0.06F * e1;
            g.pose().translate((float) this.cx + (float) this.cw / 2.0F, (float) this.cy + (float) this.ch / 2.0F);
            g.pose().scale(sc, sc);
            g.pose()
                    .translate(
                            (float) (-this.cx) - (float) this.cw / 2.0F, (float) (-this.cy) - (float) this.ch / 2.0F);
            this.k.card(g, this.cx, this.cy, this.cw, this.ch);
            Px.text(
                    g,
                    this.getTitle().getString(),
                    this.cx + pad,
                    this.cy + this.k.S(20.0),
                    Px.Weight.SEMIBOLD,
                    this.k.S(16.0),
                    PromptScreen.Kit.T().text());
            int cs = this.k.S(28.0);
            int clx = this.cx + this.cw - pad - cs;
            int cly = this.cy + this.k.S(14.0);
            this.k.close(g, clx, cly, mx, my, this.anim);
            this.hits.add(new Hit(clx, cly, cs, cs, () -> {
                UiSound.play("pop", 0.85F, 0.7F);
                this.onClose();
            }));
            int fy = this.cy + this.k.S(58.0);
            this.k.field(
                    g,
                    this.field,
                    this.cx + pad,
                    fy,
                    this.cw - pad * 2 - this.segW - this.k.S(10.0),
                    Lang.tr("Найти зачарование"),
                    "search",
                    false,
                    this.anim,
                    "f");
            int sx = this.cx + this.cw - pad - this.segW;
            int sy = fy + (this.k.S(32.0) - this.k.S(28.0)) / 2;
            boolean every = this.search.match.is("Все сразу");
            this.k.segmented(
                    g,
                    sx,
                    sy,
                    this.segW,
                    Lang.tr("Любое из них"),
                    Lang.tr("Все сразу"),
                    this.anim.to("match", every ? 1.0F : 0.0F, 16.0F),
                    mx,
                    my);
            this.hits.add(new Hit(sx, sy, this.segW / 2, this.k.S(28.0), () -> this.pickMatch("Любое из них")));
            this.hits.add(new Hit(
                    sx + this.segW / 2,
                    sy,
                    this.segW - this.segW / 2,
                    this.k.S(28.0),
                    () -> this.pickMatch("Все сразу")));
            this.list(g, mx, my);
            int by = this.cy + this.ch - this.k.S(20.0) - this.k.S(34.0);
            int bw = this.k.S(130.0);
            int n = ((Map) this.search.chosen.get()).size();
            Px.text(
                    g,
                    n == 0 ? Lang.tr("Ничего не выбрано") : Lang.tr("Выбрано") + ": " + n,
                    this.cx + pad,
                    by + (this.k.S(34.0) - this.k.S(12.0)) / 2,
                    Px.Weight.MEDIUM,
                    this.k.S(12.0),
                    n == 0 ? PromptScreen.Kit.dim() : PromptScreen.Kit.T().text());
            int okX = this.cx + this.cw - pad - bw;
            this.k.button(
                    g,
                    Lang.tr("Готово"),
                    okX,
                    by,
                    bw,
                    true,
                    PromptScreen.Kit.in((double) mx, (double) my, okX, by, bw, this.k.S(34.0)),
                    this.anim,
                    "ok");
            this.hits.add(new Hit(okX, by, bw, this.k.S(34.0), () -> {
                UiSound.play("select");
                this.onClose();
            }));
            if (n > 0) {
                int clrX = okX - this.k.S(10.0) - bw;
                this.k.secondary(
                        g,
                        Lang.tr("Сбросить"),
                        clrX,
                        by,
                        bw,
                        PromptScreen.Kit.in((double) mx, (double) my, clrX, by, bw, this.k.S(34.0)),
                        this.anim,
                        "clr");
                this.hits.add(new Hit(clrX, by, bw, this.k.S(34.0), () -> {
                    for (Ench e : this.all) {
                        this.search.chosen.put(e.id(), 0.0, 0.0);
                    }

                    Config.dirty();
                    UiSound.play("reset");
                }));
            }

            g.pose().popMatrix();
        } finally {
            Px.fade = old;
            Px.end(g);
        }
    }

    private void pickMatch(String mode) {
        if (!this.search.match.is(mode)) {
            this.search.match.set(mode);
            Config.dirty();
            UiSound.play("click");
        }
    }

    private void list(GuiGraphics g, int mx, int my) {
        int[] height = {0};
        List<Line> lines = this.layout(height);
        int view = this.listBottom - this.listTop;
        float max = (float) Math.max(0, height[0] - view);
        this.scroll = Math.max(0.0F, Math.min(max, this.scroll));
        if (!Float.isNaN(this.grab)) {
            this.shown = this.scroll;
        }

        this.shown += (this.scroll - this.shown) * 0.3F;
        if (Math.abs(this.scroll - this.shown) < 0.5F) {
            this.shown = this.scroll;
        }

        if (lines.isEmpty()) {
            String why = this.all.isEmpty() ? "Зайди в мир, чтобы увидеть зачарования сервера" : "Ничего не найдено";
            Px.textCenter(
                    g,
                    Lang.tr(why),
                    this.cx + this.cw / 2,
                    this.listTop + this.k.S(40.0),
                    Px.Weight.MEDIUM,
                    this.k.S(12.0),
                    PromptScreen.Kit.dim());
        } else {
            g.enableScissor(this.cx, this.listTop, this.cx + this.cw, this.listBottom);

            for (Line l : lines) {
                int y = Math.round((float) (this.listTop + l.y()) - this.shown);
                if (y + this.rowH() >= this.listTop && y <= this.listBottom) {
                    if (l.head() != null) {
                        this.heading(g, l, y);
                    } else {
                        this.row(g, l, y, mx, my);
                    }
                }
            }

            g.disableScissor();
            int bg = PromptScreen.Kit.light() ? -1 : 0xFF0E0E11;
            int fh = this.k.S(16.0);
            if (this.shown > 0.5F) {
                Px.vgrad(g, this.cx + this.k.S(2.0), this.listTop, this.cw - this.k.S(4.0), fh, bg, Px.a(bg, 0));
            }

            if (this.shown < max - 0.5F) {
                Px.vgrad(
                        g, this.cx + this.k.S(2.0), this.listBottom - fh, this.cw - this.k.S(4.0), fh, Px.a(bg, 0), bg);
            }

            this.bar(g, mx, my, height[0], max);
        }
    }

    private void heading(GuiGraphics g, Line l, int y) {
        int fs = this.k.S(13.0);
        Px.text(
                g,
                l.head(),
                l.x(),
                y + this.k.S(10.0),
                Px.Weight.SEMIBOLD,
                fs,
                PromptScreen.Kit.T().text());
        Px.text(
                g,
                String.valueOf(l.count()),
                l.x() + Px.textWidth(l.head(), Px.Weight.SEMIBOLD, fs) + this.k.S(8.0),
                y + this.k.S(10.0) + (fs - this.k.S(11.0)) / 2,
                Px.Weight.MEDIUM,
                this.k.S(11.0),
                PromptScreen.Kit.dim());
    }

    private void row(GuiGraphics g, Line l, int y, int mx, int my) {
        Ench e = l.e();
        int x = l.x();
        int w = l.w();
        int h = this.rowH();
        int lv = this.level(e);
        boolean on = lv > 0;
        boolean inList = my >= this.listTop && my < this.listBottom;
        boolean hv =
                inList && PromptScreen.Kit.in((double) mx, (double) my, x - this.k.S(6.0), y, w + this.k.S(12.0), h);
        float hk = this.anim.to("h:" + l.key(), hv ? 1.0F : 0.0F, 20.0F);
        float ok = this.anim.to("o:" + l.key(), on ? 1.0F : 0.0F, 18.0F);
        int acc = ArcanicMenu.acc();
        int dim = PromptScreen.Kit.dim();
        int text = PromptScreen.Kit.T().text();
        int is = this.k.S(18.0);
        HudElements.item(g, e.icon(), x, y + (h - is) / 2, is);
        g.nextStratum();
        int right = x + w;
        int fs = this.k.S(13.0);
        float lk = Math.max(hk, ok);
        if (e.max() > 1) {
            int chw = this.k.S(24.0);
            int chh = this.k.S(20.0);
            int cg = this.k.S(3.0);
            int cyy = y + (h - chh) / 2;
            int span = e.max() * chw + (e.max() - 1) * cg;
            boolean fits = span <= w / 2;
            float kf = Px.fade;
            Px.fade = kf * (0.35F + 0.65F * lk);
            if (fits) {
                int x0 = right - span;

                for (int i = 1; i <= e.max(); i++) {
                    int lx = x0 + (i - 1) * (chw + cg);
                    boolean lit = on && i >= lv;
                    boolean pick = i == lv;
                    boolean chv = inList && PromptScreen.Kit.in((double) mx, (double) my, lx, cyy, chw, chh);
                    int bgc = pick ? acc : (lit ? Px.a(acc, 60) : (chv ? PromptScreen.Kit.inset(true) : 0));
                    if (bgc >>> 24 != 0) {
                        Px.round(g, lx, cyy, chw, chh, this.k.S(5.0), bgc);
                    }

                    Px.textCenter(
                            g,
                            EnchantSearch.roman(i),
                            lx + chw / 2,
                            cyy + (chh - this.k.S(10.0)) / 2,
                            Px.Weight.SEMIBOLD,
                            this.k.S(10.0),
                            pick ? -1 : (lit ? Px.mix(acc, -1, 0.6F) : (chv ? text : dim)));
                    int level = i;
                    this.hits.add(new Hit(lx, Math.max(cyy, this.listTop), chw, chh, () -> {
                        this.set(e, lv == level ? 0 : level);
                        UiSound.play(lv == level ? "toggle_off" : "toggle_on");
                    }));
                }

                right = x0 - this.k.S(10.0);
            } else {
                int sw = this.k.S(20.0);
                int vw = this.k.S(34.0);
                int x0 = right - (sw * 2 + vw);
                String v = on ? EnchantSearch.roman(lv) + "+" : "—";
                Px.textCenter(
                        g,
                        v,
                        x0 + sw + vw / 2,
                        cyy + (chh - this.k.S(11.0)) / 2,
                        Px.Weight.SEMIBOLD,
                        this.k.S(11.0),
                        on ? text : dim);

                for (int s = 0; s < 2; s++) {
                    int bx = s == 0 ? x0 : x0 + sw + vw;
                    boolean bh = inList && PromptScreen.Kit.in((double) mx, (double) my, bx, cyy, sw, chh);
                    if (bh) {
                        Px.round(g, bx, cyy, sw, chh, this.k.S(5.0), PromptScreen.Kit.inset(true));
                    }

                    Px.textCenter(
                            g,
                            s == 0 ? "−" : "+",
                            bx + sw / 2,
                            cyy + (chh - this.k.S(12.0)) / 2,
                            Px.Weight.SEMIBOLD,
                            this.k.S(12.0),
                            bh ? text : dim);
                    int step = s == 0 ? -1 : 1;
                    this.hits.add(new Hit(bx, Math.max(cyy, this.listTop), sw, chh, () -> {
                        int to = Math.max(0, Math.min(e.max(), lv + step));
                        this.set(e, to);
                        UiSound.play(to > lv ? "toggle_on" : "toggle_off");
                    }));
                }

                right = x0 - this.k.S(10.0);
            }

            Px.fade = kf;
        } else if (ok > 0.01F) {
            float kf = Px.fade;
            Px.fade = kf * ok;
            int cs = this.k.S(14.0);
            ArcanicMenu.glyph(g, "check", right - cs, y + (h - cs) / 2, cs, acc);
            Px.fade = kf;
            right -= cs + this.k.S(10.0);
        }

        int nx = x + is + this.k.S(10.0);
        int nc = Px.mix(Px.mix(dim, PromptScreen.Kit.T().muted(), hk), text, ok);
        Px.text(
                g,
                Px.ellipsize(e.name(), Px.Weight.MEDIUM, fs, right - nx),
                nx,
                y + (h - fs) / 2,
                Px.Weight.MEDIUM,
                fs,
                nc);
        int top = Math.max(y, this.listTop);
        int bottom = Math.min(y + h, this.listBottom);
        if (bottom > top) {
            this.hits.add(0, new Hit(x - this.k.S(6.0), top, w + this.k.S(12.0), bottom - top, () -> {
                this.set(e, on ? 0 : 1);
                UiSound.play(on ? "toggle_off" : "toggle_on");
            }));
        }
    }

    private void bar(GuiGraphics g, int mx, int my, int total, float max) {
        this.barMax = max;
        if (!(max < 1.0F)) {
            int view = this.listBottom - this.listTop;
            this.barX = this.cx + this.cw - this.k.S(12.0);
            this.barH = view;
            this.thumbH = Math.max(this.k.S(30.0), Math.round((float) view * (float) view / (float) total));
            this.thumbY = this.listTop + Math.round((float) (view - this.thumbH) * Math.min(1.0F, this.shown / max));
            boolean held = !Float.isNaN(this.grab);
            boolean hv = held
                    || PromptScreen.Kit.in(
                            (double) mx, (double) my, this.barX - this.k.S(8.0), this.listTop, this.k.S(20.0), view);
            float hk = this.anim.to("bar", hv ? 1.0F : 0.0F, 18.0F);
            int wdt = Math.max(2, this.k.S(3.0));
            int tw = Math.max(wdt, Math.round((float) this.k.S(3.0) + (float) this.k.S(2.0) * hk));
            Px.round(g, this.barX - wdt / 2, this.listTop, wdt, view, wdt / 2, PromptScreen.Kit.inset(hv));
            Px.round(
                    g,
                    this.barX - tw / 2,
                    this.thumbY,
                    tw,
                    this.thumbH,
                    tw / 2,
                    Px.mix(ArcanicMenu.acc(), -1, 0.2F * hk));
        }
    }

    private void dragBar(double my) {
        if (!(this.barMax < 1.0F)) {
            float f = (float)
                    ((my - (double) this.listTop - (double) this.grab) / (double) Math.max(1, this.barH - this.thumbH));
            this.scroll = this.shown = Math.max(0.0F, Math.min(1.0F, f)) * this.barMax;
        }
    }

    static {
        ITEMS = new Item[][] {
            new Item[0],
            {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS},
            {Items.NETHERITE_SWORD, Items.NETHERITE_AXE},
            {Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE},
            {Items.BOW, Items.CROSSBOW},
            {Items.TRIDENT, Items.MACE},
            {Items.FISHING_ROD}
        };
        BOOK = new ItemStack(Items.ENCHANTED_BOOK);
    }

    private static record Ench(String id, String name, int max, int kind, ItemStack icon) {}

    private static record Hit(int x, int y, int w, int h, Runnable run) {}

    private static record Line(String head, int count, Ench e, int x, int y, int w, String key) {}
}
