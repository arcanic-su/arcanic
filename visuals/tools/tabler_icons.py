#!/usr/bin/env python3
#
# This file is part of Arcanic Visuals (https://github.com/arcanic-su/arcanic)
#
# Copyright (c) 2026 Arcanic
#
# Arcanic Visuals is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
"""
The menu's icon set: Tabler Icons (MIT, https://tabler.io/icons, see third-party/Tabler-Icons-LICENSE.txt), the
outline style with its 2-unit round stroke - and, where our own ReallyVisuals has a glyph of its own for the same
thing, that glyph, from its icon font (tools/really/really.ttf), so the two menus look alike - baked into
src/main/resources/assets/arcanicvisuals/textures/gui/kit/icons_<px>.png (one atlas per pixel size, 8..40) plus
kit_icons.json, which render/Px.java reads. The SVGs used are kept in tools/tabler/.

Usage:
  python tools/tabler_icons.py --fetch     download the SVGs listed in NAMES into tools/tabler/ (pinned version)
  python tools/tabler_icons.py             bake the atlases from tools/tabler/
"""
import json
import math
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image

VERSION = "3.24.0"
GRID = 24.0
SS = 6  # sub-samples per pixel side
SIZES = range(8, 41)
COLS = 16
HERE = os.path.dirname(os.path.abspath(__file__))
SVG_DIR = os.path.join(HERE, "tabler")
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "arcanicvisuals")

# our name -> the Tabler icon drawn for it (the first one that exists)
NAMES = {
    "arrow-up-to-line": ["arrow-bar-to-up"], "user-plus": ["user-plus"], "user-shield": ["user-shield"],
    "user-x": ["user-x"], "user-circle": ["user-circle"], "alert-circle": ["alert-circle"], "pen": ["pencil"],
    "sliders": ["adjustments-horizontal"], "sliders-v": ["adjustments"], "chev-down": ["chevron-down"],
    "play": ["player-play"], "chev-left": ["chevron-left"], "chev-right": ["chevron-right"],
    "chev-up": ["chevron-up"], "hammer": ["hammer"], "award": ["award"], "undo": ["arrow-back-up"],
    "scan-barcode": ["barcode"], "receipt": ["receipt"], "box": ["box"], "bookmark-o": ["bookmark"],
    "bookmark": ["bookmarks"], "wall": ["wall"], "broom": ["eraser"], "chat": ["message"], "check": ["check"],
    "contrast": ["contrast"], "apps": ["apps"], "cloud": ["cloud"], "frame": ["frame"], "pipette": ["color-picker"],
    "sad": ["mood-sad"], "copy": ["copy"], "card": ["credit-card"], "x": ["x"], "crown": ["crown"],
    "click": ["click"], "lamp": ["bulb"], "signpost": ["sign-right"], "monitor": ["layout-dashboard"],
    "chevrons-right": ["chevrons-right"], "download": ["download"], "log-out": ["logout"], "eye-o": ["eye"],
    "eye-off-o": ["eye-off"], "eye-off": ["eye-off"], "eye": ["eye"], "tap": ["hand-finger"],
    "fingerprint": ["fingerprint"], "fingerprint-2": ["fingerprint"], "flask": ["flask"],
    "shrink-h": ["arrows-horizontal"], "shrink-v": ["arrows-vertical"], "folder": ["folder"],
    "arrows-v": ["arrows-vertical"], "hand-heart": ["heart-handshake"], "gamepad": ["device-gamepad-2"],
    "gift-box": ["gift"], "gift": ["gift"], "grip": ["grip-vertical"], "users": ["users"], "type": ["typography"],
    "headset": ["headphones"], "calendar-heart": ["calendar-heart"], "house": ["home"], "inbox": ["inbox"],
    "key": ["key"], "keyboard": ["keyboard"], "leaf": ["leaf"], "bolt": ["bolt"], "link": ["link"],
    "list": ["list"], "list-search": ["list-search"], "menu": ["menu-2"], "arrow-right": ["arrow-right"],
    "wizard-hat": ["wand"], "search": ["search"], "plus-bold": ["plus"], "minus": ["minus"],
    "moon-stars": ["moon-stars"], "moon": ["moon"], "navigate": ["navigation"], "roller": ["paint"],
    "swatch": ["palette"], "pause": ["player-pause"], "piggy": ["pig"], "joystick": ["device-gamepad"],
    "plus-circle": ["circle-plus"], "plus": ["plus"], "puzzle": ["puzzle"], "mountains": ["mountain"],
    "quote": ["quote"], "rainbow": ["rainbow"], "refresh": ["refresh"], "select": ["border-corners"],
    "router": ["wifi"], "running": ["run"], "save": ["device-floppy"], "scan": ["scan"], "atom": ["atom"],
    "scroll": ["script"], "shield": ["shield"], "bag": ["backpack"], "cart": ["shopping-cart"],
    "filter": ["filter"], "sparkles": ["sparkles"], "stop": ["player-stop"], "server": ["server"], "sun": ["sun"],
    "sword": ["swords"], "grid": ["grid-dots"], "tag": ["tag"], "send": ["send"], "telescope": ["telescope"],
    "pie": ["clock"], "wrench": ["tool"], "trash": ["trash"], "expand-h": ["arrows-move-horizontal"],
    "expand-v": ["arrows-move-vertical"], "unlink": ["unlink"], "rocket": ["rocket"], "crosshair": ["crosshair"],
    "target": ["target"], "heart": ["heart"], "gauge": ["gauge"], "footprints": ["shoe"], "flame": ["flame"],
    "star": ["star"], "activity": ["activity"], "cpu": ["cpu"], "square": ["square"], "power": ["power"],
    "music": ["music"], "ghost": ["ghost"], "bell": ["bell"], "infinity": ["infinity"], "flag": ["flag"],
    "planet": ["world"], "gem": ["diamond"], "stack": ["stack-2"], "lang": ["language"], "cape": ["hanger"],
    "knife": ["sword"], "wings": ["feather"], "pet": ["paw"], "dots": ["dots"], "skull": ["skull"],
}
# names drawn from ReallyVisuals' icon font instead: our name -> its code point
RV_FONT = os.path.join(HERE, "really", "really.ttf")
RV = {
    "star": 0xE900, "click": 0xE901, "pipette": 0xE902, "roller": 0xE903, "sword": 0xE904, "cloud": 0xE905,
    "sliders": 0xE907, "copy": 0xE908, "monitor": 0xE90B, "chev-down": 0xE90D, "filter": 0xE910,
    "download": 0xE911, "keyboard": 0xE913, "lang": 0xE914, "inbox": 0xE916, "menu": 0xE917, "grip": 0xE919,
    "box": 0xE91B, "users": 0xE91C, "flame": 0xE91D, "eye": 0xE91E, "eye-o": 0xE91E, "save": 0xE920,
    "search": 0xE921, "gear": 0xE922, "wizard-hat": 0xE923, "award": 0xE924, "sparkles": 0xE926,
    "planet": 0xE927, "check": 0xE928, "pen": 0xE929, "select": 0xE92A, "skull": 0xE92B,
    "alert-circle": 0xE92D, "signpost": 0xE92E, "infinity": 0xE92F, "flag": 0xE934, "music": 0xE935,
    "bomb": 0xE936, "pie": 0xE938, "navigate": 0xE93B,
}
NAMES.setdefault("gear", ["settings"])
NAMES.setdefault("bomb", ["bomb"])

# the order of the cells: the names the game already knew keep their places, new ones follow
ORDER = list(NAMES)


def fetch():
    os.makedirs(SVG_DIR, exist_ok=True)
    for ours, candidates in NAMES.items():
        for name in candidates:
            out = os.path.join(SVG_DIR, name + ".svg")
            if os.path.exists(out):
                break
            url = f"https://cdn.jsdelivr.net/npm/@tabler/icons@{VERSION}/icons/outline/{name}.svg"
            try:
                data = urllib.request.urlopen(url, timeout=20).read()
            except Exception as e:
                print(f"  {name}: {e}")
                continue
            ET.fromstring(data)  # it must at least be well-formed
            with open(out, "wb") as f:
                f.write(data)
            break
        else:
            print(f"no icon for {ours}")


# ---------------------------------------------------------------- SVG paths to polylines
TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")


def _arc(p0, rx, ry, phi, large, sweep, p1, n=None):
    """SVG elliptical arc from p0 to p1 as points (without p0)."""
    x1, y1 = p0
    x2, y2 = p1
    if rx == 0 or ry == 0:
        return [p1]
    rx, ry = abs(rx), abs(ry)
    c, s = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    xp, yp = c * dx + s * dy, -s * dx + c * dy
    lam = xp * xp / (rx * rx) + yp * yp / (ry * ry)
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * yp * yp - ry * ry * xp * xp
    den = rx * rx * yp * yp + ry * ry * xp * xp
    k = math.sqrt(max(0, num / den)) if den else 0
    if large == sweep:
        k = -k
    cxp, cyp = k * rx * yp / ry, -k * ry * xp / rx
    cx, cy = c * cxp - s * cyp + (x1 + x2) / 2, s * cxp + c * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = ang(1, 0, (xp - cxp) / rx, (yp - cyp) / ry)
    dt = ang((xp - cxp) / rx, (yp - cyp) / ry, (-xp - cxp) / rx, (-yp - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    n = n or max(4, int(abs(dt) * max(rx, ry) * 3))
    out = []
    for i in range(1, n + 1):
        t = t1 + dt * i / n
        x, y = rx * math.cos(t), ry * math.sin(t)
        out.append((c * x - s * y + cx, s * x + c * y + cy))
    return out


def _bez(pts, n=16):
    out = []
    for i in range(1, n + 1):
        t = i / n
        if len(pts) == 3:
            (x0, y0), (x1, y1), (x2, y2) = pts
            a, b, c = (1 - t) ** 2, 2 * (1 - t) * t, t * t
            out.append((a * x0 + b * x1 + c * x2, a * y0 + b * y1 + c * y2))
        else:
            (x0, y0), (x1, y1), (x2, y2), (x3, y3) = pts
            a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
            out.append((a * x0 + b * x1 + c * x2 + d * x3, a * y0 + b * y1 + c * y2 + d * y3))
    return out


def parse_path(d):
    """Subpaths as (points, closed)."""
    toks = TOKEN.findall(d)
    i, cmd = 0, None
    cur, start, last_ctrl = (0.0, 0.0), (0.0, 0.0), None
    subs, pts = [], []

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    def flush(closed):
        nonlocal pts
        if len(pts) > 1:
            subs.append((pts, closed))
        pts = []

    while i < len(toks):
        if re.match(r"[A-Za-z]", toks[i]):
            cmd = toks[i]
            i += 1
        rel = cmd.islower()
        C = cmd.upper()
        ox, oy = cur if rel else (0, 0)
        if C == "M":
            flush(False)
            cur = (ox + num(), oy + num())
            start = cur
            pts = [cur]
            cmd = "l" if rel else "L"
            last_ctrl = None
        elif C == "L":
            cur = (ox + num(), oy + num())
            pts.append(cur)
            last_ctrl = None
        elif C == "H":
            cur = ((cur[0] if rel else 0) + num(), cur[1])
            pts.append(cur)
            last_ctrl = None
        elif C == "V":
            cur = (cur[0], (cur[1] if rel else 0) + num())
            pts.append(cur)
            last_ctrl = None
        elif C == "C":
            p1 = (ox + num(), oy + num())
            p2 = (ox + num(), oy + num())
            p3 = (ox + num(), oy + num())
            pts += _bez([cur, p1, p2, p3])
            last_ctrl, cur = p2, p3
        elif C == "S":
            p1 = (2 * cur[0] - last_ctrl[0], 2 * cur[1] - last_ctrl[1]) if last_ctrl else cur
            p2 = (ox + num(), oy + num())
            p3 = (ox + num(), oy + num())
            pts += _bez([cur, p1, p2, p3])
            last_ctrl, cur = p2, p3
        elif C == "Q":
            p1 = (ox + num(), oy + num())
            p2 = (ox + num(), oy + num())
            pts += _bez([cur, p1, p2])
            last_ctrl, cur = p1, p2
        elif C == "T":
            p1 = (2 * cur[0] - last_ctrl[0], 2 * cur[1] - last_ctrl[1]) if last_ctrl else cur
            p2 = (ox + num(), oy + num())
            pts += _bez([cur, p1, p2])
            last_ctrl, cur = p1, p2
        elif C == "A":
            rx, ry, phi, large, sweep = num(), num(), num(), num(), num()
            p1 = (ox + num(), oy + num())
            pts += _arc(cur, rx, ry, phi, int(large), int(sweep), p1)
            cur = p1
            last_ctrl = None
        elif C == "Z":
            if pts:
                pts.append(start)
            flush(True)
            cur = start
            pts = [cur]
            last_ctrl = None
    flush(False)
    return subs


def load(name):
    """The stroked polylines of one Tabler outline icon, in its 24-unit space."""
    root = ET.parse(os.path.join(SVG_DIR, name + ".svg")).getroot()
    lines = []
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        if el.get("stroke") == "none":
            continue
        if tag == "path":
            lines += parse_path(el.get("d"))
        elif tag in ("circle", "ellipse"):
            cx, cy = float(el.get("cx", 0)), float(el.get("cy", 0))
            rx = float(el.get("r", el.get("rx", 0)))
            ry = float(el.get("r", el.get("ry", 0)))
            n = max(12, int(max(rx, ry) * 10))
            lines.append(([(cx + rx * math.cos(2 * math.pi * k / n), cy + ry * math.sin(2 * math.pi * k / n))
                           for k in range(n + 1)], True))
        elif tag == "rect":
            x, y, w, h = (float(el.get(a, 0)) for a in ("x", "y", "width", "height"))
            lines.append(([(x, y), (x + w, y), (x + w, y + h), (x, y + h), (x, y)], True))
        elif tag == "line":
            lines.append(([(float(el.get("x1")), float(el.get("y1"))), (float(el.get("x2")), float(el.get("y2")))],
                          False))
        elif tag == "polyline":
            v = [float(t) for t in re.findall(r"[-+]?(?:\d*\.\d+|\d+)", el.get("points"))]
            lines.append((list(zip(v[::2], v[1::2])), False))
    return lines


# ---------------------------------------------------------------- baking
def render(lines, px):
    """Coverage of the 2-unit round stroke (round caps and joins) at px pixels."""
    s = px / GRID
    c = (np.arange(px * SS) + 0.5) / SS
    X, Y = np.meshgrid(c, c)
    d = np.full(X.shape, np.inf)
    for pts, _ in lines:
        p = [(x * s, y * s) for x, y in pts]
        for (ax, ay), (bx, by) in zip(p, p[1:]):
            dx, dy = bx - ax, by - ay
            L2 = dx * dx + dy * dy
            if L2 < 1e-12:
                d = np.minimum(d, np.hypot(X - ax, Y - ay))
                continue
            t = np.clip(((X - ax) * dx + (Y - ay) * dy) / L2, 0, 1)
            d = np.minimum(d, np.hypot(X - (ax + dx * t), Y - (ay + dy * t)))
    half = max(.5, 1.0 * s)  # never thinner than one pixel
    cov = (d <= half).reshape(px, SS, px, SS).mean(axis=(1, 3))
    return cov


def render_glyph(cp, px):
    """Coverage of one ReallyVisuals glyph at px pixels: its em square is the cell."""
    from PIL import ImageDraw, ImageFont

    size = px * SS
    font = ImageFont.truetype(RV_FONT, size)
    img = Image.new("L", (size, size), 0)
    # the font's em runs from its ascent (960 of 1024) down to its descent: the baseline sits at the ascent
    ImageDraw.Draw(img).text((0, size * 960 / 1024), chr(cp), font=font, fill=255, anchor="ls")
    a = np.asarray(img, dtype=np.float64) / 255
    return a.reshape(px, SS, px, SS).mean(axis=(1, 3))


def main():
    if "--fetch" in sys.argv:
        fetch()
        return
    shapes = {}
    for ours, candidates in NAMES.items():
        if ours in RV:
            continue
        found = next((n for n in candidates if os.path.exists(os.path.join(SVG_DIR, n + ".svg"))), None)
        if found is None:
            sys.exit(f"no SVG for {ours}; run with --fetch")
        shapes[ours] = load(found)
    rows = math.ceil(len(ORDER) / COLS)
    kit = os.path.join(ROOT, "textures", "gui", "kit")
    for px in SIZES:
        sheet = np.zeros((rows * px, COLS * px))
        for i, n in enumerate(ORDER):
            r, col = divmod(i, COLS)
            cell = render_glyph(RV[n], px) if n in RV else render(shapes[n], px)
            sheet[r * px:(r + 1) * px, col * px:(col + 1) * px] = cell
        rgba = np.zeros(sheet.shape + (4,), dtype=np.uint8)
        rgba[..., :3] = 255
        rgba[..., 3] = np.round(np.clip(sheet, 0, 1) * 255).astype(np.uint8)
        Image.fromarray(rgba, "RGBA").save(os.path.join(kit, f"icons_{px}.png"), optimize=True)
    with open(os.path.join(ROOT, "kit_icons.json"), "w", encoding="utf-8") as f:
        json.dump({"cols": COLS, "rows": rows, "icons": {n: i for i, n in enumerate(ORDER)}}, f, indent=1)
        f.write("\n")
    print(f"{len(ORDER)} icons, {len(SIZES)} sizes")


if __name__ == "__main__":
    main()
