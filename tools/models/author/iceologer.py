"""Iceologer: mago ilhager de manto azul e capuz, com cajado de gelo."""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import run  # noqa: E402
from vx.build import Builder  # noqa: E402

BLUE = (22, 44, 110)
P = dict(robe_w=9.0, robe_h=19.0, robe_d=7.0, hood=10.0, hood_h=10.0, head=8.0, arm=4.0, arm_l=9.0, boot=3.0,
         arm_l_x=-50.0, arm_r_x=-50.0)


def build(p=P):
    b = Builder("iceologer", res=(128, 128))
    bt = p["boot"]
    rw, rh, rd = p["robe_w"], p["robe_h"], p["robe_d"]
    top = bt + rh
    b.bone("root", origin=(0, 0, 0))
    b.bone("body", "root", origin=(0, bt + rh * 0.5, 0))
    b.cube("body", "robe", (-rw / 2, bt, -rd / 2), (rw / 2, top, rd / 2))
    b.bone("legs", "root", origin=(0, bt, 0))
    b.cube("legs", "boot_l", (-3.5, 0, -2), (-0.5, bt, 2))
    b.cube("legs", "boot_r", (0.5, 0, -2), (3.5, bt, 2))
    hs, hh = p["head"], p["hood_h"]
    b.bone("head", "body", origin=(0, top - 1, 0))
    b.cube("head", "face", (-hs / 2, top - 1, -hs / 2), (hs / 2, top - 1 + hs + 1, hs / 2))
    hd = p["hood"]
    b.cube("head", "hood", (-hd / 2, top - 1, -hd / 2 + 1), (hd / 2, top - 1 + hh + 1, hd / 2 + 1), inflate=0.0)
    b.cube("head", "nose", (-1, top + 2, -hs / 2 - 2), (1, top + 5, -hs / 2))
    aw, al = p["arm"], p["arm_l"]
    for name, sx in (("arm_l", 1), ("arm_r", -1)):
        b.bone(name, "body", origin=(sx * (rw / 2 + aw / 2 - 0.5), top - 2, 0), rot=(p["arm_l_x" if sx > 0 else "arm_r_x"], 0, 0))
        x0 = sx * (rw / 2 - 0.5)
        b.cube(name, name, (min(x0, x0 + sx * aw), top - 2 - al, -aw / 2), (max(x0, x0 + sx * aw), top - 2, aw / 2))
    b.locator("arm_r", "staff", (-(rw / 2 + aw / 2), top - 2 - al + 1, -aw / 2 - 1))
    b.locator("head", "eyes", (0, top + 4, -hs / 2))
    for cube in b.cubes.values():
        b.fill(cube, BLUE)
    idle = b.anim("idle", 3.0, "loop")
    idle.wave("body", "pos", 1, 0.4, steps=6)
    idle.wave("head", "rot", 1, 6, steps=6)
    idle.wave("arm_l", "rot", 0, 3, phase=0.5, steps=6)
    idle.wave("arm_r", "rot", 0, 3, phase=1.5, steps=6)
    walk = b.anim("walk", 0.9, "loop")
    walk.wave("legs", "pos", 1, 0.25, steps=4)
    walk.wave("body", "rot", 2, 2.5, steps=4)
    walk.wave("arm_l", "rot", 0, 8, phase=0.8, steps=4)
    walk.wave("arm_r", "rot", 0, 8, phase=0, steps=4)
    walk.wave("head", "rot", 1, 4, phase=1.0, steps=4)
    cast = b.anim("cast", 1.0, "loop")
    for n, sx in (("arm_l", 1), ("arm_r", -1)):
        cast.key(n, "rot", 0.0, (-150, 0, sx * 8)).key(n, "rot", 0.5, (-165, 0, sx * 14), "catmullrom").key(n, "rot", 1.0, (-150, 0, sx * 8))
    cast.wave("head", "rot", 0, 6, steps=4)
    cast.wave("body", "pos", 1, 0.3, steps=4)
    summon = b.anim("summon", 1.4, "hold")
    for n, sx in (("arm_l", 1), ("arm_r", -1)):
        summon.key(n, "rot", 0.0, (-50, 0, 0)).key(n, "rot", 0.5, (-170, 0, sx * 20)).key(n, "rot", 1.0, (-110, 0, sx * 6)).key(n, "rot", 1.4, (-150, 0, sx * 12))
    summon.key("head", "rot", 0.0, (0, 0, 0)).key("head", "rot", 0.5, (-18, 0, 0)).key("head", "rot", 1.4, (-8, 0, 0))
    death = b.anim("death", 1.6, "hold")
    death.key("body", "rot", 0.0, (0, 0, 0)).key("body", "rot", 1.6, (-85, 0, 0), "catmullrom")
    death.key("body", "pos", 0.0, (0, 0, 0)).key("body", "pos", 1.6, (0, -9, -9))
    for n in ("arm_l", "arm_r"):
        death.key(n, "rot", 0.0, (-50, 0, 0)).key(n, "rot", 1.6, (-20, 0, 25))
    return b


if __name__ == "__main__":
    run("iceologer", build, ref="iceologer.png", shade=dict(ns=0.82, ew=0.6), params=dict(P),
        pairs=[("boot_l", "boot_r"), ("arm_l", "arm_r")], final=dict(arm_l_x=0, arm_r_x=0),
        fit_params=["robe_w", "robe_h", "robe_d", "hood", "hood_h", "head", "arm", "arm_l", "boot", "arm_l_x", "arm_r_x"],
        steps={"arm_l_x": 8, "arm_r_x": 8, "robe_h": 1.5},
        bounds={"robe_w": (7, 12), "robe_h": (12, 26), "robe_d": (5, 10), "hood": (8, 13), "hood_h": (8, 14), "head": (7, 10), "arm": (3, 6), "arm_l": (6, 14), "boot": (2, 6), "arm_l_x": (-120, 20), "arm_r_x": (-120, 20)},
        fit_kw=dict(yaw_range=(0, 55), pitch_range=(10, 35), rounds=20))
