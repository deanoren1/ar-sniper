#!/usr/bin/env python3
"""
Builds the orb hit effects from a sheet on a BLACK background (shown in the game with additive blending, so black = transparent):
  shot_orb_hit.webp      9 frames of the small explosion (row 2 of the sheet), one atlas row, equal cells
  shot_orb_impact.webp   3 full-screen impact frames (the 3 panels at the bottom), one atlas row, equal cells
Prints the cell sizes.

Usage: python3 docs/tools/build_orb_impact.py docs/sprites_src/orb_shot/impact_sheet.png app/src/main/res/drawable-nodpi
"""
import sys
import numpy as np
from PIL import Image

BLACK = 0.05
HIT_BAND = (278, 505)
PANEL_BAND = (559, 907)


def lift(rgb):
    a = np.asarray(rgb).astype(float) / 255
    a = np.clip((a - BLACK) / (1 - BLACK), 0, 1)
    return Image.fromarray((a * 255).astype("uint8"), "RGB")


def main():
    src, out = sys.argv[1], sys.argv[2]
    im = Image.open(src).convert("RGB")
    a = np.asarray(im).astype(float) / 255
    lum = a.max(2)

    # 9 small explosion frames: black becomes transparent (alpha from brightness), each frame gets a soft round edge,
    # so neighbouring frames never show as rectangles and the sparks are not cut off by a hard cell border
    band = lum[HIT_BAND[0]:HIT_BAND[1]]
    # the nine frames are not evenly spaced: boundaries between them (found from the sheet), centre = brightness centroid
    edges = [0, 150, 297, 466, 652, 842, 1046, 1257, 1463, im.width]
    centers = []
    for i in range(9):
        seg = band[:, edges[i]:edges[i + 1]] ** 2
        cols = np.arange(edges[i], edges[i + 1])
        centers.append(float((seg.sum(0) * cols).sum() / seg.sum()))
    print("centres", [round(c) for c in centers])
    CELL = 240
    atlas = np.zeros((CELL, CELL * 9, 4), np.uint8)
    yy, xx = np.mgrid[0:CELL, 0:CELL]
    d = np.hypot(xx - CELL / 2, yy - CELL / 2)
    soft = np.clip((118 - d) / 28, 0, 1)
    cy = (HIT_BAND[0] + HIT_BAND[1]) / 2
    for i in range(9):
        cx = centers[i]
        c = im.crop((int(cx - CELL / 2), int(cy - CELL / 2), int(cx - CELL / 2) + CELL, int(cy - CELL / 2) + CELL))
        f = np.clip((np.asarray(c).astype(float) / 255 - BLACK) / (1 - BLACK), 0, 1)
        m = f.max(2)
        alpha = np.clip(m * 1.5, 0, 1) * soft
        rgb = np.where(m[..., None] > 0.01, f / np.maximum(m[..., None], 0.01), 0)
        atlas[..., :3][:, i * CELL:(i + 1) * CELL] = (np.clip(rgb, 0, 1) * 255).astype(np.uint8)
        atlas[..., 3][:, i * CELL:(i + 1) * CELL] = (alpha * 255).astype(np.uint8)
    Image.fromarray(atlas, "RGBA").save(f"{out}/shot_orb_hit.webp", "WEBP", quality=90, method=6)
    print("hit cell", CELL, CELL)

    # 3 full-screen panels (their dark middles make automatic detection split them, so the x ranges are fixed)
    band = lum[PANEL_BAND[0]:PANEL_BAND[1]]
    segs = [(16, 566), (590, 1109), (1134, 1652)]
    pw = min(s[1] - s[0] + 1 for s in segs) - 8
    ys = np.where((band.mean(1) > 0.12))[0]
    py0, py1 = PANEL_BAND[0] + ys.min() + 4, PANEL_BAND[0] + ys.max() - 3
    ph = py1 - py0
    atlas = Image.new("RGB", (pw * len(segs), ph))
    for i, (s0, s1) in enumerate(segs):
        atlas.paste(im.crop((s0 + 4, py0, s0 + 4 + pw, py1)), (i * pw, 0))
    atlas.save(f"{out}/shot_orb_impact.webp", "WEBP", quality=90, method=6)
    print("impact cell", pw, ph, "frames", len(segs))


if __name__ == "__main__":
    main()
