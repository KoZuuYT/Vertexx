"""Modelos 3D das Waystones (um por skin), feitos de texturas vanilla com runas entalhadas.

``build(ns, save, write_json)`` escreve, para cada skin:
  textures/block/waystone/<skin>_{rune,body,dark,cap}.png
  models/block/waystone/<skin>.json        (base larga, pescoço escuro, pilar com runas e tampa)
  items/waystone_<skin>.json               (item_model usado pela ItemDisplay do plugin)

O corpo cobre o Lodestone por inteiro (as peças têm 0,25 a 0,5 px a mais que o bloco), então só a
base e a tampa são mais largas. Os ids TÊM de bater com WaystoneSkin.java.
"""
from __future__ import annotations

import enditems

# id -> (textura do corpo, textura da tampa, cor da runa central)
SKINS = {
    "stone_bricks": ("stone_bricks", "stone_bricks", "#9be3ff"),
    "smooth_stone": ("smooth_stone", "smooth_stone", "#ffe08a"),
    "stone": ("stone", "cobblestone", "#b8ffb0"),
    "end_stone": ("end_stone_bricks", "end_stone_bricks", "#d9a8ff"),
    "purpur": ("purpur_block", "purpur_block", "#ff9be8"),
    "diorite": ("polished_diorite", "polished_diorite", "#9be3ff"),
    "andesite": ("polished_andesite", "polished_andesite", "#ffd08a"),
    "bricks": ("bricks", "bricks", "#ffb070"),
    "desert": ("cut_sandstone", "sandstone_top", "#ffe27a"),
    "ice": ("packed_ice", "snow", "#e6fbff"),
}


def _shade(img, k):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            px[x, y] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), a)
    return out


def _mul(px, x, y, k):
    r, g, b, a = px[x, y]
    px[x, y] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), a)


def _rune(body, accent):
    """Face do pilar: moldura afundada com uma runa em cruz e um núcleo colorido."""
    img = body.copy()
    px = img.load()
    for i in range(2, 14):                                   # moldura: sombra em cima/esquerda, luz embaixo/direita
        _mul(px, i, 2, 0.5)
        _mul(px, 2, i, 0.55)
        _mul(px, i, 13, 1.25)
        _mul(px, 13, i, 1.2)
    for y in range(3, 13):                                   # fundo da runa um pouco mais escuro
        for x in range(3, 13):
            _mul(px, x, y, 0.78)
    for i in range(4, 12):                                   # anel interno em relevo
        _mul(px, i, 4, 1.15)
        _mul(px, 4, i, 1.15)
        _mul(px, i, 11, 0.6)
        _mul(px, 11, i, 0.6)
    for i in range(5, 11):                                   # braços da cruz entalhados
        for c in (7, 8):
            _mul(px, c, i, 0.38)
            _mul(px, i, c, 0.38)
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):            # núcleo brilhante
        px[x, y] = enditems._hex(accent)
    return img


def _model(skin):
    t = f"vertexx:block/waystone/{skin}"

    def box(a, b, side, up, down):
        face = {"uv": [0, 0, 16, 16]}
        return {"from": a, "to": b, "faces": {
            "north": dict(face, texture=side), "south": dict(face, texture=side),
            "east": dict(face, texture=side), "west": dict(face, texture=side),
            "up": dict(face, texture=up), "down": dict(face, texture=down)}}

    return {
        "textures": {"rune": f"{t}_rune", "body": f"{t}_body", "dark": f"{t}_dark", "cap": f"{t}_cap",
                     "particle": f"{t}_body"},
        "elements": [
            box([-2, 0, -2], [18, 4, 18], "#body", "#cap", "#dark"),            # base larga
            box([-0.25, 4, -0.25], [16.25, 6, 16.25], "#dark", "#dark", "#dark"),   # pescoço escuro
            box([-0.5, 6, -0.5], [16.5, 14, 16.5], "#rune", "#body", "#body"),      # pilar com runas
            box([-1.5, 14, -1.5], [17.5, 16.5, 17.5], "#body", "#cap", "#dark"),    # tampa
        ],
    }


def build(ns, save, write_json):
    for skin, (body, cap, accent) in SKINS.items():
        b = enditems.vanilla(body)
        c = enditems.vanilla(cap)
        base = ns / "textures" / "block" / "waystone"
        save(_rune(b, accent), base / f"{skin}_rune.png")
        save(b, base / f"{skin}_body.png")
        save(_shade(b, 0.55), base / f"{skin}_dark.png")
        save(_shade(c, 1.12), base / f"{skin}_cap.png")
        write_json(ns / "models" / "block" / "waystone" / f"{skin}.json", _model(skin))
        write_json(ns / "items" / f"waystone_{skin}.json",
                   {"model": {"type": "minecraft:model", "model": f"vertexx:block/waystone/{skin}"}})
