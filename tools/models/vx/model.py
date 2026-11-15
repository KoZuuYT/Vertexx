"""Modelo comum de entidade (ossos, cubos, localizadores, animações) e leitura/escrita de .bbmodel.

O .bbmodel do Blockbench guarda o mesmo JSON para todos os formatos de entidade (modded entity,
OptiFine entity, Bedrock/GeckoLib, java block e "free"), só mudando ``meta.model_format`` e o uso de
box UV. Este módulo lê tudo isso para uma estrutura única; ``geo.py`` e ``jem.py`` convertem os
formatos de arquivo "crus" (GeckoLib .geo.json/.animation.json e OptiFine .jem) para a mesma estrutura.

Convenções (espaço do editor, igual ao Blockbench): +Y para cima, a frente do modelo olha para -Z
(norte), unidades em pixels (16 px = 1 bloco), rotações em graus, ordem ZYX (R = Rz·Ry·Rx).
``to_game`` reorienta para o jogo (frente em +Z).
"""
from __future__ import annotations

import base64
import io
import json
import uuid as uuidlib
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image

FACES = ("north", "east", "south", "west", "up", "down")
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "up", "down": "down"}


@dataclass
class Face:
    uv: list[float]            # [u1, v1, u2, v2] em pixels da resolução do modelo
    tex: int = 0
    rot: int = 0               # 0/90/180/270 (como no JSON do jogo)


@dataclass
class Cube:
    name: str
    frm: list[float]
    to: list[float]
    origin: list[float] = field(default_factory=lambda: [0.0, 0.0, 0.0])
    rot: list[float] = field(default_factory=lambda: [0.0, 0.0, 0.0])
    inflate: float = 0.0
    faces: dict[str, Face] = field(default_factory=dict)
    uuid: str = field(default_factory=lambda: str(uuidlib.uuid4()))
    visible: bool = True

    @property
    def size(self):
        return [self.to[i] - self.frm[i] for i in range(3)]


@dataclass
class Locator:
    name: str
    pos: list[float]
    rot: list[float] = field(default_factory=lambda: [0.0, 0.0, 0.0])


@dataclass
class Keyframe:
    channel: str                         # rotation | position | scale
    time: float
    points: list[list]                   # 1 ponto, ou 2 (antes/depois) — cada um [x, y, z] (número ou Molang)
    interp: str = "linear"               # linear | catmullrom | bezier | step
    bezier: dict = field(default_factory=dict)


@dataclass
class Bone:
    name: str
    origin: list[float] = field(default_factory=lambda: [0.0, 0.0, 0.0])
    rot: list[float] = field(default_factory=lambda: [0.0, 0.0, 0.0])
    cubes: list[Cube] = field(default_factory=list)
    children: list["Bone"] = field(default_factory=list)
    locators: list[Locator] = field(default_factory=list)
    uuid: str = field(default_factory=lambda: str(uuidlib.uuid4()))
    glow: bool = False
    visible: bool = True


@dataclass
class Animation:
    name: str
    length: float
    loop: str = "once"                   # once | hold | loop
    tracks: dict[str, list[Keyframe]] = field(default_factory=dict)   # "osso|canal" -> keyframes


@dataclass
class Texture:
    name: str
    png: bytes
    uv_w: int = 0
    uv_h: int = 0

    def image(self) -> Image.Image:
        return Image.open(io.BytesIO(self.png)).convert("RGBA")


@dataclass
class Model:
    name: str
    res: tuple[int, int] = (64, 64)
    textures: list[Texture] = field(default_factory=list)
    roots: list[Bone] = field(default_factory=list)
    anims: list[Animation] = field(default_factory=list)
    fmt: str = "free"
    facing: str = "north"                # lado para onde o modelo olha no editor (north = -Z)

    def bones(self):
        out = []

        def walk(b: Bone, parent: Bone | None):
            out.append((b, parent))
            for c in b.children:
                walk(c, b)

        for r in self.roots:
            walk(r, None)
        return out

    def find(self, name: str) -> Bone | None:
        for b, _ in self.bones():
            if b.name == name:
                return b
        return None


# ───────────────────────────── box UV ─────────────────────────────

def box_uv(offset, size):
    """Layout "cruz" do Minecraft (igual ao box UV do Blockbench) como UVs explícitos, orientados como no jogo."""
    u, v = offset
    w, h, d = size
    return {
        "up": Face([u + d, v, u + d + w, v + d]),
        "down": Face([u + d + w, v, u + d + 2 * w, v + d]),
        "east": Face([u, v + d, u + d, v + d + h]),
        "north": Face([u + d, v + d, u + d + w, v + d + h]),
        "west": Face([u + d + w, v + d, u + 2 * d + w, v + d + h]),
        "south": Face([u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h]),
    }


def _num(value, default=0.0):
    try:
        return float(value)
    except (TypeError, ValueError):
        return value  # expressão Molang: mantida como texto


# ───────────────────────────── leitura .bbmodel ─────────────────────────────

def load_bbmodel(path: str | Path) -> Model:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    meta = data.get("meta", {})
    fmt = meta.get("model_format", "free")
    res = data.get("resolution", {})
    model = Model(name=data.get("name", Path(path).stem), res=(int(res.get("width", 64)), int(res.get("height", 64))),
                  fmt=fmt)
    model.facing = data.get("vx_facing", "north")
    global_box = bool(meta.get("box_uv", False))

    for t in data.get("textures", []):
        src = t.get("source", "")
        if src.startswith("data:"):
            raw = base64.b64decode(src.split(",", 1)[1])
        elif t.get("path") and Path(t["path"]).exists():
            raw = Path(t["path"]).read_bytes()
        else:
            raise ValueError(f"textura sem dados embutidos: {t.get('name')}")
        model.textures.append(Texture(t.get("name", "tex"), raw, int(t.get("uv_width", model.res[0])),
                                      int(t.get("uv_height", model.res[1]))))

    elements = {}
    for e in data.get("elements", []):
        elements[e["uuid"]] = e

    def make_cube(e) -> Cube:
        frm, to = [float(v) for v in e["from"]], [float(v) for v in e["to"]]
        inflate = float(e.get("inflate", 0))
        cube = Cube(e.get("name", "cube"), frm, to, [float(v) for v in e.get("origin", [0, 0, 0])],
                    [float(v) for v in e.get("rotation", [0, 0, 0])], inflate, uuid=e["uuid"],
                    visible=e.get("export", True) is not False and e.get("visibility", True) is not False)
        if e.get("box_uv", global_box):
            cube.faces = box_uv(e.get("uv_offset", [0, 0]), cube.size)
            if e.get("mirror_uv"):
                for f in cube.faces.values():
                    f.uv[0], f.uv[2] = f.uv[2], f.uv[0]
        else:
            for name, f in e.get("faces", {}).items():
                if f.get("uv") is None or f.get("texture") is None:
                    continue
                cube.faces[name] = Face([float(v) for v in f["uv"]], int(f["texture"]), int(f.get("rotation", 0)))
        return cube

    def make_bone(node) -> Bone | None:
        b = Bone(node.get("name", "bone"), [float(v) for v in node.get("origin", [0, 0, 0])],
                 [float(v) for v in node.get("rotation", [0, 0, 0])], uuid=node.get("uuid", str(uuidlib.uuid4())))
        b.visible = node.get("export", True) is not False
        b.glow = b.name.endswith("_glow") or b.name.startswith("glow_")
        for child in node.get("children", []):
            if isinstance(child, str):
                e = elements.get(child)
                if e is None:
                    continue
                kind = e.get("type", "cube")
                if kind == "cube":
                    c = make_cube(e)
                    if c.visible:
                        b.cubes.append(c)
                elif kind in ("locator", "null_object"):
                    b.locators.append(Locator(e.get("name", "loc"), [float(v) for v in e.get("position", e.get("from", [0, 0, 0]))],
                                              [float(v) for v in e.get("rotation", [0, 0, 0])]))
                # malhas (mesh) não têm equivalente em display entities: ignoradas
            else:
                sub = make_bone(child)
                if sub is not None:
                    b.children.append(sub)
        return b if b.visible else None

    loose = Bone("root")
    for node in data.get("outliner", []):
        if isinstance(node, str):
            e = elements.get(node)
            if e is not None and e.get("type", "cube") == "cube":
                loose.cubes.append(make_cube(e))
        else:
            bone = make_bone(node)
            if bone is not None:
                model.roots.append(bone)
    if loose.cubes:
        model.roots.insert(0, loose)

    by_uuid = {b.uuid: b.name for b, _ in model.bones()}
    for a in data.get("animations", []):
        anim = Animation(a.get("name", "anim"), float(a.get("length", 1)), a.get("loop", "once"))
        for key, animator in a.get("animators", {}).items():
            bone_name = by_uuid.get(key, animator.get("name"))
            if bone_name is None or animator.get("type", "bone") != "bone":
                continue
            for k in animator.get("keyframes", []):
                channel = k.get("channel")
                if channel not in ("rotation", "position", "scale"):
                    continue
                pts = [[_num(p.get("x", 0)), _num(p.get("y", 0)), _num(p.get("z", 0))] for p in k.get("data_points", [])]
                if not pts:
                    continue
                bez = {n: [float(v) for v in k[n]] for n in ("bezier_left_time", "bezier_left_value",
                                                              "bezier_right_time", "bezier_right_value") if n in k}
                anim.tracks.setdefault(f"{bone_name}|{channel}", []).append(
                    Keyframe(channel, float(k.get("time", 0)), pts, k.get("interpolation", "linear"), bez))
        for frames in anim.tracks.values():
            frames.sort(key=lambda f: f.time)
        model.anims.append(anim)
    return model


# ───────────────────────────── escrita .bbmodel ─────────────────────────────

def save_bbmodel(model: Model, path: str | Path, fmt: str | None = None):
    elements = []

    def dump_bone(b: Bone):
        children = []
        for c in b.cubes:
            faces = {}
            for fname in FACES:
                f = c.faces.get(fname)
                if f is not None:
                    faces[fname] = {"uv": [round(v, 4) for v in f.uv], "texture": f.tex, **({"rotation": f.rot} if f.rot else {})}
            elements.append({"name": c.name, "box_uv": False, "rescale": False, "locked": False, "light_emission": 0,
                             "render_order": "default", "allow_mirror_modeling": True, "from": c.frm, "to": c.to,
                             "autouv": 0, "color": 0, "inflate": c.inflate, "origin": c.origin, "rotation": c.rot,
                             "faces": faces, "type": "cube", "uuid": c.uuid})
            children.append(c.uuid)
        for loc in b.locators:
            lid = str(uuidlib.uuid4())
            elements.append({"name": loc.name, "type": "locator", "position": loc.pos, "rotation": loc.rot, "uuid": lid})
            children.append(lid)
        for sub in b.children:
            children.append(dump_bone(sub))
        return {"name": b.name, "origin": b.origin, "rotation": b.rot, "color": 0, "uuid": b.uuid, "export": True,
                "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": children}

    outliner = [dump_bone(r) for r in model.roots]
    names = {b.uuid: b.name for b, _ in model.bones()}
    id_of = {b.name: b.uuid for b, _ in model.bones()}
    anims = []
    for a in model.anims:
        animators: dict = {}
        for key, frames in a.tracks.items():
            bone, channel = key.split("|")
            node = animators.setdefault(id_of[bone], {"name": bone, "type": "bone", "keyframes": []})
            for f in frames:
                kf = {"channel": channel, "data_points": [{"x": p[0], "y": p[1], "z": p[2]} for p in f.points],
                      "uuid": str(uuidlib.uuid4()), "time": f.time, "color": -1, "interpolation": f.interp}
                kf.update(f.bezier)
                node["keyframes"].append(kf)
        anims.append({"uuid": str(uuidlib.uuid4()), "name": a.name, "loop": a.loop, "override": False,
                      "length": a.length, "snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "",
                      "start_delay": "", "loop_delay": "", "animators": animators})
    textures = []
    for i, t in enumerate(model.textures):
        img = t.image()
        textures.append({"path": "", "name": t.name, "folder": "", "namespace": "", "id": str(i), "particle": i == 0,
                         "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
                         "frame_order": "", "frame_interpolate": False, "visible": True, "mode": "bitmap",
                         "saved": True, "uuid": str(uuidlib.uuid4()), "width": img.width, "height": img.height,
                         "uv_width": model.res[0], "uv_height": model.res[1],
                         "source": "data:image/png;base64," + base64.b64encode(t.png).decode()})
    out = {"meta": {"format_version": "4.10", "model_format": fmt or model.fmt, "box_uv": False},
           "name": model.name, "model_identifier": model.name, "visible_box": [1, 1, 0],
           "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
           "unhandled_root_fields": {}, "vx_facing": model.facing,
           "resolution": {"width": model.res[0], "height": model.res[1]},
           "elements": elements, "outliner": outliner, "textures": textures, "animations": anims}
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(out, indent=1, ensure_ascii=False), encoding="utf-8")


def png_bytes(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()


# ───────────────────────────── editor → jogo ─────────────────────────────

def to_game(model: Model) -> Model:
    """Gira o modelo 180° em Y quando a frente está em -Z, pondo a frente em +Z como nas entidades do jogo.

    Posições (x,z)→(-x,-z); rotações/ posições animadas idem; faces north↔south, east↔west; as faces de
    cima e de baixo ganham rotação de 180° para que a textura continue igual."""
    if model.facing != "north":
        return model
    import copy
    m = copy.deepcopy(model)

    def flip_pos(p):
        return [-p[0], p[1], -p[2]]

    def flip_rot(r):
        return [-r[0], r[1], -r[2]]

    for b, _ in m.bones():
        b.origin = flip_pos(b.origin)
        b.rot = flip_rot(b.rot)
        for loc in b.locators:
            loc.pos = flip_pos(loc.pos)
            loc.rot = flip_rot(loc.rot)
        for c in b.cubes:
            c.frm, c.to = [-c.to[0], c.frm[1], -c.to[2]], [-c.frm[0], c.to[1], -c.frm[2]]
            c.origin = flip_pos(c.origin)
            c.rot = flip_rot(c.rot)
            faces = {}
            for name, f in c.faces.items():
                nf = Face(list(f.uv), f.tex, f.rot)
                if name in ("up", "down"):
                    nf.rot = (nf.rot + 180) % 360
                faces[OPPOSITE[name]] = nf
            c.faces = faces
    for a in m.anims:
        for key, frames in a.tracks.items():
            for f in frames:
                if f.channel in ("rotation", "position"):
                    f.points = [[_neg(p[0]), p[1], _neg(p[2])] for p in f.points]
                    for n in ("bezier_left_value", "bezier_right_value"):
                        if n in f.bezier:
                            v = f.bezier[n]
                            f.bezier[n] = [-v[0], v[1], -v[2]]
    m.facing = "south"
    return m


def _neg(v):
    return -v if isinstance(v, (int, float)) else f"-({v})"
