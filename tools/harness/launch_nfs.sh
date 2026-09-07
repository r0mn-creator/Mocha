#!/bin/bash
PKG=info.cemu.cemu.debug
adb shell am force-stop $PKG >/dev/null 2>&1
sleep 3
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 14
for attempt in 1 2 3 4; do
  adb shell input tap 482 741
  sleep 6
  if adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' | python3 -c "
import sys
t=sys.stdin.read()
sys.exit(0 if 'EmulationActivity' in t else 1)
"; then
    echo "EmulationActivity started (attempt $attempt)"
    exit 0
  fi
  echo "attempt $attempt: not started, retrying"
  sleep 4
done
echo "FAILED to start emulation"
exit 1
