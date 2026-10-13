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

import net.arcanic.visuals.core.ArcanicAccount;
import net.arcanic.visuals.core.ArcanicAccount.State;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.gui.PromptScreen.Kit;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Arcanic Social: the account card over the title screen, drawn like the module settings card. */
public final class SocialScreen extends Screen {
    private static final int DANGER = 0xFFFF5C72;

    private final Screen parent;
    private final Anim anim = new Anim();
    private EditBox key;
    private String draft = "";
    private State shownState;
    private boolean shownPremium;
    private Kit k = new Kit();
    private int cx, cy, cw, ch;
    // offsets from the top of the card; -1 when the part is not shown
    private int head, whoY, keyY, redeemY, noteY, goY, outY;

    public SocialScreen(Screen parent) {
        super(Component.literal("Arcanic Social"));
        this.parent = parent;
        anim.set("open", 0);
        ArcanicAccount.refresh();
    }

    private boolean keyShown() {
        return shownState == State.SIGNED_IN && !shownPremium;
    }

    @Override
    protected void init() {
        if (key != null) draft = key.getValue();
        clearWidgets();
        key = null;
        shownState = ArcanicAccount.state();
        shownPremium = ArcanicAccount.premium();
        k = new Kit();
        cw = k.S(300);
        layout();
        if (!keyShown()) return;
        key = new EditBox(font, 0, 0, 1, 1, Component.literal(Lang.tr("Ключ Premium")));
        key.setMaxLength(24);
        key.setBordered(false);
        key.setValue(draft);
        addWidget(key); // typed natively, drawn by us in the client font
        place();
    }

    /** Header, the account row, the key and its button, the note, then signing in or out; 10 px between parts. */
    private void layout() {
        int gap = k.S(10);
        head = k.S(58);
        int y = head + k.S(12);
        whoY = y;
        y += k.S(44) + k.S(12);
        keyY = redeemY = noteY = goY = outY = -1;
        if (keyShown()) {
            keyY = y;
            y += k.S(32) + gap;
            redeemY = y;
            y += k.S(34) + gap;
        }
        if (!ArcanicAccount.note().isEmpty()) {
            noteY = y;
            y += k.S(16) + gap;
        }
        if (shownState == State.SIGNED_IN) {
            outY = y;
            y += k.S(36);
        } else {
            goY = y;
            y += k.S(34);
        }
        ch = y + k.S(outY >= 0 ? 8 : 18);
        cx = (Px.width() - cw) / 2;
        cy = (Px.height() - ch) / 2;
    }

    /** The native field sits exactly under the one we draw, so clicks focus it. */
    private void place() {
        if (key == null) return;
        key.setX(Kit.logical(cx + k.S(18)));
        key.setY(Kit.logical(cy + keyY));
        key.setWidth(Kit.logical(cw - k.S(36)));
        key.setHeight(Kit.logical(k.S(32)));
    }

    private String goLabel() {
        return switch (shownState) {
            case WAITING -> "Отменить вход";
            case LOADING -> "Загрузка…";
            default -> "Войти через сайт";
        };
    }

    private boolean redeemLive() {
        return key != null && !key.getValue().isBlank() && !ArcanicAccount.redeeming();
    }

    private void go() {
        if (shownState == State.LOADING) return;
        ArcanicAccount.signIn(); // (a second call while waiting cancels the sign-in)
        UiSound.play("click");
    }

    private void redeem() {
        if (!redeemLive()) {
            UiSound.play("pop", .7f, 1);
            return;
        }
        ArcanicAccount.redeem(key.getValue().trim());
        UiSound.play("click");
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = Px.px(e.x()), my = Px.px(e.y());
        if (!Kit.in(mx, my, cx, cy, cw, ch)) {
            onClose();
            return true;
        }
        int x = cx + k.S(18), w = cw - k.S(36);
        if (e.button() == 0) {
            if (goY >= 0 && Kit.in(mx, my, x, cy + goY, w, k.S(34))) {
                go();
                return true;
            }
            if (redeemY >= 0 && Kit.in(mx, my, x, cy + redeemY, w, k.S(34))) {
                redeem();
                return true;
            }
            if (outY >= 0 && Kit.in(mx, my, x, cy + outY, w, k.S(36))) {
                ArcanicAccount.signOut();
                UiSound.play("click");
                return true;
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (key != null) redeem();
            else if (goY >= 0) go();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (ArcanicAccount.state() == State.WAITING) ArcanicAccount.signIn(); // (a second call cancels the sign-in)
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        if (parent != null) parent.render(g, -1, -1, delta);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        if (ArcanicAccount.state() != shownState || ArcanicAccount.premium() != shownPremium) init();
        layout(); // the note comes and goes
        place();
        renderBackground(g, mx, my, delta);
        g.nextStratum();
        anim.frame();
        float open = anim.to("open", 1, 15);
        int px = (int) Px.px(mx), py = (int) Px.px(my);
        int x = cx + k.S(18), w = cw - k.S(36);
        boolean signedIn = shownState == State.SIGNED_IN;
        Px.begin(g);
        float old = Px.fade;
        try {
            Px.fade = open;
            Px.rect(g, 0, 0, Px.width(), Px.height(), Kit.SCRIM);
            int top = cy + Math.round(k.S(8) * (1 - ArcanicMenu.ease(open)));
            k.card(g, cx, top, cw, ch);
            String sub = signedIn
                    ? "Косметика и эмоции других игроков"
                    : shownState == State.WAITING
                            ? "Ждём вход в браузере"
                            : "Войди, чтобы видеть косметику других игроков";
            k.header(g, x, top, w, "Arcanic Social", Lang.tr(sub), head);

            // the account: a round letter, the login and the plan on the right
            int ay = top + whoY, ah = k.S(44), av = k.S(34), avy = ay + (ah - av) / 2;
            int acc = ArcanicMenu.acc();
            Px.round(g, x, avy, av, av, av / 2, Px.a(acc, Kit.light() ? 34 : 52));
            String name = ArcanicAccount.name();
            if (signedIn && !name.isEmpty())
                Px.textCenter(
                        g,
                        name.substring(0, 1).toUpperCase(),
                        x + av / 2,
                        avy + (av - k.S(15)) / 2,
                        Weight.SEMIBOLD,
                        k.S(15),
                        ArcanicMenu.accText());
            else
                ArcanicMenu.glyph(
                        g, "user", x + (av - k.S(16)) / 2, avy + (av - k.S(16)) / 2, k.S(16), ArcanicMenu.accText());
            int tx = x + av + k.S(12), textW = x + w - tx;
            if (signedIn) {
                String plan = ArcanicAccount.planName().toUpperCase();
                int pw = Px.textWidth(plan, Weight.SEMIBOLD, k.S(11)) + k.S(16), ph = k.S(22);
                int ppx = x + w - pw, ppy = ay + (ah - ph) / 2;
                Px.round(
                        g,
                        ppx,
                        ppy,
                        pw,
                        ph,
                        k.S(5),
                        shownPremium ? Px.a(Theme.accent(), Kit.light() ? 26 : 40) : Kit.inset(false));
                Px.outline(
                        g,
                        ppx,
                        ppy,
                        pw,
                        ph,
                        k.S(5),
                        shownPremium ? acc : Px.a(Kit.T().text(), Kit.light() ? 30 : 16));
                Px.textCenter(
                        g,
                        plan,
                        ppx + pw / 2,
                        ppy + (ph - k.S(11)) / 2,
                        Weight.SEMIBOLD,
                        k.S(11),
                        shownPremium ? ArcanicMenu.accText() : Kit.T().text());
                textW -= pw + k.S(10);
            }
            String who =
                    signedIn ? name : Lang.tr(shownState == State.WAITING ? "Ждём вход в браузере" : "Не подключён");
            Px.text(
                    g,
                    Px.ellipsize(who, Weight.SEMIBOLD, k.S(14), textW),
                    tx,
                    ay + k.S(5),
                    Weight.SEMIBOLD,
                    k.S(14),
                    Kit.T().text());
            Px.text(
                    g,
                    Px.ellipsize(Lang.tr("Аккаунт Arcanic"), Weight.MEDIUM, k.S(11), textW),
                    tx,
                    ay + k.S(26),
                    Weight.MEDIUM,
                    k.S(11),
                    Kit.dim());

            if (key != null) {
                k.field(
                        g,
                        key,
                        x,
                        top + keyY,
                        w,
                        "XXXX-XXXX-XXXX-XXXX",
                        "key",
                        Kit.in(px, py, x, cy + keyY, w, k.S(32)),
                        anim,
                        "key");
                k.button(
                        g,
                        Lang.tr(ArcanicAccount.redeeming() ? "Проверяем ключ…" : "Активировать ключ"),
                        x,
                        top + redeemY,
                        w,
                        redeemLive(),
                        Kit.in(px, py, x, cy + redeemY, w, k.S(34)),
                        anim,
                        "redeem");
            }

            if (noteY >= 0)
                Px.text(
                        g,
                        Px.ellipsize(Lang.tr(ArcanicAccount.note()), Weight.MEDIUM, k.S(12), w),
                        x,
                        top + noteY + k.S(2),
                        Weight.MEDIUM,
                        k.S(12),
                        ArcanicAccount.noteBad() ? DANGER : ArcanicMenu.accText());

            if (goY >= 0)
                k.button(
                        g,
                        Lang.tr(goLabel()),
                        x,
                        top + goY,
                        w,
                        shownState != State.LOADING,
                        Kit.in(px, py, x, cy + goY, w, k.S(34)),
                        anim,
                        "go");

            // signing out sits under a hairline, like the key row at the bottom of a module card
            if (outY >= 0) {
                int oy = top + outY, oh = k.S(36);
                Px.rect(g, x, oy, w, Math.max(1, k.S(1)), Kit.hair());
                float hk = anim.to("out", Kit.in(px, py, x, cy + outY, w, oh) ? 1 : 0, 20);
                Px.text(
                        g,
                        Lang.tr("Выйти из аккаунта"),
                        x,
                        oy + (oh - k.S(13)) / 2,
                        Weight.MEDIUM,
                        k.S(13),
                        Px.mix(Kit.T().muted(), DANGER, hk));
            }
        } finally {
            Px.fade = old;
            Px.end(g);
        }
    }
}
