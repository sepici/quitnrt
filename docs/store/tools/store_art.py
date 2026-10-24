"""Frames the app's Roborazzi screenshots for the Play listing, and draws the feature graphic.

usage, from the repository's folder:
    ./gradlew :app:recordRoborazziDebug --tests "app.thelasthit.nrt.ui.StoreScreenshotTest"
    python -I docs/store/tools/store_art.py [name ...]

It reads the light store-* renders in app/build/outputs/roborazzi/, which StoreScreenshotTest
makes from one set of data with generic products, and writes docs/store/screenshots/:
  01-today.png to 08-log.png   1080 x 1920 (9:16), a caption above a plain phone frame
  feature-graphic.png          1024 x 500, the demon band from Today with the app's name

Names on the command line (such as "today" or "feature") make only those. Every PNG is 24-bit
RGB with no alpha, as Play asks. Standard library only: PNGs are read and written with zlib, and
text is drawn by ttf.py in the app's own Schibsted Grotesk. Captions follow docs/STYLE.md.

A shot that starts partway down a page has to start in the plain space between two sections. If
a layout change moves its section, the script stops and names the nearest plain rows, so the
start in SHOTS can be moved. Look at every output all the same.
"""
import json
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ttf  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..', '..', '..'))
RENDERS = os.path.join(ROOT, 'app', 'build', 'outputs', 'roborazzi')
FONTS = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'font')
RESOURCES = os.path.join(ROOT, 'core', 'domain', 'src', 'main', 'resources')
OUT = os.path.normpath(os.path.join(HERE, '..', 'screenshots'))

# The renders are a Pixel 7: 411 dp wide at 420 dpi.
DENSITY = 2.625

# Each shot: output name, render, the first row of the page to show (render pixels) and the
# caption. A tall render is cut to a phone's height from that row, as if scrolled there, so a
# row other than 0 must be plain: one colour across, between two sections.
SHOTS = [
    ('01-today', 'store-today', 0, "Today against your plan's daily guide"),
    ('02-plan', 'store-plan', 1190, 'A taper built from your own usual day'),
    ('03-lair', 'store-lair', 0, 'Every week you meet is a hit on the demon you named'),
    ('04-schedule', 'store-schedule', 1830, 'A missed week comes back when you meet the next'),
    ('05-insights', 'store-insights', 560, 'Each day and each week against the plan'),
    ('06-hours', 'store-insights', 2620, 'The hours you use NRT most'),
    ('07-advice', 'store-advice', 0, 'Ways through a craving, matched to your triggers'),
    ('08-log', 'store-log', 0, 'Log a dose now, or one you forgot earlier'),
]

# How far a plain row's colour may wander, per channel, for antialiasing and dithering.
PLAIN_TOLERANCE = 2
# How far above and below a start the script looks for plain rows to suggest.
PLAIN_SEARCH = 400

# Colours from the app's light theme (ui/theme/Color.kt), except the phone's own.
BG = '#ECE9DE'        # sunk
TEXT = '#10221A'
SKY = '#FBEFD0'       # the band and lair's sky
SKY_TEXT2 = '#4F4A3A'  # stageText2, quieter text on the sky
HP_FULL, HP_EMPTY = '#156041', '#D5BE86'
BODY = '#1D2420'      # the phone: a near-black with the hero's green in it
BODY_EDGE = '#3A4540'
CAMERA = '#0B100D'

# Words a caption may capitalise after its first.
PROPER = {'NRT', 'Gary', 'NHS', 'CDC'}

W, H = 1080, 1920
CAPTION_SIZE = 64
CAPTION_TRACKING = -0.02  # The app's headlines are -0.03 em; a touch looser reads better small.
CAPTION_MAX = W - 2 * 80
DEVICE_TOP = 400
DEVICE_W = 856
BEZEL = 18
DEVICE_R = 92
STATUS_DP = 28


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# PNG ---------------------------------------------------------------------------------------------

def read_png(path, first=0, count=None):
    """Rows first to first + count of an 8-bit RGB or RGBA PNG, as RGB bytes, plus its size."""
    with open(path, 'rb') as f:
        data = f.read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise SystemExit(f'{path} is not a PNG')
    pos, idat = 8, []
    while pos < len(data):
        length = struct.unpack_from('>I', data, pos)[0]
        tag, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b'IHDR':
            w, h, depth, kind, _, _, interlace = struct.unpack('>IIBBBBB', body)
        elif tag == b'IDAT':
            idat.append(body)
        elif tag == b'IEND':
            break
    if depth != 8 or interlace or kind not in (2, 6):
        raise SystemExit(f'{path}: only 8-bit RGB or RGBA without interlacing is read')
    bpp = 4 if kind == 6 else 3
    stride = w * bpp
    count = h - first if count is None else min(count, h - first)
    need = (first + count) * (stride + 1)
    raw = zlib.decompressobj().decompress(b''.join(idat), need)
    prev = bytes(stride)
    rows = []
    for y in range(first + count):
        f, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        if f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 255
        elif f == 2:
            line = bytearray((a + b) & 255 for a, b in zip(line, prev))
        elif f == 3:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = bytes(line)
        if y >= first:
            if bpp == 4:
                out = bytearray(w * 3)
                out[0::3], out[1::3], out[2::3] = prev[0::4], prev[1::4], prev[2::4]
                rows.append(bytes(out))
            else:
                rows.append(prev)
    return rows, w, h


def write_png(path, canvas):
    raw = bytearray()
    stride = canvas.w * 3
    for y in range(canvas.h):
        raw.append(0)
        raw += canvas.buf[y * stride:(y + 1) * stride]

    def chunk(tag, body):
        return struct.pack('>I', len(body)) + tag + body + struct.pack('>I', zlib.crc32(tag + body) & 0xFFFFFFFF)

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', canvas.w, canvas.h, 8, 2, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)


# Drawing -----------------------------------------------------------------------------------------

class Canvas:
    def __init__(self, w, h, colour):
        self.w, self.h = w, h
        self.buf = bytearray(bytes(colour) * (w * h))

    def get(self, x, y):
        i = (y * self.w + x) * 3
        return tuple(self.buf[i:i + 3])

    def rect(self, x, y, w, h, colour):
        x0, y0, x1, y1 = max(0, x), max(0, y), min(self.w, x + w), min(self.h, y + h)
        if x1 <= x0 or y1 <= y0:
            return
        line = bytes(colour) * (x1 - x0)
        for yy in range(y0, y1):
            i = (yy * self.w + x0) * 3
            self.buf[i:i + len(line)] = line

    def mix(self, x, y, colour, alpha):
        if 0 <= x < self.w and 0 <= y < self.h and alpha > 0:
            i = (y * self.w + x) * 3
            if alpha >= 1:
                self.buf[i:i + 3] = bytes(colour)
            else:
                for c in range(3):
                    self.buf[i + c] = int(self.buf[i + c] * (1 - alpha) + colour[c] * alpha + 0.5)

    def round_rect(self, x, y, w, h, r, colour):
        """A rounded rectangle, smoothed at the corners."""
        self.rect(x, y + r, w, h - 2 * r, colour)
        self.rect(x + r, y, w - 2 * r, r, colour)
        self.rect(x + r, y + h - r, w - 2 * r, r, colour)
        for cx, cy, px, py in ((x + r, y + r, x, y), (x + w - r, y + r, x + w - r, y),
                               (x + r, y + h - r, x, y + h - r), (x + w - r, y + h - r, x + w - r, y + h - r)):
            for yy in range(py, py + r):
                for xx in range(px, px + r):
                    self.mix(xx, yy, colour, _circle_cover(xx, yy, cx, cy, r))

    def top_corners(self, x, w, y, r):
        """What's under the top corners of a w-wide picture at (x, y), to put back with round_top."""
        return [(px, bytes(self.buf[((y + j) * self.w + px) * 3:((y + j) * self.w + px + r) * 3]))
                for px in (x, x + w - r) for j in range(r)]

    def round_top(self, saved, x, w, y, r):
        """Rounds off a picture's top corners, showing the saved frame outside the curves."""
        for k, (px, row) in enumerate(saved):
            j = k % r
            cx = x + r if px == x else x + w - r
            for i in range(r):
                outside = 1 - _circle_cover(px + i, y + j, cx, y + r, r)
                self.mix(px + i, y + j, tuple(row[3 * i:3 * i + 3]), outside)

    def circle(self, cx, cy, r, colour):
        for yy in range(int(cy - r) - 1, int(cy + r) + 2):
            for xx in range(int(cx - r) - 1, int(cx + r) + 2):
                self.mix(xx, yy, colour, _circle_cover(xx, yy, cx, cy, r))

    def paste(self, rows, x, y):
        for j, row in enumerate(rows):
            yy = y + j
            if 0 <= yy < self.h:
                n = min(len(row) // 3, self.w - x)
                i = (yy * self.w + x) * 3
                self.buf[i:i + n * 3] = row[:n * 3]

    def text(self, font, s, x, baseline, size, colour, tracking=0.0):
        cov, w, h, top = ttf.render(font, s, size, tracking)
        ox, oy = round(x) - 2, round(baseline) - top
        for j in range(h):
            base = j * w
            for i in range(w):
                a = cov[base + i]
                if a > 0.002:
                    self.mix(ox + i, oy + j, colour, min(1.0, a))


def _circle_cover(px, py, cx, cy, r, n=4):
    inside = 0
    for sy in range(n):
        for sx in range(n):
            dx, dy = px + (sx + 0.5) / n - cx, py + (sy + 0.5) / n - cy
            inside += dx * dx + dy * dy <= r * r
    return inside / (n * n)


def resize(rows, sw, dw, dh):
    """Area-averaged downscale of RGB rows to dw x dh."""
    sh = len(rows)

    def weights(src, dst):
        scale, out = src / dst, []
        for i in range(dst):
            a, b = i * scale, (i + 1) * scale
            taps, j = [], int(a)
            while j < b and j < src:
                cover = min(b, j + 1) - max(a, j)
                if cover > 1e-9:
                    taps.append((j, cover / scale))
                j += 1
            out.append(taps)
        return out

    xw = [[(3 * j + c, w) for j, w in taps] for taps in weights(sw, dw) for c in range(3)]
    out = []
    for taps in weights(sh, dh):
        acc = [0.0] * (sw * 3)
        for j, w in taps:
            acc = [a + w * b for a, b in zip(acc, rows[j])]
        out.append(bytes(min(255, int(sum(acc[i] * w for i, w in t) + 0.5)) for t in xw))
    return out


def luminance(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def is_plain(row):
    """Whether a row of RGB bytes is one colour across, as the space between a page's sections is."""
    first = row[0:3]
    return all(abs(row[i] - first[i % 3]) <= PLAIN_TOLERANCE for i in range(len(row)))


def check_start(name, path, first):
    """Stops when a shot would start partway through a section, naming the nearest plain rows."""
    if first == 0:
        return
    top = max(0, first - PLAIN_SEARCH)
    rows, _, _ = read_png(path, top, 2 * PLAIN_SEARCH + 1)
    if is_plain(rows[first - top]):
        return
    plain = [top + j for j, row in enumerate(rows) if is_plain(row)]
    above = max((y for y in plain if y < first), default=None)
    below = min((y for y in plain if y > first), default=None)
    near = ', '.join(f'{y} {side}' for y, side in ((above, 'above'), (below, 'below')) if y is not None)
    raise SystemExit(f'{name}: row {first} of {os.path.basename(path)} cuts through something. The nearest plain '
                     f'rows are {near or f"more than {PLAIN_SEARCH} rows away"}. A plain row can also be a gap inside '
                     'a section, so move the start in SHOTS to one between two sections and look at the output.')


# Screenshots -------------------------------------------------------------------------------------

def shot(name, render, first, caption, fonts):
    c = Canvas(W, H, rgb(BG))
    lines = ttf.wrap(fonts['extrabold'], caption, CAPTION_SIZE, CAPTION_MAX, tracking=CAPTION_TRACKING)
    if len(lines) > 2:
        raise SystemExit(f'{name}: the caption needs {len(lines)} lines; keep it to 2')
    # Centred in the space above the phone, by the capitals' height.
    leading = round(CAPTION_SIZE * 1.12)
    cap = round(CAPTION_SIZE * 0.72)
    baseline = (DEVICE_TOP - cap - leading * (len(lines) - 1)) // 2 + cap
    for i, line in enumerate(lines):
        lw = ttf.width(fonts['extrabold'], line, CAPTION_SIZE, CAPTION_TRACKING)
        c.text(fonts['extrabold'], line, (W - lw) / 2, baseline + i * leading, CAPTION_SIZE, rgb(TEXT), tracking=CAPTION_TRACKING)

    x, y = (W - DEVICE_W) // 2, DEVICE_TOP
    body_h = H - y + DEVICE_R + 20
    c.round_rect(x, y, DEVICE_W, body_h, DEVICE_R, rgb(BODY_EDGE))
    c.round_rect(x + 3, y + 3, DEVICE_W - 6, body_h - 6, DEVICE_R - 3, rgb(BODY))

    sx, sy = x + BEZEL, y + BEZEL
    sw = DEVICE_W - 2 * BEZEL
    scale = sw / 1078 * DENSITY  # canvas pixels a dp
    status = round(STATUS_DP * scale)
    page_h = H - sy - status
    src_h = round(page_h * 1078 / sw)
    path = os.path.join(RENDERS, f'{render}-light.png')
    if not os.path.exists(path):
        raise SystemExit(f'{name}: {path} is missing. Record the renders first: see the usage at the top')
    check_start(name, path, first)
    rows, rw, rh = read_png(path, first, src_h)
    if len(rows) < src_h:
        raise SystemExit(f'{name}: {render} is {rh} px tall, too short to start at {first}')
    page = resize(rows, rw, sw, page_h)

    # The status bar takes the colour at the top of the page, as Android draws edge to edge.
    r = DEVICE_R - BEZEL
    frame = c.top_corners(sx, sw, sy, r)
    bar = tuple(page[0][6:9])
    c.rect(sx, sy, sw, status, bar)
    c.paste(page, sx, sy + status)
    ink = rgb(TEXT) if luminance(bar) > 140 else (246, 244, 236)
    clock = round(14 * scale)
    c.text(fonts['semibold'], '15:05', sx + round(26 * scale), sy + round(status * 0.68), clock, ink)
    bw, bh = round(22 * scale), round(11 * scale)
    bx, by = sx + sw - round(26 * scale) - bw, sy + (status - bh) // 2 + round(1 * scale)
    c.round_rect(bx, by, bw, bh, max(3, bh // 4), ink)
    c.round_rect(bx + 3, by + 3, bw - 6, bh - 6, max(2, bh // 4 - 2), bar)
    c.rect(bx + 5, by + 5, round((bw - 10) * 0.7), bh - 10, ink)
    c.rect(bx + bw, by + bh // 3, max(2, round(scale)), bh - 2 * (bh // 3), ink)
    c.circle(sx + sw / 2, sy + status / 2, 5.5 * scale, rgb(CAMERA))
    c.round_top(frame, sx, sw, sy, r)
    write_png(os.path.join(OUT, f'{name}.png'), c)
    print(f'{name}.png from {render} at row {first}: "{caption}"')


# Feature graphic ---------------------------------------------------------------------------------

def feature(fonts):
    fw, fh, px = 1024, 500, 8
    c = Canvas(fw, fh, rgb(SKY))
    with open(os.path.join(RESOURCES, 'backdrops', 'band.json')) as f:
        band = json.load(f)
    with open(os.path.join(RESOURCES, 'creatures', 'dragon.json')) as f:
        dragon = json.load(f)
    pal = {k: rgb(v) for k, v in band['palettes']['light'].items()}
    top = fh - band['height'] * px
    for j, row in enumerate(band['rows']['light']):
        for i, ch in enumerate(row):
            if i * px < fw:
                c.rect(i * px, top + j * px, px, px, pal[ch])
    colours = {k: rgb(v) for k, v in dragon['variants'][dragon['defaultVariant']].items()}
    ox, oy = band['sprite']['x'] * px, top + band['sprite']['y'] * px
    for j, row in enumerate(dragon['frames']['idle1']):
        for i, ch in enumerate(row):
            if ch != '.':
                c.rect(ox + i * px, oy + j * px, px, px, colours[ch])

    # The name and its subtitle sit in the sky to the right, as Gary's name does on Today's band.
    tx = 392
    c.text(fonts['extrabold'], 'The Last Hit', tx, 180, 104, rgb(TEXT), tracking=-0.045)
    c.text(fonts['bold'], 'Quit NRT', tx + 4, 246, 46, rgb(SKY_TEXT2), tracking=-0.02)
    # An HP bar like Gary's in the lair: 100 of 120 HP, with two weeks' hits landed.
    bx, by, bw, gap, bh = tx + 4, 284, 36, 8, 22
    for k in range(12):
        c.rect(bx + k * (bw + gap), by, bw, bh, rgb(HP_FULL if k < 10 else HP_EMPTY))
    write_png(os.path.join(OUT, 'feature-graphic.png'), c)
    print('feature-graphic.png')


def check_captions():
    """The mechanical half of docs/STYLE.md, as checkCopy holds the app's strings to it: no em or
    en dashes, no banned words, no exclamation marks or questions, and sentence case."""
    with open(os.path.join(ROOT, 'config', 'copy-lint', 'banned.txt'), encoding='utf-8') as f:
        banned = [l.strip().lower() for l in f if l.strip() and not l.startswith('#')]
    for name, _, _, caption in SHOTS:
        words = ' ' + ''.join(ch if ch.isalnum() or ch in "'-" else ' ' for ch in caption.lower()) + ' '
        problems = [ch for ch in '\u2014\u2013!?' if ch in caption]
        problems += [w for w in banned if f' {w} ' in words]
        problems += [w for w in caption.split()[1:] if w[:1].isupper() and w not in PROPER]
        if problems:
            raise SystemExit(f'{name}: the caption breaks docs/STYLE.md with {problems}')


def main(names):
    check_captions()
    os.makedirs(OUT, exist_ok=True)
    fonts = {w: ttf.Font(os.path.join(FONTS, f'schibsted_grotesk_{w}.ttf'))
             for w in ('semibold', 'bold', 'extrabold')}
    for name, render, first, caption in SHOTS:
        if not names or any(n in name for n in names):
            shot(name, render, first, caption, fonts)
    if not names or 'feature' in names:
        feature(fonts)


if __name__ == '__main__':
    main(sys.argv[1:])
