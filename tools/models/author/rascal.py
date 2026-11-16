"""Rascal (Mob Vote 2022): criaturinha **bípede** das minas, de capuz azul-petróleo, com uma caixa de madeira presa
nas costas.

Leitura das referências (``models/refs/rascal``): cabeça grande e quadrada (capuz com franjas caindo sobre o rosto
claro, olhos escuros, boca rosada), corpo pequeno logo abaixo, dois braços de manga azul-petróleo com mãos
cinza-claro, perninhas curtas cinza e a caixa (laterais de madeira, tampa de saco cor de palha, alça escura)
encostada nas costas, atrás da cabeça."""
import math

from kit import make
from style import R, Designer, rgb

HOOD = R("#0c2420", "#123a34", "#1b4a48", "#1f6855", "#2f7f6a", "#5c918e")
FACE = R("#57717a", "#7d969c", "#a3bdc1", "#c4d8da")
SKIN = R("#3b4f56", "#57717a", "#7d969c", "#a3bdc1", "#c4d8da")
WOOD = R("#2a1c0e", "#3e2a17", "#55391c", "#664722", "#7d5a2e")
SACK = R("#7a5a28", "#ab7e3c", "#c49a52", "#dcb874")
STRAP = rgb("#3e2a17")
EYE, MOUTH, MOUTH2, SHINE = rgb("#1e2a30"), rgb("#d07a8a"), rgb("#8a3a4a"), rgb("#e8f2f2")

P = dict(head_w=10.0, head_h=8.0, head_d=8.0, body_h=5.0, crate_h=5.0, crate_d=7.0, arm=7.0)


def hood(cv, base=3.0):
    cv.noise(HOOD, base + (0.6 if cv.face == "up" else -1.0 if cv.face == "down" else 0), 0.42)
    if cv.face not in ("up", "down"):
        for i in range(cv.w):
            if cv.rng.random() < 0.35:
                j0 = int(cv.rng.integers(0, max(1, cv.h - 2)))
                cv.vline(i, HOOD[5] if cv.rng.random() < 0.4 else HOOD[1], j0, min(cv.h, j0 + 3))


def build(p=P):
    b = Designer("rascal", res=(128, 64))
    hw, hh, hd, bh = p["head_w"], p["head_h"], p["head_d"], p["body_h"]
    b.bone("root")
    b.bone("leg_l", "root", origin=(2, 3, 0))
    b.box("leg_l", "leg_l", (3, 3, 3), (2, 0, 0))
    b.skin("leg_l", lambda cv: cv.vgrad(SKIN, 1, 3, 0.4) if cv.face not in ("up", "down") else cv.fill(SKIN[1]))
    b.mirror("leg_l", "leg_r")

    b.bone("body", "root", origin=(0, 3, 0))
    b.box("body", "torso", (8, bh, 6), (0, 3, 0))

    def torso(cv):
        hood(cv, 2.4)
        if cv.face == "north":                      # alças da caixa descendo pela frente
            cv.vline(1, STRAP).vline(cv.w - 2, STRAP)
        elif cv.face == "down":
            cv.noise(SKIN, 1.2, 0.3)
    b.skin("torso", torso)
    top = 3 + bh

    b.bone("head", "body", origin=(0, top, 0))
    b.box("head", "skull", (hw, hh, hd), (0, top, 0))

    def skull(cv):
        hood(cv)
        if cv.face == "north":                      # rosto claro emoldurado pelo capuz
            for i in range(1, cv.w - 1):
                for j in range(2, cv.h):
                    cv.set(i, j, FACE[2] if cv.rng.random() > 0.15 else FACE[3])
            cv.fringe(1, cv.w - 1, 2, lambda i: [1, 0, 1, 1, 0, 0, 1, 0, 1, 1][i % 10], HOOD[4], None)
            cv.rect(2, 4, 2, 2, EYE).set(2, 4, SHINE)
            cv.rect(cv.w - 4, 4, 2, 2, EYE).set(cv.w - 4, 4, SHINE)
            cv.rect(cv.w // 2 - 1, 6, 2, 1, MOUTH).set(cv.w // 2 - 1, 6, MOUTH2)
        elif cv.face in ("east", "west", "south"):  # franjas do capuz caindo
            cv.drips(HOOD[1], cv.h - 2, 1, 2, 0.6)
    b.skin("skull", skull)

    # braços: manga azul-petróleo, mão cinza-clara
    al = p["arm"]
    b.bone("arm_l", "body", origin=(5.5, top - 0.5, 0))
    b.box("arm_l", "arm_l", (3, al, 3), (5.5, top - 0.5 - al, 0))

    def arm(cv):
        hood(cv, 2.8)
        if cv.face not in ("up",):
            cv.rect(0, cv.h - 3, cv.w, 3, SKIN[3]).hline(cv.h - 3, SKIN[2])
        if cv.face == "down":
            cv.fill(SKIN[3])
    b.skin("arm_l", arm)
    b.mirror("arm_l", "arm_r")

    # caixa nas costas, atrás da cabeça
    cd, ch = p["crate_d"], p["crate_h"]
    b.bone("crate", "head", origin=(0, top + hh, hd / 2))
    cy = top + hh - 2                               # mochila alta: apoiada em cima da parte de trás da cabeça
    cz = hd / 2 - cd / 2 + 2
    b.box("crate", "box", (9, ch, cd), (0, cy, cz))

    def box(cv):
        if cv.face == "up":
            cv.noise(WOOD, 2, 0.4)
        else:
            cv.noise(WOOD, 3, 0.45).border(WOOD[1])
            for i in range(2, cv.w - 2, 3):
                cv.vline(i, WOOD[2], 1, cv.h - 1)
            if cv.face in ("east", "west"):
                cv.hline(cv.h // 2, STRAP)
    b.skin("box", box)

    b.bone("lid", "crate", origin=(0, cy + ch, cz))
    b.box("lid", "sack", (8, 1, cd - 1), (0, cy + ch, cz))
    b.skin("sack", lambda cv: cv.noise(SACK, 2.2, 0.5).stripes(SACK, 2, vertical=cv.face != "up"))

    # ── animações ──
    walk = b.anim("walk", 0.6, "loop")
    walk.wave("leg_l", "rot", 0, 32, steps=6).wave("leg_r", "rot", 0, 32, phase=math.pi, steps=6)
    walk.wave("arm_l", "rot", 0, 24, phase=math.pi, steps=6).wave("arm_r", "rot", 0, 24, steps=6)
    walk.wave("body", "pos", 1, 0.35, steps=3).wave("head", "rot", 2, 3, steps=6)
    walk.wave("crate", "rot", 2, 2.5, phase=1.0, steps=6)
    flee = b.anim("flee", 0.4, "loop")              # corre inclinado, braços para trás
    flee.wave("leg_l", "rot", 0, 50, steps=6).wave("leg_r", "rot", 0, 50, phase=math.pi, steps=6)
    flee.key("arm_l", "rot", 0, (-45, 0, -10)).key("arm_l", "rot", 0.4, (-45, 0, -10))
    flee.key("arm_r", "rot", 0, (-45, 0, 10)).key("arm_r", "rot", 0.4, (-45, 0, 10))
    flee.key("body", "rot", 0, (12, 0, 0)).key("body", "rot", 0.4, (12, 0, 0))
    flee.wave("body", "pos", 1, 0.6, steps=3).wave("crate", "rot", 2, 6, steps=4).wave("lid", "pos", 1, 0.4, phase=0.5, steps=4)
    idle = b.anim("idle", 3.0, "loop")              # espia olhando em volta
    idle.orbit("head", "rot", (4, 16, 0), (1.0, 0, 0))
    idle.wave("arm_l", "rot", 2, -3, steps=8).wave("arm_r", "rot", 2, 3, steps=8)
    idle.wave("body", "scl", 1, 0.02, base=(1, 1, 1), steps=8)
    hide = b.anim("hide", 2.0, "loop")              # agachado, mãos tapando o rosto
    hide.key("body", "pos", 0, (0, -1.5, 0)).key("body", "pos", 2.0, (0, -1.5, 0))
    for n, sx in (("arm_l", 1), ("arm_r", -1)):
        hide.key(n, "rot", 0, (110, 0, sx * -20)).key(n, "rot", 1.0, (115, 0, sx * -22)).key(n, "rot", 2.0, (110, 0, sx * -20))
    hide.key("head", "rot", 0, (8, 0, 0)).key("head", "rot", 1.0, (10, 0, 0)).key("head", "rot", 2.0, (8, 0, 0))
    hide.wave("crate", "rot", 2, 3, steps=8)
    return b


SPEC = {"a.png": {"role": "shape", "yaw": (10, 80), "pitch": (15, 45)},
        "b.png": {"role": "color", "note": "foto com fundo de floresta"},
        "c.png": {"role": "color", "note": "pixel art de lado"}}

if __name__ == "__main__":
    make("rascal", build, P, SPEC, anim="walk", fit=["body_h", "crate_h", "crate_d", "arm"],
         bounds={"body_h": (3, 7), "crate_h": (4, 8), "crate_d": (5, 9), "arm": (5, 9)})
