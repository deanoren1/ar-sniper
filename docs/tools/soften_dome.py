#!/usr/bin/env python3
"""Makes the glass dome of the octopus back-view frames more transparent (fade teal/cyan low-saturation glass pixels' alpha).
Usage: python3 docs/tools/soften_dome.py <sheet.webp> <out.webp> <row> [factor=0.5]"""
import sys, colorsys, numpy as np
from PIL import Image
src, out, row = sys.argv[1], sys.argv[2], int(sys.argv[3])
f = float(sys.argv[4]) if len(sys.argv) > 4 else 0.5
im = Image.open(src).convert("RGBA"); a = np.asarray(im).astype(float) / 255
ch = im.height // 9
band = a[row * ch:(row + 1) * ch]
r, g, b, al = band[..., 0], band[..., 1], band[..., 2], band[..., 3]
mx = band[..., :3].max(2); mn = band[..., :3].min(2)
sat = (mx - mn) / np.maximum(mx, 1e-3)
# glass: bluish-cyan (b and g above r), not the purple body (r high)
glass = (b > r + 0.02) & (g > r) & (sat < 0.75) & (al > 0.05)
band[..., 3] = np.where(glass, al * f, al)
a[row * ch:(row + 1) * ch] = band
Image.fromarray((a * 255).astype("uint8"), "RGBA").save(out, "WEBP", quality=90, method=6)
print("glass px", int(glass.sum()))
