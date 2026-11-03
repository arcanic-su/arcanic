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
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.helper.Pings;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Every ping at once, opened by holding the mouse wheel: move to a pill and let the wheel go. The world stays sharp
 * behind it, a fight goes on meanwhile.
 */
public final class PingMenu extends PillMenu {
    private final Pings.Aim aim;

    public PingMenu(Pings.Aim aim) {
        this.aim = aim;
    }

    @Override
    String title() {
        return Lang.tr("Пинг друзьям");
    }

    @Override
    String subtitle() {
        return this.aim.what();
    }

    @Override
    boolean blur() {
        return false;
    }

    @Override
    void build(List<Pill> into) {
        for (Pings.Kind k : Pings.Kind.values()) {
            into.add(new Pill(k.icon, k.colour, k.menuLabel, () -> Pings.send(k, this.aim), false, false));
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (e.button() == 2 && !this.closing()) {
            // the wheel comes up over a pill: that ping; anywhere else: none
            int i = this.at(Px.px(e.x()), Px.px(e.y()));
            if (i >= 0) this.pick(i);
            else this.close();
            return true;
        }
        return super.mouseReleased(e);
    }
}
