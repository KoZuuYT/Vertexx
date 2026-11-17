"""Itens da End Update (texturas 16x16, modelos de item) e as texturas das mochilas vestidas no peito.

``build(ns, save, write_json)`` escreve:
  textures/item/<id>.png, models/item/<id>.json, items/<id>.json    (um por item)
  equipment/<id>.json e textures/entity/equipment/humanoid/<id>.png  (mochilas: o modelo de armadura nas costas)
"""
from __future__ import annotations

from pathlib import Path

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


# ── texturas vanilla recoloridas ───────────────────────────────

VANILLA = Path(__file__).resolve().parent / "vanilla"


def vanilla(name: str) -> Image.Image:
    return Image.open(VANILLA / f"{name}.png").convert("RGBA")


def ramp_recolor(img: Image.Image, colors) -> Image.Image:
    """Troca as cores pela rampa ``colors`` (escura -> clara) segundo a luminância; o alfa é mantido."""
    ramp = [_hex(c) for c in colors]
    out = img.copy()
    px = out.load()
    lums = [0.299 * px[x, y][0] + 0.587 * px[x, y][1] + 0.114 * px[x, y][2]
            for y in range(img.height) for x in range(img.width) if px[x, y][3] > 0]
    lo, hi = min(lums), max(lums)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            t = (0.299 * r + 0.587 * g + 0.114 * b - lo) / max(1e-6, hi - lo)
            f = t * (len(ramp) - 1)
            i = min(len(ramp) - 2, int(f))
            u = f - i
            c0, c1 = ramp[i], ramp[i + 1]
            px[x, y] = tuple(int(c0[k] + (c1[k] - c0[k]) * u) for k in range(3)) + (a,)
    return out


def enderite_fragment():
    return ramp_recolor(vanilla("netherite_scrap"), ["#0e0818", "#2a1a45", "#5a3a8c", "#b04ad8", "#ff7af0"])


def ethereal_membrane():
    return ramp_recolor(vanilla("phantom_membrane"), ["#2a3a88", "#5a6ad8", "#9a8cf0", "#bff0ff", "#ffffff"])


def void_chitin():
    return ramp_recolor(vanilla("armadillo_scute"), ["#07050a", "#15101f", "#2b2142", "#4d3a7a", "#9a58d8"])


def levitation_core():
    img = ramp_recolor(vanilla("heart_of_the_sea"), ["#1a0f33", "#4a2a8a", "#8a5ad8", "#d8c8ff", "#fff6c8"])
    for x, y in ((1, 2), (14, 3), (13, 13), (2, 12)):
        if img.getpixel((x, y))[3] == 0:
            img.putpixel((x, y), _hex("#ffe36a"))
    return img


def wildfire_core():
    return ramp_recolor(vanilla("nether_star"), ["#3a0a00", "#8a2200", "#d85a00", "#ffa31a", "#fff2a0"])


def iceologer_core():
    return ramp_recolor(vanilla("nether_star"), ["#06203a", "#1a5a9a", "#4aa8e0", "#a8e8ff", "#ffffff"])


def warp_stone():
    return ramp_recolor(vanilla("ender_eye"), ["#1e0d36", "#4b2a8a", "#8a58d0", "#c9a0ff", "#ffffff"])


def amber_fragment():
    return ramp_recolor(vanilla("honeycomb"), ["#4a2200", "#a8560a", "#e8920f", "#ffc23a", "#fff0a0"])


def calcified_fossil():
    return ramp_recolor(vanilla("bone"), ["#262220", "#4a423c", "#7a6e62", "#a89c8a", "#d8ccb4"])


def termite_larva():
    return ramp_recolor(vanilla("slime_ball"), ["#5a4a30", "#9a8460", "#d8c496", "#f2e6c0", "#fffbe8"])


def coconut():
    return ramp_recolor(vanilla("cocoa_beans"), ["#1c0f06", "#4a2a12", "#7a4a22", "#e8e0d0", "#ffffff"])


def ostrich_egg():
    img = ramp_recolor(vanilla("egg"), ["#8a7a5a", "#c9b98c", "#e8dcb8", "#f6efd6", "#ffffff"])
    return img.resize((16, 16))


# ── mochilas ───────────────────────────────────────────────────

TIERS = {
    "backpack_leather": dict(main="#9a5a2e", flap="#b8743c", edge="#4a2812", strap="#6e3c1c", buckle="#b4b4bc", extra=None),
    "backpack_iron": dict(main="#7e838c", flap="#a2a8b2", edge="#2c2f36", strap="#565b64", buckle="#e0e2e8", extra="rivets"),
    "backpack_void": dict(main="#5a2f96", flap="#7646bd", edge="#1e0e3a", strap="#3f2070", buckle="#ffd24a", extra="gold"),
}


def backpack(tier: str):
    base = vanilla("bundle")
    if tier == "backpack_leather":
        return base
    if tier == "backpack_iron":
        return ramp_recolor(base, ["#1c1f26", "#4a505a", "#7e838c", "#b6bcc6", "#eef0f4"])
    img = ramp_recolor(base, ["#0e0620", "#2a1458", "#5a2f96", "#9a6ae0", "#e0c8ff"])
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):          # fecho dourado
        if img.getpixel((x, y))[3] > 0:
            img.putpixel((x, y), _hex("#ffd24a") if (x + y) % 2 else _hex("#c8981c"))
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
        "wildfire_core": wildfire_core(),
        "iceologer_core": iceologer_core(),
        "warp_stone": warp_stone(),
        "amber_fragment": amber_fragment(),
        "calcified_fossil": calcified_fossil(),
        "termite_larva": termite_larva(),
        "coconut": coconut(),
        "ostrich_egg": ostrich_egg(),
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
