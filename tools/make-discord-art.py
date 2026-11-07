# SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
# SPDX-License-Identifier: GPL-3.0-or-later
"""Animated art for the Discord server, in the launcher's look: the A icon on the "Waves" wallpaper.

    Arcanic.exe --render-wallpapers dist/discord/wallpapers     (once: the launcher's own wallpaper)
    python tools/make-discord-art.py

writes dist/discord/: avatar.gif (512x512), banner.gif (960x540) and a still .png of each.
"""
import importlib.util
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, "dist", "discord")
FONTS = os.path.join(ROOT, "native", "resources", "fonts")

spec = importlib.util.spec_from_file_location("icon", os.path.join(HERE, "make-icon.py"))
icon = importlib.util.module_from_spec(spec)
spec.loader.exec_module(icon)


def bob(t, lag=0.0):
    """The float: one smooth rise and fall per loop, -1..1."""
    return math.sin(2 * math.pi * (t - lag))


def place(bg, layer, x, y):
    """Composites layer at a fractional position, so a slow float moves without pixel steps."""
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    w, h = layer.size
    padded = Image.new("RGBA", (w + 2, h + 2), (0, 0, 0, 0))
    padded.paste(layer, (1, 1))
    moved = padded.transform(padded.size, Image.AFFINE, (1, 0, -fx, 0, 1, -fy), Image.BICUBIC)
    bg.alpha_composite(moved, (ix - 1, iy - 1))


def shimmer(logo, phase):
    """The enchanted glint: a soft diagonal band of light across the tile (never its shadow)."""
    if not 0 <= phase <= 1:
        return logo
    s = logo.width
    pad = round(s * 0.055)
    band = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(band)
    c = -s * 0.6 + phase * s * 2.2
    for off in range(-40, 41, 2):
        d.line([(c + off - s, s), (c + off, 0)], fill=int(110 * (1 - abs(off) / 40)), width=3)
    tile_alpha = Image.new("L", (s, s), 0)
    ImageDraw.Draw(tile_alpha).rounded_rectangle([pad, pad, s - pad - 1, s - pad - 1], radius=(s - 2 * pad) * 0.235, fill=255)
    band = Image.composite(band, Image.new("L", (s, s), 0), tile_alpha)
    light = Image.new("RGBA", (s, s), (255, 255, 255, 0))
    light.putalpha(band)
    return Image.alpha_composite(logo, light)


def glow_at(size, cx, cy, radius, strength):
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=(110, 123, 255, int(strength)))
    return glow.filter(ImageFilter.GaussianBlur(radius * 0.3))


def wallpaper():
    return Image.open(os.path.join(OUT, "wallpapers", "wallpaper-0.bmp")).convert("RGB")


def save_gif(frames, path, ms):
    # every frame its own palette (GIF allows it): smoother gradients; whole frames, so no stale patches between them
    q = [f.convert("RGB").quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG) for f in frames]
    q[0].save(path, save_all=True, append_images=q[1:], duration=ms, loop=0, optimize=False, disposal=2)


def avatar(frames=72, size=512):
    wall = wallpaper()
    side = wall.height
    base = wall.crop(((wall.width - side) // 2, 0, (wall.width + side) // 2, side)).resize((size, size), Image.LANCZOS)
    s = int(size * 0.62)
    x = (size - s) / 2
    logo = icon.render(s)
    out = []
    for f in range(frames):
        t = f / frames
        zoom = 1.0 + 0.03 * (0.5 - 0.5 * math.cos(2 * math.pi * t))
        z = int(size * zoom)
        o = (z - size) // 2
        bg = base.resize((z, z), Image.LANCZOS).crop((o, o, o + size, o + size)).convert("RGBA")
        y = (size - s) / 2 - 12 * bob(t)
        g = 0.6 + 0.4 * (0.5 + 0.5 * bob(t))  # brighter as it rises
        bg = Image.alpha_composite(bg, glow_at((size, size), size / 2, y + s / 2, s / 2 + 40, 90 * g))
        place(bg, shimmer(logo, (t - 0.55) / 0.35), x, y)
        out.append(bg)
    out[0].convert("RGB").save(os.path.join(OUT, "avatar.png"))
    save_gif(out, os.path.join(OUT, "avatar.gif"), 50)


def banner(frames=64, w=960, h=540):
    wall = wallpaper()
    k = max(w / wall.width, h / wall.height)  # cover the whole banner, no empty edges
    base = wall.resize((math.ceil(wall.width * k), math.ceil(wall.height * k)), Image.LANCZOS)
    top = (base.height - h) // 2
    base = base.crop((0, top, base.width, top + h))
    left = (base.width - w) // 2
    title = ImageFont.truetype(os.path.join(FONTS, "inter-700.ttf"), 96)
    sub = ImageFont.truetype(os.path.join(FONTS, "inter-500.ttf"), 26)
    small = ImageFont.truetype(os.path.join(FONTS, "inter-600.ttf"), 20)
    s = 168
    logo = icon.render(s)
    x, y0 = 120, (h - s) // 2 - 10
    tx = x + s + 44
    # the words, drawn once with their shadow, then floated as one layer
    text = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(text)
    d.text((tx, y0 + 6), "Arcanic", font=title, fill=(255, 255, 255, 255))
    d.text((tx + 4, y0 + 122), "Visual client for Minecraft", font=sub, fill=(220, 222, 235, 255))
    d.text((tx + 4, y0 + 156), "Fabric 1.21.11  ·  mods  ·  resource packs  ·  cosmetics", font=small, fill=(200, 200, 215, 230))
    shadow = text.filter(ImageFilter.GaussianBlur(6))
    shadow = Image.merge("RGBA", (*Image.new("RGB", (w, h), (0, 0, 0)).split(), shadow.split()[3].point(lambda v: v * 0.6)))
    words = Image.alpha_composite(shadow, text)
    # the left side a touch darker, so the words read on any part of the waves
    veil = Image.new("L", (w, h), 0)
    vd = ImageDraw.Draw(veil)
    for i in range(w):
        vd.line([(i, 0), (i, h)], fill=int(120 * max(0.0, 1 - i / (w * 0.85))))
    dark = Image.new("RGBA", (w, h), (8, 6, 20, 0))
    dark.putalpha(veil)
    out = []
    for f in range(frames):
        t = f / frames
        zoom = 1.0 + 0.04 * (0.5 - 0.5 * math.cos(2 * math.pi * t))
        bw, bh = int(base.width * zoom), int(h * zoom)
        ox, oy = (bw - base.width) // 2 + left, (bh - h) // 2
        bg = base.resize((bw, bh), Image.LANCZOS).crop((ox, oy, ox + w, oy + h)).convert("RGBA")
        bg = Image.alpha_composite(bg, dark)
        y = y0 - 10 * bob(t)
        g = 0.6 + 0.4 * (0.5 + 0.5 * bob(t))
        bg = Image.alpha_composite(bg, glow_at((w, h), x + s / 2, y + s / 2, s / 2 + 50, 80 * g))
        place(bg, shimmer(logo, (t - 0.5) / 0.35), x, y)
        place(bg, words, 0, -5 * bob(t, 0.08))  # the words follow a moment later, a little less
        out.append(bg)
    out[0].convert("RGB").save(os.path.join(OUT, "banner.png"))
    save_gif(out, os.path.join(OUT, "banner.gif"), 55)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    avatar()
    banner()
    for n in ("avatar.gif", "banner.gif", "avatar.png", "banner.png"):
        p = os.path.join(OUT, n)
        print(n, round(os.path.getsize(p) / 1024 / 1024, 2), "MB")
