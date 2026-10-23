"""The wraith: a hooded shape in a tattered robe, a bony hand out and a lantern in the other."""
from spritekit import *

KEY = 'wraith'
SLOTS = {"o": "outline", "d": "dark", "m": "mid", "l": "light", "b": "bone (hands)",
         "a": "accent (lantern flame)", "e": "eye", "h": "highlight"}
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


BASE = [
    "..............ooo...............",
    ".............olldo..............",
    "............olmmddo.............",
    "...........olmmmmddo............",
    "..........olmmmmmmddo...........",
    ".........olmoooooomddo..........",
    "........olmooooooooddo..........",
    "........olmooooooooddddo........",
    "........olmoeeooeeoddddo........",
    ".......olmmoooooooodddddo.......",
    ".......olmmoooooooodddddo.......",
    ".......olmmmoooooommddddo.......",
    "......olmmmmmmmmmmmmmddddo......",
    "....olmmommmmmmmmmmmmodddo......",
    "...olmmdolmmmmmmmmmdddoddddo....",
    "..olmmddolmmmmmmmmmdddoddddo....",
    ".oooooddolmmmmmmmmmdddoddddoo...",
    "obbbbbo.olmmmmmmmmmmdddooobbbo..",
    "obobobo.olmmmdmmmmmmddddo.obo...",
    "obobobo.olmmmdmmmmmmddddo..o....",
    ".o.o.o..olmmmdmmmmmmmddddoooooo.",
    ".......olmmmmdmmmmmmmddddoahaao.",
    ".......olmmmmdmmmmmdmddddoahhao.",
    "......olmmmmmdmmmmmdmmddddoaaao.",
    "......olmmmmdmmmmmmdmmddddooooo.",
    ".....olmmmmmdmmmmmmdmmmdddo.....",
    ".....olmmmmdmmmmmmmdmmmddddo....",
    "....olmmdmmdmmmdmmmmdmmmdddo....",
    "....oldooldmmdooldmmmoodmmdo....",
    "....oo..olddoo.olmdo..oolddo....",
    ".........olo....oldo....oldo....",
    "..........o......oo......oo.....",
]


def idle1():
    return grid(BASE)


def idle2():
    g = grid(BASE)
    sink(g, 27)               # the robe sinks on the air and the hem bunches
    # the lantern flickers
    patch(g, 25, 22, ["oaahao", "oahaao"])
    return g


def hurt():
    g = grid(BASE)
    move(g, 7, 0, 24, 12, 1, 0)       # the hood snaps back
    # eyes flare into slits
    segs(g, {7: [(13, 'e'), (18, 'e')], 8: [(13, 'oeoo'), (17, 'eo')]})
    g[8][14] = 'o'
    # the lantern swings out
    move(g, 25, 20, 30, 24, 1, -1)
    return g


def tatters(g):
    """The hood ripped, a strip of the hem torn away, the lantern glass cracked."""
    patch(g, 17, 2, ["o", "do", "_do"])
    clear(g, 9, 29, 11, 31)
    put(g, 9, 28, 'oo')


def rough():
    g = grid(BASE)
    tatters(g)
    patch(g, 25, 21, ["oaoaao", "oahoao"])
    g[8][17] = 'o'                    # one eye guttering
    return g


def slumped(drop):
    g = grid(BASE)
    tatters(g)
    # the lantern is let down to the rock, its chain trailing
    clear(g, 25, 20, 31, 24)
    for _ in range(drop):
        sink(g, 27)
    patch(g, 26, 25, [" ooo ", "oaaao", "oahao", "oaaao", "ooooo"])
    put(g, 27, 20 + drop, 'o')
    put(g, 27, 21 + drop, 'o')
    return g


def barely():
    g = slumped(2)
    g[10][13] = 'o'                   # the glow fading
    g[10][17] = 'o'
    return g


def defeat1():
    g = slumped(4)
    shut_eyes(g)
    patch(g, 26, 25, [" ooo ", "odddo", "odddo", "odddo", "ooooo"])    # the lantern goes out
    return g


def frames():
    ko = defeat1()
    d2, d3, d4 = breakup(ko, seed=KEY)
    return {"idle1": idle1(), "idle2": idle2(), "hurt": hurt(), "rough": rough(), "barely": barely(),
            "defeat1": ko, "defeat2": d2, "defeat3": d3, "defeat4": d4}
