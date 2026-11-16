"""Avestruz (Ostrich): pernas longas, corpo emplumado preto com cauda branca, pescoço fino e bico curto."""
import math

from kit import make
from style import R, Designer, rgb

BLACK = R("#0c0b0d", "#17151a", "#242128", "#34303a")
WHITE = R("#c8c3b8", "#dedad0", "#efece4", "#fbf9f3")
SKIN = R("#c98a7c", "#dba396", "#ebbcb0")
LEG = R("#b98f80", "#cfa595", "#e0bcae")
BEAK = R("#c9a24a", "#e0bb5e", "#f2d581")

P = dict(h=1.0)


def plume(cv):
    cv.noise(BLACK, 2.4 + (0.6 if cv.face == "up" else 0), 0.7)
    cv.speckle(BLACK[3], 0.12)


def build(p=P):
    b = Designer("ostrich", res=(128, 64))
    b.bone("root")
    b.bone("body", "root", origin=(0, 17, 0))
    b.box("body", "torso", (9, 8, 13), (0, 21, 0))
    b.skin("torso", plume)
    b.bone("wing_l", "body", origin=(4.5, 23, 0.4))
    b.box("wing_l", "wing_l", (1.2, 5, 8), (5.2, 21.6, 0.4))
    b.skin("wing_l", plume)
    b.mirror("wing_l", "wing_r")
    b.bone("tail", "body", origin=(0, 22, 6), rot=(-20, 0, 0))
    b.box("tail", "tail", (6, 5, 4), (0, 24, 9))
    b.skin("tail", lambda cv: cv.noise(WHITE, 2.4, 0.7))
    b.bone("neck", "body", origin=(0, 24, -5.5), rot=(10, 0, 0))
    b.box("neck", "neck", (2.2, 13, 2.2), (0, 31, -6.5))
    b.skin("neck", lambda cv: cv.noise(SKIN, 2.0, 0.5))
    b.bone("head", "neck", origin=(0, 37.5, -6.8))
    b.box("head", "head", (3.4, 3.0, 3.6), (0, 39, -7.4))
    b.skin("head", lambda cv: (cv.noise(SKIN, 2.2, 0.5), cv.set(0, 1, rgb("#0b0b0b")) if cv.face == "north" else None, cv.set(cv.w - 1, 1, rgb("#0b0b0b")) if cv.face == "north" else None)[0])
    b.box("head", "beak", (2.0, 1.0, 2.6), (0, 38.3, -10.0))
    b.skin("beak", lambda cv: cv.noise(BEAK, 1.6, 0.4))
    for side, sx in (("l", 1), ("r", -1)):
        n = f"leg_{side}"
        b.bone(n, "body", origin=(sx * 2.4, 17.5, 0.5))
        b.box(n, n, (1.5, 10, 1.7), (sx * 2.4, 12.5, 0.5))
        b.skin(n, lambda cv: cv.noise(LEG, 1.8, 0.5))
        b.bone(f"shin_{side}", n, origin=(sx * 2.4, 7.5, 0.5))
        b.box(f"shin_{side}", f"shin_{side}", (1.0, 7.5, 1.2), (sx * 2.4, 3.7, 0.3))
        b.skin(f"shin_{side}", lambda cv: cv.noise(LEG, 1.4, 0.5))
        b.box(f"shin_{side}", f"foot_{side}", (1.8, 0.7, 3.4), (sx * 2.4, 0.35, -0.8))
        b.skin(f"foot_{side}", lambda cv: cv.noise(LEG, 1.2, 0.5))
    idle = b.anim("idle", 3.5, "loop")
    idle.wave("neck", "rot", 1, 6, steps=8).wave("head", "rot", 2, 8, steps=8).wave("body", "pos", 1, 0.2, steps=8)
    walk = b.anim("walk", 0.9, "loop")
    run = b.anim("run", 0.45, "loop")
    for anim, amp in ((walk, 26), (run, 46)):
        for side, sx in (("l", 1), ("r", -1)):
            ph = 0 if sx > 0 else math.pi
            anim.wave(f"leg_{side}", "rot", 0, amp, phase=ph, steps=8)
            anim.wave(f"shin_{side}", "rot", 0, amp * 0.5, phase=ph + 1.2, steps=8)
        anim.wave("body", "pos", 1, 0.5 if amp > 30 else 0.3, steps=8).wave("neck", "rot", 0, 6, steps=8)
    return b


if __name__ == "__main__":
    make("ostrich", build, P, {}, anim="walk")
