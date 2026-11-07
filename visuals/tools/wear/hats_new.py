"""Headwear in the new style: chunky hand-painted pixels (colours down ramps), shapes with depth, and small life where
it suits - ears that twitch, a pompom that bounces, a propeller that turns, gills that wave, lights that pulse. Brand
caps and beanies (painted a little finer so their logos read), gadgets, helmets, masks, character hoods.

The head is x -4..4, y 24..32, z -4..4 (a skin's hat layer reaches 4.5 out)."""

import math

import hats as H
import packs as P
from kit import Part, art, bone as kbone
from wearlib import *
import mat

LOOP = 3.0
_mc = H.mc
H.mc = lambda *a, **k: over(_mc(*a, **k), mat.blotch(amp=.07))


def R(*cols, **kw):
    return ramp(list(cols), **kw)


def hat(n, name, D=2, view="front"):
    return Model(n, name, "hat", D=D, view=view)


def knit(c, rib=None):
    """Knitted: a darker rib every other column, a slight wave."""
    c = C(c)
    r = C(rib) if rib else mul(c, .8)

    def l(img, face, D_, rnd):
        if face in ("top", "bottom"):
            return
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                if x % 2 == 0:
                    px[x, y] = mul(r, rnd.choice((.94, 1, 1.05)))

    return over(R(mul(c, 1.1), c, mul(c, .86), edge=.9), mat.cable(), mat.blotch(amp=.05))


def mesh(c):
    """Trucker mesh: little holes in rows."""
    c = C(c)

    def l(img, face, D_, rnd):
        w, h = img.size
        px = img.load()
        for y in range(1, h, 2):
            for x in range(1 + (y // 2) % 2, w, 2):
                px[x, y] = mul(c, .45)

    return over(R(c, mul(c, .9)), l)


def logo(s, c, y, face="front", gap=1, box_c=None, scale=1):
    def l(img, f, D_, rnd):
        if f != face:
            return
        w, h = img.size
        tw = text_width(s, scale, gap)
        x = (w - tw) // 2
        Y = y if y >= 0 else h + y - 5 * scale
        if box_c:
            from PIL import ImageDraw
            ImageDraw.Draw(img).rectangle((x - 2, Y - 1, x + tw + 1, Y + 5 * scale), fill=C(box_c))
        text(img, s, x, Y, c, scale=scale, gap=gap)

    return l


def panel_seams(img, face, D_, rnd):
    """A six-panel cap's seams: darker lines down the crown, eyelets near the top."""
    w, h = img.size
    px = img.load()
    if face == "top":
        for i in range(w):
            for (x, y) in ((i, i * h // max(1, w)), (i, h - 1 - i * h // max(1, w))):
                if 0 <= y < h:
                    px[x, y] = mul(px[x, y], .75)
        return
    if face == "bottom" or w < 6:
        return
    cols = (w // 2,) if face in ("left", "right") else (w // 3, 2 * w // 3)
    for x in cols:
        for y in range(h):
            px[x, y] = mul(px[x, y], .72)
        if h > 3:
            px[max(0, x - 2), 1] = mul(px[max(0, x - 2), 1], .45)  # an eyelet


def brim_stitch(img, face, D_, rnd):
    w, h = img.size
    px = img.load()
    if face == "bottom":
        for y in range(h):
            for x in range(w):
                px[x, y] = mul(px[x, y], .55)
        return
    if face != "top" or h < 4:
        return
    for k in (1, 3):
        for x in range(w):
            if k < h and x % 2 == 0:
                px[x, h - 1 - k] = mul(px[x, h - 1 - k], .7)


def cap_shape(m, crown, brim, top=None, back=False, button=None):
    """A six-panel cap: the crown, a curved brim (forward, or backward), a button on top."""
    b = m.bone("cap")
    crown = over(crown, mat.twill(1.05), mat.blotch(amp=.05))
    b.add(box((-4.65, 29.5, -4.65), (9.3, 3.1, 9.3), over(crown, panel_seams)))
    b.add(box((-4.1, 32.6, -4.1), (8.2, 0.7, 8.2), over(top or crown, panel_seams)))
    sz = -4.75 if back else 4.6  # the snapback strap at the back (at the front when the cap is turned round)
    b.add(box((-1.6, 29.6, sz), (3.2, .9, .2), over(R("#2a2a30"), art(["k.k.k.k"], {"k": "#0a0a0c"}, y=0,
                                                                       faces_=("back", "front")))))
    b.add(box((-0.6, 33.3, -0.6), (1.2, 0.5, 1.2), button or brim))
    z0, zs = (4.65, 4.2) if back else (-8.85, 4.2)
    b.add(box((-3.4, 29.5, z0), (6.8, 0.55, zs), over(brim, brim_stitch)))
    for s in (1, -1):  # the brim's edges bent down a little: its curve
        Part(b, (0, 0, 0), s).add((3.4, 29.5, z0), (1.25, 0.55, zs), over(brim, brim_stitch), rot=(0, 0, -14),
                                  pivot=(3.4, 29.8, z0 + zs / 2))
    return b


def beanie_shape(m, cuff, body, top, pom=None):
    b = m.bone("beanie")
    fold = L_draw(lambda d, w, h, D_, f, img: f not in ("top", "bottom") and d.line((0, 0, w, 0),
                                                                                   fill=mul(img.getpixel((0, 1)), .7)))
    b.add(box((-4.85, 28.3, -4.85), (9.7, 2.3, 9.7), over(cuff, fold)))
    b.add(box((4.86, 28.6, -0.8), (.15, 1.2, 1.6), R("#ffffff", "#d6d6d6"), only=("right",)))  # a woven tag
    b.add(box((-4.7, 30.6, -4.7), (9.4, 2.8, 9.4), body))
    b.add(box((-4.0, 33.4, -4.0), (8.0, 0.8, 8.0), top))
    if pom:
        p = m.bone("pom", pivot=(0, 34.2, 0), parent="beanie")
        p.add(box((-1.6, 34.0, -1.6), (3.2, 2.8, 3.2), pom))
        m.anim("pom", "position", lambda t: (0, .5 * abs(math.sin(t * math.pi * 2)), 0), LOOP / 2)
        m.anim("pom", "scale", lambda t: (1 + .06 * math.cos(t * math.pi * 4), 1 - .07 * math.cos(t * math.pi * 4),
                                          1 + .06 * math.cos(t * math.pi * 4)), LOOP / 2)
    return b


def bucket_shape(m, crown, brim, band=None):
    crown, brim = over(crown, mat.weave(.94), mat.blotch()), over(brim, mat.weave(.94), mat.blotch())
    b = m.bone("bucket")
    b.add(box((-4.4, 30.4, -4.4), (8.8, 3.0, 8.8), crown))
    b.add(box((-4.0, 33.4, -4.0), (8.0, 0.6, 8.0), crown))
    if band:
        b.add(box((-4.45, 30.4, -4.45), (8.9, 0.8, 8.9), band))
    # the brim, each side sloping down from the crown
    for s in (1, -1):
        Part(b, (0, 0, 0), s).add((4.4, 30.0, -6.4), (2.0, 0.55, 12.8), brim, rot=(0, 0, -16), pivot=(4.4, 30.4, 0))
    b.add(box((-4.4, 30.0, -6.4), (8.8, 0.55, 2.0), brim, rot=(16, 0, 0), pivot=(0, 30.4, -4.4)))
    b.add(box((-4.4, 30.0, 4.4), (8.8, 0.55, 2.0), brim, rot=(-16, 0, 0), pivot=(0, 30.4, 4.4)))
    return b


# ------------------------------------------------------------------------------------------------ brands


def arcanic_cap(n):
    m = hat(n, "Arcanic Eclipse Cap", D=3)
    crown = over(R("#2a2440", "#1d1830", "#120f20"), art(["....vvvvv....", "...vv...vv...", "...vv.v.vv...",
                                                         "...vvvvvvv...", "...vv...vv..."], {"v": "#c4b5fd"}, y=2))
    cap_shape(m, crown, R("#8b5cf6", "#6d3fd6"), button=R("#c4b5fd"))
    return m


def arcanic_beanie(n):
    m = hat(n, "Arcanic Orbit Beanie", D=3)
    cuff = over(knit("#2a2440"), logo("ARCANIC", "#c4b5fd", 2, gap=1))
    beanie_shape(m, cuff, knit("#3a2f66"), knit("#3a2f66"), pom=R("#c4b5fd", "#8b5cf6"))
    return m


def lv_bucket(n):
    m = hat(n, "Louis Vuitton Monogram Bucket", D=3)
    canvas_ = P.lv_canvas()
    bucket_shape(m, canvas_, over(P.lv_canvas(), P.L_frame("#b5763e")), band=R("#b5763e", "#8a5a2a"))
    return m


def gucci_cap(n):
    m = hat(n, "Gucci GG Web Cap", D=3)
    crown = over(P.gg_canvas(), P.L_hstripes(["#1d5a3a", "#b4232b", "#1d5a3a"], [2, 3, 2], start=1))
    cap_shape(m, crown, R("#5a3a26", "#3f2818"), button=R("#e2b54b"))
    return m


def adidas_beanie(n):
    m = hat(n, "Adidas Trefoil Beanie", D=3)
    trefoil = art(["...ww...", "..wwww..", "ww.ww.ww", "wwwwwwww", "........", "wwwwwwww"], {"w": "#ffffff"}, y=0)
    cuff = over(knit("#16161a"), trefoil)
    body = over(knit("#16161a"), P.L_hstripes(["#ffffff", None, "#ffffff", None, "#ffffff"], [1, 1, 1, 1, 1],
                                              start=1))
    beanie_shape(m, cuff, body, knit("#16161a"))
    return m


def supreme_beanie(n):
    m = hat(n, "Supreme Box Beanie", D=3)
    cuff = over(knit("#c4102a"), logo("SUPREME", "#ffffff", 1, box_c="#e8182e"))
    beanie_shape(m, cuff, knit("#d0102c"), knit("#d0102c"), pom=R("#ffffff", "#e6e6e6"))
    return m


def chrome_trucker(n):
    m = hat(n, "Chrome Hearts Trucker", D=3)
    front = faces(mesh("#16161a"), front=over(R("#1c1c22", "#121216"), art(P.CROSS7, {"#": "#d2d7de"}, y=1)))
    b = cap_shape(m, front, R("#16161a"), top=mesh("#16161a"), button=R("#d2d7de"))
    b.add(box((-3.4, 30.05, -8.9), (6.8, 0.2, 0.3), R("#d2d7de")))  # the rope across the brim
    return m


def offwhite_bucket(n):
    m = hat(n, "Off-White Industrial Bucket", D=3)
    crown = over(R("#e6dfc8", "#cfc6aa"), logo("OFF", "#141414", 2))
    brim = over(R("#e6dfc8"), P.L_diag("#141414", 6, 2))
    bucket_shape(m, crown, brim, band=over(R("#f5d000"), logo("IND", "#141414", 0, gap=0)))
    return m


def nike_cap(n):
    m = hat(n, "Nike Swoosh Cap", D=3)
    sw = art(["..........ww", "........www.", "w.....wwww..", "ww.wwwww....", ".wwwww......"], {"w": "#ffffff"},
             y=2)
    cap_shape(m, over(R("#16161a", "#0e0e12"), sw), R("#16161a"), back=True, button=R("#16161a"))
    return m


# ------------------------------------------------------------------------------------------------ gadgets


def vision(n):
    m = hat(n, "Vision Visor")
    glass = over(R("#3a4a5a", "#16202a", "#0a0f14", edge=None), mat.glass())
    b = m.bone("visor")
    b.add(box((-4.5, 25.8, -5.1), (9.0, 4.2, 0.5), R("#e6eaf0", "#b8c0ca")))
    b.add(box((-4.2, 26.0, -5.6), (8.4, 3.8, 0.6), glass))
    H.strap(b, 27.0, "#8a8f98", 1.6)
    for s in (1, -1):
        Part(b, (0, 0, 0), s).add((4.5, 26.2, -4.9), (0.5, 3.0, 1.6), R("#e6eaf0", "#b8c0ca"))
    b.add(box((3.2, 30.0, -5.15), (0.8, 0.8, 0.4), R("#d6dbe2")))  # the crown
    gl = m.bone("glint", pivot=(0, 28, -5.7), parent="visor")
    gl.add(box((-.3, 26.2, -5.75), (.6, 3.4, .1), R("#e6f4ff", edge=None), only=("front",), rot=(0, 0, -20),
               pivot=(0, 28, -5.7)))
    m.anim("glint", "position", lambda t: (-9 + 18 * min(1, max(0, (t - .2) / .35)), 0, 0), LOOP, 48)
    return m


def vr(n):
    m = hat(n, "VR Headset")
    white = R("#ffffff", "#eceef2", "#cfd4db")
    front = over(white, art(["kk.......kk", "kk.......kk", "...........", "...........", "kk.......kk",
                             "kk.......kk"], {"k": "#16161a"}, y=1))
    b = m.bone("vr")
    b.add(box((-4.4, 25.4, -6.8), (8.8, 4.6, 2.2), front))
    b.add(box((-4.2, 25.6, -4.65), (8.4, 4.2, 0.2), R("#2a2a30")))
    H.strap(b, 27.2, "#eceef2", 1.4)
    b.add(box((-0.8, 32.0, -4.0), (1.6, 0.5, 8.0), R("#eceef2", "#cfd4db")))
    b.add(box((-2.0, 26.4, 4.5), (4.0, 2.6, 0.8), white))
    led = m.bone("led", pivot=(0, 25.0, -6.9), parent="vr")
    led.add(box((-3.6, 24.9, -6.9), (7.2, .3, .1), R("#9ff0ff", "#36e3ff", edge=None), only=("front",)))
    m.anim("led", "scale", lambda t: (.3 + .7 * abs(math.sin(t * math.pi * 2)), 1, 1), LOOP)
    return m


def kitty_headset(n):
    m = hat(n, "Kitty Headset")
    pink, glow = R("#ffb3d3", "#ff8fc2", "#e06aa0"), R("#bff8ff", "#5ee6ff", edge=None)
    b = m.bone("phones")
    b.add(box((-4.2, 32.4, -1.0), (8.4, 1.0, 2.0), pink))
    for s in (1, -1):
        p = Part(b, (0, 0, 0), s)
        p.add((4.1, 29.6, -0.8), (0.9, 3.4, 1.6), pink)
        p.add((4.4, 25.6, -2.3), (1.5, 4.4, 4.6), pink)
        p.add((5.85, 26.6, -1.3), (0.3, 2.4, 2.6), glow)
        p.add((1.2, 33.0, -0.7), (2.4, 1.4, 1.2), pink)
        p.add((1.6, 34.4, -0.6), (1.6, 1.2, 1.0), pink)
        led = kbone(m, f"led{s}", (2.4, 34.0, -0.8), parent="phones", s=s)
        led.add((1.7, 33.3, -0.85), (1.4, 1.6, 0.2), glow, only=("front",))
        m.anim(led.bone.name, "scale", (lambda k: lambda t: (1, 1 + .25 * math.sin(t * math.pi * 4 + k), 1))(s),
               LOOP)
    return m


def walkman(n):
    m = hat(n, "Walkman Retro Phones")
    metal = R("#e6eaf0", "#a8b0ba")
    foam = R("#ff9a3a", "#f07a1a", "#c45a0a")
    b = m.bone("phones")
    b.add(box((-4.5, 32.5, -0.4), (9.0, 0.5, 0.8), metal))
    for s in (1, -1):
        p = Part(b, (0, 0, 0), s)
        p.add((4.5, 28.2, -0.3), (0.5, 4.8, 0.6), metal)
        p.add((4.4, 25.8, -1.7), (1.4, 3.4, 3.4), foam)
        p.add((5.8, 26.6, -0.9), (0.3, 1.8, 1.8), metal)
    return m


# ------------------------------------------------------------------------------------------------ helmets


def astronaut(n):
    m = hat(n, "Astronaut Helmet")
    shell = R("#ffffff", "#e6eaf0", "#c3c9d2")
    visor = over(R("#ffe9a0", "#e8b04a", "#a8740f", edge=None), art(["ww........", "w.........", "..........",
                                                                     ".........w"], {"w": "#fff8e0"}, y=1))
    b = m.bone("helmet")
    b.add(box((-5.0, 23.6, -5.0), (10.0, 10.2, 10.0), faces(shell, left=over(shell, art(
        ["rwrwr", "bbbww", "rwrwr"], {"r": "#d6202c", "w": "#ffffff", "b": "#2a4aa8"}, y=6, faces_=("left",))))))
    b.add(box((-3.9, 25.0, -5.5), (7.8, 6.4, 0.5), visor))
    b.add(box((-5.2, 23.0, -5.2), (10.4, 1.0, 10.4), R("#9aa3ad", "#6f7780")))
    for s in (1, -1):
        Part(b, (0, 0, 0), s).add((5.0, 29.0, -2.0), (0.6, 1.6, 1.6), R("#ffe14a", edge=None))
    b.add(box((2.6, 33.8, 1.0), (0.4, 2.4, 0.4), R("#9aa3ad")))
    tip = m.bone("tip", pivot=(2.8, 36.4, 1.2), parent="helmet")
    tip.add(box((2.4, 36.2, .8), (.8, .8, .8), R("#ff4f4f", "#c42a2a", edge=None)))
    m.anim("tip", "scale", lambda t: (1 + .5 * max(0, math.sin(t * math.pi * 6)) ** 8,) * 3, LOOP)
    return m


def racing(n):
    m = hat(n, "Rosso Racing Helmet")
    red = over(R("#ff3a3a", "#d6202c", "#9a1018"), P.L_hstripes(["#ffffff", None, "#ffffff"], [1, 1, 1], start=4,
                                                                 vertical=True))
    b = m.bone("helmet")
    b.add(box((-4.9, 23.6, -4.9), (9.8, 9.8, 9.8), red))
    b.add(box((-4.3, 26.6, -5.25), (8.6, 2.8, 0.4), over(R("#3a4a5a", "#0a0f14", edge=None), mat.glass())))
    gl = m.bone("glint", pivot=(0, 28, -5.3), parent="helmet")
    gl.add(box((-.3, 26.6, -5.3), (.6, 2.8, .05), R("#e6f4ff", edge=None), only=("front",), rot=(0, 0, -25),
               pivot=(0, 28, -5.3)))
    m.anim("glint", "position", lambda t: (-9 + 18 * min(1, max(0, (t - .5) / .3)), 0, 0), LOOP, 48)
    b.add(box((-3.6, 23.4, -5.3), (7.2, 2.6, 0.6), R("#16161a")))
    b.add(box((-3.0, 33.0, 2.6), (6.0, 0.6, 2.4), R("#16161a"), rot=(-12, 0, 0), pivot=(0, 33, 3)))
    b.add(box((-1.0, 33.2, -2.0), (2.0, 0.6, 2.0), R("#ffd23f")))
    return m


# ------------------------------------------------------------------------------------------------ ears and fun


def ears(n, name, fur, inner, tip=None):
    m = hat(n, name)
    b = m.bone("band")
    b.add(box((-4.6, 32.0, -1.0), (9.2, 0.5, 1.4), R("#2a2a30")))
    for s in (1, -1):
        x = 0.6 if s > 0 else -4.4
        e = m.bone(f"ear{s}", pivot=(x + 1.9, 32.4, 0), parent="band", rot=(0, 0, -8 * s))
        face = over(fur, art(["...pp...", "..pppp..", ".pppppp."], {"p": inner}, y=-3))
        e.add(box((x, 32.4, -0.8), (3.8, 1.8, 1.2), face))
        e.add(box((x + 0.6, 34.2, -0.7), (2.6, 1.6, 1.0), over(fur, art(["pp"], {"p": inner}, y=-2))))
        e.add(box((x + 1.2, 35.8, -0.6), (1.4, 1.2, 0.8), tip or fur))
        m.anim(e.name, "rotation", (lambda s_: lambda t: (8 * max(0, math.sin(t * math.pi * 2 + s_ * 1.2)) ** 14, 0,
                                                          -10 * s_ * max(0, math.sin(t * math.pi * 2 + s_)) ** 14))
               (s), LOOP)
    return m


def fox_ears(n):
    return ears(n, "Ember Fox Ears", R("#ff9a4a", "#f07b2c", "#c45a16"), "#fff4e6", tip=R("#2b1d14"))


def cat_ears(n):
    return ears(n, "Lilac Cat Ears", R("#c9a6ff", "#a07ae0", "#7a54c0"), "#ffd6ec")


def propeller(n):
    m = hat(n, "Propeller Cap", D=2)
    panels = over(R("#ffd23f", "#e0a81a"), P.L_hstripes(["#e8323c", "#3ea0ff", "#5fbf4a", None], [3, 3, 3, 3],
                                                         start=0, vertical=True))
    b = cap_shape(m, panels, R("#3ea0ff", "#2a7ad6"), button=R("#e8323c"))
    p = m.bone("prop", pivot=(0, 34.6, 0), parent="cap")
    p.add(box((-0.3, 33.8, -0.3), (0.6, 1.2, 0.6), R("#9aa3ad")))
    p.add(box((-4.2, 34.8, -0.5), (8.4, 0.3, 1.0), R("#e8323c", "#3ea0ff", "#e8323c", edge=None)))
    m.anim("prop", "rotation", lambda t: (0, t * 720, 0), LOOP, 32)
    return m


def bunny_hood(n):
    m = hat(n, "Vanilla Bunny Hood")
    wh = "#f6f0ea"

    def face(img, d, w, band):
        H.eye(img, 4, 2)
        H.eye(img, w - 6, 2)
        img.putpixel((w // 2 - 1, band - 2), C("#ff8fb0"))
        img.putpixel((w // 2, band - 2), C("#ff8fb0"))

    b = H.hood(m, wh, draw=face)
    for s in (1, -1):
        x = 1.0 if s > 0 else -3.0
        e = m.bone(f"ear{s}", pivot=(x + 1, 34.4, 0), parent="hood", rot=(-8, 0, -10 * s))
        e.add(box((x, 34.4, -0.7), (2.0, 6.4, 0.9), over(R(wh, "#e2d8cc"), art(["pp"] * 10, {"p": "#ffb3c7"}, x="c",
                                                                              y=2))))
        m.anim(e.name, "rotation", (lambda s_: lambda t: (12 * math.sin(t * math.pi * 2 + s_ * .9), 0, 0))(s), LOOP)
    return m


def bear_hood(n):
    m = hat(n, "Honey Pilot Bear Hood")
    br = "#8a5a3a"

    def face(img, d, w, band):
        H.eye(img, 4, 3)
        H.eye(img, w - 6, 3)

    b = H.hood(m, br, draw=face)
    b.add(box((-1.8, 30.2, -H.HX - 0.9), (3.6, 2.2, 0.9), over(R("#d9b48a"), art(["kk"], {"k": "#2b1d14"}, y=0))))
    for s in (1, -1):
        Part(b, (0, 0, 0), s).add((2.2, 34.4, -1.4), (2.2, 2.0, 1.4), over(R(br), art(["pp", "pp"], {"p": "#ffb3c4"},
                                                                                     y=1)))
    gl = R("#bff4ff", "#5cc4ef", edge=None)
    b.add(box((-4.85, 33.2, -4.85), (9.7, 0.8, 9.7), R("#5a3a22")))  # the goggles' strap
    for x in (-3.4, 0.4):
        b.add(box((x, 33.0, -5.4), (3.0, 1.6, 0.8), R("#d9a520", "#a8740f")))
        b.add(box((x + 0.5, 33.3, -5.6), (2.0, 1.0, 0.3), gl))
    return m


def axolotl_hood(n):
    m = hat(n, "Sakura Axolotl Hood")
    pk = "#ffb3d3"

    def face(img, d, w, band):
        H.eye(img, 3, 2)
        H.eye(img, w - 5, 2)
        img.putpixel((w // 2 - 2, band - 2), C("#5a1a34"))
        img.putpixel((w // 2 + 1, band - 2), C("#5a1a34"))

    b = H.hood(m, pk, draw=face)
    gill = R("#ff6fa8", "#e0578f")
    for s in (1, -1):
        g = m.bone(f"gill{s}", pivot=(H.HX * s, 31.0, -1.0), parent="hood")
        p = Part(g, (0, 0, 0), s)
        for yy, rz in ((33.0, 35), (31.6, 10), (30.2, -15)):
            p.add((H.HX, yy - .4, -1.6), (2.4, 0.8, 0.7), gill, rot=(0, 0, rz), pivot=(H.HX, yy, -1.2))
        m.anim(g.name, "rotation", (lambda s_: lambda t: (0, 12 * s_ * math.sin(t * math.pi * 2), 0))(s), LOOP)
    return m


def frog_hood(n):
    m = hat(n, "Pond Frog Hood")
    g = "#5fcf5a"

    def face(img, d, w, band):
        for x in range(4, w - 4):
            img.putpixel((x, band - 2), C("#2f8a3b"))
        img.putpixel((4, band - 4), C("#ff9db0"))
        img.putpixel((w - 5, band - 4), C("#ff9db0"))

    b = H.hood(m, g, draw=face)
    for s in (1, -1):
        Part(b, (0, 0, 0), s).add((0.6, 34.2, -3.8), (3.0, 2.6, 2.6), over(R(g), art(["wwwww", "wkkww", "wkkww",
                                                                                      "wwwww"],
                                                                                     {"w": "#ffffff", "k": K}, y=0)))
    return m


K = "#16161d"


def kitsune(n):
    """The fox mask, worn on the side of the head: its icon looks at that side."""
    m = H.kitsune_mask(n)
    m.view = "right"
    return m


# ------------------------------------------------------------------------------------------------ show pieces


def storm_cloud(n):
    """A small storm over the head: a puffy cloud, rain falling from it, a flash of lightning now and then."""
    m = hat(n, "Storm Cloud")
    cloud = R("#ffffff", "#dfe5ee", "#b8c2d0", edge=.92)
    c = m.bone("cloud", pivot=(0, 37, 0))
    for (o, sz) in (((-4.0, 35.6, -2.6), (8.0, 2.4, 5.2)), ((-2.6, 37.4, -2.0), (4.4, 2.0, 4.0)),
                    ((-5.0, 35.8, -1.6), (2.4, 1.8, 3.2)), ((2.8, 36.0, -1.8), (2.6, 2.0, 3.6)),
                    ((0.6, 37.6, -1.4), (2.6, 1.6, 2.8))):
        c.add(box(o, sz, cloud))
    c.add(box((-3.6, 35.4, -2.2), (7.2, 0.4, 4.4), R("#8a95a8")))
    m.anim("cloud", "position", lambda t: (0, .4 * math.sin(t * math.pi * 2), 0), LOOP)
    rain = m.bone("rain", pivot=(0, 35, 0), parent="cloud")
    import random as _r
    r = _r.Random(n)
    for k in range(8):
        rain.add(box((r.uniform(-3.4, 3.0), r.uniform(31.5, 35.0), r.uniform(-1.8, 1.6)), (.35, .9, .35),
                     R("#bfe3ff", "#5fa8ff", edge=None)))
    m.anim("rain", "position", lambda t: (0, -2.4 * ((t * 3) % 1), 0), LOOP, 48)
    bolt = m.bone("bolt", pivot=(1.0, 35.4, 0), parent="cloud")
    for (x, y, rz) in ((1.0, 34.2, 25), (0.4, 33.0, -25), (0.9, 31.8, 25)):
        bolt.add(box((x - .3, y, -.3), (.6, 1.4, .6), R("#ffffff", "#fff36b", edge=None), rot=(0, 0, rz),
                     pivot=(x, y + .7, 0)))
    m.anim("bolt", "scale", lambda t: (1, 1, 1) if .62 < t < .7 or .74 < t < .78 else (0, 0, 0), LOOP, 96)
    return m


def ufo(n):
    """A little flying saucer hovering over the head, its ring of lights going round."""
    m = hat(n, "Mini UFO")
    hull = R("#e6eaf0", "#aab2bc", "#6f7887")
    u = m.bone("ufo", pivot=(0, 36, 0))
    u.add(box((-4.6, 35.4, -4.6), (9.2, 1.0, 9.2), hull))
    u.add(box((-3.4, 34.8, -3.4), (6.8, 0.6, 6.8), R("#6f7887", "#4a505a")))
    u.add(box((-2.4, 36.4, -2.4), (4.8, 1.8, 4.8), R("#d8fbff", "#7fe6ff", "#3fb7e0", edge=None)))
    u.add(box((-1.2, 38.2, -1.2), (2.4, .6, 2.4), R("#bff4ff", edge=None)))
    m.anim("ufo", "position", lambda t: (0, .7 * math.sin(t * math.pi * 2), 0), LOOP)
    m.anim("ufo", "rotation", lambda t: (3 * math.sin(t * math.pi * 2 + 1), 0, 3 * math.sin(t * math.pi * 2)), LOOP)
    ring = m.bone("lights", pivot=(0, 35.9, 0), parent="ufo")
    cols = ["#ff4f4f", "#ffd23f", "#5bff8a", "#5ec8ff"]
    for k in range(8):
        a = math.radians(k * 45)
        x, z = math.cos(a) * 4.4, math.sin(a) * 4.4
        ring.add(box((x - .4, 35.5, z - .4), (.8, .8, .8), R(cols[k % 4], edge=None)))
    m.anim("lights", "rotation", lambda t: (0, t * 360, 0), LOOP, 32)
    return m


def galaxy_halo(n):
    """A tilted ring of stars over the head, two small planets orbiting it."""
    m = hat(n, "Galaxy Halo")
    g = m.bone("galaxy", pivot=(0, 35.5, 0), rot=(12, 0, 8))
    for k in range(16):
        a = math.radians(k * 22.5)
        x, z = math.cos(a) * 4.6, math.sin(a) * 4.6
        c = ["#c9a6ff", "#7af0ff", "#ffffff", "#ff9ad8"][k % 4]
        g.add(box((x - .45, 35.1, z - .45), (.9, .8, .9), R(c, edge=None)))
    m.anim("galaxy", "rotation", lambda t: (0, -t * 360, 0), LOOP * 2, 32)
    pl = m.bone("planets", pivot=(0, 35.5, 0), parent="galaxy")
    pl.add(box((5.0, 34.9, -.7), (1.4, 1.4, 1.4), R("#ffb347", "#e0602a")))
    pl.add(box((4.4, 35.5, -1.3), (2.6, .2, 2.6), R("#ffe9a0", edge=None)))
    pl.add(box((-6.0, 35.0, .2), (1.1, 1.1, 1.1), R("#5ec8ff", "#2a7ad6")))
    m.anim("planets", "rotation", lambda t: (0, t * 720, 0), LOOP * 2, 48)
    return m


def flame_crown(n):
    """A golden band with fire burning up out of it."""
    m = hat(n, "Flame Crown")
    gold = R("#fff1a0", "#ffd23f", "#c98f1a")
    b = m.bone("crown")
    b.add(box((-4.8, 31.4, -4.8), (9.6, 1.8, 9.6), over(gold, art(["r..b..r..b..r..b"], {"r": "#e0245e",
                                                                                         "b": "#2f80ed"}, y=1))))
    fire = R("#fff6c4", "#ffd23f", "#ff7a1a", "#d6300a", edge=None)
    k = 0
    for (x, z) in ((-4.2, -4.2), (-1.4, -4.4), (1.4, -4.2), (3.6, -3.0), (3.6, 1.2), (1.0, 3.6), (-1.8, 3.6),
                   (-4.2, 1.0)):
        f = m.bone(f"flame{k}", pivot=(x + .7, 33.2, z + .7), parent="crown")
        h = 2.4 + (k % 3) * .8
        f.add(box((x, 33.2, z), (1.4, h, 1.4), fire))
        f.add(box((x + .3, 33.2 + h, z + .3), (.8, 1.0, .8), R("#ffd23f", "#ff7a1a", edge=None)))
        m.anim(f.name, "scale", (lambda q: lambda t: (1, .75 + .35 * abs(math.sin(t * math.pi * 4 + q)), 1))(k * .9),
               LOOP)
        k += 1
    return m


def kabuto(n):
    """A samurai's helmet: a dark red lacquered bowl, layered neck guard, a golden crescent crest."""
    m = hat(n, "Samurai Kabuto")
    lac = R("#a8202a", "#7a141c", "#4a0a10")
    plate = over(R("#2a2a30", "#16161a"), L_hstripes(["#a8202a"], [1], start=0))
    gold = R("#fff1a0", "#ffd23f", "#c98f1a")
    b = m.bone("kabuto")
    b.add(box((-4.8, 30.0, -4.8), (9.6, 3.0, 9.6), over(lac, L_draw(lambda d, w, h, D_, f, img: [
        d.line((x, 0, x, h), fill=rgb("#d9a520")) for x in range(2, w, 4)]))))
    b.add(box((-3.6, 33.0, -3.6), (7.2, 1.0, 7.2), lac))
    b.add(box((-.6, 34.0, -.6), (1.2, .8, 1.2), gold))
    for k in range(3):  # the neck guard: plates stepping out over the shoulders
        w = 10.2 + k * 1.0
        b.add(box((-w / 2, 29.0 - k * 1.2, -2.0 - k * .3), (w, 1.2, 7.0 + k * .6), plate, rot=(-10, 0, 0),
                  pivot=(0, 30, 3)))
    b.add(box((-4.9, 29.6, -5.4), (9.8, .6, 1.2), lac))
    for s in (1, -1):
        Part(b, (0, 0, 0), s).add((0.6, 32.6, -5.6), (1.0, 4.6, .4), gold, rot=(0, 0, -28), pivot=(.6, 32.6, -5.4))
    b.add(box((-1.0, 31.6, -5.8), (2.0, 1.6, .5), gold))
    return m


def neon_horns(n):
    """Long curved horns glowing magenta into cyan, the light breathing, sparks off their tips."""
    m = hat(n, "Neon Horns")
    glow = R("#ff4fd8", "#c45cff", "#5ee6ff", edge=None)
    b = m.bone("horns")
    for s in (1, -1):
        h = m.bone(f"horn{s}", pivot=(2.6 * s, 32, -1.6), parent="horns", rot=(0, 0, -12 * s))
        p = Part(h, (0, 0, 0), s)
        segs = [((1.6, 32.0, -2.4), (2.2, 1.6, 2.2), 0), ((2.2, 33.4, -2.1), (1.8, 1.6, 1.8), -18),
                ((3.0, 34.6, -1.8), (1.4, 1.6, 1.4), -36), ((4.0, 35.4, -1.6), (1.0, 1.6, 1.0), -58),
                ((5.0, 35.6, -1.5), (.7, 1.4, .7), -80)]
        for (o, sz, rz) in segs:
            p.add(o, sz, glow, rot=(0, 0, rz), pivot=(o[0] + sz[0] / 2, o[1], o[2] + sz[2] / 2))
        m.anim(h.name, "scale", (lambda q: lambda t: (1 + .05 * math.sin(t * math.pi * 4 + q),) * 3)(s), LOOP)
    sp = m.bone("sparks", pivot=(0, 36, -1.6), parent="horns")
    for (x, y) in ((6.2, 36.8), (-6.4, 37.2), (5.4, 38.2), (-5.6, 38.6)):
        sp.add(box((x, y, -1.6), (.5, .5, .5), R("#ffffff", "#ff9cf0", edge=None)))
    m.anim("sparks", "position", lambda t: (0, 1.6 * t, 0), LOOP / 2)
    return m


def cyber_cat(n):
    """A black visor helmet with LED cat eyes and glowing ear outlines."""
    m = hat(n, "Cyber Cat Helmet")
    shell = R("#2a2a33", "#1a1a22", "#0e0e14")
    led = R("#9ff0ff", "#36e3ff", edge=None)
    b = m.bone("helmet")
    b.add(box((-4.85, 23.6, -4.85), (9.7, 9.6, 9.7), shell))
    b.add(box((-4.0, 25.2, -5.2), (8.0, 4.6, .4), over(R("#08080c", edge=None),
                                                       art(["..........cc....cc..", ".........c..c..c..c.",
                                                            "....................", ".......c........c...",
                                                            "........cccccccc...."], {"c": "#36e3ff"}, x=-1, y=1))))
    for s in (1, -1):
        e = m.bone(f"ear{s}", pivot=(2.6 * s, 33.2, -1), parent="helmet")
        p = Part(e, (0, 0, 0), s)
        p.add((1.2, 33.2, -1.6), (2.8, 2.0, 1.2), shell)
        p.add((1.8, 35.2, -1.4), (1.6, 1.4, .9), shell)
        p.add((1.5, 33.4, -1.7), (2.2, 1.8, .1), led, only=("front",))
        m.anim(e.name, "rotation", (lambda q: lambda t: (0, 0, -9 * q * max(0, math.sin(t * math.pi * 2 + q)) ** 12))
               (s), LOOP)
    pulse = m.bone("pulse", pivot=(0, 27.5, -5.3), parent="helmet")
    pulse.add(box((-4.0, 23.9, -5.0), (8.0, .3, .2), led))
    m.anim("pulse", "scale", lambda t: (.2 + .8 * abs(math.sin(t * math.pi * 2)), 1, 1), LOOP)
    return m


def magician(n):
    """A tall top hat; a white rabbit peeks out of it and ducks back in, sparkles round the brim."""
    m = hat(n, "Magician's Top Hat")
    blk = R("#2a2a33", "#16161c", "#0c0c10")
    b = m.bone("tophat")
    b.add(box((-6.0, 31.6, -6.0), (12.0, .6, 12.0), blk))
    b.add(box((-3.8, 32.2, -3.8), (7.6, 6.2, 7.6), over(blk, L_hstripes(["#a3202f", "#d9b54a"], [3, 1], start=0))))
    b.add(box((-3.4, 38.2, -3.4), (6.8, .2, 6.8), R("#050507")))
    rab = m.bone("rabbit", pivot=(0, 36, 0), parent="tophat")
    wh = R("#ffffff", "#ececec", "#d6d6d6")
    rab.add(box((-1.6, 35.4, -1.4), (3.2, 2.8, 2.6), over(wh, art([".k..k.", "......", "..pp.."],
                                                                  {"k": "#16161d", "p": "#ff9cb5"}, y=1))))
    for x in (-1.3, .4):
        rab.add(box((x, 38.2, -.5), (.9, 2.6, .6), over(wh, art(["p"] * 4, {"p": "#ffb3c7"}, x="c", y=1))))
    m.anim("rabbit", "position", lambda t: (0, -3.2 + 3.2 * max(0, math.sin(t * math.pi * 2)) ** 3, 0), LOOP)
    sp = m.bone("sparkle", pivot=(0, 32, 0), parent="tophat")
    for k in range(6):
        a = math.radians(k * 60)
        sp.add(box((math.cos(a) * 5.4 - .25, 32.4, math.sin(a) * 5.4 - .25), (.5, .5, .5), R("#fff36b", edge=None)))
    m.anim("sparkle", "rotation", lambda t: (0, t * 360, 0), LOOP, 32)
    m.anim("sparkle", "scale", lambda t: (1, 1 + .5 * abs(math.sin(t * math.pi * 6)), 1), LOOP)
    return m


# ------------------------------------------------------------------------------------------------ old ones, more alive


def sf_mask_plus(n):
    m = H.sf_mask(n)
    em = m.bone("embers", pivot=(0, 36, 0))
    for (x, y, z) in ((4.6, 36.4, 3.6), (-4.6, 36.6, 3.4), (5.0, 38.0, 4.4), (-5.2, 37.6, 4.0), (4.2, 39.2, 3.0)):
        em.add(box((x - .3, y, z - .3), (.6, .6, .6), R("#fff1a0", "#ff7a1a", edge=None)))
    m.anim("embers", "position", lambda t: (.3 * math.sin(t * math.pi * 4), 2.2 * t, 0), LOOP)
    eyes = m.bone("eyes", pivot=(0, 30.5, -4.8))
    for x in (-3.4, 1.4):
        eyes.add(box((x, 30.0, -4.85), (2.0, .8, .1), R("#fff1a0", "#ff9a1f", edge=None), only=("front",)))
    m.anim("eyes", "scale", lambda t: (1, .6 + .6 * abs(math.sin(t * math.pi * 3)), 1), LOOP)
    return m


def pumpkin_plus(n):
    m = H.pumpkin(n)
    f = m.bone("candle", pivot=(0, 25, 0))
    f.add(box((-1.2, 24.0, -1.2), (2.4, 4.0, 2.4), R("#fff6c4", "#ffd23f", "#ff7a1a", edge=None)))
    m.anim("candle", "scale", lambda t: (1 + .15 * math.sin(t * math.pi * 8), .8 + .3 * abs(math.sin(t * math.pi * 6)),
                                         1 + .15 * math.sin(t * math.pi * 8)), LOOP)
    return m


def crown_plus(n):
    m = H.crown(n)
    sp = m.bone("glints", pivot=(0, 36, 0))
    for (x, z) in ((-4.4, -4.4), (3.8, -4.4), (-4.4, 3.8), (3.8, 3.8)):
        sp.add(box((x, 36.4, z), (.6, .6, .6), R("#ffffff", edge=None)))
    m.anim("glints", "scale", lambda t: (1 + .6 * max(0, math.sin(t * math.pi * 4)) ** 6,) * 3, LOOP)
    return m


def witch_plus(n):
    m = H.witch(n)
    bat = m.bone("bat", pivot=(0, 36, 0))
    blk = R("#2a2236", "#16101e")
    bat.add(box((6.0, 36.0, -.5), (1.2, 1.0, 1.0), over(blk, art(["r.r"], {"r": "#ff3b5c"}, y=0))))
    for s in (1, -1):
        wg = m.bone(f"batwing{s}", pivot=(6.6, 36.6, 0), parent="bat")
        wg.add(box((6.6 if s > 0 else 4.6, 36.2, -.3), (2.0, .3, 1.2), blk))
        m.anim(wg.name, "rotation", (lambda q: lambda t: (0, 0, 35 * q * math.sin(t * math.pi * 12)))(s), LOOP)
    m.anim("bat", "rotation", lambda t: (0, t * 360, 0), LOOP, 32)
    return m


ALL = [storm_cloud, ufo, galaxy_halo, flame_crown, kabuto, neon_horns, cyber_cat, magician,
       arcanic_cap, arcanic_beanie, lv_bucket, gucci_cap, adidas_beanie, supreme_beanie, chrome_trucker,
       offwhite_bucket, nike_cap, vision, vr, kitty_headset, walkman, H.studio_phones, H.visor, astronaut, racing,
       sf_mask_plus, H.krueger, H.scream, H.hockey, H.balaclava, H.gas_mask, kitsune, H.oni, H.plague,
       H.goggles, bunny_hood, bear_hood, axolotl_hood, frog_hood, H.shark, H.dino, H.panda, H.fox, pumpkin_plus,
       fox_ears, cat_ears, propeller, H.halo, crown_plus, H.mushroom, witch_plus, H.santa]
