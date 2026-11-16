"""Pinguim (Mob Vote 2023): pinguim-saltador de crista amarela, desajeitado em terra e ótimo nadador.

Referências: ``models/refs/penguin`` (pixel art de frente + render 3D). Forma: corpo 8x11x7 com barriga creme,
cabeça 8x6x7 com bico vermelho e cristas douradas encaracoladas, nadadeiras finas e pés rosados."""
import math

from kit import make
from style import R, Designer, noisy, rgb, shade

NAVY = R("#161725", "#232532", "#2c2f42", "#373a4f", "#454966")
BACK = R("#121320", "#1b1d2a", "#232532", "#2c2f42", "#373a4f")
CREAM = R("#8c8d89", "#a9b19f", "#c4c6b4", "#d9d9c7", "#e6e4d4")
BEAK = R("#5a1411", "#8a231b", "#b8352a", "#d84c3a", "#ec6a52")
GOLD = R("#6e3a12", "#8e4a17", "#be8e23", "#dcb23a", "#f0d25a")
PINK = R("#8a3d5c", "#b3567a", "#d06f93", "#e48cac", "#f2a9c2")
EYE_W, EYE_K = rgb("#d9d9c7"), rgb("#0e0f18")

P = dict(body_h=11.0, head_h=6.0)


def build(p=P):
    b = Designer("penguin", res=(64, 64))
    bh, hh = p["body_h"], p["head_h"]
    top = 1 + bh
    b.bone("root")
    b.bone("body", "root", origin=(0, 1, 0))
    b.box("body", "torso", (8, bh, 7), (0, 1, 0))
    b.skin("torso", noisy(NAVY, 2.0, 0.4))
    b.skin("torso", noisy(BACK, 2.0, 0.4), faces=("south",))

    def belly(cv):                          # barriga creme com a base esverdeada, borda escura dos lados
        cv.noise(CREAM, 2.6, 0.35)
        cv.rect(0, 0, 1, cv.h, NAVY[2]).rect(cv.w - 1, 0, 1, cv.h, NAVY[2])
        cv.hline(cv.h - 2, CREAM[1], 1, cv.w - 1).hline(cv.h - 1, CREAM[0], 1, cv.w - 1)
        for i in range(1, cv.w - 1):
            if cv.rng.random() < 0.35:
                cv.set(i, cv.h - 3, CREAM[1])
    b.skin("torso", belly, faces=("north",))
    b.skin("torso", lambda cv: cv.noise(CREAM, 1.2, 0.3).rect(0, 0, cv.w, 2, NAVY[1]), faces=("down",))

    b.bone("head", "body", origin=(0, top, 0))
    b.box("head", "skull", (8, hh, 7), (0, top, 0))
    b.skin("skull", noisy(NAVY, 1.8, 0.4, top=0.4))

    def face(cv):
        cv.noise(NAVY, 1.6, 0.35)
        cv.set(1, 2, EYE_W).set(2, 2, EYE_K).set(cv.w - 2, 2, EYE_W).set(cv.w - 3, 2, EYE_K)
        cv.set(1, 1, GOLD[3]).set(2, 1, GOLD[2]).set(cv.w - 2, 1, GOLD[3]).set(cv.w - 3, 1, GOLD[2])
        cv.rect(3, 3, 2, 2, BEAK[1])            # sombra em volta do bico
    b.skin("skull", face, faces=("north",))
    b.bone("beak", "head", origin=(0, top + 2, -3.5))
    b.box("beak", "beak", (2, 2, 3), (0, top + 2, -5), anchor="center")
    b.skin("beak", lambda cv: cv.vgrad(BEAK, 4, 1.5, 0.4) if cv.face != "up" else cv.noise(BEAK, 3.5, 0.3))
    b.skin("beak", lambda cv: cv.noise(BEAK, 1, 0.3), faces=("down",))

    # crista: sobrancelha dourada que sai para o lado e enrola para cima (lado esquerdo do mob = +X)
    b.bone("crest_l", "head", origin=(3, top + 4.5, -3.5))
    b.box("crest_l", "crest_l_bar", (3, 1, 1), (2, top + 4, -4), anchor="corner")
    b.box("crest_l", "crest_l_curl", (1, 2, 1), (5, top + 4, -4), anchor="corner")
    b.box("crest_l", "crest_l_tip", (1, 1, 2), (5, top + 6, -4), anchor="corner")
    for c in ("crest_l_bar", "crest_l_curl", "crest_l_tip"):
        b.skin(c, lambda cv: cv.noise(GOLD, 3, 0.4) if cv.face != "down" else cv.fill(GOLD[1]))
    b.mirror("crest_l", "crest_r")

    b.bone("flipper_l", "body", origin=(4, top - 1, 0))
    b.box("flipper_l", "flipper_l", (1, 8, 4), (4.5, top - 9, 0))
    b.skin("flipper_l", lambda cv: cv.vgrad(BACK, 2.5, 0.5, 0.5).hline(0, NAVY[4]))
    b.skin("flipper_l", lambda cv: cv.fill(NAVY[4]), faces=("up",))
    b.mirror("flipper_l", "flipper_r")

    b.bone("foot_l", "root", origin=(2, 1, 0))
    b.box("foot_l", "foot_l", (3, 1, 4), (2, 0, -1.5))
    b.skin("foot_l", noisy(PINK, 2.2, 0.35, top=0.6))
    b.mirror("foot_l", "foot_r")

    # ── animações ──
    idle = b.anim("idle", 3.2, "loop")
    idle.orbit("head", "rot", (3, 8, 0), (1.1, 0, 0))
    idle.wave("flipper_l", "rot", 2, 4, steps=8).wave("flipper_r", "rot", 2, -4, steps=8)
    idle.wave("body", "scl", 1, 0.012, base=(1, 1, 1), steps=8)
    walk = b.anim("walk", 0.7, "loop")          # gingado: o corpo tomba de um lado para o outro
    walk.wave("body", "rot", 2, 9, steps=8)
    walk.wave("head", "rot", 2, -5, steps=8)
    walk.wave("foot_l", "pos", 1, 0.6, steps=8).wave("foot_r", "pos", 1, 0.6, phase=math.pi, steps=8)
    walk.wave("foot_l", "rot", 0, 18, steps=8).wave("foot_r", "rot", 0, 18, phase=math.pi, steps=8)
    walk.key("flipper_l", "rot", 0, (0, 0, 14)).key("flipper_l", "rot", 0.7, (0, 0, 14))
    walk.key("flipper_r", "rot", 0, (0, 0, -14)).key("flipper_r", "rot", 0.7, (0, 0, -14))
    swim = b.anim("swim", 0.6, "loop")          # deitado de barriga, nadadeiras batendo
    swim.key("body", "rot", 0, (-80, 0, 0)).key("body", "rot", 0.6, (-80, 0, 0))
    swim.key("body", "pos", 0, (0, 4, 0)).key("body", "pos", 0.6, (0, 4, 0))
    swim.key("head", "rot", 0, (55, 0, 0)).key("head", "rot", 0.6, (55, 0, 0))
    swim.wave("flipper_l", "rot", 2, 35, base=(0, 0, 40), steps=8).wave("flipper_r", "rot", 2, -35, base=(0, 0, -40), steps=8)
    for n in ("foot_l", "foot_r"):
        swim.key(n, "pos", 0, (0, 9, 10)).key(n, "pos", 0.6, (0, 9, 10))
        swim.wave(n, "rot", 0, 20, base=(80, 0, 0), phase=0 if n == "foot_l" else math.pi, steps=8)
    return b


SPEC = {"a.png": {"role": "color", "note": "pixel art de frente"},
        "b.png": {"role": "shape", "yaw": (10, 80), "pitch": (5, 40)}}

if __name__ == "__main__":
    make("penguin", build, P, SPEC, anim="walk")
