#!/usr/bin/env python3
"""Validate and render a creature sprite file (e.g. dragon.json).

usage: python3 -I render.py [path/to/creature.json]

Validation (fails loudly, exit code 1):
  * required keys, size == 32, the 8 palette slots, hex colours per variant
  * every frame is exactly 32 rows of 32 characters
  * only '.' and the slot letters appear
  * no frame uses more than 8 colours (per slot letter, and per resolved colour in every variant)

Outputs, written next to the json:
  <creature>_<variant>_<frame>_light.png / _dark.png   each frame at 8x, nearest neighbour
  <creature>_contact_sheet.png                          all frames x all variants
  <creature>_idle.gif                                   idle1/idle2 loop at 2 fps
  creature.png                                          idle1, default variant, 4x, transparent
"""
import json
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

SIZE = 32
SLOTS = ("o", "d", "m", "l", "b", "a", "e", "h")
REQUIRED_FRAMES = ("idle1", "idle2", "hurt", "rough", "barely")
MAX_COLOURS = 8
LIGHT_BG = (0xF4, 0xF1, 0xEA)
DARK_BG = (0x14, 0x16, 0x1A)
FRAME_SCALE = 8
SHEET_SCALE = 4
UI_SCALE = 4
GIF_SCALE = 8
GIF_FRAME_MS = 500  # 2 fps
HEX = re.compile(r"^#[0-9A-Fa-f]{6}$")


class SpriteError(Exception):
    pass


def hex_to_rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


# ------------------------------------------------------------------ validation
def validate(data):
    errors = []
    for key in ("creature", "size", "slots", "variants", "defaultVariant", "frames"):
        if key not in data:
            errors.append(f"missing top-level key '{key}'")
    if errors:
        raise SpriteError("\n".join(errors))

    if data["size"] != SIZE:
        errors.append(f"size must be {SIZE}, got {data['size']!r}")

    slots = data["slots"]
    if set(slots) != set(SLOTS):
        errors.append(f"slots must be exactly {sorted(SLOTS)}, got {sorted(slots)}")

    variants = data["variants"]
    if not isinstance(variants, dict) or not variants:
        errors.append("variants must be a non-empty object")
        variants = {}
    for vname, pal in variants.items():
        if set(pal) != set(SLOTS):
            errors.append(f"variant '{vname}': slots {sorted(pal)} != {sorted(SLOTS)}")
        for slot, col in pal.items():
            if not isinstance(col, str) or not HEX.match(col):
                errors.append(f"variant '{vname}': slot '{slot}' colour {col!r} is not #RRGGBB")
    if data["defaultVariant"] not in variants:
        errors.append(f"defaultVariant '{data['defaultVariant']}' is not a variant")

    frames = data["frames"]
    for name in REQUIRED_FRAMES:
        if name not in frames:
            errors.append(f"missing frame '{name}'")
    allowed = set(SLOTS) | {"."}
    for fname, rows in frames.items():
        if not isinstance(rows, list) or len(rows) != SIZE:
            n = len(rows) if isinstance(rows, list) else type(rows).__name__
            errors.append(f"frame '{fname}': expected {SIZE} rows, got {n}")
            continue
        for y, row in enumerate(rows):
            if not isinstance(row, str) or len(row) != SIZE:
                n = len(row) if isinstance(row, str) else type(row).__name__
                errors.append(f"frame '{fname}' row {y}: expected {SIZE} chars, got {n}")
                continue
            bad = sorted(set(row) - allowed)
            if bad:
                cols = [x for x, c in enumerate(row) if c not in allowed]
                errors.append(f"frame '{fname}' row {y}: illegal char(s) {bad} at x={cols}")
        used = {c for row in rows if isinstance(row, str) for c in row} - {"."}
        if len(used) > MAX_COLOURS:
            errors.append(f"frame '{fname}': uses {len(used)} colours (max {MAX_COLOURS})")
        for vname, pal in variants.items():
            resolved = {pal[c].upper() for c in used if c in pal}
            if len(resolved) > MAX_COLOURS:
                errors.append(f"frame '{fname}' in '{vname}': {len(resolved)} colours (max {MAX_COLOURS})")
        if not used:
            errors.append(f"frame '{fname}' is empty")
    if errors:
        raise SpriteError("\n".join(errors))


# ------------------------------------------------------------------ rendering
def sprite(rows, pal):
    """1x RGBA image of one frame."""
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c != ".":
                px[x, y] = hex_to_rgb(pal[c]) + (255,)
    return im


def scaled(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def on_bg(im, bg):
    out = Image.new("RGBA", im.size, bg + (255,))
    out.alpha_composite(im)
    return out.convert("RGB")


def font(px):
    for path in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, px)
    return ImageFont.load_default()


def contact_sheet(data, frame_names):
    variants = list(data["variants"].items())
    cell = SIZE * SHEET_SCALE            # 128
    gap, pad, label_w, head_h = 12, 24, 96, 40
    thumb = SIZE + 8                     # 1x preview column
    cols = len(frame_names)
    width = pad * 2 + label_w + cols * (cell + gap) + 2 * thumb
    block_h = 2 * cell + gap             # light row + dark row per variant
    height = pad * 2 + head_h + len(variants) * (block_h + gap * 2)
    sheet = Image.new("RGB", (width, height), (0x2B, 0x2D, 0x31))
    d = ImageDraw.Draw(sheet)
    f_head, f_lab, f_small = font(15), font(14), font(11)
    d.text((pad, pad - 8), f"{data['creature']}  -  {cols} frames x {len(variants)} variants  "
           f"({SHEET_SCALE}x, plus 1x at right)", fill=(220, 220, 220), font=f_small)
    x0 = pad + label_w
    for i, fn in enumerate(frame_names):
        d.text((x0 + i * (cell + gap), pad + 14), fn, fill=(240, 240, 240), font=f_head)
    for vi, (vname, pal) in enumerate(variants):
        y0 = pad + head_h + vi * (block_h + gap * 2)
        d.text((pad, y0 + 4), vname, fill=(240, 240, 240), font=f_lab)
        if vname == data["defaultVariant"]:
            d.text((pad, y0 + 22), "(default)", fill=(160, 160, 160), font=f_small)
        sw = 10
        for si, s in enumerate(SLOTS):
            sx, sy = pad + (si % 4) * (sw + 4), y0 + 44 + (si // 4) * (sw + 4)
            d.rectangle([sx, sy, sx + sw - 1, sy + sw - 1], fill=hex_to_rgb(pal[s]),
                        outline=(90, 90, 90))
        for ri, bg in enumerate((LIGHT_BG, DARK_BG)):
            yy = y0 + ri * (cell + gap)
            for i, fn in enumerate(frame_names):
                im = sprite(data["frames"][fn], pal)
                sheet.paste(on_bg(scaled(im, SHEET_SCALE), bg), (x0 + i * (cell + gap), yy))
            # 1x previews of idle1 and the last frame, true size
            tx = x0 + cols * (cell + gap)
            for k, fn in enumerate((frame_names[0], frame_names[-1])):
                im = sprite(data["frames"][fn], pal)
                sheet.paste(on_bg(im, bg), (tx + k * thumb, yy))
    return sheet


def idle_gif(data, path):
    pal = data["variants"][data["defaultVariant"]]
    names = [n for n in ("idle1", "idle2") if n in data["frames"]]
    # one shared indexed palette -> exact colours, no dithering
    colours = [LIGHT_BG, DARK_BG] + sorted({hex_to_rgb(c) for c in pal.values()})
    index = {c: i for i, c in enumerate(colours)}
    flat = [v for c in colours for v in c] + [0] * (768 - 3 * len(colours))
    w = SIZE * GIF_SCALE
    frames = []
    for n in names:
        rgb = Image.new("RGB", (w * 2 + 16, w), (0x2B, 0x2D, 0x31))
        im = scaled(sprite(data["frames"][n], pal), GIF_SCALE)
        rgb.paste(on_bg(im, LIGHT_BG), (0, 0))
        rgb.paste(on_bg(im, DARK_BG), (w + 16, 0))
        pixels = list(getattr(rgb, "get_flattened_data", rgb.getdata)())
        colours_needed = set(pixels)
        for c in colours_needed:
            if c not in index:
                index[c] = len(colours)
                colours.append(c)
                flat[3 * index[c]:3 * index[c] + 3] = list(c)
        p = Image.new("P", rgb.size)
        p.putpalette(flat)
        p.putdata([index[c] for c in pixels])
        frames.append(p)
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=GIF_FRAME_MS,
                   loop=0, optimize=False, disposal=1)


def main(argv):
    here = os.path.dirname(os.path.abspath(__file__))
    path = argv[1] if len(argv) > 1 else os.path.join(here, "..", "..", "core", "domain", "src", "main", "resources", "creatures", "dragon.json")
    with open(path) as f:
        data = json.load(f)
    try:
        validate(data)
    except SpriteError as e:
        sys.stderr.write(f"\nINVALID SPRITE FILE {path}\n{e}\n\n")
        raise SystemExit(1)

    out = os.path.dirname(os.path.abspath(path))
    name = data["creature"]
    frame_names = list(data["frames"])
    written = []
    for vname, pal in data["variants"].items():
        for fn in frame_names:
            big = scaled(sprite(data["frames"][fn], pal), FRAME_SCALE)
            for tag, bg in (("light", LIGHT_BG), ("dark", DARK_BG)):
                p = os.path.join(out, f"{name}_{vname}_{fn}_{tag}.png")
                on_bg(big, bg).save(p)
                written.append(p)
    p = os.path.join(out, f"{name}_contact_sheet.png")
    contact_sheet(data, frame_names).save(p)
    written.append(p)
    p = os.path.join(out, f"{name}_idle.gif")
    idle_gif(data, p)
    written.append(p)
    p = os.path.join(out, "creature.png")
    scaled(sprite(data["frames"]["idle1"], data["variants"][data["defaultVariant"]]), UI_SCALE).save(p)
    written.append(p)

    print(f"OK {path}: {len(frame_names)} frames x {len(data['variants'])} variants valid")
    for fn in frame_names:
        used = sorted({c for r in data["frames"][fn] for c in r} - {"."})
        print(f"   {fn:8s} slots used: {len(used)} ({''.join(used)})")
    print(f"wrote {len(written)} files to {out}")


if __name__ == "__main__":
    main(sys.argv)
