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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** "Killsound": an announcer line when something you just hit dies - a mob or a player. */
public final class KillSound extends Module {
    public record Sound(String id, String name) {}

    public static final List<Sound> ALL = List.of(
            new Sound("what_a_kill", "What a kill!"),
            new Sound("excellent", "Excellent elimination!"),
            new Sound("godlike", "Godlike"),
            new Sound("too_easy", "Too easy for Arcanic"),
            new Sound("victory", "Arcanic Victory"));

    public final Setting.Mode sound = add(new Setting.Mode(
            "sound", "Звук", ALL.get(0).name(), ALL.stream().map(Sound::name).toArray(String[]::new)));
    public final Setting.Num volume = add(new Setting.Num("volume", "Громкость", 100, 0, 100, 5, "%"));
    public final Setting.Bool players = add(new Setting.Bool("players", "Только игроки", false));
    private final Map<Integer, Long> hit = new HashMap<>();
    private final Map<String, SoundEvent> events = new HashMap<>();
    private long playedAt;

    public KillSound() {
        super(
                "killsound",
                "Killsound",
                "Фраза диктора, когда твоя цель умирает — моб или игрок. Слышишь только ты.",
                Category.COMBAT,
                false);
    }

    public Sound current() {
        for (Sound s : ALL) if (sound.is(s.name())) return s;
        return ALL.get(0);
    }

    public void onAttack(Entity target) {
        if (target instanceof LivingEntity) hit.put(target.getId(), System.currentTimeMillis());
    }

    @Override
    public void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            hit.clear();
            return;
        }
        long now = System.currentTimeMillis();
        hit.entrySet().removeIf(en -> {
            if (now - en.getValue() > 3000) return true; // it outlived our hit: not our kill
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof LivingEntity l)) return true;
            if (l.isAlive() && l.getHealth() > 0) return false;
            if (!players.on() || l instanceof Player) play(current());
            return true;
        });
    }

    /** Plays a line (also the preview in the menu). */
    public void play(Sound s) {
        long now = System.currentTimeMillis();
        if (now - playedAt < 250 || volume.get() <= 0) return;
        playedAt = now;
        SoundEvent ev = events.computeIfAbsent(
                s.id(),
                id -> SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath("arcanicvisuals", "kill." + id)));
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, 1f, volume.f() / 100f));
        } catch (RuntimeException ignored) {
        }
    }
}
