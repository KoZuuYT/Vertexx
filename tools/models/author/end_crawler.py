"""Rastejador do Fim (End Crawler): besouro-aranha de carapaça de obsidiana, cabeça com quatro olhos magenta, três
segmentos com placas, espinhos de cristal lilás nas costas e oito pernas de duas juntas. Neutro; enrola-se em crítico.
Referência: prancha "End Crawler - Voxel Model Draft" (1 bloco de comprimento, meio bloco de altura)."""
import math

from kit import make
from style import R, Designer, rgb

OBS = R("#0d0914", "#171022", "#231a38", "#33264f", "#473568", "#5d4687")
VEIN = rgb("#b13fe0")
EYE = R("#7a1a78", "#c030c0", "#ff4ff0", "#ffd2fb")
LEG = R("#0a0810", "#150f20", "#221a34", "#33264f")
CRYSTAL = R("#7d62d6", "#a98ff2", "#d9ccff", "#ffffff")

P = dict(lift=2.6)

# (nome, z0, z1, largura, altura): da frente para trás
SEGMENTS = [("seg1", -5.0, 1.0, 8.0, 6.0), ("seg2", 1.0, 6.2, 7.0, 5.4), ("seg3", 6.2, 11.0, 5.6, 4.6)]


def shell(base=2.8, plates=True):
    def paint(cv):
        cv.noise(OBS, base + (0.8 if cv.face == "up" else -1.0 if cv.face == "down" else 0), 0.55)
        cv.speckle(OBS[5], 0.07)
        if plates and cv.face in ("up", "north", "south"):
            cv.hline(0, OBS[1])                                   # sulco entre as placas
            if cv.face == "up" and cv.w > 4:
                cv.vline(cv.w // 2, OBS[2], 1, cv.h - 1)             # nervura central
                for _ in range(2):
                    cv.set(int(cv.rng.integers(1, cv.w - 1)), int(cv.rng.integers(1, cv.h - 1)), VEIN)
        cv.darken_edges(-0.18)
    return paint


def build(p=P):
    b = Designer("end_crawler", res=(96, 64))
    lift = p["lift"]
    b.bone("root")
    b.bone("body", "root", origin=(0, lift + 2, 0))
    # plastrão e três segmentos com uma placa dorsal sobreposta
    b.box("body", "belly", (6.2, 2.0, 15.0), (0, lift, 0.8))
    b.skin("belly", shell(1.6, False))
    for name, z0, z1, w, h in SEGMENTS:
        zc, d = (z0 + z1) / 2, z1 - z0
        b.box("body", name, (w, h * 0.62, d), (0, lift + 0.4, zc))
        b.skin(name, shell(2.4))
        b.box("body", name + "_plate", (w - 1.0, h * 0.36, d - 0.8), (0, lift + 0.4 + h * 0.62, zc))
        b.drop_face(name + "_plate", "down")
        b.skin(name + "_plate", shell(3.1))
    # cristais do Fim nas costas, varridos para trás (e alguns cacos soltos)
    for k, (z, h, x, tilt) in enumerate(((-2.4, 3.8, 0.0, 16), (3.2, 4.6, 0.0, 20), (8.2, 3.4, 0.0, 24),
                                         (-1.0, 2.2, 2.2, 12), (4.6, 2.0, -2.0, 14), (0.6, 1.8, -2.4, 10))):
        top = lift + 0.4 + SEGMENTS[0 if z < 1 else 1 if z < 6.2 else 2][4] * 0.98
        b.box("body", f"spike{k}", (1.3 if h > 3 else 1.0, h, 1.3 if h > 3 else 1.0), (x, top - 0.3, z),
              rot=(tilt, 0, 0), origin=(x, top - 0.3, z))
        b.skin(f"spike{k}", lambda cv: cv.noise(CRYSTAL, 2.2 + (0.9 if cv.face in ("up", "north") else 0), 0.4))
    # cabeça: caixa larga com quatro olhos magenta e mandíbulas curvas
    b.bone("head", "body", origin=(0, lift + 2.6, -5.0))
    b.box("head", "head", (6.0, 4.6, 5.0), (0, lift - 0.4, -7.7))
    b.skin("head", shell(2.6, False))
    b.box("head", "brow", (5.2, 1.0, 3.6), (0, lift + 4.2, -7.0))
    b.drop_face("brow", "down")
    b.skin("brow", shell(3.2, False))
    b.bone("eyes_glow", "head", origin=(0, lift + 2.6, -10.2), glow=True)
    for name, x, y in (("eye_ul", 1.7, lift + 3.3), ("eye_ur", -1.7, lift + 3.3), ("eye_ll", 1.0, lift + 1.6), ("eye_lr", -1.0, lift + 1.6)):
        b.box("eyes_glow", name, (1.5 if y > lift + 2 else 1.1, 1.3, 0.5), (x, y, -10.35))
        b.skin(name, lambda cv: cv.fill(EYE[2]) if cv.face == "north" else cv.fill(EYE[1]))
    for name, x in (("mand_l", 1.7), ("mand_r", -1.7)):
        b.bone(name, "head", origin=(x, lift + 1.2, -9.8))
        b.box(name, name, (1.2, 3.2, 1.2), (x, lift - 1.9, -10.2))
        b.skin(name, shell(1.8, False))
        b.box(name, name + "_tip", (1.0, 1.0, 2.0), (x * 0.55, lift - 1.9, -11.4))
        b.skin(name + "_tip", shell(1.4, False))
    # oito pernas: coxa sobe e abre, tíbia desce até o chão
    zs = (-3.4, -0.2, 3.0, 6.2)
    yaws = (-34, -10, 12, 36)
    for k, z in enumerate(zs):
        hx = 3.0 + (0.6 if k in (1, 2) else 0)
        name = f"leg{k}_l"
        b.bone(name, "body", origin=(hx, lift + 1.6, z), rot=(0, yaws[k], 34))
        b.box(name, name, (5.4, 1.5, 1.5), (hx, lift + 1.6 - 0.75, z - 0.75), anchor="corner")
        b.skin(name, lambda cv: cv.noise(LEG, 1.8, 0.5))
        low = name + "_low"
        kx = hx + 5.2
        b.bone(low, name, origin=(kx, lift + 1.6, z), rot=(0, 0, -62))
        b.box(low, low, (1.3, 7.2, 1.3), (kx + 0.4, lift + 1.6 - 7.2, z - 0.65), anchor="corner")
        b.skin(low, lambda cv: cv.noise(LEG, 1.4, 0.5))
        b.mirror(name, f"leg{k}_r")

    idle = b.anim("idle", 3.0, "loop")
    idle.wave("body", "pos", 1, 0.25, steps=6).wave("mand_l", "rot", 1, 10, steps=6).wave("mand_r", "rot", 1, -10, steps=6)
    idle.wave("head", "rot", 1, 5, steps=6)
    walk = b.anim("walk", 0.55, "loop")
    for k in range(4):
        ph = (k % 2) * math.pi
        walk.orbit(f"leg{k}_l", "rot", (0, 18, 10), (0, ph, ph + math.pi / 2), steps=8)
        walk.orbit(f"leg{k}_r", "rot", (0, 18, -10), (0, ph + math.pi, ph + 1.5 * math.pi), steps=8)
    walk.wave("body", "rot", 2, 2, steps=4).wave("body", "pos", 1, 0.3, steps=4)
    bite = b.anim("bite", 0.4, "once")
    bite.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.12, (-22, 0, 0)).key("head", "rot", 0.4, (0, 0, 0))
    bite.key("mand_l", "rot", 0, (0, 0, 0)).key("mand_l", "rot", 0.12, (0, 38, 0)).key("mand_l", "rot", 0.4, (0, 0, 0))
    bite.key("mand_r", "rot", 0, (0, 0, 0)).key("mand_r", "rot", 0.12, (0, -38, 0)).key("mand_r", "rot", 0.4, (0, 0, 0))
    bite.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.12, (0, 0, -1.2)).key("body", "pos", 0.4, (0, 0, 0))
    curl = b.anim("curl", 0.5, "hold")             # recolhe a cabeça e as pernas, abaixa o casco
    curl.key("body", "pos", 0, (0, 0, 0)).key("body", "pos", 0.4, (0, -1.8, 0))
    curl.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.4, (50, 0, 0))
    for k in range(4):
        curl.key(f"leg{k}_l", "rot", 0, (0, 0, 0)).key(f"leg{k}_l", "rot", 0.4, (0, 0, 28))
        curl.key(f"leg{k}_r", "rot", 0, (0, 0, 0)).key(f"leg{k}_r", "rot", 0.4, (0, 0, -28))
    return b


if __name__ == "__main__":
    make("end_crawler", build, P, {}, anim="walk")
