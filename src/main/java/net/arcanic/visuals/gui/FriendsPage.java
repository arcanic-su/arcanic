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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Social;
import net.arcanic.visuals.core.Sync;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * The Friends page of the menu: Arcanic Social friends as heads, their requests and what they may see of you. A
 * click on a head opens the friend's card: where they play, their coordinates, joining their server.
 */
final class FriendsPage {
    private static final int GREEN = 0xFF4ADE80;
    private static final int AMBER = 0xFFFBBF24;
    private static final int RED = 0xFFFF5C72;
    private final ArcanicMenu menu;
    int tab;
    private String open;
    private long openAt;
    private long confirmAt;

    FriendsPage(ArcanicMenu menu) {
        this.menu = menu;
    }

    private int S(double v) {
        return this.menu.S(v);
    }

    String scrollKey() {
        return "friends" + this.tab;
    }

    String[] labels() {
        int n = Social.friends().size();
        int r = Social.incoming().size();
        return new String[] {
            Lang.tr("Друзья") + (n > 0 ? "  " + n : ""),
            Lang.tr("Заявки") + (r > 0 ? "  " + r : ""),
            Lang.tr("Приватность")
        };
    }

    void addByName() {
        UiSound.play("click");
        Minecraft.getInstance()
                .setScreen(PromptScreen.field(
                                this.menu,
                                "Добавить друга",
                                "Ник в игре или логин Arcanic",
                                "Отправить заявку",
                                Social::add)
                        .note(Lang.tr("Друг тоже должен играть с Arcanic Visuals")));
    }

    /** Closes the friend's card; true if one was open. */
    boolean back() {
        if (this.open == null) return false;
        this.open = null;
        UiSound.play("pop", 0.8F, 0.6F);
        return true;
    }

    void friends(GuiGraphics g) {
        int x = this.menu.cx0;
        int w = this.menu.cw0;
        int y = this.menu.top;
        if (!Sync.signedIn()) {
            this.empty(
                    g,
                    y,
                    "users",
                    "Войди в Arcanic Social",
                    "Друзья живут в аккаунте Arcanic: их видно на любом сервере и прямо в меню игры",
                    "Войти",
                    () -> {
                        UiSound.play("click");
                        Minecraft.getInstance().setScreen(new SocialScreen(this.menu));
                    });
            return;
        }
        if (!Social.ready()) {
            this.empty(
                    g,
                    y,
                    "refresh",
                    "Подключаемся к Arcanic Social…",
                    "Список друзей появится через пару секунд",
                    null,
                    null);
            return;
        }
        switch (this.tab) {
            case 1 -> this.requests(g, x, y, w);
            case 2 -> this.privacy(g, x, y, w);
            default -> this.heads(g, x, y, w);
        }
        Social.Friend f = this.open == null ? null : Social.friend(this.open);
        if (this.open != null && f == null) this.open = null;
        if (f != null) this.card(g, f);
        this.note(g);
    }

    /** The service's last answer as a pill at the bottom: in the title screen there is no HUD to show it. */
    private void note(GuiGraphics g) {
        String text = Social.lastNote();
        if (text.isEmpty()) return;
        g.nextStratum();
        int fs = this.S(12.0);
        int h = this.S(30.0);
        int w = Math.min(this.menu.cw0, Px.textWidth(text, Px.Weight.SEMIBOLD, fs) + this.S(40.0));
        int x = this.menu.cx0 + (this.menu.cw0 - w) / 2;
        int y = this.menu.bottom - h - this.S(12.0);
        int c = Social.lastNoteGood() ? GREEN : RED;
        Px.shadow(g, x, y + this.S(4.0), w, h, this.S(12.0), 0x55000000);
        Px.round(g, x, y, w, h, h / 2, ArcanicMenu.cardHi());
        Px.outline(g, x, y, w, h, h / 2, Px.a(c & 0xFFFFFF, 120));
        int d = Math.max(4, this.S(7.0));
        Px.round(g, x + this.S(14.0), y + (h - d) / 2, d, d, d / 2, c);
        Px.text(
                g,
                Px.ellipsize(text, Px.Weight.SEMIBOLD, fs, w - this.S(40.0)),
                x + this.S(28.0),
                y + (h - fs) / 2,
                Px.Weight.SEMIBOLD,
                fs,
                ArcanicMenu.T().text());
    }

    // ------------------------------------------------------------------ the heads

    private void heads(GuiGraphics g, int x, int y, int w) {
        List<Social.Friend> list = Social.friends();
        if (list.isEmpty()) {
            this.empty(
                    g,
                    y,
                    "user-plus",
                    "Пока нет друзей",
                    "Наведи прицел на игрока с Arcanic и нажми колёсико мыши, или добавь друга по нику",
                    "Добавить по нику",
                    this::addByName);
            return;
        }
        int gap = this.S(12.0);
        int cols = Math.max(2, (w + gap) / (this.S(176.0) + gap));
        int cw = (w - gap * (cols - 1)) / cols;
        int ch = this.S(156.0);
        int rows = (list.size() + cols - 1) / cols;
        int total = rows * (ch + gap);
        int top = y;
        int bottom = this.menu.bottom;
        float sc = (float) Math.round(this.menu.scrolled(this.scrollKey(), total, top, bottom));
        g.enableScissor(x - this.S(12.0), top, x + w + this.S(12.0), bottom);
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -sc);

        for (int i = 0; i < list.size(); ++i) {
            g.nextStratum();
            Social.Friend f = list.get(i);
            int cx = x + (i % cols) * (cw + gap);
            int cy = top + this.S(2.0) + (i / cols) * (ch + gap);
            this.head(g, f, i, cx, cy, cw, ch, sc, top, bottom);
        }

        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        this.menu.edges(g, x - this.S(12.0), w + this.S(24.0), top, bottom, sc, this.menu.baseColour());
        this.menu.scrollbar(g, x + w + this.S(14.0), top, bottom, total, sc, this.scrollKey());
    }

    private boolean over(int x, int y, int w, int h, float sc, int top, int bottom) {
        double my = this.menu.my;
        return this.open == null
                && !this.menu.under()
                && my >= top
                && my < bottom
                && this.menu.hover(x, Math.round(y - sc), w, h);
    }

    private static int statusColour(Social.Friend f) {
        return f.inWorld() ? GREEN : f.online() ? AMBER : ArcanicMenu.faint();
    }

    private void head(
            GuiGraphics g, Social.Friend f, int i, int x, int y, int w, int h, float sc, int top, int bottom) {
        String id = "fr:" + f.account();
        boolean hv = this.over(x, y, w, h, sc, top, bottom);
        if (hv) this.menu.hot(id);
        float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
        float enter = ArcanicMenu.ease((float) (this.menu.now - this.menu.contentAt - i * 30L) / 320.0F);
        float old = Px.fade;
        Px.fade = old * enter * (f.online() ? 1.0F : 0.72F);
        int lift = Math.round(this.S(8.0) * (1.0F - enter)) - Math.round(this.S(2.0) * hk);
        int yy = y + lift;
        int r = this.S(14.0);
        Px.round(g, x, yy, w, h, r, Px.mix(ArcanicMenu.cardBg(), ArcanicMenu.cardHi(), hk));
        Px.outline(g, x, yy, w, h, r, Px.mix(ArcanicMenu.edgeMedium(), Px.a(ArcanicMenu.acc(), 140), hk));

        int face = Math.max(8, this.S(64.0) / 8 * 8);
        int fx = x + (w - face) / 2;
        int fy = yy + this.S(18.0);
        int frame = this.S(4.0);
        Px.round(g, fx - frame, fy - frame, face + frame * 2, face + frame * 2, this.S(12.0), ArcanicMenu.field());
        PlayerFaceRenderer.draw(
                g, Social.skin(f.name(), f.uuid()), fx, fy, face, Math.round(255.0F * Px.fade) << 24 | 0xFFFFFF);
        int dot = Math.max(6, this.S(13.0));
        int ring = Math.max(2, this.S(3.0));
        int dx = fx + face - dot / 2 - ring / 2;
        int dy = fy + face - dot / 2 - ring / 2;
        Px.round(
                g,
                dx - ring,
                dy - ring,
                dot + ring * 2,
                dot + ring * 2,
                (dot + ring * 2) / 2,
                Px.mix(ArcanicMenu.cardBg(), ArcanicMenu.cardHi(), hk));
        Px.round(g, dx, dy, dot, dot, dot / 2, statusColour(f));
        if (!Social.lit(f.account()) && f.inWorld()) {
            ArcanicMenu.glyph(g, "eye-off", x + w - this.S(26.0), yy + this.S(12.0), this.S(14.0), ArcanicMenu.faint());
        }

        int ny = fy + face + this.S(14.0);
        int pad = this.S(12.0);
        Px.textCenter(
                g,
                Px.ellipsize(f.name(), Px.Weight.SEMIBOLD, this.S(14.0), w - pad * 2),
                x + w / 2,
                ny,
                Px.Weight.SEMIBOLD,
                this.S(14.0),
                ArcanicMenu.T().text());
        String status = f.near() ? Lang.tr("Рядом с тобой") + " · " + this.distance(f) : f.status();
        Px.textCenter(
                g,
                Px.ellipsize(status, Px.Weight.MEDIUM, this.S(11.0), w - pad * 2),
                x + w / 2,
                ny + this.S(19.0),
                Px.Weight.MEDIUM,
                this.S(11.0),
                f.inWorld() ? ArcanicMenu.sub() : ArcanicMenu.faint());
        Px.fade = old;
        if (this.open == null && !this.menu.under()) {
            this.menu.clipped(x, y, w, h, sc, top, bottom, () -> this.show(f), (Runnable) null);
        }
    }

    private void show(Social.Friend f) {
        this.open = f.account();
        this.openAt = this.menu.now;
        this.confirmAt = 0L;
        UiSound.play("pop");
    }

    private String distance(Social.Friend f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || f.pos() == null) return "";
        return (int) mc.player.position().distanceTo(f.pos()) + " " + Lang.tr("м");
    }

    // ------------------------------------------------------------------ the friend's card

    private static String world(String dim) {
        if (dim == null) return "—";
        if (dim.endsWith("overworld")) return Lang.tr("Обычный мир");
        if (dim.endsWith("the_nether")) return Lang.tr("Незер");
        if (dim.endsWith("the_end")) return Lang.tr("Энд");
        int c = dim.indexOf(':');
        return c >= 0 ? dim.substring(c + 1) : dim;
    }

    private static String coords(Vec3 p) {
        return String.format(
                Locale.ROOT, "%d %d %d", (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
    }

    private void card(GuiGraphics g, Social.Friend f) {
        int ax = this.menu.cx0 - this.S(12.0);
        int aw = this.menu.cw0 + this.S(24.0);
        int at = this.menu.top - this.S(4.0);
        int ab = this.menu.bottom;
        float k = ArcanicMenu.ease((float) (this.menu.now - this.openAt) / 260.0F);
        g.nextStratum();
        float old = Px.fade;
        Px.fade = old * k;
        Px.round(g, ax, at, aw, ab - at, this.S(12.0), Px.a(ArcanicMenu.panelBg(), 200));
        // a click beside the card closes it
        this.menu.clipped(ax, at, aw, ab - at, 0.0F, at, ab, this::back, (Runnable) null);

        List<Action> actions = this.actions(f);
        int w = Math.min(this.S(460.0), aw - this.S(40.0));
        int bh = this.S(36.0);
        int bgap = this.S(8.0);
        int rows = (actions.size() + 1) / 2;
        int infoH = this.S(4.0) * 0 + this.S(26.0) * 4;
        int h = this.S(24.0)
                + this.S(72.0)
                + this.S(18.0)
                + infoH
                + this.S(16.0)
                + rows * (bh + bgap)
                - bgap
                + this.S(22.0);
        int x = ax + (aw - w) / 2;
        int y = at + Math.max(this.S(8.0), (ab - at - h) / 2) + Math.round(this.S(10.0) * (1.0F - k));
        int r = this.S(16.0);
        Px.shadow(g, x, y + this.S(8.0), w, h, this.S(24.0), 0x66000000);
        Px.round(g, x, y, w, h, r, ArcanicMenu.cardBg());
        Px.outline(g, x, y, w, h, r, ArcanicMenu.edgeMedium());
        this.menu.clipped(x, y, w, h, 0.0F, at, ab, () -> {}, (Runnable) null);

        int pad = this.S(22.0);
        int face = Math.max(8, this.S(72.0) / 8 * 8);
        int fx = x + pad;
        int fy = y + this.S(24.0);
        Px.round(
                g,
                fx - this.S(4.0),
                fy - this.S(4.0),
                face + this.S(8.0),
                face + this.S(8.0),
                this.S(14.0),
                ArcanicMenu.field());
        PlayerFaceRenderer.draw(
                g, Social.skin(f.name(), f.uuid()), fx, fy, face, Math.round(255.0F * Px.fade) << 24 | 0xFFFFFF);
        int tx = fx + face + this.S(18.0);
        int room = x + w - pad - tx - this.S(34.0);
        Px.text(
                g,
                Px.ellipsize(f.name(), Px.Weight.BOLD, this.S(20.0), room),
                tx,
                fy + this.S(6.0),
                Px.Weight.BOLD,
                this.S(20.0),
                ArcanicMenu.T().text());
        Px.text(
                g,
                Px.ellipsize("Arcanic · " + f.account(), Px.Weight.MEDIUM, this.S(11.0), room),
                tx,
                fy + this.S(32.0),
                Px.Weight.MEDIUM,
                this.S(11.0),
                ArcanicMenu.faint());
        int dot = Math.max(4, this.S(8.0));
        int sy = fy + this.S(52.0);
        Px.round(g, tx, sy + (this.S(12.0) - dot) / 2, dot, dot, dot / 2, statusColour(f));
        Px.text(
                g,
                Px.ellipsize(f.status(), Px.Weight.SEMIBOLD, this.S(12.0), room),
                tx + dot + this.S(8.0),
                sy,
                Px.Weight.SEMIBOLD,
                this.S(12.0),
                ArcanicMenu.sub());

        // the close button, top right
        int cs = this.S(28.0);
        int cx = x + w - pad - cs + this.S(6.0);
        int cy = y + this.S(16.0);
        boolean chv = !this.menu.under() && this.menu.hover(cx, cy, cs, cs);
        float chk = this.menu.an("frClose", chv ? 1.0F : 0.0F, 18.0F);
        Px.round(g, cx, cy, cs, cs, cs / 2, Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), chk));
        int xs = this.S(12.0);
        ArcanicMenu.glyph(
                g,
                "x",
                cx + (cs - xs) / 2,
                cy + (cs - xs) / 2,
                xs,
                Px.mix(ArcanicMenu.faint(), ArcanicMenu.T().text(), chk));
        this.menu.clipped(cx, cy, cs, cs, 0.0F, at, ab, this::back, (Runnable) null);

        int iy = fy + face + this.S(22.0);
        int iw = w - pad * 2;
        Px.rect(g, x + pad, iy - this.S(8.0), iw, Math.max(1, this.S(1.0)), ArcanicMenu.hairline(14));
        this.info(
                g,
                x + pad,
                iy,
                iw,
                "Сервер",
                f.inWorld() ? (f.local() ? Lang.tr("Одиночная игра") : f.server() == null ? "—" : f.server()) : "—");
        this.info(g, x + pad, iy + this.S(26.0), iw, "Мир", f.inWorld() ? world(f.dim()) : "—");
        this.info(
                g,
                x + pad,
                iy + this.S(52.0),
                iw,
                "Координаты",
                f.pos() != null ? coords(f.pos()) : f.inWorld() ? Lang.tr("друг их скрыл") : "—");
        this.info(
                g,
                x + pad,
                iy + this.S(78.0),
                iw,
                "Расстояние",
                f.near() ? this.distance(f) : f.inWorld() ? Lang.tr("на другом сервере или в другом мире") : "—");

        int by = iy + infoH + this.S(16.0);
        int bw = (iw - bgap) / 2;
        for (int i = 0; i < actions.size(); ++i) {
            Action a = actions.get(i);
            int bx = x + pad + (i % 2) * (bw + bgap);
            int byy = by + (i / 2) * (bh + bgap);
            int wide = actions.size() % 2 == 1 && i == actions.size() - 1 ? iw : bw;
            this.action(g, a, bx, byy, wide, bh, at, ab);
        }

        Px.fade = old;
    }

    private void info(GuiGraphics g, int x, int y, int w, String label, String value) {
        int fs = this.S(12.0);
        Px.text(g, Lang.tr(label), x, y, Px.Weight.MEDIUM, fs, ArcanicMenu.faint());
        Px.textRight(
                g,
                Px.ellipsize(value, Px.Weight.SEMIBOLD, fs, w - this.S(120.0)),
                x + w,
                y,
                Px.Weight.SEMIBOLD,
                fs,
                ArcanicMenu.label());
    }

    private record Action(String id, String icon, String label, int kind, Runnable run) {}

    private List<Action> actions(Social.Friend f) {
        Minecraft mc = Minecraft.getInstance();
        List<Action> out = new ArrayList<>();
        if (f.joinable()) {
            out.add(new Action("join", "play", "Подключиться к серверу", 1, () -> {
                UiSound.play("select");
                Social.join(f);
            }));
        }
        if (f.near()) {
            boolean on = f.account().equals(Social.tracking());
            out.add(new Action("track", "target", on ? "Убрать метку" : "Метка на друга", on ? 0 : 1, () -> {
                Social.track(on ? null : f.account());
                UiSound.play(on ? "toggle_off" : "toggle_on");
            }));
        }
        if (f.pos() != null) {
            out.add(new Action("copy", "copy", "Скопировать координаты", 0, () -> {
                mc.keyboardHandler.setClipboard(coords(f.pos()));
                Social.note(Lang.tr("Координаты скопированы") + ": " + coords(f.pos()), true);
                UiSound.play("click");
            }));
        }
        if (mc.level != null && f.inWorld()) {
            out.add(new Action("inv", "bag", "Посмотреть инвентарь", 0, () -> {
                UiSound.play("click");
                Social.askInventory(f);
                mc.setScreen(new FriendInventoryScreen(this.menu, f));
            }));
        }
        if (mc.level != null && f.near()) {
            out.add(new Action("mark", "flag", "Поделиться меткой", 0, () -> {
                UiSound.play("click");
                Social.shareMark(f);
            }));
        }
        boolean lit = Social.lit(f.account());
        out.add(new Action(
                "glow", lit ? "eye" : "eye-off", lit ? "Подсветка: включена" : "Подсветка: выключена", 0, () -> {
                    Social.light(f.account(), !lit);
                    UiSound.play(lit ? "toggle_off" : "toggle_on");
                }));
        boolean sure = this.menu.now - this.confirmAt < 3000L;
        out.add(new Action("remove", "user-x", sure ? "Точно удалить?" : "Удалить из друзей", 2, () -> {
            if (this.menu.now - this.confirmAt < 3000L) {
                Social.remove(f.account());
                this.open = null;
                UiSound.play("drain");
            } else {
                this.confirmAt = this.menu.now;
                UiSound.play("pop", 0.9F, 0.8F);
            }
        }));
        return out;
    }

    /** kind: 0 plain, 1 the accent one, 2 danger */
    private void action(GuiGraphics g, Action a, int x, int y, int w, int h, int top, int bottom) {
        String id = "fra:" + a.id;
        boolean hv = !this.menu.under() && this.menu.hover(x, y, w, h);
        if (hv) this.menu.hot(id);
        float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
        int bg =
                switch (a.kind) {
                    case 1 -> Px.mix(ArcanicMenu.acc(), -1, 0.1F * hk);
                    case 2 -> Px.mix(ArcanicMenu.field(), Px.a(RED, 60), 0.4F + 0.6F * hk);
                    default -> Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), hk);
                };
        int tc =
                switch (a.kind) {
                    case 1 -> -1;
                    case 2 -> Px.mix(RED, -1, 0.2F * hk);
                    default -> Px.mix(ArcanicMenu.label(), ArcanicMenu.T().text(), hk);
                };
        Px.round(g, x, y, w, h, this.S(9.0), bg);
        int fs = this.S(12.0);
        int is = this.S(14.0);
        String text = Lang.tr(a.label);
        int tw = is + this.S(8.0) + Px.textWidth(text, Px.Weight.SEMIBOLD, fs);
        int tx = x + (w - tw) / 2;
        ArcanicMenu.glyph(g, a.icon, tx, y + (h - is) / 2, is, tc);
        Px.text(
                g,
                Px.ellipsize(text, Px.Weight.SEMIBOLD, fs, w - is - this.S(24.0)),
                tx + is + this.S(8.0),
                y + (h - fs) / 2,
                Px.Weight.SEMIBOLD,
                fs,
                tc);
        if (!this.menu.under()) this.menu.clipped(x, y, w, h, 0.0F, top, bottom, a.run, (Runnable) null);
    }

    // ------------------------------------------------------------------ requests

    private void requests(GuiGraphics g, int x, int y, int w) {
        List<Social.Request> in = Social.incoming();
        List<Social.Request> out = Social.outgoing();
        if (in.isEmpty() && out.isEmpty()) {
            this.empty(
                    g,
                    y,
                    "inbox",
                    "Заявок нет",
                    "Когда кто-то позовёт тебя в друзья, заявка появится здесь и всплывёт в игре",
                    "Добавить по нику",
                    this::addByName);
            return;
        }
        int rh = this.S(60.0);
        int gap = this.S(8.0);
        int capH = this.S(28.0);
        int total = (in.isEmpty() ? 0 : capH + in.size() * (rh + gap))
                + (out.isEmpty() ? 0 : capH + out.size() * (rh + gap));
        int top = y;
        int bottom = this.menu.bottom;
        float sc = (float) Math.round(this.menu.scrolled(this.scrollKey(), total, top, bottom));
        g.enableScissor(x - this.S(12.0), top, x + w + this.S(12.0), bottom);
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -sc);
        int yy = top;
        if (!in.isEmpty()) {
            this.menu.caps(
                    g, Lang.tr("Хотят дружить"), x + this.S(4.0), yy + this.S(9.0), this.S(10.0), ArcanicMenu.faint());
            yy += capH;
            for (Social.Request r : in) {
                g.nextStratum();
                this.request(g, r, true, x, yy, w, rh, sc, top, bottom);
                yy += rh + gap;
            }
        }
        if (!out.isEmpty()) {
            this.menu.caps(
                    g, Lang.tr("Ждут ответа"), x + this.S(4.0), yy + this.S(9.0), this.S(10.0), ArcanicMenu.faint());
            yy += capH;
            for (Social.Request r : out) {
                g.nextStratum();
                this.request(g, r, false, x, yy, w, rh, sc, top, bottom);
                yy += rh + gap;
            }
        }
        g.pose().popMatrix();
        g.disableScissor();
        g.nextStratum();
        this.menu.edges(g, x - this.S(12.0), w + this.S(24.0), top, bottom, sc, this.menu.baseColour());
        this.menu.scrollbar(g, x + w + this.S(14.0), top, bottom, total, sc, this.scrollKey());
    }

    private void request(
            GuiGraphics g, Social.Request r, boolean mine, int x, int y, int w, int h, float sc, int top, int bottom) {
        int rr = this.S(12.0);
        Px.round(g, x, y, w, h, rr, ArcanicMenu.cardBg());
        Px.outline(g, x, y, w, h, rr, ArcanicMenu.edgeMedium());
        int face = Math.max(8, this.S(36.0) / 8 * 8);
        int pad = this.S(14.0);
        PlayerFaceRenderer.draw(
                g,
                Social.skin(r.name(), r.uuid()),
                x + pad,
                y + (h - face) / 2,
                face,
                Math.round(255.0F * Px.fade) << 24 | 0xFFFFFF);
        int tx = x + pad + face + this.S(14.0);
        Px.text(
                g,
                r.name(),
                tx,
                y + this.S(13.0),
                Px.Weight.SEMIBOLD,
                this.S(14.0),
                ArcanicMenu.T().text());
        Px.text(
                g,
                Lang.tr(mine ? "хочет добавить тебя в друзья" : "ещё не ответил на заявку"),
                tx,
                y + this.S(33.0),
                Px.Weight.MEDIUM,
                this.S(11.0),
                ArcanicMenu.faint());
        int bh = this.S(32.0);
        int by = y + (h - bh) / 2;
        int right = x + w - pad;
        if (mine) {
            right -= this.small(
                            g, "acc:" + r.account(), "check", "Принять", right, by, bh, true, sc, top, bottom, () -> {
                                Social.accept(r.account());
                                UiSound.play("select");
                            })
                    + this.S(8.0);
            this.small(g, "dec:" + r.account(), "x", "Отклонить", right, by, bh, false, sc, top, bottom, () -> {
                Social.decline(r.account());
                UiSound.play("drain");
            });
        } else {
            this.small(g, "can:" + r.account(), "x", "Отменить", right, by, bh, false, sc, top, bottom, () -> {
                Social.cancel(r.account());
                UiSound.play("drain");
            });
        }
    }

    private int small(
            GuiGraphics g,
            String id,
            String icon,
            String label,
            int right,
            int y,
            int h,
            boolean accent,
            float sc,
            int top,
            int bottom,
            Runnable run) {
        int fs = this.S(12.0);
        int is = this.S(13.0);
        String text = Lang.tr(label);
        int w = this.S(12.0) + is + this.S(7.0) + Px.textWidth(text, Px.Weight.SEMIBOLD, fs) + this.S(14.0);
        int x = right - w;
        boolean hv = this.over(x, y, w, h, sc, top, bottom);
        if (hv) this.menu.hot(id);
        float hk = this.menu.an(id, hv ? 1.0F : 0.0F, 18.0F);
        Px.round(
                g,
                x,
                y,
                w,
                h,
                this.S(9.0),
                accent
                        ? Px.mix(ArcanicMenu.acc(), -1, 0.1F * hk)
                        : Px.mix(ArcanicMenu.field(), ArcanicMenu.cardHi(), hk));
        int tc = accent ? -1 : Px.mix(ArcanicMenu.label(), ArcanicMenu.T().text(), hk);
        ArcanicMenu.glyph(g, icon, x + this.S(12.0), y + (h - is) / 2, is, tc);
        Px.text(g, text, x + this.S(12.0) + is + this.S(7.0), y + (h - fs) / 2, Px.Weight.SEMIBOLD, fs, tc);
        if (this.open == null && !this.menu.under())
            this.menu.clipped(x, y, w, h, sc, top, bottom, run, (Runnable) null);
        return w;
    }

    // ------------------------------------------------------------------ privacy

    private void privacy(GuiGraphics g, int x, int y, int w) {
        int rh = this.S(64.0);
        int gap = this.S(8.0);
        this.toggleRow(
                g,
                "coords",
                x,
                y,
                w,
                rh,
                "Друзья видят, где я",
                "Сервер, мир и координаты. Без них друг увидит только сервер",
                Social.showsCoords(),
                () -> Social.showCoords(!Social.showsCoords()));
        this.toggleRow(
                g,
                "inv",
                x,
                y + rh + gap,
                w,
                rh,
                "Друзья могут смотреть мой инвентарь",
                "Только по их просьбе: ты увидишь, кто смотрел",
                Social.sharesInventory(),
                () -> Social.shareInventory(!Social.sharesInventory()));
        int ty = y + (rh + gap) * 2 + this.S(18.0);
        this.menu.caps(g, Lang.tr("Как добавить друга"), x + this.S(4.0), ty, this.S(10.0), ArcanicMenu.faint());
        String[] lines = {
            "Подойди к игроку с Arcanic Visuals, наведи на него прицел и нажми колёсико мыши",
            "В меню выбери «Добавить в друзья»: ему придёт заявка, принять её можно клавишей Y",
            "Или нажми «Добавить друга» вверху и впиши ник в игре или логин Arcanic"
        };
        int ly = ty + this.S(22.0);
        for (String line : lines) {
            Px.round(g, x + this.S(6.0), ly + this.S(5.0), this.S(5.0), this.S(5.0), this.S(3.0), ArcanicMenu.acc());
            Px.text(
                    g,
                    Px.ellipsize(Lang.tr(line), Px.Weight.MEDIUM, this.S(12.0), w - this.S(24.0)),
                    x + this.S(20.0),
                    ly,
                    Px.Weight.MEDIUM,
                    this.S(12.0),
                    ArcanicMenu.sub());
            ly += this.S(22.0);
        }
    }

    private void toggleRow(
            GuiGraphics g, String id, int x, int y, int w, int h, String title, String sub, boolean on, Runnable flip) {
        boolean hv = this.open == null && !this.menu.under() && this.menu.hover(x, y, w, h);
        if (hv) this.menu.hot("frp:" + id);
        float hk = this.menu.an("frp:" + id, hv ? 1.0F : 0.0F, 18.0F);
        float k = this.menu.tween("frpOn:" + id, on ? 1.0F : 0.0F, 200.0F);
        int r = this.S(12.0);
        Px.round(g, x, y, w, h, r, Px.mix(ArcanicMenu.cardBg(), ArcanicMenu.cardHi(), hk));
        Px.outline(g, x, y, w, h, r, ArcanicMenu.edgeMedium());
        int pad = this.S(18.0);
        Px.text(
                g,
                Lang.tr(title),
                x + pad,
                y + this.S(14.0),
                Px.Weight.SEMIBOLD,
                this.S(14.0),
                ArcanicMenu.T().text());
        Px.text(
                g,
                Px.ellipsize(Lang.tr(sub), Px.Weight.MEDIUM, this.S(11.0), w - pad * 2 - this.S(60.0)),
                x + pad,
                y + this.S(36.0),
                Px.Weight.MEDIUM,
                this.S(11.0),
                ArcanicMenu.faint());
        this.menu.toggle(g, x + w - pad - this.S(40.0), y, this.S(40.0), h, k, hk);
        if (this.open == null && !this.menu.under()) {
            this.menu.clipped(
                    x,
                    y,
                    w,
                    h,
                    0.0F,
                    this.menu.top,
                    this.menu.bottom,
                    () -> {
                        flip.run();
                        UiSound.play(on ? "toggle_off" : "toggle_on");
                    },
                    (Runnable) null);
        }
    }

    // ------------------------------------------------------------------ empty states

    private void empty(GuiGraphics g, int y, String icon, String title, String sub, String button, Runnable click) {
        int x = this.menu.cx0;
        int w = this.menu.cw0;
        int cx = x + w / 2;
        int cy = y + (this.menu.bottom - y) / 2 - this.S(60.0);
        int ts = this.S(64.0);
        Px.round(g, cx - ts / 2, cy, ts, ts, this.S(18.0), ArcanicMenu.field());
        int is = this.S(26.0);
        ArcanicMenu.glyph(g, icon, cx - is / 2, cy + (ts - is) / 2, is, ArcanicMenu.faint());
        Px.textCenter(
                g,
                Lang.tr(title),
                cx,
                cy + ts + this.S(18.0),
                Px.Weight.SEMIBOLD,
                this.S(16.0),
                ArcanicMenu.T().text());
        Px.textCenter(
                g,
                Px.ellipsize(Lang.tr(sub), Px.Weight.MEDIUM, this.S(12.0), w - this.S(40.0)),
                cx,
                cy + ts + this.S(42.0),
                Px.Weight.MEDIUM,
                this.S(12.0),
                ArcanicMenu.faint());
        if (button != null) {
            int bfs = this.S(12.0);
            String t = Lang.tr(button);
            int bw = Px.textWidth(t, Px.Weight.SEMIBOLD, bfs) + this.S(32.0);
            int bh = this.S(32.0);
            int bx = cx - bw / 2;
            int by = cy + ts + this.S(70.0);
            boolean hv = !this.menu.under() && this.menu.hover(bx, by, bw, bh);
            if (hv) this.menu.hot("frEmpty");
            float hk = this.menu.an("frEmpty", hv ? 1.0F : 0.0F, 18.0F);
            Px.round(g, bx, by, bw, bh, this.S(8.0), Px.mix(ArcanicMenu.acc(), -1, 0.1F * hk));
            Px.textCenter(g, t, cx, by + (bh - bfs) / 2, Px.Weight.SEMIBOLD, bfs, -1);
            if (!this.menu.under())
                this.menu.clipped(bx, by, bw, bh, 0.0F, this.menu.top, this.menu.bottom, click, (Runnable) null);
        }
    }
}
