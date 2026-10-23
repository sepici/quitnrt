"""lint.py SRC [frame...] - flag doubled outlines, orphan pixels and pinholes."""
import sys
sys.path.insert(0, '.')
from view import load

frames = load(sys.argv[1])
want = sys.argv[2:] or list(frames)
for n in want:
    g = frames[n]
    at = lambda x, y: g[y][x] if 0 <= x < 32 and 0 <= y < 32 else '.'
    issues = []
    for y in range(32):
        for x in range(32):
            c = at(x, y)
            # 2x2 outline blocks
            if c == 'o' and at(x + 1, y) == 'o' and at(x, y + 1) == 'o' and at(x + 1, y + 1) == 'o':
                issues.append(f'2x2 outline at ({x},{y})')
            n4 = [at(x + 1, y), at(x - 1, y), at(x, y + 1), at(x, y - 1)]
            if c == '.' and all(k != '.' for k in n4):
                issues.append(f'pinhole at ({x},{y})')
            if c not in '.o' and all(k != c for k in n4):
                n8 = n4 + [at(x + 1, y + 1), at(x - 1, y - 1), at(x + 1, y - 1), at(x - 1, y + 1)]
                if all(k != c for k in n8):
                    issues.append(f'orphan {c} at ({x},{y})')
            # outline pixel that touches no transparent pixel and no fill on one side = possibly stray
            if c == 'o':
                n8 = [at(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1) if dx or dy]
                if all(k == 'o' for k in n8):
                    issues.append(f'buried outline at ({x},{y})')
    print(f'{n}: {len(issues)} issue(s)')
    for i in issues:
        print('   ', i)
