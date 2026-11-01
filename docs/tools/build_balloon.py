#!/usr/bin/env python3
"""
Builds the balloon monster's sheets from docs/sprites_src/balloon/*.webp (green screen, 4200x2100 = 3 rows x 6 frames of 700):
  rows of the sources: 1 = ONE balloon, 2 = TWO, 3 = THREE (grounded: 1 fall, 2 stunned loop, 3 hit / death / ghost fade)
Output (every frame has the same size and the same crop, so all states share one scale):
  balloon_k3.webp, balloon_k2.webp, balloon_k1.webp   5 rows x 6: views, walk side, walk front, walk back, hit/fire
  balloon_k0.webp                                     3 rows x 6: fall, stunned loop, hit / death
The hover height of each state is drawn in the art (feet at a different height per row); the grounded rows are shifted so that
they sit on the same ground line as the one-balloon feet.
Usage: python3 docs/tools/build_balloon.py <out_dir> [scale=0.36]
"""
import os, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "sprites_src", "balloon")
sys.path.insert(0, HERE)
os.environ.pop("KEY", None)
os.environ["NOGLASS"] = "1"
import build_rig2 as b

CELL = 700
GROUND = 668          # feet of the one-balloon state; the grounded rows are placed on this line
Y1 = 690              # bottom of the crop


def load(name):
    im = Image.open(os.path.join(SRC, name + ".webp")).convert("RGB")
    b.CELL = CELL
    out = {}
    for r in range(3):
        row = []
        for c in range(6):
            cell = im.crop((c * CELL, r * CELL, (c + 1) * CELL, (r + 1) * CELL))
            rgba = b.key_cell(cell)
            row.append(Image.fromarray((rgba * 255).astype("uint8"), "RGBA"))
        out[r + 1] = row
    return out


def bottom(img):
    bb = img.getchannel("A").point(lambda v: 255 if v > 12 else 0).getbbox()
    return bb[3] if bb else None


def shifted(img, dy):
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, dy))
    return out


def main():
    out = sys.argv[1]
    k = float(sys.argv[2]) if len(sys.argv) > 2 else 0.36
    os.makedirs(out, exist_ok=True)
    views, ws, wf, wb, hf, gr = (load(n) for n in ["views_main", "walk_side", "walk_front", "walk_back", "front_hit_fire", "grounded"])

    # grounded rows: shift each so its resting frames sit on GROUND
    fall = gr[1]
    dy = GROUND - bottom(fall[2])
    fall = [shifted(f, dy) for f in fall]
    stun = gr[2]
    dy = GROUND - int(np.median([bottom(f) for f in stun]))
    stun = [shifted(f, dy) for f in stun]
    death = gr[3]
    dy = GROUND - int(np.median([bottom(f) for f in death[:4]]))
    death = [shifted(f, dy) for f in death]
    print("grounded shifts ok")

    sheets = {}
    for n in (1, 2, 3):
        hit = list(hf[n])
        hit[1] = hf[min(n + 1, 3)][1]     # the hit that takes a balloon away shows the next state's row: n balloons on the head + the loose one
        sheets[n] = [views[n], ws[n], wf[n], wb[n], hit]
    sheets[0] = [fall, stun, death]

    x1, y0 = CELL, CELL
    for rows in sheets.values():
        for row in rows:
            for f in row:
                bb = f.getchannel("A").point(lambda v: 255 if v > 12 else 0).getbbox()
                if bb:
                    y0 = min(y0, bb[1])
    y0 = max(0, y0 - 4)
    cw, ch = round(CELL * k), round((Y1 - y0) * k)
    print("crop y", y0, Y1, "frame", cw, "x", ch)
    total = 0
    for n, rows in sheets.items():
        sheet = Image.new("RGBA", (cw * 6, ch * len(rows)), (0, 0, 0, 0))
        for r, row in enumerate(rows):
            for c, f in enumerate(row):
                crop = f.crop((0, y0, CELL, Y1)).convert("RGBa").resize((cw, ch), Image.LANCZOS).convert("RGBA")
                sheet.paste(crop, (c * cw, r * ch))
        sheet.save(os.path.join(out, f"balloon_k{n}.webp"), "WEBP", quality=86, method=6)
        total += sheet.width * sheet.height * 4 / 1e6
        print("balloon_k%d" % n, sheet.size)
    print("decoded total ~%.1f MB" % total)


if __name__ == "__main__":
    main()
