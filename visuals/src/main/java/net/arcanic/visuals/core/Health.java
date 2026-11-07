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
package net.arcanic.visuals.core;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

public final class Health {
    private Health() {}

    public static Reading of(LivingEntity e) {
        float hp = e.getHealth();
        float abs = Math.max(0.0F, e.getAbsorptionAmount());
        Integer integer;
        if (e instanceof Player p) {
            integer = belowName(p);
        } else {
            integer = null;
        }

        Integer score = integer;
        if (score != null) {
            hp = (float) score;
        }

        if (!Float.isFinite(hp)) {
            hp = 0.0F;
        }

        boolean known = (hp > 0.0F || !e.isAlive()) && hp < 1000.0F;
        float max = Math.max(1.0F, Math.max(e.getMaxHealth(), hp));
        return new Reading(Math.max(0.0F, hp), max, abs, known);
    }

    private static Integer belowName(Player p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        } else {
            Scoreboard board = level.getScoreboard();
            Objective objective = board.getDisplayObjective(DisplaySlot.BELOW_NAME);
            if (objective == null) {
                return null;
            } else {
                ReadOnlyScoreInfo score = board.getPlayerScoreInfo(p, objective);
                return score == null ? null : score.value();
            }
        }
    }

    public static record Reading(float hp, float max, float absorption, boolean known) {
        public float total() {
            return this.hp + this.absorption;
        }

        public float fraction() {
            return this.known ? Math.min(1.0F, this.hp / this.max) : 1.0F;
        }

        public String text() {
            if (!this.known) {
                return "?";
            } else {
                float t = this.total();
                return !(t >= 10.0F) && !(Math.abs(t - (float) Math.round(t)) < 0.05F)
                        ? String.format(Locale.ROOT, "%.1f", t)
                        : String.valueOf(Math.round(t));
            }
        }
    }
}
