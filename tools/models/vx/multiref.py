"""Várias referências para um mesmo modelo: paleta de consenso, medidas conciliadas e conferência por vista.

Cada mob tem uma pasta ``models/refs/<mob>/`` com quantas imagens houver (renders 3D, pixel art, fotos de
apresentação). Cada imagem entra com um papel:

* ``shape``  - silhueta confiável (fundo liso ou transparente): mede câmera e proporções;
* ``color``  - só cores (fotos com fundo, pixel art plana, montagens): entra na paleta;
* ``both``   - as duas coisas.

**Conciliação ("adaptação")**: as referências raramente concordam (cada artista desenhou o mob num tamanho).
``reconcile`` ajusta as medidas do modelo a cada silhueta separadamente e combina os resultados pela mediana
ponderada pelo IoU; medidas em que uma referência destoa mais de 20 % da mediana são registradas como
adaptadas (o modelo segue o consenso, não a imagem isolada) e as medidas que nenhuma imagem consegue
determinar ficam no valor de projeto. O relatório diz o que veio de onde.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

from . import refscan
from .model import Model
from .render import render, to_image

EXTS = (".png", ".webp", ".jpg", ".jpeg")


@dataclass
class Ref:
    path: Path
    role: str
    rgb: np.ndarray
    mask: np.ndarray
    yaw: tuple = (-180, 180)
    pitch: tuple = (0, 70)
    note: str = ""
    cam: object = None
    iou: float = 0.0
    params: dict = field(default_factory=dict)


def _load(path: Path, crop=None, clip=(), bg_tol=18.0):
    im = Image.open(path).convert("RGBA")
    if crop:
        im = im.crop(crop)
    tmp = path.parent / f".__tmp_{path.stem}.png"
    im.save(tmp)
    try:
        rgb, mask, _ = refscan.load_ref(tmp, bg_tol=bg_tol, clip=clip)
    finally:
        tmp.unlink(missing_ok=True)
    # remove ilhas pequenas (texto, marcas d'água) e fecha furos
    n, lab, stats, _ = cv2.connectedComponentsWithStats(mask.astype(np.uint8), 8)
    if n > 2:
        keep = np.zeros(n, bool)
        big = stats[1:, cv2.CC_STAT_AREA].max()
        keep[1:] = stats[1:, cv2.CC_STAT_AREA] >= big * 0.04
        mask = keep[lab]
    return rgb, mask


def load_set(folder: Path, spec: dict | None = None) -> list[Ref]:
    """``spec``: {arquivo: {role, crop, clip, yaw, pitch, bg_tol, note}}. Arquivos sem spec entram como ``both``."""
    spec = spec or {}
    refs = []
    for p in sorted(folder.iterdir()):
        if p.suffix.lower() not in EXTS or p.name.startswith("."):
            continue
        s = spec.get(p.name, {})
        if s.get("role") == "skip":
            continue
        rgb, mask = _load(p, s.get("crop"), s.get("clip", ()), s.get("bg_tol", 18.0))
        refs.append(Ref(p, s.get("role", "both"), rgb, mask, tuple(s.get("yaw", (-180, 180))), tuple(s.get("pitch", (0, 70))),
                        s.get("note", "")))
    return refs


# ───────────────────────────── cores ─────────────────────────────

def palette(refs: list[Ref], k=12, per_ref=6000):
    """Paleta de consenso: k-means em CIELAB com o mesmo número de amostras de cada referência."""
    rng = np.random.default_rng(0)
    pools, owners = [], []
    for idx, r in enumerate(refs):
        px = r.rgb[r.mask]
        if len(px) == 0:
            continue
        sel = px[rng.choice(len(px), min(per_ref, len(px)), replace=False)]
        pools.append(sel)
        owners.append(np.full(len(sel), idx))
    px = np.concatenate(pools).astype(np.uint8).reshape(-1, 1, 3)
    own = np.concatenate(owners)
    lab = cv2.cvtColor(px, cv2.COLOR_RGB2LAB).reshape(-1, 3).astype(np.float32)
    _, labels, centers = cv2.kmeans(lab, k, None, (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 50, 0.3), 4,
                                    cv2.KMEANS_PP_CENTERS)
    labels = labels.ravel()
    rgbs = cv2.cvtColor(centers.reshape(-1, 1, 3).astype(np.uint8), cv2.COLOR_LAB2RGB).reshape(-1, 3)
    out = []
    for c in range(k):
        sel = labels == c
        support = len(set(own[sel].tolist()))       # em quantas referências a cor aparece
        out.append({"rgb": tuple(int(v) for v in rgbs[c]), "frac": float(sel.mean()), "refs": support,
                    "lab": centers[c].tolist()})
    out.sort(key=lambda d: -d["frac"])
    return out


def _lab(c):
    return cv2.cvtColor(np.uint8([[c[:3]]]), cv2.COLOR_RGB2LAB).reshape(3).astype(np.float32)


def delta_e(a, b) -> float:
    return float(np.linalg.norm(_lab(a) - _lab(b)))


def snap(color, pal, max_de=14.0):
    """Puxa uma cor de projeto para a cor medida mais próxima (se estiver perto o bastante)."""
    best = min(pal, key=lambda d: delta_e(color, d["rgb"]))
    return best["rgb"] if delta_e(color, best["rgb"]) <= max_de else tuple(color[:3])


def sample(ref: Ref, box, frac=True):
    """Cor mediana numa caixa (x0,y0,x1,y1) da referência (em fração do recorte se ``frac``)."""
    h, w = ref.mask.shape
    x0, y0, x1, y1 = box
    if frac:
        x0, x1, y0, y1 = int(x0 * w), int(x1 * w), int(y0 * h), int(y1 * h)
    reg = ref.rgb[y0:y1, x0:x1][ref.mask[y0:y1, x0:x1]]
    return tuple(int(v) for v in np.median(reg, axis=0)) if len(reg) else None


# ───────────────────────────── medidas ─────────────────────────────

def reconcile(build_fn, params: dict, names, refs: list[Ref], bounds=None, steps=None, rounds=10, work=96, verbose=True):
    """Ajusta as medidas a cada referência de forma e devolve (params de consenso, relatório)."""
    shape_refs = [r for r in refs if r.role in ("shape", "both")]
    per = []
    for r in shape_refs:
        p, cam, iou = refscan.fit_all(build_fn, dict(params), names, r.mask, steps=steps, bounds=bounds, work=work,
                                      rounds=rounds, yaw_range=r.yaw, pitch_range=r.pitch, verbose=False)
        r.cam, r.iou, r.params = cam, iou, p
        per.append((r, p, iou))
        if verbose:
            print(f"  {r.path.name}: IoU {iou:.3f} yaw {cam.yaw:.0f} pitch {cam.pitch:.0f}")
    out = dict(params)
    report = {"refs": {r.path.name: {"iou": round(iou, 3), "params": {n: round(p[n], 2) for n in names}} for r, p, iou in per},
              "adapted": [], "kept": []}
    for n in names:
        vals = [(p[n], max(iou, 1e-3)) for _, p, iou in per if iou > 0.55]
        if not vals:
            report["kept"].append(n)
            continue
        vals.sort()
        total = sum(w for _, w in vals)
        acc, med = 0.0, vals[0][0]
        for v, w in vals:
            acc += w
            if acc >= total / 2:
                med = v
                break
        out[n] = round(med * 2) / 2          # meio pixel: texels inteiros nas faces
        for r, p, iou in per:
            if abs(p[n] - med) > 0.2 * max(abs(med), 1):
                report["adapted"].append(f"{n}: {r.path.name} pede {p[n]:.1f}, consenso {med:.1f}")
    return out, report


# ───────────────────────────── conferência ─────────────────────────────

def evaluate(model: Model, refs: list[Ref], out_path: Path, label="", values=None):
    """Painel: para cada referência de forma, referência | modelo na mesma câmera | diferença. Devolve métricas."""
    from . import cvscan
    rows = []
    stats = {}
    H = 220
    for r in refs:
        if r.role not in ("shape", "both"):
            continue
        yr, pr = r.yaw, r.pitch
        if r.cam is not None:          # a conciliação já achou a vista: refina em volta dela
            yr = (int(r.cam.yaw) - 12, int(r.cam.yaw) + 12)
            pr = (max(-30, int(r.cam.pitch) - 10), int(r.cam.pitch) + 10)
        cam, iou = refscan.fit_camera(model, r.mask, values=values, yaw_range=yr, pitch_range=pr, work=100)
        hits = render(model, values, cam)
        mine = hits.cube >= 0
        img = np.asarray(to_image(hits, bg=(255, 255, 255))).astype(np.float32)
        m = cvscan.metrics(r.rgb, r.mask, img, mine)
        stats[r.path.name] = {**m, "yaw": round(cam.yaw), "pitch": round(cam.pitch)}
        ref_img = np.where(r.mask[..., None], r.rgb, 255).astype(np.uint8)
        diff = np.full(r.mask.shape + (3,), 255, np.uint8)
        diff[r.mask & ~mine] = (230, 40, 40)
        diff[~r.mask & mine] = (40, 80, 230)
        diff[r.mask & mine] = (200, 200, 200)
        tiles = [Image.fromarray(a) for a in (ref_img, img.astype(np.uint8), diff)]
        k = H / r.mask.shape[0]
        tiles = [t.resize((max(1, int(t.width * k)), H), Image.NEAREST) for t in tiles]
        row = Image.new("RGB", (sum(t.width for t in tiles) + 20, H + 16), (255, 255, 255))
        x = 0
        for t in tiles:
            row.paste(t, (x, 16))
            x += t.width + 10
        ImageDraw.Draw(row).text((4, 2), f"{r.path.name}  IoU {m['iou']}  dE {m.get('delta_e', '-')}  yaw {cam.yaw:.0f} pitch {cam.pitch:.0f}",
                                 fill=(0, 0, 0))
        rows.append(row)
    if rows:
        W = max(r.width for r in rows)
        sheet = Image.new("RGB", (W, sum(r.height for r in rows) + 18), (255, 255, 255))
        ImageDraw.Draw(sheet).text((4, 2), f"{label}: referência | modelo | silhueta (vermelho=falta, azul=sobra)", fill=(0, 0, 0))
        y = 18
        for r in rows:
            sheet.paste(r, (0, y))
            y += r.height
        sheet.save(out_path)
    return stats


def save_report(path: Path, data: dict):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
