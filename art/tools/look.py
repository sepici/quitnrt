"""Dev viewer: frames of a creature file at 10x with a coordinate grid, for drawing.

usage: python -I look.py CREATURE_JSON OUT.png [variant] [frame ...]

Each frame shows a gridded 10x view, then 3x previews on the light and dark stage
colours and the lurking silhouette, so details can be checked at the size they're seen.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Canvas, hex_rgb  # noqa: E402

LIGHT = hex_rgb('#FBEFD0')
DARK = hex_rgb('#16120C')
LURK = hex_rgb('#3F3829')
MARIGOLD = hex_rgb('#F5B12B')
CHECK = ((206, 206, 206), (186, 186, 186))
GRID = (120, 120, 120)
GRID4 = (40, 120, 230)
S = 10


def render(data, out, variant=None, names=None):
    variant = variant or data['defaultVariant']
    pal = {k: hex_rgb(v) for k, v in data['variants'][variant].items()}
    eye = next((k for k, v in data['slots'].items() if v == 'eye'), None)
    names = names or list(data['frames'])
    size = data['size']
    cell_w = size * S + 30 + size * 3 + 20
    cell_h = size * S + 40
    cols = min(3, len(names))
    rows = (len(names) + cols - 1) // cols
    c = Canvas(cols * cell_w + 20, rows * cell_h + 20, (60, 60, 66))
    for i, n in enumerate(names):
        grid = data['frames'][n]
        ox = 30 + (i % cols) * cell_w
        oy = 30 + (i // cols) * cell_h
        c.text(ox, oy - 18, n, (255, 255, 255), 2)
        for y in range(size):
            for x in range(size):
                ch = grid[y][x]
                col = CHECK[(x + y) % 2] if ch == '.' else pal.get(ch, (255, 0, 255))
                c.rect(ox + x * S, oy + y * S, S, S, col)
        for k in range(size + 1):
            col = GRID4 if k % 4 == 0 else GRID
            c.rect(ox + k * S, oy, 1, size * S, col)
            c.rect(ox, oy + k * S, size * S, 1, col)
        for k in range(0, size, 4):
            c.text(ox + k * S + 2, oy + size * S + 3, str(k), (255, 230, 80), 1)
            c.text(ox - 14, oy + k * S + 2, str(k), (255, 230, 80), 1)
        px = ox + size * S + 12
        for j, bg in enumerate((LIGHT, DARK)):
            y0 = oy + j * (size * 3 + 6)
            c.rect(px, y0, size * 3, size * 3, bg)
            c.pixels(grid, lambda ch: None if ch == '.' else pal.get(ch, (255, 0, 255)), px, y0, 3)
        y0 = oy + 2 * (size * 3 + 6)
        c.rect(px, y0, size * 3, size * 3, LIGHT)
        c.pixels(grid, lambda ch: None if ch == '.' else (MARIGOLD if ch == eye else LURK), px, y0, 3)
    c.save(out)


if __name__ == '__main__':
    with open(sys.argv[1]) as f:
        d = json.load(f)
    render(d, sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None, sys.argv[4:] or None)
