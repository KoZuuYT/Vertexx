"""Verificação de qualidade de um modelo antes de ir para o pack.

Pega no próprio arquivo os defeitos que no jogo aparecem como "partes sobrepostas", "piscando" ou
"faltando": faces coplanares (z-fighting), uma casca quase encostada cobrindo outra face (capuz por cima do
rosto), faces totalmente transparentes ou de cor chapada, pares esquerdo/direito incompletos ou diferentes,
ossos sem cubos visíveis e elementos fora do limite do formato de modelos do jogo.
"""
from __future__ import annotations

import numpy as np

from .model import Model, to_game
from .render import pose

AX = {"east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1), "south": (2, 1), "north": (2, -1)}


def _faces_world(gm: Model):
    """Faces (cubo, nome, eixo, sinal, plano, retângulo 2D) no repouso, para cubos sem rotação efetiva."""
    out = []
    for cube, w in pose(gm, None):
        rot = w[:3, :3]
        if not np.allclose(np.abs(rot), np.round(np.abs(rot)), atol=1e-4):
            continue      # cubo girado: fora da checagem de planos alinhados
        lo = (w @ np.array([*np.array(cube.frm) - cube.inflate, 1]))[:3]
        hi = (w @ np.array([*np.array(cube.to) + cube.inflate, 1]))[:3]
        lo, hi = np.minimum(lo, hi), np.maximum(lo, hi)
        for name in cube.faces:
            axis, sign = AX[name]
            # a orientação do cubo pode ter trocado o eixo: recalcula pela matriz
            n = np.zeros(3)
            n[axis] = sign
            nw = rot @ n
            a = int(np.argmax(np.abs(nw)))
            s = 1 if nw[a] > 0 else -1
            plane = hi[a] if s > 0 else lo[a]
            others = [i for i in range(3) if i != a]
            out.append((cube, name, a, s, plane, (lo[others[0]], hi[others[0]], lo[others[1]], hi[others[1]])))
    return out


def _overlap(r1, r2):
    w = min(r1[1], r2[1]) - max(r1[0], r2[0])
    h = min(r1[3], r2[3]) - max(r1[2], r2[2])
    return max(0.0, w) * max(0.0, h)


def _face_alpha(model: Model, cube, face):
    tex = np.asarray(model.textures[cube.faces[face].tex].image())
    u1, v1, u2, v2 = cube.faces[face].uv
    x0, x1 = sorted((int(round(u1)), int(round(u2))))
    y0, y1 = sorted((int(round(v1)), int(round(v2))))
    reg = tex[y0:max(y1, y0 + 1), x0:max(x1, x0 + 1)]
    return reg


def lint(model: Model, near=0.35, verbose=True):
    gm = to_game(model)
    issues = []
    faces = _faces_world(gm)
    for i in range(len(faces)):
        c1, f1, a1, s1, p1, r1 = faces[i]
        for j in range(i + 1, len(faces)):
            c2, f2, a2, s2, p2, r2 = faces[j]
            if a1 != a2 or s1 != s2 or c1 is c2:
                continue
            ov = _overlap(r1, r2)
            if ov < 0.2:
                continue
            gap = abs(p1 - p2)
            if gap < 0.02:
                issues.append(("erro", f"z-fighting: {c1.name}.{f1} e {c2.name}.{f2} no mesmo plano ({ov:.1f} px²)"))
            elif gap < near:
                # a de fora cobre a de dentro: só é problema se a de fora for opaca onde se sobrepõem
                outer = (c1, f1) if (p1 - p2) * s1 > 0 else (c2, f2)
                inner = (c2, f2) if outer[0] is c1 else (c1, f1)
                reg = _face_alpha(model, *outer)
                if reg.size and (reg[..., 3] > 0).mean() > 0.6:
                    issues.append(("aviso", f"casca: {outer[0].name}.{outer[1]} cobre {inner[0].name}.{inner[1]} a {gap:.2f} px "
                                            f"(pisca de longe e esconde a face de dentro)"))
    for b, _ in model.bones():
        for c in b.cubes:
            for f in list(c.faces):
                reg = _face_alpha(model, c, f)
                if reg.size == 0:
                    continue
                if (reg[..., 3] == 0).all():
                    issues.append(("aviso", f"face transparente: {c.name}.{f} (remova a face)"))
                elif reg.shape[0] * reg.shape[1] >= 9:
                    opaque = reg[reg[..., 3] > 0][:, :3]
                    if len(np.unique(opaque, axis=0)) == 1:
                        issues.append(("info", f"face de cor chapada: {c.name}.{f}"))
    names = {b.name for b, _ in model.bones()}
    for n in names:
        for a, z in (("_l", "_r"), ("_r", "_l")):
            if n.endswith(a) and n[: -len(a)] + z not in names:
                issues.append(("erro", f"par incompleto: {n} sem {n[:-len(a)] + z}"))
    for b, _ in model.bones():
        if b.name.endswith("_l"):
            other = model.find(b.name[:-2] + "_r")
            if other and (len(other.cubes) != len(b.cubes)):
                issues.append(("erro", f"par diferente: {b.name} tem {len(b.cubes)} cubos e {other.name} tem {len(other.cubes)}"))
    for a in model.anims:
        for key, frames in a.tracks.items():
            seen = {}
            for f in frames:
                t = round(f.time, 4)
                if t in seen and seen[t] != f.points:
                    issues.append(("erro", f"animação {a.name}: {key} tem dois keyframes diferentes em t={t} (ondas sobrepostas)"))
                    break
                seen[t] = f.points
    for a in model.anims:
        for key in a.tracks:
            bone = key.split("|")[0]
            if model.find(bone) is None:
                issues.append(("erro", f"animação {a.name}: osso inexistente {bone}"))
    if verbose:
        for lvl, msg in issues:
            print(f"  [{lvl}] {msg}")
        if not issues:
            print("  lint: nenhum problema")
    return issues
