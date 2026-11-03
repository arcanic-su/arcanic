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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class KillSound extends Module {
    public static final List<Sound> ALL = List.of(
            new Sound("what_a_kill", "What a kill!"),
            new Sound("excellent", "Excellent elimination!"),
            new Sound("godlike", "Godlike"),
            new Sound("too_easy", "Too easy for Arcanic"),
            new Sound("victory", "Arcanic Victory"));
    public final Setting.Mode sound;
    public final Setting.Num volume;
    public final Setting.Bool players;
    private final Map<Integer, Long> hit;
    private final Map<String, SoundEvent> events;
    private long playedAt;

    public KillSound() {
        super(
                "killsound",
                "Killsound",
                "Фраза диктора, когда твоя цель умирает — моб или игрок. Слышишь только ты.",
                Category.COMBAT,
                false);
        this.sound = this.add(new Setting.Mode("sound", "Звук", ((Sound) ALL.get(0)).name(), (String[])
                ALL.stream().map(Sound::name).toArray((x$0) -> new String[x$0])));
        this.volume = this.add(new Setting.Num("volume", "Громкость", 100.0, 0.0, 100.0, 5.0, "%"));
        this.players = this.add(new Setting.Bool("players", "Только игроки", false));
        this.hit = new HashMap<>();
        this.events = new HashMap<>();
    }

    public Sound current() {
        for (Sound s : ALL) {
            if (this.sound.is(s.name())) {
                return s;
            }
        }

        return (Sound) ALL.get(0);
    }

    public void onAttack(Entity target) {
        if (target instanceof LivingEntity) {
            this.hit.put(target.getId(), System.currentTimeMillis());
        }
    }

    public void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            this.hit.clear();
        } else {
            long now = System.currentTimeMillis();
            this.hit.entrySet().removeIf((en) -> {
                if (now - (Long) en.getValue() > 3000L) {
                    return true;
                } else {
                    Entity e = level.getEntity((Integer) en.getKey());
                    if (e instanceof LivingEntity) {
                        LivingEntity l = (LivingEntity) e;
                        if (l.isAlive() && l.getHealth() > 0.0F) {
                            return false;
                        } else {
                            if (!this.players.on() || l instanceof Player) {
                                this.play(this.current());
                            }

                            return true;
                        }
                    } else {
                        return true;
                    }
                }
            });
        }
    }

    public void play(Sound s) {
        long now = System.currentTimeMillis();
        if (now - this.playedAt >= 250L && !((Double) this.volume.get() <= 0.0)) {
            this.playedAt = now;
            SoundEvent ev = (SoundEvent) this.events.computeIfAbsent(
                    s.id(),
                    (id) -> SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath("arcanicvisuals", "kill." + id)));

            try {
                Minecraft.getInstance()
                        .getSoundManager()
                        .play(SimpleSoundInstance.forUI(ev, 1.0F, this.volume.f() / 100.0F));
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static record Sound(String id, String name) {}
}
