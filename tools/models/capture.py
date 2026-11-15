"""CLI de captura de referências.

  python tools/models/capture.py screen --window Minecraft --out models/refs/x.png [--isolate]
  python tools/models/capture.py url https://... --out models/refs/x.png
  python tools/models/capture.py frames anim.gif --outdir models/refs/x_frames
  python tools/models/capture.py vanilla assets/minecraft/textures/entity/cow/cow.png --out models/refs/cow_tex.png
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from vx import capture  # noqa: E402


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("screen")
    s.add_argument("--window")
    s.add_argument("--region", nargs=4, type=int)
    s.add_argument("--out", required=True)
    s.add_argument("--isolate", action="store_true")
    u = sub.add_parser("url")
    u.add_argument("url")
    u.add_argument("--out", required=True)
    f = sub.add_parser("frames")
    f.add_argument("path")
    f.add_argument("--outdir", required=True)
    f.add_argument("--every", type=int, default=1)
    v = sub.add_parser("vanilla")
    v.add_argument("asset")
    v.add_argument("--out")
    v.add_argument("--list", action="store_true")
    a = p.parse_args()
    if a.cmd == "screen":
        im = capture.screen(region=a.region, window=a.window)
        if a.isolate:
            im = capture.isolate(im)
        Path(a.out).parent.mkdir(parents=True, exist_ok=True)
        im.save(a.out)
        print("salvo", a.out, im.size)
    elif a.cmd == "url":
        im = capture.fetch(a.url, a.out)
        print("salvo", a.out, im.size)
    elif a.cmd == "frames":
        path = Path(a.path)
        if path.suffix.lower() in (".gif", ".webp", ".apng", ".png"):
            frames = [f for f, _ in capture.gif_frames(path)][::a.every]
        else:
            frames, fps = capture.video_frames(path, a.every)
        out = Path(a.outdir)
        out.mkdir(parents=True, exist_ok=True)
        for i, fr in enumerate(frames):
            fr.save(out / f"{i:03d}.png")
        print(len(frames), "quadros em", out)
    elif a.cmd == "vanilla":
        if a.list:
            print("\n".join(capture.vanilla_list(a.asset)))
        else:
            im = capture.vanilla_asset(a.asset)
            if a.out:
                im.save(a.out)
            print(a.asset, im.size)


if __name__ == "__main__":
    main()
