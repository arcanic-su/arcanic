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
package net.arcanic.visuals.helper;

import java.util.ArrayList;
import java.util.List;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.resources.sounds.SoundInstance;

public final class SoundMixer extends Module {
    private final List<Group> groups = new ArrayList<>();

    private void group(String key, String name, String... parts) {
        this.groups.add(new Group(this.add(new Setting.Num(key, name, 100.0, 0.0, 200.0, 5.0, "%")), parts));
    }

    public SoundMixer() {
        super(
                "sound_mixer",
                "Громкость звуков",
                "Громкость ударов, тотема, взрывов, шагов и другого по отдельности",
                Category.UTILITY,
                false);
        this.group("hits", "Удары и урон", "player.attack", "player.hurt", "generic.hurt", ".hurt");
        this.group("totem", "Тотем", "item.totem");
        this.group("pearl", "Жемчуг и телепорт", "ender_pearl", "player.teleport", "enderman.teleport", "chorus_fruit");
        this.group(
                "blast", "Взрывы и кристаллы", "explode", "tnt.primed", "respawn_anchor", "end_crystal", "wind_charge");
        this.group("steps", "Шаги", ".step");
        this.group("eat", "Еда и зелья", "generic.eat", "generic.drink", "player.burp", "splash_potion", "honey_drink");
        this.group("bow", "Луки и стрелы", "entity.arrow", "item.crossbow", "item.trident");
        this.group("xp", "Опыт", "experience_orb", "player.levelup");
        this.group(
                "chest",
                "Сундуки и двери",
                "block.chest",
                "ender_chest",
                "shulker_box",
                "block.barrel",
                "door",
                "trapdoor",
                "fence_gate");
        this.group("armor", "Броня", "item.armor");
        this.group("weather", "Дождь и гром", "weather.", "lightning_bolt");
        this.group(
                "mobs",
                "Мобы",
                "entity.zombie",
                "entity.skeleton",
                "entity.creeper",
                "entity.spider",
                "entity.enderman.ambient");
    }

    public static float scale(SoundInstance sound, float volume) {
        SoundMixer m = Modules.SOUND_MIXER;
        if (m != null && m.enabled() && sound != null) {
            String id = sound.getIdentifier().getPath();

            for (Group g : m.groups) {
                for (String p : g.parts()) {
                    if (id.contains(p)) {
                        return Math.min(2.0F, volume * g.level().f() / 100.0F);
                    }
                }
            }

            return volume;
        } else {
            return volume;
        }
    }

    private static record Group(Setting.Num level, String[] parts) {}
}
