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

    # 9 small explosion frames
    band = lum[HIT_BAND[0]:HIT_BAND[1]]
    xs = np.where((band > 0.12).any(0))[0]
    x0, x1 = xs.min(), xs.max()
    cw = (x1 - x0 + 1) / 9
    cell_w, cell_h = int(round(cw)), HIT_BAND[1] - HIT_BAND[0]
    atlas = Image.new("RGB", (cell_w * 9, cell_h))
    for i in range(9):
        c = im.crop((int(x0 + i * cw), HIT_BAND[0], int(x0 + i * cw) + cell_w, HIT_BAND[1]))
        atlas.paste(lift(c), (i * cell_w, 0))
    atlas.save(f"{out}/shot_orb_hit.webp", "WEBP", quality=90, method=6)
    print("hit cell", cell_w, cell_h)

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
