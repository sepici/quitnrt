"""Dev tool: writes the Today band and lair stage backdrops as pixel grids for the app.

The shapes and colours are the Fieldbook v2 mockups' (docs/design/fieldbook-v2/tools/build.py),
copied here so this runs without that script's image dependencies. Each grid is rows of palette
letters, like the creature sprites, with a light and a dark palette.

Usage: python -I backdrops.py
"""
import json
import math
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'core', 'domain', 'src', 'main', 'resources', 'backdrops')

STAGE = {
    'light': {
        'sky': '#FBEFD0', 'sky2': '#F8E3B2', 'sun': '#F5B731', 'sunHi': '#FAD57E',
        'far': '#D3E1BF', 'near': '#AFCB9C', 'nearDk': '#9DBE8C',
        'ground': '#E8D8B0', 'groundDk': '#D8C496', 'grass': '#86AE70',
        'rockHi': '#F3EDE0', 'rock': '#CFC5AE', 'rockLo': '#A99C82', 'rockO': '#3F3829',
        'star': '#FBEFD0', 'rim': None,
    },
    'dark': {
        'sky': '#16120C', 'sky2': '#2E210D', 'sun': '#F2B23A', 'sunHi': '#F8CF73',
        'far': '#162A20', 'near': '#0F2018', 'nearDk': '#0F2018',
        'ground': '#1D1A12', 'groundDk': '#17150E', 'grass': '#3F5E36',
        'rockHi': '#7A6F58', 'rock': '#504838', 'rockLo': '#39332A', 'rockO': '#0A0805',
        'star': '#8A7A56', 'rim': '#BCA97E',
    },
}


def blank(w, h, ch):
    return [[ch] * w for _ in range(h)]


def disc(g, cx, cy, r, ch, ring=None):
    for y in range(len(g)):
        for x in range(len(g[0])):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if d <= r * r:
                g[y][x] = ch
            elif ring and d <= (r + 1.2) ** 2 and g[y][x] in ('S', 's'):
                g[y][x] = ring


def ridge(g, base, amp, period, phase, ch, top_noise, rnd):
    w = len(g[0])
    for x in range(w):
        top = base - round(amp * (math.sin((x + phase) / period) + 0.6 * math.sin((x + phase * 2) / (period * 0.47))))
        top += rnd.choice([0] * top_noise + [1])
        for y in range(max(0, top), len(g)):
            g[y][x] = ch


def rock(g, cx, base, rw, rh):
    """Flat-topped boulder (superellipse), lit from the top left, outlined, with a ground shadow."""
    w, h = len(g[0]), len(g)
    cells = set()
    for x in range(cx - rw, cx + rw + 1):
        t = abs(x - cx) / rw
        if t > 1 or not 0 <= x < w:
            continue
        top = base - round(rh * (1 - t ** 4) ** 0.25)
        for y in range(top, base + 1):
            if 0 <= y < h:
                cells.add((x, y))
    for (x, y) in cells:
        nx, ny = (x - cx) / rw, (y - base) / max(1, rh)
        light = -0.55 * nx - 0.95 * ny
        top_edge = (x, y - 1) not in cells
        if top_edge and nx < 0.45:
            shade = 'K'
        elif light > 0.42:
            shade = 'k' if (x * 7 + y * 3) % 13 else 'j'
        else:
            shade = 'j'
        g[y][x] = shade
    for (x, y) in list(cells):
        for dx, dy in ((1, 0), (-1, 0), (0, -1)):
            xx, yy = x + dx, y + dy
            if 0 <= xx < w and 0 <= yy < h and (xx, yy) not in cells:
                g[yy][xx] = 'O'
    sy = base + 1
    if sy < h:
        for x in range(cx - rw - 1, cx + rw + 3):
            if 0 <= x < w:
                g[sy][x] = 'g'


def stage_pal(mode):
    s = STAGE[mode]
    return {'S': s['sky'], 's': s['sky2'], 'U': s['sun'], 'u': s['sunHi'], 'F': s['far'], 'N': s['near'],
            'n': s['nearDk'], 'G': s['ground'], 'g': s['groundDk'], 'v': s['grass'], 'K': s['rockHi'],
            'k': s['rock'], 'j': s['rockLo'], 'O': s['rockO'], '*': s['star']}


def sky(g, mode, glow_from, glow_to, rnd, stars, top=2, xmax=999, sun=None):
    cols = len(g[0])
    # short dither transition (2 sparse rows, 2 checker rows), then solid glow
    for i, y in enumerate(range(glow_from, glow_from + 4)):
        for x in range(cols):
            if (i < 2 and (x + 2 * y) % 4 == 0) or (i >= 2 and (x + y) % 2 == 0):
                g[y][x] = 's'
    for y in range(glow_from + 4, len(g)):
        for x in range(cols):
            g[y][x] = 's'
    if mode == 'dark':
        for _ in range(stars):
            x, y = rnd.randrange(cols), rnd.randrange(top, glow_from - 2)
            if x < xmax and not (sun and (x - sun[0]) ** 2 + (y - sun[1]) ** 2 < 100):
                g[y][x] = '*'




def lair_stage(mode, cols=70, rows=62):
    """Lair stage: Gary stands on the middle rock, his sprite's top left at (19, 21)."""
    rnd = random.Random(23)
    g = blank(cols, rows, 'S')
    sky(g, mode, 32, 44, rnd, 30, top=15, sun=(57, 16))
    disc(g, 57, 16, 5.6, 'U')
    for (x, y) in ((55, 13), (56, 13), (54, 14), (55, 14), (54, 15)):
        g[y][x] = 'u'
    ridge(g, 45, 2.0, 6.5, 3, 'F', 3, rnd)
    ridge(g, 50, 1.4, 4.2, 11, 'N', 4, rnd)
    for y in range(54, rows):
        for x in range(cols):
            g[y][x] = 'v' if y == 54 else ('g' if (x * 3 + y * 5) % 9 == 0 else 'G')
    for x in range(0, cols, 3):
        if rnd.random() < 0.5:
            g[53][x] = 'v'
    rock(g, 33, 59, 15, 6)
    rock(g, 4, 58, 6, 3)
    rock(g, 64, 59, 7, 4)
    return g, (19, 21)


def band_stage(mode, cols=138, rows=44):
    """Today band: Gary on the left, his sprite's top left at (6, 6), clear sky on the right for text."""
    rnd = random.Random(7)
    g = blank(cols, rows, 'S')
    sky(g, mode, 31, 34, rnd, 40, top=2, xmax=38)
    ridge(g, 33, 1.3, 4.5, 2, 'F', 3, rnd)
    ridge(g, 36, 0.9, 3.2, 9, 'N', 4, rnd)
    for y in range(39, rows):
        for x in range(cols):
            g[y][x] = 'v' if y == 39 else ('g' if (x * 3 + y * 5) % 9 == 0 else 'G')
    rock(g, 20, 42, 15, 4)
    return g, (6, 6)


SKY = set('Ss*Uu')


def horizon(g):
    """The top row of the land (hills, ground and rocks). Text drawn on the backdrop stays above it."""
    return next(y for y, row in enumerate(g) if any(ch not in SKY for ch in row))


def export(name, build):
    grids = {mode: build(mode) for mode in ('light', 'dark')}
    (light, sprite_at), (dark, _) = grids['light'], grids['dark']
    used = sorted({ch for g in (light, dark) for row in g for ch in row})
    data = {
        'name': name,
        'width': len(light[0]),
        'height': len(light),
        'sprite': {'x': sprite_at[0], 'y': sprite_at[1]},
        'horizon': min(horizon(light), horizon(dark)),
        'rim': {mode: STAGE[mode]['rim'] for mode in ('light', 'dark') if STAGE[mode]['rim']},
        'palettes': {mode: {ch: stage_pal(mode)[ch] for ch in used} for mode in ('light', 'dark')},
        'rows': {'light': [''.join(r) for r in light], 'dark': [''.join(r) for r in dark]},
    }
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=1)
        f.write('\n')
    print(name, data['width'], 'x', data['height'])


if __name__ == '__main__':
    export('band', band_stage)
    export('stage', lair_stage)
