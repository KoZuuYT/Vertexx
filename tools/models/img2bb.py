#!/usr/bin/env python3
"""Imagem 2D → .bbmodel com cuboides fundidos, volume estimado e ossos com pivôs (ver ``vx/voxelize.py``).

    python tools/models/img2bb.py models/refs/penguin/a.png --name penguin_sprite
    python tools/models/img2bb.py arte.png --name estatua --depth 8 --levels 4 --cell 8 --out models/src

Gera ``<out>/<nome>.bbmodel`` (abre no Blockbench, já com a árvore de ossos) e ``models/preview/<nome>_sheet.png``.
Por padrão salva em ``models/generated`` (fora de ``models/src``, para não entrar no pack sem querer).
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "author"))

from kit import PREVIEW, sheet         # noqa: E402
from vx.lint import lint               # noqa: E402
from vx.model import save_bbmodel      # noqa: E402
from vx.voxelize import image_to_model  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument("--name", required=True)
    ap.add_argument("--out", default=str(HERE.parents[1] / "models" / "generated"))
    ap.add_argument("--cell", type=float, default=None, help="tamanho do pixel da arte (auto se omitido)")
    ap.add_argument("--depth", type=int, default=None, help="espessura máxima em texels (padrão: 40%% da largura)")
    ap.add_argument("--levels", type=int, default=3, help="níveis de profundidade (menos = menos cubos)")
    ap.add_argument("--erode", type=int, default=1, help="força da separação de partes nos gargalos")
    ap.add_argument("--max-size", type=int, default=48)
    a = ap.parse_args()
    b, stats = image_to_model(Path(a.image), a.name, a.cell, a.depth, a.levels, a.erode, a.max_size)
    model = b.finish()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    save_bbmodel(model, out / f"{a.name}.bbmodel")
    PREVIEW.mkdir(parents=True, exist_ok=True)
    sheet(model).save(PREVIEW / f"{a.name}_sheet.png")
    stats["lint"] = [m for _, m in lint(model, verbose=False)]
    print(json.dumps(stats, indent=1, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
