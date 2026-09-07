#!/bin/bash
# One perf datapoint, end to end: relaunch, get into the parked save, sample, report.
#
#   ./measure.sh <label> [sample_secs]
#
# Assumes the device is already set up (game path granted, packs enabled, save in
# place, virtual pad running -- see ../README.md). Each run is ~3 min because the
# game's own load is ~90s; that cost is why you want to change ONE thing per run.
#
# ⚠️ The scene drifts (in-game night -> dawn, traffic despawning), so draws fall
# steadily even parked. Never compare two runs taken far apart -- interleave, and
# re-run the control. See project_mocha_oneplus_baseline in memory.
set -e

DEV="${DEV:-$(adb devices | awk 'NR==2{print $1}')}"
ADB="adb -s $DEV"
PKG=info.cemu.cemu.debug
LOG=/sdcard/Android/data/$PKG/files/log.txt
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-/tmp}"
LABEL="${1:?usage: measure.sh <label> [secs]}"
SECS="${2:-60}"

$ADB shell am force-stop $PKG; sleep 2
$ADB shell am start -n $PKG/info.cemu.cemu.MainActivity >/dev/null 2>&1; sleep 4
$ADB shell input tap 600 880          # the game tile
sleep 50
DEV=$DEV "$HERE/xboxpad.sh" hold start 1.0   # "PRESS +" -- must be HELD
# Wait for real gameplay rather than a fixed sleep; the title/menu scenes are ~31 draws.
for i in $(seq 1 40); do
  d=$($ADB shell "grep MOCHAFPS $LOG 2>/dev/null | tail -1" | tr -d '\r' | sed -n 's/.*draws=\([0-9]*\).*/\1/p')
  [ "${d:-0}" -ge 400 ] && break
  sleep 5
done
sleep 10   # let it settle after the load spike

S=$($ADB shell "grep -c MOCHAFPS $LOG" | tr -d '\r')
T1=$($ADB shell 'su -c "cat /sys/class/thermal/thermal_zone1/temp"' | tr -d '\r')
sleep "$SECS"
T2=$($ADB shell 'su -c "cat /sys/class/thermal/thermal_zone1/temp"' | tr -d '\r')
$ADB shell "grep MOCHAFPS $LOG | tail -n +$((S+1))" | tr -d '\r' > "$OUT/m_$LABEL.txt"

python3 - "$OUT/m_$LABEL.txt" "$LABEL" "$T1" "$T2" <<'EOF'
import re,statistics,sys
t=open(sys.argv[1]).read()
f=[float(m) for m in re.findall(r'fps=([\d.]+)',t)]
d=[int(m) for m in re.findall(r'draws=(\d+)',t)]
if not f: print(f'{sys.argv[2]}: NO SAMPLES'); sys.exit(1)
print(f'{sys.argv[2]:22s} n={len(f):3d} mean={statistics.mean(f):5.2f} median={statistics.median(f):5.2f} '
      f'min={min(f):5.2f} max={max(f):5.2f} sd={statistics.pstdev(f):4.2f} '
      f'draws={statistics.mean(d):5.0f} temp {int(sys.argv[3])/1000:.1f}->{int(sys.argv[4])/1000:.1f}C')
EOF
