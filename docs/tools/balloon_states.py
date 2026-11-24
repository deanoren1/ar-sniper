"""
יוצר מגיליון rig2 של מפלצת בלונים את שלושת המצבים: _b2 (שני בלונים), _b1 (בלון אחד), _b0 (בלי בלונים).

גרסה 3 (במקום חיתוך הבלונים מתוך הציור, שהשאיר חצאי בלונים ושאריות):
  1. b0: מוחקים את כל אשכול הבלונים, החוטים והקרחונים שמעל הראש. הגוף נמצא ע"י opening
     (מסיר חוטים וקרחונים דקים), וכל מה שמעל הגוף ובתחום האשכול נמחק.
  2. b1/b2: על b0 מדביקים בלון שלם ונקי (הבלון הקדמי מפריים עמידה מקדימה, שלא מוסתר בכלל),
     במקום של האשכול המקורי, עם חוט חדש אל הקשר שעל הראש. ב-b2 עוד בלון מאחוריו משמאל.
פריימי מוות (שורה 5 כולה, שורה 6 עמודות 3-5) נשארים כמו במקור בכל המצבים.

שימוש: python3 balloon_states.py <sheet.webp> <out_base>   ->  <out_base>_b2.webp, _b1.webp, _b0.webp
"""
import sys
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi

ROWS, COLS = 9, 6
DEATH = {(5, c) for c in range(6)} | {(6, 3), (6, 4), (6, 5)}

# הבלון הקדמי בתא (2,0): מרכז, רדיוסים, ואזור הקשר שמתחתיו (בפיקסלים של תא 288x327)
STAMP_CELL = (2, 0)
STAMP_C = (139.5, 63.0)
STAMP_R = (39.5, 41.5)
STAMP_KNOT = (130, 100, 150, 119)
ROPE = (176, 98, 46, 255)
ROPE_DARK = (110, 58, 26, 255)


def disk(r):
    y, x = np.ogrid[-r:r + 1, -r:r + 1]
    return x * x + y * y <= r * r


def sat_blue(cell):
    f = cell[..., :3].astype(float) / 255
    r, g, b = f[..., 0], f[..., 1], f[..., 2]
    mx = f.max(-1)
    mn = f.min(-1)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    return (b > r + 0.2) & (b > g) & (sat > 0.45) & (mx > 0.3) & (cell[..., 3] > 40)


def make_stamp(A, cw, ch):
    r, c = STAMP_CELL
    cell = A[r * ch:(r + 1) * ch, c * cw:(c + 1) * cw]
    cx, cy = STAMP_C
    rx, ry = STAMP_R
    yy, xx = np.mgrid[:ch, :cw]
    m = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 < 1
    k0, k1, k2, k3 = STAMP_KNOT
    m |= (xx >= k0) & (xx < k2) & (yy >= k1) & (yy < k3)
    out = cell.copy()
    out[..., 3] = np.where(m, out[..., 3], 0)
    x0, y0 = int(cx - rx - 2), int(cy - ry - 2)
    x1, y1 = int(cx + rx + 3), k3
    st = out[y0:y1, x0:x1]
    # עיגון: מרכז הבלון ונקודת הקשר התחתונה, יחסית לפינה
    return Image.fromarray(st), (cx - x0, cy - y0), ((k0 + k2) / 2 - x0, k3 - 1 - y0)


def body_info(cell, rad=6):
    ch, cw = cell.shape[:2]
    alpha = cell[..., 3] > 40
    opened = ndi.binary_opening(alpha, structure=disk(rad))
    lab, n = ndi.label(opened)
    if n == 0:
        return None
    best, best_size = 0, 0
    for k in range(1, n + 1):
        ys, xs = np.nonzero(lab == k)
        if ys.max() < ch * 0.6:
            continue
        if len(ys) > best_size:
            best, best_size = k, len(ys)
    if best == 0:
        return None
    body = lab == best
    ys, xs = np.nonzero(body)
    top = ys.min()
    band = body[top:top + 8]
    hx = np.nonzero(band)[1].mean()
    return body, top, hx


def strip(cell):
    """מחזיר את התא בלי הבלונים, ואת מיקום האשכול המקורי ונקודת הקשר"""
    for rad in (6, 9, 12):
        r = strip_with(cell, rad)
        if r is not None:
            return r
    return None


def strip_with(cell, rad):
    ch, cw = cell.shape[:2]
    info = body_info(cell, rad)
    if info is None:
        return None
    body, top, hx = info
    # הגוף נדבק לבלונים (למשל זוהר): הראש תמיד בחצי התחתון של התא
    if top < ch * 0.33:
        return None
    blue = sat_blue(cell)
    blue[top:] = False
    ys, xs = np.nonzero(blue)
    if len(ys) < 400:
        return None
    cx0, cx1, cy0 = xs.min(), xs.max(), ys.min()
    keep_body = ndi.binary_dilation(body, structure=disk(3))
    yy, xx = np.mgrid[:ch, :cw]
    zone = (xx > cx0 - cw * 0.08) & (xx < cx1 + cw * 0.08) & (yy < top + ch * 0.12)
    out = cell.copy()
    out[..., 3][zone & ~keep_body] = 0
    # הקשר על הראש: הנקודה העליונה של הגוף ליד מרכז הראש
    col = body[:, int(round(hx))]
    ky = int(np.argmax(col)) if col.any() else top
    return out, (cx0, cx1, cy0), (hx, ky + 4), top


def strip_like(cell, ref):
    """גיבוי (פריים זוהר וכו'): חותכים לפי פריים קרוב מאותה שורה, עם מעבר רך בקו החיתוך"""
    ch, cw = cell.shape[:2]
    _, (cx0, cx1, cy0), knot, top = ref
    cut = top + 4
    yy, xx = np.mgrid[:ch, :cw]
    inx = (xx > cx0 - cw * 0.12) & (xx < cx1 + cw * 0.12)
    fade = np.clip((yy - (cut - 14)) / 14.0, 0, 1)
    out = cell.copy()
    a = out[..., 3].astype(float)
    a = np.where(inx, a * fade, a)
    out[..., 3] = a.astype(np.uint8)
    return out, (cx0, cx1, cy0), knot, top


def draw_rope(img, p0, p1):
    d = ImageDraw.Draw(img)
    d.line([p0, p1], fill=ROPE_DARK, width=4)
    d.line([p0, p1], fill=ROPE, width=2)


def with_balloons(stripped, cluster, knot, stamp, count):
    st, (scx, scy), (skx, sky) = stamp
    body = Image.fromarray(stripped)
    cx0, cx1, cy0 = cluster
    # הבלון הקדמי: באמצע האשכול המקורי, הקצה העליון שלו בקצה העליון של האשכול
    fx = (cx0 + cx1) / 2
    fy = cy0 + STAMP_R[1]
    layer = Image.new('RGBA', body.size, (0, 0, 0, 0))
    balloons = []
    if count == 2:
        s = 0.9
        balloons.append((fx - STAMP_R[0] * 1.05, fy + STAMP_R[1] * 0.3, s))
    balloons.append((fx, fy, 1.0))
    for bx, by, s in balloons:
        draw_rope(layer, (bx + (skx - scx) * s, by + (sky - scy) * s), knot)
    for bx, by, s in balloons:
        im = st if s == 1.0 else st.resize((round(st.width * s), round(st.height * s)), Image.LANCZOS)
        layer.alpha_composite(im, (int(round(bx - scx * s)), int(round(by - scy * s))))
    # הגוף מעל השכבה: החוט נכנס מאחורי הקשר שעל הראש
    layer.alpha_composite(body)
    return np.array(layer)


def main():
    src, base = sys.argv[1], sys.argv[2]
    A = np.array(Image.open(src).convert('RGBA'))
    H, W = A.shape[:2]
    cw, ch = W // COLS, H // ROWS
    stamp = make_stamp(A, cw, ch)
    outs = {k: A.copy() for k in (2, 1, 0)}
    report = []
    for row in range(ROWS):
        line = []
        found = {}
        for col in range(COLS):
            if (row, col) not in DEATH:
                found[col] = strip(A[row * ch:(row + 1) * ch, col * cw:(col + 1) * cw])
        for col in range(COLS):
            if (row, col) in DEATH:
                line.append('D')
                continue
            y0, x0 = row * ch, col * cw
            cell = A[y0:y0 + ch, x0:x0 + cw]
            res = found[col]
            tag = 'ok'
            if res is None:
                near = sorted((c for c in found if found[c] is not None), key=lambda c: abs(c - col))
                if not near:
                    line.append('??')
                    continue
                res = strip_like(cell, found[near[0]])
                tag = f'~{near[0]}'
            stripped, cluster, knot, _ = res
            outs[0][y0:y0 + ch, x0:x0 + cw] = stripped
            outs[1][y0:y0 + ch, x0:x0 + cw] = with_balloons(stripped, cluster, knot, stamp, 1)
            outs[2][y0:y0 + ch, x0:x0 + cw] = with_balloons(stripped, cluster, knot, stamp, 2)
            line.append(tag)
        report.append(' '.join(f'{s:>2}' for s in line))
    for k in outs:
        Image.fromarray(outs[k]).save(f'{base}_b{k}.webp', lossless=False, quality=92, method=6)
    print('\n'.join(report))


if __name__ == '__main__':
    main()
