"""Livelier motion for finished designs: every swing made wider about its own middle, and a few motions added that
make things feel alive - wings breathing at rest, a backpack bouncing on the back, a shoulder pet hopping and shifting
its weight. Spins (whole turns), blinks and things that only appear for a moment are left as they are."""

import math


def liven(m, rot=1.5, pos=1.4, scl=1.3, skip=("lid_",)):
    for anim in m.anims.values():
        for bone, chans in anim["bones"].items():
            if any(bone.startswith(p) for p in skip):
                continue
            for ch, keys in chans.items():
                vals = list(keys.values())
                if not vals:
                    continue
                lo = [min(v[c] for v in vals) for c in range(3)]
                hi = [max(v[c] for v in vals) for c in range(3)]
                if ch == "rotation":
                    if max(h - l for h, l in zip(hi, lo)) > 300:  # a spin: a whole turn must stay a whole turn
                        continue
                    f = rot
                elif ch == "position":
                    f = pos
                else:
                    if min(lo) < .5 or max(hi) > 1.6:  # blinks, flashes: they switch, they don't swing
                        continue
                    f = scl
                mean = [sum(v[c] for v in vals) / len(vals) for c in range(3)]
                for k, v in keys.items():
                    keys[k] = [round(mean[c] + (v[c] - mean[c]) * f, 4) for c in range(3)]
    return m


def has(m, bone, ch):
    return any(ch in a["bones"].get(bone, {}) for a in m.anims.values())


def loop_of(m, default=3.0):
    for a in m.anims.values():
        return a["animation_length"]
    return default


def wing_breath(m):
    """Wings at rest open and lift a little, slowly, on top of their beat."""
    L = loop_of(m)
    for b in m.bones:
        if b.parent is None and b.rot and abs(b.rot[1]) > 5 and not has(m, b.name, "rotation"):
            s = 1 if b.rot[1] < 0 else -1  # the right wing is swept with a negative turn
            m.anim(b.name, "rotation", lambda t, s=s: (0, s * 4 * math.sin(t * math.pi * 2),
                                                        s * 3 * math.sin(t * math.pi * 2 + 1.2)), L, 64)


def pack_bounce(m):
    """The bag bounces softly on the back, twice a loop, as if walking."""
    if not any(b.name == "pack" for b in m.bones) or has(m, "pack", "position"):
        return
    L = loop_of(m)
    m.anim("pack", "position", lambda t: (0, .22 * abs(math.sin(t * math.pi * 2)), 0), L, 48)
    m.anim("pack", "rotation", lambda t: (1.6 * math.sin(t * math.pi * 4), 0, .8 * math.sin(t * math.pi * 2)), L, 48)


def pet_life(m):
    """A pet sitting on a shoulder hops now and then and shifts its weight; one floating already moves."""
    if m.type != "pet" or abs(m.y - .34) > .01:  # only the shoulder sitters
        return
    L = loop_of(m, 4.0)
    if not has(m, "pet", "position"):
        def hop(t):
            d = t - .55
            return (0, 1.1 * max(0.0, math.cos(d * math.pi / .12)) if abs(d) < .06 else 0.0, 0)

        m.anim("pet", "position", hop, L, 96)
    if not has(m, "pet", "rotation"):
        m.anim("pet", "rotation", lambda t: (2 * math.sin(t * math.pi * 4), 9 * math.sin(t * math.pi * 2 + .4),
                                             4 * math.sin(t * math.pi * 2)), L, 64)
