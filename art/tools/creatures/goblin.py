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


def hurt():
    g = grid(BASE)
    # eyes screwed shut, mouth open in a yelp
    segs(g, {9: [(12, 'mo'), (17, 'om')], 10: [(13, 'oo'), (18, 'oo')],
             13: [(9, 'ooooooooo')], 14: [(10, 'loooooomddo')]})
    return g


def nick_ear(g):
    patch(g, 2, 5, ["_o", "o_o", " o"])


def black_eye(g, dy=0):
    for x, y in ((13, 9), (13, 10), (13, 11), (14, 11), (15, 11)):
        if g[y + dy][x] in 'lm':
            g[y + dy][x] = 'd'


def damage(g, dy=0):
    nick_ear(g)
    black_eye(g, dy)
    g[12 + dy][12] = 'm'      # a tusk snapped off


def rough():
    g = grid(BASE)
    damage(g)
    # a split down the club
    for x, y in ((27, 1), (27, 2), (26, 3), (26, 4)):
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
    # lids down, mouth slack
    segs(g, {11: [(14, 'o'), (19, 'o')], 12: [(14, 'e'), (19, 'e')]})
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
