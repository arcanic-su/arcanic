"""The whole new-style collection into the mod: backpacks (packs_new, 700+), headwear (hats_new, 800+), wings and the
Feast of Abscession hooks (wings_old, 500+) and companions (pets_old, 600+), in place of the pack's backpacks,
headwear and hooks; the katanas and the hamster (85, 86, 93) stay. What is taken out is copied to BACKUP first.

    python build_v4.py"""

import json
import os
import shutil

import hats_new
import lively
import packs_new
import pets_old
import wearlib
import wings_old

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets",
                                     "arcanicvisuals"))
GEO = os.path.join(ROOT, "wear")
TEX = os.path.join(ROOT, "textures", "wear")
BACKUP = os.path.join(os.path.expanduser("~"), "OneDrive", "Desktop", "arcanic-wear-replaced-2")
KEEP = {85, 86, 93}  # the Deadpool katanas, the NASA hamster, the Ronin katana
SETS = [(wings_old.ALL, 500), (pets_old.ALL, 600), (packs_new.ALL, 700), (hats_new.ALL, 800)]
# livelier motion, per kind: how much wider every swing gets, then what is added on top
LIFE = {500: lambda m: (lively.liven(m, rot=1.6, pos=1.3, scl=1.25), lively.wing_breath(m)),
        600: lambda m: (lively.liven(m, rot=1.5, pos=1.4, scl=1.3), lively.pet_life(m)),
        700: lambda m: (lively.liven(m, rot=1.4, pos=1.2, scl=1.2), lively.pack_bounce(m)),
        800: lambda m: lively.liven(m, rot=1.3, pos=1.25, scl=1.2)}
ORDER = {"wings": 0, "bodywear": 1, "hat": 2, "pet": 3}


def files(n):
    return [os.path.join(GEO, f"cosmetic_{n}.json"), os.path.join(TEX, f"cosmetic_{n}.png"),
            os.path.join(TEX, f"icon_{n}.png")]


def main():
    idx = json.load(open(os.path.join(GEO, "index.json"), encoding="utf-8"))
    keep = [e for e in idx if e["n"] in KEEP]
    gone = [e for e in idx if e["n"] not in KEEP]
    foreign = [e for e in gone if not 500 <= e["n"] < 900]  # what this script did not make itself
    os.makedirs(BACKUP, exist_ok=True)
    if foreign:
        json.dump(foreign, open(os.path.join(BACKUP, "removed-entries.json"), "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)
    for e in gone:
        for f in files(e["n"]):
            if os.path.exists(f):
                if e in foreign:
                    shutil.copy2(f, BACKUP)
                os.remove(f)
    new = []
    for designs, base in SETS:
        for i, fn in enumerate(designs):
            m = fn(base + i)
            LIFE[base](m)
            m.shade = True  # baked light: rims, shadow down the sides, dark corners
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
    final = sorted(keep + new, key=lambda e: (ORDER.get(e["type"], 9), e["n"] not in KEEP, e["n"]))
    json.dump(final, open(os.path.join(GEO, "index.json"), "w", encoding="utf-8"), ensure_ascii=False,
              separators=(",", ":"))
    print("removed", len(gone), "made", len(new), "index", len(final), "backup", BACKUP)


if __name__ == "__main__":
    main()
