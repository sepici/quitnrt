"""The goblin: hunched, all ears and nose, with a studded club over its shoulder."""
from spritekit import *

KEY = 'goblin'
SLOTS = {"o": "outline", "d": "dark", "m": "mid", "l": "light", "b": "cloth and club",
         "a": "accent (studs, earring)", "e": "eye", "h": "highlight (teeth)"}
VARIANTS = {
    "forest": {"o": "#0b2017", "d": "#124f39", "m": "#1f7a50", "l": "#4fae70",
               "b": "#e6c48c", "a": "#f2a922", "e": "#ffe27a", "h": "#fbf8e8"},
    "ember": {"o": "#2a1519", "d": "#6b2b2d", "m": "#a3453a", "l": "#cf7a4f",
              "b": "#dcc99a", "a": "#efe3c6", "e": "#f2cd55", "h": "#fff7e6"},
    "moss": {"o": "#16201a", "d": "#2f4a33", "m": "#4d7347", "l": "#87a35e",
             "b": "#e6c48c", "a": "#ece4c8", "e": "#e8a33d", "h": "#f8f6e6"},
    "frost": {"o": "#161b2b", "d": "#2e4466", "m": "#4a6f98", "l": "#86acc7",
              "b": "#dcc99a", "a": "#f1ede2", "e": "#f0b44c", "h": "#fbfbf6"},
}


BASE = [
    ".......................oooooo...",
    "......................ohbbobbo..",
    "......................ohbbbbobo.",
    ".o....................obobbbbbo.",
    "olo.........ooooooo...ohbbbobbo.",
    "olmo.......ollllmmmo...obobbbo..",
    ".olmmo....olllmmmmmddo.obbbbo...",
    ".oldmmo..ollmmmmmmmdddo.obbbo...",
    "..olddmmmoloommmmooddddoobbooo..",
    "...oldmmmolmoommoomddddomobbomlo",
    "....omammoolloemmmoedddodobbodo.",
    ".....ooolllmmmmmmmmmdddoobbo....",
    ".....ollmmdmhmmmmhmdddo.obbo....",
    "....olmmdooooooooodddo..obbo....",
    "....omdo.olmmmmmmmmddo..obbo....",
    ".....oo...oooooooooo....obbo....",
    ".........ollmmmmmmmmddolmmdo....",
    "........olmommmmmmmmddolmmdo....",
    ".......olmdommmmmmmmddoooobo....",
    "...ollmmmmdommmmmmmmddo..oo.....",
    "..olmmddooooommmmmmmddo.........",
    "..oaoaoao..obbbbabbbbbo.........",
    "...........obbbbbbbbbbo.........",
    "...........obbbbbbbbbdo.........",
    "............obbbbbbbdo..........",
    "............obobbobobo..........",
    "...........olmdoooolmdo.........",
    "..........olmmdo..olmmdo........",
    "..........olmdo....olmdo........",
    ".........olmmdo...olmmdo........",
    "........oaammdo..oaammdo........",
    "........ooooooo..ooooooo........",
]


def idle1():
    return grid(BASE)


def idle2():
    g = grid(BASE)
    sink(g, 22)               # a breath: head, arms and club settle 1 px over the hips
    return g


# the right ear, which shows round both sides of the club
RIGHT_EAR = {(24, 9), (24, 10), (28, 8), (29, 8), (30, 8), (29, 9), (30, 9), (31, 9), (29, 10), (30, 10), (29, 11)}


def hurt():
    """The head snaps back: it jolts up 2 px on a stretched neck, eyes screwed shut and mouth
    wide open in a yelp. The club stays where it is."""
    g = grid(BASE)
    move(g, 0, 3, 22, 15, 0, -2, mask=lambda x, y: not (x == 22 and y <= 4))
    ear = [(x, y, g[y][x]) for x, y in RIGHT_EAR]
    for x, y, _ in ear:
        g[y][x] = '.' if (x, y) not in ((28, 8),) else 'o'
    for x, y, c in ear:
        if g[y - 2][x] in '.o':
            g[y - 2][x] = c
    # the neck, stretched
    patch(g, 13, 14, ["lmmmmd", "lmmmmd"])
    # eyes screwed shut
    for x, y in ((13, 7), (14, 8), (13, 9), (20, 7), (19, 8), (20, 9)):
        g[y][x] = 'o'
    # the mouth wide open, the fangs at its corners
    patch(g, 11, 10, ["ohoooho", "ooooooo", " ooooo "])
    return g


def torn_ear(g):
    """The big ear's tip torn off, leaving a ragged end."""
    clear(g, 0, 3, 3, 6)
    clear(g, 4, 5, 4, 5)


def lump(g, dy=0):
    """A lump raised on the top of the head."""
    patch(g, 14, 2 + dy, [" oo ", "ollo", "llmm"])


def black_eye(g, dy=0):
    """The left eye swollen shut in a dark ring."""
    for x, y in ((13, 9), (13, 10), (13, 11), (14, 11), (15, 11), (12, 10), (15, 10)):
        if g[y + dy][x] in 'lm':
            g[y + dy][x] = 'd'
    g[10 + dy][14] = 'o'


def damage(g, dy=0):
    torn_ear(g)
    lump(g, dy)
    black_eye(g, dy)
    g[12 + dy][12] = 'm'      # a tusk snapped off
    g[10 + dy][6] = 'm'       # the earring torn out


def rough():
    """Roughed up: the ear torn, a lump on the head, a black eye, a tusk and the earring gone,
    and the club split with a chunk knocked out of it."""
    g = grid(BASE)
    damage(g)
    move(g, 0, 3, 7, 9, 0, 1)         # the torn ear sags
    for x, y in ((27, 1), (27, 2), (26, 3), (26, 4)):
        g[y][x] = 'o'
    clear(g, 28, 0, 30, 1)
    clear(g, 29, 2, 30, 2)
    # the loincloth ripped
    for x, y in ((14, 21), (15, 22), (15, 23), (16, 24)):
        g[y][x] = 'o'
    return g


def club_down(hand_y):
    """The club let down from the hand, its studded head resting on the rock."""
    club = compose([(capsule([(24.5, hand_y), (27, 24)], 0.7) | (ellipse(27.5, 27, 2.6, 3.4) & rect(0, 0, 30, 30)), 'b')])
    for x, y in ((27, 25), (29, 27), (27, 28)):
        club[y][x] = 'o'
    for x, y in ((26, 25), (26, 26)):
        club[y][x] = 'h'
    return rows_of(club)


def slumped(drop):
    g = grid(BASE)
    damage(g)
    clear(g, 22, 0, 31, 15)           # the raised club goes
    clear(g, 22, 18, 27, 19)
    for _ in range(drop):
        sink(g, 22)
    patch_behind(g, 0, 0, [r.replace('.', ' ') for r in club_down(19 + drop)])
    # the big ear droops
    move(g, 0, 3, 7, 9, 0, 2)
    return g


def barely():
    g = slumped(2)
    # the good eye's lid down (the other is still swollen shut), mouth slack
    segs(g, {11: [(19, 'o')], 12: [(19, 'e')]})
    return g


def defeat1():
    g = slumped(4)
    shut_eyes(g)
    return g


def frames():
    ko = defeat1()
    d2, d3, d4 = breakup(ko, seed=KEY)
    return {"idle1": idle1(), "idle2": idle2(), "hurt": hurt(), "rough": rough(), "barely": barely(),
            "defeat1": ko, "defeat2": d2, "defeat3": d3, "defeat4": d4}
