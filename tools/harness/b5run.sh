#!/bin/bash
set -u
S="$(dirname "$0")"; LO=$1; HI=$2
B=/sdcard/Android/data/info.cemu.cemu.debug/files/graphicPacks
"$S/venv/bin/python" "$S/bisect5.py" "$LO" "$HI"
adb -s 3a478943 shell "rm -rf $B/NFS_B5; mkdir -p $B/NFS_B5"
adb -s 3a478943 push "$S/graphicPacks/NFS_B5/." $B/NFS_B5/ >/dev/null 2>&1
out=$("$S/packtest.sh" "b5_${LO}_${HI}" graphicPacks/NFS_B5/rules.txt 2>&1)
echo "$out" | grep -E "booted|NEVER REACHED" || true
echo "$out" | grep -q "NEVER REACHED" && exit 2
echo "  applied: $(adb -s 3a478943 shell "grep -c 'Graphic pack shader applied' /sdcard/Android/data/info.cemu.cemu.debug/files/log.txt" | tr -d '\r')"
"$S/venv/bin/python" - "$LO" "$HI" <<'EOF'
from PIL import Image
import numpy as np, sys
S='/tmp/claude-1000/-home-roman/027233c3-63e8-4956-9050-8bedc65fbc9d/scratchpad'
a=np.asarray(Image.open(f'{S}/pack_b5_{sys.argv[1]}_{sys.argv[2]}.png').convert('RGB'),dtype=np.float32)
t=a[60:360,200:600]; r=a[600:800,700:1200]
tr=(t[:,:,0]-(t[:,:,1]+t[:,:,2])/2).mean(); rr=(r[:,:,0]-(r[:,:,1]+r[:,:,2])/2).mean()
d=tr-rr
v='TREE painted -> INSIDE slice' if d>15 else ('flood (both red) - masked' if tr>25 and rr>25 else 'tree not painted -> OUTSIDE slice')
print(f'  treeRed={tr:7.2f}  roadRed={rr:7.2f}  DIFF={d:+7.2f}  frame={a.mean():6.1f}')
print(f'  VERDICT: {v}')
EOF
