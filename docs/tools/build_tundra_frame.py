#!/usr/bin/env python3
"""Cuts the static Tundra frame (2340x1080, black background) into 5 HUD pieces (RGBA webp), black outside the frame -> transparent.
Usage: python3 docs/tools/build_tundra_frame.py <slim_frame.png> <outdir> [<top_frame.png>]
The optional top frame (thicker, chains) supplies one full-width top piece (tundra_hud_top, 2340x400); the slim frame supplies the bottom pieces."""
import sys, numpy as np
from PIL import Image
from scipy import ndimage as ndi
src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB"); a = np.asarray(im).astype(float)
mx = a.max(2)
_, _l = None, None
lowl, _n = ndi.label(mx < 14)
sz = ndi.sum(mx < 14, lowl, range(1, _n + 1))
outside = np.isin(lowl, [i + 1 for i, v in enumerate(sz) if v > 1500])            # the black area around the frame (slots are enclosed, so they stay opaque)
soft = np.clip(mx / 40.0, 0, 1)
alpha = np.where(ndi.binary_dilation(outside, iterations=3), soft, 1.0)
alpha = np.where(outside, np.minimum(alpha, soft), alpha)
rgba = np.dstack([a / 255, alpha])
full = Image.fromarray((rgba * 255).astype("uint8"), "RGBA")
pieces = {"bl": (0, 560, 700, 1080), "br": (1640, 560, 2340, 1080), "bm": (900, 940, 1440, 1080)}
if len(sys.argv) > 3:
    t = np.asarray(Image.open(sys.argv[3]).convert("RGB")).astype(float)
    tm = t.max(2)
    tl, tn = ndi.label(tm < 14)
    tsz = ndi.sum(tm < 14, tl, range(1, tn + 1))
    tout = np.isin(tl, [i + 1 for i, v in enumerate(tsz) if v > 1500])
    tsoft = np.clip(tm / 40.0, 0, 1)
    ta = np.where(ndi.binary_dilation(tout, iterations=3), tsoft, 1.0)
    ta = np.where(tout, np.minimum(ta, tsoft), ta)
    Image.fromarray((np.dstack([t / 255, ta]) * 255).astype("uint8"), "RGBA").crop((0, 0, 2340, 400)).save(f"{out}/tundra_hud_top.webp", "WEBP", quality=90, method=6)
    print("top")
for k, box in pieces.items():
    full.crop(box).save(f"{out}/tundra_hud_{k}.webp", "WEBP", quality=90, method=6)
    print(k, box)
