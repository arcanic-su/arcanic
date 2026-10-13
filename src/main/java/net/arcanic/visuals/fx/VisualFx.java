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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/** Movement visuals: trails behind the player and circles on jumping; plus the custom crosshair and the hand view. */
public final class VisualFx {
    private VisualFx() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static int colourFor(String mode, Setting.Colour c, float offset) {
        return switch (mode) {
            case "Радуга" -> Draw.rainbow(offset, 1);
            case "Градиент" ->
                Draw.mix(Theme.accent(), Theme.accent2(), (float)
                        (Math.sin(System.currentTimeMillis() / 300.0 + offset * 6) * .5 + .5));
            default -> c.argb();
        };
    }

    /** Trails: a glowing ribbon behind you (rendered in the world), or particles of the chosen kind. */
    /** Jump circles: a glowing ring spreading over the ground where you jump (and land). */
    public static final class JumpCircles extends Module {
        final Setting.Mode style = add(new Setting.Mode(
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
                add(new Setting.Mode("color_mode", "Окраска", "Градиент", "Градиент", "Радуга", "Цвет"));
        final Setting.Colour colour =
                add(new Setting.Colour("color", "Цвет", true)).showIf(() -> colourMode.is("Цвет"));
        final Setting.Num radius = add(new Setting.Num("radius", "Радиус", 1.2, .4, 3, .05, " бл"));
        final Setting.Num life = add(new Setting.Num("life", "Время", 1, .3, 2.5, .05, " с"));
        final Setting.Bool spin = add(new Setting.Bool("spin", "Вращение", true));
        final Setting.Bool landing = add(new Setting.Bool("landing", "Кольцо при приземлении", true));
        final Setting.Bool motes = add(new Setting.Bool("motes", "Искры", true));
        private boolean wasOnGround = true, jumped;
        private double airTop;

        public JumpCircles() {
            super("jump_circles", "Круги от прыжка", "Светящееся кольцо на земле при прыжке", Category.EFFECTS, false);
        }

        @Override
        public void tick() {
            var p = mc().player;
            if (p == null) return;
            Vec3 feet = new Vec3(p.getX(), p.getY() + .03, p.getZ());
            if (wasOnGround && !p.onGround()) {
                jumped = p.getDeltaMovement().y > .2;
                airTop = p.getY();
                if (jumped)
                    Effects.jumpRing(
                            style.get(),
                            feet,
                            colourFor(colourMode.get(), colour, 0),
                            radius.f(),
                            life.f(),
                            spin.on(),
                            motes.on());
            }
            if (!p.onGround()) airTop = Math.max(airTop, p.getY());
            // landing ring only after a real jump or a fall of half a block or more (not on every small step)
            if (!wasOnGround && p.onGround() && landing.on() && (jumped || airTop - p.getY() > .5))
                Effects.jumpRing(
                        style.get(),
                        feet,
                        colourFor(colourMode.get(), colour, .5f),
                        radius.f() * .7f,
                        life.f() * .7f,
                        spin.on(),
                        false);
            if (p.onGround()) jumped = false;
            wasOnGround = p.onGround();
        }
    }

    /** Halo: a glowing ring floating over your head, gently bobbing and tilting, with sparks circling it. */
    public static final class Halo extends Module {
        public final Setting.Mode colourMode =
                add(new Setting.Mode("color_mode", "Окраска", "Градиент", "Градиент", "Радуга", "Цвет"));
        public final Setting.Colour colour =
                add(new Setting.Colour("color", "Цвет", true)).showIf(() -> colourMode.is("Цвет"));
        public final Setting.Num size = add(new Setting.Num("size", "Размер", 1, .5, 1.8, .05, "×"));
        public final Setting.Num height = add(new Setting.Num("height", "Высота", .42, .2, .9, .01, " бл"));
        public final Setting.Bool sparks = add(new Setting.Bool("sparks", "Искры вокруг", true));

        public Halo() {
            super("halo", "Нимб", "Светящееся кольцо над головой (виден в 3-м лице)", Category.EFFECTS, false);
        }

        public int colourAt(float f) {
            return colourFor(colourMode.get(), colour, f);
        }
    }

    /** Footprints: glowing footprints left on the ground as you walk; they fade after a few seconds. */
    public static final class Footprints extends Module {
        final Setting.Mode colourMode =
                add(new Setting.Mode("color_mode", "Окраска", "Градиент", "Градиент", "Радуга", "Цвет"));
        final Setting.Colour colour =
                add(new Setting.Colour("color", "Цвет", true)).showIf(() -> colourMode.is("Цвет"));
        final Setting.Num life = add(new Setting.Num("life", "Время", 3, 1, 8, .5, " с"));
        final Setting.Num size = add(new Setting.Num("size", "Размер", 1, .6, 1.6, .05, "×"));
        private double walked;
        private boolean left;
        private Vec3 last;
        private int groundTicks;

        public Footprints() {
            super("footprints", "Следы шагов", "Светящиеся отпечатки ног на земле", Category.EFFECTS, false);
        }

        @Override
        public void tick() {
            var p = mc().player;
            if (p == null) {
                last = null;
                return;
            }
            Vec3 now = p.position();
            if (p.onGround()) groundTicks = 0;
            else groundTicks++;
            if (last != null && !p.isSpectator() && groundTicks < 4)
                walked += Math.sqrt(Math.pow(now.x - last.x, 2) + Math.pow(now.z - last.z, 2));
            last = now;
            if (walked < .62 || groundTicks > 1) return;
            walked = 0;
            left = !left;
            double yaw = Math.toRadians(p.getYRot());
            double sx = Math.cos(yaw) * (left ? .14 : -.14), sz = Math.sin(yaw) * (left ? .14 : -.14);
            int c = colourFor(colourMode.get(), colour, left ? 0 : .5f);
            Vec3 at = new Vec3(now.x + sx, now.y + .045, now.z + sz);
            // a soft glow under the print (for the dark), and the print itself with normal transparency (for the day)
            Fx.spawn()
                    .at(at.add(0, -.005, 0))
                    .tex(Fx.Tex.GLOW)
                    .mode(Fx.Mode.FLAT)
                    .size(.36f * size.f(), .36f * size.f())
                    .life(life.f() * .8f)
                    .drag(0)
                    .fadeIn(.1f)
                    .colour((150 << 24) | (c & 0xFFFFFF), c & 0xFFFFFF);
            Fx.spawn()
                    .at(at)
                    .tex(Fx.Tex.FOOT)
                    .mode(Fx.Mode.FLAT)
                    .blend(false)
                    .size(.24f * size.f(), .24f * size.f())
                    .spin((float) yaw, 0)
                    .life(life.f())
                    .drag(0)
                    .fadeIn(.08f)
                    .colour((235 << 24) | (c & 0xFFFFFF), c & 0xFFFFFF);
        }
    }

    /** Hat: a translucent cone over your head with a bright rim, colours running around it. */
    public static final class Hat extends Module {
        public final Setting.Mode colourMode =
                add(new Setting.Mode("color_mode", "Окраска", "Градиент", "Градиент", "Радуга", "Цвет"));
        public final Setting.Colour colour =
                add(new Setting.Colour("color", "Цвет", true)).showIf(() -> colourMode.is("Цвет"));
        public final Setting.Num size = add(new Setting.Num("size", "Размер", 1, .6, 1.6, .05, "×"));
        public final Setting.Num opacity = add(new Setting.Num("opacity", "Прозрачность", .45, .15, .9, .05, ""));
        public final Setting.Bool spin = add(new Setting.Bool("spin", "Переливается", true));

        public Hat() {
            super("hat", "Шляпа", "Полупрозрачный конус над головой (в 3-м лице)", Category.EFFECTS, false);
        }

        public int colourAt(float f) {
            return colourFor(colourMode.get(), colour, f);
        }
    }

    /** Atmosphere: your own time of day and a colour tint over the fog and the sky. */
    public static final class Atmosphere extends Module {
        public final Setting.Mode time =
                add(new Setting.Mode("time", "Время", "Сервер", "Сервер", "Рассвет", "День", "Закат", "Ночь", "Своё"));
        public final Setting.Num hour =
                add(new Setting.Num("hour", "Час", 20, 0, 24, .25, " ч")).showIf(() -> time.is("Своё"));
        public final Setting.Bool tint = add(new Setting.Bool("tint", "Окрасить мир", true));
        public final Setting.Colour colour =
                add(new Setting.Colour("color", "Цвет", true)).showIf(tint::on);
        public final Setting.Num strength =
                add(new Setting.Num("strength", "Сила", .45, .05, 1, .05, "")).showIf(tint::on);
        public final Setting.Bool sky =
                add(new Setting.Bool("sky", "Небо тоже", true)).showIf(tint::on);

        public Atmosphere() {
            super("atmosphere", "Атмосфера", "Своё время суток и цвет тумана и неба", Category.WORLD, false);
        }

        long ticks() {
            return switch (time.get()) {
                case "Рассвет" -> 23200;
                case "День" -> 6000;
                case "Закат" -> 12600;
                case "Ночь" -> 18000;
                case "Своё" -> Math.floorMod(Math.round((hour.get() - 6) * 1000), 24000);
                default -> -1;
            };
        }

        @Override
        public void tick() {
            var level = mc().level;
            long t = ticks();
            if (level != null && t >= 0) level.getLevelData().setDayTime(t);
        }

        public float[] apply(float r, float g, float b, boolean isSky) {
            if (!enabled() || !tint.on() || (isSky && !sky.on())) return null;
            int c = colour.argb();
            float k = strength.f();
            float tr = (c >> 16 & 255) / 255f, tg = (c >> 8 & 255) / 255f, tb = (c & 255) / 255f;
            // keep the scene's brightness so night stays night: tint is scaled by the original luminance
            float lum = Math.max(.08f, .3f * r + .59f * g + .11f * b);
            float sr = tr * lum * 1.6f, sg = tg * lum * 1.6f, sb = tb * lum * 1.6f;
            return new float[] {
                r + (Math.min(1, sr) - r) * k, g + (Math.min(1, sg) - g) * k, b + (Math.min(1, sb) - b) * k
            };
        }
    }

    /** Crosshair: build your own crosshair from shapes. */
    public static final class Crosshair extends Module {
        final Setting.Mode shape =
                add(new Setting.Mode("shape", "Форма", "Крест", "Крест", "Крест + точка", "Точка", "Круг", "Уголки"));
        final Setting.Colour colour = add(new Setting.Colour("color", "Цвет", false));
        final Setting.Num length = add(new Setting.Num("length", "Длина", 4, 1, 12, 1, ""));
        final Setting.Num thickness = add(new Setting.Num("thickness", "Толщина", 1, 1, 4, 1, ""));
        final Setting.Num gap = add(new Setting.Num("gap", "Зазор", 2, 0, 10, 1, ""));
        final Setting.Bool outline = add(new Setting.Bool("outline", "Обводка", true));
        final Setting.Bool dynamic = add(new Setting.Bool("dynamic", "Расходится при движении", true));
        final Setting.Bool attackColour = add(new Setting.Bool("attack", "Цвет при наведении на цель", true));
        private float spread;

        public Crosshair() {
            super("crosshair", "Свой прицел", "Форма, цвет, зазор, обводка", Category.COMBAT, false);
            colour.set(0xFFFFFFFF);
        }

        public void render(GuiGraphics g) {
            var mc = mc();
            if (!mc.options.getCameraType().isFirstPerson() || mc.player == null || mc.player.isSpectator()) return;
            int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
            float move = dynamic.on()
                    ? (float) Math.min(1, mc.player.getDeltaMovement().horizontalDistance() * 5)
                    : 0;
            spread = Draw.approach(spread, move, .2f);
            int gp = gap.i() + Math.round(spread * 3), len = length.i(), t = thickness.i();
            int c = attackColour.on() && mc.crosshairPickEntity != null ? 0xFFF43F5E : colour.argb();
            draw(g, cx, cy, gp, len, t, c);
        }

        public void draw(GuiGraphics g, int cx, int cy, int gp, int len, int t, int c) {
            int o = 0xC0000000, h = t / 2;
            switch (shape.get()) {
                case "Точка" -> box(g, cx - h - (t + 1) % 2 + 0, cy - h, t + 1, t + 1, c, o);
                case "Круг" -> {
                    int r = gp + 2;
                    for (int i = 0; i < 48; i++) {
                        double a = Math.PI * 2 * i / 48;
                        int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
                        if (outline.on()) g.fill(x - 1, y - 1, x + t + 1, y + t + 1, 0x60000000);
                        g.fill(x, y, x + t, y + t, c);
                    }
                }
                case "Уголки" -> {
                    int r = gp + 2;
                    for (int sx : new int[] {-1, 1})
                        for (int sy : new int[] {-1, 1}) {
                            int x = cx + sx * r, y = cy + sy * r;
                            box(g, sx < 0 ? x : x - len + t, y, len, t, c, o);
                            box(g, x, sy < 0 ? y : y - len + t, t, len, c, o);
                        }
                }
                default -> {
                    box(g, cx - h, cy - gp - len, t, len, c, o);
                    box(g, cx - h, cy + gp + 1, t, len, c, o);
                    box(g, cx - gp - len, cy - h, len, t, c, o);
                    box(g, cx + gp + 1, cy - h, len, t, c, o);
                    if (shape.is("Крест + точка")) box(g, cx - h, cy - h, t, t, c, o);
                }
            }
        }

        private void box(GuiGraphics g, int x, int y, int w, int h, int c, int o) {
            if (outline.on()) g.fill(x - 1, y - 1, x + w + 1, y + h + 1, o);
            g.fill(x, y, x + w, y + h, c);
        }
    }

    /** View model: move, turn and scale the held item and arm (first person). */
    public static final class HandView extends Module {
        public final Setting.Mode preset =
                add(new Setting.Mode("preset", "Пресет", "Свой", "Свой", "Компактный", "Низкий", "По центру", "PvP"));
        public final Setting.Num x = add(new Setting.Num("x", "Сдвиг X", 0, -1, 1, .01, ""));
        public final Setting.Num y = add(new Setting.Num("y", "Сдвиг Y", 0, -1, 1, .01, ""));
        public final Setting.Num z = add(new Setting.Num("z", "Сдвиг Z", 0, -1, 1, .01, ""));
        public final Setting.Num rx = add(new Setting.Num("rx", "Поворот X", 0, -180, 180, 1, "°"));
        public final Setting.Num ry = add(new Setting.Num("ry", "Поворот Y", 0, -180, 180, 1, "°"));
        public final Setting.Num rz = add(new Setting.Num("rz", "Поворот Z", 0, -180, 180, 1, "°"));
        public final Setting.Num scale = add(new Setting.Num("scale", "Размер", 1, .3, 1.8, .01, "×"));
        public final Setting.Bool both = add(new Setting.Bool("both", "Обе руки", true));

        public HandView() {
            super("hand_view", "Вид рук", "Положение, поворот и размер предмета в руке", Category.HANDS, false);
        }

        /** Applies the preset values to the sliders (when a preset is picked in the menu). */
        public void applyPreset() {
            double[] v =
                    switch (preset.get()) {
                        case "Компактный" -> new double[] {.06, .02, -.1, 0, 0, 0, .78};
                        case "Низкий" -> new double[] {0, -.18, 0, 0, 0, 0, 1};
                        case "По центру" -> new double[] {-.22, -.04, -.1, 0, -8, 0, .9};
                        case "PvP" -> new double[] {.08, -.06, -.12, 0, 6, 0, .72};
                        default -> null;
                    };
            if (v == null) return;
            x.set(v[0]);
            y.set(v[1]);
            z.set(v[2]);
            rx.set(v[3]);
            ry.set(v[4]);
            rz.set(v[5]);
            scale.set(v[6]);
        }
    }
}
