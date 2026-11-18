"""Build the big-boss clips of a world from ChatGPT boss sheets.

input dir must hold <world>_boss_{idle,prepare,attack1,attack2,hit,death}.png (3x2 frames each, flat key colour)
and <world>_boss_bg.png (empty arena background).
output: boss_<world>_<clip>.mp4 (864x496, 24 fps, AAC) + boss_<world>_tracks.txt

usage: bossbuild.py <world> <key: green|blue|magenta> <gem: green|orange|teal|red> <in_dir> <out_dir> <sfx_dir>
"""
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

sys.path.insert(0, os.path.dirname(__file__))
from rig2build import key  # noqa: E402

W, H, FPS = 864, 496, 24
BOSS_H = 0.86 * H          # height of the boss body in the idle pose
FEET_Y = H - 6

# clip durations (ms) and impact moments: must match BossClip / BossAttack in IceRealm.kt
DUR = dict(intro=6667, breath=5042, prepare=5042, attack1=4042, attack3=4042, attack4=4042, attack5=4042,
           hit=2000, death=4042)
IMPACT = dict(attack1=2083, attack3=1750, attack4=1750, attack5=2625)



def hold(k):
    """each drawn pose holds most of its time and only cross-fades briefly into the next (no ghosting)"""
    if k < 0.72:
        return 0.0
    k = (k - 0.72) / 0.28
    return k * k * (3 - 2 * k)


def frames_of(path, kind):
    im = np.asarray(Image.open(path).convert('RGB')).astype(np.float32) / 255
    rgba = key(im, kind)
    h, w = rgba.shape[:2]
    out = []
    for r in range(2):
        for c in range(3):
            out.append(rgba[r * h // 2:(r + 1) * h // 2, c * w // 3:(c + 1) * w // 3].copy())
    return out


def body_box(f):
    a = f[..., 3] > 0.5
    lab, n = ndimage.label(a)
    if not n:
        return 0, 0, f.shape[1], f.shape[0]
    sizes = ndimage.sum(a, lab, range(n + 1))
    big = lab == int(np.argmax(sizes[1:]) + 1)
    ys, xs = np.where(big)
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


class Boss:
    def __init__(self, world, kind, gem, d):
        self.world, self.gem = world, gem
        self.sheets = {k: frames_of(f'{d}/{world}_boss_{k}.png', kind)
                       for k in ('idle', 'prepare', 'attack1', 'attack2', 'hit', 'death')}
        bg = Image.open(f'{d}/{world}_boss_bg.png').convert('RGB')
        # cover-crop to 864x496
        s = max(W / bg.width, H / bg.height)
        bg = bg.resize((round(bg.width * s), round(bg.height * s)), Image.LANCZOS)
        L, T = (bg.width - W) // 2, (bg.height - H) // 2
        self.bg = np.asarray(bg.crop((L, T, L + W, T + H))).astype(np.float32) / 255
        x0, y0, x1, y1 = body_box(self.sheets['idle'][0])
        self.scale = BOSS_H / (y1 - y0)
        self.cx = (x0 + x1) / 2
        self.bottom = y1
        self.cache = {}

    def sprite(self, sheet, i):
        """the frame placed in video coordinates: (rgba HxWx4 array at video size)"""
        k = (sheet, i)
        if k in self.cache:
            return self.cache[k]
        f = self.sheets[sheet][i]
        pil = Image.fromarray((np.clip(f, 0, 1) * 255).astype('uint8'), 'RGBA')
        pil = pil.convert('RGBa').resize((round(pil.width * self.scale), round(pil.height * self.scale)), Image.LANCZOS).convert('RGBA')
        canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        ox = round(W / 2 - self.cx * self.scale)
        oy = round(FEET_Y - self.bottom * self.scale)
        canvas.alpha_composite(pil, (max(0, ox), max(0, oy)), (max(0, -ox), max(0, -oy)))
        arr = np.asarray(canvas).astype(np.float32) / 255
        self.cache[k] = arr
        return arr

    def blend(self, sheet, t):
        """frame position t (float) inside a 6-frame sheet: cross-fade of neighbours"""
        t = max(0.0, min(5.0, t))
        i0 = int(t)
        i1 = min(5, i0 + 1)
        k = t - i0
        a = self.sprite(sheet, i0)
        if k < 0.02 or i1 == i0:
            return a
        b = self.sprite(sheet, i1)
        k = hold(k)
        if k <= 0:
            return a
        return a * (1 - k) + b * k

    def blobs(self, sheet, i):
        m = self.gem_mask(sheet, i)
        lab, n = ndimage.label(ndimage.binary_dilation(m, iterations=6))
        if not n:
            return []
        sizes = ndimage.sum(m, lab, range(n + 1))
        return [ndimage.center_of_mass(m, lab, k)[::-1] for k in range(1, n + 1) if sizes[k] > 25]

    def gem_mask(self, sheet, i):
        f = self.sprite(sheet, i)
        rgb = f[..., :3]
        r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
        solid = f[..., 3] > 0.8
        if self.gem == 'green':
            m = (g > 0.85) & (g > r * 1.25) & (g > b * 1.6)
        elif self.gem == 'teal':
            m = (g > 0.8) & (b > 0.7) & (r < 0.6 * np.minimum(g, b))
        elif self.gem == 'orange':
            m = (r > 0.9) & (g > 0.4) & (g < 0.8) & (b < 0.4)
        else:
            m = (r > 0.85) & (r > g * 1.8) & (r > b * 1.8)
        return ndimage.binary_opening(m & solid, iterations=1)

    def gems(self, sheet, i):
        """5 weak points in video px: head, right shoulder (screen left), left shoulder, right fist (screen left), left fist"""
        f = self.sprite(sheet, i)
        rgb = f[..., :3]
        r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
        solid = f[..., 3] > 0.8
        if self.gem == 'green':
            m = (g > 0.85) & (g > r * 1.25) & (g > b * 1.6)
        elif self.gem == 'teal':
            m = (g > 0.8) & (b > 0.7) & (r < 0.6 * np.minimum(g, b))
        elif self.gem == 'orange':
            m = (r > 0.9) & (g > 0.4) & (g < 0.8) & (b < 0.4)
        else:
            m = (r > 0.85) & (r > g * 1.8) & (r > b * 1.8)
        m = m & solid
        m = ndimage.binary_opening(m, iterations=1)
        lab, n = ndimage.label(ndimage.binary_dilation(m, iterations=3))
        if n < 5:
            return None
        sizes = ndimage.sum(m, lab, range(n + 1))
        order = np.argsort(sizes[1:])[::-1][:5] + 1
        pts = [ndimage.center_of_mass(m, lab, k)[::-1] for k in order]   # (x, y)
        rad = [max(34.0, np.sqrt(sizes[k] / np.pi) * 1.8) for k in order]
        pts = sorted(zip(pts, rad), key=lambda p: p[0][1])
        head = min(pts[:3], key=lambda p: abs(p[0][0] - W / 2))
        rest = [p for p in pts if p is not head]
        upper = sorted(rest, key=lambda p: p[0][1])[:2]
        lower = sorted(rest, key=lambda p: p[0][1])[2:]
        upper.sort(key=lambda p: p[0][0])
        lower.sort(key=lambda p: p[0][0])
        return [head, upper[0], upper[1], lower[0], lower[1]]


def render(boss, name, n_frames, fn, sfx, out_dir, sfx_dir):
    """fn(i, t_ms) -> (sheet, pos, dict(shake, flash, zoom, fade, glow)) ; writes mp4 with sound effects [(ms, file, vol)]"""
    tmp = f'{out_dir}/_{name}.mp4'
    p = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}',
                          '-r', str(FPS), '-i', '-', '-c:v', 'libx264', '-preset', 'slow', '-crf', '20', '-pix_fmt', 'yuv420p',
                          '-profile:v', 'high', tmp], stdin=subprocess.PIPE)
    for i in range(n_frames):
        t = i * 1000.0 / FPS
        sheet, pos, fx = fn(i, t)
        spr = boss.blend(sheet, pos) if sheet else np.zeros((H, W, 4), np.float32)
        zoom = fx.get('zoom', 1.0)
        if zoom != 1.0:
            pil = Image.fromarray((spr * 255).astype('uint8'), 'RGBA')
            zw, zh = round(W * zoom), round(H * zoom)
            pil = pil.resize((zw, zh), Image.BILINEAR)
            L, T = (zw - W) // 2, int((zh - H) * 0.85)
            pil = pil.crop((L, T, L + W, T + H))
            spr = np.asarray(pil).astype(np.float32) / 255
        lift = fx.get('lift', 0)
        if lift:
            spr = np.roll(spr, int(lift), axis=0)
            if lift > 0:
                spr[:int(lift)] = 0
        vis = fx.get('fade', 1.0)
        a = spr[..., 3:4] * vis
        frame = boss.bg * fx.get('bgdim', 1.0) * (1 - a) + spr[..., :3] * a
        glow = fx.get('glow', 0.0)
        if glow > 0:
            frame = frame + glow * np.array(fx.get('glowcol', (0.4, 1.0, 0.6)))[None, None, :] * a
        fl = fx.get('flash', 0.0)
        if fl > 0:
            fc = fx.get('flashat')
            if fc is None:
                frame = frame * (1 - fl) + fl
            else:
                yy, xx = np.mgrid[0:H, 0:W]
                rr = np.exp(-((xx - fc[0]) ** 2 + (yy - fc[1]) ** 2) / (2 * 45.0 ** 2))[..., None]
                frame = frame + fl * rr
        sh = fx.get('shake', 0.0)
        if sh > 0:
            dx = int(round(np.sin(t * 0.09) * 9 * sh))
            dy = int(round(np.cos(t * 0.11) * 6 * sh))
            frame = np.roll(np.roll(frame, dx, axis=1), dy, axis=0)
        p.stdin.write((np.clip(frame, 0, 1) * 255).astype('uint8').tobytes())
    p.stdin.close()
    p.wait()
    dur = n_frames / FPS
    out = f'{out_dir}/boss_{boss.world}_{name}.mp4'
    if sfx:
        args = ['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp]
        for _, f, _ in sfx:
            args += ['-i', f'{sfx_dir}/{f}']
        parts = []
        for k, (ms, _, vol) in enumerate(sfx):
            parts.append(f'[{k + 1}:a]aresample=44100,aformat=channel_layouts=stereo,volume={vol},adelay={int(ms)}|{int(ms)}[a{k}]')
        mix = ''.join(f'[a{k}]' for k in range(len(sfx)))
        flt = ';'.join(parts) + f';{mix}amix=inputs={len(sfx)}:normalize=0,apad,atrim=0:{dur:.3f}[aout]'
        args += ['-filter_complex', flt, '-map', '0:v', '-map', '[aout]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '96k', '-shortest', out]
    else:
        args = ['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-f', 'lavfi', '-i', 'anullsrc=r=44100:cl=stereo',
                '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '64k', '-shortest', out]
    subprocess.run(args, check=True)
    os.remove(tmp)


def nf(ms):
    return int(round(ms / 1000 * FPS))


def build(world, kind, gem, d, out_dir, sfx_dir):
    boss = Boss(world, kind, gem, d)
    os.makedirs(out_dir, exist_ok=True)
    gc = dict(green=(0.4, 1.0, 0.5), orange=(1.0, 0.6, 0.2), teal=(0.3, 1.0, 0.9), red=(1.0, 0.3, 0.3))[gem]

    # ---- tracks: weak points over Breath and Prepare, every 2 frames ----
    base = boss.gems('idle', 0)
    if base is None:
        raise SystemExit('no weak points found in the idle frame: check the gem colour')

    def track(sheet):
        """follow the 5 idle weak points through a sheet: each takes the nearest detected glow, else keeps its place"""
        prev = [p[0] for p in base]
        out = []
        for i in range(6):
            blobs = boss.blobs(sheet, i)
            cur = []
            for (px, py) in prev:
                best = None
                bd = 85.0
                for (bx, by) in blobs:
                    d = ((bx - px) ** 2 + (by - py) ** 2) ** 0.5
                    if d < bd:
                        best, bd = (bx, by), d
                cur.append(best or (px, py))
            out.append([(c, 0) for c in cur])
            prev = cur
        return out
    idle_g, prep_g = track('idle'), track('prepare')

    def breath_pos(t):
        # ping-pong over the 6 idle frames, one full cycle per clip
        u = (t / DUR['breath']) * 2
        return (u if u <= 1 else 2 - u) * 5

    def prep_pos(t):
        return min(5.0, t / (DUR['prepare'] * 0.8) * 5)

    def interp(gl, pos):
        i0 = int(pos)
        i1 = min(5, i0 + 1)
        k = hold(pos - i0)
        return [((a[0][0] * (1 - k) + b[0][0] * k), (a[0][1] * (1 - k) + b[0][1] * k)) for a, b in zip(gl[i0], gl[i1])]

    lines = []
    for name, gl, posf in (('breath', idle_g, breath_pos), ('prepare', prep_g, prep_pos)):
        vals = []
        for s in range(0, nf(DUR[name]) + 1, 2):
            for x, y in interp(gl, posf(s * 1000 / FPS)):
                vals += [int(round(x)), int(round(y))]
        lines.append(f'{name}: ' + ' '.join(map(str, vals)))
    rad = [round(p[1], 1) for p in base]
    sh = abs(base[1][0][0] - base[2][0][0]) + 0.0
    lines.append('RADIUS: ' + ' '.join(map(str, rad)))
    lines.append(f'SHOULDERS: {sh:.1f}')
    open(f'{out_dir}/boss_{world}_tracks.txt', 'w').write('\n'.join(lines) + '\n')

    # ---- clips ----
    def idle_fx(i, t):
        return 'idle', breath_pos(t), {}
    render(boss, 'breath', nf(DUR['breath']), idle_fx, [], out_dir, sfx_dir)

    def prep_fx(i, t):
        k = min(1.0, t / DUR['prepare'])
        return 'prepare', prep_pos(t), dict(glow=0.35 * k * (0.6 + 0.4 * np.sin(t * 0.02)), glowcol=gc, shake=0.25 * k)
    render(boss, 'prepare', nf(DUR['prepare']), prep_fx, [(300, 'boss_attack_windup.wav', 0.9), (2600, 'sfx_boss_roar.wav', 0.8)], out_dir, sfx_dir)

    def attack(sheet, impact, zoom_punch=0.0, flash=0.6, flashcol=None):
        def fx(i, t):
            # frames 0..3 lead up to the impact (frame 3 = the hit), then recover over 4..5 and back toward idle
            if t <= impact:
                pos = t / impact * 3
            else:
                pos = 3 + min(2.0, (t - impact) / 700 * 2)
            e = dict()
            since = t - impact
            if 0 <= since < 400:
                e['shake'] = 1.2 * (1 - since / 400)
                e['flash'] = flash * (1 - since / 250) if since < 250 else 0
                if zoom_punch:
                    e['zoom'] = 1 + zoom_punch * (1 - since / 400)
            if t > impact + 1400:
                # last part: settle back to the idle pose (cross to idle frame 0)
                return 'idle', 0, e
            return sheet, pos, e
        return fx
    hit_sfx = 'boss_hit_heavy.wav'
    render(boss, 'attack1', nf(DUR['attack1']), attack('attack1', IMPACT['attack1'], 0.06, 0.3),
           [(IMPACT['attack1'] - 500, 'boss_attack_windup.wav', 0.8), (IMPACT['attack1'], hit_sfx, 1.0)], out_dir, sfx_dir)
    render(boss, 'attack3', nf(DUR['attack3']), attack('attack1', IMPACT['attack3'], 0.12, 0.4),
           [(IMPACT['attack3'] - 400, 'sfx_telegraph.wav', 0.8), (IMPACT['attack3'], 'sfx_boom.wav', 1.0)], out_dir, sfx_dir)
    render(boss, 'attack4', nf(DUR['attack4']), attack('attack1', IMPACT['attack4'], 0.18, 0.3),
           [(IMPACT['attack4'] - 600, 'sfx_boss_roar.wav', 0.6), (IMPACT['attack4'], 'explosion_arcade.wav', 1.0)], out_dir, sfx_dir)

    def ranged(i, t):
        impact = IMPACT['attack5']
        # 0..2 charge (release on frame 2), 3..5 the blast flying at the camera, reaching it at the impact
        if t < 1200:
            pos = t / 1200 * 2
        elif t <= impact:
            pos = 2 + (t - 1200) / (impact - 1200) * 3
        else:
            pos = 5
        e = {}
        if 1200 <= t <= impact:
            e['zoom'] = 1 + 0.12 * (t - 1200) / (impact - 1200)
        since = t - impact
        if 0 <= since < 350:
            e['flash'] = 0.4 * (1 - since / 350)
            e['shake'] = 1.0
        if t > impact + 900:
            return 'idle', 0, {}
        return 'attack2', pos, e
    render(boss, 'attack5', nf(DUR['attack5']), ranged,
           [(900, 'sfx_launch.wav', 1.0), (IMPACT['attack5'], 'sfx_boom.wav', 0.9)], out_dir, sfx_dir)

    names = ['hit_head', 'hit_rshoulder', 'hit_lshoulder', 'hit_rhand', 'hit_lhand']
    for z, nm in enumerate(names):
        gx, gy = base[z][0]

        def hfx(i, t, gx=gx, gy=gy):
            # flinch: hit frames 0..5 over the first 1.4 s, then back to idle
            if t > 1400:
                return 'idle', 0, {}
            e = {}
            if t < 260:
                e['flash'] = 0.9 * (1 - t / 260)
                e['flashat'] = (gx, gy)
                e['shake'] = 0.6
            return 'hit', t / 1400 * 5, e
        render(boss, nm, nf(DUR['hit']), hfx, [(0, 'sfx_crack.wav', 1.0), (120, 'monster_growl.wav', 0.7)], out_dir, sfx_dir)

    def dfx(i, t):
        e = {}
        pos = min(5.0, t / 3000 * 5)
        if t < 2400:
            e['shake'] = 0.5 + 0.5 * (t / 2400)
        if 2300 < t < 2700:
            e['flash'] = 0.8 * (1 - (t - 2300) / 400)
        if t > 3000:
            e['fade'] = max(0.0, 1 - (t - 3000) / 900)
        return 'death', pos, e
    render(boss, 'death', nf(DUR['death']), dfx,
           [(0, 'sfx_boss_roar.wav', 0.9), (900, 'sfx_crack.wav', 1.0), (2300, 'explosion_arcade.wav', 1.0)], out_dir, sfx_dir)

    def ifx(i, t):
        # dark arena -> energy glow -> the boss rises into view -> roar (prepare frames) -> idle
        if t < 1500:
            return None, 0, dict(bgdim=0.35 + 0.65 * t / 1500, flash=0.0)
        if t < 3600:
            k = (t - 1500) / 2100
            k = k * k * (3 - 2 * k)
            return 'idle', 0, dict(lift=(1 - k) * H * 0.9, fade=min(1.0, k * 1.6), shake=0.5, glow=0.4 * (1 - k), glowcol=gc)
        if t < 5600:
            k = (t - 3600) / 2000
            e = dict(shake=0.35)
            if t < 3800:
                e['flash'] = 0.6 * (1 - (t - 3600) / 200)
            return 'prepare', min(5.0, k * 6), e
        k = (t - 5600) / 1067
        return 'idle', 0, dict()
    render(boss, 'intro', nf(DUR['intro']), ifx,
           [(0, 'boss_entrance_sting.wav', 0.9), (1500, 'sfx_boss_roar.wav', 0.6), (3600, 'boss_dragon_roar.wav', 1.0)], out_dir, sfx_dir)
    print('done', world, 'scale', round(boss.scale, 3), 'gems', [(int(p[0][0]), int(p[0][1])) for p in base])


if __name__ == '__main__':
    build(*sys.argv[1:7])
