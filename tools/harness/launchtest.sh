#!/bin/bash
# Automated launch-reliability harness for NFS MW U on Mocha.
# usage: launchtest.sh <sthmode 0|1|2> <attempts> [wait_seconds]
#
# The boot crash fires ~1s after "Run title", so a short wait is enough; 30s gives margin.
# A launch counts as SUCCESS if the process is alive, the log reached "Run title", and the
# log contains no "Error: signal".
set -u
DEV=3a478943
MODE=${1:-1}
N=${2:-10}
WAIT=${3:-30}
L=/sdcard/Android/data/info.cemu.cemu.debug/files/log.txt
URI='content://com.android.externalstorage.documents/tree/75D7-DC5F%3AGames%2FESDE%2FROMs%2Fwiiu/document/75D7-DC5F%3AGames/ESDE/ROMs/wiiu/Need%20for%20Speed%E2%84%A2%20Most%20Wanted%20U%20(US).wua'

adb -s $DEV shell "setprop debug.mocha.sthmode $MODE"
echo "=== sthmode=$MODE  attempts=$N  wait=${WAIT}s ==="

ok=0; fail=0; noboot=0
for i in $(seq 1 "$N"); do
  adb -s $DEV shell 'am force-stop info.cemu.cemu.debug' >/dev/null 2>&1
  command sleep 3
  adb -s $DEV shell "am start -a android.intent.action.VIEW -n info.cemu.cemu.debug/info.cemu.cemu.emulation.EmulationActivity -d '$URI'" >/dev/null 2>&1
  command sleep "$WAIT"

  pid=$(adb -s $DEV shell 'pidof info.cemu.cemu.debug' 2>/dev/null | tr -d '\r')
  sig=$(adb -s $DEV shell "grep -c 'Error: signal' $L" 2>/dev/null | tr -d '\r')
  run=$(adb -s $DEV shell "grep -c 'Run title' $L" 2>/dev/null | tr -d '\r')
  gu=$(adb -s $DEV shell "grep -c 'STHGUARD' $L" 2>/dev/null | tr -d '\r')

  if [ -n "$pid" ] && [ "$sig" = "0" ] && [ "$run" != "0" ]; then
    ok=$((ok+1)); r=OK
  elif [ "$run" = "0" ]; then
    noboot=$((noboot+1)); r="NO-BOOT"
  else
    fail=$((fail+1)); r=CRASH
    # log.txt truncates on every launch, so grab the failing one before the next attempt
    adb -s $DEV pull "$L" "$(dirname "$0")/faillog_mode${MODE}_${i}.txt" >/dev/null 2>&1
  fi
  printf '  attempt %2d: %-8s pid=%-7s signals=%s guardHits=%s\n' "$i" "$r" "${pid:-none}" "$sig" "$gu"
done

adb -s $DEV shell 'am force-stop info.cemu.cemu.debug' >/dev/null 2>&1
echo "--- sthmode=$MODE: OK=$ok CRASH=$fail NO-BOOT=$noboot of $N  => success $(( ok * 100 / N ))% ---"
