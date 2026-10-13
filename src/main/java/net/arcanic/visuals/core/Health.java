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

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;

/**
 * Health as the server reports it.
 *
 * Many PvP servers hide other players' health and put the real number on the scoreboard under the nick, so that score
 * is preferred. A value that cannot be real is reported as unknown.
 */
public final class Health {
    private Health() {}

    public record Reading(float hp, float max, float absorption, boolean known) {
        public float total() {
            return hp + absorption;
        }

        public float fraction() {
            return known ? Math.min(1, hp / max) : 1;
        }

        public String text() {
            if (!known) return "?";
            float t = total();
            return t >= 10 || Math.abs(t - Math.round(t)) < .05f
                    ? String.valueOf(Math.round(t))
                    : String.format(java.util.Locale.ROOT, "%.1f", t);
        }
    }

    public static Reading of(LivingEntity e) {
        float hp = e.getHealth(), abs = Math.max(0, e.getAbsorptionAmount());
        Integer score = e instanceof Player p ? belowName(p) : null;
        if (score != null) hp = score;
        if (!Float.isFinite(hp)) hp = 0;
        boolean known = (hp > 0 || !e.isAlive()) && hp < 1000;
        float max = Math.max(1, Math.max(e.getMaxHealth(), hp));
        return new Reading(Math.max(0, hp), max, abs, known);
    }

    private static Integer belowName(Player p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        var board = level.getScoreboard();
        var objective = board.getDisplayObjective(DisplaySlot.BELOW_NAME);
        if (objective == null) return null;
        var score = board.getPlayerScoreInfo(p, objective);
        return score == null ? null : score.value();
    }
}
