"""Cut a 6-projectile row into game sprites: shot_<kind>_<world>_big (256) / _small (128).
usage: shots.py <key> <sheet.png> <world> <out_dir>"""
import sys, os
import numpy as np
from PIL import Image
from scipy import ndimage
sys.path.insert(0, os.path.dirname(__file__))
from rig2build import key
NAMES = ['orb', 'bomb', 'gas', 'rock', 'spike', 'fire']
k, path, world, out = sys.argv[1:5]
im = np.asarray(Image.open(path).convert('RGB')).astype(np.float32) / 255
rgba = key(im, k)
H, W = rgba.shape[:2]
A = rgba[..., 3] > 0.05
lab, n = ndimage.label(A, structure=np.ones((3, 3)))
com = ndimage.center_of_mass(A, lab, range(1, n + 1))
cw = W / 6
for c, name in enumerate(NAMES):
    own = np.zeros(n + 1, bool)
    for i in range(1, n + 1):
        if int(com[i - 1][1] // cw) == c:
            own[i] = True
    m = own[lab]
    ys, xs = np.where(m)
    if len(xs) == 0:
        print('missing', name); continue
    x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    cell = rgba[y0:y1, x0:x1].copy()
    cell[..., 3] *= m[y0:y1, x0:x1]
    s = max(x1 - x0, y1 - y0)
    sq = np.zeros((s, s, 4), np.float32)
    oy, ox = (s - (y1 - y0)) // 2, (s - (x1 - x0)) // 2
    sq[oy:oy + y1 - y0, ox:ox + x1 - x0] = cell
    pil = Image.fromarray((np.clip(sq, 0, 1) * 255).astype('uint8'), 'RGBA').convert('RGBa')
    for tag, size in (('big', 256), ('small', 128)):
        pil.resize((size, size), Image.LANCZOS).convert('RGBA').save(f'{out}/shot_{name}_{world}_{tag}.webp', 'WEBP', quality=90, method=6)
    print(name, x1 - x0, y1 - y0)
