#!/usr/bin/env python3
"""Cabeças de jogador a partir dos modelos: skin 64x64 (só a região da cabeça) para o skin patch do resource pack.

  python tools/models/heads.py            # gera models/heads/<modelo>.png do osso "head" de cada .bbmodel
  python tools/models/heads.py --pack     # copia models/heads/*.png para o pack e escreve heads.txt

Veja ``dev.vertexx.model.CustomHeads`` para o porquê de não usar texturas da Mojang."""
import argparse
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from vx.model import load_bbmodel, to_game  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
HEADS = ROOT / "models" / "heads"
# região de cada face na skin (cabeça 8x8x8 em 0,0): o jogador olha para o sul (+Z)
LAYOUT = {"up": (8, 0), "down": (16, 0), "west": (0, 8), "south": (8, 8), "east": (16, 8), "north": (24, 8)}


def skin_from_bone(model, bone_name="head"):
    gm = to_game(model)
    bone = next((b for b, _ in gm.bones() if b.name == bone_name and b.cubes), None)
    if bone is None:
        return None
    cube = max(bone.cubes, key=lambda c: (c.to[0] - c.frm[0]) * (c.to[1] - c.frm[1]) * (c.to[2] - c.frm[2]))
    tex = gm.textures[0].image()
    rw, rh = gm.res
    skin = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for face, (x, y) in LAYOUT.items():
        f = cube.faces.get(face)
        if f is None:
            continue
        u1, v1, u2, v2 = f.uv
        sx, sy = tex.width / rw, tex.height / rh
        region = tex.crop((round(min(u1, u2) * sx), round(min(v1, v2) * sy), round(max(u1, u2) * sx), round(max(v1, v2) * sy)))
        if u2 < u1:
            region = region.transpose(Image.FLIP_LEFT_RIGHT)
        if v2 < v1:
            region = region.transpose(Image.FLIP_TOP_BOTTOM)
        if f.rot:
            region = region.rotate(-f.rot, expand=True)
        skin.paste(region.resize((8, 8), Image.NEAREST), (x, y))
    return skin


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", action="store_true")
    ap.add_argument("--bone", default="head")
    args = ap.parse_args()
    if args.pack:
        names = pack(ROOT / "resourcepack" / "assets" / "vertexx")
        print("cabeças no pack:", ", ".join(names))
        return 0
    HEADS.mkdir(parents=True, exist_ok=True)
    for path in sorted((ROOT / "models" / "src").glob("*.bbmodel")):
        skin = skin_from_bone(load_bbmodel(path), args.bone)
        if skin is None:
            print(path.stem, "sem osso", args.bone)
            continue
        skin.save(HEADS / f"{path.stem}.png")
        print("gerada", HEADS / f"{path.stem}.png")
    return 0


def pack(ns_dir: Path):
    """Copia as skins para ``textures/entity/player/wide`` e grava a lista usada pelo plugin."""
    out = ns_dir / "textures" / "entity" / "player" / "wide"
    out.mkdir(parents=True, exist_ok=True)
    names = []
    for p in sorted(HEADS.glob("*.png")):
        Image.open(p).convert("RGBA").save(out / p.name)
        names.append(p.stem)
    (ROOT / "src" / "main" / "resources" / "heads.txt").write_text("\n".join(names) + "\n", encoding="utf-8")
    return names


if __name__ == "__main__":
    sys.exit(main())
