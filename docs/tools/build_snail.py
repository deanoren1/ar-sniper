#!/usr/bin/env python3
"""
Builds the snail's rig2 sheet from docs/sprites_src/snail/*.webp (magenta-screen rows).
Some rows came out 2172x724 (cells 362x724, small character); every row is first normalised to a
4200x700 magenta row (standing height ~530 px, feet on y=640) and then handed to build_rig2.py.

Usage: python3 docs/tools/build_snail.py <out.webp> [frame_width]
"""
import os, sys, tempfile
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "sprites_src", "snail")
sys.path.insert(0, HERE)
os.environ["KEY"] = "magenta"
import build_rig2 as b

MAG = (255, 0, 255)
# name: (scale, source ground line or "frame" = align each frame's own lowest pixel)
ROWS = {
    "views_main": (530 / 543.0, 666.5),
    "walk_side": (1.0, 639),
    "walk_front": (1.0, "frame"),
    "walk_back": (530 / 371.0, "frame"),
    "side_charge_hit": (530 / 343.0, 485),
    "side_death": (1.0, 639),
    "front_charge_death": (1.0, 643.5),
    "front_hit_fire": (1.0, 641),
    "front_charge_release": (1.0, 641),
}


def mask_of(a):
    return (np.minimum(a[..., 0], a[..., 2]) - a[..., 1]) < 150


def normalise(name, scale, ground):
    im = Image.open(os.path.join(SRC, name + ".webp")).convert("RGB")
    w, h = im.size
    cw = w // 6
    out = Image.new("RGB", (4200, 700), MAG)
    for c in range(6):
        cell = im.crop((c * cw, 0, (c + 1) * cw, h))
        g = ground
        if g == "frame":
            ys, _ = np.where(mask_of(np.asarray(cell).astype(int)))
            g = ys.max()
        size = (max(1, round(cw * scale)), max(1, round(h * scale)))
        cell = cell.resize(size, Image.LANCZOS)
        x = c * 700 + 350 - size[0] // 2
        y = int(round(640 - g * scale))
        out.paste(cell, (x, y))
        # cells must not leak into neighbours: repaint the margins outside this cell
    # clip to each 700px cell (large source cells pasted wider than 700 would spill)
    clean = Image.new("RGB", (4200, 700), MAG)
    for c in range(6):
        clean.paste(out.crop((c * 700, 0, (c + 1) * 700, 700)), (c * 700, 0))
    return clean


def main():
    out = sys.argv[1]
    fw = sys.argv[2] if len(sys.argv) > 2 else None
    tmp = tempfile.mkdtemp()
    for name, (scale, ground) in ROWS.items():
        normalise(name, scale, ground).save(os.path.join(tmp, name + ".png"))
    b.ROWS = [(n, 1.0, 640) for n in ROWS]
    sys.argv = ["build_rig2.py", tmp, out] + ([fw] if fw else [])
    b.main()


if __name__ == "__main__":
    main()
