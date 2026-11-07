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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.arcanic.visuals.core.ArcanicAccount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Premium skins. Their textures are not in the mod: the Arcanic API hands them to Premium accounts only, so a build
 * from the sources has the model and the name of such a skin but nothing to paint it with. They live in memory for as
 * long as the account keeps Premium.
 */
public final class PremiumSkins {
    private PremiumSkins() {}

    private enum Got {
        LOADING,
        READY,
        FAILED
    }

    private record Entry(Got got, long at) {}

    private static final long RETRY_MS = 30_000L;
    private static final Map<String, Entry> TEXTURES = new ConcurrentHashMap<>();
    private static volatile String owner = "";

    /** A Premium skin this account may not wear. */
    public static boolean locked(MeleeDesign design) {
        return design.isPremium() && !ArcanicAccount.premium();
    }

    /** Whether the skin can be drawn now; asks the API for a Premium skin's texture the first time. */
    public static boolean ready(MeleeDesign design) {
        if (!design.isPremium()) return true;
        if (!ArcanicAccount.premium()) {
            forget();
            return false;
        }
        String account = ArcanicAccount.name();
        if (!account.equals(owner)) {
            forget();
            owner = account;
        }
        Entry e = TEXTURES.get(design.getKey());
        if (e != null && e.got() == Got.READY) return true;
        if (e == null || e.got() == Got.FAILED && System.currentTimeMillis() - e.at() > RETRY_MS) fetch(design);
        return false;
    }

    private static void fetch(MeleeDesign design) {
        String key = design.getKey();
        TEXTURES.put(key, new Entry(Got.LOADING, System.currentTimeMillis()));
        ArcanicAccount.premiumFile("/api/v1/cosmetics/" + key).whenComplete((bytes, err) -> {
            if (err != null || bytes == null) {
                TEXTURES.put(key, new Entry(Got.FAILED, System.currentTimeMillis()));
                return;
            }
            NativeImage image;
            try {
                image = NativeImage.read(new ByteArrayInputStream(bytes));
            } catch (IOException e) {
                TEXTURES.put(key, new Entry(Got.FAILED, System.currentTimeMillis()));
                return;
            }
            Minecraft.getInstance().execute(() -> {
                if (!ArcanicAccount.premium()) {
                    image.close();
                    TEXTURES.remove(key);
                    return;
                }
                Minecraft.getInstance()
                        .getTextureManager()
                        .register(design.getTexture(), new DynamicTexture(() -> "arcanic premium " + key, image));
                TEXTURES.put(key, new Entry(Got.READY, System.currentTimeMillis()));
            });
        });
    }

    /** Drops every Premium texture: the account signed out, changed or lost Premium. */
    private static void forget() {
        if (TEXTURES.isEmpty()) return;
        for (String key : TEXTURES.keySet()) {
            MeleeDesign design = MeleeCatalog.byKey(key);
            if (design != null && TEXTURES.get(key).got() == Got.READY)
                Minecraft.getInstance()
                        .execute(() ->
                                Minecraft.getInstance().getTextureManager().release(design.getTexture()));
        }
        TEXTURES.clear();
        owner = "";
    }
}
