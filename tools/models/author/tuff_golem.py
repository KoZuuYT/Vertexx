"""Golem de Tufo (Mob Vote 2022): estatuazinha de tufo com olhos ciano fundos, nariz comprido e uma capa de lã
(a cor vem do bloco de lã usado; o osso ``cape_tint`` é tingido e pode ser escondido sem capa).

Referências: ``models/refs/tuff_golem`` (grade de 16 capas, render pequeno, ilustração isométrica). Cabeça grande
10³, corpo 8x6x6 coberto pela capa (casca a meio pixel, sem z-fighting), braços em bloco e pés curtos."""
import math

from kit import make
from style import R, Designer, rgb

TUFF = R("#3f3d3a", "#555451", "#6d6961", "#84837b", "#9a958b", "#aea99e")
SOCKET, EYE, EYE2 = rgb("#1f1b1b"), rgb("#4fd0dc"), rgb("#2c8f9c")
CAPE = R("#9a9a9a", "#b8b8b8", "#d2d2d2", "#e6e6e6", "#f6f6f6")       # tons de cinza: a tinta multiplica

P = dict(head=10.0, body_h=6.0, arm_l=7.0)


def tuff(cv, base=3.0):
    cv.noise(TUFF, base + (0.7 if cv.face == "up" else -1.0 if cv.face == "down" else 0), 0.55)


def build(p=P):
    b = Designer("tuff_golem", res=(128, 64))
    hs, bh, al = p["head"], p["body_h"], p["arm_l"]
    b.bone("root")
    b.bone("leg_l", "root", origin=(2, 2, 0))
    b.box("leg_l", "leg_l", (3, 2, 3), (2, 0, 0))
    b.skin("leg_l", lambda cv: tuff(cv, 2.2))
    b.mirror("leg_l", "leg_r")
    b.bone("body", "root", origin=(0, 2, 0))
    b.box("body", "torso", (8, bh, 6), (0, 2, 0))
    b.skin("torso", tuff)
    top = 2 + bh
    # capa: casca a 0,5 px do corpo + barra mais larga embaixo
    b.bone("cape_tint", "body", origin=(0, top, 0))
    b.box("cape_tint", "cape_body", (9, bh - 1, 7), (0, 3, 0))
    b.box("cape_tint", "cape_hem", (10, 2, 8), (0, 1.5, 0))

    def cape(cv):
        cv.noise(CAPE, 2.8, 0.35)
        if cv.face not in ("up", "down"):
            for i in range(0, cv.w, 3):
                cv.vline(i, CAPE[1])
            cv.hline(0, CAPE[4])
    b.skin("cape_body", cape)
    b.skin("cape_hem", lambda cv: cv.fill(CAPE[4]).stripes(CAPE, 2, depth=0.6) if cv.face not in ("up", "down") else cv.fill(CAPE[3]))
    b.drop_face("cape_body", "down", "up")
    b.drop_face("cape_hem", "up")

    b.bone("head", "body", origin=(0, top, 0))
    b.box("head", "skull", (hs, hs, hs), (0, top, 0))
    b.skin("skull", tuff)

    def face(cv):
        tuff(cv)
        cv.hline(2, TUFF[4], 1, cv.w - 1)                         # testa pesada
        for i0 in (1, cv.w - 4):                                  # órbitas fundas 3x3 com o olho embaixo, para dentro
            cv.rect(i0, 3, 3, 3, SOCKET)
            ei = i0 + 2 if i0 < cv.w // 2 else i0
            cv.set(ei, 4, EYE).set(ei, 5, EYE2)
        cv.hline(cv.h - 2, TUFF[1], 3, cv.w - 3)                  # boca/queixo
    b.skin("skull", face, faces=("north",))
    b.box("head", "nose", (2, 4, 2), (0, top + hs - 8, -hs / 2 - 1))
    b.skin("nose", lambda cv: tuff(cv, 3.3))
    b.drop_face("nose", "south")

    b.bone("arm_l", "body", origin=(5.5, top - 1, 0), rot=(28, 0, 0))
    b.box("arm_l", "arm_l", (3, al, 3), (5.5, top - 1 - al, -0.5))
    b.skin("arm_l", lambda cv: tuff(cv, 2.6))
    b.locator("arm_l", "hand_item", (5.5, top - al, -2.5))
    b.mirror("arm_l", "arm_r")

    # ── animações ──
    idle = b.anim("idle", 4.0, "loop")
    idle.wave("head", "rot", 2, 1.2, steps=8)
    idle.wave("body", "scl", 1, 0.006, base=(1, 1, 1), steps=8)
    wake = b.anim("wake", 1.2, "once")
    wake.key("head", "rot", 0.0, (0, 0, 0)).key("head", "rot", 0.4, (6, 25, 6)).key("head", "rot", 0.8, (-4, -20, -4)).key("head", "rot", 1.2, (0, 0, 0))
    for n, sx in (("arm_l", 1), ("arm_r", -1)):
        wake.key(n, "rot", 0.0, (0, 0, 0)).key(n, "rot", 0.5, (40, 0, sx * 18)).key(n, "rot", 1.2, (0, 0, 0))
    wake.key("body", "pos", 0.0, (0, 0, 0)).key("body", "pos", 0.3, (0, 0.5, 0)).key("body", "pos", 0.6, (0, 0, 0))
    walk = b.anim("walk", 0.8, "loop")
    for name, ph in (("leg_l", 0), ("leg_r", math.pi)):
        walk.wave(name, "rot", 0, 30, phase=ph)
    walk.wave("arm_l", "rot", 0, 22, phase=math.pi).wave("arm_r", "rot", 0, 22, phase=0)
    walk.wave("body", "pos", 1, 0.35, steps=4).wave("body", "rot", 2, 3, steps=4)
    walk.wave("head", "rot", 2, 3, phase=0.4, steps=4).wave("cape_tint", "rot", 0, -4, phase=0.8, steps=4)
    carry = b.anim("carry", 0.8, "loop")           # braço esquerdo erguido segurando o item
    carry.key("arm_l", "rot", 0, (65, 0, -5)).key("arm_l", "rot", 0.8, (65, 0, -5))
    carry.key("arm_r", "rot", 0, (30, 0, 5)).key("arm_r", "rot", 0.8, (30, 0, 5))
    for name, ph in (("leg_l", 0), ("leg_r", math.pi)):
        carry.wave(name, "rot", 0, 30, phase=ph)
    carry.wave("body", "pos", 1, 0.3, steps=4)
    return b


SPEC = {"a.png": {"role": "color", "note": "grade com 16 capas"},
        "b.png": {"role": "shape", "yaw": (-60, 0), "pitch": (5, 40)},
        "c.png": {"role": "shape", "yaw": (-70, -20), "pitch": (20, 45), "clip": [(0, 470, 200, 554)]}}

if __name__ == "__main__":
    make("tuff_golem", build, P, SPEC, anim="walk", fit=["head", "body_h", "arm_l"],
         bounds={"head": (8, 13), "body_h": (4, 8), "arm_l": (5, 9)})
