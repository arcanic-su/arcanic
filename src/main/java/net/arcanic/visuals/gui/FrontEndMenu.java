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

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Social;
import net.arcanic.visuals.core.Sync;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.BrandLogo;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.PlayerSkin;

public final class FrontEndMenu {
    private static final Map<String, Spec> SPECS;
    private static final Map<String, String> SHORT;
    private static final float UNLOCK_SECONDS = 0.85F;
    private static final float LOCK_SECONDS = 0.6F;
    private static Screen lastScreen;
    private static float lastProgress;
    private static boolean lastOpen;
    private static boolean unlockedOnce;
    private final Anim anim = new Anim();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> plaques = new ArrayList<>();
    private final List<Entry> tools = new ArrayList<>();
    private final Screen screen;
    private float u;
    private float opacity;
    private float progress;
    private float expand;
    private float pull;
    private float pullSpeed;
    private boolean open;
    private boolean swiping;
    private double swipeFrom;
    private long lastFrame = System.nanoTime();
    private long lockedAt = System.nanoTime();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private float panelK;
    private final List<Integer> dashes = new ArrayList<>();
    private Supplier<PlayerSkin> skin;
    private static boolean hintClosed;
    private Entry hintClose;
    private Entry hovered;
    private int hintX;
    private int hintY;
    private int hintW;
    private int hintH;

    public FrontEndMenu(Screen screen, List<AbstractButton> originals, Consumer<Button> add) {
        this.screen = screen;
        boolean same = screen == lastScreen;
        this.anim.set("open", same ? 1.0F : 0.0F);
        this.progress = same ? lastProgress : 0.0F;
        this.open = same ? lastOpen : unlockedOnce;
        lastScreen = screen;
        this.expand = ease(this.progress);
        this.anim.set("padlock", this.open ? 1.0F : 0.0F);

        for (AbstractButton original : originals) {
            ComponentContents var9 = original.getMessage().getContents();
            String var10000;
            if (var9 instanceof TranslatableContents c) {
                var10000 = c.getKey();
            } else {
                var10000 = "";
            }

            String key = var10000;
            boolean credits = key.equals("title.credits")
                    || original.getMessage().getString().contains("Mojang");
            Spec spec = credits
                    ? new Spec(0, "info", FrontEndMenu.Kind.FOOTER)
                    : (Spec) SPECS.getOrDefault(key, new Spec(0, "puzzle", FrontEndMenu.Kind.TOOL));
            Component var10003 = original.getMessage();
            Entry entry = new Entry(var10003, spec, key, original::onPress);
            entry.active = original.active;
            this.entries.add(entry);
        }

        this.entries.add(new Entry(
                Component.literal("Arcanic Social"),
                new Spec(40, "players", FrontEndMenu.Kind.PLAQUE),
                "social",
                (i) -> Minecraft.getInstance().setScreen(new SocialScreen(screen))));
        int asks = Social.incoming().size();
        this.entries.add(new Entry(
                Component.literal(Lang.tr("Друзья") + (asks > 0 ? "  · " + asks : "")),
                new Spec(45, "users", FrontEndMenu.Kind.PLAQUE),
                "friends",
                (i) -> Minecraft.getInstance().setScreen(ArcanicMenu.friends())));
        this.entries.add(new Entry(
                Component.literal(Lang.tr("Аккаунты")),
                new Spec(0, "user-round", FrontEndMenu.Kind.ACCOUNT),
                "",
                (i) -> Minecraft.getInstance().setScreen(new AccountManagerScreen(screen))));
        if (!Sync.signedIn() && !hintClosed) {
            this.hintClose = new Entry(
                    Component.literal(Lang.tr("Закрыть")), new Spec(0, "x", FrontEndMenu.Kind.HINT), "", (i) -> {
                        hintClosed = true;
                        this.hintClose.visible = false;
                    });
            this.entries.add(this.hintClose);
        }

        this.entries.sort(Comparator.comparing((Entry ex) -> ex.spec.kind).thenComparingInt((ex) -> ex.spec.rank));

        for (Entry e : this.entries) {
            if (e.spec.kind == FrontEndMenu.Kind.PLAQUE) {
                this.plaques.add(e);
            }

            if (e.spec.kind == FrontEndMenu.Kind.TOOL) {
                this.tools.add(e);
            }
        }

        for (int i = 0; i < this.entries.size(); ++i) {
            Entry e = (Entry) this.entries.get(i);
            e.show = this.target(e);
            e.anim.set("show", e.show);
        }

        this.layout();
        this.entries.forEach(add);
    }

    private int s(float value) {
        return Math.max(1, Math.round(value * this.u));
    }

    private void place(Entry e, int xx, int yy, int ww, int hh) {
        int gs = Px.guiScale();
        e.setX(Math.round((float) xx / (float) gs));
        e.setY(Math.round((float) yy / (float) gs));
        e.setWidth(Math.max(1, Math.round((float) ww / (float) gs)));
        e.setHeight(Math.max(1, Math.round((float) hh / (float) gs)));
    }

    private void layout() {
        int sw = Px.width();
        int sh = Px.height();
        this.u = Wallpaper.unit();
        int ih = this.s(40.0F);
        int gap = this.s(4.0F);
        int sepH = this.s(17.0F);
        int head = this.s(20.0F) + this.s(36.0F) + this.s(18.0F);
        int pad = this.s(12.0F);
        int groups = 0;
        int last = -1;

        for (Entry e : this.plaques) {
            if (group(e) != last) {
                last = group(e);
                ++groups;
            }
        }

        this.panelW = this.s(296.0F);
        this.panelH =
                head + this.plaques.size() * ih + (this.plaques.size() - 1) * gap + (groups - 1) * (sepH - gap) + pad;
        this.panelX = (sw - this.panelW) / 2;
        this.panelY = (sh - this.panelH) / 2;
        this.dashes.clear();
        int row = this.panelY + head;
        last = -1;

        for (Entry e : this.plaques) {
            if (last >= 0 && group(e) != last) {
                this.dashes.add(row - gap + sepH / 2);
                row += sepH - gap;
            }

            last = group(e);
            this.place(e, this.panelX + this.s(10.0F), row, this.panelW - this.s(20.0F), ih);
            row += ih + gap;
        }

        for (Entry e : this.entries) {
            if (e.spec.kind == FrontEndMenu.Kind.ACCOUNT) {
                this.place(e, this.s(24.0F), this.s(22.0F), this.s(200.0F), this.s(36.0F));
            }
        }

        int d = this.s(34.0F);
        int x = sw - this.s(24.0F) - Px.textWidth(clock(), Px.Weight.SEMIBOLD, this.s(15.0F)) - this.s(18.0F) - d;

        for (int i = this.tools.size() - 1; i >= 0; --i) {
            this.place((Entry) this.tools.get(i), x, this.s(22.0F) + (this.s(36.0F) - d) / 2, d, d);
            x -= d + this.s(8.0F);
        }

        this.hintW = this.s(400.0F);
        this.hintH = this.s(142.0F);
        this.hintX = sw - this.s(24.0F) - this.hintW;
        this.hintY = this.s(76.0F);
        if (this.hintClose != null) {
            this.place(
                    this.hintClose,
                    this.hintX + this.hintW - this.s(36.0F),
                    this.hintY + this.s(12.0F),
                    this.s(24.0F),
                    this.s(24.0F));
        }

        for (Entry e : this.entries) {
            if (e.spec.kind == FrontEndMenu.Kind.FOOTER) {
                this.place(e, sw - this.s(24.0F) - this.s(220.0F), sh - this.s(38.0F), this.s(220.0F), this.s(20.0F));
            }
        }
    }

    private static int group(Entry e) {
        return e.spec.rank < 40 ? 0 : (e.spec.rank < 60 ? 1 : 2);
    }

    private static float ease(float t) {
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow((double) (-2.0F * t + 2.0F), 3.0) / 2.0F;
    }

    private static float smooth(float a, float b, float t) {
        float x = Math.max(0.0F, Math.min(1.0F, (t - a) / (b - a)));
        return x * x * (3.0F - 2.0F * x);
    }

    private float target(Entry e) {
        float var10000;
        switch (e.spec.kind.ordinal()) {
            case 0 ->
                var10000 = this.open && this.expand >= Math.min(0.98F, 0.42F + (float) this.plaques.indexOf(e) * 0.07F)
                        ? 1.0F
                        : 0.0F;
            case 3 -> var10000 = 1.0F;
            default -> var10000 = this.open && this.expand > 0.55F ? 1.0F : 0.0F;
        }

        return var10000;
    }

    private void unlock() {
        if (!this.open) {
            UiSound.play("unlock");
        }

        this.open = true;
        unlockedOnce = true;
        this.swiping = false;
    }

    private void lock() {
        if (this.open) {
            UiSound.play("lock");
        }

        this.open = false;
        this.pull = 0.0F;
        this.lockedAt = System.nanoTime();
    }

    public boolean pressed() {
        if (this.open) {
            return false;
        } else {
            Minecraft mc = Minecraft.getInstance();
            this.swiping = true;
            this.swipeFrom = mc.mouseHandler.ypos()
                    * (double) Px.height()
                    / (double) Math.max(1, mc.getWindow().getScreenHeight());
            this.pull = 0.0F;
            this.pullSpeed = 0.0F;
            return true;
        }
    }

    public void missed(double x, double y) {
        if (this.open && this.progress >= 1.0F && !this.covered(x, y)) {
            this.lock();
        }
    }

    public boolean key(int key) {
        if (!this.open) {
            if (key != 257 && key != 335 && key != 32 && key != 265) {
                return false;
            } else {
                this.unlock();
                return true;
            }
        } else if (key == 256 && this.progress >= 1.0F) {
            this.lock();
            return true;
        } else {
            return false;
        }
    }

    private boolean covered(double x, double y) {
        for (Entry e : this.entries) {
            if (e.visible
                    && x >= (double) e.getX()
                    && x < (double) (e.getX() + e.getWidth())
                    && y >= (double) e.getY()
                    && y < (double) (e.getY() + e.getHeight())) {
                return true;
            }
        }

        int gs = Px.guiScale();
        double px = x * (double) gs;
        double py = y * (double) gs;
        if (px >= (double) this.panelX
                && px < (double) (this.panelX + this.panelW)
                && py >= (double) this.panelY
                && py < (double) (this.panelY + this.panelH)) {
            return true;
        } else if (this.hintClose != null && this.hintClose.visible) {
            return px >= (double) this.hintX
                    && px < (double) (this.hintX + this.hintW)
                    && py >= (double) this.hintY
                    && py < (double) (this.hintY + this.hintH);
        } else {
            return false;
        }
    }

    private void step() {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float) (now - this.lastFrame) / 1.0E9F);
        this.lastFrame = now;
        Minecraft mc = Minecraft.getInstance();
        int sh = Px.height();
        if (this.swiping) {
            if (ArcanicMenu.leftHeld()) {
                double y = mc.mouseHandler.ypos()
                        * (double) sh
                        / (double) Math.max(1, mc.getWindow().getScreenHeight());
                float p = (float) Math.max(0.0, this.swipeFrom - y);
                if (dt > 0.0F) {
                    this.pullSpeed += ((p - this.pull) / dt - this.pullSpeed) * 0.5F;
                }

                this.pull = p;
            } else {
                this.swiping = false;
                if (this.pull < (float) this.s(6.0F)
                        || this.pull > (float) sh * 0.16F
                        || this.pullSpeed > 900.0F * this.u) {
                    this.unlock();
                }
            }
        }

        if (!this.swiping && !this.open) {
            this.pull = Theme.animations ? this.pull * (float) Math.exp((double) (-dt * 14.0F)) : 0.0F;
        }

        if (!Theme.animations) {
            this.progress = this.open ? 1.0F : 0.0F;
        } else {
            this.progress = Math.max(0.0F, Math.min(1.0F, this.progress + (this.open ? dt / 0.85F : -dt / 0.6F)));
        }

        this.expand = ease(this.progress);
        if (this.open && this.progress >= 1.0F) {
            this.pull = 0.0F;
        }

        lastProgress = this.progress;
        lastOpen = this.open;
        this.anim.to("padlock", this.open ? 1.0F : 0.0F, 12.0F);

        for (Entry e : this.entries) {
            e.show = e.anim.to("show", this.target(e), this.open ? 6.0F : 14.0F);
            if (e != this.hintClose || !hintClosed) {
                e.visible = e.show > 0.01F;
            }
        }
    }

    private static String clock() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static Locale locale() {
        return Lang.english() ? Locale.ENGLISH : Locale.forLanguageTag("ru");
    }

    public void render(GuiGraphics g, int mx, int my, float delta) {
        this.anim.frame();
        this.opacity = this.anim.to("open", 1.0F, 8.0F);
        this.layout();
        this.step();
        Px.begin(g);
        float old = Px.fade;
        Px.fade = old * this.opacity;
        int sw = Px.width();
        int sh = Px.height();
        float pullK = Math.min(1.0F, this.pull / ((float) sh * 0.4F));
        Px.rect(g, 0, 0, sw, sh, 0xFF000000);
        Wallpaper.draw(
                g,
                this.opacity,
                (1.0F - this.expand) * 0.8F * (1.0F - 0.55F * pullK),
                1.07F - 0.07F * this.expand - 0.02F * pullK * (1.0F - this.expand),
                Wallpaper.reach() * (0.6F + 0.4F * this.expand));
        g.nextStratum();
        g.fill(0, 0, sw, sh, Px.am(0xFF000000, this.opacity * 0.3F * (1.0F - this.expand) * (1.0F - 0.4F * pullK)));
        g.fillGradient(0, 0, sw, this.s(120.0F), Px.am(0xFF000000, this.opacity * 0.32F), 0);
        g.fillGradient(0, sh - this.s(170.0F), sw, sh, 0, Px.am(0xFF000000, this.opacity * 0.34F));
        g.nextStratum();
        this.lockScreen(g, sw, sh, pullK);
        this.panelK = smooth(0.35F, 0.9F, this.expand);
        this.panel(g);
        this.topBar(g, sw);
        this.footer(g, sh);
        if (this.hintClose != null && this.hintClose.visible) {
            this.accountHint(g, this.hintClose.show);
        }

        Px.fade = old;
        Px.end(g);
        g.nextStratum();
        Entry over = null;

        for (Entry e : this.entries) {
            e.render(g, mx, my, delta);
            if (e.active && e.clickable() && e.isHoveredOrFocused()) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }

            if (e.active && e.clickable() && e.isHovered()) {
                over = e;
            }
        }

        if (over != null && over != this.hovered) {
            UiSound.play("hover", 1.0F + (float) (this.entries.indexOf(over) % 5) * 0.02F, 1.0F);
        }

        this.hovered = over;
        g.nextStratum();
        Px.begin(g);

        try {
            Toasts.render(g);
        } finally {
            Px.end(g);
        }
    }

    private void lockScreen(GuiGraphics g, int sw, int sh, float pullK) {
        float left = 1.0F - this.expand;
        if (!(left <= 0.003F)) {
            float old = Px.fade;
            float k = (float) Math.pow((double) left, 1.3) * (1.0F - 0.45F * pullK);
            float go = ease(this.expand);
            long ms = System.currentTimeMillis();
            LocalDateTime now = LocalDateTime.now();
            Px.fade = old * k;
            g.pose().pushMatrix();
            g.pose().translate(0.0F, (float) (-Math.round((float) this.s(70.0F) * go)) - this.pull * left * 0.45F);
            int cx = sw / 2;
            int top = Math.max(this.s(70.0F), sh / 2 - this.s(230.0F));
            String date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale()));
            String var10000 = date.substring(0, 1).toUpperCase(locale());
            date = var10000 + date.substring(1);
            Px.textCenter(g, date, cx + this.s(1.0F), top + this.s(2.0F), Px.Weight.SEMIBOLD, this.s(22.0F), 637534208);
            Px.textCenter(g, date, cx, top, Px.Weight.SEMIBOLD, this.s(22.0F), 0xE6FFFFFF);
            String time = now.format(DateTimeFormatter.ofPattern("H:mm"));
            int cpx = this.s(150.0F);
            int cy = top + this.s(30.0F);
            int tw = Px.textWidth(time, Px.Weight.SEMIBOLD, 96) * cpx / 96;
            Px.glow(
                    g,
                    cx - tw / 2 - this.s(70.0F),
                    cy - this.s(30.0F),
                    tw + this.s(140.0F),
                    cpx + this.s(60.0F),
                    486539263);
            this.big(g, time, cx + this.s(2.0F), cy + this.s(4.0F), cpx, 603979776);
            this.big(g, time, cx, cy, cpx, 0xF0FFFFFF);
            g.pose().popMatrix();
            Px.fade = old * k * (1.0F - go);
            String hint = Lang.tr("Нажмите или проведите вверх, чтобы открыть");
            int hfs = this.s(13.0F);
            int pw = Px.textWidth(hint, Px.Weight.MEDIUM, hfs) + this.s(36.0F);
            int ph = this.s(34.0F);
            int px0 = (sw - pw) / 2;
            int py0 = sh - this.s(92.0F) - Math.round(this.pull * left * 0.2F);
            Px.shadow(g, px0, py0 + this.s(6.0F), pw, ph, this.s(20.0F), 1073741824);
            Px.round(g, px0, py0, pw, ph, ph / 2, 1090519039);
            Px.outline(g, px0, py0, pw, ph, ph / 2, 788529151);
            Px.textCenter(g, hint, sw / 2, py0 + (ph - hfs) / 2, Px.Weight.MEDIUM, hfs, 0xF2FFFFFF);
            float bob = (float) Math.sin((double) ms / 420.0) * 0.5F + 0.5F;
            Px.fade = old * k * (1.0F - go) * (0.45F + 0.55F * bob);
            this.chevron(g, sw / 2, py0 - this.s(18.0F) - Math.round((float) this.s(5.0F) * bob), this.s(9.0F));
            Px.fade = old;
        }
    }

    private void chevron(GuiGraphics g, int x, int y, int arm) {
        int th = Math.max(2, this.s(3.0F));

        for (int side = -1; side <= 1; side += 2) {
            g.pose().pushMatrix();
            g.pose().translate((float) x, (float) y);
            g.pose().rotate((float) Math.toRadians((double) (side * 35 + 90)));
            Px.round(g, 0, -th / 2, arm, th, th / 2, -1);
            g.pose().popMatrix();
        }
    }

    private void big(GuiGraphics g, String text, int cx, int y, int px, int colour) {
        int size = Math.min(96, px);
        float scale = (float) px / (float) size;
        g.pose().pushMatrix();
        g.pose().translate((float) cx, (float) y);
        g.pose().scale(scale, scale);
        Px.textCenter(g, text, 0, 0, Px.Weight.SEMIBOLD, size, colour);
        g.pose().popMatrix();
    }

    private void panel(GuiGraphics g) {
        if (!(this.panelK <= 0.003F)) {
            float old = Px.fade;
            Px.fade = old * this.panelK;
            g.pose().pushMatrix();
            g.pose().translate(0.0F, (float) Math.round((float) this.s(24.0F) * (1.0F - this.panelK)));
            int x = this.panelX;
            int y = this.panelY;
            int w = this.panelW;
            int h = this.panelH;
            int r = this.s(20.0F);
            Px.shadow(g, x, y + this.s(8.0F), w, h, this.s(30.0F), 0x8C000000);
            Px.round(g, x, y, w, h, r, Px.a(ArcanicMenu.panelBg(), 217));
            Px.outline(g, x, y, w, h, r, ArcanicMenu.light() ? 335544320 : 318767103);
            Minecraft mc = Minecraft.getInstance();
            if (this.skin == null) {
                this.skin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
            }

            int face = Math.max(8, this.s(36.0F) / 8 * 8);
            int fx = x + this.s(20.0F);
            int fy = y + this.s(20.0F);
            Px.round(
                    g,
                    fx - this.s(2.0F),
                    fy - this.s(2.0F),
                    face + this.s(4.0F),
                    face + this.s(4.0F),
                    this.s(8.0F),
                    ArcanicMenu.cardHi());
            PlayerFaceRenderer.draw(
                    g, (PlayerSkin) this.skin.get(), fx, fy, face, Math.round(255.0F * Px.fade) << 24 | 0xFFFFFF);
            int hour = LocalDateTime.now().getHour();
            String hello = hour >= 5 && hour < 12
                    ? "Доброе утро"
                    : (hour >= 12 && hour < 17
                            ? "Добрый день"
                            : (hour >= 17 && hour < 23 ? "Добрый вечер" : "Доброй ночи"));
            int tx = fx + face + this.s(12.0F);
            int tw = x + w - this.s(18.0F) - tx;
            Px.text(g, Lang.tr(hello) + ",", tx, fy + this.s(1.0F), Px.Weight.MEDIUM, this.s(12.0F), ArcanicMenu.sub());
            Px.text(
                    g,
                    Px.ellipsize(mc.getUser().getName(), Px.Weight.BOLD, this.s(17.0F), tw),
                    tx,
                    fy + this.s(16.0F),
                    Px.Weight.BOLD,
                    this.s(17.0F),
                    ArcanicMenu.T().text());

            for (int dy : this.dashes) {
                Px.rect(g, x + this.s(24.0F), dy, this.s(18.0F), Math.max(1, this.s(1.0F)), ArcanicMenu.edgeMedium());
            }

            g.pose().popMatrix();
            Px.fade = old;
        }
    }

    private void topBar(GuiGraphics g, int sw) {
        float k = smooth(0.5F, 1.0F, this.expand);
        if (!(k <= 0.003F)) {
            float old = Px.fade;
            Px.fade = old * k;
            int fs = this.s(15.0F);
            int y = this.s(22.0F) + (this.s(36.0F) - fs) / 2 - Math.round((float) this.s(6.0F) * (1.0F - k));
            Px.textRight(g, clock(), sw - this.s(24.0F), y, Px.Weight.SEMIBOLD, fs, -1);
            Px.fade = old;
        }
    }

    private void footer(GuiGraphics g, int sh) {
        Px.text(
                g,
                "Arcanic Visuals 1.0  ·  created by Vaider & tuffboy",
                this.s(24.0F),
                sh - this.s(38.0F),
                Px.Weight.MEDIUM,
                this.s(12.0F),
                0x8CFFFFFF);
    }

    private void accountHint(GuiGraphics g, float k) {
        float old = Px.fade;
        Px.fade = old * k;
        int acc = Theme.accent() | 0xFF000000;
        int acc2 = Theme.accent2() | 0xFF000000;
        int x = this.hintX + Math.round((float) this.s(16.0F) * (1.0F - k));
        int y = this.hintY;
        int w = this.hintW;
        int h = this.hintH;
        Px.round(g, x, y, w, h, this.s(12.0F), 0xE6141220);
        Px.outline(g, x, y, w, h, this.s(12.0F), Px.a(acc, 110));
        Px.circle(g, x + this.s(32.0F), y + this.s(34.0F), this.s(17.0F), Px.a(acc, 60));
        Px.icon(g, "sparkles", x + this.s(32.0F) - this.s(9.0F), y + this.s(34.0F) - this.s(9.0F), this.s(18.0F), acc2);
        int tx = x + this.s(60.0F);
        int tw = w - this.s(60.0F) - this.s(44.0F);
        Px.text(
                g,
                Lang.tr("Косметика и синхронизация"),
                tx,
                y + this.s(20.0F),
                Px.Weight.SEMIBOLD,
                this.s(15.0F),
                -724488);
        String body = Lang.tr(
                "Войди в аккаунт Arcanic в лаунчере — другие игроки увидят твои плащи, крылья и эмоции, а ты увидишь их.");
        int ly = y + this.s(46.0F);

        for (String line : wrap(body, Px.Weight.MEDIUM, this.s(12.0F), tw + this.s(24.0F))) {
            Px.text(g, line, tx, ly, Px.Weight.MEDIUM, this.s(12.0F), -5791816);
            ly += this.s(17.0F);
        }

        Px.icon(g, "user", tx, y + h - this.s(30.0F), this.s(14.0F), acc2);
        Px.text(
                g,
                Lang.tr("Войти можно в «Arcanic Social» в меню"),
                tx + this.s(20.0F),
                y + h - this.s(29.0F),
                Px.Weight.MEDIUM,
                this.s(12.0F),
                Px.mix(acc2, -1, 0.25F));
        Px.fade = old;
    }

    private static List<String> wrap(String text, Px.Weight weight, int size, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : String.valueOf(line) + " " + word;
            if (!line.isEmpty() && Px.textWidth(next, weight, size) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }

        return lines;
    }

    public static void logo(GuiGraphics g, int cx, int cy, int r) {
        BrandLogo.idle(g, cx - r, cy - r, 2 * r);
    }

    static {
        SPECS = Map.ofEntries(
                Map.entry("menu.singleplayer", new Spec(0, "world", FrontEndMenu.Kind.PLAQUE)),
                Map.entry("menu.playdemo", new Spec(0, "world", FrontEndMenu.Kind.PLAQUE)),
                Map.entry("menu.multiplayer", new Spec(10, "server-s", FrontEndMenu.Kind.PLAQUE)),
                Map.entry("menu.resetdemo", new Spec(20, "reset", FrontEndMenu.Kind.PLAQUE)),
                Map.entry("menu.options", new Spec(60, "w.sliders", FrontEndMenu.Kind.PLAQUE)),
                Map.entry("menu.quit", new Spec(100, "w.log-out", FrontEndMenu.Kind.PLAQUE)),
                Map.entry("options.language", new Spec(10, "flag-s", FrontEndMenu.Kind.TOOL)),
                Map.entry("options.accessibility", new Spec(20, "w.eye", FrontEndMenu.Kind.TOOL)),
                Map.entry("menu.online", new Spec(30, "w.cloud", FrontEndMenu.Kind.TOOL)));
        SHORT = Map.of("menu.options", "Настройки", "menu.quit", "Выйти");
    }

    private static enum Kind {
        PLAQUE,
        TOOL,
        ACCOUNT,
        FOOTER,
        HINT;
    }

    private static record Spec(int rank, String icon, Kind kind) {}

    private final class Entry extends Button {
        private final Spec spec;
        private final Anim anim = new Anim();
        private final Consumer<InputWithModifiers> action;
        private final String shortName;
        private float show = 1.0F;

        Entry(Component label, Spec spec, String key, Consumer<InputWithModifiers> action) {
            super(0, 0, 1, 1, label, (b) -> {}, DEFAULT_NARRATION);
            this.spec = spec;
            this.action = action;
            this.shortName = (String) FrontEndMenu.SHORT.get(key);
            this.anim.set("hover", 0.0F);
            this.anim.set("show", 0.0F);
            if (spec.kind == FrontEndMenu.Kind.TOOL || spec.kind == FrontEndMenu.Kind.FOOTER) {
                this.setTooltip(Tooltip.create(Lang.component(label)));
            }
        }

        boolean clickable() {
            return this.show > 0.9F;
        }

        public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
            return this.clickable() && super.mouseClicked(e, doubleClick);
        }

        public void playDownSound(SoundManager sounds) {
            UiSound.play(
                    this.spec.kind == FrontEndMenu.Kind.HINT
                            ? "pop"
                            : (this.spec.kind == FrontEndMenu.Kind.TOOL ? "tab" : "click"));
        }

        public void onPress(InputWithModifiers input) {
            this.action.accept(input);
        }

        private String label() {
            if (this.shortName != null) {
                return Lang.tr(this.shortName);
            } else {
                String s = Lang.tr(Lang.component(this.getMessage()).getString());
                return s.endsWith("...")
                        ? s.substring(0, s.length() - 3)
                        : (s.endsWith("…") ? s.substring(0, s.length() - 1) : s);
            }
        }

        protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
            this.anim.frame();
            float v = this.anim.to(
                    "hover", this.active && this.clickable() && this.isHoveredOrFocused() ? 1.0F : 0.0F, 13.0F);
            int gs = Px.guiScale();
            int xx = this.getX() * gs;
            int yy = this.getY() * gs;
            int ww = this.getWidth() * gs;
            int hh = this.getHeight() * gs;
            Px.begin(g);
            float old = Px.fade;
            Px.fade = old * FrontEndMenu.this.opacity * (this.active ? 1.0F : 0.4F) * this.show;
            g.pose().pushMatrix();
            float press = this.anim.to(
                    "press", this.active && this.isHovered() && ArcanicMenu.leftHeld() ? 1.0F : 0.0F, 22.0F);
            if (this.spec.kind == FrontEndMenu.Kind.PLAQUE) {
                g.pose().translate(0.0F, (float)
                        Math.round((float) FrontEndMenu.this.s(24.0F) * (1.0F - FrontEndMenu.this.panelK)
                                + (float) FrontEndMenu.this.s(10.0F) * (1.0F - this.show)));
            }

            if (this.spec.kind == FrontEndMenu.Kind.TOOL) {
                g.pose().translate(0.0F, (float) Math.round((float) (-FrontEndMenu.this.s(6.0F)) * (1.0F - this.show)));
            }

            if (press > 0.001F) {
                float sc = 1.0F - 0.03F * press;
                float pcx = (float) xx + (float) ww / 2.0F;
                float pcy = (float) yy + (float) hh / 2.0F;
                g.pose().translate(pcx, pcy);
                g.pose().scale(sc, sc);
                g.pose().translate(-pcx, -pcy);
            }

            int acc = Theme.accent() | 0xFF000000;
            switch (this.spec.kind.ordinal()) {
                case 0:
                    this.pill(g, xx, yy, ww, hh, v, acc);
                    break;
                case 1:
                    Px.circle(g, xx + ww / 2, yy + hh / 2, ww / 2, Px.mix(1494224916, 0x99202028, v));
                    int is = FrontEndMenu.this.s(17.0F);
                    ArcanicMenu.glyph(
                            g, this.spec.icon, xx + (ww - is) / 2, yy + (hh - is) / 2, is, Px.mix(0xD9FFFFFF, -1, v));
                    break;
                case 2:
                    Minecraft mc = Minecraft.getInstance();
                    if (FrontEndMenu.this.skin == null) {
                        FrontEndMenu.this.skin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
                    }

                    int fsz = Math.max(8, FrontEndMenu.this.s(20.0F) / 8 * 8);
                    int fx = xx + FrontEndMenu.this.s(9.0F);
                    int fy = yy + (hh - fsz) / 2;
                    int nx = fx + fsz + FrontEndMenu.this.s(9.0F);
                    String name = Px.ellipsize(
                            mc.getUser().getName(),
                            Px.Weight.SEMIBOLD,
                            FrontEndMenu.this.s(13.0F),
                            ww - fsz - FrontEndMenu.this.s(30.0F));
                    int cw = Math.min(
                            ww,
                            nx
                                    + Px.textWidth(name, Px.Weight.SEMIBOLD, FrontEndMenu.this.s(13.0F))
                                    + FrontEndMenu.this.s(12.0F)
                                    - xx);
                    Px.round(g, xx, yy, cw, hh, FrontEndMenu.this.s(8.0F), Px.mix(0xA6141220, 0xC81E1B2C, v));
                    PlayerFaceRenderer.draw(
                            g,
                            (PlayerSkin) FrontEndMenu.this.skin.get(),
                            fx,
                            fy,
                            fsz,
                            Math.round(255.0F * Px.fade) << 24 | 0xFFFFFF);
                    Px.text(
                            g,
                            name,
                            nx,
                            yy + (hh - FrontEndMenu.this.s(13.0F)) / 2,
                            Px.Weight.SEMIBOLD,
                            FrontEndMenu.this.s(13.0F),
                            Px.mix(-2501406, -1, v));
                    break;
                case 3:
                    Px.textRight(
                            g,
                            Lang.tr("© Mojang AB · Minecraft"),
                            xx + ww,
                            yy + FrontEndMenu.this.s(4.0F),
                            Px.Weight.MEDIUM,
                            FrontEndMenu.this.s(10.0F),
                            Px.mix(0x80FFFFFF, -1, v));
                    break;
                case 4:
                    Px.circle(g, xx + ww / 2, yy + hh / 2, ww / 2, Px.a(-1, Math.round(28.0F * v)));
                    is = FrontEndMenu.this.s(12.0F);
                    Px.icon(g, "x", xx + (ww - is) / 2, yy + (hh - is) / 2, is, Px.mix(-7567968, -724488, v));
            }

            g.pose().popMatrix();
            Px.fade = old;
            Px.end(g);
        }

        private void pill(GuiGraphics g, int xx, int yy, int ww, int hh, float v, int acc) {
            int fill = this.spec.rank >= 100 ? -1750963 : acc;
            if (v > 0.01F) {
                Px.shadow(
                        g,
                        xx + FrontEndMenu.this.s(6.0F),
                        yy + FrontEndMenu.this.s(5.0F),
                        ww - FrontEndMenu.this.s(12.0F),
                        hh - FrontEndMenu.this.s(4.0F),
                        FrontEndMenu.this.s(8.0F),
                        Px.a(fill, Math.round(80.0F * v)));
                Px.round(g, xx, yy, ww, hh, hh / 2, Px.a(fill, Math.round(255.0F * v)));
            }

            int shift = Math.round((float) FrontEndMenu.this.s(4.0F) * v);
            int is = FrontEndMenu.this.s(19.0F);
            int fs = FrontEndMenu.this.s(14.0F);
            int tx = xx + FrontEndMenu.this.s(14.0F) + shift;
            ArcanicMenu.glyph(g, this.spec.icon, tx, yy + (hh - is) / 2, is, Px.mix(ArcanicMenu.sub(), -1, v));
            String label =
                    Px.ellipsize(this.label(), Px.Weight.MEDIUM, fs, xx + ww - tx - is - FrontEndMenu.this.s(40.0F));
            Px.text(
                    g,
                    label,
                    tx + is + FrontEndMenu.this.s(11.0F),
                    yy + (hh - fs) / 2,
                    Px.Weight.MEDIUM,
                    fs,
                    Px.mix(ArcanicMenu.label(), -1, v));
            if (v > 0.01F) {
                float k = Px.fade;
                Px.fade = k * v;
                int cs = FrontEndMenu.this.s(14.0F);
                ArcanicMenu.glyph(
                        g,
                        "chev-right",
                        xx
                                + ww
                                - FrontEndMenu.this.s(14.0F)
                                - cs
                                - Math.round((float) FrontEndMenu.this.s(4.0F) * (1.0F - v)),
                        yy + (hh - cs) / 2,
                        cs,
                        -1);
                Px.fade = k;
            }
        }
    }
}
