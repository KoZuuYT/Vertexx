"""Importador de GeckoLib/Bedrock: ``*.geo.json`` (+ ``*.animation.json`` e a textura PNG ao lado).

Experimental: segue a convenção do Blockbench para o formato Bedrock (X espelhado nos pivôs/origens e
rotações X/Y invertidas). Os ``.bbmodel`` do Blockbench (qualquer formato, inclusive GeckoLib) já guardam
as coordenadas do editor e usam ``load_bbmodel`` — este módulo é só para quem tem os arquivos crus."""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

from .model import Animation, Bone, Cube, Face, Keyframe, Model, Texture, box_uv, png_bytes


def _val(v):
    return float(v) if isinstance(v, (int, float)) else v


def _vec(v):
    if isinstance(v, dict):
        v = v.get("vector", [0, 0, 0])
    if isinstance(v, (int, float, str)):
        v = [v, v, v]
    return [_val(x) for x in v]


def _neg(v):
    return -v if isinstance(v, (int, float)) else f"-({v})"


def load_geo(path, anim_path=None, texture=None) -> Model:
    path = Path(path)
    data = json.loads(path.read_text(encoding="utf-8"))
    if "minecraft:geometry" in data:
        geo = data["minecraft:geometry"][0]
    else:
        geo = next(v for k, v in data.items() if k.startswith("geometry."))
    desc = geo.get("description", {})
    res = (int(desc.get("texture_width", geo.get("texturewidth", 64))),
           int(desc.get("texture_height", geo.get("textureheight", 64))))
    name = path.name.replace(".geo.json", "").replace(".json", "")
    model = Model(name, res, fmt="geckolib")
    tex_path = Path(texture) if texture else path.with_name(name + ".png")
    if tex_path.exists():
        model.textures.append(Texture(name, png_bytes(Image.open(tex_path).convert("RGBA")), *res))
    bones: dict[str, Bone] = {}
    parents: dict[str, str | None] = {}
    for b in geo.get("bones", []):
        px, py, pz = b.get("pivot", [0, 0, 0])
        rot = b.get("rotation", [0, 0, 0])
        bone = Bone(b["name"], [-px, py, pz], [-rot[0], -rot[1], rot[2]])
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            cube = Cube(f"{b['name']}_cube", [-(ox + sx), oy, oz], [-ox, oy + sy, oz + sz],
                        inflate=float(c.get("inflate", 0)))
            cube.origin = [-c["pivot"][0], c["pivot"][1], c["pivot"][2]] if "pivot" in c else list(bone.origin)
            cr = c.get("rotation", [0, 0, 0])
            cube.rot = [-cr[0], -cr[1], cr[2]]
            uv = c.get("uv", [0, 0])
            if isinstance(uv, list):
                cube.faces = box_uv(uv, (sx, sy, sz))
                if c.get("mirror", b.get("mirror", False)):
                    for f in cube.faces.values():
                        f.uv[0], f.uv[2] = f.uv[2], f.uv[0]
            else:
                for fname, fd in uv.items():
                    u, v = fd["uv"]
                    w, h = fd.get("uv_size", [0, 0])
                    cube.faces[fname] = Face([u, v, u + w, v + h])
            bone.cubes.append(cube)
        bones[b["name"]] = bone
        parents[b["name"]] = b.get("parent")
    for n, bone in bones.items():
        p = parents[n]
        (bones[p].children if p in bones else model.roots).append(bone)
    if anim_path and Path(anim_path).exists():
        adata = json.loads(Path(anim_path).read_text(encoding="utf-8"))
        for aname, a in adata.get("animations", {}).items():
            loop = a.get("loop")
            anim = Animation(aname.split(".")[-1], float(a.get("animation_length", 1.0)),
                             "loop" if loop is True else ("hold" if loop == "hold_on_last_frame" else "once"))
            for bname, tracks in a.get("bones", {}).items():
                for ch in ("rotation", "position", "scale"):
                    t = tracks.get(ch)
                    if t is None:
                        continue
                    frames = []
                    if isinstance(t, dict) and "vector" not in t and "post" not in t:
                        for time, v in t.items():
                            interp = "linear"
                            if isinstance(v, dict):
                                interp = "catmullrom" if v.get("lerp_mode") == "catmullrom" else "linear"
                                post = _vec(v.get("post", v.get("pre", [0, 0, 0])))
                                pts = [_vec(v["pre"]), post] if "pre" in v and "post" in v else [post]
                            else:
                                pts = [_vec(v)]
                            frames.append(Keyframe(ch, float(time), pts, interp))
                    else:
                        frames.append(Keyframe(ch, 0.0, [_vec(t)]))
                    for f in frames:
                        if ch == "rotation":
                            f.points = [[_neg(p[0]), _neg(p[1]), p[2]] for p in f.points]
                        elif ch == "position":
                            f.points = [[_neg(p[0]), p[1], p[2]] for p in f.points]
                    anim.tracks[f"{bname}|{ch}"] = sorted(frames, key=lambda k: k.time)
            model.anims.append(anim)
    return model
