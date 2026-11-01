#!/usr/bin/env python3
"""
Builds the giant's rig2 sheet from docs/sprites_src/giant/*.webp (flat green screen behind a green-skinned character,
so the cells are keyed with KEY=flood). Source rows are 2172x724 (cells 362x724, small character); each row is normalised
to a 4200x700 row (standing body ~530 px, feet on y=640). The 9th sheet row (front charge + release) is assembled from
existing frames: charge = front_charge_death 0-2 (boulder rising and igniting), release = front_hit_fire frame 5 (follow-through; frame 4 has the boulder below the feet line, which the cell would cut).

Usage: python3 docs/tools/build_giant.py <out.webp> [frame_width] [reference.png]
"""
import os, sys, tempfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "sprites_src", "giant")
sys.path.insert(0, HERE)
os.environ["KEY"] = "flood"
os.environ["NOGLASS"] = "1"
import build_rig2 as b

GREEN = (0, 255, 0)
# name: (scale, source ground line)
ROWS = {
    # standing body ~430 px (not 530): the burning boulder held overhead is ~790 px tall at 530 and would not fit the 640 px
    # above the ground line of a 700 px cell. Every row uses the same scale family so the giant keeps one size.
    "views_main": (1.225, 521),
    "walk_side": (1.225, 524),
    "walk_front": (1.225, 513),
    "walk_back": (1.225, 512),
    "side_charge_hit": (1.24, 537),
    "side_death": (1.24, 533),
    "front_charge_death": (1.20, 541),
    "front_hit_fire": (1.22, 536),
}


def normalise(name, scale, ground):
    im = Image.open(os.path.join(SRC, name + ".webp")).convert("RGB")
    w, h = im.size
    cw = w // 6
    out = Image.new("RGB", (4200, 700), GREEN)
    for c in range(6):
        cell = im.crop((c * cw, 0, (c + 1) * cw, h))
        size = (max(1, round(cw * scale)), max(1, round(h * scale)))
        cell = cell.resize(size, Image.LANCZOS)
        out.paste(cell, (c * 700 + 350 - size[0] // 2, int(round(640 - ground * scale))))
    clean = Image.new("RGB", (4200, 700), GREEN)
    for c in range(6):
        clean.paste(out.crop((c * 700, 0, (c + 1) * 700, 700)), (c * 700, 0))
    return clean


def cell_of(img, c):
    return img.crop((c * 700, 0, (c + 1) * 700, 700))


def main():
    out = sys.argv[1]
    fw = sys.argv[2] if len(sys.argv) > 2 else "352"
    ref = sys.argv[3] if len(sys.argv) > 3 else None
    tmp = tempfile.mkdtemp()
    rows = {n: normalise(n, s, g) for n, (s, g) in ROWS.items()}
    for n, im in rows.items():
        im.save(os.path.join(tmp, n + ".png"))
    cd, hf = rows["front_charge_death"], rows["front_hit_fire"]
    seq = [cell_of(cd, 0), cell_of(cd, 1), cell_of(cd, 1), cell_of(cd, 2), cell_of(cd, 2), cell_of(hf, 5)]
    row8 = Image.new("RGB", (4200, 700), GREEN)
    for i, cimg in enumerate(seq):
        row8.paste(cimg, (i * 700, 0))
    row8.save(os.path.join(tmp, "front_charge_release.png"))
    b.ROWS = [(n, 1.0, 640) for n in list(ROWS) + ["front_charge_release"]]
    b.FRAME_SCALE = {}
    sys.argv = ["build_rig2.py", tmp, out, fw] + ([ref] if ref else [])
    b.main()


if __name__ == "__main__":
    main()
