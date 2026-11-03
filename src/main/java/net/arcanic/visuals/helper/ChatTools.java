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
package net.arcanic.visuals.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.gui.PromptScreen;
import net.arcanic.visuals.hud.DynamicIsland;
import net.arcanic.visuals.hud.Notify;
import net.arcanic.visuals.hud.Overlays;
import net.arcanic.visuals.mixin.ChatComponentAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class ChatTools {
    private static final String RU = "йцукенгшщзхъфывапролджэячсмитьбюё";
    private static final String EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`";

    private ChatTools() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static String rewrite(String msg) {
        if (msg == null) {
            return null;
        } else {
            Helper h = Modules.CHAT_HELPER;
            if (h != null && h.enabled() && h.layout.on()) {
                msg = layoutFix(msg);
            }

            Auction a = Modules.AUCTION;
            if (a != null && a.enabled() && a.amounts.on()) {
                msg = a.amounts(msg);
            }

            return msg;
        }
    }

    public static boolean send(String msg) {
        Safe s = Modules.COMMAND_SAFE;
        return s == null || !s.enabled() || !s.hold(msg);
    }

    private static boolean cyrillic(String w) {
        for (int i = 0; i < w.length(); ++i) {
            if ("йцукенгшщзхъфывапролджэячсмитьбюё".indexOf(Character.toLowerCase(w.charAt(i))) < 0) {
                return false;
            }
        }

        return true;
    }

    private static String latin(String w) {
        StringBuilder b = new StringBuilder();

        for (int i = 0; i < w.length(); ++i) {
            b.append("qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
                    .charAt("йцукенгшщзхъфывапролджэячсмитьбюё".indexOf(Character.toLowerCase(w.charAt(i)))));
        }

        return b.toString();
    }

    private static boolean known(String command) {
        ClientPacketListener conn = mc().getConnection();
        return conn != null && conn.getCommands().getRoot().getChild(command) != null;
    }

    static String layoutFix(String msg) {
        String t = msg.trim();
        if (t.length() >= 2 && (t.charAt(0) == '/' || t.charAt(0) == '.')) {
            String body = t.substring(1);
            int sp = body.indexOf(32);
            String word = sp < 0 ? body : body.substring(0, sp);
            if (!word.isEmpty() && cyrillic(word)) {
                String en = latin(word);
                return !known(en) ? msg : "/" + en + (sp < 0 ? "" : body.substring(sp));
            } else {
                return msg;
            }
        } else {
            return msg;
        }
    }

    public static Component incoming(ChatComponent chat, Component msg) {
        Helper h = Modules.CHAT_HELPER;
        if (h != null && h.enabled() && msg != null) {
            String text = msg.getString();
            if (text.startsWith("Arcanic")) {
                return msg;
            } else {
                long now = System.currentTimeMillis();
                if (h.antispam.on()) {
                    List<GuiMessage> all = ((ChatComponentAccess) chat).arcanicvisuals$all();
                    if (!text.isBlank()
                            && text.equals(h.lastText)
                            && now - h.lastAt < 120000L
                            && !all.isEmpty()
                            && ((GuiMessage) all.get(0)).content().getString().startsWith(text)) {
                        all.remove(0);
                        ((ChatComponentAccess) chat).arcanicvisuals$refresh();
                        ++h.repeats;
                        h.lastAt = now;
                        msg = msg.copy().append((Component)
                                Component.literal("  ×" + h.repeats).withStyle(ChatFormatting.DARK_GRAY));
                        return msg;
                    }

                    h.lastText = text;
                    h.repeats = 1;
                    h.lastAt = now;
                }

                if (h.mentions.on()) {
                    msg = h.mention(msg, text);
                }

                return Overlays.Streamer.hide(msg);
            }
        } else {
            return Overlays.Streamer.hide(msg);
        }
    }

    public static boolean keepChat() {
        Helper h = Modules.CHAT_HELPER;
        return h != null && h.enabled() && h.keep.on();
    }

    static Component paint(Component msg, List<int[]> ranges, UnaryOperator<Style> how) {
        record Part(String text, Style style) {}
        List<Part> parts = new ArrayList<>();
        msg.visit(
                (style, s) -> {
                    parts.add(new Part(s, style));
                    return Optional.empty();
                },
                Style.EMPTY);
        MutableComponent out = Component.empty();
        int at = 0;

        for (Part p : parts) {
            int start = at;
            int end = at + p.text().length();

            int stop;
            for (int i = at; i < end; i = stop) {
                int[] in = null;

                for (int[] r : ranges) {
                    if (i >= r[0] && i < r[1]) {
                        in = r;
                    }
                }

                stop = end;
                if (in != null) {
                    stop = Math.min(end, in[1]);
                } else {
                    for (int[] r : ranges) {
                        if (r[0] > i && r[0] < stop) {
                            stop = r[0];
                        }
                    }
                }

                out.append((Component) Component.literal(p.text().substring(i - start, stop - start))
                        .withStyle(in != null ? (Style) how.apply(p.style()) : p.style()));
            }

            at = end;
        }

        return out;
    }

    public static final class Helper extends Module {
        final Setting.Bool layout = this.add(new Setting.Bool("layout", "Исправлять раскладку команд", true));
        final Setting.Bool antispam = this.add(new Setting.Bool("antispam", "Сворачивать повторы", true));
        final Setting.Bool keep = this.add(new Setting.Bool("keep", "Не очищать чат при выходе", true));
        final Setting.Bool mentions = this.add(new Setting.Bool("mentions", "Подсвечивать мой ник", true));
        final Setting.Bool sound;
        final Setting.Text words;
        String lastText;
        int repeats;
        long lastAt;
        long pingAt;

        public Helper() {
            super(
                    "chat_helper",
                    "Помощник чата",
                    "Раскладка команд (/рги → /hub), повторы ×N, сохранение чата и подсветка ника",
                    Category.UTILITY,
                    true);
            Setting.Bool var10001 = this.add(new Setting.Bool("sound", "Звук упоминания", true));
            Setting.Bool var10002 = this.mentions;
            this.sound = (Setting.Bool) var10001.showIf(var10002::on);
            Setting.Text var1 = this.add(new Setting.Text("words", "Ещё слова", "", "Через запятую", 120));
            var10002 = this.mentions;
            this.words = (Setting.Text) var1.showIf(var10002::on);
        }

        Component mention(Component msg, String text) {
            LocalPlayer p = ChatTools.mc().player;
            if (p == null) {
                return msg;
            } else {
                List<String> names = new ArrayList<>();
                names.add(p.getName().getString());

                for (String w : ((String) this.words.get()).split(",")) {
                    if (!w.isBlank()) {
                        names.add(w.trim());
                    }
                }

                String low = text.toLowerCase(Locale.ROOT);
                int sep = -1;

                for (String s : new String[] {": ", "» ", "> ", "→ ", "-> "}) {
                    int i = low.indexOf(s);
                    if (i >= 0 && (sep < 0 || i < sep)) {
                        sep = i;
                    }
                }

                List<int[]> hits = new ArrayList<>();

                for (String n : names) {
                    if (n.length() >= 3) {
                        Matcher m = Pattern.compile("(?<![\\p{L}\\d_])" + Pattern.quote(n.toLowerCase(Locale.ROOT))
                                        + "(?![\\p{L}\\d_])")
                                .matcher(low);

                        while (m.find()) {
                            if (sep < 0 || m.start() > sep) {
                                hits.add(new int[] {m.start(), m.end()});
                            }
                        }
                    }
                }

                if (hits.isEmpty()) {
                    return msg;
                } else {
                    long now = System.currentTimeMillis();
                    if (this.sound.on() && now - this.pingAt > 1500L) {
                        UiSound.play("select", 1.35F, 0.85F);
                        this.pingAt = now;
                    }

                    int acc = Theme.accent2() | 0xFF000000;
                    return ChatTools.paint(msg, hits, (st) -> st.withColor(TextColor.fromRgb(acc & 0xFFFFFF))
                            .withBold(true));
                }
            }
        }
    }

    public static final class Safe extends Module {
        final Setting.Text list = this.add(new Setting.Text(
                "list",
                "Команды",
                "suicide, kill, clear, ci, clearinventory, pay, delhome, sellall",
                "Через запятую",
                200));
        private String pending;

        public Safe() {
            super(
                    "command_safe",
                    "Защита команд",
                    "Подтверждение опасных команд: /suicide, /clear, /pay и свои",
                    Category.UTILITY,
                    true);
        }

        boolean hold(String msg) {
            String t = msg == null ? "" : msg.trim();
            if (t.startsWith("/") && t.length() >= 2) {
                String word = t.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
                if (word.startsWith("minecraft:")) {
                    word = word.substring(10);
                }

                for (String w : ((String) this.list.get()).split(",")) {
                    if (word.equals(w.trim().toLowerCase(Locale.ROOT).replaceFirst("^/", ""))) {
                        this.pending = t;
                        return true;
                    }
                }

                return false;
            } else {
                return false;
            }
        }

        public void tick() {
            if (this.pending != null && ChatTools.mc().screen == null) {
                String cmd = this.pending;
                this.pending = null;
                UiSound.play("pop");
                ChatTools.mc()
                        .setScreen(PromptScreen.ask(
                                (Screen) null,
                                "Точно выполнить?",
                                Lang.tr("Команда") + " " + cmd + " " + Lang.tr("может быть необратимой."),
                                "Выполнить",
                                () -> {
                                    ClientPacketListener conn = ChatTools.mc().getConnection();
                                    if (conn != null) {
                                        ChatTools.mc().gui.getChat().addRecentChat(cmd);
                                        conn.sendCommand(cmd.substring(1));
                                    }
                                }));
            }
        }
    }

    public static final class Auction extends Module {
        final Setting.Bool amounts = this.add(new Setting.Bool("amounts", "Сокращения сумм (1к, 2.5м)", true));
        final Setting.Mode key = this.add(
                new Setting.Mode("key", "Искать предмет в руке", "Нет", new String[] {"Нет", "H", "J", "K", "U"}));
        final Setting.Text command = (this.add(
                        new Setting.Text("command", "Команда поиска", "/ah search {item}", "{item} — название", 80)))
                .showIf(() -> !this.key.is("Нет"));
        private boolean wasDown;
        private static final Pattern SELL =
                Pattern.compile("(?i)^(/(?:ah|auction|аук)\\s+(?:sell|resell)\\s+)(\\S+)(.*)$");
        private static final Pattern PAY = Pattern.compile("(?i)^(/pay\\s+\\S+\\s+)(\\S+)(.*)$");
        private static final Pattern AMOUNT = Pattern.compile("(?i)^(\\d+(?:[.,]\\d+)?)(k|к|kk|кк|m|м)$");

        public Auction() {
            super(
                    "auction_helper",
                    "Помощник аукциона",
                    "1к → 1000 в /ah sell и /pay, поиск предмета из руки по клавише",
                    Category.UTILITY,
                    false);
        }

        String amounts(String msg) {
            for (Pattern p : new Pattern[] {SELL, PAY}) {
                Matcher m = p.matcher(msg.trim());
                if (m.matches()) {
                    Matcher a = AMOUNT.matcher(m.group(2));
                    if (!a.matches()) {
                        return msg;
                    }

                    double v = Double.parseDouble(a.group(1).replace(',', '.'));
                    String u = a.group(2).toLowerCase(Locale.ROOT);
                    v *= !u.equals("k") && !u.equals("к") ? 1000000.0 : 1000.0;
                    String var10000 = m.group(1);
                    return var10000 + Math.round(v) + m.group(3);
                }
            }

            return msg;
        }

        public void tick() {
            byte var10000;
            switch ((String) this.key.get()) {
                case "H" -> var10000 = 72;
                case "J" -> var10000 = 74;
                case "K" -> var10000 = 75;
                case "U" -> var10000 = 85;
                default -> var10000 = -1;
            }

            int code = var10000;
            boolean down = code >= 0
                    && ChatTools.mc().screen == null
                    && GLFW.glfwGetKey(ChatTools.mc().getWindow().handle(), code) == 1;
            if (down && !this.wasDown) {
                this.search();
            }

            this.wasDown = down;
        }

        private void search() {
            LocalPlayer p = ChatTools.mc().player;
            ClientPacketListener conn = ChatTools.mc().getConnection();
            if (p != null && conn != null) {
                ItemStack st = p.getMainHandItem();
                if (st.isEmpty()) {
                    Notify.text("Аукцион: возьми предмет в руку", false);
                } else {
                    String name = ChatFormatting.stripFormatting(
                                    st.getHoverName().getString())
                            .trim();
                    String cmd = ((String) this.command.get())
                            .replace("{item}", name)
                            .trim();
                    if (cmd.startsWith("/")) {
                        conn.sendCommand(cmd.substring(1));
                    } else {
                        conn.sendChat(cmd);
                    }

                    DynamicIsland.alert(
                            "auction", st, Lang.tr("Ищу на аукционе"), name, "", Theme.accent2() | 0xFF000000);
                }
            }
        }
    }
}
