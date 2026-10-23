"""The bear: reared up on its hind legs, one paw raised, roaring."""
from spritekit import *

KEY = 'bear'
SLOTS = {"o": "outline", "d": "dark", "m": "mid", "l": "light", "b": "muzzle and belly",
         "a": "accent (claws)", "e": "eye", "h": "highlight (fangs)"}
VARIANTS = {
    "forest": {"o": "#1f1510", "d": "#4b301f", "m": "#7a4c2e", "l": "#a8744a",
               "b": "#e6c48c", "a": "#f3e4b6", "e": "#ffe27a", "h": "#fbf8e8"},
    "ember": {"o": "#2a1519", "d": "#6b2b2d", "m": "#a3453a", "l": "#cf7a4f",
              "b": "#e6c48c", "a": "#efe3c6", "e": "#f2cd55", "h": "#fff7e6"},
    "moss": {"o": "#16201a", "d": "#2f4a33", "m": "#4d7347", "l": "#87a35e",
             "b": "#dcc99a", "a": "#ece4c8", "e": "#e8a33d", "h": "#f8f6e6"},
    "frost": {"o": "#161b2b", "d": "#86acc7", "m": "#cfdcd9", "l": "#fbfbf6",
              "b": "#f1ede2", "a": "#2e4466", "e": "#f0b44c", "h": "#fff7e6"},
}


BASE = [
    ".........................o.o.o..",
    ".......ooo.....ooo......oaoaoao.",
    "......oldlo...oldlo.....oaaaaao.",
    "......oddmooooommdo.....olllldo.",
    ".......olllllllmmdo....olmmmmdo.",
    "......ollmmmmmmmmddo...olmmmmdo.",
    "......olmmommmommddo...olmmmddo.",
    "..oooooolmemmmemmdddo..olmmmdo..",
    ".oooobbbbmmmmmmmmddddoolmmmmdo..",
    ".ohoobbbbbmmmmmmmddddoolmmmmdo..",
    ".obbbbbbbbmmmmmmmdddoolmmmmdo...",
    ".ohoooohbbmmmmmmddddolllmmmdo...",
    "..oooooobbmmmmmddddoolmmmmddo...",
    "..ohbbhbbommmmmdddodddmmmdddo...",
    "...oooooooommmdddodmmmmdddddo...",
    "...ooo...ommmmddobbmmmbbdddddo..",
    "..oaoao..olmmmdommbbmbbddddddo..",
    ".oaoaoaoolmmmddommmbbbmddddddo..",
    ".oaaaaaaolmmdddommmmmmmddddddo..",
    "..ollllllmmddddommmmmmmddddddo..",
    "..olmmmmmmdddooommmmmmmddddddo..",
    "...olmmddddooooommmmmmmdddddo...",
    "....oooooooo..olmmmmmmmmdddddo..",
    ".............olmmmmmmmmmdddddo..",
    ".............olmmmmmmmmmddddo...",
    ".............olmmmmmmmmdddddo...",
    "............ollmmmmmmmdddddddo..",
    "...........olmmmddoooooolmmmddo.",
    "...........olmmmdddo...olmmmddo.",
    "..........olmmmmdddo..olmmmmddo.",
    "..........oaaammmddo..oaaammddo.",
    "..........ooooooooo...ooooooooo.",
]


def idle1():
    return grid(BASE)


def idle2():
    g = grid(BASE)
    sink(g, 23)               # a breath: everything above the hips settles 1 px
    return g


def hurt():
    g = grid(BASE)
    move(g, 0, 0, 19, 14, 1, 0)       # the head snaps back
    # eyes screwed shut, brows up
    segs(g, {6: [(10, 'o'), (16, 'o')], 7: [(11, 'om'), (14, 'mo')], 8: [(10, 'o'), (16, 'o')]})
    # the raised paw splays its claws
    patch(g, 23, 0, [
        "o.o.o.o.",
        "aoaoaoao",
        "oaaaaaao",
    ])
    return g


def torn_ear(g):
    patch(g, 6, 1, ["_o_o", "o_lo"])


def scar(g):
    """Three claw marks across the brow, and the far eye swollen shut."""
    for x, y in ((13, 4), (14, 5), (15, 6), (16, 7)):
        g[y][x] = 'o'
    g[7][14] = 'd'


def rough():
    g = grid(BASE)
    torn_ear(g)
    scar(g)
    # the raised paw sags and has lost a claw
    move(g, 22, 0, 31, 13, 0, 2)
    patch(g, 22, 1, [" ..o.o.o.", " .oaoaoao", ".ooooaaao"])
    patch(g, 24, 2, ["oo"])
    return g


# the far arm, no longer raised: it hangs at the bear's side, claws down
HANGING_ARM = [
    "oooooo    ",
    "          ",
    "          ",
    "          ",
    "          ",
    "   olmddo ",
    "   olmddo ",
    "   olmmddo",
    "   olmmddo",
    "   olmmddo",
    "  olmmmddo",
    "  olmmmddo",
    "  ommmddo ",
    "  oaoaoao ",
    "   o.o.o  ",
]


def slumped(drop):
    """Torn ear and scarred brow, the raised arm hanging, everything above the hips sunk `drop` px."""
    g = grid(BASE)
    torn_ear(g)
    scar(g)
    for y in range(0, 11):
        for x in range(22, 32):
            g[y][x] = '.'
    patch(g, 21, 10, HANGING_ARM)
    for _ in range(drop):
        sink(g, 25)
    return g


def barely():
    g = slumped(2)
    # mouth hanging open, a breath of steam at the snout
    patch(g, 0, 7, ["  o", " oho", "  o"])
    return g


def defeat1():
    g = slumped(4)
    for y in range(SIZE):
        for x in range(SIZE):
            if g[y][x] == 'e':
                g[y][x] = 'o'
    return g


def frames():
    ko = defeat1()
    d2, d3, d4 = breakup(ko, seed=KEY)
    return {"idle1": idle1(), "idle2": idle2(), "hurt": hurt(), "rough": rough(), "barely": barely(),
            "defeat1": ko, "defeat2": d2, "defeat3": d3, "defeat4": d4}
