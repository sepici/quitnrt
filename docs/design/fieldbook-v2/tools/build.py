"""Fieldbook v2: tokens, WCAG contrast, and pixel assets for variants A and B.

Usage: python3 -I build.py
Writes, for each variant folder (a/, b/):
  tokens.json   colours (light, dark), type, radii, spacing, sprite palette, contrast results
  tokens.css    @font-face, CSS variables, dark overrides
  gary-*.png, stage-*.png, band-*.png, sword.png   pixel art at device resolution (2x CSS)
All pixel art is scaled with nearest neighbour by whole-number factors only.
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DRAGON = json.loads(Path('/home/user/quitnrt/art/creatures/dragon.json').read_text())
DPR = 2  # screenshots render at 2 device px per CSS px


# ---------------------------------------------------------------- WCAG
def lum(hexcode):
    h = hexcode.lstrip('#')
    out = []
    for i in (0, 2, 4):
        v = int(h[i:i + 2], 16) / 255
        out.append(v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4)
    return 0.2126 * out[0] + 0.7152 * out[1] + 0.0722 * out[2]


def ratio(a, b):
    x, y = sorted([lum(a), lum(b)], reverse=True)
    return round((x + 0.05) / (y + 0.05), 2)


def rgb(hexcode):
    h = hexcode.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ---------------------------------------------------------------- palette
# One system for both variants: deep forest greens, one marigold accent for the
# game layer and key moments, neutral stone for nicotine used, leaf/mint only for
# "at or under the guide".
SPRITE = {  # Gary, 8 slots
    'o': '#0B2017', 'd': '#124F39', 'm': '#1F7A50', 'l': '#4FAE70',
    'b': '#F3E4B6', 'a': '#F2A922', 'e': '#FFE27A', 'h': '#FBF8E8',
}

LIGHT = {
    'bg': '#FAF9F5', 'surface': '#FFFFFF', 'sunk': '#ECE9DE', 'line': '#E2DED2', 'lineStrong': '#858A81',
    'text': '#10221A', 'text2': '#4C5A52', 'text3': '#5E6A63',
    'hero': '#0B4A33', 'onHero': '#F6F4EC', 'onHero2': '#B3D2C0', 'heroTrack': '#2A6A50', 'heroUsed': '#E6DFCB',
    'heroLine': '#2E6B52', 'heroChip': '#205F47',
    'primary': '#0B4A33', 'onPrimary': '#F6F4EC', 'primaryEdge': '#06301F',
    'link': '#0C5C3E',
    'used': '#8A877D', 'track': '#CDC9BC', 'guide': '#4C5A52',
    'success': '#1D8650', 'onSuccess': '#FFFFFF', 'successOnHero': '#7EDDA6', 'successSoft': '#DCEFE2', 'onSuccessSoft': '#0E4D2E',
    'marigold': '#F5B12B', 'onMarigold': '#1F1606', 'marigoldEdge': '#9A6508', 'marigoldSoft': '#FCEBC4',
    'onMarigoldSoft': '#5A3A00',
    'chipSel': '#0B4A33', 'onChipSel': '#F6F4EC',
    'disabledBg': '#E6E3D8', 'disabledText': '#535F58',
    'scrim': '#0A1410',
    'hpFull': '#156041', 'hpHi': '#2F8A5B', 'hpEmpty': '#D5BE86', 'hpOutline': SPRITE['o'],
    'stageSky': '#FBEFD0', 'stageText': '#10221A', 'stageText2': '#4F4A3A',
    'iconTile': '#E6EFE7', 'onIconTile': '#0B4A33',
}
DARK = {
    'bg': '#09130F', 'surface': '#122019', 'sunk': '#1A2B22', 'line': '#22342B', 'lineStrong': '#6F8679',
    'text': '#ECF1EA', 'text2': '#A7B8AD', 'text3': '#93A699',
    'hero': '#1A6A4A', 'onHero': '#F2F4EC', 'onHero2': '#D6ECDF', 'heroTrack': '#358866', 'heroUsed': '#E6DFCB',
    'heroLine': '#2E7D5C', 'heroChip': '#257556',
    'primary': '#257556', 'onPrimary': '#F2F4EC', 'primaryEdge': '#174F38',
    'link': '#8FDDB0',
    'used': '#8F8B7F', 'track': '#26372E', 'guide': '#A7B8AD',
    'success': '#6CD69B', 'onSuccess': '#09130F', 'successOnHero': '#7EDDA6', 'successSoft': '#183A28', 'onSuccessSoft': '#A9EBC4',
    'marigold': '#F7B733', 'onMarigold': '#1F1606', 'marigoldEdge': '#A86E0C', 'marigoldSoft': '#3A2A0E',
    'onMarigoldSoft': '#FFD887',
    'chipSel': '#257556', 'onChipSel': '#F2F4EC',
    'disabledBg': '#1A2B22', 'disabledText': '#A2B3A8',
    'scrim': '#000000',
    'hpFull': '#3FB072', 'hpHi': '#7DD69B', 'hpEmpty': '#463A22', 'hpOutline': '#050D09',
    'stageSky': '#16120C', 'stageText': '#F2EBDA', 'stageText2': '#CDBF9E',
    'iconTile': '#224233', 'onIconTile': '#9FE0BA',
}

# Lair stage and Today band backdrop slots.
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


# ---------------------------------------------------------------- variants
FONTS = {
    'newsreader': ('Newsreader', [('400', 'normal'), ('400', 'italic'), ('500', 'normal'), ('600', 'normal'), ('700', 'normal')]),
    'instrument-sans': ('Instrument Sans', [('400', 'normal'), ('400', 'italic'), ('500', 'normal'), ('600', 'normal'), ('700', 'normal')]),
    'schibsted-grotesk': ('Schibsted Grotesk', [('400', 'normal'), ('400', 'italic'), ('500', 'normal'), ('600', 'normal'), ('700', 'normal'), ('800', 'normal')]),
    'jersey-15': ('Jersey 15', [('400', 'normal')]),
    'silkscreen': ('Silkscreen', [('400', 'normal')]),
}

VARIANTS = {
    'a': {
        'name': 'Fieldbook v2 A, serif',
        'summary': 'Deep forest hero with a big Newsreader number, Instrument Sans for the interface, Jersey 15 for game numbers. Marigold is kept for Gary, hits and the 1 hit ready moment.',
        'fonts': {'display': 'newsreader', 'ui': 'instrument-sans', 'pixel': 'jersey-15'},
        'type': {
            'hero': {'font': 'display', 'size': 136, 'line': 100, 'weight': 600, 'tracking': -5, 'use': 'today total, the one hero number'},
            'display': {'font': 'display', 'size': 44, 'line': 44, 'weight': 600, 'tracking': -1.5, 'use': 'week total (44), Gary in the lair (48)'},
            'headline': {'font': 'display', 'size': 26, 'line': 32, 'weight': 600, 'tracking': -0.4, 'use': 'section headings, sheet title, last dose time'},
            'title': {'font': 'ui', 'size': 16, 'line': 22, 'weight': 600, 'use': 'list row titles, buttons'},
            'body': {'font': 'ui', 'size': 16, 'line': 24, 'weight': 400},
            'label': {'font': 'ui', 'size': 14, 'line': 20, 'weight': 500, 'use': 'upright labels, chips'},
            'caption': {'font': 'ui', 'size': 13, 'line': 18, 'weight': 500},
            'flavour': {'font': 'display', 'size': 18, 'line': 24, 'weight': 400, 'style': 'italic', 'use': "the demon's flavour line, the only italic"},
            'pixel': {'font': 'pixel', 'sizes': [30, 45, 60], 'note': 'Jersey 15 at multiples of 15 px (band HP 30, lair HP 45, banked hits 60). Numbers only; words stay in the UI font'},
        },
    },
    'b': {
        'name': 'Fieldbook v2 B, grotesk',
        'summary': 'All Schibsted Grotesk, heavy and tight for numbers, with Silkscreen for game numbers. Same forest and marigold system, with a rounded hero block and tactile pill actions.',
        'fonts': {'display': 'schibsted-grotesk', 'ui': 'schibsted-grotesk', 'pixel': 'silkscreen'},
        'type': {
            'hero': {'font': 'display', 'size': 128, 'line': 104, 'weight': 800, 'tracking': -7, 'use': 'today total, the one hero number'},
            'display': {'font': 'display', 'size': 44, 'line': 44, 'weight': 800, 'tracking': -2, 'use': 'week total, Gary in the lair'},
            'headline': {'font': 'display', 'size': 26, 'line': 30, 'weight': 800, 'tracking': -0.8, 'use': 'last dose time (26), section headings (24), sheet title (28)'},
            'title': {'font': 'ui', 'size': 16, 'line': 22, 'weight': 600, 'use': 'list row titles, buttons'},
            'body': {'font': 'ui', 'size': 16, 'line': 24, 'weight': 400},
            'label': {'font': 'ui', 'size': 14, 'line': 20, 'weight': 500, 'use': 'upright labels, chips'},
            'caption': {'font': 'ui', 'size': 13, 'line': 18, 'weight': 500},
            'flavour': {'font': 'ui', 'size': 17, 'line': 24, 'weight': 400, 'style': 'italic', 'use': "the demon's flavour line, the only italic"},
            'pixel': {'font': 'pixel', 'sizes': [24, 32], 'tracking': 'minus one font pixel (-3 px at 24, -4 px at 32)', 'note': 'Silkscreen at multiples of 8 px so its pixels land on whole device pixels. Numbers only'},
        },
    },
}

RADIUS = {'xs': 6, 'sm': 10, 'md': 16, 'lg': 24, 'sheet': 28, 'pill': 999, 'game': 0,
          'note': 'Tiles and chips 16, buttons pill, sheet 28 at the top. HP blocks, badges and stages are square.'}
SPACING = [4, 8, 12, 16, 20, 24, 32, 40, 56]

PAIRS = [
    # fg, bg, kind, use
    ('text', 'bg', 'text', 'body and headings on page'),
    ('text', 'surface', 'text', 'body on tiles and the sheet'),
    ('text', 'sunk', 'text', 'chip labels, stepper'),
    ('text2', 'bg', 'text', 'secondary text on page'),
    ('text2', 'surface', 'text', 'secondary text on the sheet'),
    ('text2', 'sunk', 'text', 'secondary text on chips'),
    ('link', 'bg', 'text', 'Change, More advice'),
    ('link', 'surface', 'text', 'links on the sheet'),
    ('onHero', 'hero', 'text', 'hero number and copy'),
    ('onHero2', 'hero', 'text', 'hero secondary copy'),
    ('onHero', 'heroChip', 'text', 'quick log pills inside the hero (variant B)'),
    ('onHero2', 'heroChip', 'text', 'pill secondary line inside the hero (variant B)'),
    ('onPrimary', 'primary', 'text', 'Log, Save, primary buttons'),
    ('onChipSel', 'chipSel', 'text', 'selected chips'),
    ('onMarigold', 'marigold', 'text', 'Hit button, 1 hit ready badge'),
    ('onMarigoldSoft', 'marigoldSoft', 'text', 'hit cost line on soft marigold'),
    ('onSuccessSoft', 'successSoft', 'text', 'at or under guide tag'),
    ('disabledText', 'disabledBg', 'text', '5x attack and needs 5 hits (held to 4.5 because it explains)'),
    ('stageText', 'stageSky', 'text', 'flavour line and labels on the backdrop'),
    ('stageText2', 'stageSky', 'text', 'secondary text on the backdrop'),
    ('onIconTile', 'iconTile', 'ui', 'list row leading icons'),
    ('lineStrong', 'bg', 'ui', 'outlined chips and stepper on page'),
    ('lineStrong', 'surface', 'ui', 'outlined chips on the sheet'),
    ('used', 'bg', 'ui', 'week bars: nicotine used against page (neutral, never green; no track)'),
    ('guide', 'bg', 'ui', 'dashed guide line on page'),
    ('lineStrong', 'bg', 'track', 'week strip (variant B): empty slot outline visible on page'),
    ('heroUsed', 'heroTrack', 'ui', 'today used against its track in the hero'),
    ('heroTrack', 'hero', 'track', 'empty hero track visible on hero'),
    ('onHero', 'heroTrack', 'ui', 'guide marker in the hero'),
    ('successOnHero', 'hero', 'ui', 'at or under guide mark in the hero'),
    ('success', 'bg', 'ui', 'at or under guide ticks on page'),
    ('onSuccess', 'success', 'text', 'day letter inside the at or under circle (variant A)'),
    ('primary', 'bg', 'ui', 'primary button edge on page'),
    ('marigoldEdge', 'bg', 'ui', 'Hit button tactile edge on page'),
    ('hpFull', 'hpEmpty', 'ui', 'full HP block against empty'),
    ('hpFull', 'stageSky', 'ui', 'full HP block on the Today band sky'),
    ('hpFull', 'bg', 'ui', 'full HP block on page (lair)'),
    ('hpEmpty', 'stageSky', 'track', 'empty HP block visible on the band sky'),
    ('hpEmpty', 'bg', 'track', 'empty HP block visible on page'),
]
MIN = {'text': 4.5, 'ui': 3.0, 'track': 1.5}


def contrast_report(colors):
    rows = []
    for fg, bg, kind, use in PAIRS:
        r = ratio(colors[fg], colors[bg])
        rows.append({'fg': fg, 'bg': bg, 'fgHex': colors[fg], 'bgHex': colors[bg], 'kind': kind,
                     'min': MIN[kind], 'ratio': r, 'pass': r >= MIN[kind], 'use': use})
    return rows


# ---------------------------------------------------------------- pixel helpers
def grid_image(rows, pal, scale):
    h, w = len(rows), len(rows[0])
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.' and pal.get(ch):
                img.putpixel((x, y), rgb(pal[ch]) + (255,))
    return img.resize((w * scale, h * scale), Image.NEAREST)


def add_rim(img, width, colour):
    """Light rim of `width` device px around the outer silhouette only (dark mode).
    Interior holes (between tail and body, under the wing) are left alone."""
    w, h = img.size
    src = img.getchannel('A').load()
    # flood fill the outside region from the image border
    outside = [[False] * w for _ in range(h)]
    stack = [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]
    while stack:
        x, y = stack.pop()
        if 0 <= x < w and 0 <= y < h and not outside[y][x] and not src[x, y]:
            outside[y][x] = True
            stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    rim = Image.new('L', (w, h), 0)
    rp = rim.load()
    for y in range(h):
        for x in range(w):
            if not outside[y][x]:
                continue
            for dy in range(-width, width + 1):
                for dx in range(-width, width + 1):
                    xx, yy = x + dx, y + dy
                    if abs(dx) + abs(dy) <= width and 0 <= xx < w and 0 <= yy < h and src[xx, yy]:
                        rp[x, y] = 255
                        break
                if rp[x, y]:
                    break
    out = Image.new('RGBA', (w, h), rgb(colour) + (0,))
    out.putalpha(rim)
    out.alpha_composite(img)
    return out


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
    import math
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


def place(img, mode, scale, gx, gy):
    sprite = grid_image(DRAGON['frames']['idle1'], SPRITE, scale * DPR)
    off = 0
    if STAGE[mode]['rim']:
        sprite = add_rim(sprite, RIM_PX, STAGE[mode]['rim'])
    img.alpha_composite(sprite, (gx * scale * DPR, gy * scale * DPR))
    return img


RIM_PX = 2  # device px, so 1 CSS px


def lair_stage(mode, cols=70, rows=62, gx=19, gy=21):
    """Lair stage: 70 x 62 art px at 6 CSS px each (crop to 412), Gary standing on the middle rock."""
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
    rock(g, 33, 59, 15, 6)   # Gary's rock, top at row 53
    rock(g, 4, 58, 6, 3)
    rock(g, 64, 59, 7, 4)
    img = grid_image([''.join(r) for r in g], stage_pal(mode), 6 * DPR)
    return place(img, mode, 6, gx, gy)


def band_stage(mode, cols=138, rows=44, scale=3):
    """Today strip: full-bleed band at 3x, Gary on the left, clean sky on the right for text."""
    rnd = random.Random(7)
    g = blank(cols, rows, 'S')
    sky(g, mode, 31, 34, rnd, 40, top=2, xmax=38)
    ridge(g, 33, 1.3, 4.5, 2, 'F', 3, rnd)
    ridge(g, 36, 0.9, 3.2, 9, 'N', 4, rnd)
    for y in range(39, rows):
        for x in range(cols):
            g[y][x] = 'v' if y == 39 else ('g' if (x * 3 + y * 5) % 9 == 0 else 'G')
    rock(g, 20, 42, 15, 4)
    img = grid_image([''.join(r) for r in g], stage_pal(mode), scale * DPR)
    return place(img, mode, scale, 6, 6)


def sword_grid():
    g = blank(16, 16, '.')
    for t in range(8):
        cx, cy = 12 - t, 3 + t
        g[cy][cx] = 'l'
        g[cy][cx + 1] = 'm'
    g[2][13] = 'h'
    for x, y in ((3, 9), (4, 10), (5, 11), (6, 12), (7, 13)):
        g[y][x] = 'd'
    for x, y in ((4, 12), (3, 13)):
        g[y][x] = 'g'
    g[14][2] = 'd'
    src = [r[:] for r in g]
    for y in range(16):
        for x in range(16):
            if src[y][x] != '.':
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < 16 and 0 <= yy < 16 and src[yy][xx] != '.':
                    g[y][x] = 'o'
                    break
    return [''.join(r) for r in g]


SWORD_PAL = {'o': '#2A1A0C', 'd': '#6B4423', 'm': '#B07A43', 'l': '#D9A866', 'h': '#FBF2DC', 'g': '#F2A922'}


# ---------------------------------------------------------------- css
def font_css(slug):
    fam, faces = FONTS[slug]
    out = ''
    for w, st in faces:
        out += (f"@font-face{{font-family:'{fam}';src:url('../fonts/{slug}-latin-{w}-{st}.woff2') format('woff2');"
                f"font-weight:{w};font-style:{st};font-display:block}}\n")
    return out


def css_vars(colors):
    return ';'.join(f'--{k}:{v}' for k, v in colors.items()) + ';'


def write_variant(key, v):
    out = ROOT / key
    out.mkdir(exist_ok=True)
    slugs = sorted(set(v['fonts'].values()))
    fam = {role: FONTS[slug][0] for role, slug in v['fonts'].items()}
    css = '/* generated by tools/build.py */\n' + ''.join(font_css(s) for s in slugs)
    sprite_vars = ';'.join(f'--sp-{k}:{c}' for k, c in SPRITE.items())
    css += (":root{color-scheme:light dark;"
            f"--font-display:'{fam['display']}',serif;--font-ui:'{fam['ui']}',sans-serif;--font-pixel:'{fam['pixel']}',monospace;"
            + ''.join(f'--r-{k}:{r}px;' for k, r in RADIUS.items() if isinstance(r, int))
            + sprite_vars + ';' + css_vars(LIGHT) + '}\n')
    css += '@media (prefers-color-scheme: dark){:root{' + css_vars(DARK) + '}}\n'
    (out / 'tokens.css').write_text(css)

    tokens = {
        'name': v['name'], 'slug': f'fieldbook-v2-{key}', 'summary': v['summary'],
        'colors': {'light': LIGHT, 'dark': DARK},
        'colorRoles': {
            'hero': 'the one colour block per screen (Today hero, sheet header strip)',
            'used / heroUsed': 'nicotine used. Neutral stone, never green or marigold, so a heavier day never looks more complete',
            'guide': 'the daily or weekly guide, drawn as a marker or line, never as a fill',
            'success / successOnHero': 'only for at or under the guide (week ticks, the 6 mg left mark)',
            'marigold': 'game layer and key moments only: Hit, 1 hit ready, the Lair tab badge, Gary\'s horns',
            'primary': 'Log and Save. Forest in light, the same green as the hero pills in dark',
        },
        'type': {'fonts': {role: {'family': FONTS[s][0], 'license': 'OFL-1.1', 'source': f'@fontsource/{s}'}
                           for role, s in v['fonts'].items()}, 'scale': v['type']},
        'radius': RADIUS, 'spacing': SPACING,
        'touchTarget': {'min': 48, 'note': 'every link and icon button, including Change and the quick log customise button'},
        'sprite': SPRITE,
        'spriteStage': STAGE,
        'gear': {'woodenSword': SWORD_PAL},
        'contrast': {
            'method': 'WCAG 2.x relative luminance ratio, computed by tools/build.py',
            'thresholds': MIN,
            'light': contrast_report(LIGHT), 'dark': contrast_report(DARK),
            'sprite': sprite_contrast(),
        },
    }
    (out / 'tokens.json').write_text(json.dumps(tokens, indent=2, ensure_ascii=False) + '\n')

    for mode in ('light', 'dark'):
        lair_stage(mode).save(out / f'stage-{mode}.png')
        band_stage(mode).save(out / f'band-{mode}.png')
    grid_image(sword_grid(), SWORD_PAL, 2 * DPR).save(out / 'sword.png')
    grid_image(DRAGON['frames']['idle1'], SPRITE, 1).save(out / 'gary-1x.png')
    fails = [(m, r['fg'], r['bg'], r['ratio']) for m in ('light', 'dark') for r in tokens['contrast'][m] if not r['pass']]
    return fails


def sprite_contrast():
    res = {}
    for mode in ('light', 'dark'):
        sky = STAGE[mode]['sky']
        o, m, l_ = ratio(SPRITE['o'], sky), ratio(SPRITE['m'], sky), ratio(SPRITE['l'], sky)
        row = {'pair': 'Gary silhouette vs stage sky', 'outline': o, 'mid': m, 'light': l_}
        if STAGE[mode]['rim']:
            row['rim'] = ratio(STAGE[mode]['rim'], sky)
            row['rimVsOutline'] = ratio(STAGE[mode]['rim'], SPRITE['o'])
            row['carriedBy'] = '1 px light rim'
            row['ratio'] = row['rim']
        else:
            row['carriedBy'] = 'outline'
            row['ratio'] = o
        row['min'] = 3.0
        row['pass'] = row['ratio'] >= 3.0
        hills = STAGE[mode]['near']
        res[mode] = [row, {'pair': 'Gary outline or rim vs near hills',
                           'ratio': ratio(STAGE[mode]['rim'] or SPRITE['o'], hills), 'min': 3.0,
                           'pass': ratio(STAGE[mode]['rim'] or SPRITE['o'], hills) >= 3.0}]
    return res


if __name__ == '__main__':
    for k, v in VARIANTS.items():
        f = write_variant(k, v)
        print(k, 'contrast fails:', f or 'none')
    for m in ('light', 'dark'):
        print(m, sprite_contrast()[m])
