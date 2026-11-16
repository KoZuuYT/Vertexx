"""Blocos customizados do Vertexx (estados do note block), texturas 16x16 e modelos.

``build(ns, save, write_json)`` escreve, para cada bloco:
  textures/block/<id>.png, models/block/<id>.json, items/<id>.json (item_model do item que coloca o bloco)
e o ``assets/minecraft/blockstates/note_block.json`` completo (as combinações não reservadas continuam vanilla).

Os estados reservados são ``instrument=custom_head,note=<n>,powered=false`` (só aparecem no vanilla com uma
cabeça de jogador em cima do note block). O índice ``n`` TEM de bater com CustomBlock.java.
"""
from __future__ import annotations

import random

from PIL import Image

import enditems

# id -> nota do estado (bate com dev.vertexx.block.CustomBlock)
BLOCKS = {"enderite_ore": 0, "enderite_block": 1}

INSTRUMENTS = ["harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone",
               "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "zombie", "skeleton",
               "creeper", "dragon", "wither_skeleton", "piglin", "custom_head"]

VEIN = ["#5a3a8c", "#b04ad8", "#ff7af0"]


def _vein(img, rng, count, size):
    px = img.load()
    for _ in range(count):
        cx, cy = rng.randint(1, 14), rng.randint(1, 14)
        for dx in range(size):
            for dy in range(size):
                if rng.random() < 0.8 and cx + dx < 16 and cy + dy < 16:
                    px[cx + dx, cy + dy] = enditems._hex(rng.choice(VEIN))


def enderite_ore():
    img = enditems.ramp_recolor(enditems.vanilla("deepslate"), ["#0a0612", "#1a1226", "#2a2038", "#3c3050"])
    _vein(img, random.Random(7), 6, 2)
    return img


def enderite_block():
    img = enditems.ramp_recolor(enditems.vanilla("cobbled_deepslate"), ["#08040f", "#170c2a", "#2c1850", "#4a2a80"])
    px = img.load()
    for i in range(16):                                    # moldura
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = enditems._hex("#10081e")
    rng = random.Random(11)
    for _ in range(14):                                    # rachaduras brilhantes
        x, y = rng.randint(2, 13), rng.randint(2, 13)
        px[x, y] = enditems._hex(rng.choice(VEIN))
        if rng.random() < 0.6 and x + 1 < 15:
            px[x + 1, y + 1] = enditems._hex(VEIN[0])
    return img


TEXTURES = {"enderite_ore": enderite_ore, "enderite_block": enderite_block}


def build(ns, save, write_json):
    for block, draw in TEXTURES.items():
        save(draw(), ns / "textures" / "block" / f"{block}.png")
        write_json(ns / "models" / "block" / f"{block}.json",
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"vertexx:block/{block}"}})
        write_json(ns / "items" / f"{block}.json",
                   {"model": {"type": "minecraft:model", "model": f"vertexx:block/{block}"}})
    by_note = {note: block for block, note in BLOCKS.items()}
    variants = {}
    for instrument in INSTRUMENTS:
        for note in range(25):
            for powered in ("false", "true"):
                key = f"instrument={instrument},note={note},powered={powered}"
                if instrument == "custom_head" and powered == "false" and note in by_note:
                    variants[key] = {"model": f"vertexx:block/{by_note[note]}"}
                else:
                    variants[key] = {"model": "minecraft:block/note_block"}
    write_json(ns.parent / "minecraft" / "blockstates" / "note_block.json", {"variants": dict(sorted(variants.items()))})
