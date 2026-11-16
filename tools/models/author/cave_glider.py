"""Pterodáctilo de Caverna: planador de crista longa e asas de membrana, cinza com crista âmbar e olhos brilhantes."""
from kit import make
from style import R, Designer, rgb

HIDE = R("#2a2d34", "#3c4049", "#50555f", "#686e7a")
MEMB = R("#2f3340", "#454b5c", "#5f667a", "#7d869c")
CREST = R("#8a4a06", "#d98a14", "#ffc23a")
EYE = R("#fff0a0", "#ffffff")

P = dict(span=15.0)


def build(p=P):
    b = Designer("cave_glider", res=(128, 64))
    sp = p["span"]
    b.bone("root", origin=(0, 8, 0))
    b.bone("body", "root", origin=(0, 8, 0))
    b.box("body", "torso", (4, 4, 9), (0, 8, 0))
    b.skin("torso", lambda cv: cv.noise(HIDE, 2.4, 0.6))
    b.bone("tail", "body", origin=(0, 8, 4.5))
    b.box("tail", "tail", (1.4, 1.4, 5), (0, 8, 7.5))
    b.skin("tail", lambda cv: cv.noise(HIDE, 1.8, 0.5))
    b.bone("head", "body", origin=(0, 9, -4.5))
    b.box("head", "head", (3, 3, 3.4), (0, 9.4, -6))
    b.skin("head", lambda cv: (cv.noise(HIDE, 2.2, 0.5), cv.set(0, 1, EYE[0]) if cv.face == "north" else None,
                               cv.set(cv.w - 1, 1, EYE[0]) if cv.face == "north" else None)[0])
    b.box("head", "beak", (1.4, 1.2, 4.6), (0, 9.0, -9.8))
    b.skin("beak", lambda cv: cv.noise(HIDE, 1.6, 0.4))
    b.box("head", "crest", (1.0, 2.2, 5), (0, 11.8, -3.4))     # crista longa para trás
    b.skin("crest", lambda cv: cv.noise(CREST, 2.2, 0.5))
    for side, sx in (("l", 1), ("r", -1)):
        w = f"wing_{side}"
        b.bone(w, "body", origin=(sx * 2.0, 9.6, -0.5))
        x0 = 2.0 if sx > 0 else -2.0 - sp
        b.box(w, w, (sp, 0.5, 7.0), (x0, 9.6, -3.5), anchor="corner")
        b.skin(w, lambda cv: cv.noise(MEMB, 2.2, 0.6))
        arm = f"arm_{side}"
        b.box(w, arm, (sp - 0.6, 0.9, 1.0), (x0 + 0.3, 9.5, -3.9), anchor="corner")
        b.skin(arm, lambda cv: cv.noise(HIDE, 1.8, 0.5))
        l = f"leg_{side}"
        b.bone(l, "body", origin=(sx * 1.1, 6, 2))
        b.box(l, l, (0.8, 2.4, 0.8), (sx * 1.1, 4.8, 2))
        b.skin(l, lambda cv: cv.noise(HIDE, 1.4, 0.4))
    fly = b.anim("fly", 1.3, "loop")
    glide = b.anim("glide", 3.0, "loop")
    for side, sx in (("l", 1), ("r", -1)):
        fly.wave(f"wing_{side}", "rot", 2, sx * 32, steps=10)
        glide.wave(f"wing_{side}", "rot", 2, sx * 4, steps=8)
    fly.wave("root", "pos", 1, 0.7, steps=10).wave("tail", "rot", 0, 6, steps=10)
    glide.wave("root", "pos", 1, 0.2, steps=8)
    scream = b.anim("scream", 0.9, "once")
    scream.key("head", "rot", 0, (0, 0, 0)).key("head", "rot", 0.2, (-35, 0, 0)).key("head", "rot", 0.8, (-35, 0, 0)).key("head", "rot", 0.9, (0, 0, 0))
    return b


if __name__ == "__main__":
    make("cave_glider", build, P, {}, anim="glide")
