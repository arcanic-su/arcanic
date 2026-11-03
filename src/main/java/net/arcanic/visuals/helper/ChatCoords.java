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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class ChatCoords extends Module {
    private final Setting.Bool paint = this.add(new Setting.Bool("paint", "Красить координаты", true));
    private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true, -6587393));
    private final Setting.Bool underline = this.add(new Setting.Bool("underline", "Подчёркивание", true));
    private final Setting.Bool hint = this.add(new Setting.Bool("hint", "Подсказка при наведении", true));
    private final Setting.Bool flat = this.add(new Setting.Bool("flat", "Без высоты тоже (x … z …)", true));
    private static final Pattern LABELLED = Pattern.compile(
            "(?iu)(?<![\\p{L}\\d])x\\s*[:=]?\\s*(-?\\d{1,8})[\\s,;]*(?:y\\s*[:=]?\\s*(-?\\d{1,4})[\\s,;]*)?z\\s*[:=]?\\s*(-?\\d{1,8})");
    private static final Pattern TRIPLE = Pattern.compile(
            "(?<![\\d.:\\-])(-?\\d{1,8})(?:\\s*[,;/]\\s*|\\s+)(-?\\d{1,4})(?:\\s*[,;/]\\s*|\\s+)(-?\\d{1,8})(?![\\d.:])");

    public ChatCoords() {
        super(
                "chat_coords",
                "Координаты в чате",
                "Координаты в чате кликабельны: клик ставит метку, ещё клик убирает",
                Category.HOLYWORLD,
                true);
    }

    public static Component link(Component msg) {
        ChatCoords m = Modules.CHAT_COORDS;
        if (msg != null && m != null && m.enabled()) {
            List<Part> parts = new ArrayList<>();
            StringBuilder all = new StringBuilder();
            msg.visit(
                    (style, text) -> {
                        parts.add(new Part(text, style));
                        all.append(text);
                        return Optional.empty();
                    },
                    Style.EMPTY);
            String s = all.toString();
            if (s.startsWith("Arcanic")) {
                return msg;
            } else {
                List<Hit> hits = new ArrayList<>();
                find(LABELLED, s, hits, m.flat.on());
                find(TRIPLE, s, hits, true);
                if (hits.isEmpty()) {
                    return msg;
                } else {
                    hits.sort((a, b) -> Integer.compare(a.from(), b.from()));
                    MutableComponent out = Component.empty();
                    int at = 0;

                    for (Part p : parts) {
                        int start = at;
                        int end = at + p.text().length();

                        int stop;
                        for (int i = at; i < end; i = stop) {
                            Hit h = inside(hits, i);
                            stop = end;
                            if (h != null) {
                                stop = Math.min(end, h.to());
                            } else {
                                for (Hit k : hits) {
                                    if (k.from() > i && k.from() < stop) {
                                        stop = k.from();
                                    }
                                }
                            }

                            String piece = p.text().substring(i - start, stop - start);
                            Style st = p.style();
                            if (h != null && st.getClickEvent() == null) {
                                st = st.withUnderlined(true)
                                        .withClickEvent(new ClickEvent.RunCommand(h.command()))
                                        .withHoverEvent(new HoverEvent.ShowText(
                                                Component.literal("Поставить метку на " + h.label())));
                            }

                            out.append((Component) Component.literal(piece).withStyle(st));
                        }

                        at = end;
                    }

                    return out;
                }
            }
        } else {
            return msg;
        }
    }

    private static Hit inside(List<Hit> hits, int i) {
        for (Hit h : hits) {
            if (i >= h.from() && i < h.to()) {
                return h;
            }
        }

        return null;
    }

    private static void find(Pattern p, String s, List<Hit> hits, boolean flat) {
        Matcher m = p.matcher(s);

        while (true) {
            int from;
            int to;
            long z;
            String y;
            long x;
            while (true) {
                if (!m.find()) {
                    return;
                }

                from = m.start();
                to = m.end();
                boolean overlaps = false;

                for (Hit h : hits) {
                    if (from < h.to() && to > h.from()) {
                        overlaps = true;
                    }
                }

                if (!overlaps) {
                    try {
                        x = Long.parseLong(m.group(1));
                        z = Long.parseLong(m.group(3));
                        y = m.group(2);
                        break;
                    } catch (RuntimeException ignored) {
                    }
                }
            }

            if (Math.abs(x) <= 30000000L && Math.abs(z) <= 30000000L && (y != null || flat)) {
                if (y != null) {
                    int yy = Integer.parseInt(y);
                    if (yy < -64 || yy > 320) {
                        continue;
                    }
                }

                String yv = y == null ? "~" : y;
                hits.add(new Hit(
                        from,
                        to,
                        "/arcanic markat " + x + " " + yv + " " + z,
                        x + " " + (y == null ? "" : y + " ") + z));
            }
        }
    }

    private static record Hit(int from, int to, String command, String label) {}

    private static record Part(String text, Style style) {}
}
