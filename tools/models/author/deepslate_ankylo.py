"""Anquilossauro de Ardósia: dino blindado de placas de deepslate, cauda de clava de ametista, olhos âmbar."""
import math

from kit import make
from style import R, Designer, rgb

SLATE = R("#1c1c20", "#2a2a30", "#3a3a42", "#4c4c56", "#62626e")
SKIN = R("#4a4540", "#5e574f", "#766d62", "#8c8174")
AMB = R("#8a4a06", "#d98a14", "#ffc23a", "#fff0a0")
CRYS = R("#3d2670", "#6a46b8", "#9a78e0", "#cbb0ff")

P = dict(h=1.0)


def plate(cv):
    cv.noise(SLATE, 2.4 + (0.8 if cv.face == "up" else 0), 0.6)
    cv.speckle(SLATE[4], 0.08)


def build(p=P):
    b = Designer("deepslate_ankylo", res=(128, 64))
    b.bone("root")
    b.bone("body", "root", origin=(0, 4.5, 0))
    b.box("body", "belly", (8, 4, 12), (0, 6.5, 0))
    b.skin("belly", lambda cv: cv.noise(SKIN, 2.2, 0.5))
    b.box("body", "shell", (9, 3.5, 11), (0, 10.0, 0))
    b.skin("shell", plate)
    for k, z in enumerate((-3.8, -1.2, 1.4, 4.0)):                # placas sobrepostas
        n = f"plate{k}"
        b.box("body", n, (7, 1.2, 2.2), (0, 12.7, z))
        b.skin(n, plate)
    for k, (x, z) in enumerate(((-3.6, -3), (3.6, -3), (-3.6, 3), (3.6, 3))):
        n = f"spike{k}"
        b.box("body", n, (1.2, 2.2, 1.2), (x, 12.6, z))
        b.skin(n, lambda cv: cv.noise(CRYS, 2.8, 0.5))
    b.bone("head", "body", origin=(0, 8, -6))
    b.box("head", "head", (5, 3.6, 5), (0, 7.5, -8.6))
    b.skin("head", lambda cv: (cv.noise(SKIN, 2.4, 0.5), cv.set(0, 1, AMB[2]) if cv.face == "north" else None,
                               cv.set(cv.w - 1, 1, AMB[2]) if cv.face == "north" else None)[0])
    b.box("head", "brow", (5.4, 1.2, 3), (0, 9.6, -8.2))
    b.skin("brow", plate)
    b.bone("tail", "body", origin=(0, 6.5, 6))
    b.box("tail", "tail", (3.2, 2.6, 7), (0, 6.5, 9.5))
    b.skin("tail", lambda cv: cv.noise(SKIN, 2.2, 0.5))
    b.box("tail", "club", (5, 4, 4), (0, 6.6, 14.5))
    b.skin("club", lambda cv: cv.noise(CRYS, 2.6, 0.6))
    for side, sx in (("l", 1), ("r", -1)):
        for k, z in enumerate((-4, 4)):
            n = f"leg{k}_{side}"
            b.bone(n, "body", origin=(sx * 3.8, 5.5, z))
            b.box(n, n, (2.4, 5.5, 2.6), (sx * 3.8, 2.7, z))
            b.skin(n, lambda cv: cv.noise(SKIN, 1.8, 0.5))
    idle = b.anim("idle", 3.5, "loop")
    idle.wave("head", "rot", 1, 5, steps=8).wave("tail", "rot", 2, 6, steps=8).wave("body", "pos", 1, 0.15, steps=8)
    walk = b.anim("walk", 0.8, "loop")
    for k in range(2):
        for side, sx in (("l", 1), ("r", -1)):
            ph = (k + (0 if sx > 0 else 1)) * math.pi
            walk.wave(f"leg{k}_{side}", "rot", 0, 24, phase=ph, steps=6)
    walk.wave("body", "rot", 2, 2, steps=6).wave("tail", "rot", 2, 10, steps=6).wave("body", "pos", 1, 0.2, steps=6)
    slam = b.anim("slam", 0.9, "once")                         # cauda de clava no chão
    slam.key("tail", "rot", 0, (0, 0, 0)).key("tail", "rot", 0.3, (-60, 0, 0)).key("tail", "rot", 0.45, (25, 0, 0)).key("tail", "rot", 0.9, (0, 0, 0))
    slam.key("body", "rot", 0, (0, 0, 0)).key("body", "rot", 0.3, (10, 0, 0)).key("body", "rot", 0.45, (-6, 0, 0)).key("body", "rot", 0.9, (0, 0, 0))
    return b


if __name__ == "__main__":
    make("deepslate_ankylo", build, P, {}, anim="walk")
