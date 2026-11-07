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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Frost;
import net.arcanic.visuals.render.HudShape;
import net.arcanic.visuals.render.MsdfText;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;

/** A draggable HUD element, drawn in screen pixels. */
public abstract class HudModule extends Module {
    public double fx, fy;
    public int w = 120, h = 30;
    public final Setting.Num scale;
    public final Setting.Bool background;
    protected float k = 1;
    private boolean rescaled; // its scale changed since the last frame

    protected HudModule(String id, String name, String description, boolean enabled, double fx, double fy) {
        super(id, name, description, Category.HUD, enabled);
        this.fx = fx;
        this.fy = fy;
        scale = add(new Setting.Num("scale", "Размер", 1, .5, 3, .01, "×"));
        background = add(new Setting.Bool("bg", "Подложка", true));
    }

    public abstract void render(GuiGraphics g, boolean editing);

    public boolean shown() {
        return true;
    }

    protected int S(double v) {
        return (int) Math.round(v * k);
    }

    public void prepare() {
        float nk = (float) (Px.height() / 1080.0 * scale.get());
        rescaled = Math.abs(nk - k) > 1e-5f;
        k = nk;
    }

    public boolean rescaled() {
        return rescaled;
    }

    public int x(int screenW) {
        return (int) Math.round(fx * screenW);
    }

    public int y(int screenH) {
        return (int) Math.round(fy * screenH);
    }

    protected int U(double v) {
        return S(v * 2);
    }

    // ------------------------------------------------------------------ the kit: Wexside, one to one
    // Wexside's HUD (ru.wexside.util.AbstractHudElement and its elements), with its own numbers. A Wexside unit is
    // two pixels at 1080p, exactly U(1); the w* helpers take units relative to the element's corner. Panels are
    // blurred rounded rectangles (radius 8) in hudBackground with a one-unit notificationOutline rim, the title is
    // Inter Display Bold 8 with an icon glyph at the right, rows are Inter Semibold with Inter Bold badges.

    protected static final String SEMI = "inter_semibold";
    protected static final String BOLD = "inter_bold";
    protected static final String HEAD = "inter_display_bold";
    protected static final String ICONS = "wex_icons";

    // Wexside's dark theme (BuiltInThemes "Dark")
    // the look the user pointed at: quiet dark plates, an icon on an accent tile, values on accent badges
    static final int HUD_BACKGROUND = 0xE6141418;
    static final int NOTIFICATION_OUTLINE = 0x473C3C42;
    static final int HUD_TEXT_PRIMARY = 0xFFF2F3F5;
    static final int HUD_TEXT_MUTED = 0x80FFFFFF;
    static final int SEPARATOR = 0x14FFFFFF;
    static final int CONTROL_FILL = 0x600E1016;
    static final int VISUALIZER_SLOT = 0xC6101114;
    static final int DANGER = 0xFFFF4D68;

    static boolean lightKit() {
        return false;
    }

    static int fillHead() {
        return HUD_BACKGROUND;
    }

    static int fillName() {
        return VISUALIZER_SLOT;
    }

    static int fillValue() {
        return CONTROL_FILL;
    }

    static int ink() {
        return HUD_TEXT_PRIMARY;
    }

    static int inkSoft() {
        return HUD_TEXT_MUTED;
    }

    static int accentInk() {
        return Theme.accent() | 0xFF000000;
    }

    /** Units to local pixels. */
    protected float u(double v) {
        return (float) (v * 2.0 * this.k);
    }

    private int px(double v) {
        return Math.round(this.u(v));
    }

    /** Wexside's FrameInterpolator.lerpTowards: a step of {@code speed} per second of the last frame, at most all. */
    protected static float lerpTowards(float current, float target, float speed) {
        int fps = Minecraft.getInstance().getFps();
        float t = Math.max(0.0F, Math.min(1.0F, (fps > 0 ? 1.0F / fps : 1.0F) * speed));
        return current + (target - current) * t;
    }

    /** A rounded fill in units. */
    protected void wRound(GuiGraphics g, float x, float y, float w, float h, float r, int colour) {
        int x0 = px(x), y0 = px(y);
        int pw = px(x + w) - x0, ph = px(y + h) - y0;
        if (pw <= 0 || ph <= 0) return;
        Px.round(g, x0, y0, pw, ph, Math.min(px(r), Math.min(pw, ph) / 2), colour);
    }

    /** A rounded rim in units, {@code thick} units thick. */
    protected void wOutline(GuiGraphics g, float x, float y, float w, float h, float r, float thick, int colour) {
        int x0 = px(x), y0 = px(y);
        int pw = px(x + w) - x0, ph = px(y + h) - y0;
        int t = Math.max(1, px(thick));
        int rr = Math.min(px(r), Math.min(pw, ph) / 2);
        for (int i = 0; i < t && pw - 2 * i > 0 && ph - 2 * i > 0; i++) {
            Px.outline(g, x0 + i, y0 + i, pw - 2 * i, ph - 2 * i, Math.max(1, rr - i), colour);
        }
    }

    protected void wRect(GuiGraphics g, float x, float y, float w, float h, int colour) {
        int x0 = px(x), y0 = px(y);
        int pw = Math.max(1, px(x + w) - x0), ph = Math.max(1, px(y + h) - y0);
        if (w > 0.0F && h > 0.0F) Px.rect(g, x0, y0, pw, ph, colour);
    }

    /** Wexside's renderPanelSurface: the blur, hudBackground, then the rim in notificationOutline. */
    protected void panelSurface(GuiGraphics g, float x, float y, float w, float h, float r) {
        if (!background.on()) return;
        int x0 = px(x), y0 = px(y);
        int pw = px(x + w) - x0, ph = px(y + h) - y0;
        if (pw <= 0 || ph <= 0) return;
        int rr = Math.min(px(r), Math.min(pw, ph) / 2);
        if (Theme.style != 1 && Frost.prepare()) Frost.backdrop(g, x0, y0, pw, ph, rr);
        Px.round(g, x0, y0, pw, ph, rr, HUD_BACKGROUND);
        this.wOutline(g, x, y, w, h, r, 1.0F, NOTIFICATION_OUTLINE);
    }

    /** Wexside's MsdfFontRenderer draw: {@code y} is the top of the text box, {@code size} the em in units. */
    protected void wText(GuiGraphics g, String face, String s, float x, float y, float size, int colour) {
        if (s.isEmpty()) return;
        if (!MsdfText.ready()) {
            Px.text(g, s, px(x), px(y), Weight.SEMIBOLD, px(size), colour);
            return;
        }
        MsdfText.drawTop(g, face, s, this.u(x), this.u(y), this.u(size), Px.am(colour, Px.fade));
    }

    /** Text width in units. */
    protected static float wWidth(String face, String s, float size) {
        return MsdfText.width(face, s, size);
    }

    /** Text height in units, rounded to half a unit as Wexside's process4 does. */
    protected static float wHeight(String face, float size) {
        return Math.round(MsdfText.lineHeight(face) * size * 2.0F) / 2.0F;
    }

    /** An item drawn {@code size} units square. */
    protected void wItem(GuiGraphics g, ItemStack st, float x, float y, float size) {
        g.pose().pushMatrix();
        g.pose().translate(this.u(x), this.u(y));
        g.pose().scale(this.u(size) / 16.0F, this.u(size) / 16.0F);
        g.renderItem(st, 0, 0);
        g.pose().popMatrix();
    }

    /** A mob effect's texture drawn {@code size} units square. */
    protected void wEffect(GuiGraphics g, Holder<MobEffect> effect, float x, float y, float size) {
        g.pose().pushMatrix();
        g.pose().translate(this.u(x), this.u(y));
        g.pose().scale(this.u(size) / 18.0F, this.u(size) / 18.0F);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect), 0, 0, 18, 18, Px.am(-1, Px.fade));
        g.pose().popMatrix();
    }

    /** Wexside's panel header: the title in Inter Display Bold 8 and the icon glyph at the right edge. */
    protected void header(GuiGraphics g, String title, String glyph, float width, float titleX, float iconFromRight) {
        this.wText(g, HEAD, title, titleX, 4.5F, 8.0F, HUD_TEXT_PRIMARY);
        this.wText(g, ICONS, glyph, width - iconFromRight, 6.0F, 8.0F, HUD_TEXT_PRIMARY);
    }

    // ------------------------------------------------------------------ plates, icon tiles and badges

    /** The accent a little lifted toward white, for text and icons on an accent wash. */
    static int accentText() {
        return Px.mix(Theme.accent() | 0xFF000000, 0xFFFFFFFF, 0.25F);
    }

    /** An icon on its accent tile. */
    protected void iconBox(GuiGraphics g, float x, float y, float size, String icon) {
        this.wRound(g, x, y, size, size, 3.0F, Px.a(Theme.accent() & 0xFFFFFF, 0x42));
        int is = Math.round(this.u(size * 0.62F));
        Px.icon(
                g,
                "w." + icon,
                Math.round(this.u(x + size / 2.0F)) - is / 2,
                Math.round(this.u(y + size / 2.0F)) - is / 2,
                is,
                accentText());
    }

    protected static float badgeWidth2(String text) {
        return wWidth(BOLD, text, 5.5F) + 8.0F;
    }

    /** A value on its badge, its right edge at {@code right}: the accent, or red for a danger. */
    protected void badge2(GuiGraphics g, float right, float y, String text, boolean danger, float a) {
        float bw = badgeWidth2(text);
        float bh = 9.0F;
        float x = right - bw;
        int fill = danger ? 0x38FF4D5A : Px.a(Theme.accent() & 0xFFFFFF, 0x38);
        int ink = danger ? 0xFFFF6B75 : accentText();
        this.wRound(g, x, y, bw, bh, 2.5F, fade(fill, a));
        this.wText(g, BOLD, text, x + 4.0F, y + (bh - wHeight(BOLD, 5.5F)) / 2.0F, 5.5F, fade(ink, a));
    }

    /** The plate's head: the icon tile and the title. */
    protected void headRow(GuiGraphics g, String icon, String title) {
        this.iconBox(g, 3.0F, 2.5F, 10.0F, icon);
        this.wText(g, SEMI, title, 16.0F, (15.0F - wHeight(SEMI, 6.5F)) / 2.0F, 6.5F, HUD_TEXT_PRIMARY);
    }

    protected static float headWidth(String title) {
        return 16.0F + wWidth(SEMI, title, 6.5F) + 8.0F;
    }

    // ------------------------------------------------------------------ the panel kit
    // Lists in the manner the user likes best: a solid dark panel with a soft shadow and a faint rim, the panel's icon
    // in a column at the left, rows 13 tall with the name at the left and the value on a solid accent pill with dark
    // text at the right, a timer ring or bar on the pill that warms to orange and red in the last 20 seconds, and a
    // glare that crosses the HUD from left to right now and then. In our own hand: the client's icons and faces, the
    // icon on a soft inset tile instead of a ruled column, pills in our two accents instead of square badges.

    protected static final String FACE = "sf_pro_display_medium";
    protected static final String FACE_SEMI = "inter_semibold";
    protected static final String FACE_BOLD = "inter_bold";
    protected static final float RADIUS = 5.5F;
    static final int PANEL_TEXT = 0xFFE6E6EA;
    static final int PANEL_MUTED = 0xFF8C8C98;
    private static final int TIMER_WARM = 0xFFFFAA00;
    private static final int TIMER_HOT = 0xFFFF4B4B;
    private static final long GLARE_PERIOD_MS = 3600L;
    private static final long GLARE_SWEEP_MS = 1100L;
    private static final long GLARE_EPOCH = System.currentTimeMillis();
    /** The icon column's width, a row's height and the gap between rows, in units. */
    protected static final float COL = 19.0F;

    protected static final float ROW = 13.0F;
    protected static final float GAP = 1.5F;

    /** The panel's fill: our dark plate with a breath of the accent. */
    static int panelFill() {
        return Px.mix(0xF2121216, Theme.accent() | 0xF2000000, 0.035F);
    }

    /** A dark track inside a panel (bars). */
    static int panelDark() {
        return Px.mix(0xFF0B0B0E, Theme.accent() | 0xFF000000, 0.02F);
    }

    /** The accent lifted toward white. */
    static int accentLift() {
        return Px.mix(accentInk(), 0xFFFFFFFF, 0.25F);
    }

    /** The lower end of our two-tone accent: the accent leaning to the second accent, a little deeper. */
    static int accentDeep() {
        return Px.mix(Px.mix(accentInk(), Theme.accent2() | 0xFF000000, 0.45F), 0xFF000000, 0.1F);
    }

    /** A colour a shade deeper, keeping its alpha. */
    static int deeper(int colour) {
        return colour & 0xFF000000 | Px.mix(colour | 0xFF000000, 0xFF000000, 0.12F) & 0xFFFFFF;
    }

    protected void rRound(GuiGraphics g, float x, float y, float w, float h, float r, int colour) {
        if (HudShape.ready()) HudShape.round(g, this.u(x), this.u(y), this.u(w), this.u(h), this.u(r), colour);
        else this.wRound(g, x, y, w, h, r, colour);
    }

    protected void rRim(GuiGraphics g, float x, float y, float w, float h, float r, float thick, int colour) {
        if (HudShape.ready()) {
            HudShape.rim(g, this.u(x), this.u(y), this.u(w), this.u(h), this.u(r), this.u(thick), colour);
        } else {
            this.wOutline(g, x, y, w, h, r, thick, colour);
        }
    }

    /** A rounded surface running from {@code top} to {@code bottom} colour. */
    protected void rRoundV(GuiGraphics g, float x, float y, float w, float h, float r, int top, int bottom) {
        if (HudShape.ready()) HudShape.roundV(g, this.u(x), this.u(y), this.u(w), this.u(h), this.u(r), top, bottom);
        else this.rRound(g, x, y, w, h, r, top);
    }

    /** A rounded bar running from {@code left} to {@code right} colour. */
    protected void rRoundH(GuiGraphics g, float x, float y, float w, float h, float r, int left, int right) {
        if (HudShape.ready()) HudShape.roundH(g, this.u(x), this.u(y), this.u(w), this.u(h), this.u(r), left, right);
        else this.rRound(g, x, y, w, h, r, left);
    }

    /** Text with the middle of its capitals on {@code cy}, all in units. */
    protected void cText(GuiGraphics g, String face, String s, float x, float cy, float size, int colour) {
        this.wText(g, face, s, x, cy + size * 0.36F - MsdfText.baseline(face, size), size, colour);
    }

    protected static float tWidth(String face, String s, float size) {
        return MsdfText.width(face, s, size);
    }

    /** {@code s} cut with "..." to fit {@code room} units. */
    protected static String fit(String face, String s, float size, float room) {
        if (tWidth(face, s, size) <= room) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (tWidth(face, b.toString() + s.charAt(i) + "...", size) > room) break;
            b.append(s.charAt(i));
        }
        return b + "...";
    }

    /** An icon of the client's own set, {@code size} units square, centred on (cx, cy). */
    protected void icon(GuiGraphics g, String name, float cx, float cy, float size, int colour) {
        int px = Math.max(8, Math.round(this.u(size)));
        Px.icon(g, "w." + name, Math.round(this.u(cx)) - px / 2, Math.round(this.u(cy)) - px / 2, px, colour);
    }

    /** The panel: its soft shadow, a faint frost, the fill and the rim. */
    protected void panel(GuiGraphics g, float x, float y, float w, float h, float r) {
        this.panel(g, x, y, w, h, r, 1.2F);
    }

    protected void panel(GuiGraphics g, float x, float y, float w, float h, float r, float rim) {
        if (!this.background.on()) return;
        int x0 = Math.round(this.u(x)), y0 = Math.round(this.u(y));
        int pw = Math.round(this.u(x + w)) - x0, ph = Math.round(this.u(y + h)) - y0;
        if (pw <= 0 || ph <= 0) return;
        Px.shadow(g, x0, y0 + Math.round(this.u(1.0)), pw, ph, Math.round(this.u(8.0)), 0x4A000000);
        if (Theme.style != 1 && Frost.prepare()) Frost.backdrop(g, x0, y0, pw, ph, Math.round(this.u(r)));
        this.rRound(g, x, y, w, h, r, panelFill());
        this.rRim(g, x, y, w, h, r, rim, 0x26FFFFFF);
    }

    /**
     * The glare band: 1.1 s across the panel every 3.6 s, along one diagonal and then the other. It reaches a panel
     * later the further right the panel sits, so one band crosses the whole HUD.
     */
    protected void glare(GuiGraphics g, float x, float y, float w, float h, float r) {
        if (!this.background.on() || !Theme.animations || !HudShape.ready()) return;
        int sw = Math.max(1, Px.width());
        long lag = Math.round(520.0 * this.x(sw) / sw);
        long t = Math.floorMod(System.currentTimeMillis() - GLARE_EPOCH - lag, GLARE_PERIOD_MS * 2);
        long in = t % GLARE_PERIOD_MS;
        if (in > GLARE_SWEEP_MS) return;
        float k = (float) in / GLARE_SWEEP_MS;
        k = k * k * (3 - 2 * k);
        int colour = Px.a(Px.mix(Theme.accent() | 0xFF000000, 0xFFFFFFFF, 0.55F) & 0xFFFFFF, 0x1C);
        HudShape.glare(
                g,
                this.u(x),
                this.u(y),
                this.u(w),
                this.u(h),
                this.u(r),
                k * 1.6F - 0.3F,
                t >= GLARE_PERIOD_MS,
                colour);
    }

    /** The icon column: a soft inset tile down the left of the panel with the panel's icon in the accent. */
    protected void iconColumn(GuiGraphics g, String icon, float col, float h) {
        float inset = 2.5F;
        this.rRound(g, inset, inset, col - inset * 2.0F, h - inset * 2.0F, RADIUS - 2.0F, 0x0BFFFFFF);
        this.icon(g, icon, col / 2.0F, h / 2.0F, 8.5F, accentInk());
    }

    /** The timer colour: black from 20 s, warming to orange by 15 s and to red by 5 s. */
    protected static int timerColour(float seconds) {
        if (seconds < 0.0F || seconds >= 20.0F) return 0xFF000000;
        if (seconds <= 5.0F) return TIMER_HOT;
        if (seconds <= 15.0F) return Px.mix(TIMER_WARM, TIMER_HOT, (15.0F - seconds) / 10.0F);
        return Px.mix(0xFF000000, TIMER_WARM, (20.0F - seconds) / 5.0F);
    }

    /** How strongly a timer shows that it is running out: 0 at 20 s, 1 from 15 s. */
    protected static float timerEmphasis(float seconds) {
        return seconds < 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, (20.0F - seconds) / 5.0F));
    }

    /** A pill's fill: the accent, darkened toward the timer colour as it runs out. */
    protected static int badgeFill(float seconds, float a) {
        int timer = timerColour(seconds);
        float e = timerEmphasis(seconds);
        int dark = 0xFF000000
                | Math.round((timer >> 16 & 255) * 0.26F) << 16
                | Math.round((timer >> 8 & 255) * 0.26F) << 8
                | Math.round((timer & 255) * 0.26F);
        return Px.am(Px.mix(accentInk(), dark, e), a * (0.84F + 0.1F * e));
    }

    /** Minutes and seconds, the seconds with two digits. */
    protected static String clock(int seconds) {
        int s = Math.max(0, seconds);
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    /** A pill's timer: a ring that empties (effects) or a bar that runs down (cooldowns). */
    public static final int TIMER_NONE = 0, TIMER_RING = 1, TIMER_BAR = 2;

    /**
     * One line of a list panel: an item or effect icon (or none), the name, the pill's text, the timer's kind and how
     * much of it is left (0..1), the seconds left (below 0 for none), dimmed for an inactive bind, and an optional
     * section ("Положительные").
     */
    public record Item(
            String key,
            ItemStack item,
            Holder<MobEffect> effect,
            String name,
            String badge,
            int timer,
            float progress,
            float seconds,
            boolean dim,
            String group) {
        boolean icon() {
            return this.item != null || this.effect != null;
        }

        float badgeWidth() {
            float tw = tWidth(FACE_SEMI, this.badge, 5.8F);
            return switch (this.timer) {
                case TIMER_RING -> Math.max(32.0F, 18.0F + tw);
                case TIMER_BAR -> Math.max(35.0F, 20.0F + tw);
                default -> Math.max(17.0F, tw + 10.0F);
            };
        }

        float width() {
            return 6.0F
                    + (this.icon() ? 12.0F : 0.0F)
                    + tWidth(FACE, this.name, 6.9F)
                    + 5.0F
                    + this.badgeWidth()
                    + 6.0F;
        }
    }

    private final Rows<Item> items = new Rows<>();
    private float listW = 100.0F;
    private float listH = 28.0F;
    private float listShow;
    private long listLast = System.nanoTime();

    protected boolean listing() {
        return !this.items.isEmpty() || this.listShow > 0.01F;
    }

    /**
     * The list panel: the icon column, rows that grow in over 180 ms and shrink away over 350 ms, section labels,
     * the panel easing to its width and springing in with a little overshoot when it appears.
     */
    protected void list(GuiGraphics g, String icon, String title, List<Item> list, float minWidth) {
        long now = System.nanoTime();
        float dt = Math.min(0.0667F, Math.max(0.0F, (now - this.listLast) / 1.0E9F));
        this.listLast = now;
        this.items.sync(list, Item::key, dt);
        float content = 0.0F;
        float widest = 0.0F;
        String group = null;
        boolean first = true;
        for (Rows.Row<Item> r : this.items.rows()) {
            float a = r.visibility;
            if (a < 0.03F) continue;
            Item it = r.item;
            if (it.group() != null && !it.group().equals(group)) {
                if (group != null) content += GAP * a;
                group = it.group();
                content += 8.0F * a;
                first = true;
            }
            if (!first) content += GAP * a;
            content += ROW * a;
            first = false;
            widest = Math.max(widest, it.width());
        }
        float targetW = Math.max(minWidth, COL + Math.max(widest, 6.0F + tWidth(FACE, title, 7.0F) + 8.0F));
        float targetH = content > 0.0F ? content + 10.0F : 27.0F;
        float ease = 1.0F - (float) Math.exp(-14.0F * dt);
        this.listW += (targetW - this.listW) * ease;
        this.listH += (targetH - this.listH) * ease;
        if (Math.abs(targetW - this.listW) < 0.05F || Anim.snap) this.listW = targetW;
        if (Math.abs(targetH - this.listH) < 0.05F || Anim.snap) this.listH = targetH;
        boolean open = !list.isEmpty();
        this.listShow = Math.max(0.0F, Math.min(1.0F, this.listShow + (open ? dt : -dt) / 0.35F));
        float t = this.listShow;
        // in: a back ease out; out: a back ease in, a breath larger before it shrinks away
        float out = 1.0F - t;
        float scale = open ? Anim.easeOutBack(t) : 1.0F - (2.70158F * out * out * out - 1.70158F * out * out);
        if (scale <= 0.01F) return;
        float w = this.listW, h = this.listH;
        this.w = Math.round(this.u(w));
        this.h = Math.round(this.u(h));
        float cx = this.u(w) / 2.0F, cy = this.u(h) / 2.0F;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);
        float base = Px.fade;
        Px.fade = base * Math.min(1.0F, scale * 1.4F);
        try {
            this.panel(g, 0.0F, 0.0F, w, h, RADIUS);
            this.iconColumn(g, icon, COL, h);
            g.enableScissor(0, 0, this.w, this.h);
            float cw = w - COL;
            if (this.items.isEmpty()) this.cText(g, FACE, title, COL + 6.0F, h / 2.0F, 7.0F, PANEL_TEXT);
            float y = 5.0F;
            group = null;
            first = true;
            for (Rows.Row<Item> r : this.items.rows()) {
                float a = r.visibility;
                if (a < 0.03F) continue;
                Item it = r.item;
                if (it.group() != null && !it.group().equals(group)) {
                    if (group != null) y += GAP * a;
                    group = it.group();
                    this.cText(g, FACE, group, COL + 6.0F, y + 4.0F, 5.8F, Px.am(0xFFFFFFFF, 0.45F * a));
                    y += 8.0F * a;
                    first = true;
                }
                if (!first) y += GAP * a;
                this.row(g, it, COL, y, cw, a);
                y += ROW * a;
                first = false;
            }
            g.disableScissor();
            this.glare(g, 0.0F, 0.0F, w, h, RADIUS);
        } finally {
            Px.fade = base;
            g.pose().popMatrix();
        }
    }

    /** One row: the icon, the name, and the pill with its timer and text. */
    private void row(GuiGraphics g, Item it, float x, float y, float width, float a) {
        float cy = y + ROW / 2.0F;
        float nx = x + 6.0F;
        float base = Px.fade;
        if (it.icon()) {
            Px.fade = base * a;
            if (it.effect() != null) this.wEffect(g, it.effect(), nx, cy - 4.0F, 8.0F);
            else this.wItem(g, it.item(), nx, cy - 4.0F, 8.0F);
            Px.fade = base;
            nx += 12.0F;
        }
        float bw = it.badgeWidth();
        float bh = 10.0F;
        float bx = x + width - 6.0F - bw;
        float by = cy - bh / 2.0F;
        String name = fit(FACE, it.name(), 6.9F, Math.max(0.0F, bx - 5.0F - nx));
        this.cText(g, FACE, name, nx, cy, 6.9F, Px.am(PANEL_TEXT, a * (it.dim() ? 0.55F : 1.0F)));
        float tw = tWidth(FACE_SEMI, it.badge(), 5.8F);
        if (it.timer() == TIMER_NONE) {
            float k = a * (it.dim() ? 0.34F : 0.84F);
            this.rRoundV(g, bx, by, bw, bh, bh / 2.0F, Px.am(accentInk(), k), Px.am(accentDeep(), k));
            this.cText(g, FACE_SEMI, it.badge(), bx + (bw - tw) / 2.0F, cy, 5.8F, Px.am(0xFF000000, a * 0.6F));
            return;
        }
        float sec = it.seconds();
        int timer = timerColour(sec);
        float e = timerEmphasis(sec);
        int fill = badgeFill(sec, a);
        this.rRoundV(g, bx, by, bw, bh, bh / 2.0F, fill, deeper(fill));
        float p = Math.max(0.0F, Math.min(1.0F, it.progress()));
        float tx;
        if (it.timer() == TIMER_RING) {
            float rx = bx + 2.5F, ry = by + 1.5F;
            int ring = Px.mix(Px.am(0xFF000000, 0.6F), Px.am(timer, 0.9F), e);
            if (HudShape.ready()) {
                HudShape.ring(g, this.u(rx), this.u(ry), this.u(7.0), this.u(1.2), 1.0F, Px.am(0xFF000000, a * 0.18F));
                if (p > 0.004F) {
                    HudShape.ring(g, this.u(rx), this.u(ry), this.u(7.0), this.u(1.2), p, Px.am(ring, a));
                }
            } else {
                Px.arc(
                        g,
                        this.u(rx + 3.5),
                        this.u(ry + 3.5),
                        this.u(3.5 - 0.6),
                        Math.max(1, Math.round(this.u(1.2))),
                        -Math.PI / 2.0,
                        Math.PI * 2.0 * p,
                        Px.am(ring, a),
                        Px.am(ring, a));
            }
            tx = rx + 7.0F + 2.5F;
        } else {
            float rx = bx + 3.5F, ry = cy - 1.4F;
            this.rRound(g, rx, ry, 9.0F, 2.8F, 1.4F, Px.am(0xFF000000, 0.16F * a));
            int bar = Px.mix(Px.am(0xFF000000, 0.54F), Px.am(timer, 0.9F), e);
            this.rRound(g, rx, ry, Math.max(2.8F, 9.0F * p), 2.8F, 1.4F, Px.am(bar, a));
            tx = rx + 9.0F + 3.0F;
        }
        this.cText(g, FACE_SEMI, it.badge(), tx, cy, 5.8F, Px.am(timer, a * (0.55F + 0.4F * e)));
    }

    /**
     * Animated entries: a new one grows in over 180 ms with a quadratic ease out, one that went shrinks away over
     * 350 ms and is dropped when it is gone; a row on its way out keeps its place.
     */
    protected static final class Rows<T> {
        public static final class Row<T> {
            public T item;
            boolean present = true;
            float t;
            public float visibility;
        }

        private final LinkedHashMap<String, Row<T>> map = new LinkedHashMap<>();

        public void sync(List<T> items, java.util.function.Function<T, String> key) {
            this.sync(
                    items,
                    key,
                    Math.min(
                            0.0667F, 1.0F / Math.max(20, Minecraft.getInstance().getFps())));
        }

        public void sync(List<T> items, java.util.function.Function<T, String> key, float dt) {
            List<String> before = new ArrayList<>(this.map.keySet());
            List<String> order = new ArrayList<>(items.size() + before.size());
            for (Row<T> r : this.map.values()) r.present = false;
            for (T it : items) {
                String k = key.apply(it);
                Row<T> r = this.map.computeIfAbsent(k, x -> new Row<>());
                r.item = it;
                r.present = true;
                order.add(k);
            }
            for (int i = 0; i < before.size(); i++) {
                String k = before.get(i);
                if (!this.map.get(k).present) order.add(Math.min(i, order.size()), k);
            }
            LinkedHashMap<String, Row<T>> ordered = new LinkedHashMap<>();
            for (String k : order) ordered.putIfAbsent(k, this.map.get(k));
            this.map.clear();
            this.map.putAll(ordered);
            Iterator<Row<T>> it = this.map.values().iterator();
            while (it.hasNext()) {
                Row<T> r = it.next();
                r.t = Anim.snap && r.present
                        ? 1.0F
                        : Math.max(0.0F, Math.min(1.0F, r.t + (r.present ? dt / 0.18F : -dt / 0.35F)));
                r.visibility = 1.0F - (1.0F - r.t) * (1.0F - r.t);
                if (!r.present && r.t <= 0.0F) it.remove();
            }
        }

        public Iterable<Row<T>> rows() {
            return this.map.values();
        }

        public boolean isEmpty() {
            return this.map.isEmpty();
        }
    }

    /** Fades a colour by a row's visibility. */
    protected static int fade(int colour, float alpha) {
        return Px.am(colour, alpha);
    }

    /** A header's room: 5 + the title + 19. */
    protected static float titleWidth(String title) {
        return 5.0F + wWidth(HEAD, title, 8.0F) + 19.0F;
    }

    // ------------------------------------------------------------------ pieces the cards that keep their own layout
    // use

    protected void tile(GuiGraphics g, int x, int y, int w, int h, int r, int fill) {
        if (!background.on() || w <= 0 || h <= 0) return;
        Px.round(g, x, y, w, h, Math.min(r, Math.min(w, h) / 2), fill);
    }

    protected void plate(GuiGraphics g, int w, int h) {
        this.panelSurface(g, 0.0F, 0.0F, w / this.u(1.0), h / this.u(1.0), 8.0F);
    }

    protected int kitHeader(GuiGraphics g, String icon, String title, int w) {
        return 0;
    }

    protected int headerWidth(String title) {
        return px(titleWidth(title));
    }

    /** A mob effect's own icon, at any size in pixels. */
    protected static void effectIcon(GuiGraphics g, Holder<MobEffect> effect, int x, int y, int size) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale((float) size / 18.0F, (float) size / 18.0F);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect), 0, 0, 18, 18, Px.am(-1, Px.fade));
        g.pose().popMatrix();
    }

    protected int kitTitleWidth(String title) {
        return this.headerWidth(title);
    }

    protected int badge(GuiGraphics g, String text, int right, int y, boolean filled, int colour) {
        return badge(g, null, text, right, y, filled, colour, 0);
    }
    /** The same pill with a small icon before the text (a clock for timers) and an optional tint (red for harmful). */
    protected int badge(
            GuiGraphics g, String icon, String text, int right, int y, boolean filled, int colour, int tint) {
        int fs = Math.max(8, U(5.5)), h = U(10.5), bw = badgeWidth(icon, text), x = right - bw;
        boolean lt = Theme.p().light();
        if (filled) Px.round(g, x, y, bw, h, h / 2, tint != 0 ? Px.a(tint, 46) : lt ? 0x14000000 : 0x600E1016);
        Px.outline(g, x, y, bw, h, h / 2, tint != 0 ? Px.a(tint, 90) : lt ? 0x22000000 : 0x28FFFFFF);
        int tx = x + U(4);
        if (icon != null) {
            int is = Math.max(8, U(5.5));
            Px.icon(g, "w." + icon, tx, y + (h - is) / 2, is, colour);
            tx += is + U(2);
        }
        Px.text(g, text, tx, y + (h - fs) / 2, Weight.BOLD, fs, colour);
        return bw;
    }

    protected int badgeWidth(String text) {
        return badgeWidth(null, text);
    }

    protected int badgeWidth(String icon, String text) {
        int fs = Math.max(8, U(5.5));
        return Px.textWidth(text, Weight.BOLD, fs) + U(8) + (icon == null ? 0 : Math.max(8, U(5.5)) + U(2));
    }

    protected int header(GuiGraphics g, String icon, String title, int pad) {
        Theme.Palette p = Theme.p();
        Px.icon(g, icon, pad, S(12), S(16), Theme.accent2());
        Px.text(g, Lang.tr(title), pad + S(24), S(12), Weight.SEMIBOLD, S(14), p.text());
        return S(40);
    }

    protected int chip(GuiGraphics g, String text, int right, int y, int h, int minW, int colour) {
        Theme.Palette p = Theme.p();
        int fs = S(11);
        int cw = chipWidth(text, minW);
        Px.round(g, right - cw, y, cw, h, h / 2, Px.a(p.text(), p.light() ? 14 : 9));
        Px.outline(g, right - cw, y, cw, h, h / 2, Px.a(p.text(), p.light() ? 34 : 22));
        if (text.equals("∞")) Px.icon(g, "infinity", right - cw / 2 - S(7), y + (h - S(14)) / 2, S(14), colour);
        else Px.textCenter(g, Lang.tr(text), right - cw / 2, y + (h - fs) / 2, Weight.MEDIUM, fs, colour);
        return cw;
    }

    protected int chipWidth(String text, int minW) {
        return Math.max(minW, Px.textWidth(Lang.tr(text), Weight.MEDIUM, S(11)) + S(18));
    }
}
