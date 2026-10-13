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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.gui.FrontEndLoading;
import net.arcanic.visuals.media.Music;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.BrandLogo;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** The clock / island / FPS layout and its compact-to-player interaction. */
public final class DynamicIsland extends HudModule {
    private final Setting.Bool nick = add(new Setting.Bool("show_nick", "Ник", true));
    private final Setting.Bool server = add(new Setting.Bool("server", "Сервер", true));
    private final Setting.Bool fps = add(new Setting.Bool("fps", "FPS", true));
    private final Setting.Bool ping = add(new Setting.Bool("ping", "Пинг", true));
    private final Setting.Bool time = add(new Setting.Bool("time", "Время", true));
    private final Setting.Bool centered = add(new Setting.Bool("centered", "По центру экрана", true));
    public final Setting.Bool music = add(new Setting.Bool("music", "Музыка (Яндекс и другие)", true));
    private final Anim anim = new Anim();
    private final IslandIntro intro = new IslandIntro();
    private String caption = "Arcanic", previous = "Arcanic";
    private Music.Track media = Music.Track.EMPTY;
    private long lastEvent, fpsAt;
    private int sampledFps, renderedFrames;
    private boolean arrived, expanded, clickable, stateOn = true;
    private long stateStart;

    private static final int ON = 0xFF6EE7B7, OFF = 0xFFF5A3B5;
    private float expansion;
    private final int[] hit = new int[4];
    private final int[][] buttons = new int[3][4];
    private static final Identifier PACK_ICON = Identifier.fromNamespaceAndPath("arcanicvisuals", "island_pack_icon");
    private String packTitle;
    private boolean packOn, packHasIcon;
    private int packIconW = 64, packIconH = 64;

    public DynamicIsland() {
        super(
                "watermark",
                "Вотермарка",
                "Островок Arcanic в стиле Arcanic. Плеер раскрывается нажатием в чате или меню.",
                false,
                .008,
                .012);
        anim.set("text", 1);
        anim.set("flash", 0);
        anim.set("expand", 0);
    }

    public void joined() {
        intro.queue();
        arrived = true;
        expanded = false;
        lastEvent = 0;
    }

    public void disconnected() {
        intro.reset();
        arrived = false;
        expanded = false;
        clickable = false;
    }

    @Override
    public int x(int screenW) {
        return centered.on() ? (screenW - w) / 2 : super.x(screenW);
    }

    public void detach(int screenW) {
        if (centered.on()) {
            fx = x(screenW) / (double) screenW;
            centered.set(false);
        }
    }

    public float[] landingBox(int sw, int sh) {
        int height = S(40), width = Math.max(S(250), w);
        int x = Math.max(0, Math.min(sw - width, centered.on() ? (sw - width) / 2 : super.x(sw)));
        return new float[] {x + width / 2f, Math.max(0, y(sh)) + height / 2f - S(12), S(24)};
    }

    public float[] landing() {
        prepare();
        return landingBox(Px.width(), Px.height());
    }

    private static float stage(float age, float delay, float duration) {
        return IslandIntro.stage(age, delay, duration);
    }

    private static boolean inside(int[] r, double x, double y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        anim.frame();
        var mc = HudElements.mc();
        long now = System.currentTimeMillis();
        if (music.on() && !editing) Music.request();
        Music.Track observed = Music.track();
        boolean cursor = mc.screen instanceof ChatScreen;
        boolean heard = heard(observed, now);
        if (!cursor || !music.on() || !heard || editing) expanded = false;
        float expand = expansion = anim.to("expand", expanded ? 1 : 0, 10);
        var events = Toasts.active();
        var event = events.isEmpty() ? null : events.getLast();
        boolean pack = false;
        boolean musical = music.on() && heard && !pack && event == null && !editing;
        if (observed.present()) media = observed;
        String desired =
                musical ? media.title() : pack ? packTitle : event == null ? "Arcanic" : Lang.tr(event.title());
        String sub = musical
                ? (media.playing() ? "" : Lang.tr("Пауза · ")) + media.artist()
                : pack
                        ? Lang.tr(packOn ? "Ресурспак включён" : "Ресурспак выключен")
                        : event != null && !event.key().equals("menu")
                                ? Lang.tr(event.on() ? "Включено" : "Выключено")
                                        + (event.key().isBlank() || event.key().equals("—")
                                                ? ""
                                                : " · " + Lang.tr(event.key()))
                                : nick.on() ? mc.getUser().getName() : "";
        boolean logoShown = !musical && !pack && (event == null || event.key().equals("menu"));
        if (!desired.equals(caption)) {
            previous = caption;
            caption = desired;
            anim.set("text", 0);
            anim.set("flash", .8f);
        }
        if (event != null && event.born() != lastEvent) {
            lastEvent = event.born();
            anim.set("flash", 1);
            stateStart = System.nanoTime();
        }
        float swap = anim.to("text", 1, 9),
                flash = anim.to("flash", 0, 3),
                musicK = anim.to("media", musical ? 1 : 0, 8);
        int accent = pack
                ? (packOn ? ON : OFF)
                : event == null || event.key().equals("menu") ? Theme.accent2() : event.on() ? ON : OFF;
        int fs = Math.max(9, S(14)), small = Math.max(8, S(10)), barH = S(40), icon = S(24), pad = S(10), gap = S(9);
        if (now - fpsAt >= 500) {
            fpsAt = now;
            sampledFps = mc.getFps();
        }
        LocalTime clock = LocalTime.now();
        String clockText = String.format("%02d:%02d", clock.getHour(), clock.getMinute());
        // clock and FPS/ping sit in small satellite capsules beside the island, so they read on any background
        int capH = S(30), cpad = S(11), cgap = S(6);
        String fpsText = Integer.toString(sampledFps);
        int clockW = Px.textWidth(clockText, Weight.MEDIUM, fs), fpsW = Px.textWidth(fpsText, Weight.SEMIBOLD, fs);
        int leftW = time.on() ? clockW + cpad * 2 : 0;
        int rightIn = (fps.on() ? S(19) + fpsW : 0) + (fps.on() && ping.on() ? S(10) : 0) + (ping.on() ? S(17) : 0);
        int rightW = rightIn > 0 ? rightIn + cpad * 2 : 0;
        float lw = anim.to("lw", leftW, 14), rw = anim.to("rw", rightW, 14);
        int widest = Math.round(Math.max(lw, rw));
        int side = widest > 0 ? widest + cgap : 0, maxPill = Math.max(S(90), Px.width() - side * 2 - S(24));
        int textMax = Math.max(S(25), Math.min(S(185), maxPill - icon - pad * 2 - gap - (musical ? S(30) : 0)));
        String shown = Px.ellipsize(caption, Weight.SEMIBOLD, fs, textMax);
        String old = Px.ellipsize(previous, Weight.SEMIBOLD, fs, textMax);
        sub = Px.ellipsize(sub, Weight.MEDIUM, small, textMax);
        int textW = Math.max(Px.textWidth(shown, Weight.SEMIBOLD, fs), Px.textWidth(sub, Weight.MEDIUM, small));
        int want = Math.min(maxPill, Math.max(S(128), pad * 2 + icon + gap + textW + S(6) + (musical ? S(30) : 0)));
        float pill = anim.to("pill", want, 10);
        int panelW = Math.min(
                Px.width() - S(24),
                Math.max(
                        S(300),
                        Math.min(S(380), S(135) + Px.textWidth(media.title(), Weight.SEMIBOLD, Math.max(9, S(16))))));
        int oldW = w;
        w = Math.min(Px.width() - S(8), Math.round(pill + side * 2 + (panelW - pill) * expand));
        h = Math.round(barH + (S(158) - barH) * expand);
        int shift = centered.on() ? (Px.width() - w) / 2 - (Px.width() - oldW) / 2 : 0;
        float age = editing
                ? 10
                : intro.seconds(
                        System.nanoTime(),
                        (mc.level == null
                                        ? FrontEndLoading.splashReleased()
                                        : mc.player != null && mc.screen == null && mc.getOverlay() == null)
                                && !FrontEndLoading.holdsIsland(),
                        Theme.animations);
        float arrival = stage(age, 0, .45f), opening = stage(age, .1f, .65f);
        int shellW = Math.round(barH + (pill + (panelW - pill) * expand - barH) * opening), shellX = (w - shellW) / 2;
        int radius = Math.round(barH / 2f + (S(18) - barH / 2f) * expand);
        int originX = Math.max(0, Math.min(Px.width() - w, x(Px.width()))),
                originY = Math.max(0, Math.min(Px.height() - h, y(Px.height())));
        hit[0] = originX + shellX;
        hit[1] = originY;
        hit[2] = shellW;
        hit[3] = h;
        clickable = cursor && music.on() && heard && arrival > .95f && !editing;
        boolean stateEv = event != null && !event.key().equals("menu") && !pack && !musical;
        if (stateEv) stateOn = event.on();
        String capText = musical ? media.title() : stateEv ? caption : "";
        String capSub = musical
                ? (media.playing() ? "" : Lang.tr("пауза"))
                : stateEv ? Lang.tr(event.on() ? "вкл" : "выкл") : "";
        int placedX = Math.max(0, Math.min(Px.width() - oldW, x(Px.width())));
        bar(g, mc, oldW, capText, capSub, stateEv, musical, flash, arrival, age);
        if (expand > .01f) playerCard(g, placedX, panelW, expand, cursor);
        renderedFrames++;
    }

    /** The player under the watermark: cover, title, progress and the three buttons. */
    private void playerCard(GuiGraphics g, int placedX, int width, float expand, boolean cursor) {
        int sw = Px.width(), sh = Px.height(), height = S(158), top = S(36) / 2 * 2 + S(8);
        int left = (sw - width) / 2 - placedX, placedY = Math.max(0, Math.min(sh - h, y(sh)));
        float k = (float) easeOut(expand), saved = Px.fade;
        g.pose().pushMatrix();
        try {
            g.pose().translate(left + width / 2f, top - S(6) * (1 - k));
            g.pose().scale(.96f + .04f * k, .96f + .04f * k);
            g.pose().translate(-width / 2f, 0);
            Px.fade = saved * k;
            Px.shadow(g, 0, S(3), width, height, S(14), 0x70000000);
            Px.round(g, 0, 0, width, height, S(16), 0xF00D0C12);
            Px.outline(g, 0, 0, width, height, S(16), 0x26FFFFFF);
            g.enableScissor(0, 0, width, height);
            try {
                player(g, 0, width, placedX + left, placedY + top, cursor && expand > .8f);
            } finally {
                g.disableScissor();
            }
        } finally {
            Px.fade = saved;
            g.pose().popMatrix();
        }
    }

    private final Map<String, double[]> tweens = new HashMap<>();

    private float tw(String k, float target, float ms) {
        double now = System.nanoTime() / 1e6;
        double[] t = tweens.get(k);
        if (t == null || !Theme.animations) {
            tweens.put(k, new double[] {target, target, now});
            return target;
        }
        double p = Math.min(1, (now - t[2]) / ms), cur = t[0] + (t[1] - t[0]) * easeOut(p);
        if (t[1] != target) {
            t[0] = cur;
            t[1] = target;
            t[2] = now;
        }
        return (float) cur;
    }

    private static double easeOut(double p) {
        return 1 - Math.pow(1 - p, 3);
    }

    private static int steady(String s, Weight wt, int fs) {
        return Math.max(Px.textWidth(s, wt, fs), Px.textWidth(s.replaceAll("[0-9]", "0"), wt, fs));
    }

    private String barCap = "", barSub = "", prevCap = "", prevSub = "";
    private boolean prevState, prevOn;
    private long capSwapAt;

    private void bar(
            GuiGraphics g,
            Minecraft mc,
            int oldW,
            String cap,
            String capSub,
            boolean stateEv,
            boolean musical,
            float flash,
            float arrival,
            float age) {
        int bh = S(36) / 2 * 2,
                fs = Math.max(9, S(14)),
                is = S(15),
                pad = S(14),
                igap = S(7),
                sgap = S(12),
                mark = S(20),
                face = Math.max(8, Math.round(S(16) / 8f) * 8);
        int text = 0xFFEEEBF3, faint = 0xFF8E879B, icon = Theme.accent2();
        int sw = Px.width(), sh = Px.height();
        // the segments after the mark
        List<String[]> segs = new ArrayList<>();
        if (nick.on()) segs.add(new String[] {"face", mc.getUser().getName()});
        if (server.on() && mc.level != null) {
            var sd = mc.getCurrentServer();
            segs.add(new String[] {"server-s", sd != null ? sd.ip : Lang.tr("Одиночная игра")});
        }
        if (fps.on()) segs.add(new String[] {"w.rocket", sampledFps + " FPS"});
        if (ping.on() && mc.getCurrentServer() != null) segs.add(new String[] {"ping-s", HudElements.ping() + " MS"});
        if (time.on()) {
            var c = LocalTime.now();
            segs.add(new String[] {"clock-s", String.format("%02d:%02d", c.getHour(), c.getMinute())});
        }

        // the caption beside the mark: a new one slides up over the old, an empty one folds away
        String capLine = cap.isBlank() ? "" : Px.ellipsize(cap, Weight.SEMIBOLD, fs, S(musical ? 120 : 160));
        if (!capLine.isEmpty() && (!capLine.equals(barCap) || !capSub.equals(barSub))) {
            prevCap = barCap;
            prevSub = barSub;
            prevState = barState;
            prevOn = barOn;
            barCap = capLine;
            barSub = capSub;
            capSwapAt = System.nanoTime();
        }
        if (!capLine.isEmpty()) {
            barState = stateEv;
            barOn = stateOn;
            barMusic = musical;
        }
        int waveN = 7;
        int capW = capLine.isEmpty() ? 0 : capWidth(capLine, capSub, fs, musical, waveN);
        float cw = tw("capw", capW > 0 ? capW + igap : 0, 320);
        int full = Math.max(1, capWidth(barCap, barSub, fs, barMusic, waveN) + igap);

        int segTotal = 0;
        int[] sw2 = new int[segs.size()];
        for (int i = 0; i < segs.size(); i++) {
            boolean figure = !segs.get(i)[0].equals("face") && !segs.get(i)[0].equals("server-s");
            sw2[i] = (segs.get(i)[0].equals("face") ? face : is)
                    + igap
                    + (figure
                            ? steady(segs.get(i)[1], Weight.MEDIUM, fs)
                            : Px.textWidth(segs.get(i)[1], Weight.MEDIUM, fs));
            segTotal += sgap * 2 + 1 + sw2[i];
        }
        // the width follows the content exactly: the caption and the segments are each tweened, the sum is not
        w = pad + mark + Math.round(cw) + Math.round(tw("segw", segTotal, 320)) + pad;
        h = bh;
        // always centred: undo where the HUD placed us (it used last frame's width) and stand in the middle
        int placedX = Math.max(0, Math.min(sw - oldW, x(sw)));
        int shift = (sw - w) / 2 - placedX;
        int originY = Math.max(0, Math.min(sh - h, y(sh)));
        hit[0] = (sw - w) / 2;
        hit[1] = originY;
        hit[2] = w;
        hit[3] = h;

        // arrival on entering a world: the capsule fades in and grows slightly, then the words follow
        boolean intro = Theme.animations && age < 3;
        float appear = intro ? (float) easeOut(Math.max(0, Math.min(1, age / .45f))) : 1;
        float words = intro ? (float) easeOut(Math.max(0, Math.min(1, (age - .18f) / .42f))) : 1;
        float saved = Px.fade;
        g.pose().pushMatrix();
        try {
            g.pose().translate(shift, (1 - appear) * S(6));
            if (appear < 1) {
                float k = .96f + .04f * appear;
                g.pose().translate(w / 2f, bh / 2f);
                g.pose().scale(k, k);
                g.pose().translate(-w / 2f, -bh / 2f);
            }
            int cy = bh / 2;
            int capW0 = w, capX = 0;
            int stateCol = barState ? (barOn ? ON : OFF) : Theme.accent2();
            Px.fade = saved * appear;
            if (background.on()) {
                Px.shadow(g, capX, S(3), capW0, bh, S(12), 0x70000000);
                Px.round(g, capX, 0, capW0, bh, bh / 2, 0xF00D0C12);
                Px.outline(
                        g,
                        capX,
                        0,
                        capW0,
                        bh,
                        bh / 2,
                        Px.mix(0x26FFFFFF, Px.a(stateCol, 190), Math.min(1, flash * .8f)));
            }
            g.enableScissor(capX - S(4), -S(4), capX + capW0 + S(4), bh + S(4));
            try {
                int x = pad;
                float base = Px.fade;
                // the mark, or the check / cross of a switched module, or the cover of the track
                float sk = tw("bstate", stateEv ? 1 : 0, 220), mk = tw("bmedia", musical ? 1 : 0, 260);
                if (mk > .01f) {
                    Px.fade = base * mk;
                    artwork(g, x, cy - mark / 2, mark);
                }
                if (sk > .01f) {
                    Px.fade = base * sk * (1 - mk);
                    stateMark(g, x + mark / 2, cy, mark / 2, stateOn, (System.nanoTime() - stateStart) / 1e6f);
                }
                if (sk < .99f && mk < .99f) {
                    Px.fade = base * (1 - sk) * (1 - mk);
                    BrandLogo.idle(g, x, cy - mark / 2, mark);
                }
                base = saved * appear * words; // everything after the mark arrives together, a beat later
                x += mark;
                int capArea = Math.round(cw);
                if (capArea > 1 && !barCap.isEmpty()) {
                    g.enableScissor(x, -S(4), x + capArea + sgap - Math.max(1, S(2)), bh + S(4));
                    float capOpen = Math.min(1, cw / full);
                    float sp = Theme.animations
                            ? (float) easeOut(Math.min(1, (System.nanoTime() - capSwapAt) / 280e6))
                            : 1;
                    int lift = S(10);
                    if (sp < .999f && !prevCap.isEmpty()) {
                        Px.fade = base * capOpen * (1 - sp);
                        caption(
                                g,
                                prevCap,
                                prevSub,
                                prevState,
                                prevOn,
                                false,
                                x + igap,
                                cy - Math.round(lift * sp),
                                fs,
                                text,
                                faint,
                                waveN);
                    }
                    Px.fade = base * capOpen * (prevCap.isEmpty() ? 1 : sp);
                    caption(
                            g,
                            barCap,
                            barSub,
                            barState,
                            barOn,
                            barMusic,
                            x + igap,
                            cy + (prevCap.isEmpty() ? 0 : Math.round(lift * (1 - sp))),
                            fs,
                            text,
                            faint,
                            waveN);
                    Px.fade = base;
                    g.disableScissor();
                }
                if (capW == 0 && cw < .5f) {
                    barCap = "";
                    prevCap = "";
                }
                x += capArea;
                for (int i = 0; i < segs.size(); i++) {
                    Px.fade = base;
                    g.pose().pushMatrix();
                    g.pose().translate(-S(4) * (1 - words), 0);
                    x += sgap;
                    Px.rect(g, x, cy - S(7), 1, S(14), 0x26FFFFFF);
                    x += 1 + sgap;
                    String[] sgm = segs.get(i);
                    if (sgm[0].equals("face")) {
                        if (mc.player != null)
                            PlayerFaceRenderer.draw(
                                    g,
                                    mc.player.getSkin(),
                                    x,
                                    cy - face / 2,
                                    face,
                                    (Math.round(255 * Math.max(0, Math.min(1, Px.fade))) << 24) | 0xFFFFFF);
                        else Px.icon(g, "a.user-s", x, cy - is / 2, is, icon);
                        x += face + igap;
                    } else {
                        int ic = icon;
                        if (sgm[0].equals("ping-s")) {
                            int l = HudElements.ping();
                            ic = l < 140 ? icon : l < 250 ? 0xFFF5C46B : 0xFFF87171;
                        }
                        Px.icon(g, sgm[0].startsWith("w.") ? sgm[0] : "a." + sgm[0], x, cy - is / 2, is, ic);
                        x += is + igap;
                    }
                    Px.text(g, sgm[1], x, cy - fs / 2, Weight.MEDIUM, fs, text);
                    x += sw2[i] - (sgm[0].equals("face") ? face : is) - igap;
                    g.pose().popMatrix();
                }
                Px.fade = base;
            } finally {
                g.disableScissor();
            }
        } finally {
            Px.fade = saved;
            g.pose().popMatrix();
        }
    }

    private boolean barState, barOn, barMusic;

    private int capWidth(String cap, String sub, int fs, boolean musical, int waveN) {
        if (cap.isEmpty()) return 0;
        int w = Px.textWidth(cap, Weight.SEMIBOLD, fs);
        if (!sub.isBlank()) w += S(7) + Px.textWidth(sub, Weight.MEDIUM, fs);
        if (musical) w += S(10) + waveW(waveN);
        return w;
    }

    private void caption(
            GuiGraphics g,
            String cap,
            String sub,
            boolean state,
            boolean on,
            boolean musical,
            int x,
            int cy,
            int fs,
            int text,
            int faint,
            int waveN) {
        Px.text(g, cap, x, cy - fs / 2, Weight.SEMIBOLD, fs, text);
        int sx = x + Px.textWidth(cap, Weight.SEMIBOLD, fs);
        if (!sub.isBlank()) {
            Px.text(g, sub, sx + S(7), cy - fs / 2, Weight.MEDIUM, fs, state ? (on ? ON : OFF) : faint);
            sx += S(7) + Px.textWidth(sub, Weight.MEDIUM, fs);
        }
        if (musical) wave(g, sx + S(10), cy, waveN, media.playing());
    }

    private void artwork(GuiGraphics g, int x, int y, int size) {
        var cover = Music.cover();
        if (cover != null)
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    cover,
                    x,
                    y,
                    0,
                    0,
                    size,
                    size,
                    128,
                    128,
                    128,
                    128,
                    Px.a(0xFFFFFF, Math.round(Px.fade * 255)));
        else {
            Px.round(g, x, y, size, size, S(6), 0xFF30213F);
            Px.icon(g, "music", x + size / 4, y + size / 4, size / 2, 0xFFCFB5F3);
        }
    }

    private String heardTitle;
    private long pausedAt, playingSince;

    private boolean heard(Music.Track t, long now) {
        if (!t.present()) {
            heardTitle = null;
            playingSince = 0;
            return false;
        }
        if (t.playing()) {
            pausedAt = 0;
            if (t.title().equals(heardTitle)) return true;
            if (playingSince == 0) playingSince = now;
            if (now - playingSince < 1500) return false; // must keep playing, not just blink
            heardTitle = t.title();
            return true;
        }
        playingSince = 0;
        if (!t.title().equals(heardTitle)) return false;
        if (pausedAt == 0) pausedAt = now;
        if (now - pausedAt > 60_000) {
            heardTitle = null;
            return false;
        }
        return true;
    }

    private void wave(GuiGraphics g, int x, int cy, int n, boolean playing) {
        float run = anim.to("wave", playing ? 1 : 0, 7);
        int bw = Math.max(2, S(2)), step = bw + Math.max(1, S(2)), maxH = S(14);
        double t = Theme.animations ? System.nanoTime() / 1e9 : 0;
        for (int i = 0; i < n; i++) {
            double v = .55 + .25 * Math.sin(t * 5.1 + i * .9) + .2 * Math.sin(t * 8.3 - i * 1.7);
            float edge = (float) Math.sin(Math.PI * (i + .5) / n); // lower at the ends, like a waveform
            float level = .14f + .86f * (float) v * (.45f + .55f * edge) * run;
            int bh = Math.max(bw, Math.round(maxH * level) / 2 * 2);
            Px.round(g, x + i * step, cy - bh / 2, bw, bh, bw / 2, Px.a(Theme.accent2(), 120 + Math.round(135 * edge)));
        }
    }

    private int waveW(int n) {
        int bw = Math.max(2, S(2));
        return n * (bw + Math.max(1, S(2))) - Math.max(1, S(2));
    }

    private void equalizer(GuiGraphics g, int x, int y, boolean playing) {
        float run = anim.to("playing", playing ? 1 : 0, 7);
        for (int i = 0; i < 5; i++) {
            float wave =
                    Theme.animations ? (float) (.5 + .5 * Math.sin(System.nanoTime() / 260_000_000.0 + i * 1.05)) : .6f;
            float level = anim.to("eq" + i, .18f + .82f * wave * run, 18);
            int height = Math.max(2, Math.round(S(13) * level));
            Px.round(g, x + i * S(4), y - height / 2, Math.max(2, S(2)), height, 1, Theme.accent2());
        }
    }

    private void stateMark(GuiGraphics g, int cx, int cy, int r, boolean on, float ms) {
        if (!Theme.animations) ms = 2000;
        int col = on ? ON : OFF;
        float pop = Anim.easeOutBack(Math.min(1, ms / 260f));
        int rr = Math.max(2, Math.round(r * pop));
        Px.circle(g, cx, cy, rr, Px.a(col, 40));
        Px.ring(g, cx, cy, rr, Px.a(col, 120));
        float th = Math.max(1.6f, r * .17f);
        if (on) {
            float[][] check = {{-.40f, .02f}, {-.11f, .31f}, {.42f, -.29f}};
            stroke(g, cx, cy, r, check, Anim.easeOutCubic((ms - 120) / 300f), th, col);
        } else {
            float[][] a = {{-.30f, -.30f}, {.30f, .30f}}, b = {{.30f, -.30f}, {-.30f, .30f}};
            stroke(g, cx, cy, r, a, Anim.easeOutCubic((ms - 120) / 200f), th, col);
            stroke(g, cx, cy, r, b, Anim.easeOutCubic((ms - 260) / 200f), th, col);
        }
    }

    private static void stroke(GuiGraphics g, int cx, int cy, int r, float[][] pts, float t, float thick, int col) {
        if (t <= 0) return;
        t = Math.min(1, t);
        float total = 0;
        for (int i = 1; i < pts.length; i++)
            total += (float) Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]) * r;
        float want = total * t, run = 0;
        int dot = Math.max(1, Math.round(thick / 2));
        for (int i = 1; i < pts.length && run < want; i++) {
            float x0 = cx + pts[i - 1][0] * r,
                    y0 = cy + pts[i - 1][1] * r,
                    x1 = cx + pts[i][0] * r,
                    y1 = cy + pts[i][1] * r;
            float len = (float) Math.hypot(x1 - x0, y1 - y0), use = Math.min(len, want - run);
            for (float d = 0; d <= use; d += .6f) {
                float k = len < 1e-3f ? 0 : d / len;
                Px.circle(g, Math.round(x0 + (x1 - x0) * k), Math.round(y0 + (y1 - y0) * k), dot, col);
            }
            run += len;
        }
    }

    private void player(GuiGraphics g, int x, int width, int originX, int originY, boolean interactive) {
        int pad = S(15), art = S(50), tx = x + pad + art + S(12), textW = width - (tx - x) - pad - S(26);
        artwork(g, x + pad, pad, art);
        scrolling(g, media.title(), tx, pad + S(7), textW, Math.max(9, S(16)), Weight.SEMIBOLD, 0xFFF1ECF7);
        scrolling(g, media.artist(), tx, pad + S(30), textW, Math.max(8, S(11)), Weight.MEDIUM, 0xFF9C90AB);
        equalizer(g, x + width - pad - S(18), pad + art / 2, media.playing());
        long pos = Music.position(), duration = media.duration();
        int bx = x + pad, by = pad + art + S(13), length = width - pad * 2, bh = Math.max(2, S(3));
        Px.round(g, bx, by, length, bh, bh / 2, 0xFF3E334A);
        if (duration > 0)
            Px.round(g, bx, by, Math.round(length * Math.min(1, pos / (float) duration)), bh, bh / 2, Theme.accent2());
        int fs = Math.max(8, S(10)), timeY = by + S(8);
        String remaining = duration > 0 ? "−" + Music.time(duration - pos) : "0:00";
        Px.text(g, Music.time(pos), bx, timeY, Weight.MEDIUM, fs, 0xFF9C90AB);
        Px.text(
                g,
                remaining,
                x + width - pad - Px.textWidth(remaining, Weight.MEDIUM, fs),
                timeY,
                Weight.MEDIUM,
                fs,
                0xFF9C90AB);
        var mc = HudElements.mc();
        double mx = mc.mouseHandler.xpos() * Px.width() / mc.getWindow().getScreenWidth(),
                my = mc.mouseHandler.ypos() * Px.height() / mc.getWindow().getScreenHeight();
        int center = x + width / 2, cy = S(129);
        for (int i = 0; i < 3; i++) {
            int size = S(i == 1 ? 30 : 24), left = center + (i - 1) * S(49) - size / 2, top = cy - size / 2;
            buttons[i][0] = originX + left;
            buttons[i][1] = originY + top;
            buttons[i][2] = size;
            buttons[i][3] = size;
            float hover = anim.to("button" + i, interactive && inside(buttons[i], mx, my) ? 1 : 0, 12);
            Px.round(
                    g,
                    left,
                    top - Math.round(S(1) * hover),
                    size,
                    size,
                    size / 2,
                    Px.mix(i == 1 ? 0xFF332344 : 0x00332344, 0xFF594072, hover));
            int colour = Px.mix(0xFFE0D1F2, 0xFFFFFFFF, hover);
            Identifier glyph = Identifier.fromNamespaceAndPath(
                    "arcanicvisuals",
                    "textures/gui/media/" + (i == 0 ? "prev" : i == 2 ? "next" : media.playing() ? "pause" : "play")
                            + ".png");
            int glyphSize = Math.round(size * .52f);
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    glyph,
                    left + (size - glyphSize) / 2,
                    top + (size - glyphSize) / 2,
                    0,
                    0,
                    glyphSize,
                    glyphSize,
                    64,
                    64,
                    64,
                    64,
                    Px.am(colour, Px.fade));
        }
    }

    private void scrolling(GuiGraphics g, String text, int x, int y, int width, int fs, Weight weight, int colour) {
        if (width <= 0) return;
        int overflow = Px.textWidth(text, weight, fs) - width;
        float scroll = overflow > 0 && Theme.animations
                ? overflow
                        * (float) (.5
                                - .5 * Math.cos((System.nanoTime() / 1_000_000_000.0) / Math.max(3, overflow / 22.0)))
                : 0;
        g.enableScissor(x, y - S(2), x + width, y + fs + S(3));
        try {
            g.pose().pushMatrix();
            g.pose().translate(-scroll, 0);
            Px.text(g, text, x, y, weight, fs, colour);
            g.pose().popMatrix();
        } finally {
            g.disableScissor();
        }
    }

    public boolean musicClick(double x, double y, int button) {
        if (!clickable || button != 0 || !enabled() || !music.on()) return false;
        if (expanded && expansion > .8f)
            for (int i = 0; i < 3; i++)
                if (inside(buttons[i], x, y)) {
                    Music.control(i == 0 ? "prev" : i == 1 ? "toggle" : "next");
                    UiSound.play("click");
                    return true;
                }
        if (inside(hit, x, y)) {
            expanded = !expanded;
            UiSound.play("select");
            return true;
        }
        expanded = false;
        return false;
    }
}
