"""The imp: a small horned devil with a wide grin, a pitchfork and a spade-tipped tail."""
from spritekit import *

KEY = 'imp'
SLOTS = {"o": "outline", "d": "dark", "m": "mid", "l": "light", "b": "belly",
         "a": "accent (horns, fork, claws)", "e": "eye", "h": "highlight (teeth)"}
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

BASE = [
    ".o.o.o...o..............o.......",
    "oaoaoao.oao............oao......",
    "oaoaoao.ohao..........oaao......",
    "oaaaaao..oaao........oaao.......",
    ".ooaoo....oaaooooooooaao........",
    "..oao.....ollllllllllmmo........",
    "..oao.....ollmmmmmmmmddo........",
    "..oao....ollmmmmmmmmmdddo.......",
    "..oao...olmoommmmmmmmooddo......",
    "..oao...olmmmoommmmoommddo......",
    "..oao...olmmeemmmmmmeemddo......",
    "..oao...olmmoemmmmmmoemddo......",
    "..oao...olmmmmmmddmmmmmddo......",
    "..oao...olmommmmmmmmmmoddo..o...",
    "..oao....olmooooooooooddo..olo..",
    "..oao.....omohhhhhhhhodo..olmdo.",
    "..oao......omohoooohodo..olmmddo",
    "..oao.......oooooooooo...olmmddo",
    "..oao.....ollmmmmmmmmddo..oomoo.",
    "..oao....olmolmbbbbbmmdodo.omo..",
    "..oao...olmdolbbbbbbbmdomdoomo..",
    ".ooooooolmdolbbbbbbbbbdomdoomo..",
    "ollmmmmmmdo.olbbbbbbbbdomdoodo..",
    "ommmddoooo..olbbbbbbbbdodooodo..",
    ".oooo........olbbbbbbbddooaaodo.",
    "..oao.........olbbbbbmddo.oooddo",
    "..oao.........oollllmmdooooddoo.",
    "..oao.........oommmooodddddoo...",
    "..oao........olmdoo.oolmdoo.....",
    "..oao........olmdo...olmdo......",
    "..oao.......oaamdo..oaamdo......",
    "..ooo.......oooooo..oooooo......",
]


def idle1():
    return grid(BASE)


def idle2():
    g = grid(BASE)
    sink(g, 21, 7, 31)        # head, chest and tail settle 1 px; the fork and feet stay put
    g[21][7] = 'o'            # the elbow, where the arm meets the fist
    return g


# eyes squeezed shut, brows up in the middle, mouth open in a yelp
HURT_FACE = [
    "olmmoommmmmmoomddo",
    "olmommmmmmmmmmoddo",
    "olmmommmmmmmmomddo",
    "olmmmommmmmmommddo",
    "olmmommmmmmmmomddo",
    "olmmmoooooooomdddo",
]


def hurt():
    g = grid(BASE)
    patch(g, 8, 8, HURT_FACE)
    segs(g, {14: [(10, 'lmohhhhhhhhodd')], 15: [(11, 'moooooooooo')]})
    # the tail flicks up
    move(g, 25, 13, 31, 18, 0, -1)
    put(g, 27, 18, 'omo')
    return g


def chip_horn(g):
    """The left horn snapped off halfway: a flat stub."""
    patch(g, 7, 0, [
        "__________",
        "____",
        "_____",
        "  oo",
    ])
    g[3][9] = 'o'


def black_eye(g, dy=0):
    """A dark ring round the left eye, swollen half shut."""
    segs(g, {9 + dy: [(11, 'dd')], 10 + dy: [(11, 'doo')], 11 + dy: [(11, 'd')], 12 + dy: [(11, 'ddd')]})


def broken_fork(g):
    patch(g, 0, 0, [
        ".o...o.",
        "oaooaao",
        "oaoaoao",
    ])


def claw_marks(g, dy=0):
    """Three scratches raked across the belly."""
    for x0, y0 in ((18, 19), (20, 20), (22, 21)):
        for i in range(4):
            x, y = x0 - i, y0 + dy + i
            if g[y][x] == 'b':
                g[y][x] = 'm'


def rough():
    g = grid(BASE)
    chip_horn(g)
    black_eye(g)
    broken_fork(g)
    claw_marks(g)
    g[15][15] = 'o'           # a tooth knocked out
    # the spade droops
    move(g, 25, 13, 31, 18, 0, 1)
    return g


def slumped(drop):
    """rough(), with the head and chest sunk `drop` px onto the hips and the spade hanging low."""
    g = rough()
    for _ in range(drop):
        sink(g, 24, 7, 31)
    g[24][7] = '.'
    return g


def barely():
    g = slumped(2)
    # lids half down
    segs(g, {12: [(12, 'oo'), (20, 'oo')], 13: [(12, 'ee'), (20, 'ee')]})
    # a wobbly grimace: teeth clenched on one side
    segs(g, {16: [(11, 'lmooooooooodd')], 17: [(11, 'momhhhhhooo')], 18: [(12, 'mooooood')]})
    # smoke from the snapped horn
    patch(g, 7, 0, [
        "  o",
        " oho",
        " oho",
        "oho",
    ])
    return g


def defeat1():
    g = slumped(3)
    # knocked out: eyes crossed out
    for x0 in (11, 19):
        patch(g, x0, 12, ["omo", "mom", "omo"])
    segs(g, {17: [(11, 'mmooooooooddd')], 18: [(12, 'mohhhhhhoo')], 19: [(13, 'oooooooo')]})
    # the fork drops: it lies on the ground in front
    for y in range(32):
        for x in range(0, 7):
            g[y][x] = '.'
    for x in (7, 8):
        for y in range(19, 25):
            if g[y][x] == 'o' and g[y][x + 1] == '.':
                g[y][x] = '.'
    patch(g, 0, 25, [
        ".ooo.",
        "oaaao",
        ".ooaooooooo",
        "oaaaaaaaaao",
        ".ooaooooooo",
        "oaaao",
        ".ooo.",
    ])
    return g


def frames():
    ko = defeat1()
    d2, d3, d4 = breakup(ko, seed=KEY)
    return {"idle1": idle1(), "idle2": idle2(), "hurt": hurt(), "rough": rough(), "barely": barely(),
            "defeat1": ko, "defeat2": d2, "defeat3": d3, "defeat4": d4}
