"""Rascal: pequeno ladrão das minas abandonadas, com uma caixa de madeira nas costas (Mob Vote 2022)."""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import run  # noqa: E402
from vx.build import Builder  # noqa: E402

TEAL = (50, 120, 112)
P = dict(leg_h=4.0, leg=3.5, body_w=9.0, body_h=6.0, body_l=10.0, head=8.0, head_w=8.0, head_y=3.0, crate_h=5.0, crate_l=7.0)


def build(p=P):
    b = Builder("rascal", res=(128, 128))
    lh, bw, bh, bl, hs = p["leg_h"], p["body_w"], p["body_h"], p["body_l"], p["head"]
    top = lh + bh
    zf, zb = -bl / 2, bl / 2
    b.bone("root", origin=(0, 0, 0))
    b.bone("body", "root", origin=(0, lh, 0))
    b.cube("body", "torso", (-bw / 2, lh, zf), (bw / 2, top, zb))
    hw, hy = p["head_w"], p["head_y"]
    b.bone("head", "body", origin=(0, hy + hs / 2, zf))
    b.cube("head", "skull", (-hw / 2, hy, zf - hs + 2), (hw / 2, hy + hs, zf + 2))
    b.bone("crate", "body", origin=(0, top, zb - p["crate_l"] / 2))
    b.cube("crate", "box", (-bw / 2 - 0.5, top, zb - p["crate_l"]), (bw / 2 + 0.5, top + p["crate_h"], zb + 0.5))
    b.bone("lid", "crate", origin=(0, top + p["crate_h"], zb - p["crate_l"]))
    b.cube("lid", "cloth", (-bw / 2 + 0.5, top + p["crate_h"], zb - p["crate_l"] + 0.5), (bw / 2 - 0.5, top + p["crate_h"] + 0.5, zb - 0.5))
    lg = p["leg"]
    for name, sx, z in (("leg_fl", 1, zf + lg / 2 + 0.5), ("leg_fr", -1, zf + lg / 2 + 0.5),
                        ("leg_bl", 1, zb - lg / 2 - 0.5), ("leg_br", -1, zb - lg / 2 - 0.5)):
        x = sx * (bw / 2 - lg / 2 + 0.25)
        b.bone(name, "root", origin=(x, lh, z))
        b.cube(name, name, (x - lg / 2, 0, z - lg / 2), (x + lg / 2, lh, z + lg / 2))
    for cube in b.cubes.values():
        b.fill(cube, TEAL)
    walk = b.anim("walk", 0.6, "loop")
    for name, ph in (("leg_fl", 0), ("leg_br", 0), ("leg_fr", math.pi), ("leg_bl", math.pi)):
        walk.wave(name, "rot", 0, 38, phase=ph)
    walk.wave("body", "pos", 1, 0.35, steps=4)
    walk.wave("head", "rot", 0, 5, phase=0.5)
    walk.wave("crate", "rot", 2, 3, phase=1.0)
    run_ = b.anim("flee", 0.4, "loop")
    for name, ph in (("leg_fl", 0), ("leg_br", 0), ("leg_fr", math.pi), ("leg_bl", math.pi)):
        run_.wave(name, "rot", 0, 55, phase=ph)
    run_.wave("body", "pos", 1, 0.6, steps=4)
    run_.wave("body", "rot", 0, 6, steps=4)
    run_.wave("crate", "rot", 2, 6, phase=1.0)
    idle = b.anim("idle", 3.0, "loop")
    idle.wave("head", "rot", 1, 10, steps=8)
    idle.wave("head", "rot", 0, 4, phase=1.0, steps=8)
    idle.wave("body", "scl", 1, 0.02, base=(1, 1, 1), steps=8)
    idle.wave("crate", "rot", 2, 1.5, steps=8)
    hide = b.anim("hide", 2.0, "loop")           # agachado, só a caixa balançando
    hide.wave("body", "pos", 1, 0.15, steps=8)
    hide.wave("crate", "rot", 2, 4, steps=8)
    hide.wave("head", "rot", 0, 6, phase=0.3, steps=8)
    return b


if __name__ == "__main__":
    run("rascal", build, ref="rascal.png", clip=[(190, 285, 299, 320)], shade=dict(ns=0.7, ew=0.47), params=dict(P),
        pairs=[("leg_fl", "leg_fr"), ("leg_bl", "leg_br"), ("leg_fl", "leg_bl")],
        fit_params=["leg_h", "leg", "body_w", "body_h", "body_l", "head", "head_w", "head_y", "crate_h", "crate_l"],
        bounds={"leg_h": (2, 8), "leg": (2, 6), "body_w": (6, 13), "body_h": (4, 10), "body_l": (7, 16), "head": (5, 11), "head_w": (6, 12), "head_y": (0, 6), "crate_h": (3, 9), "crate_l": (4, 12)},
        fit_kw=dict(yaw_range=(43, 47), pitch_range=(28, 32), rounds=24))
