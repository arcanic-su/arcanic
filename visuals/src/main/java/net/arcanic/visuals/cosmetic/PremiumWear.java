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
package net.arcanic.visuals.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.arcanic.visuals.core.ArcanicAccount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Premium wearables: their models are not in the jar. Any signed-in account downloads them from the API (to see them
 * on others); wearing one takes Premium, which the sync server checks too.
 */
final class PremiumWear {
    private PremiumWear() {}

    private static final long RETRY_MS = 30_000;
    private static final Map<Integer, Wearables.Geo> READY = new HashMap<>();
    private static final Map<Integer, Long> FAILED = new HashMap<>();
    private static final Map<Integer, Boolean> LOADING = new HashMap<>();

    /** The model when it is here; otherwise null, and the download starts. Game thread only. */
    static Wearables.Geo geo(int n) {
        Wearables.Geo g = READY.get(n);
        if (g != null || LOADING.containsKey(n)) return g;
        Long failed = FAILED.get(n);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_MS) return null;
        LOADING.put(n, true);
        CompletableFuture<byte[]> model = ArcanicAccount.accountFile("/api/v1/wear/" + n + "/model");
        CompletableFuture<byte[]> texture = ArcanicAccount.accountFile("/api/v1/wear/" + n + "/texture");
        model.thenCombine(texture, (m, t) -> new byte[][] {m, t})
                .whenComplete((both, e) -> Minecraft.getInstance().execute(() -> arrived(n, e == null ? both : null)));
        return null;
    }

    private static void arrived(int n, byte[][] both) {
        LOADING.remove(n);
        if (both == null || both[0] == null || both[1] == null) {
            FAILED.put(n, System.currentTimeMillis());
            return;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(both[1]));
            Identifier id = Identifier.fromNamespaceAndPath("arcanicvisuals", "wear_premium/" + n);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "wear " + n, image));
            Wearables.Geo g = Wearables.Geo.load(n, new ByteArrayInputStream(both[0]), id);
            if (g == null) FAILED.put(n, System.currentTimeMillis());
            else READY.put(n, g);
        } catch (Exception e) {
            FAILED.put(n, System.currentTimeMillis());
        }
    }
}
