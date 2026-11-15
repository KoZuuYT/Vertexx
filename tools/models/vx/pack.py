"""Compila um Model (bbmodel) para o resource pack + dados de runtime do plugin.

Cada osso com cubos vira um modelo de item (``models/entity/<id>/<osso>.json``) renderizado por uma
ItemDisplay; os ossos são animados pelo plugin com os quadros pré-amostrados (20 Hz) de ``anim.bake``.
Limites do formato de modelos do jogo tratados aqui:
* coordenadas de elemento só vão de -16 a 32: ossos grandes são reduzidos por um fator ``k`` e a display
  é ampliada de volta;
* o jogo restringe rotações de elemento: qualquer cubo girado vira um sub-osso próprio (rotação do osso).
"""
from __future__ import annotations

import copy
import json
import math
import re
from pathlib import Path

from .anim import bake
from .model import FACES, Bone, Cube, Model, to_game

NS = "vertexx"
LIMIT = 23.5     # distância máxima (px) do pivô a qualquer vértice do cubo dentro do modelo de item


def slug(name: str) -> str:
    return re.sub(r"[^a-z0-9_]+", "_", name.lower()).strip("_") or "bone"


def _split_rotated(model: Model):
    """Cubos com rotação própria viram sub-ossos (a rotação passa a ser a do osso)."""
    def walk(bone: Bone):
        keep = []
        for c in bone.cubes:
            if any(abs(v) > 1e-6 for v in c.rot):
                sub = Bone(f"{bone.name}__{slug(c.name)}", list(c.origin), list(c.rot), [copy.copy(c)], [], [])
                sub.cubes[0].rot = [0.0, 0.0, 0.0]
                bone.children.append(sub)
            else:
                keep.append(c)
        bone.cubes = keep
        for child in list(bone.children):
            walk(child)

    for r in model.roots:
        walk(r)


def _element(cube: Cube, origin, k: float, res, tint=False):
    pad = cube.inflate
    lo = [(cube.frm[i] - pad - origin[i]) / k + 8 for i in range(3)]
    hi = [(cube.to[i] + pad - origin[i]) / k + 8 for i in range(3)]
    faces = {}
    for name in FACES:
        f = cube.faces.get(name)
        if f is None:
            continue
        u1, v1, u2, v2 = f.uv
        entry = {"uv": [round(u1 * 16 / res[0], 4), round(v1 * 16 / res[1], 4), round(u2 * 16 / res[0], 4),
                        round(v2 * 16 / res[1], 4)], "texture": f"#t{f.tex}"}
        if f.rot % 360:
            entry["rotation"] = f.rot % 360
        if tint:
            entry["tintindex"] = 0
        faces[name] = entry
    return {"from": [round(v, 4) for v in lo], "to": [round(v, 4) for v in hi], "faces": faces}


def compile_model(src: Model, model_id: str, out_ns: Path, runtime_dir: Path | None, scale_hint: float = 1.0):
    """Escreve os assets em ``out_ns`` (…/assets/vertexx) e o JSON de runtime. Devolve o dicionário de runtime."""
    model = to_game(src)
    model = copy.deepcopy(model)
    _split_rotated(model)
    bones = []
    index: dict[int, int] = {}
    pack_files = []

    def walk(b: Bone, parent: int):
        i = len(bones)
        index[id(b)] = i
        bones.append((b, parent))
        for c in b.children:
            walk(c, i)

    for r in model.roots:
        walk(r, -1)

    tex_ids = []
    for t_i, tex in enumerate(model.textures):
        rel = f"entity/{model_id}" + (f"_{t_i}" if len(model.textures) > 1 else "")
        path = out_ns / "textures" / f"{rel}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(tex.png)
        tex_ids.append(f"{NS}:{rel}")

    used = set()
    entries = []
    for b, parent in bones:
        name = slug(b.name)
        while name in used:
            name += "_"
        used.add(name)
        model_ref = None
        k = 1.0
        if b.cubes:
            extent = 0.0
            for c in b.cubes:
                pad = c.inflate
                for i in range(3):
                    extent = max(extent, abs(c.frm[i] - pad - b.origin[i]), abs(c.to[i] + pad - b.origin[i]))
            k = max(1.0, math.ceil(extent / LIMIT * 100) / 100)
            tint = b.name.endswith("_tint")
            elements = [_element(c, b.origin, k, model.res, tint) for c in b.cubes if c.faces]
            if elements:
                used_tex = sorted({f.tex for c in b.cubes for f in c.faces.values()})
                textures = {f"t{i}": tex_ids[i] for i in used_tex}
                textures["particle"] = tex_ids[used_tex[0]]
                mpath = out_ns / "models" / "entity" / model_id / f"{name}.json"
                mpath.parent.mkdir(parents=True, exist_ok=True)
                mpath.write_text(json.dumps({"textures": textures, "elements": elements}, separators=(",", ":")), encoding="utf-8")
                ipath = out_ns / "items" / "entity" / model_id / f"{name}.json"
                ipath.parent.mkdir(parents=True, exist_ok=True)
                definition = {"type": "minecraft:model", "model": f"{NS}:entity/{model_id}/{name}"}
                if tint:   # a cor vem do componente dyed_color do item (lã do golem, por exemplo)
                    definition["tints"] = [{"type": "minecraft:dye", "default": -1}]
                ipath.write_text(json.dumps({"model": definition}), encoding="utf-8")
                model_ref = f"{NS}:entity/{model_id}/{name}"
        entries.append({"n": b.name, "p": parent, "o": [round(v, 4) for v in b.origin], "r": [round(v, 4) for v in b.rot],
                        "m": model_ref, "k": k, "g": bool(b.glow)})

    locators = []
    for b, _ in bones:
        for loc in b.locators:
            locators.append({"n": loc.name, "b": index[id(b)], "p": [round(v, 4) for v in loc.pos]})

    anims = {}
    for a in model.anims:
        baked = bake(model, a)
        anims[a.name] = {"len": baked["length"], "loop": baked["loop"], "n": baked["frames"], "b": baked["bones"]}

    runtime = {"id": model_id, "scale": scale_hint, "bones": entries, "loc": locators, "anim": anims}
    if runtime_dir is not None:
        runtime_dir.mkdir(parents=True, exist_ok=True)
        (runtime_dir / f"{model_id}.json").write_text(json.dumps(runtime, separators=(",", ":")), encoding="utf-8")
    return runtime
