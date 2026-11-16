"""Rotina dos modelos desenhados (``vx.design``): referências múltiplas, conciliação de medidas, lint e pranchas.

    make("crab", build, params=P, spec=REFS)                    # medidas de projeto
    make("crab", build, params=P, spec=REFS, fit=["body_w"])     # medidas conciliadas entre as referências

``build(p) -> Designer``. Saídas: ``models/src/<nome>.bbmodel``, ``models/preview/<nome>_sheet.png`` (8 vistas e
4 instantes da animação principal), ``<nome>_refs.png`` (cada referência ao lado do modelo na mesma câmera),
``<nome>_texture.png`` e ``models/analysis/<nome>.json`` (paleta, medidas, adaptações, métricas, lint).
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from vx import multiref                                   # noqa: E402
from vx.anim import bake as bake_anim                     # noqa: E402
from vx.lint import lint                                  # noqa: E402
from vx.model import save_bbmodel, to_game                # noqa: E402
from vx.render import Camera, bone_values, fit_camera_to, render, to_image  # noqa: E402

ROOT = HERE.parents[2]
SRC = ROOT / "models" / "src"
REFS = ROOT / "models" / "refs"
PREVIEW = ROOT / "models" / "preview"
ANALYSIS = ROOT / "models" / "analysis"

VIEWS = ((0, 12), (35, 25), (90, 8), (145, 25), (180, 12), (-90, 8), (-35, 25), (30, 65))


def game_pose(model, anim_name, frac):
    """Valores dos ossos no referencial do jogo (o mesmo que o plugin usa) para uma fração da animação."""
    gm = to_game(model)
    a = next(x for x in gm.anims if x.name == anim_name)
    baked = bake_anim(gm, a)
    return bone_values(gm, baked, frac * baked["frames"])


def sheet(model, anim_name=None, size=260, frames=(0, 0.25, 0.5, 0.75)):
    gm = to_game(model)
    anim = next((a for a in gm.anims if a.name == anim_name), None) if anim_name else None
    baked = bake_anim(gm, anim) if anim else None
    rows = frames if baked else (0,)
    img = Image.new("RGB", (size * len(VIEWS), size * len(rows)), (232, 234, 238))
    draw = ImageDraw.Draw(img)
    base = fit_camera_to(model, Camera(width=size, height=size), None, 0.8)
    for r, fr in enumerate(rows):
        vals = bone_values(gm, baked, fr * baked["frames"]) if baked else None
        for c, (yaw, pitch) in enumerate(VIEWS):
            cam = Camera(yaw, pitch, base.scale, size / 2, size / 2, base.center, size, size)
            img.paste(to_image(render(gm, vals, cam, already_game=True), bg=(232, 234, 238)), (c * size, r * size))
            draw.text((c * size + 5, r * size + 3), f"yaw {yaw} pitch {pitch}" + (f" {anim_name} {fr:.2f}" if baked else ""),
                      fill=(70, 70, 70))
    return img


def make(name, build, params=None, spec=None, fit=None, bounds=None, steps=None, anim="walk", folder=None, k=12,
         evaluate=True, ref_pose=None):
    """``ref_pose=(animação, fração)``: as referências mostram o mob nessa pose (boca aberta, braço erguido...)."""
    params = dict(params or {})
    PREVIEW.mkdir(parents=True, exist_ok=True)
    ANALYSIS.mkdir(parents=True, exist_ok=True)
    report = {"name": name}
    refs = []
    ref_dir = REFS / (folder or name)
    if ref_dir.is_dir():
        refs = multiref.load_set(ref_dir, spec)
        if refs:
            pal = multiref.palette(refs, k=k)
            report["palette"] = [{"rgb": d["rgb"], "frac": round(d["frac"], 3), "refs": d["refs"]} for d in pal]
    if fit and refs:
        params, rec = multiref.reconcile(build, params, fit, refs, bounds=bounds, steps=steps)
        report["reconcile"] = rec
    report["params"] = params
    b = build(params)
    model = b.finish()
    print(f"{name}: lint")
    report["lint"] = [f"[{lvl}] {msg}" for lvl, msg in lint(model)]
    SRC.mkdir(parents=True, exist_ok=True)
    save_bbmodel(model, SRC / f"{name}.bbmodel")
    Image.fromarray(b.atlas.px, "RGBA").resize((b.atlas.w * 4, b.atlas.h * 4), Image.NEAREST).save(PREVIEW / f"{name}_texture.png")
    names = [a.name for a in model.anims]
    sheet(model, anim if anim in names else (names[0] if names else None)).save(PREVIEW / f"{name}_sheet.png")
    if refs and evaluate:
        values = None
        if ref_pose:
            values = game_pose(model, *ref_pose)
        report["metrics"] = multiref.evaluate(model, refs, PREVIEW / f"{name}_refs.png", label=name, values=values)
    multiref.save_report(ANALYSIS / f"{name}.json", report)
    m = report.get("metrics", {})
    print(f"{name}: ok  " + "  ".join(f"{k}: IoU {v['iou']}" for k, v in m.items()))
    return model, report
