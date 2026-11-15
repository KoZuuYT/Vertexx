"""Análise de imagens com OpenCV / scikit-image / SciPy, complementar a ``refscan.py``.

Três coisas que o ajuste só por silhueta não dá:

* ``texel_size``: tamanho do "pixel" da arte (a referência foi ampliada?) por periodicidade das bordas;
* ``edge_directions`` + ``estimate_view``: direções das arestas (Canny + Hough) → yaw/pitch da câmera por
  geometria, independentemente da silhueta; serve de semente e de conferência do ajuste;
* ``metrics``: IoU, distância de chanfro das bordas, F1 de arestas, SSIM e ΔE (CIELAB) entre a referência e o
  modelo renderizado.
"""
from __future__ import annotations

import math

import cv2
import numpy as np
from scipy import ndimage as ndi
from skimage.metrics import structural_similarity

from .render import Camera


def _gray(rgb: np.ndarray) -> np.ndarray:
    return cv2.cvtColor(np.clip(rgb, 0, 255).astype(np.uint8), cv2.COLOR_RGB2GRAY)


# ───────────────────────────── tamanho do texel ─────────────────────────────

def texel_size(rgb: np.ndarray, mask: np.ndarray, max_cell: float = 24.0) -> dict:
    """Estima o lado (em pixels da imagem) de cada "pixel de arte". ``confidence`` ≈ 1 quando as bordas caem
    todas em uma grade regular; perto de 0 em imagens suavizadas/em perspectiva."""
    g = _gray(rgb).astype(np.float32)
    m = mask.astype(np.float32)
    dx = (np.abs(np.diff(g, axis=1)) * (m[:, 1:] * m[:, :-1])).sum(axis=0)
    dy = (np.abs(np.diff(g, axis=0)) * (m[1:] * m[:-1])).sum(axis=1)
    total = dx.sum() + dy.sum()
    if total <= 0:
        return {"cell": 1.0, "confidence": 0.0}
    cells = np.arange(2.0, max_cell, 0.25)
    contrast = np.zeros(len(cells))
    for i, s in enumerate(cells):
        best = 0.0
        for phase in np.linspace(0, s, 10, endpoint=False):
            frac = 0.0
            for sig in (dx, dy):
                idx = np.round(phase + np.arange(1, len(sig) / s + 1) * s - 1).astype(int)
                idx = idx[(idx >= 0) & (idx < len(sig))]
                frac += sig[idx].sum()
            best = max(best, frac / total * s)
        contrast[i] = best
    top = contrast.max()
    # o menor período que ainda explica quase toda a energia (múltiplos também casam)
    pick = next(i for i, c in enumerate(contrast) if c >= 0.9 * top)
    return {"cell": float(cells[pick]), "confidence": float(min(1.0, max(0.0, (top - 1.0) / 3.0))), "contrast": float(top)}


# ───────────────────────────── direções das arestas ─────────────────────────────

def edge_directions(rgb: np.ndarray, mask: np.ndarray, min_len: float = 0.05, bins: int = 180):
    """Histograma de ângulos de segmentos de reta (graus, 0..180, anti-horário a partir do eixo x da imagem)
    ponderado pelo comprimento, mais os picos. Devolve (hist, picos[(ângulo, peso)])."""
    h, w = mask.shape
    gray = cv2.GaussianBlur(_gray(rgb), (3, 3), 0)
    edges = cv2.Canny(gray, 40, 110)
    sil = cv2.Canny(mask.astype(np.uint8) * 255, 50, 150)
    edges = cv2.bitwise_or(edges, sil)
    lines = cv2.HoughLinesP(edges, 1, np.pi / 360, threshold=14, minLineLength=max(6, int(min_len * max(h, w))), maxLineGap=3)
    hist = np.zeros(bins)
    if lines is not None:
        for x1, y1, x2, y2 in np.asarray(lines).reshape(-1, 4):
            ang = math.degrees(math.atan2(-(y2 - y1), x2 - x1)) % 180
            hist[int(ang * bins / 180) % bins] += math.hypot(x2 - x1, y2 - y1)
    smooth = ndi.gaussian_filter1d(hist, 1.2, mode="wrap")
    peaks = []
    for i in range(bins):
        v = smooth[i]
        if v > 0.15 * smooth.max() and v >= smooth[i - 1] and v >= smooth[(i + 1) % bins]:
            peaks.append((i * 180 / bins, float(v)))
    peaks.sort(key=lambda p: -p[1])
    return smooth, peaks[:8]


def _axis_angles(yaw: float, pitch: float):
    cam = Camera(yaw, pitch)
    _, r, u = cam.basis()
    out = []
    for a in (np.array([1.0, 0, 0]), np.array([0, 1.0, 0]), np.array([0, 0, 1.0])):
        out.append(math.degrees(math.atan2(a @ u, a @ r)) % 180)
    return out


def estimate_view(peaks, yaw_range=(0, 90), pitch_range=(0, 80), top=3):
    """Câmeras (yaw, pitch) cujas direções de eixo explicam melhor os picos medidos. Devolve [(yaw, pitch, custo)]."""
    if not peaks:
        return []
    results = []
    for yaw in np.arange(yaw_range[0], yaw_range[1] + 0.1, 1.0):
        for pitch in np.arange(pitch_range[0], pitch_range[1] + 0.1, 1.0):
            pred = _axis_angles(float(yaw), float(pitch))
            cost = 0.0
            for ang, weight in peaks:
                d = min(min(abs(ang - p), 180 - abs(ang - p)) for p in pred)
                cost += weight * min(d, 25.0)
            results.append((float(yaw), float(pitch), cost / sum(w for _, w in peaks)))
    results.sort(key=lambda r: r[2])
    out = []
    for r in results:
        if all(abs(r[0] - o[0]) > 6 or abs(r[1] - o[1]) > 6 for o in out):
            out.append(r)
        if len(out) >= top:
            break
    return out


# ───────────────────────────── métricas ─────────────────────────────

def _boundary(mask: np.ndarray) -> np.ndarray:
    m = mask.astype(np.uint8)
    return (m - cv2.erode(m, np.ones((3, 3), np.uint8))).astype(bool)


def chamfer(a: np.ndarray, b: np.ndarray) -> float:
    """Distância média (px) entre os contornos de duas máscaras, simétrica."""
    ba, bb = _boundary(a), _boundary(b)
    if not ba.any() or not bb.any():
        return float(max(a.shape))
    da = ndi.distance_transform_edt(~ba)
    db = ndi.distance_transform_edt(~bb)
    return float((da[bb].mean() + db[ba].mean()) / 2)


def metrics(ref_rgb: np.ndarray, ref_mask: np.ndarray, img_rgb: np.ndarray, mine_mask: np.ndarray) -> dict:
    """Comparação completa referência × modelo (mesma resolução)."""
    inter = np.logical_and(ref_mask, mine_mask).sum()
    union = np.logical_or(ref_mask, mine_mask).sum()
    out = {"iou": round(float(inter / union) if union else 0.0, 4), "chamfer_px": round(chamfer(ref_mask, mine_mask), 2)}
    both = ref_mask & mine_mask
    if both.sum() > 16:
        lab_a = cv2.cvtColor(np.clip(ref_rgb, 0, 255).astype(np.uint8), cv2.COLOR_RGB2LAB).astype(np.float32)
        lab_b = cv2.cvtColor(np.clip(img_rgb, 0, 255).astype(np.uint8), cv2.COLOR_RGB2LAB).astype(np.float32)
        de = np.sqrt(((lab_a - lab_b) ** 2).sum(axis=2))
        out["delta_e"] = round(float(de[both].mean()), 2)
        ys, xs = np.nonzero(ref_mask | mine_mask)
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        ga, gb = _gray(ref_rgb)[y0:y1, x0:x1], _gray(img_rgb)[y0:y1, x0:x1]
        if min(ga.shape) >= 7:
            out["ssim"] = round(float(structural_similarity(ga, gb)), 4)
    ea = cv2.Canny(_gray(ref_rgb), 40, 110).astype(bool) & ref_mask
    eb = cv2.Canny(_gray(img_rgb), 40, 110).astype(bool) & mine_mask
    if ea.any() and eb.any():
        near_b = ndi.distance_transform_edt(~eb) <= 2
        near_a = ndi.distance_transform_edt(~ea) <= 2
        p = float(near_a[eb].mean())
        r = float(near_b[ea].mean())
        out["edge_f1"] = round(2 * p * r / (p + r), 3) if p + r else 0.0
    return out


# ───────────────────────────── cores e formas ─────────────────────────────

def palette_kmeans(rgb: np.ndarray, mask: np.ndarray, k: int = 8):
    """Paleta por k-means em CIELAB: [(rgb, fração)] da mais para a menos frequente."""
    px = rgb[mask].astype(np.uint8).reshape(-1, 1, 3)
    if len(px) < k:
        return []
    lab = cv2.cvtColor(px, cv2.COLOR_RGB2LAB).reshape(-1, 3).astype(np.float32)
    if len(lab) > 40000:
        lab = lab[np.random.default_rng(0).choice(len(lab), 40000, replace=False)]
    _, labels, centers = cv2.kmeans(lab, k, None, (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 40, 0.5), 3,
                                    cv2.KMEANS_PP_CENTERS)
    counts = np.bincount(labels.ravel(), minlength=k) / len(labels)
    rgbs = cv2.cvtColor(centers.reshape(-1, 1, 3).astype(np.uint8), cv2.COLOR_LAB2RGB).reshape(-1, 3)
    order = np.argsort(-counts)
    return [(tuple(int(v) for v in rgbs[i]), float(counts[i])) for i in order]


def polygons(mask: np.ndarray, epsilon: float = 0.012):
    """Contornos simplificados da silhueta (cv2.approxPolyDP): lista de arrays Nx2."""
    cnts, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    out = []
    for c in cnts:
        if cv2.contourArea(c) < 12:
            continue
        out.append(cv2.approxPolyDP(c, epsilon * cv2.arcLength(c, True), True).reshape(-1, 2))
    return out


def inner_edges(rgb: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """Mapa binário de arestas internas (Canny) dentro da silhueta, sem o contorno."""
    e = cv2.Canny(cv2.GaussianBlur(_gray(rgb), (3, 3), 0), 40, 110).astype(bool)
    inner = cv2.erode(mask.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool)
    return e & inner
