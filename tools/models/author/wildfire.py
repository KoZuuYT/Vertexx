"""Wildfire: chama gigante de quatro lâminas, cabeça de ferro escuro com um olho vermelho."""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import run  # noqa: E402
from vx.build import Builder  # noqa: E402

GOLD = (235, 200, 40)
P = dict(core_w=4.0, core_h=14.5, head=7.5, panel_w=7.0, panel_h=16.4, panel_t=2.0, off=7.0, panel_y=1.0, tilt=16.0, yaw=17.5)


def build(p=P):
    b = Builder("wildfire", res=(128, 128))
    cw, ch = p["core_w"], p["core_h"]
    b.bone("root", origin=(0, 0, 0))
    b.bone("core", "root", origin=(0, 2, 0))
    b.cube("core", "core", (-cw / 2, 2, -cw / 2), (cw / 2, 2 + ch, cw / 2))
    hs = p["head"]
    top = 2 + ch
    b.bone("head", "core", origin=(0, top + hs / 2 - 1, 0))
    b.cube("head", "skull", (-hs / 2, top - 1, -hs / 2), (hs / 2, top - 1 + hs, hs / 2))
    b.bone("eye_glow", "head", origin=(0, top + hs / 2 - 1, -hs / 2))
    b.cube("eye_glow", "eye", (-1.5, top + hs / 2 - 2, -hs / 2 - 0.05), (1.5, top + hs / 2, -hs / 2))
    pw, ph, pt, off, py = p["panel_w"], p["panel_h"], p["panel_t"], p["off"], p["panel_y"]
    for name, sx, sz in (("blade_fl", -1, -1), ("blade_fr", 1, -1), ("blade_bl", -1, 1), ("blade_br", 1, 1)):
        x, z = sx * off * 0.7, sz * off * 0.7
        yaw = math.degrees(math.atan2(sx, -sz)) * 1.0
        ry = (p["yaw"] if sx * sz < 0 else -p["yaw"]) if False else 0
        b.bone(name, "core", origin=(x, py + ph / 2, z), rot=(0, sx * sz * -p["yaw"], sx * p["tilt"]))
        b.cube(name, name, (x - pw / 2, py, z - pt / 2), (x + pw / 2, py + ph, z + pt / 2))
    for cube in b.cubes.values():
        b.fill(cube, GOLD)
    idle = b.anim("idle", 2.4, "loop")
    idle.wave("core", "pos", 1, 0.7, steps=6)
    for i, n in enumerate(("blade_fl", "blade_fr", "blade_bl", "blade_br")):
        idle.wave(n, "rot", 2, 3, phase=i * 1.6, steps=6)
        idle.wave(n, "rot", 0, 2, phase=i * 0.9, steps=6)
    idle.wave("head", "rot", 1, 6, steps=6)
    walk = b.anim("walk", 1.0, "loop")
    walk.wave("core", "pos", 1, 0.6, steps=4)
    walk.wave("core", "rot", 0, 6, steps=4)
    for i, n in enumerate(("blade_fl", "blade_fr", "blade_bl", "blade_br")):
        walk.wave(n, "rot", 0, 12, phase=i * math.pi / 2, steps=8)
    walk.wave("head", "rot", 0, 4, phase=1.0, steps=4)
    spin = b.anim("spin", 0.8, "loop")        # o corpo gira; as lâminas orbitam presas pelo código
    spin.key("core", "rot", 0.0, (0, 0, 0)).key("core", "rot", 0.8, (0, 360, 0))
    spin.wave("head", "rot", 0, 5, steps=4)
    windup = b.anim("windup", 1.0, "hold")    # recua e abre as lâminas antes do bote
    for i, n in enumerate(("blade_fl", "blade_fr", "blade_bl", "blade_br")):
        windup.key(n, "rot", 0.0, (0, 0, 0)).key(n, "rot", 1.0, (-14, 0, 22), "catmullrom")
    windup.key("core", "rot", 0.0, (0, 0, 0)).key("core", "rot", 1.0, (-14, 0, 0))
    windup.key("head", "rot", 0.0, (0, 0, 0)).key("head", "rot", 1.0, (10, 0, 0))
    lunge = b.anim("lunge", 0.5, "loop")
    for n in ("blade_fl", "blade_fr", "blade_bl", "blade_br"):
        lunge.key(n, "rot", 0.0, (30, 0, 6)).key(n, "rot", 0.5, (30, 0, 6))
    lunge.key("core", "rot", 0.0, (28, 0, 0)).key("core", "rot", 0.5, (28, 0, 0))
    death = b.anim("death", 2.0, "hold")
    death.key("core", "rot", 0.0, (0, 0, 0)).key("core", "rot", 2.0, (80, 0, 12), "catmullrom")
    death.key("core", "pos", 0.0, (0, 0, 0)).key("core", "pos", 2.0, (0, -8, 0))
    for n in ("blade_fl", "blade_fr", "blade_bl", "blade_br"):
        death.key(n, "rot", 0.0, (0, 0, 0)).key(n, "rot", 2.0, (0, 0, 55))
    return b


if __name__ == "__main__":
    run("wildfire", build, ref="wildfire.png", shade=dict(ns=0.8, ew=0.6), params=dict(P),
        pairs=[("blade_fl", "blade_fr"), ("blade_bl", "blade_br"), ("blade_fl", "blade_bl")],
        fit_kw=dict(yaw_range=(-25, 25), pitch_range=(22, 38)))
