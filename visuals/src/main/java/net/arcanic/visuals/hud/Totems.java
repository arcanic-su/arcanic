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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Totems: whose totem you just broke, and how many you carry over the hotbar. */
public final class Totems {
    private Totems() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (level.isClientSide()) Modules.TOTEM_TRACKER.hit(target);
            return InteractionResult.PASS;
        });
    }

    /** "Name lost a totem": only for someone you hit in the last five seconds, with whether it was enchanted. */
    public static final class Tracker extends Module {
        private static final long WINDOW = 5000L;
        private final Setting.Bool enchanted = this.add(new Setting.Bool("enchanted", "Показывать талисман", true));
        private final Map<UUID, Long> hits = new HashMap<>();
        private int lastTick = -1;

        public Tracker() {
            super(
                    "totem_tracker",
                    "Totem Tracker",
                    "Пишет в чат, когда тот, кого ты бил, теряет тотем, и был ли это талисман",
                    Category.COMBAT,
                    false);
        }

        void hit(Entity target) {
            if (this.enabled() && target instanceof LivingEntity && target != Minecraft.getInstance().player) {
                this.hits.put(target.getUUID(), System.currentTimeMillis());
            }
        }

        /** From the totem entity event, before the game swaps the totem out of the hand. */
        public void popped(LivingEntity e) {
            LocalPlayer me = Minecraft.getInstance().player;
            if (!this.enabled() || me == null || e == me) return;
            long now = System.currentTimeMillis();
            Long at = this.hits.get(e.getUUID());
            this.hits.values().removeIf(t -> now - t > WINDOW);
            if (at == null || now - at > WINDOW || me.tickCount == this.lastTick) return;
            this.lastTick = me.tickCount;
            ItemStack totem = e.getMainHandItem().is(Items.TOTEM_OF_UNDYING) ? e.getMainHandItem() : e.getOffhandItem();
            MutableComponent line = Component.literal(e.getName().getString())
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" потерял тотем").withStyle(ChatFormatting.GRAY));
            if (this.enchanted.on()) {
                boolean charm = totem.is(Items.TOTEM_OF_UNDYING) && totem.hasFoil();
                line.append(Component.literal(charm ? "  · Талик ✔" : "  · Талик ✘")
                        .withStyle(charm ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            me.displayClientMessage(line, false);
        }

        protected void onDisable() {
            this.hits.clear();
        }
    }

    /** Totem icons over the hunger bar: the ones in your hands lit, the rest in the inventory dim. */
    public static final class Bar extends Module {
        private static final Identifier FULL =
                Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/hud/totem.png");
        private static final Identifier EMPTY =
                Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/hud/totem_empty.png");
        private static final int MAX = 10;

        public Bar() {
            super(
                    "totem_bar",
                    "Totem Bar",
                    "Сколько у тебя тотемов — значками над полоской голода",
                    Category.HUD,
                    false);
        }

        public void render(GuiGraphics g) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (!this.enabled() || p == null || mc.options.hideGui || p.isSpectator() || p.isCreative()) return;
            int count = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.is(Items.TOTEM_OF_UNDYING)) count += s.getCount();
            }
            int held = 0;
            if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                count += p.getOffhandItem().getCount();
                held++;
            }
            if (p.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) held++;
            int n = Math.min(count, MAX);
            if (n <= 0) return;
            int right = g.guiWidth() / 2 + 91;
            int x0 = right - (9 + (n - 1) * 8);
            int y = g.guiHeight() - 51 - lift(p);
            for (int i = 0; i < n; i++) {
                Identifier tex = i >= n - held ? FULL : EMPTY;
                g.blit(RenderPipelines.GUI_TEXTURED, tex, x0 + i * 8, y, 0.0F, 0.0F, 9, 9, 9, 9);
            }
        }

        /** Room the game takes above the hunger bar: air bubbles and a ridden animal's hearts. */
        private static int lift(LocalPlayer p) {
            int up = 0;
            if (p.isUnderWater() || p.getAirSupply() < p.getMaxAirSupply()) up += 10;
            if (p.getVehicle() instanceof LivingEntity v && v.getMaxHealth() > 20.0F) {
                up += Mth.ceil((v.getMaxHealth() - 20.0F) / 20.0F) * 10;
            }
            return up;
        }
    }
}
