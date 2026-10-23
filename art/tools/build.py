"""Dev tool: derives every frame from the hand-drawn base and writes ../dragon.json.

Frames are built as (base grid) + (region moves) + (explicit hand-placed pixel edits),
so each change stays reviewable and reproducible.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import spritekit  # noqa: E402
# The app reads sprites from the domain module's resources, so that's the one copy.
OUT = os.path.join(HERE, '..', '..', 'core', 'domain', 'src', 'main', 'resources', 'creatures', 'dragon.json')

BASE = [
    ".............oo..............o..",
    ".........oooohao...o.......oolo.",
    ".......oolllhao...oao...ooollo..",
    "....ooolllllaoo....oaooolllddo..",
    "..oollllloooomdo...ollllddddddo.",
    ".ollommmmeoemmdo...olmmmmddddo..",
    ".olmmmmmmmllmmdooooolmdddmmmddo.",
    ".ommmmmmmommmmdmmdolmdmdddddmmdo",
    ".odoooooommmmmdmmdolmddmddddddmo",
    "..odammmmmmmmdmmdolmddddmddddoo.",
    "...ooooooobbbmmmdolmdddddmddo...",
    ".........obbbmmmdlmoddddddmdo...",
    ".........obbbmmmdlmdoddddddmdo..",
    ".........obbbmmmdlmddodddooomo..",
    "........ooooooooolmdmdooo...o...",
    "........olllllllllmommdo.....o..",
    "........oaammmmmmmmommmdo...oao.",
    "........oooooooooooommmdo..oaaao",
    "........obbbbbbblmmoooodo..oaaao",
    "........obbbbbbblmollllodo..oao.",
    "........ollllllllolllmmdo..olmo.",
    "........obbbbbbbollmmmmdo..olmo.",
    "........obbbbbbbolmmmmddo.olmdo.",
    "........olllllllommmmmddollmmdo.",
    ".........obbbbbbommmmdddommmdo..",
    ".........obbbbbblommdddommddo...",
    ".........oobbbblllodddoddddo....",
    "........olmdoooooommddooooo.....",
    "........olmdo..olmmmddo.........",
    ".......olmmdolllmmmmdddo........",
    ".....oaamdddoaamdddddddo........",
    ".....ooooooooooooooooooo........",
]

VARIANTS = {
    # The app's own palette (docs/design/fieldbook-v2/b/tokens.json): forest greens, marigold horns.
    "forest": {"o": "#0b2017", "d": "#124f39", "m": "#1f7a50", "l": "#4fae70",
               "b": "#f3e4b6", "a": "#f2a922", "e": "#ffe27a", "h": "#fbf8e8"},
    "ember": {"o": "#2a1519", "d": "#6b2b2d", "m": "#a3453a", "l": "#cf7a4f",
              "b": "#e6c48c", "a": "#efe3c6", "e": "#f2cd55", "h": "#fff7e6"},
    "moss":  {"o": "#16201a", "d": "#2f4a33", "m": "#4d7347", "l": "#87a35e",
              "b": "#dcc99a", "a": "#ece4c8", "e": "#e8a33d", "h": "#f8f6e6"},
    "frost": {"o": "#161b2b", "d": "#2e4466", "m": "#4a6f98", "l": "#86acc7",
              "b": "#cfdcd9", "a": "#f1ede2", "e": "#f0b44c", "h": "#fbfbf6"},
}


# ---------------------------------------------------------------- helpers
def grid(rows):
    return [list(r) for r in rows]


def rows_of(g):
    return [''.join(r) for r in g]


def put(g, x, y, s):
    """Write string s starting at (x, y). ' ' leaves a pixel unchanged."""
    for i, c in enumerate(s):
        if c != ' ':
            g[y][x + i] = c


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
        if 0 <= x + dx < 32 and 0 <= y + dy < 32:
            g[y + dy][x + dx] = c


def drop_row(g, row, top=0):
    """Remove `row`, shifting rows top..row-1 down by one (row `top` becomes blank)."""
    for y in range(row, top, -1):
        g[y] = list(g[y - 1])
    g[top] = ['.'] * 32


def segs(g, table):
    """table: {y: [(x, chars), ...]} written with put()."""
    for y, items in table.items():
        for x, s in items:
            put(g, x, y, s)


def pix(spec):
    """spec: [(y, x0, chars)] -> {(x, y): c}, '.' skipped."""
    d = {}
    for y, x0, s in spec:
        for i, c in enumerate(s):
            if c != '.':
                d[(x0 + i, y)] = c
    return d


def draw_behind(g, fill):
    """Outline (4-neighbour) + fill, painted only where the grid is transparent,
    so the new part sits behind everything already drawn."""
    for (x, y) in fill:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in fill and 0 <= q[0] < 32 and 0 <= q[1] < 32 and g[q[1]][q[0]] == '.':
                g[q[1]][q[0]] = 'o'
    for (x, y), c in fill.items():
        if g[y][x] == '.':
            g[y][x] = c


# ---------------------------------------------------------------- parts
# pixels of the raised wing in BASE (row: inclusive x spans)
WING_SPANS = {0: [(29, 29)], 1: [(19, 19), (27, 30)], 2: [(18, 20), (24, 29)],
              3: [(19, 29)], 4: [(19, 30)], 5: [(19, 29)], 6: [(19, 30)],
              7: [(19, 31)], 8: [(19, 31)], 9: [(18, 30)], 10: [(18, 28)],
              11: [(20, 28)], 12: [(21, 29)], 13: [(22, 29)], 14: [(23, 24), (28, 28)]}


def clear_wing(g):
    for y, spans in WING_SPANS.items():
        for a, b in spans:
            for x in range(a, b + 1):
                g[y][x] = '.'


# limp wing: wrist sagged, leading edge flattened, fingers hanging (fill only)
WING_DROOP = pix([
    (4, 21, 'a'),
    (5, 21, 'll'),
    (6, 20, 'lmmlllllll'),
    (7, 19, 'lmmmdddddddl'),
    (8, 19, 'lmmdmmdddddd'),
    (9, 18, 'lmddmddmdddd'),
    (10, 18, 'lmddmdddmmd'),
    (11, 20, 'dddmddddmd'),
    (12, 21, 'ddmdddddmd'),
    (13, 22, 'ddmddd..m'),
    (14, 23, 'dmdd'),
    (15, 24, 'm'),
])

# head thrown back: snout up, mouth open in a yelp, eye squeezed shut ('<')
HURT_HEAD = {
    0: [(8, 'oooooooooo')],
    1: [(7, 'o'), (8, 'lllllll'), (15, 'haao')],
    2: [(5, 'olo'), (8, 'lllll'), (13, 'oommoo')],
    3: [(4, 'ol'), (6, 'mmmmmm'), (12, 'ollmmdo')],
    4: [(4, 'o'), (5, 'mmmmmmmm'), (13, 'oommmd')],
    5: [(5, 'ooo'), (8, 'mmmmmmmmmm'), (18, 'd')],
    6: [(6, 'oooo'), (10, 'mmmmmmm'), (17, 'dd')],
    7: [(5, 'oam'), (8, 'oooo'), (12, 'mmmmmd')],
    8: [(5, 'olmmm'), (10, 'ooo'), (13, 'mmmdd')],
    9: [(6, 'ommmmm'), (12, 'oo'), (14, 'mmd')],
    10: [(7, 'ommmmm'), (13, 'ommd')],
    11: [(8, 'oddddd'), (14, 'mmmdd')],
    12: [(9, 'obbbbb'), (15, 'mmmdd')],
    13: [(9, 'obbbbbb'), (16, 'mmmmd')],
}

# crossed arms removed: bare chest plates (arms flung back)
BARE_CHEST = {
    14: [(8, 'olllllllmmmmmd')],
    15: [(8, 'obbbbbbblmmmmmd')],
    16: [(8, 'obbbbbbblmmmmmmd')],
    17: [(8, 'olllllllmmmmmmmd')],
}


def chip_horn(g):
    """Horn tip snapped off: a flat-topped stub."""
    segs(g, {0: [(13, '..')], 1: [(12, 'oo..')], 2: [(12, 'ha'), (14, 'o')]})


def black_eye(g, dy=0):
    """Bruises: a dark ring round the eye and a welt on the snout."""
    segs(g, {4 + dy: [(8, 'd')], 5 + dy: [(8, 'd'), (12, 'd')], 6 + dy: [(9, 'ddd')]})
    segs(g, {6 + dy: [(5, 'dd')], 7 + dy: [(5, 'dd')]})


# barely standing: slumped 2px + head hung 2px lower, wing sagging, arm limp,
# tail on the ground, lid dropped, smoke curling from the snout.
# Derived from rough(), then hand-finished, so it is kept as a full grid.
BARELY = [
    "..o.............................",
    ".oao............................",
    "..oao...........................",
    "..oao...........................",
    ".oao............................",
    ".oao.....ooooo..................",
    "..oho..oolllhao.................",
    "...oooolllllaoo......o..........",
    "..oolllldddddmdooooooao.........",
    ".ollommmdoooomdomdollllooooooo..",
    ".olmmmmmmdeemmdomdollmmlllllllo.",
    ".ommmmmmmmmmmmdmdolmmmmdddddddlo",
    ".odooooooommmmdmdolmmmdmmddddddo",
    "..odammmmmmmmdmmmmdoddmddmddddo.",
    "...ooooooobbbmmmmmddodmdddmmdo..",
    ".........olmobbbmmmmdodmddddmdo.",
    "........olmollllmmmmmdomdddddmdo",
    ".......olmobbbbblmmmmmdomdddoomo",
    ".......olmobbbbblmmmmmmdoddo..o.",
    "......olmmolllllmmmmmmmdooo.....",
    "......oaaaobbbbblmmoooodo.......",
    ".......ooobbbbbblmollllodo......",
    "........ollllllllolllmmdo.......",
    "........obbbbbbbolmmmmddooo.....",
    "........olllllllommmmmddolmo....",
    ".........obbbbbblommdddolmmdo...",
    ".........oobbbblllodddolmmmdo...",
    "........olmdoooooommddoddmmmdo..",
    "........olmdo..olmmmddooooddaao.",
    ".......olmmdolllmmmmdddo..oaahao",
    ".....oaamdddoaamdddddddo...oaao.",
    ".....ooooooooooooooooooo....oo..",
]


# ---------------------------------------------------------------- frames
def idle1():
    return grid(BASE)


def idle2():
    g = grid(BASE)
    drop_row(g, 21)          # chest, head, wing settle 1px; legs stay planted
    return g


def hurt():
    g = grid(BASE)
    # clear the smug head and the neck in front of the wing
    for y in range(0, 11):
        for x in range(0, 19 if y < 7 else (18 if y < 9 else 17)):
            g[y][x] = '.'
    segs(g, HURT_HEAD)
    segs(g, BARE_CHEST)
    return g


def rough():
    g = grid(BASE)
    clear_wing(g)
    draw_behind(g, WING_DROOP)
    chip_horn(g)
    black_eye(g)
    return g


def barely():
    g = grid(BARELY)
    segs(g, {10: [(5, 'dd')], 11: [(5, 'dd')]})   # snout welt (head sits 4px lower)
    return g


def defeat1():
    """Knocked out: eyes shut, and the body crumples 3 px on to its legs. The smoke still rises."""
    g = barely()
    segs(g, {10: [(10, 'oo')]})
    for _ in range(3):
        spritekit.sink(g, 26)
    return g


FRAMES = {
    "idle1": idle1,
    "idle2": idle2,
    "hurt": hurt,
    "rough": rough,
    "barely": barely,
    "defeat1": defeat1,
}


def build():
    frames = {k: rows_of(f()) for k, f in FRAMES.items()}
    # then it breaks apart into pixels, as every creature does (spritekit.breakup)
    for i, g in enumerate(spritekit.breakup(defeat1(), seed='dragon'), start=2):
        frames[f"defeat{i}"] = rows_of(g)
    data = {
        "creature": "dragon",
        "size": 32,
        "slots": {"o": "outline", "d": "dark", "m": "mid", "l": "light", "b": "belly",
                  "a": "accent (horns, spikes, claws)", "e": "eye", "h": "highlight"},
        "variants": VARIANTS,
        "defaultVariant": "forest",
        "frames": frames,
    }
    with open(OUT, 'w', newline='\n') as f:
        f.write(dump(data))
    return data


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


if __name__ == '__main__':
    d = build()
    # also emit a .src for the dev viewers
    with open(os.path.join(HERE, 'frames.src'), 'w') as f:
        for k, rows in d["frames"].items():
            f.write(f'== {k}\n' + '\n'.join(rows) + '\n')
    print('wrote', os.path.normpath(OUT), 'frames:', ', '.join(d["frames"]))
