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
package net.arcanic.visuals.render;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.arcanic.visuals.mixin.TextureManagerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Textures loaded without stalling a frame. */
public final class TexWarm {
    private TexWarm() {}

    private record Done(Identifier id, NativeImage image) {}

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "arcanic-texture-warm");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });
    private static final Set<Identifier> pending = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<Done> finished = new ConcurrentLinkedQueue<>();
    private static final Map<Identifier, Boolean> ours = new ConcurrentHashMap<>();

    public static boolean ready(Identifier id) {
        if (registered(id)) return true;
        request(id, () -> {
            var res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) return null;
            try (InputStream in = res.get().open()) {
                return NativeImage.read(in);
            }
        });
        return false;
    }

    /** Builds an image off the render thread and registers it as a texture under {@code id} when done. */
    public static void request(Identifier id, Callable<NativeImage> make) {
        if (registered(id) || !pending.add(id)) return;
        WORKER.execute(() -> {
            NativeImage image = null;
            try {
                image = make.call();
            } catch (Exception e) {
                System.err.println("[ArcanicVisuals] texture " + id + ": " + e);
            }
            if (image != null) finished.add(new Done(id, image));
            else pending.remove(id);
        });
    }

    public static boolean registered(Identifier id) {
        return ours.containsKey(id)
                || ((TextureManagerAccess) Minecraft.getInstance().getTextureManager())
                        .arcanicvisuals$byPath()
                        .containsKey(id);
    }

    /** Render thread: uploads finished images for at most {@code budgetNanos}. */
    public static void pump(long budgetNanos) {
        if (finished.isEmpty()) return;
        long end = System.nanoTime() + budgetNanos;
        var tm = Minecraft.getInstance().getTextureManager();
        Done d;
        while ((d = finished.poll()) != null) {
            Done done = d;
            tm.register(done.id, new DynamicTexture(() -> "arcanic " + done.id, done.image));
            ours.put(done.id, true);
            pending.remove(done.id);
            if (System.nanoTime() > end) break;
        }
    }
}
