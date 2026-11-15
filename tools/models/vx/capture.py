"""Captura de referências: tela/janela (mss), GIF/vídeo/spritesheet, URL e o .jar do cliente do jogo.

CLI: ``python tools/models/capture.py screen --window Minecraft --out models/refs/x.png``
"""
from __future__ import annotations

import ctypes
import io
import urllib.request
import zipfile
from ctypes import wintypes
from pathlib import Path

import numpy as np
from PIL import Image, ImageSequence


# ───────────────────────────── tela e janela ─────────────────────────────

def find_window(title: str):
    """Retângulo (left, top, width, height) da primeira janela visível cujo título contém ``title`` (Windows)."""
    user32 = ctypes.windll.user32
    found = []

    @ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    def enum(hwnd, _):
        if user32.IsWindowVisible(hwnd):
            n = user32.GetWindowTextLengthW(hwnd)
            buf = ctypes.create_unicode_buffer(n + 1)
            user32.GetWindowTextW(hwnd, buf, n + 1)
            if title.lower() in buf.value.lower():
                rect = wintypes.RECT()
                user32.GetWindowRect(hwnd, ctypes.byref(rect))
                found.append((rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top))
        return True

    user32.EnumWindows(enum, 0)
    return found[0] if found else None


def screen(region=None, window: str | None = None, monitor: int = 1) -> Image.Image:
    """Captura um monitor inteiro, uma região (x, y, w, h) ou a janela cujo título contém ``window``."""
    import mss
    with mss.mss() as sct:
        if window:
            rect = find_window(window)
            if rect is None:
                raise RuntimeError(f"janela não encontrada: {window}")
            region = rect
        box = ({"left": region[0], "top": region[1], "width": region[2], "height": region[3]} if region
               else sct.monitors[monitor])
        shot = sct.grab(box)
        return Image.frombytes("RGB", shot.size, shot.bgra, "raw", "BGRX")


# ───────────────────────────── sequências ─────────────────────────────

def gif_frames(path, max_frames: int = 64):
    """Quadros (RGBA) de GIF/APNG/WebP animado + duração em ms de cada um."""
    out = []
    with Image.open(path) as im:
        for fr in ImageSequence.Iterator(im):
            out.append((fr.convert("RGBA"), fr.info.get("duration", 50)))
            if len(out) >= max_frames:
                break
    return out


def video_frames(path, every: int = 1, max_frames: int = 64):
    import cv2
    cap = cv2.VideoCapture(str(path))
    fps = cap.get(cv2.CAP_PROP_FPS) or 20.0
    frames, i = [], 0
    while len(frames) < max_frames:
        ok, bgr = cap.read()
        if not ok:
            break
        if i % every == 0:
            frames.append(Image.fromarray(bgr[..., ::-1]).convert("RGBA"))
        i += 1
    cap.release()
    return frames, fps / every


def split_sheet(path, cols: int, rows: int = 1):
    """Recorta uma folha de sprites regular em quadros."""
    im = Image.open(path).convert("RGBA")
    w, h = im.width // cols, im.height // rows
    return [im.crop((c * w, r * h, (c + 1) * w, (r + 1) * h)) for r in range(rows) for c in range(cols)]


def split_by_gaps(path, min_gap: int = 6):
    """Separa várias poses lado a lado numa mesma imagem pelas colunas vazias (fundo/alfa)."""
    from .refscan import load_ref
    rgb, mask, _ = load_ref(path, crop=False)
    cols = mask.any(axis=0)
    out, start = [], None
    gap = 0
    for x, on in enumerate(list(cols) + [False] * (min_gap + 1)):
        if on:
            if start is None:
                start = x
            gap = 0
        elif start is not None:
            gap += 1
            if gap >= min_gap:
                end = x - gap + 1
                out.append(Image.open(path).convert("RGBA").crop((max(0, start - 2), 0, min(rgb.shape[1], end + 2), rgb.shape[0])))
                start = None
    return out


# ───────────────────────────── rede e jar do cliente ─────────────────────────────

def fetch(url: str, dest: str | Path | None = None) -> Image.Image:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (vertexx-ref-capture)"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = resp.read()
    im = Image.open(io.BytesIO(data))
    if dest:
        Path(dest).parent.mkdir(parents=True, exist_ok=True)
        im.save(dest)
    return im


def client_jar(version: str | None = None) -> Path | None:
    """Caminho do .jar do cliente do jogo no .minecraft (a versão mais recente se ``version`` for None)."""
    import os
    root = Path(os.environ.get("APPDATA", "")) / ".minecraft" / "versions"
    if not root.exists():
        return None
    if version:
        p = root / version / f"{version}.jar"
        return p if p.exists() else None
    jars = sorted(root.glob("*/*.jar"), key=lambda p: p.stat().st_mtime, reverse=True)
    return jars[0] if jars else None


def vanilla_asset(path: str, version: str | None = None) -> Image.Image | bytes:
    """Lê um asset do jar do cliente (ex.: ``assets/minecraft/textures/entity/cow/cow.png``): PNG vira Image.

    Serve de verdade-terreno para o layout de UV das entidades vanilla."""
    jar = client_jar(version)
    if jar is None:
        raise FileNotFoundError("jar do cliente não encontrado")
    with zipfile.ZipFile(jar) as z:
        data = z.read(path)
    return Image.open(io.BytesIO(data)).convert("RGBA") if path.endswith(".png") else data


def vanilla_list(prefix: str, version: str | None = None):
    jar = client_jar(version)
    with zipfile.ZipFile(jar) as z:
        return [n for n in z.namelist() if n.startswith(prefix)]


# ───────────────────────────── isolamento do fundo ─────────────────────────────

def isolate(im: Image.Image, rect=None, iters: int = 6) -> Image.Image:
    """Recorta o objeto do fundo com GrabCut (útil para capturas de tela com fundo irregular).

    ``rect`` = (x, y, w, h) aproximado do objeto; padrão: moldura de 4% de margem."""
    import cv2
    rgb = np.asarray(im.convert("RGB"))
    h, w = rgb.shape[:2]
    rect = rect or (int(w * 0.04), int(h * 0.04), int(w * 0.92), int(h * 0.92))
    mask = np.zeros((h, w), np.uint8)
    bgd = np.zeros((1, 65), np.float64)
    fgd = np.zeros((1, 65), np.float64)
    cv2.grabCut(rgb[..., ::-1].copy(), mask, rect, bgd, fgd, iters, cv2.GC_INIT_WITH_RECT)
    alpha = np.where((mask == 1) | (mask == 3), 255, 0).astype(np.uint8)
    out = np.dstack([rgb, alpha])
    return Image.fromarray(out, "RGBA")
