"""Moobloom (Mob Vote 2020): vaca amarela com manchas creme e botões-de-ouro nas costas, no corpo da vaca vanilla
(corpo 12x10x18, cabeça 8x8x6 com chifres cinza e focinho, pernas 4x12x4 com cascos escuros).

As flores são dois planos cruzados por osso (``flower0..3``); o plugin as esconde quando a Moobloom é tosquiada
(esconder um osso esconde os filhos)."""
import math

from kit import make
from style import R, Designer, rgb

YEL = R("#8a6400", "#b08600", "#d0a400", "#e8be10", "#f8d820", "#fde860")
CREAM = R("#e8dca8", "#fff0b8", "#fff8d8")
HORN = R("#5a5a5a", "#7a7a7a", "#9a9a9a")
MUZZLE = R("#5a5a5a", "#8a8a8a", "#b4b4b4", "#cfcfcf")
EYE, EAR, HOOF = rgb("#121212"), rgb("#9a6a6a"), rgb("#2a2a2a")
FLOWER = ([".yy.", "yYOy", "yOYy", "yYOy", ".yy.", ".gG.", "gGgg"],
          {"y": rgb("#d9a514"), "Y": rgb("#f7d84a"), "O": rgb("#e08a10"), "g": rgb("#2f6a1e"), "G": rgb("#4f9a2a")})
FLOWERS = [(-3.0, -5.0), (3.5, 0.0), (-2.5, 4.0), (2.0, 7.5)]

P = dict()


def coat(cv, patches=3):
    cv.noise(YEL, 3.4 + (0.6 if cv.face == "up" else -1.0 if cv.face == "down" else 0), 0.45)
    for _ in range(patches):                       # manchas creme em blocos irregulares
        w, h = int(cv.rng.integers(2, 5)), int(cv.rng.integers(2, 4))
        i0, j0 = int(cv.rng.integers(0, max(1, cv.w - w))), int(cv.rng.integers(0, max(1, cv.h - h)))
        for j in range(h):
            for i in range(w):
                if (i, j) not in ((0, 0), (w - 1, h - 1)) or cv.rng.random() < 0.4:
                    cv.set(i0 + i, j0 + j, CREAM[1] if cv.rng.random() > 0.25 else CREAM[2])


def build(p=P):
    b = Designer("moobloom", res=(128, 128))
    b.bone("root")
    for name, z in (("legf_l", -6.0), ("legb_l", 6.0)):
        b.bone(name, "root", origin=(4, 12, z))
        b.box(name, name, (4, 12, 4), (4, 0, z))
        b.skin(name, lambda cv: (cv.vgrad(YEL, 4, 2.5, 0.6), cv.hline(cv.h - 1, HOOF)) if cv.face not in ("up", "down") else cv.fill(HOOF if cv.face == "down" else YEL[3]))
        b.mirror(name, name[:-2] + "_r")
    b.bone("body", "root", origin=(0, 12, 0))
    b.box("body", "torso", (12, 10, 18), (0, 12, 0))
    b.skin("torso", lambda cv: coat(cv, 2 + cv.w * cv.h // 40))

    b.bone("head", "body", origin=(0, 20, -9))
    b.box("head", "skull", (8, 8, 6), (0, 16, -12))

    def skull(cv):
        coat(cv, 1)
        if cv.face == "north":
            cv.rect(0, 3, 1, 2, EYE).rect(cv.w - 1, 3, 1, 2, EYE)
            cv.rect(1, 3, 1, 1, rgb("#2a2a2a")).rect(cv.w - 2, 3, 1, 1, rgb("#2a2a2a"))
        elif cv.face in ("east", "west"):
            i = cv.w - 2 if cv.face == "east" else 0
            cv.rect(i, 2, 2, 2, EAR)
    b.skin("skull", skull)
    b.box("head", "muzzle", (4, 3, 1), (0, 16, -15.5))

    def muzzle(cv):
        cv.noise(MUZZLE, 2.3, 0.3)
        if cv.face == "north":
            cv.set(0, 1, EYE).set(cv.w - 1, 1, EYE)
    b.skin("muzzle", muzzle)
    b.drop_face("muzzle", "south")
    b.bone("horn_l", "head", origin=(4.5, 24, -12))
    b.box("horn_l", "horn_l", (1, 3, 1), (4.5, 23, -12.5))
    b.skin("horn_l", lambda cv: cv.vgrad(HORN, 2, 0, 0.3))
    b.mirror("horn_l", "horn_r")

    rows, legend = FLOWER
    legend = {**legend, "O": rgb("#e08a10")}
    for i, (x, z) in enumerate(FLOWERS):
        name = f"flower{i}"
        b.bone(name, "body", origin=(x, 22, z), rot=(0, 45, 0))
        b.cube(name, f"{name}_a", (x - 2, 22, z), (x + 2, 29, z), faces=("north", "south"))
        b.bone(f"{name}x", name, origin=(x, 22, z), rot=(0, 90, 0))
        b.cube(f"{name}x", f"{name}_b", (x - 2, 22, z), (x + 2, 29, z), faces=("north", "south"))
        for cn in (f"{name}_a", f"{name}_b"):
            for f in ("north", "south"):
                cv = b.canvas(cn, f)
                cv.clear()
                cv.sprite(rows, legend, 0, 0, flip=f == "south")

    # ── animações ──
    walk = b.anim("walk", 1.0, "loop")
    for name, ph in (("legf_l", 0), ("legb_r", 0), ("legf_r", math.pi), ("legb_l", math.pi)):
        walk.wave(name, "rot", 0, 28, phase=ph)
    walk.wave("head", "rot", 0, 4, phase=0.6).wave("body", "pos", 1, 0.3, steps=4)
    for i in range(4):
        walk.wave(f"flower{i}", "rot", 2, 6, phase=i * 0.9)
    idle = b.anim("idle", 4.0, "loop")
    idle.orbit("head", "rot", (3, 5, 0), (0, 1.2, 0))
    for i in range(4):
        idle.wave(f"flower{i}", "rot", 2, 3.5, phase=i * 1.3)
    idle.wave("body", "scl", 1, 0.008, base=(1, 1, 1), steps=8)
    return b


SPEC = {"a.png": {"role": "shape", "yaw": (20, 70), "pitch": (20, 40)}}

if __name__ == "__main__":
    make("moobloom", build, P, SPEC, anim="walk")
