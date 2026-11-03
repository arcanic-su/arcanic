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
package net.arcanic.visuals.helper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.hud.HudModule;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

public final class HolyEventsHud extends HudModule {
    private final Setting.Num lines = this.add(new Setting.Num("lines", "Строк", 5.0, 1.0, 10.0, 1.0, ""));
    private final Anim anim = new Anim();
    private final Map<String, HolyEvents.Event> seen = new LinkedHashMap<>();
    private final List<Object[]> hits = new ArrayList<>();

    public boolean clickRow(double x, double y) {
        if (this.enabled() && HolyEvents.onHolyWorld()) {
            for (Object[] r : this.hits) {
                int rx = (Integer) r[0];
                int ry = (Integer) r[1];
                int rw = (Integer) r[2];
                int rh = (Integer) r[3];
                if (x >= (double) rx && x < (double) (rx + rw) && y >= (double) ry && y < (double) (ry + rh)) {
                    Minecraft.getInstance().setScreen((Screen) null);
                    Modules.HOLY_EVENTS.join((HolyEvents.Event) r[4]);
                    this.hits.clear();
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public HolyEventsHud() {
        super(
                "holy_events_hud",
                "Ивенты",
                "Ивенты всех анархий HolyWorld; клик в чате переносит туда",
                false,
                0.008,
                0.3);
    }

    public static int colour(String rarity) {
        return switch (rarity) {
            case "Легендарный", "Мифический", "Роскошный" -> -19641;
            case "Эпический", "Зажиточный" -> 0xFFB18CFF;
            case "Редкий" -> 0xFF6EC1FF;
            case "Босс" -> -50373;
            case "Взрывной", "Смертельный" -> -38037;
            case "Мирный" -> 0xFF7BE0A0;
            default -> 0xFF8A90A2;
        };
    }

    private static String number(String server) {
        String n = server.replaceAll("\\D+", "");
        return n.isEmpty() ? server : "#" + n;
    }

    private static int mcScale(int px) {
        return Math.max(1, Math.round((float) px / 8.0F));
    }

    private static Font mcFont() {
        return Minecraft.getInstance().font;
    }

    private static String anarchy(HolyEvents ev, HolyEvents.Event e) {
        String n = ev.serverName(e.server());
        return n.equals(e.server()) ? number(e.server()) : n;
    }

    private List<HolyEvents.Event> rows() {
        HolyEvents ev = Modules.HOLY_EVENTS;
        List<HolyEvents.Event> out = new ArrayList<>();
        if (!ev.enabled()) {
            return out;
        } else {
            for (HolyEvents.Event e : ev.sorted()) {
                if (ev.wanted(e)) {
                    out.add(e);
                }

                if (out.size() >= this.lines.i()) {
                    break;
                }
            }

            return out;
        }
    }

    private static String key(HolyEvents.Event e) {
        return e.server() + "|" + e.id();
    }

    public boolean shown() {
        return HolyEvents.onHolyWorld() && (!this.rows().isEmpty() || !this.seen.isEmpty());
    }

    public void render(GuiGraphics g, boolean editing) {
        this.anim.frame();
        HolyEvents ev = Modules.HOLY_EVENTS;
        List<HolyEvents.Event> list = this.rows();
        boolean sample = list.isEmpty() && editing;
        if (sample) {
            list = List.of(
                    new HolyEvents.Event("LITE_ANARCHY_19", "CUBE", "Кубик", "Легендарный", "a"),
                    new HolyEvents.Event("LITE_ANARCHY_16", "JAYCOB", "Опытный Тыпо", "Эпический", "b"),
                    new HolyEvents.Event("LITE_ANARCHY_25", "SHIP", "Таинственный корабль", "", "c"));
        }

        List<String> want = new ArrayList<>();

        for (HolyEvents.Event e : list) {
            want.add(key(e));
            this.seen.put(key(e), e);
        }

        Theme.Palette p = Theme.p();
        int text = p.text();
        int muted = 0xFF8A91A0;
        int nfs = Math.max(9, this.U(6.5));
        int sfs = Math.max(8, this.U(5.6));
        int tfs = Math.max(9, this.U(8.0));
        int rowH = this.U(23.0);
        int gap = this.U(3.5);
        String title = "Ивенты";
        int logoH = this.U(10.0);
        int logoW = Math.round((float) (logoH * 103) / 96.0F);
        float target =
                (float) (this.U(5.0) + logoW + this.U(4.0) + Px.textWidth(title, Px.Weight.BOLD, tfs) + this.U(24.0));
        float rowsH = 0.0F;
        List<String> gone = new ArrayList<>();

        for (Map.Entry<String, HolyEvents.Event> en : this.seen.entrySet()) {
            float vis = this.anim.to("in:" + en.getKey(), want.contains(en.getKey()) ? 1.0F : 0.0F, 14.0F);
            if (!want.contains(en.getKey()) && vis < 0.01F) {
                gone.add(en.getKey());
            } else {
                HolyEvents.Event te = (HolyEvents.Event) en.getValue();
                int fs = mcScale(sfs);
                target = Math.max(target, (float) (this.U(19.0)
                        + Px.textWidth(te.name(), Px.Weight.SEMIBOLD, nfs)
                        + this.U(4.0)
                        + (te.rarity().isEmpty() ? 0 : mcFont().width(te.rarity()) * fs)
                        + this.U(6.0)));
                target = Math.max(target, (float)
                        (this.U(19.0) + Px.textWidth(anarchy(ev, te), Px.Weight.SEMIBOLD, sfs) + this.U(6.0)));
                rowsH += vis * (float) (rowH + gap);
            }
        }

        gone.forEach(this.seen::remove);
        boolean anyFresh = false;

        for (HolyEvents.Event e : list) {
            anyFresh |= !sample && ev.fresh(e);
        }

        float footK = 0.0F;
        this.w = Math.round(
                this.anim.to("w", Math.min((float) this.U(160.0), Math.max((float) this.U(100.0), target)), 14.0F));
        this.h = Math.round(this.anim.to(
                "h",
                (float) this.U(22.5)
                        + (rowsH > 0.0F ? (float) this.U(2.0) + rowsH : 0.0F)
                        + footK * (float) this.U(13.0)
                        + (float) this.U(2.0),
                14.0F));
        if (!this.seen.isEmpty() || editing) {
            this.plate(g, this.w, this.h);
            g.enableScissor(0, 0, this.w, this.h);
            float base = Px.fade;
            int ly = this.U(5.0) + (tfs - logoH) / 2;
            HolyEvents.picture(g, HolyEvents.LOGO, 103, 96, this.U(5.0), ly - this.U(1.0), logoW, logoH + this.U(1.0));
            Px.text(g, title, this.U(5.0) + logoW + this.U(4.0), this.U(4.5), Px.Weight.BOLD, tfs, text);
            int n = sample ? list.size() : ev.current().size();
            if (n > 0) {
                this.badge(
                        g,
                        String.valueOf(n),
                        this.w - this.U(5.0),
                        this.U(4.5) + (tfs - this.U(10.5)) / 2,
                        true,
                        muted);
            }

            float y = (float) (this.U(22.5) + this.U(2.0));
            Minecraft mcI = Minecraft.getInstance();
            boolean chat = !editing && !sample && mcI.screen instanceof ChatScreen;
            int sw = Px.width();
            int sh = Px.height();
            int ox = Math.max(0, Math.min(sw - this.w, this.x(sw)));
            int oy = Math.max(0, Math.min(sh - this.h, this.y(sh)));
            double mx = mcI.mouseHandler.xpos()
                    * (double) mcI.getWindow().getWidth()
                    / (double) Math.max(1, mcI.getWindow().getScreenWidth());
            double my = mcI.mouseHandler.ypos()
                    * (double) mcI.getWindow().getHeight()
                    / (double) Math.max(1, mcI.getWindow().getScreenHeight());
            this.hits.clear();
            float time = (float) (System.nanoTime() % 1000000000000L) / 1.0E9F;

            for (Map.Entry<String, HolyEvents.Event> en : this.seen.entrySet()) {
                HolyEvents.Event e = (HolyEvents.Event) en.getValue();
                float vis = this.anim.to("in:" + en.getKey(), want.contains(en.getKey()) ? 1.0F : 0.0F, 14.0F);
                if (!(vis < 0.01F)) {
                    int ry = Math.round(y);
                    int slide = Math.round((1.0F - vis) * (float) this.U(4.0));
                    Px.fade = base * vis;
                    boolean fresh = !sample && ev.fresh(e);
                    int hc = HolyEvents.headColour(e);
                    int rx0 = this.U(2.0);
                    int ry0 = ry - this.U(2.0);
                    int rw0 = this.w - this.U(4.0);
                    int rh0 = rowH + this.U(3.0);
                    boolean over = chat
                            && mx >= (double) (ox + rx0)
                            && mx < (double) (ox + rx0 + rw0)
                            && my >= (double) (oy + ry0)
                            && my < (double) (oy + ry0 + rh0);
                    float hk = this.anim.to("hv:" + en.getKey(), over ? 1.0F : 0.0F, 18.0F);
                    if (chat && vis > 0.5F) {
                        this.hits.add(new Object[] {ox + rx0, oy + ry0, rw0, rh0, e});
                    }

                    int hs = this.U(12.0);
                    int hx = this.U(4.0) + slide;
                    int line = this.U(10.5);
                    Identifier head = HolyEvents.head(e.type());
                    if (fresh) {
                        float b = 0.5F + 0.5F * (float) Math.sin((double) time * Math.PI * 2.0 * 1.1);
                        Px.glow(
                                g,
                                hx - this.U(3.0),
                                ry - this.U(2.0),
                                hs + this.U(6.0),
                                hs + this.U(6.0),
                                Px.a(hc & 0xFFFFFF, 40 + Math.round(60.0F * b)));
                    }

                    if (head != null) {
                        HolyEvents.picture(g, head, 38, 38, hx, ry, hs, hs);
                    } else {
                        int ds = this.U(4.5);
                        Px.round(g, hx + (hs - ds) / 2, ry + (hs - ds) / 2, ds, ds, ds / 2, hc);
                    }

                    int tx = this.U(19.0) + slide;
                    int fs = mcScale(sfs);
                    int rw = e.rarity().isEmpty()
                            ? 0
                            : Math.round((float) (mcFont().width(e.rarity()) * fs)) + this.U(4.0);
                    String name = Px.ellipsize(e.name(), Px.Weight.SEMIBOLD, nfs, this.w - tx - this.U(5.0) - rw);
                    Px.text(g, name, tx, ry + (line - nfs) / 2, Px.Weight.SEMIBOLD, nfs, text);
                    if (rw > 0) {
                        int rx = tx + Px.textWidth(name, Px.Weight.SEMIBOLD, nfs) + this.U(4.0);
                        g.pose().pushMatrix();
                        g.pose().translate((float) rx, (float) (ry + (line - 8 * fs) / 2));
                        if (fs != 1) {
                            g.pose().scale((float) fs, (float) fs);
                        }

                        g.drawString(
                                mcFont(),
                                (String) e.rarity(),
                                0,
                                0,
                                Px.am(colour(e.rarity()) | 0xFF000000, Px.fade),
                                true);
                        g.pose().popMatrix();
                    }

                    Px.text(g, anarchy(ev, e), tx, ry + line, Px.Weight.SEMIBOLD, sfs, text);
                    float fill = hk * hk * (3.0F - 2.0F * hk);
                    int full = this.w - tx - this.U(5.0);
                    int bw = Math.round((float) this.U(22.0) + (float) (full - this.U(22.0)) * fill);
                    int bh = Math.max(2, this.U(1.4));
                    int by = ry + rowH - this.U(2.5);
                    Px.round(g, tx, by, bw, bh, bh / 2, hc);
                    y += vis * (float) (rowH + gap);
                }
            }

            Px.fade = base;
            g.disableScissor();
        }
    }
}
