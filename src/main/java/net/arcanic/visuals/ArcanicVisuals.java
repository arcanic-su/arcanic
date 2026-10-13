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

import com.mojang.blaze3d.platform.InputConstants;
import net.arcanic.visuals.arc.ClothSimulations;
import net.arcanic.visuals.arc.HandMovement;
import net.arcanic.visuals.arc.HandShaders;
import net.arcanic.visuals.arc.MeleeAnimator;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Sync;
import net.arcanic.visuals.emote.EmoteWheel;
import net.arcanic.visuals.emote.Emotes;
import net.arcanic.visuals.fx.ArcChams;
import net.arcanic.visuals.fx.Effects;
import net.arcanic.visuals.fx.OptimizerMod;
import net.arcanic.visuals.fx.WorldFx;
import net.arcanic.visuals.gui.ArcanicMenu;
import net.arcanic.visuals.hud.DynamicIsland;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.hud.HudRenderer;
import net.arcanic.visuals.hud.Notify;
import net.arcanic.visuals.hud.TargetHud;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Arcanic Visuals entrypoint: registers the keys, tick handlers, HUD and world render hooks. */
public final class ArcanicVisuals implements ClientModInitializer {
    public static final String ID = "arcanicvisuals";
    private static final Logger LOGGER = LoggerFactory.getLogger(ID);
    private static KeyMapping menuKey, inspectKey, emoteKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        OptimizerMod.set(Modules.OPTIMIZER.enabled());
        ClientTickEvents.END_CLIENT_TICK.register(OptimizerMod::guard);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            for (var h : Modules.HUD) if (h instanceof DynamicIsland island) island.joined();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            for (var h : Modules.HUD) if (h instanceof DynamicIsland island) island.disconnected();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            HandShaders.close();
            Music.close();
        });
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.arcanicvisuals.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));

        inspectKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.arcanicvisuals.inspect", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, cat));
        emoteKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.arcanicvisuals.emotes", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, cat));
        Emotes.register();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            HandMovement.tick(mc);
            if (mc.level != null) ClothSimulations.getInstance().tickAll(mc.level);
            else ClothSimulations.getInstance().clear();
            while (inspectKey.consumeClick())
                if (mc.screen == null) {
                    if (Modules.KNIVES.enabled()) MeleeAnimator.getInstance().onInspect();
                    else if (Modules.ANIM.enabled()) Modules.ANIM.startInspect();
                }
            while (menuKey.consumeClick()) if (mc.screen == null) mc.setScreen(new ArcanicMenu());
            while (emoteKey.consumeClick())
                if (mc.screen == null && mc.player != null && Modules.EMOTES.enabled())
                    mc.setScreen(new EmoteWheel(emoteKey));
            for (Module m : Modules.ALL)
                if (m.enabled())
                    try {
                        m.tick();
                    } catch (RuntimeException e) {
                        LOGGER.debug("Client tick failed", e);
                    }
            Config.tick();
            TexWarm.pump(4_000_000L);
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
            if (Modules.MARKER.enabled()) TargetHud.update();
        });

        // hit effects, kill tracking and the target HUD learn about our own attacks (client prediction only)
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player) {
                if (Modules.HIT.enabled()) Modules.HIT.onAttack(player, entity);
                if (Modules.KILL.enabled()) Modules.KILL.onAttack(entity);
                if (Modules.KILLSOUND.enabled()) Modules.KILLSOUND.onAttack(entity);
                if (Modules.HITSOUND.enabled()) Modules.HITSOUND.onAttack(entity);

                if (Modules.COMBO.enabled()) Modules.COMBO.onAttack(player, entity);
                TargetHud.hit(entity);
            }
            return InteractionResult.PASS;
        });
        ClientPreAttackCallback.EVENT.register((mc, player, clicks) -> {
            if (clicks > 0) {
                HudElements.Clicks.click();
            }
            return false;
        });

        // The custom block outline replaces the vanilla one.
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register(
                (ctx, state) -> !Modules.BLOCK.enabled() || !Modules.BLOCK.outline.on());
        WorldRenderEvents.END_MAIN.register(ArcChams::render);
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            try {
                WorldFx.render(ctx);
            } catch (RuntimeException e) {
                LOGGER.debug("Client tick failed", e);
            }
        });
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(ID, "hud"),
                (g, dt) -> HudRenderer.renderAll(g));
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, dt) -> {
            if (Modules.CROSSHAIR.enabled()) {
                if (!Minecraft.getInstance().options.hideGui) Modules.CROSSHAIR.render(g);
            } else vanilla.render(g, dt);
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
