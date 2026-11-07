"""Brand backpacks in the new style: hand-painted (colours down ramps, stitching, worn edges) like the mod's first
backpacks, in shapes of their own - the classic daypack, a hard trunk, a roll-top, an expedition pack with a rolled mat,
a quilted puffer - with charms, padlocks, tags and carabiners hanging off them that swing as you move.

Worn on the back: the body from z 2.3 out, straps over the shoulders and down the chest."""

import math

from PIL import ImageDraw

import packs as P
from kit import art, chain
import mat
from wearlib import *

D = 4
LOOP = 3.0
OUT = 6.5  # the outer face of a standard body (2.3 + 4.2)


def R(*cols, **kw):
    """A ramp with only a faint grain: the materials and the light carry the surface now."""
    kw.setdefault("tones", (.97, .99, 1.0, 1.0, 1.02))
    return ramp(list(cols), **kw)


def leather(c, light=1.18, dark=.78, stitch=None):
    c = C(c)
    base = over(R(mul(c, light), c, mul(c, dark)), mat.grain(), mat.blotch(amp=.05))
    return over(base, P.L_stitch(stitch, inset=1, dash=2, gap=1)) if stitch else base


def webbing(c, line=None):
    c = C(c)
    ln = C(line) if line else mul(c, 1.35)

    def l(img, face, D_, rnd):
        w, h = img.size
        d = ImageDraw.Draw(img)
        if w >= h:
            d.line((0, h // 2, w, h // 2), fill=ln)
        else:
            d.line((w // 2, 0, w // 2, h), fill=ln)

    return over(R(mul(c, 1.1), c, mul(c, .85), edge=.7), mat.weave(.9), l)


def metal(c="#d9dde3"):
    c = C(c)
    return over(R(mul(c, 1.25), c, mul(c, .66), edge=.7), mat.sheen())


# ------------------------------------------------------------------------------------------------ shapes


def seams(k=.72):
    """Stitched seams: a dashed line a texel in from every edge, darker; the bottom face in shadow."""

    def l(img, face, D_, rnd):
        w, h = img.size
        px = img.load()
        if face == "bottom":
            for y in range(h):
                for x in range(w):
                    px[x, y] = mul(px[x, y], .62)
            return
        if w < 6 or h < 6:
            return
        for x in range(1, w - 1):
            if x % 3 != 2:
                px[x, 1] = mul(px[x, 1], k)
                px[x, h - 2] = mul(px[x, h - 2], k)
        for y in range(1, h - 1):
            if y % 3 != 2:
                px[1, y] = mul(px[1, y], k)
                px[w - 2, y] = mul(px[w - 2, y], k)

    return l


def S(paint):
    return over(paint, mat.blotch(amp=.05), seams())


def details(b, x0, w, top, d, metal_p, st, bottle=None):
    """What makes a bag look made: a zip running round its top, a loop to hang it by, straps cinching its sides, a
    bottle in a side pocket."""
    z1 = 2.3 + d
    b.add(box((x0 + .3, top - .35, z1 - .05), (w - .6, .3, .25), metal_p))  # the zip along the top
    for sx in (x0 - .05, x0 + w - .2):
        b.add(box((sx, top - 2.4, 2.6), (.25, .3, d - .6), metal_p))
    for sx in (x0 - .2, x0 + w - .1):  # side compression straps
        b.add(box((sx, 16.0, 2.5), (.3, .5, d - .4), st))
        b.add(box((sx, 19.2, 2.5), (.3, .5, d - .4), st))
    if bottle:
        bx = x0 + w + .15
        b.add(box((bx, 14.0, 3.2), (1.2, 4.4, 1.3), ramp([mul(C(bottle), 1.25), C(bottle), mul(C(bottle), .7)],
                                                         edge=None)))
        b.add(box((bx + .15, 18.4, 3.35), (.9, .7, 1.0), ramp(["#2a2a30"])))


def loop_handle(b, top, st):
    b.add(box((-1.4, top + .1, 3.6), (.5, .9, .6), st))
    b.add(box((.9, top + .1, 3.6), (.5, .9, .6), st))
    b.add(box((-1.4, top + .9, 3.6), (2.8, .5, .6), st))


def straps(b, st, buckle):
    for sx in (-3.1, 1.9):
        b.add(box((sx, 24.0, -2.55), (1.2, 0.45, 4.95), st))
        b.add(box((sx, 15.2, -2.6), (1.2, 8.8, 0.35), st))
        b.add(box((sx + 0.1, 17.4, -2.85), (1.0, 0.9, 0.25), buckle))


def daypack(m, body, trim, st, buckle, pocket=None, side=None, w=8.0, h=10.0, d=4.2):
    """The classic: a rounded top, a front pocket, side pockets."""
    b = m.bone("pack", pivot=(0, 24, 2))
    x0 = -w / 2
    b.add(box((x0, 13, 2.3), (w, h, d), S(body)))
    b.add(box((x0 + .3, 13 + h, 2.55), (w - .6, .6, d - .5), S(trim)))
    b.add(box((x0 + .9, 13.6 + h, 2.9), (w - 1.8, .4, d - 1.2), trim))
    if pocket:
        b.add(box((x0 + 1, 13.6, 2.3 + d), (w - 2, 4.6, 1.2), S(pocket)))
        b.add(box((x0 + 1.3, 18.0, 2.3 + d + 1.15), (w - 2.6, .25, .2), buckle))  # the pocket's zip
    for sx in (x0 + w, x0 - .9):
        b.add(box((sx, 13.8, 3.0), (.9, 4.6, d - 1.4), S(side or trim)))
    loop_handle(b, 14 + h - .1, st)
    details(b, x0, w, 13 + h, d, buckle, st)
    straps(b, st, buckle)
    return b


def trunk(m, body, corner, st, buckle, w=8.4, h=9.6, d=4.0):
    """A hard case: square, metal corners, two clasps, a handle on top."""
    b = m.bone("pack", pivot=(0, 24, 2))
    x0 = -w / 2
    b.add(box((x0, 13.4, 2.3), (w, h, d), body))
    b.add(box((x0 - .1, 13.4 + h * .62, 2.2), (w + .2, .5, d + .2), corner))  # the lid's seam
    for (x, y) in ((x0 - .15, 13.25), (x0 + w - 1.05, 13.25), (x0 - .15, 13.4 + h - .95), (x0 + w - 1.05,
                                                                                            13.4 + h - .95)):
        b.add(box((x, y, 2.15), (1.2, 1.2, d + .3), corner))
    for x in (x0 + 1.4, x0 + w - 2.4):
        b.add(box((x, 13.4 + h * .55, 2.3 + d), (1.0, 1.4, .35), corner))
    b.add(box((-1.6, 13.4 + h, 3.4), (3.2, .5, .7), st))
    b.add(box((-1.6, 13.4 + h + .5, 3.4), (.5, .7, .7), st))
    b.add(box((1.1, 13.4 + h + .5, 3.4), (.5, .7, .7), st))
    b.add(box((-1.6, 13.4 + h + 1.2, 3.4), (3.2, .5, .7), st))
    straps(b, st, buckle)
    return b


def rolltop(m, body, roll, st, buckle, front=None, w=8.0, h=10.5, d=4.0, strap=True):
    """Tall, its top rolled down and held by a strap and buckle down the front."""
    b = m.bone("pack", pivot=(0, 24, 2))
    x0 = -w / 2
    b.add(box((x0, 12.6, 2.3), (w, h, d), S(body)))
    b.add(box((x0 - .2, 12.6 + h - .4, 2.0), (w + .4, 2.0, d + .6), roll))
    if strap:
        b.add(box((-.6, 15.0, 2.3 + d), (1.2, h - 1.4, .3), front or st))
        b.add(box((-.9, 18.0, 2.6 + d), (1.8, 1.2, .3), buckle))
    else:  # two side straps over the roll instead
        for x in (-3.2, 2.2):
            b.add(box((x, 19.0, 2.3 + d), (1.0, h - 4.6, .3), front or st))
            b.add(box((x - .1, 20.4, 2.6 + d), (1.2, 1.0, .3), buckle))
    for sx in (x0 + w, x0 - .5):
        b.add(box((sx, 14.0, 2.6), (.5, .8, d - .6), st))
        b.add(box((sx, 18.0, 2.6), (.5, .8, d - .6), st))
    straps(b, st, buckle)
    return b


def expedition(m, body, lid, mat, st, buckle, w=8.4, h=11.0, d=4.6):
    """A tall hiking pack: a lid on top, a sleeping mat rolled under it, compression straps."""
    b = m.bone("pack", pivot=(0, 24, 2))
    x0 = -w / 2
    b.add(box((x0, 13.6, 2.3), (w, h, d), S(body)))
    details(b, x0, w, 13.6 + h - .2, d, buckle, st, bottle="#3ea0ff")
    b.add(box((x0 + .2, 13.6 + h - .2, 2.2), (w - .4, 1.6, d + .4), lid))
    b.add(box((x0 - .8, 11.2, 3.0), (w + 1.6, 2.4, 2.6), mat))
    for x in (x0 + 1.0, x0 + w - 1.8):
        b.add(box((x, 11.0, 2.8), (.8, 2.8, 3.0), st))
    for y in (16.4, 20.4):
        b.add(box((x0 - .3, y, 2.6 + d - .2), (w + .6, .6, .35), st))
    b.add(box((x0 + 1.2, 15.0, 2.3 + d), (w - 2.4, 4.4, .9), lid))
    straps(b, st, buckle)
    b.add(box((-3.6, 18.6, -2.75), (7.2, .6, .3), st))  # the chest strap
    return b


def puffer(m, band, trim, st, buckle, w=8.4, h=10.4, d=4.4, bands=4):
    """Quilted: puffy bands one over another, each a little rounder in the middle."""
    b = m.bone("pack", pivot=(0, 24, 2))
    x0 = -w / 2
    bh = h / bands
    for i in range(bands):
        y = 13 + i * bh
        b.add(box((x0, y, 2.3), (w, bh, d), band))
        b.add(box((x0 + .5, y + .2, 2.3 + d), (w - 1, bh - .4, .5), band))
    b.add(box((x0 + .4, 13 + h, 2.6), (w - .8, .5, d - .6), trim))
    b.add(box((-1.4, 13.4 + h, 3.6), (2.8, .8, .6), st))
    straps(b, st, buckle)
    return b


# ------------------------------------------------------------------------------------------------ things that hang


def charm(m, name, at, link, tip_fn, n=2, phase=0.0, amp=(14, 18)):
    """A charm on a short chain from at (x, y, z), swinging."""
    chain(m, name, None, "pack", at, (0, -0.8, 0.05), n, .45, .45, link, tip=tip_fn, phase=phase, amp=amp,
          speed=LOOP)


def tip_box(size, paint, layers=()):
    def f(p, end):
        x, y, z = end
        p.add((x - size[0] / 2, y - size[1], z - size[2] / 2), size, over(paint, *layers) if layers else paint)

    return f


def zip_pulls(m, xs, y, z, paint):
    """Little zip pulls that swing."""
    for i, x in enumerate(xs):
        b = m.bone(f"pull{i}", pivot=(x, y, z))
        b.add(box((x - .25, y - 1.1, z), (.5, 1.1, .25), paint))
        m.anim(b.name, "rotation", (lambda k: lambda t: (16 * math.sin(t * math.pi * 2 + k), 0,
                                                         8 * math.sin(t * math.pi * 2 + k + .8)))(i), LOOP)


# ------------------------------------------------------------------------------------------------ motifs (texels)


def A(rows, pal, x="c", y=0, faces_=("back",)):
    """Pixel art on the bag's outer face (its back, away from the player) unless told otherwise."""
    return art(rows, pal, x=x, y=y, faces_=faces_)


def studs(c="#cfd4db", step=5):
    """Silver studs along the edges of a face (none on faces too small for them)."""

    def l(img, face, D_, rnd):
        w, h = img.size
        if w < 4 or h < 4:
            return
        px = img.load()
        for x in range(2, w - 2, step):
            for yy in (1, h - 2):
                px[x, yy] = C(c)
        for y in range(2, h - 2, step):
            for xx in (1, w - 2):
                px[xx, y] = C(c)

    return l



def lvc():
    return over(P.lv_canvas(), mat.weave(.95), mat.blotch(amp=.05))


def ggc(*a):
    return over(P.gg_canvas(*a), mat.weave(.95), mat.blotch(amp=.05))


def logo_text(s, c, y, face="back", scale=1, gap=1, box_c=None, pad=1):
    def l(img, f, D_, rnd):
        if f != face:
            return
        w, h = img.size
        tw = text_width(s, scale, gap)
        x = (w - tw) // 2
        Y = y if y >= 0 else h + y - 5 * scale
        if box_c:
            ImageDraw.Draw(img).rectangle((x - pad - 1, Y - pad, x + tw + pad, Y + 5 * scale + pad - 1),
                                          fill=C(box_c))
        text(img, s, x, Y, c, scale=scale, gap=gap)

    return l


def epi(c):
    """Louis Vuitton's Epi leather: fine waving lines across."""
    c = C(c)

    def l(img, f, D_, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                if (y + int(1.5 * math.sin(x * .7))) % 3 == 0:
                    px[x, y] = mul(px[x, y], .82)

    return over(R(mul(c, 1.15), c, mul(c, .8)), l, mat.sheen(.2, .14))


def denim(c):
    c = C(c)

    def l(img, f, D_, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                if (x + 2 * y) % 4 == 0:
                    px[x, y] = mul(px[x, y], 1.16)

    return over(R(mul(c, 1.12), c, mul(c, .82)), l, mat.twill(1.05), mat.blotch(amp=.08, cell=3))


def camo(cols, seed):
    return over(R(cols[0], mul(C(cols[0]), .9)), P.L_noise_blobs(cols[1:], 3, 2, 5, seed=seed))


def nylon(c):
    c = C(c)

    def l(img, f, D_, rnd):
        w, h = img.size
        px = img.load()
        for y in range(0, h, 2):
            for x in range(w):
                px[x, y] = mul(px[x, y], 1.06)

    return over(R(mul(c, 1.15), c, mul(c, .78)), mat.ripstop())


def holo(img, f, D_, rnd):
    """An iridescent panel: cyan to pink to violet in diagonal bands, dithered."""
    w, h = img.size
    px = img.load()
    cols = [rgb("#7af0ff"), rgb("#c6a8ff"), rgb("#ff9ad8"), rgb("#9ae8ff")]
    for y in range(h):
        for x in range(w):
            t = ((x + y) / 7.0) % len(cols)
            i = int(t)
            c = cols[(i + 1) % len(cols)] if rnd.random() < t - i else cols[i]
            px[x, y] = mul(c, rnd.choice((.9, .96, 1, 1.04)))


TIGER = ["..oo....oo..", ".oyoo..ooyo.", ".oyyyyyyyyo.", "oyykyyyykyyo", "oyyyykkyyyyo", "oywwyyyywwyo",
         ".oyykkkkyyo.", "..oywwwwyo..", "...oyyyyo...", "....oooo...."]
LIPS = ["...rrrrrrrr...", ".rrrrrrrrrrrr.", "rrwwwwwwwwwwrr", "rrwkwkwkwkwkrr", "rrkkkkkkkkkkrr", "rrwkwkwkwkwkrr",
        "rrwwwwwwwwwwrr", ".rrrrrrrrrrrr.", "...rrrrrrrr..."]
DAGGER = ["..s..", ".sss.", "sssss", "..s..", "..s..", "..s..", "..s..", "..s..", "..s..", "..s..", ".sss.", "..s.."]
TRIFERG = ["....ww....", "...wkkw...", "..wkwwkw..", ".wkw..wkw.", "wkwwwwwwkw", "wkkkkkkkkw", "wwwwwwwwww"]
COMPASS = ["...yy...", "..yyyy..", ".yykkyy.", "yykkkkyy", "yykkkkyy", ".yykkyy.", "..yyyy..", "...yy..."]
SWOOSH = ["..........ww", "........www.", "w.....wwww..", "ww.wwwww....", ".wwwww......"]
TREFOIL = ["....ww....", "...wwww...", "ww.wwww.ww", "wwwwwwwwww", ".wwwwwwww.", "..........", "wwwwwwwwww",
           "..........", "wwwwwwwwww"]
HALFDOME = ["..wwwwwww", ".www.....", "www.wwwww", "ww.ww....", "ww.w.wwww", "w.w.w...."]
SHARK = ["wkwkwkwkwkwkwkwk", "kkkkkkkkkkkkkkkk", "kwkwkwkwkwkwkwkw"]


# ------------------------------------------------------------------------------------------------ the backpacks


def lv_keepall(n):
    m = Model(n, "Louis Vuitton Monogram", "bodywear", D=D)
    tan, gold = rgb("#b5763e"), rgb("#e2b54b")
    canvas_ = over(lvc(), P.L_frame(tan, 2), P.L_stitch("#e7c08a", inset=2, dash=1, gap=1))
    trim = leather("#b5763e", stitch="#e7c08a")
    daypack(m, canvas_, trim, trim, metal("#e2b54b"), pocket=over(lvc(), P.L_frame(tan, 2)), side=trim)
    b = m.bones[0]
    b.add(box((-2.4, 21.4, OUT - .1), (4.8, 1.2, .4), trim))  # a leather flap with the name tag
    charm(m, "lock", (2.6, 21.0, OUT + .4), metal("#e2b54b"),
          tip_box((1.4, 1.6, .7), metal("#e2b54b"), [A(["kk", ".."], {"k": "#7a5410"}, y=2)]))
    charm(m, "tag", (-2.6, 21.0, OUT + .4), leather("#b5763e"),
          tip_box((1.8, 2.4, .25), leather("#c98a50"), [logo_text("LV", "#7a4a20", 3, face="back")]), phase=1.5)
    return m


def lv_trunk(n):
    m = Model(n, "Louis Vuitton Epi Cobalt", "bodywear", D=D)
    body = over(epi("#1f5fa8"), logo_text("LV", "#e2b54b", 5, scale=2))
    trunk(m, body, metal("#e2b54b"), leather("#163f70", stitch="#5f8fd0"), metal("#e2b54b"))
    charm(m, "lock", (0.0, 17.2, OUT + .3), metal("#e2b54b"), tip_box((1.4, 1.6, .7), metal("#e2b54b")))
    return m


def gucci_tiger(n):
    m = Model(n, "Gucci Tiger Nocturne", "bodywear", D=D)
    canvas_ = over(ggc("#2a2a30", "#4a4a52"), P.L_hstripes(["#1d5a3a", "#b4232b", "#1d5a3a"], [3, 4, 3],
                                                                   start=11, vertical=True))
    pocket = over(R("#1c1c22", "#121216"), A(TIGER, {"o": "#3a2010", "y": "#e8902a", "k": "#16161d",
                                                       "w": "#fff4e6"}, y=3))
    trim = leather("#1c1c22", stitch="#8a6045")
    daypack(m, canvas_, trim, trim, metal("#e2b54b"), pocket=pocket)
    charm(m, "bee", (2.8, 21.2, OUT + .4), metal("#e2b54b"),
          tip_box((1.4, 1.2, .6), R("#ffd23f", "#e0a81a"), [A(["k.k"], {"k": "#16161d"}, y=1, faces_=("back",))]))
    return m


def gucci_ophidia(n):
    m = Model(n, "Gucci Ophidia Beige", "bodywear", D=D)
    web = P.L_hstripes(["#1d5a3a", "#b4232b", "#1d5a3a"], [3, 4, 3], start=8)
    canvas_ = over(ggc(), web)
    emblem = A(["..gggg..", ".gg..gg.", "gg.gg.gg", "g.g..g.g", "gg.gg.gg", ".gg..gg.", "..gggg.."],
                 {"g": "#e2b54b"}, y=5)
    trim = leather("#5a3a26", stitch="#8a6045")
    daypack(m, canvas_, trim, trim, metal("#e2b54b"), pocket=over(leather("#5a3a26"), emblem), side=trim)
    zip_pulls(m, (-1.5, 1.5), 17.9, OUT + 1.2, metal("#e2b54b"))
    return m


def adidas_terrex(n):
    m = Model(n, "Adidas Terrex Canyon", "bodywear", D=D)
    body = over(nylon("#e0602a"), logo_text("TERREX", "#ffffff", 4), A(TREFOIL[:5], {"w": "#ffffff"}, y=-10))
    mat = over(R("#2a3a2a", "#1a241a"), P.L_hstripes(["#e0602a"], [1], start=3, vertical=True))
    expedition(m, body, nylon("#1f2a24"), mat, webbing("#1a1a1f", "#e0602a"), metal("#3a3f46"))
    b = m.bones[0]
    for k in range(3):
        b.add(box((4.25, 14.5 + k * 1.2, 3.2), (.3, .6, 2.6), R("#ffffff")))  # three stripes down the side
    charm(m, "biner", (3.4, 22.6, OUT + .6), metal("#c9ced6"),
          tip_box((1.0, 1.8, .4), metal("#ff9a3a")), n=1)
    return m


def adidas_trefoil(n):
    m = Model(n, "Adidas Originals Trefoil", "bodywear", D=D)
    body = over(R("#24242a", "#18181c", "#101014"), A(TREFOIL, {"w": "#ffffff"}, y=4), logo_text("ADIDAS", "#ffffff",
                                                                                                   16))
    stripes = over(R("#18181c"), P.L_hstripes(["#ffffff", None, "#ffffff", None, "#ffffff"], [1, 1, 1, 1, 1],
                                              start=0, vertical=True))
    daypack(m, body, R("#18181c"), stripes, metal(), side=stripes)
    zip_pulls(m, (-1.0, 1.0), 22.6, OUT, R("#ffffff"))
    return m


def offwhite_lime(n):
    m = Model(n, "Off-White Arrows Lime", "bodywear", D=D)
    body = over(R("#1e2420", "#141814"), P.L_draw(lambda d, w, h, D_, f, img: f == "back" and lime_arrows(d, w, h)),
                logo_text("OFF-WHITE", "#e6fff0", -3, gap=0))
    belt = over(R("#f5d000", "#d9b400"), logo_text("INDUSTRIAL", "#141414", 0, gap=0),
                logo_text("INDUSTRIAL", "#141414", 0, face="front", gap=0))
    daypack(m, over(body, P.L_draw(lambda d, w, h, D_, f, img: f == "back" and [
        d.point((x, y), fill=rgb("#9dff3a")) for (x, y) in P_arrow_tint(w, h)])), R("#141814"),
        webbing("#1e2420", "#9dff3a"), metal())
    m.bones[0].add(box((-4.15, 20.6, 2.2), (8.3, 1.2, 4.4), belt))
    charm(m, "tie", (2.4, 13.6, OUT + 1.3), R("#d4202c"), tip_box((.5, 1.6, .3), R("#d4202c", "#a01018")), n=1)
    return m


def lime_arrows(d, w, h):
    """Off-White's crossed arrows, lime, in the middle of the face."""
    c = rgb("#9dff3a")
    cx, cy, r = w / 2, h * .55, w * .34
    for a in (45, 135, 225, 315):
        t = math.radians(a)
        x1, y1 = cx + math.cos(t) * r, cy + math.sin(t) * r
        d.line((cx, cy, x1, y1), fill=c, width=2)
        for sgn in (-1, 1):
            t2 = t + math.pi + sgn * .55
            d.line((x1, y1, x1 + math.cos(t2) * 4, y1 + math.sin(t2) * 4), fill=c, width=2)


def P_arrow_tint(w, h):
    """The arrows logo's tips, lit lime."""
    cx, cy, r = w / 2, h * .55, w * .34
    pts = []
    for a in (45, 135, 225, 315):
        t = math.radians(a)
        pts.append((int(cx + math.cos(t) * r), int(cy + math.sin(t) * r)))
    return pts


def offwhite_quote(n):
    m = Model(n, "Off-White Quote Fuchsia", "bodywear", D=D)
    body = over(R("#e8459a", "#d6307f", "#a8205f"), logo_text("OFF", "#ffffff", 3, scale=2),
                logo_text("WHITE", "#ffffff", 15, scale=1))
    pocket = over(R("#ffffff", "#ececec"), P.L_diag("#141414", 8, 3), logo_text('"BACKPACK"', "#d6307f", 5, gap=0,
                                                                                 box_c="#ffffff"))
    daypack(m, body, R("#a8205f"), webbing("#141414", "#ffffff"), metal(), pocket=pocket)
    charm(m, "tie", (-2.4, 13.6, OUT + 1.3), R("#f5d000"), tip_box((.5, 1.6, .3), R("#f5d000", "#c9a000")), n=1)
    return m


def chrome_denim(n):
    m = Model(n, "Chrome Hearts Dagger Denim", "bodywear", D=D)
    body = over(denim("#3a5a8a"), studs("#d2d7de", 5), A(DAGGER, {"s": "#d2d7de"}, y=6))
    pocket = over(denim("#2f4a74"), A(P.CROSS7, {"#": "#d2d7de"}, y=4))
    daypack(m, body, leather("#1a1a1f"), over(leather("#1a1a1f"), studs("#d2d7de", 3)), metal(), pocket=pocket)
    charm(m, "cross", (2.6, 21.0, OUT + .4), metal(),
          tip_box((1.4, 1.8, .4), metal(), [A(["..s..", "sssss", "..s..", "..s.."], {"s": "#ffffff"}, y=1)]),
          n=3)
    return m


def chrome_cherry(n):
    m = Model(n, "Chrome Hearts Cherry Chomper", "bodywear", D=D)
    body = over(leather("#c42a3a"), A(LIPS, {"r": "#ff6b7a", "w": "#ffffff", "k": "#3a0a10"}, y=7),
                logo_text("MATTY BOY", "#3a0a10", 2, gap=0))
    daypack(m, body, leather("#16161a"), over(leather("#16161a"), studs("#d2d7de", 3)), metal(),
            pocket=over(leather("#a01e2c"), studs("#d2d7de", 4)))
    zip_pulls(m, (-1.5, 1.5), 17.9, OUT + 1.2, metal())
    return m


def supreme_box(n):
    m = Model(n, "Supreme Box Logo Red", "bodywear", D=D)
    body = over(nylon("#d0102c"), logo_text("SUPREME", "#d0102c", 6, box_c="#ffffff"))
    daypack(m, body, R("#a00c22"), webbing("#17171a", "#ffffff"), metal(),
            pocket=over(nylon("#b00e26"), P.L_stitch("#ff5a6a", inset=1)))
    zip_pulls(m, (-1.5, 1.5), 17.9, OUT + 1.2, R("#ffffff"))
    return m


def supreme_alpine(n):
    m = Model(n, "Supreme Cordura Alpine", "bodywear", D=D)
    body = over(nylon("#1f6a5a"), logo_text("SUPREME", "#ffffff", 26, box_c="#d0102c"))
    b = rolltop(m, body, nylon("#1a5a4a"), webbing("#16161a"), metal(), front=webbing("#16161a", "#d0102c"),
                strap=False)
    for (a, c_) in (((-3.2, 14.0), (3.2, 20.0)), ((3.2, 14.0), (-3.2, 20.0))):  # bungee cords crossed on the front
        L = math.hypot(c_[0] - a[0], c_[1] - a[1])
        ang = math.degrees(math.atan2(c_[1] - a[1], c_[0] - a[0]))
        b.add(box((a[0], a[1] - .15, 6.35), (L, .3, .3), R("#16161a"), rot=(0, 0, ang), pivot=(a[0], a[1], 6.5)))
    charm(m, "biner", (-3.0, 22.0, 6.6), metal("#c9ced6"), tip_box((1.0, 1.8, .4), metal("#d0102c")), n=1)
    return m


def arcanic_holo(n):
    m = Model(n, "Arcanic Hologram", "bodywear", D=D)
    rune = A(["....vv....", "...vccv...", "..vcwwcv..", ".vcwwwwcv.", "..vcwwcv..", "...vccv...", "....vv...."],
               {"v": "#8b5cf6", "c": "#5eead4", "w": "#ffffff"}, y=5)
    body = over(R("#1c1c28", "#13131c", "#0c0c14"), rune, logo_text("ARCANIC", "#c4b5fd", -3, gap=0))
    pocket = over(R("#ffffff"), lambda img, f, D_, rnd: holo(img, f, D_, rnd))
    daypack(m, body, R("#13131c"), webbing("#1b1a28", "#8b5cf6"), metal("#c4b5fd"), pocket=pocket)
    glow = m.bone("glow", pivot=(0, 22.6, OUT))
    glow.add(box((-3.0, 22.4, OUT - .05), (6.0, .4, .2), R("#5eead4", edge=None)))
    m.anim("glow", "scale", lambda t: (1, 1 + .8 * max(0, math.sin(t * math.pi * 2)) ** 4, 1), LOOP)
    return m


def arcanic_lunar(n):
    m = Model(n, "Arcanic Lunar Expedition", "bodywear", D=D)
    scene = ["..........yy....", ".........yyyy...", ".........yyyy...", "..........yy....", "................",
             "....m...........", "...mmm......m...", "..mmmmm....mmm..", ".mmmmmmm..mmmmm.", "mmmmmmmmmmmmmmmm"]
    body = over(R("#1d2a5a", "#141e44", "#0c1430"), A(scene, {"y": "#ffe9a0", "m": "#3a4a8a"}, y=6),
                A(["w.....w....w..", "....w.....w..."], {"w": "#ffffff"}, y=2))
    expedition(m, body, nylon("#141e44"), over(R("#e8b04a", "#c48a2a"), P.L_hstripes(["#8a5a10"], [1], start=3,
                                                                                    vertical=True)),
               webbing("#0c1430", "#e8b04a"), metal("#c4b5fd"))
    charm(m, "star", (3.4, 22.8, 7.2), metal("#e8b04a"),
          tip_box((1.2, 1.2, .5), R("#fff3b0", "#ffd23f")), n=2)
    return m


def nike_air(n):
    m = Model(n, "Nike Air Max", "bodywear", D=D)
    body = over(R("#f2f2f2", "#dcdcdc", "#bdbdbd"), A(SWOOSH, {"w": "#16161a"}, y=4),
                logo_text("AIR", "#16161a", 12, scale=1))
    daypack(m, body, R("#bdbdbd"), webbing("#2a2a2e", "#d4ff2f"), metal(),
            pocket=over(R("#9fd8ff", "#5fb0e8", edge=None), P.L_frame("#2a2a2e")))
    zip_pulls(m, (-1.5, 1.5), 17.9, OUT + 1.2, R("#d4ff2f"))
    return m


def bape_shark(n):
    m = Model(n, "BAPE Shark Camo", "bodywear", D=D)
    cam = camo(["#7d8f4e", "#5c6d36", "#a3aa6a", "#3f4a26"], n)
    face = over(cam, A(SHARK, {"w": "#ffffff", "k": "#16161a"}, y=2), A(["kk......kk", "kw......wk"],
                                                                             {"k": "#16161a", "w": "#ffffff"}, y=8),
                logo_text("WGM", "#16161a", -2))
    daypack(m, face, R("#3f4a26"), webbing("#1d1d1d", "#a3aa6a"), metal(), pocket=over(cam, P.L_stitch("#3f4a26")))
    zip_pulls(m, (-1.0, 1.0), 22.6, OUT, R("#ffffff"))
    return m


def tnf(n):
    m = Model(n, "The North Face Expedition", "bodywear", D=D)
    body = over(nylon("#d6202c"), A(HALFDOME, {"w": "#ffffff"}, y=3), logo_text("NORTH", "#ffffff", 11, gap=0))
    expedition(m, body, nylon("#16161a"), over(R("#ffd23f", "#e0a81a"), P.L_hstripes(["#a87a10"], [1], start=3,
                                                                                    vertical=True)),
               webbing("#16161a", "#d6202c"), metal("#3a3f46"))
    charm(m, "biner", (-3.4, 22.6, 7.2), metal("#c9ced6"), tip_box((1.0, 1.8, .4), metal("#3ea0ff")), n=1)
    charm(m, "biner2", (3.4, 22.6, 7.2), metal("#c9ced6"), tip_box((1.0, 1.8, .4), metal("#ffd23f")), n=1, phase=1.7)
    return m


def stone_island(n):
    m = Model(n, "Stone Island Compass", "bodywear", D=D)
    body = over(nylon("#5a6236"), logo_text("STONE", "#e6e0c0", 4, gap=0), logo_text("ISLAND", "#e6e0c0", 11, gap=0))
    badge = faces(nylon("#4a5230"), left=over(R("#16161a"), A(COMPASS, {"y": "#c9e04a", "k": "#16161a"},
                                                                y=2, faces_=("left",))))
    b = rolltop(m, body, nylon("#4a5230"), webbing("#2a2e1a"), metal("#3a3f46"), strap=False)
    b.add(box((-5.0, 15.0, 3.2), (.6, 4.4, 2.6), badge))  # the compass badge on the sleeve side
    for y in (15.4, 18.6):
        b.add(box((-5.1, y, 3.5), (.3, .6, .6), metal("#3a3f46")))
    return m


def palace(n):
    m = Model(n, "Palace Tri-Ferg", "bodywear", D=D)
    body = over(R("#202024", "#16161a", "#0e0e12"), A(TRIFERG, {"w": "#ffffff", "k": "#16161a"}, y=5),
                logo_text("PALACE", "#ffffff", 15, gap=0))
    daypack(m, body, R("#16161a"), webbing("#16161a", "#ffffff"), metal(), pocket=over(R("#16161a"),
                                                                                       P.L_stitch("#5a5a62")))
    zip_pulls(m, (-1.5, 1.5), 17.9, OUT + 1.2, R("#ffffff"))
    return m


def prada(n):
    m = Model(n, "Prada Re-Nylon", "bodywear", D=D)
    plate = A(["wwwwwwwwwwww", ".wkkkkkkkkw.", "..wkkkkkkw..", "...wkkkkw...", "....wkkw....", ".....ww....."],
                {"w": "#e6eaf0", "k": "#16161a"}, y=6)
    body = over(nylon("#1a1a1f"), plate, logo_text("PRADA", "#e6eaf0", 15, gap=0))
    trunk(m, body, metal("#c9ced6"), leather("#1a1a1f", stitch="#3a3a42"), metal("#c9ced6"), w=8.0, h=10.0, d=3.6)
    zip_pulls(m, (-2.0,), 21.6, 2.3 + 3.6, metal("#c9ced6"))
    return m


def balenciaga(n):
    m = Model(n, "Balenciaga Puffer", "bodywear", D=D)
    band = R("#2a2a30", "#1a1a1f", "#101014")
    puffer(m, band, R("#101014"), webbing("#101014", "#ffffff"), metal())
    b = m.bones[0]
    b.add(box((-3.6, 20.6, 7.2), (7.2, 1.6, .2), over(R("#101014"), logo_text("BALENCIAGA", "#ffffff", 1, gap=0))))
    zip_pulls(m, (0.0,), 23.2, 6.7, metal())
    return m


def vlone(n):
    m = Model(n, "Vlone Friends", "bodywear", D=D)
    org = rgb("#f26a1b")

    def v(d, w, h, D_, f, img):
        if f != "back":
            return
        cx = w / 2
        d.polygon([(cx - 12, 4), (cx - 5, 4), (cx, 22), (cx + 5, 4), (cx + 12, 4), (cx + 2, 31), (cx - 2, 31)],
                  fill=org)
        text(img, "VLONE", w // 2, 34, "#ffffff", center=True)

    daypack(m, over(R("#18181c", "#101012"), P.L_draw(v)), R("#101012"), webbing("#19191c", "#f26a1b"), metal(),
            side=R("#f26a1b", "#c4500e"))
    zip_pulls(m, (-1.0, 1.0), 22.6, OUT, R("#f26a1b"))
    return m


ALL = [lv_keepall, lv_trunk, gucci_tiger, gucci_ophidia, adidas_terrex, adidas_trefoil, offwhite_lime, offwhite_quote,
       chrome_denim, chrome_cherry, supreme_box, supreme_alpine, arcanic_holo, arcanic_lunar, nike_air, bape_shark, tnf,
       stone_island, palace, prada, balenciaga, vlone]
