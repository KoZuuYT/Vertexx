"""Análise fiel de imagens de referência, pixel a pixel.

Etapas (todas só com numpy + Pillow):

``analyze``  recorta o objeto, separa o fundo, extrai paleta, regiões de cor (componentes conexos) com
             caixa/centroide, estima o tamanho do texel renderizado, e gera folhas ampliadas com grade e
             coordenadas para leitura visual.
``fit``      encontra a câmera ortográfica (yaw, pitch, escala, centro) que faz a silhueta do modelo
             coincidir com a da referência (IoU), por busca grossa + subida de encosta.
``bake``     projeta a referência sobre as faces visíveis do modelo (descontando o sombreamento por face) e
             grava nos texels da textura; texels não vistos ficam como estão.
``compare``  gera painel referência | modelo | diferença e as métricas (IoU da silhueta, erro médio de cor,
             erro por região).
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from .model import Model, to_game
from .render import Camera, FACES, SHADE_DEFAULT, pose, render, to_image


# ───────────────────────────── carregamento ─────────────────────────────

def load_ref(path, bg_tol=18.0, crop=True, clip=()):
    """-> (rgb float32 HxWx3, máscara bool HxW, caixa (x0,y0,x1,y1) do recorte na imagem original)."""
    im = Image.open(path).convert("RGBA")
    arr = np.asarray(im).astype(np.float32)
    rgb, alpha = arr[..., :3], arr[..., 3]
    if alpha.min() < 250:
        mask = alpha > 24
    else:
        # fundo opaco: cor mediana dos cantos + preenchimento a partir das bordas
        h, w = alpha.shape
        corners = np.concatenate([rgb[:6, :6].reshape(-1, 3), rgb[:6, -6:].reshape(-1, 3),
                                  rgb[-6:, :6].reshape(-1, 3), rgb[-6:, -6:].reshape(-1, 3)])
        bg = np.median(corners, axis=0)
        near = np.abs(rgb - bg).max(axis=2) < bg_tol
        mask = ~_flood_border(near)
    for (cx0, cy0, cx1, cy1) in clip:       # marcas d'água etc.: removidas da máscara
        mask[cy0:cy1, cx0:cx1] = False
    ys, xs = np.nonzero(mask)
    box = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    if crop:
        x0, y0, x1, y1 = box
        return rgb[y0:y1, x0:x1], mask[y0:y1, x0:x1], box
    return rgb, mask, box


def _flood_border(near: np.ndarray) -> np.ndarray:
    """Pixels 'iguais ao fundo' ligados à borda da imagem."""
    h, w = near.shape
    seen = np.zeros_like(near)
    stack = [(0, x) for x in range(w)] + [(h - 1, x) for x in range(w)] + [(y, 0) for y in range(h)] + \
            [(y, w - 1) for y in range(h)]
    stack = [p for p in stack if near[p]]
    for p in stack:
        seen[p] = True
    while stack:
        y, x = stack.pop()
        for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if 0 <= ny < h and 0 <= nx < w and near[ny, nx] and not seen[ny, nx]:
                seen[ny, nx] = True
                stack.append((ny, nx))
    return seen


# ───────────────────────────── análise ─────────────────────────────

def _components(label_img: np.ndarray, mask: np.ndarray, min_area: int):
    h, w = label_img.shape
    seen = np.zeros((h, w), dtype=bool)
    regions = []
    for y0 in range(h):
        for x0 in range(w):
            if seen[y0, x0] or not mask[y0, x0]:
                continue
            lab = label_img[y0, x0]
            stack = [(y0, x0)]
            seen[y0, x0] = True
            pts = []
            while stack:
                y, x = stack.pop()
                pts.append((y, x))
                for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                    if 0 <= ny < h and 0 <= nx < w and not seen[ny, nx] and mask[ny, nx] and label_img[ny, nx] == lab:
                        seen[ny, nx] = True
                        stack.append((ny, nx))
            if len(pts) >= min_area:
                regions.append((int(lab), np.array(pts)))
    return regions


def texel_estimate(rgb, mask):
    """Tamanho aproximado (px) de um texel na imagem, pelo período dominante das bordas de cor."""
    gray = rgb.mean(axis=2)
    out = {}
    for name, axis in (("x", 1), ("y", 0)):
        g = np.abs(np.diff(gray, axis=axis)) * (mask[:, 1:] & mask[:, :-1] if axis == 1 else mask[1:] & mask[:-1])
        prof = g.sum(axis=1 - axis)
        prof = prof - prof.mean()
        if len(prof) < 16:
            continue
        spec = np.abs(np.fft.rfft(prof * np.hanning(len(prof))))
        freqs = np.fft.rfftfreq(len(prof))
        valid = (freqs > 1 / 80) & (freqs < 1 / 3)
        if valid.any():
            f = freqs[valid][spec[valid].argmax()]
            out[name] = round(1 / f, 2)
    return out


def analyze(path, outdir, name=None, colors=14, grid=20, zoom=3, min_area_frac=0.002):
    name = name or Path(path).stem
    out = Path(outdir)
    out.mkdir(parents=True, exist_ok=True)
    rgb, mask, box = load_ref(path)
    h, w = mask.shape
    pic = Image.fromarray(np.where(mask[..., None], rgb, 255).astype(np.uint8))
    pic.save(out / f"{name}_crop.png")

    q = Image.fromarray(np.where(mask[..., None], rgb, 0).astype(np.uint8)).quantize(colors=colors, method=Image.MEDIANCUT,
                                                                                     dither=Image.NONE)
    labels = np.asarray(q)
    pal = np.array(q.getpalette()[: colors * 3]).reshape(-1, 3)
    total = int(mask.sum())
    report = {"image": str(path), "crop_box": box, "size": [w, h], "area_px": total,
              "texel_px": texel_estimate(rgb, mask)}
    # paleta
    entries = []
    for i, c in enumerate(pal):
        cnt = int(((labels == i) & mask).sum())
        if cnt:
            entries.append({"index": i, "hex": "#%02x%02x%02x" % tuple(int(v) for v in c), "fraction": round(cnt / total, 4)})
    entries.sort(key=lambda e: -e["fraction"])
    report["palette"] = entries
    sw = Image.new("RGB", (60 * len(entries), 70), (255, 255, 255))
    d = ImageDraw.Draw(sw)
    for k, e in enumerate(entries):
        d.rectangle([k * 60, 0, k * 60 + 59, 49], fill=e["hex"])
        d.text((k * 60 + 2, 52), f'{e["fraction"] * 100:.1f}%', fill=(0, 0, 0))
    sw.save(out / f"{name}_palette.png")
    # regiões
    regions = []
    for lab, pts in _components(labels, mask, max(8, int(total * min_area_frac))):
        ys, xs = pts[:, 0], pts[:, 1]
        regions.append({"color": "#%02x%02x%02x" % tuple(int(v) for v in pal[lab]), "area": len(pts),
                        "bbox": [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1],
                        "centroid": [round(float(xs.mean()), 1), round(float(ys.mean()), 1)]})
    regions.sort(key=lambda r: -r["area"])
    report["regions"] = regions[:80]
    # extremos da silhueta
    ys, xs = np.nonzero(mask)
    report["silhouette"] = {"left": [int(xs.min()), int(ys[xs.argmin()])], "right": [int(xs.max()), int(ys[xs.argmax()])],
                            "top": [int(xs[ys.argmin()]), int(ys.min())], "bottom": [int(xs[ys.argmax()]), int(ys.max())]}
    # perfil de largura por linha (para ler cinturas/ombros)
    prof = mask.sum(axis=1)
    report["row_width_every_10px"] = {int(y): int(prof[y]) for y in range(0, h, 10)}
    (out / f"{name}_report.json").write_text(json.dumps(report, indent=1), encoding="utf-8")

    # folhas ampliadas com grade
    scale = max(1, zoom)
    big = pic.resize((w * scale, h * scale), Image.NEAREST)
    dr = ImageDraw.Draw(big)
    for x in range(0, w, grid):
        dr.line([(x * scale, 0), (x * scale, h * scale)], fill=(255, 0, 80) if x % (grid * 5) == 0 else (0, 160, 255), width=1)
        dr.text((x * scale + 2, 2), str(x), fill=(200, 0, 60))
    for y in range(0, h, grid):
        dr.line([(0, y * scale), (w * scale, y * scale)], fill=(255, 0, 80) if y % (grid * 5) == 0 else (0, 160, 255), width=1)
        dr.text((2, y * scale + 2), str(y), fill=(200, 0, 60))
    big.save(out / f"{name}_grid.png")
    return report


# ───────────────────────────── câmera ─────────────────────────────

def _model_points(gm: Model, values=None):
    pts = []
    for cube, w in pose(gm, values):
        lo = np.array(cube.frm) - cube.inflate
        hi = np.array(cube.to) + cube.inflate
        for x in (lo[0], hi[0]):
            for y in (lo[1], hi[1]):
                for z in (lo[2], hi[2]):
                    pts.append((w @ np.array([x, y, z, 1.0]))[:3])
    return np.array(pts)


def _iou(a: np.ndarray, b: np.ndarray) -> float:
    inter = np.logical_and(a, b).sum()
    union = np.logical_or(a, b).sum()
    return inter / union if union else 0.0


def fit_camera(model: Model, mask: np.ndarray, values=None, work=110, yaw_range=(-180, 180), pitch_range=(0, 70),
               verbose=False):
    """Câmera ortográfica que melhor casa a silhueta. Devolve (Camera na resolução da máscara, IoU)."""
    gm = to_game(model)
    pts = _model_points(gm, values)
    h, w = mask.shape
    k = work / max(h, w)
    sm = np.asarray(Image.fromarray(mask.astype(np.uint8) * 255).resize((max(1, round(w * k)), max(1, round(h * k))),
                                                                      Image.BILINEAR)) > 127
    sh, sw = sm.shape
    ys, xs = np.nonzero(sm)
    mb = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    center = tuple((pts.min(axis=0) + pts.max(axis=0)) / 2)

    def make(yaw, pitch, scale, cx, cy):
        return Camera(yaw, pitch, scale, cx, cy, center, sw, sh)

    def score(cam):
        hits = render(gm, values, cam, already_game=True)
        return _iou(hits.cube >= 0, sm)

    def autoscale(yaw, pitch):
        cam = make(yaw, pitch, 1.0, sw / 2, sh / 2)
        xy = cam.project(pts)
        bx0, by0, bx1, by1 = xy[:, 0].min(), xy[:, 1].min(), xy[:, 0].max(), xy[:, 1].max()
        scale = min((mb[2] - mb[0]) / max(bx1 - bx0, 1e-6), (mb[3] - mb[1]) / max(by1 - by0, 1e-6))
        cam.scale = scale
        xy = cam.project(pts)
        cam.cx += (mb[0] + mb[2]) / 2 - (xy[:, 0].min() + xy[:, 0].max()) / 2
        cam.cy += (mb[1] + mb[3]) / 2 - (xy[:, 1].min() + xy[:, 1].max()) / 2
        return cam

    best = (-1, None)
    for yaw in range(yaw_range[0], yaw_range[1], 10):
        for pitch in range(pitch_range[0], pitch_range[1] + 1, 10):
            cam = autoscale(yaw, pitch)
            s = score(cam)
            if s > best[0]:
                best = (s, cam)
    s0, cam = best
    step = [4.0, 4.0, 0.05, 2.0, 2.0]
    for it in range(60):
        improved = False
        for p in range(5):
            for sign in (1, -1):
                vals = [cam.yaw, cam.pitch, cam.scale, cam.cx, cam.cy]
                vals[p] += sign * (step[p] * (cam.scale if p == 2 else 1))
                if not (yaw_range[0] - 3 <= vals[0] <= yaw_range[1] + 3 and pitch_range[0] - 1 <= vals[1] <= pitch_range[1] + 1):
                    continue
                cand = make(*vals)
                s = score(cand)
                if s > s0 + 1e-4:
                    s0, cam, improved = s, cand, True
        if not improved:
            step = [x * 0.5 for x in step]
            if step[0] < 0.2:
                break
    if verbose:
        print(f"  câmera: yaw={cam.yaw:.1f} pitch={cam.pitch:.1f} IoU={s0:.3f}")
    # reescala para a resolução real da máscara
    full = Camera(cam.yaw, cam.pitch, cam.scale / k, cam.cx / k, cam.cy / k, cam.center, w, h)
    return full, float(s0)


# ───────────────────────────── projeção da referência nas texturas ─────────────────────────────

def bake(model: Model, ref_rgb, mask, cam: Camera, tex: np.ndarray, tex_index=0, values=None, shade_k=None,
         erode=2, min_alpha=250, seen: np.ndarray | None = None):
    """Escreve em ``tex`` (HxWx4 uint8, in-place) a cor observada em cada texel visível. Devolve nº de texels pintados."""
    gm = to_game(model)
    k = {**SHADE_DEFAULT, **(shade_k or {})}
    hits = render(gm, values, cam, already_game=True, shade_k=k)
    h, w = mask.shape
    inner = mask.copy()
    for _ in range(erode):  # tira o pixel de borda (mistura com o fundo)
        e = inner.copy()
        e[1:] &= inner[:-1]
        e[:-1] &= inner[1:]
        e[:, 1:] &= inner[:, :-1]
        e[:, :-1] &= inner[:, 1:]
        inner = e
    cube, face = hits.cube, hits.face
    # bordas internas entre faces/cubos diferentes também são descartadas
    edge = np.zeros_like(inner)
    edge[1:] |= (cube[1:] != cube[:-1]) | (face[1:] != face[:-1])
    edge[:-1] |= (cube[1:] != cube[:-1]) | (face[1:] != face[:-1])
    edge[:, 1:] |= (cube[:, 1:] != cube[:, :-1]) | (face[:, 1:] != face[:, :-1])
    edge[:, :-1] |= (cube[:, 1:] != cube[:, :-1]) | (face[:, 1:] != face[:, :-1])
    use = inner & (cube >= 0) & ~edge
    ys, xs = np.nonzero(use)
    if not len(ys):
        return 0
    th, tw = tex.shape[:2]
    keys = {}
    rw, rh = model.res
    for y, x in zip(ys, xs):
        c, _w = hits.items[cube[y, x]]
        f = c.faces[FACES[face[y, x]]]
        if f.tex != tex_index:
            continue
        u1, v1, u2, v2 = f.uv
        tx = int(np.clip((u1 + hits.su[y, x] * (u2 - u1)) * tw / rw, 0, tw - 1))
        ty = int(np.clip((v1 + hits.sv[y, x] * (v2 - v1)) * th / rh, 0, th - 1))
        col = np.clip(ref_rgb[y, x] / max(hits.shade[y, x], 0.2), 0, 255)
        keys.setdefault((ty, tx), []).append(col)
    for (ty, tx), cols in keys.items():
        tex[ty, tx, :3] = np.median(np.array(cols), axis=0).astype(np.uint8)
        tex[ty, tx, 3] = 255
        if seen is not None:
            seen[ty, tx] = True
    return len(keys)


# ───────────────────────────── comparação ─────────────────────────────

def compare(model: Model, ref_rgb, mask, cam: Camera, out_path, values=None, shade_k=None, label=""):
    gm = to_game(model)
    hits = render(gm, values, cam, already_game=True, shade_k=shade_k)
    mine = hits.cube >= 0
    img = to_image(hits, bg=(255, 255, 255))
    ref_img = Image.fromarray(np.where(mask[..., None], ref_rgb, 255).astype(np.uint8))
    both = mask & mine
    diff = np.zeros(mask.shape + (3,), dtype=np.uint8) + 255
    err = np.abs(ref_rgb - np.asarray(img).astype(np.float32)).mean(axis=2)
    diff[both] = np.stack([np.clip(err[both] * 3, 0, 255)] * 3, axis=1).astype(np.uint8)
    diff[mask & ~mine] = (255, 40, 40)      # referência tem, modelo não (faltando)
    diff[~mask & mine] = (40, 80, 255)      # modelo tem, referência não (sobrando)
    metrics = {"iou": round(float(_iou(mask, mine)), 4), "mean_color_error": round(float(err[both].mean()) if both.any() else -1, 2),
               "missing_px": int((mask & ~mine).sum()), "extra_px": int((~mask & mine).sum())}
    h, w = mask.shape
    sheet = Image.new("RGB", (w * 3, h + 18), (255, 255, 255))
    sheet.paste(ref_img, (0, 18))
    sheet.paste(img, (w, 18))
    sheet.paste(Image.fromarray(diff), (2 * w, 18))
    ImageDraw.Draw(sheet).text((4, 3), f"{label} referência | modelo | diferença (vermelho=falta, azul=sobra)  IoU={metrics['iou']}  erro={metrics['mean_color_error']}",
                               fill=(0, 0, 0))
    sheet.save(out_path)
    return metrics


# ───────────────────────────── ajuste conjunto: câmera + medidas do modelo ─────────────────────────────

def fit_all(build_fn, params: dict, names, mask: np.ndarray, steps: dict | None = None, bounds: dict | None = None,
            work=120, rounds=14, yaw_range=(20, 80), pitch_range=(10, 50), cam: Camera | None = None, verbose=True):
    """Ajusta câmera e medidas (``names`` ⊂ ``params``) para maximizar o IoU da silhueta.

    ``build_fn(params) -> Builder``. Devolve (params, câmera na resolução da máscara, IoU)."""
    params = dict(params)
    steps = {n: (steps or {}).get(n, 1.0) for n in names}
    bounds = bounds or {}
    h, w = mask.shape
    k = work / max(h, w)
    sm = np.asarray(Image.fromarray(mask.astype(np.uint8) * 255).resize((max(1, round(w * k)), max(1, round(h * k))),
                                                                      Image.BILINEAR)) > 127
    sh, sw = sm.shape

    def model_of(p):
        return to_game(build_fn(p).finish())

    if cam is None:
        c0, _ = fit_camera(build_fn(params).finish(), mask, work=work, yaw_range=yaw_range, pitch_range=pitch_range)
        cam = c0
    lo = Camera(cam.yaw, cam.pitch, cam.scale * k, cam.cx * k, cam.cy * k, cam.center, sw, sh)

    def score(p, c):
        gm = model_of(p)
        hits = render(gm, None, c, already_game=True)
        return _iou(hits.cube >= 0, sm)

    best = score(params, lo)
    cstep = [3.0, 3.0, 0.04, 1.5, 1.5]
    for r in range(rounds):
        improved = False
        for n in names:
            for sign in (1, -1):
                cand = dict(params)
                cand[n] = params[n] + sign * steps[n]
                lo_b, hi_b = bounds.get(n, (-1e9, 1e9))
                if not (lo_b <= cand[n] <= hi_b):
                    continue
                s = score(cand, lo)
                if s > best + 1e-4:
                    best, params, improved = s, cand, True
        for p in range(5):
            for sign in (1, -1):
                vals = [lo.yaw, lo.pitch, lo.scale, lo.cx, lo.cy]
                vals[p] += sign * cstep[p] * (lo.scale if p == 2 else 1)
                if not (yaw_range[0] - 3 <= vals[0] <= yaw_range[1] + 3 and pitch_range[0] - 1 <= vals[1] <= pitch_range[1] + 1):
                    continue
                c = Camera(vals[0], vals[1], vals[2], vals[3], vals[4], lo.center, sw, sh)
                s = score(params, c)
                if s > best + 1e-4:
                    best, lo, improved = s, c, True
        if verbose:
            print(f"  rodada {r + 1}: IoU={best:.3f} yaw={lo.yaw:.1f} pitch={lo.pitch:.1f}")
        if not improved:
            steps = {n: v * 0.5 for n, v in steps.items()}
            cstep = [v * 0.5 for v in cstep]
            if max(steps.values(), default=0) < 0.12:
                break
    full = Camera(lo.yaw, lo.pitch, lo.scale / k, lo.cx / k, lo.cy / k, lo.center, w, h)
    return params, full, float(best)
