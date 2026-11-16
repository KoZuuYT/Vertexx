"""Iceologer (Minecraft Dungeons / Mob Vote 2020): ilhager de manto azul e capuz grande, com cajado de gelo.

Leitura da referência (``models/refs/iceologer``): capuz 10x12x10 com debrum cinza-claro em volta de uma
abertura **vazada** (o rosto fica 1 px para dentro: sem casca cobrindo o rosto e sem z-fighting), rosto cinza com
sobrancelha escura, olhos vermelho-escuros e o nariz comprido de ilhager saindo da abertura; manto azul com
debruns cinza e frente azul-petróleo, mangas com punho cinza e faixa azul-petróleo, botas escuras."""
import math

from kit import make
from style import R, Designer, rgb

BLUE = R("#0e1a45", "#16285e", "#1f3a80", "#2a4ea0", "#3a65c0", "#5580d0")
TEAL = R("#0f3a44", "#185662", "#1e6a78", "#2f8a9a")
TRIM = R("#6c7176", "#8d9196", "#b8bcc0", "#d4d8dc")
SKIN = R("#4f5a5e", "#6f7a7e", "#8a9a9e", "#a3b0b3")
BOOT = R("#1a1614", "#2b2522", "#3d3530")
BROW, EYE_W, EYE_R = rgb("#22282c"), rgb("#d8d8d8"), rgb("#7a2a1a")

P = dict(robe_h=18.0)


def blue(cv, base=2.8):
    cv.noise(BLUE, base + (0.7 if cv.face == "up" else -0.8 if cv.face == "down" else 0), 0.5)


def build(p=P):
    b = Designer("iceologer", res=(128, 128))
    rh = p["robe_h"]
    b.bone("root")
    b.bone("legs", "root", origin=(0, 3, 0))
    b.box("legs", "boot_l", (3, 3, 4), (2, 0, 0))
    b.box("legs", "boot_r", (3, 3, 4), (-2, 0, 0))
    for n in ("boot_l", "boot_r"):
        b.skin(n, lambda cv: cv.noise(BOOT, 1.2, 0.4))
    b.bone("body", "root", origin=(0, 3 + rh / 2, 0))
    b.box("body", "robe", (8, rh, 6), (0, 3, 0))

    def robe(cv):
        blue(cv)
        if cv.face in ("up", "down"):
            return
        cv.rect(0, cv.h - 2, cv.w, 2, TRIM[2]).hline(cv.h - 2, TRIM[3])          # barra
        if cv.face == "north":                       # frente aberta: debruns cinza e forro azul-petróleo
            cv.rect(2, 0, cv.w - 4, cv.h - 2, TEAL[2])
            for j in range(cv.h - 2):
                if cv.rng.random() < 0.3:
                    cv.set(int(cv.rng.integers(3, cv.w - 3)), j, TEAL[int(cv.rng.integers(1, 4))])
            cv.vline(2, TRIM[2], 0, cv.h - 2).vline(cv.w - 3, TRIM[2], 0, cv.h - 2)
            cv.vline(1, TRIM[1], 4, cv.h - 2).vline(cv.w - 2, TRIM[1], 4, cv.h - 2)
            cv.hline(7, TRIM[1], 3, cv.w - 3)                                        # cinto
    b.skin("robe", robe)
    top = 3 + rh

    b.bone("head", "body", origin=(0, top, 0))
    b.box("head", "face", (8, 10, 8), (0, top, 0))

    def face(cv):
        cv.noise(SKIN, 2.0, 0.35)
        if cv.face != "north":
            return
        cv.hline(2, BROW, 1, cv.w - 1)
        cv.set(1, 3, EYE_W).set(2, 3, EYE_R).set(cv.w - 2, 3, EYE_W).set(cv.w - 3, 3, EYE_R)
        cv.hline(4, SKIN[1], 1, 3).hline(4, SKIN[1], cv.w - 3, cv.w - 1)
        cv.hline(7, SKIN[0], 2, cv.w - 2)
        cv.hline(8, TEAL[2], 0, cv.w)
    b.skin("face", face)
    b.box("head", "nose", (2, 4, 2), (0, top + 3, -5))
    b.skin("nose", lambda cv: cv.noise(SKIN, 2.4, 0.3))
    b.drop_face("nose", "south")

    # capuz: casca 1 px maior em volta da cabeça, com a frente vazada (só o debrum é opaco)
    b.box("head", "hood", (10, 12, 10), (0, top - 1, 0))

    def hood(cv):
        blue(cv, 3.0)
        if cv.face == "north":
            cv.clear(1, 2, cv.w - 2, 8)                                  # abertura do rosto
            cv.rect(0, 0, cv.w, 2, TRIM[2]).hline(1, TRIM[1], 1, cv.w - 1)
            cv.vline(0, TRIM[2], 0, cv.h).vline(cv.w - 1, TRIM[2], 0, cv.h)
            cv.rect(0, cv.h - 2, cv.w, 2, TRIM[2])
        elif cv.face == "up":
            for _ in range(5):
                i, j = int(cv.rng.integers(1, cv.w - 3)), int(cv.rng.integers(1, cv.h - 2))
                cv.rect(i, j, 2, 1, BLUE[5])
    b.skin("hood", hood)
    b.locator("head", "eyes", (0, top + 6.5, -5))

    b.bone("arm_r", "body", origin=(-6, top - 1, 0))
    b.box("arm_r", "arm_r", (4, 11, 4), (-6, top - 12, 0))

    def sleeve(cv):
        blue(cv, 2.6)
        if cv.face in ("up", "down"):
            if cv.face == "down":
                cv.fill(SKIN[2])
            return
        cv.hline(cv.h - 5, TRIM[2]).hline(cv.h - 4, TEAL[2]).hline(cv.h - 3, TRIM[2])
        cv.rect(0, cv.h - 2, cv.w, 2, SKIN[2])
    b.skin("arm_r", sleeve)
    b.locator("arm_r", "staff", (-6, top - 12, -3))
    b.mirror("arm_r", "arm_l")

    # ── animações ──
    idle = b.anim("idle", 3.0, "loop")
    idle.wave("body", "pos", 1, 0.4, steps=6).wave("head", "rot", 1, 6, steps=6)
    idle.wave("arm_l", "rot", 0, 3, phase=0.5, steps=6).wave("arm_r", "rot", 0, 3, phase=1.5, steps=6)
    walk = b.anim("walk", 0.9, "loop")
    walk.wave("legs", "pos", 1, 0.25, steps=4).wave("body", "rot", 2, 2.5, steps=4)
    walk.wave("arm_l", "rot", 0, 10, phase=math.pi, steps=4).wave("arm_r", "rot", 0, 10, steps=4)
    walk.wave("head", "rot", 1, 4, phase=1.0, steps=4)
    cast = b.anim("cast", 1.0, "loop")           # braços erguidos para o céu
    for n, sx in (("arm_l", 1), ("arm_r", -1)):
        cast.key(n, "rot", 0.0, (150, 0, sx * 8)).key(n, "rot", 0.5, (165, 0, sx * 14), "catmullrom").key(n, "rot", 1.0, (150, 0, sx * 8))
    cast.wave("head", "rot", 0, 6, steps=4).wave("body", "pos", 1, 0.3, steps=4)
    summon = b.anim("summon", 1.4, "hold")
    for n, sx in (("arm_l", 1), ("arm_r", -1)):
        summon.key(n, "rot", 0.0, (50, 0, 0)).key(n, "rot", 0.5, (170, 0, sx * 20)).key(n, "rot", 1.0, (110, 0, sx * 6)).key(n, "rot", 1.4, (150, 0, sx * 12))
    summon.key("head", "rot", 0.0, (0, 0, 0)).key("head", "rot", 0.5, (18, 0, 0)).key("head", "rot", 1.4, (8, 0, 0))
    death = b.anim("death", 1.6, "hold")         # tomba para trás
    death.key("body", "rot", 0.0, (0, 0, 0)).key("body", "rot", 1.6, (-85, 0, 0), "catmullrom")
    death.key("body", "pos", 0.0, (0, 0, 0)).key("body", "pos", 1.6, (0, -9, 9))
    for n, sx in (("arm_l", 1), ("arm_r", -1)):
        death.key(n, "rot", 0.0, (0, 0, 0)).key(n, "rot", 1.6, (20, 0, sx * 25))
    return b


SPEC = {"a.png": {"role": "shape", "yaw": (20, 70), "pitch": (15, 40)}}

if __name__ == "__main__":
    make("iceologer", build, P, SPEC, anim="walk", fit=["robe_h"], bounds={"robe_h": (14, 22)})
