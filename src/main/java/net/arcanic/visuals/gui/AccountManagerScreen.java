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
import net.arcanic.visuals.render.*;
import net.arcanic.visuals.render.Px.Weight;
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
import org.lwjgl.glfw.GLFW;

/**
 * Local profiles as an account picker: the skin heads in a row, the name under each, the one in play underlined in
 * the accent.
 */
public final class AccountManagerScreen extends Screen {
    private static final int PER_PAGE = 9, PER_ROW = 5;
    private final Screen parent;
    private EditBox nickname;
    private Button add;
    private String status = "", draft = "", undo;
    private boolean error;
    private long statusAt, openedAt = System.nanoTime(), pageAt = System.nanoTime();
    private int page, cx, top, lineY, lineW, actionsY;
    private boolean offlineOpen, requestedMicrosoft;

    private List<String> accountKeys() {
        List<String> out = new ArrayList<>(LocalAccounts.list());
        for (var a : MicrosoftAccounts.list()) out.add("ms:" + a.uuid());
        return out;
    }

    private String accountName(String key) {
        if (key != null && key.startsWith("ms:"))
            for (var a : MicrosoftAccounts.list()) if (key.equals("ms:" + a.uuid())) return a.name();
        return key;
    }

    private void accountDone(String error) {
        if (Minecraft.getInstance().screen != this) return;
        notify(error, "Аккаунты Microsoft обновлены");
        init();
    }

    private boolean cannotSwitch() {
        var mc = Minecraft.getInstance();
        if (mc.level != null || mc.getConnection() != null || mc.getSingleplayerServer() != null) {
            notify("Выйдите из мира, чтобы сменить профиль", "");
            return true;
        }
        return false;
    }

    private float u;
    private double mouseX, mouseY;
    private final Anim anim = new Anim();
    private final List<Tile> tiles = new ArrayList<>();

    public AccountManagerScreen(Screen parent) {
        super(Component.literal(Lang.tr("Локальные профили")));
        this.parent = parent;
        anim.set("open", 0);
    }

    private int s(float v) {
        return Math.max(1, Math.round(v * u));
    }

    private int logical(int px) {
        return Math.round(px / (float) Px.guiScale());
    }

    private int tileW() {
        return s(112);
    }

    private int tileH() {
        return s(124);
    }

    private int head() {
        return Math.max(16, s(72) / 8 * 8);
    }

    @Override
    protected void init() {
        if (nickname != null) draft = nickname.getValue();
        clearWidgets();
        tiles.clear();
        u = Math.max(.45f, Math.min(1.35f, Math.min(Px.height() / 760f, Px.width() / 980f)));
        if (!requestedMicrosoft) {
            requestedMicrosoft = true;
            MicrosoftAccounts.refreshList(this::accountDone);
        }
        List<String> names = accountKeys();
        page = Math.min(page, Math.max(0, (names.size() - 1) / PER_PAGE));
        List<String> shown = new ArrayList<>();
        shown.add(null); // the launcher's own profile comes first
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < names.size(); i++)
            shown.add(names.get(page * PER_PAGE + i));
        int rows = (shown.size() + PER_ROW - 1) / PER_ROW, gap = s(14);
        int blockH = s(30) + s(36) + rows * tileH() + (rows - 1) * gap + s(40) + s(46) + s(22) + s(34) + s(40);
        cx = Px.width() / 2;
        top = (Px.height() - blockH) / 2;
        int ty = top + s(30) + s(36);
        for (int i = 0; i < shown.size(); i++) {
            int row = i / PER_ROW, inRow = Math.min(PER_ROW, shown.size() - row * PER_ROW), col = i % PER_ROW;
            int rowW = inRow * tileW() + (inRow - 1) * gap;
            int tx = cx - rowW / 2 + col * (tileW() + gap), tyy = ty + row * (tileH() + gap);
            String name = shown.get(i);
            Tile t = new Tile(
                    name == null ? LocalAccounts.launcherName() : accountName(name),
                    name == null,
                    tx,
                    tyy,
                    tileW(),
                    tileH(),
                    i,
                    name == null ? () -> select(null) : () -> select(name));
            t.accountKey = name;
            tiles.add(t);
            addRenderableWidget(t);
            if (name != null) new Remove(t, () -> remove(name)).addTo(this);
        }
        // two ways to add a profile, side by side under the heads
        actionsY = ty + rows * tileH() + (rows - 1) * gap + s(40);
        int bw2 = s(232), bh2 = s(46), bg = s(12);
        lineW = bw2 * 2 + bg;
        new Action("Добавить оффлайн", false, cx - lineW / 2, actionsY, bw2, bh2, this::toggleOffline).addTo(this);
        new Action("Добавить Microsoft", true, cx + bg / 2, actionsY, bw2, bh2, this::microsoft).addTo(this);
        // the nickname line opens under them
        lineY = actionsY + bh2 + s(22);
        int fh = s(34), bw = s(96);
        nickname = new EditBox(
                font,
                logical(cx - lineW / 2),
                logical(lineY),
                logical(lineW - bw - s(8)),
                logical(fh),
                Component.literal(Lang.tr("Ник профиля")));
        nickname.setMaxLength(16);
        nickname.setBordered(false);
        nickname.setTextColor(0xFFF1E9FF);
        nickname.setValue(draft);
        nickname.setVisible(offlineOpen);
        addWidget(nickname); // typed and focused natively, drawn by us in the client font
        add = new AddButton(cx + lineW / 2 - bw, lineY, bw, fh, this::addProfile);
        add.visible = offlineOpen;
        addRenderableWidget(add);
        nickname.setResponder(v -> add.active = LocalAccounts.valid(v.trim()));
        add.active = LocalAccounts.valid(draft.trim());

        new TextButton("Назад", s(26), s(22), this::onClose).addTo(this);
        int pages = Math.max(1, (names.size() + PER_PAGE - 1) / PER_PAGE);
        if (pages > 1) {
            int py = ty + (tileH() - s(32)) / 2, span = PER_ROW * tileW() + (PER_ROW - 1) * gap;
            Button prev = new Arrow(true, cx - span / 2 - s(56), py, () -> {
                        page--;
                        pageAt = System.nanoTime();
                        init();
                    })
                    .addTo(this);
            prev.active = page > 0;
            Button next = new Arrow(false, cx + span / 2 + s(24), py, () -> {
                        page++;
                        pageAt = System.nanoTime();
                        init();
                    })
                    .addTo(this);
            next.active = (page + 1) * PER_PAGE < names.size();
        }
        if (undo != null) {
            int sw = statusWidth();
            new TextButton("Вернуть", cx + sw / 2 - s(32), statusY() - s(9), () -> {
                        String back = undo;
                        undo = null;
                        notify(LocalAccounts.add(back), "Профиль «" + back + "» возвращён");
                        init();
                    })
                    .accent()
                    .addTo(this);
        }
        if (offlineOpen) setInitialFocus(nickname);
    }

    private int statusY() {
        return offlineOpen ? lineY + s(56) : lineY + s(4);
    }

    private void toggleOffline() {
        offlineOpen = !offlineOpen;
        UiSound.play(offlineOpen ? "pop" : "click");
        init();
        if (offlineOpen) setFocused(nickname);
    }

    private void microsoft() {
        if (MicrosoftAccounts.busy() || cannotSwitch()) return;
        UiSound.play("click");
        notify(null, "Открываем вход Microsoft в браузере…");
        MicrosoftAccounts.login(this::accountDone);
        init();
    }

    private int statusWidth() {
        return Px.textWidth(Lang.tr(status), Weight.MEDIUM, s(12));
    }

    private void addProfile() {
        String name = nickname.getValue().trim();
        String result = LocalAccounts.add(nickname.getValue());
        notify(result, "Профиль «" + name + "» добавлен");
        if (result == null) {
            UiSound.play("toggle_on");
            nickname.setValue("");
            draft = "";
            undo = null;
            page = (LocalAccounts.list().size() - 1) / PER_PAGE;
            pageAt = System.nanoTime();
            init();
        } else UiSound.play("pop", .7f, 1);
    }

    private void select(String name) {
        if (MicrosoftAccounts.busy() || cannotSwitch()) return;
        if (name != null && name.startsWith("ms:")) {
            notify(null, "Проверяем аккаунт Microsoft…");
            MicrosoftAccounts.select(name.substring(3), this::accountDone);
            init();
            return;
        }
        String result = name == null ? LocalAccounts.restore() : LocalAccounts.select(name);
        notify(result, "Играешь как " + Minecraft.getInstance().getUser().getName());
        UiSound.play(result == null ? "select" : "pop");
        undo = null;
        init();
    }

    private void remove(String name) {
        if (MicrosoftAccounts.busy()) return;
        if (name.startsWith("ms:")) {
            undo = null;
            MicrosoftAccounts.remove(name.substring(3), this::accountDone);
            init();
            return;
        }
        String result = LocalAccounts.remove(name);
        undo = result == null ? name : null;
        notify(result, "Профиль «" + name + "» удалён");
        UiSound.play(result == null ? "reset" : "pop");
        init();
    }

    private void notify(String result, String success) {
        error = result != null;
        status = error ? result : success;
        statusAt = System.nanoTime();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && offlineOpen) {
            toggleOffline();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER && nickname.isFocused()) {
            if (add.active) addProfile();
            else UiSound.play("pop", .7f, 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        MicrosoftAccounts.cancel();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private float since(long t) {
        return (System.nanoTime() - t) / 1e9f;
    }

    private float enter(float delay) {
        return Theme.animations ? ease((since(openedAt) - delay) / .4f) : 1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        anim.frame();
        float opacity = anim.to("open", 1, 9);
        mouseX = Px.px(mx);
        mouseY = Px.px(my);
        Px.begin(g);
        float old = Px.fade;
        try {
            MenuBackdrop.draw(g);
            g.nextStratum();
            Px.rect(g, 0, 0, Px.width(), Px.height(), Px.a(0x05030A, (int) (120 * opacity)));
            float e = enter(0);
            Px.fade = opacity * e;
            // the question, plainly
            Px.textCenter(
                    g,
                    Lang.tr("Кто играет?"),
                    cx,
                    top + Math.round(s(8) * (1 - e)),
                    Weight.SEMIBOLD,
                    s(24),
                    0xFFF4F1F8);

            // the line for a new nickname: just an underline that lights up while typing; it opens under the buttons
            float ek = enter(.12f);
            float ok = anim.to("offline", offlineOpen ? 1 : 0, offlineOpen ? 12 : 16);
            Px.fade = opacity * ek * ok;
            g.pose().pushMatrix();
            g.pose().translate(0, Math.round(-s(8) * (1 - ok)));
            int a2 = Theme.accent2() | 0xFF000000, fh = s(34), fx = cx - lineW / 2, fw = lineW - s(96) - s(8);
            boolean focus = nickname.isFocused();
            float fv = anim.to("field", focus ? 1 : 0, 14);
            String value = nickname.getValue();
            boolean bad = !value.isEmpty() && !LocalAccounts.valid(value.trim());
            boolean dup = !value.isEmpty() && LocalAccounts.list().contains(value.trim());
            Px.rect(g, fx, lineY + fh - 1, fw, Math.max(1, s(1)), bad ? 0xC0F87171 : Px.mix(0x30FFFFFF, a2, fv));
            int tfs = s(15), tyy = lineY + (fh - tfs) / 2;
            if (value.isEmpty()) Px.text(g, Lang.tr("Новый ник"), fx, tyy, Weight.MEDIUM, tfs, 0xFF6F687C);
            else Px.text(g, value, fx, tyy, Weight.SEMIBOLD, tfs, 0xFFF4F1F8);
            if (focus && (System.currentTimeMillis() / 530) % 2 == 0) {
                int cur = Math.max(0, Math.min(value.length(), nickname.getCursorPosition()));
                int x = fx
                        + (cur == 0 ? 0 : Px.textWidth(value.substring(0, cur), Weight.SEMIBOLD, tfs))
                        + (value.isEmpty() ? 0 : 1);
                Px.rect(g, x, lineY + s(9), Math.max(1, s(2)), fh - s(18), a2);
            }
            String hint = bad
                    ? (value.trim().length() < 3 ? "минимум 3 символа" : "латиница, цифры и _")
                    : dup ? "такой уже есть" : "";
            if (!hint.isEmpty()) Px.textRight(g, Lang.tr(hint), fx + fw, tyy + s(2), Weight.MEDIUM, s(11), 0xFFF87171);
            g.pose().popMatrix();
            Px.fade = opacity * ek;

            // what just happened
            if (!status.isEmpty()) {
                float sa = Math.min(1, since(statusAt) / .25f);
                Px.fade = opacity * ek * sa;
                int sw = statusWidth(), sx = cx - sw / 2 - (undo != null ? s(40) : 0);
                Px.text(g, Lang.tr(status), sx, statusY(), Weight.MEDIUM, s(12), error ? 0xFFFFA0AF : 0xFF8F889C);
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
        final int px, py, pw, ph;

        Styled(String label, int px, int py, int pw, int ph, Runnable action) {
            super(
                    logical(px),
                    logical(py),
                    logical(pw),
                    logical(ph),
                    Component.literal(label),
                    b -> action.run(),
                    sup -> sup.get());
            this.px = px;
            this.py = py;
            this.pw = pw;
            this.ph = ph;
            hover.set("h", 0);
        }

        Styled addTo(AccountManagerScreen screen) {
            screen.addRenderableWidget(this);
            return this;
        }

        float hv() {
            hover.frame();
            return hover.to("h", active && isHoveredOrFocused() ? 1 : 0, 14);
        }

        @Override
        protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
            float v = hv();
            Px.begin(g);
            float old = Px.fade;
            float k = fadeIn();
            Px.fade = anim.to("open", 1, 9) * (active ? 1 : .35f) * k;
            g.pose().pushMatrix();
            g.pose().translate(0, Math.round(s(10) * (1 - k)));
            try {
                draw(g, v);
            } finally {
                g.pose().popMatrix();
                Px.fade = old;
                Px.end(g);
            }
        }

        float fadeIn() {
            return enter(.12f);
        }

        abstract void draw(GuiGraphics g, float v);
    }

    /** A profile: the skin head in a soft frame, the name under it; the one in play is underlined in the accent. */
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
            return launcher
                    ? LocalAccounts.isLauncher()
                    : accountKey != null && accountKey.startsWith("ms:")
                            ? accountKey.equals("ms:" + LocalAccounts.onlineUuid())
                            : LocalAccounts.isLocal()
                                    && Minecraft.getInstance()
                                            .getUser()
                                            .getName()
                                            .equals(name);
        }

        boolean over() {
            return mouseX >= px && mouseX < px + pw && mouseY >= py && mouseY < py + ph;
        }

        @Override
        float fadeIn() {
            return Theme.animations ? ease((since(pageAt) - .05f - order * .045f) / .4f) : 1;
        }

        @Override
        void draw(GuiGraphics g, float v) {
            boolean on = chosen();
            int a1 = Theme.accent() | 0xFF000000, a2 = Theme.accent2() | 0xFF000000;
            int hs = head(), fr = s(5), hx = px + (pw - hs) / 2, hy = py + s(4) - Math.round(s(3) * v);
            // the frame around the head: the accent for the one in play, a faint light under the cursor for the others
            Px.round(
                    g,
                    hx - fr,
                    hy - fr,
                    hs + fr * 2,
                    hs + fr * 2,
                    s(10),
                    on ? Px.a(a1, 210) : Px.a(0xFFFFFF, (int) (10 + 22 * v)));
            float dim = on ? 1 : .72f + .28f * v;
            int tint = (Math.round(255 * Px.fade) << 24) | Px.mix(0xFF9A97A3, 0xFFFFFFFF, dim) & 0xFFFFFF;
            PlayerFaceRenderer.draw(g, DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(name)), hx, hy, hs, tint);
            int ny = hy + hs + fr + s(12);
            String shownName = Px.ellipsize(name, Weight.SEMIBOLD, s(14), pw - s(6));
            Px.textCenter(
                    g,
                    shownName,
                    px + pw / 2,
                    ny,
                    Weight.SEMIBOLD,
                    s(14),
                    on ? 0xFFFFFFFF : Px.mix(0xFFA49DB0, 0xFFF4F1F8, v));
            if (launcher)
                Px.textCenter(g, Lang.tr("лаунчер"), px + pw / 2, ny + s(19), Weight.MEDIUM, s(11), 0xFF6F687C);
            else if (accountKey != null && accountKey.startsWith("ms:"))
                Px.textCenter(g, "Microsoft", px + pw / 2, ny + s(19), Weight.MEDIUM, s(11), on ? a2 : 0xFF6F687C);
            else if (on) {
                int bw = s(18);
                Px.round(g, px + (pw - bw) / 2, ny + s(22), bw, Math.max(2, s(2)), s(1), a2);
            }
        }
    }

    /** The small cross on a head's corner: shown while the head is under the cursor. */
    private final class Remove extends Styled {
        final Tile tile;

        Remove(Tile t, Runnable action) {
            super("×", t.px + (t.pw + head()) / 2 + s(5) - s(20), t.py, s(24), s(24), action);
            tile = t;
        }

        @Override
        float fadeIn() {
            return tile.fadeIn();
        }

        @Override
        void draw(GuiGraphics g, float v) {
            float show = Math.max(v, anim.to("rm:" + tile.name, tile.over() || isHoveredOrFocused() ? 1 : 0, 14));
            if (show < .02f) return;
            float old = Px.fade;
            Px.fade = old * show;
            int y = py - Math.round(s(3) * Math.max(v, show));
            Px.circle(g, px + pw / 2, y + ph / 2, pw / 2, Px.mix(0xF01A1724, 0xFFE0565F, v));
            int is = s(12);
            Px.icon(g, "x", px + (pw - is) / 2, y + (ph - is) / 2, is, Px.mix(0xFFB9B2C6, 0xFFFFFFFF, v));
            Px.fade = old;
        }
    }

    /** Add offline / Add Microsoft: calm flat plates with their mark and a word. */
    private final class Action extends Styled {
        final boolean ms;

        Action(String label, boolean ms, int x, int y, int w, int h, Runnable action) {
            super(Lang.tr(label), x, y, w, h, action);
            this.ms = ms;
        }

        @Override
        float fadeIn() {
            return enter(.1f);
        }

        @Override
        void draw(GuiGraphics g, float v) {
            boolean on = !ms && offlineOpen;
            int a1 = Theme.accent() | 0xFF000000, a2 = Theme.accent2() | 0xFF000000;
            int plate = on ? Px.mix(Px.a(a1, 70), Px.a(a1, 100), v) : Px.mix(0xA6141220, 0xCC1E1B2C, v);
            Px.round(g, px, py, pw, ph, s(10), plate);
            Px.outline(g, px, py, pw, ph, s(10), on ? Px.a(a2, 110) : Px.mix(0x12FFFFFF, 0x26FFFFFF, v));
            String t = getMessage().getString();
            int fs = s(14),
                    is = s(16),
                    gap = s(10),
                    tw = Px.textWidth(t, Weight.MEDIUM, fs),
                    x0 = px + (pw - (is + gap + tw)) / 2,
                    cy = py + ph / 2;
            if (ms) { // the four-colour window, drawn square by square
                int q = Math.max(3, (is - Math.max(1, s(1.5f))) / 2), sp = is - q * 2, y0 = cy - is / 2;
                Px.rect(g, x0, y0, q, q, 0xFFF25022);
                Px.rect(g, x0 + q + sp, y0, q, q, 0xFF7FBA00);
                Px.rect(g, x0, y0 + q + sp, q, q, 0xFF00A4EF);
                Px.rect(g, x0 + q + sp, y0 + q + sp, q, q, 0xFFFFB900);
            } else Px.icon(g, "user", x0, cy - is / 2, is, on ? 0xFFFFFFFF : Px.mix(0xFF8F889C, a2, v));
            Px.text(
                    g,
                    t,
                    x0 + is + gap,
                    cy - fs / 2,
                    Weight.MEDIUM,
                    fs,
                    on ? 0xFFFFFFFF : Px.mix(0xFFCFC9D9, 0xFFFFFFFF, v));
        }
    }

    private final class AddButton extends Styled {
        AddButton(int x, int y, int w, int h, Runnable action) {
            super(Lang.tr("Добавить"), x, y, w, h, action);
        }

        @Override
        float fadeIn() {
            return enter(.12f);
        }

        @Override
        void draw(GuiGraphics g, float v) {
            int a2 = Theme.accent2() | 0xFF000000;
            boolean ok = active;
            String t = Lang.tr("Добавить");
            if (ok && v > .02f) Px.round(g, px, py, pw, ph, s(8), Px.a(a2, (int) (34 * v)));
            Px.textCenter(
                    g,
                    t,
                    px + pw / 2,
                    py + (ph - s(13)) / 2,
                    Weight.SEMIBOLD,
                    s(13),
                    ok ? Px.mix(a2, 0xFFFFFFFF, v * .5f) : 0xFF4F4A5A);
        }
    }

    private final class TextButton extends Styled {
        boolean accent;

        TextButton(String label, int x, int y, Runnable action) {
            super(Lang.tr(label), x, y, Px.textWidth(Lang.tr(label), Weight.SEMIBOLD, s(13)) + s(24), s(30), action);
        }

        TextButton accent() {
            accent = true;
            return this;
        }

        @Override
        TextButton addTo(AccountManagerScreen screen) {
            super.addTo(screen);
            return this;
        }

        @Override
        float fadeIn() {
            return enter(0);
        }

        @Override
        void draw(GuiGraphics g, float v) {
            String t = getMessage().getString();
            int fs = s(13);
            if (accent) {
                Px.text(
                        g,
                        t,
                        px,
                        py + (ph - fs) / 2,
                        Weight.SEMIBOLD,
                        fs,
                        Px.mix(Theme.accent2() | 0xFF000000, 0xFFFFFFFF, v));
                int tw = Px.textWidth(t, Weight.SEMIBOLD, fs);
                Px.rect(
                        g,
                        px,
                        py + (ph + fs) / 2 + s(2),
                        Math.round(tw * v),
                        Math.max(1, s(1)),
                        Theme.accent2() | 0xFF000000);
            } else {
                int is = s(12);
                g.pose().pushMatrix(); // the chevron turned to point back
                g.pose().translate(px + is / 2f - Math.round(s(3) * v), py + ph / 2f);
                g.pose().rotate((float) Math.PI);
                Px.icon(g, "chevron-right", -is / 2, -is / 2, is, Px.mix(0xFF8F889C, 0xFFF4F1F8, v));
                g.pose().popMatrix();
                Px.text(
                        g,
                        t,
                        px + is + s(6),
                        py + (ph - fs) / 2,
                        Weight.SEMIBOLD,
                        fs,
                        Px.mix(0xFF8F889C, 0xFFF4F1F8, v));
            }
        }
    }

    private final class Arrow extends Styled {
        final boolean back;

        Arrow(boolean back, int x, int y, Runnable action) {
            super(back ? "‹" : "›", x, y, s(32), s(32), action);
            this.back = back;
        }

        @Override
        Arrow addTo(AccountManagerScreen screen) {
            super.addTo(screen);
            return this;
        }

        @Override
        void draw(GuiGraphics g, float v) {
            if (v > .02f) Px.circle(g, px + pw / 2, py + ph / 2, pw / 2, Px.a(0xFFFFFF, (int) (18 * v)));
            int is = s(15);
            g.pose().pushMatrix();
            g.pose().translate(px + pw / 2f, py + ph / 2f);
            if (back) g.pose().rotate((float) Math.PI);
            Px.icon(g, "chevron-right", -is / 2, -is / 2, is, Px.mix(0xFF8F889C, 0xFFF4F1F8, v));
            g.pose().popMatrix();
        }
    }
}
