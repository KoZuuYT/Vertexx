"""Pterodáctilo de Caverna (Bone Pterodactyl): esqueleto de osso marfim com membranas escuras rasgadas.

Réplica da referência "Bone Pterodactyl": crânio grande de focinho longo com dentes um a um, duas placas de crista
voltadas para trás, olhos âmbar em órbitas escuras, pescoço de vértebras, coluna e costelas vazadas, asas com braço,
cotovelo, pulso, polegar-gancho e quatro dedos em leque com membrana escura em faixas escalonadas (borda rasgada),
pernas de osso com garras e cauda longa de vértebras com espinhos terminando em pá.

1 unidade = 1/16 de bloco. Frente em -Z. Texturas: bone_block e deepslate vanilla como base, com pintura própria
por face (rachaduras, suturas, veias, buracos, boca)."""
import math

from kit import make
from style import R, Designer, vanilla

BONE = R("#6f6a58", "#9a947e", "#bcb69e", "#d8d3bb", "#ece8d2", "#f7f4e4")
DUST = R("#4b4a3c", "#6a6753", "#8b8770", "#aaa68e", "#c4c0a8")
MEMB = R("#121317", "#1b1d23", "#262932", "#33363f", "#464a55")
MOUTH = R("#2b1512", "#43211b", "#5c2f26", "#7a4034")
EYE = R("#ff8a12", "#ffc23a", "#fff0a0")
DARK = (22, 18, 15)
HOLE = (0, 0, 0, 0)
CRACK = BONE[0]

YAW = -1     # sentido positivo da abertura dos dedos (+1/-1)
PITCH = -1   # sentido positivo da inclinação para trás (+1/-1)

P = dict(w_y=16.5)


# ---------------------------------------------------------------- pincéis
def bone(cv, lo=0.1, hi=1.0, cracks=True):
    vanilla("bone_block_side", BONE, lo, hi, top=0.06, bottom=-0.12)(cv)
    cv.darken_edges(-0.1, "tblr")
    cv.speckle(DUST[2], 0.05)
    if cracks and cv.w >= 4 and cv.h >= 4 and cv.rng.random() < 0.5:
        i, j = int(cv.rng.integers(1, cv.w - 1)), int(cv.rng.integers(0, max(1, cv.h - 3)))
        for k in range(int(cv.rng.integers(2, 4))):
            cv.set(i + (k % 2), j + k, CRACK)


def dusty(cv):
    vanilla("bone_block_top", DUST, 0.0, 1.0)(cv)
    cv.darken_edges(-0.08, "tblr")


def thin(cv):
    bone(cv, 0.25, 1.0, cracks=False)


def skull(cv):
    bone(cv, 0.15, 1.0)
    if cv.face == "up":
        for j in range(cv.h):
            cv.set(cv.w // 2 + (1 if j % 2 else 0), j, CRACK)
    elif cv.face == "north":
        cv.rect(0, 0, cv.w, 1, BONE[1])


def snout(cv):
    if cv.face == "down":
        cv.fill(MOUTH[1])
        for i in range(1, cv.w, 2):
            cv.vline(i, MOUTH[2])
        return
    bone(cv, 0.2, 1.0)
    if cv.face == "up" and cv.w >= 3:
        cv.set(1, 0, DARK)
        cv.set(cv.w - 2, 0, DARK)


def jaw(cv):
    if cv.face == "up":
        cv.fill(MOUTH[0])
        cv.vline(cv.w // 2, MOUTH[3])
        return
    bone(cv, 0.0, 0.8)


def socket(cv):
    cv.fill(DARK)


def eye(cv):
    cv.fill(EYE[1])
    cv.set(0, 0, EYE[0])
    cv.set(cv.w - 1, cv.h - 1, EYE[0])
    if cv.w > 2 and cv.h > 2:
        cv.rect(1, 1, cv.w - 2, cv.h - 2, EYE[2])


def tooth(cv):
    cv.fill(BONE[5])
    cv.darken_edges(-0.12, "b")


def membrane(seed):
    """Couro escuro (ardósia vanilla recolorida), veias claras ao longo da faixa, buracos e borda esfiapada."""
    def paint(cv):
        vanilla("deepslate", MEMB, 0.05, 1.0)(cv)
        if cv.face in ("up", "down"):
            for i in range(cv.w):
                f = i / max(1, cv.w - 1)
                for j in range(cv.h):
                    c = cv.get(i, j)
                    k = 1 - 0.2 * f
                    cv.set(i, j, (int(c[0] * k), int(c[1] * k), int(c[2] * k)))
            for j in range(1, cv.h, 3):
                cv.hline(j, MEMB[3])
            cv.hline(0, DUST[0])
        for j in range(cv.h):
            for i in range(cv.w):
                h = ((i * 73856093) ^ (j * 19349663) ^ (seed * 83492791)) % 100
                if h < 9:
                    cv.set(i, j, HOLE)
                    if h < 4:
                        cv.set(i + 1, j, HOLE)
        for i in range(cv.w):
            if (i * 5 + seed) % 7 < 2:
                cv.set(i, cv.h - 1, HOLE)
    return paint


# ---------------------------------------------------------------- modelo
def build(p=P):
    b = Designer("cave_glider", res=(256, 256), fmt="free")
    wy = p["w_y"]

    def cube(bn, name, size, center, paint, rot=None, pivot=None):
        b.box(bn, name, size, center, anchor="center", rot=rot, origin=pivot)
        b.skin(name, paint)

    b.bone("root", origin=(0, 14, 0))
    b.bone("body", "root", origin=(0, 14, 0))

    # ---- coluna, costelas, esterno, pélvis
    n = 9
    for k in range(n):
        z = -7.6 + k * 1.95
        cube("body", f"vert{k}", (2.4, 2.4, 1.8), (0, 16.4, z), bone)
        cube("body", f"spin{k}", (0.9, 1.5, 1.0), (0, 18.2, z), thin)
    fr = (0.72, 0.9, 1.0, 1.0, 0.96, 0.86, 0.72)
    for k, f in enumerate(fr):
        z = -6.6 + k * 2.15
        for sx, s in ((1, "l"), (-1, "r")):
            hgt = 5.6 * f
            cube("body", f"rib_a{s}{k}", (3.8, 1.0, 0.7), (sx * 3.1, 15.1, z), thin)
            cube("body", f"rib_b{s}{k}", (1.0, hgt, 1.3), (sx * (4.5 + 0.6 * f), 15.1 - hgt / 2, z), thin)
            cube("body", f"rib_c{s}{k}", (3.0 * f + 0.8, 1.0, 0.9), (sx * (3.2 + 0.6 * f), 15.1 - hgt, z), thin)
    cube("body", "sternum", (1.6, 1.0, 12.5), (0, 8.9, -0.4), bone)
    cube("body", "keel", (0.8, 2.6, 4.5), (0, 7.5, -5.2), thin)
    cube("body", "pelvis", (6.4, 3.2, 4.4), (0, 16.0, 11.4), bone)
    for sx, s in ((1, "l"), (-1, "r")):
        cube("body", f"ilium_{s}", (1.2, 3.4, 4.4), (sx * 3.6, 15.0, 11.2), bone)

    # ---- pescoço
    b.bone("neck", "body", origin=(0, 16.4, -9.0), rot=(-PITCH * 22, 0, 0))
    for k in range(4):
        s = 3.6 - 0.25 * k
        cube("neck", f"nv{k}", (s, s, 2.4), (0, 16.4, -10.4 - k * 2.5), bone)
        cube("neck", f"ns{k}", (0.9, 1.3, 1.2), (0, 16.4 + s / 2 + 0.5, -10.4 - k * 2.5), thin)
    cube("neck", "gorget", (4.4, 1.0, 5.5), (0, 14.3, -13.5), dusty)

    # ---- crânio
    hz = -19.2
    b.bone("head", "neck", origin=(0, 16.6, hz), rot=(PITCH * 22, 0, 0))
    cube("head", "cranium", (6.6, 6.0, 7.0), (0, 17.0, hz - 3.5), skull)
    cube("head", "snout", (3.8, 3.2, 9.6), (0, 16.2, hz - 11.8), snout)
    cube("head", "snout_ridge", (2.4, 1.0, 8.0), (0, 18.4, hz - 11.0), dusty)
    cube("head", "nose", (3.4, 3.8, 1.6), (0, 16.2, hz - 17.0), dusty)
    cube("head", "brow", (7.6, 1.3, 3.8), (0, 20.4, hz - 3.2), dusty)
    cube("head", "occiput", (4.8, 3.2, 1.6), (0, 17.6, hz + 0.3), bone)
    for sx, s in ((1, "l"), (-1, "r")):
        cube("head", f"cheek_{s}", (0.9, 2.0, 5.5), (sx * 3.7, 14.6, hz - 5.6), thin)
        cube("head", f"socket_{s}", (0.5, 2.8, 3.0), (sx * 3.55, 17.5, hz - 3.6), socket)
        cube("head", f"horn_{s}", (0.9, 0.9, 3.0), (sx * 3.9, 19.2, hz - 1.2), dusty, rot=(0, sx * YAW * 28, 0),
             pivot=(sx * 3.9, 19.2, hz - 1.2))
        for k in range(6):
            cube("head", f"ut{s}{k}", (0.8, 1.9, 0.8), (sx * 1.9, 13.65, hz - 8.2 - k * 1.55), tooth)
    # crista: duas placas inclinadas para trás
    for sx, s in ((1, "l"), (-1, "r")):
        pv = (sx * 1.7, 19.6, hz - 1.0)
        cube("head", f"crest_{s}", (1.3, 7.0, 10.0), (sx * 1.7, 23.0, hz + 3.0), bone,
             rot=(-PITCH * 32, sx * YAW * 12, 0), pivot=pv)
        cube("head", f"crest2_{s}", (1.0, 4.0, 6.0), (sx * 2.4, 21.6, hz + 0.4), dusty,
             rot=(-PITCH * 20, sx * YAW * 24, 0), pivot=(sx * 2.4, 19.8, hz - 1.5))
    b.bone("eyes_glow", "head", origin=(0, 17.5, hz - 3.6))
    for sx, s in ((1, "l"), (-1, "r")):
        cube("eyes_glow", f"eye_{s}", (0.4, 1.8, 1.9), (sx * 3.95, 17.5, hz - 3.6), eye)

    # ---- mandíbula
    b.bone("jaw", "head", origin=(0, 14.2, hz - 5.0))
    cube("jaw", "jaw", (3.6, 1.3, 10.5), (0, 12.9, hz - 11.2), jaw)
    cube("jaw", "chin", (3.0, 1.6, 1.6), (0, 12.8, hz - 16.8), dusty)
    for sx, s in ((1, "l"), (-1, "r")):
        cube("jaw", f"rail_{s}", (0.9, 1.0, 9.8), (sx * 1.3, 14.1, hz - 10.9), bone)
        cube("jaw", f"ramus_{s}", (0.9, 2.6, 1.6), (sx * 1.6, 14.3, hz - 6.0), bone)
        for k in range(5):
            cube("jaw", f"lt{s}{k}", (0.7, 1.6, 0.7), (sx * 1.0, 15.3, hz - 8.8 - k * 1.7), tooth)

    # ---- cauda
    b.bone("tail", "body", origin=(0, 15.6, 13.2))
    z = 13.2
    for k, w in enumerate((2.6, 2.3, 2.0, 1.7, 1.5, 1.3)):
        cube("tail", f"tv{k}", (w, w, 2.3), (0, 15.6, z + 1.15), bone)
        if k < 5:
            cube("tail", f"tsp{k}", (0.7, 1.1 + 0.1 * (5 - k), 0.9), (0, 15.6 + w / 2 + 0.5, z + 1.15), thin)
        z += 2.3
    b.bone("tail2", "tail", origin=(0, 15.6, z))
    for k, w in enumerate((1.2, 1.1, 1.0, 0.9, 0.8, 0.7)):
        cube("tail2", f"tw{k}", (w, w, 2.3), (0, 15.6, z + 1.15), bone)
        z += 2.3
    cube("tail2", "stalk", (0.6, 0.6, 1.8), (0, 15.6, z + 0.9), thin)
    cube("tail2", "spade", (5.2, 0.7, 5.2), (0, 15.6, z + 3.4), bone, rot=(0, 45, 0), pivot=(0, 15.6, z + 3.4))
    cube("tail2", "spade_core", (2.2, 0.9, 2.2), (0, 15.6, z + 3.4), dusty, rot=(0, 45, 0), pivot=(0, 15.6, z + 3.4))

    # ---- asas
    shx, shz = 3.6, -3.4
    wrist = 30.4
    ang = (0, 15, 31, 47)
    lens = (27.0, 29.0, 26.0, 20.5)
    for side, sx in (("l", 1), ("r", -1)):
        w, t = f"wing_{side}", f"tip_{side}"
        b.bone(w, "body", origin=(sx * shx, wy, shz))
        cube(w, f"shoulder_{side}", (3.4, 4.4, 3.8), (sx * 4.2, wy, shz), bone)
        cube(w, f"hum_{side}", (10.0, 2.6, 2.6), (sx * 9.4, wy, shz), bone)
        cube(w, f"elbow_{side}", (3.6, 3.4, 3.6), (sx * 15.2, wy, shz), dusty)
        cube(w, f"olecr_{side}", (1.2, 1.4, 1.4), (sx * 16.9, wy + 2.2, shz + 0.6), thin)
        cube(w, f"fore_{side}", (13.0, 2.2, 2.2), (sx * 23.2, wy, shz), bone)
        cube(w, f"wrist_{side}", (3.0, 3.0, 3.0), (sx * wrist, wy, shz), dusty)
        cube(w, f"thumb_{side}", (1.0, 1.0, 4.6), (sx * (wrist - 0.4), wy + 0.6, shz - 3.6), bone)
        cube(w, f"hook_{side}", (0.8, 0.8, 2.2), (sx * (wrist - 0.4), wy + 0.6, shz - 6.9), dusty)
        # membrana do braço (entre o corpo, o braço e o último dedo)
        # propatágio/braquiopatágio: faixas contínuas atrás do braço, mais fundas junto ao corpo e curtas no pulso
        x0 = shx - 0.1
        for q, dep in enumerate((13.0, 11.2, 9.4, 7.8, 6.4)):
            wq = (wrist - x0) / 5
            cube(w, f"mem{q}_{side}", (wq, 0.5, dep), (sx * (x0 + wq * (q + 0.5)), wy - 0.4, shz + dep / 2 + 0.6),
                 membrane(21 + q * 2 + (0 if sx > 0 else 11)))

        b.bone(t, w, origin=(sx * wrist, wy, shz))
        pv = (sx * wrist, wy, shz)
        for n_, (a, L) in enumerate(zip(ang, lens)):
            rot = (0, sx * YAW * a, 0)
            segs = ((0.0, 0.40, 1.8), (0.40, 0.75, 1.4), (0.75, 1.0, 1.0))
            for q, (f0, f1, th) in enumerate(segs):
                r0, r1 = f0 * L, f1 * L
                cube(t, f"f{n_}s{q}_{side}", (r1 - r0 + 0.1, th, th), (sx * (wrist + (r0 + r1) / 2), wy, shz),
                     bone if q < 2 else thin, rot=rot, pivot=pv)
            if n_ == 0:
                cube(t, f"fclaw_{side}", (0.7, 0.7, 2.2), (sx * (wrist + L + 0.4), wy, shz - 1.0), dusty, rot=rot, pivot=pv)
        # membrana entre dedos: três faixas escalonadas por vão (bordas recortadas)
        A = list(ang) + [78]
        Ls = list(lens) + [9.0]
        for g in range(4):
            da = math.radians(A[g + 1] - A[g])
            mid = (A[g] + A[g + 1]) / 2
            lm = min(Ls[g], Ls[g + 1])
            edges = (3.0, 0.42 * lm, 0.74 * lm, 0.98 * lm)
            for q in range(3):
                r0, r1 = edges[q], edges[q + 1]
                wd = max(1.5, 2 * (r0 + 0.35 * (r1 - r0)) * math.tan(da / 2) * 0.95)
                cube(t, f"tm{g}{q}_{side}", (r1 - r0, 0.5, wd), (sx * (wrist + (r0 + r1) / 2), wy - 0.4, shz),
                     membrane(40 + g * 3 + q + (0 if sx > 0 else 17)), rot=(0, sx * YAW * mid, 0), pivot=pv)

        # ---- perna
        lg = f"leg_{side}"
        hx = sx * 3.4
        b.bone(lg, "body", origin=(hx, 14.5, 11.6))
        cube(lg, f"thigh_{side}", (2.4, 5.8, 2.4), (hx, 11.6, 11.6), bone, rot=(PITCH * 38, 0, 0), pivot=(hx, 14.5, 11.6))
        ky = 14.5 - 5.8 * math.cos(math.radians(38))
        kz = 11.6 + PITCH * 5.8 * math.sin(math.radians(38))
        cube(lg, f"knee_{side}", (2.2, 2.2, 2.2), (hx, ky, kz), dusty)
        cube(lg, f"shin_{side}", (1.7, 6.0, 1.7), (hx, ky - 3.0, kz), bone, rot=(-PITCH * 52, 0, 0), pivot=(hx, ky, kz))
        fy = ky - 6.0
        cube(lg, f"foot_{side}", (1.6, 0.9, 3.0), (hx, fy - 0.3, kz - PITCH * 1.4), bone, rot=(-PITCH * 52, 0, 0), pivot=(hx, ky, kz))
        for n_, dx in enumerate((-0.9, 0.0, 0.9)):
            cube(lg, f"toe{n_}_{side}", (0.55, 0.6, 2.4), (hx + dx, fy - 0.4, kz - PITCH * 3.4), dusty,
                 rot=(-PITCH * 52, 0, 0), pivot=(hx, ky, kz))

    # ---- animações (os nomes são usados pelo Java)
    fly = b.anim("fly", 1.3, "loop")
    glide = b.anim("glide", 3.0, "loop")
    for side, sx in (("l", 1), ("r", -1)):
        fly.wave(f"wing_{side}", "rot", 2, sx * 34, steps=10)
        fly.wave(f"tip_{side}", "rot", 2, sx * 28, phase=0.9, steps=10)
        fly.wave(f"leg_{side}", "rot", 0, 8, phase=0.5, steps=10)
        glide.wave(f"wing_{side}", "rot", 2, sx * 4, steps=8)
        glide.wave(f"tip_{side}", "rot", 2, sx * 7, phase=0.6, steps=8)
    fly.wave("root", "pos", 1, 0.9, steps=10).wave("tail", "rot", 1, 9, steps=10).wave("tail2", "rot", 1, 12, phase=0.8, steps=10)
    fly.wave("neck", "rot", 0, 4, phase=0.5, steps=10)
    glide.wave("root", "pos", 1, 0.25, steps=8).wave("tail", "rot", 1, 6, steps=8).wave("tail2", "rot", 1, 9, phase=0.7, steps=8)
    glide.wave("neck", "rot", 1, 4, steps=8)
    scream = b.anim("scream", 0.9, "once")
    scream.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.2, (-PITCH * 28, 0, 0)).key("head", "rot", 0.8, (-PITCH * 28, 0, 0)).key("head", "rot", 0.9, (0, 0, 0))
    scream.key("jaw", "rot", 0, (0, 0, 0)).key("jaw", "rot", 0.2, (PITCH * 32, 0, 0)).key("jaw", "rot", 0.8, (PITCH * 32, 0, 0)).key("jaw", "rot", 0.9, (0, 0, 0))
    return b


if __name__ == "__main__":
    make("cave_glider", build, P, {}, anim="glide")
