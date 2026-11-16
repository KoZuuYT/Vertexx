"""The Great Hunger (Mob C, MINECON Earth 2017): réptil pequeno camuflado no chão, boca enorme sempre faminta.

Engole itens soltos no chão e separa encantamentos de armas, ferramentas e livros. É o único mob da lista
com variações: o corpo imita o bloco onde ele se esconde e o broto no topo muda junto (referências:
``models/refs/great_hunger``, banner com cinco variantes + arte conceitual com grama).

Geometria: mandíbula de baixo 10x3x8 (o corpo), mandíbula de cima 10x4x8 articulada atrás, boca vermelha,
olhos nas laterais, quatro patas curtas e um broto cruzado (dois planos) no topo."""
import math

from kit import make
from style import R, Designer, rgb, shade

MOUTH = R("#3a0807", "#5e0d0b", "#9a2020", "#c83a3a", "#e05a52")
TOOTH, EYE_W, EYE_K = rgb("#efe9d8"), rgb("#f1f5f0"), rgb("#1a1414")

VARIANTS = {
    # nome: (rampa do corpo, rampa do topo, broto)
    "dirt": (R("#3b2a1c", "#5a4030", "#77553a", "#8f6b4a", "#a5825d"), None, "dead_bush"),
    "sand": (R("#8f8670", "#b5aa88", "#d2c7a0", "#e6dcb6", "#f3ecd0"), None, "twig"),
    "wood": (R("#2f2012", "#4a3320", "#664a2c", "#80603a", "#9a7548"), None, "stump"),
    "stone": (R("#4a4a4a", "#646464", "#7e7e7e", "#979797", "#b0b0b0"), R("#33461e", "#4f6b2c", "#6a8a38"), "dandelion"),
    "amethyst": (R("#2e2050", "#4a3878", "#6a52a2", "#8c70c8", "#b496e8"), None, "crystal"),
    "grass": (R("#555451", "#6d6961", "#84837b", "#9a958b", "#b3aea2"), R("#1c411d", "#2e5824", "#4f8a2a", "#7aa83a", "#9bc84a"), "grass"),
}

SPROUTS = {
    "dead_bush": (["...b...", "b..b..b", ".b.b.b.", "..bbb..", "b..b..b", ".b.b.b.", "..bbb..", "...b..."],
                  {"b": rgb("#6b4a2a")}),
    "twig": ([".....c.", "....c..", ".c..c..", "..c.c..", "...cc..", "....c..", "....c..", "....c.."],
             {"c": rgb("#8a6a44")}),
    "stump": (["...w...", "..www..", "..wWw..", "..wWw..", "..wWw..", "..wWw..", "..www..", "..www.."],
              {"w": rgb("#7a5530"), "W": rgb("#a07444")}),
    "dandelion": (["..yyy..", ".yYYYy.", "..yyy..", "...g...", ".g.g...", "..gg.g.", "...gg..", "...g..."],
                  {"y": rgb("#e8c020"), "Y": rgb("#fff07a"), "g": rgb("#4f8a2a")}),
    "crystal": (["...p...", "..pP...", "..pPp.p", "p.pPp.P", "P.pPpPp", "PppPpPp", ".pPPPp.", "..ppp.."],
                {"p": rgb("#8c70c8"), "P": rgb("#c8aef5")}),
    "grass": (["g.....g", "g..g..g", ".g.g.g.", ".g.g.g.", ".gGgGg.", "..gGg..", "..ggg..", "...g..."],
              {"g": rgb("#4f8a2a"), "G": rgb("#7aa83a")}),
}


def build_variant(v):
    body, top_ramp, sprout = VARIANTS[v]

    def build(p=None):
        b = Designer(f"great_hunger_{v}", res=(64, 64))
        b.bone("root")
        # patas
        for name, z in (("legf_l", -2.5), ("legb_l", 2.5)):
            b.bone(name, "root", origin=(3.5, 2, z))
            b.box(name, name, (2, 2, 2), (3.5, 0, z))
            b.skin(name, lambda cv: cv.noise(body, 1.6, 0.4))
            b.mirror(name, name[:-2] + "_r")
        b.bone("body", "root", origin=(0, 2, 0))
        b.box("body", "jaw_low", (10, 3, 8), (0, 2, 0))

        def low(cv):
            cv.noise(body, 2.0, 0.5)
            if cv.face == "up":                       # boca: língua e gengiva por dentro
                cv.noise(MOUTH, 2.4, 0.4).border(MOUTH[1])
                for i in (1, 3, cv.w - 4, cv.w - 2):
                    cv.set(i, 0, TOOTH)
            elif cv.face == "north":
                cv.hline(0, MOUTH[1], 1, cv.w - 1)
                for i in (3, cv.w - 4):
                    cv.set(i, 0, TOOTH)
        b.skin("body", None) if False else b.skin("jaw_low", low)
        b.bone("head", "body", origin=(0, 5, 4))
        b.box("head", "jaw_up", (10, 4, 8), (0, 5, 0))

        def up(cv):
            cv.noise(body, 2.4, 0.5)
            if top_ramp is not None and cv.face == "up":
                cv.noise(top_ramp, len(top_ramp) - 2, 0.6)
            elif top_ramp is not None and cv.face != "down":   # topo escorrendo pelas laterais
                cv.drips(top_ramp[len(top_ramp) // 2], 0, 1, 2 if v == "stone" else 3, 0.75, edge=top_ramp[0])
            if cv.face == "down":
                cv.noise(MOUTH, 1.6, 0.4).border(MOUTH[0])
                for i in (1, 3, cv.w - 4, cv.w - 2):
                    cv.set(i, 0, TOOTH)
            elif cv.face == "north":
                cv.hline(cv.h - 1, MOUTH[0], 1, cv.w - 1)
                for i in (2, cv.w - 3):
                    cv.set(i, cv.h - 1, TOOTH)
                cv.rect(0, 1, 2, 1, EYE_W).set(1, 1, EYE_K)          # olhos nas quinas da frente
                cv.rect(cv.w - 2, 1, 2, 1, EYE_W).set(cv.w - 2, 1, EYE_K)
            elif cv.face in ("east", "west"):           # olho perto da frente, olhando de lado
                fi = 0 if cv.face == "west" else cv.w - 3
                cv.rect(fi + 1 if cv.face == "west" else fi, 1, 2, 2, EYE_W)
                cv.set(fi + 1 if cv.face == "west" else fi + 1, 2, EYE_K)
        b.skin("jaw_up", up)
        # broto: dois planos cruzados (bordas transparentes recortadas)
        rows, legend = SPROUTS[sprout]
        for n, ry in (("sprout_a", 45), ("sprout_b", -45)):
            b.bone(n, "head", origin=(0, 9, 0), rot=(0, ry, 0))
            c = b.cube(n, n, (-3.5, 9, 0), (3.5, 17, 0), faces=("north", "south"))
            for f in ("north", "south"):
                cv = b.canvas(c, f)
                cv.clear()
                cv.sprite(rows, legend, 0, 0, flip=f == "south")
        # ── animações ──
        idle = b.anim("idle", 3.0, "loop")
        idle.wave("head", "rot", 0, 2.5, base=(4, 0, 0), steps=6)
        idle.wave("body", "scl", 1, 0.02, base=(1, 1, 1), steps=6)
        idle.wave("sprout_a", "rot", 2, 5, steps=6).wave("sprout_b", "rot", 2, -5, phase=1, steps=6)
        gape = b.anim("gape", 1.6, "loop")             # boca escancarada, à espreita
        gape.wave("head", "rot", 0, 3, base=(42, 0, 0), steps=8)
        gape.wave("body", "pos", 1, 0.15, steps=8)
        chomp = b.anim("chomp", 0.5, "once")           # engole
        chomp.key("head", "rot", 0, (42, 0, 0)).key("head", "rot", 0.12, (0, 0, 0)).key("head", "rot", 0.25, (8, 0, 0)) \
             .key("head", "rot", 0.35, (0, 0, 0)).key("head", "rot", 0.5, (4, 0, 0))
        chomp.key("body", "scl", 0, (1, 1, 1)).key("body", "scl", 0.15, (1.08, 0.9, 1.08)).key("body", "scl", 0.5, (1, 1, 1))
        walk = b.anim("walk", 0.5, "loop")
        for n, ph in (("legf_l", 0), ("legb_r", 0), ("legf_r", math.pi), ("legb_l", math.pi)):
            walk.wave(n, "rot", 0, 30, phase=ph, steps=6)
        walk.wave("body", "pos", 1, 0.3, steps=4).wave("head", "rot", 0, 5, base=(6, 0, 0), steps=4)
        return b
    return build


SPEC = {"a.png": {"role": "color", "note": "banner com as cinco variantes"},
        "b.png": {"role": "color", "note": "arte conceitual (variante de grama)"}}

if __name__ == "__main__":
    for v in VARIANTS:
        make(f"great_hunger_{v}", build_variant(v), {}, SPEC, anim="gape", folder="great_hunger", evaluate=False)
