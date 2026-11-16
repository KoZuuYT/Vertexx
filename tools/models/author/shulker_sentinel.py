"""Sentinela de Shulker: guardião flutuante com núcleo luminoso, placas de casca de shulker e cauda em funil (estilo Breeze)."""
from kit import make
from style import R, Designer

SHELL = R("#2c1840", "#43255f", "#5e3680", "#7a4aa0", "#9a64c0", "#b78ae0")
DARK = R("#120a1e", "#1c1130", "#281a44")
CORE = R("#c8a000", "#ffd84a", "#fff6a0", "#ffffff")

P = dict(core=6.0)


def shell(cv, base=3.0):
    cv.noise(SHELL, base + (0.8 if cv.face == "up" else -0.8 if cv.face == "down" else 0), 0.55)
    if cv.face not in ("up", "down"):
        cv.hline(0, DARK[1]).hline(cv.h - 1, DARK[0])


def build(p=P):
    b = Designer("shulker_sentinel", res=(64, 64))
    c = p["core"]
    y0 = 12.0
    b.bone("root", origin=(0, y0, 0))
    b.bone("core", "root", origin=(0, y0 + c / 2 + 3, 0), glow=True)
    b.box("core", "core", (c, c, c), (0, y0 + 3, 0))
    b.skin("core", lambda cv: cv.noise(CORE, 2.2, 0.35))
    b.bone("cage", "root", origin=(0, y0 + c / 2 + 3, 0))
    for i, (x, z, w, d) in enumerate(((c / 2 + 2, 0, 2, 7), (-c / 2 - 2, 0, 2, 7), (0, c / 2 + 2, 7, 2), (0, -c / 2 - 2, 7, 2))):
        n = f"plate{i}"
        b.box("cage", n, (w, c + 2, d), (x, y0 + 2, z))
        b.skin(n, shell)
    b.box("cage", "lid", (c + 4, 2, c + 4), (0, y0 + c + 4, 0))
    b.skin("lid", lambda cv: shell(cv, 3.6))
    b.bone("tail", "root", origin=(0, y0 + 2, 0))
    for i, (w, h) in enumerate(((9, 3), (7, 3), (5, 3), (3, 3))):
        n = f"ring{i}"
        b.bone(n, "tail", origin=(0, y0 + 2 - i * 3, 0))
        b.box(n, n, (w, h, w), (0, y0 - 1 - i * 3, 0))
        b.skin(n, lambda cv: shell(cv, 2.0))
    idle = b.anim("idle", 3.0, "loop")
    idle.wave("root", "pos", 1, 0.9, steps=8)
    for i in range(4):
        idle.orbit(f"ring{i}", "rot", (0, 14 * (1 + i * 0.2), 0), (0, i * 0.8, 0), steps=8)
    idle.key("cage", "rot", 0, (0, 0, 0)).key("cage", "rot", 1.5, (0, 180, 0)).key("cage", "rot", 3.0, (0, 360, 0))
    shoot = b.anim("shoot", 0.6, "once")
    shoot.key("cage", "rot", 0, (0, 0, 0)).key("cage", "rot", 0.25, (-18, 0, 0)).key("cage", "rot", 0.6, (0, 0, 0))
    shoot.key("core", "pos", 0, (0, 0, 0)).key("core", "pos", 0.25, (0, 0, -2)).key("core", "pos", 0.6, (0, 0, 0))
    return b


if __name__ == "__main__":
    make("shulker_sentinel", build, P, {}, anim="idle")
