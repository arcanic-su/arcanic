"""
Arcanic wearables generator: models are built from cubes in code, their faces painted at a few texels per model pixel,
packed into one texture, written as the Bedrock geometry the mod reads (assets/arcanicvisuals/wear/cosmetic_N.json),
and rendered to the 128x128 icon the wardrobe shows.

Coordinates while authoring are model pixels as the mod draws them ("loader space"): x to the player's RIGHT, y up,
z to the player's BACK; the player faces -z. Feet at y=0; the body is x -4..4, y 12..24, z -2..2 (its back at z=2);
the head x -4..4, y 24..32, z -4..4. On export x is mirrored into Bedrock's convention.

Faces are named by where they look: front (-z), back (+z), right (+x), left (-x), top, bottom. Each face's picture is
drawn as seen from outside, upright and unmirrored.
"""

import json
import math
import os
import random

from PIL import Image, ImageDraw

_rect = ImageDraw.ImageDraw.rectangle


def _safe_rect(self, xy, *a, **k):
    """Rectangles given corners the wrong way round (tiny faces) are drawn instead of failing."""
    if len(xy) == 4:
        x0, y0, x1, y1 = xy
        xy = (min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))
    return _rect(self, xy, *a, **k)


ImageDraw.ImageDraw.rectangle = _safe_rect

# ------------------------------------------------------------------------------------------------ colours


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mul(c, k):
    return (max(0, min(255, int(c[0] * k))), max(0, min(255, int(c[1] * k))), max(0, min(255, int(c[2] * k))), c[3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def C(c):
    return rgb(c) if isinstance(c, str) else c


# ------------------------------------------------------------------------------------------------ a 3x5 pixel font

_F = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110",
    "E": "111100110100111", "F": "111100110100100", "G": "011100101101011", "H": "101101111101101",
    "I": "111010010010111", "J": "001001001101010", "K": "101101110101101", "L": "100100100100111",
    "M": "101111111101101", "N": "110101101101101", "O": "010101101101010", "P": "110101110100100",
    "Q": "010101101110011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101",
    "Y": "101101010010010", "Z": "111001010100111", "0": "111101101101111", "1": "010110010010111",
    "2": "110001010100111", "3": "110001010001110", "4": "101101111001001", "5": "111100110001110",
    "6": "011100111101111", "7": "111001010010010", "8": "111101111101111", "9": "111101111001110",
    "-": "000000111000000", ".": "000000000000010", "'": "010010000000000", '"': "101101000000000",
    " ": "000000000000000", "!": "010010010000010", "/": "001001010100100", "&": "010101010101011",
    "+": "000010111010000", ":": "000010000010000",
}


def text_width(s, scale=1, gap=1):
    return max(0, len(s) * (3 + gap) - gap) * scale


def text(img, s, x, y, c, scale=1, gap=1, center=False, shadow=None):
    """Writes s in the 3x5 font; (x, y) its top left, or its middle top when center."""
    if center:
        x = x - text_width(s, scale, gap) // 2
    px = img.load()
    W, H = img.size
    for pass_ in ([shadow, c] if shadow else [c]):
        dx = scale if pass_ is shadow and shadow else 0
        for i, ch in enumerate(s.upper()):
            g = _F.get(ch, _F[" "])
            for r in range(5):
                for k in range(3):
                    if g[r * 3 + k] == "1":
                        for sy in range(scale):
                            for sx in range(scale):
                                X = x + i * (3 + gap) * scale + k * scale + sx + dx
                                Y = y + r * scale + sy + dx
                                if 0 <= X < W and 0 <= Y < H:
                                    px[X, Y] = C(pass_)


def sprite(img, rows, x, y, palette, scale=1):
    """Pixel art: rows of characters, each a key of palette ('.' or ' ' clear)."""
    px = img.load()
    W, H = img.size
    for r, row in enumerate(rows):
        for k, ch in enumerate(row):
            if ch in ". ":
                continue
            for sy in range(scale):
                for sx in range(scale):
                    X, Y = x + k * scale + sx, y + r * scale + sy
                    if 0 <= X < W and 0 <= Y < H:
                        px[X, Y] = C(palette[ch])


def sprite_size(rows, scale=1):
    return max(len(r) for r in rows) * scale, len(rows) * scale


# ------------------------------------------------------------------------------------------------ painters
# A painter is f(face, w, h, D, rnd) -> RGBA image of w x h texels.


def canvas(w, h, c=(0, 0, 0, 0)):
    return Image.new("RGBA", (max(1, w), max(1, h)), C(c))


def fill(c, noise=0.035, grad=0.10, edge=0.16, edge_w=None):
    """A flat colour as the mod's models paint it: a faint grain, a little darker towards the bottom and the edges."""
    c = C(c)

    def p(face, w, h, D, rnd):
        img = canvas(w, h)
        px = img.load()
        ew = edge_w if edge_w is not None else max(1, D // 3)
        for y in range(h):
            g = 1 - grad * (y / max(1, h - 1)) if face not in ("top", "bottom") else 1
            for x in range(w):
                k = g * (1 + (rnd.random() * 2 - 1) * noise)
                if x < ew or y < ew or x >= w - ew or y >= h - ew:
                    k *= 1 - edge
                px[x, y] = mul(c, k)
        return img

    return p


def mc(c, c2=None, tones=(.84, .92, 1.0, 1.0, 1.07), edge=.78, seed_tone=None):
    """Minecraft's own way: every texel one of a few tones of the colour (chunky, not a fine grain), the outer ring
    of texels darker; with c2 the colour runs from c at the top to c2 at the bottom."""
    c = C(c)
    c2 = C(c2) if c2 is not None else None

    def p(face, w, h, D, rnd):
        img = canvas(w, h)
        px = img.load()
        for y in range(h):
            base = c if c2 is None or face in ("top", "bottom") else mix(c, c2, (y / max(1, h - 1)) ** 1.1)
            for x in range(w):
                k = rnd.choice(tones)
                if edge and (x == 0 or y == 0 or x == w - 1 or y == h - 1) and w > 2 and h > 2:
                    k *= edge
                px[x, y] = mul(base, k)
        return img

    return p


def ramp(cols, tones=(.9, .96, 1.0, 1.0, 1.04), top=None, edge=.86, power=1.0):
    """Hand-painted like the mod's first cosmetics: the colour runs down a ramp of a few colours (top to bottom),
    dithered where one gives way to the next, each texel a touch lighter or darker, the side columns a little
    darker. top: the colour of the top face (the ramp's first by default)."""
    cs = [C(c) for c in cols]

    def p(face, w, h, D, rnd):
        img = canvas(w, h)
        px = img.load()
        n = len(cs)
        for y in range(h):
            for x in range(w):
                if face == "top":
                    c = C(top) if top else cs[0]
                elif face == "bottom":
                    c = cs[-1]
                elif n == 1:
                    c = cs[0]
                else:
                    t = ((y + .5) / h) ** power * (n - 1)
                    i = min(n - 2, int(t))
                    f = t - i
                    c = cs[i + 1] if rnd.random() < f * f * (3 - 2 * f) else cs[i]
                k = rnd.choice(tones)
                if edge and (x == 0 or x == w - 1) and w > 3:
                    k *= edge
                px[x, y] = mul(c, k)
        return img

    p.glow = edge is None  # a ramp with no dark edge is a light: it is not shaded
    return p


def faces(default, **by):
    """A different painter for some faces: front=..., back=..., right=..., left=..., top=..., bottom=..., sides=..."""

    def p(face, w, h, D, rnd):
        f = by.get(face)
        if f is None and face in ("front", "back", "right", "left"):
            f = by.get("sides")
        return (f or default)(face, w, h, D, rnd)

    return p


def over(base, *layers):
    """base painted first, then each layer f(img, face, D, rnd) drawn on top."""

    def p(face, w, h, D, rnd):
        img = base(face, w, h, D, rnd)
        for l in layers:
            r = l(img, face, D, rnd)
            if isinstance(r, Image.Image):
                img = r
        return img

    p.glow = getattr(base, "glow", False)
    return p


def shade_face(img, side, D):
    """Light as a pixel artist paints it in: each side lit from above - a bright rim along its top edge, darker going
    down, its lower corners in shadow - lights turning warm and shadows cool and deeper (hue shifting, not just
    darker); tops lit with a darker edge, bottoms in deep cool shadow."""
    w, h = img.size
    px = img.load()
    rim = max(1, D // 2)
    warm, cool = (255, 236, 205, 255), (34, 30, 70, 255)
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if not c[3]:
                continue
            if side == "bottom":
                k, sh = .66, .22
            elif side == "top":
                edge = not (0 < x < w - 1 and 0 < y < h - 1)
                k, sh = (.86, .08) if edge else (1.08, -.06)
            else:
                t = y / max(1, h - 1)
                k = 1.12 - .32 * t ** 1.2
                sh = -.05 + .2 * t ** 1.5
                if y < rim:
                    k += .1
                    sh -= .08
                if t > .55 and (x < rim or x >= w - rim):
                    k *= .86
                    sh += .06
            c = mul(c, k)
            c = mix(c, cool, sh) if sh > 0 else mix(c, warm, -sh)
            px[x, y] = (c[0], c[1], c[2], px[x, y][3])
    return img


def only(faces_, layer):
    """A layer drawn only on these faces."""
    fs = set(faces_.split()) if isinstance(faces_, str) else set(faces_)

    def l(img, face, D, rnd):
        if face in fs:
            return layer(img, face, D, rnd)

    return l


def L_stitch(c, inset=None, dash=2, gap=1):
    def l(img, face, D, rnd):
        w, h = img.size
        i = inset if inset is not None else max(1, D // 2)
        d = ImageDraw.Draw(img)
        col = C(c)
        for x in range(i, w - i):
            if (x // 1) % (dash + gap) < dash:
                d.point((x, i), col)
                d.point((x, h - 1 - i), col)
        for y in range(i, h - i):
            if y % (dash + gap) < dash:
                d.point((i, y), col)
                d.point((w - 1 - i, y), col)

    return l


def L_frame(c, t=1):
    def l(img, face, D, rnd):
        w, h = img.size
        d = ImageDraw.Draw(img)
        for k in range(t):
            d.rectangle((k, k, w - 1 - k, h - 1 - k), outline=C(c))

    return l


def L_hstripes(cols, widths, start=0, vertical=False):
    """Bands across (or down) the face: cols[i] for widths[i] texels, from start."""

    def l(img, face, D, rnd):
        w, h = img.size
        d = ImageDraw.Draw(img)
        pos = start
        for c, wd in zip(cols, widths):
            if c is not None:
                if vertical:
                    d.rectangle((pos, 0, pos + wd - 1, h - 1), fill=C(c))
                else:
                    d.rectangle((0, pos, w - 1, pos + wd - 1), fill=C(c))
            pos += wd

    return l


def L_diag(c, period, width, phase=0):
    def l(img, face, D, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                if (x + y + phase) % period < width:
                    px[x, y] = mul(C(c), 1 + (rnd.random() - .5) * .06)

    return l


def L_tile(tile_fn, tw, th, ox=0, oy=0, stagger=True):
    """Repeats a small picture tile_fn(img, x, y) over the face, every other row shifted half a tile."""

    def l(img, face, D, rnd):
        w, h = img.size
        r = 0
        for y in range(oy - th, h + th, th):
            sx = (tw // 2) if (stagger and r % 2) else 0
            for x in range(ox - tw + sx, w + tw, tw):
                tile_fn(img, x, y)
            r += 1

    return l


def L_noise_blobs(cols, n, rmin, rmax, seed=0):
    """Camouflage: soft blobs of cols scattered over the face."""

    def l(img, face, D, rnd):
        w, h = img.size
        rr = random.Random(seed + hash(face) % 1000)
        d = ImageDraw.Draw(img)
        for _ in range(int(n * w * h / 400) + 2):
            c = C(rr.choice(cols))
            cx, cy = rr.randrange(-2, w + 2), rr.randrange(-2, h + 2)
            r1, r2 = rr.randint(rmin, rmax), rr.randint(rmin, rmax)
            d.ellipse((cx - r1, cy - r2, cx + r1, cy + r2), fill=c)

    return l


def L_draw(fn):
    """Free drawing: fn(ImageDraw, w, h, D, face, img)."""

    def l(img, face, D, rnd):
        fn(ImageDraw.Draw(img), img.size[0], img.size[1], D, face, img)

    return l


def L_shade(top=0.0, bottom=0.18):
    """Darkens towards the bottom (for big faces under the light)."""

    def l(img, face, D, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            k = 1 + top - (top + bottom) * y / max(1, h - 1)
            for x in range(w):
                c = px[x, y]
                if c[3]:
                    px[x, y] = mul(c, k)

    return l


def picture(fn):
    """A painter that draws everything itself: fn(img, face, w, h, D, rnd) on a clear canvas."""

    def p(face, w, h, D, rnd):
        img = canvas(w, h)
        fn(img, face, w, h, D, rnd)
        return img

    return p


# ------------------------------------------------------------------------------------------------ geometry


class Cube:
    def __init__(self, origin, size, paint, rot=None, pivot=None, only=None, inflate=0.0):
        self.origin, self.size, self.paint = list(origin), list(size), paint
        self.rot = rot  # loader-space degrees (x, y, z), about pivot
        self.pivot = pivot
        self.only = only  # face names to keep, or None for all with area
        self.inflate = inflate


class Bone:
    def __init__(self, name, pivot=(0, 0, 0), rot=None, parent=None):
        self.name, self.pivot, self.rot, self.parent = name, list(pivot), rot, parent
        self.cubes = []

    def add(self, *cubes):
        self.cubes.extend(cubes)
        return self


def box(o, s, paint, **kw):
    return Cube(o, s, paint, **kw)


def plane_z(x, y, z, w, h, paint, **kw):
    """A flat picture facing back/front (one face, seen from both sides), x..x+w, y..y+h at depth z."""
    return Cube((x, y, z), (w, h, 0), paint, only=("back",), **kw)


def plane_x(x, y, z, d, h, paint, **kw):
    """A flat picture facing right/left at x, z..z+d."""
    return Cube((x, y, z), (0, h, d), paint, only=("right",), **kw)


def plane_y(x, y, z, w, d, paint, **kw):
    """A flat picture lying level at height y."""
    return Cube((x, y, z), (w, 0, d), paint, only=("top",), **kw)


class Model:
    def __init__(self, n, name, type_, D=4, view="back", cat=None, scale=1.0, y=None, x=0.0, z=0.0):
        self.n, self.name, self.type, self.D, self.view = n, name, type_, D, view
        self.bones = []
        self.anims = {}
        self.scale, self.x, self.z = scale, x, z
        self.pos = 2 if type_ == "hat" else 1
        self.cat = cat if cat is not None else (2 if type_ == "pet" else 1)
        self.y = y if y is not None else (-1.5 if type_ == "hat" else -1.2)

    def bone(self, name, pivot=(0, 0, 0), rot=None, parent=None):
        b = Bone(name, pivot, rot, parent)
        self.bones.append(b)
        return b

    def anim(self, bone, channel, fn, length, steps=24):
        """Keyframes sampled from fn(t in 0..1) -> (x, y, z), one cycle every length seconds, looping.

        The mod plays one animation with one length for every bone, so all tracks share the loop set by the first:
        a track with a shorter cycle repeats a whole number of times in it (a track that stopped early would stand
        still until the loop came round, then jump)."""
        a = self.anims.setdefault("idle", {"loop": True, "animation_length": length, "bones": {}})
        L = a["animation_length"]
        cycles = max(1, round(L / length))
        steps = max(steps, 16 * cycles)
        keys = {}
        for i in range(steps + 1):
            t = i / steps
            v = fn((t * cycles) % 1.0)
            keys[f"{t * L:.4f}"] = [round(v[0], 4), round(v[1], 4), round(v[2], 4)]
        a["bones"].setdefault(bone, {})[channel] = keys


# loader face order and the json names for our sides
SIDES = ["left", "right", "bottom", "top", "front", "back"]
JSON_FACE = {"left": "west", "right": "east", "bottom": "down", "top": "up", "front": "north", "back": "south"}


def face_dims(size, side):
    sx, sy, sz = size
    return {"front": (sx, sy), "back": (sx, sy), "right": (sz, sy), "left": (sz, sy), "top": (sx, sz),
            "bottom": (sx, sz)}[side]


# ------------------------------------------------------------------------------------------------ texture packing


def build(model, out_dir_geo, out_dir_tex):
    """Paints, packs and writes the model's json and texture; returns its quads for the icon."""
    D = model.D
    rnd = random.Random(model.n * 7919)
    pieces = []  # (cube, side, image)
    for b in model.bones:
        for c in b.cubes:
            for side in SIDES:
                if c.only is not None and side not in c.only:
                    continue
                fw, fh = face_dims(c.size, side)
                if c.only is None and (fw <= 0 or fh <= 0):
                    continue
                tw, th = max(1, round(max(fw, 0.25) * D)), max(1, round(max(fh, 0.25) * D))
                img = c.paint(side, tw, th, D, rnd)
                if getattr(model, "shade", False) and min(c.size) > 0 and not getattr(c.paint, "glow", False):
                    img = shade_face(img, side, D)
                pieces.append((c, side, img))
    # shelf packing with a 1-texel gutter of the edge colours
    pad = 1
    order = sorted(range(len(pieces)), key=lambda i: -pieces[i][2].size[1])
    total = sum((p[2].size[0] + 2 * pad) * (p[2].size[1] + 2 * pad) for p in pieces)
    W = 64
    while W * W < total * 1.25 or W < max(p[2].size[0] + 2 * pad for p in pieces):
        W *= 2
    x = y = shelf = 0
    place = {}
    for i in order:
        w, h = pieces[i][2].size
        if x + w + 2 * pad > W:
            x, y, shelf = 0, y + shelf, 0
        place[i] = (x + pad, y + pad)
        x += w + 2 * pad
        shelf = max(shelf, h + 2 * pad)
    H = 16
    while H < y + shelf:
        H *= 2
    atlas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for i, (c, side, img) in enumerate(pieces):
        px, py = place[i]
        w, h = img.size
        # gutter: the edges stretched out by one texel
        ext = img.resize((w + 2, h + 2), Image.NEAREST)
        ext.paste(img, (1, 1))
        atlas.paste(ext, (px - 1, py - 1))
        atlas.paste(img, (px, py))
    # json
    uvs = {}
    for i, (c, side, img) in enumerate(pieces):
        px, py = place[i]
        w, h = img.size
        uvs.setdefault(id(c), {})[JSON_FACE[side]] = {"uv": [px / D, py / D], "uv_size": [w / D, h / D]}
    bones_json = []
    for b in model.bones:
        bj = {"name": b.name, "pivot": [-b.pivot[0], b.pivot[1], b.pivot[2]]}
        if b.parent:
            bj["parent"] = b.parent
        if b.rot:
            bj["rotation"] = [-b.rot[0], -b.rot[1], b.rot[2]]
        cubes = []
        for c in b.cubes:
            ox, oy, oz = c.origin
            sx, sy, sz = c.size
            cj = {"origin": [r4(-(ox + sx)), r4(oy), r4(oz)], "size": [r4(sx), r4(sy), r4(sz)],
                  "uv": uvs.get(id(c), {})}
            if c.inflate:
                cj["inflate"] = c.inflate
            if c.rot:
                pv = c.pivot or [ox + sx / 2, oy + sy / 2, oz + sz / 2]
                cj["pivot"] = [r4(-pv[0]), r4(pv[1]), r4(pv[2])]
                cj["rotation"] = [-c.rot[0], -c.rot[1], c.rot[2]]
            cubes.append(cj)
        if cubes:
            bj["cubes"] = cubes
        bones_json.append(bj)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.arcanic_" + str(model.n), "texture_width": W / D,
                        "texture_height": H / D, "visible_bounds_width": 3, "visible_bounds_height": 3,
                        "visible_bounds_offset": [0, 1, 0]},
        "bones": bones_json}]}
    doc = {"model": geo}
    if model.anims:
        anims = {}
        for name, a in model.anims.items():
            bones = {}
            for bn, chans in a["bones"].items():
                bones[bn] = {}
                for ch, keys in chans.items():
                    if ch == "rotation":
                        bones[bn][ch] = {t: [-v[0], -v[1], v[2]] for t, v in keys.items()}
                    elif ch == "position":
                        bones[bn][ch] = {t: [-v[0], v[1], v[2]] for t, v in keys.items()}
                    else:
                        bones[bn][ch] = keys
            anims[name] = {"loop": True, "animation_length": a["animation_length"], "bones": bones}
        doc["animation"] = {"format_version": "1.8.0", "animations": anims}
    with open(os.path.join(out_dir_geo, f"cosmetic_{model.n}.json"), "w", encoding="utf-8") as f:
        json.dump(doc, f, separators=(",", ":"))
    atlas.save(os.path.join(out_dir_tex, f"cosmetic_{model.n}.png"))
    return quads(model, pieces, place, atlas)


def r4(v):
    return round(v, 4)


# ------------------------------------------------------------------------------------------------ posing (as the mod)


def mat_id():
    return [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def T(x, y, z):
    m = mat_id()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def Rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def Ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def Rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def rotm(rot, pivot):
    """The mod's turn about a pivot: translate, Z, Y, X, translate back (loader-space radians from degrees)."""
    rx, ry, rz = (math.radians(v) for v in rot)
    m = T(*pivot)
    if rz:
        m = mat_mul(m, Rz(rz))
    if ry:
        m = mat_mul(m, Ry(ry))
    if rx:
        m = mat_mul(m, Rx(rx))
    return mat_mul(m, T(-pivot[0], -pivot[1], -pivot[2]))


def corners(c, side):
    ox, oy, oz = c.origin
    sx, sy, sz = c.size
    i = c.inflate
    x0, y0, z0, x1, y1, z1 = ox - i, oy - i, oz - i, ox + sx + i, oy + sy + i, oz + sz + i
    # the four corners in the mod's order, each with (u, v) in 0..1 of the face picture
    return {
        "left": [(x0, y1, z0, 0, 0), (x0, y1, z1, 1, 0), (x0, y0, z1, 1, 1), (x0, y0, z0, 0, 1)],
        "right": [(x1, y1, z1, 0, 0), (x1, y1, z0, 1, 0), (x1, y0, z0, 1, 1), (x1, y0, z1, 0, 1)],
        "bottom": [(x1, y0, z0, 0, 0), (x1, y0, z1, 0, 1), (x0, y0, z1, 1, 1), (x0, y0, z0, 1, 0)],
        "top": [(x0, y1, z0, 0, 0), (x0, y1, z1, 0, 1), (x1, y1, z1, 1, 1), (x1, y1, z0, 1, 0)],
        "front": [(x1, y1, z0, 0, 0), (x0, y1, z0, 1, 0), (x0, y0, z0, 1, 1), (x1, y0, z0, 0, 1)],
        "back": [(x0, y1, z1, 0, 0), (x1, y1, z1, 1, 0), (x1, y0, z1, 1, 1), (x0, y0, z1, 0, 1)],
    }[side]


def quads(model, pieces, place, atlas):
    """The model's faces posed at rest: (four (x, y, z), four (u, v) in atlas texels, normal) each."""
    mats = {}
    byname = {b.name: b for b in model.bones}

    def m_of(b):
        if b.name in mats:
            return mats[b.name]
        parent = m_of(byname[b.parent]) if b.parent and b.parent in byname else mat_id()
        m = parent
        if b.rot:
            m = mat_mul(m, rotm(b.rot, b.pivot))
        mats[b.name] = m
        return m

    owner = {}
    for b in model.bones:
        for c in b.cubes:
            owner[id(c)] = b
    out = []
    normals = {"left": (-1, 0, 0), "right": (1, 0, 0), "bottom": (0, -1, 0), "top": (0, 1, 0), "front": (0, 0, -1),
               "back": (0, 0, 1)}
    for i, (c, side, img) in enumerate(pieces):
        m = m_of(owner[id(c)])
        if c.rot:
            pv = c.pivot or [c.origin[k] + c.size[k] / 2 for k in range(3)]
            m = mat_mul(m, rotm(c.rot, pv))
        px, py = place[i]
        w, h = img.size
        pts, uv = [], []
        for (x, y, z, u, v) in corners(c, side):
            pts.append(apply(m, (x, y, z)))
            uv.append((px + u * w, py + v * h))
        n0 = apply(m, normals[side])
        o0 = apply(m, (0, 0, 0))
        n = (n0[0] - o0[0], n0[1] - o0[1], n0[2] - o0[2])
        out.append((pts, uv, n))
    return out, atlas


# ------------------------------------------------------------------------------------------------ the icon

VIEWS = {  # (yaw, pitch) degrees of the camera round the model; yaw 0 looks at the player's back
    "back": (-30, 18),
    "front": (180 + 32, 16),
    "side": (180 + 60, 14),
    "wings": (-14, 24),
    "pet": (180 + 34, 14),
    "right": (128, 14),
    "pet_r": (180 - 34, 14),
}


def icon(qs_atlas, path, view="back", size=128, ss=3):
    qs, atlas = qs_atlas
    apx = atlas.load()
    AW, AH = atlas.size
    yaw, pitch = VIEWS.get(view, VIEWS["back"])
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def cam(p):
        # turn round y (yaw), then tilt (pitch): x right, y up, z towards the viewer
        x, y, z = p
        x1 = x * cy - z * sy
        z1 = x * sy + z * cy
        y2 = y * cp - z1 * sp
        z2 = y * sp + z1 * cp
        return x1, y2, z2

    tq = []
    xs, ys = [], []
    for pts, uv, n in qs:
        cpts = [cam(p) for p in pts]
        nn = cam(n)
        tq.append((cpts, uv, nn))
        for p in cpts:
            xs.append(p[0])
            ys.append(p[1])
    if not xs:
        return
    S = size * ss
    span = max(max(xs) - min(xs), max(ys) - min(ys), 1e-3)
    k = S * 0.86 / span
    mx, my = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    zbuf = [[-1e9] * S for _ in range(S)]
    col = [[None] * S for _ in range(S)]
    light = norm((-0.45, 0.75, 0.55))
    for cpts, uv, nn in tq:
        n = norm(nn)
        d = n[0] * light[0] + n[1] * light[1] + n[2] * light[2]
        bright = 0.58 + 0.42 * abs(d) if True else 0
        if n[1] > 0.6:
            bright = max(bright, 0.98)
        scr = [((p[0] - mx) * k + S / 2, S / 2 - (p[1] - my) * k, p[2]) for p in cpts]
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster(scr, uv, tri, zbuf, col, apx, AW, AH, bright, S)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ipx = img.load()
    for y in range(S):
        row = col[y]
        for x in range(S):
            if row[x] is not None:
                ipx[x, y] = row[x]
    # premultiplied downscale
    small = img.resize((size, size), Image.LANCZOS)
    small.save(path)


def norm(v):
    l = math.sqrt(v[0] ** 2 + v[1] ** 2 + v[2] ** 2) or 1
    return (v[0] / l, v[1] / l, v[2] / l)


def raster(scr, uv, tri, zbuf, col, apx, AW, AH, bright, S):
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = scr[tri[0]], scr[tri[1]], scr[tri[2]]
    u0, u1, u2 = uv[tri[0]], uv[tri[1]], uv[tri[2]]
    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return
    minx, maxx = max(0, int(min(x0, x1, x2))), min(S - 1, int(max(x0, x1, x2)) + 1)
    miny, maxy = max(0, int(min(y0, y1, y2))), min(S - 1, int(max(y0, y1, y2)) + 1)
    inv = 1.0 / area
    for y in range(miny, maxy + 1):
        py = y + 0.5
        zr = zbuf[y]
        cr = col[y]
        for x in range(minx, maxx + 1):
            px = x + 0.5
            w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) * inv
            w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) * inv
            w2 = 1 - w0 - w1
            if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                continue
            z = w0 * z0 + w1 * z1 + w2 * z2
            if z <= zr[x]:
                continue
            u = w0 * u0[0] + w1 * u1[0] + w2 * u2[0]
            v = w0 * u0[1] + w1 * u1[1] + w2 * u2[1]
            tx, ty = min(AW - 1, max(0, int(u))), min(AH - 1, max(0, int(v)))
            c = apx[tx, ty]
            if c[3] < 128:
                continue
            zr[x] = z
            cr[x] = (min(255, int(c[0] * bright)), min(255, int(c[1] * bright)), min(255, int(c[2] * bright)), 255)
