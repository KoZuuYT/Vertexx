#!/usr/bin/env python3
"""Poses a partir de imagens.

  python tools/models/pose_fit.py fit moobloom models/refs/moobloom.png [--bones head,leg_fl] [--yaw 20,80 --pitch 10,50]
  python tools/models/pose_fit.py copy models/poses/a.json glare [--mirror] [--amount 0.8] [--save-as pose_a]
  python tools/models/pose_fit.py seq moobloom models/refs/walk_frames --fps 10 --name walk_ref

``fit`` acha a pose que casa com a imagem (câmera ajustada junto) e grava em models/poses/; ``copy`` leva a
pose para outro modelo (espelho/intensidade) e, com --save-as, a grava nele como animação de 1 quadro;
``seq`` ajusta uma pose por quadro de uma pasta de PNGs/GIF e cria a animação no .bbmodel."""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from vx import pose as P  # noqa: E402
from vx import refscan  # noqa: E402
from vx.model import load_bbmodel, save_bbmodel  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "models" / "src"
POSES = ROOT / "models" / "poses"
PREVIEW = ROOT / "models" / "preview"


def _rng(text, default):
    return tuple(int(v) for v in text.split(",")) if text else default


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("fit")
    f.add_argument("model")
    f.add_argument("ref")
    f.add_argument("--bones")
    f.add_argument("--yaw")
    f.add_argument("--pitch")
    f.add_argument("--color", type=float, default=0.0)
    c = sub.add_parser("copy")
    c.add_argument("pose")
    c.add_argument("model")
    c.add_argument("--mirror", action="store_true")
    c.add_argument("--amount", type=float, default=1.0)
    c.add_argument("--save-as")
    s = sub.add_parser("seq")
    s.add_argument("model")
    s.add_argument("frames")
    s.add_argument("--fps", type=float, default=10.0)
    s.add_argument("--name", default="pose_seq")
    s.add_argument("--bones")
    a = ap.parse_args()

    if a.cmd == "fit":
        model = load_bbmodel(SRC / f"{a.model}.bbmodel")
        rgb, mask, _ = refscan.load_ref(a.ref)
        cam, iou = refscan.fit_camera(model, mask, yaw_range=_rng(a.yaw, (-180, 180)), pitch_range=_rng(a.pitch, (0, 70)))
        print(f"câmera: yaw={cam.yaw:.1f} pitch={cam.pitch:.1f} IoU(repouso)={iou:.3f}")
        bones = a.bones.split(",") if a.bones else None
        pose, info = P.fit_pose(model, rgb, mask, cam, bones=bones, color_w=a.color)
        out = POSES / f"{a.model}_{Path(a.ref).stem}.json"
        P.save_pose(out, pose, model=a.model, ref=str(a.ref), yaw=cam.yaw, pitch=cam.pitch, **info)
        print(P.describe(pose))
        print("salvo", out, info)
        PREVIEW.mkdir(parents=True, exist_ok=True)
        refscan.compare(model, rgb, mask, cam, PREVIEW / f"{a.model}_pose_compare.png", values=P.to_game_values(pose), label=a.model)
    elif a.cmd == "copy":
        pose = P.load_pose(a.pose)
        model = load_bbmodel(SRC / f"{a.model}.bbmodel")
        out = P.copy_pose(pose, model, mirror=a.mirror, amount=a.amount)
        print(P.describe(out) or "(nenhum osso em comum)")
        dest = POSES / f"{a.model}_{Path(a.pose).stem}.json"
        P.save_pose(dest, out, model=a.model, copied_from=a.pose)
        if a.save_as:
            P.poses_to_animation(model, [(0.0, out)], a.save_as, length=0.05, loop="hold", interp="linear")
            save_bbmodel(model, SRC / f"{a.model}.bbmodel")
            print("animação", a.save_as, "gravada em", a.model)
    else:
        from PIL import Image
        model = load_bbmodel(SRC / f"{a.model}.bbmodel")
        folder = Path(a.frames)
        files = sorted(folder.glob("*.png")) if folder.is_dir() else []
        if not files:
            raise SystemExit("pasta de quadros vazia (use capture.py frames para extrair de GIF/vídeo)")
        frames = [refscan.load_ref(p, crop=False)[:2] for p in files]
        cam, iou = refscan.fit_camera(model, frames[0][1])
        print(f"câmera: yaw={cam.yaw:.1f} pitch={cam.pitch:.1f} IoU={iou:.3f}; {len(frames)} quadros")
        poses = P.fit_sequence(model, frames, cam, fps=a.fps, bones=a.bones.split(",") if a.bones else None, verbose=True)
        P.poses_to_animation(model, poses, a.name, length=len(poses) / a.fps)
        save_bbmodel(model, SRC / f"{a.model}.bbmodel")
        print("animação", a.name, "gravada em", a.model)
    return 0


if __name__ == "__main__":
    sys.exit(main())
