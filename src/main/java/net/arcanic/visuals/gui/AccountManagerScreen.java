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
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.LocalAccounts;
import net.arcanic.visuals.core.MicrosoftAccounts;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class AccountManagerScreen extends Screen {
    private static final int PER_PAGE = 9;
    private static final int PER_ROW = 5;
    private final Screen parent;
    private EditBox nickname;
    private Button add;
    private String status = "";
    private String draft = "";
    private String undo;
    private boolean error;
    private long statusAt;
    private long openedAt = System.nanoTime();
    private long pageAt = System.nanoTime();
    private int page;
    private int cx;
    private int top;
    private int lineY;
    private int lineW;
    private int actionsY;
    private boolean offlineOpen;
    private boolean requestedMicrosoft;
    private float u;
    private double mouseX;
    private double mouseY;
    private final Anim anim = new Anim();
    private final List<Tile> tiles = new ArrayList<>();

    private List<String> accountKeys() {
        List<String> out = new ArrayList(LocalAccounts.list());

        for (MicrosoftAccounts.Profile a : MicrosoftAccounts.list()) {
            out.add("ms:" + a.uuid());
        }

        return out;
    }

    private String accountName(String key) {
        if (key != null && key.startsWith("ms:")) {
            for (MicrosoftAccounts.Profile a : MicrosoftAccounts.list()) {
                if (key.equals("ms:" + a.uuid())) {
                    return a.name();
                }
            }
        }

        return key;
    }

    private void accountDone(String error) {
        if (Minecraft.getInstance().screen == this) {
            this.notify(error, "Аккаунты Microsoft обновлены");
            this.init();
        }
    }

    private boolean cannotSwitch() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null && mc.getConnection() == null && mc.getSingleplayerServer() == null) {
            return false;
        } else {
            this.notify("Выйдите из мира, чтобы сменить профиль", "");
            return true;
        }
    }

    public AccountManagerScreen(Screen parent) {
        super(Component.literal(Lang.tr("Локальные профили")));
        this.parent = parent;
        this.anim.set("open", 0.0F);
    }

    private int s(float v) {
        return Math.max(1, Math.round(v * this.u));
    }

    private int logical(int px) {
        return Math.round((float) px / (float) Px.guiScale());
    }

    private int tileW() {
        return this.s(112.0F);
    }

    private int tileH() {
        return this.s(124.0F);
    }

    private int head() {
        return Math.max(16, this.s(72.0F) / 8 * 8);
    }

    protected void init() {
        if (this.nickname != null) {
            this.draft = this.nickname.getValue();
        }

        this.clearWidgets();
        this.tiles.clear();
        this.u = Math.max(0.45F, Math.min(1.35F, Math.min((float) Px.height() / 760.0F, (float) Px.width() / 980.0F)));
        if (!this.requestedMicrosoft) {
            this.requestedMicrosoft = true;
            MicrosoftAccounts.refreshList(this::accountDone);
        }

        List<String> names = this.accountKeys();
        this.page = Math.min(this.page, Math.max(0, (names.size() - 1) / 9));
        List<String> shown = new ArrayList<>();
        shown.add(null);

        for (int i = 0; i < 9 && this.page * 9 + i < names.size(); ++i) {
            shown.add((String) names.get(this.page * 9 + i));
        }

        int rows = (shown.size() + 5 - 1) / 5;
        int gap = this.s(14.0F);
        int blockH = this.s(30.0F)
                + this.s(36.0F)
                + rows * this.tileH()
                + (rows - 1) * gap
                + this.s(40.0F)
                + this.s(46.0F)
                + this.s(22.0F)
                + this.s(34.0F)
                + this.s(40.0F);
        this.cx = Px.width() / 2;
        this.top = (Px.height() - blockH) / 2;
        int ty = this.top + this.s(30.0F) + this.s(36.0F);

        for (int i = 0; i < shown.size(); ++i) {
            int row = i / 5;
            int inRow = Math.min(5, shown.size() - row * 5);
            int col = i % 5;
            int rowW = inRow * this.tileW() + (inRow - 1) * gap;
            int tx = this.cx - rowW / 2 + col * (this.tileW() + gap);
            int tyy = ty + row * (this.tileH() + gap);
            String name = (String) shown.get(i);
            Tile t = new Tile(
                    name == null ? LocalAccounts.launcherName() : this.accountName(name),
                    name == null,
                    tx,
                    tyy,
                    this.tileW(),
                    this.tileH(),
                    i,
                    name == null ? () -> this.select((String) null) : () -> this.select(name));
            t.accountKey = name;
            this.tiles.add(t);
            this.addRenderableWidget(t);
            if (name != null) {
                (new Remove(t, () -> this.remove(name))).addTo(this);
            }
        }

        this.actionsY = ty + rows * this.tileH() + (rows - 1) * gap + this.s(40.0F);
        int bw2 = this.s(232.0F);
        int bh2 = this.s(46.0F);
        int bg = this.s(12.0F);
        this.lineW = bw2 * 2 + bg;
        (new Action("Добавить оффлайн", false, this.cx - this.lineW / 2, this.actionsY, bw2, bh2, this::toggleOffline))
                .addTo(this);
        (new Action("Добавить Microsoft", true, this.cx + bg / 2, this.actionsY, bw2, bh2, this::microsoft))
                .addTo(this);
        this.lineY = this.actionsY + bh2 + this.s(22.0F);
        int fh = this.s(34.0F);
        int bw = this.s(96.0F);
        this.nickname = new EditBox(
                this.font,
                this.logical(this.cx - this.lineW / 2),
                this.logical(this.lineY),
                this.logical(this.lineW - bw - this.s(8.0F)),
                this.logical(fh),
                Component.literal(Lang.tr("Ник профиля")));
        this.nickname.setMaxLength(16);
        this.nickname.setBordered(false);
        this.nickname.setTextColor(-923137);
        this.nickname.setValue(this.draft);
        this.nickname.setVisible(this.offlineOpen);
        this.addWidget(this.nickname);
        this.add = new AddButton(this.cx + this.lineW / 2 - bw, this.lineY, bw, fh, this::addProfile);
        this.add.visible = this.offlineOpen;
        this.addRenderableWidget(this.add);
        this.nickname.setResponder((v) -> this.add.active = LocalAccounts.valid(v.trim()));
        this.add.active = LocalAccounts.valid(this.draft.trim());
        (new TextButton("Назад", this.s(26.0F), this.s(22.0F), this::onClose)).addTo(this);
        int pages = Math.max(1, (names.size() + 9 - 1) / 9);
        if (pages > 1) {
            int py = ty + (this.tileH() - this.s(32.0F)) / 2;
            int span = 5 * this.tileW() + 4 * gap;
            Button prev = (new Arrow(true, this.cx - span / 2 - this.s(56.0F), py, () -> {
                        --this.page;
                        this.pageAt = System.nanoTime();
                        this.init();
                    }))
                    .addTo(this);
            prev.active = this.page > 0;
            Button next = (new Arrow(false, this.cx + span / 2 + this.s(24.0F), py, () -> {
                        ++this.page;
                        this.pageAt = System.nanoTime();
                        this.init();
                    }))
                    .addTo(this);
            next.active = (this.page + 1) * 9 < names.size();
        }

        if (this.undo != null) {
            int sw = this.statusWidth();
            (new TextButton("Вернуть", this.cx + sw / 2 - this.s(32.0F), this.statusY() - this.s(9.0F), () -> {
                        String back = this.undo;
                        this.undo = null;
                        this.notify(LocalAccounts.add(back), "Профиль «" + back + "» возвращён");
                        this.init();
                    }))
                    .accent()
                    .addTo(this);
        }

        if (this.offlineOpen) {
            this.setInitialFocus(this.nickname);
        }
    }

    private int statusY() {
        return this.offlineOpen ? this.lineY + this.s(56.0F) : this.lineY + this.s(4.0F);
    }

    private void toggleOffline() {
        this.offlineOpen = !this.offlineOpen;
        UiSound.play(this.offlineOpen ? "pop" : "click");
        this.init();
        if (this.offlineOpen) {
            this.setFocused(this.nickname);
        }
    }

    private void microsoft() {
        if (!MicrosoftAccounts.busy() && !this.cannotSwitch()) {
            UiSound.play("click");
            this.notify((String) null, "Открываем вход Microsoft в браузере…");
            MicrosoftAccounts.login(this::accountDone);
            this.init();
        }
    }

    private int statusWidth() {
        return Px.textWidth(Lang.tr(this.status), Px.Weight.MEDIUM, this.s(12.0F));
    }

    private void addProfile() {
        String name = this.nickname.getValue().trim();
        String result = LocalAccounts.add(this.nickname.getValue());
        this.notify(result, "Профиль «" + name + "» добавлен");
        if (result == null) {
            UiSound.play("toggle_on");
            this.nickname.setValue("");
            this.draft = "";
            this.undo = null;
            this.page = (LocalAccounts.list().size() - 1) / 9;
            this.pageAt = System.nanoTime();
            this.init();
        } else {
            UiSound.play("pop", 0.7F, 1.0F);
        }
    }

    private void select(String name) {
        if (!MicrosoftAccounts.busy() && !this.cannotSwitch()) {
            if (name != null && name.startsWith("ms:")) {
                this.notify((String) null, "Проверяем аккаунт Microsoft…");
                MicrosoftAccounts.select(name.substring(3), this::accountDone);
                this.init();
            } else {
                String result = name == null ? LocalAccounts.restore() : LocalAccounts.select(name);
                String var10002 = Minecraft.getInstance().getUser().getName();
                this.notify(result, "Играешь как " + var10002);
                UiSound.play(result == null ? "select" : "pop");
                this.undo = null;
                this.init();
            }
        }
    }

    private void remove(String name) {
        if (!MicrosoftAccounts.busy()) {
            if (name.startsWith("ms:")) {
                this.undo = null;
                MicrosoftAccounts.remove(name.substring(3), this::accountDone);
                this.init();
            } else {
                String result = LocalAccounts.remove(name);
                this.undo = result == null ? name : null;
                this.notify(result, "Профиль «" + name + "» удалён");
                UiSound.play(result == null ? "reset" : "pop");
                this.init();
            }
        }
    }

    private void notify(String result, String success) {
        this.error = result != null;
        this.status = this.error ? result : success;
        this.statusAt = System.nanoTime();
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 && this.offlineOpen) {
            this.toggleOffline();
            return true;
        } else if (event.key() == 257 && this.nickname.isFocused()) {
            if (this.add.active) {
                this.addProfile();
            } else {
                UiSound.play("pop", 0.7F, 1.0F);
            }

            return true;
        } else {
            return super.keyPressed(event);
        }
    }

    public void onClose() {
        MicrosoftAccounts.cancel();
        Minecraft.getInstance().setScreen(this.parent);
    }

    public boolean isPauseScreen() {
        return false;
    }

    private static float ease(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private float since(long t) {
        return (float) (System.nanoTime() - t) / 1.0E9F;
    }

    private float enter(float delay) {
        return Theme.animations ? ease((this.since(this.openedAt) - delay) / 0.4F) : 1.0F;
    }

    public void render(GuiGraphics g, int mx, int my, float delta) {
        this.anim.frame();
        float opacity = this.anim.to("open", 1.0F, 9.0F);
        this.mouseX = Px.px((double) mx);
        this.mouseY = Px.px((double) my);
        Px.begin(g);
        float old = Px.fade;

        try {
            MenuBackdrop.draw(g);
            g.nextStratum();
            Px.rect(g, 0, 0, Px.width(), Px.height(), Px.a(328458, (int) (80.0F * opacity)));
            float e = this.enter(0.0F);
            Px.fade = opacity * e;
            Px.textCenter(
                    g,
                    Lang.tr("Кто играет?"),
                    this.cx,
                    this.top + Math.round((float) this.s(8.0F) * (1.0F - e)),
                    Px.Weight.SEMIBOLD,
                    this.s(24.0F),
                    -724488);
            float ek = this.enter(0.12F);
            float ok = this.anim.to("offline", this.offlineOpen ? 1.0F : 0.0F, this.offlineOpen ? 12.0F : 16.0F);
            Px.fade = opacity * ek * ok;
            g.pose().pushMatrix();
            g.pose().translate(0.0F, (float) Math.round((float) (-this.s(8.0F)) * (1.0F - ok)));
            int a2 = Theme.accent2() | 0xFF000000;
            int fh = this.s(34.0F);
            int fx = this.cx - this.lineW / 2;
            int fw = this.lineW - this.s(96.0F) - this.s(8.0F);
            boolean focus = this.nickname.isFocused();
            float fv = this.anim.to("field", focus ? 1.0F : 0.0F, 14.0F);
            String value = this.nickname.getValue();
            boolean bad = !value.isEmpty() && !LocalAccounts.valid(value.trim());
            boolean dup = !value.isEmpty() && LocalAccounts.list().contains(value.trim());
            Px.rect(
                    g,
                    fx,
                    this.lineY + fh - 1,
                    fw,
                    Math.max(1, this.s(1.0F)),
                    bad ? 0xC0F87171 : Px.mix(822083583, a2, fv));
            int tfs = this.s(15.0F);
            int tyy = this.lineY + (fh - tfs) / 2;
            if (value.isEmpty()) {
                Px.text(g, Lang.tr("Новый ник"), fx, tyy, Px.Weight.MEDIUM, tfs, -9475972);
            } else {
                Px.text(g, value, fx, tyy, Px.Weight.SEMIBOLD, tfs, -724488);
            }

            if (focus && System.currentTimeMillis() / 530L % 2L == 0L) {
                int cur = Math.max(0, Math.min(value.length(), this.nickname.getCursorPosition()));
                int x = fx
                        + (cur == 0 ? 0 : Px.textWidth(value.substring(0, cur), Px.Weight.SEMIBOLD, tfs))
                        + (value.isEmpty() ? 0 : 1);
                Px.rect(g, x, this.lineY + this.s(9.0F), Math.max(1, this.s(2.0F)), fh - this.s(18.0F), a2);
            }

            String hint = bad
                    ? (value.trim().length() < 3 ? "минимум 3 символа" : "латиница, цифры и _")
                    : (dup ? "такой уже есть" : "");
            if (!hint.isEmpty()) {
                Px.textRight(g, Lang.tr(hint), fx + fw, tyy + this.s(2.0F), Px.Weight.MEDIUM, this.s(11.0F), -495247);
            }

            g.pose().popMatrix();
            Px.fade = opacity * ek;
            if (!this.status.isEmpty()) {
                float sa = Math.min(1.0F, this.since(this.statusAt) / 0.25F);
                Px.fade = opacity * ek * sa;
                int sw = this.statusWidth();
                int sx = this.cx - sw / 2 - (this.undo != null ? this.s(40.0F) : 0);
                Px.text(
                        g,
                        Lang.tr(this.status),
                        sx,
                        this.statusY(),
                        Px.Weight.MEDIUM,
                        this.s(12.0F),
                        this.error ? -24401 : -7370596);
            }
        } finally {
            Px.fade = old;
            Px.end(g);
        }

        g.nextStratum();
        super.render(g, mx, my, delta);
    }

    private abstract class Styled extends Button {
        final Anim hover = new Anim();
        final int px;
        final int py;
        final int pw;
        final int ph;

        Styled(String label, int px, int py, int pw, int ph, Runnable action) {
            super(
                    AccountManagerScreen.this.logical(px),
                    AccountManagerScreen.this.logical(py),
                    AccountManagerScreen.this.logical(pw),
                    AccountManagerScreen.this.logical(ph),
                    Component.literal(label),
                    (b) -> action.run(),
                    (sup) -> (MutableComponent) sup.get());
            this.px = px;
            this.py = py;
            this.pw = pw;
            this.ph = ph;
            this.hover.set("h", 0.0F);
        }

        Styled addTo(AccountManagerScreen screen) {
            screen.addRenderableWidget(this);
            return this;
        }

        float hv() {
            this.hover.frame();
            return this.hover.to("h", this.active && this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
        }

        protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
            float v = this.hv();
            Px.begin(g);
            float old = Px.fade;
            float k = this.fadeIn();
            Px.fade = AccountManagerScreen.this.anim.to("open", 1.0F, 9.0F) * (this.active ? 1.0F : 0.35F) * k;
            g.pose().pushMatrix();
            g.pose().translate(0.0F, (float) Math.round((float) AccountManagerScreen.this.s(10.0F) * (1.0F - k)));

            try {
                this.draw(g, v);
            } finally {
                g.pose().popMatrix();
                Px.fade = old;
                Px.end(g);
            }
        }

        float fadeIn() {
            return AccountManagerScreen.this.enter(0.12F);
        }

        abstract void draw(GuiGraphics var1, float var2);
    }

    private final class Tile extends Styled {
        String accountKey;
        final String name;
        final boolean launcher;
        final int order;

        Tile(String name, boolean launcher, int x, int y, int w, int h, int order, Runnable action) {
            super(name, x, y, w, h, action);
            this.name = name;
            this.launcher = launcher;
            this.order = order;
        }

        boolean chosen() {
            return this.launcher
                    ? LocalAccounts.isLauncher()
                    : (this.accountKey != null && this.accountKey.startsWith("ms:")
                            ? this.accountKey.equals("ms:" + LocalAccounts.onlineUuid())
                            : LocalAccounts.isLocal()
                                    && Minecraft.getInstance()
                                            .getUser()
                                            .getName()
                                            .equals(this.name));
        }

        boolean over() {
            return AccountManagerScreen.this.mouseX >= (double) this.px
                    && AccountManagerScreen.this.mouseX < (double) (this.px + this.pw)
                    && AccountManagerScreen.this.mouseY >= (double) this.py
                    && AccountManagerScreen.this.mouseY < (double) (this.py + this.ph);
        }

        float fadeIn() {
            return Theme.animations
                    ? AccountManagerScreen.ease((AccountManagerScreen.this.since(AccountManagerScreen.this.pageAt)
                                    - 0.05F
                                    - (float) this.order * 0.045F)
                            / 0.4F)
                    : 1.0F;
        }

        void draw(GuiGraphics g, float v) {
            boolean on = this.chosen();
            int a1 = Theme.accent() | 0xFF000000;
            int a2 = Theme.accent2() | 0xFF000000;
            int hs = AccountManagerScreen.this.head();
            int fr = AccountManagerScreen.this.s(5.0F);
            int hx = this.px + (this.pw - hs) / 2;
            int hy = this.py
                    + AccountManagerScreen.this.s(4.0F)
                    - Math.round((float) AccountManagerScreen.this.s(3.0F) * v);
            Px.round(
                    g,
                    hx - fr,
                    hy - fr,
                    hs + fr * 2,
                    hs + fr * 2,
                    AccountManagerScreen.this.s(10.0F),
                    on ? Px.a(a1, 210) : Px.a(0xFFFFFF, (int) (10.0F + 22.0F * v)));
            float dim = on ? 1.0F : 0.72F + 0.28F * v;
            int tint = Math.round(255.0F * Px.fade) << 24 | Px.mix(-6645853, -1, dim) & 0xFFFFFF;
            PlayerFaceRenderer.draw(
                    g, DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(this.name)), hx, hy, hs, tint);
            int ny = hy + hs + fr + AccountManagerScreen.this.s(12.0F);
            String shownName = Px.ellipsize(
                    this.name,
                    Px.Weight.SEMIBOLD,
                    AccountManagerScreen.this.s(14.0F),
                    this.pw - AccountManagerScreen.this.s(6.0F));
            Px.textCenter(
                    g,
                    shownName,
                    this.px + this.pw / 2,
                    ny,
                    Px.Weight.SEMIBOLD,
                    AccountManagerScreen.this.s(14.0F),
                    on ? -1 : Px.mix(-5988944, -724488, v));
            if (this.launcher) {
                Px.textCenter(
                        g,
                        Lang.tr("лаунчер"),
                        this.px + this.pw / 2,
                        ny + AccountManagerScreen.this.s(19.0F),
                        Px.Weight.MEDIUM,
                        AccountManagerScreen.this.s(11.0F),
                        -9475972);
            } else if (this.accountKey != null && this.accountKey.startsWith("ms:")) {
                Px.textCenter(
                        g,
                        "Microsoft",
                        this.px + this.pw / 2,
                        ny + AccountManagerScreen.this.s(19.0F),
                        Px.Weight.MEDIUM,
                        AccountManagerScreen.this.s(11.0F),
                        on ? a2 : -9475972);
            } else if (on) {
                int bw = AccountManagerScreen.this.s(18.0F);
                Px.round(
                        g,
                        this.px + (this.pw - bw) / 2,
                        ny + AccountManagerScreen.this.s(22.0F),
                        bw,
                        Math.max(2, AccountManagerScreen.this.s(2.0F)),
                        AccountManagerScreen.this.s(1.0F),
                        a2);
            }
        }
    }

    private final class Remove extends Styled {
        final Tile tile;

        Remove(Tile t, Runnable action) {
            super(
                    "×",
                    t.px
                            + (t.pw + AccountManagerScreen.this.head()) / 2
                            + AccountManagerScreen.this.s(5.0F)
                            - AccountManagerScreen.this.s(20.0F),
                    t.py,
                    AccountManagerScreen.this.s(24.0F),
                    AccountManagerScreen.this.s(24.0F),
                    action);
            this.tile = t;
        }

        float fadeIn() {
            return this.tile.fadeIn();
        }

        void draw(GuiGraphics g, float v) {
            float show = Math.max(
                    v,
                    AccountManagerScreen.this.anim.to(
                            "rm:" + this.tile.name,
                            !this.tile.over() && !this.isHoveredOrFocused() ? 0.0F : 1.0F,
                            14.0F));
            if (!(show < 0.02F)) {
                float old = Px.fade;
                Px.fade = old * show;
                int y = this.py - Math.round((float) AccountManagerScreen.this.s(3.0F) * Math.max(v, show));
                Px.circle(g, this.px + this.pw / 2, y + this.ph / 2, this.pw / 2, Px.mix(0xF01A1724, -2075041, v));
                int is = AccountManagerScreen.this.s(12.0F);
                Px.icon(g, "x", this.px + (this.pw - is) / 2, y + (this.ph - is) / 2, is, Px.mix(-4607290, -1, v));
                Px.fade = old;
            }
        }
    }

    private final class Action extends Styled {
        final boolean ms;

        Action(String label, boolean ms, int x, int y, int w, int h, Runnable action) {
            super(Lang.tr(label), x, y, w, h, action);
            this.ms = ms;
        }

        float fadeIn() {
            return AccountManagerScreen.this.enter(0.1F);
        }

        void draw(GuiGraphics g, float v) {
            boolean on = !this.ms && AccountManagerScreen.this.offlineOpen;
            int a1 = Theme.accent() | 0xFF000000;
            int a2 = Theme.accent2() | 0xFF000000;
            int plate = on ? Px.mix(Px.a(a1, 70), Px.a(a1, 100), v) : Px.mix(0xA6141220, 0xCC1E1B2C, v);
            Px.round(g, this.px, this.py, this.pw, this.ph, AccountManagerScreen.this.s(10.0F), plate);
            Px.outline(
                    g,
                    this.px,
                    this.py,
                    this.pw,
                    this.ph,
                    AccountManagerScreen.this.s(10.0F),
                    on ? Px.a(a2, 110) : Px.mix(318767103, 654311423, v));
            String t = this.getMessage().getString();
            int fs = AccountManagerScreen.this.s(14.0F);
            int is = AccountManagerScreen.this.s(16.0F);
            int gap = AccountManagerScreen.this.s(10.0F);
            int tw = Px.textWidth(t, Px.Weight.MEDIUM, fs);
            int x0 = this.px + (this.pw - (is + gap + tw)) / 2;
            int cy = this.py + this.ph / 2;
            if (this.ms) {
                int q = Math.max(3, (is - Math.max(1, AccountManagerScreen.this.s(1.5F))) / 2);
                int sp = is - q * 2;
                int y0 = cy - is / 2;
                Px.rect(g, x0, y0, q, q, -896990);
                Px.rect(g, x0 + q + sp, y0, q, q, -8406528);
                Px.rect(g, x0, y0 + q + sp, q, q, -16734993);
                Px.rect(g, x0 + q + sp, y0 + q + sp, q, q, -18176);
            } else {
                Px.icon(g, "user", x0, cy - is / 2, is, on ? -1 : Px.mix(-7370596, a2, v));
            }

            Px.text(g, t, x0 + is + gap, cy - fs / 2, Px.Weight.MEDIUM, fs, on ? -1 : Px.mix(-3159591, -1, v));
        }
    }

    private final class AddButton extends Styled {
        AddButton(int x, int y, int w, int h, Runnable action) {
            super(Lang.tr("Добавить"), x, y, w, h, action);
        }

        float fadeIn() {
            return AccountManagerScreen.this.enter(0.12F);
        }

        void draw(GuiGraphics g, float v) {
            int a2 = Theme.accent2() | 0xFF000000;
            boolean ok = this.active;
            String t = Lang.tr("Добавить");
            if (ok && v > 0.02F) {
                Px.round(g, this.px, this.py, this.pw, this.ph, AccountManagerScreen.this.s(8.0F), Px.a(a2, (int)
                        (34.0F * v)));
            }

            Px.textCenter(
                    g,
                    t,
                    this.px + this.pw / 2,
                    this.py + (this.ph - AccountManagerScreen.this.s(13.0F)) / 2,
                    Px.Weight.SEMIBOLD,
                    AccountManagerScreen.this.s(13.0F),
                    ok ? Px.mix(a2, -1, v * 0.5F) : -11580838);
        }
    }

    private final class TextButton extends Styled {
        boolean accent;

        TextButton(String label, int x, int y, Runnable action) {
            super(
                    Lang.tr(label),
                    x,
                    y,
                    Px.textWidth(Lang.tr(label), Px.Weight.SEMIBOLD, AccountManagerScreen.this.s(13.0F))
                            + AccountManagerScreen.this.s(24.0F),
                    AccountManagerScreen.this.s(30.0F),
                    action);
        }

        TextButton accent() {
            this.accent = true;
            return this;
        }

        TextButton addTo(AccountManagerScreen screen) {
            super.addTo(screen);
            return this;
        }

        float fadeIn() {
            return AccountManagerScreen.this.enter(0.0F);
        }

        void draw(GuiGraphics g, float v) {
            String t = this.getMessage().getString();
            int fs = AccountManagerScreen.this.s(13.0F);
            if (this.accent) {
                Px.text(
                        g,
                        t,
                        this.px,
                        this.py + (this.ph - fs) / 2,
                        Px.Weight.SEMIBOLD,
                        fs,
                        Px.mix(Theme.accent2() | 0xFF000000, -1, v));
                int tw = Px.textWidth(t, Px.Weight.SEMIBOLD, fs);
                Px.rect(
                        g,
                        this.px,
                        this.py + (this.ph + fs) / 2 + AccountManagerScreen.this.s(2.0F),
                        Math.round((float) tw * v),
                        Math.max(1, AccountManagerScreen.this.s(1.0F)),
                        Theme.accent2() | 0xFF000000);
            } else {
                int is = AccountManagerScreen.this.s(12.0F);
                g.pose().pushMatrix();
                g.pose()
                        .translate(
                                (float) this.px
                                        + (float) is / 2.0F
                                        - (float) Math.round((float) AccountManagerScreen.this.s(3.0F) * v),
                                (float) this.py + (float) this.ph / 2.0F);
                g.pose().rotate((float) Math.PI);
                Px.icon(g, "chevron-right", -is / 2, -is / 2, is, Px.mix(-7370596, -724488, v));
                g.pose().popMatrix();
                Px.text(
                        g,
                        t,
                        this.px + is + AccountManagerScreen.this.s(6.0F),
                        this.py + (this.ph - fs) / 2,
                        Px.Weight.SEMIBOLD,
                        fs,
                        Px.mix(-7370596, -724488, v));
            }
        }
    }

    private final class Arrow extends Styled {
        final boolean back;

        Arrow(boolean back, int x, int y, Runnable action) {
            super(
                    back ? "‹" : "›",
                    x,
                    y,
                    AccountManagerScreen.this.s(32.0F),
                    AccountManagerScreen.this.s(32.0F),
                    action);
            this.back = back;
        }

        Arrow addTo(AccountManagerScreen screen) {
            super.addTo(screen);
            return this;
        }

        void draw(GuiGraphics g, float v) {
            if (v > 0.02F) {
                Px.circle(g, this.px + this.pw / 2, this.py + this.ph / 2, this.pw / 2, Px.a(0xFFFFFF, (int)
                        (18.0F * v)));
            }

            int is = AccountManagerScreen.this.s(15.0F);
            g.pose().pushMatrix();
            g.pose().translate((float) this.px + (float) this.pw / 2.0F, (float) this.py + (float) this.ph / 2.0F);
            if (this.back) {
                g.pose().rotate((float) Math.PI);
            }

            Px.icon(g, "chevron-right", -is / 2, -is / 2, is, Px.mix(-7370596, -724488, v));
            g.pose().popMatrix();
        }
    }
}
