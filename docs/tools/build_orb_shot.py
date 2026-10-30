#!/usr/bin/env python3
"""
Builds the Orb shot art (spinning face + trail) from a green-screen sheet where row 2 is a growing projectile
(round orb on the right, plasma trail streaming to the left).

Outputs into app/src/main/res/drawable-nodpi:
  shot_orb_a_small.webp / shot_orb_a_big.webp   the round orb alone (the game spins it)
  shot_orb_side.webp                            the trail alone, mirrored so the trail points right (orb hole cut out)
and prints the SIDE_ANCHORS entry (cx, cy, dia, trailDeg) for ShotSprites.kt.

Usage: python3 docs/tools/build_orb_shot.py docs/sprites_src/orb_shot/sheet.webp app/src/main/res/drawable-nodpi
"""
import sys
import numpy as np
from PIL import Image, ImageFilter
import matplotlib.colors as mc
from scipy import ndimage

BAND = (440, 645)       # y range of the projectile row in the sheet
FRAME_FOR_TRAIL = 5     # 0-based: the longest trail
FRAME_FOR_ORB = 5


def key(rgb):
    a = np.asarray(rgb).astype(float) / 255
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    green = g - np.maximum(r, b)
    alpha = np.clip(1 - (green - 0.12) / 0.5, 0, 1)
    # semi-transparent edge pixels are a mix of the glow and the green background: take the green back out
    a3 = np.maximum(alpha, 0.05)[..., None]
    bg = np.array([0.0, 1.0, 0.0])
    fgc = np.clip((np.stack([r, g, b], 2) - (1 - alpha)[..., None] * bg) / a3, 0, 1)
    fgc[..., 1] = np.minimum(fgc[..., 1], np.maximum(fgc[..., 0], fgc[..., 2]))
    # this energy is purple/white only: green-cyan tinted pixels are leftover background haze. Fade them and tint purple.
    hsv = mc.rgb_to_hsv(fgc)
    hue = hsv[..., 0] * 360
    haze = ((hue > 95) & (hue < 215) & (hsv[..., 1] > 0.15)).astype(float)
    haze = ndimage.gaussian_filter(haze, 1.0)
    alpha = alpha * (1 - 0.8 * haze)
    br = np.maximum(fgc[..., 0], fgc[..., 2])
    tint = np.stack([br * 0.85, br * 0.25, br], 2)
    fgc = fgc * (1 - haze[..., None]) + tint * haze[..., None]
    return np.dstack([fgc, alpha])


def frames(fg):
    col = fg[BAND[0]:BAND[1]].any(0)
    xs = np.where(col)[0]
    cuts = np.where(np.diff(xs) > 6)[0]
    segs, start = [], xs[0]
    for c in cuts:
        segs.append((start, xs[c]))
        start = xs[c + 1]
    segs.append((start, xs[-1]))
    return [s for s in segs if s[1] - s[0] > 20]


def orb_circle(alpha, x0, x1):
    """centre and radius of the round head: rightmost blob whose vertical extent matches a circle"""
    sub = alpha[BAND[0]:BAND[1], x0:x1 + 1] > 0.5
    xr = np.where(sub.any(0))[0].max()
    best = None
    for R in range(10, 160):
        xc = xr - R
        if xc < 0:
            break
        ys = np.where(sub[:, xc])[0]
        if len(ys) == 0:
            continue
        ext = ys.max() - ys.min()
        err = abs(ext - 2 * R)
        if best is None or err < best[0]:
            best = (err, R, xc, (ys.max() + ys.min()) / 2)
    _, R, xc, yc = best
    return x0 + xc, BAND[0] + yc, R


def main():
    src, out = sys.argv[1], sys.argv[2]
    im = Image.open(src).convert("RGB")
    rgba = key(im)
    fg = rgba[..., 3] > 0.3
    segs = frames(fg)
    print("frames", segs)
    x0, x1 = segs[FRAME_FOR_ORB]
    cx, cy, R = orb_circle(rgba[..., 3], x0, x1)
    print(f"orb centre ({cx:.0f},{cy:.0f}) radius {R}")
    full = Image.fromarray((rgba * 255).astype("uint8"), "RGBA")
    # circular alpha mask with a soft edge
    yy, xx = np.mgrid[0:full.height, 0:full.width]
    d = np.hypot(xx - cx, yy - cy)
    inside = np.clip((R * 0.98 - d) / (R * 0.10), 0, 1)

    # orb alone
    pad = int(R * 1.02)
    box = (int(cx - pad), int(cy - pad), int(cx + pad), int(cy + pad))
    orb = np.asarray(full).astype(float) / 255
    orb[..., 3] *= inside
    orb_img = Image.fromarray((orb * 255).astype("uint8"), "RGBA").crop(box)
    orb_img.save(f"{out}/shot_orb_a_big.webp", "WEBP", quality=92, method=6)
    orb_img.resize((128, 128), Image.LANCZOS).save(f"{out}/shot_orb_a_small.webp", "WEBP", quality=90, method=6)

    # trail alone: this frame, hole where the orb is, mirrored so the trail points to the right
    tr = np.asarray(full).astype(float) / 255
    hole = np.clip((d - R * 0.82) / (R * 0.10), 0, 1)
    tr[..., 3] *= hole
    tx0, tx1 = max(0, x0 - 4), min(full.width, x1 + 6)
    ty0, ty1 = max(int(cy - R * 1.9), BAND[0]), min(int(cy + R * 1.9), BAND[1])
    trail = Image.fromarray((tr * 255).astype("uint8"), "RGBA").crop((tx0, ty0, tx1, ty1)).transpose(Image.FLIP_LEFT_RIGHT)
    trail.save(f"{out}/shot_orb_side.webp", "WEBP", quality=90, method=6)
    ncx = (tx1 - tx0) - (cx - tx0)
    ncy = cy - ty0
    print(f'SIDE_ANCHORS "orb" to floatArrayOf({ncx:.1f}f, {ncy:.1f}f, {2 * R:.1f}f, 0f)   size {trail.size}')


if __name__ == "__main__":
    main()
