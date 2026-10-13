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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.arcanic.visuals.core.CloudPresets;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;

/** The presets page of the menu: saved sets of settings, one per row. */
final class PresetsPage {
    static final String[] TAB_ICONS = {"w.bookmark", "w.folder", "w.cloud"};

    private final ArcanicMenu menu;
    private final SimpleDateFormat date = new SimpleDateFormat("dd.MM.yyyy");
    /** 0 all, 1 made here, 2 the ones that came by a code. */
    int tab;

    PresetsPage(ArcanicMenu menu) {
        this.menu = menu;
    }

    /** The dialog of a new preset: its name, and whether it is meant for the server the player is on. */
    void create() {
        Minecraft.getInstance()
                .setScreen(PromptScreen.fieldAndSwitch(
                        menu,
                        "Создать пресет",
                        "Название",
                        "Общий пресет",
                        "Для текущего сервера",
                        "Создать пресет",
                        (name, forServer) -> {
                            String kept = Config.savePreset(name, forServer ? server() : "");
                            menu.presetCache = null;
                            if (kept == null) {
                                Toasts.setting(Lang.tr("Не удалось создать пресет"), false);
                                return;
                            }
                            Toasts.setting(Lang.tr("Пресет создан") + ": " + kept, true);
                            UiSound.play("select");
                        }));
    }

    /** The dialog for a code someone shared. */
    void paste() {
        Minecraft.getInstance()
                .setScreen(PromptScreen.field(
                        menu,
                        "Пресет по коду",
                        "Код пресета",
                        "Загрузить пресет",
                        code -> CloudPresets.fetch(code, kept -> {
                            menu.presetCache = null;
                            tab = 2;
                            Toasts.setting(Lang.tr("Пресет добавлен") + ": " + kept, true);
                            UiSound.play("select");
                        })));
    }

    private static String server() {
        var data = Minecraft.getInstance().getCurrentServer();
        return data == null ? Lang.tr("Одиночная игра") : data.ip;
    }

    private List<Config.Preset> shown() {
        List<Config.Preset> all = menu.presetList();
        return tab == 0
                ? all
                : all.stream().filter(p -> p.cloud() == (tab == 2)).toList();
    }

    void presets(GuiGraphics g) {
        int bar = menu.S(44), listTop = menu.top + bar;
        toolbar(g, menu.top, bar - menu.S(14));
        List<Config.Preset> list = shown();
        if (list.isEmpty()) {
            empty(g, listTop);
            return;
        }
        int gap = menu.S(8), rh = menu.S(60), total = list.size() * (rh + gap);
        float sc = Math.round(menu.scrolled("presets" + tab, total, listTop, menu.bottom));
        g.enableScissor(menu.cx0 - menu.S(12), listTop, menu.cx0 + menu.cw0 + menu.S(12), menu.bottom);
        g.pose().pushMatrix();
        g.pose().translate(0, -sc);
        for (int i = 0; i < list.size(); i++) {
            g.nextStratum();
            row(g, list.get(i), i, menu.cx0, listTop + menu.S(2) + i * (rh + gap), menu.cw0, rh, sc, listTop);
        }
        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        menu.edges(g, menu.cx0 - menu.S(12), menu.cw0 + menu.S(24), listTop, menu.bottom, sc, menu.baseColour());
        menu.scrollbar(g, menu.cx0 + menu.cw0 + menu.S(14), listTop, menu.bottom, total, sc, "presets" + tab);
    }

    /** Above the list: what is active on the left; the folder, a refresh and "save the active one" on the right. */
    private void toolbar(GuiGraphics g, int y, int h) {
        String active = Config.activePreset();
        String line = active.isEmpty() ? Lang.tr("Активного пресета нет") : Lang.tr("Активный пресет") + ": " + active;
        Px.text(
                g,
                Px.ellipsize(line, Weight.MEDIUM, menu.S(13), menu.cw0 - menu.S(140)),
                menu.cx0,
                y + (h - menu.S(13)) / 2,
                Weight.MEDIUM,
                menu.S(13),
                menu.T().muted());
        int x = menu.cx0 + menu.cw0 - h;
        tool(g, "save", "w.save", x, y, h, () -> {
            if (active.isEmpty()) {
                Toasts.setting(Lang.tr("Сначала создай или загрузи пресет"), false);
                return;
            }
            save(active);
        });
        tool(g, "refresh", "w.refresh", x -= h + menu.S(8), y, h, () -> {
            menu.presetCache = null;
            Toasts.setting(Lang.tr("Список обновлён"), true);
        });
        tool(g, "folder", "w.folder", x - h - menu.S(8), y, h, () -> {
            try {
                Util.getPlatform().openPath(Config.presetsFolder());
            } catch (IOException e) {
                Toasts.setting(Lang.tr("Не удалось открыть папку пресетов"), false);
            }
        });
    }

    private void tool(GuiGraphics g, String id, String icon, int x, int y, int s, Runnable click) {
        boolean hv = !menu.under() && menu.hover(x, y, s, s);
        if (hv) menu.hot("presetTool:" + id);
        float hk = menu.an("prt:" + id, hv ? 1 : 0, 18);
        Px.round(g, x, y, s, s, menu.S(10), Px.a(menu.T().text(), Math.round(10 + 12 * hk)));
        Px.outline(g, x, y, s, s, menu.S(10), Px.a(menu.T().text(), menu.light() ? 24 : 16));
        int is = menu.S(15);
        ArcanicMenu.glyph(
                g, icon, x + (s - is) / 2, y + (s - is) / 2, is, Px.mix(menu.T().muted(), menu.accText(), hk));
        if (!menu.under())
            menu.clipped(
                    x,
                    y,
                    s,
                    s,
                    0,
                    menu.top,
                    menu.bottom,
                    () -> {
                        UiSound.play("click");
                        click.run();
                    },
                    null);
    }

    private void empty(GuiGraphics g, int listTop) {
        float t = ArcanicMenu.ease((menu.now - menu.contentAt) / 300f);
        float k = Px.fade;
        Px.fade = k * t;
        int mid = menu.cx0 + menu.cw0 / 2, cy = listTop + (menu.bottom - listTop) / 2 - menu.S(54);
        ArcanicMenu.glyph(g, tab == 2 ? "w.cloud" : "presets-s", mid - menu.S(16), cy, menu.S(32), menu.accText());
        Px.textCenter(
                g,
                Lang.tr(tab == 2 ? "Облачных пресетов пока нет" : "Пока ни одного пресета"),
                mid,
                cy + menu.S(50),
                Weight.SEMIBOLD,
                menu.S(15),
                menu.T().text());
        Px.textCenter(
                g,
                Lang.tr(
                        tab == 2
                                ? "Вставь код, которым с тобой поделились, и пресет появится здесь"
                                : "Сохрани текущие настройки, чтобы вернуться к ним в один клик"),
                mid,
                cy + menu.S(74),
                Weight.MEDIUM,
                menu.S(12),
                menu.T().dim());
        Px.fade = k;
    }

    private void row(GuiGraphics g, Config.Preset p, int i, int x, int y, int w, int h, float sc, int listTop) {
        String name = p.name();
        boolean active = name.equals(Config.activePreset());
        float app = ArcanicMenu.ease((menu.now - menu.contentAt - Math.min(i, 16) * 20L) / 300f);
        boolean hv =
                !menu.under() && menu.my >= listTop && menu.my < menu.bottom && menu.hover(x, Math.round(y - sc), w, h);
        if (hv) menu.hot("preset:" + name);
        float hk = menu.an("pr:" + name, hv ? 1 : 0, 18), ak = menu.an("prOn:" + name, active ? 1 : 0, 14);
        float k = Px.fade;
        Px.fade = k * app;
        int yy = y + Math.round((1 - app) * menu.S(8)), r = menu.S(20);
        Px.round(g, x, yy, w, h, r, Px.a(menu.T().text(), Math.round((menu.light() ? 10 : 9) * hk)));
        Px.outline(g, x, yy, w, h, r, Px.mix(Px.a(menu.T().text(), menu.light() ? 22 : 15), menu.acc(), ak));

        // the round mark on the left: a cloud for a preset that came by a code
        int av = menu.S(36), ax = x + menu.S(12), ay = yy + (h - av) / 2, is = menu.S(16);
        Px.round(g, ax, ay, av, av, av / 2, Px.a(menu.T().text(), menu.light() ? 12 : 10));
        Px.outline(g, ax, ay, av, av, av / 2, Px.a(menu.T().text(), menu.light() ? 24 : 16));
        ArcanicMenu.glyph(
                g,
                p.cloud() ? "w.cloud" : "presets-s",
                ax + (av - is) / 2,
                ay + (av - is) / 2,
                is,
                Px.mix(menu.T().muted(), menu.accText(), Math.max(hk, ak)));

        int bs = menu.S(30), step = bs + menu.S(10), by = yy + (h - bs) / 2;
        int trashX = x + w - menu.S(14) - bs, shareX = trashX - step, saveX = shareX - step, stateX = saveX - step;
        int tx = x + menu.S(60), tw = stateX - menu.S(12) - tx;
        Px.text(
                g,
                Px.ellipsize(name, Weight.SEMIBOLD, menu.S(15), tw),
                tx,
                yy + menu.S(13),
                Weight.SEMIBOLD,
                menu.S(15),
                menu.T().text());
        Px.text(
                g,
                Px.ellipsize(about(p), Weight.MEDIUM, menu.S(12), tw),
                tx,
                yy + menu.S(35),
                Weight.MEDIUM,
                menu.S(12),
                menu.T().dim());

        // on the right: the state (active, or "load"), save over it, share, delete
        boolean stateHover = button(g, "state", name, stateX, by, bs, sc, hv, ak, false);
        if (active)
            Px.textCenter(
                    g, "ON", stateX + bs / 2, by + (bs - menu.S(11)) / 2, Weight.BOLD, menu.S(11), menu.accText());
        else icon(g, "w.chev-right", stateX, by, bs, stateHover);
        icon(g, "w.save", saveX, by, bs, button(g, "save", name, saveX, by, bs, sc, hv, 0, false));
        icon(g, "w.send", shareX, by, bs, button(g, "share", name, shareX, by, bs, sc, hv, 0, false));
        boolean trashHover = button(g, "trash", name, trashX, by, bs, sc, hv, 0, true);
        ArcanicMenu.glyph(
                g,
                "trash",
                trashX + (bs - menu.S(15)) / 2,
                by + (bs - menu.S(15)) / 2,
                menu.S(15),
                Px.mix(menu.T().dim(), 0xFFFF6A7A, menu.an("prb:trash" + name, trashHover ? 1 : 0, 18)));
        Px.fade = k;
        if (menu.under()) return;

        // the row first, the buttons after it: a click goes to the region added last
        menu.clipped(x, y, w, h, sc, listTop, menu.bottom, () -> load(name), () -> delete(name));
        menu.clipped(saveX, by, bs, bs, sc, listTop, menu.bottom, () -> save(name), null);
        menu.clipped(
                shareX,
                by,
                bs,
                bs,
                sc,
                listTop,
                menu.bottom,
                () -> {
                    UiSound.play("click");
                    Toasts.setting(Lang.tr("Отправляю пресет") + ": " + name, true);
                    CloudPresets.share(name);
                },
                null);
        menu.clipped(trashX, by, bs, bs, sc, listTop, menu.bottom, () -> delete(name), null);
    }

    /** The plate of a round button of a row; true while the mouse is over it. */
    private boolean button(
            GuiGraphics g,
            String id,
            String name,
            int x,
            int y,
            int s,
            float sc,
            boolean rowHover,
            float on,
            boolean red) {
        boolean hv = rowHover && menu.hover(x, Math.round(y - sc), s, s);
        float hk = menu.an("prb:" + id + name, hv ? 1 : 0, 18);
        int idle = Px.a(menu.T().text(), menu.light() ? 9 : 8);
        int lit = red ? Px.a(0xFF5A6A, 50) : Px.a(menu.T().text(), menu.light() ? 22 : 20);
        Px.round(g, x, y, s, s, s / 2, Px.mix(Px.mix(idle, lit, hk), Px.a(menu.acc(), 60), on));
        return hv;
    }

    private void icon(GuiGraphics g, String icon, int x, int y, int s, boolean hover) {
        int is = menu.S(14);
        ArcanicMenu.glyph(
                g,
                icon,
                x + (s - is) / 2,
                y + (s - is) / 2,
                is,
                hover ? menu.accText() : menu.T().muted());
    }

    private String about(Config.Preset p) {
        StringBuilder line = new StringBuilder(Lang.tr(p.cloud() ? "Облачный" : "Локальный"));
        if (!p.server().isEmpty()) line.append("  •  ").append(p.server());
        if (!p.code().isEmpty()) line.append("  •  ").append(p.code());
        return line.append("  •  ").append(date.format(new Date(p.saved()))).toString();
    }

    private void load(String name) {
        if (!Config.loadPreset(name)) {
            Toasts.setting(Lang.tr("Не удалось загрузить пресет"), false);
            return;
        }
        UiSound.play("select");
        Toasts.setting(Lang.tr("Пресет применён") + ": " + name, true);
    }

    private void save(String name) {
        boolean saved = Config.overwritePreset(name);
        menu.presetCache = null;
        UiSound.play(saved ? "select" : "reset");
        Toasts.setting(Lang.tr(saved ? "Пресет сохранён" : "Не удалось сохранить пресет") + ": " + name, saved);
    }

    private void delete(String name) {
        if (name.equals(Config.activePreset())) {
            Toasts.setting(Lang.tr("Нельзя удалить активный пресет"), false);
            return;
        }
        boolean gone = Config.deletePreset(name);
        menu.presetCache = null;
        UiSound.play("reset");
        Toasts.setting(Lang.tr(gone ? "Пресет удалён" : "Не удалось удалить пресет") + ": " + name, false);
    }
}
