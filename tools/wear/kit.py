"""Shared building blocks for the hand-made designs: placed/mirrored cube adders, pixel art on a face, chains of
segments (tentacles, tails) that bend as one."""

import math

from PIL import ImageDraw

from wearlib import *


class Part:
    """Adds cubes to a bone, moved by off and mirrored left/right when s = -1 (rotations mirrored too)."""

    def __init__(self, bone, off=(0, 0, 0), s=1):
        self.bone, self.off, self.s = bone, off, s

    def mirror(self):
        return Part(self.bone, self.off, -self.s)

    def add(self, o, size, paint, rot=None, pivot=None, only=None, inflate=0.0):
        x, y, z = o
        if self.s < 0:
            x = -(x + size[0])
            if pivot is not None:
                pivot = (-pivot[0], pivot[1], pivot[2])
            if rot is not None:
                rot = (rot[0], -rot[1], -rot[2])
        o2 = (x + self.off[0], y + self.off[1], z + self.off[2])
        pv = None if pivot is None else (pivot[0] + self.off[0], pivot[1] + self.off[1], pivot[2] + self.off[2])
        self.bone.add(Cube(o2, size, paint, rot=rot, pivot=pv, only=only, inflate=inflate))
        return self

    def both(self, o, size, paint, **kw):
        """The cube and its mirror image."""
        self.add(o, size, paint, **kw)
        self.mirror().add(o, size, paint, **kw)
        return self


def bone(m, name, pivot, off=(0, 0, 0), parent=None, rot=None, s=1):
    """A bone whose pivot is given before the offset; returns a Part adding to it."""
    pv = (pivot[0] * s + off[0], pivot[1] + off[1], pivot[2] + off[2])
    b = m.bone(name, pivot=pv, parent=parent, rot=None if rot is None else (rot[0], rot[1] * s, rot[2] * s))
    return Part(b, off, s)


def art(rows, pal, x="c", y=0, faces_=("front",)):
    """Pixel art on a face: rows of palette keys ('.' clear), at texel (x, y); x="c" centres it."""

    def l(img, face, D_, rnd):
        if face not in faces_:
            return
        w, h = img.size
        sw = max(len(r) for r in rows)
        X = (w - sw) // 2 if x == "c" else (x if x >= 0 else w + x - sw)
        Y = y if y >= 0 else h + y - len(rows)
        px = img.load()
        for r, row in enumerate(rows):
            for k, ch in enumerate(row):
                if ch in ". " or ch not in pal:
                    continue
                X2, Y2 = X + k, Y + r
                if 0 <= X2 < w and 0 <= Y2 < h:
                    c = pal[ch]
                    px[X2, Y2] = (0, 0, 0, 0) if c is None else C(c)

    return l


def cut(rect_fn, faces_=("front",)):
    """Cuts texels out of a face: rect_fn(w, h) -> list of (x0, y0, x1, y1)."""

    def l(img, face, D_, rnd):
        if face not in faces_:
            return
        d = ImageDraw.Draw(img)
        for r in rect_fn(*img.size):
            d.rectangle(r, fill=(0, 0, 0, 0))

    return l


def chain(m, name, root, parent, start, step, n, thick0, thick1, paint, tip=None, phase=0.0, amp=(14, 10),
          speed=2.4, off=(0, 0, 0), s=1, anim=True, end_paint=None, curl=0.0):
    """A bending chain (tentacle, tail): n segments, each a bone of its own under the one before, from start along
    step (dx, dy, dz) per segment, tapering from thick0 to thick1; each sways a little later than the one before."""
    prev = parent
    x, y, z = start
    names = []
    for i in range(n):
        t = i / max(1, n - 1)
        th = thick0 + (thick1 - thick0) * t
        bn = f"{name}{i}"
        p = bone(m, bn, (x, y, z), off=off, parent=prev, s=s)
        L = math.sqrt(step[0] ** 2 + step[1] ** 2 + step[2] ** 2)
        # each segment a cube from its joint along the step's direction
        ang_z = math.degrees(math.atan2(step[0], step[1]))  # lean sideways
        ang_x = math.degrees(math.atan2(step[2], step[1]))
        paint_i = end_paint if (end_paint and i == n - 1) else paint
        p.add((x - th / 2, y, z - th / 2), (th, L + th * .9, th), paint_i, rot=(ang_x, 0, -ang_z), pivot=(x, y, z))
        if anim:
            m.anim(bn, "rotation", (lambda k: lambda t_: (
                amp[0] * math.sin(t_ * math.pi * 2 + phase + k * .7),
                0,
                amp[1] * s * math.cos(t_ * math.pi * 2 + phase + k * .7)))(i), speed, 24)
        names.append(bn)
        prev = bn
        x, y, z = x + step[0], y + step[1], z + step[2]
        if curl:  # the next segment turned a little further round (in the x-y plane)
            c, sn = math.cos(math.radians(curl)), math.sin(math.radians(curl))
            step = (step[0] * c - step[1] * sn, step[0] * sn + step[1] * c, step[2])
    if tip:
        tip(Part(m.bones[-1], off, s), (x, y, z))
    return names


def inside(poly, x, y):
    """Whether (x, y) is inside the polygon (even-odd)."""
    c = False
    n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            c = not c
    return c


def voxels(part, poly, z, depth, colours, cell=1.0, edge=None, colour_at=None):
    """Fills a flat outline (model x, y) with blocks: row by row, runs of cells inside it become cubes cell high and
    depth deep - a stepped, blocky edge like the first wings. colours run down a ramp from the top row to the bottom;
    colour_at(x, y) may give a cell its own colour (patterns), cells of one colour in a row merging."""
    ys = [p[1] for p in poly]
    xs = [p[0] for p in poly]
    y = max(ys) - cell
    rows = []
    while y >= min(ys) - 1e-6:
        rows.append(y)
        y -= cell
    n = max(1, len(rows) - 1)
    for ri, y in enumerate(rows):
        base = colours[min(len(colours) - 1, int(ri / n * (len(colours) - 1) + .5))] if colours else None
        x = min(xs)
        run = None
        while x <= max(xs) + 1e-6:
            if inside(poly, x + cell / 2, y + cell / 2):
                col = colour_at(x + cell / 2, y + cell / 2) if colour_at else None
                col = col or base
                if run and run[2] == col:
                    run[1] = x + cell
                else:
                    if run:
                        part.add((run[0], y, z), (run[1] - run[0], cell, depth), mc(run[2], edge=edge or .82))
                    run = [x, x + cell, col]
            elif run:
                part.add((run[0], y, z), (run[1] - run[0], cell, depth), mc(run[2], edge=edge or .82))
                run = None
            x += cell
        if run:
            part.add((run[0], y, z), (run[1] - run[0], cell, depth), mc(run[2], edge=edge or .82))
