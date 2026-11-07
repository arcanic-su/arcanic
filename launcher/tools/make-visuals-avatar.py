# SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
# SPDX-License-Identifier: GPL-3.0-or-later
"""The Arcanic Visuals avatar: the violet command-block cube with chrome edges, a soft glow and pixel sparks.

    python tools/make-visuals-avatar.py <cube.png|webp>

writes dist/discord/visuals-avatar.png (1024x1024) and visuals-avatar.gif (512x512, looped).
"""
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "dist", "discord")

# the cube in the source picture: an isometric hexagon, and the corner where the three faces meet
HEX = [(512, 0), (973, 230), (973, 796), (512, 1023), (50, 796), (50, 230)]
MID = (512, 460)

VIOLET = (123, 92, 255)
LILAC = (196, 178, 255)


def cube_layer(src):
    """The cube alone on transparency, cut along its own outline (the source's black stays outside)."""
    mask = Image.new("L", src.size, 0)
    ImageDraw.Draw(mask).polygon(HEX, fill=255)
    cube = src.convert("RGBA")
    cube.putalpha(mask)
    return cube, mask


def chrome(size, width):
    """The metal: every edge of the cube drawn as a bright line that runs from white through lilac to deep violet,
    the way a polished rim catches a light from the top left."""
    w, h = size
    lines = Image.new("L", size, 0)
    d = ImageDraw.Draw(lines)
    d.line(HEX + [HEX[0]], fill=255, width=width, joint="curve")
    for corner in (HEX[1], HEX[3], HEX[5]):
        d.line([MID, corner], fill=255, width=max(2, width * 2 // 3))
    # the colour of the metal: light from the top left, a dark band across the middle, a second light low down
    ramp = Image.new("RGB", size)
    rd = ImageDraw.Draw(ramp)
    stops = [(0.0, (255, 255, 255)), (0.22, (214, 204, 255)), (0.40, (58, 40, 128)), (0.50, (36, 24, 86)), (0.60, (240, 236, 255)), (0.78, (150, 126, 255)), (1.0, (64, 44, 150))]
    for y in range(h):
        t = y / (h - 1)
        for (a, ca), (b, cb) in zip(stops, stops[1:]):
            if a <= t <= b:
                k = (t - a) / (b - a)
                rd.line([(0, y), (w, y)], fill=tuple(int(ca[i] + (cb[i] - ca[i]) * k) for i in range(3)))
                break
    out = ramp.convert("RGBA")
    out.putalpha(lines)
    return out


def shine(size, mask, phase):
    """A soft band of light that slides across the faces once a loop (only on the cube)."""
    w, h = size
    band = Image.new("L", size, 0)
    if 0 <= phase <= 1:
        d = ImageDraw.Draw(band)
        c = -w * 0.4 + phase * w * 1.8
        for off in range(-90, 91, 6):
            d.line([(c + off - h * 0.6, h), (c + off + h * 0.6, 0)], fill=int(70 * (1 - abs(off) / 90) ** 1.5), width=7)
        band = band.filter(ImageFilter.GaussianBlur(10))
        band = ImageChops.multiply(band, mask)
    light = Image.new("RGBA", size, (255, 255, 255, 0))
    light.putalpha(band)
    return light


def background(n):
    """Deep violet night: a light behind the cube that fades to almost black at the edges."""
    bg = Image.new("RGB", (n, n), (9, 7, 20))
    glow = Image.new("L", (n, n), 0)
    ImageDraw.Draw(glow).ellipse([n * 0.12, n * 0.10, n * 0.88, n * 0.86], fill=255)
    glow = glow.filter(ImageFilter.GaussianBlur(n * 0.16))
    tint = Image.new("RGB", (n, n), (52, 30, 120))
    return Image.composite(tint, bg, glow).convert("RGBA")


class Sparks:
    """Square pixel sparks, like Minecraft's enchanting particles, rising slowly around the cube and looping."""

    def __init__(self, n, count=30, seed=11):
        rnd = random.Random(seed)
        self.n = n
        self.items = []
        for _ in range(count):
            a = rnd.uniform(0, 2 * math.pi)
            r = rnd.uniform(0.37, 0.47) * n
            self.items.append({
                "x": n / 2 + math.cos(a) * r, "y0": n / 2 + math.sin(a) * r * 0.9,
                "size": rnd.choice([1, 1, 1, 1.5, 2]) * n / 190, "phase": rnd.random(),
                "rise": rnd.uniform(0.05, 0.11) * n, "col": rnd.choice([LILAC, (255, 255, 255), (150, 120, 255)]),
            })

    def draw(self, t):
        layer = Image.new("RGBA", (self.n, self.n), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        for s in self.items:
            k = (t + s["phase"]) % 1.0
            a = math.sin(k * math.pi)  # fades in and out as it rises
            y = s["y0"] - k * s["rise"]
            q = s["size"]
            d.rectangle([s["x"] - q, y - q, s["x"] + q, y + q], fill=s["col"] + (int(230 * a),))
        glow = layer.filter(ImageFilter.GaussianBlur(self.n / 160))
        return Image.alpha_composite(glow, layer)


def frame(cube, mask, rim, sparks, n, t, still=False):
    """One picture of the loop: t in [0, 1)."""
    s = 0.66  # the cube's share of the avatar (Discord crops it to a circle)
    side = int(n * s)
    lift = 0 if still else math.sin(2 * math.pi * t) * n * 0.012
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * t)
    canvas = background(n)
    x = (n - side) // 2
    y = (n - side) / 2 - n * 0.01 - lift
    # the glow under and behind the cube
    halo = Image.new("L", (n, n), 0)
    small = mask.resize((side, side), Image.LANCZOS)
    halo.paste(small, (x, int(y)))
    halo = halo.filter(ImageFilter.GaussianBlur(n * 0.06)).point(lambda v: int(v * (0.62 + 0.25 * pulse)))
    tint = Image.new("RGBA", (n, n), VIOLET + (0,))
    tint.putalpha(halo)
    canvas = Image.alpha_composite(canvas, tint)
    # a soft shadow on the ground, smaller as the cube rises
    shadow = Image.new("L", (n, n), 0)
    sw = side * (0.62 - 0.03 * (lift / (n * 0.012) if lift else 0))
    ImageDraw.Draw(shadow).ellipse([n / 2 - sw / 2, n * 0.86, n / 2 + sw / 2, n * 0.91], fill=150)
    shadow = shadow.filter(ImageFilter.GaussianBlur(n * 0.02))
    canvas.paste((0, 0, 0, 255), (0, 0), shadow)
    canvas = Image.alpha_composite(canvas, sparks.draw(t + 0.5))  # the sparks behind the cube
    # the cube, its shine and its chrome rim, built at the source's size and scaled down once
    big = cube.copy()
    big = Image.alpha_composite(big, shine(cube.size, mask, -1 if still else (t - 0.15) / 0.5))
    big = Image.alpha_composite(big, rim)
    sharp = big.resize((side, side), Image.LANCZOS)
    # the place the moving cube lands on: whole pixels, then the fraction by an affine move
    iy = math.floor(y)
    fy = y - iy
    pad = Image.new("RGBA", (side, side + 2), (0, 0, 0, 0))
    pad.paste(sharp, (0, 1))
    pad = pad.transform(pad.size, Image.AFFINE, (1, 0, 0, 0, 1, -fy), Image.BICUBIC)
    canvas.alpha_composite(pad, (x, iy - 1))
    # a thin bright bloom along the rim
    bloom = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    rim_small = rim.resize((side, side), Image.LANCZOS)
    bloom.alpha_composite(rim_small, (x, iy))
    bloom = bloom.filter(ImageFilter.GaussianBlur(n * 0.008))
    canvas = Image.alpha_composite(canvas, bloom)
    canvas = Image.alpha_composite(canvas, sparks.draw(t))  # and in front of it
    return canvas.convert("RGB")


def save_gif(frames, path, ms):
    q = [f.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG) for f in frames]
    q[0].save(path, save_all=True, append_images=q[1:], duration=ms, loop=0, optimize=False, disposal=2)


def main():
    src = Image.open(sys.argv[1]).convert("RGB")
    if src.size != (1024, 1024):
        src = src.resize((1024, 1024), Image.LANCZOS)
    cube, mask = cube_layer(src)
    rim = chrome(src.size, 17)
    os.makedirs(OUT, exist_ok=True)
    frame(cube, mask, rim, Sparks(1024), 1024, 0.25, still=True).save(os.path.join(OUT, "visuals-avatar.png"))
    sparks = Sparks(512)
    frames = [frame(cube, mask, rim, sparks, 512, i / 64) for i in range(64)]
    save_gif(frames, os.path.join(OUT, "visuals-avatar.gif"), 50)
    for name in ("visuals-avatar.png", "visuals-avatar.gif"):
        p = os.path.join(OUT, name)
        print(name, round(os.path.getsize(p) / 1024 / 1024, 2), "MB")


if __name__ == "__main__":
    main()
