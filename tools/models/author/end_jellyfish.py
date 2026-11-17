"""Água-viva do Fim (End Jellyfish): sino de obsidiana salpicada de magenta sob uma redoma translúcida, três degraus de
obsidiana chorosa no topo, tentáculos grossos e segmentados (do roxo ao rosa) e correntes finas nos cantos.
Referência: prancha "End Jellyfish" (vistas de cima, de baixo e em perspectiva). Passiva, plana nas copas do Coro."""
from kit import make
from style import R, Designer, mix, rgb

OBS = R("#07050d", "#0f0a1a", "#1a1230", "#2a1b4c", "#3c2670")
CRY = R("#160a2c", "#271247", "#42207a", "#6a30b4", "#9a4ee0")
MAG = rgb("#c63cf0")
GLASS = [(70, 60, 140, 150), (96, 84, 176, 120), (130, 116, 214, 96)]
CORE = R("#7a2a9a", "#b83ad8", "#e866ff", "#ffc4ff")
CHAIN = R("#4a2a9a", "#6a3fd0", "#8a5af0", "#b08aff")
TOP = rgb("#7a3fe0")
BOT = rgb("#ff4fb0")

P = dict(bell=12.0)


def speck(cv):
    cv.noise(OBS, 1.6 + (0.5 if cv.face == "up" else 0), 0.7)
    for _ in range(max(2, cv.w * cv.h // 14)):
        cv.set(int(cv.rng.integers(0, cv.w)), int(cv.rng.integers(0, cv.h)), MAG if cv.rng.random() < 0.5 else CRY[4])
    cv.darken_edges(-0.15)


def step(cv):
    cv.noise(CRY, 2.0 + (1.0 if cv.face == "up" else 0), 0.65)
    cv.speckle(CRY[4], 0.12)
    if cv.face == "up" and cv.w >= 6:
        cx, cy = cv.w // 2, cv.h // 2
        for (i, j) in ((-1, 0), (0, -1), (1, 0), (0, 1)):
            cv.set(cx + i, cy + j, MAG)
        for i in range(2, cv.w - 2):
            cv.set(i, 1, CRY[4]) if i % 2 else None
    cv.darken_edges(-0.2)


def glass(cv):
    """Redoma: só a moldura e reflexos esparsos opacos (o resto vazado), então funciona com ou sem translucidez no cliente."""
    cv.clear()
    cv.border((150, 140, 235, 255))
    cv.speckle((110, 100, 200, 200), 0.10)
    if cv.face == "up":
        cv.rect(1, 1, cv.w - 2, 1, (176, 168, 245, 230))


def segment(k, n):
    c = mix(TOP, BOT, k / max(1, n - 1))
    ramp = R(*["#%02x%02x%02x" % tuple(max(0, min(255, int(v * f))) for v in c) for f in (0.55, 0.78, 1.0, 1.25)])

    def paint(cv):
        cv.noise(ramp, 2.0 + (0.6 if cv.face == "up" else -0.5 if cv.face == "down" else 0), 0.45)
        cv.darken_edges(-0.22)
    return paint


def build(p=P):
    b = Designer("end_jellyfish", res=(128, 96))
    w = p["bell"]
    y0 = 14.0
    b.bone("root", origin=(0, y0, 0))
    b.bone("body", "root", origin=(0, y0 + 4, 0))
    # sino de obsidiana, redoma translúcida e os três degraus do topo
    b.box("body", "bell", (w, 6.0, w), (0, y0, 0))
    b.skin("bell", speck)
    b.box("body", "step1", (w - 1.0, 3.0, w - 1.0), (0, y0 + 6.0, 0))
    b.skin("step1", step)
    b.box("body", "step2", (w - 4.0, 2.6, w - 4.0), (0, y0 + 9.0, 0))
    b.skin("step2", step)
    b.box("body", "step3", (w - 7.4, 2.2, w - 7.4), (0, y0 + 11.6, 0))
    b.skin("step3", step)
    b.box("body", "dome", (w + 1.6, 6.6, w + 1.6), (0, y0 - 0.3, 0), inflate=0.0)
    b.drop_face("dome", "down")
    b.skin("dome", glass)
    b.bone("core_glow", "body", origin=(0, y0 + 1.5, 0), glow=True)
    b.box("core_glow", "core", (4.2, 2.6, 4.2), (0, y0 - 1.2, 0))
    b.skin("core", lambda cv: cv.noise(CORE, 2.2, 0.5))
    # tentáculos grossos: duas colunas de 6 cubos, em duas juntas
    for col, (x, z) in enumerate(((-2.4, -1.2), (2.4, 1.2))):
        parent = "body"
        for seg in range(3):
            name = f"tent{col}_{seg}"
            top = y0 - 0.5 - seg * 5.2
            b.bone(name, parent, origin=(x, top, z))
            for k in range(2):
                n = f"{name}_c{k}"
                b.box(name, n, (3.0, 2.5, 3.0), (x, top - (k + 1) * 2.6, z))
                b.skin(n, segment(seg * 2 + k, 6))
            parent = name
    # correntes finas nos cantos, de elos alternados
    for ci, (x, z) in enumerate(((-5.0, -5.0), (5.0, -5.0), (-5.0, 5.0), (5.0, 5.0))):
        parent = "body"
        for seg in range(2):
            name = f"chain{ci}_{seg}"
            top = y0 - 0.5 - seg * 11.0
            b.bone(name, parent, origin=(x, top, z))
            for k in range(5):
                n = f"{name}_l{k}"
                yy = top - (k + 1) * 2.2
                size = (1.8, 2.0, 0.6) if k % 2 == 0 else (0.6, 2.0, 1.8)
                b.box(name, n, size, (x, yy, z))
                b.skin(n, lambda cv, c=ci: cv.noise(CHAIN, 1.2 + (c % 2) * 1.0, 0.5))
            parent = name

    def sway(anim, amp, speed_phase):
        for col in range(2):
            for seg in range(3):
                anim.orbit(f"tent{col}_{seg}", "rot", (amp * (1 + seg * 0.5), 0, amp * 0.8 * (1 + seg * 0.4)),
                           (col * 1.6 + seg * speed_phase, 0, col * 1.1 + seg * speed_phase + 1.2), steps=8)
        for ci in range(4):
            for seg in range(2):
                anim.orbit(f"chain{ci}_{seg}", "rot", (amp * 1.2, 0, amp * 1.1),
                           (ci * 0.9 + seg * speed_phase, 0, ci * 1.3 + seg * speed_phase + 0.9), steps=8)

    idle = b.anim("idle", 3.2, "loop")
    idle.wave("root", "pos", 1, 1.4, steps=8)
    idle.orbit("body", "scl", (0.03, -0.05, 0.03), (0, 0, 0), base=(1, 1, 1), steps=8)
    sway(idle, 5, 0.7)
    fly = b.anim("fly", 1.6, "loop")
    fly.wave("root", "pos", 1, 0.9, steps=8)
    fly.orbit("body", "scl", (0.06, -0.09, 0.06), (0, 0, 0), base=(1, 1, 1), steps=8)
    sway(fly, 9, 0.9)
    return b


if __name__ == "__main__":
    make("end_jellyfish", build, P, {}, anim="idle")
