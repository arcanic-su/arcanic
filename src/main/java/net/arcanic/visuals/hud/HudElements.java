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
import java.util.HashMap;
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
import net.arcanic.visuals.helper.HolyFeatures;
import net.arcanic.visuals.mixin.CooldownInstanceAccess;
import net.arcanic.visuals.mixin.ItemCooldownsAccess;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HudElements {
    private HudElements() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    static Theme.Palette T() {
        return Theme.p();
    }

    static int ping() {
        Minecraft mc = mc();
        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return info == null ? 0 : info.getLatency();
        } else {
            return 0;
        }
    }

    public static void item(GuiGraphics g, ItemStack it, int x, int y, int size) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale((float) size / 16.0F, (float) size / 16.0F);
        g.renderItem(it, 0, 0);
        g.pose().popMatrix();
    }

    public static final class Coordinates extends HudModule {
        final Setting.Bool nether = this.add(new Setting.Bool("nether", "Координаты ада", false));
        final Setting.Bool speed = this.add(new Setting.Bool("speed", "Скорость", true));

        public Coordinates() {
            super("coords", "Координаты", "XYZ, направление, скорость и координаты ада", false, 0.008, 0.9);
        }

        /** One pill: the icon, the value, and a softer label after it. */
        private record Part(String icon, String value, String label) {}

        /** A pill for each part: the icon in the accent, the value, and its label a little softer. */
        public void render(GuiGraphics g, boolean editing) {
            LocalPlayer p = HudElements.mc().player;
            boolean hidden = Overlays.Streamer.hidesCoords();
            List<Part> parts = new ArrayList<>();
            String xyz = hidden
                    ? "••• ••• •••"
                    : p == null ? "0 64 0" : p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
            String dir = "С";
            if (p != null) {
                dir = switch (p.getDirection()) {
                    case NORTH -> "С";
                    case SOUTH -> "Ю";
                    case EAST -> "В";
                    default -> "З";
                };
            }
            parts.add(new Part("navigate", xyz, Lang.tr(dir)));
            if (this.nether.on() && p != null && !hidden) {
                boolean inNether = p.level().dimension().identifier().getPath().contains("nether");
                int nx = inNether ? p.getBlockX() * 8 : p.getBlockX() / 8;
                int nz = inNether ? p.getBlockZ() * 8 : p.getBlockZ() / 8;
                parts.add(new Part("flame", nx + " " + nz, Lang.tr(inNether ? "мир" : "ад")));
            }
            if (this.speed.on()) {
                double bps = p == null ? 0.0 : Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) * 20.0;
                parts.add(new Part("gauge", String.format(Locale.ROOT, "%.1f", bps), Lang.tr("бл/с")));
            }
            float h = 21.0F, cy = h / 2.0F, size = 7.0F;
            float space = tWidth(FACE_SEMI, " ", size);
            float x = 0.0F;
            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                float vw = steady(part.value(), size);
                boolean label = !part.label().isBlank();
                float w =
                        6.0F + 9.0F + 4.5F + vw + (label ? space + tWidth(FACE_SEMI, part.label(), size) : 0.0F) + 7.0F;
                this.panel(g, x, 0.0F, w, h, RADIUS);
                this.icon(g, part.icon(), x + 6.0F + 4.5F, cy, 8.5F, accentInk());
                float tx = x + 6.0F + 9.0F + 4.5F;
                this.cText(g, FACE_SEMI, part.value(), tx, cy, size, PANEL_TEXT);
                if (label) {
                    this.cText(g, FACE_SEMI, part.label(), tx + vw + space, cy, size, Px.am(PANEL_TEXT, 0.55F));
                }
                this.glare(g, x, 0.0F, w, h, RADIUS);
                x += w + (i == parts.size() - 1 ? 0.0F : 4.0F);
            }
            this.w = Math.round(this.u(x));
            this.h = Math.round(this.u(h));
        }

        /** A width that does not breathe as the digits change. */
        private static float steady(String s, float size) {
            return Math.max(tWidth(FACE_SEMI, s, size), tWidth(FACE_SEMI, s.replaceAll("[0-9]", "0"), size));
        }
    }

    public static final class Potions extends HudModule {
        final Setting.Bool notice = this.add(new Setting.Bool("notice", "Сообщение об окончании", true));
        private final Map<String, Integer> full = new HashMap<>();
        private final Map<String, Row> rows = new LinkedHashMap<>();
        private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

        public Potions() {
            super("potions", "Зелья", "Активные эффекты: иконка, уровень и таймер", false, 0.865, 0.24);
        }

        static String level(int amplifier) {
            return amplifier >= 0 && amplifier < ROMAN.length ? ROMAN[amplifier] : String.valueOf(amplifier + 1);
        }

        static String time(MobEffectInstance e) {
            if (e.isInfiniteDuration()) {
                return "∞";
            } else {
                int sec = Math.max(0, (e.getDuration() + 19) / 20);
                return sec >= 3600
                        ? String.format("%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
                        : String.format("%d:%02d", sec / 60, sec % 60);
            }
        }

        private static String name(MobEffectInstance e) {
            String n = e.getEffect().value().getDisplayName().getString();
            return n + (e.getAmplifier() > 0 ? " " + level(e.getAmplifier()) : "");
        }

        /** Keeps track of the effects that are on, to tell when one runs out. */
        private void sync() {
            List<MobEffectInstance> list = HudElements.mc().player == null
                    ? List.of()
                    : new ArrayList<>(HudElements.mc().player.getActiveEffects());
            Set<String> live = new HashSet<>();
            for (MobEffectInstance e : list) {
                String key = e.getEffect().getRegisteredName();
                live.add(key);
                Row r = this.rows.computeIfAbsent(key, k -> new Row());
                r.alive = true;
                r.effect = e.getEffect();
                r.inst = e;
                r.lastTicks = e.getDuration();
            }
            for (Map.Entry<String, Row> en : this.rows.entrySet()) {
                Row r = en.getValue();
                if (r.alive && !live.contains(en.getKey())) {
                    r.alive = false;
                    if (this.notice.on() && !r.inst.isInfiniteDuration() && r.lastTicks <= 40) {
                        Notify.effectEnded(r.effect);
                    }
                }
            }
        }

        private List<MobEffectInstance> collect(boolean editing) {
            LocalPlayer p = HudElements.mc().player;
            List<MobEffectInstance> out = new ArrayList<>();
            if (p != null) for (MobEffectInstance e : p.getActiveEffects()) out.add(new MobEffectInstance(e));
            out.sort(Comparator.comparing(
                    e -> e.getEffect().value().getDisplayName().getString()));
            if (out.isEmpty() && editing) {
                out.add(new MobEffectInstance(MobEffects.SPEED, -1, 2));
                out.add(new MobEffectInstance(MobEffects.STRENGTH, 3600, 2));
                out.add(new MobEffectInstance(MobEffects.REGENERATION, 260, 0));
                out.add(new MobEffectInstance(MobEffects.SLOWNESS, 90, 0));
            }
            return out;
        }

        public void render(GuiGraphics g, boolean editing) {
            this.sync();
            this.rows.values().removeIf(r -> !r.alive);
            List<MobEffectInstance> effects = this.collect(editing);
            List<Item> list = new ArrayList<>();
            for (boolean good : new boolean[] {true, false}) {
                for (MobEffectInstance e : effects) {
                    if (e.getEffect().value().isBeneficial() != good) continue;
                    String key = e.getEffect().getRegisteredName() + "#" + e.getAmplifier();
                    boolean forever = e.isInfiniteDuration();
                    int left = Math.max(1, e.getDuration());
                    int whole = this.full.merge(key, left, Math::max);
                    list.add(new Item(
                            key,
                            null,
                            e.getEffect(),
                            name(e),
                            forever ? "∞" : clock(e.getDuration() / 20),
                            TIMER_RING,
                            forever ? 1.0F : (float) left / whole,
                            forever ? -1.0F : e.getDuration() / 20.0F,
                            false,
                            Lang.tr(good ? "Положительные" : "Отрицательные")));
                }
            }
            this.full.keySet().retainAll(list.stream().map(Item::key).toList());
            this.list(g, "flask", Lang.tr("Зелья"), list, 112.0F);
        }

        public boolean shown() {
            return HudElements.mc().player != null
                            && !HudElements.mc().player.getActiveEffects().isEmpty()
                    || this.listing();
        }

        private static final class Row {
            Holder<MobEffect> effect;
            MobEffectInstance inst;
            boolean alive = true;
            int lastTicks;
        }
    }

    public static final class HotKeys extends HudModule {
        final Setting.Bool onlyOn = this.add(new Setting.Bool("only_on", "Только включённые", true));

        public HotKeys() {
            super("hotkeys", "Бинды", "Модули с назначенными клавишами", false, 0.88, 0.02);
        }

        private List<Module> rows() {
            List<Module> out = new ArrayList<>();
            for (Module m : Modules.ALL) {
                if (m.bind >= 0 && (!this.onlyOn.on() || m.enabled()) && !HolyFeatures.hidden(m)) {
                    out.add(m);
                }
            }
            // the ones that are on first, as the list of all binds shows them
            out.sort(Comparator.comparing((Module m) -> !m.enabled()));
            return out;
        }

        public boolean shown() {
            return !this.rows().isEmpty() || this.listing();
        }

        public void render(GuiGraphics g, boolean editing) {
            List<Item> list = new ArrayList<>();
            for (Module m : this.rows()) {
                list.add(new Item(
                        m.id, null, null, m.title(), Keys.label(m.bind), TIMER_NONE, 0.0F, -1.0F, !m.enabled(), null));
            }
            if (list.isEmpty() && editing) {
                list.add(new Item(
                        "preview/hotkeys", null, null, Lang.tr("Бинды"), "NONE", TIMER_NONE, 0.0F, -1.0F, false, null));
            }
            this.list(g, "keyboard", Lang.tr("Бинды"), list, 100.0F);
        }
    }

    public static final class Cooldowns extends HudModule {
        private final Map<Identifier, ItemStack> icons = new HashMap<>();
        private final Map<String, Integer> whole = new HashMap<>();

        public Cooldowns() {
            super("cooldowns", "Кулдауны", "Предметы на перезарядке и сколько осталось", false, 0.45, 0.02);
        }

        private List<Entry> current() {
            LocalPlayer p = HudElements.mc().player;
            List<Entry> out = new ArrayList<>();
            if (p == null) return out;
            ItemCooldowns cd = p.getCooldowns();
            ItemCooldownsAccess acc = (ItemCooldownsAccess) cd;
            Map<Identifier, Object> running = acc.arcanicvisuals$cooldowns();
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty() && cd.isOnCooldown(st)) this.icons.put(cd.getCooldownGroup(st), still(st));
            }
            for (Map.Entry<Identifier, Object> en : running.entrySet()) {
                if (en.getValue() instanceof CooldownInstanceAccess ci) {
                    int left = ci.arcanicvisuals$endTime() - acc.arcanicvisuals$tickCount();
                    if (left <= 0) continue;
                    ItemStack icon = this.icons.get(en.getKey());
                    if (icon == null) {
                        icon = BuiltInRegistries.ITEM
                                .getOptional(en.getKey())
                                .map(ItemStack::new)
                                .map(Cooldowns::still)
                                .orElse(ItemStack.EMPTY);
                    }
                    if (!icon.isEmpty()) out.add(new Entry(en.getKey().toString(), icon, left));
                }
            }
            this.icons.keySet().retainAll(running.keySet());
            out.sort(Comparator.comparing(Entry::id));
            return out;
        }

        private static ItemStack still(ItemStack st) {
            ItemStack c = st.copyWithCount(1);
            c.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            return c;
        }

        public boolean shown() {
            return !this.current().isEmpty() || this.listing();
        }

        public void render(GuiGraphics g, boolean editing) {
            List<Item> list = new ArrayList<>();
            for (Entry e : this.current()) {
                int all = this.whole.merge(e.id(), e.ticks(), Math::max);
                list.add(new Item(
                        e.id(),
                        e.stack(),
                        null,
                        e.stack().getHoverName().getString(),
                        clock(e.ticks() / 20),
                        TIMER_BAR,
                        (float) e.ticks() / Math.max(1, all),
                        e.ticks() / 20.0F,
                        false,
                        null));
            }
            if (list.isEmpty() && editing) {
                ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
                list.add(new Item(
                        "preview/pearl",
                        pearl,
                        null,
                        pearl.getHoverName().getString(),
                        "00:12",
                        TIMER_BAR,
                        0.6F,
                        12.0F,
                        false,
                        null));
            }
            this.whole.keySet().retainAll(list.stream().map(Item::key).toList());
            this.list(g, "pie", Lang.tr("Кулдауны"), list, 112.0F);
        }

        private record Entry(String id, ItemStack stack, int ticks) {}
    }

    public static final class Armor extends HudModule {
        final Setting.Bool percent = this.add(new Setting.Bool("percent", "Прочность в процентах", false));

        public Armor() {
            super("armor", "Броня", "Надетая броня и прочность", false, 0.4, 0.8);
        }

        /**
         * A panel of the four pieces from the helmet to the boots, 16 units each, with a thin bar of durability (or
         * the percentage) under each that turns from our accents to red as the piece wears out.
         */
        public void render(GuiGraphics g, boolean editing) {
            float width = 86.0F, height = 22.0F;
            this.w = Math.round(this.u(width));
            this.h = Math.round(this.u(height));
            this.panel(g, 0.0F, 0.0F, width, height, RADIUS);
            LocalPlayer p = HudElements.mc().player;
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < 4; i++) {
                ItemStack it = p == null ? ItemStack.EMPTY : p.getItemBySlot(slots[i]);
                if (it.isEmpty() && editing) it = preview(3 - i);
                if (it.isEmpty()) continue;
                float x = 5.0F + i * 20.0F, y = 2.5F;
                this.wItem(g, it, x, y, 16.0F);
                if (it.getCount() > 1) {
                    String c = String.valueOf(it.getCount());
                    this.cText(g, FACE_BOLD, c, x + 16.0F - tWidth(FACE_BOLD, c, 5.0F), y + 13.0F, 5.0F, 0xFFFFFFFF);
                }
                if (!it.isDamageableItem()) continue;
                float left = 1.0F - Math.max(0.0F, Math.min(1.0F, (float) it.getDamageValue() / it.getMaxDamage()));
                boolean worn = left < 0.25F;
                int c1 = worn ? Px.mix(0xFFFF4B4B, accentInk(), left / 0.25F) : accentInk();
                int c2 = worn ? c1 : accentDeep();
                if (this.percent.on()) {
                    String s = Math.round(left * 100.0F) + "%";
                    this.cText(g, FACE_BOLD, s, x + (16.0F - tWidth(FACE_BOLD, s, 5.0F)) * 0.5F, y + 16.3F, 5.0F, c1);
                } else {
                    this.rRound(g, x + 2.5F, y + 15.6F, 11.0F, 1.3F, 0.65F, Px.am(panelDark(), 0.85F));
                    this.rRoundH(g, x + 2.5F, y + 15.6F, 11.0F * left, 1.3F, 0.65F, c1, c2);
                }
            }
            this.glare(g, 0.0F, 0.0F, width, height, RADIUS);
        }

        private static ItemStack preview(int slot) {
            return new ItemStack(
                    switch (slot) {
                        case 0 -> Items.DIAMOND_BOOTS;
                        case 1 -> Items.DIAMOND_LEGGINGS;
                        case 2 -> Items.DIAMOND_CHESTPLATE;
                        default -> Items.DIAMOND_HELMET;
                    });
        }

        public boolean shown() {
            LocalPlayer p = HudElements.mc().player;
            if (p == null) return false;
            for (EquipmentSlot s :
                    new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
                    }) if (!p.getItemBySlot(s).isEmpty()) return true;
            return false;
        }
    }

    public static final class Keystrokes extends HudModule {
        private final float[] glow = new float[7];
        private long last = System.nanoTime();

        public Keystrokes() {
            super("keystrokes", "Клавиши", "WASD, пробел, мышь и CPS", false, 0.008, 0.5);
        }

        public void render(GuiGraphics g, boolean editing) {
            Options o = HudElements.mc().options;
            long now = System.nanoTime();
            float dt = Math.min(0.1F, (float) (now - this.last) / 1.0E9F);
            this.last = now;
            boolean[] down = {
                o.keyUp.isDown(),
                o.keyLeft.isDown(),
                o.keyDown.isDown(),
                o.keyRight.isDown(),
                o.keyAttack.isDown(),
                o.keyUse.isDown(),
                o.keyJump.isDown()
            };
            int c = this.S(40.0);
            int gp = this.S(5.0);
            this.w = c * 3 + gp * 2;
            this.h = c * 2 + gp * 3 + this.S(30.0) + this.S(14.0);
            this.key(g, 0, c + gp, 0, c, c, "W", down[0], dt);
            this.key(g, 1, 0, c + gp, c, c, "A", down[1], dt);
            this.key(g, 2, c + gp, c + gp, c, c, "S", down[2], dt);
            this.key(g, 3, (c + gp) * 2, c + gp, c, c, "D", down[3], dt);
            int mw = (this.w - gp) / 2;
            int my = (c + gp) * 2;
            this.key(g, 4, 0, my, mw, this.S(30.0), "ЛКМ  " + HudElements.Clicks.cps(), down[4], dt);
            this.key(g, 5, mw + gp, my, this.w - mw - gp, this.S(30.0), "ПКМ", down[5], dt);
            this.key(g, 6, 0, my + this.S(30.0) + gp, this.w, this.S(14.0), "", down[6], dt);
        }

        private void key(GuiGraphics g, int i, int x, int y, int kw, int kh, String label, boolean down, float dt) {
            this.glow[i] += ((float) (down ? 1 : 0) - this.glow[i]) * (1.0F - (float) Math.exp((double) (-dt * 18.0F)));
            float v = this.glow[i];
            // a key is a tile of the kit; a pressed one fills with the accent
            this.tile(g, x, y, kw, kh, this.S(7.0), Px.mix(fillHead(), Px.a(Theme.accent(), 230), v));

            if (!label.isEmpty()) {
                Px.textCenter(
                        g,
                        Lang.tr(label),
                        x + kw / 2,
                        y + (kh - this.S(13.0)) / 2,
                        Px.Weight.SEMIBOLD,
                        this.S(13.0),
                        Px.mix(ink(), -1, v));
            } else {
                Px.round(
                        g,
                        x + kw / 2 - this.S(24.0),
                        y + kh / 2 - this.S(1.0),
                        this.S(48.0),
                        this.S(3.0),
                        this.S(1.0),
                        Px.mix(HudElements.T().muted(), -1, v));
            }
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

            for (long t : times) {
                if (now - t < 1000L) {
                    ++c;
                }
            }

            return c;
        }
    }
}
