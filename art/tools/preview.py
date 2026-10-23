"""Check every creature file and render preview sheets for review, with no dependencies.

usage: python -I preview.py [creature ...]

Writes to art/previews/:
  <creature>.png   every frame in every variant, 4x, on the lair's light and dark sky,
                   with the lurking silhouette and a 1x thumbnail
  line-up.png      all eight creatures side by side: idle, lurking, and each variant

The checks are the app's (SpriteSheet.parse) plus: the eight slots, one labelled "eye", the
dragon's four variants, every frame present, and every colour from the master palette.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from png import Canvas, hex_rgb  # noqa: E402
import spritekit as sk  # noqa: E402

OUT = os.path.normpath(os.path.join(HERE, '..', 'previews'))
CREATURES = ("dragon", "imp", "bear", "kraken", "goblin", "slime", "wraith", "serpent")
SLOTS = ("o", "d", "m", "l", "b", "a", "e", "h")
# The lair's sky, light and dark (core/domain/src/main/resources/backdrops), and the lurking browns.
LIGHT, DARK = hex_rgb('#FBEFD0'), hex_rgb('#16120C')
LURK = {LIGHT: hex_rgb('#3F3829'), DARK: hex_rgb('#504838')}
MARIGOLD = hex_rgb('#F5B12B')
SHEET_BG, INK, INK2 = (0x2B, 0x2D, 0x31), (236, 236, 236), (150, 150, 150)
SCALE = 4


def load(key):
    with open(os.path.join(sk.CREATURES, f'{key}.json')) as f:
        return json.load(f)


def check(data):
    key = data['creature']
    errors = []
    if data['size'] != 32:
        errors.append(f'size is {data["size"]}')
    if tuple(sorted(data['slots'])) != tuple(sorted(SLOTS)):
        errors.append(f'slots are {sorted(data["slots"])}')
    if [k for k, v in data['slots'].items() if v == 'eye'] != ['e']:
        errors.append('slot e must be the only one labelled "eye"')
    if tuple(data['variants']) != sk.VARIANT_ORDER or data['defaultVariant'] != 'forest':
        errors.append(f'variants are {list(data["variants"])}, default {data["defaultVariant"]}')
    master = sk.load_palette()
    for name, pal in data['variants'].items():
        off = [c for c in pal.values() if c.lower() not in master]
        if off:
            errors.append(f'{name} has colours outside the master palette: {off}')
        if len(set(pal.values())) != len(pal):
            errors.append(f'{name} gives two slots one colour')
    if tuple(data['frames']) != sk.FRAME_ORDER:
        errors.append(f'frames are {list(data["frames"])}')
    for name, rows in data['frames'].items():
        if len(rows) != 32 or any(len(r) != 32 for r in rows):
            errors.append(f'{name} is not 32 x 32')
        bad = {c for r in rows for c in r} - set(SLOTS) - {'.'}
        if bad:
            errors.append(f'{name} uses {bad}')
        eyes = sum(r.count('e') for r in rows)
        if name.startswith('defeat') and eyes:
            errors.append(f'{name} still has its eyes open')
    if not sum(r.count('e') for r in data['frames']['idle1']):
        errors.append('idle1 has no eyes for the lurking silhouette')
    if errors:
        raise SystemExit(f'{key}: ' + '; '.join(errors))


def sprite(c, rows, x, y, colour_of, scale=SCALE):
    c.pixels(rows, lambda ch: None if ch == '.' else colour_of(ch), x, y, scale)


def sheet(data):
    pal_rgb = {v: {k: hex_rgb(h) for k, h in p.items()} for v, p in data['variants'].items()}
    frames = list(data['frames'])
    cell = 32 * SCALE
    gap, pad, label_w, head_h = 10, 24, 132, 56
    extra = 2  # the lurking silhouette and the 1x thumbnail
    width = pad * 2 + label_w + (len(frames) + extra) * (cell + gap)
    block = 2 * cell + gap
    height = pad * 2 + head_h + len(pal_rgb) * (block + 3 * gap)
    c = Canvas(width, height, SHEET_BG)
    c.text(pad, pad, f"{data['creature']}: {len(frames)} frames, {len(pal_rgb)} variants, {SCALE}x", INK, 2)
    x0 = pad + label_w
    for i, name in enumerate(frames + ['lurking', '1x']):
        c.text(x0 + i * (cell + gap), pad + 30, name, INK, 2)
    for vi, (variant, pal) in enumerate(pal_rgb.items()):
        y0 = pad + head_h + vi * (block + 3 * gap)
        c.text(pad, y0 + 4, variant, INK, 2)
        if variant == data['defaultVariant']:
            c.text(pad, y0 + 24, '(default)', INK2, 1)
        for si, s in enumerate(SLOTS):
            sx, sy = pad + (si % 4) * 18, y0 + 44 + (si // 4) * 18
            c.rect(sx, sy, 14, 14, pal[s])
            c.outline(sx - 1, sy - 1, 16, 16, (90, 90, 90))
        for ri, bg in enumerate((LIGHT, DARK)):
            yy = y0 + ri * (cell + gap)
            for i, name in enumerate(frames):
                xx = x0 + i * (cell + gap)
                c.rect(xx, yy, cell, cell, bg)
                sprite(c, data['frames'][name], xx, yy, pal.get)
            xx = x0 + len(frames) * (cell + gap)
            c.rect(xx, yy, cell, cell, bg)
            sprite(c, data['frames']['idle1'], xx, yy, lambda ch: MARIGOLD if ch == 'e' else LURK[bg])
            xx += cell + gap
            c.rect(xx, yy, 40, 40, bg)
            sprite(c, data['frames']['idle1'], xx + 4, yy + 4, pal.get, 1)
            c.rect(xx, yy + 48, 72, 72, bg)
            sprite(c, data['frames']['idle1'], xx + 4, yy + 52, pal.get, 2)
    return c


def line_up(all_data):
    cell = 32 * SCALE
    gap, pad, head_h, label_w = 12, 24, 40, 160
    rows = ['forest', 'forest dark', 'lurking', 'lurking dark'] + list(sk.VARIANT_ORDER[1:])
    width = pad * 2 + label_w + len(all_data) * (cell + gap)
    height = pad * 2 + head_h + len(rows) * (cell + gap)
    c = Canvas(width, height, SHEET_BG)
    c.text(pad, pad, f'the eight creatures, idle1, {SCALE}x', INK, 2)
    for i, data in enumerate(all_data):
        xx = pad + label_w + i * (cell + gap)
        c.text(xx, pad + 22, data['creature'], INK, 2)
        for r, row in enumerate(rows):
            yy = pad + head_h + r * (cell + gap)
            if i == 0:
                c.text(pad, yy + cell // 2 - 6, row, INK, 2)
            bg = DARK if row.endswith('dark') else LIGHT
            c.rect(xx, yy, cell, cell, bg)
            rows_ = data['frames']['idle1']
            if row.startswith('lurking'):
                sprite(c, rows_, xx, yy, lambda ch: MARIGOLD if ch == 'e' else LURK[bg])
            else:
                pal = {k: hex_rgb(h) for k, h in data['variants'][row.split()[0]].items()}
                sprite(c, rows_, xx, yy, pal.get)
    return c


def main(argv):
    keys = argv[1:] or list(CREATURES)
    os.makedirs(OUT, exist_ok=True)
    for key in keys:
        data = load(key)
        check(data)
        path = os.path.join(OUT, f'{key}.png')
        sheet(data).save(path)
        print('checked and wrote', os.path.relpath(path, sk.ROOT))
    if not argv[1:]:
        path = os.path.join(OUT, 'line-up.png')
        line_up([load(k) for k in CREATURES]).save(path)
        print('wrote', os.path.relpath(path, sk.ROOT))


if __name__ == '__main__':
    main(sys.argv)
