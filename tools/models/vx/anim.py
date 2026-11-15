"""Avaliador mínimo de Molang e amostragem (bake) das animações do Blockbench/GeckoLib para quadros a 20 Hz.

Interpolações: linear, catmullrom, bezier e step (como no Blockbench). Valores em Molang
(``Math.sin(query.anim_time * 90) * 10``) são avaliados no instante amostrado. O resultado são
tabelas por osso e canal que o plugin só precisa interpolar linearmente — sem Molang em tempo de jogo.
"""
from __future__ import annotations

import math
import re

from .model import Animation, Keyframe, Model

FPS = 20

# ───────────────────────────── Molang ─────────────────────────────

_TOKEN = re.compile(r"\s*(?:(\d+\.?\d*(?:[eE][+-]?\d+)?)|([A-Za-z_][\w.]*)|(<=|>=|==|!=|&&|\|\||[-+*/()?:,<>!]))")


def _tokens(src: str):
    pos = 0
    out = []
    src = src.strip()
    while pos < len(src):
        m = _TOKEN.match(src, pos)
        if not m:
            raise ValueError(f"Molang inválido: {src!r}")
        pos = m.end()
        if m.group(1) is not None:
            out.append(("n", float(m.group(1))))
        elif m.group(2) is not None:
            out.append(("id", m.group(2).lower()))
        else:
            out.append(("op", m.group(3)))
    return out


_FUNCS = {
    "math.sin": lambda a: math.sin(math.radians(a)),
    "math.cos": lambda a: math.cos(math.radians(a)),
    "math.asin": lambda a: math.degrees(math.asin(max(-1, min(1, a)))),
    "math.acos": lambda a: math.degrees(math.acos(max(-1, min(1, a)))),
    "math.atan": lambda a: math.degrees(math.atan(a)),
    "math.abs": abs, "math.sqrt": lambda a: math.sqrt(max(0, a)), "math.floor": math.floor,
    "math.ceil": math.ceil, "math.round": round, "math.trunc": math.trunc, "math.exp": math.exp,
    "math.ln": lambda a: math.log(a) if a > 0 else 0,
    "math.min": min, "math.max": max, "math.pow": pow, "math.mod": lambda a, b: a % b if b else 0,
    "math.atan2": lambda a, b: math.degrees(math.atan2(a, b)),
    "math.clamp": lambda a, lo, hi: max(lo, min(hi, a)),
    "math.lerp": lambda a, b, t: a + (b - a) * t,
    "math.random": lambda lo=0, hi=1: (lo + hi) / 2,   # determinístico: valor médio
}


class _Parser:
    def __init__(self, tokens, env):
        self.t, self.i, self.env = tokens, 0, env

    def peek(self):
        return self.t[self.i] if self.i < len(self.t) else (None, None)

    def eat(self):
        tok = self.t[self.i]
        self.i += 1
        return tok

    def expr(self):
        c = self.logic()
        if self.peek() == ("op", "?"):
            self.eat()
            a = self.expr()
            if self.peek() == ("op", ":"):
                self.eat()
                b = self.expr()
            else:
                b = 0.0
            return a if c else b
        return c

    def logic(self):
        v = self.compare()
        while self.peek() in (("op", "&&"), ("op", "||")):
            op = self.eat()[1]
            r = self.compare()
            v = float(bool(v) and bool(r)) if op == "&&" else float(bool(v) or bool(r))
        return v

    def compare(self):
        v = self.add()
        while self.peek()[0] == "op" and self.peek()[1] in ("<", ">", "<=", ">=", "==", "!="):
            op = self.eat()[1]
            r = self.add()
            v = float({"<": v < r, ">": v > r, "<=": v <= r, ">=": v >= r, "==": v == r, "!=": v != r}[op])
        return v

    def add(self):
        v = self.mul()
        while self.peek() in (("op", "+"), ("op", "-")):
            op = self.eat()[1]
            r = self.mul()
            v = v + r if op == "+" else v - r
        return v

    def mul(self):
        v = self.unary()
        while self.peek() in (("op", "*"), ("op", "/")):
            op = self.eat()[1]
            r = self.unary()
            v = v * r if op == "*" else (v / r if r else 0.0)
        return v

    def unary(self):
        if self.peek() == ("op", "-"):
            self.eat()
            return -self.unary()
        if self.peek() == ("op", "+"):
            self.eat()
            return self.unary()
        if self.peek() == ("op", "!"):
            self.eat()
            return float(not self.unary())
        return self.atom()

    def atom(self):
        kind, val = self.eat()
        if kind == "n":
            return val
        if kind == "op" and val == "(":
            v = self.expr()
            self.eat()
            return v
        if kind == "id":
            if self.peek() == ("op", "("):
                self.eat()
                args = []
                if self.peek() != ("op", ")"):
                    args.append(self.expr())
                    while self.peek() == ("op", ","):
                        self.eat()
                        args.append(self.expr())
                self.eat()
                fn = _FUNCS.get(val)
                return float(fn(*args)) if fn else 0.0
            if val == "math.pi":
                return math.pi
            return float(self.env.get(val, 0.0))
        return 0.0


def molang(src, **env) -> float:
    """Avalia uma expressão (ou devolve o número). ``env``: anim_time, life_time (segundos)."""
    if isinstance(src, (int, float)):
        return float(src)
    s = str(src).strip().rstrip(";")
    if "return" in s:
        s = s.split("return", 1)[1]
    if not s:
        return 0.0
    try:
        return float(s)
    except ValueError:
        pass
    scope = {"query.anim_time": env.get("anim_time", 0.0), "query.life_time": env.get("anim_time", 0.0),
             "time": env.get("anim_time", 0.0), "query.delta_time": 0.05}
    return _Parser(_tokens(s), scope).expr()


# ───────────────────────────── amostragem ─────────────────────────────

def _bezier(t, a: Keyframe, b: Keyframe, axis: int, va: float, vb: float) -> float:
    def pts(k: Keyframe, side: str, default_t: float, default_v: float):
        tt = k.bezier.get(f"bezier_{side}_time", [default_t] * 3)[axis]
        vv = k.bezier.get(f"bezier_{side}_value", [default_v] * 3)[axis]
        return tt, vv

    rt, rv = pts(a, "right", 0.1, 0.0)
    lt, lv = pts(b, "left", -0.1, 0.0)
    x0, x1, x2, x3 = a.time, a.time + rt, b.time + lt, b.time
    x1 = min(max(x1, x0), x3)
    x2 = min(max(x2, x0), x3)
    y0, y1, y2, y3 = va, va + rv, vb + lv, vb
    lo, hi = 0.0, 1.0
    for _ in range(30):
        s = (lo + hi) / 2
        m = 1 - s
        x = m ** 3 * x0 + 3 * m * m * s * x1 + 3 * m * s * s * x2 + s ** 3 * x3
        if x < t:
            lo = s
        else:
            hi = s
    s = (lo + hi) / 2
    m = 1 - s
    return m ** 3 * y0 + 3 * m * m * s * y1 + 3 * m * s * s * y2 + s ** 3 * y3


def _catmull(p0, p1, p2, p3, t):
    return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3)


def sample_track(frames: list[Keyframe], t: float, anim: Animation, default: float):
    """Valor [x,y,z] de um canal no tempo t (segundos), ou None sem keyframes."""
    if not frames:
        return None
    env = {"anim_time": t}

    def pre(k: Keyframe, axis):
        return molang(k.points[0][axis], **env)

    def post(k: Keyframe, axis):
        return molang(k.points[-1][axis], **env)

    out = []
    n = len(frames)
    loop = anim.loop == "loop"
    for axis in range(3):
        if t <= frames[0].time:
            out.append(post(frames[0], axis) if t >= frames[0].time else pre(frames[0], axis))
            continue
        if t >= frames[-1].time:
            out.append(post(frames[-1], axis))
            continue
        i = 0
        while i + 1 < n and frames[i + 1].time <= t:
            i += 1
        a, b = frames[i], frames[i + 1]
        va, vb = post(a, axis), pre(b, axis)
        span = b.time - a.time
        u = (t - a.time) / span if span > 0 else 1.0
        if a.interp == "step":
            out.append(va)
        elif a.interp == "catmullrom" or b.interp == "catmullrom":
            def neighbour(idx, edge_value, ref):
                if 0 <= idx < n:
                    return post(frames[idx], axis) if ref == "post" else pre(frames[idx], axis)
                if loop and n > 2:
                    return post(frames[idx % n], axis)
                return edge_value
            p0 = neighbour(i - 1, va, "post")
            p3 = neighbour(i + 2, vb, "pre")
            out.append(_catmull(p0, va, vb, p3, u))
        elif a.interp == "bezier" or b.interp == "bezier":
            out.append(_bezier(t, a, b, axis, va, vb))
        else:
            out.append(va + (vb - va) * u)
    return out


def bake(model: Model, anim: Animation) -> dict:
    """{"length", "loop", "frames", "bones": {osso: {"rot": [[x,y,z]*frames], "pos": ..., "scl": ...}}}"""
    total = max(1, round(anim.length * FPS))
    count = total if anim.loop == "loop" else total + 1
    bones: dict[str, dict] = {}
    names = {b.name for b, _ in model.bones()}
    grouped: dict[str, dict[str, list[Keyframe]]] = {}
    for key, frames in anim.tracks.items():
        bone, channel = key.split("|")
        if bone in names:
            grouped.setdefault(bone, {})[channel] = frames
    for bone, channels in grouped.items():
        entry = {}
        for channel, short, default in (("rotation", "rot", 0.0), ("position", "pos", 0.0), ("scale", "scl", 1.0)):
            frames = channels.get(channel)
            if not frames:
                continue
            table = []
            for i in range(count):
                v = sample_track(frames, i / FPS, anim, default)
                table.append([round(float(x), 4) for x in v])
            entry[short] = table
        if entry:
            bones[bone] = entry
    return {"length": round(anim.length, 4), "loop": anim.loop, "frames": count, "bones": bones}
