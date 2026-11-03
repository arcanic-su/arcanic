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
package net.arcanic.visuals.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.arcanic.visuals.arc.KnifeStudio;
import net.arcanic.visuals.arc.MeleeDesign;
import net.arcanic.visuals.arc.MeleeSlot;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Keys;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Social;
import net.arcanic.visuals.core.Sync;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.fx.ItemZones;
import net.arcanic.visuals.fx.Studio;
import net.arcanic.visuals.helper.FtApi;
import net.arcanic.visuals.helper.FtEvents;
import net.arcanic.visuals.helper.FunTime;
import net.arcanic.visuals.helper.HolyEvents;
import net.arcanic.visuals.helper.HolyFeatures;
import net.arcanic.visuals.hud.HudRenderer;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.mixin.GuiGraphicsAccess;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.BrandLogo;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class ArcanicMenu extends Screen {
    private static final Category[] TABS;
    static final String[] WARD;
    private static final String[] WARD_ICONS;
    private static Category category;
    private static Page page;
    static int wardrobe;
    private final Anim anim = new Anim();
    private final Map<String, double[]> tweens = new HashMap<>();
    private float u = 1.0F;
    double mx;
    double my;
    long now;
    long openedAt = System.currentTimeMillis();
    long contentAt = System.currentTimeMillis();
    private boolean closing;
    private String search = "";
    private boolean searchFocused;
    Module popMod;
    Module popShown;
    int ancX;
    int ancY;
    int ancW;
    int ancH;
    int pmX;
    int pmY;
    int pmW;
    int pmH;
    private final Map<String, Float> scroll = new HashMap<>();
    private Setting.Mode pop;
    private int popX;
    private int popY;
    private int popW;
    private long popAt;
    private ColourPicker picker;
    Module binding;
    Setting.Key bindingKey;
    String hot;
    String lastHot;
    public static long devNanos;
    public static long devFrames;
    public static long devMax;
    private final List<Region> regions = new ArrayList<>();
    private Region dragging;
    final KnifeStudioPanel knifeStudio = new KnifeStudioPanel(this);
    int px;
    int py;
    int pw;
    int ph;
    int cx0;
    int cw0;
    int top;
    int bottom;
    final WardrobePage wardrobePage = new WardrobePage(this);
    final PresetsPage presetsPage = new PresetsPage(this);
    final FriendsPage friendsPage = new FriendsPage(this);
    final CrosshairPage crosshairPage = new CrosshairPage(this);
    final ModulePopup popup = new ModulePopup(this);
    private static int helperTab;
    // which server the helpers page shows: 0 HolyWorld, 1 FunTime
    private static int helperServer = -1;
    private int contentDx;
    private int contentDy;
    private int pendingDx;
    private int pendingDy;
    private int navPillAt = Integer.MIN_VALUE;
    private final Map<String, Float> scrollShown = new HashMap<>();
    private final Map<String, Float> scrollMax = new HashMap<>();
    final Map<String, Long> pulse = new HashMap<>();
    List<Config.Preset> presetCache;
    private long presetAt;
    private static final int NAV_CATEGORY = 0;
    private static final int NAV_WARDROBE = 1;
    private static final int NAV_PRESETS = 2;
    private static final int NAV_HUD = 3;
    private static final int NAV_UTIL = 4;
    private static final int NAV_LANGUAGE = 6;
    private static final int NAV_STUDIO = 7;
    private static final Nav[][] NAV;
    private static final String[] GROUPS;
    boolean compact;
    private long camDragAt;
    private double camX;
    private double camY;
    boolean bands;
    private final Map<String, Float> dragRaw = new HashMap<>();
    private static final Map<String, ItemStack> ICONS;

    private static Module wardMaster(int i) {
        Object var10000;
        switch (i) {
            case 0 -> var10000 = Modules.CAPES;
            case 1 -> var10000 = Modules.KNIVES;
            case 2 -> var10000 = Modules.GUNS;
            case 3 -> var10000 = Modules.HEADWEAR;
            case 4 -> var10000 = Modules.BACKPACKS;
            case 5 -> var10000 = Modules.WINGS;
            default -> var10000 = null;
        }

        return (Module) var10000;
    }

    /** The kind of melee skin a wardrobe tab lists, or null for the other tabs. */
    static MeleeSlot wardSlot(int i) {
        return switch (i) {
            case 1 -> MeleeSlot.KNIFE;
            case 2 -> MeleeSlot.BOW;
            default -> null;
        };
    }

    /** The menu opened on the Friends page, as the title screen's Friends button does. */
    public static ArcanicMenu friends() {
        page = ArcanicMenu.Page.FRIENDS;
        return new ArcanicMenu();
    }

    public ArcanicMenu() {
        super(Component.literal("Arcanic"));
        if (FunTime.here()) helperServer = 1;
        else if (HolyEvents.onHolyWorld() || helperServer < 0) helperServer = 0;
        UiSound.play("open");
        Toasts.menu(true);
        if (category == Category.COSMETICS || category == Category.COMBAT) {
            category = Category.VISUALS;
        }
    }

    public boolean isPauseScreen() {
        return false;
    }

    int S(double v) {
        return (int) Math.round(v * (double) this.u);
    }

    static Theme.Palette T() {
        return Theme.p();
    }

    static boolean light() {
        return Theme.p().light();
    }

    static int acc() {
        return Theme.accent() | 0xFF000000;
    }

    static int accText() {
        return acc();
    }

    int lt(int lightColour, int darkColour) {
        return light() ? lightColour : darkColour;
    }

    int plate() {
        return cardBg();
    }

    boolean hover(int x, int y, int w, int h) {
        return this.mx >= (double) x
                && this.mx < (double) (x + w)
                && this.my >= (double) y
                && this.my < (double) (y + h);
    }

    float an(String k, float target, float speed) {
        return this.anim.to(k, target, speed);
    }

    static float ease(float t) {
        return Anim.easeOutCubic(Math.max(0.0F, Math.min(1.0F, t)));
    }

    boolean blocked() {
        return this.pop != null || this.picker != null;
    }

    boolean under() {
        return this.blocked();
    }

    private void region(int x, int y, int w, int h, Runnable click) {
        this.regions.add(new Region(x, y, w, h, click, (Runnable) null, (Drag) null, (String) null));
    }

    void clipped(int x, int y, int w, int h, float sc, int t, int b, Runnable click, Runnable alt) {
        int y0 = (int) Math.max((float) t, (float) y - sc);
        int y1 = (int) Math.min((float) b, (float) y - sc + (float) h);
        if (y1 > y0) {
            this.regions.add(new Region(x, y0, w, y1 - y0, click, alt, (Drag) null, (String) null));
        }
    }

    void dragRegion(int x, int y, int w, int h, Drag drag, String id) {
        this.regions.add(new Region(x, y, w, h, (Runnable) null, (Runnable) null, drag, id));
    }

    boolean dragging(String id) {
        return this.dragging != null && id.equals(this.dragging.id());
    }

    static boolean leftHeld() {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), 0) == 1;
    }

    static void glyph(GuiGraphics g, String name, int x, int y, int size, int line) {
        Px.icon(g, Px.hasIcon("a." + name) ? "a." + name : name, x, y, size, line);
    }

    static int panelBg() {
        return light() ? -723722 : -15461353;
    }

    static int cardBg() {
        return light() ? -1 : -15329767;
    }

    static int cardHi() {
        return light() ? -1250065 : -14935008;
    }

    static int field() {
        return light() ? -1644822 : -15066594;
    }

    static int label() {
        return light() ? -14013903 : -2565928;
    }

    static int sub() {
        return light() ? -9803148 : -6908261;
    }

    static int faint() {
        return light() ? -6513498 : -10132111;
    }

    static int edgeSmall() {
        return light() ? -2039579 : -14803422;
    }

    static int edgeMedium() {
        return light() ? -2697508 : -14474456;
    }

    static int textOff() {
        return faint();
    }

    static int tint(float k) {
        return Px.am(acc(), k);
    }

    float tween(String k, float target, float ms) {
        double[] t = (double[]) this.tweens.get(k);
        if (t != null && Theme.animations) {
            double dur = (double) ms / Math.max(0.2, (double) Anim.speedMul);
            double p = Math.min(1.0, ((double) this.now - t[2]) / dur);
            double cur = t[0] + (t[1] - t[0]) * inOut(p);
            if (t[1] != (double) target) {
                t[0] = cur;
                t[1] = (double) target;
                t[2] = (double) this.now;
            }

            return (float) cur;
        } else {
            this.tweens.put(k, new double[] {(double) target, (double) target, (double) this.now});
            return target;
        }
    }

    private static double inOut(double p) {
        return p < 0.5 ? 4.0 * p * p * p : 1.0 - Math.pow(-2.0 * p + 2.0, 3.0) / 2.0;
    }

    void hot(String key) {
        this.hot = key;
    }

    void toggle(GuiGraphics g, int x, int y, int w, int h, float on, float hv) {
        int th = this.S(16.0) / 2 * 2;
        int tw = Math.round((float) th * 1.75F);
        int tx = x + w - tw;
        int ty = y + (h - th) / 2;
        int m = Math.max(2, this.S(2.5));
        int full = th - m * 2;
        int ks = Math.round((float) full - (float) this.S(2.0) * (1.0F - on)) / 2 * 2;
        int off = Px.mix(light() ? -2302750 : -14474456, light() ? -3092266 : -13882318, hv);
        Px.round(g, tx, ty, tw, th, Math.round((float) th * 0.38F), Px.mix(off, acc(), on));
        int kcx = tx + m + full / 2 + Math.round((float) (tw - m * 2 - full) * on);
        int kcy = ty + th / 2;
        Px.round(g, kcx - ks / 2, kcy - ks / 2, ks, ks, ks / 2, Px.mix(Px.mix(faint(), sub(), hv), -1, on));
    }

    void checkbox(GuiGraphics g, int x, int y, int s, float on, float hv) {
        int bs = this.S(16.0);
        int bx = x + s - bs;
        int by = y + (s - bs) / 2;
        int r = this.S(5.0);
        Px.round(g, bx, by, bs, bs, r, Px.mix(field(), light() ? -2565922 : -14277077, hv));
        if (on > 0.001F) {
            float k = Px.fade;
            Px.fade = k * on;
            Px.round(g, bx, by, bs, bs, r, acc());
            int is = this.S(12.0);
            Px.icon(g, "check", bx + (bs - is) / 2, by + (bs - is) / 2, is, -1);
            Px.fade = k;
        }
    }

    private int keycap(GuiGraphics g, String t, int x, int y, int fs) {
        int w = Px.textWidth(t, Px.Weight.MEDIUM, fs) + this.S(10.0);
        int h = fs + this.S(8.0);
        Px.round(g, x, y, w, h, this.S(4.0), cardHi());
        Px.text(g, t, x + this.S(5.0), y + (h - fs) / 2, Px.Weight.MEDIUM, fs, faint());
        return w;
    }

    private int keycapW(String t, int fs) {
        return Px.textWidth(t, Px.Weight.MEDIUM, fs) + this.S(10.0);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        long t0 = System.nanoTime();
        this.draw(g, mouseX, mouseY, pt);
        long dt = System.nanoTime() - t0;
        devNanos += dt;
        ++devFrames;
        devMax = Math.max(devMax, dt);
    }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        if (this.dragging != null && !leftHeld()) {
            this.dragging = null;
            this.dragRaw.clear();
        }

        Modules.PREFS.sync();
        this.anim.frame();
        this.regions.clear();
        this.now = System.currentTimeMillis();
        this.mx = Px.px((double) mouseX);
        this.my = Px.px((double) mouseY);
        int sw = Px.width();
        int sh = Px.height();
        double fit = Math.min((double) sw / 1100.0, (double) sh / 710.0);
        float want = (float) Math.max(
                0.45,
                Math.min(
                        Math.min(2.2, fit),
                        Math.min((double) sh / 1080.0, (double) sw / 1060.0) * Theme.menuScale * 0.86));
        this.u = this.an("menuScale", want, 14.0F);
        float open = this.an("open", this.closing ? 0.0F : 1.0F, this.closing ? 18.0F : 8.0F);
        if (this.closing && open < 0.02F) {
            super.onClose();
        } else {
            float big = this.tween("studioBig", page == ArcanicMenu.Page.STUDIO ? 1.0F : 0.0F, 380.0F);
            float wk = this.tween("wide", Modules.PREFS.wide.on() ? 1.0F : 0.0F, 380.0F);
            int pw0 = Math.min(Math.round((float) this.S(1040.0) + (float) this.S(300.0) * wk), sw - this.S(60.0));
            int ph0 = Math.min(Math.round((float) this.S(620.0) + (float) this.S(130.0) * wk), sh - this.S(90.0));
            if (Modules.PREFS.width.get() > 0 && Modules.PREFS.height.get() > 0) {
                pw0 = clampWidth(S(Modules.PREFS.width.get()), sw);
                ph0 = clampHeight(S(Modules.PREFS.height.get()), sh);
            }
            int pw1 = Math.min(this.S(1680.0), sw - this.S(40.0));
            int ph1 = Math.min(this.S(940.0), sh - this.S(40.0));
            this.pw = Math.round((float) pw0 + (float) (pw1 - pw0) * big);
            this.ph = Math.round((float) ph0 + (float) (ph1 - ph0) * big);
            this.px = (sw - this.pw) / 2;
            this.py = (sh - this.ph) / 2;
            if (KnifeStudio.design != null
                    && (page != ArcanicMenu.Page.WARDROBE || wardSlot(wardrobe) != KnifeStudio.design.getSlot())) {
                KnifeStudio.close();
            }

            float ks = this.tween("knifeStudio", KnifeStudio.design != null ? 1.0F : 0.0F, 380.0F);
            int pair = this.pw + this.S(12.0) + this.knifeStudio.width();
            if (ks > 0.001F && pair <= sw - this.S(20.0)) {
                this.px = Math.round((float) (sw - this.pw) / 2.0F - ks * (float) (pair - this.pw) / 2.0F);
            }

            float t = this.closing
                    ? open
                    : (Theme.animations ? Math.min(1.0F, (float) (this.now - this.openedAt) / 360.0F) : 1.0F);
            float landing = 1.0F - (float) Math.pow((double) (1.0F - t), 5.0);
            float grow = 0.95F + 0.05F * landing;
            float rise = (1.0F - landing) * (float) this.S(12.0);
            Px.begin(g);
            float keep = Px.fade;
            Px.rect(g, 0, 0, sw, sh, Px.a(328966, (int)
                    (255.0 * (Double) Modules.PREFS.dim.get() / 100.0 * (double) open)));
            Px.fade = keep * Math.min(1.0F, open * 1.3F);
            g.nextStratum();
            float ccx = (float) this.px + (float) this.pw / 2.0F;
            float ccy = (float) this.py + (float) this.ph / 2.0F;
            float base = Px.fade;
            g.pose().pushMatrix();
            g.pose().translate(ccx, ccy + (float) Math.round(rise));
            g.pose().scale(grow, grow);
            g.pose().translate(-ccx, -ccy);
            this.panel(g);
            g.pose().popMatrix();
            Px.fade = base;
            g.nextStratum();
            this.knifeStudio.draw(g);
            g.nextStratum();
            this.popup.modulePopup(g);
            g.nextStratum();
            if (this.pop != null) {
                this.popover(g);
            }

            g.nextStratum();
            this.tooltip(g, sw, sh);
            Px.fade = keep;
            if (this.picker != null) {
                g.nextStratum();
                this.picker.render(g, Px.px((double) mouseX), Px.px((double) mouseY));
                if (this.picker.finished()) {
                    this.picker = null;
                }
            }

            Px.end(g);
            HudRenderer.menuOverlay(g);
            if (this.hot != null
                    && !this.hot.equals(this.lastHot)
                    && this.now - this.openedAt > 250L
                    && !this.closing) {
                UiSound.play("hover", 1.0F + (float) (this.hot.hashCode() % 7) * 0.015F, 1.0F);
            }

            this.lastHot = this.hot;
            this.hot = null;
        }
    }

    // ------------------------------------------------------------------ the hover hint on a module card

    private Module tipNow;
    private Module tipLast;
    private Module tipShown;
    private long tipAt;

    /**
     * What a function does, when the pointer rests on its card: a small card at the cursor with the name and the
     * description, rising in after a short pause and fading out as the pointer leaves.
     */
    private void tooltip(GuiGraphics g, int sw, int sh) {
        Module m = this.tipNow;
        this.tipNow = null;
        if (m != this.tipLast) {
            this.tipLast = m;
            this.tipAt = this.now;
        }
        boolean want = m != null
                && this.now - this.tipAt > 420L
                && !this.blocked()
                && !this.closing
                && this.dragging == null
                && this.binding == null;
        float k = this.an("tip", want ? 1.0F : 0.0F, want ? 12.0F : 24.0F);
        if (want) this.tipShown = m;
        Module shown = this.tipShown;
        if (shown == null || k < 0.01F) return;
        String desc = Lang.tr(shown.description);
        if (desc.isBlank()) return;
        int pad = this.S(12.0);
        int maxW = this.S(300.0);
        int tfs = this.S(13.0);
        int dfs = this.S(12.0);
        int lh = this.S(17.0);
        List<String> lines = PromptScreen.wrap(desc, Px.Weight.MEDIUM, dfs, maxW - pad * 2);
        String title = shown.title();
        int textW = Px.textWidth(title, Px.Weight.SEMIBOLD, tfs);
        for (String l : lines) textW = Math.max(textW, Px.textWidth(l, Px.Weight.MEDIUM, dfs));
        String key = shown.bind >= 0 ? Lang.tr("Клавиша") + "  " + Keys.label(shown.bind) : null;
        int w = Math.min(maxW, textW + pad * 2);
        int h = pad + tfs + this.S(7.0) + lines.size() * lh + (key != null ? this.S(6.0) + lh : 0) + pad - this.S(3.0);
        int x = (int) this.mx + this.S(16.0);
        int y = (int) this.my + this.S(18.0);
        if (x + w > sw - this.S(8.0)) x = (int) this.mx - w - this.S(12.0);
        if (y + h > sh - this.S(8.0)) y = (int) this.my - h - this.S(10.0);
        x = Math.max(this.S(8.0), x);
        y = Math.max(this.S(8.0), y) + Math.round((1.0F - k) * this.S(5.0));
        float base = Px.fade;
        Px.fade = base * k;
        int r = this.S(10.0);
        Px.shadow(g, x, y + this.S(6.0), w, h, this.S(18.0), Px.a(0, light() ? 28 : 110));
        Px.round(g, x, y, w, h, r, cardBg());
        Px.outline(g, x, y, w, h, r, hairline(22));
        this.topLight(g, x, y, w, r);
        int ty = y + pad;
        Px.text(g, title, x + pad, ty, Px.Weight.SEMIBOLD, tfs, T().text());
        ty += tfs + this.S(7.0);
        for (String l : lines) {
            Px.text(g, l, x + pad, ty, Px.Weight.MEDIUM, dfs, sub());
            ty += lh;
        }
        if (key != null) {
            ty += this.S(6.0);
            Px.text(g, key, x + pad, ty, Px.Weight.MEDIUM, this.S(11.0), faint());
        }
        Px.fade = base;
    }

    int glass() {
        return panelBg();
    }

    static int chrome() {
        return light() ? -1315857 : -15000801;
    }

    static int hairline(int alpha) {
        return light() ? Px.a(0, alpha + 8) : Px.a(0xFFFFFF, alpha);
    }

    void topLight(GuiGraphics g, int x, int y, int w, int r) {
        if (!light()) {
            Px.rect(g, x + r, y, w - 2 * r, Math.max(1, this.S(1.0)), 352321535);
        }
    }

    private void panel(GuiGraphics g) {
        int R = this.S(14.0);
        int side = this.S(196.0);
        Px.shadow(g, this.px, this.py + this.S(12.0), this.pw, this.ph, this.S(40.0), Px.a(0, light() ? 30 : 150));
        Px.round(g, this.px, this.py, this.pw, this.ph, R, panelBg());
        Px.round(g, this.px, this.py, side + R, this.ph, R, chrome());
        Px.rect(g, this.px + side, this.py, R, this.ph, panelBg());
        Px.outline(g, this.px, this.py, this.pw, this.ph, R, hairline(14));
        g.nextStratum();
        this.sidebar(g, this.px, this.py, side, this.ph);
        int sx = this.px + side;
        int sy = this.py;
        int sw = this.pw - side;
        int sh = this.ph;
        g.nextStratum();
        int pad = this.S(24.0);
        int hy = sy + this.S(18.0);
        int hh = this.S(34.0);
        this.cx0 = sx + pad;
        this.cw0 = sw - pad * 2;
        this.header(g, this.cx0, hy, this.cw0, hh);
        this.top = hy + hh + this.S(14.0);
        this.bottom = sy + sh - this.S(10.0);
        g.nextStratum();
        float ct = Theme.animations
                ? (float) (1.0
                        - Math.pow((double) (1.0F - Math.min(1.0F, (float) (this.now - this.contentAt) / 380.0F)), 4.0))
                : 1.0F;
        g.pose().pushMatrix();
        g.pose().translate((float) Math.round((float) (this.contentDx * this.S(26.0)) * (1.0F - ct)), (float)
                Math.round((float) (this.contentDy * this.S(16.0)) * (1.0F - ct)));
        switch (page.ordinal()) {
            case 0 -> this.rows(g, this.visible(), "c:" + String.valueOf(this.search.isEmpty() ? category : "search"));
            case 1 -> this.wardrobePage.wardrobe(g);
            case 2 -> this.presetsPage.presets(g);
            case 3 -> this.studio(g);
            case 4 -> this.helpers(g);
            case 5 -> this.friendsPage.friends(g);
            case 6 -> this.crosshairPage.studio(g);
        }

        g.pose().popMatrix();
        if (page != ArcanicMenu.Page.STUDIO) resizeGrip(g);
    }

    private static final int MIN_W = 860;
    private static final int MIN_H = 540;
    private double grabX;
    private double grabY;
    private boolean grabbed;

    private int clampWidth(int w, int screen) {
        return Math.max(Math.min(S(MIN_W), screen - S(40)), Math.min(w, screen - S(40)));
    }

    private int clampHeight(int h, int screen) {
        return Math.max(Math.min(S(MIN_H), screen - S(40)), Math.min(h, screen - S(40)));
    }

    /**
     * Bottom-right corner handle. The panel stays centred, so the new size is twice the distance from the centre to
     * the cursor; the grab offset keeps the corner from jumping to the cursor on the first move.
     */
    private void resizeGrip(GuiGraphics g) {
        int size = S(18);
        int gx = px + pw - size - S(4);
        int gy = py + ph - size - S(4);
        boolean active = dragging("menu:resize");
        if (!active) grabbed = false;
        float k = an("resizeGrip", active ? 1 : (!blocked() && hover(gx, gy, size, size) ? 0.7f : 0), 18);
        int line = Px.mix(faint(), acc(), k);
        int th = Math.max(1, S(1.5));
        for (int i = 1; i <= 3; i++) {
            int len = Math.round(size * i / 3.4f);
            for (int j = 0; j < len; j += th) {
                Px.rect(g, gx + size - len + j, gy + size - j - th, th, th, line);
            }
        }
        if (k > 0.05f) hot("menu:resize");

        float cx = px + pw / 2f;
        float cy = py + ph / 2f;
        regions.add(new Region(
                gx,
                gy,
                size + S(4),
                size + S(4),
                null,
                () -> {
                    Modules.PREFS.width.set(0.0);
                    Modules.PREFS.height.set(0.0);
                    Config.dirty();
                    UiSound.play("reset");
                },
                (x, y) -> {
                    if (!grabbed) {
                        grabX = px + pw - x;
                        grabY = py + ph - y;
                        grabbed = true;
                    }
                    int sw = Px.width();
                    int sh = Px.height();
                    int w = clampWidth((int) Math.round((x + grabX - cx) * 2), sw);
                    int h = clampHeight((int) Math.round((y + grabY - cy) * 2), sh);
                    Modules.PREFS.width.set(w / (double) u);
                    Modules.PREFS.height.set(h / (double) u);
                    Config.dirty();
                },
                "menu:resize"));
    }

    private void helpers(GuiGraphics g) {
        if (helperServer == 1) {
            if (helperTab == 0) {
                this.rows(g, Modules.of(Category.FUNTIME), "c:ft");
            } else {
                this.ftEventsList(g, this.cx0, this.cw0, this.top, this.bottom);
            }
        } else if (helperTab == 0) {
            this.rows(g, Modules.of(Category.HOLYWORLD), "c:hw");
        } else {
            this.eventsList(g, this.cx0, this.cw0, this.top, this.bottom);
        }
    }

    /** The HolyWorld / FunTime switch in the helpers page's header; returns where it starts. */
    private int serverSwitch(GuiGraphics g, int right, int y, int h, int fs) {
        String[] names = {"HolyWorld", "FunTime"};
        int pad = this.S(12.0);
        int[] ws = new int[names.length];
        int total = this.S(6.0);
        for (int i = 0; i < names.length; i++) {
            ws[i] = Px.textWidth(names[i], Px.Weight.MEDIUM, fs) + pad * 2;
            total += ws[i];
        }
        int x0 = right - total;
        Px.round(g, x0, y, total, h, this.S(8.0), field());
        int cx = x0 + this.S(3.0);
        int ih = h - this.S(6.0);
        int iy = y + this.S(3.0);
        int[] xs = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            xs[i] = cx;
            cx += ws[i];
        }
        int px = Math.round(this.tween("srvX", (float) xs[helperServer == 1 ? 1 : 0], 200.0F));
        int pw = Math.round(this.tween("srvW", (float) ws[helperServer == 1 ? 1 : 0], 200.0F));
        Px.round(g, px, iy, pw, ih, this.S(6.0), light() ? -1 : -14013904);
        for (int i = 0; i < names.length; i++) {
            boolean on = (helperServer == 1 ? 1 : 0) == i;
            boolean hv = !this.under() && this.hover(xs[i], y, ws[i], h);
            if (hv && !on) this.hot("srv:" + i);
            float hk = this.an("srv:" + i, hv ? 1.0F : 0.0F, 18.0F);
            Px.textCenter(
                    g,
                    names[i],
                    xs[i] + ws[i] / 2,
                    y + (h - fs) / 2,
                    Px.Weight.MEDIUM,
                    fs,
                    on ? T().text() : Px.mix(faint(), label(), hk));
            if (!on && !this.under()) {
                int pick = i;
                this.region(xs[i], y, ws[i], h, () -> {
                    helperServer = pick;
                    helperTab = 0;
                    UiSound.play("click");
                    this.content("tab");
                });
            }
        }
        return x0;
    }

    /** Every FunTime anarchy's events from funtime.me, in the same cards as HolyWorld's list. */
    private void ftEventsList(GuiGraphics g, int x, int w, int t, int b) {
        FtEvents ev = Modules.FT_EVENTS;
        boolean onFt = FunTime.here();
        List<FtApi.Event> list = ev.enabled() ? FtApi.events() : List.of();
        int n = list.size();
        if (n == 0) {
            if (!ev.enabled()) {
                this.emptyNote(g, "Ивенты выключены", "Включи их, и здесь будут ивенты всех анархий", ev);
            } else if (!onFt) {
                this.emptyNote(
                        g, "Ты не на FunTime", "Зайди на сервер, и здесь будут ивенты всех анархий", (Module) null);
            } else {
                this.emptyNote(g, "Сейчас ивентов нет", "Новые появятся здесь сами", (Module) null);
            }
            return;
        }
        int rh = this.S(48.0);
        int gap = this.S(6.0);
        int total = n * (rh + gap) + this.S(4.0);
        float sc = this.scrolled("ftev", total, t, b);
        g.enableScissor(x - this.S(12.0), t, x + w + this.S(12.0), b);
        int here = FunTime.anarchy();
        int bs = this.S(30.0);
        int rightX = x + w - this.S(12.0) - bs - this.S(18.0);
        boolean wide = w >= this.S(560.0);
        int whereW = this.S(170.0);
        int rareW = wide ? this.S(150.0) : 0;
        int whereX = rightX - whereW;
        int rareX = whereX - rareW;
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; ++i) {
            FtApi.Event e = list.get(i);
            int ry = Math.round((float) (t + this.S(2.0) + i * (rh + gap)) - sc);
            if (ry + rh < t - this.S(10.0) || ry > b + this.S(10.0)) continue;
            String k = "ftev:" + e.anarchy() + e.id() + i;
            float app =
                    ease(Math.max(0.0F, (float) (this.now - this.contentAt - (long) Math.min(i, 14) * 14L) / 240.0F));
            float kf = Px.fade;
            Px.fade = kf * app;
            ry += Math.round((1.0F - app) * (float) this.S(6.0));
            boolean mine = e.anarchy() == here;
            boolean go = !mine && onFt;
            boolean hv = !this.under() && this.my >= (double) t && this.my < (double) b && this.hover(x, ry, w, rh);
            if (hv && go) this.hot(k);
            float hk = this.an(k, hv && go ? 1.0F : 0.0F, 18.0F);
            int R = this.S(10.0);
            Px.round(g, x, ry, w, rh, R, Px.mix(cardBg(), cardHi(), hk));
            Px.outline(g, x, ry, w, rh, R, mine ? Px.a(acc(), 150) : Px.mix(hairline(14), hairline(30), hk));
            int ts = this.S(32.0);
            int tx = x + this.S(9.0);
            int ty = ry + (rh - ts) / 2;
            int hs = this.S(28.0);
            Px.round(g, tx, ty, ts, ts, this.S(8.0), field());
            Identifier head = FtApi.picture(e.id());
            if (head != null) {
                HolyEvents.picture(g, head, 38, 38, tx + (ts - hs) / 2, ty + (ts - hs) / 2, hs, hs);
            } else {
                int ds = this.S(9.0);
                Px.round(g, tx + (ts - ds) / 2, ty + (ts - ds) / 2, ds, ds, ds / 2, FtApi.lootColour(e.loot()));
            }
            int nx = tx + ts + this.S(12.0);
            int nfs = this.S(13.0);
            int room = (wide ? rareX : whereX) - this.S(12.0) - nx;
            String name = Px.ellipsize(e.name(), Px.Weight.SEMIBOLD, nfs, room - (e.live() ? this.S(12.0) : 0));
            Px.text(g, name, nx, ry + (rh - nfs) / 2, Px.Weight.SEMIBOLD, nfs, T().text());
            if (e.live()) {
                int fd = this.S(6.0);
                Px.round(
                        g,
                        nx + Px.textWidth(name, Px.Weight.SEMIBOLD, nfs) + this.S(7.0),
                        ry + (rh - fd) / 2,
                        fd,
                        fd,
                        fd / 2,
                        acc());
            }
            int lfs = this.S(12.0);
            int ly = ry + (rh - lfs) / 2;
            if (wide && !e.loot().isEmpty()) {
                int ds = this.S(7.0);
                Px.round(g, rareX, ry + (rh - ds) / 2, ds, ds, ds / 2, FtApi.lootColour(e.loot()));
                Px.text(
                        g,
                        Px.ellipsize(e.loot(), Px.Weight.MEDIUM, lfs, rareW - ds - this.S(16.0)),
                        rareX + ds + this.S(8.0),
                        ly,
                        Px.Weight.MEDIUM,
                        lfs,
                        sub());
            }
            String when = e.phase();
            if (e.endsAt() > now) {
                long sec = (e.endsAt() - now) / 1000L;
                String left = sec >= 60 ? sec / 60 + ":" + String.format("%02d", sec % 60) : sec + " сек";
                when = when.isEmpty() ? left : when + " · " + left;
            }
            String where = Lang.tr("Анархия") + " " + e.anarchy() + (when.isEmpty() ? "" : " · " + when);
            Px.text(
                    g,
                    Px.ellipsize(where, Px.Weight.MEDIUM, lfs, whereW - this.S(8.0)),
                    whereX,
                    ly,
                    Px.Weight.MEDIUM,
                    lfs,
                    faint());
            int bx = x + w - this.S(12.0) - bs;
            int by = ry + (rh - bs) / 2;
            if (mine) {
                String you = Lang.tr("ты здесь");
                int yw = Px.textWidth(you, Px.Weight.SEMIBOLD, this.S(10.0)) + this.S(16.0);
                int yh = this.S(20.0);
                Px.round(g, x + w - this.S(12.0) - yw, ry + (rh - yh) / 2, yw, yh, yh / 2, Px.a(acc(), 60));
                Px.textCenter(
                        g,
                        you,
                        x + w - this.S(12.0) - yw / 2,
                        ry + (rh - this.S(10.0)) / 2,
                        Px.Weight.SEMIBOLD,
                        this.S(10.0),
                        Px.mix(acc(), -1, 0.45F));
            } else if (onFt) {
                Px.round(g, bx, by, bs, bs, bs / 2, Px.mix(field(), acc(), hk));
                int as = this.S(13.0);
                glyph(g, "chevron-right", bx + (bs - as) / 2, by + (bs - as) / 2, as, Px.mix(sub(), -1, hk));
            }
            if (!this.under() && app > 0.3F && go) {
                this.clipped(
                        x,
                        ry,
                        w,
                        rh,
                        0.0F,
                        t,
                        b,
                        () -> {
                            UiSound.play("click");
                            FtApi.join(e);
                        },
                        (Runnable) null);
            }
            Px.fade = kf;
        }
        g.disableScissor();
        this.scrollbar(g, x + w + this.S(14.0), t, b, total, sc, "ftev");
    }

    private static String events(int n) {
        return n + " "
                + (n % 10 == 1 && n % 100 != 11
                        ? "ивент"
                        : (n % 10 < 2 || n % 10 > 4 || n % 100 >= 12 && n % 100 <= 14 ? "ивентов" : "ивента"));
    }

    private static int rarityColour(String r) {
        int var10000;
        switch (r) {
            case "Босс":
                var10000 = -50373;
                break;
            case "Легендарный":
            case "Мифический":
            case "Роскошный":
                var10000 = -19641;
                break;
            case "Эпический":
            case "Зажиточный":
                var10000 = -5141249;
                break;
            case "Редкий":
                var10000 = -9518593;
                break;
            case "Взрывной":
            case "Смертельный":
                var10000 = -38037;
                break;
            case "Мирный":
                var10000 = -8658784;
                break;
            default:
                var10000 = -7696222;
        }

        return var10000;
    }

    private void eventsList(GuiGraphics g, int x, int w, int t, int b) {
        HolyEvents ev = Modules.HOLY_EVENTS;
        boolean onHw = HolyEvents.onHolyWorld();
        List<HolyEvents.Event> list = ev.enabled() ? ev.sorted() : List.of();
        int n = list.size();
        if (n == 0) {
            if (!ev.enabled()) {
                this.emptyNote(g, "Ивенты выключены", "Включи их, и здесь будут ивенты всех анархий", ev);
            } else if (!onHw) {
                this.emptyNote(
                        g, "Ты не на HolyWorld", "Зайди на сервер, и здесь будут ивенты всех анархий", (Module) null);
            } else {
                this.emptyNote(g, "Сейчас ивентов нет", "Новые появятся здесь сами", (Module) null);
            }

        } else {
            int rh = this.S(48.0);
            int gap = this.S(6.0);
            int total = n * (rh + gap) + this.S(4.0);
            float sc = this.scrolled("hwev", total, t, b);
            g.enableScissor(x - this.S(12.0), t, x + w + this.S(12.0), b);
            String here = ev.here();
            int bs = this.S(30.0);
            int rightX = x + w - this.S(12.0) - bs - this.S(18.0);
            boolean wide = w >= this.S(560.0);
            int whereW = this.S(150.0);
            int rareW = wide ? this.S(130.0) : 0;
            int whereX = rightX - whereW;
            int rareX = whereX - rareW;

            for (int i = 0; i < n; ++i) {
                HolyEvents.Event e = (HolyEvents.Event) list.get(i);
                int ry0 = t + this.S(2.0) + i * (rh + gap);
                int ry = Math.round((float) ry0 - sc);
                if (ry + rh >= t - this.S(10.0) && ry <= b + this.S(10.0)) {
                    String var10000 = e.server();
                    String k = var10000 + e.id();
                    float app = ease(Math.max(
                            0.0F, (float) (this.now - this.contentAt - (long) Math.min(i, 14) * 14L) / 240.0F));
                    float kf = Px.fade;
                    Px.fade = kf * app;
                    ry += Math.round((1.0F - app) * (float) this.S(6.0));
                    boolean mine = e.server().equals(here);
                    boolean fresh = ev.fresh(e);
                    boolean go = !mine && onHw;
                    boolean hv =
                            !this.under() && this.my >= (double) t && this.my < (double) b && this.hover(x, ry, w, rh);
                    if (hv && go) {
                        this.hot("ev:" + k);
                    }

                    float hk = this.an("ev:" + k, hv && go ? 1.0F : 0.0F, 18.0F);
                    int R = this.S(10.0);
                    Px.round(g, x, ry, w, rh, R, Px.mix(cardBg(), cardHi(), hk));
                    Px.outline(g, x, ry, w, rh, R, mine ? Px.a(acc(), 150) : Px.mix(hairline(14), hairline(30), hk));
                    int ts = this.S(32.0);
                    int tx = x + this.S(9.0);
                    int ty = ry + (rh - ts) / 2;
                    int hs = this.S(26.0);
                    Px.round(g, tx, ty, ts, ts, this.S(8.0), field());
                    Identifier head = HolyEvents.head(e.type());
                    if (head != null) {
                        HolyEvents.picture(g, head, 38, 38, tx + (ts - hs) / 2, ty + (ts - hs) / 2, hs, hs);
                    } else {
                        int ds = this.S(9.0);
                        Px.round(g, tx + (ts - ds) / 2, ty + (ts - ds) / 2, ds, ds, ds / 2, rarityColour(e.rarity()));
                    }

                    int nx = tx + ts + this.S(12.0);
                    int nfs = this.S(13.0);
                    int room = (wide ? rareX : whereX) - this.S(12.0) - nx;
                    String name = Px.ellipsize(e.name(), Px.Weight.SEMIBOLD, nfs, room - (fresh ? this.S(12.0) : 0));
                    Px.text(g, name, nx, ry + (rh - nfs) / 2, Px.Weight.SEMIBOLD, nfs, T().text());
                    if (fresh) {
                        int fd = this.S(6.0);
                        Px.round(
                                g,
                                nx + Px.textWidth(name, Px.Weight.SEMIBOLD, nfs) + this.S(7.0),
                                ry + (rh - fd) / 2,
                                fd,
                                fd,
                                fd / 2,
                                acc());
                    }

                    int lfs = this.S(12.0);
                    int ly = ry + (rh - lfs) / 2;
                    if (wide && !e.rarity().isEmpty()) {
                        int ds = this.S(7.0);
                        Px.round(g, rareX, ry + (rh - ds) / 2, ds, ds, ds / 2, rarityColour(e.rarity()));
                        Px.text(
                                g,
                                Px.ellipsize(e.rarity(), Px.Weight.MEDIUM, lfs, rareW - ds - this.S(16.0)),
                                rareX + ds + this.S(8.0),
                                ly,
                                Px.Weight.MEDIUM,
                                lfs,
                                sub());
                    }

                    Px.text(
                            g,
                            Px.ellipsize(ev.serverName(e.server()), Px.Weight.MEDIUM, lfs, whereW - this.S(8.0)),
                            whereX,
                            ly,
                            Px.Weight.MEDIUM,
                            lfs,
                            faint());
                    int bx = x + w - this.S(12.0) - bs;
                    int by = ry + (rh - bs) / 2;
                    if (mine) {
                        String you = Lang.tr("ты здесь");
                        int yw = Px.textWidth(you, Px.Weight.SEMIBOLD, this.S(10.0)) + this.S(16.0);
                        int yh = this.S(20.0);
                        Px.round(g, x + w - this.S(12.0) - yw, ry + (rh - yh) / 2, yw, yh, yh / 2, Px.a(acc(), 60));
                        Px.textCenter(
                                g,
                                you,
                                x + w - this.S(12.0) - yw / 2,
                                ry + (rh - this.S(10.0)) / 2,
                                Px.Weight.SEMIBOLD,
                                this.S(10.0),
                                Px.mix(acc(), -1, 0.45F));
                    } else if (onHw) {
                        Px.round(g, bx, by, bs, bs, bs / 2, Px.mix(field(), acc(), hk));
                        int as = this.S(13.0);
                        glyph(
                                g,
                                "chevron-right",
                                bx + (bs - as) / 2 + Math.round(hk * (float) this.S(1.0)),
                                by + (bs - as) / 2,
                                as,
                                Px.mix(sub(), -1, hk));
                    }

                    Px.fade = kf;
                    if (!this.under() && app > 0.3F && go) {
                        this.clipped(
                                x,
                                ry0,
                                w,
                                rh,
                                sc,
                                t,
                                b,
                                () -> {
                                    UiSound.play("select");
                                    ev.join(e);
                                    this.onClose();
                                },
                                (Runnable) null);
                    }
                }
            }

            g.disableScissor();
            g.nextStratum();
            this.edges(g, x - this.S(12.0), w + this.S(24.0), t, b, sc, this.baseColour());
            this.scrollbar(g, x + w + this.S(8.0), t, b, total, sc, "hwev");
        }
    }

    private void emptyNote(GuiGraphics g, String head, String line, Module turnOn) {
        float a = ease((float) (this.now - this.contentAt) / 300.0F);
        float k = Px.fade;
        Px.fade = k * a;
        int cx = this.cx0 + this.cw0 / 2;
        int cy = this.top + (this.bottom - this.top) / 2 - this.S(40.0);
        Px.textCenter(g, Lang.tr(head), cx, cy, Px.Weight.SEMIBOLD, this.S(14.0), T().text());
        Px.textCenter(g, Lang.tr(line), cx, cy + this.S(22.0), Px.Weight.MEDIUM, this.S(12.0), faint());
        if (turnOn != null) {
            String t = Lang.tr("Включить");
            int bfs = this.S(12.0);
            int bh = this.S(30.0);
            int bw = Px.textWidth(t, Px.Weight.SEMIBOLD, bfs) + this.S(28.0);
            int bx = cx - bw / 2;
            int by = cy + this.S(46.0);
            boolean hv = !this.under() && this.hover(bx, by, bw, bh);
            if (hv) {
                this.hot("empty:on");
            }

            float hk = this.an("empty:on", hv ? 1.0F : 0.0F, 18.0F);
            Px.round(g, bx, by, bw, bh, this.S(8.0), Px.mix(acc(), -1, 0.1F * hk));
            Px.textCenter(g, t, cx, by + (bh - bfs) / 2, Px.Weight.SEMIBOLD, bfs, -1);
            if (!this.under()) {
                this.region(bx, by, bw, bh, () -> {
                    turnOn.setEnabled(true);
                    UiSound.play("charge");
                    Toasts.module(turnOn, false);
                    Config.dirty();
                    this.content((String) null);
                });
            }
        }

        Px.fade = k;
    }

    private void header(GuiGraphics g, int x, int y, int w, int h) {
        String[] labels = null;
        int active = -1;
        String title;
        switch (page.ordinal()) {
            case 0:
                title = this.search.isEmpty() ? Lang.tr(category.title) : Lang.tr("Поиск");
                break;
            case 1:
                title = "Cosmetics";
                labels = new String[WARD.length];

                for (int i = 0; i < WARD.length; ++i) {
                    labels[i] = Lang.tr(WARD[i]);
                }

                active = wardrobe;
                break;
            case 2:
                // one flat page: no tabs, everything is on it
                title = "Configs";
                break;
            case 3:
                title = "Effects Studio";
                labels = new String[] {"0.25×", "0.5×", "1×"};
                active = Studio.speed < 0.3F ? 0 : (Studio.speed < 0.7F ? 1 : 2);
                break;
            case 4:
                if (helperServer == 1) {
                    title = "FunTime";
                    int fn = Modules.FT_EVENTS.enabled() ? FtApi.events().size() : 0;
                    labels = new String[] {"Helpers", Lang.tr("Ивенты") + (fn > 0 ? "  " + fn : "")};
                    active = helperTab;
                    break;
                }
                title = "HolyWorld";
                int n = Modules.HOLY_EVENTS.enabled()
                        ? Modules.HOLY_EVENTS.sorted().size()
                        : 0;
                String[] var10000 = new String[] {"Helpers", null};
                String var10003 = Lang.tr("Ивенты");
                var10000[1] = var10003 + (n > 0 ? "  " + n : "");
                labels = var10000;
                active = helperTab;
                break;
            case 5:
                title = "Friends";
                labels = this.friendsPage.labels();
                active = this.friendsPage.tab;
                break;
            case 6:
                title = "Crosshair Studio";
                break;
            default:
                title = "";
        }

        int bx = x + w - h;
        this.themeButton(g, bx, y, h);
        int wx = bx - this.S(8.0) - h;
        this.wideButton(g, wx, y, h);
        int right = wx - this.S(8.0);
        if (page == ArcanicMenu.Page.MODULES) {
            int sw = this.S(230.0);
            this.searchPill(g, right - sw, y, sw, h);
            right -= sw;
        } else if (page == ArcanicMenu.Page.WARDROBE && wardMaster(wardrobe) != null) {
            Module master = wardMaster(wardrobe);
            String var10001 = master.id;
            float on = this.tween("on:" + var10001, master.enabled() ? 1.0F : 0.0F, 200.0F);
            int tw2 = this.S(30.0);
            int tgx = right - this.S(4.0) - tw2;
            boolean hv = !this.under() && this.hover(tgx - this.S(12.0), y, this.S(16.0) + tw2, h);
            if (hv) {
                this.hot("master");
            }

            this.toggle(g, tgx, y, tw2, h, on, hv ? 1.0F : 0.0F);
            right = tgx - this.S(10.0);
            if (!this.under()) {
                this.region(tgx - this.S(8.0), y, this.S(12.0) + tw2, h, () -> {
                    master.toggle();
                    UiSound.play(master.enabled() ? "charge" : "drain");
                    Toasts.module(master, false);
                    Config.dirty();
                });
            }
        } else if (page == ArcanicMenu.Page.STUDIO) {
            boolean paused = Studio.paused;
            int bfs = this.S(12.0);
            int bh = this.S(28.0);
            int byy = y + (h - bh) / 2;
            String t = Lang.tr(paused ? "Играть" : "Пауза");
            int bw = Px.textWidth(t, Px.Weight.SEMIBOLD, bfs) + this.S(14.0) + this.S(8.0) + this.S(26.0);
            int bx2 = right - bw;
            boolean hv = !this.under() && this.hover(bx2, byy, bw, bh);
            if (hv) {
                this.hot("studioPause");
            }

            float hk = this.an("studioPause", hv ? 1.0F : 0.0F, 18.0F);
            Px.round(
                    g,
                    bx2,
                    byy,
                    bw,
                    bh,
                    this.S(8.0),
                    paused ? Px.mix(acc(), -1, 0.1F * hk) : Px.mix(field(), cardHi(), hk));
            int tc = paused ? -1 : label();
            glyph(g, paused ? "play" : "pause", bx2 + this.S(12.0), byy + (bh - this.S(14.0)) / 2, this.S(14.0), tc);
            Px.text(
                    g,
                    t,
                    bx2 + this.S(12.0) + this.S(14.0) + this.S(7.0),
                    byy + (bh - bfs) / 2,
                    Px.Weight.SEMIBOLD,
                    bfs,
                    tc);
            if (!this.under()) {
                this.region(bx2, byy, bw, bh, () -> {
                    Studio.paused = !Studio.paused;
                    UiSound.play("click");
                });
            }

            boolean auto = Studio.autoTurn;
            String at = Lang.tr("Авто-поворот");
            int aw = Px.textWidth(at, Px.Weight.SEMIBOLD, bfs) + this.S(30.0) + this.S(10.0) + this.S(20.0);
            int ax2 = bx2 - this.S(6.0) - aw;
            boolean ahv = !this.under() && this.hover(ax2, byy, aw, bh);
            if (ahv) {
                this.hot("studioAuto");
            }

            float ak = this.an("studioAuto", ahv ? 1.0F : 0.0F, 18.0F);
            Px.round(g, ax2, byy, aw, bh, this.S(8.0), Px.mix(field(), cardHi(), ak));
            Px.text(g, at, ax2 + this.S(10.0), byy + (bh - bfs) / 2, Px.Weight.SEMIBOLD, bfs, label());
            this.toggle(
                    g,
                    ax2 + aw - this.S(10.0) - this.S(30.0),
                    byy,
                    this.S(30.0),
                    bh,
                    this.tween("studioAutoOn", auto ? 1.0F : 0.0F, 180.0F),
                    ak);
            if (!this.under()) {
                this.region(ax2, byy, aw, bh, () -> {
                    Studio.autoTurn = !Studio.autoTurn;
                    UiSound.play("click");
                });
            }

            right = ax2;
        } else if (page == ArcanicMenu.Page.HELPERS) {
            HolyEvents ev = Modules.HOLY_EVENTS;
            boolean ft = helperServer == 1;
            boolean onHw = ft ? FunTime.here() : HolyEvents.onHolyWorld();
            String here = onHw && !ft ? ev.here() : null;
            String t = onHw
                    ? (ft ? "FunTime" : (here != null ? ev.serverName(here) : "HolyWorld"))
                    : Lang.tr("Не на сервере");
            int bfs = this.S(12.0);
            int bh = this.S(28.0);
            int byy = y + (h - bh) / 2;
            int ds = this.S(6.0);
            int bw = this.S(12.0) + ds + this.S(8.0) + Px.textWidth(t, Px.Weight.MEDIUM, bfs) + this.S(12.0);
            int bx2 = right - bw;
            Px.round(g, bx2, byy, bw, bh, this.S(8.0), field());
            Px.round(g, bx2 + this.S(12.0), byy + (bh - ds) / 2, ds, ds, ds / 2, onHw ? -9449586 : faint());
            Px.text(
                    g,
                    t,
                    bx2 + this.S(12.0) + ds + this.S(8.0),
                    byy + (bh - bfs) / 2,
                    Px.Weight.MEDIUM,
                    bfs,
                    onHw ? label() : faint());
            right = this.serverSwitch(g, bx2 - this.S(8.0), byy, bh, bfs);
        } else if (page == ArcanicMenu.Page.FRIENDS && Sync.signedIn()) {
            int bfs = this.S(12.0);
            int bh = this.S(28.0);
            int byy = y + (h - bh) / 2;
            String t = Lang.tr("Добавить друга");
            int bw = Px.textWidth(t, Px.Weight.SEMIBOLD, bfs) + this.S(14.0) + this.S(8.0) + this.S(26.0);
            int bx2 = right - bw;
            boolean hv = !this.under() && this.hover(bx2, byy, bw, bh);
            if (hv) {
                this.hot("addFriend");
            }

            float hk = this.an("addFriend", hv ? 1.0F : 0.0F, 18.0F);
            Px.round(g, bx2, byy, bw, bh, this.S(8.0), Px.mix(acc(), -1, 0.1F * hk));
            glyph(g, "user-plus", bx2 + this.S(12.0), byy + (bh - this.S(14.0)) / 2, this.S(14.0), -1);
            Px.text(
                    g,
                    t,
                    bx2 + this.S(12.0) + this.S(14.0) + this.S(7.0),
                    byy + (bh - bfs) / 2,
                    Px.Weight.SEMIBOLD,
                    bfs,
                    -1);
            if (!this.under()) {
                this.region(bx2, byy, bw, bh, this.friendsPage::addByName);
            }

            right = bx2;
        }

        int fs = this.S(12.0);
        int ip = this.S(10.0);
        int sh2 = this.S(28.0);
        int sy = y + (h - sh2) / 2;
        int tfs = this.S(17.0);
        int lead = page == ArcanicMenu.Page.HELPERS && helperServer != 1 ? this.S(30.0) : 0;
        int titleW = lead + Px.textWidth(title, Px.Weight.SEMIBOLD, tfs);
        int stripW = 0;
        int[] xs = null;
        int[] ws = null;
        if (labels != null) {
            ws = new int[labels.length];

            for (int i = 0; i < labels.length; ++i) {
                ws[i] = Px.textWidth(labels[i], Px.Weight.MEDIUM, fs) + ip * 2;
                stripW += ws[i];
            }

            stripW += this.S(6.0);
        }

        boolean showTitle = labels == null || x + titleW + this.S(18.0) + stripW <= right - this.S(12.0);
        int tx = x;
        if (showTitle) {
            if (lead > 0) {
                int ls = this.S(24.0);
                HolyEvents.picture(g, HolyEvents.LOGO, 103, 96, x - this.S(2.0), y + (h - ls) / 2, ls, ls);
            }

            Px.text(g, title, x + lead, y + (h - tfs) / 2, Px.Weight.SEMIBOLD, tfs, T().text());
            tx = x + titleW + this.S(18.0);
            if (page == ArcanicMenu.Page.MODULES) {
                String cnt = String.valueOf(this.visible().size());
                int cw = Math.max(this.S(20.0), Px.textWidth(cnt, Px.Weight.SEMIBOLD, this.S(11.0)) + this.S(12.0));
                int chh = this.S(18.0);
                Px.round(g, x + titleW + this.S(8.0), y + (h - chh) / 2, cw, chh, chh / 2, field());
                Px.textCenter(
                        g,
                        cnt,
                        x + titleW + this.S(8.0) + cw / 2,
                        y + (h - this.S(11.0)) / 2,
                        Px.Weight.SEMIBOLD,
                        this.S(11.0),
                        faint());
            }
        }

        if (labels != null) {
            Px.round(g, tx, sy, stripW, sh2, this.S(8.0), field());
            xs = new int[labels.length];
            int cx = tx + this.S(3.0);

            for (int i = 0; i < labels.length; ++i) {
                xs[i] = cx;
                cx += ws[i];
            }

            int ih = sh2 - this.S(6.0);
            int iy = sy + this.S(3.0);
            if (active >= 0) {
                String var90 = String.valueOf(page);
                int plx = Math.round(this.tween("plateX:" + var90, (float) xs[active], 200.0F));
                var90 = String.valueOf(page);
                int plw = Math.round(this.tween("plateW:" + var90, (float) ws[active], 200.0F));
                Px.round(g, plx, iy, plw, ih, this.S(6.0), light() ? -1 : -14013904);
            }

            for (int i = 0; i < labels.length; ++i) {
                boolean on = i == active;
                boolean hv = !this.under() && this.hover(xs[i], sy, ws[i], sh2);
                if (hv && !on) {
                    String var92 = String.valueOf(page);
                    this.hot("tab:" + var92 + i);
                }

                String var93 = String.valueOf(page);
                float hk = this.an("tab:" + var93 + i, hv ? 1.0F : 0.0F, 18.0F);
                var93 = String.valueOf(page);
                float ak = this.tween("tabA:" + var93 + i, on ? 1.0F : 0.0F, 180.0F);
                int c = Px.mix(Px.mix(faint(), label(), hk), T().text(), ak);
                Px.textCenter(g, labels[i], xs[i] + ws[i] / 2, iy + (ih - fs) / 2, Px.Weight.MEDIUM, fs, c);
                if (!this.under()) {
                    int tab = i;
                    this.region(xs[i], sy, ws[i], sh2, () -> this.tabClick(tab));
                }
            }
        }
    }

    private void tabClick(int i) {
        int from = -1;
        if (page == ArcanicMenu.Page.MODULES) {
            for (int j = 0; j < TABS.length; ++j) {
                if (TABS[j] == category) {
                    from = j;
                }
            }
        }

        if (page == ArcanicMenu.Page.WARDROBE) {
            from = wardrobe;
        }

        if (page == ArcanicMenu.Page.PRESETS) {
            from = this.presetsPage.tab;
        }

        if (page == ArcanicMenu.Page.HELPERS) {
            from = helperTab;
        }

        if (page == ArcanicMenu.Page.FRIENDS) {
            from = this.friendsPage.tab;
        }

        this.pendingDx = from < 0 ? 0 : Integer.signum(i - from);
        if (page == ArcanicMenu.Page.MODULES) {
            if (category != TABS[i] || !this.search.isEmpty()) {
                category = TABS[i];
                this.search = "";
                this.searchFocused = false;
                this.content("tab");
            }
        } else if (page == ArcanicMenu.Page.WARDROBE && wardrobe != i) {
            wardrobe = i;
            this.content("tab");
        } else if (page == ArcanicMenu.Page.PRESETS && this.presetsPage.tab != i) {
            this.presetsPage.tab = i;
            this.content("tab");
        } else if (page == ArcanicMenu.Page.HELPERS && helperTab != i) {
            helperTab = i;
            this.content("tab");
        } else if (page == ArcanicMenu.Page.FRIENDS && this.friendsPage.tab != i) {
            this.friendsPage.tab = i;
            this.friendsPage.back();
            this.content("tab");
        } else if (page == ArcanicMenu.Page.STUDIO) {
            Studio.speed = i == 0 ? 0.25F : (i == 1 ? 0.5F : 1.0F);
            UiSound.play("click");
        }
    }

    private void content(String sound) {
        this.contentAt = this.now;
        this.popMod = null;
        this.pop = null;
        this.presetsPage.typing = false;
        this.contentDx = this.pendingDx;
        this.contentDy = this.pendingDy;
        this.pendingDx = this.pendingDy = 0;
        if (sound != null) {
            UiSound.play(sound);
        }
    }

    private void searchPill(GuiGraphics g, int x, int y, int w, int h) {
        boolean over = !this.under() && this.hover(x, y, w, h);
        if (over) {
            this.hot("search");
        }

        float f = this.tween("sfocus", this.searchFocused ? 1.0F : 0.0F, 180.0F);
        float hv = this.an("shover", over ? 1.0F : 0.0F, 18.0F);
        Px.round(g, x, y, w, h, this.S(8.0), Px.mix(field(), cardHi(), Math.max(hv, f) * 0.7F));
        if (f > 0.01F) {
            Px.outline(g, x, y, w, h, this.S(8.0), Px.a(acc(), (int) (150.0F * f)));
        }

        glyph(
                g,
                "search-s",
                x + this.S(10.0),
                y + (h - this.S(17.0)) / 2,
                this.S(17.0),
                Px.mix(faint(), sub(), Math.max(hv, f)));
        boolean empty = this.search.isEmpty();
        Px.text(
                g,
                Px.ellipsize(empty ? Lang.tr("Поиск") : this.search, Px.Weight.MEDIUM, this.S(13.0), w - this.S(76.0)),
                x + this.S(35.0),
                y + (h - this.S(13.0)) / 2,
                Px.Weight.MEDIUM,
                this.S(13.0),
                empty ? faint() : T().text());
        if (this.searchFocused && (this.now - this.openedAt) / 530L % 2L == 0L) {
            Px.rect(
                    g,
                    x
                            + this.S(35.0)
                            + (empty ? 0 : Px.textWidth(this.search, Px.Weight.MEDIUM, this.S(13.0)))
                            + this.S(1.0),
                    y + this.S(10.0),
                    Math.max(1, this.S(1.0)),
                    h - this.S(20.0),
                    T().text());
        }

        if (!empty) {
            boolean xh = !this.under() && this.hover(x + w - this.S(34.0), y, this.S(34.0), h);
            glyph(g, "x-s", x + w - this.S(24.0), y + (h - this.S(12.0)) / 2, this.S(12.0), xh ? T().text() : faint());
            if (!this.under()) {
                this.region(x + w - this.S(34.0), y, this.S(34.0), h, () -> {
                    this.search = "";
                    this.content("click");
                });
            }
        } else if (!this.searchFocused) {
            this.keycap(
                    g,
                    "Ctrl F",
                    x + w - this.S(8.0) - this.keycapW("Ctrl F", this.S(10.0)),
                    y + (h - this.S(18.0)) / 2,
                    this.S(10.0));
        }

        if (!this.under()) {
            this.region(x, y, w - (empty ? 0 : this.S(34.0)), h, () -> {
                this.searchFocused = true;
                UiSound.play("click", 1.2F, 0.6F);
            });
        }
    }

    private void wideButton(GuiGraphics g, int x, int y, int s) {
        boolean on = Modules.PREFS.wide.on();
        boolean hv = !this.under() && this.hover(x, y, s, s);
        if (hv) {
            this.hot("wide");
        }

        float hk = this.an("wideH", hv ? 1.0F : 0.0F, 18.0F);
        float ok = this.tween("wideOn", on ? 1.0F : 0.0F, 220.0F);
        Px.round(g, x, y, s, s, this.S(8.0), Px.mix(Px.mix(field(), cardHi(), hk), Px.a(acc(), 70), ok));
        int gs = this.S(17.0);
        g.pose().pushMatrix();
        g.pose().translate((float) x + (float) s / 2.0F, (float) y + (float) s / 2.0F);
        float sc = 1.0F + 0.12F * hk;
        g.pose().scale(sc, sc);
        glyph(
                g,
                "maximize-2",
                -gs / 2,
                -gs / 2,
                gs,
                Px.mix(Px.mix(sub(), T().text(), hk), Px.mix(acc(), -1, 0.4F), ok));
        g.pose().popMatrix();
        if (!this.under()) {
            this.region(x, y, s, s, () -> {
                Modules.PREFS.wide.set(!Modules.PREFS.wide.on());
                Config.dirty();
                UiSound.play(Modules.PREFS.wide.on() ? "charge" : "drain");
            });
        }
    }

    private void themeButton(GuiGraphics g, int x, int y, int s) {
        boolean hv = !this.under() && this.hover(x, y, s, s);
        if (hv) {
            this.hot("theme");
        }

        float hk = this.an("themeH", hv ? 1.0F : 0.0F, 18.0F);
        float k = this.tween("themeK", Theme.palette == 5 ? 1.0F : 0.0F, 420.0F);
        Px.round(g, x, y, s, s, this.S(8.0), Px.mix(field(), cardHi(), hk));
        int gs = this.S(19.0);
        int c = Px.mix(sub(), T().text(), hk);

        for (int i = 0; i < 2; ++i) {
            float vis = i == 0 ? 1.0F - k : k;
            if (!(vis < 0.02F)) {
                float kf = Px.fade;
                Px.fade = kf * vis;
                g.pose().pushMatrix();
                g.pose().translate((float) x + (float) s / 2.0F, (float) y + (float) s / 2.0F);
                if (vis < 0.999F) {
                    g.pose().rotate((float) ((double) (i == 0 ? k : k - 1.0F) * Math.PI * 0.6));
                    float sc = 0.6F + 0.4F * vis;
                    g.pose().scale(sc, sc);
                }

                glyph(g, i == 0 ? "moon-s" : "sun-s", -gs / 2, -gs / 2, gs, c);
                g.pose().popMatrix();
                Px.fade = kf;
            }
        }

        if (!this.under()) {
            this.region(x, y, s, s, () -> {
                Theme.setPalette(Theme.palette == 5 ? 0 : 5);
                Config.dirty();
                UiSound.play("select");
            });
        }
    }

    float scrolled(String key, int total, int t, int b) {
        float max = (float) Math.max(0, total - (b - t));
        this.scrollMax.put(key, max);
        float want = Math.max(0.0F, Math.min(max, (Float) this.scroll.getOrDefault(key, 0.0F)));
        this.scroll.put(key, want);
        Float was = (Float) this.scrollShown.get(key);
        float v = was != null && Theme.animations ? was + (want - was) * this.anim.factor(13.0F) : want;
        if (Math.abs(want - v) < 0.25F) {
            v = want;
        }

        this.scrollShown.put(key, v);
        return v;
    }

    void resetScroll(String key) {
        this.scroll.put(key, 0.0F);
        this.scrollShown.put(key, 0.0F);
    }

    int baseColour() {
        return panelBg();
    }

    void edges(GuiGraphics g, int x, int w, int t, int b, float sc, int colour) {
        int fh = this.S(20.0);
        int max = 255;
        if (sc > 0.5F) {
            Px.vgrad(
                    g,
                    x,
                    t,
                    w,
                    fh,
                    Px.a(colour, (int) (Math.min(1.0F, sc / (float) this.S(24.0)) * (float) max)),
                    Px.a(colour, 0));
        }

        Px.vgrad(g, x, b - fh, w, fh, Px.a(colour, 0), Px.a(colour, max));
    }

    void scrollbar(GuiGraphics g, int x, int t, int b, int total, float sc, String key) {
        int view = b - t;
        float max = (Float) this.scrollMax.getOrDefault(key, (float) Math.max(0, total - view));
        if (!(max < 1.0F) && view >= this.S(20.0)) {
            int th = Math.max(
                    this.S(30.0),
                    Math.round((float) view * Math.min(1.0F, ((float) total - max) / Math.max(1.0F, (float) total))));
            int ty = t + Math.round((float) (view - th) * Math.max(0.0F, Math.min(1.0F, sc / max)));
            boolean drag = this.dragging("sb:" + key);
            boolean hv = drag || !this.blocked() && this.hover(x - this.S(8.0), t, this.S(20.0), view);
            float hk = this.an("sb:" + key, hv ? 1.0F : 0.0F, 18.0F);
            float dk = this.an("sbd:" + key, drag ? 1.0F : 0.0F, 22.0F);
            int wdt = Math.max(2, this.S(3.0));
            int tw = Math.max(wdt, Math.round((float) this.S(3.0) + (float) this.S(2.0) * Math.max(hk, dk)));
            Px.round(g, x + this.S(2.0) - wdt / 2, t, wdt, view, wdt / 2, Px.mix(field(), cardHi(), hk));
            Px.round(
                    g,
                    x + this.S(2.0) - tw / 2,
                    ty,
                    tw,
                    th,
                    tw / 2,
                    Px.mix(Px.mix(acc(), -1, 0.2F * hk), -1, 0.1F * dk));
            float[] grab = new float[] {Float.NaN};
            if (!this.blocked()) {
                this.regions.add(new Region(
                        x - this.S(8.0),
                        t,
                        this.S(20.0),
                        view,
                        (Runnable) null,
                        (Runnable) null,
                        (ax, ay) -> {
                            if (Float.isNaN(grab[0])) {
                                grab[0] = ay >= (double) ty && ay < (double) (ty + th)
                                        ? (float) (ay - (double) ty)
                                        : (float) th / 2.0F;
                            }

                            float v = (float) Math.max(
                                            0.0,
                                            Math.min(
                                                    1.0,
                                                    (ay - (double) t - (double) grab[0])
                                                            / (double) Math.max(1, view - th)))
                                    * max;
                            this.scroll.put(key, v);
                            this.scrollShown.put(key, v);
                        },
                        "sb:" + key));
            }
        }
    }

    private List<Module> visible() {
        List<Module> pool = this.search.isEmpty() ? Modules.of(category) : Modules.ALL;
        List<Module> out = new ArrayList<>();
        String q = this.search.toLowerCase(Locale.ROOT);

        for (Module m : pool) {
            if (m != Modules.PREFS
                    && !HolyFeatures.hidden(m)
                    && (q.isEmpty()
                            || Lang.matches(m.title(), q)
                            || Lang.matches(m.name, q)
                            || Lang.matches(m.description, q))) {
                out.add(m);
            }
        }

        return out;
    }

    private static int n(Module m) {
        int n = 0;

        for (Setting<?> s : m.settings) {
            if (s.visible()) {
                ++n;
            }
        }

        return n;
    }

    static List<Setting<?>> params(Module m) {
        List<Setting<?>> out = new ArrayList<>();

        for (Setting<?> st : m.settings) {
            if (st.visible()) {
                out.add(st);
            }
        }

        return out;
    }

    private int headH() {
        return this.S(46.0);
    }

    private int cardH(Module m, List<Setting<?>> ls, int cw) {
        return this.headH();
    }

    private void rows(GuiGraphics g, List<Module> list, String key) {
        if (list.isEmpty()) {
            float t = ease((float) (this.now - this.contentAt) / 300.0F);
            float k = Px.fade;
            Px.fade = k * t;
            int cy = this.top + (this.bottom - this.top) / 2 - this.S(46.0);
            Px.textCenter(
                    g,
                    Lang.tr("Ничего не найдено"),
                    this.cx0 + this.cw0 / 2,
                    cy + this.S(30.0),
                    Px.Weight.SEMIBOLD,
                    this.S(14.0),
                    T().text());
            Px.textCenter(
                    g,
                    Lang.tr("Попробуй другое слово"),
                    this.cx0 + this.cw0 / 2,
                    cy + this.S(52.0),
                    Px.Weight.MEDIUM,
                    this.S(12.0),
                    faint());
            Px.fade = k;
        } else {
            this.compact = true;
            int gap = this.S(10.0);
            int cols = !Modules.PREFS.wide.on() && this.cw0 < this.S(1100.0) ? 2 : 3;
            int cw = (this.cw0 - gap * (cols - 1)) / cols;
            int count = list.size();
            int[] colY = new int[cols];
            int[] cx = new int[count];
            int[] cy = new int[count];
            int[] ch = new int[count];
            List<List<Setting<?>>> ps = new ArrayList<>();

            for (int i = 0; i < count; ++i) {
                List<Setting<?>> ls = params((Module) list.get(i));
                ps.add(ls);
                int c = 0;

                for (int j = 1; j < cols; ++j) {
                    if (colY[j] < colY[c]) {
                        c = j;
                    }
                }

                cx[i] = this.cx0 + c * (cw + gap);
                cy[i] = this.top + this.S(2.0) + colY[c];
                ch[i] = this.cardH((Module) list.get(i), ls, cw);
                colY[c] += ch[i] + gap;
            }

            int total = 0;

            for (int v : colY) {
                total = Math.max(total, v);
            }

            total += this.S(4.0);
            float sc = this.scrolled(key, total, this.top, this.bottom);
            g.enableScissor(this.cx0 - this.S(12.0), this.top, this.cx0 + this.cw0 + this.S(12.0), this.bottom);
            g.pose().pushMatrix();
            g.pose().translate(0.0F, (float) (-Math.round(sc)));

            for (int i = 0; i < count; ++i) {
                g.nextStratum();
                this.card(
                        g, (Module) list.get(i), (List) ps.get(i), cx[i], cy[i], cw, ch[i], (float) Math.round(sc), i);
            }

            g.pose().popMatrix();
            g.disableScissor();
            g.nextStratum();
            this.compact = false;
            this.edges(
                    g, this.cx0 - this.S(12.0), this.cw0 + this.S(24.0), this.top, this.bottom, sc, this.baseColour());
            this.scrollbar(g, this.cx0 + this.cw0 + this.S(8.0), this.top, this.bottom, total, sc, key);
        }
    }

    private void card(GuiGraphics g, Module m, List<Setting<?>> ls, int x, int y, int w, int h, float sc, int i) {
        float sy = (float) y - sc;
        if (this.popMod == m) {
            this.ancX = x;
            this.ancY = Math.round(sy);
            this.ancW = w;
            this.ancH = h;
        }

        if (!(sy + (float) h < (float) (this.top - this.S(20.0))) && !(sy > (float) (this.bottom + this.S(20.0)))) {
            float app =
                    ease(Math.max(0.0F, (float) (this.now - this.contentAt - (long) Math.min(i, 12) * 14L) / 240.0F));
            int pad = this.S(14.0);
            int head = this.headH();
            int R = this.S(12.0);
            boolean hv = !this.under()
                    && this.my >= (double) this.top
                    && this.my < (double) this.bottom
                    && this.hover(x, Math.round(sy), w, head);
            if (hv) {
                this.hot("card:" + m.id);
                this.tipNow = m;
            }

            String var10001 = m.id;
            float hk = this.an("ch:" + var10001, hv ? 1.0F : 0.0F, 18.0F);
            var10001 = m.id;
            float on = this.tween("on:" + var10001, m.enabled() ? 1.0F : 0.0F, 180.0F);
            float k = Px.fade;
            Px.fade = k * app;
            g.pose().pushMatrix();
            g.pose().translate(0.0F, (float) Math.round((1.0F - app) * (float) this.S(6.0)));
            var10001 = m.id;
            float sel = this.tween("sel:" + var10001, this.popMod == m ? 1.0F : 0.0F, 200.0F);
            Px.round(g, x, y, w, h, R, Px.mix(cardBg(), cardHi(), hk));
            Px.outline(g, x, y, w, h, R, Px.mix(Px.mix(hairline(14), hairline(30), hk), Px.a(acc(), 170), sel));
            this.topLight(g, x, y, w, R);
            int ckS = this.S(30.0) / 2 * 2;
            int ckX = x + w - pad - ckS;
            boolean waiting = this.binding == m;
            String kl = waiting ? Lang.tr("Нажми клавишу") : (m.bind >= 0 ? Keys.label(m.bind) : "");
            int kfs = this.S(11.0);
            int kh = this.S(22.0) / 2 * 2;
            int kmin = this.S(26.0) / 2 * 2;
            int kwT = kl.isEmpty() ? kmin : Math.max(kmin, Px.textWidth(kl, Px.Weight.SEMIBOLD, kfs) + this.S(16.0));
            int kw = Math.round(this.tween("kw:" + m.id, (float) kwT, 220.0F));
            int kx = ckX - this.S(10.0) - kw;
            int ky = y + (head - kh) / 2;
            boolean itemList = m instanceof ItemZones;
            boolean hasP = itemList || !ls.isEmpty();
            if (itemList) {
                kx = ckX + ckS;
            }

            int gx = kx - this.S(4.0) - kh;
            int textW = (hasP ? gx : kx) - this.S(10.0) - (x + pad);
            int nfs = this.S(14.0);
            String name = Px.ellipsize(m.title(), Px.Weight.SEMIBOLD, nfs, textW);
            int nameC = T().text();
            boolean lock = HolyFeatures.locked(m);
            int ny = lock ? y + this.S(8.0) : y + (head - nfs) / 2;
            if (this.search.isEmpty()) {
                Px.text(g, name, x + pad, ny, Px.Weight.SEMIBOLD, nfs, nameC);
            } else {
                this.nameLit(g, name, x + pad, ny, nfs, nameC);
            }

            if (lock) {
                Px.text(
                        g,
                        Px.ellipsize(Lang.tr("Запрещено на HolyWorld"), Px.Weight.MEDIUM, this.S(11.0), textW),
                        x + pad,
                        ny + this.S(18.0),
                        Px.Weight.MEDIUM,
                        this.S(11.0),
                        -2535330);
            }

            boolean khv = hv && this.hover(kx, Math.round(sy + (float) (ky - y)), kw, kh);
            if (itemList) {
                ItemZones iz = (ItemZones) m;
                int var10000 = iz.shownCount();
                String cnt = var10000 + " " + Lang.tr("из") + " " + iz.items.size();
                Px.textRight(
                        g,
                        cnt,
                        gx - this.S(8.0),
                        y + (head - this.S(11.0)) / 2,
                        Px.Weight.MEDIUM,
                        this.S(11.0),
                        faint());
            } else {
                this.keyChip(g, kl, kx, ky, kw, kh, kfs, waiting, khv, m.id);
            }

            if (!itemList) {
                if (lock) {
                    int xs = this.S(14.0);
                    glyph(g, "x", ckX + ckS - xs - this.S(2.0), y + (head - xs) / 2, xs, -2535330);
                } else {
                    this.toggle(g, ckX, y, ckS, head, on, hk);
                }
            }

            if (hasP) {
                boolean gh = hv && this.hover(gx, Math.round(sy + (float) (ky - y)), kh, kh);
                if (gh) {
                    this.hot("gear:" + m.id);
                }

                var10001 = m.id;
                float gk = this.an("gear:" + var10001, gh ? 1.0F : 0.0F, 18.0F);
                Math.max(gk, sel);
                if (gk > 0.01F) {
                    Px.round(g, gx, ky, kh, kh, this.S(6.0), Px.a(field(), (int) (255.0F * gk)));
                }

                int cs = this.S(18.0);
                glyph(
                        g,
                        itemList ? "chevron-right" : "w.dots",
                        gx + (kh - cs) / 2,
                        ky + (kh - cs) / 2,
                        cs,
                        Px.mix(Px.mix(faint(), label(), gk), acc(), sel));
            }

            g.pose().popMatrix();
            Px.fade = k;
            if (!this.under() && !(app < 0.3F)) {
                Runnable flip = () -> {
                    if (!m.enabled() && HolyFeatures.forbidden(m)) {
                        HolyFeatures.deny(m);
                    } else {
                        m.toggle();
                        if (m.enabled()) {
                            this.pulse.put(m.id, this.now);
                        }

                        UiSound.play(m.enabled() ? "charge" : "drain");
                        Toasts.module(m, false);
                        Config.dirty();
                    }
                };
                Runnable params = hasP
                        ? () -> {
                            if (m == Modules.CROSSHAIR) {
                                UiSound.play("page");
                                this.toPage(ArcanicMenu.Page.CROSSHAIR);
                            } else {
                                this.openPopup(m, x, Math.round(sy), w, h);
                            }
                        }
                        : null;
                if (itemList) {
                    this.clipped(x, y, w, head, sc, this.top, this.bottom, params, params);
                } else {
                    this.clipped(
                            x, y, (hasP ? gx : kx) - this.S(4.0) - x, head, sc, this.top, this.bottom, flip, params);
                    if (hasP) {
                        this.clipped(
                                gx - this.S(3.0),
                                ky - this.S(3.0),
                                kh + this.S(6.0),
                                kh + this.S(6.0),
                                sc,
                                this.top,
                                this.bottom,
                                params,
                                params);
                    }

                    this.clipped(
                            ckX - this.S(4.0),
                            y,
                            x + w - ckX + this.S(4.0),
                            head,
                            sc,
                            this.top,
                            this.bottom,
                            flip,
                            (Runnable) null);
                    this.clipped(
                            kx - this.S(3.0),
                            ky - this.S(3.0),
                            kw + this.S(6.0),
                            kh + this.S(6.0),
                            sc,
                            this.top,
                            this.bottom,
                            () -> {
                                this.binding = this.binding == m ? null : m;
                                UiSound.play("click");
                            },
                            () -> {
                                m.bind = -1;
                                this.binding = null;
                                Config.dirty();
                                UiSound.play("reset");
                            });
                }
            }
        }
    }

    private void keyChip(
            GuiGraphics g, String t, int x, int y, int w, int h, int fs, boolean waiting, boolean hv, String id) {
        float wk = this.tween("kwait:" + id, waiting ? 1.0F : 0.0F, 200.0F);
        float pulse = (float) (Math.sin((double) this.now / 220.0) * 0.5 + 0.5) * wk;
        int bg = t.isEmpty() && !waiting ? Px.a(field(), hv ? 255 : 0) : (hv ? cardHi() : field());
        if (bg >>> 24 != 0) {
            Px.round(g, x, y, w, h, this.S(6.0), bg);
        }

        if (wk > 0.01F) {
            Px.outline(g, x, y, w, h, this.S(6.0), Px.a(acc(), (int) ((140.0F + 100.0F * pulse) * wk)));
        }

        g.enableScissor(x, y, x + w, y + h);
        float kf = Px.fade;
        if (t.isEmpty() || wk > 0.01F && waiting) {
            if (!waiting) {
                int gs = this.S(16.0);
                int gx = x + (w - gs) / 2;
                int gy = y + (h - gs) / 2;
                Px.icon(g, "w.keyboard", gx, gy, gs, hv ? label() : faint());
            } else {
                Px.fade = kf * wk;
                Px.textCenter(g, t, x + w / 2, y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, accText());
                Px.fade = kf;
            }
        } else {
            Px.textCenter(g, t, x + w / 2, y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, hv ? T().text() : sub());
        }

        g.disableScissor();
    }

    private int nameLit(GuiGraphics g, String name, int x, int y, int fs, int c) {
        int i = this.search.isEmpty()
                ? -1
                : name.toLowerCase(Locale.ROOT).indexOf(this.search.toLowerCase(Locale.ROOT));
        if (i < 0) {
            Px.text(g, name, x, y, Px.Weight.MEDIUM, fs, c);
            return x + Px.textWidth(name, Px.Weight.MEDIUM, fs);
        } else {
            String a = name.substring(0, i);
            String b = name.substring(i, Math.min(name.length(), i + this.search.length()));
            String r = name.substring(Math.min(name.length(), i + this.search.length()));
            Px.text(g, a, x, y, Px.Weight.MEDIUM, fs, c);
            x += Px.textWidth(a, Px.Weight.MEDIUM, fs);
            Px.text(g, b, x, y, Px.Weight.SEMIBOLD, fs, accText());
            x += Px.textWidth(b, Px.Weight.SEMIBOLD, fs);
            Px.text(g, r, x, y, Px.Weight.MEDIUM, fs, c);
            x += Px.textWidth(r, Px.Weight.MEDIUM, fs);
            return x;
        }
    }

    private void openPopup(Module m, int x, int y, int w, int h) {
        if (this.popMod == m) {
            this.popMod = null;
            UiSound.play("pop", 0.8F, 0.6F);
        } else {
            if (this.popShown != m) {
                this.anim.set("pm", 0.0F);
            }

            if (this.popMod != m) {
                this.popup.itemPage = null;
            }

            this.popMod = m;
            this.ancX = x;
            this.ancY = y;
            this.ancW = w;
            this.ancH = h;
            this.binding = null;
            this.pop = null;
            this.scroll.put("pm:" + m.id, 0.0F);
            this.scrollShown.put("pm:" + m.id, 0.0F);
            UiSound.play("pop");
        }
    }

    List<Config.Preset> presetList() {
        if (this.presetCache == null || this.now - this.presetAt > 400L) {
            this.presetCache = Config.presets();
            this.presetAt = this.now;
        }

        return this.presetCache;
    }

    private boolean navOn(Nav n) {
        boolean var10000;
        switch (n.kind()) {
            case 0:
                var10000 = page == ArcanicMenu.Page.MODULES && this.search.isEmpty() && category == TABS[n.idx()];
                break;
            case 1:
                var10000 = page == ArcanicMenu.Page.WARDROBE;
                break;
            case 2:
                var10000 = page == ArcanicMenu.Page.PRESETS;
                break;
            case 3:
            case 5:
            case 6:
            default:
                var10000 = false;
                break;
            case 4:
                var10000 = page == ArcanicMenu.Page.HELPERS;
                break;
            case 7:
                var10000 = page == ArcanicMenu.Page.STUDIO;
                break;
            case 8:
                var10000 = page == ArcanicMenu.Page.FRIENDS;
        }

        return var10000;
    }

    private void sidebar(GuiGraphics g, int x, int y, int w, int h) {
        int ls = this.S(22.0);
        int bx = x + this.S(20.0);
        int by = y + this.S(18.0);
        BrandLogo.idle(g, bx, by, ls);
        int fs = this.S(15.0);
        Px.text(g, "Arcanic", bx + ls + this.S(9.0), by + (ls - fs) / 2, Px.Weight.BOLD, fs, T().text());
        int count = 0;

        for (Nav[] grp : NAV) {
            count += grp.length;
        }

        int listTop = by + ls + this.S(18.0);
        int foot = this.S(46.0);
        int capH = this.S(24.0);
        int gap = this.S(2.0);
        int room = y + h - foot - listTop - NAV.length * capH - (count - 1) * gap;
        int ih = Math.max(this.S(20.0), Math.min(this.S(30.0), room / count));
        int ix = x + this.S(10.0);
        int iw = w - this.S(20.0);
        int iy = listTop;
        int onY = -1;
        int scan = listTop;

        for (int gi = 0; gi < NAV.length; ++gi) {
            scan += capH;

            for (Nav n : NAV[gi]) {
                if (this.navOn(n)) {
                    onY = scan;
                }

                scan += ih + gap;
            }
        }

        if (onY >= 0) {
            this.navPillAt = onY - this.py;
        }

        float pk = this.tween("navPillK", onY >= 0 ? 1.0F : 0.0F, 200.0F);
        if (pk > 0.01F && this.navPillAt != Integer.MIN_VALUE) {
            int pyy = this.py + Math.round(this.tween("navPillY", (float) this.navPillAt, 300.0F));
            Px.shadow(
                    g,
                    ix + this.S(6.0),
                    pyy + this.S(5.0),
                    iw - this.S(12.0),
                    ih - this.S(4.0),
                    this.S(8.0),
                    Px.a(acc(), (int) (70.0F * pk)));
            Px.round(g, ix, pyy, iw, ih, ih / 2, Px.a(acc(), (int) (255.0F * pk)));
        }

        for (int gi = 0; gi < NAV.length; ++gi) {
            this.caps(
                    g,
                    Lang.tr(GROUPS[gi]),
                    ix + this.S(12.0),
                    iy + capH - this.S(9.0) - this.S(6.0),
                    this.S(9.0),
                    faint());
            iy += capH;

            for (Nav n : NAV[gi]) {
                this.navItem(g, n, ix, iy, iw, ih);
                iy += ih + gap;
            }
        }

        String nick = Minecraft.getInstance().getUser().getName();
        int fy = y + h - foot + this.S(8.0);
        Px.round(g, bx, fy, this.S(26.0), this.S(26.0), this.S(13.0), cardHi());
        Px.textCenter(
                g,
                nick.isEmpty() ? "?" : nick.substring(0, 1).toUpperCase(Locale.ROOT),
                bx + this.S(13.0),
                fy + (this.S(26.0) - this.S(12.0)) / 2,
                Px.Weight.SEMIBOLD,
                this.S(12.0),
                sub());
        int nx = bx + this.S(26.0) + this.S(9.0);
        int nw = x + w - this.S(14.0) - nx;
        Px.text(
                g,
                Px.ellipsize(nick, Px.Weight.SEMIBOLD, this.S(12.0), nw),
                nx,
                fy + this.S(1.0),
                Px.Weight.SEMIBOLD,
                this.S(12.0),
                label());
        Px.text(g, "Arcanic Visuals", nx, fy + this.S(15.0), Px.Weight.MEDIUM, this.S(10.0), faint());
    }

    void caps(GuiGraphics g, String text, int x, int y, int fs, int colour) {
        String up = text.toUpperCase(Locale.ROOT);
        int track = Math.max(1, Math.round((float) fs * 0.12F));

        for (int i = 0; i < up.length(); ++i) {
            String ch = up.substring(i, i + 1);
            Px.text(g, ch, x, y, Px.Weight.SEMIBOLD, fs, colour);
            x += Px.textWidth(ch, Px.Weight.SEMIBOLD, fs) + track;
        }
    }

    private void navItem(GuiGraphics g, Nav n, int x, int y, int w, int h) {
        int var10000 = n.kind();
        String key = "nav:" + var10000 + ":" + n.idx();
        boolean on = this.navOn(n);
        boolean hv = !this.under() && this.hover(x, y, w, h);
        if (hv && !on) {
            this.hot(key);
        }

        float hk = this.an(key, hv && !on ? 1.0F : 0.0F, 18.0F);
        float sk = this.tween(key + ":on", on ? 1.0F : 0.0F, 260.0F);
        String icon = n.kind() == 0 ? TABS[n.idx()].icon : n.icon();
        String text = Lang.tr(n.kind() == 0 ? TABS[n.idx()].title : n.label());
        int shift = Math.round((float) this.S(12.0) * hk);
        int c = Px.mix(Px.mix(sub(), T().text(), hk), -1, sk);
        if (hk > 0.01F) {
            float k = Px.fade;
            Px.fade = k * hk;
            glyph(g, "w.grip", x + this.S(3.0), y + (h - this.S(14.0)) / 2, this.S(14.0), faint());
            Px.fade = k;
        }

        int is = this.S(20.0);
        int fs = this.S(13.0);
        int tx = x + this.S(11.0) + shift;
        glyph(g, icon, tx, y + (h - is) / 2, is, Px.mix(Px.mix(faint(), sub(), hk), -1, sk));
        Px.text(g, text, tx + is + this.S(9.0), y + (h - fs) / 2, Px.Weight.MEDIUM, fs, c);
        if (n.kind() == 8 && !Social.incoming().isEmpty()) {
            // waiting friend requests: a small accent count
            String cnt = String.valueOf(Social.incoming().size());
            int cfs = this.S(10.0);
            int chh = this.S(16.0);
            int cww = Math.max(chh, Px.textWidth(cnt, Px.Weight.BOLD, cfs) + this.S(10.0));
            int ccx = x + w - this.S(10.0) - cww;
            int ccy = y + (h - chh) / 2;
            Px.round(g, ccx, ccy, cww, chh, chh / 2, on ? -1 : acc());
            Px.textCenter(g, cnt, ccx + cww / 2, ccy + (chh - cfs) / 2, Px.Weight.BOLD, cfs, on ? acc() : -1);
        }

        if (n.kind() == 6) {
            String code = Lang.english() ? "EN" : "RU";
            Px.textRight(
                    g,
                    code,
                    x + w - this.S(12.0),
                    y + (h - this.S(11.0)) / 2,
                    Px.Weight.SEMIBOLD,
                    this.S(11.0),
                    faint());
        }

        if (!this.under()) {
            this.region(x, y, w, h, () -> this.navClick(n));
        }
    }

    private static int navIndex(Nav target) {
        int k = 0;

        for (Nav[] grp : NAV) {
            for (Nav n : grp) {
                if (n == target) {
                    return k;
                }

                ++k;
            }
        }

        return -1;
    }

    private int navOnIndex() {
        int k = 0;

        for (Nav[] grp : NAV) {
            for (Nav n : grp) {
                if (this.navOn(n)) {
                    return k;
                }

                ++k;
            }
        }

        return -1;
    }

    private void navClick(Nav n) {
        int from = this.navOnIndex();
        int to = navIndex(n);
        this.pendingDx = 0;
        this.pendingDy = from >= 0 && to >= 0 ? Integer.signum(to - from) : 0;
        switch (n.kind()) {
            case 0:
                if (page != ArcanicMenu.Page.MODULES) {
                    page = ArcanicMenu.Page.MODULES;
                    this.searchFocused = false;
                }

                if (category != TABS[n.idx()] || !this.search.isEmpty()) {
                    category = TABS[n.idx()];
                    this.search = "";
                    this.searchFocused = false;
                    this.content("tab");
                }
                break;
            case 1:
                this.toPage(ArcanicMenu.Page.WARDROBE);
                break;
            case 2:
                this.toPage(ArcanicMenu.Page.PRESETS);
                break;
            case 3:
                UiSound.play("click");
                Minecraft.getInstance().setScreen(new HudEditorScreen(this));
                break;
            case 4:
                this.toPage(ArcanicMenu.Page.HELPERS);
            case 5:
            default:
                break;
            case 6:
                UiSound.play("click");
                List<String> languages = List.of("Русский", "English");
                Minecraft.getInstance()
                        .setScreen(PromptScreen.pick(
                                this, "Язык интерфейса", languages, Lang.english() ? 1 : 0, "Применить", (picked) -> {
                                    Modules.PREFS.language.set((String) languages.get(picked));
                                    Config.dirty();
                                    UiSound.play("select");
                                }));
                break;
            case 7:
                this.toPage(ArcanicMenu.Page.STUDIO);
                break;
            case 8:
                this.toPage(ArcanicMenu.Page.FRIENDS);
        }
    }

    private void toPage(Page to) {
        if (page != to) {
            if (page == ArcanicMenu.Page.STUDIO || to == ArcanicMenu.Page.STUDIO) {
                Studio.reset();
            }

            page = to;
            this.search = "";
            this.searchFocused = false;
            this.content("page");
        }
    }

    void openKnifeStudio(MeleeDesign skin) {
        if (KnifeStudio.design == skin) {
            KnifeStudio.close();
            UiSound.play("pop", 0.8F, 0.6F);
        } else {
            KnifeStudio.open(skin);
            UiSound.play("pop");
        }
    }

    private void studio(GuiGraphics g) {
        Studio.frame();
        Module sel = Studio.selected;
        int gap = this.S(12.0);
        int lw = this.S(170.0);
        int rw = this.S(272.0);
        int lx = this.cx0;
        int pvx = this.cx0 + lw + gap;
        int pvw = this.cw0 - lw - rw - gap * 2;
        int rx = pvx + pvw + gap;
        int t = this.top;
        int b = this.bottom;
        int ih = this.S(30.0);
        int y = t + this.S(2.0);
        String heading = null;

        for (Module m : Studio.effects()) {
            String group = Studio.group(m);
            if (!group.equals(heading)) {
                if (heading != null) {
                    y += this.S(8.0);
                }

                heading = group;
                Px.text(g, Lang.tr(group), lx + this.S(12.0), y, Px.Weight.SEMIBOLD, this.S(11.0), faint());
                y += this.S(20.0);
            }

            boolean on = m == sel;
            boolean hv = !this.under() && this.hover(lx, y, lw, ih);
            if (hv && !on) {
                this.hot("studio:" + m.id);
            }

            float hk = this.an("stH:" + m.id, hv && !on ? 1.0F : 0.0F, 18.0F);
            String var10001 = m.id;
            float sk = this.tween("stS:" + var10001, on ? 1.0F : 0.0F, 180.0F);
            if (sk > 0.01F) {
                Px.round(g, lx, y, lw, ih, this.S(8.0), Px.a(cardHi(), (int) (255.0F * sk)));
            } else if (hk > 0.01F) {
                Px.round(g, lx, y, lw, ih, this.S(8.0), Px.a(field(), (int) (255.0F * hk)));
            }

            if (sk > 0.01F) {
                Px.round(
                        g,
                        lx,
                        y + this.S(9.0),
                        Math.max(2, this.S(3.0)),
                        ih - this.S(18.0),
                        this.S(2.0),
                        Px.a(acc(), (int) (255.0F * sk)));
            }

            Px.text(
                    g,
                    Px.ellipsize(m.title(), Px.Weight.MEDIUM, this.S(13.0), lw - this.S(44.0)),
                    lx + this.S(14.0),
                    y + (ih - this.S(13.0)) / 2,
                    Px.Weight.MEDIUM,
                    this.S(13.0),
                    Px.mix(sub(), T().text(), Math.max(hk, sk)));
            int d = this.S(6.0);
            Px.round(g, lx + lw - this.S(14.0) - d, y + (ih - d) / 2, d, d, d / 2, m.enabled() ? acc() : edgeMedium());
            if (!this.under()) {
                this.region(lx, y, lw, ih, () -> {
                    if (Studio.selected != m) {
                        Studio.selected = m;
                        Studio.reset();
                        this.content("click");
                    }
                });
            }

            y += ih + this.S(3.0);
        }

        int ph2 = b - t;
        Px.round(g, pvx, t, pvw, ph2, this.S(12.0), light() ? -1644821 : -15921905);
        g.enableScissor(pvx, t, pvx + pvw, b);
        Px.glow(g, pvx + pvw / 10, t - ph2 / 10, pvw * 4 / 5, ph2, Px.a(acc(), light() ? 26 : 40));
        g.disableScissor();
        Px.outline(g, pvx, t, pvw, ph2, this.S(12.0), edgeMedium());
        Window win = Minecraft.getInstance().getWindow();
        double k = (double) win.getGuiScaledWidth() / (double) Math.max(1, win.getWidth());
        int inset = this.S(3.0);
        int gx0 = (int) Math.ceil((double) (pvx + inset) * k);
        int gy0 = (int) Math.ceil((double) (t + inset) * k);
        int gx1 = (int) Math.floor((double) (pvx + pvw - inset) * k);
        int gy1 = (int) Math.floor((double) (b - inset) * k);
        float scale = (float) Math.min((double) (gy1 - gy0) / 3.6, (double) (gx1 - gx0) / 6.2);
        if (gx1 > gx0 && gy1 > gy0) {
            ((GuiGraphicsAccess) g)
                    .arcanicvisuals$state()
                    .submitPicturesInPictureState(new Studio.Picture(gx0, gy0, gx1, gy1, scale));
        }

        g.nextStratum();
        int dim = Studio.dimOverlay();
        int flash = Studio.flashOverlay();
        if (dim != 0) {
            Px.round(g, pvx, t, pvw, ph2, this.S(12.0), dim);
        }

        if (flash != 0) {
            Px.round(g, pvx, t, pvw, ph2, this.S(12.0), flash);
        }

        String what = Lang.tr(Studio.caption(sel));
        int cfs = this.S(12.0);
        int cph = this.S(26.0);
        int cpw = Px.textWidth(what, Px.Weight.MEDIUM, cfs) + this.S(24.0);
        int cpy = b - this.S(12.0) - cph;
        Px.round(g, pvx + this.S(12.0), cpy, cpw, cph, cph / 2, light() ? 0xD0FFFFFF : 0xB8101013);
        Px.text(g, what, pvx + this.S(24.0), cpy + (cph - cfs) / 2, Px.Weight.MEDIUM, cfs, T().text());
        Px.textRight(
                g,
                Lang.tr("Тяни мышью — поворот"),
                pvx + pvw - this.S(16.0),
                cpy + (cph - this.S(11.0)) / 2,
                Px.Weight.MEDIUM,
                this.S(11.0),
                faint());
        if (!this.under()) {
            this.regions.add(new Region(
                    pvx,
                    t,
                    pvw,
                    ph2,
                    (Runnable) null,
                    (Runnable) null,
                    (ax, ay) -> {
                        long nowMs = System.currentTimeMillis();
                        if (nowMs - this.camDragAt > 120L) {
                            this.camX = ax;
                            this.camY = ay;
                        }

                        this.camDragAt = nowMs;
                        Studio.dragged();
                        Studio.yaw += (float) (ax - this.camX) * 0.4F;
                        Studio.pitch =
                                (float) Math.max(-10.0, Math.min(60.0, (double) Studio.pitch + (ay - this.camY) * 0.3));
                        this.camX = ax;
                        this.camY = ay;
                    },
                    "studio-cam"));
        }

        this.stageSwitch(g, pvx + this.S(12.0), t + this.S(12.0));
        Px.round(g, rx, t, rw, ph2, this.S(12.0), cardBg());
        Px.outline(g, rx, t, rw, ph2, this.S(12.0), edgeMedium());
        int pad = this.S(14.0);
        int iw = rw - pad * 2;
        int ix = rx + pad;
        Px.text(
                g,
                Px.ellipsize(sel.title(), Px.Weight.SEMIBOLD, this.S(15.0), iw),
                ix,
                t + this.S(14.0),
                Px.Weight.SEMIBOLD,
                this.S(15.0),
                T().text());
        Px.text(
                g,
                Px.ellipsize(Lang.tr(sel.description), Px.Weight.MEDIUM, this.S(11.0), iw),
                ix,
                t + this.S(34.0),
                Px.Weight.MEDIUM,
                this.S(11.0),
                faint());
        int ry = t + this.S(56.0);
        int rh = this.S(38.0);
        Px.round(g, ix - this.S(4.0), ry, iw + this.S(8.0), rh, this.S(8.0), field());
        Px.text(
                g,
                Lang.tr("Включён в игре"),
                ix + this.S(8.0),
                ry + (rh - this.S(13.0)) / 2,
                Px.Weight.MEDIUM,
                this.S(13.0),
                label());
        String var53 = sel.id;
        float onK = this.tween("on:" + var53, sel.enabled() ? 1.0F : 0.0F, 180.0F);
        boolean thv = !this.under() && this.hover(ix - this.S(4.0), ry, iw + this.S(8.0), rh);
        this.toggle(g, ix + iw - this.S(30.0) - this.S(4.0), ry, this.S(30.0), rh, onK, thv ? 1.0F : 0.0F);
        if (!this.under()) {
            this.region(ix - this.S(4.0), ry, iw + this.S(8.0), rh, () -> {
                if (!sel.enabled() && HolyFeatures.forbidden(sel)) {
                    HolyFeatures.deny(sel);
                } else {
                    sel.toggle();
                    UiSound.play(sel.enabled() ? "charge" : "drain");
                    Toasts.module(sel, false);
                    Config.dirty();
                }
            });
        }

        int st = ry + rh + this.S(12.0);
        int sb = b - this.S(8.0);
        Px.rect(g, rx, st - this.S(6.0), rw, Math.max(1, this.S(1.0)), edgeSmall());
        List<Setting<?>> list = params(sel);
        this.compact = true;
        int inner = 0;

        for (Setting<?> s : list) {
            inner += this.rowH(s, iw);
        }

        var53 = sel.id;
        float sc = (float) Math.round(this.scrolled("studio:" + var53, inner + this.S(8.0), st, sb));
        g.enableScissor(rx, st, rx + rw, sb);
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -sc);
        this.settings(g, sel, list, ix, st, iw, sc, st, sb, !this.under());
        g.pose().popMatrix();
        g.disableScissor();
        this.compact = false;
        g.nextStratum();
        this.scrollbar(
                g,
                rx + rw - this.S(8.0),
                st + this.S(4.0),
                sb - this.S(4.0),
                inner + this.S(8.0),
                sc,
                "studio:" + sel.id);
    }

    private void stageSwitch(GuiGraphics g, int x, int y) {
        String[] names = Studio.STAGES;
        int fs = this.S(11.0);
        int h = this.S(26.0);
        int pad = this.S(12.0);
        int inset = this.S(3.0);
        int[] ws = new int[names.length];
        int[] xs = new int[names.length];
        int total = inset * 2;

        for (int i = 0; i < names.length; ++i) {
            ws[i] = Px.textWidth(Lang.tr(names[i]), Px.Weight.MEDIUM, fs) + pad * 2;
            total += ws[i];
        }

        Px.round(g, x, y, total, h, h / 2, light() ? 0xD0FFFFFF : 0xB8101013);
        int cx = x + inset;

        for (int i = 0; i < names.length; ++i) {
            xs[i] = cx;
            cx += ws[i];
        }

        int chosen = Studio.stage;
        int plx = Math.round(this.tween("stagePlateX", (float) xs[chosen], 200.0F));
        int plw = Math.round(this.tween("stagePlateW", (float) ws[chosen], 200.0F));
        Px.round(g, plx, y + inset, plw, h - inset * 2, (h - inset * 2) / 2, acc());

        for (int i = 0; i < names.length; ++i) {
            boolean on = i == chosen;
            boolean hv = !this.under() && this.hover(xs[i], y, ws[i], h);
            if (hv && !on) {
                this.hot("stage:" + i);
            }

            float hk = this.an("stage:" + i, hv ? 1.0F : 0.0F, 18.0F);
            float ak = this.tween("stageOn:" + i, on ? 1.0F : 0.0F, 180.0F);
            int c = Px.mix(Px.mix(faint(), label(), hk), -1, ak);
            Px.textCenter(g, Lang.tr(names[i]), xs[i] + ws[i] / 2, y + (h - fs) / 2, Px.Weight.MEDIUM, fs, c);
            if (!this.under()) {
                int stage = i;
                this.region(xs[i], y, ws[i], h, () -> {
                    if (Studio.stage != stage) {
                        Studio.stage = stage;
                        UiSound.play("click");
                    }
                });
            }
        }
    }

    int valueBox(GuiGraphics g, String v, int right, int y, boolean accent, boolean hv) {
        int bw = Math.max(
                this.S(this.compact ? 44.0 : 52.0),
                Px.textWidth(v, Px.Weight.MEDIUM, this.S(12.0)) + this.S(this.compact ? 18.0 : 22.0));
        int bh = this.S(this.compact ? 26.0 : 30.0);
        int bx = right - bw;
        Px.round(g, bx, y, bw, bh, this.S(6.0), hv ? cardHi() : field());
        if (accent) {
            Px.outline(g, bx, y, bw, bh, this.S(6.0), Px.a(acc(), 200));
        }

        Px.textCenter(
                g,
                v,
                bx + bw / 2,
                y + (bh - this.S(12.0)) / 2,
                Px.Weight.MEDIUM,
                this.S(12.0),
                accent ? acc() : label());
        return bw;
    }

    int rowH(Setting<?> s, int w) {
        if (s instanceof Setting.Num) {
            return this.S(this.compact ? 44.0 : 48.0);
        } else if (s instanceof Setting.Items) {
            Setting.Items it = (Setting.Items) s;
            int lines = (it.entries.size() + 1) / 2;
            return this.S(32.0) + lines * this.S(54.0) + (lines - 1) * this.S(8.0) + this.S(14.0);
        } else {
            return this.S(this.compact ? 34.0 : 38.0);
        }
    }

    int settings(
            GuiGraphics g, Module m, List<Setting<?>> list, int x, int y, int w, float sc, int t, int b, boolean live) {
        for (int i = 0; i < list.size(); ++i) {
            Setting<?> s = (Setting) list.get(i);
            int h = this.rowH(s, w);
            g.nextStratum();
            if ((float) y - sc + (float) h >= (float) t && (float) y - sc <= (float) b) {
                this.row(g, m, s, x, y, w, h, sc, t, b, live);
            }

            y += h;
        }

        return y;
    }

    private void row(
            GuiGraphics g, Module m, Setting<?> s, int x, int y, int w, int h, float sc, int t, int b, boolean live) {
        String id = m.id + ":" + s.key;
        boolean hv = live
                && this.my >= (double) t
                && this.my < (double) b
                && this.hover(x, Math.round((float) y - sc), w, h);
        if (hv) {
            this.hot("set:" + id);
        }

        float hk = this.an("row:" + id, hv ? 1.0F : 0.0F, 18.0F);
        int label = Px.mix(label(), T().text(), hk);
        String name = Px.ellipsize(Lang.tr(s.name), Px.Weight.MEDIUM, this.S(13.0), w - this.S(110.0));
        if (s instanceof Setting.Items it) {
            this.items(g, m, it, x, y, w, sc, t, b, live);
        } else {
            if (s instanceof Setting.Bool bo) {
                float on = this.tween("b:" + id, bo.on() ? 1.0F : 0.0F, 200.0F);
                Px.text(
                        g,
                        name,
                        x,
                        y + (h - this.S(13.0)) / 2,
                        Px.Weight.MEDIUM,
                        this.S(13.0),
                        Px.mix(Px.mix(sub(), label(), on), T().text(), hk));
                this.toggle(g, x + w - this.S(30.0), y, this.S(30.0), h, on, hk);
                if (live) {
                    this.clipped(
                            x,
                            y,
                            w,
                            h,
                            sc,
                            t,
                            b,
                            () -> {
                                bo.toggle();
                                UiSound.play(bo.on() ? "toggle_on" : "toggle_off");
                                Config.dirty();
                            },
                            (Runnable) null);
                }
            } else if (s instanceof Setting.Num n) {
                boolean drag = this.dragging != null && id.equals(this.dragging.id());
                int fsN = this.S(13.0);
                int ly = y + this.S(7.0);
                String vt = n.text();
                int vw = Px.textWidth(vt, Px.Weight.MEDIUM, this.S(12.0));
                float kh = this.an(
                        "nk:" + id,
                        drag
                                ? 1.0F
                                : (hv
                                                && this.hover(
                                                        x - this.S(10.0),
                                                        Math.round((float) (y + h - this.S(28.0)) - sc),
                                                        w + this.S(20.0),
                                                        this.S(30.0))
                                        ? 0.6F
                                        : 0.0F),
                        20.0F);
                Px.text(g, vt, x + w - vw, ly + this.S(1.0), Px.Weight.MEDIUM, this.S(12.0), acc());
                Px.text(
                        g,
                        Px.ellipsize(name, Px.Weight.MEDIUM, fsN, w - vw - this.S(12.0)),
                        x,
                        ly,
                        Px.Weight.MEDIUM,
                        fsN,
                        label);
                int th = Math.max(2, this.S(3.0));
                int ks = this.S(10.0) / 2 * 2;
                int kr = ks / 2;
                int ty = y + h - this.S(this.compact ? 13.0 : 15.0);
                float f = drag
                        ? (Float) this.dragRaw.getOrDefault(id, (float) n.fraction())
                        : this.an("n:" + id, (float) n.fraction(), 22.0F);
                int kx = x + kr + Math.round((float) (w - kr * 2) * f);
                int kcy = ty + th / 2;
                Px.round(g, x, ty, w, th, th / 2, field());
                Px.round(g, x, ty, Math.max(th, kx - x), th, th / 2, acc());
                int knob = (ks + Math.round((float) this.S(3.0) * kh)) / 2 * 2;
                Px.round(g, kx - knob / 2, kcy - knob / 2, knob, knob, knob / 2, -1);
                if (live) {
                    int y0 = (int) Math.max((float) t, (float) y - sc);
                    int y1 = (int) Math.min((float) b, (float) (y + h) - sc);
                    if (y1 > y0) {
                        this.regions.add(new Region(
                                x - this.S(10.0),
                                y0,
                                w + this.S(20.0),
                                y1 - y0,
                                (Runnable) null,
                                (Runnable) null,
                                (at, ay) -> {
                                    float raw = (float) Math.max(
                                            0.0,
                                            Math.min(1.0, (at - (double) x - (double) kr) / (double) (w - kr * 2)));
                                    double before = (Double) n.get();
                                    n.set(n.min + (n.max - n.min) * (double) raw);
                                    if ((Double) n.get() != before) {
                                        UiSound.play("tick", 0.75F + (float) n.fraction() * 0.8F, 0.55F);
                                    }

                                    this.dragRaw.put(id, raw);
                                    this.anim.set("n:" + id, raw);
                                    Config.dirty();
                                },
                                id));
                    }
                }
            } else if (s instanceof Setting.Mode md) {
                Px.text(g, name, x, y + (h - this.S(13.0)) / 2, Px.Weight.MEDIUM, this.S(13.0), label);
                String cur = Lang.tr((String) md.get());
                int fs = this.S(12.0);
                int bh = this.S(24.0);
                int aw = this.S(10.0);
                int bw = Math.min(w / 2, Px.textWidth(cur, Px.Weight.MEDIUM, fs) + this.S(20.0) + aw + this.S(6.0));
                int bx = x + w - bw;
                int by = y + (h - bh) / 2;
                boolean open = this.pop == md;
                Px.round(g, bx, by, bw, bh, this.S(6.0), Px.mix(field(), cardHi(), Math.max(hk, open ? 1.0F : 0.0F)));
                Px.text(
                        g,
                        Px.ellipsize(cur, Px.Weight.MEDIUM, fs, bw - this.S(20.0) - aw - this.S(6.0)),
                        bx + this.S(10.0),
                        by + (bh - fs) / 2,
                        Px.Weight.MEDIUM,
                        fs,
                        T().text());
                glyph(
                        g,
                        "chevron-down",
                        bx + bw - this.S(10.0) - aw,
                        by + (bh - aw) / 2,
                        aw,
                        Px.mix(faint(), sub(), hk));
                if (live) {
                    this.clipped(
                            x,
                            y,
                            w,
                            h,
                            sc,
                            t,
                            b,
                            () -> {
                                this.openMode(
                                        md,
                                        bx + bw,
                                        Math.round((float) by - sc) + bh + this.S(4.0),
                                        Math.max(bw, this.S(180.0)));
                                UiSound.play("pop");
                            },
                            (Runnable) null);
                }
            } else if (s instanceof Setting.Text tx) {
                Px.text(g, name, x, y + (h - this.S(13.0)) / 2, Px.Weight.MEDIUM, this.S(13.0), label);
                int is = this.S(12.0);
                int room =
                        Math.max(this.S(30.0), w - Px.textWidth(name, Px.Weight.MEDIUM, this.S(13.0)) - this.S(40.0));
                String v = ((String) tx.get()).isEmpty() ? Lang.tr(tx.hint) : (String) tx.get();
                v = Px.ellipsize(v, Px.Weight.MEDIUM, this.S(11.0), room);
                glyph(g, "pen", x + w - is, y + (h - is) / 2, is, Px.mix(faint(), acc(), hk));
                Px.textRight(
                        g,
                        v,
                        x + w - is - this.S(8.0),
                        y + (h - this.S(11.0)) / 2,
                        Px.Weight.MEDIUM,
                        this.S(11.0),
                        faint());
                if (live) {
                    this.clipped(
                            x,
                            y,
                            w,
                            h,
                            sc,
                            t,
                            b,
                            () -> {
                                UiSound.play("pop");
                                Minecraft.getInstance()
                                        .setScreen(PromptScreen.field(this, s.name, tx.hint, "Сохранить", (v2) -> {
                                                    tx.set(v2);
                                                    Config.dirty();
                                                })
                                                .value((String) tx.get())
                                                .limit(tx.max));
                            },
                            (Runnable) null);
                }
            } else if (s instanceof Setting.Key key) {
                Px.text(g, name, x, y + (h - S(13)) / 2, Px.Weight.MEDIUM, S(13), label);
                boolean waiting = bindingKey == key;
                String cap = waiting ? Lang.tr("Нажми клавишу…") : Keys.label(key.code());
                int fs = S(11);
                int bh = S(22);
                int bw = Px.textWidth(cap, Px.Weight.SEMIBOLD, fs) + S(20);
                int bx = x + w - bw;
                int by = y + (h - bh) / 2;
                Px.round(g, bx, by, bw, bh, S(6), waiting ? acc() : Px.mix(field(), cardHi(), hk));
                Px.text(g, cap, bx + S(10), by + (bh - fs) / 2, Px.Weight.SEMIBOLD, fs, waiting ? -1 : label());
                if (live) {
                    clipped(
                            x,
                            y,
                            w,
                            h,
                            sc,
                            t,
                            b,
                            () -> {
                                bindingKey = waiting ? null : key;
                                binding = null;
                                UiSound.play("click");
                            },
                            () -> {
                                key.set(-1);
                                bindingKey = null;
                                Config.dirty();
                                UiSound.play("reset");
                            });
                }
            } else if (s instanceof Setting.Action ac) {
                Px.text(g, name, x, y + (h - this.S(13.0)) / 2, Px.Weight.MEDIUM, this.S(13.0), label);
                String lb = Lang.tr(ac.label);
                int bh = this.S(22.0);
                int bw = Px.textWidth(lb, Px.Weight.SEMIBOLD, this.S(11.0)) + this.S(30.0);
                int bx = x + w - bw;
                int by = y + (h - bh) / 2;
                Px.round(g, bx, by, bw, bh, this.S(6.0), Px.mix(field(), acc(), hk));
                Px.text(
                        g,
                        lb,
                        bx + this.S(10.0),
                        by + (bh - this.S(11.0)) / 2,
                        Px.Weight.SEMIBOLD,
                        this.S(11.0),
                        Px.mix(label(), -1, hk));
                glyph(
                        g,
                        "chev-right",
                        bx + bw - this.S(16.0),
                        by + (bh - this.S(10.0)) / 2,
                        this.S(10.0),
                        Px.mix(faint(), -1, hk));
                if (live) {
                    this.clipped(
                            x,
                            y,
                            w,
                            h,
                            sc,
                            t,
                            b,
                            () -> {
                                UiSound.play("click");
                                ac.run.run();
                            },
                            (Runnable) null);
                }
            } else if (s instanceof Setting.Colour c) {
                Px.text(g, name, x, y + (h - this.S(13.0)) / 2, Px.Weight.MEDIUM, this.S(13.0), label);
                int col = c.argb() | 0xFF000000;
                int sw = this.S(22.0);
                int sx = x + w - sw;
                int syy = y + (h - sw) / 2;
                String hex = c.followsTheme() ? Lang.tr("Как в теме") : String.format("#%06X", col & 0xFFFFFF);
                Px.textRight(
                        g,
                        hex,
                        sx - this.S(8.0),
                        y + (h - this.S(11.0)) / 2,
                        Px.Weight.MEDIUM,
                        this.S(11.0),
                        Px.mix(faint(), sub(), hk));
                Px.round(g, sx, syy, sw, sw, this.S(7.0), Px.mix(field(), cardHi(), hk));
                Px.outline(g, sx, syy, sw, sw, this.S(7.0), edgeMedium());
                int cs0 = sw - this.S(7.0);
                Px.round(g, sx + (sw - cs0) / 2, syy + (sw - cs0) / 2, cs0, cs0, this.S(4.0), col);
                if (live) {
                    this.clipped(
                            x,
                            y,
                            w,
                            h,
                            sc,
                            t,
                            b,
                            () -> {
                                this.picker = (new ColourPicker(s.name, c)).at(this.mx, this.my);
                                UiSound.play("pop");
                            },
                            (Runnable) null);
                }
            }
        }
    }

    static ItemStack icon(String id) {
        return (ItemStack)
                ICONS.computeIfAbsent(id, (k) -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(k))));
    }

    private void items(
            GuiGraphics g, Module m, Setting.Items it, int x, int y, int w, float sc, int t, int b, boolean live) {
        String id = m.id + ":" + it.key;
        Px.text(g, Lang.tr(it.name), x, y + this.S(9.0), Px.Weight.MEDIUM, this.S(13.0), label());
        Px.textRight(
                g,
                it.count() + " " + Lang.tr("из") + " " + it.entries.size(),
                x + w,
                y + this.S(10.0),
                Px.Weight.MEDIUM,
                this.S(11.0),
                faint());
        int gap = this.S(8.0);
        int cw = (w - gap) / 2;
        int ch = this.S(54.0);
        int y0 = y + this.S(32.0);

        for (int i = 0; i < it.entries.size(); ++i) {
            Setting.Items.Entry e = (Setting.Items.Entry) it.entries.get(i);
            String k = id + ":" + e.key();
            int cx = x + i % 2 * (cw + gap);
            int cy = y0 + i / 2 * (ch + gap);
            boolean hv = live
                    && this.my >= (double) t
                    && this.my < (double) b
                    && this.hover(cx, Math.round((float) cy - sc), cw, ch);
            if (hv) {
                this.hot("item:" + k);
            }

            float hk = this.an("itH:" + k, hv ? 1.0F : 0.0F, 18.0F);
            float on = this.tween("itO:" + k, it.on(e.key()) ? 1.0F : 0.0F, 200.0F);
            g.nextStratum();
            Px.round(g, cx, cy, cw, ch, this.S(10.0), Px.mix(cardBg(), cardHi(), hk));
            Px.outline(g, cx, cy, cw, ch, this.S(10.0), Px.mix(edgeMedium(), Px.a(acc(), 150), on));
            int ts = this.S(34.0);
            int tx = cx + this.S(10.0);
            int ty = cy + (ch - ts) / 2;
            int is = this.S(22.0);
            Px.round(g, tx, ty, ts, ts, this.S(8.0), Px.mix(field(), Px.mix(field(), acc(), 0.14F), on));
            g.pose().pushMatrix();
            g.pose().translate((float) tx + (float) (ts - is) / 2.0F, (float) ty + (float) (ts - is) / 2.0F);
            g.pose().scale((float) is / 16.0F, (float) is / 16.0F);
            g.renderItem(icon(e.icon()), 0, 0);
            g.pose().popMatrix();
            g.nextStratum();
            if (on < 0.999F) {
                Px.round(g, tx, ty, ts, ts, this.S(8.0), Px.a(field(), (int) (150.0F * (1.0F - on))));
            }

            Setting.Colour col = e.colour();
            int ds = this.S(11.0);
            int dx = tx + ts - ds + this.S(3.0);
            int dy = ty + ts - ds + this.S(3.0);
            boolean dh = col != null
                    && hv
                    && this.hover(
                            dx - this.S(3.0),
                            Math.round((float) dy - sc - (float) this.S(3.0)),
                            ds + this.S(6.0),
                            ds + this.S(6.0));
            if (col != null) {
                Px.round(
                        g,
                        dx - this.S(2.0),
                        dy - this.S(2.0),
                        ds + this.S(4.0),
                        ds + this.S(4.0),
                        (ds + this.S(4.0)) / 2,
                        Px.mix(cardBg(), cardHi(), hk));
                int dc = col.argb() | 0xFF000000;
                Px.round(g, dx, dy, ds, ds, ds / 2, Px.mix(Px.mix(faint(), dc, 0.35F), dc, on));
                if (dh) {
                    Px.outline(
                            g,
                            dx - this.S(2.0),
                            dy - this.S(2.0),
                            ds + this.S(4.0),
                            ds + this.S(4.0),
                            (ds + this.S(4.0)) / 2,
                            label());
                }
            }

            int bs = this.S(16.0);
            int nx = tx + ts + this.S(10.0);
            int tw = cx + cw - this.S(10.0) - bs - this.S(8.0) - nx;
            Px.text(
                    g,
                    Px.ellipsize(Lang.tr(e.name()), Px.Weight.SEMIBOLD, this.S(12.0), tw),
                    nx,
                    cy + this.S(12.0),
                    Px.Weight.SEMIBOLD,
                    this.S(12.0),
                    Px.mix(sub(), T().text(), Math.max(on, hk)));
            Px.text(
                    g,
                    Px.ellipsize(Lang.tr(e.hint()), Px.Weight.MEDIUM, this.S(10.0), cx + cw - this.S(10.0) - nx),
                    nx,
                    cy + this.S(30.0),
                    Px.Weight.MEDIUM,
                    this.S(10.0),
                    faint());
            this.checkbox(g, cx + cw - this.S(10.0) - bs, cy + this.S(10.0), bs, on, hk);
            if (live) {
                this.clipped(
                        cx,
                        cy,
                        cw,
                        ch,
                        sc,
                        t,
                        b,
                        () -> {
                            it.toggle(e.key());
                            UiSound.play(it.on(e.key()) ? "toggle_on" : "toggle_off");
                            Config.dirty();
                        },
                        (Runnable) null);
                if (col != null) {
                    this.clipped(
                            dx - this.S(4.0),
                            dy - this.S(4.0),
                            ds + this.S(8.0),
                            ds + this.S(8.0),
                            sc,
                            t,
                            b,
                            () -> {
                                this.picker = (new ColourPicker(e.name(), col)).at(this.mx, this.my);
                                UiSound.play("pop");
                            },
                            (Runnable) null);
                }
            }
        }
    }

    private void openMode(Setting.Mode md, int right, int top, int width) {
        this.pop = md;
        this.popW = width;
        this.popX = Math.max(this.S(8.0), right - width);
        this.popY = top;
        this.popAt = this.now;
        this.scroll.remove("pop:" + md.key);
    }

    private void afterMode(Setting.Mode md) {
        if (md == Modules.HANDS.preset) {
            Modules.HANDS.applyPreset();
        } else if (md == Modules.HITSOUND.sound) {
            Modules.HITSOUND.play(Modules.HITSOUND.current());
        } else if (md == Modules.KILLSOUND.sound) {
            Modules.KILLSOUND.play(Modules.KILLSOUND.current());
        }

        Config.dirty();
    }

    private void popover(GuiGraphics g) {
        Setting.Mode md = this.pop;
        float a = ease((float) (this.now - this.popAt) / 170.0F);
        int ih = this.S(30.0);
        int rows = Math.min(md.modes.size(), 8);
        int h = ih * rows + this.S(10.0);
        int x = this.popX;
        int y = Math.min(this.popY, Px.height() - h - this.S(12.0));
        String key = "pop:" + md.key;
        int max = Math.max(0, (md.modes.size() - rows) * ih);
        float msc = Math.max(0.0F, Math.min((float) max, (Float)
                this.scroll.getOrDefault(key, (float) Math.max(0, Math.min(max, (md.index() - rows / 2) * ih)))));
        this.scroll.put(key, msc);
        float k = Px.fade;
        Px.fade = k * a;
        g.pose().pushMatrix();
        if (a < 0.999F) {
            g.pose().translate((float) (x + this.popW), (float) y);
            g.pose().scale(0.95F + 0.05F * a, 0.95F + 0.05F * a);
            g.pose().translate((float) (-(x + this.popW)), (float) (-y) + (1.0F - a) * (float) (-this.S(6.0)));
        }

        Px.shadow(g, x, y + this.S(4.0), this.popW, h, this.S(14.0), Px.a(0, light() ? 30 : 110));
        Px.round(g, x, y, this.popW, h, this.S(8.0), cardBg());
        Px.outline(g, x, y, this.popW, h, this.S(8.0), edgeMedium());
        g.enableScissor(x, y + this.S(5.0), x + this.popW, y + h - this.S(5.0));

        for (int i = 0; i < md.modes.size(); ++i) {
            String mode = (String) md.modes.get(i);
            int iy = Math.round((float) (y + this.S(5.0) + i * ih) - msc);
            if (iy + ih >= y && iy <= y + h) {
                boolean hv = this.hover(x + this.S(5.0), iy, this.popW - this.S(10.0), ih)
                        && this.my > (double) y
                        && this.my < (double) (y + h);
                if (hv) {
                    this.hot("popi:" + i);
                }

                float hk = this.an("popH:" + i, hv ? 1.0F : 0.0F, 20.0F);
                if (hk > 0.01F) {
                    Px.round(g, x + this.S(5.0), iy, this.popW - this.S(10.0), ih, this.S(6.0), Px.a(cardHi(), (int)
                            (255.0F * hk)));
                }

                boolean cur = md.is(mode);
                Px.text(
                        g,
                        Px.ellipsize(Lang.tr(mode), Px.Weight.MEDIUM, this.S(12.0), this.popW - this.S(48.0)),
                        x + this.S(14.0),
                        iy + (ih - this.S(12.0)) / 2,
                        Px.Weight.MEDIUM,
                        this.S(12.0),
                        cur ? acc() : Px.mix(label(), T().text(), hk));
                if (cur) {
                    glyph(
                            g,
                            "check",
                            x + this.popW - this.S(14.0) - this.S(13.0),
                            iy + (ih - this.S(13.0)) / 2,
                            this.S(13.0),
                            acc());
                }

                int y0 = Math.max(iy, y + this.S(5.0));
                int y1 = Math.min(iy + ih, y + h - this.S(5.0));
                if (y1 > y0) {
                    this.regions.add(new Region(
                            x + this.S(5.0),
                            y0,
                            this.popW - this.S(10.0),
                            y1 - y0,
                            () -> {
                                md.set(mode);
                                this.pop = null;
                                this.afterMode(md);
                                UiSound.play("click");
                            },
                            (Runnable) null,
                            (Drag) null,
                            "pop"));
                }
            }
        }

        g.disableScissor();
        g.pose().popMatrix();
        Px.fade = k;
    }

    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double x = Px.px(e.x());
        double y = Px.px(e.y());
        if (this.picker != null) {
            this.picker.click(x, y, e.button());
            return true;
        } else {
            this.presetsPage.typing = false;
            if (bindingKey != null && e.button() >= 2) {
                bindingKey.set(Keys.MOUSE + e.button());
                bindingKey = null;
                Config.dirty();
                UiSound.play("select");
                return true;
            } else if (this.binding != null && e.button() >= 2) {
                this.binding.bind = 1000 + e.button();
                this.binding = null;
                Config.dirty();
                UiSound.play("select");
                return true;
            } else if (this.pop != null) {
                for (int i = this.regions.size() - 1; i >= 0; --i) {
                    Region r = (Region) this.regions.get(i);
                    if ("pop".equals(r.id()) && r.in(x, y)) {
                        r.click().run();
                        return true;
                    }
                }

                this.pop = null;
                UiSound.play("pop", 0.8F, 0.6F);
                return true;
            } else {
                for (int i = this.regions.size() - 1; i >= 0; --i) {
                    Region r = (Region) this.regions.get(i);
                    if (r.in(x, y) && !"pop".equals(r.id())) {
                        if (r.drag() != null && e.button() == 0) {
                            this.dragging = r;
                            UiSound.play("grab", 1.0F, 0.6F);
                            r.drag().to(x, y);
                            return true;
                        }

                        if (e.button() == 0 && r.click() != null) {
                            this.searchFocused = false;
                            r.click().run();
                            return true;
                        }

                        if (e.button() == 1 && r.alt() != null) {
                            r.alt().run();
                            return true;
                        }
                    }
                }

                this.searchFocused = false;
                this.binding = null;
                if (this.popMod != null
                        && (!(x >= (double) this.pmX)
                                || !(x < (double) (this.pmX + this.pmW))
                                || !(y >= (double) this.pmY)
                                || !(y < (double) (this.pmY + this.pmH)))) {
                    this.popMod = null;
                    UiSound.play("pop", 0.8F, 0.6F);
                }

                return true;
            }
        }
    }

    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        double x = Px.px(e.x());
        double y = Px.px(e.y());
        if (this.picker != null) {
            this.picker.drag(x, y);
            return true;
        } else {
            if (this.dragging != null) {
                this.dragging.drag().to(x, y);
            }

            return true;
        }
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        if (this.picker != null) {
            this.picker.release();
            return true;
        } else {
            if (this.dragging != null) {
                UiSound.play("drop", 1.0F, 0.5F);
            }

            this.dragging = null;
            this.dragRaw.clear();
            return true;
        }
    }

    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (this.picker != null) {
            this.picker.wheel(Px.px(x), Px.px(y), sy);
            return true;
        } else {
            Window win = Minecraft.getInstance().getWindow();
            if (!InputConstants.isKeyDown(win, 341) && !InputConstants.isKeyDown(win, 345)) {
                if (this.pop != null) {
                    String k = "pop:" + this.pop.key;
                    this.scroll.put(k, (Float) this.scroll.getOrDefault(k, 0.0F) - (float) sy * (float) this.S(30.0));
                    return true;
                } else if (this.popMod != null
                        && this.mx >= (double) this.pmX
                        && this.mx < (double) (this.pmX + this.pmW)
                        && this.my >= (double) this.pmY
                        && this.my < (double) (this.pmY + this.pmH)) {
                    String p = "pm:" + this.popMod.id;
                    this.scroll.put(p, (Float) this.scroll.getOrDefault(p, 0.0F) - (float) sy * (float) this.S(60.0));
                    return true;
                } else if (this.knifeStudio.over(this.mx, this.my)) {
                    this.scroll.put(
                            "ks", (Float) this.scroll.getOrDefault("ks", 0.0F) - (float) sy * (float) this.S(60.0));
                    return true;
                } else {
                    String var10000;
                    switch (page.ordinal()) {
                        case 0 -> var10000 = "c:" + String.valueOf(this.search.isEmpty() ? category : "search");
                        case 1 -> var10000 = "w:" + wardrobe;
                        case 2 -> var10000 = "presets" + this.presetsPage.tab;
                        case 3 -> var10000 = "studio:" + Studio.selected.id;
                        case 4 ->
                            var10000 = helperServer == 1
                                    ? (helperTab == 0 ? "c:ft" : "ftev")
                                    : (helperTab == 0 ? "c:hw" : "hwev");
                        case 5 -> var10000 = this.friendsPage.scrollKey();
                        case 6 -> var10000 = this.crosshairPage.scrollKey();
                        default -> throw new MatchException((String) null, (Throwable) null);
                    }

                    String k = var10000;
                    this.scroll.put(k, (Float) this.scroll.getOrDefault(k, 0.0F) - (float) sy * (float) this.S(76.0));
                    return true;
                }
            } else {
                Modules.PREFS.scale.set((Double) Modules.PREFS.scale.get() + (double) (sy > 0.0 ? 5 : -5));
                Config.dirty();
                return true;
            }
        }
    }

    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (this.picker != null) {
            this.picker.key(e);
            return true;
        } else if (bindingKey != null) {
            if (k != 256) {
                bindingKey.set(k == 259 || k == 261 ? -1 : k);
                Config.dirty();
                UiSound.play(k == 259 || k == 261 ? "reset" : "select");
            }
            bindingKey = null;
            return true;
        } else if (this.binding != null) {
            if (k == 256) {
                this.binding = null;
                return true;
            } else if (k != 259 && k != 261) {
                this.binding.bind = k;
                this.binding = null;
                Config.dirty();
                UiSound.play("select");
                return true;
            } else {
                this.binding.bind = -1;
                this.binding = null;
                Config.dirty();
                UiSound.play("reset");
                return true;
            }
        } else if (page == ArcanicMenu.Page.PRESETS && this.presetsPage.key(k, e.modifiers())) {
            return true;
        } else if (k == 256 && page == ArcanicMenu.Page.FRIENDS && this.pop == null && this.friendsPage.back()) {
            return true;
        } else if (k == 256 && page == ArcanicMenu.Page.CROSSHAIR && this.pop == null) {
            this.toPage(ArcanicMenu.Page.MODULES); // back to the modules the studio was opened from
            UiSound.play("page");
            return true;
        } else if (k == 256) {
            if (this.pop != null) {
                this.pop = null;
                return true;
            } else if (this.popMod != null && this.popup.itemPage != null) {
                this.popup.openItem((String) null);
                UiSound.play("pop", 0.9F, 0.6F);
                return true;
            } else if (this.popMod != null) {
                this.popMod = null;
                UiSound.play("pop", 0.8F, 0.6F);
                return true;
            } else if (KnifeStudio.design != null) {
                KnifeStudio.close();
                UiSound.play("pop", 0.8F, 0.6F);
                return true;
            } else if (!this.searchFocused && this.search.isEmpty()) {
                this.onClose();
                return true;
            } else {
                this.searchFocused = false;
                if (!this.search.isEmpty()) {
                    this.search = "";
                    this.content("click");
                }

                return true;
            }
        } else if ((e.modifiers() & 2) == 0 || k != 61 && k != 334 && k != 45 && k != 333 && k != 48 && k != 320) {
            if (k == 70 && (e.modifiers() & 2) != 0) {
                this.toModules();
                this.searchFocused = true;
                UiSound.play("click", 1.2F, 0.6F);
                return true;
            } else if (!this.searchFocused
                    && page == ArcanicMenu.Page.MODULES
                    && this.search.isEmpty()
                    && (k == 263 || k == 262)
                    && this.popMod == null) {
                int i = 0;

                for (int j = 0; j < TABS.length; ++j) {
                    if (TABS[j] == category) {
                        i = j;
                    }
                }

                this.tabClick(Math.floorMod(i + (k == 262 ? 1 : -1), TABS.length));
                return true;
            } else if (this.searchFocused) {
                if (k == 259 && !this.search.isEmpty()) {
                    this.search = this.search.substring(0, this.search.length() - 1);
                    UiSound.play("type", 0.9F, 0.3F);
                    if (this.search.isEmpty()) {
                        this.contentAt = this.now;
                    }

                    return true;
                } else if (k != 257 && k != 335) {
                    return true;
                } else {
                    List<Module> v = this.visible();
                    if (!v.isEmpty() && !this.search.isEmpty()) {
                        Module m = (Module) v.get(0);
                        if (n(m) > 0) {
                            this.openPopup(
                                    m, this.cx0, this.top + this.S(2.0), (this.cw0 - this.S(12.0)) / 2, this.headH());
                        }
                    }

                    this.searchFocused = false;
                    return true;
                }
            } else {
                return super.keyPressed(e);
            }
        } else {
            boolean reset = k == 48 || k == 320;
            boolean up = k == 61 || k == 334;
            Modules.PREFS.scale.set(reset ? 100.0 : (Double) Modules.PREFS.scale.get() + (double) (up ? 10 : -10));
            Config.dirty();
            UiSound.play("click");
            return true;
        }
    }

    private void toModules() {
        if (page != ArcanicMenu.Page.MODULES) {
            page = ArcanicMenu.Page.MODULES;
            this.content((String) null);
        }
    }

    public boolean charTyped(CharacterEvent e) {
        String s = e.codepointAsString();
        if (this.picker != null) {
            this.picker.character(e);
            return true;
        } else if (!s.isEmpty() && s.codePointAt(0) >= 32 && this.pop == null && this.binding == null) {
            if (page == ArcanicMenu.Page.PRESETS && this.presetsPage.typing) {
                this.presetsPage.type(s);
                return true;
            } else {
                if (!this.searchFocused) {
                    if (!Character.isLetterOrDigit(s.codePointAt(0))) {
                        return false;
                    }

                    this.toModules();
                    this.popMod = null;
                    this.searchFocused = true;
                }

                if (this.search.length() < 40) {
                    if (this.search.isEmpty()) {
                        this.contentAt = this.now;
                    }

                    this.search = this.search + s;
                    UiSound.play("type", 1.2F, 0.35F);
                }

                return true;
            }
        } else {
            return false;
        }
    }

    public void onClose() {
        if (this.picker != null) {
            this.picker.close();
        } else {
            if (page == ArcanicMenu.Page.STUDIO) {
                Studio.reset();
            }

            KnifeStudio.close();
            Config.save();
            if (!this.closing) {
                this.closing = true;
                UiSound.play("close");
                Toasts.menu(false);
            } else {
                super.onClose();
            }
        }
    }

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (Modules.PREFS.blur.on() && Minecraft.getInstance().level != null) {
            this.renderBlurredBackground(g);
        } else if (Minecraft.getInstance().level == null) {
            super.renderBackground(g, mouseX, mouseY, pt);
        }
    }

    static {
        TABS = new Category[] {Category.VISUALS, Category.UTILITY, Category.HUD};
        WARD = new String[] {"Плащи", "Ножи", "Оружие", "Головные уборы", "Рюкзаки", "Крылья"};
        WARD_ICONS = new String[] {"w.cape", "w.knife", "w.crown", "w.bag", "w.wings"};
        category = Category.VISUALS;
        page = ArcanicMenu.Page.MODULES;
        wardrobe = 0;
        NAV = new Nav[][] {
            {
                new Nav(0, 0, (String) null, (String) null),
                new Nav(0, 1, (String) null, (String) null),
                new Nav(0, 2, (String) null, (String) null)
            },
            {
                new Nav(1, 0, "cosmetics-s", "Cosmetics"),
                new Nav(7, 0, "w.wizard-hat", "Effects Studio"),
                new Nav(2, 0, "presets-s", "Configs"),
                new Nav(8, 0, "users", "Friends")
            },
            {new Nav(3, 0, "hud-s", "HUD Editor"), new Nav(4, 0, "server-s", "Helpers")},
            {new Nav(6, 0, "flag-s", "Language")}
        };
        GROUPS = new String[] {"Sections", "Content", "Tools", "Client"};
        ICONS = new HashMap<>();
    }

    private static enum Page {
        MODULES,
        WARDROBE,
        PRESETS,
        STUDIO,
        HELPERS,
        FRIENDS,
        CROSSHAIR;
    }

    private static record Region(int x, int y, int w, int h, Runnable click, Runnable alt, Drag drag, String id) {
        boolean in(double px, double py) {
            return px >= (double) this.x
                    && px < (double) (this.x + this.w)
                    && py >= (double) this.y
                    && py < (double) (this.y + this.h);
        }
    }

    private static record Nav(int kind, int idx, String icon, String label) {}

    interface Drag {
        void to(double var1, double var3);
    }
}
