"""Wildfire (Mob Vote de Minecraft Dungeons/Legends, boss do Vertexx): cabeça de pedra-negra com coroa e gema
vermelha, coluna de fogo e quatro escudos flamejantes que orbitam (o plugin solta as lâminas ``blade_*`` com
``ModelInstance.pin``). Referências: ``models/refs/wildfire`` (quatro renders de ângulos e escalas diferentes)."""
import math

from kit import make
from style import R, Designer, rgb

STONE = R("#170605", "#260a08", "#330f0c", "#4a1a10", "#5e2a14")
FIRE = R("#5e0d0b", "#7f3405", "#a24a06", "#b77306", "#d88a0a", "#f29709", "#f1cd2f", "#faf446", "#f9f887")
GOLD = R("#835908", "#b77306", "#f1cd2f", "#faf446", "#f9f887")
GEM = R("#5a0410", "#9a0c1e", "#d0182c", "#ff5a5a", "#ffc0c0")
EYE_W, EYE_K = rgb("#f2efe0"), rgb("#140404")

P = dict(head=8.0, core_h=14.0, blade_w=7.0, blade_h=14.0, off=7.0, tilt=14.0)
BLADES = (("blade_fl", 1, -1), ("blade_fr", -1, -1), ("blade_bl", 1, 1), ("blade_br", -1, 1))


def build(p=P):
    b = Designer("wildfire", res=(128, 64))
    ch, hs = p["core_h"], p["head"]
    b.bone("root")
    b.bone("core", "root", origin=(0, 2, 0))
    b.box("core", "core", (4, ch, 4), (0, 2, 0))
    b.skin("core", lambda cv: cv.flames(FIRE, 0.6) if cv.face not in ("up", "down") else cv.fill(FIRE[1]))
    top = 2 + ch
    b.bone("head", "core", origin=(0, top, 0))
    b.box("head", "skull", (hs, hs, hs), (0, top, 0))

    def skull(cv):
        if cv.face == "up":
            cv.noise(GOLD, 2.6, 0.5).border(GOLD[1])
        elif cv.face == "down":
            cv.noise(STONE, 1.5, 0.4)
        else:
            cv.noise(STONE, 2.2, 0.5)
            cv.hline(0, STONE[4])
    b.skin("skull", skull)

    def face(cv):
        cv.noise(STONE, 2.0, 0.45)
        cv.hline(0, STONE[4])
        cv.rect(3, 1, 2, 2, GEM[2]).set(3, 1, GEM[4]).set(4, 2, GEM[1])     # gema (também no osso de brilho)
        cv.set(1, 3, EYE_W).set(2, 3, EYE_K).set(6, 3, EYE_W).set(5, 3, EYE_K)
        for j in range(4, 8):                                              # "barba" de brasa sob os olhos
            for i in range(2, 6):
                cv.set(i, j, FIRE[min(6, max(0, 5 - (j - 4) + int(cv.rng.integers(-1, 2))))])
        cv.vline(2, STONE[1], 5, 8).vline(5, STONE[1], 6, 8)
    b.skin("skull", face, faces=("north",))
    b.bone("eye_glow", "head", origin=(0, top + hs - 2, -hs / 2))
    b.box("eye_glow", "gem", (2, 2, 0.5), (0, top + hs - 3, -hs / 2 - 0.25 - 0.5), anchor="bottom")
    b.skin("gem", lambda cv: cv.fill(GEM[3]).set(0, 0, GEM[4]) if cv.face == "north" else cv.fill(GEM[2]))
    # coroa: quatro pontas nas quinas do topo
    for name, x, z in (("spike_fl", 1, -1), ("spike_bl", 1, 1)):
        b.bone(name, "head", origin=(x * (hs / 2 - 0.5), top + hs, z * (hs / 2 - 0.5)))
        b.box(name, name, (1, 2, 1), (x * (hs / 2 - 0.5), top + hs, z * (hs / 2 - 0.5)))
        b.skin(name, lambda cv: cv.fill(STONE[3]).set(0, 0, GOLD[3]) if cv.face != "up" else cv.fill(GOLD[3]))
        b.mirror(name, name.replace("_fl", "_fr").replace("_bl", "_br"))

    # escudos: face larga (norte) virada para fora, inclinados para fora no topo
    bw, bh, off = p["blade_w"], p["blade_h"], p["off"]
    first = {}
    for name, sx, sz in BLADES:
        x, z = sx * off * 0.72, sz * off * 0.72
        yaw = math.degrees(math.atan2(-sx, -sz))
        b.bone(name, "core", origin=(x, 3 + bh / 2, z), rot=(-p["tilt"], yaw, 0))
        b.box(name, name, (bw, bh, 2), (x, 3, z))

        def blade(cv):
            if cv.face in ("north", "south"):
                cv.flames(FIRE, 1.0)
                cv.vline(0, FIRE[2]).vline(cv.w - 1, FIRE[2])
            elif cv.face == "up":
                cv.fill(FIRE[7]).set(0, 0, FIRE[6])
            elif cv.face == "down":
                cv.fill(FIRE[0])
            else:
                cv.vgrad(FIRE, 7, 0, 0.5)
        b.skin(name, blade)

    # ── animações (as lâminas orbitam presas pelo plugin; aqui só corpo e cabeça) ──
    idle = b.anim("idle", 2.4, "loop")
    idle.wave("core", "pos", 1, 0.7, steps=6)
    idle.orbit("head", "rot", (3, 8, 0), (1.0, 0, 0), steps=6)
    walk = b.anim("walk", 1.0, "loop")
    walk.wave("core", "pos", 1, 0.6, steps=4).wave("core", "rot", 0, 6, steps=4)
    walk.wave("head", "rot", 0, -4, phase=1.0, steps=4)
    spin = b.anim("spin", 0.8, "loop")
    spin.key("core", "rot", 0.0, (0, 0, 0)).key("core", "rot", 0.4, (0, 180, 0)).key("core", "rot", 0.8, (0, 360, 0))
    spin.key("head", "rot", 0.0, (0, 0, 0)).key("head", "rot", 0.4, (0, -180, 0)).key("head", "rot", 0.8, (0, -360, 0))
    windup = b.anim("windup", 1.0, "hold")     # recua: cabeça para trás, corpo arqueado
    windup.key("core", "rot", 0.0, (0, 0, 0)).key("core", "rot", 1.0, (14, 0, 0), "catmullrom")
    windup.key("head", "rot", 0.0, (0, 0, 0)).key("head", "rot", 1.0, (-12, 0, 0), "catmullrom")
    lunge = b.anim("lunge", 0.5, "loop")       # mergulha para a frente
    lunge.key("core", "rot", 0.0, (-28, 0, 0)).key("core", "rot", 0.5, (-28, 0, 0))
    lunge.key("head", "rot", 0.0, (12, 0, 0)).key("head", "rot", 0.5, (12, 0, 0))
    death = b.anim("death", 2.0, "hold")
    death.key("core", "rot", 0.0, (0, 0, 0)).key("core", "rot", 2.0, (-80, 0, 12), "catmullrom")
    death.key("core", "pos", 0.0, (0, 0, 0)).key("core", "pos", 2.0, (0, -8, 0))
    death.key("head", "rot", 0.0, (0, 0, 0)).key("head", "rot", 2.0, (30, 20, 0))
    return b


SPEC = {"a.png": {"role": "shape", "yaw": (-60, 60), "pitch": (0, 40)},
        "b.png": {"role": "shape", "yaw": (-60, 60), "pitch": (0, 50)},
        "c.png": {"role": "shape", "yaw": (-60, 60), "pitch": (-10, 30)},
        "d.png": {"role": "shape", "yaw": (20, 70), "pitch": (20, 45)}}

if __name__ == "__main__":
    make("wildfire", build, P, SPEC, anim="idle", fit=["head", "core_h", "blade_w", "blade_h", "off", "tilt"],
         bounds={"head": (6, 10), "core_h": (8, 18), "blade_w": (5, 10), "blade_h": (10, 20), "off": (5, 11), "tilt": (0, 35)},
         steps={"tilt": 4})
