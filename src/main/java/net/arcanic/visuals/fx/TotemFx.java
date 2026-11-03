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

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.fx.impact.ImpactDraw;
import net.arcanic.visuals.fx.impact.ScreenFx;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class TotemFx extends Module {
    public final Setting.Mode style =
            this.add(new Setting.Mode("style", "Эффект", "Феникс", "Феникс", "Сингулярность", "Осколки"));
    final Setting.Bool self = this.add(new Setting.Bool("self", "Свой тотем", true));
    final Setting.Bool players = this.add(new Setting.Bool("players", "Тотемы игроков", true));
    final Setting.Bool mobs = this.add(new Setting.Bool("mobs", "Тотемы мобов", false));
    final Setting.Mode palette = this.add(new Setting.Mode("palette", "Цвета", "Свои", "Свои", "Тема"));
    final Setting.Num size = this.add(new Setting.Num("size", "Размер", 1.0, 0.6, 1.6, 0.05, "×"));
    final Setting.Bool screen = this.add(new Setting.Bool("screen", "Эффекты на экране", true));
    final Setting.Bool shake = this.add(new Setting.Bool("shake", "Тряска камеры", true));
    final Setting.Bool sound = this.add(new Setting.Bool("sound", "Звук", true));
    final Setting.Num volume =
            (this.add(new Setting.Num("volume", "Громкость", 0.7, 0.1, 1.0, 0.05, ""))).showIf(() -> this.sound.on());
    final Setting.Bool hideVanilla = this.add(new Setting.Bool("hide_vanilla", "Скрыть ванильный тотем", true));
    private static final Identifier PLUME = texture("plume");
    private static final Identifier TONGUE = texture("tongue");
    private static final Identifier BEAM = texture("beam");
    private static final Identifier RING = texture("ring");
    private static final Identifier DISC = texture("disc");
    private static final Identifier HORIZON = texture("horizon");
    private static final Identifier SWIRL = texture("swirl");
    private static final Identifier ORB = Identifier.fromNamespaceAndPath("arcanic", "textures/targetesp/orb.png");
    private static final Identifier TRAIL = Identifier.fromNamespaceAndPath("arcanic", "textures/targetesp/trail.png");
    private static final float RING_FRONT = 0.86F;
    private static final float HORIZON_EDGE = 0.42F;
    private static final float[] LIFE = {2.9F, 2.7F, 3.1F};
    private final List<Pop> pops = new ArrayList<>();
    private final List<Pop> studioPops = new ArrayList<>();
    private ClientLevel level;
    private static final List<Plume> PLUMES = plumes();
    private static final float[][] BONES_OPEN =
            new float[][] {{0.0F, 0.0F}, {0.32F, 0.12F}, {0.62F, 0.26F}, {0.9F, 0.32F}};
    private static final float[][] BONES_FOLDED =
            new float[][] {{0.0F, 0.0F}, {0.14F, -0.08F}, {0.07F, -0.27F}, {0.16F, -0.44F}};
    private static final float[][] EDGE =
            new float[][] {{0.03F, -0.54F}, {0.48F, -0.68F}, {1.2F, -0.4F}, {1.62F, 0.16F}};
    private static final int SEGMENTS = 5;
    private static final float BURN = 0.34F;
    private static final Vector3f LIGHT = (new Vector3f(0.4F, 1.0F, 0.3F)).normalize();

    public TotemFx() {
        super("totem_fx", "Totem Effect", "Эффект срабатывания тотема бессмертия", Category.COMBAT, false);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("arcanic", "textures/totem/" + name + ".png");
    }

    private Pal pal(int style) {
        if (this.palette.is("Тема")) {
            int a = Theme.accent() | 0xFF000000;
            int b = Theme.accent2() | 0xFF000000;
            return new Pal(Fx.lerp(a, -1, 0.5F), a, b);
        } else {
            return switch (style) {
                case 0 -> new Pal(-6234, -25808, 0xFFE2322F);
                case 1 -> new Pal(0xFFEBDFFF, 0xFFA97CFF, 0xFF5A3BFF);
                default -> new Pal(0xFFF2FDFF, 0xFF8FE7FF, 0xFF3EA6FF);
            };
        }
    }

    private boolean targets(LivingEntity e) {
        if (e == Minecraft.getInstance().player) {
            return this.self.on();
        } else {
            return e instanceof Player ? this.players.on() : this.mobs.on();
        }
    }

    public boolean hides(Entity e) {
        boolean flag;
        if (this.enabled() && this.hideVanilla.on() && e instanceof LivingEntity l) {
            if (this.targets(l)) {
                return true;
            }
        }

        return false;
    }

    public void popped(LivingEntity e) {
        if (this.enabled() && e != null && this.targets(e)) {
            Pop p = new Pop(e, this.style.index(), e == Minecraft.getInstance().player);
            this.begin(p);
            this.pops.add(p);

            while (this.pops.size() > 6) {
                this.pops.remove(0);
            }
        }
    }

    private void begin(Pop p) {
        p.scale = this.size.f() * Mth.clamp(p.height / 1.8F, 0.7F, 1.4F);
        if (p.style == 1) {
            p.tiltYaw = p.rnd.nextFloat() * (float) Math.PI * 2.0F;
            p.tilt = (float) Math.toRadians((double) (18.0F + p.rnd.nextFloat() * 14.0F));
        } else if (p.style == 2) {
            this.grow(p);
        }
    }

    public void studioPop(Vec3 feet, float height, float width, float yaw) {
        Pop p = new Pop(feet, height, width, yaw, this.style.index());
        p.start = Studio.clockMs();
        this.begin(p);
        this.studioPops.add(p);

        while (this.studioPops.size() > 2) {
            this.studioPops.remove(0);
        }
    }

    public void studio(Show.View sv) {
        if (!this.studioPops.isEmpty()) {
            View v = new View(sv.pose(), sv.cam(), sv.left(), sv.up(), sv.forward());
            long now = Studio.clockMs();
            Iterator<Pop> it = this.studioPops.iterator();

            while (it.hasNext()) {
                Pop p = (Pop) it.next();
                float t = (float) (now - p.start) / 1000.0F;
                if (!(t > LIFE[p.style]) && !(t < -0.05F)) {
                    t = Math.max(0.0F, t);
                    float dt = Math.max(0.0F, Math.min(0.1F, t - p.last));
                    p.last = t;
                    Pal c = this.pal(p.style);
                    switch (p.style) {
                        case 1:
                            this.singularity(p, t, dt, v, c);
                            break;
                        case 2:
                            this.shards(p, t, dt, v, c);
                            break;
                        default:
                            this.phoenix(p, t, dt, v, c);
                    }
                } else {
                    it.remove();
                }
            }
        }
    }

    public void studioFollow(Vec3 feet, float yaw) {
        for (Pop p : this.studioPops) {
            p.at = p.style == 2 ? new Vec3(feet.x, p.at.y, feet.z) : feet;
            p.yaw = yaw;
        }
    }

    public void clearStudio() {
        this.studioPops.clear();
    }

    protected void onDisable() {
        this.pops.clear();
        ScreenFx.reset();
    }

    public void world(PoseStack ps, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != this.level) {
            this.level = mc.level;
            this.pops.clear();
        }

        if (!this.pops.isEmpty() && mc.level != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            View v = new View(
                    ps.last().pose(),
                    camera.position(),
                    new Vector3f(camera.leftVector()),
                    new Vector3f(camera.upVector()),
                    (Vector3f) null);
            long now = System.currentTimeMillis();
            Iterator<Pop> it = this.pops.iterator();

            while (it.hasNext()) {
                Pop p = (Pop) it.next();
                float t = (float) (now - p.start) / 1000.0F;
                if (t > LIFE[p.style]) {
                    it.remove();
                } else {
                    if (p.who != null && !p.who.isRemoved() && p.who.isAlive()) {
                        p.at = p.who.getPosition(partial);
                        p.yaw = Mth.rotLerp(partial, p.who.yBodyRotO, p.who.yBodyRot);
                    }

                    float dt = Math.max(0.0F, Math.min(0.1F, t - p.last));
                    p.last = t;
                    Pal c = this.pal(p.style);
                    switch (p.style) {
                        case 1:
                            this.singularity(p, t, dt, v, c);
                            break;
                        case 2:
                            this.shards(p, t, dt, v, c);
                            break;
                        default:
                            this.phoenix(p, t, dt, v, c);
                    }
                }
            }
        }
    }

    private void phoenix(Pop p, float t, float dt, View v, Pal c) {
        float s = p.scale;
        float h = p.height;
        float yr = (float) Math.toRadians((double) p.yaw);
        Vector3f back = new Vector3f(Mth.sin((double) yr), 0.0F, -Mth.cos((double) yr));
        Vector3f right = new Vector3f(-Mth.cos((double) yr), 0.0F, -Mth.sin((double) yr));
        Vec3 feet = p.at;
        Vec3 chest = p.at.add(0.0, (double) h * 0.6, 0.0);
        if (p.once(0)) {
            this.play(p, SoundEvents.FIRECHARGE_USE, 1.0F, 0.6F);
            this.play(p, SoundEvents.BLAZE_SHOOT, 0.8F, 0.5F);
            this.jolt(p, 2.6F);
        }

        if (t > 0.12F && p.once(1)) {
            this.play(p, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
        }

        float flap = flap(t);
        float rate = (flap - flap(t - 0.04F)) / 0.04F;
        float sweep = 20.0F + 14.0F * bump(t, 0.42F, 0.64F, 1.08F);
        float span = 1.7F * s;
        Vector3f root = (new Vector3f(v.x(feet), v.y(feet) + h * 0.74F, v.z(feet))).add((new Vector3f(back)).mul(0.2F));
        List<Feather> feathers = new ArrayList<>();

        for (int side = -1; side <= 1; side += 2) {
            this.wing(p, t, root, right, back, side, flap, rate, sweep, span, c, feathers);
        }

        drawFeathers(v, feathers, t);
        tongues(v, feathers, t, span, c);
        tipTrails(v, p, feathers, t, c);
        if (t < 2.0F && !feathers.isEmpty()) {
            emit(p, dt, 70.0F, () -> {
                Feather f = (Feather) feathers.get(p.rnd.nextInt(feathers.size()));
                Vec3 at = v.world(f.point((0.45F + p.rnd.nextFloat() * 0.55F) * f.cut));
                Fx.spawn()
                        .at(at)
                        .vel(
                                (double) back.x * 0.6 + jit(p, 0.3),
                                1.1 + p.rnd.nextDouble() * 0.8,
                                (double) back.z * 0.6 + jit(p, 0.3))
                        .tex(Fx.Tex.FLAME)
                        .size(0.13F * s, 0.025F * s)
                        .spin(jit(p, 0.3) > 0.0 ? 0.1F : -0.1F, 0.0F)
                        .life(0.35F + p.rnd.nextFloat() * 0.22F)
                        .drag(0.8F)
                        .gravity(-1.0F)
                        .fadeIn(0.06F)
                        .colour(c.light, c.deep & 0xFFFFFF);
                if (p.rnd.nextBoolean()) {
                    Fx.spawn()
                            .at(at)
                            .vel(jit(p, 0.4), 0.8 + p.rnd.nextDouble() * 0.9, jit(p, 0.4))
                            .tex(Fx.Tex.SPARK)
                            .mode(Fx.Mode.STRETCH)
                            .size(0.035F * s, 0.0F)
                            .life(0.7F + p.rnd.nextFloat() * 0.6F)
                            .drag(1.0F)
                            .gravity(-1.2F)
                            .twinkle(0.5F)
                            .colour(whiter(c.light), c.main & 0xFFFFFF);
                }
            });
        }

        for (Feather f : feathers) {
            if (!(f.cut >= 1.0F)) {
                Vec3 front = v.world(f.point(f.cut));
                if (p.rnd.nextFloat() < dt * 6.0F) {
                    Fx.spawn()
                            .at(front)
                            .vel(jit(p, 0.5), 0.6 + p.rnd.nextDouble() * 0.9, jit(p, 0.5))
                            .tex(Fx.Tex.SPARK)
                            .mode(Fx.Mode.STRETCH)
                            .size(0.045F * s, 0.0F)
                            .life(0.5F + p.rnd.nextFloat() * 0.5F)
                            .drag(1.4F)
                            .gravity(-1.2F)
                            .colour(whiter(c.light), c.deep & 0xFFFFFF);
                }

                if (p.rnd.nextFloat() < dt * 2.5F) {
                    Fx.spawn()
                            .at(front)
                            .vel(jit(p, 0.2), 0.9 + p.rnd.nextDouble() * 0.5, jit(p, 0.2))
                            .tex(Fx.Tex.FLAME)
                            .size(0.16F * s, 0.03F * s)
                            .life(0.4F)
                            .drag(0.8F)
                            .gravity(-1.0F)
                            .colour(whiter(c.light), c.deep & 0xFFFFFF);
                }
            }
        }

        for (int i = 0; i < PLUMES.size() * 2; i++) {
            if (t >= ((Plume) PLUMES.get(i % PLUMES.size())).burn + 0.34F) {
                p.burnt[i] = true;
            }
        }

        float cx = v.x(chest);
        float cy = v.y(chest);
        float cz = v.z(chest);
        if (t < 0.35F) {
            sprite(v, ORB, cx, cy, cz, (0.6F + 3.4F * t) * s, 0.0F, c.light, (1.0F - t / 0.35F) * 0.85F, 0.6F);
        }

        if (t < 1.4F) {
            float rise = Math.min(1.0F, t / 0.22F);
            float fade = 1.0F - smooth((t - 0.25F) / 1.15F);
            pillar(
                    v,
                    BEAM,
                    v.x(feet),
                    v.y(feet),
                    v.z(feet),
                    5.2F * s * easeOut(rise),
                    0.6F * s,
                    c.light,
                    c.main,
                    fade * 0.9F);
        }

        groundRing(v, feet, t, 0.0F, 0.85F, 3.6F * s, c.main, 0.9F);
        groundRing(v, feet, t, 0.55F, 1.25F, 2.6F * s, c.deep, 0.75F);
        if (t < 1.5F) {
            emit(p, dt, 85.0F, () -> {
                double a = p.rnd.nextDouble() * Math.PI * 2.0;
                double r = ((double) p.width * 0.5 + 0.25) * (double) s;
                double sx = feet.x + Math.cos(a) * r;
                double sz = feet.z + Math.sin(a) * r;
                Fx.spawn()
                        .at(new Vec3(sx, feet.y + p.rnd.nextDouble() * (double) h, sz))
                        .vel(
                                -Math.sin(a) * 1.6 * (double) s,
                                2.0 + p.rnd.nextDouble() * 1.8,
                                Math.cos(a) * 1.6 * (double) s)
                        .tex(Fx.Tex.SPARK)
                        .mode(Fx.Mode.STRETCH)
                        .size(0.055F * s, 0.0F)
                        .life(0.8F + p.rnd.nextFloat() * 0.6F)
                        .drag(1.1F)
                        .gravity(-1.4F)
                        .colour(c.light, c.deep & 0xFFFFFF);
            });
        }

        if (t >= 0.55F && p.once(2) && !feathers.isEmpty()) {
            this.play(p, SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 1.25F);
            this.jolt(p, 2.0F);

            for (int i = 0; i < 80; i++) {
                Feather f = (Feather) feathers.get(p.rnd.nextInt(feathers.size()));
                int side = f.side;
                Vec3 from = v.world(f.point(0.6F + p.rnd.nextFloat() * 0.4F));
                Vec3 out = (new Vec3((double) (right.x * (float) side), -0.3, (double) (right.z * (float) side)))
                        .scale(2.0 + p.rnd.nextDouble() * 4.0);
                Fx.spawn()
                        .at(from)
                        .vel(
                                out.x + (double) (back.x * 2.0F) + jit(p, 1.2),
                                out.y + jit(p, 1.5),
                                out.z + (double) (back.z * 2.0F) + jit(p, 1.2))
                        .tex(Fx.Tex.SPARK)
                        .mode(Fx.Mode.STRETCH)
                        .size(0.07F * s, 0.0F)
                        .life(0.6F + p.rnd.nextFloat() * 0.5F)
                        .drag(2.4F)
                        .gravity(-0.6F)
                        .colour(c.light, c.deep & 0xFFFFFF);
            }

            for (int i = 0; i < 14; i++) {
                Feather f = (Feather) feathers.get(p.rnd.nextInt(feathers.size()));
                int side = f.side;
                Fx.spawn()
                        .at(v.world(f.point(0.5F)))
                        .vel(
                                (double) (right.x * (float) side) * 1.5 + jit(p, 0.8),
                                0.6 + jit(p, 0.6),
                                (double) (right.z * (float) side) * 1.5 + jit(p, 0.8))
                        .tex(Fx.Tex.FEATHER)
                        .size(0.16F * s, 0.1F * s)
                        .spin(p.rnd.nextFloat() * 6.0F, (p.rnd.nextFloat() - 0.5F) * 5.0F)
                        .life(1.8F + p.rnd.nextFloat() * 0.8F)
                        .drag(2.6F)
                        .gravity(0.7F)
                        .colour(c.main, c.deep & 0xFFFFFF);
            }
        }

        float reach = reach(p);
        if (this.screen.on() && reach > 0.0F) {
            centre(v, chest);
            ScreenFx.flash(0.42F * (1.0F - smooth(t / 0.45F)) * reach, r(c.light), g(c.light), b(c.light));
            if (t < 0.8F) {
                float k = 1.0F - t / 0.8F;
                ScreenFx.wave(t / 0.8F * 1.3F, 0.06F, 0.045F * k * reach, 0.55F * k * reach);
            }

            ScreenFx.grade(0.0F, 0.3F * (1.0F - smooth((t - 0.9F) / 1.2F)) * reach, 0.0F, 0.0F);
        }
    }

    private static float flap(float t) {
        if (t < 0.42F) {
            return Mth.lerp(smooth(t / 0.42F), -62.0F, 28.0F);
        } else if (t < 0.64F) {
            return Mth.lerp(smooth((t - 0.42F) / 0.22F), 28.0F, -30.0F);
        } else {
            return t < 1.08F
                    ? Mth.lerp(smooth((t - 0.64F) / 0.44F), -30.0F, 16.0F)
                    : 16.0F + Mth.sin((double) ((t - 1.08F) * 3.4F)) * 6.0F;
        }
    }

    private static List<Plume> plumes() {
        List<Plume> out = new ArrayList<>();

        for (int i = 0; i < 26; i++) {
            float u = (float) i / 25.0F;
            out.add(new Plume(
                    0,
                    0.03F + 0.95F * (float) Math.pow((double) u, 0.92),
                    u,
                    1.0F,
                    0.044F + 0.012F * (1.0F - u),
                    0.05F + 0.45F * u,
                    1.24F + (1.0F - u) * 0.46F));
        }

        for (int i = 0; i < 18; i++) {
            float u = (float) i / 17.0F;
            out.add(new Plume(
                    1,
                    0.06F + 0.9F * u,
                    Math.min(1.0F, u * 1.02F),
                    0.5F,
                    0.05F,
                    0.1F + 0.4F * u,
                    1.36F + (1.0F - u) * 0.44F));
        }

        for (int i = 0; i < 12; i++) {
            float u = (float) i / 11.0F;
            out.add(new Plume(2, 0.08F + 0.86F * u, u, 0.27F, 0.046F, 0.12F + 0.32F * u, 1.46F + (1.0F - u) * 0.4F));
        }

        return out;
    }

    private void wing(
            Pop p,
            float t,
            Vector3f root,
            Vector3f right,
            Vector3f back,
            int side,
            float flap,
            float rate,
            float sweep,
            float span,
            Pal c,
            List<Feather> out) {
        for (int i = 0; i < PLUMES.size(); i++) {
            int index = i + (side > 0 ? PLUMES.size() : 0);
            if (!p.burnt[index]) {
                out.add(feather(t, root, right, back, side, flap, rate, sweep, span, c, (Plume) PLUMES.get(i), index));
            }
        }
    }

    private static Feather feather(
            float t,
            Vector3f root,
            Vector3f right,
            Vector3f back,
            int side,
            float flap,
            float rate,
            float sweep,
            float span,
            Pal c,
            Plume pl,
            int index) {
        float fl = (float) Math.toRadians((double) flap);
        float sw = (float) Math.toRadians((double) sweep);
        Vector3f outward = (new Vector3f(right)).mul((float) side);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f d =
                (new Vector3f(outward)).mul(Mth.cos((double) fl)).add((new Vector3f(up)).mul(Mth.sin((double) fl)));
        Vector3f u =
                (new Vector3f(outward)).mul(-Mth.sin((double) fl)).add((new Vector3f(up)).mul(Mth.cos((double) fl)));
        d.mul(Mth.cos((double) sw))
                .add((new Vector3f(back)).mul(Mth.sin((double) sw)))
                .normalize();
        u.sub((new Vector3f(d)).mul(u.dot(d))).normalize();
        Vector3f n = (new Vector3f(d)).cross(u).normalize();
        float open = smooth(t / 0.42F);
        float own = smooth((open * 1.5F - pl.delay) / 0.55F);
        float[] base = bone(pl.along, open);
        float[] baseOpen = bone(pl.along, 1.0F);
        float[] edge = edge(pl.edge);
        float vx = (edge[0] - baseOpen[0]) * pl.reach;
        float vy = (edge[1] - baseOpen[1]) * pl.reach;
        float openAngle = (float) Math.toDegrees(Math.atan2((double) vy, (double) vx));
        float len = (float) Math.hypot((double) vx, (double) vy);
        float bend = Mth.clamp(-rate / 420.0F, -1.0F, 1.0F) * (pl.row == 0 ? 3.0F + 17.0F * pl.edge * pl.edge : 4.0F);
        float ang = (float) Math.toRadians((double) (Mth.lerp(own, -118.0F, openAngle)
                + bend
                + Mth.sin((double) (t * 5.5F + pl.edge * 8.0F)) * 1.2F * own));
        float length = len * span * (0.7F + 0.3F * own);
        float width = pl.width * span;
        float layer = (pl.row == 2 ? 0.024F : (pl.row == 1 ? 0.012F : 0.0F)) * span * (float) side;
        Vector3f origin = (new Vector3f(root))
                .add((new Vector3f(d)).mul(base[0] * span))
                .add((new Vector3f(u)).mul(base[1] * span))
                .add((new Vector3f(n)).mul(layer));
        float curve = pl.row == 0 ? 0.07F + 0.05F * pl.edge : 0.05F;
        float ca = Mth.cos((double) ang);
        float sa = Mth.sin((double) ang);
        Vector3f[] mid = new Vector3f[6];
        Vector3f[] across = new Vector3f[6];

        for (int i = 0; i <= 5; i++) {
            float f = (float) i / 5.0F;
            float ax = ca * length * f - sa * curve * length * f * f;
            float ay = sa * length * f + ca * curve * length * f * f;
            float tx = ca * length - sa * curve * length * 2.0F * f;
            float ty = sa * length + ca * curve * length * 2.0F * f;
            float tl = (float) Math.hypot((double) tx, (double) ty);
            float px = -ty / tl * width * (float) side;
            float py = tx / tl * width * (float) side;
            mid[i] = (new Vector3f(origin)).add((new Vector3f(d)).mul(ax)).add((new Vector3f(u)).mul(ay));
            across[i] = (new Vector3f(d)).mul(px).add((new Vector3f(u)).mul(py));
        }

        int tip = pl.row == 0
                ? Fx.lerp(c.main, c.deep, 0.2F + 0.8F * pl.edge)
                : (pl.row == 1 ? Fx.lerp(c.light, c.main, 0.55F) : Fx.lerp(c.light, c.main, 0.2F));
        float heat = smooth((t - pl.burn + 0.16F) / 0.16F);
        int baseCol = Fx.lerp(c.light, -1, 0.5F * heat);
        int tipCol = Fx.lerp(tip, c.light, heat);
        Vector3f lag = (new Vector3f(u)).mul(Mth.clamp(-rate / 300.0F, -1.0F, 1.0F) * 0.9F);
        float cut = 1.0F - clamp((t - pl.burn) / 0.34F);
        return new Feather(mid, across, length, pl, index, side, baseCol, tipCol, own, cut, lag);
    }

    private static float[] bone(float along, float open) {
        float seg = Math.min(2.999F, along * 3.0F);
        int b = (int) seg;
        float f = seg - (float) b;
        float[] j0 = joint(b, open);
        float[] j1 = joint(b + 1, open);
        return new float[] {Mth.lerp(f, j0[0], j1[0]), Mth.lerp(f, j0[1], j1[1])};
    }

    private static float[] joint(int i, float open) {
        return new float[] {
            Mth.lerp(open, BONES_FOLDED[i][0], BONES_OPEN[i][0]), Mth.lerp(open, BONES_FOLDED[i][1], BONES_OPEN[i][1])
        };
    }

    private static float[] edge(float u) {
        float a = 1.0F - u;
        float k0 = a * a * a;
        float k1 = 3.0F * a * a * u;
        float k2 = 3.0F * a * u * u;
        float k3 = u * u * u;
        return new float[] {
            k0 * EDGE[0][0] + k1 * EDGE[1][0] + k2 * EDGE[2][0] + k3 * EDGE[3][0],
            k0 * EDGE[0][1] + k1 * EDGE[1][1] + k2 * EDGE[2][1] + k3 * EDGE[3][1]
        };
    }

    private static void drawFeathers(View v, List<Feather> feathers, float t) {
        if (!feathers.isEmpty()) {
            float flick = 0.92F + 0.08F * Mth.sin((double) (t * 40.0F));
            twice(v.pose, PLUME, 0.92F * flick, 0.55F, (e) -> {
                for (Feather f : feathers) {
                    int n = Math.max(1, (int) Math.ceil((double) (f.cut * 5.0F - 0.001F)));

                    for (int i = 0; i < n; i++) {
                        float f0 = (float) i / 5.0F;
                        float f1 = Math.min(f.cut, (float) (i + 1) / 5.0F);
                        int c0 = colour(f, f0);
                        int c1 = colour(f, f1);
                        Vector3f m0 = f.point(f0);
                        Vector3f m1 = f.point(f1);
                        Vector3f a0 = f.acrossAt(f0);
                        Vector3f a1 = f.acrossAt(f1);
                        e.v(m0.x - a0.x, m0.y - a0.y, m0.z - a0.z, 0.0F, 1.0F - f0, c0);
                        e.v(m1.x - a1.x, m1.y - a1.y, m1.z - a1.z, 0.0F, 1.0F - f1, c1);
                        e.v(m1.x + a1.x, m1.y + a1.y, m1.z + a1.z, 1.0F, 1.0F - f1, c1);
                        e.v(m0.x + a0.x, m0.y + a0.y, m0.z + a0.z, 1.0F, 1.0F - f0, c0);
                    }
                }
            });
        }
    }

    private static int colour(Feather f, float along) {
        int c = Fx.lerp(f.colourBase, f.colourTip, along);
        return f.cut >= 1.0F ? c : Fx.lerp(c, -1, 0.8F * clamp(1.0F - (f.cut - along) / 0.25F));
    }

    private static void tongues(View v, List<Feather> feathers, float t, float span, Pal c) {
        if (!feathers.isEmpty()) {
            int hot = whiter(c.light);
            twice(v.pose, TONGUE, 0.3F, 0.8F, (e) -> {
                for (Feather f : feathers) {
                    Plume pl = f.plume;
                    boolean burning = f.cut < 1.0F;
                    boolean grows = pl.row == 0 && f.index % 2 == 0 || pl.row == 1 && f.index % 3 == 0;
                    if ((grows || burning && f.index % 2 == 0) && !(f.open < 0.05F) && !(f.cut <= 0.02F)) {
                        float phase = pl.edge * 13.0F + (float) (f.side * 3) + (float) (pl.row * 5);
                        float len = span
                                * (pl.row == 0 ? 0.12F + 0.2F * pl.edge : 0.09F)
                                * (0.8F + 0.2F * Mth.sin((double) (t * 11.0F + phase)))
                                * f.open
                                * (burning ? 1.3F : 1.0F);
                        Vector3f start = f.point(f.cut);
                        Vector3f dir = f.tangent(f.cut)
                                .mul(0.55F)
                                .add(0.0F, 0.9F, 0.0F)
                                .add(f.lag)
                                .normalize();
                        Vector3f side = (new Vector3f(dir)).cross(v.toward(start.x, start.y, start.z));
                        if (!(side.lengthSquared() < 1.0E-8F)) {
                            side.normalize();
                            int k = 6;
                            Vector3f[] pts = new Vector3f[k + 1];

                            for (int j = 0; j <= k; j++) {
                                float g = (float) j / (float) k;
                                float sway = Mth.sin((double) (g * 4.5F - t * 13.0F + phase)) * 0.2F * len * g;
                                pts[j] = (new Vector3f(dir))
                                        .mul(len * g)
                                        .add(start)
                                        .add((new Vector3f(side)).mul(sway));
                            }

                            float width = len * 0.36F;

                            for (int j = 0; j < k; j++) {
                                float g0 = (float) j / (float) k;
                                float g1 = (float) (j + 1) / (float) k;
                                Vector3f a0 = across(v, pts, j, width);
                                Vector3f a1 = across(v, pts, j + 1, width);
                                Vector3f p0 = pts[j];
                                Vector3f p1 = pts[j + 1];
                                int c0 = fire(c, hot, g0);
                                int c1 = fire(c, hot, g1);
                                e.v(p0.x - a0.x, p0.y - a0.y, p0.z - a0.z, 0.0F, 1.0F - g0, c0);
                                e.v(p1.x - a1.x, p1.y - a1.y, p1.z - a1.z, 0.0F, 1.0F - g1, c1);
                                e.v(p1.x + a1.x, p1.y + a1.y, p1.z + a1.z, 1.0F, 1.0F - g1, c1);
                                e.v(p0.x + a0.x, p0.y + a0.y, p0.z + a0.z, 1.0F, 1.0F - g0, c0);
                            }
                        }
                    }
                }
            });
        }
    }

    private static Vector3f across(View v, Vector3f[] pts, int i, float width) {
        Vector3f d = (new Vector3f(pts[Math.min(pts.length - 1, i + 1)])).sub(pts[Math.max(0, i - 1)]);
        Vector3f a = d.cross(v.toward(pts[i].x, pts[i].y, pts[i].z));
        float l = a.length();
        return l < 1.0E-6F ? a.zero() : a.mul(width * 0.5F / l);
    }

    private static int fire(Pal c, int hot, float g) {
        return g < 0.45F ? Fx.lerp(hot, c.main, g / 0.45F) : Fx.lerp(c.main, c.deep, (g - 0.45F) / 0.55F);
    }

    private static void tipTrails(View v, Pop p, List<Feather> feathers, float t, Pal c) {
        for (int side = -1; side <= 1; side += 2) {
            List<Vec3> trail = side < 0 ? p.trailL : p.trailR;
            Feather tip = null;

            for (Feather f : feathers) {
                if (f.side == side && f.plume.row == 0 && (tip == null || f.plume.edge > tip.plume.edge)) {
                    tip = f;
                }
            }

            if (tip != null) {
                trail.add(0, v.world(tip.point(1.0F)));
            }

            while (trail.size() > 14) {
                trail.remove(trail.size() - 1);
            }

            if (trail.size() >= 3) {
                float speed = (float) ((Vec3) trail.get(0)).distanceTo((Vec3) trail.get(2));
                float a = Mth.clamp(speed * 6.0F, 0.0F, 1.0F) * (1.0F - smooth((t - 1.4F) / 0.4F));
                if (!(a <= 0.02F)) {
                    int n = trail.size();
                    float[] xs = new float[n];
                    float[] ys = new float[n];
                    float[] zs = new float[n];

                    for (int i = 0; i < n; i++) {
                        xs[i] = v.x((Vec3) trail.get(i));
                        ys[i] = v.y((Vec3) trail.get(i));
                        zs[i] = v.z((Vec3) trail.get(i));
                    }

                    ribbon(v, TRAIL, xs, ys, zs, 0.3F * p.scale, c.main, 0.7F * a);
                    ribbon(v, TRAIL, xs, ys, zs, 0.1F * p.scale, c.light, a);
                }
            }
        }
    }

    private void singularity(Pop p, float t, float dt, View v, Pal c) {
        float s = p.scale;
        Vec3 centre = p.at.add(0.0, (double) p.height * 0.55, 0.0);
        float x = v.x(centre);
        float y = v.y(centre);
        float z = v.z(centre);
        float boom = 0.85F;
        float te = t - boom;
        if (p.once(0)) {
            this.play(p, SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 1.25F);
            this.play(p, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9F, 0.55F);
        }

        float reach = reach(p);
        if (te < 0.0F) {
            float core = 0.5F * s * backOut(t / 0.5F) * (1.0F - easeIn(clamp((t - 0.74F) / 0.11F)));
            float disc = 1.75F * s * smooth(t / 0.42F) * (1.0F - easeIn(clamp((t - 0.7F) / 0.15F)));
            Matrix4f tilted = (new Matrix4f(v.pose))
                    .translate(x, y, z)
                    .rotateY(p.tiltYaw)
                    .rotateX(p.tilt)
                    .rotateY(-t * 7.5F);
            flat(tilted, SWIRL, disc, new int[] {c.main, c.deep, c.main, c.deep}, 0.4F, 0.95F);
            float pulse = 1.0F + 0.08F * Mth.sin((double) (t * 34.0F));
            sprite(v, HORIZON, x, y, z, core / 0.42F * pulse, t * 2.0F, c.light, 0.55F, 1.0F);
            sprite(v, DISC, x, y, z, core / 0.9F, 0.0F, 0xFF000000, 1.0F, 0.0F);
            emit(p, dt, 150.0F, () -> {
                Vec3 d = dir(p);
                double r = (2.4 + p.rnd.nextDouble() * 1.8) * (double) s;
                float life = 0.3F + p.rnd.nextFloat() * 0.22F;
                Fx.spawn()
                        .at(centre.add(d.scale(r)))
                        .vel(-d.x * r / (double) life, -d.y * r / (double) life, -d.z * r / (double) life)
                        .tex(Fx.Tex.SPARK)
                        .mode(Fx.Mode.STRETCH)
                        .size(0.045F * s, 0.015F * s)
                        .life(life)
                        .drag(0.0F)
                        .fadeIn(0.08F)
                        .colour(c.light, c.main);
            });
            if (this.screen.on() && reach > 0.0F) {
                centre(v, centre);
                float k = smooth(t / 0.55F) * reach;
                ScreenFx.grade(0.78F * k, 0.55F * k, 0.22F * k, 0.09F * k);
                ScreenFx.wave(0.0F, 0.001F, 0.0F, 0.35F * k);
            }

        } else {
            if (p.once(1)) {
                this.play(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.15F);
                this.play(p, SoundEvents.GENERIC_EXPLODE.value(), 0.45F, 0.55F);
                this.jolt(p, 4.2F);

                for (int i = 0; i < 6 + p.rnd.nextInt(3); i++) {
                    Vec3 d = dir(p);
                    p.bolts.add((new Vector3f((float) d.x, (float) d.y * 0.7F, (float) d.z))
                            .normalize()
                            .mul((2.2F + p.rnd.nextFloat() * 1.5F) * s));
                }

                for (int i = 0; i < 95; i++) {
                    Vec3 d = dir(p);
                    double sp = (7.0 + p.rnd.nextDouble() * 7.0) * (double) s;
                    Fx.spawn()
                            .at(centre)
                            .vel(d.x * sp, d.y * sp, d.z * sp)
                            .tex(Fx.Tex.SPARK)
                            .mode(Fx.Mode.STRETCH)
                            .size(0.07F * s, 0.0F)
                            .life(0.55F + p.rnd.nextFloat() * 0.45F)
                            .drag(3.2F)
                            .colour(c.light, c.deep & 0xFFFFFF);
                }

                for (int i = 0; i < 40; i++) {
                    Vec3 d = dir(p);
                    double sp = (1.4 + p.rnd.nextDouble() * 2.6) * (double) s;
                    Fx.spawn()
                            .at(centre)
                            .vel(d.x * sp, d.y * sp, d.z * sp)
                            .tex(i % 3 == 0 ? Fx.Tex.STAR : Fx.Tex.GLOW)
                            .size(0.14F * s, 0.0F)
                            .spin(p.rnd.nextFloat() * 6.0F, (p.rnd.nextFloat() - 0.5F) * 6.0F)
                            .life(1.2F + p.rnd.nextFloat() * 0.7F)
                            .drag(1.8F)
                            .twinkle(0.5F)
                            .colour(c.main, c.deep & 0xFFFFFF);
                }
            }

            if (te < 0.3F) {
                sprite(v, ORB, x, y, z, (1.0F + te * 9.0F) * s, 0.0F, c.light, 0.0F, 1.0F - te / 0.3F);
            }

            if (te < 0.6F) {
                sprite(
                        v,
                        RING,
                        x,
                        y,
                        z,
                        3.4F * s * easeOut(te / 0.6F) / 0.86F,
                        te,
                        c.light,
                        0.5F * (1.0F - te / 0.6F),
                        1.0F - te / 0.6F);
            }

            Matrix4f here = (new Matrix4f(v.pose)).translate(x, y, z);
            if (te < 1.0F) {
                flat(
                        here,
                        RING,
                        5.2F * s * easeOut(te) / 0.86F,
                        new int[] {c.light, c.main, c.light, c.main},
                        0.5F * (1.0F - te),
                        1.0F - te);
            }

            for (int i = 0; i < 3; i++) {
                float u = (te - 0.2F - (float) i * 0.22F) / 1.1F;
                if (u > 0.0F && u < 1.0F) {
                    flat(
                            here,
                            RING,
                            (1.2F + 2.6F * easeOut(u)) * s / 0.86F,
                            new int[] {c.main, c.deep, c.main, c.deep},
                            0.0F,
                            0.4F * (1.0F - u));
                }
            }

            if (te < 1.5F) {
                sprite(
                        v,
                        ORB,
                        x,
                        y,
                        z,
                        0.5F * s * (1.0F + 0.15F * Mth.sin((double) (te * 20.0F))),
                        0.0F,
                        c.main,
                        0.0F,
                        0.6F * (1.0F - te / 1.5F));
            }

            if (te < 0.45F) {
                long frame = (long) (te / 0.045F);
                Random jag = new Random(p.start * 31L + frame);
                float k = (1.0F - te / 0.45F) * (0.6F + 0.4F * jag.nextFloat());

                for (Vector3f end : p.bolts) {
                    int n = 10;
                    float[] bx = new float[n + 1];
                    float[] by = new float[n + 1];
                    float[] bz = new float[n + 1];
                    Vector3f side = (new Vector3f(end)).cross(0.0F, 1.0F, 0.0F);
                    if (side.lengthSquared() < 1.0E-4F) {
                        side.set(1.0F, 0.0F, 0.0F);
                    }

                    side.normalize();
                    Vector3f side2 = (new Vector3f(end)).cross(side).normalize();
                    float seg = end.length() / (float) n;

                    for (int i = 0; i <= n; i++) {
                        float f = (float) i / (float) n;
                        float j = i == 0 ? 0.0F : seg * 0.9F;
                        float o1 = (jag.nextFloat() - 0.5F) * j;
                        float o2 = (jag.nextFloat() - 0.5F) * j;
                        bx[i] = x + end.x * f + side.x * o1 + side2.x * o2;
                        by[i] = y + end.y * f + side.y * o1 + side2.y * o2;
                        bz[i] = z + end.z * f + side.z * o1 + side2.z * o2;
                    }

                    ribbon(v, Fx.Tex.LINE_GLOW.id, bx, by, bz, 0.32F * s, c.main, 0.75F * k);
                    ribbon(v, Fx.Tex.LINE_GLOW.id, bx, by, bz, 0.07F * s, c.light, k);
                }
            }

            if (this.screen.on() && reach > 0.0F) {
                centre(v, centre);
                ScreenFx.flash(0.55F * (1.0F - clamp(te / 0.3F)) * reach, r(c.light), g(c.light), b(c.light));
                if (te < 0.9F) {
                    float k = (1.0F - te / 0.9F) * reach;
                    ScreenFx.wave(te / 0.9F * 1.5F, 0.05F, 0.06F * k, 0.9F * k);
                }

                float back = 1.0F - smooth(te / 0.22F);
                ScreenFx.grade(0.78F * back * reach, 0.55F * back * reach, 0.0F, 0.0F);
            }
        }
    }

    private void grow(Pop p) {
        float body = p.width * 0.71F;
        float s = p.scale;
        int n = 13;

        for (int i = 0; i < n; i++) {
            float angle =
                    (float) ((double) i * Math.PI * 2.0 / (double) n + (double) ((p.rnd.nextFloat() - 0.5F) * 0.35F));
            float base = body + 0.22F + p.rnd.nextFloat() * 0.22F;
            float length = (0.85F + p.rnd.nextFloat() * 0.95F) * s * p.height / 1.8F;
            float lean = (float) Math.toRadians(i % 4 == 0 ? -8.0 : (double) (14.0F + p.rnd.nextFloat() * 20.0F));
            float most = (base - body - 0.06F) / length;
            if (Math.sin((double) lean) > (double) most) {
                lean = (float) Math.asin((double) Math.max(-0.5F, Math.min(1.0F, most)));
            }

            p.crystals.add(new Crystal(
                    angle,
                    base,
                    length,
                    (0.07F + p.rnd.nextFloat() * 0.06F) * s,
                    lean,
                    p.rnd.nextFloat() * 6.0F,
                    (float) i * 0.022F));
        }
    }

    private void shards(Pop p, float t, float dt, View v, Pal c) {
        float s = p.scale;
        float shatter = 0.92F;
        float te = t - shatter;
        Vec3 feet = p.at;
        Vec3 chest = p.at.add(0.0, (double) p.height * 0.55, 0.0);
        float reach = reach(p);
        if (p.once(0)) {
            this.play(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0F, 0.7F);
            this.play(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F);
        }

        if (t > 0.38F && p.once(1)) {
            this.play(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.6F);
        }

        if (t > 0.64F && p.once(2)) {
            this.play(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.4F);
        }

        float fx = v.x(feet);
        float fy = v.y(feet);
        float fz = v.z(feet);
        if (te < 0.0F) {
            float tremble = 0.02F * s * smooth((t - 0.5F) / 0.42F);
            Random shake = new Random((long) (t * 60.0F) + p.start);
            BufferBuilder faces = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            List<float[]> edges = new ArrayList<>();

            for (Crystal k : p.crystals) {
                float g = backOut((t - k.delay) / 0.32F);
                if (!(g <= 0.01F)) {
                    float jx = (shake.nextFloat() - 0.5F) * tremble;
                    float jz = (shake.nextFloat() - 0.5F) * tremble;
                    crystal(faces, edges, v.pose, fx + jx, fy, fz + jz, k, g, c, 0.25F * smooth((t - 0.3F) / 0.5F));
                }
            }

            ImpactDraw.draw(ImpactDraw.type(Mode.QUADS, true, false, false), faces);

            for (float[] e : edges) {
                ribbon(
                        v,
                        Fx.Tex.LINE_GLOW.id,
                        new float[] {e[0], e[3]},
                        new float[] {e[1], e[4]},
                        new float[] {e[2], e[5]},
                        0.045F * s,
                        c.light,
                        0.7F);
            }

            float glow = smooth(t / 0.5F) * (0.7F + 0.3F * Mth.sin((double) (t * 18.0F)));
            sprite(v, ORB, v.x(chest), v.y(chest), v.z(chest), 1.3F * s * glow, 0.0F, c.main, 0.0F, 0.55F * glow);
            Matrix4f ground = (new Matrix4f(v.pose)).translate(fx, fy + 0.03F, fz);
            flat(
                    ground,
                    RING,
                    (0.6F + 1.4F * easeOut(t / 0.5F)) * s / 0.86F,
                    new int[] {c.light, c.main, c.light, c.main},
                    0.35F,
                    0.45F);
            flat(ground, ORB, 1.6F * s, new int[] {c.main, c.main, c.main, c.main}, 0.0F, 0.35F * glow);
            if (t > 0.2F) {
                emit(p, dt, 28.0F, () -> {
                    Crystal k = (Crystal) p.crystals.get(p.rnd.nextInt(p.crystals.size()));
                    float along = p.rnd.nextFloat() * k.length;
                    Vector3f at = crystalPoint(k, along);
                    Fx.spawn()
                            .at(feet.add((double) at.x, (double) at.y, (double) at.z))
                            .tex(Fx.Tex.SPARKLE)
                            .size(0.12F * s, 0.0F)
                            .spin(p.rnd.nextFloat() * 6.0F, 2.0F)
                            .life(0.35F + p.rnd.nextFloat() * 0.25F)
                            .drag(0.0F)
                            .colour(c.light, c.main & 0xFFFFFF);
                });
            }

            if (this.screen.on() && reach > 0.0F) {
                centre(v, chest);
                float k = smooth(t / 0.12F) * reach;
                ScreenFx.grade(0.88F * k, 0.45F * k, 0.0F, 0.0F);
                ScreenFx.flash(0.4F * (1.0F - clamp(t / 0.22F)) * reach, r(c.light), g(c.light), b(c.light));
                ScreenFx.wave(0.0F, 0.001F, 0.0F, 0.18F * k);
            }

        } else {
            if (p.once(3)) {
                this.play(p, SoundEvents.GLASS_BREAK, 1.0F, 0.55F);
                this.play(p, SoundEvents.GLASS_BREAK, 0.7F, 1.25F);
                this.play(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.8F);
                this.play(p, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.6F);
                this.jolt(p, 3.4F);
                this.shatter(p, c);
            }

            float fade = 1.0F - smooth((t - LIFE[2] + 0.7F) / 0.7F);
            BufferBuilder solid = Tesselator.getInstance().begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            List<float[]> glints = new ArrayList<>();
            Quaternionf q = new Quaternionf();

            for (Piece k : p.pieces) {
                k.vy -= (double) (11.0F * dt);
                float drag = (float) Math.exp((double) (-0.6F * dt));
                k.vx *= (double) drag;
                k.vz *= (double) drag;
                k.x += k.vx * (double) dt;
                k.y += k.vy * (double) dt;
                k.z += k.vz * (double) dt;
                if (k.y < feet.y + 0.03 && k.vy < 0.0) {
                    k.y = feet.y + 0.03;
                    k.vy *= -0.32;
                    k.vx *= 0.55;
                    k.vz *= 0.55;
                    k.spin *= 0.5F;
                }

                k.angle += k.spin * dt;
                q.identity().rotateAxis(k.angle, k.axis);
                Vector3f a = q.transform(new Vector3f(k.a));
                Vector3f b = q.transform(new Vector3f(k.b));
                Vector3f cc = q.transform(new Vector3f(k.c));
                float px = (float) (k.x - v.cam.x);
                float py = (float) (k.y - v.cam.y);
                float pz = (float) (k.z - v.cam.z);
                int col = alpha(Fx.lerp(c.main, c.light, k.tint), 0.82F * fade);
                solid.addVertex(v.pose, px + a.x, py + a.y, pz + a.z).setColor(col);
                solid.addVertex(v.pose, px + b.x, py + b.y, pz + b.z).setColor(col);
                solid.addVertex(v.pose, px + cc.x, py + cc.y, pz + cc.z).setColor(col);
                Vector3f n = (new Vector3f(b))
                        .sub(a)
                        .cross((new Vector3f(cc)).sub(a))
                        .normalize();
                float facing = Math.abs(n.dot((new Vector3f(v.toward(px, py, pz))).normalize()));
                float shine = (float) Math.pow((double) facing, 7.0) * fade;
                if (shine > 0.03F) {
                    glints.add(new float[] {
                        px + a.x, py + a.y, pz + a.z, px + b.x, py + b.y, pz + b.z, px + cc.x, py + cc.y, pz + cc.z,
                        shine
                    });
                }
            }

            ImpactDraw.draw(ImpactDraw.type(Mode.TRIANGLES, true, false, false), solid);
            BufferBuilder glint = Tesselator.getInstance().begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            for (float[] g : glints) {
                int col = alpha(c.light, g[9]);

                for (int i = 0; i < 3; i++) {
                    glint.addVertex(v.pose, g[i * 3], g[i * 3 + 1], g[i * 3 + 2])
                            .setColor(col);
                }
            }

            ImpactDraw.draw(ImpactDraw.type(Mode.TRIANGLES, true, false, true), glint);
            float cx = v.x(chest);
            float cy = v.y(chest);
            float cz = v.z(chest);
            if (te < 0.25F) {
                sprite(v, ORB, cx, cy, cz, (1.0F + te * 10.0F) * s, 0.0F, c.light, 0.0F, 1.0F - te / 0.25F);
            }

            if (te < 0.65F) {
                flat(
                        (new Matrix4f(v.pose)).translate(cx, cy, cz),
                        RING,
                        4.6F * s * easeOut(te / 0.65F) / 0.86F,
                        new int[] {c.light, c.main, c.light, c.main},
                        0.45F * (1.0F - te / 0.65F),
                        1.0F - te / 0.65F);
            }

            if (this.screen.on() && reach > 0.0F) {
                centre(v, chest);
                float back = 1.0F - smooth(te / 0.2F);
                ScreenFx.grade(0.88F * back * reach, 0.45F * back * reach, 0.0F, 0.0F);
                ScreenFx.flash(0.5F * (1.0F - clamp(te / 0.22F)) * reach, r(c.light), g(c.light), b(c.light));
                if (te < 0.75F) {
                    float k = (1.0F - te / 0.75F) * reach;
                    ScreenFx.wave(te / 0.75F * 1.5F, 0.05F, 0.05F * k, 0.8F * k);
                }
            }
        }
    }

    private static Vector3f crystalPoint(Crystal k, float along) {
        float ca = Mth.cos((double) k.angle);
        float sa = Mth.sin((double) k.angle);
        float ax = -ca * Mth.sin((double) k.lean);
        float ay = Mth.cos((double) k.lean);
        float az = -sa * Mth.sin((double) k.lean);
        return new Vector3f(ca * k.base + ax * along, ay * along, sa * k.base + az * along);
    }

    private static void crystal(
            BufferBuilder b,
            List<float[]> edges,
            Matrix4f m,
            float x,
            float y,
            float z,
            Crystal k,
            float g,
            Pal c,
            float inner) {
        float ca = Mth.cos((double) k.angle);
        float sa = Mth.sin((double) k.angle);
        Vector3f axis =
                new Vector3f(-ca * Mth.sin((double) k.lean), Mth.cos((double) k.lean), -sa * Mth.sin((double) k.lean));
        Vector3f base = new Vector3f(x + ca * k.base, y - 0.05F, z + sa * k.base);
        Vector3f s1 = (new Vector3f(axis)).cross(0.0F, 0.0F, 1.0F);
        if (s1.lengthSquared() < 1.0E-4F) {
            s1.set(1.0F, 0.0F, 0.0F);
        }

        s1.normalize();
        Vector3f s2 = (new Vector3f(axis)).cross(s1).normalize();
        float len = k.length * g;
        float shoulder = len * 0.76F;
        Vector3f tip = (new Vector3f(axis)).mul(len).add(base);
        Vector3f[] lo = new Vector3f[6];
        Vector3f[] hi = new Vector3f[6];

        for (int i = 0; i < 6; i++) {
            float a = k.spin + (float) i * (float) Math.PI / 3.0F;
            Vector3f o = (new Vector3f(s1))
                    .mul(Mth.cos((double) a) * k.radius)
                    .add((new Vector3f(s2)).mul(Mth.sin((double) a) * k.radius));
            lo[i] = (new Vector3f(base)).add(o);
            hi[i] = (new Vector3f(base)).add((new Vector3f(axis)).mul(shoulder)).add(o);
        }

        int deep = Fx.lerp(c.deep, c.main, inner);
        int main = Fx.lerp(c.main, c.light, inner);

        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            float shade =
                    0.55F + 0.45F * Math.max(0.0F, normal(lo[i], lo[j], hi[i]).dot(LIGHT));
            int cLo = alpha(dim(deep, shade), 0.5F);
            int cHi = alpha(dim(main, shade), 0.72F);
            v(b, m, lo[i], cLo);
            v(b, m, lo[j], cLo);
            v(b, m, hi[j], cHi);
            v(b, m, hi[i], cHi);
            float tipShade =
                    0.6F + 0.4F * Math.max(0.0F, normal(hi[i], hi[j], tip).dot(LIGHT));
            int cTip = alpha(dim(c.light, tipShade), 0.9F);
            v(b, m, hi[i], alpha(dim(main, tipShade), 0.72F));
            v(b, m, hi[j], alpha(dim(main, tipShade), 0.72F));
            v(b, m, tip, cTip);
            v(b, m, tip, cTip);
            edges.add(new float[] {hi[i].x, hi[i].y, hi[i].z, tip.x, tip.y, tip.z});
            if (i % 2 == 0) {
                edges.add(new float[] {lo[i].x, lo[i].y, lo[i].z, hi[i].x, hi[i].y, hi[i].z});
            }
        }
    }

    private static Vector3f normal(Vector3f a, Vector3f b, Vector3f c) {
        return (new Vector3f(b)).sub(a).cross((new Vector3f(c)).sub(a)).normalize();
    }

    private static void v(BufferBuilder b, Matrix4f m, Vector3f p, int c) {
        b.addVertex(m, p.x, p.y, p.z).setColor(c);
    }

    private void shatter(Pop p, Pal c) {
        float s = p.scale;
        Vec3 feet = p.at;

        for (Crystal k : p.crystals) {
            int n = 9 + p.rnd.nextInt(4);

            for (int i = 0; i < n; i++) {
                Vector3f at = crystalPoint(k, p.rnd.nextFloat() * k.length);
                float sz = (0.05F + p.rnd.nextFloat() * 0.08F) * s;
                Piece piece =
                        new Piece(rv(p, sz), rv(p, sz), rv(p, sz), rv(p, 1.0F).normalize());
                piece.x = feet.x + (double) at.x;
                piece.y = feet.y + (double) at.y;
                piece.z = feet.z + (double) at.z;
                double out = 2.5 + p.rnd.nextDouble() * 3.5;
                double len = Math.max(0.05, Math.hypot((double) at.x, (double) at.z));
                piece.vx = (double) at.x / len * out + jit(p, 0.8);
                piece.vy = 1.5 + p.rnd.nextDouble() * 2.6;
                piece.vz = (double) at.z / len * out + jit(p, 0.8);
                piece.spin = 4.0F + p.rnd.nextFloat() * 9.0F;
                piece.tint = p.rnd.nextFloat();
                p.pieces.add(piece);
            }
        }

        Vec3 chest = feet.add(0.0, (double) p.height * 0.55, 0.0);

        for (int i = 0; i < 46; i++) {
            Vec3 d = dir(p);
            double sp = (3.0 + p.rnd.nextDouble() * 5.0) * (double) s;
            Fx.spawn()
                    .at(chest)
                    .vel(d.x * sp, d.y * sp * 0.6 + 1.0, d.z * sp)
                    .tex(Fx.Tex.SPARKLE)
                    .size(0.16F * s, 0.0F)
                    .spin(p.rnd.nextFloat() * 6.0F, (p.rnd.nextFloat() - 0.5F) * 4.0F)
                    .life(0.8F + p.rnd.nextFloat() * 0.6F)
                    .drag(2.5F)
                    .twinkle(0.7F)
                    .colour(c.light, c.main & 0xFFFFFF);
        }

        for (int i = 0; i < 32; i++) {
            Vec3 d = dir(p);
            Fx.spawn()
                    .at(chest.add(d.scale(0.6 * (double) s)))
                    .vel(d.x * 1.2, Math.abs(d.y) * 0.8 + 0.4, d.z * 1.2)
                    .tex(Fx.Tex.SNOW)
                    .size(0.09F * s, 0.05F * s)
                    .spin(p.rnd.nextFloat() * 6.0F, (p.rnd.nextFloat() - 0.5F) * 2.0F)
                    .life(1.8F + p.rnd.nextFloat() * 0.9F)
                    .drag(1.6F)
                    .gravity(0.35F)
                    .colour(c.light, c.main & 0xFFFFFF);
        }
    }

    private static Vector3f rv(Pop p, float size) {
        return (new Vector3f(p.rnd.nextFloat() - 0.5F, p.rnd.nextFloat() - 0.5F, p.rnd.nextFloat() - 0.5F))
                .mul(size * 2.0F);
    }

    private void play(Pop p, SoundEvent s, float vol, float pitch) {
        if (p.studio) {
            if (this.sound.on()) {
                ImpactDraw.studioSound(s, vol * this.volume.f() * 0.6F, pitch);
            }

        } else {
            Minecraft mc = Minecraft.getInstance();
            if (this.sound.on() && mc.level != null) {
                mc.level.playLocalSound(
                        p.at.x, p.at.y + 1.0, p.at.z, s, SoundSource.PLAYERS, vol * this.volume.f(), pitch, false);
            }
        }
    }

    private static float reach(Pop p) {
        if (!p.studio && !p.mine) {
            LocalPlayer me = Minecraft.getInstance().player;
            if (me == null) {
                return 0.0F;
            } else {
                double d = me.position().distanceTo(p.at);
                return d > 28.0 ? 0.0F : (float) (1.0 - d / 28.0) * 0.8F;
            }
        } else {
            return 1.0F;
        }
    }

    private void jolt(Pop p, float strength) {
        if (this.shake.on()) {
            ImpactDraw.shake(strength * reach(p));
        }
    }

    private static void centre(View v, Vec3 at) {
        Vector4f q = new Vector4f(v.x(at), v.y(at), v.z(at), 1.0F);
        v.pose.transform(q);
        ImpactDraw.PROJECTION.transform(q);
        ScreenFx.project(q);
    }

    private static void emit(Pop p, float dt, float rate, Runnable one) {
        p.owed += dt * rate;

        while (p.owed >= 1.0F) {
            --p.owed;
            one.run();
        }
    }

    private static Vec3 dir(Pop p) {
        double u = p.rnd.nextDouble() * 2.0 - 1.0;
        double th = p.rnd.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(1.0 - u * u);
        return new Vec3(r * Math.cos(th), u, r * Math.sin(th));
    }

    private static double jit(Pop p, double k) {
        return (p.rnd.nextDouble() - 0.5) * 2.0 * k;
    }

    private static void twice(Matrix4f m, Identifier tex, float shape, float glow, Consumer<Emit> quads) {
        if (shape > 0.003F) {
            Emit e = new Emit(m, shape);
            quads.accept(e);
            ImpactDraw.draw(ImpactDraw.type(tex, Mode.QUADS, true, false), e.b);
        }

        if (glow > 0.003F) {
            Emit e = new Emit(m, glow);
            quads.accept(e);
            ImpactDraw.draw(ImpactDraw.type(tex, Mode.QUADS, true, true), e.b);
        }
    }

    private static void sprite(
            View v, Identifier tex, float x, float y, float z, float half, float rot, int c, float shape, float glow) {
        float cr = Mth.cos((double) rot) * half;
        float sr = Mth.sin((double) rot) * half;
        Vector3f l = v.left;
        Vector3f u = v.up;
        float[][] k =
                new float[][] {{cr - sr, sr + cr}, {cr + sr, sr - cr}, {-cr + sr, -sr - cr}, {-cr - sr, -sr + cr}};
        float[] tu = {0.0F, 0.0F, 1.0F, 1.0F};
        float[] tv = {0.0F, 1.0F, 1.0F, 0.0F};
        twice(v.pose, tex, shape, glow, (e) -> {
            for (int i = 0; i < 4; i++) {
                e.v(
                        x + l.x * k[i][0] + u.x * k[i][1],
                        y + l.y * k[i][0] + u.y * k[i][1],
                        z + l.z * k[i][0] + u.z * k[i][1],
                        tu[i],
                        tv[i],
                        c);
            }
        });
    }

    private static void flat(Matrix4f m, Identifier tex, float half, int[] c, float shape, float glow) {
        twice(m, tex, shape, glow, (e) -> {
            e.v(-half, 0.0F, -half, 0.0F, 0.0F, c[0]);
            e.v(-half, 0.0F, half, 0.0F, 1.0F, c[1]);
            e.v(half, 0.0F, half, 1.0F, 1.0F, c[2]);
            e.v(half, 0.0F, -half, 1.0F, 0.0F, c[3]);
        });
    }

    private static void groundRing(View v, Vec3 feet, float t, float from, float to, float radius, int c, float a) {
        float u = (t - from) / (to - from);
        if (!(u <= 0.0F) && !(u >= 1.0F)) {
            Matrix4f m = (new Matrix4f(v.pose)).translate(v.x(feet), v.y(feet) + 0.04F, v.z(feet));
            float k = (1.0F - u) * (1.0F - u);
            flat(m, RING, radius * easeOut(u) / 0.86F, new int[] {c, c, c, c}, 0.35F * k * a, k * a);
        }
    }

    private static void pillar(
            View v,
            Identifier tex,
            float x,
            float y,
            float z,
            float height,
            float width,
            int bottom,
            int top,
            float a) {
        Vector3f eye = v.toward(x, y, z);
        float len = (float) Math.hypot((double) eye.x, (double) eye.z);
        float sx = len < 1.0E-4F ? width * 0.5F : -eye.z / len * width * 0.5F;
        float sz = len < 1.0E-4F ? 0.0F : eye.x / len * width * 0.5F;
        twice(v.pose, tex, 0.3F * a, a, (e) -> {
            e.v(x - sx, y + height, z - sz, 0.0F, 0.0F, top);
            e.v(x - sx, y, z - sz, 0.0F, 1.0F, bottom);
            e.v(x + sx, y, z + sz, 1.0F, 1.0F, bottom);
            e.v(x + sx, y + height, z + sz, 1.0F, 0.0F, top);
        });
    }

    private static void ribbon(
            View v, Identifier tex, float[] xs, float[] ys, float[] zs, float width, int c, float a) {
        int n = xs.length;
        float[] ox = new float[n];
        float[] oy = new float[n];
        float[] oz = new float[n];

        for (int i = 0; i < n; i++) {
            int p = Math.max(0, i - 1);
            int q = Math.min(n - 1, i + 1);
            float tx = xs[q] - xs[p];
            float ty = ys[q] - ys[p];
            float tz = zs[q] - zs[p];
            Vector3f eye = v.toward(xs[i], ys[i], zs[i]);
            float cx = ty * eye.z - tz * eye.y;
            float cy = tz * eye.x - tx * eye.z;
            float cz = tx * eye.y - ty * eye.x;
            float l = (float) Math.sqrt((double) (cx * cx + cy * cy + cz * cz));
            float w = width * 0.5F * taper(i, n) / Math.max(l, 1.0E-6F);
            ox[i] = cx * w;
            oy[i] = cy * w;
            oz[i] = cz * w;
        }

        twice(v.pose, tex, 0.0F, a, (e) -> {
            for (int i = 0; i + 1 < n; i++) {
                int c0 = alpha(c, taper(i, n));
                int c1 = alpha(c, taper(i + 1, n));
                e.v(xs[i] - ox[i], ys[i] - oy[i], zs[i] - oz[i], 0.0F, 0.0F, c0);
                e.v(xs[i] + ox[i], ys[i] + oy[i], zs[i] + oz[i], 0.0F, 1.0F, c0);
                e.v(xs[i + 1] + ox[i + 1], ys[i + 1] + oy[i + 1], zs[i + 1] + oz[i + 1], 1.0F, 1.0F, c1);
                e.v(xs[i + 1] - ox[i + 1], ys[i + 1] - oy[i + 1], zs[i + 1] - oz[i + 1], 1.0F, 0.0F, c1);
            }
        });
    }

    private static float taper(int i, int n) {
        if (n < 3) {
            return 1.0F;
        } else {
            float f = (float) i / (float) (n - 1);
            return smooth(Math.min(f, 1.0F - f) / 0.2F) * 0.85F + 0.15F * (float) (i != 0 && i != n - 1 ? 1 : 0);
        }
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static float smooth(float t) {
        t = clamp(t);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOut(float t) {
        t = 1.0F - clamp(t);
        return 1.0F - t * t * t;
    }

    private static float easeIn(float t) {
        t = clamp(t);
        return t * t * t;
    }

    private static float backOut(float t) {
        t = clamp(t) - 1.0F;
        return 1.0F + t * t * (2.2F * t + 1.2F);
    }

    private static float bump(float t, float a, float b, float c) {
        if (!(t <= a) && !(t >= c)) {
            return t < b ? smooth((t - a) / (b - a)) : 1.0F - smooth((t - b) / (c - b));
        } else {
            return 0.0F;
        }
    }

    static int alpha(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * (float) (c >>> 24)) << 24 | c & 0xFFFFFF;
    }

    private static int whiter(int c) {
        return Fx.lerp(c | 0xFF000000, -1, 0.45F);
    }

    private static int dim(int c, float k) {
        return c & 0xFF000000
                | (int) ((float) (c >> 16 & 255) * k) << 16
                | (int) ((float) (c >> 8 & 255) * k) << 8
                | (int) ((float) (c & 255) * k);
    }

    private static float r(int c) {
        return (float) (c >> 16 & 255) / 255.0F;
    }

    private static float g(int c) {
        return (float) (c >> 8 & 255) / 255.0F;
    }

    private static float b(int c) {
        return (float) (c & 255) / 255.0F;
    }

    private static final class Pop {
        final LivingEntity who;
        final int style;
        long start = System.currentTimeMillis();
        final Random rnd = new Random();
        final boolean mine;
        boolean studio;
        Vec3 at;
        float height;
        float width;
        float yaw;
        float scale;
        float last;
        float owed;
        int done;
        float tiltYaw;
        float tilt;
        final List<Vector3f> bolts = new ArrayList<>();
        final List<Crystal> crystals = new ArrayList<>();
        final List<Piece> pieces = new ArrayList<>();
        final boolean[] burnt;
        final List<Vec3> trailL;
        final List<Vec3> trailR;

        Pop(LivingEntity who, int style, boolean mine) {
            this.burnt = new boolean[TotemFx.PLUMES.size() * 2];
            this.trailL = new ArrayList<>();
            this.trailR = new ArrayList<>();
            this.who = who;
            this.style = style;
            this.mine = mine;
            this.at = who.position();
            this.height = who.getBbHeight();
            this.width = who.getBbWidth();
            this.yaw = who.yBodyRot;
        }

        Pop(Vec3 at, float height, float width, float yaw, int style) {
            this.burnt = new boolean[TotemFx.PLUMES.size() * 2];
            this.trailL = new ArrayList<>();
            this.trailR = new ArrayList<>();
            this.who = null;
            this.style = style;
            this.mine = false;
            this.studio = true;
            this.at = at;
            this.height = height;
            this.width = width;
            this.yaw = yaw;
        }

        boolean once(int moment) {
            if ((this.done & 1 << moment) != 0) {
                return false;
            } else {
                this.done |= 1 << moment;
                return true;
            }
        }
    }

    private static record Crystal(
            float angle, float base, float length, float radius, float lean, float spin, float delay) {}

    private static final class Piece {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        final Vector3f a;
        final Vector3f b;
        final Vector3f c;
        final Vector3f axis;
        float angle;
        float spin;
        float tint;

        Piece(Vector3f a, Vector3f b, Vector3f c, Vector3f axis) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.axis = axis;
        }
    }

    private static record Pal(int light, int main, int deep) {}

    private static record View(Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vector3f forward) {
        Vector3f toward(float x, float y, float z) {
            return this.forward != null ? this.forward : new Vector3f(x, y, z);
        }

        float x(Vec3 w) {
            return (float) (w.x - this.cam.x);
        }

        float y(Vec3 w) {
            return (float) (w.y - this.cam.y);
        }

        float z(Vec3 w) {
            return (float) (w.z - this.cam.z);
        }

        Vec3 world(Vector3f p) {
            return new Vec3(this.cam.x + (double) p.x, this.cam.y + (double) p.y, this.cam.z + (double) p.z);
        }
    }

    private static record Plume(int row, float along, float edge, float reach, float width, float delay, float burn) {}

    private static record Feather(
            Vector3f[] mid,
            Vector3f[] across,
            float length,
            Plume plume,
            int index,
            int side,
            int colourBase,
            int colourTip,
            float open,
            float cut,
            Vector3f lag) {
        Vector3f point(float along) {
            float f = Mth.clamp(along, 0.0F, 1.0F) * 5.0F;
            int i = Math.min(4, (int) f);
            return (new Vector3f(this.mid[i])).lerp(this.mid[i + 1], f - (float) i);
        }

        Vector3f acrossAt(float along) {
            float f = Mth.clamp(along, 0.0F, 1.0F) * 5.0F;
            int i = Math.min(4, (int) f);
            return (new Vector3f(this.across[i])).lerp(this.across[i + 1], f - (float) i);
        }

        Vector3f tangent(float along) {
            int i = Math.min(4, (int) (Mth.clamp(along, 0.0F, 1.0F) * 5.0F));
            return (new Vector3f(this.mid[i + 1])).sub(this.mid[i]).normalize();
        }
    }

    private static final class Emit {
        final BufferBuilder b;
        final Matrix4f m;
        final float k;

        Emit(Matrix4f m, float k) {
            this.b = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            this.m = m;
            this.k = k;
        }

        void v(float x, float y, float z, float u, float v, int c) {
            this.b.addVertex(this.m, x, y, z).setUv(u, v).setColor(TotemFx.alpha(c, this.k));
        }
    }
}
