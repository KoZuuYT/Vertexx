"""Void-Scarab (Matriarca de Quitina): escaravelho colossal do Pináculo de Quitina. Carapaça de placas de quitina violeta
com nervuras claras, mandíbulas blindadas, asas translúcidas sob os élitros, ferrão abdominal brilhante e, sobre o tórax,
o Núcleo de Energia (cristal flutuante e ponto fraco). Quatro hitboxes no plugin; este modelo só desenha.
Orientação: frente = -Z. Escala de jogo em tools/models/convert.py (SCALES): 70 px de comprimento = 15 blocos.
Animações: idle, dormant, walk, charge, bite, volley, slam, stagger, roar."""
import math

from kit import make
from style import R, Designer, rgb

CHITIN = R("#0a0612", "#140c22", "#22163a", "#33235a", "#4b3480")
PLATE = R("#1c1230", "#2c1d4c", "#44307a", "#6046a0", "#8a6ad0")
PALE = R("#8a7fa8", "#a89cc4", "#c6bcdc", "#e2dcee")
GLOW = R("#6a1a9a", "#a02ee0", "#d060ff", "#f0a8ff", "#ffffff")
CRYS = R("#3a1070", "#6a2ac0", "#a060ff", "#d0a0ff", "#f4e0ff")
EYE = R("#4a0a6a", "#a020d0", "#e060ff", "#ffc0ff")
AMBER = (232, 168, 52, 255)
ACID = (150, 224, 64, 255)
VEIN = (210, 150, 60, 255)

P = dict(w=1.0)


def shell(base=2.6, ridges=True):
    def paint(cv):
        cv.noise(PLATE, base + (0.9 if cv.face == "up" else -0.9 if cv.face == "down" else 0), 0.55)
        cv.speckle(PLATE[4], 0.05)
        if ridges and cv.face in ("up", "north", "south", "east", "west"):
            step = 5 if cv.face == "up" else 4
            for i in range(2, cv.w, step):
                cv.vline(i, CHITIN[0], 0, cv.h)
            cv.hline(0, PALE[1] if cv.face != "up" else PLATE[4])
        cv.darken_edges(-0.22)
    return paint


def chitin(base=2.2):
    def paint(cv):
        cv.noise(CHITIN, base + (0.8 if cv.face == "up" else -0.7 if cv.face == "down" else 0), 0.6)
        cv.speckle(CHITIN[4], 0.07)
        cv.darken_edges(-0.2)
    return paint


def pale(base=1.8):
    def paint(cv):
        cv.noise(PALE, base, 0.5)
        cv.darken_edges(-0.2)
    return paint


def glow(base=2.6):
    def paint(cv):
        cv.noise(GLOW, base, 0.45)
        cv.darken_edges(-0.12)
    return paint


def crystal(cv):
    cv.noise(CRYS, 2.2 + (1.0 if cv.face in ("up", "north") else 0), 0.35)
    cv.border(CRYS[4])


def membrane(cv):
    """Asa translúcida: só veios âmbar/verde-ácido (o resto vazado), então lê bem com ou sem translucidez."""
    cv.clear()
    if cv.face not in ("up", "down"):
        cv.fill(VEIN)
        return
    cv.border(AMBER)
    for i in range(2, cv.w, 4):
        cv.vline(i, AMBER, 0, cv.h)
    for j in range(3, cv.h, 5):
        cv.hline(j, ACID, 0, cv.w)
    cv.speckle((255, 214, 120, 255), 0.05)


def build(p=P):
    b = Designer("void_scarab", res=(256, 160))
    b.bone("root")
    b.bone("body", "root", origin=(0, 17, 0))

    # tórax e abdômen
    b.box("body", "thorax", (20, 11, 22), (0, 12, -6))
    b.skin("thorax", chitin(2.4))
    b.box("body", "ridge", (5, 5, 32), (0, 23, 1))
    b.drop_face("ridge", "down")
    b.skin("ridge", shell(3.0, False))
    b.bone("abdomen", "body", origin=(0, 18, 5))
    b.box("abdomen", "abd", (18, 12, 24), (0, 10, 17))
    b.skin("abd", chitin(2.2))
    b.box("abdomen", "abd_top", (13, 3, 20), (0, 22, 17))
    b.drop_face("abd_top", "down")
    b.skin("abd_top", shell(2.8))
    for k, z in enumerate((9.5, 15.5, 21.5, 26.5)):
        n = f"ring{k}"
        b.box("abdomen", n, (20.6 - k * 0.8, 13.6 - k * 0.2, 1.8), (0, 9.2 + k * 0.2, z))
        b.skin(n, shell(2.2, False))
    for k, z in enumerate((9, 14, 19, 24)):
        n = f"spine{k}"
        b.box("abdomen", n, (1.8, 4.2 - k * 0.5, 1.8), (0, 25, z))
        b.skin(n, pale(2.2))
    b.bone("stinger_glow", "abdomen", origin=(0, 14, 29), glow=True)
    b.box("stinger_glow", "sting", (9, 8, 6), (0, 9, 32))
    b.skin("sting", glow(2.4))
    b.box("stinger_glow", "sting_tip", (4, 4, 6), (0, 10.5, 37.5))
    b.skin("sting_tip", glow(3.2))

    # élitros (abrem para as asas) e asas translúcidas por baixo
    b.bone("shell_l", "body", origin=(1.5, 24, -8))
    b.box("shell_l", "elytra", (10, 4.5, 38), (6.5, 23, 2))
    b.skin("elytra", shell(2.8))
    b.box("shell_l", "elytra_top", (6.5, 2, 30), (6.8, 27.5, 3))
    b.drop_face("elytra_top", "down")
    b.skin("elytra_top", shell(3.4))
    b.box("shell_l", "elytra_edge", (1.6, 5.6, 40), (11.4, 21.5, 2))
    b.skin("elytra_edge", pale(2.0))
    b.mirror("shell_l", "shell_r")
    for side, sx in (("l", 1), ("r", -1)):
        n = f"wing_{side}"
        b.bone(n, "body", origin=(sx * 6, 21.4, -4))
        x0 = 6 if sx > 0 else -6 - 26
        b.box(n, n + "_membrane", (26, 0.6, 24), (x0 + 13, 21.2, 8))
        b.skin(n + "_membrane", membrane)
        b.box(n, n + "_vein", (26, 0.9, 1.1), (x0 + 13, 21.8, -3.6))
        b.skin(n + "_vein", lambda cv: cv.fill(AMBER))

    # núcleo de energia: cristal flutuante sobre a crista dorsal
    b.box("body", "core_mount", (9, 2.4, 9), (0, 28, -6))
    b.skin("core_mount", shell(2.6, False))
    b.bone("core_glow", "body", origin=(0, 35, -6), glow=True)
    b.box("core_glow", "crystal", (5.4, 11, 5.4), (0, 31, -6))
    b.skin("crystal", crystal)
    b.box("core_glow", "crystal_b", (5.4, 9, 5.4), (0, 32, -6), rot=(0, 45, 0), origin=(0, 36, -6))
    b.skin("crystal_b", crystal)
    b.box("core_glow", "crystal_tip", (3, 4, 3), (0, 42, -6), rot=(0, 45, 0), origin=(0, 43, -6))
    b.skin("crystal_tip", crystal)
    b.box("core_glow", "crystal_foot", (3, 3, 3), (0, 28.2, -6), rot=(0, 45, 0), origin=(0, 29, -6))
    b.skin("crystal_foot", crystal)

    # cabeça, olhos, antenas e mandíbulas
    b.bone("head", "body", origin=(0, 18, -16))
    b.box("head", "skull", (14, 11, 10), (0, 13, -22))
    b.skin("skull", chitin(2.5))
    b.box("head", "brow", (15.4, 2.4, 6), (0, 24, -22))
    b.drop_face("brow", "down")
    b.skin("brow", shell(3.0, False))
    b.box("head", "clypeus", (8, 3, 2.4), (0, 14, -28.4))
    b.skin("clypeus", pale(1.6))
    b.bone("eyes_glow", "head", origin=(0, 19, -27.4), glow=True)
    for name, x in (("eye_l", 4.6), ("eye_r", -4.6)):
        b.box("eyes_glow", name, (3.6, 4.4, 1.2), (x, 17.6, -27.9))
        b.skin(name, lambda cv: cv.noise(EYE, 2.6 if cv.face == "north" else 1.6, 0.35))
    for side, sx in (("l", 1), ("r", -1)):
        a = f"antenna_{side}"
        b.bone(a, "head", origin=(sx * 3.4, 23.6, -21))
        b.box(a, a + "_a", (1.3, 8.5, 1.3), (sx * 3.8, 23.6, -23), rot=(-34, 0, sx * -12), origin=(sx * 3.4, 23.6, -21))
        b.skin(a + "_a", pale(1.4))
        b.box(a, a + "_b", (1.0, 5.5, 1.0), (sx * 6.3, 30.4, -28.6), rot=(-58, 0, sx * -18), origin=(sx * 3.4, 23.6, -21))
        b.skin(a + "_b", pale(1.0))
    for side, sx in (("l", 1), ("r", -1)):
        m = f"mandible_{side}"
        b.bone(m, "head", origin=(sx * 5, 16, -26))
        b.box(m, m + "_a", (4, 4, 8), (sx * 5.2, 14.2, -34), anchor="bottom")
        b.skin(m + "_a", shell(2.4, False))
        b.box(m, m + "_b", (3.4, 3.4, 7), (sx * 3.4, 14.6, -40.6), rot=(0, sx * 22, 0), origin=(sx * 5, 16, -34))
        b.skin(m + "_b", pale(1.8))
        b.box(m, m + "_c", (2.4, 2.4, 5), (sx * 1.2, 15, -45.6), rot=(0, sx * 38, 0), origin=(sx * 5, 16, -40))
        b.skin(m + "_c", pale(2.4))
        b.box(m, m + "_tooth", (1.4, 3, 1.4), (sx * 3.4, 17.6, -36.5))
        b.skin(m + "_tooth", pale(2.6))

    # seis pernas articuladas (fêmur para fora, tíbia para baixo, pé com garra)
    for k, z in enumerate((-12.0, -1.5, 9.5)):
        n = f"leg{k}_l"
        b.bone(n, "body", origin=(9.5, 17.5, z))
        b.box(n, n + "_coxa", (5, 5.8, 5), (11.5, 15.4, z))
        b.skin(n + "_coxa", shell(2.2, False))
        b.box(n, n + "_femur", (9, 3.2, 3.2), (16.5, 17.2, z))
        b.skin(n + "_femur", chitin(2.6))
        knee = f"leg{k}_knee_l"
        b.bone(knee, n, origin=(21, 18.8, z))
        b.box(knee, knee + "_tibia", (3.2, 18, 3.2), (23.3, 1.0, z), rot=(0, 0, 26), origin=(21.6, 18.8, z))
        b.skin(knee + "_tibia", chitin(2.8))
        b.box(knee, knee + "_spur", (1.4, 1.4, 4.4), (24.4, 11.5, z), rot=(0, 0, 26), origin=(21.6, 18.8, z))
        b.skin(knee + "_spur", pale(2.0))
        b.box(knee, knee + "_foot", (4.6, 1.6, 6.4), (30.4, 0, z - 0.6))
        b.skin(knee + "_foot", pale(1.6))
        b.mirror(n, f"leg{k}_r")

    def lift(anim, phase_of, amp=14, swing=10, steps=8):
        for k in range(3):
            for side, sx in (("l", 1), ("r", -1)):
                ph = phase_of(k, sx)
                anim.orbit(f"leg{k}_{side}", "rot", (0, sx * swing, sx * amp), (0, ph, ph + math.pi / 2), steps=steps)
                anim.wave(f"leg{k}_knee_{side}", "rot", 2, sx * amp * 0.9, phase=ph + math.pi / 2 + 0.4, steps=steps)

    tripod = lambda k, sx: (0 if (k % 2 == 0) == (sx > 0) else math.pi)

    idle = b.anim("idle", 3.6, "loop")
    idle.wave("body", "pos", 1, 0.5, steps=8)
    idle.wave("core_glow", "pos", 1, 1.2, phase=1.0, steps=8)
    idle.wave("core_glow", "rot", 1, 12, steps=8)
    idle.orbit("antenna_l", "rot", (6, 0, 5), (0, 0, 0), steps=8)
    idle.orbit("antenna_r", "rot", (6, 0, 5), (1.7, 0, 1.4), steps=8)
    idle.wave("mandible_l", "rot", 1, 4, steps=8)
    idle.wave("mandible_r", "rot", 1, -4, steps=8)
    idle.wave("abdomen", "rot", 0, 1.8, phase=0.6, steps=8)
    idle.wave("head", "rot", 1, 4, phase=0.8, steps=8)

    dormant = b.anim("dormant", 5.0, "loop")           # estase: encolhido, núcleo apagado e batendo devagar
    dormant.key("body", "pos", 0, (0, -3.4, 0))
    dormant.key("body", "pos", 5.0, (0, -3.4, 0))
    dormant.key("head", "rot", 0, (24, 0, 0)).key("head", "rot", 5.0, (24, 0, 0))
    for k in range(3):
        for side, sx in (("l", 1), ("r", -1)):
            dormant.key(f"leg{k}_{side}", "rot", 0, (0, 0, sx * 12)).key(f"leg{k}_{side}", "rot", 5.0, (0, 0, sx * 12))
            dormant.key(f"leg{k}_knee_{side}", "rot", 0, (0, 0, sx * 22)).key(f"leg{k}_knee_{side}", "rot", 5.0, (0, 0, sx * 22))
    dormant.wave("core_glow", "pos", 1, 0.8, steps=8, base=(0, -2, 0))
    dormant.orbit("core_glow", "scl", (0.06, 0.06, 0.06), (0, 0, 0), base=(0.8, 0.8, 0.8), steps=8)
    dormant.key("mandible_l", "rot", 0, (0, -14, 0)).key("mandible_l", "rot", 5.0, (0, -14, 0))
    dormant.key("mandible_r", "rot", 0, (0, 14, 0)).key("mandible_r", "rot", 5.0, (0, 14, 0))

    walk = b.anim("walk", 0.9, "loop")
    lift(walk, tripod, 16, 12)
    walk.wave("body", "pos", 1, 0.45, steps=8)
    walk.wave("body", "rot", 2, 1.6, steps=8)
    walk.wave("abdomen", "rot", 0, 2.5, phase=0.8, steps=8)
    walk.wave("core_glow", "pos", 1, 0.8, phase=1.0, steps=8)
    walk.orbit("antenna_l", "rot", (8, 0, 6), (0, 0, 0), steps=8)
    walk.orbit("antenna_r", "rot", (8, 0, 6), (1.7, 0, 1.4), steps=8)

    charge = b.anim("charge", 0.42, "loop")
    lift(charge, tripod, 24, 18)
    charge.wave("body", "pos", 1, 0.7, steps=8)
    charge.key("head", "rot", 0, (-10, 0, 0)).key("head", "rot", 0.42, (-10, 0, 0))
    charge.key("mandible_l", "rot", 0, (0, -26, 0)).key("mandible_l", "rot", 0.42, (0, -26, 0))
    charge.key("mandible_r", "rot", 0, (0, 26, 0)).key("mandible_r", "rot", 0.42, (0, 26, 0))
    charge.key("abdomen", "rot", 0, (8, 0, 0)).key("abdomen", "rot", 0.42, (8, 0, 0))

    bite = b.anim("bite", 1.2, "once")                 # duas mordidas (1,2 s de aviso) com a cabeça baixando
    for t, a in ((0, 0), (0.15, -34), (0.32, 6), (0.5, -34), (0.68, 6), (1.2, 0)):
        bite.key("mandible_l", "rot", t, (0, a, 0)).key("mandible_r", "rot", t, (0, -a, 0))
    bite.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.3, (-12, 0, 0)).key("head", "rot", 0.8, (-12, 0, 0)).key("head", "rot", 1.2, (0, 0, 0))
    bite.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.4, (0, -1.2, 0)).key("body", "pos", 1.2, (0, 0, 0))

    volley = b.anim("volley", 1.6, "once")             # élitros erguidos, asas abertas batendo
    for side, sx in (("l", 1), ("r", -1)):
        volley.key(f"shell_{side}", "rot", 0, (0, 0, 0)).key(f"shell_{side}", "rot", 0.3, (0, 0, sx * -52)).key(f"shell_{side}", "rot", 1.3, (0, 0, sx * -52)).key(f"shell_{side}", "rot", 1.6, (0, 0, 0))
        volley.key(f"wing_{side}", "scl", 0, (0.15, 1, 0.6)).key(f"wing_{side}", "scl", 0.3, (1, 1, 1)).key(f"wing_{side}", "scl", 1.3, (1, 1, 1)).key(f"wing_{side}", "scl", 1.6, (0.15, 1, 0.6))
        for i in range(1, 7):
            volley.key(f"wing_{side}", "rot", 0.3 + 0.17 * i, (0, 0, sx * (-14 if i % 2 else 8)))
        volley.key(f"wing_{side}", "rot", 0, (0, 0, 0)).key(f"wing_{side}", "rot", 1.6, (0, 0, 0))
    volley.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.3, (0, 1.5, 0)).key("body", "pos", 1.3, (0, 1.5, 0)).key("body", "pos", 1.6, (0, 0, 0))
    volley.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.3, (-14, 0, 0)).key("head", "rot", 1.3, (-14, 0, 0)).key("head", "rot", 1.6, (0, 0, 0))

    slam = b.anim("slam", 1.8, "once")                 # abdômen desce ao chão e carrega até a onda de choque (1,8 s)
    slam.key("abdomen", "rot", 0, (0, 0, 0)).key("abdomen", "rot", 0.5, (26, 0, 0)).key("abdomen", "rot", 1.8, (26, 0, 0))
    slam.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.5, (0, -1.5, 0)).key("body", "pos", 1.8, (0, -1.5, 0))
    slam.orbit("stinger_glow", "scl", (0.12, 0.12, 0.12), (0, 0, 0), base=(1.15, 1.15, 1.15), steps=8)
    slam.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.5, (-18, 0, 0)).key("head", "rot", 1.8, (-18, 0, 0))

    stagger = b.anim("stagger", 3.0, "loop")           # caído, asas abertas, núcleo em curto-circuito
    stagger.key("body", "pos", 0, (0, -4.6, 0)).key("body", "pos", 3.0, (0, -4.6, 0))
    stagger.key("head", "rot", 0, (26, 0, 0)).key("head", "rot", 3.0, (26, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        stagger.key(f"shell_{side}", "rot", 0, (0, 0, sx * -58)).key(f"shell_{side}", "rot", 3.0, (0, 0, sx * -58))
        stagger.key(f"wing_{side}", "scl", 0, (1, 1, 1)).key(f"wing_{side}", "scl", 3.0, (1, 1, 1))
        stagger.wave(f"wing_{side}", "rot", 2, sx * 5, steps=8)
    for k in range(3):
        for side, sx in (("l", 1), ("r", -1)):
            stagger.key(f"leg{k}_{side}", "rot", 0, (0, 0, sx * 16)).key(f"leg{k}_{side}", "rot", 3.0, (0, 0, sx * 16))
            stagger.key(f"leg{k}_knee_{side}", "rot", 0, (0, 0, sx * 26)).key(f"leg{k}_knee_{side}", "rot", 3.0, (0, 0, sx * 26))
    stagger.orbit("core_glow", "scl", (0.2, 0.2, 0.2), (0, 1.5, 0), base=(0.7, 0.7, 0.7), steps=12)
    stagger.wave("core_glow", "rot", 1, 25, steps=8)
    stagger.wave("antenna_l", "rot", 0, 10, steps=8)
    stagger.wave("antenna_r", "rot", 0, -10, phase=1.0, steps=8)

    roar = b.anim("roar", 2.0, "once")                 # cabeça erguida, mandíbulas bem abertas, élitros entreabertos
    roar.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.4, (-26, 0, 0)).key("head", "rot", 1.6, (-26, 0, 0)).key("head", "rot", 2.0, (0, 0, 0))
    for t, a in ((0, 0), (0.4, -40), (1.6, -40), (2.0, 0)):
        roar.key("mandible_l", "rot", t, (0, a, 0)).key("mandible_r", "rot", t, (0, -a, 0))
    for side, sx in (("l", 1), ("r", -1)):
        roar.key(f"shell_{side}", "rot", 0, (0, 0, 0)).key(f"shell_{side}", "rot", 0.5, (0, 0, sx * -24)).key(f"shell_{side}", "rot", 1.6, (0, 0, sx * -24)).key(f"shell_{side}", "rot", 2.0, (0, 0, 0))
    roar.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.4, (0, 1.4, 0)).key("body", "pos", 1.6, (0, 1.4, 0)).key("body", "pos", 2.0, (0, 0, 0))
    roar.orbit("antenna_l", "rot", (12, 0, 10), (0, 0, 0), steps=8)
    roar.orbit("antenna_r", "rot", (12, 0, 10), (1.2, 0, 0.8), steps=8)
    for anim in (idle, dormant, walk, charge, bite, slam, roar):
        for side in ("l", "r"):
            anim.key(f"wing_{side}", "scl", 0, (0.15, 1, 0.6)).key(f"wing_{side}", "scl", anim.anim.length, (0.15, 1, 0.6))
    return b


if __name__ == "__main__":
    make("void_scarab", build, P, {}, anim="walk")
