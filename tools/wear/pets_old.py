"""Companions in the style of the mod's first ones: little blocky characters painted in chunky pixels, each with its own
place - sitting on the right or the left shoulder (feet on it, as the first Pet 2-4 stood), or floating beside the
head - and its own small life: blinking, breathing, ears and tails moving, wings beating, all in one loop so nothing
stops and jumps. Some only blink and stay still.

Each is built standing on y = 0, facing -z, round x = z = 0; the catalogue entry puts it in its place."""

import math

from kit import Part, art, bone as kbone, chain
from wearlib import *

D = 2
K, W = "#16161d", "#ffffff"
LOOP = 4.0

PLACES = {  # the catalogue's x, y, z (blocks) and scale for each place
    "shoulder_r": (0.45, 0.34, 0.03, 0.8),
    "shoulder_l": (-0.45, 0.34, 0.03, 0.8),
    "float_r": (0.86, 0.42, 0.08, 0.8),
    "float_l": (-0.86, 0.42, 0.08, 0.8),
}


def R(*cols, **kw):
    return ramp(list(cols), **kw)


EYE = ["wk", "kk", "kw"]  # a shine at the top and a little one at the bottom: the eyes look alive
EYE2 = ["wk", "kw"]


def eyes(x1, x2, y, rows=EYE, pal=None):
    p = {"w": W, "k": K}
    if pal:
        p.update(pal)
    return [art(rows, p, x=x1, y=y), art(rows, p, x=x2, y=y)]


def blush(x1, x2, y, c="#ff9cb5"):
    return [art(["pp"], {"p": c}, x=x1, y=y), art(["pp"], {"p": c}, x=x2, y=y)]


def side(p, s):
    return p if s > 0 else p.mirror()


def cheeks(part, head_o, head_s, paint, y=None):
    """Tufts of fur sticking out at both cheeks."""
    yy = head_o[1] + head_s[1] * .25 if y is None else y
    for s in (1, -1):
        side(part, s).add((head_o[0] + head_s[0] - .2, yy, head_o[2] + .4), (.9, 1.1, 1.4), paint,
                          rot=(0, 0, -20), pivot=(head_o[0] + head_s[0], yy, head_o[2] + 1))


def env(t, c, w):
    """A smooth bump: 0 away from c, rising to 1 at c, w wide on each side."""
    d = abs(t - c)
    return math.cos(d / w * math.pi / 2) ** 2 if d < w else 0.0


class Pet:
    def __init__(self, n, name, place, view="pet"):
        self.m = m = Model(n, name, "pet", D=D, view=view)
        m.x, m.y, m.z, m.scale = PLACES[place]
        self.place = place
        self.root = kbone(m, "pet", (0, 0, 0))
        self.floating = place.startswith("float")

    def part(self, name, pivot, parent="pet", s=1, rot=None):
        return kbone(self.m, name, pivot, parent=parent, s=s, rot=rot)

    def anim(self, bone, ch, fn, period=LOOP):
        self.m.anim(bone, ch, fn, period, 48)

    def bob(self, amp=0.8, sway=5):
        """Floating: a slow rise and fall, a little turn."""
        self.anim("pet", "position", lambda t: (0, amp * math.sin(t * math.pi * 2), 0))
        if sway:
            self.anim("pet", "rotation", lambda t: (0, sway * math.sin(t * math.pi * 2 + 1.2), 0))

    def breathe(self, bone="pet", k=0.03):
        self.anim(bone, "scale", lambda t: (1 + k * .5 * math.sin(t * math.pi * 4), 1 + k * math.sin(t * math.pi * 4),
                                            1 + k * .5 * math.sin(t * math.pi * 4)))

    def blink(self, parent, head_o, head_s, eye_row, colour, rows=3, at=0.9):
        """An eyelid of the face's colour that closes over the eyes for a moment once a loop."""
        top = head_o[1] + head_s[1] - eye_row / D
        z = head_o[2] - 0.06
        x0, x1 = head_o[0] + .25, head_o[0] + head_s[0] - .25
        lid = self.part(f"lid_{parent}", ((x0 + x1) / 2, top, z), parent=parent)
        lid.add((x0, top - rows / D, z), (x1 - x0, rows / D, 0.05), R(colour, colour, edge=None), only=("front",))

        def f(t):
            d = abs(t - at)
            return (1, max(0.0, 1 - d / .025) if d < .025 else 0.0, 1)

        self.anim(lid.bone.name, "scale", f)

    def look(self, bone, yaw=10, pitch=4, period=LOOP):
        self.anim(bone, "rotation", lambda t: (pitch * math.sin(t * math.pi * 2 + .7), yaw * math.sin(t * math.pi * 2),
                                               0), period)


# ------------------------------------------------------------------------------------------------ on a shoulder


def fox(n):
    p = Pet(n, "Ember Fox", "shoulder_r")
    orn, wh = R("#ff9a4a", "#f07b2c", "#c45a16"), R("#fff7ec", "#f2e2cc")
    b = p.root
    b.add((-1.6, 0.0, -1.2), (3.2, 3.4, 3.2), over(orn, art(["wwww", "wwww", "wwww"], {"w": "#fff4e6"}, y=0)))
    for s in (1, -1):
        side(b, s).add((1.2, 0.0, -0.6), (1.2, 2.0, 2.4), orn)
        side(b, s).add((0.3, 0.0, -2.0), (1.0, 1.4, 1.0), R("#3a2618", "#22160d"))
    hd = p.part("head", (0, 3.2, -0.2))
    face = over(orn, art(["ww........ww", "www......www", "wwwwwwwwwwww"], {"w": "#fff4e6"}, y=4),
                *eyes(2, 8, 2, rows=EYE2))
    hd.add((-2.2, 3.2, -2.4), (4.4, 3.8, 3.4), face)
    hd.add((-1.0, 3.4, -3.5), (2.0, 1.5, 1.1), over(wh, art(["kk"], {"k": K}, y=0)))
    cheeks(hd, (-2.2, 3.2, -2.4), (4.4, 3.8, 3.4), wh)
    for s in (1, -1):
        e = p.part(f"ear{s}", (1.3, 7.0, -1.0), parent="head", s=s)
        e.add((0.6, 7.0, -1.4), (1.5, 1.8, 0.9), over(R("#f07b2c", "#f07b2c", "#2b1d14"),
                                                      art(["pp", "pp"], {"p": "#ffb3c4"}, y=1)))
        p.anim(e.bone.name, "rotation", (lambda s_: lambda t: (0, 0, -12 * s_ * max(0, math.sin(t * math.pi * 2 + s_))
                                                               ** 12))(s))
    chain(p.m, "tail", None, "pet", (0.8, 0.8, 1.8), (0.8, 0.5, -0.2), 4, 2.0, 1.4, orn, phase=0, amp=(4, 12),
          speed=LOOP / 2, end_paint=wh, curl=22)
    p.blink("head", (-2.2, 3.2, -2.4), (4.4, 3.8, 3.4), 2, "#f07b2c", rows=2)
    p.look("head", yaw=12)
    p.breathe()
    # sniffing: the head dips and comes up twice, the tail wags hard for a moment
    p.anim("head", "rotation", lambda t: (14 * env(t, .3, .12) * abs(math.sin(t * 40)), 12 * math.sin(t * math.pi * 2),
                                          6 * env(t, .7, .1)))
    return p.m


def owl(n):
    p = Pet(n, "Moon Owl", "shoulder_l")
    fea = over(R("#8a6440", "#7a5634", "#5a3e24"), L_draw(lambda d, w, h, D_, f, img: [
        d.point((x, y), fill=rgb("#a07850")) for x in range(w) for y in range(h) if (x + y * 2) % 4 == 0]))
    b = p.root
    b.add((-2.2, 0.4, -1.9), (4.4, 3.2, 3.8), over(fea, art([".vvvvvv.", "vvvvvvvv", ".vvvvvv."], {"v": "#d8b98a"},
                                                             y=1)))
    hd = p.part("head", (0, 3.6, 0))
    face = art(["..oooo..oooo..", ".oyyyyooyyyyo.", ".oyykyooykyyo.", ".oyyyyooyyyyo.", "..oooobboooo..",
                "......bb......"], {"o": "#f2e2c4", "y": "#ffd23f", "k": K, "b": "#f0a020"}, y=0)
    hd.add((-2.6, 3.6, -2.2), (5.2, 3.6, 4.2), over(fea, face))
    for s in (1, -1):
        side(hd, s).add((1.4, 7.2, -1.4), (1.2, 1.4, 1.0), fea, rot=(0, 0, -20), pivot=(2, 7.2, -1))
        w = p.part(f"wing{s}", (2.2, 3.4, 0), s=s)
        w.add((2.2, 0.6, -1.6), (0.8, 3.2, 3.2), fea)
        p.anim(w.bone.name, "rotation", (lambda s_: lambda t: (0, 0, 22 * s_ * max(0, math.sin(t * math.pi * 4)) ** 16))
               (s))
        side(b, s).add((0.3, 0.0, -2.1), (1.4, 0.5, 1.2), R("#ffb12b"))
    p.blink("head", (-2.6, 3.6, -2.2), (5.2, 3.6, 4.2), 1, "#f2e2c4", rows=3)
    p.anim("head", "rotation", lambda t: (0, 14 * math.sin(t * math.pi * 2), 10 * math.sin(t * math.pi * 2 + 2)))
    # an owl's turn: the head swings far round and back
    p.anim("head", "rotation", lambda t: (0, 50 * env(t, .45, .14) + 6 * math.sin(t * math.pi * 2),
                                          10 * math.sin(t * math.pi * 2 + 2)))
    return p.m


def cat(n):
    p = Pet(n, "Shadow Cat", "shoulder_r")
    fur = R("#2e2e38", "#22222a", "#17171d")
    b = p.root
    b.add((-1.5, 0.0, -1.0), (3.0, 3.4, 2.8), fur)
    for s in (1, -1):
        side(b, s).add((0.2, 0.0, -1.8), (1.0, 1.6, 1.0), fur)
        side(b, s).add((1.1, 0.0, -0.2), (1.0, 1.8, 2.0), fur)
    b.add((-1.4, 2.6, -1.25), (2.8, 0.5, 0.4), R("#e8323c"))
    b.add((-0.35, 2.0, -1.45), (0.7, 0.7, 0.5), R("#ffe14a", "#e0a81a"))
    hd = p.part("head", (0, 3.3, 0))
    hd.add((-2.0, 3.3, -2.0), (4.0, 3.2, 3.2), over(fur, art(["........", ".gg..gg.", ".gk..kg.", "........",
                                                               "...pp...", "..p..p.."],
                                                              {"g": "#c58aff", "k": "#3a0a6b", "p": "#ff8fb8"}, y=0)))
    for s in (1, -1):
        side(hd, s).add((0.6, 6.5, -1.4), (1.3, 1.3, 0.8), over(fur, art(["p", "p"], {"p": "#ff8fb8"}, x="c", y=0)))
    cheeks(hd, (-2.0, 3.3, -2.0), (4.0, 3.2, 3.2), fur)
    chain(p.m, "tail", None, "pet", (0, 0.4, 1.8), (0, 1.3, 0.4), 5, .9, .7, fur, phase=0, amp=(8, 18), speed=LOOP / 2,
          curl=-14)
    p.blink("head", (-2.0, 3.3, -2.0), (4.0, 3.2, 3.2), 1, "#22222a", rows=2)
    p.look("head", yaw=14, pitch=6)
    p.breathe()
    # a stretch: the head goes up, then a slow blink-and-look
    p.anim("head", "rotation", lambda t: (-16 * env(t, .35, .12), 14 * math.sin(t * math.pi * 2),
                                          8 * env(t, .7, .1)))
    return p.m


def axolotl(n):
    p = Pet(n, "Axolotl", "shoulder_l")
    pk = R("#ffc2dc", "#ffa6c9", "#f07aa8")
    b = p.root
    b.add((-1.6, 0.0, -1.6), (3.2, 2.0, 4.6), pk)
    hd = p.part("head", (0, 1.0, -1.6))
    hd.add((-2.4, 0.2, -4.4), (4.8, 2.8, 2.8), over(pk, *eyes(1, 7, 1, rows=EYE2), *blush(0, 8, 3, "#e0578f"),
                                                     art(["k....k", ".kkkk."], {"k": "#5a1a34"}, y=4)))
    gill = R("#ff6fa8", "#e0578f")
    for s in (1, -1):
        g = p.part(f"gill{s}", (2.4, 1.8, -3.0), parent="head", s=s)
        for yy, rz in ((2.6, 35), (1.7, 5), (0.8, -25)):
            g.add((2.4, yy - .3, -3.2), (1.6, 0.6, 0.5), gill, rot=(0, 0, rz), pivot=(2.4, yy, -3.0))
        p.anim(g.bone.name, "rotation", (lambda s_: lambda t: (0, 10 * s_ * math.sin(t * math.pi * 4), 0))(s))
        side(b, s).add((1.6, 0.0, -1.0), (0.9, 0.8, 0.9), pk)
        side(b, s).add((1.6, 0.0, 1.8), (0.9, 0.8, 0.9), pk)
    chain(p.m, "tail", None, "pet", (0, 1.0, 3.0), (0, 0, 1.2), 3, 1.8, 1.0,
          over(R("#ffc2dc", "#ffa6c9"), L_hstripes(["#e0578f"], [1], start=0)), amp=(3, 20), speed=LOOP / 2)
    p.blink("head", (-2.4, 0.2, -4.4), (4.8, 2.8, 2.8), 1, "#ffb3d3", rows=2)
    # a happy wiggle of the whole body
    p.anim("pet", "rotation", lambda t: (0, 14 * env(t, .5, .15) * math.sin(t * 60), 0))
    return p.m


def mushroom(n):
    p = Pet(n, "Mushroom Sprout", "shoulder_r")
    p.anim("pet", "position", lambda t: (0, 0.7 * abs(math.sin(t * math.pi * 2)), 0), LOOP / 2)
    p.anim("pet", "scale", lambda t: (1 + .05 * math.cos(t * math.pi * 4), 1 - .05 * math.cos(t * math.pi * 4),
                                      1 + .05 * math.cos(t * math.pi * 4)), LOOP / 2)
    stem = R("#fff7ea", "#efe1c6", "#d9c7a3")
    b = p.root
    b.add((-1.8, 0.8, -1.8), (3.6, 4.0, 3.6), over(stem, *eyes(1, 5, 1, rows=EYE2), *blush(0, 6, 4),
                                                    art([".k..k.", "..kk.."], {"k": "#3a1f24"}, y=4)))
    spots = art(["..ww......ww..", "..ww..ww..ww..", "......ww......"], {"w": "#fff7ea"}, y=0,
                faces_=("front", "back", "left", "right"))
    top = art(["...ww....", "...ww..ww", "w......ww", "w...ww...", "....ww..."], {"w": "#fff7ea"}, x=2, y=2,
              faces_=("top",))
    b.add((-3.4, 4.8, -3.4), (6.8, 2.2, 6.8), over(R("#ff5a5a", "#d63a3a", "#a82424"), spots))
    b.add((-2.4, 7.0, -2.4), (4.8, 1.2, 4.8), over(R("#ff6a6a", "#d63a3a"), top))
    b.add((-3.0, 4.5, -3.0), (6.0, 0.3, 6.0), R("#e6d2ae"))
    for s in (1, -1):
        side(b, s).add((0.3, 0.0, -1.6), (1.3, 0.8, 2.0), R("#8a5a3a"))
    p.blink("pet", (-1.8, 0.8, -1.8), (3.6, 4.0, 3.6), 1, "#fff2e0", rows=2)
    return p.m


def penguin(n):
    p = Pet(n, "Cozy Penguin", "shoulder_l")
    bk = R("#2a2e38", "#20232b", "#16181e")
    b = p.root
    belly = art(["...wwwwww...", "..wwwwwwww..", ".wwwwwwwwww.", ".wwwwwwwwww.", ".wwwwwwwwww.", ".wwwwwwwwww.",
                 "..wwwwwwww.."], {"w": "#f8f8f6"}, y=-7)
    face = art(["...wwwwww...", "..wwwwwwww..", "..wkwwwwkw..", "..wkwwwwkw..", "..pwwwwwwp.."],
               {"w": "#f8f8f6", "k": K, "p": "#ffb3c4"}, y=1)
    b.add((-2.4, 0.6, -2.1), (4.8, 7.4, 4.4), over(bk, belly, face))
    b.add((-0.8, 5.0, -3.1), (1.6, 0.8, 1.0), R("#ffb12b", "#e0901a"))
    b.add((-2.55, 3.8, -2.25), (5.1, 1.1, 4.7), over(R("#e8323c", "#b8222c"), L_hstripes(["#ffffff"], [1], start=0)))
    b.add((1.0, 1.4, -2.5), (1.2, 2.6, 0.5), R("#e8323c", "#b8222c"))
    for s in (1, -1):
        f = p.part(f"flip{s}", (2.4, 4.4, 0), s=s)
        f.add((2.4, 1.0, -1.2), (0.8, 3.6, 2.4), bk, rot=(0, 0, 10), pivot=(2.4, 4.4, 0))
        side(b, s).add((0.2, 0.0, -2.8), (1.6, 0.6, 2.2), R("#ffb12b"))
        side(b, s).add((2.4, 5.4, -0.9), (0.7, 1.8, 1.8), R("#ff9cc2", "#ff7aae"))
    b.add((-2.4, 7.9, -0.4), (4.8, 0.5, 0.8), R("#ff7aae"))
    p.blink("pet", (-2.4, 0.6, -2.1), (4.8, 7.4, 4.4), 2, "#f8f8f6", rows=2)
    # waddling from foot to foot, flippers flapping at the end
    p.anim("pet", "rotation", lambda t: (0, 6 * math.sin(t * math.pi * 2), 9 * math.sin(t * math.pi * 6) *
                                         (1 - env(t, .85, .12))))
    for s_ in (1, -1):
        p.anim(f"flip{s_}", "rotation", (lambda q: lambda t: (0, 0, 35 * q * env(t, .85, .1) *
                                                              abs(math.sin(t * 50))))(s_))
    return p.m


def cactus(n):
    p = Pet(n, "Potted Cactus", "shoulder_r")
    b = p.root
    b.add((-2.2, 0.0, -2.2), (4.4, 3.0, 4.4), over(R("#d9774a", "#c4623a", "#9a4a2a"), L_hstripes(["#e88c5a"], [1],
                                                                                                  start=1)))
    b.add((-2.6, 3.0, -2.6), (5.2, 0.9, 5.2), R("#e88c5a", "#c4623a"))
    b.add((-1.8, 3.6, -1.8), (3.6, 0.3, 3.6), R("#6b4a2b"))
    spines = L_draw(lambda d, w, h, D_, f, img: [d.point((x, y), fill=rgb("#fff7d6")) for x in range(1, w, 3)
                                                 for y in range(1 + (x % 2), h, 3)])
    body = over(R("#6fd36a", "#4cb848", "#2f8a3b"), spines)
    b.add((-1.5, 3.9, -1.5), (3.0, 5.6, 3.0), over(R("#6fd36a", "#4cb848", "#2f8a3b"), only("left right back", spines),
                                                   *eyes(1, 3, 3, rows=EYE2), *blush(0, 4, 5),
                                                   art([".kk."], {"k": "#1f4a1f"}, y=6)))
    for s in (1, -1):
        side(b, s).add((1.5, 5.6, -0.6), (1.2, 1.1, 1.2), body)
        side(b, s).add((2.2, 5.6, -0.6), (1.1, 2.4, 1.2), body)
    b.add((-0.9, 9.5, -0.9), (1.8, 0.8, 1.8), R("#ff8fc2", "#ff5fa8"))
    b.add((-0.35, 10.3, -0.35), (0.7, 0.5, 0.7), R("#ffe14a"))
    p.blink("pet", (-1.5, 3.9, -1.5), (3.0, 5.6, 3.0), 3, "#5fc65a", rows=2)
    # a wobble, as if someone tapped the pot, dying away
    p.anim("pet", "rotation", lambda t: (0, 0, 9 * math.exp(-8 * ((t - .2) % 1)) * math.sin(((t - .2) % 1) * 40)))
    return p.m


def bunny(n):
    p = Pet(n, "Carrot Bunny", "shoulder_l")
    wh = R("#ffffff", "#f6f0ea", "#e2d8cc")
    b = p.root
    b.add((-2.0, 0.0, -1.6), (4.0, 3.6, 3.6), wh)
    hd = p.part("head", (0, 3.6, 0))
    hd.add((-2.3, 3.4, -2.2), (4.6, 4.0, 4.0), over(wh, *eyes(2, 6, 2, rows=EYE), *blush(1, 7, 5),
                                                     art(["..pp..", "..kk.."], {"p": "#ff8fb0", "k": "#5a3a3a"}, y=5)))
    for s in (1, -1):
        e = p.part(f"ear{s}", (1.0, 7.4, -0.6), parent="head", s=s)
        e.add((0.3, 7.4, -1.0), (1.4, 4.2, 0.8), over(wh, art(["pp"] * 6, {"p": "#ffb3c7"}, y=1)),
              rot=(0, 0, -10), pivot=(1.0, 7.4, -0.6))
        p.anim(e.bone.name, "rotation", (lambda s_: lambda t: (10 * max(0, math.sin(t * math.pi * 2 + s_ * .8)) ** 10,
                                                               0, 0))(s))
        side(b, s).add((0.3, 0.0, -2.6), (1.4, 0.8, 2.0), wh)
    b.add((-0.8, 0.6, 1.9), (1.6, 1.6, 1.2), R("#ffffff"))
    b.add((-0.5, 0.2, -3.0), (1.0, 3.4, 1.0), R("#ffa53a", "#ff8a1f", "#e06a10"), rot=(25, 0, 0), pivot=(0, 0.2, -2.5))
    b.add((-0.5, 3.0, -1.6), (1.0, 1.2, 0.4), R("#5fbf4a", "#3f9a34"), rot=(25, 0, 0), pivot=(0, 0.2, -2.5))
    p.blink("head", (-2.3, 3.4, -2.2), (4.6, 4.0, 4.0), 2, "#f6f0ea", rows=3)
    p.breathe()
    # the nose twitches; the head turns to listen
    nose = p.part("nose", (0, 5.0, -2.3), parent="head")
    nose.add((-.4, 4.4, -2.35), (.8, .5, .1), R("#ff8fb0"), only=("front",))
    p.anim("nose", "scale", lambda t: (1 + .5 * abs(math.sin(t * 30)) * env(t, .3, .1), 1, 1))
    p.anim("head", "rotation", lambda t: (0, 22 * env(t, .6, .14) - 10 * env(t, .85, .08), 5 * env(t, .6, .14)))
    return p.m


def ninja_frog(n):
    p = Pet(n, "Ninja Frog", "shoulder_r")
    g = R("#7ad35a", "#5fbf4a", "#3f9a34")
    b = p.root
    b.add((-2.2, 0.0, -1.8), (4.4, 3.0, 3.8), over(g, art(["..yyyy..", ".yyyyyy.", ".yyyyyy."], {"y": "#e8f0a0"},
                                                           y=-3)))
    b.add((-2.4, 3.0, -2.2), (4.8, 1.0, 4.2), over(R("#2a2a33"), art(["rrrrrrrrrr"], {"r": "#d6202c"}, y=0)))
    for s in (1, -1):
        e = side(b, s)
        e.add((0.4, 3.6, -1.9), (1.8, 1.8, 1.8), over(g, art(["....", ".wk.", ".kk.", "...."], {"w": W, "k": K}, y=0)))
        e.add((0.6, 0.0, -2.6), (1.4, 0.6, 1.2), g)
        e.add((1.8, 0.0, -0.6), (1.2, 1.6, 2.4), g)
    b.add((-1.4, 1.4, -1.85), (2.8, 0.3, 0.1), R("#2a5a1c"), only=("front",))
    t = p.part("tails", (0, 3.5, 2.0))
    for k, (x, rz) in enumerate(((-0.4, 18), (0.4, -14))):
        t.add((x - .4, 1.0, 2.0), (0.8, 2.6, 0.3), R("#d6202c", "#a01018"), rot=(25, 0, rz), pivot=(x, 3.5, 2.0))
    p.anim("tails", "rotation", lambda t_: (10 * math.sin(t_ * math.pi * 4), 6 * math.sin(t_ * math.pi * 2), 0))
    p.anim("pet", "scale", lambda t_: (1 + .03 * math.sin(t_ * math.pi * 4), 1, 1 + .03 * math.sin(t_ * math.pi * 4)))
    # the tongue: out and back, quick as anything
    tg = p.part("tongue", (0, 1.5, -1.9))
    tg.add((-.3, 1.3, -5.2), (.6, .35, 3.3), R("#ff6f8a", "#e04060", edge=None))
    tg.add((-.5, 1.1, -5.6), (1.0, .8, .6), R("#ff6f8a"))
    p.anim("tongue", "scale", lambda t: (min(1.0, 3 * env(t, .62, .05)), min(1.0, 3 * env(t, .62, .05)),
                                         env(t, .62, .05)))
    return p.m


def panda(n):
    p = Pet(n, "Bamboo Panda", "shoulder_l")
    wh, bk = R("#ffffff", "#f0f0ea", "#d9d9d0"), R("#2a2a30", "#1a1a1f")
    b = p.root
    b.add((-2.4, 0.0, -1.8), (4.8, 4.0, 4.0), over(wh, L_hstripes(["#1c1c20"], [3], start=0)))
    hd = p.part("head", (0, 4.0, 0))
    hd.add((-2.7, 3.8, -2.4), (5.4, 4.4, 4.4), over(wh, art(["..kk...kk..", ".kwk...kwk.", ".kkk...kkk.", "....kk.....",
                                                             "...k..k...."], {"k": "#1c1c20", "w": W}, y=2),
                                                     *blush(1, 8, 6, "#ffb3c4")))
    for s in (1, -1):
        side(hd, s).add((1.5, 8.1, -1.2), (1.6, 1.4, 1.2), bk)
        side(b, s).add((0.5, 0.0, -3.4), (1.6, 1.4, 2.0), bk)
        side(b, s).add((2.2, 1.4, -2.4), (1.2, 2.6, 1.4), bk)
    b.add((1.8, 0.0, -3.4), (0.8, 8.0, 0.8), over(R("#8ad35a", "#5fbf4a", "#3f9a34"), L_hstripes(["#2f7a26"], [1],
                                                                                                  start=4)),
          rot=(0, 0, -10), pivot=(2.2, 0, -3))
    b.add((2.2, 6.4, -3.6), (2.0, 0.4, 1.0), R("#7cd34a"), rot=(0, 0, 25), pivot=(2.4, 6.6, -3.2))
    p.blink("head", (-2.7, 3.8, -2.4), (5.4, 4.4, 4.4), 3, "#1c1c20", rows=2)
    # munching: the head nods into the bamboo twice
    p.anim("head", "rotation", lambda t: (12 * (env(t, .3, .07) + env(t, .45, .07)), 8 * math.sin(t * math.pi * 2),
                                          0))
    return p.m


def knight(n):
    p = Pet(n, "Pocket Knight", "shoulder_l")
    steel, dark = R("#e6eaf0", "#b8c0ca", "#7f8894"), R("#4a505a", "#2a2f37")
    b = p.root
    b.add((-1.6, 1.6, -1.2), (3.2, 3.0, 2.4), over(steel, art(["..rr..", ".rrrr.", "..rr.."], {"r": "#c42a2a"}, y=1)))
    for s in (1, -1):
        side(b, s).add((0.2, 0.0, -1.0), (1.3, 1.8, 2.0), dark)
    hd = p.part("head", (0, 4.6, 0))
    hd.add((-2.2, 4.6, -2.2), (4.4, 4.2, 4.4), over(steel, art(["..........", "..........", "kkkkkkkkkk",
                                                                ".kkk..kkk.", "..........", "....kk....",
                                                                "....kk...."], {"k": "#1a1c20"}, y=1)))
    p.anim("head", "rotation", lambda t: (0, 8 * math.sin(t * math.pi * 2), 0))
    plume = p.part("plume", (0, 8.8, 0), parent="head")
    for k in range(3):
        plume.add((-0.4, 8.8 + k * 0.6, 0.2 + k * 0.9), (0.8, 1.2, 1.2), R("#ff4f4f", "#c42a2a"),
                  rot=(-20 - k * 15, 0, 0), pivot=(0, 8.8, 0.6))
    p.anim("plume", "rotation", lambda t: (6 * math.sin(t * math.pi * 4), 0, 4 * math.sin(t * math.pi * 2)))
    sh = p.part("shield", (-2.2, 3.4, -1.0))
    sh.add((-3.0, 1.2, -1.8), (0.6, 3.4, 2.8), over(R("#3a6ad6", "#2a4aa8"), art(["yy", "yy", "yy"], {"y": "#ffd23f"},
                                                                               x="c", y=1, faces_=("left",))))
    p.anim("shield", "rotation", lambda t: (4 * math.sin(t * math.pi * 2), 0, 3 * math.sin(t * math.pi * 2 + 1)))
    b.add((1.6, 1.8, -1.0), (1.0, 2.6, 1.0), steel)
    p.breathe(k=.02)
    # the shield comes up to guard, then down
    p.anim("shield", "rotation", lambda t: (-34 * env(t, .5, .12), 0, -16 * env(t, .5, .12) +
                                            3 * math.sin(t * math.pi * 2 + 1)))
    return p.m


def yeti(n):
    p = Pet(n, "Frost Yeti", "shoulder_r")
    fur = R("#ffffff", "#e8eff8", "#c9d6e6")
    b = p.root
    b.add((-2.3, 1.6, -1.8), (4.6, 4.2, 3.6), fur)
    for s in (1, -1):
        side(b, s).add((0.3, 0.0, -1.2), (1.8, 1.8, 2.4), fur)
        a = p.part(f"arm{s}", (2.4, 5.2, 0), s=s)
        a.add((2.3, 1.6, -0.9), (1.5, 3.8, 1.8), fur)
        a.add((2.4, 1.2, -0.8), (1.3, 0.6, 1.6), R("#9fb8d6"))
        p.anim(a.bone.name, "rotation", (lambda s_: lambda t: (10 * math.sin(t * math.pi * 2 + s_), 0, 0))(s))
    hd = p.part("head", (0, 5.8, 0))
    hd.add((-2.7, 5.6, -2.5), (5.4, 4.4, 4.6), over(fur, art([".ffffffffff.", ".ffffffffff.", ".ffffffffff.",
                                                              "..ffffffff..", "...ffffff..."], {"f": "#9fb8d6"}, y=2),
                                                     *eyes(3, 7, 3, pal={"k": "#1b2a44"}),
                                                     art(["kwwk"], {"k": "#1b2a44", "w": W}, y=7)))
    for (x, z) in ((-2.4, -1.4), (-0.6, -0.6), (1.2, -1.2), (2.0, 0.6), (-1.8, 1.0)):
        hd.add((x, 9.9, z), (1.2, 1.0, 1.2), R("#ffffff", "#e0e8f2"))
    hd.add((-0.5, 10.6, -0.6), (1.0, 1.8, 1.0), R("#e6fbff", "#7fdcff", "#3fb7e0"), rot=(0, 45, 0))
    cheeks(hd, (-2.7, 5.6, -2.5), (5.4, 4.4, 4.6), fur)
    p.blink("head", (-2.7, 5.6, -2.5), (5.4, 4.4, 4.6), 3, "#9fb8d6", rows=3)
    p.look("head", yaw=10)
    p.breathe()
    # a big wave: the right arm swings up over the head and waves
    p.anim("arm1", "rotation", lambda t: (-10 * math.sin(t * math.pi * 2), 0, 90 * env(t, .5, .2) +
                                          16 * env(t, .5, .16) * math.sin(t * 60)))
    return p.m


# ------------------------------------------------------------------------------------------------ floating beside


def wisp(n):
    p = Pet(n, "Lantern Wisp", "float_r")
    p.bob(1.0, 6)
    flame = R("#d8fbff", "#7fe6ff", "#2fa8ff", "#1a5fd6", edge=None)
    b = p.root
    b.add((-2.0, 2.6, -2.0), (4.0, 4.6, 4.0), over(flame, *eyes(1, 5, 3, rows=EYE2, pal={"k": "#0b1a3a"}),
                                                    art([".kk."], {"k": "#0b1a3a"}, y=6)))
    fl = p.part("flames", (0, 7.2, 0))
    for (x, z, h) in ((-1.6, -1.2, 2.2), (0.2, -1.4, 3.0), (-0.6, 0.6, 2.6), (1.0, 0.4, 1.8)):
        fl.add((x, 7.2, z), (1.2, h, 1.2), R("#ffffff", "#9ff0ff", "#3fb7e0", edge=None))
    p.anim("flames", "scale", lambda t: (1, .8 + .3 * abs(math.sin(t * math.pi * 4)), 1), LOOP / 2)
    b.add((-1.4, 1.2, -1.4), (2.8, 1.4, 2.8), flame)
    lan = p.part("lantern", (3.6, 3.4, 0))
    iron, glow = R("#4a4f58", "#2a2e35"), R("#fff6b0", "#ffd23f", "#ff9a1f", edge=None)
    b.add((2.0, 3.6, -0.4), (1.6, 0.6, 0.8), flame)
    lan.add((3.3, 2.2, -0.3), (0.6, 1.2, 0.6), iron)
    lan.add((2.6, 1.7, -1.0), (2.0, 0.5, 2.0), iron)
    lan.add((2.8, -0.5, -0.8), (1.6, 2.2, 1.6), glow)
    lan.add((2.6, -1.0, -1.0), (2.0, 0.5, 2.0), iron)
    p.anim("lantern", "rotation", lambda t: (8 * math.sin(t * math.pi * 2 + 1), 0, 10 * math.sin(t * math.pi * 2)))
    # the flame flares up once
    p.anim("flames", "scale", lambda t: (1 + .3 * env(t, .5, .1), .8 + .3 * abs(math.sin(t * math.pi * 4)) +
                                         .9 * env(t, .5, .1), 1 + .3 * env(t, .5, .1)), LOOP / 2)
    return p.m


def astronaut(n):
    p = Pet(n, "Little Astronaut", "float_l")
    p.anim("pet", "position", lambda t: (0, 1.2 * math.sin(t * math.pi * 2), 0))
    p.anim("pet", "rotation", lambda t: (8 * math.sin(t * math.pi * 2), 360 * t, 6 * math.sin(t * math.pi * 2 + 1)),
           LOOP * 2)
    suit = R("#ffffff", "#e6eaf0", "#c3c9d2")
    b = p.root
    b.add((-2.0, 2.0, -1.6), (4.0, 4.4, 3.2), over(suit, art(["rr", "bb"], {"r": "#e8323c", "b": "#3ea0ff"}, y=1)))
    visor = art(["..vvvvvvvv..", ".vvvvvvvvvv.", ".vwvvvvvvvv.", ".vwwvvvvvvv.", ".vvvvvvvvvv.", "..vvvvvvvv.."],
                {"v": "#1d3a6b", "w": "#bfe3ff"}, y=2)
    b.add((-2.8, 6.4, -2.8), (5.6, 5.0, 5.6), over(suit, visor))
    b.add((-2.0, 7.2, -3.3), (4.0, 3.0, 0.5), R("#2a5aa8", "#1d3a6b"))
    b.add((-0.3, 11.4, -0.3), (0.6, 1.4, 0.6), R("#9aa3ad"))
    lt = p.part("light", (0, 13.4, 0))
    lt.add((-0.6, 12.8, -0.6), (1.2, 1.2, 1.2), R("#ff4f4f", "#c42a2a"))
    p.anim("light", "scale", lambda t: (1 + .3 * max(0, math.sin(t * math.pi * 8)) ** 6,) * 3, LOOP)
    b.add((-1.6, 2.6, 1.6), (3.2, 3.6, 1.4), R("#d6dbe2", "#9aa3ad"))
    for s in (1, -1):
        a = p.part(f"arm{s}", (2.0, 6.0, 0), s=s)
        a.add((2.0, 2.6, -1.0), (1.3, 3.4, 1.8), suit)
        p.anim(a.bone.name, "rotation", (lambda s_: lambda t: (14 * math.sin(t * math.pi * 2 + s_), 0,
                                                               -10 * s_ * (1 + math.sin(t * math.pi * 2))))(s))
        side(b, s).add((0.2, 0.0, -1.0), (1.6, 2.0, 2.0), over(suit, L_hstripes(["#ff8a1f"], [1], start=2)))
    # waving at you on the way round
    p.anim("arm1", "rotation", lambda t: (0, 0, 78 * env(t, .5, .18) + 14 * env(t, .5, .14) * math.sin(t * 60)))
    return p.m


def dragon(n):
    p = Pet(n, "Baby Dragon", "float_r")
    p.bob(0.8, 4)
    sc = R("#7c63f0", "#5a3fd6", "#3f2aa8")
    bel = R("#ffe08a", "#f5c542")
    b = p.root
    b.add((-1.8, 2.0, -2.8), (3.6, 3.2, 5.6), over(sc, art(["yyyyyyy", "yyyyyyy"], {"y": "#ffe08a"}, y=-2)))
    hd = p.part("head", (0, 4.6, -2.4))
    hd.add((-2.4, 4.4, -6.0), (4.8, 4.0, 4.0), over(sc, *eyes(2, 6, 2, rows=EYE, pal={"k": "#2a0f4a"}),
                                                     *blush(1, 7, 5, "#ff8ad1")))
    hd.add((-1.4, 4.6, -7.2), (2.8, 1.8, 1.2), over(R("#b9a6ff", "#9a84f0"), art(["k..k"], {"k": "#2a0f4a"}, y=0)))
    for s in (1, -1):
        side(hd, s).add((0.8, 8.2, -4.0), (0.8, 1.6, 0.8), R("#fff1b8", "#ffd76a"), rot=(-20, 0, -15),
                        pivot=(1.2, 8.2, -3.6))
        side(b, s).add((0.5, 0.0, -2.4), (1.2, 2.4, 1.3), sc)
        side(b, s).add((0.5, 0.0, 1.2), (1.2, 2.4, 1.3), sc)
        w = p.part(f"wing{s}", (1.6, 4.6, 0.2), s=s)
        w.add((1.6, 4.3, -0.3), (4.4, 0.8, 0.8), sc, rot=(0, 0, 28), pivot=(1.6, 4.7, 0.1))
        from kit import voxels
        voxels(w, [(1.8, 4.6), (5.4, 6.6), (6.2, 3.4), (5.0, 3.6), (4.2, 2.0), (3.0, 3.2), (1.8, 3.0)], 0.0, 0.5,
               ["#c9b8ff", "#b39cff", "#9a84f0"], cell=0.6)
        p.anim(w.bone.name, "rotation", (lambda s_: lambda t: (0, 10 * s_ * math.sin(t * math.pi * 8),
                                                               30 * s_ * math.sin(t * math.pi * 8)))(s))
    chain(p.m, "tail", None, "pet", (0, 2.8, 2.8), (0, -0.3, 1.4), 4, 1.8, 1.0, sc, phase=1, amp=(5, 16),
          speed=LOOP / 2, end_paint=bel)
    p.blink("head", (-2.4, 4.4, -6.0), (4.8, 4.0, 4.0), 2, "#5a3fd6", rows=3)
    # a little puff of fire from its snout
    fp = p.part("puff", (0, 5.4, -7.4), parent="head")
    fp.add((-.8, 4.8, -9.8), (1.6, 1.4, 2.4), R("#fff6c4", "#ffb347", "#ff5a1a", edge=None))
    fp.add((-.5, 5.0, -11.2), (1.0, 1.0, 1.4), R("#ffd23f", "#ff7a1a", edge=None))
    p.anim("puff", "scale", lambda t: (env(t, .7, .08),) * 3)
    p.anim("head", "rotation", lambda t: (-14 * env(t, .66, .1), 6 * math.sin(t * math.pi * 2), 0))
    return p.m


def jelly(n):
    p = Pet(n, "Moon Jellyfish", "float_l")
    p.anim("pet", "position", lambda t: (0, 1.4 * math.sin(t * math.pi * 2), 0))
    p.anim("pet", "scale", lambda t: (1 + .06 * math.sin(t * math.pi * 2 + 1.5), 1 - .05 * math.sin(t * math.pi * 2 +
                                                                                                    1.5),
                                      1 + .06 * math.sin(t * math.pi * 2 + 1.5)))
    bell = R("#efe3ff", "#c6a8ff", "#9a74f0", edge=None)
    b = p.root
    b.add((-3.0, 5.0, -3.0), (6.0, 3.4, 6.0), over(bell, *eyes(3, 8, 3, rows=EYE2, pal={"k": "#3b2a66"}),
                                                    *blush(2, 9, 5, "#ff9cd2")))
    b.add((-2.2, 8.4, -2.2), (4.4, 1.2, 4.4), R("#f6efff", "#e2d1ff"))
    b.add((-2.8, 4.6, -2.8), (5.6, 0.4, 5.6), R("#b392f5"))
    for k, (x, z) in enumerate(((-2.0, -2.0), (2.0, -2.0), (-2.0, 2.0), (2.0, 2.0), (0, 0))):
        chain(p.m, f"t{k}_", None, "pet", (x, 4.6, z), (0, -1.15, 0), 4, 0.9, 0.6, R("#efe3ff", "#c6a8ff", edge=None),
              phase=k * 1.3, amp=(12, 10), speed=LOOP / 2)
    p.blink("pet", (-3.0, 5.0, -3.0), (6.0, 3.4, 6.0), 3, "#d8c6ff", rows=2)
    return p.m


def slime(n):
    p = Pet(n, "King Slime", "float_r")
    p.anim("pet", "position", lambda t: (0, 1.6 * abs(math.sin(t * math.pi * 2)), 0), LOOP / 2)
    p.anim("pet", "scale", lambda t: (1 + .1 * math.cos(t * math.pi * 4), 1 - .1 * math.cos(t * math.pi * 4),
                                      1 + .1 * math.cos(t * math.pi * 4)), LOOP / 2)
    g = R("#9dff8a", "#6fe06a", "#48b844")
    b = p.root
    b.add((-3.0, 0.0, -3.0), (6.0, 5.6, 6.0), over(g, art(["ww", "w."], {"w": "#d6ffc9"}, x=1, y=1),
                                                    *eyes(3, 7, 4, rows=EYE, pal={"k": "#174a1d"}),
                                                    *blush(2, 8, 7, "#ff9cb5"),
                                                    art(["k..k", ".kk."], {"k": "#174a1d"}, y=8)))
    gold = R("#fff1a0", "#ffd23f", "#d9a520")
    b.add((-2.2, 5.6, -2.2), (4.4, 1.2, 4.4), over(gold, art(["r.b.r"], {"r": "#e0245e", "b": "#2f80ed"}, y=0)))
    for (x, z) in ((-2.2, -2.2), (-0.5, -2.2), (1.2, -2.2), (-2.2, 1.2), (1.2, 1.2), (-0.5, 1.2)):
        b.add((x, 6.8, z), (1.0, 1.2, 1.0), gold)
    p.blink("pet", (-3.0, 0.0, -3.0), (6.0, 5.6, 6.0), 4, "#6fe06a", rows=3)
    # the crown tips with each landing
    pass
    return p.m


def robot(n):
    p = Pet(n, "Retro Robot", "float_l")
    p.bob(0.6, 8)
    case, dk = R("#cfd5dc", "#aab2bc", "#7f8894"), R("#4a505a", "#2a2f37")
    b = p.root
    screen = art(["kkkkkkkkkk", "kkkkkkkkkk", "kkgkkkkgkk", "kgkgkkgkgk", "kkkkkkkkkk", "kkkggggkkk", "kkkkkkkkkk"],
                 {"k": "#0f1a14", "g": "#4dff8a"}, y=1)
    b.add((-3.0, 5.4, -2.4), (6.0, 4.8, 4.6), over(case, screen))
    b.add((-0.3, 10.2, -0.3), (0.6, 1.8, 0.6), dk)
    lt = p.part("antenna", (0, 12.4, 0))
    lt.add((-0.7, 11.8, -0.7), (1.4, 1.4, 1.4), R("#ff6b6b", "#d63a3a"))
    p.anim("antenna", "scale", lambda t: (1 + .35 * max(0, math.sin(t * math.pi * 4)) ** 8,) * 3)
    b.add((-2.2, 1.8, -1.8), (4.4, 3.6, 3.6), over(case, art(["r.y.g"], {"r": "#ff4f4f", "y": "#ffd23f",
                                                                     "g": "#4dff8a"}, y=2)))
    b.add((-1.4, 0.8, -1.4), (2.8, 1.0, 2.8), dk)
    jet = p.part("jet", (0, 0.8, 0))
    jet.add((-0.9, -1.4, -0.9), (1.8, 2.2, 1.8), R("#fff6d0", "#ffd23f", "#ff7a1a", edge=None))
    p.anim("jet", "scale", lambda t: (1, .7 + .4 * abs(math.sin(t * math.pi * 8)), 1))
    for s in (1, -1):
        a = p.part(f"arm{s}", (2.2, 4.6, 0), s=s)
        a.add((2.2, 4.2, -0.4), (2.0, 0.8, 0.8), dk)
        a.add((4.0, 3.4, -0.7), (0.9, 1.8, 1.4), case)
        p.anim(a.bone.name, "rotation", (lambda s_: lambda t: (12 * math.sin(t * math.pi * 2 + s_), 0, 0))(s))
    # one arm waves
    p.anim("arm1", "rotation", lambda t: (12 * math.sin(t * math.pi * 2), 0, 72 * env(t, .4, .15) +
                                          14 * env(t, .4, .12) * math.sin(t * 50)))
    return p.m


def reaper(n):
    p = Pet(n, "Mini Reaper", "float_r")
    p.bob(1.1, 8)
    cloak = R("#2a2a33", "#1c1c22", "#101014")
    b = p.root
    b.add((-2.4, 3.0, -2.0), (4.8, 4.6, 4.0), cloak)
    b.add((-2.6, 7.2, -2.6), (5.2, 4.4, 5.0), over(cloak, art(["..........", ".kkkkkkkk.", ".kkkkkkkk.",
                                                               ".kgkkkkgk.", ".kkkkkkkk.", "..kkkkkk.."],
                                                              {"k": "#050507", "g": "#8affd8"}, y=1)))
    b.add((-1.4, 11.4, -0.2), (2.8, 1.0, 2.8), cloak, rot=(-20, 0, 0), pivot=(0, 11.4, 1))
    rags = p.part("rags", (0, 3.0, 0))
    for k, x in enumerate((-2.2, -0.9, 0.4, 1.4)):
        rags.add((x, 0.6 - (k % 2) * 0.6, -1.8 + (k % 2) * .6), (1.2, 2.6 + (k % 2) * .6, 3.4), cloak)
    p.anim("rags", "rotation", lambda t: (8 * math.sin(t * math.pi * 4), 0, 5 * math.sin(t * math.pi * 2)))
    sc = p.part("scythe", (3.0, 5.0, -0.8))
    wood, blade = R("#6b4a2b", "#4a3020"), R("#e6eaf0", "#9aa3ad", "#5a6068")
    sc.add((2.7, 0.0, -1.1), (0.6, 11.0, 0.6), wood)
    sc.add((3.0, 10.0, -1.0), (4.2, 1.0, 0.4), blade, rot=(0, 0, -18), pivot=(3.0, 10.5, -0.8))
    sc.add((6.4, 8.4, -1.0), (1.0, 1.6, 0.4), blade, rot=(0, 0, -40), pivot=(6.4, 9.2, -0.8))
    p.anim("scythe", "rotation", lambda t: (0, 0, 6 * math.sin(t * math.pi * 2 + .5)))
    b.add((2.0, 5.0, -1.4), (1.2, 1.0, 1.2), R("#e6e0d0"))
    # the scythe sweeps round once
    p.anim("scythe", "rotation", lambda t: (0, -46 * env(t, .55, .13), 4 * math.sin(t * math.pi * 2 + .5) -
                                            20 * env(t, .55, .13)))
    return p.m


def capybara(n):
    p = Pet(n, "Pool Capybara", "float_l")
    p.anim("pet", "position", lambda t: (0, 0.6 * math.sin(t * math.pi * 2), 0))
    p.anim("pet", "rotation", lambda t: (4 * math.sin(t * math.pi * 2 + 1), 0, 5 * math.sin(t * math.pi * 2)))
    fur = R("#b8875a", "#a87648", "#8a5e36")
    b = p.root
    ring = over(R("#ff8fb8", "#ff6fa0"), L_hstripes(["#ffffff", None], [2, 2], start=0, vertical=True))
    for (o, s_) in (((-4.0, 0.0, -4.0), (8.0, 2.0, 1.8)), ((-4.0, 0.0, 2.2), (8.0, 2.0, 1.8)),
                    ((-4.0, 0.0, -2.2), (1.8, 2.0, 4.4)), ((2.2, 0.0, -2.2), (1.8, 2.0, 4.4))):
        b.add(o, s_, ring)
    b.add((-2.2, 0.6, -2.4), (4.4, 4.0, 4.8), fur)
    face = art(["........", ".kk..kk.", "........", "..dddd..", "..dkkd..", "..dddd.."], {"k": "#2b1d14",
                                                                                         "d": "#7a5230"}, y=0)
    hd = p.part("head", (0, 4.0, -1.8))
    hd.add((-1.9, 2.6, -4.6), (3.8, 3.6, 2.8), over(fur, face))
    for s in (1, -1):
        side(hd, s).add((1.1, 6.2, -2.8), (0.9, 0.8, 0.7), R("#6b4a2b"))
    hd.add((-1.1, 6.2, -3.6), (2.2, 1.9, 2.2), R("#ffb347", "#ff8a1f"))
    hd.add((0.1, 8.1, -2.6), (1.2, 0.4, 0.6), R("#5fbf4a"))
    p.anim("head", "rotation", lambda t: (5 * math.sin(t * math.pi * 2), 0, 0))
    p.blink("head", (-1.9, 2.6, -4.6), (3.8, 3.6, 2.8), 1, "#a87648", rows=1)
    # a slow, content nod
    p.anim("head", "rotation", lambda t: (10 * env(t, .4, .2), 6 * math.sin(t * math.pi * 2), 0))
    return p.m


ALL = [fox, owl, cat, axolotl, mushroom, penguin, cactus, bunny, ninja_frog, panda, knight, yeti, wisp, astronaut,
       dragon, jelly, slime, robot, reaper, capybara]
