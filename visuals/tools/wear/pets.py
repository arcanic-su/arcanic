"""Companions, made like the mod's first ones: little blocky characters with heads, paws, tails and things they hold,
painted by hand in chunky pixels (2 texels a pixel, colours down ramps). Some move (bob, wag, flap, flicker), some
stand still. They float by the left shoulder; each is built round its own middle."""

import math

from kit import Part, art, bone as kbone, chain, voxels
from wearlib import *

D = 2
AT = (-13.0, 25.5, 1.0)

K, W = "#16161d", "#ffffff"


def pet(n, name, still=False, bob=0.7, speed=2.6, sway=6):
    m = Model(n, name, "pet", D=D, view="pet")
    root = kbone(m, "pet", (0, 0, 0), off=AT)
    if not still:
        m.anim("pet", "position", lambda t: (0, bob * math.sin(t * math.pi * 2), 0), speed)
        if sway:
            m.anim("pet", "rotation", lambda t: (0, sway * math.sin(t * math.pi * 2 + 1.2), 0), speed)
    return m, root


def part(m, name, pivot, parent="pet", s=1):
    return kbone(m, name, pivot, off=AT, parent=parent, s=s)


def R(*cols, **kw):
    return ramp(list(cols), **kw)


EYE = ["wk", "kk", "kk"]  # a cute eye: tall, a shine at the top
EYE2 = ["wk", "kk"]


def eyes(x1, x2, y, rows=EYE, pal=None):
    p = {"w": W, "k": K}
    if pal:
        p.update(pal)
    return [art(rows, p, x=x1, y=y), art(rows, p, x=x2, y=y)]


def blush(x1, x2, y, c="#ff9cb5"):
    return [art(["pp"], {"p": c}, x=x1, y=y), art(["pp"], {"p": c}, x=x2, y=y)]


def side(b, s):
    return b if s > 0 else b.mirror()


# ------------------------------------------------------------------------------------------------ the pets


def yeti(n):
    m, b = pet(n, "Frost Yeti")
    fur = R("#ffffff", "#e8eff8", "#c9d6e6")
    b.add((-2.5, -4.2, -2.0), (5, 5, 4), fur)
    hd = over(fur, art(["..ffffffff..", ".ffffffffff.", ".ffffffffff.", ".ffffffffff.", "..ffffffff..",
                        "...ffffff..."], {"f": "#9fb8d6"}, y=3),
              *eyes(3, 7, 4, pal={"k": "#1b2a44"}), art(["kwwk"], {"k": "#1b2a44", "w": W}, y=8),
              *blush(2, 8, 7, "#d6a6c6"))
    b.add((-3.0, 0.8, -2.8), (6, 5, 5), hd)
    for (x, y, z) in ((-2.8, 5.6, -1.5), (-1.0, 5.8, -0.6), (0.9, 5.6, -1.4), (2.2, 5.3, 0.2), (-2.2, 5.4, 1.0)):
        b.add((x, y, z), (1.2, 1.0, 1.2), R("#ffffff", "#e0e8f2"))
    b.add((-0.5, 6.4, -0.8), (1.0, 1.8, 1.0), R("#e6fbff", "#7fdcff", "#3fb7e0"), rot=(0, 45, 0))
    for s in (1, -1):
        a = part(m, f"arm{s}", (2.6, 0.5, 0), s=s)
        a.add((2.5, -3.6, -1.0), (1.6, 4.4, 2.0), fur)
        a.add((2.6, -4.2, -0.9), (1.4, 0.8, 1.8), R("#9fb8d6"))
        m.anim(a.bone.name, "rotation", (lambda s_: lambda t: (12 * math.sin(t * math.pi * 2 + s_), 0, 0))(s), 2.6)
        side(b, s).add((0.3, -6.0, -1.2), (1.9, 2.0, 2.4), fur)
    return m


def fox(n):
    m, b = pet(n, "Ember Fox", bob=0.5)
    orn = R("#ff8f3d", "#f07b2c", "#c45a16")
    b.add((-1.8, -2.4, -3.0), (3.6, 3.2, 6.0), over(orn, art(["wwwwwww", "wwwwwww"], {"w": "#fff4e6"}, y=-2)))
    hd = over(orn, art(["ww........ww", "www......www", "wwww....wwww", "wwwwwwwwwwww"], {"w": "#fff4e6"}, y=4),
              *eyes(2, 8, 2, rows=EYE2))
    b.add((-2.3, 0.2, -6.4), (4.6, 4.2, 3.8), hd)
    b.add((-1.0, 0.4, -7.6), (2.0, 1.6, 1.2), over(R("#fff7ec", "#f2e2cc"), art(["kk"], {"k": K}, y=0)))
    for s in (1, -1):
        p = side(b, s)
        p.add((0.7, 4.4, -5.6), (1.5, 1.6, 0.9), over(R("#f07b2c", "#f07b2c", "#2b1d14"),
                                                      art(["pp", "pp"], {"p": "#ffb3c4"}, y=1)))
        p.add((0.5, -4.8, -2.6), (1.2, 2.6, 1.2), R("#f07b2c", "#3a2618", "#22160d"))
        p.add((0.5, -4.8, 1.4), (1.2, 2.6, 1.2), R("#f07b2c", "#3a2618", "#22160d"))
    chain(m, "tail", None, "pet", (0, -0.6, 3.0), (0, 0.7, 1.3), 4, 2.2, 1.6, orn, phase=0, amp=(6, 18), speed=1.4,
          off=AT, end_paint=R("#fff7ec", "#ffffff"))
    return m


def panda(n):
    m, b = pet(n, "Bamboo Panda", still=True)
    wh, bk = R("#ffffff", "#f0f0ea", "#d9d9d0"), R("#2a2a30", "#1a1a1f")
    b.add((-2.6, -4.4, -2.0), (5.2, 4.8, 4.4), over(wh, L_hstripes(["#1c1c20"], [3], start=0)))
    hd = over(wh, art(["..kkk..kkk..", ".kkwk..kwkk.", ".kkkk..kkkk.", "..kk....kk..", ".....kk.....",
                       "....k..k...."], {"k": "#1c1c20", "w": W}, y=2), *blush(1, 9, 7, "#ffb3c4"))
    b.add((-3.0, 0.4, -2.6), (6, 5, 5), hd)
    for s in (1, -1):
        p = side(b, s)
        p.add((1.6, 5.0, -1.4), (1.8, 1.6, 1.4), bk)
        p.add((0.6, -4.4, -4.4), (1.8, 1.6, 2.6), bk)
        p.add((2.4, -2.6, -2.8), (1.4, 3.0, 1.6), bk)
    bam = over(R("#8ad35a", "#5fbf4a", "#3f9a34"), L_hstripes(["#2f7a26"], [1], start=4))
    b.add((2.0, -5.0, -3.6), (0.9, 9.0, 0.9), bam, rot=(0, 0, -12), pivot=(2.4, -5, -3.2))
    b.add((2.3, 2.2, -3.8), (2.2, 0.5, 1.2), R("#7cd34a"), rot=(0, 0, 25), pivot=(2.6, 2.4, -3.2))
    return m


def astronaut(n):
    m, b = pet(n, "Little Astronaut", bob=1.0, speed=4.0, sway=18)
    suit = R("#ffffff", "#e6eaf0", "#c3c9d2")
    b.add((-2.0, -4.0, -1.6), (4.0, 4.4, 3.2), over(suit, art(["rr", "bb"], {"r": "#e8323c", "b": "#3ea0ff"}, y=1)))
    visor = art(["..vvvvvvvv..", ".vvvvvvvvvv.", ".vwvvvvvvvv.", ".vwwvvvvvvv.", ".vvvvvvvvvv.", "..vvvvvvvv.."],
                {"v": "#1d3a6b", "w": "#bfe3ff"}, y=2)
    b.add((-2.8, 0.4, -2.8), (5.6, 5.0, 5.6), over(suit, visor))
    b.add((-2.0, 1.2, -3.3), (4.0, 3.0, 0.5), R("#2a5aa8", "#1d3a6b"))  # the visor stands out a little
    b.add((-0.3, 5.4, -0.3), (0.6, 1.4, 0.6), R("#9aa3ad"))
    b.add((-0.6, 6.8, -0.6), (1.2, 1.2, 1.2), R("#ff4f4f", "#c42a2a"))
    b.add((-1.6, -3.4, 1.6), (3.2, 3.6, 1.4), over(R("#d6dbe2", "#9aa3ad"), art(["gg"], {"g": "#3ee67a"}, y=1,
                                                                                faces_=("back",))))
    for s in (1, -1):
        p = side(b, s)
        p.add((2.0, -3.4, -1.0), (1.3, 3.4, 1.8), suit)
        p.add((0.2, -6.0, -1.0), (1.6, 2.0, 2.0), over(suit, L_hstripes(["#ff8a1f"], [1], start=2)))
    return m


def mushroom(n):
    m, b = pet(n, "Mushroom Sprout", sway=0)
    m.anims.clear()
    m.anim("pet", "position", lambda t: (0, 1.4 * abs(math.sin(t * math.pi * 2)), 0), 1.0)
    m.anim("pet", "scale", lambda t: (1 + .06 * math.cos(t * math.pi * 4), 1 - .06 * math.cos(t * math.pi * 4),
                                      1 + .06 * math.cos(t * math.pi * 4)), 1.0)
    stem = R("#fff7ea", "#efe1c6", "#d9c7a3")
    b.add((-1.9, -4.4, -1.9), (3.8, 4.4, 3.8), over(stem, *eyes(1, 5, 1, rows=EYE2), *blush(0, 6, 4),
                                                     art([".k..k.", "..kk.."], {"k": "#3a1f24"}, y=4)))
    spots = art(["..ww......ww..", "..ww..ww..ww..", "......ww......"], {"w": "#fff7ea"}, y=0,
                faces_=("front", "back", "left", "right"))
    top = art(["...ww....", "...ww..ww", "w......ww", "w...ww...", "....ww..."], {"w": "#fff7ea"}, x=2, y=2,
              faces_=("top",))
    b.add((-3.6, 0.0, -3.6), (7.2, 2.4, 7.2), over(R("#ff5a5a", "#d63a3a", "#a82424"), spots))
    b.add((-2.6, 2.4, -2.6), (5.2, 1.4, 5.2), over(R("#ff6a6a", "#d63a3a"), top))
    b.add((-3.2, -0.3, -3.2), (6.4, 0.3, 6.4), R("#e6d2ae"))
    for s in (1, -1):
        side(b, s).add((0.3, -5.2, -1.6), (1.4, 0.8, 2.0), R("#8a5a3a"))
    return m


def wisp(n):
    m, b = pet(n, "Lantern Wisp", bob=1.0, speed=2.2)
    flame = R("#d8fbff", "#7fe6ff", "#2fa8ff", "#1a5fd6", edge=None)
    b.add((-2.0, -2.4, -2.0), (4.0, 4.6, 4.0), over(flame, *eyes(1, 5, 3, rows=EYE2, pal={"k": "#0b1a3a"}),
                                                     art([".kk."], {"k": "#0b1a3a"}, y=6)))
    fl = part(m, "flames", (0, 2.2, 0))
    for (x, z, h) in ((-1.6, -1.2, 2.2), (0.2, -1.4, 3.0), (-0.6, 0.6, 2.6), (1.0, 0.4, 1.8)):
        fl.add((x, 2.2, z), (1.2, h, 1.2), R("#ffffff", "#9ff0ff", "#3fb7e0", edge=None))
    m.anim("flames", "scale", lambda t: (1, .8 + .3 * abs(math.sin(t * math.pi * 4)), 1), 1.0)
    iron, glow = R("#4a4f58", "#2a2e35"), R("#fff6b0", "#ffd23f", "#ff9a1f", edge=None)
    b.add((2.0, -1.4, -0.4), (1.6, 0.6, 0.8), flame)
    b.add((3.0, -4.8, -0.9), (2.0, 0.5, 2.0), iron)
    b.add((3.2, -4.3, -0.7), (1.6, 2.2, 1.6), glow)
    b.add((3.0, -2.1, -0.9), (2.0, 0.5, 2.0), iron)
    b.add((3.7, -1.6, -0.2), (0.6, 0.9, 0.6), iron)
    return m


def penguin(n):
    m, b = pet(n, "Cozy Penguin", still=True)
    bk = R("#2a2e38", "#20232b", "#16181e")
    belly = art(["...wwwwww...", "..wwwwwwww..", ".wwwwwwwwww.", ".wwwwwwwwww.", ".wwwwwwwwww.", ".wwwwwwwwww.",
                 ".wwwwwwwwww.", "..wwwwwwww.."], {"w": "#f8f8f6"}, y=-8)
    face = art(["...wwwwww...", "..wwwwwwww..", "..wkwwwwkw..", "..wkwwwwkw..", "..pwwwwwwp.."],
               {"w": "#f8f8f6", "k": K, "p": "#ffb3c4"}, y=1)
    b.add((-2.4, -4.6, -2.1), (4.8, 7.6, 4.4), over(bk, belly, face))
    b.add((-0.8, 0.0, -3.1), (1.6, 0.8, 1.0), R("#ffb12b", "#e0901a"))
    b.add((-2.55, -1.2, -2.25), (5.1, 1.1, 4.7), over(R("#e8323c", "#b8222c"), L_hstripes(["#ffffff"], [1], start=0)))
    b.add((1.0, -3.6, -2.5), (1.2, 2.6, 0.5), R("#e8323c", "#b8222c"))
    for s in (1, -1):
        p = side(b, s)
        p.add((2.4, -4.0, -1.2), (0.8, 3.8, 2.4), bk, rot=(0, 0, 10), pivot=(2.4, -0.4, 0))
        p.add((0.2, -5.2, -2.8), (1.6, 0.6, 2.2), R("#ffb12b"))
        p.add((2.4, 0.4, -0.9), (0.7, 1.8, 1.8), R("#ff9cc2", "#ff7aae"))
    b.add((-2.4, 2.9, -0.4), (4.8, 0.5, 0.8), R("#ff7aae"))
    return m


def dragon(n):
    m, b = pet(n, "Baby Dragon", bob=0.8)
    sc = R("#7c63f0", "#5a3fd6", "#3f2aa8")
    bel = R("#ffe08a", "#f5c542")
    b.add((-1.8, -2.4, -2.8), (3.6, 3.2, 5.6), over(sc, art(["yyyyyyy", "yyyyyyy"], {"y": "#ffe08a"}, y=-2)))
    hd = over(sc, *eyes(2, 6, 2, rows=EYE, pal={"k": "#2a0f4a"}), *blush(1, 7, 5, "#ff8ad1"))
    b.add((-2.4, 0.4, -6.0), (4.8, 4.0, 4.0), hd)
    b.add((-1.4, 0.6, -7.2), (2.8, 1.8, 1.2), over(R("#b9a6ff", "#9a84f0"), art(["k..k"], {"k": "#2a0f4a"}, y=0)))
    for s in (1, -1):
        p = side(b, s)
        p.add((0.8, 4.2, -4.0), (0.8, 1.6, 0.8), R("#fff1b8", "#ffd76a"), rot=(-20, 0, -15), pivot=(1.2, 4.2, -3.6))
        p.add((0.5, -4.6, -2.4), (1.2, 2.4, 1.3), sc)
        p.add((0.5, -4.6, 1.2), (1.2, 2.4, 1.3), sc)
        w = part(m, f"wing{s}", (1.6, 0.6, 0.2), s=s)
        w.add((1.6, 0.3, -0.3), (4.4, 0.8, 0.8), sc, rot=(0, 0, 28), pivot=(1.6, 0.7, 0.1))
        voxels(w, [(1.8, 0.6), (5.4, 2.6), (6.2, -0.6), (5.0, -0.4), (4.2, -2.0), (3.0, -0.8), (1.8, -1.0)], 0.0, 0.5,
               ["#c9b8ff", "#b39cff", "#9a84f0"], cell=0.6)
        m.anim(w.bone.name, "rotation", (lambda s_: lambda t: (0, 0, 28 * s_ * math.sin(t * math.pi * 2)))(s), 0.7)
    for i in range(3):
        b.add((-0.4, 4.0 - i * 0.2, -4.6 + i * 1.6), (0.8, 0.8, 0.8), bel, rot=(45, 0, 0))
    chain(m, "tail", None, "pet", (0, -1.2, 2.8), (0, -0.3, 1.4), 4, 1.8, 1.0, sc, phase=1, amp=(5, 16), speed=1.6,
          off=AT, end_paint=bel)
    return m


def bunny(n):
    m, b = pet(n, "Carrot Bunny", still=True)
    wh = R("#ffffff", "#f6f0ea", "#e2d8cc")
    b.add((-2.2, -4.4, -2.0), (4.4, 4.2, 4.2), wh)
    hd = over(wh, *eyes(2, 6, 3, rows=EYE), *blush(1, 7, 6),
              art(["..pp..", "..kk.."], {"p": "#ff8fb0", "k": "#5a3a3a"}, y=6))
    b.add((-2.5, -0.2, -2.6), (5.0, 4.4, 4.6), hd)
    for s in (1, -1):
        p = side(b, s)
        p.add((0.4, 4.2, -1.4), (1.4, 4.6, 0.8), over(wh, art(["pp"] * 6, {"p": "#ffb3c7"}, y=2)),
              rot=(0, 0, -10), pivot=(1.1, 4.2, -1))
        p.add((0.4, -5.0, -3.0), (1.6, 0.9, 2.4), wh)
    b.add((-0.9, -3.4, 2.2), (1.8, 1.8, 1.4), R("#ffffff"))
    car = R("#ffa53a", "#ff8a1f", "#e06a10")
    b.add((-0.6, -3.8, -3.6), (1.2, 4.0, 1.2), car, rot=(30, 0, 0), pivot=(0, -3.8, -3))
    b.add((-0.6, -0.6, -1.9), (1.2, 1.4, 0.4), R("#5fbf4a", "#3f9a34"), rot=(30, 0, 0), pivot=(0, -3.8, -3))
    return m


def robot(n):
    m, b = pet(n, "Retro Robot", still=True)
    case, dk = R("#cfd5dc", "#aab2bc", "#7f8894"), R("#4a505a", "#2a2f37")
    screen = art(["kkkkkkkkkk", "kkkkkkkkkk", "kkgkkkkgkk", "kgkgkkgkgk", "kkkkkkkkkk", "kkkggggkkk", "kkkkkkkkkk"],
                 {"k": "#0f1a14", "g": "#4dff8a"}, y=1)
    b.add((-3.0, 0.0, -2.4), (6.0, 4.8, 4.6), over(case, screen))
    b.add((-0.3, 4.8, -0.3), (0.6, 1.8, 0.6), dk)
    b.add((-0.7, 6.6, -0.7), (1.4, 1.4, 1.4), R("#ff6b6b", "#d63a3a"))
    b.add((-2.2, -3.6, -1.8), (4.4, 3.6, 3.6), over(case, art(["r.y.g"], {"r": "#ff4f4f", "y": "#ffd23f",
                                                                      "g": "#4dff8a"}, y=2)))
    b.add((-2.6, -5.4, -2.2), (5.2, 1.8, 4.4), over(dk, L_hstripes(["#6f7780"], [1], start=1, vertical=True)))
    for s in (1, -1):
        p = side(b, s)
        p.add((2.2, -3.0, -0.6), (2.2, 0.8, 0.8), dk)
        p.add((4.2, -3.6, -0.9), (0.9, 1.8, 1.4), case)
        p.add((3.0, 1.4, -0.6), (0.6, 1.8, 1.2), R("#ffd23f", "#e0a81a"))
    return m


def cactus(n):
    m, b = pet(n, "Potted Cactus", still=True)
    pot = over(R("#d9774a", "#c4623a", "#9a4a2a"), L_hstripes(["#e88c5a"], [1], start=1))
    b.add((-2.4, -5.6, -2.4), (4.8, 3.2, 4.8), pot)
    b.add((-2.8, -2.6, -2.8), (5.6, 1.0, 5.6), R("#e88c5a", "#c4623a"))
    b.add((-2.0, -1.8, -2.0), (4.0, 0.3, 4.0), R("#6b4a2b"))
    spines = L_draw(lambda d, w, h, D_, f, img: [d.point((x, y), fill=rgb("#fff7d6")) for x in range(1, w, 3)
                                                 for y in range(1 + (x % 2), h, 3)])
    body = over(R("#6fd36a", "#4cb848", "#2f8a3b"), spines)
    b.add((-1.6, -1.6, -1.6), (3.2, 6.4, 3.2), over(R("#6fd36a", "#4cb848", "#2f8a3b"),
                                                     only("left right back", spines),
                                                     *eyes(1, 4, 4, rows=EYE2), *blush(0, 5, 6),
                                                     art([".kk."], {"k": "#1f4a1f"}, y=7)))
    for s in (1, -1):
        p = side(b, s)
        p.add((1.6, 0.6, -0.7), (1.4, 1.2, 1.4), body)
        p.add((2.4, 0.6, -0.7), (1.2, 2.6, 1.4), body)
    b.add((-1.0, 4.8, -1.0), (2.0, 0.8, 2.0), R("#ff8fc2", "#ff5fa8"))
    b.add((-0.4, 5.6, -0.4), (0.8, 0.6, 0.8), R("#ffe14a"))
    return m


def slime(n):
    m, b = pet(n, "King Slime", sway=0)
    m.anims.clear()
    m.anim("pet", "scale", lambda t: (1 + .09 * math.sin(t * math.pi * 2), 1 - .09 * math.sin(t * math.pi * 2),
                                      1 + .09 * math.sin(t * math.pi * 2)), 1.2)
    g = R("#9dff8a", "#6fe06a", "#48b844")
    b.add((-3.2, -4.4, -3.2), (6.4, 6.0, 6.4), over(g, art(["ww", "w."], {"w": "#d6ffc9"}, x=1, y=1),
                                                     *eyes(3, 8, 4, rows=EYE, pal={"k": "#174a1d"}),
                                                     *blush(2, 9, 7, "#ff9cb5"),
                                                     art(["k..k", ".kk."], {"k": "#174a1d"}, y=8)))
    gold = R("#fff1a0", "#ffd23f", "#d9a520")
    b.add((-2.4, 1.6, -2.4), (4.8, 1.2, 4.8), over(gold, art(["r.b.r"], {"r": "#e0245e", "b": "#2f80ed"}, y=0)))
    for (x, z) in ((-2.4, -2.4), (-0.5, -2.4), (1.4, -2.4), (-2.4, 1.4), (1.4, 1.4), (-0.5, 1.4)):
        b.add((x, 2.8, z), (1.0, 1.2, 1.0), gold)
    return m


def capybara(n):
    m, b = pet(n, "Chill Capybara", still=True)
    fur = R("#b8875a", "#a87648", "#8a5e36")
    b.add((-2.4, -3.4, -3.2), (4.8, 4.4, 6.8), fur)
    face = art(["........", ".kk..kk.", "........", "..dddd..", "..dkkd..", "..dddd.."],
               {"k": "#2b1d14", "d": "#7a5230"}, y=0)
    b.add((-2.0, -1.2, -5.8), (4.0, 3.8, 3.0), over(fur, face))
    for s in (1, -1):
        p = side(b, s)
        p.add((1.2, 2.6, -4.0), (0.9, 0.8, 0.7), R("#6b4a2b"))
        p.add((0.8, -5.0, -2.6), (1.4, 1.8, 1.4), R("#8a5e36", "#6b4a2b"))
        p.add((0.8, -5.0, 1.6), (1.4, 1.8, 1.4), R("#8a5e36", "#6b4a2b"))
    b.add((-1.2, 2.6, -4.9), (2.4, 2.0, 2.4), R("#ffb347", "#ff8a1f"))
    b.add((-0.2, 4.6, -3.9), (0.4, 0.6, 0.4), R("#5a3a22"))
    b.add((0.2, 4.7, -3.9), (1.2, 0.4, 0.6), R("#5fbf4a"))
    b.add((-2.45, -0.6, -1.0), (4.9, 1.0, 2.6), over(R("#5fc6ff", "#3ea0ff"), L_hstripes(["#ffffff"], [1], start=1)))
    return m


def owl(n):
    m, b = pet(n, "Moon Owl", still=True)
    fea = over(R("#8a6440", "#7a5634", "#5a3e24"), L_draw(lambda d, w, h, D_, f, img: [
        d.point((x, y), fill=rgb("#a07850")) for x in range(w) for y in range(h) if (x + y * 2) % 4 == 0]))
    face = art(["..oooo..oooo..", ".oyyyyooyyyyo.", ".oyykyooykyyo.", ".oyyyyooyyyyo.", "..oooobboooo..",
                "......bb......"], {"o": "#f2e2c4", "y": "#ffd23f", "k": K, "b": "#f0a020"}, y=1)
    b.add((-3.0, -3.0, -2.6), (6.0, 7.2, 5.0), over(fea, face, art(["..vvvv..", ".vvvvvv.", ".vvvvvv."],
                                                                    {"v": "#d8b98a"}, y=-4)))
    for s in (1, -1):
        p = side(b, s)
        p.add((1.6, 4.2, -1.6), (1.2, 1.6, 1.2), fea, rot=(0, 0, -20), pivot=(2.2, 4.2, -1))
        p.add((3.0, -2.4, -1.8), (0.8, 4.8, 3.6), fea)
        p.add((0.4, -3.6, -2.4), (1.6, 0.6, 1.4), R("#ffb12b"))
    b.add((-5.0, -4.4, -1.0), (10.0, 1.2, 1.4), R("#7a5634", "#5a3e24"))
    b.add((3.4, -4.4, -2.2), (2.2, 0.5, 1.4), R("#5fbf4a", "#3f9a34"), rot=(0, 30, 0))
    return m


def jelly(n):
    m, b = pet(n, "Moon Jellyfish", bob=1.2, speed=3.2, sway=4)
    bell = R("#efe3ff", "#c6a8ff", "#9a74f0", edge=None)
    b.add((-3.0, 0.0, -3.0), (6.0, 3.4, 6.0), over(bell, *eyes(3, 8, 3, rows=EYE2, pal={"k": "#3b2a66"}),
                                                    *blush(2, 9, 5, "#ff9cd2")))
    b.add((-2.2, 3.4, -2.2), (4.4, 1.2, 4.4), R("#f6efff", "#e2d1ff"))
    b.add((-2.8, -0.4, -2.8), (5.6, 0.4, 5.6), R("#b392f5"))
    for k, (x, z) in enumerate(((-2.0, -2.0), (2.0, -2.0), (-2.0, 2.0), (2.0, 2.0), (0, 0))):
        chain(m, f"t{k}_", None, "pet", (x, -0.4, z), (0, -1.2, 0), 4, 0.9, 0.6,
              R("#efe3ff", "#c6a8ff", edge=None), phase=k * 1.3, amp=(12, 10), speed=2.0, off=AT)
    return m


def cat(n):
    m, b = pet(n, "Shadow Cat", bob=0.5)
    fur = R("#2e2e38", "#22222a", "#17171d")
    b.add((-1.7, -2.2, -2.8), (3.4, 3.0, 5.6), fur)
    hd = over(fur, art(["..........", ".gg....gg.", ".gk....kg.", "..........", "....pp....", "...p..p..."],
                       {"g": "#c58aff", "k": "#3a0a6b", "p": "#ff8fb8"}, y=2))
    b.add((-2.4, 0.2, -6.0), (4.8, 4.0, 3.8), hd)
    b.add((-1.6, -0.2, -3.4), (3.2, 0.6, 0.6), R("#e8323c"))
    b.add((-0.4, -0.9, -3.6), (0.8, 0.8, 0.6), R("#ffe14a", "#e0a81a"))
    for s in (1, -1):
        p = side(b, s)
        p.add((0.8, 4.2, -4.6), (1.4, 1.4, 0.8), over(fur, art(["p", "p"], {"p": "#ff8fb8"}, x="c", y=0)))
        p.add((0.4, -4.6, -2.4), (1.2, 2.6, 1.2), fur)
        p.add((0.4, -4.6, 1.3), (1.2, 2.6, 1.2), fur)
    chain(m, "tail", None, "pet", (0, -0.4, 2.8), (0, 1.3, 0.5), 5, 1.0, 0.8, fur, phase=0, amp=(10, 16), speed=1.8,
          off=AT, curl=-12)
    return m


def golem(n):
    m, b = pet(n, "Pocket Golem", still=True)
    iron = over(R("#e2ddd6", "#cfc8bd", "#a8a092"), L_draw(lambda d, w, h, D_, f, img: [
        d.point((x, y), fill=rgb("#b8b0a2")) for x in range(w) for y in range(h) if (x * 3 + y * 5) % 11 == 0]))
    vine = L_draw(lambda d, w, h, D_, f, img: [d.point((x, y), fill=rgb("#5a8a2a")) for x in range(w)
                                               for y in range(h) if (x + y) % 7 == 0 and y > h // 2])
    b.add((-3.0, -2.8, -2.0), (6.0, 4.4, 4.0), over(iron, vine))
    face = art(["........", "kkkkkkkk", "rk....kr", "...nn...", "...nn..."],
               {"k": "#6b6457", "r": "#c42a2a", "n": "#b8a890"}, y=1)
    b.add((-2.0, 1.6, -2.6), (4.0, 4.0, 3.8), over(iron, face))
    b.add((-0.5, 2.0, -3.4), (1.0, 1.8, 0.8), iron)
    for s in (1, -1):
        p = side(b, s)
        p.add((3.0, -5.0, -1.0), (1.6, 6.4, 2.0), over(iron, vine))
        p.add((0.5, -5.4, -1.2), (2.0, 2.6, 2.4), iron)
    b.add((4.6, -5.2, -1.6), (0.4, 3.0, 0.4), R("#5fbf4a"))
    b.add((4.2, -2.4, -2.0), (1.2, 1.2, 1.2), over(R("#ff5a5a", "#d63a3a"), art(["y"], {"y": "#ffe14a"}, x="c", y=0)))
    return m


def axolotl(n):
    m, b = pet(n, "Axolotl", bob=0.9, speed=2.0)
    pk = R("#ffc2dc", "#ffa6c9", "#f07aa8")
    b.add((-2.6, -1.8, -3.0), (5.2, 3.6, 4.2), over(pk, *eyes(1, 8, 2, rows=EYE2), *blush(0, 9, 4, "#e0578f"),
                                                     art(["k....k", ".kkkk."], {"k": "#5a1a34"}, y=5)))
    gill = R("#ff6fa8", "#e0578f")
    for s in (1, -1):
        p = side(b, s)
        for yy, rz in ((1.0, 30), (0.0, 0), (-1.0, -30)):
            p.add((2.6, yy, -1.6), (1.6, 0.7, 0.6), gill, rot=(0, 0, rz), pivot=(2.6, yy + .35, -1.3))
        p.add((1.6, -2.8, -2.2), (1.0, 1.2, 1.0), pk)
        p.add((1.6, -2.8, 1.6), (1.0, 1.2, 1.0), pk)
    b.add((-1.8, -1.6, 1.2), (3.6, 2.8, 3.4), pk)
    chain(m, "tail", None, "pet", (0, -0.4, 4.4), (0, 0, 1.3), 3, 2.0, 1.2,
          over(R("#ffc2dc", "#ffa6c9"), L_hstripes(["#e0578f"], [1], start=0)), phase=0, amp=(4, 22), speed=1.2,
          off=AT)
    return m


ALL = [yeti, fox, panda, astronaut, mushroom, wisp, penguin, dragon, bunny, robot, cactus, slime, capybara, owl, jelly,
       cat, golem, axolotl]
