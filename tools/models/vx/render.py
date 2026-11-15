"""Renderizador de referência (traçado de raios ortográfico, numpy) do modelo já no espaço do jogo.

Serve a três coisas: (1) prévia de modelos e animações sem abrir o jogo; (2) comparação quadro a quadro
com as imagens de referência; (3) projeção das referências sobre as faces para pintar as texturas
(``refscan.py``). As convenções de UV/face são as do JSON de modelos do jogo (ver ``model.py``).
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np
from PIL import Image

from .model import FACES, Model, to_game

FACE_IDX = {n: i for i, n in enumerate(FACES)}
SHADE_DEFAULT = {"up": 1.0, "down": 0.5, "ns": 0.8, "ew": 0.6}


# ───────────────────────────── matrizes ─────────────────────────────

def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def S(x, y, z):
    return np.diag([x, y, z, 1.0])


def euler(rx, ry, rz):
    """Rz·Ry·Rx, graus (ordem do Blockbench)."""
    a, b, c = math.radians(rx), math.radians(ry), math.radians(rz)
    ca, sa, cb, sb, cc, sc = math.cos(a), math.sin(a), math.cos(b), math.sin(b), math.cos(c), math.sin(c)
    rxm = np.array([[1, 0, 0], [0, ca, -sa], [0, sa, ca]])
    rym = np.array([[cb, 0, sb], [0, 1, 0], [-sb, 0, cb]])
    rzm = np.array([[cc, -sc, 0], [sc, cc, 0], [0, 0, 1]])
    m = np.eye(4)
    m[:3, :3] = rzm @ rym @ rxm
    return m


# ───────────────────────────── pose ─────────────────────────────

def bone_values(model: Model, frames: dict | None, frame: float, overrides: dict | None = None):
    """Valores animados por osso no quadro (float, interpolado) de uma animação já "baked"."""
    out: dict[str, dict] = {}
    if frames:
        n = frames["frames"]
        loop = frames["loop"] == "loop"
        f = frame % n if loop else min(max(frame, 0), n - 1)
        i0 = int(math.floor(f))
        i1 = (i0 + 1) % n if loop else min(i0 + 1, n - 1)
        u = f - i0
        for bone, ch in frames["bones"].items():
            vals = {}
            for key, table in ch.items():
                a, b = np.array(table[i0]), np.array(table[i1])
                vals[key] = a + (b - a) * u
            out[bone] = vals
    for bone, vals in (overrides or {}).items():
        out.setdefault(bone, {}).update(vals)
    return out


def pose(model: Model, values: dict | None = None):
    """Lista de (cubo, matriz 4x4 local→mundo) para o modelo no espaço do jogo."""
    values = values or {}
    result = []

    def walk(bone, parent_w):
        v = values.get(bone.name, {})
        rot = np.array(bone.rot) + np.array(v.get("rot", (0, 0, 0)))
        pos = np.array(v.get("pos", (0, 0, 0)))
        scl = np.array(v.get("scl", (1, 1, 1)))
        c = bone.origin
        w = parent_w @ T(*pos) @ T(*c) @ euler(*rot) @ S(*scl) @ T(-c[0], -c[1], -c[2])
        for cube in bone.cubes:
            o = cube.origin
            cw = w @ T(*o) @ euler(*cube.rot) @ T(-o[0], -o[1], -o[2])
            result.append((cube, cw))
        for child in bone.children:
            walk(child, w)

    for r in model.roots:
        walk(r, np.eye(4))
    return result


# ───────────────────────────── câmera ─────────────────────────────

@dataclass
class Camera:
    yaw: float = 30.0          # graus; 0 = câmera à frente do modelo (em +Z), positivo gira para o lado +X
    pitch: float = 25.0        # graus acima do horizonte
    scale: float = 4.0         # pixels da imagem por pixel do modelo
    cx: float = 0.0            # posição na imagem do ponto "center"
    cy: float = 0.0
    center: tuple = (0.0, 12.0, 0.0)
    width: int = 256
    height: int = 256

    def basis(self):
        th, ph = math.radians(self.yaw), math.radians(self.pitch)
        cam_dir = np.array([math.sin(th) * math.cos(ph), math.sin(ph), math.cos(th) * math.cos(ph)])
        d = -cam_dir
        r = np.cross(d, [0, 1, 0])
        r /= np.linalg.norm(r)
        u = np.cross(r, d)
        return d, r, u

    def rays(self, step: int = 1):
        d, r, u = self.basis()
        xs = (np.arange(0, self.width, step) + 0.5)
        ys = (np.arange(0, self.height, step) + 0.5)
        gx, gy = np.meshgrid(xs, ys)
        off_r = (gx - self.cx) / self.scale
        off_u = -(gy - self.cy) / self.scale
        origin = (np.array(self.center)[None, None, :] + r[None, None, :] * off_r[..., None]
                  + u[None, None, :] * off_u[..., None] - d[None, None, :] * 2000.0)
        return origin.reshape(-1, 3), d, gx.shape

    def project(self, pts):
        d, r, u = self.basis()
        rel = np.asarray(pts, dtype=float) - np.array(self.center)
        x = self.cx + (rel @ r) * self.scale
        y = self.cy - (rel @ u) * self.scale
        return np.stack([x, y], axis=-1)


# ───────────────────────────── renderização ─────────────────────────────

@dataclass
class Hits:
    rgba: np.ndarray            # (h, w, 4) uint8 já sombreado
    cube: np.ndarray            # índice do cubo (-1 = nada)
    face: np.ndarray            # índice em FACES
    su: np.ndarray              # coordenadas na região de textura (0..1), já com a rotação da face
    sv: np.ndarray
    depth: np.ndarray
    shade: np.ndarray
    items: list                 # [(cubo, matriz)] alinhado com ``cube``


def _tex_arrays(model: Model):
    return [np.asarray(t.image()) for t in model.textures]


def render(model: Model, values: dict | None = None, cam: Camera | None = None, step: int = 1,
           shade_k: dict | None = None, already_game: bool = False, background=(0, 0, 0, 0),
           textures: list | None = None) -> Hits:
    gm = model if already_game else to_game(model)
    items = pose(gm, values)
    cam = cam or Camera()
    k = {**SHADE_DEFAULT, **(shade_k or {})}
    textures = textures if textures is not None else _tex_arrays(gm)
    origin, d, shape = cam.rays(step)
    n = origin.shape[0]
    best_t = np.full(n, np.inf)
    best_cube = np.full(n, -1, dtype=np.int32)
    best_face = np.zeros(n, dtype=np.int8)
    best_su = np.zeros(n)
    best_sv = np.zeros(n)
    best_shade = np.ones(n)
    rgba = np.zeros((n, 4), dtype=np.float64)
    rgba[:] = background
    dvec = np.asarray(d, dtype=float)
    for idx, (cube, w) in enumerate(items):
        if not cube.faces:
            continue
        inv = np.linalg.inv(w)
        o = origin @ inv[:3, :3].T + inv[:3, 3]
        dl = inv[:3, :3] @ dvec
        lo = np.array(cube.frm) - cube.inflate
        hi = np.array(cube.to) + cube.inflate
        with np.errstate(divide="ignore", invalid="ignore"):
            inv_d = 1.0 / np.where(np.abs(dl) < 1e-12, 1e-12, dl)
            t1 = (lo[None, :] - o) * inv_d[None, :]
            t2 = (hi[None, :] - o) * inv_d[None, :]
        tmin = np.minimum(t1, t2)
        tmax = np.maximum(t1, t2)
        t_near = tmin.max(axis=1)
        t_far = tmax.min(axis=1)
        axis = tmin.argmax(axis=1)
        hit = (t_near <= t_far) & (t_far > 0) & (t_near < best_t)
        if not hit.any():
            continue
        hi_idx = np.nonzero(hit)[0]
        p = o[hi_idx] + t_near[hi_idx, None] * dl[None, :]
        ax = axis[hi_idx]
        sign_pos = dl[ax] < 0          # raio vai para -eixo => atinge a face +eixo
        # nome da face
        face_name = np.empty(len(hi_idx), dtype=object)
        face_i = np.zeros(len(hi_idx), dtype=np.int8)
        for a, (neg, pos) in enumerate((("west", "east"), ("down", "up"), ("north", "south"))):
            m = ax == a
            face_name[m & sign_pos] = pos
            face_name[m & ~sign_pos] = neg
        size = hi - lo
        fu = np.zeros(len(hi_idx))
        fv = np.zeros(len(hi_idx))
        px, py, pz = p[:, 0], p[:, 1], p[:, 2]
        nx = (px - lo[0]) / size[0] if size[0] else px * 0
        ny = (py - lo[1]) / size[1] if size[1] else py * 0
        nz = (pz - lo[2]) / size[2] if size[2] else pz * 0
        for name in FACES:
            m = face_name == name
            if not m.any():
                continue
            if name == "north":
                fu[m], fv[m] = 1 - nx[m], 1 - ny[m]
            elif name == "south":
                fu[m], fv[m] = nx[m], 1 - ny[m]
            elif name == "east":
                fu[m], fv[m] = 1 - nz[m], 1 - ny[m]
            elif name == "west":
                fu[m], fv[m] = nz[m], 1 - ny[m]
            elif name == "up":
                fu[m], fv[m] = nx[m], nz[m]
            else:
                fu[m], fv[m] = nx[m], 1 - nz[m]
            face_i[m] = FACE_IDX[name]
        fu = np.clip(fu, 0, 0.99999)
        fv = np.clip(fv, 0, 0.99999)
        ok = np.ones(len(hi_idx), dtype=bool)
        colors = np.zeros((len(hi_idx), 4))
        su_all = np.zeros(len(hi_idx))
        sv_all = np.zeros(len(hi_idx))
        shade_all = np.ones(len(hi_idx))
        lin = w[:3, :3]
        for name in FACES:
            m = face_name == name
            if not m.any():
                continue
            face = cube.faces.get(name)
            if face is None:
                ok[m] = False
                continue
            rot = face.rot % 360
            u_, v_ = fu[m], fv[m]
            if rot == 90:
                su, sv = v_, 1 - u_
            elif rot == 180:
                su, sv = 1 - u_, 1 - v_
            elif rot == 270:
                su, sv = 1 - v_, u_
            else:
                su, sv = u_, v_
            su_all[m], sv_all[m] = su, sv
            tex = textures[face.tex] if face.tex < len(textures) else None
            u1, v1, u2, v2 = face.uv
            if tex is not None:
                th, tw = tex.shape[:2]
                px_u = (u1 + su * (u2 - u1)) * tw / model.res[0]
                px_v = (v1 + sv * (v2 - v1)) * th / model.res[1]
                xi = np.clip(px_u.astype(int), 0, tw - 1)
                yi = np.clip(px_v.astype(int), 0, th - 1)
                col = tex[yi, xi].astype(float)
            else:
                col = np.tile(np.array([255.0, 0, 255.0, 255.0]), (m.sum(), 1))
            normal = np.zeros(3)
            normal[{"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}[name]] = (
                1 if name in ("south", "east", "up") else -1)
            wn = lin @ normal
            wn /= (np.linalg.norm(wn) + 1e-9)
            horizontal = 1 - abs(wn[1])
            s = (abs(wn[0]) * k["ew"] + abs(wn[2]) * k["ns"]) / max(1e-6, abs(wn[0]) + abs(wn[2])) * horizontal
            s += abs(wn[1]) * (k["up"] if wn[1] > 0 else k["down"])
            shade_all[m] = s
            colors[m] = col
        ok &= colors[:, 3] > 8
        sel = hi_idx[ok]
        if sel.size == 0:
            continue
        better = t_near[sel] < best_t[sel]
        sel = sel[better]
        mm = np.nonzero(ok)[0][better]
        best_t[sel] = t_near[sel]
        best_cube[sel] = idx
        best_face[sel] = face_i[mm]
        best_su[sel], best_sv[sel] = su_all[mm], sv_all[mm]
        best_shade[sel] = shade_all[mm]
        c = colors[mm].copy()
        c[:, :3] *= shade_all[mm][:, None]
        rgba[sel] = c
    h, w_ = shape
    return Hits(np.clip(rgba, 0, 255).astype(np.uint8).reshape(h, w_, 4), best_cube.reshape(h, w_),
                best_face.reshape(h, w_), best_su.reshape(h, w_), best_sv.reshape(h, w_),
                np.where(np.isfinite(best_t), best_t, 0).reshape(h, w_), best_shade.reshape(h, w_), items)


def fit_camera_to(model: Model, cam: Camera, values=None, margin=0.9) -> Camera:
    """Ajusta escala e centro da câmera para o modelo caber na imagem."""
    gm = to_game(model)
    pts = []
    for cube, w in pose(gm, values):
        lo = np.array(cube.frm) - cube.inflate
        hi = np.array(cube.to) + cube.inflate
        for x in (lo[0], hi[0]):
            for y in (lo[1], hi[1]):
                for z in (lo[2], hi[2]):
                    pts.append((w @ np.array([x, y, z, 1.0]))[:3])
    pts = np.array(pts)
    cam.center = tuple((pts.min(axis=0) + pts.max(axis=0)) / 2)
    cam.cx, cam.cy = cam.width / 2, cam.height / 2
    cam.scale = 1.0
    xy = cam.project(pts)
    ext = max(np.ptp(xy[:, 0]), np.ptp(xy[:, 1]), 1e-6)
    cam.scale = min(cam.width, cam.height) * margin / ext
    return cam


def to_image(hits: Hits, bg=(236, 236, 240)) -> Image.Image:
    img = Image.new("RGBA", (hits.rgba.shape[1], hits.rgba.shape[0]), bg + (255,))
    img.alpha_composite(Image.fromarray(hits.rgba, "RGBA"))
    return img.convert("RGB")


def contact_sheet(model: Model, frames=None, frame_list=(0,), views=((30, 25), (-30, 25), (90, 10), (0, 10)),
                  size=320) -> Image.Image:
    """Várias vistas (e instantes da animação) lado a lado."""
    from PIL import ImageDraw
    sheet = Image.new("RGB", (size * len(views), size * len(frame_list)), (236, 236, 240))
    draw = ImageDraw.Draw(sheet)
    base = fit_camera_to(model, Camera(width=size, height=size), None, 0.82)
    for r, fr in enumerate(frame_list):
        vals = bone_values(model, frames, fr) if frames else None
        for c, (yaw, pitch) in enumerate(views):
            cam = Camera(yaw, pitch, base.scale, size / 2, size / 2, base.center, size, size)
            hits = render(model, vals, cam)
            sheet.paste(to_image(hits), (c * size, r * size))
            draw.text((c * size + 6, r * size + 4), f"yaw {yaw} pitch {pitch} f{fr}", fill=(60, 60, 60))
    return sheet
