"""Avestruz (Ostrich): ~2,5 blocos de altura, corpo preto robusto com asas em bloco de pontas marrons, cauda clara,
pescoço longo e fino cor-de-rosa, cabeça com bico achatado alaranjado e pernas longas de pele rosada e tíbia bege com pés
de três dedos. Referência: prancha "Ostrich - Savanna Mob Concept"."""
import math

from kit import make
from style import R, Designer, rgb

BLACK = R("#0a090c", "#151318", "#211e26", "#312d38", "#413c4a")
TIP = R("#4a3a38", "#665250", "#85706a", "#a08a82")
WHITE = R("#b9b4a8", "#d4d0c4", "#ebe8de", "#faf8f1")
SKIN = R("#b9776c", "#d09286", "#e5aea1", "#f2c4b8")
LEG = R("#9a7a5e", "#b8967a", "#d1b093", "#e3c8ac")
THIGH = R("#b9776c", "#cf9186", "#e2aa9e")
BEAK = R("#c9715e", "#dd8c76", "#eeab95")
EYE_W = rgb("#f4efe4")
EYE_P = rgb("#0a0a0a")

P = dict(h=1.0)


def plume(cv):
    top = cv.face == "up"
    cv.noise(BLACK, 1.8 + (0.9 if top else -0.6 if cv.face == "down" else 0), 0.6)
    cv.speckle(BLACK[4], 0.10)
    if cv.face in ("north", "south", "east", "west"):
        for i in range(cv.w):                                  # fios de pena: riscos verticais mais escuros
            if cv.rng.random() < 0.3:
                cv.vline(i, BLACK[0], int(cv.rng.integers(0, cv.h // 2)), cv.h)


def wing(cv):
    plume(cv)
    if cv.face in ("north", "south", "east", "west", "down"):
        cv.fringe(0, cv.w, cv.h - 3, lambda i: int(cv.rng.integers(2, 4)), lambda i, k: TIP[1 + (k + i) % 3])


def head(cv):
    cv.noise(SKIN, 2.4, 0.4)
    if cv.face == "north":
        cv.rect(0, 1, 2, 2, EYE_W)
        cv.rect(cv.w - 2, 1, 2, 2, EYE_W)
        cv.set(0, 1, EYE_P)
        cv.set(0, 2, EYE_P)
        cv.set(cv.w - 1, 1, EYE_P)
        cv.set(cv.w - 1, 2, EYE_P)
    if cv.face in ("east", "west"):
        cv.rect(cv.w - 3, 1, 2, 2, EYE_W)
        cv.set(cv.w - 2, 1, EYE_P)
        cv.set(cv.w - 2, 2, EYE_P)


def build(p=P):
    b = Designer("ostrich", res=(128, 96))
    b.bone("root")
    b.bone("body", "root", origin=(0, 17, 0))
    b.box("body", "torso", (11, 9, 14), (0, 20.5, 0.5))
    b.skin("torso", plume)
    b.box("body", "back", (9, 1.6, 11), (0, 29.5, 0.8))       # placa de penas no dorso, um tom acima
    b.drop_face("back", "down")
    b.skin("back", lambda cv: (cv.noise(BLACK, 2.8, 0.7), cv.speckle(BLACK[4], 0.2)))
    b.box("body", "chest", (7, 6, 2), (0, 22.5, -7.6))
    b.skin("chest", plume)
    # asas: blocos grossos pendurados dos ombros, com franja de penas marrons
    b.bone("wing_l", "body", origin=(5.5, 28, -1))
    b.box("wing_l", "wing_l", (2.6, 8.5, 10), (6.9, 20.2, 0.5))
    b.skin("wing_l", wing)
    b.box("wing_l", "wing_l_lower", (2.9, 3.2, 8.6), (7.25, 17.4, 2.2))
    b.skin("wing_l_lower", lambda cv: (cv.noise(TIP, 1.6, 0.8), cv.speckle(TIP[3], 0.15)))
    b.mirror("wing_l", "wing_r")
    # cauda: leque de penas claras
    b.bone("tail", "body", origin=(0, 26, 7), rot=(-18, 0, 0))
    b.box("tail", "tail", (7, 6, 4), (0, 25.5, 9.5))
    b.skin("tail", lambda cv: (cv.noise(WHITE, 2.4, 0.6), cv.fringe(0, cv.w, cv.h - 2, lambda i: int(cv.rng.integers(1, 3)), lambda i, k: WHITE[1])))
    b.box("tail", "tail_top", (5, 2, 3), (0, 31, 9))
    b.skin("tail_top", lambda cv: cv.noise(WHITE, 2.0, 0.6))
    # pescoço e cabeça
    b.bone("neck", "body", origin=(0, 27, -6), rot=(8, 0, 0))
    b.box("neck", "neck", (2.4, 13.5, 2.4), (0, 28.5, -7.6))
    b.skin("neck", lambda cv: cv.noise(SKIN, 2.2, 0.5))
    b.box("neck", "neck_base", (4.6, 3, 4.2), (0, 27.2, -6.6))
    b.skin("neck_base", lambda cv: cv.noise(SKIN, 1.6, 0.5))
    b.bone("head", "neck", origin=(0, 41.5, -8.2))
    b.box("head", "head", (4.4, 4.0, 4.6), (0, 41.2, -8.6))
    b.skin("head", head)
    b.box("head", "beak", (2.6, 1.5, 3.6), (0, 41.7, -12.7))
    b.skin("beak", lambda cv: cv.noise(BEAK, 1.8, 0.4))
    b.box("head", "beak_tip", (2.0, 0.9, 1.2), (0, 41.5, -14.8))
    b.skin("beak_tip", lambda cv: cv.noise(BEAK, 2.4, 0.3))
    # pernas: coxa emplumada curta + coxa rosada + canela bege + pé de três dedos
    for side, sx in (("l", 1), ("r", -1)):
        n = f"leg_{side}"
        b.bone(n, "body", origin=(sx * 3.0, 17.6, 0.8))
        b.box(n, n, (2.6, 6.5, 2.8), (sx * 3.0, 11.6, 0.8))
        b.skin(n, lambda cv: cv.noise(THIGH, 1.6, 0.5))
        b.box(n, n + "_fur", (3.6, 2.4, 3.8), (sx * 3.0, 16.0, 0.8))
        b.skin(n + "_fur", plume)
        s = f"shin_{side}"
        b.bone(s, n, origin=(sx * 3.0, 11.2, 0.8))
        b.box(s, s, (1.7, 10.4, 1.9), (sx * 3.0, 1.1, 1.0))
        b.skin(s, lambda cv: cv.noise(LEG, 2.0, 0.5))
        for t, (dx, dz, ln, yaw) in enumerate(((0, -3.4, 3.8, 0), (-1.4, -2.4, 3.0, -28), (1.4, -2.4, 3.0, 28))):
            toe = f"toe{t}_{side}"
            b.box(s, toe, (1.2, 1.1, ln), (sx * 3.0 + dx * sx, 0.0, 0.6 + dz), rot=(0, yaw * sx, 0), origin=(sx * 3.0, 0.5, 1.0))
            b.skin(toe, lambda cv: cv.noise(LEG, 1.4, 0.4))
        b.box(s, f"heel_{side}", (2.0, 1.2, 2.0), (sx * 3.0, 0.0, 1.8))
        b.skin(f"heel_{side}", lambda cv: cv.noise(LEG, 1.6, 0.4))
    idle = b.anim("idle", 3.5, "loop")
    idle.wave("neck", "rot", 1, 7, steps=8).wave("head", "rot", 2, 9, steps=8).wave("body", "pos", 1, 0.2, steps=8)
    idle.wave("wing_l", "rot", 2, 2, steps=8).wave("wing_r", "rot", 2, -2, steps=8)
    walk = b.anim("walk", 0.9, "loop")
    run = b.anim("run", 0.42, "loop")
    for anim, amp, wings in ((walk, 26, 4), (run, 46, 18)):
        for side, sx in (("l", 1), ("r", -1)):
            ph = 0 if sx > 0 else math.pi
            anim.wave(f"leg_{side}", "rot", 0, amp, phase=ph, steps=8)
            anim.wave(f"shin_{side}", "rot", 0, amp * 0.55, phase=ph + 1.3, steps=8)
            anim.wave(f"wing_{side}", "rot", 2, sx * wings, phase=ph, steps=8)
        anim.wave("body", "pos", 1, 0.5 if amp > 30 else 0.3, steps=8).wave("neck", "rot", 0, 8 if amp > 30 else 5, phase=1.6, steps=8)
        anim.wave("head", "rot", 0, -6, phase=1.6, steps=8).wave("tail", "rot", 0, 6, phase=0.8, steps=8)
    return b


if __name__ == "__main__":
    make("ostrich", build, P, {}, anim="walk")
