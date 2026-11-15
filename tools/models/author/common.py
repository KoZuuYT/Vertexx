"""Rotina comum dos modelos: constrói, ajusta a câmera à referência, projeta a textura, compara e salva."""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from vx import refscan                                   # noqa: E402
from vx.anim import bake as bake_anim                    # noqa: E402
from vx.model import save_bbmodel                        # noqa: E402
from vx.render import Camera, bone_values, contact_sheet, fit_camera_to  # noqa: E402

ROOT = HERE.parents[2]
SRC = ROOT / "models" / "src"
REFS = ROOT / "models" / "refs"
PREVIEW = ROOT / "models" / "preview"


def mirror_fill(b, src: str, dst: str, mapping=None):
    """Copia os texels de um cubo para o espelhado (x invertido): leste↔oeste espelhados horizontalmente."""
    mapping = mapping or {"north": "north", "south": "south", "up": "up", "down": "down", "east": "west", "west": "east"}
    for f_src, f_dst in mapping.items():
        sx, sy, sw, sh = b.rect(src, f_src)
        dx, dy, dw, dh = b.rect(dst, f_dst)
        if (sw, sh) != (dw, dh):
            continue
        block = b.atlas.px[sy:sy + sh, sx:sx + sw].copy()
        b.atlas.px[dy:dy + dh, dx:dx + dw] = block[:, ::-1]


def copy_face(b, src: str, f_src: str, dst: str, f_dst: str, flip=False):
    sx, sy, sw, sh = b.rect(src, f_src)
    dx, dy, dw, dh = b.rect(dst, f_dst)
    if (sw, sh) != (dw, dh):
        return
    block = b.atlas.px[sy:sy + sh, sx:sx + sw].copy()
    b.atlas.px[dy:dy + dh, dx:dx + dw] = block[:, ::-1] if flip else block


OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}


def fill_unseen(b, seen, pairs=()):
    """Texels que a referência não mostrou: usa o cubo parceiro espelhado, a face oposta, ou a cor média do cubo."""
    partner = {}
    for a, c in pairs:
        partner[a], partner[c] = c, a
    for name, cube in b.cubes.items():
        lay = b.layout[cube.uuid]
        seen_px = [(x, y, w, h) for (x, y, w, h) in lay.values()]
        mask_c = np.zeros_like(seen)
        for x, y, w, h in seen_px:
            mask_c[y:y + h, x:x + w] = seen[y:y + h, x:x + w]
        mean = None
        if mask_c.any():
            mean = b.atlas.px[mask_c][:, :3].mean(axis=0).astype(np.uint8)
        for face, (x, y, w, h) in lay.items():
            miss = ~seen[y:y + h, x:x + w]
            if not miss.any():
                continue
            src = None
            if name in partner and partner[name] in b.cubes:
                oc = b.cubes[partner[name]]
                of = {"east": "west", "west": "east"}.get(face, face)
                ox, oy, ow, oh = b.layout[oc.uuid][of]
                if (ow, oh) == (w, h) and seen[oy:oy + oh, ox:ox + ow].any():
                    block = b.atlas.px[oy:oy + oh, ox:ox + ow][:, ::-1]
                    sblk = seen[oy:oy + oh, ox:ox + ow][:, ::-1]
                    src = (block, sblk)
            if src is None:
                ox, oy, ow, oh = lay[OPP[face]]
                if (ow, oh) == (w, h) and seen[oy:oy + oh, ox:ox + ow].any():
                    src = (b.atlas.px[oy:oy + oh, ox:ox + ow][:, ::-1], seen[oy:oy + oh, ox:ox + ow][:, ::-1])
            region = b.atlas.px[y:y + h, x:x + w]
            for j in range(h):
                for i in range(w):
                    if not miss[j, i]:
                        continue
                    if src is not None and src[1][j, i]:
                        region[j, i] = src[0][j, i]
                    elif mean is not None:
                        region[j, i, :3] = mean
                        region[j, i, 3] = 255


def run(name, build, ref=None, shade=None, post=None, project=True, fit=None, frames=None, views=None, quiet=False,
        fit_kw=None, params=None, fit_params=None, steps=None, bounds=None,
        pairs=(), clip=(), final=None):
    """build() -> Builder. ``post(b)`` roda depois da projeção (simetria, detalhes)."""
    call = (lambda p: build(p)) if params is not None else (lambda p: build())
    PREVIEW.mkdir(parents=True, exist_ok=True)
    info = {}
    if ref is not None and fit_params:
        rgb, mask, _ = refscan.load_ref(REFS / ref, clip=clip)
        params, cam, iou = refscan.fit_all(call, params, fit_params, mask, steps=steps, bounds=bounds, **(fit_kw or {}))
        fit = (cam, iou)
        info["params"] = {k: round(v, 2) for k, v in params.items() if k in fit_params}
    b = call(params)
    model = b.finish()
    if ref is not None:
        rgb, mask, _ = refscan.load_ref(REFS / ref, clip=clip)
        cam, iou = fit if fit else refscan.fit_camera(model, mask, **(fit_kw or {}))
        info.update(yaw=round(cam.yaw, 1), pitch=round(cam.pitch, 1), iou=round(iou, 3))
        if project:
            seen = np.zeros(b.atlas.px.shape[:2], dtype=bool)
            n = refscan.bake(model, rgb, mask, cam, b.atlas.px, shade_k=shade, seen=seen)
            info["texels"] = n
            fill_unseen(b, seen, pairs)
        if post:
            post(b)
        model = b.finish()
        metrics = refscan.compare(model, rgb, mask, cam, PREVIEW / f"{name}_compare.png", shade_k=shade, label=name)
        info.update(metrics)
    elif post:
        post(b)
        model = b.finish()
    if final is not None:           # o modelo salvo volta à pose de repouso, mantendo a textura projetada
        fb = call({**params, **final})
        fb.atlas.px[:] = b.atlas.px
        b = fb
        model = b.finish()
    SRC.mkdir(parents=True, exist_ok=True)
    save_bbmodel(model, SRC / f"{name}.bbmodel")
    Image.fromarray(b.atlas.px, "RGBA").save(PREVIEW / f"{name}_texture.png")
    anim = None
    walk = next((a for a in model.anims if a.name == "walk"), model.anims[0] if model.anims else None)
    if walk:
        anim = bake_anim(model, walk)
    pose_frames = frames or [0, 5, 10, 15]
    sheet = contact_sheet(model, anim, pose_frames, views or ((30, 25), (-30, 25), (90, 10), (0, 10)))
    sheet.save(PREVIEW / f"{name}_sheet.png")
    if not quiet:
        print(name, info)
    return model, info
