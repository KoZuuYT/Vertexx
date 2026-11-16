"""Suricato (Meerkat): pequeno, em pé sobre as patas traseiras, pelagem de areia com manchas escuras nos olhos."""
import math

from kit import make
from style import R, Designer, rgb

FUR = R("#8a6a42", "#a88556", "#c4a06a", "#dcbc84", "#ecd4a4")
BELLY = R("#c9a876", "#ddc191", "#eed7ab", "#f6e6c4")
DARK = R("#1c130c", "#33241a", "#4a3626")
NOSE = rgb("#1a1210")

P = dict(h=2.0)


def fur(cv, base=2.6):
    cv.noise(FUR, base + (0.8 if cv.face == "up" else -0.8 if cv.face == "down" else 0), 0.55)


def head_skin(cv):
    fur(cv, 2.8)
    if cv.face == "north":
        w, h = cv.w, cv.h
        for x in (0, w - 2):                       # manchas escuras ao redor dos olhos
            for y in range(1, 3):
                for dx in range(2):
                    cv.set(x + dx, y, DARK[1])
        cv.set(1, 1, rgb("#0a0806"))
        cv.set(w - 2, 1, rgb("#0a0806"))
        cv.set(w // 2, h - 1, NOSE)


def build(p=P):
    b = Designer("meerkat", res=(64, 32))
    b.bone("root")
    b.bone("body", "root", origin=(0, 4.5, 0))
    b.box("body", "torso", (3.6, 5.0, 3.0), (0, 7.0, 0))
    b.skin("torso", lambda cv: cv.noise(BELLY, 2.2, 0.5) if cv.face == "north" else fur(cv))
    b.bone("head", "body", origin=(0, 9.5, -0.2))
    b.box("head", "head", (3.6, 3.2, 3.4), (0, 11.2, -0.4))
    b.skin("head", head_skin)
    b.box("head", "snout", (1.6, 1.4, 1.4), (0, 10.5, -2.8))
    b.skin("snout", lambda cv: cv.noise(BELLY, 1.6, 0.4))
    for name, x in (("ear_l", 1.5), ("ear_r", -1.5)):
        b.box("head", name, (0.8, 0.9, 0.5), (x, 13.0, -0.2))
        b.skin(name, lambda cv: cv.noise(DARK, 1.4, 0.4))
    for side, sx in (("l", 1), ("r", -1)):
        a = f"arm_{side}"
        b.bone(a, "body", origin=(sx * 2.0, 9.2, -0.6))
        b.box(a, a, (0.9, 3.0, 0.9), (sx * 2.2, 7.8, -0.8))
        b.skin(a, lambda cv: fur(cv, 2.0))
        l = f"leg_{side}"
        b.bone(l, "root", origin=(sx * 1.1, 4.6, 0))
        b.box(l, l, (1.3, 4.6, 1.5), (sx * 1.1, 2.3, 0.1))
        b.skin(l, lambda cv: fur(cv, 2.0))
        b.box(l, f"foot_{side}", (1.3, 0.6, 2.4), (sx * 1.1, 0.3, -0.4))
        b.skin(f"foot_{side}", lambda cv: cv.noise(DARK, 1.4, 0.4))
    b.bone("tail", "body", origin=(0, 4.8, 1.4), rot=(35, 0, 0))
    b.box("tail", "tail", (0.9, 0.9, 5.5), (0, 4.4, 4.0))
    b.skin("tail", lambda cv: cv.vgrad(FUR, 3.0, 0, 0.5) if cv.face in ("east", "west") else fur(cv, 1.8))
    idle = b.anim("idle", 3.2, "loop")
    idle.wave("head", "rot", 1, 10, steps=8).wave("body", "pos", 1, 0.15, steps=8)
    idle.wave("tail", "rot", 2, 6, steps=8)
    walk = b.anim("walk", 0.5, "loop")
    for side, sx in (("l", 1), ("r", -1)):
        ph = 0 if sx > 0 else math.pi
        walk.orbit(f"leg_{side}", "rot", (0, 0, 0), (0, 0, 0), steps=1) if False else None
        walk.wave(f"leg_{side}", "rot", 0, sx * 28, phase=ph, steps=6)
        walk.wave(f"arm_{side}", "rot", 0, -sx * 20, phase=ph, steps=6)
    walk.wave("body", "pos", 1, 0.25, steps=6).wave("tail", "rot", 2, 10, steps=6)
    dig = b.anim("dig", 1.2, "once")                          # inclina o corpo e raspa a areia
    dig.key("body", "rot", 0, (0, 0, 0)).key("body", "rot", 0.3, (-48, 0, 0)).key("body", "rot", 1.0, (-48, 0, 0)).key("body", "rot", 1.2, (0, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        for t in (0.3, 0.5, 0.7, 0.9):
            sgn = 1 if int(t * 10) % 4 == 3 or t in (0.5, 0.9) else -1
            dig.key(f"arm_{side}", "rot", t, (sgn * 55 * (1 if sx > 0 else -1), 0, 0))
    return b


if __name__ == "__main__":
    make("meerkat", build, P, {}, anim="idle")
