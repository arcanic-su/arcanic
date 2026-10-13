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
package net.arcanic.visuals.emote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

/** Emotes (dances) on your own player, picked from a wheel while the emote key is held. */
public final class Emotes extends Module {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath("arcanicvisuals", "emote");

    /** One emote of the wheel; {@code icon} is a texture or null (then the wheel shows its initial). */
    public record Emote(String name, Animation anim, Identifier icon) {}

    public final Setting.Bool camera, cancelOnMove;
    public final Setting.Num speed;

    private volatile List<Emote> bundled;

    private Emote playing;
    private CameraType savedCamera;
    private int fadeOut = -1;

    public Emotes() {
        super(
                "emotes",
                "Эмоции",
                "Танцы и жесты на колесе (держи клавишу эмоций, по умолчанию B). Видят и другие "
                        + "игроки с Arcanic. Свои эмоции (JSON из Blockbench или Emotecraft) — в "
                        + "config/arcanicvisuals/emotes.",
                Category.PLAYERS,
                false);
        camera = add(new Setting.Bool("camera", "Камера от 3-го лица", true));
        cancelOnMove = add(new Setting.Bool("cancel_on_move", "Прерывать движением", true));
        speed = add(new Setting.Num("speed", "Скорость", 1.0, 0.5, 2.0, 0.1, "x"));
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER, 1000, avatar -> new PlayerAnimationController(
                                avatar, (controller, state, setter) -> PlayState.STOP)
                        .setFirstPersonMode(FirstPersonMode.NONE));
    }

    public static Path dir() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("arcanicvisuals")
                .resolve("emotes");
    }

    /**
     * Emotes of the wheel: the bundled ones, then the player's folder, read again on each opening so new files show
     * up.
     */
    public synchronized List<Emote> reload() {
        if (bundled == null) bundled = loadBundled();
        List<Emote> list = new ArrayList<>(bundled);
        Path dir = dir();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.sorted().toList()) {
                String file = f.getFileName().toString();
                if (!file.toLowerCase(Locale.ROOT).endsWith(".json")) continue;
                try (Reader in = Files.newBufferedReader(f)) {
                    Map<String, Animation> anims = parse(in);
                    String stem = file.substring(0, file.length() - 5);
                    for (var e : anims.entrySet())
                        list.add(new Emote(anims.size() == 1 ? stem : e.getKey(), e.getValue(), null));
                } catch (Exception e) {
                    warn(file, e);
                }
            }
        } catch (IOException ignored) {
        }
        known = List.copyOf(list);
        return list;
    }

    private static List<Emote> loadBundled() {
        List<Emote> out = new ArrayList<>();
        var resources = Minecraft.getInstance().getResourceManager();
        var index = resources.getResource(Identifier.fromNamespaceAndPath("arcanicvisuals", "emotes/index.json"));
        if (index.isEmpty()) return out;
        try (Reader in = index.get().openAsReader()) {
            for (JsonElement el : JsonParser.parseReader(in).getAsJsonArray()) {
                String file = el.getAsJsonObject().get("file").getAsString(),
                        name = el.getAsJsonObject().get("name").getAsString();
                var res = resources.getResource(
                        Identifier.fromNamespaceAndPath("arcanicvisuals", "emotes/" + file + ".json"));
                if (res.isEmpty()) continue;
                try (Reader r = res.get().openAsReader()) {
                    Map<String, Animation> anims = parse(r);
                    if (!anims.isEmpty())
                        out.add(new Emote(
                                name,
                                anims.values().iterator().next(),
                                Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/emote/" + file + ".png")));
                } catch (Exception e) {
                    warn(file, e);
                }
            }
        } catch (Exception e) {
            warn("index.json", e);
        }
        return out;
    }

    private static Map<String, Animation> parse(Reader in) {
        JsonObject json = JsonParser.parseReader(in).getAsJsonObject();
        for (String k : new String[] {"name", "description", "author"})
            if (json.has(k) && !json.get(k).isJsonPrimitive()) json.remove(k);
        if (!json.has("name") && json.has("emote")) json.addProperty("name", "emote");
        return UniversalAnimLoader.loadAnimations(json);
    }

    private static void warn(String file, Exception e) {
        System.err.println("[ArcanicVisuals] emote " + file + " could not be read: " + e);
    }

    private static PlayerAnimationController controller(LocalPlayer p) {
        try {
            return PlayerAnimationAccess.getPlayerAnimationLayer(p, LAYER) instanceof PlayerAnimationController c
                    ? c
                    : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Emote playing() {
        return playing;
    }

    private volatile List<Emote> known = List.of();

    public Emote byName(String name) {
        for (Emote e : known) if (e.name().equals(name)) return e;
        if (known.isEmpty()) {
            reload();
            for (Emote e : known) if (e.name().equals(name)) return e;
        }
        return null;
    }

    /** Plays (or with null stops) a dance on another player's model; false if that player has no emote layer yet. */
    public static boolean playOn(AbstractClientPlayer p, String name) {
        PlayerAnimationController c;
        try {
            c = PlayerAnimationAccess.getPlayerAnimationLayer(p, LAYER) instanceof PlayerAnimationController x
                    ? x
                    : null;
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (c == null) return false;
        if (name == null) {
            c.stop();
            c.removeAllModifiers();
            return true;
        }
        Emote e = Modules.EMOTES.byName(name);
        if (e == null) return true; // a dance this client does not have: nothing to show
        c.removeAllModifiers();
        c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, EasingType.EASE_IN_OUT_SINE), e.anim());
        return true;
    }

    public void play(Emote e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        PlayerAnimationController c = p == null ? null : controller(p);
        if (c == null || e == null) return;
        c.removeAllModifiers();
        if (Math.abs(speed.f() - 1) > 1e-3) c.addModifierLast(new SpeedModifier(speed.f()));
        c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, EasingType.EASE_IN_OUT_SINE), e.anim());
        playing = e;
        fadeOut = -1;
        if (camera.on() && savedCamera == null && mc.options.getCameraType().isFirstPerson()) {
            savedCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    public void stop() {
        if (playing == null) return;
        playing = null;
        LocalPlayer p = Minecraft.getInstance().player;
        PlayerAnimationController c = p == null ? null : controller(p);
        if (c != null && c.isActive()) {
            c.addModifierLast(AbstractFadeModifier.standardFadeOut(4, EasingType.EASE_IN_OUT_SINE));
            fadeOut = 4;
        }
        restoreCamera();
    }

    private void restoreCamera() {
        if (savedCamera == null) return;
        Minecraft mc = Minecraft.getInstance();
        // leave the camera alone if the player switched it themselves during the emote
        if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(savedCamera);
        savedCamera = null;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            playing = null;
            savedCamera = null;
            fadeOut = -1;
            return;
        }
        PlayerAnimationController c = controller(p);
        if (fadeOut >= 0 && fadeOut-- == 0) {
            if (c != null && playing == null) {
                c.stop();
                c.removeAllModifiers();
            }
            fadeOut = -1;
        }
        if (playing == null) return;
        if (c == null || !c.isActive()) {
            playing = null;
            restoreCamera();
            return;
        } // a one-shot emote has ended
        if (cancelOnMove.on() && (moving(mc.options) || p.hurtTime > 0 || p.isPassenger() || p.isFallFlying())) stop();
    }

    private static boolean moving(Options o) {
        return o.keyUp.isDown()
                || o.keyDown.isDown()
                || o.keyLeft.isDown()
                || o.keyRight.isDown()
                || o.keyJump.isDown()
                || o.keyShift.isDown()
                || o.keyAttack.isDown()
                || o.keyUse.isDown();
    }

    @Override
    protected void onDisable() {
        stop();
    }
}
