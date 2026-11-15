"""Glare: criatura de folhagem das cavernas exuberantes (Mob Vote 2021)."""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import run  # noqa: E402
from vx.build import Builder  # noqa: E402

MOSS = (92, 120, 52)
P = dict(head=13.0, l1=12.0, l1h=4.0, l2=10.0, l2h=5.0, root=4.0)
STRANDS = [(-4, -3, 5), (3, -4, 4), (-1, 4, 6), (4, 3, 3), (-3, 1, 5), (0, -1, 6), (2, 0, 4)]


def build(p=P):
    b = Builder("glare", res=(128, 128))
    H = p["head"]
    y0 = p["root"] + p["l2h"] + p["l1h"]
    b.bone("root", origin=(0, y0, 0))
    b.bone("head", "root", origin=(0, y0 + H / 2, 0))
    b.cube("head", "skull", (-H / 2, y0, -H / 2), (H / 2, y0 + H, H / 2))
    b.bone("brow_glow", "head", origin=(0, y0 + H * 0.6, -H / 2))
    b.cube("brow_glow", "eye_l", (1.2, y0 + H * 0.5, -H / 2 - 0.05), (4.2, y0 + H * 0.5 + 2.4, -H / 2))
    b.cube("brow_glow", "eye_r", (-4.2, y0 + H * 0.5, -H / 2 - 0.05), (-1.2, y0 + H * 0.5 + 2.4, -H / 2))
    b.bone("skirt1", "root", origin=(0, y0, 0))
    b.cube("skirt1", "layer1", (-p["l1"] / 2, y0 - p["l1h"], -p["l1"] / 2), (p["l1"] / 2, y0, p["l1"] / 2))
    b.bone("skirt2", "skirt1", origin=(0, y0 - p["l1h"], 0))
    y2 = y0 - p["l1h"]
    b.cube("skirt2", "layer2", (-p["l2"] / 2, y2 - p["l2h"], -p["l2"] / 2), (p["l2"] / 2, y2, p["l2"] / 2))
    y3 = y2 - p["l2h"]
    for i, (x, z, h) in enumerate(STRANDS):
        name = f"root{i}"
        b.bone(name, "skirt2", origin=(x, y3 + 1, z))
        b.cube(name, name, (x - 0.75, y3 + 1 - h, z - 0.75), (x + 0.75, y3 + 1, z + 0.75))
    for cube in b.cubes.values():
        b.fill(cube, MOSS)
    idle = b.anim("idle", 3.0, "loop")
    idle.wave("root", "pos", 1, 0.6, steps=6)
    idle.wave("skirt1", "rot", 2, 2.5, steps=6)
    idle.wave("skirt2", "rot", 0, 3, phase=1.0, steps=6)
    for i in range(len(STRANDS)):
        idle.wave(f"root{i}", "rot", 0, 6, phase=i * 0.9, steps=6)
        idle.wave(f"root{i}", "rot", 2, 5, phase=i * 1.4 + 0.5, steps=6)
    angry = b.anim("angry", 1.0, "loop")
    angry.wave("head", "rot", 2, 4, steps=8)
    angry.wave("head", "pos", 0, 0.35, steps=8)
    angry.wave("root", "pos", 1, 0.5, steps=4)
    for i in range(len(STRANDS)):
        angry.wave(f"root{i}", "rot", 0, 12, phase=i * 0.7, steps=8)
        angry.wave(f"root{i}", "rot", 2, 10, phase=i * 1.1, steps=8)
    return b


if __name__ == "__main__":
    run("glare", build, ref="glare.png", shade=dict(ns=0.9, ew=0.7), params=dict(P),
        fit_params=["head", "l1", "l1h", "l2", "l2h", "root"], steps={"head": 1, "l1": 1, "l2": 1},
        bounds={"head": (9, 16), "l1": (8, 15), "l1h": (2, 9), "l2": (5, 14), "l2h": (2, 9), "root": (2, 8)},
        fit_kw=dict(yaw_range=(-85, 10), pitch_range=(0, 30)))
