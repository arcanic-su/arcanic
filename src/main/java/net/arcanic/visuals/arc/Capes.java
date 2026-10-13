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
package net.arcanic.visuals.arc;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Sync;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.render.TexWarm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;

/** Animated capes on the cloth physics. */
public final class Capes extends Module {
    private static final Map<String, int[]> TIMING = new LinkedHashMap<>();

    private static final Map<String, String> FILES = new LinkedHashMap<>();

    static {
        add("Arcanic", "arcanic", 16, 140);
        add("Arcanic Neon", "neon", 12, 110);
        // made for Arcanic (converted to the same strips: the cape's outer face, 160 x 256 a frame)
        add("Leopard", "leopard", 1, 1000);
        add("Potion", "potion", 1, 1000);
        add("Diamond Sword", "diamond_sword", 11, 150);
        add("Glitch", "glitch", 14, 50);
        add("Sweatgod", "sweatgod", 5, 200);
    }

    private static void add(String name, String file, int frames, int ms) {
        FILES.put(name, file);
        TIMING.put(name, new int[] {frames, ms});
    }

    public static List<String> designs() {
        return List.copyOf(FILES.keySet());
    }

    public final Setting.Mode design =
            add(new Setting.Mode("design", "Рисунок", "Arcanic", FILES.keySet().toArray(String[]::new)));
    private static final Map<String, Identifier[]> textures = new LinkedHashMap<>();

    public Capes() {
        super(
                "capes",
                "Плащи",
                "Живая ткань Arcanic и анимированные плащи Arcanic: фирменные и забавные.",
                Category.COSMETICS,
                false);
    }

    public static Identifier resolve(AbstractClientPlayer player) {
        var mc = Minecraft.getInstance();
        if (player == null || player.isInvisible()) return null;
        if (player == mc.player) return Modules.CAPES.enabled() ? worldFrame(Modules.CAPES.design.get()) : null;
        // another player with the client: the cape they chose, as the sync reports it
        var remote = Sync.of(player);
        String design = remote == null || remote.cape() == null ? null : byId(remote.cape());
        return design == null ? null : worldFrame(design);
    }

    public static String id(String design) {
        return FILES.get(known(design));
    }

    private static String byId(String id) {
        for (var e : FILES.entrySet()) if (e.getValue().equals(id)) return e.getKey();
        return null;
    }

    private static String known(String design) {
        return FILES.containsKey(design) ? design : "Arcanic";
    }

    private static int frame(String design) {
        int[] timing = TIMING.get(design);
        return Theme.animations ? (int) ((System.currentTimeMillis() / timing[1]) % timing[0]) : 0;
    }

    /**
     * Menu preview: the art strip itself (the cape's front, 160 x 256 per frame, frames stacked), or null while it
     * loads.
     */
    public record Strip(Identifier id, int frame, int frames) {}

    public static Strip strip(String design) {
        design = known(design);
        Identifier id =
                Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/cape/" + FILES.get(design) + ".png");
        if (!TexWarm.ready(id)) return null;
        return new Strip(id, frame(design), TIMING.get(design)[0]);
    }

    private static Identifier worldFrame(String design) {
        design = known(design);
        Identifier[] ids = textures.computeIfAbsent(design, Capes::requestWorld);
        int n = ids.length, i = frame(design);
        for (int k = 0; k < n; k++) {
            Identifier id = ids[(i - k + n) % n];
            if (TexWarm.registered(id)) return id;
        }
        return null;
    }

    private static Identifier[] requestWorld(String design) {
        String file = FILES.get(design);
        int count = TIMING.get(design)[0];
        Identifier[] out = new Identifier[count];
        NativeImage[] strip = {null}; // decoded once on the worker, shared by its frames, freed after the last
        int[] left = {count};
        for (int f = 0; f < count; f++) {
            int frame = f;
            out[f] = Identifier.fromNamespaceAndPath("arcanicvisuals", "cape/" + file + "_" + f);
            TexWarm.request(out[f], () -> {
                if (strip[0] == null)
                    try (InputStream in =
                            Capes.class.getResourceAsStream("/assets/arcanicvisuals/textures/cape/" + file + ".png")) {
                        strip[0] = NativeImage.read(in);
                    }
                NativeImage image = layout(strip[0], frame);
                if (--left[0] == 0) {
                    strip[0].close();
                    strip[0] = null;
                }
                return image;
            });
        }
        return out;
    }

    private static NativeImage layout(NativeImage strip, int frame) {
        NativeImage image = new NativeImage(1024, 512, false);
        int oy = frame * 256;
        int edge = strip.getPixel(2, oy + 128) | 0xFF000000;
        image.fillRect(0, 0, 1024, 512, edge);
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 160; x++) {
                int c = strip.getPixel(x, oy + y) | 0xFF000000;
                image.setPixel(16 + x, 16 + y, c);
                image.setPixel(192 + (159 - x), 16 + y, c);
            }
        // the cape's thin sides and top/bottom take the colour of the face next to them
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 16; x++) {
                image.setPixel(x, 16 + y, strip.getPixel(0, oy + y) | 0xFF000000);
                image.setPixel(176 + x, 16 + y, strip.getPixel(159, oy + y) | 0xFF000000);
            }
        for (int x = 0; x < 160; x++)
            for (int y = 0; y < 16; y++) {
                image.setPixel(16 + x, y, strip.getPixel(x, oy) | 0xFF000000);
                image.setPixel(176 + x, y, strip.getPixel(x, oy + 255) | 0xFF000000);
            }
        return image;
    }
}
