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

import net.arcanic.visuals.arc.Capes;
import net.arcanic.visuals.arc.MeleeAnimator;
import net.arcanic.visuals.arc.MeleeModels;
import net.arcanic.visuals.arc.MeleePreview;
import net.arcanic.visuals.core.*;
import net.arcanic.visuals.cosmetic.Wearables;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.arcanic.visuals.render.TexWarm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/** The wardrobe page of the menu: capes, knives and the worn models, each in its own tab. */
final class WardrobePage {
    private final ArcanicMenu menu;

    WardrobePage(ArcanicMenu menu) {
        this.menu = menu;
    }

    void wardrobe(GuiGraphics g) {
        TexWarm.pump(3_000_000L); // previews decoded in the background land here, a few per frame
        if (menu.wardrobe >= menu.WARD.length) menu.wardrobe = 0;
        switch (menu.wardrobe) {
            case 2 -> {
                wearWardrobe(g, Modules.HEADWEAR, "Головной убор снят", "Головной убор: ");
                return;
            }
            case 3 -> {
                wearWardrobe(g, Modules.BACKPACKS, "Рюкзак снят", "Рюкзак: ");
                return;
            }
            case 4 -> {
                wearWardrobe(g, Modules.WINGS, "Крылья сняты", "Крылья: ");
                return;
            }
            case 5 -> {
                wearWardrobe(g, Modules.COMPANIONS, "Питомец убран", "Питомец: ");
                return;
            }
            default -> {}
        }
        boolean capes = menu.wardrobe == 0;
        int cols = capes ? 5 : 3, gap = menu.S(12), cw = (menu.cw0 - gap * (cols - 1)) / cols;
        int ch = capes ? Math.round(cw * 1.4f) : menu.S(180);
        int count = capes
                ? Capes.designs().size()
                : net.arcanic.visuals.arc.MeleeCatalog.all().size();
        int total = ((count + cols - 1) / cols) * (ch + gap);
        String key = "w:" + menu.wardrobe;
        float sc = Math.round(menu.scrolled(key, total, menu.top, menu.bottom));
        g.enableScissor(menu.cx0 - menu.S(12), menu.top, menu.cx0 + menu.cw0 + menu.S(12), menu.bottom);
        g.pose().pushMatrix();
        g.pose().translate(0, -sc);
        int i = 0;
        if (capes)
            for (String design : Capes.designs()) {
                int x = menu.cx0 + (i % cols) * (cw + gap), y = menu.top + menu.S(2) + (i / cols) * (ch + gap);
                if (y - sc > menu.bottom || y + ch - sc < menu.top) {
                    i++;
                    continue;
                } // off screen: nothing to draw
                g.nextStratum();
                boolean worn = Modules.CAPES.enabled() && Modules.CAPES.design.is(design);
                float app = menu.ease((menu.now - menu.contentAt - Math.min(i, 14) * 24L) / 320f);
                float hv = garment(g, "cape:" + design, x, y, cw, ch, sc, worn, app);
                float kf = Px.fade;
                Px.fade = kf * app;
                int artH = ch - menu.S(70),
                        artW = Math.round(artH * 10f / 16),
                        lift = Math.round((1 - app) * menu.S(10));
                var strip = Capes.strip(design);
                if (strip != null)
                    g.blit(
                            RenderPipelines.GUI_TEXTURED,
                            strip.id(),
                            x + (cw - artW) / 2,
                            y + menu.S(16) + lift,
                            0,
                            strip.frame() * 256,
                            artW,
                            artH,
                            160,
                            256,
                            160,
                            256 * strip.frames(),
                            Px.a(0xFFFFFF, Math.round(Px.fade * 255)));
                garmentLabel(g, x, y + ch - menu.S(44) + lift, cw, design, worn, hv);
                Px.fade = kf;
                if (!menu.under() && app > .3f)
                    menu.clipped(
                            x,
                            y,
                            cw,
                            ch,
                            sc,
                            menu.top,
                            menu.bottom,
                            () -> {
                                if (worn) {
                                    Modules.CAPES.setEnabled(false);
                                    UiSound.play("drain");
                                    Toasts.setting("Плащ снят", false);
                                } else {
                                    Modules.CAPES.design.set(design);
                                    Modules.CAPES.setEnabled(true);
                                    UiSound.play("charge");
                                    Toasts.setting("Плащ: " + Lang.tr(design), true);
                                }
                                Config.dirty();
                            },
                            null);
                i++;
            }
        else
            for (var skin : net.arcanic.visuals.arc.MeleeCatalog.all()) {
                int x = menu.cx0 + (i % cols) * (cw + gap), y = menu.top + menu.S(2) + (i / cols) * (ch + gap);
                if (y - sc > menu.bottom || y + ch - sc < menu.top) {
                    i++;
                    continue;
                }
                g.nextStratum();
                boolean worn = Modules.KNIVES.enabled() && Modules.KNIVES.skin.is(skin.getDisplayName());
                float app = menu.ease((menu.now - menu.contentAt - Math.min(i, 14) * 24L) / 320f);
                float hv = garment(g, "knife:" + skin.getKey(), x, y, cw, ch, sc, worn, app);
                float kf = Px.fade;
                Px.fade = kf * app;
                int lift = Math.round((1 - app) * menu.S(10));
                MeleePreview.draw(
                        g,
                        skin,
                        x + menu.S(10),
                        y + menu.S(8) + lift,
                        cw - menu.S(20),
                        ch - menu.S(62),
                        hv,
                        Math.round(menu.top + sc),
                        Math.round(menu.bottom + sc));
                garmentLabel(g, x, y + ch - menu.S(44) + lift, cw, skin.getDisplayName(), worn, hv);
                Px.fade = kf;
                if (!menu.under() && app > .3f)
                    menu.clipped(
                            x,
                            y,
                            cw,
                            ch,
                            sc,
                            menu.top,
                            menu.bottom,
                            () -> {
                                if (worn) {
                                    Modules.KNIVES.setEnabled(false);
                                    UiSound.play("drain");
                                    Toasts.setting("Нож снят", false);
                                } else {
                                    Modules.KNIVES.skin.set(skin.getDisplayName());
                                    Modules.KNIVES.setEnabled(true);
                                    MeleeAnimator.resetAll();
                                    MeleeModels.preload(skin);
                                    UiSound.play("charge");
                                    Toasts.setting("Нож: " + skin.getDisplayName(), true);
                                }
                                Config.dirty();
                            },
                            null);
                i++;
            }
        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        menu.edges(g, menu.cx0 - menu.S(12), menu.cw0 + menu.S(24), menu.top, menu.bottom, sc, menu.baseColour());
        menu.scrollbar(g, menu.cx0 + menu.cw0 + menu.S(14), menu.top, menu.bottom, total, sc, key);
    }

    void wearWardrobe(GuiGraphics g, Wearables master, String offToast, String onPrefix) {
        var items = master.items();
        int cols = 6, gap = menu.S(10), cw = (menu.cw0 - gap * (cols - 1)) / cols, ch = cw + menu.S(30);
        int total = ((items.size() + cols - 1) / cols) * (ch + gap);
        String key = "w:" + menu.wardrobe;
        float sc = Math.round(menu.scrolled(key, total, menu.top, menu.bottom));
        g.enableScissor(menu.cx0 - menu.S(12), menu.top, menu.cx0 + menu.cw0 + menu.S(12), menu.bottom);
        g.pose().pushMatrix();
        g.pose().translate(0, -sc);
        for (int i = 0; i < items.size(); i++) {
            int x = menu.cx0 + (i % cols) * (cw + gap), y = menu.top + menu.S(2) + (i / cols) * (ch + gap);
            if (y - sc > menu.bottom || y + ch - sc < menu.top) continue;
            g.nextStratum();
            var it = items.get(i);
            String name = it.name();
            boolean worn = master.enabled() && master.design.is(name);
            float app = menu.ease((menu.now - menu.contentAt - Math.min(i, 14) * 24L) / 320f);
            float hv = garment(g, master.id + ":" + it.n(), x, y, cw, ch, sc, worn, app);
            float kf = Px.fade;
            Px.fade = kf * app;
            int lift = Math.round((1 - app) * menu.S(10)), art = ch - menu.S(44), size = Math.min(cw - menu.S(20), art);
            size = Math.round(size * (1 + .05f * hv));
            var icon = Wearables.icon(it);
            if (TexWarm.ready(icon))
                g.blit(
                        RenderPipelines.GUI_TEXTURED,
                        icon,
                        x + (cw - size) / 2,
                        y + menu.S(10) + lift + (art - size) / 2,
                        0,
                        0,
                        size,
                        size,
                        128,
                        128,
                        128,
                        128,
                        Px.a(0xFFFFFF, Math.round(Px.fade * 255)));
            petLabel(g, x, y + ch - menu.S(30) + lift, cw, name, worn, hv);
            Px.fade = kf;
            if (!menu.under() && app > .3f)
                menu.clipped(
                        x,
                        y,
                        cw,
                        ch,
                        sc,
                        menu.top,
                        menu.bottom,
                        () -> {
                            if (worn) {
                                master.setEnabled(false);
                                UiSound.play("drain");
                                Toasts.setting(offToast, false);
                            } else {
                                master.design.set(name);
                                master.setEnabled(true);
                                UiSound.play("charge");
                                Toasts.setting(Lang.tr(onPrefix) + Lang.tr(name), true);
                            }
                            Config.dirty();
                        },
                        null);
        }
        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        menu.edges(g, menu.cx0 - menu.S(12), menu.cw0 + menu.S(24), menu.top, menu.bottom, sc, menu.baseColour());
        menu.scrollbar(g, menu.cx0 + menu.cw0 + menu.S(14), menu.top, menu.bottom, total, sc, key);
    }

    void petLabel(GuiGraphics g, int x, int y, int w, String name, boolean worn, float hv) {
        int fs = menu.S(12);
        String s = Px.ellipsize(Lang.tr(name), Weight.MEDIUM, fs, w - menu.S(14));
        Px.textCenter(
                g,
                s,
                x + w / 2,
                y + menu.S(4),
                Weight.MEDIUM,
                fs,
                Px.mix(menu.T().text(), Theme.accent2() | 0xFF000000, worn ? 1 : hv * .4f));
        if (worn) Px.circle(g, x + w / 2, y + menu.S(22), menu.S(2), Theme.accent());
    }

    float garment(GuiGraphics g, String key, int x, int y, int w, int h, float sc, boolean worn, float app) {
        boolean hov = !menu.under()
                && menu.my >= menu.top
                && menu.my < menu.bottom
                && menu.hover(x, Math.round(y - sc), w, h);
        if (hov) menu.hot(key);
        float hv = menu.an("gh:" + key, hov ? 1 : 0, 16), wa = menu.tween("gwo:" + key, worn ? 1 : 0, 220);
        float k = Px.fade;
        Px.fade = k * app;
        int yy = y + Math.round((1 - app) * menu.S(10)), R = menu.S(9);
        boolean lt = menu.light();
        Px.round(
                g,
                x,
                yy,
                w,
                h,
                R,
                Px.mix(menu.plate(), lt ? 0xFFFFFFFF : Px.mix(menu.T().bg() | 0xFF000000, 0xFFFFFFFF, .07f), hv));
        if (wa > .001f) Px.round(g, x, yy, w, h, R, Px.a(Theme.accent(), (int) ((lt ? 20 : 32) * wa)));
        Px.outline(
                g,
                x,
                yy,
                w,
                h,
                R,
                Px.mix(Px.a(menu.T().text(), (int) ((lt ? 20 : 13) + 14 * hv)), Px.a(Theme.accent(), 230), wa));
        if (wa > .01f) {
            int r = menu.S(10), ccx = x + w - menu.S(12) - r, ccy = yy + menu.S(12) + r;
            float o = Px.fade;
            Px.fade = o * wa;
            Px.circle(g, ccx, ccy, r, menu.acc());
            menu.glyph(g, "check", ccx - menu.S(6), ccy - menu.S(6), menu.S(12), 0xFFFFFFFF);
            Px.fade = o;
        }
        Px.fade = k;
        return hv;
    }

    void garmentLabel(GuiGraphics g, int x, int y, int w, String name, boolean worn, float hv) {
        Px.text(
                g,
                Px.ellipsize(Lang.tr(name), Weight.SEMIBOLD, menu.S(13), w - menu.S(28)),
                x + menu.S(14),
                y,
                Weight.SEMIBOLD,
                menu.S(13),
                menu.T().text());
        Px.text(
                g,
                Lang.tr(worn ? "Надето" : hv > .5f ? "Нажми, чтобы надеть" : "Не надето"),
                x + menu.S(14),
                y + menu.S(19),
                Weight.MEDIUM,
                menu.S(11),
                worn ? menu.accText() : Px.mix(menu.T().dim(), menu.T().muted(), hv));
    }
}
