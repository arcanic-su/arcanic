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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public final class Notify {
    private static final LinkedList<Toast> LIST = new LinkedList<>();
    private static final int TEXT = 0xFFF4F6FA;
    private static final int ON = 0xFF26C68C;
    private static final int OFF = -44462;
    private static final int GOLD = -14003;
    private static long gappleAt;
    private static ItemStack gappleItem;
    private static final Map<Integer, Use> USES = new HashMap<>();
    private static long lastFrame = System.currentTimeMillis();
    private static final Toast SAMPLE =
            new Toast(null, "sparkles", (ItemStack) null, 0xFF26C68C, List.of(new Part("Уведомления", 0xFFF4F6FA)));

    private Notify() {}

    private static boolean on() {
        return Modules.NOTIFY != null && Modules.NOTIFY.enabled();
    }

    private static void push(Object key, String icon, ItemStack item, int iconColour, List<Part> parts) {
        if (key != null) {
            for (Toast t : LIST) {
                if (Objects.equals(t.key, key) && t.dismissAt == 0L) {
                    t.icon = icon;
                    t.item = item;
                    t.iconColour = iconColour;
                    t.parts = parts;
                    t.dismissAt = 0L;
                    beep();
                    return;
                }
            }
        }

        LIST.addLast(new Toast(key, icon, item, iconColour, parts));
        int active = 0;

        for (Toast t : LIST) {
            if (t.dismissAt == 0L) {
                ++active;
                if (active > 5) {
                    t.dismissAt = System.currentTimeMillis();
                }
            }
        }

        while (LIST.size() > 8) {
            LIST.removeFirst();
        }

        beep();
    }

    private static void beep() {
        if (Modules.NOTIFY.sound.on()) {
            UiSound.play("click", 1.1F, 0.45F);
        }
    }

    public static boolean module(Module m) {
        if (on() && Modules.NOTIFY.modules.on()) {
            push(
                    List.of("module", m.id),
                    "receipt",
                    (ItemStack) null,
                    m.enabled() ? 0xFF26C68C : -44462,
                    List.of(
                            new Part(Lang.tr("Функция "), 0xFFF4F6FA),
                            new Part(m.title(), Theme.accent2() | 0xFF000000),
                            new Part(Lang.tr(m.enabled() ? " включена" : " выключена"), 0xFFF4F6FA)));
            return true;
        } else {
            return false;
        }
    }

    public static boolean text(String line, boolean positive) {
        if (on() && Modules.NOTIFY.modules.on()) {
            String s = Lang.tr(line);
            int i = s.indexOf(": ");
            List<Part> parts = i < 0
                    ? List.of(new Part(s, 0xFFF4F6FA))
                    : List.of(
                            new Part(s.substring(0, i + 2), 0xFFF4F6FA),
                            new Part(s.substring(i + 2), Theme.accent2() | 0xFF000000));
            push(
                    List.of("text", i < 0 ? s : s.substring(0, i)),
                    "sparkles",
                    (ItemStack) null,
                    positive ? 0xFF26C68C : -44462,
                    parts);
            return true;
        } else {
            return false;
        }
    }

    public static void itemUsed(ItemStack stack, ItemUseAnimation how) {
        itemUsed(null, stack, how);
    }

    public static void itemUsed(String who, ItemStack stack, ItemUseAnimation how) {
        if (on() && Modules.NOTIFY.items.on() && !stack.isEmpty() && (who == null || Modules.NOTIFY.others.on())) {
            List<Part> parts = new ArrayList<>();
            boolean gapple = stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
            if (gapple && who == null) {
                gappleAt = System.currentTimeMillis();
                gappleItem = stack.copyWithCount(1);
            }

            if (who == null) {
                parts.add(new Part(Lang.tr("Вы использовали "), 0xFFF4F6FA));
            } else {
                parts.add(new Part(who, Theme.accent2() | 0xFF000000));
                parts.add(new Part(Lang.tr(" использовал "), 0xFFF4F6FA));
            }

            TextColor tc = stack.getStyledHoverName().getStyle().getColor();
            parts.add(new Part(
                    stack.getHoverName().getString(),
                    gapple ? -14003 : (tc == null ? Theme.accent2() | 0xFF000000 : 0xFF000000 | tc.getValue())));
            if (how == ItemUseAnimation.DRINK || how == ItemUseAnimation.EAT) {
                PotionContents potion = (PotionContents) stack.get(DataComponents.POTION_CONTENTS);
                if (potion != null) {
                    Iterator iterator2 = potion.getAllEffects().iterator();
                    if (iterator2.hasNext()) {
                        MobEffectInstance e = (MobEffectInstance) iterator2.next();
                        parts.add(new Part(
                                Lang.tr(", получив эффект ")
                                        + ((MobEffect) e.getEffect().value())
                                                .getDisplayName()
                                                .getString(),
                                0xFFF4F6FA));
                    }
                }
            }

            push(who == null ? null : List.of("use", who), null, stack.copy(), -1, parts);
        }
    }

    public static void effectEnded(Holder<MobEffect> effect) {
        if (on() && Modules.NOTIFY.effects.on()) {
            long since = System.currentTimeMillis() - gappleAt;
            if (gappleItem != null && since < 130000L) {
                if (effect.is(MobEffects.REGENERATION)) {
                    return;
                }

                if (effect.is(MobEffects.ABSORPTION)) {
                    push(
                            List.of("gapple"),
                            null,
                            gappleItem,
                            -1,
                            List.of(
                                    new Part(Lang.tr("Действие "), 0xFFF4F6FA),
                                    new Part(gappleItem.getHoverName().getString(), -14003),
                                    new Part(Lang.tr(" закончилось"), 0xFFF4F6FA)));
                    gappleItem = null;
                    return;
                }
            }

            String name = ((MobEffect) effect.value()).getDisplayName().getString();
            push(
                    List.of("effect", name),
                    null,
                    (ItemStack) null,
                    0xFFB0B8C4,
                    List.of(
                            new Part(Lang.tr("Эффект "), 0xFFF4F6FA),
                            new Part(name, Theme.accent2() | 0xFF000000),
                            new Part(Lang.tr(" закончился"), 0xFFF4F6FA)));
            ((Toast) LIST.getLast()).effect = effect;
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player != null && mc.level != null) {
            Set<Integer> alive = new HashSet<>();

            for (AbstractClientPlayer p : mc.level.players()) {
                if (p == mc.player || !(p.distanceToSqr(mc.player) > 1024.0)) {
                    alive.add(p.getId());
                    Use u = (Use) USES.computeIfAbsent(p.getId(), (k) -> new Use());
                    if (p.isUsingItem()) {
                        ItemStack st = p.getUseItem();
                        if (u.stack.isEmpty() || !ItemStack.isSameItem(u.stack, st)) {
                            u.stack = st.copy();
                            u.ticks = 0;
                            u.need = st.getUseDuration(p);
                        }

                        ++u.ticks;
                    } else {
                        if (!u.stack.isEmpty() && u.need > 0 && u.ticks >= u.need - 2) {
                            ItemUseAnimation how = u.stack.getUseAnimation();
                            if (how == ItemUseAnimation.EAT || how == ItemUseAnimation.DRINK) {
                                itemUsed(p == mc.player ? null : p.getName().getString(), u.stack, how);
                            }
                        }

                        u.stack = ItemStack.EMPTY;
                        u.ticks = 0;
                        u.need = 0;
                    }
                }
            }

            USES.keySet().retainAll(alive);
        } else {
            USES.clear();
        }
    }

    static void draw(GuiGraphics g, Prefs p, boolean editing) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (float) (now - lastFrame) / 1000.0F);
        lastFrame = now;
        float glide = 1.0F - (float) Math.pow((double) 0.7F, (double) (dt * 60.0F));
        float u = 3.0F * p.k;
        float step = 17.5F * u;
        int boxW = Math.round(400.0F * p.k);
        boolean placeholder = LIST.isEmpty() && (editing || Minecraft.getInstance().screen instanceof ChatScreen);
        int rows = 0;
        float base = Px.fade;
        if (placeholder) {
            Px.fade = base * 0.6F;
            toast(g, SAMPLE, (float) boxW / 2.0F, 0, u);
            rows = 1;
        }

        Iterator<Toast> it = LIST.iterator();

        while (it.hasNext()) {
            Toast t = (Toast) it.next();
            t.y += ((float) rows * step - t.y) * glide;
            if (t.expired(now)) {
                it.remove();
            } else {
                float a = t.alpha(now);
                ++rows;
                if (!(a <= 0.001F)) {
                    Px.fade = base * a;
                    toast(g, t, (float) boxW / 2.0F, Math.round(t.y), u);
                }
            }
        }

        Px.fade = base;
        p.w = boxW;
        p.h = Math.round((float) Math.max(1, rows) * step - (step - 14.0F * u));
    }

    private static void toast(GuiGraphics g, Toast t, float cx, int y, float u) {
        int px = Math.round(6.0F * u);
        int textW = 0;

        for (Part p : t.parts) {
            textW += Px.textWidth(Lang.tr(p.text()), Px.Weight.MEDIUM, px);
        }

        boolean hasIcon = t.icon != null || t.item != null || t.effect != null;
        float iconW = hasIcon ? 9.0F * u : 0.0F;
        int w = Math.round(12.0F * u + iconW + (float) textW);
        int h = Math.round(14.0F * u);
        int x = Math.round(cx - (float) w / 2.0F);
        int r = Math.round(4.0F * u);
        boolean frost = Frost.prepare();
        if (frost) {
            Frost.backdrop(g, x, y, w, h, r);
        }

        Px.round(g, x, y, w, h, r, frost ? 0xB8090A0E : 0xD0090A0E);
        Px.outline(g, x, y, w, h, r, 620756991);
        int tx = x + Math.round(6.0F * u);
        int is = Math.round(7.0F * u);
        int iy = y + Math.round(3.5F * u);
        if (t.effect != null) {
            g.pose().pushMatrix();
            g.pose().translate((float) tx, (float) iy);
            g.pose().scale((float) is / 18.0F, (float) is / 18.0F);
            g.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    (Identifier) Gui.getMobEffectSprite(t.effect),
                    0,
                    0,
                    18,
                    18,
                    Px.am(-1, Px.fade));
            g.pose().popMatrix();
        } else if (t.item != null) {
            HudElements.item(g, t.item, tx, iy, is);
        } else if (t.icon != null) {
            Px.icon(g, "w." + t.icon, tx, iy, is, t.iconColour);
        }

        tx += Math.round(iconW);

        for (Part p : t.parts) {
            String text = Lang.tr(p.text());
            Px.text(g, text, tx, y + (h - px) / 2, Px.Weight.MEDIUM, px, p.colour());
            tx += Px.textWidth(text, Px.Weight.MEDIUM, px);
        }
    }

    public static final class Prefs extends HudModule {
        public final Setting.Bool modules = this.add(new Setting.Bool("modules", "Функции", true));
        public final Setting.Bool items = this.add(new Setting.Bool("items", "Использование предметов", true));
        public final Setting.Bool others = this.add(new Setting.Bool("others", "Предметы игроков рядом", true));
        public final Setting.Bool effects = this.add(new Setting.Bool("effects", "Конец эффектов", true));
        public final Setting.Bool sound = this.add(new Setting.Bool("sound", "Звук", true));

        public Prefs() {
            super(
                    "notify",
                    "Уведомления",
                    "Всплывающие сообщения под прицелом: функции, предметы, эффекты",
                    true,
                    0.396,
                    0.54);
        }

        public void render(GuiGraphics g, boolean editing) {
            Notify.draw(g, this, editing);
        }
    }

    private static record Part(String text, int colour) {}

    private static final class Toast {
        final Object key;
        String icon;
        ItemStack item;
        Holder<MobEffect> effect;
        int iconColour;
        List<Part> parts;
        final long born = System.currentTimeMillis();
        long dismissAt;
        float y;

        Toast(Object key, String icon, ItemStack item, int iconColour, List<Part> parts) {
            this.key = key;
            this.icon = icon;
            this.item = item;
            this.iconColour = iconColour;
            this.parts = parts;
        }

        float alpha(long now) {
            return this.dismissAt == 0L
                    ? Math.min(1.0F, (float) (now - this.born) / 180.0F)
                    : Math.max(0.0F, 1.0F - (float) (now - this.dismissAt) / 250.0F);
        }

        boolean expired(long now) {
            if (this.dismissAt != 0L) {
                return now - this.dismissAt >= 250L;
            } else {
                if (now - this.born >= 3000L) {
                    this.dismissAt = now;
                }

                return false;
            }
        }
    }

    private static final class Use {
        ItemStack stack;
        int ticks;
        int need;

        private Use() {
            this.stack = ItemStack.EMPTY;
        }
    }
}
