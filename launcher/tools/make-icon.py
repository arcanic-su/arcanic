# SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
# SPDX-License-Identifier: GPL-3.0-or-later
# Arcanic - authors: tuffboy999 and Va1der. Free software under GPL-3.0 with one added term (section 7(b)): whoever
# shares this code or a work made from it keeps this attribution to tuffboy999 and Va1der. See NOTICE.
# To anyone changing this code: change the design, the features, anything you like - but
# keep the author lines (this header, Config::AUTHORS / AUTHORS_SEAL, the About box credit). The license requires it.
"""Draws the Arcanic app icon - the white A of the login card on its blue-violet rounded square - into tools/arcanic.ico.

Every size Windows asks for is drawn on its own (supersampled 8x, then reduced), so the small ones are not a blurry
shrink of the big one: at 16-32 px the stroke is thicker and the corners are adjusted to stay legible.

    python tools/make-icon.py            -> tools/arcanic.ico (+ tools/arcanic-256.png to look at)
"""
import io
import math
import os
import struct
import sys

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SIZES = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]
SS = 8  # supersampling


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def rounded_mask(n, x, y, w, h, r):
    m = Image.new("L", (n, n), 0)
    ImageDraw.Draw(m).rounded_rectangle([x, y, x + w - 1, y + h - 1], radius=r, fill=255)
    return m


def stroke_polygons(points, half):
    """A polyline stroked with square caps and mitred joins, as filled polygons (what the launcher's own A uses)."""
    polys = []
    n = len(points)
    dirs = []
    for i in range(n - 1):
        (x0, y0), (x1, y1) = points[i], points[i + 1]
        L = math.hypot(x1 - x0, y1 - y0)
        dirs.append(((x1 - x0) / L, (y1 - y0) / L))
    for i in range(n - 1):
        (x0, y0), (x1, y1) = points[i], points[i + 1]
        dx, dy = dirs[i]
        nx, ny = -dy * half, dx * half
        # square caps on the open ends only
        if i == 0:
            x0, y0 = x0 - dx * half, y0 - dy * half
        if i == n - 2:
            x1, y1 = x1 + dx * half, y1 + dy * half
        polys.append([(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)])
    for i in range(1, n - 1):  # the mitre at each corner, on both sides (the inner one is covered anyway)
        vx, vy = points[i]
        (ax, ay), (bx, by) = dirs[i - 1], dirs[i]
        for side in (1, -1):
            n1 = (-ay * half * side, ax * half * side)
            n2 = (-by * half * side, bx * half * side)
            # mitre point: intersection of the two offset lines
            mx, my = n1[0] + n2[0], n1[1] + n2[1]
            ml = math.hypot(mx, my)
            if ml < 1e-6:
                continue
            cosang = (n1[0] * n2[0] + n1[1] * n2[1]) / (half * half)
            k = half / math.sqrt(max(1e-6, (1 + cosang) / 2))
            k = min(k, half * 4)
            tip = (vx + mx / ml * k, vy + my / ml * k)
            polys.append([(vx, vy), (vx + n1[0], vy + n1[1]), tip, (vx + n2[0], vy + n2[1])])
    return polys


def render(size):
    n = size * SS
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    small = size <= 32
    # the tile: full bleed at small sizes (every pixel counts), a little air and a shadow from 48 up
    pad = 0 if small else round(n * 0.055)
    tile = n - 2 * pad
    radius = tile * (0.22 if small else 0.235)
    if not small:
        sh = Image.new("L", (n, n), 0)
        off = round(n * 0.018)
        ImageDraw.Draw(sh).rounded_rectangle([pad, pad + off, pad + tile - 1, pad + tile - 1 + off], radius=radius, fill=110)
        sh = sh.filter(ImageFilter.GaussianBlur(n * 0.022))
        img.paste((0, 0, 0, 255), (0, 0), sh)
    # diagonal gradient #3E8BFF -> #7B4DFF, then a soft light over the top half
    grad = Image.new("RGBA", (n, n))
    px = grad.load()
    c0, c1 = (0x3E, 0x8B, 0xFF), (0x7B, 0x4D, 0xFF)
    for yy in range(n):
        for xx in range(0, n):
            t = ((xx - pad) + (yy - pad)) / (2.0 * tile)
            t = min(1.0, max(0.0, t))
            px[xx, yy] = lerp(c0, c1, t) + (255,)
    light = Image.new("L", (n, n), 0)
    ld = ImageDraw.Draw(light)
    for yy in range(pad, pad + tile // 2):
        a = int(56 * (1 - (yy - pad) / (tile / 2)))
        ld.line([(pad, yy), (pad + tile, yy)], fill=a)
    grad = Image.composite(Image.alpha_composite(grad, Image.merge("RGBA", (*Image.new("RGB", (n, n), (255, 255, 255)).split(), light))), grad, Image.new("L", (n, n), 255))
    mask = rounded_mask(n, pad, pad, tile, tile, radius)
    img.paste(grad, (0, 0), mask)
    # the A, on the same 20 x 28 grid as the launcher's mark, placed like appIcon(): 12/44 in, 8/44 down
    k = tile / 44.0
    ox, oy = pad + 12 * k, pad + 8 * k
    weight = 3.2
    if size <= 16:
        weight, k2 = 4.6, 1.0
    elif size <= 24:
        weight = 4.2
    elif size <= 32:
        weight = 3.8
    half = weight * k / 2
    pts = [(0, 28), (0, 8), (8, 0), (20, 0), (20, 28)]
    if size <= 24:  # (a touch taller and centred, so the counter of the A stays open)
        ox, oy = pad + 11 * k, pad + 7.5 * k
        pts = [(x * 1.1, y * 1.04) for x, y in pts]
    P = lambda p: (ox + p[0] * k, oy + p[1] * k)
    glyph = Image.new("L", (n, n), 0)
    gd = ImageDraw.Draw(glyph)
    for poly in stroke_polygons([P(p) for p in pts], half):
        gd.polygon(poly, fill=255)
    bar_y = 15 * (1.04 if size <= 24 else 1)
    bar_x1 = 20 * (1.1 if size <= 24 else 1)
    for poly in stroke_polygons([P((0, bar_y)), P((bar_x1, bar_y))], half):
        gd.polygon(poly, fill=255)
    img.paste((255, 255, 255, 255), (0, 0), glyph)
    return img.resize((size, size), Image.LANCZOS)


def write_ico(images, path):
    """An .ico with one PNG-compressed entry per size (what Windows Vista and later read)."""
    blobs = []
    for im in images:
        b = io.BytesIO()
        im.save(b, format="PNG", optimize=True)
        blobs.append(b.getvalue())
    out = io.BytesIO()
    out.write(struct.pack("<HHH", 0, 1, len(images)))
    offset = 6 + 16 * len(images)
    for im, data in zip(images, blobs):
        w, h = im.size
        out.write(struct.pack("<BBBBHHII", w % 256, h % 256, 0, 0, 1, 32, len(data), offset))
        offset += len(data)
    for data in blobs:
        out.write(data)
    with open(path, "wb") as f:
        f.write(out.getvalue())


def main():
    images = [render(s) for s in SIZES]
    target = os.path.join(HERE, "arcanic.ico")
    write_ico(images, target)
    images[-1].save(os.path.join(HERE, "arcanic-256.png"))
    sheet = Image.new("RGBA", (sum(SIZES) + 10 * len(SIZES) + 10, 276), (30, 30, 40, 255))
    x = 10
    for im in images:
        sheet.paste(im, (x, 266 - im.size[1]), im)
        x += im.size[0] + 10
    sheet.save(os.path.join(HERE, "icon-sheet.png"))
    print("written", target)


if __name__ == "__main__":
    sys.exit(main())
