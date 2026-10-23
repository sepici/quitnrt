"""Shared helpers for the creature build scripts.

Every creature is a 32 x 32 grid of palette slot letters, the dragon's format:
  o outline, d dark, m mid, l light, b belly (or the creature's second light), a accent,
  e eye (the lurking silhouette keeps these pixels in marigold), h highlight, '.' transparent.

A frame is built as (hand-drawn grid) + (region moves) + (hand-placed pixel edits), so each
change stays reviewable and reproducible. The defeat frames after the knockout are made by
breakup(), which cracks the knocked-out pose apart and scatters it as loose pixels.
"""
import json
import os
import random

SIZE = 32
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
CREATURES = os.path.join(ROOT, 'core', 'domain', 'src', 'main', 'resources', 'creatures')
PALETTE = os.path.join(ROOT, 'core', 'domain', 'src', 'main', 'resources', 'palette.json')

FRAME_ORDER = ("idle1", "idle2", "hurt", "rough", "barely", "defeat1", "defeat2", "defeat3", "defeat4")
VARIANT_ORDER = ("forest", "ember", "moss", "frost")


# ---------------------------------------------------------------- grids
def grid(rows):
    rows = list(rows)
    assert len(rows) == SIZE, f'{len(rows)} rows'
    for i, r in enumerate(rows):
        assert len(r) == SIZE, f'row {i} is {len(r)} wide: {r!r}'
    return [list(r) for r in rows]


def blank():
    return [['.'] * SIZE for _ in range(SIZE)]


def rows_of(g):
    return [''.join(r) for r in g]


def copy(g):
    return [list(r) for r in g]


def at(g, x, y):
    return g[y][x] if 0 <= x < SIZE and 0 <= y < SIZE else '.'


def put(g, x, y, s):
    """Write string s starting at (x, y). ' ' leaves a pixel unchanged."""
    for i, c in enumerate(s):
        if c != ' ' and 0 <= x + i < SIZE:
            g[y][x + i] = c


def segs(g, table):
    """table: {y: [(x, chars), ...]} written with put()."""
    for y, items in table.items():
        for x, s in items:
            put(g, x, y, s)


def patch(g, x0, y0, rows):
    """Write a block of rows at (x0, y0); ' ' leaves a pixel unchanged, '_' clears it."""
    for dy, r in enumerate(rows):
        for dx, c in enumerate(r):
            if c == ' ':
                continue
            x, y = x0 + dx, y0 + dy
            if 0 <= x < SIZE and 0 <= y < SIZE:
                g[y][x] = '.' if c == '_' else c


def patch_behind(g, x0, y0, rows):
    """Like patch, but only on transparent pixels, so the new part sits behind what's drawn."""
    for dy, r in enumerate(rows):
        for dx, c in enumerate(r):
            x, y = x0 + dx, y0 + dy
            if c not in ' _' and 0 <= x < SIZE and 0 <= y < SIZE and g[y][x] == '.':
                g[y][x] = c


def clear(g, x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            g[y][x] = '.'


def shut_eyes(g, lid='o'):
    """Every eye pixel closed, for a knockout."""
    for row in g:
        for x, c in enumerate(row):
            if c == 'e':
                row[x] = lid


def move(g, x0, y0, x1, y1, dx, dy, mask=None):
    """Move the non-transparent pixels in the inclusive rect by (dx, dy).
    Vacated pixels become transparent; moved pixels paint over the destination."""
    pts = []
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if g[y][x] != '.' and (mask is None or mask(x, y)):
                pts.append((x, y, g[y][x]))
                g[y][x] = '.'
    for x, y, c in pts:
        if 0 <= x + dx < SIZE and 0 <= y + dy < SIZE:
            g[y + dy][x + dx] = c


def drop_row(g, row, top=0):
    """Remove `row`, shifting rows top..row-1 down by one (row `top` becomes blank)."""
    for y in range(row, top, -1):
        g[y] = list(g[y - 1])
    g[top] = ['.'] * SIZE


def sink(g, row, x0=0, x1=SIZE - 1, top=0):
    """Like drop_row, but only in columns x0..x1: everything above `row` settles 1 px."""
    for y in range(row, top, -1):
        for x in range(x0, x1 + 1):
            g[y][x] = g[y - 1][x]
    for x in range(x0, x1 + 1):
        g[top][x] = '.'


def mirror(rows):
    return [r[::-1] for r in rows]


def replace(g, old, new, x0=0, y0=0, x1=SIZE - 1, y1=SIZE - 1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if g[y][x] == old:
                g[y][x] = new


def seal(g):
    """Close the outline: every coloured pixel that touches the background gets an outline pixel
    there, and one-pixel holes are filled. Hand-drawn grids and region moves both leave gaps."""
    for y in range(SIZE):
        for x in range(SIZE):
            if g[y][x] not in '.o*':
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < SIZE and 0 <= ny < SIZE and g[ny][nx] == '.':
                        g[ny][nx] = '*'
    for y in range(SIZE):
        for x in range(SIZE):
            if g[y][x] == '*':
                g[y][x] = 'o'
    holes = [(x, y) for y in range(1, SIZE - 1) for x in range(1, SIZE - 1) if g[y][x] == '.'
             and all(g[y + dy][x + dx] != '.' for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in holes:
        g[y][x] = 'o'
    return g


def count(g, chars=None):
    return sum(1 for r in g for c in r if c != '.' and (chars is None or c in chars))


# ---------------------------------------------------------------- blockout
# Shapes are sets of (x, y). A part is a shape with a material; parts are painted back to front,
# each with its own outline, so a front part gets an outline where it overlaps the one behind,
# as the dragon's arms and belly do. Light comes from the top left.
def ellipse(cx, cy, rx, ry):
    pts = set()
    for y in range(SIZE):
        for x in range(SIZE):
            if ((x - cx) / (rx + 0.5)) ** 2 + ((y - cy) / (ry + 0.5)) ** 2 <= 1.0:
                pts.add((x, y))
    return pts


def poly(points):
    """Filled polygon through pixel centres (even-odd rule)."""
    pts = set()
    n = len(points)
    for y in range(SIZE):
        yc = y + 0.5
        xs = []
        for i in range(n):
            (x0, y0), (x1, y1) = points[i], points[(i + 1) % n]
            y0c, y1c = y0 + 0.5, y1 + 0.5
            if (y0c <= yc < y1c) or (y1c <= yc < y0c):
                xs.append(x0 + 0.5 + (yc - y0c) * (x1 - x0) / (y1c - y0c))
        xs.sort()
        for a, b in zip(xs[::2], xs[1::2]):
            for x in range(SIZE):
                if a <= x + 0.5 <= b:
                    pts.add((x, y))
    return pts


def capsule(path, r):
    """A thick line along path [(x, y), ...] with radius r."""
    pts = set()
    for (x0, y0), (x1, y1) in zip(path, path[1:]):
        steps = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) * 3))
        for i in range(steps + 1):
            t = i / steps
            cx, cy = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            for y in range(int(cy - r - 1), int(cy + r + 2)):
                for x in range(int(cx - r - 1), int(cx + r + 2)):
                    if (x - cx) ** 2 + (y - cy) ** 2 <= (r + 0.35) ** 2 and 0 <= x < SIZE and 0 <= y < SIZE:
                        pts.add((x, y))
    return pts


def rect(x0, y0, x1, y1):
    return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}


def shift(shape, dx, dy):
    return {(x + dx, y + dy) for x, y in shape if 0 <= x + dx < SIZE and 0 <= y + dy < SIZE}


def shade(shape, x, y, light='l', mid='m', dark='d', rim=0.72):
    """Top-left light: lit on the upper-left edge, dark on the lower-right edge and the far side.
    Thin parts get one lit edge and no shadow, so they don't turn to noise."""
    inside = lambda px, py: (px, py) in shape
    row = [px for px, py in shape if py == y]
    col = [py for px, py in shape if px == x]
    lo, hi = min(row), max(row)
    width = hi - lo + 1
    tall = max(col) - min(col) + 1
    top_lit = not inside(x, y - 1) and tall > 2 and width > 2 and (x - lo) < width * 0.6
    if (not inside(x - 1, y) and width > 1) or top_lit:
        return light
    if width >= 3 and (not inside(x + 1, y) or (not inside(x, y + 1) and tall > 3)):
        return dark
    if width >= 7 and (x - lo) / (hi - lo) > rim:
        return dark
    return mid


def paint(g, shape, fill='shade', outline='o', **kw):
    """Outline the shape (4-neighbours just outside it), then fill it.
    fill: 'shade' for the body ramp, or a single slot letter, or a function (x, y) -> letter."""
    for x, y in shape:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in shape and 0 <= q[0] < SIZE and 0 <= q[1] < SIZE and outline:
                g[q[1]][q[0]] = outline
    for x, y in shape:
        if fill == 'shade':
            g[y][x] = shade(shape, x, y, **kw)
        elif callable(fill):
            g[y][x] = fill(x, y)
        else:
            g[y][x] = fill


def compose(parts):
    """parts: [(shape, fill, kwargs)] back to front."""
    g = blank()
    for part in parts:
        shape, fill = part[0], part[1]
        kw = part[2] if len(part) > 2 else {}
        paint(g, shape, fill, **kw)
    return g


# ---------------------------------------------------------------- defeat
def breakup(knocked_out, seed):
    """The three frames after the knockout: the body cracks into tiles that start to drift
    apart, the tiles burst into loose 2 x 2 bits, and the last motes scatter and fall.

    Deterministic for a seed, and every pixel keeps its slot letter, so palette swaps still work.
    """
    rnd = random.Random(seed)
    knocked_out = seal(copy(knocked_out))
    src = [(x, y, knocked_out[y][x]) for y in range(SIZE) for x in range(SIZE) if knocked_out[y][x] != '.']
    cx = sum(p[0] for p in src) / len(src)
    cy = sum(p[1] for p in src) / len(src)

    def place(g, x, y, c):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            g[y][x] = c

    # 1. cracks: irregular shards, each nudged 1 or 2 px away from the middle, parting along
    #    jagged cracks where two shards meet
    seeds = rnd.sample([(x, y) for x, y, _ in src], 18)
    shard = {(x, y): min(range(len(seeds)), key=lambda i: (x - seeds[i][0]) ** 2 + (y - seeds[i][1]) ** 2)
             for x, y, _ in src}
    nudge = {}
    for i, (sx, sy) in enumerate(seeds):
        dx, dy = sx - cx, sy - cy
        n = max(1.0, (dx * dx + dy * dy) ** 0.5)
        reach = 1.0 + min(1.0, n / 12)
        nudge[i] = (round(dx / n * reach), round(dy / n * reach - 0.3))
    crack = blank()
    for x, y, c in src:
        i = shard[(x, y)]
        border = any(shard.get((x + ox, y + oy), i) != i for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if border and rnd.random() < 0.55:
            continue
        place(crack, x + nudge[i][0], y + nudge[i][1], c)

    # 2. burst: 2 x 2 bits flung out from the middle as if it had popped, a little over half kept
    burst = blank()
    bits = {}
    for x, y, c in src:
        bits.setdefault((x // 2, y // 2), []).append((x, y, c))
    for (bx, by), pts in sorted(bits.items()):
        if rnd.random() < 0.45:
            continue
        mx, my = bx * 2 + 0.5, by * 2 + 0.5
        ox = round((mx - cx) * 0.45 + rnd.uniform(-1.2, 1.2))
        oy = round((my - cy) * 0.35 - rnd.uniform(0.5, 2.5))
        # a bit keeps one colour, its most common slot other than the outline, so it reads as body
        body = [c for _, _, c in pts if c != 'o'] or [c for _, _, c in pts]
        colour = max(sorted(set(body)), key=body.count)
        for x, y, _ in pts:
            place(burst, x + ox, y + oy, colour)

    # 3. motes: single pixels, further out and drifting down, in the body's colours (not the
    #    creams, which vanish against the lair's light sky)
    motes = blank()
    light_first = [p for p in src if p[2] in 'lma'] or src
    for x, y, c in rnd.sample(light_first, max(14, len(light_first) // 6)):
        nx = round(x + (x - cx) * 0.9 + rnd.uniform(-2, 2))
        ny = round(y + (y - cy) * 0.6 + rnd.uniform(0, 4))
        place(motes, nx, ny, c)
    return crack, burst, motes


# ---------------------------------------------------------------- palette
def load_palette():
    with open(PALETTE) as f:
        data = json.load(f)
    return {c.lower() for ramp in data['ramps'].values() for c in ramp}


def check_variants(key, variants, slots):
    known = load_palette()
    assert tuple(variants) == VARIANT_ORDER, f'{key}: variants {list(variants)}'
    for name, pal in variants.items():
        assert set(pal) == set(slots), f'{key} {name}: slots {sorted(pal)}'
        off = [c for c in pal.values() if c.lower() not in known]
        assert not off, f'{key} {name}: {off} not in the master palette'
        assert len(set(pal.values())) == len(pal), f'{key} {name}: two slots share a colour'


# ---------------------------------------------------------------- output
def dump(data):
    """JSON with one frame row per line so the grids stay reviewable as text."""
    lines = ['{']
    lines.append(f'  "creature": {json.dumps(data["creature"])},')
    lines.append(f'  "size": {data["size"]},')
    lines.append(f'  "slots": {json.dumps(data["slots"])},')
    lines.append('  "variants": {')
    vs = list(data["variants"].items())
    for i, (k, v) in enumerate(vs):
        lines.append(f'    {json.dumps(k)}: {json.dumps(v)}' + (',' if i < len(vs) - 1 else ''))
    lines.append('  },')
    lines.append(f'  "defaultVariant": {json.dumps(data["defaultVariant"])},')
    lines.append('  "frames": {')
    fs = list(data["frames"].items())
    for i, (k, rows) in enumerate(fs):
        lines.append(f'    {json.dumps(k)}: [')
        for j, r in enumerate(rows):
            lines.append(f'      {json.dumps(r)}' + (',' if j < len(rows) - 1 else ''))
        lines.append('    ]' + (',' if i < len(fs) - 1 else ''))
    lines.append('  }')
    lines.append('}')
    out = '\n'.join(lines) + '\n'
    assert json.loads(out) == data
    return out


def write(key, slots, variants, frames):
    """Check and write core/domain/src/main/resources/creatures/<key>.json."""
    check_variants(key, variants, slots)
    assert tuple(frames) == FRAME_ORDER, f'{key}: frames {list(frames)}'
    out_frames = {}
    for name, g in frames.items():
        if not name.startswith('defeat') or name == 'defeat1':
            g = seal(copy(g))
        rows = rows_of(g)
        assert len(rows) == SIZE and all(len(r) == SIZE for r in rows), f'{key} {name}: not 32 x 32'
        bad = {c for r in rows for c in r} - set(slots) - {'.'}
        assert not bad, f'{key} {name}: unknown slots {bad}'
        out_frames[name] = rows
    eye = [k for k, v in slots.items() if v == 'eye']
    assert len(eye) == 1, f'{key}: one slot must be labelled "eye"'
    data = {"creature": key, "size": SIZE, "slots": slots, "variants": variants,
            "defaultVariant": "forest", "frames": out_frames}
    path = os.path.join(CREATURES, f'{key}.json')
    with open(path, 'w', newline='\n') as f:
        f.write(dump(data))
    return path
