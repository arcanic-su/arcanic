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

import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Social;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.gui.PromptScreen.Kit;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A friend's inventory, asked over Arcanic Social: armour and the off hand on top, then the bag and the hotbar. */
public final class FriendInventoryScreen extends Screen {
    private static final long WAIT = 10000L;
    private static final int[] TOP = {39, 38, 37, 36, -1, 40};

    private final Screen parent;
    private final Social.Friend friend;
    private final Anim anim = new Anim();
    private long askedAt = System.currentTimeMillis();
    private Kit k = new Kit();
    private int cx;
    private int cy;
    private int cw;
    private int ch;
    private int refreshY;

    public FriendInventoryScreen(Screen parent, Social.Friend friend) {
        super(Component.literal(friend.name()));
        this.parent = parent;
        this.friend = friend;
        this.anim.set("open", 0.0F);
    }

    @Override
    protected void init() {
        this.k = new Kit();
    }

    private int cell() {
        return this.k.S(40.0);
    }

    private void layout() {
        int cell = this.cell();
        int pad = this.k.S(18.0);
        this.cw = pad * 2 + cell * 9;
        int head = this.k.S(58.0);
        // header, the top row, a gap, three rows of the bag, a gap, the hotbar, then the refresh button
        this.refreshY = head + this.k.S(8.0) + cell + this.k.S(12.0) + cell * 3 + this.k.S(8.0) + cell + this.k.S(14.0);
        this.ch = this.refreshY + this.k.S(34.0) + this.k.S(18.0);
        this.cx = (Px.width() - this.cw) / 2;
        this.cy = (Px.height() - this.ch) / 2;
    }

    private Social.InventoryView view() {
        Social.InventoryView v = Social.shown(this.friend.account());
        return v != null && v.at() >= this.askedAt ? v : null;
    }

    private void refresh() {
        this.askedAt = System.currentTimeMillis();
        Social.askInventory(this.friend);
        UiSound.play("click");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        if (this.parent != null) this.parent.render(g, -1, -1, delta);
        else if (Minecraft.getInstance().level != null) this.renderBlurredBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.layout();
        float open = this.anim.to("open", 1.0F, 15.0F);
        int px = (int) Px.px(mouseX);
        int py = (int) Px.px(mouseY);
        Social.InventoryView v = this.view();
        long waited = System.currentTimeMillis() - this.askedAt;
        ItemStack hovered = ItemStack.EMPTY;
        Px.begin(g);
        float old = Px.fade;
        try {
            Px.fade = open;
            Px.rect(g, 0, 0, Px.width(), Px.height(), Kit.SCRIM);
            int top = this.cy + Math.round(this.k.S(8.0) * (1.0F - ArcanicMenu.ease(open)));
            this.k.card(g, this.cx, top, this.cw, this.ch);
            int pad = this.k.S(18.0);
            int x = this.cx + pad;
            int face = Math.max(8, this.k.S(30.0) / 8 * 8);
            PlayerFaceRenderer.draw(
                    g,
                    Social.skin(this.friend.name(), this.friend.uuid()),
                    x,
                    top + this.k.S(16.0),
                    face,
                    Math.round(255.0F * Px.fade) << 24 | 0xFFFFFF);
            String sub = v == null
                    ? Lang.tr(waited > WAIT ? "Друг не ответил: он не в игре?" : "Ждём ответ друга…")
                    : Lang.tr(v.denied() ? "Друг закрыл свой инвентарь" : "Инвентарь друга сейчас");
            int tx = x + face + this.k.S(12.0);
            this.k.header(
                    g, tx, top - this.k.S(2.0), this.cw - pad * 2 - face - this.k.S(50.0), this.friend.name(), sub, 0);
            this.k.close(g, this.cx + this.cw - this.k.S(46.0), top + this.k.S(18.0), px, py, this.anim);

            int cell = this.cell();
            int y = top + this.k.S(66.0);
            for (int i = 0; i < TOP.length; ++i) {
                if (TOP[i] < 0) continue;
                ItemStack st = this.slot(g, v, TOP[i], x + i * cell, y, px, py);
                if (!st.isEmpty()) hovered = st;
            }
            y += cell + this.k.S(12.0);
            for (int row = 0; row < 3; ++row) {
                for (int col = 0; col < 9; ++col) {
                    ItemStack st = this.slot(g, v, 9 + row * 9 + col, x + col * cell, y + row * cell, px, py);
                    if (!st.isEmpty()) hovered = st;
                }
            }
            y += cell * 3 + this.k.S(8.0);
            for (int col = 0; col < 9; ++col) {
                ItemStack st = this.slot(g, v, col, x + col * cell, y, px, py);
                if (!st.isEmpty()) hovered = st;
            }

            int bw = this.cw - pad * 2;
            this.k.secondary(
                    g,
                    Lang.tr("Обновить"),
                    x,
                    top + this.refreshY,
                    bw,
                    Kit.in(px, py, x, this.cy + this.refreshY, bw, this.k.S(34.0)),
                    this.anim,
                    "refresh");
        } finally {
            Px.fade = old;
            Px.end(g);
        }
        if (!hovered.isEmpty()) g.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
    }

    /** One slot; the stack in it when the mouse is over it, else empty. */
    private ItemStack slot(GuiGraphics g, Social.InventoryView v, int index, int x, int y, int px, int py) {
        int cell = this.cell();
        int gap = this.k.S(3.0);
        boolean hv = Kit.in(px, py, x, y, cell, cell);
        Px.round(g, x + gap / 2, y + gap / 2, cell - gap, cell - gap, this.k.S(7.0), Kit.inset(hv));
        ItemStack st = v == null || v.items().size() <= index
                ? ItemStack.EMPTY
                : v.items().get(index);
        if (st.isEmpty()) return ItemStack.EMPTY;
        int is = this.k.S(26.0);
        int ix = x + (cell - is) / 2;
        int iy = y + (cell - is) / 2;
        g.pose().pushMatrix();
        g.pose().translate((float) ix, (float) iy);
        g.pose().scale((float) is / 16.0F, (float) is / 16.0F);
        g.renderItem(st, 0, 0);
        g.renderItemDecorations(this.font, st, 0, 0);
        g.pose().popMatrix();
        return hv ? st : ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = Px.px(e.x());
        double my = Px.px(e.y());
        if (!Kit.in(mx, my, this.cx, this.cy, this.cw, this.ch)
                || Kit.in(
                        mx,
                        my,
                        this.cx + this.cw - this.k.S(46.0),
                        this.cy + this.k.S(18.0),
                        this.k.S(28.0),
                        this.k.S(28.0))) {
            this.onClose();
            return true;
        }
        int pad = this.k.S(18.0);
        if (Kit.in(mx, my, this.cx + pad, this.cy + this.refreshY, this.cw - pad * 2, this.k.S(34.0))) {
            this.refresh();
            return true;
        }
        return super.mouseClicked(e, dbl);
    }
}
