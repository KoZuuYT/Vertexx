#!/usr/bin/env python3
"""Análise de referências:  python tools/models/analyze.py models/refs/moobloom.png [...]  (saída em models/analysis/)"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from vx import refscan

ROOT = Path(__file__).resolve().parents[2]

if __name__ == "__main__":
    for p in sys.argv[1:] or sorted((ROOT / "models" / "refs").glob("*.png")):
        r = refscan.analyze(p, ROOT / "models" / "analysis")
        print(Path(p).name, r["size"], "texel~", r["texel_px"], "regiões:", len(r["regions"]))
