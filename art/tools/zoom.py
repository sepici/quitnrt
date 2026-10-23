import sys
sys.path.insert(0, '.')
from view import load, PAL, hexrgb
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
names = sys.argv[3:]
frames = load(src)
pal = {k: hexrgb(v) for k, v in PAL['ember'].items()}
tiles = []
for n in names or list(frames):
    rows = frames[n]
    for bg in [(244, 241, 234), (20, 22, 26)]:
        im = Image.new('RGB', (32, 32), bg)
        for y in range(32):
            for x in range(32):
                c = rows[y][x]
                if c != '.': im.putpixel((x, y), pal[c])
        tiles.append(im)
S = 8
W = len(tiles) * (32 * S + 8)
sheet = Image.new('RGB', (W, 32 * S + 32 * 2 + 16), (90, 90, 90))
for i, t in enumerate(tiles):
    sheet.paste(t.resize((32 * S, 32 * S), Image.NEAREST), (i * (32 * S + 8), 0))
    sheet.paste(t, (i * (32 * S + 8), 32 * S + 8))
    sheet.paste(t.resize((64, 64), Image.NEAREST), (i * (32 * S + 8) + 40, 32 * S + 8))
sheet.save(out)
