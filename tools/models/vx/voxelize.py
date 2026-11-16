"""Imagem 2D (pixel art, sprite, render de frente) → modelo ``.bbmodel`` em cuboides, com volume e ossos.

Pipeline:

1. **Pré-processamento**: remove o fundo (alfa ou cor dos cantos), corta ilhas pequenas (texto, marcas
   d'água) e reamostra para a grade de texels da arte (tamanho do "pixel" estimado por ``cvscan.texel_size``).
2. **Partes e auto-rig** (*rigging priors*): acha o eixo de simetria, separa as partes por gargalos da silhueta
   (erosão + componentes conexos + reatribuição pelo vizinho mais próximo) e classifica cada parte pela caixa
   e posição: ``head`` (alta e no eixo), ``body`` (maior no eixo), ``arm_l/arm_r`` (laterais na altura do tronco),
   ``leg_l/leg_r`` (abaixo do tronco), demais ``part_N``. O pivô vai na junta esperada (base da cabeça, topo
   interno do braço, topo da perna) e os membros são filhos do tronco.
3. **Voxelização adaptativa**: em cada parte, os texels opacos são fundidos em retângulos máximos
   (*greedy meshing*), em vez de um cubo por pixel.
4. **Profundidade**: a espessura de cada retângulo vem da distância à borda da silhueta (miolo mais grosso,
   bordas finas), da luminância (claro avança, sombra recua) e das bordas de Canny (contornos internos
   afundam). Extrusão simétrica em Z.
5. **UV e serialização**: frente com os pixels da arte, costas espelhadas, laterais/topo/base com a cor da
   borda estendida; ilhas por face com margem anti-vazamento (``build.Atlas``); salva o ``.bbmodel``.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np
from scipy import ndimage as ndi

from . import cvscan
from .design import Designer


# ───────────────────────────── 1. pré-processamento ─────────────────────────────

def to_grid(rgb: np.ndarray, mask: np.ndarray, cell: float | None = None, max_size=48):
    """Reamostra para a grade de texels: RGBA (h, w, 4) uint8 com a cor mediana de cada célula."""
    if cell is None:
        est = cvscan.texel_size(rgb, mask, max_cell=40)
        cell = est["cell"] if est.get("confidence", 0) > 0.3 else max(mask.shape) / 32
    cell = max(1.0, float(cell))
    h, w = mask.shape
    if max(h, w) / cell > max_size:
        cell = max(h, w) / max_size
    gh, gw = max(1, int(round(h / cell))), max(1, int(round(w / cell)))
    out = np.zeros((gh, gw, 4), np.uint8)
    for j in range(gh):
        for i in range(gw):
            y0, y1 = int(j * h / gh), max(int(j * h / gh) + 1, int((j + 1) * h / gh))
            x0, x1 = int(i * w / gw), max(int(i * w / gw) + 1, int((i + 1) * w / gw))
            m = mask[y0:y1, x0:x1]
            if m.mean() >= 0.5:
                out[j, i, :3] = np.median(rgb[y0:y1, x0:x1][m], axis=0)
                out[j, i, 3] = 255
    return out


# ───────────────────────────── 2. partes e auto-rig ─────────────────────────────

def symmetry_axis(alpha: np.ndarray) -> float:
    """Coluna (pode ser meia) que maximiza a sobreposição da silhueta com o próprio espelho."""
    h, w = alpha.shape
    best, axis = -1.0, w / 2
    for a2 in range(w // 2, 3 * w // 2 + 1):           # eixo = a2 / 2
        a = a2 / 2
        score = 0
        tot = 0
        for i in range(w):
            mi = int(round(2 * a - i - 1))
            if 0 <= mi < w:
                score += np.logical_and(alpha[:, i], alpha[:, mi]).sum()
                tot += np.logical_or(alpha[:, i], alpha[:, mi]).sum()
        s = score / tot if tot else 0
        if s > best:
            best, axis = s, a
    return axis


def segment(alpha: np.ndarray, erode=1, min_frac=0.02):
    """Separa partes pelos gargalos: erosão, componentes conexos e reatribuição dos texels pelo mais próximo."""
    core = ndi.binary_erosion(alpha, iterations=erode) if erode else alpha
    lab, n = ndi.label(core)
    if n == 0:
        lab, n = ndi.label(alpha)
    sizes = ndi.sum(core, lab, range(1, n + 1)) if n else []
    keep = [i + 1 for i, s in enumerate(sizes) if s >= max(2, min_frac * alpha.sum())]
    if not keep:
        return ndi.label(alpha)[0]
    seeds = np.where(np.isin(lab, keep), lab, 0)
    _, (iy, ix) = ndi.distance_transform_edt(seeds == 0, return_indices=True)
    full = seeds[iy, ix] * alpha
    # renumera 1..k
    out = np.zeros_like(full)
    for k, v in enumerate(sorted(set(full[full > 0].tolist())), 1):
        out[full == v] = k
    return out


def classify(parts: np.ndarray, axis: float):
    """Nome, pai e pivô (em coordenadas da grade) de cada parte."""
    info = {}
    for k in range(1, parts.max() + 1):
        ys, xs = np.nonzero(parts == k)
        if len(xs) == 0:
            continue
        info[k] = dict(x0=xs.min(), x1=xs.max() + 1, y0=ys.min(), y1=ys.max() + 1, area=len(xs),
                       cx=(xs.min() + xs.max() + 1) / 2, cy=(ys.min() + ys.max() + 1) / 2)
    if not info:
        return {}
    on_axis = {k: v for k, v in info.items() if v["x0"] <= axis <= v["x1"]}
    body = max(on_axis or info, key=lambda k: info[k]["area"])
    B = info[body]
    names = {body: ("body", None, ((B["x0"] + B["x1"]) / 2, B["y1"]))}
    heads = [k for k in on_axis if k != body and info[k]["cy"] < B["y0"] + 1]
    if heads:
        hd = max(heads, key=lambda k: info[k]["area"])
        H = info[hd]
        names[hd] = ("head", "body", ((H["x0"] + H["x1"]) / 2, H["y1"]))
    count = 0
    for k, v in sorted(info.items(), key=lambda kv: kv[1]["cx"]):
        if k in names:
            continue
        side = "l" if v["cx"] < axis else "r"      # esquerda da imagem = direita do mob visto de frente
        side = {"l": "r", "r": "l"}[side]
        if v["y0"] >= B["y1"] - 2 and v["cy"] > B["cy"]:
            names[k] = (f"leg_{side}", "body", (v["cx"], v["y0"]))
        elif B["y0"] - 2 <= v["cy"] <= B["y1"] and (v["x1"] <= B["x0"] + 2 or v["x0"] >= B["x1"] - 2):
            inner = v["x1"] if v["cx"] < axis else v["x0"]
            names[k] = (f"arm_{side}", "body", (inner, v["y0"] + 1))
        else:
            count += 1
            names[k] = (f"part{count}", "body", (v["cx"], v["cy"]))
    # nomes repetidos (duas "pernas direitas"): numera
    seen = {}
    for k, (n, p, piv) in list(names.items()):
        if n in seen:
            seen[n] += 1
            names[k] = (f"{n}{seen[n]}", p, piv)
        else:
            seen[n] = 0
    return names


# ───────────────────────────── 3. fusão gulosa ─────────────────────────────

def greedy_rects(mask: np.ndarray, key: np.ndarray | None = None):
    """Retângulos máximos cobrindo ``mask``; com ``key`` só funde texels de mesma chave (ex.: profundidade)."""
    m = mask.copy()
    h, w = m.shape
    rects = []
    for j in range(h):
        i = 0
        while i < w:
            if not m[j, i]:
                i += 1
                continue
            k = key[j, i] if key is not None else 0
            ww = 1
            while i + ww < w and m[j, i + ww] and (key is None or key[j, i + ww] == k):
                ww += 1
            hh = 1
            while j + hh < h and m[j + hh, i:i + ww].all() and (key is None or (key[j + hh, i:i + ww] == k).all()):
                hh += 1
            m[j:j + hh, i:i + ww] = False
            rects.append((i, j, ww, hh, k))
            i += ww
    return rects


# ───────────────────────────── 4. profundidade ─────────────────────────────

def depth_map(grid: np.ndarray, parts: np.ndarray, min_d=1, max_d=6, levels=3):
    """Espessura (texels) por texel: miolo grosso, bordas finas, claro avança, contorno interno recua."""
    alpha = grid[..., 3] > 0
    dist = np.zeros(alpha.shape, float)
    for k in range(1, parts.max() + 1):
        pm = parts == k
        if pm.any():
            d = ndi.distance_transform_edt(pm)
            dist = np.where(pm, d / max(d.max(), 1), dist)
    lum = cv2.cvtColor(grid[..., :3], cv2.COLOR_RGB2GRAY).astype(float) / 255
    lum_n = (lum - lum[alpha].mean()) / (lum[alpha].std() + 1e-6) if alpha.any() else lum
    edges = cv2.Canny(cv2.resize(grid[..., :3], None, fx=4, fy=4, interpolation=cv2.INTER_NEAREST), 60, 140)
    edges = cv2.resize(edges, (grid.shape[1], grid.shape[0]), interpolation=cv2.INTER_AREA) > 40
    raw = 0.65 * dist + 0.2 * np.clip(lum_n, -1.5, 1.5) / 1.5 - 0.15 * edges
    raw = (raw - raw[alpha].min()) / max(np.ptp(raw[alpha]), 1e-6) if alpha.any() else raw
    q = np.round(raw * (levels - 1)) / max(levels - 1, 1)                  # poucos níveis = poucos cubos
    return np.where(alpha, np.round(min_d + q * (max_d - min_d)), 0).astype(int)


# ───────────────────────────── 5. montagem ─────────────────────────────

def build(name: str, grid: np.ndarray, depth: np.ndarray, parts: np.ndarray, names: dict, axis: float, res=(64, 64)):
    h, w = grid.shape[:2]
    b = Designer(name, res=res)
    b.bone("root", origin=(0, 0, 0))

    def X(i):            # coluna da grade → x do editor (frente em -Z: esquerda da imagem = +X do editor)
        return axis - i

    def Y(j):
        return h - j

    order = sorted(names.items(), key=lambda kv: kv[1][1] is not None)
    for k, (bn, parent, (px, py)) in order:
        b.bone(bn, parent or "root", origin=(X(px), Y(py), 0))
    n = 0
    for k, (bn, _, _) in names.items():
        pm = parts == k
        for (i, j, rw, rh, d) in greedy_rects(pm, key=depth):
            n += 1
            c = b.cube(bn, f"{bn}_{n}", (X(i + rw), Y(j + rh), -d / 2), (X(i), Y(j), d / 2))
            tile = grid[j:j + rh, i:i + rw]
            for f in c.faces:
                cv = b.canvas(c, f)
                if f == "north":                              # frente: os próprios pixels
                    cv.px[:, :] = tile
                elif f == "south":
                    cv.px[:, :] = tile[:, ::-1]
                elif f in ("east", "west"):
                    col = tile[:, 0] if f == "east" else tile[:, -1]
                    cv.px[:, :] = np.repeat(col[:, None, :], cv.w, axis=1)
                elif f == "up":
                    cv.px[:, :] = np.repeat(tile[0][None, ::-1, :], cv.h, axis=0)[:, :cv.w]
                else:
                    cv.px[:, :] = np.repeat(tile[-1][None, ::-1, :], cv.h, axis=0)[:, :cv.w]
                if (cv.px[..., 3] > 0).mean() < 0.5:          # face lateral apontando para buraco: escurece levemente
                    cv.px[..., 3] = np.where(cv.px[..., 3] > 0, 255, 0)
    return b


def image_to_model(path: Path, name: str, cell=None, max_d=None, levels=3, erode=1, max_size=48, crop=None, clip=()):
    from .multiref import _load
    rgb, mask = _load(Path(path), crop, clip)
    grid = to_grid(rgb, mask, cell, max_size)
    alpha = grid[..., 3] > 0
    axis = symmetry_axis(alpha)
    parts = segment(alpha, erode)
    names = classify(parts, axis)
    max_d = max_d or max(2, int(round(grid.shape[1] * 0.4)))
    depth = depth_map(grid, parts, 1, max_d, levels)
    res = 64
    while True:
        try:
            b = build(name, grid, depth, parts, names, axis, (res, res))
            break
        except ValueError:
            res *= 2
    stats = {"grid": list(grid.shape[:2]), "axis": axis, "parts": {v[0]: int((parts == k).sum()) for k, v in names.items()},
             "cubes": len(b.cubes), "texels": int(alpha.sum())}
    return b, stats
