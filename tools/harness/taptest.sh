#!/bin/bash
# Launch-reliability harness for the REAL user path: open the app, then TAP the game tile.
# The earlier 46-launch measurement used `am start` with an ACTION_VIEW intent, which is a
# different code path from tapping a tile in the games list.
#
# usage: taptest.sh <attempts> [wait_seconds]
set -u
DEV=3a478943
N=${1:-10}
WAIT=${2:-30}
S="$(dirname "$0")"
L=/sdcard/Android/data/info.cemu.cemu.debug/files/log.txt
TILE_X=482
TILE_Y=740

ok=0; crash=0; noboot=0
for i in $(seq 1 "$N"); do
  adb -s $DEV shell 'am force-stop info.cemu.cemu.debug' >/dev/null 2>&1
  command sleep 3
  adb -s $DEV shell 'am start -n info.cemu.cemu.debug/info.cemu.cemu.MainActivity' >/dev/null 2>&1
  command sleep 7                      # let the games list render
  adb -s $DEV shell "input tap $TILE_X $TILE_Y" >/dev/null 2>&1
  command sleep "$WAIT"

  pid=$(adb -s $DEV shell 'pidof info.cemu.cemu.debug' 2>/dev/null | tr -d '\r')
  sig=$(adb -s $DEV shell "grep -c 'Error: signal' $L" 2>/dev/null | tr -d '\r')
  run=$(adb -s $DEV shell "grep -c 'Run title' $L" 2>/dev/null | tr -d '\r')
  gu=$(adb -s $DEV shell "grep -c STHGUARD $L" 2>/dev/null | tr -d '\r')

  if [ -n "$pid" ] && [ "$sig" = "0" ] && [ "$run" != "0" ]; then
    ok=$((ok+1)); r=OK
  elif [ "$run" = "0" ]; then
    noboot=$((noboot+1)); r=NO-BOOT
  else
    crash=$((crash+1)); r=CRASH
    adb -s $DEV pull "$L" "$S/taplog_${i}.txt" >/dev/null 2>&1
  fi
  printf '  tap %2d: %-8s pid=%-7s signals=%s guard=%s\n' "$i" "$r" "${pid:-none}" "$sig" "$gu"
done
adb -s $DEV shell 'am force-stop info.cemu.cemu.debug' >/dev/null 2>&1
echo "--- TAP path: OK=$ok CRASH=$crash NO-BOOT=$noboot of $N => $(( ok * 100 / N ))% ---"
