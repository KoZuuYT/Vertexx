"""Glare (Mob Vote 2021): moita de musgo flutuante das cavernas exuberantes que fica brava no escuro.

Referências: ``models/refs/glare`` (render 3D de frente, ícone em pixel art, render isométrico). Cabeça 12³ de
musgo com franja escorrendo por cima de uma faixa escura onde ficam os olhos, "saia" de musgo em duas camadas
afunilando para baixo e raízes penduradas. Os olhos ficam num osso de brilho (``brow_glow``), saltados meio pixel."""
from kit import make
from style import R, Designer, rgb

MOSS = R("#1f2712", "#2e3619", "#414e24", "#51672b", "#5f7a30", "#74933b", "#8fb046")
DARK = R("#070b07", "#0f160f", "#18221a")
ROOT = R("#2c2014", "#3e2e1c", "#594332", "#6e5640")
EYE_W, EYE_S, PUPIL, PUPIL2 = rgb("#c9d0c9"), rgb("#8f9890"), rgb("#8e3a12"), rgb("#c0581e")

P = dict(head=12.0, l1=10.0, l1h=5.0, l2=8.0, l2h=4.0)
STRANDS = [(-3, -2, 4), (2, -3, 3), (-1, 3, 5), (3, 2, 3), (-3, 2, 3), (0, -1, 5), (1, 1, 4)]


def moss(cv, base=3.6):
    cv.noise(MOSS, base + (0.6 if cv.face == "up" else -1.2 if cv.face == "down" else 0), 0.55)
    if cv.face not in ("up", "down"):            # fios verticais claros/escuros do musgo
        for i in range(cv.w):
            if cv.rng.random() < 0.6:
                c = MOSS[int(cv.rng.choice([1, 2, 5, 6]))]
                j0 = int(cv.rng.integers(0, max(1, cv.h - 2)))
                cv.vline(i, c, j0, min(cv.h, j0 + int(cv.rng.integers(3, 7))))


def eye_art(cv, i0, j0):
    cv.rect(i0, j0, 3, 2, EYE_W).hline(j0 + 1, EYE_S, i0, i0 + 3)
    cv.set(i0 + 1, j0, PUPIL2).set(i0 + 1, j0 + 1, PUPIL)


def build(p=P):
    b = Designer("glare", res=(128, 64))
    H = p["head"]
    y0 = 1 + p["l2h"] + p["l1h"]
    b.bone("root", origin=(0, y0, 0))
    b.bone("head", "root", origin=(0, y0 + H / 2, 0))
    b.box("head", "skull", (H, H, H), (0, y0, 0))

    def skull(cv):
        moss(cv)
        if cv.face == "north":
            cv.rect(1, 3, cv.w - 2, 6, DARK[1])           # faixa escura larga onde ficam os olhos
            cv.speckle(DARK[2], 0.2, lambda i, j: 3 <= j < 9 and 0 < i < cv.w - 1)
            cv.fringe(1, cv.w - 1, 3, lambda i: [1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0, 1][i % 12], lambda i, k: MOSS[5 - k], MOSS[1])
            eye_art(cv, 2, 5)
            eye_art(cv, cv.w - 5, 5)
            for i in range(1, cv.w - 1):                  # lábio de musgo subindo por baixo
                d = [1, 0, 1, 0, 0, 1][i % 6]
                for k in range(d):
                    cv.set(i, 8 - k, MOSS[4])
        elif cv.face in ("east", "west", "south"):
            cv.drips(MOSS[5], 0, 1, 3, 0.5, edge=MOSS[2])
    b.skin("skull", skull)
    b.bone("brow_glow", "head", origin=(0, y0 + H / 2, -H / 2))
    for name, x in (("eye_l", H / 2 - 3.5), ("eye_r", -(H / 2 - 3.5))):
        c = b.box("brow_glow", name, (3, 2, 0.5), (x, y0 + H - 7, -H / 2 - 0.25))
        b.skin(name, lambda cv: (eye_art(cv, 0, 0) if cv.face == "north" else cv.fill(EYE_S)))

    b.bone("skirt1", "root", origin=(0, y0, 0))
    b.box("skirt1", "layer1", (p["l1"], p["l1h"], p["l1"]), (0, y0 - p["l1h"], 0))
    b.skin("layer1", lambda cv: moss(cv, 2.6))
    y2 = y0 - p["l1h"]
    b.bone("skirt2", "skirt1", origin=(0, y2, 0))
    b.box("skirt2", "layer2", (p["l2"], p["l2h"], p["l2"]), (0, y2 - p["l2h"], 0))

    def layer2(cv):
        moss(cv, 1.8)
        if cv.face not in ("up", "down"):
            for i in range(1, cv.w, 3):
                cv.vline(i, ROOT[2], cv.h // 2)
    b.skin("layer2", layer2)
    y3 = y2 - p["l2h"]
    for i, (x, z, h) in enumerate(STRANDS):
        name = f"strand{i}"
        b.bone(name, "skirt2", origin=(x, y3, z))
        b.box(name, name, (1, h, 1), (x, y3 - h, z))
        b.skin(name, lambda cv: cv.vgrad(ROOT, 3, 0, 0.6).set(0, 0, MOSS[2]))

    # ── animações ──
    idle = b.anim("idle", 3.0, "loop")
    idle.wave("root", "pos", 1, 0.6, steps=6)
    idle.wave("skirt1", "rot", 2, 2.5, steps=6).wave("skirt2", "rot", 0, 3, phase=1.0, steps=6)
    for i in range(len(STRANDS)):
        idle.orbit(f"strand{i}", "rot", (6, 0, 5), (i * 0.9, 0, i * 1.4 + 0.5), steps=6)
    angry = b.anim("angry", 1.0, "loop")
    angry.wave("head", "rot", 2, 4, steps=8)
    angry.wave("head", "pos", 0, 0.35, steps=8)
    angry.wave("root", "pos", 1, 0.5, steps=4)
    for i in range(len(STRANDS)):
        angry.orbit(f"strand{i}", "rot", (12, 0, 10), (i * 0.7, 0, i * 1.1), steps=8)
    return b


SPEC = {"a.png": {"role": "shape", "yaw": (-30, 30), "pitch": (-25, 15)},
        "b.webp": {"role": "color", "note": "ícone em pixel art com moldura"},
        "c.png": {"role": "shape", "yaw": (-40, 40), "pitch": (0, 40)}}

if __name__ == "__main__":
    make("glare", build, P, SPEC, anim="idle", fit=["head", "l1", "l1h", "l2", "l2h"],
         bounds={"head": (10, 15), "l1": (8, 13), "l1h": (3, 8), "l2": (6, 11), "l2h": (2, 7)})
