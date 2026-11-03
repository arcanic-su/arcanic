"""Builds the AWP bow skin from the [TaCZ] CS+ gun pack.

Source: csplus-1.3.1-hotfix2.jar, https://modrinth.com/mod/tacz-cs (CC BY-NC 4.0, by lolokeia).

    python tools/csplus_awp.py <csplus.jar> <src/main/resources>

The gun and its scope come as two models with two textures; the scope is mounted on the gun's
scope_pos bone and both textures share one atlas, so the skin draws with a single texture like a knife.
"""

import io
import json
import math
import os
import sys
import zipfile

from PIL import Image

PACK = "assets/csplus/"
KEY = "awp"
UNIT = 8  # atlas pixels per model texture unit (the scope texture's own density)
SCOPE_PREFIX = "scope_"
CLIPS = ("draw", "shoot", "inspect", "static_idle", "idle", "run", "run_start", "run_end")
SOUNDS = {
    "awp/awp_01": "awp_01",
    "awp/awp_draw": "awp_draw",
    "awp/awp_boltback_d": "awp_boltback_d",
    "awp/awp_boltforward_d": "awp_boltforward_d",
    "all/1": "movement1",
    "all/2": "movement2",
    "all/3": "movement3",
}
EFFECTS = {
    "csplus:awp/awp_draw": "awp_draw",
    "csplus:awp/awp_boltback_d": "awp_boltback_d",
    "csplus:awp/awp_boltforward_d": "awp_boltforward_d",
    "csplus:all/movement1": "movement1",
    "csplus:all/movement2": "movement2",
    "csplus:all/movement3": "movement3",
}


def geometry(jar, path):
    return json.loads(jar.read(PACK + path))["minecraft:geometry"][0]


def faces(geo, skip=()):
    for bone in geo["bones"]:
        if bone["name"] in skip:
            continue
        for cube in bone.get("cubes", []):
            for face in cube["uv"].values():
                yield face


def extent(geo, skip=()):
    w = h = 0
    for face in faces(geo, skip):
        u, v = face["uv"]
        su, sv = face["uv_size"]
        w = max(w, u, u + su)
        h = max(h, v, v + sv)
    return math.ceil(w), math.ceil(h)


def subtree(geo, root):
    names = {root}
    for bone in geo["bones"]:
        if bone.get("parent") in names:
            names.add(bone["name"])
    return [b for b in geo["bones"] if b["name"] in names]


def moved(point, by):
    return [round(point[i] + by[i], 5) for i in range(3)]


def build_geometry(jar):
    gun = geometry(jar, "geo_models/gun/awp_geo.json")
    scope = geometry(jar, "geo_models/attachment/awp_scope_geo.json")
    hands = ("righthand_pos", "lefthand_pos")
    for bone in gun["bones"]:
        if bone["name"] in hands:
            bone.pop("cubes", None)  # Blockbench's hand stand-ins, the player's own arms are drawn instead

    mount = next(b["pivot"] for b in gun["bones"] if b["name"] == "scope_pos")
    gun_w, gun_h = extent(gun)
    scope_bones = subtree(scope, "scope_body")
    scope_w, scope_h = extent({"bones": scope_bones})
    offset = gun_w + 1
    for bone in scope_bones:
        bone["name"] = SCOPE_PREFIX + bone["name"]
        bone["parent"] = SCOPE_PREFIX + bone["parent"] if "parent" in bone else "scope_pos"
        bone["pivot"] = moved(bone["pivot"], mount)
        for cube in bone.get("cubes", []):
            cube["origin"] = moved(cube["origin"], mount)
            if "pivot" in cube:
                cube["pivot"] = moved(cube["pivot"], mount)
            for face in cube["uv"].values():
                face["uv"] = [face["uv"][0] + offset, face["uv"][1]]
    eye = next(b["pivot"] for b in scope["bones"] if b["name"] == "scope_view")
    aim = {"name": "aim_view", "parent": "views", "pivot": moved(eye, mount)}

    bones = gun["bones"] + scope_bones + [aim]
    width, height = offset + scope_w + 1, max(gun_h, scope_h) + 1
    gun["description"].update({"identifier": "geometry.awp", "texture_width": width, "texture_height": height})
    model = {"format_version": "1.12.0", "minecraft:geometry": [{"description": gun["description"], "bones": bones}]}
    return model, (gun_w, gun_h), (scope_w, scope_h), offset, (width, height)


def build_atlas(jar, gun_size, scope_size, offset, size):
    gun = Image.open(io.BytesIO(jar.read(PACK + "textures/gun/uv/awp.png"))).convert("RGBA")
    scope = Image.open(io.BytesIO(jar.read(PACK + "textures/attachment/uv/awp_scope.png"))).convert("RGBA")
    gun_unit = gun.width // 128
    scope_unit = scope.width // 128
    atlas = Image.new("RGBA", (size[0] * UNIT, size[1] * UNIT), (0, 0, 0, 0))
    part = gun.crop((0, 0, gun_size[0] * gun_unit, gun_size[1] * gun_unit))
    atlas.paste(part.resize((gun_size[0] * UNIT, gun_size[1] * UNIT), Image.NEAREST), (0, 0))
    part = scope.crop((0, 0, scope_size[0] * scope_unit, scope_size[1] * scope_unit))
    atlas.paste(part.resize((scope_size[0] * UNIT, scope_size[1] * UNIT), Image.NEAREST), (offset * UNIT, 0))
    return atlas


def build_animation(jar):
    source = json.loads(jar.read(PACK + "animations/awp.animation.json"))["animations"]
    clips = {}
    for name in CLIPS:
        clip = dict(source[name])
        clip.pop("blend_weight", None)
        cues = {}
        for time, cue in clip.get("sound_effects", {}).items():
            effect = EFFECTS.get(cue["effect"])
            if effect:
                cues[time] = {"effect": "melee.awp." + effect}
        if name == "shoot":
            cues["0.0"] = {"effect": "melee.awp.awp_01"}
        if cues:
            clip["sound_effects"] = dict(sorted(cues.items(), key=lambda c: float(c[0])))
        else:
            clip.pop("sound_effects", None)
        clips[name] = clip
    clips["bolt"] = bolt_clip(clips["draw"], clips["static_idle"])
    clips["shoot"] = recoil_clip(clips["shoot"])
    return {"format_version": "1.8.0", "animations": clips}


# The draw works the bolt cleanly: the right hand reaches the handle, pulls it back and pushes it home while the
# left hand stays on the handguard. The bolt after a shot is that same stretch of the draw, starting from the
# still pose instead of from the gun coming up.
DRAW_GRAB = 0.3167  # the right hand is on the handle in the draw
BOLT_GRAB = 0.22  # and here in the bolt clip
BOLT_FROM = {"righthand": DRAW_GRAB, "main": DRAW_GRAB, "bolt": DRAW_GRAB, "rotating part": 0.2167}
# The shot itself only kicks the gun and the view; it is laid over the pose, so its keys start from zero.
RECOIL_BONES = ("main", "camera")
RECOIL_SETTLED = 0.3667
RECOIL_ZERO = 0.5


def key_time(time):
    return "%.4g" % time


def bolt_clip(draw, still):
    shift = BOLT_GRAB - DRAW_GRAB
    bones = {}
    for bone, start in BOLT_FROM.items():
        channels = {}
        for channel, keys in draw["bones"][bone].items():
            rest = still["bones"].get(bone, {}).get(channel, [0, 0, 0])
            if isinstance(rest, dict):
                rest = next(iter(rest.values()))
                rest = rest["post"] if isinstance(rest, dict) else rest
            out = {"0.0": {"post": rest, "lerp_mode": "catmullrom"}}
            for time, key in keys.items():
                if float(time) >= start - 1.0e-4:
                    out[key_time(float(time) + shift)] = key
            channels[channel] = out
        bones[bone] = channels
    bones["lefthand"] = draw["bones"]["lefthand"]
    cues = {
        key_time(float(time) + shift): cue
        for time, cue in draw["sound_effects"].items()
        if float(time) >= DRAW_GRAB
    }
    return {
        "animation_length": round(draw["animation_length"] + shift, 4),
        "bones": bones,
        "sound_effects": cues,
    }


def recoil_clip(shoot):
    bones = {}
    for bone in RECOIL_BONES:
        channels = {}
        for channel, keys in shoot["bones"][bone].items():
            if bone == "camera":
                channels[channel] = keys
                continue
            kept = {t: k for t, k in keys.items() if float(t) <= RECOIL_SETTLED + 1.0e-4}
            kept[key_time(RECOIL_ZERO)] = {"post": [0, 0, 0], "lerp_mode": "catmullrom"}
            channels[channel] = kept
        bones[bone] = channels
    return {
        "animation_length": shoot["animation_length"],
        "bones": bones,
        "sound_effects": {"0.0": {"effect": "melee.awp.awp_01"}},
    }


def main(jar_path, resources):
    jar = zipfile.ZipFile(jar_path)
    arcanic = resources + "/assets/arcanic/"
    model, gun_size, scope_size, offset, size = build_geometry(jar)
    with open(arcanic + "cosmetic/melee/awp.geo.json", "w", encoding="utf-8") as out:
        json.dump(model, out, separators=(",", ":"))
    with open(arcanic + "cosmetic/melee/awp.anim.json", "w", encoding="utf-8") as out:
        json.dump(build_animation(jar), out, separators=(",", ":"))
    build_atlas(jar, gun_size, scope_size, offset, size).save(arcanic + "textures/cosmetic/melee/awp.png", optimize=True)
    with open(arcanic + "textures/cosmetic/melee/slot/awp.png", "wb") as out:
        out.write(jar.read(PACK + "textures/gun/slot/awp.png"))

    os.makedirs(arcanic + "sounds/melee/awp", exist_ok=True)
    for source, name in SOUNDS.items():
        with open(arcanic + "sounds/melee/awp/" + name + ".ogg", "wb") as out:
            out.write(jar.read(PACK + "tacz_sounds/" + source + ".ogg"))
    with open(arcanic + "sounds.json", encoding="utf-8") as f:
        sounds = json.load(f)
    for name in SOUNDS.values():
        sounds["melee.awp." + name] = {"sounds": [{"name": "arcanic:melee/awp/" + name, "stream": False}]}
    with open(arcanic + "sounds.json", "w", encoding="utf-8", newline="\r\n") as out:
        json.dump(sounds, out, ensure_ascii=False, indent=2)
    print("atlas", size, "units, gun", gun_size, "scope", scope_size, "at", offset)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
