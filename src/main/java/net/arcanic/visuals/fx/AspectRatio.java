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

public final class AspectRatio extends Module {
    public final Setting.Mode ratio = this.add(
            new Setting.Mode("ratio", "Соотношение", "4:3", new String[] {"4:3", "5:4", "16:10", "1:1", "Своё"}));
    public final Setting.Num custom = (this.add(
                    new Setting.Num("custom", "Своё соотношение", 1.33, 0.5, 2.5, 0.01, "")))
            .showIf(() -> this.ratio.is("Своё"));
    public final Setting.Bool hand = this.add(new Setting.Bool("hand", "Растягивать руку", true));
    private float cur = -1.0F;
    private boolean settled = true;
    private long last;

    public AspectRatio() {
        super(
                "aspectratio",
                "Aspect Ratio",
                "Растянутая картинка, как на растянутом разрешении: мир рисуется под другое соотношение сторон",
                Category.WORLD,
                false);
    }

    private float wanted() {
        float var10000;
        switch ((String) this.ratio.get()) {
            case "4:3" -> var10000 = 1.3333334F;
            case "5:4" -> var10000 = 1.25F;
            case "16:10" -> var10000 = 1.6F;
            case "1:1" -> var10000 = 1.0F;
            default -> var10000 = this.custom.f();
        }

        return var10000;
    }

    public float aspect(float window) {
        long t = System.nanoTime();
        float dt = this.last == 0L ? 0.0F : Math.min(0.1F, (float) (t - this.last) / 1.0E9F);
        this.last = t;
        if (!this.enabled() && this.settled) {
            this.cur = window;
            return window;
        } else {
            float want = this.enabled() ? this.wanted() : window;
            if (this.cur < 0.0F) {
                this.cur = window;
            }

            this.settled = false;
            this.cur += (want - this.cur) * (1.0F - (float) Math.exp((double) (-dt * 9.0F)));
            if (Math.abs(want - this.cur) < 0.001F) {
                this.cur = want;
                if (!this.enabled()) {
                    this.settled = true;
                }
            }

            return this.cur;
        }
    }

    public float now(float window) {
        return !this.settled && !(this.cur < 0.0F) ? this.cur : window;
    }
}
