"""Materials, as layers laid over a colour: each makes a face read as what it is made of - leather grain and creases,
ripstop nylon, twill, woven canvas, knitted cables, fur, metal with its shine, glass with reflections, bone, scales.
Every layer only darkens or lightens what is painted under it, so it keeps the colour and the logos."""

import math
import random

from wearlib import mix, mul


def _rng(face, w, h, seed):
    return random.Random(hash((face, w, h, seed)) & 0xFFFFFFFF)


def _scale(px, x, y, k):
    c = px[x, y]
    if c[3]:
        px[x, y] = mul(c, k)


def _lift(px, x, y, t):
    c = px[x, y]
    if c[3]:
        px[x, y] = mix(c, (255, 255, 255, c[3]), t)


def grain(density=.09, seed=1):
    """Leather: fine darker pits, a few lighter ones, short creases."""

    def l(img, face, D, rnd):
        w, h = img.size
        r = _rng(face, w, h, seed)
        px = img.load()
        for _ in range(int(w * h * density)):
            _scale(px, r.randrange(w), r.randrange(h), r.choice((.82, .86, .9, 1.08)))
        for _ in range(max(1, w * h // 160)):
            x, y = r.randrange(w), r.randrange(h)
            for k in range(r.randint(2, 4)):
                if 0 <= x + k < w:
                    _scale(px, x + k, y, .84)

    return l


def ripstop(step=4, k=1.08):
    """Ripstop nylon: a grid of slightly raised threads, and a soft sheen across."""

    def l(img, face, D, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                if x % step == 0 or y % step == 0:
                    _scale(px, x, y, k)
        for y in range(h):
            for x in range(w):
                d = abs((x - y * .7) - w * .3) / max(1, w)
                if d < .12:
                    _lift(px, x, y, .07 * (1 - d / .12))

    return l


def twill(k=1.08):
    """Twill and denim: fine diagonal ribs."""

    def l(img, face, D, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                if (x + y) % 3 == 0:
                    _scale(px, x, y, k)
                elif (x + y) % 3 == 1:
                    _scale(px, x, y, .96)

    return l


def weave(k=.93):
    """Woven canvas: a fine checker of over-and-under threads."""

    def l(img, face, D, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                if (x // 1 + y // 1) % 2:
                    _scale(px, x, y, k)

    return l


def cable(k=.78):
    """A knitted cable: columns of little V stitches, darker where they cross."""

    def l(img, face, D, rnd):
        if face in ("top", "bottom"):
            return
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                col = x % 4
                if (col == 0) or (col == 1 and y % 2 == 0) or (col == 2 and y % 2 == 1):
                    _scale(px, x, y, k if col == 0 else .9)

    return l


def fur(seed=3, k=.86):
    """Fur: short strands of lighter and darker running down."""

    def l(img, face, D, rnd):
        w, h = img.size
        r = _rng(face, w, h, seed)
        px = img.load()
        for _ in range(max(2, w * h // 5)):
            x, y = r.randrange(w), r.randrange(h)
            kk = r.choice((k, k, 1.12))
            for d in range(r.randint(2, 3)):
                if y + d < h:
                    _scale(px, x, y + d, kk)

    return l


def blotch(seed=5, amp=.06, cell=2):
    """Hand-painted unevenness: soft patches a little lighter or darker, larger than the grain."""

    def l(img, face, D, rnd):
        w, h = img.size
        r = _rng(face, w, h, seed)
        px = img.load()
        cw, ch = max(1, w // cell + 1), max(1, h // cell + 1)
        g = [[1 + r.uniform(-amp, amp) for _ in range(cw + 1)] for _ in range(ch + 1)]
        for y in range(h):
            for x in range(w):
                _scale(px, x, y, g[y // cell][x // cell])

    return l


def sheen(width=.18, t=.35):
    """Metal: a bright streak across, a lighter top edge, a darker bottom edge."""

    def l(img, face, D, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                d = abs((x + y * .9) / max(1, w + h * .9) - .4)
                if d < width:
                    _lift(px, x, y, t * (1 - d / width))
        for x in range(w):
            _lift(px, x, 0, .25)
            if h > 2:
                _scale(px, x, h - 1, .7)

    return l


def glass(t=.38):
    """Glass: two thin bright reflections slanting across."""

    def l(img, face, D, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                u = (x - y * .8) / max(1, w)
                if abs(u - .18) < .04 or abs(u - .3) < .02:
                    _lift(px, x, y, t)

    return l


def bone_bands(step=5):
    """Bone: rings every few texels, a pale highlight along the top."""

    def l(img, face, D, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                if x % step == 0:
                    _scale(px, x, y, .8)
            if y == 0:
                for x in range(w):
                    _lift(px, x, y, .18)

    return l


def scales(k=.8):
    """Scales: overlapping rows of little arcs, a lighter crown on each."""

    def l(img, face, D, rnd):
        w, h = img.size
        px = img.load()
        for y in range(h):
            for x in range(w):
                row = y // 3
                xx = (x + (row % 2) * 2) % 4
                yy = y % 3
                if yy == 2 or (yy == 1 and xx in (0, 3)):
                    _scale(px, x, y, k)
                elif yy == 0 and xx in (1, 2):
                    _lift(px, x, y, .1)

    return l
