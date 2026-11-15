"""Fundos de menu (glifos), ícones de interface e itens invisíveis do resource pack do Vertexx.

Um menu de 6 linhas tem 176x222 pixels de GUI. O fundo é desenhado em 2x (352x444) e usado como um
glifo da fonte ``vertexx:menu``: o título do inventário o posiciona sobre toda a janela (ver ``Ui.java``).
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 2                      # escala do fundo em relação aos pixels de GUI
W, H = 176, 222            # tamanho da GUI de 6 linhas
GOLD = (255, 214, 102)

THEMES = {  # ordem = U+E100 + índice (mesma de Ui.Theme)
    "vertexx": dict(dark=(24, 16, 46), mid=(96, 64, 214), light=(196, 167, 255), accent=(44, 182, 125)),
    "enternais": dict(dark=(24, 12, 64), mid=(88, 48, 210), light=(196, 167, 255), accent=(124, 77, 255)),
    "aurudos": dict(dark=(58, 32, 4), mid=(217, 130, 0), light=(255, 224, 130), accent=(255, 143, 0)),
    "cafetoes": dict(dark=(48, 8, 10), mid=(183, 28, 28), light=(255, 138, 128), accent=(229, 57, 53)),
    "outlaw": dict(dark=(22, 8, 10), mid=(140, 22, 26), light=(230, 96, 84), accent=(255, 170, 60)),
}


def _mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _scale(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def _rect(d, x0, y0, x1, y1, **kw):
    """Retângulo em pixels de GUI (x1, y1 exclusivos)."""
    d.rectangle([x0 * S, y0 * S, x1 * S - 1, y1 * S - 1], **kw)


def _cell(d, x, y, fill, edge_dark, edge_light):
    """Casa de 18x18 com relevo interno (afundada)."""
    _rect(d, x, y, x + 18, y + 18, fill=fill)
    _rect(d, x, y, x + 18, y + 1, fill=edge_dark)            # topo e esquerda escuros
    _rect(d, x, y, x + 1, y + 18, fill=edge_dark)
    _rect(d, x, y + 17, x + 18, y + 18, fill=edge_light)     # base e direita claras
    _rect(d, x + 17, y, x + 18, y + 18, fill=edge_light)


def _plate(d, x, y, mid, light, dark):
    """Placa decorativa (em relevo) para o anel do menu."""
    _rect(d, x, y, x + 18, y + 18, fill=dark)
    _rect(d, x + 1, y + 1, x + 17, y + 17, fill=mid)
    _rect(d, x + 1, y + 1, x + 17, y + 2, fill=light)
    _rect(d, x + 1, y + 1, x + 2, y + 17, fill=light)
    _rect(d, x + 1, y + 16, x + 17, y + 17, fill=_scale(mid, 0.6))
    _rect(d, x + 16, y + 1, x + 17, y + 17, fill=_scale(mid, 0.6))


def _star(d, cx, cy, r, fill):
    pts = []
    for i in range(8):
        ang = math.pi / 4 * i - math.pi / 2
        rad = r if i % 2 == 0 else r * 0.38
        pts.append((cx * S + math.cos(ang) * rad * S, cy * S + math.sin(ang) * rad * S))
    d.polygon(pts, fill=fill)


def _daggers(d, cx, cy, r, fill, edge):
    for sign in (1, -1):
        x0, y0 = cx - sign * r, cy - r
        x1, y1 = cx + sign * r, cy + r
        d.line([(x0 * S, y0 * S), (x1 * S, y1 * S)], fill=edge, width=int(2.6 * S))
        d.line([(x0 * S, y0 * S), (x1 * S, y1 * S)], fill=fill, width=int(1.4 * S))


def background(name: str, emblem: Image.Image | None) -> Image.Image:
    t = THEMES[name]
    dark, mid, light, accent = t["dark"], t["mid"], t["light"], t["accent"]
    rng = np.random.default_rng(sum(ord(c) * (i + 1) for i, c in enumerate(name)))  # determinístico
    ys = np.linspace(0, 1, H * S)[:, None, None]
    base = np.array(_scale(dark, 1.35), dtype=np.float32)[None, None, :] * (1 - ys) \
        + np.array(_scale(dark, 0.75), dtype=np.float32)[None, None, :] * ys
    base = np.repeat(base, W * S, axis=1)
    base += rng.normal(0, 1.6, base.shape)
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    d = ImageDraw.Draw(img)

    # moldura externa: contorno escuro, linha de destaque do tema e fio dourado
    _rect(d, 0, 0, W, H, outline=_scale(dark, 0.35), width=S)
    _rect(d, 1, 1, W - 1, H - 1, outline=mid, width=S)
    _rect(d, 2, 2, W - 2, H - 2, outline=_mix(GOLD, mid, 0.35), width=1)

    # faixa do título
    for y in range(3, 17):
        k = 1.0 - (y - 3) / 14.0
        _rect(d, 3, y, W - 3, y + 1, fill=_mix(_scale(dark, 1.0), _scale(mid, 0.55), 0.25 + 0.55 * k))
    _rect(d, 3, 16, W - 3, 17, fill=_mix(GOLD, mid, 0.4))
    # ornamento: losangos e emblema à direita
    for x in (W - 36, W - 30):
        d.polygon([((x) * S, 10 * S), ((x + 3) * S, 7 * S), ((x + 6) * S, 10 * S), ((x + 3) * S, 13 * S)], fill=_mix(GOLD, accent, 0.3))
    if emblem is not None:
        small = emblem.resize((14 * S, 14 * S), Image.LANCZOS)
        img.alpha_composite(small, ((W - 21) * S, 2 * S))
    elif name == "outlaw":
        _daggers(d, W - 14, 10, 5.5, (225, 230, 240), EDGE[:3])
    else:
        _star(d, W - 14, 10, 6.5, GOLD)

    # anel de placas e casas internas do conteúdo (6 linhas x 9 colunas)
    cell_fill = _scale(dark, 0.55)
    edge_dark = _scale(dark, 0.28)
    edge_light = _mix(_scale(dark, 1.4), mid, 0.3)
    for row in range(6):
        for col in range(9):
            x, y = 7 + 18 * col, 17 + 18 * row
            if row in (0, 5) or col in (0, 8):
                _plate(d, x, y, _scale(mid, 0.5), _mix(mid, light, 0.35), _scale(dark, 0.4))
            else:
                _cell(d, x, y, cell_fill, edge_dark, edge_light)
    # cantos do anel: ponto de luz do tema
    for col, row in ((0, 0), (8, 0), (0, 5), (8, 5)):
        _star(d, 7 + 18 * col + 9, 17 + 18 * row + 9, 4.5, _mix(light, GOLD, 0.35))

    # separador e inventário do jogador (3 linhas + barra de atalhos)
    _rect(d, 5, 126, W - 5, 127, fill=_mix(GOLD, mid, 0.5))
    _rect(d, 5, 127, W - 5, 128, fill=_scale(dark, 0.3))
    pfill = _scale(dark, 0.62)
    for row in range(3):
        for col in range(9):
            _cell(d, 7 + 18 * col, 139 + 18 * row, pfill, edge_dark, edge_light)
    for col in range(9):
        _cell(d, 7 + 18 * col, 197, pfill, edge_dark, edge_light)
    # coluna da direita (borda do tema) para fechar a moldura
    _rect(d, 3, H - 4, W - 3, H - 3, fill=_mix(GOLD, mid, 0.55))

    # a última coluna precisa ser opaca para o avanço do glifo ser a largura inteira
    px = img.load()
    for y in range(H * S):
        px[W * S - 1, y] = px[W * S - 2, y][:3] + (255,)
    return img


# ═══════════════════════════ ícones de interface (32x32) ═══════════════════════════

EDGE = (20, 14, 26, 255)


def _canvas():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def _fin(img: Image.Image) -> Image.Image:
    return img.resize((32, 32), Image.LANCZOS)


def _outline_line(d, pts, color, width, edge=EDGE, extra=16):
    d.line(pts, fill=edge, width=width + extra, joint="curve")
    d.line(pts, fill=color, width=width, joint="curve")


def icon_close():
    img, d = _canvas()
    _outline_line(d, [(30, 30), (98, 98)], (235, 64, 64, 255), 22)
    _outline_line(d, [(98, 30), (30, 98)], (235, 64, 64, 255), 22)
    return _fin(img)


def icon_back(flip=False):
    img, d = _canvas()
    pts = [(86, 20), (36, 64), (86, 108)]
    if flip:
        pts = [(128 - x, y) for x, y in pts]
    _outline_line(d, pts, (255, 196, 64, 255), 24)
    return _fin(img)


def icon_check():
    img, d = _canvas()
    _outline_line(d, [(22, 68), (50, 98), (106, 32)], (80, 220, 110, 255), 22)
    return _fin(img)


def icon_circle(symbol: str, color):
    img, d = _canvas()
    d.ellipse([10, 10, 118, 118], fill=EDGE)
    d.ellipse([20, 20, 108, 108], fill=color)
    d.ellipse([20, 20, 108, 70], fill=tuple(min(255, c + 28) for c in color[:3]) + (255,))
    white = (255, 255, 255, 255)
    d.rectangle([40, 56, 88, 72], fill=white)
    if symbol == "+":
        d.rectangle([56, 40, 72, 88], fill=white)
    return _fin(img)


def icon_coin():
    img, d = _canvas()
    d.ellipse([8, 8, 120, 120], fill=EDGE)
    d.ellipse([16, 16, 112, 112], fill=(255, 205, 70, 255))
    d.ellipse([28, 28, 100, 100], outline=(190, 130, 20, 255), width=8)
    d.polygon([(64, 40), (74, 60), (96, 62), (80, 76), (86, 98), (64, 86), (42, 98), (48, 76), (32, 62), (54, 60)],
              fill=(255, 245, 190, 255))
    return _fin(img)


def icon_daggers():
    img, d = _canvas()
    for sign in (1, -1):
        cx = 64
        def P(x, y):
            return (cx + sign * x, y)
        blade = [P(-52, 14), P(-40, 6), P(30, 78), P(18, 90)]
        d.polygon([(x, y) for x, y in blade], fill=(225, 230, 240, 255), outline=EDGE)
        guard = [P(22, 66), P(40, 84), P(34, 92), P(16, 74)]
        d.polygon(guard, fill=(255, 200, 70, 255), outline=EDGE)
        grip = [P(34, 88), P(48, 102), P(56, 114), P(46, 118), P(30, 104)]
        d.polygon(grip, fill=(150, 90, 40, 255), outline=EDGE)
    return _fin(img)


def icon_skull():
    img, d = _canvas()
    bone = (238, 234, 222, 255)
    d.ellipse([22, 8, 106, 92], fill=EDGE)
    d.rounded_rectangle([40, 70, 88, 118], radius=8, fill=EDGE)
    d.ellipse([28, 14, 100, 86], fill=bone)
    d.rounded_rectangle([46, 74, 82, 112], radius=6, fill=bone)
    d.ellipse([38, 42, 60, 66], fill=(30, 20, 30, 255))
    d.ellipse([68, 42, 90, 66], fill=(30, 20, 30, 255))
    d.polygon([(64, 62), (56, 78), (72, 78)], fill=(60, 40, 50, 255))
    for x in (54, 64, 74):
        d.line([(x, 92), (x, 110)], fill=(150, 140, 130, 255), width=4)
    return _fin(img)


def icon_star():
    img, d = _canvas()
    pts = []
    for i in range(10):
        ang = math.pi / 5 * i - math.pi / 2
        rad = 56 if i % 2 == 0 else 24
        pts.append((64 + math.cos(ang) * rad, 68 + math.sin(ang) * rad))
    d.polygon(pts, fill=(255, 205, 70, 255), outline=EDGE)
    d.line(pts + [pts[0]], fill=EDGE, width=6)
    inner = [(64 + (x - 64) * 0.5, 68 + (y - 68) * 0.5) for x, y in pts]
    d.polygon(inner, fill=(255, 240, 170, 255))
    return _fin(img)


def icon_link():
    img, d = _canvas()
    for cx, col in ((46, (90, 220, 140, 255)), (82, (255, 255, 255, 255))):
        d.ellipse([cx - 36, 30, cx + 36, 98], outline=EDGE, width=26)
        d.ellipse([cx - 36, 30, cx + 36, 98], outline=col, width=14)
    return _fin(img)


def icon_broken():
    img, d = _canvas()
    # lâmina partida em duas, com rachadura vermelha
    top = [(104, 14), (112, 22), (66, 62), (58, 48)]
    bot = [(54, 76), (70, 70), (24, 114), (14, 104)]
    for part in (top, bot):
        d.polygon(part, fill=(225, 230, 240, 255), outline=EDGE)
    d.line([(60, 56), (68, 64), (56, 70), (64, 78)], fill=(235, 64, 64, 255), width=8)
    d.polygon([(20, 96), (34, 110), (26, 120), (10, 106)], fill=(255, 200, 70, 255), outline=EDGE)
    return _fin(img)


ICONS = {
    "ui_close": icon_close,
    "ui_back": lambda: icon_back(False),
    "ui_next": lambda: icon_back(True),
    "ui_check": icon_check,
    "ui_plus": lambda: icon_circle("+", (60, 190, 90)),
    "ui_minus": lambda: icon_circle("-", (214, 64, 64)),
    "ui_coin": icon_coin,
    "ui_daggers": icon_daggers,
    "ui_skull": icon_skull,
    "ui_star": icon_star,
    "ui_link": icon_link,
    "ui_broken": icon_broken,
}


def build(ns, kingdoms, render_emblem, save, write_json):
    """Gera texturas, modelos, itens e o arquivo de fonte dos menus."""
    tex = ns / "textures"

    # fundos dos menus + fonte
    providers = []
    for i, name in enumerate(THEMES):
        emblem = render_emblem(name, 64, with_glow=False) if name in kingdoms else None
        save(background(name, emblem), tex / "gui" / f"{name}.png")
        providers.append({"type": "bitmap", "file": f"vertexx:gui/{name}.png", "ascent": 13, "height": H,
                          "chars": [chr(0xE100 + i)]})
    # recuos: antes do glifo (origem do título -> borda da janela) e depois dele (volta à origem)
    providers.append({"type": "space", "advances": {chr(0xE0F0): -8, chr(0xE0F1): -(W + 1) + 8}})
    write_json(ns / "font" / "menu.json", {"providers": providers})

    # item invisível (painéis decorativos) e ícones
    save(Image.new("RGBA", (16, 16), (0, 0, 0, 0)), tex / "item" / "blank.png")
    write_json(ns / "models" / "item" / "blank.json",
               {"textures": {"particle": "vertexx:item/blank"}, "elements": []})
    write_json(ns / "items" / "blank.json", {"model": {"type": "minecraft:model", "model": "vertexx:item/blank"}})

    def register(key: str, image: Image.Image):
        save(image, tex / "item" / f"{key}.png")
        write_json(ns / "models" / "item" / f"{key}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"vertexx:item/{key}"}})
        write_json(ns / "items" / f"{key}.json", {"model": {"type": "minecraft:model", "model": f"vertexx:item/{key}"}})

    for key, make in ICONS.items():
        register(key, make())
    for name in kingdoms:
        register(f"emblem_{name}", render_emblem(name, 32, with_glow=False))
