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
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

public final class AutoSprint extends Module {
    private final Setting.Bool any = this.add(new Setting.Bool("any", "В любую сторону", false));

    public AutoSprint() {
        super("auto_sprint", "Автоспринт", "Бег включается сам, когда идёшь вперёд", Category.WORLD, false);
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && mc.screen == null) {
            Options o = mc.options;
            boolean going = o.keyUp.isDown()
                    || this.any.on() && (o.keyLeft.isDown() || o.keyRight.isDown() || o.keyDown.isDown());
            if (going && !p.isShiftKeyDown() && !p.isUsingItem() && !p.horizontalCollision) {
                if (p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly) {
                    if (!p.isSprinting()) {
                        p.setSprinting(true);
                    }
                }
            }
        }
    }
}
