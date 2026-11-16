"""Abutre (Vulture): ave grande de asas largas, pescoço pelado e gola clara, bico curvo."""
import math

from kit import make
from style import R, Designer, rgb

BROWN = R("#2a1d14", "#3d2b1d", "#52392a", "#6b4a36")
RUFF = R("#b9b2a2", "#d3ccbc", "#e8e2d4", "#f6f1e6")
SKIN = R("#a8626a", "#c0777e", "#d68e92")
BEAK = R("#8e8a82", "#b0aca2", "#cfcac0")

P = dict(span=13.0)


def feather(cv):
    cv.noise(BROWN, 2.4, 0.7)
    if cv.face == "up":
        cv.speckle(BROWN[3], 0.1)


def build(p=P):
    b = Designer("vulture", res=(128, 64))
    sp = p["span"]
    b.bone("root", origin=(0, 8, 0))
    b.bone("body", "root", origin=(0, 8, 0))
    b.box("body", "torso", (5, 4.5, 9), (0, 8, 0))
    b.skin("torso", feather)
    b.bone("tail", "body", origin=(0, 8, 4.5), rot=(-8, 0, 0))
    b.box("tail", "tail", (4, 0.8, 5), (0, 8, 7.4))
    b.skin("tail", feather)
    b.bone("neck", "body", origin=(0, 9, -4))
    b.box("neck", "ruff", (3.8, 2.4, 3.8), (0, 9.6, -4.8))
    b.skin("ruff", lambda cv: cv.noise(RUFF, 2.4, 0.7))
    b.box("neck", "neck", (1.6, 3, 1.6), (0, 11.4, -5.6))
    b.skin("neck", lambda cv: cv.noise(SKIN, 1.8, 0.5))
    b.bone("head", "neck", origin=(0, 13, -5.8))
    b.box("head", "head", (2.6, 2.6, 3.0), (0, 14, -6.4))
    b.skin("head", lambda cv: (cv.noise(SKIN, 2.0, 0.5), cv.set(0, 1, rgb("#0a0a0a")) if cv.face == "north" else None,
                               cv.set(cv.w - 1, 1, rgb("#0a0a0a")) if cv.face == "north" else None)[0])
    b.box("head", "beak", (1.2, 1.2, 2.2), (0, 13.4, -8.5))
    b.skin("beak", lambda cv: cv.noise(BEAK, 1.6, 0.4))
    b.box("head", "hook", (1.0, 0.8, 0.9), (0, 12.6, -9.3))
    b.skin("hook", lambda cv: cv.noise(BEAK, 1.2, 0.4))
    for side, sx in (("l", 1), ("r", -1)):
        w = f"wing_{side}"
        b.bone(w, "body", origin=(sx * 2.4, 9.8, -0.6))
        x0 = 2.4 if sx > 0 else -2.4 - sp
        b.box(w, w, (sp, 0.8, 6.0), (x0, 9.8, -3.6), anchor="corner")
        b.skin(w, feather)
        t = f"tip_{side}"
        b.bone(t, w, origin=(sx * (2.4 + sp), 9.8, -0.6))
        x1 = 2.4 + sp if sx > 0 else -2.4 - sp - 6
        b.box(t, t, (6, 0.6, 4.4), (x1, 9.8, -2.8), anchor="corner")
        b.skin(t, feather)
        l = f"leg_{side}"
        b.bone(l, "body", origin=(sx * 1.2, 6.0, 1))
        b.box(l, l, (0.8, 2.2, 0.8), (sx * 1.2, 4.9, 1))
        b.skin(l, lambda cv: cv.noise(BEAK, 1.2, 0.4))
    fly = b.anim("fly", 1.4, "loop")
    glide = b.anim("glide", 3.0, "loop")
    for side, sx in (("l", 1), ("r", -1)):
        fly.wave(f"wing_{side}", "rot", 2, sx * 34, steps=10)
        fly.wave(f"tip_{side}", "rot", 2, sx * 24, phase=0.9, steps=10)
        glide.wave(f"wing_{side}", "rot", 2, sx * 5, steps=8)
        glide.wave(f"tip_{side}", "rot", 2, sx * 8, phase=0.6, steps=8)
    fly.wave("root", "pos", 1, 0.6, steps=10)
    glide.wave("root", "pos", 1, 0.2, steps=8).wave("tail", "rot", 0, 4, steps=8)
    return b


if __name__ == "__main__":
    make("vulture", build, P, {}, anim="glide")
