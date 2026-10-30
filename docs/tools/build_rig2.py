#!/usr/bin/env python3
"""
Builds a "rig2" sprite sheet (the new multi-angle format) from the green-screen rows made in ChatGPT.

Usage:
    python3 docs/tools/build_rig2.py <source_dir> <out.webp> [frame_width]

<source_dir> holds these files (4200x700 rows, 6 cells of 700x700, flat #00FF00 background):
    views_main (.png/.webp)  row 1 of the 6x6 sheet: front, 3/4 front, side, 3/4 back, back, side-left   (see VIEWS_*)
    walk_side.png         6 walk frames, side view facing right
    walk_front.png        6 walk frames facing the camera
    walk_back.png         6 walk frames facing away
    side_charge_hit.png   3 charge frames + 3 hit frames, side view
    side_death.png        6 death frames, side view
    front_charge_death.png 3 charge frames + 3 death frames, front view
    front_hit_fire.png    3 hit frames + 3 fire frames, front view

Output sheet: 6 columns x 8 rows, every cell the same size (cropped to the union of all frames, feet on the bottom line):
    row 0 views   row 1 walk side   row 2 walk front   row 3 walk back
    row 4 side: charge x3, hit x3   row 5 side: death x6
    row 6 front: charge x3, death x3   row 7 front: hit x3, fire x3
The game reads this layout in MonsterSprites.loadRig2.
"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage
import matplotlib.colors as mc

CELL = 700
GROUND = 640          # target ground line (y) inside a 700px cell
# (file, source scale about x=350, source ground line). Rows from the main sheet were drawn ~14% larger.
ROWS = [
    ("views_main", 530 / 619.0, 663),
    ("walk_side", 530 / 617.0, 684),
    ("walk_front", 1.0, 640),
    ("walk_back", 1.0, 640),
    ("side_charge_hit", 1.0, 640),
    ("side_death", 1.0, 640),
    ("front_charge_death", 1.0, 640),
    ("front_hit_fire", 1.0, 640),
]
EDGE_MARGIN = 100      # small stray fragments inside this margin from the left/right cell edge are removed
FRAGMENT_AREA = 4000


def key_cell(cell_rgb):
    """Green screen -> RGBA (float 0..1). Glass (blue/cyan hues) is made see-through."""
    a = np.asarray(cell_rgb).astype(float) / 255
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    green = g - np.maximum(r, b)
    alpha = np.clip(1 - (green - 0.12) / 0.5, 0, 1)
    hsv = mc.rgb_to_hsv(a)
    hue = hsv[..., 0] * 360
    glass = (hue > 165) & (hue < 235) & (hsv[..., 1] > 0.22) & (alpha > 0.5)
    alpha[glass] = np.minimum(alpha[glass], 0.55)
    g2 = np.minimum(g, np.maximum(r, b))            # remove green spill
    rgb = np.stack([r, g2, b], 2)
    # stray fragments that leaked in from the neighbouring cell
    labels, n = ndimage.label(alpha > 0.1)
    for i in range(1, n + 1):
        ys, xs = np.where(labels == i)
        if len(ys) < FRAGMENT_AREA and (xs.min() < EDGE_MARGIN or xs.max() > CELL - EDGE_MARGIN):
            alpha[labels == i] = 0
    return np.dstack([rgb, alpha])


def place(rgba, scale, ground_src):
    """Scale about the cell's horizontal centre and move the source ground line to GROUND."""
    img = Image.fromarray((rgba * 255).astype("uint8"), "RGBA")
    if abs(scale - 1.0) > 1e-3:
        size = max(1, round(CELL * scale))
        img = img.convert("RGBa").resize((size, size), Image.LANCZOS).convert("RGBA")
        ground_src = ground_src * scale
    canvas = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    off_x = CELL // 2 - img.width // 2
    off_y = int(round(GROUND - ground_src))
    canvas.alpha_composite(img, (off_x, off_y)) if off_y >= 0 and off_x >= 0 else canvas.paste(img, (off_x, off_y))
    return canvas


ROW_LABELS = [
    "VIEWS: front, 3/4 front, side R, 3/4 back, back, side L",
    "WALK side (facing right), 6-frame loop",
    "WALK toward camera, 6-frame loop",
    "WALK away from camera, 6-frame loop",
    "SIDE: charge up x3, then hit x3",
    "SIDE: defeat x6",
    "FRONT: charge up x3, then defeat x3",
    "FRONT: hit x3, then fire/release x3",
]


def write_reference(sheet, cw, ch, nrows, path):
    """Reference sheet for generating another character with the same poses: green background, a label strip above each row."""
    from PIL import ImageDraw, ImageFont
    import matplotlib
    import os
    font_path = os.path.join(os.path.dirname(matplotlib.__file__), "mpl-data", "fonts", "ttf", "DejaVuSans-Bold.ttf")
    font = ImageFont.truetype(font_path, 22)
    strip = 40
    ref = Image.new("RGBA", (cw * 6, (ch + strip) * nrows), (0, 255, 0, 255))
    d = ImageDraw.Draw(ref)
    for r in range(nrows):
        y = r * (ch + strip)
        d.rectangle((0, y, cw * 6, y + strip - 1), fill=(20, 60, 20, 255))
        d.text((12, y + 7), f"ROW {r + 1}  -  {ROW_LABELS[r]}", font=font, fill=(255, 255, 255, 255))
        ref.alpha_composite(sheet.crop((0, r * ch, cw * 6, (r + 1) * ch)), (0, y + strip))
    ref.convert("RGB").save(path)


def main():
    src, out = sys.argv[1], sys.argv[2]
    frame_w = int(sys.argv[3]) if len(sys.argv) > 3 else 352
    rows = []
    for name, scale, ground in ROWS:
        import os
        path = next(f"{src}/{name}{e}" for e in (".png", ".webp") if os.path.exists(f"{src}/{name}{e}"))
        im = Image.open(path).convert("RGB")
        w, h = im.size
        if (w, h) != (CELL * 6, CELL):
            if name == "views_main" and w == CELL * 6:
                im = im.crop((0, 0, w, CELL))
            else:
                sys.exit(f"{name}: expected {CELL*6}x{CELL}, got {w}x{h}")
        rows.append([place(key_cell(im.crop((c * CELL, 0, (c + 1) * CELL, CELL))), scale, ground) for c in range(6)])

    # union box of every frame; the bottom is fixed just under the ground line so feet stay anchored
    x0, y0, x1 = CELL, CELL, 0
    for row in rows:
        for f in row:
            bb = f.getchannel("A").point(lambda v: 255 if v > 12 else 0).getbbox()
            if bb:
                x0, y0, x1 = min(x0, bb[0]), min(y0, bb[1]), max(x1, bb[2])
    y1 = GROUND + 6
    x0, y0, x1 = max(0, x0 - 4), max(0, y0 - 4), min(CELL, x1 + 4)
    k = frame_w / (x1 - x0)
    cw, ch = frame_w, round((y1 - y0) * k)
    sheet = Image.new("RGBA", (cw * 6, ch * len(rows)), (0, 0, 0, 0))
    for r, row in enumerate(rows):
        for c, f in enumerate(row):
            crop = f.crop((x0, y0, x1, y1)).convert("RGBa").resize((cw, ch), Image.LANCZOS).convert("RGBA")
            sheet.paste(crop, (c * cw, r * ch))
    sheet.save(out, "WEBP", quality=88, method=6)
    if len(sys.argv) > 4:
        write_reference(sheet, cw, ch, len(rows), sys.argv[4])
    print(f"union box x {x0}-{x1}, y {y0}-{y1}; frame {cw}x{ch}; sheet {sheet.size}; decoded ~{sheet.width*sheet.height*4/1e6:.1f} MB")


if __name__ == "__main__":
    main()
