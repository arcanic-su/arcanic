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
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;

public final class CustomFog extends Module {
    public final Setting.Bool customRange = this.add(new Setting.Bool("range", "Своя дистанция", true));
    public final Setting.Num fogEnd;
    public final Setting.Colour colorTumana;
    public final Setting.Bool neTrogatPodVodoy;

    public CustomFog() {
        super("custom_fog", "CustomFog", "Свой цвет и дальность тумана", Category.WORLD, false);
        Setting.Num var10001 = this.add(new Setting.Num("end", "Конец тумана", 128.0, 1.0, 1024.0, 1.0, ""));
        Setting.Bool var10002 = this.customRange;
        this.fogEnd = (Setting.Num) var10001.showIf(var10002::on);
        this.colorTumana = this.add(new Setting.Colour("color", "Цвет тумана", false, -7358209));
        this.neTrogatPodVodoy = this.add(new Setting.Bool("submerged", "Не трогать под водой", true));
    }

    public boolean submerged() {
        if (!this.neTrogatPodVodoy.on()) {
            return false;
        } else {
            FogType medium =
                    Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera();
            return medium == FogType.WATER || medium == FogType.LAVA || medium == FogType.POWDER_SNOW;
        }
    }

    public boolean active() {
        return this.enabled() && !this.submerged();
    }

    public boolean hidesSky() {
        return this.enabled() && this.customRange.on() && this.fogEnd.f() < 32.0F;
    }

    public void tint(Vector4f colour) {
        int c = this.colorTumana.argb();
        if (c >>> 24 != 0) {
            float alpha = (float) (c >>> 24) / 255.0F;
            colour.x += ((float) (c >> 16 & 255) / 255.0F - colour.x) * alpha;
            colour.y += ((float) (c >> 8 & 255) / 255.0F - colour.y) * alpha;
            colour.z += ((float) (c & 255) / 255.0F - colour.z) * alpha;
        }
    }
}
