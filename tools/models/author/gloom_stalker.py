"""Gloom Stalker: predador quadrúpede do Deep Dark, de carapaça de ardósia e sculk, com sensores dorsais, garras de deepslate,
cauda articulada e o núcleo Soul-Heart azul exposto entre as costelas. Textura 2x (32 texels por bloco). Frente = -Z.
Escala de jogo em tools/models/convert.py (SCALES).
Animações: idle, stalk, run, dormant, pounce, sonic, snare, leap, shred, overload, roar, stagger."""
import math
import random

from kit import make
from style import R, Designer

SLATE = R("#080c12", "#141c26", "#222e3c", "#34465a", "#4c627a")
SCULK = R("#04080c", "#0a1a24", "#103040", "#1a4a5e", "#2a7088")
CLAW = R("#0c1218", "#26323c", "#4a5a68", "#7c93a6", "#b4d0e0")
GLOW = R("#0a4a66", "#1c8cb0", "#46d0f0", "#98f0ff", "#e8ffff")
EYE = R("#0a4a66", "#2aa8d0", "#7ae8ff", "#f0ffff")
P = dict(w=1.0)


def slate(seed, base=2.2, veins=3, ramp=SLATE):
    def paint(cv):
        rng = random.Random(seed * 131 + hash(cv.face) % 97)
        cv.noise(ramp, base + (0.8 if cv.face == "up" else -0.8 if cv.face == "down" else 0), 0.5)
        for j in range(4, cv.h, 9):                    # estratos de deepslate
            cv.hline(j, ramp[0], 0, cv.w)
        cv.speckle(ramp[4], 0.04)
        for _ in range(veins):                          # veios de sculk ciano
            i, j = rng.randrange(cv.w), rng.randrange(max(1, cv.h))
            for _ in range(rng.randrange(3, 9)):
                if 0 <= i < cv.w and 0 <= j < cv.h:
                    cv.set(i, j, GLOW[1])
                j += 1
                i += rng.choice((-1, 0, 1))
        cv.darken_edges(-0.22)
    return paint


def sculk(seed):
    def paint(cv):
        cv.noise(SCULK, 2.2, 0.55)
        cv.speckle(GLOW[2], 0.025)
        cv.darken_edges(-0.2)
    return paint


def claw(seed):
    def paint(cv):
        cv.noise(CLAW, 2.4, 0.45)
        cv.darken_edges(-0.2)
    return paint


def glow_paint(cv):
    cv.noise(GLOW, 2.6, 0.5)
    cv.speckle(GLOW[4], 0.1)


def eye_paint(cv):
    cv.noise(EYE, 2.8 if cv.face == "north" else 2.0, 0.3)


def key_poses(anim, name, kind, frames):
    for t, v in frames:
        anim.key(name, kind, t, v)


def hold(anim, bone, kind, dur, value):
    anim.key(bone, kind, 0, value).key(bone, kind, dur, value)


def build(p=P):
    b = Designer("gloom_stalker", res=(512, 512))
    b.bone("root")
    b.bone("body", "root", origin=(0, 56, 0))

    # quadris e tronco
    b.box("body", "pelvis", (50, 34, 40), (0, 40, 45))
    b.skin("pelvis", slate(1))
    b.box("body", "torso", (54, 36, 64), (0, 48, 6))
    b.skin("torso", slate(2, 2.2, 4))
    b.box("body", "belly", (40, 6, 70), (0, 45, 8))
    b.drop_face("belly", "up")
    b.skin("belly", sculk(3))
    for side, sx in (("l", 1), ("r", -1)):                         # costelas e o Soul-Heart exposto entre elas
        for k, z in enumerate((-20, -10, 10, 20)):
            n = f"rib_{side}{k}"
            b.box("body", n, (4, 26, 5), (sx * 30, 50, z + 6), rot=(0, 0, sx * 6), origin=(sx * 30, 76, z + 6))
            b.skin(n, claw(10 + k))
    b.bone("core_glow", "body", origin=(0, 64, 6), glow=True)
    for side, sx in (("l", 1), ("r", -1)):
        b.box("core_glow", f"core_{side}", (3, 24, 14), (sx * 28.2, 50, 6))
        b.skin(f"core_{side}", glow_paint)
    for k, (z, x) in enumerate(((-26, 8), (-14, -8), (-2, 8), (10, -8), (22, 8), (34, -8))):   # sensores dorsais
        n = f"sensor{k}"
        b.box("body", n, (8, 26, 8), (x, 82, z + 6), rot=(-10, 0, x * 0.8), origin=(x, 82, z + 6))
        b.skin(n, slate(20 + k, 2.0, 1))
    b.bone("sensors_glow", "body", origin=(0, 108, 6), glow=True)
    for k, (z, x) in enumerate(((-26, 8), (-14, -8), (-2, 8), (10, -8), (22, 8), (34, -8))):
        n = f"tip{k}"
        b.box("sensors_glow", n, (10, 8, 10), (x + (0.8 * x) * 0.0, 106, z + 6 - 4))
        b.skin(n, glow_paint)

    # peito (conjunto dianteiro: cabeça e patas da frente)
    b.bone("chest", "body", origin=(0, 66, -30))
    b.box("chest", "chest_box", (68, 44, 44), (0, 44, -40))
    b.skin("chest_box", slate(30, 2.3, 4))
    b.box("chest", "chest_plate", (56, 12, 34), (0, 88, -40))
    b.skin("chest_plate", sculk(31))

    # cabeça: crânio alongado, focinho, mandíbula e presas
    b.bone("head", "chest", origin=(0, 84, -62))
    b.box("head", "skull", (36, 30, 40), (0, 66, -82))
    b.skin("skull", slate(40, 2.3, 3))
    b.box("head", "snout", (22, 16, 26), (0, 68, -112))
    b.skin("snout", slate(41, 2.1, 1))
    b.box("head", "crest", (10, 14, 30), (0, 96, -78))
    b.skin("crest", sculk(42))
    b.bone("eyes_glow", "head", origin=(0, 88, -102), glow=True)
    for name, x in (("eye_l", 11), ("eye_r", -11)):
        b.box("eyes_glow", name, (7, 5, 2.4), (x, 84, -102.4))
        b.skin(name, eye_paint)
    b.bone("jaw", "head", origin=(0, 72, -70))
    b.box("jaw", "jaw_a", (24, 8, 36), (0, 58, -92))
    b.skin("jaw_a", slate(43, 2.0, 1))
    for i, x in enumerate((-8, -4, 0, 4, 8)):
        n = f"fang{i}"
        b.box("jaw", n, (3.4, 7, 3), (x, 66.5, -108 + (i % 2) * 3))
        b.skin(n, claw(44 + i))
    b.bone("mouth_glow", "jaw", origin=(0, 68, -90), glow=True)
    b.box("mouth_glow", "mouth", (16, 2, 24), (0, 65, -92))
    b.skin("mouth", glow_paint)

    # patas dianteiras: braços de Void-Growth Armor com garras de deepslate
    for side, sx in (("l", 1), ("r", -1)):
        a = f"arm_{side}"
        b.bone(a, "chest", origin=(sx * 40, 84, -42))
        b.box(a, a + "_upper", (24, 48, 24), (sx * 40, 39, -42))
        b.skin(a + "_upper", slate(50 + sx, 2.2, 3))
        b.box(a, a + "_pad", (30, 10, 30), (sx * 40, 80, -43))
        b.skin(a + "_pad", sculk(52 + sx))
        f = f"fore_{side}"
        b.bone(f, a, origin=(sx * 40, 44, -42))
        b.box(f, f + "_a", (20, 40, 20), (sx * 40, 6, -46))
        b.skin(f + "_a", slate(54 + sx, 2.1, 2))
        b.box(f, f + "_paw", (28, 8, 34), (sx * 40, 0, -54))
        b.skin(f + "_paw", claw(56 + sx))
        for i, dx in enumerate((-8, 0, 8)):
            n = f"{f}_claw{i}"
            b.box(f, n, (5, 5, 20), (sx * 40 + dx, 1, -80))
            b.skin(n, claw(58 + i))

    # patas traseiras
    for side, sx in (("l", 1), ("r", -1)):
        n = f"leg_{side}"
        b.bone(n, "body", origin=(sx * 34, 70, 46))
        b.box(n, n + "_thigh", (26, 40, 30), (sx * 34, 33, 46))
        b.skin(n + "_thigh", slate(60 + sx, 2.2, 3))
        k = f"shin_{side}"
        b.bone(k, n, origin=(sx * 34, 36, 50))
        b.box(k, k + "_a", (18, 32, 18), (sx * 34, 4, 56))
        b.skin(k + "_a", slate(62 + sx, 2.1, 1))
        b.box(k, k + "_foot", (24, 6, 32), (sx * 34, 0, 46))
        b.skin(k + "_foot", claw(64 + sx))
        for i, dx in enumerate((-7, 0, 7)):
            c = f"{k}_claw{i}"
            b.box(k, c, (4, 4, 14), (sx * 34 + dx, 1, 26))
            b.skin(c, claw(66 + i))

    # cauda articulada com lâmina
    b.bone("tail1", "body", origin=(0, 60, 62))
    b.box("tail1", "tail1_a", (24, 22, 36), (0, 48, 82))
    b.skin("tail1_a", slate(70, 2.2, 2))
    b.bone("tail2", "tail1", origin=(0, 60, 98))
    b.box("tail2", "tail2_a", (18, 18, 34), (0, 51, 116))
    b.skin("tail2_a", slate(71, 2.1, 2))
    b.bone("tail3", "tail2", origin=(0, 60, 132))
    b.box("tail3", "tail3_a", (13, 13, 30), (0, 53, 148))
    b.skin("tail3_a", sculk(72))
    b.box("tail3", "blade", (5, 22, 28), (0, 52, 176))
    b.skin("blade", claw(73))

    # ---- animações ----
    def legs(anim, swing, lift, steps=8):
        for side, sx in (("l", 1), ("r", -1)):
            ph = 0 if sx > 0 else math.pi
            anim.wave(f"leg_{side}", "rot", 0, swing, phase=ph + math.pi, steps=steps)
            anim.wave(f"shin_{side}", "rot", 0, lift, phase=ph + 1.2, steps=steps)
            anim.wave(f"arm_{side}", "rot", 0, swing * 1.1, phase=ph, steps=steps)

    idle = b.anim("idle", 3.5, "loop")
    idle.wave("body", "pos", 1, 0.8, steps=8)
    idle.wave("head", "rot", 0, 2.4, phase=0.6, steps=8)
    idle.wave("tail1", "rot", 1, 5, steps=8)
    idle.wave("tail2", "rot", 1, 7, phase=0.8, steps=8)
    idle.wave("tail3", "rot", 1, 9, phase=1.6, steps=8)
    idle.wave("core_glow", "scl", 1, 0.06, steps=8, base=(1, 1, 1))
    idle.wave("sensors_glow", "scl", 1, 0.08, phase=1.2, steps=8, base=(1, 1, 1))

    stalk = b.anim("stalk", 1.8, "loop")                           # caminhada baixa e silenciosa de predador
    legs(stalk, 20, 14)
    stalk.wave("body", "pos", 1, 1.8, steps=8, base=(0, -6, 0))
    hold(stalk, "chest", "rot", 1.8, (10, 0, 0))
    stalk.wave("head", "rot", 0, 3, phase=0.5, steps=8, base=(8, 0, 0))
    stalk.wave("tail1", "rot", 1, 8, steps=8)
    stalk.wave("tail2", "rot", 1, 10, phase=0.8, steps=8)
    stalk.wave("tail3", "rot", 1, 12, phase=1.6, steps=8)

    run = b.anim("run", 0.55, "loop")                              # galope
    legs(run, 40, 24)
    run.wave("body", "pos", 1, 5, steps=8, base=(0, -4, 0))
    hold(run, "chest", "rot", 0.55, (6, 0, 0))
    run.wave("head", "rot", 0, 4, steps=8, base=(-4, 0, 0))
    run.wave("tail1", "rot", 1, 10, steps=8)
    run.wave("tail2", "rot", 1, 12, phase=0.8, steps=8)
    run.wave("tail3", "rot", 1, 14, phase=1.6, steps=8)
    hold(run, "jaw", "rot", 0.55, (22, 0, 0))

    dormant = b.anim("dormant", 6.0, "loop")                       # encolhido e quieto como uma pedra, sensores pulsando
    hold(dormant, "body", "pos", 6.0, (0, -26, 0))
    hold(dormant, "chest", "rot", 6.0, (14, 0, 0))
    hold(dormant, "head", "rot", 6.0, (30, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        hold(dormant, f"arm_{side}", "rot", 6.0, (-20, 0, sx * 10))
        hold(dormant, f"fore_{side}", "rot", 6.0, (30, 0, 0))
        hold(dormant, f"leg_{side}", "rot", 6.0, (-40, 0, 0))
        hold(dormant, f"shin_{side}", "rot", 6.0, (60, 0, 0))
    dormant.orbit("core_glow", "scl", (0.1, 0.1, 0.1), (0, 0, 0), base=(0.4, 0.4, 0.4), steps=8)
    dormant.orbit("sensors_glow", "scl", (0.1, 0.1, 0.1), (0, 0, 0), base=(0.5, 0.5, 0.5), steps=8)

    pounce = b.anim("pounce", 2.9, "once")                         # agacha, some e salta de garras abertas
    key_poses(pounce, "body", "pos", [(0, (0, 0, 0)), (0.5, (0, -22, 6)), (1.7, (0, -22, 6)), (1.9, (0, 6, -8)), (2.2, (0, 0, 0)), (2.9, (0, 0, 0))])
    key_poses(pounce, "chest", "rot", [(0, (0, 0, 0)), (0.5, (16, 0, 0)), (1.7, (16, 0, 0)), (1.9, (-18, 0, 0)), (2.9, (0, 0, 0))])
    for side in ("l", "r"):
        key_poses(pounce, f"arm_{side}", "rot", [(0, (0, 0, 0)), (0.5, (-30, 0, 0)), (1.7, (-30, 0, 0)), (1.9, (70, 0, 0)), (2.2, (40, 0, 0)), (2.9, (0, 0, 0))])
        key_poses(pounce, f"leg_{side}", "rot", [(0, (0, 0, 0)), (0.5, (-40, 0, 0)), (1.7, (-40, 0, 0)), (1.9, (30, 0, 0)), (2.9, (0, 0, 0))])
    key_poses(pounce, "tail1", "rot", [(0, (0, 0, 0)), (1.9, (-24, 0, 0)), (2.9, (0, 0, 0))])

    sonic = b.anim("sonic", 1.7, "once")                           # ergue o tronco e abre a mandíbula
    key_poses(sonic, "chest", "rot", [(0, (0, 0, 0)), (0.5, (-38, 0, 0)), (1.2, (-38, 0, 0)), (1.7, (0, 0, 0))])
    key_poses(sonic, "head", "rot", [(0, (0, 0, 0)), (0.5, (-16, 0, 0)), (1.2, (-16, 0, 0)), (1.7, (0, 0, 0))])
    key_poses(sonic, "jaw", "rot", [(0, (0, 0, 0)), (0.5, (44, 0, 0)), (1.3, (44, 0, 0)), (1.7, (0, 0, 0))])
    for side in ("l", "r"):
        key_poses(sonic, f"arm_{side}", "rot", [(0, (0, 0, 0)), (0.5, (-50, 0, 0)), (1.2, (-50, 0, 0)), (1.7, (0, 0, 0))])
    key_poses(sonic, "mouth_glow", "scl", [(0, (1, 1, 1)), (0.5, (1.5, 1.5, 1.5)), (1.2, (1.5, 1.5, 1.5)), (1.7, (1, 1, 1))])

    snare = b.anim("snare", 2.0, "once")                           # crava as garras dianteiras no chão
    key_poses(snare, "chest", "rot", [(0, (0, 0, 0)), (0.7, (24, 0, 0)), (1.6, (24, 0, 0)), (2.0, (0, 0, 0))])
    key_poses(snare, "body", "pos", [(0, (0, 0, 0)), (0.7, (0, -14, 0)), (1.6, (0, -14, 0)), (2.0, (0, 0, 0))])
    for side in ("l", "r"):
        key_poses(snare, f"arm_{side}", "rot", [(0, (0, 0, 0)), (0.4, (-60, 0, 0)), (0.7, (36, 0, 0)), (1.6, (36, 0, 0)), (2.0, (0, 0, 0))])
    snare.wave("sensors_glow", "scl", 1, 0.5, steps=12, base=(1.5, 1.5, 1.5))

    leap = b.anim("leap", 1.6, "loop")                             # salto em arco, patas estendidas
    hold(leap, "body", "pos", 1.6, (0, 6, 0))
    hold(leap, "chest", "rot", 1.6, (-10, 0, 0))
    for side in ("l", "r"):
        hold(leap, f"arm_{side}", "rot", 1.6, (70, 0, 0))
        hold(leap, f"leg_{side}", "rot", 1.6, (-50, 0, 0))
        hold(leap, f"shin_{side}", "rot", 1.6, (20, 0, 0))
    hold(leap, "tail1", "rot", 1.6, (-18, 0, 0))
    hold(leap, "jaw", "rot", 1.6, (30, 0, 0))

    shred = b.anim("shred", 1.5, "once")                           # três cortes alternados com as garras e a cauda
    key_poses(shred, "arm_l", "rot", [(0, (0, 0, 0)), (0.25, (-70, 0, 0)), (0.4, (50, 0, 0)), (0.9, (0, 0, 0)), (1.5, (0, 0, 0))])
    key_poses(shred, "arm_r", "rot", [(0, (0, 0, 0)), (0.5, (0, 0, 0)), (0.6, (-70, 0, 0)), (0.7, (50, 0, 0)), (1.1, (0, 0, 0)), (1.5, (0, 0, 0))])
    key_poses(shred, "tail1", "rot", [(0, (0, 0, 0)), (0.9, (0, 0, 0)), (1.0, (0, 50, 0)), (1.2, (0, -50, 0)), (1.5, (0, 0, 0))])
    key_poses(shred, "chest", "rot", [(0, (0, 0, 0)), (0.3, (8, -10, 0)), (0.65, (8, 10, 0)), (1.0, (8, -10, 0)), (1.5, (0, 0, 0))])
    key_poses(shred, "body", "pos", [(0, (0, 0, 0)), (0.3, (0, -8, 0)), (1.2, (0, -8, 0)), (1.5, (0, 0, 0))])

    overload = b.anim("overload", 2.3, "once")                     # concentra o núcleo e libera a detonação
    key_poses(overload, "body", "pos", [(0, (0, 0, 0)), (1.4, (0, -16, 0)), (1.5, (0, 6, 0)), (2.3, (0, 0, 0))])
    key_poses(overload, "chest", "rot", [(0, (0, 0, 0)), (1.4, (22, 0, 0)), (1.5, (-26, 0, 0)), (2.3, (0, 0, 0))])
    key_poses(overload, "head", "rot", [(0, (0, 0, 0)), (1.4, (24, 0, 0)), (1.5, (-30, 0, 0)), (2.3, (0, 0, 0))])
    key_poses(overload, "jaw", "rot", [(0, (0, 0, 0)), (1.5, (50, 0, 0)), (2.3, (0, 0, 0))])
    for side, sx in (("l", 1), ("r", -1)):
        key_poses(overload, f"arm_{side}", "rot", [(0, (0, 0, 0)), (1.4, (30, 0, sx * 20)), (1.5, (-40, 0, sx * 60)), (2.3, (0, 0, 0))])
    overload.wave("core_glow", "scl", 1, 0.7, steps=16, base=(1.8, 1.8, 1.8))
    overload.wave("sensors_glow", "scl", 1, 0.5, steps=16, base=(1.6, 1.6, 1.6))

    roar = b.anim("roar", 2.4, "once")                             # urro que marca a transição de fase
    key_poses(roar, "chest", "rot", [(0, (0, 0, 0)), (0.5, (-42, 0, 0)), (2.0, (-42, 0, 0)), (2.4, (0, 0, 0))])
    key_poses(roar, "head", "rot", [(0, (0, 0, 0)), (0.5, (-20, 0, 0)), (2.0, (-20, 0, 0)), (2.4, (0, 0, 0))])
    key_poses(roar, "jaw", "rot", [(0, (0, 0, 0)), (0.5, (46, 0, 0)), (2.0, (46, 0, 0)), (2.4, (0, 0, 0))])
    for side, sx in (("l", 1), ("r", -1)):
        key_poses(roar, f"arm_{side}", "rot", [(0, (0, 0, 0)), (0.5, (-40, 0, sx * 40)), (2.0, (-40, 0, sx * 40)), (2.4, (0, 0, 0))])
    roar.wave("core_glow", "scl", 1, 0.4, steps=12, base=(1.8, 1.8, 1.8))
    roar.wave("sensors_glow", "scl", 1, 0.4, steps=12, base=(1.8, 1.8, 1.8))

    stagger = b.anim("stagger", 3.0, "loop")
    hold(stagger, "body", "pos", 3.0, (0, -26, 0))
    hold(stagger, "chest", "rot", 3.0, (20, 0, 0))
    stagger.wave("head", "rot", 2, 5, steps=8, base=(30, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        hold(stagger, f"arm_{side}", "rot", 3.0, (-10, 0, sx * 12))
        hold(stagger, f"leg_{side}", "rot", 3.0, (-30, 0, 0))
    return b


if __name__ == "__main__":
    make("gloom_stalker", build, P, {}, anim="stalk")
