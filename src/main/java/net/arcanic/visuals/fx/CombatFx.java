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

import java.awt.Color;
import java.util.*;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Draw;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Hit particles, damage numbers and kill effects. */
public final class CombatFx {
    private CombatFx() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    static boolean critical(Player p) {
        return p.fallDistance > 0
                && !p.onGround()
                && !p.onClimbable()
                && !p.isInWater()
                && !p.hasEffect(MobEffects.BLINDNESS)
                && !p.isPassenger()
                && !p.isSprinting()
                && p.getAttackStrengthScale(.5f) > .9f;
    }

    /** Hit effects: glowing bursts on every hit, a brighter one with a shockwave on a crit. */
    public static final class HitEffects extends Module {
        final Setting.Mode type = add(
                new Setting.Mode("type", "Стиль", "Искры", "Искры", "Звёзды", "Волна", "Разрез", "Магия", "Сердца"));
        final Setting.Mode colourMode =
                add(new Setting.Mode("color_mode", "Окраска", "Тема", "Тема", "Радуга", "Цвет"));
        final Setting.Colour colour =
                add(new Setting.Colour("color", "Цвет", false)).showIf(() -> colourMode.is("Цвет"));
        final Setting.Num power = add(new Setting.Num("power", "Сила", 1, .4, 2, .05, "×"));
        final Setting.Bool crit = add(new Setting.Bool("crit", "Вспышка на крит", true));
        final Setting.Bool sound = add(new Setting.Bool("sound", "Звук попадания", false));

        public HitEffects() {
            super("hit_effects", "Эффекты удара", "Светящиеся частицы при попадании", Category.COMBAT, false);
            colour.set(0xFFF43F5E);
        }

        int colour() {
            return colourMode.is("Радуга")
                    ? Draw.rainbow(0, 1)
                    : colourMode.is("Цвет") ? colour.get() : Theme.accent2();
        }

        public void onAttack(Player p, Entity target) {
            if (!(target instanceof LivingEntity)) return;
            boolean isCrit = crit.on() && critical(p);
            Vec3 at = target.position().add(0, target.getBbHeight() * .6, 0);
            // shift the burst towards the attacker so it shows in front of the body
            Vec3 toMe = p.getEyePosition().subtract(at);
            at = at.add(toMe.normalize().scale(Math.min(.45, target.getBbWidth() * .6)));
            Effects.hit(type.get(), at, colour(), power.f(), isCrit);
            if (sound.on() && mc().level != null)
                mc().level
                        .playLocalSound(
                                at.x,
                                at.y,
                                at.z,
                                isCrit ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_HIT,
                                SoundSource.PLAYERS,
                                .6f,
                                isCrit ? 1.6f : 1.2f,
                                false);
        }
    }

    /** Damage numbers: numbers float up from anyone who loses health nearby. */
    public static final class DamageNumbers extends Module {
        final Setting.Mode style = add(new Setting.Mode("style", "Вид", "Число", "Число", "Сердца"));
        final Setting.Num size = add(new Setting.Num("size", "Размер", 1, .5, 2.5, .05, "×"));
        final Setting.Num life = add(new Setting.Num("life", "Время", 1.2, .4, 3, .1, " с"));
        final Setting.Bool self = add(new Setting.Bool("self", "Свой урон тоже", false));
        final Setting.Colour colour = add(new Setting.Colour("color", "Цвет", false));
        private final Map<Integer, Float> health = new HashMap<>();
        private final List<Pop> pops = new ArrayList<>();

        private record Pop(Vec3 at, String text, int colour, long born, double drift) {}

        public DamageNumbers() {
            super("damage_numbers", "Цифры урона", "Всплывающий урон над целью", Category.COMBAT, false);
            colour.set(0xFFF43F5E);
        }

        @Override
        public void tick() {
            var level = mc().level;
            var me = mc().player;
            if (level == null || me == null) {
                health.clear();
                return;
            }
            Map<Integer, Float> seen = new HashMap<>();
            for (Entity e : level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity l) || l.distanceToSqr(me) > 48 * 48) continue;
                float hp = l.getHealth() + l.getAbsorptionAmount();
                Float before = health.get(l.getId());
                seen.put(l.getId(), hp);
                if (before == null || hp >= before - .01f || (l == me && !self.on())) continue;
                float dmg = before - hp;
                String text = style.is("Сердца")
                        ? String.format(Locale.ROOT, "-%.1f ❤", dmg / 2)
                        : String.format(Locale.ROOT, dmg >= 10 ? "-%.0f" : "-%.1f", dmg);
                int c = dmg >= 6 ? 0xFFFACC15 : colour.argb();
                pops.add(new Pop(
                        l.position().add(0, l.getBbHeight() + .3, 0),
                        text,
                        c,
                        System.currentTimeMillis(),
                        (Math.random() - .5) * .6));
            }
            health.clear();
            health.putAll(seen);
            pops.removeIf(p -> System.currentTimeMillis() - p.born > life.get() * 1000);
            if (pops.size() > 60) pops.subList(0, pops.size() - 60).clear();
        }

        @Override
        protected void onDisable() {
            pops.clear();
            health.clear();
        }

        /** Called inside pixel space. */
        public void render(GuiGraphics g) {
            if (pops.isEmpty()) return;
            int sw = Px.width(), sh = Px.height();
            float base = sh / 1080f;
            for (Pop p : pops) {
                float t = (System.currentTimeMillis() - p.born) / (float) (life.get() * 1000);
                Vec3 world = p.at.add(p.drift * t, t * .9, 0);
                double[] s = Projection.toScreen(world, sw, sh);
                if (s == null) continue;
                float pop = t < .12f ? .6f + t / .12f * .6f : 1.2f - Math.min(.2f, (t - .12f) * .5f);
                int px = Math.round(
                        (float) (22 * base * size.get() * pop * Math.max(.5, Math.min(1.3, 6 / Math.max(1, s[2])))));
                float a = t > .7f ? 1 - (t - .7f) / .3f : 1;
                var W = Px.Weight.BOLD;
                int tw = Px.textWidth(Lang.tr(p.text), W, px);
                Px.textShadow(
                        g, Lang.tr(p.text), (int) s[0] - tw / 2, (int) s[1] - px / 2, W, px, Draw.am(p.colour, a));
            }
        }
    }

    /** Kill effects: when someone you just hit dies. */
    public static final class KillEffects extends Module {
        final Setting.Mode type =
                add(new Setting.Mode("type", "Эффект", "Душа", "Душа", "Вознесение", "Взрыв", "Молния", "Конфетти"));
        final Setting.Mode colourMode = add(new Setting.Mode("color_mode", "Окраска", "Тема", "Тема", "Цвет"));
        final Setting.Colour colour =
                add(new Setting.Colour("color", "Цвет", false)).showIf(() -> colourMode.is("Цвет"));
        final Setting.Bool sound = add(new Setting.Bool("sound", "Звук", true));
        final Setting.Bool players = add(new Setting.Bool("players", "Только игроки", false));
        private final Map<Integer, Long> hit = new HashMap<>();

        public KillEffects() {
            super("kill_effects", "Эффекты убийства", "Эффект, когда твоя цель умирает", Category.COMBAT, false);
            colour.set(0xFF22D3EE);
        }

        public void onAttack(Entity target) {
            if (target instanceof LivingEntity) hit.put(target.getId(), System.currentTimeMillis());
        }

        @Override
        public void tick() {
            var level = mc().level;
            if (level == null) {
                hit.clear();
                return;
            }
            long now = System.currentTimeMillis();
            hit.entrySet().removeIf(en -> {
                if (now - en.getValue() > 3000) return true;
                Entity e = level.getEntity(en.getKey());
                if (!(e instanceof LivingEntity l)) return true;
                if (l.isAlive() && l.getHealth() > 0) return false;
                if (!players.on() || l instanceof Player) play(l);
                return true;
            });
        }

        public void play(LivingEntity l) {
            var level = mc().level;
            if (level == null) return;
            Vec3 at = l.position();
            int c = colourMode.is("Цвет") ? colour.get() : Theme.accent2();
            Effects.kill(type.get(), at, l.getBbHeight(), c);
            if (type.is("Молния")) {
                var bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.setPos(at);
                    bolt.setVisualOnly(true);
                    level.addEntity(bolt);
                }
            }
            if (!sound.on()) return;
            switch (type.get()) {
                case "Молния" ->
                    level.playLocalSound(
                            at.x,
                            at.y,
                            at.z,
                            SoundEvents.LIGHTNING_BOLT_THUNDER,
                            SoundSource.PLAYERS,
                            .45f,
                            1.3f,
                            false);
                case "Взрыв" ->
                    level.playLocalSound(
                            at.x,
                            at.y,
                            at.z,
                            SoundEvents.GENERIC_EXPLODE.value(),
                            SoundSource.PLAYERS,
                            .45f,
                            1.3f,
                            false);
                case "Конфетти" ->
                    level.playLocalSound(
                            at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, .7f, 1f, false);
                case "Вознесение" ->
                    level.playLocalSound(
                            at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, .6f, 1.6f, false);
                default ->
                    level.playLocalSound(
                            at.x, at.y, at.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1f, 1f, false);
            }
        }
    }

    /** Swing trail: a glowing crescent left in the air by every swing of your weapon. */
    public static final class SwingTrail extends Module {
        public final Setting.Mode colourMode =
                add(new Setting.Mode("color_mode", "Окраска", "Тема", "Тема", "Радуга", "Цвет"));
        public final Setting.Colour colour =
                add(new Setting.Colour("color", "Цвет", false)).showIf(() -> colourMode.is("Цвет"));
        public final Setting.Num size = add(new Setting.Num("size", "Размер", 1, .5, 1.8, .05, "×"));
        public final Setting.Num life = add(new Setting.Num("life", "Время", .35, .15, 1, .05, " с"));
        public final Setting.Bool firstPerson = add(new Setting.Bool("fp", "Видно от 1-го лица", false));

        public SwingTrail() {
            super("swing_trail", "След удара", "Светящаяся дуга за взмахом оружия", Category.COMBAT, false);
            colour.set(0xFF22D3EE);
        }

        public int colourAt(float f) {
            if (colourMode.is("Радуга")) return Draw.rainbow(f * .5f, 1);
            if (colourMode.is("Цвет")) return colour.get();
            return Fx.lerp(Theme.accent() | 0xFF000000, Theme.accent2() | 0xFF000000, f);
        }
    }

    /** Hit marker: a CS-style X on the crosshair for every hit (red on a kill), and a combo counter that pops. */
    public static final class HitMarker extends Module {
        final Setting.Bool combo = add(new Setting.Bool("combo", "Счётчик комбо", true));
        final Setting.Bool sound = add(new Setting.Bool("sound", "Щелчок", true));
        final Setting.Num size = add(new Setting.Num("size", "Размер", 1, .5, 2, .05, "×"));
        private long hitAt, comboAt;
        private int comboCount, lastTargetId;
        private boolean crit, killed;

        public HitMarker() {
            super("hitmarker", "Хитмаркер", "Крестик при попадании и счётчик комбо", Category.COMBAT, false);
        }

        public void onHit(Player p, Entity target) {
            if (!(target instanceof LivingEntity l)) return;
            long now = System.currentTimeMillis();
            comboCount = now - comboAt < 1600 ? comboCount + 1 : 1;
            comboAt = now;
            hitAt = now;
            crit = critical(p);
            killed = false;
            lastTargetId = target.getId();
            if (sound.on() && !Modules.HITSOUND.enabled() && mc().level != null)
                mc().level
                        .playLocalSound(
                                p.getX(),
                                p.getY(),
                                p.getZ(),
                                SoundEvents.NOTE_BLOCK_HAT.value(),
                                SoundSource.PLAYERS,
                                .35f,
                                crit ? 1.9f : 1.6f,
                                false);
        }

        @Override
        public void tick() {
            if (hitAt > 0 && !killed && System.currentTimeMillis() - hitAt < 600 && mc().level != null) {
                Entity e = mc().level.getEntity(lastTargetId);
                if (e instanceof LivingEntity l && (!l.isAlive() || l.getHealth() <= 0)) {
                    killed = true;
                    hitAt = System.currentTimeMillis();
                }
            }
        }
        /** Drawn in pixel space by the crosshair element. */
        public void render(GuiGraphics g) {
            long now = System.currentTimeMillis();
            int cx = Px.width() / 2, cy = Px.height() / 2;
            float u = Px.height() / 1080f * size.f();
            float t = (now - hitAt) / (killed ? 600f : 340f);
            if (hitAt > 0 && t < 1) {
                float pop = t < .18f ? 1.35f - .35f * (t / .18f) : 1;
                float a = t < .55f ? 1 : 1 - (t - .55f) / .45f;
                int col = killed ? 0xFFFF3B4E : crit ? 0xFFFFD166 : 0xFFFFFFFF;
                int gap = Math.round(9 * u * pop),
                        len = Math.round(13 * u * pop),
                        th = Math.max(3, Math.round(3.2f * u));
                Px.shadow(
                        g,
                        cx - gap - len,
                        cy - gap - len,
                        (gap + len) * 2,
                        (gap + len) * 2,
                        Math.round(10 * u),
                        Draw.am(col, a * .25f));
                for (int i = 0; i < 4; i++) {
                    double ang = Math.PI / 4 + i * Math.PI / 2 + (killed ? t * .6 : 0);
                    for (int s = 0; s <= len; s++) {
                        int x = Math.round((float) (cx + Math.cos(ang) * (gap + s))),
                                y = Math.round((float) (cy + Math.sin(ang) * (gap + s)));
                        Px.circle(g, x, y, Math.max(1, th / 2), Draw.am(0xFF000000, a * .35f));
                    }
                    for (int s = 0; s <= len; s++) {
                        int x = Math.round((float) (cx + Math.cos(ang) * (gap + s))),
                                y = Math.round((float) (cy + Math.sin(ang) * (gap + s)));
                        Px.circle(g, x, y, Math.max(1, th / 2 - (s == len ? 1 : 0)), Draw.am(col, a));
                    }
                }
            }
            float ct = (now - comboAt) / 1600f;
            if (combo.on() && comboCount >= 2 && ct < 1) {
                float pop = Math.min(1, (now - comboAt) / 140f);
                float scale = 1 + .45f * (1 - Anim.easeOutCubic(pop));
                float a = ct < .7f ? 1 : 1 - (ct - .7f) / .3f;
                int col = comboCount >= 10 ? 0xFFFF3B4E : comboCount >= 5 ? 0xFFFFB020 : Theme.accent2();
                String txt = "x" + comboCount;
                int px = Math.round(30 * u);
                g.pose().pushMatrix();
                g.pose().translate(cx + 48 * u, cy + 18 * u);
                g.pose().scale(scale, scale);
                float old = Px.fade;
                Px.fade = old * a;
                int tw = Px.textWidth(Lang.tr(txt), Px.Weight.BOLD, px);
                Px.shadow(
                        g,
                        -Math.round(6 * u),
                        -px / 2,
                        tw + Math.round(12 * u),
                        Math.round(px * 1.6f),
                        Math.round(14 * u),
                        Draw.am(0xFF000000, a * .45f));
                Px.textShadow(g, Lang.tr(txt), 0, -px / 2, Px.Weight.BOLD, px, col);
                Px.text(
                        g,
                        Lang.tr("КОМБО"),
                        0,
                        Math.round(px * .62f),
                        Px.Weight.BOLD,
                        Math.round(12 * u),
                        Draw.am(col, .8f));
                Px.fade = old;
                g.pose().popMatrix();
            }
        }
    }

    /** Target ESP: animated marks on the entity you fight, drawn by {@link TargetEsp}. */
    public static final class TargetMarker extends Module {
        public final Setting.Mode style = add(
                new Setting.Mode("style", "Стиль", "Вихрь", "Вихрь", "Гирлянда", "Скобки", "Орбита", "Маркер", "Круг"));
        public final Setting.Num size = add(new Setting.Num("size", "Размер", 1.2, .3, 3, .05, "×"))
                .showIf(() -> !style.is("Маркер") && !style.is("Круг"));
        public final Setting.Num circleRadius = add(new Setting.Num(
                        "circle_radius", "Радиус круга", 1.4, 1.3, 1.5, .01, "×"))
                .showIf(() -> style.is("Круг"));
        public final Setting.Num tilt = add(new Setting.Num("circle_tilt", "Наклон круга", 40, 20, 60, 1, "°"))
                .showIf(() -> style.is("Круг"));
        public final Setting.Num thickness = add(new Setting.Num(
                        "thickness", "Толщина серпов", 1.75, 1.5, 2.5, .05, "×"))
                .showIf(() -> style.is("Круг"));
        public final Setting.Num glow =
                add(new Setting.Num("glow", "Сила свечения", 2, 0, 3, .1, "×")).showIf(() -> style.is("Круг"));
        public final Setting.Bool throughWalls =
                add(new Setting.Bool("through_walls", "Сквозь стены", false)).showIf(() -> style.is("Круг"));
        public final Setting.Num markerSize = add(new Setting.Num("marker_size", "Размер маркера", 100, 30, 220, 5, ""))
                .showIf(() -> style.is("Маркер"));
        public final Setting.Bool markerGlow =
                add(new Setting.Bool("marker_glow", "Свечение маркера", true)).showIf(() -> style.is("Маркер"));
        public final Setting.Num speed = add(new Setting.Num("speed", "Скорость", 1, 0, 4, .05, "×"));
        public final Setting.Bool hurtTint = add(new Setting.Bool("hurt_tint", "Вспышка при уроне", true));
        final Setting.Mode colourMode =
                add(new Setting.Mode("color_mode", "Окраска", "Тема", "Тема", "Радуга", "Цвет"));
        final Setting.Colour colour =
                add(new Setting.Colour("color", "Цвет", false)).showIf(() -> colourMode.is("Цвет"));
        private final long start = System.currentTimeMillis();

        public TargetMarker() {
            super(
                    "target_marker",
                    "Маркер цели",
                    "Target ESP: вихрь, гирлянда, скобки, орбита, маркер и круг",
                    Category.COMBAT,
                    false);
            colour.set(0xFFF43F5E);
        }

        public int colour(LivingEntity t, int offset, float alpha) {
            int c = base(offset);
            if (hurtTint.on() && t.hurtTime > 0)
                c = Fx.lerp(c, 0xFFFF0000 | (c & 0xFF000000), Math.min(1, t.hurtTime / 10f));
            return TargetEsp.alpha(c, alpha);
        }

        private int base(int offset) {
            long now = System.currentTimeMillis();
            if (colourMode.is("Цвет")) return colour.get();
            if (colourMode.is("Радуга")) {
                float hue = ((now % 4000) / 4000f + offset / 360f) % 1;
                return 0xFF000000 | Color.HSBtoRGB(hue, .85f, 1);
            }
            // theme: back and forth between the two accents
            float phase = ((now - start) / 4f + offset) % 360;
            if (phase >= 180) phase = 360 - phase;
            return Fx.lerp(Theme.accent(), Theme.accent2(), phase * 2 / 360);
        }
    }
}
