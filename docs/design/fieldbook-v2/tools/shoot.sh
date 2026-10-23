#!/bin/sh
# Usage: sh shoot.sh <variant dir> [page ...]
S="$(cd "$(dirname "$0")/../../tools" && pwd)/shot.mjs"
D=$1; shift
P=${*:-today log lair}
for p in $P; do for m in light dark; do node $S $D/$p.html $D/$p-$m.png $m >/dev/null & done; done; wait
echo shot $D: $P
