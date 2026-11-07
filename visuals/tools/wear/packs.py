"""Backpacks: brand ones and Arcanic's own. Worn on the back (z from 2.3 out), straps over the shoulders."""

import math

from PIL import ImageDraw

from wearlib import *

D = 5  # texels per model pixel: logos need the room


def rot_ellipse(d, cx, cy, rx, ry, ang, fill_):
    pts = []
    for i in range(28):
        t = i / 28 * math.pi * 2
        x, y = math.cos(t) * rx, math.sin(t) * ry
        a = math.radians(ang)
        pts.append((cx + x * math.cos(a) - y * math.sin(a), cy + x * math.sin(a) + y * math.cos(a)))
    d.polygon(pts, fill=fill_)


# ------------------------------------------------------------------------------------------------ the shape


def pack(m, P, width=8, height=10, depth=4.2, pocket=True, sides=True):
    """The common backpack: P has body, pocket, side, strap, trim, buckle, pull painters (some optional)."""
    b = m.bone("pack", pivot=(0, 24, 2))
    x0 = -width / 2
    top = 13 + height
    b.add(box((x0, 13, 2.3), (width, height, depth), P["body"]))
    b.add(box((x0 + 0.4, top, 2.6), (width - 0.8, 0.7, depth - 0.6), P.get("cap", P["trim"])))
    z_out = 2.3 + depth
    if pocket:
        b.add(box((x0 + 1, 13.8, z_out), (width - 2, 4.4, 1.2), P["pocket"]))
        b.add(box((-0.25, 17.1, z_out + 1.2), (0.5, 1.0, 0.25), P.get("pull", P["trim"])))
    else:
        b.add(box((-0.25, top - 1.6, z_out), (0.5, 1.0, 0.25), P.get("pull", P["trim"])))
    if sides:
        b.add(box((x0 + width, 14, 3.0), (0.8, 4.5, depth - 1.4), P.get("side", P["trim"])))
        b.add(box((x0 - 0.8, 14, 3.0), (0.8, 4.5, depth - 1.4), P.get("side", P["trim"])))
    st = P["strap"]
    for sx in (-3.1, 1.9):
        b.add(box((sx, 24.0, -2.55), (1.2, 0.45, 4.95), st))  # over the shoulder
        b.add(box((sx, 15.2, -2.6), (1.2, 8.8, 0.35), st))  # down the chest
        b.add(box((sx + 0.1, 17.4, -2.8), (1.0, 0.9, 0.2), P.get("buckle", P["trim"])))
    b.add(box((-1.4, top + 0.6, 3.6), (2.8, 0.9, 0.6), st))  # the handle
    return b


def strap(c, edge=None):
    """A webbing strap: darker at its edges, a thin line of the accent down the middle."""
    c = C(c)
    line = mul(c, 1.3) if edge is None else C(edge)

    def l(img, face, D_, rnd):
        w, h = img.size
        d = ImageDraw.Draw(img)
        if w >= h:
            d.line((0, h // 2, w, h // 2), fill=line)
        else:
            d.line((w // 2, 0, w // 2, h), fill=line)

    return over(fill(c, noise=.03, edge=.3), l)


def metal(c="#d9dde3"):
    return over(fill(c, noise=.02, grad=.25, edge=.3),
                L_draw(lambda d, w, h, D_, f, img: d.line((0, 0, w - 1, 0), fill=rgb("#ffffff"))))


# ------------------------------------------------------------------------------------------------ motifs


def M_text_box(s, box_c, text_c, y=None, pad=2):
    def l(img, face, D_, rnd):
        if face != "back":
            return
        w, h = img.size
        tw = text_width(s)
        bw, bh = tw + pad * 2 + 1, 5 + pad * 2
        x0 = (w - bw) // 2
        y0 = y if y is not None else (h - bh) // 2
        ImageDraw.Draw(img).rectangle((x0, y0, x0 + bw - 1, y0 + bh - 1), fill=C(box_c))
        text(img, s, x0 + pad + 1, y0 + pad, text_c)

    return l


LV_MARK = ["#..#.#", "#..#.#", "#...#.", "###.#."]
FLOWER = [".#.", "###", ".#."]
STAR = ["#.#", ".#.", "#.#"]


def lv_canvas(base="#5b3a22", gold="#c9a063"):
    def tile(img, x, y):
        sprite(img, LV_MARK, x + 1, y + 1, {"#": gold})
        sprite(img, FLOWER, x + 9, y + 1, {"#": gold})
        sprite(img, STAR, x + 5, y + 7, {"#": mul(rgb(gold), .85)})
        sprite(img, [".#.", "#.#", ".#."], x + 11, y + 7, {"#": gold})

    return over(fill(base, noise=.04, edge=.2), L_tile(tile, 14, 12))


G1 = [".###", "#...", "#.##", "#..#", ".###"]
G2 = ["###.", "#..#", "##.#", "...#", "###."]


def gg_canvas(base="#c9b48d", ink="#4a3423"):
    def tile(img, x, y):
        sprite(img, G1, x, y, {"#": ink})
        sprite(img, G2, x + 3, y, {"#": ink})

    return over(fill(base, noise=.03, edge=.18), L_tile(tile, 9, 7))


CROSS7 = ["..###..", "...#...", "#..#..#", "#######", "#..#..#", "...#...", "..###.."]
CROSS_BIG = ["....###....", ".....#.....", ".....#.....", "#...###...#", "###########", "#...###...#",
             ".....#.....", ".....#.....", ".....#.....", ".....#.....", ".....#.....", "....###...."]


def outlined(rows):
    """The sprite with a dark outline ('o') round it."""
    h, w = len(rows), max(len(r) for r in rows)
    grid = [list(r.ljust(w, ".")) for r in rows]
    out = [["."] * (w + 2) for _ in range(h + 2)]
    for y in range(h):
        for x in range(w):
            if grid[y][x] != ".":
                out[y + 1][x + 1] = grid[y][x]
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        if out[y + 1 + dy][x + 1 + dx] == "." and not (0 <= y + dy < h and 0 <= x + dx < w
                                                                          and grid[y + dy][x + dx] != "."):
                            out[y + 1 + dy][x + 1 + dx] = "o"
    return ["".join(r) for r in out]


def M_sprite(rows, pal, where="back", y=None, scale=1):
    def l(img, face, D_, rnd):
        if face != where:
            return
        w, h = img.size
        sw, sh = sprite_size(rows, scale)
        sprite(img, rows, (w - sw) // 2, (h - sh) // 2 if y is None else y, pal, scale)

    return l


def M_text(s, c, y, where="back", shadow=None, scale=1):
    def l(img, face, D_, rnd):
        if face == where:
            text(img, s, img.size[0] // 2, y if y >= 0 else img.size[1] + y, c, scale=scale, center=True,
                 shadow=shadow)

    return l


def M_studs(c="#cfd4db", step=5):
    def l(img, face, D_, rnd):
        w, h = img.size
        px = img.load()
        for x in range(2, w - 2, step):
            for yy in (1, h - 2):
                px[x, yy] = C(c)
        for y in range(2, h - 2, step):
            for xx in (1, w - 2):
                px[xx, y] = C(c)

    return l


# ------------------------------------------------------------------------------------------------ the packs


def supreme(n):
    m = Model(n, "Supreme Box Pack", "bodywear", D=D)
    blk, red = rgb("#17171a"), rgb("#d0102c")
    body = over(fill(blk, noise=.05), L_stitch("#2b2b30"), M_text_box("SUPREME", red, "#ffffff", y=8))
    pocket = over(fill(blk, noise=.05), L_hstripes([red], [2], start=0), L_stitch("#2b2b30"))
    pack(m, {"body": body, "pocket": pocket, "side": fill(red), "strap": strap("#1f1f23", rgb("#d0102c")),
             "trim": fill(red), "buckle": metal(), "pull": fill("#ffffff")})
    return m


def offwhite(n):
    m = Model(n, "Off-White Industrial", "bodywear", D=D)
    wh, bk, yl = rgb("#ecebe4"), rgb("#141414"), rgb("#f5d000")
    body = over(fill(wh, noise=.025), L_diag(bk, 10, 4), L_stitch("#9a9a92"),
                M_text_box('"BACKPACK"', wh, bk, y=3, pad=1))
    pocket = over(fill(bk, noise=.03), L_draw(lambda d, w, h, D_, f, img: arrows(d, w, h)), L_stitch("#3a3a3a"))
    belt = over(fill(yl, noise=.02, edge=.1), M_text("INDUSTRIAL", bk, 0, where="back"),
                M_text("INDUSTRIAL", bk, 0, where="front"))
    b = pack(m, {"body": body, "pocket": pocket, "side": fill(bk), "strap": strap(yl, rgb("#141414")),
                 "trim": fill(bk), "buckle": metal(), "pull": fill("#d4202c")})
    b.add(box((-4.15, 20.6, 2.2), (8.3, 1.2, 4.4), belt))  # the industrial belt round the bag
    b.add(box((2.3, 13.6, 7.75), (0.5, 1.6, 0.3), fill("#d4202c")))  # the red zip tie tag
    return m


def arrows(d, w, h):
    c = rgb("#ecebe4")
    cx, cy, r = w / 2, h / 2, min(w, h) * .36
    for a in (45, 135, 225, 315):
        t = math.radians(a)
        x1, y1 = cx + math.cos(t) * r, cy + math.sin(t) * r
        d.line((cx, cy, x1, y1), fill=c, width=1)
        for s in (-1, 1):
            t2 = t + math.pi + s * .5
            d.line((x1, y1, x1 + math.cos(t2) * 3, y1 + math.sin(t2) * 3), fill=c)


def louis(n):
    m = Model(n, "Louis Vuitton Monogram", "bodywear", D=D)
    tan, gold = rgb("#b5763e"), rgb("#e2b54b")
    canvas_ = lv_canvas()
    trim = over(fill(tan, noise=.03, edge=.25), L_stitch("#e7c08a", inset=1, dash=1, gap=1))
    body = over(canvas_, L_frame(tan, 2), L_stitch("#e7c08a", inset=2, dash=1, gap=1))
    pocket = over(canvas_, L_frame(tan, 2), L_draw(lambda d, w, h, D_, f, img: d.rectangle(
        (w // 2 - 2, h - 8, w // 2 + 2, h - 4), fill=gold, outline=mul(gold, .7))))
    pack(m, {"body": body, "pocket": pocket, "side": trim, "strap": trim, "trim": trim, "cap": trim,
             "buckle": metal("#e2b54b"), "pull": metal("#e2b54b")})
    return m


def chrome(n):
    m = Model(n, "Chrome Hearts Cross", "bodywear", D=D)
    lea, sil = rgb("#141416"), rgb("#d2d7de")
    leather = fill(lea, noise=.09, edge=.3)
    body = over(leather, M_studs(sil, 5),
                M_sprite(outlined(CROSS_BIG), {"#": sil, "o": "#5c6068"}, y=12))
    pocket = over(leather, M_studs(sil, 4), M_sprite(outlined(CROSS7), {"#": sil, "o": "#5c6068"}))
    pack(m, {"body": body, "pocket": pocket, "side": over(leather, M_studs(sil, 3)),
             "strap": over(fill("#1c1c1f", edge=.3), M_studs(sil, 3)), "trim": leather, "buckle": metal(),
             "pull": metal()})
    return m


def gucci(n):
    m = Model(n, "Gucci GG Web", "bodywear", D=D)
    brown = rgb("#5a3a26")
    web = L_hstripes([rgb("#1d5a3a"), rgb("#b4232b"), rgb("#1d5a3a")], [3, 4, 3], start=15, vertical=True)
    body = over(gg_canvas(), web, L_frame(brown, 1))
    pocket = over(fill(brown, noise=.04, edge=.25), L_stitch("#8a6045", inset=1, dash=1, gap=1),
                  M_sprite([".##..##.", "#...#...", "#.###.##", "#..##..#", ".##..##."],
                           {"#": "#e2b54b"}))
    trim = over(fill(brown, noise=.04, edge=.25), L_stitch("#8a6045", inset=1, dash=1, gap=1))
    pack(m, {"body": body, "pocket": pocket, "side": trim, "strap": over(fill("#1d5a3a"), L_hstripes(
        [rgb("#b4232b")], [2], start=2, vertical=True)), "trim": trim, "cap": trim, "buckle": metal("#e2b54b"),
             "pull": metal("#e2b54b")})
    return m


def adidas(n):
    m = Model(n, "Adidas Originals", "bodywear", D=D)
    bk, wh = rgb("#131316"), rgb("#f4f4f4")

    def trefoil(d, w, h, D_, f, img):
        if f != "back":
            return
        cx, cy = w / 2, 19
        rot_ellipse(d, cx, cy - 4, 3.6, 7, 0, wh)
        rot_ellipse(d, cx - 6.5, cy + 1, 3.4, 6.2, -52, wh)
        rot_ellipse(d, cx + 6.5, cy + 1, 3.4, 6.2, 52, wh)
        for yy in (cy + 1, cy + 4):
            d.line((0, yy, w, yy), fill=bk)
        d.rectangle((cx - 12, cy + 7, cx + 12, cy + 7), fill=bk)
        text(img, "ADIDAS", w // 2, cy + 11, wh, center=True)

    body = over(fill(bk, noise=.04), L_draw(trefoil), L_stitch("#2a2a2e"))
    stripes = over(fill(bk), L_hstripes([wh, None, wh, None, wh], [1, 1, 1, 1, 1], start=1, vertical=True))
    pocket = over(fill(bk, noise=.04), L_hstripes([wh, None, wh, None, wh], [1, 2, 1, 2, 1], start=8),
                  L_stitch("#2a2a2e"))
    pack(m, {"body": body, "pocket": pocket, "side": stripes, "strap": stripes, "trim": fill(bk),
             "buckle": metal(), "pull": fill(wh)}, pocket=False)
    return m


def nike(n):
    m = Model(n, "Nike Swoosh", "bodywear", D=D)
    bk, wh, volt = rgb("#18181b"), rgb("#ffffff"), rgb("#d4ff2f")

    def swoosh(d, w, h, D_, f, img):
        if f != "back":
            return
        pts = [(.06, .62), (.02, .82), (.12, .92), (.32, .86), (.98, .36), (.34, .70), (.18, .73), (.12, .66)]
        d.polygon([(x * w, y * h) for x, y in pts], fill=wh)

    body = over(fill(bk, noise=.04), M_text("NIKE", wh, 4, scale=2), M_text("JUST DO IT", volt, 16),
                L_stitch("#2c2c30"),
                L_draw(lambda d, w, h, D_, f, img: f == "back" and d.rectangle((0, h - 4, w, h - 3), fill=volt)))
    pocket = over(fill(bk, noise=.04), L_draw(swoosh), L_stitch("#2c2c30"))
    pack(m, {"body": body, "pocket": pocket, "side": fill(volt), "strap": strap("#222226", volt),
             "trim": fill(bk), "buckle": metal(), "pull": fill(volt)})
    return m


def vlone(n):
    m = Model(n, "Vlone Friends", "bodywear", D=D)
    bk, org = rgb("#101012"), rgb("#f26a1b")

    def v(d, w, h, D_, f, img):
        if f != "back":
            return
        cx = w / 2
        d.polygon([(cx - 15, 6), (cx - 6, 6), (cx, 30), (cx + 6, 6), (cx + 15, 6), (cx + 2, 40), (cx - 2, 40)],
                  fill=org)
        d.polygon([(cx - 11, 8), (cx - 7.5, 8), (cx, 34), (cx + 7.5, 8), (cx + 11, 8), (cx + 1, 37), (cx - 1, 37)],
                  fill=mul(org, .78))
        text(img, "VLONE", w // 2, 43, "#ffffff", center=True)

    body = over(fill(bk, noise=.05), L_draw(v), L_stitch("#262629"))
    pocket = over(fill(bk, noise=.05), M_text("FRIENDS", org, 10), L_stitch("#262629"))
    pack(m, {"body": body, "pocket": pocket, "side": fill(org), "strap": strap("#19191c", org), "trim": fill(bk),
             "buckle": metal(), "pull": fill(org)}, pocket=False)
    return m


APE = ["...######...", "..########..", ".###.##.###.", "##..####..##", "#.##.##.##.#", "#.#o#..#o#.#",
       "#.##.##.##.#", ".#...##...#.", "..#.####.#..", "...#....#...", "....####...."]


def bape(n):
    m = Model(n, "BAPE Camo Shark", "bodywear", D=D)
    camo = over(fill("#7d8f4e", noise=.03, edge=.15),
                L_noise_blobs(["#5c6d36", "#a3aa6a", "#3f4a26", "#8e7b4a"], 3, 2, 5, seed=n))
    body = over(camo, M_text_box("BAPE", "#111111", "#ffffff", y=6), L_stitch("#3f4a26"))
    pocket = over(camo, M_sprite(outlined(APE), {"#": "#1d1d1d", "o": "#f2efe6"}), L_stitch("#3f4a26"))
    pack(m, {"body": body, "pocket": pocket, "side": fill("#1d1d1d"), "strap": strap("#1d1d1d", rgb("#a3aa6a")),
             "trim": fill("#1d1d1d"), "buckle": metal(), "pull": fill("#f2efe6")})
    return m


def arcanic(n):
    m = Model(n, "Arcanic Rune", "bodywear", D=D)
    dk, vi, cy = rgb("#13131c"), rgb("#8b5cf6"), rgb("#5eead4")

    def runes(d, w, h, D_, f, img):
        if f != "back":
            return
        cx = w // 2
        d.polygon([(cx, 8), (cx + 9, 22), (cx, 36), (cx - 9, 22)], outline=vi)
        d.polygon([(cx, 13), (cx + 5, 22), (cx, 31), (cx - 5, 22)], fill=mul(vi, .9))
        d.polygon([(cx, 16), (cx + 2, 22), (cx, 28), (cx - 2, 22)], fill=cy)
        for yy in (4, h - 5):
            d.line((4, yy, w - 5, yy), fill=mul(vi, .55))
        text(img, "ARCANIC", cx, h - 12, vi, center=True)

    body = over(fill(dk, noise=.05), L_draw(runes), L_stitch("#2a2840"))
    pocket = over(fill(dk, noise=.05), L_hstripes([vi, None, cy], [1, 2, 1], start=3), L_stitch("#2a2840"))
    pack(m, {"body": body, "pocket": pocket, "side": fill(vi), "strap": strap("#1b1a28", vi), "trim": fill(dk),
             "buckle": metal("#c4b5fd"), "pull": fill(cy)}, pocket=False)
    return m


def tactical(n):
    m = Model(n, "Tactical Molle", "bodywear", D=D)
    ol, dk = rgb("#5b6236"), rgb("#3c4224")

    def molle(img, face, D_, rnd):
        w, h = img.size
        d = ImageDraw.Draw(img)
        for y in range(3, h - 2, 6):
            d.rectangle((1, y, w - 2, y + 2), fill=dk)
            for x in range(4, w - 3, 6):
                d.line((x, y, x, y + 2), fill=mul(dk, .7))

    body = over(fill(ol, noise=.06), molle, L_stitch("#7b8350"))
    patch = L_draw(lambda d, w, h, D_, f, img: f == "back" and (
        d.rectangle((w // 2 - 7, 3, w // 2 + 7, 11), fill=rgb("#2a2d1a")),
        text(img, "ARC", w // 2, 5, "#c9c2a0", center=True)))
    pocket = over(fill(ol, noise=.06), molle, patch)
    pack(m, {"body": body, "pocket": pocket, "side": over(fill(ol), molle), "strap": strap(dk),
             "trim": fill(dk), "buckle": fill("#1c1c1c"), "pull": fill("#1c1c1c")})
    return m


def jetpack(n):
    m = Model(n, "Rocket Jetpack", "bodywear", D=4)
    b = m.bone("pack", pivot=(0, 20, 3))
    steel = over(fill("#c3c9d2", noise=.03, grad=.3, edge=.2),
                 L_hstripes(["#d63b3b", None, "#d63b3b"], [2, 2, 2], start=6))
    for sx in (-3.9, 0.4):
        b.add(box((sx, 14, 2.4), (3.5, 9, 3.5), steel))
        b.add(box((sx + 0.4, 23, 2.8), (2.7, 1.2, 2.7), fill("#8d949e")))
        b.add(box((sx + 0.6, 12.6, 3.0), (2.3, 1.4, 2.3), fill("#3d4148")))
    b.add(box((-0.6, 15, 2.4), (1.2, 7, 2.5), fill("#2f3238")))
    for sx in (-3.1, 1.9):
        b.add(box((sx, 24.0, -2.55), (1.2, 0.45, 4.95), strap("#2c2f35")))
        b.add(box((sx, 15.2, -2.6), (1.2, 8.8, 0.35), strap("#2c2f35")))
    fl = m.bone("flame", pivot=(0, 12.6, 4.2), parent="pack")
    flame = picture(lambda img, face, w, h, D_, rnd: flame_pic(img))
    for sx in (-2.15, 2.15):
        fl.add(plane_x(sx, 8.4, 3.1, 2.2, 4.2, flame))
        fl.add(Cube((sx - 1.1, 8.4, 4.2), (2.2, 4.2, 0), flame, only=("back",)))
    m.anim("flame", "scale", lambda t: (1, 0.85 + 0.25 * abs(math.sin(t * math.pi * 6)), 1), 1.0, 24)
    return m


def flame_pic(img):
    w, h = img.size
    d = ImageDraw.Draw(img)
    d.polygon([(0, 0), (w - 1, 0), (w * .7, h * .55), (w / 2, h - 1), (w * .3, h * .55)], fill=rgb("#ff7a1a"))
    d.polygon([(w * .2, 0), (w * .8, 0), (w * .6, h * .45), (w / 2, h * .75), (w * .4, h * .45)], fill=rgb("#ffd23f"))
    d.polygon([(w * .38, 0), (w * .62, 0), (w / 2, h * .35)], fill=rgb("#fff6d0"))


def guitar(n):
    m = Model(n, "Guitar Case", "bodywear", D=4)
    b = m.bone("pack", pivot=(0, 18, 4), rot=(0, 0, -22))
    case = over(fill("#1d1d22", noise=.05, edge=.25), L_stitch("#3a3a44"))
    b.add(box((-3.6, 9.5, 2.4), (7.2, 9, 3.2), case))
    b.add(box((-2.8, 18.5, 2.6), (5.6, 3, 2.8), case))
    b.add(box((-1.1, 21.5, 2.8), (2.2, 9, 2.3), case))
    b.add(box((-1.6, 30.5, 2.7), (3.2, 2.6, 2.5), case))
    b.add(box((-2.2, 12, 5.6), (4.4, 0.6, 0.3), metal("#c9a23f")))
    b.add(box((-0.5, 24, 5.1), (1, 4, 0.3), fill("#c9a23f")))
    s = m.bone("strap")
    s.add(box((1.9, 15.2, -2.6), (1.2, 8.8, 0.35), strap("#6b4a2b")))
    s.add(box((1.9, 24.0, -2.55), (1.2, 0.45, 4.95), strap("#6b4a2b")))
    return m


def teddy(n):
    m = Model(n, "Teddy Bear Pack", "bodywear", D=4, view="back")
    fur, muz, dk = rgb("#a8744a"), rgb("#e3c39a"), rgb("#2b1d14")

    def face_(d, w, h, D_, f, img):
        if f != "back":
            return
        d.ellipse((w * .3, h * .45, w * .7, h * .85), fill=muz)
        d.rectangle((w * .45, h * .5, w * .55, h * .6), fill=dk)
        d.line((w * .5, h * .6, w * .5, h * .72), fill=dk)
        for ex in (.28, .66):
            d.rectangle((w * ex, h * .3, w * ex + 2, h * .3 + 3), fill=dk)
            d.point((w * ex + 1, h * .3), fill=rgb("#ffffff"))

    furp = fill(fur, noise=.1, edge=.2)
    b = m.bone("pack", pivot=(0, 18, 2))
    b.add(box((-3.5, 13, 2.3), (7, 7, 3.6), furp))
    b.add(box((-1.8, 14.2, 5.9), (3.6, 3.4, 0.6), fill(muz)))  # the tummy
    b.add(box((-3, 20, 2.6), (6, 5.5, 3.6), over(furp, L_draw(face_))))
    for sx in (-3.2, 1.8):
        b.add(box((sx, 25.2, 3.5), (1.6, 1.6, 1.2), over(furp, L_draw(
            lambda d, w, h, D_, f, img: f == "back" and d.rectangle((1, 1, w - 2, h - 2), fill=muz)))))
    b.add(box((3.4, 16.5, 3), (1.4, 3.2, 2), furp))
    b.add(box((-4.8, 16.5, 3), (1.4, 3.2, 2), furp))
    for sx in (-3.1, 1.9):
        b.add(box((sx, 24.0, -2.55), (1.2, 0.45, 4.95), strap("#7a2f3b")))
        b.add(box((sx, 15.2, -2.6), (1.2, 8.8, 0.35), strap("#7a2f3b")))
    b.add(box((-1.4, 24.6, 5.9), (2.8, 1, 0.5), fill("#c43a50")))  # a bow
    return m


def ender(n):
    m = Model(n, "Ender Chest Pack", "bodywear", D=4)
    obs, teal, eye = rgb("#13201f"), rgb("#1f4a47"), rgb("#3ee0a0")

    def side(img, face, D_, rnd):
        w, h = img.size
        d = ImageDraw.Draw(img)
        d.rectangle((0, 0, w - 1, h - 1), outline=teal)
        d.line((0, h * .3, w, h * .3), fill=mul(teal, 1.3))

    def lock(d, w, h, D_, f, img):
        if f != "back":
            return
        d.rectangle((w / 2 - 4, h * .3 - 3, w / 2 + 3, h * .3 + 5), fill=rgb("#0b1110"), outline=teal)
        d.ellipse((w / 2 - 2, h * .3 - 1, w / 2 + 1, h * .3 + 3), fill=eye)
        d.point((w / 2, h * .3 + 1), fill=rgb("#08100c"))

    chest = over(fill(obs, noise=.12, edge=.2), side, L_draw(lock))
    b = pack(m, {"body": chest, "pocket": chest, "side": fill(teal), "strap": strap("#162625", eye),
                 "trim": fill(teal), "buckle": fill(eye), "pull": fill(eye)}, pocket=False)
    return m


def mushroom(n):
    m = Model(n, "Mushroom Pack", "bodywear", D=4)
    red, wh, stem = rgb("#d63a3a"), rgb("#fff7ea"), rgb("#efe1c6")

    def dots(img, face, D_, rnd):
        d = ImageDraw.Draw(img)
        w, h = img.size
        import random as _r
        r = _r.Random(len(face) * 13 + w)
        for _ in range(max(2, w * h // 90)):
            x, y = r.randrange(w), r.randrange(h)
            s = r.choice((2, 3, 3, 4))
            d.ellipse((x, y, x + s, y + s), fill=wh)

    cap = over(fill(red, noise=.05, edge=.15), dots)
    b = m.bone("pack", pivot=(0, 18, 2))
    b.add(box((-3, 13, 2.4), (6, 6, 3.4), fill(stem, noise=.06)))
    def eyes(img, face, w, h, D_, rnd):
        ImageDraw.Draw(img).rectangle((0, 0, w, h), fill=stem)
        if face == "back":
            img.putpixel((2, 2), rgb("#2b1d14"))
            img.putpixel((w - 3, 2), rgb("#2b1d14"))

    b.add(box((-1.2, 15, 5.8), (2.4, 1.6, 0.3), picture(eyes)))
    b.add(box((-4.6, 19, 1.9), (9.2, 3.5, 6.4), cap))
    b.add(box((-3.6, 22.5, 2.6), (7.2, 1.6, 5), cap))
    b.add(box((-4.2, 18.5, 2.3), (8.4, 0.6, 5.6), fill("#f2e3c8")))
    for sx in (-3.1, 1.9):
        b.add(box((sx, 24.0, -2.55), (1.2, 0.45, 4.95), strap("#8b5a3c")))
        b.add(box((sx, 15.2, -2.6), (1.2, 8.8, 0.35), strap("#8b5a3c")))
    return m


ALL = [supreme, offwhite, louis, chrome, gucci, adidas, nike, vlone, bape, arcanic, tactical, guitar, teddy,
       ender, mushroom]
