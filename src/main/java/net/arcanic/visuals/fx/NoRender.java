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

import java.util.Set;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

/** Turns off vanilla clutter: particles, screen overlays, camera shake, fog, weather and HUD bits. */
public final class NoRender extends Module {
    private static final Set<ParticleType<?>> EXPLOSIONS = Set.of(
            ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION_EMITTER,
            ParticleTypes.GUST,
            ParticleTypes.GUST_EMITTER_LARGE,
            ParticleTypes.GUST_EMITTER_SMALL);
    private static final Set<ParticleType<?>> BLOCKS = Set.of(
            ParticleTypes.BLOCK,
            ParticleTypes.BLOCK_MARKER,
            ParticleTypes.BLOCK_CRUMBLE,
            ParticleTypes.FALLING_DUST,
            ParticleTypes.DUST_PILLAR);
    private static final Set<ParticleType<?>> POTIONS = Set.of(
            ParticleTypes.ENTITY_EFFECT, ParticleTypes.EFFECT, ParticleTypes.INSTANT_EFFECT, ParticleTypes.WITCH);
    private static final Set<ParticleType<?>> HITS = Set.of(
            ParticleTypes.CRIT,
            ParticleTypes.ENCHANTED_HIT,
            ParticleTypes.DAMAGE_INDICATOR,
            ParticleTypes.SWEEP_ATTACK);
    private static final Set<ParticleType<?>> WATER = Set.of(
            ParticleTypes.BUBBLE,
            ParticleTypes.BUBBLE_POP,
            ParticleTypes.BUBBLE_COLUMN_UP,
            ParticleTypes.CURRENT_DOWN,
            ParticleTypes.UNDERWATER,
            ParticleTypes.SPLASH,
            ParticleTypes.FISHING,
            ParticleTypes.RAIN,
            ParticleTypes.DRIPPING_WATER,
            ParticleTypes.FALLING_WATER);

    public final Setting.Bool allParticles = add(new Setting.Bool("all_particles", "Все частицы", true));
    public final Setting.Bool explosions =
            add(new Setting.Bool("explosions", "Частицы взрывов", true)).showIf(this::someParticles);
    public final Setting.Bool blockParticles =
            add(new Setting.Bool("block_particles", "Частицы блоков", true)).showIf(this::someParticles);
    public final Setting.Bool potionParticles =
            add(new Setting.Bool("potion_particles", "Частицы зелий", true)).showIf(this::someParticles);
    public final Setting.Bool hitParticles =
            add(new Setting.Bool("hit_particles", "Частицы ударов", false)).showIf(this::someParticles);
    public final Setting.Bool waterParticles =
            add(new Setting.Bool("water_particles", "Пузыри и брызги", true)).showIf(this::someParticles);

    public final Setting.Bool fire = add(new Setting.Bool("fire", "Огонь на экране", true));
    public final Setting.Bool insideBlock = add(new Setting.Bool("inside_block", "Вода и блоки перед глазами", true));
    public final Setting.Bool nausea = add(new Setting.Bool("nausea", "Тошнота и портал", true));
    public final Setting.Bool darkness = add(new Setting.Bool("darkness", "Тьма и слепота", true));
    public final Setting.Bool vignette = add(new Setting.Bool("vignette", "Виньетка", true));
    public final Setting.Bool hurtShake = add(new Setting.Bool("hurt_bob", "Тряска при уроне", true));
    public final Setting.Bool walkBob = add(new Setting.Bool("walk_bob", "Покачивание при ходьбе", false));
    public final Setting.Bool fog = add(new Setting.Bool("fog", "Туман", false));
    public final Setting.Bool fluidFog = add(new Setting.Bool("fluid_fog", "Туман в воде и лаве", false));
    public final Setting.Bool weather = add(new Setting.Bool("weather", "Дождь и снег", true));
    public final Setting.Bool shadows = add(new Setting.Bool("shadows", "Тени", false));
    public final Setting.Bool border = add(new Setting.Bool("world_border", "Граница мира", true));
    public final Setting.Bool glowing = add(new Setting.Bool("glowing", "Эффект свечения", false));
    public final Setting.Bool xpBottles = add(new Setting.Bool("xp_bottles", "Бутылки опыта", true));
    public final Setting.Bool bossBar = add(new Setting.Bool("bossbar", "Полоса босса", false));
    public final Setting.Bool scoreboard = add(new Setting.Bool("scoreboard", "Таблица очков", false));
    public final Setting.Bool effectIcons = add(new Setting.Bool("effects", "Иконки эффектов", false));
    public final Setting.Bool airBubbles = add(new Setting.Bool("air_bubbles", "Полоска воздуха", false));
    public final Setting.Bool attackIndicator = add(new Setting.Bool("attack_indicator", "Индикатор атаки", false));
    public final Setting.Bool witherHearts = add(new Setting.Bool("wither_hearts", "Чёрные сердца иссушения", true));

    public NoRender() {
        super(
                "no_render",
                "No Render",
                "Убирает лишнее: частицы, огонь и воду на экране, тряску, тьму, туман, погоду и куски интерфейса. Каждую часть можно оставить.",
                Category.WORLD,
                false);
    }

    private boolean someParticles() {
        return !allParticles.on();
    }

    /** Shortcut for mixins: is the module on and this switch enabled. */
    public static boolean hides(Setting.Bool part) {
        NoRender n = Modules.NO_RENDER;
        return n != null && n.enabled() && part.on();
    }

    public boolean hidesParticle(ParticleType<?> type) {
        if (!enabled()) return false;
        if (allParticles.on()) return true;
        return explosions.on() && EXPLOSIONS.contains(type)
                || blockParticles.on() && BLOCKS.contains(type)
                || potionParticles.on() && POTIONS.contains(type)
                || hitParticles.on() && HITS.contains(type)
                || waterParticles.on() && WATER.contains(type);
    }

    /** Particles created without options (block breaking cracks use TerrainParticle directly). */
    public boolean hidesParticle(Particle particle) {
        if (!enabled()) return false;
        return allParticles.on() || blockParticles.on() && particle instanceof TerrainParticle;
    }
}
