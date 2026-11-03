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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Keys;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.fx.ItemZones;
import net.arcanic.visuals.helper.HolyFeatures;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.gui.GuiGraphics;

final class ModulePopup {
    private final ArcanicMenu menu;
    String itemPage;
    private final Set<String> more = new HashSet<>();
    private static final int FIRST = 5;
    private long pageAt;
    private int pageDir = 1;
    private static final int GROUP = 30;
    private static final int ROW = 36;

    ModulePopup(ArcanicMenu menu) {
        this.menu = menu;
    }

    void openItem(String key) {
        this.pageDir = key == null ? -1 : 1;
        this.itemPage = key;
        this.pageAt = this.menu.now;
        if (this.menu.popMod != null) {
            this.menu.resetScroll("pm:" + this.menu.popMod.id);
        }
    }

    void modulePopup(GuiGraphics g) {
        float k = this.menu.an("pm", this.menu.popMod != null ? 1.0F : 0.0F, this.menu.popMod != null ? 14.0F : 22.0F);
        if (this.menu.popMod != null) {
            this.menu.popShown = this.menu.popMod;
        } else if (k < 0.01F) {
            this.menu.popShown = null;
        }

        Module m = this.menu.popShown;
        if (m != null) {
            float a = 1.0F - (float) Math.pow((double) (1.0F - Math.min(1.0F, k)), 4.0);
            boolean live = this.menu.popMod != null && !this.menu.blocked() && k > 0.5F;
            int sw = Px.width();
            int sh = Px.height();
            ItemZones var10000;
            if (m instanceof ItemZones) {
                ItemZones z = (ItemZones) m;
                var10000 = z;
            } else {
                var10000 = null;
            }

            ItemZones iz = var10000;
            ItemZones.Item page = iz != null && this.itemPage != null ? iz.item(this.itemPage) : null;
            List<Setting<?>> list = new ArrayList<>();
            if (page != null) {
                list.addAll(page.shown);
            } else {
                for (Setting<?> s : m.settings) {
                    if (s.visible()) {
                        list.add(s);
                    }
                }
            }

            List<Setting<?>> extra = new ArrayList<>();
            if (iz == null && list.size() > 6) {
                extra = new ArrayList(list.subList(5, list.size()));
                list = new ArrayList(list.subList(0, 5));
            }

            boolean unfolded = this.more.contains(m.id);
            this.menu.compact = true;
            this.menu.bands = true;
            int w = this.menu.S(iz != null ? 340.0 : 292.0);
            int pad = this.menu.S(16.0);
            int cw = w - pad * 2;
            int head = this.menu.S(58.0);
            int moreH = this.menu.S(34.0);
            int inner;
            if (iz != null && page == null) {
                inner = head + this.listH(iz) + this.menu.S(8.0);
            } else {
                inner = head;

                for (Setting<?> s : list) {
                    inner += this.menu.rowH(s, cw);
                }

                if (!extra.isEmpty()) {
                    inner += moreH;
                    if (unfolded) {
                        for (Setting<?> s : extra) {
                            inner += this.menu.rowH(s, cw);
                        }
                    }
                }

                inner += iz != null ? this.menu.S(10.0) : this.menu.S(10.0) + this.menu.S(40.0) + this.menu.S(4.0);
            }

            int h = Math.min(inner, Math.min(this.menu.ph, sh - this.menu.S(24.0)));
            boolean fitsR = this.menu.px + this.menu.pw + this.menu.S(14.0) + w <= sw - this.menu.S(10.0);
            boolean fitsL = this.menu.px - this.menu.S(14.0) - w >= this.menu.S(10.0);
            boolean leftColumn = this.menu.ancX + this.menu.ancW / 2 < this.menu.px + this.menu.pw / 2;
            boolean right = leftColumn ? !fitsL : fitsR || !fitsL;
            int x = right ? this.menu.px + this.menu.pw + this.menu.S(10.0) : this.menu.px - this.menu.S(10.0) - w;
            if (x < this.menu.S(10.0)) {
                right = true;
                x = Math.min(sw - w - this.menu.S(10.0), this.menu.ancX + this.menu.ancW + this.menu.S(10.0));
            }

            int y = Math.max(
                    Math.max(this.menu.S(12.0), this.menu.py),
                    Math.min(
                            Math.min(sh - this.menu.S(12.0), this.menu.py + this.menu.ph) - h,
                            this.menu.ancY - this.menu.S(6.0)));
            this.menu.pmX = x;
            this.menu.pmY = y;
            this.menu.pmW = w;
            this.menu.pmH = h;
            float kf = Px.fade;
            Px.fade = kf * Math.min(1.0F, k * 1.6F);
            g.pose().pushMatrix();
            if (a < 0.999F) {
                float grow = 0.96F + 0.04F * a;
                float ox = right ? (float) x : (float) (x + w);
                float oy = (float) (y + Math.min(h, this.menu.S(60.0)));
                g.pose().translate((1.0F - a) * (float) (right ? -this.menu.S(10.0) : this.menu.S(10.0)), 0.0F);
                g.pose().translate(ox, oy);
                g.pose().scale(grow, grow);
                g.pose().translate(-ox, -oy);
            }

            int bg = ArcanicMenu.panelBg();
            int R = this.menu.S(12.0);
            int var10002 = y + this.menu.S(8.0);
            int var10005 = this.menu.S(24.0);
            ArcanicMenu var10007 = this.menu;
            Px.shadow(g, x, var10002, w, h, var10005, Px.a(0, ArcanicMenu.light() ? 30 : 130));
            Px.round(g, x, y, w, h, R, bg);
            Px.outline(g, x, y, w, h, R, ArcanicMenu.hairline(16));
            this.menu.topLight(g, x, y, w, R);
            int t = y + this.menu.S(3.0);
            int b = y + h - this.menu.S(3.0);
            float sc = (float) Math.round(this.menu.scrolled("pm:" + m.id, inner, y, y + h));
            g.enableScissor(x, t, x + w, b);
            g.pose().pushMatrix();
            float pt = iz == null ? 1.0F : ArcanicMenu.ease((float) (this.menu.now - this.pageAt) / 260.0F);
            g.pose().translate((float) Math.round((1.0F - pt) * (float) this.menu.S(18.0) * (float) this.pageDir), -sc);
            float pf = Px.fade;
            Px.fade = pf * (iz == null ? 1.0F : 0.3F + 0.7F * pt);
            int cx = x + pad;
            if (iz == null) {
                this.header(g, m, cx, y, cw, sc, t, b, live);
            } else if (page == null) {
                Px.text(
                        g,
                        m.title(),
                        cx,
                        y + this.menu.S(15.0),
                        Px.Weight.SEMIBOLD,
                        this.menu.S(15.0),
                        ArcanicMenu.T().text());
                Px.text(
                        g,
                        Lang.tr("Нажми на предмет, чтобы настроить его"),
                        cx,
                        y + this.menu.S(35.0),
                        Px.Weight.MEDIUM,
                        this.menu.S(11.0),
                        ArcanicMenu.faint());
                this.list(g, iz, cx, y + head, cw, sc, t, b, live);
            } else {
                this.itemHeader(g, page, cx, y, cw, sc, t, b, live);
            }

            if (iz == null || page != null) {
                int cy = this.menu.settings(g, m, list, cx, y + head, cw, sc, t, b, live);
                if (!extra.isEmpty()) {
                    this.moreRow(g, m, extra.size(), unfolded, cx, cy, cw, moreH, sc, t, b, live);
                    cy += moreH;
                    if (unfolded) {
                        cy = this.menu.settings(g, m, extra, cx, cy, cw, sc, t, b, live);
                    }
                }

                if (iz == null) {
                    this.key(g, m, cx, cy + this.menu.S(10.0), cw, sc, t, b, live);
                }
            }

            Px.fade = pf;
            g.pose().popMatrix();
            g.disableScissor();
            g.nextStratum();
            if (inner > h) {
                this.menu.edges(g, x + this.menu.S(1.0), w - this.menu.S(2.0), t, b, sc, bg);
                this.menu.scrollbar(
                        g,
                        x + w - this.menu.S(8.0),
                        t + this.menu.S(10.0),
                        b - this.menu.S(10.0),
                        inner,
                        sc,
                        "pm:" + m.id);
            }

            g.pose().popMatrix();
            Px.fade = kf;
            this.menu.compact = false;
            this.menu.bands = false;
        }
    }

    private void header(GuiGraphics g, Module m, int cx, int y, int cw, float sc, int t, int b, boolean live) {
        int tgW = this.menu.S(30.0);
        int textW = cw - tgW - this.menu.S(12.0);
        Px.text(
                g,
                Px.ellipsize(m.title(), Px.Weight.SEMIBOLD, this.menu.S(15.0), textW),
                cx,
                y + this.menu.S(15.0),
                Px.Weight.SEMIBOLD,
                this.menu.S(15.0),
                ArcanicMenu.T().text());
        Px.text(
                g,
                Px.ellipsize(Lang.tr(m.description), Px.Weight.MEDIUM, this.menu.S(11.0), textW),
                cx,
                y + this.menu.S(35.0),
                Px.Weight.MEDIUM,
                this.menu.S(11.0),
                ArcanicMenu.faint());
        String var10001 = m.id;
        float mOn = this.menu.tween("on:" + var10001, m.enabled() ? 1.0F : 0.0F, 180.0F);
        int tgY = y + this.menu.S(14.0);
        boolean tgHv = live && this.menu.hover(cx + cw - tgW, Math.round((float) tgY - sc), tgW, this.menu.S(30.0));
        this.menu.toggle(g, cx + cw - tgW, tgY, tgW, this.menu.S(30.0), mOn, tgHv ? 1.0F : 0.0F);
        if (live) {
            this.menu.clipped(
                    cx + cw - tgW - this.menu.S(4.0),
                    tgY - this.menu.S(4.0),
                    tgW + this.menu.S(8.0),
                    this.menu.S(38.0),
                    sc,
                    t,
                    b,
                    () -> {
                        if (!m.enabled() && HolyFeatures.forbidden(m)) {
                            HolyFeatures.deny(m);
                        } else {
                            m.toggle();
                            if (m.enabled()) {
                                this.menu.pulse.put(m.id, this.menu.now);
                            }

                            UiSound.play(m.enabled() ? "charge" : "drain");
                            Toasts.module(m, false);
                            Config.dirty();
                        }
                    },
                    (Runnable) null);
        }
    }

    private void moreRow(
            GuiGraphics g,
            Module m,
            int n,
            boolean open,
            int cx,
            int cy,
            int cw,
            int h,
            float sc,
            int t,
            int b,
            boolean live) {
        boolean hv = live
                && this.menu.my >= (double) t
                && this.menu.my < (double) b
                && this.menu.hover(cx, Math.round((float) cy - sc), cw, h);
        if (hv) {
            this.menu.hot("more:" + m.id);
        }

        String var10001 = m.id;
        float hk = this.menu.an("moreH:" + var10001, hv ? 1.0F : 0.0F, 18.0F);
        var10001 = m.id;
        float ok = this.menu.tween("more:" + var10001, open ? 1.0F : 0.0F, 220.0F);
        String label = Lang.tr(open ? "Скрыть" : "Ещё настройки");
        int fs = this.menu.S(12.0);
        int col = Px.mix(ArcanicMenu.sub(), ArcanicMenu.T().text(), hk);
        Px.text(g, label, cx, cy + (h - fs) / 2, Px.Weight.MEDIUM, fs, col);
        int lw = Px.textWidth(label, Px.Weight.MEDIUM, fs);
        Px.text(
                g,
                String.valueOf(n),
                cx + lw + this.menu.S(7.0),
                cy + (h - fs) / 2,
                Px.Weight.MEDIUM,
                fs,
                ArcanicMenu.faint());
        int as = this.menu.S(11.0);
        g.pose().pushMatrix();
        g.pose().translate((float) (cx + cw) - (float) as / 2.0F, (float) cy + (float) h / 2.0F);
        g.pose().rotate((float) Math.PI * ok);
        ArcanicMenu.glyph(g, "chevron-down", -as / 2, -as / 2, as, col);
        g.pose().popMatrix();
        if (live) {
            this.menu.clipped(
                    cx,
                    cy,
                    cw,
                    h,
                    sc,
                    t,
                    b,
                    () -> {
                        if (!this.more.remove(m.id)) {
                            this.more.add(m.id);
                        }

                        UiSound.play("click");
                    },
                    (Runnable) null);
        }
    }

    private void key(GuiGraphics g, Module m, int cx, int cy, int cw, float sc, int t, int b, boolean live) {
        boolean cap = this.menu.binding == m;
        int rowH = this.menu.S(40.0);
        boolean khv = live
                && this.menu.my >= (double) t
                && this.menu.my < (double) b
                && this.menu.hover(cx, Math.round((float) cy - sc), cw, rowH);
        if (khv) {
            this.menu.hot("bind");
        }

        Px.text(
                g,
                Lang.tr("Клавиша"),
                cx,
                cy + (rowH - this.menu.S(13.0)) / 2,
                Px.Weight.MEDIUM,
                this.menu.S(13.0),
                khv ? ArcanicMenu.T().text() : ArcanicMenu.label());
        String kt = cap ? Lang.tr("Нажми клавишу") : (m.bind >= 0 ? Keys.label(m.bind) : "—");
        this.menu.valueBox(g, kt, cx + cw, cy + (rowH - this.menu.S(26.0)) / 2, cap, khv);
        if (live) {
            this.menu.clipped(
                    cx,
                    cy,
                    cw,
                    rowH,
                    sc,
                    t,
                    b,
                    () -> {
                        this.menu.binding = cap ? null : m;
                        UiSound.play("click");
                    },
                    () -> {
                        m.bind = -1;
                        this.menu.binding = null;
                        Config.dirty();
                        UiSound.play("reset");
                    });
        }
    }

    private static Map<String, List<ItemZones.Item>> groups(ItemZones iz) {
        Map<String, List<ItemZones.Item>> out = new LinkedHashMap<>();

        for (ItemZones.Item it : iz.items) {
            ((List) out.computeIfAbsent(it.group, (k) -> new ArrayList<>())).add(it);
        }

        return out;
    }

    private int listH(ItemZones iz) {
        int hh = 0;

        for (List<ItemZones.Item> l : groups(iz).values()) {
            hh += this.menu.S(30.0) + (l.size() + 1) / 2 * this.menu.S(36.0);
        }

        return hh;
    }

    private void list(GuiGraphics g, ItemZones iz, int x, int y, int w, float sc, int t, int b, boolean live) {
        int colW = w / 2;
        int cy = y;

        for (Map.Entry<String, List<ItemZones.Item>> e : groups(iz).entrySet()) {
            Px.text(
                    g,
                    Lang.tr((String) e.getKey()),
                    x,
                    cy + this.menu.S(8.0),
                    Px.Weight.SEMIBOLD,
                    this.menu.S(14.0),
                    ArcanicMenu.T().text());
            cy += this.menu.S(30.0);
            List<ItemZones.Item> l = (List) e.getValue();

            for (int i = 0; i < l.size(); ++i) {
                ItemZones.Item it = (ItemZones.Item) l.get(i);
                int rx = x + i % 2 * colW - this.menu.S(8.0);
                int ry = cy + i / 2 * this.menu.S(36.0);
                int rw = colW - this.menu.S(4.0);
                int rh = this.menu.S(36.0) - this.menu.S(4.0);
                boolean hv = live
                        && this.menu.my >= (double) t
                        && this.menu.my < (double) b
                        && this.menu.hover(rx, Math.round((float) ry - sc), rw, rh);
                if (hv) {
                    this.menu.hot("it:" + it.key);
                }

                String var10001 = it.key;
                float hk = this.menu.an("itH:" + var10001, hv ? 1.0F : 0.0F, 18.0F);
                var10001 = it.key;
                float on = this.menu.tween("itO:" + var10001, it.on.on() ? 1.0F : 0.0F, 200.0F);
                if (hk > 0.01F) {
                    Px.round(g, rx, ry, rw, rh, this.menu.S(8.0), Px.a(ArcanicMenu.cardHi(), (int) (255.0F * hk)));
                }

                int is = this.menu.S(18.0);
                int ix = rx + this.menu.S(8.0);
                int iy = ry + (rh - is) / 2;
                if (Px.fade > 0.35F) {
                    g.pose().pushMatrix();
                    g.pose().translate((float) ix, (float) iy);
                    g.pose().scale((float) is / 16.0F, (float) is / 16.0F);
                    g.renderItem(ArcanicMenu.icon(it.icon), 0, 0);
                    g.pose().popMatrix();
                    g.nextStratum();
                    if (on < 0.999F) {
                        Px.rect(
                                g,
                                ix - 1,
                                iy - 1,
                                is + 2,
                                is + 2,
                                Px.a(Px.mix(ArcanicMenu.panelBg(), ArcanicMenu.cardHi(), hk), (int)
                                        (150.0F * (1.0F - on))));
                    }
                }

                int nx = ix + is + this.menu.S(10.0);
                int ds = this.menu.S(6.0);
                Px.text(
                        g,
                        Px.ellipsize(
                                Lang.tr(it.name),
                                Px.Weight.MEDIUM,
                                this.menu.S(12.0),
                                rx + rw - nx - ds - this.menu.S(14.0)),
                        nx,
                        ry + (rh - this.menu.S(12.0)) / 2,
                        Px.Weight.MEDIUM,
                        this.menu.S(12.0),
                        Px.mix(
                                Px.mix(ArcanicMenu.faint(), ArcanicMenu.label(), on),
                                ArcanicMenu.T().text(),
                                hk));
                if (on > 0.01F) {
                    float f0 = Px.fade;
                    Px.fade = f0 * on;
                    Px.round(
                            g,
                            rx + rw - this.menu.S(10.0) - ds,
                            ry + (rh - ds) / 2,
                            ds,
                            ds,
                            ds / 2,
                            it.zone.argb() | 0xFF000000);
                    Px.fade = f0;
                }

                if (live) {
                    this.menu.clipped(
                            rx,
                            ry,
                            rw,
                            rh,
                            sc,
                            t,
                            b,
                            () -> {
                                UiSound.play("click");
                                this.openItem(it.key);
                            },
                            () -> {
                                it.on.toggle();
                                UiSound.play(it.on.on() ? "toggle_on" : "toggle_off");
                                Config.dirty();
                            });
                }
            }

            cy += (l.size() + 1) / 2 * this.menu.S(36.0);
        }
    }

    private void itemHeader(
            GuiGraphics g, ItemZones.Item it, int cx, int y, int cw, float sc, int t, int b, boolean live) {
        int bs = this.menu.S(28.0);
        int by = y + this.menu.S(14.0);
        boolean bh = live && this.menu.hover(cx, Math.round((float) by - sc), bs, bs);
        if (bh) {
            this.menu.hot("itBack");
        }

        float bk = this.menu.an("itBack", bh ? 1.0F : 0.0F, 18.0F);
        Px.round(g, cx, by, bs, bs, this.menu.S(8.0), Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), bk));
        int gs = this.menu.S(14.0);
        g.pose().pushMatrix();
        g.pose()
                .translate(
                        (float) cx + (float) bs / 2.0F - (float) Math.round(bk * (float) this.menu.S(2.0)),
                        (float) by + (float) bs / 2.0F);
        g.pose().rotate((float) Math.PI);
        ArcanicMenu.glyph(
                g,
                "chevron-right",
                -gs / 2,
                -gs / 2,
                gs,
                Px.mix(ArcanicMenu.sub(), ArcanicMenu.T().text(), bk));
        g.pose().popMatrix();
        int is = this.menu.S(20.0);
        int ix = cx + bs + this.menu.S(12.0);
        if (Px.fade > 0.35F) {
            g.pose().pushMatrix();
            g.pose().translate((float) ix, (float) by + (float) (bs - is) / 2.0F);
            g.pose().scale((float) is / 16.0F, (float) is / 16.0F);
            g.renderItem(ArcanicMenu.icon(it.icon), 0, 0);
            g.pose().popMatrix();
            g.nextStratum();
        }

        Px.text(
                g,
                Lang.tr(it.name),
                ix + is + this.menu.S(10.0),
                by + (bs - this.menu.S(15.0)) / 2,
                Px.Weight.SEMIBOLD,
                this.menu.S(15.0),
                ArcanicMenu.T().text());
        if (live) {
            this.menu.clipped(
                    cx,
                    by,
                    bs,
                    bs,
                    sc,
                    t,
                    b,
                    () -> {
                        UiSound.play("pop", 0.9F, 0.6F);
                        this.openItem((String) null);
                    },
                    (Runnable) null);
        }
    }
}
