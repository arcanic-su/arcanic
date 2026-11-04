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

import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.render.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class VisualFx {
    private VisualFx() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static int colourFor(String mode, Setting.Colour c, float offset) {
        int n;
        switch (mode) {
            case "Радуга" -> n = Draw.rainbow(offset, 1.0F);
            case "Градиент" ->
                n = Draw.mix(Theme.accent(), Theme.accent2(), (float)
                        (Math.sin((double) System.currentTimeMillis() / 300.0 + (double) (offset * 6.0F)) * 0.5 + 0.5));
            default -> n = c.argb();
        }

        return n;
    }

    public static final class JumpCircles extends Module {
        final Setting.Mode style = this.add(new Setting.Mode(
                "style",
                "Стиль",
                "Неон",
                "Неон",
                "Волна",
                "Точки",
                "Солнце",
                "Arc Storm",
                "Arc Pool",
                "Arc Rings",
                "Arc Explosion"));
        final Setting.Mode colourMode =
                this.add(new Setting.Mode("color_mode", "Окраска", "Градиент", "Градиент", "Радуга", "Цвет"));
        final Setting.Colour colour =
                (this.add(new Setting.Colour("color", "Цвет", true))).showIf(() -> this.colourMode.is("Цвет"));
        final Setting.Num radius = this.add(new Setting.Num("radius", "Радиус", 1.2, 0.4, 3.0, 0.05, " бл"));
        final Setting.Num life = this.add(new Setting.Num("life", "Время", 1.0, 0.3, 2.5, 0.05, " с"));
        final Setting.Bool spin = this.add(new Setting.Bool("spin", "Вращение", true));
        final Setting.Bool landing = this.add(new Setting.Bool("landing", "Кольцо при приземлении", true));
        final Setting.Bool motes = this.add(new Setting.Bool("motes", "Искры", true));
        private boolean wasOnGround = true;
        private boolean jumped;
        private double airTop;

        public JumpCircles() {
            super("jump_circles", "Круги от прыжка", "Светящееся кольцо на земле при прыжке", Category.EFFECTS, false);
        }

        public void tick() {
            LocalPlayer p = VisualFx.mc().player;
            if (p != null) {
                Vec3 feet = new Vec3(p.getX(), p.getY() + 0.03, p.getZ());
                if (this.wasOnGround && !p.onGround()) {
                    this.jumped = p.getDeltaMovement().y > 0.2;
                    this.airTop = p.getY();
                    if (this.jumped) {
                        Effects.jumpRing(
                                this.style.get(),
                                feet,
                                VisualFx.colourFor(this.colourMode.get(), this.colour, 0.0F),
                                this.radius.f(),
                                this.life.f(),
                                this.spin.on(),
                                this.motes.on());
                    }
                }

                if (!p.onGround()) {
                    this.airTop = Math.max(this.airTop, p.getY());
                }

                if (!this.wasOnGround
                        && p.onGround()
                        && this.landing.on()
                        && (this.jumped || this.airTop - p.getY() > 0.5)) {
                    Effects.jumpRing(
                            this.style.get(),
                            feet,
                            VisualFx.colourFor(this.colourMode.get(), this.colour, 0.5F),
                            this.radius.f() * 0.7F,
                            this.life.f() * 0.7F,
                            this.spin.on(),
                            false);
                }

                if (p.onGround()) {
                    this.jumped = false;
                }

                this.wasOnGround = p.onGround();
            }
        }
    }

    public static final class Footprints extends Module {
        final Setting.Mode colourMode =
                this.add(new Setting.Mode("color_mode", "Окраска", "Градиент", "Градиент", "Радуга", "Цвет"));
        final Setting.Colour colour =
                (this.add(new Setting.Colour("color", "Цвет", true))).showIf(() -> this.colourMode.is("Цвет"));
        final Setting.Num life = this.add(new Setting.Num("life", "Время", 3.0, 1.0, 8.0, 0.5, " с"));
        final Setting.Num size = this.add(new Setting.Num("size", "Размер", 1.0, 0.6, 1.6, 0.05, "×"));
        private double walked;
        private boolean left;
        private Vec3 last;
        private int groundTicks;

        public Footprints() {
            super("footprints", "Следы шагов", "Светящиеся отпечатки ног на земле", Category.EFFECTS, false);
        }

        public void tick() {
            LocalPlayer p = VisualFx.mc().player;
            if (p == null) {
                this.last = null;
            } else {
                Vec3 now = p.position();
                if (p.onGround()) {
                    this.groundTicks = 0;
                } else {
                    ++this.groundTicks;
                }

                if (this.last != null && !p.isSpectator() && this.groundTicks < 4) {
                    this.walked += Math.sqrt(Math.pow(now.x - this.last.x, 2.0) + Math.pow(now.z - this.last.z, 2.0));
                }

                this.last = now;
                if (!(this.walked < 0.62) && this.groundTicks <= 1) {
                    this.walked = 0.0;
                    this.left = !this.left;
                    double yaw = Math.toRadians((double) p.getYRot());
                    double sx = Math.cos(yaw) * (this.left ? 0.14 : -0.14);
                    double sz = Math.sin(yaw) * (this.left ? 0.14 : -0.14);
                    int c = VisualFx.colourFor(this.colourMode.get(), this.colour, this.left ? 0.0F : 0.5F);
                    Vec3 at = new Vec3(now.x + sx, now.y + 0.045, now.z + sz);
                    Fx.spawn()
                            .at(at.add(0.0, -0.005, 0.0))
                            .tex(Fx.Tex.GLOW)
                            .mode(Fx.Mode.FLAT)
                            .size(0.36F * this.size.f(), 0.36F * this.size.f())
                            .life(this.life.f() * 0.8F)
                            .drag(0.0F)
                            .fadeIn(0.1F)
                            .colour(0x96000000 | c & 0xFFFFFF, c & 0xFFFFFF);
                    Fx.spawn()
                            .at(at)
                            .tex(Fx.Tex.FOOT)
                            .mode(Fx.Mode.FLAT)
                            .blend(false)
                            .size(0.24F * this.size.f(), 0.24F * this.size.f())
                            .spin((float) yaw, 0.0F)
                            .life(this.life.f())
                            .drag(0.0F)
                            .fadeIn(0.08F)
                            .colour(0xEB000000 | c & 0xFFFFFF, c & 0xFFFFFF);
                }
            }
        }
    }

    public static final class HandView extends Module {
        public final Setting.Mode preset = this.add(
                new Setting.Mode("preset", "Пресет", "Свой", "Свой", "Компактный", "Низкий", "По центру", "PvP"));
        public final Setting.Num x = this.add(new Setting.Num("x", "Сдвиг X", 0.0, -1.0, 1.0, 0.01, ""));
        public final Setting.Num y = this.add(new Setting.Num("y", "Сдвиг Y", 0.0, -1.0, 1.0, 0.01, ""));
        public final Setting.Num z = this.add(new Setting.Num("z", "Сдвиг Z", 0.0, -1.0, 1.0, 0.01, ""));
        public final Setting.Num rx = this.add(new Setting.Num("rx", "Поворот X", 0.0, -180.0, 180.0, 1.0, "°"));
        public final Setting.Num ry = this.add(new Setting.Num("ry", "Поворот Y", 0.0, -180.0, 180.0, 1.0, "°"));
        public final Setting.Num rz = this.add(new Setting.Num("rz", "Поворот Z", 0.0, -180.0, 180.0, 1.0, "°"));
        public final Setting.Num scale = this.add(new Setting.Num("scale", "Размер", 1.0, 0.3, 1.8, 0.01, "×"));
        public final Setting.Bool both = this.add(new Setting.Bool("both", "Обе руки одинаково", true));
        // the left hand on its own, when the hands are not set the same
        public final Setting.Num lx = this.add(new Setting.Num("lx", "Левая: сдвиг X", 0.0, -1.0, 1.0, 0.01, ""))
                .showIf(() -> !both.on());
        public final Setting.Num ly = this.add(new Setting.Num("ly", "Левая: сдвиг Y", 0.0, -1.0, 1.0, 0.01, ""))
                .showIf(() -> !both.on());
        public final Setting.Num lz = this.add(new Setting.Num("lz", "Левая: сдвиг Z", 0.0, -1.0, 1.0, 0.01, ""))
                .showIf(() -> !both.on());
        public final Setting.Num lrx = this.add(
                        new Setting.Num("lrx", "Левая: поворот X", 0.0, -180.0, 180.0, 1.0, "°"))
                .showIf(() -> !both.on());
        public final Setting.Num lry = this.add(
                        new Setting.Num("lry", "Левая: поворот Y", 0.0, -180.0, 180.0, 1.0, "°"))
                .showIf(() -> !both.on());
        public final Setting.Num lrz = this.add(
                        new Setting.Num("lrz", "Левая: поворот Z", 0.0, -180.0, 180.0, 1.0, "°"))
                .showIf(() -> !both.on());
        public final Setting.Num lscale = this.add(new Setting.Num("lscale", "Левая: размер", 1.0, 0.3, 1.8, 0.01, "×"))
                .showIf(() -> !both.on());

        public HandView() {
            super("hand_view", "Вид рук", "Положение, поворот и размер предмета в руке", Category.HANDS, false);
        }

        public void applyPreset() {
            double[] v =
                    switch (this.preset.get()) {
                        case "Компактный" -> new double[] {0.06, 0.02, -0.1, 0.0, 0.0, 0.0, 0.78};
                        case "Низкий" -> new double[] {0.0, -0.18, 0.0, 0.0, 0.0, 0.0, 1.0};
                        case "По центру" -> new double[] {-0.22, -0.04, -0.1, 0.0, -8.0, 0.0, 0.9};
                        case "PvP" -> new double[] {0.08, -0.06, -0.12, 0.0, 6.0, 0.0, 0.72};
                        default -> null;
                    };
            if (v != null) {
                this.x.set(v[0]);
                this.y.set(v[1]);
                this.z.set(v[2]);
                this.rx.set(v[3]);
                this.ry.set(v[4]);
                this.rz.set(v[5]);
                this.scale.set(v[6]);
            }
        }
    }
}
