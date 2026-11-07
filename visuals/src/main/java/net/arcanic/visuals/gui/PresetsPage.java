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
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Util;

/**
 * The configs page, all on one flat screen with no cards, tabs or dialogs: what is on now with its save and share
 * buttons, one line that both names a new config and takes a friend's code, and the list of configs as plain rows.
 * A click on a row loads it; on hover a row shows its few actions in words; deleting asks again in place.
 */
final class PresetsPage {
    static final String[] TAB_ICONS = {"w.stack", "w.folder", "w.cloud"};
    private static final int AMBER = 0xFFF5B946;
    private static final int GREEN = 0xFF4ADE80;
    private static final int RED = -41620;
    private static final long CONFIRM_MS = 2600L;
    private final ArcanicMenu menu;
    private final SimpleDateFormat date = new SimpleDateFormat("dd.MM.yyyy");
    /** Kept for the menu's header and scroll keys; the page has no tabs any more. */
    int tab;

    boolean typing;
    boolean forServer;
    String draft = "";
    private String confirm = "";
    private String flashed = "";
    private long confirmAt;
    private long flashAt;
    /** When a code was sent off; the button waits a few seconds for the answer so it is not pressed twice. */
    private long fetchAt;

    PresetsPage(ArcanicMenu menu) {
        this.menu = menu;
    }

    private int S(double v) {
        return this.menu.S(v);
    }

    void focus() {
        this.typing = true;
        UiSound.play("click", 1.15F, 0.7F);
    }

    boolean key(int k, int mods) {
        if (!this.typing) return false;
        boolean ctrl = (mods & 2) != 0;
        switch (k) {
            case 86 -> {
                if (ctrl) this.type(Minecraft.getInstance().keyboardHandler.getClipboard());
            }
            case 256 -> this.typing = false;
            case 257, 335 -> {
                if (ctrl) this.fetch();
                else this.create();
            }
            case 259 -> {
                if (!this.draft.isEmpty()) {
                    this.draft = ctrl ? "" : this.draft.substring(0, this.draft.length() - 1);
                    UiSound.play("type", 0.9F, 0.3F);
                }
            }
            default -> {}
        }
        return true;
    }

    void type(String s) {
        boolean any = false;
        int i = 0;
        while (i < s.length() && this.draft.length() < 32) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 32 && "\\/:*?\"<>|".indexOf(cp) < 0) {
                this.draft = this.draft + new String(Character.toChars(cp));
                any = true;
            }
        }
        if (any) UiSound.play("type", 1.15F, 0.35F);
    }

    /** Puts the clipboard in the line, ready to load as a code; no window opens. */
    void paste() {
        this.typing = true;
        this.draft = "";
        this.type(Minecraft.getInstance().keyboardHandler.getClipboard().strip());
    }

    private void create() {
        String name = this.draft.strip();
        if (name.isEmpty()) {
            UiSound.play("reset", 1.0F, 0.6F);
            return;
        }
        String kept = Config.savePreset(name, this.forServer ? server() : "");
        this.menu.presetCache = null;
        if (kept == null) {
            UiSound.play("reset");
            Toasts.setting(Lang.tr("Не удалось создать конфиг"), false);
        } else {
            this.draft = "";
            this.typing = false;
            this.flash(kept);
            UiSound.play("select");
            Toasts.setting(Lang.tr("Конфиг создан") + ": " + kept, true);
        }
    }

    /** Loads a friend's config by the code in the line. */
    private void fetch() {
        String code = this.draft.strip();
        if (code.isEmpty() || this.fetching()) {
            UiSound.play("reset", 1.0F, 0.6F);
            return;
        }
        this.fetchAt = System.currentTimeMillis();
        UiSound.play("click");
        CloudPresets.fetch(code, kept -> {
            this.fetchAt = 0L;
            this.menu.presetCache = null;
            this.draft = "";
            this.typing = false;
            this.flash(kept);
            Toasts.setting(Lang.tr("Конфиг добавлен") + ": " + kept, true);
            UiSound.play("select");
        });
    }

    private boolean fetching() {
        return System.currentTimeMillis() - this.fetchAt < 4000L;
    }

    private void load(String name) {
        if (name.equals(Config.activePreset())) {
            this.flash(name);
            UiSound.play("tab");
        } else if (!Config.loadPreset(name)) {
            UiSound.play("reset");
            Toasts.setting(Lang.tr("Не удалось загрузить конфиг"), false);
        } else {
            this.flash(name);
            UiSound.play("select");
            Toasts.setting(Lang.tr("Конфиг применён") + ": " + name, true);
        }
    }

    private void save(String name) {
        boolean saved = Config.overwritePreset(name);
        this.menu.presetCache = null;
        if (saved) this.flash(name);
        UiSound.play(saved ? "select" : "reset");
        Toasts.setting(Lang.tr(saved ? "Конфиг сохранён" : "Не удалось сохранить конфиг") + ": " + name, saved);
    }

    private void share(String name) {
        UiSound.play("click");
        Toasts.setting(Lang.tr("Отправляю конфиг") + ": " + name, true);
        CloudPresets.share(name);
    }

    private void delete(String name) {
        long now = System.currentTimeMillis();
        if (this.confirm.equals(name) && now - this.confirmAt <= CONFIRM_MS) {
            this.confirm = "";
            boolean gone = Config.deletePreset(name);
            this.menu.presetCache = null;
            UiSound.play(gone ? "drain" : "reset");
            Toasts.setting(Lang.tr(gone ? "Конфиг удалён" : "Не удалось удалить конфиг") + ": " + name, false);
        } else if (name.equals(Config.activePreset())) {
            UiSound.play("reset");
            Toasts.setting(Lang.tr("Нельзя удалить активный конфиг"), false);
        } else {
            this.confirm = name;
            this.confirmAt = now;
            UiSound.play("pop", 0.9F, 0.8F);
        }
    }

    private void flash(String name) {
        this.flashed = name;
        this.flashAt = System.currentTimeMillis();
    }

    private static String server() {
        ServerData data = Minecraft.getInstance().getCurrentServer();
        return data == null ? Lang.tr("Одиночная игра") : data.ip;
    }

    private Config.Preset find(String name) {
        for (Config.Preset p : this.menu.presetList()) if (p.name().equals(name)) return p;
        return null;
    }

    void presets(GuiGraphics g) {
        int x = this.menu.cx0;
        int w = this.menu.cw0;
        int y = this.menu.top;
        y = this.now(g, x, y, w);
        this.rule(g, x, y, w);
        y += this.S(14.0);
        y = this.input(g, x, y, w);
        this.rule(g, x, y, w);
        y += this.S(16.0);
        List<Config.Preset> list = this.menu.presetList();
        y = this.heading(g, x, y, w, list.size());
        int listTop = y;
        if (list.isEmpty()) {
            this.empty(g, listTop);
            return;
        }
        int rh = this.S(48.0);
        int total = list.size() * rh;
        float sc = (float) Math.round(this.menu.scrolled("presets0", total, listTop, this.menu.bottom));
        g.enableScissor(x - this.S(12.0), listTop, x + w + this.S(12.0), this.menu.bottom);
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -sc);
        for (int i = 0; i < list.size(); i++) {
            g.nextStratum();
            this.row(g, list.get(i), i, x, listTop + i * rh, w, rh, sc, listTop, i == list.size() - 1);
        }
        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        this.menu.edges(g, x - this.S(12.0), w + this.S(24.0), listTop, this.menu.bottom, sc, this.menu.baseColour());
        this.menu.scrollbar(g, x + w + this.S(14.0), listTop, this.menu.bottom, total, sc, "presets0");
    }

    private void rule(GuiGraphics g, int x, int y, int w) {
        Px.rect(g, x, y, w, Math.max(1, this.S(1.0)), ArcanicMenu.edgeSmall());
    }

    /** What is on now: its name, whether it is saved, and the two things one does with it. */
    private int now(GuiGraphics g, int x, int y, int w) {
        String active = Config.activePreset();
        boolean has = !active.isEmpty();
        boolean changed = has && Config.activeChanged();
        float ck = this.menu.tween("cfgChanged", changed ? 1.0F : 0.0F, 260.0F);
        int h = this.S(66.0);
        int right = x + w;
        int bh = this.S(30.0);
        int by = y + this.S(8.0);
        int shareW = this.button(
                g, "cfgShare", "w.send", Lang.tr("Поделиться"), right, by, bh, false, has, () -> this.share(active));
        int saveW = this.button(
                g,
                "cfgSave",
                "w.save",
                Lang.tr("Сохранить"),
                right - shareW - this.S(6.0),
                by,
                bh,
                changed,
                has,
                () -> this.save(active));
        // autosave: a word and a box under the buttons
        boolean autosave = Modules.PREFS.presetAutosave.on();
        String at = Lang.tr("Автосохранение");
        int cs = this.S(16.0);
        int fs = this.S(11.5);
        int aw = Px.textWidth(at, Px.Weight.MEDIUM, fs) + this.S(8.0) + cs;
        int ay = by + bh + this.S(6.0);
        int ah = this.S(20.0);
        boolean ahv = !this.menu.under() && this.menu.hover(right - aw, ay, aw, ah);
        if (ahv) this.menu.hot("cfgAuto");
        float ak = this.menu.an("cfgAutoH", ahv ? 1.0F : 0.0F, 18.0F);
        float on = this.menu.tween("cfgAutoOn", autosave ? 1.0F : 0.0F, 200.0F);
        Px.text(
                g,
                at,
                right - aw,
                ay + (ah - fs) / 2,
                Px.Weight.MEDIUM,
                fs,
                Px.mix(ArcanicMenu.faint(), ArcanicMenu.T().text(), ak));
        this.menu.checkbox(g, right - cs, ay + (ah - cs) / 2, cs, on, ak);
        if (!this.menu.under()) {
            this.menu.clipped(
                    right - aw,
                    ay,
                    aw,
                    ah,
                    0.0F,
                    this.menu.top,
                    this.menu.bottom,
                    () -> {
                        Modules.PREFS.presetAutosave.toggle();
                        Config.dirty();
                        UiSound.play(Modules.PREFS.presetAutosave.on() ? "toggle_on" : "toggle_off");
                    },
                    null);
        }
        int room = right - Math.max(shareW + saveW + this.S(6.0), aw) - this.S(24.0) - x;
        Px.text(g, Lang.tr("Сейчас"), x, y + this.S(8.0), Px.Weight.MEDIUM, this.S(11.0), ArcanicMenu.faint());
        if (has) {
            int ns = this.S(18.0);
            Px.text(
                    g,
                    Px.ellipsize(active, Px.Weight.BOLD, ns, room),
                    x,
                    y + this.S(24.0),
                    Px.Weight.BOLD,
                    ns,
                    ArcanicMenu.T().text());
            int dot = Math.max(3, this.S(6.0));
            int dy = y + this.S(49.0);
            int col = Px.mix(GREEN, AMBER, ck);
            Px.round(g, x, dy + (this.S(11.0) - dot) / 2, dot, dot, dot / 2, col);
            String state = Lang.tr(changed ? "есть несохранённые изменения" : "все изменения сохранены");
            Config.Preset info = this.find(active);
            if (info != null && info.cloud()) state += "  ·  " + Lang.tr("из облака");
            Px.text(
                    g,
                    Px.ellipsize(state, Px.Weight.MEDIUM, this.S(11.0), room - dot - this.S(7.0)),
                    x + dot + this.S(7.0),
                    dy,
                    Px.Weight.MEDIUM,
                    this.S(11.0),
                    Px.mix(ArcanicMenu.sub(), AMBER, ck * 0.85F));
        } else {
            Px.text(
                    g,
                    Px.ellipsize(Lang.tr("Конфиг не выбран"), Px.Weight.BOLD, this.S(18.0), room),
                    x,
                    y + this.S(24.0),
                    Px.Weight.BOLD,
                    this.S(18.0),
                    ArcanicMenu.sub());
            Px.text(
                    g,
                    Px.ellipsize(
                            Lang.tr("Назови текущие настройки ниже, и они сохранятся"),
                            Px.Weight.MEDIUM,
                            this.S(11.0),
                            room),
                    x,
                    y + this.S(49.0),
                    Px.Weight.MEDIUM,
                    this.S(11.0),
                    ArcanicMenu.faint());
        }
        return y + h;
    }

    /**
     * A flat button: an icon and a word, no plate until hovered; the primary one stands in the accent. Returns its
     * width; {@code right} is its right edge.
     */
    private int button(
            GuiGraphics g,
            String id,
            String icon,
            String text,
            int right,
            int y,
            int h,
            boolean primary,
            boolean live,
            Runnable click) {
        int fs = this.S(12.0);
        int is = this.S(14.0);
        int w = this.S(11.0) + is + this.S(6.0) + Px.textWidth(text, Px.Weight.SEMIBOLD, fs) + this.S(12.0);
        int x = right - w;
        boolean hv = live && !this.menu.under() && this.menu.hover(x, y, w, h);
        if (hv) this.menu.hot(id);
        float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
        float pk = this.menu.tween(id + "P", primary && live ? 1.0F : 0.0F, 200.0F);
        float old = Px.fade;
        if (!live) Px.fade = old * 0.4F;
        int plate = Px.mix(Px.a(ArcanicMenu.field(), Math.round(255.0F * hk)), ArcanicMenu.acc(), pk);
        if (plate >>> 24 != 0) Px.round(g, x, y, w, h, this.S(8.0), Px.mix(plate, -1, 0.08F * hk * pk));
        int tc = Px.mix(Px.mix(ArcanicMenu.sub(), ArcanicMenu.T().text(), hk), -1, pk);
        ArcanicMenu.glyph(g, icon, x + this.S(11.0), y + (h - is) / 2, is, tc);
        Px.text(g, text, x + this.S(11.0) + is + this.S(6.0), y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, tc);
        Px.fade = old;
        if (live && !this.menu.under()) {
            this.menu.clipped(x, y, w, h, 0.0F, this.menu.top, this.menu.bottom, click, null);
        }
        return w;
    }

    /** One line for a name or a code, with "save as new" and "load by code" at its end. */
    private int input(GuiGraphics g, int x, int y, int w) {
        int h = this.S(38.0);
        boolean ready = !this.draft.isBlank();
        int bh = this.S(30.0);
        int by = y + (h - bh) / 2;
        int right = x + w;
        int codeW = this.button(
                g,
                "cfgCode",
                "w.cloud",
                Lang.tr("Загрузить по коду"),
                right,
                by,
                bh,
                false,
                ready && !this.fetching(),
                this::fetch);
        right -= codeW + this.S(6.0);
        int newW = this.button(
                g, "cfgNew", "plus", Lang.tr("Сохранить как новый"), right, by, bh, ready, ready, this::create);
        right -= newW + this.S(10.0);
        if (Minecraft.getInstance().getCurrentServer() != null) {
            String st = Lang.tr("для сервера");
            int cs = this.S(16.0);
            int fs = this.S(11.5);
            int sw = Px.textWidth(st, Px.Weight.MEDIUM, fs) + this.S(8.0) + cs;
            int sx = right - sw;
            boolean shv = !this.menu.under() && this.menu.hover(sx, by, sw, bh);
            if (shv) this.menu.hot("cfgServer");
            float sk = this.menu.an("cfgServerH", shv ? 1.0F : 0.0F, 18.0F);
            float son = this.menu.tween("cfgServerOn", this.forServer ? 1.0F : 0.0F, 200.0F);
            Px.text(
                    g,
                    st,
                    sx,
                    by + (bh - fs) / 2,
                    Px.Weight.MEDIUM,
                    fs,
                    Px.mix(ArcanicMenu.faint(), ArcanicMenu.T().text(), Math.max(sk, son)));
            this.menu.checkbox(g, right - cs, by + (bh - cs) / 2, cs, son, sk);
            if (!this.menu.under()) {
                this.menu.clipped(
                        sx,
                        by,
                        sw,
                        bh,
                        0.0F,
                        this.menu.top,
                        this.menu.bottom,
                        () -> {
                            this.forServer = !this.forServer;
                            UiSound.play(this.forServer ? "toggle_on" : "toggle_off");
                        },
                        null);
            }
            right = sx - this.S(12.0);
        }
        // the field itself: plain text on the page, an underline that lights up while typing
        int fx = x;
        int fw = right - fx;
        boolean over = !this.menu.under() && this.menu.hover(fx, y, fw, h);
        if (over) this.menu.hot("cfgField");
        float hv = this.menu.an("cfgFieldH", over ? 1.0F : 0.0F, 18.0F);
        float f = this.menu.tween("cfgFieldF", this.typing ? 1.0F : 0.0F, 180.0F);
        if (!this.menu.under()) {
            this.menu.clipped(fx, y, fw, h, 0.0F, this.menu.top, this.menu.bottom, this::focus, null);
        }
        int gs = this.S(14.0);
        ArcanicMenu.glyph(g, "type", fx, y + (h - gs) / 2, gs, Px.mix(ArcanicMenu.faint(), ArcanicMenu.acc(), f));
        int tx = fx + gs + this.S(10.0);
        int tw = right - this.S(4.0) - tx;
        int ts = this.S(13.0);
        int line = y + h - this.S(7.0);
        Px.rect(
                g,
                tx,
                line,
                Math.max(0, tw),
                Math.max(1, this.S(1.0)),
                Px.mix(Px.mix(ArcanicMenu.edgeSmall(), ArcanicMenu.edgeMedium(), hv), ArcanicMenu.acc(), f));
        boolean empty = this.draft.isEmpty();
        String shown = empty
                ? Lang.tr(
                        this.typing
                                ? "Название нового конфига или код друга"
                                : "Нажми, чтобы назвать новый конфиг или вставить код")
                : this.draft;
        if (!empty) {
            while (shown.length() > 1 && Px.textWidth(shown, Px.Weight.MEDIUM, ts) > tw) shown = shown.substring(1);
        } else {
            shown = Px.ellipsize(shown, Px.Weight.MEDIUM, ts, tw);
        }
        int ty = line - this.S(6.0) - ts;
        Px.text(
                g,
                shown,
                tx,
                ty,
                Px.Weight.MEDIUM,
                ts,
                empty ? ArcanicMenu.faint() : ArcanicMenu.T().text());
        if (this.typing && this.menu.now / 530L % 2L == 0L) {
            int cx = tx + (empty ? 0 : Px.textWidth(shown, Px.Weight.MEDIUM, ts)) + this.S(1.0);
            Px.rect(
                    g,
                    cx,
                    ty - this.S(1.0),
                    Math.max(1, this.S(1.0)),
                    ts + this.S(3.0),
                    ArcanicMenu.T().text());
        }
        if (this.typing && ready) {
            String hint = Lang.tr("Enter — сохранить, Ctrl+Enter — загрузить код");
            Px.text(
                    g,
                    Px.ellipsize(hint, Px.Weight.MEDIUM, this.S(10.0), tw),
                    tx,
                    line + this.S(3.0),
                    Px.Weight.MEDIUM,
                    this.S(10.0),
                    ArcanicMenu.faint());
        }
        return y + h + this.S(14.0);
    }

    private int heading(GuiGraphics g, int x, int y, int w, int count) {
        int h = this.S(26.0);
        String t = Lang.tr("Мои конфиги");
        int fs = this.S(13.0);
        Px.text(
                g,
                t,
                x,
                y + (h - fs) / 2,
                Px.Weight.SEMIBOLD,
                fs,
                ArcanicMenu.T().text());
        int tw = Px.textWidth(t, Px.Weight.SEMIBOLD, fs);
        Px.text(
                g,
                String.valueOf(count),
                x + tw + this.S(8.0),
                y + (h - this.S(12.0)) / 2,
                Px.Weight.MEDIUM,
                this.S(12.0),
                ArcanicMenu.faint());
        int right = x + w;
        int rw = this.button(
                g,
                "cfgRefresh",
                "w.refresh",
                Lang.tr("Обновить"),
                right,
                y - this.S(2.0),
                h + this.S(4.0),
                false,
                true,
                () -> {
                    this.menu.presetCache = null;
                    UiSound.play("tab");
                });
        this.button(
                g,
                "cfgFolder",
                "w.folder",
                Lang.tr("Папка"),
                right - rw - this.S(4.0),
                y - this.S(2.0),
                h + this.S(4.0),
                false,
                true,
                () -> {
                    try {
                        Util.getPlatform().openPath(Config.presetsFolder());
                    } catch (IOException e) {
                        Toasts.setting(Lang.tr("Не удалось открыть папку конфигов"), false);
                    }
                });
        return y + h + this.S(8.0);
    }

    private void empty(GuiGraphics g, int listTop) {
        float t = ArcanicMenu.ease((float) (this.menu.now - this.menu.contentAt) / 300.0F);
        float k = Px.fade;
        Px.fade = k * t;
        int mid = this.menu.cx0 + this.menu.cw0 / 2;
        int cy = listTop + (this.menu.bottom - listTop) / 2 - this.S(30.0);
        Px.textCenter(
                g,
                Lang.tr("Пока ни одного конфига"),
                mid,
                cy,
                Px.Weight.SEMIBOLD,
                this.S(15.0),
                ArcanicMenu.T().text());
        Px.textCenter(
                g,
                Lang.tr("Назови текущие настройки в строке выше и нажми Enter"),
                mid,
                cy + this.S(24.0),
                Px.Weight.MEDIUM,
                this.S(12.0),
                ArcanicMenu.faint());
        Px.fade = k;
    }

    /** One config as a plain row: the name and a line about it; on hover, its actions in words at the right. */
    private void row(
            GuiGraphics g, Config.Preset p, int i, int x, int y, int w, int h, float sc, int listTop, boolean last) {
        String name = p.name();
        boolean active = name.equals(Config.activePreset());
        float app =
                ArcanicMenu.ease((float) (this.menu.now - this.menu.contentAt - (long) Math.min(i, 16) * 18L) / 280.0F);
        boolean hv = !this.menu.under()
                && this.menu.my >= (double) listTop
                && this.menu.my < (double) this.menu.bottom
                && this.menu.hover(x, Math.round((float) y - sc), w, h);
        if (hv) this.menu.hot("preset:" + name);
        long now = this.menu.now;
        boolean arming = this.confirm.equals(name) && now - this.confirmAt < CONFIRM_MS;
        float hk = this.menu.an("pr:" + name, hv || arming ? 1.0F : 0.0F, 16.0F);
        float ak = this.menu.tween("prOn:" + name, active ? 1.0F : 0.0F, 220.0F);
        float fl = name.equals(this.flashed) ? Math.max(0.0F, 1.0F - (float) (now - this.flashAt) / 700.0F) : 0.0F;
        float k = Px.fade;
        Px.fade = k * app;
        int yy = y + Math.round((1.0F - app) * (float) this.S(6.0));
        // the hover band, a flash when it was just loaded or saved
        int band = Px.mix(Px.a(ArcanicMenu.T().text(), Math.round(10.0F * hk)), Px.a(ArcanicMenu.acc(), 40), fl);
        if (band >>> 24 != 0) {
            Px.round(g, x - this.S(10.0), yy + this.S(2.0), w + this.S(20.0), h - this.S(4.0), this.S(10.0), band);
        }
        if (!last) Px.rect(g, x, yy + h - 1, w, 1, Px.a(ArcanicMenu.edgeSmall(), 200));
        // the accent mark of the one that is on
        int mark = Math.max(2, this.S(3.0));
        if (ak > 0.01F) {
            float kk = Px.fade;
            Px.fade = kk * ak;
            Px.round(g, x - this.S(10.0), yy + this.S(14.0), mark, h - this.S(28.0), mark / 2, ArcanicMenu.acc());
            Px.fade = kk;
        }
        // its actions, in words
        int right = x + w;
        int bh = this.S(28.0);
        int by = yy + (h - bh) / 2;
        int actionsLeft = right;
        if (hk > 0.01F) {
            float kk = Px.fade;
            Px.fade = kk * hk;
            int dw = this.action(
                    g,
                    "prDel:" + name,
                    arming ? Lang.tr("Точно удалить?") : Lang.tr("Удалить"),
                    right,
                    by,
                    bh,
                    arming ? RED : -38278,
                    arming,
                    hv || arming,
                    () -> this.delete(name),
                    sc,
                    listTop);
            int cw = this.action(
                    g,
                    "prCode:" + name,
                    Lang.tr("Код"),
                    right - dw - this.S(4.0),
                    by,
                    bh,
                    ArcanicMenu.sub(),
                    false,
                    hv,
                    () -> this.share(name),
                    sc,
                    listTop);
            actionsLeft = right - dw - cw - this.S(8.0);
            if (!active) {
                int lw = this.action(
                        g,
                        "prLoad:" + name,
                        Lang.tr("Загрузить"),
                        actionsLeft - this.S(4.0),
                        by,
                        bh,
                        ArcanicMenu.acc(),
                        false,
                        hv,
                        () -> this.load(name),
                        sc,
                        listTop);
                actionsLeft -= lw + this.S(4.0);
            }
            Px.fade = kk;
        }
        // otherwise its state or date at the right
        int sideRight = Math.round(right - (right - actionsLeft) * hk);
        int fs = this.S(11.5);
        String side = active ? Lang.tr("включён") : this.date.format(new Date(p.saved()));
        int sw = Px.textWidth(side, Px.Weight.MEDIUM, fs);
        if (hk < 0.99F) {
            float kk = Px.fade;
            Px.fade = kk * (1.0F - hk);
            Px.text(
                    g,
                    side,
                    sideRight - sw,
                    yy + (h - fs) / 2,
                    Px.Weight.MEDIUM,
                    fs,
                    active ? ArcanicMenu.acc() : ArcanicMenu.faint());
            Px.fade = kk;
        }
        int room = Math.min(sideRight - sw, actionsLeft) - this.S(16.0) - x;
        int ns = this.S(14.0);
        Px.text(
                g,
                Px.ellipsize(name, Px.Weight.SEMIBOLD, ns, room),
                x,
                yy + this.S(9.0),
                Px.Weight.SEMIBOLD,
                ns,
                ArcanicMenu.T().text());
        Px.text(
                g,
                Px.ellipsize(this.about(p), Px.Weight.MEDIUM, this.S(11.0), room),
                x,
                yy + this.S(27.0),
                Px.Weight.MEDIUM,
                this.S(11.0),
                ArcanicMenu.faint());
        Px.fade = k;
        if (!this.menu.under()) {
            this.menu.clipped(
                    x,
                    y,
                    Math.max(0, actionsLeft - x),
                    h,
                    sc,
                    listTop,
                    this.menu.bottom,
                    () -> this.load(name),
                    () -> this.delete(name));
        }
    }

    /** An action of a row: a word in its colour, a light plate on hover; the armed delete stands in red. */
    private int action(
            GuiGraphics g,
            String id,
            String text,
            int right,
            int y,
            int h,
            int colour,
            boolean filled,
            boolean live,
            Runnable click,
            float sc,
            int listTop) {
        int fs = this.S(12.0);
        int w = Px.textWidth(text, Px.Weight.SEMIBOLD, fs) + this.S(20.0);
        int x = right - w;
        boolean over = live && this.menu.hover(x, Math.round((float) y - sc), w, h);
        float ok = this.menu.an(id + "H", over ? 1.0F : 0.0F, 18.0F);
        if (filled) {
            Px.round(g, x, y, w, h, this.S(8.0), colour);
        } else if (ok > 0.01F) {
            Px.round(g, x, y, w, h, this.S(8.0), Px.a(colour, Math.round(34.0F * ok)));
        }
        Px.textCenter(g, text, x + w / 2, y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, filled ? -1 : colour);
        if (live && !this.menu.under()) {
            this.menu.clipped(
                    x,
                    y,
                    w,
                    h,
                    sc,
                    listTop,
                    this.menu.bottom,
                    () -> {
                        UiSound.play("click");
                        click.run();
                    },
                    null);
        }
        return w;
    }

    private String about(Config.Preset p) {
        StringBuilder line = new StringBuilder(Lang.tr(p.cloud() ? "Из облака" : "На этом ПК"));
        if (!p.server().isEmpty()) line.append("  ·  ").append(p.server());
        if (!p.code().isEmpty())
            line.append("  ·  ").append(Lang.tr("код")).append(' ').append(p.code());
        return line.append("  ·  ")
                .append(this.date.format(new Date(p.saved())))
                .toString();
    }
}
