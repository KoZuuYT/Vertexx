"""Anquilossauro de Ardósia (Slate Ankylosaurus): dino baixo e pesado de placas de ardósia sobrepostas, espinhos marrom-rosados
nas laterais, crânio blindado com olhos salmão, pernas grossas de três garras e cauda de clava maciça.
Referência: prancha "Slate Ankylosaurus - Mob Concept Art"."""
import math

from kit import make
from style import R, Designer, rgb

SLATE = R("#1f2226", "#2b3036", "#3a4047", "#4a5159", "#5d666f")
HIDE = R("#17191c", "#22252a", "#2f343a", "#3e444b")
SPIKE = R("#3b2a2f", "#553d43", "#6f5258", "#8a6a70")
CLAW = R("#2a2724", "#3d3934", "#544e46")
EYE = rgb("#eab9a2")
EYE_D = rgb("#7a4a40")

P = dict(h=1.0)


def plate(base=2.6, cracks=True):
    def paint(cv):
        cv.noise(SLATE, base + (0.8 if cv.face == "up" else -0.7 if cv.face == "down" else 0), 0.55)
        cv.speckle(SLATE[4], 0.07)
        if cracks and cv.face in ("up", "north", "south", "east", "west"):
            for _ in range(max(1, cv.w // 4)):
                i, j = int(cv.rng.integers(0, cv.w)), int(cv.rng.integers(0, cv.h))
                cv.hline(j, SLATE[0], i, min(cv.w, i + int(cv.rng.integers(2, 5))))
        cv.darken_edges(-0.2)
    return paint


def hide(cv):
    cv.noise(HIDE, 1.8 + (0.6 if cv.face == "up" else -0.5 if cv.face == "down" else 0), 0.55)
    cv.speckle(HIDE[3], 0.06)


def spike(cv):
    cv.noise(SPIKE, 2.0 + (0.8 if cv.face in ("up", "north") else 0), 0.45)


def build(p=P):
    b = Designer("deepslate_ankylo", res=(160, 96))
    b.bone("root")
    b.bone("body", "root", origin=(0, 8, 0))
    b.box("body", "belly", (12, 5.5, 21), (0, 5.2, -0.5))
    b.skin("belly", hide)
    # carapaça: placas transversais sobrepostas, crista central e saias laterais
    for k, z in enumerate((-8.0, -4.2, -0.4, 3.4, 7.2)):
        n = f"plate{k}"
        w = 13.6 - abs(k - 2) * 0.5
        b.box("body", n, (w, 2.6, 4.4), (0, 10.4 + (0.3 if k % 2 else 0), z))
        b.skin(n, plate(2.7))
    b.box("body", "ridge", (4.4, 1.2, 17), (0, 13.0, -0.4))
    b.skin("ridge", plate(3.2, False))
    for sx in (1, -1):
        n = "skirt_l" if sx > 0 else "skirt_r"
        b.box("body", n, (2.4, 4.4, 17.5), (sx * 7.0, 6.4, -0.5))
        b.skin(n, plate(2.2))
    # espinhos de ardósia nas costas e espetos marrom-rosados nos flancos
    for k, (z, hgt) in enumerate(((-6.0, 4.2), (-2.0, 5.0), (2.4, 4.6), (6.4, 3.6))):
        b.box("body", f"horn{k}", (1.8, hgt, 1.8), (0, 13.4, z), rot=(14, 0, 0), origin=(0, 13.4, z))
        b.skin(f"horn{k}", plate(3.0, False))
    for k, z in enumerate((-8.2, -4.0, 0.2, 4.4, 8.4)):
        for sx in (1, -1):
            n = f"flank{k}_{'l' if sx > 0 else 'r'}"
            b.box("body", n, (1.2, 1.2, 4.2), (sx * 8.3, 9.0, z), rot=(0, sx * -14, sx * -10), origin=(sx * 7.2, 9.0, z))
            b.skin(n, spike)
    # cabeça: crânio blindado, focinho curto, olhos salmão, chifres de bochecha
    b.bone("head", "body", origin=(0, 8.4, -10.5))
    b.box("head", "skull", (8.2, 5.2, 7.6), (0, 6.2, -14.2))
    b.skin("skull", lambda cv: (hide(cv), (cv.set(1, 1, EYE), cv.set(0, 1, EYE_D), cv.set(cv.w - 2, 1, EYE), cv.set(cv.w - 1, 1, EYE_D)) if cv.face == "north" else None))
    b.box("head", "snout", (6.4, 3.8, 3.6), (0, 6.0, -18.0))
    b.skin("snout", hide)
    b.box("head", "helm", (8.8, 1.8, 7.4), (0, 11.4, -14.0))
    b.drop_face("helm", "down")
    b.skin("helm", plate(2.9))
    b.box("head", "brow", (9.2, 1.2, 2.0), (0, 10.4, -16.8))
    b.drop_face("brow", "down")
    b.skin("brow", plate(2.4, False))
    for sx in (1, -1):
        n = "cheek_l" if sx > 0 else "cheek_r"
        b.box("head", n, (1.6, 1.6, 4.4), (sx * 5.2, 8.2, -13.0), rot=(0, sx * 24, 0), origin=(sx * 4.2, 8.2, -12.0))
        b.skin(n, spike)
    # cauda: três segmentos e uma clava maciça
    b.bone("tail", "body", origin=(0, 8.0, 9.5))
    b.box("tail", "tail", (5.2, 4.2, 7.5), (0, 6.2, 13.0))
    b.skin("tail", hide)
    b.box("tail", "tail_plate", (4.6, 1.2, 6.5), (0, 10.4, 13.0))
    b.drop_face("tail_plate", "down")
    b.skin("tail_plate", plate(2.6, False))
    b.bone("tail1", "tail", origin=(0, 8.0, 16.6))
    b.box("tail1", "tail1", (3.6, 3.4, 7.0), (0, 6.6, 20.2))
    b.skin("tail1", hide)
    for sx in (1, -1):
        n = "tail_spike_l" if sx > 0 else "tail_spike_r"
        b.box("tail1", n, (1.0, 1.0, 3.2), (sx * 2.5, 8.0, 18.5), rot=(0, sx * -16, 0), origin=(sx * 1.8, 8.0, 18.0))
        b.skin(n, spike)
    b.bone("club", "tail1", origin=(0, 8.0, 23.6))
    b.box("club", "club", (7.6, 5.4, 6.4), (0, 5.4, 26.6))
    b.skin("club", plate(2.4))
    b.box("club", "club_top", (6.4, 1.2, 5.4), (0, 10.8, 26.6))
    b.drop_face("club_top", "down")
    b.skin("club_top", plate(3.1, False))
    # pernas curtas e grossas, de três garras
    for side, sx in (("l", 1), ("r", -1)):
        for k, (z, big) in enumerate(((-7.0, 0), (6.4, 0.6))):
            n = f"leg{k}_{side}"
            wd = 4.2 + big
            b.bone(n, "body", origin=(sx * 6.0, 7.6, z))
            b.box(n, n, (wd, 7.0, wd + 0.2), (sx * 6.0, 1.0, z))
            b.skin(n, hide)
            b.box(n, n + "_foot", (wd + 0.6, 1.3, wd + 1.6), (sx * 6.0, 0.0, z - 0.5))
            b.skin(n + "_foot", plate(1.8, False))
            for c, dx in enumerate((-1.4, 0, 1.4)):
                b.box(n, f"{n}_claw{c}", (0.9, 0.9, 1.5), (sx * 6.0 + dx, 0.15, z - wd / 2 - 1.6))
                b.skin(f"{n}_claw{c}", lambda cv: cv.noise(CLAW, 1.4, 0.4))
    idle = b.anim("idle", 3.5, "loop")
    idle.wave("head", "rot", 1, 5, steps=8).wave("tail", "rot", 1, 6, steps=8).wave("body", "pos", 1, 0.15, steps=8)
    idle.wave("tail1", "rot", 1, 7, phase=0.8, steps=8).wave("club", "rot", 1, 5, phase=1.6, steps=8)
    walk = b.anim("walk", 0.9, "loop")
    for k in range(2):
        for side, sx in (("l", 1), ("r", -1)):
            ph = (k + (0 if sx > 0 else 1)) * math.pi
            walk.wave(f"leg{k}_{side}", "rot", 0, 22, phase=ph, steps=8)
    walk.wave("body", "rot", 2, 1.5, steps=8).wave("body", "pos", 1, 0.25, steps=8)
    walk.wave("tail", "rot", 1, 9, steps=8).wave("tail1", "rot", 1, 12, phase=0.9, steps=8).wave("club", "rot", 1, 8, phase=1.8, steps=8)
    walk.wave("head", "rot", 1, 4, phase=1.2, steps=8)
    slam = b.anim("slam", 0.9, "once")                         # clava erguida e batida no chão
    slam.key("tail", "rot", 0, (0, 0, 0)).key("tail", "rot", 0.3, (-42, 0, 0)).key("tail", "rot", 0.45, (14, 0, 0)).key("tail", "rot", 0.9, (0, 0, 0))
    slam.key("tail1", "rot", 0, (0, 0, 0)).key("tail1", "rot", 0.3, (-26, 0, 0)).key("tail1", "rot", 0.45, (22, 0, 0)).key("tail1", "rot", 0.9, (0, 0, 0))
    slam.key("body", "rot", 0, (0, 0, 0)).key("body", "rot", 0.3, (8, 0, 0)).key("body", "rot", 0.45, (-5, 0, 0)).key("body", "rot", 0.9, (0, 0, 0))
    slam.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.3, (-8, 0, 0)).key("head", "rot", 0.9, (0, 0, 0))
    return b


if __name__ == "__main__":
    make("deepslate_ankylo", build, P, {}, anim="walk")
