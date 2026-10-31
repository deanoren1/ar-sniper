#!/usr/bin/env python3
"""Cuts the static Tundra frame (2340x1080, black background) into 5 HUD pieces (RGBA webp), black outside the frame -> transparent.
Usage: python3 docs/tools/build_tundra_frame.py <frame.png> <outdir>"""
import sys, numpy as np
from PIL import Image
from scipy import ndimage as ndi
src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB"); a = np.asarray(im).astype(float)
mx = a.max(2)
low, _ = ndi.label(mx < 14)
outside = low == low[540, 1170]            # the black area around the frame (slots are enclosed, so they stay opaque)
soft = np.clip(mx / 40.0, 0, 1)
alpha = np.where(ndi.binary_dilation(outside, iterations=3), soft, 1.0)
alpha = np.where(outside, np.minimum(alpha, soft), alpha)
rgba = np.dstack([a / 255, alpha])
full = Image.fromarray((rgba * 255).astype("uint8"), "RGBA")
pieces = {"tl": (0, 0, 560, 200), "tc": (780, 0, 1560, 200), "tr": (1780, 0, 2340, 200),
          "bl": (0, 560, 700, 1080), "br": (1640, 560, 2340, 1080)}
for k, box in pieces.items():
    full.crop(box).save(f"{out}/tundra_hud_{k}.webp", "WEBP", quality=90, method=6)
    print(k, box)
