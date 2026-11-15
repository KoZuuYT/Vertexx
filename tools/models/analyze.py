#!/usr/bin/env python3
"""Análise de referências:  python tools/models/analyze.py models/refs/moobloom.png [...]  (saída em models/analysis/)

Além do relatório de paleta/regiões (refscan), imprime a análise com OpenCV: tamanho do texel, direções das
arestas, câmera estimada só pela geometria das arestas e paleta k-means em CIELAB."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from vx import cvscan, refscan

ROOT = Path(__file__).resolve().parents[2]

if __name__ == "__main__":
    for p in sys.argv[1:] or sorted((ROOT / "models" / "refs").glob("*.png")):
        r = refscan.analyze(p, ROOT / "models" / "analysis")
        rgb, mask, _ = refscan.load_ref(p)
        texel = cvscan.texel_size(rgb, mask)
        _, peaks = cvscan.edge_directions(rgb, mask)
        views = cvscan.estimate_view(peaks)
        print(Path(p).name, r["size"], "regiões:", len(r["regions"]))
        print(f"  texel: {texel['cell']:.2f}px (confiança {texel['confidence']:.2f})")
        print("  arestas (ângulo°, peso):", [(round(a), round(w)) for a, w in peaks[:5]])
        print("  câmera pelas arestas (yaw°, pitch°, custo):", [(y, pi, round(c, 1)) for y, pi, c in views[:2]])
        print("  paleta:", [("#%02x%02x%02x" % c, round(f, 2)) for c, f in cvscan.palette_kmeans(rgb, mask, 6)])
