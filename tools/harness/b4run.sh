#!/bin/bash
# One bisection step over the full 278-shader set.
# usage: [MARKER=clamp|red] [EXCLUDE=1,2] b4run.sh <lo> <hi>
#
# MARKER=clamp is the working mode: clamping output caps the blowout while leaving the image
# readable, so "tree fixed" can be told apart from "frame destroyed". MARKER=red floods on a
# deferred renderer (any full-screen pass paints everything) and is only useful for geometry.
set -u
S="$(dirname "$0")"
LO=$1; HI=$2
B=/sdcard/Android/data/info.cemu.cemu.debug/files/graphicPacks
LOG=/sdcard/Android/data/info.cemu.cemu.debug/files/log.txt

"$S/venv/bin/python" "$S/bisect4.py" "$LO" "$HI"
adb -s 3a478943 shell "rm -rf $B/NFS_B4; mkdir -p $B/NFS_B4"
adb -s 3a478943 push "$S/graphicPacks/NFS_B4/." $B/NFS_B4/ >/dev/null 2>&1

out=$("$S/packtest.sh" "b4_${LO}_${HI}" graphicPacks/NFS_B4/rules.txt 2>&1)
echo "$out" | grep -E "booted|NEVER REACHED" || true
if echo "$out" | grep -q "NEVER REACHED"; then
  echo "  RESULT: INVALID (never reached gameplay)"
  exit 2
fi
echo "  applied: $(adb -s 3a478943 shell "grep -c 'Graphic pack shader applied' $LOG" | tr -d '\r')"

"$S/venv/bin/python" - "$LO" "$HI" "${MARKER:-red}" <<'EOF'
from PIL import Image
import numpy as np, sys
S='/tmp/claude-1000/-home-roman/027233c3-63e8-4956-9050-8bedc65fbc9d/scratchpad'
lo, hi, marker = sys.argv[1], sys.argv[2], sys.argv[3]
a=np.asarray(Image.open(f'{S}/pack_b4_{lo}_{hi}.png').convert('RGB'),dtype=np.float32)
t=a[60:360,200:600]; r=a[600:800,700:1200]
g=t.mean(axis=2)
red=(t[:,:,0]-(t[:,:,1]+t[:,:,2])/2).mean()
roadred=(r[:,:,0]-(r[:,:,1]+r[:,:,2])/2).mean()
blown=(g>240).mean()*100
if marker=='clamp':
    verdict = 'FIXED -> culprit INSIDE slice' if blown < 20 else 'still blown -> culprit OUTSIDE slice'
elif red > 25 and roadred > 25:
    verdict='BOTH RED - full-screen flood, tree signal MASKED'
elif red > 25:
    verdict='TREE RED only -> culprit INSIDE slice'
elif blown > 20:
    verdict='tree still blown -> culprit OUTSIDE slice'
else:
    verdict='AMBIGUOUS - inspect image'
print(f'  treeRed={red:7.2f}  roadRed={roadred:7.2f}  treeBlown={blown:6.2f}%  treeMean={g.mean():6.1f}  frame={a.mean():6.1f}')
print(f'  VERDICT: {verdict}')
EOF
