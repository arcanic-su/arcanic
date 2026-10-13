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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;

/** Baked melee models, loaded on a background thread the first time a skin is asked for. */
public final class MeleeModels {
    private static final String LOADER_THREAD = "arc-melee-loader";
    private static final Map<String, MeleeSkeleton> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private MeleeModels() {}

    public static MeleeSkeleton get(MeleeDesign design) {
        if (design == null) return null;
        MeleeSkeleton skeleton = CACHE.get(design.getGeometryKey());
        if (skeleton == null) preload(design);
        return skeleton;
    }

    public static GeoParts definition(MeleeDesign design) {
        if (design == null) return null;
        try {
            String json = read(design.modelResource());
            return json == null ? null : GeoParser.parse(json);
        } catch (Exception e) {
            LoggerFactory.getLogger("arcanicvisuals").warn("Melee loading failed: " + design.getGeometryKey(), e);
            return null;
        }
    }

    public static void preload(MeleeDesign design) {
        if (design == null || CACHE.containsKey(design.getGeometryKey()) || !LOADING.add(design.getGeometryKey()))
            return;
        Thread loader = new Thread(() -> load(design), LOADER_THREAD);
        loader.setDaemon(true);
        loader.start();
    }

    private static void load(MeleeDesign design) {
        try {
            String model = read(design.modelResource());
            String animation = read(design.animationResource());
            if (model == null || animation == null) return;
            GeoParts parts = GeoParser.parse(model);
            if (parts != null) {
                CACHE.put(design.getGeometryKey(), MeleeSkeleton.bake(parts, animation));
                evict(design.getGeometryKey());
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("arcanicvisuals").warn("Melee loading failed: " + design.getGeometryKey(), e);
        } finally {
            LOADING.remove(design.getGeometryKey());
        }
    }

    private static void evict(String justLoaded) {
        MeleeDesign equipped = MeleeCatalog.equipped();
        String equippedKey = equipped == null ? null : equipped.getGeometryKey();
        for (Iterator<String> it = CACHE.keySet().iterator(); it.hasNext(); ) {
            String key = it.next();
            if (!key.equals(justLoaded) && !key.equals(equippedKey)) it.remove();
        }
    }

    private static String read(String resource) throws Exception {
        try (InputStream in = MeleeModels.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
