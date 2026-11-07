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

import java.util.List;
import net.arcanic.visuals.arc.Capes;
import net.arcanic.visuals.arc.MeleeAnimator;
import net.arcanic.visuals.arc.MeleeCatalog;
import net.arcanic.visuals.arc.MeleeDesign;
import net.arcanic.visuals.arc.MeleeModels;
import net.arcanic.visuals.arc.MeleePreview;
import net.arcanic.visuals.arc.MeleeSkins;
import net.arcanic.visuals.arc.MeleeSlot;
import net.arcanic.visuals.arc.PremiumSkins;
import net.arcanic.visuals.core.ArcanicAccount;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.cosmetic.WearPreview;
import net.arcanic.visuals.cosmetic.Wearables;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.TexWarm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class WardrobePage {
    private static final int PREMIUM = 0xFF9B5CFF;
    private final ArcanicMenu menu;

    WardrobePage(ArcanicMenu menu) {
        this.menu = menu;
    }

    void wardrobe(GuiGraphics g) {
        TexWarm.pump(3000000L);
        ArcanicMenu arcanicMenu = this.menu;
        ArcanicMenu arcanicMenu2 = this.menu;
        if (ArcanicMenu.wardrobe >= ArcanicMenu.WARD.length) {
            arcanicMenu = this.menu;
            ArcanicMenu.wardrobe = 0;
        }

        arcanicMenu = this.menu;
        switch (ArcanicMenu.wardrobe) {
            case 3:
                this.wearWardrobe(g, Modules.HEADWEAR, "Головной убор снят", "Головной убор: ");
                return;
            case 4:
                this.wearWardrobe(g, Modules.BACKPACKS, "Рюкзак снят", "Рюкзак: ");
                return;
            case 5:
                this.wearWardrobe(g, Modules.WINGS, "Крылья сняты", "Крылья: ");
                return;
            default:
                arcanicMenu = this.menu;
                boolean capes = ArcanicMenu.wardrobe == 0;
                MeleeSlot slot = ArcanicMenu.wardSlot(ArcanicMenu.wardrobe);
                List<MeleeDesign> designs = slot == null ? List.of() : MeleeCatalog.of(slot);
                MeleeSkins owner = slot == null ? Modules.KNIVES : MeleeSkins.of(slot);
                boolean guns = slot == MeleeSlot.BOW;
                int cols = capes ? 5 : 3;
                int gap = this.menu.S(12.0);
                int cw = (this.menu.cw0 - gap * (cols - 1)) / cols;
                int ch = capes ? Math.round((float) cw * 1.4F) : this.menu.S(180.0);
                int count = capes ? Capes.designs().size() : designs.size();
                int total = (count + cols - 1) / cols * (ch + gap);
                arcanicMenu = this.menu;
                String key = "w:" + ArcanicMenu.wardrobe;
                float sc = (float) Math.round(this.menu.scrolled(key, total, this.menu.top, this.menu.bottom));
                g.enableScissor(
                        this.menu.cx0 - this.menu.S(12.0),
                        this.menu.top,
                        this.menu.cx0 + this.menu.cw0 + this.menu.S(12.0),
                        this.menu.bottom);
                g.pose().pushMatrix();
                g.pose().translate(0.0F, -sc);
                int i = 0;
                if (capes) {
                    for (String design : Capes.designs()) {
                        int x = this.menu.cx0 + i % cols * (cw + gap);
                        int y = this.menu.top + this.menu.S(2.0) + i / cols * (ch + gap);
                        if (!((float) y - sc > (float) this.menu.bottom)
                                && !((float) (y + ch) - sc < (float) this.menu.top)) {
                            g.nextStratum();
                            boolean worn = Modules.CAPES.enabled() && Modules.CAPES.design.is(design);
                            arcanicMenu = this.menu;
                            float app = ArcanicMenu.ease(
                                    (float) (this.menu.now - this.menu.contentAt - (long) Math.min(i, 14) * 24L)
                                            / 320.0F);
                            float hv = this.garment(g, "cape:" + design, x, y, cw, ch, sc, worn, app);
                            float kf = Px.fade;
                            Px.fade = kf * app;
                            int artH = ch - this.menu.S(70.0);
                            int artW = Math.round((float) artH * 10.0F / 16.0F);
                            int lift = Math.round((1.0F - app) * (float) this.menu.S(10.0));
                            Capes.Strip strip = Capes.strip(design);
                            if (strip != null) {
                                g.blit(
                                        RenderPipelines.GUI_TEXTURED,
                                        strip.id(),
                                        x + (cw - artW) / 2,
                                        y + this.menu.S(16.0) + lift,
                                        0.0F,
                                        (float) (strip.frame() * 256),
                                        artW,
                                        artH,
                                        160,
                                        256,
                                        160,
                                        256 * strip.frames(),
                                        Px.a(0xFFFFFF, Math.round(Px.fade * 255.0F)));
                            }

                            this.garmentLabel(
                                    g, x, y + ch - this.menu.S(44.0) + lift, cw, design, worn, hv, false, false);
                            Px.fade = kf;
                            if (!this.menu.under() && app > 0.3F) {
                                this.menu.clipped(
                                        x,
                                        y,
                                        cw,
                                        ch,
                                        sc,
                                        this.menu.top,
                                        this.menu.bottom,
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
                            }

                            ++i;
                        } else {
                            ++i;
                        }
                    }
                } else {
                    for (MeleeDesign skin : designs) {
                        int x = this.menu.cx0 + i % cols * (cw + gap);
                        int y = this.menu.top + this.menu.S(2.0) + i / cols * (ch + gap);
                        if (!((float) y - sc > (float) this.menu.bottom)
                                && !((float) (y + ch) - sc < (float) this.menu.top)) {
                            g.nextStratum();
                            boolean locked = PremiumSkins.locked(skin);
                            boolean worn = !locked && owner.enabled() && owner.skin.is(skin.getDisplayName());
                            arcanicMenu = this.menu;
                            float app = ArcanicMenu.ease(
                                    (float) (this.menu.now - this.menu.contentAt - (long) Math.min(i, 14) * 24L)
                                            / 320.0F);
                            float hv = this.garment(g, "knife:" + skin.getKey(), x, y, cw, ch, sc, worn, app);
                            float kf = Px.fade;
                            Px.fade = kf * app;
                            int lift = Math.round((1.0F - app) * (float) this.menu.S(10.0));
                            // a locked Premium skin has no texture here: its model is shown in the free paint, dimmed
                            MeleeDesign shown = locked ? MeleeCatalog.byKey(skin.getGeometryKey()) : skin;
                            Px.fade = kf * app * (locked ? 0.4F : 1.0F);
                            if (shown != null)
                                MeleePreview.draw(
                                        g,
                                        shown,
                                        x + this.menu.S(10.0),
                                        y + this.menu.S(8.0) + lift,
                                        cw - this.menu.S(20.0),
                                        ch - this.menu.S(62.0),
                                        hv,
                                        Math.round((float) this.menu.top + sc),
                                        Math.round((float) this.menu.bottom + sc));
                            Px.fade = kf * app;
                            if (skin.isPremium())
                                this.premiumBadge(g, x + this.menu.S(10.0), y + this.menu.S(10.0) + lift, locked);
                            this.garmentLabel(
                                    g,
                                    x,
                                    y + ch - this.menu.S(44.0) + lift,
                                    cw,
                                    skin.getDisplayName(),
                                    worn,
                                    hv,
                                    !locked,
                                    locked);
                            Px.fade = kf;
                            if (!this.menu.under() && app > 0.3F) {
                                this.menu.clipped(
                                        x,
                                        y,
                                        cw,
                                        ch,
                                        sc,
                                        this.menu.top,
                                        this.menu.bottom,
                                        () -> {
                                            if (locked) {
                                                UiSound.play("pop", 0.7F, 1.0F);
                                                Toasts.setting("Этот скин доступен с Premium", false);
                                                return;
                                            }
                                            if (worn) {
                                                owner.setEnabled(false);
                                                UiSound.play("drain");
                                                Toasts.setting(guns ? "Оружие снято" : "Нож снят", false);
                                            } else {
                                                owner.skin.set(skin.getDisplayName());
                                                owner.setEnabled(true);
                                                MeleeAnimator.resetAll();
                                                MeleeModels.preload(skin);
                                                UiSound.play("charge");
                                                Toasts.setting(
                                                        (guns ? "Оружие: " : "Нож: ") + skin.getDisplayName(), true);
                                            }

                                            Config.dirty();
                                        },
                                        locked ? null : () -> this.menu.openKnifeStudio(skin));
                            }

                            ++i;
                        } else {
                            ++i;
                        }
                    }
                }

                g.pose().popMatrix();
                g.disableScissor();
                g.nextStratum();
                this.menu.edges(
                        g,
                        this.menu.cx0 - this.menu.S(12.0),
                        this.menu.cw0 + this.menu.S(24.0),
                        this.menu.top,
                        this.menu.bottom,
                        sc,
                        this.menu.baseColour());
                this.menu.scrollbar(
                        g,
                        this.menu.cx0 + this.menu.cw0 + this.menu.S(14.0),
                        this.menu.top,
                        this.menu.bottom,
                        total,
                        sc,
                        key);
        }
    }

    void wearWardrobe(GuiGraphics g, Wearables master, String offToast, String onPrefix) {
        List<Wearables.Item> items = master.items();
        int cols = 5;
        int gap = this.menu.S(10.0);
        int cw = (this.menu.cw0 - gap * (cols - 1)) / cols;
        int ch = cw + this.menu.S(30.0);
        int total = (items.size() + cols - 1) / cols * (ch + gap);
        ArcanicMenu arcanicMenu3 = this.menu;
        String key = "w:" + ArcanicMenu.wardrobe;
        float sc = (float) Math.round(this.menu.scrolled(key, total, this.menu.top, this.menu.bottom));
        g.enableScissor(
                this.menu.cx0 - this.menu.S(12.0),
                this.menu.top,
                this.menu.cx0 + this.menu.cw0 + this.menu.S(12.0),
                this.menu.bottom);
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -sc);

        for (int i = 0; i < items.size(); i++) {
            int x = this.menu.cx0 + i % cols * (cw + gap);
            int y = this.menu.top + this.menu.S(2.0) + i / cols * (ch + gap);
            if (!((float) y - sc > (float) this.menu.bottom) && !((float) (y + ch) - sc < (float) this.menu.top)) {
                g.nextStratum();
                Wearables.Item it = (Wearables.Item) items.get(i);
                String name = it.name();
                boolean locked = it.premium() && !ArcanicAccount.premium();
                boolean worn = !locked && master.enabled() && master.design.is(name);
                arcanicMenu3 = this.menu;
                float app = ArcanicMenu.ease(
                        (float) (this.menu.now - this.menu.contentAt - (long) Math.min(i, 14) * 24L) / 320.0F);
                float hv = this.garment(g, master.id + ":" + it.n(), x, y, cw, ch, sc, worn, app);
                float kf = Px.fade;
                Px.fade = kf * app;
                int lift = Math.round((1.0F - app) * (float) this.menu.S(10.0));
                int art = ch - this.menu.S(44.0);
                int size = Math.min(cw - this.menu.S(20.0), art);
                size = Math.round((float) size * (1.0F + 0.05F * hv));
                Identifier icon = Wearables.icon(it);
                if (locked) Px.fade = kf * app * 0.45F;
                boolean live = WearPreview.draw(
                        g,
                        it,
                        x + this.menu.S(6.0),
                        y + this.menu.S(6.0) + lift,
                        cw - this.menu.S(12.0),
                        art + this.menu.S(8.0),
                        hv,
                        Math.round((float) this.menu.top + sc),
                        Math.round((float) this.menu.bottom + sc));
                if (!live && TexWarm.ready(icon)) {
                    g.blit(
                            RenderPipelines.GUI_TEXTURED,
                            icon,
                            x + (cw - size) / 2,
                            y + this.menu.S(10.0) + lift + (art - size) / 2,
                            0.0F,
                            0.0F,
                            size,
                            size,
                            128,
                            128,
                            128,
                            128,
                            Px.a(0xFFFFFF, Math.round(Px.fade * 255.0F)));
                }

                Px.fade = kf * app;
                if (it.premium()) {
                    this.premiumBadge(g, x + this.menu.S(8.0), y + this.menu.S(8.0) + lift, locked);
                }
                this.petLabel(g, x, y + ch - this.menu.S(30.0) + lift, cw, name, worn, hv);
                Px.fade = kf;
                if (!this.menu.under() && app > 0.3F) {
                    this.menu.clipped(
                            x,
                            y,
                            cw,
                            ch,
                            sc,
                            this.menu.top,
                            this.menu.bottom,
                            () -> {
                                if (locked) {
                                    UiSound.play("pop", 0.7F, 1.0F);
                                    Toasts.setting("Этот рюкзак доступен с Premium", false);
                                    return;
                                }
                                if (worn) {
                                    master.setEnabled(false);
                                    UiSound.play("drain");
                                    Toasts.setting(offToast, false);
                                } else {
                                    master.design.set(name);
                                    master.setEnabled(true);
                                    if (this.menu.wearStudio.open == master) this.menu.wearStudio.item = it;
                                    UiSound.play("charge");
                                    Toasts.setting(Lang.tr(onPrefix) + Lang.tr(name), true);
                                }

                                Config.dirty();
                            },
                            () -> this.menu.openWearStudio(master, it));
                }
            }
        }

        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        this.menu.edges(
                g,
                this.menu.cx0 - this.menu.S(12.0),
                this.menu.cw0 + this.menu.S(24.0),
                this.menu.top,
                this.menu.bottom,
                sc,
                this.menu.baseColour());
        this.menu.scrollbar(
                g, this.menu.cx0 + this.menu.cw0 + this.menu.S(14.0), this.menu.top, this.menu.bottom, total, sc, key);
    }

    void petLabel(GuiGraphics g, int x, int y, int w, String name, boolean worn, float hv) {
        int fs = this.menu.S(12.0);
        String s = Px.ellipsize(Lang.tr(name), Px.Weight.MEDIUM, fs, w - this.menu.S(14.0));
        int n2 = x + w / 2;
        int n3 = y + this.menu.S(4.0);
        ArcanicMenu arcanicMenu4 = this.menu;
        Px.textCenter(
                g,
                s,
                n2,
                n3,
                Px.Weight.MEDIUM,
                fs,
                Px.mix(ArcanicMenu.T().text(), Theme.accent2() | 0xFF000000, worn ? 1.0F : hv * 0.4F));
        if (worn) {
            Px.circle(g, x + w / 2, y + this.menu.S(22.0), this.menu.S(2.0), Theme.accent());
        }
    }

    float garment(GuiGraphics g, String key, int x, int y, int w, int h, float sc, boolean worn, float app) {
        boolean hov = !this.menu.under()
                && this.menu.my >= (double) this.menu.top
                && this.menu.my < (double) this.menu.bottom
                && this.menu.hover(x, Math.round((float) y - sc), w, h);
        if (hov) {
            this.menu.hot(key);
        }

        float hv = this.menu.an("gh:" + key, hov ? 1.0F : 0.0F, 16.0F);
        float wa = this.menu.tween("gwo:" + key, worn ? 1.0F : 0.0F, 220.0F);
        float k = Px.fade;
        Px.fade = k * app;
        int yy = y + Math.round((1.0F - app) * (float) this.menu.S(6.0));
        int R = this.menu.S(12.0);
        Px.round(g, x, yy, w, h, R, Px.mix(ArcanicMenu.cardBg(), ArcanicMenu.cardHi(), hv));
        int n4 = ArcanicMenu.edgeMedium();
        ArcanicMenu arcanicMenu5 = this.menu;
        Px.outline(g, x, yy, w, h, R, Px.mix(n4, ArcanicMenu.acc(), wa));
        if (wa > 0.01F) {
            int r = this.menu.S(9.0);
            int ccx = x + w - this.menu.S(10.0) - r;
            int ccy = yy + this.menu.S(10.0) + r;
            float o = Px.fade;
            Px.fade = o * wa;
            ArcanicMenu arcanicMenu6 = this.menu;
            Px.circle(g, ccx, ccy, r, ArcanicMenu.acc());
            ArcanicMenu arcanicMenu7 = this.menu;
            ArcanicMenu.glyph(g, "check", ccx - this.menu.S(5.0), ccy - this.menu.S(5.0), this.menu.S(10.0), -1);
            Px.fade = o;
        }

        Px.fade = k;
        return hv;
    }

    /** A purple PREMIUM tag in the card's corner, with a padlock while the account does not have Premium. */
    void premiumBadge(GuiGraphics g, int x, int y, boolean locked) {
        int fs = this.menu.S(10.0);
        int h = this.menu.S(20.0);
        int pad = this.menu.S(8.0);
        int icon = locked ? this.menu.S(10.0) : 0;
        int gap = locked ? this.menu.S(5.0) : 0;
        int w = pad * 2 + icon + gap + Px.textWidth("PREMIUM", Px.Weight.SEMIBOLD, fs);
        Px.round(g, x, y, w, h, h / 2, Px.a(PREMIUM, 52));
        Px.outline(g, x, y, w, h, h / 2, Px.a(PREMIUM, 170));
        if (locked) padlock(g, x + pad, y + (h - icon) / 2, icon, PREMIUM | 0xFF000000);
        Px.text(g, "PREMIUM", x + pad + icon + gap, y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, 0xFFD9C2FF);
    }

    private void padlock(GuiGraphics g, int x, int y, int s, int colour) {
        ArcanicMenu.padlock(g, x, y, s, colour);
    }

    void garmentLabel(
            GuiGraphics g, int x, int y, int w, String name, boolean worn, float hv, boolean studio, boolean locked) {
        String text2 = Px.ellipsize(Lang.tr(name), Px.Weight.SEMIBOLD, this.menu.S(13.0), w - this.menu.S(28.0));
        int n5 = x + this.menu.S(14.0);
        Px.Weight weight = Px.Weight.SEMIBOLD;
        int n6 = this.menu.S(13.0);
        ArcanicMenu arcanicMenu8 = this.menu;
        Px.text(g, text2, n5, y, weight, n6, ArcanicMenu.T().text());
        text2 = Lang.tr(
                locked
                        ? "Только с Premium"
                        : studio && hv > 0.5F
                                ? (worn ? "ЛКМ: снять, ПКМ: студия" : "ЛКМ: надеть, ПКМ: студия")
                                : (worn ? "Надето" : (hv > 0.5F ? "Нажми, чтобы надеть" : "Не надето")));
        n5 = x + this.menu.S(14.0);
        int n7 = y + this.menu.S(19.0);
        weight = Px.Weight.MEDIUM;
        n6 = this.menu.S(11.0);
        int n8;
        if (locked) {
            n8 = 0xFFB794FF;
        } else if (worn) {
            ArcanicMenu arcanicMenu9 = this.menu;
            n8 = ArcanicMenu.accText();
        } else {
            ArcanicMenu arcanicMenu10 = this.menu;
            int n9 = ArcanicMenu.T().dim();
            ArcanicMenu arcanicMenu11 = this.menu;
            n8 = Px.mix(n9, ArcanicMenu.T().muted(), hv);
        }

        Px.text(g, text2, n5, n7, weight, n6, n8);
    }
}
