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

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.hud.DynamicIsland;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * FunTime events: on joining an anarchy the helper asks the server ({@code /event delay}), keeps that answer out
 * of the chat, puts marks on the events' coordinates and tells what is on or coming in the island. Events the
 * server announces in the chat get a mark too.
 */
public final class FtEvents extends Module {
    private final Setting.Bool ask = this.add(new Setting.Bool("ask", "Спрашивать ивенты при входе", true));
    private final Setting.Bool marks = this.add(new Setting.Bool("marks", "Метки ивентов", true));
    private final Setting.Bool notices = this.add(new Setting.Bool("notices", "Уведомления в островке", true));
    private final Setting.Bool hide = this.add(new Setting.Bool("hide", "Прятать ответ сервера", true));
    private final Setting.Bool warn = this.add(new Setting.Bool("warn", "Спец. предметы на анархии", true));

    /** One event of the anarchy, as the server told it: for the list on the helpers page. */
    public static final class Entry {
        public final String name;
        public String status;
        public String rarity;
        public int[] at;
        public long startsAt;
        public boolean on;

        Entry(String name) {
            this.name = name;
        }
    }

    private final java.util.List<Entry> events = new java.util.concurrent.CopyOnWriteArrayList<>();
    private Entry current;
    private int listedOn = -1;

    private static final Set<String> SKIPPED = Set.of("сундук смерти", "адская резня");
    private static final Pattern LIST_ROW = Pattern.compile("\\[\\d+].+");
    private static final Pattern ROW_NAME = Pattern.compile("\\[(\\d+)]\\s*(.+?):");
    private static final Pattern NEXT_IN =
            Pattern.compile("до следующего ивента:\\s*(?:(\\d+)\\s*мин)?\\s*(?:(\\d+)\\s*сек)?");
    private static final Pattern TITLE = Pattern.compile("\\|{3}\\s*\\[(.+?)]\\s*\\|{3}");
    private static final Pattern APPEARED =
            Pattern.compile("появился на координатах\\s*(?:\\[)?(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)(?:])?");
    private static final Pattern QUOTED = Pattern.compile("«\\s*(.+?)\\s*»");
    private static final Pattern AT_COORDS = Pattern.compile("на координатах\\s*\\[(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)]");
    private static final Pattern COORDS = Pattern.compile("\\[(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)]");
    private static final String PHASES =
            "(до активации|до открытия|до закрытия|до извержения|до аварии|на подходе|до конца|открытие через|"
                    + "закрытие через|начнётся через|начнется через|осталось)";
    private static final Pattern PHASE_TIME =
            Pattern.compile(PHASES + ":?\\s*(?:(\\d+)\\s*ч\\s*)?(?:(\\d+)\\s*мин\\s*)?(?:(\\d+)\\s*сек)?");
    private static final Pattern PHASE_SECONDS = Pattern.compile(PHASES + ":?\\s*(\\d+)\\s*сек");
    private static final long ANSWER_WINDOW = 1000L;
    private static final long EVENT_LIFE = 600000L;

    private Object level;
    private boolean askPending;
    private boolean waiting;
    private long listStartedAt;
    private String row;
    private int rowSeconds;
    private String rarity;
    private String status;
    private boolean anyPlaced;
    private boolean soonSeen;
    private String soonName;
    private int soonSeconds;
    private int nextSeconds;
    private boolean done;
    private boolean warnedOff;
    private long warnedAt;
    private String[] queued;
    private long queuedAt;

    public FtEvents() {
        super(
                "ft_events",
                "Ивенты FunTime",
                "Сам спрашивает ивенты на анархии, ставит метки на их координаты и пишет в островке, что идёт",
                Category.FUNTIME,
                true);
    }

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register(
                (msg, overlay) -> overlay || Modules.FT_EVENTS.heard(msg.getString()));
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public void tick() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;
        FtApi.tick();
        if (this.level != mc.level) {
            this.level = mc.level;
            this.askPending = FunTime.here();
        }
        if (this.queued != null && System.currentTimeMillis() >= this.queuedAt) {
            this.say(this.queued[0], this.queued[1], this.queued[2]);
            this.queued = null;
        }
        if (!FunTime.here() || !loggedIn()) return;
        if (this.askPending && mc.player.tickCount > 6) {
            this.askPending = false;
            int anarchy = FunTime.anarchy();
            if (anarchy == -1) return;
            this.warnedOff = false;
            if (FunTime.specialItemsOff(anarchy) && this.warn.on()) {
                DynamicIsland.notice("Внимание: на этой анархии не работают спец. предметы", false);
                this.warnedOff = true;
                this.warnedAt = System.currentTimeMillis();
            }
            if (this.ask.on()) {
                ClientPacketListener conn = mc.getConnection();
                if (conn != null) {
                    conn.sendCommand("event delay");
                    this.waiting = true;
                }
            }
        }
        this.finishList();
    }

    /** The player has a coloured name once they are past FunTime's login lobby. */
    private static boolean loggedIn() {
        String shown = mc().player.getDisplayName().getString();
        return !shown.equals(ChatFormatting.stripFormatting(shown));
    }

    /** Reads a chat line; false hides it (only the answer to our own question). */
    boolean heard(String raw) {
        if (!this.enabled() || !FunTime.here() || mc().player == null) return true;
        String line = ChatFormatting.stripFormatting(raw);
        if (line == null) return true;
        String s = line.toLowerCase(Locale.ROOT);
        boolean ours = this.waiting;
        boolean read = false;
        long now = System.currentTimeMillis();
        boolean inList = this.listStartedAt > 0L && now - this.listStartedAt < ANSWER_WINDOW;
        if (s.contains("[ивенты]")) {
            if (this.marks.on()) Modules.MARKS.clearFunTimeEvents();
            this.events.clear();
            this.current = null;
            this.listedOn = FunTime.anarchy();
            this.row = null;
            this.rowSeconds = 0;
            this.rarity = null;
            this.status = null;
            this.anyPlaced = false;
            this.soonSeen = false;
            this.soonName = null;
            this.soonSeconds = 0;
            this.nextSeconds = 0;
            this.done = false;
            this.listStartedAt = now;
            read = true;
        } else if (inList && LIST_ROW.matcher(s).matches()) {
            this.listRow(s);
            read = true;
        } else if (inList
                && (s.contains("статус:")
                        || s.contains("координаты:")
                        || s.contains("уровень лута:")
                        || s.contains("редкость:")
                        || s.contains("/event vote"))) {
            this.detail(s, line);
            read = true;
        }
        if (s.contains("появился на координатах")) {
            this.appeared(line);
            read = true;
        }
        if (s.contains("на координатах") && s.contains("«") && s.contains("»")) {
            this.quoted(line);
            read = true;
        }
        return !(read && ours && this.hide.on());
    }

    private void listRow(String s) {
        if (s.contains("до следующего ивента:")) {
            int seconds = nextIn(s);
            if (seconds > 0) this.nextSeconds = seconds;
            return;
        }
        Matcher m = ROW_NAME.matcher(s);
        if (m.find()) {
            String name = m.group(2).trim();
            if (skipped(name)) {
                this.row = null;
                return;
            }
            this.row = name;
            this.rowSeconds = 0;
            this.current = new Entry(title(name));
            this.events.add(this.current);
        }
    }

    private void detail(String s, String original) {
        if (s.contains("статус:")) {
            String st = s.substring(s.indexOf("статус:") + "статус:".length())
                    .replace("»", "")
                    .trim();
            if (this.row == null) return;
            this.status = phaseName(st);
            int seconds = phaseSeconds(st);
            if (this.current != null) {
                this.current.status = this.status;
                if (seconds > 0) this.current.startsAt = System.currentTimeMillis() + seconds * 1000L;
            }
            if (seconds > 0) {
                this.rowSeconds = seconds;
                if (this.marks.on()) {
                    Modules.MARKS.funTimeStarts(title(this.row), System.currentTimeMillis() + seconds * 1000L);
                }
                if (!this.soonSeen) {
                    this.soonSeen = true;
                    this.soonName = this.row;
                    this.soonSeconds = seconds;
                }
            }
        } else if (s.contains("уровень лута:") || s.contains("редкость:")) {
            String key = s.contains("уровень лута:") ? "уровень лута:" : "редкость:";
            this.rarity = title(
                    s.substring(s.indexOf(key) + key.length()).replace("»", "").trim());
            if (this.current != null) this.current.rarity = this.rarity;
        } else if (s.contains("координаты:")) {
            int[] at = coords(s.substring(s.indexOf("координаты:") + "координаты:".length()));
            if (at != null && this.row != null) {
                if (this.current != null) {
                    this.current.at = at;
                    this.current.on = true;
                }
                this.place(this.row, at, this.rowSeconds);
                this.answer("Текущий ивент", title(this.row), this.rarity != null ? this.rarity : this.status);
                this.anyPlaced = true;
            }
        } else if (s.contains("/event vote")) {
            this.answer("Ивенты", "Идёт голосование", "/event vote");
            this.done = true;
        }
    }

    /** The list is over a second after it started: say what is coming if nothing is on. */
    private void finishList() {
        if (this.listStartedAt <= 0L || this.done) return;
        if (System.currentTimeMillis() - this.listStartedAt < ANSWER_WINDOW) return;
        if (!this.anyPlaced) {
            if (this.soonSeen && this.soonName != null) {
                this.answer("Скоро ивент", title(this.soonName), duration(this.soonSeconds));
            } else if (this.nextSeconds > 0) {
                this.answer("Следующий ивент", "Начнётся через", duration(this.nextSeconds));
            }
        }
        this.waiting = false;
        this.done = true;
        this.listStartedAt = 0L;
    }

    private void appeared(String line) {
        String name = null;
        int[] at = null;
        for (String part : line.split("\\n")) {
            Matcher t = TITLE.matcher(part.trim());
            if (t.find()) name = t.group(1).trim();
            Matcher c = APPEARED.matcher(part.trim().toLowerCase(Locale.ROOT));
            if (c.find()) at = new int[] {num(c, 1), num(c, 2), num(c, 3)};
        }
        if (name != null && at != null && !skipped(name)) {
            this.seen(name, at);
            this.place(name, at, 0);
            this.say("Ивент", title(name), coordsText(at));
        }
    }

    private void quoted(String line) {
        Matcher n = QUOTED.matcher(line);
        Matcher c = AT_COORDS.matcher(line.toLowerCase(Locale.ROOT));
        if (n.find() && c.find()) {
            String name = n.group(1).trim();
            if (!skipped(name)) {
                int[] at = {num(c, 1), num(c, 2), num(c, 3)};
                this.seen(name, at);
                this.place(name, at, 0);
                this.say("Ивент", title(name), coordsText(at));
            }
        }
    }

    /** An event announced in the chat: on now, at these coordinates. */
    private void seen(String name, int[] at) {
        String t = title(name);
        for (Entry e : this.events) {
            if (e.name.equalsIgnoreCase(t)) {
                e.at = at;
                e.on = true;
                return;
            }
        }
        Entry e = new Entry(t);
        e.at = at;
        e.on = true;
        this.events.add(0, e);
    }

    /** The anarchy's events from the last answer, while you are still on it. */
    public java.util.List<Entry> events() {
        if (!FunTime.here() || FunTime.anarchy() != this.listedOn && this.listedOn != -1) return java.util.List.of();
        return this.events;
    }

    /** Asks the server again (the button on the events page). */
    public void refresh() {
        ClientPacketListener conn = mc().getConnection();
        if (conn == null || !FunTime.here()) return;
        conn.sendCommand("event delay");
        this.waiting = true;
    }

    /** A mark on an event from its row on the events page. */
    public void mark(Entry e) {
        if (e.at == null) return;
        Modules.MARKS.funTimeEvent(
                e.name,
                e.at[0] + 0.5,
                e.at[1],
                e.at[2] + 0.5,
                e.startsAt,
                Math.max(e.startsAt, System.currentTimeMillis()) + EVENT_LIFE);
        DynamicIsland.notice("Метка: " + e.name, true);
    }

    private void place(String name, int[] at, int seconds) {
        if (!this.marks.on()) return;
        long now = System.currentTimeMillis();
        long starts = seconds > 0 ? now + seconds * 1000L : 0L;
        Modules.MARKS.funTimeEvent(
                title(name), at[0] + 0.5, at[1], at[2] + 0.5, starts, Math.max(starts, now) + EVENT_LIFE);
    }

    /** An answer to our own question only; a special-items warning goes first and this waits behind it. */
    private void answer(String head, String what, String chip) {
        if (!this.waiting) return;
        this.waiting = false;
        if (this.warnedOff) {
            this.queued = new String[] {head, what, chip};
            this.queuedAt = this.warnedAt + 7000L;
            this.warnedOff = false;
        } else {
            this.say(head, what, chip);
        }
    }

    private void say(String head, String what, String chip) {
        if (!this.notices.on()) return;
        String text = head + ": " + what + (chip == null || chip.isEmpty() ? "" : " · " + chip);
        DynamicIsland.notice(text, true);
    }

    private static boolean skipped(String name) {
        return SKIPPED.contains(name.toLowerCase(Locale.ROOT));
    }

    private static int nextIn(String s) {
        Matcher m = NEXT_IN.matcher(s);
        if (!m.find()) return 0;
        int out = 0;
        if (m.group(1) != null) out += Integer.parseInt(m.group(1)) * 60;
        if (m.group(2) != null) out += Integer.parseInt(m.group(2));
        return out;
    }

    private static int phaseSeconds(String s) {
        Matcher m = PHASE_TIME.matcher(s);
        if (m.find()) {
            int out = 0;
            if (m.group(2) != null) out += Integer.parseInt(m.group(2)) * 3600;
            if (m.group(3) != null) out += Integer.parseInt(m.group(3)) * 60;
            if (m.group(4) != null) out += Integer.parseInt(m.group(4));
            if (out > 0) return out;
        }
        Matcher sec = PHASE_SECONDS.matcher(s);
        return sec.find() ? Integer.parseInt(sec.group(2)) : 0;
    }

    private static String phaseName(String s) {
        String out = s.replaceAll("\\d+\\s*ч", "")
                .replaceAll("\\d+\\s*мин", "")
                .replaceAll("\\d+\\s*сек", "")
                .replaceAll(",?\\s*" + PHASES + ":?", "")
                .replaceAll(",?\\s*до подарков:?", "")
                .replaceAll(",\\s*$", "")
                .replaceAll(":$", "")
                .trim();
        return out.isEmpty() ? null : title(out);
    }

    private static int[] coords(String s) {
        Matcher m = COORDS.matcher(s);
        return m.find() ? new int[] {num(m, 1), num(m, 2), num(m, 3)} : null;
    }

    private static int num(Matcher m, int group) {
        return Integer.parseInt(m.group(group));
    }

    private static String coordsText(int[] at) {
        return at[0] + " " + at[1] + " " + at[2];
    }

    private static String title(String s) {
        return s == null || s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static String duration(int seconds) {
        if (seconds < 60) return seconds + " сек";
        int min = seconds / 60;
        int sec = seconds % 60;
        return sec > 0 ? min + " мин " + sec + " сек" : min + " мин";
    }
}
