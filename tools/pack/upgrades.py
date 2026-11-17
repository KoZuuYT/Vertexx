"""Faixas de metal dos baús e barris melhorados (um modelo por nível, 1 a 6).

``build(ns, save, write_json)`` escreve textures/block/band/<n>_{band,gem}.png, models/block/band/<n>.json e
items/chest_band_<n>.json. As cores seguem o material do nível (cobre, ouro, ametista, diamante, obsidiana,
netherite), na mesma ordem de ``upgrades.chest.tiers`` do config.yml.
"""
from __future__ import annotations

from PIL import Image

import enditems

# nível -> (cor escura, cor base, cor clara, cor da gema)
TIERS = {
    1: ("#6b3a1e", "#c8693f", "#f0a070", "#ffd9b0"),
    2: ("#8a6a10", "#f5c542", "#fff08a", "#ffffff"),
    3: ("#3b2270", "#8a58d0", "#c9a0ff", "#f0e0ff"),
    4: ("#0f6b66", "#2fd1c2", "#9ffff4", "#ffffff"),
    5: ("#0e0820", "#2a1a4a", "#5b3a9a", "#c95bff"),
    6: ("#1c1717", "#4a3f3f", "#7d6e6e", "#ff9a3c"),
}


def _band(dark, base, light):
    img = Image.new("RGBA", (16, 16), enditems._hex(base))
    px = img.load()
    for x in range(16):
        px[x, 0] = enditems._hex(light)       # borda de cima clara
        px[x, 15] = enditems._hex(dark)       # borda de baixo escura
        px[x, 1] = enditems._hex(light) if x % 4 else enditems._hex(base)
        px[x, 14] = enditems._hex(dark) if x % 4 else enditems._hex(base)
    for x in (2, 7, 12):                       # rebites
        px[x, 7] = enditems._hex(light)
        px[x, 8] = enditems._hex(dark)
    return img


def _gem(dark, base, light, gem):
    img = Image.new("RGBA", (16, 16), enditems._hex(dark))
    px = img.load()
    for y in range(2, 14):
        for x in range(2, 14):
            px[x, y] = enditems._hex(base)
    for y in range(5, 11):
        for x in range(5, 11):
            px[x, y] = enditems._hex(light)
    for y in range(6, 10):
        for x in range(6, 10):
            px[x, y] = enditems._hex(gem)
    return img


def _model(n):
    t = f"vertexx:block/band/{n}"

    def faces(tex, up=False):
        sides = {d: {"uv": [0, 0, 16, 16], "texture": tex} for d in ("north", "south", "east", "west")}
        if up:
            sides["up"] = {"uv": [0, 0, 16, 16], "texture": tex}
        return sides

    elements = [{"from": [-0.2, 5, -0.2], "to": [16.2, 8, 16.2], "faces": faces("#band")}]
    for (fx, fz, tx, tz) in ((6, -0.5, 10, 0), (6, 16, 10, 16.5)):               # gemas norte e sul
        elements.append({"from": [fx, 5.5, fz], "to": [tx, 7.5, tz], "faces": faces("#gem")})
    for (fx, fz, tx, tz) in ((-0.5, 6, 0, 10), (16, 6, 16.5, 10)):               # gemas oeste e leste
        elements.append({"from": [fx, 5.5, fz], "to": [tx, 7.5, tz], "faces": faces("#gem")})
    return {"textures": {"band": f"{t}_band", "gem": f"{t}_gem", "particle": f"{t}_band"}, "elements": elements}


def build(ns, save, write_json):
    for n, (dark, base, light, gem) in TIERS.items():
        folder = ns / "textures" / "block" / "band"
        save(_band(dark, base, light), folder / f"{n}_band.png")
        save(_gem(dark, base, light, gem), folder / f"{n}_gem.png")
        write_json(ns / "models" / "block" / "band" / f"{n}.json", _model(n))
        write_json(ns / "items" / f"chest_band_{n}.json",
                   {"model": {"type": "minecraft:model", "model": f"vertexx:block/band/{n}"}})
