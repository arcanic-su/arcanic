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
import net.arcanic.visuals.core.Keys;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.helper.CommandBinds;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class CommandBindsScreen extends Screen {
    private static final int MAX = 10;
    private final Screen parent;
    private final Anim anim = new Anim();
    private PromptScreen.Kit k = new PromptScreen.Kit();
    private int capturing = -1;
    private int cx;
    private int cy;
    private int cw;
    private int ch;
    private int rowsY;

    public CommandBindsScreen(Screen parent) {
        super(Component.literal(Lang.tr("Бинды команд")));
        this.parent = parent;
        this.anim.set("open", 0.0F);
    }

    private List<CommandBinds.Bind> binds() {
        return Modules.COMMAND_BINDS.list();
    }

    protected void init() {
        this.k = new PromptScreen.Kit();
        this.cw = this.k.S(380.0);
        int n = Math.max(1, this.binds().size());
        this.rowsY = this.k.S(58.0);
        this.ch = this.rowsY + n * this.k.S(38.0) + this.k.S(10.0) + this.k.S(34.0) + this.k.S(18.0);
        this.cx = (Px.width() - this.cw) / 2;
        this.cy = (Px.height() - this.ch) / 2;
    }

    private int[] keyPill(int i) {
        int x = this.cx + this.k.S(18.0);
        int y = this.cy + this.rowsY + i * this.k.S(38.0);
        return new int[] {x, y + this.k.S(3.0), this.k.S(70.0), this.k.S(30.0)};
    }

    private int[] commandBox(int i) {
        int x = this.cx + this.k.S(18.0) + this.k.S(78.0);
        int y = this.cy + this.rowsY + i * this.k.S(38.0);
        return new int[] {x, y + this.k.S(3.0), this.cw - this.k.S(36.0) - this.k.S(78.0), this.k.S(30.0)};
    }

    private int[] addButton() {
        return new int[] {
            this.cx + this.k.S(18.0),
            this.cy + this.ch - this.k.S(18.0) - this.k.S(34.0),
            this.cw - this.k.S(36.0),
            this.k.S(34.0)
        };
    }

    private void changed() {
        Modules.COMMAND_BINDS.save();
        this.init();
    }

    public boolean keyPressed(KeyEvent event) {
        if (this.capturing < 0) {
            if (event.key() == 256) {
                this.onClose();
                return true;
            } else {
                return super.keyPressed(event);
            }
        } else {
            if (event.key() != 256 && this.capturing < this.binds().size()) {
                ((CommandBinds.Bind) this.binds().get(this.capturing)).key = event.key();
                UiSound.play("select");
            } else if (this.capturing < this.binds().size()
                    && ((CommandBinds.Bind) this.binds().get(this.capturing)).key <= 0) {
                this.binds().remove(this.capturing);
            }

            this.capturing = -1;
            this.changed();
            return true;
        }
    }

    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = Px.px(e.x());
        double my = Px.px(e.y());
        if (this.capturing >= 0) {
            return true;
        } else {
            List<CommandBinds.Bind> list = this.binds();

            for (int i = 0; i < list.size(); i++) {
                int[] kp = this.keyPill(i);
                int[] cb = this.commandBox(i);
                if (PromptScreen.Kit.in(mx, my, kp[0], kp[1], kp[2], kp[3])) {
                    this.capturing = i;
                    UiSound.play("pop");
                    return true;
                }

                if (PromptScreen.Kit.in(mx, my, cb[0], cb[1], cb[2], cb[3])) {
                    CommandBinds.Bind b = (CommandBinds.Bind) list.get(i);
                    UiSound.play("pop");
                    Minecraft minecraft2 = Minecraft.getInstance();
                    PromptScreen promptScreen = PromptScreen.field(
                                    this, "Бинд", "/команда или текст", "Сохранить", (v) -> {
                                        b.command = v;
                                        this.changed();
                                    })
                            .value(b.command)
                            .limit(200);
                    minecraft2.setScreen(promptScreen
                            .note(Lang.tr("Клавиша") + ": " + Keys.label(b.key))
                            .danger("Удалить", () -> {
                                list.remove(b);
                                this.changed();
                            }));
                    return true;
                }
            }

            int[] ab = this.addButton();
            if (PromptScreen.Kit.in(mx, my, ab[0], ab[1], ab[2], ab[3]) && list.size() < 10) {
                UiSound.play("click");
                Minecraft.getInstance()
                        .setScreen(PromptScreen.field(this, "Новый бинд", "/команда или текст", "Дальше", (v) -> {
                                    list.add(new CommandBinds.Bind(-1, v));
                                    this.capturing = list.size() - 1;
                                    this.changed();
                                })
                                .limit(200));
                return true;
            } else {
                if (!PromptScreen.Kit.in(mx, my, this.cx, this.cy, this.cw, this.ch)) {
                    this.onClose();
                }

                return true;
            }
        }
    }

    public void onClose() {
        Modules.COMMAND_BINDS.save();
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

    public void render(GuiGraphics g, int mx, int my, float delta) {
        this.renderBackground(g, mx, my, delta);
        g.nextStratum();
        this.anim.frame();
        float open = this.anim.to("open", 1.0F, 15.0F);
        int pmx = (int) Px.px((double) mx);
        int pmy = (int) Px.px((double) my);
        Px.begin(g);
        float old = Px.fade;

        try {
            Px.fade = open;
            Px.rect(g, 0, 0, Px.width(), Px.height(), 0x99000000);
            this.k.card(g, this.cx, this.cy, this.cw, this.ch);
            int pad = this.k.S(18.0);
            this.k.header(
                    g,
                    this.cx + pad,
                    this.cy,
                    this.cw - pad * 2,
                    this.getTitle().getString(),
                    Lang.tr("Клавиша — команда или сообщение в чат"),
                    this.k.S(48.0));
            List<CommandBinds.Bind> list = this.binds();
            if (list.isEmpty()) {
                Px.text(
                        g,
                        Lang.tr("Пока ни одного: добавь первый"),
                        this.cx + pad,
                        this.cy + this.rowsY + this.k.S(10.0),
                        Px.Weight.MEDIUM,
                        this.k.S(12.0),
                        PromptScreen.Kit.dim());
            }

            for (int i = 0; i < list.size(); i++) {
                CommandBinds.Bind b = (CommandBinds.Bind) list.get(i);
                int[] kp = this.keyPill(i);
                int[] cb = this.commandBox(i);
                boolean kh = PromptScreen.Kit.in((double) pmx, (double) pmy, kp[0], kp[1], kp[2], kp[3]);
                boolean cbh = PromptScreen.Kit.in((double) pmx, (double) pmy, cb[0], cb[1], cb[2], cb[3]);
                boolean wait = this.capturing == i;
                float pulse = wait ? 0.5F + 0.5F * (float) Math.sin((double) System.currentTimeMillis() / 160.0) : 0.0F;
                Px.round(
                        g,
                        kp[0],
                        kp[1],
                        kp[2],
                        kp[3],
                        this.k.S(7.0),
                        wait
                                ? Px.mix(PromptScreen.Kit.inset(true), ArcanicMenu.acc(), 0.35F + 0.3F * pulse)
                                : PromptScreen.Kit.inset(kh));
                Px.outline(
                        g,
                        kp[0],
                        kp[1],
                        kp[2],
                        kp[3],
                        this.k.S(7.0),
                        !wait && !kh ? PromptScreen.Kit.hair() : ArcanicMenu.acc());
                String key = wait ? "…" : Keys.label(b.key);
                Px.textCenter(
                        g,
                        key,
                        kp[0] + kp[2] / 2,
                        kp[1] + (kp[3] - this.k.S(13.0)) / 2,
                        Px.Weight.SEMIBOLD,
                        this.k.S(13.0),
                        PromptScreen.Kit.T().text());
                Px.round(g, cb[0], cb[1], cb[2], cb[3], this.k.S(7.0), PromptScreen.Kit.inset(cbh));
                String cmd = wait ? Lang.tr("Нажми клавишу… (Esc — отмена)") : b.command;
                Px.text(
                        g,
                        Px.ellipsize(cmd, Px.Weight.MEDIUM, this.k.S(12.0), cb[2] - this.k.S(34.0)),
                        cb[0] + this.k.S(11.0),
                        cb[1] + (cb[3] - this.k.S(12.0)) / 2,
                        Px.Weight.MEDIUM,
                        this.k.S(12.0),
                        wait ? ArcanicMenu.accText() : PromptScreen.Kit.T().text());
                ArcanicMenu.glyph(
                        g,
                        "pen",
                        cb[0] + cb[2] - this.k.S(22.0),
                        cb[1] + (cb[3] - this.k.S(12.0)) / 2,
                        this.k.S(12.0),
                        cbh ? ArcanicMenu.accText() : PromptScreen.Kit.dim());
            }

            int[] ab = this.addButton();
            this.k.button(
                    g,
                    Lang.tr(list.size() < 10 ? "Добавить бинд" : "Не больше десяти"),
                    ab[0],
                    ab[1],
                    ab[2],
                    list.size() < 10 && this.capturing < 0,
                    PromptScreen.Kit.in((double) pmx, (double) pmy, ab[0], ab[1], ab[2], ab[3]),
                    this.anim,
                    "add");
        } finally {
            Px.fade = old;
            Px.end(g);
        }
    }
}
