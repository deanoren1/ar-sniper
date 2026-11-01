#!/usr/bin/env python3
"""
Builds the snail's slime effects from docs/sprites_src/snail (magenta-screen rows):
  shot_gas_grow.webp    6 flight frames, 256x256 cells; the slime blob has the same size (BLOB px) in every frame
  shot_gas_splats.webp  6 screen splats, 384x384 cells
Usage: python3 docs/tools/build_slime_fx.py <out_dir>
"""
import os, sys
import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "sprites_src", "snail")
sys.path.insert(0, HERE)
os.environ["KEY"] = "magenta"
import build_rig2 as b

GROW_CELL = 256
BLOB = 150          # blob diameter inside a flight cell


def cells(name):
    im = Image.open(os.path.join(SRC, name + ".webp")).convert("RGB")
    return [b.key_cell(im.crop((c * 700, 0, (c + 1) * 700, 700))) for c in range(6)]


def to_img(rgba):
    return Image.fromarray((rgba * 255).astype("uint8"), "RGBA")


def grow():
    sheet = Image.new("RGBA", (GROW_CELL * 6, GROW_CELL), (0, 0, 0, 0))
    for i, c in enumerate(cells("slime_grow")):
        a = c[..., 3]
        lab, n = ndimage.label(a > 0.5)
        sizes = ndimage.sum(a > 0.5, lab, range(1, n + 1))
        k = int(np.argmax(sizes)) + 1
        area = sizes[k - 1]
        cy, cx = ndimage.center_of_mass(lab == k)
        s = BLOB / (2 * np.sqrt(area / np.pi))
        img = to_img(c).convert("RGBa")
        size = round(700 * s)
        img = img.resize((size, size), Image.LANCZOS).convert("RGBA")
        cell = Image.new("RGBA", (GROW_CELL, GROW_CELL), (0, 0, 0, 0))
        cell.alpha_composite(img, (round(GROW_CELL / 2 - cx * s), round(GROW_CELL / 2 - cy * s))) if False else None
        ox, oy = round(GROW_CELL / 2 - cx * s), round(GROW_CELL / 2 - cy * s)
        # crop-paste with clipping
        canvas = Image.new("RGBA", (GROW_CELL, GROW_CELL), (0, 0, 0, 0))
        canvas.paste(img, (ox, oy))
        sheet.paste(canvas, (i * GROW_CELL, 0))
        bb = canvas.getchannel("A").point(lambda v: 255 if v > 12 else 0).getbbox()
        print("grow", i, "scale %.2f" % s, "bbox", bb)
    return sheet


def splats(cell=384):
    sheet = Image.new("RGBA", (cell * 6, cell), (0, 0, 0, 0))
    for i, c in enumerate(cells("slime_splats")):
        img = to_img(c)
        bb = img.getchannel("A").point(lambda v: 255 if v > 12 else 0).getbbox()
        img = img.crop((0, 0, 700, 700)).convert("RGBa").resize((cell, cell), Image.LANCZOS).convert("RGBA")
        sheet.paste(img, (i * cell, 0))
    return sheet


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    grow().save(os.path.join(out, "shot_gas_grow.webp"), "WEBP", quality=90, method=6)
    splats().save(os.path.join(out, "shot_gas_splats.webp"), "WEBP", quality=88, method=6)


if __name__ == "__main__":
    main()
