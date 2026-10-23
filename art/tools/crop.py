"""crop.py SRC OUT x0 y0 x1 y1 scale frame..."""
import sys
sys.path.insert(0, '.')
from view import load, PAL, hexrgb
from PIL import Image, ImageDraw
src, out = sys.argv[1], sys.argv[2]
x0, y0, x1, y1, S = map(int, sys.argv[3:8])
names = sys.argv[8:]
frames = load(src)
pal = {k: hexrgb(v) for k, v in PAL['ember'].items()}
w, h = (x1 - x0) * S, (y1 - y0) * S
sheet = Image.new('RGB', (len(names) * (w + 10), 2 * (h + 10) + 14), (90, 90, 90))
d = ImageDraw.Draw(sheet)
for i, n in enumerate(names):
    rows = frames[n]
    for j, bg in enumerate([(244, 241, 234), (20, 22, 26)]):
        im = Image.new('RGB', (32, 32), bg)
        for y in range(32):
            for x in range(32):
                c = rows[y][x]
                if c != '.': im.putpixel((x, y), pal[c])
        im = im.crop((x0, y0, x1, y1)).resize((w, h), Image.NEAREST)
        sheet.paste(im, (i * (w + 10), 14 + j * (h + 10)))
    d.text((i * (w + 10) + 2, 1), n, fill=(255, 255, 255))
sheet.save(out)
