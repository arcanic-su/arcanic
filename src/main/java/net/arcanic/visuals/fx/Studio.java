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

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.fx.impact.ImpactDraw;
import net.arcanic.visuals.fx.impact.ScreenFx;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class Studio {
    public static final String[] STAGES = new String[] {"Остров", "Арена", "Пустота"};
    public static int stage;
    public static Module selected;
    public static boolean paused;
    public static boolean autoTurn;
    public static float speed;
    public static float yaw;
    public static float pitch;
    private static long draggedAt;
    private static float time;
    private static float step;
    private static boolean struck;
    private static long lastNanos;
    private static int loop;
    private static RemotePlayer attacker;
    private static RemotePlayer victim;
    private static float flashNow;
    private static float flashShown;
    private static float dimNow;
    private static float dimShown;
    private static int flashRgbNow;
    private static int flashRgbShown;
    private static final int[] BLOWS;
    private static final int CRIT_JUMP;
    private static final int POP = 32;
    private static final int BACK = 61;
    private static final int LEAP = 6;
    private static final double LEAP_POWER = 0.6;
    private static final double START_A = -1.75;
    private static final double NEAR_A = -1.15;
    private static final double VICTIM_X = 1.05;
    private static final double RUN_A = -1.35;
    private static final double LAND_A = 0.25;
    private static final Map<String, Play> PLAYS;
    private static final Map<Integer, List<Placed>> BUILT;
    private static final int[][] LANTERNS;
    private static final ArrayDeque<SwingPt> swing;
    private static float lastAttack;
    private static float lastPhi;

    private Studio() {}

    public static List<Module> effects() {
        return List.of(
                Modules.HIT,
                Modules.KILL,
                Modules.TOTEM,
                Modules.MACE,
                Modules.MARKER,
                Modules.SWING,
                Modules.JUMP,
                Modules.HITSOUND,
                Modules.KILLSOUND);
    }

    public static String group(Module m) {
        if (m == Modules.JUMP) {
            return "Движение";
        } else {
            return m != Modules.HITSOUND && m != Modules.KILLSOUND ? "Бой" : "Звуки";
        }
    }

    public static String caption(Module m) {
        if (m == Modules.KILL) {
            return "Два удара и смертельный крит";
        } else if (m == Modules.KILLSOUND) {
            return "Смертельный удар со звуком";
        } else if (m == Modules.TOTEM) {
            return "Смертельный удар — тотем спасает куклу";
        } else if (m == Modules.MACE) {
            return "Прыжок с булавой и удар сверху";
        } else if (m == Modules.MARKER) {
            return "Кукла в прицеле: маркер цели";
        } else if (m == Modules.SWING) {
            return "Взмахи мечом: след удара";
        } else if (m == Modules.JUMP) {
            return "Кукла бегает и прыгает";
        } else {
            return m == Modules.HITSOUND ? "Удары по кукле со звуком" : "Три удара по кукле, последний — крит";
        }
    }

    public static long clockMs() {
        return (long) (time * 1000.0F);
    }

    public static void dragged() {
        draggedAt = System.currentTimeMillis();
    }

    public static void flash(float strength, float r, float g, float b) {
        if (!(strength <= flashNow)) {
            flashNow = strength;
            flashRgbNow = Math.round(clamp(r) * 255.0F) << 16
                    | Math.round(clamp(g) * 255.0F) << 8
                    | Math.round(clamp(b) * 255.0F);
        }
    }

    public static void grade(float desaturate, float darkEdges) {
        dimNow = Math.max(dimNow, desaturate * 0.26F + darkEdges * 0.3F);
    }

    public static int flashOverlay() {
        return flashShown <= 0.01F ? 0 : Math.round(Math.min(0.8F, flashShown) * 255.0F) << 24 | flashRgbShown;
    }

    public static int dimOverlay() {
        return dimShown <= 0.01F ? 0 : Math.round(Math.min(0.55F, dimShown) * 255.0F) << 24;
    }

    private static Story story() {
        if (selected == Modules.JUMP) {
            return Studio.Story.JUMP;
        } else if (selected != Modules.KILL && selected != Modules.KILLSOUND) {
            if (selected == Modules.TOTEM) {
                return Studio.Story.TOTEM;
            } else {
                return selected == Modules.MACE ? Studio.Story.MACE : Studio.Story.HIT;
            }
        } else {
            return Studio.Story.KILL;
        }
    }

    private static float period() {
        return (float) ticks(story()) / 20.0F;
    }

    private static int ticks(Story s) {
        byte var10000;
        switch (s.ordinal()) {
            case 0 -> var10000 = 54;
            case 1 -> var10000 = 68;
            case 2 -> var10000 = 52;
            case 3 -> var10000 = 100;
            case 4 -> var10000 = 80;
            default -> throw new MatchException((String) null, (Throwable) null);
        }

        return var10000;
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static float smooth(float t) {
        t = clamp(t);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float backOut(float t) {
        t = clamp(t) - 1.0F;
        return 1.0F + t * t * (2.7F * t + 1.7F);
    }

    private static int airTicks(double power) {
        double y = 0.0;
        double v = power;
        int t = 0;

        while (true) {
            y += v;
            if (y <= 0.0) {
                return t;
            }

            v = (v - 0.08) * 0.98;
            ++t;
        }
    }

    private static Play play() {
        Story s = story();
        boolean crystals = s == Studio.Story.TOTEM && Modules.TOTEM.style.index() == 2;
        return (Play)
                PLAYS.computeIfAbsent(String.valueOf(s) + (crystals ? ":crystals" : ""), (k) -> work(s, crystals));
    }

    private static Play work(Story s, boolean crystals) {
        int n = ticks(s);
        int air = airTicks(0.6);
        int smash = 6 + air;
        Doll a = new Doll(-1.75, -90.0F);
        Doll v = new Doll(1.05, 90.0F);
        Tick[] at = new Tick[n + 1];
        Tick[] vt = new Tick[n + 1];
        List<Event> events = new ArrayList<>();
        int lastHit = -99;

        for (int i = 0; i <= 2 * n; ++i) {
            int k;
            boolean keep;
            float yawA;
            float pitchA;
            float yawV;
            float pitchV;
            label309:
            {
                k = i % n;
                keep = i >= n && i < 2 * n;
                float sec = (float) k / 20.0F;
                float ph = (float) ((Math.PI * 2D) * (double) k / (double) n);
                if (k == 0) {
                    lastHit = -99;
                }

                yawA = 3.0F * Mth.sin((double) (ph + 1.0F)) + 1.5F * Mth.sin((double) (2.0F * ph + 0.4F));
                pitchA = 2.0F * Mth.sin((double) (2.0F * ph + 2.1F));
                yawV = 3.0F * Mth.sin((double) (ph + 4.0F)) + 1.5F * Mth.sin((double) (2.0F * ph + 2.7F));
                pitchV = 2.0F * Mth.sin((double) (2.0F * ph + 0.3F));
                a.push = v.push = 0.1;
                v.goX = 1.05;
                boolean totem;
                int b;
                switch (s.ordinal()) {
                    case 2:
                        a.goX = a.x;
                        v.goZ = k >= 40 ? 0.0 : (k >= 17 ? 0.7 : 0.0);
                        if ((k == 3 || k == 27) && v.ground) {
                            if (keep) {
                                events.add(new Event(k, 'J', 0, new Vec3(v.x, 0.03, v.z), v.body));
                            }

                            v.jump(0.42);
                        }
                        break label309;
                    case 4:
                        a.push = k < 6 ? 0.13 : 0.1;
                        a.goX = k < 6 ? -1.35 : (k >= 58 ? -1.75 : a.x);
                        if (k == 6) {
                            a.jump(0.6);
                            a.launched = true;
                            a.vx = (0.25 - a.x) / (double) air;
                        }

                        if (k == smash - 1) {
                            a.swing();
                        }

                        if (k == smash) {
                            if (keep) {
                                events.add(new Event(k, 'S', 0, new Vec3(v.x - 0.15, 0.0, v.z), v.body));
                            }

                            v.knock(0.12, 0.42, true);
                            lastHit = k;
                        }

                        if (lastHit < 0 || k - lastHit < 14) {
                            v.goX = v.x;
                        }
                        break label309;
                    default:
                        totem = s == Studio.Story.TOTEM;
                        a.goX = totem
                                ? (k >= 75 ? -1.75 : (k >= 32 ? -1.45 : -1.15))
                                : (k >= (s == Studio.Story.HIT ? 41 : 54) ? -1.75 : -1.15);
                        if (k == CRIT_JUMP) {
                            a.jump(0.42);
                        }

                        b = 0;
                }

                for (; b < BLOWS.length; ++b) {
                    if (k == BLOWS[b] - 1) {
                        a.swing();
                    }

                    if (k == BLOWS[b]) {
                        if (keep) {
                            events.add(new Event(k, 'B', b, new Vec3(v.x, v.y, v.z), v.body));
                        }

                        boolean crit = b == BLOWS.length - 1;
                        v.knock(
                                totem ? (crit ? 0.07 : 0.045) : (crit ? 0.1 : 0.06),
                                totem ? (crit ? 0.25 : 0.2) : (crit ? 0.32 : 0.2),
                                true);
                        lastHit = k;
                        if (crit && s == Studio.Story.KILL) {
                            v.dying = true;
                        }
                    }
                }

                label242:
                {
                    if (totem) {
                        if (k >= 86) {
                            break label242;
                        }
                    } else if (k - lastHit >= 10 && !v.dying) {
                        break label242;
                    }

                    v.goX = v.x;
                }

                if (s == Studio.Story.KILL) {
                    if (k == 61) {
                        if (keep) {
                            events.add(new Event(k, 'R', 0, new Vec3(1.05, 0.9, 0.0), 90.0F));
                        }

                        v.set(1.05, 0.0, 90.0F);
                        v.dying = v.gone = false;
                        v.death = (float) (v.hurt = 0);
                    }

                    v.scale = k >= 61 ? Math.max(0.02F, backOut((float) (k - 61) / 6.0F)) : 1.0F;
                }

                if (totem) {
                    if (k == 32) {
                        if (keep) {
                            events.add(new Event(k, 'P', 0, new Vec3(v.x, v.y, v.z), v.body));
                        }

                        a.knock(-0.12, 0.0, false);
                    }

                    float sp = (float) (k - 32) / 20.0F;
                    v.crouch = v.armsUp = false;
                    if (crystals) {
                        v.crouch = k >= 32 && k < 50;
                        v.armsUp = k >= 51 && k < 70;
                        pitchV -= 25.0F * smooth((sp - 0.95F) / 0.25F) * (1.0F - smooth((sec - 3.2F) / 0.3F));
                    } else if (k >= 32) {
                        float lift = smooth(sp / 0.25F) * (1.0F - smooth((sec - 3.1F) / 0.3F));
                        float up = 0.6F * backOut((sp - 0.05F) / 0.7F) * (1.0F - smooth((sec - 3.0F) / 0.6F));
                        float hover = 0.04F
                                * Mth.sin((double) (sp * 5.0F))
                                * smooth((sp - 0.5F) / 0.3F)
                                * (1.0F - smooth((sec - 2.8F) / 0.3F));
                        v.hold((double) Math.max(0.0F, up + hover));
                        v.armsUp = k >= 34 && k < 67;
                        pitchV -= 28.0F * lift;
                        yawV += 360.0F * smooth(sp / 1.25F);
                    }
                }
            }

            a.tick(v.x, v.y + 1.2, v.z, yawA, pitchA);
            v.tick(a.x, a.y + 1.62, a.z, yawV, pitchV);
            if (s == Studio.Story.JUMP && v.landed && keep) {
                events.add(new Event(k, 'L', 0, new Vec3(v.x, 0.03, v.z), v.body));
            }

            if (i >= n) {
                at[i - n] = a.keep();
                vt[i - n] = v.keep();
            }
        }

        seam(at);
        seam(vt);
        return new Play(at, vt, events);
    }

    private static void seam(Tick[] t) {
        int n = t.length - 1;
        Tick a = t[0];
        Tick b = t[n];
        double ex = b.x - a.x;
        double ey = b.y - a.y;
        double ez = b.z - a.z;
        double ecx = b.cloakX - a.cloakX;
        double ecy = b.cloakY - a.cloakY;
        double ecz = b.cloakZ - a.cloakZ;
        float eb = Mth.wrapDegrees(b.body - a.body);
        float eh = Mth.wrapDegrees(b.head - a.head);
        float ep = b.pitch - a.pitch;
        float es = b.walkSpeed - a.walkSpeed;
        float ebob = b.bob - a.bob;
        float step = 9.43138F;
        float flap = ((float) Math.PI / 3F);
        float ew = b.walkPos - a.walkPos;
        float ed = b.walkDist - a.walkDist;
        ew -= (float) Math.round(ew / step) * step;
        ed -= (float) Math.round(ed / flap) * flap;

        for (int i = 1; i <= n; ++i) {
            float f = (float) i / (float) n;
            Tick c = t[i];
            c.x -= ex * (double) f;
            c.y = Math.max(0.0, c.y - ey * (double) f);
            c.z -= ez * (double) f;
            c.cloakX -= ecx * (double) f;
            c.cloakY -= ecy * (double) f;
            c.cloakZ -= ecz * (double) f;
            c.body -= eb * f;
            c.head -= eh * f;
            c.pitch -= ep * f;
            c.walkSpeed -= es * f;
            c.bob -= ebob * f;
            c.walkPos -= ew * f;
            c.walkDist -= ed * f;
        }
    }

    private static Shown shown(Tick[] ticks, float t) {
        int n = ticks.length - 1;
        float f = Math.max(0.0F, Math.min((float) n - 1.0E-4F, t * 20.0F));
        int k = (int) f;
        float p = f - (float) k;
        Tick a = ticks[k];
        Tick b = ticks[k + 1];
        Shown s = new Shown();
        s.x = Mth.lerp((double) p, a.x, b.x);
        s.y = Mth.lerp((double) p, a.y, b.y);
        s.z = Mth.lerp((double) p, a.z, b.z);
        s.body = Mth.rotLerp(p, a.body, b.body);
        s.head = Mth.rotLerp(p, a.head, b.head);
        s.pitch = Mth.lerp(p, a.pitch, b.pitch);
        s.walkPos = b.walkPos - b.walkSpeed * (1.0F - p);
        s.walkSpeed = Mth.lerp(p, a.walkSpeed, b.walkSpeed);
        float swung = b.attack - a.attack;
        s.attack = a.attack + (swung < 0.0F ? swung + 1.0F : swung) * p;
        s.death = b.death > 0.0F ? Mth.lerp(p, a.death, b.death) : 0.0F;
        s.scale = Mth.lerp(p, a.scale, b.scale);
        s.hurt = b.hurt;
        s.crouch = b.crouch;
        s.gone = b.gone;
        s.armsUp = b.armsUp;
        double cx = Mth.lerp((double) p, a.cloakX, b.cloakX) - s.x;
        double cy = Mth.lerp((double) p, a.cloakY, b.cloakY) - s.y;
        double cz = Mth.lerp((double) p, a.cloakZ, b.cloakZ) - s.z;
        float r = (float) Math.toRadians((double) s.body);
        float sin = Mth.sin((double) r);
        float cos = -Mth.cos((double) r);
        s.capeFlap = Mth.clamp((float) cy * 10.0F, -6.0F, 32.0F)
                + Mth.sin((double) (Mth.lerp(p, a.walkDist, b.walkDist) * 6.0F)) * 32.0F * Mth.lerp(p, a.bob, b.bob);
        s.capeLean = Mth.clamp((float) (cx * (double) sin + cz * (double) cos) * 100.0F, 0.0F, 150.0F);
        s.capeLean2 = Mth.clamp((float) (cx * (double) cos - cz * (double) sin) * 100.0F, -20.0F, 20.0F);
        return s;
    }

    private static float sinceBlow(float t) {
        float since = 9.0F;

        for (Event e : play().events()) {
            if ((e.kind() == 'B' || e.kind() == 'S') && t >= (float) e.tick() / 20.0F) {
                since = Math.min(since, t - (float) e.tick() / 20.0F);
            }
        }

        return since;
    }

    public static void frame() {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min(0.1F, (float) (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        if (autoTurn && System.currentTimeMillis() - draggedAt > 2500L) {
            yaw += dt * 9.0F;
        }

        step = paused ? 0.0F : dt * speed;
        if (!paused) {
            float p = period();
            float before = time % p;
            time += dt * speed;
            float after = time % p;
            boolean wrapped = after < before;
            if (wrapped) {
                ++loop;
            }

            for (Event e : play().events()) {
                if (passed(before, after, wrapped, (float) e.tick() / 20.0F)) {
                    switch (e.kind()) {
                        case 'B':
                            blow(e.index(), e.at());
                            break;
                        case 'P':
                            Modules.TOTEM.studioPop(e.at(), 1.8F, 0.6F, e.yaw());
                            break;
                        case 'R':
                            back(e.at());
                            break;
                        case 'S':
                            smash(e.at());
                            break;
                        default:
                            jumpRing(e.kind() == 'L', e.at());
                    }
                }
            }
        }
    }

    private static boolean passed(float before, float after, boolean wrapped, float at) {
        return wrapped ? at > before || at <= after : at > before && at <= after;
    }

    private static void blow(int i, Vec3 feet) {
        boolean last = i == BLOWS.length - 1;
        Vec3 chest = feet.add(-0.32, 1.15, 0.0);
        struck = true;
        if (last) {
            ImpactDraw.studioShake(1.4F);
        }

        Fx.studio = true;

        try {
            if (selected == Modules.HIT) {
                Modules.HIT.preview(
                        chest,
                        (new Vec3(1.0, -0.1, 0.0)).normalize(),
                        last,
                        new AABB(feet.x - 0.3, feet.y, feet.z - 0.3, feet.x + 0.3, feet.y + 1.8, feet.z + 0.3));
            } else if (selected == Modules.HITSOUND) {
                Modules.HITSOUND.play(Modules.HITSOUND.current());
            } else if (story() == Studio.Story.KILL) {
                if (!last) {
                    Effects.hit("Искры", chest, Theme.accent2(), 0.7F, false);
                } else if (selected == Modules.KILL) {
                    Modules.KILL.preview(feet);
                } else {
                    Modules.KILLSOUND.play(Modules.KILLSOUND.current());
                }
            } else if (story() == Studio.Story.TOTEM) {
                Effects.hit("Искры", chest, Theme.accent2(), last ? 1.0F : 0.7F, last);
            }
        } finally {
            Fx.studio = false;
        }
    }

    private static void smash(Vec3 at) {
        struck = true;
        Fx.studio = true;

        try {
            Modules.MACE.studioSmash(at, 0.85F, ground(), dust());
        } finally {
            Fx.studio = false;
        }
    }

    private static void back(Vec3 at) {
        Fx.studio = true;

        try {
            for (int i = 0; i < 14; ++i) {
                Vec3 d = Fx.dir();
                Fx.spawn()
                        .at(at.add(d.scale(0.3)))
                        .vel(d.x * 0.8, Math.abs(d.y) * 0.8, d.z * 0.8)
                        .tex(Fx.Tex.GLOW)
                        .size(0.18F, 0.0F)
                        .life(0.5F)
                        .drag(3.0F)
                        .colour(0xB0FFFFFF, 0xFFFFFF);
            }
        } finally {
            Fx.studio = false;
        }
    }

    private static void jumpRing(boolean landing, Vec3 at) {
        VisualFx.JumpCircles m = Modules.JUMP;
        if (!landing || m.landing.on()) {
            Fx.studio = true;

            try {
                Effects.jumpRing(
                        (String) m.style.get(),
                        at,
                        VisualFx.colourFor((String) m.colourMode.get(), m.colour, landing ? 0.5F : 0.0F),
                        m.radius.f() * (landing ? 0.7F : 1.0F),
                        m.life.f() * (landing ? 0.7F : 1.0F),
                        m.spin.on(),
                        !landing && m.motes.on());
            } finally {
                Fx.studio = false;
            }
        }
    }

    public static void reset() {
        Fx.clearStudio();
        ArcEffects.clearStudio();
        HitFx.clearStudio();
        KillFx.clearStudio();
        Modules.TOTEM.clearStudio();
        Modules.MACE.clearStudio();
        swing.clear();
        lastAttack = 0.0F;
        lastPhi = -99.0F;
        struck = false;
        dimShown = 0.0F;
        flashShown = 0.0F;
        time = 0.0F;
        loop = 0;
        lastNanos = 0L;
    }

    private static void dolls() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            if (attacker == null || attacker.level() != mc.level) {
                attacker = new RemotePlayer(mc.level, mc.player.getGameProfile());
                victim = new RemotePlayer(
                        mc.level, new GameProfile(UUID.nameUUIDFromBytes("arcanic:doll".getBytes()), "Doll"));
            }
        }
    }

    private static void equip(float t) {
        hold(attacker, EquipmentSlot.MAINHAND, story() == Studio.Story.MACE ? Items.MACE : Items.NETHERITE_SWORD);
        hold(victim, EquipmentSlot.OFFHAND, story() == Studio.Story.TOTEM && t < 1.6F ? Items.TOTEM_OF_UNDYING : null);
    }

    private static void hold(RemotePlayer p, EquipmentSlot slot, Item item) {
        ItemStack now = p.getItemBySlot(slot);
        if (item == null) {
            if (now.isEmpty()) {
                return;
            }
        } else if (now.is(item)) {
            return;
        }

        p.setItemSlot(slot, item == null ? ItemStack.EMPTY : new ItemStack(item));
    }

    private static EntityRenderState state(RemotePlayer p) {
        EntityRenderer r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
        EntityRenderState s = r.createRenderState(p, 1.0F);
        s.nameTag = null;
        s.lightCoords = 15728880;
        s.shadowPieces.clear();
        s.shadowRadius = 0.0F;
        return s;
    }

    private static void act(EntityRenderState s, Shown d) {
        s.ageInTicks = time * 20.0F;
        if (s instanceof LivingEntityRenderState l) {
            l.bodyRot = d.body;
            l.yRot = Mth.wrapDegrees(d.head - d.body);
            l.xRot = d.pitch;
            l.walkAnimationPos = d.walkPos;
            l.walkAnimationSpeed = d.walkSpeed;
            l.deathTime = d.death;
            l.hasRedOverlay = d.hurt > 0 || d.death > 0.0F;
            l.pose = d.crouch ? Pose.CROUCHING : Pose.STANDING;
        }

        if (s instanceof HumanoidRenderState h) {
            h.attackTime = d.attack;
            h.attackArm = HumanoidArm.RIGHT;
            h.isCrouching = d.crouch;
            if (d.armsUp) {
                h.rightArmPose = h.leftArmPose = ArmPose.THROW_TRIDENT;
            }
        }

        if (s instanceof AvatarRenderState p) {
            p.capeFlap = d.capeFlap;
            p.capeLean = d.capeLean;
            p.capeLean2 = d.capeLean2;
        }
    }

    private static void place(
            EntityRenderDispatcher dispatcher,
            FeatureRenderDispatcher features,
            CameraRenderState cam,
            PoseStack ps,
            EntityRenderState s,
            Shown d) {
        ps.pushPose();
        ps.translate(d.x, d.y, d.z);
        if (d.scale != 1.0F) {
            ps.scale(d.scale, d.scale, d.scale);
        }

        dispatcher.submit(s, cam, 0.0, 0.0, 0.0, ps, features.getSubmitNodeStorage());
        ps.popPose();
    }

    private static BlockState ground() {
        BlockState var10000;
        switch (stage) {
            case 0 -> var10000 = Blocks.GRASS_BLOCK.defaultBlockState();
            case 1 -> var10000 = Blocks.DEEPSLATE_TILES.defaultBlockState();
            default -> var10000 = Blocks.STONE.defaultBlockState();
        }

        return var10000;
    }

    private static int dust() {
        int var10000;
        switch (stage) {
            case 0 -> var10000 = -8494009;
            case 1 -> var10000 = -11513768;
            default -> var10000 = -7568264;
        }

        return var10000;
    }

    private static List<Placed> blocks() {
        return (List) BUILT.computeIfAbsent(stage, (s) -> s == 0 ? island() : (s == 1 ? arena() : List.of()));
    }

    private static List<Placed> island() {
        List<Placed> out = new ArrayList<>();
        Random r = new Random(7L);
        BlockState[] flowers = new BlockState[] {
            Blocks.POPPY.defaultBlockState(),
            Blocks.DANDELION.defaultBlockState(),
            Blocks.CORNFLOWER.defaultBlockState(),
            Blocks.OXEYE_DAISY.defaultBlockState(),
            Blocks.ALLIUM.defaultBlockState(),
            Blocks.AZURE_BLUET.defaultBlockState(),
            Blocks.LILY_OF_THE_VALLEY.defaultBlockState()
        };

        for (int i = -4; i <= 4; ++i) {
            for (int k = -3; k <= 2; ++k) {
                double dx = ((double) i + 0.5 - 0.25) / 3.4;
                double dz = ((double) k + 0.5) / 2.35;
                double d = dx * dx + dz * dz;
                if (!(d > 1.0 + (r.nextDouble() - 0.5) * 0.3)) {
                    int depth = 1 + (int) Math.round((1.0 - d) * 2.6 + r.nextDouble() * 0.7);
                    int earth = 1 + r.nextInt(2);

                    for (int j = 0; j < depth; ++j) {
                        BlockState st = j == 0
                                ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : (j <= earth
                                        ? Blocks.DIRT.defaultBlockState()
                                        : (r.nextInt(4) == 0
                                                ? Blocks.ANDESITE.defaultBlockState()
                                                : Blocks.STONE.defaultBlockState()));
                        out.add(new Placed(i, -1 - j, k, st, true));
                    }

                    boolean path = (k == -1 || k == 0) && (double) i + 0.5 > -2.8 && (double) i + 0.5 < 3.0;
                    if (!path && !(r.nextFloat() > 0.55F)) {
                        BlockState plant = i == -3 && k == 1 || i == 3 && k == -2
                                ? (i < 0 ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA).defaultBlockState()
                                : (r.nextFloat() < 0.5F
                                        ? Blocks.SHORT_GRASS.defaultBlockState()
                                        : flowers[r.nextInt(flowers.length)]);
                        out.add(new Placed(i, 0, k, plant, false));
                    }
                }
            }
        }

        return out;
    }

    private static List<Placed> arena() {
        List<Placed> out = new ArrayList<>();

        for (int i = -4; i <= 4; ++i) {
            for (int k = -4; k <= 3; ++k) {
                double d = Math.hypot((double) i + 0.5 - 0.25, (double) k + 0.5);
                if (!(d > 3.25)) {
                    BlockState top = d < 0.9
                            ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                            : (d > 2.45
                                    ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                                    : Blocks.DEEPSLATE_TILES.defaultBlockState());
                    out.add(new Placed(i, -1, k, top, true));
                    out.add(new Placed(i, -2, k, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), true));
                    if (d < 2.2) {
                        out.add(new Placed(i, -3, k, Blocks.DEEPSLATE.defaultBlockState(), true));
                    }
                }
            }
        }

        for (int[] at : LANTERNS) {
            out.add(new Placed(at[0], 0, at[1], Blocks.LANTERN.defaultBlockState(), false));
        }

        return out;
    }

    private static long key(int x, int y, int z) {
        return (long) (x + 64) << 20 | (long) (y + 64) << 10 | (long) (z + 64);
    }

    public static void register() {
        SpecialGuiElementRegistry.register((ctx) -> new Renderer(ctx.vertexConsumers()));
    }

    static {
        selected = Modules.HIT;
        autoTurn = true;
        speed = 1.0F;
        yaw = 30.0F;
        pitch = 14.0F;
        flashRgbNow = 0xFFFFFF;
        flashRgbShown = 0xFFFFFF;
        BLOWS = new int[] {8, 17, 29};
        CRIT_JUMP = BLOWS[2] - 8;
        PLAYS = new HashMap<>();
        BUILT = new HashMap<>();
        LANTERNS = new int[][] {{-2, 1}, {-2, -2}, {2, 1}, {2, -2}};
        swing = new ArrayDeque<>();
        lastPhi = -99.0F;
    }

    private static enum Story {
        HIT,
        KILL,
        JUMP,
        TOTEM,
        MACE;
    }

    private static final class Tick {
        double x;
        double y;
        double z;
        double cloakX;
        double cloakY;
        double cloakZ;
        float body;
        float head;
        float pitch;
        float walkPos;
        float walkSpeed;
        float attack;
        float bob;
        float walkDist;
        float death;
        float scale = 1.0F;
        int hurt;
        boolean crouch;
        boolean gone;
        boolean armsUp;
    }

    private static record Event(int tick, char kind, int index, Vec3 at, float yaw) {}

    private static record Play(Tick[] attacker, Tick[] victim, List<Event> events) {}

    private static final class Doll {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        double cloakX;
        double cloakY;
        double cloakZ;
        float head;
        float body;
        float pitch;
        float walkPos;
        float walkSpeed;
        float attack;
        float bob;
        float walkDist;
        float death;
        float scale = 1.0F;
        int swingTime;
        int hurt;
        boolean ground = true;
        boolean swinging;
        boolean launched;
        boolean dying;
        boolean crouch;
        boolean gone;
        boolean armsUp;
        boolean landed;
        double goX;
        double goZ;
        double push;

        Doll(double x, float yaw) {
            this.x = this.goX = this.cloakX = x;
            this.head = this.body = yaw;
        }

        void swing() {
            if (!this.swinging || this.swingTime >= 3 || this.swingTime < 0) {
                this.swingTime = -1;
                this.swinging = true;
            }
        }

        void jump(double power) {
            if (this.ground) {
                this.vy = power;
                this.ground = false;
            }
        }

        void knock(double kx, double ky, boolean hurts) {
            this.vx = this.vx / 2.0 + kx;
            this.vz /= 2.0;
            if (this.ground && ky > 0.0) {
                this.vy = ky;
                this.ground = false;
            }

            if (hurts) {
                this.hurt = 10;
            }
        }

        void hold(double h) {
            if (!(h <= this.y)) {
                this.y = h;
                this.vy = 0.0;
                this.ground = false;
            }
        }

        void set(double px, double pz, float yaw) {
            this.x = this.goX = this.cloakX = px;
            this.z = this.goZ = this.cloakZ = pz;
            this.y = this.cloakY = this.vx = this.vy = this.vz = 0.0;
            this.head = this.body = yaw;
            this.pitch = 0.0F;
            this.ground = true;
        }

        void tick(double lx, double ly, double lz, float yawBy, float pitchBy) {
            if (this.hurt > 0) {
                --this.hurt;
            }

            double ox = this.x;
            double oz = this.z;
            double rx = this.goX - this.x;
            double rz = this.goZ - this.z;
            double rem = Math.hypot(rx, rz);
            if (!this.launched && !this.dying && this.push > 0.0 && rem > 0.02) {
                double stop = Math.hypot(this.vx, this.vz) * (this.ground ? 1.2 : 10.0);
                double a = (this.ground ? this.push : this.push * 0.2)
                        * 0.98
                        * (double) Studio.clamp((float) ((rem - stop) / 0.12));
                this.vx += rx / rem * a;
                this.vz += rz / rem * a;
            }

            this.x += this.vx;
            this.z += this.vz;
            this.y += this.vy;
            this.landed = false;
            if (!this.ground) {
                if (this.y <= 0.0) {
                    this.y = this.vy = 0.0;
                    this.ground = this.landed = true;
                    this.launched = false;
                } else {
                    this.vy = (this.vy - 0.08) * 0.98;
                }
            }

            double drag = this.launched ? 1.0 : (this.ground ? 0.546 : 0.91);
            this.vx *= drag;
            this.vz *= drag;
            if (this.swinging) {
                if (++this.swingTime >= 6) {
                    this.swingTime = 0;
                    this.swinging = false;
                }
            } else {
                this.swingTime = 0;
            }

            this.attack = (float) this.swingTime / 6.0F;
            double dx = lx - this.x;
            double dz = lz - this.z;
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F + yawBy;
            float eye = this.crouch ? 1.27F : 1.62F;
            float look = (float)
                    (-Math.toDegrees(Math.atan2(ly - (this.y + (double) eye), Math.max(0.3, Math.hypot(dx, dz)))));
            this.head += Mth.clamp(Mth.wrapDegrees(yaw - this.head), -25.0F, 25.0F);
            this.pitch += Mth.clamp(Mth.clamp(look + pitchBy, -90.0F, 90.0F) - this.pitch, -20.0F, 20.0F);
            double mx = this.x - ox;
            double mz = this.z - oz;
            float target = this.body;
            if (mx * mx + mz * mz > 0.0025) {
                float move = (float) Math.toDegrees(Math.atan2(mz, mx)) - 90.0F;
                target = Math.abs(Mth.wrapDegrees(this.head - move)) > 95.0F ? move - 180.0F : move;
            }

            if (this.attack > 0.0F) {
                target = this.head;
            }

            this.body += Mth.wrapDegrees(target - this.body) * 0.3F;
            float off = Mth.wrapDegrees(this.head - this.body);
            if (Math.abs(off) > 50.0F) {
                this.body += off - Math.signum(off) * 50.0F;
            }

            float dist = (float) Math.hypot(mx, mz);
            this.walkSpeed += (Math.min(dist * 4.0F, 1.0F) - this.walkSpeed) * 0.4F;
            this.walkPos += this.walkSpeed;
            this.walkDist += dist * 0.6F;
            this.bob += ((this.ground && !this.dying ? Math.min(0.1F, (float) Math.hypot(this.vx, this.vz)) : 0.0F)
                            - this.bob)
                    * 0.4F;
            this.cloakX += (this.x - this.cloakX) * 0.25;
            this.cloakY += (this.y - this.cloakY) * 0.25;
            this.cloakZ += (this.z - this.cloakZ) * 0.25;
            if (this.dying && ++this.death >= 20.0F) {
                this.gone = true;
            }
        }

        Tick keep() {
            Tick t = new Tick();
            t.x = this.x;
            t.y = this.y;
            t.z = this.z;
            t.cloakX = this.cloakX;
            t.cloakY = this.cloakY;
            t.cloakZ = this.cloakZ;
            t.body = this.body;
            t.head = this.head;
            t.pitch = this.pitch;
            t.walkPos = this.walkPos;
            t.walkSpeed = this.walkSpeed;
            t.attack = this.attack;
            t.bob = this.bob;
            t.walkDist = this.walkDist;
            t.death = this.death;
            t.scale = this.scale;
            t.hurt = this.hurt;
            t.crouch = this.crouch;
            t.gone = this.gone;
            t.armsUp = this.armsUp;
            return t;
        }
    }

    private static final class Shown {
        double x;
        double y;
        double z;
        float body;
        float head;
        float pitch;
        float walkPos;
        float walkSpeed;
        float attack;
        float death;
        float scale;
        float capeFlap;
        float capeLean;
        float capeLean2;
        int hurt;
        boolean crouch;
        boolean gone;
        boolean armsUp;
    }

    private static record Placed(int x, int y, int z, BlockState state, boolean solid) {}

    private static record SwingPt(Vec3 in, Vec3 out, float t, float frac) {}

    public static record Picture(int x0, int y0, int x1, int y1, float scale) implements PictureInPictureRenderState {
        public ScreenRectangle scissorArea() {
            return null;
        }

        public ScreenRectangle bounds() {
            return PictureInPictureRenderState.getBounds(this.x0, this.y0, this.x1, this.y1, (ScreenRectangle) null);
        }
    }

    private static final class Renderer extends PictureInPictureRenderer<Picture> {
        private static MultiBufferSource.BufferSource solid;

        Renderer(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }

        public Class<Picture> getRenderStateClass() {
            return Picture.class;
        }

        protected float getTranslateY(int height, int guiScale) {
            return (float) height / 2.0F;
        }

        protected boolean textureIsReadyToBlit(Picture state) {
            return false;
        }

        protected String getTextureLabel() {
            return "arcanic effects studio";
        }

        protected void renderToTexture(Picture state, PoseStack ps) {
            Minecraft mc = Minecraft.getInstance();
            Studio.dolls();
            if (Studio.attacker != null) {
                float t = Studio.time % Studio.period();
                Play play = Studio.play();
                Shown a = Studio.shown(play.attacker(), t);
                Shown v = Studio.shown(play.victim(), t);
                Studio.equip(t);
                Studio.dimNow = 0.0F;
                Studio.flashNow = 0.0F;
                mc.gameRenderer.getLighting().setupFor(Entry.ENTITY_IN_UI);
                float[] shake = ImpactDraw.studioShakeOffset();
                float shakeYaw = shake == null ? 0.0F : shake[0];
                float shakePitch = shake == null ? 0.0F : shake[1];
                Quaternionf turn = (new Quaternionf())
                        .rotateX((float) Math.toRadians((double) (Studio.pitch + shakePitch * 1.2F)))
                        .rotateY((float) Math.toRadians((double) (Studio.yaw + shakeYaw * 1.2F)));
                ps.mulPose((Quaternionfc) Axis.ZP.rotation((float) Math.PI));
                ps.mulPose((Quaternionfc) turn);
                ps.translate(-0.25 + (double) shakeYaw * 0.02, -0.8 + (double) shakePitch * 0.02, 0.0);
                Quaternionf back = (new Quaternionf(turn)).conjugate();
                Vector3f left = back.transform(new Vector3f(1.0F, 0.0F, 0.0F));
                Vector3f up = back.transform(new Vector3f(0.0F, 1.0F, 0.0F));
                Vector3f forward = back.transform(new Vector3f(0.0F, 0.0F, 1.0F));
                this.stage(ps, a, v);
                CameraRenderState cam = new CameraRenderState();
                cam.orientation = new Quaternionf(back);
                cam.pos = Vec3.ZERO;
                FeatureRenderDispatcher features = mc.gameRenderer.getFeatureRenderDispatcher();
                EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
                EntityRenderState as = Studio.state(Studio.attacker);
                EntityRenderState vs = Studio.state(Studio.victim);
                Studio.act(as, a);
                Studio.act(vs, v);
                Studio.place(dispatcher, features, cam, ps, as, a);
                if (!v.gone) {
                    Studio.place(dispatcher, features, cam, ps, vs, v);
                }

                features.renderAllFeatures();
                this.bufferSource.endBatch();
                mc.renderBuffers().bufferSource().endBatch();
                Show.View view = new Show.View(ps, ps.last().pose(), Vec3.ZERO, left, up, forward);
                boolean hit = Studio.struck;
                Studio.struck = false;
                float since = Studio.sinceBlow(t);
                Studio.victim.hurtTime = v.hurt;
                if (Studio.story() == Studio.Story.TOTEM) {
                    Modules.TOTEM.studioFollow(new Vec3(v.x, v.y, v.z), v.body);
                }

                boolean wasDraw = ImpactDraw.studio;
                boolean wasScreen = ScreenFx.studio;
                ScreenFx.studio = true;
                ImpactDraw.studio = true;
                Fx.studio = true;

                try {
                    HitFx.studio(view);
                    KillFx.studio(view);
                    Modules.TOTEM.studio(view);
                    Modules.MACE.studio(view);
                    if (Studio.selected == Modules.MARKER && !v.gone) {
                        TargetEsp.studio(
                                this.bufferSource,
                                ps.last().pose(),
                                left,
                                up,
                                forward,
                                Studio.victim,
                                new AABB(v.x - 0.3, v.y, v.z - 0.3, v.x + 0.3, v.y + 1.8, v.z + 0.3),
                                t,
                                Studio.clamp(1.0F - since / 0.5F),
                                hit,
                                Studio.step);
                        this.bufferSource.endBatch();
                    }
                } finally {
                    Fx.studio = false;
                    ImpactDraw.studio = wasDraw;
                    ScreenFx.studio = wasScreen;
                }

                Studio.flashShown = Studio.flashNow;
                Studio.flashRgbShown = Studio.flashRgbNow;
                Studio.dimShown = Studio.dimNow;
                if (Studio.selected == Modules.SWING) {
                    this.swingTrail(ps, t, a.x, a.y, a.attack);
                }

                this.motes(ps, left, up);
                Vector3f eye = back.transform(new Vector3f(0.0F, 0.0F, 40.0F));
                Vec3 camPos = new Vec3((double) eye.x(), (double) eye.y(), (double) eye.z());
                ps.pushPose();
                ps.translate(camPos.x, camPos.y, camPos.z);
                Fx.renderStudio(ps, this.bufferSource, camPos, left, up, Studio.paused ? 0.0F : Studio.speed);
                ArcEffects.renderStudio(ps.last(), this.bufferSource, camPos);
                ps.popPose();
            }
        }

        private void stage(PoseStack ps, Shown a, Shown v) {
            List<Placed> placed = Studio.blocks();
            if (!placed.isEmpty()) {
                BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();
                if (solid == null) {
                    solid = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
                }

                for (Placed b : placed) {
                    ps.pushPose();
                    ps.translate((float) b.x(), (float) b.y(), (float) b.z());
                    renderer.renderSingleBlock(b.state(), ps, solid, 15728880, OverlayTexture.NO_OVERLAY);
                    ps.popPose();
                }

                solid.endBatch();
                shade(ps.last(), placed);
            }

            PoseStack.Pose pose = ps.last();
            int accent = Theme.accent() | 0xFF000000;
            int accent2 = Theme.accent2() | 0xFF000000;
            float floor = Studio.stage == 2 ? 0.001F : 0.012F;
            VertexConsumer glow = this.bufferSource.getBuffer(Fx.type(Fx.Tex.GLOW, true));
            int pool = Studio.stage == 0 ? 28 : 48;
            Fx.quad(
                    glow,
                    pose,
                    0.25F,
                    floor,
                    0.0F,
                    4.2F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    3.2F,
                    Fx.pm(accent & 0xFFFFFF | pool << 24));
            if (Studio.stage == 1) {
                VertexConsumer ring = this.bufferSource.getBuffer(Fx.type(Fx.Tex.RING_SOFT, true));
                float big = 3.6627908F;
                float small = 1.2209301F;
                Fx.quad(
                        ring,
                        pose,
                        0.25F,
                        floor + 0.002F,
                        0.0F,
                        big,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        big,
                        Fx.pm(accent & 0xFFFFFF | 0xB0000000));
                Fx.quad(
                        ring,
                        pose,
                        0.25F,
                        floor + 0.003F,
                        0.0F,
                        small,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        small,
                        Fx.pm(accent2 & 0xFFFFFF | 0x90000000));
                VertexConsumer light = this.bufferSource.getBuffer(Fx.type(Fx.Tex.GLOW, true));

                for (int i = 0; i < Studio.LANTERNS.length; ++i) {
                    int[] at = Studio.LANTERNS[i];
                    float flicker = 0.85F
                            + 0.15F
                                    * Mth.sin((double) (Studio.time * 9.0F + (float) i * 2.3F))
                                    * Mth.sin((double) (Studio.time * 5.3F + (float) i));
                    int c = Fx.pm((int) (112.0F * flicker) << 24 | 16758881);
                    Fx.quad(
                            light,
                            pose,
                            (float) at[0] + 0.5F,
                            0.3F,
                            (float) at[1] + 0.5F,
                            0.55F,
                            0.0F,
                            0.0F,
                            0.0F,
                            0.55F,
                            0.0F,
                            c);
                    Fx.quad(
                            light,
                            pose,
                            (float) at[0] + 0.5F,
                            0.3F,
                            (float) at[1] + 0.5F,
                            0.0F,
                            0.0F,
                            0.55F,
                            0.0F,
                            0.55F,
                            0.0F,
                            c);
                    Fx.quad(
                            light,
                            pose,
                            (float) at[0] + 0.5F,
                            floor + 0.004F,
                            (float) at[1] + 0.5F,
                            0.9F,
                            0.0F,
                            0.0F,
                            0.0F,
                            0.0F,
                            0.9F,
                            c);
                }
            } else if (Studio.stage == 2) {
                VertexConsumer flat = this.bufferSource.getBuffer(Fx.type(Fx.Tex.WHITE, false));
                float w = 0.012F;

                for (int i = -5; i <= 5; ++i) {
                    float k = 1.0F - (float) Math.abs(i) / 6.0F;
                    int col = (int) (34.0F * k) << 24 | 0xFFFFFF;
                    Fx.quad(flat, pose, (float) i + 0.25F, 0.001F, 0.0F, w, 0.0F, 0.0F, 0.0F, 0.0F, 4.2F, col);
                    Fx.quad(flat, pose, 0.25F, 0.001F, (float) i * 0.8F, 5.2F, 0.0F, 0.0F, 0.0F, 0.0F, w, col);
                }

                VertexConsumer ring = this.bufferSource.getBuffer(Fx.type(Fx.Tex.RING_SOFT, true));
                float big = 3.372093F;
                Fx.quad(
                        ring,
                        pose,
                        0.25F,
                        0.002F,
                        0.0F,
                        big,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        big,
                        Fx.pm(accent & 0xFFFFFF | 1610612736));
            }

            VertexConsumer shadow = this.bufferSource.getBuffer(Fx.type(Fx.Tex.GLOW, false));
            this.blot(shadow, pose, (float) a.x, (float) a.z, (float) a.y, floor + 0.004F, 1.0F);
            if (!v.gone) {
                this.blot(shadow, pose, (float) v.x, (float) v.z, (float) v.y, floor + 0.004F, v.scale);
            }
        }

        private void blot(
                VertexConsumer vc, PoseStack.Pose pose, float x, float z, float height, float y, float scale) {
            float k = Math.max(0.3F, 1.0F - height * 0.5F);
            float r = 0.62F * k * scale;
            int a = (int) (150.0F * k);
            Fx.quad(vc, pose, x, y, z, r, 0.0F, 0.0F, 0.0F, 0.0F, r, a << 24);
        }

        private static void shade(PoseStack.Pose pose, List<Placed> placed) {
            Set<Long> full = new HashSet<>();

            for (Placed b : placed) {
                if (b.solid()) {
                    full.add(Studio.key(b.x(), b.y(), b.z()));
                }
            }

            BufferBuilder buf = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float e = 0.003F;

            for (Placed b : placed) {
                if (b.solid()) {
                    int x = b.x();
                    int y = b.y();
                    int z = b.z();
                    float low = Math.min(0.6F, (float) (-1 - y) * 0.13F);
                    float x0 = (float) x;
                    float x1 = (float) (x + 1);
                    float y0 = (float) y;
                    float y1 = (float) (y + 1);
                    float z0 = (float) z;
                    float z1 = (float) (z + 1);
                    if (!full.contains(Studio.key(x, y, z - 1))) {
                        side(buf, pose, x0, y0, z0 - e, x1, y1, z0 - e, false, 0.2F + low);
                    }

                    if (!full.contains(Studio.key(x, y, z + 1))) {
                        side(buf, pose, x0, y0, z1 + e, x1, y1, z1 + e, false, 0.2F + low);
                    }

                    if (!full.contains(Studio.key(x - 1, y, z))) {
                        side(buf, pose, x0 - e, y0, z0, x0 - e, y1, z1, true, 0.34F + low);
                    }

                    if (!full.contains(Studio.key(x + 1, y, z))) {
                        side(buf, pose, x1 + e, y0, z0, x1 + e, y1, z1, true, 0.34F + low);
                    }

                    if (!full.contains(Studio.key(x, y - 1, z))) {
                        int c = Math.round(255.0F * Math.min(0.9F, 0.55F + low)) << 24;
                        buf.addVertex(pose, x0, y0 - e, z0).setColor(c);
                        buf.addVertex(pose, x1, y0 - e, z0).setColor(c);
                        buf.addVertex(pose, x1, y0 - e, z1).setColor(c);
                        buf.addVertex(pose, x0, y0 - e, z1).setColor(c);
                    }
                }
            }

            ImpactDraw.draw(ImpactDraw.type(Mode.QUADS, true, false, false), buf);
        }

        private static void side(
                BufferBuilder buf,
                PoseStack.Pose pose,
                float x0,
                float y0,
                float z0,
                float x1,
                float y1,
                float z1,
                boolean alongZ,
                float a) {
            int top = Math.round(255.0F * Math.min(0.9F, a)) << 24;
            int foot = Math.round(255.0F * Math.min(0.9F, a + 0.12F)) << 24;
            if (alongZ) {
                buf.addVertex(pose, x0, y0, z0).setColor(foot);
                buf.addVertex(pose, x0, y0, z1).setColor(foot);
                buf.addVertex(pose, x0, y1, z1).setColor(top);
                buf.addVertex(pose, x0, y1, z0).setColor(top);
            } else {
                buf.addVertex(pose, x0, y0, z0).setColor(foot);
                buf.addVertex(pose, x1, y0, z0).setColor(foot);
                buf.addVertex(pose, x1, y1, z0).setColor(top);
                buf.addVertex(pose, x0, y1, z0).setColor(top);
            }
        }

        private void motes(PoseStack ps, Vector3f left, Vector3f up) {
            VertexConsumer vc = this.bufferSource.getBuffer(Fx.type(Fx.Tex.GLOW, true));
            PoseStack.Pose pose = ps.last();
            int accent = Fx.lerp(Theme.accent() | 0xFF000000, -1, 0.35F);
            Random r = new Random(11L);

            for (int i = 0; i < 36; ++i) {
                float bx = -3.1F + r.nextFloat() * 6.7F;
                float bz = -2.3F + r.nextFloat() * 4.6F;
                float rise = 0.07F + r.nextFloat() * 0.12F;
                float phase = r.nextFloat();
                float size = 0.025F + r.nextFloat() * 0.035F;
                boolean white = r.nextInt(3) == 0;
                float h = (Studio.time * rise / 3.4F + phase) % 1.0F;
                float x = bx + Mth.sin((double) (Studio.time * 0.5F + (float) i)) * 0.18F;
                float z = bz + Mth.cos((double) (Studio.time * 0.4F + (float) i * 1.3F)) * 0.18F;
                float y = -0.3F + h * 3.4F;
                float a = Mth.sin((double) (h * (float) Math.PI))
                        * (0.55F + 0.45F * Mth.sin((double) (Studio.time * 2.3F + (float) i * 2.1F)));
                if (!(a <= 0.02F)) {
                    int c = Fx.pm(Math.round(150.0F * a) << 24 | (white ? -1 : accent) & 0xFFFFFF);
                    Fx.quad(
                            vc,
                            pose,
                            x,
                            y,
                            z,
                            left.x * size,
                            left.y * size,
                            left.z * size,
                            up.x * size,
                            up.y * size,
                            up.z * size,
                            c);
                }
            }
        }

        private void swingTrail(PoseStack ps, float t, double ax, double ay, float attack) {
            CombatFx.SwingTrail m = Modules.SWING;
            float life = m.life.f();
            if (attack > 0.001F && (Studio.lastAttack <= 0.001F || attack < Studio.lastAttack - 0.2F)) {
                Studio.swing.addLast(new SwingPt((Vec3) null, (Vec3) null, t, 0.0F));
                Studio.lastPhi = -99.0F;
            }

            Studio.lastAttack = attack;
            if (attack > 0.001F && attack < 0.999F) {
                float e = 1.0F - (float) Math.pow((double) (1.0F - attack), 3.0);
                float phi0 = 0.7F;
                float phi1 = 3.75F;
                float phi = phi0 + (phi1 - phi0) * e;
                float from = Studio.lastPhi < -50.0F ? phi : Studio.lastPhi;
                int steps = Math.max(1, (int) Math.ceil((double) (Math.abs(phi - from) / 0.1F)));
                Vec3 f = new Vec3(1.0, 0.0, 0.0);
                Vec3 e1 = new Vec3(0.0, 0.0, 1.0);
                Vec3 e2 = (new Vec3(0.0, 0.8, 0.0)).add(f.scale(0.6)).normalize();
                Vec3 shoulder = (new Vec3(ax, ay + 1.116, 0.0)).add(f.scale(0.55));
                float lo = 1.55F * m.size.f();
                float li = 0.6F * m.size.f();

                for (int i = 1; i <= steps; ++i) {
                    float ph = from + (phi - from) * (float) i / (float) steps;
                    Vec3 dir = e1.scale(Math.cos((double) ph)).add(e2.scale(Math.sin((double) ph)));
                    Studio.swing.addLast(new SwingPt(
                            shoulder.add(dir.scale((double) li)),
                            shoulder.add(dir.scale((double) lo)),
                            t,
                            (ph - phi0) / (phi1 - phi0)));
                }

                Studio.lastPhi = phi;
            } else {
                Studio.lastPhi = -99.0F;
            }

            while (!Studio.swing.isEmpty()
                    && (t - ((SwingPt) Studio.swing.peekFirst()).t() > life
                            || ((SwingPt) Studio.swing.peekFirst()).t() > t + 0.01F)) {
                Studio.swing.pollFirst();
            }

            if (Studio.swing.size() >= 2) {
                PoseStack.Pose pose = ps.last();
                VertexConsumer vc = this.bufferSource.getBuffer(Fx.type(Fx.Tex.SWING, false));
                SwingPt prev = null;

                for (SwingPt s : Studio.swing) {
                    if (s.in() == null) {
                        prev = null;
                    } else {
                        if (prev != null) {
                            float a0 = 1.0F - Math.min(1.0F, (t - prev.t()) / life);
                            float a1 = 1.0F - Math.min(1.0F, (t - s.t()) / life);
                            int c0 = straight(m.colourAt(prev.frac()), a0 * 0.95F);
                            int c1 = straight(m.colourAt(s.frac()), a1 * 0.95F);
                            quad4(vc, pose, prev.out(), prev.in(), s.in(), s.out(), a0, a1, c0, c1);
                            quad4(vc, pose, s.out(), s.in(), prev.in(), prev.out(), a1, a0, c1, c0);
                        }

                        prev = s;
                    }
                }
            }
        }

        private static int straight(int c, float a) {
            return (int) (Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | c & 0xFFFFFF;
        }

        private static void quad4(
                VertexConsumer vc,
                PoseStack.Pose pose,
                Vec3 a0,
                Vec3 b0,
                Vec3 b1,
                Vec3 a1,
                float u0,
                float u1,
                int c0,
                int c1) {
            Fx.v(vc, pose, (float) a0.x, (float) a0.y, (float) a0.z, u0, 0.0F, c0);
            Fx.v(vc, pose, (float) b0.x, (float) b0.y, (float) b0.z, u0, 1.0F, c0);
            Fx.v(vc, pose, (float) b1.x, (float) b1.y, (float) b1.z, u1, 1.0F, c1);
            Fx.v(vc, pose, (float) a1.x, (float) a1.y, (float) a1.z, u1, 0.0F, c1);
        }
    }
}
