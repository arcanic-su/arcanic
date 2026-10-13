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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.arcanic.visuals.core.Keys;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.mixin.CooldownInstanceAccess;
import net.arcanic.visuals.mixin.ItemCooldownsAccess;
import net.arcanic.visuals.render.Anim;
import net.arcanic.visuals.render.Frost;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The stock HUD elements. */
public final class HudElements {
    private HudElements() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    static Theme.Palette T() {
        return Theme.p();
    }

    static int ping() {
        var mc = mc();
        if (mc.getConnection() == null || mc.player == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    public static void item(GuiGraphics g, ItemStack it, int x, int y, int size) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        g.renderItem(it, 0, 0);
        g.pose().popMatrix();
    }

    /** FPS / ping / memory with bars and a small FPS graph. */
    public static final class Performance extends HudModule {
        private final float[] history = new float[48];
        private int head;
        private long last;

        public Performance() {
            super("performance", "Производительность", "FPS, пинг и память с полосками", false, .008, .07);
        }

        @Override
        public void render(GuiGraphics g, boolean editing) {
            int fps = mc().getFps();
            if (System.currentTimeMillis() - last > 200) {
                last = System.currentTimeMillis();
                history[head] = fps;
                head = (head + 1) % history.length;
            }
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
            w = S(190);
            h = S(112);
            plate(g, w, h);
            int p = ping();
            row(
                    g,
                    S(10),
                    "gauge",
                    "FPS",
                    String.valueOf(fps),
                    Math.min(1, fps / 240f),
                    fps < 30 ? 0xFFF43F5E : fps < 60 ? 0xFFFACC15 : 0xFF4ADE80);
            row(
                    g,
                    S(34),
                    "activity",
                    "Пинг",
                    p + " ms",
                    Math.min(1, p / 300f),
                    p > 150 ? 0xFFF43F5E : p > 80 ? 0xFFFACC15 : 0xFF4ADE80);
            row(g, S(58), "cpu", "RAM", used + " / " + max + " МБ", used / (float) Math.max(1, max), Theme.accent());
            float top = 1;
            for (float v : history) top = Math.max(top, v);
            int gx = S(12), gy = S(84), gw = w - S(24), gh = S(18), bw = Math.max(1, gw / history.length);
            for (int i = 0; i < history.length; i++) {
                float v = history[(head + i) % history.length] / top;
                int bh = Math.max(S(2), Math.round(v * gh));
                Px.round(
                        g,
                        gx + i * bw,
                        gy + gh - bh,
                        Math.max(1, bw - 1),
                        bh,
                        Math.min(S(1), bw / 2),
                        Px.mix(Px.a(Theme.accent(), 120), Theme.accent2(), i / (float) history.length));
            }
        }

        private void row(GuiGraphics g, int y, String icon, String label, String value, float frac, int color) {
            int fs = S(12);
            Px.icon(g, icon, S(12), y + S(1), S(12), T().dim());
            Px.text(g, Lang.tr(label), S(30), y, Weight.MEDIUM, fs, T().muted());
            Px.textRight(g, Lang.tr(value), w - S(12), y, Weight.SEMIBOLD, fs, T().text());
            int bx = S(70), bw = S(50), by = y + S(6);
            Px.round(g, bx, by, bw, S(4), S(2), T().control());
            Px.pill(g, bx, by, Math.max(S(4), Math.round(bw * frac)), S(4), Px.mix(color, 0xFFFFFFFF, .25f), color);
        }
    }

    public static final class Coordinates extends HudModule {
        final Setting.Bool nether = add(new Setting.Bool("nether", "Координаты ада", false));

        public Coordinates() {
            super("coords", "Координаты", "XYZ, направление и биом", false, .008, .9);
        }

        @Override
        public void render(GuiGraphics g, boolean editing) {
            var p = mc().player;
            String xyz = p == null ? "0 64 0" : p.getBlockX() + "  " + p.getBlockY() + "  " + p.getBlockZ();
            String dir = p == null
                    ? "N"
                    : switch (p.getDirection()) {
                        case NORTH -> "С";
                        case SOUTH -> "Ю";
                        case EAST -> "В";
                        default -> "З";
                    };
            String extra = nether.on() && p != null ? (p.getBlockX() / 8) + "  " + (p.getBlockZ() / 8) : null;
            int fs = S(13);
            w = S(48)
                    + Px.textWidth(Lang.tr(xyz), Weight.SEMIBOLD, fs)
                    + S(30)
                    + (extra == null ? 0 : S(34) + Px.textWidth(Lang.tr(extra), Weight.MEDIUM, fs));
            h = S(34);
            plate(g, w, h);
            Px.icon(g, "compass", S(12), S(10), S(14), Theme.accent2());
            Px.text(g, Lang.tr(xyz), S(34), S(9), Weight.SEMIBOLD, fs, T().text());
            int x = S(34) + Px.textWidth(Lang.tr(xyz), Weight.SEMIBOLD, fs) + S(10);
            Px.round(g, x, S(8), S(20), S(18), S(6), Px.a(Theme.accent(), 60));
            Px.textCenter(g, Lang.tr(dir), x + S(10), S(10), Weight.BOLD, S(11), Theme.accent2());
            if (extra != null) {
                x += S(30);
                Px.icon(g, "flame", x, S(10), S(13), 0xFFFB923C);
                Px.text(g, Lang.tr(extra), x + S(18), S(9), Weight.MEDIUM, fs, T().muted());
            }
        }
    }

    /** Effects. */
    public static final class Potions extends HudModule {
        final Setting.Mode view = add(new Setting.Mode("view", "Вид", "Панель", "Панель", "Карточки"));
        final Setting.Bool notice = add(new Setting.Bool("notice", "Сообщение об окончании", true));
        private final Anim anim = new Anim();

        private static final class Row {
            Holder<MobEffect> effect;
            MobEffectInstance inst;
            boolean alive = true;
            int lastTicks;
        }

        private final Map<String, Row> rows = new LinkedHashMap<>();

        public Potions() {
            super("potions", "Зелья", "Активные эффекты: иконка, уровень и таймер", false, .865, .24);
        }

        private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

        static String level(int amplifier) {
            return amplifier >= 0 && amplifier < ROMAN.length ? ROMAN[amplifier] : String.valueOf(amplifier + 1);
        }

        static String time(MobEffectInstance e) {
            if (e.isInfiniteDuration()) return "∞";
            int sec = Math.max(0, (e.getDuration() + 19) / 20);
            return sec >= 3600
                    ? String.format("%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
                    : String.format("%d:%02d", sec / 60, sec % 60);
        }

        private static String name(MobEffectInstance e) {
            return e.getEffect().value().getDisplayName().getString()
                    + (e.getAmplifier() > 0 ? " " + level(e.getAmplifier()) : "");
        }

        private static void icon(GuiGraphics g, Holder<MobEffect> effect, int x, int y, int size) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(size / 18f, size / 18f);
            g.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    Gui.getMobEffectSprite(effect),
                    0,
                    0,
                    18,
                    18,
                    Px.am(0xFFFFFFFF, Px.fade));
            g.pose().popMatrix();
        }

        private void sync() {
            List<MobEffectInstance> list = mc().player == null
                    ? List.of()
                    : new ArrayList<>(mc().player.getActiveEffects());
            Set<String> live = new HashSet<>();
            for (MobEffectInstance e : list) {
                String key = e.getEffect().getRegisteredName();
                live.add(key);
                Row r = rows.computeIfAbsent(key, k -> {
                    anim.set("in:" + k, 0);
                    return new Row();
                });
                r.alive = true;
                r.effect = e.getEffect();
                r.inst = e;
                r.lastTicks = e.getDuration();
            }
            for (var en : rows.entrySet()) {
                Row r = en.getValue();
                if (r.alive && !live.contains(en.getKey())) {
                    r.alive = false;
                    if (notice.on() && !r.inst.isInfiniteDuration() && r.lastTicks <= 40) Notify.effectEnded(r.effect);
                }
            }
        }

        @Override
        public void render(GuiGraphics g, boolean editing) {
            anim.frame();
            sync();
            int text = T().text(), muted = 0xFF757D8A, danger = 0xFFFF4D68;
            float base = Px.fade;
            if (rows.isEmpty()) {
                w = Math.max(U(110), kitTitleWidth("Эффекты"));
                h = U(18);
                if (editing) {
                    plate(g, w, h);
                    kitHeader(g, "flask", "Эффекты", w);
                }
                return;
            }
            boolean cards = view.is("Карточки");
            int nfs = Math.max(9, U(cards ? 7 : 6.5)), tfs = Math.max(8, U(cards ? 6 : 5.5));
            float target = cards ? 0 : kitTitleWidth("Эффекты"), rowsH = 0;
            List<String> gone = new ArrayList<>();
            for (var en : rows.entrySet()) {
                Row r = en.getValue();
                float vis = anim.to("in:" + en.getKey(), r.alive ? 1 : 0, 14);
                if (!r.alive && vis < .01f) {
                    gone.add(en.getKey());
                    continue;
                }
                String nm = name(r.inst), tm = r.alive ? time(r.inst) : "0:00";
                target = Math.max(
                        target,
                        cards
                                ? U(30)
                                        + Math.max(
                                                Px.textWidth(nm, Weight.SEMIBOLD, nfs),
                                                Px.textWidth(tm, Weight.BOLD, tfs))
                                : U(19) + Px.textWidth(nm, Weight.SEMIBOLD, nfs) + badgeWidth("pie", tm) + U(12));
                rowsH += vis * (cards ? U(26) : U(13.5));
            }
            for (String k2 : gone) {
                rows.remove(k2);
                anim.set("in:" + k2, 0);
            }
            w = Math.round(anim.to("w", Math.max(cards ? U(40) : U(110), target), 14));
            h = Math.round(anim.to(
                    "h", cards ? Math.max(U(23), rowsH - U(3)) : U(18) + (rowsH > 0 ? U(4.5) + rowsH + U(2) : 0), 14));
            int sw = Px.width();
            boolean right = x(sw) + w / 2 > sw / 2;
            if (!cards) {
                plate(g, w, h);
                g.enableScissor(0, 0, w, h);
                float y = kitHeader(g, "flask", "Эффекты", w);
                for (Row r : rows.values()) {
                    float vis = anim.to("in:" + r.effect.getRegisteredName(), r.alive ? 1 : 0, 14);
                    if (vis < .01f) continue;
                    int slide = Math.round((1 - vis) * U(4)), ry = Math.round(y), rh = U(10.5);
                    Px.fade = base * vis;
                    icon(g, r.effect, U(5) + slide, ry + U(1.75), U(7));
                    Px.text(g, name(r.inst), U(15) + slide, ry + (rh - nfs) / 2, Weight.SEMIBOLD, nfs, text);
                    boolean good = r.effect.value().isBeneficial();
                    // the time in a pill with a clock; harmful effects get a red pill
                    badge(
                            g,
                            "pie",
                            r.alive ? time(r.inst) : "0:00",
                            w - U(5) + slide,
                            ry,
                            true,
                            good ? 0xFFB0B8C4 : danger,
                            good ? 0 : danger);
                    y += vis * U(13.5);
                }
                g.disableScissor();
            } else {
                float y = 0;
                for (Row r : rows.values()) {
                    float vis = anim.to("in:" + r.effect.getRegisteredName(), r.alive ? 1 : 0, 14);
                    if (vis < .01f) continue;
                    String nm = name(r.inst), tm = r.alive ? time(r.inst) : "0:00";
                    int cw = U(30)
                            + Math.max(Px.textWidth(nm, Weight.SEMIBOLD, nfs), Px.textWidth(tm, Weight.BOLD, tfs));
                    int cx = (right ? w - cw : 0) + Math.round((1 - vis) * U(4)),
                            cy = Math.round(y),
                            ch = U(23),
                            rr = U(7);
                    Px.fade = base * vis;
                    if (background.on()) {
                        Px.shadow(g, cx, cy, cw, ch, U(10), 0x1C000000);
                        boolean frost = Frost.prepare();
                        if (frost) Frost.backdrop(g, cx, cy, cw, ch, rr);
                        Px.round(g, cx, cy, cw, ch, rr, (T().bg() & 0xFFFFFF) | (frost ? 0xB8000000 : 0xD8000000));
                        Px.outline(g, cx, cy, cw, ch, rr, 0x30FFFFFF);
                    }
                    icon(g, r.effect, cx + U(6), cy + U(4.5), U(14));
                    int th = nfs + U(1) + tfs, ty = cy + (ch - th) / 2;
                    Px.text(g, nm, cx + U(24), ty, Weight.SEMIBOLD, nfs, text);
                    Px.text(
                            g,
                            tm,
                            cx + U(24),
                            ty + nfs + U(1),
                            Weight.BOLD,
                            tfs,
                            r.effect.value().isBeneficial() ? muted : danger);
                    y += vis * U(26);
                }
            }
            Px.fade = base;
        }
    }

    /** Keybinds: every module with a key, the key in an outlined pill; off ones dimmed. */
    public static final class HotKeys extends HudModule {
        final Setting.Bool onlyOn = add(new Setting.Bool("only_on", "Только включённые", false));
        private final Anim anim = new Anim();
        private final LinkedHashMap<String, Module> seen = new LinkedHashMap<>();

        public HotKeys() {
            super("hotkeys", "Бинды", "Модули с назначенными клавишами", false, .88, .02);
        }

        private List<Module> rows() {
            List<Module> out = new ArrayList<>();
            for (Module m : Modules.ALL) if (m.bind >= 0 && (!onlyOn.on() || m.enabled())) out.add(m);
            return out;
        }

        @Override
        public boolean shown() {
            return !rows().isEmpty() || !seen.isEmpty();
        }

        @Override
        public void render(GuiGraphics g, boolean editing) {
            anim.frame();
            Theme.Palette p = T();
            List<Module> list = rows();
            for (Module m : list) seen.putIfAbsent(m.id, m);
            int fs = Math.max(9, U(6.5)), rh = U(11);
            float target = kitTitleWidth("Бинды"), rowsH = 0;
            List<String> gone = new ArrayList<>();
            for (var en : seen.entrySet()) {
                Module m = en.getValue();
                float vis = anim.to("in:" + m.id, list.contains(m) ? 1 : 0, 14);
                if (!list.contains(m) && vis < .01f) {
                    gone.add(en.getKey());
                    continue;
                }
                target = Math.max(
                        target,
                        U(6)
                                + Px.textWidth(Lang.tr(m.name), Weight.SEMIBOLD, fs)
                                + U(8)
                                + badgeWidth(Keys.label(m.bind))
                                + U(6));
                rowsH += vis * (rh + U(3));
            }
            gone.forEach(seen::remove);
            w = Math.round(anim.to("w", Math.max(U(90), target), 14));
            h = Math.round(anim.to("h", U(18) + (rowsH > 0 ? U(4.5) + rowsH + U(2) : 0), 14));
            if (seen.isEmpty() && !editing) return;
            plate(g, w, h);
            g.enableScissor(0, 0, w, h);
            float y = kitHeader(g, "keyboard", "Бинды", w), base = Px.fade;
            for (Module m : seen.values()) {
                float vis = anim.to("in:" + m.id, list.contains(m) ? 1 : 0, 14);
                if (vis < .01f) continue;
                int slide = Math.round((1 - vis) * U(4)), ry = Math.round(y);
                Px.fade = base * vis;
                float on = anim.to("on:" + m.id, m.enabled() ? 1 : 0, 12);
                Px.text(
                        g,
                        Lang.tr(m.name),
                        U(6) + slide,
                        ry + (rh - fs) / 2,
                        Weight.SEMIBOLD,
                        fs,
                        Px.mix(0xFF757D8A, p.text(), on));
                badge(
                        g,
                        Keys.label(m.bind),
                        w - U(6) + slide,
                        ry,
                        false,
                        Px.mix(0xFF757D8A, Theme.accent2() | 0xFF000000, on));
                y += vis * (rh + U(3));
            }
            Px.fade = base;
            g.disableScissor();
        }
    }

    /** Cooldowns: items on cooldown with how long is left (pearl, chorus, shield…). */
    public static final class Cooldowns extends HudModule {
        private final Anim anim = new Anim();

        private record Entry(String id, ItemStack stack, int ticks) {}

        private final LinkedHashMap<String, Entry> seen = new LinkedHashMap<>();

        public Cooldowns() {
            super("cooldowns", "Кулдауны", "Предметы на перезарядке и сколько осталось", false, .45, .02);
        }

        private List<Entry> current() {
            var p = mc().player;
            List<Entry> out = new ArrayList<>();
            if (p == null) return out;
            var cd = p.getCooldowns();
            var acc = (ItemCooldownsAccess) cd;
            Set<Identifier> done = new HashSet<>();
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty() || !cd.isOnCooldown(st)) continue;
                var group = cd.getCooldownGroup(st);
                if (!done.add(group)) continue;
                Object inst = acc.arcanicvisuals$cooldowns().get(group);
                if (!(inst instanceof CooldownInstanceAccess ci)) continue;
                int left = ci.arcanicvisuals$endTime() - acc.arcanicvisuals$tickCount();
                if (left > 0) out.add(new Entry(group.toString(), st.copy(), left));
            }
            return out;
        }

        private static String left(int ticks) {
            float s = ticks / 20f;
            if (s < 1) return String.format(Locale.ROOT, "%.1f %s", s, Lang.tr("сек"));
            return s < 60 ? (int) s + " " + Lang.tr("сек") : String.format("%d:%02d", (int) s / 60, (int) s % 60);
        }

        @Override
        public boolean shown() {
            return !current().isEmpty() || !seen.isEmpty();
        }

        @Override
        public void render(GuiGraphics g, boolean editing) {
            anim.frame();
            List<Entry> list = current();
            if (list.isEmpty() && editing && seen.isEmpty())
                list = List.of(
                        new Entry("p:pearl", new ItemStack(Items.ENDER_PEARL), 200),
                        new Entry("p:chorus", new ItemStack(Items.CHORUS_FRUIT), 120));
            Set<String> live = new HashSet<>();
            for (Entry e : list) {
                seen.put(e.id(), e);
                live.add(e.id());
            }
            int fs = Math.max(9, U(6.5)), tfs = Math.max(8, U(5.5)), rh = U(10.5);
            float target = kitTitleWidth("Кулдауны"), rowsH = 0;
            List<String> gone = new ArrayList<>();
            for (Entry e : seen.values()) {
                float vis = anim.to("in:" + e.id(), live.contains(e.id()) ? 1 : 0, 14);
                if (!live.contains(e.id()) && vis < .01f) {
                    gone.add(e.id());
                    continue;
                }
                target = Math.max(
                        target,
                        U(15)
                                + Px.textWidth(e.stack().getHoverName().getString(), Weight.SEMIBOLD, fs)
                                + U(4)
                                + Px.textWidth(left(e.ticks()), Weight.BOLD, tfs)
                                + U(5));
                rowsH += vis * U(13.5);
            }
            gone.forEach(seen::remove);
            w = Math.round(anim.to("w", Math.max(U(110), target), 14));
            h = Math.round(anim.to("h", U(18) + (rowsH > 0 ? U(4.5) + rowsH + U(2) : 0), 14));
            if (seen.isEmpty()) return;
            plate(g, w, h);
            g.enableScissor(0, 0, w, h);
            float y = kitHeader(g, "pie", "Кулдауны", w), base = Px.fade;
            for (Entry e : seen.values()) {
                float vis = anim.to("in:" + e.id(), live.contains(e.id()) ? 1 : 0, 14);
                if (vis < .01f) continue;
                int slide = Math.round((1 - vis) * U(4)), ry = Math.round(y);
                Px.fade = base * vis;
                item(g, e.stack(), U(5) + slide, ry + U(1.75), U(7));
                Px.text(
                        g,
                        e.stack().getHoverName().getString(),
                        U(15) + slide,
                        ry + (rh - fs) / 2,
                        Weight.SEMIBOLD,
                        fs,
                        T().text());
                String t = left(e.ticks());
                Px.text(
                        g,
                        t,
                        w - U(5) - Px.textWidth(t, Weight.BOLD, tfs) + slide,
                        ry + (rh - tfs) / 2,
                        Weight.BOLD,
                        tfs,
                        0xFF757D8A);
                y += vis * U(13.5);
            }
            Px.fade = base;
            g.disableScissor();
        }
    }

    public static final class Armor extends HudModule {
        public Armor() {
            super("armor", "Броня", "Надетая броня и прочность", false, .4, .8);
        }

        @Override
        public void render(GuiGraphics g, boolean editing) {
            int cell = S(40);
            w = S(8) + 4 * cell;
            h = S(52);
            plate(g, w, h);
            var p = mc().player;
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < 4; i++) {
                ItemStack it = p == null ? ItemStack.EMPTY : p.getItemBySlot(slots[i]);
                int x = S(8) + i * cell;
                Px.round(g, x, S(6), cell - S(8), cell - S(8), S(8), T().control());
                if (it.isEmpty()) continue;
                item(g, it, x + (cell - S(8) - S(24)) / 2, S(10), S(24));
                if (it.isDamageableItem()) {
                    float f = 1 - it.getDamageValue() / (float) it.getMaxDamage();
                    Px.round(g, x, S(42), cell - S(8), S(4), S(2), T().control());
                    Px.round(
                            g,
                            x,
                            S(42),
                            Math.max(S(4), Math.round((cell - S(8)) * f)),
                            S(4),
                            S(2),
                            Px.mix(0xFFF43F5E, 0xFF4ADE80, f));
                }
            }
        }
    }

    /** WASD, space and mouse buttons with left-click CPS. */
    public static final class Keystrokes extends HudModule {
        private final float[] glow = new float[7];
        private long last = System.nanoTime();

        public Keystrokes() {
            super("keystrokes", "Клавиши", "WASD, пробел, мышь и CPS", false, .008, .5);
        }

        @Override
        public void render(GuiGraphics g, boolean editing) {
            var o = mc().options;
            long now = System.nanoTime();
            float dt = Math.min(.1f, (now - last) / 1e9f);
            last = now;
            boolean[] down = {
                o.keyUp.isDown(),
                o.keyLeft.isDown(),
                o.keyDown.isDown(),
                o.keyRight.isDown(),
                o.keyAttack.isDown(),
                o.keyUse.isDown(),
                o.keyJump.isDown()
            };
            int c = S(40), gp = S(5);
            w = c * 3 + gp * 2;
            h = c * 2 + gp * 3 + S(30) + S(14);
            key(g, 0, c + gp, 0, c, c, "W", down[0], dt);
            key(g, 1, 0, c + gp, c, c, "A", down[1], dt);
            key(g, 2, c + gp, c + gp, c, c, "S", down[2], dt);
            key(g, 3, (c + gp) * 2, c + gp, c, c, "D", down[3], dt);
            int mw = (w - gp) / 2, my = (c + gp) * 2;
            key(g, 4, 0, my, mw, S(30), "ЛКМ  " + Clicks.cps(), down[4], dt);
            key(g, 5, mw + gp, my, w - mw - gp, S(30), "ПКМ", down[5], dt);
            key(g, 6, 0, my + S(30) + gp, w, S(14), "", down[6], dt);
        }

        private void key(GuiGraphics g, int i, int x, int y, int kw, int kh, String label, boolean down, float dt) {
            glow[i] += ((down ? 1 : 0) - glow[i]) * (1 - (float) Math.exp(-dt * 18));
            float v = glow[i];
            if (background.on()) {
                Px.round(
                        g,
                        x,
                        y,
                        kw,
                        kh,
                        S(9),
                        Px.mix((T().bg() & 0xFFFFFF) | 0xD8000000, Px.a(Theme.accent(), 220), v));
                Px.outline(g, x, y, kw, kh, S(9), Px.mix(T().border(), Theme.accent2(), v));
            }
            if (!label.isEmpty())
                Px.textCenter(
                        g,
                        Lang.tr(label),
                        x + kw / 2,
                        y + (kh - S(13)) / 2,
                        Weight.SEMIBOLD,
                        S(13),
                        Px.mix(T().text(), 0xFFFFFFFF, v));
            else
                Px.round(
                        g,
                        x + kw / 2 - S(24),
                        y + kh / 2 - S(1),
                        S(48),
                        S(3),
                        S(1),
                        Px.mix(T().muted(), 0xFFFFFFFF, v));
        }
    }

    public static final class Clicks {
        private static final long[] times = new long[64];
        private static int n;

        public static void click() {
            times[n++ % times.length] = System.currentTimeMillis();
        }

        public static int cps() {
            long now = System.currentTimeMillis();
            int c = 0;
            for (long t : times) if (now - t < 1000) c++;
            return c;
        }
    }
}
