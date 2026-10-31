#!/usr/bin/env python3
"""
Cuts the 7-angle x 5-row green-screen Plasma Blaster sheet (4900x3500, 700px cells) into an RGBA sheet (weapon_aim.webp).
Columns = aim angle (far left .. far right); rows: 0 idle, 1-3 reload, 4 charged/fire glow.
Usage: python3 docs/tools/build_weapon_aim.py <sheet> <out.webp> [cell]
"""
import sys
import numpy as np
from PIL import Image

src, out = sys.argv[1], sys.argv[2]
CELL = int(sys.argv[3]) if len(sys.argv) > 3 else 320
im = Image.open(src).convert("RGB")
S = im.width // 7
INSET = 10
a = np.asarray(im).astype(float) / 255

def key(rgb):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    excess = g - np.maximum(r, b)
    alpha = np.clip(1 - (excess - 0.12) / 0.30, 0, 1)
    alpha = np.where(excess < 0.12, 1, alpha)
    rgb2 = rgb.copy(); rgb2[..., 1] = np.minimum(g, np.maximum(r, b) + 0.02)
    return np.dstack([rgb2, alpha])

def feather(img):
    """soft alpha ramp on the left/right/top cell edges so arms that run off the cell fade instead of ending in a hard cut"""
    arr = np.asarray(img).astype(float)
    n = arr.shape[0]
    ramp = lambda d, w: np.clip(d / w, 0, 1) ** 2 * (3 - 2 * np.clip(d / w, 0, 1))
    idx = np.arange(n)
    fx = np.minimum(ramp(idx, n * 0.10), ramp(n - 1 - idx, n * 0.10))
    fy = ramp(idx, n * 0.07)
    arr[..., 3] *= fx[None, :] * fy[:, None]
    return Image.fromarray(arr.astype("uint8"), "RGBA")

sheet = Image.new("RGBA", (CELL * 7, CELL * 5), (0, 0, 0, 0))
muzzle = []
for ri in range(5):
    for ci in range(7):
        c = a[ri * S + INSET:(ri + 1) * S - INSET, ci * S + INSET:(ci + 1) * S - INSET]
        k = key(c)
        img = Image.fromarray((k * 255).astype("uint8"), "RGBA").resize((CELL, CELL), Image.LANCZOS)
        img = feather(img)
        sheet.paste(img, (ci * CELL, ri * CELL))
        if ri == 0:
            al = np.asarray(img)[..., 3] > 128
            ys = np.where(al.any(1))[0]; y0 = ys[0]
            xs = np.where(al[y0:y0 + 12].any(0))[0]
            muzzle.append((round(xs.mean() / CELL, 3), round((y0 + 22) / CELL, 3)))
sheet.save(out, "WEBP", quality=88, method=6)
print(out, sheet.size, "muzzle(row0):", muzzle)
