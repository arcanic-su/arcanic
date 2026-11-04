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
package net.arcanic.visuals;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.arcanic.visuals.arc.ClothSimulations;
import net.arcanic.visuals.arc.HandMovement;
import net.arcanic.visuals.arc.HandShaders;
import net.arcanic.visuals.arc.KnifeStudio;
import net.arcanic.visuals.arc.MeleeAnimator;
import net.arcanic.visuals.arc.MeleeShots;
import net.arcanic.visuals.arc.MeleeSkins;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Sync;
import net.arcanic.visuals.emote.EmoteWheel;
import net.arcanic.visuals.emote.Emotes;
import net.arcanic.visuals.fx.AmbienceWeather;
import net.arcanic.visuals.fx.ArcChams;
import net.arcanic.visuals.fx.Effects;
import net.arcanic.visuals.fx.ExplosiveThing;
import net.arcanic.visuals.fx.ItemScroller;
import net.arcanic.visuals.fx.OptimizerMod;
import net.arcanic.visuals.fx.Studio;
import net.arcanic.visuals.fx.TntZone;
import net.arcanic.visuals.fx.TrapZone;
import net.arcanic.visuals.fx.WorldFx;
import net.arcanic.visuals.fx.impact.ImpactEffects;
import net.arcanic.visuals.gui.ArcanicMenu;
import net.arcanic.visuals.helper.EnchantSearch;
import net.arcanic.visuals.helper.HolyFeatures;
import net.arcanic.visuals.helper.ItemCatalog;
import net.arcanic.visuals.helper.Kits;
import net.arcanic.visuals.helper.Pings;
import net.arcanic.visuals.helper.ShulkerPreview;
import net.arcanic.visuals.helper.Waypoints;
import net.arcanic.visuals.hud.DynamicIsland;
import net.arcanic.visuals.hud.FriendCards;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.hud.HudModule;
import net.arcanic.visuals.hud.HudRenderer;
import net.arcanic.visuals.hud.Notify;
import net.arcanic.visuals.hud.TargetHud;
import net.arcanic.visuals.lua.Scripts;
import net.arcanic.visuals.media.Music;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.TexWarm;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArcanicVisuals implements ClientModInitializer {
    public static final String ID = "arcanicvisuals";
    private static final Logger LOGGER = LoggerFactory.getLogger("arcanicvisuals");
    private static KeyMapping menuKey;
    private static KeyMapping inspectKey;
    private static KeyMapping emoteKey;
    private static KeyMapping markKey;
    private static KeyMapping acceptKey;

    public void onInitializeClient() {
        Config.load();
        OptimizerMod.set(Modules.OPTIMIZER.enabled());
        ClientTickEvents.END_CLIENT_TICK.register(OptimizerMod::guard);
        ClientPlayConnectionEvents.JOIN.register((ClientPlayConnectionEvents.Join) (handler, sender, mc) -> {
            for (HudModule h : Modules.HUD) {
                if (h instanceof DynamicIsland island) {
                    island.joined();
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((ClientPlayConnectionEvents.Disconnect) (handler, mc) -> {
            for (HudModule h : Modules.HUD) {
                if (h instanceof DynamicIsland island) {
                    island.disconnected();
                }
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register((ClientLifecycleEvents.ClientStopping) (mc) -> {
            HandShaders.close();
            Music.close();
        });
        KeyMapping.Category cat = Category.register(Identifier.fromNamespaceAndPath("arcanicvisuals", "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.arcanicvisuals.menu", Type.KEYSYM, 344, cat));
        inspectKey =
                KeyBindingHelper.registerKeyBinding(new KeyMapping("key.arcanicvisuals.inspect", Type.KEYSYM, 71, cat));
        markKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.arcanicvisuals.mark", Type.KEYSYM, 78, cat));
        emoteKey =
                KeyBindingHelper.registerKeyBinding(new KeyMapping("key.arcanicvisuals.emotes", Type.KEYSYM, 66, cat));
        acceptKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.arcanicvisuals.friend_accept", Type.KEYSYM, 89, cat));
        Emotes.register();
        TrapZone.register();
        HolyFeatures.register();
        ItemScroller.register();
        ItemCatalog.register();
        Waypoints.register();
        Studio.register();
        KnifeStudio.register();
        net.arcanic.visuals.helper.FtEvents.register();
        net.arcanic.visuals.fx.FtRegions.register();
        net.arcanic.visuals.hud.Totems.register();
        net.arcanic.visuals.fx.Extras.register();
        net.arcanic.visuals.helper.ServerTools.register();
        net.arcanic.visuals.helper.ChestSorter.register();
        TntZone.register();
        EnchantSearch.register();
        ExplosiveThing.register();
        ShulkerPreview.register();
        Kits.register();
        Scripts.register();
        net.arcanic.visuals.media.MusicAccounts.load();
        ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick) (mc) -> {
            try {
                Scripts.tick();
                net.arcanic.visuals.media.MusicAccounts.tick();
            } catch (RuntimeException e) {
                LOGGER.debug("Lua tick failed", e);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick) (mc) -> {
            HandMovement.tick(mc);
            MeleeShots.tick(mc);
            MeleeSkins.tickAnimators();
            if (mc.level != null) {
                ClothSimulations.getInstance().tickAll(mc.level);
            } else {
                ClothSimulations.getInstance().clear();
            }

            while (inspectKey.consumeClick()) {
                if (mc.screen == null && (Modules.KNIVES.enabled() || Modules.GUNS.enabled())) {
                    MeleeAnimator.getInstance().onInspect();
                }
            }

            while (menuKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new ArcanicMenu());
                }
            }

            while (markKey.consumeClick()) {
                if (mc.screen == null && Modules.MARKS.enabled()) {
                    Modules.MARKS.key();
                }
            }

            while (acceptKey.consumeClick()) {
                if (mc.screen == null) {
                    FriendCards.acceptNewest();
                }
            }

            FriendCards.keyName(acceptKey.getTranslatedKeyMessage().getString());
            Pings.tick(mc);

            while (emoteKey.consumeClick()) {
                if (mc.screen == null && mc.player != null && Modules.EMOTES.enabled()) {
                    mc.setScreen(new EmoteWheel(emoteKey));
                }
            }

            for (Module m : Modules.ALL) {
                if (m.enabled()) {
                    try {
                        m.tick();
                    } catch (RuntimeException e) {
                        LOGGER.debug("Client tick failed", e);
                    }
                }
            }

            Config.tick();
            TexWarm.pump(4000000L);
            Notify.tick(mc);

            try {
                Sync.tick(mc);
            } catch (RuntimeException e) {
                LOGGER.debug("Client tick failed", e);
            }

            try {
                EmoteWheel.warm(mc);
            } catch (RuntimeException e) {
                LOGGER.debug("Client tick failed", e);
            }

            Effects.tick();
            if (Modules.MARKER.enabled()) {
                TargetHud.update();
            }
        });
        AttackEntityCallback.EVENT.register((AttackEntityCallback) (player, level, hand, entity, hit) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player) {
                if (Modules.HIT.enabled()) {
                    Modules.HIT.onAttack(player, entity);
                }

                if (Modules.KILL.enabled()) {
                    Modules.KILL.onAttack(entity);
                }

                ImpactEffects.attack(entity);
                if (Modules.KILLSOUND.enabled()) {
                    Modules.KILLSOUND.onAttack(entity);
                }

                if (Modules.HITSOUND.enabled()) {
                    Modules.HITSOUND.onAttack(entity);
                }

                if (Modules.COMBO.enabled()) {
                    Modules.COMBO.onAttack(player, entity);
                }

                TargetHud.hit(entity);
            }

            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((UseItemCallback) (player, level, hand) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player && Modules.POTION_FX.enabled()) {
                Modules.POTION_FX.used(player.getItemInHand(hand));
            }

            return InteractionResult.PASS;
        });
        ClientPreAttackCallback.EVENT.register((ClientPreAttackCallback) (mc, player, clicks) -> {
            if (clicks > 0) {
                HudElements.Clicks.click();
            }

            return false;
        });
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((WorldRenderEvents.BeforeBlockOutline)
                (ctx, state) -> !Modules.BLOCK.enabled() || !Modules.BLOCK.outline.on());
        WorldRenderEvents.END_MAIN.register(AmbienceWeather::render);
        WorldRenderEvents.END_MAIN.register(AmbienceWeather::potionWaves);
        WorldRenderEvents.END_MAIN.register(ArcChams::render);
        WorldRenderEvents.AFTER_ENTITIES.register((WorldRenderEvents.AfterEntities) (ctx) -> {
            try {
                WorldFx.render(ctx);
            } catch (RuntimeException e) {
                LOGGER.debug("Client tick failed", e);
            }
        });
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("arcanicvisuals", "hud"),
                (g, dt) -> HudRenderer.renderAll(g));
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("arcanicvisuals", "lua"),
                (g, dt) -> Scripts.render(g));
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, (vanilla) -> (g, dt) -> {
            vanilla.render(g, dt);
            Modules.SATURATION.overlay(g);
            Modules.TOTEM_BAR.render(g);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, (vanilla) -> (g, dt) -> {
            if (!Modules.BOSS_BARS.enabled()) {
                vanilla.render(g, dt);
            }
        });
        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, (vanilla) -> (g, dt) -> {
            if (Modules.TAB.enabled()) {
                Modules.TAB.render(g);
            } else {
                Modules.UI_ANIMATIONS.tab(g, () -> vanilla.render(g, dt));
            }
        });
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, (vanilla) -> (g, dt) -> {
            if (Modules.CROSSHAIR.enabled()) {
                if (!Minecraft.getInstance().options.hideGui) {
                    Modules.CROSSHAIR.render(g);
                }
            } else {
                vanilla.render(g, dt);
            }

            if (Modules.COMBO.enabled()
                    && !Minecraft.getInstance().options.hideGui
                    && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                Px.begin(g);

                try {
                    Modules.COMBO.render(g);
                } finally {
                    Px.end(g);
                }
            }
        });
    }
}
