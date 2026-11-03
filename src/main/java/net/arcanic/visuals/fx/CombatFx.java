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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CombatFx {
    private CombatFx() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    static boolean critical(Player p) {
        return p.fallDistance > 0.0
                && !p.onGround()
                && !p.onClimbable()
                && !p.isInWater()
                && !p.hasEffect(MobEffects.BLINDNESS)
                && !p.isPassenger()
                && !p.isSprinting()
                && p.getAttackStrengthScale(0.5F) > 0.9F;
    }

    public static final class HitEffects extends Module {
        public final Setting.Mode type = this.add(new Setting.Mode(
                "type", "Стиль", "Стекло", new String[] {"Стекло", "Сакура", "Звёздная пыль", "Бабочки", "Пузыри"}));
        final Setting.Mode colourMode = (Setting.Mode)
                this.add(new Setting.Mode("color_mode", "Окраска", "Свои", new String[] {"Свои", "Тема", "Цвет"}));
        final Setting.Colour colour =
                (this.add(new Setting.Colour("color", "Цвет", false))).showIf(() -> this.colourMode.is("Цвет"));
        final Setting.Num size = (Setting.Num) this.add(new Setting.Num("size", "Размер", 1.0, 0.5, 2.0, 0.05, "×"));
        final Setting.Bool critsOnly = this.add(new Setting.Bool("crits_only", "Только криты", false));
        final Setting.Bool sound = this.add(new Setting.Bool("sound", "Звук", true));

        public HitEffects() {
            super(
                    "hit_effects",
                    "Эффекты удара",
                    "Стекло, сакура, звёздная пыль, бабочки или пузыри там, куда пришёлся удар",
                    Category.COMBAT,
                    false);
            this.colour.set(-770210);
        }

        Show.Pal pal() {
            if (this.colourMode.is("Тема")) {
                return Show.theme();
            } else if (this.colourMode.is("Цвет")) {
                int c = (Integer) this.colour.get() | 0xFF000000;
                return new Show.Pal(Fx.lerp(c, -1, 0.5F), c, Fx.lerp(c, 0xFF000000, 0.5F));
            } else {
                return HitFx.own(this.type.index());
            }
        }

        public void onAttack(Player p, Entity target) {
            if (target instanceof LivingEntity l) {
                if (target != p) {
                    boolean crit = CombatFx.critical(p);
                    if (this.critsOnly.on() && !crit) {
                        return;
                    }

                    Vec3 eye = p.getEyePosition(1.0F);
                    Vec3 at = null;
                    HitResult var8 = CombatFx.mc().hitResult;
                    if (var8 instanceof EntityHitResult) {
                        EntityHitResult eh = (EntityHitResult) var8;
                        if (eh.getEntity() == target) {
                            at = eh.getLocation();
                        }
                    }

                    if (at == null) {
                        AABB box = l.getBoundingBox();
                        at = new Vec3(
                                Math.max(box.minX, Math.min(box.maxX, eye.x)),
                                Math.max(box.minY, Math.min(box.maxY, eye.y)),
                                Math.max(box.minZ, Math.min(box.maxZ, eye.z)));
                    }

                    Vec3 dir = at.subtract(eye);
                    dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
                    at = at.subtract(dir.scale(0.08));
                    HitFx.spawn(
                            this.type.index(),
                            at,
                            dir,
                            l.getBoundingBox(),
                            HitFx.ground(l),
                            crit,
                            this.size.f(),
                            this.pal(),
                            this.sound.on(),
                            false);
                    return;
                }
            }
        }

        void preview(Vec3 at, Vec3 dir, boolean crit, AABB body) {
            HitFx.spawn(
                    this.type.index(),
                    at,
                    dir,
                    body,
                    HitFx.Ground.STUDIO,
                    crit,
                    this.size.f(),
                    this.pal(),
                    this.sound.on(),
                    true);
        }

        protected void onDisable() {
            HitFx.clear();
        }
    }

    public static final class DamageNumbers extends Module {
        final Setting.Mode style =
                this.add(new Setting.Mode("style", "Вид", "Число", new String[] {"Число", "Сердца"}));
        final Setting.Num size = (Setting.Num) this.add(new Setting.Num("size", "Размер", 1.0, 0.5, 2.5, 0.05, "×"));
        final Setting.Num life = this.add(new Setting.Num("life", "Время", 1.2, 0.4, 3.0, 0.1, " с"));
        final Setting.Bool self = this.add(new Setting.Bool("self", "Свой урон тоже", false));
        final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", false));
        private final Map<Integer, Float> health = new HashMap<>();
        private final List<Pop> pops = new ArrayList<>();

        public DamageNumbers() {
            super("damage_numbers", "Цифры урона", "Всплывающий урон над целью", Category.COMBAT, false);
            this.colour.set(-770210);
        }

        public void tick() {
            ClientLevel level = CombatFx.mc().level;
            LocalPlayer me = CombatFx.mc().player;
            if (level != null && me != null) {
                Map<Integer, Float> seen = new HashMap<>();

                for (Entity e : level.entitiesForRendering()) {
                    if (e instanceof LivingEntity) {
                        LivingEntity l = (LivingEntity) e;
                        if (!(l.distanceToSqr(me) > 2304.0)) {
                            float hp = l.getHealth() + l.getAbsorptionAmount();
                            Float before = (Float) this.health.get(l.getId());
                            seen.put(l.getId(), hp);
                            if (before != null && !(hp >= before - 0.01F) && (l != me || this.self.on())) {
                                float dmg = before - hp;
                                String text = this.style.is("Сердца")
                                        ? String.format(Locale.ROOT, "-%.1f ❤", dmg / 2.0F)
                                        : String.format(Locale.ROOT, dmg >= 10.0F ? "-%.0f" : "-%.1f", dmg);
                                int c = dmg >= 6.0F ? -340971 : this.colour.argb();
                                this.pops.add(new Pop(
                                        l.position().add(0.0, (double) l.getBbHeight() + 0.3, 0.0),
                                        text,
                                        c,
                                        System.currentTimeMillis(),
                                        (Math.random() - 0.5) * 0.6));
                            }
                        }
                    }
                }

                this.health.clear();
                this.health.putAll(seen);
                this.pops.removeIf(
                        (p) -> (double) (System.currentTimeMillis() - p.born) > (Double) this.life.get() * 1000.0);
                if (this.pops.size() > 60) {
                    this.pops.subList(0, this.pops.size() - 60).clear();
                }

            } else {
                this.health.clear();
            }
        }

        protected void onDisable() {
            this.pops.clear();
            this.health.clear();
        }

        public void render(GuiGraphics g) {
            if (!this.pops.isEmpty()) {
                int sw = Px.width();
                int sh = Px.height();
                float base = (float) sh / 1080.0F;

                for (Pop p : this.pops) {
                    float t =
                            (float) (System.currentTimeMillis() - p.born) / (float) ((Double) this.life.get() * 1000.0);
                    Vec3 world = p.at.add(p.drift * (double) t, (double) t * 0.9, 0.0);
                    double[] s = Projection.toScreen(world, sw, sh);
                    if (s != null) {
                        float pop = t < 0.12F ? 0.6F + t / 0.12F * 0.6F : 1.2F - Math.min(0.2F, (t - 0.12F) * 0.5F);
                        int px = Math.round((float) ((double) (22.0F * base)
                                * (Double) this.size.get()
                                * (double) pop
                                * Math.max(0.5, Math.min(1.3, 6.0 / Math.max(1.0, s[2])))));
                        float a = t > 0.7F ? 1.0F - (t - 0.7F) / 0.3F : 1.0F;
                        Px.Weight W = Px.Weight.BOLD;
                        int tw = Px.textWidth(Lang.tr(p.text), W, px);
                        Px.textShadow(
                                g,
                                Lang.tr(p.text),
                                (int) s[0] - tw / 2,
                                (int) s[1] - px / 2,
                                W,
                                px,
                                Draw.am(p.colour, a));
                    }
                }
            }
        }

        private static record Pop(Vec3 at, String text, int colour, long born, double drift) {}
    }

    public static final class KillEffects extends Module {
        public final Setting.Mode type = this.add(new Setting.Mode("type", "Эффект", "Пепел", new String[] {
            "Пепел", "Казнь", "Чёрная дыра", "Глитч", "Рассечение", "Конфетти", "Метеорит", "Гроза"
        }));
        final Setting.Mode colourMode = (Setting.Mode)
                this.add(new Setting.Mode("color_mode", "Окраска", "Свои", new String[] {"Свои", "Тема", "Цвет"}));
        final Setting.Colour colour =
                (this.add(new Setting.Colour("color", "Цвет", false))).showIf(() -> this.colourMode.is("Цвет"));
        final Setting.Num size = this.add(new Setting.Num("size", "Размер", 1.0, 0.6, 1.6, 0.05, "×"));
        final Setting.Bool screen = this.add(new Setting.Bool("screen", "Эффекты на экране", true));
        final Setting.Bool shake = this.add(new Setting.Bool("shake", "Тряска камеры", true));
        final Setting.Bool sound = this.add(new Setting.Bool("sound", "Звук", true));
        final Setting.Num volume =
                (Setting.Num) (this.add(new Setting.Num("volume", "Громкость", 0.7, 0.1, 1.0, 0.05, "")))
                        .showIf(() -> this.sound.on());
        final Setting.Bool players = this.add(new Setting.Bool("players", "Только игроки", false));
        private final Map<Integer, Long> hit = new HashMap<>();

        public KillEffects() {
            super(
                    "kill_effects",
                    "Эффекты убийства",
                    "Пепел, казнь, чёрная дыра, глитч, рассечение, конфетти, метеорит или гроза, когда твоя цель умирает",
                    Category.COMBAT,
                    false);
            this.colour.set(-14494738);
        }

        Show.Pal pal() {
            if (this.colourMode.is("Тема")) {
                return Show.theme();
            } else if (this.colourMode.is("Цвет")) {
                int c = (Integer) this.colour.get() | 0xFF000000;
                return new Show.Pal(Fx.lerp(c, -1, 0.5F), c, Fx.lerp(c, 0xFF000000, 0.45F));
            } else {
                return KillFx.own(this.type.index());
            }
        }

        public void onAttack(Entity target) {
            if (target instanceof LivingEntity) {
                this.hit.put(target.getId(), System.currentTimeMillis());
            }
        }

        public void tick() {
            ClientLevel level = CombatFx.mc().level;
            if (level == null) {
                this.hit.clear();
            } else {
                long now = System.currentTimeMillis();
                this.hit.entrySet().removeIf((en) -> {
                    if (now - (Long) en.getValue() > 4000L) {
                        return true;
                    } else {
                        Entity e = level.getEntity((Integer) en.getKey());
                        if (e instanceof LivingEntity) {
                            LivingEntity l = (LivingEntity) e;
                            if (!l.isDeadOrDying()) {
                                return false;
                            } else {
                                if (!this.players.on() || l instanceof Player) {
                                    KillFx.spawn(
                                            this.type.index(),
                                            l.position(),
                                            l.getBbWidth(),
                                            l.getBbHeight(),
                                            l.yBodyRot,
                                            this.size.f() * Math.max(0.6F, Math.min(1.6F, l.getBbHeight() / 1.8F)),
                                            this.pal(),
                                            false);
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

        void preview(Vec3 feet) {
            KillFx.spawn(this.type.index(), feet, 0.6F, 1.8F, 90.0F, this.size.f(), this.pal(), true);
        }

        protected void onDisable() {
            this.hit.clear();
            KillFx.clear();
        }
    }

    public static final class SwingTrail extends Module {
        public final Setting.Mode colourMode = (Setting.Mode)
                this.add(new Setting.Mode("color_mode", "Окраска", "Тема", new String[] {"Тема", "Радуга", "Цвет"}));
        public final Setting.Colour colour =
                (this.add(new Setting.Colour("color", "Цвет", false))).showIf(() -> this.colourMode.is("Цвет"));
        public final Setting.Num size = this.add(new Setting.Num("size", "Размер", 1.0, 0.5, 1.8, 0.05, "×"));
        public final Setting.Num life = this.add(new Setting.Num("life", "Время", 0.35, 0.15, 1.0, 0.05, " с"));
        public final Setting.Bool firstPerson = this.add(new Setting.Bool("fp", "Видно от 1-го лица", false));

        public SwingTrail() {
            super("swing_trail", "След удара", "Светящаяся дуга за взмахом оружия", Category.COMBAT, false);
            this.colour.set(-14494738);
        }

        public int colourAt(float f) {
            if (this.colourMode.is("Радуга")) {
                return Draw.rainbow(f * 0.5F, 1.0F);
            } else {
                return this.colourMode.is("Цвет")
                        ? (Integer) this.colour.get()
                        : Fx.lerp(Theme.accent() | 0xFF000000, Theme.accent2() | 0xFF000000, f);
            }
        }
    }

    public static final class HitMarker extends Module {
        final Setting.Bool combo = this.add(new Setting.Bool("combo", "Счётчик комбо", true));
        final Setting.Bool sound = this.add(new Setting.Bool("sound", "Щелчок", true));
        final Setting.Num size = (Setting.Num) this.add(new Setting.Num("size", "Размер", 1.0, 0.5, 2.0, 0.05, "×"));
        private long hitAt;
        private long comboAt;
        private int comboCount;
        private int lastTargetId;
        private boolean crit;
        private boolean killed;

        public HitMarker() {
            super("hitmarker", "Хитмаркер", "Крестик при попадании и счётчик комбо", Category.COMBAT, false);
        }

        public void onHit(Player p, Entity target) {
            if (target instanceof LivingEntity l) {
                long now = System.currentTimeMillis();
                this.comboCount = now - this.comboAt < 1600L ? this.comboCount + 1 : 1;
                this.comboAt = now;
                this.hitAt = now;
                this.crit = CombatFx.critical(p);
                this.killed = false;
                this.lastTargetId = target.getId();
                if (this.sound.on() && !Modules.HITSOUND.enabled() && CombatFx.mc().level != null) {
                    CombatFx.mc()
                            .level
                            .playLocalSound(
                                    p.getX(),
                                    p.getY(),
                                    p.getZ(),
                                    SoundEvents.NOTE_BLOCK_HAT.value(),
                                    SoundSource.PLAYERS,
                                    0.35F,
                                    this.crit ? 1.9F : 1.6F,
                                    false);
                }
            }
        }

        public void tick() {
            if (this.hitAt > 0L
                    && !this.killed
                    && System.currentTimeMillis() - this.hitAt < 600L
                    && CombatFx.mc().level != null) {
                Entity e = CombatFx.mc().level.getEntity(this.lastTargetId);
                if (e instanceof LivingEntity) {
                    LivingEntity l = (LivingEntity) e;
                    if (!l.isAlive() || l.getHealth() <= 0.0F) {
                        this.killed = true;
                        this.hitAt = System.currentTimeMillis();
                    }
                }
            }
        }

        public void render(GuiGraphics g) {
            long now = System.currentTimeMillis();
            int cx = Px.width() / 2;
            int cy = Px.height() / 2;
            float u = (float) Px.height() / 1080.0F * this.size.f();
            float t = (float) (now - this.hitAt) / (this.killed ? 600.0F : 340.0F);
            if (this.hitAt > 0L && t < 1.0F) {
                float pop = t < 0.18F ? 1.35F - 0.35F * (t / 0.18F) : 1.0F;
                float a = t < 0.55F ? 1.0F : 1.0F - (t - 0.55F) / 0.45F;
                int col = this.killed ? -50354 : (this.crit ? -11930 : -1);
                int gap = Math.round(9.0F * u * pop);
                int len = Math.round(13.0F * u * pop);
                int th = Math.max(3, Math.round(3.2F * u));
                Px.shadow(
                        g,
                        cx - gap - len,
                        cy - gap - len,
                        (gap + len) * 2,
                        (gap + len) * 2,
                        Math.round(10.0F * u),
                        Draw.am(col, a * 0.25F));

                for (int i = 0; i < 4; ++i) {
                    double ang = (Math.PI / 4D) + (double) i * Math.PI / 2.0 + (this.killed ? (double) t * 0.6 : 0.0);

                    for (int s = 0; s <= len; ++s) {
                        int x = Math.round((float) ((double) cx + Math.cos(ang) * (double) (gap + s)));
                        int y = Math.round((float) ((double) cy + Math.sin(ang) * (double) (gap + s)));
                        Px.circle(g, x, y, Math.max(1, th / 2), Draw.am(0xFF000000, a * 0.35F));
                    }

                    for (int s = 0; s <= len; ++s) {
                        int x = Math.round((float) ((double) cx + Math.cos(ang) * (double) (gap + s)));
                        int y = Math.round((float) ((double) cy + Math.sin(ang) * (double) (gap + s)));
                        Px.circle(g, x, y, Math.max(1, th / 2 - (s == len ? 1 : 0)), Draw.am(col, a));
                    }
                }
            }

            float ct = (float) (now - this.comboAt) / 1600.0F;
            if (this.combo.on() && this.comboCount >= 2 && ct < 1.0F) {
                float pop = Math.min(1.0F, (float) (now - this.comboAt) / 140.0F);
                float scale = 1.0F + 0.45F * (1.0F - Anim.easeOutCubic(pop));
                float a = ct < 0.7F ? 1.0F : 1.0F - (ct - 0.7F) / 0.3F;
                int col = this.comboCount >= 10 ? -50354 : (this.comboCount >= 5 ? -20448 : Theme.accent2());
                String txt = "x" + this.comboCount;
                int px = Math.round(30.0F * u);
                g.pose().pushMatrix();
                g.pose().translate((float) cx + 48.0F * u, (float) cy + 18.0F * u);
                g.pose().scale(scale, scale);
                float old = Px.fade;
                Px.fade = old * a;
                int tw = Px.textWidth(Lang.tr(txt), Px.Weight.BOLD, px);
                Px.shadow(
                        g,
                        -Math.round(6.0F * u),
                        -px / 2,
                        tw + Math.round(12.0F * u),
                        Math.round((float) px * 1.6F),
                        Math.round(14.0F * u),
                        Draw.am(0xFF000000, a * 0.45F));
                Px.textShadow(g, Lang.tr(txt), 0, -px / 2, Px.Weight.BOLD, px, col);
                Px.text(
                        g,
                        Lang.tr("КОМБО"),
                        0,
                        Math.round((float) px * 0.62F),
                        Px.Weight.BOLD,
                        Math.round(12.0F * u),
                        Draw.am(col, 0.8F));
                Px.fade = old;
                g.pose().popMatrix();
            }
        }
    }

    public static final class TargetMarker extends Module {
        public final Setting.Mode style = this.add(new Setting.Mode("style", "Стиль", "Дуги", new String[] {
            "Дуги", "Трилистник", "Ромбы", "Прицел", "Треугольник", "Спутники", "Ореол"
        }));
        public final Setting.Num size =
                (Setting.Num) this.add(new Setting.Num("size", "Размер", 1.0, 0.5, 2.0, 0.05, "×"));
        public final Setting.Num speed =
                (Setting.Num) this.add(new Setting.Num("speed", "Скорость", 1.0, 0.0, 4.0, 0.05, "×"));
        public final Setting.Bool hurtTint = this.add(new Setting.Bool("hurt_tint", "Краснеть при ударе", true));
        final Setting.Mode colourMode = (Setting.Mode)
                this.add(new Setting.Mode("color_mode", "Окраска", "Тема", new String[] {"Тема", "Радуга", "Цвет"}));
        final Setting.Colour colour =
                (this.add(new Setting.Colour("color", "Цвет", false))).showIf(() -> this.colourMode.is("Цвет"));
        private final long start = System.currentTimeMillis();

        public TargetMarker() {
            super(
                    "target_marker",
                    "Маркер цели",
                    "Target ESP: дуги, трилистник, ромбы, прицел, треугольник, спутники и ореол",
                    Category.COMBAT,
                    false);
            this.colour.set(-770210);
        }

        public int colour(LivingEntity t, int offset, float alpha) {
            int c = this.base(offset);
            if (this.hurtTint.on() && t.hurtTime > 0) {
                c = Fx.lerp(c, -65536 | c & 0xFF000000, Math.min(1.0F, (float) t.hurtTime / 10.0F));
            }

            return TargetEsp.alpha(c, alpha);
        }

        private int base(int offset) {
            long now = System.currentTimeMillis();
            if (this.colourMode.is("Цвет")) {
                return (Integer) this.colour.get();
            } else if (this.colourMode.is("Радуга")) {
                float hue = ((float) (now % 4000L) / 4000.0F + (float) offset / 360.0F) % 1.0F;
                return 0xFF000000 | Color.HSBtoRGB(hue, 0.85F, 1.0F);
            } else {
                float phase = ((float) (now - this.start) / 4.0F + (float) offset) % 360.0F;
                if (phase >= 180.0F) {
                    phase = 360.0F - phase;
                }

                return Fx.lerp(Theme.accent(), Theme.accent2(), phase * 2.0F / 360.0F);
            }
        }
    }
}
