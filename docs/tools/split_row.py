#!/usr/bin/env python3
"""
Turns a ChatGPT row that came out in the wrong format (oversized characters that touch their cell edges, extra empty
canvas above and below) into the standard 4200x700 green row used by build_rig2.py.

The characters are separated by the empty green columns between them (no fixed cell grid is needed), every frame gets
the same scale, and the feet are put on the standard ground line.

Usage: python3 docs/tools/split_row.py <in.png> <out.png> [ground_y_in_source] [standing_height_in_source]
  ground_y_in_source   y of the feet in the source image (default: bottom of the first frame)
  standing_height      height from the feet to the top of the first frame (default: measured)
"""
import sys
import numpy as np
from PIL import Image

CELL, GROUND, STAND = 700, 640, 540


def main():
    src, out = sys.argv[1], sys.argv[2]
    im = Image.open(src).convert("RGB")
    a = np.asarray(im).astype(float) / 255
    fg = (a[..., 1] - np.maximum(a[..., 0], a[..., 2])) < 0.3
    ys, _ = np.where(fg)
    top, bottom = ys.min(), ys.max()
    col = fg[top:bottom + 1].sum(0)
    n = 6
    step = im.width / n
    # the empty column run nearest to each expected boundary
    bounds = [0]
    for k in range(1, n):
        x = int(k * step)
        lo, hi = max(0, x - 120), min(im.width, x + 120)
        zeros = np.where(col[lo:hi] == 0)[0] + lo
        if len(zeros) == 0:
            sys.exit(f"no empty column between frames {k} and {k + 1}")
        bounds.append(int(zeros[np.argmin(abs(zeros - x))]))
    bounds.append(im.width)
    first = fg[:, bounds[0]:bounds[1]]
    fy, _ = np.where(first)
    ground = int(sys.argv[3]) if len(sys.argv) > 3 else int(fy.max())
    stand = int(sys.argv[4]) if len(sys.argv) > 4 else int(ground - fy.min())
    s = STAND / stand
    band_top = max(0, int(ground - (GROUND - 0) / s))
    row = Image.new("RGB", (CELL * n, CELL), (0, 255, 0))
    for i in range(n):
        x0, x1 = bounds[i], bounds[i + 1]
        m = fg[:, x0:x1]
        _, xs = np.where(m)
        cx = (xs.min() + xs.max()) / 2
        crop = im.crop((x0, band_top, x1, im.height))
        w, h = round(crop.width * s), round(crop.height * s)
        crop = crop.resize((w, h), Image.LANCZOS)
        px = round(i * CELL + CELL / 2 - cx * s)
        py = round(GROUND - (ground - band_top) * s)
        row.paste(crop, (px, py))
    row.save(out)
    print(f"bounds {bounds}; scale {s:.3f}; ground {ground}; standing {stand}")


if __name__ == "__main__":
    main()
