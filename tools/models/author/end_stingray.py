"""Raia do Fim (End Stingray): raia plana de obsidiana com veios de ametista incandescentes, ventre de pedra do End,
olhos magenta, asas em três placas que ondulam, crista de blocos no dorso e cauda longa de segmentos terminando num
cacho de cristais de ametista. Passiva, plana em círculos largos sobre as ilhas do Fim.
Referência: prancha "End Stingray - Mob Concept Art"."""
import math

from kit import make
from style import R, Designer, rgb

OBS = R("#08060e", "#110b1c", "#1c1330", "#2b1d4a", "#3b2a66")
PINK = rgb("#ff59e6")
VIOLET = rgb("#9b4cff")
BELLY = R("#9a9560", "#b8b37a", "#d3cf96", "#ebe8b8")
EYE = R("#8a1a86", "#d83ad2", "#ff6af2", "#ffd0fb")
CRYS = R("#6a3ab8", "#9a66e8", "#c9a4ff", "#f0e0ff")
TAIL = R("#120c20", "#1d1432", "#2c2050", "#40306e")

P = dict(span=1.0)


def shell(base=2.4):
    def paint(cv):
        cv.noise(OBS, base + (0.7 if cv.face == "up" else -1.0 if cv.face == "down" else 0), 0.6)
        cv.speckle(OBS[4], 0.06)
        cv.darken_edges(-0.2)
    return paint


def veins(base=2.4, count=3):
    """Placa dorsal: obsidiana escura cortada por veios magenta que ramificam."""
    def paint(cv):
        shell(base)(cv)
        if cv.face != "up":
            return
        for _ in range(count):
            x, y = int(cv.rng.integers(1, max(2, cv.w - 1))), int(cv.rng.integers(0, cv.h))
            dx, dy = (1, 0) if cv.rng.random() < 0.5 else (0, 1)
            for step in range(int(cv.rng.integers(5, 11))):
                cv.set(x, y, PINK if step % 4 else VIOLET)
                if cv.rng.random() < 0.3:
                    dx, dy = dy, dx
                x, y = x + dx, y + dy
    return paint


def underside(cv):
    if cv.face == "down":
        cv.noise(BELLY, 1.8, 0.5)
        cv.darken_edges(-0.2)
    else:
        cv.noise(OBS, 2.0, 0.5)
        cv.rect(0, cv.h // 2, cv.w, cv.h - cv.h // 2, BELLY[1])           # metade de baixo clara
        cv.speckle(BELLY[2], 0.08, mask=lambda i, j: j >= cv.h // 2)


def build(p=P):
    b = Designer("end_stingray", res=(160, 96))
    y0 = 10.0
    b.bone("root", origin=(0, y0, 0))
    b.bone("body", "root", origin=(0, y0 + 2, 0))
    b.box("body", "disc", (14, 3.8, 15), (0, y0, 1.0))
    b.skin("disc", underside)
    b.box("body", "disc_top", (14.6, 1.8, 15.6), (0, y0 + 3.8, 1.0))
    b.drop_face("disc_top", "down")
    b.skin("disc_top", veins(2.8, 4))
    # cabeça e olhos
    b.box("body", "snout", (9.6, 3.0, 5.4), (0, y0 + 0.6, -9.2))
    b.skin("snout", underside)
    b.box("body", "snout_top", (10.2, 1.4, 5.6), (0, y0 + 3.6, -9.2))
    b.drop_face("snout_top", "down")
    b.skin("snout_top", shell(3.0))
    b.bone("eyes_glow", "body", origin=(0, y0 + 3, -11.8), glow=True)
    for name, x in (("eye_l", 3.2), ("eye_r", -3.2)):
        b.box("eyes_glow", name, (1.8, 1.5, 0.5), (x, y0 + 2.4, -12.1))
        b.skin(name, lambda cv: cv.fill(EYE[2]) if cv.face == "north" else cv.fill(EYE[1]))
    # crista dorsal: blocos pequenos em fila
    for k, z in enumerate((-4.0, -1.4, 1.2, 3.8, 6.2)):
        n = f"crest{k}"
        b.box("body", n, (2.2, 1.4, 1.6), (0, y0 + 5.6, z))
        b.skin(n, lambda cv: cv.noise(CRYS, 1.4 + (0.8 if cv.face == "up" else 0), 0.4))
    # asas: três placas encadeadas, cada uma bate um pouco depois da anterior
    spans = ((9.0, 1.8, 13.5, 0.0), (8.2, 1.6, 11.5, 1.2), (7.0, 1.4, 9.5, 2.0))
    for side, sx in (("l", 1), ("r", -1)):
        parent = "body"
        x = 6.8
        for k, (wd, th, dp, back) in enumerate(spans):
            n = f"wing{k}_{side}"
            b.bone(n, parent, origin=(sx * x, y0 + 2.2, 1.0 + back), rot=(0, 0, 0))
            x0 = x if sx > 0 else -x - wd
            b.box(n, n, (wd, th, dp), (x0, y0 + 2.2 - th / 2 + 0.2, 1.0 + back - dp / 2), anchor="corner")
            b.skin(n, veins(2.6, 3 - k // 2))
            parent = n
            x += wd - 0.4
    # cauda: seis segmentos com pontos magenta e o cacho de cristais na ponta
    parent = "body"
    z = 8.0
    for k in range(6):
        n = f"tail{k}"
        length = 3.4
        b.bone(n, parent, origin=(0, y0 + 1.6, z))
        w = max(2.0, 4.2 - k * 0.35)
        b.box(n, n, (w, w * 0.8, length), (0, y0 + 1.6 - w * 0.4, z + length / 2 - 0.2))
        b.skin(n, lambda cv: (cv.noise(TAIL, 2.2, 0.5), cv.set(cv.w // 2, cv.h // 2, VIOLET) if cv.face in ("east", "west") else None, cv.darken_edges(-0.2)))
        b.box(n, n + "_stud", (1.0, 0.7, 1.0), (0, y0 + 1.6 + w * 0.4, z + length / 2))
        b.skin(n + "_stud", lambda cv: cv.noise(CRYS, 2.4, 0.3))
        parent = n
        z += length
    b.bone("stinger", parent, origin=(0, y0 + 1.6, z))
    for k, (dx, dy, h, tilt) in enumerate(((0, 0, 5.0, 0), (1.3, 0.4, 3.6, 24), (-1.3, 0.2, 3.2, -26), (0.4, -0.2, 2.6, 40))):
        n = f"crystal{k}"
        b.box("stinger", n, (1.4, 1.4, h), (dx, y0 + 1.6 + dy, z + h / 2 + 0.4), rot=(0, tilt, 0), origin=(0, y0 + 1.6, z))
        b.skin(n, lambda cv: cv.noise(CRYS, 2.2 + (0.8 if cv.face in ("up", "north") else 0), 0.4))

    fly = b.anim("fly", 2.4, "loop")
    fly.wave("root", "pos", 1, 1.1, steps=8)
    for side, sx in (("l", 1), ("r", -1)):
        for k, amp in enumerate((10, 16, 22)):
            fly.wave(f"wing{k}_{side}", "rot", 2, sx * amp, phase=-k * 0.7, steps=8)
    for k in range(6):
        fly.orbit(f"tail{k}", "rot", (5, 6, 0), (-k * 0.6, -k * 0.6 + 1.0, 0), steps=8)
    fly.orbit("stinger", "rot", (4, 8, 0), (-3.6, -2.6, 0), steps=8)
    fly.wave("body", "rot", 0, 3, steps=8)
    return b


if __name__ == "__main__":
    make("end_stingray", build, P, {}, anim="fly")
