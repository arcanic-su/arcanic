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
package net.arcanic.visuals.fx.impact;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.CombatShow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImpactEffects {
    private static final Logger LOG = LoggerFactory.getLogger("arcanicvisuals");
    private static ClientLevel level;

    private ImpactEffects() {}

    public static List<ImpactEffect> all() {
        return List.of(Modules.PROJECTILE_HIT);
    }

    public static void attack(Entity target) {
        for (ImpactEffect e : all()) {
            if (e.enabled()) {
                try {
                    e.attack(target);
                } catch (RuntimeException ex) {
                    LOG.debug("Impact effect attack failed", ex);
                }
            }
        }

        try {
            if (Modules.MACE.enabled()) {
                Modules.MACE.attack(target);
            }
        } catch (RuntimeException ex) {
            LOG.debug("Combat effect failed", ex);
        }
    }

    public static void world(PoseStack ps, float partial) {
        ScreenFx.reset();
        ClientLevel now = Minecraft.getInstance().level;
        if (now != level) {
            level = now;

            for (ImpactEffect e : all()) {
                e.left();
            }
        }

        if (now != null) {
            for (ImpactEffect e : all()) {
                if (e.enabled()) {
                    try {
                        e.world(ps, partial);
                    } catch (RuntimeException ex) {
                        LOG.debug("Impact effect render failed", ex);
                    }
                }
            }

            if (Modules.TOTEM.enabled()) {
                try {
                    Modules.TOTEM.world(ps, partial);
                } catch (RuntimeException ex) {
                    LOG.debug("Totem effect render failed", ex);
                }
            }

            if (Modules.POTION_FX.enabled()) {
                try {
                    Modules.POTION_FX.world(ps, partial);
                } catch (RuntimeException ex) {
                    LOG.debug("Potion effect render failed", ex);
                }
            }

            if (Modules.MACE.enabled()) {
                try {
                    Modules.MACE.world(ps, partial);
                } catch (RuntimeException ex) {
                    LOG.debug("Mace effect render failed", ex);
                }
            }

            try {
                CombatShow.world(ps);
            } catch (RuntimeException ex) {
                LOG.debug("Hit or kill effect render failed", ex);
            }
        }
    }

    public static void frameDrawn() {
        ScreenFx.apply();
    }

    public static void inStudio(Runnable r) {
        boolean was = ImpactDraw.studio;
        ImpactDraw.studio = true;

        try {
            r.run();
        } finally {
            ImpactDraw.studio = was;
        }
    }
}
