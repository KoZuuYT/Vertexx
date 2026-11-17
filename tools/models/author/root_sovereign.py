"""Root Sovereign: guardião de troncos musgosos das Lush Caves, com ombros de folhagem cor-de-rosa (azaleia florida),
raízes expostas nas costas, braço esquerdo de casca grossa (a Casca Reforçada) e o Cajado de Esporos na mão direita.
Fase 2: apoia-se nos braços e corre de quatro (clipes q_*). Textura 2x (32 texels por bloco). Frente = -Z.
Escala de jogo em tools/models/convert.py (SCALES).
Animações: idle, dormant, walk, guard, spore, snare, vortex, roar, stagger, q_idle, q_walk, q_cast, q_dash, q_frenzy, q_thorns."""
import math
import random

from kit import make
from style import R, Designer

BARK = R("#24160c", "#3e2814", "#5c3c1e", "#7c5430", "#9c7242")
LOG = R("#3a2a1a", "#5a4024", "#7a5a34", "#9a7a4c", "#b8985c")
MOSS = R("#143010", "#25521c", "#3a7a2a", "#58a03c", "#86c85a")
ROOT = R("#3a1c10", "#5e3018", "#82482a", "#a8683c", "#c88c58")
LEAF = R("#1e4a18", "#2f6e26", "#4a9a36", "#6cc050", "#98e078")
PINK = R("#8a2c5c", "#c04884", "#e878ac", "#f8a8c8", "#ffd8e8")
GLOW = R("#1c5a0c", "#3ca020", "#7ee048", "#c4ff88", "#f2ffd0")
EYE = R("#1c5a0c", "#58c824", "#a8ff58", "#f2ffb0")
P = dict(w=1.0)


def bark(seed, base=2.4, moss=0.0, ramp=BARK):
    def paint(cv):
        rng = random.Random(seed * 131 + hash(cv.face) % 97)
        cv.noise(ramp, base + (0.8 if cv.face == "up" else -0.8 if cv.face == "down" else 0), 0.5)
        if cv.face not in ("up", "down"):
            for i in range(2, cv.w, 5):               # sulcos verticais da casca
                cv.vline(i, ramp[0], rng.randrange(0, max(1, cv.h // 3)), cv.h)
        cv.speckle(ramp[4], 0.04)
        if moss > 0:                                   # musgo cobrindo a parte de cima
            for i in range(cv.w):
                top = int(cv.h * moss * (0.4 + 0.6 * rng.random()))
                for j in range(top):
                    cv.set(i, j, MOSS[1 + (i + j) % 3])
        cv.darken_edges(-0.2)
    return paint


def moss(seed, base=2.4):
    def paint(cv):
        cv.noise(MOSS, base + (0.9 if cv.face == "up" else -0.6 if cv.face == "down" else 0), 0.6)
        cv.speckle(MOSS[4], 0.07)
        cv.darken_edges(-0.15)
    return paint


def leaf(seed, pink=0.0):
    def paint(cv):
        rng = random.Random(seed * 17 + hash(cv.face) % 53)
        cv.noise(LEAF, 2.4 + (0.6 if cv.face == "up" else 0), 0.6)
        cv.speckle(LEAF[0], 0.1)
        if pink > 0:                                   # flores de azaleia espalhadas
            for _ in range(int(cv.w * cv.h * pink / 6)):
                i, j = rng.randrange(cv.w), rng.randrange(cv.h)
                cv.set(i, j, PINK[3])
                if i + 1 < cv.w:
                    cv.set(i + 1, j, PINK[2])
                if j + 1 < cv.h:
                    cv.set(i, j + 1, PINK[1])
    return paint


def petals(seed):
    def paint(cv):
        cv.noise(PINK, 2.6, 0.45)
        cv.speckle(PINK[4], 0.08)
        cv.darken_edges(-0.15)
    return paint


def roots(seed, ramp=ROOT):
    def paint(cv):
        cv.noise(ramp, 2.2, 0.55)
        for j in range(2, cv.h, 4):
            cv.hline(j, ramp[0], 0, cv.w)
        cv.speckle(ramp[4], 0.05)
    return paint


def glow_paint(cv):
    cv.noise(GLOW, 2.6, 0.5)
    cv.speckle(GLOW[4], 0.1)


def eye_paint(cv):
    cv.noise(EYE, 2.8 if cv.face == "north" else 2.0, 0.3)


def key_poses(anim, name, kind, frames):
    """Atalho: ``frames`` = [(t, (x, y, z)), ...] para o osso ``name``."""
    for t, v in frames:
        anim.key(name, kind, t, v)


def build(p=P):
    b = Designer("root_sovereign", res=(512, 512))
    b.bone("root")
    b.bone("body", "root", origin=(0, 84, 0))

    # pélvis, cinto de musgo e pernas de tronco
    b.box("body", "pelvis", (58, 22, 40), (0, 74, 0))
    b.skin("pelvis", bark(1, 2.2))
    b.box("body", "belt", (64, 6, 46), (0, 93, 0))
    b.skin("belt", moss(2))
    for side, sx in (("l", 1), ("r", -1)):
        n = f"leg_{side}"
        b.bone(n, "body", origin=(sx * 20, 86, 0))
        b.box(n, n + "_thigh", (28, 44, 28), (sx * 20, 42, 0))
        b.skin(n + "_thigh", bark(3 + sx, 2.4, 0.18))
        b.box(n, n + "_knee", (32, 10, 32), (sx * 20, 38, -3))
        b.skin(n + "_knee", moss(5))
        k = f"shin_{side}"
        b.bone(k, n, origin=(sx * 20, 42, 1))
        b.box(k, k + "_a", (24, 34, 24), (sx * 20, 8, 0))
        b.skin(k + "_a", bark(7 + sx, 2.3))
        b.box(k, k + "_foot", (32, 8, 44), (sx * 20, 0, -8))
        b.skin(k + "_foot", roots(9))
        for i, dx in enumerate((-9, 0, 9)):                       # dedos de raiz
            n2 = f"{k}_toe{i}"
            b.box(k, n2, (7, 6, 16), (sx * 20 + dx, 1, -38))
            b.skin(n2, roots(10 + i, BARK))

    # tronco: peito largo de casca, costelas de madeira, musgo nas costas e raízes expostas
    b.bone("chest", "body", origin=(0, 96, 0))
    b.box("chest", "torso", (76, 62, 48), (0, 96, -2))
    b.skin("torso", bark(11, 2.4, 0.12))
    b.box("chest", "plate_front", (60, 36, 5), (0, 104, -30))
    b.skin("plate_front", bark(12, 2.1, 0, LOG))
    for k in range(3):
        n = f"rib{k}"
        b.box("chest", n, (64, 4, 5), (0, 108 + k * 12, -34))
        b.skin(n, bark(13 + k, 1.9, 0, BARK))
    b.box("chest", "back_moss", (62, 22, 34), (0, 130, 20))
    b.skin("back_moss", moss(14))
    rng = random.Random(7)
    for k in range(7):                                             # raízes expostas nas costas
        x = rng.uniform(-26, 26)
        y = rng.uniform(112, 150)
        n = f"backroot{k}"
        b.box("chest", n, (5, rng.uniform(22, 38), 5), (x, y, 28), rot=(rng.uniform(-50, -15), rng.uniform(0, 90), rng.uniform(-40, 40)),
              origin=(x, y, 28))
        b.skin(n, roots(20 + k))
    b.bone("rune_glow", "chest", origin=(0, 118, -33), glow=True)
    for name, size, at in (("rune_a", (6, 26, 2), (0, 112, -33.6)), ("rune_b", (20, 5, 3), (0, 126, -33.6)),
                           ("rune_c", (5, 8, 2), (-14, 113, -33.6)), ("rune_d", (5, 8, 2), (14, 113, -33.6))):
        b.box("rune_glow", name, size, at)
        b.skin(name, glow_paint)

    # ombros de folhagem e flores cor-de-rosa
    for side, sx in (("l", 1), ("r", -1)):
        n = f"shoulder_{side}"
        b.box("chest", n, (34, 16, 34), (sx * 48, 152, 0))
        b.skin(n, leaf(30 + sx, 0.5))
        b.box("chest", n + "_bloom", (22, 12, 22), (sx * 48, 166, 0))
        b.skin(n + "_bloom", petals(31 + sx))
        b.box("chest", n + "_tuft", (14, 14, 14), (sx * 54, 176, 4), rot=(0, 30, 12))
        b.skin(n + "_tuft", leaf(33 + sx, 0.9))

    # cabeça: máscara de madeira com coroa de galhos e flores
    b.bone("head", "chest", origin=(0, 158, -6))
    b.box("head", "skull", (40, 34, 38), (0, 164, -8))
    b.skin("skull", bark(40, 2.5, 0, LOG))
    b.box("head", "brow", (46, 7, 12), (0, 190, -28))
    b.drop_face("brow", "down")
    b.skin("brow", bark(41, 2.0))
    b.box("head", "snout", (18, 14, 12), (0, 166, -32))
    b.skin("snout", bark(42, 2.2, 0, LOG))
    b.bone("eyes_glow", "head", origin=(0, 180, -28), glow=True)
    for name, x in (("eye_l", 10), ("eye_r", -10)):
        b.box("eyes_glow", name, (9, 8, 2.4), (x, 176, -27.6))
        b.skin(name, eye_paint)
    b.bone("jaw", "head", origin=(0, 166, -14))
    b.box("jaw", "jaw_a", (30, 9, 24), (0, 158, -16))
    b.skin("jaw_a", bark(43, 2.0))
    for i, x in enumerate((-10, -3, 4, 11)):
        n = f"fang{i}"
        b.box("jaw", n, (4, 6, 3), (x, 162, -28))
        b.skin(n, bark(44 + i, 2.7, 0, LOG))
    for k, (x, h, rz, rx) in enumerate(((-16, 40, 24, -8), (-6, 50, 8, -4), (8, 46, -10, -6), (18, 38, -26, -8))):
        n = f"antler{k}"
        b.box("head", n, (6, h, 6), (x, 194, -4), rot=(rx, 0, rz), origin=(x, 194, -4))
        b.skin(n, bark(50 + k, 2.2))
        b.box("head", n + "_tip", (14, 10, 14), (x + rz * 0.9, 194 + h * 0.85, -4), rot=(0, k * 20, 0))
        b.skin(n + "_tip", leaf(54 + k, 0.8) if k % 2 else petals(54 + k))

    # braços: esquerdo com a casca grossa; direito empunha o cajado
    for side, sx in (("l", 1), ("r", -1)):
        a = f"arm_{side}"
        b.bone(a, "chest", origin=(sx * 50, 150, -2))
        b.box(a, a + "_upper", (26, 52, 26), (sx * 52, 100, -2))
        b.skin(a + "_upper", bark(60 + sx, 2.3, 0.14))
        f = f"fore_{side}"
        b.bone(f, a, origin=(sx * 52, 102, -2))
        b.box(f, f + "_a", (30, 44, 30), (sx * 52, 58, -5))
        b.skin(f + "_a", bark(62 + sx, 2.3, 0.1))
        b.box(f, f + "_fist", (34, 26, 32), (sx * 52, 34, -7))
        b.skin(f + "_fist", roots(64 + sx, BARK))
        for i in range(3):                                         # dedos de raiz
            n = f"{f}_finger{i}"
            b.box(f, n, (6, 12, 6), (sx * 52 + (i - 1) * 10, 24, -22), rot=(-20, 0, 0), origin=(sx * 52 + (i - 1) * 10, 30, -20))
            b.skin(n, roots(66 + i))
    b.bone("guard", "fore_l", origin=(70, 34, -26))                # Casca Reforçada: laje de madeira à frente do antebraço
    b.box("guard", "guard_plate", (56, 70, 12), (72, 36, -26))
    b.skin("guard_plate", bark(70, 2.3, 0.2, LOG))
    b.box("guard", "guard_edge", (60, 8, 16), (72, 100, -26))
    b.skin("guard_edge", moss(71))
    b.box("guard", "guard_boss", (18, 18, 6), (72, 56, -34))
    b.skin("guard_boss", glow_paint)

    # Cajado de Esporos: haste de raiz, coroa de quatro garras e o orbe de esporos
    b.bone("staff", "fore_r", origin=(-52, 34, -12))
    b.box("staff", "shaft", (8, 150, 8), (-52, 0, -12))
    b.skin("shaft", bark(80, 2.2, 0, LOG))
    for i, (dx, dz) in enumerate(((-9, 0), (9, 0), (0, -9), (0, 9))):
        n = f"claw{i}"
        b.box("staff", n, (5, 26, 5), (-52 + dx, 150, -12 + dz), rot=(dz * 2.2, 0, -dx * 2.2), origin=(-52 + dx, 150, -12 + dz))
        b.skin(n, roots(82 + i, BARK))
    b.box("staff", "wrap", (12, 6, 12), (-52, 120, -12))
    b.skin("wrap", moss(86))
    b.bone("orb_glow", "staff", origin=(-52, 166, -12), glow=True)
    b.box("orb_glow", "orb", (16, 16, 16), (-52, 158, -12))
    b.skin("orb", glow_paint)

    # ---- animações ----
    def legs(anim, swing, lift, steps=8):
        for side, sx in (("l", 1), ("r", -1)):
            ph = 0 if sx > 0 else math.pi
            anim.wave(f"leg_{side}", "rot", 0, swing, phase=ph, steps=steps)
            anim.wave(f"shin_{side}", "rot", 0, lift, phase=ph + 1.2, steps=steps, base=(lift, 0, 0))

    def hold(anim, bone, kind, dur, value):
        anim.key(bone, kind, 0, value).key(bone, kind, dur, value)

    idle = b.anim("idle", 4.0, "loop")
    idle.wave("chest", "rot", 0, 1.4, steps=8)
    idle.wave("head", "rot", 0, 2.0, phase=0.6, steps=8)
    idle.wave("body", "pos", 1, 0.8, steps=8)
    idle.wave("arm_l", "rot", 0, 3, phase=0.4, steps=8, base=(8, 0, 4))
    idle.wave("arm_r", "rot", 0, 2, phase=1.0, steps=8, base=(10, 0, -4))
    hold(idle, "fore_r", "rot", 4.0, (14, 0, 0))
    hold(idle, "fore_l", "rot", 4.0, (16, 0, 0))
    idle.wave("orb_glow", "scl", 1, 0.08, steps=8, base=(1, 1, 1))
    idle.wave("rune_glow", "scl", 1, 0.04, phase=1.0, steps=8, base=(1, 1, 1))

    dormant = b.anim("dormant", 6.0, "loop")            # sentado/agachado, coberto de musgo, cabeça baixa
    hold(dormant, "body", "pos", 6.0, (0, -34, 4))
    hold(dormant, "chest", "rot", 6.0, (24, 0, 0))
    hold(dormant, "head", "rot", 6.0, (32, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        hold(dormant, f"leg_{side}", "rot", 6.0, (-70, 0, 0))
        hold(dormant, f"shin_{side}", "rot", 6.0, (96, 0, 0))
        hold(dormant, f"arm_{side}", "rot", 6.0, (14, 0, sx * 6))
        hold(dormant, f"fore_{side}", "rot", 6.0, (28, 0, 0))
    dormant.orbit("orb_glow", "scl", (0.1, 0.1, 0.1), (0, 0, 0), base=(0.5, 0.5, 0.5), steps=8)
    dormant.orbit("rune_glow", "scl", (0.1, 0.1, 0.1), (0, 0, 0), base=(0.4, 0.4, 0.4), steps=8)

    walk = b.anim("walk", 1.4, "loop")
    legs(walk, 24, 16)
    walk.wave("body", "pos", 1, 2.2, steps=8)
    walk.wave("chest", "rot", 1, 2.6, steps=8)
    walk.wave("arm_l", "rot", 0, 12, phase=math.pi, steps=8, base=(8, 0, 4))
    walk.wave("arm_r", "rot", 0, 5, steps=8, base=(10, 0, -4))
    walk.wave("head", "rot", 1, 1.8, steps=8)
    hold(walk, "fore_r", "rot", 1.4, (14, 0, 0))
    hold(walk, "fore_l", "rot", 1.4, (16, 0, 0))

    guard = b.anim("guard", 2.5, "once")                # Casca Reforçada: braço esquerdo erguido diante do corpo
    key_poses(guard, "arm_l", "rot", [(0, (8, 0, 4)), (0.25, (-70, 0, -20)), (2.2, (-70, 0, -20)), (2.5, (8, 0, 4))])
    key_poses(guard, "fore_l", "rot", [(0, (16, 0, 0)), (0.25, (-60, 0, 0)), (2.2, (-60, 0, 0)), (2.5, (16, 0, 0))])
    key_poses(guard, "chest", "rot", [(0, (0, 0, 0)), (0.25, (-6, 0, 0)), (2.2, (-6, 0, 0)), (2.5, (0, 0, 0))])
    key_poses(guard, "body", "pos", [(0, (0, 0, 0)), (0.25, (0, -6, 0)), (2.2, (0, -6, 0)), (2.5, (0, 0, 0))])

    spore = b.anim("spore", 1.5, "once")                # ergue o cajado, concentra o orbe e o balança para a frente
    key_poses(spore, "arm_r", "rot", [(0, (10, 0, -4)), (0.6, (130, 0, -10)), (0.9, (40, 0, -6)), (1.5, (10, 0, -4))])
    key_poses(spore, "fore_r", "rot", [(0, (14, 0, 0)), (0.6, (-10, 0, 0)), (0.9, (30, 0, 0)), (1.5, (14, 0, 0))])
    key_poses(spore, "chest", "rot", [(0, (0, 0, 0)), (0.6, (-14, 0, 0)), (0.9, (18, 0, 0)), (1.5, (0, 0, 0))])
    key_poses(spore, "head", "rot", [(0, (0, 0, 0)), (0.6, (-10, 0, 0)), (0.9, (10, 0, 0)), (1.5, (0, 0, 0))])
    spore.wave("orb_glow", "scl", 1, 0.5, steps=8, base=(1.4, 1.4, 1.4))

    snare = b.anim("snare", 1.7, "once")                # bate o cajado no chão
    key_poses(snare, "arm_r", "rot", [(0, (10, 0, -4)), (0.45, (120, 0, -6)), (0.8, (0, 0, -4)), (1.7, (0, 0, -4))])
    key_poses(snare, "fore_r", "rot", [(0, (14, 0, 0)), (0.45, (0, 0, 0)), (0.8, (30, 0, 0)), (1.7, (30, 0, 0))])
    key_poses(snare, "chest", "rot", [(0, (0, 0, 0)), (0.45, (-12, 0, 0)), (0.8, (22, 0, 0)), (1.7, (22, 0, 0))])
    key_poses(snare, "body", "pos", [(0, (0, 0, 0)), (0.8, (0, -10, 0)), (1.7, (0, -10, 0))])

    vortex = b.anim("vortex", 2.5, "once")              # cajado em círculos e um golpe seco no fim
    key_poses(vortex, "arm_r", "rot", [(0, (10, 0, -4)), (0.4, (120, 0, -40)), (1.8, (120, 0, 40)), (1.95, (10, 0, -4)), (2.5, (10, 0, -4))])
    key_poses(vortex, "fore_r", "rot", [(0, (14, 0, 0)), (0.4, (0, 0, 0)), (1.8, (0, 0, 0)), (1.95, (34, 0, 0)), (2.5, (14, 0, 0))])
    key_poses(vortex, "chest", "rot", [(0, (0, 0, 0)), (0.4, (-10, -20, 0)), (1.8, (-10, 20, 0)), (1.95, (26, 0, 0)), (2.5, (0, 0, 0))])
    key_poses(vortex, "body", "pos", [(0, (0, 0, 0)), (1.8, (0, 2, 0)), (1.95, (0, -12, 0)), (2.5, (0, 0, 0))])

    roar = b.anim("roar", 2.4, "once")                  # ruge, finca o cajado e desce para as quatro patas
    key_poses(roar, "head", "rot", [(0, (0, 0, 0)), (0.5, (-30, 0, 0)), (2.0, (-30, 0, 0)), (2.4, (0, 0, 0))])
    key_poses(roar, "jaw", "rot", [(0, (0, 0, 0)), (0.5, (40, 0, 0)), (2.0, (40, 0, 0)), (2.4, (0, 0, 0))])
    key_poses(roar, "chest", "rot", [(0, (0, 0, 0)), (0.5, (-16, 0, 0)), (2.0, (-16, 0, 0)), (2.4, (0, 0, 0))])
    for side, sx in (("l", 1), ("r", -1)):
        key_poses(roar, f"arm_{side}", "rot", [(0, (14, 0, sx * 4)), (0.5, (30, 0, sx * 60)), (2.0, (30, 0, sx * 60)), (2.4, (14, 0, sx * 4))])
    roar.wave("orb_glow", "scl", 1, 0.3, steps=12, base=(1.5, 1.5, 1.5))

    stagger = b.anim("stagger", 3.0, "loop")            # atordoado: de joelhos, cabeça baixa
    hold(stagger, "body", "pos", 3.0, (0, -26, 4))
    hold(stagger, "chest", "rot", 3.0, (30, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        hold(stagger, f"leg_{side}", "rot", 3.0, (-60, 0, 0))
        hold(stagger, f"shin_{side}", "rot", 3.0, (80, 0, 0))
        hold(stagger, f"arm_{side}", "rot", 3.0, (10, 0, sx * 8))
    stagger.wave("head", "rot", 2, 5, steps=8, base=(30, 0, 0))

    # ---- fase 2: quadrúpede (corpo inclinado, braços de apoio, cajado cravado no chão) ----
    def quad_base(anim, dur, skip=()):
        poses = {"body": ("pos", (0, -30, -10)), "chest": ("rot", (62, 0, 0)), "head": ("rot", (-40, 0, 0))}
        for side, sx in (("l", 1), ("r", -1)):
            poses[f"arm_{side}"] = ("rot", (-8, 0, sx * 6))
            poses[f"fore_{side}"] = ("rot", (4, 0, 0))
            poses[f"leg_{side}"] = ("rot", (-34, 0, 0))
            poses[f"shin_{side}"] = ("rot", (46, 0, 0))
        for bone, (kind, value) in poses.items():
            if bone not in skip:
                hold(anim, bone, kind, dur, value)

    q_idle = b.anim("q_idle", 3.0, "loop")
    q_idle.wave("body", "pos", 1, 1.6, steps=8, base=(0, -30, -10))
    q_idle.wave("chest", "rot", 0, 2.4, steps=8, base=(62, 0, 0))
    q_idle.wave("head", "rot", 0, 3, phase=0.8, steps=8, base=(-40, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        hold(q_idle, f"arm_{side}", "rot", 3.0, (-8, 0, sx * 6))
        hold(q_idle, f"fore_{side}", "rot", 3.0, (4, 0, 0))
        hold(q_idle, f"leg_{side}", "rot", 3.0, (-34, 0, 0))
        hold(q_idle, f"shin_{side}", "rot", 3.0, (46, 0, 0))
    q_idle.wave("rune_glow", "scl", 1, 0.08, steps=8, base=(1.1, 1.1, 1.1))

    q_walk = b.anim("q_walk", 0.8, "loop")
    for side, sx in (("l", 1), ("r", -1)):
        ph = 0 if sx > 0 else math.pi
        q_walk.wave(f"arm_{side}", "rot", 0, 30, phase=ph, steps=8, base=(-8, 0, sx * 6))
        q_walk.wave(f"leg_{side}", "rot", 0, 26, phase=ph + math.pi, steps=8, base=(-34, 0, 0))
        hold(q_walk, f"fore_{side}", "rot", 0.8, (4, 0, 0))
        q_walk.wave(f"shin_{side}", "rot", 0, 16, phase=ph + 1.4, steps=8, base=(46, 0, 0))
    q_walk.wave("body", "pos", 1, 3, steps=8, base=(0, -30, -10))
    hold(q_walk, "chest", "rot", 0.8, (62, 0, 0))
    hold(q_walk, "head", "rot", 0.8, (-40, 0, 0))

    q_cast = b.anim("q_cast", 1.5, "once")              # ergue o tronco e conjura com um braço
    quad_base(q_cast, 1.5, ('chest', 'arm_r', 'head'))
    key_poses(q_cast, "chest", "rot", [(0, (62, 0, 0)), (0.5, (30, 0, 0)), (1.0, (30, 0, 0)), (1.5, (62, 0, 0))])
    key_poses(q_cast, "arm_r", "rot", [(0, (-8, 0, -6)), (0.5, (120, 0, -20)), (1.0, (40, 0, -6)), (1.5, (-8, 0, -6))])
    key_poses(q_cast, "head", "rot", [(0, (-40, 0, 0)), (0.5, (-10, 0, 0)), (1.0, (-10, 0, 0)), (1.5, (-40, 0, 0))])

    q_dash = b.anim("q_dash", 0.4, "loop")              # corrida em galope
    for side, sx in (("l", 1), ("r", -1)):
        ph = 0 if sx > 0 else math.pi
        q_dash.wave(f"arm_{side}", "rot", 0, 54, phase=ph, steps=8, base=(-10, 0, sx * 6))
        q_dash.wave(f"leg_{side}", "rot", 0, 40, phase=ph + math.pi, steps=8, base=(-30, 0, 0))
        hold(q_dash, f"fore_{side}", "rot", 0.4, (8, 0, 0))
        q_dash.wave(f"shin_{side}", "rot", 0, 24, phase=ph + 1.4, steps=8, base=(52, 0, 0))
    q_dash.wave("body", "pos", 1, 5, steps=8, base=(0, -36, -14))
    hold(q_dash, "chest", "rot", 0.4, (74, 0, 0))
    hold(q_dash, "head", "rot", 0.4, (-56, 0, 0))
    hold(q_dash, "jaw", "rot", 0.4, (28, 0, 0))

    q_frenzy = b.anim("q_frenzy", 1.9, "once")          # ergue o corpo e gira, golpeando em círculo com os braços
    quad_base(q_frenzy, 1.9, ('chest', 'arm_l', 'arm_r', 'head'))
    key_poses(q_frenzy, "root", "rot", [(0, (0, 0, 0)), (0.5, (0, 0, 0)), (1.7, (0, 720, 0)), (1.9, (0, 720, 0))])
    key_poses(q_frenzy, "chest", "rot", [(0, (62, 0, 0)), (0.5, (26, 0, 0)), (1.7, (26, 0, 0)), (1.9, (62, 0, 0))])
    for side, sx in (("l", 1), ("r", -1)):
        key_poses(q_frenzy, f"arm_{side}", "rot", [(0, (-8, 0, sx * 6)), (0.5, (80, 0, sx * 80)), (1.7, (80, 0, sx * 80)), (1.9, (-8, 0, sx * 6))])
    key_poses(q_frenzy, "head", "rot", [(0, (-40, 0, 0)), (0.5, (-14, 0, 0)), (1.7, (-14, 0, 0)), (1.9, (-40, 0, 0))])

    q_thorns = b.anim("q_thorns", 2.4, "once")          # de pé, braços ao alto, invocando as vinhas do teto
    quad_base(q_thorns, 2.4, ('chest', 'head', 'arm_l', 'arm_r', 'body'))
    key_poses(q_thorns, "chest", "rot", [(0, (62, 0, 0)), (0.6, (-18, 0, 0)), (2.0, (-18, 0, 0)), (2.4, (62, 0, 0))])
    key_poses(q_thorns, "head", "rot", [(0, (-40, 0, 0)), (0.6, (-30, 0, 0)), (2.0, (-30, 0, 0)), (2.4, (-40, 0, 0))])
    key_poses(q_thorns, "jaw", "rot", [(0, (0, 0, 0)), (0.6, (40, 0, 0)), (2.0, (40, 0, 0)), (2.4, (0, 0, 0))])
    for side, sx in (("l", 1), ("r", -1)):
        key_poses(q_thorns, f"arm_{side}", "rot", [(0, (-8, 0, sx * 6)), (0.6, (170, 0, sx * 14)), (2.0, (170, 0, sx * 14)), (2.4, (-8, 0, sx * 6))])
    key_poses(q_thorns, "body", "pos", [(0, (0, -30, -10)), (0.6, (0, -4, 0)), (2.0, (0, -4, 0)), (2.4, (0, -30, -10))])
    q_thorns.wave("rune_glow", "scl", 1, 0.2, steps=12, base=(1.3, 1.3, 1.3))
    return b


if __name__ == "__main__":
    make("root_sovereign", build, P, {}, anim="walk")
