"""The Monster of the Ocean Depths / Barnacle (Mob A, MINECON Earth 2017): lula gigante ancorada no fundo do
mar, com uma bocarra de três mandíbulas dentadas que cria um redemoinho e puxa jogadores e barcos para baixo.

Só há uma referência (``models/refs/barnacle/a.png``): o resto (costas, tentáculos, olhos dos dois lados) é
deduzido por simetria e pela anatomia da lula do jogo."""
import math

from kit import make
from style import R, Designer, rgb

SKIN = R("#0d171e", "#162630", "#1b3340", "#22414b", "#295063", "#2f6570", "#3d7f8a")
TEETH = R("#5d6640", "#7f895a", "#c4cf80", "#ddf09f")
TONGUE = R("#5a2232", "#83394e", "#b44e62", "#e2717d", "#f096a0")
EYE = rgb("#c8e4e0")

P = dict(jaw_len=20.0, head=10.0)


def skin(cv, base=3.2):
    cv.noise(SKIN, base + (0.8 if cv.face == "up" else -1.0 if cv.face == "down" else 0), 0.6)
    for _ in range(max(1, cv.w * cv.h // 40)):           # pintas claras como as da lula-brilhante
        cv.set(int(cv.rng.integers(0, cv.w)), int(cv.rng.integers(0, cv.h)), SKIN[6])


def jaw_paint(cv):
    skin(cv)
    if cv.face == "down":                                # face de dentro (voltada para o eixo da boca)
        cv.noise(SKIN, 1.0, 0.4)
        for j in range(cv.h):
            if j % 2 == 0:
                cv.set(0, j, TEETH[3]).set(cv.w - 1, j, TEETH[3]).set(1, j, TEETH[2]).set(cv.w - 2, j, TEETH[2])
            else:
                cv.set(0, j, TEETH[1]).set(cv.w - 1, j, TEETH[1])
    elif cv.face in ("east", "west"):                    # dentes aparecendo na borda de baixo das laterais
        for i in range(cv.w):
            if i % 2 == 0:
                cv.set(i, cv.h - 1, TEETH[3])
                if i % 4 == 0:
                    cv.set(i, cv.h - 2, TEETH[2])
    elif cv.face == "north":
        cv.hline(cv.h - 1, TEETH[2], 1, cv.w - 1)


def build(p=P):
    b = Designer("barnacle", res=(128, 128))
    L, H = p["jaw_len"], p["head"]
    b.bone("root")
    b.bone("body", "root", origin=(0, 8, 2))
    b.box("body", "head", (H, H, 10), (0, 8, 1), anchor="center")

    def head(cv):
        skin(cv)
        if cv.face == "north":                           # garganta escura no meio da boca
            cv.rect(2, 2, cv.w - 4, cv.h - 4, SKIN[0]).rect(3, 3, cv.w - 6, cv.h - 6, TONGUE[0])
        elif cv.face in ("east", "west"):                # olho perto da frente
            i = 1 if cv.face == "west" else cv.w - 4
            cv.rect(i, 3, 3, 2, EYE).rect(i + 1, 3, 1, 2, SKIN[0])
    b.skin("head", head)

    # três mandíbulas em volta do eixo da boca: em cima e embaixo dos dois lados (giradas 120°)
    for name, roll in (("jaw_top", 0), ("jaw_l", 120), ("jaw_r", -120)):
        b.bone(name, "body", origin=(0, 8, -4), rot=(0, 0, roll))
        b.box(name, name, (6, 5, L), (0, 9, -4 - L / 2))
        b.skin(name, jaw_paint)
        b.drop_face(name, "south")                       # encostada na cabeça: nunca aparece
    b.bone("tongue", "body", origin=(0, 8, -4))
    b.box("tongue", "tongue", (4, 3, L * 0.6), (0, 6.5, -4 - L * 0.3))
    b.drop_face("tongue", "south")
    b.skin("tongue", lambda cv: cv.noise(TONGUE, 2.6 if cv.face == "up" else 1.8, 0.45))

    for side, x in (("l", 3), ):
        b.bone(f"tentacle_{side}", "body", origin=(x, 11, 6))
        b.box(f"tentacle_{side}", f"tentacle_{side}", (3, 3, 12), (x, 9.5, 12))
        b.bone(f"tentacle2_{side}", f"tentacle_{side}", origin=(x, 11, 18))
        b.box(f"tentacle2_{side}", f"tentacle2_{side}", (3, 3, 12), (x, 9.5, 24))
        b.skin(f"tentacle_{side}", skin)
        b.skin(f"tentacle2_{side}", lambda cv: skin(cv, 3.8))
    b.mirror("tentacle_l", "tentacle_r")

    # ── animações ──
    def open_(a, t, deg):
        for j in ("jaw_top", "jaw_l", "jaw_r"):          # abre no próprio eixo; o giro de 120° é o repouso do osso
            a.key(j, "rot", t, (deg, 0, 0))
    idle = b.anim("idle", 4.0, "loop")
    for t, d in ((0, 6), (2, 14), (4, 6)):
        open_(idle, t, d)
    for s, ph in (("l", 0), ("r", 1.3)):
        idle.orbit(f"tentacle_{s}", "rot", (6, 12, 0), (ph + 0.4, ph, 0)).wave(f"tentacle2_{s}", "rot", 1, 18, phase=ph + 0.8, steps=8)
    idle.wave("body", "pos", 1, 0.6, steps=8)
    whirl = b.anim("whirl", 0.6, "loop")              # bocarra escancarada sugando a água
    for t, d in ((0, 50), (0.15, 58), (0.3, 50), (0.45, 58), (0.6, 50)):
        open_(whirl, t, d)
    whirl.wave("tongue", "rot", 1, 12, steps=6)
    whirl.wave("body", "rot", 2, 4, steps=6)
    for s, ph in (("l", 0), ("r", math.pi)):
        whirl.wave(f"tentacle_{s}", "rot", 1, 20, phase=ph, steps=6).wave(f"tentacle2_{s}", "rot", 1, 25, phase=ph + 1, steps=6)
    bite = b.anim("bite", 0.6, "once")
    for t, d in ((0, 55), (0.15, 0), (0.3, 10), (0.6, 6)):
        open_(bite, t, d)
    swim = b.anim("swim", 1.2, "loop")
    for t, d in ((0, 3), (1.2, 3)):
        open_(swim, t, d)
    for s, ph in (("l", 0), ("r", 0.6)):
        swim.wave(f"tentacle_{s}", "rot", 0, 18, phase=ph, steps=8).wave(f"tentacle2_{s}", "rot", 0, 28, phase=ph + 1.0, steps=8)
    swim.wave("body", "rot", 0, 4, steps=8)
    return b


SPEC = {"a.png": {"role": "shape", "yaw": (-180, 180), "pitch": (0, 60)}}

if __name__ == "__main__":
    make("barnacle", build, P, SPEC, anim="whirl", ref_pose=("whirl", 0.0))
