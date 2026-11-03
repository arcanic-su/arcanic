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
package net.arcanic.visuals.hud;

import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** The game's own interface eased: the hotbar frame slides, chat lines rise, the tab list opens, the camera pulls back. */
public final class UiAnimations extends Module {
    private final Setting.Bool hotbar = this.add(new Setting.Bool("hotbar", "Хотбар", true));
    private final Setting.Num hotbarMs = this.add(
                    new Setting.Num("hotbar_ms", "Длительность хотбара", 100.0, 50.0, 500.0, 10.0, " мс"))
            .showIf(this.hotbar::on);
    private final Setting.Bool chat = this.add(new Setting.Bool("chat", "Чат", true));
    private final Setting.Num chatMs = this.add(
                    new Setting.Num("chat_ms", "Длительность чата", 200.0, 50.0, 500.0, 10.0, " мс"))
            .showIf(this.chat::on);
    private final Setting.Bool tab = this.add(new Setting.Bool("tab", "Таб", true));
    private final Setting.Num tabMs = this.add(
                    new Setting.Num("tab_ms", "Длительность таба", 200.0, 50.0, 500.0, 10.0, " мс"))
            .showIf(this.tab::on);
    private final Setting.Bool view = this.add(new Setting.Bool("perspective", "Смена перспективы", true));
    private final Setting.Num viewMs = this.add(
                    new Setting.Num("perspective_ms", "Длительность перспективы", 300.0, 50.0, 1000.0, 10.0, " мс"))
            .showIf(this.view::on);

    private int slot = -1;
    private float slotFrom;
    private float slotTo;
    private long slotAt;
    private long chatAt;
    private boolean tabOpen;
    private long tabAt;
    private boolean detached;
    private long viewAt;

    public UiAnimations() {
        super(
                "ui_animations",
                "Анимации интерфейса",
                "Плавный хотбар, чат, таб и смена перспективы",
                Category.HUD,
                false);
    }

    private static float ease(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (float) Math.pow(1.0F - t, 3.0);
    }

    private static float progress(long since, Setting.Num ms) {
        return ease((System.currentTimeMillis() - since) / (float) ms.get().doubleValue());
    }

    /** Where the selection frame is drawn, sliding to the slot picked. */
    public int hotbarX(int x) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.enabled() || !this.hotbar.on() || mc.player == null) {
            this.slot = -1;
            return x;
        }
        int now = mc.player.getInventory().getSelectedSlot();
        if (this.slot == -1) {
            this.slot = now;
            this.slotFrom = this.slotTo = x;
        } else if (now != this.slot) {
            this.slotFrom = this.current();
            this.slotTo = x;
            this.slotAt = System.currentTimeMillis();
            this.slot = now;
        } else {
            this.slotTo = x;
        }
        return Math.round(this.current());
    }

    private float current() {
        float t = this.slotAt == 0L ? 1.0F : progress(this.slotAt, this.hotbarMs);
        return this.slotFrom + (this.slotTo - this.slotFrom) * t;
    }

    public void messageAdded() {
        this.chatAt = System.currentTimeMillis();
    }

    /** How far the chat still sits below its place after a new line, in gui pixels. */
    public float chatOffset() {
        if (!this.enabled() || !this.chat.on() || this.chatAt == 0L) return 0.0F;
        Minecraft mc = Minecraft.getInstance();
        float line = (float) (9.0
                * mc.options.chatScale().get()
                * (mc.options.chatLineSpacing().get() + 1.0));
        return line * (1.0F - progress(this.chatAt, this.chatMs));
    }

    /** Wraps the tab list: it grows and fades in from its top when the key goes down. */
    public void tab(GuiGraphics g, Runnable draw) {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.options.keyPlayerList.isDown();
        if (down && !this.tabOpen) this.tabAt = System.currentTimeMillis();
        this.tabOpen = down;
        if (!this.enabled() || !this.tab.on() || !down) {
            draw.run();
            return;
        }
        float t = progress(this.tabAt, this.tabMs);
        if (t >= 1.0F) {
            draw.run();
            return;
        }
        float cx = g.guiWidth() / 2.0F;
        float s = 0.92F + 0.08F * t;
        g.pose().pushMatrix();
        g.pose().translate(cx, 10.0F * (1.0F - t));
        g.pose().scale(s, s);
        g.pose().translate(-cx, 0.0F);
        draw.run();
        g.pose().popMatrix();
    }

    /** The third-person camera's distance: it eases out from the head instead of jumping back. */
    public float cameraDistance(float distance) {
        Minecraft mc = Minecraft.getInstance();
        boolean out = mc.options.getCameraType() != CameraType.FIRST_PERSON;
        if (out && !this.detached) this.viewAt = System.currentTimeMillis();
        this.detached = out;
        if (!this.enabled() || !this.view.on() || !out) return distance;
        float t = progress(this.viewAt, this.viewMs);
        return distance * (0.25F + 0.75F * t);
    }
}
