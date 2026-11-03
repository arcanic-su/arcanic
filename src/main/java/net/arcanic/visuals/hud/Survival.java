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

import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Survival {
    private static final int RED = -45730;
    private static final int ORANGE = -24822;
    private static final int GOLD = -14003;

    private Survival() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    static boolean playing() {
        Minecraft mc = mc();
        return mc.player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer() && mc.player.isAlive();
    }

    public static void screen(GuiGraphics g) {
        LowHealth m = Survival.LowHealth.instance;
        if (m != null && m.enabled()) {
            m.edges(g);
        }
    }

    public static final class LowHealth extends HudModule {
        static LowHealth instance;
        final Setting.Num threshold = this.add(new Setting.Num("threshold", "Порог", 6.0, 1.0, 16.0, 1.0, " ХП"));
        final Setting.Bool edges = this.add(new Setting.Bool("edges", "Красные края экрана", true));
        final Setting.Bool beat = this.add(new Setting.Bool("beat", "Стук сердца", true));
        final Setting.Num volume;
        private double phase;
        private long last;
        private float shown;
        private float danger;

        public LowHealth() {
            super(
                    "low_hp",
                    "Мало здоровья",
                    "Красные края экрана, стук сердца и предупреждение в вотермарке",
                    false,
                    0.46,
                    0.6);
            Setting.Num var10001 = this.add(new Setting.Num("volume", "Громкость стука", 60.0, 10.0, 100.0, 5.0, "%"));
            Setting.Bool var10002 = this.beat;
            this.volume = (Setting.Num) var10001.showIf(var10002::on);
            instance = this;
        }

        private float hp() {
            LocalPlayer p = Survival.mc().player;
            return p == null ? 20.0F : p.getHealth() + p.getAbsorptionAmount();
        }

        private float danger() {
            float t = this.threshold.f();
            return Survival.playing()
                    ? Math.max(
                            0.0F,
                            Math.min(
                                    1.0F, (t - this.hp()) / Math.max(1.0F, t - 1.0F) + (this.hp() <= t ? 0.15F : 0.0F)))
                    : 0.0F;
        }

        private static float pulse(double ph) {
            double a = Math.exp(-Math.pow((ph - 0.08) / 0.06, 2.0));
            double b = 0.55 * Math.exp(-Math.pow((ph - 0.3) / 0.07, 2.0));
            return (float) Math.min(1.0, a + b);
        }

        private void step() {
            long now = System.nanoTime();
            float dt = this.last == 0L ? 0.0F : Math.min(0.1F, (float) (now - this.last) / 1.0E9F);
            this.last = now;
            boolean low = Survival.playing() && this.hp() <= this.threshold.f();
            this.shown += ((float) (low ? 1 : 0) - this.shown) * (1.0F - (float) Math.exp((double) (-dt * 8.0F)));
            this.danger += (this.danger() - this.danger) * (1.0F - (float) Math.exp((double) (-dt * 6.0F)));
            if (low) {
                DynamicIsland.lowHearts(this.hp());
            }

            if (this.shown < 0.01F) {
                this.phase = 0.0;
            } else {
                double period = 1.05 - 0.5 * (double) this.danger;
                double before = this.phase;
                this.phase += (double) dt / period;
                if (this.phase >= 1.0) {
                    --this.phase;
                    if (low && this.beat.on()) {
                        this.knock();
                    }
                } else if (before == 0.0 && low && this.beat.on()) {
                    this.knock();
                }
            }
        }

        private void knock() {
            float vol = this.volume.f() / 100.0F * (0.45F + 0.55F * this.danger);
            Survival.mc()
                    .getSoundManager()
                    .play(SimpleSoundInstance.forUI(
                            SoundEvents.WARDEN_HEARTBEAT, 1.0F - 0.08F * (1.0F - this.danger), vol));
        }

        public boolean shown() {
            return false;
        }

        void edges(GuiGraphics g) {
            this.step();
            if (this.edges.on() && !(this.shown < 0.01F)) {
                int w = Px.width();
                int h = Px.height();
                int d = Math.round((float) Math.min(w, h) * (0.14F + 0.08F * this.danger));
                float k = this.shown * (0.3F + 0.4F * this.danger) * (0.6F + 0.4F * pulse(this.phase));
                int c = Px.a(12850206, Math.round(130.0F * k));
                int z = 12850206;
                Px.vgrad(g, 0, 0, w, d, c, z);
                Px.vgrad(g, 0, h - d, w, d, z, c);
                Px.hgrad(g, 0, 0, d, h, c, z);
                Px.hgrad(g, w - d, 0, d, h, z, c);
            }
        }

        public void render(GuiGraphics g, boolean editing) {}
    }

    public static final class Inventory extends HudModule {
        final Setting.Bool hotbar = this.add(new Setting.Bool("hotbar", "Хотбар тоже", false));
        final Setting.Bool hideEmpty = this.add(new Setting.Bool("hide_empty", "Прятать пустой", false));

        public Inventory() {
            super("inventory_hud", "Инвентарь", "Содержимое инвентаря на экране", false, 0.008, 0.3);
        }

        public boolean shown() {
            if (!this.hideEmpty.on()) {
                return true;
            } else {
                LocalPlayer p = Survival.mc().player;
                if (p == null) {
                    return false;
                } else {
                    for (int i = 9; i < 36; ++i) {
                        if (!p.getInventory().getItem(i).isEmpty()) {
                            return true;
                        }
                    }

                    return false;
                }
            }
        }

        /**
         * A 142-unit panel with the bag in the icon column, then the 27 slots (36 with the hotbar) as a grid of equal
         * cells across the rest, each item 8.3 units square in the middle of its cell with its count at the corner.
         */
        public void render(GuiGraphics g, boolean editing) {
            LocalPlayer p = Survival.mc().player;
            int rows = this.hotbar.on() ? 4 : 3;
            float width = 142.0F;
            float height = 34.72F / 3.0F * rows + 12.0F;
            this.w = Math.round(this.u(width));
            this.h = Math.round(this.u(height));
            this.panel(g, 0.0F, 0.0F, width, height, RADIUS);
            this.iconColumn(g, "bag", 20.0F, height);
            float cw = (width - 20.0F) / 9.0F, ch = (height - 4.0F) / rows, is = 8.3F;
            for (int i = 0; i < rows * 9; ++i) {
                int slot = i < 27 ? 9 + i : i - 27;
                ItemStack st = p == null ? ItemStack.EMPTY : p.getInventory().getItem(slot);
                if (st.isEmpty()) continue;
                float x = 20.0F + (i % 9) * cw + (cw - is) * 0.5F;
                float y = 2.0F + (i / 9) * ch + (ch - is) * 0.5F;
                this.wItem(g, st, x, y, is);
                if (st.getCount() > 1) {
                    String c = String.valueOf(st.getCount());
                    this.cText(
                            g,
                            FACE_BOLD,
                            c,
                            x + is + 1.0F - tWidth(FACE_BOLD, c, 4.0F),
                            y + is - 0.6F,
                            4.0F,
                            0xF2FFFFFF);
                }
            }
            this.glare(g, 0.0F, 0.0F, width, height, RADIUS);
        }
    }

    public static final class Saturation extends Module {
        final Setting.Bool saturation = this.add(new Setting.Bool("saturation", "Насыщение под шкалой", true));
        final Setting.Bool preview = this.add(new Setting.Bool("preview", "Сколько даст еда в руке", true));
        private static final Identifier FULL = Identifier.withDefaultNamespace("hud/food_full");
        private static final Identifier HALF = Identifier.withDefaultNamespace("hud/food_half");

        public Saturation() {
            super(
                    "saturation",
                    "Сытость",
                    "Насыщение под шкалой голода и сколько восполнит еда в руке",
                    Category.HUD,
                    false);
        }

        public void overlay(GuiGraphics g) {
            Minecraft mc = Survival.mc();
            Player p = mc.player;
            if (this.enabled() && p != null && !mc.options.hideGui && Survival.playing()) {
                Entity var5 = p.getVehicle();
                if (var5 instanceof LivingEntity) {
                    LivingEntity v = (LivingEntity) var5;
                    if (v.showVehicleHealth()) {
                        return;
                    }
                }

                FoodData food = p.getFoodData();
                int right = g.guiWidth() / 2 + 91;
                int y = g.guiHeight() - 39;
                ItemStack held = p.getMainHandItem();
                FoodProperties props = (FoodProperties) held.get(DataComponents.FOOD);
                if (props == null) {
                    props = (FoodProperties) p.getOffhandItem().get(DataComponents.FOOD);
                }

                float t = (float) ((double) (System.currentTimeMillis() % 1400L) / 1400.0);
                float wave = 0.5F + 0.5F * (float) Math.sin((double) t * Math.PI * 2.0);
                if (this.preview.on() && props != null) {
                    int from = food.getFoodLevel();
                    int to = Math.min(20, from + props.nutrition());
                    int a = Math.round(90.0F + 110.0F * wave);

                    for (int v = from; v < to; v += 2) {
                        int i = v / 2;
                        int x = right - i * 8 - 9;
                        boolean half = to - v == 1 && v % 2 == 0;
                        g.blitSprite(
                                RenderPipelines.GUI_TEXTURED,
                                (Identifier) (half ? HALF : FULL),
                                x,
                                y,
                                9,
                                9,
                                a << 24 | 0xFFFFFF);
                    }
                }

                if (this.saturation.on()) {
                    Px.begin(g);

                    try {
                        float s = (float) Px.guiScale();
                        float sat = food.getSaturationLevel();
                        float extra = this.preview.on() && props != null
                                ? ((float) Math.min(food.getFoodLevel() + props.nutrition(), 20) - sat > 0.0F
                                        ? Math.min((float) props.nutrition() * props.saturation() * 2.0F, 20.0F - sat)
                                        : 0.0F)
                                : 0.0F;

                        for (int i = 0; i < 10; ++i) {
                            float have = Math.max(0.0F, Math.min(2.0F, sat - (float) (i * 2))) / 2.0F;
                            float more = Math.max(0.0F, Math.min(2.0F, sat + extra - (float) (i * 2))) / 2.0F;
                            if (more <= 0.0F) {
                                break;
                            }

                            int x = Math.round((float) (right - i * 8 - 9 + 1) * s);
                            int bw = Math.round(7.0F * s);
                            int by = Math.round(((float) y + 9.4F) * s);
                            int bh = Math.max(2, Math.round(1.3F * s));
                            if (more > have) {
                                int mw = Math.round((float) bw * more);
                                Px.round(
                                        g,
                                        x + bw - mw,
                                        by,
                                        mw,
                                        bh,
                                        bh / 2,
                                        Px.a(-14003, Math.round(60.0F + 90.0F * wave)));
                            }

                            if (have > 0.0F) {
                                int hw = Math.max(bh, Math.round((float) bw * have));
                                Px.round(g, x + bw - hw, by, hw, bh, bh / 2, -14003);
                            }
                        }
                    } finally {
                        Px.end(g);
                    }
                }
            }
        }
    }

    public static final class ArmorAlert extends Module {
        final Setting.Num threshold =
                this.add(new Setting.Num("threshold", "Предупреждать при", 15.0, 5.0, 50.0, 5.0, "%"));
        private static final EquipmentSlot[] SLOTS;
        private static final String[] NAMES;
        private final int[] said = new int[4];

        public ArmorAlert() {
            super(
                    "armor_alert",
                    "Прочность брони",
                    "Предупреждение в острове, когда броня почти сломана",
                    Category.HUD,
                    true);
        }

        public void tick() {
            Player p = Survival.mc().player;
            if (p != null) {
                for (int i = 0; i < 4; ++i) {
                    ItemStack st = p.getItemBySlot(SLOTS[i]);
                    if (!st.isEmpty() && st.isDamageableItem()) {
                        int pct = Math.round(
                                100.0F * (float) (st.getMaxDamage() - st.getDamageValue()) / (float) st.getMaxDamage());
                        if (pct > this.threshold.i()) {
                            this.said[i] = 0;
                        } else if (this.said[i] == 0 || this.said[i] == 1 && pct <= 5) {
                            boolean end = pct <= 5;
                            boolean elytra = st.is(Items.ELYTRA);
                            boolean many = elytra || i >= 2;
                            String var10000 = Lang.tr(elytra ? "Элитры" : NAMES[i]);
                            String title = var10000 + " "
                                    + Lang.tr(
                                            end
                                                    ? (many ? "вот-вот сломаются" : "вот-вот сломается")
                                                    : (many ? "почти сломаны" : "почти сломан"));
                            String sub = pct + "% " + Lang.tr("прочности");
                            if (!DynamicIsland.alert("armor:" + i, st, title, sub, "", end ? -45730 : -24822)) {
                                Notify.text(title + ": " + sub, false);
                            }

                            this.said[i] = end ? 2 : 1;
                        }
                    } else {
                        this.said[i] = 0;
                    }
                }
            }
        }

        static {
            SLOTS = new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
            };
            NAMES = new String[] {"Шлем", "Нагрудник", "Поножи", "Ботинки"};
        }
    }
}
