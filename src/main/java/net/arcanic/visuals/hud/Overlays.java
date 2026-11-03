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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.mixin.BossHealthOverlayAccess;
import net.arcanic.visuals.mixin.PlayerTabOverlayAccess;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerScoreEntry;

public final class Overlays {
    private static final Comparator<PlayerScoreEntry> ORDER;

    private Overlays() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    static void line(GuiGraphics g, Component c, int x, int y, float k, int colour) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(k, k);
        g.drawString(mc().font, (Component) c, 0, 0, Px.am(colour, Px.fade), false);
        g.pose().popMatrix();
    }

    static void line(GuiGraphics g, FormattedCharSequence c, int x, int y, float k, int colour) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(k, k);
        g.drawString(mc().font, (FormattedCharSequence) c, 0, 0, Px.am(colour, Px.fade), false);
        g.pose().popMatrix();
    }

    static int width(Component c, float k) {
        return Math.round((float) mc().font.width((FormattedText) c) * k);
    }

    static float fontK(double scale) {
        return Math.max(1.0F, (float) Math.round((double) Px.guiScale() * scale * 2.0) / 2.0F);
    }

    static {
        ORDER = Comparator.comparing(PlayerScoreEntry::value)
                .reversed()
                .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    }

    public static final class BossBars extends HudModule {
        private final Anim anim = new Anim();

        public BossBars() {
            super(
                    "boss_bars",
                    "Боссбары",
                    "Полосы боссов и ивентов в стиле клиента, двигаются как HUD",
                    false,
                    0.422,
                    0.085);
            this.background.set(false);
        }

        static List<LerpingBossEvent> events() {
            return new ArrayList(((BossHealthOverlayAccess) Overlays.mc().gui.getBossOverlay())
                    .arcanicvisuals$events()
                    .values());
        }

        public boolean shown() {
            return !events().isEmpty();
        }

        private static int colour(BossEvent.BossBarColor c) {
            return switch (c) {
                case PINK -> -36939;
                case BLUE -> 0xFF5AA9FF;
                case RED -> -42396;
                case GREEN -> 0xFF4ADE80;
                case YELLOW -> -11686;
                case PURPLE -> 0xFFB07CFF;
                default -> 0xFFE8E8EE;
            };
        }

        private static int notches(BossEvent.BossBarOverlay o) {
            return switch (o) {
                case NOTCHED_6 -> 6;
                case NOTCHED_10 -> 10;
                case NOTCHED_12 -> 12;
                case NOTCHED_20 -> 20;
                default -> 0;
            };
        }

        public void render(GuiGraphics g, boolean editing) {
            this.anim.frame();
            List<LerpingBossEvent> list = events();
            float fk = Overlays.fontK(this.scale.get());
            int fh = Math.round(9.0F * fk);
            int bh = Math.max(3, this.U(3.5));
            int rowH = fh + this.U(3.0) + bh + this.U(6.0);
            int pad = this.U(5.0);
            this.w = this.U(150.0);
            boolean sample = list.isEmpty() && editing;
            int n = sample ? 1 : list.size();
            this.h = pad * 2 + n * rowH - this.U(6.0);
            if (n != 0) {
                this.plate(g, this.w, this.h);
                int y = pad;

                for (int i = 0; i < n; i++) {
                    Component name = (Component)
                            (sample
                                    ? Component.literal(Lang.tr("Боссбар"))
                                    : ((LerpingBossEvent) list.get(i)).getName());
                    float prog = sample ? 0.62F : ((LerpingBossEvent) list.get(i)).getProgress();
                    int col = sample ? 0xFFB07CFF : colour(((LerpingBossEvent) list.get(i)).getColor());
                    int marks = sample ? 0 : notches(((LerpingBossEvent) list.get(i)).getOverlay());
                    int tw = Overlays.width(name, fk);
                    Overlays.line(g, (Component) name, (this.w - tw) / 2, y, fk, -1);
                    int by = y + fh + this.U(3.0);
                    int bx = pad;
                    int bw = this.w - pad * 2;
                    String id = sample
                            ? "s"
                            : ((LerpingBossEvent) list.get(i)).getId().toString();
                    float shownP = this.anim.to("p:" + id, prog, 10.0F);
                    Px.round(g, pad, by, bw, bh, bh / 2, 654311423);
                    int fw = Math.max(bh, Math.round((float) bw * Math.max(0.0F, Math.min(1.0F, shownP))));
                    Px.glow(
                            g,
                            pad + fw - this.U(8.0),
                            by - this.U(5.0),
                            this.U(16.0),
                            bh + this.U(10.0),
                            Px.a(col, 70));
                    Px.pill(g, pad, by, fw, bh, Px.mix(col, 0xFF000000, 0.12F), Px.mix(col, -1, 0.3F));

                    for (int k = 1; k < marks; k++) {
                        int mx = bx + Math.round((float) (bw * k) / (float) marks);
                        Px.rect(g, mx, by, Math.max(1, this.U(0.6)), bh, 1711276032);
                    }

                    y += rowH;
                }
            }
        }
    }

    public static final class TabList extends Module {
        final Setting.Bool pingText = this.add(new Setting.Bool("ping_ms", "Пинг числом", false));
        private float open;
        private long last;

        public TabList() {
            super(
                    "tab_list",
                    "Таб",
                    "Список игроков в стиле клиента: головы, пинг полосками, ты выделен",
                    Category.HUD,
                    false);
        }

        public void render(GuiGraphics g) {
            Minecraft mc = Overlays.mc();
            long now = System.nanoTime();
            float dt = this.last == 0L ? 0.0F : Math.min(0.1F, (float) (now - this.last) / 1.0E9F);
            this.last = now;
            boolean want = mc.options.keyPlayerList.isDown() && mc.getConnection() != null && !mc.options.hideGui;
            this.open += ((float) (want ? 1 : 0) - this.open) * (1.0F - (float) Math.exp((double) (-dt * 16.0F)));
            if (!(this.open < 0.01F) && mc.getConnection() != null) {
                PlayerTabOverlay tab = mc.gui.getTabList();
                PlayerTabOverlayAccess acc = (PlayerTabOverlayAccess) tab;
                List<PlayerInfo> players = acc.arcanicvisuals$players();
                Px.begin(g);
                float old = Px.fade;
                Px.fade = old * Math.min(1.0F, this.open);

                try {
                    this.draw(g, players, acc.arcanicvisuals$header(), acc.arcanicvisuals$footer(), tab);
                } finally {
                    Px.fade = old;
                    Px.end(g);
                }
            }
        }

        private void draw(
                GuiGraphics g, List<PlayerInfo> players, Component header, Component footer, PlayerTabOverlay tab) {
            Minecraft mc = Overlays.mc();
            int sw = Px.width();
            float u = (float) ((double) Px.height() / 1080.0);
            float fk = Overlays.fontK(1.0);
            int fh = Math.round(9.0F * fk);
            int face = fh;
            int pad = Math.round(14.0F * u);
            int cellH = fh + Math.round(6.0F * u);
            int rows = Math.max(1, Math.min(20, players.size()));
            int cols = (players.size() + 19) / 20;
            if (cols > 0) {
                rows = (players.size() + cols - 1) / cols;
            }

            int nameW = 0;
            List<Component> names = new ArrayList<>();

            for (PlayerInfo p : players) {
                Component n = Overlays.Streamer.hide(tab.getNameForDisplay(p));
                names.add(n);
                nameW = Math.max(nameW, Overlays.width(n, fk));
            }

            int pingW = this.pingText.on()
                    ? Px.textWidth("999 мс", Px.Weight.MEDIUM, Math.round(11.0F * u))
                    : Math.round(16.0F * u);
            int cellW = fh + Math.round(6.0F * u) + nameW + Math.round(10.0F * u) + pingW;
            int gridW = cols * cellW + Math.max(0, cols - 1) * Math.round(8.0F * u);
            int maxW = Math.round((float) sw * 0.8F);
            List<FormattedCharSequence> head =
                    header == null ? List.of() : mc.font.split(header, Math.round((float) maxW / fk));
            List<FormattedCharSequence> foot =
                    footer == null ? List.of() : mc.font.split(footer, Math.round((float) maxW / fk));
            int textW = 0;

            for (FormattedCharSequence l : head) {
                textW = Math.max(textW, Math.round((float) mc.font.width(l) * fk));
            }

            for (FormattedCharSequence l : foot) {
                textW = Math.max(textW, Math.round((float) mc.font.width(l) * fk));
            }

            int w = Math.max(gridW, textW) + pad * 2;
            int h = pad
                    + head.size() * fh
                    + (head.isEmpty() ? 0 : Math.round(8.0F * u))
                    + rows * cellH
                    + (foot.isEmpty() ? 0 : Math.round(8.0F * u) + foot.size() * fh)
                    + pad;
            int x = (sw - w) / 2;
            int y = Math.round(10.0F * u) + Math.round((1.0F - this.open) * -8.0F * u);
            Px.shadow(g, x, y + Math.round(4.0F * u), w, h, Math.round(16.0F * u), 1073741824);
            Px.round(g, x, y, w, h, Math.round(12.0F * u), Theme.p().bg() & 0xFFFFFF | 0xE6000000);
            Px.outline(g, x, y, w, h, Math.round(12.0F * u), 587202559);
            int cy = y + pad;

            for (FormattedCharSequence l : head) {
                Overlays.line(
                        g,
                        (FormattedCharSequence) l,
                        x + (w - Math.round((float) mc.font.width(l) * fk)) / 2,
                        cy,
                        fk,
                        -1);
                cy += fh;
            }

            if (!head.isEmpty()) {
                cy += Math.round(8.0F * u);
            }

            int gx = x + (w - gridW) / 2;
            LocalPlayer me = mc.player;

            for (int i = 0; i < players.size(); i++) {
                PlayerInfo p = (PlayerInfo) players.get(i);
                int col = i / rows;
                int row = i % rows;
                int px = gx + col * (cellW + Math.round(8.0F * u));
                int py = cy + row * cellH;
                boolean self = me != null && p.getProfile().id().equals(me.getUUID());
                if (self) {
                    Px.round(
                            g,
                            px - Math.round(4.0F * u),
                            py,
                            cellW + Math.round(8.0F * u),
                            cellH - Math.round(2.0F * u),
                            Math.round(6.0F * u),
                            Px.a(Theme.accent(), 60));
                } else if (row % 2 == 1) {
                    Px.round(
                            g,
                            px - Math.round(4.0F * u),
                            py,
                            cellW + Math.round(8.0F * u),
                            cellH - Math.round(2.0F * u),
                            Math.round(6.0F * u),
                            184549375);
                }

                int fy = py + (cellH - Math.round(2.0F * u) - face) / 2;
                PlayerFaceRenderer.draw(g, p.getSkin(), px, fy, face, Math.round(255.0F * Px.fade) << 24 | 0xFFFFFF);
                Overlays.line(
                        g,
                        (Component) names.get(i),
                        px + face + Math.round(6.0F * u),
                        fy,
                        fk,
                        p.getGameMode() == GameType.SPECTATOR ? 0xFF8A8A92 : -1);
                this.ping(g, p.getLatency(), px + cellW - pingW, fy, pingW, face, u);
            }

            cy += rows * cellH;
            if (!foot.isEmpty()) {
                cy += Math.round(8.0F * u);

                for (FormattedCharSequence l : foot) {
                    Overlays.line(
                            g,
                            (FormattedCharSequence) l,
                            x + (w - Math.round((float) mc.font.width(l) * fk)) / 2,
                            cy,
                            fk,
                            -1);
                    cy += fh;
                }
            }
        }

        private void ping(GuiGraphics g, int ms, int x, int y, int w, int h, float u) {
            int col = ms < 0 ? 0xFF8A8A92 : (ms < 80 ? 0xFF4ADE80 : (ms < 160 ? 0xFFFACC15 : -42396));
            if (this.pingText.on()) {
                String t = ms < 0 ? "—" : ms + " мс";
                int fs = Math.round(11.0F * u);
                Px.textRight(g, t, x + w, y + (h - fs) / 2, Px.Weight.MEDIUM, fs, col);
            } else {
                int bars = ms < 0 ? 0 : (ms < 80 ? 4 : (ms < 160 ? 3 : (ms < 300 ? 2 : 1)));
                int bw = Math.max(2, Math.round(2.5F * u));
                int gap = Math.max(1, Math.round(1.5F * u));
                int bx = x + w - 4 * bw - 3 * gap;

                for (int i = 0; i < 4; i++) {
                    int bh = Math.round((float) h * (0.35F + 0.65F * (float) (i + 1) / 4.0F));
                    Px.round(g, bx + i * (bw + gap), y + h - bh, bw, bh, bw / 2, i < bars ? col : 721420287);
                }
            }
        }
    }

    public static final class Streamer extends Module {
        final Setting.Text alias =
                this.add(new Setting.Text("alias", "Ник для показа", "Arcanic", "Как подписывать тебя", 16));
        final Setting.Bool nick = this.add(new Setting.Bool("nick", "Скрывать ник", true));
        final Setting.Bool coords = this.add(new Setting.Bool("coords", "Скрывать координаты", true));
        final Setting.Bool server = this.add(new Setting.Bool("server", "Скрывать сервер", true));

        public Streamer() {
            super("streamer", "Режим стримера", "Скрывает ник, координаты и сервер на экране", Category.UTILITY, false);
        }

        private static boolean on() {
            return Modules.STREAMER != null && Modules.STREAMER.enabled();
        }

        public static boolean hidesCoords() {
            return on() && Modules.STREAMER.coords.on();
        }

        public static boolean hidesServer() {
            return on() && Modules.STREAMER.server.on();
        }

        public static String name(String real) {
            return on() && Modules.STREAMER.nick.on() && !((String) Modules.STREAMER.alias.get()).isBlank()
                    ? (String) Modules.STREAMER.alias.get()
                    : real;
        }

        public static Component hide(Component c) {
            LocalPlayer p = Overlays.mc().player;
            if (on() && Modules.STREAMER.nick.on() && p != null && c != null) {
                String nick = p.getName().getString();
                String to = name(nick);
                if (!to.equals(nick) && c.getString().contains(nick)) {
                    MutableComponent out = Component.empty();
                    c.visit(
                            (style, s) -> {
                                out.append((Component)
                                        Component.literal(s.replace(nick, to)).withStyle(style));
                                return Optional.empty();
                            },
                            Style.EMPTY);
                    return out;
                } else {
                    return c;
                }
            } else {
                return c;
            }
        }
    }
}
