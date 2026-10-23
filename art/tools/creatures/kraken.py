"""The kraken: a domed head with slit eyes and tentacles curling out over the rock."""
from spritekit import *

KEY = 'kraken'
SLOTS = {"o": "outline", "d": "dark", "m": "mid", "l": "light", "b": "suckers",
         "a": "accent (spots)", "e": "eye", "h": "highlight"}
VARIANTS = {
    "forest": {"o": "#0b2017", "d": "#124f39", "m": "#1f7a50", "l": "#4fae70",
               "b": "#f3e4b6", "a": "#f2a922", "e": "#ffe27a", "h": "#fbf8e8"},
    "ember": {"o": "#2a1519", "d": "#6b2b2d", "m": "#a3453a", "l": "#cf7a4f",
              "b": "#e6c48c", "a": "#efe3c6", "e": "#f2cd55", "h": "#fff7e6"},
    "moss": {"o": "#16201a", "d": "#2f4a33", "m": "#4d7347", "l": "#87a35e",
             "b": "#dcc99a", "a": "#ece4c8", "e": "#e8a33d", "h": "#f8f6e6"},
    "frost": {"o": "#161b2b", "d": "#2e4466", "m": "#4a6f98", "l": "#86acc7",
              "b": "#cfdcd9", "a": "#f1ede2", "e": "#f0b44c", "h": "#fbfbf6"},
}

# the two back tentacles: raised and curling, flung up, or hanging limp
ARMS = {
    'raised': ([(11, 17), (6, 16), (3, 13), (2, 9), (3, 6), (5, 6)],
               [(21, 17), (26, 15), (29, 12), (29, 8), (27, 6)]),
    'flung': ([(11, 16), (6, 14), (3, 10), (3, 6), (4, 3), (6, 3)],
              [(21, 16), (26, 13), (29, 9), (29, 5), (27, 3)]),
    'curled': ([(11, 17), (6, 16), (3, 13), (2, 10), (3, 7), (5, 7)],
               [(21, 17), (26, 15), (29, 12), (29, 9), (27, 7)]),
    'limp': ([(11, 18), (6, 19), (3, 22), (2, 25), (3, 28)],
             [(21, 18), (26, 19), (29, 22), (30, 25), (29, 28)]),
}

EYES = {
    # big eyes with bar pupils, looking left, under heavy lids
    'open': ([" oo  ", "oeeoo", "ooeeo", "oeeeo", " ooo "],
             ["  oo ", "ooeeo", "ooeeo", "oeeeo", " ooo "]),
    # screwed shut
    'squeeze': ([" o   ", "oooo ", "  ooo", "oooo ", " o   "],
                ["   o ", " oooo", "ooo  ", " oooo", "   o "]),
    # lids most of the way down
    'heavy': ([" ooo ", "ooooo", "ooooo", "oeeeo", " ooo "],
              [" ooo ", "ooooo", "ooooo", "oeeeo", " ooo "]),
    'shut': ([" ooo ", "omMmo", "ooooo", "omMmo", " ooo "],
             [" ooo ", "omMmo", "ooooo", "omMmo", " ooo "]),
}


# the front tentacles stop a row short of the bottom, so their outline sits on the rock
GROUND = rect(1, 0, 30, 30)


def tentacle(points, r0, r1):
    """A tapering tentacle along points, radius r0 at the root to r1 at the tip."""
    shape = set()
    n = len(points) - 1
    for i in range(n):
        r = r0 + (r1 - r0) * (i / max(1, n - 1))
        shape |= capsule([points[i], points[i + 1]], r)
    return shape


def build(arms='raised', eyes='open', mantle_dy=0, squash=0, lost_tip=False):
    left, right = ARMS[arms]
    if lost_tip:
        left = left[:-2]
    parts = [
        (tentacle(left, 1.6, 0.5 if not lost_tip else 1.0), 'shade'),
        (tentacle(right, 1.6, 0.5), 'shade'),
        # the front tentacles, spread on the ground
        (tentacle([(12, 21), (7, 25), (3, 27), (2, 29)], 1.7, 0.6) & GROUND, 'shade'),
        (tentacle([(20, 21), (25, 25), (29, 26), (30, 24)], 1.7, 0.6) & GROUND, 'shade'),
        (tentacle([(14, 22), (12, 27), (9, 30)], 1.7, 0.8) & GROUND, 'shade'),
        (tentacle([(18, 22), (20, 27), (23, 30)], 1.7, 0.8) & GROUND, 'shade'),
        # the mantle
        (ellipse(15.5, 9.5 + mantle_dy + squash / 2, 7.5 + squash / 2, 8 - squash / 2), 'shade'),
    ]
    g = compose(parts)
    dy = mantle_dy + squash
    left_eye, right_eye = EYES[eyes]
    patch(g, 10, 11 + dy, [r.replace('M', 'm') for r in left_eye])
    patch(g, 17, 11 + dy, [r.replace('M', 'm') for r in right_eye])
    # a shine on the dome and a few spots
    segs(g, {3 + mantle_dy + squash: [(12, 'h')], 4 + mantle_dy + squash: [(11, 'hl')]})
    for x, y in ((15, 4), (19, 6), (13, 8), (17, 9), (20, 11)):
        if g[y + dy][x] in 'md':
            g[y + dy][x] = 'a'
    # suckers along the undersides of the tentacles on the ground
    for y in range(20, 31):
        for x in range(SIZE):
            if g[y][x] in 'md' and g[y + 1][x] == 'o' and (x + y) % 2 == 0:
                g[y][x] = 'b'
    return g


def rough_marks(g):
    # a bite out of the dome's rim, and a welt across it
    for x, y in ((20, 3), (21, 4), (22, 5)):
        g[y][x] = 'o'
    g[2][19] = '.'
    for x, y in ((11, 7), (12, 7), (13, 8), (14, 8)):
        g[y][x] = 'd'


def frames():
    rough = build(arms='raised', lost_tip=True)
    rough_marks(rough)
    barely = build(arms='limp', eyes='heavy', mantle_dy=2, lost_tip=True)
    rough_marks_low = barely
    for x, y in ((11, 9), (12, 9), (13, 10), (14, 10)):
        rough_marks_low[y][x] = 'd'
    ko = build(arms='limp', eyes='shut', mantle_dy=3, squash=1, lost_tip=True)
    d2, d3, d4 = breakup(ko, seed=KEY)
    return {
        "idle1": build(),
        "idle2": build(arms='curled', mantle_dy=1),
        "hurt": build(arms='flung', eyes='squeeze', mantle_dy=-1),
        "rough": rough,
        "barely": barely,
        "defeat1": ko, "defeat2": d2, "defeat3": d3, "defeat4": d4,
    }
