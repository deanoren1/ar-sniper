#!/usr/bin/env python3
"""
Builds the balloon's on-screen explosion sheet from docs/sprites_src/balloon/shot_boom.webp (green screen, 6 cells of 768):
  shot_boom_splats.webp   6 explosions, 384x384 cells (0-2 small, 3-5 big), centred on the blast
Usage: python3 docs/tools/build_balloon_boom.py <out_dir>
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

CELL = 384


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    im = Image.open(os.path.join(SRC, "shot_boom.webp")).convert("RGB")
    cw = im.width // 6
    b.CELL = cw
    sheet = Image.new("RGBA", (CELL * 6, CELL), (0, 0, 0, 0))
    for i in range(6):
        rgba = b.key_cell(im.crop((i * cw, 0, (i + 1) * cw, im.height)))
        # yellow-green fringe on the rays: nothing in the blast is greener than it is red
        rgba[..., 1] = np.minimum(rgba[..., 1], rgba[..., 0] * 0.88)
        img = Image.fromarray((rgba * 255).astype("uint8"), "RGBA")
        sc = CELL / cw * 0.92
        small = img.convert("RGBa").resize((round(img.width * sc), round(img.height * sc)), Image.LANCZOS).convert("RGBA")
        canvas = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
        canvas.paste(small, ((CELL - small.width) // 2, (CELL - small.height) // 2))
        sheet.paste(canvas, (i * CELL, 0))
        print("boom", i, canvas.getchannel("A").point(lambda v: 255 if v > 12 else 0).getbbox())
    sheet.save(os.path.join(out, "shot_boom_splats.webp"), "WEBP", quality=88, method=6)


if __name__ == "__main__":
    main()
