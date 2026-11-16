"""Pincéis comuns dos mobs desenhados (rampas medidas nas referências e padrões repetidos)."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from vx.design import ALL, SIDES, Designer, Ramp, mix, rgb, shade  # noqa: E402,F401


def R(*hexes) -> Ramp:
    return Ramp([rgb(h) for h in hexes])


def noisy(ramp: Ramp, base=None, amount=0.45, top=0.0, bottom=0.0):
    """Ruído com a face de cima um tom mais clara e a de baixo mais escura (luz "pintada" leve)."""
    def paint(cv):
        b = (len(ramp) - 1) / 2 if base is None else base
        if cv.face == "up":
            b += top
        elif cv.face == "down":
            b += bottom
        cv.noise(ramp, b, amount)
    return paint


def streaky(ramp: Ramp, base=None, amount=0.5):
    """Ruído alongado na vertical (musgo, pelo, chamas, madeira)."""
    def paint(cv):
        b = (len(ramp) - 1) / 2 if base is None else base
        cv.noise(ramp, b, amount)
        if cv.face not in ("up", "down"):
            for i in range(cv.w):
                run = cv.rng.integers(1, 4)
                for j in range(cv.h):
                    if cv.rng.random() < 0.35 and j + 1 < cv.h:
                        cv.set(i, j + 1, cv.get(i, j))
                    run -= 1
    return paint


def eye(cv, i, j, white, pupil, w=2, h=1, pupil_side="in", mirror=True):
    """Olho simples: ``w`` x ``h`` de branco com a pupila de 1 texel no lado ``in`` (para o nariz) ou ``out``."""
    cx = cv.w - i - w
    for (x0, side) in ((i, 1), (cx, -1)) if mirror else ((i, 1),):
        cv.rect(x0, j, w, h, white)
        if pupil is not None:
            px = x0 + w - 1 if (pupil_side == "in") == (side == 1) else x0
            cv.rect(px, j, 1, h, pupil)
