"""Itens da End Update (texturas 16x16, modelos de item) e as texturas das mochilas vestidas no peito.

``build(ns, save, write_json)`` escreve:
  textures/item/<id>.png, models/item/<id>.json, items/<id>.json    (um por item)
  equipment/<id>.json e textures/entity/equipment/humanoid/<id>.png  (mochilas: o modelo de armadura nas costas)
"""
from __future__ import annotations

from PIL import Image


def _hex(h: str):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def _shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def _paint(rows, palette, size=16):
    """Desenha o mapa ASCII centralizado numa tela size x size ('.' é transparente)."""
    h = len(rows)
    w = max(len(r) for r in rows)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ox, oy = (size - w) // 2, (size - h) // 2
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch in palette:
                img.putpixel((ox + x, oy + y), palette[ch])
    return img


# ── materiais ──────────────────────────────────────────────────

def enderite_fragment():
    rows = [
        ".....ooo........",
        "....oPPPoo......",
        "...oPqqPPPoo....",
        "..oPqqMPPPPoo...",
        "..oPPMMPPPPPPo..",
        "..oPPPPDPPPPPo..",
        "...oPPPDDPPPo...",
        "...ooPPPDDPPo...",
        ".....oPPPDDo....",
        "......ooPDo.....",
        ".......ooo......",
    ]
    pal = {"o": _hex("#120a1c"), "P": _hex("#3a2a52"), "q": _hex("#7a58b0"), "M": _hex("#e857e8"), "D": _hex("#241638")}
    return _paint(rows, pal)


def ethereal_membrane():
    rows = [
        "....oo..........",
        "...oMMoo........",
        "..oMmmMMoo......",
        "..oMmvmmMMoo....",
        ".oMmvmmmmmMMoo..",
        ".oMmvmmvmmmmMMo.",
        ".oMmmvmmvmmmmMo.",
        "..oMmmvmmvmmmo..",
        "..ooMmmmvmmmo...",
        "....ooMmmmmo....",
        "......ooMMo.....",
        "........oo......",
    ]
    pal = {"o": _hex("#1d2a5c"), "M": _hex("#8fd8f5"), "m": _hex("#b49cf0"), "v": _hex("#4a64c8")}
    img = _paint(rows, pal)
    for x, y in ((3, 3), (12, 2), (13, 11), (2, 12)):          # faíscas
        img.putpixel((x, y), _hex("#d8fbff"))
    return img


def void_chitin():
    rows = [
        "...ooooooo...",
        "..oKKKKKKKo..",
        ".oKkkKKKKKKo.",
        ".oKkPKKKKKKo.",
        ".oKKKKppKKKo.",
        ".oKKKKKKKKKo.",
        "..oKKKKKKKo..",
        "..oDKKKKKDo..",
        "...oDDDDDo...",
        "....ooooo....",
    ]
    pal = {"o": _hex("#07050a"), "K": _hex("#1d1829"), "k": _hex("#41345f"), "P": _hex("#8a3fc4"),
           "p": _hex("#c864f0"), "D": _hex("#0e0b14")}
    return _paint(rows, pal)


def levitation_core():
    rows = [
        "....ooooo....",
        "..ooPPPPPoo..",
        ".oPPwwPPPPPo.",
        ".oPwWWPPPPPo.",
        "oPPwWPPPPQQPo",
        "oPPPPPPPQQQPo",
        "oPPPPPPQQQQPo",
        ".oPPPPQQQQPo.",
        ".oPPQQQQQQPo.",
        "..ooQQQQQoo..",
        "....ooooo....",
    ]
    pal = {"o": _hex("#1a0f33"), "P": _hex("#8a5ad8"), "Q": _hex("#4a2a8a"), "w": _hex("#d8c8ff"), "W": _hex("#ffffff")}
    img = _paint(rows, pal)
    for x, y in ((1, 1), (14, 2), (14, 13), (1, 12), (8, 0)):
        img.putpixel((x, y), _hex("#ffe36a"))
    return img


# ── mochilas ───────────────────────────────────────────────────

TIERS = {
    "backpack_leather": dict(main="#9a5a2e", flap="#b8743c", edge="#4a2812", strap="#6e3c1c", buckle="#b4b4bc", extra=None),
    "backpack_iron": dict(main="#7e838c", flap="#a2a8b2", edge="#2c2f36", strap="#565b64", buckle="#e0e2e8", extra="rivets"),
    "backpack_void": dict(main="#5a2f96", flap="#7646bd", edge="#1e0e3a", strap="#3f2070", buckle="#ffd24a", extra="gold"),
}


def backpack(tier: str):
    t = TIERS[tier]
    main, flap, edge = _hex(t["main"]), _hex(t["flap"]), _hex(t["edge"])
    strap, buckle = _hex(t["strap"]), _hex(t["buckle"])
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c

    # orelhas (alças de cima)
    for x0 in (3, 10):
        for y in range(1, 5):
            for x in range(x0, x0 + 3):
                put(x, y, edge if (y == 1 or x in (x0, x0 + 2)) else strap)
    # corpo
    for y in range(3, 15):
        for x in range(2, 14):
            corner = (x in (2, 13)) and (y in (3, 14))
            if corner:
                continue
            border = x in (2, 13) or y in (3, 14)
            shade = 1.12 - 0.03 * (x - 2) * 0.5 - 0.02 * (y - 3)
            put(x, y, edge if border else _shade(main, max(0.8, shade)))
    # aba (parte de cima) com bico no meio
    for y in range(4, 9):
        for x in range(3, 13):
            put(x, y, _shade(flap, 1.08 - 0.02 * (y - 4)))
    for x in range(3, 13):
        put(x, 9, edge)
    for x in (7, 8):
        put(x, 10, edge)
    # bolsos laterais
    for y in range(11, 14):
        for x in (3, 4, 11, 12):
            put(x, y, _shade(main, 0.78))
    for y in range(11, 14):
        put(5, y, edge)
        put(10, y, edge)
    # fivela e tira
    for y in range(7, 11):
        put(7, y, strap)
        put(8, y, strap)
    for y in (9, 10):
        for x in (7, 8):
            put(x, y, buckle)
    put(7, 9, _shade(buckle, 1.15))
    put(8, 10, _shade(buckle, 0.7))
    if t["extra"] == "rivets":
        for x, y in ((4, 5), (11, 5), (4, 8), (11, 8)):
            put(x, y, _hex("#f4f5f8"))
        for x in range(3, 13):
            put(x, 4, _shade(flap, 1.25))
    elif t["extra"] == "gold":
        gold = _hex("#ffd24a")
        for x in range(3, 13):
            put(x, 8, gold)
        for y in range(4, 9):
            put(3, y, gold)
            put(12, y, gold)
        put(7, 11, _hex("#e848e8"))
        put(8, 11, _hex("#e848e8"))
    return img


def backpack_worn(tier: str):
    """Textura 64x32 do modelo de armadura "humanoid": só a mochila nas costas, tiras na frente e laterais."""
    t = TIERS[tier]
    main, flap, edge = _hex(t["main"]), _hex(t["flap"]), _hex(t["edge"])
    strap, buckle = _hex(t["strap"]), _hex(t["buckle"])
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()

    def rect(x0, y0, w, h, c):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                px[x, y] = c

    # costas (32,20) 8x12
    bx, by = 32, 20
    rect(bx, by, 8, 12, _shade(main, 0.95))
    rect(bx, by, 8, 1, edge)
    rect(bx, by + 11, 8, 1, edge)
    rect(bx, by, 1, 12, edge)
    rect(bx + 7, by, 1, 12, edge)
    rect(bx + 1, by + 1, 6, 5, flap)                       # aba
    rect(bx + 1, by + 6, 6, 1, edge)
    rect(bx + 3, by + 6, 2, 3, strap)
    rect(bx + 3, by + 7, 2, 2, buckle)
    rect(bx + 1, by + 8, 2, 3, _shade(main, 0.75))         # bolsos
    rect(bx + 5, by + 8, 2, 3, _shade(main, 0.75))
    if t["extra"] == "gold":
        rect(bx + 1, by + 1, 6, 1, _hex("#ffd24a"))
    elif t["extra"] == "rivets":
        for x, y in ((1, 2), (6, 2)):
            px[bx + x, by + y] = _hex("#f4f5f8")
    # laterais: o volume da mochila na metade de trás
    rect(18, 21, 2, 9, _shade(main, 0.8))
    rect(28, 21, 2, 9, _shade(main, 0.8))
    rect(18, 21, 2, 1, edge)
    rect(28, 21, 2, 1, edge)
    # tiras na frente (20,20) e por cima dos ombros (20,16)
    rect(21, 20, 1, 9, strap)
    rect(26, 20, 1, 9, strap)
    rect(21, 16, 1, 4, strap)
    rect(26, 16, 1, 4, strap)
    return img


def build(ns, save, write_json):
    tex = ns / "textures"
    items = {
        "enderite_fragment": enderite_fragment(),
        "ethereal_membrane": ethereal_membrane(),
        "void_chitin": void_chitin(),
        "levitation_core": levitation_core(),
    }
    for tier in TIERS:
        items[tier] = backpack(tier)
    for key, image in items.items():
        save(image, tex / "item" / f"{key}.png")
        write_json(ns / "models" / "item" / f"{key}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"vertexx:item/{key}"}})
        write_json(ns / "items" / f"{key}.json", {"model": {"type": "minecraft:model", "model": f"vertexx:item/{key}"}})
    for tier in TIERS:
        save(backpack_worn(tier), tex / "entity" / "equipment" / "humanoid" / f"{tier}.png")
        write_json(ns / "equipment" / f"{tier}.json", {"layers": {"humanoid": [{"texture": f"vertexx:{tier}"}]}})
    return list(items)
