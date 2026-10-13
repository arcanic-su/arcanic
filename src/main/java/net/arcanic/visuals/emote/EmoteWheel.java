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
package net.arcanic.visuals.emote;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.render.Draw;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** The emote wheel (B): a hollow ring of sectors around the crosshair. */
public final class EmoteWheel extends Screen {
    private static final int PER_PAGE = 8;
    private final KeyMapping key;
    private List<Emotes.Emote> emotes = List.of();
    private int page, hot = -1, lastHot = -2;
    private boolean centre, sticky;
    private final long openedAt = System.currentTimeMillis();
    private long pageAt;
    private final float[] glow = new float[PER_PAGE];
    public float[] devPoint;
    private double vx, vy, lastX = Double.NaN, lastY, dotX, dotY;

    public EmoteWheel(KeyMapping key) {
        super(Component.literal("Эмоции"));
        this.key = key;
    }

    private static CompletableFuture<List<Emotes.Emote>> preload;
    private static int warmStep = -1;
    /** Loads the emotes, the sector masks and the icons a little at a time, so the wheel opens without a hitch. */
    public static void warm(Minecraft mc) {
        if (mc.player == null || !Modules.EMOTES.enabled() || warmStep == Integer.MAX_VALUE) return;
        if (preload == null) {
            if (mc.player.tickCount >= 40) preload = CompletableFuture.supplyAsync(Modules.EMOTES::reload);
            return;
        }
        if (!preload.isDone()) return;
        List<Emotes.Emote> list = preload.getNow(List.of());
        if (warmStep < 0) {
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            int R = Math.max(90, Math.round(Math.min(w, h) * .19f)), rIn = Math.round(R * .56f);
            Segments.get(Math.min(PER_PAGE, Math.max(1, list.size())), R, rIn);
            if (list.size() % PER_PAGE != 0 && list.size() > PER_PAGE) Segments.get(list.size() % PER_PAGE, R, rIn);
            warmStep = 0;
            return;
        }
        if (warmStep < list.size()) {
            var id = list.get(warmStep++).icon();
            if (id != null) mc.getTextureManager().getTexture(id);
        } else warmStep = Integer.MAX_VALUE;
    }

    @Override
    protected void init() {
        emotes = Modules.EMOTES.reload();
        // no cursor: the mouse only turns the dot (the game grabs the mouse back when the wheel closes)
        GLFW.glfwSetInputMode(
                Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    private int pages() {
        return Math.max(1, (emotes.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private int onPage() {
        return Math.max(0, Math.min(PER_PAGE, emotes.size() - page * PER_PAGE));
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        float in = ease((now - openedAt) / 150f), pageIn = ease((now - pageAt) / 150f);
        Px.begin(g);
        int w = Px.width(), h = Px.height(), cx = w / 2, cy = h / 2;
        int R = Math.max(90, Math.round(Math.min(w, h) * .19f)), rIn = Math.round(R * .56f);
        int n = onPage();
        double step = n > 0 ? Math.PI * 2 / n : 0;

        // mouse movement adds up into a direction, kept within the track so turning back answers at once
        int track = Math.round(rIn * .74f);
        var mouse = Minecraft.getInstance().mouseHandler;
        if (!Double.isNaN(lastX)) {
            vx += (mouse.xpos() - lastX) * .8;
            vy += (mouse.ypos() - lastY) * .8;
        }
        lastX = mouse.xpos();
        lastY = mouse.ypos();
        if (devPoint != null) {
            vx = devPoint[0] * w - cx;
            vy = devPoint[1] * h - cy;
        }
        double len = Math.hypot(vx, vy);
        if (len > track) {
            vx *= track / len;
            vy *= track / len;
            len = track;
        }
        centre = len < track * .4;
        hot = n > 0 && !centre ? (int) Math.floorMod(Math.round((Math.atan2(vy, vx) + Math.PI / 2) / step), n) : -1;
        // the dot runs along the track once a direction is chosen, and rests in the middle otherwise
        double tx = centre ? vx * .5 : vx / Math.max(1e-6, len) * track,
                ty = centre ? vy * .5 : vy / Math.max(1e-6, len) * track;
        dotX = Draw.approach((float) dotX, (float) tx, .5f);
        dotY = Draw.approach((float) dotY, (float) ty, .5f);
        int sel = centre ? -3 : hot;
        if (sel != lastHot && sel != -1 && now - openedAt > 100)
            UiSound.play("hover", 1 + Math.max(0, sel) * .02f, .5f);
        lastHot = sel;

        // the ring grows in from 92 % around its centre
        float s = .92f + .08f * in;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(s, s);
        g.pose().translate(-cx, -cy);

        Segment[] segs = n > 0 ? Segments.get(n, R, rIn) : new Segment[0];
        int base = Px.am(Theme.bg() & 0xFFFFFF | 0xB8000000, in),
                accent = Px.am(Theme.accent() & 0xFFFFFF | 0xE0000000, in);
        // icons are baked at 64 px; drawn at that size when the ring has room, so they stay crisp
        int icon = Math.min(64, Math.round((R - rIn) * .86f)), mid = (R + rIn) / 2;
        Emotes.Emote playing = Modules.EMOTES.playing();
        for (int i = 0; i < n; i++) {
            glow[i] = Draw.approach(glow[i], i == hot ? 1 : 0, .35f);
            double a = -Math.PI / 2 + i * step;
            float pop = glow[i] * R * .045f;
            int ox = Math.round((float) Math.cos(a) * pop), oy = Math.round((float) Math.sin(a) * pop);
            Segment sg = segs[i];
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    sg.id,
                    cx + sg.x + ox,
                    cy + sg.y + oy,
                    0,
                    0,
                    sg.w,
                    sg.h,
                    sg.w,
                    sg.h,
                    sg.w,
                    sg.h,
                    Px.mix(base, accent, glow[i]));

            Emotes.Emote e = emotes.get(page * PER_PAGE + i);
            int ix = cx + (int) Math.round(Math.cos(a) * mid) + ox, iy = cy + (int) Math.round(Math.sin(a) * mid) + oy;
            int size = icon;
            float alpha = in * pageIn * (.82f + .18f * glow[i]);
            if (e.icon() != null) {
                g.blit(
                        RenderPipelines.GUI_TEXTURED,
                        e.icon(),
                        ix - size / 2,
                        iy - size / 2,
                        0,
                        0,
                        size,
                        size,
                        64,
                        64,
                        64,
                        64,
                        Px.am(0xFFFFFFFF, alpha));
            } else {
                int px = Px.clampPx(Math.round(size * .42f));
                String letter =
                        e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase(Locale.ROOT);
                Px.textCenter(g, letter, ix, iy - px / 2, Px.Weight.BOLD, px, Px.am(0xFFFFFFFF, alpha));
            }
            if (e == playing) Draw.round(g, ix - 4, iy + size / 2 + 2, 8, 8, 4, Px.am(0xFFFFFFFF, alpha));
        }
        int knob = Math.max(4, Math.round(R * .03f)),
                dxp = cx + (int) Math.round(dotX),
                dyp = cy + (int) Math.round(dotY);
        Px.circle(g, dxp, dyp, knob * 2, Px.am(Theme.accent(), in * (centre ? .25f : .45f)));
        Px.circle(g, dxp, dyp, knob, Px.am(0xFFFFFFFF, in));
        g.pose().popMatrix();

        // the name above the ring; the hole says "Stop" while a dance plays
        int big = Px.clampPx(Math.round(R * .12f)), small = Px.clampPx(Math.round(R * .085f));
        String title = Lang.tr(hot >= 0 ? emotes.get(page * PER_PAGE + hot).name() : n == 0 ? "Пусто" : "Эмоции");
        int tw = Px.textWidth(title, Px.Weight.SEMIBOLD, big);
        Px.textShadow(g, title, cx - tw / 2, cy - R - big * 2, Px.Weight.SEMIBOLD, big, Px.am(0xFFFFFFFF, in));
        if (playing != null) {
            String stop = Lang.tr("Стоп");
            int sw = Px.textWidth(stop, Px.Weight.MEDIUM, small);
            Px.textShadow(
                    g,
                    stop,
                    cx - sw / 2,
                    cy + knob * 3,
                    Px.Weight.MEDIUM,
                    small,
                    Px.am(centre ? Theme.accent() : 0xCCFFFFFF, in));
        } else if (n == 0) {
            String hint = "config/arcanicvisuals/emotes";
            Px.textShadow(
                    g,
                    hint,
                    cx - Px.textWidth(hint, Px.Weight.MEDIUM, small) / 2,
                    cy - small / 2,
                    Px.Weight.MEDIUM,
                    small,
                    Px.am(0xCCFFFFFF, in));
        }

        // pages: dots under the ring, the current one a short accent pill
        if (pages() > 1) {
            int dot = Math.max(5, Math.round(R * .035f)), gap = dot, wide = dot * 3;
            int x = cx - ((pages() - 1) * (dot + gap) + wide) / 2, y = cy + R + big;
            for (int i = 0; i < pages(); i++) {
                int ww = i == page ? wide : dot;
                Draw.round(g, x, y, ww, dot, dot / 2, Px.am(i == page ? Theme.accent() : 0x99FFFFFF, in));
                x += ww + gap;
            }
        }
        Px.end(g);
    }

    /** One baked sector: its texture and where it sits relative to the wheel centre. */
    private record Segment(Identifier id, int x, int y, int w, int h) {}

    /**
     * Sector masks for the current ring size and count, rebuilt only when either changes (window resize, last page).
     */
    private static final class Segments {
        private static final Map<Integer, Segment[]> CACHE = new HashMap<>();
        private static int cachedR = -1, cachedIn = -1;

        static Segment[] get(int n, int R, int rIn) {
            if (R != cachedR || rIn != cachedIn) {
                clear();
                cachedR = R;
                cachedIn = rIn;
            }
            return CACHE.computeIfAbsent(n, k -> bake(k, R, rIn));
        }

        private static void clear() {
            var tm = Minecraft.getInstance().getTextureManager();
            for (Segment[] ss : CACHE.values()) for (Segment s : ss) tm.release(s.id);
            CACHE.clear();
        }

        private static Segment[] bake(int n, int R, int rIn) {
            Segment[] out = new Segment[n];
            double half = Math.PI / n, gap = Math.max(3, R * .03), round = Math.max(3, R * .06);
            for (int i = 0; i < n; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / n;
                // bounding box of the sector, from its corner points and the outer arc's extremes
                double x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
                for (int k = 0; k <= 16; k++) {
                    double t = a - half + 2 * half * k / 16;
                    for (double r : new double[] {rIn, R}) {
                        double px = Math.cos(t) * r, py = Math.sin(t) * r;
                        x0 = Math.min(x0, px);
                        y0 = Math.min(y0, py);
                        x1 = Math.max(x1, px);
                        y1 = Math.max(y1, py);
                    }
                }
                int bx = (int) Math.floor(x0) - 2,
                        by = (int) Math.floor(y0) - 2,
                        bw = (int) Math.ceil(x1) + 2 - bx,
                        bh = (int) Math.ceil(y1) + 2 - by;
                NativeImage img = new NativeImage(bw, bh, true);
                double ca = Math.cos(a), sa = Math.sin(a);
                for (int y = 0; y < bh; y++)
                    for (int x = 0; x < bw; x++) {
                        double px = bx + x + .5, py = by + y + .5;
                        double u = px * ca + py * sa,
                                v = Math.abs(-px * sa + py * ca); // along / across the sector's middle
                        double d = Math.hypot(px, py);
                        double radial = Math.max(rIn - d, d - R); // > 0 outside the ring
                        double side = gap / 2 - (u * Math.sin(half) - v * Math.cos(half)); // > 0 inside the gap
                        double qa = radial + round, qb = side + round;
                        double sdf =
                                Math.min(Math.max(qa, qb), 0) + Math.hypot(Math.max(qa, 0), Math.max(qb, 0)) - round;
                        int alpha = (int) Math.round(Math.max(0, Math.min(1, .5 - sdf)) * 255);
                        img.setPixel(x, y, alpha << 24 | 0xFFFFFF);
                    }
                Identifier id = Identifier.fromNamespaceAndPath("arcanicvisuals", "emote_wheel/" + n + "_" + i);
                Minecraft.getInstance()
                        .getTextureManager()
                        .register(id, new DynamicTexture(() -> "emote wheel sector", img));
                out[i] = new Segment(id, bx, by, bw, bh);
            }
            return out;
        }
    }

    @Override
    public void tick() {
        // releasing the key picks what the mouse points at; a quick tap keeps the wheel open for a click
        if (sticky || held()) return;
        if (System.currentTimeMillis() - openedAt < 250) sticky = true;
        else choose();
    }

    private boolean held() {
        if (key == null) return true; // opened without a key: stays open
        InputConstants.Key k = KeyBindingHelper.getBoundKeyOf(key);
        long win = Minecraft.getInstance().getWindow().handle();
        if (k.getType() == InputConstants.Type.MOUSE)
            return GLFW.glfwGetMouseButton(win, k.getValue()) == GLFW.GLFW_PRESS;
        return k.getValue() >= 0 && GLFW.glfwGetKey(win, k.getValue()) == GLFW.GLFW_PRESS;
    }

    private void choose() {
        int i = page * PER_PAGE + hot;
        boolean stop = centre && Modules.EMOTES.playing() != null;
        onClose();
        if (stop) {
            UiSound.play("click", .9f, 1);
            Modules.EMOTES.stop();
        } else if (hot >= 0 && i < emotes.size()) {
            UiSound.play("click");
            Modules.EMOTES.play(emotes.get(i));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            choose();
            return true;
        }
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            onClose();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (pages() > 1 && sy != 0) turn(-(int) Math.signum(sy));
        return true;
    }

    public void turn(int dir) {
        page = Math.floorMod(page + dir, pages());
        pageAt = System.currentTimeMillis();
        Arrays.fill(glow, 0);
        UiSound.play("click", 1.1f, .5f);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }
}
