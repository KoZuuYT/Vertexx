"""Rastejador do Fim (End Crawler): besouro de carapaça de obsidiana com olhos magenta. Neutro; enrola-se ao receber golpe crítico.
Referência: concept art da End Update (PDF)."""
import math

from kit import make
from style import R, Designer, rgb

OBS = R("#07050c", "#100c1a", "#1a1428", "#271d3c", "#382a58", "#4d3a78")
VEIN = rgb("#7b3fc4")
EYE = R("#7a1a78", "#c030c0", "#ff4ff0", "#ffc0fb")
LEG = R("#0a0810", "#14101e", "#1e1830")

P = dict(w=12.0, h=5.0, d=14.0, lift=3.0, leg=5.0)


def shell(cv, base=2.6):
    cv.noise(OBS, base + (0.9 if cv.face == "up" else -0.9 if cv.face == "down" else 0), 0.6)
    cv.speckle(OBS[5], 0.06)
    if cv.face == "up":
        for _ in range(3):
            i, j = int(cv.rng.integers(0, cv.w)), int(cv.rng.integers(0, cv.h))
            cv.set(i, j, VEIN)


def build(p=P):
    b = Designer("end_crawler", res=(64, 64))
    w, h, d, lift = p["w"], p["h"], p["d"], p["lift"]
    b.bone("root")
    b.bone("body", "root", origin=(0, lift, 0))
    b.box("body", "base", (w - 1, 3, d - 1), (0, lift, 0))
    b.skin("base", lambda cv: shell(cv, 1.8))
    b.bone("case_l", "body", origin=(0.4, lift + 2, -d / 2 + 2))
    b.box("case_l", "case_l", (w / 2 - 0.6, h, d - 2), (w / 4 + 0.3, lift + 2, 1))
    b.skin("case_l", shell)
    b.mirror("case_l", "case_r")
    b.bone("head", "body", origin=(0, lift + 1, -d / 2))
    b.box("head", "head", (7, 4, 5), (0, lift - 0.5, -d / 2 - 2))
    b.skin("head", lambda cv: shell(cv, 2.4))
    b.bone("eyes_glow", "head", origin=(0, lift + 1, -d / 2 - 4), glow=True)
    for name, x in (("eye_l", 2.0), ("eye_r", -2.0)):
        b.box("eyes_glow", name, (2, 2, 0.6), (x, lift, -d / 2 - 4.5))
        b.skin(name, lambda cv: cv.fill(EYE[2]) if cv.face == "north" else cv.fill(EYE[1]))
    for name, x in (("mand_l", 1.4), ("mand_r", -1.4)):
        b.bone(name, "head", origin=(x, lift, -d / 2 - 4))
        b.box(name, name, (1, 1, 3), (x, lift - 0.5, -d / 2 - 7))
        b.skin(name, lambda cv: shell(cv, 1.2))
    for k, z in enumerate((-d / 2 + 3, 0.5, d / 2 - 3)):
        name = f"leg{k}_l"
        b.bone(name, "body", origin=(w / 2 - 1, lift + 1, z), rot=(0, (k - 1) * -22, -38))
        b.box(name, name, (p["leg"], 1.6, 1.6), (w / 2 - 1, lift + 0.2, z - 0.8), anchor="corner")
        b.skin(name, lambda cv: cv.noise(LEG, 1.2, 0.5))
        b.mirror(name, f"leg{k}_r")
    idle = b.anim("idle", 3.0, "loop")
    idle.wave("body", "pos", 1, 0.25, steps=6).wave("mand_l", "rot", 1, 8, steps=6).wave("mand_r", "rot", 1, -8, steps=6)
    walk = b.anim("walk", 0.5, "loop")
    for k in range(3):
        ph = k * 2 * math.pi / 3
        walk.orbit(f"leg{k}_l", "rot", (0, 16, 8), (0, ph, ph), steps=6)
        walk.orbit(f"leg{k}_r", "rot", (0, 16, -8), (0, ph + math.pi, ph + math.pi), steps=6)
    walk.wave("body", "rot", 2, 2, steps=4).wave("body", "pos", 1, 0.3, steps=4)
    bite = b.anim("bite", 0.4, "once")
    bite.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.12, (-20, 0, 0)).key("head", "rot", 0.4, (0, 0, 0))
    bite.key("mand_l", "rot", 0, (0, 0, 0)).key("mand_l", "rot", 0.12, (0, 30, 0)).key("mand_l", "rot", 0.4, (0, 0, 0))
    bite.key("mand_r", "rot", 0, (0, 0, 0)).key("mand_r", "rot", 0.12, (0, -30, 0)).key("mand_r", "rot", 0.4, (0, 0, 0))
    curl = b.anim("curl", 0.5, "hold")             # recolhe cabeça e pernas, abaixa o casco
    curl.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.4, (0, -2.2, 0))
    curl.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.4, (55, 0, 0))
    for k in range(3):
        curl.key(f"leg{k}_l", "rot", 0, (0, 0, 0)).key(f"leg{k}_l", "rot", 0.4, (0, 0, 40))
        curl.key(f"leg{k}_r", "rot", 0, (0, 0, 0)).key(f"leg{k}_r", "rot", 0.4, (0, 0, -40))
    return b


if __name__ == "__main__":
    make("end_crawler", build, P, {}, anim="walk")
