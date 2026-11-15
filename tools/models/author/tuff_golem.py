"""Golem de Tufo: estátua de tufo com capa de lã (a cor vem do bloco) e olhos ciano (Mob Vote 2022).

A referência é uma pose esticada (braços abertos), então a textura é pintada por código com a paleta
medida na imagem (tufo cinza-quente em quatro tons, olhos ciano em órbitas escuras) em vez de projetada."""
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import run  # noqa: E402
from vx.build import Builder  # noqa: E402

TUFF = [(158, 156, 141), (133, 131, 122), (133, 131, 122), (101, 105, 101), (139, 143, 135), (80, 83, 83)]
P = dict(head=9.0, torso_w=8.0, torso_h=7.0, torso_d=6.0, skirt_h=3.0, arm_l=9.0, arm=3.0, leg_h=3.0)


def tuff_paint(rng, light=0.0):
    def fn(i, j, w, h):
        c = rng.choice(TUFF)
        return tuple(max(0, min(255, int(v + light))) for v in c)
    return fn


def build(p=P):
    rng = random.Random(7)
    b = Builder("tuff_golem", res=(128, 128))
    lh, th, tw, td, sh = p["leg_h"], p["torso_h"], p["torso_w"], p["torso_d"], p["skirt_h"]
    base = lh
    top = base + sh + th
    b.bone("root", origin=(0, 0, 0))
    b.bone("body", "root", origin=(0, base + sh, 0))
    b.cube("body", "torso", (-tw / 2, base + sh, -td / 2), (tw / 2, top, td / 2))
    b.bone("cape_tint", "body", origin=(0, top, td / 2))
    b.cube("cape_tint", "cape_body", (-tw / 2 - 0.6, base + sh - 0.2, -td / 2 - 0.6), (tw / 2 + 0.6, top - 1.0, td / 2 + 0.6))
    b.cube("cape_tint", "cape_skirt", (-tw / 2 - 1.0, base - 0.2, -td / 2 - 1.0), (tw / 2 + 1.0, base + sh, td / 2 + 1.0))
    b.cube("cape_tint", "cape_collar", (-tw / 2 - 0.6, top - 1.0, -td / 2 - 0.6), (tw / 2 + 0.6, top - 0.2, td / 2 + 0.6))
    hs = p["head"]
    b.bone("head", "body", origin=(0, top, 0))
    b.cube("head", "skull", (-hs / 2, top, -hs / 2), (hs / 2, top + hs, hs / 2))
    al, aw = p["arm_l"], p["arm"]
    for name, sx in (("arm_l", 1), ("arm_r", -1)):
        b.bone(name, "body", origin=(sx * (tw / 2 + aw / 2 + 1.0), top - 1, 0))
        x0 = sx * (tw / 2 + 1.0)
        b.cube(name, name, (min(x0, x0 + sx * aw), top - 1 - al, -aw / 2), (max(x0, x0 + sx * aw), top - 1, aw / 2))
    b.locator("arm_l", "hand_item", (tw / 2 + aw / 2 + 1.0, top - 1 - al + 1, -aw / 2 - 1.5))
    for name, sx in (("leg_l", 1), ("leg_r", -1)):
        b.bone(name, "root", origin=(sx * 2, lh, 0))
        b.cube(name, name, (sx * 2 - 1.5, 0, -1.5), (sx * 2 + 1.5, lh, 1.5))
    # ── textura ──
    for name in ("torso", "skull", "arm_l", "arm_r", "leg_l", "leg_r"):
        for face in ("north", "south", "east", "west", "up", "down"):
            light = {"up": 14, "down": -22}.get(face, 0)
            b.paint(name, face, tuff_paint(rng, light))
    # rosto: órbitas escuras com olhos ciano e a testa pesada
    sk = b.cubes["skull"]
    w = int(math.ceil(hs))

    def face_fn(i, j, fw, fh):
        for ex in (2, fw - 3):                      # centro do olho: ciano brilhante
            if i == ex and 4 <= j <= 5:
                return (79, 209, 232) if j == 4 else (60, 170, 200)
        for ex in (1, fw - 3):                      # órbitas escuras
            if ex <= i <= ex + 1 and 3 <= j <= 5:
                return (24, 26, 28)
        if j == 2 and 0 < i < fw - 1:
            return (90, 92, 88)                     # testa pesada
        return TUFF[rng.randrange(len(TUFF))]

    b.paint(sk, "north", face_fn)
    # capa: padrão em cinzas (a tinta multiplica): listras verticais e barra mais clara
    for name in ("cape_body", "cape_skirt", "cape_collar"):
        for face in ("north", "south", "east", "west", "up", "down"):
            def cape(i, j, fw, fh, name=name):
                v = 215 if (i % 4 != 3) else 185
                if name == "cape_skirt":
                    v = 235 if j < fh - 1 else 255
                    if i % 3 == 0:
                        v -= 22
                if rng.random() < 0.08:
                    v -= 18
                return (v, v, v)
            b.paint(name, face, cape)
    # ── animações ──
    idle = b.anim("idle", 4.0, "loop")
    idle.wave("head", "rot", 2, 0.8, steps=8)
    idle.wave("body", "scl", 1, 0.006, base=(1, 1, 1), steps=8)
    wake = b.anim("wake", 1.2, "once")
    wake.key("head", "rot", 0.0, (0, 0, 0)).key("head", "rot", 0.4, (-6, 25, 6)).key("head", "rot", 0.8, (4, -20, -4)).key("head", "rot", 1.2, (0, 0, 0))
    for n, sx in (("arm_l", 1), ("arm_r", -1)):
        wake.key(n, "rot", 0.0, (0, 0, 0)).key(n, "rot", 0.5, (-40, 0, sx * 18)).key(n, "rot", 1.2, (0, 0, 0))
    wake.key("body", "pos", 0.0, (0, 0, 0)).key("body", "pos", 0.3, (0, 0.5, 0)).key("body", "pos", 0.6, (0, 0, 0))
    walk = b.anim("walk", 0.8, "loop")
    for name, ph in (("leg_l", 0), ("leg_r", math.pi)):
        walk.wave(name, "rot", 0, 30, phase=ph)
    walk.wave("arm_l", "rot", 0, 22, phase=math.pi)
    walk.wave("arm_r", "rot", 0, 22, phase=0)
    walk.wave("body", "pos", 1, 0.35, steps=4)
    walk.wave("body", "rot", 2, 3, steps=4)
    walk.wave("head", "rot", 2, 3, phase=0.4, steps=4)
    walk.wave("cape_tint", "rot", 0, 4, phase=0.8, steps=4)
    carry = b.anim("carry", 0.8, "loop")
    carry.key("arm_l", "rot", 0, (-65, 0, -5)).key("arm_l", "rot", 0.8, (-65, 0, -5))
    carry.key("arm_r", "rot", 0, (-30, 0, 5)).key("arm_r", "rot", 0.8, (-30, 0, 5))
    for name, ph in (("leg_l", 0), ("leg_r", math.pi)):
        carry.wave(name, "rot", 0, 30, phase=ph)
    carry.wave("body", "pos", 1, 0.3, steps=4)
    return b


if __name__ == "__main__":
    run("tuff_golem", build, ref="tuff_golem.png", params=dict(P), fit_kw=dict(yaw_range=(-50, 50), pitch_range=(0, 35)), frames=[0, 3, 6, 9],
        pose=dict(bones=["body", "head", "arm_l", "arm_r", "leg_l", "leg_r"], work=90, color_w=0.4, passes=4, sweep=True,
                  limits={"head": {"x": (-50, 50), "y": (-70, 70), "z": (-45, 45)},
                          "body": {"x": (-30, 30), "y": (-40, 40), "z": (-30, 30)}}),
        exclude=("cape_body", "cape_skirt", "cape_collar"),
        pairs=[("arm_l", "arm_r"), ("leg_l", "leg_r")])
