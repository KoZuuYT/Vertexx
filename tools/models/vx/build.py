"""Construtor de modelos em Python: monta ossos/cubos/animações, aloca UV numa textura e pinta texels.

É a forma de "escrever um bbmodel por código": o resultado é salvo com ``save_bbmodel`` e abre
normalmente no Blockbench (cubos com UV explícito, textura embutida, animações por osso).
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image

from .model import Animation, Bone, Cube, Face, Keyframe, Locator, Model, Texture, png_bytes


class Atlas:
    """Textura única com alocação em prateleiras. Cresce em altura (potência de 2) quando enche.

    Cada ilha de UV ganha ``pad`` texels de margem; ``bleed`` preenche essas margens com a cor da borda da face
    vizinha, então mipmaps e filtragem no jogo nunca puxam texels de outra face (linhas escuras nas dobras)."""

    def __init__(self, w: int, h: int, pad: int = 1):
        self.w, self.h = w, h
        self.pad = pad
        self.px = np.zeros((h, w, 4), dtype=np.uint8)
        self.used = np.zeros((h, w), dtype=bool)
        self._x = self._y = self._row = 0

    def _grow(self):
        self.px = np.concatenate([self.px, np.zeros_like(self.px)], axis=0)
        self.used = np.concatenate([self.used, np.zeros_like(self.used)], axis=0)
        self.h *= 2

    def alloc(self, rw: int, rh: int):
        p = self.pad
        if rw + 2 * p > self.w:
            raise ValueError(f"face {rw}x{rh} mais larga que o atlas ({self.w})")
        if self._x + rw + 2 * p > self.w:
            self._x, self._y, self._row = 0, self._y + self._row, 0
        while self._y + rh + 2 * p > self.h:
            self._grow()
        pos = (self._x + p, self._y + p)
        self._x += rw + 2 * p
        self._row = max(self._row, rh + 2 * p)
        self.used[pos[1]:pos[1] + rh, pos[0]:pos[0] + rw] = True
        return pos

    def bleed(self):
        """Estende a cor das bordas de cada ilha para a margem (só fora das faces; recortes continuam vazios)."""
        px = self.px
        filled = self.used & (px[..., 3] > 0)
        for _ in range(self.pad):
            src = px.copy()
            got = filled.copy()
            for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                sh = np.roll(filled, (dy, dx), axis=(0, 1))
                shc = np.roll(src, (dy, dx), axis=(0, 1))
                take = sh & ~got & ~self.used
                px[take] = shc[take]
                got |= take
            filled = got

    def image(self) -> Image.Image:
        return Image.fromarray(self.px, "RGBA")


def _faces_layout(w, h, d):
    """Retângulos de cada face dentro do bloco "cruz" (0,0) de tamanho (2d+2w, d+h)."""
    return {
        "up": (d, 0, w, d), "down": (d + w, 0, w, d),
        "east": (0, d, d, h), "north": (d, d, w, h), "west": (d + w, d, d, h), "south": (2 * d + w, d, w, h),
    }


class Builder:
    def __init__(self, name: str, res=(64, 64), facing="north", fmt="modded_entity"):
        self.model = Model(name=name, res=tuple(res), fmt=fmt, facing=facing)
        self.atlas = Atlas(*res)
        self.bones: dict[str, Bone] = {}
        self.cubes: dict[str, Cube] = {}
        self.layout: dict[str, dict] = {}      # cubo -> {face: (x, y, w, h)} em texels
        self._pending: list[tuple[Cube, int, int]] = []

    # ── estrutura ───────────────────────────────────────────
    def bone(self, name, parent=None, origin=(0, 0, 0), rot=(0, 0, 0), glow=False) -> Bone:
        b = Bone(name, [float(v) for v in origin], [float(v) for v in rot], glow=glow or name.endswith("_glow"))
        if parent is None:
            self.model.roots.append(b)
        else:
            self.bones[parent].children.append(b)
        self.bones[name] = b
        return b

    def cube(self, bone, name, frm, to, rot=None, origin=None, inflate=0.0, faces="all") -> Cube:
        b = self.bones[bone] if isinstance(bone, str) else bone
        frm = [float(v) for v in frm]
        to = [float(v) for v in to]
        lo = [min(frm[i], to[i]) for i in range(3)]
        hi = [max(frm[i], to[i]) for i in range(3)]
        c = Cube(name, lo, hi, [float(v) for v in (origin if origin is not None else b.origin)],
                 [float(v) for v in (rot or (0, 0, 0))], float(inflate))
        w, h, d = (max(1, math.ceil(hi[0] - lo[0] - 1e-6)), max(1, math.ceil(hi[1] - lo[1] - 1e-6)),
                   max(1, math.ceil(hi[2] - lo[2] - 1e-6)))
        lay = _faces_layout(w, h, d)
        self.layout[c.uuid] = {}
        for f, r in lay.items():
            if faces == "all" or f in faces:
                fx, fy = self.atlas.alloc(r[2], r[3])
                self.layout[c.uuid][f] = (fx, fy, r[2], r[3])
                c.faces[f] = Face([fx, fy, fx + r[2], fy + r[3]])
        b.cubes.append(c)
        self.cubes[name] = c
        return c

    def locator(self, bone, name, pos):
        self.bones[bone].locators.append(Locator(name, [float(v) for v in pos]))

    # ── pintura ─────────────────────────────────────────────
    def rect(self, cube: Cube | str, face: str):
        c = self.cubes[cube] if isinstance(cube, str) else cube
        return self.layout[c.uuid][face]

    def fill(self, cube, color, faces=None):
        c = self.cubes[cube] if isinstance(cube, str) else cube
        color = tuple(color) if len(color) == 4 else (*color, 255)
        for f in (faces or self.layout[c.uuid].keys()):
            x, y, w, h = self.layout[c.uuid][f]
            self.atlas.px[y:y + h, x:x + w] = color

    def paint(self, cube, face, fn):
        """fn(i, j, w, h) -> (r,g,b[,a]) | None, com (i,j) = texel da face (esquerda→direita, cima→baixo)."""
        x, y, w, h = self.rect(cube, face)
        for j in range(h):
            for i in range(w):
                col = fn(i, j, w, h)
                if col is not None:
                    self.atlas.px[y + j, x + i] = col if len(col) == 4 else (*col, 255)

    def texel(self, cube, face, i, j, color):
        x, y, w, h = self.rect(cube, face)
        if 0 <= i < w and 0 <= j < h:
            self.atlas.px[y + j, x + i] = color if len(color) == 4 else (*color, 255)

    # ── animação ────────────────────────────────────────────
    def anim(self, name, length, loop="loop") -> "AnimBuilder":
        a = Animation(name, float(length), loop)
        self.model.anims.append(a)
        return AnimBuilder(a)

    def finish(self) -> Model:
        self.atlas.bleed()
        self.model.res = (self.atlas.w, self.atlas.h)
        self.model.textures = [Texture(self.model.name, png_bytes(self.atlas.image()), *self.model.res)]
        # cubos sem face pintada continuam válidos; faces fora de "faces" simplesmente não existem
        return self.model


class AnimBuilder:
    def __init__(self, anim: Animation):
        self.anim = anim

    def key(self, bone, channel, time, value, interp="linear"):
        ch = {"rot": "rotation", "pos": "position", "scl": "scale", "rotation": "rotation",
              "position": "position", "scale": "scale"}[channel]
        v = list(value) if isinstance(value, (list, tuple)) else [value, value, value]
        self.anim.tracks.setdefault(f"{bone}|{ch}", []).append(Keyframe(ch, float(time), [v], interp))
        self.anim.tracks[f"{bone}|{ch}"].sort(key=lambda k: k.time)
        return self

    def orbit(self, bone, channel, amps, phases=(0.0, 0.0, 0.0), steps=8, base=(0, 0, 0), interp="catmullrom"):
        """Oscilação em vários eixos ao mesmo tempo (um único conjunto de keyframes por canal)."""
        for i in range(steps + 1):
            t = self.anim.length * i / steps
            v = [base[a] + amps[a] * math.sin(2 * math.pi * i / steps + phases[a]) for a in range(3)]
            self.key(bone, channel, t, v, interp)
        return self

    def wave(self, bone, channel, axis, amp, phase=0.0, steps=8, base=(0, 0, 0), interp="catmullrom"):
        """Oscilação senoidal fechada (loop perfeito) em um eixo: ``steps`` keyframes por ciclo."""
        n = steps
        for i in range(n + 1):
            t = self.anim.length * i / n
            v = list(base)
            v[axis] += amp * math.sin(2 * math.pi * i / n + phase)
            self.key(bone, channel, t, v, interp)
        return self
