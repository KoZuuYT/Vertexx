#!/usr/bin/env python3
"""Converte os modelos de ``models/src`` (.bbmodel, .geo.json) em assets do resource pack + dados de runtime.

    python tools/models/convert.py                 # gera em resourcepack/ e src/main/resources/models/
    python tools/models/convert.py --list          # lista modelos, ossos, cubos e animações

Chamado também por ``tools/pack/build.py`` (o pack é recriado a cada build)."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from vx.geo import load_geo            # noqa: E402
from vx.model import load_bbmodel      # noqa: E402
from vx.pack import compile_model      # noqa: E402

ROOT = HERE.parents[1]
SRC = ROOT / "models" / "src"
RUNTIME = ROOT / "src" / "main" / "resources" / "models"
# escala padrão (blocos por modelo de 16 px) de cada modelo; o plugin ainda pode sobrescrever
SCALES = {"wildfire": 2.6, "void_scarab": 3.4, "parched_titan": 0.55, "root_sovereign": 0.5, "iceologer": 1.45, "rascal": 0.75, "tuff_golem": 1.0, "moobloom": 1.0, "glare": 0.8,
          "crab": 0.8, "penguin": 0.85, "barnacle": 1.5,
          **{f"great_hunger_{v}": 0.9 for v in ("dirt", "sand", "wood", "stone", "amethyst", "grass")}}


def sources():
    for p in sorted(SRC.glob("*.bbmodel")):
        yield p.stem, load_bbmodel(p)
    for p in sorted(SRC.glob("*.geo.json")):
        name = p.name.replace(".geo.json", "")
        yield name, load_geo(p, SRC / f"{name}.animation.json")


def build(ns_dir: Path):
    out = []
    for name, model in sources():
        out.append((name, compile_model(model, name, ns_dir, RUNTIME, SCALES.get(name, 1.0))))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--out", default=str(ROOT / "resourcepack" / "assets" / "vertexx"))
    args = ap.parse_args()
    if args.list:
        for name, m in sources():
            n_cubes = sum(len(b.cubes) for b, _ in m.bones())
            print(f"{name}: formato={m.fmt} ossos={len(m.bones())} cubos={n_cubes} animações={[a.name for a in m.anims]}")
        return 0
    for name, rt in build(Path(args.out)):
        n_models = sum(1 for b in rt["bones"] if b["m"])
        print(f"{name}: {len(rt['bones'])} ossos ({n_models} com modelo), {len(rt['anim'])} animações, {len(rt['loc'])} localizadores")
    return 0


if __name__ == "__main__":
    sys.exit(main())
