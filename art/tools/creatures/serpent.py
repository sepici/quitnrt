"""The serpent: a hooded snake reared up out of its coils, fangs out, tongue flicking."""
from spritekit import *

KEY = 'serpent'
SLOTS = {"o": "outline", "d": "dark", "m": "mid", "l": "light", "b": "belly",
         "a": "accent (tongue, rattle)", "e": "eye", "h": "highlight (fangs)"}
VARIANTS = {
    "forest": {"o": "#0b2017", "d": "#124f39", "m": "#1f7a50", "l": "#4fae70",
               "b": "#f3e4b6", "a": "#f2a922", "e": "#ffe27a", "h": "#fbf8e8"},
    "ember": {"o": "#2a1519", "d": "#6b2b2d", "m": "#a3453a", "l": "#cf7a4f",
              "b": "#e6c48c", "a": "#e8a33d", "e": "#f2cd55", "h": "#fff7e6"},
    "moss": {"o": "#16201a", "d": "#2f4a33", "m": "#4d7347", "l": "#87a35e",
             "b": "#dcc99a", "a": "#f2a922", "e": "#e8a33d", "h": "#f8f6e6"},
    "frost": {"o": "#161b2b", "d": "#2e4466", "m": "#4a6f98", "l": "#86acc7",
              "b": "#cfdcd9", "a": "#f2a922", "e": "#f0b44c", "h": "#fbfbf6"},
}

# the hood, flared behind the head, with the pale belly scales down its front
HOOD = [
    "    ooo          ooo    ",
    "  oolllo        oddoo   ",
    " olmmmmmo      oddddddo ",
    "olmmmmmmmo    odddddddo ",
    "olmmmmmmmmbbbbmmdddddddo",
    "olmmmmmmmmllllmmmddddddo",
    "olmmdmmmmmbbbbmmmdmdddo ",
    " olmmdmmmmllllmmmdmddo  ",
    "  olmmdmmmbbbbmmdmddo   ",
    "   olmmmmmllllmmmddo    ",
    "    oolmmmbbbbmmddo     ",
    "      oolmllllmdoo      ",
    "        olbbbbdo        ",
    "        olllllmo        ",
]

# the head from the front: brows down, eyes at the sides, fangs out
HEAD = [
    "  oooooo  ",
    " olllllmo ",
    "olllllmmdo",
    "oooolmoooo",
    "oeeolmoeeo",
    " omlmmmmo ",
    " omommomo ",
    "  ommmmo  ",
    "  ohooho  ",
    "   oooo   ",
    "          ",
    "          ",
]
# the tongue flicks out, for the idle's second frame
TONGUE_OUT = {9: "    oao   ", 10: "    oao   ", 11: "     o    "}
HEAD_HURT = {3: "oooolmoooo", 4: "ooooloooo ", 5: " omomoomo ",
             8: " ohoooooho", 9: "  oooaooo ", 10: "    oao   ", 11: "     o    "}
HEAD_TIRED = {3: "olllllmmdo", 4: "oooolmoooo", 5: " oeommoeo ", 9: "    oao   ", 10: "    oao   ", 11: "     o    "}


def head(changes=None):
    rows = list(HEAD)
    for y, r in (changes or {}).items():
        rows[y] = r
    return rows


def band(x0, x1, cy, r, phase):
    """One loop of the coil seen side on: a rounded tube, lit along the top, with blotches."""
    shape = capsule([(x0, cy), (x1, cy)], r) & rect(0, 0, 31, 30)
    top = min(y for _, y in shape)
    bottom = max(y for _, y in shape)

    def fill(x, y):
        if y == top or (x, y - 1) not in shape:
            return 'l'
        if y == bottom or (x, y + 1) not in shape:
            return 'd'
        if (x + 1, y) not in shape:
            return 'd'
        if (x - 1, y) not in shape:
            return 'l'
        # blotches down the back, staggered row to row
        if (x + phase + (y - top) * 3) % 7 in (0, 1) and y - top in (1, 2):
            return 'd'
        return 'm'
    return shape, fill


def coils(neck=True):
    """Three loops of coil piled on the rock, the tail curling up behind with its rattle."""
    parts = [(capsule([(25, 27), (28, 24), (29, 20)], 0.9), 'shade')]
    for x0, x1, cy, r, phase in ((3, 28, 28, 2.4, 0), (6, 25, 24.5, 2.0, 3), (10, 21, 21, 1.8, 5)):
        shape, fill = band(x0, x1, cy, r, phase)
        parts.append((shape, fill))
    # the neck rising out of the top loop
    if neck:
        parts.append((rect(13, 15, 18, 20), lambda x, y: 'lmbbmd'[x - 13]))
    g = compose(parts)
    patch(g, 28, 16, [" o", "oao", "oao"])
    return g


def build(head_rows=None, head_dx=0, head_dy=0, hood_dy=0, hood=None):
    g = coils()
    patch(g, 4, 3 + hood_dy, hood or HOOD)
    patch(g, 11 + head_dx, 1 + head_dy, head_rows or HEAD)
    return g


def torn_hood():
    """The hood after a beating: the right flap ripped down from its tip, and a notch torn out
    of its edge. The outline closes round the gaps when the frame is sealed."""
    rows = [list(r) for r in HOOD]
    for y, x0, x1 in ((0, 16, 23), (1, 16, 23), (2, 18, 23), (3, 21, 23), (5, 21, 23), (6, 20, 23), (7, 21, 23)):
        for x in range(x0, x1 + 1):
            rows[y][x] = ' '
    return [''.join(r) for r in rows]


# knocked out: the hood folds, and the head falls sideways on to the coils, jaw open
FALLEN_HEAD = [
    "   oooooo     ",
    "  olllllmoo   ",
    " ollmmooommo  ",
    "olmmmmmmmmmdo ",
    "ohohohommmmddo",
    "oooooooommmddo",
    " oaoo  ooooooo",
    "oa o          ",
]


def knocked_out():
    g = coils(neck=False)
    # the neck slumps over the top loop to the left, and the head lies on its side
    patch(g, 9, 17, [" oooooooo", "olllmmmmdo", "ommmmmmdddo"])
    patch(g, 1, 14, FALLEN_HEAD)
    return g


# a fang snapped off
HEAD_CHIPPED = {8: "  ohoooo  "}


def scuffs(g, dy=0):
    """Claw scratches raked pale across the coils."""
    for x0, y0, n in ((7, 25, 2), (9, 25, 2), (8, 28, 3), (10, 28, 3), (19, 28, 3), (21, 28, 3), (21, 25, 2), (23, 25, 2)):
        for i in range(n):
            x, y = x0 + i, y0 + dy + i
            if g[y][x] in 'dm':
                g[y][x] = 'l'


def rough():
    """Roughed up: the head dips, the hood hangs torn, a fang is gone and the coils are scuffed."""
    g = build(head(HEAD_CHIPPED), head_dy=1, hood_dy=1, hood=torn_hood())
    scuffs(g)
    return g


def frames():
    # barely standing: the head hangs lower still, tongue out, and the damage stays
    barely = build(head({**HEAD_TIRED, **HEAD_CHIPPED}), head_dy=4, hood_dy=2, hood=torn_hood())
    scuffs(barely)
    ko = knocked_out()
    d2, d3, d4 = breakup(ko, seed=KEY)
    return {
        "idle1": build(),
        "idle2": build(head(TONGUE_OUT)),
        "hurt": build(head(HEAD_HURT), head_dx=1),
        "rough": rough(),
        "barely": barely,
        "defeat1": ko, "defeat2": d2, "defeat3": d3, "defeat4": d4,
    }
