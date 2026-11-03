"""Wings made the way the mod's first wings were (Dark Wings, Dragon Wings, Vulcano...): each wing a chain of three
hinged segments - shoulder, elbow, wrist - every segment a thick block of bone with painted cut-out planes of feathers
(or skin) hanging from it, the planes set at their own angles so the wing has depth; chunky pixel art (2 texels a
pixel), feathers with ragged dithered tips. They move as the old ones do: each segment swings a little after the one
before it, so the wing ripples out to the tip.

Authored for the right wing (x out to the player's right, y up, z back); the left is its mirror image."""

import math
import random

from PIL import Image, ImageDraw

from kit import Part, bone as kbone, chain
import mat
from wearlib import *

D = 2
P0, P1, P2, P3 = (1.5, 21.5), (7.0, 28.0), (13.0, 31.0), (19.0, 30.0)  # shoulder, elbow, wrist, tip
LOOP = 3.2


# ------------------------------------------------------------------------------------------------ painting


def dith(cols, t, rnd):
    """The colour t (0..1) down a ramp, dithered between neighbours."""
    cs = cols
    n = len(cs)
    if n == 1:
        return C(cs[0])
    x = max(0.0, min(.999, t)) * (n - 1)
    i = int(x)
    f = x - i
    return C(cs[i + 1]) if rnd.random() < f else C(cs[i])


def outline(img, k=.62):
    px = img.load()
    w, h = img.size
    edge = []
    for y in range(h):
        for x in range(w):
            if px[x, y][3] and any(not (0 <= x + dx < w and 0 <= y + dy < h) or px[x + dx, y + dy][3] == 0
                                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge.append((x, y))
    for x, y in edge:
        px[x, y] = mul(px[x, y], k)


def feathers(x0, y1, top, lengths, cols, width=2.0, lean=0.0, gap=True, seed=1, extra=None, shaft=True):
    """A plane of feathers: columns of feathers starting under the line top(x) (model px) and hanging down
    lengths[i] px, each width px wide, coloured down cols from root to tip with ragged pointed ends. lean shifts the
    tips outward (px per px down). extra(img, T) may paint on top (T maps model x, y to texels)."""

    def fn(img, face, w, h, D_, rnd):
        r = random.Random(seed)
        px = img.load()
        n = len(lengths)
        fw = width * D_
        for i in range(n):
            L = lengths[i] * D_
            c_lo = i * fw
            for cx in range(int(c_lo), int(c_lo + fw)):
                if cx >= w:
                    break
                xm = x0 + (cx + .5) / D_
                ty0 = (y1 - top(xm)) * D_
                off = abs((cx - c_lo) - (fw - 1) / 2) / max(1, fw / 2)  # 0 middle, 1 edge
                end = ty0 + L * (1 - .18 * off) - r.randint(0, 2)
                for ty in range(max(0, int(ty0)), min(h, int(end))):
                    t = (ty - ty0) / max(1, L)
                    sx = int(cx + lean * (ty - ty0))
                    if not 0 <= sx < w:
                        continue
                    c = dith(cols, t + r.uniform(-.06, .06), rnd)
                    c = mul(c, rnd.choice((.88, .95, 1, 1, 1.05)))
                    if t < .14:
                        c = mix(c, (255, 255, 255, 255), .12 * (1 - t / .14))  # the light root of the feather
                    if gap and cx == int(c_lo):
                        c = mul(c, .62)  # the dark line between feathers
                    elif shaft and fw >= 3 and cx == int(c_lo + fw / 2) and t < .78:
                        c = mix(c, (255, 255, 255, 255), .2)  # its shaft
                    elif (ty + (cx - c_lo) * (1 if cx > c_lo + fw / 2 else -1)) % 3 == 0:
                        c = mul(c, .9)  # its barbs, slanting out from the shaft
                    px[sx, ty] = c
        if extra:
            extra(img, lambda x, y: ((x - x0) * D_, (y1 - y) * D_))
        outline(img)

    return picture(fn)


def membrane(x0, y1, poly, cols, veins=(), vein_c="#000000", seed=2):
    """A skin cut out along poly (model px): dithered down the ramp, darker edge, veins drawn as lines."""

    def fn(img, face, w, h, D_, rnd):
        d = ImageDraw.Draw(img)
        T = lambda p: ((p[0] - x0) * D_, (y1 - p[1]) * D_)
        d.polygon([T(p) for p in poly], fill=(255, 255, 255, 255))
        px = img.load()
        for y in range(h):
            for x in range(w):
                if px[x, y][3]:
                    px[x, y] = mul(dith(cols, y / h, rnd), rnd.choice((.88, .95, 1, 1, 1.05)))
        mat.scales(.9)(img, face, D_, rnd)
        for a, b in veins:
            d.line((T(a), T(b)), fill=mul(C(vein_c), 1))
        outline(img)

    return picture(fn)


def flipped(paint):
    def p(face, w, h, D_, rnd):
        return paint(face, w, h, D_, rnd).transpose(Image.FLIP_LEFT_RIGHT)

    return p


def line_y(a, b):
    """y on the straight line from a to b at x (clamped to its ends)."""

    def f(x):
        t = max(0.0, min(1.0, (x - a[0]) / max(1e-6, b[0] - a[0])))
        return a[1] + (b[1] - a[1]) * t

    return f


# ------------------------------------------------------------------------------------------------ the frame


class Wing:
    """One side's segments: .seg[0..2] are Parts on the three bones; plane() and arm() add to them."""

    def __init__(self, m, s, sweep=30, fold=(0, -5, -7), lift=(0, 4, -3), z=2.6, name="wing"):
        self.m, self.s, self.z = m, s, z
        S = "R" if s > 0 else "L"
        root = kbone(m, f"{name}{S}", (P0[0], P0[1], z), rot=(0, -sweep, 0), s=s)
        self.root = root
        self.names = []
        parent = root.bone.name
        self.seg = []
        for i, (p, f, l) in enumerate(zip((P0, P1, P2), fold, lift)):
            nm = f"{name}{S}{i}"
            part = kbone(m, nm, (p[0], p[1], z + .9), parent=parent, rot=(0, f, l), s=s)
            self.seg.append(part)
            self.names.append(nm)
            parent = nm
        self.fb = {}

    def arm(self, i, a, b, thick, paint, depth=1.8, dz=0.0):
        if not getattr(paint, "glow", False):
            paint = over(paint, mat.sheen(.16, .2), mat.bone_bands(6))
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
        self.seg[i].add((a[0] - thick * .3, a[1] - thick / 2, self.z + dz), (L + thick * .6, thick, depth), paint,
                        rot=(0, 0, ang), pivot=(a[0], a[1], self.z + dz + depth / 2))

    def feathers_bone(self, i):
        """The feathers of segment i hang on a bone of their own under it, so they can flutter in the air."""
        if i not in self.fb:
            p = (P0, P1, P2)[i]
            nm = self.names[i] + "f"
            self.fb[i] = kbone(self.m, nm, (p[0], p[1], self.z + .9), parent=self.names[i], s=self.s)
        return self.fb[i]

    def plane(self, i, x0, y0, x1, y1, paint, z, rz=0.0, pivot=None):
        """A painted cut-out plane x0..x1, y0..y1 at depth z on segment i (the left wing gets it mirrored)."""
        if self.s < 0:
            paint = flipped(paint)
        rot = (0, 0, rz) if rz else None
        self.feathers_bone(i).add((x0, y0, z), (x1 - x0, y1 - y0, 0), paint, rot=rot,
                                  pivot=pivot or (x0, y1, z), only=("back",))

    def block(self, i, o, s, paint, rot=None, pivot=None):
        self.seg[i].add(o, s, paint, rot=rot, pivot=pivot)

    def flap(self, amp=(6, 5, 5), lift=(3, 3, 4), lag=.55, length=LOOP, flutter=(1.5, 3, 5)):
        """A wingbeat: each segment swings back and up a little after the one before it (down quickly, up slowly),
        and the feathers flutter on their own, faster, the most at the tip."""
        s = self.s

        def beat(x):  # a quick downstroke and a slower recovery
            return math.sin(x) + .28 * math.sin(2 * x - .6)

        def stretch(t, i):  # once a loop the wing opens wide and lifts, the tip a moment later
            d = abs(t - (.72 + .03 * i))
            return math.cos(d / .14 * math.pi / 2) ** 2 if d < .14 else 0.0

        for i, nm in enumerate(self.names):
            a, l = amp[i], lift[i]
            self.m.anim(nm, "rotation", (lambda a_, l_, ph, k: lambda t: (
                0, -a_ * s * beat(t * math.pi * 2 - ph) + s * 7 * stretch(t, k),
                l_ * s * beat(t * math.pi * 2 - ph - .5) + s * 6 * stretch(t, k)))(a, l, i * lag, i), length, 96)
        for i, fb in self.fb.items():
            f = flutter[i]
            self.m.anim(fb.bone.name, "rotation", (lambda f_, ph: lambda t: (
                f_ * math.sin(t * math.pi * 4 - ph), 0, .4 * f_ * s * math.sin(t * math.pi * 4 - ph - 1)))(
                f, i * .9), length, 64)


def pair(m, build, **kw):
    """build(wing) for both sides."""
    wings = []
    for s in (1, -1):
        w = Wing(m, s, **kw)
        build(w)
        wings.append(w)
    return wings


def model(n, name):
    return Model(n, name, "wings", D=D, view="wings")


def joints(w, paint):
    """A knuckle at the elbow and the wrist, a claw at the tip."""
    for i, p in ((1, P1), (2, P2)):
        w.block(i, (p[0] - 1.1, p[1] - 1.1, w.z - .1), (2.2, 2.2, 2.0), paint)
    w.block(2, (P3[0] - .4, P3[1] - .3, w.z + .2), (.8, 2.0, .8), paint, rot=(0, 0, -35), pivot=(P3[0], P3[1], w.z + .6))


def feathered(w, arm, cov, sec, prim, long_=14.0, seed=1, extra=None, prim_lean=.18):
    """The common feathered wing on a Wing: arm blocks, coverts over secondaries, long primaries at the tip."""
    w.arm(0, P0, P1, 1.7, arm, depth=1.6)
    w.arm(1, P1, P2, 1.4, arm, depth=1.4)
    w.arm(2, P2, P3, 1.1, arm, depth=1.2)
    joints(w, arm)
    z = w.z + .9
    # secondaries under the shoulder and the elbow
    w.plane(0, 1.2, P0[1] - long_ * .75, 7.6, P1[1] + .2,
            feathers(1.2, P1[1] + .2, line_y(P0, P1), [long_ * k for k in (.42, .5, .58)], sec, width=2.1, seed=seed),
            z)
    w.plane(1, 6.6, P1[1] - long_ * .9, 13.0, P2[1] + .2,
            feathers(6.6, P2[1] + .2, line_y(P1, P2), [long_ * k for k in (.68, .74, .8)], sec, width=2.1,
                     seed=seed + 1), z)
    # primaries at the wrist, longest first, leaning out
    w.plane(2, 12.0, P3[1] - long_ * 1.05, 19.6, P2[1] + .3,
            feathers(12.0, P2[1] + .3, line_y(P2, P3), [long_ * k for k in (.98, .92, .82, .66)], prim, width=1.9,
                     lean=prim_lean, seed=seed + 2), z + .15, rz=10, pivot=(12.0, P2[1], z))
    # an under layer, darker and a little longer, behind: the wing gets depth
    dark = [mul(C(c), .62) for c in sec]
    darkp = [mul(C(c), .62) for c in prim]
    w.plane(1, 6.4, P1[1] - long_ * 1.0, 13.2, P2[1] + .1,
            feathers(6.4, P2[1] + .1, line_y(P1, P2), [long_ * k for k in (.78, .85, .9)], dark, width=2.2,
                     seed=seed + 7, shaft=False), z - .35)
    w.plane(2, 11.8, P3[1] - long_ * 1.15, 19.8, P2[1] + .2,
            feathers(11.8, P2[1] + .2, line_y(P2, P3), [long_ * k for k in (1.06, 1.0, .9, .74)], darkp, width=2.0,
                     lean=prim_lean, seed=seed + 8, shaft=False), z - .2, rz=12, pivot=(11.8, P2[1], z))
    # tufts of small feathers at the shoulder and the elbow
    for i, p in ((0, P0), (1, P1)):
        w.block(i, (p[0] - .8, p[1] - 1.6, w.z + 1.6), (1.6, 1.6, .8), ramp([cov[0], cov[-1]]))
    # coverts: short and light over the top of each segment, in front
    for i, (a, b) in enumerate(((P0, P1), (P1, P2), (P2, P3))):
        x0, x1 = min(a[0], b[0]) - .3, max(a[0], b[0]) + .3
        top = max(a[1], b[1]) + .2
        w.plane(i, x0, top - 5.2, x1, top,
                feathers(x0, top, line_y(a, b), [3.6, 4.4, 3.8, 4.6][: max(2, int((x1 - x0) / 1.8))], cov,
                         width=1.8, seed=seed + 5 + i, gap=False), z + .5)
    if extra:
        extra(w)


# ------------------------------------------------------------------------------------------------ the wings


def eclipse(n):
    """Black feathers burning gold at the tips, a golden arm, a small sun disc on each wrist."""
    m = model(n, "Eclipse Wings")
    arm = ramp(["#ffe08a", "#d9a520", "#8a5a10"])

    def sun(w):
        w.block(2, (P2[0] - 1.2, P2[1] - 1.2, w.z - .2), (2.4, 2.4, 2.4), ramp(["#fff6c4", "#ffd23f", "#ff9a1f"],
                                                                              edge=None), rot=(0, 0, 45),
                pivot=(P2[0], P2[1], w.z + 1))

    for w in pair(m, lambda w: feathered(w, arm, ["#2a2420", "#1a1512", "#3a2a14"],
                                         ["#16120f", "#1f1812", "#5a3a10", "#d9a520"],
                                         ["#120e0b", "#1e1610", "#6a4410", "#ffcf4a", "#fff1a0"], seed=11,
                                         extra=sun)):
        w.flap()
    return m


def crimson_chains(n):
    """Black-and-red ragged feathers, chains swinging from the elbows and wrists, spiked weights on them."""
    m = model(n, "Crimson Chain Wings")
    arm = ramp(["#3a2a2a", "#1e1414"])
    link = over(ramp(["#9aa0a8", "#5a6068", "#33373d"]), L_draw(
        lambda d, w, h, D_, f, img: d.rectangle((w // 2, 1, w // 2, h - 2), fill=rgb("#1a1c20"))))
    spike = ramp(["#c9ced6", "#6f7780"])

    def weight(p, end):
        x, y, z = end
        p.add((x - .8, y - 1.6, z - .8), (1.6, 1.6, 1.6), spike, rot=(45, 45, 0), pivot=(x, y - .8, z))

    def build(w):
        feathered(w, arm, ["#3a1a1e", "#2a1216"], ["#1a1012", "#2a1418", "#7a1424", "#c42a3a"],
                  ["#140c0e", "#24121a", "#8a1a2a", "#e8323c"], seed=21)
        S = "R" if w.s > 0 else "L"
        for i, (pt, n_) in enumerate(((P1, 5), (P2, 6))):
            chain(w.m, f"ch{S}{i}_", None, w.names[i + 1], (pt[0], pt[1] - .8, w.z + 2.0), (0.15, -1.0, 0.05), n_,
                  .7, .7, link, tip=weight, phase=i * 1.3, amp=(9, 7), speed=LOOP, s=w.s)

    for w in pair(m, build, sweep=42):
        w.flap(amp=(5, 4, 4))
    return m


def frost_crystal(n):
    """Bones of blue ice panels; instead of feathers, icicles of every length hanging in rows, crystals at the wrists."""
    m = model(n, "Frost Crystal Wings")
    panel = over(ramp(["#bff4ff", "#6fd0f0", "#3a8ac0"]), L_draw(lambda d, w, h, D_, f, img: [
        d.line((x, 0, x, h), fill=rgb("#2a5a8a")) for x in range(0, w, 3)]))
    ice = [ramp(["#ffffff", "#bff4ff", "#5cc4ef"], edge=None), ramp(["#e6fbff", "#7fdcff", "#3fb7e0"], edge=None)]

    def build(w):
        w.arm(0, P0, P1, 2.4, panel)
        w.arm(1, P1, P2, 2.0, panel)
        w.arm(2, P2, P3, 1.5, panel, depth=1.5)
        r = random.Random(31)
        for i, (a, b) in enumerate(((P0, P1), (P1, P2), (P2, P3))):
            k = 4 if i < 2 else 5
            for j in range(k):
                t = (j + .5) / k
                x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
                L = (5 + i * 3.5) * (0.75 + r.random() * .5)
                th = 1.2 - i * .15
                w.block(i, (x - th / 2, y - L, w.z + .6 + r.random() * .6), (th, L, th), ice[j % 2],
                        rot=(0, 45, 8 + i * 6), pivot=(x, y, w.z + 1.2))
                w.block(i, (x - th * .3, y - L - th, w.z + .9), (th * .6, th, th * .6), ice[0], rot=(0, 45, 8 + i * 6),
                        pivot=(x, y, w.z + 1.2))
        w.block(2, (P3[0] - 1, P3[1] - 1, w.z), (2, 3.4, 2), ice[0], rot=(0, 45, -20), pivot=(P3[0], P3[1], w.z + 1))

    for w in pair(m, build, sweep=38):
        w.flap(amp=(4, 4, 3), lift=(2, 2, 3))
    return m


def toxic_bat(n):
    """A bat's wings in acid green fading to violet, veins, green drops hanging from the edge."""
    m = model(n, "Toxic Bat Wings")
    bone_p = ramp(["#3a2a4a", "#22182e"])
    drop = ramp(["#d6ff8a", "#7cd34a"], edge=None)

    def build(w):
        w.arm(0, P0, P1, 1.8, bone_p)
        w.arm(1, P1, P2, 1.5, bone_p)
        tips = [(P2[0] + 6.5, P2[1] - 6), (P2[0] + 4.5, P2[1] - 12), (P2[0] + 0.5, P2[1] - 15)]
        for tp in tips:
            w.arm(2, P2, tp, .8, bone_p, depth=.8, dz=.5)
        w.block(2, (P2[0] - .4, P2[1] - .2, w.z + .4), (.9, 2.4, .9), ramp(["#e8e3c4", "#b8ae92"]), rot=(0, 0, -30),
                pivot=(P2[0], P2[1], w.z + .8))
        cols = ["#9dff5a", "#5fbf4a", "#5a3a8a", "#3a1f5a"]
        z = w.z + .9
        inner = [(P0[0], P0[1]), (P1[0], P1[1]), (P1[0] + .5, P1[1] - 9), (4.5, 11.5), (P0[0], 13)]
        w.plane(0, 1.0, 10.0, 8.0, 27.5, membrane(1.0, 27.5, inner, cols, veins=[((P1[0], P1[1]), (4, 13))],
                                                   vein_c="#2a1a3a"), z)
        outer = [P1, P2, tips[0], (P2[0] + 3.2, P2[1] - 8.5), tips[1], (P2[0] - 1.2, P2[1] - 11.5), tips[2],
                 (P1[0] + .3, P1[1] - 10)]
        w.plane(1, 6.5, 13.0, 19.5, 29.5, membrane(6.5, 29.5, outer, cols), z)
        r = random.Random(41)
        for k in range(4):
            x = 4 + k * 3.4 + r.random()
            w.block(0 if x < 7 else 1, (x - .3, 10.5 - r.random() * 2, z - .3), (.6, 1.4, .6), drop)

    for w in pair(m, build, sweep=36):
        w.flap(amp=(7, 6, 6), lift=(4, 4, 5))
    return m


def storm(n):
    """Slate-blue feathers, lightning zigzagging down them, sparks hanging by the tips."""
    m = model(n, "Storm Wings")
    arm = ramp(["#5a6478", "#3a4254"])

    def bolt(img, T):
        d = ImageDraw.Draw(img)
        w, h = img.size
        x = w * .5
        y = 2
        pts = [(x, y)]
        r = random.Random(w)
        while y < h - 3:
            y += 3
            x += r.choice((-3, 3))
            pts.append((max(1, min(w - 2, x)), y))
        d.line(pts, fill=rgb("#fff36b"))

    def build(w):
        cols_sec = ["#2a3040", "#3a4660", "#7a94c0", "#cfe0ff"]
        w.arm(0, P0, P1, 2.2, arm)
        w.arm(1, P1, P2, 1.8, arm)
        w.arm(2, P2, P3, 1.3, arm, depth=1.4)
        z = w.z + .9
        w.plane(1, 6.6, P1[1] - 12.6, 13.0, P2[1] + .2, feathers(6.6, P2[1] + .2, line_y(P1, P2), [9.5, 10.5, 11.2],
                                                                cols_sec, width=2.1, seed=51, extra=bolt), z)
        w.plane(0, 1.2, P0[1] - 10.5, 7.6, P1[1] + .2, feathers(1.2, P1[1] + .2, line_y(P0, P1), [6, 7, 8],
                                                               cols_sec, width=2.1, seed=52), z)
        w.plane(2, 12.0, P3[1] - 14.5, 19.6, P2[1] + .3, feathers(12.0, P2[1] + .3, line_y(P2, P3),
                                                                 [13.5, 12.8, 11.2, 9], cols_sec, width=1.9, lean=.18,
                                                                 seed=53, extra=bolt), z + .15, rz=10,
                pivot=(12.0, P2[1], z))
        for i, (a, b) in enumerate(((P0, P1), (P1, P2), (P2, P3))):
            x0, x1 = min(a[0], b[0]) - .3, max(a[0], b[0]) + .3
            top = max(a[1], b[1]) + .2
            w.plane(i, x0, top - 5.2, x1, top, feathers(x0, top, line_y(a, b), [3.6, 4.4, 3.8], ["#5a6a88", "#3a4660"],
                                                        width=1.8, seed=55 + i, gap=False), z + .5)
        for k, (x, y) in enumerate(((17, 12), (14.5, 10.5), (18.5, 16))):
            w.block(2, (x - .4, y - .4, z), (.8, .8, .8), ramp(["#ffffff", "#fff36b"], edge=None), rot=(45, 45, 0),
                    pivot=(x, y, z + .4))

    for w in pair(m, build):
        w.flap(amp=(6, 5, 6))
    return m


def koi(n):
    """Long flowing koi fins, white with orange and black patches and dark rays, rippling like water."""
    m = model(n, "Koi Fin Wings")
    arm = ramp(["#ff9a4a", "#e0602a"])

    def fin(x0, y1, top, lengths, seed):
        def patches(img, T):
            px = img.load()
            w, h = img.size
            r = random.Random(seed)
            for _ in range(5):
                cx, cy, rr = r.randrange(w), r.randrange(h // 2), r.randint(2, 4)
                col = r.choice((rgb("#ff7a1f"), rgb("#ff7a1f"), rgb("#1a1414")))
                for y in range(max(0, cy - rr), min(h, cy + rr)):
                    for x in range(max(0, cx - rr), min(w, cx + rr)):
                        if px[x, y][3] and (x - cx) ** 2 + (y - cy) ** 2 < rr * rr:
                            px[x, y] = mul(col, px[x, y][0] / 255 * .2 + .85)
            for x in range(1, w, 3):
                for y in range(h):
                    if px[x, y][3]:
                        px[x, y] = mul(px[x, y], .82)

        return feathers(x0, y1, top, lengths, ["#ffffff", "#fff6ec", "#ffd8b8", "#ffb07a"], width=2.4, seed=seed,
                        extra=patches, gap=False)

    def build(w):
        w.arm(0, P0, P1, 1.6, arm)
        w.arm(1, P1, P2, 1.3, arm)
        w.arm(2, P2, P3, 1.0, arm, depth=1.0)
        z = w.z + .9
        w.plane(0, 1.2, 9.0, 7.6, P1[1] + .2, fin(1.2, P1[1] + .2, line_y(P0, P1), [10, 12, 13], 61), z)
        w.plane(1, 6.6, 9.0, 13.0, P2[1] + .2, fin(6.6, P2[1] + .2, line_y(P1, P2), [16, 17, 18], 62), z)
        w.plane(2, 12.0, 10.0, 20.0, P2[1] + .3, fin(12.0, P2[1] + .3, line_y(P2, P3), [17, 16, 14, 11], 63), z,
                rz=14, pivot=(12.0, P2[1], z))

    for w in pair(m, build, sweep=34, fold=(0, -10, -16)):
        w.flap(amp=(6, 7, 8), lift=(4, 5, 7), lag=.8)
    return m


def phoenix(n):
    """Feathers of fire, red at the root to yellow-white tips, embers rising off them."""
    m = model(n, "Phoenix Ember Wings")
    arm = ramp(["#ff7a1a", "#c42a0a"])
    for w in pair(m, lambda w: feathered(w, arm, ["#ffd23f", "#ff8a1a"], ["#a8140a", "#e8341c", "#ff8a1a", "#ffd23f"],
                                         ["#8a0e08", "#e8341c", "#ff8a1a", "#ffe14a", "#fff6c4"], seed=71)):
        w.flap(amp=(7, 5, 6))
    em = kbone(m, "embers", (0, 20, 6))
    r = random.Random(n)
    for k in range(12):
        s = r.choice((-1, 1))
        em.add((s * r.uniform(5, 15) - .4, r.uniform(12, 30), r.uniform(5, 11)), (.8, .8, .8),
               ramp(["#fff6c4", "#ffb347"], edge=None))
    m.anim("embers", "position", lambda t: (0, 3.0 * t, 0), LOOP / 2)
    return m


def clockwork(n):
    """Brass wings of overlapping plates, a gear turning at every joint."""
    m = model(n, "Clockwork Wings")
    brass = ramp(["#f3d27a", "#d9a520", "#9a6a10"])
    copper = ramp(["#f0a070", "#c46a2a", "#7a3a14"])
    dark = ramp(["#3a2e22", "#1e1812"])

    def build(w):
        w.arm(0, P0, P1, 2.0, dark)
        w.arm(1, P1, P2, 1.7, dark)
        w.arm(2, P2, P3, 1.3, dark, depth=1.4)
        for i, (a, b) in enumerate(((P0, P1), (P1, P2), (P2, P3))):
            k = 3 if i < 2 else 4
            for j in range(k):
                t = (j + .5) / k
                x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
                L = 6 + i * 3 + j * (1.2 if i == 2 else .6)
                w.block(i, (x - 1.0, y - L, w.z + .7 + j * .12), (2.0, L, .5), brass if (i + j) % 2 else copper,
                        rot=(0, 0, 6 + i * 8 + j * 3), pivot=(x, y, w.z + 1))
                w.block(i, (x - .3, y - L + .3, w.z + 1.25 + j * .12), (.6, L - .8, .15), dark,
                        rot=(0, 0, 6 + i * 8 + j * 3), pivot=(x, y, w.z + 1))
        S = "R" if w.s > 0 else "L"
        for i, p in enumerate((P1, P2)):
            g = kbone(w.m, f"gear{S}{i}", (p[0], p[1], w.z - .4), parent=w.names[i + 1], s=w.s)
            g.add((p[0] - 1.6, p[1] - 1.6, w.z - .6), (3.2, 3.2, .8), brass)
            g.add((p[0] - 1.6, p[1] - 1.6, w.z - .6), (3.2, 3.2, .8), brass, rot=(0, 0, 45), pivot=(p[0], p[1], w.z))
            g.add((p[0] - .5, p[1] - .5, w.z - .9), (1.0, 1.0, .4), dark)
            w.m.anim(g.bone.name, "rotation", (lambda d_: lambda t: (0, 0, d_ * t * 360))(1 if i else -1), LOOP)

    for w in pair(m, build, sweep=40):
        w.flap(amp=(4, 4, 3), lift=(2, 2, 3))
    return m


def void_rift(n):
    """Black feathers split by glowing magenta cracks; shards of the void drift round the tips."""
    m = model(n, "Void Rift Wings")
    arm = ramp(["#2a1a3a", "#120a1e"])

    def cracks(img, T):
        d = ImageDraw.Draw(img)
        w, h = img.size
        r = random.Random(w + h)
        for _ in range(3):
            x, y = r.randrange(w), r.randrange(h // 3)
            pts = [(x, y)]
            for _ in range(5):
                x += r.randint(-2, 2)
                y += r.randint(2, 4)
                pts.append((x, y))
            d.line(pts, fill=rgb("#ff4fd8"))

    def build(w):
        feathered(w, arm, ["#2a1a3a", "#1a1028"], ["#0e0816", "#1a1028", "#2a1640", "#5a2a8a"],
                  ["#0a0610", "#160c22", "#3a1a5a", "#a03fd6"], seed=81)
        z = w.z + .9
        for k in range(3):
            x, y = 15 + k * 1.8, 18 - k * 4
            w.block(2, (x - .6, y - .9, z + 1), (1.2, 1.8, 1.2), ramp(["#ff9cf0", "#c42aa8"], edge=None),
                    rot=(30, 45, 20), pivot=(x, y, z + 1.6))

    for w in pair(m, build):
        w.flap(amp=(6, 5, 5))
    # the drifting shards round both wings
    sh = kbone(m, "shards", (0, 25, 8))
    for k in range(6):
        a = k * 60
        x, y = math.cos(math.radians(a)) * 12, 25 + math.sin(math.radians(a)) * 6
        sh.add((x - .6, y - .9, 8), (1.2, 1.8, 1.2), ramp(["#ff9cf0", "#a03fd6"], edge=None), rot=(20, 45, a),
               pivot=(x, y, 8.6))
    m.anim("shards", "rotation", lambda t: (0, 0, t * 360), LOOP * 2)
    return m


def seraph(n):
    """Six wings: a small pair raised over the shoulders, the great pair, a small pair folded down."""
    m = model(n, "Seraph Six Wings")
    arm = ramp(["#fff1c4", "#e0b040"])
    cov, sec, prim = ["#ffffff", "#f2f5fb"], ["#ffffff", "#eef2f9", "#dde5f2", "#fff1c4"], \
        ["#ffffff", "#f2f5fb", "#e6ecf7", "#ffe08a", "#e0b040"]
    for w in pair(m, lambda w: feathered(w, arm, cov, sec, prim, long_=13, seed=91), name="mid"):
        w.flap(amp=(5, 4, 4))
    for w in pair(m, lambda w: feathered(w, arm, cov, sec, prim, long_=8, seed=93), name="top", sweep=22,
                  fold=(0, -4, -6), lift=(16, 2, -2), z=2.3):
        w.flap(amp=(4, 4, 4), lag=.7)
    for w in pair(m, lambda w: feathered(w, arm, cov, sec, prim, long_=7, seed=95), name="low", sweep=40,
                  fold=(0, -4, -6), lift=(-30, -4, 0), z=2.9):
        w.flap(amp=(3, 3, 3), lag=.4)
    return m


# ------------------------------------------------------------------------------------------------ more wings


def butterfly_pic(x0, y1, poly, cols, vein_c, dots_c=None, dots=(), seed=3):
    """A butterfly's wing: the ramp, dark veins fanning from the root, a dark border with pale dots."""

    def fn(img, face, w, h, D_, rnd):
        from PIL import ImageDraw as _D
        d = _D.Draw(img)
        T = lambda p: ((p[0] - x0) * D_, (y1 - p[1]) * D_)
        d.polygon([T(p) for p in poly], fill=(255, 255, 255, 255))
        px = img.load()
        for y in range(h):
            for x in range(w):
                if px[x, y][3]:
                    px[x, y] = mul(dith(cols, y / h, rnd), rnd.choice((.9, .96, 1, 1.04)))
        root = T(poly[0])
        for q in poly[1:]:
            d.line((root, T(q)), fill=C(vein_c))
        for k in range(len(poly)):
            d.line((T(poly[k]), T(poly[(k + 1) % len(poly)])), fill=C(vein_c), width=3)
        for p_ in dots:
            x, y = T(p_)
            d.rectangle((x, y, x + 1, y + 1), fill=C(dots_c))
        outline(img, .5)

    return picture(fn)


def obsidian_dragon(n):
    """A great dragon's wings, black skin veined with lava, horns along the arm."""
    m = model(n, "Obsidian Dragon Wings")
    bone_p = ramp(["#3a2a2a", "#1a1212"])
    horn = ramp(["#fff1d6", "#c9b08a"])

    def build(w):
        w.arm(0, P0, P1, 2.0, bone_p)
        w.arm(1, P1, P2, 1.6, bone_p)
        tips = [(P2[0] + 7.5, P2[1] - 5), (P2[0] + 5.5, P2[1] - 12.5), (P2[0] + 1.0, P2[1] - 17)]
        for tp in tips:
            w.arm(2, P2, tp, .9, bone_p, depth=.9, dz=.45)
        joints(w, bone_p)
        cols = ["#2a1a1a", "#1a1010", "#120a0a"]
        z = w.z + .9
        veins = [((P1[0], P1[1]), (3.0, 12.0)), ((P1[0], P1[1]), (6.0, 11.5))]
        inner = [P0, P1, (P1[0] + .5, P1[1] - 11), (4.5, 10.5), (P0[0], 12)]
        w.plane(0, 1.0, 9.5, 8.0, 28.5, membrane(1.0, 28.5, inner, cols, veins=veins, vein_c="#ff6a1a"), z)
        outer = [P1, P2, tips[0], (P2[0] + 3.6, P2[1] - 9), tips[1], (P2[0] - 1.0, P2[1] - 13), tips[2],
                 (P1[0] + .2, P1[1] - 11.5)]
        ov = [(P2, tips[0]), (P2, tips[1]), (P2, tips[2]), ((P1[0] + 2, P1[1]), (P1[0] + 1, P1[1] - 10))]
        w.plane(1, 6.5, 13.0, 21.0, 32.0, membrane(6.5, 32.0, outer, cols, veins=ov, vein_c="#ff7a2a"), z)
        for k in range(4):  # horns along the arm
            t = (k + .5) / 4
            a, b = (P0, P1) if k < 2 else (P1, P2)
            tt = (t * 2) % 1
            x, y = a[0] + (b[0] - a[0]) * tt, a[1] + (b[1] - a[1]) * tt
            w.block(0 if k < 2 else 1, (x - .4, y + .6, w.z + .4), (.8, 1.8, .8), horn, rot=(0, 0, -25),
                    pivot=(x, y + .6, w.z + .8))

    for w in pair(m, build, sweep=34):
        w.flap(amp=(8, 6, 7), lift=(5, 4, 6), flutter=(1, 2, 4))
    return m


def monarch(n):
    """Monarch butterfly wings: orange, black veins, white dots along the black border, fluttering quickly."""
    m = model(n, "Monarch Wings")
    up = [(1.5, 22), (5, 31), (12, 33), (17, 30), (16, 24), (9, 20.5)]
    lo = [(1.5, 20.5), (9, 19.5), (11.5, 14), (8, 9.5), (3, 11)]

    def build(w):
        w.arm(0, (1.2, 21.0), (2.2, 22.0), .8, ramp(["#16161a"]), depth=.8)
        z = w.z + .9
        dots_u = [(12, 32.3), (14, 31.5), (16, 29.6), (16.2, 26), (15.4, 24.4)]
        dots_l = [(9.8, 17), (10.6, 15), (8.6, 11), (6.2, 10), (4, 10.8)]
        w.plane(1, 1.5, 20.0, 17.5, 33.5, butterfly_pic(1.5, 33.5, up, ["#ffb347", "#ff8a1a", "#e8601a"],
                                                        "#16161a", "#ffffff", dots_u), z)
        w.plane(0, 1.5, 9.0, 12.0, 21.0, butterfly_pic(1.5, 21.0, lo, ["#ff9a3a", "#e8701a", "#c4500e"],
                                                       "#16161a", "#ffffff", dots_l), z + .1)

    for w in pair(m, build, sweep=18, fold=(0, 0, 0), lift=(0, 0, 0)):
        w.flap(amp=(22, 4, 2), lift=(6, 2, 1), lag=.2, flutter=(3, 3, 3))
    return m


def sakura(n):
    """A blossoming branch for a wing: pink petals hang from it, blossoms cluster on it, petals drift down."""
    m = model(n, "Sakura Branch Wings")
    wood = ramp(["#7a5240", "#4a3020"])
    pink = ["#fff0f6", "#ffc6dc", "#ff9cc2", "#e86a9a"]
    bloom = [ramp(["#ffffff", "#ffd6e6"]), ramp(["#ffc6dc", "#ff8fb8"])]

    def build(w):
        w.arm(0, P0, P1, 1.4, wood)
        w.arm(1, P1, P2, 1.1, wood)
        w.arm(2, P2, P3, .8, wood, depth=.9)
        z = w.z + .9
        w.plane(0, 1.2, 12.0, 7.6, P1[1] + .2, feathers(1.2, P1[1] + .2, line_y(P0, P1), [5, 6.5, 7.5], pink,
                                                        width=2.1, seed=101, gap=False), z)
        w.plane(1, 6.6, 15.0, 13.0, P2[1] + .2, feathers(6.6, P2[1] + .2, line_y(P1, P2), [8.5, 9.5, 10], pink,
                                                         width=2.1, seed=102, gap=False), z)
        w.plane(2, 12.0, 15.0, 19.6, P2[1] + .3, feathers(12.0, P2[1] + .3, line_y(P2, P3), [10, 9, 8, 6], pink,
                                                          width=1.9, lean=.15, seed=103, gap=False), z + .15, rz=8,
                pivot=(12.0, P2[1], z))
        r = random.Random(7)
        for i, (a, b) in enumerate(((P0, P1), (P1, P2), (P2, P3))):
            for k in range(3):
                t = (k + .5) / 3
                x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
                w.block(i, (x - .7, y - .1, w.z + .2 + r.random() * .6), (1.4, 1.4, 1.0), bloom[(i + k) % 2])

    for w in pair(m, build, sweep=32):
        w.flap(amp=(4, 4, 4), lift=(2, 2, 3), flutter=(2, 3, 4))
    fall = kbone(m, "petals", (0, 24, 6))
    r = random.Random(n)
    for k in range(12):
        s_ = r.choice((-1, 1))
        fall.add((s_ * r.uniform(4, 16), r.uniform(10, 26), r.uniform(5, 10)), (.7, .7, .2),
                 ramp(["#ffd6e6", "#ff9cc2"], edge=None))
    m.anim("petals", "position", lambda t: (1.5 * math.sin(t * math.pi * 2), -4.0 * t, 0), LOOP)
    return m


def jet(n):
    """Jet wings: overlapping steel plates, a thruster at each wrist breathing fire."""
    m = model(n, "Jet Wings")
    plate = ramp(["#e6eaf0", "#aab2bc", "#6f7887"])
    dark = ramp(["#3a3f48", "#22262c"])
    stripe = ramp(["#ff3a3a", "#c41a1a"])
    fire = ramp(["#ffffff", "#9ff0ff", "#3fb7e0", "#1a5fd6"], edge=None)

    def build(w):
        w.arm(0, P0, P1, 2.0, dark)
        w.arm(1, P1, P2, 1.7, dark)
        w.arm(2, P2, P3, 1.3, dark, depth=1.4)
        for i, (a, b) in enumerate(((P0, P1), (P1, P2), (P2, P3))):
            for j in range(3):
                t = (j + .5) / 3
                x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
                L = 5 + i * 2.6 + j * .5
                w.block(i, (x - 1.1, y - L, w.z + .6 + j * .1), (2.2, L, .5), plate, rot=(0, 0, 4 + i * 7 + j * 2),
                        pivot=(x, y, w.z + 1))
                if j == 1:
                    w.block(i, (x - .3, y - L + .4, w.z + 1.12), (.6, L - 1, .1), stripe,
                            rot=(0, 0, 4 + i * 7 + j * 2), pivot=(x, y, w.z + 1))
        S = "R" if w.s > 0 else "L"
        w.block(2, (P2[0] - .9, P2[1] - 2.6, w.z + 1.6), (1.8, 2.4, 1.8), dark)
        f = kbone(w.m, f"thrust{S}", (P2[0], P2[1] - 2.6, w.z + 2.5), parent=w.names[2], s=w.s)
        f.add((P2[0] - .6, P2[1] - 5.6, w.z + 1.9), (1.2, 3.0, 1.2), fire)
        w.m.anim(f.bone.name, "scale", lambda t: (1, .75 + .35 * abs(math.sin(t * math.pi * 6)), 1), LOOP)

    for w in pair(m, build, sweep=38):
        w.flap(amp=(3, 3, 3), lift=(2, 2, 2), flutter=(0, 0, 0))
    return m


def reaper(n):
    """Bone wings hung with a torn black shroud that ripples."""
    m = model(n, "Reaper Shroud Wings")
    bone_p = ramp(["#f5f0e0", "#d9d0b6", "#b8ae92"])

    def torn(x0, y1, top, lengths, seed):
        def holes(img, T):
            px = img.load()
            w, h = img.size
            r = random.Random(seed)
            for _ in range(6):
                cx, cy = r.randrange(w), r.randrange(h // 3, h)
                for y in range(cy, min(h, cy + r.randint(2, 4))):
                    for x in range(cx, min(w, cx + r.randint(1, 3))):
                        px[x, y] = (0, 0, 0, 0)

        return feathers(x0, y1, top, lengths, ["#2a2a33", "#1c1c22", "#121216", "#3a1a4a"], width=2.4, seed=seed,
                        gap=False, extra=holes)

    def build(w):
        w.arm(0, P0, P1, 1.4, bone_p)
        w.arm(1, P1, P2, 1.1, bone_p)
        w.arm(2, P2, P3, .9, bone_p, depth=.9)
        joints(w, bone_p)
        z = w.z + .9
        w.plane(0, 1.2, 9.0, 7.6, P1[1] + .2, torn(1.2, P1[1] + .2, line_y(P0, P1), [10, 12, 13], 111), z)
        w.plane(1, 6.6, 10.0, 13.0, P2[1] + .2, torn(6.6, P2[1] + .2, line_y(P1, P2), [16, 17, 18], 112), z)
        w.plane(2, 12.0, 9.0, 20.0, P2[1] + .3, torn(12.0, P2[1] + .3, line_y(P2, P3), [18, 16, 14, 11], 113), z,
                rz=12, pivot=(12.0, P2[1], z))

    for w in pair(m, build, sweep=30):
        w.flap(amp=(5, 4, 4), lift=(3, 3, 4), flutter=(4, 6, 8))
    return m


def fairy(n):
    """A fairy's wings: two pale lobes a side shimmering pink, lilac and blue, sparkles on them."""
    m = model(n, "Glimmer Fairy Wings")
    up = [(1.5, 22.5), (6, 33.5), (11, 35), (13, 31), (10, 25), (5, 21.5)]
    lo = [(1.5, 21), (6, 20.5), (9, 16), (7.5, 11.5), (3.5, 12.5)]

    def build(w):
        z = w.z + .9
        cols_u = ["#fff0fa", "#ffc6ec", "#c9b8ff", "#9ae8ff"]
        cols_l = ["#e6f8ff", "#b8e6ff", "#d6c6ff", "#ffc6ec"]
        dots = [(7, 30), (9, 27), (5, 26), (10.5, 32)]
        w.plane(1, 1.5, 21.0, 13.5, 35.5, butterfly_pic(1.5, 35.5, up, cols_u, "#ffffff", "#ffffff", dots), z)
        w.plane(0, 1.5, 11.0, 9.5, 21.5, butterfly_pic(1.5, 21.5, lo, cols_l, "#ffffff", "#ffffff",
                                                       [(5, 15), (7, 18)]), z + .1)

    for w in pair(m, build, sweep=14, fold=(0, 0, 0), lift=(0, 0, 0)):
        w.flap(amp=(26, 4, 2), lift=(4, 2, 1), lag=.15, flutter=(4, 4, 4), length=LOOP / 4)
    return m


# ------------------------------------------------------------------------------------------------ Pudge's arcana


def feast(n, name, pal):
    """After Pudge's Feast of Abscession: two great chains rising off a harness on the back, over the shoulders,
    a crescent hook on each, steel with a molten edge; fire licking along the links, embers rising; two short
    chains with small hooks hanging at the waist. Everything sways a little after what it hangs from."""
    m = Model(n, name, "wings", D=2, view="wings")
    iron = over(ramp([pal["iron"], pal["iron2"], "#0e0a09"]), mat.grain(.06), mat.sheen(.14, .22))
    hot = ramp(pal["hot"], edge=None)
    fire = ramp(pal["fire"], edge=None)

    def link(even):
        def l(img, face, D_, rnd):
            w, h = img.size
            px = img.load()
            if w > 2 and h > 2:
                for y in range(1, h - 1):
                    px[w // 2, y] = C("#050404")  # the link's hole
            px[0, 0] = C(pal["fire"][1])

        return over(iron, l)

    har = kbone(m, "harness", (0, 19, 2.4))
    cracks = over(ramp([pal["iron"], pal["iron2"]]), L_draw(lambda d, w, h, D_, f, img: [
        d.line((x, 0, x + 2, h // 2, x - 1, h), fill=C(pal["hot"][1])) for x in (3, 8, 12)]))
    har.add((-3.6, 14.2, 2.2), (7.2, 8.6, 1.4), cracks)
    har.add((-1.8, 16.4, 3.55), (3.6, 4.0, .6), hot)
    har.add((-2.6, 15.6, 3.4), (5.2, 5.6, .3), iron)
    for x in (-3.0, 2.2):
        har.add((x, 23.0, -2.6), (.8, .6, 5.6), iron)  # straps over the shoulders
        har.add((x - .3, 21.6, 3.2), (1.4, 1.4, 1.4), iron)
    core = kbone(m, "core", (0, 18.4, 4.2), parent="harness")
    core.add((-1.0, 17.4, 4.1), (2.0, 2.0, .4), ramp(["#ffffff"] + pal["hot"][:2], edge=None))
    m.anim("core", "scale", lambda t: (1 + .15 * math.sin(t * math.pi * 4), 1 + .15 * math.sin(t * math.pi * 4), 1),
           LOOP)

    def big_chain(s, ph):
        prev = "harness"
        x, y, z = 2.6, 22.6, 3.6
        step = (0.5, 1.45, 0.35)
        S = "R" if s > 0 else "L"
        for k in range(11):
            nm = f"c{S}{k}"
            b = kbone(m, nm, (x, y, z), parent=prev, s=s)
            L = math.hypot(step[0], step[1])
            ang = math.degrees(math.atan2(step[0], step[1]))
            even = k % 2 == 0
            size = (1.5, L + .5, .5) if even else (.5, L + .5, 1.5)
            b.add((x - size[0] / 2, y - .25, z - size[2] / 2), size, link(even), rot=(0, 0, -ang), pivot=(x, y, z))
            if k in (2, 5, 8):  # a lick of fire on the link
                fl = kbone(m, f"f{S}{k}", (x, y + .6, z), parent=nm, s=s)
                fl.add((x - .5, y + .4, z - .5), (1.0, 1.8, 1.0), fire)
                m.anim(fl.bone.name, "scale", (lambda q: lambda t: (1, .7 + .4 * abs(math.sin(t * math.pi * 6 + q)),
                                                                    1))(k), LOOP)
            m.anim(nm, "rotation", (lambda q: lambda t: (2.5 * math.sin(t * math.pi * 2 - q * .45 - ph), 0,
                                                         3.5 * s * math.sin(t * math.pi * 2 - q * .45 - ph + 1)))(k),
                   LOOP, 64)
            prev = nm
            x, y, z = x + step[0], y + step[1], z + step[2]
            c, sn = math.cos(math.radians(-6.5)), math.sin(math.radians(-6.5))
            step = (step[0] * c - step[1] * sn, step[0] * sn + step[1] * c, step[2])
        # the hook: an eye, then a crescent blade sweeping round and hooking down, its inner edge molten
        hk = kbone(m, f"hook{S}", (x, y, z), parent=prev, s=s)
        hk.add((x - 1.0, y - .6, z - .6), (2.0, 2.0, 1.2), iron)
        R_ = 6.6
        cx, cy = x + .866 * R_, y + .55 * R_  # the blade starts at the chain's end (its heel) and sweeps over
        pts = [(cx + math.cos(math.radians(210 - k * 16)) * R_, cy + math.sin(math.radians(210 - k * 16)) * R_ * 1.05)
               for k in range(11)]
        for k in range(len(pts) - 1):
            (x1, y1), (x2, y2) = pts[k], pts[k + 1]
            L = math.hypot(x2 - x1, y2 - y1)
            a = math.degrees(math.atan2(y2 - y1, x2 - x1))
            t = k / (len(pts) - 2)
            wd = 3.6 * math.sin(math.pi * min(1, .25 + t * .85)) ** .6 * (1 - t) ** .5 + .3  # widest in its belly
            hk.add((x1, y1 - wd / 2, z - .35), (L + .3, wd, .7), iron, rot=(0, 0, a), pivot=(x1, y1, z))
            hk.add((x1, y1 - wd / 2 - .5, z - .25), (L + .3, .6, .5), hot, rot=(0, 0, a), pivot=(x1, y1, z))
        hk.add((pts[1][0] - .5, pts[1][1] - .4, z - .4), (1.0, 2.8, .8), iron, rot=(0, 0, 150),
               pivot=(pts[1][0], pts[1][1], z))  # a spike off its heel
        fl = kbone(m, f"hf{S}", (pts[4][0], pts[4][1], z), parent=f"hook{S}", s=s)
        fl.add((pts[4][0] - .7, pts[4][1] + .4, z - .7), (1.4, 2.4, 1.4), fire)
        m.anim(fl.bone.name, "scale", lambda t: (1, .7 + .45 * abs(math.sin(t * math.pi * 4 + 1)), 1), LOOP)
        m.anim(f"hook{S}", "rotation", lambda t: (3 * math.sin(t * math.pi * 2 - 5.5 - ph), 0,
                                                  5 * s * math.sin(t * math.pi * 2 - 5 - ph)), LOOP, 64)

    def small_chain(s, ph):
        prev = "harness"
        x, y, z = 2.8, 15.0, 3.6
        S = "R" if s > 0 else "L"
        for k in range(5):
            nm = f"s{S}{k}"
            b = kbone(m, nm, (x, y, z), parent=prev, s=s)
            size = (1.1, 1.3, .4) if k % 2 == 0 else (.4, 1.3, 1.1)
            b.add((x - size[0] / 2, y - 1.3, z - size[2] / 2), size, link(k % 2 == 0))
            m.anim(nm, "rotation", (lambda q: lambda t: (6 * math.sin(t * math.pi * 2 - q * .6 - ph), 0,
                                                         5 * s * math.sin(t * math.pi * 2 - q * .6 - ph + 1)))(k),
                   LOOP, 48)
            prev = nm
            x, y, z = x + .25, y - 1.1, z + .1
        hk = kbone(m, f"shook{S}", (x, y, z), parent=prev, s=s)
        pts = [(x + math.cos(math.radians(160 - k * 30)) * 2.2 + 1.6, y - 2.2 + math.sin(math.radians(160 - k * 30))
                * 2.4) for k in range(8)]
        for k in range(len(pts) - 1):
            (x1, y1), (x2, y2) = pts[k], pts[k + 1]
            L = math.hypot(x2 - x1, y2 - y1)
            a = math.degrees(math.atan2(y2 - y1, x2 - x1))
            wd = 1.2 * (1 - k / 7) + .25
            hk.add((x1, y1 - wd / 2, z - .3), (L + .25, wd, .6), iron, rot=(0, 0, a), pivot=(x1, y1, z))
            hk.add((x1, y1 - wd / 2 - .3, z - .2), (L + .25, .35, .4), hot, rot=(0, 0, a), pivot=(x1, y1, z))

    for s, ph in ((1, 0.0), (-1, 1.6)):
        big_chain(s, ph)
        small_chain(s, ph + .8)
    em = kbone(m, "embers", (0, 30, 5))
    r = random.Random(n)
    for k in range(16):
        s = r.choice((-1, 1))
        em.add((s * r.uniform(3, 14) - .35, r.uniform(20, 38), r.uniform(3.5, 8)), (.7, .7, .7),
               ramp([pal["fire"][0], pal["fire"][2]], edge=None))
    m.anim("embers", "position", lambda t: (.6 * math.sin(t * math.pi * 4), 3.5 * t, 0), LOOP)
    return m


def feast_crimson(n):
    return feast(n, "Feast of Abscession Crimson", {
        "iron": "#4a3a36", "iron2": "#2a2220", "hot": ["#fff1a0", "#ffb347", "#ff5a1a", "#c4200a"],
        "fire": ["#fff6c4", "#ffb347", "#ff5a1a", "#a8140a"]})


def feast_verdant(n):
    return feast(n, "Feast of Abscession Verdant", {
        "iron": "#2e3a34", "iron2": "#1a2420", "hot": ["#f0ffc4", "#b8ff5a", "#5fd63a", "#1f7a2a"],
        "fire": ["#f6ffd6", "#c9ff6a", "#5fd63a", "#1a5a1c"]})


ALL = [feast_crimson, feast_verdant, eclipse, crimson_chains, frost_crystal, toxic_bat, storm, koi, phoenix, clockwork,
       void_rift, seraph, obsidian_dragon, monarch, sakura, jet, reaper, fairy]
