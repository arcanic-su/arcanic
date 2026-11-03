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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class HitSound extends Module {
    public static final List<KillSound.Sound> ALL = List.of(
            new KillSound.Sound("pop", "Pop"),
            new KillSound.Sound("click", "Click"),
            new KillSound.Sound("bell", "Bell"),
            new KillSound.Sound("bubble", "Bubble"),
            new KillSound.Sound("punch", "Punch"),
            new KillSound.Sound("crystal", "Crystal"),
            new KillSound.Sound("coin", "Coin"),
            new KillSound.Sound("wood", "Wood"),
            new KillSound.Sound("gamesense", "Gamesense"),
            new KillSound.Sound("neverlose", "Neverlose"));
    public final Setting.Mode sound;
    public final Setting.Num volume;
    public final Setting.Bool vary;
    public final Setting.Bool mute;
    private long attackAt;
    private final Map<String, SoundEvent> events;
    private long playedAt;

    public HitSound() {
        super(
                "hitsound",
                "Hitsound",
                "Короткий приятный звук при каждом твоём ударе по мобу или игроку. Слышишь только ты.",
                Category.COMBAT,
                false);
        this.sound = (Setting.Mode)
                this.add(new Setting.Mode("sound", "Звук", ((KillSound.Sound) ALL.get(0)).name(), (String[])
                        ALL.stream().map(KillSound.Sound::name).toArray((x$0) -> new String[x$0])));
        this.volume = this.add(new Setting.Num("volume", "Громкость", 100.0, 0.0, 100.0, 5.0, "%"));
        this.vary = this.add(new Setting.Bool("vary", "Слегка менять тон", true));
        this.mute = this.add(new Setting.Bool("mute", "Заменять звук удара", true));
        this.events = new HashMap<>();
    }

    public KillSound.Sound current() {
        for (KillSound.Sound s : ALL) {
            if (this.sound.is(s.name())) {
                return s;
            }
        }

        return (KillSound.Sound) ALL.get(0);
    }

    public void onAttack(Entity target) {
        if (target instanceof LivingEntity) {
            this.attackAt = System.currentTimeMillis();
            this.play(this.current());
        }
    }

    public boolean silences(SoundInstance s) {
        if (this.enabled() && this.mute.on() && System.currentTimeMillis() - this.attackAt <= 700L) {
            Identifier id = s.getIdentifier();
            if (id != null && "minecraft".equals(id.getNamespace())) {
                String p = id.getPath();
                LocalPlayer me = Minecraft.getInstance().player;
                return p.startsWith("entity.player.attack.")
                        && me != null
                        && me.distanceToSqr(s.getX(), s.getY(), s.getZ()) < 16.0;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void play(KillSound.Sound s) {
        long now = System.currentTimeMillis();
        if (now - this.playedAt >= 40L && !((Double) this.volume.get() <= 0.0)) {
            this.playedAt = now;
            SoundEvent ev = (SoundEvent) this.events.computeIfAbsent(
                    s.id(),
                    (id) -> SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath("arcanicvisuals", "hit." + id)));
            float pitch = this.vary.on() ? 0.96F + (float) Math.random() * 0.08F : 1.0F;

            try {
                Minecraft.getInstance()
                        .getSoundManager()
                        .play(SimpleSoundInstance.forUI(ev, pitch, this.volume.f() / 100.0F));
            } catch (RuntimeException ignored) {
            }
        }
    }
}
