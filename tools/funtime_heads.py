"""Paints the FunTime event icons: themed custom player heads, rendered small and isometric like HolyWorld's.

The heads come from the Minecraft-Heads.com database (mirrored at github.com/TheLuca98/MinecraftHeads);
each one is a skin on textures.minecraft.net named by its hash.

    python tools/funtime_heads.py <skins dir> <src/main/resources>          paint the chosen heads
    python tools/funtime_heads.py --sheet <skins dir> <out.png> <hash>...   preview candidates
"""

import os
import sys

from PIL import Image

SIZE = 38
# event picture name -> head skin hash
HEADS = {
    "airdrop": "f52d5148a107301c93e303e1f53b28635a319224a813eedc0a99cbafc0deb5c1",  # Acacia Crate
    "mystic": "bd95e00f165161d4c22bd40875a598aae2d5241a0ea68e50383b016711b76210",  # Mystery Chest
    "deathchest": "7b7cf777202997bb6a15b08634dce9a54aa5640460c131c9babb8df1ad28613d",  # Sculk Chest
    "altar": "86d76900d70722e171111edc21e15c998bed8b2f393a7a6e9c0e0a52869cfca6",  # Pedestal
    "altarundead": "f26da4e4e59904375272e8db6394bd050bd7f203e9814e02f7d2d2265d49fede",  # Necromancer
    "beacon": "4a2648a9e53613f0ab1266b72918f40788f84e160a52348cd67138410262f39f",  # Beacon
    "myst_beacon": "b423289510c54b67df023580979c465d0481c769c865bf4b465cf478749f1c4f",  # End Crystal
    "geyser": "ed1d8319a1ec08a40549c3dd74b3a05cc83c4d019a98a446e0a13c6ffd38e095",  # Water Fountain
    "hellm": "a53c823d3239c8cb79dc9f345d5eea56f364d2a2abd77aa985f836096f8fe4e6",  # Satan
    "meteor": "c1147d89d2498f9e21f36ad3e5356b2327c8f855183d3e69f4d660e5bc210abb",  # Red Meteor
    "volcano": "92dd521beb67533311ccf6d7234ba0f28963c06335f834b2136ce89ab3ba31db",  # Erupting Volcano
}


def faces(skin):
    """The head's top, front and right side, each 8x8 with the hat layer over it."""
    s = skin.convert("RGBA")

    def part(x, y):
        base = s.crop((x, y, x + 8, y + 8))
        hat = s.crop((x + 32, y, x + 40, y + 8))
        base.alpha_composite(hat)
        return base

    return part(8, 0), part(8, 8), part(0, 8)


def shade(im, k):
    r, g, b, a = im.split()
    return Image.merge("RGBA", [c.point(lambda v: int(v * k)) for c in (r, g, b)] + [a])


def inverse(points):
    """Affine coefficients taking output (x, y) back to texture (u, v), from three (u, v, x, y) pairs."""
    (u0, v0, x0, y0), (u1, v1, x1, y1), (u2, v2, x2, y2) = points
    ax, ay = (x1 - x0) / (u1 - u0), (y1 - y0) / (u1 - u0)
    bx, by = (x2 - x0) / (v2 - v0), (y2 - y0) / (v2 - v0)
    det = ax * by - bx * ay
    ia, ib, ic, id_ = by / det, -bx / det, -ay / det, ax / det
    return (ia, ib, -(ia * x0 + ib * y0), ic, id_, -(ic * x0 + id_ * y0))


def head(skin):
    s = SIZE * 4
    w, h = s / 2.0, s / 4.0
    top, front, side = faces(skin)

    def put(im, k, pts):
        big = shade(im, k).resize((64, 64), Image.NEAREST)
        return big.transform((s, s), Image.AFFINE, inverse(pts), Image.NEAREST)

    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.alpha_composite(put(front, 0.82, [(0, 0, 0, h), (64, 0, w, 2 * h), (0, 64, 0, 3 * h)]))
    out.alpha_composite(put(side, 0.62, [(0, 0, w, 2 * h), (64, 0, s, h), (0, 64, w, 4 * h)]))
    out.alpha_composite(put(top, 1.0, [(0, 0, w, 0), (64, 0, s, h), (0, 64, 0, h)]))
    return out.resize((SIZE, SIZE), Image.LANCZOS)


def sheet(skins, out, hashes):
    tile = SIZE * 2
    im = Image.new("RGBA", (tile * len(hashes), tile), (30, 30, 30, 255))
    for i, hsh in enumerate(hashes):
        im.alpha_composite(head(Image.open(os.path.join(skins, hsh + ".png"))).resize((tile, tile), Image.NEAREST),
                           (i * tile, 0))
    im.save(out)


def main(skins, resources):
    out = resources + "/assets/arcanicvisuals/textures/gui/funtime/"
    os.makedirs(out, exist_ok=True)
    for name, hsh in HEADS.items():
        head(Image.open(os.path.join(skins, hsh + ".png"))).save(out + name + ".png", optimize=True)
    print("painted", len(HEADS))


if __name__ == "__main__":
    if sys.argv[1] == "--sheet":
        sheet(sys.argv[2], sys.argv[3], sys.argv[4:])
    else:
        main(sys.argv[1], sys.argv[2])
