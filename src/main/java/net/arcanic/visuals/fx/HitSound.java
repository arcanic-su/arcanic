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
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** "Hitsound": a short pleasant sound for every hit you land on a mob or a player. */
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

    public final Setting.Mode sound = add(new Setting.Mode(
            "sound",
            "Звук",
            ALL.get(0).name(),
            ALL.stream().map(KillSound.Sound::name).toArray(String[]::new)));
    public final Setting.Num volume = add(new Setting.Num("volume", "Громкость", 100, 0, 100, 5, "%"));
    public final Setting.Bool vary = add(new Setting.Bool("vary", "Слегка менять тон", true));
    public final Setting.Bool mute = add(new Setting.Bool("mute", "Заменять звук удара", true));
    private long attackAt;
    private final Map<String, SoundEvent> events = new HashMap<>();
    private long playedAt;

    public HitSound() {
        super(
                "hitsound",
                "Hitsound",
                "Короткий приятный звук при каждом твоём ударе по мобу или игроку. Слышишь только ты.",
                Category.COMBAT,
                false);
    }

    public KillSound.Sound current() {
        for (KillSound.Sound s : ALL) if (sound.is(s.name())) return s;
        return ALL.get(0);
    }

    public void onAttack(Entity target) {
        if (!(target instanceof LivingEntity)) return;
        attackAt = System.currentTimeMillis();
        play(current());
    }

    /** True for the game's own sound of your hit, which the Hitsound replaces. */
    public boolean silences(SoundInstance s) {
        if (!enabled() || !mute.on() || System.currentTimeMillis() - attackAt > 700) return false;
        Identifier id = s.getIdentifier();
        if (id == null || !"minecraft".equals(id.getNamespace())) return false;
        String p = id.getPath();
        var me = Minecraft.getInstance().player;
        // only the swing of your own hit; the target's hurt sound and everything else stays as it is
        return p.startsWith("entity.player.attack.")
                && me != null
                && me.distanceToSqr(s.getX(), s.getY(), s.getZ()) < 16;
    }

    /** Plays a sound (also the preview in the menu). */
    public void play(KillSound.Sound s) {
        long now = System.currentTimeMillis();
        if (now - playedAt < 40 || volume.get() <= 0) return;
        playedAt = now;
        SoundEvent ev = events.computeIfAbsent(
                s.id(),
                id -> SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath("arcanicvisuals", "hit." + id)));
        float pitch = vary.on() ? .96f + (float) Math.random() * .08f : 1f;
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume.f() / 100f));
        } catch (RuntimeException ignored) {
        }
    }
}
