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
import com.mojang.blaze3d.platform.InputConstants.Type;
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
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class EmoteWheel extends Screen {
    private static final int PER_PAGE = 8;
    private final KeyMapping key;
    private List<Emotes.Emote> emotes = List.of();
    private int page;
    private int hot = -1;
    private int lastHot = -2;
    private boolean centre;
    private boolean sticky;
    private final long openedAt = System.currentTimeMillis();
    private long pageAt;
    private final float[] glow = new float[8];
    public float[] devPoint;
    private double vx;
    private double vy;
    private double lastX = Double.NaN;
    private double lastY;
    private double dotX;
    private double dotY;
    private static CompletableFuture<List<Emotes.Emote>> preload;
    private static int warmStep = -1;

    public EmoteWheel(KeyMapping key) {
        super(Component.literal("Эмоции"));
        this.key = key;
    }

    public static void warm(Minecraft mc) {
        if (mc.player != null && Modules.EMOTES.enabled() && warmStep != Integer.MAX_VALUE) {
            if (preload == null) {
                if (mc.player.tickCount >= 40) {
                    preload = CompletableFuture.supplyAsync(Modules.EMOTES::reload);
                }

            } else if (preload.isDone()) {
                List<Emotes.Emote> list = (List) preload.getNow(List.of());
                if (warmStep < 0) {
                    int w = mc.getWindow().getWidth();
                    int h = mc.getWindow().getHeight();
                    int R = Math.max(90, Math.round((float) Math.min(w, h) * 0.19F));
                    int rIn = Math.round((float) R * 0.56F);
                    EmoteWheel.Segments.get(Math.min(8, Math.max(1, list.size())), R, rIn);
                    if (list.size() % 8 != 0 && list.size() > 8) {
                        EmoteWheel.Segments.get(list.size() % 8, R, rIn);
                    }

                    warmStep = 0;
                } else {
                    if (warmStep < list.size()) {
                        Identifier id = ((Emotes.Emote) list.get(warmStep++)).icon();
                        if (id != null) {
                            mc.getTextureManager().getTexture(id);
                        }
                    } else {
                        warmStep = Integer.MAX_VALUE;
                    }
                }
            }
        }
    }

    protected void init() {
        this.emotes = Modules.EMOTES.reload();
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().handle(), 208897, 212995);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    private int pages() {
        return Math.max(1, (this.emotes.size() + 8 - 1) / 8);
    }

    private int onPage() {
        return Math.max(0, Math.min(8, this.emotes.size() - this.page * 8));
    }

    private static float ease(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        float in = ease((float) (now - this.openedAt) / 150.0F);
        float pageIn = ease((float) (now - this.pageAt) / 150.0F);
        Px.begin(g);
        int w = Px.width();
        int h = Px.height();
        int cx = w / 2;
        int cy = h / 2;
        int R = Math.max(90, Math.round((float) Math.min(w, h) * 0.19F));
        int rIn = Math.round((float) R * 0.56F);
        int n = this.onPage();
        double step = n > 0 ? (Math.PI * 2D) / (double) n : 0.0;
        int track = Math.round((float) rIn * 0.74F);
        MouseHandler mouse = Minecraft.getInstance().mouseHandler;
        if (!Double.isNaN(this.lastX)) {
            this.vx += (mouse.xpos() - this.lastX) * 0.8;
            this.vy += (mouse.ypos() - this.lastY) * 0.8;
        }

        this.lastX = mouse.xpos();
        this.lastY = mouse.ypos();
        if (this.devPoint != null) {
            this.vx = (double) (this.devPoint[0] * (float) w - (float) cx);
            this.vy = (double) (this.devPoint[1] * (float) h - (float) cy);
        }

        double len = Math.hypot(this.vx, this.vy);
        if (len > (double) track) {
            this.vx *= (double) track / len;
            this.vy *= (double) track / len;
            len = (double) track;
        }

        this.centre = len < (double) track * 0.4;
        this.hot = n > 0 && !this.centre
                ? Math.floorMod(Math.round((Math.atan2(this.vy, this.vx) + (Math.PI / 2D)) / step), n)
                : -1;
        double tx = this.centre ? this.vx * 0.5 : this.vx / Math.max(1.0E-6, len) * (double) track;
        double ty = this.centre ? this.vy * 0.5 : this.vy / Math.max(1.0E-6, len) * (double) track;
        this.dotX = (double) Draw.approach((float) this.dotX, (float) tx, 0.5F);
        this.dotY = (double) Draw.approach((float) this.dotY, (float) ty, 0.5F);
        int sel = this.centre ? -3 : this.hot;
        if (sel != this.lastHot && sel != -1 && now - this.openedAt > 100L) {
            UiSound.play("hover", 1.0F + (float) Math.max(0, sel) * 0.02F, 0.5F);
        }

        this.lastHot = sel;
        float s = 0.92F + 0.08F * in;
        g.pose().pushMatrix();
        g.pose().translate((float) cx, (float) cy);
        g.pose().scale(s, s);
        g.pose().translate((float) (-cx), (float) (-cy));
        Segment[] segs = n > 0 ? EmoteWheel.Segments.get(n, R, rIn) : new Segment[0];
        int base = Px.am(Theme.bg() & 0xFFFFFF | 0xB8000000, in);
        int accent = Px.am(Theme.accent() & 0xFFFFFF | 0xE0000000, in);
        int icon = Math.min(64, Math.round((float) (R - rIn) * 0.86F));
        int mid = (R + rIn) / 2;
        Emotes.Emote playing = Modules.EMOTES.playing();

        for (int i = 0; i < n; i++) {
            this.glow[i] = Draw.approach(this.glow[i], i == this.hot ? 1.0F : 0.0F, 0.35F);
            double a = (-Math.PI / 2D) + (double) i * step;
            float pop = this.glow[i] * (float) R * 0.045F;
            int ox = Math.round((float) Math.cos(a) * pop);
            int oy = Math.round((float) Math.sin(a) * pop);
            Segment sg = segs[i];
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    sg.id,
                    cx + sg.x + ox,
                    cy + sg.y + oy,
                    0.0F,
                    0.0F,
                    sg.w,
                    sg.h,
                    sg.w,
                    sg.h,
                    sg.w,
                    sg.h,
                    Px.mix(base, accent, this.glow[i]));
            Emotes.Emote e = (Emotes.Emote) this.emotes.get(this.page * 8 + i);
            int ix = cx + (int) Math.round(Math.cos(a) * (double) mid) + ox;
            int iy = cy + (int) Math.round(Math.sin(a) * (double) mid) + oy;
            float alpha = in * pageIn * (0.82F + 0.18F * this.glow[i]);
            if (e.icon() != null) {
                g.blit(
                        RenderPipelines.GUI_TEXTURED,
                        e.icon(),
                        ix - icon / 2,
                        iy - icon / 2,
                        0.0F,
                        0.0F,
                        icon,
                        icon,
                        64,
                        64,
                        64,
                        64,
                        Px.am(-1, alpha));
            } else {
                int px = Px.clampPx(Math.round((float) icon * 0.42F));
                String letter =
                        e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase(Locale.ROOT);
                Px.textCenter(g, letter, ix, iy - px / 2, Px.Weight.BOLD, px, Px.am(-1, alpha));
            }

            if (e == playing) {
                Draw.round(g, ix - 4, iy + icon / 2 + 2, 8, 8, 4, Px.am(-1, alpha));
            }
        }

        int knob = Math.max(4, Math.round((float) R * 0.03F));
        int dxp = cx + (int) Math.round(this.dotX);
        int dyp = cy + (int) Math.round(this.dotY);
        Px.circle(g, dxp, dyp, knob * 2, Px.am(Theme.accent(), in * (this.centre ? 0.25F : 0.45F)));
        Px.circle(g, dxp, dyp, knob, Px.am(-1, in));
        g.pose().popMatrix();
        int big = Px.clampPx(Math.round((float) R * 0.12F));
        int small = Px.clampPx(Math.round((float) R * 0.085F));
        String title = Lang.tr(
                this.hot >= 0
                        ? ((Emotes.Emote) this.emotes.get(this.page * 8 + this.hot)).name()
                        : (n == 0 ? "Пусто" : "Эмоции"));
        int tw = Px.textWidth(title, Px.Weight.SEMIBOLD, big);
        Px.textShadow(g, title, cx - tw / 2, cy - R - big * 2, Px.Weight.SEMIBOLD, big, Px.am(-1, in));
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
                    Px.am(this.centre ? Theme.accent() : 0xCCFFFFFF, in));
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

        if (this.pages() > 1) {
            int dot = Math.max(5, Math.round((float) R * 0.035F));
            int gap = dot;
            int wide = dot * 3;
            int x = cx - ((this.pages() - 1) * (dot + dot) + wide) / 2;
            int y = cy + R + big;

            for (int i = 0; i < this.pages(); i++) {
                int ww = i == this.page ? wide : dot;
                Draw.round(g, x, y, ww, dot, dot / 2, Px.am(i == this.page ? Theme.accent() : 0x99FFFFFF, in));
                x += ww + gap;
            }
        }

        Px.end(g);
    }

    public void tick() {
        if (!this.sticky && !this.held()) {
            if (System.currentTimeMillis() - this.openedAt < 250L) {
                this.sticky = true;
            } else {
                this.choose();
            }
        }
    }

    private boolean held() {
        if (this.key == null) {
            return true;
        } else {
            InputConstants.Key k = KeyBindingHelper.getBoundKeyOf(this.key);
            long win = Minecraft.getInstance().getWindow().handle();
            if (k.getType() == Type.MOUSE) {
                return GLFW.glfwGetMouseButton(win, k.getValue()) == 1;
            } else {
                return k.getValue() >= 0 && GLFW.glfwGetKey(win, k.getValue()) == 1;
            }
        }
    }

    private void choose() {
        int i = this.page * 8 + this.hot;
        boolean stop = this.centre && Modules.EMOTES.playing() != null;
        this.onClose();
        if (stop) {
            UiSound.play("click", 0.9F, 1.0F);
            Modules.EMOTES.stop();
        } else if (this.hot >= 0 && i < this.emotes.size()) {
            UiSound.play("click");
            Modules.EMOTES.play((Emotes.Emote) this.emotes.get(i));
        }
    }

    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0) {
            this.choose();
            return true;
        } else if (e.button() == 1) {
            this.onClose();
            return true;
        } else {
            return super.mouseClicked(e, doubleClick);
        }
    }

    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (this.pages() > 1 && sy != 0.0) {
            this.turn(-((int) Math.signum(sy)));
        }

        return true;
    }

    public void turn(int dir) {
        this.page = Math.floorMod(this.page + dir, this.pages());
        this.pageAt = System.currentTimeMillis();
        Arrays.fill(this.glow, 0.0F);
        UiSound.play("click", 1.1F, 0.5F);
    }

    public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) {
            this.onClose();
            return true;
        } else {
            return super.keyPressed(e);
        }
    }

    private static record Segment(Identifier id, int x, int y, int w, int h) {}

    private static final class Segments {
        private static final Map<Integer, Segment[]> CACHE = new HashMap<>();
        private static int cachedR = -1;
        private static int cachedIn = -1;

        static Segment[] get(int n, int R, int rIn) {
            if (R != cachedR || rIn != cachedIn) {
                clear();
                cachedR = R;
                cachedIn = rIn;
            }

            return (Segment[]) CACHE.computeIfAbsent(n, (k) -> bake(k, R, rIn));
        }

        private static void clear() {
            TextureManager tm = Minecraft.getInstance().getTextureManager();

            for (Segment[] ss : CACHE.values()) {
                for (Segment s : ss) {
                    tm.release(s.id);
                }
            }

            CACHE.clear();
        }

        private static Segment[] bake(int n, int R, int rIn) {
            Segment[] out = new Segment[n];
            double half = Math.PI / (double) n;
            double gap = Math.max(3.0, (double) R * 0.03);
            double round = Math.max(3.0, (double) R * 0.06);

            for (int i = 0; i < n; i++) {
                double a = (-Math.PI / 2D) + (double) i * Math.PI * 2.0 / (double) n;
                double x0 = (double) 1.0E9F;
                double y0 = (double) 1.0E9F;
                double x1 = (double) -1.0E9F;
                double y1 = (double) -1.0E9F;

                for (int k = 0; k <= 16; k++) {
                    double t = a - half + 2.0 * half * (double) k / 16.0;

                    for (double r : new double[] {(double) rIn, (double) R}) {
                        double px = Math.cos(t) * r;
                        double py = Math.sin(t) * r;
                        x0 = Math.min(x0, px);
                        y0 = Math.min(y0, py);
                        x1 = Math.max(x1, px);
                        y1 = Math.max(y1, py);
                    }
                }

                int bx = (int) Math.floor(x0) - 2;
                int by = (int) Math.floor(y0) - 2;
                int bw = (int) Math.ceil(x1) + 2 - bx;
                int bh = (int) Math.ceil(y1) + 2 - by;
                NativeImage img = new NativeImage(bw, bh, true);
                double ca = Math.cos(a);
                double sa = Math.sin(a);

                for (int y = 0; y < bh; y++) {
                    for (int x = 0; x < bw; x++) {
                        double px = (double) (bx + x) + 0.5;
                        double py = (double) (by + y) + 0.5;
                        double u = px * ca + py * sa;
                        double v = Math.abs(-px * sa + py * ca);
                        double d = Math.hypot(px, py);
                        double radial = Math.max((double) rIn - d, d - (double) R);
                        double side = gap / 2.0 - (u * Math.sin(half) - v * Math.cos(half));
                        double qa = radial + round;
                        double qb = side + round;
                        double sdf = Math.min(Math.max(qa, qb), 0.0)
                                + Math.hypot(Math.max(qa, 0.0), Math.max(qb, 0.0))
                                - round;
                        int alpha = (int) Math.round(Math.max(0.0, Math.min(1.0, 0.5 - sdf)) * 255.0);
                        img.setPixel(x, y, alpha << 24 | 0xFFFFFF);
                    }
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
}
