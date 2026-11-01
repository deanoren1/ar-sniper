#!/usr/bin/env python3
"""
Builds the giant's burning-boulder flight sheet (orb-style: head on the left, tail streaming to the right) from
docs/sprites_src/giant/rock_trail.webp (flat green screen, 6 frames):
  shot_rock_flight.webp   6 frames of 480x230; head centre at (100, 115), head diameter 162 (same anchors as shot_orb_flight)
Usage: python3 docs/tools/build_rock_fx.py <out_dir>
"""
import os, sys
import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "sprites_src", "giant")
sys.path.insert(0, HERE)
os.environ.pop("KEY", None)   # plain green key: no green-skinned character here, and flame gaps must not keep green
os.environ["NOGLASS"] = "1"
import build_rig2 as b

CW, CH = 480, 230
HEAD_X, HEAD_Y, HEAD_D = 100, 115, 162


def head_of(rgb, alpha):
    """Rock = dark red pixels (the flames are yellow/orange and brighter). Returns centre x, y and diameter."""
    r, g, bl = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mask = (alpha > 0.5) & (r > 0.28) & (g < 0.34) & (bl < 0.3) & (r > g * 1.8)
    lab, n = ndimage.label(mask)
    sizes = ndimage.sum(mask, lab, range(1, n + 1))
    k = int(np.argmax(sizes)) + 1
    ys, xs = np.where(lab == k)
    return (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2, ((xs.max() - xs.min()) + (ys.max() - ys.min())) / 2


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    im = Image.open(os.path.join(SRC, "rock_trail.webp")).convert("RGB")
    cw = im.width // 6
    b.CELL = cw
    cells = [b.key_cell(im.crop((c * cw, 0, (c + 1) * cw, im.height))) for c in range(6)]
    infos = [head_of(c[..., :3], c[..., 3]) for c in cells]
    d = float(np.median([i[2] for i in infos]))     # one scale for every frame
    s = HEAD_D / d
    sheet = Image.new("RGBA", (CW * 6, CH), (0, 0, 0, 0))
    for i, (c, (cx, cy, _)) in enumerate(zip(cells, infos)):
        img = Image.fromarray((c * 255).astype("uint8"), "RGBA").convert("RGBa")
        big = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS).convert("RGBA")
        canvas = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
        canvas.paste(big, (round(HEAD_X - cx * s), round(HEAD_Y - cy * s)))
        sheet.paste(canvas, (i * CW, 0))
        print("frame", i, "head", round(cx), round(cy), round(infos[i][2]), "bbox", canvas.getchannel("A").point(lambda v: 255 if v > 12 else 0).getbbox())
    sheet.save(os.path.join(out, "shot_rock_flight.webp"), "WEBP", quality=90, method=6)


if __name__ == "__main__":
    main()
