"""Builds designs into a scratch folder and shows them as the game poses them (jsonview), on the mannequin:
    python look.py <module> <out.png> [i,j,...] [--t 0,0.25,0.5] [--views back,side]"""

import importlib
import json
import os
import sys
import tempfile

import jsonview
import wearlib

a = sys.argv[1:]
mod = importlib.import_module(a[0])
out = a[1]
pick = None
LIVE = None
if "--live" in a:
    import build_v4
    a.remove("--live")
    base = {"wings_old": 500, "pets_old": 600, "packs_new": 700, "hats_new": 800}[a[0]]
    LIVE = build_v4.LIFE[base]
times, views = [0.0], ["back"]
i = 2
while i < len(a):
    if a[i] == "--t":
        times = [float(x) for x in a[i + 1].split(",")]
        i += 2
    elif a[i] == "--views":
        views = a[i + 1].split(",")
        i += 2
    else:
        pick = [int(x) for x in a[i].split(",")]
        i += 1
tmp = tempfile.mkdtemp(prefix="look_")
entries = []
ns = []
for k, f in enumerate(mod.ALL):
    if pick and k not in pick:
        continue
    m = f(900 + k)
    if LIVE:
        LIVE(m)
        m.shade = True
    wearlib.build(m, tmp, tmp)
    entries.append({"n": m.n, "name": m.name, "type": m.type, "pos": m.pos, "cat": m.cat, "y": m.y, "x": m.x,
                    "z": m.z, "scale": m.scale})
    ns.append(m.n)
json.dump(entries, open(os.path.join(tmp, "index.json"), "w"))
sys.argv = ["jsonview", tmp, tmp, out] + [str(n) for n in ns] + ["--t", ",".join(map(str, times)), "--views",
                                                                 ",".join(views), "--body"]
jsonview.main()
