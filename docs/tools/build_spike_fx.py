#!/usr/bin/env python3
"""
Builds the hedgehog's spike effects from docs/sprites_src/hedgehog (green-screen rows):
  shot_spike_grow.webp    6 spinning-crystal flight frames, 256x256 cells (same scale in every frame, centred on the crystal)
  shot_spike_splats.webp  6 clusters of quills stuck in the screen, 384x384 cells
Usage: python3 docs/tools/build_spike_fx.py <out_dir>
"""
import os, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "sprites_src", "hedgehog")
sys.path.insert(0, HERE)
os.environ["NOGLASS"] = "1"
import build_rig2 as b

GROW_CELL = 256
LENGTH = 170        # longest crystal extent (px) inside a flight cell


def cells(name, n):
    im = Image.open(os.path.join(SRC, name + ".webp")).convert("RGB")
    cw = im.width // 6
    b.CELL = cw
    return [b.key_cell(im.crop((c * cw, 0, (c + 1) * cw, im.height))) for c in range(6)]


def to_img(rgba):
    return Image.fromarray((rgba * 255).astype("uint8"), "RGBA")


def bbox(img):
    return img.getchannel("A").point(lambda v: 255 if v > 12 else 0).getbbox()


def grow():
    imgs = [to_img(c) for c in cells("spike_flight", 6)]
    boxes = [bbox(i) for i in imgs]
    longest = max(max(bb[2] - bb[0], bb[3] - bb[1]) for bb in boxes)
    s = LENGTH / longest
    sheet = Image.new("RGBA", (GROW_CELL * 6, GROW_CELL), (0, 0, 0, 0))
    for i, (img, bb) in enumerate(zip(imgs, boxes)):
        cx, cy = (bb[0] + bb[2]) / 2, (bb[1] + bb[3]) / 2
        size = round(img.width * s)
        sc = img.convert("RGBa").resize((size, size), Image.LANCZOS).convert("RGBA")
        canvas = Image.new("RGBA", (GROW_CELL, GROW_CELL), (0, 0, 0, 0))
        canvas.paste(sc, (round(GROW_CELL / 2 - cx * s), round(GROW_CELL / 2 - cy * s)))
        sheet.paste(canvas, (i * GROW_CELL, 0))
        print("grow", i, "bbox", bbox(canvas))
    return sheet


def splats(cell=384):
    sheet = Image.new("RGBA", (cell * 6, cell), (0, 0, 0, 0))
    for i, c in enumerate(cells("spike_stuck", 6)):
        img = to_img(c).convert("RGBa").resize((cell, cell), Image.LANCZOS).convert("RGBA")
        # the source clusters touch the bottom edge: fade the last 12% so there is no hard cut
        a = np.asarray(img).astype(float)
        ramp = np.clip((cell - np.arange(cell)) / (cell * 0.12), 0, 1)[:, None]
        a[..., 3] *= ramp
        img = Image.fromarray(a.astype("uint8"), "RGBA")
        sheet.paste(img, (i * cell, 0))
        print("splat", i, "bbox", bbox(img))
    return sheet


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    grow().save(os.path.join(out, "shot_spike_grow.webp"), "WEBP", quality=90, method=6)
    splats().save(os.path.join(out, "shot_spike_splats.webp"), "WEBP", quality=88, method=6)


if __name__ == "__main__":
    main()
