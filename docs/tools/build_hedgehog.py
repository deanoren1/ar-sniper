#!/usr/bin/env python3
"""
Builds the hedgehog's rig2 sheet from docs/sprites_src/hedgehog/*.webp (green-screen rows, 4200x700).
Rows were drawn at slightly different scales / ground lines, so each is normalised first (standing body
~530 px, feet on y=640). The 9th sheet row (front charge + release) is assembled from existing frames:
charge = front_charge_death 0-2, release = front_hit_fire frame 4 (spike leaving the mouth).

Usage: python3 docs/tools/build_hedgehog.py <out.webp> [frame_width] [reference.png]
"""
import os, sys, tempfile
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "sprites_src", "hedgehog")
sys.path.insert(0, HERE)
os.environ["NOGLASS"] = "1"   # the hedgehog's body is solid blue: no glass rule
import build_rig2 as b

GREEN = (0, 255, 0)
# name: (scale, source ground line)
ROWS = {
    "views_main": (0.914, 660),
    "walk_side": (0.82, 694),
    "walk_front": (0.987, 659),
    "walk_back": (0.912, 659),
    "side_charge_hit": (0.92, 679),
    "side_death": (0.935, 675),
    "front_charge_death": (0.915, 680),
    "front_hit_fire": (0.92, 676),
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
        x = c * 700 + 350 - size[0] // 2
        y = int(round(640 - ground * scale))
        out.paste(cell, (x, y))
    clean = Image.new("RGB", (4200, 700), GREEN)
    for c in range(6):
        clean.paste(out.crop((c * 700, 0, (c + 1) * 700, 700)), (c * 700, 0))
    return clean


def cell_of(img, c):
    return img.crop((c * 700, 0, (c + 1) * 700, 700))


DIR_SCALE = 0.90          # the direction frames were drawn ~10% larger than the main rows (crown to feet)
DIR_HALF = 440            # half-width (in 700-px cell units) kept around the body centre: room for the crystal


def dirs(out, x0, y0, y1, k, ch):
    """Firing frames with the crystal leaving the mouth sideways (fire_dirs.webp, top row, 5 frames of 840x700).
    Output: 4 frames [far left, near left, near right, far right] on cells wider than the main ones, same
    pixel scale and the same feet line, body centred."""
    im = Image.open(os.path.join(SRC, "fire_dirs.webp")).convert("RGB")
    fw = 840
    width = round(2 * DIR_HALF * k)
    sheet = Image.new("RGBA", (width * 4, ch), (0, 0, 0, 0))
    b.CELL = fw
    for out_i, col in enumerate([0, 1, 3, 4]):
        cell = im.crop((col * fw, 0, (col + 1) * fw, 700))
        rgba = b.key_cell(cell)
        img = Image.fromarray((rgba * 255).astype("uint8"), "RGBA")
        a = np.asarray(img.getchannel("A")) > 12
        # feet line: the source cells all end their feet at y~608 (the last pixel row holds a stray line)
        ground = 608
        # body centre = median x of the crown's top spike (the crystal never reaches that high)
        ys, xs = np.where(a)
        top = ys.min()
        cx = float(np.median(xs[ys < top + 30]))
        s = DIR_SCALE
        size = (round(img.width * s), round(img.height * s))
        sc = img.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")
        canvas = Image.new("RGBA", (1500, 700), (0, 0, 0, 0))
        canvas.alpha_composite(sc, (round(750 - cx * s), round(640 - ground * s))) if (round(750 - cx * s) >= 0 and round(640 - ground * s) >= 0) else canvas.paste(sc, (round(750 - cx * s), round(640 - ground * s)))
        crop = canvas.crop((750 - DIR_HALF, y0, 750 + DIR_HALF, y1)).convert("RGBa").resize((width, ch), Image.LANCZOS).convert("RGBA")
        sheet.paste(crop, (out_i * width, 0))
        print("dir", out_i, "col", col, "feet-centre", round(cx), "ground", ground)
    sheet.save(out, "WEBP", quality=88, method=6)
    print("dir sheet", sheet.size)


def main():
    out = sys.argv[1]
    fw = sys.argv[2] if len(sys.argv) > 2 else None
    ref = sys.argv[3] if len(sys.argv) > 3 else None
    tmp = tempfile.mkdtemp()
    rows = {n: normalise(n, s, g) for n, (s, g) in ROWS.items()}
    for n, im in rows.items():
        im.save(os.path.join(tmp, n + ".png"))
    # row 8: charge (5) + release (1)
    cd, hf = rows["front_charge_death"], rows["front_hit_fire"]
    seq = [cell_of(cd, 0), cell_of(cd, 1), cell_of(cd, 1), cell_of(cd, 2), cell_of(cd, 2), cell_of(hf, 4)]
    row8 = Image.new("RGB", (4200, 700), GREEN)
    for i, cimg in enumerate(seq):
        row8.paste(cimg, (i * 700, 0))
    row8.save(os.path.join(tmp, "front_charge_release.png"))
    b.ROWS = [(n, 1.0, 640) for n in list(ROWS) + ["front_charge_release"]]
    b.FRAME_SCALE = {}
    sys.argv = ["build_rig2.py", tmp, out] + ([fw] if fw else ["352"]) + ([ref] if ref else [])
    x0, y0, x1, y1, k, cw, ch = b.main()
    dirs(out.replace(".webp", "_dir.webp"), x0, y0, y1, k, ch)


if __name__ == "__main__":
    main()
