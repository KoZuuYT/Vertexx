"""Sentinela de Shulker (Shulker Sentry, variante Breeze): relicário de painéis roxos com moldura verde-azulada. Aberto, a
tampa flutua sobre uma base em U e, no meio, um núcleo de Breeze gira dentro de anéis de vento. Hostil raro das ruínas do End.
Referência: prancha "Shulker Sentry - Breeze Variant" (estado fechado, aberto e disparo)."""
from kit import make
from style import R, Designer, rgb

PURPLE = R("#5c3a66", "#7a4d86", "#965a9c", "#b078b4", "#c896cb")
TEAL = R("#1f5a52", "#2b7a6c", "#3d9a86", "#5cbfa5", "#86dcc4")
CORE = R("#5e8a86", "#79a6a0", "#98c6bf", "#bfe6dd", "#e8fffa")
WIND = R("#4a9aa8", "#7fd6e0", "#b8f4f6", "#e4ffff", "#c9a8ee")
RUNE = rgb("#6df2ff")
DARK = rgb("#0c1c1e")
WHITE = rgb("#f4fffe")

P = dict(w=14.0)


def panel(cv):
    cv.noise(PURPLE, 2.4 + (0.6 if cv.face == "up" else -0.8 if cv.face == "down" else 0), 0.5)
    cv.speckle(PURPLE[4], 0.05)
    cv.darken_edges(-0.22)


def frame(cv):
    cv.noise(TEAL, 2.4 + (0.5 if cv.face == "up" else -0.7 if cv.face == "down" else 0), 0.45)
    cv.speckle(TEAL[4], 0.06)
    cv.darken_edges(-0.25)


def rune(cv):
    """Painel de cima da tampa: roxo com o glifo em espiral ciano."""
    panel(cv)
    if cv.face == "up":
        cx, cy = cv.w // 2, cv.h // 2
        for (i, j) in ((-3, -2), (-2, -2), (-1, -2), (0, -2), (1, -2), (2, -2), (3, -1), (3, 0), (-3, -1), (-3, 0), (-2, 0),
                       (-1, 0), (0, 0), (1, 0), (2, 1), (3, 2), (2, 2), (1, 2), (0, 2), (-1, 2), (-2, 2), (-1, 1), (0, 1)):
            cv.set(cx + i, cy + j, RUNE)
    elif cv.face != "down":
        cv.hline(cv.h // 2, TEAL[3])


def build(p=P):
    b = Designer("shulker_sentinel", res=(128, 96))
    w = p["w"]
    b.bone("root")
    # base em U: laje e quatro pilares de canto
    b.bone("base", "root", origin=(0, 0, 0))
    b.box("base", "base_slab", (w, 3.0, w), (0, 0, 0))
    b.skin("base_slab", panel)
    for k, (sx, sz) in enumerate(((1, 1), (-1, 1), (1, -1), (-1, -1))):
        n = f"base_post{k}"
        b.box("base", n, (3.6, 4.4, 3.6), (sx * (w / 2 - 1.8), 3.0, sz * (w / 2 - 1.8)))
        b.skin(n, frame)
    b.box("base", "base_inner", (w - 7.4, 1.0, w - 7.4), (0, 3.0, 0))
    b.skin("base_inner", lambda cv: cv.noise(TEAL, 1.0, 0.4))
    # tampa flutuante, espelho da base
    b.bone("lid", "root", origin=(0, 17, 0))
    b.box("lid", "lid_slab", (w, 3.0, w), (0, 21.0, 0))
    b.skin("lid_slab", rune)
    for k, (sx, sz) in enumerate(((1, 1), (-1, 1), (1, -1), (-1, -1))):
        n = f"lid_post{k}"
        b.box("lid", n, (3.6, 4.4, 3.6), (sx * (w / 2 - 1.8), 16.6, sz * (w / 2 - 1.8)))
        b.skin(n, frame)
    # núcleo: a cabeça do Breeze flutuando entre as duas metades
    b.bone("core", "root", origin=(0, 12, 0))
    b.box("core", "core_head", (6.6, 6.6, 6.6), (0, 8.4, 0))
    b.skin("core_head", lambda cv: (cv.noise(CORE, 2.4, 0.45), cv.darken_edges(-0.2)))
    b.box("core", "core_brow", (7.2, 1.2, 7.2), (0, 13.4, 0), inflate=0.0)
    b.skin("core_brow", lambda cv: cv.noise(CORE, 1.4, 0.4))
    b.drop_face("core_brow", "down")
    b.bone("eyes_glow", "core", origin=(0, 12, -3.3), glow=True)
    for name, x in (("eye_l", 1.7), ("eye_r", -1.7)):
        b.box("eyes_glow", name, (1.8, 1.4, 0.5), (x, 10.6, -3.5))
        b.skin(name, lambda cv: cv.fill(WHITE) if cv.face == "north" else cv.fill(WIND[2]))
    # anéis de vento: molduras de painéis vazados girando em velocidades diferentes
    for k, (y, size, thick) in enumerate(((6.2, 11.6, 1.2), (10.0, 14.6, 1.3), (14.2, 11.0, 1.2))):
        ring = f"ring{k}"
        b.bone(ring, "root", origin=(0, y, 0))
        half = size / 2
        for s, (sx, sz, dx, dz) in enumerate(((size, thick, 0, half - thick / 2), (size, thick, 0, -half + thick / 2),
                                                (thick, size - 2 * thick, half - thick / 2, 0), (thick, size - 2 * thick, -half + thick / 2, 0))):
            n = f"{ring}_{s}"
            b.box(ring, n, (sx, 0.9, sz), (dx, y, dz))
            b.skin(n, lambda cv: (cv.noise(WIND, 2.2, 0.8), [cv.set(int(cv.rng.integers(0, cv.w)), int(cv.rng.integers(0, cv.h)), (0, 0, 0, 0)) for _ in range(max(1, cv.w * cv.h // 5))]))
    # estilhaços de pedra girando no vento
    for k, (x, y, z) in enumerate(((5.4, 8.0, 3.0), (-5.0, 11.0, -3.5), (2.0, 15.0, -5.2), (-3.0, 5.0, 5.0))):
        n = f"chip{k}"
        b.bone(n, "root", origin=(x, y, z))
        b.box(n, n, (1.4, 1.2, 1.4), (x, y, z), anchor="center")
        b.skin(n, lambda cv: cv.noise(R("#3a3a40", "#55555c", "#74747c"), 1.2, 0.5))

    spin = lambda anim, bone, turns: anim.key(bone, "rot", 0, (0, 0, 0)).key(bone, "rot", anim.anim.length, (0, 360 * turns, 0))
    idle = b.anim("idle", 4.0, "loop")
    idle.wave("lid", "pos", 1, 0.9, steps=8).wave("core", "pos", 1, 0.7, phase=1.0, steps=8)
    idle.wave("lid", "rot", 1, 0, steps=2)
    spin(idle, "ring0", 2)
    spin(idle, "ring1", -1)
    spin(idle, "ring2", 3)
    for k in range(4):
        spin(idle, f"chip{k}", 1 if k % 2 else -1)
    shoot = b.anim("shoot", 0.8, "once")                 # a tampa sobe, o núcleo avança e os anéis disparam
    shoot.key("lid", "pos", 0, (0, 0, 0)).key("lid", "pos", 0.2, (0, 4.5, 0)).key("lid", "pos", 0.8, (0, 0, 0))
    shoot.key("core", "pos", 0, (0, 0, 0)).key("core", "pos", 0.2, (0, 0, -3)).key("core", "pos", 0.8, (0, 0, 0))
    for k, t in enumerate((1, -2, 2)):
        shoot.key(f"ring{k}", "rot", 0, (0, 0, 0)).key(f"ring{k}", "rot", 0.8, (0, 360 * t, 0))
        shoot.key(f"ring{k}", "scl", 0, (1, 1, 1)).key(f"ring{k}", "scl", 0.25, (1.35, 1, 1.35)).key(f"ring{k}", "scl", 0.8, (1, 1, 1))
    return b


if __name__ == "__main__":
    make("shulker_sentinel", build, P, {}, anim="idle")
