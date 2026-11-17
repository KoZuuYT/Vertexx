"""Parched Titan: colosso de arenito rachado do Deserto, com fendas incandescentes no peito, cactos e arbustos secos
incrustados, braço esquerdo blindado por uma laje e a Grande Cimitarra de Arenito na mão direita.
Textura 2x (32 texels por bloco). Orientação: frente = -Z. Escala de jogo em tools/models/convert.py (SCALES).
Animações: idle, dormant, walk, guard, parry, cleave, quicksand, leap, rush, spin, roar, stagger."""
import math
import random

from kit import make
from style import R, Designer

SAND = R("#6e4f30", "#966f42", "#bf9a5e", "#dcbb80", "#efd9a4")
RED = R("#5a2e1c", "#7e4228", "#a2603a", "#c4825a", "#dba67c")
CRACK = (44, 26, 14, 255)
EMBER = R("#7a1a00", "#d4480a", "#ff8a1a", "#ffc850", "#fff0b0")
CACT = R("#1c3e14", "#2c6420", "#3f8a2c", "#5cb040", "#8ad060")
STICK = R("#2c1c0e", "#4a321a", "#6e4c28", "#94703c")
BONE = R("#6e6450", "#968c70", "#c2b894", "#e2d8b4", "#f6efd2")
EYE = R("#7a1a00", "#e04a08", "#ff9020", "#ffe070")
P = dict(w=1.0)


def _cracks(cv, rng, count, ember):
    """Fissuras irregulares: caminham em zigue-zague de cima para baixo; as quentes brilham no centro."""
    for _ in range(count):
        i = rng.randrange(1, max(2, cv.w - 1))
        j = rng.randrange(0, max(1, cv.h // 2))
        for _ in range(rng.randrange(5, 14)):
            if 0 <= i < cv.w and 0 <= j < cv.h:
                cv.set(i, j, EMBER[2] if ember else CRACK)
                if ember:
                    cv.set(i + 1, j, CRACK) if 0 <= i + 1 < cv.w else None
            j += 1
            i += rng.choice((-1, 0, 0, 1))


def sand(seed, base=2.4, cracks=2, ember=False, ramp=SAND):
    rng_seed = seed

    def paint(cv):
        rng = random.Random(rng_seed * 131 + hash(cv.face) % 97)
        cv.noise(ramp, base + (0.9 if cv.face == "up" else -0.8 if cv.face == "down" else 0), 0.55)
        if cv.face not in ("up", "down"):
            for j in range(3, cv.h, 7):          # estratos horizontais do arenito
                cv.hline(j, ramp[1], 0, cv.w)
        cv.speckle(ramp[4], 0.05)
        _cracks(cv, rng, cracks, False)
        if ember:
            _cracks(cv, rng, 1 + cracks // 2, True)
        cv.darken_edges(-0.2)
    return paint


def cactus(cv):
    cv.noise(CACT, 2.4, 0.5)
    for i in range(1, cv.w, 4):
        cv.vline(i, CACT[0], 0, cv.h)
    cv.speckle(CACT[4], 0.06)


def stick(cv):
    cv.noise(STICK, 1.8, 0.5)


def bone(cv):
    cv.noise(BONE, 2.2, 0.45)
    cv.darken_edges(-0.18)


def glow_paint(cv):
    cv.noise(EMBER, 2.6, 0.5)
    cv.speckle(EMBER[4], 0.1)


def eye_paint(cv):
    cv.noise(EYE, 2.8 if cv.face == "north" else 2.0, 0.3)


def build(p=P):
    b = Designer("parched_titan", res=(512, 512))
    b.bone("root")
    b.bone("body", "root", origin=(0, 92, 0))

    # pélvis e pernas
    b.box("body", "pelvis", (78, 30, 54), (0, 80, 0))
    b.skin("pelvis", sand(1, 2.2, 3))
    b.box("body", "belt", (84, 8, 60), (0, 100, 0))
    b.skin("belt", sand(2, 1.8, 1, ramp=RED))
    for side, sx in (("l", 1), ("r", -1)):
        n = f"leg_{side}"
        b.bone(n, "body", origin=(sx * 26, 94, 0))
        b.box(n, n + "_thigh", (36, 52, 38), (sx * 26, 44, 0))
        b.skin(n + "_thigh", sand(3 + sx, 2.4, 3))
        b.box(n, n + "_knee", (40, 12, 44), (sx * 26, 40, -4))
        b.skin(n + "_knee", sand(5, 1.9, 1, ramp=RED))
        k = f"shin_{side}"
        b.bone(k, n, origin=(sx * 26, 44, 2))
        b.box(k, k + "_a", (32, 38, 32), (sx * 26, 8, 0))
        b.skin(k + "_a", sand(7 + sx, 2.3, 3, True))
        b.box(k, k + "_foot", (40, 14, 56), (sx * 26, 0, -10))
        b.skin(k + "_foot", sand(9, 2.0, 2))
        b.box(k, k + "_toe", (36, 7, 12), (sx * 26, 1, -42))
        b.skin(k + "_toe", sand(10, 1.8, 1, ramp=RED))

    # tronco: peito largo e curvado para a frente
    b.bone("chest", "body", origin=(0, 106, 0))
    b.box("chest", "torso", (114, 76, 66), (0, 106, -4))
    b.skin("torso", sand(11, 2.4, 5, True))
    b.box("chest", "plate_front", (92, 40, 6), (0, 112, -39))
    b.skin("plate_front", sand(12, 2.0, 4, False, RED))
    b.box("chest", "back_hump", (80, 42, 40), (0, 150, 22))
    b.skin("back_hump", sand(13, 2.2, 3))
    b.bone("core_glow", "chest", origin=(0, 130, -40), glow=True)
    for name, size, at, rot in (("crack_a", (7, 44, 2), (0, 116, -42.2), (0, 0, 0)),
                                ("crack_b", (5, 26, 2), (-18, 124, -42.2), (0, 0, 24)),
                                ("crack_c", (5, 24, 2), (18, 122, -42.2), (0, 0, -22)),
                                ("crack_d", (30, 5, 2.4), (0, 146, -42.5), (0, 0, 0)),
                                ("crack_e", (5, 16, 2), (-30, 112, -42.2), (0, 0, -14)),
                                ("crack_f", (5, 16, 2), (30, 112, -42.2), (0, 0, 14))):
        b.box("core_glow", name, size, at, rot=rot, origin=(at[0], at[1] + size[1] / 2, at[2]))
        b.skin(name, glow_paint)

    # cactos e arbustos secos incrustados
    for k, (x, y, z, h, rz) in enumerate(((-34, 178, 18, 30, 10), (30, 176, 26, 42, -8), (4, 192, 36, 26, 4))):
        n = f"cactus{k}"
        b.box("chest", n, (12, h, 12), (x, y, z), rot=(0, 0, rz), origin=(x, y, z))
        b.skin(n, cactus)
        b.box("chest", n + "_arm", (8, 14, 8), (x + 9, y + h * 0.5, z), rot=(0, 0, rz - 40), origin=(x + 6, y + h * 0.5, z))
        b.skin(n + "_arm", cactus)
    rng = random.Random(7)
    for k in range(9):
        x = rng.uniform(-48, 48)
        y = rng.uniform(150, 184)
        z = rng.choice((-1, 1)) * rng.uniform(26, 34) + (0 if k % 3 else 14)
        n = f"brush{k}"
        b.box("chest", n, (3, rng.uniform(14, 26), 3), (x, y, z), rot=(rng.uniform(-40, 40), rng.uniform(0, 90), rng.uniform(-50, 50)),
              origin=(x, y, z))
        b.skin(n, stick)

    # cabeça: crânio de pedra com órbitas incandescentes e mandíbula
    b.bone("head", "chest", origin=(0, 178, -12))
    b.box("head", "skull", (54, 46, 54), (0, 186, -22))
    b.skin("skull", sand(14, 2.6, 4))
    b.box("head", "brow", (60, 10, 22), (0, 220, -42))
    b.drop_face("brow", "down")
    b.skin("brow", sand(15, 2.2, 2, False, RED))
    b.box("head", "nose", (10, 16, 8), (0, 196, -50))
    b.skin("nose", sand(16, 1.8, 1))
    b.bone("eyes_glow", "head", origin=(0, 204, -48), glow=True)
    for name, x in (("eye_l", 13), ("eye_r", -13)):
        b.box("eyes_glow", name, (13, 11, 3), (x, 200, -49))
        b.skin(name, eye_paint)
    b.bone("jaw", "head", origin=(0, 190, -30))
    b.box("jaw", "jaw_a", (44, 14, 34), (0, 176, -36))
    b.skin("jaw_a", sand(17, 2.0, 2))
    for i, x in enumerate((-17, -9, -1, 7, 15)):
        n = f"tooth{i}"
        b.box("jaw", n, (5, 8, 4), (x + 1, 190, -53))
        b.skin(n, bone)
    b.bone("mouth_glow", "jaw", origin=(0, 190, -36), glow=True)
    b.box("mouth_glow", "mouth", (34, 3, 22), (0, 190, -37))
    b.skin("mouth", glow_paint)
    for k, (x, h, rz) in enumerate(((-18, 30, 12), (18, 22, -14))):
        n = f"hcactus{k}"
        b.box("head", n, (10, h, 10), (x, 230, -10), rot=(0, 0, rz), origin=(x, 230, -10))
        b.skin(n, cactus)
    for k in range(4):
        n = f"hbrush{k}"
        b.box("head", n, (3, 20, 3), (-22 + k * 14, 228, -28 + (k % 2) * 16), rot=(0, 0, (k - 1.5) * 26), origin=(-22 + k * 14, 228, -28))
        b.skin(n, stick)

    # braços: o esquerdo com laje blindada, o direito empunha a cimitarra
    for side, sx in (("l", 1), ("r", -1)):
        a = f"arm_{side}"
        b.bone(a, "chest", origin=(sx * 70, 170, -4))
        b.box(a, a + "_pad", (46, 26, 50), (sx * 72, 160, -4))
        b.skin(a + "_pad", sand(20 + sx, 2.3, 3, False, RED))
        b.box(a, a + "_upper", (34, 62, 34), (sx * 72, 100, -4))
        b.skin(a + "_upper", sand(22 + sx, 2.3, 4, True))
        f = f"fore_{side}"
        b.bone(f, a, origin=(sx * 72, 104, -4))
        b.box(f, f + "_a", (40, 54, 40), (sx * 72, 52, -6))
        b.skin(f + "_a", sand(24 + sx, 2.3, 4, True))
        b.box(f, f + "_fist", (48, 34, 46), (sx * 72, 20, -8))
        b.skin(f + "_fist", sand(26 + sx, 2.1, 3))
    b.bone("guard", "fore_l", origin=(92, 34, -38))
    b.box("guard", "guard_plate", (70, 86, 14), (92, 34, -38))
    b.skin("guard_plate", sand(30, 2.2, 6, False, RED))
    b.box("guard", "guard_edge", (74, 8, 18), (92, 124, -38))
    b.skin("guard_edge", sand(31, 1.8, 2))
    b.box("guard", "guard_boss", (22, 22, 8), (92, 66, -48))
    b.skin("guard_boss", glow_paint)

    # Grande Cimitarra de Arenito (apontando para a frente, em -Z)
    b.bone("sword", "fore_r", origin=(-72, 36, -10))
    b.box("sword", "grip", (12, 12, 40), (-72, 30, 6))
    b.skin("grip", sand(40, 1.6, 0, False, STICK))
    b.box("sword", "guard", (14, 56, 12), (-72, 8, -16))
    b.skin("guard", sand(41, 2.0, 2, False, RED))
    for k, (w, th, z0, ln) in enumerate(((32, 9, -22, 60), (36, 9.4, -80, 60), (34, 9.8, -138, 50), (26, 9.4, -186, 30), (14, 8, -214, 20))):
        n = f"blade{k}"
        b.box("sword", n, (w, th, ln), (-72, 36 - th / 2, z0 - ln / 2 - 4))
        b.skin(n, sand(42 + k, 2.4, 3, k < 3, SAND))
    for k in range(5):
        n = f"serr{k}"
        b.box("sword", n, (8, 6, 12), (-72 + (19 if k % 2 else -19), 33, -50 - k * 32))
        b.skin(n, sand(52 + k, 2.0, 1))

    # ---- animações ----
    def legs(anim, swing, lift, steps=8):
        for side, sx in (("l", 1), ("r", -1)):
            ph = 0 if sx > 0 else math.pi
            anim.wave(f"leg_{side}", "rot", 0, swing, phase=ph, steps=steps)
            anim.wave(f"shin_{side}", "rot", 0, lift, phase=ph + 1.2, steps=steps, base=(lift, 0, 0))

    idle = b.anim("idle", 4.0, "loop")
    idle.wave("chest", "rot", 0, 1.6, steps=8)
    idle.wave("head", "rot", 0, 2.4, phase=0.6, steps=8)
    idle.wave("body", "pos", 1, 0.8, steps=8)
    idle.wave("arm_l", "rot", 0, 3, phase=0.4, steps=8, base=(8, 0, 4))
    idle.wave("arm_r", "rot", 0, 3, phase=1.0, steps=8, base=(38, 0, -4))
    idle.key("fore_r", "rot", 0, (24, 0, 0)).key("fore_r", "rot", 4.0, (24, 0, 0))
    idle.key("fore_l", "rot", 0, (16, 0, 0)).key("fore_l", "rot", 4.0, (16, 0, 0))
    idle.wave("core_glow", "scl", 1, 0.04, steps=8, base=(1, 1, 1))

    dormant = b.anim("dormant", 6.0, "loop")           # ajoelhado, imóvel como uma estátua, brasas quase apagadas
    dormant.key("body", "pos", 0, (0, -34, 6)).key("body", "pos", 6.0, (0, -34, 6))
    dormant.key("chest", "rot", 0, (26, 0, 0)).key("chest", "rot", 6.0, (26, 0, 0))
    dormant.key("head", "rot", 0, (34, 0, 0)).key("head", "rot", 6.0, (34, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        dormant.key(f"leg_{side}", "rot", 0, (-70, 0, 0)).key(f"leg_{side}", "rot", 6.0, (-70, 0, 0))
        dormant.key(f"shin_{side}", "rot", 0, (96, 0, 0)).key(f"shin_{side}", "rot", 6.0, (96, 0, 0))
        dormant.key(f"arm_{side}", "rot", 0, (12, 0, sx * 6)).key(f"arm_{side}", "rot", 6.0, (12, 0, sx * 6))
        dormant.key(f"fore_{side}", "rot", 0, (30, 0, 0)).key(f"fore_{side}", "rot", 6.0, (30, 0, 0))
    dormant.orbit("core_glow", "scl", (0.1, 0.1, 0.1), (0, 0, 0), base=(0.5, 0.5, 0.5), steps=8)

    walk = b.anim("walk", 1.3, "loop")
    legs(walk, 26, 18)
    walk.wave("body", "pos", 1, 2.4, steps=8)
    walk.wave("chest", "rot", 1, 3, steps=8)
    walk.wave("arm_l", "rot", 0, 14, phase=math.pi, steps=8, base=(8, 0, 4))
    walk.wave("arm_r", "rot", 0, 6, steps=8, base=(34, 0, -4))
    walk.wave("head", "rot", 1, 2, steps=8)
    walk.key("fore_r", "rot", 0, (24, 0, 0)).key("fore_r", "rot", 1.3, (24, 0, 0))
    walk.key("fore_l", "rot", 0, (16, 0, 0)).key("fore_l", "rot", 1.3, (16, 0, 0))

    guard = b.anim("guard", 2.5, "once")               # Guarda de Arenito: laje à frente, espada baixa
    guard.key("arm_l", "rot", 0, (8, 0, 4)).key("arm_l", "rot", 0.25, (-70, 0, -20)).key("arm_l", "rot", 2.2, (-70, 0, -20)).key("arm_l", "rot", 2.5, (8, 0, 4))
    guard.key("fore_l", "rot", 0, (16, 0, 0)).key("fore_l", "rot", 0.25, (-60, 0, 0)).key("fore_l", "rot", 2.2, (-60, 0, 0)).key("fore_l", "rot", 2.5, (16, 0, 0))
    guard.key("chest", "rot", 0, (0, 0, 0)).key("chest", "rot", 0.25, (-8, 0, 0)).key("chest", "rot", 2.2, (-8, 0, 0)).key("chest", "rot", 2.5, (0, 0, 0))
    guard.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.25, (0, -8, 0)).key("body", "pos", 2.2, (0, -8, 0)).key("body", "pos", 2.5, (0, 0, 0))
    for side in ("l", "r"):
        guard.key(f"leg_{side}", "rot", 0, (0, 0, 0)).key(f"leg_{side}", "rot", 0.25, (-18, 0, 0)).key(f"leg_{side}", "rot", 2.2, (-18, 0, 0)).key(f"leg_{side}", "rot", 2.5, (0, 0, 0))

    parry = b.anim("parry", 0.9, "once")               # punho esmaga o chão
    parry.key("arm_l", "rot", 0, (-60, 0, -10)).key("arm_l", "rot", 0.3, (-150, 0, -10)).key("arm_l", "rot", 0.5, (30, 0, 0)).key("arm_l", "rot", 0.9, (8, 0, 4))
    parry.key("fore_l", "rot", 0, (-50, 0, 0)).key("fore_l", "rot", 0.3, (-20, 0, 0)).key("fore_l", "rot", 0.5, (20, 0, 0)).key("fore_l", "rot", 0.9, (16, 0, 0))
    parry.key("chest", "rot", 0, (0, 0, 0)).key("chest", "rot", 0.3, (-14, 0, 0)).key("chest", "rot", 0.5, (24, 0, 0)).key("chest", "rot", 0.9, (0, 0, 0))
    parry.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.5, (0, -10, 0)).key("body", "pos", 0.9, (0, 0, 0))

    cleave = b.anim("cleave", 1.9, "once")             # espada ergue por 1,1 s e desce num corte vertical
    cleave.key("arm_r", "rot", 0, (38, 0, -4)).key("arm_r", "rot", 1.1, (162, 0, -8)).key("arm_r", "rot", 1.3, (38, 0, -4)).key("arm_r", "rot", 1.9, (38, 0, -4))
    cleave.key("fore_r", "rot", 0, (24, 0, 0)).key("fore_r", "rot", 1.1, (10, 0, 0)).key("fore_r", "rot", 1.3, (30, 0, 0)).key("fore_r", "rot", 1.9, (24, 0, 0))
    cleave.key("chest", "rot", 0, (0, 0, 0)).key("chest", "rot", 1.1, (-18, 0, 0)).key("chest", "rot", 1.3, (26, 0, 0)).key("chest", "rot", 1.9, (0, 0, 0))
    cleave.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 1.1, (0, 2, 0)).key("body", "pos", 1.3, (0, -10, 0)).key("body", "pos", 1.9, (0, 0, 0))
    cleave.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 1.1, (-14, 0, 0)).key("head", "rot", 1.3, (14, 0, 0)).key("head", "rot", 1.9, (0, 0, 0))

    quick = b.anim("quicksand", 1.6, "once")           # crava a ponta da espada no solo
    quick.key("arm_r", "rot", 0, (38, 0, -4)).key("arm_r", "rot", 0.4, (110, 0, -4)).key("arm_r", "rot", 0.7, (-12, 0, -4)).key("arm_r", "rot", 1.6, (-12, 0, -4))
    quick.key("fore_r", "rot", 0, (24, 0, 0)).key("fore_r", "rot", 0.7, (50, 0, 0)).key("fore_r", "rot", 1.6, (50, 0, 0))
    quick.key("chest", "rot", 0, (0, 0, 0)).key("chest", "rot", 0.7, (28, 0, 0)).key("chest", "rot", 1.6, (28, 0, 0))
    quick.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.7, (0, -14, 0)).key("body", "pos", 1.6, (0, -14, 0))
    quick.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.7, (-12, 0, 0)).key("head", "rot", 1.6, (-12, 0, 0))

    leap = b.anim("leap", 1.3, "once")                 # agacha, salta e esmaga
    leap.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.35, (0, -22, 0)).key("body", "pos", 0.55, (0, 6, 0)).key("body", "pos", 0.95, (0, 4, 0)).key("body", "pos", 1.1, (0, -16, 0)).key("body", "pos", 1.3, (0, 0, 0))
    for side in ("l", "r"):
        leap.key(f"leg_{side}", "rot", 0, (0, 0, 0)).key(f"leg_{side}", "rot", 0.35, (-48, 0, 0)).key(f"leg_{side}", "rot", 0.55, (14, 0, 0)).key(f"leg_{side}", "rot", 1.1, (-30, 0, 0)).key(f"leg_{side}", "rot", 1.3, (0, 0, 0))
        leap.key(f"shin_{side}", "rot", 0, (0, 0, 0)).key(f"shin_{side}", "rot", 0.35, (70, 0, 0)).key(f"shin_{side}", "rot", 0.55, (-10, 0, 0)).key(f"shin_{side}", "rot", 1.1, (50, 0, 0)).key(f"shin_{side}", "rot", 1.3, (0, 0, 0))
        leap.key(f"arm_{side}", "rot", 0, (20, 0, 0)).key(f"arm_{side}", "rot", 0.35, (-30, 0, 0)).key(f"arm_{side}", "rot", 0.55, (150, 0, 0)).key(f"arm_{side}", "rot", 1.05, (150, 0, 0)).key(f"arm_{side}", "rot", 1.15, (-10, 0, 0)).key(f"arm_{side}", "rot", 1.3, (20, 0, 0))
    leap.key("chest", "rot", 0, (0, 0, 0)).key("chest", "rot", 0.35, (22, 0, 0)).key("chest", "rot", 0.55, (-14, 0, 0)).key("chest", "rot", 1.1, (24, 0, 0)).key("chest", "rot", 1.3, (0, 0, 0))

    rush = b.anim("rush", 0.5, "loop")                 # cabeça baixa, espada arrastando
    legs(rush, 34, 24)
    rush.wave("body", "pos", 1, 3.2, steps=8, base=(0, -6, 0))
    rush.key("chest", "rot", 0, (34, 0, 0)).key("chest", "rot", 0.5, (34, 0, 0))
    rush.key("head", "rot", 0, (24, 0, 0)).key("head", "rot", 0.5, (24, 0, 0))
    rush.key("arm_r", "rot", 0, (-14, 0, -8)).key("arm_r", "rot", 0.5, (-14, 0, -8))
    rush.key("fore_r", "rot", 0, (38, 0, 0)).key("fore_r", "rot", 0.5, (38, 0, 0))
    rush.wave("arm_l", "rot", 0, 16, steps=8, base=(-20, 0, 4))
    rush.key("jaw", "rot", 0, (24, 0, 0)).key("jaw", "rot", 0.5, (24, 0, 0))

    spin = b.anim("spin", 1.6, "once")                 # giro de 360 graus com a espada aberta
    spin.key("root", "rot", 0, (0, 0, 0)).key("root", "rot", 0.3, (0, 0, 0)).key("root", "rot", 1.4, (0, 720, 0)).key("root", "rot", 1.6, (0, 720, 0))
    spin.key("arm_r", "rot", 0, (38, 0, -4)).key("arm_r", "rot", 0.3, (84, 0, -80)).key("arm_r", "rot", 1.4, (84, 0, -80)).key("arm_r", "rot", 1.6, (38, 0, -4))
    spin.key("arm_l", "rot", 0, (8, 0, 4)).key("arm_l", "rot", 0.3, (10, 0, 90)).key("arm_l", "rot", 1.4, (10, 0, 90)).key("arm_l", "rot", 1.6, (8, 0, 4))
    spin.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.3, (0, -8, 0)).key("body", "pos", 1.4, (0, -8, 0)).key("body", "pos", 1.6, (0, 0, 0))
    spin.key("chest", "rot", 0, (0, 0, 0)).key("chest", "rot", 0.3, (12, 0, 0)).key("chest", "rot", 1.4, (12, 0, 0)).key("chest", "rot", 1.6, (0, 0, 0))

    roar = b.anim("roar", 2.4, "once")                 # ruge e estilhaça a armadura de pedra
    roar.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.5, (-30, 0, 0)).key("head", "rot", 2.0, (-30, 0, 0)).key("head", "rot", 2.4, (0, 0, 0))
    roar.key("jaw", "rot", 0, (0, 0, 0)).key("jaw", "rot", 0.5, (42, 0, 0)).key("jaw", "rot", 2.0, (42, 0, 0)).key("jaw", "rot", 2.4, (0, 0, 0))
    roar.key("chest", "rot", 0, (0, 0, 0)).key("chest", "rot", 0.5, (-16, 0, 0)).key("chest", "rot", 2.0, (-16, 0, 0)).key("chest", "rot", 2.4, (0, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        roar.key(f"arm_{side}", "rot", 0, (20, 0, sx * 4)).key(f"arm_{side}", "rot", 0.5, (30, 0, sx * 60)).key(f"arm_{side}", "rot", 2.0, (30, 0, sx * 60)).key(f"arm_{side}", "rot", 2.4, (20, 0, sx * 4))
    roar.wave("core_glow", "scl", 1, 0.12, steps=12, base=(1.15, 1.15, 1.15))

    stagger = b.anim("stagger", 3.0, "loop")           # atordoado: de joelhos, cabeça baixa
    stagger.key("body", "pos", 0, (0, -26, 4)).key("body", "pos", 3.0, (0, -26, 4))
    stagger.key("chest", "rot", 0, (30, 0, 0)).key("chest", "rot", 3.0, (30, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        stagger.key(f"leg_{side}", "rot", 0, (-60, 0, 0)).key(f"leg_{side}", "rot", 3.0, (-60, 0, 0))
        stagger.key(f"shin_{side}", "rot", 0, (80, 0, 0)).key(f"shin_{side}", "rot", 3.0, (80, 0, 0))
        stagger.key(f"arm_{side}", "rot", 0, (10, 0, sx * 8)).key(f"arm_{side}", "rot", 3.0, (10, 0, sx * 8))
    stagger.wave("head", "rot", 2, 5, steps=8, base=(30, 0, 0))
    return b


if __name__ == "__main__":
    make("parched_titan", build, P, {}, anim="walk")
