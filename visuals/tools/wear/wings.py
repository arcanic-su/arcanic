"""Wings, built the way the mod's first wings were: out of small cubes - an arm of a few segments, feathers hanging
from it in rows (short ones over long ones), or bones with a skin between them - in chunky Minecraft pixels.
Hinged at the spine, swept back, flapping slowly."""

import math
import random

from PIL import Image, ImageDraw

from wearlib import *

D = 2
Z = 2.6  # the wing's plane, just off the back


def flipped(paint):
    def p(face, w, h, D_, rnd):
        return paint(face, w, h, D_, rnd).transpose(Image.FLIP_LEFT_RIGHT)

    return p


class Side:
    """Adds the right wing's cubes, or the same mirrored for the left."""

    def __init__(self, bone, s):
        self.b, self.s = bone, s

    def add(self, o, size, paint, rz=0.0, ry=0.0, rx=0.0, pivot=None, only=None, flip=False):
        if self.s < 0:
            o = (-(o[0] + size[0]), o[1], o[2])
            if pivot is not None:
                pivot = (-pivot[0], pivot[1], pivot[2])
            rz, ry = -rz, -ry
            if flip:
                paint = flipped(paint)
        rot = (rx, ry, rz) if (rx or ry or rz) else None
        self.b.add(Cube(o, size, paint, rot=rot, pivot=pivot, only=only))

    def seg(self, p, q, thick, depth, paint, z=Z):
        """A bone from p to q (x, y), thick high and depth deep."""
        L = math.hypot(q[0] - p[0], q[1] - p[1])
        a = math.degrees(math.atan2(q[1] - p[1], q[0] - p[0]))
        self.add((p[0], p[1] - thick / 2, z), (L + thick * .4, thick, depth), paint, rz=a,
                 pivot=(p[0], p[1], z + depth / 2))

    def feather(self, at, L, wd, ang, paint, z, depth=0.6):
        """A feather hanging from at, L long, tilted ang degrees outward from straight down."""
        x, y = at
        self.add((x - wd / 2, y - L, z), (wd, L, depth), paint, rz=ang, pivot=(x, y, z + depth / 2))


def bez(t, P):
    u = 1 - t
    return (u ** 3 * P[0][0] + 3 * u * u * t * P[1][0] + 3 * u * t * t * P[2][0] + t ** 3 * P[3][0],
            u ** 3 * P[0][1] + 3 * u * u * t * P[1][1] + 3 * u * t * t * P[2][1] + t ** 3 * P[3][1])


ARM = [(1.2, 21.5), (5.4, 31.5), (13.5, 35.0), (21.5, 31.0)]  # a bigger span, as the first wings


def wing_model(n, name):
    return Model(n, name, "wings", D=D, view="wings")


def both(m, build, sweep=26, flap=16, speed=2.4, top=22):
    """build(side) for each wing; the bones hinge at the spine."""
    for s in (1, -1):
        name = "wingR" if s > 0 else "wingL"
        b = m.bone(name, pivot=(1.0 * s, top, Z), rot=(0, -sweep * s, 0))
        build(Side(b, s))
        if flap:  # some wings are still: flap=0
            m.anim(name, "rotation",
                   (lambda s_: lambda t: (0, -flap * s_ * (0.5 + 0.5 * math.sin(t * math.pi * 2)), 0))(s), speed, 32)
    return m


def feathered(m, arm, cover, mid, prim, prim_n=8, mid_n=7, cover_n=9, long_=15.0, extra=None, **kw):
    """Feathered wings: arm, coverts, secondaries and primaries (each a painter, hanging feathers)."""

    def build(sd):
        pts = [bez(t, ARM) for t in (0, .33, .66, 1)]
        for i in range(3):
            sd.seg(pts[i], pts[i + 1], 2.0 - i * .35, 1.6, arm, z=Z + .2)
        for i in range(prim_n):  # the long ones, out at the tip, under the rest
            t = .48 + .52 * i / (prim_n - 1)
            x, y = bez(min(.98, t), ARM)
            L = long_ * (1.0 - .45 * ((i / (prim_n - 1)) ** 2)) * (0.9 + .1 * math.sin(i))
            sd.feather((x, y - .6), L, 1.6, 12 + 46 * (i / (prim_n - 1)) ** 1.2, prim, Z)
        for i in range(mid_n):
            t = .02 + .5 * i / (mid_n - 1)
            x, y = bez(t, ARM)
            sd.feather((x, y - .6), long_ * (.62 + .1 * i / mid_n), 1.9, 2 + 10 * i / (mid_n - 1), mid, Z + .45)
        for i in range(cover_n):
            t = .04 + .86 * i / (cover_n - 1)
            x, y = bez(t, ARM)
            sd.feather((x, y - .2), 3.2 + 1.2 * math.sin(i * 1.3) ** 2, 2.2, 8 + 30 * i / (cover_n - 1), cover,
                       Z + .9, depth=.7)
        if extra:
            extra(sd)

    return both(m, build, **kw)


def cut_pic(poly_fn, c1, c2, X0, Y1, edge_c=None):
    """A cut-out picture in chunky pixels: poly_fn(T) gives the outline (T maps model x, y to texels)."""

    def fn(img, face, w, h, D_, rnd):
        d = ImageDraw.Draw(img)

        def T(p):
            return ((p[0] - X0) * D_, (Y1 - p[1]) * D_)

        for poly, col in poly_fn(T):
            d.polygon(poly, fill=C(col) if col else (255, 255, 255, 255))
        px = img.load()
        a, b = C(c1), C(c2)
        for y in range(h):
            for x in range(w):
                p = px[x, y]
                if p[3]:
                    base = mix(a, b, y / max(1, h - 1)) if p[:3] == (255, 255, 255) else p
                    px[x, y] = mul(base, rnd.choice((.86, .93, 1, 1, 1.06)))
        edge = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] and any(
            not (0 <= x + dx < w and 0 <= y + dy < h) or px[x + dx, y + dy][3] == 0
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
        for x, y in edge:
            px[x, y] = C(edge_c) if edge_c else mul(px[x, y], .7)

    return picture(fn)


def membrane_wing(m, bone_p, skin_c, skin_c2, fingers=4, spikes=None, skin=True, **kw):
    """A bat's or a dragon's wing: a bone arm, fingers fanning down from the wrist, a skin between them cut out
    along a scalloped edge (chunky pixels)."""
    shoulder, elbow, wrist = (1.2, 21.5), (7.5, 32.5), (14.5, 33.5)
    tips = []
    for i in range(fingers):
        t = i / max(1, fingers - 1)
        a = math.radians(-20 - 70 * t)
        L = 11.5 - 3.0 * t
        tips.append((wrist[0] + math.cos(a) * L * (1.1 - .5 * t) + 2.5 * (1 - t), wrist[1] + math.sin(a) * L * 1.5))
    X0, Y0, X1, Y1 = 1.0, 4.0, 25.0, 35.0

    def outline(T):
        poly = [T(shoulder), T(elbow), T(wrist), T(tips[0])]
        for i in range(1, fingers):
            a, b = tips[i - 1], tips[i]
            mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
            poly += [T((wrist[0] + (mx - wrist[0]) * .68, wrist[1] + (my - wrist[1]) * .68)), T(b)]
        poly += [T((shoulder[0] + 1.2, tips[-1][1] + 2.5)), T((shoulder[0], 15))]
        return [(poly, None)]

    def build(sd):
        if skin:
            from kit import voxels
            poly = outline(lambda p: p)[0][0]
            voxels(sd, poly, Z + .25, .5, [mix(C(skin_c), (255, 255, 255, 255), .12), C(skin_c),
                                          mix(C(skin_c), C(skin_c2), .5), C(skin_c2)], cell=.9)
        sd.seg(shoulder, elbow, 1.6, 1.4, bone_p, z=Z)
        sd.seg(elbow, wrist, 1.3, 1.2, bone_p, z=Z + .1)
        for tip in tips:
            sd.seg(wrist, tip, .8, .8, bone_p, z=Z + .2)
        sd.add((wrist[0] - .3, wrist[1] - .2, Z + .1), (1.0, 2.2, 1.0), bone_p, rz=-25, pivot=(wrist[0], wrist[1], Z))
        if spikes:
            for k in range(4):
                p = (shoulder[0] + (elbow[0] - shoulder[0]) * (k + .5) / 4,
                     shoulder[1] + (elbow[1] - shoulder[1]) * (k + .5) / 4)
                sd.add((p[0] - .4, p[1] + .5, Z + .4), (.8, 1.6, .8), spikes, rz=-20, pivot=(p[0], p[1], Z))

    return both(m, build, **kw)


# ------------------------------------------------------------------------------------------------ the wings
# feathers painted down a ramp, as the first Dark Wings: the colour of the root down to the colour of the tip


def seraph(n):
    m = wing_model(n, "Seraph Wings")
    return feathered(m, ramp(["#dfe5ef", "#c9d2e2"]), ramp(["#ffffff", "#eef2f9", "#dde5f2"]),
                     ramp(["#f7f9ff", "#e6ecf7", "#cfd9eb"]), ramp(["#ffffff", "#f2f5fb", "#fff1c4", "#ffd978"]))


def raven(n):
    m = wing_model(n, "Raven Wings")
    return feathered(m, ramp(["#16171c", "#0e0f13"]), ramp(["#2a2e38", "#1b1d24"]),
                     ramp(["#1b1d24", "#262a35", "#3a4152"]), ramp(["#15161b", "#1f232d", "#353c4d", "#4d5670"]),
                     long_=16, flap=0)


def void(n):
    m = wing_model(n, "Void Wings")
    return feathered(m, ramp(["#120a1f", "#0b0614"]), ramp(["#24143d", "#170d29"]),
                     ramp(["#160c28", "#2a1650", "#5b2aa8"]), ramp(["#120a1f", "#2a1650", "#6a33c9", "#b07cff"]),
                     long_=16)


def phoenix(n):
    m = wing_model(n, "Phoenix Wings")

    def embers(sd):
        r = random.Random(5)
        for i in range(7):
            x, y = bez(.3 + .6 * r.random(), ARM)
            sd.add((x + r.random() * 2, y - 11 - r.random() * 4, Z + .3), (.8, .8, .8), ramp(["#fff1a0", "#ffb12b"]))

    return feathered(m, ramp(["#c42a1a", "#8a1a0e"]), ramp(["#ff9a2e", "#ff6a1f"]),
                     ramp(["#ff5a1f", "#ff8c1a", "#ffc23d"]), ramp(["#e8341c", "#ff7a1a", "#ffc23d", "#fff1a0"]),
                     extra=embers)


def golden(n):
    m = wing_model(n, "Golden Wings")
    return feathered(m, ramp(["#b8871c", "#8a6210"]), ramp(["#ffe9a0", "#f5c542"]),
                     ramp(["#f5c542", "#e0a92a", "#c98f1a"]), ramp(["#fff0b0", "#ffd86b", "#e0a92a", "#a8740f"]),
                     flap=0)


def frost(n):
    m = wing_model(n, "Frost Wings")

    def ice(sd):
        r = random.Random(9)
        for i in range(10):
            x, y = bez(.12 + .82 * i / 9, ARM)
            L = 1.2 + r.random() * 1.8
            sd.add((x - .4 + r.random(), y - 10 - r.random() * 5, Z + .6), (.9, L, .9),
                   ramp(["#ffffff", "#bff4ff", "#5cc4ef"]), rz=45, pivot=(x, y - 10, Z + 1))

    return feathered(m, ramp(["#3fb7e0", "#2a86b0"]), ramp(["#e6fbff", "#bff4ff"]),
                     ramp(["#bff4ff", "#7fdcff", "#3fb7e0"]), ramp(["#ffffff", "#bff4ff", "#5cc4ef", "#2a86b0"]),
                     extra=ice)


def demon(n):
    m = wing_model(n, "Demon Wings")
    return membrane_wing(m, ramp(["#2a0e12", "#120507"]), "#8a1a2a", "#2a050c", fingers=4, sweep=30, flap=12)


def dragon(n):
    m = wing_model(n, "Emerald Dragon")
    return membrane_wing(m, ramp(["#14502f", "#0b2f1b"]), "#3fbf70", "#0f4426", fingers=5,
                         spikes=ramp(["#fffbe6", "#d9cfa3"]), flap=14, speed=2.8)


def skeleton(n):
    m = wing_model(n, "Bone Wings")
    return membrane_wing(m, ramp(["#f5f0e0", "#d9d0b6", "#b8ae92"]), "#000000", "#000000", fingers=5, skin=False,
                         flap=0)


def nether(n):
    m = wing_model(n, "Magma Wings")
    return membrane_wing(m, ramp(["#3a2418", "#1a0f0a"]), "#ffb347", "#c42a0a", fingers=4,
                         spikes=ramp(["#fff1a0", "#ff7a1a"]), flap=12)


def cobweb(n):
    m = wing_model(n, "Silk Web Wings")
    silk = ramp(["#ffffff", "#e6e8ee", "#c9ccd6"])

    def build(sd):
        hub = (2.0, 22.0)
        rays = []
        for i in range(6):
            a = math.radians(80 - 22 * i)
            L = 15 - abs(i - 2) * 1.6
            rays.append((hub[0] + math.cos(a) * L, hub[1] + math.sin(a) * L * .95))
        for r in rays:
            sd.seg(hub, r, .6, .6, silk)
        for ring in (.35, .6, .85):
            pts = [(hub[0] + (r[0] - hub[0]) * ring, hub[1] + (r[1] - hub[1]) * ring) for r in rays]
            for a, b in zip(pts, pts[1:]):
                sd.seg(a, b, .45, .5, silk, z=Z + .3)
        sp = rays[2]
        sd.add((sp[0] - .8, sp[1] - 1.5, Z + .3), (1.6, 1.4, 1.6), ramp(["#2a2a33", "#16161c"]))
        sd.add((sp[0] - .5, sp[1] - .2, Z + .4), (1.0, .8, 1.0), ramp(["#c42a2a", "#8a1a1a"]))

    return both(m, build, flap=0)


def mecha(n):
    m = wing_model(n, "Mecha Wings")
    plate, light, frame = ramp(["#c3cad4", "#9aa3b0", "#6f7887"]), ramp(["#bff8ff", "#3ee6ff"]), ramp(["#4a505a", "#2a2f37"])

    def build(sd):
        pts = [bez(t, ARM) for t in (0, .33, .66, 1)]
        for i in range(3):
            sd.seg(pts[i], pts[i + 1], 2.0, 1.8, frame, z=Z)
        for i in range(6):
            t = .1 + .85 * i / 5
            x, y = bez(t, ARM)
            L = 12 - i * .9
            ang = 6 + 9 * i
            sd.feather((x, y - .8), L, 2.2, ang, plate, Z + .4 + i * .05, depth=.8)
            sd.feather((x, y - 1.6), L - 2.5, .5, ang, light, Z + 1.25 + i * .05, depth=.2)
        sd.add((pts[1][0] - 1, pts[1][1] - 1, Z + 1.7), (2, 2, .4), light)

    return both(m, build, flap=8, speed=3.2)


def butterfly(n):
    m = wing_model(n, "Morpho Butterfly")
    from kit import voxels
    upper = [(1, 22), (4, 30.5), (10, 31.5), (14.5, 28), (13.5, 22.5), (8, 19.5)]
    lower = [(1, 20.5), (8, 19.5), (10, 15), (7, 10.5), (3, 11.5)]

    def spots(x, y):
        if (x - 10.5) ** 2 + (y - 26.5) ** 2 < 2.2:
            return "#bff4ff"
        if (x - 5.5) ** 2 + (y - 14.5) ** 2 < 1.6:
            return "#bff4ff"
        if (x - 9) ** 2 + (y - 28.5) ** 2 > 30 and y > 24:
            return "#0b2a5a"
        return None

    def build(sd):
        voxels(sd, upper, Z + .2, .5, ["#5cc4ff", "#2a9bff", "#1d6fd6"], cell=.9, colour_at=spots)
        voxels(sd, lower, Z + .2, .5, ["#2a86e0", "#1468c9", "#0b3f8a"], cell=.9, colour_at=spots)
        sd.seg((1, 21), (13, 27.5), .6, .7, ramp(["#08152e"]), z=Z + .45)
        sd.seg((1, 20), (8, 12), .6, .7, ramp(["#08152e"]), z=Z + .45)

    return both(m, build, sweep=16, flap=28, speed=1.1)


# ------------------------------------------------------------------------------------------------ not wings

from kit import Part, bone as kbone, chain  # noqa: E402


def feast_hooks(n):
    """After Pudge's Feast of Abscession: two chains rising off the back over the shoulders, a great crescent hook
    on each, dark steel with a molten edge, embers on the links; they sway a little."""
    m = Model(n, "Feast Hooks", "wings", D=2, view="wings")
    iron = over(ramp(["#4a3a36", "#2a2220", "#161210"]), L_draw(lambda d, w, h, D_, f, img: [
        d.point((x, y), fill=rgb("#ff6a1a")) for x in range(w) for y in range(h) if (x * 5 + y * 3) % 9 == 0]))
    steel = ramp(["#5a4a46", "#3a2e2b", "#1e1715"])
    edge = ramp(["#fff1a0", "#ffb347", "#ff5a1a", "#c4200a"], edge=None)
    harness = kbone(m, "harness", (0, 19, 2.4))
    harness.add((-3.2, 14.8, 2.2), (6.4, 8, 1.4), over(ramp(["#3a2a24", "#1e1612"]), L_draw(
        lambda d, w, h, D_, f, img: [d.line((x, 0, x + 3, h), fill=rgb("#ff5a1a")) for x in (2, 7)])))
    harness.add((-1.8, 17.0, 3.6), (3.6, 3.6, .6), ramp(["#ff8a2a", "#d6300a"], edge=None))
    harness.add((-2.6, 16.4, 3.4), (5.2, 4.8, .3), steel)
    harness.add((-2.6, 23.4, -2.5), (1.4, .6, 5.0), steel)
    harness.add((1.2, 23.4, -2.5), (1.4, .6, 5.0), steel)

    def hook(p, end):
        x, y, z = end
        p.add((x - 1.1, y - .5, z - .6), (2.2, 2.2, 1.2), steel)  # the eye the chain goes through
        R_, cx, cy = 5.4, x + 4.4, y + 2.2
        n = 12
        pts = [(cx + math.cos(math.radians(200 - k * 22)) * R_, cy + math.sin(math.radians(200 - k * 22)) * R_ * 1.15)
               for k in range(n)]
        for k in range(n - 1):
            (x1, y1), (x2, y2) = pts[k], pts[k + 1]
            L = math.hypot(x2 - x1, y2 - y1)
            ang = math.degrees(math.atan2(y2 - y1, x2 - x1))
            t = k / (n - 2)
            wd = 2.6 * (1 - t) ** .8 + .35  # the blade narrows to a point
            p.add((x1, y1 - wd / 2, z - .3), (L + .35, wd, .7), steel, rot=(0, 0, ang), pivot=(x1, y1, z))
            # the molten cutting edge on the inside of the crescent
            p.add((x1, y1 - wd / 2 - .45, z - .2), (L + .35, .55, .5), edge, rot=(0, 0, ang), pivot=(x1, y1, z))
        p.add((pts[1][0] - .6, pts[1][1] + .6, z - .4), (1.2, 2.4, .8), steel, rot=(0, 0, 40),
              pivot=(pts[1][0], pts[1][1], z))  # a spike on its back

    link = over(ramp(["#4a3a36", "#2a2220", "#161210"]), L_draw(lambda d, w, h, D_, f, img: (
        d.rectangle((w // 2 - 0, 1, w // 2, h - 2), fill=rgb("#0a0706")), d.point((0, 0), fill=rgb("#ff6a1a")))))
    for s_ in (1, -1):
        chain(m, f"chain{'R' if s_ > 0 else 'L'}", None, "harness", (2.0, 21.0, 3.6), (0.55, 2.0, 0.45), 10, 1.4,
              1.2, link, tip=hook, phase=0 if s_ > 0 else 1.7, amp=(3, 4), speed=3.6, s=s_, curl=-6,
              end_paint=iron)
    em = kbone(m, "embers", (0, 30, 4))
    import random as _r
    rr = _r.Random(n)
    for k in range(10):
        sx = rr.choice((-1, 1))
        em.add((sx * rr.uniform(3, 9) - .4, rr.uniform(24, 38), rr.uniform(3, 6)), (.8, .8, .8),
               ramp(["#fff1a0", "#ff8a2a"], edge=None))
    m.anim("embers", "position", lambda t: (0, 2.0 * t, 0), 1.4)
    return m


def kraken(n):
    m = Model(n, "Kraken Tentacles", "wings", D=2, view="wings")
    skin = over(ramp(["#a86fe6", "#7a3fc4", "#4a1f80"]), L_draw(lambda d, w, h, D_, f, img: f in ("front", "left") and [
        d.point((w // 2, y), fill=rgb("#f6d6ff")) for y in range(1, h, 2)]))
    core = kbone(m, "core", (0, 19, 2.4))
    core.add((-2.8, 15.5, 2.2), (5.6, 7, 1.6), ramp(["#6a33a8", "#2f1250"]))
    core.add((-1.2, 18.4, 3.8), (2.4, 1.6, .5), ramp(["#ffd23f", "#e0a81a"]))
    for i in range(6):
        a = math.radians(-150 + i * 60 + 30)
        st = (math.cos(a) * 2.4, 19 + math.sin(a) * 2.4, 3.4)
        sp = (math.cos(a) * 1.7, math.sin(a) * 1.7 + .2, .5)
        chain(m, f"k{i}_", None, "core", st, sp, 7, 2.0, .7, skin, phase=i * 1.1, amp=(9, 12), speed=3.8,
              curl=11 if i % 2 else -11)
    return m


def jetpack(n):
    m = Model(n, "Rocket Jetpack", "wings", D=2, view="back")
    steel = over(ramp(["#e6eaf0", "#c3c9d2", "#8d949e"]), L_hstripes(["#d63b3b", None, "#d63b3b"], [1, 1, 1], start=5))
    b = kbone(m, "pack", (0, 20, 3))
    for sx in (-3.9, 0.4):
        b.add((sx, 14, 2.4), (3.5, 9, 3.5), steel)
        b.add((sx + 0.4, 23, 2.8), (2.7, 1.4, 2.7), ramp(["#d63b3b", "#9a2020"]))
        b.add((sx + 0.6, 12.4, 3.0), (2.3, 1.6, 2.3), ramp(["#5a606a", "#2f3238"]))
    b.add((-0.7, 15, 2.4), (1.4, 7, 2.6), ramp(["#3a3f48", "#22262c"]))
    b.add((-0.5, 19.5, 5.0), (1.0, 1.0, .4), ramp(["#3ee6ff"]))
    for sx in (-3.1, 1.9):
        b.add((sx, 24.0, -2.55), (1.2, 0.45, 4.95), ramp(["#2c2f35"]))
        b.add((sx, 15.2, -2.6), (1.2, 8.8, 0.35), ramp(["#2c2f35"]))
    fl = kbone(m, "flame", (0, 12.4, 4.2), parent="pack")
    fire = ramp(["#fff6d0", "#ffd23f", "#ff7a1a", "#d6300a"], edge=None)
    for sx in (-2.15, 2.15):
        fl.add((sx - .8, 9.0, 3.35), (1.6, 3.4, 1.6), fire)
        fl.add((sx - .4, 7.4, 3.75), (.8, 1.6, .8), ramp(["#ff7a1a", "#d6300a"]))
    m.anim("flame", "scale", lambda t: (1, 0.8 + 0.3 * abs(math.sin(t * math.pi * 6)), 1), 1.0, 24)
    return m


def spider_legs(n):
    m = Model(n, "Mech Spider Legs", "wings", D=2, view="wings")
    metal, joint, eye = ramp(["#6a717c", "#3e444d"]), ramp(["#2a2e35"]), ramp(["#ff3b3b", "#a01818"])
    b = kbone(m, "core", (0, 19, 3))
    b.add((-2.6, 15.5, 2.3), (5.2, 7, 2.4), ramp(["#4a505a", "#2a2f37"]))
    b.add((-1.8, 18, 4.7), (3.6, 2.4, .5), eye)
    for s in (1, -1):
        p = Part(b.bone, (0, 0, 0), s)
        for i, (y, a1, a2) in enumerate(((21.5, 35, -70), (19, 15, -80), (16.5, -10, -95))):
            p.add((2.6, y - .5, 3.3), (5.5, 1.0, 1.0), metal, rot=(0, -25, a1), pivot=(2.6, y, 3.8))
            ex = 2.6 + 5.5 * math.cos(math.radians(a1)) * math.cos(math.radians(25))
            ey = y + 5.5 * math.sin(math.radians(a1))
            ez = 3.8 + 5.5 * math.cos(math.radians(a1)) * math.sin(math.radians(25))
            p.add((ex - .7, ey - .7, ez - .7), (1.4, 1.4, 1.4), joint)
            p.add((ex, ey - .4, ez - .4), (6.5, .8, .8), metal, rot=(0, -25, a2), pivot=(ex, ey, ez))
    return m


def celestial(n):
    m = Model(n, "Celestial Ring", "wings", D=2, view="wings")
    gold = ramp(["#fff1a0", "#ffd23f", "#d9a520"])
    r = kbone(m, "ring", (0, 22, 5))
    R = 8.0
    for i in range(16):
        a = i * 22.5
        x, y = math.cos(math.radians(a)) * R, 22 + math.sin(math.radians(a)) * R
        r.add((x - 1.7, y - .5, 4.6), (3.4, 1.0, .8), gold, rot=(0, 0, a + 90), pivot=(x, y, 5.0))
        if i % 4 == 0:
            r.add((x - .6, y - .6, 4.4), (1.2, 1.2, 1.2), ramp(["#bff8ff", "#3ee6ff"]), rot=(0, 0, 45), pivot=(x, y, 5.0))
    core = kbone(m, "core", (0, 22, 5), parent=None)
    core.add((-1.2, 20.8, 4.4), (2.4, 2.4, 1.2), ramp(["#fffbe6", "#ffd23f"]), rot=(0, 0, 45), pivot=(0, 22, 5))
    m.anim("ring", "rotation", lambda t: (0, 0, t * 360), 12.0, 32)
    return m


def kitsune(n):
    """Nine fox tails fanned behind, each bending on its own."""
    m = Model(n, "Kitsune Tails", "wings", D=2, view="back")
    fur = ramp(["#ffb35a", "#ff8f3d", "#f07b2c"])
    tip = ramp(["#fff7ec", "#ffffff"])
    root = kbone(m, "root", (0, 13, 2.4))
    root.add((-2.0, 12.0, 2.2), (4.0, 2.6, 1.6), ramp(["#f07b2c", "#c45a16"]))
    for i in range(9):
        a = math.radians(-60 + i * 15)
        st = (math.sin(a) * 1.2, 13.2, 3.4)
        sp = (math.sin(a) * 1.8, math.cos(a) * 1.9, .3)
        chain(m, f"tail{i}_", None, "root", st, sp, 6, 2.8, 1.8, fur, phase=i * .7, amp=(8, 10), speed=2.6,
              curl=(-6 if a > 0 else 6) if abs(a) > .2 else 0, end_paint=tip)
    return m


def dragon_tail(n):
    m = Model(n, "Dragon Tail", "wings", D=2, view="side")
    sc = over(ramp(["#c42a2a", "#9a1a1a", "#6b0e0e"]), L_draw(lambda d, w, h, D_, f, img: [
        d.point((x, y), fill=rgb("#e04040")) for x in range(w) for y in range(h) if (x + 2 * y) % 5 == 0]))
    spike = ramp(["#2a1010", "#120606"])
    root = kbone(m, "root", (0, 12.5, 2.4))
    root.add((-2.2, 11.4, 1.8), (4.4, 2.6, 1.4), ramp(["#9a1a1a", "#6b0e0e"]))

    def fin(p, end):
        x, y, z = end
        p.add((x - .3, y - .6, z - .6), (.6, 1.6, 2.4), spike, rot=(30, 0, 0), pivot=(x, y, z))

    names = chain(m, "seg", None, "root", (0, 12.5, 3.2), (0, -0.7, 1.7), 9, 2.6, 0.9, sc, tip=fin, phase=0,
                  amp=(5, 14), speed=2.2, curl=0)
    for k, bn in enumerate(names[:-1]):
        b = next(x for x in m.bones if x.name == bn)
        z = 3.2 + k * 1.7
        y = 12.5 - k * 0.7
        th = 2.6 + (0.9 - 2.6) * k / 8
        b.add(Cube((-0.3, y + th / 2, z), (0.6, 1.4 - k * .1, 1.0), spike))
    return m


def turtle(n):
    m = Model(n, "Turtle Shell", "wings", D=2, view="back")
    b = kbone(m, "shell", (0, 18, 3))
    green, dark, rim = ramp(["#5fbf4a", "#3f9a34"]), ramp(["#3f7a2a", "#2a5a1c"]), ramp(["#d9c38a", "#b89f60"])
    b.add((-5.0, 12.4, 2.2), (10.0, 11.6, 2.0), rim)
    b.add((-4.4, 13.0, 4.2), (8.8, 10.4, 1.4), dark)
    for (x, y, w, h) in ((-1.6, 16.6, 3.2, 3.2), (-4.0, 18.6, 2.4, 3.6), (1.6, 18.6, 2.4, 3.6), (-4.0, 13.8, 2.4, 3.6),
                         (1.6, 13.8, 2.4, 3.6), (-1.4, 20.4, 2.8, 2.6), (-1.4, 13.4, 2.8, 2.6)):
        b.add((x, y, 5.6), (w, h, 0.9), over(green, L_frame("#2a5a1c")))
    b.add((-1.0, 17.2, 6.5), (2.0, 2.0, 0.5), ramp(["#8ad35a", "#5fbf4a"]))
    for sx in (-3.1, 1.9):
        b.add((sx, 24.0, -2.55), (1.2, 0.45, 4.95), ramp(["#8a6a3a"]))
        b.add((sx, 15.2, -2.6), (1.2, 8.8, 0.35), ramp(["#8a6a3a"]))
    return m


def gears(n):
    m = Model(n, "Clockwork Gears", "wings", D=2, view="back")
    brass, copper, dark = ramp(["#f3d27a", "#d9a520", "#a8740f"]), ramp(["#e8945a", "#c46a2a", "#8a4a1a"]), ramp(
        ["#3a2e22", "#1e1812"])
    base = kbone(m, "base", (0, 18, 3))
    base.add((-3.2, 14.0, 2.2), (6.4, 8.4, 1.6), over(dark, L_frame("#a8740f")))

    def gear(name, cx, cy, z, r, teeth, paint, speed, rev):
        g = kbone(m, name, (cx, cy, z + .5), parent="base")
        g.add((cx - r * .55, cy - r * .55, z), (r * 1.1, r * 1.1, 1.0), paint)
        g.add((cx - r * .55, cy - r * .55, z), (r * 1.1, r * 1.1, 1.0), paint, rot=(0, 0, 45), pivot=(cx, cy, z + .5))
        for k in range(teeth):
            a = k * 360 / teeth
            tx, ty = cx + math.cos(math.radians(a)) * r * .72, cy + math.sin(math.radians(a)) * r * .72
            g.add((tx - .45, ty - .45, z + .1), (.9, .9, .8), paint, rot=(0, 0, a), pivot=(tx, ty, z + .5))
        g.add((cx - .5, cy - .5, z + .9), (1.0, 1.0, .5), dark)
        m.anim(name, "rotation", lambda t: (0, 0, (-1 if rev else 1) * t * 360), speed, 24)

    gear("g1", 0, 20.5, 3.8, 5.0, 10, brass, 8.0, False)
    gear("g2", -3.6, 15.2, 4.4, 3.4, 8, copper, 5.0, True)
    gear("g3", 3.8, 14.8, 4.4, 2.8, 7, brass, 4.0, True)
    gear("g4", 4.4, 25.6, 3.2, 3.0, 7, copper, 4.6, True)
    return m


def crystals(n):
    m = Model(n, "Orbiting Crystals", "wings", D=2, view="back")
    core = kbone(m, "orbit", (0, 20, 5))
    cols = [["#e6fbff", "#7fdcff", "#2a86b0"], ["#f6d8ff", "#c58aff", "#7a3fc4"], ["#fff3c4", "#ffd23f", "#c98f1a"]]
    for k in range(6):
        a = math.radians(k * 60)
        x, y = math.cos(a) * 7.0, 20 + math.sin(a) * 7.0
        c = ramp(cols[k % 3], edge=None)
        core.add((x - .8, y - 1.6, 4.4), (1.6, 3.2, 1.6), c, rot=(0, 45, 0), pivot=(x, y, 5.2))
        core.add((x - .5, y + 1.6, 4.7), (1.0, 1.0, 1.0), c, rot=(0, 45, 0), pivot=(x, y + 2, 5.2))
        core.add((x - .5, y - 2.6, 4.7), (1.0, 1.0, 1.0), c, rot=(0, 45, 0), pivot=(x, y - 2, 5.2))
    m.anim("orbit", "rotation", lambda t: (0, 0, t * 360), 10.0, 32)
    mid = kbone(m, "heart", (0, 20, 5))
    mid.add((-1.2, 18.8, 4.0), (2.4, 2.4, 2.4), ramp(["#ffffff", "#bff4ff", "#3fb7e0"], edge=None), rot=(45, 45, 0),
            pivot=(0, 20, 5.2))
    m.anim("heart", "position", lambda t: (0, .6 * math.sin(t * math.pi * 2), 0), 2.0)
    return m


ALL = [feast_hooks, kitsune, seraph, void, phoenix, dragon, demon, butterfly, mecha, skeleton, cobweb, kraken,
       jetpack, spider_legs, celestial, gears, turtle, crystals, dragon_tail]
