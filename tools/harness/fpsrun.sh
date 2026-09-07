#!/bin/bash
# Capture a MOCHAFPS trace for a fixed duration, plus device temperature either side
# (the Odin 2 throttles hard - see memory: it hits 94-95C in ~2min).
# usage: fpsrun.sh <tag> [seconds]
set -u
DEV=3a478943
TAG=${1:-run}
DUR=${2:-180}
OUT="$(dirname "$0")/fps_${TAG}.txt"

temp() {
  adb -s $DEV shell 'for z in /sys/class/thermal/thermal_zone*/; do
      t=$(cat $z/type 2>/dev/null); v=$(cat $z/temp 2>/dev/null)
      case "$t" in *cpu*|*gpu*|*soc*) echo "$t=$v";; esac
    done' 2>/dev/null | head -6
}

echo "=== $TAG: ${DUR}s ==="
echo "temp before:"; temp
# NOTE: cemuLog_log writes to Cemu's own log.txt, NOT logcat. Marking the start line
# lets us take only the samples from this run out of an already-populated log.
CEMULOG=/sdcard/Android/data/info.cemu.cemu.debug/files/log.txt
START=$(adb -s $DEV shell "grep -c MOCHAFPS $CEMULOG" 2>/dev/null | tr -d '\r')
START=${START:-0}
adb -s $DEV shell setprop debug.mocha.fpslog 1
command sleep "$DUR"
adb -s $DEV shell "grep MOCHAFPS $CEMULOG" 2>/dev/null | tail -n +$((START + 1)) > "$OUT"
adb -s $DEV shell setprop debug.mocha.fpslog 0
echo "temp after:"; temp
echo "wrote $OUT"
wc -l < "$OUT"
