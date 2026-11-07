"""Builds some designs into a scratch folder and lays their icons out on one sheet: preview.py packs [out.png]."""

import importlib
import os
import sys
import tempfile

from PIL import Image

import wearlib

mod = importlib.import_module(sys.argv[1])
out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(tempfile.gettempdir(), "wear_preview.png")
body = "--body" in sys.argv


def mannequin(m):
    """A plain grey player under the item, to see how it sits (preview only)."""
    skin, shirt = wearlib.fill("#c9a98e", edge=.1), wearlib.fill("#5d6b7a", edge=.1)
    b = m.bone("_mannequin")
    b.add(wearlib.box((-4, 24, -4), (8, 8, 8), skin), wearlib.box((-4, 12, -2), (8, 12, 4), shirt),
          wearlib.box((4, 12, -2), (4, 12, 4), shirt), wearlib.box((-8, 12, -2), (4, 12, 4), shirt))
tmp = tempfile.mkdtemp(prefix="wear_")
icons = []
for i, f in enumerate(mod.ALL):
    m = f(900 + i)
    m.shade = True
    if body:
        mannequin(m)
    q = wearlib.build(m, tmp, tmp)
    p = os.path.join(tmp, f"icon_{m.n}.png")
    wearlib.icon(q, p, m.view)
    icons.append(Image.open(p))
cols = 6
S = 160
rows = (len(icons) + cols - 1) // cols
sheet = Image.new("RGBA", (cols * S, rows * S), (38, 38, 46, 255))
for i, im in enumerate(icons):
    sheet.alpha_composite(im.resize((S - 8, S - 8), Image.LANCZOS), ((i % cols) * S + 4, (i // cols) * S + 4))
sheet.save(out)
print(out)
