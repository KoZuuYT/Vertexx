"""Caranguejo (Mob Vote 2023): casco azul, olhos em haste, uma garra grande de cobre e outra pequena, três
pernas de cada lado. Vive nos manguezais e sobe nas árvores; a garra grande cai como item (alcance maior).

Referências: ``models/refs/crab`` (render 3D de lado + pixel art de cima/lado)."""
import math

from kit import make
from style import R, Designer, noisy, rgb

SHELL = R("#121355", "#2a3a72", "#33477b", "#4562a2", "#5171b6", "#6a8ad0")
CLAW = R("#5e0d0b", "#7e3820", "#95532e", "#b77344", "#d9844b", "#f19251", "#f9c695")
BELLY = R("#8a6e5e", "#b09080", "#cdb3a0", "#e2cdbb")
STALK = R("#120806", "#210e0a", "#33170f")
WHITE = rgb("#d8dcd8")

P = dict(body_w=14.0, body_h=7.0, body_d=12.0, lift=3.0, claw=11.0, leg=9.0, spread=50.0)


def shell(cv):
    if cv.face == "up":
        cv.noise(SHELL, 3.6, 0.55)
        for _ in range(4):                           # manchas claras no casco
            i, j = int(cv.rng.integers(1, cv.w - 2)), int(cv.rng.integers(1, cv.h - 2))
            cv.set(i, j, SHELL[5]).set(i + 1, j, SHELL[5])
    elif cv.face == "down":
        cv.noise(BELLY, 1.5, 0.4)
    else:
        cv.noise(SHELL, 2.2, 0.45)
        cv.hline(0, SHELL[4])
        cv.hline(cv.h - 1, SHELL[0])


def copper(cv, base=3.5):
    cv.noise(CLAW, base + (1.2 if cv.face == "up" else -1.4 if cv.face == "down" else 0), 0.5)


def build(p=P):
    b = Designer("crab", res=(128, 64))
    bw, bh, bd, lift = p["body_w"], p["body_h"], p["body_d"], p["lift"]
    top = lift + bh
    b.bone("root")
    b.bone("body", "root", origin=(0, lift, 0))
    b.box("body", "shell", (bw, bh, bd), (0, lift, 0))
    b.skin("shell", shell)

    def front(cv):                                   # boca: fenda escura e "bigodes" claros
        shell(cv)
        cv.hline(cv.h - 2, SHELL[0], 3, cv.w - 3)
        cv.set(4, cv.h - 3, BELLY[3]).set(cv.w - 5, cv.h - 3, BELLY[3])
    b.skin("shell", front, faces=("north",))

    # olhos em haste (lado esquerdo do mob = +X) espelhados
    b.bone("eye_l", "body", origin=(2.5, top, -bd / 2 + 1.5))
    b.box("eye_l", "eye_l", (1, 5, 1), (2.5, top, -bd / 2 + 1.5))
    b.skin("eye_l", lambda cv: cv.vgrad(STALK, 0, 2, 0.4).set(0, 1, WHITE) if cv.face == "north" else cv.vgrad(STALK, 0, 2, 0.4))
    b.mirror("eye_l", "eye_r")

    # garra grande (esquerda do mob = +X) e garra pequena (direita)
    c = p["claw"]
    b.bone("claw_big", "body", origin=(bw / 2 - 1, lift + 2, -bd / 2))
    b.box("claw_big", "claw_big_arm", (2, 2, 3), (bw / 2 - 1, lift + 1, -bd / 2 - 1.5))
    b.box("claw_big", "claw_big_palm", (c, 6, 4), (bw / 2 - c / 2 + 1, lift - 1, -bd / 2 - 4.5))
    b.skin("claw_big_arm", copper)
    b.skin("claw_big_palm", lambda cv: copper(cv, 3.8))

    def palm_face(cv):                               # dedo de cima e a junta mais escura
        copper(cv, 3.8)
        cv.hline(cv.h - 1, CLAW[1], 0, cv.w).vline(cv.w - 2, CLAW[2], 1, cv.h - 1)
    b.skin("claw_big_palm", palm_face, faces=("north", "east", "west"))
    b.bone("pincer", "claw_big", origin=(bw / 2 - 1, lift - 0.5, -bd / 2 - 3))
    b.box("pincer", "pincer", (5, 2, 3), (bw / 2 - c + 4.5, lift - 3, -bd / 2 - 6.5))
    b.skin("pincer", lambda cv: copper(cv, 4.6))

    b.bone("claw_small", "body", origin=(-bw / 2 + 1, lift + 2, -bd / 2))
    b.box("claw_small", "claw_small_arm", (2, 2, 2), (-bw / 2 + 1, lift + 1, -bd / 2 - 1))
    b.box("claw_small", "claw_small_palm", (4, 3, 3), (-bw / 2, lift + 0.5, -bd / 2 - 3.5))
    b.box("claw_small", "claw_small_tip", (3, 1, 2), (-bw / 2 + 0.5, lift - 0.5, -bd / 2 - 4))
    for n in ("claw_small_arm", "claw_small_palm", "claw_small_tip"):
        b.skin(n, copper)

    # pernas: três de cada lado, saindo do casco para baixo e para fora
    for k, z in enumerate((-bd / 2 + 2.5, 0.5, bd / 2 - 2.5)):
        name = f"leg{k}_l"
        b.bone(name, "body", origin=(bw / 2 - 0.5, lift + 1.5, z), rot=(0, (k - 1) * -24, -p["spread"]))
        b.box(name, name, (p["leg"], 2, 2), (bw / 2 - 0.5, lift + 0.5, z - 1), anchor="corner")

        def leg(cv):
            copper(cv, 3.2)
            if cv.face in ("north", "south", "up", "down"):
                cv.rect(0, 0, 2, cv.h, SHELL[3])       # junta azul colada ao casco
                cv.rect(cv.w - 1, 0, 1, cv.h, CLAW[1]) if cv.face != "south" else cv.rect(0, 0, 1, cv.h, CLAW[1])
        b.skin(name, leg)
        b.mirror(name, f"leg{k}_r")

    # ── animações ──
    idle = b.anim("idle", 3.0, "loop")
    idle.wave("eye_l", "rot", 2, 8, steps=6).wave("eye_r", "rot", 2, -8, phase=0.7, steps=6)
    idle.key("pincer", "rot", 0, (0, 0, 0)).key("pincer", "rot", 1.2, (0, 0, 0)).key("pincer", "rot", 1.5, (20, 0, 0)) \
        .key("pincer", "rot", 1.8, (0, 0, 0)).key("pincer", "rot", 2.1, (20, 0, 0)).key("pincer", "rot", 2.4, (0, 0, 0)).key("pincer", "rot", 3.0, (0, 0, 0))
    idle.wave("body", "pos", 1, 0.2, steps=6)
    walk = b.anim("walk", 0.5, "loop")              # anda de lado: as pernas de cada lado em fase alternada
    for k in range(3):
        ph = k * 2 * math.pi / 3
        walk.orbit(f"leg{k}_l", "rot", (0, 10, 14), (0, ph + 1, ph), steps=6)
        walk.orbit(f"leg{k}_r", "rot", (0, 10, -14), (0, ph + 1 + math.pi, ph + math.pi), steps=6)
    walk.wave("body", "rot", 2, 3, steps=4).wave("body", "pos", 1, 0.3, steps=4)
    walk.wave("claw_big", "rot", 0, 6, steps=4)
    wave = b.anim("wave", 1.2, "loop")              # acena com a garra grande
    wave.key("claw_big", "rot", 0, (0, 0, 0)).key("claw_big", "rot", 0.3, (-55, 0, -20), "catmullrom") \
        .key("claw_big", "rot", 0.6, (-40, 0, 10), "catmullrom").key("claw_big", "rot", 0.9, (-55, 0, 20), "catmullrom").key("claw_big", "rot", 1.2, (0, 0, 0))
    wave.wave("pincer", "rot", 0, 18, base=(18, 0, 0), steps=4)
    return b


SPEC = {"a.png": {"role": "shape", "yaw": (20, 80), "pitch": (10, 50)},
        "b.png": {"role": "color", "note": "pixel art, várias garras em sequência"}}

if __name__ == "__main__":
    make("crab", build, P, SPEC, anim="walk", fit=["body_w", "body_h", "body_d", "claw", "leg"],
         bounds={"body_w": (10, 18), "body_h": (4, 10), "body_d": (8, 16), "claw": (7, 15), "leg": (6, 12)})
