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
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/** Grey copies of textures for Recolour. */
public final class GrayTex {
    private GrayTex() {}

    static float lum(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return (.299f * r + .587f * g + .114f * b) / 255f;
    }

    static int grey(int argb, float mean) {
        int a = argb >>> 24;
        if (a == 0) return 0;
        float v = Math.max(.05f, Math.min(1, .78f + (lum(argb) - mean) * 1.45f));
        int c = Math.round(v * 255);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static float mean(NativeImage img, int x0, int y0, int w, int h) {
        double sum = 0, n = 0;
        for (int y = y0; y < y0 + h && y < img.getHeight(); y++)
            for (int x = x0; x < x0 + w && x < img.getWidth(); x++) {
                int p = img.getPixel(x, y);
                float a = (p >>> 24) / 255f;
                if (a <= .1f) continue;
                sum += lum(p) * a;
                n += a;
            }
        return n == 0 ? .5f : (float) (sum / n);
    }

    private static final class Atlas {
        final Identifier id;
        final DynamicTexture tex;
        final int w, h;
        final Set<TextureAtlasSprite> done = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<TextureAtlasSprite> failed = Collections.newSetFromMap(new IdentityHashMap<>());

        Atlas(Identifier id, DynamicTexture tex, int w, int h) {
            this.id = id;
            this.tex = tex;
            this.w = w;
            this.h = h;
        }
    }

    private static final Map<Identifier, Atlas> ATLASES = new HashMap<>();
    private static final Map<Identifier, Identifier> SINGLE = new HashMap<>();
    private static final Set<Identifier> FAILED = new HashSet<>();

    /** The grey copy of the atlas these quads use, with their sprites filled in; null if it can't be made. */
    public static Identifier atlasFor(List<BakedQuad> quads) {
        if (quads.isEmpty()) return null;
        Identifier loc = null;
        for (BakedQuad q : quads) {
            Identifier l = q.sprite().atlasLocation();
            if (loc == null) loc = l;
            else if (!loc.equals(l)) return null;
        }
        Atlas at = atlas(loc);
        if (at == null) return null;
        boolean changed = false;
        for (BakedQuad q : quads) {
            TextureAtlasSprite sp = q.sprite();
            if (at.failed.contains(sp)) return null;
            if (at.done.contains(sp)) continue;
            if (!copy(sp, at)) {
                at.failed.add(sp);
                return null;
            }
            at.done.add(sp);
            changed = true;
        }
        if (changed) at.tex.upload();
        return at.id;
    }

    private static Atlas atlas(Identifier loc) {
        try {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(loc);
            int w = t.getTexture().getWidth(0), h = t.getTexture().getHeight(0);
            Atlas at = ATLASES.get(loc);
            if (at != null && at.w == w && at.h == h) return at;
            if (at != null) Minecraft.getInstance().getTextureManager().release(at.id);
            Identifier id = Identifier.fromNamespaceAndPath(
                    "arcanicvisuals", "grey/" + loc.getNamespace() + "/" + loc.getPath());
            DynamicTexture tex = new DynamicTexture(() -> "arcanicvisuals grey " + loc, w, h, true);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            at = new Atlas(id, tex, w, h);
            ATLASES.put(loc, at);
            return at;
        } catch (RuntimeException e) {
            System.err.println("[ArcanicVisuals] grey atlas " + loc + ": " + e);
            return null;
        }
    }

    private static boolean copy(TextureAtlasSprite sp, Atlas at) {
        SpriteContents c = sp.contents();
        Identifier name = c.name();
        Identifier file = Identifier.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(file);
        if (res.isEmpty()) return false;
        try (InputStream in = res.get().open();
                NativeImage img = NativeImage.read(in)) {
            NativeImage dst = at.tex.getPixels();
            if (dst == null) return false;
            int fw = c.width(), fh = c.height();
            // a resource pack texture of another size than the sprite: sample it nearest-neighbour
            float sx = img.getWidth() / (float) fw,
                    sy = Math.min(img.getHeight(), img.getWidth() * fh / Math.max(1, fw)) / (float) fh;
            float mean = mean(img, 0, 0, img.getWidth(), Math.round(fh * sy));
            for (int y = 0; y < fh; y++)
                for (int x = 0; x < fw; x++) {
                    int ax = sp.getX() + x, ay = sp.getY() + y;
                    if (ax < 0 || ay < 0 || ax >= at.w || ay >= at.h) continue;
                    int px = Math.min(img.getWidth() - 1, (int) (x * sx)),
                            py = Math.min(img.getHeight() - 1, (int) (y * sy));
                    dst.setPixel(ax, ay, grey(img.getPixel(px, py), mean));
                }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** A grey copy of a standalone texture (armour layers); the original when it can't be made. */
    public static Identifier single(Identifier tex) {
        Identifier have = SINGLE.get(tex);
        if (have != null) return have;
        if (FAILED.contains(tex)) return tex;
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (res.isEmpty()) {
            FAILED.add(tex);
            return tex;
        }
        try (InputStream in = res.get().open()) {
            NativeImage img = NativeImage.read(in);
            float mean = mean(img, 0, 0, img.getWidth(), img.getHeight());
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) img.setPixel(x, y, grey(img.getPixel(x, y), mean));
            Identifier id = Identifier.fromNamespaceAndPath(
                    "arcanicvisuals", "grey/" + tex.getNamespace() + "/" + tex.getPath());
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(id, new DynamicTexture(() -> "arcanicvisuals grey " + tex, img));
            SINGLE.put(tex, id);
            return id;
        } catch (IOException | RuntimeException e) {
            FAILED.add(tex);
            return tex;
        }
    }

    public static void reset() {
        for (Atlas at : ATLASES.values())
            Minecraft.getInstance().getTextureManager().release(at.id);
        for (Identifier id : SINGLE.values())
            Minecraft.getInstance().getTextureManager().release(id);
        ATLASES.clear();
        SINGLE.clear();
        FAILED.clear();
    }
}
