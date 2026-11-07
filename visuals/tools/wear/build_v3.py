"""Puts the old-style wings (wings_old, numbers 500+) and companions (pets_old, 600+) into the mod, in place of the
plain wings of the applied pack and the earlier companions; everything else in the catalogue stays as it is.
What it takes out is copied to BACKUP first.

    python build_v3.py"""

import json
import os
import shutil

import pets_old
import wearlib
import wings_old

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets",
                                     "arcanicvisuals"))
GEO = os.path.join(ROOT, "wear")
TEX = os.path.join(ROOT, "textures", "wear")
BACKUP = os.path.join(os.path.expanduser("~"), "OneDrive", "Desktop", "arcanic-wear-replaced")
PLAIN_WINGS = {89, 90, 91, 92, 94, 95, 96}  # the pack's plain feathered wings; its hooks (87, 88) stay


def files(n):
    return [os.path.join(GEO, f"cosmetic_{n}.json"), os.path.join(TEX, f"cosmetic_{n}.png"),
            os.path.join(TEX, f"icon_{n}.png")]


def main():
    idx = json.load(open(os.path.join(GEO, "index.json"), encoding="utf-8"))
    gone = [e for e in idx if e["n"] in PLAIN_WINGS or e["type"] == "pet" or 500 <= e["n"] < 700]
    keep = [e for e in idx if e not in gone]
    os.makedirs(BACKUP, exist_ok=True)
    for e in gone:
        for f in files(e["n"]):
            if os.path.exists(f):
                shutil.copy2(f, BACKUP)
                os.remove(f)
    json.dump(gone, open(os.path.join(BACKUP, "removed-entries.json"), "w", encoding="utf-8"), ensure_ascii=False,
              indent=1)
    new = []
    for designs, base in ((wings_old.ALL, 500), (pets_old.ALL, 600)):
        for i, fn in enumerate(designs):
            m = fn(base + i)
            q = wearlib.build(m, GEO, TEX)
            wearlib.icon(q, os.path.join(TEX, f"icon_{m.n}.png"), m.view)
            e = {"n": m.n, "name": m.name, "type": m.type, "pos": m.pos, "cat": m.cat, "y": m.y}
            for k in ("x", "z"):
                if getattr(m, k):
                    e[k] = round(getattr(m, k), 4)
            if m.scale != 1:
                e["scale"] = m.scale
            new.append(e)
            print(m.n, m.type, m.name, flush=True)
    wings = [e for e in keep if e["type"] == "wings"]
    rest = [e for e in keep if e["type"] != "wings"]
    final = wings + [e for e in new if e["type"] == "wings"] + rest + [e for e in new if e["type"] == "pet"]
    json.dump(final, open(os.path.join(GEO, "index.json"), "w", encoding="utf-8"), ensure_ascii=False,
              separators=(",", ":"))
    print("removed", len(gone), "added", len(new), "index", len(final), "backup", BACKUP)


if __name__ == "__main__":
    main()
