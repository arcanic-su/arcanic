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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.render.Frost;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.Px.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Pop-up notices under the crosshair: a slim plate per line, an icon in its colour on the left, the text in parts
 * with the important word lit.
 */
public final class Notify {
    private Notify() {}

    /** The module that switches them on and picks what they report (HUD section). */
    public static final class Prefs extends HudModule {
        public final Setting.Bool modules = add(new Setting.Bool("modules", "Функции", true));
        public final Setting.Bool items = add(new Setting.Bool("items", "Использование предметов", true));
        public final Setting.Bool others = add(new Setting.Bool("others", "Предметы игроков рядом", true));
        public final Setting.Bool effects = add(new Setting.Bool("effects", "Конец эффектов", true));
        public final Setting.Bool sound = add(new Setting.Bool("sound", "Звук", true));

        public Prefs() {
            super(
                    "notify",
                    "Уведомления",
                    "Всплывающие сообщения под прицелом: функции, предметы, эффекты",
                    false,
                    .396,
                    .54);
        }

        @Override
        public void render(GuiGraphics g, boolean editing) {
            draw(g, this, editing);
        }
    }

    private record Part(String text, int colour) {}

    private static final class Toast {
        final Object key;
        String icon;
        ItemStack item;
        Holder<MobEffect> effect;
        int iconColour;
        List<Part> parts;
        final long born = System.currentTimeMillis();
        long dismissAt;
        float y; // y starts at the top: a new toast flies down into its row

        Toast(Object key, String icon, ItemStack item, int iconColour, List<Part> parts) {
            this.key = key;
            this.icon = icon;
            this.item = item;
            this.iconColour = iconColour;
            this.parts = parts;
        }

        float alpha(long now) {
            return dismissAt == 0 ? Math.min(1, (now - born) / 180f) : Math.max(0, 1 - (now - dismissAt) / 250f);
        }

        boolean expired(long now) {
            if (dismissAt != 0) return now - dismissAt >= 250;
            if (now - born >= 3000) dismissAt = now;
            return false;
        }
    }

    private static final LinkedList<Toast> LIST = new LinkedList<>();
    private static final int TEXT = 0xFFF4F6FA, ON = 0xFF26C68C, OFF = 0xFFFF5252;

    private static boolean on() {
        return Modules.NOTIFY != null && Modules.NOTIFY.enabled();
    }

    private static void push(Object key, String icon, ItemStack item, int iconColour, List<Part> parts) {
        if (key != null)
            for (Toast t : LIST)
                if (Objects.equals(t.key, key) && t.dismissAt == 0) {
                    t.icon = icon;
                    t.item = item;
                    t.iconColour = iconColour;
                    t.parts = parts;
                    t.dismissAt = 0;
                    beep();
                    return;
                }
        LIST.addLast(new Toast(key, icon, item, iconColour, parts));
        int active = 0;
        for (Toast t : LIST) if (t.dismissAt == 0 && ++active > 5) t.dismissAt = System.currentTimeMillis();
        while (LIST.size() > 8) LIST.removeFirst();
        beep();
    }

    private static void beep() {
        if (Modules.NOTIFY.sound.on()) UiSound.play("click", 1.1f, .45f);
    }

    /** "Hitboxes enabled": the name in the accent, the icon green or red. */
    public static boolean module(Module m) {
        if (!on() || !Modules.NOTIFY.modules.on()) return false;
        push(
                List.of("module", m.id),
                "receipt",
                null,
                m.enabled() ? ON : OFF,
                List.of(
                        new Part(Lang.tr("Функция "), TEXT),
                        new Part(Lang.tr(m.name), Theme.accent2() | 0xFF000000),
                        new Part(Lang.tr(m.enabled() ? " включена" : " выключена"), TEXT)));
        return true;
    }

    public static boolean text(String line, boolean positive) {
        if (!on() || !Modules.NOTIFY.modules.on()) return false;
        String s = Lang.tr(line);
        int i = s.indexOf(": ");
        List<Part> parts = i < 0
                ? List.of(new Part(s, TEXT))
                : List.of(
                        new Part(s.substring(0, i + 2), TEXT),
                        new Part(s.substring(i + 2), Theme.accent2() | 0xFF000000));
        push(List.of("text", i < 0 ? s : s.substring(0, i)), "sparkles", null, positive ? ON : OFF, parts);
        return true;
    }

    public static void itemUsed(ItemStack stack, ItemUseAnimation how) {
        itemUsed(null, stack, how);
    }
    /** The same for another player (null = you): "Nick used a Golden Apple", the nick lit. */
    public static void itemUsed(String who, ItemStack stack, ItemUseAnimation how) {
        if (!on() || !Modules.NOTIFY.items.on() || stack.isEmpty() || who != null && !Modules.NOTIFY.others.on())
            return;
        List<Part> parts = new ArrayList<>();
        boolean gapple = stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
        if (gapple && who == null) {
            gappleAt = System.currentTimeMillis();
            gappleItem = stack.copyWithCount(1);
        }
        if (who == null) parts.add(new Part(Lang.tr("Вы использовали "), TEXT));
        else {
            parts.add(new Part(who, Theme.accent2() | 0xFF000000));
            parts.add(new Part(Lang.tr(" использовал "), TEXT));
        }
        TextColor tc = stack.getStyledHoverName().getStyle().getColor();
        parts.add(new Part(
                stack.getHoverName().getString(),
                gapple ? GOLD : tc == null ? Theme.accent2() | 0xFF000000 : 0xFF000000 | tc.getValue()));
        if (how == ItemUseAnimation.DRINK || how == ItemUseAnimation.EAT) {
            PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
            if (potion != null)
                for (MobEffectInstance e : potion.getAllEffects()) {
                    parts.add(new Part(
                            Lang.tr(", получив эффект ")
                                    + e.getEffect().value().getDisplayName().getString(),
                            TEXT));
                    break;
                }
        }
        push(who == null ? null : List.of("use", who), null, stack.copy(), -1, parts);
    }

    /** "Strength has worn off", with that effect's own icon. */
    public static void effectEnded(Holder<MobEffect> effect) {
        if (!on() || !Modules.NOTIFY.effects.on()) return;
        long since = System.currentTimeMillis() - gappleAt;
        if (gappleItem != null && since < 130_000) {
            // a golden apple's regeneration and absorption: one notice for the apple when absorption runs out
            if (effect.is(MobEffects.REGENERATION)) return;
            if (effect.is(MobEffects.ABSORPTION)) {
                push(
                        List.of("gapple"),
                        null,
                        gappleItem,
                        -1,
                        List.of(
                                new Part(Lang.tr("Действие "), TEXT),
                                new Part(gappleItem.getHoverName().getString(), GOLD),
                                new Part(Lang.tr(" закончилось"), TEXT)));
                gappleItem = null;
                return;
            }
        }
        String name = effect.value().getDisplayName().getString();
        push(
                List.of("effect", name),
                null,
                null,
                0xFFB0B8C4,
                List.of(
                        new Part(Lang.tr("Эффект "), TEXT),
                        new Part(name, Theme.accent2() | 0xFF000000),
                        new Part(Lang.tr(" закончился"), TEXT)));
        LIST.getLast().effect = effect;
    }

    private static final int GOLD = 0xFFFFC94D;
    private static long gappleAt;
    private static ItemStack gappleItem;

    /** One player's current use, counted in our own ticks (the server's timing does not reach the client reliably). */
    private static final class Use {
        ItemStack stack = ItemStack.EMPTY;
        int ticks, need;
    }

    private static final Map<Integer, Use> USES = new HashMap<>();
    /** Every client tick: a player who stops using food or a potion right as its time is up ate it. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            USES.clear();
            return;
        }
        Set<Integer> alive = new HashSet<>();
        for (var p : mc.level.players()) {
            if (p != mc.player && p.distanceToSqr(mc.player) > 32 * 32) continue;
            alive.add(p.getId());
            Use u = USES.computeIfAbsent(p.getId(), k -> new Use());
            if (p.isUsingItem()) {
                ItemStack st = p.getUseItem();
                if (u.stack.isEmpty() || !ItemStack.isSameItem(u.stack, st)) {
                    u.stack = st.copy();
                    u.ticks = 0;
                    u.need = st.getUseDuration(p);
                }
                u.ticks++;
            } else {
                if (!u.stack.isEmpty() && u.need > 0 && u.ticks >= u.need - 2) {
                    var how = u.stack.getUseAnimation();
                    if (how == ItemUseAnimation.EAT || how == ItemUseAnimation.DRINK)
                        itemUsed(p == mc.player ? null : p.getName().getString(), u.stack, how);
                }
                u.stack = ItemStack.EMPTY;
                u.ticks = 0;
                u.need = 0;
            }
        }
        USES.keySet().retainAll(alive);
    }

    private static long lastFrame = System.currentTimeMillis();
    private static final Toast SAMPLE = new Toast(null, "sparkles", null, ON, List.of(new Part("Уведомления", TEXT)));

    /** The toasts as a HUD element: a column with the newest at the bottom. */
    static void draw(GuiGraphics g, Prefs p, boolean editing) {
        long now = System.currentTimeMillis();
        float dt = Math.min(.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float glide = 1 - (float) Math.pow(1 - 18 / 60f, dt * 60);
        float u = 3 * p.k, step = 17.5f * u;
        int boxW = Math.round(400 * p.k);
        boolean placeholder = LIST.isEmpty() && (editing || Minecraft.getInstance().screen instanceof ChatScreen);
        int rows = 0;
        float base = Px.fade;
        if (placeholder) {
            Px.fade = base * .6f;
            toast(g, SAMPLE, boxW / 2f, 0, u);
            rows = 1;
        }
        for (Iterator<Toast> it = LIST.iterator(); it.hasNext(); ) {
            Toast t = it.next();
            t.y += (rows * step - t.y) * glide;
            if (t.expired(now)) {
                it.remove();
                continue;
            }
            float a = t.alpha(now);
            rows++;
            if (a <= .001f) continue;
            Px.fade = base * a;
            toast(g, t, boxW / 2f, Math.round(t.y), u);
        }
        Px.fade = base;
        p.w = boxW;
        p.h = Math.round(Math.max(1, rows) * step - (step - 14 * u));
    }

    private static void toast(GuiGraphics g, Toast t, float cx, int y, float u) {
        int px = Math.round(6 * u);
        int textW = 0;
        for (Part p : t.parts) textW += Px.textWidth(Lang.tr(p.text()), Weight.MEDIUM, px);
        boolean hasIcon = t.icon != null || t.item != null || t.effect != null;
        float iconW = hasIcon ? 9 * u : 0;
        int w = Math.round(12 * u + iconW + textW), h = Math.round(14 * u), x = Math.round(cx - w / 2f);
        int r = Math.round(4 * u);
        boolean frost = Frost.prepare();
        if (frost) Frost.backdrop(g, x, y, w, h, r);
        Px.round(g, x, y, w, h, r, frost ? 0xB8090A0E : 0xD0090A0E);
        Px.outline(g, x, y, w, h, r, 0x24FFFFFF);
        int tx = x + Math.round(6 * u), is = Math.round(7 * u), iy = y + Math.round(3.5f * u);
        if (t.effect != null) {
            g.pose().pushMatrix();
            g.pose().translate(tx, iy);
            g.pose().scale(is / 18f, is / 18f);
            g.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    Gui.getMobEffectSprite(t.effect),
                    0,
                    0,
                    18,
                    18,
                    Px.am(0xFFFFFFFF, Px.fade));
            g.pose().popMatrix();
        } else if (t.item != null) HudElements.item(g, t.item, tx, iy, is);
        else if (t.icon != null) Px.icon(g, "w." + t.icon, tx, iy, is, t.iconColour);
        tx += Math.round(iconW);
        for (Part p : t.parts) {
            String text = Lang.tr(p.text());
            Px.text(g, text, tx, y + (h - px) / 2, Weight.MEDIUM, px, p.colour());
            tx += Px.textWidth(text, Weight.MEDIUM, px);
        }
    }
}
