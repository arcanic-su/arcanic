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
package net.arcanic.visuals.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.arcanic.visuals.core.Keys;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.gui.FrontEndLoading;
import net.arcanic.visuals.helper.HolyEvents;
import net.arcanic.visuals.media.Music;
import net.arcanic.visuals.render.HudShape;
import net.arcanic.visuals.render.IslandShape;
import net.arcanic.visuals.render.MsdfText;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class DynamicIsland extends HudModule {
    private final Setting.Bool nick = this.add(new Setting.Bool("show_nick", "Ник", true));
    private final Setting.Bool server = this.add(new Setting.Bool("server2", "Сервер", false));
    private final Setting.Bool fps = this.add(new Setting.Bool("fps", "FPS", true));
    private final Setting.Bool ping = this.add(new Setting.Bool("ping", "Пинг", true));
    private final Setting.Bool time = this.add(new Setting.Bool("time", "Время", true));
    public final Setting.Bool music = this.add(new Setting.Bool("music", "Музыка (Яндекс и другие)", true));
    private final Setting.Bool binds = this.add(new Setting.Bool("binds", "Бинды и переключения", true));
    private final Setting.Bool totems = this.add(new Setting.Bool("totems", "Тотемы", true));
    private final Setting.Bool alerts =
            this.add(new Setting.Bool("alerts", "Предупреждения (броня, ХП, тотемы)", true));
    private final Setting.Bool intro = this.add(new Setting.Bool("intro", "Анимация входа", true));
    private final Setting.Bool glow = this.add(new Setting.Bool("glow", "Свечение и блики", true));
    private static DynamicIsland instance;
    private static final int WHITE = -1;
    private static final int SOFT = 0xFFC9C9D1;
    private static final int GREY = 0xFF8E8E98;
    private static final int FAINT = 1509949439;
    private static final int GREEN = 0xFF30D158;
    private static final int RED = -47814;
    private static final int YELLOW = -10742;
    private static final int GOLD = -15043;
    private static final int ORANGE = -24822;
    private static final int MODULE = 0;
    private static final int NOTICE = 1;
    private static final int TOTEM = 2;
    private static final int TOTEM_OTHER = 3;
    private static final int ITEM = 4;
    private final IslandIntro introClock = new IslandIntro();
    private final Spring mw = new Spring();
    private final Spring mh = new Spring();
    private final Spring mr = new Spring();
    private final Spring lOut = new Spring();
    private final Spring rOut = new Spring();
    private final Spring frame = new Spring();
    private final Spring hoverK = new Spring();
    private final Spring pop = new Spring();
    private final IslandShape.Spec spec = new IslandShape.Spec();
    private final float[] levels = new float[6];
    private final int[] hit = new int[4];
    private final int[][] buttons = new int[3][4];
    private final float[] buttonHover = new float[3];
    private View view;
    private View leaving;
    private Event event;
    private Event shown;
    private Event leavingEvent;
    private long viewSince;
    private long lastNanos;
    private long pulseAt;
    private long joinedAt;
    private long fpsAt;
    private long nowPlayingUntil;
    private int pulseColour;
    private int sampledFps;
    private int placedX;
    private int placedY;
    private boolean expanded;
    private boolean dropped;
    private Music.Track media;
    private String announced;
    private String heardTitle;
    private long pausedAt;
    private long playingSince;
    private double beatClock;
    private Supplier<PlayerSkin> ownSkin;
    private ItemStack totemStack;
    private float cx;
    private float mW;
    private float mH;
    private float lCx;
    private float lW;
    private float lH;
    private float lK;
    private float rCx;
    private float rW;
    private float rH;
    private float rK;
    private static float dropHearts;
    private static boolean dropLethal;
    private static boolean dropBeat;
    private static boolean dropCrystal;
    private static ItemStack crystalStack;
    private static long dropAt;
    private static long crystalAt;
    private static final Identifier H_FRAME = Identifier.withDefaultNamespace("hud/heart/container_hardcore");
    private static final Identifier H_FULL = Identifier.withDefaultNamespace("hud/heart/hardcore_full");
    private static final Identifier H_HALF = Identifier.withDefaultNamespace("hud/heart/hardcore_half");
    private static final Identifier W_FULL = Identifier.withDefaultNamespace("hud/heart/withered_hardcore_full");
    private static final Identifier W_HALF = Identifier.withDefaultNamespace("hud/heart/withered_hardcore_half");
    private static final long HOLY_SHOWN = 5000L;
    private final Set<String> holySeen;
    private String holyKey;
    private long holyUntil;

    public DynamicIsland() {
        super(
                "watermark",
                "Вотермарка",
                "Динамический остров Arcanic: ник, сервер, FPS, пинг и время. Живо отвечает на бинды, тотемы и музыку; в чате раскрывается нажатием.",
                true,
                0.008,
                0.012);
        this.view = DynamicIsland.View.IDLE;
        this.leaving = DynamicIsland.View.IDLE;
        this.pulseAt = -2305843009213693952L;
        this.media = Music.Track.EMPTY;
        this.announced = "";
        this.holySeen = new HashSet<>();
        instance = this;
    }

    public void joined() {
        if (this.intro.on()) {
            this.introClock.queue();
        } else {
            this.introClock.reset();
        }

        this.dropped = false;
        this.expanded = false;
        this.event = null;
        this.joinedAt = System.currentTimeMillis();
    }

    public void disconnected() {
        this.introClock.reset();
        this.expanded = false;
        this.event = null;
        this.joinedAt = 0L;
        this.holySeen.clear();
        this.holyKey = null;
    }

    public static void module(Module m) {
        DynamicIsland d = instance;
        if (d != null && d.enabled() && d.binds.on() && m != null) {
            boolean on = m.enabled();
            d.push(
                    "module:" + m.id,
                    0,
                    m.title(),
                    Lang.tr(on ? "Включено" : "Выключено"),
                    m.bind < 0 ? "" : Keys.label(m.bind),
                    on,
                    on ? Theme.accent() | 0xFF000000 : 0xFF8E8E98,
                    (UUID) null,
                    1800L);
        }
    }

    public static void notice(String text, boolean positive) {
        DynamicIsland d = instance;
        if (d != null && d.enabled() && d.binds.on() && text != null && !text.isBlank()) {
            String s = Lang.tr(text);
            int i = s.indexOf(": ");
            String head = i < 0 ? s : s.substring(0, i);
            String rest = i < 0 ? "" : s.substring(i + 2);
            d.push(
                    "notice:" + head,
                    1,
                    head,
                    rest,
                    "",
                    positive,
                    positive ? Theme.accent2() | 0xFF000000 : -47814,
                    (UUID) null,
                    2200L);
        }
    }

    public static boolean alert(String key, ItemStack stack, String title, String sub, String chip, int colour) {
        DynamicIsland d = instance;
        if (d != null && d.enabled() && d.alerts.on() && stack != null) {
            d.push(
                    "alert:" + key,
                    4,
                    title,
                    sub == null ? "" : sub,
                    chip == null ? "" : chip,
                    false,
                    colour,
                    (UUID) null,
                    2600L);
            d.event.stack = stack.copyWithCount(1);
            return true;
        } else {
            return false;
        }
    }

    public static void crystalHearts(float hp, boolean lethal) {
        long now = System.currentTimeMillis();
        dropHearts = hp / 2.0F;
        dropLethal = lethal;
        dropBeat = false;
        dropCrystal = true;
        crystalAt = now;
        dropAt = now;
    }

    public static void lowHearts(float hp) {
        long now = System.currentTimeMillis();
        if (now - crystalAt >= 300L) {
            dropHearts = hp / 2.0F;
            dropLethal = false;
            dropBeat = true;
            dropCrystal = false;
            dropAt = now;
        }
    }

    public static void totem(LivingEntity e) {
        DynamicIsland d = instance;
        if (d != null && d.enabled() && d.totems.on() && e != null) {
            Minecraft mc = Minecraft.getInstance();
            if (e == mc.player) {
                d.push("totem:self", 2, Lang.tr("Тотем спас тебя"), "", "", true, -15043, (UUID) null, 2600L);
            } else if (e instanceof Player) {
                Player p = (Player) e;
                d.push(
                        "totem:" + p.getUUID(),
                        3,
                        p.getName().getString(),
                        Lang.tr("потерял тотем"),
                        "",
                        false,
                        -24822,
                        p.getUUID(),
                        2400L);
            }
        }
    }

    private void push(
            String key, int kind, String title, String sub, String chip, boolean on, int colour, UUID who, long life) {
        long now = System.currentTimeMillis();
        boolean again = this.event != null && this.event.key.equals(key) && now - this.event.born < this.event.life;
        Event e = again ? this.event : new Event();
        e.from = again ? e.knob.v : (on ? 0.0F : 1.0F);
        if (!again) {
            e.knob.snap(e.from);
        }

        e.key = key;
        e.kind = kind;
        e.title = title;
        e.sub = sub;
        e.chip = chip;
        e.on = on;
        e.colour = colour;
        e.who = who;
        e.born = now;
        e.life = life;
        this.event = e;
    }

    public int x(int screenW) {
        return (screenW - this.w) / 2;
    }

    private static double mouseX(Minecraft mc) {
        return mc.mouseHandler.xpos()
                * (double) Px.width()
                / (double) Math.max(1, mc.getWindow().getScreenWidth());
    }

    private static double mouseY(Minecraft mc) {
        return mc.mouseHandler.ypos()
                * (double) Px.height()
                / (double) Math.max(1, mc.getWindow().getScreenHeight());
    }

    private static boolean inside(int[] r, double x, double y) {
        return x >= (double) r[0] && x < (double) (r[0] + r[2]) && y >= (double) r[1] && y < (double) (r[1] + r[3]);
    }

    private static float smooth(float a, float b, float t) {
        float x = Math.max(0.0F, Math.min(1.0F, (t - a) / (b - a)));
        return x * x * (3.0F - 2.0F * x);
    }

    public void render(GuiGraphics g, boolean editing) {
        Minecraft mc = Minecraft.getInstance();
        long nanos = System.nanoTime();
        long now = System.currentTimeMillis();
        float dt = this.lastNanos == 0L ? 0.016666668F : Math.min(0.05F, (float) (nanos - this.lastNanos) / 1.0E9F);
        this.lastNanos = nanos;
        if (now - this.fpsAt >= 500L) {
            this.fpsAt = now;
            this.sampledFps = mc.getFps();
        }

        int sw = Px.width();
        int sh = Px.height();
        if (this.music.on() && !editing) {
            Music.request();
        }

        Music.Track observed = Music.track();
        if (observed.present()) {
            this.media = observed;
        }

        boolean heard = this.music.on() && !editing && this.heard(observed, now);
        if (heard && this.media.playing() && !this.announced.equals(this.media.key())) {
            this.announced = this.media.key();
            this.nowPlayingUntil = now + 3600L;
            this.pulseAt = nanos;
            this.pulseColour = musicColour();
            this.pop.snap(1.0F);
        }

        if (!heard) {
            this.nowPlayingUntil = 0L;
        }

        float beat = this.beat(dt);
        boolean cursor = mc.screen instanceof ChatScreen;
        if (!cursor || editing) {
            this.expanded = false;
        }

        if (this.event != null && now - this.event.born > this.event.life) {
            this.event = null;
        }

        boolean visible = mc.level == null
                ? FrontEndLoading.splashReleased()
                : mc.player != null && mc.screen == null && mc.getOverlay() == null;
        float age = editing
                ? 10.0F
                : this.introClock.seconds(
                        nanos, visible && !FrontEndLoading.holdsIsland(), Theme.animations && this.intro.on());
        if (!editing && this.introClock.waiting()) {
            this.h = this.S(28.0);
        } else {
            if (age < 0.3F && !this.dropped) {
                this.dropped = true;
                this.mw.snap(0.0F);
                this.mh.snap(0.0F);
                this.mr.snap(0.0F);
                this.lOut.snap(0.0F);
                this.rOut.snap(0.0F);
                this.pulseAt = nanos;
                this.pulseColour = Theme.accent2() | 0xFF000000;
            }

            View want;
            if (editing) {
                want = DynamicIsland.View.IDLE;
            } else if (age < 0.3F) {
                want = DynamicIsland.View.DROP;
            } else if (this.expanded) {
                want = heard ? DynamicIsland.View.PLAYER : DynamicIsland.View.INFO;
            } else if (this.event != null) {
                want = DynamicIsland.View.EVENT;
            } else if (age < 3.3F) {
                want = DynamicIsland.View.WELCOME;
            } else if (now - dropAt < 300L && dropHearts > 0.05F) {
                want = DynamicIsland.View.HEARTS;
            } else if (heard && now < this.nowPlayingUntil) {
                want = DynamicIsland.View.NOW_PLAYING;
            } else if (this.holyNow(now) != null) {
                want = DynamicIsland.View.HOLY;
            } else if (heard) {
                want = DynamicIsland.View.MUSIC;
            } else {
                want = DynamicIsland.View.IDLE;
            }

            Event wantEvent = want == DynamicIsland.View.EVENT ? this.event : null;
            if (want != this.view || wantEvent != this.shown) {
                this.leaving = this.view;
                this.leavingEvent = this.shown;
                this.view = want;
                this.shown = wantEvent;
                this.viewSince = now;
            }

            if (this.event != null) {
                this.event.knob.step(
                        now - this.event.born > 110L ? (float) (this.event.on ? 1 : 0) : this.event.from,
                        dt,
                        300.0F,
                        35.0F);
            }

            this.pop.step(1.0F, dt, 240.0F, 14.0F);
            String name = Overlays.Streamer.name(mc.getUser().getName());
            String clock = clock();
            boolean online = mc.getCurrentServer() != null;
            boolean pingOn = this.ping.on() && online;
            float idleW = this.idleWidth(mc, name);
            float[] target = this.size(want, mc, name, idleW);
            boolean over = cursor && inside(this.hit, mouseX(mc), mouseY(mc));
            float hv = this.hoverK.step(over && !this.expanded ? 1.0F : 0.0F, dt, 260.0F, 26.0F);
            this.mW = Math.max(0.0F, this.mw.step(target[0] + (float) this.S(4.0) * hv, dt, 240.0F, 31.0F));
            this.mH = Math.max(0.0F, this.mh.step(target[1] + (float) this.S(1.0) * hv, dt, 240.0F, 31.0F));
            float mR = Math.max(0.0F, this.mr.step(target[2], dt, 240.0F, 31.0F));
            boolean sats = want == DynamicIsland.View.IDLE || want == DynamicIsland.View.MUSIC;
            boolean late = now - this.viewSince > 150L
                    || this.leaving == DynamicIsland.View.IDLE
                    || this.leaving == DynamicIsland.View.MUSIC;
            float lo = this.lOut.step(sats && late && this.time.on() ? 1.0F : 0.0F, dt, 200.0F, 28.0F);
            float ro = this.rOut.step(!sats || !late || !this.fps.on() && !pingOn ? 0.0F : 1.0F, dt, 200.0F, 28.0F);
            boolean both = this.time.on() && (this.fps.on() || pingOn);
            float lW0 = (float) this.leftWidth(clock);
            float rW0 = (float) this.rightWidth(pingOn);
            float satH = (float) this.S(22.0);
            float gap = (float) this.S(6.0);
            if (both) {
                lW0 = rW0 = Math.max(lW0, rW0);
            }

            float side = Math.max(this.time.on() ? lW0 : 0.0F, !this.fps.on() && !pingOn ? 0.0F : rW0);
            float frameW = idleW + 2.0F * (gap + side);
            int oldW = this.w;
            this.placedX = Math.max(0, Math.min(sw - oldW, this.x(sw)));
            this.placedY = Math.max(0, Math.min(sh - this.h, this.y(sh)));
            this.cx = (float) sw / 2.0F - (float) this.placedX;
            this.w = Math.max(1, Math.round(this.frame.step(frameW, dt, 300.0F, 35.0F)));
            this.h = this.S(28.0);
            float mainL = this.cx - this.mW / 2.0F;
            float mainR = this.cx + this.mW / 2.0F;
            float cy = this.mH / 2.0F;
            this.lK = lo;
            float lk = Math.max(0.0F, Math.min(1.0F, lo));
            this.lW = lW0 * (0.3F + 0.7F * lk);
            this.lH = satH * (0.45F + 0.55F * lk);
            this.lCx = mainL + this.lW / 2.0F + (mainL - gap - lW0 / 2.0F - (mainL + this.lW / 2.0F)) * lo;
            this.rK = ro;
            float rk = Math.max(0.0F, Math.min(1.0F, ro));
            this.rW = rW0 * (0.3F + 0.7F * rk);
            this.rH = satH * (0.45F + 0.55F * rk);
            this.rCx = mainR - this.rW / 2.0F + (mainR + gap + rW0 / 2.0F - (mainR - this.rW / 2.0F)) * ro;
            this.hit[0] = this.placedX + Math.round(mainL);
            this.hit[1] = this.placedY;
            this.hit[2] = Math.round(this.mW);
            this.hit[3] = Math.round(this.mH);
            float base = Px.fade;
            boolean lShown = lk > 0.02F;
            boolean rShown = rk > 0.02F;
            float margin = (float) this.S(30.0);
            float minX = Math.min(mainL, lShown ? this.lCx - this.lW / 2.0F : mainL) - margin;
            float maxX = Math.max(mainR, rShown ? this.rCx + this.rW / 2.0F : mainR) + margin;
            int bx = (int) Math.floor((double) minX);
            int by = -Math.round(margin);
            int bw = (int) Math.ceil((double) maxX) - bx;
            int bh = Math.round(Math.max(this.mH, satH) + margin * 2.0F);
            IslandShape.Spec s = this.spec;
            s.mx = this.cx - (float) bx;
            s.my = cy - (float) by;
            s.mw = this.mW / 2.0F;
            s.mh = this.mH / 2.0F;
            s.mr = mR;
            s.lw = lShown ? this.lW / 2.0F : 0.0F;
            s.lx = this.lCx - (float) bx;
            s.ly = cy - (float) by;
            s.lh = this.lH / 2.0F;
            s.lr = this.lH / 2.0F;
            s.rw = rShown ? this.rW / 2.0F : 0.0F;
            s.rx = this.rCx - (float) bx;
            s.ry = cy - (float) by;
            s.rh = this.rH / 2.0F;
            s.rr = this.rH / 2.0F;
            float bump = Math.max(4.0F * lk * (1.0F - lk), 4.0F * rk * (1.0F - rk));
            s.melt = (float) this.S(10.0) + (float) this.S(14.0) * bump;
            s.scale = this.k;
            s.fill = 0xF2000000;
            s.rim = 0x1AFFFFFF;
            s.shadow = 0.3F;
            s.shadowBlur = (float) this.S(9.0);
            s.shadowDrop = (float) this.S(3.0);
            s.sheen = 0.03F;
            boolean lit = this.glow.on() && Theme.animations;
            float since = (float) (nanos - this.pulseAt) / 1.0E9F;
            float pulse = since >= 0.0F && since < 3.0F ? (float) Math.exp((double) (-since * 2.3F)) : 0.0F;
            boolean playingView = this.view == DynamicIsland.View.MUSIC
                    || this.view == DynamicIsland.View.NOW_PLAYING
                    || this.view == DynamicIsland.View.PLAYER;
            float ambient = playingView && this.media.playing() ? 0.04F + 0.03F * beat : 0.0F;
            s.glow = pulse * 0.35F >= ambient ? this.pulseColour | 0xFF000000 : musicColour();
            s.glowStrength = lit ? Math.max(pulse * 0.35F, ambient) : 0.0F;
            s.glowReach = (float) this.S(13.0);
            s.sweep = this.pulseColour | 0xFF000000;
            s.sweepStrength = 0.0F;
            s.sweepHead = 0.25F + since / 0.9F;
            s.sweepLength = 0.16F;
            s.tint = this.pulseColour | 0xFF000000;
            s.tintStrength = 0.0F;
            s.tintReach = 0.75F;
            if (this.background.on() && !IslandShape.draw(g, bx, by, bw, bh, s, base)) {
                this.plain(g, mR);
            }

            g.nextStratum();
            long t = now - this.viewSince;
            float in = this.leaving == this.view && this.leavingEvent == this.shown
                    ? 1.0F
                    : smooth(60.0F, 240.0F, (float) t);
            float out = 1.0F - smooth(0.0F, 140.0F, (float) t);
            if (out > 0.01F && (this.leaving != this.view || this.leavingEvent != this.shown)) {
                this.content(g, mc, this.leaving, this.leavingEvent, base * out, 1.0F, name);
            }

            this.content(g, mc, this.view, this.shown, base * in, 1.0F, name);
            this.satellites(g, base, clock, pingOn);
            Px.fade = base;
        }
    }

    private void plain(GuiGraphics g, float radius) {
        Px.round(
                g,
                Math.round(this.cx - this.mW / 2.0F),
                0,
                Math.round(this.mW),
                Math.round(this.mH),
                Math.round(radius),
                0xF0000000);
        if (this.lK > 0.02F) {
            Px.round(
                    g,
                    Math.round(this.lCx - this.lW / 2.0F),
                    Math.round(this.mH / 2.0F - this.lH / 2.0F),
                    Math.round(this.lW),
                    Math.round(this.lH),
                    Math.round(this.lH / 2.0F),
                    0xF0000000);
        }

        if (this.rK > 0.02F) {
            Px.round(
                    g,
                    Math.round(this.rCx - this.rW / 2.0F),
                    Math.round(this.mH / 2.0F - this.rH / 2.0F),
                    Math.round(this.rW),
                    Math.round(this.rH),
                    Math.round(this.rH / 2.0F),
                    0xF0000000);
        }
    }

    private int crystalIcon() {
        return this.S(17.0);
    }

    private int heartPx() {
        return 9 * Math.max(1, Math.round((float) this.S(16.0) / 9.0F));
    }

    private int heartSlots() {
        return Math.max(1, Math.min(10, (int) Math.ceil((double) (dropHearts - 0.05F))));
    }

    private float heartsWidth() {
        int hs = this.heartPx();
        float row = (float) hs * 8.0F / 9.0F * (float) (this.heartSlots() - 1) + (float) hs;
        return (float) (this.S(14.0) + (dropCrystal ? this.crystalIcon() + this.S(8.0) : 0))
                + row
                + (float) this.S(14.0);
    }

    private void heartsView(GuiGraphics g) {
        int hs = this.heartPx();
        int slots = this.heartSlots();
        int cy = Math.round(this.mH / 2.0F);
        float step = (float) hs * 8.0F / 9.0F;
        float rowW = step * (float) (slots - 1) + (float) hs;
        int icon = dropCrystal ? this.crystalIcon() : 0;
        int gap = dropCrystal ? this.S(8.0) : 0;
        int x0 = Math.round(this.cx - (rowW + (float) icon + (float) gap) / 2.0F);
        if (dropCrystal) {
            if (crystalStack == null) {
                crystalStack = new ItemStack(Items.END_CRYSTAL);
            }

            HudElements.item(g, crystalStack, x0, cy - icon / 2, icon);
            g.nextStratum();
            x0 += icon + gap;
        }

        int c = Px.am(-1, Px.fade);
        double time = (double) System.nanoTime() / (double) 1.0E9F;
        float speed = !dropLethal && !dropBeat ? 1.1F : 1.6F;
        RenderPipeline pipe = RenderPipelines.GUI_TEXTURED;

        for (int i = 0; i < slots; i++) {
            boolean half = dropHearts - (float) i < 0.95F;
            float wave = (float) Math.max(0.0, Math.sin(time * Math.PI * 2.0 * (double) speed - (double) i * 0.45));
            float k = 1.0F + 0.14F * wave * wave;
            int hx = x0 + Math.round(step * (float) i) + hs / 2;
            int hy = cy - Math.round((float) this.S(1.5) * wave);
            g.pose().pushMatrix();
            g.pose().translate((float) hx, (float) hy);
            g.pose().scale(k, k);
            g.blitSprite(pipe, H_FRAME, -hs / 2, -hs / 2, hs, hs, c);
            Identifier fill = dropLethal ? (half ? W_HALF : W_FULL) : (half ? H_HALF : H_FULL);
            g.blitSprite(pipe, fill, -hs / 2, -hs / 2, hs, hs, c);
            g.pose().popMatrix();
        }
    }

    private static HolyEvents.Event holyHere() {
        HolyEvents ev = Modules.HOLY_EVENTS;
        if (ev != null && ev.enabled() && HolyEvents.onHolyWorld()) {
            String here = ev.here();
            if (here == null) {
                return null;
            } else {
                for (HolyEvents.Event e : ev.sorted()) {
                    if (e.server().equals(here)) {
                        return e;
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }

    private HolyEvents.Event holyNow(long now) {
        HolyEvents.Event e = holyHere();
        if (e == null) {
            return null;
        } else {
            String key = e.server() + "|" + e.id() + "|" + e.type();
            if (this.holySeen.add(key)) {
                this.holyKey = key;
                this.holyUntil = now + 5000L;
            }

            return key.equals(this.holyKey) && now < this.holyUntil ? e : null;
        }
    }

    private float holyWidth() {
        HolyEvents.Event e = holyHere();
        if (e == null) {
            return (float) this.S(190.0);
        } else {
            int w = this.S(7.0)
                    + this.S(22.0)
                    + this.S(9.0)
                    + Px.textWidth(
                            Px.ellipsize(e.name(), Px.Weight.SEMIBOLD, this.S(13.0), this.S(200.0)),
                            Px.Weight.SEMIBOLD,
                            this.S(13.0));
            return (float) (w + this.S(14.0));
        }
    }

    private void holyView(GuiGraphics g) {
        HolyEvents.Event e = holyHere();
        if (e != null) {
            int cy = Math.round(this.mH / 2.0F);
            int x = Math.round(this.cx - this.mW / 2.0F) + this.S(7.0);
            int hs = this.S(22.0);
            Identifier head = HolyEvents.head(e.type());
            if (head != null) {
                HolyEvents.picture(g, head, 38, 38, x, cy - hs / 2, hs, hs);
            } else {
                this.orb(g, x, cy, hs);
            }

            x += hs + this.S(9.0);
            String name = Px.ellipsize(e.name(), Px.Weight.SEMIBOLD, this.S(13.0), this.S(200.0));
            Px.text(g, name, x, cy - this.S(13.0) / 2, Px.Weight.SEMIBOLD, this.S(13.0), -1);
        }
    }

    private float[] size(View v, Minecraft mc, String name, float idleW) {
        float h0 = (float) this.S(28.0);
        float[] fs2;
        switch (v) {
            case DROP:
                fs2 = new float[] {(float) this.S(16.0), (float) this.S(16.0), (float) this.S(8.0)};
                break;
            case WELCOME:
                fs2 = new float[] {this.welcomeWidth(mc, name), (float) this.S(66.0), (float) this.S(30.0)};
                break;
            case IDLE:
            default:
                fs2 = new float[] {idleW, h0, h0 / 2.0F};
                break;
            case EVENT:
                fs2 = new float[] {
                    this.event == null ? idleW : this.eventWidth(this.event), (float) this.S(40.0), (float) this.S(20.0)
                };
                break;
            case NOW_PLAYING:
                fs2 = new float[] {(float) this.S(384.0), (float) this.S(80.0), (float) this.S(34.0)};
                break;
            case MUSIC:
                fs2 = new float[] {this.musicWidth(), h0, h0 / 2.0F};
                break;
            case PLAYER:
                fs2 = new float[] {(float) this.S(388.0), (float) this.S(172.0), (float) this.S(40.0)};
                break;
            case INFO:
                fs2 = new float[] {(float) this.S(348.0), (float) this.S(130.0), (float) this.S(38.0)};
                break;
            case HEARTS:
                fs2 = new float[] {this.heartsWidth(), h0, h0 / 2.0F};
                break;
            case HOLY:
                fs2 = new float[] {this.holyWidth(), h0, h0 / 2.0F};
        }

        return fs2;
    }

    private void content(GuiGraphics g, Minecraft mc, View v, Event e, float a, float scale, String name) {
        if (!(a <= 0.01F) && v != DynamicIsland.View.DROP) {
            float old = Px.fade;
            Px.fade = a;
            int l = (int) Math.floor((double) (this.cx - this.mW / 2.0F));
            int r = (int) Math.ceil((double) (this.cx + this.mW / 2.0F));
            int b = (int) Math.ceil((double) this.mH);
            g.enableScissor(l, 0, r, Math.max(1, b));
            g.pose().pushMatrix();

            try {
                g.pose().translate(this.cx, this.mH / 2.0F);
                g.pose().scale(scale, scale);
                g.pose().translate(-this.cx, -this.mH / 2.0F);
                switch (v) {
                    case WELCOME:
                        this.welcome(g, mc, name);
                        break;
                    case IDLE:
                        this.idle(g, mc, name);
                        break;
                    case EVENT:
                        if (e != null) {
                            this.event(g, mc, e);
                        }
                        break;
                    case NOW_PLAYING:
                        this.nowPlaying(g);
                        break;
                    case MUSIC:
                        this.compactMusic(g);
                        break;
                    case PLAYER:
                        this.player(g, mc);
                        break;
                    case INFO:
                        this.info(g, mc, name);
                        break;
                    case HEARTS:
                        this.heartsView(g);
                        break;
                    case HOLY:
                        this.holyView(g);
                }
            } finally {
                g.pose().popMatrix();
                g.disableScissor();
                Px.fade = old;
            }
        }
    }

    // ---- the resting island: a dot of the accent and the name in the middle drop, the clock and the numbers in the
    // two side drops, all in the HUD's face and nothing else

    private static final String FACE = "sf_pro_display_medium";
    private static final String FACE_SEMI = "inter_semibold";

    /** Text in the HUD's face with the middle of its capitals on {@code cy}. */
    private void label(GuiGraphics g, String s, float x, int cy, int px, boolean semi, int colour) {
        if (!MsdfText.ready()) {
            Px.text(g, s, Math.round(x), cy - px / 2, semi ? Px.Weight.SEMIBOLD : Px.Weight.MEDIUM, px, colour);
            return;
        }
        String face = semi ? FACE_SEMI : FACE;
        MsdfText.drawTop(g, face, s, x, cy + px * 0.35F - MsdfText.baseline(face, px), px, Px.am(colour, Px.fade));
    }

    private static int labelWidth(String s, int px, boolean semi) {
        if (!MsdfText.ready()) return Px.textWidth(s, semi ? Px.Weight.SEMIBOLD : Px.Weight.MEDIUM, px);
        return Math.round(MsdfText.width(semi ? FACE_SEMI : FACE, s, px));
    }

    /** A width that does not breathe as the digits change. */
    private static int labelSteady(String s, int px, boolean semi) {
        return Math.max(labelWidth(s, px, semi), labelWidth(s.replaceAll("[0-9]", "0"), px, semi));
    }

    private float idleWidth(Minecraft mc, String name) {
        int w = this.S(12.0) + this.S(6.0) + this.S(7.0) + labelWidth("Arcanic", this.S(12.0), true);
        if (this.nick.on()) {
            w += this.dividerWidth() + labelWidth(name, this.S(11.5), false);
        }
        String srv = this.serverLine(mc);
        if (srv != null) {
            w += this.dividerWidth() + labelWidth(srv, this.S(11.5), false);
        }
        return (float) (w + this.S(13.0));
    }

    private String serverLine(Minecraft mc) {
        ServerData sd = mc.getCurrentServer();
        if (!this.server.on() || sd == null) return null;
        if (Overlays.Streamer.hidesServer()) return Lang.tr("Сервер скрыт");
        String ip = sd.ip;
        while (ip.length() > 4 && labelWidth(ip + "…", this.S(11.5), false) > this.S(130.0)) {
            ip = ip.substring(0, ip.length() - 1);
        }
        return ip.equals(sd.ip) ? ip : ip + "…";
    }

    private void idle(GuiGraphics g, Minecraft mc, String name) {
        int cy = Math.round(this.mH / 2.0F);
        int x = Math.round(this.cx - this.mW / 2.0F) + this.S(12.0);
        this.dot(g, x, cy, this.S(6.0));
        x += this.S(6.0) + this.S(7.0);
        int fs = this.S(12.0);
        this.label(g, "Arcanic", x, cy, fs, true, -1);
        x += labelWidth("Arcanic", fs, true);
        if (this.nick.on()) {
            x = this.divider(g, x, cy);
            this.label(g, name, x, cy, this.S(11.5), false, 0xFFB4B4BC);
            x += labelWidth(name, this.S(11.5), false);
        }
        String srv = this.serverLine(mc);
        if (srv != null) {
            x = this.divider(g, x, cy);
            this.label(g, srv, x, cy, this.S(11.5), false, 0xFF7E7E88);
        }
    }

    /** The accent dot, breathing a soft halo every few seconds. */
    private void dot(GuiGraphics g, int x, int cy, int d) {
        int r = Math.max(2, d / 2);
        int ox = x + r;
        int c = Theme.accent() | 0xFF000000;
        if (Theme.animations) {
            float t = (float) (System.currentTimeMillis() % 4200L) / 4200.0F;
            float k = (float) Math.sin(Math.PI * t);
            Px.glow(g, ox - r * 4, cy - r * 4, r * 8, r * 8, Px.am(c, 0.18F + 0.22F * k * k));
        }
        Px.circle(g, ox, cy, r, c);
    }

    private void orb(GuiGraphics g, int x, int cy, int d) {
        this.dot(g, x, cy, d);
    }

    private int dividerWidth() {
        return this.S(7.0) * 2 + Math.max(2, this.S(2.5));
    }

    private int divider(GuiGraphics g, int x, int cy) {
        int d = Math.max(2, this.S(2.5));
        x += this.S(7.0);
        Px.circle(g, x + d / 2, cy, Math.max(1, d / 2), 0x48FFFFFF);
        return x + d + this.S(7.0);
    }

    private int leftWidth(String clock) {
        return this.S(10.0) * 2 + labelSteady(clock, this.S(11.5), true);
    }

    private int rightWidth(boolean pingOn) {
        int w = this.S(10.0) * 2;
        if (pingOn) {
            w += this.S(5.0) + this.S(5.0) + this.valueWidth(Integer.toString(HudElements.ping()), "ms");
        }
        if (pingOn && this.fps.on()) {
            w += this.S(9.0);
        }
        if (this.fps.on()) {
            w += this.valueWidth(Integer.toString(this.sampledFps), "fps");
        }
        return w;
    }

    private void satellites(GuiGraphics g, float base, String clock, boolean pingOn) {
        int cy = Math.round(this.mH / 2.0F);
        if (this.lK > 0.02F && this.time.on()) {
            float a = smooth(0.55F, 1.0F, this.lK);
            if (a > 0.01F) {
                Px.fade = base * a;
                g.enableScissor(
                        Math.round(this.lCx - this.lW / 2.0F),
                        0,
                        Math.round(this.lCx + this.lW / 2.0F),
                        Math.max(1, Math.round(this.mH)));
                int fs = this.S(11.5);
                this.label(g, clock, this.lCx - labelSteady(clock, fs, true) / 2.0F, cy, fs, true, -1);
                g.disableScissor();
            }
        }

        if (this.rK > 0.02F && (this.fps.on() || pingOn)) {
            float a = smooth(0.55F, 1.0F, this.rK);
            if (a > 0.01F) {
                Px.fade = base * a;
                g.enableScissor(
                        Math.round(this.rCx - this.rW / 2.0F),
                        0,
                        Math.round(this.rCx + this.rW / 2.0F),
                        Math.max(1, Math.round(this.mH)));
                int x = Math.round(this.rCx - (float) (this.rightWidth(pingOn) - this.S(20.0)) / 2.0F);
                if (pingOn) {
                    int latency = HudElements.ping();
                    // the connection as one coloured dot: green, yellow under strain, red when it lags
                    int col = latency < 100 ? 0xFF30D158 : (latency < 200 ? 0xFFFFD60A : 0xFFFF453A);
                    int d = this.S(5.0);
                    Px.circle(g, x + d / 2, cy, Math.max(2, d / 2), col);
                    x += d + this.S(5.0);
                    x += this.value(g, Integer.toString(latency), "ms", x, cy);
                    if (this.fps.on()) {
                        x += this.S(9.0);
                    }
                }
                if (this.fps.on()) {
                    this.value(g, Integer.toString(this.sampledFps), "fps", x, cy);
                }
                g.disableScissor();
            }
        }
    }

    private int bars(GuiGraphics g, int x, int cy, int latency) {
        int bw = Math.max(2, this.S(2.5));
        int gap = Math.max(1, this.S(1.5));
        int col = latency < 100 ? 0xFF30D158 : (latency < 200 ? -10742 : -47814);
        int level = latency <= 0 ? 0 : (latency < 70 ? 4 : (latency < 140 ? 3 : (latency < 240 ? 2 : 1)));
        int bottom = cy + this.S(5.0);

        for (int i = 0; i < 4; i++) {
            int bh = this.S(4.0 + (double) i * 2.5);
            Px.round(g, x + i * (bw + gap), bottom - bh, bw, bh, bw / 2, i < level ? col : 956301311);
        }

        return this.barsWidth();
    }

    private int barsWidth() {
        return 4 * Math.max(2, this.S(2.5)) + 3 * Math.max(1, this.S(1.5));
    }

    private int valueWidth(String v, String unit) {
        return labelSteady(v, this.S(11.5), true) + this.S(2.5) + labelWidth(unit, this.S(9.5), false);
    }

    private int value(GuiGraphics g, String v, String unit, int x, int cy) {
        int fs = this.S(11.5);
        int us = this.S(9.5);
        int vw = labelSteady(v, fs, true);
        this.label(g, v, x, cy, fs, true, -1);
        this.label(g, unit, x + vw + this.S(2.5), cy + (fs - us) * 7 / 20, us, false, 0xFF7E7E88);
        return vw + this.S(2.5) + labelWidth(unit, us, false);
    }

    private static int steady(String s, Px.Weight wt, int fs) {
        return Math.max(Px.textWidth(s, wt, fs), Px.textWidth(s.replaceAll("[0-9]", "0"), wt, fs));
    }

    private static String clock() {
        LocalTime c = LocalTime.now();
        return String.format("%02d:%02d", c.getHour(), c.getMinute());
    }

    private String whereText(Minecraft mc) {
        ServerData sd = mc.getCurrentServer();
        if (sd != null && Overlays.Streamer.hidesServer()) {
            return Lang.tr("Сервер скрыт");
        } else {
            return sd != null
                    ? Px.ellipsize(sd.ip, Px.Weight.SEMIBOLD, this.S(13.0), this.S(170.0))
                    : Lang.tr(mc.level != null ? "Одиночный мир" : "Arcanic");
        }
    }

    private String pingLine(Minecraft mc) {
        return mc.getCurrentServer() != null ? HudElements.ping() + " " + Lang.tr("мс") : Lang.tr("локально");
    }

    private float welcomeWidth(Minecraft mc, String name) {
        int left = Math.max(
                Px.textWidth(name, Px.Weight.BOLD, this.S(17.0)),
                Px.textWidth(Lang.tr("Добро пожаловать"), Px.Weight.MEDIUM, this.S(11.0)));
        int right = Math.max(
                Px.textWidth(this.whereText(mc), Px.Weight.SEMIBOLD, this.S(13.0)),
                (mc.getCurrentServer() != null ? this.barsWidth() + this.S(5.0) : 0)
                        + Px.textWidth(this.pingLine(mc), Px.Weight.MEDIUM, this.S(11.0)));
        return (float) Math.max(
                this.S(320.0), this.S(13.0) + this.S(40.0) + this.S(12.0) + left + this.S(30.0) + right + this.S(16.0));
    }

    private void welcome(GuiGraphics g, Minecraft mc, String name) {
        int cy = Math.round(this.mH / 2.0F);
        int left = Math.round(this.cx - this.mW / 2.0F);
        int right = Math.round(this.cx + this.mW / 2.0F);
        int f = this.S(40.0);
        int x = left + this.S(13.0);
        this.face(g, mc, (UUID) null, x, cy - f / 2, f);
        x += f + this.S(12.0);
        int ss = this.S(11.0);
        int fs = this.S(17.0);
        int top = cy - (ss + this.S(3.0) + fs) / 2;
        Px.text(g, Lang.tr("Добро пожаловать"), x, top, Px.Weight.MEDIUM, ss, 0xFF8E8E98);
        Px.text(g, name, x, top + ss + this.S(3.0), Px.Weight.BOLD, fs, -1);
        int rx = right - this.S(16.0);
        int ws = this.S(13.0);
        int ps = this.S(11.0);
        int rtop = cy - (ws + this.S(4.0) + ps) / 2;
        Px.textRight(g, this.whereText(mc), rx, rtop, Px.Weight.SEMIBOLD, ws, -1);
        String pl = this.pingLine(mc);
        int pw = Px.textWidth(pl, Px.Weight.MEDIUM, ps);
        int py = rtop + ws + this.S(4.0);
        Px.text(g, pl, rx - pw, py, Px.Weight.MEDIUM, ps, 0xFF8E8E98);
        if (mc.getCurrentServer() != null) {
            this.bars(g, rx - pw - this.S(5.0) - this.barsWidth(), py + ps / 2, HudElements.ping());
        }
    }

    private int lead(Event e) {
        return e.kind == 0 ? this.S(26.0) : this.S(28.0);
    }

    private float eventWidth(Event e) {
        String sub = e.kind == 2 ? Lang.tr("Осталось") + ": 00" : e.sub;
        int text = Math.max(
                Px.textWidth(e.title, Px.Weight.SEMIBOLD, this.S(14.0)),
                sub.isEmpty() ? 0 : Px.textWidth(sub, Px.Weight.MEDIUM, this.S(11.0)));
        int w = this.S(7.0)
                + this.lead(e)
                + this.S(11.0)
                + text
                + (e.chip.isEmpty() ? 0 : this.S(12.0) + this.chipWidth(e.chip))
                + this.S(16.0);
        return (float) Math.min(Math.max(w, this.S(190.0)), this.S(420.0));
    }

    private void event(GuiGraphics g, Minecraft mc, Event e) {
        int cy = Math.round(this.mH / 2.0F);
        int left = Math.round(this.cx - this.mW / 2.0F);
        int right = Math.round(this.cx + this.mW / 2.0F);
        int x = left + this.S(7.0);
        int lead = this.lead(e);
        switch (e.kind) {
            case 0:
                this.mark(g, x, cy, lead, e.knob.v);
                break;
            case 1:
                this.tile(g, x, cy, lead, e.colour, e.on ? "sparkles" : "x");
                break;
            case 2:
                this.tile(g, x, cy, lead, -15043, null);
                if (this.totemStack == null) {
                    this.totemStack = new ItemStack(Items.TOTEM_OF_UNDYING);
                }

                int is = this.S(20.0);
                HudElements.item(g, this.totemStack, x + (lead - is) / 2, cy - is / 2, is);
                break;
            case 3:
                int f = this.S(26.0);
                this.face(g, mc, e.who, x + (lead - f) / 2, cy - f / 2, f);
                break;
            case 4:
                this.tile(g, x, cy, lead, e.colour, null);
                if (e.stack != null) {
                    is = this.S(18.0);
                    HudElements.item(g, e.stack, x + (lead - is) / 2, cy - is / 2, is);
                }
        }

        x += lead + this.S(11.0);
        String sub = e.kind == 2 ? Lang.tr("Осталось") + ": " + totems(mc) : e.sub;
        int chipW = e.chip.isEmpty() ? 0 : this.chipWidth(e.chip);
        int room = Math.max(this.S(20.0), right - this.S(14.0) - (chipW > 0 ? chipW + this.S(12.0) : 0) - x);
        int fs = this.S(14.0);
        int ss = this.S(11.0);
        String title = Px.ellipsize(e.title, Px.Weight.SEMIBOLD, fs, room);
        if (sub.isEmpty()) {
            Px.text(g, title, x, cy - fs / 2, Px.Weight.SEMIBOLD, fs, -1);
        } else {
            int top = cy - (fs + this.S(3.0) + ss) / 2;
            Px.text(g, title, x, top, Px.Weight.SEMIBOLD, fs, -1);
            int sc = e.kind != 0 && e.kind != 4
                    ? (e.kind == 2 ? -15043 : (e.kind == 3 ? -24822 : 0xFF8E8E98))
                    : e.colour;
            Px.text(
                    g,
                    Px.ellipsize(sub, Px.Weight.MEDIUM, ss, room),
                    x,
                    top + fs + this.S(3.0),
                    Px.Weight.MEDIUM,
                    ss,
                    sc);
        }

        if (chipW > 0) {
            this.chip(g, e.chip, right - this.S(12.0) - chipW, cy);
        }
    }

    /**
     * A module's state as a ring: switching it on draws the accent ring round from the top and fills its middle with
     * a dot; switching it off winds the ring back and leaves a small grey dot.
     */
    private void mark(GuiGraphics g, int x, int cy, int size, float pos) {
        float p = Math.max(0.0F, Math.min(1.0F, pos));
        int accent = Theme.accent() | 0xFF000000;
        float d = size, ox = x, oy = cy - d / 2.0F;
        float thick = Math.max(1.5F, this.S(2.2));
        if (HudShape.ready()) {
            HudShape.ring(g, ox, oy, d, thick, 1.0F, 0x24FFFFFF);
            if (p > 0.004F) HudShape.ring(g, ox, oy, d, thick, p, accent);
        } else {
            Px.ring(g, Math.round(ox + d / 2.0F), cy, Math.round(d / 2.0F), 0x24FFFFFF);
            if (p > 0.004F) {
                Px.arc(
                        g,
                        ox + d / 2.0F,
                        cy,
                        d / 2.0F - thick / 2.0F,
                        Math.round(thick),
                        -Math.PI / 2.0,
                        Math.PI * 2.0 * p,
                        accent,
                        accent);
            }
        }
        int on = Math.round(this.S(4.5) * p);
        int off = Math.round(this.S(2.5) * (1.0F - p));
        if (off > 0) Px.circle(g, Math.round(ox + d / 2.0F), cy, off, 0x66FFFFFF);
        if (on > 0) Px.circle(g, Math.round(ox + d / 2.0F), cy, on, accent);
    }

    private void tile(GuiGraphics g, int x, int cy, int size, int colour, String icon) {
        Px.circle(g, x + size / 2, cy, size / 2, Px.a(colour, 56));
        if (icon != null) {
            int is = Math.round((float) size * 0.5F);
            Px.icon(g, icon, x + (size - is) / 2, cy - is / 2, is, colour);
        }
    }

    private int chipWidth(String t) {
        return this.S(9.0) * 2 + Px.textWidth(t, Px.Weight.SEMIBOLD, this.S(12.0));
    }

    private void chip(GuiGraphics g, String t, int x, int cy) {
        int cw = this.chipWidth(t);
        int ch = this.S(24.0);
        Px.round(g, x, cy - ch / 2, cw, ch, this.S(8.0), 704643071);
        Px.outline(g, x, cy - ch / 2, cw, ch, this.S(8.0), 452984831);
        Px.text(g, t, x + this.S(9.0), cy - this.S(12.0) / 2, Px.Weight.SEMIBOLD, this.S(12.0), -1);
    }

    private static int totems(Minecraft mc) {
        if (mc.player == null) {
            return 0;
        } else {
            int n = 0;
            Inventory inv = mc.player.getInventory();

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.is(Items.TOTEM_OF_UNDYING)) {
                    n += st.getCount();
                }
            }

            return n;
        }
    }

    private static int musicColour() {
        int c = Music.coverColour();
        return c != 0 ? c | 0xFF000000 : Theme.accent2() | 0xFF000000;
    }

    private float musicWidth() {
        int bw = Math.max(2, this.S(2.5));
        int gap = Math.max(1, this.S(2.0));
        int title = Math.min(labelWidth(this.media.title(), this.S(11.5), true), this.S(170.0));
        return (float) (this.S(4.0)
                + this.S(20.0)
                + this.S(8.0)
                + title
                + this.S(10.0)
                + waveWidth(4, bw, gap)
                + this.S(13.0));
    }

    private float beat(float dt) {
        boolean playing = this.media.present() && this.media.playing();
        if (playing) {
            this.beatClock =
                    this.media.duration() > 0L ? (double) Music.position() / 1000.0 : this.beatClock + (double) dt;
        }

        double t = this.beatClock;
        float beat = playing ? (float) Math.pow(1.0 - (t * 2.0 - Math.floor(t * 2.0)), 3.0) : 0.0F;

        for (int i = 0; i < this.levels.length; i++) {
            double lag = t * 2.0 - (double) i * 0.07;
            float target = playing
                    ? (float) Math.min(
                            1.0,
                            0.2
                                    + 0.5
                                            * Math.abs(Math.sin(
                                                    t * Math.PI * (1.7 + (double) i * 0.43) + (double) i * 1.3))
                                            * (0.55 + 0.45 * Math.abs(Math.sin(t * 2.3 + (double) i * 2.1)))
                                    + 0.42 * Math.pow(1.0 - (lag - Math.floor(lag)), 4.0))
                    : 0.14F;
            float rate = target > this.levels[i] ? 26.0F : 8.0F;
            this.levels[i] += (target - this.levels[i]) * (1.0F - (float) Math.exp((double) (-dt * rate)));
        }

        return beat;
    }

    private static int waveWidth(int n, int bw, int gap) {
        return n * bw + (n - 1) * gap;
    }

    private void wave(GuiGraphics g, int x, int cy, int n, int maxH, int bw, int gap, int col) {
        for (int i = 0; i < n; i++) {
            float lv = this.levels[(i + (n < this.levels.length ? 1 : 0)) % this.levels.length];
            int bh = Math.max(bw, Math.round((float) maxH * lv));
            Px.round(g, x + i * (bw + gap), cy - bh / 2, bw, bh, bw / 2, col);
        }
    }

    private void cover(GuiGraphics g, int x, int y, int size) {
        Identifier c = Music.cover();
        if (c != null) {
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    c,
                    x,
                    y,
                    0.0F,
                    0.0F,
                    size,
                    size,
                    128,
                    128,
                    128,
                    128,
                    Px.a(0xFFFFFF, Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, Px.fade)))));
        } else {
            Px.round(g, x, y, size, size, Math.max(2, size / 5), 0xFF2A2333);
            int is = size / 2;
            Px.icon(g, "music", x + (size - is) / 2, y + (size - is) / 2, is, 0xFFCFB5F3);
        }
    }

    private void progress(GuiGraphics g, int x, int y, int w, int h, int col) {
        long pos = Music.position();
        long dur = this.media.duration();
        Px.round(g, x, y, w, h, h / 2, 872415231);
        if (dur > 0L) {
            Px.round(
                    g,
                    x,
                    y,
                    Math.max(h, Math.round((float) w * Math.min(1.0F, (float) pos / (float) dur))),
                    h,
                    h / 2,
                    col);
        }
    }

    private void nowPlaying(GuiGraphics g) {
        int cy = Math.round(this.mH / 2.0F);
        int left = Math.round(this.cx - this.mW / 2.0F);
        int right = Math.round(this.cx + this.mW / 2.0F);
        int art = this.S(56.0);
        int ax = left + this.S(12.0);
        int ay = cy - art / 2;
        int col = musicColour();
        Px.shadow(g, ax, ay, art, art, this.S(10.0), Px.a(col, 110));
        g.pose().pushMatrix();
        g.pose().translate((float) ax + (float) art / 2.0F, (float) cy);
        g.pose().scale(this.pop.v, this.pop.v);
        g.pose().translate(-((float) ax + (float) art / 2.0F), (float) (-cy));
        this.cover(g, ax, ay, art);
        g.pose().popMatrix();
        int bw = Math.max(2, this.S(3.0));
        int gap = Math.max(1, this.S(3.0));
        int ww = waveWidth(6, bw, gap);
        int wx = right - this.S(18.0) - ww;
        int tx = ax + art + this.S(13.0);
        int tw = Math.max(this.S(20.0), wx - this.S(14.0) - tx);
        int ls = this.S(10.0);
        int fs = this.S(15.0);
        int ss = this.S(12.0);
        int top = ay + this.S(1.0);
        Px.text(g, Lang.tr("Сейчас играет"), tx, top, Px.Weight.MEDIUM, ls, Px.mix(col, -1, 0.35F));
        this.scrolling(g, this.media.title(), tx, top + ls + this.S(4.0), tw, fs, Px.Weight.SEMIBOLD, -1);
        this.scrolling(
                g,
                this.media.artist(),
                tx,
                top + ls + this.S(4.0) + fs + this.S(3.0),
                tw,
                ss,
                Px.Weight.MEDIUM,
                0xFF8E8E98);
        this.progress(g, tx, ay + art - this.S(3.0), tw, Math.max(2, this.S(3.0)), col);
        this.wave(g, wx, cy, 6, this.S(26.0), bw, gap, col);
    }

    private void compactMusic(GuiGraphics g) {
        int cy = Math.round(this.mH / 2.0F);
        int left = Math.round(this.cx - this.mW / 2.0F);
        int right = Math.round(this.cx + this.mW / 2.0F);
        int art = this.S(20.0);
        int x = left + this.S(4.0);
        this.cover(g, x, cy - art / 2, art);
        x += art + this.S(8.0);
        int bw = Math.max(2, this.S(2.5));
        int gap = Math.max(1, this.S(2.0));
        int ww = waveWidth(4, bw, gap);
        int wx = right - this.S(13.0) - ww;
        int fs = this.S(11.5);
        int room = Math.max(this.S(20.0), wx - this.S(10.0) - x);
        String title = this.media.title();
        if (labelWidth(title, fs, true) > room) {
            while (title.length() > 1 && labelWidth(title + "…", fs, true) > room)
                title = title.substring(0, title.length() - 1);
            title = title + "…";
        }
        this.label(g, title, x, cy, fs, true, this.media.playing() ? -1 : 0xFF8E8E98);
        this.wave(g, wx, cy, 4, this.S(14.0), bw, gap, musicColour());
    }

    private void player(GuiGraphics g, Minecraft mc) {
        int left = Math.round(this.cx - this.mW / 2.0F);
        int right = Math.round(this.cx + this.mW / 2.0F);
        int pad = this.S(16.0);
        int art = this.S(60.0);
        int ax = left + pad;
        int col = musicColour();
        Px.shadow(g, ax, pad, art, art, this.S(10.0), Px.a(col, 100));
        this.cover(g, ax, pad, art);
        int bw = Math.max(2, this.S(3.0));
        int gp = Math.max(1, this.S(3.0));
        int ww = waveWidth(5, bw, gp);
        int wx = right - pad - ww;
        int tx = ax + art + this.S(14.0);
        int tw = Math.max(this.S(20.0), wx - this.S(12.0) - tx);
        this.scrolling(g, this.media.title(), tx, pad + this.S(10.0), tw, this.S(16.0), Px.Weight.SEMIBOLD, -1);
        this.scrolling(g, this.media.artist(), tx, pad + this.S(34.0), tw, this.S(12.0), Px.Weight.MEDIUM, 0xFF8E8E98);
        this.wave(g, wx, pad + art / 2, 5, this.S(22.0), bw, gp, col);
        int py = pad + art + this.S(16.0);
        int pw = right - left - pad * 2;
        this.progress(g, left + pad, py, pw, Math.max(2, this.S(4.0)), col);
        long pos = Music.position();
        long dur = this.media.duration();
        int ts = this.S(10.0);
        Px.text(g, Music.time(pos), left + pad, py + this.S(9.0), Px.Weight.MEDIUM, ts, 0xFF8E8E98);
        Px.textRight(
                g,
                dur > 0L ? "−" + Music.time(dur - pos) : "0:00",
                right - pad,
                py + this.S(9.0),
                Px.Weight.MEDIUM,
                ts,
                0xFF8E8E98);
        int by = Math.round(this.mH) - this.S(32.0);
        double mx = mouseX(mc) - (double) this.placedX;
        double my = mouseY(mc) - (double) this.placedY;

        for (int i = 0; i < 3; i++) {
            int size = this.S(i == 1 ? 34.0 : 28.0);
            int bx = Math.round(this.cx) + (i - 1) * this.S(54.0) - size / 2;
            int top = by - size / 2;
            this.buttons[i][0] = this.placedX + bx;
            this.buttons[i][1] = this.placedY + top;
            this.buttons[i][2] = size;
            this.buttons[i][3] = size;
            boolean hov =
                    mx >= (double) bx && mx < (double) (bx + size) && my >= (double) top && my < (double) (top + size);
            this.buttonHover[i] += ((float) (hov ? 1 : 0) - this.buttonHover[i]) * 0.25F;
            int fill = i == 1
                    ? Px.mix(704643071, 1207959551, this.buttonHover[i])
                    : Px.a(0xFFFFFF, Math.round(48.0F * this.buttonHover[i]));
            Px.circle(g, bx + size / 2, by, size / 2, fill);
            String glyph = i == 0 ? "prev" : (i == 2 ? "next" : (this.media.playing() ? "pause" : "play"));
            int gs = Math.round((float) size * 0.5F);
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/gui/media/" + glyph + ".png"),
                    bx + (size - gs) / 2,
                    top + (size - gs) / 2,
                    0.0F,
                    0.0F,
                    gs,
                    gs,
                    64,
                    64,
                    64,
                    64,
                    Px.am(-1, Px.fade));
        }
    }

    private void info(GuiGraphics g, Minecraft mc, String name) {
        int left = Math.round(this.cx - this.mW / 2.0F);
        int right = Math.round(this.cx + this.mW / 2.0F);
        int pad = this.S(16.0);
        int f = this.S(36.0);
        int x = left + pad;
        this.face(g, mc, (UUID) null, x, pad, f);
        int tx = x + f + this.S(12.0);
        Px.text(
                g,
                Px.ellipsize(name, Px.Weight.BOLD, this.S(16.0), right - pad - tx),
                tx,
                pad + this.S(2.0),
                Px.Weight.BOLD,
                this.S(16.0),
                -1);
        Px.text(g, this.whereText(mc), tx, pad + this.S(22.0), Px.Weight.MEDIUM, this.S(12.0), 0xFF8E8E98);
        int ty = pad + f + this.S(14.0);
        int th = this.S(48.0);
        int gap = this.S(8.0);
        int tw = (right - left - pad * 2 - gap * 2) / 3;
        this.stat(g, left + pad, ty, tw, th, Integer.toString(this.sampledFps), "FPS", -1);
        boolean online = mc.getCurrentServer() != null;
        this.stat(
                g,
                left + pad + tw + gap,
                ty,
                tw,
                th,
                online ? Integer.toString(HudElements.ping()) : "—",
                online ? Lang.tr("Пинг, мс") : Lang.tr("локально"),
                online ? HudElements.ping() : -1);
        this.stat(g, left + pad + (tw + gap) * 2, ty, tw, th, this.session(), Lang.tr("В игре"), -1);
    }

    private void stat(GuiGraphics g, int x, int y, int w, int h, String value, String label, int latency) {
        Px.round(g, x, y, w, h, this.S(14.0), 452984831);
        int vx = x + this.S(12.0);
        if (latency >= 0) {
            vx += this.bars(g, vx, y + this.S(17.0), latency) + this.S(6.0);
        }

        Px.text(g, value, vx, y + this.S(9.0), Px.Weight.SEMIBOLD, this.S(16.0), -1);
        Px.text(g, label, x + this.S(12.0), y + this.S(29.0), Px.Weight.MEDIUM, this.S(10.0), 0xFF8E8E98);
    }

    private String session() {
        if (this.joinedAt <= 0L) {
            return "—";
        } else {
            long s = Math.max(0L, (System.currentTimeMillis() - this.joinedAt) / 1000L);
            return s >= 3600L
                    ? String.format("%d:%02d:%02d", s / 3600L, s / 60L % 60L, s % 60L)
                    : String.format("%d:%02d", s / 60L, s % 60L);
        }
    }

    private void face(GuiGraphics g, Minecraft mc, UUID who, int x, int y, int size) {
        PlayerSkin skin = null;
        if (who == null) {
            if (mc.player != null) {
                skin = mc.player.getSkin();
            } else {
                if (this.ownSkin == null) {
                    this.ownSkin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
                }

                skin = (PlayerSkin) this.ownSkin.get();
            }
        } else if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(who);
            if (info != null) {
                skin = info.getSkin();
            }
        }

        if (skin == null) {
            Px.round(g, x, y, size, size, Math.max(2, size / 4), 0xFF2C2C30);
            int is = size * 3 / 5;
            Px.icon(g, "user", x + (size - is) / 2, y + (size - is) / 2, is, 0xFF8E8E98);
        } else {
            PlayerFaceRenderer.draw(
                    g, skin, x, y, size, Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, Px.fade))) << 24 | 0xFFFFFF);
        }
    }

    private void scrolling(GuiGraphics g, String text, int x, int y, int width, int fs, Px.Weight weight, int colour) {
        if (width > 0) {
            int overflow = Px.textWidth(text, weight, fs) - width;
            float scroll = overflow > 0 && Theme.animations
                    ? (float) overflow
                            * (float) (0.5
                                    - 0.5
                                            * Math.cos((double) System.nanoTime()
                                                    / (double) 1.0E9F
                                                    / Math.max(3.0, (double) overflow / 22.0)))
                    : 0.0F;
            g.enableScissor(x, y - this.S(2.0), x + width, y + fs + this.S(3.0));

            try {
                g.pose().pushMatrix();
                g.pose().translate(-scroll, 0.0F);
                Px.text(g, text, x, y, weight, fs, colour);
                g.pose().popMatrix();
            } finally {
                g.disableScissor();
            }
        }
    }

    private boolean heard(Music.Track t, long now) {
        if (!t.present()) {
            this.heardTitle = null;
            this.playingSince = 0L;
            return false;
        } else if (t.playing()) {
            this.pausedAt = 0L;
            if (t.title().equals(this.heardTitle)) {
                return true;
            } else {
                if (this.playingSince == 0L) {
                    this.playingSince = now;
                }

                if (now - this.playingSince < 1500L) {
                    return false;
                } else {
                    this.heardTitle = t.title();
                    return true;
                }
            }
        } else {
            this.playingSince = 0L;
            if (!t.title().equals(this.heardTitle)) {
                return false;
            } else {
                if (this.pausedAt == 0L) {
                    this.pausedAt = now;
                }

                if (now - this.pausedAt > 60000L) {
                    this.heardTitle = null;
                    return false;
                } else {
                    return true;
                }
            }
        }
    }

    public boolean musicClick(double x, double y, int button) {
        if (button == 0 && this.enabled()) {
            if (!(Minecraft.getInstance().screen instanceof ChatScreen)) {
                return false;
            } else {
                if (this.expanded && this.view == DynamicIsland.View.PLAYER && this.mH > (float) this.S(150.0)) {
                    for (int i = 0; i < 3; i++) {
                        if (inside(this.buttons[i], x, y)) {
                            Music.control(i == 0 ? "prev" : (i == 1 ? "toggle" : "next"));
                            UiSound.play("click");
                            return true;
                        }
                    }
                }

                if (inside(this.hit, x, y)) {
                    this.expanded = !this.expanded;
                    UiSound.play("select");
                    return true;
                } else {
                    this.expanded = false;
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    private static enum View {
        DROP,
        WELCOME,
        IDLE,
        EVENT,
        NOW_PLAYING,
        MUSIC,
        PLAYER,
        INFO,
        HEARTS,
        HOLY;
    }

    private static final class Spring {
        float v;
        float vel;
        private boolean set;

        float step(float target, float dt, float stiffness, float damping) {
            if (this.set && Theme.animations) {
                for (float left = dt; left > 1.0E-5F; left -= 0.004166667F) {
                    float h = Math.min(left, 0.004166667F);
                    this.vel += (stiffness * (target - this.v) - damping * this.vel) * h;
                    this.v += this.vel * h;
                }

                return this.v;
            } else {
                this.v = target;
                this.vel = 0.0F;
                this.set = true;
                return this.v;
            }
        }

        void snap(float x) {
            this.v = x;
            this.vel = 0.0F;
            this.set = true;
        }

        void kick(float speed) {
            this.vel += speed;
        }
    }

    private static final class Event {
        String key = "";
        String title = "";
        String sub = "";
        String chip = "";
        int kind;
        int colour;
        boolean on;
        UUID who;
        ItemStack stack;
        long born;
        long life;
        float from;
        final Spring knob = new Spring();
    }
}
