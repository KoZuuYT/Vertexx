"""Design de modelos em pixel art: geometria declarada por peças e texturas pintadas face a face.

Substitui a "projeção da referência na textura" (que deixava faces borradas, rostos sumindo e lados
inventados) por pintura determinística no estilo das texturas do jogo: rampas de cor, ruído agrupado,
gradientes pontilhados, listras, sprites desenhados à mão (olhos, bocas, emblemas) e bordas. As cores
vêm da análise das referências (``multiref``) e as medidas podem ser conciliadas entre várias imagens.

Convenção das faces (``Canvas``): cada face é vista de fora, ``i`` da esquerda para a direita e ``j`` de
cima para baixo. Nas faces laterais "cima" é +Y. ``up`` é vista de cima com a frente do modelo no topo da
tela (``i=0`` do lado esquerdo do próprio mob); ``down`` usa o mesmo eixo ``i`` mas ``j=0`` é a traseira.

``Designer.mirror`` duplica um osso (com filhos) espelhado em X e copia a textura espelhada, garantindo
pares (braços, pernas, olhos) idênticos dos dois lados.
"""
from __future__ import annotations

import colorsys
import hashlib
import math

import numpy as np

from .build import Builder
from .model import Bone, Cube, Face

Color = tuple


def rgb(hexstr: str) -> Color:
    h = hexstr.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c: Color, k: float) -> Color:
    """Escurece (k<0) ou clareia (k>0) mantendo o matiz, como as rampas das texturas do jogo."""
    r, g, b = (v / 255 for v in c[:3])
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    l = min(1.0, max(0.0, l + k * (1 - l if k > 0 else l)))
    # escurecer puxa levemente o matiz para o frio e satura um pouco (sombra "pintada")
    if k < 0:
        s = min(1.0, s * (1 + 0.25 * -k))
    r, g, b = colorsys.hls_to_rgb(h, l, s)
    return (round(r * 255), round(g * 255), round(b * 255))


def mix(a: Color, b: Color, t: float) -> Color:
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Ramp:
    """Rampa de cores do mais escuro para o mais claro."""

    def __init__(self, colors):
        self.c = [tuple(c[:3]) for c in colors]

    @classmethod
    def of(cls, base: Color, n=5, spread=0.42):
        ks = np.linspace(-spread, spread * 0.8, n)
        return cls([shade(base, float(k)) for k in ks])

    def __len__(self):
        return len(self.c)

    def __getitem__(self, i):
        return self.c[max(0, min(len(self.c) - 1, int(i)))]

    def at(self, t: float) -> Color:
        return self[round(t * (len(self.c) - 1))]


def _seed(*parts) -> int:
    return int.from_bytes(hashlib.blake2b("|".join(map(str, parts)).encode(), digest_size=8).digest(), "little")


class Canvas:
    """Uma face de um cubo dentro do atlas, para pintar texel a texel."""

    def __init__(self, atlas: np.ndarray, rect, name: str, face: str):
        x, y, w, h = rect
        self.px = atlas[y:y + h, x:x + w]
        self.w, self.h = w, h
        self.face = face
        self.rng = np.random.default_rng(_seed(name, face))

    # básicos
    def set(self, i, j, c):
        if 0 <= i < self.w and 0 <= j < self.h and c is not None:
            self.px[j, i] = (*c[:3], c[3] if len(c) > 3 else 255)
        return self

    def get(self, i, j):
        return tuple(int(v) for v in self.px[j, i])

    def fill(self, c):
        self.px[:, :] = (*c[:3], 255)
        return self

    def clear(self, i0=0, j0=0, w=None, h=None):
        w = self.w if w is None else w
        h = self.h if h is None else h
        self.px[max(0, j0):j0 + h, max(0, i0):i0 + w] = 0
        return self

    def rect(self, i0, j0, w, h, c):
        for j in range(j0, j0 + h):
            for i in range(i0, i0 + w):
                self.set(i, j, c)
        return self

    def hline(self, j, c, i0=0, i1=None):
        return self.rect(i0, j, (self.w if i1 is None else i1) - i0, 1, c)

    def vline(self, i, c, j0=0, j1=None):
        return self.rect(i, j0, 1, (self.h if j1 is None else j1) - j0, c)

    def border(self, c, sides="tblr"):
        if "t" in sides:
            self.hline(0, c)
        if "b" in sides:
            self.hline(self.h - 1, c)
        if "l" in sides:
            self.vline(0, c)
        if "r" in sides:
            self.vline(self.w - 1, c)
        return self

    # texturas
    def noise(self, ramp: Ramp, base: float | None = None, amount=0.45, cluster=0.55, lo=0, hi=None):
        """Ruído agrupado em torno do tom ``base`` da rampa (texturas de pedra, lã, terra...)."""
        hi = len(ramp) - 1 if hi is None else hi
        base = (len(ramp) - 1) / 2 if base is None else base
        field = self.rng.normal(0, 1, (self.h + 2, self.w + 2))
        # suaviza com os vizinhos: manchas de 2-3 texels em vez de pontos soltos
        sm = (field[1:-1, 1:-1] + cluster * (field[:-2, 1:-1] + field[2:, 1:-1] + field[1:-1, :-2] + field[1:-1, 2:]) / 4)
        sm = sm / (sm.std() + 1e-6)
        for j in range(self.h):
            for i in range(self.w):
                v = base + sm[j, i] * amount * 1.6
                self.set(i, j, ramp[int(round(min(hi, max(lo, v))))])
        return self

    def speckle(self, c, p=0.08, mask=None):
        for j in range(self.h):
            for i in range(self.w):
                if self.rng.random() < p and (mask is None or mask(i, j)):
                    self.set(i, j, c)
        return self

    def vgrad(self, ramp: Ramp, top: float, bottom: float, dither=0.6, j0=0, j1=None, wobble=0.0):
        """Gradiente vertical pontilhado (fogo, pelagem que escurece para baixo)."""
        j1 = self.h if j1 is None else j1
        n = max(1, j1 - j0 - 1)
        for i in range(self.w):
            off = self.rng.normal(0, wobble) if wobble else 0.0
            for j in range(j0, j1):
                t = (j - j0) / n
                v = top + (bottom - top) * t + off + self.rng.uniform(-dither, dither)
                self.set(i, j, ramp[round(min(len(ramp) - 1, max(0, v)))])
        return self

    def flames(self, ramp: Ramp, tongues=0.5, hot_top=True):
        """Gradiente de fogo com "línguas" verticais (colunas que sobem mais que as vizinhas)."""
        n = len(ramp) - 1
        heights = self.rng.uniform(-tongues, tongues, self.w) * self.h * 0.35
        heights = np.convolve(heights, [0.25, 0.5, 0.25], mode="same")
        for i in range(self.w):
            for j in range(self.h):
                t = (j + heights[i]) / max(1, self.h - 1)
                t = min(1, max(0, t))
                v = (n * (1 - t)) if hot_top else n * t
                v += self.rng.uniform(-0.55, 0.55)
                self.set(i, j, ramp[round(min(n, max(0, v)))])
        return self

    def stripes(self, ramp: Ramp, every=3, vertical=True, offset=0, depth=1.0):
        for j in range(self.h):
            for i in range(self.w):
                k = (i if vertical else j) + offset
                if k % every == 0:
                    c = self.get(i, j)
                    self.set(i, j, shade(c, -0.18 * depth))
        return self

    def drips(self, c, from_top=0, mn=1, mx=4, density=0.7, edge=None):
        """Pingos/franjas descendo a partir de ``from_top`` (musgo, lama, gelo)."""
        for i in range(self.w):
            if self.rng.random() < density:
                n = int(self.rng.integers(mn, mx + 1))
                for j in range(from_top, min(self.h, from_top + n)):
                    self.set(i, j, c)
                if edge is not None and from_top + n < self.h:
                    self.set(i, from_top + n, edge)
        return self

    def fringe(self, i0, i1, j, depth_fn, c, below=None):
        """Franja irregular: em cada coluna, pinta ``depth_fn(i)`` texels a partir de ``j``."""
        for i in range(i0, i1):
            d = depth_fn(i)
            for k in range(d):
                self.set(i, j + k, c(i, k) if callable(c) else c)
            if below is not None:
                self.set(i, j + d, below)
        return self

    def sprite(self, rows, legend: dict, i0=0, j0=0, flip=False):
        """Desenha linhas de texto: cada caractere é uma cor da legenda ('.' ou espaço = não pinta)."""
        for j, row in enumerate(rows):
            if flip:
                row = row[::-1]
            for i, ch in enumerate(row):
                if ch in ".  " or ch not in legend:
                    continue
                self.set(i0 + i, j0 + j, legend[ch])
        return self

    def mirror_x(self):
        """Espelha a metade esquerda sobre a direita (rostos simétricos)."""
        half = self.w // 2
        self.px[:, self.w - half:] = self.px[:, :half][:, ::-1]
        return self

    def darken_edges(self, k=-0.12, sides="lrtb"):
        for i in range(self.w):
            for j in range(self.h):
                edge = (("l" in sides and i == 0) or ("r" in sides and i == self.w - 1)
                        or ("t" in sides and j == 0) or ("b" in sides and j == self.h - 1))
                if edge and self.px[j, i, 3] > 0:
                    self.set(i, j, shade(self.get(i, j), k))
        return self


SIDES = ("north", "south", "east", "west")
ALL = ("north", "south", "east", "west", "up", "down")


class Designer(Builder):
    """Builder com pintura por face, espelhamento de ossos e peças com tamanho em vez de cantos."""

    def __init__(self, name, res=(128, 128), **kw):
        super().__init__(name, res=res, **kw)
        self.name = name
        self.pairs: list[tuple[str, str]] = []

    # geometria
    def box(self, bone, name, size, at, inflate=0.0, faces="all", anchor="bottom", rot=None, origin=None) -> Cube:
        """Cubo de ``size`` (w,h,d) posicionado por ``at``: base-centro (``anchor='bottom'``), centro ou canto."""
        w, h, d = size
        x, y, z = at
        if anchor == "bottom":
            frm, to = (x - w / 2, y, z - d / 2), (x + w / 2, y + h, z + d / 2)
        elif anchor == "center":
            frm, to = (x - w / 2, y - h / 2, z - d / 2), (x + w / 2, y + h / 2, z + d / 2)
        elif anchor == "top":
            frm, to = (x - w / 2, y - h, z - d / 2), (x + w / 2, y, z + d / 2)
        else:   # canto
            frm, to = (x, y, z), (x + w, y + h, z + d)
        return self.cube(bone, name, frm, to, rot=rot, origin=origin, inflate=inflate, faces=faces)

    def canvas(self, cube, face) -> Canvas:
        c = self.cubes[cube] if isinstance(cube, str) else cube
        return Canvas(self.atlas.px, self.layout[c.uuid][face], f"{self.name}:{c.name}", face)

    def skin(self, cube, painter, faces=ALL):
        """``painter(canvas)`` em cada face existente do cubo (``canvas.face`` diz qual é)."""
        c = self.cubes[cube] if isinstance(cube, str) else cube
        for f in faces:
            if f in c.faces:
                painter(self.canvas(c, f))
        return self

    def drop_face(self, cube, *faces):
        c = self.cubes[cube] if isinstance(cube, str) else cube
        for f in faces:
            c.faces.pop(f, None)

    # simetria
    def mirror(self, src: str, dst: str, rename=None):
        """Cria ``dst`` espelhando ``src`` (e filhos) em X, com a textura espelhada. ``rename(nome)`` renomeia filhos."""
        rename = rename or (lambda n: n.replace("_r", "_l") if n.endswith("_r") or "_r_" in n else
                            n.replace("_l", "_r") if n.endswith("_l") or "_l_" in n else n + "_m")
        sb = self.bones[src]
        parent = self._parent_of(sb)

        def mirror_bone(b: Bone, parent_name, name):
            nb = self.bone(name, parent_name, origin=(-b.origin[0], b.origin[1], b.origin[2]),
                           rot=(b.rot[0], -b.rot[1], -b.rot[2]), glow=b.glow)
            for c in b.cubes:
                cname = name + c.name[len(b.name):] if c.name.startswith(b.name) else rename(c.name)
                if cname in self.cubes:
                    cname += "_m"
                nc = self.cube(nb, cname, (-c.to[0], c.frm[1], c.frm[2]), (-c.frm[0], c.to[1], c.to[2]),
                               rot=(c.rot[0], -c.rot[1], -c.rot[2]), origin=(-c.origin[0], c.origin[1], c.origin[2]),
                               inflate=c.inflate, faces=[{"east": "west", "west": "east"}.get(f, f) for f in c.faces])
                # copia texels espelhados: leste<->oeste trocados, todas as faces invertidas na horizontal
                nc.faces = {}
                for f, face in c.faces.items():
                    tf = {"east": "west", "west": "east"}.get(f, f)
                    sx, sy, sw, sh = self.layout[c.uuid][f]
                    dx, dy, dw, dh = self.layout[nc.uuid][tf]
                    block = self.atlas.px[sy:sy + sh, sx:sx + sw]
                    self.atlas.px[dy:dy + sh, dx:dx + sw] = block[:, ::-1]
                    nc.faces[tf] = Face([dx, dy, dx + sw, dy + sh], face.tex, face.rot)
            for loc in b.locators:
                self.bones[name].locators.append(type(loc)(rename(loc.name), [-loc.pos[0], loc.pos[1], loc.pos[2]]))
            for ch in b.children:
                mirror_bone(ch, name, rename(ch.name))

        mirror_bone(sb, parent, dst)
        self.pairs.append((src, dst))
        return self.bones[dst]

    def _parent_of(self, bone: Bone):
        for name, b in self.bones.items():
            if bone in b.children:
                return name
        return None
