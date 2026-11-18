"""Build a rig2 sheet (6 cols x 9 rows) from four ChatGPT 6x2 sheets (r1..r4).

r1: top = turnaround 6 views, bottom = side walk/fly cycle
r2: top = walk toward camera, bottom = walk away
r3: top = front charge(0-2) + front hit(3-5), bottom = side death 6 (last = ghost)
r4: top = side charge(0-2) + side hit(3-5), bottom = front windup(0-2), release(3), projectile(4), after(5)

rig2 rows (game format):
0 views, 1 walk side, 2 walk front, 3 walk back, 4 side charge3+hit3, 5 side death6,
6 front charge3 + death(last 3), 7 front hit3 + fire3, 8 front charge5 + release1

usage: rig2build.py <key: green|blue|magenta> <r1> <r2> <r3> <r4> <out.webp> <preview.jpg>
"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage

STAND = 540   # body height of the standing reference pose inside a work cell
CELL = 760
GROUND = 700


def key(a, kind):
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    if kind == 'green':
        d = g - np.maximum(r, b)
    elif kind == 'blue':
        d = b - np.maximum(r, g)
    else:  # magenta
        d = np.minimum(r, b) - g
    alpha = np.clip(1 - (d - 0.12) / 0.45, 0, 1)
    out = a.copy()
    # despill: pull the key channel(s) back toward the others
    if kind == 'green':
        out[..., 1] = np.minimum(g, np.maximum(r, b))
    elif kind == 'blue':
        out[..., 2] = np.minimum(b, np.maximum(r, g))
    else:
        s = np.clip(np.minimum(r, b) - g, 0, 1)
        out[..., 0] = r - s * 0.8
        out[..., 2] = b - s * 0.8
    # clean: remove tiny isolated specks, soften edge by one pixel
    solid = alpha > 0.5
    lab, n = ndimage.label(solid)
    if n:
        sizes = ndimage.sum(solid, lab, range(n + 1))
        tiny = np.isin(lab, np.where(sizes < 30)[0]) & (lab > 0)
        alpha[tiny] = 0
    alpha = np.where(ndimage.binary_erosion(alpha > 0.05, iterations=1), alpha, alpha * 0.5)
    return np.dstack([out, alpha])


def cells(path, kind):
    """Cut a 6x2 sheet into frames. Each connected piece goes to the cell its centre lies in,
    so a tail or effect that crosses the grid line stays whole and never leaves a stray sliver in the neighbour."""
    im = np.asarray(Image.open(path).convert('RGB')).astype(np.float32) / 255
    rgba = key(im, kind)
    H, W = rgba.shape[:2]
    cwid = W / 6
    res = []
    warn = []
    for r in range(2):
        y0, y1 = r * H // 2, (r + 1) * H // 2
        band = rgba[y0:y1]
        A = band[..., 3] > 0.04
        lab, n = ndimage.label(A, structure=np.ones((3, 3)))
        owner = np.full(n + 1, -1)
        if n:
            objs = ndimage.find_objects(lab)
            com = ndimage.center_of_mass(A, lab, range(1, n + 1))
            for i, sl in enumerate(objs, start=1):
                width = sl[1].stop - sl[1].start
                if width > 1.35 * cwid:
                    owner[i] = -2          # spans several cells (merged glow): split it by the grid
                else:
                    owner[i] = min(5, int(com[i - 1][1] // cwid))
        row = []
        cols = np.arange(W)
        gridcol = np.minimum(5, (cols // cwid).astype(int))
        for c in range(6):
            own = owner[lab]
            mine = (own == c) | ((own == -2) & (gridcol[None, :] == c))
            ys_, xs_ = np.where(mine)
            if len(xs_) == 0:
                cell = np.zeros((y1 - y0, int(cwid), 4), np.float32)
                row.append(dict(img=cell, bh=1, bottom=cell.shape[0] - 1, cx=cell.shape[1] / 2))
                continue
            L, R = xs_.min(), xs_.max() + 1
            if R - L > 1.6 * cwid:
                warn.append((r, c, int(R - L)))
            cell = band[:, L:R].copy()
            cell[..., 3] *= mine[:, L:R]
            solid = (cell[..., 3] > 0.5)
            lab2, n2 = ndimage.label(solid)
            if n2:
                sizes = ndimage.sum(solid, lab2, range(n2 + 1))
                body = lab2 == int(np.argmax(sizes[1:]) + 1)
            else:
                body = solid
            ys, xs = np.where(body)
            if len(ys) == 0:
                row.append(dict(img=cell, bh=1, bottom=cell.shape[0] - 1, cx=cell.shape[1] / 2))
                continue
            row.append(dict(img=cell, bh=ys.max() - ys.min() + 1, bottom=ys.max(), cx=(xs.min() + xs.max()) / 2))
        res.append(row)
    return res, warn


def build(kind, paths, out_path, prev_path, mirror_side=False):
    S = [cells(p, kind) for p in paths]
    warns = [(i + 1, w) for i, (_, ws) in enumerate(S) for w in ws]
    S = [s for s, _ in S]
    # one scale per sheet, from its first standing-ish pose (top row, frame 0)
    raw = [STAND / max(40, s[0][0]['bh']) for s in S]
    # ChatGPT keeps one character scale across the 4 sheets (within ~5%), but the first pose of sheets 3/4 is a crouch,
    # so its height would inflate those sheets. One scale for all: from sheets 1 and 2 (upright front poses)
    g = (raw[0] + raw[1]) / 2
    scale = [g] * 4
    # ground per row = median of body bottoms
    def place(si, r, i):
        f = S[si][r][i]
        s = scale[si]
        pil = Image.fromarray((np.clip(f['img'], 0, 1) * 255).astype('uint8'), 'RGBA')
        gnd = float(np.median([q['bottom'] for q in S[si][r]]))
        w, h = pil.size
        pil = pil.convert('RGBa').resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS).convert('RGBA')
        canvas = Image.new('RGBA', (CELL, CELL), (0, 0, 0, 0))
        ox = int(round(CELL / 2 - f['cx'] * s))
        oy = int(round(GROUND - gnd * s))
        canvas.alpha_composite(pil, (max(0, ox), max(0, oy)), (max(0, -ox), max(0, -oy)))
        return canvas
    r1, r2, r3, r4 = 0, 1, 2, 3
    rows = [
        [place(r1, 0, i) for i in range(6)],
        [place(r1, 1, i) for i in range(6)],
        [place(r2, 0, i) for i in range(6)],
        [place(r2, 1, i) for i in range(6)],
        [place(r4, 0, i) for i in range(6)],
        [place(r3, 1, i) for i in range(6)],
        [place(r3, 0, i) for i in range(3)] + [place(r3, 1, i) for i in (3, 4, 5)],
        [place(r3, 0, i) for i in (3, 4, 5)] + [place(r4, 1, i) for i in (3, 4, 5)],
        [place(r4, 1, i) for i in (0, 1, 1, 2, 2)] + [place(r4, 1, 3)],
    ]
    x0, y0, x1, y1 = CELL, CELL, 0, 0
    for row in rows:
        for f in row:
            bb = f.getchannel('A').point(lambda v: 255 if v > 12 else 0).getbbox()
            if bb:
                x0, y0, x1, y1 = min(x0, bb[0]), min(y0, bb[1]), max(x1, bb[2]), max(y1, bb[3])
    y1 = max(y1, GROUND + 6)
    x0, y0, x1 = max(0, x0 - 4), max(0, y0 - 4), min(CELL, x1 + 4)
    fw = 288
    k = fw / (x1 - x0)
    cw, ch = fw, round((y1 - y0) * k)
    sheet = Image.new('RGBA', (cw * 6, ch * 9), (0, 0, 0, 0))
    for r, row in enumerate(rows):
        for c, f in enumerate(row):
            sheet.paste(f.crop((x0, y0, x1, y1)).convert('RGBa').resize((cw, ch), Image.LANCZOS).convert('RGBA'), (c * cw, r * ch))
    sheet.save(out_path, 'WEBP', quality=88, method=6)
    prev = Image.new('RGBA', sheet.size, (70, 80, 100, 255))
    prev.alpha_composite(sheet)
    p = prev.convert('RGB')
    p.thumbnail((1200, 2400))
    p.save(prev_path, quality=85)
    # leftover key colour check (QA): fraction of visible pixels still close to the key colour
    arr = np.asarray(sheet).astype(np.float32) / 255
    vis = arr[..., 3] > 0.5
    r_, g_, b_ = arr[..., 0], arr[..., 1], arr[..., 2]
    if kind == 'green':
        left = (g_ - np.maximum(r_, b_) > 0.35) & vis
    elif kind == 'blue':
        left = (b_ - np.maximum(r_, g_) > 0.35) & vis
    else:
        left = (np.minimum(r_, b_) - g_ > 0.35) & vis
    print('sheet', sheet.size, 'cell', cw, ch, 'scales', [round(s, 3) for s in scale],
          'key-leftover px', int(left.sum()), 'cell-overflow warnings', warns)


if __name__ == '__main__':
    k, a, b, c, d, out, prev = sys.argv[1:8]
    build(k, [a, b, c, d], out, prev)
