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
package net.arcanic.visuals.fx;

import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.helper.HolyFeatures;
import net.arcanic.visuals.helper.HolyRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/**
 * "Click Pearl": its key (bound in the menu, on this module) throws an ender pearl from the hotbar - a second after the
 * press the pearl is taken in hand, half a second later it is thrown where you look. Without a pearl in the hotbar a
 * red note says so. Locked on HolyWorld, whose rules (2.4) forbid macros.
 */
public final class ClickPearl extends Module {
    private final Setting.Bool back = add(new Setting.Bool("back", "Вернуть прежний слот", true));

    private long pressedAt, noteUntil;
    private int pearl = -1, before = -1, stage;

    public ClickPearl() {
        super(
                "click_pearl",
                "Click Pearl",
                "По бинду кидает эндер-жемчуг из хотбара: через 1 с берёт его в руку, ещё через 0,5 с кидает",
                Category.COMBAT,
                true);
    }

    /** Its key was pressed. */
    public void press() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !enabled()) return;
        if (HolyFeatures.forbidden(this)) {
            HolyFeatures.deny(this);
            return;
        }
        if (stage != 0) return; // one throw at a time
        int at = find(mc);
        if (at < 0) {
            noteUntil = System.currentTimeMillis() + 2000;
            return;
        }
        pearl = at;
        pressedAt = System.currentTimeMillis();
        stage = 1;
    }

    private static int find(Minecraft mc) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) if (inv.getItem(i).is(Items.ENDER_PEARL)) return i;
        return -1;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (stage == 0) return;
        if (mc.player == null || mc.gameMode == null) {
            stage = 0;
            return;
        }
        var inv = mc.player.getInventory();
        long t = System.currentTimeMillis() - pressedAt;
        if (stage == 1 && t >= 1000) {
            if (!inv.getItem(pearl).is(Items.ENDER_PEARL)) pearl = find(mc); // moved meanwhile
            if (pearl < 0) {
                noteUntil = System.currentTimeMillis() + 2000;
                stage = 0;
                return;
            }
            before = inv.getSelectedSlot();
            inv.setSelectedSlot(pearl);
            stage = 2;
        } else if (stage == 2 && t >= 1500) {
            if (inv.getSelectedSlot() == pearl && inv.getItem(pearl).is(Items.ENDER_PEARL) && mc.screen == null) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            stage = back.on() && before >= 0 && before != pearl ? 3 : 0;
        } else if (stage == 3 && t >= 1600) {
            if (inv.getSelectedSlot() == pearl) inv.setSelectedSlot(before);
            stage = 0;
        }
    }

    @Override
    protected void onDisable() {
        stage = 0;
    }

    public void hud(GuiGraphics g) {
        if (System.currentTimeMillis() < noteUntil) HolyRules.note(g, Lang.tr("Жемчуг эндера не в хотбаре!"));
    }
}
