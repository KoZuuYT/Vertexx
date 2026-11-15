"""Entende poses a partir de imagens e as copia para modelos/animações.

Uma *pose* é ``{osso: {"rot": [x, y, z], "pos": [x, y, z]}}`` em **espaço do editor** (o mesmo que o Blockbench
mostra), somada à rotação de repouso do osso. ``fit_pose`` acha a pose cuja renderização casa com a imagem
(silhueta + arestas internas + cor, câmera fixa), ``save_pose``/``load_pose`` gravam em ``models/poses``,
``copy_pose`` leva a pose para outro modelo (mapa de nomes, espelho) e ``poses_to_animation`` transforma uma
sequência de poses (GIF, vídeo, folha de sprites) em animação do .bbmodel.
"""
from __future__ import annotations

import copy
import json
import re
from pathlib import Path

import cv2
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

from .cvscan import _boundary, inner_edges
from .model import Animation, Keyframe, Model, to_game
from .render import Camera, render

ZERO = [0.0, 0.0, 0.0]


# ───────────────────────────── conversões ─────────────────────────────

def to_game_values(values: dict | None) -> dict:
    """Pose do editor → espaço do jogo (modelo girado 180° em Y): rot (-x, y, -z), pos (-x, y, -z)."""
    out = {}
    for bone, v in (values or {}).items():
        d = {}
        if "rot" in v:
            r = v["rot"]
            d["rot"] = (-r[0], r[1], -r[2])
        if "pos" in v:
            p = v["pos"]
            d["pos"] = (-p[0], p[1], -p[2])
        if "scl" in v:
            d["scl"] = tuple(v["scl"])
        out[bone] = d
    return out


def _clean(values: dict, eps: float = 0.05) -> dict:
    out = {}
    for b, v in values.items():
        d = {k: [round(float(x), 2) for x in val] for k, val in v.items()
             if any(abs(x - (1 if k == "scl" else 0)) > eps for x in val)}
        if d:
            out[b] = d
    return out


def bone_names(model: Model, only_cubes: bool = True):
    return [b.name for b, _ in model.bones() if b.cubes or not only_cubes]


def bone_depth(model: Model) -> dict[str, int]:
    depth = {}

    def walk(b, d):
        depth[b.name] = d
        for c in b.children:
            walk(c, d + 1)

    for r in model.roots:
        walk(r, 0)
    return depth


# ───────────────────────────── ajuste ─────────────────────────────

class _Target:
    """Imagem de referência em baixa resolução + mapas pré-calculados."""

    def __init__(self, rgb, mask, cam: Camera, work: int):
        h, w = mask.shape
        k = work / max(h, w)
        self.k = k
        self.size = (max(1, round(w * k)), max(1, round(h * k)))
        self.mask = np.asarray(Image.fromarray(mask.astype(np.uint8) * 255).resize(self.size, Image.BILINEAR)) > 127
        self.rgb = np.asarray(Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).resize(self.size, Image.BILINEAR)).astype(np.float32)
        self.cam = Camera(cam.yaw, cam.pitch, cam.scale * k, cam.cx * k, cam.cy * k, cam.center, *self.size)
        self.dt = ndi.distance_transform_edt(~_boundary(self.mask))
        ie = inner_edges(self.rgb, self.mask)
        self.has_inner = ie.sum() > 12
        self.inner_dt = ndi.distance_transform_edt(~ie) if self.has_inner else None
        self.diag = float(np.hypot(*self.size))


def _loss(gm: Model, values_game: dict, t: _Target, color_w: float, inner_w: float, shade_k=None):
    hits = render(gm, values_game, t.cam, already_game=True, shade_k=shade_k)
    mine = hits.cube >= 0
    inter = np.logical_and(mine, t.mask).sum()
    union = np.logical_or(mine, t.mask).sum()
    iou = inter / union if union else 0.0
    b = _boundary(mine)
    cham = (t.dt[b].mean() / t.diag * 40) if b.any() else 1.0
    loss = (1 - iou) + 0.5 * cham
    if inner_w and t.has_inner:
        cube = hits.cube * 8 + hits.face
        e = np.zeros_like(mine)
        e[1:] |= cube[1:] != cube[:-1]
        e[:, 1:] |= cube[:, 1:] != cube[:, :-1]
        e &= mine
        if e.any():
            loss += inner_w * (t.inner_dt[e].mean() / t.diag * 20)
    if color_w:
        both = mine & t.mask
        if both.any():
            img = hits.rgba[..., :3].astype(np.float32)
            loss += color_w * float(np.abs(img[both] - t.rgb[both]).mean() / 255)
    return float(loss), float(iou)


def fit_pose(model: Model, rgb, mask, cam: Camera, bones: list[str] | None = None, start: dict | None = None,
             limits: dict | None = None, free_pos: tuple = (), work: int = 110, passes: int = 5,
             color_w: float = 0.0, inner_w: float = 0.6, prior: float = 0.06, min_gain: float = 0.001, sweep: bool = False, steps=(30, 15, 8, 4, 2, 1), shade_k=None,
             verbose: bool = True):
    """Acha a pose (espaço do editor) que melhor casa a imagem, com a câmera ``cam`` fixa.

    ``bones``: ossos livres (padrão: todos com cubos), do pai para os filhos. ``limits``: {osso: {"x": (min,max),
    "y": …, "z": …}} em graus, para eliminar a ambiguidade de profundidade (a silhueta não diz se o braço
    foi para frente ou para trás). ``free_pos``: ossos que também podem se deslocar (px). ``prior`` penaliza
    ângulos grandes (prefere o repouso quando duas poses casam igual). Devolve (pose, métricas)."""
    gm = to_game(model)
    t = _Target(rgb, mask, cam, work)
    names = bones or bone_names(model)
    depth = bone_depth(model)
    names = sorted(names, key=lambda n: depth.get(n, 0))
    limits = limits or {}
    values = {b: {"rot": list(v.get("rot", ZERO)), "pos": list(v.get("pos", ZERO))} for b, v in (start or {}).items()}
    for n in names:
        values.setdefault(n, {"rot": list(ZERO), "pos": list(ZERO)})

    def total(vals):
        loss, iou = _loss(gm, to_game_values(vals), t, color_w, inner_w, shade_k)
        reg = sum(abs(a) for n in names for a in vals[n]["rot"]) / (180.0 * max(1, len(names)))
        return loss + prior * reg, iou

    best, iou = total(values)
    if verbose:
        print(f"  pose inicial: perda={best:.3f} IoU={iou:.3f}")
    def descend(values, best, iou):
        for p in range(passes):
            improved = False
            for step in steps:
                for n in names:
                    channels = [("rot", i) for i in range(3)] + ([("pos", i) for i in range(3)] if n in free_pos else [])
                    for kind, i in channels:
                        lim = limits.get(n, {}).get("xyz"[i]) if kind == "rot" else None
                        for sign in (1, -1):
                            cand = copy.deepcopy(values)
                            cand[n][kind][i] += step * sign * (0.25 if kind == "pos" else 1.0)
                            if lim and not (lim[0] - 1e-6 <= cand[n][kind][i] <= lim[1] + 1e-6):
                                continue
                            loss, cand_iou = total(cand)
                            if loss < best - min_gain:
                                best, iou, values, improved = loss, cand_iou, cand, True
            if verbose:
                print(f"  passada {p + 1}: perda={best:.3f} IoU={iou:.3f}")
            if not improved:
                break
        return values, best, iou

    def coarse(values, best, iou):
        """Varredura grossa por osso/eixo: escapa de mínimos locais (membros atrás de outros)."""
        for n in names:
            for i in range(3):
                lim = limits.get(n, {}).get("xyz"[i])
                for angle in (-90, -60, -35, 35, 60, 90):
                    if lim and not (lim[0] <= angle <= lim[1]):
                        continue
                    cand = copy.deepcopy(values)
                    cand[n]["rot"][i] = angle
                    loss, cand_iou = total(cand)
                    if loss < best - min_gain:
                        best, iou, values = loss, cand_iou, cand
        return values, best, iou

    run_a = descend(copy.deepcopy(values), best, iou)
    run_b = descend(*coarse(copy.deepcopy(run_a[0]), run_a[1], run_a[2])) if sweep else run_a
    values, best, iou = min(run_a, run_b, key=lambda r: r[1])
    if verbose:
        print(f"  escolhido: {'repouso' if run_a[1] <= run_b[1] else 'varredura'}")
    pose = _clean({n: values[n] for n in names})
    return pose, {"iou": round(iou, 4), "loss": round(best, 4)}


# ───────────────────────────── arquivos de pose ─────────────────────────────

def save_pose(path, pose: dict, **meta):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps({**meta, "bones": pose}, indent=1, ensure_ascii=False), encoding="utf-8")


def load_pose(path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))["bones"]


def describe(pose: dict) -> str:
    """Texto legível da pose (ângulos por osso), em ordem decrescente de amplitude."""
    rows = []
    for b, v in pose.items():
        r = v.get("rot", ZERO)
        rows.append((max(abs(a) for a in r), f"{b}: rot=({r[0]:+.0f}°, {r[1]:+.0f}°, {r[2]:+.0f}°)"
                     + (f" pos=({v['pos'][0]:+.1f}, {v['pos'][1]:+.1f}, {v['pos'][2]:+.1f})" if "pos" in v else "")))
    return "\n".join(r for _, r in sorted(rows, reverse=True))


# ───────────────────────────── copiar poses ─────────────────────────────

_SWAPS = [("left", "right"), ("_l", "_r"), ("_fl", "_fr"), ("_bl", "_br"), ("_rl", "_rr"), ("_fl_", "_fr_"), ("_bl_", "_br_")]


def mirror_name(name: str) -> str:
    for a, b in _SWAPS:
        if a in name:
            return name.replace(a, b, 1)
        if b in name:
            return name.replace(b, a, 1)
    return name


def copy_pose(pose: dict, target: Model | None = None, mapping: dict | None = None, mirror: bool = False,
              amount: float = 1.0) -> dict:
    """Leva ``pose`` para outro modelo: ``mapping`` {osso_origem: osso_destino} (padrão: mesmo nome; ossos que o
    alvo não tem são ignorados), ``mirror`` troca esquerda↔direita e inverte os eixos y/z, ``amount`` escala os ângulos."""
    have = set(bone_names(target, only_cubes=False)) if target else None
    out = {}
    for b, v in pose.items():
        dst = (mapping or {}).get(b, b)
        if mirror:
            dst = mirror_name(dst)
        if have is not None and dst not in have:
            continue
        d = {}
        if "rot" in v:
            r = [a * amount for a in v["rot"]]
            d["rot"] = [r[0], -r[1], -r[2]] if mirror else r
        if "pos" in v:
            p = list(v["pos"])
            d["pos"] = [-p[0], p[1], p[2]] if mirror else p
        out[dst] = d
    return out


def blend_poses(a: dict, b: dict, u: float) -> dict:
    out = {}
    for n in set(a) | set(b):
        va, vb = a.get(n, {}), b.get(n, {})
        d = {}
        for k in ("rot", "pos"):
            if k in va or k in vb:
                x, y = np.array(va.get(k, ZERO)), np.array(vb.get(k, ZERO))
                d[k] = list(x + (y - x) * u)
        out[n] = d
    return out


# ───────────────────────────── de poses para animações ─────────────────────────────

def poses_to_animation(model: Model, poses: list[tuple[float, dict]], name: str, length: float | None = None,
                       loop: str = "loop", interp: str = "catmullrom", replace: bool = True) -> Animation:
    """Cria (ou substitui) a animação ``name`` do modelo com uma pose por instante (segundos)."""
    length = length if length is not None else max(t for t, _ in poses)
    tracks: dict[str, list[Keyframe]] = {}
    for time, pose in poses:
        for bone, v in pose.items():
            for key, channel in (("rot", "rotation"), ("pos", "position")):
                if key in v:
                    tracks.setdefault(f"{bone}|{channel}", []).append(Keyframe(channel, round(float(time), 4), [[round(float(x), 3) for x in v[key]]], interp))
    # canal que só aparece em alguns quadros precisa de zero nos outros, senão a interpolação o distorce
    times = sorted({t for t, _ in poses})
    for key, frames in tracks.items():
        have = {f.time for f in frames}
        for t in times:
            if round(t, 4) not in have:
                frames.append(Keyframe(frames[0].channel, round(float(t), 4), [list(ZERO)], interp))
        frames.sort(key=lambda f: f.time)
    anim = Animation(name, float(length), loop, tracks)
    if replace:
        model.anims = [a for a in model.anims if a.name != name]
    model.anims.append(anim)
    return anim


def fit_sequence(model: Model, frames, cam: Camera, fps: float = 10.0, bones=None, limits=None, free_pos=(),
                 smooth: float = 0.5, verbose: bool = False, **kw):
    """Ajusta uma pose por quadro (cada quadro parte da pose do anterior → continuidade) e devolve
    ``[(tempo, pose)]``. ``frames``: lista de (rgb, mask) já recortados na mesma escala/ancoragem da câmera."""
    out, prev = [], None
    for i, (rgb, mask) in enumerate(frames):
        pose, info = fit_pose(model, rgb, mask, cam, bones=bones, start=prev, limits=limits, free_pos=free_pos,
                              verbose=False, **kw)
        if prev is not None and smooth > 0:
            pose = blend_poses(pose, prev, smooth * 0.5)
        if verbose:
            print(f"  quadro {i}: IoU={info['iou']}")
        out.append((i / fps, pose))
        prev = pose
    return out
