"""Dev viewer: render frames from a .src file with a coordinate grid, plus 1x/4x previews.

usage: python3 -I view.py SRC OUT.png [variant] [frame ...]
"""
import sys
from PIL import Image, ImageDraw, ImageFont

PAL = {
    "ember": {"o": "#2a1519", "d": "#6b2b2d", "m": "#a3453a", "l": "#cf7a4f",
              "b": "#e6c48c", "a": "#efe3c6", "e": "#f2cd55", "h": "#fff7e6"},
}
EXTRA = {  # blockout letters
    "H": "#c06040", "N": "#a05040", "B": "#904030", "W": "#603040", "T": "#b07040",
    "L": "#805030", "A": "#e0d0a0", "X": "#ff00ff",
}


def load(path):
    frames, name, rows = {}, None, []
    for line in open(path):
        line = line.rstrip("\n")
        if line.startswith("=="):
            if name:
                frames[name] = rows
            name, rows = line[2:].strip(), []
        elif name and line.strip() and not line.startswith("#"):
            rows.append(line.split()[0] if " " in line.strip() else line.strip())
    if name:
        frames[name] = rows
    return frames


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def main():
    src, out = sys.argv[1], sys.argv[2]
    variant = sys.argv[3] if len(sys.argv) > 3 else "ember"
    want = sys.argv[4:]
    frames = load(src)
    pal = {**{k: hexrgb(v) for k, v in EXTRA.items()},
           **{k: hexrgb(v) for k, v in PAL[variant].items()}}
    names = [n for n in frames if not want or n in want]
    S = 16
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 10)
    pad = 20
    cw = 32 * S + pad + 4 * 32 + 10 + 40
    W = cw * min(len(names), 3)
    Hh = (32 * S + pad + 20) * ((len(names) + 2) // 3)
    img = Image.new("RGB", (W, Hh), (60, 60, 66))
    d = ImageDraw.Draw(img)
    for i, n in enumerate(names):
        rows = frames[n]
        ox = (i % 3) * cw + pad
        oy = (i // 3) * (32 * S + pad + 20) + pad
        d.text((ox, oy - 16), f"{n}  ({len(rows)} rows)", fill=(255, 255, 255), font=font)
        # checker background
        for y in range(32):
            for x in range(32):
                c = (200, 200, 200) if (x + y) % 2 else (180, 180, 180)
                ch = rows[y][x] if y < len(rows) and x < len(rows[y]) else "?"
                if ch != ".":
                    c = pal.get(ch, (255, 0, 255))
                d.rectangle([ox + x * S, oy + y * S, ox + x * S + S - 1, oy + y * S + S - 1], fill=c)
        for k in range(33):
            col = (0, 120, 255) if k % 4 == 0 else (110, 110, 110)
            d.line([ox + k * S, oy, ox + k * S, oy + 32 * S], fill=col)
            d.line([ox, oy + k * S, ox + 32 * S, oy + k * S], fill=col)
        for k in range(0, 32, 2):
            d.text((ox + k * S + 3, oy + 32 * S + 2), str(k), fill=(255, 255, 0), font=font)
            d.text((ox - 18, oy + k * S + 2), str(k), fill=(255, 255, 0), font=font)
        # previews 1x and 4x on light and dark
        px = ox + 32 * S + 10
        for bi, bg in enumerate([(244, 241, 234), (20, 22, 26)]):
            sp = Image.new("RGB", (32, 32), bg)
            for y in range(min(32, len(rows))):
                for x in range(min(32, len(rows[y]))):
                    ch = rows[y][x]
                    if ch != ".":
                        sp.putpixel((x, y), pal.get(ch, (255, 0, 255)))
            img.paste(sp, (px + bi * 40, oy))
            img.paste(sp.resize((128, 128), Image.NEAREST), (px, oy + 40 + bi * 136))
    img.save(out)


if __name__ == "__main__":
    main()
