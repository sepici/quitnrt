"""The slime: a lopsided heap of jelly with a coin it swallowed, bubbles and a sour grin."""
from spritekit import *

KEY = 'slime'
SLOTS = {"o": "outline", "d": "dark", "m": "mid", "l": "light", "b": "bubbles",
         "a": "accent (a swallowed coin)", "e": "eye", "h": "highlight (shine)"}
VARIANTS = {
    "forest": {"o": "#0b2017", "d": "#124f39", "m": "#1f7a50", "l": "#4fae70",
               "b": "#f3e4b6", "a": "#f2a922", "e": "#ffe27a", "h": "#fbf8e8"},
    # molten: bubbles that glow, and hot gold eyes
    "ember": {"o": "#2a1519", "d": "#6b2b2d", "m": "#a3453a", "l": "#cf7a4f",
              "b": "#f0b44c", "a": "#f2a922", "e": "#ffe27a", "h": "#fff7e6"},
    "moss": {"o": "#16201a", "d": "#2f4a33", "m": "#4d7347", "l": "#87a35e",
             "b": "#dcc99a", "a": "#f2a922", "e": "#e8a33d", "h": "#f8f6e6"},
    "frost": {"o": "#161b2b", "d": "#2e4466", "m": "#4a6f98", "l": "#86acc7",
              "b": "#cfdcd9", "a": "#f2a922", "e": "#f0b44c", "h": "#fbfbf6"},
}


def body_shape(squash=0):
    """The heap: a wide dome with a lump on the upper left and a droopy peak, on a puddle."""
    top = 22 + squash
    body = (ellipse(16, top, 13 + squash, 10 - squash) | ellipse(13, 14 + squash, 7, 6 - squash)) & rect(1, 0, 30, 30)
    body |= rect(3, 28, 28, 30)
    # a drip running down the right side
    body |= capsule([(27, 15 + squash), (28, 20 + squash)], 0.8)
    return body


def blockout(squash=0):
    g = compose([(body_shape(squash), 'shade', {'rim': 0.84})])
    # the underside, where the jelly meets the rock
    for x in range(SIZE):
        if g[30][x] in 'lmd' and g[31][x] == 'o':
            g[30][x] = 'd'
    # light coming through the jelly: a rim along the lower right, inside the shadow
    for y in range(17 + squash, 28):
        edge = max((x for x in range(SIZE) if g[y][x] in 'lmd'), default=None)
        if edge is not None and g[y][edge - 1] == 'd':
            g[y][edge - 1] = 'l'
    # a drop that has run off on to the rock
    patch(g, 0, 28, [".o", "olo", "olo"])
    return g


EYES = {
    # big eyes, pupils low and to the left, under cross brows
    'open': ["ooooo", "oeeeo", "ooeeo", "ooeeo", " ooo "],
    'squeeze': ["o    ", "ooo  ", "  ooo", "ooo  ", "o    "],
    'heavy': ["ooooo", "ooooo", "ooooo", "oeeeo", " ooo "],
    'shut': ["     ", "     ", "ooooo", " ooo ", "     "],
}
BROWS = {'open': (" oo  ", "   oo"), 'heavy': ("     ", "     ")}
MOUTHS = {
    'grin': {0: [(0, 'o'), (10, 'o')], 1: [(1, 'ooooooooo')], 2: [(3, 'ddddd')]},
    'yelp': {0: [(3, 'ooooo')], 1: [(2, 'ooooooo')], 2: [(2, 'ooooooo')], 3: [(3, 'ooooo')]},
    'wobble': {1: [(1, 'oo'), (5, 'oo'), (9, 'o')], 2: [(3, 'oo'), (7, 'oo')]},
    'flat': {1: [(2, 'ooooooo')]},
}


def face(g, dy=1, eyes='open', mouth='grin', coin=True):
    # shine along the upper left, as on wet jelly
    for x, y in ((12, 7), (13, 7), (14, 7), (10, 8), (11, 8), (9, 9), (8, 10)):
        if g[y + dy][x] in 'lmd':
            g[y + dy][x] = 'h'
    left_brow, right_brow = BROWS.get(eyes, BROWS['open'])
    shape = EYES[eyes]
    patch(g, 9, 13 + dy, [left_brow] + shape)
    patch(g, 18, 13 + dy, [right_brow] + ([r[::-1] for r in shape] if eyes == 'squeeze' else shape))
    segs(g, {20 + dy + k: [(11 + x, s) for x, s in v] for k, v in MOUTHS[mouth].items()})
    # bubbles, and the coin it swallowed
    for x, y in ((24, 13), (6, 20), (25, 18), (9, 24)):
        if g[y + dy][x] in 'lmd':
            g[y + dy][x] = 'b'
    if coin:
        patch(g, 20, 22 + dy, [" oo ", "ohao", "oaao", " oo "])


def slime(squash=0, **kw):
    g = blockout(squash)
    face(g, dy=1 + squash, **kw)
    return g


def drips(g, dy=0):
    """Jelly running down the sides."""
    for x, y0, n in ((5, 18, 4), (26, 16, 5), (21, 24, 2)):
        for y in range(y0 + dy, y0 + dy + n):
            if g[y][x] in 'lmdb':
                g[y][x] = 'd'


def rough():
    g = slime()
    # a bite out of the top right
    patch(g, 21, 11, ["o___", "ldoo_", "mmdo"])
    drips(g)
    return g


def frames():
    barely = slime(squash=2, eyes='heavy', mouth='wobble')
    drips(barely, 3)
    ko = slime(squash=4, eyes='shut', mouth='flat')
    d2, d3, d4 = breakup(ko, seed=KEY)
    return {
        "idle1": slime(),
        "idle2": slime(squash=1),
        "hurt": slime(squash=1, eyes='squeeze', mouth='yelp'),
        "rough": rough(),
        "barely": barely,
        "defeat1": ko, "defeat2": d2, "defeat3": d3, "defeat4": d4,
    }
