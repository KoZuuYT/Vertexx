"""Moobloom: vaca amarela coberta de botões-de-ouro (Mob Vote 2020), no corpo de uma vaca vanilla.

As medidas (``P``) partem do modelo da vaca e são refinadas contra a referência por ``fit_all``."""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import run  # noqa: E402
from vx.build import Builder  # noqa: E402

YELLOW = (255, 220, 0)
P = dict(leg_h=12.0, body_h=10.0, body_w=12.0, body_l=18.0, head=8.0, head_d=6.0)


def build(p=P):
    b = Builder("moobloom", res=(128, 128))
    lh, bh, bw, bl = p["leg_h"], p["body_h"], p["body_w"], p["body_l"]
    top = lh + bh
    zf = -bl / 2 + 1          # frente do corpo (vanilla: -8 com 18 de comprimento)
    zb = zf + bl
    b.bone("root", origin=(0, 0, 0))
    b.bone("body", "root", origin=(0, lh, 0))
    b.cube("body", "torso", (-bw / 2, lh, zf), (bw / 2, top, zb))
    hs, hd = p["head"], p["head_d"]
    b.bone("head", "body", origin=(0, top - 2, zf))
    b.cube("head", "skull", (-hs / 2, top - hs + 2, zf - hd), (hs / 2, top + 2, zf))
    for name, sx in (("horn_l", 1), ("horn_r", -1)):
        b.bone(name, "head", origin=(sx * hs / 2, top - 2, zf - 2))
        b.cube(name, name, (min(sx * hs / 2, sx * (hs / 2 + 1)), top + 2, zf - 3), (max(sx * hs / 2, sx * (hs / 2 + 1)), top + 5, zf - 2))
    for name, sx, z in (("leg_fl", 1, zf + 3), ("leg_fr", -1, zf + 3), ("leg_bl", 1, zb - 3), ("leg_br", -1, zb - 3)):
        x = sx * (bw / 2 - 2)
        b.bone(name, "root", origin=(x, lh, z))
        b.cube(name, name, (x - 2, 0, z - 2), (x + 2, lh, z + 2))
    # (x, y da base, z, altura da haste, altura total) medidos na referência por projeção inversa
    flowers = [(-1.6, 24, -10.3, 1, 6.0), (3.3, 22, 0.9, 1, 6.0), (-3.2, 22, -0.9, 1.5, 6.5), (0.3, 22, 7.0, 3, 7.5)]
    for i, (x, y, z, stem, total) in enumerate(flowers):
        name = f"flower{i}"
        b.bone(name, "body", origin=(x, y, z))
        petal = total - stem - 0.5
        b.cube(name, f"{name}_stem", (x - 0.5, y, z - 0.5), (x + 0.5, y + stem + 0.5, z + 0.5))
        b.cube(name, f"{name}_leaf_a", (x - 1, y + stem - 0.25, z - 0.25), (x + 1, y + stem + 0.75, z + 0.25))
        b.cube(name, f"{name}_leaf_b", (x - 0.25, y + stem - 0.25, z - 1), (x + 0.25, y + stem + 0.75, z + 1))
        b.cube(name, f"{name}_core", (x - 0.5, y + stem + 0.5, z - 0.5), (x + 0.5, y + stem + 0.5 + petal, z + 0.5))
        b.cube(name, f"{name}_petal_a", (x - 1.5, y + stem + 0.5, z - 0.25), (x + 1.5, y + stem + 0.5 + petal * 0.85, z + 0.25))
        b.cube(name, f"{name}_petal_b", (x - 0.25, y + stem + 0.5, z - 1.5), (x + 0.25, y + stem + 0.5 + petal * 0.85, z + 1))
    for cube in b.cubes.values():
        b.fill(cube, YELLOW)
    # ── animações ──
    walk = b.anim("walk", 1.0, "loop")
    for name, ph in (("leg_fl", 0), ("leg_br", 0), ("leg_fr", math.pi), ("leg_bl", math.pi)):
        walk.wave(name, "rot", 0, 28, phase=ph)
    walk.wave("head", "rot", 0, 4, phase=0.6)
    walk.wave("body", "pos", 1, 0.3, steps=4)
    for i in range(4):
        walk.wave(f"flower{i}", "rot", 2, 6, phase=i * 0.9)
    idle = b.anim("idle", 4.0, "loop")
    idle.wave("head", "rot", 0, 3, steps=8)
    idle.wave("head", "rot", 1, 5, phase=1.2, steps=8)
    for i in range(4):
        idle.wave(f"flower{i}", "rot", 2, 3.5, phase=i * 1.3)
    idle.wave("body", "scl", 1, 0.008, base=(1, 1, 1), steps=8)
    return b


if __name__ == "__main__":
    run("moobloom", build, ref="moobloom.png", shade=dict(ns=0.7, ew=0.47), params=dict(P), pairs=[("leg_fl", "leg_fr"), ("leg_bl", "leg_br"), ("leg_fl", "leg_bl"), ("horn_l", "horn_r")],
        fit_kw=dict(yaw_range=(40, 50), pitch_range=(26, 34)))
