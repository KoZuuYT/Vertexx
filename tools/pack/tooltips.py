"""Tooltip especial do Vertexx (fundo e moldura de 9 fatias), escolhida pelo componente tooltip_style
``vertexx:special`` nos itens especiais e nos itens dos menus.

``build(ns, save, write_json)`` escreve textures/gui/sprites/tooltip/special_{background,frame}.png e os .mcmeta.
O tamanho 24x24 com borda 8 mantém os ornamentos dos cantos do tamanho certo em qualquer tooltip.
"""
from __future__ import annotations

from PIL import Image

import enditems

SIZE = 24
BORDER = 8


def _background():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    fill = (14, 8, 26, 244)
    edge = (28, 16, 52, 250)
    for y in range(SIZE):
        for x in range(SIZE):
            inner = 2 <= x < SIZE - 2 and 2 <= y < SIZE - 2
            if 1 <= x < SIZE - 1 and 1 <= y < SIZE - 1:
                px[x, y] = fill if inner else edge
    return img


def _frame():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    top = enditems._hex("#7f5af0")
    bottom = enditems._hex("#2cb67d")
    bright = enditems._hex("#e9ddff")
    dim = enditems._hex("#3a2a78")
    for i in range(SIZE):
        for edge in ((i, 1), (i, SIZE - 2), (1, i), (SIZE - 2, i)):
            x, y = edge
            t = y / (SIZE - 1)
            px[x, y] = tuple(int(top[k] + (bottom[k] - top[k]) * t) for k in range(3)) + (255,)
        for edge in ((i, 0), (i, SIZE - 1), (0, i), (SIZE - 1, i)):
            px[edge] = dim
    # cantos ornamentados: um "L" mais grosso e um brilho no vértice
    for cx, cy, sx, sy in ((1, 1, 1, 1), (SIZE - 2, 1, -1, 1), (1, SIZE - 2, 1, -1), (SIZE - 2, SIZE - 2, -1, -1)):
        for k in range(0, 6):
            for w in (0, 1):
                px[cx + sx * k, cy + sy * w] = top if cy < SIZE // 2 else bottom
                px[cx + sx * w, cy + sy * k] = top if cy < SIZE // 2 else bottom
        px[cx, cy] = bright
        px[cx + sx * 2, cy + sy * 2] = bright
    return img


def build(ns, save, write_json):
    base = ns / "textures" / "gui" / "sprites" / "tooltip"
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": SIZE, "height": SIZE, "border": BORDER}}}
    for name, img in (("special_background", _background()), ("special_frame", _frame())):
        save(img, base / f"{name}.png")
        write_json(base / f"{name}.png.mcmeta", meta)
