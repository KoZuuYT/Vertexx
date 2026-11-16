"""Arraia do Vazio (Void Ray): água-viva voadora, orbe lilás/turquesa, quatro asas e tentáculos. Passiva.
Referência: concept art da End Update (PDF)."""
from kit import make
from style import R, Designer

TOP = R("#4a3a98", "#7a62d0", "#a58df0", "#c8b8ff", "#ddd0ff")
BOT = R("#0e4a58", "#17707c", "#209aa0", "#40c0bc")
WING = R("#14246a", "#1f3a9a", "#3a4fd0", "#6a5ae0", "#a890f8", "#8ae8ff")
TENT = R("#5a6ab0", "#8a9ad8", "#b0c0f0", "#d8e4ff")

P = dict(orb=9.0, wing=11.0)


def orb(cv):
    if cv.face == "up":
        cv.noise(TOP, 4.0, 0.4)
    elif cv.face == "down":
        cv.noise(BOT, 1.5, 0.4)
    else:
        cv.noise(BOT, 1.8, 0.4)
        cv.vgrad(TOP, 3.5, 1.0, 0.5, j1=max(1, cv.h // 3))


def build(p=P):
    b = Designer("void_ray", res=(128, 64))
    o, wl = p["orb"], p["wing"]
    y0 = 10.0
    b.bone("root", origin=(0, y0, 0))
    b.bone("body", "root", origin=(0, y0 + o / 2, 0))
    b.box("body", "orb", (o, o, o), (0, y0, 0))
    b.skin("orb", orb)
    b.box("body", "orb_lid", (o - 3, 1, o - 3), (0, y0 - 1, 0))
    b.skin("orb_lid", lambda cv: cv.noise(BOT, 1.2, 0.4))
    for side, sx in (("l", 1), ("r", -1)):
        for k, (dz, ang) in enumerate(((-2.0, 18), (2.0, -14))):
            name = f"wing{k}_{side}"
            b.bone(name, "body", origin=(sx * o / 2, y0 + o * 0.7, dz), rot=(0, sx * ang, sx * 18))
            x0 = o / 2 if sx > 0 else -o / 2 - wl
            b.box(name, name, (wl, 1.0, 3.6), (x0, y0 + o * 0.7, dz - 1.8), anchor="corner")
            b.skin(name, lambda cv: cv.noise(WING, 2.6 if cv.face == "up" else 1.8, 0.7))
    for i, (x, z, hgt) in enumerate(((-2.5, -2, 9), (2.5, -2, 7), (0, 0, 10), (-2.5, 2.5, 8), (2.5, 2.5, 9), (0, -3, 6))):
        n = f"tent{i}"
        b.bone(n, "body", origin=(x, y0 - 1, z))
        b.box(n, n, (1, hgt, 1), (x, y0 - 1 - hgt, z))
        b.skin(n, lambda cv: cv.vgrad(TENT, 3, 0, 0.6))
    idle = b.anim("idle", 2.4, "loop")
    idle.wave("root", "pos", 1, 1.2, steps=8)
    for side, sx in (("l", 1), ("r", -1)):
        for k in range(2):
            idle.wave(f"wing{k}_{side}", "rot", 2, sx * 14, phase=k * 0.6, steps=8)
    for i in range(6):
        idle.orbit(f"tent{i}", "rot", (7, 0, 6), (i * 0.9, 0, i * 1.3), steps=8)
    fly = b.anim("fly", 1.2, "loop")
    fly.wave("root", "pos", 1, 0.8, steps=8)
    for side, sx in (("l", 1), ("r", -1)):
        for k in range(2):
            fly.wave(f"wing{k}_{side}", "rot", 2, sx * 26, phase=k * 0.5, steps=8)
    for i in range(6):
        fly.orbit(f"tent{i}", "rot", (14, 0, 6), (i * 0.9, 0, i * 1.3), steps=8)
    return b


if __name__ == "__main__":
    make("void_ray", build, P, {}, anim="idle")
