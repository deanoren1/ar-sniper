#!/usr/bin/env python3
"""
Builds the dragon boss sheets (classic arena) from docs/sprites_src/dragon/*.webp (green screen, 4200x700 = 6 cells of 700):
  boss_dragon.webp        flying, 5 rows x 6 (BossAnim layout): idle(6), charge(5), fire(2), hit(4), death(6)
  boss_dragon_land.webp   landed (enraged phase), 5 rows x 6: landing(6), idle(6), charge(5), fire(2), hit(4)
  shot_fire_flight.webp   6 frames of 480x230, head on the left (orb-style flight sheet), head diameter 170 at (100,115)
Every dragon frame has the same crop and scale. The flying fire row is assembled from the flying charge row (the full charge
frame is the release) and the first idle frame.
Usage: python3 docs/tools/build_dragon.py <out_dir> [scale=0.5]
"""
import os, sys
import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "sprites_src", "dragon")
sys.path.insert(0, HERE)
os.environ.pop("KEY", None)
os.environ["NOGLASS"] = "1"
import build_rig2 as b

CELL = 700


def keyed(name, n):
    """n keyed frames (RGBA images) of a 4200x700 row; slivers of the neighbouring cell at the left/right border are removed"""
    im = Image.open(os.path.join(SRC, name + ".webp")).convert("RGB")
    b.CELL = CELL
    out = []
    for c in range(n):
        rgba = b.key_cell(im.crop((c * CELL, 0, (c + 1) * CELL, CELL)))
        alpha = rgba[..., 3] > 0.05
        lab, k = ndimage.label(alpha)
        if k > 1:
            sizes = ndimage.sum(alpha, lab, range(1, k + 1))
            big = sizes.max()
            for i in range(1, k + 1):
                ys, xs = np.where(lab == i)
                touches = xs.min() == 0 or xs.max() == CELL - 1
                if touches and sizes[i - 1] < 0.2 * big:
                    rgba[lab == i, 3] = 0
        out.append(Image.fromarray((rgba * 255).astype("uint8"), "RGBA"))
    return out


def blank():
    return Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))


def bbox(img):
    return img.getchannel("A").point(lambda v: 255 if v > 12 else 0).getbbox()


def main():
    out = sys.argv[1]
    s = float(sys.argv[2]) if len(sys.argv) > 2 else 0.5
    os.makedirs(out, exist_ok=True)
    idle_air = keyed("idle_air", 6)
    charge_air = keyed("charge_air", 5)
    hit_air = keyed("hit_air", 4)
    death = keyed("death", 6)
    fire_air = [charge_air[4], idle_air[0]]
    air = [idle_air, charge_air, fire_air, hit_air, death]
    landing = keyed("landing_v1", 6)
    idle_land = keyed("idle_land", 6)
    charge_land = keyed("charge_land", 5)
    fire_land = keyed("fire_land", 2)
    hit_land = keyed("hit_land", 4)
    land = [landing, idle_land, charge_land, fire_land, hit_land]

    x0, x1, y0 = CELL, 0, CELL
    for rows in (air, land):
        for row in rows:
            for f in row:
                bb = bbox(f)
                if bb:
                    x0, x1, y0 = min(x0, bb[0]), max(x1, bb[2]), min(y0, bb[1])
    x0, x1, y0, y1 = max(0, x0 - 4), min(CELL, x1 + 4), max(0, y0 - 4), 690
    cw, ch = round((x1 - x0) * s), round((y1 - y0) * s)
    print("crop x", x0, x1, "y", y0, y1, "frame", cw, "x", ch)
    for name, rows in (("boss_dragon", air), ("boss_dragon_land", land)):
        sheet = Image.new("RGBA", (cw * 6, ch * len(rows)), (0, 0, 0, 0))
        for r, row in enumerate(rows):
            for c, f in enumerate(row):
                crop = f.crop((x0, y0, x1, y1)).convert("RGBa").resize((cw, ch), Image.LANCZOS).convert("RGBA")
                sheet.paste(crop, (c * cw, r * ch))
        sheet.save(os.path.join(out, name + ".webp"), "WEBP", quality=86, method=6)
        print(name, sheet.size, "~%.1f MB decoded" % (sheet.width * sheet.height * 4 / 1e6))

    # fireball flight
    CW, CH, HX, HY, HD = 480, 230, 100, 115, 170
    fl = keyed("shot_flight", 6)
    h0 = bbox(fl[0])
    scale = HD / (h0[3] - h0[1])
    sheet = Image.new("RGBA", (CW * 6, CH), (0, 0, 0, 0))
    for i, f in enumerate(fl):
        bb = bbox(f)
        big = f.convert("RGBa").resize((round(f.width * scale), round(f.height * scale)), Image.LANCZOS).convert("RGBA")
        cy = (bb[1] + bb[3]) / 2
        canvas = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
        canvas.paste(big, (round(HX - HD / 2 - bb[0] * scale), round(HY - cy * scale)))
        sheet.paste(canvas, (i * CW, 0))
        print("flight", i, bbox(canvas))
    sheet.save(os.path.join(out, "shot_fire_flight.webp"), "WEBP", quality=90, method=6)


if __name__ == "__main__":
    main()
