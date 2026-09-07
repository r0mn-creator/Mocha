#!/bin/bash
# Fully automated graphic-pack A/B at the NFS save point (car parked, camera reset = repeatable).
# usage: packtest.sh <tag> <pack-relative-path-or-NONE>
#   e.g. packtest.sh vanilla NONE
#        packtest.sh official graphicPacks/downloadedGraphicPacks/NeedforSpeedMostWantedU_BloomFix/rules.txt
set -u
DEV=3a478943
S="$(dirname "$0")"
TAG=$1
PACK=${2:-NONE}
CFG=/sdcard/Android/data/info.cemu.cemu.debug/files/settings.xml
URI='content://com.android.externalstorage.documents/tree/75D7-DC5F%3AGames%2FESDE%2FROMs%2Fwiiu/document/75D7-DC5F%3AGames/ESDE/ROMs/wiiu/Need%20for%20Speed%E2%84%A2%20Most%20Wanted%20U%20(US).wua'
D=/dev/input/event7

adb -s $DEV shell 'am force-stop info.cemu.cemu.debug'; command sleep 3

# rewrite the <GraphicPack> block in place (sed -i keeps ownership; the app must be stopped
# or it will overwrite settings.xml on exit)
# Rewrite the GraphicPack block from scratch. Doing this by patching in place is fragile:
# the app writes it as <GraphicPack/> when empty, and as a single line containing BOTH tags plus
# the Entry when populated - so a naive "delete the Entry line" wipes the tags too and corrupts
# the file. Instead delete every GraphicPack-related line and re-insert a well-formed block
# after the stable <GameCache/> anchor.
adb -s $DEV shell "sed -i '/GraphicPack/d' $CFG"
if [ "$PACK" = "NONE" ]; then
  adb -s $DEV shell "sed -i 's|<GameCache/>|<GameCache/>\\n    <GraphicPack/>|' $CFG"
else
  adb -s $DEV shell "sed -i 's|<GameCache/>|<GameCache/>\\n    <GraphicPack><Entry filename=\"$PACK\"/></GraphicPack>|' $CFG"
fi
echo "--- $TAG: pack=$PACK ---"
adb -s $DEV shell "grep -A 2 '<GraphicPack>' $CFG" | /usr/bin/head -3

# ~30-40% of launches die from the pre-existing boot crash, so retry until one boots
LOG=/sdcard/Android/data/info.cemu.cemu.debug/files/log.txt
booted=0
for try in 1 2 3 4 5 6 7 8 9 10 11 12; do
  adb -s $DEV shell 'am force-stop info.cemu.cemu.debug' >/dev/null 2>&1; command sleep 3
  adb -s $DEV shell "am start -a android.intent.action.VIEW -n info.cemu.cemu.debug/info.cemu.cemu.emulation.EmulationActivity -d '$URI'" >/dev/null 2>&1
  command sleep 32
  sig=$(adb -s $DEV shell "grep -c 'Error: signal' $LOG" 2>/dev/null | tr -d '\r')
  pid=$(adb -s $DEV shell 'pidof info.cemu.cemu.debug' 2>/dev/null | tr -d '\r')
  if [ -n "$pid" ] && [ "$sig" = "0" ]; then booted=1; echo "  booted on attempt $try"; break; fi
  echo "  attempt $try: boot crash, retrying"
done
[ "$booted" = "1" ] || { echo "  FAILED to boot after 12 attempts"; exit 1; }
# title screen -> press START (held; taps get missed)
adb -s $DEV shell "sendevent $D 1 315 1; sendevent $D 0 0 0"; command sleep 0.5
adb -s $DEV shell "sendevent $D 1 315 0; sendevent $D 0 0 0"
command sleep 45
"$S/pad.sh" wake >/dev/null 2>&1   # left stick: steers wheels, does NOT move a parked car
command sleep 2

# Wait until we are ACTUALLY in gameplay before capturing. Loading screens otherwise get measured
# as "the glow is gone" - they contain no scene at all. The minimap's green markers are present
# only in gameplay (761-772 px) and absent on loading screens (0).
for w in 1 2 3 4 5 6 7 8 9 10 11 12; do
  adb -s $DEV exec-out screencap -p > "$S/pack_${TAG}.png" 2>/dev/null
  gp=$("$S/venv/bin/python" - "$S/pack_${TAG}.png" <<'EOF'
from PIL import Image
import numpy as np, sys
a=np.asarray(Image.open(sys.argv[1]).convert('RGB'),dtype=np.int16)[760:1040,20:360]
print(((a[:,:,1]>a[:,:,0]+30)&(a[:,:,1]>a[:,:,2]+30)).sum())
EOF
)
  if [ "${gp:-0}" -gt 120 ]; then
    break
  fi
  echo "  waiting for gameplay (green=${gp:-0})..."
  if [ "$w" = "12" ]; then
    echo "  *** NEVER REACHED GAMEPLAY - measurement INVALID, do not use ***"
    exit 2
  fi
  command sleep 10
  "$S/pad.sh" wake >/dev/null 2>&1
done
adb -s $DEV shell "grep -c 'Activate graphic pack:' $CFG" >/dev/null 2>&1
adb -s $DEV shell "grep 'Activate graphic pack:' /sdcard/Android/data/info.cemu.cemu.debug/files/log.txt" || echo "  (no pack activated)"
echo "  alive: $(adb -s $DEV shell 'pidof info.cemu.cemu.debug' | tr -d '\r')"
