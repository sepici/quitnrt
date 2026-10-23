"""Dev tool: builds the seven creatures after the dragon and writes their sprite files.

Each creature's source is art/tools/creatures/<key>.py: its slots, its four colour variants
and a frames() that returns the nine frames as grids. The dragon has its own build.py.

usage: python -I build_creatures.py [creature ...]
Then python -I preview.py renders the sheets for review.
"""
import importlib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, 'creatures'))
import spritekit as sk  # noqa: E402

CREATURES = ("imp", "bear", "kraken", "goblin", "slime", "wraith", "serpent")


def main(argv):
    for key in argv[1:] or CREATURES:
        mod = importlib.import_module(key)
        assert mod.KEY == key
        path = sk.write(key, mod.SLOTS, mod.VARIANTS, mod.frames())
        print('wrote', os.path.relpath(path, sk.ROOT))


if __name__ == '__main__':
    main(sys.argv)
