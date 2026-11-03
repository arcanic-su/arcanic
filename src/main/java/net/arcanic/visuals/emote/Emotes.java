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
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
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
import java.util.Optional;
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
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class Emotes extends Module {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath("arcanicvisuals", "emote");
    public final Setting.Bool camera = this.add(new Setting.Bool("camera", "Камера от 3-го лица", true));
    public final Setting.Bool cancelOnMove = this.add(new Setting.Bool("cancel_on_move", "Прерывать движением", true));
    public final Setting.Num speed =
            (Setting.Num) this.add(new Setting.Num("speed", "Скорость", 1.0, 0.5, 2.0, 0.1, "x"));
    private volatile List<Emote> bundled;
    private Emote playing;
    private CameraType savedCamera;
    private int fadeOut = -1;
    private volatile List<Emote> known = List.of();

    public Emotes() {
        super(
                "emotes",
                "Эмоции",
                "Танцы и жесты на колесе (держи клавишу эмоций, по умолчанию B). Видят и другие игроки с Arcanic. Свои эмоции (JSON из Blockbench или Emotecraft) — в config/arcanicvisuals/emotes.",
                Category.PLAYERS,
                false);
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER, 1000, (avatar) -> (new PlayerAnimationController(
                                avatar, (controller, state, setter) -> PlayState.STOP))
                        .setFirstPersonMode(FirstPersonMode.NONE));
    }

    public static Path dir() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("arcanicvisuals")
                .resolve("emotes");
    }

    public synchronized List<Emote> reload() {
        if (this.bundled == null) {
            this.bundled = loadBundled();
        }

        List<Emote> list = new ArrayList(this.bundled);
        Path dir = dir();

        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }

        try {
            Stream<Path> files = Files.list(dir);

            try {
                for (Path f : files.sorted().toList()) {
                    String file = f.getFileName().toString();
                    if (file.toLowerCase(Locale.ROOT).endsWith(".json")) {
                        try {
                            Reader in = Files.newBufferedReader(f);

                            try {
                                Map<String, Animation> anims = parse(in);
                                String stem = file.substring(0, file.length() - 5);

                                for (Map.Entry<String, Animation> e : anims.entrySet()) {
                                    list.add(new Emote(
                                            anims.size() == 1 ? stem : (String) e.getKey(),
                                            (Animation) e.getValue(),
                                            (Identifier) null));
                                }
                            } catch (Throwable var15) {
                                if (in != null) {
                                    try {
                                        in.close();
                                    } catch (Throwable var13) {
                                        var15.addSuppressed(var13);
                                    }
                                }

                                throw var15;
                            }

                            if (in != null) {
                                in.close();
                            }
                        } catch (Exception e) {
                            warn(file, e);
                        }
                    }
                }
            } catch (Throwable var17) {
                if (files != null) {
                    try {
                        files.close();
                    } catch (Throwable var12) {
                        var17.addSuppressed(var12);
                    }
                }

                throw var17;
            }

            if (files != null) {
                files.close();
            }
        } catch (IOException ignored) {
        }

        this.known = List.copyOf(list);
        return list;
    }

    private static List<Emote> loadBundled() {
        List<Emote> out = new ArrayList<>();
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        Optional<Resource> index =
                resources.getResource(Identifier.fromNamespaceAndPath("arcanicvisuals", "emotes/index.json"));
        if (index.isEmpty()) {
            return out;
        } else {
            try {
                Reader in = ((Resource) index.get()).openAsReader();

                try {
                    for (JsonElement el : JsonParser.parseReader(in).getAsJsonArray()) {
                        String file = el.getAsJsonObject().get("file").getAsString();
                        String name = el.getAsJsonObject().get("name").getAsString();
                        Optional<Resource> res = resources.getResource(
                                Identifier.fromNamespaceAndPath("arcanicvisuals", "emotes/" + file + ".json"));
                        if (!res.isEmpty()) {
                            try {
                                Reader r = ((Resource) res.get()).openAsReader();

                                try {
                                    Map<String, Animation> anims = parse(r);
                                    if (!anims.isEmpty()) {
                                        out.add(new Emote(
                                                name,
                                                (Animation) anims.values()
                                                        .iterator()
                                                        .next(),
                                                Identifier.fromNamespaceAndPath(
                                                        "arcanicvisuals", "textures/emote/" + file + ".png")));
                                    }
                                } catch (Throwable var14) {
                                    if (r != null) {
                                        try {
                                            r.close();
                                        } catch (Throwable var13) {
                                            var14.addSuppressed(var13);
                                        }
                                    }

                                    throw var14;
                                }

                                if (r != null) {
                                    r.close();
                                }
                            } catch (Exception e) {
                                warn(file, e);
                            }
                        }
                    }
                } catch (Throwable var16) {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Throwable var12) {
                            var16.addSuppressed(var12);
                        }
                    }

                    throw var16;
                }

                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
                warn("index.json", e);
            }

            return out;
        }
    }

    private static Map<String, Animation> parse(Reader in) {
        JsonObject json = JsonParser.parseReader(in).getAsJsonObject();

        for (String k : new String[] {"name", "description", "author"}) {
            if (json.has(k) && !json.get(k).isJsonPrimitive()) {
                json.remove(k);
            }
        }

        if (!json.has("name") && json.has("emote")) {
            json.addProperty("name", "emote");
        }

        return UniversalAnimLoader.loadAnimations(json);
    }

    private static void warn(String file, Exception e) {
        System.err.println("[ArcanicVisuals] emote " + file + " could not be read: " + String.valueOf(e));
    }

    private static PlayerAnimationController controller(LocalPlayer p) {
        try {
            IAnimation var2 = PlayerAnimationAccess.getPlayerAnimationLayer(p, LAYER);
            PlayerAnimationController var10000;
            if (var2 instanceof PlayerAnimationController c) {
                var10000 = c;
            } else {
                var10000 = null;
            }

            return var10000;
        } catch (IllegalArgumentException var3) {
            return null;
        }
    }

    public Emote playing() {
        return this.playing;
    }

    public Emote byName(String name) {
        for (Emote e : this.known) {
            if (e.name().equals(name)) {
                return e;
            }
        }

        if (this.known.isEmpty()) {
            this.reload();

            for (Emote e : this.known) {
                if (e.name().equals(name)) {
                    return e;
                }
            }
        }

        return null;
    }

    public static boolean playOn(AbstractClientPlayer p, String name) {
        PlayerAnimationController c;
        try {
            IAnimation var4 = PlayerAnimationAccess.getPlayerAnimationLayer(p, LAYER);
            PlayerAnimationController var10000;
            if (var4 instanceof PlayerAnimationController x) {
                var10000 = x;
            } else {
                var10000 = null;
            }

            c = var10000;
        } catch (IllegalArgumentException var5) {
            return false;
        }

        if (c == null) {
            return false;
        } else if (name == null) {
            c.stop();
            c.removeAllModifiers();
            return true;
        } else {
            Emote e = Modules.EMOTES.byName(name);
            if (e == null) {
                return true;
            } else {
                c.removeAllModifiers();
                c.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(4, EasingType.EASE_IN_OUT_SINE), e.anim());
                return true;
            }
        }
    }

    public void play(Emote e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        PlayerAnimationController c = p == null ? null : controller(p);
        if (c != null && e != null) {
            c.removeAllModifiers();
            if ((double) Math.abs(this.speed.f() - 1.0F) > 0.001) {
                c.addModifierLast(new SpeedModifier(this.speed.f()));
            }

            c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, EasingType.EASE_IN_OUT_SINE), e.anim());
            this.playing = e;
            this.fadeOut = -1;
            if (this.camera.on()
                    && this.savedCamera == null
                    && mc.options.getCameraType().isFirstPerson()) {
                this.savedCamera = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
    }

    public void stop() {
        if (this.playing != null) {
            this.playing = null;
            LocalPlayer p = Minecraft.getInstance().player;
            PlayerAnimationController c = p == null ? null : controller(p);
            if (c != null && c.isActive()) {
                c.addModifierLast(AbstractFadeModifier.standardFadeOut(4, EasingType.EASE_IN_OUT_SINE));
                this.fadeOut = 4;
            }

            this.restoreCamera();
        }
    }

    private void restoreCamera() {
        if (this.savedCamera != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(this.savedCamera);
            }

            this.savedCamera = null;
        }
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            this.playing = null;
            this.savedCamera = null;
            this.fadeOut = -1;
        } else {
            PlayerAnimationController c = controller(p);
            if (this.fadeOut >= 0 && this.fadeOut-- == 0) {
                if (c != null && this.playing == null) {
                    c.stop();
                    c.removeAllModifiers();
                }

                this.fadeOut = -1;
            }

            if (this.playing != null) {
                if (c != null && c.isActive()) {
                    if (this.cancelOnMove.on()
                            && (moving(mc.options) || p.hurtTime > 0 || p.isPassenger() || p.isFallFlying())) {
                        this.stop();
                    }

                } else {
                    this.playing = null;
                    this.restoreCamera();
                }
            }
        }
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

    protected void onDisable() {
        this.stop();
    }

    public static record Emote(String name, Animation anim, Identifier icon) {}
}
