"""Dev tool: draws the gear and badge icons, 16 x 16 in the master palette, and writes them for the app.

Usage: python -I icons.py
       python -I icons.py zoom OUT.png KEY [KEY ...]    a close-up of some icons with a pixel grid

Needs only Python 3 (pixelfont.py writes the PNGs). It reads the one master palette for creatures,
gear and badges, core/domain/src/main/resources/palette.json (ramps of colours, shared with the
creature tools), and writes:
  core/domain/src/main/resources/icons/gear.json      8 gear icons, in unlock order
  core/domain/src/main/resources/icons/badges.json    a badge for each achievement in PLAN 8.1
  art/previews/icons-gear.png, icons-badges.png       preview sheets at 8x, light and dark
  art/previews/icons-3x.png                           every icon at 3x, about its size on a phone
Each icon file carries the colour key below, one letter per master palette colour, as the sprite
sheets carry their slot colours.

Each icon is drawn as fills only, and the ink outline is added round the outside, as the signed-off
wooden sword was (docs/design/fieldbook-v2/tools/build.py). Badges are a group frame with a glyph
stamped in the middle, so a group reads as a set: logging is a ledger page, matching the plan a
shield, beating the plan a medal, habits a hexagon, and the end a trophy. The domain tests in
`domain/game` check the same rules this tool does: every key has an icon, 8 colours at most.

The art is cosmetic. It stays clear of anything that reads as smoking or vaping: no ball on a
stick (a match), no clouds or waves near breathing (vapour).
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from pixelfont import Image, text_width  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
RESOURCES = os.path.join(ROOT, 'core', 'domain', 'src', 'main', 'resources')
MASTER = os.path.join(RESOURCES, 'palette.json')
OUT = os.path.join(RESOURCES, 'icons')
PREVIEWS = os.path.join(ROOT, 'art', 'previews')
SIZE = 16
MAX_COLOURS = 8

# ---------------------------------------------------------------- the colour key
# The icons draw from the master palette through this key, one letter per colour: the forest
# dragon's greens and its ember and frost ramps, the signed-off wooden sword's wood, stone for
# steel, and the app's marigold and cream. Every colour must be in palette.json; any icon uses 8
# at most.
KEY = {
    'k': ('#0b2017', 'ink, every outline'),
    'G': ('#124f39', 'forest dark'),
    'g': ('#1f7a50', 'forest'),
    'v': ('#4fae70', 'leaf'),
    'u': ('#9a6508', 'marigold dark'),
    'y': ('#f2a922', 'marigold'),
    'Y': ('#ffe27a', 'gold light'),
    'w': ('#6b4423', 'wood dark'),
    'W': ('#b07a43', 'wood'),
    't': ('#d9a866', 'wood light'),
    'c': ('#f3e4b6', 'cream'),
    'h': ('#fbf8e8', 'highlight'),
    's': ('#3c3a30', 'stone dark'),
    'S': ('#6f6c62', 'stone'),
    'e': ('#a6a398', 'stone light'),
    'E': ('#d5d2c5', 'stone pale'),
    'r': ('#6b2b2d', 'ember dark'),
    'R': ('#a3453a', 'ember'),
    'f': ('#cf7a4f', 'ember light'),
    'n': ('#2e4466', 'frost dark'),
    'b': ('#4a6f98', 'frost'),
    'i': ('#86acc7', 'frost light'),
    'I': ('#cfdcd9', 'frost pale'),
}
OUTLINE = 'k'


def master_colours():
    """Every colour in the master palette, lower case."""
    with open(MASTER, encoding='utf-8') as f:
        return {c.lower() for ramp in json.load(f)['ramps'].values() for c in ramp}


def rgb(letter):
    h = KEY[letter][0]
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


# ---------------------------------------------------------------- grid helpers
def grid(rows):
    assert len(rows) == SIZE, f"{len(rows)} rows"
    for r in rows:
        assert len(r) == SIZE, f"row {r!r} is {len(r)} wide"
    return [list(r) for r in rows]


def rows_of(g):
    return [''.join(r) for r in g]


BARE = {'1': 'S'}  # fills drawn without an outline, such as the bow string


def outline(g, ink=OUTLINE):
    """Ink round every filled pixel, on its four sides, where the grid is clear. Bare pixels (the
    digits in BARE) and pixels already in ink get no outline of their own, so they stay fine lines."""
    src = [r[:] for r in g]
    for y in range(SIZE):
        for x in range(SIZE):
            if src[y][x] != '.':
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < SIZE and 0 <= yy < SIZE and src[yy][xx] not in BARE and src[yy][xx] not in '.' + ink:
                    g[y][x] = ink
                    break
    for row in g:
        for x, ch in enumerate(row):
            row[x] = BARE.get(ch, ch)
    return g


def stamp(g, art, x0, y0, colours):
    """Paste art (rows of keys) at (x0, y0), inside the frame only: never on its outline or outside
    it. '.' and ' ' are skipped; keys map through colours, and other letters are palette letters."""
    for dy, row in enumerate(art):
        for dx, ch in enumerate(row):
            if ch in '. ':
                continue
            if g[y0 + dy][x0 + dx] in '.' + OUTLINE:
                continue
            g[y0 + dy][x0 + dx] = colours.get(ch, ch)


def centred(art, box):
    """Top left for art centred in box (x, y, w, h), leaning up and left when it can't be exact."""
    x, y, w, h = box
    aw, ah = max(len(r) for r in art), len(art)
    assert aw <= w and ah <= h, f"art {aw}x{ah} is bigger than its box {w}x{h}"
    return x + (w - aw) // 2, y + (h - ah) // 2


def with_shadow(art, key='1', shadow='2'):
    """Adds a drop shadow one pixel down and right of every `key` pixel, for raised numbers."""
    h, w = len(art) + 1, max(len(r) for r in art) + 1
    g = [['.'] * w for _ in range(h)]
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch == key and g[y + 1][x + 1] == '.':
                g[y + 1][x + 1] = shadow
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch != '.':
                g[y][x] = ch
    return [''.join(r) for r in g]


# ---------------------------------------------------------------- numbers on badges
DIGITS = {
    '0': ["111", "1.1", "1.1", "1.1", "111"],
    '1': [".1", "11", ".1", ".1", ".1"],
    '2': ["111", "..1", "111", "1..", "111"],
    '3': ["111", "..1", ".11", "..1", "111"],
    '4': ["1.1", "1.1", "111", "..1", "..1"],
    '5': ["111", "1..", "111", "..1", "111"],
    '6': ["111", "1..", "111", "1.1", "111"],
    '7': ["111", "..1", "..1", "..1", "..1"],
    '8': ["111", "1.1", "111", "1.1", "111"],
    '9': ["111", "1.1", "111", "..1", "111"],
}


def number(n):
    parts = [DIGITS[d] for d in str(n)]
    return [".".join(p[y] for p in parts) for y in range(5)]


def above(top, bottom, gap=1):
    """Stacks two pieces of art, centred on each other."""
    w = max(max(len(r) for r in top), max(len(r) for r in bottom))

    def pad(a):
        aw = max(len(r) for r in a)
        left = (w - aw) // 2
        return ['.' * left + r.ljust(aw, '.') + '.' * (w - aw - left) for r in a]
    return pad(top) + ['.' * w] * gap + pad(bottom)


# ---------------------------------------------------------------- gear
# Fills only: outline() adds the ink. Everything points up and right like the signed-off wooden
# sword, handle at the bottom left, so the gear row reads as one set. Digits are bare pixels
# (see BARE), drawn without an outline.
GEAR = {}

# A crooked branch with a knot and a sprig, the starting weapon.
GEAR['wooden_stick'] = [
    "................",
    "................",
    "....vv......tW..",
    "...vvg.....tW...",
    "....gw.....tw...",
    "......w...tW....",
    ".......w.tW.....",
    "........tW......",
    "........tw......",
    ".......tW.......",
    "......tWw.......",
    ".....tw.........",
    "....tW..........",
    "...tW...........",
    "..tw............",
    "................",
]

# The signed-off wooden sword from the lair mockup, in the master palette.
GEAR['wooden_sword'] = [
    "................",
    "................",
    ".............h..",
    "............tW..",
    "...........tW...",
    "..........tW....",
    ".........tW.....",
    "........tW......",
    ".......tW.......",
    "...w..tW........",
    "....wtW.........",
    ".....w..........",
    "....y.w.........",
    "...y...w........",
    "..w.............",
    "................",
]

# A broader steel blade with a fuller, a marigold guard and pommel.
GEAR['iron_sword'] = [
    "................",
    "..............h.",
    "............heS.",
    "...........EeS..",
    "..........EeS...",
    ".........EeS....",
    "........EeS.....",
    ".......EeS......",
    "...u..EeS.......",
    "....yEeS........",
    ".....yS.........",
    "....w.y.........",
    "...w...u........",
    ".uy.............",
    "..u.............",
    "................",
]

# A bearded steel head facing up and left, with a short poll behind the haft.
GEAR['axe'] = [
    "................",
    "........EEeS....",
    ".......EeeeSS...",
    "......EeeeSStW..",
    ".....hEeeSStWS..",
    ".....EeeSStWS...",
    "......EeStW.....",
    ".......StW......",
    ".......tW.......",
    "......tW........",
    ".....tW.........",
    "....tW..........",
    "...tW...........",
    "..tW............",
    "..W.............",
    "................",
]

# A heavy block across the top of the haft, lit from the top left.
GEAR['war_hammer'] = [
    "................",
    ".........hE.....",
    "........hEEE....",
    "........eEeeE...",
    ".........SeeSE..",
    "..........SSSSE.",
    ".........tWSSss.",
    "........tW..ss..",
    ".......tW.......",
    "......tW........",
    ".....tW.........",
    "....tW..........",
    "...tW...........",
    "..tW............",
    "..W.............",
    "................",
]

# A staff with a forked head, the flame held up between its two prongs. Not a ball on a stick,
# which reads as a match, and the prongs are in the light woods so the fork still shows on the
# dark page, where the ink outline disappears.
GEAR['fire_staff'] = [
    "................",
    "...........f....",
    "..........fy....",
    "........t.fYf.W.",
    "........t.yYy.t.",
    "........tW.R.tW.",
    ".........tWWtW..",
    "..........tW....",
    ".........tW.....",
    "........tW......",
    ".......tW.......",
    "......tW........",
    ".....tW.........",
    "....tW..........",
    "...tW...........",
    "...W............",
]

# A wooden bow with its string drawn bare, loosing a lightning bolt.
GEAR['lightning_bow'] = [
    "................",
    "............Y...",
    "...........YY...",
    "..wWtttW..Yy....",
    "...1....WYy.....",
    "....1...YYYW....",
    ".....1...yY.W...",
    "......1.Yy..W...",
    ".......YY....W..",
    "........1....W..",
    ".........1...W..",
    "..........1..W..",
    "...........1.W..",
    "............1w..",
    "................",
    "................",
]

# The iron sword's shape made grand: a frost blade, a gold guard swept towards the blade, a ruby,
# and a wrapped gold grip.
GEAR['legendary_blade'] = [
    "................",
    "..............h.",
    "............hIi.",
    "...........hIi..",
    "..........hIi...",
    ".........hIi....",
    "........hIi.....",
    "...y...hIi......",
    "...Y..hIi.......",
    "....yhIi........",
    ".....Ri.........",
    "....u.y.........",
    "...y...uu.......",
    ".yY.............",
    "..u.............",
    "................",
]

GEAR_NAMES = {
    'wooden_stick': 'Wooden stick', 'wooden_sword': 'Wooden sword', 'iron_sword': 'Iron sword',
    'axe': 'Axe', 'war_hammer': 'War hammer', 'fire_staff': 'Fire staff',
    'lightning_bow': 'Lightning bow', 'legendary_blade': 'Legendary blade',
}


def gear_icon(key):
    return rows_of(outline(grid(GEAR[key])))


# ---------------------------------------------------------------- badge frames
# Each frame is drawn as fills; outline() inks it. A glyph is centred in `box` and only ever lands
# inside the frame. `ink` maps a glyph's keys: 1 the glyph, 2 its shadow, 3 an accent.
FRAMES = {
    # Logging: a ledger page under a wooden header with two ring holes, its corner turned.
    'logging': {
        'fill': [
            "................",
            ".WWhWWWWWWWWhWw.",
            ".wwwwwwwwwwwwww.",
            ".hhhhhhhhhhhhhc.",
            ".hcccccccccccce.",
            ".hcccccccccccce.",
            ".hcccccccccccce.",
            ".hcccccccccccce.",
            ".hcccccccccccce.",
            ".hcccccccccccce.",
            ".hcccccccccccce.",
            ".hcccccccccccce.",
            ".hcccccccccccce.",
            ".hccccccccccce..",
            ".ceeeeeeeeeee...",
            "................",
        ],
        'box': (2, 4, 12, 10),
        'ink': {'1': 'w', '2': 't', '3': 'R'},
    },
    # Matching the plan: a shield in the forest greens, since green means at or under the guide.
    'matching': {
        'fill': [
            "................",
            ".vvvvvvvvvvvvvv.",
            ".vggggggggggggG.",
            ".vggggggggggggG.",
            ".vggggggggggggG.",
            ".vggggggggggggG.",
            ".vggggggggggggG.",
            ".vggggggggggggG.",
            ".vggggggggggggG.",
            "..vggggggggggG..",
            "..vggggggggggG..",
            "...vggggggggG...",
            "....vggggggG....",
            ".....vggggG.....",
            "......GGGG......",
            "................",
        ],
        'box': (2, 2, 12, 10),
        'ink': {'1': 'h', '2': 'G', '3': 'Y'},
    },
    # Beating the plan: a marigold medal with two short ember ribbons close under it, the game
    # layer's colours. (A scalloped rosette read as a cog, which means settings; ribbons at the
    # sides read as legs, and a ribbon above as horns.)
    'beating': {
        'fill': [
            "................",
            "......YYYY......",
            "....YYyyyyYY....",
            "...Yyyyyyyyyu...",
            "...Yyyyyyyyyu...",
            "..Yyyyyyyyyyyu..",
            "..Yyyyyyyyyyyu..",
            "..Yyyyyyyyyyyu..",
            "..Yyyyyyyyyyyu..",
            "...Yyyyyyyyyu...",
            "...Yyyyyyyyyu...",
            "....uuyyyyuu....",
            ".....Ruuuur.....",
            "....RRr..RRr....",
            "....R.r..R.r....",
            "................",
        ],
        'box': (2, 2, 12, 9),
        # No drop shadow here: on marigold it muddied the glyphs.
        'shadow': False,
        'ink': {'1': 'w', '2': 'y', '3': 'h'},
    },
    # Habits: a frost hexagon.
    'habits': {
        'fill': [
            "................",
            ".......ii.......",
            ".....iibbbb.....",
            "...iibbbbbbbb...",
            ".iibbbbbbbbbbbb.",
            ".ibbbbbbbbbbbbn.",
            ".ibbbbbbbbbbbbn.",
            ".ibbbbbbbbbbbbn.",
            ".ibbbbbbbbbbbbn.",
            ".ibbbbbbbbbbbbn.",
            ".ibbbbbbbbbbbbn.",
            ".nbbbbbbbbbbbbn.",
            "...nnbbbbbbnn...",
            ".....nnbbnn.....",
            ".......nn.......",
            "................",
        ],
        'box': (2, 3, 12, 10),
        'ink': {'1': 'h', '2': 'n', '3': 'Y'},
    },
}

# The end: a trophy of its own, on a wooden plinth.
THE_END = [
    "................",
    "...hYYYYYYYYu...",
    ".yyYhyyyyyyyuuu.",
    ".y.Yhyyyyyyyu.u.",
    ".y.Yhyyyyyyyu.u.",
    "..yYhyyyyyyyuu..",
    "....Yhyyyyyu....",
    ".....Yyyyyu.....",
    "......Yyyu......",
    ".......yu.......",
    ".......yu.......",
    "......Yyuu......",
    ".....tWWWWw.....",
    "....tWWWWWWw....",
    "....wwwwwwww....",
    "................",
]


# ---------------------------------------------------------------- badge glyphs
def overlay(back, front, dx, dy):
    """Front drawn over back, with front's top left at (dx, dy) in back; both grow to fit."""
    w = max(max(len(r) for r in back), dx + max(len(r) for r in front))
    h = max(len(back), dy + len(front))
    g = [['.'] * w for _ in range(h)]
    for y, row in enumerate(back):
        for x, ch in enumerate(row):
            g[y][x] = ch
    for y, row in enumerate(front):
        for x, ch in enumerate(row):
            if ch != '.':
                g[dy + y][dx + x] = ch
    return [''.join(r) for r in g]


def shadowed_above(top, bottom):
    """A shadowed number or glyph over a second piece that keeps no shadow, such as a chain."""
    return above(with_shadow(top), bottom)


CHAIN = [".11.11.11.", "1..1..1..1", ".11.11.11."]


def breaths(n):
    """The count in a box-breathing square, with the timer's dot on its edge. No waves or clouds,
    which could read as vapour."""
    digits = number(n)
    w = len(digits[0])
    box = [".111111111.", "1.........1"] + ["1" + "." * 9 + "1"] * 5 + ["1.........1", ".111111111."]
    box[0] = ".13" + box[0][3:]
    left = 1 + (9 - w) // 2
    return overlay(box, digits, left, 2)


# A luggage tag; 'c' fills it with the page's cream, so a tag in front hides the one behind.
TAG = [
    "...111111",
    "..1ccccc1",
    ".1cc1ccc1",
    "..1ccccc1",
    "...111111",
]

NO_SHADOW = {'shadow': False}


def exploded(main, wedge, dx=1, dy=-1):
    """A pie chart: `main` with `wedge` pulled out by (dx, dy), both drawn on the same square."""
    n = len(main)
    g = [['.'] * (n + dx) for _ in range(n - dy)]
    for part, (ox, oy) in ((main, (0, -dy)), (wedge, (dx, 0))):
        for y, row in enumerate(part):
            for x, ch in enumerate(row):
                if ch != '.':
                    g[oy + y][ox + x] = ch
    return [''.join(r) for r in g]


GLYPHS = {
    # A yellow pencil: grey ferrule, cream cone and a dark point. Drawn without the drop shadow,
    # which in the page's grey ran into the ferrule. (Brown with a red end read as a cigar.)
    'pencil': [
        "......111.",
        ".....1eee1",
        "....1yyee1",
        "...1yyuu1.",
        "..1yyuu1..",
        ".1hyuu1...",
        "1hhhh1....",
        "1khh1.....",
        "1111......",
    ],
    'ruler': [
        "11111111111",
        "1.1.1.1.1.1",
        "1.1...1...1",
        "1.........1",
        "11111111111",
    ],
    # The History tab's icon: a clock whose ring turns back, its arrowhead at the top left, with
    # the hands at 12 and 3. (Hands on the diagonal read as a no-entry sign.)
    'history': [
        ".1..11111..",
        ".1.1.....1.",
        ".111..1...1",
        "......1...1",
        "..1...11..1",
        "..1.......1",
        "..1.......1",
        "...1.....1.",
        "....11111..",
    ],
    'tag': TAG,
    # Two tags, the one behind in wood so each keeps its point and hole. (Three cream tags read as
    # a stack of paper.)
    'tags': overlay([r.replace('c', 'W') for r in TAG], ['..' + r for r in TAG], 0, 4),
    'rebound': [
        "11.....1111",
        ".11.....111",
        "..11...11.1",
        "...11.11...",
        "....111....",
    ],
    'tick': [
        ".......11",
        "......11.",
        "1....11..",
        "11..11...",
        ".1111....",
        "..11.....",
    ],
    'half': [
        "..1111..",
        ".111..1.",
        "1111...1",
        "1111...1",
        "1111...1",
        "1111...1",
        ".111..1.",
        "..1111..",
    ],
    'down': [
        "..111..",
        "..111..",
        "..111..",
        "1111111",
        ".11111.",
        "..111..",
        "...1...",
    ],
    'pennant': [
        "11....",
        "1111..",
        "111111",
        "1111..",
        "11....",
        "1.....",
        "1.....",
        "1.....",
    ],
    'percent10': [
        ".1.111.1.1",
        "11.1.1...1",
        ".1.1.1..1.",
        ".1.1.1.1..",
        ".1.111.1.1",
    ],
    # The medal's glyphs fill its whole box (12 x 9) so they sit exactly: the dashed guide stays
    # inside the face, clear of the rim. Five days in columns under the guide, like Today's week.
    'chart5': [
        "............",
        "............",
        "..11.11.11..",
        "............",
        "..1...1.....",
        "..1.1.1.1...",
        "..1.1.1.1.1.",
        "............",
        "............",
    ],
    # Three weeks as wide bars, each lower than the last, under the guide. (A bare 3 on a medal read
    # as third place, and chevrons merged into a lattice.)
    'bars3': [
        "............",
        "............",
        "...111111...",
        "............",
        "..11........",
        "..11.11.....",
        "..11.11.11..",
        "..11.11.11..",
        "............",
    ],
    'clock': [
        "..111..",
        ".1.1..1",
        "1..1...1",
        "1..11..1",
        "1......1",
        ".1....1.",
        "..1111..",
    ],
    # Two doses, the gold marks, with a long arrow between them. (Bars at the ends read as a
    # dumbbell.)
    'gap': [
        "..1.....1..",
        ".1.......1.",
        "31111111113",
        ".1.......1.",
        "..1.....1..",
    ],
    # A pie chart with a third pulled out in gold. (An outlined quarter read as a moon, and a tag
    # with an arrow as a fish.)
    'pie': exploded(
        [
            "..11....",
            ".111....",
            "1111....",
            "1111....",
            "11111...",
            "1111111.",
            ".111111.",
            "..1111..",
        ],
        [
            "....33..",
            "....333.",
            "....3333",
            "....3333",
            ".....333",
            ".......3",
            "........",
            "........",
        ],
    ),
}

# The achievements of PLAN 8.1, in its order: (key, group, glyph[, options for badge_icon]). Tiers
# show their number. The streaks (10, 21, 40) and the last weeks-met tier (10) were tuned from
# PLAN's numbers by the pace test in domain/game (UnlockPaceTest).
BADGES = [
    ('first_entry', 'logging', GLYPHS['pencil'], NO_SHADOW),
    ('logged_days_3', 'logging', number(3)),
    ('logged_days_7', 'logging', number(7)),
    ('logged_days_14', 'logging', number(14)),
    ('logged_days_30', 'logging', number(30)),
    ('logged_days_60', 'logging', number(60)),
    ('logged_days_90', 'logging', number(90)),
    ('streak_10', 'logging', shadowed_above(number(10), CHAIN)),
    ('streak_21', 'logging', shadowed_above(number(21), CHAIN)),
    ('streak_40', 'logging', shadowed_above(number(40), CHAIN)),
    ('baseline_done', 'logging', GLYPHS['ruler']),
    ('honest_ledger', 'logging', GLYPHS['history']),
    ('tagged_25', 'logging', GLYPHS['tag']),
    ('tagged_100', 'logging', GLYPHS['tags']),
    ('first_day_in_guide', 'matching', GLYPHS['tick']),
    ('first_week_met', 'matching', number(1)),
    ('weeks_met_3', 'matching', number(3)),
    ('weeks_met_6', 'matching', number(6)),
    ('weeks_met_10', 'matching', number(10)),
    ('back_in_step', 'matching', GLYPHS['rebound']),
    ('halfway', 'matching', GLYPHS['half']),
    ('down_a_strength', 'matching', GLYPHS['down']),
    ('final_taper_week', 'matching', GLYPHS['pennant']),
    ('week_under_by_10', 'beating', GLYPHS['percent10']),
    ('five_days_under', 'beating', GLYPHS['chart5']),
    ('three_weeks_under', 'beating', GLYPHS['bars3']),
    ('later_first_dose', 'habits', GLYPHS['clock']),
    ('longer_gaps', 'habits', GLYPHS['gap']),
    ('top_trigger_down', 'habits', GLYPHS['pie'], NO_SHADOW),
    ('breathing_1', 'habits', breaths(1)),
    ('breathing_10', 'habits', breaths(10)),
    ('breathing_25', 'habits', breaths(25)),
    ('demon_defeated', 'end', None),
]

BADGE_NAMES = {
    'first_entry': 'First entry', 'logged_days_3': 'Logged on 3 days', 'logged_days_7': 'Logged on 7 days',
    'logged_days_14': 'Logged on 14 days', 'logged_days_30': 'Logged on 30 days',
    'logged_days_60': 'Logged on 60 days', 'logged_days_90': 'Logged on 90 days',
    'streak_10': '10 days in a row', 'streak_21': '21 days in a row', 'streak_40': '40 days in a row',
    'baseline_done': 'Baseline done', 'honest_ledger': 'Honest ledger',
    'tagged_25': 'Tagged 25', 'tagged_100': 'Tagged 100',
    'first_day_in_guide': 'First day in the guide', 'first_week_met': 'First week met',
    'weeks_met_3': '3 weeks met', 'weeks_met_6': '6 weeks met', 'weeks_met_10': '10 weeks met',
    'back_in_step': 'Back in step', 'halfway': 'Halfway', 'down_a_strength': 'Down a strength',
    'final_taper_week': 'Final taper week', 'week_under_by_10': 'A week 10% under',
    'five_days_under': '5 days under in a week', 'three_weeks_under': '3 weeks under in a row',
    'later_first_dose': 'Later first dose', 'longer_gaps': 'Longer gaps',
    'top_trigger_down': 'Top trigger down', 'breathing_1': '1 breathing session',
    'breathing_10': '10 breathing sessions', 'breathing_25': '25 breathing sessions',
    'demon_defeated': 'Demon defeated',
}

GROUPS = [('logging', 'Logging'), ('matching', 'Matching the plan'), ('beating', 'Beating the plan'),
          ('habits', 'Habits'), ('end', 'The end')]


def badge_icon(key, group, art, opts=()):
    """The group's frame with `art` stamped in it. `opts`, if given, is one dict: 'shadow': False
    for a glyph drawn without the drop shadow, and glyph keys that override the frame's ink."""
    if group == 'end':
        return rows_of(outline(grid(THE_END)))
    frame = FRAMES[group]
    opts = dict(opts[0]) if opts else {}
    g = outline(grid(frame['fill']))
    if opts.pop('shadow', frame.get('shadow', True)) and not any('2' in r for r in art):
        art = with_shadow(art)
    x, y = centred(art, frame['box'])
    stamp(g, art, x, y, {**frame['ink'], **opts})
    return rows_of(g)


# ---------------------------------------------------------------- checks and output
def check(kind, icons):
    """Problems with a set of icons, as messages. The domain tests check the same rules."""
    problems = []
    for key, rows in icons.items():
        if len(rows) != SIZE or any(len(r) != SIZE for r in rows):
            problems.append(f"{kind} {key} isn't {SIZE} x {SIZE}")
        used = {c for r in rows for c in r} - {'.'}
        unknown = used - set(KEY)
        if unknown:
            problems.append(f"{kind} {key} uses letters outside the colour key: {sorted(unknown)}")
        if len(used) > MAX_COLOURS:
            problems.append(f"{kind} {key} uses {len(used)} colours: {''.join(sorted(used))}")
    return problems


def dump(data, icon_key='icons'):
    """JSON with one icon row per line, so the grids stay reviewable as text."""
    lines = ['{']
    for k, v in data.items():
        if k == icon_key:
            continue
        lines.append(f'  {json.dumps(k)}: {json.dumps(v)},')
    lines.append(f'  {json.dumps(icon_key)}: {{')
    items = list(data[icon_key].items())
    for i, (key, rows) in enumerate(items):
        lines.append(f'    {json.dumps(key)}: [')
        for j, r in enumerate(rows):
            lines.append(f'      {json.dumps(r)}' + (',' if j < len(rows) - 1 else ''))
        lines.append('    ]' + (',' if i < len(items) - 1 else ''))
    lines.append('  }')
    lines.append('}')
    out = '\n'.join(lines) + '\n'
    assert json.loads(out) == data
    return out


def write(name, text):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name), 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)


def build():
    gear = {key: gear_icon(key) for key in GEAR}
    badges = {key: badge_icon(key, group, art, opts) for key, group, art, *opts in BADGES}
    problems = check('gear', gear) + check('badge', badges)
    master = master_colours()
    problems += [f"key {k} is {v[0]}, which isn't in palette.json" for k, v in KEY.items() if v[0] not in master]

    def key_for(icons):
        used = {c for rows in icons.values() for r in rows for c in r} - {'.'}
        return {k: v[0] for k, v in KEY.items() if k in used}
    write('gear.json', dump({'size': SIZE, 'colours': key_for(gear), 'icons': gear}))
    write('badges.json', dump({
        'size': SIZE,
        'colours': key_for(badges),
        'groups': {g: [k for k, grp, *_ in BADGES if grp == g] for g, _ in GROUPS},
        'icons': badges,
    }))
    return gear, badges, problems


# ---------------------------------------------------------------- previews
LIGHT = {'page': (0xFA, 0xF9, 0xF5), 'tile': (0xEC, 0xE9, 0xDE), 'text': (0x10, 0x22, 0x1A), 'text2': (0x4C, 0x5A, 0x52)}
DARK = {'page': (0x09, 0x13, 0x0F), 'tile': (0x1A, 0x2B, 0x22), 'text': (0xEC, 0xF1, 0xEA), 'text2': (0xA7, 0xB8, 0xAD)}
MARIGOLD = (0xF5, 0xB1, 0x2B)


def blit(img, rows, x, y, scale, bg=None):
    for gy, row in enumerate(rows):
        for gx, ch in enumerate(row):
            if ch != '.':
                img.rect(x + gx * scale, y + gy * scale, scale, scale, rgb(ch))
            elif bg is not None:
                img.rect(x + gx * scale, y + gy * scale, scale, scale, bg)


def wrap(label, width, scale):
    lines, line = [], ''
    for word in label.split(' '):
        trial = (line + ' ' + word).strip()
        if line and text_width(trial, scale) > width:
            lines.append(line)
            line = word
        else:
            line = trial
    return lines + [line]


def sheet(title, items, path, per_row, big=8, swatches=False):
    """items: [(heading or None, [(label, rows)])]. Each icon shows at `big` on the light page, then
    again on the dark page, with 3x copies on the tile and on marigold: about its size on a phone."""
    pad, gap = 28, 24
    cell = SIZE * big
    small = SIZE * 3
    col_w = max(cell, 2 * (small + 12) + 4) + 56
    label_h = 2 * 20 + 6
    width = pad * 2 + per_row * col_w
    sections = []
    for heading, icons in items:
        n_rows = (len(icons) + per_row - 1) // per_row
        sections.append((heading, icons, n_rows))
    item_h = cell + 10 + label_h + small + 12
    height = pad * 2 + 36
    for heading, icons, n_rows in sections:
        height += (34 if heading else 0) + n_rows * (item_h + gap)
    if swatches:
        height += 90
    img = Image(width, height * 2, LIGHT['page'])
    img.rect(0, height, width, height, DARK['page'])
    for half, theme in enumerate((LIGHT, DARK)):
        y = half * height + pad
        img.text(pad, y, f"{title}, {'dark' if half else 'light'} mode", theme['text'], 3)
        y += 36
        for heading, icons, n_rows in sections:
            if heading:
                img.text(pad, y, heading, theme['text'], 2)
                y += 34
            for i, (label, rows) in enumerate(icons):
                cx = pad + (i % per_row) * col_w
                cy = y + (i // per_row) * (item_h + gap)
                blit(img, rows, cx, cy, big)
                for n, line in enumerate(wrap(label, col_w - 16, 2)):
                    img.text(cx, cy + cell + 10 + n * 20, line, theme['text2'], 2)
                ty = cy + cell + 10 + label_h
                img.rect(cx, ty, small + 12, small + 12, theme['tile'])
                blit(img, rows, cx + 6, ty + 6, 3)
                img.rect(cx + small + 16, ty, small + 12, small + 12, MARIGOLD)
                blit(img, rows, cx + small + 22, ty + 6, 3)
            y += n_rows * (item_h + gap)
        if swatches:
            img.text(pad, y, 'Colour key, all from the master palette', theme['text'], 2)
            for i, (letter, (_, name)) in enumerate(KEY.items()):
                sx = pad + i * 44
                img.rect(sx, y + 28, 36, 36, rgb(letter))
                img.text(sx + 14, y + 70, letter, theme['text2'], 1)
    img.save(path)


def strip(path, gear, badges):
    """Every icon at 3x in one block, on each tile colour: how a shelf of them reads."""
    pad, step = 16, SIZE * 3 + 8
    per_row = 14
    keys = [('g', k) for k in gear] + [('b', k) for k, *_ in BADGES]
    rows_n = (len(keys) + per_row - 1) // per_row
    block_h = rows_n * step + pad
    themes = [LIGHT['page'], LIGHT['tile'], DARK['page'], DARK['tile']]
    img = Image(pad * 2 + per_row * step, pad + len(themes) * block_h, (0x80, 0x80, 0x80))
    for t, bg in enumerate(themes):
        y0 = pad + t * block_h
        img.rect(pad // 2, y0 - pad // 2, per_row * step + pad, block_h, bg)
        for i, (kind, key) in enumerate(keys):
            rows = gear[key] if kind == 'g' else badges[key]
            blit(img, rows, pad + (i % per_row) * step, y0 + (i // per_row) * step, 3)
    img.save(path)


def previews(gear, badges):
    os.makedirs(PREVIEWS, exist_ok=True)
    sheet('Gear at 8x, in unlock order', [(None, [(GEAR_NAMES[k], v) for k, v in gear.items()])],
          os.path.join(PREVIEWS, 'icons-gear.png'), per_row=8, swatches=True)
    sections = [(name, [(BADGE_NAMES[k], badges[k]) for k, grp, *_ in BADGES if grp == g]) for g, name in GROUPS]
    sheet('Badges at 8x', sections, os.path.join(PREVIEWS, 'icons-badges.png'), per_row=7)
    strip(os.path.join(PREVIEWS, 'icons-3x.png'), gear, badges)


def zoom(path, icons, keys, scale=20):
    """Dev view: icons at `scale` with a pixel grid and coordinates, on light and dark, plus 3x."""
    cell = SIZE * scale
    w = 40 + len(keys) * (cell + 40)
    img = Image(w, 2 * cell + 200, (0x80, 0x80, 0x80))
    for i, key in enumerate(keys):
        x0 = 30 + i * (cell + 40)
        img.text(x0, 6, key, (0, 0, 0), 2)
        for j, bg in enumerate((LIGHT['page'], DARK['page'])):
            y0 = 30 + j * (cell + 10)
            img.rect(x0, y0, cell, cell, bg)
            blit(img, icons[key], x0, y0, scale)
            for n in range(SIZE + 1):
                img.rect(x0 + n * scale, y0, 1, cell, (0x90, 0x90, 0x90))
                img.rect(x0, y0 + n * scale, cell, 1, (0x90, 0x90, 0x90))
            if j == 0:
                for n in range(0, SIZE, 2):
                    img.text(x0 + n * scale + 4, y0 - 12, str(n), (0, 0, 0), 1)
        y = 40 + 2 * cell + 20
        for j, bg in enumerate((LIGHT['tile'], DARK['tile'], MARIGOLD)):
            img.rect(x0 + j * 60, y, 60, 60, bg)
            blit(img, icons[key], x0 + j * 60 + 6, y + 6, 3)
        img.rect(x0 + 200, y, 40, 40, LIGHT['tile'])
        blit(img, icons[key], x0 + 204, y + 4, 2)
    img.save(path)


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == 'zoom':
        gear, badges, _ = build()
        zoom(sys.argv[2], {**gear, **badges}, sys.argv[3:])
        raise SystemExit(0)
    gear, badges, problems = build()
    previews(gear, badges)
    print(f"wrote {len(gear)} gear and {len(badges)} badges to {os.path.relpath(OUT, ROOT)}, previews to art/previews")
    for p in problems:
        print('PROBLEM', p)
    raise SystemExit(1 if problems else 0)
