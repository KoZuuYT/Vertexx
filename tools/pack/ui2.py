"""Menus largos (painel de 360x222) das páginas Status, Habilidades e Informações, no estilo de tela de classes.

O painel é um glifo da fonte ``vertexx:panel`` desenhado atrás da janela do inventário (176x222), com uma faixa
de 92 px de cada lado. Tudo que muda (textos, barras de XP, molduras dos nós) é desenhado pelo servidor no
título do inventário com fontes de uma linha: ``vertexx:ln<Y>`` coloca texto, barras e molduras com o topo em Y
(em pixels de GUI a partir do topo da janela). Os recuos horizontais usam ``vertexx:sp``.

Também escreve ``src/main/resources/ui-widths.txt`` (larguras dos caracteres) para o servidor medir os textos.
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
VAN = HERE / "vanilla"

S = 2
PW, PH, WX = 360, 222, 92          # painel e posição da janela do inventário dentro dele
BG = (10, 14, 26)

# ordem = índice do glifo (U+E400 + índice) e de Java: StatsGUI.CATEGORIES
CATEGORIES = [
    # stat, item da arte, cor de destaque, nós: (nome, índice do pai ou None)
    ("MINING", "diamond_pickaxe", (76, 214, 240), [("HASTE", None), ("AREA_MINER", 0), ("VEIN_MINER", None), ("LUCKY", 2)]),
    ("WOODCUTTING", "diamond_axe", (98, 214, 98), [("CAPITATOR", None)]),
    ("EXCAVATION", "diamond_shovel", (240, 176, 64), [("RESTOCK", None), ("REACH", 0)]),
    ("FARMING", "diamond_hoe", (240, 220, 80), [("HARVEST", None), ("REPLANT", None)]),
    ("COMBAT", "diamond_sword", (240, 84, 84), [("STUN", None), ("FRENZY", 0)]),
    ("EXPLORATION", "ender_eye", (64, 190, 200), [("MAGNET", None), ("DASH", None)]),
    ("ACROBATICS", "feather", (228, 232, 240), [("FEATHER", None)]),
    ("ENDURANCE", "iron_chestplate", (196, 70, 70), [("THICK", None), ("VITALITY", 0)]),
]
PAGES = {"stats": ("nether_star", (255, 206, 80)), "info": ("book", (255, 209, 102))}
FIRST_TREE = 2

ASCII_ROWS = ["\0" * 16, "\0" * 16, " !\"#$%&'()*+,-./", "0123456789:;<=>?", "@ABCDEFGHIJKLMNO",
              "PQRSTUVWXYZ[\\]^_", "`abcdefghijklmno", "pqrstuvwxyz{|}~\0"] + ["\0" * 16] * 8
Y_MIN, Y_MAX = 6, 130
BAR_W, BAR_STEPS = 40, 20
FRAME_STATES = 7


def _mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _scale(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def _rect(d, x0, y0, x1, y1, **kw):
    d.rectangle([x0 * S, y0 * S, x1 * S - 1, y1 * S - 1], **kw)


def hero_art(path: Path, accent, width: int, height: int) -> Image.Image:
    """Item vanilla ampliado sobre um brilho do tema, com pixels grandes e leve vinheta."""
    art = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    yy, xx = np.mgrid[0:height, 0:width]
    cx, cy = width * 0.5, height * 0.52
    dist = np.sqrt(((xx - cx) / (width * 0.5)) ** 2 + ((yy - cy) / (height * 0.5)) ** 2)
    glow = np.clip(1 - dist, 0, 1) ** 1.6
    layer = np.zeros((height, width, 4), dtype=np.float32)
    layer[..., 0], layer[..., 1], layer[..., 2] = accent
    layer[..., 3] = glow * 120
    art = Image.fromarray(layer.astype(np.uint8), "RGBA")
    icon = Image.open(path).convert("RGBA")
    k = max(1, int(min(width, height) * 0.78) // 16)
    icon = icon.resize((16 * k, 16 * k), Image.NEAREST)
    shadow = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 150), mask=icon.split()[3])
    shadow = shadow.filter(ImageFilter.GaussianBlur(k * 1.4))
    ox, oy = (width - icon.width) // 2, (height - icon.height) // 2
    art.alpha_composite(shadow, (ox + k, oy + k))
    art.alpha_composite(icon, (ox, oy))
    return art


def panel(name: str, art_item: str, accent) -> Image.Image:
    W, H = PW * S, PH * S
    rng = np.random.default_rng(sum(map(ord, name)))
    ys = np.linspace(0, 1, H)[:, None, None]
    base = np.array(_scale(BG, 1.5), dtype=np.float32) * (1 - ys) + np.array(_scale(BG, 0.8), dtype=np.float32) * ys
    base = np.repeat(base, W, axis=1) + rng.normal(0, 1.2, (H, W, 3))
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    d = ImageDraw.Draw(img)

    # brilho do tema vindo do canto direito
    yy, xx = np.mgrid[0:H, 0:W]
    dist = np.sqrt(((xx - W * 0.86) / (W * 0.5)) ** 2 + ((yy - H * 0.6) / (H * 0.8)) ** 2)
    tint = np.clip(1 - dist, 0, 1) ** 1.8
    arr = np.array(img).astype(np.float32)
    for i, c in enumerate(accent):
        arr[..., i] += tint * c * 0.22
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(img)

    # faixa do cabeçalho e moldura de vidro
    _rect(d, 1, 1, PW - 1, 17, fill=(6, 9, 18, 255))
    _rect(d, 1, 16, PW - 1, 17, fill=(40, 48, 68, 255))
    _rect(d, 0, 0, PW, PH, outline=(3, 5, 10, 255), width=S)
    _rect(d, 1, 1, PW - 1, 2, fill=(86, 98, 124, 255))
    _rect(d, 1, 1, 2, PH - 1, fill=(70, 80, 104, 255))
    _rect(d, 1, PH - 2, PW - 1, PH - 1, fill=(26, 32, 48, 255))
    _rect(d, PW - 2, 1, PW - 1, PH - 1, fill=(26, 32, 48, 255))

    # coluna central (a janela do inventário): placa mais escura com divisórias
    _rect(d, WX - 2, 18, WX + 178, 127, fill=(8, 11, 21, 235))
    _rect(d, WX - 3, 18, WX - 2, 127, fill=(40, 48, 68, 255))
    _rect(d, WX + 178, 18, WX + 179, 127, fill=(40, 48, 68, 255))
    _rect(d, WX - 2, 127, WX + 178, 128, fill=(40, 48, 68, 255))
    # corpo inferior (inventário do jogador): casas discretas
    pf = (14, 18, 32, 255)
    for row in range(3):
        for col in range(9):
            _rect(d, WX + 7 + 18 * col, 139 + 18 * row, WX + 25 + 18 * col, 157 + 18 * row, fill=pf, outline=(34, 42, 60, 255))
    for col in range(9):
        _rect(d, WX + 7 + 18 * col, 197, WX + 25 + 18 * col, 215, fill=pf, outline=(34, 42, 60, 255))

    # arte do herói na faixa da direita, esmaecendo para a esquerda
    band_x = WX + 182
    art = hero_art(VAN / f"{art_item}.png", accent, (PW - band_x - 6) * S, 150 * S)
    img.alpha_composite(art, (band_x * S, 52 * S))
    # tarja de cabeçalho em cima da arte
    d = ImageDraw.Draw(img)
    _rect(d, 1, 1, PW - 1, 17, fill=(6, 9, 18, 255))
    _rect(d, 1, 16, PW - 1, 17, fill=(40, 48, 68, 255))
    # separador da faixa esquerda
    _rect(d, 4, 20, WX - 5, 21, fill=(40, 48, 68, 255))
    # a última coluna precisa ser opaca para o avanço do glifo ser a largura inteira
    px = img.load()
    for y in range(H):
        px[W - 1, y] = px[W - 2, y][:3] + (255,)
    return img


def frames() -> Image.Image:
    """Sete molduras 18x18 (casa vazia, aprendida, disponível, bloqueada, selecionada, ativa, máxima)."""
    cell = 18 * S
    img = Image.new("RGBA", (cell, cell * FRAME_STATES), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    specs = [
        ((14, 18, 32), (44, 54, 76)),        # 0 vazia
        ((16, 34, 24), (98, 224, 122)),      # 1 aprendida
        ((36, 30, 14), (255, 208, 64)),      # 2 disponível
        ((10, 12, 20), (54, 60, 78)),        # 3 bloqueada
        ((26, 34, 58), (236, 242, 255)),     # 4 selecionada
        ((30, 18, 48), (178, 106, 255)),     # 5 ativa
        ((40, 30, 8), (255, 196, 40)),       # 6 máxima
    ]
    for i, (fill, edge) in enumerate(specs):
        y0 = i * cell
        d.rectangle([0, y0, cell - 1, y0 + cell - 1], fill=fill + (255,))
        d.rectangle([0, y0, cell - 1, y0 + cell - 1], outline=edge + (255,), width=S)
        if i in (1, 2, 4, 5, 6):
            d.rectangle([S, y0 + S, cell - 1 - S, y0 + cell - 1 - S], outline=_scale(edge, 0.45) + (255,), width=1)
    return img


def bars() -> Image.Image:
    """BAR_STEPS+1 barras de BAR_W x 8: trecho cheio branco e trilho cinza (a cor do texto tinge as duas)."""
    w, h = BAR_W * S, 8 * S
    img = Image.new("RGBA", (w, h * (BAR_STEPS + 1)), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(BAR_STEPS + 1):
        y0 = i * h
        top, bot = y0 + 2 * S, y0 + 5 * S
        d.rectangle([0, top, w - 1, bot - 1], fill=(70, 70, 70, 255))
        fill = round(w * i / BAR_STEPS)
        if fill > 0:
            d.rectangle([0, top, fill - 1, bot - 1], fill=(255, 255, 255, 255))
            d.rectangle([0, top, fill - 1, top + 1], fill=(255, 255, 255, 255))
    return img


def widths() -> dict[str, int]:
    im = Image.open(VAN / "ascii.png").convert("RGBA")
    a = np.array(im)[..., 3]
    out = {}
    for r, row in enumerate(ASCII_ROWS):
        for c, ch in enumerate(row):
            if ch == "\0" or ch == " ":
                continue
            cell = a[r * 8:(r + 1) * 8, c * 8:(c + 1) * 8]
            cols = np.where(cell.any(axis=0))[0]
            out[ch] = int(cols.max()) + 2 if len(cols) else 0
    return out


def build(ns: Path, save, write_json):
    tex = ns / "textures" / "gui"
    names = ["stats", "info"] + [c[0].lower() for c in CATEGORIES]
    arts = [PAGES["stats"], PAGES["info"]] + [(c[1], c[2]) for c in CATEGORIES]
    providers = []
    for i, (name, (item, accent)) in enumerate(zip(names, arts)):
        save(panel(name, item, accent), tex / f"panel_{name}.png")
        providers.append({"type": "bitmap", "file": f"vertexx:gui/panel_{name}.png", "ascent": 13, "height": PH,
                          "chars": [chr(0xE400 + i)]})
    write_json(ns / "font" / "panel.json", {"providers": providers})

    # recuos horizontais: +1, +2, +4 ... +256 (U+E200..) e os negativos (U+E210..)
    adv = {}
    for k in range(9):
        adv[chr(0xE200 + k)] = 1 << k
        adv[chr(0xE210 + k)] = -(1 << k)
    write_json(ns / "font" / "sp.json", {"providers": [{"type": "space", "advances": adv}]})

    save(frames(), tex / "frames.png")
    save(bars(), tex / "bars.png")
    for y in range(Y_MIN, Y_MAX + 1):
        ascent = 13 - y
        write_json(ns / "font" / f"ln{y}.json", {"providers": [
            {"type": "space", "advances": {" ": 4}},
            {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": ascent, "height": 8, "chars": ASCII_ROWS},
            {"type": "bitmap", "file": "vertexx:gui/bars.png", "ascent": ascent, "height": 8,
             "chars": [chr(0xE300 + i) for i in range(BAR_STEPS + 1)]},
            {"type": "bitmap", "file": "vertexx:gui/frames.png", "ascent": ascent, "height": 18,
             "chars": [chr(0xE340 + i) for i in range(FRAME_STATES)]},
        ]})
    w = widths()
    lines = [f"{ord(ch)} {n}" for ch, n in sorted(w.items())]
    out = ROOT / "src" / "main" / "resources" / "ui-widths.txt"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    print(len(widths()))
