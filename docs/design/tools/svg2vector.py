"""Turns the mockups' inline SVG <symbol> icons into Android vector drawables.

The app's icons in app/src/main/res/drawable/ came from here. extra-icons.html holds the few the
mockups don't draw (microtab, strip, pouch, other), in the same style.

Usage, from the repo root, with symbol ids and drawable names after the --:
  python -I docs/design/tools/svg2vector.py app/src/main/res/drawable
      "docs/design/fieldbook-v2/b/*.html" docs/design/tools/extra-icons.html -- gum:ic_form_gum
"""
import re, sys, glob

def num(v): return float(v)

def rect_path(x, y, w, h, rx):
    if rx <= 0:
        return f"M{x},{y}h{w}v{h}h{-w}z"
    rx = min(rx, w / 2, h / 2)
    return (f"M{x+rx},{y}h{w-2*rx}a{rx},{rx} 0 0 1 {rx},{rx}v{h-2*rx}a{rx},{rx} 0 0 1 {-rx},{rx}"
            f"h{-(w-2*rx)}a{rx},{rx} 0 0 1 {-rx},{-rx}v{-(h-2*rx)}a{rx},{rx} 0 0 1 {rx},{-rx}z")

def circle_path(cx, cy, r):
    return f"M{cx-r},{cy}a{r},{r} 0 1 0 {2*r},0a{r},{r} 0 1 0 {-2*r},0z"

def attrs(tag):
    return dict(re.findall(r'([a-zA-Z-]+)="([^"]*)"', tag))

out, rest = sys.argv[1], sys.argv[2:]
split = rest.index("--")
files, wanted = rest[:split], rest[split + 1:]
symbols = {}
for f in files:
    for path in glob.glob(f):
        html = open(path, encoding="utf-8").read()
        for sid, body in re.findall(r'<symbol id="i-([a-z-]+)"[^>]*>(.*?)</symbol>', html, re.S):
            symbols.setdefault(sid, body)

for item in wanted:
    sid, name = item.split(":")
    body = symbols[sid]
    paths = []
    for tag, kind in re.findall(r"(<(path|circle|rect|line)\b[^>]*>)", body):
        a = attrs(tag)
        filled = a.get("fill") not in (None, "none")
        if kind == "path":
            d = a["d"]
        elif kind == "circle":
            d = circle_path(num(a["cx"]), num(a["cy"]), num(a["r"]))
        elif kind == "rect":
            d = rect_path(num(a.get("x", 0)), num(a.get("y", 0)), num(a["width"]), num(a["height"]), num(a.get("rx", 0)))
        else:
            d = f"M{a['x1']},{a['y1']}L{a['x2']},{a['y2']}"
        width = a.get("stroke-width", "1.9")
        if filled:
            paths.append(f'    <path\n        android:fillColor="#FF000000"\n        android:pathData="{d}" />')
        else:
            paths.append(
                f'    <path\n        android:pathData="{d}"\n        android:strokeColor="#FF000000"\n'
                f'        android:strokeWidth="{width}"\n        android:strokeLineCap="round"\n        android:strokeLineJoin="round" />'
            )
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- From the Fieldbook v2 B mockups. -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp"\n    android:height="24dp"\n'
           '    android:viewportWidth="24"\n    android:viewportHeight="24">\n' + "\n".join(paths) + "\n</vector>\n")
    open(f"{out}/{name}.xml", "w", encoding="utf-8", newline="\n").write(xml)
    print(name, len(paths), "paths")
