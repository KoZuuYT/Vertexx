#!/usr/bin/env python3
"""Gera o resource pack do Vertexx (texturas, fontes, modelos, sons) e o compacta em um .zip.

Uso:
    python tools/pack/build.py            # gera resourcepack/ e libs/Vertexx-Pack.zip

Dependências: Pillow, numpy, soundfile (com suporte a OGG/Vorbis).
O zip é determinístico (timestamps fixos), então o SHA-1 só muda se o conteúdo mudar.
"""
from __future__ import annotations

import json
import math
import shutil
import sys
import zipfile
from pathlib import Path

import numpy as np
import soundfile as sf
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / "resourcepack"
NS = PACK / "assets" / "vertexx"
OUT_ZIP = ROOT / "libs" / "Vertexx-Pack.zip"
SR = 44100

KINGDOMS = {
    "enternais": dict(dark=(30, 14, 84), mid=(88, 48, 210), light=(196, 167, 255), accent=(124, 77, 255)),
    "aurudos": dict(dark=(92, 48, 0), mid=(217, 130, 0), light=(255, 224, 130), accent=(255, 143, 0)),
    "cafetoes": dict(dark=(66, 8, 8), mid=(183, 28, 28), light=(255, 138, 128), accent=(229, 57, 53)),
}
GOLD = (255, 214, 102)


# ═══════════════════════════ utilidades de imagem ═══════════════════════════

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def save(img: Image.Image, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)


def glow(layer: Image.Image, radius: float, strength: float = 1.0) -> Image.Image:
    blurred = layer.filter(ImageFilter.GaussianBlur(radius))
    if strength != 1.0:
        r, g, b, a = blurred.split()
        a = a.point(lambda v: min(255, int(v * strength)))
        blurred = Image.merge("RGBA", (r, g, b, a))
    return blurred


# ── emblemas (desenhados em 4x e reduzidos) ─────────────────────────────────

def emblem_enternais(d: ImageDraw.ImageDraw, c: int, s: float, col, hi):
    """Estrela de quatro pontas com facetas de cristal e órbita."""
    def star(r_out, r_in, fill):
        pts = []
        for i in range(8):
            ang = math.pi / 2 * (i / 2) - math.pi / 2
            r = r_out if i % 2 == 0 else r_in
            pts.append((c + math.cos(ang) * r * s, c + math.sin(ang) * r * s))
        d.polygon(pts, fill=fill)
    star(0.44, 0.10, col)
    star(0.34, 0.07, hi)
    star(0.20, 0.04, (255, 255, 255, 255))
    for k in range(4):  # facetas
        ang = math.pi / 2 * k
        d.line([(c, c), (c + math.cos(ang) * 0.44 * s, c + math.sin(ang) * 0.44 * s)], fill=col, width=int(0.012 * s))
    d.ellipse([c - 0.36 * s, c - 0.36 * s, c + 0.36 * s, c + 0.36 * s], outline=hi, width=int(0.014 * s))
    for k in range(4):
        ang = math.pi / 4 + math.pi / 2 * k
        x, y = c + math.cos(ang) * 0.36 * s, c + math.sin(ang) * 0.36 * s
        d.ellipse([x - 0.028 * s, y - 0.028 * s, x + 0.028 * s, y + 0.028 * s], fill=(255, 255, 255, 255))


def emblem_aurudos(d: ImageDraw.ImageDraw, c: int, s: float, col, hi):
    """Coroa de cinco pontas com joias."""
    def P(x, y):
        return (c + x * s, c + y * s)
    crown = [P(-0.40, 0.22), P(-0.44, -0.22), P(-0.22, -0.02), P(-0.12, -0.30), P(0.0, -0.04),
             P(0.12, -0.30), P(0.22, -0.02), P(0.44, -0.22), P(0.40, 0.22)]
    d.polygon(crown, fill=col)
    inner = [P(-0.34, 0.18), P(-0.37, -0.12), P(-0.20, 0.02), P(-0.11, -0.22), P(0.0, 0.02),
             P(0.11, -0.22), P(0.20, 0.02), P(0.37, -0.12), P(0.34, 0.18)]
    d.polygon(inner, fill=hi)
    d.rounded_rectangle([c - 0.42 * s, c + 0.22 * s, c + 0.42 * s, c + 0.34 * s], radius=int(0.03 * s), fill=col)
    d.rounded_rectangle([c - 0.38 * s, c + 0.25 * s, c + 0.38 * s, c + 0.31 * s], radius=int(0.02 * s), fill=hi)
    for x, y, r in [(-0.44, -0.24, 0.045), (-0.12, -0.32, 0.05), (0.12, -0.32, 0.05), (0.44, -0.24, 0.045), (0.0, -0.06, 0.04)]:
        d.ellipse([c + (x - r) * s, c + (y - r) * s, c + (x + r) * s, c + (y + r) * s], fill=(255, 255, 255, 255))
    for x in (-0.22, 0.0, 0.22):
        d.ellipse([c + (x - 0.03) * s, c + 0.23 * s, c + (x + 0.03) * s, c + 0.29 * s], fill=(200, 40, 60, 255))


def emblem_cafetoes(d: ImageDraw.ImageDraw, c: int, s: float, col, hi):
    """Espadas (naipe) dentro de um anel."""
    def P(x, y):
        return (c + x * s, c + y * s)
    d.ellipse([c - 0.46 * s, c - 0.46 * s, c + 0.46 * s, c + 0.46 * s], outline=col, width=int(0.02 * s))
    for fill, k in ((col, 1.0), (hi, 0.78)):
        r = 0.17 * k
        for x in (-0.19 * k, 0.19 * k):
            d.ellipse([c + (x - r) * s, c + (0.06 * k + 0.02 - r) * s, c + (x + r) * s, c + (0.06 * k + 0.02 + r) * s], fill=fill)
        d.polygon([P(0, -0.36 * k), P(-0.35 * k, 0.04 * k), P(-0.17 * k, 0.16 * k), P(0, 0.06 * k),
                   P(0.17 * k, 0.16 * k), P(0.35 * k, 0.04 * k)], fill=fill)
        d.polygon([P(-0.035 * k, 0.12 * k), P(0.035 * k, 0.12 * k), P(0.10 * k, 0.36 * k), P(-0.10 * k, 0.36 * k)], fill=fill)
    d.ellipse([c - 0.03 * s, c - 0.18 * s, c + 0.03 * s, c - 0.12 * s], fill=(255, 255, 255, 255))


EMBLEMS = {"enternais": emblem_enternais, "aurudos": emblem_aurudos, "cafetoes": emblem_cafetoes}


def render_emblem(name: str, size: int, with_glow: bool = True) -> Image.Image:
    k = KINGDOMS[name]
    big = size * 4
    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    EMBLEMS[name](d, big // 2, big, k["accent"] + (255,), k["light"] + (255,))
    img = layer.resize((size, size), Image.LANCZOS)
    if with_glow:
        halo = glow(img, size * 0.05, 1.6)
        img = Image.alpha_composite(halo, img)
    return img


# ── bandeirão ───────────────────────────────────────────────────────────────

def banner_texture(name: str) -> Image.Image:
    k = KINGDOMS[name]
    w, h = 256, 512
    rng = np.random.default_rng({"enternais": 1, "aurudos": 2, "cafetoes": 3}[name])
    ys = np.linspace(0, 1, h)[:, None]
    xs = np.linspace(-1, 1, w)[None, :]
    base = np.zeros((h, w, 3), dtype=np.float32)
    for ch in range(3):
        top = k["mid"][ch] * 1.15
        bot = k["dark"][ch]
        base[:, :, ch] = top + (bot - top) * (ys ** 1.2)
    base *= (1.0 - 0.22 * (xs ** 2))[:, :, None]  # vinheta lateral
    weave = (np.sin(np.arange(w)[None, :] * 1.9) * np.sin(np.arange(h)[:, None] * 1.9) * 3.0)[:, :, None]
    noise = rng.normal(0, 2.2, (h, w, 1))
    base = np.clip(base + weave + noise, 0, 255).astype(np.uint8)
    img = Image.fromarray(base, "RGB").convert("RGBA")
    d = ImageDraw.Draw(img)

    # moldura dourada dupla
    for inset, width, col in ((10, 7, GOLD), (24, 3, lerp(GOLD, k["light"], 0.4))):
        d.rectangle([inset, inset, w - inset - 1, h - inset - 1], outline=col + (255,), width=width)
    # cantos ornamentais
    for cx, cy in ((24, 24), (w - 24, 24)):
        d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=GOLD + (255,))
        d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=k["dark"] + (255,))

    # emblema central com halo
    emb = render_emblem(name, 190)
    img.alpha_composite(emb, ((w - 190) // 2, 120))
    # divisória
    y = 345
    d.line([(46, y), (w - 46, y)], fill=GOLD + (255,), width=3)
    d.polygon([(w // 2, y - 12), (w // 2 + 12, y), (w // 2, y + 12), (w // 2 - 12, y)], fill=GOLD + (255,))
    # padrão de losangos ao pé do estandarte
    for i in range(5):
        cx = 58 + i * 35
        d.polygon([(cx, 380), (cx + 13, 396), (cx, 412), (cx - 13, 396)], fill=lerp(k["mid"], k["light"], 0.25) + (255,),
                  outline=GOLD + (255,))
    # corte em "cauda de andorinha" na base (transparente)
    notch = Image.new("L", (w, h), 255)
    nd = ImageDraw.Draw(notch)
    nd.polygon([(w // 2 - 70, h), (w // 2, h - 92), (w // 2 + 70, h)], fill=0)
    r, g, b, a = img.split()
    img = Image.merge("RGBA", (r, g, b, ImageChops.multiply(a, notch)))
    return img


def trim_texture() -> Image.Image:
    img = Image.new("RGBA", (32, 32))
    px = img.load()
    for y in range(32):
        for x in range(32):
            t = 0.5 + 0.5 * math.sin(x * 0.4 + y * 0.2)
            base = lerp((176, 120, 20), (255, 226, 130), (y / 31) * 0.5 + t * 0.35)
            if x in (0, 31) or y in (0, 31):
                base = lerp(base, (110, 70, 8), 0.5)
            px[x, y] = base + (255,)
    return img


def selector_texture() -> Image.Image:
    s = 256
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # cabo diagonal
    d.line([(44, 212), (150, 108)], fill=(86, 52, 22, 255), width=26)
    d.line([(44, 212), (150, 108)], fill=(150, 98, 44, 255), width=14)
    d.line([(60, 196), (80, 176)], fill=GOLD + (255,), width=30)
    d.line([(100, 156), (118, 138)], fill=GOLD + (255,), width=26)
    # garras e joia
    cx, cy = 176, 82
    d.polygon([(cx - 52, cy + 40), (cx - 34, cy - 4), (cx - 14, cy + 34)], fill=GOLD + (255,))
    d.polygon([(cx + 52, cy - 40), (cx + 4, cy - 34), (cx + 34, cy - 14)], fill=GOLD + (255,))
    gem = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gem)
    gd.polygon([(cx, cy - 50), (cx + 38, cy), (cx, cy + 50), (cx - 38, cy)], fill=(160, 110, 255, 255))
    gd.polygon([(cx, cy - 50), (cx + 38, cy), (cx, cy)], fill=(210, 190, 255, 255))
    gd.polygon([(cx, cy + 50), (cx - 38, cy), (cx, cy)], fill=(96, 56, 200, 255))
    gd.polygon([(cx - 8, cy - 22), (cx + 6, cy - 30), (cx + 2, cy - 14)], fill=(255, 255, 255, 255))
    img = Image.alpha_composite(glow(gem, 10, 1.3), img)
    img.alpha_composite(gem)
    return img


def key_icon(kind: str) -> Image.Image:
    s = 128
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if kind in ("lmb", "rmb"):
        body = (236, 236, 244, 255)
        edge = (40, 40, 56, 255)
        d.rounded_rectangle([34, 10, 94, 118], radius=30, fill=body, outline=edge, width=5)
        d.line([(34, 58), (94, 58)], fill=edge, width=4)
        d.line([(64, 10), (64, 58)], fill=edge, width=4)
        hi = (255, 186, 56, 255)
        if kind == "lmb":
            d.pieslice([34, 10, 98, 74], 180, 270, fill=hi, outline=edge, width=4)
            d.rectangle([37, 12, 62, 56], fill=hi)
        else:
            d.pieslice([30, 10, 94, 74], 270, 360, fill=hi, outline=edge, width=4)
            d.rectangle([66, 12, 91, 56], fill=hi)
        d.rounded_rectangle([34, 10, 94, 118], radius=30, outline=edge, width=5)
    else:  # SHIFT
        edge = (40, 40, 56, 255)
        d.rounded_rectangle([6, 22, 122, 106], radius=14, fill=(236, 236, 244, 255), outline=edge, width=5)
        d.polygon([(64, 36), (92, 64), (76, 64), (76, 88), (52, 88), (52, 64), (36, 64)], fill=(255, 186, 56, 255), outline=edge)
    return img


BANNERS = {  # nome: (cor principal, cor clara, símbolo); ordem = U+E030 + índice (ver Cinematics.Banner)
    "level": ((255, 190, 40), (255, 240, 170), "star"),
    "skill": ((60, 180, 240), (190, 240, 255), "gem"),
    "elite": ((220, 40, 120), (255, 170, 210), "crown"),
    "forge": ((255, 140, 20), (255, 215, 150), "anvil"),
    "stat": ((44, 182, 125), (170, 255, 215), "chevrons"),
}


def banner_image(name: str) -> Image.Image:
    """Faixa de título 3:1: lâminas de luz saindo de um emblema central com brilho."""
    main, light, symbol = BANNERS[name]
    W, H = 384, 128
    cx, cy = W // 2, H // 2
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for sign in (-1, 1):
        for width, length, col, alpha in ((16, 186, main, 190), (8, 150, light, 235), (3, 120, (255, 255, 255), 255)):
            pts = [(cx + sign * 34, cy - width), (cx + sign * (34 + length), cy), (cx + sign * 34, cy + width)]
            d.polygon(pts, fill=col + (alpha,))
        for dy in (-30, 30):  # filetes finos acima e abaixo
            d.line([(cx + sign * 60, cy + dy), (cx + sign * 150, cy + dy * 0.9)], fill=light + (170,), width=2)
            d.ellipse([cx + sign * 150 - 4, cy + dy * 0.9 - 4, cx + sign * 150 + 4, cy + dy * 0.9 + 4],
                      fill=GOLD + (255,))
    d.ellipse([cx - 40, cy - 40, cx + 40, cy + 40], fill=(16, 10, 30, 235), outline=GOLD + (255,), width=4)
    d.ellipse([cx - 33, cy - 33, cx + 33, cy + 33], outline=main + (255,), width=3)
    sym = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sym)
    if symbol == "star":
        pts = []
        for i in range(10):
            ang = math.pi / 5 * i - math.pi / 2
            r = 27 if i % 2 == 0 else 11
            pts.append((cx + math.cos(ang) * r, cy + math.sin(ang) * r))
        sd.polygon(pts, fill=light + (255,), outline=main + (255,))
    elif symbol == "gem":
        sd.polygon([(cx, cy - 28), (cx + 22, cy - 4), (cx, cy + 28), (cx - 22, cy - 4)],
                   fill=light + (255,), outline=main + (255,))
        sd.polygon([(cx, cy - 28), (cx + 22, cy - 4), (cx, cy - 4)], fill=(255, 255, 255, 255))
    elif symbol == "crown":
        sd.polygon([(cx - 24, cy + 14), (cx - 28, cy - 14), (cx - 12, cy), (cx, cy - 22), (cx + 12, cy),
                    (cx + 28, cy - 14), (cx + 24, cy + 14)], fill=light + (255,), outline=main + (255,))
    elif symbol == "anvil":
        sd.polygon([(cx - 28, cy - 14), (cx + 26, cy - 14), (cx + 16, cy), (cx + 8, cy + 4), (cx + 14, cy + 18),
                    (cx - 14, cy + 18), (cx - 8, cy + 4), (cx - 16, cy)], fill=light + (255,), outline=main + (255,))
    else:  # chevrons
        for k in range(3):
            y = cy + 14 - k * 14
            sd.line([(cx - 20, y), (cx, y - 14), (cx + 20, y)], fill=light + (255,), width=6)
    layer.alpha_composite(sym)
    return Image.alpha_composite(glow(layer, 10, 1.4), layer)


def overlay_solid() -> Image.Image:
    return Image.new("RGBA", (64, 32), (255, 255, 255, 255))


# ═══════════════════════════ fonte, modelos, itens ═══════════════════════════

def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def build_assets():
    tex = NS / "textures"
    names = list(KINGDOMS)

    # texturas
    for name in names:
        save(banner_texture(name), tex / "item" / f"banner_{name}.png")
        save(render_emblem(name, 128), tex / "font" / f"emblem_{name}.png")
    save(trim_texture(), tex / "item" / "banner_trim.png")
    save(selector_texture(), tex / "item" / "selector.png")
    for kind in ("lmb", "rmb", "shift"):
        save(key_icon(kind), tex / "font" / f"key_{kind}.png")
    save(overlay_solid(), tex / "font" / "solid.png")
    for name in BANNERS:
        save(banner_image(name), tex / "font" / f"banner_{name}.png")

    # fonte: sobreposições de tela cheia, emblemas e ícones de teclas
    providers = [
        # 64x32 de pixels esticados para 1024x512 unidades: cobre a tela em qualquer escala de GUI
        {"type": "bitmap", "file": "vertexx:font/solid.png", "ascent": 253, "height": 512, "chars": [""]},
    ]
    for i, name in enumerate(names):
        providers.append({"type": "bitmap", "file": f"vertexx:font/emblem_{name}.png", "ascent": 22,
                          "height": 22, "chars": [chr(0xE010 + i)]})
    for i, kind in enumerate(("lmb", "rmb", "shift")):
        providers.append({"type": "bitmap", "file": f"vertexx:font/key_{kind}.png", "ascent": 8,
                          "height": 10, "chars": [chr(0xE020 + i)]})
    for i, name in enumerate(BANNERS):
        providers.append({"type": "bitmap", "file": f"vertexx:font/banner_{name}.png", "ascent": 30,
                          "height": 36, "chars": [chr(0xE030 + i)]})
    providers.append({"type": "space", "advances": {"": 4, "": 12, "": -4}})
    write_json(NS / "font" / "cinematic.json", {"providers": providers})

    # modelos dos bandeirões: pano + barra superior + ponteiras douradas
    def face(tex_ref, uv=(0, 0, 16, 16)):
        return {"uv": list(uv), "texture": tex_ref}

    for name in names:
        model = {
            "textures": {"cloth": f"vertexx:item/banner_{name}", "trim": "vertexx:item/banner_trim",
                         "particle": "vertexx:item/banner_trim"},
            "elements": [
                {"from": [1, 1, 7.5], "to": [15, 29, 8.5], "faces": {
                    "north": face("#cloth"), "south": face("#cloth"),
                    "east": face("#trim", (0, 0, 1, 16)), "west": face("#trim", (0, 0, 1, 16))}},
                {"from": [-1, 29, 7], "to": [17, 31, 9], "faces": {
                    s: face("#trim", (0, 0, 16, 4)) for s in ("north", "south", "east", "west", "up", "down")}},
                {"from": [-2.5, 28, 6.5], "to": [-0.5, 32, 9.5], "faces": {
                    s: face("#trim", (0, 0, 8, 8)) for s in ("north", "south", "east", "west", "up", "down")}},
                {"from": [16.5, 28, 6.5], "to": [18.5, 32, 9.5], "faces": {
                    s: face("#trim", (0, 0, 8, 8)) for s in ("north", "south", "east", "west", "up", "down")}},
            ],
            "display": {"gui": {"rotation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]}},
        }
        write_json(NS / "models" / "item" / f"banner_{name}.json", model)
        write_json(NS / "items" / f"banner_{name}.json",
                   {"model": {"type": "minecraft:model", "model": f"vertexx:item/banner_{name}"}})

    write_json(NS / "models" / "item" / "selector.json",
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "vertexx:item/selector"}})
    write_json(NS / "items" / "selector.json",
               {"model": {"type": "minecraft:model", "model": "vertexx:item/selector"}})

    # menus: fundos (glifos), ícones de interface, item invisível e emblemas
    import menus
    menus.build(NS, KINGDOMS, render_emblem, save, write_json)

    # pack.mcmeta (formato com versão maior/menor do 26.x)
    write_json(PACK / "pack.mcmeta", {"pack": {
        "description": "§dVertexx §7— menus, status e cinemáticas",
        "min_format": [97, 0], "max_format": [99, 0]}})
    pack_png = render_emblem("enternais", 128)
    bg = Image.new("RGBA", (128, 128), (20, 12, 48, 255))
    bg.alpha_composite(pack_png)
    save(bg, PACK / "pack.png")


# ═══════════════════════════ síntese de áudio ═══════════════════════════

def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def env_adsr(n, a, d, s, r):
    e = np.ones(n)
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    e[:na] = np.linspace(0, 1, max(na, 1))
    e[na:na + nd] = np.linspace(1, s, max(nd, 1))
    e[na + nd:n - nr] = s
    e[n - nr:] = np.linspace(s, 0, max(nr, 1))
    return e


def lowpass(x, cutoff):
    """Filtro passa-baixa por FFT (cutoff pode ser escalar)."""
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1 / SR)
    spec *= 1.0 / (1.0 + (freqs / cutoff) ** 4)
    return np.fft.irfft(spec, len(x))


def bandpass_sweep(noise, f0, f1, width, n_blocks=48):
    """Ruído filtrado com frequência central varrendo de f0 a f1."""
    n = len(noise)
    out = np.zeros(n)
    block = n // n_blocks
    window = np.hanning(block * 2)
    for i in range(n_blocks):
        start = i * block
        seg = noise[start:start + block * 2]
        if len(seg) < block * 2:
            seg = np.pad(seg, (0, block * 2 - len(seg)))
        f = f0 * (f1 / f0) ** (i / max(n_blocks - 1, 1))
        spec = np.fft.rfft(seg)
        freqs = np.fft.rfftfreq(len(seg), 1 / SR)
        spec *= np.exp(-((freqs - f) / (f * width)) ** 2)
        filtered = np.fft.irfft(spec, len(seg)) * window
        end = min(start + block * 2, n)
        out[start:end] += filtered[:end - start]
    return out


def reverb(x, seconds=2.5, wet=0.35, seed=7):
    rng = np.random.default_rng(seed)
    n = int(seconds * SR)
    ir = rng.normal(0, 1, n) * np.exp(-np.linspace(0, 7, n))
    ir = lowpass(ir, 5500)
    ir[0] = 0
    size = len(x) + n
    wet_sig = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)
    wet_sig /= (np.max(np.abs(wet_sig)) + 1e-9) / (np.max(np.abs(x)) + 1e-9)
    dry = np.pad(x, (0, size - len(x)))
    return dry * (1 - wet) + wet_sig * wet


def stereo_from(x, width=0.35, seed=3):
    """Pseudo-estéreo: decorrelaciona levemente os canais."""
    rng = np.random.default_rng(seed)
    delay = int(0.011 * SR)
    left = x + width * np.roll(x, delay)
    right = x + width * np.roll(x, -delay)
    return np.stack([left, right], axis=1)


def write_ogg(path: Path, data, peak=0.89):
    data = np.asarray(data, dtype=np.float64)
    m = np.max(np.abs(data)) + 1e-9
    data = data / m * peak
    # evita clique no corte final
    fade = int(0.01 * SR)
    ramp = np.linspace(1, 0, fade)
    if data.ndim == 1:
        data[-fade:] *= ramp
    else:
        data[-fade:] *= ramp[:, None]
    path.parent.mkdir(parents=True, exist_ok=True)
    data = np.nan_to_num(data).astype(np.float32)
    channels = 1 if data.ndim == 1 else data.shape[1]
    # em blocos: escrever tudo de uma vez estoura a pilha do libvorbis no Windows
    with sf.SoundFile(str(path), "w", samplerate=SR, channels=channels, format="OGG", subtype="VORBIS") as out:
        for start in range(0, len(data), 4096):
            out.write(data[start:start + 4096])


def hz(note):
    names = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}
    pitch, octave = note[:-1], int(note[-1])
    return 440.0 * 2 ** ((names[pitch] + 12 * (octave + 1) - 69) / 12)


def tone(freq, seconds, harmonics=(1.0,), detune=0.0, phase=0.0):
    t = t_axis(seconds)
    out = np.zeros_like(t)
    for i, amp in enumerate(harmonics, start=1):
        out += amp * np.sin(2 * np.pi * freq * i * (1 + detune) * t + phase * i)
    return out


def bell(freq, seconds, decay=2.2):
    t = t_axis(seconds)
    partials = [(1.0, 1.0), (2.76, 0.5), (5.4, 0.28), (8.93, 0.14)]
    out = sum(a * np.sin(2 * np.pi * freq * r * t) * np.exp(-t * decay * (0.6 + 0.4 * r)) for r, a in partials)
    return out * np.minimum(1, t * 600)


def place(dst, src, at_seconds, gain=1.0):
    """Soma src em dst a partir de at_seconds, cortando o que passar do fim."""
    start = int(at_seconds * SR)
    count = min(len(src), len(dst) - start)
    if count > 0:
        dst[start:start + count] += src[:count] * gain


def build_sounds():
    snd = NS / "sounds" / "intro"
    rng = np.random.default_rng(11)

    # 1. ambiente em loop perfeito (24 s): pad em Ré menor com respiração lenta
    T = 24.0
    t = t_axis(T)
    pad = np.zeros_like(t)
    chord = [("D2", 0.7), ("A2", 0.5), ("D3", 0.55), ("F3", 0.4), ("A3", 0.35), ("C4", 0.22), ("E4", 0.16)]
    for i, (note, amp) in enumerate(chord):
        f = round(hz(note) * T) / T  # ciclos inteiros: o loop fecha sem emenda
        lfo = 0.65 + 0.35 * np.sin(2 * np.pi * (1 / T) * (2 + i % 3) * t + i)
        for det in (-0.0035, 0.0, 0.0035):
            fd = round(f * (1 + det) * T) / T
            pad += amp * lfo * (np.sin(2 * np.pi * fd * t) + 0.35 * np.sin(2 * np.pi * 2 * fd * t + i)
                                + 0.12 * np.sin(2 * np.pi * 3 * fd * t))
    shimmer = np.zeros_like(t)
    for k, note in enumerate(["D5", "A5", "F5", "C6"]):
        f = round(hz(note) * T) / T
        shimmer += (0.5 + 0.5 * np.sin(2 * np.pi * (3 + k) / T * t + k * 1.7)) ** 3 * np.sin(2 * np.pi * f * t)
    pad = pad / np.max(np.abs(pad)) + 0.16 * shimmer / np.max(np.abs(shimmer))
    left = pad + 0.28 * np.roll(pad, int(0.017 * SR))
    right = pad + 0.28 * np.roll(pad, -int(0.017 * SR))
    write_ogg(snd / "drone.ogg", np.stack([left, right], axis=1), 0.7)

    # 2. riser (crescendo antes da revelação)
    T = 3.6
    n = int(T * SR)
    t = t_axis(T)
    noise = rng.normal(0, 1, n)
    sweep = bandpass_sweep(noise, 300, 6500, 0.6)
    freq = 110 * (8 ** (t / T))
    tonal = np.sin(2 * np.pi * np.cumsum(freq) / SR) * 0.5
    riser = (sweep / np.max(np.abs(sweep)) * 0.8 + tonal) * (t / T) ** 2.2
    write_ogg(snd / "riser.ogg", stereo_from(reverb(riser, 1.5, 0.25)), 0.85)

    # 3. impacto grave
    T = 3.2
    t = t_axis(T)
    f = 28 + 90 * np.exp(-t * 7)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 1.5)
    burst = lowpass(rng.normal(0, 1, len(t)), 900) * np.exp(-t * 9)
    crack = rng.normal(0, 1, len(t)) * np.exp(-t * 60) * 0.5
    boom = reverb(sub + burst * 0.9 + crack, 2.4, 0.3)
    write_ogg(snd / "boom.ogg", stereo_from(boom, 0.2), 0.95)

    # 4. whoosh (mono, posicional): sobe e desce
    T = 1.3
    n = int(T * SR)
    t = t_axis(T)
    w = bandpass_sweep(rng.normal(0, 1, n), 250, 3800, 0.7)
    w *= np.sin(np.pi * (t / T)) ** 1.5
    write_ogg(snd / "whoosh.ogg", w / (np.max(np.abs(w)) + 1e-9), 0.8)

    # 5. tique de seleção (mono): dois parciais metálicos curtos
    T = 0.45
    t = t_axis(T)
    tick = (np.sin(2 * np.pi * 1480 * t) * 0.7 + np.sin(2 * np.pi * 2220 * t) * 0.4 + np.sin(2 * np.pi * 3960 * t) * 0.2) \
        * np.exp(-t * 16) * np.minimum(1, t * 900)
    write_ogg(snd / "tick.ogg", tick, 0.75)

    # 6. confirmação: coro sintético + sinos (estéreo)
    T = 6.0
    t = t_axis(T)
    swell = np.minimum(1, t / 1.6) * np.exp(-np.maximum(0, t - 3.2) * 0.9)
    choir = np.zeros_like(t)
    for note, amp in [("D3", 0.6), ("A3", 0.5), ("D4", 0.5), ("F#4", 0.4), ("A4", 0.35), ("D5", 0.25)]:
        for det in (-0.004, 0.0, 0.004):
            vibrato = 0.0025 * np.sin(2 * np.pi * 5.2 * t + det * 900)
            choir += amp * np.sin(2 * np.pi * hz(note) * (1 + det) * t + vibrato * 40)
    choir *= swell
    bells = np.zeros_like(t)
    for i, (note, at) in enumerate([("D5", 0.0), ("A5", 0.35), ("F#5", 0.7), ("D6", 1.05)]):
        place(bells, bell(hz(note), T - at, 1.4), at, 0.5 - i * 0.05)
    confirm = reverb(choir / np.max(np.abs(choir)) * 0.7 + bells * 0.6, 3.0, 0.4)
    write_ogg(snd / "confirm.ogg", stereo_from(confirm, 0.3), 0.9)

    # 7-9. vinhetas por reino (mono, posicionais)
    # Enternais: arpejo cristalino em Ré lídio
    T = 3.2
    st = np.zeros(int(T * SR))
    for i, note in enumerate(["D5", "F#5", "A5", "C#6", "E6", "A6"]):
        place(st, bell(hz(note), T - i * 0.12, 2.6), i * 0.12, 0.75 + i * 0.04)
    write_ogg(snd / "stinger_enternais.ogg", reverb(st, 2.2, 0.35), 0.85)

    # Aurudos: fanfarra de metais (serra filtrada, quinta e oitava)
    T = 3.2
    t = t_axis(T)
    out = np.zeros_like(t)
    for note, at, dur in [("D3", 0.0, 1.0), ("A3", 0.5, 1.2), ("D4", 1.0, 2.2)]:
        n = int(dur * SR)
        tt = np.arange(n) / SR
        voice = np.zeros(n)
        for f in (hz(note), hz(note) * 1.5, hz(note) * 2):
            for det in (-0.003, 0.003):
                voice += (2 * ((tt * f * (1 + det)) % 1.0) - 1)
        voice = lowpass(voice, 1500 + 1800 * np.minimum(1, tt / 0.25).mean())
        voice *= env_adsr(n, 0.04, 0.2, 0.7, 0.5)
        place(out, voice, at)
    write_ogg(snd / "stinger_aurudos.ogg", reverb(out / np.max(np.abs(out)), 2.4, 0.33), 0.85)

    # Cafetões: baixo que desliza + acorde menor "de veludo"
    T = 3.2
    t = t_axis(T)
    f = 55 * (1 + 0.5 * np.exp(-t * 6))
    bass = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_adsr(len(t), 0.01, 0.4, 0.5, 1.2)
    stab = np.zeros_like(t)
    for note in ["A3", "C4", "E4", "G4"]:
        pluck = bell(hz(note), T, 3.5) * 0.5 + np.sin(2 * np.pi * hz(note) * t) * np.exp(-t * 2.8) * 0.5
        stab += pluck
    smoke = lowpass(rng.normal(0, 1, len(t)), 600) * np.exp(-t * 3) * 0.15
    out = bass * 1.1 + stab * 0.6 + smoke
    write_ogg(snd / "stinger_cafetoes.ogg", reverb(out / np.max(np.abs(out)), 2.0, 0.3), 0.85)

    # 10. revelação do título (fanfarra de fechamento, estéreo)
    T = 5.0
    t = t_axis(T)
    swell = np.minimum(1, t / 0.9) * np.exp(-np.maximum(0, t - 2.4) * 1.1)
    out = np.zeros_like(t)
    for note, amp in [("D2", 1.0), ("A2", 0.7), ("D3", 0.7), ("A3", 0.5), ("D4", 0.45), ("F#4", 0.3), ("A4", 0.3)]:
        for det in (-0.003, 0.0, 0.003):
            out += amp * (np.sin(2 * np.pi * hz(note) * (1 + det) * t) + 0.4 * np.sin(4 * np.pi * hz(note) * (1 + det) * t))
    out *= swell
    out += bell(hz("D6"), T, 1.2) * 0.35 + bell(hz("A5"), T, 1.2) * 0.25
    write_ogg(snd / "title.ogg", stereo_from(reverb(out / np.max(np.abs(out)), 3.0, 0.38), 0.3), 0.9)

    events = {
        "intro.drone": [{"name": "vertexx:intro/drone", "stream": True}],
        "intro.riser": ["vertexx:intro/riser"],
        "intro.boom": ["vertexx:intro/boom"],
        "intro.whoosh": ["vertexx:intro/whoosh"],
        "intro.tick": ["vertexx:intro/tick"],
        "intro.confirm": ["vertexx:intro/confirm"],
        "intro.title": ["vertexx:intro/title"],
        "intro.stinger.enternais": ["vertexx:intro/stinger_enternais"],
        "intro.stinger.aurudos": ["vertexx:intro/stinger_aurudos"],
        "intro.stinger.cafetoes": ["vertexx:intro/stinger_cafetoes"],
    }
    write_json(NS / "sounds.json", {k: {"sounds": v} for k, v in events.items()})


# ═══════════════════════════ empacotamento ═══════════════════════════

def zip_pack():
    OUT_ZIP.parent.mkdir(parents=True, exist_ok=True)
    stamp = (2026, 1, 1, 0, 0, 0)
    files = sorted(p for p in PACK.rglob("*") if p.is_file() and p.name != "README.md")
    with zipfile.ZipFile(OUT_ZIP, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for path in files:
            info = zipfile.ZipInfo(path.relative_to(PACK).as_posix(), stamp)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, path.read_bytes())
    return OUT_ZIP


def build_models():
    """Modelos de entidade (bbmodel → item models + texturas) e os dados de runtime do plugin."""
    sys.path.insert(0, str(ROOT / "tools" / "models"))
    import convert
    for name, rt in convert.build(NS):
        print(f"modelo {name}: {len(rt['bones'])} ossos, {len(rt['anim'])} animações")


def main():
    # preserva o README, recria o resto
    readme = PACK / "README.md"
    keep = readme.read_text(encoding="utf-8") if readme.exists() else None
    if PACK.exists():
        shutil.rmtree(PACK)
    PACK.mkdir(parents=True)
    if keep is not None:
        readme.write_text(keep, encoding="utf-8")
    build_assets()
    build_models()
    build_sounds()
    out = zip_pack()
    import hashlib
    digest = hashlib.sha1(out.read_bytes()).hexdigest()
    print(f"OK {out} ({out.stat().st_size / 1024:.0f} KiB) sha1={digest}")


if __name__ == "__main__":
    sys.exit(main())
