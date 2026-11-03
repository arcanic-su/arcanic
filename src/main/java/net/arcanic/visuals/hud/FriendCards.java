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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Social;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.world.phys.Vec3;

/** Friend news in the top right corner: a request (Y accepts it), an accepted one, a shared mark, a friend online. */
public final class FriendCards {
    private static final List<Card> CARDS = new ArrayList<>();
    private static final long REQUEST_LIFE = 20000L;
    private static final long LIFE = 6000L;
    private static final int GREEN = 0xFF4ADE80;
    private static final int AMBER = 0xFFFBBF24;
    private static final int GREY = 0xFF9CA3AF;
    private static long lastFrame = System.currentTimeMillis();
    private static String acceptKey = "Y";

    private FriendCards() {}

    private static final class Card {
        final String account;
        final String name;
        final UUID uuid;
        final String title;
        final String sub;
        final int colour;
        final boolean request;
        long born = System.currentTimeMillis();
        boolean shown;
        String icon;
        long gone;
        float y = -1.0F;

        Card(String account, String name, UUID uuid, String title, String sub, int colour, boolean request) {
            this.account = account;
            this.name = name;
            this.uuid = uuid;
            this.title = title;
            this.sub = sub;
            this.colour = colour;
            this.request = request;
        }

        long life() {
            return this.request ? REQUEST_LIFE : LIFE;
        }
    }

    private static void push(Card c) {
        CARDS.removeIf((o) -> o.request == c.request && o.account != null && o.account.equals(c.account));
        CARDS.add(0, c);
        while (CARDS.size() > 4) CARDS.remove(CARDS.size() - 1);
    }

    public static void keyName(String name) {
        acceptKey = name == null || name.isBlank() ? "Y" : name.toUpperCase(Locale.ROOT);
    }

    public static void request(Social.Request r) {
        push(new Card(
                r.account(),
                r.name(),
                r.uuid(),
                r.name(),
                Lang.tr("хочет добавить тебя в друзья"),
                Theme.accent() | 0xFF000000,
                true));
        UiSound.play("friend_request");
    }

    public static void accepted(Social.Request r) {
        push(new Card(r.account(), r.name(), r.uuid(), r.name(), Lang.tr("теперь твой друг"), GREEN, false));
        UiSound.play("friend_accept");
    }

    public static void mark(String name, String label, Vec3 at, String elsewhere) {
        String where = String.format(
                Locale.ROOT, "%d %d %d", (int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
        String sub = (label == null ? "" : label + " · ") + where + (elsewhere == null ? "" : " · " + elsewhere);
        push(new Card(null, name, null, Lang.tr("Метка от") + " " + name, sub, AMBER, false));
        UiSound.play("friend_accept", 1.25F, 0.7F);
    }

    /** A short answer of Arcanic Social: sent, not found, no connection. */
    public static void note(String text, boolean good) {
        CARDS.removeIf((o) -> o.icon != null && o.gone == 0L);
        int cut = text.indexOf(": ");
        String title = cut < 0 ? text : text.substring(0, cut);
        String sub = cut < 0 ? "Arcanic Social" : text.substring(cut + 2);
        Card c = new Card(null, null, null, title, sub, good ? GREEN : 0xFFFF5C72, false);
        c.icon = good ? "users" : "alert-circle";
        CARDS.add(0, c);
        while (CARDS.size() > 4) CARDS.remove(CARDS.size() - 1);
    }

    public static void status(Social.Friend f, boolean online) {
        if (!online) return; // a friend leaving is no news worth a sound
        push(new Card(f.account(), f.name(), f.uuid(), f.name(), Lang.tr("зашёл в игру"), GREY, false));
        UiSound.play("friend_online");
    }

    public static void answered(String account) {
        for (Card c : CARDS)
            if (c.request && account.equals(c.account) && c.gone == 0L) c.gone = System.currentTimeMillis();
    }

    /** The accept key: the newest request that is still on screen. */
    public static void acceptNewest() {
        for (Card c : CARDS) {
            if (c.request && c.gone == 0L) {
                Social.accept(c.account);
                UiSound.play("select");
                return;
            }
        }
    }

    public static void render(GuiGraphics g) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (float) (now - lastFrame) / 1000.0F);
        lastFrame = now;
        float glide = 1.0F - (float) Math.pow(0.72, dt * 60.0F);
        float u = Math.max(0.6F, Math.min(1.6F, (float) Px.height() / 1080.0F));
        int w = Math.round(300.0F * u);
        int h = Math.round(62.0F * u);
        int gap = Math.round(10.0F * u);
        int x = Px.width() - w - Math.round(22.0F * u);
        int y0 = Math.round(22.0F * u);
        float base = Px.fade;
        int row = 0;
        Iterator<Card> it = CARDS.iterator();

        while (it.hasNext()) {
            Card c = it.next();
            if (!c.shown) {
                // a card that came in the menu starts its time when it is first on screen
                c.shown = true;
                c.born = now;
            }
            if (c.gone == 0L && now - c.born > c.life()) c.gone = now;
            float out = c.gone == 0L ? 0.0F : (float) (now - c.gone) / 220.0F;
            if (out >= 1.0F) {
                it.remove();
                continue;
            }
            float in = Math.min(1.0F, (float) (now - c.born) / 220.0F);
            float target = y0 + row * (h + gap);
            c.y = c.y < 0.0F ? target : c.y + (target - c.y) * glide;
            Px.fade = base * in * (1.0F - out);
            int slide = Math.round(24.0F * u * (1.0F - in) + 24.0F * u * out);
            card(g, c, x + slide, Math.round(c.y), w, h, u, now);
            ++row;
        }

        Px.fade = base;
    }

    private static void card(GuiGraphics g, Card c, int x, int y, int w, int h, float u, long now) {
        int r = Math.round(12.0F * u);
        Px.shadow(g, x, y + Math.round(4.0F * u), w, h, Math.round(14.0F * u), 0x66000000);
        Px.round(g, x, y, w, h, r, 0xEB0E0E11);
        Px.outline(g, x, y, w, h, r, 0x1FFFFFFF);
        int pad = Math.round(12.0F * u);
        int face = Math.max(8, Math.round(38.0F * u) / 8 * 8);
        int fy = y + (h - face) / 2;
        int dot = Math.max(4, Math.round(8.0F * u));
        if (c.icon != null) {
            Px.round(g, x + pad, fy, face, face, Math.round(10.0F * u), Px.a(c.colour & 0xFFFFFF, 40));
            int is = Math.round(face * 0.5F);
            Px.icon(g, "w." + c.icon, x + pad + (face - is) / 2, fy + (face - is) / 2, is, c.colour);
        } else if (c.name != null) {
            PlayerFaceRenderer.draw(
                    g, Social.skin(c.name, c.uuid), x + pad, fy, face, Math.round(255.0F * Px.fade) << 24 | 0xFFFFFF);
            Px.round(g, x + pad + face - dot / 2, fy + face - dot / 2, dot, dot, dot / 2, c.colour);
        }
        int tx = x + pad + face + Math.round(12.0F * u);
        int right = x + w - pad;
        int fs = Math.round(14.0F * u);
        int fs2 = Math.round(11.5F * u);
        if (c.request) {
            String key = acceptKey;
            int kw = Px.textWidth(key, Px.Weight.BOLD, fs2) + Math.round(12.0F * u);
            int kh = Math.round(20.0F * u);
            String acc = Lang.tr("Принять");
            int aw = Px.textWidth(acc, Px.Weight.SEMIBOLD, fs2);
            int kx = right - aw - Math.round(6.0F * u) - kw;
            int ky = y + Math.round(12.0F * u);
            Px.round(g, kx, ky, kw, kh, Math.round(5.0F * u), 0xFFF4F4F6);
            Px.textCenter(g, key, kx + kw / 2, ky + (kh - fs2) / 2, Px.Weight.BOLD, fs2, 0xFF141518);
            Px.text(g, acc, kx + kw + Math.round(6.0F * u), ky + (kh - fs2) / 2, Px.Weight.SEMIBOLD, fs2, 0xFFE5E7EB);
            right = kx - Math.round(8.0F * u);
            // how long the request stays on screen
            float left = 1.0F - Math.min(1.0F, (float) (now - c.born) / (float) c.life());
            int bw = w - r * 2;
            Px.round(
                    g,
                    x + r,
                    y + h - Math.max(2, Math.round(3.0F * u)),
                    Math.round(bw * left),
                    Math.max(2, Math.round(2.0F * u)),
                    1,
                    Px.a(c.colour & 0xFFFFFF, 160));
        }
        Px.text(
                g,
                Px.ellipsize(c.title, Px.Weight.SEMIBOLD, fs, right - tx),
                tx,
                y + Math.round(14.0F * u),
                Px.Weight.SEMIBOLD,
                fs,
                0xFFF4F4F6);
        Px.text(
                g,
                Px.ellipsize(c.sub, Px.Weight.MEDIUM, fs2, x + w - pad - tx),
                tx,
                y + Math.round(35.0F * u),
                Px.Weight.MEDIUM,
                fs2,
                0xFF9CA3AF);
    }

    public static boolean any() {
        return !CARDS.isEmpty() && Minecraft.getInstance().player != null;
    }
}
