#!/usr/bin/env python3
"""
Turns a green-screen smoke image into a soft white RGBA fog (webp) for the game.
White smoke over pure green: pixel = a*smoke + (1-a)*(0,1,0), so red/blue carry the smoke amount (the green background has none).

Usage: python3 docs/tools/build_fog.py <in.png> <out.webp> [width]
"""
import sys
import numpy as np
from PIL import Image

src, out = sys.argv[1], sys.argv[2]
width = int(sys.argv[3]) if len(sys.argv) > 3 else 1280
im = Image.open(src).convert("RGB")
if im.width > width:
    im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
a = np.asarray(im).astype(float) / 255
r, g, b = a[..., 0], a[..., 1], a[..., 2]
m = np.minimum(r, b)                       # smoke amount * smoke brightness
alpha = np.clip(m * 1.08, 0, 1)
bright = np.where(alpha > 0.02, m / np.maximum(alpha, 0.02), 1.0)   # smoke brightness (shading)
bright = np.clip(bright, 0.55, 1.0)
rgb = np.stack([bright * 0.90, bright * 0.96, bright * 1.00], 2)    # slightly cold white for the tundra
rgba = np.dstack([rgb, alpha])
Image.fromarray((rgba * 255).astype("uint8"), "RGBA").save(out, "WEBP", quality=82, method=6)
print(out, im.size, "mean alpha %.2f" % alpha.mean())
