"""Renders any cosmetic json exactly as the mod's loader poses it (bones, cube turns, the idle animation at a moment),
placed on the player as the catalogue entry says, next to a plain mannequin - to study and check designs.

    python jsonview.py <wear dir> <textures dir> <out.png> n [n ...] [--t 0.0,0.5] [--views back,front,side] [--body]
"""

import json
import math
import os
import sys

from PIL import Image

import wearlib
from wearlib import T, Rx, Ry, Rz, apply, mat_id, mat_mul

FACES = ["west", "east", "down", "up", "north", "south"]
SIDE = dict(zip(FACES, wearlib.SIDES))
NORM = {"left": (-1, 0, 0), "right": (1, 0, 0), "bottom": (0, -1, 0), "top": (0, 1, 0), "front": (0, 0, -1),
        "back": (0, 0, 1)}


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def vec3(e):
    if isinstance(e, dict):
        e = e.get("vector", e.get("post", e.get("pre")))
        if isinstance(e, dict):
            e = e.get("vector", e.get("post"))
    if isinstance(e, list) and len(e) >= 3:
        v = [num(x) for x in e[:3]]
        return None if None in v else v
    f = num(e)
    return None if f is None else [f, f, f]


def track(e):
    if e is None:
        return None
    keys = []
    if isinstance(e, dict) and "vector" not in e and "post" not in e:
        for k, v in e.items():
            t, vv = num(k), vec3(v)
            if t is not None and vv is not None:
                keys.append((t, vv))
    else:
        vv = vec3(e)
        if vv is not None:
            keys.append((0.0, vv))
    keys.sort()
    return keys or None


def at(tr, time):
    if time <= tr[0][0]:
        return tr[0][1]
    if time >= tr[-1][0]:
        return tr[-1][1]
    for i in range(1, len(tr)):
        if tr[i][0] >= time:
            (t0, a), (t1, b) = tr[i - 1], tr[i]
            k = (time - t0) / max(1e-6, t1 - t0)
            return [a[c] + (b[c] - a[c]) * k for c in range(3)]


def pick_anim(doc):
    an = doc.get("animation", {}).get("animations", {})
    first = None
    for k, v in an.items():
        if not isinstance(v, dict) or "gui" in k.lower():
            continue
        if first is None:
            first = v
        if "idle" in k.lower():
            return v
    return first


def quads(path_json, path_png, time=None, entry=None):
    doc = json.load(open(path_json, encoding="utf-8"))
    g = doc["model"]["minecraft:geometry"][0]
    d = g["description"]
    tw, th = float(d.get("texture_width", 64)), float(d.get("texture_height", 64))
    atlas = Image.open(path_png).convert("RGBA")
    AW, AH = atlas.size
    an = pick_anim(doc) if time is not None else None
    tracks = {}
    length = 0
    if an:
        length = num(an.get("animation_length")) or 0
        for bn, ch in (an.get("bones") or {}).items():
            if isinstance(ch, dict):
                tracks[bn] = {c: track(ch.get(c)) for c in ("rotation", "position", "scale")}
                for tr in tracks[bn].values():
                    if tr:
                        length = max(length, tr[-1][0]) if not num(an.get("animation_length")) else length
    t = (time * length) if (an and length > 0) else 0  # time given as 0..1 of the loop
    bones = g.get("bones", [])
    byname = {b["name"]: b for b in bones}
    mats = {}

    def m_of(b):
        if b["name"] in mats:
            return mats[b["name"]]
        p = b.get("parent")
        m = m_of(byname[p]) if p in byname and p != b["name"] else mat_id()
        pv = b.get("pivot", [0, 0, 0])
        px, py, pz = -pv[0], pv[1], pv[2]
        r = b.get("rotation", [0, 0, 0])
        rx, ry, rz = math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2])
        sx = sy = sz = 1
        tr = tracks.get(b["name"], {}) if an else {}
        if tr.get("position"):
            k = at(tr["position"], t)
            m = mat_mul(m, T(-k[0], k[1], k[2]))
        if tr.get("rotation"):
            k = at(tr["rotation"], t)
            rx += math.radians(-k[0])
            ry += math.radians(-k[1])
            rz += math.radians(k[2])
        if tr.get("scale"):
            sx, sy, sz = at(tr["scale"], t)
        m = mat_mul(m, T(px, py, pz))
        if rz:
            m = mat_mul(m, Rz(rz))
        if ry:
            m = mat_mul(m, Ry(ry))
        if rx:
            m = mat_mul(m, Rx(rx))
        if (sx, sy, sz) != (1, 1, 1):
            m = mat_mul(m, [[sx, 0, 0, 0], [0, sy, 0, 0], [0, 0, sz, 0], [0, 0, 0, 1]])
        m = mat_mul(m, T(-px, -py, -pz))
        mats[b["name"]] = m
        return m

    # where the catalogue puts it: pos 1 the body, pos 2 the head (both hinge at the neck, y 24)
    place = mat_id()
    if entry:
        lift = -.3 if entry.get("pos", 1) == 1 else (.5 if entry.get("cat") == 3 else 0)
        s = entry.get("scale", 1)
        place = mat_mul(T(entry.get("x", 0) * 16, 24 + (entry.get("y", 0) + lift) * 16, entry.get("z", 0) * 16),
                        [[s, 0, 0, 0], [0, s, 0, 0], [0, 0, s, 0], [0, 0, 0, 1]])
    out = []

    class Cb:
        pass

    for b in bones:
        mb = mat_mul(place, m_of(b))
        for c in b.get("cubes", []):
            o, sz = c.get("origin", [0, 0, 0]), c.get("size", [1, 1, 1])
            cb = Cb()
            cb.origin = [-(o[0] + sz[0]), o[1], o[2]]
            cb.size = sz
            cb.inflate = num(c.get("inflate", 0)) or 0
            m = mb
            if c.get("rotation"):
                pv = c.get("pivot", o)
                r = c["rotation"]
                m = mat_mul(m, wearlib.rotm((-r[0], -r[1], r[2]), (-pv[0], pv[1], pv[2])))
            uv = c.get("uv")
            for fi, fn in enumerate(FACES):
                side = SIDE[fn]
                if isinstance(uv, dict):
                    f = uv.get(fn)
                    if not f or "uv" not in f:
                        continue
                    u, v = f["uv"]
                    du, dv = f.get("uv_size", [0, 0])
                else:
                    bu, bv = (uv if isinstance(uv, list) else [0, 0])
                    sw, sh, sd = sz
                    u, v, du, dv = {
                        "west": (bu + sd + sw, bv + sd, sd, sh), "east": (bu, bv + sd, sd, sh),
                        "down": (bu + sd + sw, bv, sw, sd), "up": (bu + sd, bv, sw, sd),
                        "north": (bu + sd, bv + sd, sw, sh), "south": (bu + sd + sw + sd, bv + sd, sw, sh)}[fn]
                pts, uvs = [], []
                for (x, y, z, uu, vv) in wearlib.corners(cb, side):
                    pts.append(apply(m, (x, y, z)))
                    uvs.append(((u + uu * du) / tw * AW, (v + vv * dv) / th * AH))
                n0, o0 = apply(m, NORM[side]), apply(m, (0, 0, 0))
                out.append((pts, uvs, (n0[0] - o0[0], n0[1] - o0[1], n0[2] - o0[2]), id(atlas)))
    return out, atlas


def mannequin():
    """A grey player as quads with its own small texture."""
    img = Image.new("RGBA", (4, 1))
    img.putpixel((0, 0), (201, 169, 142, 255))
    img.putpixel((1, 0), (93, 107, 122, 255))
    img.putpixel((2, 0), (60, 70, 90, 255))
    parts = [((-4, 24, -4), (8, 8, 8), 0), ((-4, 12, -2), (8, 12, 4), 1), ((4, 12, -2), (4, 12, 4), 1),
             ((-8, 12, -2), (4, 12, 4), 1), ((-4, 0, -2), (4, 12, 4), 2), ((0, 0, -2), (4, 12, 4), 2)]
    out = []

    class Cb:
        pass

    for o, s, k in parts:
        cb = Cb()
        cb.origin, cb.size, cb.inflate = list(o), list(s), 0
        for side in wearlib.SIDES:
            pts = [(x, y, z) for (x, y, z, _, _) in wearlib.corners(cb, side)]
            out.append((pts, [(k + .5, .5)] * 4, NORM[side], id(img)))
    return out, img


def render(sets, path, view, size=320, ss=2):
    """sets: list of (quads, atlas); one picture of them all."""
    allq = []
    atl = {}
    for qs, a in sets:
        atl[id(a)] = a
        allq += qs
    # merge into one atlas: render each quad with its own atlas by re-pointing uvs into a combined image
    W = sum(a.size[0] for a in atl.values())
    H = max(a.size[1] for a in atl.values())
    big = Image.new("RGBA", (W, H))
    offs = {}
    x = 0
    for k, a in atl.items():
        big.paste(a, (x, 0))
        offs[k] = x
        x += a.size[0]
    qs = [(p, [(u + offs[k], v) for (u, v) in uv], n) for (p, uv, n, k) in allq]
    wearlib.icon((qs, big), path, view, size=size, ss=ss)


def main():
    a = sys.argv[1:]
    wear, tex, out = a[0], a[1], a[2]
    rest = a[3:]
    times, views, body, extra = [None], ["back"], False, None
    ns = []
    i = 0
    while i < len(rest):
        if rest[i] == "--t":
            times = [float(x) for x in rest[i + 1].split(",")]
            i += 2
        elif rest[i] == "--views":
            views = rest[i + 1].split(",")
            i += 2
        elif rest[i] == "--body":
            body = True
            i += 1
        elif rest[i] == "--index":
            extra = rest[i + 1]
            i += 2
        else:
            ns.append(int(rest[i]))
            i += 1
    idx = {e["n"]: e for e in json.load(open(extra or os.path.join(wear, "index.json"), encoding="utf-8"))}
    S = 300
    cols = len(times) * len(views)
    sheet = Image.new("RGBA", (cols * S, len(ns) * S), (38, 38, 46, 255))
    tmp = os.path.join(os.path.dirname(out) or ".", "_jv.png")
    for r, n in enumerate(ns):
        for ti, tm in enumerate(times):
            for vi, v in enumerate(views):
                sets = [quads(os.path.join(wear, f"cosmetic_{n}.json"), os.path.join(tex, f"cosmetic_{n}.png"), tm,
                              idx.get(n) if body else None)]
                if body:
                    sets.append(mannequin())
                render(sets, tmp, v, size=S, ss=2)
                sheet.alpha_composite(Image.open(tmp), ((ti * len(views) + vi) * S, r * S))
    sheet.save(out)
    os.remove(tmp)
    print(out)


if __name__ == "__main__":
    main()
