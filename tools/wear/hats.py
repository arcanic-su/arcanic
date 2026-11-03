"""Headwear in the style of the mod's first hats: character hoods (a creature's face on the forehead, the player's
face showing through), little figures sitting on the head, and New Year ones - in chunky Minecraft pixels.

The head is x -4..4, y 24..32, z -4..4 (a skin's hat layer reaches 4.5 out); hoods sit at 4.75."""

import math
import random

from PIL import ImageDraw

from wearlib import *
from kit import Part, bone as kbone  # noqa: E402

D = 2
HX = 4.75  # a hood's half width
CLEAR = (0, 0, 0, 0)


def hat(n, name, view="front"):
    return Model(n, name, "hat", D=D, view=view)


def eye(img, x, y, c="#16161d", s=2, shine=True):
    px = img.load()
    w, h = img.size
    for dy in range(s):
        for dx in range(s):
            if 0 <= x + dx < w and 0 <= y + dy < h:
                px[x + dx, y + dy] = C(c)
    if shine and 0 <= x < w and 0 <= y < h:
        px[x, y] = (255, 255, 255, 255)


def dots(cols, n_per=60, seed=1, faces_=None):
    """Scattered single texels (sprinkles, ornaments, snow)."""

    def l(img, face, D_, rnd):
        if faces_ and face not in faces_:
            return
        w, h = img.size
        r = random.Random(seed + w * 7 + h + len(face))
        px = img.load()
        for _ in range(max(1, w * h // n_per)):
            x, y = r.randrange(w), r.randrange(h)
            if px[x, y][3]:
                px[x, y] = C(r.choice(cols))

    return l


def hood_front(col, top, draw=None, opening=True, holes=None):
    """The hood's front: its colour, the creature's face drawn on the forehead band by draw(img, d, w, band), the
    opening for the player's face cut out under it (or holes(img, d, w, h) cut instead)."""
    base = mc(col)

    def p(face, w, h, D_, rnd):
        img = base(face, w, h, D_, rnd)
        d = ImageDraw.Draw(img)
        band = round((top - 30.4) * D_)
        if opening:
            x0 = round((HX - 3.5) * D_)
            d.rectangle((x0, band, w - 1 - x0, h - 1), fill=CLEAR)
            px = img.load()
            for x in range(x0, w - x0):  # a darker rim round the opening
                px[x, band - 1] = mul(px[x, band - 1], .7)
        if holes:
            holes(img, d, w, h)
        if draw:
            draw(img, d, w, band)
        return img

    return p


def hood(m, col, top=34.4, draw=None, opening=True, holes=None, lining="#1e1714", sides=None, back=None):
    """A hood round the head: the shell, and a dark lining inside it seen through the opening."""
    b = m.bone("hood")
    shell = faces(mc(col), front=hood_front(col, top, draw, opening, holes), **({"sides": sides} if sides else {}))
    if back:
        shell = faces(mc(col), front=hood_front(col, top, draw, opening, holes), back=back,
                      **({"right": sides, "left": sides} if sides else {}))
    b.add(box((-HX, 23.7, -HX), (HX * 2, top - 23.7, HX * 2), shell))
    b.add(Cube((-4.6, 23.8, -4.6), (9.2, top - 23.95, 9.2), mc(lining, edge=.9),
               only=("left", "right", "back", "top")))
    return b


# ------------------------------------------------------------------------------------------------ New Year


def reindeer(n):
    m = hat(n, "Reindeer Hood")
    br, tan, ant = "#8a5a3a", "#d9b48a", "#e3cfa3"

    def face(img, d, w, band):
        eye(img, 4, 2)
        eye(img, w - 6, 2)

    b = hood(m, br, draw=face)
    b.add(box((-2.3, 30.2, -HX - 0.9), (4.6, 2.4, 0.9), mc(tan)))
    b.add(box((-1.0, 31.0, -HX - 1.8), (2.0, 1.8, 0.9), mc("#ff2a2a", "#d41010")))
    for s in (-1, 1):
        x = HX if s > 0 else -HX - 2.2
        b.add(box((x, 32.0, -1.2), (2.2, 1.0, 1.4), mc(br), rot=(0, 0, 25 * s), pivot=(HX * s, 32.5, 0)))
        a = m.bone("antler" + str(s), pivot=(2.4 * s, 34.4, 0), parent="hood")
        ax = 1.9 if s > 0 else -2.9
        a.add(box((ax, 34.4, -0.5), (1.0, 3.0, 1.0), mc(ant)))
        a.add(box((ax, 37.0, -0.5), (1.0, 2.6, 1.0), mc(ant), rot=(0, 0, -30 * s), pivot=(ax + .5, 37.0, 0)))
        a.add(box((ax + 1.2 * s, 36.2, -0.5), (1.0, 2.2, 1.0), mc(ant), rot=(0, 0, -60 * s), pivot=(ax + .5, 36.0, 0)))
        a.add(box((ax - 0.6 * s, 38.6, -0.5), (0.8, 2.0, 0.8), mc(ant), rot=(0, 0, 10 * s), pivot=(ax + .4, 38.6, 0)))
        a.add(box((ax + 2.6 * s, 37.4, -0.5), (0.8, 1.8, 0.8), mc(ant), rot=(0, 0, -15 * s), pivot=(ax + .4, 37.4, 0)))
    return m


def snowman(n):
    m = hat(n, "Snowman Buddy")
    sn, coal = "#f3f6fa", "#1f1f24"
    b = m.bone("snowman")
    bottom = over(mc(sn, edge=.9), only("front", L_draw(lambda d, w, h, D_, f, img: [
        img.putpixel((w // 2, y), C(coal)) for y in (2, 5, 8) if y < h])))
    b.add(box((-3.0, 32.0, -3.0), (6.0, 5.0, 6.0), bottom))

    def face(d, w, h, D_, f, img):
        if f != "front":
            return
        eye(img, 2, 2, coal, s=1, shine=False)
        eye(img, w - 3, 2, coal, s=1, shine=False)
        for x in range(2, w - 2, 1):
            if x % 2 == 0:
                img.putpixel((x, h - 3 + (1 if 2 < x < w - 3 else 0) - 1), C(coal))

    b.add(box((-2.2, 37.0, -2.2), (4.4, 4.0, 4.4), over(mc(sn, edge=.9), L_draw(face))))
    b.add(box((-0.4, 38.4, -3.9), (0.8, 0.8, 1.7), mc("#ff8a1f", "#e66a00")))
    b.add(box((-2.6, 36.5, -2.6), (5.2, 1.0, 5.2), over(mc("#d62a2a"), L_hstripes(["#f3f6fa"], [1], start=0))))
    b.add(box((0.6, 34.2, -2.95), (1.2, 2.4, 0.5), mc("#d62a2a")))
    b.add(box((-2.6, 41.0, -2.6), (5.2, 0.5, 5.2), mc("#1a1a1f")))
    b.add(box((-1.8, 41.5, -1.8), (3.6, 2.6, 3.6), over(mc("#1a1a1f"), L_hstripes(["#c43030"], [1], start=4))))
    for s in (-1, 1):
        x = 3.0 if s > 0 else -6.0
        b.add(box((x, 34.6, -0.4), (3.0, 0.6, 0.6), mc("#6b4a2b"), rot=(0, 0, 25 * s), pivot=(3.0 * s, 34.9, 0)))
    return m


def xmas_tree(n):
    m = hat(n, "Christmas Tree")
    g = "#2f8a3b"
    ornaments = dots(["#e8323c", "#ffd23f", "#3ea0ff", "#ff7ad9", "#ffffff"], n_per=14, seed=n)
    needles = over(mc(g, "#1f6b2c"), ornaments, only("top", dots(["#f4f8fb"], n_per=4, seed=n + 1)))
    b = m.bone("tree")
    b.add(box((-2.4, 32.0, -2.4), (4.8, 1.4, 4.8), over(mc("#a33a2a"), L_hstripes(["#ffd23f"], [1], start=1))))
    for (w, y, h) in ((8.4, 33.4, 2.4), (6.8, 35.6, 2.4), (5.2, 37.8, 2.2), (3.6, 39.8, 2.0), (2.0, 41.6, 1.6)):
        b.add(box((-w / 2, y, -w / 2), (w, h, w), needles))
    s = m.bone("star", pivot=(0, 44.2, 0), parent="tree")
    s.add(box((-0.9, 43.3, -0.4), (1.8, 1.8, 0.8), mc("#ffe14a", "#ffc21a"), rot=(0, 0, 45), pivot=(0, 44.2, 0)))
    s.add(box((-0.6, 43.6, -0.5), (1.2, 1.2, 1.0), mc("#fff6b0")))
    m.anim("star", "rotation", lambda t: (0, t * 360, 0), 3.0, 24)
    return m


def santa(n):
    m = hat(n, "Santa Hat")
    red, wh = "#d42a2a", "#f6f6f6"
    b = m.bone("santa")
    b.add(box((-4.95, 28.8, -4.95), (9.9, 2.3, 9.9), mc(wh, tones=(.86, .93, 1, 1, 1.04))))
    b.add(box((-4.5, 31.1, -4.5), (9.0, 2.2, 9.0), mc(red)))
    t = m.bone("tip", pivot=(0, 33.3, 0), parent="santa")
    t.add(box((-3.6, 33.3, -3.0), (7.2, 2.0, 7.2), mc(red)))
    t.add(box((-2.6, 35.3, -1.4), (5.2, 1.8, 5.6), mc(red)))
    t.add(box((-1.8, 36.5, 0.8), (3.6, 1.6, 3.8), mc(red, "#b81f1f")))
    t.add(box((-1.2, 36.2, 3.8), (2.4, 1.4, 2.4), mc("#b81f1f")))
    t.add(box((-1.5, 34.8, 5.4), (3.0, 3.0, 3.0), mc(wh, tones=(.86, .93, 1, 1, 1.04))))
    m.anim("tip", "rotation", lambda t_: (4 * math.sin(t_ * math.pi * 2), 0, 3 * math.sin(t_ * math.pi * 2 + 1)), 2.2)
    return m


def elf(n):
    m = hat(n, "Elf Hat")
    gr, red, skin = "#2fa84f", "#d62a2a", "#f2c9a0"

    def zig(img, face, D_, rnd):
        w, h = img.size
        px = img.load()
        for x in range(w):
            y = 1 + (x % 4 if x % 4 < 2 else 4 - x % 4)
            if y < h:
                px[x, y] = C("#ffffff")

    b = m.bone("elf")
    b.add(box((-4.9, 29.4, -4.9), (9.8, 2.0, 9.8), over(mc(red), zig)))
    t = m.bone("tip", pivot=(0, 31.4, 0), parent="elf")
    for i, (w, dz) in enumerate(((8.6, 0), (7.0, .6), (5.4, 1.4), (4.0, 2.4), (2.8, 3.6), (1.8, 5.0))):
        t.add(box((-w / 2, 31.4 + i * 1.5, -w / 2 + dz), (w, 1.55, w), mc(gr, "#23863e")))
    t.add(box((-0.9, 39.8, 5.4), (1.8, 1.8, 1.8), mc("#ffd23f", "#e0a81a")))
    m.anim("tip", "rotation", lambda t_: (5 * math.sin(t_ * math.pi * 2), 0, 0), 1.6)
    for s in (-1, 1):
        x = 4.5 if s > 0 else -6.3
        b.add(box((x, 26.4, -0.6), (1.8, 2.2, 1.0), mc(skin), rot=(0, 0, -30 * s), pivot=(4.5 * s, 27.5, 0)))
        b.add(box((x + (1.0 if s > 0 else -0.2), 28.0, -0.5), (1.2, 1.4, 0.8), mc(skin), rot=(0, 0, -45 * s),
                  pivot=(4.5 * s + 1.5 * s, 28.4, 0)))
    return m


def gingerbread(n):
    m = hat(n, "Gingerbread Buddy")
    gin, ice = "#b86b2e", "#fbf6ee"

    def icing_body(d, w, h, D_, f, img):
        if f != "front":
            return
        for y in (2, 4):
            img.putpixel((w // 2, y), C("#e8323c" if y == 2 else "#3ea0ff"))
        for x in range(w):
            if x % 2 == 0:
                img.putpixel((x, h - 2), C(ice))

    def icing_head(d, w, h, D_, f, img):
        if f != "front":
            return
        img.putpixel((2, 2), C("#1f1f24"))
        img.putpixel((w - 3, 2), C("#1f1f24"))
        for x in range(2, w - 2):
            img.putpixel((x, h - 3 if 2 < x < w - 3 else h - 4), C(ice))
        img.putpixel((1, 4), C("#ff9cb5"))
        img.putpixel((w - 2, 4), C("#ff9cb5"))

    b = m.bone("gingerbread")
    b.add(box((-2.2, 32.0, -1.0), (4.4, 4.0, 2.2), over(mc(gin), L_draw(icing_body))))
    b.add(box((-1.9, 36.0, -1.2), (3.8, 3.6, 2.6), over(mc(gin), L_draw(icing_head))))
    for s in (-1, 1):
        x = 2.2 if s > 0 else -4.4
        b.add(box((x, 34.4, -0.6), (2.2, 1.3, 1.3), over(mc(gin), L_hstripes([ice], [1], start=0, vertical=True)),
                  rot=(0, 0, 35 * s), pivot=(2.2 * s, 35, 0)))
        lx = 0.3 if s > 0 else -1.7
        b.add(box((lx, 32.0, -3.8), (1.4, 1.2, 2.8), mc(gin)))
        b.add(box((lx, 30.4, -HX - 0.1), (1.4, 1.8, 1.2), over(mc(gin), L_hstripes([ice], [1], start=2))))
    return m


def gift(n):
    m = hat(n, "Present Box")
    red, gold = "#d62a3a", "#ffd23f"
    ribbon = over(mc(red), L_draw(lambda d, w, h, D_, f, img: (
        d.rectangle((w // 2 - 1, 0, w // 2, h), fill=C(gold)) if f != "bottom" else None,
        d.rectangle((0, h // 2 - 1, w, h // 2), fill=C(gold)) if f in ("top",) else None)))
    b = m.bone("gift")
    b.add(box((-4.8, 31.6, -4.8), (9.6, 4.4, 9.6), ribbon))
    b.add(box((-5.15, 35.6, -5.15), (10.3, 1.5, 10.3), ribbon))
    bow = mc(gold, "#e0a81a")
    for s in (-1, 1):
        b.add(box((-2.8 if s < 0 else 0.4, 37.1, -0.8), (2.4, 2.0, 1.6), bow, rot=(0, 0, -25 * s), pivot=(0, 37.1, 0)))
    b.add(box((-0.8, 37.1, -0.9), (1.6, 1.4, 1.8), bow))
    b.add(box((3.4, 33.0, -5.3), (1.6, 1.2, 0.3), mc("#fbf6ee")))
    return m


def penguin(n):
    m = hat(n, "Penguin Rider")
    bk, wh, org = "#22252e", "#f8f8f6", "#ffa51f"

    def front(d, w, h, D_, f, img):
        if f != "front":
            return
        d.rectangle((2, 3, w - 3, h - 1), fill=mul(C(wh), 1))
        eye(img, 2, 2, "#16161d", s=1)
        eye(img, w - 3, 2, "#16161d", s=1)
        img.putpixel((2, 5), C("#ff9cb5"))
        img.putpixel((w - 3, 5), C("#ff9cb5"))

    b = m.bone("penguin")
    b.add(box((-2.5, 32.0, -2.2), (5.0, 5.2, 4.4), over(mc(bk), L_draw(front))))
    b.add(box((-0.7, 35.2, -3.2), (1.4, 0.8, 1.0), mc(org)))
    for s in (-1, 1):
        b.add(box((2.5 if s > 0 else -3.2, 32.8, -1.2), (0.7, 3.0, 2.2), mc(bk), rot=(0, 0, 15 * s),
                  pivot=(2.5 * s, 35.5, 0)))
        b.add(box((0.2 if s > 0 else -1.8, 32.0, -3.8), (1.6, 0.6, 1.8), mc(org)))
        b.add(box((0.2 if s > 0 else -1.8, 31.0, -HX - 0.2), (1.6, 1.2, 0.6), mc(org)))
    b.add(box((-2.6, 37.2, -2.3), (5.2, 1.8, 4.6), over(mc("#d62a2a"), L_hstripes(["#f8f8f6"], [1], start=0))))
    b.add(box((-0.8, 39.0, -0.8), (1.6, 1.6, 1.6), mc(wh)))
    return m


def cocoa(n):
    m = hat(n, "Hot Cocoa")
    b = m.bone("mug")
    mug = over(mc("#f4f1ea"), L_hstripes(["#d62a2a", None, "#d62a2a"], [2, 3, 2], start=3))
    b.add(box((-3.2, 32.0, -3.2), (6.4, 5.2, 6.4), mug))
    b.add(box((-2.7, 36.2, -2.7), (5.4, 0.8, 5.4), mc("#6b3f22", "#4a2a15")))
    b.add(box((3.2, 33.0, -0.6), (1.6, 3.0, 1.2), mc("#f4f1ea")))
    for (x, z, c) in ((-1.8, -1.6, "#ffffff"), (0.4, -0.6, "#ffd1e0"), (-0.8, 0.9, "#ffffff")):
        b.add(box((x, 36.9, z), (1.4, 1.2, 1.4), mc(c, edge=.92)))
    cane = over(mc("#ffffff"), L_diag("#e8323c", 3, 1))
    b.add(box((1.2, 36.6, 0.6), (0.7, 4.0, 0.7), cane, rot=(0, 0, -15), pivot=(1.5, 36.6, 1)))
    b.add(box((1.4, 40.2, 0.6), (1.8, 0.7, 0.7), cane, rot=(0, 0, -15), pivot=(1.5, 36.6, 1)))
    st = m.bone("steam", pivot=(0, 38, 0), parent="mug")
    for i, (x, y) in enumerate(((-1.2, 38.6), (0.2, 40.0), (-0.6, 41.4))):
        st.add(box((x, y, -0.4), (0.8, 0.8, 0.8), mc("#e9eef5", edge=.95)))
    m.anim("steam", "position", lambda t: (0, 1.2 * t, 0), 1.6)
    return m


# ------------------------------------------------------------------------------------------------ character hoods


def shark(n):
    m = hat(n, "Shark Hood")
    blue, belly = "#6f8fa8", "#eef2f5"

    def teeth(img, d, w, h):
        band = round((34.4 - 30.4) * D)
        x0 = round((HX - 3.5) * D)
        for x in range(x0, w - x0, 2):
            d.polygon([(x, band), (x + 1, band), (x + .5, band + 1)], fill=C("#ffffff"))
            img.putpixel((x, band), C("#ffffff"))

    def face(img, d, w, band):
        pass

    sides = over(mc(blue), L_draw(lambda d, w, h, D_, f, img: f in ("right", "left") and (
        eye(img, 2 if f == "left" else w - 4, 3), [d.line((w // 2 + k * 2 - 2, 7, w // 2 + k * 2 - 2, 10),
                                                          fill=mul(C(blue), .7)) for k in range(3)])))
    b = hood(m, blue, holes=teeth, sides=sides)
    b.add(box((-0.6, 34.4, -0.5), (1.2, 3.6, 3.6), mc(blue, "#5a7890"), rot=(-25, 0, 0), pivot=(0, 34.4, 1)))
    b.add(box((-HX, 23.7, -HX - 0.4), (HX * 2, 1.2, 0.4), mc(belly)))
    b.add(box((-0.5, 28.0, HX), (1.0, 3.0, 2.4), mc(blue), rot=(30, 0, 0), pivot=(0, 30, HX)))
    return m


def dino(n):
    m = hat(n, "Dino Hood")
    g = "#4caf50"

    def face(img, d, w, band):
        eye(img, 3, 2)
        eye(img, w - 5, 2)
        img.putpixel((w // 2 - 2, band - 2), C("#1f5a24"))
        img.putpixel((w // 2 + 1, band - 2), C("#1f5a24"))

    def teeth(img, d, w, h):
        band = round((34.4 - 30.4) * D)
        x0 = round((HX - 3.5) * D)
        for x in range(x0, w - x0, 3):
            img.putpixel((x + 1, band), C("#ffffff"))
            img.putpixel((x + 1, band + 1), C("#ffffff"))

    b = hood(m, g, draw=face, holes=teeth)
    for i in range(5):
        z = -3.6 + i * 2.0
        y = 34.4 if i < 3 else 34.4 - (i - 2) * 1.6
        b.add(box((-0.7, y - 0.4, z), (1.4, 1.4, 1.4), mc("#ffb12b", "#f08a1a"), rot=(45, 0, 0),
                  pivot=(0, y, z + .7)))
    b.add(box((-0.7, 24.5, HX), (1.4, 1.4, 1.4), mc("#ffb12b"), rot=(45, 0, 0), pivot=(0, 25.2, HX + .7)))
    return m


def pumpkin(n):
    m = hat(n, "Jack-o'-Lantern")
    org = "#e8781c"

    def carve(img, d, w, h):
        d.polygon([(3, 7), (7, 7), (5, 4)], fill=CLEAR)
        d.polygon([(w - 4, 7), (w - 8, 7), (w - 6, 4)], fill=CLEAR)
        pts = [(3, 12), (w - 4, 12), (w - 5, 16), (w - 7, 15), (w // 2 + 1, 17), (w // 2 - 1, 15), (6, 17), (4, 15)]
        d.polygon(pts, fill=CLEAR)

    ribs = over(mc(org, "#d0600e"), L_draw(lambda d, w, h, D_, f, img: f != "top" and [
        d.line((x, 0, x, h), fill=C("#b8540c")) for x in range(3, w, 5)]))
    b = m.bone("hood")
    b.add(box((-HX, 23.7, -HX), (HX * 2, 10.0, HX * 2), faces(ribs, front=over(ribs, lambda img, f, D_, r: carve(
        img, ImageDraw.Draw(img), *img.size)))))
    b.add(Cube((-4.6, 23.8, -4.6), (9.2, 9.75, 9.2), mc("#fff07a", "#ffb12b", edge=.95),
               only=("left", "right", "back", "top")))
    b.add(box((-0.7, 33.7, -0.7), (1.4, 1.8, 1.4), mc("#5a7a2a", "#3f5a1c"), rot=(0, 0, 10)))
    b.add(box((0.4, 34.4, -0.3), (2.0, 0.4, 1.4), mc("#4caf50"), rot=(0, 0, -20), pivot=(0.6, 34.6, 0)))
    return m


def fox(n):
    m = hat(n, "Fox Hood")
    org, wh = "#f07b2c", "#fff4e6"

    def face(img, d, w, band):
        d.rectangle((0, band - 3, 4, band - 1), fill=C(wh))
        d.rectangle((w - 5, band - 3, w - 1, band - 1), fill=C(wh))
        eye(img, 4, 2)
        eye(img, w - 6, 2)

    b = hood(m, org, draw=face)
    b.add(box((-1.8, 30.2, -HX - 1.2), (3.6, 2.0, 1.2), over(mc(wh), only("front", L_draw(
        lambda d, w, h, D_, f, img: d.rectangle((w // 2 - 1, 0, w // 2, 1), fill=C("#1f1f24")))))))
    for s in (-1, 1):
        x = 1.0 if s > 0 else -4.4
        b.add(box((x, 34.4, -2.6), (3.4, 2.0, 1.4), mc(org)))
        b.add(box((x + 0.6, 36.4, -2.5), (2.2, 1.4, 1.2), mc(org)))
        b.add(box((x + 1.1, 37.8, -2.4), (1.2, 1.0, 1.0), mc("#2b1d14")))
    return m


def panda(n):
    m = hat(n, "Panda Hood")
    wh, bk = "#f6f6f2", "#1c1c20"

    def face(img, d, w, band):
        d.rectangle((3, 1, 6, 4), fill=C(bk))
        d.rectangle((w - 7, 1, w - 4, 4), fill=C(bk))
        img.putpixel((4, 2), (255, 255, 255, 255))
        img.putpixel((w - 6, 2), (255, 255, 255, 255))

    b = hood(m, wh, draw=face)
    b.add(box((-1.6, 30.3, -HX - 0.9), (3.2, 1.8, 0.9), over(mc(wh), only("front", L_draw(
        lambda d, w, h, D_, f, img: d.rectangle((w // 2 - 1, 0, w // 2, 1), fill=C(bk)))))))
    for s in (-1, 1):
        b.add(box((2.4 if s > 0 else -4.6, 34.4, -1.6), (2.2, 2.0, 1.6), mc(bk)))
    return m


def polar(n):
    m = hat(n, "Polar Bear Hood")
    wh = "#eef2f6"

    def face(img, d, w, band):
        eye(img, 4, 2)
        eye(img, w - 6, 2)

    b = hood(m, wh, draw=face)
    b.add(box((-2.0, 30.2, -HX - 1.3), (4.0, 2.4, 1.3), over(mc("#f7f2e8"), only("front", L_draw(
        lambda d, w, h, D_, f, img: d.rectangle((w // 2 - 1, 0, w // 2, 1), fill=C("#1f1f24")))))))
    for s in (-1, 1):
        b.add(box((2.6 if s > 0 else -4.6, 34.4, -1.4), (2.0, 1.6, 1.4), over(mc(wh), only("front", L_draw(
            lambda d, w, h, D_, f, img: d.rectangle((1, 1, w - 2, h - 1), fill=C("#ffb3c4")))))))
    b.add(box((-HX - 0.1, 23.6, -HX - 0.1), (HX * 2 + .2, 1.0, HX * 2 + .2), mc("#3ea0ff")))  # a blue scarf
    return m


def frog_prince(n):
    m = hat(n, "Frog Prince")
    g = "#5fcf5a"

    def face(d, w, h, D_, f, img):
        if f != "front":
            return
        img.putpixel((w // 2 - 2, h - 3), C("#2f8a3b"))
        img.putpixel((w // 2 + 1, h - 3), C("#2f8a3b"))
        for x in range(2, w - 2):
            img.putpixel((x, h - 2), C("#2f8a3b"))

    b = m.bone("frog")
    b.add(box((-3.0, 32.0, -2.8), (6.0, 3.2, 5.0), over(mc(g), L_draw(face))))
    for s in (-1, 1):
        x = 0.4 if s > 0 else -2.6
        b.add(box((x, 35.2, -2.6), (2.2, 2.0, 2.0), over(mc(g), only("front", lambda img, f, D_, r: (
            ImageDraw.Draw(img).rectangle((0, 0, img.size[0] - 1, img.size[1] - 1), fill=C("#ffffff")),
            eye(img, 1, 1, s=2, shine=False))))))
        b.add(box((x - 0.2 * s + (0.2 if s < 0 else 0), 32.0, -4.0), (1.8, 0.8, 1.6), mc("#4cb848")))
    b.add(box((-1.6, 35.2, -0.4), (3.2, 0.9, 2.6), mc("#ffd23f")))
    for x in (-1.6, -0.4, 0.8):
        b.add(box((x, 36.1, -0.4), (0.8, 0.9, 0.8), mc("#ffd23f")))
    return m


def mushroom(n):
    m = hat(n, "Amanita Cap")
    red = "#d63a3a"
    spots = dots(["#fff7ea"], n_per=7, seed=n)
    cap = faces(over(mc(red, "#b82828"), spots), bottom=over(mc("#efe1c6"), L_draw(
        lambda d, w, h, D_, f, img: [d.line((w // 2, h // 2, x, 0 if x % 4 else h - 1), fill=C("#d9c7a3"))
                                     for x in range(0, w, 3)])))
    b = m.bone("cap")
    b.add(box((-5.6, 30.8, -5.6), (11.2, 2.2, 11.2), cap))
    b.add(box((-4.6, 33.0, -4.6), (9.2, 1.6, 9.2), over(mc(red), spots)))
    b.add(box((-3.0, 34.6, -3.0), (6.0, 0.8, 6.0), over(mc(red), spots)))
    return m


def crown(n):
    m = hat(n, "Royal Crown")
    gold = "#f3c23a"
    gems = ["#e0245e", "#2f80ed", "#27ae60"]

    def band(img, face, D_, rnd):
        w, h = img.size
        px = img.load()
        for i, x in enumerate(range(2, w - 1, 5)):
            for dy in (0, 1):
                for dx in (0, 1):
                    if 0 <= h // 2 - 1 + dy < h:
                        px[x + dx, h // 2 - 1 + dy] = C(gems[(i + len(face)) % 3])
            px[x, h // 2 - 1] = (255, 255, 255, 255)

    b = m.bone("crown")
    b.add(box((-4.8, 31.4, -4.8), (9.6, 2.4, 9.6), over(mc(gold, "#d9a520"), band)))
    for (x, z) in ((-4.8, -4.8), (-0.7, -4.8), (3.4, -4.8), (-4.8, 3.4), (-0.7, 3.4), (3.4, 3.4), (-4.8, -0.7),
                   (3.4, -0.7)):
        b.add(box((x, 33.8, z), (1.4, 1.8, 1.4), mc(gold, "#d9a520")))
        b.add(box((x + .3, 35.6, z + .3), (0.8, 0.8, 0.8), mc(gems[int(abs(x + z)) % 3])))
    b.add(box((-3.9, 33.4, -3.9), (7.8, 0.8, 7.8), mc("#8a1f3d")))
    return m


def halo(n):
    m = hat(n, "Angel Halo")
    gold = mc("#ffe14a", "#ffc21a", edge=.85)
    b = m.bone("halo", pivot=(0, 36, 0))
    for i in range(8):
        a = i * 45
        r = 3.6
        x, z = math.cos(math.radians(a)) * r, math.sin(math.radians(a)) * r
        b.add(box((x - 1.55, 35.6, z - 0.4), (3.1, 0.8, 0.8), gold, rot=(0, -a - 90, 0), pivot=(x, 36, z)))
    m.anim("halo", "position", lambda t: (0, .5 * math.sin(t * math.pi * 2), 0), 2.4)
    return m


def cupcake(n):
    m = hat(n, "Cupcake")
    sprinkles = dots(["#ff4f8b", "#ffd23f", "#3ec5ff", "#7cf06b", "#ffffff"], n_per=6, seed=n)
    b = m.bone("cupcake")
    b.add(box((-4.85, 30.2, -4.85), (9.7, 2.8, 9.7), over(mc("#ff9cc2"), L_hstripes(
        ["#ff7aae", None] * 6, [1, 1] * 6, start=0, vertical=True))))
    for (w, y, h) in ((9.0, 33.0, 1.8), (7.0, 34.8, 1.6), (4.8, 36.4, 1.4)):
        b.add(box((-w / 2, y, -w / 2), (w, h, w), over(mc("#fff1f6", "#ffd9e8"), sprinkles)))
    b.add(box((-0.9, 37.8, -0.9), (1.8, 1.8, 1.8), mc("#e8233a", "#b8122a")))
    b.add(box((0.2, 39.6, -0.2), (0.4, 1.2, 0.4), mc("#5a7a2a"), rot=(0, 0, -20)))
    return m


def witch(n):
    m = hat(n, "Witch Hat")
    pur = "#3b2466"
    b = m.bone("witch")
    b.add(box((-6.6, 31.2, -6.6), (13.2, 0.7, 13.2), mc(pur)))
    b.add(box((-4.4, 31.9, -4.4), (8.8, 1.4, 8.8), over(mc("#7b4dd6"), only("front", L_draw(
        lambda d, w, h, D_, f, img: d.rectangle((w // 2 - 2, 0, w // 2 + 1, h - 1), outline=C("#ffd23f")))))))
    t = m.bone("tip", pivot=(0, 33.3, 0), parent="witch")
    for i, (w, dz) in enumerate(((7.6, 0), (6.0, .3), (4.6, .9), (3.4, 1.8), (2.4, 3.0), (1.6, 4.4))):
        t.add(box((-w / 2, 33.3 + i * 1.6, -w / 2 + dz), (w, 1.65, w), mc(pur, "#2a1a4a")))
    m.anim("tip", "rotation", lambda t_: (3 * math.sin(t_ * math.pi * 2), 0, 2 * math.sin(t_ * math.pi * 2)), 2.6)
    return m


# ------------------------------------------------------------------------------------------------ masks and gear

from kit import art, cut  # noqa: E402

FACE_Z = -4.55  # just in front of a skin's hat layer


def strap(b, y, col, h=0.8):
    """A strap round the head at height y (sides and back)."""
    b.add(Cube((-4.65, y, -4.4), (9.3, h, 9.05), mc(col), only=("left", "right", "back")))


def sf_mask(n):
    """A fiend's mask after Dota's Shadow Fiend: black horned head, burning eyes and mouth, horns sweeping back."""
    m = hat(n, "Shadow Fiend Mask")
    dark = ramp(["#2a2224", "#1a1416", "#0e0a0b"])
    glow = {"o": "#ffb347", "r": "#ff4a1a", "y": "#fff1a0", "k": "#0a0607"}
    face = over(dark, art(["...................", "..rr...........rr..", "..ooor.......rooo..", "...yoor.....rooy...",
                           "....rr.......rr....", "...................", "........k..........",
                           "..r.r.r.r.r.r.r.r..", "..ooooooooooooooo..", "...r.r.r.r.r.r.r...",
                           "..................."], glow, y=3))
    b = m.bone("fiend")
    b.add(box((-HX, 23.7, -HX), (HX * 2, 10.0, HX * 2), faces(dark, front=face)))
    b.add(box((-3.0, 24.0, -HX - 0.8), (6.0, 2.0, 0.8), over(dark, art(["o.o.o.o.o.o."], glow, y=0))))
    horn = ramp(["#5a4a46", "#3a2e2b", "#1e1715"])
    for s_ in (1, -1):
        h = Part(b, (0, 0, 0), s_)
        h.add((2.4, 33.4, -2.6), (2.2, 2.0, 2.2), horn)
        h.add((3.2, 35.0, -1.8), (1.8, 1.8, 2.4), horn, rot=(-25, 0, -15), pivot=(4, 35, -1))
        h.add((3.8, 36.0, 0.0), (1.4, 1.4, 2.6), horn, rot=(-50, 0, -20), pivot=(4.5, 36.4, 0.8))
        h.add((4.2, 36.0, 2.2), (1.0, 1.0, 2.6), horn, rot=(-80, 0, -25), pivot=(4.7, 36.4, 2.8))
        h.add((4.4, 34.6, 4.0), (.7, .7, 1.8), ramp(["#ff8a2a", "#c4200a"], edge=None), rot=(-105, 0, -25),
              pivot=(4.7, 35, 4.4))
        for k, y in enumerate((26.0, 28.4, 30.8)):
            h.add((HX, y, -1.5 + k * 1.5), (1.0, .9, .9), horn, rot=(0, 0, -30), pivot=(HX, y, 0))
    return m


def krueger(n):
    m = hat(n, "Krueger Fedora")
    felt, band = ramp(["#8a6a48", "#6b4f34", "#4a3622"]), ramp(["#2a1e14", "#1a120c"])
    b = m.bone("fedora")
    b.add(box((-6.3, 31.4, -6.6), (12.6, 0.6, 13.0), mc("#6b4f34")))
    b.add(box((-6.3, 31.0, -7.2), (12.6, 0.6, 1.4), mc("#5a4028")))  # the brim tipped down at the front
    b.add(box((-4.2, 32.0, -4.2), (8.4, 1.2, 8.4), band))
    b.add(box((-4.0, 33.2, -4.0), (8.0, 2.6, 8.0), felt))
    b.add(box((-3.6, 35.8, -3.6), (2.8, 0.6, 7.2), felt))
    b.add(box((0.8, 35.8, -3.6), (2.8, 0.6, 7.2), felt))
    b.add(box((-0.8, 35.4, -3.6), (1.6, 0.6, 7.2), felt))
    # the striped jumper's collar, red and green
    b.add(box((-4.5, 23.4, -4.5), (9.0, 1.2, 9.0), over(mc("#b8222c"), L_hstripes(
        ["#2f7a26", None] * 5, [2, 2] * 5, start=0, vertical=True))))
    return m


def scream(n):
    m = hat(n, "Ghostface Mask")
    cloth = ramp(["#24242a", "#16161b", "#0c0c10"])
    mask = over(ramp(["#ffffff", "#f2f2ee", "#d9d9d2"]), art(["...............", "...kk.....kk...", "..kkkk...kkkk..",
                                                             "..kkkk...kkkk..", "...kkk...kkk...", "...kk.....kk...",
                                                             "...............", "......k.k......", "...............",
                                                             "......kkk......", ".....kkkkk.....", ".....kkkkk.....",
                                                             "......kkk......", "......kkk......"], {"k": "#0c0c10"},
                                                            y=0))
    b = hood(m, "#16161b", opening=False)
    b.add(box((-3.6, 24.0, -HX - 0.7), (7.2, 7.4, 0.7), mask))
    b.add(box((-3.0, 31.4, -HX - 0.6), (6.0, 0.8, 0.6), mc("#ececea")))
    d = kbone(m, "drape", (0, 24, HX), parent="hood")
    for k, x in enumerate((-4.6, -1.6, 1.4)):
        d.add((x, 19.0 - (k % 2), HX - 0.4), (3.2, 5.0 + (k % 2), 0.8), cloth)
    for s_ in (1, -1):
        Part(b, (0, 0, 0), s_).add((HX - .2, 19.5, -3.6), (0.8, 4.4, 7.4), cloth)
    m.anim("drape", "rotation", lambda t: (6 * math.sin(t * math.pi * 2), 0, 0), 2.4)
    return m


def hockey(n):
    m = hat(n, "Voorhees Hockey Mask")
    mask = over(ramp(["#fbfbf6", "#ecebe2", "#d6d4c6"]),
                art(["....r.....r....", "...r.r...r.r...", "...............", "..kkk.....kkk..", "..kkk.....kkk..",
                     "...k.......k...", "......k.k......", "..k...k.k...k..", "......k.k......", ".k..k.....k..k.",
                     "......k.k......", "...k..k.k..k...", "......k.k......", "..............."],
                    {"r": "#d6202c", "k": None}, y=0))
    b = m.bone("mask")
    b.add(box((-3.7, 24.2, FACE_Z - 0.9), (7.4, 7.4, 0.9), mask))
    b.add(box((-3.0, 31.6, FACE_Z - 0.7), (6.0, 0.6, 0.7), mc("#ecebe2")))
    for y in (25.4, 28.6, 30.6):
        strap(b, y, "#3a3a3e", 0.6)
    return m


def balaclava(n):
    m = hat(n, "Balaclava")
    knit = over(mc("#1c1c22"), L_draw(lambda d, w, h, D_, f, img: [
        d.line((x, 0, x, h), fill=rgb("#26262e")) for x in range(0, w, 3)]))
    slit = cut(lambda w, h: [(3, round((32.9 - 29.6) * D), w - 4, round((32.9 - 27.2) * D))])
    b = m.bone("balaclava")
    b.add(box((-4.7, 23.6, -4.7), (9.4, 9.3, 9.4), faces(knit, front=over(knit, slit,
                                                                          art(["w"], {"w": "#ffffff"}, x=2, y=1)))))
    b.add(Cube((-4.55, 23.7, -4.55), (9.1, 9.0, 9.1), mc("#101014"), only=("left", "right", "back", "top")))
    return m


def visor(n):
    m = hat(n, "Cyber Visor")
    lens = ramp(["#7af0ff", "#3ea0ff", "#8a5cff", "#ff4fd8"], edge=None)
    b = m.bone("visor")
    b.add(box((-4.4, 27.0, FACE_Z - 1.2), (8.8, 2.4, 1.2), over(lens, art(["ww..............."],
                                                                          {"w": "#ffffff"}, y=0))))
    b.add(box((-4.7, 26.8, FACE_Z - 1.4), (9.4, 0.4, 1.6), mc("#2a2e38")))
    b.add(box((-4.7, 29.4, FACE_Z - 1.4), (9.4, 0.4, 1.6), mc("#2a2e38")))
    strap(b, 27.6, "#2a2e38", 1.0)
    for s_ in (1, -1):
        Part(b, (0, 0, 0), s_).add((4.55, 27.2, -1.4), (0.6, 1.8, 2.4), ramp(["#7af0ff", "#3ea0ff"], edge=None))
    return m


def goggles(n):
    m = hat(n, "Steampunk Goggles")
    brass, glass = ramp(["#f3d27a", "#d9a520", "#a8740f"]), ramp(["#bff4ff", "#5cc4ef", "#2a86b0"], edge=None)
    b = m.bone("goggles")
    strap(b, 30.2, "#5a3a22", 1.2)
    b.add(box((-4.65, 30.2, -4.65), (9.3, 1.2, 0.3), mc("#5a3a22")))
    for x in (-3.9, 0.5):
        b.add(box((x, 29.6, -5.6), (3.4, 2.6, 1.0), brass))
        b.add(box((x + 0.5, 30.0, -5.9), (2.4, 1.8, 0.4), glass))
        b.add(box((x + 0.3, 29.4, -6.0), (2.8, 0.4, 0.5), brass))
    b.add(box((-0.5, 30.4, -5.2), (1.0, 0.8, 0.6), brass))
    b.add(box((3.4, 30.4, -5.0), (0.8, 0.8, 0.8), ramp(["#e8945a", "#c46a2a"])))
    return m


def studio_phones(n):
    m = hat(n, "Studio Headphones")
    shell, pad, led = ramp(["#3a3e48", "#22252c"]), ramp(["#5a5e68", "#3a3e48"]), ramp(["#ff4fd8", "#c42aa8"], edge=None)
    b = m.bone("phones")
    b.add(box((-4.0, 32.6, -1.2), (8.0, 1.2, 2.4), over(shell, L_hstripes(["#ff4fd8"], [1], start=1))))
    b.add(box((-3.0, 33.6, -0.9), (6.0, 0.6, 1.8), pad))
    for s_ in (1, -1):
        p = Part(b, (0, 0, 0), s_)
        p.add((4.0, 30.0, -0.9), (1.0, 3.2, 1.8), shell)
        p.add((4.5, 25.4, -2.6), (1.4, 5.0, 5.2), shell)
        p.add((4.3, 25.8, -2.2), (0.4, 4.2, 4.4), pad)
        p.add((5.9, 26.6, -1.4), (0.3, 2.6, 2.8), led)
    return m


def headset(n):
    m = hat(n, "Gamer Headset")
    body, glow = ramp(["#1e1e24", "#121216"]), ramp(["#5bff8a", "#20c45a"], edge=None)
    b = m.bone("headset")
    b.add(box((-4.2, 32.4, -1.0), (8.4, 1.0, 2.0), over(body, L_hstripes(["#5bff8a"], [1], start=0))))
    for s_ in (1, -1):
        p = Part(b, (0, 0, 0), s_)
        p.add((4.1, 29.6, -0.8), (0.9, 3.4, 1.6), body)
        p.add((4.4, 25.8, -2.2), (1.4, 4.2, 4.4), body)
        p.add((5.8, 26.6, -1.4), (0.3, 2.6, 2.8), over(glow, art(["k.k", ".k.", "k.k"], {"k": "#0a2a12"}, y=1,
                                                                       faces_=("right", "left"))))
    mic = Part(b, (0, 0, 0), -1)
    mic.add((4.6, 26.6, -3.2), (0.6, 0.6, 3.6), body, rot=(0, 25, 0), pivot=(4.9, 26.9, -1.4))
    mic.add((3.0, 26.2, -5.6), (1.4, 1.2, 1.2), over(body, art(["g"], {"g": "#5bff8a"}, x="c", y=0)))
    for k, x in enumerate((-1.2, 0.4)):
        b.add((x, 33.4, -0.3), (0.8, 1.4, 0.6), glow, rot=(0, 0, 0)) if False else None
    return m


def cap(n):
    m = hat(n, "Arcanic Snapback")
    top, brim = ramp(["#2a2440", "#1d1830", "#120f20"]), ramp(["#8b5cf6", "#6d3fd6"])
    logo = art(["...vvvvvv...", "..vv....vv..", "..vv.vv.vv..", "..vvvvvvvv..", "..vv....vv.."], {"v": "#c4b5fd"},
               y=1)
    b = m.bone("cap")
    b.add(box((-4.65, 29.6, -4.65), (9.3, 3.2, 9.3), over(top, logo)))
    b.add(box((-4.0, 32.8, -4.0), (8.0, 0.8, 8.0), top))
    b.add(box((-0.6, 33.6, -0.6), (1.2, 0.5, 1.2), brim))
    b.add(box((-4.65, 29.6, -9.0), (9.3, 0.6, 4.4), faces(brim, bottom=mc("#2a5a3a"))))
    b.add(box((-2.0, 30.0, 4.6), (4.0, 1.0, 0.3), mc("#c9ced6")))
    return m


def pom_beanie(n):
    m = hat(n, "Pom Beanie")
    stripes = over(mc("#e8323c"), L_hstripes(["#ffffff", None, "#ffffff", None], [2, 2, 2, 2], start=1))
    b = m.bone("beanie")
    b.add(box((-4.85, 28.4, -4.85), (9.7, 2.2, 9.7), over(mc("#f2f2f2"), L_draw(lambda d, w, h, D_, f, img: [
        d.line((x, 0, x, h), fill=rgb("#d9d9d9")) for x in range(0, w, 2)]))))
    b.add(box((-4.7, 30.6, -4.7), (9.4, 2.8, 9.4), stripes))
    b.add(box((-4.0, 33.4, -4.0), (8.0, 0.8, 8.0), mc("#e8323c")))
    b.add(box((-1.6, 34.0, -1.6), (3.2, 2.8, 3.2), mc("#ffffff", tones=(.86, .93, 1, 1, 1.04))))
    return m


def bucket(n):
    m = hat(n, "Fisherman Bucket")
    den = over(mc("#c9b48d"), L_draw(lambda d, w, h, D_, f, img: [d.point((x, y), fill=rgb("#a8946c")) for x in range(w)
                                                                  for y in range(h) if (x + y) % 3 == 0]))
    b = m.bone("bucket")
    b.add(box((-6.4, 29.8, -6.4), (12.8, 0.6, 12.8), den, rot=(0, 0, 0)))
    b.add(box((-4.4, 30.4, -4.4), (8.8, 2.8, 8.8), den))
    b.add(box((-4.0, 33.2, -4.0), (8.0, 0.6, 8.0), den))
    b.add(box((-4.45, 30.4, -4.45), (8.9, 0.8, 8.9), mc("#3a2e22")))
    b.add(box((2.6, 31.2, -4.7), (1.2, 1.2, 0.4), ramp(["#ff8a1f", "#e06a10"])))  # a fly hook
    return m


def gas_mask(n):
    m = hat(n, "Gas Mask")
    rub, glass, can = ramp(["#3a3e36", "#2a2e26", "#1a1d16"]), ramp(["#d6ff8a", "#8ad35a"], edge=None), ramp(
        ["#6b7a4a", "#4a5a2a"])
    b = m.bone("gas")
    b.add(box((-3.8, 24.4, FACE_Z - 0.8), (7.6, 6.4, 0.8), rub))
    for x in (-3.2, 0.6):
        b.add(box((x, 27.4, FACE_Z - 1.2), (2.6, 2.4, 0.6), over(glass, art(["w."], {"w": "#ffffff"}, x=1, y=1))))
        b.add(box((x - .2, 27.2, FACE_Z - 1.0), (3.0, 0.3, 0.3), rub))
    b.add(box((-1.2, 24.6, FACE_Z - 2.0), (2.4, 2.2, 1.4), rub))
    for s_ in (1, -1):
        Part(b, (0, 0, 0), s_).add((1.0, 23.8, FACE_Z - 2.6), (2.2, 2.2, 2.2), over(can, L_hstripes(["#2a2e26"], [1],
                                                                                                   start=2)),
                                    rot=(0, 25, 15), pivot=(1.6, 24.8, FACE_Z - 1.6))
    for y in (25.6, 29.4):
        strap(b, y, "#2a2e26", 0.7)
    return m


def kitsune_mask(n):
    m = hat(n, "Kitsune Mask", view="right")
    wh = ramp(["#ffffff", "#f6f2ea", "#e2dccf"])
    face = over(wh, art(["...............", "..rr.......rr..", "..rrr.....rrr..", "...rk.....kr...", "...............",
                         ".......k.......", "......rrr......", "..............."], {"r": "#d6202c", "k": "#1a1414"},
                        y=1))
    b = kbone(m, "mask", (0, 28, 0), rot=(0, -62, 0))  # worn on the side of the head
    b.add((-3.4, 24.6, -5.6), (6.8, 6.6, 0.9), face)
    b.add((-1.2, 25.2, -6.6), (2.4, 1.8, 1.0), over(wh, art(["k"], {"k": "#1a1414"}, x="c", y=0)))
    for s_ in (1, -1):
        Part(b.bone, (0, 0, 0), s_).add((1.4, 31.2, -5.4), (1.8, 2.0, 0.8), over(wh, art(["rr", "rr"],
                                                                                         {"r": "#d6202c"}, y=1)))
    strap(m.bone("tie"), 28.4, "#d6202c", 0.6)
    return m


def oni(n):
    m = hat(n, "Oni Mask")
    red = ramp(["#e8323c", "#c4202a", "#8a1018"])
    face = over(red, art(["...............", "..kk.......kk..", "..yyk.....kyy..", "...kk.....kk...", "...............",
                          ".......k.......", "...............", "..kkkkkkkkkkk..", "..kwkkkkkkkwk..", "..kkkkkkkkkkk.."],
                         {"k": "#1a0a0c", "y": "#ffd23f", "w": "#ffffff"}, y=1))
    b = m.bone("oni")
    b.add(box((-3.8, 24.2, FACE_Z - 1.0), (7.6, 7.6, 1.0), face))
    b.add(box((-3.2, 31.6, FACE_Z - 0.9), (6.4, 1.0, 0.9), ramp(["#1a1a1f"])))  # the brows
    horn = ramp(["#fff7e0", "#e6dcc0", "#b8ae92"])
    for s_ in (1, -1):
        p = Part(b, (0, 0, 0), s_)
        p.add((2.0, 31.8, FACE_Z - 0.8), (1.4, 1.8, 1.0), horn)
        p.add((2.3, 33.4, FACE_Z - 0.6), (1.0, 1.6, 0.8), horn, rot=(0, 0, -20), pivot=(2.8, 33.4, FACE_Z))
        p.add((0.6, 23.6, FACE_Z - 1.1), (0.7, 1.2, 0.5), ramp(["#ffffff", "#d9d9d2"]))
    strap(b, 28.4, "#1a1414", 0.6)
    return m


def plague(n):
    m = hat(n, "Plague Doctor")
    lea, glass = ramp(["#3a2e26", "#2a201a", "#18120e"]), ramp(["#ffe9a0", "#e0b040"], edge=None)
    b = m.bone("plague")
    b.add(box((-4.0, 24.2, FACE_Z - 0.9), (8.0, 7.6, 0.9), lea))
    for x in (-3.2, 0.8):
        b.add(box((x, 28.0, FACE_Z - 1.3), (2.4, 2.4, 0.6), over(glass, art(["w."], {"w": "#ffffff"}, x=1, y=1))))
    beak = ramp(["#4a3a2e", "#2a201a"])
    for k, (w, h, z) in enumerate(((3.0, 3.0, 2.0), (2.2, 2.2, 2.0), (1.4, 1.4, 2.0), (0.8, 0.8, 1.6))):
        b.add(box((-w / 2, 26.8 - k * 0.6, FACE_Z - 0.9 - 2.0 * (k + 1)), (w, h, z), beak))
    b.add(box((-5.8, 31.8, -5.8), (11.6, 0.6, 11.6), mc("#1a1414")))
    b.add(box((-3.6, 32.4, -3.6), (7.2, 3.6, 7.2), over(mc("#1a1414"), L_hstripes(["#5a4a3a"], [1], start=5))))
    return m


def cowboy(n):
    m = hat(n, "Desert Cowboy")
    lea = ramp(["#b07a48", "#8a5a33", "#6b4426"])
    b = m.bone("cowboy")
    b.add(box((-4.4, 31.6, -6.4), (8.8, 0.6, 12.8), lea))
    for s_ in (1, -1):
        Part(b, (0, 0, 0), s_).add((4.4, 31.6, -5.6), (3.4, 0.6, 11.2), lea, rot=(0, 0, 28), pivot=(4.4, 31.9, 0))
    b.add(box((-3.8, 32.2, -3.4), (7.6, 1.0, 6.8), over(mc("#3b2414"), art(["....yy...."], {"y": "#e8d080"}, y=0))))
    b.add(box((-3.8, 33.2, -3.4), (7.6, 3.0, 6.8), lea))
    b.add(box((-3.8, 36.2, -3.4), (2.8, 0.8, 6.8), lea))
    b.add(box((1.0, 36.2, -3.4), (2.8, 0.8, 6.8), lea))
    return m


ALL = [sf_mask, krueger, scream, hockey, balaclava, visor, goggles, studio_phones, headset, cap, pom_beanie, bucket,
       gas_mask, kitsune_mask, oni, plague, cowboy, reindeer, snowman, xmas_tree, santa, elf, gingerbread, gift,
       penguin, cocoa, shark, dino, pumpkin, fox, panda, polar, frog_prince, mushroom, crown, halo, cupcake, witch]
