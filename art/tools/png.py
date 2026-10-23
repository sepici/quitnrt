"""A tiny RGB canvas that writes PNGs with the standard library only (no Pillow).

The art tools draw preview sheets with it: filled rectangles, sprites scaled with nearest
neighbour, and labels in a 5 x 7 pixel font.
"""
import struct
import zlib


def hex_rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# 5 x 7 capitals, digits and a little punctuation. Lower case is drawn as upper case.
_FONT = {
    'A': ".###.#...##...#######...##...##...#", 'B': "####.#...##...#####.#...##...#####.",
    'C': ".###.#...##....#....#....#...#.###.", 'D': "####.#...##...##...##...##...#####.",
    'E': "######....#....####.#....#....#####", 'F': "######....#....####.#....#....#....",
    'G': ".###.#...##....#.####...##...#.####", 'H': "#...##...##...#######...##...##...#",
    'I': ".###...#....#....#....#....#...###.", 'J': "..###...#....#....#....##..#..##...",
    'K': "#...##..#.#.#..##...#.#..#..#.#...#", 'L': "#....#....#....#....#....#....#####",
    'M': "#...###.###.#.##.#.##...##...##...#", 'N': "#...###..##.#.##..###...##...##...#",
    'O': ".###.#...##...##...##...##...#.###.", 'P': "####.#...##...#####.#....#....#....",
    'Q': ".###.#...##...##...##.#.##..#..##.#", 'R': "####.#...##...#####.#.#..#..#.#...#",
    'S': ".#####....#.....###.....#....#####.", 'T': "#####..#....#....#....#....#....#..",
    'U': "#...##...##...##...##...##...#.###.", 'V': "#...##...##...##...##...#.#.#...#..",
    'W': "#...##...##...##.#.##.#.##.#.#.#.#.", 'X': "#...##...#.#.#...#...#.#.#...##...#",
    'Y': "#...##...#.#.#...#....#....#....#..", 'Z': "#####....#...#...#...#...#....#####",
    '0': ".###.#...##..###.#.###..##...#.###.", '1': "..#...##....#....#....#....#...###.",
    '2': ".###.#...#....#...#...#...#...#####", '3': "#####...#...#.....#.....##...#.###.",
    '%': "##...##..#...#...#...#...#..##...##",
    '4': "...#...##..#.#.#..#.#####...#....#.", '5': "######....####.....#....##...#.###.",
    '6': "..##..#...#....####.#...##...#.###.", '7': "#####....#...#...#...#....#....#...",
    '8': ".###.#...##...#.###.#...##...#.###.", '9': ".###.#...##...#.####....#...#..##..",
    ' ': "." * 35, '-': "...............#####...............",
    '.': "..............................##...", ',': ".........................##....#...",
    '(': "...#...#...#....#....#.....#.....#.", ')': ".#.....#.....#....#....#...#...#...",
    ':': ".....##...##.........##...##.......", '/': ".....#...#...#...#...#.............",
    '+': ".......#....#..#####..#....#.......", '_': "..............................#####",
    "'": "..#....#...#.......................",
}
GLYPH_W, GLYPH_H = 5, 7


class Canvas:
    def __init__(self, w, h, bg=(0, 0, 0)):
        self.w, self.h = w, h
        self.buf = bytearray(bytes(bg) * (w * h))

    def rect(self, x, y, w, h, colour):
        x0, y0 = max(0, x), max(0, y)
        x1, y1 = min(self.w, x + w), min(self.h, y + h)
        if x1 <= x0 or y1 <= y0:
            return
        line = bytes(colour) * (x1 - x0)
        for yy in range(y0, y1):
            i = (yy * self.w + x0) * 3
            self.buf[i:i + len(line)] = line

    def outline(self, x, y, w, h, colour):
        self.rect(x, y, w, 1, colour)
        self.rect(x, y + h - 1, w, 1, colour)
        self.rect(x, y, 1, h, colour)
        self.rect(x + w - 1, y, 1, h, colour)

    def pixels(self, rows, colour_of, x, y, scale):
        """Draw a grid of characters; colour_of(ch) gives an RGB tuple or None for transparent."""
        for yy, row in enumerate(rows):
            for xx, ch in enumerate(row):
                c = colour_of(ch)
                if c is not None:
                    self.rect(x + xx * scale, y + yy * scale, scale, scale, c)

    def text(self, x, y, s, colour, scale=2):
        for ch in s:
            glyph = _FONT.get(ch.upper(), _FONT[' '])
            for i, bit in enumerate(glyph):
                if bit == '#':
                    self.rect(x + (i % GLYPH_W) * scale, y + (i // GLYPH_W) * scale, scale, scale, colour)
            x += (GLYPH_W + 1) * scale

    @staticmethod
    def text_width(s, scale=2):
        return len(s) * (GLYPH_W + 1) * scale

    def save(self, path):
        raw = bytearray()
        stride = self.w * 3
        for y in range(self.h):
            raw.append(0)
            raw += self.buf[y * stride:(y + 1) * stride]

        def chunk(tag, data):
            body = tag + data
            return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xFFFFFFFF)

        png = b'\x89PNG\r\n\x1a\n'
        png += chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 2, 0, 0, 0))
        png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
        png += chunk(b'IEND', b'')
        with open(path, 'wb') as f:
            f.write(png)
