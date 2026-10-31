#!/usr/bin/env python3
"""Cuts the 3-layer green-screen snow sheet into three RGBA webp strips (snow_near/mid/far).
Usage: python3 docs/tools/build_snow.py <sheet.png> <outdir>"""
import sys, numpy as np
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB"); W, H = im.size
bounds = [(0, 298), (302, 580), (586, H)]
for name, (y0, y1) in zip(["near", "mid", "far"], bounds):
    a = np.asarray(im.crop((0, y0 + 62, W, y1 - 2))).astype(float) / 255
    m = np.minimum(a[..., 0], a[..., 2])
    alpha = np.clip(m * 1.1, 0, 1)
    rgb = np.stack([np.full_like(alpha, .96)] * 2 + [np.full_like(alpha, 1.0)], 2)
    img = Image.fromarray((np.dstack([rgb, alpha]) * 255).astype("uint8"), "RGBA")
    img = img.resize((1280, round(img.height * 1280 / img.width)), Image.LANCZOS)
    img.save(f"{out}/snow_{name}.webp", "WEBP", quality=82, method=6)
    print(name, img.size, "alpha %.3f" % alpha.mean())
