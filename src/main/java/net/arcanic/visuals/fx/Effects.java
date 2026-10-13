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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.fx.Fx.Mode;
import net.arcanic.visuals.fx.Fx.Tex;
import net.minecraft.world.phys.Vec3;

/** Ready-made effects built from {@link Fx} particles: hit bursts, crit flashes, kill effects, rings. */
public final class Effects {
    private Effects() {}

    static Random r() {
        return Fx.rnd();
    }

    static float rf(float a, float b) {
        return a + r().nextFloat() * (b - a);
    }

    static int a(int c, int alpha) {
        return (alpha << 24) | (c & 0xFFFFFF);
    }

    static int white(int c, float k) {
        return Fx.lerp(c | 0xFF000000, 0xFFFFFFFF, k);
    }

    private record Emitter(Runnable step, int[] left) {}

    private static final List<Emitter> emitters = new ArrayList<>();

    static void every(int ticks, Runnable step) {
        emitters.add(new Emitter(step, new int[] {ticks}));
    }

    public static void tick() {
        for (Iterator<Emitter> it = emitters.iterator(); it.hasNext(); ) {
            Emitter e = it.next();
            e.step.run();
            if (--e.left[0] <= 0) it.remove();
        }
    }

    static void flash(Vec3 at, int c, float size) {
        Fx.spawn()
                .at(at)
                .tex(Tex.GLOW)
                .size(size * 1.9f, size * .6f)
                .life(.38f)
                .colour(a(c, 200), a(c, 0))
                .fadeIn(0);
        Fx.spawn()
                .at(at)
                .tex(Tex.GLOW)
                .size(size * .8f, size * .1f)
                .life(.22f)
                .colour(0xFFFFFFFF, a(white(c, .7f), 0))
                .fadeIn(0);
    }

    static int second(int c) {
        return c == Theme.accent2() ? Theme.accent() : Fx.lerp(c, 0xFFFFFFFF, .35f);
    }

    static void sparks(Vec3 at, int c, int n, float speed, float size) {
        for (int i = 0; i < n; i++) {
            Vec3 d = Fx.dir();
            float sp = speed * rf(.45f, 1.15f);
            Fx.spawn()
                    .at(at)
                    .vel(d.x * sp, d.y * sp * .8 + speed * .25, d.z * sp)
                    .tex(Tex.SPARK)
                    .mode(Mode.STRETCH)
                    .size(size * rf(.9f, 1.5f), 0)
                    .life(rf(.4f, .75f))
                    .drag(2.6f)
                    .gravity(3.5f)
                    .colour(white(i % 3 == 0 ? second(c) : c, .55f), a(i % 3 == 0 ? second(c) : c, 0))
                    .fadeIn(0);
        }
    }

    static void motes(Vec3 at, int c, int n, float spread, float size, float life) {
        for (int i = 0; i < n; i++) {
            Vec3 d = Fx.dir();
            Fx.spawn()
                    .at(at.add(d.scale(spread * r().nextFloat())))
                    .vel(d.x * .4, .3 + r().nextFloat() * .5, d.z * .4)
                    .tex(Tex.GLOW)
                    .size(size * rf(.8f, 1.5f), 0)
                    .life(life * rf(.9f, 1.4f))
                    .drag(1.5f)
                    .colour(white(c, .3f), a(c, 0));
        }
    }

    static void shockwave(Vec3 at, int c, float size, float life, boolean flat) {
        Fx.spawn()
                .at(at)
                .tex(Tex.SHOCK)
                .mode(flat ? Mode.FLAT : Mode.BILLBOARD)
                .size(size * .15f, size)
                .life(life)
                .colour(white(c, .4f), a(c, 0))
                .fadeIn(0);
    }

    public static void hit(String style, Vec3 at, int c, float power, boolean crit) {
        float p = power * (crit ? 1.5f : 1);
        switch (style) {
            case "Звёзды" -> {
                flash(at, c, .5f * p);
                for (int i = 0; i < (int) (10 * p); i++) {
                    Vec3 d = Fx.dir();
                    float sp = rf(1.2f, 2.8f) * p;
                    Fx.spawn()
                            .at(at)
                            .vel(d.x * sp, d.y * sp + .6, d.z * sp)
                            .tex(Tex.STAR)
                            .twinkle(.6f)
                            .size(rf(.18f, .3f) * p, 0)
                            .spin(rf(0, 6), rf(-6, 6))
                            .life(rf(.7f, 1.1f))
                            .drag(3.5f)
                            .gravity(1.2f)
                            .colour(white(c, .5f), a(c, 0));
                }
            }
            case "Сердца" -> {
                for (int i = 0; i < (int) (7 * p); i++) {
                    Vec3 d = Fx.dir();
                    int col = r().nextInt(3) == 0 ? 0xFFFF8FB8 : 0xFFFF4D7A;
                    Fx.spawn()
                            .at(at.add(d.scale(.25)))
                            .vel(d.x * 1.2, 1.2 + r().nextFloat(), d.z * 1.2)
                            .tex(Tex.HEART)
                            .blend(false)
                            .size(rf(.14f, .22f) * p, .05f)
                            .spin(rf(-.4f, .4f), rf(-1, 1))
                            .life(rf(.7f, 1.1f))
                            .drag(2.2f)
                            .gravity(-.3f)
                            .colour(col, a(col, 0));
                }
            }
            case "Волна" -> {
                shockwave(at, c, 1.5f * p, .5f, false);
                shockwave(at, c, .9f * p, .4f, false);
                flash(at, c, .45f * p);
                sparks(at, c, (int) (8 * p), 4f * p, .07f);
            }
            case "Разрез" -> {
                Fx.spawn()
                        .at(at)
                        .tex(Tex.SLASH)
                        .size(.8f * p, 1.05f * p)
                        .spin(rf(0, 6.28f), rf(-2, 2))
                        .life(.32f)
                        .colour(white(c, .5f), a(c, 0))
                        .fadeIn(0);
                sparks(at, c, (int) (10 * p), 5f * p, .06f);
            }
            case "Магия" -> {
                flash(at, c, .4f * p);
                for (int i = 0; i < (int) (16 * p); i++) {
                    double ang = Math.PI * 2 * i / (16 * p), rad = .15;
                    Fx.spawn()
                            .at(at.add(Math.cos(ang) * rad, rf(-.3f, .3f), Math.sin(ang) * rad))
                            .vel(Math.cos(ang) * 1.6 * p, rf(.4f, 1.6f), Math.sin(ang) * 1.6 * p)
                            .tex(Tex.GLOW)
                            .size(rf(.1f, .18f) * p, 0)
                            .life(rf(.7f, 1.2f))
                            .drag(2.6f)
                            .gravity(-.6f)
                            .colour(white(c, .3f), a(Fx.lerp(c, 0xFFFFFFFF, .5f), 0));
                }
            }
            default -> { // Sparks
                flash(at, c, .55f * p);
                sparks(at, c, (int) (22 * p), 6f * p, .085f);
                motes(at, c, (int) (8 * p), .25f, .1f, .8f);
            }
        }
        if (crit) {
            shockwave(at, Fx.lerp(c, 0xFFFFFFFF, .3f), 1.3f, .4f, false);
            flash(at, 0xFFFFFFFF, .7f);
            for (int i = 0; i < 6; i++) {
                Vec3 d = Fx.dir();
                Fx.spawn()
                        .at(at)
                        .vel(d.x * 3, d.y * 3, d.z * 3)
                        .tex(Tex.STAR)
                        .size(.16f, 0)
                        .spin(rf(0, 6), rf(-8, 8))
                        .life(.45f)
                        .drag(4)
                        .colour(0xFFFFFFFF, a(c, 0));
            }
        }
    }

    public static void kill(String style, Vec3 feet, float height, int c) {
        Vec3 mid = feet.add(0, height * .5, 0);
        switch (style) {
            case "Взрыв" -> {
                shockwave(feet.add(0, .05, 0), c, 4f, .9f, true);
                shockwave(mid, c, 2.2f, .45f, false);
                flash(mid, c, 3f);
                sparks(mid, c, 70, 10f, .13f);
                motes(mid, c, 20, .6f, .16f, 1.2f);
                for (int i = 0; i < 14; i++) {
                    Vec3 d = Fx.dir();
                    Fx.spawn()
                            .at(mid.add(d.scale(.3)))
                            .vel(d.x * 1.5, Math.abs(d.y) * 1.2 + .3, d.z * 1.5)
                            .tex(Tex.SMOKE)
                            .blend(false)
                            .size(rf(.4f, .7f), rf(1.1f, 1.6f))
                            .spin(rf(0, 6), rf(-1, 1))
                            .life(rf(.9f, 1.4f))
                            .drag(2)
                            .colour(0x90303038, 0x00303038);
                }
            }
            case "Вознесение" -> {
                shockwave(feet.add(0, .05, 0), c, 2.6f, 1.1f, true);
                every(36, () -> {
                    for (int i = 0; i < 3; i++) {
                        double ang = r().nextDouble() * Math.PI * 2, rad = rf(.1f, .6f);
                        Fx.spawn()
                                .at(feet.add(Math.cos(ang) * rad, rf(0, .4f), Math.sin(ang) * rad))
                                .vel(0, rf(2, 4), 0)
                                .tex(r().nextBoolean() ? Tex.STAR : Tex.GLOW)
                                .size(rf(.08f, .16f), 0)
                                .spin(0, rf(-4, 4))
                                .life(rf(.8f, 1.3f))
                                .drag(.6f)
                                .colour(white(c, .4f), a(c, 0));
                    }
                });
                Fx.spawn()
                        .at(mid)
                        .vel(0, 1.4, 0)
                        .tex(Tex.GLOW)
                        .size(1.3f, .3f)
                        .life(2f)
                        .drag(.2f)
                        .colour(white(c, .5f), a(c, 0));
            }
            case "Конфетти" -> {
                int[] cols = {0xFFF43F5E, 0xFFFACC15, 0xFF4ADE80, 0xFF22D3EE, 0xFFA855F7, 0xFFFB923C};
                for (int i = 0; i < 70; i++) {
                    Vec3 d = Fx.dir();
                    int col = cols[r().nextInt(cols.length)];
                    Fx.spawn()
                            .at(mid)
                            .vel(d.x * rf(2, 5), Math.abs(d.y) * rf(3, 6), d.z * rf(2, 5))
                            .tex(r().nextBoolean() ? Tex.STAR : Tex.GLOW)
                            .size(rf(.07f, .13f), .04f)
                            .spin(rf(0, 6), rf(-10, 10))
                            .life(rf(1.2f, 1.8f))
                            .drag(1.8f)
                            .gravity(4f)
                            .colour(col, a(col, 0));
                }
                flash(mid, 0xFFFFFFFF, 1.2f);
            }
            case "Молния" -> {
                flash(mid, 0xFFBFDBFE, 3f);
                shockwave(feet.add(0, .05, 0), 0xFF93C5FD, 2.6f, .5f, true);
                sparks(mid, 0xFF93C5FD, 40, 8f, .09f);
            }
            default -> { // Soul
                shockwave(feet.add(0, .05, 0), c, 2.8f, 1f, true);
                shockwave(feet.add(0, .05, 0), c, 1.6f, .8f, true);
                flash(mid, c, 2.2f);
                motes(mid, c, 24, .5f, .14f, 1.4f);
                // an orb that rises, leaving a glowing trail
                double[] y = {height * .5};
                every(30, () -> {
                    y[0] += .09;
                    Vec3 at = feet.add(Math.sin(y[0] * 3) * .15, y[0], Math.cos(y[0] * 3) * .15);
                    Fx.spawn()
                            .at(at)
                            .tex(Tex.GLOW)
                            .size(.6f, .08f)
                            .life(.8f)
                            .colour(white(c, .5f), a(c, 0))
                            .fadeIn(0);
                    Fx.spawn()
                            .at(at)
                            .vel(rf(-.3f, .3f), -.2, rf(-.3f, .3f))
                            .tex(Tex.STAR)
                            .size(.1f, 0)
                            .spin(0, 5)
                            .life(.6f)
                            .colour(0xFFFFFFFF, a(c, 0));
                });
            }
        }
    }

    public static void jumpRing(String style, Vec3 at, int c, float radius, float life, boolean spin, boolean motes) {
        if (ArcEffects.jump(style, at, c, radius, life, spin)) return;
        Tex t =
                switch (style) {
                    case "Волна" -> Tex.RING_WAVE;
                    case "Точки" -> Tex.RING_DOTS;
                    case "Солнце" -> Tex.RING_SUN;
                    default -> Tex.RING_NEON;
                };
        Fx.spawn()
                .at(at)
                .tex(t)
                .mode(Mode.FLAT)
                .size(radius * .25f, radius)
                .spin(rf(0, 6.28f), spin ? rf(1.2f, 2f) * (r().nextBoolean() ? 1 : -1) : 0)
                .life(life)
                .drag(0)
                .colour(white(c, .25f), a(c, 0))
                .fadeIn(.06f);
        Fx.spawn()
                .at(at.add(0, -.005, 0))
                .tex(Tex.GLOW)
                .mode(Mode.FLAT)
                .size(radius * .5f, radius * 1.1f)
                .life(life * .8f)
                .drag(0)
                .colour(a(c, 90), a(c, 0))
                .fadeIn(.05f);
        Fx.spawn()
                .at(at.add(0, .01, 0))
                .tex(Tex.RING_NEON)
                .mode(Mode.FLAT)
                .size(radius * .05f, radius * .62f)
                .spin(0, spin ? -2.5f : 0)
                .life(life * .85f)
                .drag(0)
                .colour(a(second(c), 230), a(second(c), 0))
                .fadeIn(.18f);
        if (motes)
            for (int i = 0; i < 10; i++) {
                double ang = Math.PI * 2 * i / 10;
                Fx.spawn()
                        .at(at.add(Math.cos(ang) * radius * .5, .05, Math.sin(ang) * radius * .5))
                        .vel(Math.cos(ang) * radius * 1.2, rf(.3f, .9f), Math.sin(ang) * radius * 1.2)
                        .tex(Tex.GLOW)
                        .size(.09f, 0)
                        .life(life * .8f)
                        .drag(2.5f)
                        .colour(white(c, .4f), a(c, 0));
            }
    }
}
