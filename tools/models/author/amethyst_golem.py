"""Golem de Ametista: base de bloco de ametista, torso de calcita e cabeça de abóbora entalhada, com cristais nos braços.
Referência: concept art da Especificação Técnica (Overworld Revival)."""
from kit import make
from style import R, Designer, rgb

AME = R("#33205c", "#4e2f8a", "#6c46b8", "#8d68db", "#b490f4", "#d6bcff")
CAL = R("#9c988f", "#bcb8ae", "#d4d0c5", "#e6e2d8", "#f3f0e8")
PUMP = R("#6e3206", "#94490b", "#bd6a12", "#da8a1f", "#efae3c")
DARK = rgb("#241206")
GLOW = R("#ffb030", "#ffd860", "#fff2b0")

P = dict(base=7.0, torso=6.0, head=7.0)


def amethyst(cv, scale=2.4):
    cv.noise(AME, scale + (0.8 if cv.face == "up" else -0.8 if cv.face == "down" else 0), 0.5)
    for _ in range(max(2, cv.w * cv.h // 18)):
        cv.set(int(cv.rng.integers(0, cv.w)), int(cv.rng.integers(0, cv.h)), AME[5])


def calcite(cv):
    cv.noise(CAL, 2.4, 0.5)
    cv.speckle(CAL[0], 0.08)


def pumpkin(cv):
    cv.noise(PUMP, 2.2 + (0.9 if cv.face == "up" else 0), 0.5)
    if cv.face in ("north", "south", "east", "west"):
        for i in range(0, cv.w, 2):                       # gomos verticais
            for j in range(cv.h):
                cv.set(i, j, PUMP[1])
    if cv.face == "north":                                # rosto entalhado
        w, h = cv.w, cv.h
        for (ex, ey) in ((1, 1), (w - 3, 1)):
            for dx in range(2):
                for dy in range(2):
                    cv.set(ex + dx, ey + dy, DARK)
        for i in range(1, w - 1):
            cv.set(i, h - 2, DARK)
        cv.set(1, h - 3, DARK)
        cv.set(w - 2, h - 3, DARK)


def build(p=P):
    b = Designer("amethyst_golem", res=(64, 64))
    bw, tw, hw = p["base"], p["torso"], p["head"]
    b.bone("root")
    b.bone("body", "root", origin=(0, 0, 0))
    b.box("body", "base", (bw, 6, bw), (0, 2.0, 0))
    b.skin("base", amethyst)
    b.box("body", "torso", (tw, 5, tw - 1), (0, 8.0, 0))
    b.skin("torso", calcite)
    b.bone("head", "body", origin=(0, 10.5, 0))
    b.box("head", "head", (hw, hw, hw), (0, 10.5 + hw / 2, 0))
    b.skin("head", pumpkin)
    b.box("head", "stem", (1.4, 1.8, 1.4), (0, 10.5 + hw + 0.9, 0))
    b.skin("stem", lambda cv: cv.noise(AME, 1.0, 0.4))
    b.bone("glow", "head", origin=(0, 10.5 + hw / 2, -hw / 2), glow=True)
    for name, x in (("eye_l", 1.7), ("eye_r", -1.7)):
        b.box("glow", name, (1.6, 1.6, 0.4), (x, 10.5 + hw * 0.62, -hw / 2 - 0.15))
        b.skin(name, lambda cv: cv.fill(GLOW[1]))
    for side, sx in (("l", 1), ("r", -1)):
        n = f"arm_{side}"
        b.bone(n, "body", origin=(sx * (tw / 2 + 0.5), 11.5, 0), rot=(0, 0, sx * 12))
        b.box(n, n, (1.8, 5.5, 1.8), (sx * (tw / 2 + 1.4), 8.6, 0))
        b.skin(n, lambda cv: amethyst(cv, 3.0))
        c = f"crys_{side}"
        b.bone(c, n, origin=(sx * (tw / 2 + 1.4), 6.0, 0), rot=(0, 0, sx * 22))
        b.box(c, c, (1.2, 3.2, 1.2), (sx * (tw / 2 + 1.9), 4.8, 0))
        b.skin(c, lambda cv: amethyst(cv, 4.0))
    for k, (x, z, h) in enumerate(((-1.8, 2.6, 4.0), (1.4, 3.0, 5.0), (0, 3.4, 3.0))):
        n = f"spike{k}"
        b.bone(n, "body", origin=(x, 4.0, z), rot=(14, 0, (k - 1) * 12))
        b.box(n, n, (1.4, h, 1.4), (x, 4.0 + h / 2, z))
        b.skin(n, lambda cv: amethyst(cv, 4.2))
    idle = b.anim("idle", 3.0, "loop")
    idle.wave("body", "pos", 1, 0.5, steps=8)
    idle.wave("head", "rot", 1, 3, steps=8)
    for side, sx in (("l", 1), ("r", -1)):
        idle.wave(f"arm_{side}", "rot", 2, sx * 5, steps=8)
    listen = b.anim("listen", 0.8, "loop")                 # inclina a cabeça e balança os cristais ao ouvir um som
    listen.wave("head", "rot", 2, 10, steps=6).wave("body", "pos", 1, 1.1, steps=6)
    for side, sx in (("l", 1), ("r", -1)):
        listen.wave(f"arm_{side}", "rot", 2, sx * 16, steps=6)
        listen.wave(f"crys_{side}", "rot", 2, sx * 14, phase=0.4, steps=6)
    return b


if __name__ == "__main__":
    make("amethyst_golem", build, P, {}, anim="idle")
