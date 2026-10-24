"""Draws text in the app's own TrueType fonts, with the standard library only (no Pillow).

It reads just enough TrueType for store captions:
- character to glyph from cmap (formats 4 and 12)
- outlines from glyf, simple and composite
- advance widths from hmtx
- pair kerning from GPOS (lookup type 2, formats 1 and 2, directly or through extension lookups)

Outlines are filled with the non-zero rule: 5 sub-scanlines a pixel, with exact coverage across
each one. There's no hinting and no ligatures, which text at caption sizes doesn't need.
"""
import math
import struct


def _u16(d, o):
    return struct.unpack_from('>H', d, o)[0]


def _s16(d, o):
    return struct.unpack_from('>h', d, o)[0]


def _u32(d, o):
    return struct.unpack_from('>I', d, o)[0]


class Font:
    def __init__(self, path):
        with open(path, 'rb') as f:
            d = self.d = f.read()
        self.tables = {}
        for i in range(_u16(d, 4)):
            rec = 12 + 16 * i
            self.tables[d[rec:rec + 4].decode('latin-1')] = _u32(d, rec + 8)
        head = self.tables['head']
        self.upem = _u16(d, head + 18)
        self.long_loca = _s16(d, head + 50) == 1
        hhea = self.tables['hhea']
        self.ascent = _s16(d, hhea + 4)
        self.descent = _s16(d, hhea + 6)
        self.metrics = _u16(d, hhea + 34)
        self.glyph_count = _u16(d, self.tables['maxp'] + 4)
        self.cmap = self._cmap()
        self.pairs = self._kern_subtables()
        self._outlines = {}

    # Character map ------------------------------------------------------------------------------

    def _cmap(self):
        d, base = self.d, self.tables['cmap']
        best = None
        for i in range(_u16(d, base + 2)):
            rec = base + 4 + 8 * i
            platform, encoding, off = _u16(d, rec), _u16(d, rec + 2), _u32(d, rec + 4)
            fmt = _u16(d, base + off)
            if platform in (0, 3) and fmt == 12:
                best = (12, base + off)
                break
            if platform in (0, 3) and fmt == 4 and best is None:
                best = (4, base + off)
        fmt, t = best
        out = {}
        if fmt == 4:
            segs = _u16(d, t + 6) // 2
            ends, starts = t + 14, t + 16 + 2 * segs
            deltas, ranges = starts + 2 * segs, starts + 4 * segs
            for s in range(segs):
                end, start = _u16(d, ends + 2 * s), _u16(d, starts + 2 * s)
                delta, ro = _s16(d, deltas + 2 * s), _u16(d, ranges + 2 * s)
                for c in range(start, end + 1):
                    if c == 0xFFFF:
                        continue
                    if ro == 0:
                        g = (c + delta) & 0xFFFF
                    else:
                        g = _u16(d, ranges + 2 * s + ro + 2 * (c - start))
                        g = (g + delta) & 0xFFFF if g else 0
                    out[c] = g
        else:
            for i in range(_u32(d, t + 12)):
                start, end, g = struct.unpack_from('>III', d, t + 16 + 12 * i)
                for c in range(start, end + 1):
                    out[c] = g + c - start
        return out

    def glyph(self, ch):
        return self.cmap.get(ord(ch), 0)

    def advance(self, gid):
        hmtx = self.tables['hmtx']
        return _u16(self.d, hmtx + 4 * min(gid, self.metrics - 1))

    # Kerning ------------------------------------------------------------------------------------

    def _coverage(self, o):
        d, fmt, out = self.d, _u16(self.d, o), {}
        if fmt == 1:
            for i in range(_u16(d, o + 2)):
                out[_u16(d, o + 4 + 2 * i)] = i
        else:
            for i in range(_u16(d, o + 2)):
                start, end, index = struct.unpack_from('>HHH', d, o + 4 + 6 * i)
                for g in range(start, end + 1):
                    out[g] = index + g - start
        return out

    def _classes(self, o):
        d, fmt, out = self.d, _u16(self.d, o), {}
        if fmt == 1:
            first = _u16(d, o + 2)
            for i in range(_u16(d, o + 4)):
                out[first + i] = _u16(d, o + 6 + 2 * i)
        else:
            for i in range(_u16(d, o + 2)):
                start, end, cls = struct.unpack_from('>HHH', d, o + 4 + 6 * i)
                for g in range(start, end + 1):
                    out[g] = cls
        return out

    def _kern_subtables(self):
        """Every PairPos subtable the 'kern' feature uses, in lookup order."""
        d, gpos = self.d, self.tables.get('GPOS')
        if gpos is None:
            return []
        features, lookups = gpos + _u16(d, gpos + 6), gpos + _u16(d, gpos + 8)
        wanted = set()
        for i in range(_u16(d, features)):
            rec = features + 2 + 6 * i
            if d[rec:rec + 4] == b'kern':
                table = features + _u16(d, rec + 4)
                for j in range(_u16(d, table + 2)):
                    wanted.add(_u16(d, table + 4 + 2 * j))
        subtables = []
        for index in sorted(wanted):
            lookup = lookups + _u16(d, lookups + 2 + 2 * index)
            kind = _u16(d, lookup)
            for j in range(_u16(d, lookup + 4)):
                sub = lookup + _u16(d, lookup + 6 + 2 * j)
                sub_kind = kind
                if kind == 9:  # Extension: the real subtable sits further on.
                    sub_kind = _u16(d, sub + 2)
                    sub = sub + _u32(d, sub + 4)
                if sub_kind == 2:
                    subtables.append(self._pair_subtable(sub))
        return subtables

    def _pair_subtable(self, o):
        d = self.d
        fmt, cov, vf1, vf2 = struct.unpack_from('>HHHH', d, o)
        size1, size2 = 2 * bin(vf1).count('1'), 2 * bin(vf2).count('1')
        # XAdvance of the first glyph's value record, the only part kerning needs here.
        x_adv = 2 * bin(vf1 & 0x3).count('1') if vf1 & 0x4 else None
        sub = {'fmt': fmt, 'cov': self._coverage(o + cov), 'o': o, 'size1': size1,
               'size2': size2, 'x_adv': x_adv}
        if fmt == 2:
            sub['cd1'] = self._classes(o + _u16(d, o + 8))
            sub['cd2'] = self._classes(o + _u16(d, o + 10))
            sub['n2'] = _u16(d, o + 14)
        return sub

    def kern(self, left, right):
        d = self.d
        for sub in self.pairs:
            index = sub['cov'].get(left)
            if index is None:
                continue
            o, x_adv = sub['o'], sub['x_adv']
            if sub['fmt'] == 1:
                pair_set = o + _u16(d, o + 10 + 2 * index)
                record = 2 + sub['size1'] + sub['size2']
                for i in range(_u16(d, pair_set)):
                    rec = pair_set + 2 + record * i
                    if _u16(d, rec) == right:
                        return 0 if x_adv is None else _s16(d, rec + 2 + x_adv)
                continue
            c1, c2 = sub['cd1'].get(left, 0), sub['cd2'].get(right, 0)
            record = sub['size1'] + sub['size2']
            rec = o + 16 + (c1 * sub['n2'] + c2) * record
            return 0 if x_adv is None else _s16(d, rec + x_adv)
        return 0

    # Outlines -----------------------------------------------------------------------------------

    def _loca(self, gid):
        loca = self.tables['loca']
        if self.long_loca:
            return _u32(self.d, loca + 4 * gid), _u32(self.d, loca + 4 * gid + 4)
        return 2 * _u16(self.d, loca + 2 * gid), 2 * _u16(self.d, loca + 2 * gid + 2)

    def contours(self, gid):
        """The glyph's contours in font units, each a list of (x, y, on_curve)."""
        if gid not in self._outlines:
            self._outlines[gid] = self._read(gid)
        return self._outlines[gid]

    def _read(self, gid):
        d = self.d
        start, end = self._loca(gid)
        if start == end:
            return []
        o = self.tables['glyf'] + start
        count = _s16(d, o)
        if count >= 0:
            return self._simple(o, count)
        return self._composite(o)

    def _simple(self, o, count):
        d = self.d
        ends = [_u16(d, o + 10 + 2 * i) for i in range(count)]
        total = ends[-1] + 1 if ends else 0
        p = o + 10 + 2 * count
        p += 2 + _u16(d, p)
        flags = []
        while len(flags) < total:
            f = d[p]
            p += 1
            flags.append(f)
            if f & 8:
                flags.extend([f] * d[p])
                p += 1
        xs, ys = [], []
        for coords, short, same in ((xs, 2, 16), (ys, 4, 32)):
            value = 0
            for f in flags:
                if f & short:
                    step = d[p]
                    p += 1
                    value += step if f & same else -step
                elif not f & same:
                    value += _s16(d, p)
                    p += 2
                coords.append(value)
        out, begin = [], 0
        for e in ends:
            out.append([(xs[i], ys[i], bool(flags[i] & 1)) for i in range(begin, e + 1)])
            begin = e + 1
        return out

    def _composite(self, o):
        d = self.d
        p, out = o + 10, []
        while True:
            flags, gid = _u16(d, p), _u16(d, p + 2)
            p += 4
            if flags & 1:
                a, b = _s16(d, p), _s16(d, p + 2)
                p += 4
            else:
                a, b = struct.unpack_from('>bb', d, p)
                p += 2
            xx, xy, yx, yy = 1.0, 0.0, 0.0, 1.0
            if flags & 0x8:
                xx = yy = _s16(d, p) / 16384
                p += 2
            elif flags & 0x40:
                xx, yy = _s16(d, p) / 16384, _s16(d, p + 2) / 16384
                p += 4
            elif flags & 0x80:
                xx, xy, yx, yy = (_s16(d, p + 2 * i) / 16384 for i in range(4))
                p += 8
            dx, dy = (a, b) if flags & 2 else (0, 0)
            for contour in self.contours(gid):
                out.append([(x * xx + y * yx + dx, x * xy + y * yy + dy, on) for x, y, on in contour])
            if not flags & 0x20:
                return out


def _segments(contour, steps):
    """Flattens one TrueType contour into line segments."""
    pts = contour
    if not any(on for _, _, on in pts):
        x0, y0, _ = pts[0]
        x1, y1, _ = pts[1 % len(pts)]
        pts = [((x0 + x1) / 2, (y0 + y1) / 2, True)] + pts
    first = next(i for i, p in enumerate(pts) if p[2])
    pts = pts[first:] + pts[:first]
    out, start = [], pts[0]
    cur, ctrl = (start[0], start[1]), None
    for x, y, on in pts[1:] + [start]:
        if on:
            if ctrl is None:
                out.append((cur, (x, y)))
            else:
                out.extend(_quad(cur, ctrl, (x, y), steps))
                ctrl = None
            cur = (x, y)
        elif ctrl is None:
            ctrl = (x, y)
        else:
            mid = ((ctrl[0] + x) / 2, (ctrl[1] + y) / 2)
            out.extend(_quad(cur, ctrl, mid, steps))
            cur, ctrl = mid, (x, y)
    return out


def _quad(a, c, b, steps):
    out, prev = [], a
    for i in range(1, steps + 1):
        t = i / steps
        u = 1 - t
        pt = (u * u * a[0] + 2 * u * t * c[0] + t * t * b[0], u * u * a[1] + 2 * u * t * c[1] + t * t * b[1])
        out.append((prev, pt))
        prev = pt
    return out


SUB = 5  # Sub-scanlines a pixel.


def coverage(edges, width, height):
    """Fills line segments (pixel units, y down) with the non-zero rule: a flat list of 0 to 1."""
    cov = [0.0] * (width * height)
    rows = {}
    for (x0, y0), (x1, y1) in edges:
        if y0 == y1:
            continue
        wind = 1 if y1 > y0 else -1
        if y0 > y1:
            x0, y0, x1, y1 = x1, y1, x0, y0
        first = max(0, math.ceil(y0 * SUB - 0.5))
        last = min(height * SUB - 1, math.ceil(y1 * SUB - 0.5) - 1)
        slope = (x1 - x0) / (y1 - y0)
        for s in range(first, last + 1):
            y = (s + 0.5) / SUB
            rows.setdefault(s, []).append((x0 + (y - y0) * slope, wind))
    weight = 1.0 / SUB
    for s, crossings in rows.items():
        crossings.sort()
        base = (s // SUB) * width
        wind = 0
        for i, (x, w) in enumerate(crossings):
            before = wind
            wind += w
            if before == 0 and wind != 0:
                left = x
            elif before != 0 and wind == 0:
                _span(cov, base, width, left, x, weight)
    return cov


def _span(cov, base, width, a, b, weight):
    a, b = max(0.0, a), min(float(width), b)
    if b <= a:
        return
    ia, ib = int(a), int(b)
    if ia == ib:
        cov[base + ia] += (b - a) * weight
        return
    cov[base + ia] += (ia + 1 - a) * weight
    for i in range(ia + 1, min(ib, width)):
        cov[base + i] += weight
    if ib < width:
        cov[base + ib] += (b - ib) * weight


def layout(font, text, size, tracking=0.0):
    """Glyphs and their x positions in pixels, plus the line's width."""
    scale = size / font.upem
    out, x, prev = [], 0.0, None
    for ch in text:
        gid = font.glyph(ch)
        if prev is not None:
            x += font.kern(prev, gid) * scale + tracking * size
        out.append((gid, x))
        x += font.advance(gid) * scale
        prev = gid
    return out, x


def width(font, text, size, tracking=0.0):
    return layout(font, text, size, tracking)[1]


def wrap(font, text, size, max_width, tracking=0.0):
    """Breaks text into as few lines as fit, then evens them out so no line is left short."""
    words = text.split(' ')
    lines, line = [], ''
    for w in words:
        trial = (line + ' ' + w).strip()
        if line and width(font, trial, size, tracking) > max_width:
            lines.append(line)
            line = w
        else:
            line = trial
    lines.append(line)
    if len(lines) == 2:
        best, best_w = lines, max(width(font, l, size, tracking) for l in lines)
        for cut in range(1, len(words)):
            pair = [' '.join(words[:cut]), ' '.join(words[cut:])]
            w = max(width(font, l, size, tracking) for l in pair)
            if w <= max_width and w < best_w:
                best, best_w = pair, w
        lines = best
    return lines


def render(font, text, size, tracking=0.0):
    """Rasterises one line: (coverage, width, height, baseline) in whole pixels."""
    glyphs, advance = layout(font, text, size, tracking)
    scale = size / font.upem
    top = math.ceil(font.ascent * scale) + 2
    height = top + math.ceil(-font.descent * scale) + 2
    w = math.ceil(advance) + 4
    steps = max(4, int(size / 12))
    edges = []
    for gid, x in glyphs:
        for contour in font.contours(gid):
            for (ax, ay), (bx, by) in _segments(contour, steps):
                edges.append(((2 + x + ax * scale, top - ay * scale), (2 + x + bx * scale, top - by * scale)))
    return coverage(edges, w, height), w, height, top
