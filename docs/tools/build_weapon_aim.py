#!/usr/bin/env python3
"""
Cuts the 7-angle x 5-row green-screen Plasma Blaster sheet into a uniform RGBA sheet (weapon_aim.webp).
Columns = aim angle (far left .. far right); rows: 0 idle, 1-3 reload (hand brings the cell), 4 charged/fire glow.
Every output cell is CW x CH, the grip centre sits on the same x for a given column (grid centre), bottoms aligned.
Usage: python3 docs/tools/build_weapon_aim.py <sheet.png> <out.webp>
"""
import sys
import numpy as np
from PIL import Image

CW, CH = 340, 200
src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
a = np.asarray(im).astype(float) / 255
W = im.width
g = a[..., 1] - np.maximum(a[..., 0], a[..., 2])
bg = (g > 0.235) & (a[..., 1] > 0.58)
fg = ~bg

def runs(v, th=3):
    res, s = [], None
    for i, x in enumerate(v):
        if x > th and s is None: s = i
        if x <= th and s is not None: res.append((s, i)); s = None
    if s is not None: res.append((s, len(v)))
    return res

def key(rgb):
    """soft green key with spill removal"""
    r, gg, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    excess = gg - np.maximum(r, b)
    alpha = np.clip(1 - (excess - 0.10) / 0.30, 0, 1)
    alpha = np.where(excess < 0.10, 1, alpha)
    cap = np.maximum(r, b) + 0.02
    rgb2 = rgb.copy(); rgb2[..., 1] = np.minimum(gg, cap)
    return rgb2, alpha

rows = runs(fg.sum(1))
sheet = Image.new("RGBA", (CW * 7, CH * len(rows)), (0, 0, 0, 0))
for ri, (y0, y1) in enumerate(rows):
    segs = runs(fg[y0:y1].sum(0))
    assert len(segs) == 7, (ri, segs)
    for ci, (s, e) in enumerate(segs):
        centre = (W / 7) * (ci + 0.5)
        x0 = int(round(centre - CW / 2)); top = y1 - CH
        crop = np.zeros((CH, CW, 3)); mask = np.zeros((CH, CW))
        for yy in range(CH):
            sy = top + yy
            if sy < 0 or sy >= a.shape[0]: continue
            for_x0, for_x1 = max(x0, 0), min(x0 + CW, W)
            crop[yy, for_x0 - x0:for_x1 - x0] = a[sy, for_x0:for_x1]
            m = np.zeros(CW); lo, hi = max(s - 3, x0), min(e + 3, x0 + CW)
            if hi > lo: m[lo - x0:hi - x0] = 1
            mask[yy] = m
            if sy >= y1 or sy < y0: mask[yy] = 0
        rgb, al = key(crop)
        al = al * mask
        cell = np.dstack([rgb, al])
        sheet.paste(Image.fromarray((cell * 255).astype("uint8"), "RGBA"), (ci * CW, ri * CH))
sheet.save(out, "WEBP", quality=88, method=6)
print(out, sheet.size)
